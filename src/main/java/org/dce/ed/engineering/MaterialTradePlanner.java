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

        Map<String, Integer> workingInv = copyInventory(inventory);

        for (Map.Entry<String, Integer> need : shortfalls.entrySet()) {
            String toKey = need.getKey();
            int stillNeed = need.getValue();
            if (stillNeed <= 0) {
                continue;
            }
            Optional<EngineeringMaterial> toMat = database.material(toKey);
            if (toMat.isEmpty() || !MaterialTraderCatalog.isTradeableAtMaterialTrader(toMat.get())) {
                continue;
            }

            List<String> sourceKeys = tradeableSourceKeys(workingInv, toKey, toMat.get(), requiredForGoals);
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
                                fromMat.get(), toMat.get(), excess, stillNeed);
                if (exchange.isEmpty() || exchange.get().getToCount() <= 0) {
                    continue;
                }
                MaterialTradeRateCalculator.Exchange trade = exchange.get();
                if (trade.getFromCount() > excess) {
                    continue;
                }
                TradeSuggestion suggestion = new TradeSuggestion(
                        fromKey,
                        database.materialDisplayName(fromKey),
                        trade.getFromCount(),
                        toKey,
                        database.materialDisplayName(toKey),
                        trade.getToCount(),
                        fromMat.get().getSubtype().equalsIgnoreCase(toMat.get().getSubtype()),
                        toMat.get().getType());
                out.add(suggestion);
                applyTradeToInventory(workingInv, suggestion);
                stillNeed -= trade.getToCount();
            }
        }
        out.sort(Comparator
                .comparingInt((TradeSuggestion t) -> traderTypeRank(t.getTraderType()))
                .thenComparing(TradeSuggestion::getToName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(TradeSuggestion::getFromName, String.CASE_INSENSITIVE_ORDER));
        return out;
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
        Map<String, Integer> inv = new LinkedHashMap<>();
        if (inventory != null) {
            for (Map.Entry<String, Integer> e : inventory.entrySet()) {
                if (e.getValue() != null && e.getValue() > 0) {
                    inv.put(e.getKey(), e.getValue());
                }
            }
        }
        if (trades == null) {
            return inv;
        }
        for (TradeSuggestion trade : trades) {
            applyTradeToInventory(inv, trade);
        }
        return inv;
    }

    private static void applyTradeToInventory(Map<String, Integer> inv, TradeSuggestion trade) {
        if (trade == null) {
            return;
        }
        adjustCount(inv, trade.getFromKey(), -trade.getFromCount());
        adjustCount(inv, trade.getToKey(), trade.getToCount());
    }

    private static Map<String, Integer> copyInventory(Map<String, Integer> inventory) {
        Map<String, Integer> inv = new LinkedHashMap<>();
        if (inventory != null) {
            for (Map.Entry<String, Integer> e : inventory.entrySet()) {
                if (e.getValue() != null && e.getValue() > 0) {
                    inv.put(e.getKey(), e.getValue());
                }
            }
        }
        return inv;
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
                .thenComparing(key -> -database.material(key).map(EngineeringMaterial::getGrade).orElse(0)));
        return keys;
    }

    private static void adjustCount(Map<String, Integer> inv, String key, int delta) {
        if (key == null || key.isBlank() || delta == 0) {
            return;
        }
        int next = inv.getOrDefault(key, 0) + delta;
        if (next <= 0) {
            inv.remove(key);
        } else {
            inv.put(key, next);
        }
    }

    private static int excessTradeable(String materialKey,
                                       Map<String, Integer> inventory,
                                       Map<String, Integer> requiredForGoals) {
        int owned = EngineeringMaterialKeys.countInInventory(inventory, materialKey);
        if (owned <= 0) {
            return 0;
        }
        int reserved = 0;
        if (requiredForGoals != null) {
            reserved = EngineeringMaterialKeys.countInInventory(requiredForGoals, materialKey);
        }
        return Math.max(0, owned - reserved);
    }

    private int sameGroupRank(String fromKey, EngineeringMaterial to) {
        return database.material(fromKey)
                .map(f -> f.getSubtype().equalsIgnoreCase(to.getSubtype()) ? 0 : 1)
                .orElse(2);
    }
}
