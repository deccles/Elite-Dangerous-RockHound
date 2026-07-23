package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class EngineeringGoalMergerTest {

    private static EngineeringGoal hrp(
            long shipId,
            String slot,
            String blueprint,
            int targetGrade,
            int quantity) {
        return new EngineeringGoal(
                "bp",
                "Hull Reinforcement Package",
                blueprint,
                0,
                0,
                targetGrade,
                "",
                GoalPriority.MEDIUM,
                false,
                quantity,
                0,
                shipId,
                "Test Ship",
                true,
                slot);
    }

    @Test
    void merge_identicalPinnedGoals_sumsQuantityAndClearsSlot() {
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(hrp(7L, "Slot08_Size4", "Heavy Duty", 5, 1));
        goals.add(hrp(7L, "Slot09_Size4", "Heavy Duty", 5, 1));

        assertTrue(EngineeringGoalMerger.mergeInPlace(goals));
        assertEquals(1, goals.size());
        assertEquals(2, goals.get(0).getQuantity());
        assertFalse(goals.get(0).hasTargetSlot());
    }

    @Test
    void merge_differentBlueprints_staySeparate() {
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(hrp(7L, "Slot08_Size4", "Heavy Duty", 5, 1));
        goals.add(hrp(7L, "Slot09_Size5", "Lightweight", 5, 1));

        assertFalse(EngineeringGoalMerger.mergeInPlace(goals));
        assertEquals(2, goals.size());
        assertTrue(goals.get(0).hasTargetSlot());
        assertTrue(goals.get(1).hasTargetSlot());
    }

    @Test
    void merge_differentTargetGrades_staySeparate() {
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(hrp(7L, "Slot08_Size4", "Heavy Duty", 4, 1));
        goals.add(hrp(7L, "Slot09_Size4", "Heavy Duty", 5, 1));

        assertFalse(EngineeringGoalMerger.mergeInPlace(goals));
        assertEquals(2, goals.size());
    }

    @Test
    void merge_preservesBestPartialProgress() {
        EngineeringGoal advanced = new EngineeringGoal(
                "bp",
                "Hull Reinforcement Package",
                "Heavy Duty",
                3,
                2,
                5,
                "",
                GoalPriority.MEDIUM,
                false,
                1,
                0,
                7L,
                "Ship",
                true,
                "SlotA");
        EngineeringGoal fresh = hrp(7L, "SlotB", "Heavy Duty", 5, 1);
        List<EngineeringGoal> goals = new ArrayList<>(List.of(fresh, advanced));

        assertTrue(EngineeringGoalMerger.mergeInPlace(goals));
        assertEquals(1, goals.size());
        assertEquals(2, goals.get(0).getQuantity());
        assertEquals(3, goals.get(0).getFromGrade());
        assertEquals(2, goals.get(0).getCraftsAtCurrentGrade());
    }

    @Test
    void findMatching_locatesByPlanFieldsIgnoringSlot() {
        EngineeringGoal pinned = hrp(3L, "Slot01", "Heavy Duty", 5, 1);
        EngineeringGoal other = hrp(3L, "Slot02", "Lightweight", 5, 1);
        List<EngineeringGoal> goals = List.of(pinned, other);

        EngineeringGoal found = EngineeringGoalMerger.findMatching(
                goals, 3L, "Hull Reinforcement Package", "Heavy Duty", "", 5);
        assertEquals(pinned, found);
    }

    @Test
    void singleGoalWithQuantity_clearsPin() {
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(hrp(1L, "Slot01", "Heavy Duty", 5, 3));
        assertTrue(EngineeringGoalMerger.mergeInPlace(goals));
        assertEquals(1, goals.size());
        assertEquals(3, goals.get(0).getQuantity());
        assertFalse(goals.get(0).hasTargetSlot());
    }
}
