package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.position.KeplerOrbitRing;
import org.junit.jupiter.api.Test;

/** Higher-resolution map rings must stay centred on the model-authoritative world ring. */
class ModelMapRingResampleTest {

    private static final double LS = 299_792_458.0;

    @Test
    void higherSampleCount_keepsPhaseZeroWorldPointOnParentAnchor() {
        Instant t = Instant.EPOCH;
        OrbitalElements orbit = new OrbitalElements(
                5410.0 * LS, 0.42, 33.0, 10.0, 210.0, 0.0, 365.25 * 86400.0, t);
        var rel128 = KeplerOrbitRing.ringForBody(7, 0, orbit, t, 128);
        double[] parent = new double[] { 2.0e11, -4.0e10, 1.0e9 };
        double[] w0 = new double[] {
                parent[0] + rel128.pointsMetres().get(0)[0],
                parent[1] + rel128.pointsMetres().get(0)[1],
                parent[2] + rel128.pointsMetres().get(0)[2]
        };

        var rel384 = KeplerOrbitRing.ringForBody(7, 0, orbit, t, 384);
        double[] r0 = rel384.pointsMetres().get(0);
        double[] inferredParent = new double[] { w0[0] - r0[0], w0[1] - r0[1], w0[2] - r0[2] };
        assertEquals(parent[0], inferredParent[0], 1.0);
        assertEquals(parent[1], inferredParent[1], 1.0);
        assertEquals(parent[2], inferredParent[2], 1.0);
    }

    @Test
    void gasGiantLikeOrbit_firstVertexStableAcrossSampleCounts() {
        Instant t = Instant.EPOCH;
        OrbitalElements orbit = new OrbitalElements(1046.0 * LS, 0.18, 12.0, 5.0, 90.0, 0.0, 200.0 * 86400.0, t);
        var rel128 = KeplerOrbitRing.ringForBody(6, 0, orbit, t, 128);
        var rel384 = KeplerOrbitRing.ringForBody(6, 0, orbit, t, 384);
        double[] a = rel128.pointsMetres().get(0);
        double[] b = rel384.pointsMetres().get(0);
        assertTrue(Math.hypot(a[0] - b[0], Math.hypot(a[1] - b[1], a[2] - b[2])) < 1.0);
    }
}
