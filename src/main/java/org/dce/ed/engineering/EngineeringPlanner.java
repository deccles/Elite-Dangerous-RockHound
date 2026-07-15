package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Aggregates material requirements for active engineering goals vs inventory.
 */
public final class EngineeringPlanner {

    private final EngineeringDatabase database;

    public EngineeringPlanner(EngineeringDatabase database) {
        this.database = database != null ? database : EngineeringDatabase.getInstance();
    }

    public List<ShoppingListRow> buildShoppingList(List<EngineeringGoal> goals,
                                                   Map<String, Integer> inventory) {
        return buildShoppingList(goals, inventory, inventory);
    }

    public List<ShoppingListRow> buildShoppingList(List<EngineeringGoal> goals,
                                                   Map<String, Integer> inventory,
                                                   Map<String, Integer> inventoryAfterTrades) {
        Map<String, Integer> required = new LinkedHashMap<>();
        for (EngineeringGoal goal : goals) {
            if (goal == null) {
                continue;
            }
            accumulateGoalMaterials(goal, required);
        }

        Map<String, Integer> haveNow = inventory != null ? inventory : Map.of();
        Map<String, Integer> haveAfter = inventoryAfterTrades != null ? inventoryAfterTrades : haveNow;

        List<ShoppingListRow> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> e : required.entrySet()) {
            String key = e.getKey();
            int need = e.getValue();
            int have = EngineeringMaterialKeys.countInInventory(haveNow, key);
            int haveAfterTrades = EngineeringMaterialKeys.countInInventory(haveAfter, key);
            String display = database.materialDisplayName(key);
            String type = database.material(key).map(EngineeringMaterial::getType).orElse("");
            rows.add(new ShoppingListRow(key, display, type, need, have, haveAfterTrades));
        }
        rows.sort((a, b) -> {
            int cmp = Integer.compare(b.getShortfall(), a.getShortfall());
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(b.getShortfallAfterTrades(), a.getShortfallAfterTrades());
            if (cmp != 0) {
                return cmp;
            }
            return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
        });
        return rows;
    }

    public Map<String, Integer> shortfalls(List<EngineeringGoal> goals, Map<String, Integer> inventory) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (ShoppingListRow row : buildShoppingList(goals, inventory)) {
            if (row.getShortfall() > 0) {
                out.put(row.getMaterialKey(), row.getShortfall());
            }
        }
        return out;
    }

    /** Material keys and counts required for one goal (grades + experimental). */
    public Map<String, Integer> materialsForGoal(EngineeringGoal goal) {
        Map<String, Integer> required = new LinkedHashMap<>();
        if (goal != null) {
            accumulateGoalMaterials(goal, required);
        }
        return required;
    }

    /** Per-material shortfall for a single goal vs inventory. */
    public Map<String, Integer> goalMaterialShortfalls(EngineeringGoal goal, Map<String, Integer> inventory) {
        Map<String, Integer> shortfalls = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : materialsForGoal(goal).entrySet()) {
            int have = EngineeringMaterialKeys.countInInventory(inventory, e.getKey());
            int shortfall = Math.max(0, e.getValue() - have);
            if (shortfall > 0) {
                shortfalls.put(e.getKey(), shortfall);
            }
        }
        return shortfalls;
    }

    public boolean isGoalReady(EngineeringGoal goal, Map<String, Integer> inventory) {
        return goal != null && (goal.isComplete() || goalMaterialShortfalls(goal, inventory).isEmpty());
    }

    public boolean isGoalComplete(EngineeringGoal goal) {
        return goal != null && goal.isComplete();
    }

    public GoalReadiness goalReadiness(EngineeringGoal goal,
                                       Map<String, Integer> inventory,
                                       Map<String, Integer> inventoryAfterTrades) {
        if (isGoalReady(goal, inventory)) {
            return GoalReadiness.READY;
        }
        if (isGoalReady(goal, inventoryAfterTrades)) {
            return GoalReadiness.READY_WITH_TRADES;
        }
        return GoalReadiness.STILL_SHORT;
    }

    /**
     * Trades and readiness planned High → Medium → Low so higher-priority goals claim inventory
     * and trade stock first. Disabled goals are omitted from readiness.
     *
     * <p>{@code inventoryAfterTrades} applies suggested trades only (no craft claims), for shopping.
     */
    public PriorityPlanResult planByPriority(List<EngineeringGoal> goals,
                                             Map<String, Integer> inventory,
                                             MaterialTradePlanner tradePlanner) {
        List<TradeSuggestion> trades = new ArrayList<>();
        Map<EngineeringGoal, GoalReadiness> readiness = new LinkedHashMap<>();
        Map<String, Integer> planningInv = mutableCopy(inventory);
        Map<String, Integer> shoppingInv = mutableCopy(inventory);
        if (goals == null || goals.isEmpty() || tradePlanner == null) {
            return new PriorityPlanResult(List.of(), shoppingInv, readiness);
        }

        List<EngineeringGoal> claimOrder = new ArrayList<>();
        for (EngineeringGoal goal : goals) {
            if (goal != null && goal.getPriority().isActive()) {
                claimOrder.add(goal);
            }
        }
        claimOrder.sort(Comparator
                .comparingInt((EngineeringGoal g) -> g.getPriority().sortRank())
                .thenComparingInt(goals::indexOf));

        for (EngineeringGoal goal : claimOrder) {
            if (goal.isComplete()) {
                readiness.put(goal, GoalReadiness.READY);
                continue;
            }
            Map<String, Integer> before = mutableCopy(planningInv);
            Map<String, Integer> shortfalls = goalMaterialShortfalls(goal, planningInv);
            Map<String, Integer> required = materialsForGoal(goal);
            List<TradeSuggestion> goalTrades = tradePlanner.suggest(shortfalls, planningInv, required);
            if (!goalTrades.isEmpty()) {
                trades.addAll(goalTrades);
                planningInv = tradePlanner.inventoryAfterTrades(planningInv, goalTrades);
                shoppingInv = tradePlanner.inventoryAfterTrades(shoppingInv, goalTrades);
            }
            readiness.put(goal, goalReadiness(goal, before, planningInv));
            claimGoalMaterials(goal, planningInv);
        }
        return new PriorityPlanResult(List.copyOf(trades), shoppingInv, readiness);
    }

    /**
     * Readiness using a precomputed post-trade inventory (inventory + trade stock claimed
     * High → Medium → Low). Prefer {@link #planByPriority} so trades themselves respect priority.
     */
    public Map<EngineeringGoal, GoalReadiness> goalReadinessWithPriorityClaim(
            List<EngineeringGoal> goals,
            Map<String, Integer> inventory,
            Map<String, Integer> inventoryAfterTrades) {
        Map<EngineeringGoal, GoalReadiness> out = new LinkedHashMap<>();
        if (goals == null || goals.isEmpty()) {
            return out;
        }
        List<EngineeringGoal> claimOrder = new ArrayList<>();
        for (EngineeringGoal goal : goals) {
            if (goal != null && goal.getPriority().isActive()) {
                claimOrder.add(goal);
            }
        }
        claimOrder.sort(Comparator
                .comparingInt((EngineeringGoal g) -> g.getPriority().sortRank())
                .thenComparingInt(goals::indexOf));

        Map<String, Integer> working = mutableCopy(inventory);
        Map<String, Integer> workingTrades = mutableCopy(inventoryAfterTrades);
        for (EngineeringGoal goal : claimOrder) {
            out.put(goal, goalReadiness(goal, working, workingTrades));
            if (!goal.isComplete()) {
                claimGoalMaterials(goal, working);
                claimGoalMaterials(goal, workingTrades);
            }
        }
        return out;
    }

    private void claimGoalMaterials(EngineeringGoal goal, Map<String, Integer> working) {
        if (working == null || working.isEmpty() || goal == null) {
            return;
        }
        for (Map.Entry<String, Integer> need : materialsForGoal(goal).entrySet()) {
            int remaining = need.getValue() != null ? need.getValue() : 0;
            if (remaining <= 0) {
                continue;
            }
            String want = EngineeringMaterialKeys.canonicalKey(need.getKey());
            for (Map.Entry<String, Integer> have : new ArrayList<>(working.entrySet())) {
                if (remaining <= 0) {
                    break;
                }
                if (!EngineeringMaterialKeys.canonicalKey(have.getKey()).equals(want)) {
                    continue;
                }
                int stock = have.getValue() != null ? have.getValue() : 0;
                int take = Math.min(stock, remaining);
                working.put(have.getKey(), stock - take);
                remaining -= take;
            }
        }
    }

    private static Map<String, Integer> mutableCopy(Map<String, Integer> source) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(source);
    }

    public int countGoalsWithReadiness(List<EngineeringGoal> goals,
                                       Map<String, Integer> inventory,
                                       Map<String, Integer> inventoryAfterTrades,
                                       GoalReadiness readiness) {
        int count = 0;
        for (GoalReadiness r : goalReadinessWithPriorityClaim(goals, inventory, inventoryAfterTrades).values()) {
            if (r == readiness) {
                count++;
            }
        }
        return count;
    }

    /** Result of {@link #planByPriority}. */
    public record PriorityPlanResult(
            List<TradeSuggestion> trades,
            Map<String, Integer> inventoryAfterTrades,
            Map<EngineeringGoal, GoalReadiness> readinessByGoal) {
        public PriorityPlanResult {
            trades = trades != null ? List.copyOf(trades) : List.of();
            inventoryAfterTrades = inventoryAfterTrades != null
                    ? Map.copyOf(inventoryAfterTrades)
                    : Map.of();
            readinessByGoal = readinessByGoal != null
                    ? Map.copyOf(readinessByGoal)
                    : Map.of();
        }
    }

    /** Total materials required across all active goals (including grades already covered). */
    public Map<String, Integer> requiredMaterials(List<EngineeringGoal> goals) {
        Map<String, Integer> required = new LinkedHashMap<>();
        if (goals != null) {
            for (EngineeringGoal goal : goals) {
                if (goal != null) {
                    accumulateGoalMaterials(goal, required);
                }
            }
        }
        return required;
    }

    private void accumulateGoalMaterials(EngineeringGoal goal, Map<String, Integer> required) {
        int remaining = goal.remainingUnits();
        if (remaining <= 0) {
            return;
        }
        Map<String, Integer> currentUnit = new LinkedHashMap<>();
        accumulateSingleUnitMaterials(goal, currentUnit);
        for (Map.Entry<String, Integer> e : currentUnit.entrySet()) {
            required.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        if (remaining > 1) {
            EngineeringGoal freshUnit = goal.withProgress(0, 0).withExperimentalApplied(false);
            Map<String, Integer> fullUnit = new LinkedHashMap<>();
            accumulateSingleUnitMaterials(freshUnit, fullUnit);
            for (Map.Entry<String, Integer> e : fullUnit.entrySet()) {
                required.merge(e.getKey(), e.getValue() * (remaining - 1), Integer::sum);
            }
        }
    }

    private void accumulateSingleUnitMaterials(EngineeringGoal goal, Map<String, Integer> required) {
        List<BlueprintGrade> grades = database.gradesFor(goal.getModuleType(), goal.getBlueprintName());
        for (BlueprintGrade grade : grades) {
            if (grade.isExperimental()) {
                continue;
            }
            int g = grade.getGrade();
            if (g <= goal.getFromGrade() || g > goal.getTargetGrade()) {
                continue;
            }
            int rolls = EngineeringGradeProgress.rollsRemainingAtGrade(goal, g);
            if (rolls <= 0) {
                continue;
            }
            for (MaterialRequirement mat : grade.getMaterials()) {
                required.merge(mat.getKey(), mat.getCount() * rolls, Integer::sum);
            }
        }
        if (goal.getExperimentalId() != null && !goal.getExperimentalId().isBlank() && !goal.isExperimentalApplied()) {
            Optional<BlueprintGrade> exp = database.findById(goal.getExperimentalId());
            exp.ifPresent(bp -> {
                for (MaterialRequirement mat : bp.getMaterials()) {
                    required.merge(mat.getKey(), mat.getCount(), Integer::sum);
                }
            });
        }
    }
}
