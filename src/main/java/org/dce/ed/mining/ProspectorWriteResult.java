package org.dce.ed.mining;

/**
 * Result of a Google Sheets / CSV / composite prospector write operation.
 *
 * <p>{@link #getMirrorWarning()} is populated only by {@link CompositeProspectorLogBackend} when the primary write
 * succeeded but the mirror failed. The status itself reflects the primary's outcome, so primary success surfaces as
 * {@link Status#OK} with a mirror warning string the UI shows in the status bar.</p>
 */
public final class ProspectorWriteResult {

    public enum Status {
        OK,
        FAILURE
    }

    private final Status status;
    private final String message;
    private final Throwable cause;
    private final String mirrorWarning;

    private ProspectorWriteResult(Status status, String message, Throwable cause, String mirrorWarning) {
        this.status = status;
        this.message = message != null ? message : "";
        this.cause = cause;
        this.mirrorWarning = mirrorWarning != null && !mirrorWarning.isBlank() ? mirrorWarning.trim() : null;
    }

    public static ProspectorWriteResult ok() {
        return new ProspectorWriteResult(Status.OK, "", null, null);
    }

    public static ProspectorWriteResult okWithMirrorWarning(String mirrorWarning) {
        return new ProspectorWriteResult(Status.OK, "", null, mirrorWarning);
    }

    public static ProspectorWriteResult failure(String message) {
        return new ProspectorWriteResult(Status.FAILURE, message, null, null);
    }

    public static ProspectorWriteResult failure(String message, Throwable cause) {
        return new ProspectorWriteResult(Status.FAILURE, message, cause, null);
    }

    /** Returns a copy of this result with the given mirror warning attached. */
    public ProspectorWriteResult withMirrorWarning(String mirrorWarning) {
        return new ProspectorWriteResult(status, message, cause, mirrorWarning);
    }

    public Status getStatus() {
        return status;
    }

    public boolean isOk() {
        return status == Status.OK;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getCause() {
        return cause;
    }

    /**
     * In Both mode, populated when the primary write succeeded but the mirror failed (or vice versa is being
     * reported). {@code null} when there is no warning to surface.
     */
    public String getMirrorWarning() {
        return mirrorWarning;
    }

    public boolean hasMirrorWarning() {
        return mirrorWarning != null && !mirrorWarning.isBlank();
    }
}
