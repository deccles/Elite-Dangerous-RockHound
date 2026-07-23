package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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
        assertTrue(armour.contains("#6DFF6D") || armour.contains("#FF0000"),
                "expected good/bad modifier colors: " + armour);
    }

    @Test
    void experimentalTooltip_includesModifiers() {
        String tip = db.experimentalEffectTooltip("Armour", "Heavy Duty", "Deep Plating");
        assertNotNull(tip, "Deep Plating tooltip");
        assertTrue(tip.toLowerCase().contains("hull") || tip.toLowerCase().contains("resist"), tip);

        String mass = db.experimentalEffectTooltip("Frame Shift Drive", "Increased FSD Range", "Mass Manager");
        assertNotNull(mass, "Mass Manager tooltip");
        assertTrue(mass.toLowerCase().contains("mass") || mass.toLowerCase().contains("optim"), mass);
        assertTrue(mass.contains("#6DFF6D") || mass.contains("#FF0000"),
                "expected good/bad modifier colors: " + mass);
    }

    @Test
    void formatEffectTooltip_colorsGoodAndBadModifiers() {
        BlueprintGrade bp = new BlueprintGrade(
                "id", 0, "Armour", "Test", 1, false, "Desc", "",
                List.of(), List.of(),
                List.of(
                        new BlueprintModifier("Hull Strength", "+10%", true),
                        new BlueprintModifier("Kinetic Resistance", "-5%", false),
                        new BlueprintModifier("Mass", "+8%", false),
                        new BlueprintModifier("Armor", "+12%", true)));
        String tip = EngineeringDatabase.formatEffectTooltip(bp);
        assertNotNull(tip);
        assertTrue(tip.contains("background-color:#161616"), tip);
        int bad1 = tip.indexOf("<font color='#FF0000'>Kinetic Resistance -5%</font>");
        int bad2 = tip.indexOf("<font color='#FF0000'>Mass +8%</font>");
        int good1 = tip.indexOf("<font color='#6DFF6D'>Hull Strength +10%</font>");
        int good2 = tip.indexOf("<font color='#6DFF6D'>Armor +12%</font>");
        assertTrue(bad1 >= 0 && bad2 >= 0 && good1 >= 0 && good2 >= 0, tip);
        assertTrue(bad1 < bad2 && bad2 < good1 && good1 < good2,
                "expected reds then greens in original relative order: " + tip);
    }
}
