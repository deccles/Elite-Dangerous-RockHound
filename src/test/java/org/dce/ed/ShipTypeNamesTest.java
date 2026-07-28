package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShipTypeNamesTest {

    @BeforeEach
    @AfterEach
    void clearLearned() {
        ShipTypeNames.clearLearnedForTests();
    }

    @Test
    void displayMapsJournalIds() {
        assertEquals("Cobra MkIV", ShipTypeNames.display("cobramkiv"));
        assertEquals("Asp Scout", ShipTypeNames.display("asp_scout"));
        assertEquals("Type-10 Defender", ShipTypeNames.display("type9_military"));
        assertEquals("Krait MkII", ShipTypeNames.display("krait_mkii"));
        assertEquals("Panther Clipper MkII", ShipTypeNames.display("panthermkii"));
        assertEquals("Caspian Explorer", ShipTypeNames.display("explorer_nx"));
        assertEquals("Type-11 Prospector", ShipTypeNames.display("lakonminer"));
        assertEquals("Mandalay", ShipTypeNames.display("mandalay"));
    }

    @Test
    void displayKeepsLocalisedNames() {
        assertEquals("Cobra Mk IV", ShipTypeNames.display("Cobra Mk IV"));
    }

    @Test
    void learnOverridesKnownWhenLocalisedProvided() {
        ShipTypeNames.learn("cobramkiv", "Cobra Mk IV");
        assertEquals("Cobra Mk IV", ShipTypeNames.display("cobramkiv"));
        assertEquals("Cobra Mk IV", ShipTypeNames.display("CobraMkIV"));
    }

    @Test
    void preferTypeKeepsInternalOverDisplay() {
        assertEquals("cobramkiv", ShipTypeNames.preferType("cobramkiv", "Cobra Mk IV"));
        assertEquals("cobramkiv", ShipTypeNames.preferType("Cobra Mk IV", "cobramkiv"));
        assertEquals("asp_scout", ShipTypeNames.preferType("asp_scout", ""));
        assertEquals("krait_mkii", ShipTypeNames.preferType("", "krait_mkii"));
    }

    @Test
    void looksInternal() {
        assertTrue(ShipTypeNames.looksInternal("cobramkiv"));
        assertTrue(ShipTypeNames.looksInternal("asp_scout"));
        assertFalse(ShipTypeNames.looksInternal("Cobra Mk IV"));
        assertFalse(ShipTypeNames.looksInternal(""));
    }
}
