package org.dce.ed.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RouteGeometryTest {

    @Test
    void pointToSegmentDistanceSquared_interiorPoint() {
        double[] p = { 1, 0, 0 };
        double[] v = { 0, 0, 0 };
        double[] w = { 2, 0, 0 };
        double d2 = RouteGeometry.pointToSegmentDistanceSquared(p, v, w);
        assertEquals(0.0, d2, 1e-9);
    }

    @Test
    void pointToSegmentDistanceSquared_beyondEnd() {
        double[] p = { 3, 0, 0 };
        double[] v = { 0, 0, 0 };
        double[] w = { 1, 0, 0 };
        double d2 = RouteGeometry.pointToSegmentDistanceSquared(p, v, w);
        assertEquals(4.0, d2, 1e-9);
    }

    @Test
    void bestInsertionIndexByCoords_degenerateSegments_skipsToEnd() {
        java.util.List<RouteEntry> list = new java.util.ArrayList<>();
        RouteEntry a = new RouteEntry();
        a.systemName = "A";
        a.x = 0.0;
        a.y = 0.0;
        a.z = 0.0;
        RouteEntry b = new RouteEntry();
        b.systemName = "B";
        list.add(a);
        list.add(b);
        int idx = RouteGeometry.bestInsertionIndexByCoords(list, new Double[] { 0.5, 0.0, 0.0 });
        assertEquals(2, idx);
    }

    @Test
    void findSystemRow_staleAddressAndNewName_prefersNameOrAbsent() {
        java.util.List<RouteEntry> list = new java.util.ArrayList<>();
        RouteEntry old = new RouteEntry();
        old.systemName = "HIP 12099";
        old.systemAddress = 111L;
        list.add(old);

        assertEquals(0, RouteGeometry.findSystemRow(list, "HIP 12099", 111L));
        // Name-only update left a stale address: must not keep CURRENT on the old hop.
        assertEquals(-1, RouteGeometry.findSystemRow(list, "Sol", 111L));
        RouteEntry sol = new RouteEntry();
        sol.systemName = "Sol";
        sol.systemAddress = 222L;
        list.add(sol);
        assertEquals(1, RouteGeometry.findSystemRow(list, "Sol", 111L));
    }

    @Test
    void recomputeLegDistances_skipsDestinationBodyRows() {
        RouteEntry ross104 = new RouteEntry();
        ross104.systemName = "Ross 104";
        ross104.x = 0.0;
        ross104.y = 0.0;
        ross104.z = 0.0;

        RouteEntry bunchCity = RouteEntry.syntheticBody("Bunch City");

        RouteEntry ministry = new RouteEntry();
        ministry.systemName = "Ministry";
        ministry.x = 3.0;
        ministry.y = 4.0;
        ministry.z = 0.0;

        java.util.List<RouteEntry> rows = new java.util.ArrayList<>();
        rows.add(ross104);
        rows.add(bunchCity);
        rows.add(ministry);

        RouteGeometry.recomputeLegDistances(rows);

        assertEquals(5.0, ministry.distanceLy, 1e-9);
    }

    @Test
    void cumulativeDistanceLy_skipsDestinationBodyRows() {
        RouteEntry ross104 = new RouteEntry();
        RouteEntry bunchCity = RouteEntry.syntheticBody("Bunch City");
        RouteEntry ministry = new RouteEntry();
        ministry.distanceLy = 5.0;

        java.util.List<RouteEntry> rows = java.util.List.of(ross104, bunchCity, ministry);

        assertEquals(5.0, RouteGeometry.cumulativeDistanceLy(rows, 0, 2), 1e-9);
    }
}
