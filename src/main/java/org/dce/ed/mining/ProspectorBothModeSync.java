package org.dce.ed.mining;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One-shot per-commander synchronization between Google Sheets and the local CSV when running in Both mode.
 *
 * <p>Workflow:</p>
 * <ol>
 *   <li>Read the commander's rows from each side via {@link ProspectorLogBackend#loadRowsWithStatusForCommander}.</li>
 *   <li>Merge the union of both row sets keyed on {@code (run, asteroid, material, commander)}, applying the same
 *       {@link ProspectorRowMergeRules} as a normal upsert (highest dud counter wins, non-blank core wins, non-blank
 *       comments wins, etc.).</li>
 *   <li>Upsert the merged rows back into <em>both</em> backends so they end up identical for that commander.</li>
 * </ol>
 *
 * <p>Only the local user's commander(s) are synced — we never pull other commanders' rows from the cloud sheet
 * down to the local CSV because the spreadsheet may be shared with users whose data we don't own.</p>
 */
public final class ProspectorBothModeSync {

    private ProspectorBothModeSync() {
    }

    /**
     * Sync one commander's rows. Both arguments must point at the per-commander view (sheet tab + commander CSV);
     * the method handles loading and merging.
     *
     * @return primary-result-style status. {@link ProspectorWriteResult.Status#FAILURE} when either side errored
     *         and we could not safely write the merged set back; {@link ProspectorWriteResult.Status#OK} with a
     *         non-null mirror warning when one side wrote successfully but the other did not.
     */
    public static ProspectorWriteResult syncCommander(GoogleSheetsBackend sheets, LocalCsvBackend csv,
            String commander) {
        if (sheets == null) {
            return ProspectorWriteResult.failure("Google Sheets backend is unavailable; sync skipped.");
        }
        if (csv == null) {
            return ProspectorWriteResult.failure("Local CSV backend is unavailable; sync skipped.");
        }
        String cmdr = commander != null ? commander.trim() : "";
        if (cmdr.isEmpty()) {
            return ProspectorWriteResult.failure("Commander name is empty; cannot sync.");
        }

        ProspectorLoadResult sheetsLoad = sheets.loadRowsWithStatusForCommander(cmdr);
        if (sheetsLoad != null && sheetsLoad.getStatus() == ProspectorLoadResult.Status.ERROR) {
            return ProspectorWriteResult.failure(
                    "Could not read Google Sheets for sync: "
                            + (sheetsLoad.getDetailMessage() != null ? sheetsLoad.getDetailMessage() : "unknown error"));
        }

        ProspectorLoadResult csvLoad = csv.loadRowsWithStatusForCommander(cmdr);
        if (csvLoad != null && csvLoad.getStatus() == ProspectorLoadResult.Status.ERROR) {
            return ProspectorWriteResult.failure(
                    "Could not read Local CSV for sync: "
                            + (csvLoad.getDetailMessage() != null ? csvLoad.getDetailMessage() : "unknown error"));
        }

        List<ProspectorLogRow> sheetRows = sheetsLoad != null && sheetsLoad.getRows() != null
                ? sheetsLoad.getRows() : List.of();
        List<ProspectorLogRow> csvRows = csvLoad != null && csvLoad.getRows() != null
                ? csvLoad.getRows() : List.of();

        List<ProspectorLogRow> merged = mergeForSync(sheetRows, csvRows);
        if (merged.isEmpty()) {
            return ProspectorWriteResult.ok();
        }

        ProspectorWriteResult sheetsResult = sheets.upsertRowsResult(merged);
        ProspectorWriteResult csvResult = csv.upsertRowsResult(merged);

        boolean sheetsOk = sheetsResult != null && sheetsResult.isOk();
        boolean csvOk = csvResult != null && csvResult.isOk();
        if (sheetsOk && csvOk) {
            return ProspectorWriteResult.ok();
        }
        if (!sheetsOk && !csvOk) {
            return ProspectorWriteResult.failure(
                    "Sync failed on both sides — Sheets: " + msg(sheetsResult) + "; CSV: " + msg(csvResult));
        }
        if (!sheetsOk) {
            return ProspectorWriteResult.okWithMirrorWarning(
                    "Sync wrote local CSV but Google Sheets failed: " + msg(sheetsResult));
        }
        return ProspectorWriteResult.okWithMirrorWarning(
                "Sync wrote Google Sheets but local CSV failed: " + msg(csvResult));
    }

    /** Merge two row lists into one keyed by upsert identity. Visible for tests. */
    static List<ProspectorLogRow> mergeForSync(List<ProspectorLogRow> a, List<ProspectorLogRow> b) {
        Map<Key, ProspectorLogRow> byKey = new LinkedHashMap<>();
        for (ProspectorLogRow r : a) {
            if (r == null) continue;
            byKey.put(Key.of(r), r);
        }
        for (ProspectorLogRow r : b) {
            if (r == null) continue;
            Key k = Key.of(r);
            ProspectorLogRow existing = byKey.get(k);
            if (existing == null) {
                byKey.put(k, r);
            } else {
                byKey.put(k, mergeForSyncRow(existing, r));
            }
        }
        return new ArrayList<>(byKey.values());
    }

    /** Merge two rows that share the same upsert identity using the same shared rules as {@code upsertRowsResult}. */
    static ProspectorLogRow mergeForSyncRow(ProspectorLogRow x, ProspectorLogRow y) {
        // Pick the row with the most recent timestamp as the "primary" base, but apply merge rules to fields where
        // the rules know better than newest-wins (core, comments, duds, runStart-not-overwriting-meaningful).
        ProspectorLogRow newest;
        ProspectorLogRow other;
        if (laterOf(x, y)) {
            newest = x;
            other = y;
        } else {
            newest = y;
            other = x;
        }
        String core = ProspectorRowMergeRules.mergeCore(newest.getCoreType(), other.getCoreType());
        if ("-".equals(core)) {
            core = "";
        }
        String comments = ProspectorRowMergeRules.mergeComments(newest.getComments(), other.getComments());
        int duds = ProspectorRowMergeRules.mergeDuds(newest.getDuds(), other.getDuds());

        Instant runStart = newest.getRunStartTime() != null ? newest.getRunStartTime() : other.getRunStartTime();
        Instant runEnd = newest.getRunEndTime() != null ? newest.getRunEndTime() : other.getRunEndTime();

        String body = !blank(newest.getFullBodyName()) ? newest.getFullBodyName() : other.getFullBodyName();
        String ship = !blank(newest.getShipType()) ? newest.getShipType() : other.getShipType();

        return new ProspectorLogRow(
                newest.getRun(),
                newest.getAsteroidId(),
                body,
                newest.getTimestamp() != null ? newest.getTimestamp() : other.getTimestamp(),
                newest.getMaterial(),
                newest.getPercent(),
                newest.getBeforeAmount(),
                newest.getAfterAmount(),
                newest.getDifference(),
                newest.getCommanderName(),
                ship,
                core,
                duds,
                runStart,
                runEnd,
                comments);
    }

    private static boolean laterOf(ProspectorLogRow x, ProspectorLogRow y) {
        Instant tx = x.getTimestamp();
        Instant ty = y.getTimestamp();
        if (tx == null && ty == null) return true;
        if (tx == null) return false;
        if (ty == null) return true;
        return !tx.isBefore(ty);
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String msg(ProspectorWriteResult r) {
        if (r == null) return "no result";
        String m = r.getMessage();
        return m == null || m.isBlank() ? "unknown error" : m;
    }

    private static final class Key {
        final int run;
        final String asteroid;
        final String material;
        final String commander;

        Key(int run, String asteroid, String material, String commander) {
            this.run = run;
            this.asteroid = norm(asteroid);
            this.material = norm(material);
            this.commander = norm(commander);
        }

        static Key of(ProspectorLogRow r) {
            return new Key(r.getRun(), r.getAsteroidId(), r.getMaterial(), r.getCommanderName());
        }

        private static String norm(String s) {
            return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return run == k.run && asteroid.equals(k.asteroid) && material.equals(k.material)
                    && commander.equals(k.commander);
        }

        @Override
        public int hashCode() {
            int h = Integer.hashCode(run);
            h = 31 * h + asteroid.hashCode();
            h = 31 * h + material.hashCode();
            h = 31 * h + commander.hashCode();
            return h;
        }
    }
}
