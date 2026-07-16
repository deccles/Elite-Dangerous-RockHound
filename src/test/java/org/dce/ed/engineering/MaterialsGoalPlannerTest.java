package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MaterialsGoalPlannerTest {

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
    void materialsGoal_reservesStackOnBlueprintNeeds() {
        BlueprintGrade g1 = db.gradesFor("Power Distributor", "Charge Enhanced").stream()
                .filter(b -> b.getGrade() == 1)
                .findFirst()
                .orElseThrow();
        EngineeringGoal craft = new EngineeringGoal(
                g1.getId(), g1.getModuleType(), g1.getName(), 0, 1, "");
        Map<String, Integer> craftNeed = planner.materialsForGoal(craft);
        assertFalse(craftNeed.isEmpty());
        String key = craftNeed.keySet().iterator().next();
        int craftCount = craftNeed.get(key);

        MaterialsGoal reserve = new MaterialsGoal(
                "Mission request",
                List.of(new MaterialRequirement(key, 7)),
                GoalPriority.MEDIUM);

        Map<String, Integer> combined = planner.requiredMaterials(List.of(craft), List.of(reserve));
        assertEquals(craftCount + 7, combined.get(key).intValue());
    }

    @Test
    void planByPriority_highMaterialsGoalClaimsBeforeLowCraft() {
        BlueprintGrade lowBp = db.gradesFor("Power Distributor", "Charge Enhanced").stream()
                .filter(b -> b.getGrade() == 1)
                .findFirst()
                .orElseThrow();
        EngineeringGoal lowCraft = new EngineeringGoal(
                lowBp.getId(), lowBp.getModuleType(), lowBp.getName(), 0, 0, 1, "",
                GoalPriority.LOW);
        Map<String, Integer> craftNeed = planner.materialsForGoal(lowCraft);
        assertFalse(craftNeed.isEmpty());
        String contested = craftNeed.keySet().iterator().next();
        int craftCount = craftNeed.get(contested);

        MaterialsGoal highReserve = new MaterialsGoal(
                "Mission request",
                List.of(new MaterialRequirement(contested, craftCount)),
                GoalPriority.HIGH);

        // Exactly enough for the reserve OR the craft, not both.
        Map<String, Integer> inv = new HashMap<>();
        inv.put(contested, craftCount);

        EngineeringPlanner.PriorityPlanResult plan = planner.planByPriority(
                List.of(lowCraft), List.of(highReserve), inv, tradePlanner);

        assertEquals(GoalReadiness.READY, plan.readinessByMaterialsGoal().get(highReserve));
        assertEquals(GoalReadiness.STILL_SHORT, plan.readinessByBlueprintGoal().get(lowCraft));
    }

    @Test
    void materialsGoal_readiness_readyTradesAndShort() {
        MaterialsGoal goal = new MaterialsGoal(
                "Need iron",
                List.of(new MaterialRequirement("iron", 10)),
                GoalPriority.MEDIUM);

        assertEquals(GoalReadiness.STILL_SHORT, planner.goalReadiness(goal, Map.of(), Map.of()));
        assertEquals(GoalReadiness.READY, planner.goalReadiness(goal, Map.of("iron", 10), Map.of("iron", 10)));
        assertEquals(
                GoalReadiness.READY_WITH_TRADES,
                planner.goalReadiness(goal, Map.of("iron", 0), Map.of("iron", 10)));
    }

    @Test
    void materialsGoal_multiMaterialShortWhenAnyMissing() {
        MaterialsGoal goal = new MaterialsGoal(
                "Bundle",
                List.of(
                        new MaterialRequirement("iron", 5),
                        new MaterialRequirement("phosphorus", 3)),
                GoalPriority.MEDIUM);

        Map<String, Integer> partial = Map.of("iron", 5, "phosphorus", 0);
        assertEquals(GoalReadiness.STILL_SHORT, planner.goalReadiness(goal, partial, partial));

        Map<String, Integer> full = Map.of("iron", 5, "phosphorus", 3);
        assertEquals(GoalReadiness.READY, planner.goalReadiness(goal, full, full));
        assertTrue(goal.isSatisfied(full));
    }
}
