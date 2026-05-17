package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.dce.ed.testutil.OrbitGeometryTestSupport.assertBarycentreFarFromStar;
import static org.dce.ed.testutil.OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing;
import static org.dce.ed.testutil.OrbitGeometryTestSupport.assertNoPerBodyOrbitRing;
import static org.dce.ed.testutil.OrbitGeometryTestSupport.findByShortName;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmap.SystemMapPipeline;
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
