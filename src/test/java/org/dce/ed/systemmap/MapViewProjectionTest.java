package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MapViewProjectionTest {

    @Test
    void thirdAxisIndex_picksUnusedAxis() {
        assertEquals(2, MapViewProjection.thirdAxisIndex(0, 1));
        assertEquals(1, MapViewProjection.thirdAxisIndex(0, 2));
        assertEquals(0, MapViewProjection.thirdAxisIndex(1, 2));
    }

    @Test
    void projectWorldComponents_zeroTilt_matchesMapPlane() {
        double[] v = MapViewProjection.projectWorldComponents(10.0, 20.0, 30.0, 0, 1, 0);
        assertEquals(10.0, v[0], 1e-9);
        assertEquals(20.0, v[1], 1e-9);
    }

    @Test
    void projectWorldComponents_ninetyTilt_usesDepthOnVertical() {
        double[] v = MapViewProjection.projectWorldComponents(10.0, 20.0, 30.0, 0, 1, 90);
        assertEquals(10.0, v[0], 1e-9);
        assertEquals(30.0, v[1], 1e-6);
    }

    @Test
    void projectWorldComponents_inclinedOrbitSpreadIncreasesWithTilt() {
        double min0 = Double.POSITIVE_INFINITY;
        double max0 = 0.0;
        double min90 = Double.POSITIVE_INFINITY;
        double max90 = 0.0;
        double a = 1e11;
        double e = 0.35;
        double inc = Math.toRadians(89.0);
        for (int i = 0; i < 32; i++) {
            double M = (Math.PI * 2.0 * i) / 32;
            double[] rel = sampleKeplerOffset(a, e, inc, M);
            double x = rel[0];
            double y = rel[1];
            double z = rel[2];
            double[] p0 = MapViewProjection.projectWorldComponents(x, y, z, 0, 1, 0);
            double[] p90 = MapViewProjection.projectWorldComponents(x, y, z, 0, 1, 90);
            double r0 = Math.hypot(p0[0], p0[1]);
            double r90 = Math.hypot(p90[0], p90[1]);
            min0 = Math.min(min0, r0);
            max0 = Math.max(max0, r0);
            min90 = Math.min(min90, r90);
            max90 = Math.max(max90, r90);
        }
        double spread0 = max0 / Math.max(min0, 1.0);
        double spread90 = max90 / Math.max(min90, 1.0);
        assertTrue(spread0 > 8.0, "edge-on at 0° tilt: " + spread0);
        assertTrue(spread90 < spread0 * 0.55, "90° tilt should open projection: 0=" + spread0 + " 90=" + spread90);
    }

    private static double[] sampleKeplerOffset(double a, double e, double inc, double M) {
        double E = M;
        for (int i = 0; i < 12; i++) {
            E = M + e * Math.sin(E);
        }
        double cosE = Math.cos(E);
        double sinE = Math.sin(E);
        double sqrtTerm = Math.sqrt((1 + e) / Math.max(1e-12, (1 - e)));
        double nu = 2 * Math.atan2(sqrtTerm * sinE, cosE - e);
        double r = a * (1 - e * cosE);
        double u = nu;
        double cosI = Math.cos(inc);
        double sinI = Math.sin(inc);
        double x = r * Math.cos(u);
        double y = r * Math.sin(u) * cosI;
        double z = r * Math.sin(u) * sinI;
        return new double[] { x, y, z };
    }
}
