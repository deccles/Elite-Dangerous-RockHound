package org.dce.systemmodel.model;

/**
 * Separates journal {@code Scan} body ids from {@code ScanBaryCentre} null ids in {@link HierarchyGraph}
 * (planet 5 and {@code Null:5} share the same journal number).
 */
public final class HierarchyKeys {

    /** Matches {@link org.dce.ed.util.SystemOrbitGeometry#PLANET_BINARY_BARYCENTRE_MAP_KEY_BASE}. */
    public static final int BARY_MAP_KEY_BASE = -50_000;

    private HierarchyKeys() {
    }

    public static int baryMapKey(int journalNullId) {
        return BARY_MAP_KEY_BASE - journalNullId;
    }

    public static boolean isBaryMapKey(int mapKey) {
        return mapKey <= BARY_MAP_KEY_BASE;
    }

    public static int journalNullFromBaryMapKey(int mapKey) {
        return BARY_MAP_KEY_BASE - mapKey;
    }
}
