package org.dce.ed.systemmap;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.dce.ed.util.SystemOrbitGeometry.WideBinaryFlattenFrame;

/**
 * Immutable schematic map state for one system: classified layout, projected body positions, orbit strokes,
 * and resolved topology used by the map panel.
 * <p>
 * Built only through {@link SystemMapPipeline}. {@link org.dce.ed.ui.SystemPlanMapPanel} must read parent links,
 * branch assignment, rings, and schematic positions from this model — do not re-derive layout rules in paint code.
 */
public final class SystemMapModel {

    private final String systemName;
    private final Map<Integer, BodyInfo> bodies;
    private final SystemMapClassification classification;
    private final int projectionAxis0;
    private final int projectionAxis1;
    private final Map<Integer, double[]> positionsMetres;
    private final List<OrbitPolylineWorldXY> orbitPolylines;
    private final WideBinaryFlattenFrame wideBinaryFlattenFrame;
    private final Map<Integer, Integer> resolvedParentByBodyId;
    private final Map<Integer, Integer> directChildCountByBodyId;
    private final Set<Integer> subsystemHubBodyIds;
    private final Set<Integer> orbitRevolutionCenterBodyIds;
    private final Map<Integer, Boolean> labelVisibleWhenZoomedOut;

    SystemMapModel(String systemName,
            Map<Integer, BodyInfo> bodies,
            SystemMapClassification classification,
            int projectionAxis0,
            int projectionAxis1,
            Map<Integer, double[]> positionsMetres,
            List<OrbitPolylineWorldXY> orbitPolylines,
            WideBinaryFlattenFrame wideBinaryFlattenFrame,
            Map<Integer, Integer> resolvedParentByBodyId,
            Map<Integer, Integer> directChildCountByBodyId,
            Set<Integer> subsystemHubBodyIds,
            Set<Integer> orbitRevolutionCenterBodyIds,
            Map<Integer, Boolean> labelVisibleWhenZoomedOut) {
        this.systemName = systemName;
        this.bodies = Collections.unmodifiableMap(bodies);
        this.classification = classification;
        this.projectionAxis0 = projectionAxis0;
        this.projectionAxis1 = projectionAxis1;
        this.positionsMetres = Collections.unmodifiableMap(positionsMetres);
        this.orbitPolylines = List.copyOf(orbitPolylines);
        this.wideBinaryFlattenFrame = wideBinaryFlattenFrame;
        this.resolvedParentByBodyId = Collections.unmodifiableMap(resolvedParentByBodyId);
        this.directChildCountByBodyId = Collections.unmodifiableMap(directChildCountByBodyId);
        this.subsystemHubBodyIds = Set.copyOf(subsystemHubBodyIds);
        this.orbitRevolutionCenterBodyIds = Set.copyOf(orbitRevolutionCenterBodyIds);
        this.labelVisibleWhenZoomedOut = Collections.unmodifiableMap(labelVisibleWhenZoomedOut);
    }

    public String systemName() {
        return systemName;
    }

    public Map<Integer, BodyInfo> bodies() {
        return bodies;
    }

    public SystemMapClassification classification() {
        return classification;
    }

    public int projectionAxis0() {
        return projectionAxis0;
    }

    public int projectionAxis1() {
        return projectionAxis1;
    }

    public Map<Integer, double[]> positionsMetres() {
        return positionsMetres;
    }

    public List<OrbitPolylineWorldXY> orbitPolylines() {
        return orbitPolylines;
    }

    public WideBinaryFlattenFrame wideBinaryFlattenFrame() {
        return wideBinaryFlattenFrame;
    }

    /** Resolved orbit parent map key ({@link SystemMapRules#resolveOrbitParentBodyId}), shared by GUI and tests. */
    public int resolveParentBodyId(int bodyId) {
        Integer p = resolvedParentByBodyId.get(Integer.valueOf(bodyId));
        return p != null ? p.intValue() : -1;
    }

    public Map<Integer, Integer> resolvedParentByBodyId() {
        return resolvedParentByBodyId;
    }

    public int directChildCount(int bodyId) {
        return directChildCountByBodyId.getOrDefault(Integer.valueOf(bodyId), 0).intValue();
    }

    public boolean isSubsystemHubBody(int bodyId) {
        return subsystemHubBodyIds.contains(Integer.valueOf(bodyId));
    }

    public Set<Integer> subsystemHubBodyIds() {
        return subsystemHubBodyIds;
    }

    /** Major orbit anchor (star, giant host, planet-binary pair member) — not moons. */
    public boolean isOrbitRevolutionCenter(int bodyId) {
        return orbitRevolutionCenterBodyIds.contains(Integer.valueOf(bodyId));
    }

    public Set<Integer> orbitRevolutionCenterBodyIds() {
        return orbitRevolutionCenterBodyIds;
    }

    public boolean labelVisibleWhenZoomedOut(int bodyId, boolean starDot, boolean moon, boolean soleOrbitCluster) {
        Boolean v = labelVisibleWhenZoomedOut.get(Integer.valueOf(bodyId));
        if (v != null) {
            return v.booleanValue();
        }
        BodyInfo b = bodies.get(Integer.valueOf(bodyId));
        return b != null && SystemMapRules.bodyLabelVisibleWhenZoomedOut(b, bodyId, bodies, starDot, moon,
                soleOrbitCluster);
    }

    public boolean isPrimaryBranchBody(int bodyId) {
        return SystemOrbitGeometry.isWideBinaryPrimaryBranchBody(bodyId, bodies);
    }

    public double mapPlaneX(int bodyId) {
        double[] p = positionsMetres.get(Integer.valueOf(bodyId));
        if (p == null) {
            return Double.NaN;
        }
        return SystemOrbitGeometry.worldAxisMetres(p, projectionAxis0);
    }

    public double mapPlaneY(int bodyId) {
        double[] p = positionsMetres.get(Integer.valueOf(bodyId));
        if (p == null) {
            return Double.NaN;
        }
        return SystemOrbitGeometry.worldAxisMetres(p, projectionAxis1);
    }

    public boolean hasOrbitRingForBody(int bodyId) {
        for (OrbitPolylineWorldXY poly : orbitPolylines) {
            if (poly != null && poly.bodyId == bodyId) {
                return true;
            }
        }
        return false;
    }

    public boolean hasBarycentreMutualRing() {
        for (OrbitPolylineWorldXY poly : orbitPolylines) {
            if (poly != null && poly.bodyId == SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID) {
                return true;
            }
        }
        return false;
    }

    public boolean hasHierarchicalTripleStarTrunk() {
        for (OrbitPolylineWorldXY poly : orbitPolylines) {
            if (poly != null && poly.bodyId == SystemOrbitGeometry.HIERARCHICAL_TRIPLE_STAR_TRUNK_POLYLINE_ID) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPlanetBinaryMutualRing(int journalNullId) {
        int ringId = SystemOrbitGeometry.PLANET_BINARY_MUTUAL_ORBIT_RING_ID_BASE - journalNullId;
        for (OrbitPolylineWorldXY poly : orbitPolylines) {
            if (poly != null && poly.bodyId == ringId) {
                return true;
            }
        }
        return false;
    }

    /** Count of schematic branch rings (synthetic negative body ids from {@link SystemOrbitGeometry}). */
    public int schematicBranchRingCount() {
        int n = 0;
        for (OrbitPolylineWorldXY poly : orbitPolylines) {
            if (poly != null && poly.bodyId < -2) {
                n++;
            }
        }
        return n;
    }

    /** Barycentric wide-binary stars only (system barycentre, not inner ScanBaryCentre rows). */
    public Set<Integer> wideBinarySystemBarycentreStarIds() {
        Set<Integer> ids = new HashSet<>();
        if (!classification.wideBinary()) {
            return ids;
        }
        for (Integer sid : classification.barycentricStarIds()) {
            if (sid != null) {
                ids.add(sid);
            }
        }
        return ids;
    }
}
