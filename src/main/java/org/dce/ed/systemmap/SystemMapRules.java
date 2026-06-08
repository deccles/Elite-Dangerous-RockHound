package org.dce.ed.systemmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry;

/**
 * Testable rules for classifying a system and resolving map layout. Topology (parent links) comes from
 * {@link SystemModel} hierarchy when a {@link SystemSession} is present via {@link ModelMapTranscriber}.
 * The map GUI consumes {@link SystemMapModel} built by {@link SystemMapPipeline}.
 * <p>
 * Rule catalogue (each should have a fixture assertion):
 * <ul>
 *   <li><b>R-STELLAR-01</b> — {@link #isMapStellarBody}: journal {@code starType}, primary short name = system name,
 *       or single-letter branch {@code A}/{@code B}.</li>
 *   <li><b>R-STELLAR-02</b> — Sudarsky-tagged gas giants with atmosphere/planet class are never stars.</li>
 *   <li><b>R-LAYOUT-01</b> — {@link SystemLayoutKind#SINGLE_STAR} when exactly one map star and ≥1 orbiting
 *       body beyond 2 Ls.</li>
 *   <li><b>R-LAYOUT-02</b> — {@link SystemLayoutKind#WIDE_BINARY} when ≥2 barycentric map stars.</li>
 *   <li><b>R-PARENT-01</b> — Wide-binary companion stars parent = barycentre ({@code -1}), not arrival star.</li>
 *   <li><b>R-PARENT-02</b> — Designation suffix {@code A 1}/{@code B 3 a} resolves to branch star or host planet.</li>
 *   <li><b>R-PARENT-03</b> — In wide binaries, unresolved bodies are not forced onto the primary anchor.</li>
 *   <li><b>R-RING-01</b> — Barycentric stars do not get per-star giant rings at the origin.</li>
 *   <li><b>R-RING-02</b> — Wide binary: one mutual barycentre ring + concentric rings at each branch star for direct
 *       children.</li>
 *   <li><b>R-RING-03</b> — Moons keep per-parent rings; branch planets use branch guide rings only.</li>
 *   <li><b>R-POS-01</b> — Single-star: central star at origin; planets on map-plane circles matching rings.</li>
 *   <li><b>R-POS-02</b> — Wide binary: flatten A–B separation, recenter on stellar centroid, then branch guide
 *       planet placement.</li>
 *   <li><b>R-LABEL-01</b> — At cluster zoom, each branch shows labels for planets under that branch star (not only
 *       hub lump).</li>
 * </ul>
 * <p>
 * GUI translation ({@link org.dce.ed.ui.SystemPlanMapPanel}) — see model-to-draw plan; enforced by
 * {@code SystemPlanMapPanelDrawTranslationTest}:
 * <ul>
 *   <li><b>R-DRAW-01</b> — {@code setScene} uses {@link SystemMapPipeline#build}; Kepler positions from caller are
 *       discarded.</li>
 *   <li><b>R-DRAW-02</b> — Dots and rings use {@code model.positionsMetres()} / {@code orbitPolylines()} after rebuild;
 *       parents from {@code model.resolveParentBodyId}, not re-resolved in paint.</li>
 *   <li><b>R-DRAW-03</b> — {@link SystemMapPipeline#rebuildOrbitPolylines} passes {@code resolvedParentByBodyId} into
 *       geometry so strokes match the model contract.</li>
 *   <li><b>R-DRAW-04</b> — No {@code BINARY_BARYCENTRE_ORBIT_RING_BODY_ID} for hierarchical wide binaries; no
 *       heliocentric ~49k Ls ring around arrival star.</li>
 *   <li><b>R-DRAW-05</b> — No dots for {@code scanBarycentreRow} bodies (Null:2/3/49 metadata rows).</li>
 * </ul>
 */
public final class SystemMapRules {

    private SystemMapRules() {
    }

    public static SystemMapClassification classify(Map<Integer, BodyInfo> bodies) {
        return classify(bodies, null);
    }

    public static SystemMapClassification classify(Map<Integer, BodyInfo> bodies,
            org.dce.systemmodel.model.SystemModel model) {
        int stellar = SystemOrbitGeometry.countMapStellarBodies(bodies);
        int primary = SystemOrbitGeometry.primaryAnchorBodyMapKey(bodies);
        int central = SystemOrbitGeometry.centralStarMapKey(bodies);
        boolean singleStar = SystemOrbitGeometry.isSingleStarMap(bodies)
                || SystemOrbitGeometry.shouldApplySingleStarLayout(bodies);
        List<Integer> baryStars = barycentricMapStellarIds(bodies);
        SystemLayoutKind kind;
        if (SystemOrbitGeometry.isHierarchicalWideBinary(bodies)) {
            kind = SystemLayoutKind.WIDE_BINARY;
        } else if (baryStars.size() >= 2 && !singleStar) {
            kind = SystemLayoutKind.WIDE_BINARY;
        } else if (singleStar) {
            kind = SystemLayoutKind.SINGLE_STAR;
        } else {
            kind = SystemLayoutKind.GENERIC;
        }
        return new SystemMapClassification(kind, stellar, primary, central, List.copyOf(baryStars));
    }

    public static boolean isMapStellarBody(BodyInfo b) {
        return SystemOrbitGeometry.isMapStellarBody(b);
    }

    /**
     * Planetary ring belt scan rows ({@code 5 A Ring}, {@code bodyType: Ring}) — not map dots; ring art stays on the
     * host planet.
     */
    public static boolean isPlanetaryRingMapBody(BodyInfo b) {
        return SystemOrbitGeometry.isPlanetaryRingMapBody(b);
    }

    public static int resolveOrbitParentBodyId(BodyInfo child, Map<Integer, BodyInfo> bodies, int mapBodyId) {
        return SystemOrbitGeometry.resolveOrbitParentBodyId(child, bodies, mapBodyId);
    }

    public static int branchStarOrbitHubId(Map<Integer, BodyInfo> bodies, int parentMapId) {
        return SystemOrbitGeometry.branchStarOrbitHubId(bodies, parentMapId);
    }

    /** Whether a body belongs on the primary (arrival) wide-binary branch, not the companion cluster. */
    public static boolean isWideBinaryPrimaryBranchBody(int bodyId, Map<Integer, BodyInfo> bodies) {
        return SystemOrbitGeometry.isWideBinaryPrimaryBranchBody(bodyId, bodies);
    }

    /**
     * Moon-host hubs for lump view: parents of satellite moons, excluding wide-binary branch stars (computed once in
     * {@link SystemMapPipeline}).
     */
    /**
     * Bodies whose labels may show at subsystem / cluster zoom before moon designations appear: branch stars,
     * moon-host giants, direct planets under a star, and planet-binary mutual members (e.g. BCD 2 and BCD 3).
     */
    public static Set<Integer> orbitRevolutionCenterBodyIds(Map<Integer, BodyInfo> bodies,
            Map<Integer, Integer> resolvedParents,
            Map<Integer, Integer> directChildCounts) {
        Set<Integer> centers = new HashSet<>();
        if (bodies == null || bodies.isEmpty()) {
            return centers;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isScanBarycentreRow()) {
                continue;
            }
            int id = e.getKey().intValue();
            BodyInfo b = e.getValue();
            if (isMapStellarBody(b)) {
                centers.add(e.getKey());
                continue;
            }
            if (SystemOrbitGeometry.isMoonSatelliteBody(b, bodies)) {
                continue;
            }
            int children = directChildCounts != null
                    ? directChildCounts.getOrDefault(e.getKey(), Integer.valueOf(0)).intValue()
                    : 0;
            if (children > 0) {
                centers.add(e.getKey());
                continue;
            }
            int pId = resolvedParents != null
                    ? resolvedParents.getOrDefault(e.getKey(), Integer.valueOf(-1)).intValue()
                    : -1;
            if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(pId)) {
                centers.add(e.getKey());
                continue;
            }
            if (pId >= 0) {
                BodyInfo parent = bodies.get(Integer.valueOf(pId));
                if (parent != null && isMapStellarBody(parent)) {
                    centers.add(e.getKey());
                }
            }
        }
        return centers;
    }

    public static Set<Integer> subsystemHubBodyIds(Map<Integer, BodyInfo> bodies,
            Map<Integer, Integer> resolvedParents,
            SystemMapClassification classification) {
        Set<Integer> hubs = new HashSet<>();
        if (bodies == null || bodies.isEmpty() || resolvedParents == null) {
            return hubs;
        }
        int loneStarCentral = classification.singleStar()
                ? classification.centralStarId()
                : -1;
        boolean wideBinary = classification.wideBinary();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (!SystemOrbitGeometry.isMoonSatelliteBody(e.getValue(), bodies)) {
                continue;
            }
            int mapId = e.getKey().intValue();
            int pId = resolveOrbitParentBodyId(e.getValue(), bodies, mapId);
            if (pId < 0 || pId == loneStarCentral) {
                continue;
            }
            if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(pId)) {
                int hostHub = SystemOrbitGeometry.planetBinaryBarycentreHierarchyParentMapKey(pId, bodies);
                if (hostHub >= 0 && hostHub != loneStarCentral) {
                    BodyInfo host = bodies.get(Integer.valueOf(hostHub));
                    if (host != null && !isMapStellarBody(host)) {
                        hubs.add(Integer.valueOf(hostHub));
                    }
                }
                continue;
            }
            if (wideBinary && isWideBinaryBranchStarHub(bodies, pId)) {
                continue;
            }
            BodyInfo parent = bodies.get(Integer.valueOf(pId));
            if (parent != null && isMapStellarBody(parent)) {
                continue;
            }
            hubs.add(Integer.valueOf(pId));
        }
        return hubs;
    }

    /**
     * Whether a body label should be drawn when the map is zoomed out (cluster / subsystem lump view).
     */
    public static boolean bodyLabelVisibleWhenZoomedOut(BodyInfo body, int mapBodyId, Map<Integer, BodyInfo> bodies,
            boolean starDot, boolean moon, boolean soleOrbitCluster) {
        if (bodies == null || body == null) {
            return false;
        }
        SystemMapClassification clf = classify(bodies);
        if (clf.wideBinary() && SystemOrbitGeometry.isHierarchicalWideBinary(bodies)) {
            return starDot;
        }
        if (!starDot && soleOrbitCluster) {
            return true;
        }
        if (starDot) {
            return true;
        }
        if (clf.singleStar()) {
            if (mapBodyId != clf.centralStarId()) {
                return true;
            }
            return false;
        }
        if (clf.wideBinary()) {
            int pId = resolveOrbitParentBodyId(body, bodies, mapBodyId);
            if (pId >= 0) {
                BodyInfo parent = bodies.get(Integer.valueOf(pId));
                if (parent != null && isMapStellarBody(parent)
                        && resolveOrbitParentBodyId(parent, bodies, pId) < 0) {
                    return true;
                }
                if (branchStarOrbitHubId(bodies, pId) >= 0) {
                    return true;
                }
            }
            if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(pId)) {
                return true;
            }
        } else if (clf.mapStellarCount() == 1 && clf.primaryAnchorBodyId() >= 0) {
            int pId = resolveOrbitParentBodyId(body, bodies, mapBodyId);
            if (pId == clf.primaryAnchorBodyId()) {
                return true;
            }
        }
        return false;
    }

    /** Body ids that are parents of other bodies but should not trigger hub-lump label suppression. */
    public static boolean isWideBinaryBranchStarHub(Map<Integer, BodyInfo> bodies, int parentMapId) {
        if (bodies == null || parentMapId < 0 || SystemOrbitGeometry.countMapStellarBodies(bodies) < 2) {
            return false;
        }
        BodyInfo parent = bodies.get(Integer.valueOf(parentMapId));
        return parent != null && isMapStellarBody(parent)
                && SystemOrbitGeometry.orbitsWideBinarySystemBarycentre(parent, bodies, parentMapId);
    }

    private static List<Integer> barycentricMapStellarIds(Map<Integer, BodyInfo> bodies) {
        List<Integer> ids = new ArrayList<>();
        if (bodies == null) {
            return ids;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            if (!isMapStellarBody(e.getValue())) {
                continue;
            }
            if (SystemOrbitGeometry.orbitsWideBinarySystemBarycentre(e.getValue(), bodies, id)) {
                ids.add(Integer.valueOf(id));
            }
        }
        return ids;
    }

    /**
     * Map key for a journal {@code BodyID} / HUD destination id. Prefers a drawable planet or star over scan-only
     * barycentre or ring rows when several entries share the same {@link BodyInfo#getBodyId()}.
     */
    public static Integer mapKeyForJournalBodyId(Map<Integer, BodyInfo> bodies, int journalBodyId) {
        if (bodies == null || journalBodyId <= 0) {
            return null;
        }
        Integer scanOrDecor = null;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (e.getValue().getBodyId() != journalBodyId) {
                continue;
            }
            BodyInfo b = e.getValue();
            if (b.isScanBarycentreRow() || isPlanetaryRingMapBody(b)) {
                scanOrDecor = e.getKey();
                continue;
            }
            return e.getKey();
        }
        if (scanOrDecor != null) {
            return scanOrDecor;
        }
        Integer direct = Integer.valueOf(journalBodyId);
        return bodies.containsKey(direct) ? direct : null;
    }
}
