package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.LoadGameEvent;
import org.dce.ed.logreader.event.ShipTargetedEvent;
import org.dce.ed.tts.TtsSprintf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class BountyScanTrackerTest {

    @BeforeEach
    void reset() {
        BountyScanTracker.getInstance().resetSession();
        OverlayPreferences.setBountyScanFirstAnnouncementEnabled(true);
        OverlayPreferences.setBountyScanAdditionalAnnouncementEnabled(true);
    }

    @Test
    void firstStage3BountyAnnouncesRoundedTotal() {
        Optional<BountyScanTracker.SpeechRequest> req = BountyScanTracker.getInstance()
                .onShipTargeted(stage3("Carlos SpicyWeiner", 242_475L));
        assertTrue(req.isPresent());
        assertEquals(BountyScanTracker.FIRST_BOUNTY_SPEECH, req.get().getTemplate());
        assertEquals(TtsSprintf.roundCreditsForSpeech(242_475L), req.get().getCredits1());
    }

    @Test
    void additionalBountyAnnouncesDeltaAndTotal() {
        BountyScanTracker tracker = BountyScanTracker.getInstance();
        tracker.onShipTargeted(stage3("Carlos SpicyWeiner", 242_475L));

        Optional<BountyScanTracker.SpeechRequest> req = tracker
                .onShipTargeted(stage3("Carlos SpicyWeiner", 305_335L));
        assertTrue(req.isPresent());
        assertEquals(BountyScanTracker.ADDITIONAL_BOUNTY_SPEECH, req.get().getTemplate());
        assertEquals(TtsSprintf.roundCreditsForSpeech(62_860L), req.get().getCredits1());
        assertEquals(TtsSprintf.roundCreditsForSpeech(305_335L), req.get().getCredits2());
    }

    @Test
    void thirdScanWithHigherBountyIsSilent() {
        BountyScanTracker tracker = BountyScanTracker.getInstance();
        tracker.onShipTargeted(stage3("Carlos SpicyWeiner", 242_475L));
        tracker.onShipTargeted(stage3("Carlos SpicyWeiner", 305_335L));

        assertTrue(tracker.onShipTargeted(stage3("Carlos SpicyWeiner", 400_000L)).isEmpty());
    }

    @Test
    void sameBountyOnSecondScanIsSilent() {
        BountyScanTracker tracker = BountyScanTracker.getInstance();
        tracker.onShipTargeted(stage3("Carlos SpicyWeiner", 242_475L));

        assertTrue(tracker.onShipTargeted(stage3("Carlos SpicyWeiner", 242_475L)).isEmpty());
    }

    @Test
    void lowerBountyOnSecondScanIsSilent() {
        BountyScanTracker tracker = BountyScanTracker.getInstance();
        tracker.onShipTargeted(stage3("Carlos SpicyWeiner", 305_335L));

        assertTrue(tracker.onShipTargeted(stage3("Carlos SpicyWeiner", 242_475L)).isEmpty());
    }

    @Test
    void ignoresNonStage3AndMissingBounty() {
        BountyScanTracker tracker = BountyScanTracker.getInstance();
        assertTrue(tracker.onShipTargeted(new ShipTargetedEvent(
                Instant.now(), new JsonObject(), true, 2, "Pilot", 100L)).isEmpty());
        assertTrue(tracker.onShipTargeted(new ShipTargetedEvent(
                Instant.now(), new JsonObject(), true, 3, "Pilot", null)).isEmpty());
        assertTrue(tracker.onShipTargeted(new ShipTargetedEvent(
                Instant.now(), new JsonObject(), false, 3, "Pilot", 100L)).isEmpty());
    }

    @Test
    void pilotsTrackedIndependently() {
        BountyScanTracker tracker = BountyScanTracker.getInstance();
        assertTrue(tracker.onShipTargeted(stage3("Alpha", 100_000L)).isPresent());
        assertTrue(tracker.onShipTargeted(stage3("Beta", 200_000L)).isPresent());
    }

    @Test
    void loadGameResetsSession() {
        BountyScanTracker tracker = BountyScanTracker.getInstance();
        tracker.onShipTargeted(stage3("Carlos SpicyWeiner", 242_475L));
        tracker.applyJournalEvent(loadGameEvent());

        Optional<BountyScanTracker.SpeechRequest> req = tracker
                .onShipTargeted(stage3("Carlos SpicyWeiner", 305_335L));
        assertTrue(req.isPresent());
        assertEquals(BountyScanTracker.FIRST_BOUNTY_SPEECH, req.get().getTemplate());
    }

    @Test
    void firstAnnouncementRespectsPreference() {
        OverlayPreferences.setBountyScanFirstAnnouncementEnabled(false);
        assertFalse(BountyScanTracker.getInstance()
                .onShipTargeted(stage3("Carlos SpicyWeiner", 242_475L))
                .isPresent());
    }

    @Test
    void additionalAnnouncementRespectsPreference() {
        BountyScanTracker tracker = BountyScanTracker.getInstance();
        tracker.onShipTargeted(stage3("Carlos SpicyWeiner", 242_475L));

        OverlayPreferences.setBountyScanAdditionalAnnouncementEnabled(false);
        assertFalse(tracker.onShipTargeted(stage3("Carlos SpicyWeiner", 305_335L)).isPresent());
    }

    @Test
    void targetedBountyInSightWhileLockedOnWantedShip() {
        BountyScanTracker tracker = BountyScanTracker.getInstance();
        tracker.updateTargetedBountyInSight(stage3("Carlos SpicyWeiner", 5_000_000L));
        assertEquals(5_000_000L, tracker.getTargetedBountyInSight().longValue());
    }

    @Test
    void targetedBountyInSightClearsOnUnlock() {
        BountyScanTracker tracker = BountyScanTracker.getInstance();
        tracker.updateTargetedBountyInSight(stage3("Carlos SpicyWeiner", 242_475L));
        tracker.updateTargetedBountyInSight(new ShipTargetedEvent(
                Instant.now(), new JsonObject(), false, 3, "Carlos SpicyWeiner", 242_475L));
        assertNull(tracker.getTargetedBountyInSight());
    }

    @Test
    void targetedBountyInSightClearsForCleanTarget() {
        BountyScanTracker tracker = BountyScanTracker.getInstance();
        tracker.updateTargetedBountyInSight(stage3("Carlos SpicyWeiner", 242_475L));
        tracker.updateTargetedBountyInSight(new ShipTargetedEvent(
                Instant.now(), new JsonObject(), true, 3, "DJNoNo Ulysses", null));
        assertNull(tracker.getTargetedBountyInSight());
    }

    @Test
    void targetedBountyInSightUpdatesOnKwsRescan() {
        BountyScanTracker tracker = BountyScanTracker.getInstance();
        tracker.updateTargetedBountyInSight(stage3("Carlos SpicyWeiner", 242_475L));
        tracker.updateTargetedBountyInSight(stage3("Carlos SpicyWeiner", 305_335L));
        assertEquals(305_335L, tracker.getTargetedBountyInSight().longValue());
    }

    @Test
    void targetedBountyInSightNotShownBeforeStage3Scan() {
        BountyScanTracker tracker = BountyScanTracker.getInstance();
        tracker.updateTargetedBountyInSight(new ShipTargetedEvent(
                Instant.now(), new JsonObject(), true, 2, "Carlos SpicyWeiner", null));
        assertNull(tracker.getTargetedBountyInSight());
    }

    @Test
    void targetedBountyInSightLabelUsesCompactMillions() {
        assertEquals("Bounty: 5M", OverlayFrame.formatTargetedBountyInSightLabel(5_000_000L));
        assertEquals("Bounty: 242K", OverlayFrame.formatTargetedBountyInSightLabel(242_475L));
    }

    private static ShipTargetedEvent stage3(String pilot, long bounty) {
        return new ShipTargetedEvent(Instant.parse("2026-06-22T13:04:47Z"),
                new JsonObject(), true, 3, pilot, Long.valueOf(bounty));
    }

    private static LoadGameEvent loadGameEvent() {
        JsonObject raw = JsonParser.parseString(
                "{\"event\":\"LoadGame\",\"Commander\":\"Test\",\"Ship\":\"cutter\"}").getAsJsonObject();
        return new LoadGameEvent(Instant.parse("2026-06-22T14:00:00Z"), raw,
                "Test", null, "cutter", 1, null, null, 0.0, 0.0, null, 0L);
    }
}
