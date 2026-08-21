package org.dce.ed.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class RouteSessionTest {
    @Test
    void removeBaseRouteEntry_adjustsCurrentIndex() {
        RouteSession session = new RouteSession(null, null);
        session.replaceBaseRouteEntries(List.of(
                new RouteEntry(0, "A", 1L, "G", 0.0, RouteScanStatus.UNKNOWN),
                new RouteEntry(1, "B", 2L, "G", 1.0, RouteScanStatus.UNKNOWN),
                new RouteEntry(2, "C", 3L, "G", 1.0, RouteScanStatus.UNKNOWN)));
        session.applyKnownCurrentSystem("B", 2L, null);

        assertTrue(session.removeBaseRouteEntry(0));
        assertEquals(0, session.getCurrentBaseIndex());
        assertEquals(List.of("B", "C"), session.getBaseRouteEntries().stream().map(e -> e.systemName).toList());
        assertTrue(session.removeBaseRouteEntry(0));
        assertEquals(0, session.getCurrentBaseIndex());
        assertEquals("C", session.getBaseRouteEntries().get(0).systemName);
    }


    private TestJumpFlash flash;
    private RouteSession session;

    @BeforeEach
    void setUp() {
        flash = new TestJumpFlash();
        session = new RouteSession(flash, j -> false);
    }

    @Test
    void locationEventUpdatesCurrentAndClearsPending() {
        session.startCarrierPendingJumpBlink("Dest", 42L);
        assertTrue(flash.running);
        LocationEvent loc = new LocationEvent(Instant.now(), new JsonObject(),
                false, false, false,
                "Sol", 100L, new double[] { 0, 0, 0 },
                null, 0, null);
        RouteJournalApplyOutcome o = session.applySecondaryJournalEvent(loc);
        assertEquals(false, o.exitHandleLogWithoutSessionPersist());
        assertEquals(true, o.refreshDisplayedRows());
        assertEquals("Sol", session.getCurrentSystemName());
        assertEquals(100L, session.getCurrentSystemAddress());
        assertEquals(0L, session.getPendingJumpLockedAddress());
        // Matches legacy RouteTabPanel: Location clears latch fields but does not stop the blink timer.
        assertTrue(flash.running);
    }

    @Test
    void fsdJumpSeedsSingleRowWhenBaseEmpty() {
        FsdJumpEvent jump = new FsdJumpEvent(Instant.now(), new JsonObject(), "Remote", 999L, new double[] { 1, 2, 3 },
                null, 0, null, 0, 0, 0, null);
        session.applySecondaryJournalEvent(jump);
        assertEquals(1, session.getBaseRouteEntries().size());
        assertEquals("Remote", session.getBaseRouteEntries().get(0).systemName);
    }

    @Test
    void fsdJumpUpdatesSingleRowPlaceholderOnLaterArrival() {
        session.applySecondaryJournalEvent(new FsdJumpEvent(Instant.now(), new JsonObject(),
                "HIP 12099", 111L, new double[] { 1, 2, 3 }, null, 0, null, 0, 0, 0, null));
        session.applySecondaryJournalEvent(new FsdJumpEvent(Instant.now(), new JsonObject(),
                "Sol", 222L, new double[] { 0, 0, 0 }, null, 0, null, 0, 0, 0, null));
        assertEquals(1, session.getBaseRouteEntries().size());
        assertEquals("Sol", session.getBaseRouteEntries().get(0).systemName);
        assertEquals(222L, session.getBaseRouteEntries().get(0).systemAddress);
        assertEquals("Sol", session.getCurrentSystemName());
    }

    @Test
    void locationEventUpdatesSingleRowPlaceholderWhenNoPlottedRoute() {
        session.applySecondaryJournalEvent(new FsdJumpEvent(Instant.now(), new JsonObject(),
                "HIP 12099", 111L, new double[] { 1, 2, 3 }, null, 0, null, 0, 0, 0, null));
        LocationEvent loc = new LocationEvent(Instant.now(), new JsonObject(),
                false, false, false,
                "Sol", 222L, new double[] { 0, 0, 0 },
                null, 0, null);
        session.applySecondaryJournalEvent(loc);
        assertEquals(1, session.getBaseRouteEntries().size());
        assertEquals("Sol", session.getBaseRouteEntries().get(0).systemName);
        assertEquals(222L, session.getBaseRouteEntries().get(0).systemAddress);
    }

    @Test
    void fsdJumpDoesNotRewriteMultiHopPlottedRoute() {
        session.applyNavRouteReloadParsed(List.of(sampleEntry("A", 1L), sampleEntry("B", 2L)));
        session.applySecondaryJournalEvent(new FsdJumpEvent(Instant.now(), new JsonObject(),
                "B", 2L, new double[] { 0, 0, 0 }, null, 0, null, 0, 0, 0, null));
        assertEquals(2, session.getBaseRouteEntries().size());
        assertEquals("A", session.getBaseRouteEntries().get(0).systemName);
        assertEquals("B", session.getBaseRouteEntries().get(1).systemName);
        assertEquals("B", session.getCurrentSystemName());
    }

    @Test
    void fsdJumpClearsPlottedTargetWhenArrivalMatches() {
        session.getTargetState().restoreFromPersistence("Remote", 999L, null, null, null);
        FsdJumpEvent jump = new FsdJumpEvent(Instant.now(), new JsonObject(), "Remote", 999L, new double[] { 1, 2, 3 },
                null, 0, null, 0, 0, 0, null);
        session.applySecondaryJournalEvent(jump);
        assertTrue(session.getTargetState().getTargetSystemName() == null
                || session.getTargetState().getTargetSystemName().isBlank());
        assertEquals(0L, session.getTargetState().getTargetSystemAddress());
    }

    @Test
    void navRouteReloadClearsTargetState() {
        session.getTargetState().restoreFromPersistence("T", 5L, null, null, null);
        session.applyNavRouteReloadParsed(List.of(sampleEntry("A", 1L)));
        assertTrue(session.getTargetState().getTargetSystemName() == null
                || session.getTargetState().getTargetSystemName().isEmpty());
    }

    @Test
    void applyPersistenceSnapshotRestartsJumpFlashWhenPendingLockedPresent() {
        RoutePersistenceSnapshot snap = new RoutePersistenceSnapshot(
                "Sol", 1L, null,
                null, null, null, null, null,
                "Paesui Xena", 42L,
                Boolean.FALSE,
                null);
        session.applyPersistenceSnapshot(snap);
        assertTrue(flash.running);
        assertEquals("Paesui Xena", session.getPendingJumpLockedName());
        assertEquals(42L, session.getPendingJumpLockedAddress());
    }

    @Test
    void applyPersistenceSnapshotStopsJumpFlashWhenPendingLockedAbsent() {
        session.startCarrierPendingJumpBlink("Temp", 99L);
        assertTrue(flash.running);
        RoutePersistenceSnapshot cleared = new RoutePersistenceSnapshot(
                "Sol", 1L, null,
                null, null, null, null, null,
                null, null,
                Boolean.FALSE,
                null);
        session.applyPersistenceSnapshot(cleared);
        assertFalse(flash.running);
        assertEquals(0L, session.getPendingJumpLockedAddress());
    }

    @Test
    void appendBaseRouteEntry_addsHopAndRenumbers() {
        session.appendBaseRouteEntry(sampleEntry("Sol", 1L));
        session.appendBaseRouteEntry(sampleEntry("Alpha Centauri", 2L));
        assertEquals(2, session.getBaseRouteEntries().size());
        assertEquals(0, session.getBaseRouteEntries().get(0).index);
        assertEquals(1, session.getBaseRouteEntries().get(1).index);
        assertEquals("Alpha Centauri", session.getBaseRouteEntries().get(1).systemName);
    }

    @Test
    void insertBaseRouteEntries_preservesOrderAfterSelectedHop() {
        session.replaceBaseRouteEntries(List.of(
                sampleEntry("A", 1L),
                sampleEntry("D", 4L)));

        session.insertBaseRouteEntries(1, List.of(
                sampleEntry("B", 2L),
                sampleEntry("C", 3L)));

        assertEquals(List.of("A", "B", "C", "D"),
                session.getBaseRouteEntries().stream().map(e -> e.systemName).toList());
        assertEquals(List.of(0, 1, 2, 3),
                session.getBaseRouteEntries().stream().map(e -> e.index).toList());
    }

    @Test
    void ensureCurrentSystemAtStartIfMissing_seedsBeforePaste() {
        session.ensureCurrentSystemAtStartIfMissing("Sol", 100L, new double[] { 0, 0, 0 });
        session.appendBaseRouteEntry(sampleEntry("Colonia", 200L));
        assertEquals(2, session.getBaseRouteEntries().size());
        assertEquals("Sol", session.getBaseRouteEntries().get(0).systemName);
        assertEquals("Colonia", session.getBaseRouteEntries().get(1).systemName);
    }

    @Test
    void ensureCurrentSystemAtStartIfMissing_noopWhenAlreadyPresent() {
        session.appendBaseRouteEntry(sampleEntry("Sol", 100L));
        session.appendBaseRouteEntry(sampleEntry("Colonia", 200L));
        session.ensureCurrentSystemAtStartIfMissing("Sol", 100L, null);
        assertEquals(2, session.getBaseRouteEntries().size());
        assertEquals("Sol", session.getBaseRouteEntries().get(0).systemName);
    }

    @Test
    void moveBaseRouteEntry_reordersHops() {
        session.appendBaseRouteEntry(sampleEntry("A", 1L));
        session.appendBaseRouteEntry(sampleEntry("B", 2L));
        session.appendBaseRouteEntry(sampleEntry("C", 3L));
        assertTrue(session.moveBaseRouteEntry(2, 0));
        assertEquals("C", session.getBaseRouteEntries().get(0).systemName);
        assertEquals("A", session.getBaseRouteEntries().get(1).systemName);
        assertEquals("B", session.getBaseRouteEntries().get(2).systemName);
        assertEquals(0, session.getBaseRouteEntries().get(0).index);
        assertEquals(2, session.getBaseRouteEntries().get(2).index);
    }

    @Test
    void moveBaseRouteEntry_noOpWhenAdjacent() {
        session.appendBaseRouteEntry(sampleEntry("A", 1L));
        session.appendBaseRouteEntry(sampleEntry("B", 2L));
        assertFalse(session.moveBaseRouteEntry(0, 1));
        assertEquals("A", session.getBaseRouteEntries().get(0).systemName);
    }

    @Test
    void loopedCustomRoute_advancesCurrentBaseIndexMonotonically() {
        session.replaceBaseRouteEntries(List.of(
                sampleEntry("Gyll", 1L),
                sampleEntry("Fliese", 2L),
                sampleEntry("Gyll", 1L),
                sampleEntry("Fliese", 2L)));
        session.applyKnownCurrentSystem("Gyll", 1L, null);
        assertEquals(0, session.getCurrentBaseIndex());
        assertEquals("Fliese", org.dce.ed.RouteTabPanel.nextRouteDestinationSystemName(session));

        session.applyKnownCurrentSystem("Fliese", 2L, null);
        assertEquals(1, session.getCurrentBaseIndex());
        assertEquals("Gyll", org.dce.ed.RouteTabPanel.nextRouteDestinationSystemName(session));

        session.applyKnownCurrentSystem("Gyll", 1L, null);
        assertEquals(2, session.getCurrentBaseIndex());
        assertEquals("Fliese", org.dce.ed.RouteTabPanel.nextRouteDestinationSystemName(session));

        session.applyKnownCurrentSystem("Fliese", 2L, null);
        assertEquals(3, session.getCurrentBaseIndex());
        assertEquals(null, org.dce.ed.RouteTabPanel.nextRouteDestinationSystemName(session));
    }

    @Test
    void loopedCustomRoute_doesNotMoveIndexBackwardOnRepeatArrival() {
        session.replaceBaseRouteEntries(List.of(
                sampleEntry("Gyll", 1L),
                sampleEntry("Fliese", 2L),
                sampleEntry("Gyll", 1L)));
        session.applyKnownCurrentSystem("Gyll", 1L, null);
        session.applyKnownCurrentSystem("Fliese", 2L, null);
        session.applyKnownCurrentSystem("Gyll", 1L, null);
        assertEquals(2, session.getCurrentBaseIndex());
        // Same system again (Location spam) must not snap back to hop 0.
        session.applyKnownCurrentSystem("Gyll", 1L, null);
        assertEquals(2, session.getCurrentBaseIndex());
    }

    @Test
    void enabledCustomRouteLoop_resetsIndexWhenFirstSystemReachedAfterEnd() {
        session.replaceBaseRouteEntries(List.of(
                sampleEntry("Alpha", 1L),
                sampleEntry("Beta", 2L),
                sampleEntry("Gamma", 3L)));
        session.applyKnownCurrentSystem("Alpha", 1L, null, true);
        session.applyKnownCurrentSystem("Beta", 2L, null, true);
        session.applyKnownCurrentSystem("Gamma", 3L, null, true);
        assertEquals(2, session.getCurrentBaseIndex());

        session.applyKnownCurrentSystem("Alpha", 1L, null, true);

        assertEquals(0, session.getCurrentBaseIndex());
    }

    @Test
    void customRouteLoop_doesNotResetBeforeEndOrWhenDisabled() {
        session.replaceBaseRouteEntries(List.of(
                sampleEntry("Alpha", 1L),
                sampleEntry("Beta", 2L),
                sampleEntry("Gamma", 3L)));
        session.applyKnownCurrentSystem("Alpha", 1L, null, true);
        session.applyKnownCurrentSystem("Beta", 2L, null, true);
        session.applyKnownCurrentSystem("Alpha", 1L, null, true);
        assertEquals(1, session.getCurrentBaseIndex());

        session.applyKnownCurrentSystem("Gamma", 3L, null, true);
        session.applyKnownCurrentSystem("Alpha", 1L, null, false);
        assertEquals(2, session.getCurrentBaseIndex());
    }

    @Test
    void persistenceRoundTrip_preservesCurrentBaseIndexOnLoop() {
        session.replaceBaseRouteEntries(List.of(
                sampleEntry("Gyll", 1L),
                sampleEntry("Fliese", 2L),
                sampleEntry("Gyll", 1L),
                sampleEntry("Fliese", 2L)));
        session.applyKnownCurrentSystem("Gyll", 1L, null);
        session.applyKnownCurrentSystem("Fliese", 2L, null);
        session.applyKnownCurrentSystem("Gyll", 1L, null);
        assertEquals(2, session.getCurrentBaseIndex());

        RoutePersistenceSnapshot snap = session.toPersistenceSnapshot();
        assertEquals(Integer.valueOf(2), snap.currentBaseIndex());

        RouteSession restored = new RouteSession(flash, j -> false);
        restored.replaceBaseRouteEntries(session.getBaseRouteEntries());
        restored.applyPersistenceSnapshot(snap);
        assertEquals(2, restored.getCurrentBaseIndex());
        assertEquals("Gyll", restored.getCurrentSystemName());
        assertEquals("Fliese", org.dce.ed.RouteTabPanel.nextRouteDestinationSystemName(restored));
    }

    @Test
    void persistenceRestore_realignsStaleIndexToCurrentSystemOnNewGameRoute() {
        session.replaceBaseRouteEntries(List.of(
                sampleEntry("Current", 1L),
                sampleEntry("Next", 2L),
                sampleEntry("Destination", 3L)));
        RoutePersistenceSnapshot stale = new RoutePersistenceSnapshot(
                "Current", 1L, null,
                null, null, null, null, null,
                null, null, false, 2);

        session.applyPersistenceSnapshot(stale);

        assertEquals(0, session.getCurrentBaseIndex());
        assertEquals("Next", org.dce.ed.RouteTabPanel.nextRouteDestinationSystemName(session));
    }

    private static RouteEntry sampleEntry(String name, long addr) {
        RouteEntry e = new RouteEntry();
        e.systemName = name;
        e.systemAddress = addr;
        e.status = RouteScanStatus.UNKNOWN;
        return e;
    }

    static final class TestJumpFlash implements RouteJumpFlashHandle {
        boolean running;

        @Override
        public boolean isTimerRunning() {
            return running;
        }

        @Override
        public void startTimer() {
            running = true;
        }

        @Override
        public void stopTimer() {
            running = false;
        }
    }
}
