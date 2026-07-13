package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

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
}
