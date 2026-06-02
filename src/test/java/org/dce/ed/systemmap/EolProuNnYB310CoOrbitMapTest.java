package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Eol Prou NN-Y b31-0: co-orbit gas giants 5 and 6 at {@code Null:20} with moons and a planetary ring scan row.
 */
class EolProuNnYB310CoOrbitMapTest {

    private static final double LS = SystemOrbitGeometry.LIGHT_SECOND_METRES;

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static int id5;
    private static int id6;
    private static int id5a;
    private static int idRing;
    private static int baryKey20;

    @BeforeAll
    static void loadFixture() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-nn-y-b31-0-co-orbit.json");
        id5 = fixture.bodyIdByLabel("5");
        id6 = fixture.bodyIdByLabel("6");
        id5a = fixture.bodyIdByLabel("5 a");
        idRing = fixture.bodyIdByLabel("5 A Ring");
        baryKey20 = SystemOrbitGeometry.planetBinaryBarycentreMapKey(20);
    }

    @BeforeEach
    void freshBodies() {
        bodies = fixture.toBodies();
    }

    @Test
    @DisplayName("Null:20 detected from journal refs even when cache parents planets to star")
    void null20_detectedFromJournalRefs() {
        assertTrue(SystemOrbitGeometry.isPlanetBinaryNullParentId(20, bodies));
        assertTrue(SystemOrbitGeometry.isSharedNullBarycentreId(20, bodies));
    }

    @Test
    @DisplayName("Ring scan row is not a map body; moons parent to planet 5")
    void ringExcluded_moonsOrbitPlanet5() {
        assertTrue(SystemMapRules.isPlanetaryRingMapBody(bodies.get(Integer.valueOf(idRing))));
        assertEquals(id5, SystemMapRules.resolveOrbitParentBodyId(
                bodies.get(Integer.valueOf(id5a)), bodies, id5a));
    }

    @Test
    @DisplayName("Co-orbit majors share mutual ring at Null:20; 5a sits on orbit around 5")
    void coOrbit_mutualRingAndMoonAlignment() {
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);

        assertNotNull(model.positionsMetres().get(Integer.valueOf(baryKey20)),
                "synthetic Null:20 barycentre should be positioned");

        boolean mutualRing = false;
        for (OrbitPolylineWorldXY poly : model.orbitPolylines()) {
            if (poly != null && SystemOrbitGeometry.isPlanetBinaryMutualOrbitRingBodyId(poly.bodyId)) {
                mutualRing = true;
                break;
            }
        }
        assertTrue(mutualRing, "Null:20 mutual orbit ring should be drawn");

        assertNull(findOrbitPolyline(model.orbitPolylines(), idRing),
                "planetary ring scan row must not get its own orbit stroke");

        double baryX = model.mapPlaneX(baryKey20);
        double baryY = model.mapPlaneY(baryKey20);
        double r5 = Math.hypot(model.mapPlaneX(id5) - baryX, model.mapPlaneY(id5) - baryY) / LS;
        double r6 = Math.hypot(model.mapPlaneX(id6) - baryX, model.mapPlaneY(id6) - baryY) / LS;
        assertTrue(r5 > 0.1 && r6 > 0.1, "co-orbit majors should sit on the mutual ring, not at barycentre");
        assertTrue(Math.abs(r5 - r6) < Math.max(r5, r6) * 0.35,
                "5 and 6 should share the same mutual-orbit radius (r5=" + r5 + " r6=" + r6 + ")");

        double hostX = model.mapPlaneX(id5);
        double hostY = model.mapPlaneY(id5);
        double moonSep = Math.hypot(model.mapPlaneX(id5a) - hostX, model.mapPlaneY(id5a) - hostY) / LS;
        assertTrue(moonSep > 0.05 && moonSep < 5.0, "5 a should orbit near planet 5");

        boolean moonRingAroundHost = false;
        for (OrbitPolylineWorldXY poly : model.orbitPolylines()) {
            if (poly == null || poly.bodyId != id5a || poly.wx == null) {
                continue;
            }
            double sum = 0.0;
            for (int i = 0; i < poly.wx.length; i++) {
                sum += Math.hypot(poly.wx[i] - hostX, poly.wy[i] - hostY);
            }
            double meanR = sum / poly.wx.length;
            if (meanR > 0.0 && Math.abs(meanR - moonSep * LS) < moonSep * LS * 0.45) {
                moonRingAroundHost = true;
            }
        }
        assertTrue(moonRingAroundHost, "5 a orbit stroke should be centred on planet 5");
    }

    @Test
    @DisplayName("Resolved parents match journal topology")
    void resolvedParents_matchExpect() {
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        if (fixture.expect == null || fixture.expect.parents == null) {
            return;
        }
        for (SystemMapFixture.ParentExpect pe : fixture.expect.parents) {
            int bodyId = fixture.bodyIdByLabel(pe.body);
            int resolved = model.resolveParentBodyId(bodyId);
            if (pe.resolvesTo.startsWith("planetBinary:")) {
                int nullId = Integer.parseInt(pe.resolvesTo.substring("planetBinary:".length()));
                assertEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(nullId), resolved, pe.body);
            } else {
                int expectedId = fixture.bodyIdByLabel(pe.resolvesTo);
                assertEquals(expectedId, resolved, pe.body);
            }
        }
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
}
