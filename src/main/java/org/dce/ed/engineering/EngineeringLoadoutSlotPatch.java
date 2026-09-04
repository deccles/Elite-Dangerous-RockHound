package org.dce.ed.engineering;

import java.time.Instant;
import java.util.Locale;

import org.dce.ed.logreader.event.ModuleRetrieveEvent;
import org.dce.ed.logreader.event.ModuleStoreEvent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Elite does not write {@code Loadout} on each retrieve. It writes when leaving stored modules /
 * Outfitting, switching ships, or loading a session.
 * <p>
 * A retrieve that names {@code Level} / {@code EngineerModifications} is applied as engineered.
 * A retrieve that omits those fields is applied as stock (G0) until the next real {@code Loadout}
 * confirms the slot — the wait banner stays up for that case.
 */
public final class EngineeringLoadoutSlotPatch {

    private EngineeringLoadoutSlotPatch() {
    }

    /**
     * Journal item symbols are often {@code $int_lifesupport_size3_class2_name;}; Loadout
     * {@code Item} is {@code int_lifesupport_size3_class2}.
     */
    public static String toLoadoutItemId(String journalItem) {
        if (journalItem == null || journalItem.isBlank()) {
            return "";
        }
        String s = journalItem.trim();
        if (s.startsWith("$")) {
            s = s.substring(1);
        }
        if (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.toLowerCase(Locale.ROOT).endsWith("_name")) {
            s = s.substring(0, s.length() - 5);
        }
        return s;
    }

    public static String patchRetrieve(String loadoutRawJson, ModuleRetrieveEvent event) {
        if (event == null) {
            return null;
        }
        String item = toLoadoutItemId(event.getRetrievedItem());
        if (item.isBlank()) {
            return null;
        }
        return applyFittedSlot(
                loadoutRawJson,
                event.getTimestamp(),
                event.getShipId(),
                event.getSlot(),
                item,
                event.getEngineerModifications(),
                event.getLevel(),
                event.getQuality());
    }

    public static String patchStore(String loadoutRawJson, ModuleStoreEvent event) {
        if (event == null) {
            return null;
        }
        String replacement = toLoadoutItemId(event.getReplacementItem());
        if (replacement.isBlank()) {
            return removeSlot(loadoutRawJson, event.getTimestamp(), event.getShipId(), event.getSlot());
        }
        return applyFittedSlot(
                loadoutRawJson,
                event.getTimestamp(),
                event.getShipId(),
                event.getSlot(),
                replacement,
                "",
                0,
                Double.NaN);
    }

    private static String applyFittedSlot(String loadoutRawJson,
                                          Instant eventTs,
                                          long eventShipId,
                                          String slot,
                                          String itemId,
                                          String blueprintName,
                                          int level,
                                          double quality) {
        if (loadoutRawJson == null || loadoutRawJson.isBlank()
                || slot == null || slot.isBlank()
                || itemId == null || itemId.isBlank()) {
            return null;
        }
        JsonObject root = parseRoot(loadoutRawJson);
        if (root == null || !shipMatches(root, eventShipId)) {
            return null;
        }
        JsonArray modules = modulesArray(root);
        if (modules == null) {
            return null;
        }
        JsonObject match = findModuleBySlot(modules, slot);
        if (match == null) {
            match = new JsonObject();
            match.addProperty("Slot", slot);
            match.addProperty("On", true);
            match.addProperty("Priority", 0);
            modules.add(match);
        }
        boolean changed = false;
        String prevItem = text(match, "Item");
        if (!itemId.equalsIgnoreCase(prevItem)) {
            match.addProperty("Item", itemId);
            changed = true;
        }
        if (applyEngineering(match, blueprintName, level, quality)) {
            changed = true;
        }
        if (!changed) {
            return null;
        }
        stampTimestamp(root, eventTs);
        return root.toString();
    }

    private static String removeSlot(String loadoutRawJson,
                                     Instant eventTs,
                                     long eventShipId,
                                     String slot) {
        if (loadoutRawJson == null || loadoutRawJson.isBlank()
                || slot == null || slot.isBlank()) {
            return null;
        }
        JsonObject root = parseRoot(loadoutRawJson);
        if (root == null || !shipMatches(root, eventShipId)) {
            return null;
        }
        JsonArray modules = modulesArray(root);
        if (modules == null) {
            return null;
        }
        for (int i = 0; i < modules.size(); i++) {
            JsonElement el = modules.get(i);
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            if (slot.equalsIgnoreCase(text(el.getAsJsonObject(), "Slot"))) {
                modules.remove(i);
                stampTimestamp(root, eventTs);
                return root.toString();
            }
        }
        return null;
    }

    /**
     * @return true when the Engineering block was added, removed, or rewritten
     */
    private static boolean applyEngineering(JsonObject module,
                                            String blueprintName,
                                            int level,
                                            double quality) {
        boolean engineered = (blueprintName != null && !blueprintName.isBlank()) || level > 0;
        if (!engineered) {
            if (module.has("Engineering")) {
                module.remove("Engineering");
                return true;
            }
            return false;
        }
        double q = Double.isNaN(quality) ? 1.0 : quality;
        String blueprint = blueprintName != null ? blueprintName : "";
        if (module.has("Engineering") && module.get("Engineering").isJsonObject()) {
            JsonObject existing = module.getAsJsonObject("Engineering");
            boolean sameBlueprint = blueprint.equals(text(existing, "BlueprintName"));
            int prevLevel = existing.has("Level") && !existing.get("Level").isJsonNull()
                    ? existing.get("Level").getAsInt()
                    : 0;
            double prevQ = existing.has("Quality") && !existing.get("Quality").isJsonNull()
                    ? existing.get("Quality").getAsDouble()
                    : Double.NaN;
            boolean sameQuality = !Double.isNaN(prevQ) && Math.abs(prevQ - q) < 1e-9;
            boolean hadStaleDetail = existing.has("ExperimentalEffect")
                    || existing.has("Modifiers")
                    || existing.has("ExperimentalEffect_Localised");
            if (sameBlueprint && prevLevel == level && sameQuality && !hadStaleDetail) {
                return false;
            }
        }
        JsonObject engineering = new JsonObject();
        if (!blueprint.isBlank()) {
            engineering.addProperty("BlueprintName", blueprint);
        }
        engineering.addProperty("Level", level);
        engineering.addProperty("Quality", q);
        module.add("Engineering", engineering);
        return true;
    }

    private static boolean shipMatches(JsonObject root, long eventShipId) {
        if (eventShipId < 0 || !root.has("ShipID") || root.get("ShipID").isJsonNull()) {
            return true;
        }
        try {
            return root.get("ShipID").getAsLong() == eventShipId;
        } catch (Exception ex) {
            return true;
        }
    }

    private static JsonArray modulesArray(JsonObject root) {
        if (!root.has("Modules") || !root.get("Modules").isJsonArray()) {
            return null;
        }
        return root.getAsJsonArray("Modules");
    }

    private static JsonObject findModuleBySlot(JsonArray modules, String slot) {
        for (JsonElement el : modules) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject mod = el.getAsJsonObject();
            if (slot.equalsIgnoreCase(text(mod, "Slot"))) {
                return mod;
            }
        }
        return null;
    }

    private static void stampTimestamp(JsonObject root, Instant eventTs) {
        if (eventTs != null) {
            root.addProperty("timestamp", eventTs.toString());
        }
    }

    private static JsonObject parseRoot(String loadoutRawJson) {
        try {
            return JsonParser.parseString(loadoutRawJson).getAsJsonObject();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String text(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception ex) {
            return "";
        }
    }
}
