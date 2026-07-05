package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class CarrierFuelTrackerTest {

    private static final long OWNED_ID = 3714348544L;

    @Test
    void ingestCarrierStats_recordsNameAndCallsign() {
        CarrierFuelTracker tracker = new CarrierFuelTracker();
        JsonObject stats = JsonParser.parseString(
                "{ \"CarrierID\": " + OWNED_ID
                        + ", \"Name\": \"BLUE EVENT HORIZON\", \"Callsign\": \"JFZ-93T\", \"FuelLevel\": 455 }")
                .getAsJsonObject();
        assertTrue(tracker.ingestCarrierStats(stats, OWNED_ID));
        assertEquals("BLUE EVENT HORIZON", tracker.getLastKnownCarrierName());
        assertEquals("JFZ-93T", tracker.getLastKnownCallsign());
        assertEquals(455, tracker.getLastKnownFuelLevel());
    }

    @Test
    void updateFromCarrierStats_firesOnceUntilHysteresisClears() {
        CarrierFuelTracker tracker = new CarrierFuelTracker();
        JsonObject low = JsonParser.parseString(
                "{ \"CarrierID\": " + OWNED_ID + ", \"FuelLevel\": 80 }").getAsJsonObject();
        JsonObject notYetCleared = JsonParser.parseString(
                "{ \"CarrierID\": " + OWNED_ID + ", \"FuelLevel\": 110 }").getAsJsonObject();
        JsonObject recover = JsonParser.parseString(
                "{ \"CarrierID\": " + OWNED_ID + ", \"FuelLevel\": 130 }").getAsJsonObject();

        assertTrue(tracker.updateFromCarrierStats(low, OWNED_ID, 100, 20));
        assertFalse(tracker.updateFromCarrierStats(low, OWNED_ID, 100, 20));
        assertFalse(tracker.updateFromCarrierStats(notYetCleared, OWNED_ID, 100, 20));
        assertFalse(tracker.updateFromCarrierStats(low, OWNED_ID, 100, 20));
        assertFalse(tracker.updateFromCarrierStats(recover, OWNED_ID, 100, 20));
        assertTrue(tracker.updateFromCarrierStats(low, OWNED_ID, 100, 20));
    }

    @Test
    void updateFromCarrierStats_ignoresOtherCarrier() {
        CarrierFuelTracker tracker = new CarrierFuelTracker();
        JsonObject other = JsonParser.parseString(
                "{ \"CarrierID\": 999, \"FuelLevel\": 10 }").getAsJsonObject();
        assertFalse(tracker.updateFromCarrierStats(other, OWNED_ID, 100, 20));
        assertEquals(-1, tracker.getLastKnownFuelLevel());
    }
}
