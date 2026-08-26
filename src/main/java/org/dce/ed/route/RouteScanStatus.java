package org.dce.ed.route;

/**
 * EDSM / local scan summary for a route system row.
 */
public enum RouteScanStatus {
    DISCOVERY_MISSING_VISITED,
    DISCOVERY_MISSING_NOT_VISITED,
    BODYCOUNT_MISMATCH_VISITED,
    BODYCOUNT_MISMATCH_NOT_VISITED,
    FULLY_DISCOVERED_VISITED,
    FULLY_DISCOVERED_NOT_VISITED,
    /** EDSM has not returned a result for this route row yet. */
    PENDING,
    /** Outside the rolling EDSM lookup window; displayed as an inactive empty circle. */
    DEFERRED,
    /** EDSM completed without useful body-discovery data. */
    UNKNOWN;

    public boolean needsEdsmQuery() {
        return this == PENDING;
    }
}
