package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.dce.ed.route.RouteEntry;
import org.dce.ed.route.RouteJumpFlashHandle;
import org.dce.ed.route.RouteSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NextRouteDestinationTest {

    private RouteSession session;

    @BeforeEach
    void setUp() {
        session = new RouteSession(new NoOpJumpFlash(), j -> false);
    }

    @Test
    void nextHop_afterCurrentRow_returnsFollowingSystem() {
        session.replaceBaseRouteEntries(List.of(
                system("NGC 6153 Sector KX-T b3-1", 100L),
                system("Traikee UP-O d6-33", 200L),
                system("Khun", 300L)));
        session.applyKnownCurrentSystem("NGC 6153 Sector KX-T b3-1", 100L, null);

        assertEquals("Traikee UP-O d6-33", RouteTabPanel.nextRouteDestinationSystemName(session));
    }

    @Test
    void nextHop_skipsCurrentSystemWhenRowUnknown() {
        session.replaceBaseRouteEntries(List.of(
                system("NGC 6153 Sector KX-T b3-1", 100L),
                system("Traikee UP-O d6-33", 200L)));
        session.applyKnownCurrentSystem("NGC 6153 Sector KX-T b3-1", 999L, null);

        assertEquals("Traikee UP-O d6-33", RouteTabPanel.nextRouteDestinationSystemName(session));
    }

    @Test
    void nextHop_prefersLiveCurrentWhenSessionLags() {
        session.replaceBaseRouteEntries(List.of(
                system("Gyllembo", 100L),
                system("Gliese 868", 200L),
                system("Core Sys Sector CB-O a6-1", 300L)));
        // Session still thinks we're in Gyllembo after arriving in Gliese.
        session.applyKnownCurrentSystem("Gyllembo", 100L, null);

        assertEquals("Gliese 868", RouteTabPanel.nextRouteDestinationSystemName(session));
        assertEquals("Core Sys Sector CB-O a6-1",
                RouteTabPanel.nextRouteDestinationSystemName(
                        session.getBaseRouteEntries(), "Gliese 868", 200L));
    }

    @Test
    void nextHop_loopUsesMonotonicBaseIndex() {
        session.replaceBaseRouteEntries(List.of(
                system("Gyll", 1L),
                system("Fliese", 2L),
                system("Gyll", 1L),
                system("Fliese", 2L)));
        session.applyKnownCurrentSystem("Gyll", 1L, null);
        session.applyKnownCurrentSystem("Fliese", 2L, null);
        session.applyKnownCurrentSystem("Gyll", 1L, null);
        assertEquals(2, session.getCurrentBaseIndex());
        assertEquals("Fliese", RouteTabPanel.nextRouteDestinationSystemName(session));
        // Live override from earlier on the loop must still search from the session cursor.
        assertEquals("Fliese",
                RouteTabPanel.nextRouteDestinationSystemName(
                        session.getBaseRouteEntries(), "Gyll", 1L, session.getCurrentBaseIndex()));
    }

    private static RouteEntry system(String name, long address) {
        RouteEntry e = new RouteEntry();
        e.systemName = name;
        e.systemAddress = address;
        e.isBodyRow = false;
        return e;
    }

    private static final class NoOpJumpFlash implements RouteJumpFlashHandle {
        @Override
        public boolean isTimerRunning() {
            return false;
        }

        @Override
        public void startTimer() {
        }

        @Override
        public void stopTimer() {
        }
    }
}
