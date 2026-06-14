package org.dce.ed.logreader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.CarrierLocationEvent;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class OwnedFleetCarrierTrackerTest {

    private static final long OWNED_ID = 3714348544L;
    private static final long FRIEND_ID = 9999999999L;

    @Test
    void carrierStats_setsOwnedId() {
        OwnedFleetCarrierTracker tracker = new OwnedFleetCarrierTracker();
        tracker.onCarrierStats(OWNED_ID);
        assertEquals(OWNED_ID, tracker.getOwnedCarrierId());
    }

    @Test
    void carrierLocation_updatesOwnedLocationOnlyForOwnedId() {
        OwnedFleetCarrierTracker tracker = new OwnedFleetCarrierTracker();
        tracker.onCarrierStats(OWNED_ID);

        tracker.onCarrierLocation(location(FRIEND_ID, "Friend System", 111L));
        assertFalse(tracker.hasOwnedCarrierLocation());

        tracker.onCarrierLocation(location(OWNED_ID, "My Carrier System", 222L));
        assertEquals("My Carrier System", tracker.getOwnedSystemName());
        assertEquals(222L, tracker.getOwnedSystemAddress());
    }

    @Test
    void ownedCarrierJump_matchesLastOwnedCarrierLocation() {
        OwnedFleetCarrierTracker tracker = new OwnedFleetCarrierTracker();
        tracker.onCarrierStats(OWNED_ID);
        tracker.onCarrierLocation(location(OWNED_ID, "Before Jump", 100L));

        CarrierJumpEvent jump = carrierJump("After Jump", 200L);
        assertTrue(tracker.isOwnedCarrierJump(jump, false));
    }

    @Test
    void friendCarrierJump_isRejectedWhenAboardFriend() {
        OwnedFleetCarrierTracker tracker = new OwnedFleetCarrierTracker();
        tracker.onCarrierStats(OWNED_ID);
        tracker.onCarrierLocation(location(FRIEND_ID, "Friend System", 300L));

        CarrierJumpEvent jump = carrierJump("Friend Jump", 400L);
        assertFalse(tracker.isOwnedCarrierJump(jump, false));
    }

    @Test
    void pendingOwnedJumpRequest_allowsJumpWithoutMatchingLocationId() {
        OwnedFleetCarrierTracker tracker = new OwnedFleetCarrierTracker();
        tracker.onCarrierStats(OWNED_ID);

        CarrierJumpEvent jump = carrierJump("Target", 500L);
        assertTrue(tracker.isOwnedCarrierJump(jump, true));
    }

    @Test
    void carrierLocationBeforeCarrierStats_appliesAfterStats() {
        OwnedFleetCarrierTracker tracker = new OwnedFleetCarrierTracker();
        tracker.onCarrierLocation(location(OWNED_ID, "Early System", 100L));
        assertFalse(tracker.hasOwnedCarrierLocation());

        tracker.onCarrierStats(OWNED_ID);

        assertEquals("Early System", tracker.getOwnedSystemName());
        assertEquals(100L, tracker.getOwnedSystemAddress());
    }

    @Test
    void carrierLocationBeforeCarrierStats_ignoredWhenIdsDiffer() {
        OwnedFleetCarrierTracker tracker = new OwnedFleetCarrierTracker();
        tracker.onCarrierLocation(location(FRIEND_ID, "Friend System", 100L));
        tracker.onCarrierStats(OWNED_ID);
        assertFalse(tracker.hasOwnedCarrierLocation());
    }

    private static CarrierLocationEvent location(long carrierId, String system, long address) {
        return new CarrierLocationEvent(
                Instant.EPOCH,
                new JsonObject(),
                carrierId,
                system,
                address,
                1);
    }

    private static CarrierJumpEvent carrierJump(String system, long address) {
        return new CarrierJumpEvent(
                Instant.EPOCH,
                new JsonObject(),
                false,
                true,
                null,
                null,
                0L,
                null,
                null,
                null,
                java.util.Collections.emptyList(),
                null,
                null,
                java.util.Collections.emptyList(),
                false,
                false,
                system,
                address,
                new double[] { 1.0, 2.0, 3.0 },
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0L,
                system,
                0,
                "Star");
    }
}
