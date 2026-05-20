package org.dce.ed.util;

import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmap.MapScaleMode;
import org.dce.ed.systemmap.MapViewProjection;

/**
 * Approximate in-system body positions from journal orbital elements and hierarchy,
 * for ship-centric distance ordering on the System tab.
 * <p>
 * Units: positions in metres; distances converted to light-seconds using {@link #LIGHT_SECOND_METRES}.
 */
public final class SystemOrbitGeometry {

    /** Elite "Ls" length in metres (one light-second). */
    public static final double LIGHT_SECOND_METRES = 299792458.0;

    /** Standard gravitational parameter (m³/s²) for schematic period from semi-major axis. */
    private static final double STANDARD_GRAVITATIONAL_PARAMETER_SUN_M3_S2 = 1.32712440018e20;

    /** Reference epoch when journal {@code OrbitalEpoch} is absent — sim/wall {@code now} advances mean anomaly. */
    private static final long SCHEMATIC_ORBIT_EPOCH_MILLIS = 946684800000L;

    private SystemOrbitGeometry() {
    }

    /**
     * Euclidean distance from {@code pointM} to each body's approximate centre (Ls).
     * Missing positions are omitted from the map.
     */
    public static Map<Integer, Double> distancesFromPointLs(Map<Integer, BodyInfo> bodies,
            double[] pointM) {
        Map<Integer, double[]> pos = bodyPositionsMetres(bodies, Instant.now());
        Map<Integer, Double> out = new HashMap<>();
        if (pointM == null || pointM.length < 3 || pos.isEmpty()) {
            return out;
        }
        for (Map.Entry<Integer, double[]> e : pos.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            double dx = e.getValue()[0] - pointM[0];
            double dy = e.getValue()[1] - pointM[1];
            double dz = e.getValue()[2] - pointM[2];
            double m = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!Double.isFinite(m)) {
                continue;
            }
            out.put(e.getKey(), Double.valueOf(m / LIGHT_SECOND_METRES));
        }
        return out;
    }

    /**
     * Positions of body centres relative to an approximate system origin at the primary (body id 0 when present).
     * Uses {@link Instant#now()} — prefer {@link #bodyPositionsMetres(Map, Instant)} for tests or a fixed clock.
     */
    public static Map<Integer, double[]> bodyPositionsMetres(Map<Integer, BodyInfo> bodies) {
        return bodyPositionsMetres(bodies, Instant.now());
    }

    /**
     * Same as {@link #bodyPositionsMetres(Map)} but mean anomaly is evolved to {@code now} using journal period/epoch
     * when present, otherwise a schematic period from semi-major axis (or arrival distance) and a fixed reference epoch.
     */
    public static Map<Integer, double[]> bodyPositionsMetres(Map<Integer, BodyInfo> bodies, Instant now) {
        return bodyPositionsMetres(bodies, now, false);
    }

    /**
     * @param freezeBarycentreStars when true, stars that orbit the system barycentre (parent {@code -1}) keep a fixed
     *        mean anomaly so schematic playback does not spin the wide-binary frame or its ring.
     */
    public static Map<Integer, double[]> bodyPositionsMetres(Map<Integer, BodyInfo> bodies, Instant now,
            boolean freezeBarycentreStars) {
        Map<Integer, double[]> memo = new HashMap<>();
        Set<Integer> visiting = new HashSet<>();
        if (bodies == null || bodies.isEmpty()) {
            return memo;
        }
        boolean loneStarSchematic = shouldApplyLoneStarSchematicLayout(bodies);
        int maxDepth = bodies.size() + 32;
        Instant t = now != null ? now : Instant.now();
        for (Integer id : bodies.keySet()) {
            if (id != null) {
                positionRecursive(id.intValue(), bodies, memo, visiting, t, freezeBarycentreStars, loneStarSchematic,
                        0, maxDepth);
            }
        }
        return memo;
    }

    /**
     * Lone-star schematic: place the central star at the origin and each planet on a circle in the map plane so dots
     * lie on the orbit strokes ({@link #appendSingleStarSchematicRings}). Call after map projection axes are chosen.
     */
    public static Map<Integer, double[]> bodyPositionsMetresForSingleStarMap(Map<Integer, BodyInfo> bodies,
            Instant now,
            int mapProjA0,
            int mapProjA1,
            boolean freezeBarycentreStars) {
        if (!shouldApplyLoneStarSchematicLayout(bodies)) {
            return bodyPositionsMetres(bodies, now, freezeBarycentreStars);
        }
        int central = schematicCentralStarMapKey(bodies);
        if (central < 0) {
            return bodyPositionsMetres(bodies, now, freezeBarycentreStars);
        }
        int p0 = clampWorldAxisIndex(mapProjA0);
        int p1 = clampWorldAxisIndex(mapProjA1);
        if (p0 == p1) {
            p1 = p0 == 2 ? 1 : 2;
        }
        Map<Integer, double[]> memo = new HashMap<>();
        memo.put(Integer.valueOf(central), new double[] { 0.0, 0.0, 0.0 });
        Instant t = now != null ? now : Instant.now();
        seedPlanetBinaryBarycentresForSingleStarMap(bodies, memo, central, t, p0, p1, freezeBarycentreStars);
        boolean placed;
        do {
            placed = false;
            for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                Integer key = e.getKey();
                if (memo.containsKey(key)) {
                    continue;
                }
                int id = key.intValue();
                BodyInfo b = e.getValue();
                if (id == central || b.isScanBarycentreRow()) {
                    continue;
                }
                int pId = resolveOrbitParentBodyId(b, bodies, id);
                if (!isPlanetBinaryBarycentreMapKey(pId)
                        && (pId < 0 || !bodies.containsKey(Integer.valueOf(pId)))) {
                    if (!isMapStellarBody(b)) {
                        pId = central;
                    } else {
                        continue;
                    }
                }
                double[] parentPos = memo.get(Integer.valueOf(pId));
                if (parentPos == null) {
                    continue;
                }
                double[] rel;
                if (!isMapStellarBody(b) && pId == central) {
                    rel = schematicMapPlaneOffsetMetres(b, id, bodies, central, t, p0, p1, freezeBarycentreStars);
                } else if (isPlanetBinaryBarycentreMapKey(pId)
                        && (!isMapStellarBody(b) || isHierarchicalWideBinary(bodies))) {
                    rel = planetBinaryOffsetFromBarycentreMetres(b, id, bodies,
                            journalNullIdFromPlanetBinaryBarycentreMapKey(pId), t, p0, p1, freezeBarycentreStars,
                            memo.get(Integer.valueOf(central)), parentPos);
                } else {
                    rel = orbitalDisplacementMetres(b, id, t, bodies, freezeBarycentreStars);
                    if (!isFiniteXYZ(rel)) {
                        rel = pseudoOffsetMetresAtTime(b, id, bodies, pId, t, freezeBarycentreStars);
                    }
                }
                memo.put(key, combineParentAndRelativeOffset(parentPos, rel, p0, p1));
                placed = true;
            }
        } while (placed);
        alignPlanetBinaryGroupsOnMapPlane(memo, bodies, t, p0, p1, freezeBarycentreStars);
        alignMoonsOnSchematicRingsAroundParents(memo, bodies, t, p0, p1, freezeBarycentreStars);
        return memo;
    }

    /**
     * Wide-binary schematic: keep barycentric stars where {@code starAnchoredPositions} already placed them (after
     * flatten + recenter), and put each branch's planets on map-plane circles so dots sit on branch schematic rings.
     */
    public static Map<Integer, double[]> bodyPositionsMetresForWideBinaryMap(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> starAnchoredPositions,
            Instant now,
            int mapProjA0,
            int mapProjA1,
            boolean freezeBarycentreStars) {
        if (bodies == null || bodies.isEmpty() || starAnchoredPositions == null || starAnchoredPositions.isEmpty()) {
            return starAnchoredPositions;
        }
        if (countMapStellarBodies(bodies) < 2 && !isHierarchicalWideBinary(bodies)) {
            return starAnchoredPositions;
        }
        int p0 = clampWorldAxisIndex(mapProjA0);
        int p1 = clampWorldAxisIndex(mapProjA1);
        if (p0 == p1) {
            p1 = p0 == 2 ? 1 : 2;
        }
        Map<Integer, double[]> memo = new HashMap<>();
        for (Map.Entry<Integer, double[]> e : starAnchoredPositions.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int starId = e.getKey().intValue();
            BodyInfo star = bodies.get(e.getKey());
            if (star == null || !isMapStellarBody(star)) {
                continue;
            }
            /* Only anchor system-barycentre stars from flatten; companion-branch stars are placed on inner rings. */
            if (!orbitsWideBinarySystemBarycentre(star, bodies, starId)) {
                continue;
            }
            double[] sp = e.getValue();
            if (sp.length >= 3 && isFiniteXYZ(sp)) {
                memo.put(e.getKey(), new double[] {
                        worldAxisMetres(sp, 0),
                        worldAxisMetres(sp, 1),
                        worldAxisMetres(sp, 2)
                });
            }
        }
        if (countMapStellarBodies(bodies) < 2 && !isHierarchicalWideBinary(bodies)) {
            return starAnchoredPositions;
        }
        Instant t = now != null ? now : Instant.now();
        int primaryStarId = primaryAnchorBodyMapKey(bodies);
        if (primaryStarId >= 0 && !isHierarchicalWideBinary(bodies)) {
            seedPrimaryBranchPlanetBinaryBarycentresAtStar(bodies, memo, primaryStarId, t, p0, p1,
                    freezeBarycentreStars);
        }
        seedSharedNullBarycentresForWideBinaryMap(bodies, memo, primaryStarId, starAnchoredPositions, t, p0, p1,
                freezeBarycentreStars);
        placeSharedNullStellarMembersOnMutualOrbit(memo, bodies, t, p0, p1, freezeBarycentreStars);
        boolean placed;
        do {
            placed = false;
            for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                Integer key = e.getKey();
                if (memo.containsKey(key)) {
                    continue;
                }
                int id = key.intValue();
                BodyInfo b = e.getValue();
                if (isMapStellarBody(b) && orbitsWideBinarySystemBarycentre(b, bodies, id)) {
                    continue;
                }
                int pId = resolveOrbitParentBodyId(b, bodies, id);
                if (pId < 0 && !isPlanetBinaryBarycentreMapKey(pId)) {
                    continue;
                }
                if (pId >= 0 && !bodies.containsKey(Integer.valueOf(pId))) {
                    continue;
                }
                double[] parentPos = memo.get(Integer.valueOf(pId));
                if (parentPos == null) {
                    continue;
                }
                double[] rel;
                int branchStar = branchSchematicStarParentId(bodies, pId);
                int schematicStar = branchStar;
                if (schematicStar < 0 && isHierarchicalWideBinary(bodies) && primaryStarId >= 0 && pId == primaryStarId
                        && isWideBinaryPrimaryBranchBody(id, primaryStarId, bodies) && !isMapStellarBody(b)) {
                    schematicStar = primaryStarId;
                }
                if (schematicStar >= 0 && pId == schematicStar && !isMapStellarBody(b)) {
                    rel = schematicMapPlaneOffsetMetres(b, id, bodies, schematicStar, t, p0, p1, freezeBarycentreStars);
                } else if (isPlanetBinaryBarycentreMapKey(pId)) {
                    rel = planetBinaryOffsetFromBarycentreMetres(b, id, bodies,
                            journalNullIdFromPlanetBinaryBarycentreMapKey(pId), t, p0, p1, freezeBarycentreStars,
                            null, memo.get(Integer.valueOf(pId)));
                } else if (isMoonSatelliteBody(b, bodies) && pId >= 0
                        && branchSchematicStarParentId(bodies, pId) < 0) {
                    rel = schematicMapPlaneOffsetMetres(b, id, bodies, pId, t, p0, p1, freezeBarycentreStars);
                } else {
                    rel = orbitalDisplacementMetres(b, id, t, bodies, freezeBarycentreStars);
                    if (!isFiniteXYZ(rel)) {
                        rel = pseudoOffsetMetresAtTime(b, id, bodies, pId, t, freezeBarycentreStars);
                    }
                }
                if (!isFiniteXYZ(rel)) {
                    continue;
                }
                memo.put(key, combineParentAndRelativeOffset(parentPos, rel, p0, p1));
                placed = true;
            }
        } while (placed);
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            BodyInfo b = e.getValue();
            if (b.isScanBarycentreRow() || isMapStellarBody(b)) {
                continue;
            }
            int pId = resolveOrbitParentBodyId(b, bodies, id);
            if (!isPlanetBinaryBarycentreMapKey(pId)) {
                continue;
            }
            double[] parentPos = memo.get(Integer.valueOf(pId));
            if (parentPos == null) {
                continue;
            }
            double[] rel = planetBinaryOffsetFromBarycentreMetres(b, id, bodies,
                    journalNullIdFromPlanetBinaryBarycentreMapKey(pId), t, p0, p1, freezeBarycentreStars,
                    null, parentPos);
            if (!isFiniteXYZ(rel)) {
                continue;
            }
            memo.put(e.getKey(), combineParentAndRelativeOffset(parentPos, rel, p0, p1));
        }
        for (Map.Entry<Integer, double[]> e : starAnchoredPositions.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || memo.containsKey(e.getKey())) {
                continue;
            }
            BodyInfo b = bodies.get(e.getKey());
            if (b != null && isMapStellarBody(b)
                    && !orbitsWideBinarySystemBarycentre(b, bodies, e.getKey().intValue())) {
                continue;
            }
            double[] p = e.getValue();
            if (p.length >= 3 && isFiniteXYZ(p)) {
                memo.put(e.getKey(), new double[] {
                        worldAxisMetres(p, 0),
                        worldAxisMetres(p, 1),
                        worldAxisMetres(p, 2)
                });
            }
        }
        if (!isHierarchicalWideBinary(bodies)) {
            alignPlanetBinaryGroupsOnMapPlane(memo, bodies, t, p0, p1, freezeBarycentreStars);
        }
        alignMoonsOnSchematicRingsAroundParents(memo, bodies, t, p0, p1, freezeBarycentreStars);
        return memo;
    }

    /**
     * After wide-binary flatten, re-seat direct children of the primary star (A 1, A 2, …) on journal-radius
     * schematic circles so dots match {@link #appendSchematicRingsAtStar} (flatten can shorten map-plane separation).
     */
    public static void alignPrimaryBranchPlanetsOnSchematicRings(Map<Integer, double[]> positions,
            Map<Integer, BodyInfo> bodies,
            Instant now,
            int p0,
            int p1,
            boolean freezeBarycentreStars) {
        if (positions == null || bodies == null || !isHierarchicalWideBinary(bodies)) {
            return;
        }
        int primaryId = primaryAnchorBodyMapKey(bodies);
        if (primaryId < 0) {
            return;
        }
        double[] parentPos = positions.get(Integer.valueOf(primaryId));
        if (parentPos == null) {
            return;
        }
        BodyInfo anchor = bodies.get(Integer.valueOf(primaryId));
        String anchorBranch = branchLetterOfStellarBody(anchor, bodies);
        if (anchorBranch == null && anchor != null) {
            anchorBranch = designationBranchLetter(anchor);
        }
        Instant t = now != null ? now : Instant.now();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            BodyInfo b = e.getValue();
            if (b.isScanBarycentreRow() || isMapStellarBody(b) || isMoonSatelliteBody(b, bodies)) {
                continue;
            }
            int mapId = e.getKey().intValue();
            if (isStellarDirectNullMember(b, bodies)) {
                continue;
            }
            String branch = designationBranchLetter(b);
            if (branch == null || anchorBranch == null || !anchorBranch.equalsIgnoreCase(branch)) {
                continue;
            }
            /*
             * Always seat on primary-star schematic rings. Live cache may parent A 2/A 3 to C; resolveOrbitParentBodyId
             * can then return companion Null:3 barycentre at the B/C cluster — using that hub parks A planets on C.
             */
            int centerId = primaryId;
            double[] centerPos = parentPos;
            double[] rel = schematicMapPlaneOffsetMetres(b, mapId, bodies, centerId, t, p0, p1, freezeBarycentreStars);
            if (!isFiniteXYZ(rel)) {
                continue;
            }
            positions.put(e.getKey(), combineParentAndRelativeOffset(centerPos, rel, p0, p1));
        }
    }

    /**
     * Moons around gas giants (A 2 a, A 3 a, …): journal parent-relative distance on the map plane, not Kepler SMA
     * which often places them far from the host (Eor Aowsy A 2 a ~5 Ls journal vs ~22 Ls Kepler).
     */
    public static void alignMoonsOnSchematicRingsAroundParents(Map<Integer, double[]> positions,
            Map<Integer, BodyInfo> bodies,
            Instant now,
            int p0,
            int p1,
            boolean freezeBarycentreStars) {
        if (positions == null || bodies == null) {
            return;
        }
        Instant t = now != null ? now : Instant.now();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            BodyInfo b = e.getValue();
            if (b.isScanBarycentreRow() || !isMoonSatelliteBody(b, bodies)) {
                continue;
            }
            int mapId = e.getKey().intValue();
            int pId = resolveOrbitParentBodyId(b, bodies, mapId);
            if (pId < 0 || isPlanetBinaryBarycentreMapKey(pId)) {
                continue;
            }
            double[] parentPos = positions.get(Integer.valueOf(pId));
            if (parentPos == null) {
                continue;
            }
            double[] rel = schematicMapPlaneOffsetMetres(b, mapId, bodies, pId, t, p0, p1, freezeBarycentreStars);
            if (!isFiniteXYZ(rel)) {
                continue;
            }
            positions.put(e.getKey(), combineParentAndRelativeOffset(parentPos, rel, p0, p1));
        }
    }

    /**
     * After schematic placement, snap each planet-binary barycentre to its members' centroid and place majors on the
     * mutual-orbit circle so dots match {@link #appendPlanetBinaryMutualOrbitRings}.
     */
    public static void alignPlanetBinaryGroupsOnMapPlane(Map<Integer, double[]> positions, Map<Integer, BodyInfo> bodies,
            Instant now, int p0, int p1, boolean freezeBarycentreStars) {
        if (positions == null || bodies == null) {
            return;
        }
        HashSet<Integer> nullParents = new HashSet<>();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || isMoonSatelliteBody(e.getValue(), bodies)) {
                continue;
            }
            int ip = e.getValue().getImmediateParentBodyId();
            if (isPlanetBinaryNullParentRef(ip, bodies)) {
                nullParents.add(Integer.valueOf(ip));
            }
            int resolved = resolveOrbitParentBodyId(e.getValue(), bodies, e.getKey().intValue());
            if (isPlanetBinaryBarycentreMapKey(resolved)) {
                nullParents.add(Integer.valueOf(PLANET_BINARY_BARYCENTRE_MAP_KEY_BASE - resolved));
            }
        }
        if (isHierarchicalTripleStarMap(bodies)) {
            int tripleNull = hierarchicalTripleStellarNullId(bodies);
            if (tripleNull > 0) {
                nullParents.add(Integer.valueOf(tripleNull));
            }
        }
        for (Integer nullIdObj : nullParents) {
            int nullId = nullIdObj.intValue();
            boolean tripleInnerNull = isHierarchicalTripleStarMap(bodies)
                    && hierarchicalTripleStellarNullId(bodies) == nullId;
            if (!isSharedNullBarycentreId(nullId, bodies) && !isPlanetBinaryNullParentId(nullId, bodies)
                    && !tripleInnerNull) {
                continue;
            }
            if (isNestedStellarInnerNullOfOuterPair(nullId, bodies)) {
                /*
                 * Inner B+C at Null:3 is aligned inside {@link #alignHierarchicalOuterStellarNullPair}, but a later
                 * {@link #placeHierarchicalTripleStarCluster} pass shifts the hub and stars — re-seat on the mutual
                 * ring whenever this is the triple inner null (Eor Aowsy / live cache).
                 */
                boolean tripleInnerStellarNull = isHierarchicalTripleStarMap(bodies)
                        && hierarchicalTripleStellarNullId(bodies) == nullId;
                if (countStellarDirectNullMembers(nullId, bodies) < 2 || tripleInnerStellarNull) {
                    alignSinglePlanetBinaryNullGroup(positions, bodies, nullId, now, p0, p1, freezeBarycentreStars,
                            true);
                }
                continue;
            }
            if (isNestedPlanetBinaryNullUnderOuterTrunk(nullId, bodies)) {
                continue;
            }
            if (isHierarchicalOuterStellarNullPair(nullId, bodies)) {
                alignHierarchicalOuterStellarNullPair(positions, bodies, nullId, now, p0, p1, freezeBarycentreStars);
                continue;
            }
            alignSinglePlanetBinaryNullGroup(positions, bodies, nullId, now, p0, p1, freezeBarycentreStars, true);
        }
    }

    /** Inner Null (e.g. 3) already aligned inside {@link #alignHierarchicalOuterStellarNullPair}. */
    private static boolean isNestedStellarInnerNullOfOuterPair(int innerNullId, Map<Integer, BodyInfo> bodies) {
        if (!isHierarchicalWideBinary(bodies) || innerNullId <= 0) {
            return false;
        }
        HashSet<Integer> outerCandidates = new HashSet<>();
        for (BodyInfo b : bodies.values()) {
            if (b == null || isMoonSatelliteBody(b, bodies)) {
                continue;
            }
            int ip = b.getImmediateParentBodyId();
            if (isPlanetBinaryNullParentRef(ip, bodies)) {
                outerCandidates.add(Integer.valueOf(ip));
            }
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() != null && e.getValue() != null && e.getValue().isScanBarycentreRow()) {
                outerCandidates.add(e.getKey());
            }
        }
        for (Integer outerIdObj : outerCandidates) {
            int outerId = outerIdObj.intValue();
            if (outerId == innerNullId) {
                continue;
            }
            if (isHierarchicalOuterStellarNullPair(outerId, bodies)
                    && nestedStellarInnerNullIds(outerId, bodies).contains(Integer.valueOf(innerNullId))) {
                return true;
            }
        }
        return false;
    }

    /**
     * True-scale map: place each planet-binary {@code Null:N} map key at its members' centroid without moving majors
     * onto a schematic mutual circle (journal Kepler positions stay intact).
     */
    public static void snapPlanetBinaryBarycentreCentroidsOnMapPlane(Map<Integer, double[]> positions,
            Map<Integer, BodyInfo> bodies, int p0, int p1) {
        if (positions == null || bodies == null) {
            return;
        }
        HashSet<Integer> nullParents = new HashSet<>();
        for (BodyInfo b : bodies.values()) {
            if (b == null || isMoonSatelliteBody(b, bodies)) {
                continue;
            }
            int ip = b.getImmediateParentBodyId();
            if (isPlanetBinaryNullParentRef(ip, bodies)) {
                nullParents.add(Integer.valueOf(ip));
            }
        }
        for (Integer nullIdObj : nullParents) {
            int nullId = nullIdObj.intValue();
            if (!isPlanetBinaryNullParentId(nullId, bodies) || isHierarchicalWideBinary(bodies)) {
                continue;
            }
            alignSinglePlanetBinaryNullGroup(positions, bodies, nullId, Instant.now(), p0, p1, false, true, false);
        }
    }

    /** One Null group: majors on the mutual-orbit circle; optionally snap barycentre to member centroid first. */
    private static void alignSinglePlanetBinaryNullGroup(Map<Integer, double[]> positions, Map<Integer, BodyInfo> bodies,
            int nullId, Instant now, int p0, int p1, boolean freezeBarycentreStars, boolean snapBarycentreToCentroid) {
        alignSinglePlanetBinaryNullGroup(positions, bodies, nullId, now, p0, p1, freezeBarycentreStars,
                snapBarycentreToCentroid, true);
    }

    private static void alignSinglePlanetBinaryNullGroup(Map<Integer, double[]> positions, Map<Integer, BodyInfo> bodies,
            int nullId, Instant now, int p0, int p1, boolean freezeBarycentreStars, boolean snapBarycentreToCentroid,
            boolean repositionMembersOnMutualRing) {
        int bKey = planetBinaryBarycentreMapKey(nullId);
        boolean tripleStellarNull = isHierarchicalTripleStarMap(bodies)
                && hierarchicalTripleStellarNullId(bodies) == nullId;
        int needLen = Math.max(3, Math.max(p0, p1) + 1);
        double[] bary = positions.get(Integer.valueOf(bKey));
        boolean schematicNestedPlanetPair = isNestedPlanetBinaryNullUnderOuterTrunk(nullId, bodies);
        if (snapBarycentreToCentroid && !tripleStellarNull && !schematicNestedPlanetPair) {
            double[] centroid = planetBinaryMemberCentroidWorldXY(nullId, bodies, positions, p0, p1);
            if (centroid != null) {
                if (bary == null || bary.length < needLen) {
                    bary = new double[needLen];
                } else {
                    bary = Arrays.copyOf(bary, Math.max(needLen, bary.length));
                }
                bary[p0] = centroid[0];
                bary[p1] = centroid[1];
                positions.put(Integer.valueOf(bKey), bary);
                BodyInfo scanRow = bodies.get(Integer.valueOf(nullId));
                if (scanRow != null && scanRow.isScanBarycentreRow()) {
                    positions.put(Integer.valueOf(nullId), Arrays.copyOf(bary, bary.length));
                }
            }
        }
        if (!repositionMembersOnMutualRing) {
            return;
        }
        if (bary == null || bary.length < needLen) {
            bary = positions.get(Integer.valueOf(bKey));
            if (bary == null || bary.length < needLen) {
                return;
            }
        }
        boolean schematicStellarPair = (isHierarchicalWideBinary(bodies)
                && isNestedStellarInnerNullOfOuterPair(nullId, bodies))
                || (isHierarchicalTripleStarMap(bodies)
                        && hierarchicalTripleStellarNullId(bodies) == nullId
                        && countTripleInnerStellarPairMembers(nullId, bodies) >= 2);
        double schematicRadM = Double.NaN;
        if (schematicNestedPlanetPair) {
            schematicRadM = nestedPlanetBinaryMutualOrbitRadiusLs(nullId, bodies) * LIGHT_SECOND_METRES;
        } else if (schematicStellarPair) {
            if (countStellarDirectNullMembers(nullId, bodies) < 2
                    || (tripleStellarNull && isHierarchicalTripleStarMap(bodies))) {
                /* Cache-parented or wide-heliocentric B+C: use tight schematic mutual orbit, not journal spread. */
                schematicRadM = Math.max(STELLAR_SHARED_NULL_MUTUAL_ORBIT_MIN_LS * LIGHT_SECOND_METRES,
                        HIERARCHICAL_INNER_STELLAR_PAIR_SCHEMATIC_MIN_LS * LIGHT_SECOND_METRES * 0.5);
            } else {
                schematicRadM = planetBinaryMutualOrbitRadiusLs(nullId, bodies) * LIGHT_SECOND_METRES;
            }
        }
        int stellarPlaced = 0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            BodyInfo child = e.getValue();
            if (isMoonSatelliteBody(child, bodies)) {
                continue;
            }
            int mapId = e.getKey().intValue();
            boolean nullMember = child.getImmediateParentBodyId() == nullId
                    || (isStellarDirectNullMember(child, bodies)
                            && resolveOrbitParentBodyId(child, bodies, mapId) == bKey);
            if (!nullMember) {
                continue;
            }
            if (schematicStellarPair && isStellarDirectNullMember(child, bodies)) {
                double theta = stellarPlaced == 0 ? 0.0 : Math.PI;
                stellarPlaced++;
                double[] rel = new double[] { 0.0, 0.0, 0.0 };
                rel[p0] = schematicRadM * Math.cos(theta);
                rel[p1] = schematicRadM * Math.sin(theta);
                positions.put(e.getKey(), combineParentAndRelativeOffset(bary, rel, p0, p1));
                continue;
            }
            if (schematicNestedPlanetPair && !isStellarDirectNullMember(child, bodies)) {
                double theta = stellarPlaced == 0 ? 0.0 : Math.PI;
                stellarPlaced++;
                double[] rel = new double[] { 0.0, 0.0, 0.0 };
                rel[p0] = schematicRadM * Math.cos(theta);
                rel[p1] = schematicRadM * Math.sin(theta);
                positions.put(e.getKey(), combineParentAndRelativeOffset(bary, rel, p0, p1));
                continue;
            }
            double[] rel = planetBinaryOffsetFromBarycentreMetres(child, mapId, bodies, nullId, now, p0, p1,
                    freezeBarycentreStars, null, bary);
            if (!isFiniteXYZ(rel)) {
                continue;
            }
            positions.put(e.getKey(), combineParentAndRelativeOffset(bary, rel, p0, p1));
        }
    }

    /**
     * Hierarchical BCD trunk: outer Null (e.g. 2) is a stellar pair D vs inner Null:3 (B+C), not a mutual ring that
     * also includes distant BCD giants.
     */
    private static boolean orbitsJournalOrResolvedNull(int mapId, BodyInfo child, int nullId, int bKey,
            Map<Integer, BodyInfo> bodies) {
        if (child == null || bodies == null) {
            return false;
        }
        return child.getImmediateParentBodyId() == nullId
                || resolveOrbitParentBodyId(child, bodies, mapId) == bKey;
    }

    private static void alignHierarchicalOuterStellarNullPair(Map<Integer, double[]> positions,
            Map<Integer, BodyInfo> bodies,
            int outerNullId,
            Instant now,
            int p0,
            int p1,
            boolean freezeBarycentreStars) {
        int bKey = planetBinaryBarycentreMapKey(outerNullId);
        int needLen = Math.max(3, Math.max(p0, p1) + 1);
        List<Integer> innerNullIds = nestedStellarInnerNullIds(outerNullId, bodies);
        double radLs = planetBinaryOuterStellarPairOrbitRadiusLs(outerNullId, bodies);
        double r = radLs * LIGHT_SECOND_METRES;
        double sx = 0.0;
        double sy = 0.0;
        int n = 0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            BodyInfo b = e.getValue();
            int mapId = e.getKey().intValue();
            if (isMoonSatelliteBody(b, bodies) || !orbitsJournalOrResolvedNull(mapId, b, outerNullId, bKey, bodies)) {
                continue;
            }
            if (!isStellarDirectNullMember(b, bodies)) {
                continue;
            }
            double[] p = positions.get(e.getKey());
            if (p == null || p.length <= Math.max(p0, p1)) {
                continue;
            }
            sx += worldAxisMetres(p, p0);
            sy += worldAxisMetres(p, p1);
            n++;
        }
        for (Integer innerId : innerNullIds) {
            int ik = planetBinaryBarycentreMapKey(innerId.intValue());
            double[] p = positions.get(Integer.valueOf(ik));
            if (p == null || p.length <= Math.max(p0, p1)) {
                continue;
            }
            sx += worldAxisMetres(p, p0);
            sy += worldAxisMetres(p, p1);
            n++;
        }
        if (n < 1) {
            double[] centroid = planetBinaryMemberCentroidWorldXY(outerNullId, bodies, positions, p0, p1);
            if (centroid == null && !innerNullIds.isEmpty()) {
                int ik = planetBinaryBarycentreMapKey(innerNullIds.get(0).intValue());
                double[] innerBary = positions.get(Integer.valueOf(ik));
                if (innerBary != null && innerBary.length > Math.max(p0, p1)) {
                    sx = worldAxisMetres(innerBary, p0);
                    sy = worldAxisMetres(innerBary, p1);
                    n = 1;
                }
            }
            if (centroid != null) {
                sx = centroid[0];
                sy = centroid[1];
                n = 1;
            }
        }
        if (n < 1) {
            return;
        }
        double cx = sx / n;
        double cy = sy / n;
        double[] bary = positions.get(Integer.valueOf(bKey));
        if (bary == null || bary.length < needLen) {
            bary = new double[needLen];
        } else {
            bary = Arrays.copyOf(bary, Math.max(needLen, bary.length));
        }
        bary[p0] = cx;
        bary[p1] = cy;
        positions.put(Integer.valueOf(bKey), bary);
        int stellarPlaced = 0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            BodyInfo child = e.getValue();
            int mapId = e.getKey().intValue();
            if (isMoonSatelliteBody(child, bodies)
                    || !orbitsJournalOrResolvedNull(mapId, child, outerNullId, bKey, bodies)) {
                continue;
            }
            if (!isStellarDirectNullMember(child, bodies)) {
                continue;
            }
            double theta = stellarPlaced == 0 ? 0.0 : Math.PI;
            stellarPlaced++;
            double[] rel = new double[] { 0.0, 0.0, 0.0 };
            rel[p0] = r * Math.cos(theta);
            rel[p1] = r * Math.sin(theta);
            positions.put(e.getKey(), combineParentAndRelativeOffset(bary, rel, p0, p1));
        }
        for (Integer innerId : innerNullIds) {
            int ik = planetBinaryBarycentreMapKey(innerId.intValue());
            double[] rel = new double[] { 0.0, 0.0, 0.0 };
            rel[p0] = -r;
            rel[p1] = 0.0;
            positions.put(Integer.valueOf(ik), combineParentAndRelativeOffset(bary, rel, p0, p1));
            alignSinglePlanetBinaryNullGroup(positions, bodies, innerId.intValue(), now, p0, p1, freezeBarycentreStars,
                    false);
        }
        for (Integer pbNullId : nestedPlanetBinaryNullIdsUnderOuterTrunk(outerNullId, bodies)) {
            int nullId = pbNullId.intValue();
            int ik = planetBinaryBarycentreMapKey(nullId);
            double distLs = planetBinaryOuterStellarPairOrbitRadiusLs(outerNullId, bodies) * 0.42;
            distLs = Math.max(HIERARCHICAL_INNER_STELLAR_PAIR_SCHEMATIC_MIN_LS, distLs);
            BodyInfo ref = firstPlanetBinarySibling(nullId, bodies);
            BodyInfo outer = planetBinaryOuterOrbitalSource(nullId, bodies);
            if (ref == null) {
                continue;
            }
            BodyInfo angleSource = outer != null ? outer : ref;
            double[] rel = schematicMapPlaneOffsetMetresAtHintLs(angleSource, distLs, now, p0, p1,
                    freezeBarycentreStars);
            positions.put(Integer.valueOf(ik), combineParentAndRelativeOffset(bary, rel, p0, p1));
            alignSinglePlanetBinaryNullGroup(positions, bodies, nullId, now, p0, p1, freezeBarycentreStars, true);
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            BodyInfo child = e.getValue();
            int mapId = e.getKey().intValue();
            if (isMoonSatelliteBody(child, bodies)
                    || !orbitsJournalOrResolvedNull(mapId, child, outerNullId, bKey, bodies)) {
                continue;
            }
            if (isStellarDirectNullMember(child, bodies)) {
                continue;
            }
            double[] rel = planetBinaryOffsetFromBarycentreMetres(child, mapId, bodies, outerNullId, now, p0, p1,
                    freezeBarycentreStars, null, bary);
            if (!isFiniteXYZ(rel)) {
                continue;
            }
            positions.put(e.getKey(), combineParentAndRelativeOffset(bary, rel, p0, p1));
        }
    }

    private static double[] schematicMapPlaneOffsetMetres(BodyInfo child, int mapBodyId, Map<Integer, BodyInfo> bodies,
            int parentMapId, Instant now, int p0, int p1, boolean freezeBarycentreStars) {
        double[] rest = pseudoOffsetMetres(child, mapBodyId, bodies, parentMapId);
        double hintLs = journalOrbitRadiusLsFromParent(child, parentMapId, bodies, mapBodyId);
        if (!Double.isFinite(hintLs) || hintLs <= 0.0) {
            double pm = Math.sqrt(rest[0] * rest[0] + rest[1] * rest[1] + rest[2] * rest[2]);
            hintLs = pm > 1.0 ? pm / LIGHT_SECOND_METRES : 1.0;
        }
        double r = hintLs * LIGHT_SECOND_METRES;
        double px = worldAxisMetres(rest, p0);
        double py = worldAxisMetres(rest, p1);
        double pm = Math.hypot(px, py);
        if (!(pm > 1.0)) {
            px = 1.0;
            py = 0.0;
            pm = 1.0;
        }
        double ux = px / pm;
        double uy = py / pm;
        double M = freezeBarycentreStars && isBarycentreOrbitingStar(child, bodies, mapBodyId)
                ? wrapToTwoPi(angleRad(child.getMeanAnomaly()))
                : evolvedMeanAnomalyRadians(child, now);
        double cosM = Math.cos(M);
        double sinM = Math.sin(M);
        double ox = (ux * cosM - uy * sinM) * r;
        double oy = (ux * sinM + uy * cosM) * r;
        double[] out = new double[] { 0.0, 0.0, 0.0 };
        out[p0] = ox;
        out[p1] = oy;
        return out;
    }

    /** {@code "1 a"}, {@code "1a"}, {@code "12 a"} → parent designation {@code "1"} / {@code "12"}; plain {@code "1"} → no match. */
    private static final Pattern MOON_DESIGNATION = Pattern.compile("^(\\d+)\\s*([A-Za-z])\\s*$");

    /**
     * Elite body short names often end with {@code B 3} or {@code B 3 a}: stellar branch letter, major index, optional
     * moon suffix — used when {@link BodyInfo#getImmediateParentBodyId()} is unset so B-branch planets orbit star B.
     */
    private static final Pattern TRAILING_STAR_BODY_DESIGNATION = Pattern
            .compile("(?<![A-Za-z])([A-Za-z])\\s+(\\d+)(?:\\s+([a-z]+))?\\s*$");

    /** Minimum X/Y separation from parent (m) before drawing a fallback circular orbit. */
    private static final double MIN_FALLBACK_ORBIT_RADIUS_METRES = 50.0;

    /**
     * Moons around planets (e.g. {@code A 2 a}): enforce at least this many screen pixels of orbit radius when
     * {@code scalePixelsPerMetre} is known so the stroke stays visible at subsystem-detail zoom despite a wide layout span.
     */
    public static final double MIN_MOON_ORBIT_SCREEN_RADIUS_PX = 5.5;

    /**
     * Hide oversized hierarchical / branch schematic rings when the viewport span is smaller than this fraction of the
     * ring radius (Ls). Keeps Null:2 concentric rings from filling the B+C cluster when zoomed in.
     */
    public static final double SCHEMATIC_RING_HIDE_WHEN_VIEW_SPAN_FRAC_OF_RADIUS = 0.72;

    /** Schematic rings smaller than this (Ls) are never culled by the detail-view filter. */
    public static final double SCHEMATIC_RING_DETAIL_VIEW_MIN_RADIUS_LS = 200.0;

    /** Ramanujan's second approximation to an ellipse perimeter (m). */
    private static double keplerEllipsePerimeterMetres(double semiMajorM, double eccentricity) {
        double a = semiMajorM;
        if (!(a > 0.0) || !Double.isFinite(a)) {
            return 0.0;
        }
        double e = clamp(eccentricity, 0.0, 0.999999);
        double b = a * Math.sqrt(Math.max(1e-18, 1.0 - e * e));
        if (!Double.isFinite(b)) {
            return 0.0;
        }
        double h = (a - b) * (a - b) / ((a + b) * (a + b));
        double root = Math.sqrt(Math.max(0.0, 4.0 - 3.0 * h));
        return Math.PI * (a + b) * (1.0 + (3.0 * h) / (10.0 + root));
    }

    /**
     * Vertex count for a closed orbit so typical segment length on screen stays near {@code targetChordPx}.
     * Uses a low floor when the projected ring is only a few pixels around so zoomed-out maps stay light.
     */
    private static int segmentCountForScreenChord(double scalePxPerM, double perimeterMetres, int legacyN) {
        if (!Double.isFinite(perimeterMetres) || perimeterMetres <= 0.0) {
            return legacyN;
        }
        if (!Double.isFinite(scalePxPerM) || scalePxPerM <= 0.0) {
            return legacyN;
        }
        double circPx = perimeterMetres * scalePxPerM;
        final double targetChordPx = 3.0;
        int n = (int) Math.ceil(circPx / targetChordPx);
        int softMin = circPx < 120.0 ? 32 : 48;
        n = Math.max(softMin, Math.min(ORBIT_POLYLINE_SEGMENTS_HARD_MAX, n));
        return n;
    }

    /**
     * Closed orbit polylines in world metres (X/Y only) for the system map.
     * <p>
     * When {@link BodyInfo#getSemiMajorAxisM()} is set (journal Scan), uses the Kepler ellipse.
     * Otherwise draws a <strong>schematic</strong> circle in the X/Y plane at the body's current projected
     * separation from its parent so orbits still appear when orbital elements were never persisted (cache / EDSM).
     *
     * @param segments number of straight segments per closed orbit when {@code scalePixelsPerMetre} is not finite
     *        (legacy path); otherwise ignored except as a rough fallback if chord-based count fails
     * @param scalePixelsPerMetre world (X/Y metres) to screen pixel scale at the map centre; when finite and
     *        positive, each orbit chooses a segment count so the inscribed polygon stays near a target chord length
     *        in pixels (more segments when zoomed in / large orbits on screen; fewer when zoomed out).
     */
    public static List<OrbitPolylineWorldXY> orbitPolylinesWorldMetresXY(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int segments) {
        return orbitPolylinesWorldMetresXY(bodies, bodyWorldPositions, segments, Double.NaN, 0, 1);
    }

    /** Upper clamp for per-orbit vertex count (must stay within {@link #orbitPolylinesWorldMetresXY} loop budget). */
    public static final int ORBIT_POLYLINE_SEGMENTS_HARD_MAX = 768;

    /** Synthetic map id for the wide-binary barycentre ring (never collides with journal body ids in {@code uniq}). */
    public static final int BINARY_BARYCENTRE_ORBIT_RING_BODY_ID = -2;

    /**
     * Schematic A vs B+C mutual-orbit ring ({@link #isHierarchicalTripleStarMap}) — centre between A and the inner
     * {@code ScanBaryCentre} hub, not a straight chord.
     */
    public static final int HIERARCHICAL_TRIPLE_STAR_TRUNK_POLYLINE_ID = -10_003;

    /**
     * Four-star A vs BCD: mutual ring between primary A and the closest companion (e.g. B at ~225 Ls) when outer
     * companions sit much farther out — distinct from {@link #BINARY_BARYCENTRE_ORBIT_RING_BODY_ID}.
     */
    public static final int HIERARCHICAL_INNER_STELLAR_PAIR_POLYLINE_ID = -10_004;

    /**
     * Synthetic map keys for planet–planet binary barycentres ({@code Parents:[{"Null":N},…]} with no body row
     * {@code N}). Key = {@code PLANET_BINARY_BARYCENTRE_MAP_KEY_BASE - journalNullId}.
     */
    public static final int PLANET_BINARY_BARYCENTRE_MAP_KEY_BASE = -50_000;

    /** Mutual orbit stroke around a planet-binary barycentre ({@link #appendPlanetBinaryMutualOrbitRings}). */
    public static final int PLANET_BINARY_MUTUAL_ORBIT_RING_ID_BASE = -51_000;

    /** Heliocentric orbit of the pair barycentre around the star ({@link #appendPlanetBinaryBarycentreRingsAtStar}). */
    public static final int PLANET_BINARY_OUTER_ORBIT_RING_ID_BASE = -52_000;

    /**
     * When the map view spans less than this (Ls, min plot axis), hide {@link #isPlanetBinaryOuterBarycentreOrbitRingBodyId}
     * strokes so zoomed-in binary pairs show the mutual ring only.
     */
    public static final double PLANET_BINARY_OUTER_RING_MAX_VISIBLE_LS = 48.0;

    /** Synthetic ids for {@link #appendSingleStarSchematicRings} ({@code -4000 - roundLs}). */
    private static final int SINGLE_STAR_SCHEMATIC_RING_ID_BASE = -4000;

    /** Cached wide-binary flatten chord from {@link #captureWideBinaryFlattenFrame}. */
    public static final class WideBinaryFlattenFrame {
        public final int primaryId;
        public final int companionId;
        public final double chordUx;
        public final double chordUy;
        public final double journalSepM;

        public WideBinaryFlattenFrame(int primaryId, int companionId, double chordUx, double chordUy,
                double journalSepM) {
            this.primaryId = primaryId;
            this.companionId = companionId;
            this.chordUx = chordUx;
            this.chordUy = chordUy;
            this.journalSepM = journalSepM;
        }
    }

    public static List<OrbitPolylineWorldXY> orbitPolylinesWorldMetresXY(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int segments,
            double scalePixelsPerMetre) {
        return orbitPolylinesWorldMetresXY(bodies, bodyWorldPositions, segments, scalePixelsPerMetre, 0, 1);
    }

    /**
     * @param proj0 first world axis index (0=x,1=y,2=z) for map “horizontal” coordinate
     * @param proj1 second world axis index for map “vertical” coordinate (must differ from {@code proj0})
     */
    public static List<OrbitPolylineWorldXY> orbitPolylinesWorldMetresXY(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int segments,
            double scalePixelsPerMetre,
            int proj0,
            int proj1) {
        return orbitPolylinesWorldMetresXY(bodies, bodyWorldPositions, segments, scalePixelsPerMetre, proj0, proj1,
                true);
    }

    public static List<OrbitPolylineWorldXY> orbitPolylinesWorldMetresXY(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int segments,
            double scalePixelsPerMetre,
            int proj0,
            int proj1,
            boolean includeBinaryBarycentreRing) {
        return orbitPolylinesWorldMetresXY(bodies, bodyWorldPositions, segments, scalePixelsPerMetre, proj0, proj1,
                includeBinaryBarycentreRing, null);
    }

    /**
     * @param resolvedParentsByBodyId when non-null (e.g. from {@link org.dce.ed.systemmap.SystemMapModel}), orbit
     *        strokes use these parents and do not apply the primary-star fallback for unresolved planets — keeps GUI
     *        rings aligned with the committed topology contract.
     */
    public static List<OrbitPolylineWorldXY> orbitPolylinesWorldMetresXY(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int segments,
            double scalePixelsPerMetre,
            int proj0,
            int proj1,
            boolean includeBinaryBarycentreRing,
            Map<Integer, Integer> resolvedParentsByBodyId) {
        return orbitPolylinesWorldMetresXY(bodies, bodyWorldPositions, segments, scalePixelsPerMetre, proj0, proj1,
                includeBinaryBarycentreRing, resolvedParentsByBodyId, MapScaleMode.SCHEMATIC);
    }

    public static List<OrbitPolylineWorldXY> orbitPolylinesWorldMetresXY(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int segments,
            double scalePixelsPerMetre,
            int proj0,
            int proj1,
            boolean includeBinaryBarycentreRing,
            Map<Integer, Integer> resolvedParentsByBodyId,
            MapScaleMode scaleMode) {
        return orbitPolylinesWorldMetresXY(bodies, bodyWorldPositions, segments, scalePixelsPerMetre, proj0, proj1,
                includeBinaryBarycentreRing, resolvedParentsByBodyId, scaleMode, false, null);
    }

    /**
     * @param enforceSchematicMoonMinOrbitRadius when true and {@code scalePixelsPerMetre} is finite, planet-hosted moon
     *        fallback circles are expanded in world metres so they stay visible on screen. Off by default so zoom-driven
     *        rebuilds keep journal/true schematic radii while screen-chord segment counts still adapt to zoom.
     * @param ringRadiusReferencePositions when non-null, schematic ring radii use this layout snapshot; centres still
     *        follow {@code bodyWorldPositions}.
     */
    private static void assignMapViewVertex(double[] wx, double[] wy, int i, double[] parentPos, double[] rel,
            int p0, int p1, int viewTiltDeg) {
        if (viewTiltDeg <= 0) {
            wx[i] = worldAxisMetres(parentPos, p0) + worldAxisMetres(rel, p0);
            wy[i] = worldAxisMetres(parentPos, p1) + worldAxisMetres(rel, p1);
            return;
        }
        double[] view = MapViewProjection.projectSum(parentPos, rel, p0, p1, viewTiltDeg);
        wx[i] = view[0];
        wy[i] = view[1];
    }

    /** Fills {@code wx}/{@code wy} with a circle in the map plane (axes {@code p0}/{@code p1}), optionally view-tilted. */
    private static void fillMapPlaneCircleVertices(double[] wx, double[] wy, double[] centerWorldMetres,
            double radiusM, int p0, int p1, int viewTiltDeg) {
        int n = wx.length;
        double cx = worldAxisMetres(centerWorldMetres, 0);
        double cy = worldAxisMetres(centerWorldMetres, 1);
        double cz = centerWorldMetres.length >= 3 ? worldAxisMetres(centerWorldMetres, 2) : 0.0;
        int a0 = clampWorldAxisIndex(p0);
        int a1 = clampWorldAxisIndex(p1);
        double[] c = new double[] { cx, cy, cz };
        for (int i = 0; i < n; i++) {
            double theta = (Math.PI * 2.0 * i) / n;
            double du = radiusM * Math.cos(theta);
            double dv = radiusM * Math.sin(theta);
            double x = c[0];
            double y = c[1];
            double z = c[2];
            if (a0 == 0) {
                x += du;
            } else if (a0 == 1) {
                y += du;
            } else {
                z += du;
            }
            if (a1 == 0) {
                x += dv;
            } else if (a1 == 1) {
                y += dv;
            } else {
                z += dv;
            }
            double[] view = MapViewProjection.projectWorldComponents(x, y, z, p0, p1, viewTiltDeg);
            wx[i] = view[0];
            wy[i] = view[1];
        }
    }

    public static List<OrbitPolylineWorldXY> orbitPolylinesWorldMetresXY(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int segments,
            double scalePixelsPerMetre,
            int proj0,
            int proj1,
            boolean includeBinaryBarycentreRing,
            Map<Integer, Integer> resolvedParentsByBodyId,
            MapScaleMode scaleMode,
            boolean enforceSchematicMoonMinOrbitRadius) {
        return orbitPolylinesWorldMetresXY(bodies, bodyWorldPositions, segments, scalePixelsPerMetre, proj0, proj1,
                includeBinaryBarycentreRing, resolvedParentsByBodyId, scaleMode, enforceSchematicMoonMinOrbitRadius,
                null);
    }

    public static List<OrbitPolylineWorldXY> orbitPolylinesWorldMetresXY(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int segments,
            double scalePixelsPerMetre,
            int proj0,
            int proj1,
            boolean includeBinaryBarycentreRing,
            Map<Integer, Integer> resolvedParentsByBodyId,
            MapScaleMode scaleMode,
            boolean enforceSchematicMoonMinOrbitRadius,
            Map<Integer, double[]> ringRadiusReferencePositions) {
        return orbitPolylinesWorldMetresXY(bodies, bodyWorldPositions, segments, scalePixelsPerMetre, proj0, proj1,
                includeBinaryBarycentreRing, resolvedParentsByBodyId, scaleMode, enforceSchematicMoonMinOrbitRadius,
                ringRadiusReferencePositions, 0);
    }

    /**
     * @param viewTiltDegrees true-scale view tilt 0…90 ({@link MapViewProjection}); ignored when schematic.
     */
    public static List<OrbitPolylineWorldXY> orbitPolylinesWorldMetresXY(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int segments,
            double scalePixelsPerMetre,
            int proj0,
            int proj1,
            boolean includeBinaryBarycentreRing,
            Map<Integer, Integer> resolvedParentsByBodyId,
            MapScaleMode scaleMode,
            boolean enforceSchematicMoonMinOrbitRadius,
            Map<Integer, double[]> ringRadiusReferencePositions,
            int viewTiltDegrees) {
        if (bodies == null || bodies.isEmpty() || bodyWorldPositions == null || bodyWorldPositions.isEmpty()) {
            return Collections.emptyList();
        }
        boolean trueScale = scaleMode != null && scaleMode.trueScale();
        int viewTilt = trueScale ? MapViewProjection.clampViewTiltDegrees(viewTiltDegrees) : 0;
        boolean moonMinRadius = enforceSchematicMoonMinOrbitRadius && !trueScale;
        int p0 = clampWorldAxisIndex(proj0);
        int p1 = clampWorldAxisIndex(proj1);
        if (p0 == p1) {
            p1 = p0 == 2 ? 1 : 2;
        }
        int legacyN = Math.max(12, Math.min(ORBIT_POLYLINE_SEGMENTS_HARD_MAX, segments));
        boolean useScreenChord = Double.isFinite(scalePixelsPerMetre) && scalePixelsPerMetre > 0.0;
        boolean useModelParents = resolvedParentsByBodyId != null && !resolvedParentsByBodyId.isEmpty();
        List<OrbitPolylineWorldXY> out = new ArrayList<>();
        Set<String> seenOrbitCurveKeys = new HashSet<>();
        int primaryAnch = primaryAnchorBodyMapKey(bodies);
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int bodyId = e.getKey().intValue();
            BodyInfo b = e.getValue();
            if (b.isScanBarycentreRow()) {
                continue;
            }
            int pId;
            if (useModelParents) {
                Integer rp = resolvedParentsByBodyId.get(Integer.valueOf(bodyId));
                pId = rp != null ? rp.intValue() : resolveOrbitParentBodyId(b, bodies, bodyId);
            } else {
                pId = resolveOrbitParentBodyId(b, bodies, bodyId);
                if (pId < 0 && !isMapStellarBody(b) && !isPlanetBinaryBarycentreMapKey(pId)) {
                    int primary = primaryAnchorBodyMapKey(bodies);
                    if (primary >= 0 && primary != bodyId) {
                        pId = primary;
                    }
                }
            }
            /*
             * Lone-star: no ring on the anchor itself (planet rings come from schematic rings at the star).
             * Wide binary: barycentric stars share one mutual ring ({@link #appendBinaryBarycentreOrbitRing}), not
             * per-star circles at the origin (journal distance from arrival would dwarf the true A–B separation).
             */
            if (isMapStellarBody(b) && pId < 0 && countMapStellarBodies(bodies) >= 2) {
                continue;
            }
            if (!trueScale && bodyId == primaryAnch && (pId >= 0
                    || (pId < 0 && isMapStellarBody(b) && isSingleStarSchematicMap(bodies)))) {
                continue;
            }
            if (!trueScale && isSingleStarSchematicMap(bodies) && bodyId == schematicCentralStarMapKey(bodies)) {
                continue;
            }
            if (!trueScale && !isMapStellarBody(b)) {
                int branchStar = branchSchematicStarParentId(bodies, pId);
                if (branchStar >= 0) {
                    continue;
                }
                if (isPlanetBinaryBarycentreMapKey(pId)) {
                    continue;
                }
            }
            if (trueScale && !isMapStellarBody(b) && isPlanetBinaryBarycentreMapKey(pId)) {
                continue;
            }
            double[] bodyPos = bodyWorldPositions.get(Integer.valueOf(bodyId));
            if (bodyPos == null || bodyPos.length < 2) {
                continue;
            }
            int needLen = Math.max(p0, p1) + 1;
            if (bodyPos.length < needLen) {
                continue;
            }

            final double[] parentPos;
            if (pId < 0) {
                if (isPlanetBinaryBarycentreMapKey(pId)) {
                    parentPos = resolveParentWorldMetres(pId, bodies, bodyWorldPositions);
                    if (parentPos == null || parentPos.length < needLen) {
                        continue;
                    }
                } else if (!isMapStellarBody(b)) {
                    continue;
                } else {
                    parentPos = new double[] { 0.0, 0.0, 0.0 };
                }
            } else {
                parentPos = resolveParentWorldMetres(pId, bodies, bodyWorldPositions);
                if (parentPos == null || parentPos.length < needLen) {
                    continue;
                }
            }

            /*
             * Map orbit strokes are schematic: planets/moons always use a circle at journal distance from the parent.
             * FSS supplies Kepler elements that are often barycentric or skewed; drawing one ellipse per body (different
             * i/Ω/ω) stacks into a spirograph even when each ellipse passes a per-orbit sanity check.
             */
            Double aObj = b.getSemiMajorAxisM();
            boolean haveKepler = aObj != null
                    && aObj.doubleValue() > 0
                    && !Double.isNaN(aObj.doubleValue());
            if (!trueScale) {
                haveKepler = isMapStellarBody(b) && haveKepler;
            }
            if (!trueScale && haveKepler && !keplerOrbitPolylineMatchesSchematicPlacement(b, pId, bodies, bodyId,
                    bodyPos, parentPos, p0, p1)) {
                haveKepler = false;
            }
            int n;
            double fallbackRadMetres = Double.NaN;
            if (useScreenChord) {
                if (haveKepler) {
                    double a = aObj.doubleValue();
                    double ecc = (b.getEccentricity() != null && !Double.isNaN(b.getEccentricity()))
                            ? clamp(b.getEccentricity().doubleValue(), 0, 0.999999)
                            : 0.0;
                    double perimM = keplerEllipsePerimeterMetres(a, ecc);
                    n = segmentCountForScreenChord(scalePixelsPerMetre, perimM, legacyN);
                } else {
                double[] radiusBodyPos = orbitRadiusBodyPosition(bodyWorldPositions, ringRadiusReferencePositions,
                        Integer.valueOf(bodyId));
                double[] radiusParentPos = orbitRadiusParentPosition(bodyWorldPositions, ringRadiusReferencePositions,
                        pId, parentPos);
                double rad = schematicOrbitRadiusMetres(b, pId, bodies, bodyId,
                        radiusBodyPos != null ? radiusBodyPos : bodyPos,
                        radiusParentPos != null ? radiusParentPos : parentPos, p0, p1);
                if (!Double.isFinite(rad) || rad < MIN_FALLBACK_ORBIT_RADIUS_METRES) {
                    continue;
                }
                fallbackRadMetres = rad;
                double perimM = Math.PI * 2.0 * rad;
                n = segmentCountForScreenChord(scalePixelsPerMetre, perimM, legacyN);
                }
            } else {
                n = legacyN;
            }
            n = Math.max(12, Math.min(ORBIT_POLYLINE_SEGMENTS_HARD_MAX, n));

            double[] wx = null;
            double[] wy = null;
            boolean estimatedOrbit = !haveKepler;

            if (haveKepler) {
                wx = new double[n];
                wy = new double[n];
                boolean keplerOk = true;
                Instant strokeNow = Instant.now();
                for (int i = 0; i < n; i++) {
                    double M = (Math.PI * 2.0 * i) / n;
                    double[] rel;
                    if (trueScale && !isMapStellarBody(b)
                            && isTrueScaleBranchStarPlanetStroke(b, pId, bodies)) {
                        rel = trueScaleBranchPlanetKeplerDisplacementMetres(b, pId, bodies, bodyId, M, strokeNow,
                                p0, p1, parentPos, bodyWorldPositions, viewTilt);
                    } else {
                        rel = keplerDisplacementMetres(b, M);
                        if (pId < 0) {
                            rel = reconcileOrbitalDisplacementWithJournalHint(b, pId, bodies, bodyId, rel, strokeNow);
                        }
                    }
                    if (rel == null || rel.length < (viewTilt > 0 ? 3 : needLen)) {
                        keplerOk = false;
                        break;
                    }
                    assignMapViewVertex(wx, wy, i, parentPos, rel, p0, p1, viewTilt);
                }
                if (!keplerOk) {
                    wx = null;
                    wy = null;
                }
            }
            if (wx == null && trueScale && haveKepler) {
                wx = new double[n];
                wy = new double[n];
                boolean keplerOk = true;
                Instant strokeNow = Instant.now();
                for (int i = 0; i < n; i++) {
                    double M = (Math.PI * 2.0 * i) / n;
                    double[] rel;
                    if (isTrueScaleBranchStarPlanetStroke(b, pId, bodies)) {
                        rel = trueScaleBranchPlanetKeplerDisplacementMetres(b, pId, bodies, bodyId, M, strokeNow,
                                p0, p1, parentPos, bodyWorldPositions, viewTilt);
                    } else {
                        rel = keplerDisplacementMetres(b, M);
                    }
                    if (rel == null || rel.length < (viewTilt > 0 ? 3 : needLen)) {
                        keplerOk = false;
                        break;
                    }
                    assignMapViewVertex(wx, wy, i, parentPos, rel, p0, p1, viewTilt);
                }
                if (!keplerOk) {
                    wx = null;
                    wy = null;
                } else {
                    estimatedOrbit = false;
                }
            }
            if (wx == null) {
                if (trueScale && haveKepler) {
                    continue;
                }
                double[] radiusBodyPos = orbitRadiusBodyPosition(bodyWorldPositions, ringRadiusReferencePositions,
                        Integer.valueOf(bodyId));
                double[] radiusParentPos = orbitRadiusParentPosition(bodyWorldPositions, ringRadiusReferencePositions,
                        pId, parentPos);
                double rad = schematicOrbitRadiusMetres(b, pId, bodies, bodyId,
                        radiusBodyPos != null ? radiusBodyPos : bodyPos,
                        radiusParentPos != null ? radiusParentPos : parentPos, p0, p1);
                if (moonMinRadius) {
                    rad = enforceMinMoonOrbitRadiusMetres(b, pId, bodies, rad, scalePixelsPerMetre);
                }
                if (!Double.isFinite(rad) || rad < MIN_FALLBACK_ORBIT_RADIUS_METRES) {
                    continue;
                }
                fallbackRadMetres = rad;
                estimatedOrbit = bodyLayoutDataIsEstimated(b, bodyId, bodies);
                if (useScreenChord) {
                    int circleN = segmentCountForScreenChord(scalePixelsPerMetre, Math.PI * 2.0 * rad, legacyN);
                    circleN = Math.max(12, Math.min(ORBIT_POLYLINE_SEGMENTS_HARD_MAX, circleN));
                    if (circleN != n) {
                        n = circleN;
                    }
                }
                wx = new double[n];
                wy = new double[n];
                fillMapPlaneCircleVertices(wx, wy, parentPos, rad, p0, p1, viewTilt);
            }

            if (wx != null) {
                if (!trueScale && isHierarchicalWideBinary(bodies) && Double.isFinite(fallbackRadMetres)
                        && fallbackRadMetres > HIERARCHICAL_WIDE_BINARY_SCHEMATIC_SEP_LS * 0.55
                                * LIGHT_SECOND_METRES) {
                    continue;
                }
                String curveKey = orbitCurveShapeKey(pId, bodyId, b, bodies, haveKepler, fallbackRadMetres);
                if (curveKey != null && !seenOrbitCurveKeys.add(curveKey)) {
                    continue;
                }
                out.add(new OrbitPolylineWorldXY(bodyId, wx, wy, estimatedOrbit));
            }
        }
        List<OrbitPolylineWorldXY> geomUnique = dedupeOrbitPolylinesUntilStable(out);
        /* One ring per body id — rare merge/cache edge cases can otherwise duplicate keys in the bodies map. */
        Map<Integer, OrbitPolylineWorldXY> uniq = new LinkedHashMap<>();
        for (OrbitPolylineWorldXY p : geomUnique) {
            uniq.put(Integer.valueOf(p.bodyId), p);
        }
        List<OrbitPolylineWorldXY> merged = new ArrayList<>(uniq.values());
        /* After uniq so companion-star Kepler rings cannot replace this schematic (same journal id). */
        if (includeBinaryBarycentreRing) {
            appendBinaryBarycentreOrbitRing(merged, bodies, bodyWorldPositions, p0, p1, legacyN, useScreenChord,
                    scalePixelsPerMetre, viewTilt);
        }
        if (!trueScale) {
            if (countMapStellarBodies(bodies) >= 2 || isHierarchicalWideBinary(bodies)) {
                appendBranchStarSchematicRings(merged, bodies, bodyWorldPositions, p0, p1, legacyN, useScreenChord,
                        scalePixelsPerMetre, ringRadiusReferencePositions);
                appendPlanetBinaryMutualOrbitRings(merged, bodies, bodyWorldPositions, p0, p1, legacyN, useScreenChord,
                        scalePixelsPerMetre, 0);
                int primaryStar = primaryAnchorBodyMapKey(bodies);
                if (primaryStar >= 0) {
                    appendPlanetBinaryBarycentreRingsAtStar(merged, bodies, bodyWorldPositions, primaryStar, p0, p1,
                            legacyN, useScreenChord, scalePixelsPerMetre, 0);
                }
                if (isHierarchicalWideBinary(bodies)) {
                    if (isHierarchicalTripleStarMap(bodies)) {
                        appendHierarchicalTripleStarTrunk(merged, bodies, bodyWorldPositions, p0, p1, legacyN,
                                useScreenChord, scalePixelsPerMetre);
                    } else {
                        appendHierarchicalSystemBarycentreRing(merged, bodies, bodyWorldPositions, p0, p1, legacyN,
                                useScreenChord, scalePixelsPerMetre);
                        appendHierarchicalInnerPrimaryCompanionRing(merged, bodies, bodyWorldPositions, p0, p1,
                                legacyN, useScreenChord, scalePixelsPerMetre);
                    }
                    appendSchematicRingsAtHierarchicalNullBarycentres(merged, bodies, bodyWorldPositions, p0, p1,
                            legacyN, useScreenChord, scalePixelsPerMetre, ringRadiusReferencePositions);
                }
            } else if (shouldApplyLoneStarSchematicLayout(bodies)) {
                int central = schematicCentralStarMapKey(bodies);
                appendSchematicRingsAtStar(merged, bodies, bodyWorldPositions, central, p0, p1,
                        legacyN, useScreenChord, scalePixelsPerMetre, ringRadiusReferencePositions);
                appendPlanetBinaryBarycentreRingsAtStar(merged, bodies, bodyWorldPositions, central, p0, p1,
                        legacyN, useScreenChord, scalePixelsPerMetre, 0);
                appendPlanetBinaryMutualOrbitRings(merged, bodies, bodyWorldPositions, p0, p1, legacyN,
                        useScreenChord, scalePixelsPerMetre, 0);
            }
        } else {
            /*
             * True-scale playback rebuild: only wide-binary mutual + planet-binary guide rings. Schematic concentric
             * branch/hub circles duplicate per-body Kepler strokes and stay circular while bodies move on ellipses.
             */
            appendPlanetBinaryMutualOrbitRings(merged, bodies, bodyWorldPositions, p0, p1, legacyN, useScreenChord,
                    scalePixelsPerMetre, viewTilt);
            int primaryStar = primaryAnchorBodyMapKey(bodies);
            if (primaryStar >= 0) {
                appendPlanetBinaryBarycentreRingsAtStar(merged, bodies, bodyWorldPositions, primaryStar, p0, p1,
                        legacyN, useScreenChord, scalePixelsPerMetre, viewTilt);
            }
            merged = removeTrueScaleBarycentricGhostPlanetStrokes(merged, bodies, bodyWorldPositions,
                    resolvedParentsByBodyId, useModelParents, p0, p1);
            merged = removeTrueScaleSchematicConcentricBranchRings(merged);
        }
        return dedupeOrbitPolylinesUntilStable(merged);
    }

    /**
     * True when journal distance / semi-major axis are missing so layout falls back to deterministic pseudo radii.
     */
    public static boolean bodyLayoutDataIsEstimated(BodyInfo b, int mapBodyId, Map<Integer, BodyInfo> bodies) {
        if (b == null) {
            return true;
        }
        int pId = bodies != null ? resolveOrbitParentBodyId(b, bodies, mapBodyId) : -1;
        double ls = journalOrbitRadiusLsFromParent(b, pId, bodies, mapBodyId);
        if (Double.isFinite(ls) && ls > 0.0) {
            return false;
        }
        double dist = b.getDistanceLs();
        if (Double.isFinite(dist) && dist > 0.0) {
            return false;
        }
        Double aObj = b.getSemiMajorAxisM();
        if (aObj != null && aObj.doubleValue() > 0.0 && Double.isFinite(aObj.doubleValue())) {
            return false;
        }
        if (bodies != null && !bodies.isEmpty()) {
            double cum = cumulativeSemiMajorAxisChainLs(bodies, mapBodyId);
            if (Double.isFinite(cum) && cum > 0.0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Wide / multi-star: one concentric set per branch star (A, B, …) for planets that orbit that star directly — not
     * moons (those keep per-parent rings in the generic loop).
     */
    private static void appendBranchStarSchematicRings(List<OrbitPolylineWorldXY> out,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int p0,
            int p1,
            int legacyN,
            boolean useScreenChord,
            double scalePixelsPerMetre,
            Map<Integer, double[]> ringRadiusReferencePositions) {
        if (out == null || bodies == null || bodies.isEmpty()) {
            return;
        }
        boolean hierarchical = isHierarchicalWideBinary(bodies);
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            boolean branchStar = isMapStellarBody(e.getValue())
                    || (hierarchical && isStellarBody(e.getValue()));
            if (!branchStar) {
                continue;
            }
            int starId = e.getKey().intValue();
            /*
             * Four-star hierarchies: only the primary branch star (A) gets concentric schematic rings; B/C/D use
             * Null mutual rings and hierarchical barycentre rings — drawing giant circles at each companion star
             * pulls BCD giants onto misleading ~50k Ls arcs through the B+C cluster.
             */
            if (hierarchical) {
                if (!orbitsWideBinarySystemBarycentre(e.getValue(), bodies, starId)) {
                    continue;
                }
            } else if (!orbitsWideBinarySystemBarycentre(e.getValue(), bodies, starId)) {
                continue;
            }
            appendSchematicRingsAtStar(out, bodies, bodyWorldPositions, starId, p0, p1, legacyN, useScreenChord,
                    scalePixelsPerMetre, ringRadiusReferencePositions);
        }
    }

    /**
     * Concentric schematic rings at each hierarchical {@code ScanBaryCentre} hub (Null:2/3/49) so BCD planets show what
     * orbits the subsystem barycentre when companion stars are excluded from {@link #isMapStellarBody}.
     */
    private static void appendSchematicRingsAtHierarchicalNullBarycentres(List<OrbitPolylineWorldXY> out,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int p0,
            int p1,
            int legacyN,
            boolean useScreenChord,
            double scalePixelsPerMetre,
            Map<Integer, double[]> ringRadiusReferencePositions) {
        if (out == null || bodies == null || bodyWorldPositions == null) {
            return;
        }
        /* Triple A vs B+C: only branch rings at A and the B+C mutual/trunk strokes — not moon-binary hubs. */
        if (isHierarchicalTripleStarMap(bodies)) {
            return;
        }
        HashSet<Integer> nullIds = new HashSet<>();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || !e.getValue().isScanBarycentreRow()) {
                continue;
            }
            nullIds.add(e.getKey());
        }
        int tripleNullId = isHierarchicalTripleStarMap(bodies) ? hierarchicalTripleStellarNullId(bodies) : -1;
        for (Integer nullIdObj : nullIds) {
            int nullId = nullIdObj.intValue();
            if (!isSharedNullBarycentreId(nullId, bodies) && !isPlanetBinaryNullParentId(nullId, bodies)) {
                continue;
            }
            /* B+C stellar pair: mutual ring only — heliocentric hub rings read as empty ~50k Ls circles. */
            if (nullId == tripleNullId) {
                continue;
            }
            appendSchematicRingsAtBarycentreMapKey(out, bodies, bodyWorldPositions,
                    planetBinaryBarycentreMapKey(nullId), nullId, p0, p1, legacyN, useScreenChord,
                    scalePixelsPerMetre, ringRadiusReferencePositions);
        }
    }

    private static void appendSchematicRingsAtBarycentreMapKey(List<OrbitPolylineWorldXY> out,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int barycentreMapKey,
            int journalNullId,
            int p0,
            int p1,
            int legacyN,
            boolean useScreenChord,
            double scalePixelsPerMetre,
            Map<Integer, double[]> ringRadiusReferencePositions) {
        if (out == null || bodies == null || bodyWorldPositions == null || barycentreMapKey >= 0) {
            return;
        }
        double[] hubPos = bodyWorldPositions.get(Integer.valueOf(barycentreMapKey));
        int needLen = Math.max(p0, p1) + 1;
        if (hubPos == null || hubPos.length < needLen) {
            return;
        }
        double cx = worldAxisMetres(hubPos, p0);
        double cy = worldAxisMetres(hubPos, p1);
        if (!Double.isFinite(cx) || !Double.isFinite(cy)) {
            return;
        }
        boolean hierarchicalTrunk = isHierarchicalWideBinary(bodies)
                && isHierarchicalOuterStellarNullPair(journalNullId, bodies);
        double trunkMaxRingLs = hierarchicalTrunk
                ? Math.max(HIERARCHICAL_OUTER_STELLAR_PAIR_SCHEMATIC_MIN_LS * 2.5,
                        planetBinaryOuterStellarPairOrbitRadiusLs(journalNullId, bodies) * 1.15)
                : Double.POSITIVE_INFINITY;
        TreeSet<Long> radiiLs = new TreeSet<>();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isScanBarycentreRow()) {
                continue;
            }
            int id = e.getKey().intValue();
            BodyInfo child = e.getValue();
            int pId = resolveOrbitParentBodyId(child, bodies, id);
            boolean direct = pId == barycentreMapKey
                    || child.getImmediateParentBodyId() == journalNullId;
            if (!direct || isMoonSatelliteBody(child, bodies)) {
                continue;
            }
            double[] bodyPos = bodyWorldPositions.get(e.getKey());
            double[] radiusBodyPos = orbitRadiusBodyPosition(bodyWorldPositions, ringRadiusReferencePositions,
                    e.getKey());
            double[] radiusHubPos = orbitRadiusBodyPosition(bodyWorldPositions, ringRadiusReferencePositions,
                    Integer.valueOf(barycentreMapKey));
            double radM;
            if (hierarchicalTrunk && radiusBodyPos != null && radiusBodyPos.length > Math.max(p0, p1)
                    && radiusHubPos != null && radiusHubPos.length > Math.max(p0, p1)) {
                double bx = worldAxisMetres(radiusBodyPos, p0);
                double by = worldAxisMetres(radiusBodyPos, p1);
                double rcx = worldAxisMetres(radiusHubPos, p0);
                double rcy = worldAxisMetres(radiusHubPos, p1);
                radM = Math.hypot(bx - rcx, by - rcy);
            } else {
                radM = schematicOrbitRadiusMetres(child, barycentreMapKey, bodies, id,
                        radiusBodyPos != null ? radiusBodyPos : bodyPos,
                        radiusHubPos != null ? radiusHubPos : hubPos, p0, p1);
            }
            if (!Double.isFinite(radM) || radM < MIN_FALLBACK_ORBIT_RADIUS_METRES) {
                continue;
            }
            double ls = radM / LIGHT_SECOND_METRES;
            if (hierarchicalTrunk && ls > trunkMaxRingLs) {
                continue;
            }
            radiiLs.add(Long.valueOf(Math.max(1L, Math.round(ls))));
        }
        if (hierarchicalTrunk) {
            for (Integer innerId : nestedStellarInnerNullIds(journalNullId, bodies)) {
                int ik = planetBinaryBarycentreMapKey(innerId.intValue());
                double[] ip = orbitRadiusBodyPosition(bodyWorldPositions, ringRadiusReferencePositions,
                        Integer.valueOf(ik));
                double[] hubRef = orbitRadiusBodyPosition(bodyWorldPositions, ringRadiusReferencePositions,
                        Integer.valueOf(barycentreMapKey));
                if (ip == null || ip.length <= Math.max(p0, p1) || hubRef == null || hubRef.length <= Math.max(p0, p1)) {
                    continue;
                }
                double ls = Math.hypot(worldAxisMetres(ip, p0) - worldAxisMetres(hubRef, p0),
                        worldAxisMetres(ip, p1) - worldAxisMetres(hubRef, p1)) / LIGHT_SECOND_METRES;
                if (Double.isFinite(ls) && ls > 0.5 && ls <= trunkMaxRingLs) {
                    radiiLs.add(Long.valueOf(Math.max(1L, Math.round(ls))));
                }
            }
            for (Integer pbNull : nestedPlanetBinaryNullIdsUnderOuterTrunk(journalNullId, bodies)) {
                int ik = planetBinaryBarycentreMapKey(pbNull.intValue());
                double[] ip = orbitRadiusBodyPosition(bodyWorldPositions, ringRadiusReferencePositions,
                        Integer.valueOf(ik));
                double[] hubRef = orbitRadiusBodyPosition(bodyWorldPositions, ringRadiusReferencePositions,
                        Integer.valueOf(barycentreMapKey));
                if (ip == null || ip.length <= Math.max(p0, p1) || hubRef == null || hubRef.length <= Math.max(p0, p1)) {
                    continue;
                }
                double ls = Math.hypot(worldAxisMetres(ip, p0) - worldAxisMetres(hubRef, p0),
                        worldAxisMetres(ip, p1) - worldAxisMetres(hubRef, p1)) / LIGHT_SECOND_METRES;
                if (Double.isFinite(ls) && ls > 0.5 && ls <= trunkMaxRingLs) {
                    radiiLs.add(Long.valueOf(Math.max(1L, Math.round(ls))));
                }
            }
        }
        double hierarchicalHubCapLs = isHierarchicalWideBinary(bodies)
                ? (hierarchicalTrunk ? trunkMaxRingLs : HIERARCHICAL_WIDE_BINARY_SCHEMATIC_SEP_LS * 0.55)
                : Double.POSITIVE_INFINITY;
        for (Long lsRounded : radiiLs) {
            if (isHierarchicalWideBinary(bodies) && lsRounded.longValue() > hierarchicalHubCapLs) {
                continue;
            }
            double radM = lsRounded.longValue() * LIGHT_SECOND_METRES;
            int n = legacyN;
            if (useScreenChord && Double.isFinite(scalePixelsPerMetre) && scalePixelsPerMetre > 0.0) {
                n = segmentCountForScreenChord(scalePixelsPerMetre, Math.PI * 2.0 * radM, legacyN);
            }
            n = Math.max(12, Math.min(ORBIT_POLYLINE_SEGMENTS_HARD_MAX, n));
            double[] wx = new double[n];
            double[] wy = new double[n];
            for (int i = 0; i < n; i++) {
                double theta = (Math.PI * 2.0 * i) / n;
                wx[i] = cx + radM * Math.cos(theta);
                wy[i] = cy + radM * Math.sin(theta);
            }
            int ringId = SINGLE_STAR_SCHEMATIC_RING_ID_BASE - (-barycentreMapKey) * 100_000 - lsRounded.intValue();
            out.add(new OrbitPolylineWorldXY(ringId, wx, wy));
        }
    }

    /**
     * Concentric rings at {@code starId}: one circle per direct child distance. Skips moons and deeper descendants so
     * sub-system views do not stack a giant ring at the star for every moon distance.
     */
    private static void appendSchematicRingsAtStar(List<OrbitPolylineWorldXY> out,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int starId,
            int p0,
            int p1,
            int legacyN,
            boolean useScreenChord,
            double scalePixelsPerMetre,
            Map<Integer, double[]> ringRadiusReferencePositions) {
        if (out == null || bodies == null || bodies.isEmpty() || bodyWorldPositions == null || starId < 0) {
            return;
        }
        double[] starPos = bodyWorldPositions.get(Integer.valueOf(starId));
        int needLen = Math.max(p0, p1) + 1;
        if (starPos == null || starPos.length < needLen) {
            return;
        }
        double cx = worldAxisMetres(starPos, p0);
        double cy = worldAxisMetres(starPos, p1);
        if (!Double.isFinite(cx) || !Double.isFinite(cy)) {
            return;
        }
        TreeSet<Long> radiiLs = new TreeSet<>();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            if (id == starId || isMapStellarBody(e.getValue())) {
                continue;
            }
            int pId = resolveOrbitParentBodyId(e.getValue(), bodies, id);
            if (pId != starId) {
                continue;
            }
            double[] bodyPos = bodyWorldPositions.get(e.getKey());
            double[] radiusBodyPos = orbitRadiusBodyPosition(bodyWorldPositions, ringRadiusReferencePositions,
                    e.getKey());
            double[] radiusStarPos = orbitRadiusBodyPosition(bodyWorldPositions, ringRadiusReferencePositions,
                    Integer.valueOf(starId));
            double radM = schematicOrbitRadiusMetres(e.getValue(), starId, bodies, id,
                    radiusBodyPos != null ? radiusBodyPos : bodyPos,
                    radiusStarPos != null ? radiusStarPos : starPos, p0, p1);
            if (!Double.isFinite(radM) || radM < MIN_FALLBACK_ORBIT_RADIUS_METRES) {
                continue;
            }
            radiiLs.add(Long.valueOf(Math.max(1L, Math.round(radM / LIGHT_SECOND_METRES))));
        }
        double maxBranchRingLs = isHierarchicalWideBinary(bodies)
                ? HIERARCHICAL_WIDE_BINARY_SCHEMATIC_SEP_LS * 0.55
                : Double.POSITIVE_INFINITY;
        for (Long lsRounded : radiiLs) {
            if (Double.isFinite(maxBranchRingLs) && lsRounded.longValue() > maxBranchRingLs) {
                continue;
            }
            double radM = lsRounded.longValue() * LIGHT_SECOND_METRES;
            int n = legacyN;
            if (useScreenChord && Double.isFinite(scalePixelsPerMetre) && scalePixelsPerMetre > 0.0) {
                n = segmentCountForScreenChord(scalePixelsPerMetre, Math.PI * 2.0 * radM, legacyN);
            }
            n = Math.max(12, Math.min(ORBIT_POLYLINE_SEGMENTS_HARD_MAX, n));
            double[] wx = new double[n];
            double[] wy = new double[n];
            for (int i = 0; i < n; i++) {
                double theta = (Math.PI * 2.0 * i) / n;
                wx[i] = cx + radM * Math.cos(theta);
                wy[i] = cy + radM * Math.sin(theta);
            }
            int ringId = SINGLE_STAR_SCHEMATIC_RING_ID_BASE - starId * 100_000 - lsRounded.intValue();
            out.add(new OrbitPolylineWorldXY(ringId, wx, wy));
        }
    }

    /**
     * When a planet/moon's parent is a branch star (A/B orbiting the barycentre), the concentric schematic at that star
     * replaces a per-body circle in the generic loop.
     */
    public static int branchSchematicStarParentId(Map<Integer, BodyInfo> bodies, int parentMapId) {
        if (bodies == null || parentMapId < 0) {
            return -1;
        }
        BodyInfo parent = bodies.get(Integer.valueOf(parentMapId));
        if (parent == null) {
            return -1;
        }
        boolean branchStar = isMapStellarBody(parent)
                || (isHierarchicalWideBinary(bodies) && isStellarBody(parent));
        if (!branchStar) {
            return -1;
        }
        if (!orbitsWideBinarySystemBarycentre(parent, bodies, parentMapId)
                && !isHierarchicalWideBinary(bodies)) {
            return -1;
        }
        if (countMapStellarBodies(bodies) >= 2 || isHierarchicalWideBinary(bodies)) {
            return parentMapId;
        }
        if (countMapStellarBodies(bodies) == 1 && isSingleStarSchematicMap(bodies)) {
            int central = schematicCentralStarMapKey(bodies);
            return parentMapId == central ? central : -1;
        }
        return -1;
    }

    /**
     * Wide-binary schematic: both stars orbit the barycentre (parent {@code -1}) but journal semi-major axes are
     * often tiny (metres) while {@link BodyInfo#getDistanceLs()} implies thousands of Ls separation. Draw one large
     * ring at the origin so the map links the two stellar clusters.
     */
    private static void appendBinaryBarycentreOrbitRing(List<OrbitPolylineWorldXY> out,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int p0,
            int p1,
            int legacyN,
            boolean useScreenChord,
            double scalePixelsPerMetre,
            int viewTiltDeg) {
        if (out == null || bodies == null || bodies.isEmpty() || bodyWorldPositions == null) {
            return;
        }
        /*
         * Four-star hierarchies (A vs BCD): only A orbits Null:0. A heliocentric mutual ring through B/C at ~50k Ls
         * is the screenshot failure mode — never draw it.
         */
        if (isHierarchicalWideBinary(bodies)) {
            return;
        }
        List<Integer> baryStarIds = new ArrayList<>();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            if (!isMapStellarBody(e.getValue())) {
                continue;
            }
            if (!orbitsWideBinarySystemBarycentre(e.getValue(), bodies, id)) {
                continue;
            }
            baryStarIds.add(Integer.valueOf(id));
        }
        if (baryStarIds.size() < 2) {
            return;
        }

        double maxSepLs = 0.0;
        int primaryId = primaryAnchorBodyMapKey(bodies);
        int needLen = Math.max(p0, p1) + 1;
        List<double[]> baryStarProj = new ArrayList<>();
        for (Integer sid : baryStarIds) {
            BodyInfo b = bodies.get(sid);
            if (b == null) {
                continue;
            }
            double dLs = b.getDistanceLs();
            if (Double.isFinite(dLs)) {
                maxSepLs = Math.max(maxSepLs, dLs);
            }
            double[] pos = bodyWorldPositions.get(sid);
            if (pos == null || pos.length < needLen) {
                continue;
            }
            double rx = worldAxisMetres(pos, p0);
            double ry = worldAxisMetres(pos, p1);
            if (Double.isFinite(rx) && Double.isFinite(ry)) {
                baryStarProj.add(new double[] { rx, ry, sid.intValue() });
            }
        }
        BodyInfo primary = primaryId >= 0 ? bodies.get(Integer.valueOf(primaryId)) : null;
        for (Integer sid : baryStarIds) {
            if (sid.intValue() == primaryId) {
                continue;
            }
            BodyInfo companion = bodies.get(sid);
            if (primary == null || companion == null) {
                continue;
            }
            double dp = primary.getDistanceLs();
            double dc = companion.getDistanceLs();
            if (Double.isFinite(dp) && Double.isFinite(dc)) {
                maxSepLs = Math.max(maxSepLs, Math.abs(dc - dp));
            }
        }

        double cx = 0.0;
        double cy = 0.0;
        double radiusM = 0.0;
        double sepProjLs = Double.NaN;
        double distPrimaryLs = Double.NaN;
        double distCompanionLs = Double.NaN;
        boolean journalRadiusFallback = false;
        if (baryStarProj.size() == 2) {
            double ax = baryStarProj.get(0)[0];
            double ay = baryStarProj.get(0)[1];
            int idA = (int) baryStarProj.get(0)[2];
            double bx = baryStarProj.get(1)[0];
            double by = baryStarProj.get(1)[1];
            int idB = (int) baryStarProj.get(1)[2];
            if (primaryId >= 0 && idB == primaryId) {
                double tx = ax;
                double ty = ay;
                int ti = idA;
                ax = bx;
                ay = by;
                idA = idB;
                bx = tx;
                by = ty;
                idB = ti;
            }
            double sepProjM = Math.hypot(bx - ax, by - ay);
            sepProjLs = sepProjM / LIGHT_SECOND_METRES;
            if (Double.isFinite(maxSepLs) && maxSepLs >= WIDE_BINARY_MIN_JOURNAL_SEP_LS) {
                double journalSepM = maxSepLs * LIGHT_SECOND_METRES;
                double journalHalfSepM = journalSepM * 0.5;
                radiusM = journalHalfSepM;
                journalRadiusFallback = true;
                double dux = bx - ax;
                double duy = by - ay;
                double duLen = Math.hypot(dux, duy);
                if (duLen > LIGHT_SECOND_METRES) {
                    cx = ax + (dux / duLen) * journalHalfSepM;
                    cy = ay + (duy / duLen) * journalHalfSepM;
                } else if (primaryId >= 0) {
                    double[] pPrim = bodyWorldPositions.get(Integer.valueOf(primaryId));
                    if (pPrim != null && pPrim.length >= Math.max(p0, p1) + 1) {
                        cx = worldAxisMetres(pPrim, p0) + journalHalfSepM;
                        cy = worldAxisMetres(pPrim, p1);
                    } else {
                        cx = journalHalfSepM;
                        cy = 0.0;
                    }
                } else {
                    cx = ax + journalHalfSepM;
                    cy = ay;
                }
            } else {
                cx = (ax + bx) * 0.5;
                cy = (ay + by) * 0.5;
                radiusM = sepProjM * 0.5;
                if (sepProjM < LIGHT_SECOND_METRES * 50.0 && Double.isFinite(maxSepLs) && maxSepLs > 50.0) {
                    double halfSepM = (maxSepLs * LIGHT_SECOND_METRES) * 0.5;
                    radiusM = halfSepM;
                    journalRadiusFallback = true;
                }
            }
            distPrimaryLs = Math.hypot(ax - cx, ay - cy) / LIGHT_SECOND_METRES;
            distCompanionLs = Math.hypot(bx - cx, by - cy) / LIGHT_SECOND_METRES;
        } else if (!baryStarProj.isEmpty()) {
            double sumCx = 0.0;
            double sumCy = 0.0;
            for (double[] p : baryStarProj) {
                sumCx += p[0];
                sumCy += p[1];
            }
            cx = sumCx / baryStarProj.size();
            cy = sumCy / baryStarProj.size();
            for (double[] p : baryStarProj) {
                radiusM = Math.max(radiusM, Math.hypot(p[0] - cx, p[1] - cy));
            }
            if (Double.isFinite(maxSepLs) && maxSepLs > 50.0) {
                double halfSepM = (maxSepLs * LIGHT_SECOND_METRES) * 0.5;
                radiusM = Math.max(radiusM, halfSepM);
                journalRadiusFallback = true;
            }
        }
        if (!Double.isFinite(radiusM) || radiusM < MIN_FALLBACK_ORBIT_RADIUS_METRES) {
            return;
        }

        int n = legacyN;
        if (useScreenChord && Double.isFinite(scalePixelsPerMetre) && scalePixelsPerMetre > 0.0) {
            double perimM = Math.PI * 2.0 * radiusM;
            n = segmentCountForScreenChord(scalePixelsPerMetre, perimM, legacyN);
        }
        n = Math.max(12, Math.min(ORBIT_POLYLINE_SEGMENTS_HARD_MAX, n));

        double[] wx = new double[n];
        double[] wy = new double[n];
        double[] centerWorld = meanWorldMetresForBodyIds(bodyWorldPositions, baryStarIds);
        if (centerWorld == null) {
            centerWorld = new double[] { cx, cy, 0.0 };
        }
        fillMapPlaneCircleVertices(wx, wy, centerWorld, radiusM, p0, p1, viewTiltDeg);
        out.add(new OrbitPolylineWorldXY(BINARY_BARYCENTRE_ORBIT_RING_BODY_ID, wx, wy));
    }

    private static double[] meanWorldMetresForBodyIds(Map<Integer, double[]> bodyWorldPositions,
            List<Integer> bodyIds) {
        if (bodyWorldPositions == null || bodyIds == null || bodyIds.isEmpty()) {
            return null;
        }
        double sx = 0.0;
        double sy = 0.0;
        double sz = 0.0;
        int count = 0;
        for (Integer id : bodyIds) {
            if (id == null) {
                continue;
            }
            double[] p = bodyWorldPositions.get(id);
            if (p == null || p.length < 2) {
                continue;
            }
            sx += worldAxisMetres(p, 0);
            sy += worldAxisMetres(p, 1);
            sz += p.length >= 3 ? worldAxisMetres(p, 2) : 0.0;
            count++;
        }
        if (count == 0) {
            return null;
        }
        return new double[] { sx / count, sy / count, sz / count };
    }

    /**
     * Stable key for one logical closed orbit: same parent plus Kepler shape (inclination / nodes / periapsis matter
     * for the projected ellipse) or, for the circular fallback, parent plus radius. Multiple bodies can map to one
     * curve (co-orbital data, twin fallback circles), which would otherwise be stroked twice.
     */
    private static String orbitCurveShapeKey(int parentBodyId, int mapBodyId, BodyInfo b,
            Map<Integer, BodyInfo> bodies, boolean kepler, double fallbackRadiusM) {
        if (bodies != null && b != null && isMoonSatelliteBody(b, bodies)) {
            return "M:" + parentBodyId + ":" + mapBodyId;
        }
        if (kepler) {
            Double aObj = b.getSemiMajorAxisM();
            if (aObj == null || aObj.doubleValue() <= 0 || Double.isNaN(aObj.doubleValue())) {
                return null;
            }
            double a = aObj.doubleValue();
            double e = (b.getEccentricity() != null && !Double.isNaN(b.getEccentricity()))
                    ? clamp(b.getEccentricity().doubleValue(), 0, 0.999999)
                    : 0;
            long ak = Math.round(a / 1000.0);
            long ek = Math.round(e * 1_000_000.0);
            long inc = Math.round(angleRad(b.getOrbitalInclination()) * 1e9);
            long om = Math.round(angleRad(b.getAscendingNode()) * 1e9);
            long wp = Math.round(angleRad(b.getPeriapsis()) * 1e9);
            /*
             * Planets/moons keep per-body keys — identical FSS elements on co-orbital rows must not collapse to one
             * invisible stroke (common on true-scale maps).
             */
            if (bodies != null && b != null && !isMapStellarBody(b)) {
                return "K:" + parentBodyId + ":" + mapBodyId + ":" + ak + ":" + ek + ":" + inc + ":" + om + ":" + wp;
            }
            return "K:" + parentBodyId + ":" + ak + ":" + ek + ":" + inc + ":" + om + ":" + wp;
        }
        if (!Double.isFinite(fallbackRadiusM)) {
            return null;
        }
        long rkLs = Math.max(1L, Math.round(fallbackRadiusM / LIGHT_SECOND_METRES));
        return "C:" + parentBodyId + ":" + mapBodyId + ":" + rkLs;
    }

    /**
     * Drops curves that coincide in world X/Y (duplicate journal/cache rows with different ids draw as a double line).
     */
    private static List<OrbitPolylineWorldXY> dedupeNearlyIdenticalOrbits(List<OrbitPolylineWorldXY> in) {
        if (in.size() <= 1) {
            return in;
        }
        List<OrbitPolylineWorldXY> res = new ArrayList<>(in.size());
        for (OrbitPolylineWorldXY p : in) {
            boolean dup = false;
            for (OrbitPolylineWorldXY q : res) {
                if (!orbitPolylinesEligibleForDedupe(p, q)) {
                    continue;
                }
                if (orbitPolylinesCoincide(p, q)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                res.add(p);
            }
        }
        return res;
    }

    /**
     * Greedy dedupe is not transitive: A may differ enough from B, and B from C, while A and C coincide. Re-run
     * bidirectional dedupe until the list stabilizes so near-duplicate journal rows do not leave parallel strokes.
     */
    private static List<OrbitPolylineWorldXY> dedupeOrbitPolylinesUntilStable(List<OrbitPolylineWorldXY> in) {
        if (in == null || in.size() <= 1) {
            return in;
        }
        List<OrbitPolylineWorldXY> cur = in;
        for (int pass = 0; pass < 12; pass++) {
            List<OrbitPolylineWorldXY> next = dedupeNearlyIdenticalOrbitsBidirectional(new ArrayList<>(cur));
            if (next.size() == cur.size()) {
                return next;
            }
            cur = next;
        }
        return cur;
    }

    /**
     * Greedy dedupe is order-sensitive; a second pass on the reversed list catches pairs that only matched the
     * wrong survivor the first time through.
     */
    private static List<OrbitPolylineWorldXY> dedupeNearlyIdenticalOrbitsBidirectional(List<OrbitPolylineWorldXY> in) {
        List<OrbitPolylineWorldXY> forward = dedupeNearlyIdenticalOrbits(in);
        ArrayList<OrbitPolylineWorldXY> rev = new ArrayList<>(forward);
        Collections.reverse(rev);
        List<OrbitPolylineWorldXY> back = dedupeNearlyIdenticalOrbits(rev);
        Collections.reverse(back);
        return back;
    }

    /** Schematic rings ({@code bodyId < 0}) only dedupe with the same synthetic id; real bodies only with themselves. */
    private static boolean orbitPolylinesEligibleForDedupe(OrbitPolylineWorldXY a, OrbitPolylineWorldXY b) {
        if (a == null || b == null) {
            return false;
        }
        return a.bodyId == b.bodyId;
    }

    /** True when two closed polylines trace the same curve (metres tolerance), including cyclic phase and winding. */
    private static boolean orbitPolylinesCoincide(OrbitPolylineWorldXY a, OrbitPolylineWorldXY b) {
        if (a.wx == null || b.wx == null || a.wy == null || b.wy == null) {
            return false;
        }
        if (a.wx.length < 3 || b.wx.length < 3) {
            return false;
        }
        final int m = 56;
        double[] ax = resampleClosedPolyline1D(a.wx, m);
        double[] ay = resampleClosedPolyline1D(a.wy, m);
        double[] bx = resampleClosedPolyline1D(b.wx, m);
        double[] by = resampleClosedPolyline1D(b.wy, m);
        double span = Math.max(orbitPolylineWorldBoundingSpan(a), orbitPolylineWorldBoundingSpan(b));
        /*
         * Duplicate journal / cache rows often differ slightly in elements while projecting to the same X/Y ring.
         * Greedy dedupe can still leave pairs offset by tens of km; use span-relative slack (plus a metre floor).
         */
        final double eps = Math.max(220_000.0, span * 5e-6);
        for (int shift = 0; shift < m; shift++) {
            if (resampledClosedShiftMatch(ax, ay, bx, by, shift, false, m, eps)) {
                return true;
            }
            if (resampledClosedShiftMatch(ax, ay, bx, by, shift, true, m, eps)) {
                return true;
            }
        }
        return false;
    }

    private static double[] resampleClosedPolyline1D(double[] v, int m) {
        int n = v.length;
        double[] out = new double[m];
        for (int k = 0; k < m; k++) {
            double t = (k + 0.5) / m * n;
            int i0 = (int) Math.floor(t);
            if (i0 >= n) {
                i0 = n - 1;
            }
            int i1 = (i0 + 1) % n;
            double f = t - Math.floor(t);
            out[k] = v[i0] * (1.0 - f) + v[i1] * f;
        }
        return out;
    }

    private static boolean resampledClosedShiftMatch(double[] ax, double[] ay, double[] bx, double[] by,
            int shift, boolean reverse, int m, double eps) {
        for (int i = 0; i < m; i++) {
            int j = reverse ? Math.floorMod(shift - i, m) : Math.floorMod(i + shift, m);
            if (Math.abs(ax[i] - bx[j]) > eps || Math.abs(ay[i] - by[j]) > eps) {
                return false;
            }
        }
        return true;
    }

    private static double orbitPolylineWorldBoundingSpan(OrbitPolylineWorldXY p) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < p.wx.length; i++) {
            minX = Math.min(minX, p.wx[i]);
            maxX = Math.max(maxX, p.wx[i]);
            minY = Math.min(minY, p.wy[i]);
            maxY = Math.max(maxY, p.wy[i]);
        }
        return Math.max(1.0, Math.max(maxX - minX, maxY - minY));
    }

    /**
     * Resolves the parent's journal body id for map orbits and related UI (declared parent, moon name, or primary).
     * <p>
     * Prefer {@link #resolveOrbitParentBodyId(BodyInfo, Map, int)} with the body's <strong>map key</strong> when
     * {@link BodyInfo#getBodyId()} may still be {@code -1} (cache / EDSM rows).
     */
    public static int resolveOrbitParentBodyId(BodyInfo child, Map<Integer, BodyInfo> bodies) {
        return resolveOrbitParentBodyId(child, bodies, inferMapBodyId(child, bodies));
    }

    /**
     * @param mapBodyId key in {@code bodies} for {@code child} (journal id when known)
     */
    public static int resolveOrbitParentBodyId(BodyInfo child, Map<Integer, BodyInfo> bodies, int mapBodyId) {
        return stabilizePrimaryBranchMajorParent(child, bodies, mapBodyId,
                resolveOrbitParentBodyIdImpl(child, bodies, mapBodyId));
    }

    private static int resolveOrbitParentBodyIdImpl(BodyInfo child, Map<Integer, BodyInfo> bodies, int mapBodyId) {
        if (child == null || bodies == null || bodies.isEmpty()) {
            return -1;
        }
        int declared = child.getImmediateParentBodyId();
        /*
         * Moons (A 3 a, A 3 e, …) must orbit their gas-giant host even when journal/cache also lists a co-orbit
         * {@code Null:N} pair. Without this, they parent to the synthetic barycentre map key and drift onto the
         * companion star during wide-binary layout/playback.
         */
        int moonHostPlanet = resolveMoonHostPlanetParent(child, bodies, mapBodyId);
        if (moonHostPlanet >= 0) {
            return moonHostPlanet;
        }
        /*
         * ScanBaryCentre rows (Null:N) before wide-binary companion override — inner stellar multiples (B+C at Null:3)
         * must not be flattened to the system barycentre.
         */
        if (declared >= 0 && bodies.containsKey(Integer.valueOf(declared))) {
            BodyInfo declaredParent = bodies.get(Integer.valueOf(declared));
            if (declaredParent != null && declaredParent.isScanBarycentreRow()) {
                int bypass = designationParentOverCompanionStellarNull(child, bodies, mapBodyId, declared);
                if (bypass >= 0) {
                    return bypass;
                }
                return planetBinaryBarycentreMapKey(declared);
            }
        }
        /*
         * Parents:[{"Null":N}] before wide-binary companion override — works even when ScanBaryCentre rows are not
         * in the cache yet (FSS still scanning).
         */
        if (declared > 0 && isPlanetBinaryNullParentRef(declared, bodies)) {
            return planetBinaryBarycentreMapKey(declared);
        }
        /*
         * Hierarchical companion branch stars (B/C/D) wrongly parented to the arrival star in live cache — including
         * when EDSM syncs planet class and {@link #isMapStellarBody} is false.
         */
        if (isHierarchicalWideBinary(bodies) && isStellarDirectNullMember(child, bodies)) {
            int primary = primaryAnchorBodyMapKey(bodies);
            if (primary >= 0 && mapBodyId != primary && (declared == primary || declared == 0)) {
                int innerNull = inferInnerClusterNullForHierarchicalStar(child, bodies, mapBodyId);
                if (innerNull > 0) {
                    return planetBinaryBarycentreMapKey(innerNull);
                }
            }
        }
        /*
         * Wide-binary companion stars must orbit the barycentre (parent -1), not the primary star. Cache rows often
         * declare parent = primary body id; the primary-anchor fallback would also attach B to A and spin the frame.
         */
        if (isMapStellarBody(child) && isWideBinaryCompanionStar(child, bodies, mapBodyId)) {
            return -1;
        }
        /*
         * Journal {@code Parents:[{"Null":0}]} — id {@code 0} is the barycentre sentinel for stars, not "orbit body
         * row 0". Must run before {@code bodies.containsKey(0)} or companion B is parented to star A when A uses id 0.
         */
        if (declared == 0) {
            if (isMapStellarBody(child)) {
                return -1;
            }
            int inferredNullAtZero = inferPlanetBinaryNullParentId(child, bodies, mapBodyId);
            if (inferredNullAtZero > 0) {
                return planetBinaryBarycentreMapKey(inferredNullAtZero);
            }
            if (bodies.containsKey(Integer.valueOf(0))) {
                return 0;
            }
            int primary = primaryAnchorBodyMapKey(bodies);
            if (primary >= 0 && primary != mapBodyId) {
                return primary;
            }
            return -1;
        }
        if (declared >= 0 && bodies.containsKey(Integer.valueOf(declared))) {
            if (isMapStellarBody(child) && countMapStellarBodies(bodies) >= 2) {
                BodyInfo declaredParent = bodies.get(Integer.valueOf(declared));
                if (declaredParent != null && isMapStellarBody(declaredParent)) {
                    if (isHierarchicalWideBinary(bodies)) {
                        int primary = primaryAnchorBodyMapKey(bodies);
                        if (mapBodyId == primary) {
                            return -1;
                        }
                        if (declared == primary) {
                            int innerNull = inferInnerClusterNullForHierarchicalStar(child, bodies, mapBodyId);
                            if (innerNull > 0) {
                                return planetBinaryBarycentreMapKey(innerNull);
                            }
                            if (isHierarchicalTripleStarMap(bodies)) {
                                int tripleNull = hierarchicalTripleStellarNullId(bodies);
                                if (tripleNull > 0
                                        && isHierarchicalTripleCompanionShiftBody(mapBodyId, tripleNull, bodies)) {
                                    return planetBinaryBarycentreMapKey(tripleNull);
                                }
                            }
                        }
                    } else {
                        return -1;
                    }
                }
            }
            if (!isMapStellarBody(child) && !isPrimaryBranchMajorOnAnchorStar(child, bodies, declared)) {
                int inferredNull = inferPlanetBinaryNullParentId(child, bodies, mapBodyId);
                if (inferredNull > 0) {
                    return planetBinaryBarycentreMapKey(inferredNull);
                }
                int primary = primaryAnchorBodyMapKey(bodies);
                if (isHierarchicalWideBinary(bodies) && (declared == primary || declared == 0)) {
                    if (isHierarchicalTripleStarMap(bodies)) {
                        int tripleNull = hierarchicalTripleStellarNullId(bodies);
                        if (tripleNull > 0
                                && isHierarchicalTripleCompanionShiftBody(mapBodyId, tripleNull, bodies)) {
                            return planetBinaryBarycentreMapKey(tripleNull);
                        }
                    }
                    if (isStellarDirectNullMember(child, bodies)) {
                        int innerNull = inferInnerClusterNullForHierarchicalStar(child, bodies, mapBodyId);
                        if (innerNull > 0) {
                            return planetBinaryBarycentreMapKey(innerNull);
                        }
                    }
                    int fromSiblings = inferNullParentFromDesignationSiblings(child, bodies, mapBodyId);
                    if (fromSiblings > 0) {
                        return planetBinaryBarycentreMapKey(fromSiblings);
                    }
                }
            }
            /*
             * Live cache often parents moons to the companion branch star (e.g. A 3 a–f → star B). Designation
             * {@code A 3 a} must attach to gas giant A 3 so schematic rings and map dots stay on the correct branch.
             */
            if (!isMapStellarBody(child) && isMoonSatelliteBody(child, bodies)) {
                BodyInfo declaredParent = bodies.get(Integer.valueOf(declared));
                if (declaredParent != null && isDeclaredStellarBranchParent(declaredParent, bodies)) {
                    int desigParent = inferParentFromBinarySystemDesignation(child, bodies, mapBodyId);
                    if (desigParent < 0) {
                        String moonHost = moonParentDesignationFromName(child);
                        if (moonHost != null) {
                            desigParent = findBodyIdByDesignationTailMatch(bodies, moonHost, true);
                        }
                    }
                    if (desigParent >= 0 && desigParent != mapBodyId && desigParent != declared) {
                        return desigParent;
                    }
                }
            }
            if (!isMapStellarBody(child)) {
                int desigParent = inferParentFromBinarySystemDesignation(child, bodies, mapBodyId);
                if (desigParent >= 0 && desigParent != mapBodyId && desigParent != declared) {
                    BodyInfo declaredParent = bodies.get(Integer.valueOf(declared));
                    if (declaredParent != null && shouldPreferDesignationParent(child, declaredParent, bodies)) {
                        return desigParent;
                    }
                }
            }
            return declared;
        }
        if (declared > 0 && isPlanetBinaryNullParentId(declared, bodies)) {
            return planetBinaryBarycentreMapKey(declared);
        }
        int fromSuffix = inferParentFromBinarySystemDesignation(child, bodies, mapBodyId);
        if (fromSuffix >= 0) {
            return fromSuffix;
        }
        String moonParentDesig = moonParentDesignationFromName(child);
        if (moonParentDesig != null) {
            int inferred = findBodyIdByDesignation(bodies, moonParentDesig);
            if (inferred >= 0) {
                return inferred;
            }
        }
        /*
         * Do not attach unresolved bodies to the arrival star in wide binaries — B-branch planets would stack on A
         * and branch schematic rings/labels break. Designation parsing above is authoritative when present.
         */
        if (countMapStellarBodies(bodies) < 2) {
            int ip = child.getImmediateParentBodyId();
            if (ip > 0 && isPlanetBinaryNullParentId(ip, bodies)) {
                return planetBinaryBarycentreMapKey(ip);
            }
            int primary = primaryAnchorBodyMapKey(bodies);
            if (primary >= 0 && mapBodyId >= 0 && mapBodyId != primary) {
                return primary;
            }
        }
        return -1;
    }

    private static int inferMapBodyId(BodyInfo child, Map<Integer, BodyInfo> bodies) {
        if (child == null || bodies == null) {
            return -1;
        }
        int bid = child.getBodyId();
        if (bid >= 0 && bodies.containsKey(Integer.valueOf(bid))) {
            return bid;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() != null && e.getValue() == child) {
                return e.getKey().intValue();
            }
        }
        return -1;
    }

    /**
     * Map key for the schematic root: journal body {@code 0} when present, else lowest-id body with a star type,
     * else lowest non-negative map key (EDSM-only systems without id {@code 0}).
     */
    /** Count of bodies treated as real stars on the map (not Sudarsky-tagged gas giants). */
    public static int countMapStellarBodies(Map<Integer, BodyInfo> bodies) {
        if (bodies == null || bodies.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (BodyInfo b : bodies.values()) {
            if (isMapStellarBody(b)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Central star for single-star schematic maps — works when FSS has not set {@link BodyInfo#getStarType()} yet but
     * the table row is still the primary (short name = system name, or 0 Ls).
     */
    public static int schematicCentralStarMapKey(Map<Integer, BodyInfo> bodies) {
        if (bodies == null || bodies.isEmpty()) {
            return -1;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (isPrimaryStarBodyByName(e.getValue())) {
                return e.getKey().intValue();
            }
        }
        if (countMapStellarBodies(bodies) == 1) {
            return primaryAnchorBodyMapKey(bodies);
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            BodyInfo b = e.getValue();
            if (b.getDistanceLs() <= 1.0 && isStellarBody(b) && !hasPlanetarySurfaceOrAtmosphere(b)) {
                return e.getKey().intValue();
            }
        }
        return primaryAnchorBodyMapKey(bodies);
    }

    /** True when the map should use lone-star concentric rings and a {@code *} central marker. */
    public static boolean isSingleStarSchematicMap(Map<Integer, BodyInfo> bodies) {
        if (bodies == null || bodies.isEmpty()) {
            return false;
        }
        if (countMapStellarBodies(bodies) != 1) {
            return false;
        }
        int central = schematicCentralStarMapKey(bodies);
        if (central < 0) {
            return false;
        }
        int orbiting = 0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (e.getKey().intValue() == central) {
                continue;
            }
            if (isMapStellarBody(e.getValue())) {
                continue;
            }
            double d = e.getValue().getDistanceLs();
            if (Double.isFinite(d) && d > 2.0) {
                orbiting++;
            }
        }
        return orbiting >= 1;
    }

    /**
     * Lone-star schematic layout (positions, rings, planet-binary strokes) when {@link #isSingleStarSchematicMap}
     * is false but the system still has one map star and a resolvable central body — e.g. belt-cluster rows counted
     * as a second stellar body, or FSS distances not yet above the 2 Ls threshold.
     */
    public static boolean shouldApplyLoneStarSchematicLayout(Map<Integer, BodyInfo> bodies) {
        if (bodies == null || bodies.isEmpty()) {
            return false;
        }
        if (isHierarchicalWideBinary(bodies)) {
            return false;
        }
        if (isSingleStarSchematicMap(bodies)) {
            return true;
        }
        int central = schematicCentralStarMapKey(bodies);
        if (central < 0) {
            return false;
        }
        if (hasPlanetBinaryNullParentInSystem(bodies) && countMapStellarBodies(bodies) < 2) {
            return true;
        }
        return countMapStellarBodies(bodies) == 1;
    }

    /** True when any journal {@code Null:N} planet-binary barycentre is present. */
    public static boolean hasPlanetBinaryNullParentInSystem(Map<Integer, BodyInfo> bodies) {
        if (bodies == null || bodies.isEmpty()) {
            return false;
        }
        HashSet<Integer> nullParents = new HashSet<>();
        for (BodyInfo b : bodies.values()) {
            if (b == null || isMapStellarBody(b) || isMoonSatelliteBody(b, bodies)) {
                continue;
            }
            int ip = b.getImmediateParentBodyId();
            if (isPlanetBinaryNullParentRef(ip, bodies)) {
                nullParents.add(Integer.valueOf(ip));
            }
        }
        for (Integer nullId : nullParents) {
            if (isPlanetBinaryNullParentId(nullId.intValue(), bodies)) {
                return true;
            }
        }
        return false;
    }

    /** OBAFGKM… and brown-dwarf letters used in Elite spectral tables — not binary branch letters at distance. */
    private static boolean isSpectralTypeLetter(char c) {
        return c == 'O' || c == 'B' || c == 'A' || c == 'F' || c == 'G' || c == 'K' || c == 'M' || c == 'L'
                || c == 'T' || c == 'Y' || c == 'D';
    }

    /**
     * FSS/EDSM often copies spectral class into {@link BodyInfo#getAtmoOrType()} before {@link #getStarType()} is set.
     */
    private static boolean isSpectralClassAtmosphereOnly(BodyInfo b) {
        if (b == null || isStellarBody(b) || hasJournalPlanetClass(b)) {
            return false;
        }
        String atmo = firstNonBlank(b.getAtmosphere(), b.getAtmoOrType());
        if (atmo == null || atmo.isBlank()) {
            return false;
        }
        return isSpectralTypeLetter(Character.toUpperCase(atmo.trim().charAt(0)));
    }

    /** Same rule as System tab Body column {@code *} (short name equals system name). */
    public static boolean isPrimaryStarBodyByName(BodyInfo b) {
        if (b == null) {
            return false;
        }
        String shortName = b.getShortName();
        if (shortName != null && b.getStarSystem() != null && !b.getStarSystem().isBlank()
                && shortName.trim().equals(b.getStarSystem().trim())) {
            return true;
        }
        /*
         * Arrival star row often shows short name {@code M}/{@code K} (spectral class) instead of the system name
         * until a full FSS scan fills {@link BodyInfo#getStarType()}.
         */
        if (shortName != null && !hasJournalPlanetClass(b)) {
            String sn = shortName.trim();
            if (sn.length() == 1) {
                char c = Character.toUpperCase(sn.charAt(0));
                if (isSpectralTypeLetter(c)) {
                    double d = b.getDistanceLs();
                    if (Double.isFinite(d) && d <= 1.0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static int primaryAnchorBodyMapKey(Map<Integer, BodyInfo> bodies) {
        if (bodies == null || bodies.isEmpty()) {
            return 0;
        }
        BodyInfo atZero = bodies.get(Integer.valueOf(0));
        if (atZero != null && isMapStellarBody(atZero)) {
            return 0;
        }
        int bestStar = -1;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (isMapStellarBody(e.getValue())) {
                int id = e.getKey().intValue();
                if (id >= 0 && (bestStar < 0 || id < bestStar)) {
                    bestStar = id;
                }
            }
        }
        if (bestStar >= 0) {
            return bestStar;
        }
        int minKey = Integer.MAX_VALUE;
        for (Integer k : bodies.keySet()) {
            if (k == null) {
                continue;
            }
            int id = k.intValue();
            if (id >= 0 && id < minKey) {
                minKey = id;
            }
        }
        return minKey != Integer.MAX_VALUE ? minKey : 0;
    }

    /**
     * Stellar branch letter from an Elite body name ({@code A 3 e} → {@code A}, {@code BCD 2} → no single letter).
     */
    public static String designationBranchLetter(BodyInfo body) {
        String s = firstNonBlank(body != null ? body.getShortName() : null,
                body != null ? body.getBodyName() : null);
        if (s == null || s.isBlank()) {
            return null;
        }
        Matcher m = TRAILING_STAR_BODY_DESIGNATION.matcher(s.trim());
        if (!m.find()) {
            return null;
        }
        String letter = m.group(1);
        return letter != null ? letter.toUpperCase(Locale.ROOT) : null;
    }

    /**
     * Declared parent is a branch star ({@code A}/{@code B}/…), including when live cache synced planet class onto
     * B/C/D and {@link #isMapStellarBody} is false.
     */
    private static boolean isDeclaredStellarBranchParent(BodyInfo declaredParent, Map<Integer, BodyInfo> bodies) {
        if (declaredParent == null) {
            return false;
        }
        if (isMapStellarBody(declaredParent)) {
            return true;
        }
        return bodies != null && isStellarDirectNullMember(declaredParent, bodies)
                && stellarBranchLetter(declaredParent) != null;
    }

    /**
     * True when cache/journal parents a body to a branch star that does not match its name prefix ({@code A 3} → C).
     */
    private static boolean isWrongBranchStellarParent(BodyInfo child, BodyInfo declaredParent,
            Map<Integer, BodyInfo> bodies) {
        if (child == null || declaredParent == null) {
            return false;
        }
        String bodyBranch = designationBranchLetter(child);
        if (bodyBranch == null || bodyBranch.length() != 1) {
            return false;
        }
        String starBranch = branchLetterOfStellarBody(declaredParent, bodies);
        return starBranch != null && !bodyBranch.equalsIgnoreCase(starBranch);
    }

    /**
     * Prefer designation-inferred parent when cache parents an {@code A n} body to another branch's planet/star
     * ({@code A 3} → {@code BCD 2}) or to a companion stellar {@code Null:N} (B+C at Null:3).
     */
    private static boolean shouldPreferDesignationParent(BodyInfo child, BodyInfo declaredParent,
            Map<Integer, BodyInfo> bodies) {
        if (isWrongBranchStellarParent(child, declaredParent, bodies)) {
            return true;
        }
        String childBranch = designationBranchLetter(child);
        String parentBranch = designationBranchLetter(declaredParent);
        return childBranch != null && childBranch.length() == 1
                && parentBranch != null && parentBranch.length() == 1
                && !childBranch.equalsIgnoreCase(parentBranch);
    }

    /**
     * Live cache often parents primary-branch planets to the inner B+C {@code Null:3} row; they must stay on star A.
     */
    private static int designationParentOverCompanionStellarNull(BodyInfo child, Map<Integer, BodyInfo> bodies,
            int mapBodyId, int nullId) {
        if (child == null || bodies == null || isMapStellarBody(child)
                || isStellarDirectNullMember(child, bodies)) {
            return -1;
        }
        String branch = designationBranchLetter(child);
        if (branch == null || branch.length() != 1 || !hasStellarDirectMemberAtNull(nullId, bodies)) {
            return -1;
        }
        if (!companionStellarNullHasNonMatchingBranch(nullId, branch, bodies)) {
            return -1;
        }
        return inferParentFromBinarySystemDesignation(child, bodies, mapBodyId);
    }

    private static boolean companionStellarNullHasNonMatchingBranch(int nullId, String branch,
            Map<Integer, BodyInfo> bodies) {
        for (BodyInfo b : bodies.values()) {
            if (b == null || b.getImmediateParentBodyId() != nullId) {
                continue;
            }
            if (!isStellarDirectNullMember(b, bodies)) {
                continue;
            }
            String letter = stellarBranchLetter(b);
            if (letter != null && !branch.equalsIgnoreCase(letter)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code A 1}/{@code A 2} cache-parented to arrival star A must not be re-parented to companion {@code Null:3}
     * via {@link #inferPlanetBinaryNullParentId} (live Eor Aowsy cache).
     */
    private static boolean isPrimaryBranchMajorOnAnchorStar(BodyInfo child, Map<Integer, BodyInfo> bodies,
            int declared) {
        if (child == null || bodies == null || isMapStellarBody(child)
                || hasEliteMoonDesignationSuffix(child)) {
            return false;
        }
        int primary = primaryAnchorBodyMapKey(bodies);
        if (primary < 0 || declared != primary) {
            return false;
        }
        String branch = designationBranchLetter(child);
        if (branch == null || branch.length() != 1) {
            return false;
        }
        BodyInfo anchor = bodies.get(Integer.valueOf(primary));
        String anchorLetter = anchor != null ? stellarBranchLetter(anchor) : null;
        return anchorLetter != null && branch.equalsIgnoreCase(anchorLetter);
    }

    /**
     * Final guard: {@code A n} majors must orbit star {@code A}, not companion nulls or system root ({@code -1}).
     */
    private static int stabilizePrimaryBranchMajorParent(BodyInfo child, Map<Integer, BodyInfo> bodies,
            int mapBodyId, int resolved) {
        if (child == null || bodies == null || isMapStellarBody(child)
                || hasEliteMoonDesignationSuffix(child)) {
            return resolved;
        }
        String branch = designationBranchLetter(child);
        if (branch == null || branch.length() != 1) {
            return resolved;
        }
        int branchStar = findBodyIdByDesignationTailMatch(bodies, branch, true);
        if (branchStar < 0 || branchStar == mapBodyId) {
            return resolved;
        }
        if (resolved == branchStar) {
            return resolved;
        }
        if (isPlanetBinaryBarycentreMapKey(resolved)) {
            int nullId = journalNullIdFromPlanetBinaryBarycentreMapKey(resolved);
            if (companionStellarNullHasNonMatchingBranch(nullId, branch, bodies)) {
                return branchStar;
            }
        }
        if (resolved == -1 || resolved == 0) {
            return branchStar;
        }
        if (resolved >= 0 && bodies.containsKey(Integer.valueOf(resolved))) {
            BodyInfo parent = bodies.get(Integer.valueOf(resolved));
            if (parent != null && !parent.isScanBarycentreRow()) {
                String parentBranch = designationBranchLetter(parent);
                if (parentBranch != null && parentBranch.length() == 1
                        && !branch.equalsIgnoreCase(parentBranch)) {
                    return branchStar;
                }
                if (parentBranch != null && parentBranch.length() > 1) {
                    return branchStar;
                }
            }
        }
        return resolved;
    }

    /**
     * Branch letter for a star row even when live cache synced {@link BodyInfo#setPlanetClass} and
     * {@link #isMapStellarBody} / {@link #isDeclaredStellarBranchParent} are false ({@code A 3} → C in cache).
     */
    private static String branchLetterOfStellarBody(BodyInfo star, Map<Integer, BodyInfo> bodies) {
        if (star == null) {
            return null;
        }
        String fromStellar = stellarBranchLetter(star);
        if (fromStellar != null) {
            return fromStellar.toUpperCase(Locale.ROOT);
        }
        if (isDeclaredStellarBranchParent(star, bodies) || isMapStellarBody(star) || isStellarBody(star)) {
            String sn = firstNonBlank(star.getShortName(), star.getBodyName());
            if (sn != null) {
                sn = sn.trim();
                if (sn.length() == 1) {
                    return sn.toUpperCase(Locale.ROOT);
                }
            }
        }
        String fromDesig = designationBranchLetter(star);
        return fromDesig != null && fromDesig.length() == 1 ? fromDesig.toUpperCase(Locale.ROOT) : null;
    }

    /**
     * Host planet for schematic moon rings ({@code A 3 a} → A 3), not a co-orbit {@code Null:N} barycentre or branch star.
     */
    private static int resolveMoonHostPlanetParent(BodyInfo child, Map<Integer, BodyInfo> bodies, int mapBodyId) {
        if (child == null || bodies == null || !hasEliteMoonDesignationSuffix(child)
                || isPlanetBinaryMajorDesignation(child)) {
            return -1;
        }
        int declared = child.getImmediateParentBodyId();
        if (declared > 0) {
            BodyInfo declaredParent = bodies.get(Integer.valueOf(declared));
            if (declaredParent != null && !declaredParent.isScanBarycentreRow()
                    && !isMapStellarBody(declaredParent)
                    && !(isPlanetBinaryNullParentRef(declared, bodies) && isPlanetBinaryMajorDesignation(child))) {
                return declared;
            }
        }
        int host = inferParentFromBinarySystemDesignation(child, bodies, mapBodyId);
        if (host < 0) {
            String moonHostDesig = moonParentDesignationFromName(child);
            if (moonHostDesig != null) {
                host = findBodyIdByDesignationTailMatch(bodies, moonHostDesig, true);
            }
        }
        if (host < 0 || host == mapBodyId) {
            return -1;
        }
        BodyInfo hostBody = bodies.get(Integer.valueOf(host));
        if (hostBody == null || hostBody.isScanBarycentreRow() || isMapStellarBody(hostBody)) {
            return -1;
        }
        return host;
    }

    private static String moonParentDesignationFromName(BodyInfo child) {
        String s = firstNonBlank(child.getShortName(), child.getBodyName());
        if (s == null) {
            return null;
        }
        s = s.trim();
        Matcher compact = MOON_DESIGNATION.matcher(s);
        if (compact.matches()) {
            return compact.group(1);
        }
        String[] parts = s.split("\\s+");
        if (parts.length >= 3) {
            String last = parts[parts.length - 1];
            String prev = parts[parts.length - 2];
            if (last.length() == 1 && Character.isLetter(last.charAt(0))
                    && prev.length() == 1 && Character.isLetter(prev.charAt(0))) {
                return String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1));
            }
        }
        if (parts.length >= 2) {
            String last = parts[parts.length - 1];
            String prev = parts[parts.length - 2];
            if (last.length() == 1 && Character.isLetter(last.charAt(0)) && prev.matches("\\d+")) {
                return String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1));
            }
        }
        return null;
    }

    /**
     * True for Elite-style moon designations ({@code 3 a}, {@code 12a}), not majors like {@code 2}.
     * <p>
     * The plan map uses this for subsystem hub cues only: twin blue rings mean “moon cluster host”, so a giant that
     * only has another gas giant co-orbiting as a hierarchical child must not qualify.
     */
    public static boolean isMoonSatelliteBody(BodyInfo b) {
        return isMoonSatelliteBody(b, null);
    }

    /**
     * @param bodies when non-null, direct children of a journal {@code ScanBaryCentre} / {@code Null:N} row are
     *               planet-binary majors ({@code 1 b}, {@code 1 c}), not moons — unless the name is a moon suffix
     *               ({@code A 3 e} co-orbiting at {@code Null:15} still orbits planet {@code A 3}).
     */
    public static boolean isMoonSatelliteBody(BodyInfo b, Map<Integer, BodyInfo> bodies) {
        if (b == null || !hasEliteMoonDesignationSuffix(b)) {
            return false;
        }
        if (bodies != null) {
            int ip = b.getImmediateParentBodyId();
            if (ip > 0 && isPlanetBinaryNullParentRef(ip, bodies) && isPlanetBinaryMajorDesignation(b)) {
                return false;
            }
        }
        return true;
    }

    /** {@code 1 b} / {@code 12a} co-orbit row — not {@code A 3 e} or {@code BCD 2 a} moon suffixes. */
    private static boolean isPlanetBinaryMajorDesignation(BodyInfo b) {
        String s = firstNonBlank(b.getShortName(), b.getBodyName());
        return s != null && MOON_DESIGNATION.matcher(s.trim()).matches();
    }

    /** True when the body name ends with an Elite moon suffix ({@code 3 a}, {@code A 3 e}, {@code BCD 2 a}). */
    public static boolean hasEliteMoonDesignationSuffix(BodyInfo b) {
        return hasEliteMoonDesignationInName(firstNonBlank(b != null ? b.getShortName() : null,
                b != null ? b.getBodyName() : null));
    }

    public static boolean hasEliteMoonDesignationInName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String s = name.trim();
        Matcher compact = MOON_DESIGNATION.matcher(s);
        if (compact.matches()) {
            return true;
        }
        Matcher trailing = TRAILING_STAR_BODY_DESIGNATION.matcher(s);
        if (trailing.find()) {
            String moon = trailing.group(3);
            return moon != null && !moon.isEmpty();
        }
        String[] parts = s.split("\\s+");
        if (parts.length >= 3) {
            String last = parts[parts.length - 1];
            String prev = parts[parts.length - 2];
            if (last.length() == 1 && Character.isLetter(last.charAt(0))
                    && prev.length() == 1 && Character.isLetter(prev.charAt(0))) {
                return true;
            }
        }
        if (parts.length >= 2) {
            String last = parts[parts.length - 1];
            String prev = parts[parts.length - 2];
            return prev.matches("\\d+") && last.length() == 1 && Character.isLetter(last.charAt(0));
        }
        return false;
    }

    public static boolean isPlanetBinaryBarycentreMapKey(int mapKey) {
        return mapKey <= PLANET_BINARY_BARYCENTRE_MAP_KEY_BASE
                && mapKey > PLANET_BINARY_BARYCENTRE_MAP_KEY_BASE - 100_000;
    }

    public static boolean isPlanetBinaryMutualOrbitRingBodyId(int bodyId) {
        return bodyId <= PLANET_BINARY_MUTUAL_ORBIT_RING_ID_BASE
                && bodyId > PLANET_BINARY_MUTUAL_ORBIT_RING_ID_BASE - 100_000;
    }

    public static boolean isPlanetBinaryOuterBarycentreOrbitRingBodyId(int bodyId) {
        return bodyId <= PLANET_BINARY_OUTER_ORBIT_RING_ID_BASE
                && bodyId > PLANET_BINARY_OUTER_ORBIT_RING_ID_BASE - 100_000;
    }

    public static int planetBinaryBarycentreMapKey(int journalNullParentId) {
        return PLANET_BINARY_BARYCENTRE_MAP_KEY_BASE - journalNullParentId;
    }

    public static int journalNullIdFromPlanetBinaryBarycentreMapKey(int mapKey) {
        return PLANET_BINARY_BARYCENTRE_MAP_KEY_BASE - mapKey;
    }

    /**
     * Tree/hierarchy parent for a synthetic planet-binary hub: primary-branch co-orbiters (e.g. {@code A 2}+{@code A 3}
     * at {@code Null:14}) hang under star {@code A}, not {@code Null:0}. Companion-cluster pairs (e.g. {@code BCD 2}+
     * {@code BCD 3} at {@code Null:49}) return {@code -1} (system root / companion subtree).
     */
    public static int planetBinaryBarycentreHierarchyParentMapKey(int pbMapKey, Map<Integer, BodyInfo> bodies) {
        if (!isPlanetBinaryBarycentreMapKey(pbMapKey) || bodies == null || bodies.isEmpty()) {
            return -1;
        }
        int nullId = journalNullIdFromPlanetBinaryBarycentreMapKey(pbMapKey);
        if (!isPlanetBinaryNullParentId(nullId, bodies)) {
            return -1;
        }
        int primary = primaryAnchorBodyMapKey(bodies);
        if (primary < 0) {
            return -1;
        }
        BodyInfo anchor = bodies.get(Integer.valueOf(primary));
        String anchorBranch = branchLetterOfStellarBody(anchor, bodies);
        if (anchorBranch == null) {
            if (countMapStellarBodies(bodies) < 2) {
                return schematicCentralStarMapKey(bodies);
            }
            return -1;
        }
        boolean anyMajor = false;
        for (BodyInfo b : bodies.values()) {
            if (b == null || b.isScanBarycentreRow() || isMapStellarBody(b) || isMoonSatelliteBody(b, bodies)) {
                continue;
            }
            if (b.getImmediateParentBodyId() != nullId) {
                continue;
            }
            String branch = designationBranchLetter(b);
            if (branch == null || !anchorBranch.equalsIgnoreCase(branch)) {
                return -1;
            }
            anyMajor = true;
        }
        return anyMajor ? primary : -1;
    }

    /**
     * Journal {@code Parents:[{"Null":N},…]} where {@code N} is not a scanned body row — two or more majors share it.
     */
    public static boolean isPlanetBinaryNullParentId(int journalNullParentId, Map<Integer, BodyInfo> bodies) {
        if (journalNullParentId <= 0 || bodies == null || bodies.isEmpty()) {
            return false;
        }
        BodyInfo sentinel = bodies.get(Integer.valueOf(journalNullParentId));
        if (sentinel != null && !sentinel.isScanBarycentreRow()) {
            return false;
        }
        int majors = 0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            BodyInfo b = e.getValue();
            if (isMapStellarBody(b) || isMoonSatelliteBody(b, bodies)) {
                continue;
            }
            if (b.getImmediateParentBodyId() == journalNullParentId) {
                majors++;
                if (majors >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@code ScanBaryCentre} Null parent shared by two or more non-moon bodies (planets and/or stars), e.g. B+C at
     * Null:3 or BCD 2+BCD 3 at Null:49.
     */
    public static boolean isSharedNullBarycentreId(int journalNullParentId, Map<Integer, BodyInfo> bodies) {
        if (journalNullParentId <= 0 || bodies == null || bodies.isEmpty()) {
            return false;
        }
        BodyInfo sentinel = bodies.get(Integer.valueOf(journalNullParentId));
        if (sentinel != null && !sentinel.isScanBarycentreRow()) {
            return false;
        }
        int members = 0;
        for (BodyInfo b : bodies.values()) {
            if (b == null || isMoonSatelliteBody(b, bodies)) {
                continue;
            }
            if (b.getImmediateParentBodyId() == journalNullParentId) {
                members++;
                if (members >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    /** {@code Parents:[{"Null":N}]} — {@code N} absent from bodies or present as {@code ScanBaryCentre} sentinel. */
    private static boolean isPlanetBinaryNullParentRef(int parentId, Map<Integer, BodyInfo> bodies) {
        if (parentId <= 0 || bodies == null) {
            return false;
        }
        BodyInfo row = bodies.get(Integer.valueOf(parentId));
        if (row == null) {
            return true;
        }
        return row.isScanBarycentreRow();
    }

    private static BodyInfo firstPlanetBinarySibling(int journalNullParentId, Map<Integer, BodyInfo> bodies) {
        return firstSharedNullMember(journalNullParentId, bodies, false);
    }

    private static BodyInfo firstSharedNullMember(int journalNullParentId, Map<Integer, BodyInfo> bodies,
            boolean includeStars) {
        BodyInfo best = null;
        int bestKey = Integer.MAX_VALUE;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            BodyInfo b = e.getValue();
            if (!includeStars && isMapStellarBody(b)) {
                continue;
            }
            if (isMoonSatelliteBody(b, bodies)) {
                continue;
            }
            if (b.getImmediateParentBodyId() != journalNullParentId) {
                continue;
            }
            int k = e.getKey().intValue();
            if (k < bestKey) {
                bestKey = k;
                best = b;
            }
        }
        return best;
    }

    private static double sharedNullBarycentreDistanceLs(int journalNullParentId, Map<Integer, BodyInfo> bodies) {
        double sum = 0.0;
        int n = 0;
        for (BodyInfo b : bodies.values()) {
            if (b == null || isMoonSatelliteBody(b, bodies)) {
                continue;
            }
            if (b.getImmediateParentBodyId() != journalNullParentId) {
                continue;
            }
            double d = b.getDistanceLs();
            if (Double.isFinite(d) && d > 0.0) {
                sum += d;
                n++;
            }
        }
        return n > 0 ? sum / n : Double.NaN;
    }

    /** Mean {@link BodyInfo#getDistanceLs()} of majors sharing a planet-binary Null parent (barycentre from star). */
    private static double planetBinaryBarycentreDistanceLsFromStar(int journalNullParentId,
            Map<Integer, BodyInfo> bodies) {
        double sum = 0.0;
        int n = 0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            BodyInfo b = e.getValue();
            if (isMapStellarBody(b) || isMoonSatelliteBody(b, bodies)) {
                continue;
            }
            if (b.getImmediateParentBodyId() != journalNullParentId) {
                continue;
            }
            double d = b.getDistanceLs();
            if (Double.isFinite(d) && d > 0.0) {
                sum += d;
                n++;
            }
        }
        return n > 0 ? sum / n : Double.NaN;
    }

    private static void seedPlanetBinaryBarycentresForSingleStarMap(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> memo,
            int centralStarId,
            Instant now,
            int p0,
            int p1,
            boolean freezeBarycentreStars) {
        seedPrimaryBranchPlanetBinaryBarycentresAtStar(bodies, memo, centralStarId, now, p0, p1,
                freezeBarycentreStars);
    }

    /**
     * Seeds synthetic {@code Null:N} hub keys on a journal-radius circle around a branch star (e.g. Coeus {@code Null:14}
     * around A). Skips companion-cluster pairs (e.g. Eor Aowsy {@code Null:49} on BCD).
     */
    private static void seedPrimaryBranchPlanetBinaryBarycentresAtStar(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> memo,
            int centralStarId,
            Instant now,
            int p0,
            int p1,
            boolean freezeBarycentreStars) {
        if (bodies == null || memo == null || centralStarId < 0) {
            return;
        }
        HashSet<Integer> nullParents = new HashSet<>();
        for (BodyInfo b : bodies.values()) {
            if (b == null || isMapStellarBody(b) || isMoonSatelliteBody(b, bodies)) {
                continue;
            }
            int ip = b.getImmediateParentBodyId();
            if (isPlanetBinaryNullParentRef(ip, bodies)) {
                nullParents.add(Integer.valueOf(ip));
            }
        }
        double[] starPos = memo.get(Integer.valueOf(centralStarId));
        if (starPos == null) {
            return;
        }
        Instant t = now != null ? now : Instant.now();
        for (Integer nullIdObj : nullParents) {
            int nullId = nullIdObj.intValue();
            if (!isPlanetBinaryNullParentId(nullId, bodies)) {
                continue;
            }
            int bKey = planetBinaryBarycentreMapKey(nullId);
            if (planetBinaryBarycentreHierarchyParentMapKey(bKey, bodies) != centralStarId) {
                continue;
            }
            if (memo.containsKey(Integer.valueOf(bKey))) {
                continue;
            }
            double distLs = planetBinaryBarycentreDistanceLsFromStar(nullId, bodies);
            if (isHierarchicalWideBinary(bodies) && Double.isFinite(distLs)
                    && distLs > HIERARCHICAL_WIDE_BINARY_SCHEMATIC_SEP_LS) {
                distLs = HIERARCHICAL_WIDE_BINARY_SCHEMATIC_SEP_LS;
            }
            BodyInfo outer = planetBinaryOuterOrbitalSource(nullId, bodies);
            BodyInfo ref = firstPlanetBinarySibling(nullId, bodies);
            if (ref == null || !Double.isFinite(distLs) || distLs <= 0.0) {
                continue;
            }
            BodyInfo angleSource = outer != null ? outer : ref;
            double[] rel = schematicMapPlaneOffsetMetresAtHintLs(angleSource, distLs, t, p0, p1,
                    freezeBarycentreStars);
            memo.put(Integer.valueOf(bKey), combineParentAndRelativeOffset(starPos, rel, p0, p1));
            BodyInfo scanRow = bodies.get(Integer.valueOf(nullId));
            if (scanRow != null && scanRow.isScanBarycentreRow()) {
                memo.put(Integer.valueOf(nullId), memo.get(Integer.valueOf(bKey)));
            }
        }
    }

    /**
     * True-scale wide binary (A+B): journal flatten leaves primary-branch planet binaries (e.g. Coeus {@code A 2}+{@code A 3}
     * at {@code Null:14}) on the A–B chord; shift each group onto its star's orbit radius while preserving mutual separation.
     */
    public static void placeTrueScalePrimaryBranchPlanetBinaryHubs(Map<Integer, double[]> positions,
            Map<Integer, BodyInfo> bodies,
            Instant now,
            int mapProjA0,
            int mapProjA1,
            boolean freezeBarycentreStars) {
        if (positions == null || bodies == null || isHierarchicalWideBinary(bodies)) {
            return;
        }
        int p0 = clampWorldAxisIndex(mapProjA0);
        int p1 = clampWorldAxisIndex(mapProjA1);
        if (p0 == p1) {
            return;
        }
        int primaryId = primaryAnchorBodyMapKey(bodies);
        if (primaryId < 0) {
            return;
        }
        double[] starPos = positions.get(Integer.valueOf(primaryId));
        if (starPos == null || starPos.length <= Math.max(p0, p1)) {
            return;
        }
        HashSet<Integer> nullParents = new HashSet<>();
        for (BodyInfo b : bodies.values()) {
            if (b == null || isMapStellarBody(b) || isMoonSatelliteBody(b, bodies)) {
                continue;
            }
            int ip = b.getImmediateParentBodyId();
            if (isPlanetBinaryNullParentRef(ip, bodies)) {
                nullParents.add(Integer.valueOf(ip));
            }
        }
        Instant t = now != null ? now : Instant.now();
        for (Integer nullIdObj : nullParents) {
            int nullId = nullIdObj.intValue();
            if (!isPlanetBinaryNullParentId(nullId, bodies)) {
                continue;
            }
            int bKey = planetBinaryBarycentreMapKey(nullId);
            if (planetBinaryBarycentreHierarchyParentMapKey(bKey, bodies) != primaryId) {
                continue;
            }
            double distLs = planetBinaryBarycentreDistanceLsFromStar(nullId, bodies);
            BodyInfo ref = firstPlanetBinarySibling(nullId, bodies);
            if (ref == null || !Double.isFinite(distLs) || distLs <= 0.0) {
                continue;
            }
            BodyInfo angleSource = planetBinaryOuterOrbitalSource(nullId, bodies);
            if (angleSource == null) {
                angleSource = ref;
            }
            double[] rel = schematicMapPlaneOffsetMetresAtHintLs(angleSource, distLs, t, p0, p1,
                    freezeBarycentreStars);
            double[] targetHub = combineParentAndRelativeOffset(starPos, rel, p0, p1);
            double[] centroid = planetBinaryMemberCentroidWorldXY(nullId, bodies, positions, p0, p1);
            if (centroid == null) {
                positions.put(Integer.valueOf(bKey), targetHub);
                BodyInfo scanRow = bodies.get(Integer.valueOf(nullId));
                if (scanRow != null && scanRow.isScanBarycentreRow()) {
                    positions.put(Integer.valueOf(nullId), Arrays.copyOf(targetHub, targetHub.length));
                }
                continue;
            }
            double dx = worldAxisMetres(targetHub, p0) - centroid[0];
            double dy = worldAxisMetres(targetHub, p1) - centroid[1];
            if (Math.hypot(dx, dy) < LIGHT_SECOND_METRES * 0.5) {
                continue;
            }
            shiftPlanetBinaryNullGroupOnMapPlane(positions, bodies, nullId, dx, dy, p0, p1);
        }
    }

    private static void shiftPlanetBinaryNullGroupOnMapPlane(Map<Integer, double[]> positions,
            Map<Integer, BodyInfo> bodies,
            int journalNullId,
            double shift0,
            double shift1,
            int p0,
            int p1) {
        if (positions == null || bodies == null) {
            return;
        }
        int bKey = planetBinaryBarycentreMapKey(journalNullId);
        HashSet<Integer> keys = new HashSet<>();
        keys.add(Integer.valueOf(bKey));
        keys.add(Integer.valueOf(journalNullId));
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (e.getValue().getImmediateParentBodyId() == journalNullId) {
                keys.add(e.getKey());
            }
        }
        for (Integer key : keys) {
            double[] p = positions.get(key);
            if (p == null || p.length <= Math.max(p0, p1)) {
                continue;
            }
            double[] next = Arrays.copyOf(p, Math.max(3, p.length));
            next[p0] = worldAxisMetres(p, p0) + shift0;
            next[p1] = worldAxisMetres(p, p1) + shift1;
            positions.put(key, next);
        }
    }

    /**
     * Seeds {@code ScanBaryCentre} map keys on the companion side of a wide binary so inner multiples (B+C, BCD 2+3)
     * and their planets can be placed before the iterative wide-binary pass.
     */
    private static void seedSharedNullBarycentresForWideBinaryMap(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> memo,
            int primaryStarId,
            Map<Integer, double[]> starAnchoredPositions,
            Instant now,
            int p0,
            int p1,
            boolean freezeBarycentreStars) {
        if (bodies == null || memo == null || primaryStarId < 0) {
            return;
        }
        int anchorStarId = wideBinaryCompanionAnchorStarId(bodies, memo, primaryStarId);
        if (anchorStarId < 0) {
            return;
        }
        double[] hostPos = memo.get(Integer.valueOf(anchorStarId));
        if (hostPos == null && starAnchoredPositions != null) {
            double[] anchored = starAnchoredPositions.get(Integer.valueOf(anchorStarId));
            if (anchored != null && anchored.length >= 3 && isFiniteXYZ(anchored)) {
                hostPos = new double[] {
                        worldAxisMetres(anchored, 0),
                        worldAxisMetres(anchored, 1),
                        worldAxisMetres(anchored, 2)
                };
                memo.put(Integer.valueOf(anchorStarId), hostPos);
            }
        }
        List<Integer> nullIds = new ArrayList<>();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || !e.getValue().isScanBarycentreRow()) {
                continue;
            }
            nullIds.add(e.getKey());
        }
        Collections.sort(nullIds);
        Instant t = now != null ? now : Instant.now();
        for (Integer nullIdObj : nullIds) {
            int nullId = nullIdObj.intValue();
            if (!isSharedNullBarycentreId(nullId, bodies) && !isPlanetBinaryNullParentId(nullId, bodies)) {
                continue;
            }
            int bKey = planetBinaryBarycentreMapKey(nullId);
            if (memo.containsKey(Integer.valueOf(bKey))) {
                continue;
            }
            double[] loopHost = memo.get(Integer.valueOf(anchorStarId));
            if (loopHost == null) {
                continue;
            }
            BodyInfo anchorBody = bodies.get(Integer.valueOf(anchorStarId));
            if (anchorBody != null && anchorBody.getImmediateParentBodyId() == nullId) {
                memo.put(Integer.valueOf(bKey), new double[] {
                        loopHost[0],
                        loopHost[1],
                        loopHost[2]
                });
                continue;
            }
            int outerNullId = -1;
            for (Integer on : nullIds) {
                if (on.intValue() >= nullId) {
                    continue;
                }
                double[] outerPos = memo.get(Integer.valueOf(planetBinaryBarycentreMapKey(on.intValue())));
                if (outerPos != null) {
                    outerNullId = on.intValue();
                    loopHost = outerPos;
                    break;
                }
            }
            for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
                if (e.getValue() == null) {
                    continue;
                }
                int ip = e.getValue().getImmediateParentBodyId();
                if (ip <= 0 || ip == nullId || !isPlanetBinaryNullParentRef(ip, bodies)) {
                    continue;
                }
                if (!isSharedNullBarycentreId(ip, bodies) && !isPlanetBinaryNullParentId(ip, bodies)) {
                    continue;
                }
                outerNullId = ip;
                double[] outerPos = memo.get(Integer.valueOf(planetBinaryBarycentreMapKey(ip)));
                if (outerPos != null) {
                    loopHost = outerPos;
                    break;
                }
            }
            double baryLs = isHierarchicalOuterStellarNullPair(nullId, bodies)
                    ? stellarNullHostDistanceLs(nullId, bodies)
                    : sharedNullBarycentreDistanceLs(nullId, bodies);
            if (!Double.isFinite(baryLs) || baryLs <= 0.0) {
                baryLs = planetBinaryBarycentreDistanceLsFromStar(nullId, bodies);
            }
            double distLs = baryLs;
            if (outerNullId > 0) {
                double hostLs = isHierarchicalOuterStellarNullPair(outerNullId, bodies)
                        ? stellarNullHostDistanceLs(outerNullId, bodies)
                        : sharedNullBarycentreDistanceLs(outerNullId, bodies);
                if (!Double.isFinite(hostLs)) {
                    BodyInfo hostStar = bodies.get(Integer.valueOf(anchorStarId));
                    hostLs = hostStar != null ? hostStar.getDistanceLs() : Double.NaN;
                }
                if (isNestedStellarInnerNullOfOuterPair(nullId, bodies) && !Double.isFinite(hostLs)) {
                    hostLs = stellarNullHostDistanceLs(outerNullId, bodies);
                }
                if (!Double.isFinite(baryLs) && isNestedStellarInnerNullOfOuterPair(nullId, bodies)) {
                    baryLs = stellarNullHostDistanceLs(nullId, bodies);
                }
                if (Double.isFinite(hostLs) && Double.isFinite(baryLs)) {
                    distLs = Math.max(1.0, Math.abs(baryLs - hostLs));
                }
            } else if (isPlanetBinaryNullParentId(nullId, bodies)) {
                distLs = Math.max(1.0, planetBinaryMutualOrbitRadiusLs(nullId, bodies));
            }
            BodyInfo ref = firstSharedNullMember(nullId, bodies, true);
            if (ref == null || !Double.isFinite(distLs) || distLs <= 0.0) {
                continue;
            }
            if (outerNullId < 0 && isSharedNullBarycentreId(nullId, bodies)
                    && firstSharedNullMember(nullId, bodies, true) != null
                    && isMapStellarBody(firstSharedNullMember(nullId, bodies, true))) {
                memo.put(Integer.valueOf(bKey), new double[] {
                        loopHost[0],
                        loopHost[1],
                        loopHost[2]
                });
                continue;
            }
            BodyInfo outer = planetBinaryOuterOrbitalSource(nullId, bodies);
            BodyInfo angleSource = outer != null ? outer : ref;
            double[] rel = schematicMapPlaneOffsetMetresAtHintLs(angleSource, distLs, t, p0, p1,
                    freezeBarycentreStars);
            memo.put(Integer.valueOf(bKey), combineParentAndRelativeOffset(loopHost, rel, p0, p1));
        }
    }

    /**
     * Stars sharing a {@code ScanBaryCentre} row (e.g. B+C at Null:3) are drawn on a mutual-orbit circle around the
     * synthetic barycentre map key — not stacked at the flatten anchor.
     */
    private static void placeSharedNullStellarMembersOnMutualOrbit(Map<Integer, double[]> memo,
            Map<Integer, BodyInfo> bodies,
            Instant now,
            int p0,
            int p1,
            boolean freezeBarycentreStars) {
        if (memo == null || bodies == null) {
            return;
        }
        Instant t = now != null ? now : Instant.now();
        for (Map.Entry<Integer, BodyInfo> row : bodies.entrySet()) {
            if (row.getKey() == null || row.getValue() == null || !row.getValue().isScanBarycentreRow()) {
                continue;
            }
            int nullId = row.getKey().intValue();
            if (!isSharedNullBarycentreId(nullId, bodies)) {
                continue;
            }
            int bKey = planetBinaryBarycentreMapKey(nullId);
            double[] parentPos = memo.get(Integer.valueOf(bKey));
            if (parentPos == null) {
                continue;
            }
            for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                BodyInfo star = e.getValue();
                if (!isMapStellarBody(star) || star.getImmediateParentBodyId() != nullId) {
                    continue;
                }
                int mapId = e.getKey().intValue();
                double[] rel = planetBinaryOffsetFromBarycentreMetres(star, mapId, bodies, nullId, t, p0, p1,
                        freezeBarycentreStars, null, parentPos);
                if (!isFiniteXYZ(rel)) {
                    continue;
                }
                memo.put(e.getKey(), combineParentAndRelativeOffset(parentPos, rel, p0, p1));
            }
        }
    }

    private static int wideBinaryCompanionAnchorStarId(Map<Integer, BodyInfo> bodies, Map<Integer, double[]> memo,
            int primaryStarId) {
        if (bodies == null || memo == null || primaryStarId < 0) {
            return -1;
        }
        boolean hierarchical = isHierarchicalWideBinary(bodies);
        BodyInfo primary = bodies.get(Integer.valueOf(primaryStarId));
        double dP = primary != null ? primary.getDistanceLs() : 0.0;
        int bestId = -1;
        double bestSep = -1.0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            if (id == primaryStarId) {
                continue;
            }
            boolean companionStar = isMapStellarBody(e.getValue())
                    || (hierarchical && isStellarBody(e.getValue()));
            if (!companionStar) {
                continue;
            }
            if (isWideBinaryPrimaryBranchBody(id, primaryStarId, bodies)) {
                continue;
            }
            double dC = e.getValue().getDistanceLs();
            if (!Double.isFinite(dC) || !Double.isFinite(dP)) {
                continue;
            }
            double sep = Math.abs(dC - dP);
            if (sep > bestSep) {
                bestSep = sep;
                bestId = id;
            }
        }
        return bestId;
    }

    /**
     * Schematic radius of the mutual orbit around a planet-binary barycentre: max {@code |d_child − d_bary|} from
     * heliocentric FSS distances, or half the widest pair separation when those collapse to zero.
     */
    private static boolean isStellarDirectNullMember(BodyInfo b, Map<Integer, BodyInfo> bodies) {
        if (b == null) {
            return false;
        }
        if (isMapStellarBody(b) || isStellarBody(b)) {
            return true;
        }
        /*
         * Live cache may sync planet class onto B/C/D so {@link #isMapStellarBody} is false and starType is blank;
         * still treat single-letter BCD branch stars at a Null parent as mutual-orbit members (Eor Aowsy B+C at Null:3).
         */
        if (!isHierarchicalWideBinary(bodies)) {
            return false;
        }
        String sn = b.getShortName();
        if (sn == null || sn.isBlank()) {
            sn = b.getBodyName();
        }
        if (sn == null) {
            return false;
        }
        sn = sn.trim();
        if (sn.length() == 1) {
            char c = Character.toUpperCase(sn.charAt(0));
            return c >= 'B' && c <= 'Z';
        }
        return false;
    }

    private static boolean hasStellarDirectMemberAtNull(int journalNullParentId, Map<Integer, BodyInfo> bodies) {
        for (BodyInfo b : bodies.values()) {
            if (b == null || isMoonSatelliteBody(b, bodies) || b.getImmediateParentBodyId() != journalNullParentId) {
                continue;
            }
            if (isStellarDirectNullMember(b, bodies)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Outer Null hosts one stellar major (D) plus an inner stellar binary Null (B+C at Null:3) — not a single mutual
     * ring shared with distant BCD giants.
     */
    private static boolean isHierarchicalOuterStellarNullPair(int outerNullId, Map<Integer, BodyInfo> bodies) {
        if (!isHierarchicalWideBinary(bodies) || outerNullId <= 0) {
            return false;
        }
        if (nestedStellarInnerNullIds(outerNullId, bodies).isEmpty()) {
            return false;
        }
        if (hasStellarDirectMemberAtNull(outerNullId, bodies)) {
            return true;
        }
        if (nestedStellarInnerNullIds(outerNullId, bodies).isEmpty()) {
            return false;
        }
        /*
         * Planet-binary hubs (Null:49 for BCD 2+3) are not outer stellar pairs even though Null:3 also exists as a
         * scan row. Four-star A+BCD cache case: infer outer pair only when this Null has no planet-binary majors.
         */
        if (isPlanetBinaryNullParentId(outerNullId, bodies)
                && countStellarDirectNullMembers(outerNullId, bodies) == 0) {
            return false;
        }
        /*
         * Four-star A+BCD: live cache may parent D to star A while ScanBaryCentre still has Null:2 vs inner Null:3
         * (B+C). Treat as outer pair when three branch stars exist and an inner stellar ScanBaryCentre is present.
         */
        return countNonPrimaryHierarchicalBranchStars(bodies) >= 3;
    }

    /**
     * Inner Null ids hosting a stellar pair (e.g. B+C at Null:3 under outer Null:2). Uses {@code ScanBaryCentre} rows
     * when present and journal {@code immediateParentBodyId} refs when live cache rows are not loaded yet.
     */
    /**
     * Planet–planet binaries under a hierarchical outer Null trunk (e.g. BCD 2+3 at Null:49 under Null:2), not the
     * inner stellar pair (B+C at Null:3).
     */
    private static List<Integer> nestedPlanetBinaryNullIdsUnderOuterTrunk(int outerNullId, Map<Integer, BodyInfo> bodies) {
        ArrayList<Integer> out = new ArrayList<>();
        if (!isHierarchicalWideBinary(bodies) || outerNullId <= 0) {
            return out;
        }
        HashSet<Integer> candidates = new HashSet<>();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (e.getValue().isScanBarycentreRow()) {
                int rowId = e.getKey().intValue();
                if (rowId != outerNullId) {
                    candidates.add(Integer.valueOf(rowId));
                }
            }
        }
        for (Integer nullIdObj : candidates) {
            int nullId = nullIdObj.intValue();
            if (nullId == outerNullId || isNestedStellarInnerNullOfOuterPair(nullId, bodies)) {
                continue;
            }
            if (countStellarDirectNullMembers(nullId, bodies) >= 2) {
                continue;
            }
            if (!isPlanetBinaryNullParentId(nullId, bodies) && !isSharedNullBarycentreId(nullId, bodies)) {
                continue;
            }
            out.add(Integer.valueOf(nullId));
        }
        Collections.sort(out);
        return out;
    }

    private static boolean isNestedPlanetBinaryNullUnderOuterTrunk(int nullId, Map<Integer, BodyInfo> bodies) {
        if (!isHierarchicalWideBinary(bodies) || nullId <= 0) {
            return false;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || !e.getValue().isScanBarycentreRow()) {
                continue;
            }
            int outerNullId = e.getKey().intValue();
            if (!isHierarchicalOuterStellarNullPair(outerNullId, bodies)) {
                continue;
            }
            return nestedPlanetBinaryNullIdsUnderOuterTrunk(outerNullId, bodies).contains(Integer.valueOf(nullId));
        }
        return false;
    }

    private static List<Integer> nestedStellarInnerNullIds(int outerNullId, Map<Integer, BodyInfo> bodies) {
        ArrayList<Integer> inners = new ArrayList<>();
        if (!isHierarchicalWideBinary(bodies)) {
            return inners;
        }
        HashSet<Integer> candidates = new HashSet<>();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (e.getValue().isScanBarycentreRow()) {
                int rowId = e.getKey().intValue();
                if (rowId != outerNullId) {
                    candidates.add(Integer.valueOf(rowId));
                }
                continue;
            }
            int ip = e.getValue().getImmediateParentBodyId();
            if (ip > 0 && ip != outerNullId
                    && (isPlanetBinaryNullParentId(ip, bodies) || isSharedNullBarycentreId(ip, bodies))) {
                candidates.add(Integer.valueOf(ip));
            }
        }
        for (Integer innerIdObj : candidates) {
            int innerId = innerIdObj.intValue();
            if (innerId == outerNullId) {
                continue;
            }
            if (countStellarDirectNullMembers(innerId, bodies) >= 2) {
                inners.add(Integer.valueOf(innerId));
                continue;
            }
            /*
             * Four-star B+C at ScanBaryCentre Null:3 under outer Null:2 — cache may parent B/C to star A so
             * countStellarDirectNullMembers(3) is 0; still align via {@link #alignHierarchicalOuterStellarNullPair}.
             */
            BodyInfo scanRow = bodies.get(Integer.valueOf(innerId));
            if (scanRow != null && scanRow.isScanBarycentreRow()
                    && innerId != outerNullId
                    && !isPlanetBinaryNullParentId(innerId, bodies)
                    && countNonPrimaryHierarchicalBranchStars(bodies) >= 3) {
                inners.add(Integer.valueOf(innerId));
            }
        }
        return inners;
    }

    /** Mean heliocentric distance of stellar majors at a Null (excludes BCD giants on the same Null row). */
    private static double stellarNullHostDistanceLs(int journalNullParentId, Map<Integer, BodyInfo> bodies) {
        double sum = 0.0;
        int n = 0;
        for (BodyInfo b : bodies.values()) {
            if (b == null || isMoonSatelliteBody(b, bodies)) {
                continue;
            }
            if (b.getImmediateParentBodyId() != journalNullParentId) {
                continue;
            }
            if (!isStellarDirectNullMember(b, bodies)) {
                continue;
            }
            double d = b.getDistanceLs();
            if (Double.isFinite(d) && d > 0.0) {
                sum += d;
                n++;
            }
        }
        return n > 0 ? sum / n : Double.NaN;
    }

    private static double planetBinaryOuterStellarPairOrbitRadiusLs(int outerNullId, Map<Integer, BodyInfo> bodies) {
        double dOuter = stellarNullHostDistanceLs(outerNullId, bodies);
        if (!Double.isFinite(dOuter)) {
            dOuter = sharedNullBarycentreDistanceLs(outerNullId, bodies);
        }
        if (!Double.isFinite(dOuter)) {
            dOuter = planetBinaryBarycentreDistanceLsFromStar(outerNullId, bodies);
        }
        double maxSep = STELLAR_SHARED_NULL_MUTUAL_ORBIT_MIN_LS;
        for (Integer innerId : nestedStellarInnerNullIds(outerNullId, bodies)) {
            double dInner = stellarNullHostDistanceLs(innerId.intValue(), bodies);
            if (!Double.isFinite(dInner)) {
                dInner = sharedNullBarycentreDistanceLs(innerId.intValue(), bodies);
            }
            if (Double.isFinite(dInner) && Double.isFinite(dOuter)) {
                maxSep = Math.max(maxSep, Math.abs(dInner - dOuter));
            }
        }
        for (BodyInfo b : bodies.values()) {
            if (b == null || isMoonSatelliteBody(b, bodies) || b.getImmediateParentBodyId() != outerNullId) {
                continue;
            }
            if (!isStellarDirectNullMember(b, bodies)) {
                continue;
            }
            double dC = b.getDistanceLs();
            if (Double.isFinite(dC) && Double.isFinite(dOuter)) {
                maxSep = Math.max(maxSep, Math.abs(dC - dOuter));
            }
        }
        return Math.max(maxSep, HIERARCHICAL_OUTER_STELLAR_PAIR_SCHEMATIC_MIN_LS);
    }

    /** Centroid for hierarchical outer stellar pair mutual ring (D + inner Null hub, not BCD giants). */
    private static double[] planetBinaryStellarPairCentroidWorldXY(int outerNullId, Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions, int p0, int p1) {
        if (bodies == null || bodyWorldPositions == null) {
            return null;
        }
        double sx = 0.0;
        double sy = 0.0;
        int n = 0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            BodyInfo b = e.getValue();
            if (isMoonSatelliteBody(b, bodies) || b.getImmediateParentBodyId() != outerNullId) {
                continue;
            }
            if (!isStellarDirectNullMember(b, bodies)) {
                continue;
            }
            double[] p = bodyWorldPositions.get(e.getKey());
            if (p == null || p.length <= Math.max(p0, p1)) {
                continue;
            }
            sx += worldAxisMetres(p, p0);
            sy += worldAxisMetres(p, p1);
            n++;
        }
        for (Integer innerId : nestedStellarInnerNullIds(outerNullId, bodies)) {
            int ik = planetBinaryBarycentreMapKey(innerId.intValue());
            double[] p = bodyWorldPositions.get(Integer.valueOf(ik));
            if (p == null || p.length <= Math.max(p0, p1)) {
                continue;
            }
            sx += worldAxisMetres(p, p0);
            sy += worldAxisMetres(p, p1);
            n++;
        }
        if (n < 2) {
            return null;
        }
        return new double[] { sx / n, sy / n };
    }

    /** Map-plane centroid of non-moon bodies sharing a Null barycentre (for mutual-orbit ring placement). */
    private static double[] planetBinaryMemberCentroidWorldXY(int journalNullParentId, Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions, int p0, int p1) {
        if (bodies == null || bodyWorldPositions == null) {
            return null;
        }
        double sx = 0.0;
        double sy = 0.0;
        int n = 0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            BodyInfo b = e.getValue();
            if (isMoonSatelliteBody(b, bodies) || b.getImmediateParentBodyId() != journalNullParentId) {
                continue;
            }
            double[] p = bodyWorldPositions.get(e.getKey());
            if (p == null || p.length <= Math.max(p0, p1)) {
                continue;
            }
            double x = worldAxisMetres(p, p0);
            double y = worldAxisMetres(p, p1);
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                continue;
            }
            sx += x;
            sy += y;
            n++;
        }
        if (n < 2) {
            return null;
        }
        return new double[] { sx / n, sy / n };
    }

    /** Minimum drawn mutual-orbit radius for a stellar pair at a shared {@code ScanBaryCentre} (e.g. B+C at Null:3). */
    private static final double STELLAR_SHARED_NULL_MUTUAL_ORBIT_MIN_LS = 10.5;

    /**
     * Schematic floor for B+C at Null:3 when nested under Null:2 in a four-star hierarchy — journal separations are only
     * ~4 Ls but must read on the map beside 7500 Ls branch trunks.
     */
    private static final double HIERARCHICAL_INNER_STELLAR_PAIR_SCHEMATIC_MIN_LS = 84.0;

    /** Schematic floor for D vs B+C hub at Null:2 (opposite sides of the outer mutual ring). */
    private static final double HIERARCHICAL_OUTER_STELLAR_PAIR_SCHEMATIC_MIN_LS = 180.0;

    /** Agent/debug: schematic mutual-orbit radius (Ls) for Null:N. */
    public static double planetBinaryMutualOrbitRadiusLsPublic(int journalNullParentId, Map<Integer, BodyInfo> bodies) {
        return planetBinaryMutualOrbitRadiusLs(journalNullParentId, bodies);
    }

    public static boolean hierarchicalOuterStellarNullPairForDebug(int outerNullId, Map<Integer, BodyInfo> bodies) {
        return isHierarchicalOuterStellarNullPair(outerNullId, bodies);
    }

    public static int countStellarDirectNullMembersPublic(int journalNullParentId, Map<Integer, BodyInfo> bodies) {
        return countStellarDirectNullMembers(journalNullParentId, bodies);
    }

    /**
     * BCD 2+3 at Null:49 under Null:2: journal parent-relative separation (~tens of Ls), not heliocentric trunk scale.
     */
    private static double nestedPlanetBinaryMutualOrbitRadiusLs(int journalNullParentId, Map<Integer, BodyInfo> bodies) {
        int bKey = planetBinaryBarycentreMapKey(journalNullParentId);
        double maxHint = 0.0;
        double dBary = sharedNullBarycentreDistanceLs(journalNullParentId, bodies);
        if (!Double.isFinite(dBary)) {
            dBary = planetBinaryBarycentreDistanceLsFromStar(journalNullParentId, bodies);
        }
        double maxRadial = 0.0;
        double minD = Double.POSITIVE_INFINITY;
        double maxD = Double.NEGATIVE_INFINITY;
        int n = 0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            BodyInfo b = e.getValue();
            if (isMoonSatelliteBody(b, bodies) || b.getImmediateParentBodyId() != journalNullParentId) {
                continue;
            }
            int mapId = e.getKey().intValue();
            double hintLs = journalOrbitRadiusLsFromParent(b, bKey, bodies, mapId);
            if (Double.isFinite(hintLs) && hintLs > 0.5) {
                maxHint = Math.max(maxHint, hintLs);
            }
            double dC = b.getDistanceLs();
            if (Double.isFinite(dC) && dC > 0.0) {
                n++;
                minD = Math.min(minD, dC);
                maxD = Math.max(maxD, dC);
                if (Double.isFinite(dBary)) {
                    maxRadial = Math.max(maxRadial, Math.abs(dC - dBary));
                }
            }
        }
        if (maxRadial < 0.5 && n >= 2 && Double.isFinite(minD) && Double.isFinite(maxD)) {
            maxRadial = Math.max(0.5, (maxD - minD) * 0.5);
        }
        double rad = Math.max(maxRadial, maxHint);
        return Math.max(rad, STELLAR_SHARED_NULL_MUTUAL_ORBIT_MIN_LS);
    }

    private static double planetBinaryMutualOrbitRadiusLs(int journalNullParentId, Map<Integer, BodyInfo> bodies) {
        if (isHierarchicalTripleStarMap(bodies) && journalNullParentId == hierarchicalTripleStellarNullId(bodies)) {
            return hierarchicalTripleStellarPairSchematicOrbitRadiusLs(journalNullParentId, bodies);
        }
        if (isHierarchicalOuterStellarNullPair(journalNullParentId, bodies)) {
            return planetBinaryOuterStellarPairOrbitRadiusLs(journalNullParentId, bodies);
        }
        if (isNestedPlanetBinaryNullUnderOuterTrunk(journalNullParentId, bodies)) {
            return nestedPlanetBinaryMutualOrbitRadiusLs(journalNullParentId, bodies);
        }
        double dBary = sharedNullBarycentreDistanceLs(journalNullParentId, bodies);
        if (!Double.isFinite(dBary)) {
            dBary = planetBinaryBarycentreDistanceLsFromStar(journalNullParentId, bodies);
        }
        double maxRadial = 0.0;
        double minD = Double.POSITIVE_INFINITY;
        double maxD = Double.NEGATIVE_INFINITY;
        int n = 0;
        boolean allStellar = true;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            BodyInfo b = e.getValue();
            if (isMoonSatelliteBody(b, bodies) || b.getImmediateParentBodyId() != journalNullParentId) {
                continue;
            }
            if (!isStellarDirectNullMember(b, bodies)) {
                allStellar = false;
            }
            double dC = b.getDistanceLs();
            if (!Double.isFinite(dC) || dC <= 0.0) {
                continue;
            }
            n++;
            minD = Math.min(minD, dC);
            maxD = Math.max(maxD, dC);
            if (Double.isFinite(dBary)) {
                maxRadial = Math.max(maxRadial, Math.abs(dC - dBary));
            }
        }
        if (maxRadial < 0.5 && n >= 2 && Double.isFinite(minD) && Double.isFinite(maxD)) {
            maxRadial = Math.max(0.5, (maxD - minD) * 0.5);
        }
        if (allStellar && n >= 2) {
            double rad = Math.max(maxRadial, STELLAR_SHARED_NULL_MUTUAL_ORBIT_MIN_LS);
            if (isNestedStellarInnerNullOfOuterPair(journalNullParentId, bodies)) {
                rad = Math.max(rad, HIERARCHICAL_INNER_STELLAR_PAIR_SCHEMATIC_MIN_LS);
            }
            return rad;
        }
        if (isHierarchicalWideBinary(bodies) && n >= 2 && countStellarDirectNullMembers(journalNullParentId, bodies) >= 2) {
            double rad = Math.max(maxRadial, STELLAR_SHARED_NULL_MUTUAL_ORBIT_MIN_LS);
            if (isNestedStellarInnerNullOfOuterPair(journalNullParentId, bodies)) {
                rad = Math.max(rad, HIERARCHICAL_INNER_STELLAR_PAIR_SCHEMATIC_MIN_LS);
            }
            return rad;
        }
        return Math.max(maxRadial, 1.0);
    }

    private static int countStellarDirectNullMembers(int journalNullParentId, Map<Integer, BodyInfo> bodies) {
        int n = 0;
        for (BodyInfo b : bodies.values()) {
            if (b == null || isMoonSatelliteBody(b, bodies) || b.getImmediateParentBodyId() != journalNullParentId) {
                continue;
            }
            if (isStellarDirectNullMember(b, bodies)) {
                n++;
            }
        }
        return n;
    }

    private static int countDirectNonMoonChildren(int hostMapId, Map<Integer, BodyInfo> bodies) {
        int n = 0;
        if (bodies == null || hostMapId < 0) {
            return n;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (e.getValue().getImmediateParentBodyId() == hostMapId
                    && !isMoonSatelliteBody(e.getValue(), bodies)) {
                n++;
            }
        }
        return n;
    }

    private static int planetBinarySiblingOrderIndex(int journalNullParentId, int mapBodyId,
            Map<Integer, BodyInfo> bodies) {
        int idx = 0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            BodyInfo b = e.getValue();
            if (isMoonSatelliteBody(b, bodies) || b.getImmediateParentBodyId() != journalNullParentId) {
                continue;
            }
            if (!isStellarDirectNullMember(b, bodies)) {
                continue;
            }
            int k = e.getKey().intValue();
            if (k == mapBodyId) {
                return idx;
            }
            idx++;
        }
        return mapBodyId & 1;
    }

    /**
     * Each major on the schematic mutual-orbit circle ({@link #appendPlanetBinaryMutualOrbitRings}) in map axes.
     * Mutual phase uses each planet's journal {@code OrbitalPeriod} (mutual orbit); heliocentric barycentre motion
     * uses {@link #planetBinaryOuterOrbitalSource} ({@code ScanBaryCentre} period).
     */
    private static double[] planetBinaryOffsetFromBarycentreMetres(BodyInfo child, int mapBodyId,
            Map<Integer, BodyInfo> bodies,
            int journalNullId,
            Instant now,
            int p0,
            int p1,
            boolean freezeBarycentreStars,
            double[] starPosWorld,
            double[] baryPosWorld) {
        if (child == null) {
            return new double[] { 0.0, 0.0, 0.0 };
        }
        double mutualRingLs = planetBinaryMutualOrbitRadiusLs(journalNullId, bodies);
        double dBary = planetBinaryBarycentreDistanceLsFromStar(journalNullId, bodies);
        double dC = child.getDistanceLs();
        double radialLs = mutualRingLs;
        boolean stellarMember = isMapStellarBody(child) || isStellarBody(child);
        if (!stellarMember) {
            int bKey = planetBinaryBarycentreMapKey(journalNullId);
            double hintLs = journalOrbitRadiusLsFromParent(child, bKey, bodies, mapBodyId);
            if (isHierarchicalOuterStellarNullPair(journalNullId, bodies)) {
                double rOuter = planetBinaryOuterStellarPairOrbitRadiusLs(journalNullId, bodies);
                if (Double.isFinite(hintLs) && hintLs > 0.5) {
                    int moonSats = countDirectNonMoonChildren(mapBodyId, bodies);
                    if (hintLs > rOuter * 1.12 && moonSats >= 2) {
                        radialLs = hintLs;
                    } else {
                        radialLs = Math.min(rOuter * 0.95, Math.max(rOuter * 0.55, hintLs));
                    }
                }
            } else if (Double.isFinite(hintLs) && hintLs > 0.5) {
                radialLs = hintLs;
            }
        } else if (bodies == null || countMapStellarBodies(bodies) < 2) {
            if (Double.isFinite(dC) && Double.isFinite(dBary) && dC > 0.0) {
                radialLs = Math.max(mutualRingLs, Math.max(0.5, Math.abs(dC - dBary)));
            }
        }
        double r = radialLs * LIGHT_SECOND_METRES;
        /* freezeBarycentreStars is for wide-binary stellar barycentres, not mutual phase on this ring. */
        double phase = planetBinaryMutualPhaseRadians(child, journalNullId, mapBodyId, bodies, now);
        double[] out = new double[] { 0.0, 0.0, 0.0 };
        out[p0] = r * Math.cos(phase);
        out[p1] = r * Math.sin(phase);
        return out;
    }

    /**
     * Mutual-orbit phase on the drawn ring (map cos/sin) at {@code ω_mut = 2π/P_mutual} from the planet's journal
     * elements — independent of heliocentric barycentre motion ({@link #planetBinaryOuterOrbitalSource}).
     */
    private static double planetBinaryMutualPhaseRadians(BodyInfo child, int journalNullId, int mapBodyId,
            Map<Integer, BodyInfo> bodies, Instant now) {
        Instant t = now != null ? now : Instant.now();
        double M0 = angleRad(child.getMeanAnomaly());
        double pSec = orbitalPeriodSecondsForEvolution(child);
        long epochMs = orbitalEpochMillisForEvolution(child);
        double dtSec = (t.toEpochMilli() - epochMs) / 1000.0;
        double n = (Math.PI * 2.0) / pSec;
        double mutualM = M0 + n * dtSec;
        return wrapToTwoPi(mutualM + (planetBinarySiblingOrderIndex(journalNullId, mapBodyId, bodies) & 1) * Math.PI);
    }

    /**
     * Journal {@code ScanBaryCentre} row for {@code Null:N} heliocentric elements (persisted in {@link CachedBody}).
     * Legacy caches without {@link BodyInfo#isScanBarycentreRow()} may still match via {@link #hasUsableOuterOrbitalElements}.
     */
    private static BodyInfo planetBinaryOuterOrbitalSource(int journalNullParentId, Map<Integer, BodyInfo> bodies) {
        if (journalNullParentId <= 0 || bodies == null) {
            return null;
        }
        BodyInfo row = bodies.get(Integer.valueOf(journalNullParentId));
        if (row != null && row.isScanBarycentreRow()) {
            return row;
        }
        BodyInfo sibling = firstPlanetBinarySibling(journalNullParentId, bodies);
        if (row != null && sibling != null && hasUsableOuterOrbitalElements(row, sibling)) {
            return row;
        }
        return null;
    }

    /** Pre-persistence row at {@code Null:N} with {@code P_outer} much longer than mutual {@code P_mutual}. */
    private static boolean hasUsableOuterOrbitalElements(BodyInfo candidate, BodyInfo mutualRef) {
        if (candidate == null || mutualRef == null) {
            return false;
        }
        double pOuter = orbitalPeriodSecondsForEvolution(candidate);
        double pMutual = orbitalPeriodSecondsForEvolution(mutualRef);
        return pOuter > 1e-6 && pMutual > 1e-6 && pOuter > pMutual * 1.5;
    }

    /** Ratio {@code P_outer / P_mutual} for debug (H10); {@code NaN} when unavailable. */
    public static double planetBinaryOuterToMutualPeriodRatio(int journalNullParentId, Map<Integer, BodyInfo> bodies) {
        BodyInfo outer = planetBinaryOuterOrbitalSource(journalNullParentId, bodies);
        BodyInfo sibling = firstPlanetBinarySibling(journalNullParentId, bodies);
        if (outer == null || sibling == null) {
            return Double.NaN;
        }
        double pOuter = orbitalPeriodSecondsForEvolution(outer);
        double pMutual = orbitalPeriodSecondsForEvolution(sibling);
        if (!(pOuter > 1e-6) || !(pMutual > 1e-6)) {
            return Double.NaN;
        }
        return pOuter / pMutual;
    }

    /**
     * Recover {@code Null:N} when cache still has {@code immediateParentBodyId = 0} (star) but a co-orbiting sibling
     * already stores the shared barycentre id (common after partial FSS before detailed scan).
     */
    /**
     * When cache wrongly parents a body to the arrival star, copy the {@code Null:N} id already stored on a
     * designation sibling (e.g. {@code BCD 4} still has parent {@code 2} while {@code BCD 1} was flattened to star A).
     */
    private static int inferNullParentFromDesignationSiblings(BodyInfo child, Map<Integer, BodyInfo> bodies,
            int mapBodyId) {
        if (child == null || bodies == null) {
            return -1;
        }
        String prefix = multiTokenDesignationPrefix(child);
        if (prefix == null) {
            return -1;
        }
        String prefixKey = prefix + " ";
        int bestNull = -1;
        int bestVotes = 0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getKey().intValue() == mapBodyId) {
                continue;
            }
            BodyInfo other = e.getValue();
            String otherName = firstNonBlank(other.getShortName(), other.getBodyName());
            if (otherName == null || !otherName.startsWith(prefixKey)) {
                continue;
            }
            int otherIp = other.getImmediateParentBodyId();
            if (otherIp <= 0 || (!isPlanetBinaryNullParentId(otherIp, bodies)
                    && !isPlanetBinaryNullParentRef(otherIp, bodies))) {
                continue;
            }
            int votes = 0;
            for (BodyInfo peer : bodies.values()) {
                if (peer != null && peer.getImmediateParentBodyId() == otherIp) {
                    String peerName = firstNonBlank(peer.getShortName(), peer.getBodyName());
                    if (peerName != null && peerName.startsWith(prefixKey)) {
                        votes++;
                    }
                }
            }
            if (votes > bestVotes || (votes == bestVotes && votes > 0 && otherIp < bestNull)) {
                bestVotes = votes;
                bestNull = otherIp;
            }
        }
        return bestNull;
    }

    /** Leading designation tokens before the major index, e.g. {@code BCD 1} → {@code BCD}. */
    private static String multiTokenDesignationPrefix(BodyInfo child) {
        String s = firstNonBlank(child.getShortName(), child.getBodyName());
        if (s == null) {
            return null;
        }
        int lastSpace = s.trim().lastIndexOf(' ');
        if (lastSpace <= 0) {
            return null;
        }
        String tail = s.trim().substring(lastSpace + 1);
        if (!tail.matches("\\d+[a-z]*")) {
            return null;
        }
        String head = s.trim().substring(0, lastSpace).trim();
        if (head.isEmpty() || head.length() == 1) {
            return null;
        }
        return head;
    }

    private static int inferPlanetBinaryNullParentId(BodyInfo child, Map<Integer, BodyInfo> bodies, int mapBodyId) {
        if (child == null || bodies == null) {
            return -1;
        }
        int ip = child.getImmediateParentBodyId();
        if (isPlanetBinaryNullParentRef(ip, bodies)) {
            if (isPlanetBinaryNullParentId(ip, bodies)) {
                return ip;
            }
            for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
                if (e.getValue() != null && e.getValue().getImmediateParentBodyId() == ip) {
                    return ip;
                }
            }
        }
        int primary = primaryAnchorBodyMapKey(bodies);
        if (ip != primary && ip != 0) {
            return -1;
        }
        Double period = child.getOrbitalPeriod();
        if (period == null || !Double.isFinite(period.doubleValue()) || period.doubleValue() <= 0.0) {
            return -1;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getKey().intValue() == mapBodyId) {
                continue;
            }
            BodyInfo other = e.getValue();
            if (isMapStellarBody(other) || isMoonSatelliteBody(other, bodies)) {
                continue;
            }
            int otherIp = other.getImmediateParentBodyId();
            if (otherIp > 0 && !bodies.containsKey(Integer.valueOf(otherIp))) {
                Double op = other.getOrbitalPeriod();
                if (op != null && Math.abs(op.doubleValue() - period.doubleValue()) < 0.01) {
                    return otherIp;
                }
            }
        }
        return -1;
    }

    private static double[] schematicMapPlaneOffsetMetresAtHintLs(BodyInfo angleSource, double hintLs, Instant now,
            int p0, int p1, boolean freezeBarycentreStars) {
        if (angleSource == null || !Double.isFinite(hintLs) || hintLs <= 0.0) {
            return new double[] { 0.0, 0.0, 0.0 };
        }
        double r = hintLs * LIGHT_SECOND_METRES;
        double[] rest = pseudoOffsetMetres(angleSource, 0, null, -1);
        double px = worldAxisMetres(rest, p0);
        double py = worldAxisMetres(rest, p1);
        double pm = Math.hypot(px, py);
        if (!(pm > 1.0)) {
            px = 1.0;
            py = 0.0;
            pm = 1.0;
        }
        double ux = px / pm;
        double uy = py / pm;
        /* Planet-binary barycentre only — always evolve (playback freeze is for wide-binary barycentric stars). */
        double M = evolvedMeanAnomalyRadians(angleSource, now != null ? now : Instant.now());
        double cosM = Math.cos(M);
        double sinM = Math.sin(M);
        double ox = (ux * cosM - uy * sinM) * r;
        double oy = (ux * sinM + uy * cosM) * r;
        double[] out = new double[] { 0.0, 0.0, 0.0 };
        out[p0] = ox;
        out[p1] = oy;
        return out;
    }

    private static void appendPlanetBinaryBarycentreRingsAtStar(List<OrbitPolylineWorldXY> out,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int starId,
            int p0,
            int p1,
            int legacyN,
            boolean useScreenChord,
            double scalePixelsPerMetre,
            int viewTiltDeg) {
        if (out == null || bodies == null || bodyWorldPositions == null || starId < 0) {
            return;
        }
        HashSet<Integer> nullParents = new HashSet<>();
        for (BodyInfo b : bodies.values()) {
            if (b == null || isMapStellarBody(b) || isMoonSatelliteBody(b, bodies)) {
                continue;
            }
            int ip = b.getImmediateParentBodyId();
            if (isPlanetBinaryNullParentRef(ip, bodies)) {
                nullParents.add(Integer.valueOf(ip));
            }
        }
        double[] starPos = bodyWorldPositions.get(Integer.valueOf(starId));
        int needLen = Math.max(p0, p1) + 1;
        if (starPos == null || starPos.length < needLen) {
            return;
        }
        for (Integer nullIdObj : nullParents) {
            int nullId = nullIdObj.intValue();
            if (!isPlanetBinaryNullParentId(nullId, bodies)) {
                continue;
            }
            if (countMapStellarBodies(bodies) >= 2) {
                int hubKey = planetBinaryBarycentreMapKey(nullId);
                int hierarchyStar = planetBinaryBarycentreHierarchyParentMapKey(hubKey, bodies);
                if (hierarchyStar != starId) {
                    continue;
                }
            }
            double distLs = planetBinaryBarycentreDistanceLsFromStar(nullId, bodies);
            if (isHierarchicalWideBinary(bodies) && Double.isFinite(distLs)
                    && distLs > HIERARCHICAL_WIDE_BINARY_SCHEMATIC_SEP_LS) {
                distLs = HIERARCHICAL_WIDE_BINARY_SCHEMATIC_SEP_LS;
            }
            if (!Double.isFinite(distLs) || distLs < 2.0) {
                continue;
            }
            double radM = distLs * LIGHT_SECOND_METRES;
            int n = legacyN;
            if (useScreenChord && Double.isFinite(scalePixelsPerMetre) && scalePixelsPerMetre > 0.0) {
                n = segmentCountForScreenChord(scalePixelsPerMetre, Math.PI * 2.0 * radM, legacyN);
            }
            n = Math.max(12, Math.min(ORBIT_POLYLINE_SEGMENTS_HARD_MAX, n));
            double[] wx = new double[n];
            double[] wy = new double[n];
            fillMapPlaneCircleVertices(wx, wy, starPos, radM, p0, p1, viewTiltDeg);
            int ringId = PLANET_BINARY_OUTER_ORBIT_RING_ID_BASE - nullId;
            out.add(new OrbitPolylineWorldXY(ringId, wx, wy));
        }
    }

    /** One mutual-orbit ring per planet-binary barycentre (members share the same curve in the generic loop). */
    private static void appendPlanetBinaryMutualOrbitRings(List<OrbitPolylineWorldXY> out,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int p0,
            int p1,
            int legacyN,
            boolean useScreenChord,
            double scalePixelsPerMetre,
            int viewTiltDeg) {
        if (out == null || bodies == null || bodyWorldPositions == null) {
            return;
        }
        HashSet<Integer> nullParents = new HashSet<>();
        for (BodyInfo b : bodies.values()) {
            if (b == null || isMoonSatelliteBody(b, bodies)) {
                continue;
            }
            int ip = b.getImmediateParentBodyId();
            if (isPlanetBinaryNullParentRef(ip, bodies)) {
                nullParents.add(Integer.valueOf(ip));
            }
        }
        int needLen = Math.max(p0, p1) + 1;
        for (Integer nullIdObj : nullParents) {
            int nullId = nullIdObj.intValue();
            if (!isSharedNullBarycentreId(nullId, bodies) && !isPlanetBinaryNullParentId(nullId, bodies)) {
                continue;
            }
            int bKey = planetBinaryBarycentreMapKey(nullId);
            double[] centerWorld = planetBinaryMutualRingCenterWorldMetres(nullId, bodies, bodyWorldPositions,
                    p0, p1, needLen);
            if (centerWorld == null) {
                continue;
            }
            double radM = planetBinaryMutualOrbitRadiusLs(nullId, bodies) * LIGHT_SECOND_METRES;
            if (!Double.isFinite(radM) || radM < MIN_FALLBACK_ORBIT_RADIUS_METRES) {
                continue;
            }
            int n = legacyN;
            if (useScreenChord && Double.isFinite(scalePixelsPerMetre) && scalePixelsPerMetre > 0.0) {
                n = segmentCountForScreenChord(scalePixelsPerMetre, Math.PI * 2.0 * radM, legacyN);
            }
            n = Math.max(12, Math.min(ORBIT_POLYLINE_SEGMENTS_HARD_MAX, n));
            double[] wx = new double[n];
            double[] wy = new double[n];
            fillMapPlaneCircleVertices(wx, wy, centerWorld, radM, p0, p1, viewTiltDeg);
            out.add(new OrbitPolylineWorldXY(PLANET_BINARY_MUTUAL_ORBIT_RING_ID_BASE - nullId, wx, wy));
        }
    }

    /** 3D centre for a planet-binary mutual guide ring (hub position, else member mean). */
    private static double[] planetBinaryMutualRingCenterWorldMetres(int nullId,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int p0,
            int p1,
            int needLen) {
        if (bodies == null || bodyWorldPositions == null) {
            return null;
        }
        int bKey = planetBinaryBarycentreMapKey(nullId);
        double[] baryPos = bodyWorldPositions.get(Integer.valueOf(bKey));
        boolean nestedPlanetPair = isNestedPlanetBinaryNullUnderOuterTrunk(nullId, bodies);
        if (nestedPlanetPair && baryPos != null && baryPos.length >= needLen) {
            return baryPos;
        }
        if (baryPos != null && baryPos.length >= needLen
                && Double.isFinite(worldAxisMetres(baryPos, p0))
                && Double.isFinite(worldAxisMetres(baryPos, p1))) {
            return baryPos;
        }
        List<Integer> memberKeys = new ArrayList<>();
        boolean outerStellarPair = isHierarchicalOuterStellarNullPair(nullId, bodies);
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            BodyInfo b = e.getValue();
            if (isMoonSatelliteBody(b, bodies) || b.getImmediateParentBodyId() != nullId) {
                continue;
            }
            if (outerStellarPair && !isStellarDirectNullMember(b, bodies)) {
                continue;
            }
            memberKeys.add(e.getKey());
        }
        if (outerStellarPair) {
            for (Integer innerId : nestedStellarInnerNullIds(nullId, bodies)) {
                memberKeys.add(Integer.valueOf(planetBinaryBarycentreMapKey(innerId.intValue())));
            }
        }
        return meanWorldMetresForBodyIds(bodyWorldPositions, memberKeys);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static int findBodyIdByDesignation(Map<Integer, BodyInfo> bodies, String designation) {
        return findBodyIdByDesignationIgnoreCase(bodies, designation, false);
    }

    private static int findBodyIdByDesignationIgnoreCase(Map<Integer, BodyInfo> bodies, String designation,
            boolean ignoreCase) {
        if (designation == null || designation.isBlank()) {
            return -1;
        }
        String d = designation.trim();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String sn = e.getValue().getShortName();
            if (sn == null) {
                continue;
            }
            sn = sn.trim();
            boolean match = ignoreCase ? sn.equalsIgnoreCase(d) : sn.equals(d);
            if (match) {
                return e.getKey().intValue();
            }
        }
        return -1;
    }

    /**
     * Like {@link #findBodyIdByDesignationIgnoreCase} but also matches full {@link BodyInfo#getBodyName()} tails
     * {@code " …B 1"} when the stored short name still includes the system prefix (cache / EDSM / name mismatch).
     */
    private static int findBodyIdByDesignationTailMatch(Map<Integer, BodyInfo> bodies, String designation,
            boolean ignoreCase) {
        if (bodies == null || designation == null || designation.isBlank()) {
            return -1;
        }
        String d = designation.trim();
        int exact = findBodyIdByDesignationIgnoreCase(bodies, d, ignoreCase);
        if (exact >= 0) {
            return exact;
        }
        String spacePlus = " " + d;
        int bestId = -1;
        int bestLen = Integer.MAX_VALUE;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String sn = firstNonBlank(e.getValue().getShortName(), e.getValue().getBodyName());
            if (sn == null || sn.isBlank()) {
                continue;
            }
            sn = sn.trim();
            if (endsWithSpaceThenDesignation(sn, spacePlus, ignoreCase) && sn.length() < bestLen) {
                bestLen = sn.length();
                bestId = e.getKey().intValue();
            }
        }
        return bestId;
    }

    /** {@code full} ends with {@code spacePlusDesignation} (e.g. {@code " Foo B 1"}) with a space boundary. */
    private static boolean endsWithSpaceThenDesignation(String full, String spacePlusDesignation,
            boolean ignoreCase) {
        if (full == null || spacePlusDesignation == null || spacePlusDesignation.length() < 2
                || !spacePlusDesignation.startsWith(" ")) {
            return false;
        }
        if (full.length() < spacePlusDesignation.length()) {
            return false;
        }
        String tail = full.substring(full.length() - spacePlusDesignation.length());
        boolean eq = ignoreCase ? tail.equalsIgnoreCase(spacePlusDesignation) : tail.equals(spacePlusDesignation);
        if (!eq) {
            return false;
        }
        int before = full.length() - spacePlusDesignation.length();
        return before == 0 || full.charAt(before - 1) == ' ';
    }

    private static boolean isStellarBody(BodyInfo b) {
        return b != null && b.getStarType() != null && !b.getStarType().isBlank();
    }

    /** Elite labels gas giants with a Sudarsky {@link BodyInfo#getStarType()} — exclude {@code … A 1} style bodies. */
    private static boolean hasPlanetStyleDesignation(BodyInfo b) {
        String s = firstNonBlank(b.getShortName(), b.getBodyName());
        if (s == null) {
            return false;
        }
        return TRAILING_STAR_BODY_DESIGNATION.matcher(s.trim()).find();
    }

    /**
     * Bodies Elite tags with {@link BodyInfo#getStarType()} for Sudarsky gas-giant classes but that still have
     * planetary atmosphere/class (e.g. c16-241 body 6). Must not use stellar Kepler rings or barycentre-star logic.
     */
    private static boolean hasJournalPlanetClass(BodyInfo b) {
        if (b == null) {
            return false;
        }
        String pc = b.getPlanetClass();
        return pc != null && !pc.isBlank();
    }

    private static boolean hasPlanetarySurfaceOrAtmosphere(BodyInfo b) {
        if (b == null) {
            return false;
        }
        if (hasJournalPlanetClass(b)) {
            return true;
        }
        if (isStellarBody(b)) {
            return false;
        }
        String atmo = firstNonBlank(b.getAtmosphere(), b.getAtmoOrType());
        return atmo != null && !atmo.isBlank();
    }

    /**
     * FSS discovery sometimes fills {@link BodyInfo#getStarType()} with Elite’s Sudarsky gas-giant label before
     * {@code PlanetClass} / atmosphere arrive — counting that world as the second stellar body trips wide-binary
     * flattening and breaks the schematic map / ship anchor (see TT-X thin partial scans).
     */
    private static boolean isSudarskyGasGiantStarTypeHold(BodyInfo b) {
        if (b == null) {
            return false;
        }
        String st = b.getStarType();
        if (st == null || st.isBlank()) {
            return false;
        }
        String sl = st.toLowerCase(Locale.ROOT);
        return sl.contains("sudarsky") || sl.contains("gas giant");
    }

    /** True stars for map/UI, not Sudarsky-class gas giants (starType + atmosphere/planet class). */
    public static boolean isMapStellarBody(BodyInfo b) {
        if (b == null || hasPlanetStyleDesignation(b)) {
            return false;
        }
        /*
         * Sudarsky / gas giant in starType alone must never count as stellar even when planet class is not synced yet.
         */
        if (isSudarskyGasGiantStarTypeHold(b)) {
            return false;
        }
        /* Primary row often keeps the system name as short name until FSS fills {@code starType}. */
        if (isPrimaryStarBodyByName(b)) {
            return true;
        }
        /*
         * Scan rows copy spectral type into {@link BodyInfo#getAtmoOrType()} for the table ({@code K}, {@code M}, …).
         * That must not disqualify the star via {@link #hasPlanetarySurfaceOrAtmosphere}.
         */
        if (isStellarBody(b) && !hasJournalPlanetClass(b)) {
            return true;
        }
        if (isSpectralClassAtmosphereOnly(b)) {
            double d = b.getDistanceLs();
            if (Double.isFinite(d) && d <= 1.0) {
                return true;
            }
        }
        if (hasPlanetarySurfaceOrAtmosphere(b)) {
            return false;
        }
        if (isStellarBody(b)) {
            return true;
        }
        /* FSS rows like short name {@code A} / {@code B} with spectral type in the table but no {@code starType} yet. */
        String sn = firstNonBlank(b.getShortName(), b.getBodyName());
        if (sn != null) {
            sn = sn.trim();
            if (sn.length() == 1) {
                char c = sn.charAt(0);
                if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                    return true;
                }
            }
        }
        return false;
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
            if (!isMapStellarBody(e.getValue())) {
                continue;
            }
            if (orbitsWideBinarySystemBarycentre(e.getValue(), bodies, e.getKey().intValue())) {
                ids.add(e.getKey());
            }
        }
        return ids;
    }

    /**
     * Stellar body orbiting the wide-binary system barycentre (parent {@code -1}), not an inner {@code ScanBaryCentre}
     * row ({@link #planetBinaryBarycentreMapKey}).
     */
    public static boolean orbitsWideBinarySystemBarycentre(BodyInfo body, Map<Integer, BodyInfo> bodies, int mapBodyId) {
        if (body == null || bodies == null) {
            return false;
        }
        int p = resolveOrbitParentBodyId(body, bodies, mapBodyId);
        return p < 0 && !isPlanetBinaryBarycentreMapKey(p);
    }

    /**
     * Shift all positions so the map-plane centroid of barycentric stars (A/B orbiting Null) is at the origin — neither
     * star sits in the middle of the view by default.
     */
    public static void recenterBinaryBarycentreInMapPlane(Map<Integer, double[]> positions,
            Map<Integer, BodyInfo> bodies,
            int mapProjA0,
            int mapProjA1) {
        if (positions == null || positions.isEmpty() || bodies == null) {
            return;
        }
        List<Integer> stars = barycentricMapStellarIds(bodies);
        if (stars.isEmpty()) {
            return;
        }
        int a0 = clampWorldAxisIndex(mapProjA0);
        int a1 = clampWorldAxisIndex(mapProjA1);
        if (a0 == a1) {
            return;
        }
        double sumX = 0.0;
        double sumY = 0.0;
        int n = 0;
        for (Integer sid : stars) {
            double[] p = positions.get(sid);
            if (p == null || p.length <= Math.max(a0, a1)) {
                continue;
            }
            double x = worldAxisMetres(p, a0);
            double y = worldAxisMetres(p, a1);
            if (Double.isFinite(x) && Double.isFinite(y)) {
                sumX += x;
                sumY += y;
                n++;
            }
        }
        if (n < 1) {
            return;
        }
        double cx = sumX / n;
        double cy = sumY / n;
        if (!Double.isFinite(cx) || !Double.isFinite(cy)) {
            return;
        }
        for (Map.Entry<Integer, double[]> e : positions.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            double[] p = e.getValue();
            if (p.length <= Math.max(a0, a1)) {
                continue;
            }
            double x = worldAxisMetres(p, a0) - cx;
            double y = worldAxisMetres(p, a1) - cy;
            double[] shifted = Arrays.copyOf(p, Math.max(3, p.length));
            shifted[a0] = x;
            shifted[a1] = y;
            positions.put(e.getKey(), shifted);
        }
    }

    /**
     * Schematic orbit radius from journal {@link BodyInfo#getDistanceLs()}: for children of a mapped parent, use
     * {@code |child − parent|} because arrival distances are measured from the drop-in star, not each orbit parent
     * (companion-branch planets otherwise stack on star {@code B} at ~3300 Ls).
     */
    static double journalOrbitRadiusLsFromParent(BodyInfo child, int parentMapId, Map<Integer, BodyInfo> bodies,
            int mapBodyId) {
        if (child == null) {
            return Double.NaN;
        }
        if (bodies != null) {
            int nullId = isPlanetBinaryBarycentreMapKey(parentMapId)
                    ? journalNullIdFromPlanetBinaryBarycentreMapKey(parentMapId)
                    : parentMapId;
            if (isPlanetBinaryNullParentId(nullId, bodies)) {
                double dChild = child.getDistanceLs();
                double dBary = planetBinaryBarycentreDistanceLsFromStar(nullId, bodies);
                if (Double.isFinite(dChild) && Double.isFinite(dBary)) {
                    return Math.max(0.5, Math.abs(dChild - dBary));
                }
            }
        }
        double dC = child.getDistanceLs();
        if (!Double.isFinite(dC)) {
            return Double.NaN;
        }
        if (parentMapId >= 0 && bodies != null) {
            BodyInfo par = bodies.get(Integer.valueOf(parentMapId));
            if (par != null) {
                double dP = par.getDistanceLs();
                if (Double.isFinite(dP)) {
                    return Math.abs(dC - dP);
                }
            }
        }
        if (parentMapId < 0 && bodies != null && isStellarBody(child)) {
            int primaryId = primaryAnchorBodyMapKey(bodies);
            if (primaryId >= 0 && mapBodyId != primaryId) {
                BodyInfo primary = bodies.get(Integer.valueOf(primaryId));
                if (primary != null) {
                    double dP = primary.getDistanceLs();
                    if (Double.isFinite(dP)) {
                        return Math.abs(dC - dP);
                    }
                }
            }
        }
        return Math.abs(dC);
    }

    /**
     * Planet-hosted moons: optional minimum on-screen orbit radius (see {@link #orbitPolylinesWorldMetresXY} moon-min flag).
     */
    private static double enforceMinMoonOrbitRadiusMetres(BodyInfo child, int parentMapId, Map<Integer, BodyInfo> bodies,
            double radiusM, double scalePixelsPerMetre) {
        if (!(radiusM > 0.0) || !Double.isFinite(radiusM) || bodies == null || child == null) {
            return radiusM;
        }
        if (!(scalePixelsPerMetre > 0.0) || !Double.isFinite(scalePixelsPerMetre)) {
            return radiusM;
        }
        if (!isMoonSatelliteBody(child, bodies) || parentMapId < 0) {
            return radiusM;
        }
        BodyInfo parent = bodies.get(Integer.valueOf(parentMapId));
        if (parent == null || isMapStellarBody(parent)) {
            return radiusM;
        }
        double minM = MIN_MOON_ORBIT_SCREEN_RADIUS_PX / scalePixelsPerMetre;
        return minM > radiusM ? minM : radiusM;
    }

    /**
     * Schematic ring radius for map strokes: journal parent-relative distance when known, else projected separation
     * from the parent's current position.
     */
    private static double[] orbitRadiusBodyPosition(Map<Integer, double[]> livePositions,
            Map<Integer, double[]> radiusReferencePositions, Integer bodyId) {
        if (radiusReferencePositions != null && bodyId != null) {
            double[] ref = radiusReferencePositions.get(bodyId);
            if (ref != null) {
                return ref;
            }
        }
        return livePositions != null && bodyId != null ? livePositions.get(bodyId) : null;
    }

    private static double[] orbitRadiusParentPosition(Map<Integer, double[]> livePositions,
            Map<Integer, double[]> radiusReferencePositions, int parentMapId, double[] liveParentPos) {
        if (parentMapId >= 0) {
            double[] ref = orbitRadiusBodyPosition(livePositions, radiusReferencePositions,
                    Integer.valueOf(parentMapId));
            if (ref != null) {
                return ref;
            }
        }
        return liveParentPos;
    }

    private static double schematicOrbitRadiusMetres(BodyInfo b, int parentMapId, Map<Integer, BodyInfo> bodies,
            int mapBodyId, double[] bodyPos, double[] parentPos, int p0, int p1) {
        double dx = worldAxisMetres(bodyPos, p0) - worldAxisMetres(parentPos, p0);
        double dy = worldAxisMetres(bodyPos, p1) - worldAxisMetres(parentPos, p1);
        double projM = Math.hypot(dx, dy);
        if (bodies != null && isSingleStarSchematicMap(bodies) && projM >= MIN_FALLBACK_ORBIT_RADIUS_METRES) {
            return projM;
        }
        double hintLs = journalOrbitRadiusLsFromParent(b, parentMapId, bodies, mapBodyId);
        if (Double.isFinite(hintLs) && hintLs > 2.0) {
            return hintLs * LIGHT_SECOND_METRES;
        }
        if (bodies != null && countMapStellarBodies(bodies) >= 2 && branchSchematicStarParentId(bodies, parentMapId) >= 0
                && Double.isFinite(hintLs) && hintLs > 0.5) {
            return hintLs * LIGHT_SECOND_METRES;
        }
        return projM;
    }

    /** Planets/moons: if Kepler radius exceeds journal parent-relative arrival distance by more than this, use pseudo. */
    private static final double KEPLER_MAX_OVER_HINT_RATIO = 1.18;
    /**
     * When projected apoapsis/periapsis span in the map plane exceeds this ratio, stroke a schematic circle instead
     * (FSS AutoScan often supplies barycentric / high-e elements that look like a spirograph on the 2D map).
     */
    private static final double KEPLER_STROKE_MAX_PROJ_SPREAD_RATIO = 1.42;
    /**
     * True-scale planets parented to a branch star: reject Kepler strokes whose centroid sits nearer the wide-binary
     * system barycentre than the resolved star (barycentric FSS elements drawn at the origin).
     */
    private static final double TRUE_SCALE_ORBIT_STROKE_BARY_CENTRE_MAX_FRAC_OF_PARENT = 0.58;
    /**
     * True-scale branch-star planets: when map-plane Kepler span exceeds this, redraw in the parent stellar frame
     * (inclination forced to the map plane) instead of a barycentric / edge-on needle.
     */
    private static final double TRUE_SCALE_BRANCH_PLANET_EDGE_ON_SPREAD_RATIO = 10.0;
    /** Reject needle-like 2D projections after parent-centred correction (edge-on + bad elements). */
    private static final double TRUE_SCALE_ORBIT_STROKE_MAX_PROJ_SPREAD_RATIO = 12.0;
    /**
     * After parent-frame correction, reject strokes whose centroid is still far from the branch star.
     */
    private static final double TRUE_SCALE_ORBIT_STROKE_MAX_PARENT_CENTRE_FRAC = 0.42;

    /**
     * Orbit strokes must match schematic body placement. Journal SMA often describes a barycentric orbit while the
     * dot uses reconciled parent-relative distance; drawing the raw Kepler ellipse yields identical huge rings that
     * dedupe away — use the circular fallback through the body instead.
     */
    private static boolean keplerOrbitPolylineMatchesSchematicPlacement(BodyInfo child, int parentMapId,
            Map<Integer, BodyInfo> bodies, int mapBodyId, double[] bodyPos, double[] parentPos, int p0, int p1) {
        if (child == null || bodyPos == null || parentPos == null) {
            return true;
        }
        Double aObj = child.getSemiMajorAxisM();
        if (aObj == null || aObj.doubleValue() <= 0 || Double.isNaN(aObj.doubleValue())) {
            return true;
        }
        if (isStellarBody(child) && parentMapId < 0) {
            return true;
        }
        double hintLs = journalOrbitRadiusLsFromParent(child, parentMapId, bodies, mapBodyId);
        if (!Double.isFinite(hintLs) || hintLs <= 2.0) {
            return true;
        }
        double hintM = hintLs * LIGHT_SECOND_METRES;
        double a = aObj.doubleValue();
        double ecc = (child.getEccentricity() != null && !Double.isNaN(child.getEccentricity()))
                ? clamp(child.getEccentricity().doubleValue(), 0, 0.999999)
                : 0.0;
        if (a > hintM * KEPLER_MAX_OVER_HINT_RATIO) {
            return false;
        }
        if (a * (1.0 + ecc) > hintM * KEPLER_MAX_OVER_HINT_RATIO) {
            return false;
        }
        if (!keplerProjectedOrbitSpanMatchesHint(child, parentPos, p0, p1, hintM)) {
            return false;
        }
        double[] rel = keplerDisplacementMetres(child, evolvedMeanAnomalyRadians(child, Instant.now()));
        if (rel == null) {
            return true;
        }
        double bx = worldAxisMetres(bodyPos, p0);
        double by = worldAxisMetres(bodyPos, p1);
        double kx = worldAxisMetres(parentPos, p0) + worldAxisMetres(rel, p0);
        double ky = worldAxisMetres(parentPos, p1) + worldAxisMetres(rel, p1);
        double missM = Math.hypot(bx - kx, by - ky);
        return missM <= hintM * 0.35;
    }

    /**
     * Sample the closed Kepler curve in the map plane; reject strokes that swing between very different radii
     * (spirograph / star polygon) while the body dot sits at one journal distance.
     */
    /** Map-plane centre of wide-binary stellar barycentre (mean of barycentric stars), or origin when unknown. */
    private static double[] wideBinarySystemBarycentreMapPlaneXY(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> positions, int p0, int p1) {
        if (bodies == null || positions == null) {
            return new double[] { 0.0, 0.0 };
        }
        double sumX = 0.0;
        double sumY = 0.0;
        int count = 0;
        int needLen = Math.max(p0, p1) + 1;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || !isMapStellarBody(e.getValue())) {
                continue;
            }
            int id = e.getKey().intValue();
            if (!orbitsWideBinarySystemBarycentre(e.getValue(), bodies, id)) {
                continue;
            }
            double[] pos = positions.get(e.getKey());
            if (pos == null || pos.length < needLen) {
                continue;
            }
            sumX += worldAxisMetres(pos, p0);
            sumY += worldAxisMetres(pos, p1);
            count++;
        }
        if (count == 0) {
            return new double[] { 0.0, 0.0 };
        }
        return new double[] { sumX / count, sumY / count };
    }

    /**
     * True-scale majors orbiting a wide-binary branch star directly (A, B, …) — use parent-relative Kepler, not
     * schematic journal-radius circles.
     */
    private static boolean isTrueScaleBranchStarPlanetStroke(BodyInfo child, int parentMapId,
            Map<Integer, BodyInfo> bodies) {
        if (child == null || bodies == null || parentMapId < 0 || isMapStellarBody(child)
                || isPlanetBinaryBarycentreMapKey(parentMapId) || isMoonSatelliteBody(child, bodies)) {
            return false;
        }
        return branchSchematicStarParentId(bodies, parentMapId) >= 0;
    }

    /**
     * Parent-relative Kepler for true-scale primary-branch planets: reconcile journal distance, then if the 2D
     * projection is edge-on (high inclination / barycentric elements), redraw in the parent stellar frame (i=0).
     */
    private static double[] trueScaleBranchPlanetKeplerDisplacementMetres(BodyInfo child, int parentMapId,
            Map<Integer, BodyInfo> bodies, int mapBodyId, double M, Instant now, int p0, int p1,
            double[] parentPos, Map<Integer, double[]> bodyWorldPositions, int viewTiltDeg) {
        Double aObj = child.getSemiMajorAxisM();
        if (aObj == null || aObj.doubleValue() <= 0 || Double.isNaN(aObj.doubleValue())) {
            return null;
        }
        double hintLs = journalOrbitRadiusLsFromParent(child, parentMapId, bodies, mapBodyId);
        double hintM = Double.isFinite(hintLs) && hintLs > 2.0 ? hintLs * LIGHT_SECOND_METRES : Double.NaN;
        /*
         * When view tilt is active, keep full 3D inclination so {@link MapViewProjection} can open edge-on orbits;
         * i=0 flattening is only for the default top-down map plane.
         */
        boolean flat = viewTiltDeg <= 0 && Double.isFinite(hintM)
                && keplerMapPlaneProjectionTooFlat(child, p0, p1, hintM);
        double[] rel = keplerDisplacementMetres(child, M, flat ? 0.0 : Double.NaN);
        if (rel == null) {
            return null;
        }
        if (Double.isFinite(hintM)) {
            double a = aObj.doubleValue();
            double e = (child.getEccentricity() != null && !Double.isNaN(child.getEccentricity()))
                    ? clamp(child.getEccentricity().doubleValue(), 0, 0.999999)
                    : 0.0;
            /*
             * Uniform semi-major scaling to journal parent-relative distance. Orbit strokes are anchored on the
             * branch star ({@code parentPos + rel}); barycentric parent-relative subtraction would inject the
             * wide-binary separation (~10⁴ Ls) into every vertex.
             */
            double apo = a * (1.0 + e);
            double peri = a * Math.max(1e-12, 1.0 - e);
            if (Math.abs(a - hintM) > hintM * 0.08
                    || apo > hintM * KEPLER_MAX_OVER_HINT_RATIO
                    || peri < hintM * 0.12) {
                double scale = hintM / a;
                rel = new double[] { rel[0] * scale, rel[1] * scale, rel[2] * scale };
            }
        }
        return rel;
    }

    /**
     * Journal Kepler offsets are usually barycentric; subtract the parent star's offset from the wide-binary
     * barycentre so {@code parentPos + rel} places the parent at the orbit focus.
     */
    private static double[] parentRelativeKeplerDisplacementMetres(double[] relBarycentric, double[] parentPos,
            Map<Integer, BodyInfo> bodies, Map<Integer, double[]> bodyWorldPositions, int p0, int p1) {
        if (relBarycentric == null || parentPos == null) {
            return relBarycentric;
        }
        int needLen = Math.max(3, Math.max(p0, p1) + 1);
        double[] out = relBarycentric.length >= needLen ? relBarycentric.clone() : new double[needLen];
        if (relBarycentric.length < needLen) {
            System.arraycopy(relBarycentric, 0, out, 0, relBarycentric.length);
        }
        double[] bary = wideBinarySystemBarycentreMapPlaneXY(bodies, bodyWorldPositions, p0, p1);
        out[p0] = worldAxisMetres(relBarycentric, p0) - (worldAxisMetres(parentPos, p0) - bary[0]);
        out[p1] = worldAxisMetres(relBarycentric, p1) - (worldAxisMetres(parentPos, p1) - bary[1]);
        int dropped = 3 - p0 - p1;
        if (needLen > 2 && relBarycentric.length > dropped && parentPos.length > dropped) {
            out[dropped] = worldAxisMetres(relBarycentric, dropped) - worldAxisMetres(parentPos, dropped);
        }
        return out;
    }

    /** True when sampled Kepler offsets are nearly edge-on in the map plane (needle / frozen stroke). */
    private static boolean keplerMapPlaneProjectionTooFlat(BodyInfo child, int p0, int p1, double hintM) {
        if (child == null || !Double.isFinite(hintM) || hintM <= 0.0) {
            return false;
        }
        double minR = Double.POSITIVE_INFINITY;
        double maxR = 0.0;
        final int samples = 16;
        for (int i = 0; i < samples; i++) {
            double M = (Math.PI * 2.0 * i) / samples;
            double[] rel = keplerDisplacementMetres(child, M);
            if (rel == null) {
                continue;
            }
            double r = Math.hypot(worldAxisMetres(rel, p0), worldAxisMetres(rel, p1));
            if (!Double.isFinite(r)) {
                continue;
            }
            maxR = Math.max(maxR, r);
            minR = Math.min(minR, r);
        }
        if (!Double.isFinite(minR) || minR >= Double.POSITIVE_INFINITY) {
            return false;
        }
        Double aObj = child.getSemiMajorAxisM();
        double refM = (aObj != null && aObj.doubleValue() > 0 && Double.isFinite(aObj.doubleValue()))
                ? aObj.doubleValue()
                : hintM;
        double floor = Math.max(1.0, refM * 0.03);
        return maxR / Math.max(minR, floor) > TRUE_SCALE_BRANCH_PLANET_EDGE_ON_SPREAD_RATIO;
    }

    /**
     * True-scale branch planets must orbit the resolved star on the map, not the system barycentre from barycentric
     * journal elements.
     */
    private static boolean trueScalePlanetKeplerStrokePlausible(BodyInfo child, int parentMapId,
            Map<Integer, BodyInfo> bodies, int mapBodyId, double[] parentPos, double[] wx, double[] wy,
            Map<Integer, double[]> bodyWorldPositions, int p0, int p1) {
        if (child == null || parentPos == null || wx == null || wy == null || wx.length < 3
                || bodies == null || parentMapId < 0 || isPlanetBinaryBarycentreMapKey(parentMapId)) {
            return true;
        }
        BodyInfo parent = bodies.get(Integer.valueOf(parentMapId));
        if (parent == null || !isMapStellarBody(parent)) {
            return true;
        }
        double hintLs = journalOrbitRadiusLsFromParent(child, parentMapId, bodies, mapBodyId);
        if (!Double.isFinite(hintLs) || hintLs <= 2.0) {
            return true;
        }
        double hintM = hintLs * LIGHT_SECOND_METRES;
        double pcx = worldAxisMetres(parentPos, p0);
        double pcy = worldAxisMetres(parentPos, p1);
        double sumCx = 0.0;
        double sumCy = 0.0;
        for (int i = 0; i < wx.length; i++) {
            sumCx += wx[i];
            sumCy += wy[i];
        }
        double cx = sumCx / wx.length;
        double cy = sumCy / wx.length;
        double minR = Double.POSITIVE_INFINITY;
        double maxR = 0.0;
        for (int i = 0; i < wx.length; i++) {
            double r = Math.hypot(wx[i] - cx, wy[i] - cy);
            minR = Math.min(minR, r);
            maxR = Math.max(maxR, r);
        }
        double spreadFloor = Math.max(1.0, hintM * 0.02);
        if (maxR / Math.max(minR, spreadFloor) > TRUE_SCALE_ORBIT_STROKE_MAX_PROJ_SPREAD_RATIO) {
            return false;
        }
        double offParent = Math.hypot(cx - pcx, cy - pcy);
        if (offParent > hintM * TRUE_SCALE_ORBIT_STROKE_MAX_PARENT_CENTRE_FRAC) {
            return false;
        }
        if (!(offParent > LIGHT_SECOND_METRES * 2.0)) {
            return true;
        }
        double[] bary = wideBinarySystemBarycentreMapPlaneXY(bodies, bodyWorldPositions, p0, p1);
        double offBary = Math.hypot(cx - bary[0], cy - bary[1]);
        return offBary >= offParent * TRUE_SCALE_ORBIT_STROKE_BARY_CENTRE_MAX_FRAC_OF_PARENT;
    }

    /**
     * Schematic branch-star concentric rings ({@code SINGLE_STAR_SCHEMATIC_RING_ID_BASE}) must never appear on
     * true-scale maps — they duplicate per-body Kepler/fallback strokes as a frozen journal-radius circle.
     */
    private static List<OrbitPolylineWorldXY> removeTrueScaleSchematicConcentricBranchRings(
            List<OrbitPolylineWorldXY> merged) {
        if (merged == null || merged.isEmpty()) {
            return merged;
        }
        List<OrbitPolylineWorldXY> kept = new ArrayList<>(merged.size());
        for (OrbitPolylineWorldXY poly : merged) {
            if (poly == null) {
                continue;
            }
            if (poly.bodyId <= -4_000 && poly.bodyId > -50_000) {
                continue;
            }
            kept.add(poly);
        }
        return kept;
    }

    /**
     * Drops per-body planet strokes anchored on the system barycentre while the model parents them to a branch star.
     */
    private static List<OrbitPolylineWorldXY> removeTrueScaleBarycentricGhostPlanetStrokes(
            List<OrbitPolylineWorldXY> merged,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            Map<Integer, Integer> resolvedParentsByBodyId,
            boolean useModelParents,
            int p0,
            int p1) {
        if (merged == null || merged.isEmpty() || bodies == null || bodyWorldPositions == null) {
            return merged;
        }
        double[] bary = wideBinarySystemBarycentreMapPlaneXY(bodies, bodyWorldPositions, p0, p1);
        List<OrbitPolylineWorldXY> kept = new ArrayList<>(merged.size());
        for (OrbitPolylineWorldXY poly : merged) {
            if (poly == null || poly.wx == null || poly.wy == null || poly.wx.length < 3
                    || poly.bodyId <= 0) {
                if (poly != null) {
                    kept.add(poly);
                }
                continue;
            }
            BodyInfo b = bodies.get(Integer.valueOf(poly.bodyId));
            if (b == null || b.isScanBarycentreRow() || isMapStellarBody(b)) {
                kept.add(poly);
                continue;
            }
            /* Eccentric Kepler strokes are parent-focused; polyline centroid ≠ star position. */
            if (!poly.estimated) {
                kept.add(poly);
                continue;
            }
            int pId;
            if (useModelParents && resolvedParentsByBodyId != null) {
                Integer rp = resolvedParentsByBodyId.get(Integer.valueOf(poly.bodyId));
                pId = rp != null ? rp.intValue() : resolveOrbitParentBodyId(b, bodies, poly.bodyId);
            } else {
                pId = resolveOrbitParentBodyId(b, bodies, poly.bodyId);
            }
            if (pId < 0 || isPlanetBinaryBarycentreMapKey(pId)) {
                kept.add(poly);
                continue;
            }
            BodyInfo parent = bodies.get(Integer.valueOf(pId));
            if (parent == null || !isMapStellarBody(parent)) {
                kept.add(poly);
                continue;
            }
            double[] parentPos = bodyWorldPositions.get(Integer.valueOf(pId));
            if (parentPos == null) {
                kept.add(poly);
                continue;
            }
            double pcx = worldAxisMetres(parentPos, p0);
            double pcy = worldAxisMetres(parentPos, p1);
            double sumCx = 0.0;
            double sumCy = 0.0;
            for (int i = 0; i < poly.wx.length; i++) {
                sumCx += poly.wx[i];
                sumCy += poly.wy[i];
            }
            double cx = sumCx / poly.wx.length;
            double cy = sumCy / poly.wy.length;
            double offParent = Math.hypot(cx - pcx, cy - pcy);
            double offBary = Math.hypot(cx - bary[0], cy - bary[1]);
            if (offParent > LIGHT_SECOND_METRES * 2.0
                    && offBary < offParent * TRUE_SCALE_ORBIT_STROKE_BARY_CENTRE_MAX_FRAC_OF_PARENT) {
                continue;
            }
            kept.add(poly);
        }
        return kept;
    }

    private static boolean keplerProjectedOrbitSpanMatchesHint(BodyInfo child, double[] parentPos, int p0, int p1,
            double hintM) {
        double cx = worldAxisMetres(parentPos, p0);
        double cy = worldAxisMetres(parentPos, p1);
        double maxR = 0.0;
        double minR = Double.POSITIVE_INFINITY;
        final int samples = 12;
        for (int i = 0; i < samples; i++) {
            double M = (Math.PI * 2.0 * i) / samples;
            double[] rel = keplerDisplacementMetres(child, M);
            if (rel == null) {
                return true;
            }
            double px = cx + worldAxisMetres(rel, p0);
            double py = cy + worldAxisMetres(rel, p1);
            double r = Math.hypot(px - cx, py - cy);
            if (!Double.isFinite(r)) {
                continue;
            }
            maxR = Math.max(maxR, r);
            minR = Math.min(minR, r);
        }
        if (!Double.isFinite(minR) || minR >= Double.POSITIVE_INFINITY) {
            return true;
        }
        if (maxR > hintM * KEPLER_MAX_OVER_HINT_RATIO) {
            return false;
        }
        if (minR < hintM * 0.40) {
            return false;
        }
        if (minR > 1.0 && maxR / minR > KEPLER_STROKE_MAX_PROJ_SPREAD_RATIO) {
            return false;
        }
        return true;
    }

    /**
     * Align Kepler offset with journal {@link BodyInfo#getDistanceLs()} relative to the orbit parent. Widens tiny
     * stellar/companion offsets; for non-stellar bodies replaces Kepler when it is far larger than arrival distance
     * (journal SMA often describes barycentric or stale elements — e.g. A 6 drawn near 3300 Ls instead of ~258 Ls).
     */
    private static double[] reconcileOrbitalDisplacementWithJournalHint(BodyInfo child, int parentMapId,
            Map<Integer, BodyInfo> bodies, int mapBodyId, double[] relKepler, Instant now) {
        if (child == null || bodies == null || relKepler == null || !isFiniteXYZ(relKepler)) {
            return relKepler;
        }
        double hintLs = journalOrbitRadiusLsFromParent(child, parentMapId, bodies, mapBodyId);
        if (!Double.isFinite(hintLs)) {
            return relKepler;
        }
        double minHintLs = (parentMapId < 0 && isStellarBody(child)) ? 500.0 : 2.0;
        if (hintLs <= minHintLs) {
            return relKepler;
        }
        double hintM = hintLs * LIGHT_SECOND_METRES;
        double mag = Math.sqrt(relKepler[0] * relKepler[0] + relKepler[1] * relKepler[1] + relKepler[2] * relKepler[2]);
        if (!isStellarBody(child) && mag > hintM * KEPLER_MAX_OVER_HINT_RATIO) {
            /* Scale current Kepler direction (varies with mean anomaly) — do not replace with circular
             * inc/node=0 placement, which stacks every body on one ray (horizontal line on the map). */
            double s = hintM / mag;
            return new double[] {
                    relKepler[0] * s,
                    relKepler[1] * s,
                    relKepler[2] * s
            };
        }
        if (mag >= hintM * 0.35) {
            return relKepler;
        }
        if (mag < LIGHT_SECOND_METRES * 2.0) {
            double[] spread = pseudoOffsetMetresAtTime(child, mapBodyId, bodies, parentMapId, now);
            double sm = Math.sqrt(spread[0] * spread[0] + spread[1] * spread[1] + spread[2] * spread[2]);
            if (sm > 1e3) {
                return spread;
            }
            return relKepler;
        }
        double scale = hintM / mag;
        return new double[] {
                relKepler[0] * scale,
                relKepler[1] * scale,
                relKepler[2] * scale
        };
    }

    /**
     * Journal-radius orbit with per-body azimuth from {@link #pseudoOffsetMetres}, advanced by mean anomaly at
     * {@code now}. Avoids stacking all bodies on one axis when inclination/node are missing in cache.
     */
    private static double[] pseudoOffsetMetresAtTime(BodyInfo b, int mapBodyId, Map<Integer, BodyInfo> bodies,
            int parentMapId, Instant now) {
        return pseudoOffsetMetresAtTime(b, mapBodyId, bodies, parentMapId, now, false);
    }

    private static double[] pseudoOffsetMetresAtTime(BodyInfo b, int mapBodyId, Map<Integer, BodyInfo> bodies,
            int parentMapId, Instant now, boolean freezeBarycentreStars) {
        double[] rest = pseudoOffsetMetres(b, mapBodyId, bodies, parentMapId);
        double pm = Math.sqrt(rest[0] * rest[0] + rest[1] * rest[1] + rest[2] * rest[2]);
        if (!(pm > 1.0) || !Double.isFinite(pm)) {
            return rest;
        }
        double ux = rest[0] / pm;
        double uy = rest[1] / pm;
        double uz = rest[2] / pm;
        double ls = journalOrbitRadiusLsFromParent(b, parentMapId, bodies, mapBodyId);
        if (Double.isNaN(ls) || ls <= 0.0) {
            ls = pm / LIGHT_SECOND_METRES;
        }
        double r = ls * LIGHT_SECOND_METRES;
        Instant t = now != null ? now : Instant.now();
        double M = freezeBarycentreStars && isBarycentreOrbitingStar(b, bodies, mapBodyId)
                ? wrapToTwoPi(angleRad(b.getMeanAnomaly()))
                : evolvedMeanAnomalyRadians(b, t);
        double cosM = Math.cos(M);
        double sinM = Math.sin(M);
        double px = ux * cosM - uy * sinM;
        double py = ux * sinM + uy * cosM;
        return new double[] { px * r, py * r, uz * r };
    }

    private static double[] widenBinaryStarKeplerIfJournalSeparationHint(BodyInfo child, int parentMapId,
            Map<Integer, BodyInfo> bodies, int mapBodyId, double[] relKepler) {
        return reconcileOrbitalDisplacementWithJournalHint(child, parentMapId, bodies, mapBodyId, relKepler,
                Instant.now());
    }

    /**
     * Parent map key from elite-style suffix {@code … A 1}, {@code … B 3 a}: moons attach to the major body;
     * majors attach to the branch star when self-match.
     */
    private static int inferParentFromBinarySystemDesignation(BodyInfo child, Map<Integer, BodyInfo> bodies,
            int mapBodyId) {
        String s = firstNonBlank(child.getShortName(), child.getBodyName());
        if (s == null) {
            return -1;
        }
        Matcher m = TRAILING_STAR_BODY_DESIGNATION.matcher(s.trim());
        if (!m.find()) {
            return -1;
        }
        String letter = m.group(1).toUpperCase(Locale.ROOT);
        String num = m.group(2);
        String moon = m.group(3);
        String hostDes = letter + " " + num;
        int host = findBodyIdByDesignationTailMatch(bodies, hostDes, true);
        if (moon != null && !moon.isEmpty()) {
            if (host >= 0 && host != mapBodyId) {
                return host;
            }
            return -1;
        }
        if (host >= 0 && host != mapBodyId) {
            return host;
        }
        int star = findBodyIdByDesignationTailMatch(bodies, letter, true);
        if (star >= 0 && star != mapBodyId) {
            return star;
        }
        return -1;
    }

    /**
     * World position of parent body {@code pId}, or {@code null} if missing from the position map.
     */
    private static double[] resolveParentWorldMetres(int pId,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions) {
        if (isPlanetBinaryBarycentreMapKey(pId)) {
            return bodyWorldPositions != null ? bodyWorldPositions.get(Integer.valueOf(pId)) : null;
        }
        if (pId < 0 || !bodies.containsKey(Integer.valueOf(pId))) {
            return null;
        }
        double[] pp = bodyWorldPositions.get(Integer.valueOf(pId));
        if (pp == null || pp.length < 3) {
            return null;
        }
        return pp;
    }

    /** Closed orbit in world metres (X/Y), same frame as {@link #bodyPositionsMetres(Map, Instant)}. */
    public static final class OrbitPolylineWorldXY {
        /** Orbiting body (never {@code 0}); used by the map to LOD-hide inner-major paths. */
        public final int bodyId;
        public final double[] wx;
        public final double[] wy;
        /** Dashed stroke when position/orbit radius was invented (missing journal elements). */
        public final boolean estimated;

        public OrbitPolylineWorldXY(int bodyId, double[] wx, double[] wy) {
            this(bodyId, wx, wy, false);
        }

        public OrbitPolylineWorldXY(int bodyId, double[] wx, double[] wy, boolean estimated) {
            this.bodyId = bodyId;
            this.wx = wx;
            this.wy = wy;
            this.estimated = estimated;
        }
    }

    /**
     * Ship position in metres: nearest body's centre plus optional surface offset from Status lat/lon/alt.
     */
    public static double[] shipPositionMetres(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> positions,
            int nearBodyId,
            Double latitudeDeg,
            Double longitudeDeg,
            Double altitudeM,
            Double planetRadiusM) {

        if (positions == null || nearBodyId < 0) {
            return null;
        }
        double[] base = positions.get(Integer.valueOf(nearBodyId));
        if (base == null) {
            return null;
        }
        BodyInfo nb = bodies.get(Integer.valueOf(nearBodyId));
        if (latitudeDeg != null && longitudeDeg != null
                && altitudeM != null && planetRadiusM != null
                && planetRadiusM.doubleValue() > 1.0
                && nb != null) {
            double[] surf = surfaceOffsetMetres(
                    latitudeDeg.doubleValue(),
                    longitudeDeg.doubleValue(),
                    altitudeM.doubleValue(),
                    planetRadiusM.doubleValue());
            return new double[] {
                    base[0] + surf[0],
                    base[1] + surf[1],
                    base[2] + surf[2]
            };
        }
        return new double[] { base[0], base[1], base[2] };
    }

    static double[] surfaceOffsetMetres(double latitudeDeg, double longitudeDeg, double altitudeM, double planetRadiusM) {
        double phi = Math.toRadians(latitudeDeg);
        double lam = Math.toRadians(longitudeDeg);
        double r = planetRadiusM + altitudeM;
        double x = r * Math.cos(phi) * Math.cos(lam);
        double y = r * Math.cos(phi) * Math.sin(lam);
        double z = r * Math.sin(phi);
        return new double[] { x, y, z };
    }

    private static boolean isWideBinaryCompanionStar(BodyInfo child, Map<Integer, BodyInfo> bodies, int mapBodyId) {
        if (!isMapStellarBody(child)) {
            return false;
        }
        if (isHierarchicalWideBinary(bodies)) {
            return false;
        }
        int ip = child.getImmediateParentBodyId();
        if (ip > 0 && isPlanetBinaryNullParentRef(ip, bodies)) {
            return false;
        }
        if (ip >= 0) {
            BodyInfo par = bodies.get(Integer.valueOf(ip));
            if (par != null && par.isScanBarycentreRow()) {
                return false;
            }
        }
        int primaryId = primaryAnchorBodyMapKey(bodies);
        if (primaryId < 0 || mapBodyId == primaryId) {
            return false;
        }
        BodyInfo primary = bodies.get(Integer.valueOf(primaryId));
        if (primary == null || !isMapStellarBody(primary)) {
            return false;
        }
        /*
         * Two-star systems always use barycentric map layout; journal/cache may parent the companion to the arrival
         * star even when separation is still below the wide-binary flatten threshold.
         */
        if (countMapStellarBodies(bodies) == 2) {
            return true;
        }
        double dC = child.getDistanceLs();
        double dP = primary.getDistanceLs();
        return Double.isFinite(dC) && Double.isFinite(dP)
                && Math.abs(dC - dP) >= WIDE_BINARY_MIN_JOURNAL_SEP_LS;
    }

    private static boolean isBarycentreOrbitingStar(BodyInfo b, Map<Integer, BodyInfo> bodies, int mapBodyId) {
        return isMapStellarBody(b) && orbitsWideBinarySystemBarycentre(b, bodies, mapBodyId);
    }

    private static double[] positionRecursive(int bodyId,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> memo,
            Set<Integer> visiting,
            Instant now,
            boolean freezeBarycentreStars,
            boolean loneStarSchematic,
            int depth,
            int maxDepth) {

        Integer key = Integer.valueOf(bodyId);
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        if (depth > maxDepth) {
            double[] z = new double[] { 0, 0, 0 };
            memo.put(key, z);
            return z;
        }
        if (visiting.contains(key)) {
            double[] z = new double[] { 0, 0, 0 };
            memo.put(key, z);
            return z;
        }

        BodyInfo b = bodies.get(key);
        if (b != null && b.isScanBarycentreRow()) {
            /*
             * Sentinel rows live at journal Null id (e.g. 49). Never pass a negative barycentre map key into
             * planetBinaryBarycentreMapKey — that function encodes null ids, so mapKey(-50049) == 49 and
             * scanRow(-50049) ↔ mapKey(-50049) recurses until stack overflow.
             */
            int journalNullId = isPlanetBinaryBarycentreMapKey(bodyId)
                    ? journalNullIdFromPlanetBinaryBarycentreMapKey(bodyId)
                    : bodyId;
            int mapKey = planetBinaryBarycentreMapKey(journalNullId);
            if (mapKey != bodyId) {
                if (memo.containsKey(Integer.valueOf(mapKey))) {
                    double[] aliased = memo.get(Integer.valueOf(mapKey));
                    memo.put(key, aliased);
                    return aliased;
                }
                visiting.add(key);
                try {
                    double[] pos = positionRecursive(mapKey, bodies, memo, visiting, now, freezeBarycentreStars,
                            loneStarSchematic, depth + 1, maxDepth);
                    memo.put(key, pos);
                    return pos;
                } finally {
                    visiting.remove(key);
                }
            }
            /* bodyId is already the virtual barycentre map key — fall through to normal placement below. */
            b = null;
        }
        if (b == null) {
            if (isPlanetBinaryBarycentreMapKey(bodyId)) {
                if (memo.containsKey(key)) {
                    return memo.get(key);
                }
                if (loneStarSchematic) {
                    int central = schematicCentralStarMapKey(bodies);
                    int nullId = journalNullIdFromPlanetBinaryBarycentreMapKey(bodyId);
                    double distLs = planetBinaryBarycentreDistanceLsFromStar(nullId, bodies);
                    BodyInfo outer = planetBinaryOuterOrbitalSource(nullId, bodies);
                    BodyInfo ref = firstPlanetBinarySibling(nullId, bodies);
                    if (central >= 0 && ref != null && Double.isFinite(distLs) && distLs > 0.0) {
                        double[] starPos = positionRecursive(central, bodies, memo, visiting, now,
                                freezeBarycentreStars, loneStarSchematic, depth + 1, maxDepth);
                        double[] rel = schematicMapPlaneOffsetMetresAtHintLs(outer != null ? outer : ref, distLs, now,
                                0, 1, freezeBarycentreStars);
                        double[] pos = new double[] {
                                starPos[0] + rel[0],
                                starPos[1] + rel[1],
                                starPos[2] + rel[2]
                        };
                        memo.put(key, pos);
                        return pos;
                    }
                }
            }
            double[] z = new double[] { 0, 0, 0 };
            memo.put(key, z);
            return z;
        }

        if (loneStarSchematic && bodyId == schematicCentralStarMapKey(bodies)) {
            double[] origin = new double[] { 0.0, 0.0, 0.0 };
            memo.put(key, origin);
            return origin;
        }

        visiting.add(key);
        try {
            int pId = resolveOrbitParentBodyId(b, bodies, bodyId);
            if (loneStarSchematic && !isMapStellarBody(b)) {
                int central = schematicCentralStarMapKey(bodies);
                if (central >= 0 && !isPlanetBinaryBarycentreMapKey(pId)
                        && (pId < 0 || !bodies.containsKey(Integer.valueOf(pId)))) {
                    pId = central;
                }
            }
            if (pId == bodyId) {
                pId = -1;
            }
            double[] parentPos;
            if (isPlanetBinaryBarycentreMapKey(pId)) {
                parentPos = positionRecursive(pId, bodies, memo, visiting, now, freezeBarycentreStars,
                        loneStarSchematic, depth + 1, maxDepth);
            } else if (pId < 0 || !bodies.containsKey(Integer.valueOf(pId))) {
                parentPos = new double[] { 0, 0, 0 };
            } else {
                parentPos = positionRecursive(pId, bodies, memo, visiting, now, freezeBarycentreStars,
                        loneStarSchematic, depth + 1, maxDepth);
            }
            if (!isFiniteXYZ(parentPos)) {
                parentPos = new double[] { 0.0, 0.0, 0.0 };
            }

            double[] rel = orbitalDisplacementMetres(b, bodyId, now, bodies, freezeBarycentreStars);
            if (!isFiniteXYZ(rel)) {
                rel = pseudoOffsetMetresAtTime(b, bodyId, bodies, pId, now, freezeBarycentreStars);
            } else if (isMapStellarBody(b)) {
                rel = reconcileOrbitalDisplacementWithJournalHint(b, pId, bodies, bodyId, rel, now);
            }
            double[] out = new double[] {
                    parentPos[0] + rel[0],
                    parentPos[1] + rel[1],
                    parentPos[2] + rel[2]
            };
            if (!isFiniteXYZ(out)) {
                rel = pseudoOffsetMetres(b, bodyId, bodies, pId);
                out = new double[] {
                        parentPos[0] + rel[0],
                        parentPos[1] + rel[1],
                        parentPos[2] + rel[2]
                };
            }
            memo.put(key, out);
            return out;
        } finally {
            visiting.remove(key);
        }
    }

    static double[] orbitalDisplacementMetres(BodyInfo b, int mapBodyId, Instant now,
            Map<Integer, BodyInfo> bodies) {
        return orbitalDisplacementMetres(b, mapBodyId, now, bodies, false);
    }

    static double[] orbitalDisplacementMetres(BodyInfo b, int mapBodyId, Instant now,
            Map<Integer, BodyInfo> bodies, boolean freezeBarycentreStars) {
        int pId = bodies != null ? resolveOrbitParentBodyId(b, bodies, mapBodyId) : -1;
        /*
         * Schematic map motion: planets/moons orbit in the X/Y plane at journal distance. Journal Kepler elements are
         * often barycentric or high-inclination — 3D Kepler barely moves in the 2D projection (looks frozen) while a
         * mis-tagged gas giant can still get a spiky Kepler stroke.
         */
        if (bodies != null && shouldApplyLoneStarSchematicLayout(bodies) && !isMapStellarBody(b)) {
            int central = schematicCentralStarMapKey(bodies);
            if (central >= 0 && pId == central) {
                return pseudoOffsetMetresAtTime(b, mapBodyId, bodies, central, now, freezeBarycentreStars);
            }
            if (isPlanetBinaryBarycentreMapKey(pId)) {
                int nullId = journalNullIdFromPlanetBinaryBarycentreMapKey(pId);
                double[] starPos = central >= 0 ? new double[] { 0.0, 0.0, 0.0 } : null;
                return planetBinaryOffsetFromBarycentreMetres(b, mapBodyId, bodies, nullId, now, 0, 1,
                        freezeBarycentreStars, starPos, null);
            }
        }
        Double aObj = b.getSemiMajorAxisM();
        if (aObj != null && aObj.doubleValue() > 0 && !Double.isNaN(aObj.doubleValue())) {
            double M = freezeBarycentreStars && isBarycentreOrbitingStar(b, bodies, mapBodyId)
                    ? wrapToTwoPi(angleRad(b.getMeanAnomaly()))
                    : evolvedMeanAnomalyRadians(b, now);
            double[] k = keplerDisplacementMetres(b, M);
            if (k != null && isFiniteXYZ(k)) {
                return k;
            }
        }

        return pseudoOffsetMetres(b, mapBodyId, bodies, pId);
    }

    /**
     * Parent-relative displacement from Keplerian elements at mean anomaly {@code M} (radians).
     * @return {@code null} if semi-major axis is missing or invalid
     */
    static double[] keplerDisplacementMetres(BodyInfo b, double M) {
        return keplerDisplacementMetres(b, M, Double.NaN);
    }

    /**
     * Parent-relative Kepler displacement; {@code inclinationOverrideRad} replaces journal inclination when finite
     * (true-scale map strokes around a branch star use i=0 when the 3D orbit is edge-on in the map plane).
     */
    static double[] keplerDisplacementMetres(BodyInfo b, double M, double inclinationOverrideRad) {
        Double aObj = b.getSemiMajorAxisM();
        if (aObj == null || aObj.doubleValue() <= 0 || Double.isNaN(aObj.doubleValue())) {
            return null;
        }
        double a = aObj.doubleValue();
        double e = (b.getEccentricity() != null && !Double.isNaN(b.getEccentricity()))
                ? clamp(b.getEccentricity().doubleValue(), 0, 0.999999)
                : 0;

        double inc = Double.isFinite(inclinationOverrideRad)
                ? inclinationOverrideRad
                : angleRad(b.getOrbitalInclination());
        double om = angleRad(b.getAscendingNode());
        double wp = angleRad(b.getPeriapsis());
        double Mw = wrapToTwoPi(M);

        double E = solveKepler(Mw, e);
        double cosE = Math.cos(E);
        double sinE = Math.sin(E);
        double sqrtTerm = Math.sqrt(Math.max(0, (1 + e) / Math.max(1e-12, (1 - e))));
        double nu = 2 * Math.atan2(sqrtTerm * sinE, cosE - e);

        double r = a * (1 - e * cosE);

        double u = wp + nu;
        double cosOm = Math.cos(om);
        double sinOm = Math.sin(om);
        double cosI = Math.cos(inc);
        double sinI = Math.sin(inc);
        double cosU = Math.cos(u);
        double sinU = Math.sin(u);

        double x = r * (cosU * cosOm - sinU * sinOm * cosI);
        double y = r * (cosU * sinOm + sinU * cosOm * cosI);
        double z = r * (sinU * sinI);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return null;
        }
        return new double[] { x, y, z };
    }

    /**
     * Mean anomaly at {@code now}: {@code M = M0 + n Δt} with {@code n = 2π/P}. Uses journal period/epoch when
     * present; otherwise estimates {@code P} from semi-major axis (Kepler) and a schematic epoch so schematic
     * playback still advances bodies that only have EDSM/cache elements.
     */
    static double evolvedMeanAnomalyRadians(BodyInfo b, Instant now) {
        double M0 = angleRad(b.getMeanAnomaly());
        Instant t = now != null ? now : Instant.now();
        double pSec = orbitalPeriodSecondsForEvolution(b);
        if (!(pSec > 1e-6) || !Double.isFinite(pSec)) {
            return wrapToTwoPi(M0);
        }
        long epochMs = orbitalEpochMillisForEvolution(b);
        double dtSec = (t.toEpochMilli() - epochMs) / 1000.0;
        double n = (Math.PI * 2.0) / pSec;
        return wrapToTwoPi(M0 + n * dtSec);
    }

    private static double orbitalPeriodSecondsForEvolution(BodyInfo b) {
        Double p = b.getOrbitalPeriod();
        if (p != null && Double.isFinite(p.doubleValue()) && p.doubleValue() > 1e-6) {
            return p.doubleValue();
        }
        Double a = b.getSemiMajorAxisM();
        if (a != null && a.doubleValue() > 0.0 && Double.isFinite(a.doubleValue())) {
            return keplerOrbitalPeriodSecondsFromSemiMajorAxis(a.doubleValue());
        }
        double distLs = b.getDistanceLs();
        if (distLs > 0.0 && Double.isFinite(distLs)) {
            return keplerOrbitalPeriodSecondsFromSemiMajorAxis(distLs * LIGHT_SECOND_METRES);
        }
        return 86400.0;
    }

    private static double keplerOrbitalPeriodSecondsFromSemiMajorAxis(double aM) {
        return (Math.PI * 2.0) * Math.sqrt((aM * aM * aM) / STANDARD_GRAVITATIONAL_PARAMETER_SUN_M3_S2);
    }

    private static long orbitalEpochMillisForEvolution(BodyInfo b) {
        Long e = b.getOrbitalEpochMillis();
        if (e != null && e.longValue() > 0L) {
            return e.longValue();
        }
        return SCHEMATIC_ORBIT_EPOCH_MILLIS;
    }

    static double wrapToTwoPi(double rad) {
        if (!Double.isFinite(rad)) {
            return 0.0;
        }
        double twoPi = Math.PI * 2.0;
        double x = rad % twoPi;
        if (x < 0) {
            x += twoPi;
        }
        return x;
    }

    /**
     * Rough distance from the schematic primary (Ls): sum of this body's and ancestors' {@code SemiMajorAxis}
     * (journal metres) along {@link #resolveOrbitParentBodyId(BodyInfo, Map, int)} until the primary anchor is
     * reached. Used when journal arrival distance is missing so schematic positions are not all degenerate at the
     * primary.
     */
    public static double cumulativeSemiMajorAxisChainLs(Map<Integer, BodyInfo> bodies, int mapBodyId) {
        if (bodies == null || bodies.isEmpty() || mapBodyId < 0) {
            return Double.NaN;
        }
        int primary = primaryAnchorBodyMapKey(bodies);
        if (mapBodyId == primary) {
            return 0.0;
        }
        double sumLs = 0.0;
        int cur = mapBodyId;
        for (int hop = 0; hop < 64 && cur >= 0; hop++) {
            if (cur == primary) {
                break;
            }
            BodyInfo bi = bodies.get(Integer.valueOf(cur));
            if (bi == null) {
                break;
            }
            Double aObj = bi.getSemiMajorAxisM();
            if (aObj != null) {
                double a = aObj.doubleValue();
                if (a > 0.0 && Double.isFinite(a)) {
                    sumLs += a / LIGHT_SECOND_METRES;
                }
            }
            int p = resolveOrbitParentBodyId(bi, bodies, cur);
            if (p < 0 || p == cur) {
                break;
            }
            cur = p;
        }
        return sumLs > 0.0 ? sumLs : Double.NaN;
    }

    /**
     * Fallback when Keplerian offset is unavailable: stable pseudo-vector. Prefers parent-relative journal radius
     * ({@link #journalOrbitRadiusLsFromParent}), else semi-major axis, else {@link #cumulativeSemiMajorAxisChainLs}.
     */
    static double[] pseudoOffsetMetres(BodyInfo b, int mapBodyId, Map<Integer, BodyInfo> bodies) {
        int pId = bodies != null ? resolveOrbitParentBodyId(b, bodies, mapBodyId) : -1;
        return pseudoOffsetMetres(b, mapBodyId, bodies, pId);
    }

    static double[] pseudoOffsetMetres(BodyInfo b, int mapBodyId, Map<Integer, BodyInfo> bodies, int parentMapId) {
        double ls = journalOrbitRadiusLsFromParent(b, parentMapId, bodies, mapBodyId);
        if (Double.isNaN(ls) || ls <= 0.0) {
            ls = b.getDistanceLs();
        }
        if (Double.isNaN(ls) || ls <= 0.0) {
            Double aObj = b.getSemiMajorAxisM();
            if (aObj != null) {
                double a = aObj.doubleValue();
                if (a > 0.0 && Double.isFinite(a)) {
                    ls = a / LIGHT_SECOND_METRES;
                }
            }
            if ((Double.isNaN(ls) || ls <= 0.0) && bodies != null && !bodies.isEmpty()) {
                double cum = cumulativeSemiMajorAxisChainLs(bodies, mapBodyId);
                if (Double.isFinite(cum) && cum > 0.0) {
                    ls = cum;
                }
            }
        }
        if (Double.isNaN(ls) || ls < 0.0) {
            ls = 0.0;
        }
        if (ls <= 0.0 && bodies != null && !bodies.isEmpty()) {
            int primary = primaryAnchorBodyMapKey(bodies);
            if (mapBodyId != primary) {
                // FSS / EDSM-only rows often lack DistanceFromArrivalLS and SemiMajorAxis; Kepler offset can still be
                // degenerate. A deterministic schematic radius keeps inner bodies separable for map + ship-centric Ls.
                int h = mapBodyId;
                String nm = firstNonBlank(b != null ? b.getShortName() : null, b != null ? b.getBodyName() : null);
                if (nm != null && !nm.isBlank()) {
                    String s = nm.trim();
                    for (int i = 0; i < s.length(); i++) {
                        h = h * 31 + s.charAt(i);
                    }
                }
                int hh = h == Integer.MIN_VALUE ? 1 : Math.abs(h);
                double u = (hh % 10001) / 10001.0;
                ls = 2.0 + 4200.0 * Math.pow(u, 2.15);
            }
        }
        double r = ls * LIGHT_SECOND_METRES;
        int id = mapBodyId >= 0 ? mapBodyId : 0;
        double u = id * 2.39996322972865332;
        double v = id * 1.337053072;
        double sinU = Math.sin(u);
        double x = r * sinU * Math.cos(v);
        double y = r * sinU * Math.sin(v);
        double z = r * Math.cos(u);
        return new double[] { x, y, z };
    }

    /**
     * Journal angles: values with magnitude above ~2π are treated as degrees (works for periapsis ~300°); smaller
     * values are treated as radians (typical inclinations).
     */
    static double angleRad(Double raw) {
        if (raw == null || Double.isNaN(raw.doubleValue())) {
            return 0;
        }
        double v = raw.doubleValue();
        if (!Double.isFinite(v)) {
            return 0;
        }
        if (Math.abs(v) > Math.PI * 2 + 0.02) {
            double deg = Math.toRadians(v);
            return Double.isFinite(deg) ? deg : 0;
        }
        return v;
    }

    static double solveKepler(double M, double e) {
        if (e < 1e-12) {
            return normAngle(M);
        }
        double E = normAngle(M);
        for (int i = 0; i < 30; i++) {
            double f = E - e * Math.sin(E) - M;
            double fp = 1 - e * Math.cos(E);
            if (Math.abs(fp) < 1e-12) {
                break;
            }
            double step = f / fp;
            E -= step;
            if (Math.abs(step) < 1e-10) {
                break;
            }
        }
        return E;
    }

    private static boolean isFiniteXYZ(double[] v) {
        return v != null && v.length >= 3
                && Double.isFinite(v[0]) && Double.isFinite(v[1]) && Double.isFinite(v[2]);
    }

    static double normAngle(double rad) {
        if (!Double.isFinite(rad)) {
            return 0.0;
        }
        double twoPi = Math.PI * 2;
        double x = rad % twoPi;
        if (x < -Math.PI) {
            x += twoPi;
        } else if (x > Math.PI) {
            x -= twoPi;
        }
        return x;
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** World metres along axis {@code axis} (0=x, 1=y, 2=z); missing coordinate → {@code 0}. */
    public static double worldAxisMetres(double[] v, int axis) {
        if (v == null) {
            return 0.0;
        }
        int a = clampWorldAxisIndex(axis);
        if (a >= v.length) {
            return 0.0;
        }
        double t = v[a];
        return Double.isFinite(t) ? t : 0.0;
    }

    public static int clampWorldAxisIndex(int axis) {
        if (axis <= 0) {
            return 0;
        }
        if (axis >= 2) {
            return 2;
        }
        return axis;
    }

    /** Adds a map-plane relative offset ({@code rel} only non-zero on {@code p0}/{@code p1}) to {@code parentPos}. */
    private static double[] combineParentAndRelativeOffset(double[] parentPos, double[] rel, int p0, int p1) {
        if (parentPos == null || rel == null) {
            return rel != null ? rel : parentPos;
        }
        int maxAxis = Math.max(2, Math.max(p0, p1));
        int need = Math.max(3, Math.max(parentPos.length, rel.length));
        need = Math.max(need, maxAxis + 1);
        double[] out = new double[need];
        for (int i = 0; i < parentPos.length && i < out.length; i++) {
            out[i] = worldAxisMetres(parentPos, i);
        }
        out[p0] = worldAxisMetres(parentPos, p0) + worldAxisMetres(rel, p0);
        out[p1] = worldAxisMetres(parentPos, p1) + worldAxisMetres(rel, p1);
        return out;
    }

    /** Wide binaries: journal A–B separation often lies mostly on the axis dropped by the 2D map projection. */
    private static final double WIDE_BINARY_MIN_JOURNAL_SEP_LS = 500.0;
    private static final double WIDE_BINARY_PROJ_SEP_MIN_FRAC_OF_JOURNAL = 0.95;
    /**
     * Four-star hierarchies (A vs BCD cluster): heliocentric companion distance is ~50k Ls but the schematic map uses a
     * second trunk near the primary like a normal wide binary (~3–8k Ls), with B/C/D grouped on the companion side.
     */
    private static final double HIERARCHICAL_WIDE_BINARY_SCHEMATIC_SEP_LS = 7500.0;

    /**
     * True when only the arrival star orbits the system barycentre and other stars sit under {@code ScanBaryCentre} rows
     * (e.g. Eor Aowsy: A vs B+C at Null:3 under Null:2).
     */
    public static boolean isHierarchicalWideBinary(Map<Integer, BodyInfo> bodies) {
        if (bodies == null) {
            return false;
        }
        /*
         * Journal-only: count companion-cluster stars parented to ScanBaryCentre / Null rows (B+C at Null:3, D at
         * Null:2). Must not call {@link #resolveOrbitParentBodyId} — that path consults this method and would recurse.
         */
        int innerClusterStars = 0;
        int scanBaryRows = 0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            BodyInfo b = e.getValue();
            if (b == null) {
                continue;
            }
            if (b.isScanBarycentreRow()) {
                scanBaryRows++;
                continue;
            }
            if (!isMapStellarBody(b)) {
                continue;
            }
            int ip = b.getImmediateParentBodyId();
            if (ip > 0 && isPlanetBinaryNullParentRef(ip, bodies)) {
                innerClusterStars++;
                continue;
            }
            if (ip >= 0) {
                BodyInfo par = bodies.get(Integer.valueOf(ip));
                if (par != null && par.isScanBarycentreRow()) {
                    innerClusterStars++;
                }
            }
        }
        if (innerClusterStars >= 2) {
            return true;
        }
        /* Four-star A+BCD: ScanBaryCentre rows remain even when cache parents B/C/D to the arrival star. */
        if (countMapStellarBodies(bodies) >= 4 && scanBaryRows >= 2) {
            return true;
        }
        /*
         * Live cache/EDSM may attach planet class to B/C/D so only A counts as a map star; still a hierarchical
         * companion cluster when ScanBaryCentre rows and branch stars sit at ~50k Ls heliocentric distance.
         */
        if (scanBaryRows >= 2 && hasHierarchicalCompanionBranchMarkers(bodies)) {
            return true;
        }
        /* Triple (A vs B+C): one inner ScanBaryCentre row and two distant companion branch stars. */
        if (scanBaryRows >= 1 && countNonPrimaryHierarchicalBranchStars(bodies) == 2
                && hasHierarchicalCompanionBranchMarkers(bodies)) {
            return true;
        }
        /*
         * Four-star A+BCD when cache drops ScanBaryCentre rows but B/C/D branch stars remain at ~50k Ls — must still
         * use hierarchical trunk layout, not a heliocentric wide-binary ring around A only.
         */
        return countNonPrimaryHierarchicalBranchStars(bodies) >= 3
                && hasHierarchicalCompanionBranchMarkers(bodies);
    }

    /**
     * Three map stars with one {@code ScanBaryCentre} stellar pair (e.g. Eol Prou OR-V d2-399: A vs B+C at Null:2) —
     * not the four-star A vs BCD hierarchy that uses {@link #appendHierarchicalSystemBarycentreRing}.
     */
    public static boolean isHierarchicalTripleStarMap(Map<Integer, BodyInfo> bodies) {
        if (bodies == null || !isHierarchicalWideBinary(bodies)) {
            return false;
        }
        int tripleNull = hierarchicalTripleStellarNullId(bodies);
        if (tripleNull < 0 || countTripleInnerStellarPairMembers(tripleNull, bodies) < 2) {
            return false;
        }
        if (primaryAnchorBodyMapKey(bodies) < 0) {
            return false;
        }
        if (countNonPrimaryHierarchicalBranchStars(bodies) != 2) {
            return false;
        }
        /*
         * Four-star A+BCD only: extra ScanBaryCentre rows (e.g. Null:49) must not demote a triple-star journal that
         * happens to list multiple barycentre rows while still having only B+C branch stars.
         */
        if (countNonPrimaryHierarchicalBranchStars(bodies) >= 3) {
            for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || !e.getValue().isScanBarycentreRow()) {
                    continue;
                }
                int nullId = e.getKey().intValue();
                if (nullId != tripleNull && isHierarchicalOuterStellarNullPair(nullId, bodies)) {
                    return false;
                }
            }
        }
        return hierarchicalCompanionBranchStarsCohesive(bodies);
    }

    /**
     * True when non-A branch stars sit at similar heliocentric distance (B+C at Null:N). False when one companion is
     * clearly outer (e.g. EOL PROU LH-U D3-2700: B ~1k Ls, C ~11k Ls) — those maps are hierarchical, not triple.
     */
    private static boolean hierarchicalCompanionBranchStarsCohesive(Map<Integer, BodyInfo> bodies) {
        if (bodies == null) {
            return false;
        }
        int primaryId = primaryAnchorBodyMapKey(bodies);
        if (primaryId < 0) {
            return false;
        }
        BodyInfo primary = bodies.get(Integer.valueOf(primaryId));
        double dP = primary != null ? primary.getDistanceLs() : 0.0;
        List<Double> helioLs = new ArrayList<>();
        Integer sharedNullParent = null;
        boolean mixedNullParents = false;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isScanBarycentreRow()) {
                continue;
            }
            BodyInfo b = e.getValue();
            String letter = stellarBranchLetter(b);
            if (letter == null || "A".equals(letter)) {
                if (!isMapStellarBody(b) && !isStellarBody(b)) {
                    String sn = b.getShortName();
                    if (sn == null || sn.isBlank()) {
                        sn = b.getBodyName();
                    }
                    if (sn == null) {
                        continue;
                    }
                    sn = sn.trim();
                    if (sn.length() != 1) {
                        continue;
                    }
                    char c = Character.toUpperCase(sn.charAt(0));
                    if (c < 'B' || c > 'Z') {
                        continue;
                    }
                } else {
                    continue;
                }
            }
            if (!isMapStellarBody(b) && !isStellarBody(b)) {
                continue;
            }
            double d = b.getDistanceLs();
            if (Double.isFinite(d)) {
                helioLs.add(Double.valueOf(Math.abs(d - dP)));
            }
            int ip = b.getImmediateParentBodyId();
            if (ip > 0) {
                BodyInfo par = bodies.get(Integer.valueOf(ip));
                if (par != null && par.isScanBarycentreRow()) {
                    if (sharedNullParent == null) {
                        sharedNullParent = Integer.valueOf(ip);
                    } else if (sharedNullParent.intValue() != ip) {
                        mixedNullParents = true;
                    }
                }
            }
        }
        if (helioLs.size() < 2) {
            return true;
        }
        helioLs.sort(Double::compareTo);
        double near = helioLs.get(0).doubleValue();
        double far = helioLs.get(helioLs.size() - 1).doubleValue();
        if (far < 5_000.0) {
            return true;
        }
        if (near >= far * 0.4) {
            return true;
        }
        return !mixedNullParents && sharedNullParent != null
                && countStellarDirectNullMembers(sharedNullParent.intValue(), bodies) >= 2;
    }

    /** B+C at the inner {@code ScanBaryCentre} and its synthetic map key — not the whole non-A subtree. */
    private static boolean isHierarchicalTripleCompanionShiftBody(int bodyId, int tripleNullId,
            Map<Integer, BodyInfo> bodies) {
        if (bodies == null || tripleNullId < 0 || bodyId < 0) {
            return false;
        }
        if (bodyId == planetBinaryBarycentreMapKey(tripleNullId)) {
            return true;
        }
        BodyInfo b = bodies.get(Integer.valueOf(bodyId));
        if (b == null) {
            return false;
        }
        if (b.getImmediateParentBodyId() == tripleNullId && isStellarDirectNullMember(b, bodies)) {
            return true;
        }
        /*
         * Live cache may parent B/C to the arrival star while journal has Null:2 — still move the inner pair with the
         * schematic hub (same as {@link #isStellarDirectNullMember} fallback used for Eor Aowsy B+C).
         */
        if (!isStellarDirectNullMember(b, bodies)) {
            return false;
        }
        String letter = stellarBranchLetter(b);
        if (letter == null || "A".equals(letter)) {
            return false;
        }
        return bodyId != primaryAnchorBodyMapKey(bodies);
    }

    /**
     * B+C mutual-orbit radius on the schematic map — journal heliocentric spread is ~1.3k Ls but the map uses a
     * tight circle beside the A trunk (~{@link #HIERARCHICAL_INNER_STELLAR_PAIR_SCHEMATIC_MIN_LS} scale).
     */
    private static double hierarchicalTripleStellarPairSchematicOrbitRadiusLs(int journalNullParentId,
            Map<Integer, BodyInfo> bodies) {
        double minD = Double.POSITIVE_INFINITY;
        double maxD = Double.NEGATIVE_INFINITY;
        for (BodyInfo b : bodies.values()) {
            if (b == null || !isStellarDirectNullMember(b, bodies)) {
                continue;
            }
            if (b.getImmediateParentBodyId() != journalNullParentId) {
                String letter = stellarBranchLetter(b);
                if (letter == null || "A".equals(letter)) {
                    continue;
                }
            }
            double d = b.getDistanceLs();
            if (!Double.isFinite(d)) {
                continue;
            }
            minD = Math.min(minD, d);
            maxD = Math.max(maxD, d);
        }
        double spreadLs = Double.isFinite(minD) && Double.isFinite(maxD) ? (maxD - minD) * 0.5 : Double.NaN;
        double capLs = HIERARCHICAL_INNER_STELLAR_PAIR_SCHEMATIC_MIN_LS * 0.5;
        if (!Double.isFinite(spreadLs)) {
            return Math.max(STELLAR_SHARED_NULL_MUTUAL_ORBIT_MIN_LS, capLs);
        }
        return Math.max(STELLAR_SHARED_NULL_MUTUAL_ORBIT_MIN_LS, Math.min(spreadLs, capLs));
    }

    /** Non-A single-letter branch stars (B+C in a triple, B+C+D in four-star). */
    private static int countNonPrimaryHierarchicalBranchStars(Map<Integer, BodyInfo> bodies) {
        int n = 0;
        for (BodyInfo b : bodies.values()) {
            if (b == null || b.isScanBarycentreRow()) {
                continue;
            }
            if (isMapStellarBody(b) || isStellarBody(b)) {
                String letter = stellarBranchLetter(b);
                if (letter != null && !"A".equals(letter)) {
                    n++;
                }
                continue;
            }
            String sn = b.getShortName();
            if (sn == null || sn.isBlank()) {
                sn = b.getBodyName();
            }
            if (sn == null) {
                continue;
            }
            sn = sn.trim();
            if (sn.length() == 1) {
                char c = Character.toUpperCase(sn.charAt(0));
                if (c >= 'B' && c <= 'Z') {
                    n++;
                }
            }
        }
        return n;
    }

    /** Stellar inner pair at a {@code ScanBaryCentre} — journal-parented or cache-parented to the arrival star. */
    private static int countTripleInnerStellarPairMembers(int tripleNullId, Map<Integer, BodyInfo> bodies) {
        if (tripleNullId <= 0 || bodies == null) {
            return 0;
        }
        int atNull = countStellarDirectNullMembers(tripleNullId, bodies);
        if (atNull >= 2) {
            return atNull;
        }
        if (!isTripleInnerStellarScanNull(tripleNullId, bodies)) {
            return atNull;
        }
        if (!hierarchicalCompanionBranchStarsCohesive(bodies)) {
            return atNull;
        }
        return countNonPrimaryHierarchicalBranchStars(bodies);
    }

    private static boolean isTripleInnerStellarScanNull(int nullId, Map<Integer, BodyInfo> bodies) {
        if (countNonPrimaryHierarchicalBranchStars(bodies) != 2) {
            return false;
        }
        if (!hierarchicalCompanionBranchStarsCohesive(bodies)) {
            return false;
        }
        BodyInfo scanRow = bodies != null ? bodies.get(Integer.valueOf(nullId)) : null;
        if (scanRow == null || !scanRow.isScanBarycentreRow()) {
            return false;
        }
        /*
         * Triple-star journals often include extra planet-binary ScanBaryCentre rows; only four-star A+BCD uses the
         * outer-stellar-pair exclusion on the B+C null.
         */
        if (countNonPrimaryHierarchicalBranchStars(bodies) >= 3
                && isHierarchicalOuterStellarNullPair(nullId, bodies)) {
            return false;
        }
        if (countStellarDirectNullMembers(nullId, bodies) >= 2) {
            return true;
        }
        int planetsAtNull = 0;
        for (BodyInfo b : bodies.values()) {
            if (b == null || b.isScanBarycentreRow() || b.getImmediateParentBodyId() != nullId) {
                continue;
            }
            if (isStellarDirectNullMember(b, bodies)) {
                continue;
            }
            planetsAtNull++;
        }
        return planetsAtNull == 0;
    }

    /** Journal Null id hosting the inner stellar pair (B+C) in a triple-star map. */
    public static int hierarchicalTripleStellarNullId(Map<Integer, BodyInfo> bodies) {
        if (bodies == null) {
            return -1;
        }
        int fallback = -1;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || !e.getValue().isScanBarycentreRow()) {
                continue;
            }
            int nullId = e.getKey().intValue();
            if (!isTripleInnerStellarScanNull(nullId, bodies)) {
                continue;
            }
            if (countStellarDirectNullMembers(nullId, bodies) >= 2) {
                return nullId;
            }
            if (fallback < 0) {
                fallback = nullId;
            }
        }
        return fallback;
    }

    /**
     * Companion-side branch stars (B/C/D) far from the arrival star — used when {@link #isMapStellarBody} under-counts
     * due to synced planet class/atmosphere on true stars.
     */
    private static boolean hasHierarchicalCompanionBranchMarkers(Map<Integer, BodyInfo> bodies) {
        int primaryId = primaryAnchorBodyMapKey(bodies);
        if (primaryId < 0) {
            return false;
        }
        BodyInfo primary = bodies.get(Integer.valueOf(primaryId));
        double dP = primary != null ? primary.getDistanceLs() : 0.0;
        int markers = 0;
        for (BodyInfo b : bodies.values()) {
            if (b == null || b.isScanBarycentreRow()) {
                continue;
            }
            String letter = stellarBranchLetter(b);
            if (letter == null || "A".equals(letter)) {
                continue;
            }
            if (!isStellarBody(b) && !isMapStellarBody(b)) {
                String sn = b.getShortName();
                if (sn == null || sn.isBlank()) {
                    sn = b.getBodyName();
                }
                if (sn == null) {
                    continue;
                }
                sn = sn.trim();
                if (sn.length() != 1) {
                    continue;
                }
                char c = Character.toUpperCase(sn.charAt(0));
                if (c < 'B' || c > 'Z') {
                    continue;
                }
            }
            double d = b.getDistanceLs();
            if (Double.isFinite(d) && Double.isFinite(dP) && Math.abs(d - dP) > 5_000.0) {
                markers++;
            }
        }
        return markers >= 1;
    }

    /**
     * When a hierarchical companion star is wrongly parented to the arrival star, map it back to the inner
     * {@code ScanBaryCentre} null id (e.g. B+C at Null:3) using siblings or scan rows still in the cache.
     */
    private static int inferInnerClusterNullForHierarchicalStar(BodyInfo child, Map<Integer, BodyInfo> bodies,
            int mapBodyId) {
        if (child == null || bodies == null || !isStellarDirectNullMember(child, bodies)) {
            return -1;
        }
        int ip = child.getImmediateParentBodyId();
        if (ip > 0 && isPlanetBinaryNullParentRef(ip, bodies)) {
            return ip;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int nullId = e.getKey().intValue();
            if (!isSharedNullBarycentreId(nullId, bodies)) {
                continue;
            }
            int stellarAtNull = 0;
            for (BodyInfo other : bodies.values()) {
                if (other == null || !isMapStellarBody(other) || other.getImmediateParentBodyId() != nullId) {
                    continue;
                }
                stellarAtNull++;
            }
            if (stellarAtNull < 2) {
                continue;
            }
            for (BodyInfo other : bodies.values()) {
                if (other == null || other == child || !isMapStellarBody(other)) {
                    continue;
                }
                if (other.getImmediateParentBodyId() == nullId) {
                    return nullId;
                }
            }
        }
        String letter = stellarBranchLetter(child);
        if (letter == null) {
            return -1;
        }
        List<Integer> scanNulls = new ArrayList<>();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() != null && e.getValue() != null && e.getValue().isScanBarycentreRow()) {
                scanNulls.add(e.getKey());
            }
        }
        Collections.sort(scanNulls);
        if (scanNulls.isEmpty()) {
            return -1;
        }
        for (int nullId : scanNulls) {
            for (BodyInfo other : bodies.values()) {
                if (other == null || !isMapStellarBody(other) || other.getImmediateParentBodyId() != nullId) {
                    continue;
                }
                String otherLetter = stellarBranchLetter(other);
                if (letter.equals(otherLetter)) {
                    return nullId;
                }
            }
        }
        if ("B".equals(letter) || "C".equals(letter)) {
            int best = -1;
            for (int nullId : scanNulls) {
                if (nullId >= 49) {
                    continue;
                }
                best = nullId;
            }
            if (best > 0) {
                return best;
            }
        }
        if ("D".equals(letter)) {
            return scanNulls.get(0).intValue();
        }
        return -1;
    }

    /** Single-letter branch star designation ({@code A}, {@code B}, …) from short or full name. */
    private static String stellarBranchLetter(BodyInfo star) {
        String s = firstNonBlank(star.getShortName(), star.getBodyName());
        if (s == null) {
            return null;
        }
        s = s.trim();
        if (s.length() == 1 && Character.isLetter(s.charAt(0))) {
            return s.toUpperCase(Locale.ROOT);
        }
        Matcher m = TRAILING_STAR_BODY_DESIGNATION.matcher(s);
        if (!m.find()) {
            return null;
        }
        if (m.group(2) != null && !m.group(2).isEmpty()) {
            return null;
        }
        String letter = m.group(1);
        return letter != null ? letter.toUpperCase(Locale.ROOT) : null;
    }

    /** Map-plane chord length for {@link #flattenWideBinaryIntoMapPlane} (metres). */
    private static double wideBinaryFlattenTargetSepMetres(BodyInfo primary, BodyInfo companion,
            Map<Integer, BodyInfo> bodies) {
        return wideBinaryFlattenTargetSepMetres(primary, companion, bodies, false);
    }

    /**
     * @param journalTrueScaleChord when true, use full journal {@code |Δ DistanceFromArrivalLS|} (no schematic cap).
     */
    private static double wideBinaryFlattenTargetSepMetres(BodyInfo primary, BodyInfo companion,
            Map<Integer, BodyInfo> bodies, boolean journalTrueScaleChord) {
        if (primary == null || companion == null || bodies == null) {
            return Double.NaN;
        }
        double dP = primary.getDistanceLs();
        double dC = companion.getDistanceLs();
        if (!Double.isFinite(dP) || !Double.isFinite(dC)) {
            return Double.NaN;
        }
        double helioLs = Math.abs(dC - dP);
        double targetLs = helioLs;
        if (!journalTrueScaleChord) {
            if (isHierarchicalTripleStarMap(bodies)) {
                targetLs = HIERARCHICAL_WIDE_BINARY_SCHEMATIC_SEP_LS;
            } else if (isHierarchicalWideBinary(bodies)) {
                targetLs = Math.min(helioLs, HIERARCHICAL_WIDE_BINARY_SCHEMATIC_SEP_LS);
            }
        }
        return targetLs * LIGHT_SECOND_METRES;
    }

    /**
     * After {@code mapProjA0}/{@code mapProjA1} are chosen, move the companion barycentre star (and its subtree) so
     * projected A–B separation matches journal {@code |Δ DistanceFromArrivalLS|}. Keeps on-screen cluster scale ratios
     * aligned with journal (e.g. A 6 at ~258 Ls vs A–B ~3305 Ls).
     */
    public static void flattenWideBinaryIntoMapPlane(Map<Integer, double[]> positions, Map<Integer, BodyInfo> bodies,
            int mapProjA0, int mapProjA1) {
        flattenWideBinaryIntoMapPlane(positions, bodies, mapProjA0, mapProjA1, false);
    }

    /**
     * @param journalTrueScaleChord when true, align A–B chord to full journal heliocentric separation (true-scale map).
     */
    public static void flattenWideBinaryIntoMapPlane(Map<Integer, double[]> positions, Map<Integer, BodyInfo> bodies,
            int mapProjA0, int mapProjA1, boolean journalTrueScaleChord) {
        if (positions == null || positions.isEmpty() || bodies == null || bodies.isEmpty()) {
            return;
        }
        int a0 = clampWorldAxisIndex(mapProjA0);
        int a1 = clampWorldAxisIndex(mapProjA1);
        if (a0 == a1) {
            return;
        }
        int dropped = 3 - a0 - a1;

        int primaryId = primaryAnchorBodyMapKey(bodies);
        int companionId = wideBinaryCompanionAnchorStarId(bodies, positions, primaryId);
        if (companionId < 0) {
            for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                int id = e.getKey().intValue();
                if (id == primaryId) {
                    continue;
                }
                boolean companionStar = isMapStellarBody(e.getValue())
                        || (isHierarchicalWideBinary(bodies) && isStellarBody(e.getValue()));
                if (!companionStar) {
                    continue;
                }
                if (!orbitsWideBinarySystemBarycentre(e.getValue(), bodies, id)
                        && !isHierarchicalWideBinary(bodies)) {
                    continue;
                }
                companionId = id;
                break;
            }
        }
        if (primaryId < 0 || companionId < 0) {
            return;
        }
        BodyInfo primary = bodies.get(Integer.valueOf(primaryId));
        BodyInfo companion = bodies.get(Integer.valueOf(companionId));
        double[] pA = positions.get(Integer.valueOf(primaryId));
        double[] pB = positions.get(Integer.valueOf(companionId));
        if (primary == null || companion == null || pA == null || pB == null || pA.length < 3 || pB.length < 3) {
            return;
        }
        boolean hierarchical = isHierarchicalWideBinary(bodies);
        double targetSepM = wideBinaryFlattenTargetSepMetres(primary, companion, bodies, journalTrueScaleChord);
        if (!Double.isFinite(targetSepM) || targetSepM < WIDE_BINARY_MIN_JOURNAL_SEP_LS * LIGHT_SECOND_METRES) {
            return;
        }
        double refBx = worldAxisMetres(pB, a0);
        double refBy = worldAxisMetres(pB, a1);
        if (hierarchical) {
            double[] companionCentroid = hierarchicalCompanionClusterCentroidMapPlane(positions, bodies, primaryId,
                    a0, a1);
            if (companionCentroid != null) {
                refBx = companionCentroid[0];
                refBy = companionCentroid[1];
            }
        }
        double dPlane0 = refBx - worldAxisMetres(pA, a0);
        double dPlane1 = refBy - worldAxisMetres(pA, a1);
        double projSepM = Math.hypot(dPlane0, dPlane1);
        if (!Double.isFinite(projSepM)) {
            return;
        }
        if (!hierarchical && projSepM >= targetSepM * WIDE_BINARY_PROJ_SEP_MIN_FRAC_OF_JOURNAL) {
            return;
        }
        if (hierarchical && Math.abs(projSepM - targetSepM) < 50.0 * LIGHT_SECOND_METRES) {
            return;
        }
        double ux;
        double uy;
        if (projSepM > LIGHT_SECOND_METRES) {
            ux = dPlane0 / projSepM;
            uy = dPlane1 / projSepM;
        } else {
            ux = 1.0;
            uy = 0.0;
        }
        double refBxNew = worldAxisMetres(pA, a0) + ux * targetSepM;
        double refByNew = worldAxisMetres(pA, a1) + uy * targetSepM;
        double dPlane0Shift = refBxNew - refBx;
        double dPlane1Shift = refByNew - refBy;
        int tripleNullId = isHierarchicalTripleStarMap(bodies) ? hierarchicalTripleStellarNullId(bodies) : -1;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            if (tripleNullId >= 0) {
                if (!isHierarchicalTripleCompanionShiftBody(id, tripleNullId, bodies)) {
                    continue;
                }
            } else if (id == primaryId || isWideBinaryPrimaryBranchBody(id, primaryId, bodies)) {
                continue;
            }
            double[] p = positions.get(e.getKey());
            if (p == null || p.length < 3) {
                continue;
            }
            double[] shifted = Arrays.copyOf(p, Math.max(3, p.length));
            shifted[a0] = worldAxisMetres(p, a0) + dPlane0Shift;
            shifted[a1] = worldAxisMetres(p, a1) + dPlane1Shift;
            positions.put(e.getKey(), shifted);
        }
        if (journalTrueScaleChord) {
            return;
        }
        if (hierarchical) {
            placeHierarchicalWideBinaryOnSystemBarycentre(positions, bodies, mapProjA0, mapProjA1);
        } else {
            recenterBinaryBarycentreInMapPlane(positions, bodies, mapProjA0, mapProjA1);
        }
    }

    /**
     * Centroid for placing the BCD companion trunk: branch stars and Null hubs only — not every planet at ~51k Ls,
     * which skews the direction away from star A's schematic ring.
     */
    private static double[] hierarchicalCompanionClusterCentroidMapPlane(Map<Integer, double[]> positions,
            Map<Integer, BodyInfo> bodies,
            int primaryId,
            int a0,
            int a1) {
        if (positions == null || bodies == null || primaryId < 0) {
            return null;
        }
        int tripleNullId = isHierarchicalTripleStarMap(bodies) ? hierarchicalTripleStellarNullId(bodies) : -1;
        double sumX = 0.0;
        double sumY = 0.0;
        int n = 0;
        if (tripleNullId >= 0) {
            for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                int id = e.getKey().intValue();
                if (!isHierarchicalTripleCompanionShiftBody(id, tripleNullId, bodies)) {
                    continue;
                }
                double[] p = positions.get(e.getKey());
                if (p == null || p.length <= Math.max(a0, a1)) {
                    continue;
                }
                double x = worldAxisMetres(p, a0);
                double y = worldAxisMetres(p, a1);
                if (!Double.isFinite(x) || !Double.isFinite(y)) {
                    continue;
                }
                sumX += x;
                sumY += y;
                n++;
            }
        } else {
            for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                int id = e.getKey().intValue();
                if (!contributesToHierarchicalCompanionCentroid(id, e.getValue(), primaryId, bodies)) {
                    continue;
                }
                double[] p = positions.get(e.getKey());
                if (p == null || p.length <= Math.max(a0, a1)) {
                    continue;
                }
                double x = worldAxisMetres(p, a0);
                double y = worldAxisMetres(p, a1);
                if (!Double.isFinite(x) || !Double.isFinite(y)) {
                    continue;
                }
                sumX += x;
                sumY += y;
                n++;
            }
            for (Map.Entry<Integer, double[]> e : positions.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                int id = e.getKey().intValue();
                if (!isPlanetBinaryBarycentreMapKey(id)) {
                    continue;
                }
                double[] p = e.getValue();
                if (p.length <= Math.max(a0, a1)) {
                    continue;
                }
                double x = worldAxisMetres(p, a0);
                double y = worldAxisMetres(p, a1);
                if (!Double.isFinite(x) || !Double.isFinite(y)) {
                    continue;
                }
                sumX += x;
                sumY += y;
                n++;
            }
        }
        if (n <= 0) {
            return null;
        }
        return new double[] { sumX / n, sumY / n };
    }

    private static boolean contributesToHierarchicalCompanionCentroid(int id, BodyInfo b, int primaryId,
            Map<Integer, BodyInfo> bodies) {
        if (b == null || bodies == null || id == primaryId) {
            return false;
        }
        if (b.isScanBarycentreRow()) {
            return true;
        }
        if (isWideBinaryPrimaryBranchBody(id, primaryId, bodies)) {
            return false;
        }
        if (isMapStellarBody(b) || isStellarBody(b) || isStellarDirectNullMember(b, bodies)) {
            String letter = stellarBranchLetter(b);
            return letter != null && !"A".equals(letter);
        }
        return false;
    }

    /**
     * {@code +} markers use scan-row map keys; copy schematic positions from synthetic Null hub keys after layout.
     */
    /**
     * After A-branch / mutual-orbit alignment, nudge the companion trunk so its cluster centroid sits on the
     * schematic A↔BCD ring rim (matches {@link #appendHierarchicalSystemBarycentreRing}).
     */
    /** Map-plane centroid of the BCD companion trunk (stars + Null hubs), for tests and debug logs. */
    public static double[] companionClusterCentroidMapPlane(Map<Integer, double[]> positions,
            Map<Integer, BodyInfo> bodies,
            int primaryId,
            int a0,
            int a1) {
        return hierarchicalCompanionClusterCentroidMapPlane(positions, bodies, primaryId, a0, a1);
    }

    public static void snapCompanionClusterOntoTrunkRing(Map<Integer, double[]> positions,
            Map<Integer, BodyInfo> bodies,
            Instant now,
            int a0,
            int a1,
            boolean freezeBarycentreStars) {
        if (positions == null || bodies == null || !isHierarchicalWideBinary(bodies)) {
            return;
        }
        int primaryId = primaryAnchorBodyMapKey(bodies);
        if (primaryId < 0) {
            return;
        }
        double[] aPos = positions.get(Integer.valueOf(primaryId));
        int needLen = Math.max(a0, a1) + 1;
        if (aPos == null || aPos.length < needLen) {
            return;
        }
        double ax = worldAxisMetres(aPos, a0);
        double ay = worldAxisMetres(aPos, a1);
        double[] cc = hierarchicalCompanionClusterCentroidMapPlane(positions, bodies, primaryId, a0, a1);
        if (cc == null) {
            return;
        }
        double cx = cc[0];
        double cy = cc[1];
        double mx = (ax + cx) * 0.5;
        double my = (ay + cy) * 0.5;
        double chord = Math.hypot(cx - ax, cy - ay);
        if (!(chord > LIGHT_SECOND_METRES)) {
            return;
        }
        double halfR = chord * 0.5;
        double dx = cx - mx;
        double dy = cy - my;
        double d = Math.hypot(dx, dy);
        if (!(d > 1.0)) {
            return;
        }
        double rimX = mx + dx / d * halfR;
        double rimY = my + dy / d * halfR;
        double shift0 = rimX - cx;
        double shift1 = rimY - cy;
        if (Math.abs(shift0) < 1.0 && Math.abs(shift1) < 1.0) {
            return;
        }
        shiftMapPlaneBranch(positions, bodies, primaryId, -1, shift0, shift1, a0, a1);
        alignPlanetBinaryGroupsOnMapPlane(positions, bodies, now, a0, a1, freezeBarycentreStars);
    }

    /**
     * True-scale hierarchical maps: place inner {@code ScanBaryCentre} hubs from journal heliocentric distance along
     * the A→outer-companion axis (wide-binary flatten can leave hubs at companion-cluster scale).
     */
    public static void placeTrueScaleHierarchicalScanHubs(Map<Integer, double[]> positions,
            Map<Integer, BodyInfo> bodies,
            int mapProjA0,
            int mapProjA1) {
        if (positions == null || bodies == null || !isHierarchicalWideBinary(bodies)
                || isHierarchicalTripleStarMap(bodies)) {
            return;
        }
        int a0 = clampWorldAxisIndex(mapProjA0);
        int a1 = clampWorldAxisIndex(mapProjA1);
        if (a0 == a1) {
            return;
        }
        int primaryId = primaryAnchorBodyMapKey(bodies);
        if (primaryId < 0) {
            return;
        }
        BodyInfo primary = bodies.get(Integer.valueOf(primaryId));
        double dP = primary != null ? primary.getDistanceLs() : 0.0;
        double outerHelioLs = 0.0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isScanBarycentreRow()) {
                continue;
            }
            if (!contributesToHierarchicalCompanionCentroid(e.getKey().intValue(), e.getValue(), primaryId, bodies)) {
                continue;
            }
            double d = e.getValue().getDistanceLs();
            if (Double.isFinite(d)) {
                outerHelioLs = Math.max(outerHelioLs, Math.abs(d - dP));
            }
        }
        if (outerHelioLs < 500.0) {
            return;
        }
        double[] aPos = positions.get(Integer.valueOf(primaryId));
        double[] outer = hierarchicalOutermostCompanionMapPlane(positions, bodies, primaryId, a0, a1);
        if (aPos == null || outer == null) {
            return;
        }
        int needLen = Math.max(a0, a1) + 1;
        if (aPos.length < needLen) {
            return;
        }
        double ax = worldAxisMetres(aPos, a0);
        double ay = worldAxisMetres(aPos, a1);
        double mapOuter = Math.hypot(outer[0] - ax, outer[1] - ay);
        if (!(mapOuter > MIN_FALLBACK_ORBIT_RADIUS_METRES)) {
            return;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || !e.getValue().isScanBarycentreRow()) {
                continue;
            }
            int nullId = e.getKey().intValue();
            if (isHierarchicalOuterStellarNullPair(nullId, bodies)) {
                continue;
            }
            double d = e.getValue().getDistanceLs();
            if (!Double.isFinite(d)) {
                continue;
            }
            double helio = Math.abs(d - dP);
            if (helio >= outerHelioLs * 0.45 || helio > 5_000.0) {
                continue;
            }
            double mapInner = mapOuter * (helio / outerHelioLs);
            double bx = ax + (outer[0] - ax) * (mapInner / mapOuter);
            double by = ay + (outer[1] - ay) * (mapInner / mapOuter);
            int hubKey = planetBinaryBarycentreMapKey(nullId);
            double[] template = positions.get(Integer.valueOf(hubKey));
            if (template == null) {
                template = positions.get(Integer.valueOf(primaryId));
            }
            int len = Math.max(needLen, template != null ? template.length : 3);
            double[] hub = template != null ? Arrays.copyOf(template, len) : new double[len];
            hub[a0] = bx;
            hub[a1] = by;
            positions.put(Integer.valueOf(hubKey), hub);
            positions.put(Integer.valueOf(nullId), Arrays.copyOf(hub, len));
        }
    }

    public static void syncScanBarycentreRowPositionsToSyntheticHubs(Map<Integer, double[]> positions,
            Map<Integer, BodyInfo> bodies) {
        if (positions == null || bodies == null) {
            return;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || !e.getValue().isScanBarycentreRow()) {
                continue;
            }
            int nullId = e.getKey().intValue();
            double[] hub = positions.get(Integer.valueOf(planetBinaryBarycentreMapKey(nullId)));
            if (hub == null || hub.length < 2) {
                continue;
            }
            positions.put(e.getKey(), Arrays.copyOf(hub, Math.max(3, hub.length)));
        }
    }

    /**
     * Four-star hierarchies: A and the BCD cluster sit on opposite sides of the system barycentre (map origin), not
     * with A at the centre of the large mutual-orbit ring.
     */
    public static void placeHierarchicalWideBinaryOnSystemBarycentre(Map<Integer, double[]> positions,
            Map<Integer, BodyInfo> bodies,
            int mapProjA0,
            int mapProjA1) {
        if (positions == null || bodies == null || !isHierarchicalWideBinary(bodies)) {
            return;
        }
        if (isHierarchicalTripleStarMap(bodies)) {
            placeHierarchicalTripleStarCluster(positions, bodies, mapProjA0, mapProjA1);
            return;
        }
        int a0 = clampWorldAxisIndex(mapProjA0);
        int a1 = clampWorldAxisIndex(mapProjA1);
        if (a0 == a1) {
            return;
        }
        int primaryId = primaryAnchorBodyMapKey(bodies);
        if (primaryId < 0) {
            return;
        }
        double[] pA = positions.get(Integer.valueOf(primaryId));
        if (pA == null || pA.length < 3) {
            return;
        }
        double ax = worldAxisMetres(pA, a0);
        double ay = worldAxisMetres(pA, a1);
        double refBx = ax;
        double refBy = ay;
        double[] companionCentroid = hierarchicalCompanionClusterCentroidMapPlane(positions, bodies, primaryId, a0,
                a1);
        if (companionCentroid != null) {
            refBx = companionCentroid[0];
            refBy = companionCentroid[1];
        }
        double halfSepM = HIERARCHICAL_WIDE_BINARY_SCHEMATIC_SEP_LS * LIGHT_SECOND_METRES * 0.5;
        double dx = refBx - ax;
        double dy = refBy - ay;
        double sep = Math.hypot(dx, dy);
        double ux = sep > LIGHT_SECOND_METRES ? dx / sep : 1.0;
        double uy = sep > LIGHT_SECOND_METRES ? dy / sep : 0.0;
        double targetAx = -ux * halfSepM;
        double targetAy = -uy * halfSepM;
        double targetBx = ux * halfSepM;
        double targetBy = uy * halfSepM;
        shiftMapPlaneBranch(positions, bodies, primaryId, primaryId, targetAx - ax, targetAy - ay, a0, a1);
        shiftMapPlaneBranch(positions, bodies, primaryId, -1, targetBx - refBx, targetBy - refBy, a0, a1);
        syncScanBarycentreRowPositionsToSyntheticHubs(positions, bodies);
    }

    /**
     * Triple star (A vs B+C): keep A on the arrival branch and place the inner pair on a schematic trunk (~7.5k Ls),
     * not the four-star system-barycentre ring at map origin.
     */
    private static void placeHierarchicalTripleStarCluster(Map<Integer, double[]> positions,
            Map<Integer, BodyInfo> bodies,
            int mapProjA0,
            int mapProjA1) {
        int nullId = hierarchicalTripleStellarNullId(bodies);
        if (nullId < 0) {
            return;
        }
        int a0 = clampWorldAxisIndex(mapProjA0);
        int a1 = clampWorldAxisIndex(mapProjA1);
        if (a0 == a1) {
            return;
        }
        int primaryId = primaryAnchorBodyMapKey(bodies);
        if (primaryId < 0) {
            return;
        }
        double[] pA = positions.get(Integer.valueOf(primaryId));
        if (pA == null || pA.length < 3) {
            return;
        }
        double ax = worldAxisMetres(pA, a0);
        double ay = worldAxisMetres(pA, a1);
        double refBx = ax;
        double refBy = ay;
        double sumX = 0.0;
        double sumY = 0.0;
        int n = 0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            BodyInfo star = e.getValue();
            if (id == primaryId || !isStellarDirectNullMember(star, bodies)) {
                continue;
            }
            if (star.getImmediateParentBodyId() != nullId) {
                String letter = stellarBranchLetter(star);
                if (letter == null || "A".equals(letter)) {
                    continue;
                }
            }
            double[] p = positions.get(e.getKey());
            if (p == null || p.length <= Math.max(a0, a1)) {
                continue;
            }
            double x = worldAxisMetres(p, a0);
            double y = worldAxisMetres(p, a1);
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                continue;
            }
            sumX += x;
            sumY += y;
            n++;
        }
        if (n > 0) {
            refBx = sumX / n;
            refBy = sumY / n;
        }
        double sepM = HIERARCHICAL_WIDE_BINARY_SCHEMATIC_SEP_LS * LIGHT_SECOND_METRES;
        double dx = refBx - ax;
        double dy = refBy - ay;
        double journalSep = Math.hypot(dx, dy);
        double ux = journalSep > LIGHT_SECOND_METRES ? dx / journalSep : 1.0;
        double uy = journalSep > LIGHT_SECOND_METRES ? dy / journalSep : 0.0;
        double targetCx = ax + ux * sepM;
        double targetCy = ay + uy * sepM;
        double dPlane0 = targetCx - refBx;
        double dPlane1 = targetCy - refBy;
        if (Math.abs(dPlane0) >= 1.0 || Math.abs(dPlane1) >= 1.0) {
            for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                int id = e.getKey().intValue();
                if (!isHierarchicalTripleCompanionShiftBody(id, nullId, bodies)) {
                    continue;
                }
                double[] p = positions.get(e.getKey());
                if (p == null || p.length < 3) {
                    continue;
                }
                double[] shifted = Arrays.copyOf(p, Math.max(3, p.length));
                shifted[a0] = worldAxisMetres(p, a0) + dPlane0;
                shifted[a1] = worldAxisMetres(p, a1) + dPlane1;
                positions.put(e.getKey(), shifted);
            }
        }
        int bKey = planetBinaryBarycentreMapKey(nullId);
        double[] hub = positions.get(Integer.valueOf(bKey));
        if (hub == null || hub.length < Math.max(a0, a1) + 1) {
            hub = new double[Math.max(3, Math.max(a0, a1) + 1)];
        } else {
            hub = Arrays.copyOf(hub, Math.max(3, hub.length));
        }
        hub[a0] = targetCx;
        hub[a1] = targetCy;
        positions.put(Integer.valueOf(bKey), hub);
    }

    private static void shiftMapPlaneBranch(Map<Integer, double[]> positions,
            Map<Integer, BodyInfo> bodies,
            int primaryId,
            int branchRootId,
            double dPlane0,
            double dPlane1,
            int a0,
            int a1) {
        if (positions == null || bodies == null) {
            return;
        }
        if (!Double.isFinite(dPlane0) || !Double.isFinite(dPlane1)) {
            return;
        }
        if (Math.abs(dPlane0) < 1.0 && Math.abs(dPlane1) < 1.0) {
            return;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            boolean inBranch = branchRootId >= 0
                    ? isWideBinaryPrimaryBranchBody(id, branchRootId, bodies)
                    : !isWideBinaryPrimaryBranchBody(id, primaryId, bodies);
            if (!inBranch) {
                continue;
            }
            double[] p = positions.get(e.getKey());
            if (p == null || p.length < 3) {
                continue;
            }
            double[] shifted = Arrays.copyOf(p, Math.max(3, p.length));
            shifted[a0] = worldAxisMetres(p, a0) + dPlane0;
            shifted[a1] = worldAxisMetres(p, a1) + dPlane1;
            positions.put(e.getKey(), shifted);
        }
        if (branchRootId < 0) {
            shiftSyntheticBarycentrePositions(positions, dPlane0, dPlane1, a0, a1);
        }
    }

    /** {@link #planetBinaryBarycentreMapKey} rows live only in {@code positions}, not {@code bodies}. */
    private static void shiftSyntheticBarycentrePositions(Map<Integer, double[]> positions,
            double dPlane0,
            double dPlane1,
            int a0,
            int a1) {
        if (positions == null) {
            return;
        }
        for (Map.Entry<Integer, double[]> e : positions.entrySet()) {
            if (e.getKey() == null || e.getKey().intValue() >= 0) {
                continue;
            }
            double[] p = e.getValue();
            if (p == null || p.length < 3) {
                continue;
            }
            double[] shifted = Arrays.copyOf(p, Math.max(3, p.length));
            shifted[a0] = worldAxisMetres(p, a0) + dPlane0;
            shifted[a1] = worldAxisMetres(p, a1) + dPlane1;
            positions.put(e.getKey(), shifted);
        }
    }

    /**
     * Schematic trunk ring through two anchor points (primary A and companion hub) — matches
     * {@link #placeHierarchicalWideBinaryOnSystemBarycentre} after layout, not a fixed circle at map origin.
     */
    private static void appendSchematicTrunkRingBetweenAnchors(List<OrbitPolylineWorldXY> out,
            double ax,
            double ay,
            double bx,
            double by,
            int strokeBodyId,
            int legacyN,
            boolean useScreenChord,
            double scalePixelsPerMetre) {
        if (out == null || !Double.isFinite(ax) || !Double.isFinite(ay) || !Double.isFinite(bx)
                || !Double.isFinite(by)) {
            return;
        }
        double trunkLen = Math.hypot(bx - ax, by - ay);
        double halfSepM = trunkLen * 0.5;
        if (!Double.isFinite(halfSepM) || halfSepM < MIN_FALLBACK_ORBIT_RADIUS_METRES) {
            halfSepM = HIERARCHICAL_WIDE_BINARY_SCHEMATIC_SEP_LS * LIGHT_SECOND_METRES * 0.5;
        }
        double cx = (ax + bx) * 0.5;
        double cy = (ay + by) * 0.5;
        int n = legacyN;
        if (useScreenChord && Double.isFinite(scalePixelsPerMetre) && scalePixelsPerMetre > 0.0) {
            n = segmentCountForScreenChord(scalePixelsPerMetre, Math.PI * 2.0 * halfSepM, legacyN);
        }
        n = Math.max(12, Math.min(ORBIT_POLYLINE_SEGMENTS_HARD_MAX, n));
        double[] wx = new double[n];
        double[] wy = new double[n];
        for (int i = 0; i < n; i++) {
            double theta = (Math.PI * 2.0 * i) / n;
            wx[i] = cx + halfSepM * Math.cos(theta);
            wy[i] = cy + halfSepM * Math.sin(theta);
        }
        out.add(new OrbitPolylineWorldXY(strokeBodyId, wx, wy));
    }

    /**
     * Four-star A vs BCD: trunk ring through star A and the companion-cluster centroid (same anchors as placement).
     */
    private static void appendHierarchicalSystemBarycentreRing(List<OrbitPolylineWorldXY> out,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int p0,
            int p1,
            int legacyN,
            boolean useScreenChord,
            double scalePixelsPerMetre) {
        if (out == null || bodies == null || bodyWorldPositions == null || !isHierarchicalWideBinary(bodies)
                || isHierarchicalTripleStarMap(bodies)) {
            return;
        }
        int primaryId = primaryAnchorBodyMapKey(bodies);
        if (primaryId < 0) {
            return;
        }
        double[] aPos = bodyWorldPositions.get(Integer.valueOf(primaryId));
        int needLen = Math.max(p0, p1) + 1;
        if (aPos == null || aPos.length < needLen) {
            return;
        }
        double ax = worldAxisMetres(aPos, p0);
        double ay = worldAxisMetres(aPos, p1);
        double bx;
        double by;
        double[] outermost = hierarchicalOutermostCompanionMapPlane(bodyWorldPositions, bodies, primaryId, p0, p1);
        if (!hierarchicalCompanionBranchStarsCohesive(bodies) && outermost != null) {
            bx = outermost[0];
            by = outermost[1];
        } else {
            double[] companion = hierarchicalCompanionClusterCentroidMapPlane(bodyWorldPositions, bodies, primaryId,
                    p0, p1);
            if (companion != null && Double.isFinite(companion[0]) && Double.isFinite(companion[1])) {
                bx = companion[0];
                by = companion[1];
            } else {
                bx = ax + HIERARCHICAL_WIDE_BINARY_SCHEMATIC_SEP_LS * LIGHT_SECOND_METRES;
                by = ay;
            }
        }
        appendSchematicTrunkRingBetweenAnchors(out, ax, ay, bx, by, BINARY_BARYCENTRE_ORBIT_RING_BODY_ID, legacyN,
                useScreenChord, scalePixelsPerMetre);
    }

    /** Farthest non-A branch star on the map plane — outer trunk anchor when companions are not a tight pair. */
    private static double[] hierarchicalOutermostCompanionMapPlane(Map<Integer, double[]> bodyWorldPositions,
            Map<Integer, BodyInfo> bodies,
            int primaryId,
            int p0,
            int p1) {
        if (bodyWorldPositions == null || bodies == null || primaryId < 0) {
            return null;
        }
        double[] aPos = bodyWorldPositions.get(Integer.valueOf(primaryId));
        int needLen = Math.max(p0, p1) + 1;
        if (aPos == null || aPos.length < needLen) {
            return null;
        }
        double ax = worldAxisMetres(aPos, p0);
        double ay = worldAxisMetres(aPos, p1);
        double bestSep = -1.0;
        double[] best = null;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            if (!contributesToHierarchicalCompanionCentroid(id, e.getValue(), primaryId, bodies)) {
                continue;
            }
            double[] pos = bodyWorldPositions.get(Integer.valueOf(id));
            if (pos == null || pos.length < needLen) {
                continue;
            }
            double bx = worldAxisMetres(pos, p0);
            double by = worldAxisMetres(pos, p1);
            if (!Double.isFinite(bx) || !Double.isFinite(by)) {
                continue;
            }
            double sep = Math.hypot(bx - ax, by - ay);
            if (sep > bestSep) {
                bestSep = sep;
                best = new double[] { bx, by };
            }
        }
        return best;
    }

    /**
     * Four-star hierarchies with a tight primary–companion pair (e.g. EOL PROU LH-U D3-2700: B at ~225 Ls, C at ~11k
     * Ls): draw a second trunk ring between A and the nearest branch star when it is clearly inner vs the rest.
     */
    private static void appendHierarchicalInnerPrimaryCompanionRing(List<OrbitPolylineWorldXY> out,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int p0,
            int p1,
            int legacyN,
            boolean useScreenChord,
            double scalePixelsPerMetre) {
        if (out == null || bodies == null || bodyWorldPositions == null || !isHierarchicalWideBinary(bodies)
                || isHierarchicalTripleStarMap(bodies)) {
            return;
        }
        int primaryId = primaryAnchorBodyMapKey(bodies);
        if (primaryId < 0) {
            return;
        }
        double[] aPos = bodyWorldPositions.get(Integer.valueOf(primaryId));
        int needLen = Math.max(p0, p1) + 1;
        if (aPos == null || aPos.length < needLen) {
            return;
        }
        double ax = worldAxisMetres(aPos, p0);
        double ay = worldAxisMetres(aPos, p1);
        if (!Double.isFinite(ax) || !Double.isFinite(ay)) {
            return;
        }
        List<double[]> companions = new ArrayList<>();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            if (!isHierarchicalInnerCompanionCandidate(id, e.getValue(), primaryId, bodies)) {
                continue;
            }
            double[] pos = bodyWorldPositions.get(Integer.valueOf(id));
            if (pos == null || pos.length < needLen) {
                continue;
            }
            double bx = worldAxisMetres(pos, p0);
            double by = worldAxisMetres(pos, p1);
            if (!Double.isFinite(bx) || !Double.isFinite(by)) {
                continue;
            }
            double sepM = Math.hypot(bx - ax, by - ay);
            if (!Double.isFinite(sepM) || sepM < MIN_FALLBACK_ORBIT_RADIUS_METRES) {
                continue;
            }
            companions.add(new double[] { sepM, bx, by, id });
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || !e.getValue().isScanBarycentreRow()) {
                continue;
            }
            int nullId = e.getKey().intValue();
            if (isHierarchicalOuterStellarNullPair(nullId, bodies)) {
                continue;
            }
            int hubKey = planetBinaryBarycentreMapKey(nullId);
            double[] hubPos = bodyWorldPositions.get(Integer.valueOf(hubKey));
            if (hubPos == null || hubPos.length < needLen) {
                continue;
            }
            double bx = worldAxisMetres(hubPos, p0);
            double by = worldAxisMetres(hubPos, p1);
            if (!Double.isFinite(bx) || !Double.isFinite(by)) {
                continue;
            }
            double sepM = Math.hypot(bx - ax, by - ay);
            if (!Double.isFinite(sepM) || sepM < MIN_FALLBACK_ORBIT_RADIUS_METRES) {
                continue;
            }
            companions.add(new double[] { sepM, bx, by, hubKey });
        }
        double[] innerHub = hierarchicalInnerScanHubMapPlane(bodyWorldPositions, bodies, primaryId, p0, p1);
        if (companions.isEmpty() && innerHub == null) {
            return;
        }
        companions.sort((a, b) -> Double.compare(a[0], b[0]));
        double closestSepLs = companions.isEmpty() ? 0.0 : companions.get(0)[0] / LIGHT_SECOND_METRES;
        double secondSepLs = companions.size() >= 2 ? companions.get(1)[0] / LIGHT_SECOND_METRES
                : Double.POSITIVE_INFINITY;
        boolean tightInnerPair = companions.size() >= 2 && closestSepLs < secondSepLs * 0.4;
        boolean loneCloseCompanion = companions.size() == 1 && closestSepLs < 4_000.0;
        if (!tightInnerPair && !loneCloseCompanion && innerHub == null) {
            return;
        }
        double anchorX;
        double anchorY;
        if (innerHub != null) {
            anchorX = innerHub[0];
            anchorY = innerHub[1];
        } else {
            anchorX = companions.get(0)[1];
            anchorY = companions.get(0)[2];
        }
        if (closestSepLs > 5_000.0 && innerHub == null) {
            return;
        }
        appendSchematicTrunkRingBetweenAnchors(out, ax, ay, anchorX, anchorY,
                HIERARCHICAL_INNER_STELLAR_PAIR_POLYLINE_ID, legacyN, useScreenChord, scalePixelsPerMetre);
    }

    /**
     * Inner {@code ScanBaryCentre} hub for the A–B trunk when journal distance is clearly inside the outer companion
     * (map-plane flatten can place the hub farther than a branch star).
     */
    private static double[] hierarchicalInnerScanHubMapPlane(Map<Integer, double[]> bodyWorldPositions,
            Map<Integer, BodyInfo> bodies,
            int primaryId,
            int p0,
            int p1) {
        if (bodyWorldPositions == null || bodies == null || primaryId < 0) {
            return null;
        }
        BodyInfo primary = bodies.get(Integer.valueOf(primaryId));
        double dP = primary != null ? primary.getDistanceLs() : 0.0;
        double outerHelioLs = 0.0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isScanBarycentreRow()) {
                continue;
            }
            if (!contributesToHierarchicalCompanionCentroid(e.getKey().intValue(), e.getValue(), primaryId, bodies)) {
                continue;
            }
            double d = e.getValue().getDistanceLs();
            if (Double.isFinite(d)) {
                outerHelioLs = Math.max(outerHelioLs, Math.abs(d - dP));
            }
        }
        if (outerHelioLs < 500.0) {
            return null;
        }
        double bestJournalLs = Double.POSITIVE_INFINITY;
        double[] bestPlane = null;
        int needLen = Math.max(p0, p1) + 1;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || !e.getValue().isScanBarycentreRow()) {
                continue;
            }
            int nullId = e.getKey().intValue();
            if (isHierarchicalOuterStellarNullPair(nullId, bodies)) {
                continue;
            }
            double d = e.getValue().getDistanceLs();
            if (!Double.isFinite(d)) {
                continue;
            }
            double helio = Math.abs(d - dP);
            if (helio >= outerHelioLs * 0.45 || helio > 5_000.0) {
                continue;
            }
            int hubKey = planetBinaryBarycentreMapKey(nullId);
            double[] hubPos = bodyWorldPositions.get(Integer.valueOf(hubKey));
            if (hubPos == null || hubPos.length < needLen) {
                hubPos = bodyWorldPositions.get(Integer.valueOf(nullId));
            }
            double bx = Double.NaN;
            double by = Double.NaN;
            double[] aPos = bodyWorldPositions.get(Integer.valueOf(primaryId));
            double[] outer = hierarchicalOutermostCompanionMapPlane(bodyWorldPositions, bodies, primaryId, p0, p1);
            if (aPos != null && aPos.length >= needLen && outer != null && outerHelioLs > helio) {
                double ax = worldAxisMetres(aPos, p0);
                double ay = worldAxisMetres(aPos, p1);
                double mapOuter = Math.hypot(outer[0] - ax, outer[1] - ay);
                if (mapOuter > MIN_FALLBACK_ORBIT_RADIUS_METRES) {
                    double mapInner = mapOuter * (helio / outerHelioLs);
                    bx = ax + (outer[0] - ax) * (mapInner / mapOuter);
                    by = ay + (outer[1] - ay) * (mapInner / mapOuter);
                }
            }
            if (hubPos != null && hubPos.length >= needLen) {
                double hx = worldAxisMetres(hubPos, p0);
                double hy = worldAxisMetres(hubPos, p1);
                if (Double.isFinite(hx) && Double.isFinite(hy) && aPos != null && aPos.length >= needLen) {
                    double mapHubLs = Math.hypot(hx - worldAxisMetres(aPos, p0), hy - worldAxisMetres(aPos, p1))
                            / LIGHT_SECOND_METRES;
                    if (!Double.isFinite(bx) || !Double.isFinite(by) || mapHubLs <= helio * 2.5) {
                        bx = hx;
                        by = hy;
                    }
                }
            }
            if (!Double.isFinite(bx) || !Double.isFinite(by)) {
                continue;
            }
            if (helio < bestJournalLs) {
                bestJournalLs = helio;
                bestPlane = new double[] { bx, by };
            }
        }
        return bestPlane;
    }

    /** Non-primary branch stars for the inner A–B ring, including companions parented directly to A. */
    private static boolean isHierarchicalInnerCompanionCandidate(int id, BodyInfo b, int primaryId,
            Map<Integer, BodyInfo> bodies) {
        if (b == null || bodies == null || id == primaryId || b.isScanBarycentreRow()) {
            return false;
        }
        if (contributesToHierarchicalCompanionCentroid(id, b, primaryId, bodies)) {
            return true;
        }
        if (!isMapStellarBody(b) && !isStellarBody(b)) {
            return false;
        }
        String letter = stellarBranchLetter(b);
        if (letter == null || "A".equals(letter)) {
            return false;
        }
        int ip = b.getImmediateParentBodyId();
        return ip == primaryId;
    }

    /**
     * Schematic mutual-orbit ring between primary A and the B+C hub — same stroke style as other orbits, not a straight
     * chord (replaces the empty system-barycentre ring at map origin).
     */
    private static void appendHierarchicalTripleStarTrunk(List<OrbitPolylineWorldXY> out,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int p0,
            int p1,
            int legacyN,
            boolean useScreenChord,
            double scalePixelsPerMetre) {
        if (out == null || bodies == null || bodyWorldPositions == null || !isHierarchicalTripleStarMap(bodies)) {
            return;
        }
        int nullId = hierarchicalTripleStellarNullId(bodies);
        if (nullId < 0) {
            return;
        }
        int primaryId = primaryAnchorBodyMapKey(bodies);
        int bKey = planetBinaryBarycentreMapKey(nullId);
        if (primaryId < 0) {
            return;
        }
        double[] aPos = bodyWorldPositions.get(Integer.valueOf(primaryId));
        double[] bPos = bodyWorldPositions.get(Integer.valueOf(bKey));
        int needLen = Math.max(p0, p1) + 1;
        if (aPos == null || aPos.length < needLen) {
            return;
        }
        double ax = worldAxisMetres(aPos, p0);
        double ay = worldAxisMetres(aPos, p1);
        double bx;
        double by;
        if (bPos != null && bPos.length >= needLen) {
            bx = worldAxisMetres(bPos, p0);
            by = worldAxisMetres(bPos, p1);
        } else {
            double[] centroid = planetBinaryMemberCentroidWorldXY(nullId, bodies, bodyWorldPositions, p0, p1);
            if (centroid == null) {
                return;
            }
            bx = centroid[0];
            by = centroid[1];
        }
        appendSchematicTrunkRingBetweenAnchors(out, ax, ay, bx, by, HIERARCHICAL_TRIPLE_STAR_TRUNK_POLYLINE_ID,
                legacyN, useScreenChord, scalePixelsPerMetre);
    }

    /**
     * True when {@code bodyId} is the primary anchor star or a descendant on that branch (e.g. A and A 1…A 4 moons),
     * not the wide-binary companion cluster.
     */
    public static boolean isWideBinaryPrimaryBranchBody(int bodyId, Map<Integer, BodyInfo> bodies) {
        if (bodies == null || bodyId < 0) {
            return false;
        }
        int primaryId = primaryAnchorBodyMapKey(bodies);
        return isWideBinaryPrimaryBranchBody(bodyId, primaryId, bodies);
    }

    /** True when {@code bodyId} is the primary star or orbits it (A-branch), not the wide-binary companion cluster. */
    private static boolean isWideBinaryPrimaryBranchBody(int bodyId, int primaryId, Map<Integer, BodyInfo> bodies) {
        if (bodies == null || primaryId < 0 || bodyId < 0) {
            return false;
        }
        BodyInfo start = bodies.get(Integer.valueOf(bodyId));
        if (start != null && isHierarchicalWideBinary(bodies)) {
            String branch = designationBranchLetter(start);
            BodyInfo primary = bodies.get(Integer.valueOf(primaryId));
            String primaryBranch = branchLetterOfStellarBody(primary, bodies);
            if (primaryBranch == null && primary != null) {
                primaryBranch = designationBranchLetter(primary);
            }
            if (branch != null && primaryBranch != null && branch.equalsIgnoreCase(primaryBranch)) {
                return true;
            }
        }
        int cur = bodyId;
        Set<Integer> seen = new HashSet<>();
        for (int hop = 0; hop < 64; hop++) {
            if (cur == primaryId) {
                return true;
            }
            if (!seen.add(Integer.valueOf(cur))) {
                return false;
            }
            BodyInfo b = bodies.get(Integer.valueOf(cur));
            if (b == null) {
                if (isPlanetBinaryBarycentreMapKey(cur)) {
                    return false;
                }
                return false;
            }
            int p = resolveOrbitParentBodyId(b, bodies, cur);
            if (p < 0) {
                return false;
            }
            cur = p;
        }
        return false;
    }

    /**
     * Records the wide-binary chord after {@link #flattenWideBinaryIntoMapPlane} so schematic playback can re-apply the
     * same A→B direction each tick instead of recomputing it from moving Kepler offsets.
     */
    public static WideBinaryFlattenFrame captureWideBinaryFlattenFrame(Map<Integer, double[]> positions,
            Map<Integer, BodyInfo> bodies, int mapProjA0, int mapProjA1) {
        if (positions == null || bodies == null || bodies.isEmpty()) {
            return null;
        }
        int a0 = clampWorldAxisIndex(mapProjA0);
        int a1 = clampWorldAxisIndex(mapProjA1);
        if (a0 == a1) {
            return null;
        }
        int primaryId = primaryAnchorBodyMapKey(bodies);
        int companionId = wideBinaryCompanionAnchorStarId(bodies, positions, primaryId);
        if (companionId < 0) {
            for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                int id = e.getKey().intValue();
                if (id == primaryId || !isMapStellarBody(e.getValue())) {
                    continue;
                }
                if (!orbitsWideBinarySystemBarycentre(e.getValue(), bodies, id)) {
                    continue;
                }
                companionId = id;
                break;
            }
        }
        if (primaryId < 0 || companionId < 0) {
            return null;
        }
        BodyInfo primary = bodies.get(Integer.valueOf(primaryId));
        BodyInfo companion = bodies.get(Integer.valueOf(companionId));
        double[] pA = positions.get(Integer.valueOf(primaryId));
        double[] pB = positions.get(Integer.valueOf(companionId));
        if (primary == null || companion == null || pA == null || pB == null || pA.length < 3 || pB.length < 3) {
            return null;
        }
        double targetSepM = wideBinaryFlattenTargetSepMetres(primary, companion, bodies);
        if (!Double.isFinite(targetSepM) || targetSepM < WIDE_BINARY_MIN_JOURNAL_SEP_LS * LIGHT_SECOND_METRES) {
            return null;
        }
        double dPlane0 = worldAxisMetres(pB, a0) - worldAxisMetres(pA, a0);
        double dPlane1 = worldAxisMetres(pB, a1) - worldAxisMetres(pA, a1);
        double projSepM = Math.hypot(dPlane0, dPlane1);
        if (!Double.isFinite(projSepM) || projSepM < LIGHT_SECOND_METRES) {
            return null;
        }
        return new WideBinaryFlattenFrame(primaryId, companionId, dPlane0 / projSepM, dPlane1 / projSepM, targetSepM);
    }

    /**
     * Re-applies wide-binary flatten using a chord captured at {@link #setScene} time (schematic playback ticks).
     */
    public static void reapplyWideBinaryFlattenWithFrame(Map<Integer, double[]> positions,
            Map<Integer, BodyInfo> bodies, int mapProjA0, int mapProjA1, WideBinaryFlattenFrame frame) {
        if (positions == null || bodies == null || frame == null) {
            return;
        }
        int a0 = clampWorldAxisIndex(mapProjA0);
        int a1 = clampWorldAxisIndex(mapProjA1);
        if (a0 == a1) {
            return;
        }
        int dropped = 3 - a0 - a1;
        double[] pA = positions.get(Integer.valueOf(frame.primaryId));
        double[] pB = positions.get(Integer.valueOf(frame.companionId));
        if (pA == null || pB == null || pA.length < 3 || pB.length < 3) {
            return;
        }
        double[] pBnew = new double[] {
                worldAxisMetres(pA, 0),
                worldAxisMetres(pA, 1),
                worldAxisMetres(pA, 2)
        };
        pBnew[a0] = worldAxisMetres(pA, a0) + frame.chordUx * frame.journalSepM;
        pBnew[a1] = worldAxisMetres(pA, a1) + frame.chordUy * frame.journalSepM;
        pBnew[dropped] = worldAxisMetres(pA, dropped);
        double dx = pBnew[0] - pB[0];
        double dy = pBnew[1] - pB[1];
        double dz = pBnew[2] - pB[2];
        positions.put(Integer.valueOf(frame.companionId), pBnew);
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            if (id == frame.primaryId || isWideBinaryPrimaryBranchBody(id, frame.primaryId, bodies)) {
                continue;
            }
            double[] p = positions.get(e.getKey());
            if (p == null || p.length < 3) {
                continue;
            }
            positions.put(e.getKey(), new double[] { p[0] + dx, p[1] + dy, p[2] + dz });
        }
        shiftSyntheticBarycentrePositionsXYZ(positions, dx, dy, dz);
        if (!isHierarchicalWideBinary(bodies)) {
            recenterBinaryBarycentreInMapPlane(positions, bodies, mapProjA0, mapProjA1);
        }
    }

    private static void shiftSyntheticBarycentrePositionsXYZ(Map<Integer, double[]> positions,
            double dx,
            double dy,
            double dz) {
        if (positions == null) {
            return;
        }
        for (Map.Entry<Integer, double[]> e : positions.entrySet()) {
            if (e.getKey() == null || e.getKey().intValue() >= 0) {
                continue;
            }
            double[] p = e.getValue();
            if (p == null || p.length < 3) {
                continue;
            }
            positions.put(e.getKey(), new double[] { p[0] + dx, p[1] + dy, p[2] + dz });
        }
    }

    private static boolean isDescendantOf(int ancestorId, int bodyId, Map<Integer, BodyInfo> bodies) {
        if (bodies == null || ancestorId < 0 || bodyId < 0) {
            return false;
        }
        int cur = bodyId;
        for (int hop = 0; hop < 64; hop++) {
            if (cur == ancestorId) {
                return true;
            }
            BodyInfo b = bodies.get(Integer.valueOf(cur));
            if (b == null) {
                return false;
            }
            int p = resolveOrbitParentBodyId(b, bodies, cur);
            if (p < 0) {
                return false;
            }
            cur = p;
        }
        return false;
    }
}
