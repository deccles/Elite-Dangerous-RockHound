package org.dce.systemmodel.position;

import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.model.OrbitRing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class KeplerOrbitRing {

    private static final int RING_SAMPLES_DEFAULT = 128;
    private static final int RING_SAMPLES_MIN = 64;
    private static final int RING_SAMPLES_MAX = 768;

    private KeplerOrbitRing() {
    }

    public static OrbitRing ringForBody(int bodyId, int parentId, OrbitalElements orbit, Instant t) {
        return ringForBody(bodyId, parentId, orbit, t, RING_SAMPLES_DEFAULT);
    }

    public static OrbitRing ringForBody(int bodyId, int parentId, OrbitalElements orbit, Instant t, int samples) {
        int n = Math.max(RING_SAMPLES_MIN, Math.min(RING_SAMPLES_MAX, samples));
        List<double[]> pts = new ArrayList<>(n);
        if (!(orbit.semiMajorAxisM() > 0) || Double.isNaN(orbit.semiMajorAxisM())) {
            return new OrbitRing(bodyId, parentId, List.of());
        }
        double period = orbit.orbitalPeriodSec();
        double M0 = Math.toRadians(orbit.meanAnomalyDeg());
        long epochMs = orbit.orbitalEpoch() != null ? orbit.orbitalEpoch().toEpochMilli() : 0L;
        double nDot;
        double dtBase;
        if (period > 1e-6 && Double.isFinite(period)) {
            nDot = (Math.PI * 2.0) / period;
            dtBase = t != null ? (t.toEpochMilli() - epochMs) / 1000.0 : 0.0;
        } else {
            /* SMA without period: draw static closed path (map ring shape, not time evolution). */
            nDot = 0.0;
            dtBase = 0.0;
        }
        double Mbase = KeplerMath.wrapToTwoPi(M0 + nDot * dtBase);
        for (int i = 0; i < n; i++) {
            double phase = (Math.PI * 2.0 * i) / n;
            double M = KeplerMath.wrapToTwoPi(Mbase + phase);
            double[] rel = KeplerMath.keplerDisplacementMetres(orbit, M);
            if (rel != null) {
                pts.add(rel);
            }
        }
        return new OrbitRing(bodyId, parentId, List.copyOf(pts));
    }
}
