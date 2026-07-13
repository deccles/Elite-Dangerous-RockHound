package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Suggests same-row material-trader upgrades to convert excess low-grade stock into fewer,
 * higher-grade units and free inventory slots.
 */
public final class InventoryConsolidationPlanner {

    private final EngineeringDatabase database;

    public InventoryConsolidationPlanner(EngineeringDatabase database) {
        this.database = database != null ? database : EngineeringDatabase.getInstance();
    }

    public List<TradeSuggestion> suggest(EngineeringGoal goal,
                                         Map<String, Integer> inventory,
                                         Map<String, Integer> reservedForGoals) {
        if (goal == null || !goal.isInventoryConsolidation() || inventory == null || inventory.isEmpty()) {
            return List.of();
        }
        int maxSourceGrade = goal.getFromGrade();
        int targetGrade = goal.getTargetGrade();
        if (targetGrade <= maxSourceGrade) {
            return List.of();
        }

        Map<String, Integer> working = copyPositive(inventory);
        List<TradeSuggestion> out = new ArrayList<>();
        List<EngineeringMaterial> tradeable = database.getAllMaterials().stream()
                .filter(MaterialTraderCatalog::isTradeableAtMaterialTrader)
                .sorted(Comparator
                        .comparing((EngineeringMaterial m) -> traderTypeRank(m.getType()))
                        .thenComparing(EngineeringMaterial::getSubtype, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(EngineeringMaterial::getGrade))
                .toList();

        boolean changed = true;
        while (changed) {
            changed = false;
            for (EngineeringMaterial from : tradeable) {
                int grade = from.getGrade();
                if (grade > maxSourceGrade || grade >= targetGrade) {
                    continue;
                }
                int excess = excessTradeable(working, from.getKey(), reservedForGoals);
                if (excess <= 0) {
                    continue;
                }
                Optional<EngineeringMaterial> toOpt =
                        database.traderRowMaterial(from.getType(), from.getSubtype(), grade + 1);
                if (toOpt.isEmpty()) {
                    continue;
                }
                EngineeringMaterial to = toOpt.get();
                Optional<MaterialTradeRateCalculator.Exchange> exchange =
                        MaterialTradeRateCalculator.planExchange(from, to, excess, Integer.MAX_VALUE);
                if (exchange.isEmpty() || exchange.get().getToCount() <= 0) {
                    continue;
                }
                MaterialTradeRateCalculator.Exchange trade = exchange.get();
                out.add(new TradeSuggestion(
                        from.getKey(),
                        database.materialDisplayName(from.getKey()),
                        trade.getFromCount(),
                        to.getKey(),
                        database.materialDisplayName(to.getKey()),
                        trade.getToCount(),
                        true,
                        from.getType()));
                adjustCount(working, from.getKey(), -trade.getFromCount());
                adjustCount(working, to.getKey(), trade.getToCount());
                changed = true;
            }
        }

        out.sort(Comparator
                .comparingInt((TradeSuggestion t) -> traderTypeRank(t.getTraderType()))
                .thenComparing(TradeSuggestion::getToName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(TradeSuggestion::getFromName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** Count of low-grade units that could still be consolidated for this goal. */
    public int excessCommonUnits(EngineeringGoal goal,
                                 Map<String, Integer> inventory,
                                 Map<String, Integer> reservedForGoals) {
        if (goal == null || !goal.isInventoryConsolidation() || inventory == null) {
            return 0;
        }
        int maxSourceGrade = goal.getFromGrade();
        int total = 0;
        for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
            Optional<EngineeringMaterial> mat = database.material(entry.getKey());
            if (mat.isEmpty()
                    || !MaterialTraderCatalog.isTradeableAtMaterialTrader(mat.get())
                    || mat.get().getGrade() > maxSourceGrade) {
                continue;
            }
            total += excessTradeable(inventory, entry.getKey(), reservedForGoals);
        }
        return total;
    }

    public Map<String, Integer> consolidationTargets(List<TradeSuggestion> trades) {
        Map<String, Integer> targets = new LinkedHashMap<>();
        if (trades == null) {
            return targets;
        }
        for (TradeSuggestion trade : trades) {
            if (trade.getToCount() > 0) {
                targets.merge(trade.getToKey(), trade.getToCount(), Integer::sum);
            }
        }
        return targets;
    }

    private static int traderTypeRank(String type) {
        if ("Raw".equalsIgnoreCase(type)) {
            return 0;
        }
        if ("Manufactured".equalsIgnoreCase(type)) {
            return 1;
        }
        if ("Encoded".equalsIgnoreCase(type)) {
            return 2;
        }
        return 3;
    }

    private static Map<String, Integer> copyPositive(Map<String, Integer> inventory) {
        Map<String, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }

    private static int excessTradeable(Map<String, Integer> inventory,
                                       String materialKey,
                                       Map<String, Integer> reservedForGoals) {
        if (materialKey == null || materialKey.isBlank()) {
            return 0;
        }
        int owned = countInInventory(inventory, materialKey);
        if (owned <= 0) {
            return 0;
        }
        int reserved = 0;
        if (reservedForGoals != null) {
            reserved = reservedForGoals.getOrDefault(materialKey, 0);
            if (reserved <= 0) {
                for (Map.Entry<String, Integer> e : reservedForGoals.entrySet()) {
                    if (e.getKey() != null && e.getKey().equalsIgnoreCase(materialKey)) {
                        reserved = e.getValue() != null ? e.getValue() : 0;
                        break;
                    }
                }
            }
        }
        return Math.max(0, owned - reserved);
    }

    private static int countInInventory(Map<String, Integer> inventory, String key) {
        if (inventory == null || key == null) {
            return 0;
        }
        int count = inventory.getOrDefault(key, 0);
        if (count > 0) {
            return count;
        }
        for (Map.Entry<String, Integer> e : inventory.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                return e.getValue() != null ? e.getValue() : 0;
            }
        }
        return 0;
    }

    private static void adjustCount(Map<String, Integer> inv, String key, int delta) {
        if (key == null || key.isBlank() || delta == 0) {
            return;
        }
        int next = countInInventory(inv, key) + delta;
        String existingKey = key;
        for (String k : inv.keySet()) {
            if (k != null && k.equalsIgnoreCase(key)) {
                existingKey = k;
                break;
            }
        }
        if (next <= 0) {
            inv.remove(existingKey);
        } else {
            inv.put(existingKey, next);
        }
    }
}
