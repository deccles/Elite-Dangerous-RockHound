package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Repro: 2026-07-24 Sensor_LightWeight crafts on lakonminer (ship 12) while the session goal
 * is pinned to Anaconda (ship 7).
 */
class EngineeringGoalProgressSensorsJournalTest {

    private static EngineeringDatabase db;
    private final EliteLogParser parser = new EliteLogParser();

    private static final String CRAFT_G3_DONE = """
            {
              "timestamp": "2026-07-24T15:45:48Z",
              "event": "EngineerCraft",
              "Slot": "Radar",
              "Module": "int_sensors_size3_class2",
              "Ingredients": [{"Name": "iron", "Count": 1}],
              "Engineer": "Etienne Dorn",
              "BlueprintName": "Sensor_LightWeight",
              "Level": 3,
              "Quality": 1.0
            }
            """;

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
    }

    @Test
    void sensorLightWeight_resolvesToLightWeightScanner() {
        Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
                EngineeringJournalBlueprintResolver.resolve(
                        "Radar", "int_sensors_size3_class2", "Sensor_LightWeight", db);
        assertTrue(resolved.isPresent());
        assertEquals("Sensors", resolved.get().moduleType());
        assertEquals("Light Weight Scanner", resolved.get().blueprintName());
    }

    @Test
    void craftOnMiner_doesNotAdvanceAnacondaPinnedGoal() {
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(anacondaSensorsGoal(2));

        EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord(CRAFT_G3_DONE);
        assertFalse(EngineeringGoalProgress.applyCraft(goals, craft, db, 12L),
                "Anaconda-pinned Sensors goal must not move when crafting on lakonminer");
        assertEquals(2, goals.get(0).getFromGrade());
    }

    @Test
    void craftOnMatchingShip_advancesToGrade3ButNotCompleteForG5Target() {
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(anacondaSensorsGoal(2));

        EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord(CRAFT_G3_DONE);
        assertTrue(EngineeringGoalProgress.applyCraft(goals, craft, db, 7L));
        assertEquals(3, goals.get(0).getFromGrade());
        assertFalse(goals.get(0).isComplete(),
                "G3@1.0 is not Complete when target grade is 5");
    }

    @Test
    void craftOnMatchingShip_withTargetG3_marksComplete() {
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(new EngineeringGoal(
                "sensors-light-weight-scanner-g5",
                "Sensors",
                "Light Weight Scanner",
                2,
                0,
                3,
                "",
                GoalPriority.LOW,
                false,
                1,
                0,
                7L,
                "Anaconda · Exception Handler",
                true));

        EngineerCraftEvent craft = (EngineerCraftEvent) parser.parseRecord(CRAFT_G3_DONE);
        assertTrue(EngineeringGoalProgress.applyCraft(goals, craft, db, 7L));
        assertEquals(3, goals.get(0).getFromGrade());
        assertTrue(goals.get(0).isComplete());
    }

    private static EngineeringGoal anacondaSensorsGoal(int fromGrade) {
        return new EngineeringGoal(
                "sensors-light-weight-scanner-g5",
                "Sensors",
                "Light Weight Scanner",
                fromGrade,
                0,
                5,
                "",
                GoalPriority.LOW,
                false,
                1,
                0,
                7L,
                "Anaconda · Exception Handler",
                true);
    }
}
