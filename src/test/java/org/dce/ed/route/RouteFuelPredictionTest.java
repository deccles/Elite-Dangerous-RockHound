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
        assertEquals(RouteFuelPrediction.BlockReason.TANK_EMPTY, r.blockReason());
        assertTrue(r.fuelOnArrivalAt(1) > 2.3 && r.fuelOnArrivalAt(1) < 2.5);
    }

    @Test
    void scoopShipRefuelsAtScoopableStarsAndReachesFarther() {
        // Same route, but hop 1 is a scoopable K star: tank resets to full there,
        // so hop 2 succeeds and the dry point moves one system down the route.
        // Use T (brown dwarf), not N — N would apply a 4× jet-cone boost on departure.
        RouteFuelPrediction.Result r = RouteFuelPrediction.simulate(
                route("M", "K", "T", "T"), profile(true), 8.0, 0.0);
        assertEquals(RouteFuelPrediction.RowFuelState.REACHABLE, r.stateAt(1));
        assertEquals(RouteFuelPrediction.RowFuelState.LAST_REACHABLE, r.stateAt(2));
        assertEquals(RouteFuelPrediction.RowFuelState.UNREACHABLE, r.stateAt(3));
        assertTrue(r.assumesScooping());
    }

    @Test
    void scoopShipIgnoresScoopWhenConsiderFuelScoopFalse() {
        RouteFuelPrediction.Result withScoop = RouteFuelPrediction.simulate(
                route("M", "K", "T", "T"), profile(true), 8.0, 0.0, true);
        RouteFuelPrediction.Result ignoreScoop = RouteFuelPrediction.simulate(
                route("M", "K", "T", "T"), profile(true), 8.0, 0.0, false);
        assertTrue(withScoop.assumesScooping());
        assertTrue(!ignoreScoop.assumesScooping());
        assertEquals(RouteFuelPrediction.RowFuelState.REACHABLE, withScoop.stateAt(1));
        assertEquals(RouteFuelPrediction.RowFuelState.LAST_REACHABLE, ignoreScoop.stateAt(1));
        assertEquals(RouteFuelPrediction.RowFuelState.UNREACHABLE, ignoreScoop.stateAt(2));
    }

    @Test
    void legLongerThanMaxFuelPerJumpIsBeyondRangeEvenWithFullTank() {
        // maxFuelPerJump 2 t: hop 1 needs ~5.6 t — impossible regardless of the 8 t tank.
        RouteFuelPrediction.ShipFuelProfile p = new RouteFuelPrediction.ShipFuelProfile(
                100, 8, 0, 100, 2, 12, 2.0, false, 0);
        RouteFuelPrediction.Result r = RouteFuelPrediction.simulate(route("M", "M"), p, 8.0, 0.0);
        assertEquals(RouteFuelPrediction.RowFuelState.LAST_REACHABLE, r.stateAt(0));
        assertEquals(RouteFuelPrediction.RowFuelState.BEYOND_JUMP_RANGE, r.stateAt(1));
        assertEquals(RouteFuelPrediction.BlockReason.JUMP_TOO_FAR, r.blockReason());
    }

    @Test
    void tankEmptyIsDistinctFromJumpTooFar() {
        // Full tank: hop 1 costs ~5.60 t, hop 2 needs ~5.03 t but only ~2.40 t remain.
        RouteFuelPrediction.Result r = RouteFuelPrediction.simulate(
                route("M", "M", "M", "M"), profile(false), 8.0, 0.0);
        assertEquals(RouteFuelPrediction.BlockReason.TANK_EMPTY, r.blockReason());
        assertEquals(RouteFuelPrediction.RowFuelState.UNREACHABLE, r.stateAt(2));
    }

    @Test
    void noCurrentMarkerMeansNoPrediction() {
        List<RouteEntry> rows = route("M", "M");
        rows.get(0).markerKind = RouteMarkerKind.NONE;
        assertNull(RouteFuelPrediction.simulate(rows, profile(false), 8.0, 0.0));
    }

    @Test
    void applyFsdCraftUpdatesOptimalMassAndClearsStaleMaxJump() {
        RouteFuelPrediction.ShipFuelProfile before = new RouteFuelPrediction.ShipFuelProfile(
                1318.813599, 32, 1.07, 2808, 8.30, 13, 2.6, false, 0, 25.373886);
        // Minimal EngineerCraft raw JSON matching a Farseer G5 finish (opt 3224).
        com.google.gson.JsonObject raw = com.google.gson.JsonParser.parseString("""
                {"event":"EngineerCraft","Slot":"FrameShiftDrive",
                 "Module":"int_hyperdrive_overcharge_size6_class5",
                 "Modifiers":[{"Label":"FSDOptimalMass","Value":3224.0,"OriginalValue":2000.0}]}
                """).getAsJsonObject();
        org.dce.ed.logreader.event.EngineerCraftEvent craft =
                new org.dce.ed.logreader.event.EngineerCraftEvent(
                        java.time.Instant.EPOCH, raw, "FrameShiftDrive",
                        "int_hyperdrive_overcharge_size6_class5", "Felicity Farseer", 300100,
                        "FSD_LongRange", 128673694, 5, 1.0, "", "special_fsd_heavy", "Mass Manager",
                        List.of());
        RouteFuelPrediction.ShipFuelProfile after = RouteFuelPrediction.applyFsdCraft(before, craft);
        assertEquals(3224.0, after.optimalMass, 1e-6);
        assertTrue(after.maxJumpRangeLy() > 28.0 && after.maxJumpRangeLy() < 30.0);
        // 28 Ly hop must now be within maxFuelPerJump at full tank.
        double cost = RouteFuelPrediction.fuelForJump(after, 28.045, 1318.813599 + 32);
        assertTrue(cost < after.maxFuelPerJump);
    }

    @Test
    void guardianBoosterReducesFuelCost() {
        RouteFuelPrediction.ShipFuelProfile boosted = new RouteFuelPrediction.ShipFuelProfile(
                100, 8, 0, 100, 8, 12, 2.0, false, 10.5);
        double plain = RouteFuelPrediction.fuelForJump(profile(false), 20, 108);
        double withBooster = RouteFuelPrediction.fuelForJump(boosted, 20, 108);
        assertTrue(withBooster < plain);
    }

    @Test
    void neutronDepartureAllowsJumpThatExceedsUnboostedMaxFuel() {
        // maxFuelPerJump 2 t: 20 Ly hop needs ~5.6 t unboosted — beyond FSD.
        // Leaving a neutron (4×) treats the hop as 5 Ly → affordable.
        RouteFuelPrediction.ShipFuelProfile p = new RouteFuelPrediction.ShipFuelProfile(
                100, 8, 0, 100, 2, 12, 2.0, false, 0);
        RouteFuelPrediction.Result blocked = RouteFuelPrediction.simulate(
                route("M", "M"), p, 8.0, 0.0);
        assertEquals(RouteFuelPrediction.RowFuelState.BEYOND_JUMP_RANGE, blocked.stateAt(1));

        RouteFuelPrediction.Result ok = RouteFuelPrediction.simulate(route("N", "M"), p, 8.0, 0.0);
        assertEquals(RouteFuelPrediction.RowFuelState.REACHABLE, ok.stateAt(1));
        assertEquals(RouteFuelPrediction.BlockReason.NONE, ok.blockReason());
    }

    @Test
    void whiteDwarfDepartureUsesOnePointFiveMultiplier() {
        double plain = RouteFuelPrediction.fuelForJump(profile(false), 30, 108, 1.0);
        double wd = RouteFuelPrediction.fuelForJump(profile(false), 30, 108, FsdJetConeBoost.WHITE_DWARF);
        double neutron = RouteFuelPrediction.fuelForJump(profile(false), 30, 108, FsdJetConeBoost.NEUTRON);
        assertTrue(wd < plain);
        assertTrue(neutron < wd);
        assertEquals(FsdJetConeBoost.WHITE_DWARF, FsdJetConeBoost.multiplierLeaving("DA"), 1e-9);
        assertEquals(FsdJetConeBoost.NEUTRON, FsdJetConeBoost.multiplierLeaving("N"), 1e-9);
        assertEquals(1.0, FsdJetConeBoost.multiplierLeaving("K"), 1e-9);
    }
}
