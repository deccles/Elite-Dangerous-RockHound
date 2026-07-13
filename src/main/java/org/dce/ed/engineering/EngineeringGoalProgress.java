package org.dce.ed.engineering;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.dce.ed.logreader.event.MaterialStack;

/**
 * Updates engineering goals when {@link EngineerCraftEvent} journal entries arrive.
 */
public final class EngineeringGoalProgress {

    private EngineeringGoalProgress() {
    }

    /**
     * Advances matching goals' roll progress toward the next grade.
     *
     * @return true if any goal was updated
     */
    public static boolean applyCraft(List<EngineeringGoal> goals,
                                     EngineerCraftEvent craft,
                                     EngineeringDatabase database) {
        if (goals == null || goals.isEmpty() || craft == null || craft.getLevel() <= 0) {
            return false;
        }
        EngineeringDatabase db = database != null ? database : EngineeringDatabase.getInstance();
        boolean changed = false;
        for (int i = 0; i < goals.size(); i++) {
            EngineeringGoal goal = goals.get(i);
            if (goal.isInventoryConsolidation() || !matchesCraft(goal, craft, db)) {
                continue;
            }
            EngineeringGoal updated = EngineeringGradeProgress.afterCraft(goal, craft.getLevel());
            if (!updated.equals(goal)) {
                goals.set(i, updated);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Replays journal {@code EngineerCraft} events so saved goals reflect crafts from the current session.
     *
     * <p>Roll progress is rebuilt from scratch on each call so saved session progress is not stacked on
     * top of journal history.
     */
    public static boolean bootstrapFromJournal(List<EngineeringGoal> goals,
                                               String clientKey,
                                               EngineeringDatabase database) {
        if (goals == null || goals.isEmpty() || clientKey == null || clientKey.isBlank()) {
            return false;
        }
        List<EngineeringGoal> before = List.copyOf(goals);
        for (int i = 0; i < goals.size(); i++) {
            EngineeringGoal goal = goals.get(i);
            if (!goal.isInventoryConsolidation()) {
                goals.set(i, goal.withProgress(0, 0));
            }
        }
        try {
            EliteJournalReader reader = new EliteJournalReader(clientKey);
            for (EliteLogEvent event : reader.readAllEvents()) {
                if (event instanceof EngineerCraftEvent craft) {
                    applyCraft(goals, craft, database);
                }
            }
        } catch (Exception ignored) {
            // journal directory unavailable
        }
        boolean changed = false;
        for (int i = 0; i < goals.size(); i++) {
            if (!goals.get(i).equals(before.get(i))) {
                changed = true;
                break;
            }
        }
        return changed;
    }

    private static boolean matchesCraft(EngineeringGoal goal,
                                      EngineerCraftEvent craft,
                                      EngineeringDatabase db) {
        if (goal == null || craft.getLevel() <= 0) {
            return false;
        }
        Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
                EngineeringJournalBlueprintResolver.resolve(craft.getSlot(), craft.getBlueprintName(), db);
        if (resolved.isPresent()) {
            EngineeringJournalBlueprintResolver.ResolvedBlueprint bp = resolved.get();
            if (goal.getModuleType().equalsIgnoreCase(bp.moduleType())
                    && goal.getBlueprintName().equalsIgnoreCase(bp.blueprintName())) {
                return ingredientsMatchGrade(goal, craft, db);
            }
        }
        String moduleType = EngineeringJournalBlueprintResolver.slotToModuleType(craft.getSlot());
        if (!goal.getModuleType().equalsIgnoreCase(moduleType)) {
            return false;
        }
        return ingredientsMatchGrade(goal, craft, db);
    }

    private static boolean ingredientsMatchGrade(EngineeringGoal goal,
                                                 EngineerCraftEvent craft,
                                                 EngineeringDatabase db) {
        Optional<BlueprintGrade> grade = db.gradesFor(goal.getModuleType(), goal.getBlueprintName()).stream()
                .filter(g -> g.getGrade() == craft.getLevel())
                .findFirst();
        if (grade.isEmpty()) {
            return false;
        }
        return ingredientsMatch(grade.get().getMaterials(), craft.getIngredients(), db);
    }

    private static boolean ingredientsMatch(List<MaterialRequirement> required,
                                            List<MaterialStack> consumed,
                                            EngineeringDatabase db) {
        if (required == null || required.isEmpty()) {
            return false;
        }
        Map<String, Integer> consumedByKey = new HashMap<>();
        for (MaterialStack stack : consumed) {
            String key = EngineeringMaterialKeys.resolveKey(stack.getName(), stack.getNameLocalised(), db);
            if (!key.isBlank()) {
                consumedByKey.merge(key, stack.getCount(), Integer::sum);
            }
        }
        for (MaterialRequirement req : required) {
            String key = EngineeringMaterialKeys.canonicalKey(req.getKey());
            int have = consumedByKey.getOrDefault(key, 0);
            if (have < req.getCount()) {
                return false;
            }
        }
        return true;
    }
}
