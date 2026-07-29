package org.dce.ed.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.dce.ed.route.RouteSession;
import org.junit.jupiter.api.Test;

class FleetCarrierSessionMapperTest {

    @Test
    void applyToRouteSession_prefersOwnedCarrierLocationOverStaleCurrent() {
        RouteSession session = new RouteSession(null, j -> false);

        FleetCarrierSessionData d = new FleetCarrierSessionData();
        d.setCurrentSystemName("Friend Carrier System");
        d.setCurrentSystemAddress(999L);
        d.setOwnedCarrierId(3714348544L);
        d.setOwnedCarrierSystemName("My Carrier System");
        d.setOwnedCarrierSystemAddress(638709240514L);

        FleetCarrierSessionMapper.applyToRouteSession(session, d);

        assertEquals("My Carrier System", session.getCurrentSystemName());
        assertEquals(638709240514L, session.getCurrentSystemAddress());
    }
}
