package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.dce.ed.OverlayPreferences;
import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.BountyEvent;
import org.dce.ed.logreader.event.CargoDepotEvent;
import org.dce.ed.logreader.event.MissionAcceptedEvent;
import org.dce.ed.logreader.event.MissionCompletedEvent;
import org.dce.ed.logreader.event.MissionRedirectedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MissionSpeechTrackerTest {

    private final EliteLogParser parser = new EliteLogParser();
    private MissionTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new MissionTracker();
        MissionSpeechTracker.getInstance().resetSession();
        OverlayPreferences.setMissionProgressAnnouncementEnabled(true);
    }

    @Test
    void bountyKill_inHuntSystem_announcesTargetDestroyed() {
        tracker.setCurrentSystemSupplier(() -> "Nuenets");
        acceptMassacre(10L, "Nuenets Corp.", 5);
        BountyEvent bounty = (BountyEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-05-22T10:10:00Z\",\"event\":\"Bounty\","
                        + "\"VictimFaction\":\"Nuenets Corp.\",\"TotalReward\":5000,\"Target\":\"eagle\"}");
        tracker.applyEvent(bounty);
        Optional<MissionSpeechTracker.SpeechRequest> req = MissionSpeechTracker.getInstance()
                .announceAfterLiveApply(tracker, bounty, null, false);
        assertTrue(req.isPresent());
        assertEquals(MissionSpeechTracker.TARGET_DESTROYED_SPEECH, req.get().getTemplate());
        assertEquals(4, req.get().getN1());
    }

    @Test
    void bountyKill_wrongSystem_doesNotAnnounce() {
        tracker.setCurrentSystemSupplier(() -> "Sol");
        acceptMassacre(11L, "Nuenets Corp.", 5);
        BountyEvent bounty = (BountyEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-05-22T10:10:00Z\",\"event\":\"Bounty\","
                        + "\"VictimFaction\":\"Nuenets Corp.\",\"TotalReward\":5000,\"Target\":\"eagle\"}");
        tracker.applyEvent(bounty);
        assertTrue(MissionSpeechTracker.getInstance()
                .announceAfterLiveApply(tracker, bounty, null, false)
                .isEmpty());
    }

    @Test
    void combatRedirect_announcesCompleteOnce() {
        acceptMassacre(20L, "X", 3);
        MissionRedirectedEvent redirect = (MissionRedirectedEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-05-22T11:00:00Z\",\"event\":\"MissionRedirected\","
                        + "\"MissionID\":20,\"Name\":\"Mission_Massacre\","
                        + "\"NewDestinationSystem\":\"A\",\"NewDestinationStation\":\"Hub\"}");
        tracker.applyEvent(redirect);
        Optional<MissionSpeechTracker.SpeechRequest> first = MissionSpeechTracker.getInstance()
                .announceAfterLiveApply(tracker, redirect, null, false);
        assertTrue(first.isPresent());
        assertEquals(MissionSpeechTracker.COMBAT_COMPLETE_SPEECH, first.get().getTemplate());

        MissionCompletedEvent done = (MissionCompletedEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-05-22T12:00:00Z\",\"event\":\"MissionCompleted\","
                        + "\"MissionID\":20,\"Name\":\"Mission_Massacre\"}");
        MissionRecord prior = tracker.findById(20L);
        tracker.applyEvent(done);
        Optional<MissionSpeechTracker.SpeechRequest> second = MissionSpeechTracker.getInstance()
                .announceAfterLiveApply(tracker, done, prior, false);
        assertTrue(second.isEmpty());
    }

    @Test
    void assassinateComplete_announcesWithoutRedirect() {
        MissionAcceptedEvent accept = (MissionAcceptedEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                        + "\"MissionID\":30,\"Name\":\"Mission_Assassinate\","
                        + "\"Target\":\"Blaze\",\"KillCount\":1,\"DestinationSystem\":\"A\"}");
        tracker.applyEvent(accept);
        MissionCompletedEvent done = (MissionCompletedEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-05-22T11:00:00Z\",\"event\":\"MissionCompleted\","
                        + "\"MissionID\":30,\"Name\":\"Mission_Assassinate\"}");
        MissionRecord prior = tracker.findById(30L);
        tracker.applyEvent(done);
        Optional<MissionSpeechTracker.SpeechRequest> req = MissionSpeechTracker.getInstance()
                .announceAfterLiveApply(tracker, done, prior, false);
        assertTrue(req.isPresent());
        assertEquals(MissionSpeechTracker.COMBAT_COMPLETE_SPEECH, req.get().getTemplate());
    }

    @Test
    void cargoDeliver_announcesCountAndRemaining() {
        MissionAcceptedEvent accept = (MissionAcceptedEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                        + "\"MissionID\":40,\"Name\":\"Mission_Mining_Boom\","
                        + "\"Commodity_Localised\":\"Osmium\",\"Count\":28,"
                        + "\"DestinationSystem\":\"Coeus\",\"DestinationStation\":\"Foster Terminal\"}");
        tracker.applyEvent(accept);
        CargoDepotEvent depot = (CargoDepotEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-05-22T10:30:00Z\",\"event\":\"CargoDepot\","
                        + "\"MissionID\":40,\"UpdateType\":\"Deliver\",\"Count\":12,"
                        + "\"ItemsDelivered\":12,\"TotalItemsToDeliver\":28}");
        tracker.applyEvent(depot);
        Optional<MissionSpeechTracker.SpeechRequest> req = MissionSpeechTracker.getInstance()
                .announceAfterLiveApply(tracker, depot, null, false);
        assertTrue(req.isPresent());
        assertEquals(MissionSpeechTracker.DELIVERED_SPEECH, req.get().getTemplate());
        assertEquals(12, req.get().getN1());
        assertEquals(16, req.get().getN2());
    }

    @Test
    void disabledPref_suppressesSpeech() {
        OverlayPreferences.setMissionProgressAnnouncementEnabled(false);
        acceptMassacre(50L, "X", 3);
        MissionRedirectedEvent redirect = (MissionRedirectedEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-05-22T11:00:00Z\",\"event\":\"MissionRedirected\","
                        + "\"MissionID\":50,\"Name\":\"Mission_Massacre\","
                        + "\"NewDestinationSystem\":\"A\",\"NewDestinationStation\":\"Hub\"}");
        tracker.applyEvent(redirect);
        assertTrue(MissionSpeechTracker.getInstance()
                .announceAfterLiveApply(tracker, redirect, null, false)
                .isEmpty());
    }

    private void acceptMassacre(long id, String faction, int killCount) {
        MissionAcceptedEvent accept = (MissionAcceptedEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                        + "\"MissionID\":" + id + ",\"Name\":\"Mission_Massacre\","
                        + "\"TargetFaction\":\"" + faction + "\","
                        + "\"TargetType_Localised\":\"Pirate\",\"KillCount\":" + killCount + ","
                        + "\"DestinationSystem\":\"Nuenets\"}");
        tracker.applyEvent(accept);
    }
}
