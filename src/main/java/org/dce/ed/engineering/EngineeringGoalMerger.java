package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Collapses identical engineering goals into one row with summed quantity.
 *
 * <p>Grouped slot-pinned goals retain every intended module slot so unrelated siblings cannot
 * satisfy their progress.
 */
public final class EngineeringGoalMerger {

    private EngineeringGoalMerger() {
    }

    /**
     * True when two goals share the same planning identity (ship, module, blueprint,
     * experimental, target grade, priority, enabled).
     */
    public static boolean samePlanIdentity(EngineeringGoal a, EngineeringGoal b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.getShipId() != b.getShipId()) {
            return false;
        }
        if (!EngineeringJournalBlueprintResolver.sameModuleType(a.getModuleType(), b.getModuleType())) {
            return false;
        }
        if (!norm(a.getBlueprintName()).equals(norm(b.getBlueprintName()))) {
            return false;
        }
        if (!norm(a.getExperimentalId()).equals(norm(b.getExperimentalId()))) {
            return false;
        }
        if (a.getTargetGrade() != b.getTargetGrade()) {
            return false;
        }
        if (a.getPriority() != b.getPriority()) {
            return false;
        }
        return a.isIncludeInPlanning() == b.isIncludeInPlanning()
                && a.hasTargetSlot() == b.hasTargetSlot();
    }

    /**
     * Merges duplicate plan identities in place. Returns {@code true} if the list changed.
     */
    public static boolean mergeInPlace(List<EngineeringGoal> goals) {
        if (goals == null || goals.isEmpty()) {
            return false;
        }
        List<EngineeringGoal> merged = merge(goals);
        if (merged.size() == goals.size() && identicalContents(goals, merged)) {
            return false;
        }
        goals.clear();
        goals.addAll(merged);
        return true;
    }

    /**
     * Returns a new list with identical plan identities collapsed.
     */
    public static List<EngineeringGoal> merge(List<EngineeringGoal> goals) {
        if (goals == null || goals.isEmpty()) {
            return List.of();
        }
        Map<String, List<EngineeringGoal>> groups = new LinkedHashMap<>();
        for (EngineeringGoal goal : goals) {
            if (goal == null) {
                continue;
            }
            groups.computeIfAbsent(planKey(goal), k -> new ArrayList<>()).add(goal);
        }
        List<EngineeringGoal> out = new ArrayList<>(groups.size());
        for (List<EngineeringGoal> group : groups.values()) {
            out.add(collapseGroup(group));
        }
        return out;
    }

    /**
     * Finds the first goal in {@code goals} that matches the given planning fields.
     * {@code experimentalId} should already be a catalog id (or blank).
     */
    public static EngineeringGoal findMatching(
            List<EngineeringGoal> goals,
            long shipId,
            String moduleType,
            String blueprintName,
            String experimentalId,
            int targetGrade) {
        if (goals == null || goals.isEmpty()) {
            return null;
        }
        String mod = moduleType != null ? moduleType : "";
        String bp = blueprintName != null ? blueprintName : "";
        String exp = experimentalId != null ? experimentalId : "";
        for (EngineeringGoal goal : goals) {
            if (goal == null) {
                continue;
            }
            if (goal.getShipId() != shipId) {
                continue;
            }
            if (!EngineeringJournalBlueprintResolver.sameModuleType(goal.getModuleType(), mod)) {
                continue;
            }
            if (!norm(goal.getBlueprintName()).equals(norm(bp))) {
                continue;
            }
            if (!norm(goal.getExperimentalId()).equals(norm(exp))) {
                continue;
            }
            if (goal.getTargetGrade() != Math.max(1, targetGrade)) {
                continue;
            }
            return goal;
        }
        return null;
    }

    private static EngineeringGoal collapseGroup(List<EngineeringGoal> group) {
        EngineeringGoal template = group.get(0);
        if (group.size() == 1) {
            if (template.getQuantity() > 1
                    && template.getTargetSlots().size() < template.getQuantity()) {
                return template.withTargetSlot("");
            }
            return template;
        }
        int quantity = 0;
        int completed = 0;
        List<String> targetSlots = new ArrayList<>();
        EngineeringGoal bestPartial = null;
        int bestScore = -1;
        for (EngineeringGoal instance : group) {
            quantity += Math.max(1, instance.getQuantity());
            completed += Math.max(0, instance.getCompletedUnits());
            targetSlots.addAll(instance.getTargetSlots());
            if (instance.getCompletedUnits() == 0 && instance.isCurrentUnitComplete()) {
                completed++;
                continue;
            }
            if (instance.getCompletedUnits() > 0) {
                continue;
            }
            int score = instance.getFromGrade() * 1000
                    + instance.getCraftsAtCurrentGrade() * 10
                    + (instance.isExperimentalApplied() ? 1 : 0);
            if (score > bestScore) {
                bestScore = score;
                bestPartial = instance;
            }
        }
        quantity = Math.max(1, quantity);
        completed = Math.min(quantity, completed);
        EngineeringGoal aggregated = template
                .withQuantity(quantity)
                .withCompletedUnits(completed)
                .withTargetSlots(targetSlots);
        if (completed >= quantity) {
            boolean expDone = template.getExperimentalId().isBlank()
                    || completedInstancesHaveExperimental(group);
            if (!expDone) {
                // Sticky completedUnits without experimental evidence — keep grades, not Ready.
                EngineeringGoal partial = bestPartial != null
                        ? aggregated.withProgress(
                                        bestPartial.getFromGrade(), bestPartial.getCraftsAtCurrentGrade())
                                .withExperimentalApplied(false)
                        : aggregated.withProgress(template.getTargetGrade(), 0)
                                .withExperimentalApplied(false);
                return partial.withCompletedUnits(Math.max(0, quantity - 1));
            }
            return aggregated.withProgress(template.getTargetGrade(), 0)
                    .withExperimentalApplied(!template.getExperimentalId().isBlank());
        }
        if (bestPartial != null) {
            return aggregated.withProgress(bestPartial.getFromGrade(), bestPartial.getCraftsAtCurrentGrade())
                    .withExperimentalApplied(bestPartial.isExperimentalApplied());
        }
        return aggregated.withProgress(0, 0).withExperimentalApplied(false);
    }

    private static boolean completedInstancesHaveExperimental(
            Iterable<EngineeringGoal> instances) {
        if (instances == null) {
            return false;
        }
        for (EngineeringGoal instance : instances) {
            if (instance != null && instance.isExperimentalApplied()) {
                return true;
            }
        }
        return false;
    }

    private static String planKey(EngineeringGoal goal) {
        return goal.getShipId()
                + "|" + norm(goal.getModuleType())
                + "|" + norm(goal.getBlueprintName())
                + "|" + norm(goal.getExperimentalId())
                + "|" + goal.getTargetGrade()
                + "|" + goal.getPriority().name()
                + "|" + (goal.isIncludeInPlanning() ? "1" : "0")
                + "|" + (goal.hasTargetSlot() ? "scoped" : "unscoped");
    }

    private static String norm(String value) {
        return EngineeringJournalBlueprintResolver.normalizeToken(value != null ? value : "");
    }

    private static boolean identicalContents(List<EngineeringGoal> a, List<EngineeringGoal> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }
}
