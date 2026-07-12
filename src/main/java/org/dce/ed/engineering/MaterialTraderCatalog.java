package org.dce.ed.engineering;

import java.util.Set;

/**
 * Which engineering materials appear on Horizons material-trader exchange tables.
 * Guardian and Thargoid items are encoded/manufactured in the journal but cannot be swapped at traders.
 */
public final class MaterialTraderCatalog {

    private static final Set<String> RAW_SUBTYPES = Set.of(
            "Category1", "Category2", "Category3", "Category4",
            "Category5", "Category6", "Category7");

    private static final Set<String> MANUFACTURED_SUBTYPES = Set.of(
            "Alloys", "Capacitors", "Chemical", "Composite", "Conductive",
            "Crystals", "Heat", "MechanicalComponents", "Shielding", "Thermic");

    private static final Set<String> ENCODED_SUBTYPES = Set.of(
            "DataArchives", "EmissionData", "EncodedFirmware", "EncryptionFiles",
            "ShieldData", "WakeScans");

    private MaterialTraderCatalog() {
    }

    public static boolean isTradeableAtMaterialTrader(EngineeringMaterial material) {
        if (material == null) {
            return false;
        }
        String type = material.getType();
        String subtype = material.getSubtype();
        if (type == null || type.isBlank() || subtype == null || subtype.isBlank()) {
            return false;
        }
        if (subtype.regionMatches(true, 0, "Guardian", 0, "Guardian".length())
                || subtype.regionMatches(true, 0, "Thargoid", 0, "Thargoid".length())) {
            return false;
        }
        return switch (type) {
            case "Raw" -> RAW_SUBTYPES.contains(subtype);
            case "Manufactured" -> MANUFACTURED_SUBTYPES.contains(subtype);
            case "Encoded" -> ENCODED_SUBTYPES.contains(subtype);
            default -> false;
        };
    }
}
