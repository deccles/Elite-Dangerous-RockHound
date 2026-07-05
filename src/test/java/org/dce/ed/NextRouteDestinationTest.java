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
    void nextHop_atEndOfRoute_returnsNull() {
        session.replaceBaseRouteEntries(List.of(
                system("NGC 6153 Sector KX-T b3-1", 100L),
                system("Khun", 300L)));
        session.applyKnownCurrentSystem("Khun", 300L, null);

        assertNull(RouteTabPanel.nextRouteDestinationSystemName(session));
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
