package org.dce.ed.systemmap;

import org.dce.ed.util.SystemOrbitGeometry;

/**
 * True-scale map view tilt: blends the map-plane vertical axis ({@code proj1}) with the unused world axis so
 * edge-on 3D orbits open on screen.
 */
public final class MapViewProjection {

    private MapViewProjection() {
    }

    /** Clamps to 0…90 inclusive. */
    public static int clampViewTiltDegrees(int degrees) {
        if (degrees <= 0) {
            return 0;
        }
        return Math.min(90, degrees);
    }

    /** The world axis index (0, 1, or 2) not used by {@code proj0} or {@code proj1}. */
    public static int thirdAxisIndex(int proj0, int proj1) {
        int a0 = clampAxis(proj0);
        int a1 = clampAxis(proj1);
        if (a0 != a1) {
            for (int a = 0; a < 3; a++) {
                if (a != a0 && a != a1) {
                    return a;
                }
            }
        }
        return a0 == 2 ? 1 : 2;
    }

    private static int clampAxis(int axis) {
        if (axis < 0) {
            return 0;
        }
        if (axis > 2) {
            return 2;
        }
        return axis;
    }

    /**
     * Projects a 3D world position (metres, indices 0=x, 1=y, 2=z) to map view coordinates {@code (u, v)}.
     * At 0°: {@code u = axis(proj0)}, {@code v = axis(proj1)}. At 90°: {@code v = axis(third)}.
     */
    public static double[] projectFromPositionMetres(double[] positionMetres, int proj0, int proj1, int viewTiltDeg) {
        if (positionMetres == null || positionMetres.length < 2) {
            return new double[] { Double.NaN, Double.NaN };
        }
        double x = SystemOrbitGeometry.worldAxisMetres(positionMetres, 0);
        double y = SystemOrbitGeometry.worldAxisMetres(positionMetres, 1);
        double z = positionMetres.length >= 3 ? SystemOrbitGeometry.worldAxisMetres(positionMetres, 2) : 0.0;
        return projectWorldComponents(x, y, z, proj0, proj1, viewTiltDeg);
    }

    /** {@code parent + rel} in world metres, then {@link #projectFromPositionMetres}. */
    public static double[] projectSum(double[] parentMetres, double[] relMetres, int proj0, int proj1,
            int viewTiltDeg) {
        if (parentMetres == null || relMetres == null) {
            return new double[] { Double.NaN, Double.NaN };
        }
        double x = SystemOrbitGeometry.worldAxisMetres(parentMetres, 0)
                + SystemOrbitGeometry.worldAxisMetres(relMetres, 0);
        double y = SystemOrbitGeometry.worldAxisMetres(parentMetres, 1)
                + SystemOrbitGeometry.worldAxisMetres(relMetres, 1);
        double z = 0.0;
        if (parentMetres.length >= 3 || relMetres.length >= 3) {
            double pz = parentMetres.length >= 3 ? SystemOrbitGeometry.worldAxisMetres(parentMetres, 2) : 0.0;
            double rz = relMetres.length >= 3 ? SystemOrbitGeometry.worldAxisMetres(relMetres, 2) : 0.0;
            z = pz + rz;
        }
        return projectWorldComponents(x, y, z, proj0, proj1, viewTiltDeg);
    }

    public static double[] projectWorldComponents(double x, double y, double z, int proj0, int proj1,
            int viewTiltDeg) {
        int tilt = clampViewTiltDegrees(viewTiltDeg);
        int a0 = clampAxis(proj0);
        int a1 = clampAxis(proj1);
        double u = component(x, y, z, a0);
        double v1 = component(x, y, z, a1);
        if (tilt <= 0) {
            return new double[] { u, v1 };
        }
        int aPerp = thirdAxisIndex(a0, a1);
        double vPerp = component(x, y, z, aPerp);
        double rad = Math.toRadians(tilt);
        double c = Math.cos(rad);
        double s = Math.sin(rad);
        double v = v1 * c + vPerp * s;
        return new double[] { u, v };
    }

    /**
     * Approximate inverse of {@link #projectWorldComponents} for the map-plane point {@code (u, v)} at {@code viewTiltDeg},
     * with depth fixed on the unused axis ({@code vPerp = 0} except at 90°). Keeps the plot centre on the same world
     * point as tilt changes — no snap to the nearest body.
     */
    public static double[] worldMetresAtMapViewPoint(double u, double v, int proj0, int proj1, int viewTiltDeg) {
        int tilt = clampViewTiltDegrees(viewTiltDeg);
        int a0 = clampAxis(proj0);
        int a1 = clampAxis(proj1);
        int aPerp = thirdAxisIndex(a0, a1);
        double v1;
        double vPerp;
        if (tilt <= 0) {
            v1 = v;
            vPerp = 0.0;
        } else if (tilt >= 90) {
            v1 = 0.0;
            vPerp = v;
        } else {
            double c = Math.cos(Math.toRadians(tilt));
            v1 = Math.abs(c) > 1e-9 ? v / c : 0.0;
            vPerp = 0.0;
        }
        return worldVectorFromAxisComponents(u, v1, vPerp, a0, a1, aPerp);
    }

    static double[] worldVectorFromAxisComponents(
            double onA0, double onA1, double onAPerp, int a0, int a1, int aPerp) {
        double[] out = new double[3];
        out[clampAxis(a0)] = onA0;
        out[clampAxis(a1)] = onA1;
        out[clampAxis(aPerp)] = onAPerp;
        return out;
    }

    private static double component(double x, double y, double z, int axis) {
        return switch (axis) {
            case 0 -> x;
            case 1 -> y;
            default -> z;
        };
    }
}
