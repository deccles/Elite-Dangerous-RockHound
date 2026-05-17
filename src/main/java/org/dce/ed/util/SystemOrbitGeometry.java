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
        Instant t = now != null ? now : Instant.now();
        for (Integer id : bodies.keySet()) {
            if (id != null) {
                positionRecursive(id.intValue(), bodies, memo, visiting, t, freezeBarycentreStars);
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
                } else if (!isMapStellarBody(b) && isPlanetBinaryBarycentreMapKey(pId)) {
                    rel = planetBinaryOffsetFromBarycentreMetres(b, id, bodies,
                            journalNullIdFromPlanetBinaryBarycentreMapKey(pId), t, p0, p1, freezeBarycentreStars,
                            memo.get(Integer.valueOf(central)), parentPos);
                } else {
                    rel = orbitalDisplacementMetres(b, id, t, bodies, freezeBarycentreStars);
                    if (!isFiniteXYZ(rel)) {
                        rel = pseudoOffsetMetresAtTime(b, id, bodies, pId, t, freezeBarycentreStars);
                    }
                }
                memo.put(key, new double[] {
                        parentPos[0] + rel[0],
                        parentPos[1] + rel[1],
                        parentPos[2] + rel[2]
                });
                placed = true;
            }
        } while (placed);
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
        if (countMapStellarBodies(bodies) < 2) {
            return starAnchoredPositions;
        }
        int p0 = clampWorldAxisIndex(mapProjA0);
        int p1 = clampWorldAxisIndex(mapProjA1);
        if (p0 == p1) {
            p1 = p0 == 2 ? 1 : 2;
        }
        Map<Integer, double[]> memo = new HashMap<>();
        for (Integer sid : barycentricMapStellarIds(bodies)) {
            double[] sp = starAnchoredPositions.get(sid);
            if (sp != null && sp.length >= 3 && isFiniteXYZ(sp)) {
                memo.put(sid, new double[] {
                        worldAxisMetres(sp, 0),
                        worldAxisMetres(sp, 1),
                        worldAxisMetres(sp, 2)
                });
            }
        }
        if (memo.size() < 2) {
            return starAnchoredPositions;
        }
        Instant t = now != null ? now : Instant.now();
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
                if (isMapStellarBody(b) && resolveOrbitParentBodyId(b, bodies, id) < 0) {
                    continue;
                }
                int pId = resolveOrbitParentBodyId(b, bodies, id);
                if (pId < 0 || !bodies.containsKey(Integer.valueOf(pId))) {
                    continue;
                }
                double[] parentPos = memo.get(Integer.valueOf(pId));
                if (parentPos == null) {
                    continue;
                }
                double[] rel;
                int branchStar = branchSchematicStarParentId(bodies, pId);
                if (branchStar >= 0 && pId == branchStar && !isMapStellarBody(b)) {
                    rel = schematicMapPlaneOffsetMetres(b, id, bodies, branchStar, t, p0, p1, freezeBarycentreStars);
                } else {
                    rel = orbitalDisplacementMetres(b, id, t, bodies, freezeBarycentreStars);
                    if (!isFiniteXYZ(rel)) {
                        rel = pseudoOffsetMetresAtTime(b, id, bodies, pId, t, freezeBarycentreStars);
                    }
                }
                if (!isFiniteXYZ(rel)) {
                    continue;
                }
                memo.put(key, new double[] {
                        parentPos[0] + rel[0],
                        parentPos[1] + rel[1],
                        parentPos[2] + rel[2]
                });
                placed = true;
            }
        } while (placed);
        for (Map.Entry<Integer, double[]> e : starAnchoredPositions.entrySet()) {
            if (e.getKey() != null && e.getValue() != null && !memo.containsKey(e.getKey())) {
                double[] p = e.getValue();
                if (p.length >= 3 && isFiniteXYZ(p)) {
                    memo.put(e.getKey(), new double[] {
                            worldAxisMetres(p, 0),
                            worldAxisMetres(p, 1),
                            worldAxisMetres(p, 2)
                    });
                }
            }
        }
        return memo;
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
            .compile("([A-Za-z])\\s+(\\d+)(?:\\s+([a-z]+))?\\s*$");

    /** Minimum X/Y separation from parent (m) before drawing a fallback circular orbit. */
    private static final double MIN_FALLBACK_ORBIT_RADIUS_METRES = 50.0;

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
        final double targetChordPx = 4.0;
        int n = (int) Math.ceil(circPx / targetChordPx);
        int softMin = circPx < 140.0 ? 24 : 36;
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
        if (bodies == null || bodies.isEmpty() || bodyWorldPositions == null || bodyWorldPositions.isEmpty()) {
            return Collections.emptyList();
        }
        int p0 = clampWorldAxisIndex(proj0);
        int p1 = clampWorldAxisIndex(proj1);
        if (p0 == p1) {
            p1 = p0 == 2 ? 1 : 2;
        }
        int legacyN = Math.max(12, Math.min(ORBIT_POLYLINE_SEGMENTS_HARD_MAX, segments));
        boolean useScreenChord = Double.isFinite(scalePixelsPerMetre) && scalePixelsPerMetre > 0.0;
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
            int pId = resolveOrbitParentBodyId(b, bodies, bodyId);
            if (pId < 0 && !isMapStellarBody(b) && !isPlanetBinaryBarycentreMapKey(pId)) {
                int primary = primaryAnchorBodyMapKey(bodies);
                if (primary >= 0 && primary != bodyId) {
                    pId = primary;
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
            if (bodyId == primaryAnch && (pId >= 0
                    || (pId < 0 && isMapStellarBody(b) && isSingleStarSchematicMap(bodies)))) {
                continue;
            }
            if (isSingleStarSchematicMap(bodies) && bodyId == schematicCentralStarMapKey(bodies)) {
                continue;
            }
            if (!isMapStellarBody(b)) {
                int branchStar = branchSchematicStarParentId(bodies, pId);
                if (branchStar >= 0) {
                    continue;
                }
                if (isPlanetBinaryBarycentreMapKey(pId)) {
                    continue;
                }
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
            boolean haveKepler = isMapStellarBody(b)
                    && aObj != null
                    && aObj.doubleValue() > 0
                    && !Double.isNaN(aObj.doubleValue());
            if (haveKepler && !keplerOrbitPolylineMatchesSchematicPlacement(b, pId, bodies, bodyId, bodyPos,
                    parentPos, p0, p1)) {
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
                    double rad = schematicOrbitRadiusMetres(b, pId, bodies, bodyId, bodyPos, parentPos, p0, p1);
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

            double[] wx = new double[n];
            double[] wy = new double[n];

            if (haveKepler) {
                for (int i = 0; i < n; i++) {
                    double M = (Math.PI * 2.0 * i) / n;
                    double[] rel = keplerDisplacementMetres(b, M);
                    if (rel == null || rel.length < needLen) {
                        wx = null;
                        break;
                    }
                    if (pId < 0) {
                        rel = reconcileOrbitalDisplacementWithJournalHint(b, pId, bodies, bodyId, rel,
                                Instant.now());
                    }
                    wx[i] = worldAxisMetres(parentPos, p0) + worldAxisMetres(rel, p0);
                    wy[i] = worldAxisMetres(parentPos, p1) + worldAxisMetres(rel, p1);
                }
            } else {
                double rad = schematicOrbitRadiusMetres(b, pId, bodies, bodyId, bodyPos, parentPos, p0, p1);
                if (!Double.isFinite(rad) || rad < MIN_FALLBACK_ORBIT_RADIUS_METRES) {
                    continue;
                }
                fallbackRadMetres = rad;
                double pcx = worldAxisMetres(parentPos, p0);
                double pcy = worldAxisMetres(parentPos, p1);
                for (int i = 0; i < n; i++) {
                    double theta = (Math.PI * 2.0 * i) / n;
                    wx[i] = pcx + rad * Math.cos(theta);
                    wy[i] = pcy + rad * Math.sin(theta);
                }
            }

            if (wx != null) {
                String curveKey = orbitCurveShapeKey(pId, b, haveKepler, fallbackRadMetres);
                if (curveKey != null && !seenOrbitCurveKeys.add(curveKey)) {
                    continue;
                }
                out.add(new OrbitPolylineWorldXY(bodyId, wx, wy));
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
                    scalePixelsPerMetre);
        }
        if (countMapStellarBodies(bodies) >= 2) {
            appendBranchStarSchematicRings(merged, bodies, bodyWorldPositions, p0, p1, legacyN, useScreenChord,
                    scalePixelsPerMetre);
        } else if (shouldApplyLoneStarSchematicLayout(bodies)) {
            int central = schematicCentralStarMapKey(bodies);
            appendSchematicRingsAtStar(merged, bodies, bodyWorldPositions, central, p0, p1,
                    legacyN, useScreenChord, scalePixelsPerMetre);
            appendPlanetBinaryBarycentreRingsAtStar(merged, bodies, bodyWorldPositions, central, p0, p1,
                    legacyN, useScreenChord, scalePixelsPerMetre);
            appendPlanetBinaryMutualOrbitRings(merged, bodies, bodyWorldPositions, p0, p1, legacyN, useScreenChord,
                    scalePixelsPerMetre);
        }
        return dedupeOrbitPolylinesUntilStable(merged);
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
            double scalePixelsPerMetre) {
        if (out == null || bodies == null || bodies.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || !isMapStellarBody(e.getValue())) {
                continue;
            }
            int starId = e.getKey().intValue();
            if (resolveOrbitParentBodyId(e.getValue(), bodies, starId) >= 0) {
                continue;
            }
            appendSchematicRingsAtStar(out, bodies, bodyWorldPositions, starId, p0, p1, legacyN, useScreenChord,
                    scalePixelsPerMetre);
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
            double scalePixelsPerMetre) {
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
            double radM = schematicOrbitRadiusMetres(e.getValue(), starId, bodies, id, bodyPos, starPos, p0, p1);
            if (!Double.isFinite(radM) || radM < MIN_FALLBACK_ORBIT_RADIUS_METRES) {
                continue;
            }
            radiiLs.add(Long.valueOf(Math.max(1L, Math.round(radM / LIGHT_SECOND_METRES))));
        }
        for (Long lsRounded : radiiLs) {
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
        if (parent == null || !isMapStellarBody(parent)) {
            return -1;
        }
        if (resolveOrbitParentBodyId(parent, bodies, parentMapId) >= 0) {
            return -1;
        }
        if (countMapStellarBodies(bodies) >= 2) {
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
            double scalePixelsPerMetre) {
        if (out == null || bodies == null || bodies.isEmpty() || bodyWorldPositions == null) {
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
            if (resolveOrbitParentBodyId(e.getValue(), bodies, id) >= 0) {
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
        for (int i = 0; i < n; i++) {
            double theta = (Math.PI * 2.0 * i) / n;
            wx[i] = cx + radiusM * Math.cos(theta);
            wy[i] = cy + radiusM * Math.sin(theta);
        }
        out.add(new OrbitPolylineWorldXY(BINARY_BARYCENTRE_ORBIT_RING_BODY_ID, wx, wy));
    }

    /**
     * Stable key for one logical closed orbit: same parent plus Kepler shape (inclination / nodes / periapsis matter
     * for the projected ellipse) or, for the circular fallback, parent plus radius. Multiple bodies can map to one
     * curve (co-orbital data, twin fallback circles), which would otherwise be stroked twice.
     */
    private static String orbitCurveShapeKey(int parentBodyId, BodyInfo b, boolean kepler, double fallbackRadiusM) {
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
            return "K:" + parentBodyId + ":" + ak + ":" + ek + ":" + inc + ":" + om + ":" + wp;
        }
        if (!Double.isFinite(fallbackRadiusM)) {
            return null;
        }
        long rkLs = Math.max(1L, Math.round(fallbackRadiusM / LIGHT_SECOND_METRES));
        return "C:" + parentBodyId + ":" + rkLs;
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

    /** Schematic rings ({@code bodyId < 0}) only dedupe with the same synthetic id — not planet strokes or the barycentre ring. */
    private static boolean orbitPolylinesEligibleForDedupe(OrbitPolylineWorldXY a, OrbitPolylineWorldXY b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.bodyId < 0 || b.bodyId < 0) {
            return a.bodyId == b.bodyId;
        }
        return true;
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
        if (child == null || bodies == null || bodies.isEmpty()) {
            return -1;
        }
        /*
         * Wide-binary companion stars must orbit the barycentre (parent -1), not the primary star. Cache rows often
         * declare parent = primary body id; the primary-anchor fallback would also attach B to A and spin the frame.
         */
        if (isMapStellarBody(child) && isWideBinaryCompanionStar(child, bodies, mapBodyId)) {
            return -1;
        }
        int declared = child.getImmediateParentBodyId();
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
            if (!isMapStellarBody(child)) {
                int inferredNull = inferPlanetBinaryNullParentId(child, bodies, mapBodyId);
                if (inferredNull > 0) {
                    return planetBinaryBarycentreMapKey(inferredNull);
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
        if (isSingleStarSchematicMap(bodies)) {
            return true;
        }
        int central = schematicCentralStarMapKey(bodies);
        if (central < 0) {
            return false;
        }
        if (hasPlanetBinaryNullParentInSystem(bodies)) {
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
            if (b == null || isMapStellarBody(b) || isMoonSatelliteBody(b)) {
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

    /** Same rule as System tab Body column {@code *} (short name equals system name). */
    public static boolean isPrimaryStarBodyByName(BodyInfo b) {
        if (b == null) {
            return false;
        }
        String shortName = b.getShortName();
        return shortName != null
                && b.getStarSystem() != null
                && !b.getStarSystem().isBlank()
                && shortName.trim().equals(b.getStarSystem().trim());
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
     * @return parent designation string (digits only), e.g. {@code "1"} for moon {@code "1 a"}, or {@code null}
     */
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
        if (parts.length >= 2) {
            String last = parts[parts.length - 1];
            String prev = parts[parts.length - 2];
            if (prev.matches("\\d+") && last.length() == 1 && Character.isLetter(last.charAt(0))) {
                return prev;
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
        if (b == null) {
            return false;
        }
        String s = firstNonBlank(b.getShortName(), b.getBodyName());
        if (s == null) {
            return false;
        }
        s = s.trim();
        Matcher compact = MOON_DESIGNATION.matcher(s);
        if (compact.matches()) {
            return true;
        }
        String[] parts = s.split("\\s+");
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
            if (isMapStellarBody(b) || isMoonSatelliteBody(b)) {
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
        BodyInfo best = null;
        int bestKey = Integer.MAX_VALUE;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            BodyInfo b = e.getValue();
            if (isMapStellarBody(b) || isMoonSatelliteBody(b)) {
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
            if (isMapStellarBody(b) || isMoonSatelliteBody(b)) {
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
        if (bodies == null || memo == null || centralStarId < 0) {
            return;
        }
        HashSet<Integer> nullParents = new HashSet<>();
        for (BodyInfo b : bodies.values()) {
            if (b == null || isMapStellarBody(b) || isMoonSatelliteBody(b)) {
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
            if (memo.containsKey(Integer.valueOf(bKey))) {
                continue;
            }
            double distLs = planetBinaryBarycentreDistanceLsFromStar(nullId, bodies);
            BodyInfo outer = planetBinaryOuterOrbitalSource(nullId, bodies);
            BodyInfo ref = firstPlanetBinarySibling(nullId, bodies);
            if (ref == null || !Double.isFinite(distLs) || distLs <= 0.0) {
                continue;
            }
            BodyInfo angleSource = outer != null ? outer : ref;
            double[] rel = schematicMapPlaneOffsetMetresAtHintLs(angleSource, distLs, t, p0, p1,
                    freezeBarycentreStars);
            memo.put(Integer.valueOf(bKey), new double[] {
                    starPos[0] + rel[0],
                    starPos[1] + rel[1],
                    starPos[2] + rel[2]
            });
        }
    }

    /**
     * Schematic radius of the mutual orbit around a planet-binary barycentre: max {@code |d_child − d_bary|} from
     * heliocentric FSS distances, or half the widest pair separation when those collapse to zero.
     */
    private static double planetBinaryMutualOrbitRadiusLs(int journalNullParentId, Map<Integer, BodyInfo> bodies) {
        double dBary = planetBinaryBarycentreDistanceLsFromStar(journalNullParentId, bodies);
        double maxRadial = 0.0;
        double minD = Double.POSITIVE_INFINITY;
        double maxD = Double.NEGATIVE_INFINITY;
        int n = 0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            BodyInfo b = e.getValue();
            if (isMapStellarBody(b) || isMoonSatelliteBody(b)
                    || b.getImmediateParentBodyId() != journalNullParentId) {
                continue;
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
        return Math.max(maxRadial, 1.0);
    }

    private static int planetBinarySiblingOrderIndex(int journalNullParentId, int mapBodyId,
            Map<Integer, BodyInfo> bodies) {
        int idx = 0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            BodyInfo b = e.getValue();
            if (isMapStellarBody(b) || isMoonSatelliteBody(b)
                    || b.getImmediateParentBodyId() != journalNullParentId) {
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
        double dBary = planetBinaryBarycentreDistanceLsFromStar(journalNullId, bodies);
        double dC = child.getDistanceLs();
        double radialLs;
        if (Double.isFinite(dC) && Double.isFinite(dBary) && dC > 0.0) {
            radialLs = Math.max(0.5, Math.abs(dC - dBary));
        } else {
            radialLs = planetBinaryMutualOrbitRadiusLs(journalNullId, bodies);
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
            if (isMapStellarBody(other) || isMoonSatelliteBody(other)) {
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
            double scalePixelsPerMetre) {
        if (out == null || bodies == null || bodyWorldPositions == null || starId < 0) {
            return;
        }
        HashSet<Integer> nullParents = new HashSet<>();
        for (BodyInfo b : bodies.values()) {
            if (b == null || isMapStellarBody(b) || isMoonSatelliteBody(b)) {
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
        double cx = worldAxisMetres(starPos, p0);
        double cy = worldAxisMetres(starPos, p1);
        if (!Double.isFinite(cx) || !Double.isFinite(cy)) {
            return;
        }
        for (Integer nullIdObj : nullParents) {
            int nullId = nullIdObj.intValue();
            if (!isPlanetBinaryNullParentId(nullId, bodies)) {
                continue;
            }
            double distLs = planetBinaryBarycentreDistanceLsFromStar(nullId, bodies);
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
            for (int i = 0; i < n; i++) {
                double theta = (Math.PI * 2.0 * i) / n;
                wx[i] = cx + radM * Math.cos(theta);
                wy[i] = cy + radM * Math.sin(theta);
            }
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
            double scalePixelsPerMetre) {
        if (out == null || bodies == null || bodyWorldPositions == null) {
            return;
        }
        HashSet<Integer> nullParents = new HashSet<>();
        for (BodyInfo b : bodies.values()) {
            if (b == null || isMapStellarBody(b) || isMoonSatelliteBody(b)) {
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
            if (!isPlanetBinaryNullParentId(nullId, bodies)) {
                continue;
            }
            int bKey = planetBinaryBarycentreMapKey(nullId);
            double[] baryPos = bodyWorldPositions.get(Integer.valueOf(bKey));
            if (baryPos == null || baryPos.length < needLen) {
                continue;
            }
            double cx = worldAxisMetres(baryPos, p0);
            double cy = worldAxisMetres(baryPos, p1);
            if (!Double.isFinite(cx) || !Double.isFinite(cy)) {
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
            for (int i = 0; i < n; i++) {
                double theta = (Math.PI * 2.0 * i) / n;
                wx[i] = cx + radM * Math.cos(theta);
                wy[i] = cy + radM * Math.sin(theta);
            }
            out.add(new OrbitPolylineWorldXY(PLANET_BINARY_MUTUAL_ORBIT_RING_ID_BASE - nullId, wx, wy));
        }
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
    private static boolean hasPlanetarySurfaceOrAtmosphere(BodyInfo b) {
        if (b == null) {
            return false;
        }
        String pc = b.getPlanetClass();
        if (pc != null && !pc.isBlank()) {
            return true;
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
        if (hasPlanetarySurfaceOrAtmosphere(b)) {
            return false;
        }
        if (isStellarBody(b)) {
            return true;
        }
        /* Primary row often keeps the system name as short name until FSS fills {@code starType}. */
        if (isPrimaryStarBodyByName(b)) {
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
            if (resolveOrbitParentBodyId(e.getValue(), bodies, e.getKey().intValue()) < 0) {
                ids.add(e.getKey());
            }
        }
        return ids;
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
        if (stars.size() < 2) {
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
        if (n < 2) {
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
     * Schematic ring radius for map strokes: journal parent-relative distance when known, else projected separation
     * from the parent's current position.
     */
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

        public OrbitPolylineWorldXY(int bodyId, double[] wx, double[] wy) {
            this.bodyId = bodyId;
            this.wx = wx;
            this.wy = wy;
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
        int primaryId = primaryAnchorBodyMapKey(bodies);
        if (primaryId < 0 || mapBodyId == primaryId) {
            return false;
        }
        BodyInfo primary = bodies.get(Integer.valueOf(primaryId));
        if (primary == null || !isMapStellarBody(primary)) {
            return false;
        }
        double dC = child.getDistanceLs();
        double dP = primary.getDistanceLs();
        return Double.isFinite(dC) && Double.isFinite(dP)
                && Math.abs(dC - dP) >= WIDE_BINARY_MIN_JOURNAL_SEP_LS;
    }

    private static boolean isBarycentreOrbitingStar(BodyInfo b, Map<Integer, BodyInfo> bodies, int mapBodyId) {
        return isMapStellarBody(b) && resolveOrbitParentBodyId(b, bodies, mapBodyId) < 0;
    }

    private static double[] positionRecursive(int bodyId,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> memo,
            Set<Integer> visiting,
            Instant now,
            boolean freezeBarycentreStars) {

        Integer key = Integer.valueOf(bodyId);
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        if (visiting.contains(key)) {
            return new double[] { 0, 0, 0 };
        }

        BodyInfo b = bodies.get(key);
        if (b == null) {
            if (isPlanetBinaryBarycentreMapKey(bodyId)) {
                if (memo.containsKey(key)) {
                    return memo.get(key);
                }
                if (shouldApplyLoneStarSchematicLayout(bodies)) {
                    int central = schematicCentralStarMapKey(bodies);
                    int nullId = journalNullIdFromPlanetBinaryBarycentreMapKey(bodyId);
                    double distLs = planetBinaryBarycentreDistanceLsFromStar(nullId, bodies);
                    BodyInfo outer = planetBinaryOuterOrbitalSource(nullId, bodies);
                    BodyInfo ref = firstPlanetBinarySibling(nullId, bodies);
                    if (central >= 0 && ref != null && Double.isFinite(distLs) && distLs > 0.0) {
                        double[] starPos = positionRecursive(central, bodies, memo, visiting, now, freezeBarycentreStars);
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

        if (shouldApplyLoneStarSchematicLayout(bodies) && bodyId == schematicCentralStarMapKey(bodies)) {
            double[] origin = new double[] { 0.0, 0.0, 0.0 };
            memo.put(key, origin);
            return origin;
        }

        visiting.add(key);
        try {
            int pId = resolveOrbitParentBodyId(b, bodies, bodyId);
            if (shouldApplyLoneStarSchematicLayout(bodies) && !isMapStellarBody(b)) {
                int central = schematicCentralStarMapKey(bodies);
                if (central >= 0 && !isPlanetBinaryBarycentreMapKey(pId)
                        && (pId < 0 || !bodies.containsKey(Integer.valueOf(pId)))) {
                    pId = central;
                }
            }
            double[] parentPos;
            if (isPlanetBinaryBarycentreMapKey(pId)) {
                parentPos = positionRecursive(pId, bodies, memo, visiting, now, freezeBarycentreStars);
            } else if (pId < 0 || !bodies.containsKey(Integer.valueOf(pId))) {
                parentPos = new double[] { 0, 0, 0 };
            } else {
                parentPos = positionRecursive(pId, bodies, memo, visiting, now, freezeBarycentreStars);
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
        Double aObj = b.getSemiMajorAxisM();
        if (aObj == null || aObj.doubleValue() <= 0 || Double.isNaN(aObj.doubleValue())) {
            return null;
        }
        double a = aObj.doubleValue();
        double e = (b.getEccentricity() != null && !Double.isNaN(b.getEccentricity()))
                ? clamp(b.getEccentricity().doubleValue(), 0, 0.999999)
                : 0;

        double inc = angleRad(b.getOrbitalInclination());
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

    /** Wide binaries: journal A–B separation often lies mostly on the axis dropped by the 2D map projection. */
    private static final double WIDE_BINARY_MIN_JOURNAL_SEP_LS = 500.0;
    private static final double WIDE_BINARY_PROJ_SEP_MIN_FRAC_OF_JOURNAL = 0.95;

    /**
     * After {@code mapProjA0}/{@code mapProjA1} are chosen, move the companion barycentre star (and its subtree) so
     * projected A–B separation matches journal {@code |Δ DistanceFromArrivalLS|}. Keeps on-screen cluster scale ratios
     * aligned with journal (e.g. A 6 at ~258 Ls vs A–B ~3305 Ls).
     */
    public static void flattenWideBinaryIntoMapPlane(Map<Integer, double[]> positions, Map<Integer, BodyInfo> bodies,
            int mapProjA0, int mapProjA1) {
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
        int companionId = -1;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            if (id == primaryId || !isMapStellarBody(e.getValue())) {
                continue;
            }
            if (resolveOrbitParentBodyId(e.getValue(), bodies, id) >= 0) {
                continue;
            }
            companionId = id;
            break;
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
        double dP = primary.getDistanceLs();
        double dC = companion.getDistanceLs();
        if (!Double.isFinite(dP) || !Double.isFinite(dC)) {
            return;
        }
        double journalSepM = Math.abs(dC - dP) * LIGHT_SECOND_METRES;
        if (journalSepM < WIDE_BINARY_MIN_JOURNAL_SEP_LS * LIGHT_SECOND_METRES) {
            return;
        }
        double dPlane0 = worldAxisMetres(pB, a0) - worldAxisMetres(pA, a0);
        double dPlane1 = worldAxisMetres(pB, a1) - worldAxisMetres(pA, a1);
        double projSepM = Math.hypot(dPlane0, dPlane1);
        if (!Double.isFinite(projSepM) || projSepM >= journalSepM * WIDE_BINARY_PROJ_SEP_MIN_FRAC_OF_JOURNAL) {
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
        double[] pBnew = new double[] {
                worldAxisMetres(pA, 0),
                worldAxisMetres(pA, 1),
                worldAxisMetres(pA, 2)
        };
        pBnew[a0] = worldAxisMetres(pA, a0) + ux * journalSepM;
        pBnew[a1] = worldAxisMetres(pA, a1) + uy * journalSepM;
        pBnew[dropped] = worldAxisMetres(pA, dropped);
        double dx = pBnew[0] - pB[0];
        double dy = pBnew[1] - pB[1];
        double dz = pBnew[2] - pB[2];
        positions.put(Integer.valueOf(companionId), pBnew);
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            if (id == companionId || !isDescendantOf(companionId, id, bodies)) {
                continue;
            }
            double[] p = positions.get(e.getKey());
            if (p == null || p.length < 3) {
                continue;
            }
            positions.put(e.getKey(), new double[] { p[0] + dx, p[1] + dy, p[2] + dz });
        }
        recenterBinaryBarycentreInMapPlane(positions, bodies, mapProjA0, mapProjA1);
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
        int companionId = -1;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            if (id == primaryId || !isMapStellarBody(e.getValue())) {
                continue;
            }
            if (resolveOrbitParentBodyId(e.getValue(), bodies, id) >= 0) {
                continue;
            }
            companionId = id;
            break;
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
        double dP = primary.getDistanceLs();
        double dC = companion.getDistanceLs();
        if (!Double.isFinite(dP) || !Double.isFinite(dC)) {
            return null;
        }
        double journalSepM = Math.abs(dC - dP) * LIGHT_SECOND_METRES;
        if (journalSepM < WIDE_BINARY_MIN_JOURNAL_SEP_LS * LIGHT_SECOND_METRES) {
            return null;
        }
        double dPlane0 = worldAxisMetres(pB, a0) - worldAxisMetres(pA, a0);
        double dPlane1 = worldAxisMetres(pB, a1) - worldAxisMetres(pA, a1);
        double projSepM = Math.hypot(dPlane0, dPlane1);
        if (!Double.isFinite(projSepM) || projSepM < LIGHT_SECOND_METRES) {
            return null;
        }
        return new WideBinaryFlattenFrame(primaryId, companionId, dPlane0 / projSepM, dPlane1 / projSepM, journalSepM);
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
            if (id == frame.companionId || !isDescendantOf(frame.companionId, id, bodies)) {
                continue;
            }
            double[] p = positions.get(e.getKey());
            if (p == null || p.length < 3) {
                continue;
            }
            positions.put(e.getKey(), new double[] { p[0] + dx, p[1] + dy, p[2] + dz });
        }
        recenterBinaryBarycentreInMapPlane(positions, bodies, mapProjA0, mapProjA1);
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
