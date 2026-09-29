package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void rollsRequired_rank5_equalsGradeNumber() {
        assertEquals(1, EngineeringGradeProgress.rollsRequired(5, 1));
        assertEquals(2, EngineeringGradeProgress.rollsRequired(5, 2));
        assertEquals(3, EngineeringGradeProgress.rollsRequired(5, 3));
        assertEquals(4, EngineeringGradeProgress.rollsRequired(5, 4));
        assertEquals(5, EngineeringGradeProgress.rollsRequired(5, 5));
    }

    @Test
    void rollsRequired_rank1_g1NeedsFive() {
        assertEquals(5, EngineeringGradeProgress.rollsRequired(1, 1));
        assertEquals(5, EngineeringGradeProgress.rollsRequired(0, 3), "unknown rank is conservative");
    }

    @Test
    void planner_ignoresRank5Discount_keepsConservativeFiveRollNeed() {
        // Reputation is tracked for progress/UI, but Need always uses the 5-roll schedule.
        BlueprintGrade g3 = db.gradesFor("Power Distributor", "Charge Enhanced").stream()
                .filter(b -> b.getGrade() == 3)
                .findFirst()
                .orElseThrow();
        EngineeringGoal goal = new EngineeringGoal(
                g3.getId(), g3.getModuleType(), g3.getName(), 0, 0, 3, "");

        Map<String, Integer> required = planner.materialsForGoal(goal);
        int firmware = required.getOrDefault("specialisedlegacyfirmware", 0);
        assertEquals(10, firmware, "G1+G2 ×5 specialised legacy firmware (conservative)");
    }

    /**
     * Slot03 cargo rack, Sept 28: G4 rolls 0.2004, 0.4012 at rank 4, then 0.6511, 0.901 at rank 5.
     * The game finished the grade at 0.901 (all four rows checked at Bill Turner).
     */
    @Test
    void afterCraft_qualityAboveEightyPercentFinishesTheGrade() {
        EngineeringGoal goal = new EngineeringGoal("id", "Cargo Rack", "Extended", 3, 0, 4, "");
        goal = EngineeringGradeProgress.afterCraft(goal, 4, 0.901);
        assertEquals(4, goal.getFromGrade());
        assertTrue(goal.isCurrentUnitComplete());
    }

    /** An unfinished grade tops out at 0.80: four of five rolls. */
    @Test
    void afterCraft_eightyPercentStillHasARollLeft() {
        EngineeringGoal goal = new EngineeringGoal("id", "Cargo Rack", "Extended", 3, 0, 4, "");
        goal = EngineeringGradeProgress.afterCraft(goal, 4, 0.8);
        assertEquals(3, goal.getFromGrade());
        assertFalse(goal.isCurrentUnitComplete());
    }

    /** Rank rose after two rolls: the 4th roll meets rank 5's requirement even at Quality 0.80. */
    @Test
    void afterCraft_rollsMeetTheRequirementAtTheCurrentRank() {
        EngineeringGoal goal = new EngineeringGoal("id", "Cargo Rack", "Extended", 3, 0, 4, "");
        goal = EngineeringGradeProgress.afterCraft(goal, 4, 0.2, 4);
        goal = EngineeringGradeProgress.afterCraft(goal, 4, 0.4, 4);
        goal = EngineeringGradeProgress.afterCraft(goal, 4, 0.6, 5);
        assertEquals(3, goal.getFromGrade(), "three rolls, rank 5 needs four");
        assertEquals(3, goal.getRollsAtCurrentGrade());
        goal = EngineeringGradeProgress.afterCraft(goal, 4, 0.8, 5);
        assertEquals(4, goal.getFromGrade());
    }

    @Test
    void afterCraft_lowerRankNeedsMoreRolls() {
        EngineeringGoal goal = new EngineeringGoal("id", "Cargo Rack", "Extended", 3, 0, 4, "");
        for (double q : new double[] {0.2, 0.4, 0.6, 0.8}) {
            goal = EngineeringGradeProgress.afterCraft(goal, 4, q, 4);
        }
        assertEquals(3, goal.getFromGrade(), "rank 4 needs five G4 rolls");
        goal = EngineeringGradeProgress.afterCraft(goal, 4, 1.0, 4);
        assertEquals(4, goal.getFromGrade());
    }

    /** Quality 0.5 on the first of two rolls must not count as two rolls. */
    @Test
    void afterCraft_highQualityStepDoesNotSkipARoll() {
        EngineeringGoal goal = new EngineeringGoal("id", "Cargo Rack", "Extended", 1, 0, 4, "");
        goal = EngineeringGradeProgress.afterCraft(goal, 2, 0.5006, 5);
        assertEquals(1, goal.getFromGrade(), "rank 5 G2 needs two rolls");
        goal = EngineeringGradeProgress.afterCraft(goal, 2, 1.0, 5);
        assertEquals(2, goal.getFromGrade());
    }

    @Test
    void afterCraft_qualityOneCompletesGradeInFewerThanFiveRolls() {
        // Marco Qwent / pinned mats: G3 can finish in three applications (0.33 / 0.67 / 1.0).
        EngineeringGoal goal = new EngineeringGoal("id", "Power Distributor", "Charge Enhanced", 2, 0, 5, "");
        goal = EngineeringGradeProgress.afterCraft(goal, 3, 0.3333);
        assertEquals(2, goal.getFromGrade());
        assertEquals(2, goal.getCraftsAtCurrentGrade());

        goal = EngineeringGradeProgress.afterCraft(goal, 3, 0.6667);
        assertEquals(2, goal.getFromGrade());
        assertEquals(3, goal.getCraftsAtCurrentGrade());

        goal = EngineeringGradeProgress.afterCraft(goal, 3, 1.0);
        assertEquals(3, goal.getFromGrade());
        assertEquals(0, goal.getCraftsAtCurrentGrade());
    }

    @Test
    void afterCraft_qualityOneOnSkippedGradesMarksLevelComplete() {
        EngineeringGoal goal = new EngineeringGoal("id", "Power Distributor", "Charge Enhanced", 0, 0, 5, "");
        goal = EngineeringGradeProgress.afterCraft(goal, 2, 1.0);
        assertEquals(2, goal.getFromGrade());
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
    void afterCraft_higherGradeSnapsAheadEvenWhenLowerGradeWasInProgress() {
        // Real journals often log 1×L1 then L2… — fewer than 5 crafts per grade visible.
        EngineeringGoal goal = new EngineeringGoal(
                "power-distributor-charge-enhanced-g5",
                "Power Distributor",
                "Charge Enhanced",
                0,
                1,
                5,
                "");

        EngineeringGoal after = EngineeringGradeProgress.afterCraft(goal, 2);

        assertEquals(1, after.getFromGrade());
        assertEquals(1, after.getCraftsAtCurrentGrade());
    }

    @Test
    void afterCraft_higherGradeSnapsWhenStartingMidProgress() {
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
    void afterCraft_realPulseEfficientJournalSequenceReachesG4() {
        EngineeringGoal goal = new EngineeringGoal(
                "pulse", "Pulse Laser", "Efficient Weapon", 0, 0, 5, "");
        int[] levels = {1, 2, 2, 3, 3, 3, 4, 4, 4, 4, 4};
        for (int level : levels) {
            goal = EngineeringGradeProgress.afterCraft(goal, level);
        }
        assertEquals(4, goal.getFromGrade());
        assertEquals(0, goal.getCraftsAtCurrentGrade());
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

    @Test
    void completionFraction_zeroUntilCraftsThenRisesTowardOne() {
        EngineeringGoal goal = new EngineeringGoal(
                "id", "Power Distributor", "Charge Enhanced", 0, 0, 5, "");
        assertEquals(0.0, EngineeringGradeProgress.completionFraction(goal, 0), 1e-9);

        goal = EngineeringGradeProgress.afterCraft(goal, 1, 0.2);
        double midG1 = EngineeringGradeProgress.completionFraction(goal, 0);
        assertTrue(midG1 > 0.0 && midG1 < 1.0, "partial G1: " + midG1);

        goal = goal.withProgress(5, 0);
        assertEquals(1.0, EngineeringGradeProgress.completionFraction(goal, 0), 1e-9);
    }

    @Test
    void completionFraction_includesExperimentalAsFinalStep() {
        EngineeringGoal goal = new EngineeringGoal(
                "id",
                "Armour",
                "Heavy Duty",
                5,
                0,
                5,
                "armour-deep-plating-experimental");
        double beforeExp = EngineeringGradeProgress.completionFraction(goal, 0);
        assertTrue(beforeExp > 0.9 && beforeExp < 1.0, "G5 without experimental: " + beforeExp);

        goal = goal.withExperimentalApplied(true);
        assertEquals(1.0, EngineeringGradeProgress.completionFraction(goal, 0), 1e-9);
    }
}
