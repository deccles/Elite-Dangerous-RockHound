package org.dce.systemmodel.position;

import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.model.OrbitRing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class KeplerOrbitRing {

    private static final int RING_SAMPLES = 128;

    private KeplerOrbitRing() {
    }

    public static OrbitRing ringForBody(int bodyId, int parentId, OrbitalElements orbit, Instant t) {
        List<double[]> pts = new ArrayList<>(RING_SAMPLES);
        double period = orbit.orbitalPeriodSec();
        if (!(period > 1e-6)) {
            return new OrbitRing(bodyId, parentId, List.of());
        }
        double M0 = Math.toRadians(orbit.meanAnomalyDeg());
        long epochMs = orbit.orbitalEpoch() != null ? orbit.orbitalEpoch().toEpochMilli() : 0L;
        double n = (Math.PI * 2.0) / period;
        double dtBase = t != null ? (t.toEpochMilli() - epochMs) / 1000.0 : 0.0;

        for (int i = 0; i < RING_SAMPLES; i++) {
            double phase = (Math.PI * 2.0 * i) / RING_SAMPLES;
            double M = KeplerMath.wrapToTwoPi(M0 + n * dtBase + phase);
            double[] rel = KeplerMath.keplerDisplacementMetres(orbit, M);
            if (rel != null) {
                pts.add(rel);
            }
        }
        return new OrbitRing(bodyId, parentId, List.copyOf(pts));
    }
}
