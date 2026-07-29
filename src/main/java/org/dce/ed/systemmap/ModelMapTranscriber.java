package org.dce.ed.systemmap;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmodel.SystemModelService;
import org.dce.ed.systemmodel.SystemModelService.ModelHandle;
import org.dce.ed.systemmodel.SystemModelService.ModelState;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.dce.ed.util.SystemOrbitGeometry.WideBinaryFlattenFrame;
import org.dce.systemmodel.build.SystemModelBuilder;
import org.dce.systemmodel.journal.JournalRecord;
import org.dce.systemmodel.model.HierarchyGraph;
import org.dce.systemmodel.model.HierarchyKeys;
import org.dce.systemmodel.model.SystemModel;

/**
 * Sole map build path: transcribes {@link SystemModel} hierarchy + Kepler geometry into {@link SystemMapModel}.
 */
public final class ModelMapTranscriber {

    private ModelMapTranscriber() {
    }

    public static SystemMapModel build(Map<Integer, BodyInfo> bodies) {
        return build(null, bodies, Instant.now(), false);
    }

    public static SystemMapModel build(String systemName, Map<Integer, BodyInfo> bodies, Instant epoch,
            boolean freezeBarycentreStars) {
        return build(systemName, bodies, epoch, freezeBarycentreStars, null);
    }

    public static SystemMapModel build(
            String systemName,
            Map<Integer, BodyInfo> bodies,
            Instant epoch,
            boolean freezeBarycentreStars,
            SystemSession session) {
        if (bodies == null || bodies.isEmpty()) {
            return emptyModel(systemName);
        }
        SystemMapJournalEnricher.prepareMapBodies(bodies);
        Instant t = epoch != null ? epoch : Instant.now();

        SystemModel model = session != null ? session.model() : null;
        ModelHandle handle = session != null ? session.handle() : null;
        if (model == null) {
            model = buildModelFromBodies(systemName, bodies);
            handle = model != null
                    ? new ModelHandle(ModelState.INCOMPLETE, model, null, List.of())
                    : null;
        }

        Map<Integer, double[]> positions = positionsFromModel(handle, model, bodies, t);
        int[] axes = SystemMapPipeline.chooseProjectionAxes(bodies, positions);
        int a0 = axes[0];
        int a1 = axes[1];

        List<OrbitPolylineWorldXY> polylines = orbitPolylinesFromModel(
                model, handle, t, a0, a1, 0, bodies, positions);

        SystemMapClassification classification = SystemMapRules.classify(bodies, model);
        Map<Integer, Integer> resolvedParents = hierarchyResolvedParents(model, bodies);
        Map<Integer, Integer> childCounts = buildDirectChildCounts(resolvedParents);
        Set<Integer> hubIds = SystemMapRules.subsystemHubBodyIds(bodies, resolvedParents, classification);
        Set<Integer> revolutionCenters = SystemMapRules.orbitRevolutionCenterBodyIds(bodies, resolvedParents,
                childCounts);
        Map<Integer, Boolean> labelVisibility = buildLabelVisibility(bodies, resolvedParents, childCounts,
                classification);

        return new SystemMapModel(systemName, bodies, classification, a0, a1, positions, polylines, null,
                resolvedParents, childCounts, hubIds, revolutionCenters, labelVisibility);
    }

    public static List<OrbitPolylineWorldXY> rebuildOrbitPolylines(
            SystemMapModel base,
            Map<Integer, double[]> positionsMetres,
            int segments,
            double scalePixelsPerMetre,
            Map<Integer, double[]> ringRadiusReferencePositions,
            int viewTiltDegrees,
            Instant strokeEpoch,
            SystemSession session) {
        if (base == null || base.bodies().isEmpty()) {
            return List.of();
        }
        SystemModel model = session != null ? session.model() : null;
        ModelHandle handle = session != null ? session.handle() : null;
        if (model == null) {
            model = buildModelFromBodies(base.systemName(), base.bodies());
            handle = model != null
                    ? new ModelHandle(ModelState.INCOMPLETE, model, null, List.of())
                    : null;
        }
        Instant t = strokeEpoch != null ? strokeEpoch : Instant.now();
        return orbitPolylinesFromModel(model, handle, t, base.projectionAxis0(), base.projectionAxis1(),
                viewTiltDegrees, base.bodies(), positionsMetres, segments, scalePixelsPerMetre);
    }

    public static Map<Integer, double[]> refreshPositionsForPlayback(
            SystemMapModel base,
            Map<Integer, double[]> keplerPositions,
            Instant epoch,
            boolean freezeBarycentreStars,
            SystemSession session) {
        if (base == null || base.bodies().isEmpty()) {
            return keplerPositions;
        }
        SystemModel model = session != null ? session.model() : null;
        ModelHandle handle = session != null ? session.handle() : null;
        if (model == null) {
            model = buildModelFromBodies(base.systemName(), base.bodies());
            handle = model != null
                    ? new ModelHandle(ModelState.INCOMPLETE, model, null, List.of())
                    : null;
        }
        Instant t = epoch != null ? epoch : Instant.now();
        return positionsFromModel(handle, model, base.bodies(), t);
    }

    public static SystemMapModel playbackBase(
            Map<Integer, BodyInfo> bodies,
            int projectionAxis0,
            int projectionAxis1,
            Map<Integer, double[]> lastPositions,
            WideBinaryFlattenFrame frame) {
        SystemMapClassification clf = SystemMapRules.classify(bodies);
        Map<Integer, Integer> resolvedParents = hierarchyResolvedParents(null, bodies);
        Map<Integer, Integer> childCounts = buildDirectChildCounts(resolvedParents);
        return new SystemMapModel(null, bodies, clf, projectionAxis0, projectionAxis1,
                lastPositions != null ? lastPositions : Map.of(), List.of(), frame, resolvedParents, childCounts,
                SystemMapRules.subsystemHubBodyIds(bodies, resolvedParents, clf),
                SystemMapRules.orbitRevolutionCenterBodyIds(bodies, resolvedParents, childCounts),
                buildLabelVisibility(bodies, resolvedParents, childCounts, clf));
    }

    static SystemModel buildModelFromBodies(String systemName, Map<Integer, BodyInfo> bodies) {
        if (bodies == null || bodies.isEmpty()) {
            return null;
        }
        String name = systemName != null && !systemName.isBlank()
                ? systemName.trim()
                : "";
        List<JournalRecord> merged = CachedBodyJournalBridge.mergeMissingFromBodyInfo(name, List.of(), bodies);
        if (merged.isEmpty()) {
            return null;
        }
        return new SystemModelBuilder().systemName(name).addAll(merged).buildPartial();
    }

    private static Map<Integer, double[]> positionsFromModel(
            ModelHandle handle, SystemModel model, Map<Integer, BodyInfo> bodies, Instant t) {
        if (model == null || handle == null || handle.state() == ModelState.ERROR) {
            return new HashMap<>(SystemOrbitGeometry.bodyPositionsMetres(bodies, t, false));
        }
        Map<Integer, double[]> out = new HashMap<>(ModelMapScene.positionsMetres(handle, bodies, t));
        boolean definitiveOnly = handle.state() == ModelState.INCOMPLETE;
        Set<Integer> definitive = definitiveOnly
                ? model.definitiveSubgraph().definitiveBodyIds()
                : null;
        for (int nullId : model.barycentres().keySet()) {
            int hubKey = HierarchyKeys.baryMapKey(nullId);
            if (definitiveOnly && definitive != null && !definitive.contains(hubKey)) {
                continue;
            }
            SystemModelService.safePositionAt(handle, hubKey, t)
                    .ifPresent(p -> out.put(hubKey, p.asArray()));
        }
        return out;
    }

    private static List<OrbitPolylineWorldXY> orbitPolylinesFromModel(
            SystemModel model,
            ModelHandle handle,
            Instant t,
            int a0,
            int a1,
            int viewTiltDeg,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> positionsMetres) {
        return orbitPolylinesFromModel(model, handle, t, a0, a1, viewTiltDeg, bodies, positionsMetres, 128,
                Double.NaN);
    }

    private static List<OrbitPolylineWorldXY> orbitPolylinesFromModel(
            SystemModel model,
            ModelHandle handle,
            Instant t,
            int a0,
            int a1,
            int viewTiltDeg,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> positionsMetres,
            int legacySegments,
            double scalePixelsPerMetre) {
        if (model == null) {
            return List.of();
        }
        boolean definitiveOnly = handle != null && handle.state() == ModelState.INCOMPLETE;
        return ModelMapScene.orbitPolylines(model, bodies, positionsMetres, t, a0, a1, viewTiltDeg, definitiveOnly,
                legacySegments, scalePixelsPerMetre);
    }

    static Map<Integer, Integer> hierarchyResolvedParents(SystemModel model, Map<Integer, BodyInfo> bodies) {
        Map<Integer, Integer> resolved = new HashMap<>();
        if (bodies == null || bodies.isEmpty()) {
            return resolved;
        }
        HierarchyGraph hg = model != null ? model.hierarchy() : null;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isScanBarycentreRow()) {
                continue;
            }
            int mapKey = e.getKey().intValue();
            if (hg == null) {
                resolved.put(e.getKey(), Integer.valueOf(-1));
                continue;
            }
            Integer parent = hg.parentOf(mapKey);
            resolved.put(e.getKey(), Integer.valueOf(parent != null ? parent.intValue() : -1));
        }
        if (hg != null && model != null) {
            for (int nullId : model.barycentres().keySet()) {
                int hubKey = HierarchyKeys.baryMapKey(nullId);
                Integer parent = hg.parentOf(hubKey);
                if (parent != null) {
                    resolved.put(Integer.valueOf(hubKey), parent);
                }
                BodyInfo scanRow = bodies.get(Integer.valueOf(nullId));
                if (scanRow != null && scanRow.isScanBarycentreRow() && parent != null) {
                    resolved.put(Integer.valueOf(nullId), parent);
                }
            }
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

    private static Map<Integer, Boolean> buildLabelVisibility(
            Map<Integer, BodyInfo> bodies,
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

    private static SystemMapModel emptyModel(String systemName) {
        SystemMapClassification empty = new SystemMapClassification(SystemLayoutKind.GENERIC, 0, -1, -1, List.of());
        return new SystemMapModel(systemName, Map.of(), empty, 0, 1, Map.of(), List.of(), null, Map.of(),
                Map.of(), Set.of(), Set.of(), Map.of());
    }
}
