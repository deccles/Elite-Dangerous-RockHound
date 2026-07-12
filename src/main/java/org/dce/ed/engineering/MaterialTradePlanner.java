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

            List<Map.Entry<String, Integer>> sources = new ArrayList<>();
            for (Map.Entry<String, Integer> owned : inventory.entrySet()) {
                if (owned.getValue() == null || owned.getValue() <= 0) {
                    continue;
                }
                if (owned.getKey().equalsIgnoreCase(toKey)) {
                    continue;
                }
                int excess = excessTradeable(owned.getKey(), owned.getValue(), requiredForGoals);
                if (excess <= 0) {
                    continue;
                }
                Optional<EngineeringMaterial> fromMat = database.material(owned.getKey());
                if (fromMat.isEmpty() || !MaterialTraderCatalog.isTradeableAtMaterialTrader(fromMat.get())) {
                    continue;
                }
                if (!fromMat.get().getType().equalsIgnoreCase(toMat.get().getType())) {
                    continue;
                }
                sources.add(Map.entry(owned.getKey(), excess));
            }

            sources.sort(Comparator
                    .comparing((Map.Entry<String, Integer> e) -> sameGroupRank(e.getKey(), toMat.get()))
                    .thenComparing(e -> -database.material(e.getKey()).map(EngineeringMaterial::getGrade).orElse(0)));

            for (Map.Entry<String, Integer> source : sources) {
                if (stillNeed <= 0) {
                    break;
                }
                Optional<EngineeringMaterial> fromMat = database.material(source.getKey());
                if (fromMat.isEmpty()) {
                    continue;
                }
                Optional<MaterialTradeRateCalculator.Exchange> exchange =
                        MaterialTradeRateCalculator.planExchange(
                                fromMat.get(), toMat.get(), source.getValue(), stillNeed);
                if (exchange.isEmpty() || exchange.get().getToCount() <= 0) {
                    continue;
                }
                MaterialTradeRateCalculator.Exchange trade = exchange.get();
                out.add(new TradeSuggestion(
                        source.getKey(),
                        database.materialDisplayName(source.getKey()),
                        trade.getFromCount(),
                        toKey,
                        database.materialDisplayName(toKey),
                        trade.getToCount(),
                        fromMat.get().getSubtype().equalsIgnoreCase(toMat.get().getSubtype()),
                        toMat.get().getType()));
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
            adjustCount(inv, trade.getFromKey(), -trade.getFromCount());
            adjustCount(inv, trade.getToKey(), trade.getToCount());
        }
        return inv;
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

    private static int excessTradeable(String materialKey, int owned, Map<String, Integer> requiredForGoals) {
        if (owned <= 0) {
            return 0;
        }
        int reserved = 0;
        if (requiredForGoals != null) {
            reserved = requiredForGoals.getOrDefault(materialKey, 0);
            if (reserved <= 0) {
                for (Map.Entry<String, Integer> e : requiredForGoals.entrySet()) {
                    if (e.getKey() != null && e.getKey().equalsIgnoreCase(materialKey)) {
                        reserved = e.getValue() != null ? e.getValue() : 0;
                        break;
                    }
                }
            }
        }
        return Math.max(0, owned - reserved);
    }

    private int sameGroupRank(String fromKey, EngineeringMaterial to) {
        return database.material(fromKey)
                .map(f -> f.getSubtype().equalsIgnoreCase(to.getSubtype()) ? 0 : 1)
                .orElse(2);
    }
}
