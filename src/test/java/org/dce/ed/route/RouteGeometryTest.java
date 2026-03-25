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
}
