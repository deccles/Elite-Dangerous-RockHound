package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;
import org.dce.ed.systemmap.SystemMapRules;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Eol Prou OR-V d2-399: three stars (A vs B+C). Must not show the four-star empty system-barycentre ring on A.
 */
class EolProuOrVD2399TripleStarTest {

    private static final double LS = SystemOrbitGeometry.LIGHT_SECOND_METRES;
    private static final double SCHEMATIC_TRUNK_LS = 7500.0;

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static SystemMapModel model;
    private static int idA;
    private static int null2Key;

    @BeforeAll
    static void load() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-or-v-d2-399.json");
        bodies = fixture.toBodies();
        model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);
        idA = fixture.bodyIdByLabel("A");
        null2Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(2);
    }

    @Test
    void classifiedAsHierarchicalTripleStar() {
        assertTrue(SystemOrbitGeometry.isHierarchicalWideBinary(bodies));
        assertTrue(SystemOrbitGeometry.isHierarchicalTripleStarMap(bodies));
        assertEquals(2, SystemOrbitGeometry.hierarchicalTripleStellarNullId(bodies));
    }

    @Test
    void noEmptySystemBarycentreRingAroundA() {
        assertFalse(model.hasBarycentreMutualRing());
        OrbitGeometryTestSupport.assertNoHeliocentricRingAroundPrimaryStar(model, bodies, idA, 12_000.0);
    }

    @Test
    void schematicTrunkFromAToBcHub() {
        assertTrue(model.hasHierarchicalTripleStarTrunk());
        double d = Math.hypot(model.mapPlaneX(null2Key) - model.mapPlaneX(idA),
                model.mapPlaneY(null2Key) - model.mapPlaneY(idA)) / LS;
        assertTrue(d >= SCHEMATIC_TRUNK_LS * 0.75 && d <= SCHEMATIC_TRUNK_LS * 1.25,
                "A to B+C hub schematic trunk; was " + d + " Ls");
        OrbitGeometryTestSupport.assertPrimaryOnSchematicMutualRing(model, idA,
                SystemOrbitGeometry.HIERARCHICAL_TRIPLE_STAR_TRUNK_POLYLINE_ID, 0.35);
    }

    @Test
    void bAndC_onMutualOrbitAtNull2() {
        assertTrue(model.hasPlanetBinaryMutualRing(2));
        OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(model, bodies, "B", 2, 0.35);
        OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(model, bodies, "C", 2, 0.35);
        assertFalse(model.hasOrbitRingForBody(fixture.bodyIdByLabel("B")));
        assertFalse(model.hasOrbitRingForBody(fixture.bodyIdByLabel("C")));
    }

    @Test
    void stillTripleWhenCacheParentsBcToAAndSyncsPlanetClass() {
        Map<Integer, BodyInfo> cache = fixture.toBodies();
        for (String label : new String[] { "B", "C" }) {
            BodyInfo star = cache.get(Integer.valueOf(fixture.bodyIdByLabel(label)));
            star.setPlanetClass("Class II gas giant");
            star.setStarType(null);
            star.setImmediateParentBodyId(idA);
        }
        assertTrue(SystemOrbitGeometry.isHierarchicalTripleStarMap(cache));
        int idB = fixture.bodyIdByLabel("B");
        int idC = fixture.bodyIdByLabel("C");
        assertEquals(null2Key, SystemMapRules.resolveOrbitParentBodyId(cache.get(Integer.valueOf(idB)), cache, idB));
        assertEquals(null2Key, SystemMapRules.resolveOrbitParentBodyId(cache.get(Integer.valueOf(idC)), cache, idC));
        SystemMapModel cacheModel = SystemMapPipeline.build(fixture.name, cache, Instant.EPOCH, true);
        assertTrue(cacheModel.hasHierarchicalTripleStarTrunk());
        double dBc = Math.hypot(cacheModel.mapPlaneX(fixture.bodyIdByLabel("B"))
                        - cacheModel.mapPlaneX(fixture.bodyIdByLabel("C")),
                cacheModel.mapPlaneY(fixture.bodyIdByLabel("B"))
                        - cacheModel.mapPlaneY(fixture.bodyIdByLabel("C")))
                / LS;
        assertTrue(dBc < 200.0, "B and C clustered when cache mis-parents companions; dBc=" + dBc + " Ls");
    }

    @Test
    void bAndC_notParentedToA() {
        for (String star : new String[] { "B", "C" }) {
            int id = fixture.bodyIdByLabel(star);
            assertNotEquals(idA, model.resolveParentBodyId(id), star + " must not orbit A");
            assertEquals(null2Key, model.resolveParentBodyId(id));
        }
    }

    @Nested
    @DisplayName("Full journal replay (planets + belt)")
    class FromJournal {

        private static SystemMapModel journalModel;
        private static Map<Integer, BodyInfo> journalBodies;
        private static int journalIdA;

        @BeforeAll
        static void loadJournal() throws IOException {
            Path journalDir = Path.of(System.getenv("USERPROFILE"), "Saved Games", "Frontier Developments",
                    "Elite Dangerous");
            assumeTrue(Files.isDirectory(journalDir), "Elite journal directory not found");
            SystemState state = JournalSystemMapLoader.loadFromJournal(journalDir, "Eol Prou OR-V d2-399");
            assumeTrue(state.getBodies().size() >= 10, "need FSS scans in journal");
            journalBodies = state.getBodies();
            journalModel = SystemMapPipeline.build(state.getSystemName(), journalBodies, Instant.EPOCH, true);
            journalIdA = OrbitGeometryTestSupport.findByShortName(journalBodies, "A");
            assumeTrue(journalIdA >= 0);
        }

        @Test
        void journalStillClassifiedAsTripleStar() {
            assertTrue(SystemOrbitGeometry.isHierarchicalTripleStarMap(journalBodies));
            assertEquals(3, journalModel.classification().mapStellarCount());
        }

        @Test
        void noHeliocentricHubRingsAtBcBarycentre() {
            double maxHubRingLs = 15_000.0;
            for (OrbitPolylineWorldXY poly : journalModel.orbitPolylines()) {
                if (poly == null || poly.wx == null || poly.wx.length < 3) {
                    continue;
                }
                if (poly.bodyId == SystemOrbitGeometry.HIERARCHICAL_TRIPLE_STAR_TRUNK_POLYLINE_ID
                        || poly.bodyId == SystemOrbitGeometry.PLANET_BINARY_MUTUAL_ORBIT_RING_ID_BASE - 2) {
                    continue;
                }
                double cx = OrbitGeometryTestSupport.ringCentroid(poly.wx);
                double cy = OrbitGeometryTestSupport.ringCentroid(poly.wy);
                double radLs = OrbitGeometryTestSupport.meanRadius(poly.wx, poly.wy, cx, cy) / LS;
                double[] aPos = journalModel.positionsMetres().get(Integer.valueOf(journalIdA));
                double offALs = Math.hypot(
                        cx - SystemOrbitGeometry.worldAxisMetres(aPos, journalModel.projectionAxis0()),
                        cy - SystemOrbitGeometry.worldAxisMetres(aPos, journalModel.projectionAxis1())) / LS;
                if (radLs > maxHubRingLs && offALs > maxHubRingLs * 0.4) {
                    assertTrue(false, "heliocentric hub ring at " + offALs + " Ls from A, radius " + radLs + " Ls");
                }
            }
        }

        @Test
        void bAndC_clusteredOnMutualOrbit() {
            int idB = OrbitGeometryTestSupport.findByShortName(journalBodies, "B");
            int idC = OrbitGeometryTestSupport.findByShortName(journalBodies, "C");
            assumeTrue(idB >= 0 && idC >= 0);
            double dBc = distLs(idB, idC);
            assertTrue(dBc < 200.0, "B and C on tight schematic mutual orbit; dBc=" + dBc + " Ls");
            OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(journalModel, journalBodies, "B", 2, 0.4);
            OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(journalModel, journalBodies, "C", 2, 0.4);
        }

        @Test
        void aBranchPlanets_nearA_notAtBcHub() {
            int idA1 = OrbitGeometryTestSupport.findByShortName(journalBodies, "A 1");
            int idA2 = OrbitGeometryTestSupport.findByShortName(journalBodies, "A 2");
            assumeTrue(idA1 >= 0 && idA2 >= 0);
            double dA1 = distLs(journalIdA, idA1);
            double dA2 = distLs(journalIdA, idA2);
            double dHub = distLs(journalIdA, null2Key);
            assertTrue(dA1 < 5000.0 && dA2 < 5000.0, "A-branch planets near A; dA1=" + dA1 + " dA2=" + dA2);
            assertTrue(dHub >= SCHEMATIC_TRUNK_LS * 0.5 && dHub <= SCHEMATIC_TRUNK_LS * 1.5,
                    "BC hub on schematic trunk; was " + dHub + " Ls");
            assertTrue(dA1 < dHub * 0.85 && dA2 < dHub * 0.85);
        }

        private static double distLs(int fromId, int toId) {
            double dx = journalModel.mapPlaneX(toId) - journalModel.mapPlaneX(fromId);
            double dy = journalModel.mapPlaneY(toId) - journalModel.mapPlaneY(fromId);
            return Math.hypot(dx, dy) / LS;
        }
    }
}
