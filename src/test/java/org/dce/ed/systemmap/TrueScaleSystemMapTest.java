package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.systemmap.SystemMapRules;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * True-scale pipeline: journal Kepler positions without schematic compression.
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
        SystemMapModel trueScale = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        assertEquals(MapScaleMode.TRUE_SCALE, trueScale.mapScaleMode());
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
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
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
    @DisplayName("True scale single-star: star-hosted planets get orbit strokes")
    void singleStar_trueScale_starHostedPlanetsHaveOrbitStrokes() throws IOException {
        SystemMapFixture single = SystemMapFixtureLoader.loadClasspath("c16-241-single-k-star.json");
        Map<Integer, BodyInfo> singleBodies = single.toBodies();
        SystemMapModel model = SystemMapPipeline.build(single.name, singleBodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        int id1 = single.bodyIdByLabel("A 1");
        int id2 = single.bodyIdByLabel("A 2");
        int id3 = single.bodyIdByLabel("A 3");
        assertTrue(hasOrbitStrokeForBody(model, id1), "A 1 should have an orbit stroke at true scale");
        assertTrue(hasOrbitStrokeForBody(model, id2), "A 2 should have an orbit stroke at true scale");
        assertTrue(hasOrbitStrokeForBody(model, id3), "A 3 should have an orbit stroke at true scale");
        assertTrue(model.orbitPolylines().size() >= 3,
                "true-scale single star should include per-body and/or star concentric rings");
    }

    private static boolean hasOrbitStrokeForBody(SystemMapModel model, int bodyId) {
        for (OrbitPolylineWorldXY poly : model.orbitPolylines()) {
            if (poly != null && poly.bodyId == bodyId && poly.wx != null && poly.wx.length >= 3) {
                return true;
            }
        }
        int starId = model.classification().schematicCentralStarId();
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

    @Test
    @DisplayName("EOL PROU LH-U D3-2700 (journal): not triple; inner A–bary and outer A–C trunk rings")
    void eolProuD32700_journal_trueScale_innerAndOuterStellarRings() throws IOException {
        SystemMapFixture journal = SystemMapFixtureLoader.loadClasspath("eol-prou-lh-u-d3-2700-journal.json");
        Map<Integer, BodyInfo> journalBodies = journal.toBodies();
        assertTrue(SystemOrbitGeometry.isHierarchicalWideBinary(journalBodies));
        assertFalse(SystemOrbitGeometry.isHierarchicalTripleStarMap(journalBodies),
                "B ~1k Ls and C ~11k Ls must not be treated as a tight B+C pair");
        SystemMapModel model = SystemMapPipeline.build(journal.name, journalBodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        int idA = journal.bodyIdByLabel("A");
        int idC = journal.bodyIdByLabel("C");
        int null1Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(2);
        double sepAc = distLs(model, idA, idC);
        assertTrue(sepAc > 8_000.0 && sepAc < 16_000.0, "A–C separation Ls=" + sepAc);
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
    @DisplayName("Schematic mode still compresses Eor Aowsy wide binary")
    void eorAowsy_schematic_compressesStarSeparation() {
        SystemMapModel schematic = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false);
        SystemMapModel trueScale = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        double schematicSep = distLs(schematic, idA, idB);
        double trueSep = distLs(trueScale, idA, idB);
        assertTrue(schematicSep < 9_000.0, "schematic A–B Ls=" + schematicSep);
        assertTrue(trueSep > schematicSep * 4.0,
                "true=" + trueSep + " schematic=" + schematicSep);
    }

}
