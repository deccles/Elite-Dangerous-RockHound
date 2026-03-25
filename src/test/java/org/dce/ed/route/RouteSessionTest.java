package org.dce.ed.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class RouteSessionTest {

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
    void navRouteReloadClearsTargetState() {
        session.getTargetState().restoreFromPersistence("T", 5L, null, null, null);
        session.applyNavRouteReloadParsed(List.of(sampleEntry("A", 1L)));
        assertTrue(session.getTargetState().getTargetSystemName() == null
                || session.getTargetState().getTargetSystemName().isEmpty());
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
