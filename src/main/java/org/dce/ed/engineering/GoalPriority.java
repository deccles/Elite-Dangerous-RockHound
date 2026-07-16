package org.dce.ed.engineering;

/**
 * Planning priority for an engineering goal.
 * Active goals claim inventory High → Medium → Low when {@code enabled}.
 * {@link #DISABLED} is kept only for session migration (maps to enabled=false + {@link #MEDIUM}).
 */
public enum GoalPriority {
    HIGH,
    MEDIUM,
    LOW,
    /** @deprecated Prefer a separate enabled flag; still parsed from older sessions. */
    @Deprecated
    DISABLED;

    /** True for High / Medium / Low (not the legacy Disabled sentinel). */
    public boolean isActive() {
        return this != DISABLED;
    }

    /** Ascending sort rank: High first, then Medium, Low; legacy Disabled last. */
    public int sortRank() {
        return ordinal();
    }

    public GoalPriority next() {
        return switch (this) {
            case HIGH -> MEDIUM;
            case MEDIUM -> LOW;
            case LOW, DISABLED -> HIGH;
        };
    }

    /** Priorities offered in the UI chooser. */
    public static GoalPriority[] chooserValues() {
        return new GoalPriority[] { HIGH, MEDIUM, LOW };
    }

    /** Maps legacy {@link #DISABLED} to {@link #MEDIUM}. */
    public static GoalPriority normalize(GoalPriority priority) {
        if (priority == null || priority == DISABLED) {
            return MEDIUM;
        }
        return priority;
    }

    public String tooltip() {
        return menuLabel();
    }

    public String menuLabel() {
        return switch (this) {
            case HIGH -> "High priority";
            case MEDIUM -> "Medium priority";
            case LOW -> "Low priority";
            case DISABLED -> "Disabled";
        };
    }

    /** @deprecated Use an explicit enabled flag; this only yields MEDIUM vs DISABLED for migration. */
    @Deprecated
    public static GoalPriority fromInclude(boolean include) {
        return include ? MEDIUM : DISABLED;
    }

    public static GoalPriority parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEDIUM;
        }
        try {
            return GoalPriority.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return MEDIUM;
        }
    }
}
