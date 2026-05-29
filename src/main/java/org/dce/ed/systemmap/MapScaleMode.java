package org.dce.ed.systemmap;

/**
 * System plan map always uses journal Kepler positions at true scale (accurate ruler distances in the map plane).
 */
public enum MapScaleMode {
    TRUE_SCALE;

    public boolean trueScale() {
        return true;
    }

    public static MapScaleMode fromPrefsString(String raw) {
        return TRUE_SCALE;
    }

    public String toPrefsString() {
        return name();
    }
}
