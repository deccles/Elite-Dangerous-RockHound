package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Planner / trade-rate coverage built from real CMDR Villunus journal + EDO session
 * data (Anaconda · Exception Handler, 2026-07-24 trader session).
 *
 * <p>Fixtures under {@code src/test/resources/engineering/journal/} — not live journal reads.
 */
class EngineeringPlannerJournalScenarioTest {

    private static final String FIXTURE_DIR = "/engineering/journal/";

    private static EngineeringDatabase db;
    private static EngineeringPlanner planner;
    private static MaterialTradePlanner tradePlanner;

    private static Map<String, Integer> anacondaInventory;
    private static List<EngineeringGoal> anacondaGoals;
    private static List<RecordedTrade> sessionTrades;

    @BeforeAll
    static void loadFixtures() {
        db = EngineeringDatabase.getInstance();
        planner = new EngineeringPlanner(db);
        tradePlanner = new MaterialTradePlanner(db);
        anacondaInventory = loadInventory(FIXTURE_DIR + "materials-anaconda-2026-07-24T133214.json");
        anacondaGoals = loadGoals(FIXTURE_DIR + "goals-anaconda-exception-handler.json");
        sessionTrades = loadTrades(FIXTURE_DIR + "material-trades-2026-07-24-session.json");
    }

    @Test
    void fixtures_anacondaExceptionHandlerGoalsMatchScreenshotSession() {
        assertEquals(7, anacondaGoals.size());
        assertTrue(anacondaGoals.stream().anyMatch(g ->
                "Armour".equals(g.getModuleType()) && g.getPriority() == GoalPriority.HIGH));
        assertTrue(anacondaGoals.stream().anyMatch(g ->
                "Hull Reinforcement Package".equals(g.getModuleType()) && g.getQuantity() == 2));
        assertTrue(anacondaGoals.stream().anyMatch(g ->
                "Shield Booster".equals(g.getModuleType())
                        && "Heavy Duty".equals(g.getBlueprintName())
                        && g.getQuantity() == 3));
        assertTrue(anacondaGoals.stream().anyMatch(g ->
                "Life Support".equals(g.getModuleType())));
        assertEquals(0, anacondaInventory.getOrDefault("phasealloys", 0));
        assertEquals(100, anacondaInventory.getOrDefault("protoheatradiators", 0));
        assertEquals(6, anacondaInventory.getOrDefault("shieldfrequencydata", 0));
    }

    @Test
    void planByPriority_anacondaSession_neverPaysMaterialsNotOwned() {
        EngineeringPlanner.PriorityPlanResult plan =
                planner.planByPriority(anacondaGoals, List.of(), anacondaInventory, tradePlanner);

        assertFalse(plan.trades().isEmpty(), "expected trade suggestions for Anaconda shortfalls");

        for (TradeSuggestion trade : plan.trades()) {
            int owned = anacondaInventory.getOrDefault(
                    EngineeringMaterialKeys.canonicalKey(trade.getFromKey()), 0);
            // Pay stock is depleted across the plan; cumulative pays must stay within on-hand.
            // Per-suggestion check: from material must exist in the starting dump (owned > 0).
            assertTrue(owned > 0,
                    () -> "suggested pay " + trade.getFromName()
                            + " but journal Materials dump had 0 (owned=" + owned + ")");
        }

        // Specific regression from the Phase Alloys screenshot: never pay Phase when owned=0.
        assertTrue(plan.trades().stream().noneMatch(
                        t -> "phasealloys".equalsIgnoreCase(t.getFromKey())),
                "must not suggest paying Phase Alloys when Materials dump has 0");
    }

    @Test
    void planByPriority_anacondaSession_shortGoalsExposeRemainingShortfalls() {
        EngineeringPlanner.PriorityPlanResult plan =
                planner.planByPriority(anacondaGoals, List.of(), anacondaInventory, tradePlanner);

        List<EngineeringGoal> shortGoals = anacondaGoals.stream()
                .filter(g -> plan.readinessByBlueprintGoal().get(g) == GoalReadiness.STILL_SHORT)
                .toList();
        assertFalse(shortGoals.isEmpty(),
                "this inventory cannot finish every Anaconda goal — expected some Short");

        assertFalse(plan.shortfallsRemainingAfterPlan().isEmpty(),
                "Short goals must leave remaining shortfalls for Trade Suggestions");

        for (EngineeringGoal goal : shortGoals) {
            Map<String, Integer> stillNeed =
                    planner.goalMaterialShortfalls(goal, plan.inventoryAfterTrades());
            // After aggregate trades (no claims) a Short goal may still look covered; the plan
            // remaining map is the authoritative Short signal for the UI.
            assertTrue(
                    plan.shortfallsRemainingAfterPlan().keySet().stream().anyMatch(k ->
                            planner.materialsForGoal(goal).containsKey(k)
                                    || planner.materialsForGoal(goal).keySet().stream()
                                    .anyMatch(need -> EngineeringMaterialKeys.canonicalKey(need)
                                            .equalsIgnoreCase(k))),
                    () -> "remaining shortfalls should include mats for Short goal "
                            + goal.getModuleType() + " / " + goal.getBlueprintName()
                            + " stillNeedVsShopping=" + stillNeed
                            + " remaining=" + plan.shortfallsRemainingAfterPlan());
        }
    }

    @Test
    void planByPriority_anacondaSession_paysStayWithinCumulativeOnHandStock() {
        EngineeringPlanner.PriorityPlanResult plan =
                planner.planByPriority(anacondaGoals, List.of(), anacondaInventory, tradePlanner);

        Map<String, Integer> remainingPay = new HashMap<>(anacondaInventory);
        for (TradeSuggestion trade : plan.trades()) {
            String from = EngineeringMaterialKeys.canonicalKey(trade.getFromKey());
            int have = remainingPay.getOrDefault(from, 0);
            assertTrue(have >= trade.getFromCount(),
                    () -> "plan spent " + trade.getFromCount() + " " + from
                            + " but only " + have + " remained from journal dump");
            remainingPay.put(from, have - trade.getFromCount());
        }
    }

    @Test
    void recordedJournalTrades_2026_07_24_session_matchCalculator() {
        assertFalse(sessionTrades.isEmpty());
        int checked = 0;
        for (RecordedTrade trade : sessionTrades) {
            Optional<EngineeringMaterial> from = db.material(trade.paidKey());
            Optional<EngineeringMaterial> to = db.material(trade.recvKey());
            if (from.isEmpty() || to.isEmpty()) {
                continue;
            }
            if (!MaterialTraderCatalog.isTradeableAtMaterialTrader(from.get())
                    || !MaterialTraderCatalog.isTradeableAtMaterialTrader(to.get())) {
                continue;
            }
            Optional<MaterialTradeRateCalculator.Exchange> planned =
                    MaterialTradeRateCalculator.planExchange(
                            from.get(), to.get(), trade.paidQty(), trade.recvQty());
            assertTrue(planned.isPresent(),
                    () -> trade.paidKey() + " x" + trade.paidQty()
                            + " -> " + trade.recvKey() + " x" + trade.recvQty()
                            + " @" + trade.timestamp() + " not tradable by calculator");
            assertEquals(trade.paidQty(), planned.get().getFromCount(), trade.timestamp());
            assertEquals(trade.recvQty(), planned.get().getToCount(), trade.timestamp());
            checked++;
        }
        assertTrue(checked >= 50, "expected most of the 71 session trades to be calculator-checked, got " + checked);
    }

    @Test
    void recordedJournalTrades_includeScreenshotStyleRawAndEncodedExchanges() {
        // Screenshot Trade Suggestions: Mn→Cd, Zn/Iron→Tin, shieldfrequencydata→shield data.
        assertTrue(sessionTrades.stream().anyMatch(t ->
                        "manganese".equals(t.paidKey()) && "cadmium".equals(t.recvKey())),
                "journal should include Manganese → Cadmium from this session");
        assertTrue(sessionTrades.stream().anyMatch(t ->
                        "iron".equals(t.paidKey()) && "tin".equals(t.recvKey())),
                "journal should include Iron → Tin");
        assertTrue(sessionTrades.stream().anyMatch(t ->
                        "zinc".equals(t.paidKey()) && "tin".equals(t.recvKey())),
                "journal should include Zinc → Tin");
        assertTrue(sessionTrades.stream().anyMatch(t ->
                        "shieldfrequencydata".equals(t.paidKey())
                                && "shieldcyclerecordings".equals(t.recvKey())),
                "journal should include Shield Frequency Data → Cycle Recordings");
        assertTrue(sessionTrades.stream().anyMatch(t ->
                        "protoheatradiators".equals(t.paidKey())
                                && "compoundshielding".equals(t.recvKey())),
                "journal should include Proto Heat Radiators → Compound Shielding");
    }

    @Test
    void suggest_fromAnacondaInventory_canCoverCompoundShieldingLikeJournalTrade() {
        // First real trade of the session: Proto Heat Radiators 10 → Compound Shielding 5.
        Map<String, Integer> shortfalls = Map.of("compoundshielding", 5);
        List<TradeSuggestion> trades = tradePlanner.suggest(shortfalls, anacondaInventory, Map.of());
        assertTrue(trades.stream().anyMatch(t ->
                        "compoundshielding".equalsIgnoreCase(t.getToKey())
                                && "protoheatradiators".equalsIgnoreCase(t.getFromKey())),
                "planner should offer Proto Heat Radiators → Compound Shielding with this dump");
    }

    @Test
    void planByPriority_phaseAlloysZero_cannotPayPhaseForConductive_evenAfterProtoTrade() {
        // Repro shape from live inventory: Phase=0, some Proto Light, need Phase then Conductive.
        MaterialsGoal needPhase = new MaterialsGoal(
                "Phase alloys",
                List.of(new MaterialRequirement("phasealloys", 10)),
                GoalPriority.HIGH);
        MaterialsGoal needConductive = new MaterialsGoal(
                "Conductive components",
                List.of(new MaterialRequirement("conductivecomponents", 3)),
                GoalPriority.LOW);

        Map<String, Integer> inv = new LinkedHashMap<>(anacondaInventory);
        inv.put("phasealloys", 0);
        inv.put("conductivecomponents", 0);
        // Boost Proto Light so the high goal can trade for Phase (overshoot leaves virtual Phase).
        inv.put("protolightalloys", Math.max(12, inv.getOrDefault("protolightalloys", 0)));

        EngineeringPlanner.PriorityPlanResult plan = planner.planByPriority(
                List.of(), List.of(needPhase, needConductive), inv, tradePlanner);

        assertTrue(plan.trades().stream().anyMatch(t -> "phasealloys".equalsIgnoreCase(t.getToKey())),
                "should acquire Phase Alloys for the high goal");
        assertTrue(plan.trades().stream().noneMatch(t -> "phasealloys".equalsIgnoreCase(t.getFromKey())),
                "must not pay Phase Alloys that only exist after a suggested trade");
    }

    @Test
    void shoppingList_afterRecordedJournalTrades_stillNeedShrinksForReceivedMats() {
        Map<String, Integer> shortBefore = planner.shortfalls(anacondaGoals, anacondaInventory);
        assertFalse(shortBefore.isEmpty(), "Anaconda goals should have shortfalls before trades");

        List<TradeSuggestion> recorded = sessionTradesAsSuggestions();
        assertFalse(recorded.isEmpty());
        Map<String, Integer> after = tradePlanner.inventoryAfterTrades(anacondaInventory, recorded);

        List<ShoppingListRow> shopping =
                planner.buildShoppingList(anacondaGoals, anacondaInventory, after);
        assertFalse(shopping.isEmpty());

        // Materials the journal actually received should show lower (or zero) still-need.
        assertTrue(stillNeed(shopping, "compoundshielding")
                        < shortBefore.getOrDefault("compoundshielding", 0)
                        || stillNeed(shopping, "compoundshielding") == 0,
                "Compound Shielding Need should drop after recorded Proto→Compound trades");
        assertTrue(stillNeed(shopping, "cadmium") < shortBefore.getOrDefault("cadmium", Integer.MAX_VALUE)
                        || stillNeed(shopping, "cadmium") == 0,
                "Cadmium Need should drop after recorded Mn→Cd trades");
        assertTrue(stillNeed(shopping, "tin") < shortBefore.getOrDefault("tin", Integer.MAX_VALUE)
                        || stillNeed(shopping, "tin") == 0,
                "Tin Need should drop after recorded Fe/Zn→Tin trades");

        int totalStillNeed = shopping.stream().mapToInt(ShoppingListRow::getShortfallAfterTrades).sum();
        int totalBefore = shortBefore.values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(totalStillNeed < totalBefore,
                "overall still-need after journal trades must be lower ("
                        + totalStillNeed + " vs " + totalBefore + ")");
    }

    @Test
    void shoppingList_afterRecordedJournalTrades_matchesManualJournalDeltas() {
        List<TradeSuggestion> recorded = sessionTradesAsSuggestions();
        Map<String, Integer> afterPlanner =
                tradePlanner.inventoryAfterTrades(anacondaInventory, recorded);

        Map<String, Integer> afterManual = new LinkedHashMap<>(anacondaInventory);
        for (RecordedTrade trade : sessionTrades) {
            String paid = EngineeringMaterialKeys.canonicalKey(trade.paidKey());
            String recv = EngineeringMaterialKeys.canonicalKey(trade.recvKey());
            afterManual.merge(paid, -trade.paidQty(), (a, b) -> Math.max(0, a + b));
            afterManual.merge(recv, trade.recvQty(), Integer::sum);
        }
        afterManual.entrySet().removeIf(e -> e.getValue() == null || e.getValue() <= 0);

        for (String key : List.of(
                "compoundshielding", "cadmium", "tin", "hybridcapacitors",
                "polymercapacitors", "protoheatradiators")) {
            assertEquals(
                    afterManual.getOrDefault(key, 0),
                    afterPlanner.getOrDefault(key, 0),
                    "planner inventoryAfterTrades vs manual journal deltas for " + key);
        }
    }

    private static int stillNeed(List<ShoppingListRow> shopping, String materialKey) {
        String want = EngineeringMaterialKeys.canonicalKey(materialKey);
        for (ShoppingListRow row : shopping) {
            if (EngineeringMaterialKeys.canonicalKey(row.getMaterialKey()).equals(want)) {
                return row.getShortfallAfterTrades();
            }
        }
        return 0;
    }

    private List<TradeSuggestion> sessionTradesAsSuggestions() {
        List<TradeSuggestion> out = new ArrayList<>();
        for (RecordedTrade trade : sessionTrades) {
            Optional<EngineeringMaterial> from = db.material(trade.paidKey());
            Optional<EngineeringMaterial> to = db.material(trade.recvKey());
            if (from.isEmpty() || to.isEmpty()) {
                continue;
            }
            out.add(new TradeSuggestion(
                    trade.paidKey(),
                    from.get().getName(),
                    trade.paidQty(),
                    trade.recvKey(),
                    to.get().getName(),
                    trade.recvQty(),
                    from.get().getSubtype().equalsIgnoreCase(to.get().getSubtype()),
                    to.get().getType()));
        }
        return out;
    }

    private static Map<String, Integer> loadInventory(String resource) {
        JsonObject root = readJson(resource).getAsJsonObject();
        JsonObject inv = root.getAsJsonObject("inventory");
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : inv.entrySet()) {
            out.put(EngineeringMaterialKeys.canonicalKey(e.getKey()), e.getValue().getAsInt());
        }
        return out;
    }

    private static List<EngineeringGoal> loadGoals(String resource) {
        JsonObject root = readJson(resource).getAsJsonObject();
        JsonArray arr = root.getAsJsonArray("goals");
        List<EngineeringGoal> out = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject g = el.getAsJsonObject();
            out.add(new EngineeringGoal(
                    text(g, "blueprintId"),
                    text(g, "moduleType"),
                    text(g, "blueprintName"),
                    g.get("fromGrade").getAsInt(),
                    g.get("craftsAtCurrentGrade").getAsInt(),
                    g.get("targetGrade").getAsInt(),
                    text(g, "experimentalId"),
                    GoalPriority.valueOf(text(g, "priority")),
                    g.get("experimentalApplied").getAsBoolean(),
                    g.get("quantity").getAsInt(),
                    g.get("completedUnits").getAsInt(),
                    g.get("shipId").getAsLong(),
                    text(g, "shipLabel"),
                    g.get("includeInPlanning").getAsBoolean()));
        }
        return out;
    }

    private static List<RecordedTrade> loadTrades(String resource) {
        JsonObject root = readJson(resource).getAsJsonObject();
        JsonArray arr = root.getAsJsonArray("trades");
        List<RecordedTrade> out = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject t = el.getAsJsonObject();
            out.add(new RecordedTrade(
                    text(t, "timestamp"),
                    text(t, "paidKey"),
                    t.get("paidQty").getAsInt(),
                    text(t, "recvKey"),
                    t.get("recvQty").getAsInt()));
        }
        return out;
    }

    private static JsonElement readJson(String resource) {
        try (Reader reader = new InputStreamReader(
                EngineeringPlannerJournalScenarioTest.class.getResourceAsStream(resource),
                StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (Exception e) {
            throw new IllegalStateException("missing fixture " + resource, e);
        }
    }

    private static String text(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el == null || el.isJsonNull() ? "" : el.getAsString();
    }

    private record RecordedTrade(
            String timestamp, String paidKey, int paidQty, String recvKey, int recvQty) {
    }
}
