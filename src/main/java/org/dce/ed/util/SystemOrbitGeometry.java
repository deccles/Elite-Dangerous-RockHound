package org.dce.ed.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * Same as {@link #bodyPositionsMetres(Map)} but mean anomaly is evolved to {@code now} when
     * {@link BodyInfo#getOrbitalEpochMillis()}, {@link BodyInfo#getOrbitalPeriod()}, and semi-major axis exist.
     */
    public static Map<Integer, double[]> bodyPositionsMetres(Map<Integer, BodyInfo> bodies, Instant now) {
        Map<Integer, double[]> memo = new HashMap<>();
        Set<Integer> visiting = new HashSet<>();
        if (bodies == null || bodies.isEmpty()) {
            return memo;
        }
        Instant t = now != null ? now : Instant.now();
        for (Integer id : bodies.keySet()) {
            if (id != null) {
                positionRecursive(id.intValue(), bodies, memo, visiting, t);
            }
        }
        return memo;
    }

    /** {@code "1 a"}, {@code "1a"}, {@code "12 a"} → parent designation {@code "1"} / {@code "12"}; plain {@code "1"} → no match. */
    private static final Pattern MOON_DESIGNATION = Pattern.compile("^(\\d+)\\s*([A-Za-z])\\s*$");

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
        final double targetChordPx = 5.0;
        int n = (int) Math.ceil(circPx / targetChordPx);
        int softMin = circPx < 140.0 ? 12 : 24;
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
        return orbitPolylinesWorldMetresXY(bodies, bodyWorldPositions, segments, Double.NaN);
    }

    /** Upper clamp for per-orbit vertex count (must stay within {@link #orbitPolylinesWorldMetresXY} loop budget). */
    public static final int ORBIT_POLYLINE_SEGMENTS_HARD_MAX = 768;

    public static List<OrbitPolylineWorldXY> orbitPolylinesWorldMetresXY(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions,
            int segments,
            double scalePixelsPerMetre) {
        if (bodies == null || bodies.isEmpty() || bodyWorldPositions == null || bodyWorldPositions.isEmpty()) {
            return Collections.emptyList();
        }
        int legacyN = Math.max(12, Math.min(ORBIT_POLYLINE_SEGMENTS_HARD_MAX, segments));
        boolean useScreenChord = Double.isFinite(scalePixelsPerMetre) && scalePixelsPerMetre > 0.0;
        List<OrbitPolylineWorldXY> out = new ArrayList<>();
        Set<String> seenOrbitCurveKeys = new HashSet<>();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int bodyId = e.getKey().intValue();
            if (bodyId == 0) {
                continue;
            }
            BodyInfo b = e.getValue();
            double[] bodyPos = bodyWorldPositions.get(Integer.valueOf(bodyId));
            if (bodyPos == null || bodyPos.length < 2) {
                continue;
            }

            int pId = resolveOrbitParentBodyId(b, bodies);
            if (pId < 0) {
                continue;
            }
            double[] parentPos = resolveParentWorldMetres(pId, bodies, bodyWorldPositions);
            if (parentPos == null) {
                continue;
            }

            Double aObj = b.getSemiMajorAxisM();
            boolean haveKepler = aObj != null && aObj.doubleValue() > 0 && !Double.isNaN(aObj.doubleValue());

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
                    double dx = bodyPos[0] - parentPos[0];
                    double dy = bodyPos[1] - parentPos[1];
                    double rad = Math.hypot(dx, dy);
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
                    if (rel == null) {
                        wx = null;
                        break;
                    }
                    wx[i] = parentPos[0] + rel[0];
                    wy[i] = parentPos[1] + rel[1];
                }
            } else {
                double dx = bodyPos[0] - parentPos[0];
                double dy = bodyPos[1] - parentPos[1];
                double rad = Math.hypot(dx, dy);
                if (!Double.isFinite(rad) || rad < MIN_FALLBACK_ORBIT_RADIUS_METRES) {
                    continue;
                }
                fallbackRadMetres = rad;
                for (int i = 0; i < n; i++) {
                    double theta = (Math.PI * 2.0 * i) / n;
                    wx[i] = parentPos[0] + rad * Math.cos(theta);
                    wy[i] = parentPos[1] + rad * Math.sin(theta);
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
        /* Two different journal ids can still share one curve after uniq; compare again without body-id coupling. */
        return dedupeOrbitPolylinesUntilStable(new ArrayList<>(uniq.values()));
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
        long rk = Math.round(fallbackRadiusM / 100.0);
        return "C:" + parentBodyId + ":" + rk;
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
     */
    public static int resolveOrbitParentBodyId(BodyInfo child, Map<Integer, BodyInfo> bodies) {
        if (child == null || bodies == null || bodies.isEmpty()) {
            return -1;
        }
        int declared = child.getImmediateParentBodyId();
        if (declared >= 0 && bodies.containsKey(Integer.valueOf(declared))) {
            return declared;
        }
        String moonParentDesig = moonParentDesignationFromName(child);
        if (moonParentDesig != null) {
            int inferred = findBodyIdByDesignation(bodies, moonParentDesig);
            if (inferred >= 0) {
                return inferred;
            }
        }
        int primary = primaryStarBodyId(bodies);
        if (primary >= 0 && child.getBodyId() != primary) {
            return primary;
        }
        return -1;
    }

    private static int primaryStarBodyId(Map<Integer, BodyInfo> bodies) {
        if (bodies.containsKey(Integer.valueOf(0))) {
            return 0;
        }
        int best = -1;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (e.getValue().getStarType() != null && !e.getValue().getStarType().isBlank()) {
                int id = e.getKey().intValue();
                if (id >= 0 && (best < 0 || id < best)) {
                    best = id;
                }
            }
        }
        return best >= 0 ? best : 0;
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
        if (designation == null || designation.isBlank()) {
            return -1;
        }
        String d = designation.trim();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String sn = e.getValue().getShortName();
            if (sn != null && sn.trim().equals(d)) {
                return e.getKey().intValue();
            }
        }
        return -1;
    }

    /**
     * World position of parent body {@code pId}, or {@code null} if missing from the position map.
     */
    private static double[] resolveParentWorldMetres(int pId,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> bodyWorldPositions) {
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

    private static double[] positionRecursive(int bodyId,
            Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> memo,
            Set<Integer> visiting,
            Instant now) {

        Integer key = Integer.valueOf(bodyId);
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        if (visiting.contains(key)) {
            return new double[] { 0, 0, 0 };
        }

        BodyInfo b = bodies.get(key);
        if (b == null) {
            double[] z = new double[] { 0, 0, 0 };
            memo.put(key, z);
            return z;
        }

        visiting.add(key);
        try {
            int pId = resolveOrbitParentBodyId(b, bodies);
            double[] parentPos;
            if (pId < 0 || !bodies.containsKey(Integer.valueOf(pId))) {
                parentPos = new double[] { 0, 0, 0 };
            } else {
                parentPos = positionRecursive(pId, bodies, memo, visiting, now);
            }

            double[] rel = orbitalDisplacementMetres(b, now);
            double[] out = new double[] {
                    parentPos[0] + rel[0],
                    parentPos[1] + rel[1],
                    parentPos[2] + rel[2]
            };
            memo.put(key, out);
            return out;
        } finally {
            visiting.remove(key);
        }
    }

    static double[] orbitalDisplacementMetres(BodyInfo b, Instant now) {
        Double aObj = b.getSemiMajorAxisM();
        if (aObj != null && aObj.doubleValue() > 0 && !Double.isNaN(aObj.doubleValue())) {
            double M = evolvedMeanAnomalyRadians(b, now);
            double[] k = keplerDisplacementMetres(b, M);
            if (k != null) {
                return k;
            }
        }

        return pseudoOffsetMetres(b);
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
        return new double[] { x, y, z };
    }

    /**
     * Mean anomaly at {@code now}: {@code M = M0 + n Δt} with {@code n = 2π/P}, journal {@code OrbitalPeriod}
     * in seconds, {@code M0} from {@link BodyInfo#getMeanAnomaly()}, epoch from {@link BodyInfo#getOrbitalEpochMillis()}.
     * If epoch or period is missing, returns angle from {@link #angleRad(Double)} on stored mean anomaly only.
     */
    static double evolvedMeanAnomalyRadians(BodyInfo b, Instant now) {
        double M0 = angleRad(b.getMeanAnomaly());
        Double pSecObj = b.getOrbitalPeriod();
        Long epochMs = b.getOrbitalEpochMillis();
        Instant t = now != null ? now : Instant.now();
        if (epochMs != null && pSecObj != null) {
            double pSec = pSecObj.doubleValue();
            if (pSec > 1e-6 && epochMs.longValue() > 0L) {
                double dtSec = (t.toEpochMilli() - epochMs.longValue()) / 1000.0;
                double n = (Math.PI * 2.0) / pSec;
                return wrapToTwoPi(M0 + n * dtSec);
            }
        }
        return wrapToTwoPi(M0);
    }

    static double wrapToTwoPi(double rad) {
        double twoPi = Math.PI * 2.0;
        double x = rad % twoPi;
        if (x < 0) {
            x += twoPi;
        }
        return x;
    }

    /** Fallback when semi-major axis is missing: stable pseudo-vector from {@link BodyInfo#getDistanceLs()}. */
    static double[] pseudoOffsetMetres(BodyInfo b) {
        double ls = b.getDistanceLs();
        if (Double.isNaN(ls)) {
            ls = 0;
        }
        double r = ls * LIGHT_SECOND_METRES;
        int id = Math.max(0, b.getBodyId());
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
        if (Math.abs(v) > Math.PI * 2 + 0.02) {
            return Math.toRadians(v);
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

    static double normAngle(double rad) {
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
}
