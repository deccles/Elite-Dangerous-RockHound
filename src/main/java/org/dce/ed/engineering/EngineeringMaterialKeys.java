package org.dce.ed.engineering;

import java.util.Locale;
import java.util.Map;

import org.dce.ed.market.GalacticAveragePrices;

/**
 * Canonical journal keys for engineering materials (handles spelling variants).
 */
public final class EngineeringMaterialKeys {

    private static final Map<String, String> CANONICAL = Map.of(
            "sulfur", "sulphur",
            "legacyfirmware", "specialisedlegacyfirmware",
            "consumerfirmware", "modifiedconsumerfirmware");

    private EngineeringMaterialKeys() {
    }

    /** Normalizes journal / blueprint keys to the canonical form used in bundled data. */
    public static String canonicalKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String norm = GalacticAveragePrices.normalizeMaterialKey(key);
        return CANONICAL.getOrDefault(norm, norm);
    }

    /**
     * Resolves a journal material name to the bundled catalog key, using localised display name when needed.
     */
    public static String resolveKey(String journalName, String localisedName, EngineeringDatabase database) {
        String canon = canonicalKey(journalName);
        if (database != null && database.material(canon).isPresent()) {
            return canon;
        }
        if (localisedName != null && !localisedName.isBlank() && database != null) {
            for (EngineeringMaterial material : database.getAllMaterials()) {
                if (material.getName().equalsIgnoreCase(localisedName.trim())) {
                    return material.getKey();
                }
            }
        }
        return canon;
    }

    /** Counts inventory for a material, including known spelling aliases. */
    public static int countInInventory(Map<String, Integer> inventory, String materialKey) {
        if (inventory == null || inventory.isEmpty()) {
            return 0;
        }
        String canonical = canonicalKey(materialKey);
        int total = 0;
        for (Map.Entry<String, Integer> e : inventory.entrySet()) {
            if (canonicalKey(e.getKey()).equals(canonical)) {
                total += e.getValue() != null ? e.getValue() : 0;
            }
        }
        return total;
    }
}
