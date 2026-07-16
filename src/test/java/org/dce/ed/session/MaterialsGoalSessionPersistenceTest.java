package org.dce.ed.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.dce.ed.engineering.GoalPriority;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

class MaterialsGoalSessionPersistenceTest {

    private final Gson gson = new Gson();

    @Test
    void materialGoals_roundTripThroughJson() {
        EngineeringSessionData written = new EngineeringSessionData();
        EngineeringSessionData.MaterialsGoalPersisted goal = new EngineeringSessionData.MaterialsGoalPersisted();
        goal.setLabel("Mission request");
        goal.setPriority(GoalPriority.HIGH.name());
        goal.setShipId(42L);
        goal.setShipLabel("Anaconda");
        goal.setMaterials(List.of(
                new EngineeringSessionData.MaterialNeedPersisted("iron", 20),
                new EngineeringSessionData.MaterialNeedPersisted("phosphorus", 5)));
        written.setMaterialGoals(List.of(goal));

        EngineeringSessionData read = gson.fromJson(gson.toJson(written), EngineeringSessionData.class);
        assertEquals(1, read.materialGoalsOrEmpty().size());
        EngineeringSessionData.MaterialsGoalPersisted restored = read.materialGoalsOrEmpty().get(0);
        assertEquals("Mission request", restored.getLabel());
        assertEquals(GoalPriority.HIGH, restored.priorityOrDefault());
        assertEquals(42L, restored.shipIdOrUnknown());
        assertEquals("Anaconda", restored.getShipLabel());
        assertEquals(2, restored.materialsOrEmpty().size());
        assertEquals("iron", restored.materialsOrEmpty().get(0).getKey());
        assertEquals(20, restored.materialsOrEmpty().get(0).getCount());
        assertEquals("phosphorus", restored.materialsOrEmpty().get(1).getKey());
        assertEquals(5, restored.materialsOrEmpty().get(1).getCount());
    }

    @Test
    void materialGoals_missingField_isEmpty() {
        EngineeringSessionData read = gson.fromJson("{\"goals\":[]}", EngineeringSessionData.class);
        assertNotNull(read);
        assertTrue(read.materialGoalsOrEmpty().isEmpty());
    }

    @Test
    void materialGoals_nullPriorityDefaultsToMedium() {
        EngineeringSessionData.MaterialsGoalPersisted goal = new EngineeringSessionData.MaterialsGoalPersisted();
        goal.setLabel("Stockpile");
        goal.setMaterials(List.of(new EngineeringSessionData.MaterialNeedPersisted("iron", 1)));
        assertEquals(GoalPriority.MEDIUM, goal.priorityOrDefault());
        assertTrue(goal.includeInPlanningOrDefault());
        assertFalse(goal.getLabel().isBlank());
    }

    @Test
    void materialGoals_legacyDisabledPriority_migratesToDisabledInclude() {
        EngineeringSessionData.MaterialsGoalPersisted goal = new EngineeringSessionData.MaterialsGoalPersisted();
        goal.setLabel("Old");
        goal.setPriority("DISABLED");
        goal.setMaterials(List.of(new EngineeringSessionData.MaterialNeedPersisted("iron", 1)));
        assertEquals(GoalPriority.MEDIUM, goal.priorityOrDefault());
        assertFalse(goal.includeInPlanningOrDefault());
    }

    @Test
    void materialGoals_includeFlagRoundTrip() {
        EngineeringSessionData written = new EngineeringSessionData();
        EngineeringSessionData.MaterialsGoalPersisted goal = new EngineeringSessionData.MaterialsGoalPersisted();
        goal.setLabel("Paused");
        goal.setPriority(GoalPriority.HIGH.name());
        goal.setIncludeInPlanning(Boolean.FALSE);
        goal.setMaterials(List.of(new EngineeringSessionData.MaterialNeedPersisted("iron", 3)));
        written.setMaterialGoals(List.of(goal));

        EngineeringSessionData read = gson.fromJson(gson.toJson(written), EngineeringSessionData.class);
        EngineeringSessionData.MaterialsGoalPersisted restored = read.materialGoalsOrEmpty().get(0);
        assertEquals(GoalPriority.HIGH, restored.priorityOrDefault());
        assertFalse(restored.includeInPlanningOrDefault());
    }
}
