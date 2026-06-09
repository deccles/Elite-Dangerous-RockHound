package org.dce.ed.util;

import java.util.ArrayList;
import java.util.List;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmap.MapViewProjection;

/**
 * Equatorial planetary ring circles in world metres, oriented from journal orbit frame + {@link BodyInfo#getAxialTilt()}.
 */
public final class PlanetaryRingWorldGeometry {

    private static final int MIN_RING_SEGMENTS = 48;
    private static final int MAX_RING_SEGMENTS = 160;

    private PlanetaryRingWorldGeometry() {
    }

    /** Unit spin axis (ring plane normal) in parent/world coordinates. */
    public static double[] spinAxisUnit(BodyInfo body) {
        double[] orbitNormal = orbitPlaneNormalUnit(body);
        double[] tiltAxis = normalize(cross(orbitNormal, new double[] { 0, 0, 1 }));
        if (tiltAxis == null) {
            tiltAxis = normalize(cross(orbitNormal, new double[] { 1, 0, 0 }));
        }
        if (tiltAxis == null) {
            return orbitNormal;
        }
        double axialDeg = 0;
        if (body != null && body.getAxialTilt() != null && Double.isFinite(body.getAxialTilt().doubleValue())) {
            axialDeg = body.getAxialTilt().doubleValue();
        }
        return normalize(rodrigues(orbitNormal, tiltAxis, Math.toRadians(axialDeg)));
    }

    /**
     * Closed ring loop in map view coordinates (metres, same space as {@code BodyDot.wx}/{@code wy}).
     */
    public static double[][] ringLoopMapView(
            double[] hostWorldMetres,
            BodyInfo body,
            double radiusMetres,
            int proj0,
            int proj1,
            int viewTiltDeg,
            int segments) {
        if (hostWorldMetres == null || body == null || !(radiusMetres > 0) || !Double.isFinite(radiusMetres)) {
            return new double[0][0];
        }
        double[] spin = spinAxisUnit(body);
        double[] tiltAxis = normalize(cross(spin, orbitPlaneNormalUnit(body)));
        if (tiltAxis == null) {
            tiltAxis = normalize(cross(spin, new double[] { 1, 0, 0 }));
        }
        if (tiltAxis == null) {
            return new double[0][0];
        }
        double[] u = normalize(cross(spin, tiltAxis));
        if (u == null) {
            return new double[0][0];
        }
        double[] v = cross(spin, u);
        int n = Math.max(MIN_RING_SEGMENTS, Math.min(MAX_RING_SEGMENTS, segments));
        double[][] out = new double[n + 1][2];
        for (int i = 0; i <= n; i++) {
            double phi = (Math.PI * 2.0 * i) / n;
            double cos = Math.cos(phi);
            double sin = Math.sin(phi);
            double ox = radiusMetres * (cos * u[0] + sin * v[0]);
            double oy = radiusMetres * (cos * u[1] + sin * v[1]);
            double oz = radiusMetres * (cos * u[2] + sin * v[2]);
            double[] world = {
                    SystemOrbitGeometry.worldAxisMetres(hostWorldMetres, 0) + ox,
                    SystemOrbitGeometry.worldAxisMetres(hostWorldMetres, 1) + oy,
                    hostWorldMetres.length >= 3
                            ? SystemOrbitGeometry.worldAxisMetres(hostWorldMetres, 2) + oz
                            : oz
            };
            double[] map = MapViewProjection.projectFromPositionMetres(world, proj0, proj1, viewTiltDeg);
            out[i][0] = map[0];
            out[i][1] = map[1];
        }
        return out;
    }

    public static int segmentsForRadiusPx(double radiusPx) {
        if (!Double.isFinite(radiusPx) || radiusPx <= 0) {
            return MIN_RING_SEGMENTS;
        }
        return Math.max(MIN_RING_SEGMENTS, Math.min(MAX_RING_SEGMENTS, (int) Math.ceil(radiusPx / 4.0)));
    }

    private static double[] orbitPlaneNormalUnit(BodyInfo body) {
        double inc = body != null ? SystemOrbitGeometry.angleRad(body.getOrbitalInclination()) : 0;
        double om = body != null ? SystemOrbitGeometry.angleRad(body.getAscendingNode()) : 0;
        double sinI = Math.sin(inc);
        double cosI = Math.cos(inc);
        double sinOm = Math.sin(om);
        double cosOm = Math.cos(om);
        return normalize(new double[] { sinI * sinOm, -sinI * cosOm, cosI });
    }

    static double[] rodrigues(double[] vector, double[] axisUnit, double angleRad) {
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        double[] cross = cross(axisUnit, vector);
        double dot = dot(axisUnit, vector);
        return new double[] {
                vector[0] * cos + cross[0] * sin + axisUnit[0] * dot * (1 - cos),
                vector[1] * cos + cross[1] * sin + axisUnit[1] * dot * (1 - cos),
                vector[2] * cos + cross[2] * sin + axisUnit[2] * dot * (1 - cos)
        };
    }

    static double[] cross(double[] a, double[] b) {
        return new double[] {
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] normalize(double[] v) {
        if (v == null) {
            return null;
        }
        double len = Math.hypot(v[0], Math.hypot(v[1], v[2]));
        if (!Double.isFinite(len) || len < 1e-12) {
            return null;
        }
        return new double[] { v[0] / len, v[1] / len, v[2] / len };
    }
}
