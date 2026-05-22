package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.dce.ed.testutil.OrbitGeometryTestSupport.assertBarycentreFarFromStar;
import static org.dce.ed.testutil.OrbitGeometryTestSupport.assertBodyOnBinaryBarycentreOrbitRingAtViewTilt;
import static org.dce.ed.testutil.OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing;
import static org.dce.ed.testutil.OrbitGeometryTestSupport.assertNoPerBodyOrbitRing;
import static org.dce.ed.testutil.OrbitGeometryTestSupport.assertOrbitPolylineIsNonCircularKepler;
import static org.dce.ed.testutil.OrbitGeometryTestSupport.assertOrbitPolylineNotNearPerfectCircle;
import static org.dce.ed.testutil.OrbitGeometryTestSupport.findBinaryBarycentreOrbitRing;
import static org.dce.ed.testutil.OrbitGeometryTestSupport.findByShortName;
import static org.dce.ed.testutil.OrbitGeometryTestSupport.maxVertexDeltaMetres;
import static org.dce.ed.testutil.OrbitGeometryTestSupport.meanRadius;
import static org.dce.ed.testutil.OrbitGeometryTestSupport.ringCentroid;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmap.MapScaleMode;
import org.dce.ed.systemmap.SystemMapFixture;
import org.dce.ed.systemmap.SystemMapFixtureLoader;
import org.dce.ed.systemmap.SystemMapModel;
import org.dce.ed.systemmap.SystemMapPipeline;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Orbit-stroke regressions: planet-binary mutual rings, moon rings around parents, and no spurious
 * giant-centred rings for co-orbiting majors ({@code 2 a} / {@code 2 b} pattern).
 */
class SystemOrbitGeometryOrbitPolylinesTest {

    @Test
    void szG_d10_2113_emitsMutualRing_bothCoOrbitersOnRing_notAtStar() {
        Map<Integer, BodyInfo> bodies = szG_d10_2113_1bc();
        var model = SystemMapPipeline.build("Byua Aim SZ-G d10-2113", bodies, Instant.EPOCH, true);

        assertBarycentreFarFromStar(model, bodies, 12, 500.0);
        assertNotNull(findPlanetBinaryMutualRing(model, 12));
        int pB = findByShortName(bodies, "1 b");
        int pC = findByShortName(bodies, "1 c");
        assertNoPerBodyOrbitRing(model, pB);
        assertNoPerBodyOrbitRing(model, pC);
        assertBodyOnMutualOrbitRing(model, bodies, "1 b", 12, 0.25);
        assertBodyOnMutualOrbitRing(model, bodies, "1 c", 12, 0.25);
    }

    @Test
    void gasGiantChildBinary_2a2b_mutualRing_notGiantCentredPerBodyRings() {
        Map<Integer, BodyInfo> bodies = gasGiant2BinaryMoons();
        var model = SystemMapPipeline.build("Byua Aim SZ-G d10-2113", bodies, Instant.EPOCH, true);

        assertBarycentreFarFromStar(model, bodies, 25, 500.0);
        assertNotNull(findPlanetBinaryMutualRing(model, 25));
        int p2a = findByShortName(bodies, "2 a");
        int p2b = findByShortName(bodies, "2 b");
        assertNoPerBodyOrbitRing(model, p2a);
        assertNoPerBodyOrbitRing(model, p2b);
        assertBodyOnMutualOrbitRing(model, bodies, "2 a", 25, 0.25);
        assertBodyOnMutualOrbitRing(model, bodies, "2 b", 25, 0.25);
    }

    @Test
    void regularMoon_2aOnly_getsOrbitRingAroundGiant() {
        Map<Integer, BodyInfo> bodies = gasGiantWithSingleMoon();
        var model = SystemMapPipeline.build("Test", bodies, Instant.EPOCH, true);
        int moon = findByShortName(bodies, "2 a");
        int giant = findByShortName(bodies, "2");
        assertTrue(model.hasOrbitRingForBody(moon) || model.hasOrbitRingForBody(giant),
                "a normal moon should have a visible orbit stroke around its parent");
        assertFalse(SystemOrbitGeometry.hasPlanetBinaryNullParentInSystem(bodies));
    }

    @Test
    void hasPlanetBinaryNullParent_detectsPair_excludesMoons() {
        Map<Integer, BodyInfo> binary = szG_d10_2113_1bc();
        assertTrue(SystemOrbitGeometry.hasPlanetBinaryNullParentInSystem(binary));

        Map<Integer, BodyInfo> moonOnly = gasGiantWithSingleMoon();
        assertFalse(SystemOrbitGeometry.hasPlanetBinaryNullParentInSystem(moonOnly));
    }

    // --- 3D inclined orbit regressions (see .cursor/rules/system-map-3d-orbits.mdc) ---

    @Test
    @DisplayName("Coeus A 1: Kepler samples vary on out-of-plane axis; stroke not a map-plane circle")
    void coeus_trueScale_a1_inclinedOrbitPreserves3D() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        applyCoeusHighInclinationKeplerElements(bodies);
        int idA1 = coeus.bodyIdByLabel("A 1");
        BodyInfo a1 = bodies.get(Integer.valueOf(idA1));
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        OrbitPolylineWorldXY ring = findPolyline(model.orbitPolylines(), idA1);
        assertNotNull(ring, "A 1 Kepler stroke");
        assertTrue(assertKeplerDroppedAxisSpanMetres(a1, model.projectionAxis0(), model.projectionAxis1()) > 1.0e9,
                "inclined A 1 should retain substantial off-plane Kepler span");
        assertOrbitPolylineIsNonCircularKepler(ring, 0.10);
        assertOrbitPolylineNotNearPerfectCircle(ring, 1.06);
    }

    @Test
    @DisplayName("Coeus A 4: high inclination — z-span, elliptical projection, view tilt changes stroke")
    void coeus_trueScale_a4_inclinedOrbitPreserves3DAndViewTilt() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        applyCoeusHighInclinationKeplerElements(bodies);
        int idA4 = coeus.bodyIdByLabel("A 4");
        BodyInfo a4 = bodies.get(Integer.valueOf(idA4));
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        OrbitPolylineWorldXY flat = findPolyline(model.orbitPolylines(), idA4);
        assertNotNull(flat);
        double zSpan = assertKeplerDroppedAxisSpanMetres(a4, model.projectionAxis0(), model.projectionAxis1());
        assertTrue(zSpan > 1.0e10, "A 4 Kepler curve must extend out of map plane (z-span=" + zSpan + " m)");
        assertOrbitPolylineIsNonCircularKepler(flat, 0.12);
        assertOrbitPolylineNotNearPerfectCircle(flat, 1.06);

        List<OrbitPolylineWorldXY> tilted = SystemOrbitGeometry.orbitPolylinesWorldMetresXY(
                bodies, model.positionsMetres(), 96, Double.NaN, model.projectionAxis0(),
                model.projectionAxis1(), !SystemOrbitGeometry.isHierarchicalWideBinary(bodies),
                model.resolvedParentByBodyId(), MapScaleMode.TRUE_SCALE, false, null, 90);
        OrbitPolylineWorldXY opened = findPolyline(tilted, idA4);
        assertNotNull(opened);
        assertTrue(maxVertexDeltaMetres(flat, opened) > 1.0e8,
                "view tilt must change A 4 orbit stroke projection");
    }

    @Test
    @DisplayName("Coeus A–B mutual ring: no jump 0°→1° view tilt; companion B on ring 0–90°")
    void coeus_trueScale_binaryBarycentreRing_viewTiltContinuous() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;

        OrbitPolylineWorldXY ring0 = findBinaryBarycentreOrbitRing(
                SystemMapPipeline.rebuildOrbitPolylines(model, model.positionsMetres(), 96, Double.NaN, false, null,
                        MapScaleMode.TRUE_SCALE, 0));
        OrbitPolylineWorldXY ring1 = findBinaryBarycentreOrbitRing(
                SystemMapPipeline.rebuildOrbitPolylines(model, model.positionsMetres(), 96, Double.NaN, false, null,
                        MapScaleMode.TRUE_SCALE, 1));
        OrbitPolylineWorldXY ring2 = findBinaryBarycentreOrbitRing(
                SystemMapPipeline.rebuildOrbitPolylines(model, model.positionsMetres(), 96, Double.NaN, false, null,
                        MapScaleMode.TRUE_SCALE, 2));
        assertNotNull(ring0);
        assertNotNull(ring1);
        assertNotNull(ring2);

        double r0 = meanRadius(ring0.wx, ring0.wy, ringCentroid(ring0.wx), ringCentroid(ring0.wy));
        double jump01 = maxVertexDeltaMetres(ring0, ring1);
        double jump12 = maxVertexDeltaMetres(ring1, ring2);
        assertTrue(jump01 < r0 * 0.05,
                "0°→1° must not snap ring (max vertex Δ=" + (jump01 / ls) + " Ls, ringR=" + (r0 / ls) + " Ls)");
        assertTrue(jump12 < r0 * 0.05,
                "1°→2° must stay smooth (max vertex Δ=" + (jump12 / ls) + " Ls)");

        double cx0 = ringCentroid(ring0.wx);
        double cy0 = ringCentroid(ring0.wy);
        double cx1 = ringCentroid(ring1.wx);
        double cy1 = ringCentroid(ring1.wy);
        assertTrue(Math.hypot(cx1 - cx0, cy1 - cy0) < r0 * 0.02,
                "ring centre must not jump at 1° tilt");

        for (int tilt : new int[] { 0, 1, 2, 45, 90 }) {
            List<OrbitPolylineWorldXY> polys = SystemMapPipeline.rebuildOrbitPolylines(model, model.positionsMetres(),
                    96, Double.NaN, false, null, MapScaleMode.TRUE_SCALE, tilt);
            assertBodyOnBinaryBarycentreOrbitRingAtViewTilt(model, bodies, polys, "B", tilt, 0.02, 5.0);
        }
    }

    @Test
    @DisplayName("Flattening i=0 for all samples collapses out-of-plane span (regression probe)")
    void inclinedKepler_flattenedToMapPlane_hasNoDroppedAxisSpan() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        applyCoeusHighInclinationKeplerElements(bodies);
        BodyInfo a4 = bodies.get(Integer.valueOf(coeus.bodyIdByLabel("A 4")));
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        double full3d = assertKeplerDroppedAxisSpanMetres(a4, model.projectionAxis0(), model.projectionAxis1());
        double flattened = keplerDroppedAxisSpanMetres(a4, model.projectionAxis0(), model.projectionAxis1(), 0.0);
        assertTrue(full3d > 1.0e10);
        assertTrue(flattened < full3d * 0.01,
                "i=0 flatten must collapse out-of-plane span: full=" + full3d + " flat=" + flattened);
    }

    @Test
    void screenChordSegmentCount_neverBelowLegacyFloor() {
        Map<Integer, BodyInfo> bodies = gasGiantWithSingleMoon();
        Map<Integer, double[]> pos = SystemOrbitGeometry.bodyPositionsMetres(bodies, Instant.EPOCH, true);
        int legacySeg = 96;
        double tinyScalePxPerM = 1e-15;
        var polys = SystemOrbitGeometry.orbitPolylinesWorldMetresXY(bodies, pos, legacySeg, tinyScalePxPerM, 0, 1,
                false, null, MapScaleMode.SCHEMATIC);
        int moon = findByShortName(bodies, "2 a");
        OrbitPolylineWorldXY ring = null;
        for (OrbitPolylineWorldXY p : polys) {
            if (p != null && p.bodyId == moon) {
                ring = p;
                break;
            }
        }
        assertNotNull(ring);
        assertTrue(ring.wx.length >= legacySeg,
                "screen-chord tessellation must not drop below legacy segment floor: " + ring.wx.length);
    }

    private static OrbitPolylineWorldXY findPolyline(java.util.List<OrbitPolylineWorldXY> polys, int bodyId) {
        if (polys == null) {
            return null;
        }
        for (OrbitPolylineWorldXY p : polys) {
            if (p != null && p.bodyId == bodyId) {
                return p;
            }
        }
        return null;
    }

    @Test
    void isMoonSatelliteBody_distinguishesMoonFromPlanetBinaryCoOrbiter() {
        Map<Integer, BodyInfo> bodies = szG_d10_2113_1bc();
        BodyInfo moon = new BodyInfo();
        moon.setBodyShortName("2 a");
        moon.setImmediateParentBodyId(findByShortName(bodies, "2"));
        assertTrue(SystemOrbitGeometry.isMoonSatelliteBody(moon, moonOnlyParents(bodies, 20)));

        BodyInfo co = bodies.get(Integer.valueOf(findByShortName(bodies, "1 b")));
        assertFalse(SystemOrbitGeometry.isMoonSatelliteBody(co, bodies));
        assertTrue(SystemOrbitGeometry.isMoonSatelliteBody(co));
    }

    private static Map<Integer, BodyInfo> moonOnlyParents(Map<Integer, BodyInfo> bodies, int giantId) {
        Map<Integer, BodyInfo> m = new HashMap<>(bodies);
        BodyInfo giant = new BodyInfo();
        giant.setBodyShortName("2");
        giant.setPlanetClass("Gas giant");
        m.put(Integer.valueOf(giantId), giant);
        return m;
    }

    private static org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY findPlanetBinaryMutualRing(
            org.dce.ed.systemmap.SystemMapModel model, int journalNullId) {
        return org.dce.ed.testutil.OrbitGeometryTestSupport.findPlanetBinaryMutualRing(model, journalNullId);
    }

    private static Map<Integer, BodyInfo> szG_d10_2113_1bc() {
        Map<Integer, BodyInfo> bodies = new HashMap<>();
        BodyInfo star = new BodyInfo();
        star.setBodyShortName("Byua Aim SZ-G d10-2113");
        star.setStarType("F");
        star.setDistanceLs(0);
        bodies.put(Integer.valueOf(0), star);

        BodyInfo giant = new BodyInfo();
        giant.setBodyShortName("1");
        giant.setPlanetClass("Sudarsky class I gas giant");
        giant.setDistanceLs(2250.91);
        giant.setImmediateParentBodyId(0);
        bodies.put(Integer.valueOf(10), giant);

        BodyInfo bary = new BodyInfo();
        bary.setScanBarycentreRow(true);
        bary.setSemiMajorAxisM(2_030_467_450.0);
        bodies.put(Integer.valueOf(12), bary);

        BodyInfo pB = new BodyInfo();
        pB.setBodyShortName("1 b");
        pB.setPlanetClass("Icy body");
        pB.setDistanceLs(2250.79);
        pB.setImmediateParentBodyId(12);
        bodies.put(Integer.valueOf(13), pB);

        BodyInfo pC = new BodyInfo();
        pC.setBodyShortName("1 c");
        pC.setPlanetClass("Icy body");
        pC.setDistanceLs(2250.82);
        pC.setImmediateParentBodyId(12);
        bodies.put(Integer.valueOf(14), pC);
        return bodies;
    }

    /** Gas giant 2 with co-orbiting 2 a / 2 b (RockHound-style ~3130 Ls subsystem). */
    private static Map<Integer, BodyInfo> gasGiant2BinaryMoons() {
        Map<Integer, BodyInfo> bodies = new HashMap<>();
        BodyInfo star = new BodyInfo();
        star.setBodyShortName("Byua Aim SZ-G d10-2113");
        star.setStarType("F");
        bodies.put(Integer.valueOf(0), star);

        BodyInfo giant = new BodyInfo();
        giant.setBodyShortName("2");
        giant.setPlanetClass("Sudarsky class I gas giant");
        giant.setDistanceLs(3130.0);
        giant.setImmediateParentBodyId(0);
        bodies.put(Integer.valueOf(20), giant);

        BodyInfo bary = new BodyInfo();
        bary.setScanBarycentreRow(true);
        bary.setSemiMajorAxisM(2_100_000_000.0);
        bodies.put(Integer.valueOf(25), bary);

        BodyInfo pA = new BodyInfo();
        pA.setBodyShortName("2 a");
        pA.setPlanetClass("Icy body");
        pA.setDistanceLs(3130.0);
        pA.setImmediateParentBodyId(25);
        bodies.put(Integer.valueOf(21), pA);

        BodyInfo pB = new BodyInfo();
        pB.setBodyShortName("2 b");
        pB.setPlanetClass("Icy body");
        pB.setDistanceLs(3127.0);
        pB.setImmediateParentBodyId(25);
        bodies.put(Integer.valueOf(22), pB);
        return bodies;
    }

    /** Span on world axis not used by map projection (out-of-plane for default 0/1). */
    private static double assertKeplerDroppedAxisSpanMetres(BodyInfo body, int mapProjA0, int mapProjA1) {
        double span = keplerDroppedAxisSpanMetres(body, mapProjA0, mapProjA1, Double.NaN);
        assertTrue(span > 0.0, "expected finite Kepler samples");
        return span;
    }

    private static double keplerDroppedAxisSpanMetres(BodyInfo body, int mapProjA0, int mapProjA1,
            double inclinationOverrideRad) {
        int dropped = 3 - mapProjA0 - mapProjA1;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 32; i++) {
            double M = (Math.PI * 2.0 * i) / 32;
            double[] rel = SystemOrbitGeometry.keplerDisplacementMetres(body, M, inclinationOverrideRad);
            if (rel == null || rel.length <= dropped) {
                continue;
            }
            double v = SystemOrbitGeometry.worldAxisMetres(rel, dropped);
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return max - min;
    }

    private static void applyCoeusHighInclinationKeplerElements(Map<Integer, BodyInfo> bodies) {
        BodyInfo a1 = bodies.get(Integer.valueOf(findByShortName(bodies, "A 1")));
        if (a1 != null) {
            a1.setSemiMajorAxisM(8.93e10);
            a1.setEccentricity(0.22);
            a1.setOrbitalInclination(1.2);
            a1.setAscendingNode(45.0);
            a1.setPeriapsis(10.0);
            a1.setMeanAnomaly(0.0);
            a1.setOrbitalPeriod(1.5e7);
        }
        BodyInfo a4 = bodies.get(Integer.valueOf(findByShortName(bodies, "A 4")));
        if (a4 != null) {
            a4.setSemiMajorAxisM(2.298e11);
            a4.setEccentricity(0.35);
            a4.setOrbitalInclination(89.0);
            a4.setAscendingNode(120.0);
            a4.setPeriapsis(200.0);
            a4.setMeanAnomaly(1.0);
            a4.setOrbitalPeriod(2.2e7);
        }
    }

    private static Map<Integer, BodyInfo> gasGiantWithSingleMoon() {
        Map<Integer, BodyInfo> bodies = new HashMap<>();
        BodyInfo star = new BodyInfo();
        star.setStarType("F");
        bodies.put(Integer.valueOf(0), star);
        BodyInfo giant = new BodyInfo();
        giant.setBodyShortName("2");
        giant.setPlanetClass("Gas giant");
        giant.setDistanceLs(3130.0);
        giant.setImmediateParentBodyId(0);
        bodies.put(Integer.valueOf(20), giant);
        BodyInfo moon = new BodyInfo();
        moon.setBodyShortName("2 a");
        moon.setPlanetClass("Icy body");
        moon.setDistanceLs(3130.5);
        moon.setImmediateParentBodyId(20);
        bodies.put(Integer.valueOf(21), moon);
        return bodies;
    }
}
