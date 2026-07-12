package org.dce.ed.engineering;

/**
 * Whether a single engineering goal can be completed from inventory, optionally after trades.
 */
public enum GoalReadiness {
    /** All required materials are already in inventory. */
    READY,
    /** Missing materials; suggested trades would cover them. */
    READY_WITH_TRADES,
    /** Missing materials even after applying suggested trades. */
    STILL_SHORT
}
