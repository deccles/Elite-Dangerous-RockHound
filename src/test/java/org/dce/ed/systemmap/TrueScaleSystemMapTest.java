package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.dce.ed.testutil.OrbitGeometryTestSupport.assertCompanionClusterOnTrunkRing;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.systemmap.SystemMapRules;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * True-scale pipeline: journal Kepler positions without layout compression.
 */
class TrueScaleSystemMapTest {

    private static final double LS = SystemOrbitGeometry.LIGHT_SECOND_METRES;

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static int idA;
    private static int idB;

    @BeforeAll
    static void loadFixture() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eor-aowsy-ri-k-c8-3670.json");
        bodies = fixture.toBodies();
        idA = fixture.bodyIdByLabel("A");
        idB = fixture.bodyIdByLabel("B");
    }

    private static double distLs(SystemMapModel model, int fromId, int toId) {
        double dx = model.mapPlaneX(toId) - model.mapPlaneX(fromId);
        double dy = model.mapPlaneY(toId) - model.mapPlaneY(fromId);
        return Math.hypot(dx, dy) / LS;
    }

    @Test
    @DisplayName("Eor Aowsy A–B separation matches journal heliocentric distance (~50k Ls)")
    void eorAowsy_trueScale_starSeparationNearJournal() {
        SystemMapModel trueScale = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false);
                double sep = distLs(trueScale, idA, idB);
        BodyInfo starB = bodies.get(Integer.valueOf(idB));
        double journalLs = starB != null ? Math.abs(starB.getDistanceLs()) : Double.NaN;
        assertTrue(journalLs > 45_000.0 && journalLs < 52_000.0,
                "fixture journal B heliocentric Ls=" + journalLs);
        assertTrue(sep > 38_000.0 && sep < 56_000.0,
                "true-scale map A–B Ls=" + sep + " journal=" + journalLs);
        assertTrue(Math.abs(sep - journalLs) < journalLs * 0.22,
                "map-plane separation should track journal heliocentric distance");
    }

    @Test
    @DisplayName("Eol Prou e1-1362: moon 2 a parents to host 2, not Null:2 barycentre")
    void eolProuE1362_moonParentsToHostPlanet() throws IOException {
        SystemMapFixture fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-iw-w-e1-1362-planet-binary-moon.json");
        Map<Integer, BodyInfo> bodies = fixture.toBodies();
        int id2a = fixture.bodyIdByLabel("2 a");
        int id2 = fixture.bodyIdByLabel("2");
        int resolved = SystemMapRules.resolveOrbitParentBodyId(bodies.get(Integer.valueOf(id2a)), bodies, id2a);
        assertEquals(id2, resolved, "2 a must orbit planet 2, not the Null:2 barycentre key");
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false);
        boolean moonRingAroundHost = false;
        double hostX = model.mapPlaneX(id2);
        double hostY = model.mapPlaneY(id2);
        for (SystemOrbitGeometry.OrbitPolylineWorldXY poly : model.orbitPolylines()) {
            if (poly == null || poly.bodyId != id2a || poly.wx == null) {
                continue;
            }
            double sum = 0.0;
            for (int i = 0; i < poly.wx.length; i++) {
                sum += Math.hypot(poly.wx[i] - hostX, poly.wy[i] - hostY);
            }
            double meanR = sum / poly.wx.length;
            double sep = Math.hypot(model.mapPlaneX(id2a) - hostX, model.mapPlaneY(id2a) - hostY);
            if (meanR > 0.0 && Math.abs(meanR - sep) < sep * 0.35) {
                moonRingAroundHost = true;
            }
        }
        assertTrue(moonRingAroundHost, "true-scale map should stroke 2 a around planet 2");
    }

    @Test
    @DisplayName("Coeus A 4: pipeline rebuild preserves inclined Kepler stroke (not map-plane circle)")
    void coeus_trueScale_rebuildOrbitPolylines_inclinedA4NotFlattened() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        applyCoeusInclinedA4(bodies);
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, false);
        int idA4 = coeus.bodyIdByLabel("A 4");
        OrbitPolylineWorldXY built = findOrbitPolyline(model.orbitPolylines(), idA4);
        assertNotNull(built, "initial build should stroke A 4");
        OrbitGeometryTestSupport.assertOrbitPolylineIsNonCircularKepler(built, 0.12);

        List<OrbitPolylineWorldXY> flat = SystemMapPipeline.rebuildOrbitPolylines(model, model.positionsMetres(),
                96, Double.NaN, null, 0);
        List<OrbitPolylineWorldXY> tilt90 = SystemMapPipeline.rebuildOrbitPolylines(model, model.positionsMetres(),
                96, Double.NaN, null, 90);
        OrbitPolylineWorldXY rebuilt = findOrbitPolyline(flat, idA4);
        OrbitPolylineWorldXY tilted = findOrbitPolyline(tilt90, idA4);
        assertNotNull(rebuilt);
        assertNotNull(tilted);
        OrbitGeometryTestSupport.assertOrbitPolylineNotNearPerfectCircle(rebuilt, 1.06);
        assertTrue(OrbitGeometryTestSupport.maxVertexDeltaMetres(rebuilt, tilted) > 1.0e8,
                "rebuildOrbitPolylines must honour view tilt for inclined orbits");
    }

    private static OrbitPolylineWorldXY findOrbitPolyline(List<OrbitPolylineWorldXY> polys, int bodyId) {
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

    private static void applyCoeusInclinedA4(Map<Integer, BodyInfo> bodies) {
        int id = OrbitGeometryTestSupport.findByShortName(bodies, "A 4");
        BodyInfo a4 = bodies.get(Integer.valueOf(id));
        if (a4 == null) {
            return;
        }
        a4.setSemiMajorAxisM(2.298e11);
        a4.setEccentricity(0.35);
        a4.setOrbitalInclination(89.0);
        a4.setAscendingNode(120.0);
        a4.setPeriapsis(200.0);
        a4.setMeanAnomaly(1.0);
        a4.setOrbitalPeriod(2.2e7);
    }

    @Test
    @DisplayName("True scale single-star: star-hosted planets get orbit strokes")
    void singleStar_trueScale_starHostedPlanetsHaveOrbitStrokes() throws IOException {
        SystemMapFixture single = SystemMapFixtureLoader.loadClasspath("c16-241-single-k-star.json");
        Map<Integer, BodyInfo> singleBodies = single.toBodies();
        SystemMapModel model = SystemMapPipeline.build(single.name, singleBodies, Instant.EPOCH, false);
        int id1 = single.bodyIdByLabel("A 1");
        int id2 = single.bodyIdByLabel("A 2");
        int id3 = single.bodyIdByLabel("A 3");
        assertTrue(hasOrbitStrokeForBody(model, id1), "A 1 should have an orbit stroke at true scale");
        assertTrue(hasOrbitStrokeForBody(model, id2), "A 2 should have an orbit stroke at true scale");
        assertTrue(hasOrbitStrokeForBody(model, id3), "A 3 should have an orbit stroke at true scale");
        assertTrue(model.orbitPolylines().size() >= 3,
                "true-scale single star should include per-body and/or star concentric rings");
    }

    @Test
    @DisplayName("True scale single-star: Kepler planet dot sits on its Kepler orbit stroke")
    void singleStar_trueScale_keplerPlanetDotSitsOnOrbitStroke() {
        Map<Integer, BodyInfo> singleBodies = singleStarWithKeplerPlanet();
        SystemMapModel model = SystemMapPipeline.build("Test Single Star", singleBodies, Instant.EPOCH, false);
        OrbitPolylineWorldXY orbit = findOrbitPolyline(model.orbitPolylines(), 1);
        assertNotNull(orbit, "true-scale single-star planet should get a per-body orbit stroke");

        double missLs = minDistanceToPolylineLs(model.mapPlaneX(1), model.mapPlaneY(1), orbit);
        assertTrue(missLs < 50.0,
                "planet dot should sit near its true-scale orbit stroke after SMA/journal reconcile; miss="
                        + missLs + " Ls");
    }

    private static boolean hasOrbitStrokeForBody(SystemMapModel model, int bodyId) {
        for (OrbitPolylineWorldXY poly : model.orbitPolylines()) {
            if (poly != null && poly.bodyId == bodyId && poly.wx != null && poly.wx.length >= 3) {
                return true;
            }
        }
        int starId = model.classification().centralStarId();
        if (starId < 0) {
            return false;
        }
        double starX = model.mapPlaneX(starId);
        double starY = model.mapPlaneY(starId);
        double targetR = Math.hypot(model.mapPlaneX(bodyId) - starX, model.mapPlaneY(bodyId) - starY);
        if (!(targetR > 0.0)) {
            return false;
        }
        for (OrbitPolylineWorldXY poly : model.orbitPolylines()) {
            if (poly == null || poly.wx == null || poly.wy == null || poly.wx.length < 3) {
                continue;
            }
            double sum = 0.0;
            for (int i = 0; i < poly.wx.length; i++) {
                sum += Math.hypot(poly.wx[i] - starX, poly.wy[i] - starY);
            }
            double meanR = sum / poly.wx.length;
            if (Math.abs(meanR - targetR) <= targetR * 0.12) {
                return true;
            }
        }
        return false;
    }

    private static Map<Integer, BodyInfo> singleStarWithKeplerPlanet() {
        Map<Integer, BodyInfo> out = new LinkedHashMap<>();
        BodyInfo star = new BodyInfo();
        star.setBodyId(0);
        star.setBodyName("Test Single Star");
        star.setBodyShortName("Test Single Star");
        star.setDistanceLs(0.0);
        star.setStarType("M");
        out.put(Integer.valueOf(0), star);

        BodyInfo planet = new BodyInfo();
        planet.setBodyId(1);
        planet.setBodyName("Test Single Star 1");
        planet.setBodyShortName("1");
        planet.setPlanetClass("Icy body");
        planet.setImmediateParentBodyId(0);
        planet.setDistanceLs(500.0);
        planet.setSemiMajorAxisM(120_000_000_000.0);
        planet.setEccentricity(0.25);
        planet.setOrbitalInclination(12.0);
        planet.setAscendingNode(33.0);
        planet.setPeriapsis(140.0);
        planet.setMeanAnomaly(82.0);
        planet.setOrbitalPeriod(50_000_000.0);
        out.put(Integer.valueOf(1), planet);
        return out;
    }

    private static double minDistanceToPolylineLs(double x, double y, OrbitPolylineWorldXY poly) {
        if (poly == null || poly.wx == null || poly.wy == null || poly.wx.length < 2) {
            return Double.POSITIVE_INFINITY;
        }
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < poly.wx.length; i++) {
            int j = (i + 1) % poly.wx.length;
            best = Math.min(best, pointSegmentDistance(x, y, poly.wx[i], poly.wy[i], poly.wx[j], poly.wy[j]));
        }
        return best / LS;
    }

    private static double pointSegmentDistance(double px, double py, double ax, double ay, double bx, double by) {
        double vx = bx - ax;
        double vy = by - ay;
        double wx = px - ax;
        double wy = py - ay;
        double len2 = vx * vx + vy * vy;
        if (!(len2 > 0.0)) {
            return Math.hypot(px - ax, py - ay);
        }
        double t = Math.max(0.0, Math.min(1.0, (wx * vx + wy * vy) / len2));
        return Math.hypot(px - (ax + t * vx), py - (ay + t * vy));
    }

    @Test
    @DisplayName("EOL PROU LH-U D3-2700 (journal): not triple; inner A–bary and outer A–C trunk rings")
    void eolProuD32700_journal_trueScale_innerAndOuterStellarRings() throws IOException {
        SystemMapFixture journal = SystemMapFixtureLoader.loadClasspath("eol-prou-lh-u-d3-2700-journal.json");
        Map<Integer, BodyInfo> journalBodies = journal.toBodies();
        assertTrue(SystemOrbitGeometry.isHierarchicalWideBinary(journalBodies));
        assertFalse(SystemOrbitGeometry.isHierarchicalTripleStarMap(journalBodies),
                "B ~1k Ls and C ~11k Ls must not be treated as a tight B+C pair");
        SystemMapModel model = SystemMapPipeline.build(journal.name, journalBodies, Instant.EPOCH, false);
        int idA = journal.bodyIdByLabel("A");
        int idC = journal.bodyIdByLabel("C");
        int null1Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(2);
        double sepAc = distLs(model, idA, idC);
        assertTrue(sepAc > 9_000.0 && sepAc < 13_000.0, "A–C journal true-scale separation Ls=" + sepAc);
        double innerRingLs = meanPolylineRadiusLs(model,
                SystemOrbitGeometry.HIERARCHICAL_INNER_STELLAR_PAIR_POLYLINE_ID);
        double outerRingLs = meanPolylineRadiusLs(model, SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID);
        assertTrue(innerRingLs > 80.0 && innerRingLs < 200.0,
                "inner A–barycentre ring radius Ls=" + innerRingLs);
        assertTrue(outerRingLs > innerRingLs * 8.0,
                "outer A–C trunk; inner=" + innerRingLs + " outer=" + outerRingLs);
        assertTrue(outerRingLs > 4_000.0, "outer companion trunk Ls=" + outerRingLs);
        double hubDist = Math.hypot(model.mapPlaneX(null1Key) - model.mapPlaneX(idA),
                model.mapPlaneY(null1Key) - model.mapPlaneY(idA)) / LS;
        assertTrue(hubDist > 150.0 && hubDist < 450.0, "inner barycentre near 225 Ls; was " + hubDist);
    }

    private static double meanPolylineRadiusLs(SystemMapModel model, int polylineBodyId) {
        for (OrbitPolylineWorldXY poly : model.orbitPolylines()) {
            if (poly == null || poly.bodyId != polylineBodyId || poly.wx == null || poly.wx.length < 3) {
                continue;
            }
            double sumX = 0.0;
            double sumY = 0.0;
            for (int i = 0; i < poly.wx.length; i++) {
                sumX += poly.wx[i];
                sumY += poly.wy[i];
            }
            double cx = sumX / poly.wx.length;
            double cy = sumY / poly.wy.length;
            double sumR = 0.0;
            for (int i = 0; i < poly.wx.length; i++) {
                sumR += Math.hypot(poly.wx[i] - cx, poly.wy[i] - cy);
            }
            return (sumR / poly.wx.length) / LS;
        }
        return Double.NaN;
    }

    @Test
    @DisplayName("Eor Aowsy true scale: A 2 on Kepler stroke after high-zoom polyline rebuild")
    void eorAowsy_trueScale_a2OnOrbitPolylineAfterZoomRebuild() {
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false);
        int idA2 = fixture.bodyIdByLabel("A 2");
        assertTrue(model.hasOrbitRingForBody(idA2));
        OrbitGeometryTestSupport.assertPerBodyOrbitAlignedAfterHighZoomRebuild(model, bodies, "A 2",
                2.0E-5, 0.12, 500.0);
    }

    @Test
    @DisplayName("Eor Aowsy true scale: B+C mutual orbit, D opposite BC hub on Null:2")
    void eorAowsy_trueScale_bcdClusterStructure() {
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false);
        double sepBc = distLs(model, fixture.bodyIdByLabel("B"), fixture.bodyIdByLabel("C"));
        assertTrue(sepBc > 140.0,
                "B and C should orbit Null:3, not stack; separation Ls=" + sepBc);
        OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(model, bodies, "B", 3, 2.0);
        OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(model, bodies, "C", 3, 2.0);
        OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(model, bodies, "D", 2, 3.0);
        int null3Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(3);
        double[] dPos = model.positionsMetres().get(Integer.valueOf(fixture.bodyIdByLabel("D")));
        double[] bcPos = model.positionsMetres().get(Integer.valueOf(null3Key));
        assertNotNull(dPos);
        assertNotNull(bcPos);
        int a0 = model.projectionAxis0();
        int a1 = model.projectionAxis1();
        double distDbc = Math.hypot(
                SystemOrbitGeometry.worldAxisMetres(dPos, a0) - SystemOrbitGeometry.worldAxisMetres(bcPos, a0),
                SystemOrbitGeometry.worldAxisMetres(dPos, a1) - SystemOrbitGeometry.worldAxisMetres(bcPos, a1))
                / LS;
        double mutual2 = SystemOrbitGeometry.planetBinaryMutualOrbitRadiusLsPublic(2, bodies);
        assertTrue(distDbc >= mutual2 * 0.85 && distDbc <= mutual2 * 2.2,
                "D and B+C hub on opposite sides of Null:2; dist=" + distDbc + " Ls mutual2=" + mutual2);
    }

    @Test
    @DisplayName("Eor Aowsy true scale: BCD cluster on outer trunk ring, inner ring, no stray Null hub")
    void eorAowsy_trueScale_hierarchicalTrunkRingsAndClusterAlignment() {
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false);
        int idA = fixture.bodyIdByLabel("A");
        assertCompanionClusterOnTrunkRing(model, bodies, idA,
                SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID, 0.18);
        double outerRingLs = meanPolylineRadiusLs(model,
                SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID);
        double innerRingLs = meanPolylineRadiusLs(model,
                SystemOrbitGeometry.HIERARCHICAL_INNER_STELLAR_PAIR_POLYLINE_ID);
        assertTrue(Double.isFinite(outerRingLs) && outerRingLs > 20_000.0,
                "outer A–BCD trunk ring Ls=" + outerRingLs);
        assertTrue(Double.isFinite(innerRingLs) && innerRingLs > 500.0 && innerRingLs < outerRingLs * 0.98,
                "inner A–Null:3 trunk ring Ls=" + innerRingLs + " outer=" + outerRingLs);
        int null2Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(2);
        int null3Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(3);
        assertTrue(Math.hypot(model.mapPlaneX(2) - model.mapPlaneX(null2Key),
                model.mapPlaneY(2) - model.mapPlaneY(null2Key)) < LS * 2.0,
                "scan row Null:2 must match synthetic hub after layout");
        assertTrue(Math.hypot(model.mapPlaneX(3) - model.mapPlaneX(null3Key),
                model.mapPlaneY(3) - model.mapPlaneY(null3Key)) < LS * 2.0,
                "scan row Null:3 must match synthetic hub after layout");
        double hubSepFromCluster = Math.hypot(model.mapPlaneX(null2Key) - model.mapPlaneX(fixture.bodyIdByLabel("D")),
                model.mapPlaneY(null2Key) - model.mapPlaneY(fixture.bodyIdByLabel("D"))) / LS;
        assertTrue(hubSepFromCluster < 3_000.0,
                "Null:2 hub should stay near D / BCD cluster, not float away; sep=" + hubSepFromCluster + " Ls");
    }

    @Test
    @DisplayName("Eor Aowsy true scale: outer trunk ring keeps zoom segment floor at low px/m scale")
    void eorAowsy_trueScale_trunkRingVertexCountRespectsLegacyFloor() {
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false);
        int legacySeg = 144;
        double scalePxPerM = 1.0E-5;
        List<OrbitPolylineWorldXY> polys = SystemMapPipeline.rebuildOrbitPolylines(model,
                model.positionsMetres(), legacySeg, scalePxPerM);
        OrbitPolylineWorldXY outer = null;
        for (OrbitPolylineWorldXY p : polys) {
            if (p != null && p.bodyId == SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID) {
                outer = p;
                break;
            }
        }
        assertNotNull(outer, "outer A–BCD trunk ring");
        assertTrue(outer.wx.length >= legacySeg,
                "screen-chord count must not undercut zoom floor; vertices=" + outer.wx.length
                        + " legacySeg=" + legacySeg);
    }

    @Test
    @DisplayName("Coeus true scale: star-hosted A 4 ring smooth after zoom-style rebuild")
    void coeus_trueScale_a4RingVertexCountRespectsLegacyFloor() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> coeusBodies = coeus.toBodies();
        SystemMapModel model = SystemMapPipeline.build(coeus.name, coeusBodies, Instant.EPOCH, false);
        int idA4 = coeus.bodyIdByLabel("A 4");
        int legacySeg = 144;
        double scalePxPerM = 8.0E-4;
        List<OrbitPolylineWorldXY> polys = SystemMapPipeline.rebuildOrbitPolylines(model,
                model.positionsMetres(), legacySeg, scalePxPerM);
        OrbitPolylineWorldXY ring = null;
        for (OrbitPolylineWorldXY p : polys) {
            if (p != null && p.bodyId == idA4) {
                ring = p;
                break;
            }
        }
        assertNotNull(ring, "A 4 heliocentric orbit stroke");
        assertTrue(ring.wx.length >= legacySeg,
                "A 4 ring vertices=" + ring.wx.length + " legacySeg=" + legacySeg);
        OrbitGeometryTestSupport.assertPerBodyOrbitAlignedAfterHighZoomRebuild(model, coeusBodies, "A 4",
                scalePxPerM, 0.15, 200.0);
    }

}
