package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.dce.ed.engineering.EngineeringGoal;
import org.dce.ed.engineering.GoalPriority;
import org.junit.jupiter.api.Test;

class EngineeringBuildProgressDialogSummaryTest {

    @Test
    void goalModuleDisplay_includesPinnedOptionalInternalSize() {
        EngineeringGoal goal = new EngineeringGoal(
                "hrp-lightweight-5",
                "Hull Reinforcement Package",
                "Lightweight Hull Reinforcement",
                0,
                0,
                5,
                "",
                GoalPriority.MEDIUM,
                false,
                1,
                0,
                7L,
                "Exception Handler",
                true,
                "Slot08_Size4");

        assertEquals("Hull Reinforcement Package · Size 4",
                EngineeringBuildProgressDialog.goalModuleDisplay(goal));
    }
}
