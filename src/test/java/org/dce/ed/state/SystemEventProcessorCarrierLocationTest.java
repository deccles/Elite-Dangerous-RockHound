package org.dce.ed.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.CarrierLocationEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class SystemEventProcessorCarrierLocationTest {

    private SystemState state;
    private SystemEventProcessor processor;

    @BeforeEach
    void setUp() {
        state = new SystemState();
        processor = new SystemEventProcessor("test", state);
    }

    @Test
    void carrierLocationWhileOffCarrier_doesNotChangeCommanderSystem() {
        state.setSystemName("Byua Aim NK-M c21-510");
        state.setSystemAddress(140265679658938L);

        CarrierLocationEvent loc = new CarrierLocationEvent(
                Instant.parse("2026-05-17T19:48:10Z"),
                new JsonObject(),
                0L,
                "Byua Aim SZ-G d10-2113",
                72611866382299L,
                0);
        processor.handleEvent(loc);

        assertEquals("Byua Aim NK-M c21-510", state.getSystemName());
        assertEquals(140265679658938L, state.getSystemAddress());
        assertFalse(state.isCommanderAboardFleetCarrier());
    }

    @Test
    void carrierLocationWhileAboard_updatesCommanderSystem() {
        dockOnFleetCarrier();

        state.setSystemName("Sifeae EX-Z c1-3");
        state.setSystemAddress(913117352722L);

        CarrierLocationEvent loc = new CarrierLocationEvent(
                Instant.parse("2025-12-02T04:25:10Z"),
                new JsonObject(),
                3714348544L,
                "Ploea Eurl TH-N c22-2",
                638709240514L,
                0);
        processor.handleEvent(loc);

        assertEquals("Ploea Eurl TH-N c22-2", state.getSystemName());
        assertEquals(638709240514L, state.getSystemAddress());
        assertTrue(state.isCommanderAboardFleetCarrier());
    }

    @Test
    void carrierJumpOnFootWhileAboard_updatesCommanderSystem() {
        dockOnFleetCarrier();
        state.setSystemName("Sifeae EX-Z c1-3");
        state.setSystemAddress(913117352722L);

        CarrierJumpEvent jump = new CarrierJumpEvent(
                Instant.parse("2025-12-02T04:26:01Z"),
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
                "Ploea Eurl TH-N c22-2",
                638709240514L,
                new double[] { 3041.625, 754.125, -67.15625 },
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
                "Ploea Eurl TH-N c22-2",
                0,
                "Star");
        processor.handleEvent(jump);

        assertEquals("Ploea Eurl TH-N c22-2", state.getSystemName());
        assertEquals(638709240514L, state.getSystemAddress());
        assertTrue(state.isCommanderAboardFleetCarrier());
    }

    @Test
    void undockFromFleetCarrier_thenCarrierLocation_doesNotMoveCommander() {
        dockOnFleetCarrier();
        undockFromFleetCarrier();

        state.setSystemName("Elsewhere");
        state.setSystemAddress(999L);

        CarrierLocationEvent loc = new CarrierLocationEvent(
                Instant.parse("2026-05-17T19:48:10Z"),
                new JsonObject(),
                0L,
                "Byua Aim SZ-G d10-2113",
                72611866382299L,
                0);
        processor.handleEvent(loc);

        assertEquals("Elsewhere", state.getSystemName());
        assertEquals(999L, state.getSystemAddress());
        assertFalse(state.isCommanderAboardFleetCarrier());
    }

    private void dockOnFleetCarrier() {
        JsonObject raw = new JsonObject();
        raw.addProperty("event", "Docked");
        raw.addProperty("StationType", "FleetCarrier");
        raw.addProperty("BodyID", 1);
        raw.addProperty("SystemAddress", 913117352722L);
        processor.handleEvent(new EliteLogEvent.GenericEvent(Instant.EPOCH, EliteEventType.DOCKED, raw));
    }

    private void undockFromFleetCarrier() {
        JsonObject raw = new JsonObject();
        raw.addProperty("event", "Undocked");
        raw.addProperty("StationType", "FleetCarrier");
        processor.handleEvent(new EliteLogEvent.GenericEvent(Instant.EPOCH, EliteEventType.UNDOCKED, raw));

        FsdJumpEvent jump = new FsdJumpEvent(
                Instant.parse("2026-05-17T19:35:07Z"),
                new JsonObject(),
                "Byua Aim BB-U c17-546",
                150161955495834L,
                new double[] { 0, 0, 0 },
                null,
                0,
                null,
                0,
                0,
                0,
                null);
        processor.handleEvent(jump);
    }
}
