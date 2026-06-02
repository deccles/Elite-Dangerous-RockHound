package org.dce.ed.systemmap;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.dce.ed.util.SystemOrbitGeometry.WideBinaryFlattenFrame;

/**
 * Builds a {@link SystemMapModel} from journal/cache {@link BodyInfo} rows. All schematic layout stages and
 * resolved topology (parent links, branch hubs, label visibility) are computed here — not in
 * {@link org.dce.ed.ui.SystemPlanMapPanel}.
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
        return build(systemName, bodies, epoch, freezeBarycentreStars, MapScaleMode.TRUE_SCALE);
    }

    public static SystemMapModel build(String systemName, Map<Integer, BodyInfo> bodies, Instant epoch,
            boolean freezeBarycentreStars, MapScaleMode scaleMode) {
        if (bodies == null || bodies.isEmpty()) {
            return emptyModel(systemName, scaleMode);
        }
        SystemMapJournalEnricher.prepareMapBodies(bodies);
        MapScaleMode mode = MapScaleMode.TRUE_SCALE;
        Instant t = epoch != null ? epoch : Instant.now();
        SystemMapClassification classification = SystemMapRules.classify(bodies);

        Map<Integer, double[]> positions = new HashMap<>(
                SystemOrbitGeometry.bodyPositionsMetres(bodies, t, freezeBarycentreStars));

        int[] axes = chooseProjectionAxes(bodies, positions);
        int a0 = axes[0];
        int a1 = axes[1];

        WideBinaryFlattenFrame frame = null;
        if (classification.layoutKind() == SystemLayoutKind.WIDE_BINARY) {
            SystemOrbitGeometry.flattenWideBinaryIntoMapPlane(positions, bodies, a0, a1, true);
            if (SystemOrbitGeometry.isHierarchicalWideBinary(bodies)) {
                SystemOrbitGeometry.placeTrueScaleHierarchicalScanHubs(positions, bodies, a0, a1);
                SystemOrbitGeometry.syncScanBarycentreRowPositionsToSyntheticHubs(positions, bodies);
                /*
                 * Seat B+C at Null:3 and D vs that hub on Null:2 (journal radii). Without this, flatten only shifts
                 * the cluster rigidly and B/C/D keep raw Kepler phase — B beside D with C far away on the map.
                 */
                SystemOrbitGeometry.alignPlanetBinaryGroupsOnMapPlane(positions, bodies, t, a0, a1,
                        freezeBarycentreStars);
                SystemOrbitGeometry.restoreTrueScaleHierarchicalOuterCompanionChord(positions, bodies, a0, a1);
                /*
                 * Outer-chord restore scales by outermost star; trunk rings use companion centroid — snap cluster onto
                 * the rim and re-sync scan rows so Null:2/3 crosses are not left at pre-shift map keys.
                 */
                SystemOrbitGeometry.snapCompanionClusterOntoTrunkRing(positions, bodies, t, a0, a1,
                        freezeBarycentreStars);
                SystemOrbitGeometry.syncScanBarycentreRowPositionsToSyntheticHubs(positions, bodies);
            } else {
                SystemOrbitGeometry.placeTrueScalePrimaryBranchPlanetBinaryHubs(positions, bodies, t, a0, a1,
                        freezeBarycentreStars);
            }
            frame = SystemOrbitGeometry.captureWideBinaryFlattenFrame(positions, bodies, a0, a1);
        } else {
            SystemOrbitGeometry.snapPlanetBinaryBarycentreCentroidsOnMapPlane(positions, bodies, a0, a1);
            SystemOrbitGeometry.alignPlanetBinaryGroupsOnMapPlane(positions, bodies, t, a0, a1,
                    freezeBarycentreStars);
            SystemOrbitGeometry.alignMoonsOnSchematicRingsAroundParents(positions, bodies, t, a0, a1,
                    freezeBarycentreStars);
            SystemOrbitGeometry.syncScanBarycentreRowPositionsToSyntheticHubs(positions, bodies);
        }

        Map<Integer, Integer> resolvedParents = buildResolvedParents(bodies);
        boolean includeBinaryBarycentreRing = !SystemOrbitGeometry.isHierarchicalWideBinary(bodies);
        List<OrbitPolylineWorldXY> polylines = SystemOrbitGeometry.orbitPolylinesWorldMetresXY(bodies, positions,
                DEFAULT_ORBIT_SEGMENTS, Double.NaN, a0, a1, includeBinaryBarycentreRing, resolvedParents, mode,
                false, null, 0, t);
        Map<Integer, Integer> childCounts = buildDirectChildCounts(resolvedParents);
        Set<Integer> hubIds = SystemMapRules.subsystemHubBodyIds(bodies, resolvedParents, classification);
        Set<Integer> revolutionCenters = SystemMapRules.orbitRevolutionCenterBodyIds(bodies, resolvedParents,
                childCounts);
        Map<Integer, Boolean> labelVisibility = buildLabelVisibility(bodies, resolvedParents, childCounts,
                classification);

        return new SystemMapModel(systemName, bodies, mode, classification, a0, a1, positions, polylines, frame,
                resolvedParents, childCounts, hubIds, revolutionCenters, labelVisibility);
    }

    /**
     * Rebuilds orbit polylines for the same bodies/axes as {@code base} using updated schematic positions (e.g. zoom
     * or playback). Same geometry entry point as initial {@link #build}.
     */
    public static List<OrbitPolylineWorldXY> rebuildOrbitPolylines(SystemMapModel base,
            Map<Integer, double[]> positionsMetres,
            int segments,
            double scalePixelsPerMetre) {
        return rebuildOrbitPolylines(base, positionsMetres, segments, scalePixelsPerMetre, false);
    }

    public static List<OrbitPolylineWorldXY> rebuildOrbitPolylines(SystemMapModel base,
            Map<Integer, double[]> positionsMetres,
            int segments,
            double scalePixelsPerMetre,
            boolean enforceSchematicMoonMinOrbitRadius) {
        return rebuildOrbitPolylines(base, positionsMetres, segments, scalePixelsPerMetre,
                enforceSchematicMoonMinOrbitRadius, null);
    }

    /**
     * @param ringRadiusReferencePositions when non-null, schematic ring radii are derived from this layout snapshot
     *        (e.g. play T+0) while stroke centres follow {@code positionsMetres}, so radii stay fixed during playback.
     */
    public static List<OrbitPolylineWorldXY> rebuildOrbitPolylines(SystemMapModel base,
            Map<Integer, double[]> positionsMetres,
            int segments,
            double scalePixelsPerMetre,
            boolean enforceSchematicMoonMinOrbitRadius,
            Map<Integer, double[]> ringRadiusReferencePositions) {
        return rebuildOrbitPolylines(base, positionsMetres, segments, scalePixelsPerMetre,
                enforceSchematicMoonMinOrbitRadius, ringRadiusReferencePositions, null);
    }

    /**
     * @param renderScaleMode when non-null, overrides {@link SystemMapModel#mapScaleMode()} so GUI rebuilds match the
     *        panel toggle even if {@code base} was primed under a different mode during playback.
     */
    public static List<OrbitPolylineWorldXY> rebuildOrbitPolylines(SystemMapModel base,
            Map<Integer, double[]> positionsMetres,
            int segments,
            double scalePixelsPerMetre,
            boolean enforceSchematicMoonMinOrbitRadius,
            Map<Integer, double[]> ringRadiusReferencePositions,
            MapScaleMode renderScaleMode) {
        return rebuildOrbitPolylines(base, positionsMetres, segments, scalePixelsPerMetre,
                enforceSchematicMoonMinOrbitRadius, ringRadiusReferencePositions, renderScaleMode, 0);
    }

    /**
     * @param viewTiltDegrees true-scale view tilt 0…90 ({@link MapViewProjection}); ignored when schematic.
     */
    public static List<OrbitPolylineWorldXY> rebuildOrbitPolylines(SystemMapModel base,
            Map<Integer, double[]> positionsMetres,
            int segments,
            double scalePixelsPerMetre,
            boolean enforceSchematicMoonMinOrbitRadius,
            Map<Integer, double[]> ringRadiusReferencePositions,
            MapScaleMode renderScaleMode,
            int viewTiltDegrees) {
        return rebuildOrbitPolylines(base, positionsMetres, segments, scalePixelsPerMetre,
                enforceSchematicMoonMinOrbitRadius, ringRadiusReferencePositions, renderScaleMode, viewTiltDegrees,
                null);
    }

    /**
     * @param strokeEpoch sim instant for Kepler stroke sampling during true-scale playback; null uses wall clock.
     */
    public static List<OrbitPolylineWorldXY> rebuildOrbitPolylines(SystemMapModel base,
            Map<Integer, double[]> positionsMetres,
            int segments,
            double scalePixelsPerMetre,
            boolean enforceSchematicMoonMinOrbitRadius,
            Map<Integer, double[]> ringRadiusReferencePositions,
            MapScaleMode renderScaleMode,
            int viewTiltDegrees,
            Instant strokeEpoch) {
        if (base == null || positionsMetres == null || base.bodies().isEmpty()) {
            return List.of();
        }
        MapScaleMode mode = renderScaleMode != null ? renderScaleMode : base.mapScaleMode();
        /*
         * Match {@link #build}: wide-binary (non-hierarchical) A+B mutual ring. Do not use
         * {@link SystemMapModel#hasBarycentreMutualRing()} — {@link #playbackBase} carries empty polylines.
         */
        boolean includeBinaryBarycentreRing = !SystemOrbitGeometry.isHierarchicalWideBinary(base.bodies());
        return SystemOrbitGeometry.orbitPolylinesWorldMetresXY(base.bodies(), positionsMetres, segments,
                scalePixelsPerMetre, base.projectionAxis0(), base.projectionAxis1(), includeBinaryBarycentreRing,
                base.resolvedParentByBodyId(), mode, enforceSchematicMoonMinOrbitRadius,
                ringRadiusReferencePositions, viewTiltDegrees, strokeEpoch);
    }

    private static SystemMapModel emptyModel(String systemName, MapScaleMode scaleMode) {
        MapScaleMode mode = MapScaleMode.TRUE_SCALE;
        SystemMapClassification empty = new SystemMapClassification(SystemLayoutKind.GENERIC, 0, -1, -1,
                List.of(), false);
        return new SystemMapModel(systemName, Map.of(), mode, empty, 0, 1, Map.of(), List.of(), null, Map.of(),
                Map.of(), Set.of(), Set.of(), Map.of());
    }

    private static Map<Integer, Integer> buildResolvedParents(Map<Integer, BodyInfo> bodies) {
        Map<Integer, Integer> resolved = new HashMap<>();
        if (bodies == null) {
            return resolved;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            resolved.put(e.getKey(), Integer.valueOf(
                    SystemMapRules.resolveOrbitParentBodyId(e.getValue(), bodies, id)));
        }
        return resolved;
    }

    private static Map<Integer, Integer> buildDirectChildCounts(Map<Integer, Integer> resolvedParents) {
        Map<Integer, Integer> counts = new HashMap<>();
        if (resolvedParents == null) {
            return counts;
        }
        for (Map.Entry<Integer, Integer> e : resolvedParents.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int pid = e.getValue().intValue();
            if (pid >= 0) {
                counts.merge(Integer.valueOf(pid), 1, Integer::sum);
            }
        }
        return counts;
    }

    private static Map<Integer, Boolean> buildLabelVisibility(Map<Integer, BodyInfo> bodies,
            Map<Integer, Integer> resolvedParents,
            Map<Integer, Integer> childCounts,
            SystemMapClassification classification) {
        Map<Integer, Boolean> out = new HashMap<>();
        if (bodies == null) {
            return out;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            boolean star = SystemMapRules.isMapStellarBody(e.getValue());
            boolean moon = !star && SystemOrbitGeometry.isMoonSatelliteBody(e.getValue(), bodies);
            int children = childCounts.getOrDefault(e.getKey(), 0).intValue();
            boolean soleOrbitCluster = !star && children == 0;
            out.put(e.getKey(), Boolean.valueOf(SystemMapRules.bodyLabelVisibleWhenZoomedOut(e.getValue(), id, bodies,
                    star, moon, soleOrbitCluster)));
        }
        return out;
    }

    /**
     * Re-applies wide-binary flatten + branch schematic positions during orbit playback ticks.
     */
    /** Lightweight handle for {@link #refreshPositionsForPlayback} when the GUI already has flatten frame + axes. */
    public static SystemMapModel playbackBase(Map<Integer, BodyInfo> bodies, int projectionAxis0, int projectionAxis1,
            Map<Integer, double[]> lastPositions, WideBinaryFlattenFrame frame, MapScaleMode scaleMode) {
        MapScaleMode mode = MapScaleMode.TRUE_SCALE;
        SystemMapClassification clf = SystemMapRules.classify(bodies);
        Map<Integer, Integer> resolvedParents = buildResolvedParents(bodies);
        Map<Integer, Integer> childCounts = buildDirectChildCounts(resolvedParents);
        return new SystemMapModel(null, bodies, mode, clf, projectionAxis0, projectionAxis1,
                lastPositions != null ? lastPositions : Map.of(), List.of(), frame, resolvedParents, childCounts,
                SystemMapRules.subsystemHubBodyIds(bodies, resolvedParents, clf),
                SystemMapRules.orbitRevolutionCenterBodyIds(bodies, resolvedParents, childCounts),
                buildLabelVisibility(bodies, resolvedParents, childCounts, clf));
    }

    public static SystemMapModel playbackBase(Map<Integer, BodyInfo> bodies, int projectionAxis0, int projectionAxis1,
            Map<Integer, double[]> lastPositions, WideBinaryFlattenFrame frame) {
        return playbackBase(bodies, projectionAxis0, projectionAxis1, lastPositions, frame, MapScaleMode.TRUE_SCALE);
    }

    public static Map<Integer, double[]> refreshPositionsForPlayback(SystemMapModel base,
            Map<Integer, double[]> keplerPositions,
            Instant epoch,
            boolean freezeBarycentreStars) {
        if (base == null || keplerPositions == null || base.bodies().isEmpty()) {
            return keplerPositions;
        }
        Map<Integer, BodyInfo> bodies = base.bodies();
        if (base.classification().layoutKind() == SystemLayoutKind.WIDE_BINARY) {
            Map<Integer, double[]> positions = new HashMap<>(keplerPositions);
            int a0 = base.projectionAxis0();
            int a1 = base.projectionAxis1();
            SystemOrbitGeometry.flattenWideBinaryIntoMapPlane(positions, bodies, a0, a1, true);
            if (SystemOrbitGeometry.isHierarchicalWideBinary(bodies)) {
                SystemOrbitGeometry.placeTrueScaleHierarchicalScanHubs(positions, bodies, a0, a1);
                SystemOrbitGeometry.syncScanBarycentreRowPositionsToSyntheticHubs(positions, bodies);
                Instant t = epoch != null ? epoch : Instant.now();
                SystemOrbitGeometry.alignPlanetBinaryGroupsOnMapPlane(positions, bodies, t, a0, a1,
                        freezeBarycentreStars);
                SystemOrbitGeometry.restoreTrueScaleHierarchicalOuterCompanionChord(positions, bodies, a0, a1);
                SystemOrbitGeometry.snapCompanionClusterOntoTrunkRing(positions, bodies, t, a0, a1,
                        freezeBarycentreStars);
                SystemOrbitGeometry.syncScanBarycentreRowPositionsToSyntheticHubs(positions, bodies);
            } else {
                Instant t = epoch != null ? epoch : Instant.now();
                SystemOrbitGeometry.placeTrueScalePrimaryBranchPlanetBinaryHubs(positions, bodies, t, a0, a1,
                        freezeBarycentreStars);
                SystemOrbitGeometry.syncScanBarycentreRowPositionsToSyntheticHubs(positions, bodies);
            }
            return positions;
        }
        Map<Integer, double[]> positions = new HashMap<>(keplerPositions);
        int a0 = base.projectionAxis0();
        int a1 = base.projectionAxis1();
        Instant t = epoch != null ? epoch : Instant.now();
        SystemOrbitGeometry.snapPlanetBinaryBarycentreCentroidsOnMapPlane(positions, bodies, a0, a1);
        SystemOrbitGeometry.alignPlanetBinaryGroupsOnMapPlane(positions, bodies, t, a0, a1,
                freezeBarycentreStars);
        SystemOrbitGeometry.alignMoonsOnSchematicRingsAroundParents(positions, bodies, t, a0, a1,
                freezeBarycentreStars);
        SystemOrbitGeometry.syncScanBarycentreRowPositionsToSyntheticHubs(positions, bodies);
        return positions;
    }

    /**
     * Picks two distinct world axes for the map plane (same logic as the former GUI method).
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
