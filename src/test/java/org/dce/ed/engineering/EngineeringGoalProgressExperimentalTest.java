package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EngineeringGoalProgressExperimentalTest {

    private static EngineeringDatabase db;
    private static EngineeringPlanner planner;
    private final EliteLogParser parser = new EliteLogParser();

    private static final String FAST_CHARGE_CRAFT = """
            {
              "timestamp": "2026-07-13T15:48:23Z",
              "event": "EngineerCraft",
              "Slot": "Slot01_Size7",
              "Module": "int_shieldgenerator_size7_class3_fast",
              "BlueprintName": "ShieldGenerator_Thermic",
              "ApplyExperimentalEffect": "special_shield_regenerative",
              "ExperimentalEffect": "special_shield_regenerative",
              "ExperimentalEffect_Localised": "Fast Charge",
              "Ingredients": [
                { "Name": "wornshieldemitters", "Count": 5 },
                { "Name": "uncutfocuscrystals", "Count": 3 },
                { "Name": "compoundshielding", "Count": 1 }
              ],
              "Level": 5
            }
            """;

    private static final String THERMO_BLOCK_CRAFT = """
            {
              "timestamp": "2026-07-12T18:37:59Z",
              "event": "EngineerCraft",
              "Slot": "ShieldGenerator",
              "Module": "int_shieldgenerator_size5_class5",
              "BlueprintName": "ShieldGenerator_Thermal",
              "ExperimentalEffect": "special_shieldgenerator_thermoblock",
              "ExperimentalEffect_Localised": "Thermo Block",
              "Ingredients": [
                { "Name": "wornshieldemitters", "Count": 5 },
                { "Name": "uncutfocuscrystals", "Count": 3 },
                { "Name": "heatvanes", "Count": 1 }
              ],
              "Level": 5
            }
            """;

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
        planner = new EngineeringPlanner(db);
    }

    @Test
    void parse_engineerCraft_readsExperimentalEffectFields() {
        var event = parser.parseRecord(THERMO_BLOCK_CRAFT);
        assertInstanceOf(EngineerCraftEvent.class, event);
        EngineerCraftEvent craft = (EngineerCraftEvent) event;
        assertEquals("special_shieldgenerator_thermoblock", craft.getExperimentalEffect());
        assertEquals("Thermo Block", craft.getExperimentalEffectLocalised());
    }

    @Test
    void applyCraft_marksFastChargeCompleteFromThermicJournalName() {
        EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord(FAST_CHARGE_CRAFT);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "shield-generator-thermal-resistant-shields-g5",
                "Shield Generator",
                "Thermal Resistant Shields",
                5,
                0,
                5,
                "shield-generator-fast-charge-experimental"));

        assertTrue(EngineeringGoalProgress.applyCraft(goals, craft, db));
        assertTrue(goals.get(0).isExperimentalApplied());
        assertTrue(goals.get(0).isComplete());
    }

    @Test
    void applyCraft_marksExperimentalAppliedForMatchingGoal() {
        EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord(THERMO_BLOCK_CRAFT);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "shield-generator-thermal-resistant-shields-g5",
                "Shield Generator",
                "Thermal Resistant Shields",
                5,
                0,
                5,
                "shield-generator-thermo-block-experimental"));

        assertTrue(EngineeringGoalProgress.applyCraft(goals, craft, db));
        assertTrue(goals.get(0).isExperimentalApplied());
        assertTrue(goals.get(0).isComplete());
    }

    @Test
    void completedGoal_dropsExperimentalMaterialsFromPlanner() {
        EngineeringGoal goal = new EngineeringGoal(
                "shield-generator-thermal-resistant-shields-g5",
                "Shield Generator",
                "Thermal Resistant Shields",
                5,
                0,
                5,
                "shield-generator-thermo-block-experimental",
                true,
                true);

        assertTrue(goal.isComplete());
        assertTrue(planner.materialsForGoal(goal).isEmpty());
        assertTrue(planner.isGoalReady(goal, Map.of()));
        assertEquals(GoalReadiness.READY, planner.goalReadiness(goal, Map.of(), Map.of()));
    }

    @Test
    void incompleteExperimentalGoal_stillRequiresExperimentalMaterials() {
        EngineeringGoal goal = new EngineeringGoal(
                "shield-generator-thermal-resistant-shields-g5",
                "Shield Generator",
                "Thermal Resistant Shields",
                5,
                0,
                5,
                "shield-generator-thermo-block-experimental");

        Map<String, Integer> required = planner.materialsForGoal(goal);
        assertFalse(required.isEmpty());
        assertTrue(required.containsKey("wornshieldemitters"));
        assertFalse(goal.isComplete());
    }
}
