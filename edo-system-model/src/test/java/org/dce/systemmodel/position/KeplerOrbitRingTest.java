package org.dce.systemmodel.position;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.dce.systemmodel.journal.OrbitalElements;
import org.junit.jupiter.api.Test;

class KeplerOrbitRingTest {

    @Test
    void ringDrawn_whenSemiMajorAxisPresentButPeriodMissing() {
        Instant t = Instant.EPOCH;
        OrbitalElements orbit = new OrbitalElements(1.5e11, 0, 0, 0, 0, 0, 0, t);
        var ring = KeplerOrbitRing.ringForBody(3, -50002, orbit, t);
        assertFalse(ring.pointsMetres().isEmpty(), "static ring from SMA when period is zero");
        assertTrue(ring.pointsMetres().size() >= 64);
    }

    @Test
    void highEccentricityRing_usesRequestedSampleCount() {
        Instant t = Instant.EPOCH;
        OrbitalElements orbit = new OrbitalElements(5.0e11, 0.72, 0, 0, 0, 0, 0, t);
        var ring = KeplerOrbitRing.ringForBody(6, 0, orbit, t, 384);
        assertEquals(384, ring.pointsMetres().size());
    }
}
