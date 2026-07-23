package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShipEngineeringSummaryTest {

    private static EngineeringDatabase db;

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
    }

    private static final String LOADOUT =
            "{\"timestamp\":\"2026-07-23T04:16:12Z\",\"event\":\"Loadout\",\"Ship\":\"anaconda\",\"ShipID\":7,"
                    + "\"ShipName\":\"Exception Handler\",\"Modules\":["
                    + "{\"Slot\":\"HugeHardpoint1\",\"Item\":\"hpt_multicannon_gimbal_huge\",\"On\":true,"
                    + "\"Engineering\":{\"BlueprintName\":\"Weapon_Overcharged\",\"Level\":5,\"Quality\":1.0,"
                    + "\"ExperimentalEffect\":\"special_auto_loader\","
                    + "\"ExperimentalEffect_Localised\":\"Auto Loader\"}},"
                    + "{\"Slot\":\"MediumHardpoint1\",\"Item\":\"hpt_multicannon_gimbal_medium\",\"On\":true,"
                    + "\"Engineering\":{\"BlueprintName\":\"Weapon_Overcharged\",\"Level\":3,\"Quality\":1.0,"
                    + "\"ExperimentalEffect\":\"special_corrosive_shell\","
                    + "\"ExperimentalEffect_Localised\":\"Corrosive Shell\"}},"
                    + "{\"Slot\":\"PowerDistributor\",\"Item\":\"int_powerdistributor_size7_class5\",\"On\":true},"
                    + "{\"Slot\":\"PaintJob\",\"Item\":\"PaintJob_Anaconda_Black\",\"On\":true}"
                    + "]}";

    @Test
    void bands_gapPartialDone_andSkipsCosmetic() {
        LoadoutEvent loadout = (LoadoutEvent) new EliteLogParser().parseRecord(LOADOUT);
        ShipEngineeringSummary summary = ShipEngineeringSummary.fromLoadout(loadout, db);

        assertEquals(1, summary.gapCount());
        assertEquals(1, summary.partialCount());
        assertEquals(1, summary.doneCount());
        assertEquals(3, summary.rows().size());

        ShipEngineeringSummary.Row done = summary.rowsInBand(ShipEngineeringSummary.Band.DONE).get(0);
        assertTrue(done.componentDisplay().toLowerCase().contains("multi")
                || done.componentDisplay().toLowerCase().contains("cannon"));
        assertEquals("Huge", done.slotSizeDisplay());
        assertTrue(done.moduleDisplay().toLowerCase().contains("huge"));
        assertTrue(done.engineeringDisplay().contains("G5"));
        assertTrue(done.engineeringDisplay().contains("Auto Loader"));
        assertFalse(done.canUpgrade());

        ShipEngineeringSummary.Row partial = summary.rowsInBand(ShipEngineeringSummary.Band.PARTIAL).get(0);
        assertEquals("Med", partial.slotSizeDisplay());
        assertTrue(partial.engineeringDisplay().contains("G3"));
        assertTrue(partial.canUpgrade());
        assertEquals("G3", partial.levelDisplay(),
                "partial without goal should show current grade only");
        assertEquals("G3→G5", partial.levelDisplay(5));
        assertEquals("G3", partial.levelDisplay(3));
        assertEquals("G3", partial.levelDisplay(2));

        ShipEngineeringSummary.Row gap = summary.rowsInBand(ShipEngineeringSummary.Band.GAP).get(0);
        assertEquals("—", gap.engineeringDisplay());
        assertEquals("—", gap.slotSizeDisplay());
        assertTrue(gap.moduleType().toLowerCase().contains("power")
                || gap.moduleLabel().toLowerCase().contains("power")
                || gap.componentDisplay().toLowerCase().contains("power"));
    }

    @Test
    void shortSlotSize_hardpointAndOptional() {
        assertEquals("Huge", ShipEngineeringSummary.shortSlotSize("Huge Hardpoint 1"));
        assertEquals("Huge", ShipEngineeringSummary.shortSlotSize("HugeHardpoint1"));
        assertEquals("Med", ShipEngineeringSummary.shortSlotSize("Medium Hardpoint 1"));
        assertEquals("Med", ShipEngineeringSummary.shortSlotSize("MediumHardpoint1"));
        assertEquals("Tiny", ShipEngineeringSummary.shortSlotSize("Tiny Hardpoint 6"));
        assertEquals("Size 4", ShipEngineeringSummary.shortSlotSize("Slot 09 Size 4"));
        assertEquals("Size 6", ShipEngineeringSummary.shortSlotSize("Slot02_Size6"));
        assertEquals("", ShipEngineeringSummary.shortSlotSize("Armour"));
        assertEquals("", ShipEngineeringSummary.shortSlotSize("Power Distributor"));
    }

    @Test
    void clipboard_includesCountsAndSections() {
        LoadoutEvent loadout = (LoadoutEvent) new EliteLogParser().parseRecord(LOADOUT);
        ShipEngineeringSummary summary = ShipEngineeringSummary.fromLoadout(loadout, db);
        String text = summary.toClipboardText("Exception Handler (Anaconda)");

        assertTrue(text.startsWith("Exception Handler (Anaconda)\n"));
        assertTrue(text.contains("1 no engineering · 1 partial · 1 done"));
        assertTrue(text.contains("\nNo Engineering\n"));
        assertTrue(text.contains("\nPartial\n"));
        assertTrue(text.contains("\nDone\n"));
        assertTrue(text.contains(" — "));
        assertFalse(text.toLowerCase().contains("paintjob"));
    }

    @Test
    void hullReinforcementAdvanced_mapsToLightweightPartial() {
        String loadoutJson =
                "{\"timestamp\":\"2026-07-23T04:16:12Z\",\"event\":\"Loadout\",\"Ship\":\"anaconda\",\"ShipID\":7,"
                        + "\"ShipName\":\"Exception Handler\",\"Modules\":["
                        + "{\"Slot\":\"Slot08_Size4\",\"Item\":\"int_hullreinforcement_size4_class2\",\"On\":true,"
                        + "\"Engineering\":{\"BlueprintName\":\"HullReinforcement_Advanced\",\"Level\":1,\"Quality\":0.33}}"
                        + "]}";
        LoadoutEvent loadout = (LoadoutEvent) new EliteLogParser().parseRecord(loadoutJson);
        ShipEngineeringSummary summary = ShipEngineeringSummary.fromLoadout(loadout, db);
        assertEquals(1, summary.partialCount());
        assertEquals(0, summary.doneCount());
        ShipEngineeringSummary.Row row = summary.rowsInBand(ShipEngineeringSummary.Band.PARTIAL).get(0);
        assertEquals("Lightweight Hull Reinforcement", row.blueprintLabel());
        assertEquals("G1", row.levelDisplay());
        assertEquals("G1→G5", row.levelDisplay(5));
    }

    @Test
    void emptyLoadout_emptySummary() {
        ShipEngineeringSummary summary = ShipEngineeringSummary.fromLoadout(null, db);
        assertTrue(summary.isEmpty());
        assertEquals("0 no engineering · 0 partial · 0 done", summary.countsLine());
    }
}
