package org.dce.ed.mining;

import java.util.Locale;

/**
 * Shared merge rules for prospector row upserts. Both {@link GoogleSheetsBackend} and {@link LocalCsvBackend}
 * delegate to these methods so identical inputs produce identical merged outputs regardless of storage backend.
 *
 * <p>Identity for an upsert is {@code (run, asteroid, material, commander)}; system/body are display-only and
 * never affect identity. See {@link GoogleSheetsBackend#findProspectorUpsertRowIndex} for the matching rules.</p>
 */
public final class ProspectorRowMergeRules {

    private ProspectorRowMergeRules() {
    }

    /** Trim/blank/dash check matching the Sheets cell semantics ({@code -} is treated as blank). */
    public static boolean isBlankCell(String s) {
        if (s == null) {
            return true;
        }
        String t = s.trim();
        return t.isEmpty() || "-".equals(t);
    }

    /**
     * Cargo-driven updates frequently arrive with no core type; preserve a previously written core cell instead of
     * replacing it with the legacy {@code "-"} placeholder.
     */
    public static String mergeCore(String incomingCore, String existingCoreCell) {
        String inc = incomingCore != null ? incomingCore.trim() : "";
        if (!inc.isEmpty()) {
            return inc;
        }
        String ex = existingCoreCell != null ? existingCoreCell.trim() : "";
        return !isBlankCell(ex) ? ex : "-";
    }

    /** Like {@link #mergeCore} but the comments column stays empty (no {@code "-"} placeholder). */
    public static String mergeComments(String incomingComments, String existingCell) {
        String inc = incomingComments != null ? incomingComments.trim() : "";
        if (!inc.isEmpty()) {
            return inc;
        }
        String ex = existingCell != null ? existingCell.trim() : "";
        return !isBlankCell(ex) ? ex : "";
    }

    /**
     * Duds are a per-asteroid running counter that may legitimately reach a high value, then later updates for the
     * same row arrive with zero (e.g. a cargo refresh). Always keep the higher value.
     */
    public static int mergeDuds(int incomingDuds, int existingDuds) {
        return Math.max(0, Math.max(incomingDuds, existingDuds));
    }

    /**
     * Sort index for asteroid IDs: A=0, B=1, ..., Z=25, AA=26, AB=27, ... Invalid / blank IDs sort last.
     */
    public static int asteroidIdSortIndex(String asteroidId) {
        String s = asteroidId != null ? asteroidId.trim().toUpperCase(Locale.ROOT) : "";
        if (s.isEmpty() || "-".equals(s)) {
            return Integer.MAX_VALUE;
        }
        int idx = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < 'A' || ch > 'Z') {
                return Integer.MAX_VALUE;
            }
            idx = idx * 26 + (ch - 'A' + 1);
        }
        return idx - 1;
    }
}
