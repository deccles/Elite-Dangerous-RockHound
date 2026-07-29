package org.dce.ed.engineering;

import java.time.Instant;

import org.dce.ed.logreader.event.EngineerCraftEvent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Elite often omits a fresh {@code Loadout} after {@code EngineerCraft} — both experimental-only
 * applies and blueprint grade rolls. Loadout UI / ship report read the last stored Loadout
 * snapshot, so this patches that JSON from craft fields ({@code Level}, {@code Modifiers},
 * {@code ExperimentalEffect}, etc.).
 */
public final class EngineeringLoadoutExperimentalPatch {

    private EngineeringLoadoutExperimentalPatch() {
    }

    /** Experimental-only apply/trade ({@code ApplyExperimentalEffect} set). */
    public static boolean isExperimentalApply(EngineerCraftEvent craft) {
        return craft != null
                && craft.getApplyExperimentalEffect() != null
                && !craft.getApplyExperimentalEffect().isBlank();
    }

    /** Blueprint grade roll ({@code Level} &gt; 0). May also carry an experimental already on the module. */
    public static boolean isGradeCraft(EngineerCraftEvent craft) {
        return craft != null && craft.getLevel() > 0;
    }

    /** Whether this craft carries enough data to refresh a stored Loadout module. */
    public static boolean shouldPatchLoadout(EngineerCraftEvent craft) {
        return isExperimentalApply(craft) || isGradeCraft(craft);
    }

    /**
     * Patches the matching module's {@code Engineering} block from a craft event.
     *
     * @return updated Loadout JSON, or {@code null} if nothing changed / could not patch
     */
    public static String patchLoadoutRawJson(String loadoutRawJson, EngineerCraftEvent craft) {
        if (loadoutRawJson == null || loadoutRawJson.isBlank() || craft == null || !shouldPatchLoadout(craft)) {
            return null;
        }
        if (isGradeCraft(craft)) {
            return patchGradeCraft(loadoutRawJson, craft);
        }
        String effect = !craft.getExperimentalEffect().isBlank()
                ? craft.getExperimentalEffect()
                : craft.getApplyExperimentalEffect();
        String localised = craft.getExperimentalEffectLocalised();
        return patchExperimentalOnly(
                loadoutRawJson,
                craft.getSlot(),
                craft.getModule(),
                effect,
                localised);
    }

    /**
     * @deprecated use {@link #patchLoadoutRawJson(String, EngineerCraftEvent)}; kept for call sites
     *             that patch experimental fields by value.
     */
@Deprecated
    public static String patchLoadoutRawJson(String loadoutRawJson,
                                            String slot,
                                            String moduleItem,
                                            String experimentalEffect,
                                            String experimentalEffectLocalised) {
        return patchExperimentalOnly(
                loadoutRawJson, slot, moduleItem, experimentalEffect, experimentalEffectLocalised);
    }

    private static String patchExperimentalOnly(String loadoutRawJson,
                                                String slot,
                                                String moduleItem,
                                                String experimentalEffect,
                                                String experimentalEffectLocalised) {
        if (loadoutRawJson == null || loadoutRawJson.isBlank()
                || slot == null || slot.isBlank()
                || experimentalEffect == null || experimentalEffect.isBlank()) {
            return null;
        }
        JsonObject root = parseRoot(loadoutRawJson);
        if (root == null) {
            return null;
        }
        JsonObject match = findModule(root, slot, moduleItem);
        if (match == null) {
            return null;
        }
        JsonObject engineering = ensureEngineering(match);
        String prevEffect = text(engineering, "ExperimentalEffect");
        String prevLocal = text(engineering, "ExperimentalEffect_Localised");
        String newLocal = experimentalEffectLocalised != null ? experimentalEffectLocalised.trim() : "";
        if (experimentalEffect.equals(prevEffect) && newLocal.equals(prevLocal)) {
            return null;
        }
        applyExperimentalFields(engineering, experimentalEffect, newLocal);
        return root.toString();
    }

    private static String patchGradeCraft(String loadoutRawJson, EngineerCraftEvent craft) {
        JsonObject root = parseRoot(loadoutRawJson);
        if (root == null) {
            return null;
        }
        JsonObject match = findModule(root, craft.getSlot(), craft.getModule());
        if (match == null) {
            return null;
        }
        JsonObject engineering = ensureEngineering(match);
        JsonObject craftRaw = craft.getRawJson();
        boolean changed = false;

        double oldModuleMass = modifierValue(engineering, "Mass");

        if (craft.getLevel() > 0 && (!engineering.has("Level")
                || engineering.get("Level").getAsInt() != craft.getLevel())) {
            engineering.addProperty("Level", craft.getLevel());
            changed = true;
        }
        if (!Double.isNaN(craft.getQuality())) {
            double prevQ = engineering.has("Quality") && !engineering.get("Quality").isJsonNull()
                    ? engineering.get("Quality").getAsDouble()
                    : Double.NaN;
            if (Double.isNaN(prevQ) || Math.abs(prevQ - craft.getQuality()) > 1e-9) {
                engineering.addProperty("Quality", craft.getQuality());
                changed = true;
            }
        }
        if (!craft.getBlueprintName().isBlank()
                && !craft.getBlueprintName().equals(text(engineering, "BlueprintName"))) {
            engineering.addProperty("BlueprintName", craft.getBlueprintName());
            changed = true;
        }
        if (craft.getBlueprintId() > 0
                && (!engineering.has("BlueprintID")
                        || engineering.get("BlueprintID").getAsLong() != craft.getBlueprintId())) {
            engineering.addProperty("BlueprintID", craft.getBlueprintId());
            changed = true;
        }
        if (!craft.getEngineer().isBlank()
                && !craft.getEngineer().equals(text(engineering, "Engineer"))) {
            engineering.addProperty("Engineer", craft.getEngineer());
            changed = true;
        }
        if (craft.getEngineerId() > 0
                && (!engineering.has("EngineerID")
                        || engineering.get("EngineerID").getAsLong() != craft.getEngineerId())) {
            engineering.addProperty("EngineerID", craft.getEngineerId());
            changed = true;
        }

        if (craftRaw != null && craftRaw.has("Modifiers") && craftRaw.get("Modifiers").isJsonArray()) {
            JsonArray newMods = craftRaw.getAsJsonArray("Modifiers").deepCopy();
            if (!modifiersEqual(engineering.has("Modifiers") && engineering.get("Modifiers").isJsonArray()
                    ? engineering.getAsJsonArray("Modifiers")
                    : null, newMods)) {
                engineering.add("Modifiers", newMods);
                changed = true;
            }
        }

        // Grade rolls often repeat the fitted experimental; keep Loadout in sync when present.
        String effect = !craft.getExperimentalEffect().isBlank()
                ? craft.getExperimentalEffect()
                : "";
        if (!effect.isBlank()) {
            String local = craft.getExperimentalEffectLocalised();
            String prevEffect = text(engineering, "ExperimentalEffect");
            String prevLocal = text(engineering, "ExperimentalEffect_Localised");
            String newLocal = local != null ? local.trim() : "";
            if (!effect.equals(prevEffect) || !newLocal.equals(prevLocal)) {
                applyExperimentalFields(engineering, effect, newLocal);
                changed = true;
            }
        }

        double newModuleMass = modifierValue(engineering, "Mass");
        if (oldModuleMass > 0 && newModuleMass > 0 && Math.abs(newModuleMass - oldModuleMass) > 1e-6
                && root.has("UnladenMass") && !root.get("UnladenMass").isJsonNull()) {
            double unladen = root.get("UnladenMass").getAsDouble();
            root.addProperty("UnladenMass", Math.max(1.0, unladen + (newModuleMass - oldModuleMass)));
            changed = true;
        }

        // Stale after FSD grade crafts; omit rather than keep a wrong ceiling in Loadout UI.
        if (changed && isFsdModule(craft) && root.has("MaxJumpRange")) {
            root.remove("MaxJumpRange");
        }

        return changed ? root.toString() : null;
    }

    private static boolean isFsdModule(EngineerCraftEvent craft) {
        if (craft == null) {
            return false;
        }
        if ("FrameShiftDrive".equalsIgnoreCase(craft.getSlot())) {
            return true;
        }
        String module = craft.getModule() != null ? craft.getModule().toLowerCase() : "";
        return module.startsWith("int_hyperdrive");
    }

    private static void applyExperimentalFields(JsonObject engineering, String effect, String newLocal) {
        engineering.addProperty("ExperimentalEffect", effect);
        if (newLocal != null && !newLocal.isBlank()) {
            engineering.addProperty("ExperimentalEffect_Localised", newLocal);
        } else if (engineering.has("ExperimentalEffect_Localised")) {
            engineering.remove("ExperimentalEffect_Localised");
        }
    }

    /**
     * Whether this craft should overlay a stored loadout: craft at/after the loadout timestamp on
     * the same hull.
     */
    public static boolean craftShouldOverlayLoadout(Instant craftTs,
                                                    Instant loadoutTs,
                                                    long craftShipId,
                                                    long loadoutShipId) {
        if (craftShipId < 0 || craftShipId != loadoutShipId || craftTs == null) {
            return false;
        }
        if (loadoutTs == null) {
            return true;
        }
        return !craftTs.isBefore(loadoutTs);
    }

    private static JsonObject parseRoot(String loadoutRawJson) {
        try {
            return JsonParser.parseString(loadoutRawJson).getAsJsonObject();
        } catch (Exception ex) {
            return null;
        }
    }

    private static JsonObject findModule(JsonObject root, String slot, String moduleItem) {
        if (root == null || !root.has("Modules") || !root.get("Modules").isJsonArray()
                || slot == null || slot.isBlank()) {
            return null;
        }
        JsonArray modules = root.getAsJsonArray("Modules");
        for (JsonElement el : modules) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject mod = el.getAsJsonObject();
            if (!slot.equalsIgnoreCase(text(mod, "Slot"))) {
                continue;
            }
            String item = text(mod, "Item");
            if (moduleItem != null && !moduleItem.isBlank()
                    && !item.isBlank()
                    && !moduleItem.equalsIgnoreCase(item)) {
                continue;
            }
            return mod;
        }
        return null;
    }

    private static JsonObject ensureEngineering(JsonObject module) {
        if (module.has("Engineering") && module.get("Engineering").isJsonObject()) {
            return module.getAsJsonObject("Engineering");
        }
        JsonObject engineering = new JsonObject();
        module.add("Engineering", engineering);
        return engineering;
    }

    private static double modifierValue(JsonObject engineering, String label) {
        if (engineering == null || !engineering.has("Modifiers") || !engineering.get("Modifiers").isJsonArray()) {
            return Double.NaN;
        }
        for (JsonElement el : engineering.getAsJsonArray("Modifiers")) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject m = el.getAsJsonObject();
            if (label.equalsIgnoreCase(text(m, "Label")) && m.has("Value") && !m.get("Value").isJsonNull()) {
                try {
                    return m.get("Value").getAsDouble();
                } catch (Exception ignored) {
                    return Double.NaN;
                }
            }
        }
        return Double.NaN;
    }

    private static boolean modifiersEqual(JsonArray a, JsonArray b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }
        // Order-sensitive compare is fine: journal emits a stable modifier list.
        return a.toString().equals(b.toString());
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
