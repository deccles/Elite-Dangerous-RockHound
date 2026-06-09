package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.state.BodyInfo;
import org.junit.jupiter.api.Test;

class PlanetaryRingWorldGeometryTest {

    @Test
    void ringLoopOrientationChangesWithViewTilt() {
        BodyInfo body = new BodyInfo();
        body.setOrbitalInclination(0.45);
        body.setAscendingNode(1.2);
        body.setAxialTilt(26.0);
        double[] host = { 5e11, 2e11, 0.0 };
        double radius = 2e8;
        double[][] flat = PlanetaryRingWorldGeometry.ringLoopMapView(host, body, radius, 0, 1, 0, 64);
        double[][] tilted = PlanetaryRingWorldGeometry.ringLoopMapView(host, body, radius, 0, 1, 45, 64);
        assertTrue(flat.length > 4);
        assertTrue(tilted.length > 4);
        double flatSpanY = span(flat, 1);
        double tiltSpanY = span(tilted, 1);
        assertNotEquals(flatSpanY, tiltSpanY, 1.0);
    }

    @Test
    void spinAxisIsUnitLength() {
        BodyInfo body = new BodyInfo();
        body.setOrbitalInclination(0.3);
        body.setAscendingNode(0.8);
        body.setAxialTilt(15.0);
        double[] spin = PlanetaryRingWorldGeometry.spinAxisUnit(body);
        double len = Math.hypot(spin[0], Math.hypot(spin[1], spin[2]));
        assertTrue(Math.abs(len - 1.0) < 1e-6);
    }

    private static double span(double[][] loop, int axis) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double[] p : loop) {
            min = Math.min(min, p[axis]);
            max = Math.max(max, p[axis]);
        }
        return max - min;
    }
}
