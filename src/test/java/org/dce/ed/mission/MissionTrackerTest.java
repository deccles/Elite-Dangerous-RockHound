package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.BountyEvent;
import org.dce.ed.logreader.event.CargoDepotEvent;
import org.dce.ed.logreader.event.MissionAcceptedEvent;
import org.dce.ed.logreader.event.MissionCompletedEvent;
import org.dce.ed.logreader.event.MissionRedirectedEvent;
import org.dce.ed.logreader.event.MissionsEvent;
import org.dce.ed.session.EdoSessionState;
import org.junit.jupiter.api.Test;

class MissionTrackerTest {

    private final EliteLogParser parser = new EliteLogParser();

    @Test
    void journalReplayFill_preservesPersistedManualSourceByMissionId() {
        MissionTracker saved = new MissionTracker();
        saved.applyEvent((MissionAcceptedEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-08-12T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":42,\"Name\":\"Mission_Collect_Industrial\","
                + "\"Commodity_Localised\":\"Gold\",\"Count\":50}"));
        assertTrue(saved.setSourcedFrom(42L, "Sol", "Galileo"));
        EdoSessionState state = new EdoSessionState();
        saved.fillSessionState(state);

        MissionTracker replayed = new MissionTracker();
        replayed.applyEvent((MissionAcceptedEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-08-12T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":42,\"Name\":\"Mission_Collect_Industrial\","
                + "\"Commodity_Localised\":\"Gold\",\"Count\":50}"));
        replayed.fillSessionState(state);

        MissionTracker restored = new MissionTracker();
        restored.applySessionState(state);
        assertEquals("Sol", restored.findById(42L).getSourcedFromSystem());
        assertEquals("Galileo", restored.findById(42L).getSourcedFromStation());
    }

    @Test
    void acceptAndComplete_removesMission() {
        MissionTracker tracker = new MissionTracker();
        String accept = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":1055486629,\"Name\":\"Mission_Mining_Boom\","
                + "\"LocalisedName\":\"Mining rush\",\"Commodity_Localised\":\"Bromellite\","
                + "\"Count\":28,\"DestinationSystem\":\"Coeus\",\"DestinationStation\":\"Foster Terminal\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(accept));
        assertEquals(1, tracker.getActive().size());

        String done = "{\"timestamp\":\"2026-05-22T11:00:00Z\",\"event\":\"MissionCompleted\","
                + "\"MissionID\":1055486629}";
        tracker.applyEvent((MissionCompletedEvent) parser.parseRecord(done));
        assertTrue(tracker.getActive().isEmpty());
    }

    @Test
    void cargoDepot_updatesProgress() {
        MissionTracker tracker = new MissionTracker();
        String accept = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":99,\"Commodity_Localised\":\"Osmium\",\"Count\":10,"
                + "\"DestinationSystem\":\"Coeus\",\"DestinationStation\":\"Foster Terminal\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(accept));
        String depot = "{\"timestamp\":\"2026-05-22T10:30:00Z\",\"event\":\"CargoDepot\","
                + "\"MissionID\":99,\"ItemsDelivered\":4,\"TotalItemsToDeliver\":10}";
        tracker.applyEvent((CargoDepotEvent) parser.parseRecord(depot));
        MissionRecord r = tracker.getActive().get(0);
        assertEquals(4, r.getItemsDelivered());
        assertEquals(10, r.getTotalItemsToDeliver());
    }

    @Test
    void missionsSnapshot_emptyActive_doesNotWipeTrackedMissions() {
        MissionTracker tracker = new MissionTracker();
        String accept = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":1,\"Name\":\"Mission_Mining_Boom\",\"Commodity_Localised\":\"Osmium\","
                + "\"Count\":28,\"DestinationSystem\":\"Coeus\",\"DestinationStation\":\"Foster Terminal\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(accept));
        String snap = "{\"timestamp\":\"2026-05-22T12:00:00Z\",\"event\":\"Missions\",\"Active\":[],\"Failed\":[],\"Complete\":[]}";
        tracker.applyEvent((MissionsEvent) parser.parseRecord(snap));
        assertEquals(1, tracker.getActive().size());
        assertEquals(1L, tracker.getActive().get(0).getMissionId());
    }

    @Test
    void missionsSnapshot_reconcilesActiveSet() {
        MissionTracker tracker = new MissionTracker();
        String accept = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":1,\"Name\":\"Mission_Courier\",\"DestinationSystem\":\"A\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(accept));
        String snap = "{\"timestamp\":\"2026-05-22T12:00:00Z\",\"event\":\"Missions\","
                + "\"Active\":[{\"MissionID\":2,\"Name\":\"Mission_Mining_Boom\",\"PassengerMission\":false,\"Expires\":3600}]}";
        tracker.applyEvent((MissionsEvent) parser.parseRecord(snap));
        assertEquals(1, tracker.getActive().size());
        assertEquals(2L, tracker.getActive().get(0).getMissionId());
    }

    @Test
    void bounty_inHuntSystem_advancesMassacreProgress() {
        MissionTracker tracker = new MissionTracker();
        tracker.setCurrentSystemSupplier(() -> "Nuenets");
        String accept = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":10,\"Name\":\"Mission_Massacre\","
                + "\"TargetFaction\":\"Nuenets Corp.\",\"TargetType_Localised\":\"Pirate\",\"KillCount\":5,"
                + "\"DestinationSystem\":\"Nuenets\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(accept));

        String bounty = "{\"timestamp\":\"2026-05-22T10:10:00Z\",\"event\":\"Bounty\","
                + "\"VictimFaction\":\"Nuenets Corp.\",\"TotalReward\":5000,\"Target\":\"eagle\"}";
        tracker.applyEvent((BountyEvent) parser.parseRecord(bounty));

        MissionRecord r = tracker.getActive().get(0);
        assertEquals(1, r.getKillsCompleted());
        assertEquals("1/5 pirates", MissionDestinationResolver.objectiveFor(r).displayLine());
        assertEquals(4, tracker.consumeLastMassacreKillRemaining().orElse(-1));
    }

    @Test
    void accept_snapshotsOriginSystemAndStationIndependently() {
        MissionTracker tracker = new MissionTracker();
        tracker.setCurrentSystemSupplier(() -> "Sol");
        tracker.setCurrentStationSupplier(() -> "Abraham Lincoln");
        String accept = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":42,\"Name\":\"Mission_Delivery\","
                + "\"Commodity_Localised\":\"Osmium\",\"Count\":10,"
                + "\"DestinationSystem\":\"Tenjin\",\"DestinationStation\":\"Balakor's Beacon\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(accept));

        MissionRecord r = tracker.getActive().get(0);
        assertEquals("Sol", r.getOriginSystem());
        assertEquals("Abraham Lincoln", r.getOriginStation());
        assertEquals("Sol / Abraham Lincoln", MissionDestinationResolver.originFor(r).displayLine());

        // Later accept replay can backfill station even when system was already set.
        r.setOriginStation(null);
        tracker.setCurrentStationSupplier(() -> "Abraham Lincoln");
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(accept));
        assertEquals("Abraham Lincoln", tracker.findById(42L).getOriginStation());
    }

    @Test
    void bounty_wrongSystem_doesNotAdvanceMassacreProgress() {
        MissionTracker tracker = new MissionTracker();
        tracker.setCurrentSystemSupplier(() -> "Sol");
        String accept = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":11,\"Name\":\"Mission_Massacre\","
                + "\"TargetFaction\":\"Nuenets Corp.\",\"TargetType_Localised\":\"Pirate\",\"KillCount\":5,"
                + "\"DestinationSystem\":\"Nuenets\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(accept));

        String bounty = "{\"timestamp\":\"2026-05-22T10:10:00Z\",\"event\":\"Bounty\","
                + "\"VictimFaction\":\"Nuenets Corp.\",\"TotalReward\":5000,\"Target\":\"eagle\"}";
        tracker.applyEvent((BountyEvent) parser.parseRecord(bounty));

        MissionRecord r = tracker.getActive().get(0);
        assertEquals(0, r.getKillsCompleted());
        assertEquals("0/5 pirates", MissionDestinationResolver.objectiveFor(r).displayLine());
        assertTrue(tracker.consumeLastMassacreKillRemaining().isEmpty());
    }

    @Test
    void bounty_stackedMissions_fromDifferentIssuingFactions_advanceTogether() {
        MissionTracker tracker = new MissionTracker();
        tracker.setCurrentSystemSupplier(() -> "Nuenets");
        String a1 = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":12,\"Name\":\"Mission_Massacre\",\"Faction\":\"Issuer A\","
                + "\"TargetFaction\":\"Nuenets Corp.\",\"KillCount\":5,\"DestinationSystem\":\"Nuenets\"}";
        String a2 = "{\"timestamp\":\"2026-05-22T10:01:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":13,\"Name\":\"Mission_Massacre\",\"Faction\":\"Issuer B\","
                + "\"TargetFaction\":\"Nuenets Corp.\",\"KillCount\":20,\"DestinationSystem\":\"Nuenets\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(a1));
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(a2));

        String bounty = "{\"timestamp\":\"2026-05-22T10:10:00Z\",\"event\":\"Bounty\","
                + "\"VictimFaction\":\"Nuenets Corp.\",\"TotalReward\":5000,\"Target\":\"eagle\"}";
        tracker.applyEvent((BountyEvent) parser.parseRecord(bounty));

        assertEquals(1, tracker.findById(12L).getKillsCompleted());
        assertEquals(1, tracker.findById(13L).getKillsCompleted());
        // Lowest remaining among stacked matches.
        assertEquals(4, tracker.consumeLastMassacreKillRemaining().orElse(-1));
    }

    @Test
    void bounty_stackedMissions_fromSameIssuingFaction_advancesOldestOnly() {
        MissionTracker tracker = new MissionTracker();
        tracker.setCurrentSystemSupplier(() -> "Nuenets");
        String older = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":15,\"Name\":\"Mission_Massacre\",\"Faction\":\"Issuer A\","
                + "\"TargetFaction\":\"Nuenets Corp.\",\"KillCount\":5,\"DestinationSystem\":\"Nuenets\"}";
        String newer = "{\"timestamp\":\"2026-05-22T10:01:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":16,\"Name\":\"Mission_Massacre\",\"Faction\":\"Issuer A\","
                + "\"TargetFaction\":\"Nuenets Corp.\",\"KillCount\":20,\"DestinationSystem\":\"Nuenets\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(older));
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(newer));

        String bounty = "{\"timestamp\":\"2026-05-22T10:10:00Z\",\"event\":\"Bounty\","
                + "\"VictimFaction\":\"Nuenets Corp.\",\"TotalReward\":5000,\"Target\":\"eagle\"}";
        tracker.applyEvent((BountyEvent) parser.parseRecord(bounty));

        assertEquals(1, tracker.findById(15L).getKillsCompleted());
        assertEquals(0, tracker.findById(16L).getKillsCompleted());
        assertEquals(4, tracker.consumeLastMassacreKillRemaining().orElse(-1));
    }

    @Test
    void bounty_beforeMissionAccepted_doesNotAdvanceProgress() {
        MissionTracker tracker = new MissionTracker();
        tracker.setCurrentSystemSupplier(() -> "Nuenets");
        String accept = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":17,\"Name\":\"Mission_Massacre\",\"Faction\":\"Issuer A\","
                + "\"TargetFaction\":\"Nuenets Corp.\",\"KillCount\":5,\"DestinationSystem\":\"Nuenets\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(accept));

        String stale = "{\"timestamp\":\"2026-05-21T22:00:00Z\",\"event\":\"Bounty\","
                + "\"VictimFaction\":\"Nuenets Corp.\",\"TotalReward\":5000,\"Target\":\"eagle\"}";
        tracker.applyEvent((BountyEvent) parser.parseRecord(stale));
        assertEquals(0, tracker.findById(17L).getKillsCompleted());

        String fresh = "{\"timestamp\":\"2026-05-22T10:10:00Z\",\"event\":\"Bounty\","
                + "\"VictimFaction\":\"Nuenets Corp.\",\"TotalReward\":5000,\"Target\":\"eagle\"}";
        tracker.applyEvent((BountyEvent) parser.parseRecord(fresh));
        assertEquals(1, tracker.findById(17L).getKillsCompleted());
    }

    @Test
    void redirect_correctsSiblingEstimateByTheVoucherDrift() {
        MissionTracker tracker = new MissionTracker();
        tracker.setCurrentSystemSupplier(() -> "Cemiess");
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-08-01T03:24:55Z\",\"event\":\"MissionAccepted\",\"MissionID\":70,"
                        + "\"Name\":\"Mission_Massacre\",\"Faction\":\"Vequess Legal Industry\","
                        + "\"TargetFaction\":\"Cemiess Purple Council\",\"KillCount\":9,"
                        + "\"DestinationSystem\":\"Cemiess\"}"));
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-08-01T03:25:32Z\",\"event\":\"MissionAccepted\",\"MissionID\":71,"
                        + "\"Name\":\"Mission_MassacreWing\",\"Faction\":\"Vequess Empire Pact\","
                        + "\"TargetFaction\":\"Cemiess Purple Council\",\"KillCount\":56,"
                        + "\"DestinationSystem\":\"Cemiess\"}"));

        // Eleven bounty vouchers, but only nine were mission kills.
        for (int i = 0; i < 11; i++) {
            tracker.applyEvent((BountyEvent) parser.parseRecord(
                    String.format("{\"timestamp\":\"2026-08-01T04:%02d:00Z\",\"event\":\"Bounty\","
                            + "\"VictimFaction\":\"Cemiess Purple Council\",\"TotalReward\":5000,"
                            + "\"Target\":\"python\"}", 20 + i)));
        }
        assertEquals(11, tracker.findById(71L).getKillsCompleted());

        tracker.applyEvent((MissionRedirectedEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-08-01T04:42:04Z\",\"event\":\"MissionRedirected\",\"MissionID\":70,"
                        + "\"Name\":\"Mission_Massacre\",\"NewDestinationSystem\":\"Vequess\","
                        + "\"NewDestinationStation\":\"Agnews' Folly\"}"));

        assertEquals(9, tracker.findById(70L).getKillsCompleted());
        assertEquals(9, tracker.findById(71L).getKillsCompleted());
    }

    @Test
    void redirect_leavesSiblingsHuntingOtherFactionsAlone() {
        MissionTracker tracker = new MissionTracker();
        tracker.setCurrentSystemSupplier(() -> "Cemiess");
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-08-01T03:24:55Z\",\"event\":\"MissionAccepted\",\"MissionID\":72,"
                        + "\"Name\":\"Mission_Massacre\",\"Faction\":\"Issuer A\","
                        + "\"TargetFaction\":\"Cemiess Purple Council\",\"KillCount\":2,"
                        + "\"DestinationSystem\":\"Cemiess\"}"));
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-08-01T03:24:56Z\",\"event\":\"MissionAccepted\",\"MissionID\":73,"
                        + "\"Name\":\"Mission_Massacre\",\"Faction\":\"Issuer B\","
                        + "\"TargetFaction\":\"Some Other Gang\",\"KillCount\":20,"
                        + "\"DestinationSystem\":\"Cemiess\"}"));
        tracker.findById(73L).setKillsCompleted(6);
        tracker.findById(72L).setKillsCompleted(5);

        tracker.applyEvent((MissionRedirectedEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-08-01T04:42:04Z\",\"event\":\"MissionRedirected\",\"MissionID\":72,"
                        + "\"Name\":\"Mission_Massacre\",\"NewDestinationSystem\":\"Vequess\"}"));

        assertEquals(6, tracker.findById(73L).getKillsCompleted());
    }

    @Test
    void rebuildReplay_keepsKillsWithTurnedInMission_ratherThanSurvivingSameIssuerMission() {
        String massacre = "\"event\":\"MissionAccepted\",\"Name\":\"Mission_Massacre\",\"Faction\":\"Issuer A\","
                + "\"TargetFaction\":\"Nuenets Corp.\",\"KillCount\":12,\"DestinationSystem\":\"Nuenets\"";
        List<EliteLogEvent> history = new ArrayList<>();
        history.add(parser.parseRecord("{\"timestamp\":\"2026-05-22T09:00:00Z\",\"event\":\"Location\","
                + "\"StarSystem\":\"Nuenets\",\"SystemAddress\":1}"));
        history.add(parser.parseRecord("{\"timestamp\":\"2026-05-22T10:00:00Z\",\"MissionID\":60," + massacre + "}"));
        history.add(parser.parseRecord("{\"timestamp\":\"2026-05-22T11:00:00Z\",\"MissionID\":61," + massacre + "}"));
        for (int i = 0; i < 3; i++) {
            history.add(parser.parseRecord("{\"timestamp\":\"2026-05-22T12:0" + i + ":00Z\",\"event\":\"Bounty\","
                    + "\"VictimFaction\":\"Nuenets Corp.\",\"TotalReward\":5000,\"Target\":\"eagle\"}"));
        }
        history.add(parser.parseRecord("{\"timestamp\":\"2026-05-22T13:00:00Z\",\"event\":\"MissionCompleted\","
                + "\"MissionID\":60}"));

        // Only the second mission is still on the board, as after turning the first one in.
        MissionTracker live = new MissionTracker();
        live.applyEvent((MissionAcceptedEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-05-22T11:00:00Z\",\"MissionID\":61," + massacre + "}"));
        live.findById(61L).setKillsCompleted(3);

        assertTrue(live.adoptMassacreKillProgress(MissionTracker.replayMissionHistory(history)));
        assertEquals(0, live.findById(61L).getKillsCompleted());
    }

    @Test
    void bounty_skimmer_ignored() {
        MissionTracker tracker = new MissionTracker();
        tracker.setCurrentSystemSupplier(() -> "Nuenets");
        String accept = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":14,\"Name\":\"Mission_Massacre\","
                + "\"TargetFaction\":\"Nuenets Corp.\",\"KillCount\":5,\"DestinationSystem\":\"Nuenets\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(accept));
        String bounty = "{\"timestamp\":\"2026-05-22T10:10:00Z\",\"event\":\"Bounty\","
                + "\"VictimFaction\":\"Nuenets Corp.\",\"TotalReward\":500,\"Target\":\"Skimmer\"}";
        tracker.applyEvent((BountyEvent) parser.parseRecord(bounty));
        assertEquals(0, tracker.getActive().get(0).getKillsCompleted());
    }

    @Test
    void bounty_ignoresKillsOutsideHuntSystem() {
        MissionTracker tracker = new MissionTracker();
        String accept = "{\"timestamp\":\"2026-07-16T00:50:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":99,\"Name\":\"Mission_Massacre\","
                + "\"TargetFaction\":\"Pirates Inc\",\"TargetType_Localised\":\"Pirate\",\"KillCount\":10,"
                + "\"DestinationSystem\":\"HuntSys\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(accept));
        tracker.resetEstimatedMassacreProgress();
        final String[] system = { null };
        tracker.setCurrentSystemSupplier(() -> system[0]);
        system[0] = "HuntSys";
        tracker.applyEvent((BountyEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-07-16T01:01:00Z\",\"event\":\"Bounty\","
                        + "\"VictimFaction\":\"Pirates Inc\",\"TotalReward\":1000,\"Target\":\"eagle\"}"));
        tracker.applyEvent((BountyEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-07-16T01:02:00Z\",\"event\":\"Bounty\","
                        + "\"VictimFaction\":\"Pirates Inc\",\"TotalReward\":1000,\"Target\":\"sidewinder\"}"));
        system[0] = "Other";
        tracker.applyEvent((BountyEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-07-16T01:04:00Z\",\"event\":\"Bounty\","
                        + "\"VictimFaction\":\"Pirates Inc\",\"TotalReward\":1000,\"Target\":\"eagle\"}"));
        assertEquals(2, tracker.findById(99L).getKillsCompleted());
    }

    @Test
    void resetEstimatedMassacreProgress_clearsIncompleteOnly() {
        MissionTracker tracker = new MissionTracker();
        String accept = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":30,\"Name\":\"Mission_Massacre\","
                + "\"TargetFaction\":\"Nuenets Corp.\",\"KillCount\":5,\"DestinationSystem\":\"Nuenets\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(accept));
        tracker.findById(30L).setKillsCompleted(3);

        String acceptDone = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":31,\"Name\":\"Mission_Massacre\","
                + "\"TargetFaction\":\"X\",\"KillCount\":10,\"DestinationSystem\":\"A\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(acceptDone));
        tracker.findById(31L).setKillsCompleted(10);
        tracker.findById(31L).setRedirected(true);

        tracker.resetEstimatedMassacreProgress();
        assertEquals(0, tracker.findById(30L).getKillsCompleted());
        assertEquals(10, tracker.findById(31L).getKillsCompleted());
    }

    @Test
    void fullReplayStyle_rebuildsMassacreFromBountiesInHuntSystem() {
        MissionTracker tracker = new MissionTracker();
        // Stale session progress that would be wrong after a bad attribution era.
        String accept = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":40,\"Name\":\"Mission_Massacre\","
                + "\"TargetFaction\":\"Nuenets Corp.\",\"TargetType_Localised\":\"Pirate\",\"KillCount\":5,"
                + "\"DestinationSystem\":\"Nuenets\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(accept));
        tracker.findById(40L).setKillsCompleted(99);

        tracker.resetEstimatedMassacreProgress();
        assertEquals(0, tracker.findById(40L).getKillsCompleted());

        final String[] system = { "Sol" };
        tracker.setCurrentSystemSupplier(() -> system[0]);

        tracker.applyEvent((BountyEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-05-22T10:05:00Z\",\"event\":\"Bounty\","
                        + "\"VictimFaction\":\"Nuenets Corp.\",\"TotalReward\":1000,\"Target\":\"eagle\"}"));
        assertEquals(0, tracker.findById(40L).getKillsCompleted());

        system[0] = "Nuenets";
        tracker.applyEvent((BountyEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-05-22T10:10:00Z\",\"event\":\"Bounty\","
                        + "\"VictimFaction\":\"Nuenets Corp.\",\"TotalReward\":1000,\"Target\":\"eagle\"}"));
        tracker.applyEvent((BountyEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-05-22T10:11:00Z\",\"event\":\"Bounty\","
                        + "\"VictimFaction\":\"Nuenets Corp.\",\"TotalReward\":1000,\"Target\":\"sidewinder\"}"));
        assertEquals(2, tracker.findById(40L).getKillsCompleted());
        assertEquals("2/5 pirates", MissionDestinationResolver.objectiveFor(tracker.findById(40L)).displayLine());
    }

    @Test
    void redirect_fillsMassacreKillProgress() {
        MissionTracker tracker = new MissionTracker();
        String accept = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":20,\"Name\":\"Mission_Massacre\","
                + "\"TargetFaction\":\"X\",\"KillCount\":12,\"DestinationSystem\":\"A\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(accept));
        String redirect = "{\"timestamp\":\"2026-05-22T11:00:00Z\",\"event\":\"MissionRedirected\","
                + "\"MissionID\":20,\"Name\":\"Mission_Massacre\","
                + "\"NewDestinationSystem\":\"A\",\"NewDestinationStation\":\"Hub\"}";
        tracker.applyEvent((MissionRedirectedEvent) parser.parseRecord(redirect));
        MissionRecord r = tracker.getActive().get(0);
        assertTrue(r.isRedirected());
        assertEquals(12, r.getKillsCompleted());
        assertEquals("12/12 X", MissionDestinationResolver.objectiveFor(r).displayLine());
    }
}
