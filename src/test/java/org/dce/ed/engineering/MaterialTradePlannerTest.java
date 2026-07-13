package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MaterialTradePlannerTest {

    @Test
    void groupByTraderType_ordersRawManufacturedEncoded() {
        List<TradeSuggestion> trades = List.of(
                new TradeSuggestion("a", "A", 1, "b", "B", 1, true, "Encoded"),
                new TradeSuggestion("c", "C", 1, "d", "D", 1, true, "Raw"),
                new TradeSuggestion("e", "E", 1, "f", "F", 1, true, "Manufactured"));

        Map<String, List<TradeSuggestion>> grouped = MaterialTradePlanner.groupByTraderType(trades);

        assertEquals(List.of("Raw", "Manufactured", "Encoded"), List.copyOf(grouped.keySet()));
        assertEquals(1, grouped.get("Raw").size());
        assertEquals(1, grouped.get("Manufactured").size());
        assertEquals(1, grouped.get("Encoded").size());
    }

    @Test
    void suggest_setsTraderTypeFromTargetMaterial() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        MaterialTradePlanner planner = new MaterialTradePlanner(db);

        Map<String, Integer> shortfalls = Map.of("phosphorus", 1);
        Map<String, Integer> inventory = Map.of("iron", 12);

        List<TradeSuggestion> trades = planner.suggest(shortfalls, inventory, Map.of());
        assertTrue(!trades.isEmpty(), "expected a trade suggestion for phosphorus");
        assertEquals("Raw", trades.get(0).getTraderType());
    }

    @Test
    void suggest_insufficientCrossCategoryInventory_skipsImpossibleTrade() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        MaterialTradePlanner planner = new MaterialTradePlanner(db);

        Map<String, Integer> shortfalls = Map.of("crackedindustrialfirmware", 1);
        Map<String, Integer> inventory = Map.of("dataminedwakeexceptions", 1);

        List<TradeSuggestion> trades = planner.suggest(shortfalls, inventory, Map.of());
        assertTrue(trades.isEmpty(), "1 DMWE cannot buy cracked firmware at 2:3 batch minimum");
    }

    @Test
    void suggest_crossCategory_usesCorrectRateWhenInventoryAllows() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        MaterialTradePlanner planner = new MaterialTradePlanner(db);

        Map<String, Integer> shortfalls = Map.of("crackedindustrialfirmware", 1);
        Map<String, Integer> inventory = Map.of("dataminedwakeexceptions", 2);

        List<TradeSuggestion> trades = planner.suggest(shortfalls, inventory, Map.of());
        assertEquals(1, trades.size());
        TradeSuggestion trade = trades.get(0);
        assertEquals(2, trade.getFromCount());
        assertEquals(3, trade.getToCount());
        assertEquals("Encoded", trade.getTraderType());
    }

    @Test
    void suggest_doesNotTradeMaterialsReservedForGoals() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        MaterialTradePlanner planner = new MaterialTradePlanner(db);

        Map<String, Integer> shortfalls = Map.of("securityfirmwarepatch", 1);
        Map<String, Integer> inventory = Map.of(
                "crackedindustrialfirmware", 10,
                "modifiedconsumerfirmware", 20);
        Map<String, Integer> required = Map.of(
                "crackedindustrialfirmware", 10,
                "securityfirmwarepatch", 1);

        List<TradeSuggestion> trades = planner.suggest(shortfalls, inventory, required);

        assertTrue(trades.stream().noneMatch(t ->
                "crackedindustrialfirmware".equalsIgnoreCase(t.getFromKey())),
                "should not spend CIF already needed for the blueprint");
    }

    @Test
    void suggest_usesOnlyExcessInventoryAboveGoalRequirement() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        MaterialTradePlanner planner = new MaterialTradePlanner(db);

        Map<String, Integer> shortfalls = Map.of("securityfirmwarepatch", 1);
        Map<String, Integer> inventory = Map.of(
                "crackedindustrialfirmware", 16,
                "modifiedconsumerfirmware", 20);
        Map<String, Integer> required = Map.of(
                "crackedindustrialfirmware", 10,
                "securityfirmwarepatch", 1);

        List<TradeSuggestion> trades = planner.suggest(shortfalls, inventory, required);

        assertEquals(1, trades.size());
        assertEquals("crackedindustrialfirmware", trades.get(0).getFromKey());
        assertEquals(6, trades.get(0).getFromCount());
        assertEquals(1, trades.get(0).getToCount());
    }

    @Test
    void groupByTraderTypeAndTarget_groupsAlternativesUnderOneReceiveMaterial() {
        List<TradeSuggestion> trades = List.of(
                new TradeSuggestion("iron", "Iron", 6, "phosphorus", "Phosphorus", 1, true, "Raw"),
                new TradeSuggestion("sulphur", "Sulphur", 2, "phosphorus", "Phosphorus", 1, true, "Raw"),
                new TradeSuggestion("a", "A", 1, "b", "B", 1, true, "Manufactured"));

        Map<String, List<TradeTargetGroup>> grouped = MaterialTradePlanner.groupByTraderTypeAndTarget(
                trades, Map.of("phosphorus", 3));

        assertEquals(2, grouped.size());
        List<TradeTargetGroup> raw = grouped.get("Raw");
        assertEquals(1, raw.size());
        assertEquals("phosphorus", raw.get(0).getToKey());
        assertEquals(3, raw.get(0).getShortfall());
        assertEquals(2, raw.get(0).getOptions().size());
    }

    @Test
    void suggest_crossCategoryG3ToG1_usesTwoForThreeBatch() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        MaterialTradePlanner planner = new MaterialTradePlanner(db);

        Map<String, Integer> shortfalls = Map.of("specialisedlegacyfirmware", 3);
        Map<String, Integer> inventory = Map.of("emissiondata", 47);
        Map<String, Integer> required = Map.of();

        List<TradeSuggestion> trades = planner.suggest(shortfalls, inventory, required);

        assertEquals(1, trades.size());
        assertEquals("emissiondata", trades.get(0).getFromKey());
        assertEquals(2, trades.get(0).getFromCount());
        assertEquals(3, trades.get(0).getToCount());
    }

    @Test
    void suggest_skipsGuardianMaterialsNotAvailableAtTraders() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        MaterialTradePlanner planner = new MaterialTradePlanner(db);

        Map<String, Integer> shortfalls = Map.of("hybridcapacitors", 1);
        Map<String, Integer> inventory = Map.of("guardian_sentinel_weaponparts", 20);
        Map<String, Integer> required = Map.of();

        List<TradeSuggestion> trades = planner.suggest(shortfalls, inventory, required);

        assertTrue(trades.isEmpty(), "guardian materials cannot be traded at material traders");
    }

    @Test
    void suggest_sameRowDowngrade_usesYieldNotLinearRate() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        MaterialTradePlanner planner = new MaterialTradePlanner(db);

        Map<String, Integer> shortfalls = Map.of("heatresistantceramics", 3);
        Map<String, Integer> inventory = Map.of("precipitatedalloys", 10);
        Map<String, Integer> required = Map.of();

        List<TradeSuggestion> trades = planner.suggest(shortfalls, inventory, required);

        assertEquals(1, trades.size());
        assertEquals("precipitatedalloys", trades.get(0).getFromKey());
        assertEquals(1, trades.get(0).getFromCount());
        assertEquals(3, trades.get(0).getToCount());
    }
}
