package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.dce.ed.engineering.ShipEngineeringSummary.Band;
import org.dce.ed.engineering.ShipEngineeringSummary.Row;
import org.dce.ed.session.EngineeringSessionData;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

class EngineeringGoalSlotMatcherTest {

    private static Row gapRow(long shipId, String slotKey, String moduleType) {
        return new Row(shipId, slotKey, slotKey, moduleType, moduleType, "", "", "", 0, 0, 1, Band.GAP);
    }

    private static EngineeringGoal hrpGoal(long shipId, String targetSlot, String blueprint) {
        return moduleGoal(shipId, targetSlot, "Hull Reinforcement Package", blueprint, 1);
    }

    private static EngineeringGoal moduleGoal(
            long shipId, String targetSlot, String moduleType, String blueprint, int quantity) {
        return new EngineeringGoal(
                "bp",
                moduleType,
                blueprint,
                0,
                0,
                5,
                "",
                GoalPriority.MEDIUM,
                false,
                quantity,
                0,
                shipId,
                "Test Ship",
                true,
                targetSlot);
    }

    @Test
    void pinnedSlots_matchOnlyTheirRows() {
        Row size4 = gapRow(7L, "Slot08_Size4", "Hull Reinforcement Package");
        Row size5 = gapRow(7L, "Slot09_Size5", "Hull Reinforcement Package");
        EngineeringGoal g4 = hrpGoal(7L, "Slot08_Size4", "Heavy Duty");
        EngineeringGoal g5 = hrpGoal(7L, "Slot09_Size5", "Lightweight");

        Map<String, EngineeringGoal> assigned =
                EngineeringGoalSlotMatcher.assign(List.of(size4, size5), List.of(g4, g5));

        assertEquals(g4, assigned.get(EngineeringGoalSlotMatcher.rowKey(size4)));
        assertEquals(g5, assigned.get(EngineeringGoalSlotMatcher.rowKey(size5)));
        assertEquals(g4, EngineeringGoalSlotMatcher.forRow(size4, List.of(size4, size5), List.of(g4, g5)));
        assertEquals(g5, EngineeringGoalSlotMatcher.forRow(size5, List.of(size4, size5), List.of(g4, g5)));
    }

    @Test
    void unscopedGoal_qty1_matchesOnlyOneGapRow() {
        Row size4 = gapRow(7L, "Slot08_Size4", "Hull Reinforcement Package");
        Row size5 = gapRow(7L, "Slot09_Size5", "Hull Reinforcement Package");
        EngineeringGoal unscoped = hrpGoal(7L, "", "Heavy Duty");

        Map<String, EngineeringGoal> assigned =
                EngineeringGoalSlotMatcher.assign(List.of(size4, size5), List.of(unscoped));

        assertEquals(1, assigned.size());
        assertEquals(unscoped, assigned.get(EngineeringGoalSlotMatcher.rowKey(size4)));
        assertNull(assigned.get(EngineeringGoalSlotMatcher.rowKey(size5)));
    }

    @Test
    void unscopedGoal_qty2_claimsTwoCompatibleRows() {
        Row a = gapRow(7L, "TinyHardpoint1", "Shield Booster");
        Row b = gapRow(7L, "TinyHardpoint2", "Shield Booster");
        Row c = gapRow(7L, "TinyHardpoint3", "Shield Booster");
        EngineeringGoal resistance = moduleGoal(7L, "", "Shield Booster", "Resistance Augmented", 2);

        Map<String, EngineeringGoal> assigned =
                EngineeringGoalSlotMatcher.assign(List.of(a, b, c), List.of(resistance));

        assertEquals(2, assigned.size());
        assertEquals(resistance, assigned.get(EngineeringGoalSlotMatcher.rowKey(a)));
        assertEquals(resistance, assigned.get(EngineeringGoalSlotMatcher.rowKey(b)));
        assertNull(assigned.get(EngineeringGoalSlotMatcher.rowKey(c)));
    }

    @Test
    void twoQty2Goals_splitFourShieldBoosters() {
        Row a = gapRow(7L, "TinyHardpoint1", "Shield Booster");
        Row b = gapRow(7L, "TinyHardpoint2", "Shield Booster");
        Row c = gapRow(7L, "TinyHardpoint3", "Shield Booster");
        Row d = gapRow(7L, "TinyHardpoint4", "Shield Booster");
        EngineeringGoal resistance = moduleGoal(7L, "", "Shield Booster", "Resistance Augmented", 2);
        EngineeringGoal heavyDuty = moduleGoal(7L, "", "Shield Booster", "Heavy Duty", 2);

        Map<String, EngineeringGoal> assigned =
                EngineeringGoalSlotMatcher.assign(List.of(a, b, c, d), List.of(resistance, heavyDuty));

        assertEquals(4, assigned.size());
        assertEquals(resistance, assigned.get(EngineeringGoalSlotMatcher.rowKey(a)));
        assertEquals(resistance, assigned.get(EngineeringGoalSlotMatcher.rowKey(b)));
        assertEquals(heavyDuty, assigned.get(EngineeringGoalSlotMatcher.rowKey(c)));
        assertEquals(heavyDuty, assigned.get(EngineeringGoalSlotMatcher.rowKey(d)));
    }

    @Test
    void pinnedGoal_doesNotStealOtherSlot() {
        Row size4 = gapRow(7L, "Slot08_Size4", "Hull Reinforcement Package");
        Row size5 = gapRow(7L, "Slot09_Size5", "Hull Reinforcement Package");
        EngineeringGoal pinnedToSize5 = hrpGoal(7L, "Slot09_Size5", "Heavy Duty");

        assertNull(EngineeringGoalSlotMatcher.forRow(
                size4, List.of(size4, size5), List.of(pinnedToSize5)));
        assertEquals(pinnedToSize5, EngineeringGoalSlotMatcher.forRow(
                size5, List.of(size4, size5), List.of(pinnedToSize5)));
    }

    @Test
    void session_targetSlot_roundTripsThroughJson() {
        EngineeringSessionData written = new EngineeringSessionData();
        EngineeringSessionData.EngineeringGoalPersisted goal =
                new EngineeringSessionData.EngineeringGoalPersisted();
        goal.setBlueprintId("bp");
        goal.setModuleType("Hull Reinforcement Package");
        goal.setBlueprintName("Heavy Duty");
        goal.setFromGrade(0);
        goal.setCraftsAtCurrentGrade(0);
        goal.setTargetGrade(5);
        goal.setExperimentalId("");
        goal.setPriority(GoalPriority.MEDIUM.name());
        goal.setIncludeInPlanning(Boolean.TRUE);
        goal.setQuantity(1);
        goal.setCompletedUnits(0);
        goal.setShipId(7L);
        goal.setShipLabel("Anaconda");
        goal.setTargetSlot("Slot08_Size4");
        written.setGoals(List.of(goal));

        EngineeringSessionData read = new Gson().fromJson(
                new Gson().toJson(written), EngineeringSessionData.class);
        assertEquals(1, read.goalsOrEmpty().size());
        EngineeringSessionData.EngineeringGoalPersisted restored = read.goalsOrEmpty().get(0);
        assertEquals("Slot08_Size4", restored.getTargetSlot());
        assertTrue(new EngineeringGoal(
                restored.getBlueprintId(),
                restored.getModuleType(),
                restored.getBlueprintName(),
                restored.getFromGrade(),
                restored.getCraftsAtCurrentGrade(),
                restored.getTargetGrade(),
                restored.getExperimentalId(),
                restored.priorityOrDefault(),
                restored.isExperimentalApplied(),
                restored.getQuantity(),
                restored.getCompletedUnits(),
                restored.shipIdOrUnknown(),
                restored.getShipLabel(),
                restored.includeInPlanningOrDefault(),
                restored.getTargetSlot()).hasTargetSlot());
    }

    @Test
    void withTargetSlot_preservesAcrossProgressHelpers() {
        EngineeringGoal goal = hrpGoal(3L, "Slot02_Size4", "Lightweight");
        EngineeringGoal progressed = goal.withProgress(2, 1).withQuantity(1);
        assertEquals("Slot02_Size4", progressed.getTargetSlot());
        assertEquals("Slot02_Size4", progressed.resetJournalProgress().getTargetSlot());
    }
}
