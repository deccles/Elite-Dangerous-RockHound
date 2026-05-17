package org.dce.ed.systemmap;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.dce.ed.util.SystemOrbitGeometry.WideBinaryFlattenFrame;

/**
 * Builds a {@link SystemMapModel} from journal/cache {@link BodyInfo} rows. All schematic layout stages live here
 * (not in {@link org.dce.ed.ui.SystemPlanMapPanel}).
 */
public final class SystemMapPipeline {

    private static final int DEFAULT_ORBIT_SEGMENTS = 96;

    private SystemMapPipeline() {
    }

    public static SystemMapModel build(Map<Integer, BodyInfo> bodies) {
        return build(null, bodies, Instant.now(), false);
    }

    public static SystemMapModel build(String systemName, Map<Integer, BodyInfo> bodies, Instant epoch,
            boolean freezeBarycentreStars) {
        if (bodies == null || bodies.isEmpty()) {
            SystemMapClassification empty = new SystemMapClassification(SystemLayoutKind.GENERIC, 0, -1, -1,
                    List.of(), false);
            return new SystemMapModel(systemName, Map.of(), empty, 0, 1, Map.of(), List.of(), null);
        }
        Instant t = epoch != null ? epoch : Instant.now();
        SystemMapClassification classification = SystemMapRules.classify(bodies);

        Map<Integer, double[]> positions = new HashMap<>(
                SystemOrbitGeometry.bodyPositionsMetres(bodies, t, freezeBarycentreStars));

        int[] axes = chooseProjectionAxes(bodies, positions);
        int a0 = axes[0];
        int a1 = axes[1];

        WideBinaryFlattenFrame frame = null;
        if (classification.layoutKind() == SystemLayoutKind.SINGLE_STAR_SCHEMATIC
                || SystemOrbitGeometry.shouldApplyLoneStarSchematicLayout(bodies)) {
            positions = new HashMap<>(SystemOrbitGeometry.bodyPositionsMetresForSingleStarMap(bodies, t, a0, a1,
                    freezeBarycentreStars));
        } else if (classification.layoutKind() == SystemLayoutKind.WIDE_BINARY) {
            SystemOrbitGeometry.flattenWideBinaryIntoMapPlane(positions, bodies, a0, a1);
            SystemOrbitGeometry.recenterBinaryBarycentreInMapPlane(positions, bodies, a0, a1);
            positions = new HashMap<>(SystemOrbitGeometry.bodyPositionsMetresForWideBinaryMap(bodies, positions, t,
                    a0, a1, freezeBarycentreStars));
            frame = SystemOrbitGeometry.captureWideBinaryFlattenFrame(positions, bodies, a0, a1);
        }

        List<OrbitPolylineWorldXY> polylines = SystemOrbitGeometry.orbitPolylinesWorldMetresXY(bodies, positions,
                DEFAULT_ORBIT_SEGMENTS, Double.NaN, a0, a1);

        return new SystemMapModel(systemName, bodies, classification, a0, a1, positions, polylines, frame);
    }

    /**
     * Re-applies wide-binary flatten + branch schematic positions during orbit playback ticks.
     */
    /** Lightweight handle for {@link #refreshPositionsForPlayback} when the GUI already has flatten frame + axes. */
    public static SystemMapModel playbackBase(Map<Integer, BodyInfo> bodies, int projectionAxis0, int projectionAxis1,
            Map<Integer, double[]> lastPositions, WideBinaryFlattenFrame frame) {
        return new SystemMapModel(null, bodies, SystemMapRules.classify(bodies), projectionAxis0, projectionAxis1,
                lastPositions != null ? lastPositions : Map.of(), List.of(), frame);
    }

    public static Map<Integer, double[]> refreshPositionsForPlayback(SystemMapModel base,
            Map<Integer, double[]> keplerPositions,
            Instant epoch,
            boolean freezeBarycentreStars) {
        if (base == null || keplerPositions == null || base.bodies().isEmpty()) {
            return keplerPositions;
        }
        Map<Integer, BodyInfo> bodies = base.bodies();
        if (base.classification().layoutKind() != SystemLayoutKind.WIDE_BINARY) {
            if (base.classification().layoutKind() == SystemLayoutKind.SINGLE_STAR_SCHEMATIC
                    || SystemOrbitGeometry.shouldApplyLoneStarSchematicLayout(bodies)) {
                return SystemOrbitGeometry.bodyPositionsMetresForSingleStarMap(bodies,
                        epoch != null ? epoch : Instant.now(), base.projectionAxis0(), base.projectionAxis1(),
                        freezeBarycentreStars);
            }
            return keplerPositions;
        }
        Map<Integer, double[]> positions = new HashMap<>(keplerPositions);
        int a0 = base.projectionAxis0();
        int a1 = base.projectionAxis1();
        WideBinaryFlattenFrame frame = base.wideBinaryFlattenFrame();
        if (frame != null) {
            SystemOrbitGeometry.reapplyWideBinaryFlattenWithFrame(positions, bodies, a0, a1, frame);
        } else {
            SystemOrbitGeometry.flattenWideBinaryIntoMapPlane(positions, bodies, a0, a1);
        }
        SystemOrbitGeometry.recenterBinaryBarycentreInMapPlane(positions, bodies, a0, a1);
        Instant t = epoch != null ? epoch : Instant.now();
        return SystemOrbitGeometry.bodyPositionsMetresForWideBinaryMap(bodies, positions, t, a0, a1,
                freezeBarycentreStars);
    }

    /**
     * Picks two distinct world axes for the schematic map plane (same logic as the former GUI method).
     */
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
            double x = SystemOrbitGeometry.worldAxisMetres(p, 0);
            double y = SystemOrbitGeometry.worldAxisMetres(p, 1);
            double z = p.length >= 3 ? SystemOrbitGeometry.worldAxisMetres(p, 2) : 0.0;
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
            double x = SystemOrbitGeometry.worldAxisMetres(p, 0);
            double y = SystemOrbitGeometry.worldAxisMetres(p, 1);
            double z = p.length >= 3 ? SystemOrbitGeometry.worldAxisMetres(p, 2) : 0.0;
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
