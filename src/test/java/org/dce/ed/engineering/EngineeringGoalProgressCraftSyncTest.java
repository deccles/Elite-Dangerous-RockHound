package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EngineeringGoalProgressCraftSyncTest {

    private static EngineeringDatabase db;
    private final EliteLogParser parser = new EliteLogParser();

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
    }

    @Test
    void applyCraft_advancesProgressEvenWhenIngredientCountsDifferFromCatalog() {
        // Journal uses names that resolve, but omit a catalog ingredient — must still advance.
        String json = """
                {
                  "timestamp": "2026-07-13T20:00:00Z",
                  "event": "EngineerCraft",
                  "Slot": "PowerDistributor",
                  "Module": "int_powerdistributor_size8_class5",
                  "Ingredients": [
                    {
                      "Name": "legacyfirmware",
                      "Name_Localised": "Specialised Legacy Firmware",
                      "Count": 1
                    }
                  ],
                  "Engineer": "The Dweller",
                  "BlueprintName": "PowerDistributor_HighFrequency",
                  "Level": 1,
                  "Quality": 0.2
                }
                """;
        EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord(json);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "power-distributor-charge-enhanced-g5",
                "Power Distributor",
                "Charge Enhanced",
                0,
                0,
                5,
                null));

        assertTrue(EngineeringGoalProgress.applyCraft(goals, craft, db));
        assertEquals(0, goals.get(0).getFromGrade());
        assertEquals(1, goals.get(0).getCraftsAtCurrentGrade());
    }

    @Test
    void applyLoadout_usesQualityForInProgressGradeNotCompletedLevel() {
        String json = """
                {
                  "timestamp": "2026-07-13T20:05:00Z",
                  "event": "Loadout",
                  "Ship": "anaconda",
                  "ShipID": 1,
                  "Modules": [
                    {
                      "Slot": "PowerDistributor",
                      "Item": "int_powerdistributor_size8_class5",
                      "On": true,
                      "Priority": 0,
                      "Health": 1.0,
                      "Engineering": {
                        "Engineer": "The Dweller",
                        "BlueprintName": "PowerDistributor_HighFrequency",
                        "Level": 2,
                        "Quality": 0.4
                      }
                    }
                  ]
                }
                """;
        LoadoutEvent loadout = (LoadoutEvent) parser.parseRecord(json);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "power-distributor-charge-enhanced-g5",
                "Power Distributor",
                "Charge Enhanced",
                0,
                0,
                5,
                null));

        assertTrue(EngineeringGoalProgress.applyLoadout(goals, loadout, db));
        // Level 2 @ 40% quality ⇒ G1 complete, 2/5 rolls into G2 — not G2 complete.
        assertEquals(1, goals.get(0).getFromGrade());
        assertEquals(2, goals.get(0).getCraftsAtCurrentGrade());
    }

    @Test
    void applyLoadout_doesNotRegressCraftTrackingAheadOfLoadout() {
        String json = """
                {
                  "timestamp": "2026-07-13T20:06:00Z",
                  "event": "Loadout",
                  "Ship": "anaconda",
                  "ShipID": 1,
                  "Modules": [
                    {
                      "Slot": "PowerDistributor",
                      "Item": "int_powerdistributor_size8_class5",
                      "On": true,
                      "Priority": 0,
                      "Health": 1.0,
                      "Engineering": {
                        "Engineer": "The Dweller",
                        "BlueprintName": "PowerDistributor_HighFrequency",
                        "Level": 2,
                        "Quality": 0.4
                      }
                    }
                  ]
                }
                """;
        LoadoutEvent loadout = (LoadoutEvent) parser.parseRecord(json);
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "power-distributor-charge-enhanced-g5",
                "Power Distributor",
                "Charge Enhanced",
                1,
                3,
                5,
                null));

        EngineeringGoalProgress.applyLoadout(goals, loadout, db);
        assertEquals(1, goals.get(0).getFromGrade());
        assertEquals(3, goals.get(0).getCraftsAtCurrentGrade());
    }
}
