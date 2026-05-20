package org.dce.ed.systemmap;

/**
 * Whether the system plan map uses schematic layout (readable, compressed wide binaries) or journal Kepler
 * positions at true scale (accurate ruler distances in the map plane).
 */
public enum MapScaleMode {
    SCHEMATIC,
    TRUE_SCALE;

    public boolean trueScale() {
        return this == TRUE_SCALE;
    }

    public static MapScaleMode fromPrefsString(String raw) {
        if (raw == null || raw.isBlank()) {
            return SCHEMATIC;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            if ("true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim())) {
                return TRUE_SCALE;
            }
            return SCHEMATIC;
        }
    }

    public String toPrefsString() {
        return name();
    }
}
