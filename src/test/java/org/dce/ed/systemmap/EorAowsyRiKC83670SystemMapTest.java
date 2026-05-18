package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for {@code Eor Aowsy RI-K c8-3670}: four-star hierarchy (K + M/T/L cluster) and the
 * planet-binary around {@code Null:49} (BCD 2 + BCD 3) in the BCD branch. Fixture from journal FSS (May 2026).
 */
class EorAowsyRiKC83670SystemMapTest {

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static SystemMapClassification clf;
    private static SystemMapModel model;
    private static int primaryId;

    @BeforeAll
    static void load() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eor-aowsy-ri-k-c8-3670.json");
        bodies = fixture.toBodies();
        clf = SystemMapRules.classify(bodies);
        model = SystemMapPipeline.build(fixture.name, bodies, java.time.Instant.EPOCH, true);
        primaryId = clf.primaryAnchorBodyId();
    }

    private static int id(String label) {
        return fixture.bodyIdByLabel(label);
    }

    private static int resolvedParent(String label) {
        int bid = id(label);
        return model.resolveParentBodyId(bid);
    }

    @Nested
    @DisplayName("Model topology (GUI contract)")
    class ModelTopologyForGui {

        @Test
        void resolvedParents_matchRules() {
            for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
                int bid = e.getKey().intValue();
                int fromModel = model.resolveParentBodyId(bid);
                int fromRules = SystemMapRules.resolveOrbitParentBodyId(e.getValue(), bodies, bid);
                assertEquals(fromRules, fromModel, "bodyId=" + bid);
            }
        }

        @Test
        void subsystemHubs_includeA3_notBranchStars() {
            assertTrue(model.isSubsystemHubBody(id("A 3")));
            assertFalse(model.isSubsystemHubBody(id("A")));
            assertFalse(model.isSubsystemHubBody(id("B")));
            assertTrue(model.isSubsystemHubBody(id("BCD 4")));
        }

        @Test
        void planetBinary49_ringOnModel() {
            assertTrue(model.hasPlanetBinaryMutualRing(49));
        }

        @Test
        void primaryBranch_membership() {
            assertTrue(model.isPrimaryBranchBody(id("A")));
            assertTrue(model.isPrimaryBranchBody(id("A 3")));
            assertFalse(model.isPrimaryBranchBody(id("BCD 2")));
        }

        @Test
        void wideBinaryBarycentricStars_onlyA() {
            assertEquals(1, model.wideBinarySystemBarycentreStarIds().size());
            assertTrue(model.wideBinarySystemBarycentreStarIds().contains(Integer.valueOf(id("A"))));
        }
    }

    @Nested
    @DisplayName("Classification and layout")
    class Classification {

        @Test
        void wideBinary_fourStellarBodies() {
            assertEquals(SystemLayoutKind.WIDE_BINARY, clf.layoutKind());
            assertEquals(4, clf.mapStellarCount());
            assertTrue(clf.wideBinary());
            assertFalse(model.hasBarycentreMutualRing(),
                    "only A orbits Null:0; B/C/D use inner Null:2/3 — no giant ring through all four stars");
        }

        @Test
        void primaryAnchor_isStarA() {
            assertEquals(id("A"), primaryId);
            assertTrue(clf.barycentricStarIds().contains(Integer.valueOf(id("A"))));
        }

        @Test
        void barycentricStars_includeA_notInnerBC() {
            assertTrue(clf.barycentricStarIds().contains(Integer.valueOf(id("A"))));
            assertFalse(clf.barycentricStarIds().contains(Integer.valueOf(id("B"))));
            assertFalse(clf.barycentricStarIds().contains(Integer.valueOf(id("C"))));
            assertFalse(clf.barycentricStarIds().contains(Integer.valueOf(id("D"))));
        }
    }

    @Nested
    @DisplayName("Four-star hierarchy")
    class StellarHierarchy {

        @Test
        void starA_orbitsSystemBarycentre() {
            assertTrue(resolvedParent("A") < 0);
        }

        @Test
        void starsBAndC_orbitNull3Barycentre() {
            int null3 = SystemOrbitGeometry.planetBinaryBarycentreMapKey(3);
            assertEquals(null3, resolvedParent("B"));
            assertEquals(null3, resolvedParent("C"));
        }

        @Test
        void starD_orbitsNull2Barycentre() {
            assertEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(2), resolvedParent("D"));
        }

        @Test
        void journalImmediateParents_matchScanBarycentreRows() {
            assertEquals(3, bodies.get(id("B")).getImmediateParentBodyId());
            assertEquals(3, bodies.get(id("C")).getImmediateParentBodyId());
            assertEquals(2, bodies.get(id("D")).getImmediateParentBodyId());
        }

        @Test
        void innerStars_notParentedToArrivalStar() {
            for (String label : new String[] { "B", "C", "D" }) {
                assertNotEquals(primaryId, resolvedParent(label), label + " must not orbit A directly");
            }
        }
    }

    @Nested
    @DisplayName("Planet-binary Null:49 (BCD 2 + BCD 3)")
    class PlanetBinary49 {

        @Test
        void giantsShareNull49Barycentre() {
            int bary49 = SystemOrbitGeometry.planetBinaryBarycentreMapKey(49);
            assertEquals(bary49, resolvedParent("BCD 2"));
            assertEquals(bary49, resolvedParent("BCD 3"));
        }

        @Test
        void mutualOrbitRing_drawn() {
            assertNotNull(OrbitGeometryTestSupport.findPlanetBinaryMutualRing(model, 49));
        }

        @Test
        void bcd2AndBcd3_onMutualRing_notPerBodyKeplerRing() {
            OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(model, bodies, "BCD 2", 49, 0.5);
            OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(model, bodies, "BCD 3", 49, 0.5);
            OrbitGeometryTestSupport.assertNoPerBodyOrbitRing(model, id("BCD 2"));
            OrbitGeometryTestSupport.assertNoPerBodyOrbitRing(model, id("BCD 3"));
        }

        @Test
        void bcd2Moon_orbitsHostGiant() {
            assertEquals(id("BCD 2"), resolvedParent("BCD 2 a"));
        }

        @Test
        void bcd2AndBcd3_notSiblingsOfBcd4() {
            assertNotEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(49), resolvedParent("BCD 4"));
        }
    }

    @Nested
    @DisplayName("BCD branch bodies")
    class BcdBranch {

        @Test
        void bcd1_onNull2Subsystem() {
            assertEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(2), resolvedParent("BCD 1"));
        }

        @Test
        void bcd4_onNull2_notNull49() {
            assertEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(2), resolvedParent("BCD 4"));
        }

        @Test
        void bcd5_onNull2() {
            assertEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(2), resolvedParent("BCD 5"));
        }

        @Test
        void bcd4Moons_orbitBcd4() {
            int host = id("BCD 4");
            for (String moon : new String[] { "BCD 4 a", "BCD 4 b", "BCD 4 c" }) {
                assertEquals(host, resolvedParent(moon), moon);
            }
        }

        @Test
        void bcd5Moons_orbitBcd5() {
            int host = id("BCD 5");
            for (String moon : new String[] { "BCD 5 a", "BCD 5 b", "BCD 5 c", "BCD 5 d", "BCD 5 e", "BCD 5 f", "BCD 5 g" }) {
                assertEquals(host, resolvedParent(moon), moon);
            }
        }
    }

    @Nested
    @DisplayName("A-branch planets and moons")
    class ABranch {

        @Test
        void majors_orbitStarA() {
            int aId = id("A");
            for (String label : new String[] { "A 1", "A 2", "A 3", "A 4" }) {
                assertEquals(aId, resolvedParent(label), label);
            }
        }

        @Test
        void a2Moon_orbitsA2() {
            assertEquals(id("A 2"), resolvedParent("A 2 a"));
        }

        @Test
        void a3Moons_orbitA3() {
            int host = id("A 3");
            for (String m : new String[] { "A 3 a", "A 3 b", "A 3 c", "A 3 d", "A 3 e" }) {
                assertEquals(host, resolvedParent(m), m);
            }
        }

        @Test
        void a4Moons_orbitA4() {
            int host = id("A 4");
            for (String m : new String[] { "A 4 a", "A 4 b", "A 4 c" }) {
                assertEquals(host, resolvedParent(m), m);
            }
        }

        @Test
        void a3aa_submoon_orbitsA3a() {
            assertEquals(id("A 3 a"), resolvedParent("A 3 a a"));
        }

        @Test
        void noABranchBody_onBcdSubsystem() {
            for (String label : new String[] { "A 1", "A 2", "A 3", "A 4", "A 2 a", "A 3 a" }) {
                int p = resolvedParent(label);
                assertNotEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(2), p, label);
                assertNotEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(49), p, label);
            }
        }
    }

    @Nested
    @DisplayName("Schematic GUI layout (no giant A-ring)")
    class SchematicGuiLayout {

        @Test
        void innerStars_notOnSystemBarycentreMutualRing() {
            assertFalse(model.hasBarycentreMutualRing());
            for (String label : new String[] { "B", "C", "D" }) {
                assertNotEquals(primaryId, resolvedParent(label));
            }
        }

        @Test
        void noSystemBarycentreRingThroughAllStars() {
            for (SystemOrbitGeometry.OrbitPolylineWorldXY poly : model.orbitPolylines()) {
                assertNotEquals(SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID, poly.bodyId,
                        "must not draw one giant Null:0 ring through A+B+C+D");
            }
        }

        @Test
        void bcdPlanets_parentToSubsystem_notStarA() {
            for (String label : new String[] { "BCD 1", "BCD 2", "BCD 4", "BCD 5", "BCD 2 a" }) {
                int p = resolvedParent(label);
                assertNotEquals(id("A"), p, label);
                assertNotEquals(primaryId, p, label);
            }
        }

        @Test
        void resolvesParents_withoutScanBarycentreRows() {
            Map<Integer, BodyInfo> partial = new java.util.HashMap<>(bodies);
            for (Integer key : new Integer[] { Integer.valueOf(2), Integer.valueOf(3), Integer.valueOf(49) }) {
                partial.remove(key);
            }
            SystemMapModel partialModel = SystemMapPipeline.build(fixture.name, partial, java.time.Instant.EPOCH, true);
            assertEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(3),
                    partialModel.resolveParentBodyId(id("B")));
            assertEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(3),
                    partialModel.resolveParentBodyId(id("C")));
            assertEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(2),
                    partialModel.resolveParentBodyId(id("D")));
            assertFalse(partialModel.hasBarycentreMutualRing());
        }
    }

    @Nested
    @DisplayName("Branch topology (primary vs companion)")
    class BranchTopology {

        @Test
        void primaryBranch_containsAAndPlanets() {
            for (String label : new String[] { "A", "A 1", "A 2", "A 3", "A 4", "A 2 a", "A 3 a", "A 4 a" }) {
                assertTrue(SystemMapRules.isWideBinaryPrimaryBranchBody(id(label), bodies), label);
            }
        }

        @Test
        void companionBranch_containsBcdCluster() {
            for (String label : new String[] { "B", "C", "D", "BCD 1", "BCD 2", "BCD 3", "BCD 4", "BCD 5", "BCD 2 a" }) {
                assertFalse(SystemMapRules.isWideBinaryPrimaryBranchBody(id(label), bodies), label);
            }
        }

        @Test
        void mapPlane_companionCluster_fartherFromA_thanA3() {
            double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
            double ax = model.mapPlaneX(id("A"));
            double ay = model.mapPlaneY(id("A"));
            double dA3 = Math.hypot(model.mapPlaneX(id("A 3")) - ax, model.mapPlaneY(id("A 3")) - ay) / ls;
            double dBcd = Math.hypot(model.mapPlaneX(id("BCD 2")) - ax, model.mapPlaneY(id("BCD 2")) - ay) / ls;
            assertTrue(dBcd > dA3 + 100.0, "BCD branch should be much farther from A than inner A-branch bodies");
        }
    }

    @Nested
    @DisplayName("Map schematic layout")
    class MapLayout {

        @Test
        void bcStars_separated_mutualOrbitAtNull3() {
            double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
            double sepBc = Math.hypot(
                    model.mapPlaneX(id("B")) - model.mapPlaneX(id("C")),
                    model.mapPlaneY(id("B")) - model.mapPlaneY(id("C")))
                    / ls;
            assertTrue(sepBc > 10.0,
                    "B and C should not stack (mutual orbit at Null:3); separation was " + sepBc + " Ls");
        }

        @Test
        void null49Barycentre_inPositionMap() {
            int bKey = SystemOrbitGeometry.planetBinaryBarycentreMapKey(49);
            assertTrue(model.positionsMetres().containsKey(Integer.valueOf(bKey)));
        }

        @Test
        void bcdGiants_nearNull49Barycentre() {
            double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
            int bKey = SystemOrbitGeometry.planetBinaryBarycentreMapKey(49);
            double[] bary = model.positionsMetres().get(Integer.valueOf(bKey));
            int a0 = model.projectionAxis0();
            int a1 = model.projectionAxis1();
            for (String label : new String[] { "BCD 2", "BCD 3" }) {
                double[] p = model.positionsMetres().get(Integer.valueOf(id(label)));
                double dx = SystemOrbitGeometry.worldAxisMetres(p, a0) - SystemOrbitGeometry.worldAxisMetres(bary, a0);
                double dy = SystemOrbitGeometry.worldAxisMetres(p, a1) - SystemOrbitGeometry.worldAxisMetres(bary, a1);
                assertTrue(Math.hypot(dx, dy) / ls < 50.0, label);
            }
        }

        @Test
        void primaryBarycentricStar_nearOrigin_afterRecenter() {
            double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
            double ax = model.mapPlaneX(id("A"));
            double ay = model.mapPlaneY(id("A"));
            assertTrue(Math.hypot(ax, ay) / ls < 5.0, "A (system barycentre star) should be near map origin");
        }
    }

    @Nested
    @DisplayName("Journal vs resolved parents")
    class JournalVsResolved {

        @Test
        void scanRows_resolveToSyntheticBaryKeys() {
            assertEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(2),
                    SystemMapRules.resolveOrbitParentBodyId(bodies.get(id("D")), bodies, id("D")));
            assertEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(3),
                    SystemMapRules.resolveOrbitParentBodyId(bodies.get(id("B")), bodies, id("B")));
        }

        @Test
        void resolvedDiffersFromJournal_whenWideBinaryOverridesCompanion() {
            BodyInfo b = bodies.get(id("B"));
            assertEquals(3, b.getImmediateParentBodyId());
            assertEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(3),
                    SystemMapRules.resolveOrbitParentBodyId(b, bodies, id("B")));
        }

        @Test
        void allFixtureParentExpectations() {
            List<String> failures = new ArrayList<>();
            for (SystemMapFixture.ParentExpect pe : fixture.expect.parents) {
                int childId = id(pe.body);
                int resolved = resolvedParent(pe.body);
                if ("barycentre".equalsIgnoreCase(pe.resolvesTo)) {
                    if (resolved >= 0) {
                        failures.add(pe.body + " expected barycentre, got " + resolved);
                    }
                } else if (pe.resolvesTo != null && pe.resolvesTo.startsWith("planetBinary:")) {
                    int nullId = Integer.parseInt(pe.resolvesTo.substring("planetBinary:".length()));
                    int exp = SystemOrbitGeometry.planetBinaryBarycentreMapKey(nullId);
                    if (resolved != exp) {
                        failures.add(pe.body + " expected Null:" + nullId + ", got " + resolved);
                    }
                } else if (pe.resolvesTo != null) {
                    int exp = id(pe.resolvesTo);
                    if (resolved != exp) {
                        failures.add(pe.body + " expected " + pe.resolvesTo + ", got " + resolved);
                    }
                }
            }
            assertTrue(failures.isEmpty(), String.join("; ", failures));
        }
    }
}
