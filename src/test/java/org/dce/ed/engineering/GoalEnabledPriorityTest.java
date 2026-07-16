package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GoalEnabledPriorityTest {

    private static EngineeringDatabase db;
    private static EngineeringPlanner planner;
    private static MaterialTradePlanner tradePlanner;

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
        planner = new EngineeringPlanner(db);
        tradePlanner = new MaterialTradePlanner(db);
    }

    @Test
    void withEnabled_preservesPriority() {
        EngineeringGoal high = new EngineeringGoal(
                "bp", "Power Distributor", "Charge Enhanced", 0, 0, 1, "",
                GoalPriority.HIGH);
        assertTrue(high.isEnabled());
        assertEquals(GoalPriority.HIGH, high.getPriority());

        EngineeringGoal hidden = high.withEnabled(false);
        assertFalse(hidden.isEnabled());
        assertEquals(GoalPriority.HIGH, hidden.getPriority());
        assertFalse(hidden.isIncludeInPlanning());

        EngineeringGoal restored = hidden.withEnabled(true);
        assertTrue(restored.isEnabled());
        assertEquals(GoalPriority.HIGH, restored.getPriority());
    }

    @Test
    void legacyDisabledPriority_normalizesToMediumAndOff() {
        EngineeringGoal migrated = new EngineeringGoal(
                "bp", "Power Distributor", "Charge Enhanced", 0, 0, 1, "",
                GoalPriority.DISABLED);
        assertFalse(migrated.isEnabled());
        assertEquals(GoalPriority.MEDIUM, migrated.getPriority());
    }

    @Test
    void disabledGoal_omittedFromPlanningTotals() {
        BlueprintGrade g1 = db.gradesFor("Power Distributor", "Charge Enhanced").stream()
                .filter(b -> b.getGrade() == 1)
                .findFirst()
                .orElseThrow();
        EngineeringGoal enabled = new EngineeringGoal(
                g1.getId(), g1.getModuleType(), g1.getName(), 0, 0, 1, "",
                GoalPriority.HIGH);
        EngineeringGoal disabled = enabled.withEnabled(false);

        Map<String, Integer> need = planner.materialsForGoal(enabled);
        assertFalse(need.isEmpty());

        EngineeringPlanner.PriorityPlanResult plan = planner.planByPriority(
                List.of(disabled), Map.of(), tradePlanner);
        assertTrue(plan.readinessByBlueprintGoal().isEmpty());
        assertTrue(plan.trades().isEmpty());
    }

    @Test
    void materialsGoal_enabledToggleIndependentOfPriority() {
        MaterialsGoal goal = new MaterialsGoal(
                "Mission",
                List.of(new MaterialRequirement("iron", 10)),
                GoalPriority.LOW);
        MaterialsGoal hidden = goal.withEnabled(false);
        assertEquals(GoalPriority.LOW, hidden.getPriority());
        assertFalse(hidden.isIncludeInPlanning());
    }
}
