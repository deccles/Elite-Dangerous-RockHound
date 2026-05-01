package org.dce.ed.mining;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure logic for choosing the next prospector <strong>run number</strong> when appending mining log rows.
 * <p>
 * <strong>Why this is a separate type:</strong> run selection has subtle rules around “open” vs “closed” runs
 * and spreadsheet layout (only the <em>canonical</em> row receives {@linkplain ProspectorLogRow#getRunEndTime()
 * run end} on dock). Regressions here have happened when other edits touched {@code MiningTabPanel} without
 * re-reading the invariants. Call sites should delegate here; tests lock the behavior in.
 * </p>
 *
 * <h2>Product rules (do not change without updating tests and release notes)</h2>
 * <ol>
 *   <li><strong>Run end time is stored on one row only</strong> (asteroid {@code A} with a start time, else the
 *       first row in that run that has a start). Other rows for the same run may still have
 *       {@code runStartTime} set and {@code runEndTime == null} in the loaded model.</li>
 *   <li><strong>A run is “closed” for this commander if <em>any</em> row for that run has a non-null end time.</strong>
 *       We must not treat sibling rows (same run, still missing end on that line) as an open run, or the app
 *       will keep appending to the old run forever after dock.</li>
 *   <li><strong>“Active” / in-progress run</strong> = highest run id for this commander where there exists a row
 *       with {@code runStartTime != null && runEndTime == null} <em>and</em> that run id is not in the
 *       closed set from rule (2).</li>
 *   <li><strong>Location continuation:</strong> if there is no active run but the latest row at the current
 *       system (body may drift with a space-separated journal suffix such as hotspot labels) belongs to a run
 *       that is <em>not</em> closed, continue that run number; otherwise allocate
 *       {@code lastRunForThisCommander + 1}.</li>
 *   <li><strong>Run numbers are per commander</strong> (1..n within each commander), not globally unique across commanders.</li>
 * </ol>
 */
public final class MiningRunNumberResolver {

    private MiningRunNumberResolver() {
    }

    /**
     * Computes which run number new mining rows should use.
     *
     * @param commander     normalized mining log commander (blank becomes {@code "-"} when matching rows)
     * @param system        current star system name (may be empty)
     * @param body          current body name (may be empty)
     * @param forceNewRun   when true, allocate {@code lastRunForThisCommander + 1} if there is no active run
     * @param existingRows  all rows from the backend for this commander (or merged list; other commanders are ignored); may be empty, never null from callers
     * @return run number to use for new writes
     */
    public static int compute(
            String commander,
            String system,
            String body,
            boolean forceNewRun,
            List<ProspectorLogRow> existingRows) {
        List<ProspectorLogRow> existing = existingRows != null ? existingRows : List.of();

        int lastRunForCommander = 0;
        int lastRunForCommanderAtLocation = 0;
        int activeRunForCommander = 0;
        Instant latestTsForCommanderAtLocation = null;

        Set<Integer> runsWithEndForCommander = new HashSet<>();
        for (ProspectorLogRow r : existing) {
            if (r == null) {
                continue;
            }
            String rowCommander = normalizeCommander(r.getCommanderName());
            if (!rowCommander.equals(normalizeCommander(commander))) {
                continue;
            }
            if (r.getRunEndTime() != null) {
                runsWithEndForCommander.add(r.getRun());
            }
        }

        for (ProspectorLogRow r : existing) {
            if (r == null) {
                continue;
            }
            int rRun = r.getRun();
            String rowCommander = normalizeCommander(r.getCommanderName());
            if (!rowCommander.equals(normalizeCommander(commander))) {
                continue;
            }
            if (rRun > lastRunForCommander) {
                lastRunForCommander = rRun;
            }
            Instant ts = r.getTimestamp();
            if (ts == null) {
                continue;
            }
            // Open-row signal per line — invalidated for the whole run if any row in that run has an end (rule 2).
            if (r.getRunStartTime() != null && r.getRunEndTime() == null
                    && !runsWithEndForCommander.contains(rRun)) {
                if (rRun > activeRunForCommander) {
                    activeRunForCommander = rRun;
                }
            }
            String rowSystem;
            String rowBody;
            String fb = r.getFullBodyName();
            if (fb != null && !fb.isBlank()) {
                String[] parts = fb.split(">");
                if (parts.length == 2) {
                    rowSystem = parts[0].trim();
                    rowBody = parts[1].trim();
                } else {
                    rowSystem = "";
                    rowBody = fb.trim();
                }
            } else {
                rowSystem = "";
                rowBody = "";
            }
            boolean sameBody = Objects.equals(rowBody, body)
                    || GoogleSheetsBackend.bodiesDriftCompatible(
                            rowBody != null ? rowBody : "", body != null ? body : "");
            boolean sameLocation = Objects.equals(rowSystem, system) && sameBody;
            if (sameLocation) {
                if (latestTsForCommanderAtLocation == null || ts.isAfter(latestTsForCommanderAtLocation)) {
                    latestTsForCommanderAtLocation = ts;
                    lastRunForCommanderAtLocation = rRun;
                }
            }
        }

        if (lastRunForCommander == 0) {
            return 1;
        }
        if (activeRunForCommander > 0) {
            return activeRunForCommander;
        }
        if (forceNewRun) {
            return lastRunForCommander + 1;
        }
        if (lastRunForCommanderAtLocation > 0 && !runsWithEndForCommander.contains(lastRunForCommanderAtLocation)) {
            return lastRunForCommanderAtLocation;
        }
        return lastRunForCommander + 1;
    }

    static String normalizeCommander(String name) {
        if (name == null || name.isBlank()) {
            return "-";
        }
        return name;
    }
}
