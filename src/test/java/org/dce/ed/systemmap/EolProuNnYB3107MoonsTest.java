package org.dce.ed.systemmap;



import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertSame;

import static org.junit.jupiter.api.Assertions.assertTrue;



import java.io.IOException;

import java.time.Instant;

import java.util.Map;



import org.dce.ed.state.BodyInfo;

import org.dce.ed.testutil.OrbitGeometryTestSupport;

import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;

import org.junit.jupiter.api.Test;



/**

 * Eol Prou NN-Y b31-0: gas giant {@code 7} (journal id 28) with icy moons; {@code 7 d}/{@code 7 e} share Null:32.

 */

class EolProuNnYB3107MoonsTest {



    private static final double LS = SystemOrbitGeometry.LIGHT_SECOND_METRES;



    private static SystemMapFixture fixture;

    private static Map<Integer, BodyInfo> bodies;

    private static SystemMapModel model;

    private static int id7;

    private static int id7d;

    private static int id7e;

    private static int null32Key;



    @BeforeAll

    static void load() throws IOException {

        fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-nn-y-b31-0-7-moons.json");

        bodies = fixture.toBodies();

        model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);

        id7 = fixture.bodyIdByLabel("7");

        id7d = fixture.bodyIdByLabel("7 d");

        id7e = fixture.bodyIdByLabel("7 e");

        null32Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(32);

    }



    @Test

    void moons_classifiedAsSatellites() {

        assertTrue(SystemOrbitGeometry.isMoonSatelliteBody(bodies.get(id7d), bodies));

        assertTrue(SystemOrbitGeometry.isMoonSatelliteBody(bodies.get(id7e), bodies));

    }



    @Test

    void moons_resolveToNull32Barycentre_notDirectlyTo7() {

        assertEquals(null32Key, model.resolveParentBodyId(id7d), "7 d");

        assertEquals(null32Key, model.resolveParentBodyId(id7e), "7 e");

        assertEquals(id7, SystemOrbitGeometry.planetBinaryBarycentreHierarchyParentMapKey(null32Key, bodies));

    }



    @Test

    void gasGiant7_isMoonHostSubsystemHub() {

        assertTrue(model.subsystemHubBodyIds().contains(Integer.valueOf(id7)),

                "gas giant 7 is a moon-host subsystem hub");

    }



    @Test

    void moon7a_onOrbitRingAroundPlanet7() {

        OrbitGeometryTestSupport.assertBodyOnPerBodyOrbitRing(model, bodies, "7 a", 0.5);

    }



    @Test

    void withSystemSession_guideRingsMatchAlignedDots() {

        SystemSession session = SystemSessionFactory.open(new SystemMapSystemLoader.Loaded(

                fixture.name, bodies, "cache"));

        Assumptions.assumeTrue(session.hasModel(), "journal-backed model from fixture bodies");

        SystemMapModel sessionModel = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true, session);

        OrbitGeometryTestSupport.assertBodyOnPerBodyOrbitRing(sessionModel, bodies, "7 a", 0.5);

        OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(sessionModel, bodies, "7 d", 32, 0.5);

        OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(sessionModel, bodies, "7 e", 32, 0.5);

        int baryKey = SystemOrbitGeometry.planetBinaryBarycentreMapKey(32);

        OrbitPolylineWorldXY mutual = OrbitGeometryTestSupport.findPlanetBinaryMutualRing(sessionModel, 32);

        assertNotNull(mutual);

        double bx = sessionModel.mapPlaneX(baryKey);

        double by = sessionModel.mapPlaneY(baryKey);

        double cx = OrbitGeometryTestSupport.ringCentroid(mutual.wx);

        double cy = OrbitGeometryTestSupport.ringCentroid(mutual.wy);

        double miss = Math.hypot(bx - cx, by - cy) / LS;

        assertTrue(miss < 2.0, "Null:32 barycentre on mutual ring centre; missLs=" + miss);

    }



    @Test

    void binaryMoons_onMutualRingAroundBarycentre() {

        assertTrue(model.hasPlanetBinaryMutualRing(32));

        assertFalse(model.hasOrbitRingForBody(id7d), "7 d uses mutual ring, not per-body stroke around 7");

        assertFalse(model.hasOrbitRingForBody(id7e));

        OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(model, bodies, "7 d", 32, 0.5);

        OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(model, bodies, "7 e", 32, 0.5);

    }



    @Test

    void hierarchy_null32UnderPlanet7_notStar() {

        SystemMapHierarchyBuilder.Graph g = SystemMapHierarchyBuilder.build(fixture.name, model, bodies);

        SystemMapHierarchyBuilder.Node star = findNode(g.root, "Eol Prou NN-Y b31-0");

        assertNotNull(star, "primary star");

        SystemMapHierarchyBuilder.Node planet7 = findNode(star, "7");

        assertNotNull(planet7, "gas giant 7 must appear under star");

        SystemMapHierarchyBuilder.Node null32 = findNode(planet7, "Null:32");

        assertNotNull(null32, "Null:32 hub must sit under planet 7, not star");

        SystemMapHierarchyBuilder.Node moon7d = findNode(null32, "7 d");

        SystemMapHierarchyBuilder.Node moon7e = findNode(null32, "7 e");

        assertNotNull(moon7d);

        assertNotNull(moon7e);

        assertSame(planet7, parentNode(g, null32));

    }



    @Test

    void hierarchy_host7FullBodyNameOnly_null32UnderPlanet7() throws Exception {

        SystemMapFixture fx = SystemMapFixtureLoader.loadClasspath("eol-prou-nn-y-b31-0-7-moons.json");

        java.util.HashMap<Integer, BodyInfo> bodies = new java.util.HashMap<>(fx.toBodies());

        BodyInfo planet7 = bodies.get(Integer.valueOf(fx.bodyIdByLabel("7")));

        planet7.setBodyShortName("");

        planet7.setBodyName("Eol Prou NN-Y b31-0 7");

        BodyInfo bary = bodies.get(Integer.valueOf(32));

        bary.setImmediateParentBodyId(-1);

        bary.setJournalParentRefs(java.util.List.of());

        SystemMapModel model = SystemMapPipeline.build(fx.name, bodies, Instant.EPOCH, true);

        SystemMapHierarchyBuilder.Graph g = SystemMapHierarchyBuilder.build(fx.name, model, bodies);

        int id7 = fx.bodyIdByLabel("7");

        int null32Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(32);

        assertEquals(id7, SystemOrbitGeometry.planetBinaryBarycentreHierarchyParentMapKey(null32Key, bodies));

        SystemMapHierarchyBuilder.Node null32Node = findNode(g.root, "Null:32");

        assertNotNull(null32Node, "Null:32 hub node");

        assertEquals(id7, null32Node.parentKey,

                "Null:32 must parent to planet 7 when host has full body name only");

    }



    @Test

    void hierarchy_unlinkedBarycentreRow_null32UnderPlanet7_notStar() throws Exception {

        SystemMapFixture fx = SystemMapFixtureLoader.loadClasspath("eol-prou-nn-y-b31-0-7-moons.json");

        java.util.HashMap<Integer, BodyInfo> bodies = new java.util.HashMap<>(fx.toBodies());

        BodyInfo bary = bodies.get(Integer.valueOf(32));

        bary.setImmediateParentBodyId(-1);

        bary.setJournalParentRefs(java.util.List.of());

        SystemMapModel model = SystemMapPipeline.build(fx.name, bodies, Instant.EPOCH, true);

        SystemMapHierarchyBuilder.Graph g = SystemMapHierarchyBuilder.build(fx.name, model, bodies);

        SystemMapHierarchyBuilder.Node star = findNode(g.root, "Eol Prou NN-Y b31-0");

        SystemMapHierarchyBuilder.Node planet7 = findNode(star, "7");

        assertNotNull(planet7, "gas giant 7");

        SystemMapHierarchyBuilder.Node null32 = findNode(planet7, "Null:32");

        assertNotNull(null32, "Null:32 must sit under planet 7 even when ScanBaryCentre row lacks host link");

        assertSame(planet7, parentNode(g, null32));

    }



    private static SystemMapHierarchyBuilder.Node findNode(SystemMapHierarchyBuilder.Node node, String label) {

        if (label.equals(node.label)) {

            return node;

        }

        for (SystemMapHierarchyBuilder.Node child : node.children) {

            SystemMapHierarchyBuilder.Node hit = findNode(child, label);

            if (hit != null) {

                return hit;

            }

        }

        return null;

    }



    private static SystemMapHierarchyBuilder.Node parentNode(SystemMapHierarchyBuilder.Graph graph,

            SystemMapHierarchyBuilder.Node node) {

        return graph.nodeByKey.get(Integer.valueOf(node.parentKey));

    }



    @Test

    void map_moon7dCacheParentOnly_stillUsesNull32Barycentre() throws Exception {

        SystemMapFixture fx = SystemMapFixtureLoader.loadClasspath("eol-prou-nn-y-b31-0-7-moons.json");

        java.util.HashMap<Integer, BodyInfo> bodies = new java.util.HashMap<>(fx.toBodies());

        BodyInfo bary = bodies.get(Integer.valueOf(32));

        bary.setJournalParentRefs(java.util.List.of("Planet:28", "Star:0"));

        BodyInfo d = bodies.get(Integer.valueOf(fx.bodyIdByLabel("7 d")));

        d.setJournalParentRefs(java.util.List.of());

        d.setImmediateParentBodyId(28);

        BodyInfo e = bodies.get(Integer.valueOf(fx.bodyIdByLabel("7 e")));

        SystemMapModel model = SystemMapPipeline.build(fx.name, bodies, Instant.EPOCH, true);

        int null32Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(32);

        assertEquals(null32Key, model.resolveParentBodyId(fx.bodyIdByLabel("7 d")));

        assertFalse(model.hasOrbitRingForBody(fx.bodyIdByLabel("7 d")));

    }



    @Test

    void map_planetHostOnlyOn7d_inferBinaryFrom7eSibling() throws Exception {

        SystemMapFixture fx = SystemMapFixtureLoader.loadClasspath("eol-prou-nn-y-b31-0-7-moons.json");

        java.util.HashMap<Integer, BodyInfo> bodies = new java.util.HashMap<>(fx.toBodies());

        int id7d = fx.bodyIdByLabel("7 d");

        int id7e = fx.bodyIdByLabel("7 e");

        int id7a = fx.bodyIdByLabel("7 a");

        BodyInfo d = bodies.get(Integer.valueOf(id7d));

        d.setJournalParentRefs(java.util.List.of("Planet:28", "Star:0"));

        d.setImmediateParentBodyId(28);

        BodyInfo e = bodies.get(Integer.valueOf(id7e));

        e.setJournalParentRefs(java.util.List.of("Null:32", "Planet:28", "Star:0"));

        e.setImmediateParentBodyId(28);

        SystemMapModel model = SystemMapPipeline.build(fx.name, bodies, Instant.EPOCH, true);

        int null32Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(32);

        assertEquals(null32Key, model.resolveParentBodyId(id7d), "7 d with Planet-only cache refs");

        assertEquals(null32Key, model.resolveParentBodyId(id7e));

        assertEquals(28, model.resolveParentBodyId(id7a), "7 a stays on host 7");

        assertTrue(model.hasPlanetBinaryMutualRing(32));

        assertFalse(model.hasOrbitRingForBody(id7d));

        assertFalse(model.hasOrbitRingForBody(id7e));

        double moonSep = Math.hypot(model.mapPlaneX(id7d) - model.mapPlaneX(id7e),

                model.mapPlaneY(id7d) - model.mapPlaneY(id7e)) / LS;

        assertTrue(moonSep < 5.0, "7 d and 7 e on mutual ring; sep=" + moonSep + " Ls");

    }



    @Test

    void map_cacheStyleParents_moonsOnMutualRingNotHost7() throws Exception {

        SystemMapFixture fx = SystemMapFixtureLoader.loadClasspath("eol-prou-nn-y-b31-0-7-moons.json");

        java.util.HashMap<Integer, BodyInfo> bodies = new java.util.HashMap<>(fx.toBodies());

        BodyInfo bary = bodies.get(Integer.valueOf(32));

        bary.setImmediateParentBodyId(-1);

        bary.setJournalParentRefs(java.util.List.of("Planet:28", "Star:0"));

        int id7d = fx.bodyIdByLabel("7 d");

        int id7e = fx.bodyIdByLabel("7 e");

        BodyInfo d = bodies.get(Integer.valueOf(id7d));

        BodyInfo e = bodies.get(Integer.valueOf(id7e));

        d.setImmediateParentBodyId(28);

        e.setImmediateParentBodyId(28);

        SystemMapModel model = SystemMapPipeline.build(fx.name, bodies, Instant.EPOCH, true);

        int null32Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(32);

        assertEquals(null32Key, model.resolveParentBodyId(id7d), "7 d orbit parent");

        assertEquals(null32Key, model.resolveParentBodyId(id7e), "7 e orbit parent");

        assertTrue(model.hasPlanetBinaryMutualRing(32));

        assertFalse(model.hasOrbitRingForBody(id7d));

        assertFalse(model.hasOrbitRingForBody(id7e));

        double moonSep = Math.hypot(model.mapPlaneX(id7d) - model.mapPlaneX(id7e),

                model.mapPlaneY(id7d) - model.mapPlaneY(id7e)) / LS;

        assertTrue(moonSep < 5.0, "7 d and 7 e close on mutual ring; sep=" + moonSep + " Ls");

    }



    @Test

    void barycentre_pairOrbitsHost7() {

        double sep7d = Math.hypot(model.mapPlaneX(id7d) - model.mapPlaneX(id7),

                model.mapPlaneY(id7d) - model.mapPlaneY(id7)) / LS;

        double sep7e = Math.hypot(model.mapPlaneX(id7e) - model.mapPlaneX(id7),

                model.mapPlaneY(id7e) - model.mapPlaneY(id7)) / LS;

        double hint = Math.abs(bodies.get(id7d).getDistanceLs() - bodies.get(id7).getDistanceLs());

        assertTrue(sep7d < Math.max(25.0, hint * 1.5),

                "binary pair centre should orbit near host 7; sep7d=" + sep7d + " Ls hint=" + hint + " Ls");

        assertTrue(sep7e < Math.max(25.0, hint * 1.5), "sep7e=" + sep7e);

        double moonSep = Math.hypot(model.mapPlaneX(id7d) - model.mapPlaneX(id7e),

                model.mapPlaneY(id7d) - model.mapPlaneY(id7e)) / LS;

        assertTrue(moonSep < 5.0, "7 d and 7 e should be close on their mutual ring; sep=" + moonSep + " Ls");

    }

}


