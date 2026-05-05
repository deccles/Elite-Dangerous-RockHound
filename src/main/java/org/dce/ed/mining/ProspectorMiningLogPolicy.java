package org.dce.ed.mining;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Shared, testable rules for how prospector mining logs treat <strong>run start</strong> and
 * <strong>run end</strong> times in Google Sheets (and the same ideas elsewhere).
 * <p>
 * See {@link MiningRunNumberResolver} for run <em>number</em> selection. This class covers:
 * </p>
 * <ul>
 *   <li><strong>Upsert run start (column O / index 14 when Ship column is present, else 13):</strong> never overwrite a cell that already holds a
 *       <em>recognisable</em> run-start timestamp — cargo updates after a new undock must not replace the original trip start. Non-date text in that
 *       cell (e.g. a ship name shifted by a column bug) is not treated as canonical start and may be replaced.</li>
 *   <li><strong>Run end placement:</strong> only one sheet row per run should receive end time on dock —
 *       prefer asteroid {@code A} with a meaningful start time, else the first data row with a meaningful start
 *       (empty or legacy {@code "-"} start cells do not count).</li>
 * </ul>
 * <p>
 * Regressions here have shipped when logic was inlined in {@code GoogleSheetsBackend} without re-reading
 * these invariants; keep changes here and in tests together.
 * </p>
 */
public final class ProspectorMiningLogPolicy {

    private static final int COL_RUN = 0;
    private static final int COL_ASTEROID = 1;
    private static final int COL_COMMANDER = 12;
    private static final int COL_START_NEW_LAYOUT = 15;
    private static final int COL_START_OLD_LAYOUT = 14;
    private static final int MIN_WIDTH_WITH_COMMENTS = 17;
    private static final int MIN_WIDTH_WITH_SHIP = 16;

    private static final Pattern RUN_START_TEXT_LIKE =
            Pattern.compile("\\d{1,2}/\\d{1,2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}|\\d{4}-\\d{2}-\\d{2}T");

    private ProspectorMiningLogPolicy() {
    }

    /**
     * When upserting an existing sheet row, write run start only on asteroid {@code A} and only if the incoming row
     * carries a start instant while the cell is empty, legacy {@code "-"}, or holds non-date text (corrupt placement).
     * Preserves a real timestamp already in the cell across later cargo upserts.
     */
    public static boolean shouldWriteRunStartOnUpsertExistingRow(
            String asteroidId,
            String existingStartCellText,
            Instant incomingRunStart) {
        if (!"A".equalsIgnoreCase(asteroidId != null ? asteroidId.trim() : "")) {
            return false;
        }
        if (incomingRunStart == null) {
            return false;
        }
        if (!hasMeaningfulRunStartCell(existingStartCellText)) {
            return true;
        }
        return !looksLikeRunStartTimestamp(existingStartCellText);
    }

    /**
     * True when column N already holds a substantive run start (not empty and not a legacy {@code "-"} placeholder).
     */
    static boolean hasMeaningfulRunStartCell(String cell) {
        if (cell == null) {
            return false;
        }
        String t = cell.trim();
        return !t.isEmpty() && !"-".equals(t);
    }

    /**
     * True when the run-start cell text looks like a date/time (or Sheets serial), not arbitrary text such as a ship name.
     */
    static boolean looksLikeRunStartTimestamp(String cell) {
        if (cell == null) {
            return false;
        }
        String t = cell.trim();
        if (t.isEmpty() || "-".equals(t)) {
            return false;
        }
        if (RUN_START_TEXT_LIKE.matcher(t).find()) {
            return true;
        }
        try {
            double d = Double.parseDouble(t);
            return d > 20_000 && d < 80_000;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Finds the 1-based data row index in {@code values} (row {@code 0} is the header) that should receive
     * run end time on dock, or {@code -1} if none.
     * <p>
     * Matches {@link org.dce.ed.mining.GoogleSheetsBackend#updateRunEndTime} scan order.
     * </p>
     */
    public static int findDataRowIndexForCanonicalRunEnd(List<List<Object>> values, int run, String commander) {
        if (values == null || values.size() < 2) {
            return -1;
        }
        String cmdr = commander != null ? commander : "";
        int preferA = findFirstMatching(values, run, cmdr, true);
        if (preferA >= 0) {
            return preferA;
        }
        int fallbackA = findFirstAsteroidA(values, run, cmdr);
        if (fallbackA >= 0) {
            return fallbackA;
        }
        return findFirstMatching(values, run, cmdr, false);
    }

    private static int findFirstMatching(List<List<Object>> values, int run, String cmdr, boolean requireAsteroidA) {
        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row == null || row.size() < 13) {
                continue;
            }
            int rowRun = parseInt(row.get(COL_RUN), 0);
            String rowCommander = str(row.get(COL_COMMANDER));
            int startCol = row.size() >= MIN_WIDTH_WITH_COMMENTS ? COL_START_NEW_LAYOUT
                    : (row.size() >= MIN_WIDTH_WITH_SHIP ? COL_START_OLD_LAYOUT : 13);
            String rowStart = row.size() > startCol ? str(row.get(startCol)) : "";
            if (rowRun != run || !Objects.equals(rowCommander, cmdr) || !looksLikeRunStartTimestamp(rowStart)) {
                continue;
            }
            if (requireAsteroidA) {
                String asteroid = str(row.get(COL_ASTEROID));
                if (!"A".equalsIgnoreCase(asteroid)) {
                    continue;
                }
            }
            return i;
        }
        return -1;
    }

    private static int findFirstAsteroidA(List<List<Object>> values, int run, String cmdr) {
        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row == null || row.size() < 13) {
                continue;
            }
            int rowRun = parseInt(row.get(COL_RUN), 0);
            String rowCommander = str(row.get(COL_COMMANDER));
            String asteroid = str(row.get(COL_ASTEROID));
            if (rowRun == run && Objects.equals(rowCommander, cmdr) && "A".equalsIgnoreCase(asteroid)) {
                return i;
            }
        }
        return -1;
    }

    private static String str(Object o) {
        return o != null ? o.toString().trim() : "";
    }

    private static int parseInt(Object o, int def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
