package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.dce.ed.logreader.event.BountyEvent;
import org.dce.ed.logreader.event.FactionKillBondEvent;
import org.dce.ed.logreader.event.RedeemVoucherEvent;
import org.dce.ed.logreader.event.ShipTargetedEvent;
import org.dce.ed.session.EdoSessionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class CombatTargetTrackerTest {

    private CombatTargetTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = CombatTargetTracker.getInstance();
        tracker.resetForTests();
    }

    @Test
    void tracksLocalAndRemoteBountySplit() {
        tracker.applyShipTargeted(stage3("Carlos", 242_475L, "Wanted", false));
        assertFalse(tracker.getScannedWantedShips().get(0).isWarrantScanned());
        assertEquals("?", CombatTargetTracker.remoteDisplayToken(
                tracker.getScannedWantedShips().get(0).getRemoteBounty(),
                tracker.getScannedWantedShips().get(0).isWarrantScanned()));

        tracker.applyShipTargeted(stage3("Carlos", 305_335L, "Wanted", false));

        assertEquals(1, tracker.getScannedWantedShips().size());
        CombatTargetTracker.ScannedWantedShip scanned = tracker.getScannedWantedShips().get(0);
        assertEquals(242_475L, scanned.getFirstBounty());
        assertEquals(305_335L, scanned.getCurrentBounty());
        assertEquals(62_860L, scanned.getRemoteBounty());
        assertTrue(scanned.isWarrantScanned());
        assertNull(CombatTargetTracker.remoteDisplayToken(scanned.getRemoteBounty(), true));

        CombatTargetTracker.LockedTarget locked = tracker.getLockedTarget();
        assertNotNull(locked);
        assertEquals(242_475L, locked.getLocalBounty().longValue());
        assertEquals(62_860L, locked.getRemoteBounty());
        assertTrue(locked.isWarrantScanned());
    }

    @Test
    void warrantScanWithNoAdditionalShowsZero() {
        tracker.applyShipTargeted(stage3("Dana", 100_000L, "Wanted", false));
        tracker.applyShipTargeted(stage3("Dana", 100_000L, "Wanted", false));

        CombatTargetTracker.ScannedWantedShip scanned = tracker.getScannedWantedShips().get(0);
        assertTrue(scanned.isWarrantScanned());
        assertEquals(0L, scanned.getRemoteBounty());
        assertEquals("0", CombatTargetTracker.remoteDisplayToken(0L, true));
        assertEquals("0", CombatTabPanel.formatRemote(0L, true));
        assertEquals("?", CombatTabPanel.formatRemote(0L, false));
    }

    @Test
    void clearsLockedTargetOnUnlock() {
        tracker.applyShipTargeted(stage3("Raider", 50_000L, "Wanted", false));
        assertNotNull(tracker.getLockedTarget());

        tracker.applyShipTargeted(unlock());
        assertNull(tracker.getLockedTarget());
    }

    @Test
    void killListClearsOnBountyRedeem() {
        tracker.applyBounty(bounty(
                "{ \"Rewards\":[{\"Faction\":\"A\",\"Reward\":7000},{\"Faction\":\"B\",\"Reward\":3000}], "
                        + "\"TotalReward\":10000, \"Target\":\"eagle\", \"VictimFaction\":\"Faction A\" }",
                10_000L));
        assertEquals(1, tracker.getKills().size());
        assertEquals(10_000L, tracker.getTotalBountiesEarned());
        assertEquals(3_000L, tracker.getTotalOtherBounties());

        tracker.applyJournalEvent(new RedeemVoucherEvent(
                Instant.parse("2026-06-22T14:00:00Z"),
                new JsonObject(),
                "bounty",
                10_000L));
        assertTrue(tracker.getKills().isEmpty());
        assertEquals(0L, tracker.getTotalBountiesEarned());
        assertEquals(0L, tracker.getTotalOtherBounties());
    }

    @Test
    void combatBondKillListClearsOnCombatBondRedeem() {
        JsonObject raw = JsonParser.parseString(
                "{\"Reward\":41881,\"VictimFaction\":\"Union of Gliese 868 Green Party\"}")
                .getAsJsonObject();
        tracker.applyJournalEvent(new FactionKillBondEvent(
                Instant.parse("2026-08-13T20:02:14Z"), raw, 41_881L));
        assertEquals(1, tracker.getKills().size());

        tracker.applyJournalEvent(new RedeemVoucherEvent(
                Instant.parse("2026-08-13T21:00:00Z"),
                new JsonObject(),
                "CombatBond",
                41_881L));

        assertTrue(tracker.getKills().isEmpty());
    }

    @Test
    void combatBondRedeemKeepsUnredeemedBountyState() {
        tracker.applyBounty(bounty(
                "{\"TotalReward\":10000,\"Target\":\"eagle\",\"VictimFaction\":\"Pirates\"}",
                10_000L));
        JsonObject raw = JsonParser.parseString(
                "{\"Reward\":41881,\"VictimFaction\":\"Union of Gliese 868 Green Party\"}")
                .getAsJsonObject();
        tracker.applyJournalEvent(new FactionKillBondEvent(
                Instant.parse("2026-08-13T20:02:14Z"), raw, 41_881L));

        tracker.applyJournalEvent(new RedeemVoucherEvent(
                Instant.parse("2026-08-13T21:00:00Z"),
                new JsonObject(),
                "CombatBond",
                41_881L));

        assertEquals(1, tracker.getKills().size());
        assertEquals(10_000L, tracker.getKills().get(0).getTotalReward());
        assertEquals(10_000L, tracker.getTotalBountiesEarned());
    }

    @Test
    void bountyRedeemKeepsUnredeemedCombatBondKills() {
        tracker.applyBounty(bounty(
                "{\"TotalReward\":10000,\"Target\":\"eagle\",\"VictimFaction\":\"Pirates\"}",
                10_000L));
        JsonObject raw = JsonParser.parseString(
                "{\"Reward\":41881,\"VictimFaction\":\"Union of Gliese 868 Green Party\"}")
                .getAsJsonObject();
        tracker.applyJournalEvent(new FactionKillBondEvent(
                Instant.parse("2026-08-13T20:02:14Z"), raw, 41_881L));

        tracker.applyJournalEvent(new RedeemVoucherEvent(
                Instant.parse("2026-08-13T21:00:00Z"),
                new JsonObject(),
                "bounty",
                10_000L));

        assertEquals(1, tracker.getKills().size());
        assertTrue(tracker.getKills().get(0).isCombatBond());
        assertEquals(41_881L, tracker.getKills().get(0).getTotalReward());
    }

    @Test
    void killUsesLocalisedShipNameFromPriorScan() {
        tracker.applyShipTargeted(new ShipTargetedEvent(
                Instant.parse("2026-06-22T13:04:47Z"),
                new JsonObject(),
                true,
                3,
                "Rexford",
                "$npc_name_decorate:#name=Rexford;",
                Long.valueOf(231_695L),
                "asp_scout",
                "Asp Scout",
                "Hostile",
                "Faction",
                "Dangerous",
                null,
                null,
                null,
                false));
        tracker.applyBounty(bounty(
                "{ \"Rewards\":[{\"Faction\":\"A\",\"Reward\":231695}], "
                        + "\"TotalReward\":231695, \"Target\":\"asp_scout\", \"VictimFaction\":\"Faction A\" }",
                231_695L));

        CombatTargetTracker.KillVictim kill = tracker.getKills().get(0);
        assertEquals("Asp Scout", kill.getShipDisplay());
        assertEquals("Rexford", kill.getPilotName());
        assertTrue(tracker.getScannedWantedShips().isEmpty(),
                "Killed pilots should leave the scanned (living) list");
        assertNull(tracker.getLockedTarget());
    }

    @Test
    void factionKillBondAddsLockedConflictTargetToKills() {
        tracker.applyShipTargeted(new ShipTargetedEvent(
                Instant.parse("2026-08-13T20:02:06Z"),
                new JsonObject(),
                true,
                3,
                "Federal Navy Ship",
                "$ShipName_Military_Federation;",
                Long.valueOf(0L),
                "federation_dropship_mkii",
                "Federal Assault Ship",
                "Lawless",
                "Union of Gliese 868 Green Party",
                "Competent",
                null,
                null,
                null,
                false));
        JsonObject raw = JsonParser.parseString(
                "{\"Reward\":41881,\"AwardingFaction\":\"Gliese 868 Services\","
                        + "\"VictimFaction\":\"Union of Gliese 868 Green Party\"}")
                .getAsJsonObject();

        tracker.applyJournalEvent(new FactionKillBondEvent(
                Instant.parse("2026-08-13T20:02:14Z"), raw, 41_881L));

        assertEquals(1, tracker.getKills().size());
        CombatTargetTracker.KillVictim kill = tracker.getKills().get(0);
        assertEquals("Federal Navy Ship", kill.getPilotName());
        assertEquals("Federal Assault Ship", kill.getShipDisplay());
        assertEquals("Union of Gliese 868 Green Party", kill.getVictimFaction());
        assertEquals(41_881L, kill.getTotalReward());
    }

    @Test
    void killRemovesVictimFromScannedButKeepsOthers() {
        tracker.applyShipTargeted(stage3("Keep Me", 100_000L, "Wanted", false));
        tracker.applyShipTargeted(stage3("Kill Me", 200_000L, "Wanted", false));
        assertEquals(2, tracker.getScannedWantedShips().size());

        tracker.applyBounty(bounty(
                "{ \"Rewards\":[{\"Faction\":\"A\",\"Reward\":200000}], "
                        + "\"TotalReward\":200000, \"Target\":\"viper\", \"VictimFaction\":\"Faction A\" }",
                200_000L));

        assertEquals(1, tracker.getScannedWantedShips().size());
        assertEquals("Keep Me", tracker.getScannedWantedShips().get(0).getPilotName());
        assertEquals(1, tracker.getKills().size());
        assertEquals("Kill Me", tracker.getKills().get(0).getPilotName());
    }

    @Test
    void prettyShipIdTitleCasesInternalIds() {
        assertEquals("Asp Scout", CombatTargetTracker.prettyShipId("asp_scout"));
        assertEquals("Eagle", CombatTargetTracker.prettyShipId("eagle"));
        assertEquals("Cobra MkIV", CombatTargetTracker.prettyShipId("cobramkiv"));
        assertEquals("Type-10 Defender", CombatTargetTracker.prettyShipId("type9_military"));
    }

    @Test
    void scannedWantedSortedByCurrentBountyDescending() {
        tracker.applyShipTargeted(stage3("Low", 50_000L, "Wanted", false));
        tracker.applyShipTargeted(stage3("High", 400_000L, "Wanted", false));
        tracker.applyShipTargeted(stage3("Mid", 120_000L, "Wanted", false));

        List<CombatTargetTracker.ScannedWantedShip> scanned = tracker.getScannedWantedShips();
        assertEquals(3, scanned.size());
        assertEquals("High", scanned.get(0).getPilotName());
        assertEquals("Mid", scanned.get(1).getPilotName());
        assertEquals("Low", scanned.get(2).getPilotName());
    }

    @Test
    void killsSortedRecentToOlder() {
        tracker.applyBounty(bountyAt(
                "{ \"TotalReward\":1000, \"Target\":\"eagle\", \"VictimFaction\":\"A\" }",
                1_000L,
                Instant.parse("2026-06-22T13:10:00Z")));
        tracker.applyBounty(bountyAt(
                "{ \"TotalReward\":2000, \"Target\":\"viper\", \"VictimFaction\":\"B\" }",
                2_000L,
                Instant.parse("2026-06-22T13:12:00Z")));
        tracker.applyBounty(bountyAt(
                "{ \"TotalReward\":3000, \"Target\":\"cobra\", \"VictimFaction\":\"C\" }",
                3_000L,
                Instant.parse("2026-06-22T13:11:00Z")));

        List<CombatTargetTracker.KillVictim> kills = tracker.getKills();
        assertEquals(3, kills.size());
        assertEquals(2_000L, kills.get(0).getTotalReward());
        assertEquals(3_000L, kills.get(1).getTotalReward());
        assertEquals(1_000L, kills.get(2).getTotalReward());
    }

    @Test
    void sessionRoundTripPreservesScannedAndKills() {
        tracker.applyShipTargeted(stage3("Carlos", 242_475L, "Wanted", false));
        tracker.applyShipTargeted(stage3("Carlos", 305_335L, "Wanted", false));
        // Living scan that should survive — kill a different pilot.
        tracker.applyShipTargeted(stage3("Dana", 80_000L, "Wanted", false));
        tracker.applyBounty(bounty(
                "{ \"Rewards\":[{\"Faction\":\"A\",\"Reward\":7000},{\"Faction\":\"B\",\"Reward\":3000}], "
                        + "\"TotalReward\":10000, \"Target\":\"viper\", \"VictimFaction\":\"Faction A\" }",
                10_000L));

        EdoSessionState state = new EdoSessionState();
        tracker.fillSessionState(state);
        assertNotNull(state.getCombat());
        assertEquals(1, state.getCombat().scannedOrEmpty().size());
        assertEquals("Carlos", state.getCombat().scannedOrEmpty().get(0).getPilotName());
        assertEquals(1, state.getCombat().killsOrEmpty().size());

        tracker.resetForTests();
        assertTrue(tracker.getScannedWantedShips().isEmpty());
        assertTrue(tracker.getKills().isEmpty());

        tracker.applySessionState(state);
        assertEquals(1, tracker.getScannedWantedShips().size());
        CombatTargetTracker.ScannedWantedShip scanned = tracker.getScannedWantedShips().get(0);
        assertEquals("Carlos", scanned.getPilotName());
        assertEquals(242_475L, scanned.getFirstBounty());
        assertEquals(305_335L, scanned.getCurrentBounty());
        assertTrue(scanned.isWarrantScanned());
        assertEquals(1, tracker.getKills().size());
        assertEquals("Dana", tracker.getKills().get(0).getPilotName());
        assertEquals(10_000L, tracker.getTotalBountiesEarned());
        assertEquals(3_000L, tracker.getTotalOtherBounties());
    }

    @Test
    void sessionRoundTripPreservesRewardTypeForSelectiveRedemption() {
        tracker.applyBounty(bounty(
                "{\"TotalReward\":10000,\"Target\":\"eagle\",\"VictimFaction\":\"Pirates\"}",
                10_000L));
        JsonObject raw = JsonParser.parseString(
                "{\"Reward\":41881,\"VictimFaction\":\"Union of Gliese 868 Green Party\"}")
                .getAsJsonObject();
        tracker.applyJournalEvent(new FactionKillBondEvent(
                Instant.parse("2026-08-13T20:02:14Z"), raw, 41_881L));
        EdoSessionState state = new EdoSessionState();
        tracker.fillSessionState(state);

        tracker.resetForTests();
        tracker.applySessionState(state);
        tracker.applyJournalEvent(new RedeemVoucherEvent(
                Instant.parse("2026-08-13T21:00:00Z"),
                new JsonObject(),
                "bounty",
                10_000L));

        assertEquals(1, tracker.getKills().size());
        assertTrue(tracker.getKills().get(0).isCombatBond());
        assertEquals(41_881L, tracker.getKills().get(0).getTotalReward());
    }

    private static ShipTargetedEvent stage3(String pilot, long bounty, String legal, boolean player) {
        String rawName = player ? pilot : "$npc_name_decorate:#name=" + pilot + ";";
        return new ShipTargetedEvent(
                Instant.parse("2026-06-22T13:04:47Z"),
                new JsonObject(),
                true,
                3,
                pilot,
                rawName,
                Long.valueOf(bounty),
                "viper",
                null,
                legal,
                "Faction",
                "Dangerous",
                null,
                null,
                null,
                player);
    }

    private static ShipTargetedEvent unlock() {
        return new ShipTargetedEvent(
                Instant.parse("2026-06-22T13:05:00Z"),
                new JsonObject(),
                false,
                0,
                null,
                null);
    }

    private static BountyEvent bounty(String json, long total) {
        return bountyAt(json, total, Instant.parse("2026-06-22T13:10:00Z"));
    }

    private static BountyEvent bountyAt(String json, long total, Instant when) {
        JsonObject raw = JsonParser.parseString(json).getAsJsonObject();
        return new BountyEvent(when, raw, total);
    }
}
