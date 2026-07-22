package org.dce.ed.logreader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.dce.ed.engineering.EngineeringDatabase;
import org.dce.ed.engineering.EngineeringGoal;
import org.dce.ed.engineering.EngineeringGoalProgress;
import org.dce.ed.engineering.EngineeringInventoryTracker;
import org.dce.ed.engineering.EngineeringJournalBlueprintResolver;
import org.dce.ed.engineering.EngineeringMaterialKeys;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.junit.jupiter.api.Test;

class EngineerCraftParsingTest {

    private final EliteLogParser parser = new EliteLogParser();

    private static final String SAMPLE = """
            {
              "timestamp": "2026-07-12T18:37:59Z",
              "event": "EngineerCraft",
              "Slot": "PowerDistributor",
              "Module": "int_powerdistributor_size8_class5",
              "Ingredients": [
                {
                  "Name": "legacyfirmware",
                  "Name_Localised": "Specialised Legacy Firmware",
                  "Count": 1
                },
                {
                  "Name": "chemicalprocessors",
                  "Name_Localised": "Chemical Processors",
                  "Count": 1
                }
              ],
              "Engineer": "The Dweller",
              "EngineerID": 300180,
              "BlueprintID": 128673736,
              "BlueprintName": "PowerDistributor_HighFrequency",
              "Level": 2,
              "Quality": 0.400000
            }
            """;

    @Test
    void parse_engineerCraft_readsFieldsAndIngredients() {
        var event = parser.parseRecord(SAMPLE);
        assertInstanceOf(EngineerCraftEvent.class, event);
        EngineerCraftEvent craft = (EngineerCraftEvent) event;
        assertEquals(EliteEventType.ENGINEER_CRAFT, craft.getType());
        assertEquals("PowerDistributor", craft.getSlot());
        assertEquals("PowerDistributor_HighFrequency", craft.getBlueprintName());
        assertEquals(2, craft.getLevel());
        assertEquals(2, craft.getIngredients().size());
        assertEquals("legacyfirmware", craft.getIngredients().get(0).getName());
        assertEquals("Specialised Legacy Firmware", craft.getIngredients().get(0).getNameLocalised());
    }

    @Test
    void resolveKey_mapsLegacyFirmwareToCatalogKey() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        String key = EngineeringMaterialKeys.resolveKey(
                "legacyfirmware", "Specialised Legacy Firmware", db);
        assertEquals("specialisedlegacyfirmware", key);
    }

    @Test
    void journalBlueprintMap_resolvesChargeEnhanced() {
        var resolved = EngineeringJournalBlueprintResolver.resolve(
                "PowerDistributor", "PowerDistributor_HighFrequency", EngineeringDatabase.getInstance());
        assertTrue(resolved.isPresent());
        assertEquals("Power Distributor", resolved.get().moduleType());
        assertEquals("Charge Enhanced", resolved.get().blueprintName());
    }

    @Test
    void applyCraft_advancesGoalFromGradeAndDeductsMaterials() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        var event = parser.parseRecord(SAMPLE);
        assertInstanceOf(EngineerCraftEvent.class, event);
        EngineerCraftEvent craft = (EngineerCraftEvent) event;

        EngineeringInventoryTracker tracker = new EngineeringInventoryTracker();
        tracker.applyEvent(new org.dce.ed.logreader.event.MaterialsEvent(
                craft.getTimestamp(),
                craft.getRawJson(),
                java.util.List.of(),
                java.util.List.of(
                        new org.dce.ed.logreader.event.MaterialStack("specialisedlegacyfirmware", "", 5),
                        new org.dce.ed.logreader.event.MaterialStack("chemicalprocessors", "", 3)),
                java.util.List.of()));

        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "power-distributor-charge-enhanced-g5",
                "Power Distributor",
                "Charge Enhanced",
                0,
                5,
                null));

        assertTrue(EngineeringGoalProgress.applyCraft(goals, craft, db));
        // Level 2 craft with Quality 0.4 ⇒ G1 finished, ~2/5 into G2 on the 5-roll scale.
        assertEquals(1, goals.get(0).getFromGrade());
        assertEquals(2, goals.get(0).getCraftsAtCurrentGrade());

        tracker.applyEvent(craft);
        assertEquals(4, tracker.getCount("specialisedlegacyfirmware"));
        assertEquals(2, tracker.getCount("chemicalprocessors"));
    }
}
