package org.dce.ed.engineering;

import java.util.ArrayList;
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

    public int countGoalsWithReadiness(List<EngineeringGoal> goals,
                                       Map<String, Integer> inventory,
                                       Map<String, Integer> inventoryAfterTrades,
                                       GoalReadiness readiness) {
        int count = 0;
        for (EngineeringGoal goal : goals) {
            if (goal != null && goalReadiness(goal, inventory, inventoryAfterTrades) == readiness) {
                count++;
            }
        }
        return count;
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
