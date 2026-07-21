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
    void materialsGoal_readiness_readyIsStockedNotCraftComplete() {
        MaterialsGoal goal = new MaterialsGoal(
                "Need iron",
                List.of(new MaterialRequirement("iron", 10)),
                GoalPriority.MEDIUM);
        assertEquals(GoalReadiness.STILL_SHORT, planner.goalReadiness(goal, Map.of(), Map.of()));
        // Stocked reserve is READY; the Engineering tab labels that "Ready" (not "Complete").
        assertEquals(GoalReadiness.READY, planner.goalReadiness(goal, Map.of("iron", 10), Map.of("iron", 10)));
        assertEquals(
                GoalReadiness.READY_WITH_TRADES,
                planner.goalReadiness(goal, Map.of("iron", 0), Map.of("iron", 10)));
        assertTrue(planner.isGoalReady(goal, Map.of("iron", 10)));
        assertFalse(planner.isGoalReady(goal, Map.of("iron", 3)));
    }

    @Test
    void materialsGoal_acquisitionTarget_ownedPlusNeedCreatesShortfall() {
        // Mirrors Add-dialog semantics: Need=7 more while owning 5 → target 12 → shortfall 7.
        int owned = 5;
        int needMore = 7;
        int target = owned + needMore;
        MaterialsGoal goal = new MaterialsGoal(
                "Acquire iron",
                List.of(new MaterialRequirement("iron", target)),
                GoalPriority.MEDIUM);
        Map<String, Integer> inv = Map.of("iron", owned);
        assertEquals(GoalReadiness.STILL_SHORT, planner.goalReadiness(goal, inv, inv));
        assertEquals(needMore, goal.shortfalls(inv).get("iron").intValue());
    }

    @Test
    void planByPriority_doesNotTradeAwayStockReservedForLaterGoal() {
        // Repro: commander owns exactly the 5 Compound Shielding a later goal needs. The
        // higher-priority Heat Exchangers goal must not spend them (which used to produce a
        // circular "Need 0" buy-back suggestion for Compound Shielding).
        MaterialsGoal heatExchangers = new MaterialsGoal(
                "Heat exchangers",
                List.of(new MaterialRequirement("heatexchangers", 6)),
                GoalPriority.HIGH);
        MaterialsGoal compoundShielding = new MaterialsGoal(
                "Compound shielding",
                List.of(new MaterialRequirement("compoundshielding", 5)),
                GoalPriority.LOW);

        Map<String, Integer> inv = Map.of("compoundshielding", 5);

        EngineeringPlanner.PriorityPlanResult plan = planner.planByPriority(
                List.of(), List.of(heatExchangers, compoundShielding), inv, tradePlanner);

        assertTrue(plan.trades().stream().noneMatch(
                        t -> "compoundshielding".equalsIgnoreCase(t.getFromKey())),
                "must not spend Compound Shielding reserved for the later goal");
        assertTrue(plan.trades().stream().noneMatch(
                        t -> "compoundshielding".equalsIgnoreCase(t.getToKey())),
                "must not suggest buying back a material with no overall shortfall");
        assertEquals(GoalReadiness.READY, plan.readinessByMaterialsGoal().get(compoundShielding));
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
