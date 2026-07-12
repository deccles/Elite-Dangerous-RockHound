package org.dce.ed.engineering;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
        }
        return Optional.empty();
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
