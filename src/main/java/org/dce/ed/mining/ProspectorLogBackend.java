package org.dce.ed.mining;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Backend for prospector log: append/upsert rows, load rows, and update run end time. Implementations:
 * {@link GoogleSheetsBackend}, {@link LocalCsvBackend}, and {@link CompositeProspectorLogBackend} (Both mode).
 *
 * <p>Both API styles co-exist: the {@code *Result}-returning methods are the canonical surface for production
 * rules code (failures are reported as a structured {@link ProspectorWriteResult} / {@link ProspectorLoadResult}
 * instead of thrown), and the simpler {@link #appendRows}, {@link #loadRows}, and {@link #updateRunEndTime} hooks
 * are kept for legacy tests and historical call sites.</p>
 *
 * <p>To minimize the impact on existing test mocks, the legacy methods remain abstract and the {@code *Result}
 * variants have default implementations that wrap them. Production backends override the {@code *Result} methods
 * directly so they can return rich error information without throwing.</p>
 */
public interface ProspectorLogBackend {

    /**
     * Append the given rows. Throws {@link RuntimeException} on I/O or API failure (legacy hook).
     */
    void appendRows(List<ProspectorLogRow> rows);

    /**
     * Load all rows. Returns empty list if none or on read failure (caller may log).
     */
    List<ProspectorLogRow> loadRows();

    /**
     * Set run end time on the canonical row (the row with run start time set) for the given run and commander.
     * No-op if no such row or backend does not support updates.
     */
    void updateRunEndTime(String commander, int run, Instant endTime);

    /**
     * Append rows, returning a structured result instead of throwing. Default wraps {@link #appendRows} so legacy
     * test mocks continue to work; production backends override directly.
     */
    default ProspectorWriteResult appendRowsResult(List<ProspectorLogRow> rows) {
        try {
            appendRows(rows);
            return ProspectorWriteResult.ok();
        } catch (Exception e) {
            String m = e.getMessage();
            return ProspectorWriteResult.failure(m != null && !m.isBlank() ? m : "append failed", e);
        }
    }

    /**
     * Insert or update rows keyed by {@code (run, asteroid, material, commander)}, returning a structured result.
     * Default for backends that don't support upsert: falls back to {@link #appendRowsResult}.
     */
    default ProspectorWriteResult upsertRowsResult(List<ProspectorLogRow> rows) {
        return appendRowsResult(rows);
    }

    /**
     * Set the run end time on the canonical row, returning a structured result. Default wraps the legacy hook.
     */
    default ProspectorWriteResult updateRunEndTimeResult(String commander, int run, Instant endTime) {
        try {
            updateRunEndTime(commander, run, endTime);
            return ProspectorWriteResult.ok();
        } catch (Exception e) {
            String m = e.getMessage();
            return ProspectorWriteResult.failure(m != null && !m.isBlank() ? m : "update failed", e);
        }
    }

    /**
     * Load all rows with explicit status. Default wraps {@link #loadRows}; backends that natively distinguish
     * "empty" from "error" should override.
     */
    default ProspectorLoadResult loadRowsWithStatus() {
        try {
            List<ProspectorLogRow> rows = loadRows();
            if (rows == null || rows.isEmpty()) {
                return new ProspectorLoadResult(ProspectorLoadResult.Status.EMPTY_SHEET, Collections.emptyList());
            }
            return new ProspectorLoadResult(ProspectorLoadResult.Status.OK, rows);
        } catch (Exception e) {
            String m = e.getMessage();
            return new ProspectorLoadResult(ProspectorLoadResult.Status.ERROR, Collections.emptyList(),
                    m != null && !m.isBlank() ? m : "load failed");
        }
    }

    /**
     * Load rows for a single commander only. Default delegates to {@link #loadRowsWithStatus} and filters in
     * memory; backends with native commander scoping (CSV per-commander files, Sheets tabs) override.
     */
    default ProspectorLoadResult loadRowsWithStatusForCommander(String commander) {
        ProspectorLoadResult all = loadRowsWithStatus();
        if (all == null || all.getRows() == null || commander == null || commander.isBlank()) {
            return all;
        }
        String want = commander.trim();
        java.util.List<ProspectorLogRow> filtered = new java.util.ArrayList<>();
        for (ProspectorLogRow r : all.getRows()) {
            if (r != null && r.getCommanderName() != null && want.equalsIgnoreCase(r.getCommanderName().trim())) {
                filtered.add(r);
            }
        }
        if (filtered.isEmpty()) {
            return new ProspectorLoadResult(ProspectorLoadResult.Status.EMPTY_SHEET, Collections.emptyList());
        }
        return new ProspectorLoadResult(ProspectorLoadResult.Status.OK, filtered);
    }

    /**
     * Short, user-facing name for status bar messages. E.g. "Google Sheets", "Local CSV", "Both (primary: Local CSV)".
     */
    default String displayName() {
        return "Mining log";
    }

    /**
     * True if the backend benefits from debounced refresh after writes (network-bound). Local CSV returns false
     * (refreshes are cheap); Sheets / composite return true.
     */
    default boolean prefersDebouncedRefresh() {
        return false;
    }
}
