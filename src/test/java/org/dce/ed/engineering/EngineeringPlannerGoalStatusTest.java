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
}
