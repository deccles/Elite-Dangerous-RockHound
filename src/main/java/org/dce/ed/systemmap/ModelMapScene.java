package org.dce.ed.systemmap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.systemmodel.SystemModelService;
import org.dce.ed.systemmodel.SystemModelService.ModelHandle;
import org.dce.ed.systemmodel.SystemModelService.ModelState;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.model.BarycentreNode;
import org.dce.systemmodel.model.BodyNode;
import org.dce.systemmodel.model.HierarchyKeys;
import org.dce.systemmodel.model.OrbitRing;
import org.dce.systemmodel.model.SystemModel;
import org.dce.systemmodel.position.KeplerOrbitRing;

/**
 * Journal-authoritative map geometry: Kepler positions and orbit rings from {@link SystemModel}.
 */
public final class ModelMapScene {

    private static final int DEFAULT_LEGACY_SEGMENTS = 128;

    private ModelMapScene() {
    }

    public static Map<Integer, double[]> positionsMetres(
            ModelHandle handle, Map<Integer, BodyInfo> tableBodies, Instant t) {
        if (handle == null || handle.model() == null || handle.state() == ModelState.ERROR
                || tableBodies == null || tableBodies.isEmpty()) {
            return Map.of();
        }
        SystemModel model = handle.model();
        Set<Integer> definitiveIds = definitiveBodyIds(model, handle.state() == ModelState.INCOMPLETE);
        Map<Integer, double[]> out = new HashMap<>();
        for (Map.Entry<Integer, BodyInfo> e : tableBodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isScanBarycentreRow()) {
                continue;
            }
            int id = e.getKey().intValue();
            if (handle.state() == ModelState.INCOMPLETE && !definitiveIds.contains(id)) {
                continue;
            }
            SystemModelService.safePositionAt(handle, id, t)
                    .ifPresent(p -> out.put(Integer.valueOf(id), p.asArray()));
        }
        return out;
    }

    public static List<OrbitPolylineWorldXY> orbitPolylines(
            SystemModel model,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> positionsMetres,
            Instant t,
            int proj0,
            int proj1,
            int viewTiltDeg,
            boolean definitiveOnly) {
        return orbitPolylines(model, bodies, positionsMetres, t, proj0, proj1, viewTiltDeg, definitiveOnly,
                DEFAULT_LEGACY_SEGMENTS, Double.NaN);
    }

    public static List<OrbitPolylineWorldXY> orbitPolylines(
            SystemModel model,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> positionsMetres,
            Instant t,
            int proj0,
            int proj1,
            int viewTiltDeg,
            boolean definitiveOnly,
            int legacySegments,
            double scalePixelsPerMetre) {
        if (model == null) {
            return List.of();
        }
        Set<Integer> definitiveIds = definitiveBodyIds(model, definitiveOnly);
        List<OrbitPolylineWorldXY> polys = new ArrayList<>();
        boolean innerArrivalWideBinary = SystemOrbitGeometry.innerArrivalStellarPairBarycentreBodyId(bodies) >= 0;
        for (OrbitRing ring : model.orbitRingsAt(t)) {
            if (definitiveOnly && !ringIncludedInDefinitiveView(ring, definitiveIds, model)) {
                continue;
            }
            if (innerArrivalWideBinary && SystemOrbitGeometry.shouldSkipModelOrbitRingForInnerArrivalDisplay(
                    ring.bodyId(), ring.parentId(), bodies)) {
                continue;
            }
            List<double[]> pts = ringPointsForDisplay(model, ring, t, proj0, proj1, viewTiltDeg, positionsMetres,
                    legacySegments, scalePixelsPerMetre);
            if (pts == null || pts.isEmpty()) {
                continue;
            }
            int n = pts.size();
            double[] wx = new double[n];
            double[] wy = new double[n];
            for (int i = 0; i < n; i++) {
                double[] view = MapViewProjection.projectFromPositionMetres(pts.get(i), proj0, proj1, viewTiltDeg);
                wx[i] = view[0];
                wy[i] = view[1];
            }
            OrbitPolylineWorldXY raw = new OrbitPolylineWorldXY(ring.bodyId(), wx, wy, false);
            int parentId = ring.parentId();
            OrbitPolylineWorldXY display = SystemOrbitGeometry.displayOrbitPolylineFromModel(
                    raw, bodies, positionsMetres, parentId, proj0, proj1, viewTiltDeg);
            if (display != null) {
                polys.add(display);
            }
        }
        if (innerArrivalWideBinary) {
            boolean useScreenChord = Double.isFinite(scalePixelsPerMetre) && scalePixelsPerMetre > 0.0;
            SystemOrbitGeometry.appendInnerArrivalWideBinaryOrbitPolylines(
                    polys, bodies, positionsMetres, proj0, proj1, legacySegments, useScreenChord,
                    scalePixelsPerMetre, viewTiltDeg);
        }
        return List.copyOf(polys);
    }

    /**
     * Resample Kepler rings when the map zoom / projected perimeter needs more vertices than the model default
     * (high eccentricity periapsis and view tilt otherwise look faceted).
     */
    private static List<double[]> ringPointsForDisplay(
            SystemModel model,
            OrbitRing ring,
            Instant t,
            int proj0,
            int proj1,
            int viewTiltDeg,
            Map<Integer, double[]> positionsMetres,
            int legacySegments,
            double scalePixelsPerMetre) {
        List<double[]> pts = ring.pointsMetres();
        if (pts == null || pts.isEmpty()) {
            return List.of();
        }
        int targetSamples = targetRingSampleCount(pts, proj0, proj1, viewTiltDeg, legacySegments, scalePixelsPerMetre);
        if (targetSamples > pts.size()) {
            List<double[]> resampled = resampleWorldRing(model, ring, t, targetSamples);
            if (resampled != null && !resampled.isEmpty()) {
                pts = resampled;
            }
        }
        return pts;
    }

    private static int targetRingSampleCount(
            List<double[]> worldPts,
            int proj0,
            int proj1,
            int viewTiltDeg,
            int legacySegments,
            double scalePixelsPerMetre) {
        double perimProjM = projectedRingPerimeterMetres(worldPts, proj0, proj1, viewTiltDeg);
        int legacy = legacySegments > 0 ? legacySegments : DEFAULT_LEGACY_SEGMENTS;
        return SystemOrbitGeometry.orbitRingSegmentCountForScreen(scalePixelsPerMetre, perimProjM, legacy);
    }

    private static double projectedRingPerimeterMetres(
            List<double[]> worldPts, int proj0, int proj1, int viewTiltDeg) {
        if (worldPts == null || worldPts.size() < 2) {
            return 0.0;
        }
        double perim = 0.0;
        double[] prev = MapViewProjection.projectFromPositionMetres(worldPts.get(0), proj0, proj1, viewTiltDeg);
        for (int i = 1; i <= worldPts.size(); i++) {
            double[] cur = MapViewProjection.projectFromPositionMetres(worldPts.get(i % worldPts.size()), proj0, proj1,
                    viewTiltDeg);
            perim += Math.hypot(cur[0] - prev[0], cur[1] - prev[1]);
            prev = cur;
        }
        return perim;
    }

    /**
     * Higher-resolution ring in the same world frame as {@link SystemModel#orbitRingsAt}. The map position table
     * can disagree with the parent anchor {@link org.dce.systemmodel.position.PositionEngine} used when building
     * the model ring — infer parent offset from the existing world ring instead of re-adding {@code positionsMetres(parent)}.
     */
    private static List<double[]> resampleWorldRing(
            SystemModel model,
            OrbitRing ring,
            Instant t,
            int targetSamples) {
        List<double[]> engineWorld = ring.pointsMetres();
        OrbitalElements orbit = orbitElementsFor(model, ring.bodyId());
        if (orbit == null) {
            return upsampleWorldRingArcLength(engineWorld, targetSamples);
        }
        OrbitRing rel = KeplerOrbitRing.ringForBody(ring.bodyId(), ring.parentId(), orbit, t, targetSamples);
        if (rel.pointsMetres().isEmpty() || engineWorld.isEmpty()) {
            return upsampleWorldRingArcLength(engineWorld, targetSamples);
        }
        double[] anchor = orbitParentWorldAnchor(engineWorld, rel.pointsMetres());
        if (anchor == null) {
            return upsampleWorldRingArcLength(engineWorld, targetSamples);
        }
        List<double[]> world = new ArrayList<>(rel.pointsMetres().size());
        for (double[] pt : rel.pointsMetres()) {
            world.add(new double[] {
                    anchor[0] + pt[0],
                    anchor[1] + pt[1],
                    anchor[2] + (pt.length >= 3 ? pt[2] : 0.0)
            });
        }
        return world;
    }

    /** {@code parentWorld ≈ engineWorld[i] − rel[i]} at matching mean-anomaly phase (index 0). */
    private static double[] orbitParentWorldAnchor(List<double[]> engineWorld, List<double[]> relPoints) {
        if (engineWorld == null || relPoints == null || engineWorld.isEmpty() || relPoints.isEmpty()) {
            return null;
        }
        double[] w0 = engineWorld.get(0);
        double[] r0 = relPoints.get(0);
        if (w0 == null || r0 == null || w0.length < 2 || r0.length < 2) {
            return null;
        }
        double pz = 0.0;
        if (w0.length >= 3 && r0.length >= 3) {
            pz = w0[2] - r0[2];
        }
        return new double[] { w0[0] - r0[0], w0[1] - r0[1], pz };
    }

    private static List<double[]> upsampleWorldRingArcLength(List<double[]> pts, int targetSamples) {
        if (pts == null || pts.size() < 3 || targetSamples <= pts.size()) {
            return pts;
        }
        int n = pts.size();
        double[] cum = new double[n + 1];
        cum[0] = 0.0;
        for (int i = 0; i < n; i++) {
            double[] a = pts.get(i);
            double[] b = pts.get((i + 1) % n);
            cum[i + 1] = cum[i] + chord3d(a, b);
        }
        double total = cum[n];
        if (!(total > 0.0) || !Double.isFinite(total)) {
            return pts;
        }
        List<double[]> out = new ArrayList<>(targetSamples);
        int seg = 0;
        for (int i = 0; i < targetSamples; i++) {
            double target = (total * i) / targetSamples;
            while (seg + 1 < n && cum[seg + 1] < target) {
                seg++;
            }
            double[] a = pts.get(seg);
            double[] b = pts.get((seg + 1) % n);
            double segLen = cum[seg + 1] - cum[seg];
            double u = segLen > 0.0 ? (target - cum[seg]) / segLen : 0.0;
            out.add(lerp3d(a, b, u));
        }
        return out;
    }

    private static double chord3d(double[] a, double[] b) {
        if (a == null || b == null) {
            return 0.0;
        }
        double dz = (a.length >= 3 && b.length >= 3) ? a[2] - b[2] : 0.0;
        return Math.hypot(a[0] - b[0], Math.hypot(a[1] - b[1], dz));
    }

    private static double[] lerp3d(double[] a, double[] b, double u) {
        double t = Math.max(0.0, Math.min(1.0, u));
        double az = a.length >= 3 ? a[2] : 0.0;
        double bz = b.length >= 3 ? b[2] : 0.0;
        return new double[] {
                a[0] + (b[0] - a[0]) * t,
                a[1] + (b[1] - a[1]) * t,
                az + (bz - az) * t
        };
    }

    private static OrbitalElements orbitElementsFor(SystemModel model, int bodyId) {
        if (model == null) {
            return null;
        }
        if (HierarchyKeys.isBaryMapKey(bodyId)) {
            int nullId = HierarchyKeys.journalNullFromBaryMapKey(bodyId);
            return model.barycentre(nullId).map(BarycentreNode::orbit).orElse(null);
        }
        return model.body(bodyId).map(BodyNode::orbit).orElse(null);
    }

    /** Bary hub rings and bodies whose immediate parent is a {@code Null:N} hub use dashed strokes in the map panel. */
    static boolean isBarycentreAssociatedOrbit(int bodyId, int parentId) {
        return HierarchyKeys.isBaryMapKey(bodyId) || HierarchyKeys.isBaryMapKey(parentId);
    }

    private static boolean ringIncludedInDefinitiveView(
            OrbitRing ring, Set<Integer> definitiveIds, SystemModel model) {
        if (definitiveIds.contains(ring.bodyId())) {
            return true;
        }
        if (HierarchyKeys.isBaryMapKey(ring.bodyId())) {
            int nullId = HierarchyKeys.journalNullFromBaryMapKey(ring.bodyId());
            return model.barycentre(nullId).isPresent();
        }
        return false;
    }

    private static Set<Integer> definitiveBodyIds(SystemModel model, boolean definitiveOnly) {
        if (!definitiveOnly) {
            Set<Integer> all = new HashSet<>(model.bodies().keySet());
            all.addAll(model.barycentres().keySet());
            return all;
        }
        return model.definitiveSubgraph().definitiveBodyIds();
    }
}
