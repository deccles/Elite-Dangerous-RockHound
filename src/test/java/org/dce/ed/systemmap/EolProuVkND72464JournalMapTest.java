package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Eol Prou VK-N d7-2464: hierarchical AB inner pair + outer companion C. AB-branch planets parent to star A;
 * moons and C-branch bodies must not collapse onto the inner barycentre hub.
 */
class EolProuVkND72464JournalMapTest {

    private static final double LS = SystemOrbitGeometry.LIGHT_SECOND_METRES;

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static SystemMapModel model;
    private static int idA;
    private static int idB;
    private static int idC;
    private static int idAb1;
    private static int idAb1a;
    private static int idC1;

    @BeforeAll
    static void load() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-vk-n-d7-2464.json");
        bodies = fixture.toBodies();
        model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);
        idA = fixture.bodyIdByLabel("A");
        idB = fixture.bodyIdByLabel("B");
        idC = fixture.bodyIdByLabel("C");
        idAb1 = fixture.bodyIdByLabel("AB 1");
        idAb1a = fixture.bodyIdByLabel("AB 1 a");
        idC1 = fixture.bodyIdByLabel("C 1");
    }

    @Test
    void hierarchicalWideBinary() {
        assertTrue(SystemOrbitGeometry.isHierarchicalWideBinary(bodies));
        assertTrue(model.classification().wideBinary());
    }

    @Test
    void abBranchMajors_parentToStarA() {
        for (String label : List.of("AB 1", "AB 2", "AB 3", "AB 4")) {
            int id = fixture.bodyIdByLabel(label);
            assertTrue(id >= 0, label);
            assertEquals(idA, model.resolveParentBodyId(id), label + " must orbit star A");
        }
    }

    @Test
    void abMoon_parentToAb1() {
        assertEquals(idAb1, model.resolveParentBodyId(idAb1a), "AB 1 a must orbit AB 1");
    }

    @Test
    void notTightTriple_innerOuterHierarchy() {
        assertTrue(SystemOrbitGeometry.isHierarchicalWideBinary(bodies));
        assertTrue(!SystemOrbitGeometry.hierarchicalCompanionBranchStarsCohesive(bodies),
                "B ~6 Ls and C ~1.8k Ls are inner+outer, not a tight B+C cluster");
        assertTrue(!SystemOrbitGeometry.isHierarchicalTripleStarMap(bodies));
    }

    @Test
    void starsBAndC_separatedOnMap() {
        double dBc = distLs(idB, idC);
        assertTrue(dBc > 100.0, "B and C must not overlap; dBc=" + dBc + " Ls");
        double dAb = distLs(idA, idB);
        double dAc = distLs(idA, idC);
        assertTrue(dAb < dAc, "inner B nearer A than outer C on map; dAb=" + dAb + " dAc=" + dAc);
    }

    @Test
    void cBranch_nearerCThanA() {
        double sepC = distLs(idC1, idC);
        double sepA = distLs(idC1, idA);
        assertTrue(sepC < sepA * 0.5, "C 1 nearer C than A; sepC=" + sepC + " sepA=" + sepA);
    }

    @Test
    void abMoon_nearHostPlanet() {
        double sep = distLs(idAb1a, idAb1);
        assertTrue(sep < 5.0, "AB 1 a near AB 1; sep=" + sep + " Ls");
    }

    @Test
    void playbackRefresh_keepsAbMoonNearHost() {
        Map<Integer, double[]> kepler = SystemOrbitGeometry.bodyPositionsMetres(bodies, Instant.EPOCH, true);
        Map<Integer, double[]> after = SystemMapPipeline.refreshPositionsForPlayback(model, kepler, Instant.EPOCH, true);
        SystemMapModel playback = SystemMapPipeline.playbackBase(bodies, model.projectionAxis0(),
                model.projectionAxis1(), after, model.wideBinaryFlattenFrame());
        double sep = Math.hypot(playback.mapPlaneX(idAb1a) - playback.mapPlaneX(idAb1),
                playback.mapPlaneY(idAb1a) - playback.mapPlaneY(idAb1)) / LS;
        assertTrue(sep < 5.0, "playback AB 1 a near AB 1; sep=" + sep + " Ls");
    }

    private static double distLs(int fromId, int toId) {
        return Math.hypot(model.mapPlaneX(toId) - model.mapPlaneX(fromId),
                model.mapPlaneY(toId) - model.mapPlaneY(fromId)) / LS;
    }
}
