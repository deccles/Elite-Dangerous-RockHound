package org.dce.ed.systemmap;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.dce.ed.util.SystemOrbitGeometry.WideBinaryFlattenFrame;

/**
 * Thin facade over {@link ModelMapTranscriber} — sole map build entry.
 */
public final class SystemMapPipeline {

    private SystemMapPipeline() {
    }

    public static SystemMapModel build(Map<Integer, BodyInfo> bodies) {
        return ModelMapTranscriber.build(bodies);
    }

    public static SystemMapModel build(String systemName, Map<Integer, BodyInfo> bodies, Instant epoch,
            boolean freezeBarycentreStars) {
        return ModelMapTranscriber.build(systemName, bodies, epoch, freezeBarycentreStars);
    }

    public static SystemMapModel build(
            String systemName,
            Map<Integer, BodyInfo> bodies,
            Instant epoch,
            boolean freezeBarycentreStars,
            SystemSession session) {
        return ModelMapTranscriber.build(systemName, bodies, epoch, freezeBarycentreStars, session);
    }

    public static List<OrbitPolylineWorldXY> rebuildOrbitPolylines(SystemMapModel base,
            Map<Integer, double[]> positionsMetres,
            int segments,
            double scalePixelsPerMetre) {
        return rebuildOrbitPolylines(base, positionsMetres, segments, scalePixelsPerMetre, null);
    }

    public static List<OrbitPolylineWorldXY> rebuildOrbitPolylines(SystemMapModel base,
            Map<Integer, double[]> positionsMetres,
            int segments,
            double scalePixelsPerMetre,
            Map<Integer, double[]> ringRadiusReferencePositions) {
        return rebuildOrbitPolylines(base, positionsMetres, segments, scalePixelsPerMetre,
                ringRadiusReferencePositions, 0);
    }

    public static List<OrbitPolylineWorldXY> rebuildOrbitPolylines(SystemMapModel base,
            Map<Integer, double[]> positionsMetres,
            int segments,
            double scalePixelsPerMetre,
            Map<Integer, double[]> ringRadiusReferencePositions,
            int viewTiltDegrees) {
        return rebuildOrbitPolylines(base, positionsMetres, segments, scalePixelsPerMetre,
                ringRadiusReferencePositions, viewTiltDegrees, null);
    }

    public static List<OrbitPolylineWorldXY> rebuildOrbitPolylines(SystemMapModel base,
            Map<Integer, double[]> positionsMetres,
            int segments,
            double scalePixelsPerMetre,
            Map<Integer, double[]> ringRadiusReferencePositions,
            int viewTiltDegrees,
            Instant strokeEpoch) {
        return rebuildOrbitPolylines(base, positionsMetres, segments, scalePixelsPerMetre,
                ringRadiusReferencePositions, viewTiltDegrees, strokeEpoch, null);
    }

    public static List<OrbitPolylineWorldXY> rebuildOrbitPolylines(SystemMapModel base,
            Map<Integer, double[]> positionsMetres,
            int segments,
            double scalePixelsPerMetre,
            Map<Integer, double[]> ringRadiusReferencePositions,
            int viewTiltDegrees,
            Instant strokeEpoch,
            SystemSession session) {
        return ModelMapTranscriber.rebuildOrbitPolylines(base, positionsMetres, segments, scalePixelsPerMetre,
                ringRadiusReferencePositions, viewTiltDegrees, strokeEpoch, session);
    }

    public static SystemMapModel playbackBase(Map<Integer, BodyInfo> bodies, int projectionAxis0, int projectionAxis1,
            Map<Integer, double[]> lastPositions, WideBinaryFlattenFrame frame) {
        return ModelMapTranscriber.playbackBase(bodies, projectionAxis0, projectionAxis1, lastPositions, frame);
    }

    public static Map<Integer, double[]> refreshPositionsForPlayback(SystemMapModel base,
            Map<Integer, double[]> keplerPositions,
            Instant epoch,
            boolean freezeBarycentreStars) {
        return refreshPositionsForPlayback(base, keplerPositions, epoch, freezeBarycentreStars, null);
    }

    public static Map<Integer, double[]> refreshPositionsForPlayback(SystemMapModel base,
            Map<Integer, double[]> keplerPositions,
            Instant epoch,
            boolean freezeBarycentreStars,
            SystemSession session) {
        return ModelMapTranscriber.refreshPositionsForPlayback(base, keplerPositions, epoch, freezeBarycentreStars,
                session);
    }

    static int[] chooseProjectionAxes(Map<Integer, BodyInfo> bodies, Map<Integer, double[]> positions) {
        int a0 = 0;
        int a1 = 1;
        if (bodies == null || positions == null) {
            return new int[] { a0, a1 };
        }
        double mx = 0.0;
        double my = 0.0;
        double mz = 0.0;
        int n = 0;
        for (Integer k : bodies.keySet()) {
            if (k == null) {
                continue;
            }
            double[] p = positions.get(k);
            if (p == null || p.length < 2) {
                continue;
            }
            double x = org.dce.ed.util.SystemOrbitGeometry.worldAxisMetres(p, 0);
            double y = org.dce.ed.util.SystemOrbitGeometry.worldAxisMetres(p, 1);
            double z = p.length >= 3 ? org.dce.ed.util.SystemOrbitGeometry.worldAxisMetres(p, 2) : 0.0;
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                continue;
            }
            mx += x;
            my += y;
            mz += z;
            n++;
        }
        if (n < 3) {
            return new int[] { a0, a1 };
        }
        mx /= n;
        my /= n;
        mz /= n;
        double vx = 0.0;
        double vy = 0.0;
        double vz = 0.0;
        for (Integer k : bodies.keySet()) {
            if (k == null) {
                continue;
            }
            double[] p = positions.get(k);
            if (p == null || p.length < 2) {
                continue;
            }
            double x = org.dce.ed.util.SystemOrbitGeometry.worldAxisMetres(p, 0);
            double y = org.dce.ed.util.SystemOrbitGeometry.worldAxisMetres(p, 1);
            double z = p.length >= 3 ? org.dce.ed.util.SystemOrbitGeometry.worldAxisMetres(p, 2) : 0.0;
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                continue;
            }
            double dx = x - mx;
            double dy = y - my;
            double dz = z - mz;
            vx += dx * dx;
            vy += dy * dy;
            vz += dz * dz;
        }
        vx /= n;
        vy /= n;
        vz /= n;
        double vmax = Math.max(1e-120, Math.max(vx, Math.max(vy, vz)));
        if (vz <= 0.02 * vmax && vx >= 0.02 * vmax && vy >= 0.02 * vmax) {
            a0 = 0;
            a1 = 1;
        } else if (vy <= 0.02 * vmax) {
            a0 = 0;
            a1 = 2;
        } else if (vx <= 0.02 * vmax) {
            a0 = 1;
            a1 = 2;
        }
        return new int[] { a0, a1 };
    }
}
