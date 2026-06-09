package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.state.BodyInfo;
import org.junit.jupiter.api.Test;

class SystemOrbitGeometryOrbitStrokeColorTest {

    @Test
    void orbitStrokeSupportsTimeColoring_requiresPeriodAndMeanAnomaly() {
        BodyInfo body = new BodyInfo();
        assertFalse(SystemOrbitGeometry.orbitStrokeSupportsTimeColoring(body));
        body.setOrbitalPeriod(3600.0);
        assertFalse(SystemOrbitGeometry.orbitStrokeSupportsTimeColoring(body));
        body.setMeanAnomaly(1.2);
        assertTrue(SystemOrbitGeometry.orbitStrokeSupportsTimeColoring(body));
    }

    @Test
    void orbitPolylineNearestVertexIndex_picksClosestPoint() {
        double[] wx = { 0.0, 10.0, 0.0, -10.0 };
        double[] wy = { 10.0, 0.0, -10.0, 0.0 };
        assertEquals(0, SystemOrbitGeometry.orbitPolylineNearestVertexIndex(wx, wy, 1.0, 9.0));
        assertEquals(2, SystemOrbitGeometry.orbitPolylineNearestVertexIndex(wx, wy, -1.0, -9.0));
    }

    @Test
    void orbitStrokePolylinePhaseSeparationFraction_zeroAtAnchor() {
        assertEquals(0.0, SystemOrbitGeometry.orbitStrokePolylinePhaseSeparationFraction(0, 1, 0, 64), 1e-9);
        assertEquals(0.0, SystemOrbitGeometry.orbitStrokePolylinePhaseSeparationFraction(63, 0, 0, 64), 1e-9);
    }

    @Test
    void orbitStrokePolylinePhaseSeparationFraction_oneHalfRingAway() {
        double sep = SystemOrbitGeometry.orbitStrokePolylinePhaseSeparationFraction(31, 32, 0, 64);
        assertEquals(1.0, sep, 0.04);
    }

    @Test
    void orbitPolylineVertexMeanAnomalyRad_wrapsFullCircle() {
        assertEquals(0.0, SystemOrbitGeometry.orbitPolylineVertexMeanAnomalyRad(0, 64), 1e-12);
        assertEquals(Math.PI, SystemOrbitGeometry.orbitPolylineVertexMeanAnomalyRad(32, 64), 1e-9);
    }
}
