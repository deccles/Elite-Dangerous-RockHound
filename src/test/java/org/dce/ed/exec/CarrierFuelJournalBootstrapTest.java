package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.logreader.OwnedFleetCarrierTracker;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class CarrierFuelJournalBootstrapTest {

    private static final long OWNED_ID = 3714348544L;

    @Test
    void fuelLevelFromJson_acceptsDecimalValue() {
        JsonObject raw = JsonParser.parseString("{ \"FuelLevel\": 455.0 }").getAsJsonObject();
        assertEquals(455, CarrierFuelTracker.fuelLevelFromJson(raw));
    }

    @Test
    void recordFuelFromCarrierStats_updatesLevel() {
        CarrierFuelTracker tracker = new CarrierFuelTracker();
        JsonObject raw = JsonParser.parseString(
                "{ \"CarrierID\": " + OWNED_ID + ", \"FuelLevel\": 320 }").getAsJsonObject();
        assertTrue(tracker.recordFuelFromCarrierStats(raw, OWNED_ID));
        assertEquals(320, tracker.getLastKnownFuelLevel());
    }
}
