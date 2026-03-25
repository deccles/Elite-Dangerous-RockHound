package org.dce.ed.route;

/**
 * Result of applying a non-file journal event to {@link RouteSession}.
 */
public record RouteJournalApplyOutcome(boolean exitHandleLogWithoutSessionPersist, boolean refreshDisplayedRows) {
}
