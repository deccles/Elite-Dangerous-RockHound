package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmap.SystemMapHierarchyBuilder.NodeKind;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class SystemMapHierarchyBuilderTest {

    private static SystemMapFixture fixture;
    private static SystemMapHierarchyBuilder.Graph graph;

    @BeforeAll
    static void buildGraph() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eor-aowsy-ri-k-c8-3670.json");
        Map<Integer, BodyInfo> bodies = fixture.toBodies();
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);
        graph = SystemMapHierarchyBuilder.build(fixture.name, model, bodies);
    }

    @Test
    void eorAowsy_containsBcd2MoonUnderBcd2() {
        SystemMapHierarchyBuilder.Node bcd2 = findNode(graph.root, "BCD 2");
        assertNotNull(bcd2);
        boolean hasMoon = bcd2.children.stream().anyMatch(n -> "BCD 2 a".equals(n.label));
        assertTrue(hasMoon);
    }

    @Test
    void eorAowsy_allAbranchShareStarAAncestor() {
        SystemMapHierarchyBuilder.Node starA = findNode(graph.root, "A");
        assertNotNull(starA);
        for (String label : List.of("A 1", "A 2", "A 3", "A 4", "A 2 a", "A 3 a", "A 3 b")) {
            SystemMapHierarchyBuilder.Node body = findNode(graph.root, label);
            assertNotNull(body, label);
            assertSame(starA, findBranchStarAncestor(graph, body, "A"), label + " must be under star A in graph");
        }
    }

    @Test
    void eorAowsy_aBranchParentedToC_withPlanetClassOnC_stillUnderStarA() throws IOException {
        Map<Integer, BodyInfo> bodies = new HashMap<>(fixture.toBodies());
        int idC = fixture.bodyIdByLabel("C");
        BodyInfo starC = bodies.get(Integer.valueOf(idC));
        starC.setPlanetClass("High metal content body");
        starC.setStarType(null);
        for (String label : List.of("A 1", "A 2", "A 3", "A 4", "A 3 a")) {
            bodies.get(Integer.valueOf(fixture.bodyIdByLabel(label))).setImmediateParentBodyId(idC);
        }
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);
        SystemMapHierarchyBuilder.Graph corrupt =
                SystemMapHierarchyBuilder.build(fixture.name, model, bodies);
        SystemMapHierarchyBuilder.Node starA = findNode(corrupt.root, "A");
        assertNotNull(starA);
        for (String label : List.of("A 2", "A 3", "A 3 a")) {
            SystemMapHierarchyBuilder.Node body = findNode(corrupt.root, label);
            assertNotNull(body);
            assertSame(starA, findBranchStarAncestor(corrupt, body, "A"),
                    label + " must not hang under star C when cache corrupts parent");
        }
    }

    @Test
    void coeus_a2A3UnderStarA_notSystemRoot() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        int idA = coeus.bodyIdByLabel("A");
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, true);
        int null14Hub = SystemOrbitGeometry.planetBinaryBarycentreMapKey(14);
        assertTrue(model.resolveParentBodyId(coeus.bodyIdByLabel("A 2")) == null14Hub);
        assertTrue(model.resolveParentBodyId(coeus.bodyIdByLabel("A 3")) == null14Hub);
        SystemMapHierarchyBuilder.Graph g = SystemMapHierarchyBuilder.build(coeus.name, model, bodies);
        SystemMapHierarchyBuilder.Node starA = findNode(g.root, "A");
        assertNotNull(starA);
        for (String label : List.of("A 2", "A 3")) {
            SystemMapHierarchyBuilder.Node body = findNode(g.root, label);
            assertNotNull(body, label);
            assertSame(starA, findBranchStarAncestor(g, body, "A"), label + " must be under star A");
        }
        boolean null14AtRoot = g.root.children.stream()
                .anyMatch(n -> "Null:14".equals(n.label) && n.subtitle.contains("planet-binary"));
        assertTrue(!null14AtRoot, "Null:14 hub must not be a direct child of system barycentre");
        SystemMapHierarchyBuilder.Node null14 = findNode(starA, "Null:14");
        assertNotNull(null14, "Null:14 hub should sit under star A");
        SystemMapHierarchyBuilder.Node a2 = findNode(g.root, "A 2");
        assertNotNull(a2.parentsLine);
        assertTrue(a2.parentsLine.contains("Null:14"));
        assertTrue(a2.parentsLine.contains("→ A →"));
    }

    @Test
    void coeus_moonA4a_parentsLineA4_notJournalId19() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, true);
        SystemMapHierarchyBuilder.Graph g = SystemMapHierarchyBuilder.build(coeus.name, model, bodies);
        SystemMapHierarchyBuilder.Node moon = findNode(g.root, "A 4 a");
        assertNotNull(moon);
        assertEquals("Parents: A 4", moon.parentsLine);
    }

    @Test
    void coeus_null14Hub_parentsA_notSelfNull14() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, true);
        SystemMapHierarchyBuilder.Graph g = SystemMapHierarchyBuilder.build(coeus.name, model, bodies);
        SystemMapHierarchyBuilder.Node starA = findNode(g.root, "A");
        SystemMapHierarchyBuilder.Node hub = findNode(starA, "Null:14");
        assertNotNull(hub);
        assertEquals("Parents: A → Null:0", hub.parentsLine);
    }

    @Test
    void coeus_a3_notStarWhenParentStarTypeCopiedOntoPlanet() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        BodyInfo a3 = bodies.get(Integer.valueOf(coeus.bodyIdByLabel("A 3")));
        a3.setStarType("M");
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, true);
        SystemMapHierarchyBuilder.Graph g = SystemMapHierarchyBuilder.build(coeus.name, model, bodies);
        SystemMapHierarchyBuilder.Node node = findNode(g.root, "A 3");
        assertNotNull(node);
        assertNotEquals(NodeKind.STAR, node.kind);
        assertTrue(node.subtitle.contains("Neon rich"), "subtitle should show planet class, not inherited star type");
        assertTrue(node.subtitle.contains("map: Null:14"), node.subtitle);
        assertTrue(!node.subtitle.startsWith("★"), node.subtitle);
    }

    @Test
    void coeus_moonA4a_subtitleMapA4() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, true);
        SystemMapHierarchyBuilder.Graph g = SystemMapHierarchyBuilder.build(coeus.name, model, bodies);
        SystemMapHierarchyBuilder.Node moon = findNode(g.root, "A 4 a");
        assertNotNull(moon);
        assertEquals("Parents: A 4", moon.parentsLine);
        assertTrue(moon.subtitle.contains("map: A 4"), moon.subtitle);
    }

    @Test
    void coeus_childrenUnderA_alphaLeftToRight() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, true);
        SystemMapHierarchyBuilder.Graph g = SystemMapHierarchyBuilder.build(coeus.name, model, bodies);
        java.awt.FontMetrics fm = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
                .createGraphics().getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        SystemMapHierarchyBuilder.applyLayout(g, fm, 10, 72, 36, 24);
        SystemMapHierarchyBuilder.Node starA = findNode(g.root, "A");
        assertNotNull(starA);
        java.util.List<String> leftToRight = new java.util.ArrayList<>();
        for (SystemMapHierarchyBuilder.Node child : starA.children) {
            leftToRight.add(child.label);
        }
        assertEquals(java.util.List.of("A 1", "A 4", "A 5", "Null:14"), leftToRight);
        double x1 = findNode(starA, "A 1").layoutX;
        double x4 = findNode(starA, "A 4").layoutX;
        double xHub = findNode(starA, "Null:14").layoutX;
        assertTrue(x1 < x4 && x4 < xHub, "siblings should read A–Z left to right");
    }

    @Test
    void coeus_trueScale_null14OuterRingAroundStarA() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        int idA = coeus.bodyIdByLabel("A");
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        int ringId = SystemOrbitGeometry.PLANET_BINARY_OUTER_ORBIT_RING_ID_BASE - 14;
        double starX = model.mapPlaneX(idA);
        double starY = model.mapPlaneY(idA);
        double expectedR = 1572.0 * SystemOrbitGeometry.LIGHT_SECOND_METRES;
        boolean found = false;
        for (SystemOrbitGeometry.OrbitPolylineWorldXY poly : model.orbitPolylines()) {
            if (poly == null || poly.bodyId != ringId || poly.wx == null || poly.wx.length < 3) {
                continue;
            }
            found = true;
            double sum = 0.0;
            for (int i = 0; i < poly.wx.length; i++) {
                sum += Math.hypot(poly.wx[i] - starX, poly.wy[i] - starY);
            }
            double meanR = sum / poly.wx.length;
            assertTrue(Math.abs(meanR - expectedR) <= expectedR * 0.08,
                    "Null:14 outer ring should orbit star A at ~1570 Ls, meanR=" + (meanR / SystemOrbitGeometry.LIGHT_SECOND_METRES));
        }
        assertTrue(found, "wide-binary Coeus should include planet-binary outer ring for Null:14");
    }

    @Test
    void coeus_trueScale_starsShareMutualBarycentreRing_notPerStarOrigin() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        int idA = coeus.bodyIdByLabel("A");
        int idB = coeus.bodyIdByLabel("B");
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        assertFalse(model.hasOrbitRingForBody(idA), "star A should not have heliocentric per-star ring");
        assertFalse(model.hasOrbitRingForBody(idB), "star B should not have heliocentric per-star ring");
        assertTrue(model.hasBarycentreMutualRing(), "A and B should share one mutual barycentre ring");
    }

    static Stream<String> coeusPrimaryBranchDirectStarChildren() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        return OrbitGeometryTestSupport.directResolvedMajorChildrenOfStar(model, bodies, "A").stream();
    }

    @ParameterizedTest(name = "Coeus {0} orbits star A")
    @MethodSource("coeusPrimaryBranchDirectStarChildren")
    void coeus_trueScale_primaryBranchDirectStarChildren(String label) throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        int idA = coeus.bodyIdByLabel("A");
        int bodyId = coeus.bodyIdByLabel(label);
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        assertEquals(idA, model.resolveParentBodyId(bodyId), label + " should orbit star A directly");
        OrbitGeometryTestSupport.assertPerBodyOrbitRingCentredOnResolvedParent(model, bodies, label,
                model.orbitPolylines(), 200.0);
        OrbitGeometryTestSupport.assertExactlyOneDirectParentOrbitStroke(model, bodies, label,
                model.orbitPolylines(), 200.0);
    }

    @Test
    void coeus_trueScale_a1_singleStroke_noSchematicCircle() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        BodyInfo a1 = bodies.get(Integer.valueOf(coeus.bodyIdByLabel("A 1")));
        a1.setJournalParentRefs(java.util.List.of("Star:0", "Null:0"));
        applyCoeusHighInclinationKeplerElements(bodies);
        Instant t11 = Instant.EPOCH.plus(java.time.temporal.ChronoUnit.DAYS.getDuration().multipliedBy(402));
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, t11, false, MapScaleMode.TRUE_SCALE);
        List<SystemOrbitGeometry.OrbitPolylineWorldXY> polys = model.orbitPolylines();
        OrbitGeometryTestSupport.assertNoSchematicConcentricBranchRings(polys);
        OrbitGeometryTestSupport.assertExactlyOneDirectParentOrbitStroke(model, bodies, "A 1", polys, 200.0);
        OrbitGeometryTestSupport.assertNoEllipticalAndCircularOrbitPairNearParent(model, bodies, "A 1", polys,
                200.0);
        Map<Integer, double[]> kepler = SystemOrbitGeometry.bodyPositionsMetres(bodies, t11, false);
        Map<Integer, double[]> playback = SystemMapPipeline.refreshPositionsForPlayback(model, kepler, t11, false);
        SystemMapModel playModel = SystemMapPipeline.playbackBase(bodies, model.projectionAxis0(),
                model.projectionAxis1(), playback, model.wideBinaryFlattenFrame(), MapScaleMode.SCHEMATIC);
        var rebuilt = SystemMapPipeline.rebuildOrbitPolylines(playModel, playback, 96, Double.NaN, false, null,
                MapScaleMode.TRUE_SCALE);
        OrbitGeometryTestSupport.assertNoSchematicConcentricBranchRings(rebuilt);
        OrbitGeometryTestSupport.assertExactlyOneDirectParentOrbitStroke(playModel, bodies, "A 1", rebuilt, 200.0);
        OrbitGeometryTestSupport.assertNoEllipticalAndCircularOrbitPairNearParent(playModel, bodies, "A 1", rebuilt,
                200.0);
    }

    @Test
    void coeus_trueScale_a1_keplerEllipseAroundStarA() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        applyCoeusHighInclinationKeplerElements(bodies);
        int idA1 = coeus.bodyIdByLabel("A 1");
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        SystemOrbitGeometry.OrbitPolylineWorldXY ring = null;
        for (SystemOrbitGeometry.OrbitPolylineWorldXY p : model.orbitPolylines()) {
            if (p != null && p.bodyId == idA1) {
                ring = p;
                break;
            }
        }
        assertNotNull(ring, "A 1 should have a Kepler orbit stroke at true scale");
        OrbitGeometryTestSupport.assertExactlyOneDirectParentOrbitStroke(model, bodies, "A 1",
                model.orbitPolylines(), 200.0);
        OrbitGeometryTestSupport.assertNoEllipticalAndCircularOrbitPairNearParent(model, bodies, "A 1",
                model.orbitPolylines(), 200.0);
        if (!ring.estimated) {
            OrbitGeometryTestSupport.assertOrbitPolylineAspectRatioSane(ring, 12.0);
            OrbitGeometryTestSupport.assertOrbitPolylineIsNonCircularKepler(ring, 0.12);
        }
    }

    @Test
    void coeus_trueScale_a4_keplerNotSquishedAtStarA() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        applyCoeusHighInclinationKeplerElements(bodies);
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        int idA4 = coeus.bodyIdByLabel("A 4");
        SystemOrbitGeometry.OrbitPolylineWorldXY ring = null;
        for (SystemOrbitGeometry.OrbitPolylineWorldXY p : model.orbitPolylines()) {
            if (p != null && p.bodyId == idA4) {
                ring = p;
                break;
            }
        }
        assertNotNull(ring);
        OrbitGeometryTestSupport.assertExactlyOneDirectParentOrbitStroke(model, bodies, "A 4",
                model.orbitPolylines(), 200.0);
        OrbitGeometryTestSupport.assertOrbitPolylineAspectRatioSane(ring, 12.0);
        OrbitGeometryTestSupport.assertOrbitPolylineIsNonCircularKepler(ring, 0.12);
    }

    @Test
    void coeus_trueScale_a5_keplerNotSquishedAtStarA() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        int idA = coeus.bodyIdByLabel("A");
        int idA5 = 27;
        BodyInfo a5 = new BodyInfo();
        a5.setBodyName("Coeus A 5");
        a5.setBodyShortName("A 5");
        a5.setDistanceLs(1850.0);
        a5.setPlanetClass("Class II gas giant");
        a5.setImmediateParentBodyId(idA);
        bodies.put(Integer.valueOf(idA5), a5);
        applyCoeusHighInclinationKeplerElements(bodies);
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        assertEquals(idA, model.resolveParentBodyId(idA5));
        OrbitGeometryTestSupport.assertExactlyOneDirectParentOrbitStroke(model, bodies, "A 5",
                model.orbitPolylines(), 200.0);
        SystemOrbitGeometry.OrbitPolylineWorldXY ring = null;
        for (SystemOrbitGeometry.OrbitPolylineWorldXY p : model.orbitPolylines()) {
            if (p != null && p.bodyId == idA5) {
                ring = p;
                break;
            }
        }
        assertNotNull(ring);
        OrbitGeometryTestSupport.assertOrbitPolylineAspectRatioSane(ring, 12.0);
        OrbitGeometryTestSupport.assertOrbitPolylineIsNonCircularKepler(ring, 0.12);
    }

    @Test
    void coeus_trueScale_aBranchDirectChildren_keplerNotSquishedAtStarA() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        applyCoeusHighInclinationKeplerElements(bodies);
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        OrbitGeometryTestSupport.assertDirectPrimaryBranchPlanetOrbitsNotSquished(model, bodies,
                OrbitGeometryTestSupport.directResolvedMajorChildrenOfStar(model, bodies, "A"),
                model.orbitPolylines(), 12.0, 200.0);
    }

    private static void applyCoeusHighInclinationKeplerElements(Map<Integer, BodyInfo> bodies) {
        BodyInfo a1 = bodies.get(Integer.valueOf(OrbitGeometryTestSupport.findByShortName(bodies, "A 1")));
        if (a1 != null) {
            a1.setSemiMajorAxisM(8.93e10);
            a1.setEccentricity(0.22);
            a1.setOrbitalInclination(1.2);
            a1.setAscendingNode(45.0);
            a1.setPeriapsis(10.0);
            a1.setMeanAnomaly(0.0);
            a1.setOrbitalPeriod(1.5e7);
        }
        BodyInfo a4 = bodies.get(Integer.valueOf(OrbitGeometryTestSupport.findByShortName(bodies, "A 4")));
        if (a4 != null) {
            a4.setSemiMajorAxisM(2.298e11);
            a4.setEccentricity(0.35);
            a4.setOrbitalInclination(89.0);
            a4.setAscendingNode(120.0);
            a4.setPeriapsis(200.0);
            a4.setMeanAnomaly(1.0);
            a4.setOrbitalPeriod(2.2e7);
        }
        BodyInfo a5 = bodies.get(Integer.valueOf(OrbitGeometryTestSupport.findByShortName(bodies, "A 5")));
        if (a5 != null) {
            a5.setSemiMajorAxisM(1.85e11);
            a5.setEccentricity(0.28);
            a5.setOrbitalInclination(88.5);
            a5.setAscendingNode(30.0);
            a5.setPeriapsis(90.0);
            a5.setMeanAnomaly(2.0);
            a5.setOrbitalPeriod(1.9e7);
        }
    }

    @Test
    void coeus_trueScale_playback_starsAdvanceOnMutualBarycentreRing() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        int idA = coeus.bodyIdByLabel("A");
        int idB = coeus.bodyIdByLabel("B");
        /*
         * Fixture has no orbital elements; give B a Kepler orbit so mean anomaly advances (half a year ≠ full period).
         */
        BodyInfo starB = bodies.get(Integer.valueOf(idB));
        double aMetres = 120_000.0 * SystemOrbitGeometry.LIGHT_SECOND_METRES;
        starB.setSemiMajorAxisM(aMetres);
        starB.setOrbitalPeriod(3.15576e7);
        starB.setEccentricity(0.05);
        starB.setMeanAnomaly(0.0);
        starB.setOrbitalInclination(0.0);
        starB.setAscendingNode(0.0);
        starB.setPeriapsis(0.0);
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        Instant tHalfYear = Instant.EPOCH.plus(java.time.temporal.ChronoUnit.DAYS.getDuration().multipliedBy(183));
        Map<Integer, double[]> kepler0 = SystemOrbitGeometry.bodyPositionsMetres(bodies, Instant.EPOCH, false);
        Map<Integer, double[]> kepler1 = SystemOrbitGeometry.bodyPositionsMetres(bodies, tHalfYear, false);
        int a0 = model.projectionAxis0();
        int a1 = model.projectionAxis1();
        assertTrue(Math.hypot(
                mapPlaneCoord(kepler1, idB, a0) - mapPlaneCoord(kepler0, idB, a0),
                mapPlaneCoord(kepler1, idB, a1) - mapPlaneCoord(kepler0, idB, a1)) > 1.0,
                "Kepler integration should advance star B before map flatten");
        Map<Integer, double[]> play0 = SystemMapPipeline.refreshPositionsForPlayback(model, kepler0, Instant.EPOCH,
                false);
        Map<Integer, double[]> play1 = SystemMapPipeline.refreshPositionsForPlayback(model, kepler1, tHalfYear, false);
        double b0 = mapPlaneSeparationLs(play0, idA, idB, model.projectionAxis0(), model.projectionAxis1());
        double b1 = mapPlaneSeparationLs(play1, idA, idB, model.projectionAxis0(), model.projectionAxis1());
        assertTrue(b0 > 100_000.0 && b1 > 100_000.0, "A–B chord should stay at journal wide-binary scale");
        double bx0 = mapPlaneCoord(play0, idB, model.projectionAxis0());
        double by0 = mapPlaneCoord(play0, idB, model.projectionAxis1());
        double bx1 = mapPlaneCoord(play1, idB, model.projectionAxis0());
        double by1 = mapPlaneCoord(play1, idB, model.projectionAxis1());
        assertTrue(Math.hypot(bx1 - bx0, by1 - by0) > 1.0,
                "star B should move on the mutual barycentre ring over half a sim year");
        Map<Integer, double[]> keplerFrozenYear = SystemOrbitGeometry.bodyPositionsMetres(bodies, tHalfYear, true);
        Map<Integer, double[]> keplerFrozenEpoch = SystemOrbitGeometry.bodyPositionsMetres(bodies, Instant.EPOCH,
                true);
        assertEquals(mapPlaneCoord(keplerFrozenEpoch, idB, 0), mapPlaneCoord(keplerFrozenYear, idB, 0), 1e-6,
                "freezeBarycentreStars should hold B fixed in Kepler space (schematic behaviour)");
        assertNotEquals(mapPlaneCoord(keplerFrozenYear, idB, 0), mapPlaneCoord(kepler1, idB, 0), 1e-6,
                "unfrozen Kepler should advance B over half a sim year");
    }

    @Test
    void coeus_trueScale_playbackRebuild_noSchematicBranchRings() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        applyCoeusHighInclinationKeplerElements(bodies);
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        Instant t12 = Instant.EPOCH.plus(java.time.temporal.ChronoUnit.DAYS.getDuration().multipliedBy(365));
        Map<Integer, double[]> kepler = SystemOrbitGeometry.bodyPositionsMetres(bodies, t12, false);
        Map<Integer, double[]> playback = SystemMapPipeline.refreshPositionsForPlayback(model, kepler, t12, false);
        SystemMapModel playModel = SystemMapPipeline.playbackBase(bodies, model.projectionAxis0(),
                model.projectionAxis1(), playback, model.wideBinaryFlattenFrame(), MapScaleMode.TRUE_SCALE);
        var polys = SystemMapPipeline.rebuildOrbitPolylines(playModel, playback, 96, Double.NaN);
        OrbitGeometryTestSupport.assertNoSchematicConcentricBranchRings(polys);
        assertTrue(polys.stream().anyMatch(
                p -> p != null && p.bodyId == SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID),
                "playback rebuild should keep A/B mutual ring");
        for (String label : OrbitGeometryTestSupport.directResolvedMajorChildrenOfStar(playModel, bodies, "A")) {
            OrbitGeometryTestSupport.assertPerBodyOrbitRingCentredOnResolvedParent(playModel, bodies, label, polys,
                    200.0);
            OrbitGeometryTestSupport.assertExactlyOneDirectParentOrbitStroke(playModel, bodies, label, polys, 200.0);
            if ("A 1".equals(label)) {
                OrbitGeometryTestSupport.assertNoEllipticalAndCircularOrbitPairNearParent(playModel, bodies, label,
                        polys, 200.0);
            }
        }
        OrbitGeometryTestSupport.assertDirectPrimaryBranchPlanetOrbitsNotSquished(playModel, bodies,
                List.of("A 1", "A 4"), polys, 12.0, 200.0);
    }

    @Test
    void coeus_trueScale_a2a3NearStarA_notOnAbChord() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        int idA = coeus.bodyIdByLabel("A");
        int idA2 = coeus.bodyIdByLabel("A 2");
        int idB = coeus.bodyIdByLabel("B");
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        double distA2 = Math.hypot(model.mapPlaneX(idA2) - model.mapPlaneX(idA),
                model.mapPlaneY(idA2) - model.mapPlaneY(idA))
                / SystemOrbitGeometry.LIGHT_SECOND_METRES;
        double distAB = Math.hypot(model.mapPlaneX(idB) - model.mapPlaneX(idA),
                model.mapPlaneY(idB) - model.mapPlaneY(idA))
                / SystemOrbitGeometry.LIGHT_SECOND_METRES;
        assertTrue(distA2 > 1200.0 && distA2 < 2100.0, "A 2 should orbit near A (~1570 Ls), was " + distA2);
        assertTrue(distAB > 50_000.0, "A–B separation should stay wide-binary scale");
        assertTrue(distA2 < distAB * 0.05, "A 2 must not sit on the A–B chord midpoint");
    }

    @Test
    void eolProuE1362_planetSiblingsDoNotOverlap() throws IOException {
        SystemMapFixture e1362 = SystemMapFixtureLoader.loadClasspath("eol-prou-iw-w-e1-1362-planet-binary-moon.json");
        Map<Integer, BodyInfo> bodies = e1362.toBodies();
        SystemMapModel model = SystemMapPipeline.build(e1362.name, bodies, Instant.EPOCH, true);
        SystemMapHierarchyBuilder.Graph g =
                SystemMapHierarchyBuilder.build(e1362.name, model, bodies);
        FontMetrics fm = fontMetrics();
        SystemMapHierarchyBuilder.applyLayout(g, fm, 10, 72, 36, 24);
        SystemMapHierarchyBuilder.Node n1 = findNode(g.root, "1");
        SystemMapHierarchyBuilder.Node n2 = findNode(g.root, "2");
        assertNotNull(n1);
        assertNotNull(n2);
        double right1 = subtreeRight(n1);
        double left2 = subtreeLeft(n2);
        assertTrue(left2 >= right1 + 20.0,
                "sibling subtrees must not overlap; right1=" + right1 + " left2=" + left2);
    }

    private static double subtreeLeft(SystemMapHierarchyBuilder.Node node) {
        double left = node.layoutX - node.layoutW / 2.0;
        for (SystemMapHierarchyBuilder.Node child : node.children) {
            left = Math.min(left, subtreeLeft(child));
        }
        return left;
    }

    private static double subtreeRight(SystemMapHierarchyBuilder.Node node) {
        double right = node.layoutX + node.layoutW / 2.0;
        for (SystemMapHierarchyBuilder.Node child : node.children) {
            right = Math.max(right, subtreeRight(child));
        }
        return right;
    }

    private static FontMetrics fontMetrics() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        try {
            return g2.getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        } finally {
            g2.dispose();
        }
    }

    @Test
    void eorAowsy_bNotUnderA() {
        SystemMapHierarchyBuilder.Node b = findNode(graph.root, "B");
        assertNotNull(b);
        SystemMapHierarchyBuilder.Node parent = graph.nodeByKey.get(Integer.valueOf(b.parentKey));
        assertNotNull(parent);
        assertTrue(parent.label.contains("Null:3") || parent.label.startsWith("Null:"));
    }

    private static SystemMapHierarchyBuilder.Node findBranchStarAncestor(SystemMapHierarchyBuilder.Graph graph,
            SystemMapHierarchyBuilder.Node start, String branchLetter) {
        for (SystemMapHierarchyBuilder.Node cur = start; cur != null; cur = parentNode(graph, cur)) {
            if (cur.kind == SystemMapHierarchyBuilder.NodeKind.STAR
                    && branchLetter.equalsIgnoreCase(cur.label.trim())) {
                return cur;
            }
        }
        return null;
    }

    private static SystemMapHierarchyBuilder.Node parentNode(SystemMapHierarchyBuilder.Graph graph,
            SystemMapHierarchyBuilder.Node node) {
        if (node.parentKey == Integer.MIN_VALUE) {
            return null;
        }
        return graph.nodeByKey.get(Integer.valueOf(node.parentKey));
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

    private static double mapPlaneCoord(Map<Integer, double[]> positions, int bodyId, int axis) {
        double[] p = positions.get(Integer.valueOf(bodyId));
        assertNotNull(p, "missing position for body " + bodyId);
        return SystemOrbitGeometry.worldAxisMetres(p, axis);
    }

    private static double mapPlaneSeparationLs(Map<Integer, double[]> positions, int idA, int idB, int a0, int a1) {
        double dx = mapPlaneCoord(positions, idB, a0) - mapPlaneCoord(positions, idA, a0);
        double dy = mapPlaneCoord(positions, idB, a1) - mapPlaneCoord(positions, idA, a1);
        return Math.hypot(dx, dy) / SystemOrbitGeometry.LIGHT_SECOND_METRES;
    }
}
