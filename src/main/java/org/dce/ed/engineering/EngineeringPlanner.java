package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
        return buildShoppingList(goals, List.of(), inventory, inventory);
    }

    public List<ShoppingListRow> buildShoppingList(List<EngineeringGoal> goals,
                                                   Map<String, Integer> inventory,
                                                   Map<String, Integer> inventoryAfterTrades) {
        return buildShoppingList(goals, List.of(), inventory, inventoryAfterTrades);
    }

    public List<ShoppingListRow> buildShoppingList(List<EngineeringGoal> goals,
                                                   List<MaterialsGoal> materialsGoals,
                                                   Map<String, Integer> inventory,
                                                   Map<String, Integer> inventoryAfterTrades) {
        Map<String, Integer> required = requiredMaterials(goals, materialsGoals);
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
        return shortfalls(goals, List.of(), inventory);
    }

    public Map<String, Integer> shortfalls(List<EngineeringGoal> goals,
                                           List<MaterialsGoal> materialsGoals,
                                           Map<String, Integer> inventory) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (ShoppingListRow row : buildShoppingList(goals, materialsGoals, inventory, inventory)) {
            if (row.getShortfall() > 0) {
                out.put(row.getMaterialKey(), row.getShortfall());
            }
        }
        return out;
    }

    /** Material keys and counts required for one blueprint goal (grades + experimental). */
    public Map<String, Integer> materialsForGoal(EngineeringGoal goal) {
        Map<String, Integer> required = new LinkedHashMap<>();
        if (goal != null) {
            accumulateBlueprintGoalMaterials(goal, required);
        }
        return required;
    }

    public Map<String, Integer> materialsForGoal(MaterialsGoal goal) {
        return goal != null ? goal.requiredMaterials() : Map.of();
    }

    /** Per-material shortfall for a single blueprint goal vs inventory. */
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

    public Map<String, Integer> goalMaterialShortfalls(MaterialsGoal goal, Map<String, Integer> inventory) {
        return goal != null ? goal.shortfalls(inventory) : Map.of();
    }

    public boolean isGoalReady(EngineeringGoal goal, Map<String, Integer> inventory) {
        return goal != null && (goal.isComplete() || goalMaterialShortfalls(goal, inventory).isEmpty());
    }

    public boolean isGoalReady(MaterialsGoal goal, Map<String, Integer> inventory) {
        return goal != null && goal.isSatisfied(inventory);
    }

    public boolean isGoalComplete(EngineeringGoal goal) {
        return goal != null && goal.isComplete();
    }

    public boolean isGoalComplete(MaterialsGoal goal, Map<String, Integer> inventory) {
        return isGoalReady(goal, inventory);
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

    public GoalReadiness goalReadiness(MaterialsGoal goal,
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
     * and trade stock first. Disabled goals are omitted from readiness. Trade suggestions never
     * spend stock any remaining goal requires, so a plan cannot trade away one goal's materials
     * and then buy the same material back for another goal.
     *
     * <p>Suggested pays for later goals are limited to materials the commander already owns
     * (minus prior suggested pays and materials already claimed for earlier goals). Receives
     * from earlier suggestions still fill shortfalls / readiness, but cannot become pay stock
     * for a later {@code suggest}.
     *
     * <p>{@code inventoryAfterTrades} applies suggested trades only (no craft claims), for shopping.
     * {@code shortfallsRemainingAfterPlan} is what Short goals still lack after their trades —
     * Trade Suggestions uses this so goal Short status lines up with uncovered / no-trade rows.
     */
    public PriorityPlanResult planByPriority(List<EngineeringGoal> goals,
                                             Map<String, Integer> inventory,
                                             MaterialTradePlanner tradePlanner) {
        return planByPriority(goals, List.of(), inventory, tradePlanner);
    }

    public PriorityPlanResult planByPriority(List<EngineeringGoal> goals,
                                             List<MaterialsGoal> materialsGoals,
                                             Map<String, Integer> inventory,
                                             MaterialTradePlanner tradePlanner) {
        return planByPriority(goals, materialsGoals, inventory, tradePlanner, null, null);
    }

    /**
     * Plans trades in priority order while reserving materials for every included goal.
     *
     * <p>When {@code tradeForBlueprints} / {@code tradeForMaterials} are non-null, only those
     * goals receive trade suggestions. Other goals still claim inventory and keep reservations
     * so a ship-scoped view cannot spend stock another ship's goal still needs.
     */
    public PriorityPlanResult planByPriority(List<EngineeringGoal> goals,
                                             List<MaterialsGoal> materialsGoals,
                                             Map<String, Integer> inventory,
                                             MaterialTradePlanner tradePlanner,
                                             Collection<EngineeringGoal> tradeForBlueprints,
                                             Collection<MaterialsGoal> tradeForMaterials) {
        List<TradeSuggestion> trades = new ArrayList<>();
        Map<EngineeringGoal, GoalReadiness> blueprintReadiness = new LinkedHashMap<>();
        Map<MaterialsGoal, GoalReadiness> materialsReadiness = new LinkedHashMap<>();
        Map<String, Integer> shortfallsRemaining = new LinkedHashMap<>();
        Map<String, Integer> planningInv = mutableCopy(inventory);
        Map<String, Integer> shoppingInv = mutableCopy(inventory);
        // Pay stock for suggest(): on-hand materials, minus suggested pays and craft claims.
        Map<String, Integer> payInv = mutableCopy(inventory);
        List<ClaimItem> claimOrder = buildClaimOrder(goals, materialsGoals);
        if (claimOrder.isEmpty() || tradePlanner == null) {
            return new PriorityPlanResult(
                    List.of(), shoppingInv, blueprintReadiness, materialsReadiness, Map.of());
        }

        Set<EngineeringGoal> tradeBlueprintSet = identitySet(tradeForBlueprints);
        Set<MaterialsGoal> tradeMaterialsSet = identitySet(tradeForMaterials);
        boolean filterTradeTargets = tradeForBlueprints != null || tradeForMaterials != null;

        // Reserve materials for ALL remaining goals, not just the one being planned, so a
        // higher-priority goal never trades away stock a later goal is counting on (which
        // would force a wasteful buy-back trade for a material with Need 0).
        Map<String, Integer> remainingReserved = new LinkedHashMap<>();
        for (ClaimItem item : claimOrder) {
            adjustReservation(remainingReserved, requiredForClaimItem(item), 1);
        }

        for (ClaimItem item : claimOrder) {
            if (item.blueprint() != null) {
                EngineeringGoal goal = item.blueprint();
                if (goal.isComplete()) {
                    blueprintReadiness.put(goal, GoalReadiness.READY);
                    continue;
                }
                Map<String, Integer> before = mutableCopy(planningInv);
                Map<String, Integer> shortfalls = goalMaterialShortfalls(goal, planningInv);
                Map<String, Integer> required = materialsForGoal(goal);
                boolean suggestTrades = !filterTradeTargets || tradeBlueprintSet.contains(goal);
                if (suggestTrades) {
                    List<TradeSuggestion> goalTrades =
                            tradePlanner.suggest(shortfalls, payInv, remainingReserved);
                    if (!goalTrades.isEmpty()) {
                        trades.addAll(goalTrades);
                        planningInv = tradePlanner.inventoryAfterTrades(planningInv, goalTrades);
                        shoppingInv = tradePlanner.inventoryAfterTrades(shoppingInv, goalTrades);
                        payInv = tradePlanner.inventoryAfterPayingTrades(payInv, goalTrades);
                    }
                }
                GoalReadiness readiness = goalReadiness(goal, before, planningInv);
                blueprintReadiness.put(goal, readiness);
                if (suggestTrades && readiness == GoalReadiness.STILL_SHORT) {
                    mergeShortfallCounts(shortfallsRemaining, goalMaterialShortfalls(goal, planningInv));
                }
                claimMaterials(required, planningInv);
                claimMaterials(required, payInv);
                adjustReservation(remainingReserved, required, -1);
            } else if (item.materials() != null) {
                MaterialsGoal goal = item.materials();
                Map<String, Integer> before = mutableCopy(planningInv);
                Map<String, Integer> shortfalls = goalMaterialShortfalls(goal, planningInv);
                Map<String, Integer> required = materialsForGoal(goal);
                boolean suggestTrades = !filterTradeTargets || tradeMaterialsSet.contains(goal);
                if (suggestTrades) {
                    List<TradeSuggestion> goalTrades =
                            tradePlanner.suggest(shortfalls, payInv, remainingReserved);
                    if (!goalTrades.isEmpty()) {
                        trades.addAll(goalTrades);
                        planningInv = tradePlanner.inventoryAfterTrades(planningInv, goalTrades);
                        shoppingInv = tradePlanner.inventoryAfterTrades(shoppingInv, goalTrades);
                        payInv = tradePlanner.inventoryAfterPayingTrades(payInv, goalTrades);
                    }
                }
                GoalReadiness readiness = goalReadiness(goal, before, planningInv);
                materialsReadiness.put(goal, readiness);
                if (suggestTrades && readiness == GoalReadiness.STILL_SHORT) {
                    mergeShortfallCounts(shortfallsRemaining, goalMaterialShortfalls(goal, planningInv));
                }
                claimMaterials(required, planningInv);
                claimMaterials(required, payInv);
                adjustReservation(remainingReserved, required, -1);
            }
        }
        return new PriorityPlanResult(
                List.copyOf(trades),
                shoppingInv,
                blueprintReadiness,
                materialsReadiness,
                Map.copyOf(shortfallsRemaining));
    }

    private static <T> Set<T> identitySet(Collection<T> items) {
        Set<T> set = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        if (items != null) {
            for (T item : items) {
                if (item != null) {
                    set.add(item);
                }
            }
        }
        return set;
    }

    private static void mergeShortfallCounts(Map<String, Integer> into, Map<String, Integer> add) {
        if (into == null || add == null || add.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> e : add.entrySet()) {
            int count = e.getValue() != null ? e.getValue() : 0;
            if (count <= 0 || e.getKey() == null) {
                continue;
            }
            String key = EngineeringMaterialKeys.canonicalKey(e.getKey());
            if (!key.isBlank()) {
                into.merge(key, count, Integer::sum);
            }
        }
    }

    private Map<String, Integer> requiredForClaimItem(ClaimItem item) {
        if (item.blueprint() != null) {
            return item.blueprint().isComplete() ? Map.of() : materialsForGoal(item.blueprint());
        }
        if (item.materials() != null) {
            return materialsForGoal(item.materials());
        }
        return Map.of();
    }

    /** Adds ({@code sign} = 1) or releases ({@code sign} = -1) a goal's requirements, canonical keys. */
    private static void adjustReservation(Map<String, Integer> totals, Map<String, Integer> required, int sign) {
        if (totals == null || required == null || required.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> e : required.entrySet()) {
            int count = e.getValue() != null ? e.getValue() : 0;
            if (count <= 0) {
                continue;
            }
            String key = EngineeringMaterialKeys.canonicalKey(e.getKey());
            if (key.isBlank()) {
                continue;
            }
            int next = totals.getOrDefault(key, 0) + sign * count;
            if (next <= 0) {
                totals.remove(key);
            } else {
                totals.put(key, next);
            }
        }
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
        List<ClaimItem> claimOrder = buildClaimOrder(goals, List.of());
        Map<String, Integer> working = mutableCopy(inventory);
        Map<String, Integer> workingTrades = mutableCopy(inventoryAfterTrades);
        for (ClaimItem item : claimOrder) {
            EngineeringGoal goal = item.blueprint();
            if (goal == null) {
                continue;
            }
            out.put(goal, goalReadiness(goal, working, workingTrades));
            if (!goal.isComplete()) {
                claimMaterials(materialsForGoal(goal), working);
                claimMaterials(materialsForGoal(goal), workingTrades);
            }
        }
        return out;
    }

    private List<ClaimItem> buildClaimOrder(List<EngineeringGoal> goals, List<MaterialsGoal> materialsGoals) {
        List<ClaimItem> items = new ArrayList<>();
        if (goals != null) {
            for (int i = 0; i < goals.size(); i++) {
                EngineeringGoal goal = goals.get(i);
                if (goal != null && goal.isIncludeInPlanning()) {
                    items.add(new ClaimItem(goal, null, goal.getPriority(), i));
                }
            }
        }
        int blueprintCount = goals != null ? goals.size() : 0;
        if (materialsGoals != null) {
            for (int i = 0; i < materialsGoals.size(); i++) {
                MaterialsGoal goal = materialsGoals.get(i);
                if (goal != null && goal.isIncludeInPlanning() && goal.isValid()) {
                    items.add(new ClaimItem(null, goal, goal.getPriority(), blueprintCount + i));
                }
            }
        }
        items.sort(Comparator
                .comparingInt((ClaimItem c) -> c.priority().sortRank())
                .thenComparingInt(ClaimItem::listIndex));
        return items;
    }

    private void claimMaterials(Map<String, Integer> required, Map<String, Integer> working) {
        if (working == null || working.isEmpty() || required == null || required.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> need : required.entrySet()) {
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
            Map<EngineeringGoal, GoalReadiness> readinessByBlueprintGoal,
            Map<MaterialsGoal, GoalReadiness> readinessByMaterialsGoal,
            Map<String, Integer> shortfallsRemainingAfterPlan) {

        public PriorityPlanResult {
            trades = trades != null ? List.copyOf(trades) : List.of();
            inventoryAfterTrades = inventoryAfterTrades != null
                    ? Map.copyOf(inventoryAfterTrades)
                    : Map.of();
            readinessByBlueprintGoal = readinessByBlueprintGoal != null
                    ? Map.copyOf(readinessByBlueprintGoal)
                    : Map.of();
            readinessByMaterialsGoal = readinessByMaterialsGoal != null
                    ? Map.copyOf(readinessByMaterialsGoal)
                    : Map.of();
            shortfallsRemainingAfterPlan = shortfallsRemainingAfterPlan != null
                    ? Map.copyOf(shortfallsRemainingAfterPlan)
                    : Map.of();
        }

        /** @deprecated use {@link #readinessByBlueprintGoal()} */
        @Deprecated
        public Map<EngineeringGoal, GoalReadiness> readinessByGoal() {
            return readinessByBlueprintGoal;
        }
    }

    private record ClaimItem(EngineeringGoal blueprint, MaterialsGoal materials, GoalPriority priority, int listIndex) {
    }

    /** Total materials required across blueprint + materials goals. */
    public Map<String, Integer> requiredMaterials(List<EngineeringGoal> goals) {
        return requiredMaterials(goals, List.of());
    }

    public Map<String, Integer> requiredMaterials(List<EngineeringGoal> goals, List<MaterialsGoal> materialsGoals) {
        Map<String, Integer> required = new LinkedHashMap<>();
        if (goals != null) {
            for (EngineeringGoal goal : goals) {
                if (goal != null) {
                    accumulateBlueprintGoalMaterials(goal, required);
                }
            }
        }
        if (materialsGoals != null) {
            for (MaterialsGoal goal : materialsGoals) {
                if (goal != null && goal.isValid()) {
                    for (Map.Entry<String, Integer> e : goal.requiredMaterials().entrySet()) {
                        required.merge(e.getKey(), e.getValue(), Integer::sum);
                    }
                }
            }
        }
        return required;
    }

    private void accumulateBlueprintGoalMaterials(EngineeringGoal goal, Map<String, Integer> required) {
        int remaining = goal.remainingUnits();
        if (remaining <= 0) {
            return;
        }
        // Progress can sit on a finished unit (fromGrade/target + experimental done) while
        // completedUnits has not yet absorbed it. Cost those leftover units as fresh G0→target
        // work — do not report Need=0 / Ready while quantity remains.
        if (goal.isCurrentUnitComplete()) {
            EngineeringGoal freshUnit = goal.withProgress(0, 0).withExperimentalApplied(false);
            Map<String, Integer> fullUnit = new LinkedHashMap<>();
            accumulateSingleUnitMaterials(freshUnit, fullUnit);
            for (Map.Entry<String, Integer> e : fullUnit.entrySet()) {
                required.merge(e.getKey(), e.getValue() * remaining, Integer::sum);
            }
            return;
        }
        Map<String, Integer> currentUnit = new LinkedHashMap<>();
        accumulateSingleUnitMaterials(goal, currentUnit);
        for (Map.Entry<String, Integer> e : currentUnit.entrySet()) {
            required.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        if (remaining > 1) {
            // Shared fromGrade is the worst incomplete module. When nothing is finished yet,
            // remaining siblings are at that same progress (e.g. two G3 SCBs going to G4), not
            // stock G0 — costing them as fresh modules re-added experimental mats like Chemical
            // Storage Units after a restart. Once any unit is finished, unknown siblings are
            // still treated as G0→target + experimental.
            EngineeringGoal extraUnit;
            if (goal.getCompletedUnits() == 0) {
                extraUnit = goal;
            } else {
                extraUnit = goal.withProgress(0, 0).withExperimentalApplied(false);
            }
            Map<String, Integer> fullUnit = new LinkedHashMap<>();
            accumulateSingleUnitMaterials(extraUnit, fullUnit);
            for (Map.Entry<String, Integer> e : fullUnit.entrySet()) {
                required.merge(e.getKey(), e.getValue() * (remaining - 1), Integer::sum);
            }
        }
    }

    private void accumulateSingleUnitMaterials(EngineeringGoal goal, Map<String, Integer> required) {
        // Always use the conservative 5-roll schedule for Need. Rank-5 discounts (1/2/3/4/5)
        // would under-buy when crafting at a lower-rep engineer or before ranks are known.
        // Live progress still follows journal Quality (early grade completion).
        List<BlueprintGrade> grades = database.gradesFor(goal.getModuleType(), goal.getBlueprintName());
        for (BlueprintGrade grade : grades) {
            if (grade.isExperimental()) {
                continue;
            }
            int g = grade.getGrade();
            if (g <= goal.getFromGrade() || g > goal.getTargetGrade()) {
                continue;
            }
            int rolls = EngineeringGradeProgress.rollsRemainingAtGrade(goal, g, 0);
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
