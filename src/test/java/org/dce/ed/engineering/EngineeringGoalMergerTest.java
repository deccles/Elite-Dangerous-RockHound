package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.LoadoutEvent;
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
    void merge_identicalPinnedGoals_groupsTargetsAndRejectsCompletedSiblings() {
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(hrp(23L, "Slot04_Size6", "Heavy Duty Hull Reinforcement", 5, 1));
        goals.add(hrp(23L, "Slot06_Size5", "Heavy Duty Hull Reinforcement", 5, 1));
        goals.add(hrp(23L, "Slot07_Size5", "Heavy Duty Hull Reinforcement", 5, 1));

        assertTrue(EngineeringGoalMerger.mergeInPlace(goals));
        assertEquals(1, goals.size());
        assertEquals(3, goals.get(0).getQuantity());
        assertTrue(goals.get(0).hasTargetSlot());
        assertEquals(List.of("Slot04_Size6", "Slot06_Size5", "Slot07_Size5"),
                goals.get(0).getTargetSlots());

        LoadoutEvent loadout = (LoadoutEvent) new EliteLogParser().parseRecord("""
                {"timestamp":"2026-08-19T16:00:00Z","event":"Loadout",
                 "Ship":"federation_corvette","ShipID":23,"Modules":[
                  {"Slot":"Slot04_Size6","Item":"int_hullreinforcement_size5_class2"},
                  {"Slot":"Slot06_Size5","Item":"int_hullreinforcement_size5_class2"},
                  {"Slot":"Slot07_Size5","Item":"int_hullreinforcement_size5_class2"},
                  {"Slot":"Military02","Item":"int_hullreinforcement_size5_class2",
                   "Engineering":{"BlueprintName":"HullReinforcement_HeavyDuty","Level":5,"Quality":1.0}},
                  {"Slot":"Slot08_Size4","Item":"int_hullreinforcement_size4_class2",
                   "Engineering":{"BlueprintName":"HullReinforcement_HeavyDuty","Level":5,"Quality":1.0}},
                  {"Slot":"Slot09_Size4","Item":"int_hullreinforcement_size4_class2",
                   "Engineering":{"BlueprintName":"HullReinforcement_HeavyDuty","Level":5,"Quality":1.0}}
                 ]}
                """);
        EngineeringGoalProgress.applyLoadout(goals, loadout, EngineeringDatabase.getInstance());

        assertFalse(goals.get(0).isComplete());
        assertEquals(0, goals.get(0).getCompletedUnits());
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
    void merge_pinnedAndUnscopedGoals_staySeparate() {
        List<EngineeringGoal> goals = new ArrayList<>();
        goals.add(hrp(7L, "", "Heavy Duty", 5, 2));
        goals.add(hrp(7L, "Slot08_Size4", "Heavy Duty", 5, 1));

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
                "");
        EngineeringGoal fresh = hrp(7L, "", "Heavy Duty", 5, 1);
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
