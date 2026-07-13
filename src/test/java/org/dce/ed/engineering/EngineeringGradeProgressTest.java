package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EngineeringGradeProgressTest {

    private static EngineeringDatabase db;
    private static EngineeringPlanner planner;

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
        planner = new EngineeringPlanner(db);
    }

    @Test
    void planner_multipliesMaterialsByFiveRollsPerGrade() {
        BlueprintGrade g1 = db.gradesFor("Power Distributor", "Charge Enhanced").stream()
                .filter(b -> b.getGrade() == 1)
                .findFirst()
                .orElseThrow();

        EngineeringGoal goal = new EngineeringGoal(
                g1.getId(), g1.getModuleType(), g1.getName(), 0, 1, "");

        Map<String, Integer> required = planner.materialsForGoal(goal);
        int firmware = required.getOrDefault("specialisedlegacyfirmware", 0);
        assertEquals(5, firmware, "G1 alone needs 5 rolls");
    }

    @Test
    void planner_g1ThroughG5_multipliesEachGradeByFive() {
        BlueprintGrade g5 = db.gradesFor("Power Distributor", "Charge Enhanced").stream()
                .filter(b -> b.getGrade() == 5)
                .findFirst()
                .orElseThrow();

        EngineeringGoal goal = new EngineeringGoal(
                g5.getId(), g5.getModuleType(), g5.getName(), 0, 5, "");

        Map<String, Integer> required = planner.materialsForGoal(goal);
        int firmware = required.getOrDefault("specialisedlegacyfirmware", 0);
        assertEquals(10, firmware, "G1 and G2 each need 5 firmware");
    }

    @Test
    void afterCraft_fiveRollsAtG1CompletesGrade() {
        EngineeringGoal goal = new EngineeringGoal("id", "Power Distributor", "Charge Enhanced", 0, 5, "");
        for (int i = 0; i < 5; i++) {
            goal = EngineeringGradeProgress.afterCraft(goal, 1);
        }
        assertEquals(1, goal.getFromGrade());
        assertEquals(0, goal.getCraftsAtCurrentGrade());
    }

    @Test
    void rollsRemaining_partialProgressAtCurrentGrade() {
        EngineeringGoal goal = new EngineeringGoal("id", "Mod", "BP", 1, 2, 5, "");
        assertEquals(3, EngineeringGradeProgress.rollsRemainingAtGrade(goal, 2));
        assertEquals(5, EngineeringGradeProgress.rollsRemainingAtGrade(goal, 3));
        assertEquals(0, EngineeringGradeProgress.rollsRemainingAtGrade(goal, 1));
    }

    @Test
    void planner_partialG2Progress_reducesG2MaterialsOnly() {
        EngineeringGoal goal = new EngineeringGoal(
                "power-distributor-charge-enhanced-g5",
                "Power Distributor",
                "Charge Enhanced",
                1,
                2,
                5,
                "");

        Map<String, Integer> required = planner.materialsForGoal(goal);
        int firmware = required.getOrDefault("specialisedlegacyfirmware", 0);
        assertEquals(3, firmware, "only 3 G2 rolls remain and firmware is not used above G2");
        int processors = required.getOrDefault("chemicalprocessors", 0);
        assertEquals(3, processors);
    }

    @Test
    void afterCraft_higherGradeIgnoredWhileCurrentGradeInProgress() {
        EngineeringGoal goal = new EngineeringGoal(
                "power-distributor-charge-enhanced-g5",
                "Power Distributor",
                "Charge Enhanced",
                1,
                2,
                5,
                "");

        EngineeringGoal after = EngineeringGradeProgress.afterCraft(goal, 3);

        assertEquals(1, after.getFromGrade());
        assertEquals(2, after.getCraftsAtCurrentGrade());
    }

    @Test
    void afterCraft_higherGradeSnapsOnlyWhenNoRollsInProgress() {
        EngineeringGoal goal = new EngineeringGoal(
                "power-distributor-charge-enhanced-g5",
                "Power Distributor",
                "Charge Enhanced",
                0,
                0,
                5,
                "");

        EngineeringGoal after = EngineeringGradeProgress.afterCraft(goal, 3);

        assertEquals(2, after.getFromGrade());
        assertEquals(1, after.getCraftsAtCurrentGrade());
    }

    @Test
    void partialG2_includesFirmwareAndProcessorsInShoppingList() {
        EngineeringGoal goal = new EngineeringGoal(
                "power-distributor-charge-enhanced-g5",
                "Power Distributor",
                "Charge Enhanced",
                1,
                2,
                5,
                "");

        List<ShoppingListRow> rows = planner.buildShoppingList(List.of(goal), Map.of());

        assertTrue(rows.stream().anyMatch(r -> "specialisedlegacyfirmware".equals(r.getMaterialKey())));
        assertTrue(rows.stream().anyMatch(r -> "chemicalprocessors".equals(r.getMaterialKey())));
        ShoppingListRow firmware = rows.stream()
                .filter(r -> "specialisedlegacyfirmware".equals(r.getMaterialKey()))
                .findFirst()
                .orElseThrow();
        ShoppingListRow processors = rows.stream()
                .filter(r -> "chemicalprocessors".equals(r.getMaterialKey()))
                .findFirst()
                .orElseThrow();
        assertEquals(3, firmware.getRequired());
        assertEquals(3, processors.getRequired());
    }
}
