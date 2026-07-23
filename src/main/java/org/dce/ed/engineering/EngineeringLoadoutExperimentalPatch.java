package org.dce.ed.engineering;

import java.time.Instant;

import org.dce.ed.logreader.event.EngineerCraftEvent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Elite does not emit a fresh {@code Loadout} when you only apply/trade an experimental effect.
 * Loadout UI and ship report read the last stored Loadout snapshot, so without patching that JSON
 * from {@code EngineerCraft} ({@code ApplyExperimentalEffect}), fitted experimentals stay stale.
 */
public final class EngineeringLoadoutExperimentalPatch {

    private EngineeringLoadoutExperimentalPatch() {
    }

    public static boolean isExperimentalApply(EngineerCraftEvent craft) {
        return craft != null
                && craft.getApplyExperimentalEffect() != null
                && !craft.getApplyExperimentalEffect().isBlank();
    }

    /**
     * Patches the matching module's {@code Engineering.ExperimentalEffect*} fields.
     *
     * @return updated Loadout JSON, or {@code null} if nothing changed / could not patch
     */
    public static String patchLoadoutRawJson(String loadoutRawJson, EngineerCraftEvent craft) {
        if (loadoutRawJson == null || loadoutRawJson.isBlank() || !isExperimentalApply(craft)) {
            return null;
        }
        String effect = !craft.getExperimentalEffect().isBlank()
                ? craft.getExperimentalEffect()
                : craft.getApplyExperimentalEffect();
        String localised = craft.getExperimentalEffectLocalised();
        return patchLoadoutRawJson(
                loadoutRawJson,
                craft.getSlot(),
                craft.getModule(),
                effect,
                localised);
    }

    public static String patchLoadoutRawJson(String loadoutRawJson,
                                            String slot,
                                            String moduleItem,
                                            String experimentalEffect,
                                            String experimentalEffectLocalised) {
        if (loadoutRawJson == null || loadoutRawJson.isBlank()
                || slot == null || slot.isBlank()
                || experimentalEffect == null || experimentalEffect.isBlank()) {
            return null;
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(loadoutRawJson).getAsJsonObject();
        } catch (Exception ex) {
            return null;
        }
        if (!root.has("Modules") || !root.get("Modules").isJsonArray()) {
            return null;
        }
        JsonArray modules = root.getAsJsonArray("Modules");
        JsonObject match = null;
        for (JsonElement el : modules) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject mod = el.getAsJsonObject();
            String modSlot = text(mod, "Slot");
            if (!slot.equalsIgnoreCase(modSlot)) {
                continue;
            }
            String item = text(mod, "Item");
            if (moduleItem != null && !moduleItem.isBlank()
                    && !item.isBlank()
                    && !moduleItem.equalsIgnoreCase(item)) {
                continue;
            }
            match = mod;
            break;
        }
        if (match == null) {
            return null;
        }
        JsonObject engineering;
        if (match.has("Engineering") && match.get("Engineering").isJsonObject()) {
            engineering = match.getAsJsonObject("Engineering");
        } else {
            engineering = new JsonObject();
            match.add("Engineering", engineering);
        }
        String prevEffect = text(engineering, "ExperimentalEffect");
        String prevLocal = text(engineering, "ExperimentalEffect_Localised");
        String newLocal = experimentalEffectLocalised != null ? experimentalEffectLocalised.trim() : "";
        if (experimentalEffect.equals(prevEffect) && newLocal.equals(prevLocal)) {
            return null;
        }
        engineering.addProperty("ExperimentalEffect", experimentalEffect);
        if (!newLocal.isBlank()) {
            engineering.addProperty("ExperimentalEffect_Localised", newLocal);
        } else if (engineering.has("ExperimentalEffect_Localised")) {
            engineering.remove("ExperimentalEffect_Localised");
        }
        return root.toString();
    }

    /**
     * Whether this craft should overlay a stored loadout: experimental apply at/after the loadout
     * timestamp on the same hull.
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
