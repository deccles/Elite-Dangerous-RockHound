package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Parses SLEF target loadouts and previews their merge into Engineering goals. */
public final class EngineeringRecommendationImport {

    private EngineeringRecommendationImport() {
    }

    public record Plan(long shipId, String shipType, String shipName,
                       List<EngineeringGoal> goals, List<String> errors) {
        public Plan {
            shipType = shipType != null ? shipType : "";
            shipName = shipName != null ? shipName : "";
            goals = goals == null ? List.of() : List.copyOf(goals);
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    public enum Action {
        ADD, UPDATE, SKIP
    }

    public record Change(Action action, EngineeringGoal existing, EngineeringGoal recommendation) {
    }

    public static final class MergePreview {
        private final List<Change> changes;

        private MergePreview(List<Change> changes) {
            this.changes = List.copyOf(changes);
        }

        public List<Change> changes() {
            return changes;
        }

        public int addCount() {
            return count(Action.ADD);
        }

        public int updateCount() {
            return count(Action.UPDATE);
        }

        public int skipCount() {
            return count(Action.SKIP);
        }

        private int count(Action action) {
            int count = 0;
            for (Change change : changes) {
                if (change.action() == action) {
                    count++;
                }
            }
            return count;
        }

        public void applyTo(List<EngineeringGoal> goals) {
            if (goals == null) {
                return;
            }
            for (Change change : changes) {
                if (change.action() == Action.ADD) {
                    goals.add(change.recommendation());
                } else if (change.action() == Action.UPDATE) {
                    int index = indexOfIdentity(goals, change.existing());
                    if (index >= 0) {
                        goals.set(index, mergedGoal(change.existing(), change.recommendation()));
                    }
                }
            }
        }
    }

    public static Plan parse(String json, EngineeringDatabase database) {
        List<String> errors = new ArrayList<>();
        List<EngineeringGoal> goals = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return new Plan(-1, "", "", goals, List.of("The recommendation is empty."));
        }
        if (database == null) {
            return new Plan(-1, "", "", goals, List.of("Engineering data is unavailable."));
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(json.trim()).getAsJsonObject();
        } catch (Exception ex) {
            return new Plan(-1, "", "", goals, List.of("The recommendation is not valid JSON."));
        }
        JsonObject data = object(root, "data");
        if (data == null) {
            data = root;
        }
        if (!"Loadout".equalsIgnoreCase(text(data, "event"))) {
            errors.add("SLEF data.event must be Loadout.");
        }
        String shipType = text(data, "Ship");
        long shipId = longValue(data, "ShipID", -1);
        String shipName = text(data, "ShipName");
        if (shipType.isBlank()) {
            errors.add("SLEF data.Ship is required.");
        }
        if (shipId < 0) {
            errors.add("SLEF data.ShipID is required for RockHound recommendations.");
        }
        JsonArray modules = array(data, "Modules");
        if (modules == null) {
            errors.add("SLEF data.Modules is required.");
            return new Plan(shipId, shipType, shipName, goals, errors);
        }

        String shipLabel = !shipName.isBlank() ? shipName : shipType;
        for (JsonElement element : modules) {
            if (element == null || !element.isJsonObject()) {
                errors.add("Every Modules entry must be an object.");
                continue;
            }
            JsonObject module = element.getAsJsonObject();
            JsonObject engineering = object(module, "Engineering");
            if (engineering == null) {
                continue;
            }
            String slot = text(module, "Slot");
            String item = text(module, "Item");
            String journalBlueprint = text(engineering, "BlueprintName");
            int level = intValue(engineering, "Level", 0);
            if (slot.isBlank() || item.isBlank() || journalBlueprint.isBlank() || level < 1) {
                errors.add("An engineered module requires Slot, Item, BlueprintName, and Level: " + slot);
                continue;
            }
            Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
                    EngineeringJournalBlueprintResolver.resolve(slot, item, journalBlueprint, database);
            if (resolved.isEmpty()) {
                errors.add("Unknown blueprint " + journalBlueprint + " for slot " + slot + ".");
                continue;
            }
            String moduleType = resolved.get().moduleType();
            String blueprintName = resolved.get().blueprintName();
            Optional<BlueprintGrade> target = database.gradesFor(moduleType, blueprintName).stream()
                    .filter(bp -> !bp.isExperimental() && bp.getGrade() == level)
                    .findFirst();
            if (target.isEmpty()) {
                errors.add("Unsupported grade G" + level + " for " + journalBlueprint + " in slot " + slot + ".");
                continue;
            }

            String experimentalId = "";
            String experimental = text(engineering, "ExperimentalEffect");
            String experimentalLocalised = text(engineering, "ExperimentalEffect_Localised");
            if (!experimental.isBlank() || !experimentalLocalised.isBlank()) {
                Optional<BlueprintGrade> exp = resolveExperimental(
                        database, moduleType, blueprintName, experimental, experimentalLocalised);
                if (exp.isEmpty()) {
                    String label = !experimentalLocalised.isBlank() ? experimentalLocalised : experimental;
                    errors.add("Unknown experimental effect " + label + " for slot " + slot + ".");
                    continue;
                }
                experimentalId = exp.get().getId();
            }
            goals.add(new EngineeringGoal(
                    target.get().getId(), moduleType, blueprintName, 0, 0, level, experimentalId,
                    GoalPriority.MEDIUM, false, 1, 0, shipId, shipLabel, true, slot));
        }
        return new Plan(shipId, shipType, shipName, goals, errors);
    }

    public static MergePreview previewMerge(List<EngineeringGoal> existing,
                                            List<EngineeringGoal> recommendations) {
        List<Change> changes = new ArrayList<>();
        List<EngineeringGoal> current = existing != null ? existing : List.of();
        if (recommendations == null) {
            return new MergePreview(changes);
        }
        for (EngineeringGoal recommendation : recommendations) {
            EngineeringGoal match = findByShipAndSlot(current, recommendation);
            if (match == null) {
                changes.add(new Change(Action.ADD, null, recommendation));
            } else if (sameTarget(match, recommendation)) {
                changes.add(new Change(Action.SKIP, match, recommendation));
            } else {
                changes.add(new Change(Action.UPDATE, match, recommendation));
            }
        }
        return new MergePreview(changes);
    }

    private static EngineeringGoal findByShipAndSlot(List<EngineeringGoal> goals, EngineeringGoal wanted) {
        for (EngineeringGoal goal : goals) {
            if (goal != null && goal.getShipId() == wanted.getShipId()
                    && goal.getTargetSlot().equalsIgnoreCase(wanted.getTargetSlot())) {
                return goal;
            }
        }
        return null;
    }

    private static boolean sameTarget(EngineeringGoal a, EngineeringGoal b) {
        return a.getModuleType().equalsIgnoreCase(b.getModuleType())
                && a.getBlueprintName().equalsIgnoreCase(b.getBlueprintName())
                && a.getTargetGrade() == b.getTargetGrade()
                && a.getExperimentalId().equalsIgnoreCase(b.getExperimentalId());
    }

    private static EngineeringGoal mergedGoal(EngineeringGoal existing, EngineeringGoal recommendation) {
        if (existing.getModuleType().equalsIgnoreCase(recommendation.getModuleType())
                && existing.getBlueprintName().equalsIgnoreCase(recommendation.getBlueprintName())) {
            EngineeringGoal progressed = existing.withUserSettings(
                    recommendation.getTargetGrade(), recommendation.getExperimentalId(), 1);
            return new EngineeringGoal(
                    recommendation.getBlueprintId(),
                    progressed.getModuleType(),
                    progressed.getBlueprintName(),
                    progressed.getFromGrade(),
                    progressed.getCraftsAtCurrentGrade(),
                    progressed.getTargetGrade(),
                    progressed.getExperimentalId(),
                    progressed.getPriority(),
                    progressed.isExperimentalApplied(),
                    progressed.getQuantity(),
                    progressed.getCompletedUnits(),
                    progressed.getShipId(),
                    progressed.getShipLabel(),
                    progressed.isEnabled(),
                    progressed.getTargetSlot());
        }
        return recommendation.withPriority(existing.getPriority()).withEnabled(existing.isEnabled());
    }

    private static int indexOfIdentity(List<EngineeringGoal> goals, EngineeringGoal wanted) {
        for (int i = 0; i < goals.size(); i++) {
            if (goals.get(i) == wanted) {
                return i;
            }
        }
        return -1;
    }

    private static Optional<BlueprintGrade> resolveExperimental(
            EngineeringDatabase database, String moduleType, String blueprintName,
            String canonical, String localised) {
        String canonicalNorm = normalize(canonical);
        String localisedNorm = normalize(localised);
        for (BlueprintGrade exp : database.experimentalsFor(moduleType, blueprintName)) {
            String name = normalize(exp.getName());
            String id = normalize(exp.getId());
            if ((!localisedNorm.isBlank() && (name.equals(localisedNorm) || id.equals(localisedNorm)))
                    || (!canonicalNorm.isBlank() && (name.equals(canonicalNorm) || id.equals(canonicalNorm)))) {
                return Optional.of(exp);
            }
        }
        return Optional.empty();
    }

    private static String normalize(String value) {
        return EngineeringJournalBlueprintResolver.normalizeToken(value).toLowerCase(Locale.ROOT);
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key) : null;
    }

    private static JsonArray array(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonArray()
                ? parent.getAsJsonArray(key) : null;
    }

    private static String text(JsonObject object, String key) {
        try {
            return object != null && object.has(key) && !object.get(key).isJsonNull()
                    ? object.get(key).getAsString().trim() : "";
        } catch (Exception ex) {
            return "";
        }
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        try {
            return object.get(key).getAsInt();
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        try {
            return object.get(key).getAsLong();
        } catch (Exception ex) {
            return fallback;
        }
    }
}
