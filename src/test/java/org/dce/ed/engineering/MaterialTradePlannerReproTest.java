package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Reproduces user inventory from debug session 9cb2ef. */
class MaterialTradePlannerReproTest {

    @Test
    void suggest_precipitatedShortfall_prefersLowerGradePayMaterialOverCdc() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        MaterialTradePlanner planner = new MaterialTradePlanner(db);

        Map<String, Integer> shortfalls = Map.of("precipitatedalloys", 3);
        Map<String, Integer> inventory = Map.of(
                "conductivepolymers", 25,
                "fedcorecomposites", 2);

        List<TradeSuggestion> trades = planner.suggest(shortfalls, inventory, Map.of());

        assertTrue(trades.stream().anyMatch(t -> "precipitatedalloys".equalsIgnoreCase(t.getToKey())),
                "expected precipitated trade");
        assertTrue(trades.stream().noneMatch(
                        t -> "fedcorecomposites".equalsIgnoreCase(t.getFromKey())),
                "should prefer lower-grade conductive polymers over G5 CDC when both can cover shortfall");
    }

    @Test
    void suggest_userInventoryFromSession9cb2ef_avoidsCdcWhenLowerGradeStockExists() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        MaterialTradePlanner planner = new MaterialTradePlanner(db);

        Map<String, Integer> shortfalls = Map.of("precipitatedalloys", 3);
        Map<String, Integer> inventory = new LinkedHashMap<>();
        inventory.put("conductivepolymers", 25);
        inventory.put("fedcorecomposites", 2);
        inventory.put("precipitatedalloys", 17);
        inventory.put("heatexchangers", 18);
        Map<String, Integer> required = Map.of(
                "heatexchangers", 15,
                "precipitatedalloys", 20);

        List<TradeSuggestion> trades = planner.suggest(shortfalls, inventory, required);

        assertTrue(trades.stream().anyMatch(t -> "precipitatedalloys".equalsIgnoreCase(t.getToKey())),
                "expected a trade targeting precipitated alloys");
        assertTrue(trades.stream().noneMatch(
                        t -> "fedcorecomposites".equalsIgnoreCase(t.getFromKey())),
                "should not spend G5 CDC when lower-grade manufactured stock can cover the shortfall");
    }
}
