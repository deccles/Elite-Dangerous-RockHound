package org.dce.ed.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class SystemEventProcessorDockedTest {

    @Test
    void dockedEvent_setsDockedAndCarrierParkedBodyFromBodyId() {
        SystemState state = new SystemState();
        state.setSystemAddress(42L);
        SystemEventProcessor proc = new SystemEventProcessor("test", state);

        JsonObject raw = new JsonObject();
        raw.addProperty("event", "Docked");
        raw.addProperty("BodyID", 7);
        raw.addProperty("SystemAddress", 42L);
        raw.addProperty("StationType", "FleetCarrier");

        proc.handleEvent(new EliteLogEvent.GenericEvent(Instant.EPOCH, EliteEventType.DOCKED, raw));

        assertTrue(state.isDocked());
        assertEquals(Integer.valueOf(7), state.getCarrierParkedBodyId());
        assertEquals(42L, state.getCarrierParkedSystemAddress());
    }
}
