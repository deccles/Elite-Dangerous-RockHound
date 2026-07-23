package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EngineeringEffectTooltipTest {

    private static EngineeringDatabase db;

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
    }

    @Test
    void blueprintTooltip_includesModifiers() {
        String tip = db.blueprintEffectTooltip("Frame Shift Drive", "Increased FSD Range", 5);
        assertNotNull(tip, "FSD Increased Range G5 tooltip");
        assertTrue(tip.toLowerCase().contains("mass") || tip.toLowerCase().contains("optim"), tip);

        String armour = db.blueprintEffectTooltip("Armour", "Heavy Duty", 1);
        assertNotNull(armour, "Armour Heavy Duty G1 tooltip");
        assertTrue(armour.toLowerCase().contains("hull") || armour.toLowerCase().contains("kinetic"), armour);
    }

    @Test
    void experimentalTooltip_includesModifiers() {
        String tip = db.experimentalEffectTooltip("Armour", "Heavy Duty", "Deep Plating");
        assertNotNull(tip, "Deep Plating tooltip");
        assertTrue(tip.toLowerCase().contains("hull") || tip.toLowerCase().contains("resist"), tip);

        String mass = db.experimentalEffectTooltip("Frame Shift Drive", "Increased FSD Range", "Mass Manager");
        assertNotNull(mass, "Mass Manager tooltip");
        assertTrue(mass.toLowerCase().contains("mass") || mass.toLowerCase().contains("optim"), mass);
    }
}
