package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.dce.ed.engineering.GoalPriority;
import org.dce.ed.engineering.MaterialRequirement;
import org.dce.ed.engineering.MaterialsGoal;
import org.junit.jupiter.api.Test;

class MaterialsGoalShipFilterTest {

    @Test
    void unassignedMaterialsGoal_alwaysVisible() {
        MaterialsGoal unassigned = new MaterialsGoal(
                "Mission request",
                List.of(new MaterialRequirement("iron", 1)),
                GoalPriority.MEDIUM);
        assertTrue(EngineeringTabPanel.isMaterialsGoalVisibleForShipFilter(unassigned, null));
        assertTrue(EngineeringTabPanel.isMaterialsGoalVisibleForShipFilter(unassigned, 99L));
    }

    @Test
    void assignedMaterialsGoal_respectsShipFilter() {
        MaterialsGoal assigned = new MaterialsGoal(
                "Ship stockpile",
                List.of(new MaterialRequirement("iron", 1)),
                GoalPriority.MEDIUM,
                42L,
                "Anaconda");
        assertTrue(EngineeringTabPanel.isMaterialsGoalVisibleForShipFilter(assigned, null));
        assertTrue(EngineeringTabPanel.isMaterialsGoalVisibleForShipFilter(assigned, 42L));
        assertFalse(EngineeringTabPanel.isMaterialsGoalVisibleForShipFilter(assigned, 99L));
    }
}
