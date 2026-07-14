package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.logreader.event.MaterialStack;

/**
 * Updates engineering goals when {@link EngineerCraftEvent} journal entries arrive.
 */
public final class EngineeringGoalProgress {

    private EngineeringGoalProgress() {
    }

    /**
     * Advances matching goals' roll progress toward the next grade and marks experimental effects applied.
     *
     * @return true if any goal was updated
     */
    public static boolean applyCraft(List<EngineeringGoal> goals,
                                     EngineerCraftEvent craft,
                                     EngineeringDatabase database) {
        if (goals == null || goals.isEmpty() || craft == null) {
            return false;
        }
        EngineeringDatabase db = database != null ? database : EngineeringDatabase.getInstance();
        boolean changed = false;
        for (int i = 0; i < goals.size(); i++) {
            EngineeringGoal goal = goals.get(i);
            EngineeringGoal updated = goal;
            if (craft.getLevel() > 0 && matchesCraft(goal, craft, db)) {
                EngineeringGoal gradeUpdated = EngineeringGradeProgress.afterCraft(goal, craft.getLevel());
                if (!gradeUpdated.equals(updated)) {
                    updated = gradeUpdated;
                }
            }
            if (matchesExperimentalCraft(goal, craft, db)) {
                EngineeringGoal expUpdated = updated.withExperimentalApplied(true);
                if (!expUpdated.equals(updated)) {
                    updated = expUpdated;
                }
            }
            if (!updated.equals(goal)) {
                goals.set(i, advanceCompletedUnits(updated));
                changed = true;
            }
        }
        return changed;
    }

    private static EngineeringGoal advanceCompletedUnits(EngineeringGoal goal) {
        if (!goal.isCurrentUnitComplete()) {
            return goal;
        }
        int nextCompleted = goal.getCompletedUnits() + 1;
        if (nextCompleted >= goal.getQuantity()) {
            return goal.withCompletedUnits(goal.getQuantity())
                    .withProgress(goal.getTargetGrade(), 0)
                    .withExperimentalApplied(!goal.getExperimentalId().isBlank());
        }
        return goal.withCompletedUnits(nextCompleted)
                .withProgress(0, 0)
                .withExperimentalApplied(false);
    }

    /**
     * Replays journal {@code EngineerCraft} events so saved goals reflect crafts from the current session.
     *
     * <p>Roll progress is rebuilt from scratch on each call so saved session progress is not stacked on
     * top of journal history. Saved session progress is merged back when it is ahead of the replay.
     */
    public static boolean bootstrapFromJournal(List<EngineeringGoal> goals,
                                               String clientKey,
                                               EngineeringDatabase database) {
        if (goals == null || goals.isEmpty() || clientKey == null || clientKey.isBlank()) {
            return false;
        }
        List<EngineeringGoal> saved = List.copyOf(goals);
        EngineeringDatabase db = database != null ? database : EngineeringDatabase.getInstance();
        for (int i = 0; i < goals.size(); i++) {
            goals.set(i, goals.get(i).resetJournalProgress());
        }
        boolean replayed = false;
        try {
            EliteJournalReader reader = new EliteJournalReader(clientKey);
            replayed = replayCraftHistory(goals, reader.readAllEvents(), db);
        } catch (Exception ignored) {
            // journal directory unavailable
        }
        boolean loadoutChanged = bootstrapFromLatestLoadout(goals, clientKey, database);
        boolean merged = false;
        for (int i = 0; i < goals.size(); i++) {
            EngineeringGoal mergedGoal = mergeProgress(saved.get(i), goals.get(i));
            if (!mergedGoal.equals(goals.get(i))) {
                goals.set(i, mergedGoal);
                merged = true;
            }
        }
        return replayed || loadoutChanged || merged || goalsChanged(saved, goals);
    }

    /**
     * Replays {@code EngineerCraft} events onto already-reset goals, tracking each physical module
     * instance separately so qty&gt;1 interleaved crafts are counted correctly.
     */
    static boolean replayCraftHistory(List<EngineeringGoal> goals,
                                      Iterable<? extends EliteLogEvent> events,
                                      EngineeringDatabase database) {
        if (goals == null || goals.isEmpty() || events == null) {
            return false;
        }
        EngineeringDatabase db = database != null ? database : EngineeringDatabase.getInstance();
        List<Map<String, EngineeringGoal>> instancesByGoal = collectInstancesByGoal(goals, events, db);
        boolean replayed = false;
        for (int i = 0; i < goals.size(); i++) {
            Map<String, EngineeringGoal> instances = instancesByGoal.get(i);
            if (!instances.isEmpty()) {
                goals.set(i, aggregateInstances(goals.get(i), instances.values()));
                replayed = true;
            }
        }
        return replayed;
    }

    /**
     * Per-module craft progress for the build dialog (one row per physical slot/item).
     */
    public record ModuleUnitProgress(String goalLabel,
                                     String moduleLabel,
                                     int targetGrade,
                                     EngineeringGoal unit) {
    }

    /**
     * Builds per-module progress rows from journal crafts (and current-ship loadout when available).
     */
    public static List<ModuleUnitProgress> collectModuleUnitProgress(List<EngineeringGoal> goals,
                                                                     String clientKey,
                                                                     EngineeringDatabase database) {
        List<ModuleUnitProgress> rows = new ArrayList<>();
        if (goals == null || goals.isEmpty() || clientKey == null || clientKey.isBlank()) {
            return rows;
        }
        EngineeringDatabase db = database != null ? database : EngineeringDatabase.getInstance();
        try {
            EliteJournalReader reader = new EliteJournalReader(clientKey);
            List<EngineeringGoal> templates = new ArrayList<>(goals.size());
            for (EngineeringGoal goal : goals) {
                templates.add(goal.resetJournalProgress());
            }
            List<Map<String, EngineeringGoal>> byGoal =
                    collectInstancesByGoal(templates, reader.readAllEvents(), db);
            // Overlay current-ship loadout onto matching slot|item keys when it is ahead.
            try {
                EliteLogEvent loadoutEvent = reader.findMostRecentEvent(EliteEventType.LOADOUT, 24);
                if (loadoutEvent instanceof LoadoutEvent loadout) {
                    mergeLoadoutIntoInstances(templates, byGoal, loadout, db);
                }
            } catch (Exception ignored) {
                // loadout optional for display
            }
            for (int i = 0; i < templates.size(); i++) {
                EngineeringGoal goal = goals.get(i);
                String goalLabel = goal.getModuleType() + ": " + goal.getBlueprintName();
                Map<String, EngineeringGoal> instances = byGoal.get(i);
                if (instances.isEmpty()) {
                    rows.add(new ModuleUnitProgress(
                            goalLabel,
                            "(no journal crafts found)",
                            goal.getTargetGrade(),
                            goal));
                    continue;
                }
                List<Map.Entry<String, EngineeringGoal>> sorted = new ArrayList<>(instances.entrySet());
                sorted.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));
                for (Map.Entry<String, EngineeringGoal> e : sorted) {
                    rows.add(new ModuleUnitProgress(
                            goalLabel,
                            formatInstanceLabel(e.getKey()),
                            goal.getTargetGrade(),
                            e.getValue()));
                }
            }
        } catch (Exception ignored) {
            for (EngineeringGoal goal : goals) {
                rows.add(new ModuleUnitProgress(
                        goal.getModuleType() + ": " + goal.getBlueprintName(),
                        "(journal unavailable)",
                        goal.getTargetGrade(),
                        goal));
            }
        }
        return rows;
    }

    private static List<Map<String, EngineeringGoal>> collectInstancesByGoal(
            List<EngineeringGoal> goals,
            Iterable<? extends EliteLogEvent> events,
            EngineeringDatabase db) {
        List<Map<String, EngineeringGoal>> instancesByGoal = new ArrayList<>(goals.size());
        for (int i = 0; i < goals.size(); i++) {
            instancesByGoal.add(new LinkedHashMap<>());
        }
        if (events == null) {
            return instancesByGoal;
        }
        for (EliteLogEvent event : events) {
            if (!(event instanceof EngineerCraftEvent craft)) {
                continue;
            }
            for (int i = 0; i < goals.size(); i++) {
                EngineeringGoal template = goals.get(i);
                if (!matchesGoalModuleBlueprint(template, craft, db)) {
                    continue;
                }
                Map<String, EngineeringGoal> instances = instancesByGoal.get(i);
                String key = instanceKey(craft);
                EngineeringGoal working = instances.computeIfAbsent(key, k -> blankUnitProgress(template));
                List<EngineeringGoal> one = new ArrayList<>(1);
                one.add(working);
                if (applyCraft(one, craft, db)) {
                    instances.put(key, one.get(0));
                }
            }
        }
        return instancesByGoal;
    }

    private static void mergeLoadoutIntoInstances(List<EngineeringGoal> goals,
                                                  List<Map<String, EngineeringGoal>> instancesByGoal,
                                                  LoadoutEvent loadout,
                                                  EngineeringDatabase db) {
        for (LoadoutEvent.Module module : loadout.getModules()) {
            LoadoutEvent.Engineering engineering = module.getEngineering();
            if (engineering == null) {
                continue;
            }
            Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
                    EngineeringJournalBlueprintResolver.resolve(
                            module.getSlot(), module.getItem(), engineering.getBlueprintName(), db);
            if (resolved.isEmpty()) {
                continue;
            }
            String key = loadoutInstanceKey(module);
            for (int i = 0; i < goals.size(); i++) {
                EngineeringGoal template = goals.get(i);
                if (!template.getModuleType().equalsIgnoreCase(resolved.get().moduleType())
                        || !template.getBlueprintName().equalsIgnoreCase(resolved.get().blueprintName())) {
                    continue;
                }
                Map<String, EngineeringGoal> instances = instancesByGoal.get(i);
                EngineeringGoal working = instances.computeIfAbsent(key, k -> blankUnitProgress(template));
                EngineeringGoal updated = applyPartialLoadoutProgress(working, engineering, db);
                if (isEngineeringCompleteForGoal(template, engineering, db)) {
                    updated = updated.withProgress(template.getTargetGrade(), 0)
                            .withExperimentalApplied(!template.getExperimentalId().isBlank())
                            .withCompletedUnits(1);
                }
                instances.put(key, updated);
            }
        }
    }

    private static String instanceKey(EngineerCraftEvent craft) {
        String slot = craft.getSlot() == null ? "" : craft.getSlot().trim();
        String module = craft.getModule() == null ? "" : craft.getModule().trim().toLowerCase(Locale.ROOT);
        return slot + "|" + module;
    }

    private static String loadoutInstanceKey(LoadoutEvent.Module module) {
        String slot = module.getSlot() == null ? "" : module.getSlot().trim();
        String item = module.getItem() == null ? "" : module.getItem().trim().toLowerCase(Locale.ROOT);
        return slot + "|" + item;
    }

    static String formatInstanceLabel(String instanceKey) {
        if (instanceKey == null || instanceKey.isBlank()) {
            return "?";
        }
        int bar = instanceKey.indexOf('|');
        String slot = bar >= 0 ? instanceKey.substring(0, bar) : instanceKey;
        String item = bar >= 0 ? instanceKey.substring(bar + 1) : "";
        String friendly = friendlifyItemId(item);
        if (!slot.isBlank() && !friendly.isBlank()) {
            return slot + " · " + friendly;
        }
        if (!slot.isBlank()) {
            return slot;
        }
        return friendly.isBlank() ? instanceKey : friendly;
    }

    private static String friendlifyItemId(String item) {
        if (item == null || item.isBlank()) {
            return "";
        }
        String s = item;
        if (s.startsWith("hpt_")) {
            s = s.substring(4);
        } else if (s.startsWith("int_")) {
            s = s.substring(4);
        }
        s = s.replace('_', ' ').trim();
        if (s.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        boolean cap = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                out.append(c);
                cap = true;
            } else if (cap) {
                out.append(Character.toUpperCase(c));
                cap = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static EngineeringGoal blankUnitProgress(EngineeringGoal template) {
        return new EngineeringGoal(
                template.getBlueprintId(),
                template.getModuleType(),
                template.getBlueprintName(),
                0,
                0,
                template.getTargetGrade(),
                template.getExperimentalId(),
                template.isIncludeInPlanning(),
                false,
                1,
                0);
    }

    private static EngineeringGoal aggregateInstances(EngineeringGoal template,
                                                      Collection<EngineeringGoal> instances) {
        int targetQty = Math.max(1, template.getQuantity());
        int completed = 0;
        EngineeringGoal bestPartial = null;
        int bestScore = -1;
        for (EngineeringGoal instance : instances) {
            if (instance == null) {
                continue;
            }
            completed += Math.max(0, instance.getCompletedUnits());
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
        completed = Math.min(targetQty, completed);
        EngineeringGoal aggregated = template.withCompletedUnits(completed);
        if (completed >= targetQty) {
            return aggregated.withProgress(template.getTargetGrade(), 0)
                    .withExperimentalApplied(!template.getExperimentalId().isBlank());
        }
        if (bestPartial != null) {
            return aggregated.withProgress(bestPartial.getFromGrade(), bestPartial.getCraftsAtCurrentGrade())
                    .withExperimentalApplied(bestPartial.isExperimentalApplied());
        }
        return aggregated;
    }

    /**
     * Updates goals from a live {@link LoadoutEvent}.
     */
    public static boolean applyLoadout(List<EngineeringGoal> goals,
                                       LoadoutEvent loadout,
                                       EngineeringDatabase database) {
        if (goals == null || goals.isEmpty() || loadout == null) {
            return false;
        }
        EngineeringDatabase db = database != null ? database : EngineeringDatabase.getInstance();
        boolean changed = false;
        for (int i = 0; i < goals.size(); i++) {
            EngineeringGoal updated = applyLoadoutToGoal(goals.get(i), loadout, db);
            if (!updated.equals(goals.get(i))) {
                goals.set(i, updated);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Uses the latest ship loadout to infer completed grades and applied experimental effects.
     */
    public static boolean bootstrapFromLatestLoadout(List<EngineeringGoal> goals,
                                                     String clientKey,
                                                     EngineeringDatabase database) {
        if (goals == null || goals.isEmpty() || clientKey == null || clientKey.isBlank()) {
            return false;
        }
        try {
            EliteJournalReader reader = new EliteJournalReader(clientKey);
            EliteLogEvent event = reader.findMostRecentEvent(EliteEventType.LOADOUT, 24);
            if (!(event instanceof LoadoutEvent loadout)) {
                return false;
            }
            return applyLoadout(goals, loadout, database);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static EngineeringGoal applyLoadoutToGoal(EngineeringGoal goal,
                                                        LoadoutEvent loadout,
                                                        EngineeringDatabase db) {
        if (goal == null) {
            return goal;
        }
        int completeOnShip = 0;
        EngineeringGoal bestPartial = goal;
        int bestPartialLevel = -1;

        for (LoadoutEvent.Module module : loadout.getModules()) {
            LoadoutEvent.Engineering engineering = module.getEngineering();
            if (engineering == null) {
                continue;
            }
            Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
                    EngineeringJournalBlueprintResolver.resolve(
                            module.getSlot(), module.getItem(), engineering.getBlueprintName(), db);
            if (resolved.isEmpty()) {
                continue;
            }
            if (!goal.getModuleType().equalsIgnoreCase(resolved.get().moduleType())
                    || !goal.getBlueprintName().equalsIgnoreCase(resolved.get().blueprintName())) {
                continue;
            }
            if (isEngineeringCompleteForGoal(goal, engineering, db)) {
                completeOnShip++;
                continue;
            }
            int level = engineering.getLevel();
            if (level > bestPartialLevel) {
                bestPartialLevel = level;
                bestPartial = applyPartialLoadoutProgress(goal, engineering, db);
            }
        }

        // Current Loadout is only one ship. Never regress journal/session progress for modules
        // parked on other ships (full multi-ship engineering status is a separate follow-up).
        int completedUnits = Math.max(
                goal.getCompletedUnits(),
                Math.min(goal.getQuantity(), completeOnShip));
        EngineeringGoal updated = goal.withCompletedUnits(completedUnits);
        if (completedUnits >= goal.getQuantity()) {
            return updated.withProgress(goal.getTargetGrade(), 0)
                    .withExperimentalApplied(!goal.getExperimentalId().isBlank());
        }
        if (bestPartialLevel >= 0) {
            updated = bestPartial.withCompletedUnits(completedUnits);
        }
        return updated;
    }

    private static EngineeringGoal applyPartialLoadoutProgress(EngineeringGoal goal,
                                                                 LoadoutEvent.Engineering engineering,
                                                                 EngineeringDatabase db) {
        EngineeringGoal updated = goal;
        int level = engineering.getLevel();
        double quality = engineering.getQuality();
        if (level > 0) {
            int loadoutFrom;
            int loadoutCrafts;
            // Quality < 1 means the commander is still rolling the current Level grade.
            // fromGrade = grades fully finished; craftsAtCurrentGrade ≈ Quality * 5.
            if (quality >= 0.999d) {
                loadoutFrom = Math.min(level, goal.getTargetGrade());
                loadoutCrafts = 0;
            } else {
                loadoutFrom = Math.min(Math.max(0, level - 1), goal.getTargetGrade());
                loadoutCrafts = Math.max(0, Math.min(
                        EngineeringGradeProgress.ROLLS_PER_GRADE - 1,
                        (int) Math.round(quality * EngineeringGradeProgress.ROLLS_PER_GRADE)));
                if (loadoutCrafts <= 0 && quality > 0.01d) {
                    loadoutCrafts = 1;
                }
            }
            if (loadoutFrom > updated.getFromGrade()
                    || (loadoutFrom == updated.getFromGrade()
                            && loadoutCrafts > updated.getCraftsAtCurrentGrade())) {
                updated = updated.withProgress(loadoutFrom, loadoutCrafts);
            }
        }
        if (!goal.getExperimentalId().isBlank() && !updated.isExperimentalApplied()) {
            Optional<BlueprintGrade> experimental = db.findById(goal.getExperimentalId());
            if (experimental.isPresent()
                    && experimentalEffectMatches(
                            "",
                            engineering.getExperimentalEffect(),
                            engineering.getExperimentalEffectLocalised(),
                            experimental.get())) {
                updated = updated.withExperimentalApplied(true);
            }
        }
        return updated;
    }

    private static boolean isEngineeringCompleteForGoal(EngineeringGoal goal,
                                                          LoadoutEvent.Engineering engineering,
                                                          EngineeringDatabase db) {
        int level = engineering.getLevel();
        if (level < goal.getTargetGrade() || engineering.getQuality() < 0.999d) {
            return false;
        }
        if (goal.getExperimentalId().isBlank()) {
            return true;
        }
        Optional<BlueprintGrade> experimental = db.findById(goal.getExperimentalId());
        return experimental.isPresent()
                && experimentalEffectMatches(
                        "",
                        engineering.getExperimentalEffect(),
                        engineering.getExperimentalEffectLocalised(),
                        experimental.get());
    }

    private static EngineeringGoal mergeProgress(EngineeringGoal saved, EngineeringGoal replayed) {
        EngineeringGoal merged = replayed;
        if (saved.getFromGrade() > replayed.getFromGrade()) {
            merged = merged.withProgress(saved.getFromGrade(), saved.getCraftsAtCurrentGrade());
        } else if (saved.getFromGrade() == replayed.getFromGrade()
                && saved.getCraftsAtCurrentGrade() > replayed.getCraftsAtCurrentGrade()) {
            merged = merged.withProgress(saved.getFromGrade(), saved.getCraftsAtCurrentGrade());
        }
        if (saved.isExperimentalApplied()) {
            merged = merged.withExperimentalApplied(true);
        }
        if (saved.getCompletedUnits() > merged.getCompletedUnits()) {
            EngineeringGoal candidate = merged.withCompletedUnits(saved.getCompletedUnits());
            // Stale "done" counts from a lower target must not hide materials for a raised target.
            boolean staleComplete = candidate.getFromGrade() < candidate.getTargetGrade()
                    && candidate.getCompletedUnits() >= candidate.getQuantity();
            if (!staleComplete) {
                merged = candidate;
            }
        }
        return merged;
    }

    private static boolean goalsChanged(List<EngineeringGoal> before, List<EngineeringGoal> after) {
        for (int i = 0; i < before.size(); i++) {
            if (!before.get(i).equals(after.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesCraft(EngineeringGoal goal,
                                      EngineerCraftEvent craft,
                                      EngineeringDatabase db) {
        if (goal == null || craft.getLevel() <= 0) {
            return false;
        }
        // Trust resolved blueprint + craft level. Strict ingredient matching rejected valid crafts when
        // journal names/counts diverged from the catalog, so inventory fell while Need did not.
        return matchesGoalModuleBlueprint(goal, craft, db);
    }

    private static boolean matchesExperimentalCraft(EngineeringGoal goal,
                                                    EngineerCraftEvent craft,
                                                    EngineeringDatabase db) {
        if (goal == null || goal.isExperimentalApplied()) {
            return false;
        }
        String expId = goal.getExperimentalId();
        if (expId == null || expId.isBlank()) {
            return false;
        }
        Optional<BlueprintGrade> expBp = db.findById(expId);
        if (expBp.isEmpty()) {
            return false;
        }
        if (!matchesGoalModuleBlueprint(goal, craft, db)) {
            return false;
        }
        if (experimentalEffectMatches(
                craft.getApplyExperimentalEffect(),
                craft.getExperimentalEffect(),
                craft.getExperimentalEffectLocalised(),
                expBp.get())) {
            return true;
        }
        return ingredientsMatch(expBp.get().getMaterials(), craft.getIngredients(), db);
    }

    private static boolean matchesGoalModuleBlueprint(EngineeringGoal goal,
                                                      EngineerCraftEvent craft,
                                                      EngineeringDatabase db) {
        Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
                EngineeringJournalBlueprintResolver.resolve(
                        craft.getSlot(), craft.getModule(), craft.getBlueprintName(), db);
        if (resolved.isPresent()) {
            EngineeringJournalBlueprintResolver.ResolvedBlueprint bp = resolved.get();
            return goal.getModuleType().equalsIgnoreCase(bp.moduleType())
                    && goal.getBlueprintName().equalsIgnoreCase(bp.blueprintName());
        }
        return false;
    }

    private static boolean experimentalEffectMatches(String applyExperimentalEffect,
                                                     String experimentalEffect,
                                                     String experimentalEffectLocalised,
                                                     BlueprintGrade experimental) {
        String normalizedName = EngineeringJournalBlueprintResolver.normalizeToken(experimental.getName());
        String normalizedId = EngineeringJournalBlueprintResolver.normalizeToken(experimental.getId());
        for (String candidate : List.of(
                applyExperimentalEffect,
                experimentalEffect,
                experimentalEffectLocalised)) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String normalized = EngineeringJournalBlueprintResolver.normalizeToken(candidate);
            if (normalized.isEmpty()) {
                continue;
            }
            if (normalized.contains(normalizedName)
                    || normalizedName.contains(normalized)
                    || normalized.contains(normalizedId)
                    || normalizedId.contains(normalized)) {
                return true;
            }
        }
        return false;
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
