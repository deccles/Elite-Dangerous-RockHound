package org.dce.ed.engineering;

/**
 * Planning priority for an engineering goal.
 * Active goals claim inventory High → Medium → Low; {@link #DISABLED} is omitted from materials/trades.
 */
public enum GoalPriority {
    HIGH,
    MEDIUM,
    LOW,
    DISABLED;

    /** Included in materials / trade planning. */
    public boolean isActive() {
        return this != DISABLED;
    }

    /** Ascending sort rank: High first, Disabled last. */
    public int sortRank() {
        return ordinal();
    }

    public GoalPriority next() {
        return switch (this) {
            case HIGH -> MEDIUM;
            case MEDIUM -> LOW;
            case LOW -> DISABLED;
            case DISABLED -> HIGH;
        };
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
