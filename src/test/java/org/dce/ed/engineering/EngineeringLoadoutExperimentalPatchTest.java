package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class EngineeringLoadoutExperimentalPatchTest {

    private static final String LOADOUT =
            "{\"timestamp\":\"2026-07-23T04:16:12Z\",\"event\":\"Loadout\",\"Ship\":\"anaconda\",\"ShipID\":7,"
                    + "\"ShipName\":\"Exception Handler\",\"Modules\":["
                    + "{\"Slot\":\"HugeHardpoint1\",\"Item\":\"hpt_multicannon_gimbal_huge\",\"On\":true,"
                    + "\"Engineering\":{\"BlueprintName\":\"Weapon_Overcharged\",\"Level\":5,\"Quality\":1.0,"
                    + "\"ExperimentalEffect\":\"special_corrosive_shell\","
                    + "\"ExperimentalEffect_Localised\":\"Corrosive Shell\"}},"
                    + "{\"Slot\":\"MediumHardpoint1\",\"Item\":\"hpt_multicannon_gimbal_medium\",\"On\":true,"
                    + "\"Engineering\":{\"BlueprintName\":\"Weapon_Overcharged\",\"Level\":5,\"Quality\":1.0,"
                    + "\"ExperimentalEffect\":\"special_corrosive_shell\","
                    + "\"ExperimentalEffect_Localised\":\"Corrosive Shell\"}}]}";

    private static final String CRAFT =
            "{\"timestamp\":\"2026-07-23T04:58:00Z\",\"event\":\"EngineerCraft\","
                    + "\"Slot\":\"HugeHardpoint1\",\"Module\":\"hpt_multicannon_gimbal_huge\","
                    + "\"ApplyExperimentalEffect\":\"special_auto_loader\","
                    + "\"ExperimentalEffect\":\"special_auto_loader\","
                    + "\"ExperimentalEffect_Localised\":\"Auto Loader\","
                    + "\"BlueprintName\":\"Weapon_Overcharged\",\"Level\":5,\"Quality\":1.0,"
                    + "\"Ingredients\":[],\"Engineer\":\"Tod\",\"EngineerID\":1,\"BlueprintID\":1}";

    @Test
    void patch_updatesMatchingSlotExperimental() {
        EngineerCraftEvent craft = (EngineerCraftEvent) new EliteLogParser().parseRecord(CRAFT);
        String patched = EngineeringLoadoutExperimentalPatch.patchLoadoutRawJson(LOADOUT, craft);
        assertNotNull(patched);

        LoadoutEvent loadout = (LoadoutEvent) new EliteLogParser().parseRecord(patched);
        LoadoutEvent.Module huge = loadout.getModules().stream()
                .filter(m -> "HugeHardpoint1".equals(m.getSlot()))
                .findFirst()
                .orElseThrow();
        assertEquals("special_auto_loader", huge.getEngineering().getExperimentalEffect());
        assertEquals("Auto Loader", huge.getEngineering().getExperimentalEffectLocalised());

        LoadoutEvent.Module mid = loadout.getModules().stream()
                .filter(m -> "MediumHardpoint1".equals(m.getSlot()))
                .findFirst()
                .orElseThrow();
        assertEquals("special_corrosive_shell", mid.getEngineering().getExperimentalEffect());
    }

    @Test
    void patch_noopWhenAlreadyApplied() {
        EngineerCraftEvent craft = (EngineerCraftEvent) new EliteLogParser().parseRecord(CRAFT);
        String once = EngineeringLoadoutExperimentalPatch.patchLoadoutRawJson(LOADOUT, craft);
        assertNotNull(once);
        assertNull(EngineeringLoadoutExperimentalPatch.patchLoadoutRawJson(once, craft));
    }

    @Test
    void patch_gradeRollUpdatesEngineeringWithoutApplyExperimental() {
        String gradeRoll =
                "{\"timestamp\":\"2026-07-23T04:58:00Z\",\"event\":\"EngineerCraft\","
                        + "\"Slot\":\"HugeHardpoint1\",\"Module\":\"hpt_multicannon_gimbal_huge\","
                        + "\"ExperimentalEffect\":\"special_corrosive_shell\","
                        + "\"ExperimentalEffect_Localised\":\"Corrosive Shell\","
                        + "\"BlueprintName\":\"Weapon_Overcharged\",\"Level\":5,\"Quality\":1.0,"
                        + "\"Ingredients\":[],\"Engineer\":\"Tod\",\"EngineerID\":1,\"BlueprintID\":1}";
        EngineerCraftEvent craft = (EngineerCraftEvent) new EliteLogParser().parseRecord(gradeRoll);
        assertFalse(EngineeringLoadoutExperimentalPatch.isExperimentalApply(craft));
        assertTrue(EngineeringLoadoutExperimentalPatch.isGradeCraft(craft));
        String patched = EngineeringLoadoutExperimentalPatch.patchLoadoutRawJson(LOADOUT, craft);
        assertNotNull(patched);
        LoadoutEvent loadout = (LoadoutEvent) new EliteLogParser().parseRecord(patched);
        LoadoutEvent.Module huge = loadout.getModules().stream()
                .filter(m -> "HugeHardpoint1".equals(m.getSlot()))
                .findFirst()
                .orElseThrow();
        assertEquals(5, huge.getEngineering().getLevel());
        assertEquals("Tod", huge.getEngineering().getEngineer());
        assertEquals(1L, huge.getEngineering().getBlueprintId());
    }

    @Test
    void craftShouldOverlay_whenSameShipAndNotBeforeLoadout() {
        Instant loadoutTs = Instant.parse("2026-07-23T04:16:12Z");
        Instant craftTs = Instant.parse("2026-07-23T04:58:00Z");
        assertTrue(EngineeringLoadoutExperimentalPatch.craftShouldOverlayLoadout(
                craftTs, loadoutTs, 7L, 7L));
        assertFalse(EngineeringLoadoutExperimentalPatch.craftShouldOverlayLoadout(
                loadoutTs.minusSeconds(60), loadoutTs, 7L, 7L));
        assertFalse(EngineeringLoadoutExperimentalPatch.craftShouldOverlayLoadout(
                craftTs, loadoutTs, 7L, 9L));
    }

    @Test
    void patch_rawFieldsRoundTripInJson() {
        String patched = EngineeringLoadoutExperimentalPatch.patchLoadoutRawJson(
                LOADOUT,
                "MediumHardpoint1",
                "hpt_multicannon_gimbal_medium",
                "special_emissive_munitions",
                "Emissive Munitions");
        assertNotNull(patched);
        JsonObject root = JsonParser.parseString(patched).getAsJsonObject();
        JsonObject eng = root.getAsJsonArray("Modules").get(1).getAsJsonObject()
                .getAsJsonObject("Engineering");
        assertEquals("special_emissive_munitions", eng.get("ExperimentalEffect").getAsString());
        assertEquals("Emissive Munitions", eng.get("ExperimentalEffect_Localised").getAsString());
    }
}
