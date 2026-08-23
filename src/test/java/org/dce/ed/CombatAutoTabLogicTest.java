package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.BountyEvent;
import org.dce.ed.logreader.event.FactionKillBondEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.logreader.event.UnderAttackEvent;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class CombatAutoTabLogicTest {

    @Test
    void attackedPreferenceSwitchesForUnderAttack() {
        CombatAutoTabLogic logic = new CombatAutoTabLogic();

        assertTrue(logic.shouldSwitch(
                new UnderAttackEvent(Instant.EPOCH, new JsonObject(), "You"), true, false));
        assertFalse(logic.shouldSwitch(
                new UnderAttackEvent(Instant.EPOCH, new JsonObject(), "You"), false, true));
    }

    @Test
    void attackedPreferenceSwitchesOnlyOnInterdictionEntry() {
        CombatAutoTabLogic logic = new CombatAutoTabLogic();

        assertFalse(logic.shouldSwitch(status(false), true, false));
        assertTrue(logic.shouldSwitch(status(true), true, false));
        assertFalse(logic.shouldSwitch(status(true), true, false));
        assertFalse(logic.shouldSwitch(status(false), true, false));
        assertTrue(logic.shouldSwitch(status(true), true, false));
    }

    @Test
    void disabledAttackedPreferenceStillTracksInterdictionEdge() {
        CombatAutoTabLogic logic = new CombatAutoTabLogic();

        assertFalse(logic.shouldSwitch(status(true), false, false));
        assertFalse(logic.shouldSwitch(status(true), true, false));
        assertFalse(logic.shouldSwitch(status(false), true, false));
        assertTrue(logic.shouldSwitch(status(true), true, false));
    }

    @Test
    void rewardPreferenceSwitchesForBountyAndFactionKillBond() {
        CombatAutoTabLogic logic = new CombatAutoTabLogic();

        assertTrue(logic.shouldSwitch(
                new BountyEvent(Instant.EPOCH, new JsonObject(), 12_000L), false, true));
        assertTrue(logic.shouldSwitch(
                new FactionKillBondEvent(Instant.EPOCH, new JsonObject(), 8_000L), false, true));
        assertFalse(logic.shouldSwitch(
                new BountyEvent(Instant.EPOCH, new JsonObject(), 12_000L), true, false));
    }

    private static StatusEvent status(boolean beingInterdicted) {
        long flags = beingInterdicted ? 0x00800000L : 0L;
        return (StatusEvent) new EliteLogParser().parseRecord(
                "{\"timestamp\":\"2026-08-22T12:00:00Z\",\"event\":\"Status\",\"Flags\":"
                        + flags + ",\"Flags2\":0}");
    }
}
