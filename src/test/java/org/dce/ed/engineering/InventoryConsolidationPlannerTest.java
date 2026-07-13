package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class InventoryConsolidationPlannerTest {

    @Test
    void inventoryConsolidationGoal_isRecognized() {
        EngineeringGoal goal = EngineeringGoal.inventoryConsolidation(2, 4);
        assertTrue(goal.isInventoryConsolidation());
        assertEquals(2, goal.getFromGrade());
        assertEquals(4, goal.getTargetGrade());
        assertTrue(goal.displayLabel().contains("G4"));
    }

    @Test
    void suggest_upgradesExcessG2WithinSameRow() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        InventoryConsolidationPlanner planner = new InventoryConsolidationPlanner(db);
        EngineeringGoal goal = EngineeringGoal.inventoryConsolidation(2, 3);

        Map<String, Integer> inventory = Map.of("filamentcomposites", 18);
        List<TradeSuggestion> trades = planner.suggest(goal, inventory, Map.of());

        assertFalse(trades.isEmpty());
        TradeSuggestion first = trades.get(0);
        assertEquals("filamentcomposites", first.getFromKey());
        assertEquals(18, first.getFromCount());
        assertEquals(3, first.getToCount());
        assertTrue(first.isSameGroup());
    }

    @Test
    void suggest_respectsMaterialsReservedForBlueprintGoals() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        InventoryConsolidationPlanner planner = new InventoryConsolidationPlanner(db);
        EngineeringGoal goal = EngineeringGoal.inventoryConsolidation(2, 4);

        Map<String, Integer> inventory = Map.of("filamentcomposites", 20);
        Map<String, Integer> reserved = Map.of("filamentcomposites", 15);
        List<TradeSuggestion> trades = planner.suggest(goal, inventory, reserved);

        assertTrue(trades.isEmpty());
        assertEquals(5, planner.excessCommonUnits(goal, inventory, reserved));
    }

    @Test
    void suggest_chainsUpToTargetGrade() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        InventoryConsolidationPlanner planner = new InventoryConsolidationPlanner(db);
        EngineeringGoal goal = EngineeringGoal.inventoryConsolidation(2, 4);

        Map<String, Integer> inventory = Map.of("exceptionalscrambledemissiondata", 216);
        List<TradeSuggestion> trades = planner.suggest(goal, inventory, Map.of());

        assertFalse(trades.isEmpty());
        assertTrue(trades.stream().anyMatch(t -> db.material(t.getToKey())
                .map(EngineeringMaterial::getGrade).orElse(0) >= 3));
        int freed = trades.stream().mapToInt(t -> t.getFromCount() - t.getToCount()).sum();
        assertTrue(freed > 0);
    }
}
