package org.dce.ed.systemmap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmodel.SystemModelService;
import org.dce.ed.systemmodel.SystemModelService.ModelHandle;
import org.dce.ed.systemmodel.SystemModelService.ModelState;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.dce.systemmodel.model.OrbitRing;
import org.dce.systemmodel.model.SystemModel;

/**
 * Journal-authoritative map geometry: Kepler positions and orbit rings from {@link SystemModel}.
 */
public final class ModelMapScene {

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
            Instant t,
            int proj0,
            int proj1,
            int viewTiltDeg,
            boolean definitiveOnly) {
        if (model == null) {
            return List.of();
        }
        Set<Integer> definitiveIds = definitiveBodyIds(model, definitiveOnly);
        List<OrbitPolylineWorldXY> polys = new ArrayList<>();
        for (OrbitRing ring : model.orbitRingsAt(t)) {
            if (definitiveOnly && !definitiveIds.contains(ring.bodyId())) {
                continue;
            }
            List<double[]> pts = ring.pointsMetres();
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
            polys.add(new OrbitPolylineWorldXY(ring.bodyId(), wx, wy));
        }
        return List.copyOf(polys);
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
