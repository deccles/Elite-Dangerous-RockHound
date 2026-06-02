package org.dce.systemmodel.position;

import org.dce.systemmodel.journal.OrbitalElements;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeplerMathTest {

    @Test
    void keplerDisplacementIsFinite() {
        OrbitalElements o = new OrbitalElements(
                4.22630339860916138e11, 0.01, 5, 10, 20, 30, 312515056.13327,
                Instant.parse("2026-01-01T00:00:00Z"));
        double M = KeplerMath.evolvedMeanAnomalyRadians(o, Instant.now());
        double[] rel = KeplerMath.keplerDisplacementMetres(o, M);
        assertNotNull(rel);
        assertTrue(Double.isFinite(rel[0]));
        assertTrue(Double.isFinite(rel[1]));
        assertTrue(Double.isFinite(rel[2]));
    }
}
