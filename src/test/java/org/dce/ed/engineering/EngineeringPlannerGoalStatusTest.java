package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EngineeringPlannerGoalStatusTest {

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
    void goalReadiness_readyWhenInventoryCoversMaterials() {
        BlueprintGrade g1 = db.gradesFor("Power Distributor", "Charge Enhanced").stream()
                .filter(b -> b.getGrade() == 1)
                .findFirst()
                .orElseThrow();

        EngineeringGoal goal = new EngineeringGoal(
                g1.getId(), g1.getModuleType(), g1.getName(), 0, 1, "");

        Map<String, Integer> required = planner.materialsForGoal(goal);
        assertFalse(required.isEmpty());
        assertFalse(planner.isGoalReady(goal, Map.of()));

        Map<String, Integer> stocked = new java.util.HashMap<>();
        for (Map.Entry<String, Integer> e : required.entrySet()) {
            stocked.put(e.getKey(), e.getValue());
        }
        assertTrue(planner.isGoalReady(goal, stocked));
        assertEquals(GoalReadiness.READY, planner.goalReadiness(goal, stocked, stocked));
    }

    @Test
    void inventoryAfterTrades_addsOutputsAndConsumesInputs() {
        TradeSuggestion trade = new TradeSuggestion(
                "iron", "Iron", 6,
                "phosphorus", "Phosphorus", 1,
                true);
        Map<String, Integer> inv = Map.of("iron", 12, "phosphorus", 0);
        Map<String, Integer> after = tradePlanner.inventoryAfterTrades(inv, List.of(trade));
        assertEquals(6, after.get("iron"));
        assertEquals(1, after.get("phosphorus"));
    }

    /**
     * Aggregate trade planning can spend fodder on a lower-priority shortfall first.
     * Priority planning must cover High before Lower goals get that stock.
     */
    @Test
    void planByPriority_highClaimsTradeStockBeforeLowerGoals() {
        BlueprintGrade highBp = db.gradesFor("Shield Booster", "Resistance Augmented").stream()
                .filter(b -> b.getGrade() == 5)
                .findFirst()
                .orElseThrow();
        BlueprintGrade lowBp = db.gradesFor("Power Distributor", "Charge Enhanced").stream()
                .filter(b -> b.getGrade() == 5)
                .findFirst()
                .orElseThrow();

        EngineeringGoal high = new EngineeringGoal(
                highBp.getId(), highBp.getModuleType(), highBp.getName(), 0, 0, 5, "",
                GoalPriority.HIGH);
        EngineeringGoal low = new EngineeringGoal(
                lowBp.getId(), lowBp.getModuleType(), lowBp.getName(), 0, 0, 5, "",
                GoalPriority.LOW);

        Map<String, Integer> highNeed = planner.materialsForGoal(high);
        Map<String, Integer> lowNeed = planner.materialsForGoal(low);
        assertFalse(highNeed.isEmpty());
        assertFalse(lowNeed.isEmpty());

        // Big feeder stock, but almost none of the materials these G5 goals actually need.
        Map<String, Integer> inv = new java.util.HashMap<>();
        inv.put("iron", 400);
        inv.put("phosphorus", 400);
        inv.put("sulphur", 400);
        inv.put("manganese", 400);
        inv.put("nickel", 400);
        inv.put("zinc", 400);
        inv.put("chromium", 200);
        inv.put("vanadium", 200);
        inv.put("selenium", 200);
        inv.put("cadmium", 80);
        inv.put("tin", 80);
        inv.put("mercury", 40);
        inv.put("molybdenum", 40);
        inv.put("conductivecomponents", 120);
        inv.put("heatconductionwiring", 120);
        inv.put("chemicaldistillery", 80);
        inv.put("chemicalprocessors", 80);
        inv.put("gridresistors", 80);
        inv.put("hybridcapacitors", 80);
        inv.put("wornshieldemitters", 80);
        inv.put("shieldemitters", 80);
        inv.put("compoundshielding", 40);
        inv.put("protolightalloys", 60);
        inv.put("protoradiolicalloys", 60);
        inv.put("proprietarycomposites", 60);
        inv.put("protoheatradiators", 40);

        EngineeringPlanner.PriorityPlanResult highOnly =
                planner.planByPriority(List.of(high), inv, tradePlanner);
        GoalReadiness highAlone = highOnly.readinessByGoal().get(high);
        assertTrue(
                highAlone == GoalReadiness.READY || highAlone == GoalReadiness.READY_WITH_TRADES,
                "High alone should be completable (got " + highAlone + ")");

        EngineeringPlanner.PriorityPlanResult both =
                planner.planByPriority(List.of(high, low), inv, tradePlanner);
        GoalReadiness highWithCompete = both.readinessByGoal().get(high);
        assertTrue(
                highWithCompete == GoalReadiness.READY || highWithCompete == GoalReadiness.READY_WITH_TRADES,
                "High must stay completable when Lower goals compete (got " + highWithCompete + ")");

        // Old path: one pooled trade pass, then claim. That can leave High SHORT while Low is covered.
        Map<String, Integer> pooledShort = planner.shortfalls(List.of(high, low), inv);
        Map<String, Integer> pooledRequired = planner.requiredMaterials(List.of(high, low));
        List<TradeSuggestion> pooledTrades = tradePlanner.suggest(pooledShort, inv, pooledRequired);
        Map<String, Integer> afterPooled = tradePlanner.inventoryAfterTrades(inv, pooledTrades);
        Map<EngineeringGoal, GoalReadiness> oldClaim =
                planner.goalReadinessWithPriorityClaim(List.of(high, low), inv, afterPooled);
        // Document the regression: if pooled trades starve High, priority plan must still save it.
        if (oldClaim.get(high) == GoalReadiness.STILL_SHORT) {
            assertTrue(
                    highWithCompete == GoalReadiness.READY
                            || highWithCompete == GoalReadiness.READY_WITH_TRADES);
        }
    }
}
