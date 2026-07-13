package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
