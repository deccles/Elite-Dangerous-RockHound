package org.dce.ed.systemmap;

import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.model.OrbitRing;
import org.dce.systemmodel.position.KeplerOrbitRing;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Probe chord lengths for tilted Kepler ring projection (diagnostic for string-art orbits). */
class TiltedOrbitChordProbeTest {

    private static final double LS = 299_792_458.0;

    @Test
    void highInclinationPlanetAt20kLs_23DegTilt_maxChordIsSmallVersusRing() {
        double a = 20_381.0 * LS;
        OrbitalElements orbit = new OrbitalElements(
                a, 0.35, 89.0, 45.0, 120.0, 0.0, 365.25 * 86400.0, Instant.EPOCH);
        OrbitRing ring = KeplerOrbitRing.ringForBody(1, 0, orbit, Instant.EPOCH);
        int n = ring.pointsMetres().size();
        double[] sx = new double[n];
        double[] sy = new double[n];
        for (int i = 0; i < n; i++) {
            double[] w = ring.pointsMetres().get(i);
            double[] p = MapViewProjection.projectWorldComponents(w[0], w[1], w[2], 0, 1, 23);
            sx[i] = p[0];
            sy[i] = p[1];
        }
        double maxChord = 0.0;
        double sumChord = 0.0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double d = Math.hypot(sx[i] - sx[j], sy[i] - sy[j]);
            maxChord = Math.max(maxChord, d);
            sumChord += d;
        }
        double avgChord = sumChord / n;
        double closeChord = Math.hypot(sx[0] - sx[n - 1], sy[0] - sy[n - 1]);
        System.out.printf("n=%d avgChord=%.3e maxChord=%.3e close=%.3e max/avg=%.2f%n",
                n, avgChord, maxChord, closeChord, maxChord / avgChord);
        assertTrue(maxChord < avgChord * 8.0,
                "unexpected long chords at 23° tilt: max/avg=" + (maxChord / avgChord));
    }
}
