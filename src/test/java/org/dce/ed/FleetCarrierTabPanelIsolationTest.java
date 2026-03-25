package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.route.RouteEntry;
import org.dce.ed.route.RouteSession;
import org.junit.jupiter.api.Test;

/**
 * Two tabs ⇒ two {@link RouteSession} instances; base route lists must not be shared.
 */
class FleetCarrierTabPanelIsolationTest {

    @Test
    void shipAndFleetTabsUseDistinctRouteSessions() {
        RouteTabPanel ship = new RouteTabPanel(() -> false);
        FleetCarrierTabPanel fleet = new FleetCarrierTabPanel(() -> false);
        RouteSession a = ship.routeSessionForTests();
        RouteSession b = fleet.routeSessionForTests();
        assertNotSame(a, b);
        assertNotSame(a.getBaseRouteEntries(), b.getBaseRouteEntries());
    }

    @Test
    void mutatingOneSessionBaseDoesNotAffectOther() {
        RouteSession s1 = new RouteSession(new NoOpFlash(), j -> false);
        RouteSession s2 = new RouteSession(new NoOpFlash(), j -> true);
        RouteEntry e = new RouteEntry();
        e.systemName = "X";
        e.systemAddress = 1L;
        s1.applyNavRouteReloadParsed(java.util.List.of(e));
        assertTrue(s2.getBaseRouteEntries().isEmpty());
    }

    private static final class NoOpFlash implements org.dce.ed.route.RouteJumpFlashHandle {
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
