package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Greedy material-trader suggestions for engineering shortfalls.
 */
public final class MaterialTradePlanner {

    private static final List<String> TRADER_TYPE_ORDER = List.of("Raw", "Manufactured", "Encoded");

    private final EngineeringDatabase database;

    public MaterialTradePlanner(EngineeringDatabase database) {
        this.database = database != null ? database : EngineeringDatabase.getInstance();
    }

    public List<TradeSuggestion> suggest(Map<String, Integer> shortfalls,
                                         Map<String, Integer> inventory,
                                         Map<String, Integer> requiredForGoals) {
        List<TradeSuggestion> out = new ArrayList<>();
        if (shortfalls == null || shortfalls.isEmpty() || inventory == null) {
            return out;
        }

        Map<String, Integer> initialInv = copyInventory(inventory);
        Map<String, Integer> workingInv = copyInventory(inventory);
        Map<String, Integer> plannedSpend = new LinkedHashMap<>();

        // Prefer finishing whole shortfalls (green TS rows) before pouring stock into partials.
        for (String toKey : orderShortfallsForMaxFullCoverage(shortfalls, workingInv, requiredForGoals)) {
            int stillNeed = shortfalls.getOrDefault(toKey, 0);
            if (stillNeed <= 0) {
                continue;
            }
            Optional<EngineeringMaterial> toMat = database.material(toKey);
            if (toMat.isEmpty() || !MaterialTraderCatalog.isTradeableAtMaterialTrader(toMat.get())) {
                continue;
            }
            stillNeed = fillShortfall(out, initialInv, workingInv, plannedSpend, requiredForGoals,
                    toKey, toMat.get(), stillNeed);
        }
        out.sort(Comparator
                .comparingInt((TradeSuggestion t) -> traderTypeRank(t.getTraderType()))
                .thenComparing(TradeSuggestion::getToName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(TradeSuggestion::getFromName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /**
     * Fully-coverable shortfalls first (smallest Need first), then closest-to-complete partials,
     * so stock is not wasted on unfinished reds when another material could go green.
     */
    private List<String> orderShortfallsForMaxFullCoverage(Map<String, Integer> shortfalls,
                                                           Map<String, Integer> inventory,
                                                           Map<String, Integer> requiredForGoals) {
        record Rank(String key, boolean full, int sortKey) {
        }
        List<Rank> ranks = new ArrayList<>();
        for (Map.Entry<String, Integer> e : shortfalls.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            int need = e.getValue();
            int obtainable = maxObtainable(e.getKey(), need, inventory, requiredForGoals);
            boolean full = obtainable >= need;
            ranks.add(new Rank(e.getKey(), full, full ? need : need - obtainable));
        }
        ranks.sort(Comparator
                .comparing((Rank r) -> r.full ? 0 : 1)
                .thenComparingInt(Rank::sortKey)
                .thenComparing(r -> database.materialDisplayName(r.key), String.CASE_INSENSITIVE_ORDER));
        List<String> keys = new ArrayList<>(ranks.size());
        for (Rank r : ranks) {
            keys.add(r.key);
        }
        return keys;
    }

    private int maxObtainable(String toKey,
                              int need,
                              Map<String, Integer> inventory,
                              Map<String, Integer> requiredForGoals) {
        if (need <= 0) {
            return 0;
        }
        Optional<EngineeringMaterial> toMat = database.material(toKey);
        if (toMat.isEmpty() || !MaterialTraderCatalog.isTradeableAtMaterialTrader(toMat.get())) {
            return 0;
        }
        List<TradeSuggestion> probe = new ArrayList<>();
        Map<String, Integer> initialInv = copyInventory(inventory);
        Map<String, Integer> workingInv = copyInventory(inventory);
        Map<String, Integer> plannedSpend = new LinkedHashMap<>();
        int remaining = fillShortfall(probe, initialInv, workingInv, plannedSpend, requiredForGoals,
                toKey, toMat.get(), need);
        return need - remaining;
    }

    /** @return how many units of {@code toKey} are still needed after applying affordable trades */
    private int fillShortfall(List<TradeSuggestion> out,
                              Map<String, Integer> initialInv,
                              Map<String, Integer> workingInv,
                              Map<String, Integer> plannedSpend,
                              Map<String, Integer> requiredForGoals,
                              String toKey,
                              EngineeringMaterial toMat,
                              int stillNeed) {
        List<String> sourceKeys = tradeableSourceKeys(workingInv, toKey, toMat, requiredForGoals);
        for (String fromKey : sourceKeys) {
            if (stillNeed <= 0) {
                break;
            }
            Optional<EngineeringMaterial> fromMat = database.material(fromKey);
            if (fromMat.isEmpty()) {
                continue;
            }
            int excess = excessTradeable(fromKey, workingInv, requiredForGoals);
            if (excess <= 0) {
                continue;
            }
            Optional<MaterialTradeRateCalculator.Exchange> exchange =
                    MaterialTradeRateCalculator.planExchange(
                            fromMat.get(), toMat, excess, stillNeed);
            if (exchange.isEmpty() || exchange.get().getToCount() <= 0) {
                continue;
            }
            MaterialTradeRateCalculator.Exchange trade = exchange.get();
            if (trade.getFromCount() > excess) {
                continue;
            }
            if (!canAffordPayMaterial(initialInv, plannedSpend, requiredForGoals, fromKey,
                    trade.getFromCount())) {
                continue;
            }
            TradeSuggestion suggestion = new TradeSuggestion(
                    fromKey,
                    database.materialDisplayName(fromKey),
                    trade.getFromCount(),
                    toKey,
                    database.materialDisplayName(toKey),
                    trade.getToCount(),
                    fromMat.get().getSubtype().equalsIgnoreCase(toMat.getSubtype()),
                    toMat.getType());
            out.add(suggestion);
            recordPlannedSpend(plannedSpend, fromKey, trade.getFromCount());
            applyPlannedPayCost(workingInv, suggestion);
            stillNeed -= trade.getToCount();
        }
        return Math.max(0, stillNeed);
    }

    /** Groups suggestions by trader type in Raw → Manufactured → Encoded order. */
    public static Map<String, List<TradeSuggestion>> groupByTraderType(List<TradeSuggestion> trades) {
        Map<String, List<TradeSuggestion>> grouped = new LinkedHashMap<>();
        for (String type : TRADER_TYPE_ORDER) {
            grouped.put(type, new ArrayList<>());
        }
        if (trades != null) {
            for (TradeSuggestion trade : trades) {
                String type = trade.getTraderType();
                if (type == null || type.isBlank()) {
                    grouped.computeIfAbsent("Other", k -> new ArrayList<>()).add(trade);
                } else {
                    grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(trade);
                }
            }
        }
        grouped.entrySet().removeIf(e -> e.getValue().isEmpty());
        return grouped;
    }

    /**
     * Groups trades by trader type, then by receive material. Each target lists alternative pay options (pick one).
     */
    public static Map<String, List<TradeTargetGroup>> groupByTraderTypeAndTarget(
            List<TradeSuggestion> trades,
            Map<String, Integer> shortfalls) {
        Map<String, List<TradeTargetGroup>> grouped = new LinkedHashMap<>();
        for (Map.Entry<String, List<TradeSuggestion>> entry : groupByTraderType(trades).entrySet()) {
            List<TradeTargetGroup> targets = groupByTarget(entry.getValue(), shortfalls);
            if (!targets.isEmpty()) {
                grouped.put(entry.getKey(), targets);
            }
        }
        return grouped;
    }

    private static List<TradeTargetGroup> groupByTarget(List<TradeSuggestion> trades,
                                                      Map<String, Integer> shortfalls) {
        if (trades == null || trades.isEmpty()) {
            return List.of();
        }
        Map<String, List<TradeSuggestion>> byTarget = new LinkedHashMap<>();
        for (TradeSuggestion trade : trades) {
            byTarget.computeIfAbsent(trade.getToKey(), k -> new ArrayList<>()).add(trade);
        }
        List<TradeTargetGroup> out = new ArrayList<>();
        for (Map.Entry<String, List<TradeSuggestion>> entry : byTarget.entrySet()) {
            List<TradeSuggestion> options = entry.getValue();
            if (options.isEmpty()) {
                continue;
            }
            TradeSuggestion first = options.get(0);
            int shortfall = 0;
            if (shortfalls != null) {
                shortfall = shortfalls.getOrDefault(entry.getKey(), 0);
                if (shortfall <= 0) {
                    for (Map.Entry<String, Integer> sf : shortfalls.entrySet()) {
                        if (sf.getKey() != null && sf.getKey().equalsIgnoreCase(entry.getKey())) {
                            shortfall = sf.getValue() != null ? sf.getValue() : 0;
                            break;
                        }
                    }
                }
            }
            out.add(new TradeTargetGroup(
                    entry.getKey(),
                    first.getToName(),
                    first.getTraderType(),
                    shortfall,
                    options));
        }
        return out;
    }

    private static int traderTypeRank(String type) {
        if (type == null) {
            return TRADER_TYPE_ORDER.size();
        }
        for (int i = 0; i < TRADER_TYPE_ORDER.size(); i++) {
            if (TRADER_TYPE_ORDER.get(i).equalsIgnoreCase(type)) {
                return i;
            }
        }
        return TRADER_TYPE_ORDER.size();
    }

    /**
     * Simulates applying trade suggestions to a copy of inventory (consumes inputs, adds outputs).
     */
    public Map<String, Integer> inventoryAfterTrades(Map<String, Integer> inventory,
                                                     List<TradeSuggestion> trades) {
        Map<String, Integer> inv = copyInventory(inventory);
        if (trades == null) {
            return inv;
        }
        for (TradeSuggestion trade : trades) {
            if (trade == null) {
                continue;
            }
            applyTradeToInventory(inv, trade);
        }
        return inv;
    }

    private static void applyTradeToInventory(Map<String, Integer> inv, TradeSuggestion trade) {
        if (trade == null) {
            return;
        }
        applyPlannedPayCost(inv, trade);
        adjustCount(inv, trade.getToKey(), trade.getToCount());
    }

    /**
     * While planning suggestions, only deduct pay materials. Do not credit receives — otherwise a
     * trade that earns CDC could make a later suggestion spend CDC the commander does not have yet.
     */
    private static void applyPlannedPayCost(Map<String, Integer> inv, TradeSuggestion trade) {
        if (trade == null) {
            return;
        }
        adjustCount(inv, trade.getFromKey(), -trade.getFromCount());
    }

    private static Map<String, Integer> copyInventory(Map<String, Integer> inventory) {
        Map<String, Integer> inv = new LinkedHashMap<>();
        if (inventory != null) {
            for (Map.Entry<String, Integer> e : inventory.entrySet()) {
                if (e.getValue() != null && e.getValue() > 0) {
                    String key = EngineeringMaterialKeys.canonicalKey(e.getKey());
                    if (!key.isBlank()) {
                        inv.merge(key, e.getValue(), Integer::sum);
                    }
                }
            }
        }
        return inv;
    }

    private static void recordPlannedSpend(Map<String, Integer> plannedSpend, String fromKey, int fromCount) {
        if (plannedSpend == null || fromKey == null || fromKey.isBlank() || fromCount <= 0) {
            return;
        }
        String canonical = EngineeringMaterialKeys.canonicalKey(fromKey);
        plannedSpend.merge(canonical, fromCount, Integer::sum);
    }

    private static boolean canAffordPayMaterial(Map<String, Integer> initialInventory,
                                                Map<String, Integer> plannedSpend,
                                                Map<String, Integer> requiredForGoals,
                                                String fromKey,
                                                int fromCount) {
        if (fromCount <= 0) {
            return false;
        }
        String canonical = EngineeringMaterialKeys.canonicalKey(fromKey);
        int owned = initialInventory.getOrDefault(canonical, 0);
        int reserved = reservedForGoals(canonical, requiredForGoals);
        int spent = plannedSpend != null ? plannedSpend.getOrDefault(canonical, 0) : 0;
        return owned - reserved - spent >= fromCount;
    }

    private List<String> tradeableSourceKeys(Map<String, Integer> inventory,
                                             String toKey,
                                             EngineeringMaterial toMat,
                                             Map<String, Integer> requiredForGoals) {
        List<String> keys = new ArrayList<>();
        if (inventory == null || inventory.isEmpty()) {
            return keys;
        }
        for (Map.Entry<String, Integer> owned : inventory.entrySet()) {
            if (owned.getValue() == null || owned.getValue() <= 0) {
                continue;
            }
            String fromKey = owned.getKey();
            if (fromKey == null || fromKey.isBlank() || fromKey.equalsIgnoreCase(toKey)) {
                continue;
            }
            if (excessTradeable(fromKey, inventory, requiredForGoals) <= 0) {
                continue;
            }
            Optional<EngineeringMaterial> fromMat = database.material(fromKey);
            if (fromMat.isEmpty() || !MaterialTraderCatalog.isTradeableAtMaterialTrader(fromMat.get())) {
                continue;
            }
            if (!fromMat.get().getType().equalsIgnoreCase(toMat.getType())) {
                continue;
            }
            keys.add(fromKey);
        }
        keys.sort(Comparator
                .comparing((String key) -> sameGroupRank(key, toMat))
                .thenComparing(key -> database.material(key).map(EngineeringMaterial::getGrade).orElse(99)));
        return keys;
    }

    private static void adjustCount(Map<String, Integer> inv, String key, int delta) {
        if (key == null || key.isBlank() || delta == 0) {
            return;
        }
        String canonical = EngineeringMaterialKeys.canonicalKey(key);
        int next = inv.getOrDefault(canonical, 0) + delta;
        if (next <= 0) {
            inv.remove(canonical);
        } else {
            inv.put(canonical, next);
        }
    }

    private static int excessTradeable(String materialKey,
                                       Map<String, Integer> inventory,
                                       Map<String, Integer> requiredForGoals) {
        String canonical = EngineeringMaterialKeys.canonicalKey(materialKey);
        int owned = inventory.getOrDefault(canonical, 0);
        if (owned <= 0) {
            return 0;
        }
        int reserved = reservedForGoals(canonical, requiredForGoals);
        return Math.max(0, owned - reserved);
    }

    private static int reservedForGoals(String materialKey, Map<String, Integer> requiredForGoals) {
        if (requiredForGoals == null || requiredForGoals.isEmpty()) {
            return 0;
        }
        String canonical = EngineeringMaterialKeys.canonicalKey(materialKey);
        int reserved = 0;
        for (Map.Entry<String, Integer> e : requiredForGoals.entrySet()) {
            if (e.getKey() != null
                    && EngineeringMaterialKeys.canonicalKey(e.getKey()).equals(canonical)) {
                reserved += e.getValue() != null ? e.getValue() : 0;
            }
        }
        return reserved;
    }

    private int sameGroupRank(String fromKey, EngineeringMaterial to) {
        return database.material(fromKey)
                .map(f -> f.getSubtype().equalsIgnoreCase(to.getSubtype()) ? 0 : 1)
                .orElse(2);
    }
}
