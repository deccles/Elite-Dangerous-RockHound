package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EngineeringGoalQuantityTest {

    private static EngineeringDatabase db;
    private static EngineeringPlanner planner;

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
        planner = new EngineeringPlanner(db);
    }

    @Test
    void quantity_multipliesMaterialRequirements() {
        BlueprintGrade g5 = db.getAllBlueprints().stream()
                .filter(b -> "Power Distributor".equals(b.getModuleType()))
                .filter(b -> "Charge Enhanced".equals(b.getName()))
                .filter(b -> b.getGrade() == 5)
                .findFirst()
                .orElseThrow();

        EngineeringGoal one = new EngineeringGoal(
                g5.getId(), g5.getModuleType(), g5.getName(), 0, 5, "");
        EngineeringGoal four = new EngineeringGoal(
                g5.getId(), g5.getModuleType(), g5.getName(), 0, 0, 5, "",
                true, false, 4, 0);

        Map<String, Integer> matsOne = planner.materialsForGoal(one);
        Map<String, Integer> matsFour = planner.materialsForGoal(four);
        assertFalse(matsOne.isEmpty());
        for (Map.Entry<String, Integer> e : matsOne.entrySet()) {
            assertEquals(e.getValue() * 4, matsFour.getOrDefault(e.getKey(), 0),
                    () -> "material " + e.getKey() + " should scale with quantity");
        }
    }

    @Test
    void isComplete_whenAllUnitsFinished() {
        EngineeringGoal goal = new EngineeringGoal(
                "id", "Pulse Laser", "Overcharged", 5, 0, 5, "",
                true, true, 3, 2);
        assertTrue(goal.isComplete());
        assertEquals(0, goal.remainingUnits());
    }

    @Test
    void isNotComplete_whenUnitsRemain() {
        EngineeringGoal goal = new EngineeringGoal(
                "id", "Pulse Laser", "Overcharged", 3, 2, 5, "",
                true, false, 3, 1);
        assertFalse(goal.isComplete());
        assertEquals(2, goal.remainingUnits());
    }
}
