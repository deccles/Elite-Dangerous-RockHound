package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;

import javax.swing.SwingUtilities;

import org.dce.ed.logreader.OwnedFleetCarrierTracker;
import org.dce.ed.logreader.event.CarrierJumpRequestEvent;
import org.junit.jupiter.api.Test;

/**
 * Two tabs ⇒ two {@link RouteSession} instances; base route lists must not be shared.
 */
class FleetCarrierTabPanelIsolationTest {

    @Test
    void shipAndFleetTabsUseDistinctRouteSessions() {
        RouteTabPanel ship = new RouteTabPanel(() -> false);
        FleetCarrierTabPanel fleet = new FleetCarrierTabPanel(() -> false);
        Object a = ship.routeSessionForTests();
        Object b = fleet.routeSessionForTests();
        assertNotSame(a, b);
    }

    @Test
    void carrierJumpRequestPopulatesBlankDestinationQuery() throws Exception {
        FleetCarrierTabPanel fleet = new FleetCarrierTabPanel(() -> false);
        CarrierJumpRequestEvent request = carrierJumpRequest("Col 285 Sector ZZ-Y b15-0");

        runOnEdtAndWait(() -> fleet.applyScheduledJumpDestinationIfNeeded(request));

        assertEquals("Col 285 Sector ZZ-Y b15-0", fleet.destinationQueryForTests());
    }

    @Test
    void carrierJumpRequestDoesNotOverwriteTypedDestinationQuery() throws Exception {
        FleetCarrierTabPanel fleet = new FleetCarrierTabPanel(() -> false);
        runOnEdtAndWait(() -> fleet.setDestinationQueryForTests("Sagittarius A*"));
        CarrierJumpRequestEvent request = carrierJumpRequest("Col 285 Sector ZZ-Y b15-0");

        runOnEdtAndWait(() -> fleet.applyScheduledJumpDestinationIfNeeded(request));

        assertEquals("Sagittarius A*", fleet.destinationQueryForTests());
    }

    @Test
    void carrierJumpRequestFromFriendCarrier_isIgnoredForDestinationQuery() throws Exception {
        OwnedFleetCarrierTracker tracker = new OwnedFleetCarrierTracker();
        tracker.onCarrierStats(111L);
        FleetCarrierTabPanel fleet = new FleetCarrierTabPanel(() -> false, tracker);
        CarrierJumpRequestEvent request = carrierJumpRequest("Col 285 Sector ZZ-Y b15-0", 999L);

        runOnEdtAndWait(() -> fleet.handleLogEvent(request));

        assertTrue(fleet.destinationQueryForTests() == null || fleet.destinationQueryForTests().isBlank());
    }

    @Test
    void carrierJumpRequestFromOwnedCarrier_populatesBlankDestinationQuery() throws Exception {
        OwnedFleetCarrierTracker tracker = new OwnedFleetCarrierTracker();
        tracker.onCarrierStats(123456789L);
        FleetCarrierTabPanel fleet = new FleetCarrierTabPanel(() -> false, tracker);
        CarrierJumpRequestEvent request = carrierJumpRequest("Col 285 Sector ZZ-Y b15-0", 123456789L);

        runOnEdtAndWait(() -> fleet.handleLogEvent(request));

        assertEquals("Col 285 Sector ZZ-Y b15-0", fleet.destinationQueryForTests());
    }

    private static CarrierJumpRequestEvent carrierJumpRequest(String systemName) {
        return carrierJumpRequest(systemName, 123456789L);
    }

    private static CarrierJumpRequestEvent carrierJumpRequest(String systemName, long carrierId) {
        return new CarrierJumpRequestEvent(
                Instant.parse("2026-05-26T20:45:00Z"),
                null,
                "FleetCarrier",
                carrierId,
                systemName,
                systemName + " A",
                12345L,
                1,
                Instant.parse("2026-05-26T21:00:00Z"));
    }

    private static void runOnEdtAndWait(Runnable runnable) throws InterruptedException, InvocationTargetException {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeAndWait(runnable);
        }
    }
}
