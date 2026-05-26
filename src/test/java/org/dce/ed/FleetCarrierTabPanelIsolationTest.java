package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;

import javax.swing.SwingUtilities;

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

    private static CarrierJumpRequestEvent carrierJumpRequest(String systemName) {
        return new CarrierJumpRequestEvent(
                Instant.parse("2026-05-26T20:45:00Z"),
                null,
                "FleetCarrier",
                123456789L,
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
