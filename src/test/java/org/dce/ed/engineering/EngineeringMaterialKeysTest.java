package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class EngineeringMaterialKeysTest {

    @Test
    void canonicalKey_mapsSulfurToSulphur() {
        assertEquals("sulphur", EngineeringMaterialKeys.canonicalKey("sulfur"));
        assertEquals("sulphur", EngineeringMaterialKeys.canonicalKey("Sulphur"));
    }

    @Test
    void canonicalKey_mapsLegacyFirmwareAlias() {
        assertEquals("specialisedlegacyfirmware", EngineeringMaterialKeys.canonicalKey("legacyfirmware"));
    }

    @Test
    void canonicalKey_mapsConsumerFirmwareAlias() {
        assertEquals("modifiedconsumerfirmware", EngineeringMaterialKeys.canonicalKey("consumerfirmware"));
    }

    @Test
    void resolveKey_usesLocalisedNameWhenJournalKeyUnknown() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        assertEquals("specialisedlegacyfirmware",
                EngineeringMaterialKeys.resolveKey("legacyfirmware", "Specialised Legacy Firmware", db));
        assertEquals("modifiedconsumerfirmware",
                EngineeringMaterialKeys.resolveKey("consumerfirmware", "Modified Consumer Firmware", db));
    }

    @Test
    void countInInventory_sumsSpellingVariants() {
        Map<String, Integer> inv = Map.of("sulfur", 2, "sulphur", 3);
        assertEquals(5, EngineeringMaterialKeys.countInInventory(inv, "sulphur"));
    }

    @Test
    void canonicalKey_mapsInaraCompositeAndScanAliases() {
        assertEquals("fedproprietarycomposites",
                EngineeringMaterialKeys.canonicalKey("proprietarycomposites"));
        assertEquals("fedcorecomposites", EngineeringMaterialKeys.canonicalKey("corecomposites"));
        assertEquals("fedcorecomposites",
                EngineeringMaterialKeys.canonicalKey("coredynamicscomposites"));
        assertEquals("scandatabanks",
                EngineeringMaterialKeys.canonicalKey("classifiedscandatabanks"));
    }

    @Test
    void cargoRackExtendedG5_usesCatalogProprietaryComposites() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        BlueprintGrade bp = db.findById("cargo-rack-extended-g5").orElseThrow();
        assertTrue(bp.getMaterials().stream()
                .anyMatch(m -> "fedproprietarycomposites".equals(m.getKey())));
        assertEquals("Proprietary Composites", db.materialDisplayName("proprietarycomposites"));
        assertEquals("Manufactured", db.material("proprietarycomposites").orElseThrow().getType());
    }

    @Test
    void everyBlueprintMaterialResolvesToCatalog() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (BlueprintGrade bp : db.getAllBlueprints()) {
            for (MaterialRequirement req : bp.getMaterials()) {
                if (db.material(req.getKey()).isEmpty()) {
                    missing.add(bp.getId() + " " + req.getKey());
                }
            }
        }
        assertEquals(java.util.List.of(), missing);
    }

    @Test
    void canonicalKey_mapsMercCoinToMercCoins() {
        assertEquals("merccoins", EngineeringMaterialKeys.canonicalKey("merccoin"));
        assertEquals("merccoins", EngineeringMaterialKeys.canonicalKey("MercCoins"));
    }

    @Test
    void isMercCoins_acceptsAliases() {
        assertTrue(EngineeringMaterialKeys.isMercCoins("merccoin"));
        assertTrue(EngineeringMaterialKeys.isMercCoins("MercCoins"));
        assertFalse(EngineeringMaterialKeys.isMercCoins("mercury"));
        assertFalse(EngineeringMaterialKeys.isMercCoins("phosphorus"));
    }

    @Test
    void blueprintRequiresMercCoins_detectsScoopRateEnhanced() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        assertTrue(db.blueprintRequiresMercCoins("Fuel Scoop", "Scoop Rate Enhanced"));
        assertFalse(db.blueprintRequiresMercCoins("Frame Shift Drive", "Increased FSD Range"));
    }
}
