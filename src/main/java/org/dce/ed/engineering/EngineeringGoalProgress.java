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
import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.dce.ed.logreader.event.LoadGameEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.logreader.event.MaterialStack;

import com.google.gson.JsonObject;

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
        return applyCraft(goals, craft, database, -1L);
    }

    /**
     * @param currentShipId active hull when the craft occurred; {@code < 0} skips ship filtering
     */
    public static boolean applyCraft(List<EngineeringGoal> goals,
                                     EngineerCraftEvent craft,
                                     EngineeringDatabase database,
                                     long currentShipId) {
        if (goals == null || goals.isEmpty() || craft == null) {
            return false;
        }
        EngineeringDatabase db = database != null ? database : EngineeringDatabase.getInstance();
        boolean changed = false;
        for (int i = 0; i < goals.size(); i++) {
            EngineeringGoal goal = goals.get(i);
            if (!goalMatchesShip(goal, currentShipId)) {
                continue;
            }
            EngineeringGoal updated = goal;
            if (craft.getLevel() > 0 && matchesCraft(goal, craft, db)) {
                EngineeringGoal gradeUpdated = EngineeringGradeProgress.afterCraft(
                        updated, craft.getLevel(), craft.getQuality());
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

    /** When {@code currentShipId >= 0} and the goal has a ship, only match that hull. */
    private static boolean goalMatchesShip(EngineeringGoal goal, long currentShipId) {
        if (goal == null || currentShipId < 0 || !goal.hasShip()) {
            return true;
        }
        return goal.getShipId() == currentShipId;
    }

    /**
     * Returns whether a live craft is covered by a goal for the current ship and module slot.
     *
     * <p>Grade rolls only need a matching module and blueprint. Applying an experimental effect
     * is covered only when a matching goal requests that same effect.</p>
     */
    public static boolean hasMatchingGoal(List<EngineeringGoal> goals,
                                          EngineerCraftEvent craft,
                                          EngineeringDatabase database,
                                          long currentShipId) {
        if (goals == null || goals.isEmpty() || craft == null) {
            return false;
        }
        EngineeringDatabase db = database != null ? database : EngineeringDatabase.getInstance();
        boolean experimentalApply = EngineeringLoadoutExperimentalPatch.isExperimentalApply(craft);
        for (EngineeringGoal goal : goals) {
            if (!goalMatchesShip(goal, currentShipId)
                    || !matchesGoalModuleBlueprint(goal, craft, db)) {
                continue;
            }
            if (goal.hasTargetSlot()) {
                String craftSlot = craft.getSlot() != null ? craft.getSlot().trim() : "";
                if (!craftSlot.isBlank() && !goal.targetsSlot(craftSlot)) {
                    continue;
                }
            }
            if (!experimentalApply) {
                return true;
            }
            String experimentalId = goal.getExperimentalId();
            if (experimentalId == null || experimentalId.isBlank()) {
                continue;
            }
            Optional<BlueprintGrade> experimental = db.findById(experimentalId);
            if (experimental.isPresent()
                    && experimentalEffectMatches(
                            craft.getApplyExperimentalEffect(),
                            craft.getExperimentalEffect(),
                            craft.getExperimentalEffectLocalised(),
                            experimental.get())) {
                return true;
            }
        }
        return false;
    }

    private static EngineeringGoal advanceCompletedUnits(EngineeringGoal goal) {
        if (!goal.isCurrentUnitComplete()) {
            return goal;
        }
        int nextCompleted = goal.getCompletedUnits() + 1;
        if (nextCompleted >= goal.getQuantity()) {
            // experimentalApplied must already be true when the goal requires one (see
            // isCurrentUnitComplete); do not invent it from experimentalId alone.
            return goal.withCompletedUnits(goal.getQuantity())
                    .withProgress(goal.getTargetGrade(), 0);
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
        boolean[] replayEvidence = new boolean[goals.size()];
        boolean replayed = replayCraftHistoryFromStore(goals, clientKey, db, replayEvidence);
        if (!replayed) {
            try {
                EliteJournalReader reader = new EliteJournalReader(clientKey);
                replayed = replayCraftHistory(goals, reader.readAllEvents(), db, replayEvidence);
            } catch (Exception ignored) {
                // journal directory unavailable
            }
        }
        boolean loadoutChanged = applyStoredLoadouts(goals, clientKey, db);
        if (!loadoutChanged) {
            loadoutChanged = bootstrapFromLatestLoadout(goals, clientKey, database);
        }
        boolean merged = false;
        for (int i = 0; i < goals.size(); i++) {
            EngineeringGoal mergedGoal = mergeProgress(saved.get(i), goals.get(i), replayEvidence[i]);
            if (!mergedGoal.equals(goals.get(i))) {
                goals.set(i, mergedGoal);
                merged = true;
            }
        }
        return replayed || loadoutChanged || merged || goalsChanged(saved, goals);
    }

    /** Replays ship-attributed crafts from {@link EngineeringCraftStore}. */
    static boolean replayCraftHistoryFromStore(List<EngineeringGoal> goals,
                                               String clientKey,
                                               EngineeringDatabase database) {
        return replayCraftHistoryFromStore(goals, clientKey, database, null);
    }

    /**
     * @param replayEvidenceOut when non-null, {@code [i]} is set when goal {@code i} had at least one
     *        matching craft in the store (per-instance replay is authoritative for that goal)
     */
    static boolean replayCraftHistoryFromStore(List<EngineeringGoal> goals,
                                               String clientKey,
                                               EngineeringDatabase database,
                                               boolean[] replayEvidenceOut) {
        List<EngineeringCraftRecord> records = EngineeringCraftStore.listCrafts(clientKey);
        if (records.isEmpty()) {
            return false;
        }
        EngineeringDatabase db = database != null ? database : EngineeringDatabase.getInstance();
        EliteLogParser parser = new EliteLogParser();
        List<Map<String, EngineeringGoal>> instancesByGoal = new ArrayList<>(goals.size());
        for (int i = 0; i < goals.size(); i++) {
            instancesByGoal.add(new LinkedHashMap<>());
        }
        boolean replayed = false;
        for (EngineeringCraftRecord record : records) {
            EngineerCraftEvent craft = toCraftEvent(record, parser);
            if (craft == null) {
                continue;
            }
            long shipId = record.getShipId();
            if (shipId < 0) {
                continue;
            }
            for (int i = 0; i < goals.size(); i++) {
                EngineeringGoal template = goals.get(i);
                if (!goalMatchesShip(template, shipId)) {
                    continue;
                }
                if (applyCraftToInstances(instancesByGoal.get(i), template, craft, db, shipId)) {
                    replayed = true;
                }
            }
        }
        for (int i = 0; i < goals.size(); i++) {
            Map<String, EngineeringGoal> instances = instancesByGoal.get(i);
            if (!instances.isEmpty()) {
                goals.set(i, aggregateInstances(goals.get(i), instances.values()));
                replayed = true;
                if (replayEvidenceOut != null && i < replayEvidenceOut.length) {
                    replayEvidenceOut[i] = true;
                }
            }
        }
        return replayed;
    }

    static boolean applyStoredLoadouts(List<EngineeringGoal> goals,
                                       String clientKey,
                                       EngineeringDatabase database) {
        Map<Long, LoadoutEvent> loadouts = EngineeringCraftStore.loadLatestLoadouts(clientKey);
        if (loadouts.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (LoadoutEvent loadout : loadouts.values()) {
            if (applyLoadout(goals, loadout, database)) {
                changed = true;
            }
        }
        return changed;
    }

    private static EngineerCraftEvent toCraftEvent(EngineeringCraftRecord record, EliteLogParser parser) {
        if (record == null) {
            return null;
        }
        if (record.getRawJson() != null && !record.getRawJson().isBlank()) {
            try {
                EliteLogEvent event = parser.parseRecord(record.getRawJson());
                if (event instanceof EngineerCraftEvent craft) {
                    return craft;
                }
            } catch (Exception ignored) {
                // fall through to synthetic
            }
        }
        JsonObject raw = new JsonObject();
        raw.addProperty("timestamp", record.getTimestamp().toString());
        raw.addProperty("event", "EngineerCraft");
        return new EngineerCraftEvent(
                record.getTimestamp(),
                raw,
                record.getSlot(),
                record.getModule(),
                "",
                0L,
                record.getBlueprintName(),
                0L,
                record.getLevel(),
                record.getQuality(),
                "",
                "",
                "",
                List.of());
    }

    /**
     * Replays {@code EngineerCraft} events onto already-reset goals, tracking each physical module
     * instance separately so qty&gt;1 interleaved crafts are counted correctly.
     */
    static boolean replayCraftHistory(List<EngineeringGoal> goals,
                                      Iterable<? extends EliteLogEvent> events,
                                      EngineeringDatabase database) {
        return replayCraftHistory(goals, events, database, null);
    }

    static boolean replayCraftHistory(List<EngineeringGoal> goals,
                                      Iterable<? extends EliteLogEvent> events,
                                      EngineeringDatabase database,
                                      boolean[] replayEvidenceOut) {
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
                if (replayEvidenceOut != null && i < replayEvidenceOut.length) {
                    replayEvidenceOut[i] = true;
                }
            }
        }
        return replayed;
    }

    /**
     * Per-module craft progress for the build dialog (one row per physical slot/item).
     */
    public record ModuleUnitProgress(long shipId,
                                     String shipLabel,
                                     String moduleType,
                                     String blueprintName,
                                     String moduleLabel,
                                     int targetGrade,
                                     EngineeringGoal unit,
                                     boolean installed) {
        public String goalHeadline() {
            String mod = moduleType != null ? moduleType.trim() : "";
            String bp = blueprintName != null ? blueprintName.trim() : "";
            if (!mod.isEmpty() && !bp.isEmpty()) {
                return mod + " · " + bp;
            }
            return !bp.isEmpty() ? bp : (!mod.isEmpty() ? mod : "Goal");
        }
    }

    /**
     * Builds per-module progress rows from journal crafts and latest loadout per ship.
     */
    public static List<ModuleUnitProgress> collectModuleUnitProgress(List<EngineeringGoal> goals,
                                                                     String clientKey,
                                                                     EngineeringDatabase database) {
        List<ModuleUnitProgress> rows = new ArrayList<>();
        if (goals == null || goals.isEmpty() || clientKey == null || clientKey.isBlank()) {
            return rows;
        }
        EngineeringDatabase db = database != null ? database : EngineeringDatabase.getInstance();
        List<EngineeringGoal> templates = new ArrayList<>(goals.size());
        for (EngineeringGoal goal : goals) {
            templates.add(goal.resetJournalProgress());
        }

        List<Map<String, EngineeringGoal>> byGoal = collectInstancesFromStore(templates, clientKey, db);
        Map<Long, LoadoutEvent> latestLoadoutByShip = EngineeringCraftStore.loadLatestLoadouts(clientKey);
        boolean storeReady = EngineeringCraftStore.hasCrafts(clientKey) || !latestLoadoutByShip.isEmpty();
        if (!storeReady) {
            // Store empty / not yet imported — fall back to a full journal scan.
            try {
                EliteJournalReader reader = new EliteJournalReader(clientKey);
                List<EliteLogEvent> events = reader.readAllEvents();
                byGoal = collectInstancesByGoal(templates, events, db);
                latestLoadoutByShip = latestLoadoutByShipId(events);
            } catch (Exception ignored) {
                for (EngineeringGoal goal : goals) {
                    long shipId = goal.getShipId();
                    String shipLabel = !goal.getShipLabel().isBlank()
                            ? goal.getShipLabel()
                            : (shipId >= 0 ? "Ship #" + shipId : "Unassigned");
                    rows.add(new ModuleUnitProgress(
                            shipId,
                            shipLabel,
                            goal.getModuleType(),
                            goal.getBlueprintName(),
                            "(journal unavailable)",
                            goal.getTargetGrade(),
                            goal,
                            false));
                }
                return rows;
            }
        }
        for (LoadoutEvent loadout : latestLoadoutByShip.values()) {
            mergeLoadoutIntoInstances(templates, byGoal, loadout, db);
        }
        for (int i = 0; i < templates.size(); i++) {
            EngineeringGoal goal = goals.get(i);
            long shipId = goal.getShipId();
            String shipLabel = !goal.getShipLabel().isBlank()
                    ? goal.getShipLabel()
                    : (shipId >= 0 ? "Ship #" + shipId : "Unassigned");
            Map<String, EngineeringGoal> instances = byGoal.get(i);
            int qty = Math.max(1, goal.getQuantity());
            if (instances.isEmpty()) {
                for (int u = 0; u < qty; u++) {
                    rows.add(notInstalledRow(goal, shipId, shipLabel));
                }
                continue;
            }
            List<Map.Entry<String, EngineeringGoal>> sorted = new ArrayList<>(instances.entrySet());
            sorted.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));
            for (Map.Entry<String, EngineeringGoal> e : sorted) {
                rows.add(new ModuleUnitProgress(
                        shipId,
                        shipLabel,
                        goal.getModuleType(),
                        goal.getBlueprintName(),
                        formatInstanceLabel(e.getKey()),
                        goal.getTargetGrade(),
                        e.getValue(),
                        true));
            }
            for (int u = sorted.size(); u < qty; u++) {
                rows.add(notInstalledRow(goal, shipId, shipLabel));
            }
        }
        return rows;
    }

    private static List<Map<String, EngineeringGoal>> collectInstancesFromStore(
            List<EngineeringGoal> goals,
            String clientKey,
            EngineeringDatabase db) {
        List<Map<String, EngineeringGoal>> instancesByGoal = new ArrayList<>(goals.size());
        for (int i = 0; i < goals.size(); i++) {
            instancesByGoal.add(new LinkedHashMap<>());
        }
        List<EngineeringCraftRecord> records = EngineeringCraftStore.listCrafts(clientKey);
        if (records.isEmpty()) {
            return instancesByGoal;
        }
        EliteLogParser parser = new EliteLogParser();
        for (EngineeringCraftRecord record : records) {
            EngineerCraftEvent craft = toCraftEvent(record, parser);
            if (craft == null) {
                continue;
            }
            long shipId = record.getShipId();
            if (shipId < 0) {
                continue;
            }
            for (int i = 0; i < goals.size(); i++) {
                EngineeringGoal template = goals.get(i);
                if (!goalMatchesShip(template, shipId)) {
                    continue;
                }
                applyCraftToInstances(instancesByGoal.get(i), template, craft, db, shipId);
            }
        }
        return instancesByGoal;
    }

    private static ModuleUnitProgress notInstalledRow(EngineeringGoal goal, long shipId, String shipLabel) {
        return new ModuleUnitProgress(
                shipId,
                shipLabel,
                goal.getModuleType(),
                goal.getBlueprintName(),
                "Not installed",
                goal.getTargetGrade(),
                goal,
                false);
    }

    private static Map<Long, LoadoutEvent> latestLoadoutByShipId(Iterable<? extends EliteLogEvent> events) {
        Map<Long, LoadoutEvent> latest = new LinkedHashMap<>();
        if (events == null) {
            return latest;
        }
        for (EliteLogEvent event : events) {
            if (event instanceof LoadoutEvent loadout && loadout.getShipId() >= 0) {
                latest.put(Long.valueOf(loadout.getShipId()), loadout);
            }
        }
        return latest;
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
        long currentShipId = -1L;
        for (EliteLogEvent event : events) {
            if (event instanceof LoadoutEvent loadout && loadout.getShipId() >= 0) {
                currentShipId = loadout.getShipId();
                continue;
            }
            if (event instanceof LoadGameEvent loadGame && loadGame.getShipId() >= 0) {
                currentShipId = loadGame.getShipId();
                continue;
            }
            if (!(event instanceof EngineerCraftEvent craft)) {
                continue;
            }
            for (int i = 0; i < goals.size(); i++) {
                EngineeringGoal template = goals.get(i);
                if (!goalMatchesShip(template, currentShipId)) {
                    continue;
                }
                applyCraftToInstances(instancesByGoal.get(i), template, craft, db, currentShipId);
            }
        }
        return instancesByGoal;
    }

    /**
     * Applies one craft to the per-instance progress map for a goal. Crafts for the goal's
     * module/blueprint advance the matching instance; crafts that re-engineer the same physical
     * module (same slot/item) with a <em>different</em> blueprint wipe its progress — in game,
     * replacing the blueprint destroys the old grades and experimental (LargeHardpoint1 rolled to
     * Focused and back kept a January "Oversized applied" flag and falsely completed the goal).
     *
     * @return true when the goal's blueprint progress advanced (not for wipes)
     */
    private static boolean applyCraftToInstances(Map<String, EngineeringGoal> instances,
                                                 EngineeringGoal template,
                                                 EngineerCraftEvent craft,
                                                 EngineeringDatabase db,
                                                 long shipId) {
        String key = instanceKey(craft);
        if (!matchesGoalModuleBlueprint(template, craft, db)) {
            if (craft.getLevel() > 0 && instances.containsKey(key)
                    && matchesGoalModule(template, craft, db)) {
                instances.put(key, blankUnitProgress(template));
            }
            return false;
        }
        EngineeringGoal working = instances.computeIfAbsent(key, k -> blankUnitProgress(template));
        working = syncExperimentalFromCraft(working, craft, db);
        List<EngineeringGoal> one = new ArrayList<>(1);
        one.add(working);
        boolean advanced = applyCraft(one, craft, db, shipId);
        instances.put(key, one.get(0));
        return advanced;
    }

    /**
     * Grade rolls report the module's <em>current</em> experimental in {@code ExperimentalEffect};
     * absence means the module has none right now, so a stale "applied" flag from an earlier
     * blueprint must be cleared. Only trusted for real journal records (raw JSON has a Level) —
     * synthetic store fallback rows omit these fields entirely.
     */
    private static EngineeringGoal syncExperimentalFromCraft(EngineeringGoal working,
                                                             EngineerCraftEvent craft,
                                                             EngineeringDatabase db) {
        if (!working.isExperimentalApplied() || working.getCompletedUnits() > 0) {
            return working;
        }
        if (craft.getLevel() <= 0 || !craft.getApplyExperimentalEffect().isBlank()) {
            return working;
        }
        JsonObject raw = craft.getRawJson();
        if (raw == null || !raw.has("Level")) {
            return working;
        }
        String expId = working.getExperimentalId();
        if (expId == null || expId.isBlank()) {
            return working;
        }
        Optional<BlueprintGrade> expBp = db.findById(expId);
        if (expBp.isEmpty()) {
            return working;
        }
        boolean stillPresent = experimentalEffectMatches(
                null,
                craft.getExperimentalEffect(),
                craft.getExperimentalEffectLocalised(),
                expBp.get());
        return stillPresent ? working : working.withExperimentalApplied(false);
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
                if (!goalMatchesShip(template, loadout.getShipId())) {
                    continue;
                }
                if (template.hasTargetSlot()) {
                    String modSlot = module.getSlot() != null ? module.getSlot().trim() : "";
                    if (!template.targetsSlot(modSlot)) {
                        continue;
                    }
                }
                if (!template.getModuleType().equalsIgnoreCase(resolved.get().moduleType())
                        || !template.getBlueprintName().equalsIgnoreCase(resolved.get().blueprintName())) {
                    continue;
                }
                if (!template.hasTargetSlot()
                        && fittedExperimentalConflictsWithGoal(template, engineering, db)) {
                    continue;
                }
                Map<String, EngineeringGoal> instances = instancesByGoal.get(i);
                EngineeringGoal working = instances.computeIfAbsent(key, k -> blankUnitProgress(template));
                EngineeringGoal updated = applyPartialLoadoutProgress(working, engineering, db);
                if (isEngineeringCompleteForGoal(template, engineering, db)) {
                    // isEngineeringCompleteForGoal already verified the experimental when required.
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
                template.getPriority(),
                false,
                1,
                0,
                template.getShipId(),
                template.getShipLabel(),
                template.isIncludeInPlanning(),
                template.getTargetSlots());
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
            // Units only count complete when isCurrentUnitComplete (experimental satisfied) or when
            // loadout/craft marked completedUnits with experimental verified — never invent
            // experimentalApplied from experimentalId alone.
            return aggregated.withProgress(template.getTargetGrade(), 0)
                    .withExperimentalApplied(
                            template.getExperimentalId().isBlank() || anyExperimentalApplied(instances));
        }
        if (bestPartial != null) {
            return aggregated.withProgress(bestPartial.getFromGrade(), bestPartial.getCraftsAtCurrentGrade())
                    .withExperimentalApplied(bestPartial.isExperimentalApplied());
        }
        return aggregated;
    }

    private static boolean anyExperimentalApplied(Collection<EngineeringGoal> instances) {
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
        if (goal.hasShip() && loadout.getShipId() >= 0 && goal.getShipId() != loadout.getShipId()) {
            return goal;
        }
        int completeOnShip = 0;
        EngineeringGoal bestPartial = null;
        int bestPartialScore = -1;
        EngineeringGoal worstIncomplete = null;
        int worstIncompleteScore = Integer.MAX_VALUE;
        boolean sawMatchingModule = false;
        boolean sawConflictingExperimentalModule = false;
        boolean incompleteMissingExperimental = false;

        for (LoadoutEvent.Module module : loadout.getModules()) {
            if (goal.hasTargetSlot()) {
                String modSlot = module.getSlot() != null ? module.getSlot().trim() : "";
                if (!goal.targetsSlot(modSlot)) {
                    continue;
                }
            }
            LoadoutEvent.Engineering engineering = module.getEngineering();
            if (engineering == null) {
                // Stock / unengineered module of this type still counts as an unfinished G0 unit.
                String itemType = EngineeringJournalBlueprintResolver.moduleItemToModuleType(module.getItem());
                if (itemType != null && !itemType.isBlank()
                        && goal.getModuleType().equalsIgnoreCase(itemType)) {
                    sawMatchingModule = true;
                    if (!goal.getExperimentalId().isBlank()) {
                        incompleteMissingExperimental = true;
                    }
                    EngineeringGoal stock = goal.withProgress(0, 0).withExperimentalApplied(false);
                    int score = progressScore(stock);
                    if (score < worstIncompleteScore) {
                        worstIncompleteScore = score;
                        worstIncomplete = stock;
                    }
                }
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
            // Unscoped goals must not inherit grade progress from a sibling gun that already has a
            // different experimental (e.g. Auto Loader plan reading a Corrosive Overcharged MC).
            // Pinned slots still take grade (experimental swap on that hardpoint).
            if (!goal.hasTargetSlot() && fittedExperimentalConflictsWithGoal(goal, engineering, db)) {
                sawConflictingExperimentalModule = true;
                continue;
            }
            sawMatchingModule = true;
            if (isEngineeringCompleteForGoal(goal, engineering, db)) {
                completeOnShip++;
                continue;
            }
            EngineeringGoal snapshot = progressSnapshotFromEngineering(goal, engineering, db);
            if (!goal.getExperimentalId().isBlank() && !snapshot.isExperimentalApplied()) {
                incompleteMissingExperimental = true;
            }
            int score = progressScore(snapshot);
            if (score > bestPartialScore) {
                bestPartialScore = score;
                bestPartial = snapshot;
            }
            if (score < worstIncompleteScore) {
                worstIncompleteScore = score;
                worstIncomplete = snapshot;
            }
        }

        // When this hull's loadout shows an engineered goal module, it is authoritative for
        // quantity-1 goals (clears sticky session "Complete" / experimentalApplied that invent
        // materials as done). Journal craft-roll counts that are ahead of Loadout Quality must
        // not regress.
        //
        // Stock-only matches (module present, no Engineering block) are NOT authoritative:
        // Elite often omits a Loadout after EngineerCraft, so the last snapshot still looks
        // unengineered and must not wipe craft/journal progress (FSD/PD after restart).
        if (sawMatchingModule && goal.getQuantity() <= 1) {
            if (completeOnShip >= 1) {
                return goal.withCompletedUnits(1)
                        .withProgress(goal.getTargetGrade(), 0)
                        .withExperimentalApplied(!goal.getExperimentalId().isBlank());
            }
            if (bestPartial == null) {
                if (sawConflictingExperimentalModule
                        && !goal.hasTargetSlot()
                        && !goal.isComplete()
                        && worstIncomplete != null) {
                    // Sibling gun had the blueprint but the wrong experimental — snap back to the
                    // stock/G0 unit instead of keeping phantom G4/G5 from that sibling.
                    return worstIncomplete.withCompletedUnits(0).withExperimentalApplied(false);
                }
                return goal;
            }
            return mergeProgress(goal.withCompletedUnits(0), bestPartial.withCompletedUnits(0), true);
        }

        // Multi-unit: completed count from fully-done modules. Shared fromGrade must follow the
        // LEAST progressed incomplete module — using the best one made Need treat every sibling as
        // "experimental only" when a single HRP reached G5.
        int completedUnits = Math.max(
                goal.getCompletedUnits(),
                Math.min(goal.getQuantity(), completeOnShip));
        EngineeringGoal updated = goal.withCompletedUnits(completedUnits);
        if (completeOnShip >= goal.getQuantity()) {
            return updated.withProgress(goal.getTargetGrade(), 0)
                    .withExperimentalApplied(!goal.getExperimentalId().isBlank());
        }
        if (worstIncomplete != null) {
            EngineeringGoal progressed = worstIncomplete.withCompletedUnits(completedUnits);
            if (incompleteMissingExperimental) {
                progressed = progressed.withExperimentalApplied(false);
            }
            return progressed;
        }
        if (sawMatchingModule && !goal.getExperimentalId().isBlank() && completeOnShip == 0) {
            updated = updated.withExperimentalApplied(false);
        }
        return updated;
    }

    /**
     * Absolute grade/experimental progress for one loadout module (ignores sticky session fromGrade).
     */
    private static EngineeringGoal progressSnapshotFromEngineering(EngineeringGoal template,
                                                                     LoadoutEvent.Engineering engineering,
                                                                     EngineeringDatabase db) {
        EngineeringGoal updated = template.withProgress(0, 0).withExperimentalApplied(false);
        int level = engineering.getLevel();
        double quality = engineering.getQuality();
        if (level > 0) {
            int loadoutFrom;
            int loadoutCrafts;
            if (quality >= 0.999d) {
                loadoutFrom = Math.min(level, template.getTargetGrade());
                loadoutCrafts = 0;
            } else {
                loadoutFrom = Math.min(Math.max(0, level - 1), template.getTargetGrade());
                loadoutCrafts = Math.max(0, Math.min(
                        EngineeringGradeProgress.ROLLS_PER_GRADE - 1,
                        (int) Math.round(quality * EngineeringGradeProgress.ROLLS_PER_GRADE)));
                if (loadoutCrafts <= 0 && quality > 0.01d) {
                    loadoutCrafts = 1;
                }
            }
            updated = updated.withProgress(loadoutFrom, loadoutCrafts);
        }
        if (!template.getExperimentalId().isBlank()) {
            Optional<BlueprintGrade> experimental = db.findById(template.getExperimentalId());
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

    /**
     * Status-column progress 0..1. Multi-quantity goals average per-module loadout progress —
     * {@link EngineeringGoal#getFromGrade()} is the <em>worst</em> incomplete unit (for Need),
     * which would hide a single advanced sibling (e.g. one Shield Booster at G2 among stock).
     */
    public static double displayCompletionFraction(EngineeringGoal goal,
                                                   LoadoutEvent loadout,
                                                   EngineeringDatabase database,
                                                   int engineerRank) {
        if (goal == null) {
            return 0.0;
        }
        if (goal.isComplete()) {
            return 1.0;
        }
        int qty = Math.max(1, goal.getQuantity());
        if (qty <= 1 || loadout == null || !goalMatchesShip(goal, loadout.getShipId())) {
            return EngineeringGradeProgress.completionFraction(goal, engineerRank);
        }
        EngineeringDatabase db = database != null ? database : EngineeringDatabase.getInstance();
        List<Double> unitFracs = unitDisplayFractionsFromLoadout(goal, loadout, db, engineerRank);
        if (unitFracs.isEmpty()) {
            return EngineeringGradeProgress.completionFraction(goal, engineerRank);
        }
        unitFracs.sort(java.util.Comparator.reverseOrder());
        double sum = 0.0;
        int n = Math.min(qty, unitFracs.size());
        for (int i = 0; i < n; i++) {
            sum += unitFracs.get(i);
        }
        return Math.min(1.0, sum / qty);
    }

    /** Individual module fills for the stacked Status display, completed modules first. */
    public static List<Double> displayCompletionFractions(EngineeringGoal goal,
                                                          LoadoutEvent loadout,
                                                          EngineeringDatabase database,
                                                          int engineerRank) {
        if (goal == null) {
            return List.of();
        }
        int qty = Math.max(1, goal.getQuantity());
        if (goal.isComplete()) {
            return new ArrayList<>(java.util.Collections.nCopies(qty, 1.0));
        }
        if (qty == 1) {
            return List.of(EngineeringGradeProgress.completionFraction(goal, engineerRank));
        }
        if (loadout == null || !goalMatchesShip(goal, loadout.getShipId())) {
            List<Double> fills = new ArrayList<>();
            int complete = Math.min(qty, Math.max(0, goal.getCompletedUnits()));
            fills.addAll(java.util.Collections.nCopies(complete, 1.0));
            if (fills.size() < qty && (goal.getFromGrade() > 0
                    || goal.getCraftsAtCurrentGrade() > 0 || goal.isExperimentalApplied())) {
                fills.add(EngineeringGradeProgress.unitCompletionFraction(goal, engineerRank));
            }
            while (fills.size() < qty) {
                fills.add(0.0);
            }
            return fills;
        }
        EngineeringDatabase db = database != null ? database : EngineeringDatabase.getInstance();
        List<Double> fills = unitDisplayFractionsFromLoadout(goal, loadout, db, engineerRank);
        fills.sort(java.util.Comparator.reverseOrder());
        int knownComplete = 0;
        for (Double fill : fills) {
            if (fill != null && fill >= 0.999) {
                knownComplete++;
            }
        }
        int missingComplete = Math.min(qty, Math.max(0, goal.getCompletedUnits())) - knownComplete;
        for (int i = 0; i < missingComplete; i++) {
            fills.add(0, 1.0);
        }
        if (fills.size() > qty) {
            fills = new ArrayList<>(fills.subList(0, qty));
        }
        while (fills.size() < qty) {
            fills.add(0.0);
        }
        return fills;
    }

    /** True when the Status bar should show craft progress (not a blank Ready/Short row). */
    public static boolean hasDisplayCraftProgress(EngineeringGoal goal,
                                                  LoadoutEvent loadout,
                                                  EngineeringDatabase database) {
        if (goal == null) {
            return false;
        }
        if (goal.getFromGrade() > 0
                || goal.getCraftsAtCurrentGrade() > 0
                || goal.isExperimentalApplied()
                || goal.getCompletedUnits() > 0) {
            return true;
        }
        if (loadout == null || goal.getQuantity() <= 1 || !goalMatchesShip(goal, loadout.getShipId())) {
            return false;
        }
        EngineeringDatabase db = database != null ? database : EngineeringDatabase.getInstance();
        for (Double frac : unitDisplayFractionsFromLoadout(goal, loadout, db, 0)) {
            if (frac != null && frac > 1e-9) {
                return true;
            }
        }
        return false;
    }

    private static List<Double> unitDisplayFractionsFromLoadout(EngineeringGoal goal,
                                                                LoadoutEvent loadout,
                                                                EngineeringDatabase db,
                                                                int engineerRank) {
        List<Double> fracs = new ArrayList<>();
        if (goal == null || loadout == null) {
            return fracs;
        }
        EngineeringGoal unitTemplate = blankUnitProgress(goal);
        for (LoadoutEvent.Module module : loadout.getModules()) {
            if (module == null) {
                continue;
            }
            if (goal.hasTargetSlot()) {
                String modSlot = module.getSlot() != null ? module.getSlot().trim() : "";
                if (!goal.targetsSlot(modSlot)) {
                    continue;
                }
            }
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
            if (!goal.hasTargetSlot() && fittedExperimentalConflictsWithGoal(goal, engineering, db)) {
                continue;
            }
            if (isEngineeringCompleteForGoal(goal, engineering, db)) {
                fracs.add(1.0);
                continue;
            }
            EngineeringGoal snap = progressSnapshotFromEngineering(unitTemplate, engineering, db);
            fracs.add(EngineeringGradeProgress.unitCompletionFraction(snap, engineerRank));
        }
        return fracs;
    }

    private static int progressScore(EngineeringGoal goal) {
        if (goal == null) {
            return -1;
        }
        return goal.getFromGrade() * 1000
                + goal.getCraftsAtCurrentGrade() * 10
                + (goal.isExperimentalApplied() ? 1 : 0);
    }

    private static EngineeringGoal applyPartialLoadoutProgress(EngineeringGoal goal,
                                                                 LoadoutEvent.Engineering engineering,
                                                                 EngineeringDatabase db) {
        // Re-evaluate experimental from this module; do not keep a sticky session "applied" flag.
        EngineeringGoal snapshot = progressSnapshotFromEngineering(goal, engineering, db);
        EngineeringGoal updated = goal.withExperimentalApplied(snapshot.isExperimentalApplied());
        if (snapshot.getFromGrade() > updated.getFromGrade()
                || (snapshot.getFromGrade() == updated.getFromGrade()
                        && snapshot.getCraftsAtCurrentGrade() > updated.getCraftsAtCurrentGrade())) {
            updated = updated.withProgress(snapshot.getFromGrade(), snapshot.getCraftsAtCurrentGrade());
        }
        if (snapshot.isExperimentalApplied()) {
            updated = updated.withExperimentalApplied(true);
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

    /**
     * True when the fitted module already has an experimental that is not the one this goal wants.
     * Blank fitted experimental is not a conflict (grade-only progress / pending experimental).
     */
    private static boolean fittedExperimentalConflictsWithGoal(EngineeringGoal goal,
                                                               LoadoutEvent.Engineering engineering,
                                                               EngineeringDatabase db) {
        if (goal == null || engineering == null || db == null) {
            return false;
        }
        String expId = goal.getExperimentalId();
        if (expId == null || expId.isBlank()) {
            return false;
        }
        String effect = engineering.getExperimentalEffect();
        String localised = engineering.getExperimentalEffectLocalised();
        boolean fittedHasExp = (effect != null && !effect.isBlank())
                || (localised != null && !localised.isBlank());
        if (!fittedHasExp) {
            return false;
        }
        Optional<BlueprintGrade> experimental = db.findById(expId);
        if (experimental.isEmpty()) {
            return false;
        }
        return !experimentalEffectMatches("", effect, localised, experimental.get());
    }

    /** Same conflict rule for journal craft rows that report the module's current experimental. */
    private static boolean craftExperimentalConflictsWithGoal(EngineeringGoal goal,
                                                              EngineerCraftEvent craft,
                                                              EngineeringDatabase db) {
        if (goal == null || craft == null || db == null) {
            return false;
        }
        String expId = goal.getExperimentalId();
        if (expId == null || expId.isBlank()) {
            return false;
        }
        String effect = craft.getExperimentalEffect();
        String localised = craft.getExperimentalEffectLocalised();
        boolean craftHasExp = (effect != null && !effect.isBlank())
                || (localised != null && !localised.isBlank());
        if (!craftHasExp) {
            return false;
        }
        Optional<BlueprintGrade> experimental = db.findById(expId);
        if (experimental.isEmpty()) {
            return false;
        }
        return !experimentalEffectMatches(
                craft.getApplyExperimentalEffect(),
                effect,
                localised,
                experimental.get());
    }

    /**
     * @param replayHadEvidence true when journal/store crafts actually matched this goal, making the
     *        replayed unit/experimental state authoritative; saved session values only fill gaps when
     *        no craft history was found (e.g. journals rolled off disk)
     */
    private static EngineeringGoal mergeProgress(EngineeringGoal saved,
                                                 EngineeringGoal replayed,
                                                 boolean replayHadEvidence) {
        EngineeringGoal merged = replayed;
        if (saved.getFromGrade() > replayed.getFromGrade()) {
            merged = merged.withProgress(saved.getFromGrade(), saved.getCraftsAtCurrentGrade());
        } else if (saved.getFromGrade() == replayed.getFromGrade()
                && saved.getCraftsAtCurrentGrade() > replayed.getCraftsAtCurrentGrade()) {
            merged = merged.withProgress(saved.getFromGrade(), saved.getCraftsAtCurrentGrade());
        }
        if (replayHadEvidence) {
            // Replay saw this goal's crafts: trust it for experimental + completed units. Merging a
            // previously over-counted session here made bogus "Complete" states permanently sticky.
            return merged;
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
        if (!matchesGoalModuleBlueprint(goal, craft, db)) {
            return false;
        }
        if (goal.hasTargetSlot()) {
            String craftSlot = craft.getSlot() != null ? craft.getSlot().trim() : "";
            if (!craftSlot.isBlank() && !goal.targetsSlot(craftSlot)) {
                return false;
            }
        } else if (craftExperimentalConflictsWithGoal(goal, craft, db)) {
            // Unscoped sibling plans must not share crafts from a gun that already carries a
            // different experimental.
            return false;
        }
        return true;
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
        // Ingredient inference is a last resort for journals predating the experimental-effect
        // fields, and for ApplyExperimentalEffect crafts that only report Frontier effect codes
        // (e.g. special_armour_chunky) without a matchable Localised name. A plain grade roll that
        // names Level but not ApplyExperimentalEffect must NOT infer from ingredients — those
        // materials can overlap an experimental recipe and falsely mark it done.
        if (craft.getLevel() > 0 && craft.getApplyExperimentalEffect().isBlank()) {
            return false;
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

    /** True when the craft targets the goal's module type, regardless of which blueprint it rolled. */
    private static boolean matchesGoalModule(EngineeringGoal goal,
                                             EngineerCraftEvent craft,
                                             EngineeringDatabase db) {
        Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
                EngineeringJournalBlueprintResolver.resolve(
                        craft.getSlot(), craft.getModule(), craft.getBlueprintName(), db);
        return resolved.isPresent()
                && goal.getModuleType().equalsIgnoreCase(resolved.get().moduleType());
    }

    private static boolean experimentalEffectMatches(String applyExperimentalEffect,
                                                     String experimentalEffect,
                                                     String experimentalEffectLocalised,
                                                     BlueprintGrade experimental) {
        String normalizedName = EngineeringJournalBlueprintResolver.normalizeToken(experimental.getName());
        String normalizedId = EngineeringJournalBlueprintResolver.normalizeToken(experimental.getId());
        // Journal fields may be null (e.g. Loadout modules without an experimental effect); List.of rejects nulls.
        for (String candidate : new String[] {
                applyExperimentalEffect,
                experimentalEffect,
                experimentalEffectLocalised}) {
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
