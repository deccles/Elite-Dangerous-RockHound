package org.dce.ed.mining;

import java.time.Instant;
import java.util.List;

/**
 * "Both" mining-log backend: writes to {@code primary} first then {@code mirror}, reads only from {@code primary}.
 *
 * <p>Read delegation matches the user contract: whichever backend is selected as the displayed source on the Mining
 * tab is the {@code primary} for the composite. Run resolution and the table both come from the primary so the
 * mirror divergence (when one side is offline) does not affect what the user sees.</p>
 *
 * <p>Write semantics: primary failure surfaces as {@link ProspectorWriteResult.Status#FAILURE} and the mirror is
 * skipped (the row was not durably stored). Primary success + mirror failure surfaces as
 * {@link ProspectorWriteResult.Status#OK} with a non-null {@link ProspectorWriteResult#getMirrorWarning()} so the
 * UI can show the divergence in the status bar without rolling back the primary.</p>
 */
public final class CompositeProspectorLogBackend implements ProspectorLogBackend {

    private final ProspectorLogBackend primary;
    private final ProspectorLogBackend mirror;

    public CompositeProspectorLogBackend(ProspectorLogBackend primary, ProspectorLogBackend mirror) {
        if (primary == null) {
            throw new IllegalArgumentException("primary backend is required");
        }
        if (mirror == null) {
            throw new IllegalArgumentException("mirror backend is required");
        }
        this.primary = primary;
        this.mirror = mirror;
    }

    public ProspectorLogBackend getPrimary() {
        return primary;
    }

    public ProspectorLogBackend getMirror() {
        return mirror;
    }

    @Override
    public void appendRows(List<ProspectorLogRow> rows) {
        ProspectorWriteResult r = appendRowsResult(rows);
        if (r != null && !r.isOk()) {
            Throwable c = r.getCause();
            if (c instanceof RuntimeException re) throw re;
            throw new RuntimeException(r.getMessage(), c);
        }
    }

    @Override
    public java.util.List<ProspectorLogRow> loadRows() {
        ProspectorLoadResult r = loadRowsWithStatus();
        return r != null ? r.getRows() : java.util.Collections.emptyList();
    }

    @Override
    public void updateRunEndTime(String commander, int run, Instant endTime) {
        updateRunEndTimeResult(commander, run, endTime);
    }

    @Override
    public ProspectorWriteResult appendRowsResult(List<ProspectorLogRow> rows) {
        ProspectorWriteResult primaryResult = primary.appendRowsResult(rows);
        if (primaryResult == null || !primaryResult.isOk()) {
            return primaryResult;
        }
        return attachMirrorWarning(primaryResult, mirror.appendRowsResult(rows));
    }

    @Override
    public ProspectorWriteResult upsertRowsResult(List<ProspectorLogRow> rows) {
        ProspectorWriteResult primaryResult = primary.upsertRowsResult(rows);
        if (primaryResult == null || !primaryResult.isOk()) {
            return primaryResult;
        }
        return attachMirrorWarning(primaryResult, mirror.upsertRowsResult(rows));
    }

    @Override
    public ProspectorWriteResult updateRunEndTimeResult(String commander, int run, Instant endTime) {
        ProspectorWriteResult primaryResult = primary.updateRunEndTimeResult(commander, run, endTime);
        if (primaryResult == null || !primaryResult.isOk()) {
            return primaryResult;
        }
        return attachMirrorWarning(primaryResult, mirror.updateRunEndTimeResult(commander, run, endTime));
    }

    private ProspectorWriteResult attachMirrorWarning(ProspectorWriteResult primaryResult,
            ProspectorWriteResult mirrorResult) {
        if (mirrorResult == null || mirrorResult.isOk()) {
            return primaryResult;
        }
        String warn = mirror.displayName() + " mirror failed: " + mirrorResult.getMessage();
        return primaryResult.withMirrorWarning(warn);
    }

    @Override
    public ProspectorLoadResult loadRowsWithStatus() {
        return primary.loadRowsWithStatus();
    }

    @Override
    public ProspectorLoadResult loadRowsWithStatusForCommander(String commander) {
        return primary.loadRowsWithStatusForCommander(commander);
    }

    @Override
    public String displayName() {
        return "Both (primary: " + primary.displayName() + ")";
    }

    @Override
    public boolean prefersDebouncedRefresh() {
        return primary.prefersDebouncedRefresh() || mirror.prefersDebouncedRefresh();
    }
}
