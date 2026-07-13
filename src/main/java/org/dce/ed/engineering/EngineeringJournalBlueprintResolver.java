package org.dce.ed.engineering;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Maps journal {@code EngineerCraft} / loadout {@code BlueprintName} strings to catalog blueprints.
 */
public final class EngineeringJournalBlueprintResolver {

    private static final Map<String, ResolvedBlueprint> BY_JOURNAL_NAME = loadJournalMap();

    private EngineeringJournalBlueprintResolver() {
    }

    public record ResolvedBlueprint(String moduleType, String blueprintName) {
    }

    public static Optional<ResolvedBlueprint> resolve(String slot,
                                                        String journalBlueprintName,
                                                        EngineeringDatabase database) {
        if (journalBlueprintName != null && !journalBlueprintName.isBlank()) {
            ResolvedBlueprint mapped = BY_JOURNAL_NAME.get(journalBlueprintName);
            if (mapped != null) {
                return Optional.of(mapped);
            }
            if (database != null) {
                Optional<ResolvedBlueprint> derived = deriveFromJournalName(slot, journalBlueprintName, database);
                if (derived.isPresent()) {
                    return derived;
                }
            }
        }
        return Optional.empty();
    }

    static Optional<ResolvedBlueprint> deriveFromJournalName(String slot,
                                                               String journalBlueprintName,
                                                               EngineeringDatabase database) {
        int underscore = journalBlueprintName.indexOf('_');
        if (underscore <= 0 || underscore >= journalBlueprintName.length() - 1) {
            return Optional.empty();
        }
        String journalSlot = journalBlueprintName.substring(0, underscore);
        String suffix = journalBlueprintName.substring(underscore + 1);
        String moduleType = slotToModuleType(journalSlot);
        if (moduleType.isBlank()) {
            moduleType = slotToModuleType(slot);
        }
        if (moduleType.isBlank()) {
            return Optional.empty();
        }

        String normSuffix = normalizeToken(suffix);
        if (normSuffix.isBlank()) {
            return Optional.empty();
        }

        ResolvedBlueprint best = null;
        int bestScore = 0;
        for (String blueprintName : blueprintNamesForModule(database, moduleType)) {
            int score = matchScore(normSuffix, blueprintName);
            if (score > bestScore) {
                bestScore = score;
                best = new ResolvedBlueprint(moduleType, blueprintName);
            }
        }
        return bestScore > 0 ? Optional.of(best) : Optional.empty();
    }

    private static Set<String> blueprintNamesForModule(EngineeringDatabase database, String moduleType) {
        Set<String> names = new HashSet<>();
        for (BlueprintGrade bp : database.getAllBlueprints()) {
            if (bp.isExperimental()) {
                continue;
            }
            if (bp.getModuleType().equalsIgnoreCase(moduleType)) {
                names.add(bp.getName());
            }
        }
        return names;
    }

    private static int matchScore(String normSuffix, String blueprintName) {
        String normName = normalizeToken(blueprintName);
        if (normName.startsWith(normSuffix)) {
            return 100;
        }
        if (normSuffix.length() >= 4 && normName.contains(normSuffix)) {
            return 80;
        }
        // Elite journal truncates some blueprint suffixes (e.g. Thermal → Thermic).
        if (normSuffix.startsWith("thermic") && normName.contains("thermal")) {
            return 75;
        }
        return 0;
    }

    static String normalizeToken(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static String slotToModuleType(String slot) {
        if (slot == null || slot.isBlank()) {
            return "";
        }
        String base = slot;
        int underscore = base.indexOf('_');
        if (underscore > 0) {
            base = base.substring(0, underscore);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(base.charAt(i - 1))) {
                out.append(' ');
            }
            out.append(c);
        }
        return out.toString().trim();
    }

    private static Map<String, ResolvedBlueprint> loadJournalMap() {
        Map<String, ResolvedBlueprint> map = new HashMap<>();
        try (InputStream in = EngineeringJournalBlueprintResolver.class
                .getResourceAsStream("/engineering/journal_blueprint_map.json")) {
            if (in == null) {
                return map;
            }
            JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                return map;
            }
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                if (!e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getValue().getAsJsonObject();
                String moduleType = text(o, "moduleType");
                String blueprintName = text(o, "blueprintName");
                if (!moduleType.isBlank() && !blueprintName.isBlank()) {
                    map.put(e.getKey(), new ResolvedBlueprint(moduleType, blueprintName));
                }
            }
        } catch (Exception ignored) {
            // bundled map optional
        }
        return map;
    }

    private static String text(JsonObject o, String field) {
        if (o == null || !o.has(field) || o.get(field).isJsonNull()) {
            return "";
        }
        return o.get(field).getAsString();
    }
}
