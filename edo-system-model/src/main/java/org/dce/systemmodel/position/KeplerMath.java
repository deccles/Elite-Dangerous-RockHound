package org.dce.systemmodel.position;

import org.dce.systemmodel.journal.OrbitalElements;

import java.time.Instant;

public final class KeplerMath {

    private KeplerMath() {
    }

    public static double[] keplerDisplacementMetres(OrbitalElements orbit, double M) {
        double a = orbit.semiMajorAxisM();
        if (!(a > 0) || Double.isNaN(a)) {
            return null;
        }
        double e = clamp(orbit.eccentricity(), 0, 0.999999);
        double inc = Math.toRadians(orbit.orbitalInclinationDeg());
        double om = Math.toRadians(orbit.ascendingNodeDeg());
        double wp = Math.toRadians(orbit.periapsisArgDeg());
        double Mw = wrapToTwoPi(M);

        double E = solveKepler(Mw, e);
        double cosE = Math.cos(E);
        double sinE = Math.sin(E);
        double sqrtTerm = Math.sqrt(Math.max(0, (1 + e) / Math.max(1e-12, (1 - e))));
        double nu = 2 * Math.atan2(sqrtTerm * sinE, cosE - e);
        double r = a * (1 - e * cosE);
        double u = wp + nu;
        double cosOm = Math.cos(om);
        double sinOm = Math.sin(om);
        double cosI = Math.cos(inc);
        double sinI = Math.sin(inc);
        double cosU = Math.cos(u);
        double sinU = Math.sin(u);

        double x = r * (cosU * cosOm - sinU * sinOm * cosI);
        double y = r * (cosU * sinOm + sinU * cosOm * cosI);
        double z = r * (sinU * sinI);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return null;
        }
        return new double[] {x, y, z};
    }

    public static double evolvedMeanAnomalyRadians(OrbitalElements orbit, Instant now) {
        double M0 = Math.toRadians(orbit.meanAnomalyDeg());
        Instant t = now != null ? now : Instant.now();
        double pSec = orbit.orbitalPeriodSec();
        if (!(pSec > 1e-6) || !Double.isFinite(pSec)) {
            return wrapToTwoPi(M0);
        }
        long epochMs = orbit.orbitalEpoch() != null ? orbit.orbitalEpoch().toEpochMilli() : 0L;
        double dtSec = (t.toEpochMilli() - epochMs) / 1000.0;
        double n = (Math.PI * 2.0) / pSec;
        return wrapToTwoPi(M0 + n * dtSec);
    }

    public static double wrapToTwoPi(double rad) {
        if (!Double.isFinite(rad)) {
            return 0.0;
        }
        double twoPi = Math.PI * 2.0;
        double x = rad % twoPi;
        if (x < 0) {
            x += twoPi;
        }
        return x;
    }

    public static double solveKepler(double M, double e) {
        if (e < 1e-12) {
            return normAngle(M);
        }
        double E = normAngle(M);
        for (int i = 0; i < 30; i++) {
            double f = E - e * Math.sin(E) - M;
            double fp = 1 - e * Math.cos(E);
            if (Math.abs(fp) < 1e-12) {
                break;
            }
            double step = f / fp;
            E -= step;
            if (Math.abs(step) < 1e-10) {
                break;
            }
        }
        return E;
    }

    private static double normAngle(double rad) {
        if (!Double.isFinite(rad)) {
            return 0.0;
        }
        double twoPi = Math.PI * 2;
        double x = rad % twoPi;
        if (x < -Math.PI) {
            x += twoPi;
        } else if (x > Math.PI) {
            x -= twoPi;
        }
        return x;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
