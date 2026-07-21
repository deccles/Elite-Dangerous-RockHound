package org.dce.ed.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RouteFuelPredictionTest {

    /** unladen 100 t, main tank 8 t, optMass 100 t, maxFuel 8 t, r=12 (A-rated), p=2.0 (class 2). */
    private static RouteFuelPrediction.ShipFuelProfile profile(boolean scoop) {
        return new RouteFuelPrediction.ShipFuelProfile(
                100, 8, 0, 100, 8, 12, 2.0, scoop, 0);
    }

    /** Route rows on the x-axis, 20 Ly apart; row 0 carries the CURRENT marker. */
    private static List<RouteEntry> route(String... starClasses) {
        List<RouteEntry> rows = new ArrayList<>();
        for (int i = 0; i < starClasses.length; i++) {
            RouteEntry e = new RouteEntry();
            e.index = i;
            e.systemName = "Sys" + i;
            e.starClass = starClasses[i];
            e.x = 20.0 * i;
            e.y = 0.0;
            e.z = 0.0;
            e.markerKind = i == 0 ? RouteMarkerKind.CURRENT : RouteMarkerKind.NONE;
            rows.add(e);
        }
        return rows;
    }

    @Test
    void fuelForJumpMatchesHyperspaceEquation() {
        // 12e-3 * (20 Ly * 108 t / 100 t)^2 = 0.012 * 21.6^2
        double fuel = RouteFuelPrediction.fuelForJump(profile(false), 20, 108);
        assertEquals(0.012 * 21.6 * 21.6, fuel, 1e-9);
    }

    @Test
    void noScoopShipMarksLastReachableThenRed() {
        // Full tank (8 t): hop 1 costs ~5.60 t, hop 2 needs ~5.03 t but only ~2.40 t remain.
        RouteFuelPrediction.Result r = RouteFuelPrediction.simulate(
                route("M", "M", "M", "M"), profile(false), 8.0, 0.0);
        assertEquals(RouteFuelPrediction.RowFuelState.LAST_REACHABLE, r.stateAt(1));
        assertEquals(RouteFuelPrediction.RowFuelState.UNREACHABLE, r.stateAt(2));
        assertEquals(RouteFuelPrediction.RowFuelState.UNREACHABLE, r.stateAt(3));
        assertTrue(r.fuelOnArrivalAt(1) > 2.3 && r.fuelOnArrivalAt(1) < 2.5);
    }

    @Test
    void scoopShipRefuelsAtScoopableStarsAndReachesFarther() {
        // Same route, but hop 1 is a scoopable K star: tank resets to full there,
        // so hop 2 succeeds and the dry point moves one system down the route.
        RouteFuelPrediction.Result r = RouteFuelPrediction.simulate(
                route("M", "K", "N", "N"), profile(true), 8.0, 0.0);
        assertEquals(RouteFuelPrediction.RowFuelState.REACHABLE, r.stateAt(1));
        assertEquals(RouteFuelPrediction.RowFuelState.LAST_REACHABLE, r.stateAt(2));
        assertEquals(RouteFuelPrediction.RowFuelState.UNREACHABLE, r.stateAt(3));
        assertTrue(r.assumesScooping());
    }

    @Test
    void legLongerThanMaxFuelPerJumpIsUnreachableEvenWithFullTank() {
        // maxFuelPerJump 2 t: hop 1 needs ~5.6 t — impossible regardless of the 8 t tank.
        RouteFuelPrediction.ShipFuelProfile p = new RouteFuelPrediction.ShipFuelProfile(
                100, 8, 0, 100, 2, 12, 2.0, false, 0);
        RouteFuelPrediction.Result r = RouteFuelPrediction.simulate(route("M", "M"), p, 8.0, 0.0);
        assertEquals(RouteFuelPrediction.RowFuelState.LAST_REACHABLE, r.stateAt(0));
        assertEquals(RouteFuelPrediction.RowFuelState.UNREACHABLE, r.stateAt(1));
    }

    @Test
    void noCurrentMarkerMeansNoPrediction() {
        List<RouteEntry> rows = route("M", "M");
        rows.get(0).markerKind = RouteMarkerKind.NONE;
        assertNull(RouteFuelPrediction.simulate(rows, profile(false), 8.0, 0.0));
    }

    @Test
    void guardianBoosterReducesFuelCost() {
        RouteFuelPrediction.ShipFuelProfile boosted = new RouteFuelPrediction.ShipFuelProfile(
                100, 8, 0, 100, 8, 12, 2.0, false, 10.5);
        double plain = RouteFuelPrediction.fuelForJump(profile(false), 20, 108);
        double withBooster = RouteFuelPrediction.fuelForJump(boosted, 20, 108);
        assertTrue(withBooster < plain);
    }
}
