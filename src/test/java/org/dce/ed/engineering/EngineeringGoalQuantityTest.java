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

    @Test
    void withUserSettings_raisingTargetClearsCompletedUnits() {
        EngineeringGoal g4Complete = new EngineeringGoal(
                "id", "Pulse Laser", "Efficient Weapon", 4, 0, 4, "",
                true, false, 3, 3);
        assertTrue(g4Complete.isComplete());

        EngineeringGoal g5 = g4Complete.withUserSettings(5, "", 3);
        assertEquals(5, g5.getTargetGrade());
        assertEquals(4, g5.getFromGrade());
        assertEquals(0, g5.getCompletedUnits());
        assertFalse(g5.isComplete());
    }

    @Test
    void withUserSettings_raisingTargetIncreasesMaterialNeed() {
        BlueprintGrade g5 = db.getAllBlueprints().stream()
                .filter(b -> "Shield Booster".equals(b.getModuleType()))
                .filter(b -> "Heavy Duty".equals(b.getName()))
                .filter(b -> b.getGrade() == 5)
                .findFirst()
                .orElseThrow();

        EngineeringGoal atG3 = new EngineeringGoal(
                g5.getId(), "Shield Booster", "Heavy Duty", 3, 0, 3, "",
                true, false, 4, 4);
        assertTrue(atG3.isComplete());
        assertTrue(planner.materialsForGoal(atG3).isEmpty());

        EngineeringGoal raised = atG3.withUserSettings(5, "", 4);
        Map<String, Integer> mats = planner.materialsForGoal(raised);
        assertFalse(raised.isComplete());
        assertFalse(mats.isEmpty(), "G3→G5 must add materials for remaining grades");
        // G4 and G5 still needed on the in-progress unit; remaining grades include higher-tier mats.
        assertTrue(mats.values().stream().mapToInt(Integer::intValue).sum() > 0);
    }

    @Test
    void withUserSettings_loweringTargetKeepsUnitsWhenAlreadyPastNewGrade() {
        EngineeringGoal g5Done = new EngineeringGoal(
                "id", "Multi-cannon", "Overcharged Weapon", 5, 0, 5, "",
                true, false, 5, 5);
        EngineeringGoal g4 = g5Done.withUserSettings(4, "", 5);
        assertEquals(4, g4.getTargetGrade());
        assertEquals(4, g4.getFromGrade());
        assertEquals(5, g4.getCompletedUnits());
        assertTrue(g4.isComplete());
    }

    @Test
    void materialsForGoal_multiUnitExperimentalSwapAtG5DoesNotRebuyGrades() {
        BlueprintGrade autoLoader = db.findById("multi-cannon-auto-loader-experimental").orElseThrow();
        BlueprintGrade g5 = db.gradesFor("Multi-cannon", "Efficient Weapon").stream()
                .filter(b -> b.getGrade() == 5 && !b.isExperimental())
                .findFirst()
                .orElseThrow();

        EngineeringGoal threeAtG5NeedExp = new EngineeringGoal(
                g5.getId(),
                "Multi-cannon",
                "Efficient Weapon",
                5,
                0,
                5,
                autoLoader.getId(),
                true,
                false,
                3,
                0);

        Map<String, Integer> mats = planner.materialsForGoal(threeAtG5NeedExp);
        assertFalse(mats.isEmpty());
        for (MaterialRequirement req : autoLoader.getMaterials()) {
            assertEquals(req.getCount() * 3, mats.getOrDefault(req.getKey(), 0),
                    () -> "expected 3× " + req.getKey() + " for Auto Loader only");
        }
        // No grade-roll mats (e.g. Efficient G1 scrap) should appear.
        assertEquals(autoLoader.getMaterials().size(), mats.size(),
                "G5 experimental-only swap must not re-add grade materials for sibling units");
    }
}
