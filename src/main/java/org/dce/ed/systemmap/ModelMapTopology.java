package org.dce.ed.systemmap;

import java.util.HashMap;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.systemmodel.model.BodyKind;
import org.dce.systemmodel.model.BodyNode;
import org.dce.systemmodel.model.HierarchyGraph;
import org.dce.systemmodel.model.HierarchyKeys;
import org.dce.systemmodel.model.SystemModel;
/**
 * Resolves map parent keys from journal-authoritative {@link SystemModel#hierarchy()}.
 */
public final class ModelMapTopology {

    private ModelMapTopology() {
    }

    /**
     * Map keys match {@link SystemMapModel#resolveParentBodyId}: body ids and synthetic Null hub keys
     * ({@link SystemOrbitGeometry#planetBinaryBarycentreMapKey}).
     */
    public static Map<Integer, Integer> resolvedParents(SystemModel model, Map<Integer, BodyInfo> bodies) {
        Map<Integer, Integer> resolved = new HashMap<>();
        if (model == null || bodies == null || bodies.isEmpty()) {
            return resolved;
        }
        HierarchyGraph hg = model.hierarchy();
        int primaryId = primaryStarBodyId(model, bodies);
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int mapKey = e.getKey().intValue();
            if (e.getValue().isScanBarycentreRow()) {
                continue;
            }
            Integer parent = hg.parentOf(mapKey);
            if (parent == null) {
                resolved.put(Integer.valueOf(mapKey), Integer.valueOf(-1));
                continue;
            }
            int parentKey = parent.intValue();
            if (parentKey == HierarchyKeys.baryMapKey(0)) {
                if (mapKey == primaryId) {
                    resolved.put(Integer.valueOf(mapKey), Integer.valueOf(-1));
                } else if (isMapStellarBranch(e.getValue())) {
                    // Wide-binary companion stars orbit the barycentre, not the arrival star (avoids a spurious
                    // Kepler ring around A from B's journal barycentric elements).
                    resolved.put(Integer.valueOf(mapKey), Integer.valueOf(-1));
                } else {
                    resolved.put(Integer.valueOf(mapKey), Integer.valueOf(primaryId >= 0 ? primaryId : -1));
                }
                continue;
            }
            resolved.put(Integer.valueOf(mapKey), Integer.valueOf(parentKey));
        }
        for (int journalNullId : model.barycentres().keySet()) {
            int hubKey = SystemOrbitGeometry.planetBinaryBarycentreMapKey(journalNullId);
            Integer parent = hg.parentOf(HierarchyKeys.baryMapKey(journalNullId));
            if (parent != null) {
                resolved.put(Integer.valueOf(hubKey), parent);
            }
            BodyInfo scanRow = bodies.get(Integer.valueOf(journalNullId));
            if (scanRow != null && scanRow.isScanBarycentreRow() && parent != null) {
                resolved.put(Integer.valueOf(journalNullId), parent);
            }
        }
        return resolved;
    }

    private static int primaryStarBodyId(SystemModel model, Map<Integer, BodyInfo> bodies) {
        int fromBodies = SystemOrbitGeometry.primaryAnchorBodyMapKey(bodies);
        if (fromBodies >= 0) {
            return fromBodies;
        }
        for (BodyNode b : model.bodies().values()) {
            if (b.kind() == BodyKind.STAR) {
                Integer p = model.hierarchy().parentOf(b.bodyId());
                if (p != null && p.intValue() == HierarchyKeys.baryMapKey(0)) {
                    return b.bodyId();
                }
            }
        }
        return -1;
    }

    private static boolean isMapStellarBranch(BodyInfo b) {
        return SystemMapRules.isMapStellarBody(b);
    }
}
