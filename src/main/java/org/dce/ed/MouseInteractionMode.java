package org.dce.ed;

/**
 * Overlay mouse interaction: whole-window clicks, selective clickable regions, or full OS pass-through.
 */
public enum MouseInteractionMode {
    /** Entire overlay receives mouse input. */
    NORMAL,
    /** Chrome + listed tab controls receive clicks; other content passes through to the game. */
    SELECTIVE,
    /** Full Win32 click-through except chrome exceptions; dwell/wheel pollers for overlay actions. */
    FULL_PASS_THROUGH;

    public boolean isPassThroughLike() {
        return this != NORMAL;
    }

    public MouseInteractionMode next() {
        return switch (this) {
            case NORMAL -> SELECTIVE;
            case SELECTIVE -> FULL_PASS_THROUGH;
            case FULL_PASS_THROUGH -> NORMAL;
        };
    }

    public String prefsValue() {
        return switch (this) {
            case NORMAL -> "normal";
            case SELECTIVE -> "selective";
            case FULL_PASS_THROUGH -> "full";
        };
    }

    public static MouseInteractionMode fromPrefsValue(String raw, MouseInteractionMode defaultIfUnset) {
        if (raw == null || raw.isBlank()) {
            return defaultIfUnset;
        }
        return switch (raw.trim().toLowerCase()) {
            case "normal", "off", "false" -> NORMAL;
            case "selective" -> SELECTIVE;
            case "full", "full_pass_through", "passthrough", "pass_through", "true", "on" -> FULL_PASS_THROUGH;
            default -> defaultIfUnset;
        };
    }
}
