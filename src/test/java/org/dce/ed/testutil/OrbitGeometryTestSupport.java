package org.dce.ed.testutil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;

import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmap.SystemMapModel;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;

/** Shared assertions for schematic orbit geometry and map polylines. */
public final class OrbitGeometryTestSupport {

    /**
     * Minimum map-plane separation (Ls) between a body's branch star and every other branch star. Catches
     * {@code A 2} drawn on star C even when it is only slightly closer than star A.
     */
    public static final double DESIGNATION_BRANCH_MIN_MARGIN_LS = 500.0;

    private OrbitGeometryTestSupport() {
    }

    /**
     * Major bodies (not moons, not scan rows) whose resolved orbit parent is {@code starLabel}'s map key.
     */
    public static List<String> directResolvedMajorChildrenOfStar(SystemMapModel model,
            Map<Integer, BodyInfo> bodies,
            String starLabel) {
        int starId = findByShortName(bodies, starLabel);
        if (starId < 0 || model == null || bodies == null) {
            return Collections.emptyList();
        }
        List<String> labels = new ArrayList<>();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isScanBarycentreRow()) {
                continue;
            }
            if (SystemOrbitGeometry.isMapStellarBody(e.getValue())
                    || SystemOrbitGeometry.isMoonSatelliteBody(e.getValue(), bodies)) {
                continue;
            }
            if (model.resolveParentBodyId(e.getKey().intValue()) == starId) {
                labels.add(e.getValue().getShortName());
            }
        }
        Collections.sort(labels);
        return labels;
    }

    public static int findByShortName(Map<Integer, BodyInfo> bodies, String shortName) {
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getValue() != null && shortName.equals(e.getValue().getShortName())) {
                return e.getKey().intValue();
            }
        }
        return -1;
    }

    public static double distOnAxes(double[] a, double[] b, int axis0, int axis1) {
        double dx = SystemOrbitGeometry.worldAxisMetres(a, axis0) - SystemOrbitGeometry.worldAxisMetres(b, axis0);
        double dy = SystemOrbitGeometry.worldAxisMetres(a, axis1) - SystemOrbitGeometry.worldAxisMetres(b, axis1);
        return Math.hypot(dx, dy);
    }

    /** Map-plane coordinates for axis {@code axis} (same frame as orbit polyline {@code wx}/{@code wy}). */
    public static double axisCoord(double[] p, int axis) {
        return SystemOrbitGeometry.worldAxisMetres(p, axis);
    }

    private static double[] mapPlanePoint(double x, double y, int axis0, int axis1) {
        int need = Math.max(3, Math.max(axis0, axis1) + 1);
        double[] out = new double[need];
        out[axis0] = x;
        out[axis1] = y;
        return out;
    }

    public static OrbitPolylineWorldXY findPlanetBinaryMutualRing(SystemMapModel model, int journalNullId) {
        int ringId = SystemOrbitGeometry.PLANET_BINARY_MUTUAL_ORBIT_RING_ID_BASE - journalNullId;
        for (OrbitPolylineWorldXY p : model.orbitPolylines()) {
            if (p != null && p.bodyId == ringId) {
                return p;
            }
        }
        return null;
    }

    /** Body world position should lie on the mutual-orbit circle (constant radius from ring centre). */
    public static void assertBodyOnMutualOrbitRing(SystemMapModel model, Map<Integer, BodyInfo> bodies,
            String shortName, int journalNullId, double toleranceLs) {
        int mapKey = findByShortName(bodies, shortName);
        assertTrue(mapKey >= 0, "missing body: " + shortName);
        OrbitPolylineWorldXY ring = findPlanetBinaryMutualRing(model, journalNullId);
        assertNotNull(ring, "mutual orbit ring for Null:" + journalNullId);
        double[] pos = model.positionsMetres().get(Integer.valueOf(mapKey));
        assertNotNull(pos, "position for " + shortName);
        int a0 = model.projectionAxis0();
        int a1 = model.projectionAxis1();
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        int bKey = SystemOrbitGeometry.planetBinaryBarycentreMapKey(journalNullId);
        double[] hub = model.positionsMetres().get(Integer.valueOf(bKey));
        double cx;
        double cy;
        if (hub != null) {
            cx = axisCoord(hub, a0);
            cy = axisCoord(hub, a1);
        } else {
            cx = ringCentroid(ring.wx);
            cy = ringCentroid(ring.wy);
        }
        double ringRad = meanRadius(ring.wx, ring.wy, cx, cy);
        double bodyRad = distOnAxes(pos, mapPlanePoint(cx, cy, a0, a1), a0, a1);
        double tolM = toleranceLs * ls;
        /* Polyline mean radius can differ slightly from schematic dot radius (segment count, phase at epoch). */
        assertTrue(Math.abs(bodyRad - ringRad) <= Math.max(tolM, ringRad * 0.12),
                shortName + " should sit on mutual ring (bodyR=" + (bodyRad / ls) + " Ls ringR="
                        + (ringRad / ls) + " Ls)");
    }

    public static void assertNoPerBodyOrbitRing(SystemMapModel model, int bodyMapKey) {
        assertFalse(model.hasOrbitRingForBody(bodyMapKey),
                "body id " + bodyMapKey + " should not have its own orbit stroke (uses mutual ring)");
    }

    /** Per-body orbit stroke (moon around giant): dot should lie on its own ring at roughly journal separation. */
    public static void assertBodyOnPerBodyOrbitRing(SystemMapModel model, Map<Integer, BodyInfo> bodies,
            String shortName, double toleranceLs) {
        int mapKey = findByShortName(bodies, shortName);
        assertTrue(mapKey >= 0, "missing body: " + shortName);
        assertTrue(model.hasOrbitRingForBody(mapKey), "missing per-body ring for " + shortName);
        OrbitPolylineWorldXY ring = null;
        for (OrbitPolylineWorldXY p : model.orbitPolylines()) {
            if (p != null && p.bodyId == mapKey) {
                ring = p;
                break;
            }
        }
        assertNotNull(ring, "polyline for " + shortName);
        int parentId = model.resolveParentBodyId(mapKey);
        double[] parentPos = model.positionsMetres().get(Integer.valueOf(parentId));
        double[] bodyPos = model.positionsMetres().get(Integer.valueOf(mapKey));
        assertNotNull(parentPos, "parent position");
        assertNotNull(bodyPos, "body position");
        int a0 = model.projectionAxis0();
        int a1 = model.projectionAxis1();
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        double cx = axisCoord(parentPos, a0);
        double cy = axisCoord(parentPos, a1);
        double ringRad = meanRadius(ring.wx, ring.wy, cx, cy);
        double bodyRad = distOnAxes(bodyPos, parentPos, a0, a1);
        double tolM = toleranceLs * ls;
        assertTrue(Math.abs(bodyRad - ringRad) <= Math.max(tolM, ringRad * 0.15),
                shortName + " on per-body ring (bodyR=" + (bodyRad / ls) + " Ls ringR=" + (ringRad / ls)
                        + " Ls parent=" + parentId + ")");
        assertTrue(ringRad / ls < 500.0,
                shortName + " moon ring should be parent-relative, not heliocentric; ringR=" + (ringRad / ls) + " Ls");
    }

    public static void assertBarycentreFarFromStar(SystemMapModel model, Map<Integer, BodyInfo> bodies,
            int journalNullId, double minDistanceLs) {
        int bKey = SystemOrbitGeometry.planetBinaryBarycentreMapKey(journalNullId);
        int star = SystemOrbitGeometry.schematicCentralStarMapKey(bodies);
        Map<Integer, double[]> pos = model.positionsMetres();
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        double d = distOnAxes(pos.get(Integer.valueOf(bKey)), pos.get(Integer.valueOf(star)),
                model.projectionAxis0(), model.projectionAxis1()) / ls;
        assertTrue(d >= minDistanceLs,
                "planet-binary barycentre should not sit on the star; was " + d + " Ls");
    }

    public static double ringCentroid(double[] coords) {
        double sum = 0.0;
        for (double c : coords) {
            sum += c;
        }
        return coords.length > 0 ? sum / coords.length : 0.0;
    }

    public static double meanRadius(double[] wx, double[] wy, double cx, double cy) {
        double sum = 0.0;
        for (int i = 0; i < wx.length; i++) {
            sum += Math.hypot(wx[i] - cx, wy[i] - cy);
        }
        return wx.length > 0 ? sum / wx.length : 0.0;
    }

    /** Hierarchical A vs BCD: trunk ring through star A and companion-cluster centroid (not a fixed origin circle). */
    public static void assertHierarchicalSchematicBarycentreRing(SystemMapModel model, Map<Integer, BodyInfo> bodies,
            int primaryStarId) {
        assertTrue(model.hasBarycentreMutualRing(), "expected schematic system barycentre ring");
        assertPrimaryOnSchematicMutualRing(model, primaryStarId, SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID,
                0.12);
        assertCompanionClusterOnTrunkRing(model, bodies, primaryStarId,
                SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID, 0.15);
    }

    /** Companion trunk centroid (B/C/D cluster) on the schematic A vs BCD ring rim. */
    public static void assertCompanionClusterOnTrunkRing(SystemMapModel model, Map<Integer, BodyInfo> bodies,
            int primaryStarId, int polylineBodyId, double toleranceFrac) {
        int a0 = model.projectionAxis0();
        int a1 = model.projectionAxis1();
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        double sumX = 0.0;
        double sumY = 0.0;
        int n = 0;
        for (String label : new String[] { "B", "C", "D" }) {
            int id = findByShortName(bodies, label);
            if (id < 0) {
                continue;
            }
            double[] p = model.positionsMetres().get(Integer.valueOf(id));
            if (p == null) {
                continue;
            }
            sumX += axisCoord(p, a0);
            sumY += axisCoord(p, a1);
            n++;
        }
        int null3Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(3);
        double[] hub3 = model.positionsMetres().get(Integer.valueOf(null3Key));
        if (hub3 != null) {
            sumX += axisCoord(hub3, a0);
            sumY += axisCoord(hub3, a1);
            n++;
        }
        assertTrue(n > 0, "companion cluster anchors for trunk ring");
        double cx = sumX / n;
        double cy = sumY / n;
        for (OrbitPolylineWorldXY poly : model.orbitPolylines()) {
            if (poly == null || poly.bodyId != polylineBodyId) {
                continue;
            }
            double ringCx = ringCentroid(poly.wx);
            double ringCy = ringCentroid(poly.wy);
            double ringRad = meanRadius(poly.wx, poly.wy, ringCx, ringCy);
            double dist = Math.hypot(cx - ringCx, cy - ringCy);
            assertTrue(Math.abs(dist - ringRad) <= Math.max(ringRad * toleranceFrac, ls * 80.0),
                    "companion cluster on trunk ring (dist=" + (dist / ls) + " Ls ringR=" + (ringRad / ls) + " Ls)");
            return;
        }
        fail("missing trunk ring polyline id " + polylineBodyId);
    }

    /** Primary star on the rim of a schematic mutual-orbit polyline (e.g. triple-star A vs B+C trunk ring). */
    public static void assertPrimaryOnSchematicMutualRing(SystemMapModel model, int primaryStarId, int polylineBodyId,
            double toleranceFrac) {
        int a0 = model.projectionAxis0();
        int a1 = model.projectionAxis1();
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        double[] aPos = model.positionsMetres().get(Integer.valueOf(primaryStarId));
        assertNotNull(aPos, "primary position");
        double ax = axisCoord(aPos, a0);
        double ay = axisCoord(aPos, a1);
        for (OrbitPolylineWorldXY poly : model.orbitPolylines()) {
            if (poly == null || poly.bodyId != polylineBodyId) {
                continue;
            }
            double cx = ringCentroid(poly.wx);
            double cy = ringCentroid(poly.wy);
            double ringRad = meanRadius(poly.wx, poly.wy, cx, cy);
            double distA = Math.hypot(ax - cx, ay - cy);
            assertTrue(Math.abs(distA - ringRad) <= Math.max(ringRad * toleranceFrac, ls * 50.0),
                    "primary on schematic ring rim (distA=" + (distA / ls) + " Ls ringR=" + (ringRad / ls) + " Ls)");
            assertTrue(distA >= ringRad * 0.55,
                    "ring centre must not coincide with primary (empty ring around star)");
            return;
        }
        fail("missing schematic ring polyline id " + polylineBodyId);
    }

    /**
     * Mirrors the screenshot failure: one light-blue circle centred on the arrival star at heliocentric (~50k Ls)
     * scale with B/C on its rim — not the schematic ~7k Ls BCD trunk.
     */
    /**
     * Synthetic schematic concentric ring ids from {@code appendSchematicRingsAtStar} / branch-star layout
     * ({@code -4000 - starId * 100_000 - ls}). Planet-binary guide rings ({@code -51k}/{@code -52k}) sit below {@code -50k}.
     */
    public static boolean isSchematicConcentricRingPolylineId(int bodyId) {
        return bodyId <= -4_000 && bodyId > -50_000;
    }

    /**
     * Per-body (positive id) orbit stroke centroid should lie near the resolved direct parent, not the wide-binary
     * mutual-ring centre or map origin.
     */
    public static void assertPerBodyOrbitRingCentredOnResolvedParent(SystemMapModel model,
            Map<Integer, BodyInfo> bodies,
            String shortName,
            List<OrbitPolylineWorldXY> polylines,
            double toleranceLs) {
        int mapKey = findByShortName(bodies, shortName);
        assertTrue(mapKey >= 0, "missing body: " + shortName);
        int parentId = model.resolveParentBodyId(mapKey);
        assertTrue(parentId >= 0 || SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(parentId),
                shortName + " needs resolved parent for orbit ring");
        OrbitPolylineWorldXY ring = null;
        for (OrbitPolylineWorldXY p : polylines) {
            if (p != null && p.bodyId == mapKey) {
                ring = p;
                break;
            }
        }
        assertNotNull(ring, "missing per-body orbit ring for " + shortName);
        double[] parentPos = model.positionsMetres().get(
                Integer.valueOf(parentId >= 0 ? parentId
                        : SystemOrbitGeometry.planetBinaryBarycentreMapKey(
                                SystemOrbitGeometry.journalNullIdFromPlanetBinaryBarycentreMapKey(parentId))));
        assertNotNull(parentPos, "parent position for " + shortName);
        int a0 = model.projectionAxis0();
        int a1 = model.projectionAxis1();
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        double px = axisCoord(parentPos, a0);
        double py = axisCoord(parentPos, a1);
        BodyInfo body = bodies.get(Integer.valueOf(mapKey));
        double hintLs = body != null && Double.isFinite(body.getDistanceLs()) && body.getDistanceLs() > 0
                ? body.getDistanceLs()
                : Double.NaN;
        double ecc = 0.0;
        if (body != null && body.getEccentricity() != null && Double.isFinite(body.getEccentricity())) {
            ecc = Math.max(0.0, Math.min(0.999, body.getEccentricity().doubleValue()));
        }
        double minFromParent = Double.POSITIVE_INFINITY;
        double maxFromParent = 0.0;
        for (int i = 0; i < ring.wx.length; i++) {
            double r = Math.hypot(ring.wx[i] - px, ring.wy[i] - py) / ls;
            minFromParent = Math.min(minFromParent, r);
            maxFromParent = Math.max(maxFromParent, r);
        }
        if (Double.isFinite(hintLs) && hintLs > 2.0) {
            assertTrue(maxFromParent <= hintLs * (1.0 + ecc) * 1.25 + 50.0,
                    shortName + " orbit should not extend far beyond journal distance (max=" + maxFromParent
                            + " hint=" + hintLs + ")");
            assertTrue(minFromParent >= hintLs * Math.max(0.05, 1.0 - ecc) * 0.35,
                    shortName + " orbit should wrap parent star focus (min=" + minFromParent + " hint=" + hintLs
                            + ")");
        }
        double cx = ringCentroid(ring.wx);
        double cy = ringCentroid(ring.wy);
        double off = Math.hypot(cx - px, cy - py) / ls;
        double centroidTol = toleranceLs;
        if (!ring.estimated && ecc > 0.05 && Double.isFinite(hintLs)) {
            centroidTol = Math.max(toleranceLs, hintLs * ecc * 1.35);
        }
        assertTrue(off <= centroidTol,
                shortName + " orbit ring should anchor on direct parent (off=" + off + " Ls tol=" + centroidTol
                        + " parent=" + parentId + ")");
        for (OrbitPolylineWorldXY poly : polylines) {
            if (poly == null || poly.bodyId != SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID) {
                continue;
            }
            double bx = ringCentroid(poly.wx);
            double by = ringCentroid(poly.wy);
            double baryOff = Math.hypot(cx - bx, cy - by) / ls;
            assertTrue(baryOff > 500.0 || off < 50.0,
                    shortName + " ring must not coincide with A/B mutual barycentre ring (baryOff=" + baryOff
                            + " Ls)");
            return;
        }
    }

    /**
     * True-scale Kepler stroke should be measurably non-circular when journal eccentricity is significant.
     */
    /**
     * Exactly one closed stroke with {@code bodyId == map key} for {@code shortName}; no second stroke of similar
     * radius whose centre is nearer the wide-binary system barycentre than the resolved direct parent.
     */
    public static void assertExactlyOneDirectParentOrbitStroke(SystemMapModel model,
            Map<Integer, BodyInfo> bodies,
            String shortName,
            List<OrbitPolylineWorldXY> polylines,
            double toleranceLs) {
        int mapKey = findByShortName(bodies, shortName);
        assertTrue(mapKey >= 0, "missing body: " + shortName);
        int parentId = model.resolveParentBodyId(mapKey);
        assertTrue(parentId >= 0, shortName + " needs star parent");
        int matches = 0;
        OrbitPolylineWorldXY primary = null;
        for (OrbitPolylineWorldXY p : polylines) {
            if (p != null && p.bodyId == mapKey) {
                matches++;
                primary = p;
            }
        }
        assertEquals(1, matches, shortName + " should have exactly one per-body orbit polyline");
        assertNotNull(primary);
        assertPerBodyOrbitRingCentredOnResolvedParent(model, bodies, shortName, polylines, toleranceLs);
        double[] parentPos = model.positionsMetres().get(Integer.valueOf(parentId));
        assertNotNull(parentPos);
        int a0 = model.projectionAxis0();
        int a1 = model.projectionAxis1();
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        double px = axisCoord(parentPos, a0);
        double py = axisCoord(parentPos, a1);
        double ringR = meanRadius(primary.wx, primary.wy, ringCentroid(primary.wx), ringCentroid(primary.wy));
        double baryX = 0.0;
        double baryY = 0.0;
        int starCount = 0;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null
                    || !SystemOrbitGeometry.isMapStellarBody(e.getValue())) {
                continue;
            }
            if (!SystemOrbitGeometry.orbitsWideBinarySystemBarycentre(e.getValue(), bodies, e.getKey().intValue())) {
                continue;
            }
            double[] sp = model.positionsMetres().get(e.getKey());
            if (sp == null) {
                continue;
            }
            baryX += axisCoord(sp, a0);
            baryY += axisCoord(sp, a1);
            starCount++;
        }
        if (starCount > 0) {
            baryX /= starCount;
            baryY /= starCount;
        }
        for (OrbitPolylineWorldXY poly : polylines) {
            if (poly == null || poly.wx == null || poly.wy == null || poly.wx.length < 3
                    || poly.bodyId == mapKey
                    || poly.bodyId == SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID) {
                continue;
            }
            double cx = ringCentroid(poly.wx);
            double cy = ringCentroid(poly.wy);
            double rad = meanRadius(poly.wx, poly.wy, cx, cy);
            if (Math.abs(rad - ringR) > ringR * 0.45) {
                continue;
            }
            double offParent = Math.hypot(cx - px, cy - py) / ls;
            double offBary = Math.hypot(cx - baryX, cy - baryY) / ls;
            assertTrue(offBary >= offParent * 0.5 || offParent <= toleranceLs,
                    shortName + " ghost barycentre stroke (bodyId=" + poly.bodyId + " offBary=" + offBary
                            + " offParent=" + offParent + " Ls)");
        }
    }

    /**
     * Every direct child of the primary anchor star on the A-branch should have one parent-centred stroke that is not
     * needle-flat in the map plane (true-scale Kepler around the branch star, not the system barycentre).
     */
    public static void assertDirectPrimaryBranchPlanetOrbitsNotSquished(SystemMapModel model,
            Map<Integer, BodyInfo> bodies,
            List<String> shortNames,
            List<OrbitPolylineWorldXY> polylines,
            double maxApoPeriRatio,
            double parentCentreToleranceLs) {
        int primaryId = SystemOrbitGeometry.primaryAnchorBodyMapKey(bodies);
        assertTrue(primaryId >= 0, "missing wide-binary primary anchor star");
        BodyInfo primary = bodies.get(Integer.valueOf(primaryId));
        String starLabel = primary != null && primary.getShortName() != null ? primary.getShortName().trim() : "A";
        List<String> targets = shortNames != null && !shortNames.isEmpty()
                ? shortNames
                : directResolvedMajorChildrenOfStar(model, bodies, starLabel);
        for (String shortName : targets) {
            int mapKey = findByShortName(bodies, shortName);
            assertTrue(mapKey >= 0, "missing body: " + shortName);
            assertEquals(primaryId, model.resolveParentBodyId(mapKey),
                    shortName + " must resolve to primary anchor star");
            assertExactlyOneDirectParentOrbitStroke(model, bodies, shortName, polylines, parentCentreToleranceLs);
            OrbitPolylineWorldXY ring = null;
            for (OrbitPolylineWorldXY p : polylines) {
                if (p != null && p.bodyId == mapKey) {
                    ring = p;
                    break;
                }
            }
            assertNotNull(ring, shortName + " orbit ring");
            assertOrbitPolylineAspectRatioSane(ring, maxApoPeriRatio);
            BodyInfo body = bodies.get(Integer.valueOf(mapKey));
            double ecc = 0.0;
            if (body != null && body.getEccentricity() != null && Double.isFinite(body.getEccentricity())) {
                ecc = Math.max(0.0, body.getEccentricity().doubleValue());
            }
            if (ecc > 0.15 && !ring.estimated) {
                assertOrbitPolylineIsNonCircularKepler(ring, 0.12);
            }
        }
    }

    /**
     * High-eccentricity edge-on Kepler projections can be very flat; reject only absurd needle-like polylines.
     */
    public static void assertOrbitPolylineAspectRatioSane(OrbitPolylineWorldXY ring, double maxApoPeriRatio) {
        assertNotNull(ring);
        double cx = ringCentroid(ring.wx);
        double cy = ringCentroid(ring.wy);
        double minR = Double.POSITIVE_INFINITY;
        double maxR = 0.0;
        for (int i = 0; i < ring.wx.length; i++) {
            double r = Math.hypot(ring.wx[i] - cx, ring.wy[i] - cy);
            minR = Math.min(minR, r);
            maxR = Math.max(maxR, r);
        }
        double ratio = maxR / Math.max(minR, 1.0);
        assertTrue(ratio <= maxApoPeriRatio,
                "orbit polyline too squished (apo/peri=" + ratio + ", max=" + maxApoPeriRatio + ")");
    }

    public static void assertOrbitPolylineIsNonCircularKepler(OrbitPolylineWorldXY ring, double minEccentricityHint) {
        assertNotNull(ring, "orbit polyline");
        assertTrue(ring.wx != null && ring.wx.length >= 8, "need enough vertices");
        double cx = ringCentroid(ring.wx);
        double cy = ringCentroid(ring.wy);
        double minR = Double.POSITIVE_INFINITY;
        double maxR = 0.0;
        for (int i = 0; i < ring.wx.length; i++) {
            double r = Math.hypot(ring.wx[i] - cx, ring.wy[i] - cy);
            minR = Math.min(minR, r);
            maxR = Math.max(maxR, r);
        }
        double ratio = maxR / Math.max(minR, 1.0);
        assertTrue(ratio >= 1.0 + minEccentricityHint,
                "expected elliptical Kepler stroke, apo/peri ratio=" + ratio);
    }

    public static void assertNoSchematicConcentricBranchRings(List<OrbitPolylineWorldXY> polylines) {
        for (OrbitPolylineWorldXY poly : polylines) {
            if (poly != null && isSchematicConcentricRingPolylineId(poly.bodyId)) {
                fail("unexpected schematic concentric ring id " + poly.bodyId);
            }
        }
    }

    /**
     * Regression for Coeus A-branch screenshot: one Kepler ellipse around the branch star must not stack with a second
     * journal-radius schematic circle ({@code SINGLE_STAR_SCHEMATIC_RING_ID_BASE} or near-circular fallback).
     */
    public static void assertNoEllipticalAndCircularOrbitPairNearParent(SystemMapModel model,
            Map<Integer, BodyInfo> bodies,
            String shortName,
            List<OrbitPolylineWorldXY> polylines,
            double toleranceLs) {
        int mapKey = findByShortName(bodies, shortName);
        assertTrue(mapKey >= 0, "missing body: " + shortName);
        int parentId = model.resolveParentBodyId(mapKey);
        assertTrue(parentId >= 0, shortName + " needs star parent");
        BodyInfo body = bodies.get(Integer.valueOf(mapKey));
        assertNotNull(body);
        double hintLs = body.getDistanceLs();
        if (!Double.isFinite(hintLs) || hintLs <= 2.0) {
            return;
        }
        double[] parentPos = model.positionsMetres().get(Integer.valueOf(parentId));
        assertNotNull(parentPos);
        int a0 = model.projectionAxis0();
        int a1 = model.projectionAxis1();
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        double px = axisCoord(parentPos, a0);
        double py = axisCoord(parentPos, a1);
        double hintM = hintLs * ls;
        boolean circularNear = false;
        boolean ellipticalNear = false;
        int nearCount = 0;
        for (OrbitPolylineWorldXY poly : polylines) {
            if (poly == null || poly.wx == null || poly.wy == null || poly.wx.length < 3) {
                continue;
            }
            if (poly.bodyId == SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID) {
                continue;
            }
            double cx = ringCentroid(poly.wx);
            double cy = ringCentroid(poly.wy);
            double radM = meanRadius(poly.wx, poly.wy, cx, cy);
            double offParent = Math.hypot(cx - px, cy - py) / ls;
            if (offParent > toleranceLs * 3.0 && poly.bodyId != mapKey) {
                continue;
            }
            if (poly.bodyId != mapKey && !isSchematicConcentricRingPolylineId(poly.bodyId)) {
                continue;
            }
            if (Math.abs(radM - hintM) > hintM * 0.55) {
                continue;
            }
            if (isSchematicConcentricRingPolylineId(poly.bodyId)) {
                fail(shortName + " must not have schematic branch-star ring id " + poly.bodyId
                        + " at true scale (journal-radius circle)");
            }
            nearCount++;
            double minR = Double.POSITIVE_INFINITY;
            double maxR = 0.0;
            for (int i = 0; i < poly.wx.length; i++) {
                double r = Math.hypot(poly.wx[i] - cx, poly.wy[i] - cy);
                minR = Math.min(minR, r);
                maxR = Math.max(maxR, r);
            }
            double ratio = maxR / Math.max(minR, 1.0);
            if (ratio >= 1.08) {
                ellipticalNear = true;
            } else if (ratio <= 1.06) {
                circularNear = true;
            }
        }
        assertTrue(nearCount <= 1,
                shortName + " should have at most one orbit stroke near journal radius (had " + nearCount + ")");
        assertFalse(circularNear && ellipticalNear,
                shortName + " must not show both a circular journal-radius ring and a separate Kepler ellipse");
    }

    public static void assertNoHeliocentricRingAroundPrimaryStar(SystemMapModel model, Map<Integer, BodyInfo> bodies,
            int primaryStarId, double maxRingRadiusLs) {
        assertNoHeliocentricRingAroundPrimaryStar(model, bodies, primaryStarId, maxRingRadiusLs,
                model.orbitPolylines());
    }

    public static void assertNoHeliocentricRingAroundPrimaryStar(SystemMapModel model, Map<Integer, BodyInfo> bodies,
            int primaryStarId, double maxRingRadiusLs, List<OrbitPolylineWorldXY> polylines) {
        int a0 = model.projectionAxis0();
        int a1 = model.projectionAxis1();
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        double[] aPos = model.positionsMetres().get(Integer.valueOf(primaryStarId));
        assertNotNull(aPos, "primary position");
        double ax = axisCoord(aPos, a0);
        double ay = axisCoord(aPos, a1);
        double maxRadM = maxRingRadiusLs * ls;
        for (OrbitPolylineWorldXY poly : polylines) {
            if (poly == null || poly.wx == null || poly.wy == null || poly.wx.length < 3) {
                continue;
            }
            double cx = ringCentroid(poly.wx);
            double cy = ringCentroid(poly.wy);
            double ringRad = meanRadius(poly.wx, poly.wy, cx, cy);
            double centreOff = Math.hypot(cx - ax, cy - ay);
            double distAFromRingCentre = centreOff;
            /* Hierarchical schematic: large ring at barycentre (origin), primary on the ring — not star at centre. */
            if (poly.bodyId == SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID
                    && ringRad <= maxRadM * 1.5
                    && distAFromRingCentre >= ringRad * 0.55
                    && distAFromRingCentre <= ringRad * 1.45) {
                continue;
            }
            if (poly.bodyId == SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID
                    && centreOff <= ringRad * 0.25
                    && ringRad > maxRadM) {
                fail("mutual ring centred on primary star (heliocentric layout)");
            }
            if (ringRad <= maxRadM || centreOff > ringRad * 0.35) {
                continue;
            }
            int companionOnRim = 0;
            for (String label : new String[] { "B", "C" }) {
                int bid = findByShortName(bodies, label);
                if (bid < 0) {
                    continue;
                }
                double[] p = model.positionsMetres().get(Integer.valueOf(bid));
                if (p == null) {
                    continue;
                }
                double bx = axisCoord(p, a0);
                double by = axisCoord(p, a1);
                double bodyRad = Math.hypot(bx - cx, by - cy);
                if (Math.abs(bodyRad - ringRad) <= ringRad * 0.08) {
                    companionOnRim++;
                }
            }
            if (companionOnRim >= 2) {
                fail("heliocentric-scale ring centred near primary (ringR=" + (ringRad / ls) + " Ls, bodyId="
                        + poly.bodyId + ", centreOff=" + (centreOff / ls) + " Ls)");
            }
        }
    }

    /**
     * Exhaustive designation-branch invariants: resolved parents, moon hosts, and map-plane proximity must never
     * cross Elite letter groups ({@code A 3 a} must not orbit star C or planet BCD 2).
     */
    public static void assertDesignationBranchInvariants(SystemMapModel model, Map<Integer, BodyInfo> bodies) {
        assertBranchLetterMapPlacement(model, bodies, DESIGNATION_BRANCH_MIN_MARGIN_LS);
        assertDesignationBranchParentInvariants(model, bodies);
    }

    private static void assertDesignationBranchParentInvariants(SystemMapModel model, Map<Integer, BodyInfo> bodies) {
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isScanBarycentreRow()) {
                continue;
            }
            if (SystemOrbitGeometry.isMapStellarBody(e.getValue())) {
                continue;
            }
            String branch = SystemOrbitGeometry.designationBranchLetter(e.getValue());
            if (branch == null || branch.length() != 1) {
                continue;
            }
            int bodyId = e.getKey().intValue();
            String label = e.getValue().getShortName();
            assertResolvedOrbitStaysInDesignationBranch(model, bodies, bodyId, branch, label);
            if (SystemOrbitGeometry.isMoonSatelliteBody(e.getValue(), bodies)) {
                assertMoonHostMatchesDesignation(model, bodies, bodyId, branch, label);
            }
        }
    }

    private static void assertResolvedOrbitStaysInDesignationBranch(SystemMapModel model,
            Map<Integer, BodyInfo> bodies,
            int bodyId,
            String branch,
            String label) {
        int walk = bodyId;
        for (int hop = 0; hop < 24; hop++) {
            int parentId = model.resolveParentBodyId(walk);
            if (parentId < 0) {
                return;
            }
            BodyInfo parent = bodies.get(Integer.valueOf(parentId));
            if (parent == null) {
                return;
            }
            if (SystemOrbitGeometry.isMapStellarBody(parent)) {
                String starLetter = stellarBranchLetterForMap(parent);
                assertTrue(starLetter != null && branch.equalsIgnoreCase(starLetter),
                        label + " must not resolve under star " + starLetter + " (expected branch " + branch + ")");
                return;
            }
            String parentBranch = SystemOrbitGeometry.designationBranchLetter(parent);
            if (parentBranch != null && parentBranch.length() == 1
                    && !branch.equalsIgnoreCase(parentBranch)) {
                fail(label + " must not resolve under " + parent.getShortName() + " (branch " + parentBranch + ")");
            }
            walk = parentId;
        }
    }

    private static void assertMoonHostMatchesDesignation(SystemMapModel model,
            Map<Integer, BodyInfo> bodies,
            int moonId,
            String branch,
            String moonLabel) {
        int hostId = model.resolveParentBodyId(moonId);
        assertTrue(hostId >= 0, moonLabel + " needs resolved host");
        BodyInfo host = bodies.get(Integer.valueOf(hostId));
        assertNotNull(host, moonLabel + " host");
        assertFalse(SystemOrbitGeometry.isMapStellarBody(host),
                moonLabel + " must not parent directly to a star");
        String hostLabel = host.getShortName();
        assertNotNull(hostLabel, moonLabel + " host label");
        assertTrue(moonLabel.startsWith(hostLabel.trim()),
                moonLabel + " must orbit host " + hostLabel + ", not unrelated body");
        String hostBranch = SystemOrbitGeometry.designationBranchLetter(host);
        if (hostBranch != null && hostBranch.length() == 1) {
            assertEquals(branch, hostBranch.toUpperCase(Locale.ROOT),
                    moonLabel + " and host " + hostLabel + " must share branch letter");
        }
        for (String otherStar : List.of("A", "B", "C", "D")) {
            if (otherStar.equalsIgnoreCase(branch)) {
                continue;
            }
            int otherId = findByShortName(bodies, otherStar);
            if (otherId < 0) {
                continue;
            }
            assertTrue(hostId != otherId,
                    moonLabel + " must not use star " + otherStar + " as host");
        }
    }

    private static String stellarBranchLetterForMap(BodyInfo star) {
        if (star == null) {
            return null;
        }
        String fromDesig = SystemOrbitGeometry.designationBranchLetter(star);
        if (fromDesig != null && fromDesig.length() == 1) {
            return fromDesig.toUpperCase(Locale.ROOT);
        }
        String sn = star.getShortName();
        if (sn != null && sn.trim().length() == 1) {
            return sn.trim().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    /**
     * Single-letter branch stars in the system ({@code A}, {@code B}, …) keyed by letter.
     */
    public static Map<String, Integer> branchStarsByLetter(Map<Integer, BodyInfo> bodies) {
        Map<String, Integer> stars = new LinkedHashMap<>();
        if (bodies == null) {
            return stars;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String letter = stellarBranchLetterForMap(e.getValue());
            if (letter != null && letter.length() == 1) {
                stars.putIfAbsent(letter.toUpperCase(Locale.ROOT), e.getKey().intValue());
            }
        }
        return stars;
    }

    /**
     * Nuclear invariant: every {@code X …} body must be modeled and drawn measurably closer to star {@code X} than to
     * any other branch star {@code Y} (margin {@link #DESIGNATION_BRANCH_MIN_MARGIN_LS} Ls).
     */
    public static void assertNuclearDesignationBranchPlacement(SystemMapModel model, Map<Integer, BodyInfo> bodies) {
        assertNuclearDesignationBranchPlacement(model, bodies, DESIGNATION_BRANCH_MIN_MARGIN_LS);
    }

    public static void assertNuclearDesignationBranchPlacement(SystemMapModel model, Map<Integer, BodyInfo> bodies,
            double marginLs) {
        assertBranchLetterMapPlacement(model, bodies, marginLs);
        assertDesignationBranchParentInvariants(model, bodies);
    }

    public static void assertBranchLetterMapPlacement(SystemMapModel model, Map<Integer, BodyInfo> bodies) {
        assertBranchLetterMapPlacement(model, bodies, DESIGNATION_BRANCH_MIN_MARGIN_LS);
    }

    public static void assertBranchLetterMapPlacement(SystemMapModel model, Map<Integer, BodyInfo> bodies,
            double marginLs) {
        Map<String, Integer> stars = branchStarsByLetter(bodies);
        if (stars.size() < 2) {
            return;
        }
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isScanBarycentreRow()) {
                continue;
            }
            if (SystemOrbitGeometry.isMapStellarBody(e.getValue())) {
                continue;
            }
            String branch = SystemOrbitGeometry.designationBranchLetter(e.getValue());
            if (branch == null || branch.length() != 1) {
                continue;
            }
            branch = branch.toUpperCase(Locale.ROOT);
            Integer ownStarObj = stars.get(branch);
            assertTrue(ownStarObj != null, "missing branch star " + branch + " for " + e.getValue().getShortName());
            int ownStarId = ownStarObj.intValue();
            int bodyId = e.getKey().intValue();
            double nearOwn = mapSepLs(model, bodyId, ownStarId, ls);
            double bx = model.mapPlaneX(bodyId);
            double by = model.mapPlaneY(bodyId);
            for (Map.Entry<String, Integer> star : stars.entrySet()) {
                if (star.getKey().equals(branch)) {
                    continue;
                }
                int otherId = star.getValue().intValue();
                double nearOther = mapSepLs(model, bodyId, otherId, ls);
                assertTrue(nearOwn + marginLs < nearOther,
                        e.getValue().getShortName() + " at (" + (bx / ls) + "," + (by / ls) + ") Ls must be >= "
                                + marginLs + " Ls nearer star " + branch + " (" + nearOwn + " Ls) than star "
                                + star.getKey() + " (" + nearOther + " Ls)");
            }
        }
    }

    /**
     * Every body whose name starts with a branch letter (A, B, …) must resolve/orbit that branch's star, not another.
     */
    public static void assertPlanetaryBranchConsistency(SystemMapModel model, Map<Integer, BodyInfo> bodies) {
        assertBranchLetterMapPlacement(model, bodies, DESIGNATION_BRANCH_MIN_MARGIN_LS);
    }

    private static double mapSepLs(SystemMapModel model, int fromId, int toId, double ls) {
        return Math.hypot(model.mapPlaneX(fromId) - model.mapPlaneX(toId),
                model.mapPlaneY(fromId) - model.mapPlaneY(toId)) / ls;
    }
}
