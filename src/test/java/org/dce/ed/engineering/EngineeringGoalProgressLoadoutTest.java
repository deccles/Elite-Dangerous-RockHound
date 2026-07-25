package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EngineeringGoalProgressLoadoutTest {

    private static EngineeringDatabase db;
    private final EliteLogParser parser = new EliteLogParser();

    private static final String LOADOUT_WITH_THERMIC_FAST_CHARGE = """
            {
              "timestamp": "2026-07-13T15:50:00Z",
              "event": "Loadout",
              "Ship": "anaconda",
              "ShipID": 7,
              "Modules": [
                {
                  "Slot": "Slot01_Size7",
                  "Item": "int_shieldgenerator_size7_class3_fast",
                  "On": true,
                  "Priority": 0,
                  "Health": 1.0,
                  "Engineering": {
                    "Engineer": "Lei Cheung",
                    "BlueprintName": "ShieldGenerator_Thermic",
                    "Level": 5,
                    "Quality": 1.0,
                    "ExperimentalEffect": "special_shield_regenerative",
                    "ExperimentalEffect_Localised": "Fast Charge"
                  }
                }
              ]
            }
            """;

    private static final String LOADOUT_WITH_THERMO_BLOCK = """
            {
              "timestamp": "2026-07-12T18:40:00Z",
              "event": "Loadout",
              "Ship": "cutter",
              "ShipID": 1,
              "Modules": [
                {
                  "Slot": "ShieldGenerator",
                  "Item": "int_shieldgenerator_size8_class5",
                  "On": true,
                  "Priority": 0,
                  "Health": 1.0,
                  "Engineering": {
                    "Engineer": "Lei Cheung",
                    "BlueprintName": "ShieldGenerator_Thermal",
                    "Level": 5,
                    "Quality": 1.0,
                    "ExperimentalEffect": "special_shieldgenerator_thermoblock",
                    "ExperimentalEffect_Localised": "Thermo Block"
                  }
                }
              ]
            }
            """;

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
    }

    @Test
    void applyLoadout_marksFastChargeGoalCompleteFromThermicJournalName() {
        LoadoutEvent loadout = (LoadoutEvent) parser.parseRecord(LOADOUT_WITH_THERMIC_FAST_CHARGE);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "shield-generator-thermal-resistant-shields-g5",
                "Shield Generator",
                "Thermal Resistant Shields",
                0,
                0,
                5,
                "shield-generator-fast-charge-experimental"));

        assertTrue(EngineeringGoalProgress.applyLoadout(goals, loadout, db));
        assertTrue(goals.get(0).isExperimentalApplied());
        assertTrue(goals.get(0).isComplete());
    }

    @Test
    void applyLoadout_marksCompletedGoalFromEngineeredModule() {
        LoadoutEvent loadout = (LoadoutEvent) parser.parseRecord(LOADOUT_WITH_THERMO_BLOCK);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "shield-generator-thermal-resistant-shields-g5",
                "Shield Generator",
                "Thermal Resistant Shields",
                0,
                0,
                5,
                "shield-generator-thermo-block-experimental"));

        assertTrue(EngineeringGoalProgress.applyLoadout(goals, loadout, db));
        assertTrue(goals.get(0).isExperimentalApplied());
        assertTrue(goals.get(0).isComplete());
    }

    @Test
    void applyLoadout_g5WithoutExperimental_doesNotMarkDeepPlatingDone() {
        // Repro: sticky session had experimentalApplied/completedUnits while Armour was only
        // Heavy Duty G5 with no Deep Plating — Materials Required omitted Mechanical Equipment.
        String loadoutJson = """
                {
                  "timestamp": "2026-07-24T21:49:42Z",
                  "event": "Loadout",
                  "Ship": "anaconda",
                  "ShipID": 7,
                  "Modules": [
                    {
                      "Slot": "Armour",
                      "Item": "anaconda_armour_reactive",
                      "On": true,
                      "Priority": 1,
                      "Health": 1.0,
                      "Engineering": {
                        "Engineer": "Selene Jean",
                        "BlueprintName": "Armour_HeavyDuty",
                        "Level": 5,
                        "Quality": 1.0
                      }
                    }
                  ]
                }
                """;
        LoadoutEvent loadout = (LoadoutEvent) parser.parseRecord(loadoutJson);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "armour-heavy-duty-g5",
                "Armour",
                "Heavy Duty",
                5,
                0,
                5,
                "armour-deep-plating-experimental",
                GoalPriority.HIGH,
                true,
                1,
                1,
                7L,
                "Anaconda · Exception Handler",
                true));

        assertTrue(EngineeringGoalProgress.applyLoadout(goals, loadout, db));
        EngineeringGoal goal = goals.get(0);
        assertTrue(!goal.isExperimentalApplied(), "Deep Plating must not be sticky-complete");
        assertTrue(!goal.isComplete(), "goal must stay incomplete until experimental is applied");
        assertEquals(5, goal.getFromGrade());

        EngineeringPlanner planner = new EngineeringPlanner(db);
        Map<String, Integer> need = planner.materialsForGoal(goal);
        assertTrue(need.keySet().stream().anyMatch(k -> k.equalsIgnoreCase("mechanicalequipment")),
                "Deep Plating materials must appear in Need: " + need);
    }

    @Test
    void applyLoadout_multiUnit_usesLeastProgressedIncompleteModuleForSharedGrade() {
        // Repro: one HRP at G5 + one stock G0 with qty 2 used to set fromGrade=5 from the best
        // module, so Materials Required dropped to experimental-only for both.
        String loadoutJson = """
                {
                  "timestamp": "2026-07-24T22:00:00Z",
                  "event": "Loadout",
                  "Ship": "anaconda",
                  "ShipID": 7,
                  "Modules": [
                    {
                      "Slot": "Slot07_Size5",
                      "Item": "int_hullreinforcement_size5_class2",
                      "On": true,
                      "Priority": 1,
                      "Health": 1.0,
                      "Engineering": {
                        "Engineer": "Selene Jean",
                        "BlueprintName": "HullReinforcement_HeavyDuty",
                        "Level": 5,
                        "Quality": 1.0
                      }
                    },
                    {
                      "Slot": "Slot10_Size4",
                      "Item": "int_hullreinforcement_size4_class2",
                      "On": true,
                      "Priority": 1,
                      "Health": 1.0
                    }
                  ]
                }
                """;
        LoadoutEvent loadout = (LoadoutEvent) parser.parseRecord(loadoutJson);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "hull-reinforcement-package-heavy-duty-hull-reinforcement-g5",
                "Hull Reinforcement Package",
                "Heavy Duty Hull Reinforcement",
                5,
                0,
                5,
                "hull-reinforcement-package-deep-plating-experimental",
                GoalPriority.MEDIUM,
                false,
                2,
                0,
                7L,
                "Anaconda · Exception Handler",
                true));

        assertTrue(EngineeringGoalProgress.applyLoadout(goals, loadout, db));
        EngineeringGoal goal = goals.get(0);
        assertEquals(0, goal.getFromGrade(),
                "shared progress must follow the least progressed incomplete module");
        assertEquals(0, goal.getCompletedUnits());

        EngineeringPlanner planner = new EngineeringPlanner(db);
        Map<String, Integer> need = planner.materialsForGoal(goal);
        assertTrue(need.getOrDefault("carbon", 0) >= 15,
                "G0 sibling must keep full grade Need in the estimate: " + need);
    }
}
