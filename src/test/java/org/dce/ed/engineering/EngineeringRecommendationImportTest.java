package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class EngineeringRecommendationImportTest {

    private final EngineeringDatabase database = EngineeringDatabase.getInstance();

    @Test
    void parsesSlefRecommendationIntoSlotPinnedGoal() {
        String slef = """
                {
                  "header":{"appName":"Recommendation Agent","appVersion":"1"},
                  "data":{
                    "event":"Loadout","Ship":"mandalay","ShipID":9,"ShipName":"Wayfinder",
                    "Modules":[{
                      "Slot":"FrameShiftDrive","Item":"int_hyperdrive_overcharge_size5_class5",
                      "Engineering":{
                        "BlueprintName":"FSD_LongRange","Level":5,
                        "ExperimentalEffect":"special_fsd_heavy",
                        "ExperimentalEffect_Localised":"Mass Manager"
                      }
                    }]
                  }
                }
                """;

        EngineeringRecommendationImport.Plan plan = EngineeringRecommendationImport.parse(slef, database);

        assertFalse(plan.hasErrors(), plan.errors().toString());
        assertEquals(9L, plan.shipId());
        assertEquals("mandalay", plan.shipType());
        assertEquals(1, plan.goals().size());
        EngineeringGoal goal = plan.goals().get(0);
        assertEquals("FrameShiftDrive", goal.getTargetSlot());
        assertEquals("Frame Shift Drive", goal.getModuleType());
        assertEquals("Increased FSD Range", goal.getBlueprintName());
        assertEquals(5, goal.getTargetGrade());
        assertEquals("frame-shift-drive-mass-manager-experimental", goal.getExperimentalId());
    }

    @Test
    void rejectsUnknownBlueprintWithoutGuessing() {
        String slef = """
                {"data":{"event":"Loadout","Ship":"mandalay","ShipID":9,"Modules":[{
                  "Slot":"FrameShiftDrive","Item":"int_hyperdrive_overcharge_size5_class5",
                  "Engineering":{"BlueprintName":"FSD_Imaginary","Level":5}
                }]}}
                """;

        EngineeringRecommendationImport.Plan plan = EngineeringRecommendationImport.parse(slef, database);

        assertTrue(plan.hasErrors());
        assertTrue(plan.errors().get(0).contains("FSD_Imaginary"), plan.errors().toString());
        assertTrue(plan.goals().isEmpty());
    }

    @Test
    void mergeUpdatesMatchingSlotAndLeavesUnrelatedGoals() {
        EngineeringGoal existing = new EngineeringGoal(
                "frame-shift-drive-increased-fsd-range-grade-3", "Frame Shift Drive", "Increased FSD Range",
                2, 1, 3, "", GoalPriority.HIGH, false, 1, 0, 9L, "Wayfinder", true,
                "FrameShiftDrive");
        EngineeringGoal unrelated = new EngineeringGoal(
                "power-plant-armoured-grade-5", "Power Plant", "Armoured Power Plant",
                0, 0, 5, "", GoalPriority.LOW, false, 1, 0, 9L, "Wayfinder", true,
                "PowerPlant");
        EngineeringGoal recommended = new EngineeringGoal(
                "frame-shift-drive-increased-fsd-range-grade-5", "Frame Shift Drive", "Increased FSD Range",
                0, 0, 5, "frame-shift-drive-mass-manager-experimental", GoalPriority.MEDIUM, false,
                1, 0, 9L, "Wayfinder", true, "FrameShiftDrive");
        List<EngineeringGoal> goals = new ArrayList<>(List.of(existing, unrelated));

        EngineeringRecommendationImport.MergePreview preview =
                EngineeringRecommendationImport.previewMerge(goals, List.of(recommended));
        preview.applyTo(goals);

        assertEquals(1, preview.updateCount());
        assertEquals(0, preview.addCount());
        assertEquals(2, goals.size());
        EngineeringGoal updated = goals.stream()
                .filter(g -> "FrameShiftDrive".equals(g.getTargetSlot()))
                .findFirst().orElseThrow();
        assertEquals("frame-shift-drive-increased-fsd-range-grade-5", updated.getBlueprintId());
        assertEquals(5, updated.getTargetGrade());
        assertEquals(2, updated.getFromGrade(), "compatible journal progress is preserved");
        assertEquals(GoalPriority.HIGH, updated.getPriority(), "existing planning priority is preserved");
        assertTrue(goals.contains(unrelated));
    }
}
