package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.FsdTargetEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.route.RouteTargetState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link org.dce.ed.route.RouteTargetState}: side-trip clearing, FSD target, and NavRoute clear.
 */
class RouteTargetStateTest {

    private RouteTargetState state;
    private EliteLogParser parser;
    private static final String ISO_TS = "2026-02-15T22:47:39Z";

    @BeforeEach
    void setUp() {
        state = new RouteTargetState();
        parser = new EliteLogParser();
    }

    @Test
    void restoreFromPersistence_roundTripsTargetAndDestination() {
        state.restoreFromPersistence("Tgt", 42L, 1L, 7, "Body A");
        assertEquals("Tgt", state.getTargetSystemName());
        assertEquals(42L, state.getTargetSystemAddress());
        assertEquals(1L, state.getDestinationSystemAddress().longValue());
        assertEquals(7, state.getDestinationBodyId().intValue());
        assertEquals("Body A", state.getDestinationName());
    }

    @Test
    void applyNavRouteClear_clearsTargetAndDestination() {
        state.applyFsdTargetEvent(parseFsdTarget("Alpha Centauri", 12345L), false, false);
        state.applyStatusEvent(parseStatusWithDestination("Sol", 999L), List.of());
        state.applyNavRouteClear();
        assertNull(state.getTargetSystemName());
        assertEquals(0L, state.getTargetSystemAddress());
        assertNull(state.getDestinationSystemAddress());
        assertNull(state.getDestinationBodyId());
        assertNull(state.getDestinationName());
        assertFalse(state.wasSideTripCleared());
    }

    @Test
    void applyFsdTargetEvent_setsTargetWhenNotHyperspace() {
        state.applyFsdTargetEvent(parseFsdTarget("Barnard's Star", 45678L), false, false);
        assertEquals("Barnard's Star", state.getTargetSystemName());
        assertEquals(45678L, state.getTargetSystemAddress());
    }

    @Test
    void applyFsdTargetEvent_clearsTargetWhenNameBlank() {
        state.applyFsdTargetEvent(parseFsdTarget("Sol", 100L), false, false);
        state.applyFsdTargetEvent(parseFsdTarget("", 0L), false, false);
        assertNull(state.getTargetSystemName());
        assertEquals(0L, state.getTargetSystemAddress());
    }

    @Test
    void applyFsdTargetEvent_doesNotUpdateWhenInHyperspace() {
        state.applyFsdTargetEvent(parseFsdTarget("Sol", 100L), false, false);
        state.applyFsdTargetEvent(parseFsdTarget("Alpha Centauri", 200L), true, false);
        assertEquals("Sol", state.getTargetSystemName());
        assertEquals(100L, state.getTargetSystemAddress());
    }

    @Test
    void applyFsdTargetEvent_doesNotUpdateWhenTimerRunning() {
        state.applyFsdTargetEvent(parseFsdTarget("Sol", 100L), false, false);
        state.applyFsdTargetEvent(parseFsdTarget("Alpha Centauri", 200L), false, true);
        assertEquals("Sol", state.getTargetSystemName());
        assertEquals(100L, state.getTargetSystemAddress());
    }

    @Test
    void applyStatusEvent_blankDestination_clearsTarget() {
        state.applyFsdTargetEvent(parseFsdTarget("Side Trip", 999L), false, false);
        state.applyStatusEvent(parseStatusWithDestination(null, null), List.of());
        assertNull(state.getTargetSystemName());
        assertEquals(0L, state.getTargetSystemAddress());
        assertTrue(state.wasSideTripCleared());
    }

    @Test
    void applyStatusEvent_destinationOnRoute_targetOffRoute_clearsTarget() {
        state.applyFsdTargetEvent(parseFsdTarget("Off-Route System", 999L), false, false);
        List<RouteTargetState.RouteSystemRef> route = List.of(
                new RouteTargetState.RouteSystemRef("Sol", 100L),
                new RouteTargetState.RouteSystemRef("Alpha Centauri", 200L));
        // Status says destination is "Sol" (on route); target is off route -> clear target
        state.applyStatusEvent(parseStatusWithDestination("Sol", 100L), route);
        assertNull(state.getTargetSystemName());
        assertEquals(0L, state.getTargetSystemAddress());
        assertTrue(state.wasSideTripCleared());
    }

    @Test
    void applyStatusEvent_destinationOnRoute_targetOnRoute_doesNotClearTarget() {
        state.applyFsdTargetEvent(parseFsdTarget("Alpha Centauri", 200L), false, false);
        List<RouteTargetState.RouteSystemRef> route = List.of(
                new RouteTargetState.RouteSystemRef("Sol", 100L),
                new RouteTargetState.RouteSystemRef("Alpha Centauri", 200L));
        state.applyStatusEvent(parseStatusWithDestination("Alpha Centauri", 200L), route);
        assertEquals("Alpha Centauri", state.getTargetSystemName());
        assertEquals(200L, state.getTargetSystemAddress());
        assertFalse(state.wasSideTripCleared());
    }

    @Test
    void applyStatusEvent_destinationOnRoute_targetMatchesByName_doesNotClearTarget() {
        state.applyFsdTargetEvent(parseFsdTarget("Sol", 100L), false, false);
        List<RouteTargetState.RouteSystemRef> route = List.of(
                new RouteTargetState.RouteSystemRef("Sol", 100L));
        state.applyStatusEvent(parseStatusWithDestination("Sol", 100L), route);
        assertEquals("Sol", state.getTargetSystemName());
        assertEquals(100L, state.getTargetSystemAddress());
    }

    // --- Sequence tests: form route, side-trip, cancel, etc. ---

    @Test
    void sequence_routeThenSideTripThenStatusOnRoute_clearsTarget() {
        List<RouteTargetState.RouteSystemRef> route = List.of(
                new RouteTargetState.RouteSystemRef("Sol", 100L),
                new RouteTargetState.RouteSystemRef("Alpha Centauri", 200L));
        state.applyFsdTargetEvent(parseFsdTarget("Side Trip System", 999L), false, false);
        state.applyStatusEvent(parseStatusWithDestination("Alpha Centauri", 200L), route);
        assertNull(state.getTargetSystemName());
        assertEquals(0L, state.getTargetSystemAddress());
        assertTrue(state.wasSideTripCleared());
    }

    @Test
    void sequence_routeThenTargetOnRouteThenStatus_sameDestination_targetUnchanged() {
        List<RouteTargetState.RouteSystemRef> route = List.of(
                new RouteTargetState.RouteSystemRef("Sol", 100L),
                new RouteTargetState.RouteSystemRef("Alpha Centauri", 200L));
        state.applyFsdTargetEvent(parseFsdTarget("Alpha Centauri", 200L), false, false);
        state.applyStatusEvent(parseStatusWithDestination("Alpha Centauri", 200L), route);
        assertEquals("Alpha Centauri", state.getTargetSystemName());
        assertEquals(200L, state.getTargetSystemAddress());
    }

    @Test
    void sequence_targetSetThenNavRouteClear_clearsEverything() {
        state.applyFsdTargetEvent(parseFsdTarget("Some System", 123L), false, false);
        state.applyStatusEvent(parseStatusWithDestination("Some System", 123L), List.of());
        state.applyNavRouteClear();
        assertNull(state.getTargetSystemName());
        assertNull(state.getDestinationName());
    }

    @Test
    void sequence_blankStatusAfterSideTrip_clearsTarget() {
        state.applyFsdTargetEvent(parseFsdTarget("Side Trip", 999L), false, false);
        state.applyStatusEvent(parseStatusWithDestination(null, null), List.of());
        assertNull(state.getTargetSystemName());
        state.applyStatusEvent(parseStatusWithDestination("Sol", 100L), List.of(new RouteTargetState.RouteSystemRef("Sol", 100L)));
        assertEquals("Sol", state.getDestinationName());
    }

    private FsdTargetEvent parseFsdTarget(String name, long address) {
        String json = "{\"event\":\"FSDTarget\",\"timestamp\":\"" + ISO_TS + "\",\"Name\":\"" + name + "\",\"SystemAddress\":" + address + "}";
        return (FsdTargetEvent) parser.parseRecord(json);
    }

    private StatusEvent parseStatusWithDestination(String destName, Long destSystem) {
        StringBuilder json = new StringBuilder("{\"event\":\"Status\",\"timestamp\":\"" + ISO_TS + "\",\"Flags\":0,\"Flags2\":0");
        if (destName != null || destSystem != null) {
            json.append(",\"Destination\":{");
            if (destName != null) {
                json.append("\"Name\":\"").append(destName.replace("\"", "\\\"")).append("\"");
            }
            if (destSystem != null) {
                if (destName != null) {
                    json.append(",");
                }
                json.append("\"System\":").append(destSystem);
            }
            json.append("}");
        }
        json.append("}");
        return (StatusEvent) parser.parseRecord(json.toString());
    }
}
