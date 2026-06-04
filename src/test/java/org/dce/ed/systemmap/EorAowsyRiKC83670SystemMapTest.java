package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
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
            OrbitGeometryTestSupport.assertHierarchicalBarycentreRing(model, bodies, id("A"));
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
        void bodyPositionsMetres_withScanBarycentreRows_terminates() {
            Map<Integer, double[]> pos = SystemOrbitGeometry.bodyPositionsMetres(bodies, Instant.EPOCH);
            assertNotNull(pos);
            int null49Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(49);
            assertTrue(pos.containsKey(Integer.valueOf(null49Key)), "Null:49 barycentre position");
            assertTrue(pos.containsKey(Integer.valueOf(49)), "scan row aliases Null:49");
        }

        @Test
        void innerStars_notParentedToArrivalStar() {
            for (String label : new String[] { "B", "C", "D" }) {
                assertNotEquals(primaryId, resolvedParent(label), label + " must not orbit A directly");
            }
        }
    }

    @Nested
    @DisplayName("Map label tiers")
    class MapLabelTiers {

        @Test
        void revolutionCenters_includeBcdMajorsAndStars() {
            for (String label : new String[] { "A", "B", "C", "D", "BCD 2", "BCD 3", "BCD 4", "BCD 5", "A 2" }) {
                assertTrue(model.isOrbitRevolutionCenter(id(label)), label);
            }
            for (String label : new String[] { "BCD 2 a", "BCD 4 a", "A 2 a", "A 3 a" }) {
                assertFalse(model.isOrbitRevolutionCenter(id(label)), label);
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
        void bcd4ab_separateOrbitRings_notMerged() {
            int idA = id("BCD 4 a");
            int idB = id("BCD 4 b");
            assertTrue(model.hasOrbitRingForBody(idA));
            assertTrue(model.hasOrbitRingForBody(idB));
            OrbitPolylineWorldXY ringA = null;
            OrbitPolylineWorldXY ringB = null;
            for (OrbitPolylineWorldXY p : model.orbitPolylines()) {
                if (p == null) {
                    continue;
                }
                if (p.bodyId == idA) {
                    ringA = p;
                } else if (p.bodyId == idB) {
                    ringB = p;
                }
            }
            assertNotNull(ringA);
            assertNotNull(ringB);
            assertNotEquals(ringA, ringB, "BCD 4 a and BCD 4 b must not share one deduped orbit curve");
        }

        @Test
        void bcd1_journalParentNull2_notMisreadAsStarD() {
            int idBcd1 = id("BCD 1");
            assertEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(2),
                    SystemOrbitGeometry.resolveOrbitParentBodyId(bodies.get(idBcd1), bodies, idBcd1));
        }

        @Test
        void bcd1_onNull2Trunk_withMapPosition() {
            int idBcd1 = id("BCD 1");
            double[] pos = model.positionsMetres().get(Integer.valueOf(idBcd1));
            assertNotNull(pos, "BCD 1 should have a map position");
            int null2Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(2);
            double[] hub = model.positionsMetres().get(Integer.valueOf(null2Key));
            assertNotNull(hub);
            double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
            double dist = Math.hypot(model.mapPlaneX(idBcd1) - model.mapPlaneX(null2Key),
                    model.mapPlaneY(idBcd1) - model.mapPlaneY(null2Key)) / ls;
            double mutual2 = SystemOrbitGeometry.planetBinaryMutualOrbitRadiusLsPublic(2, bodies);
            assertTrue(dist >= mutual2 * 0.35 && dist <= mutual2 * 1.05,
                    "BCD 1 should sit on the Null:2 trunk ring, not at the hub; dist=" + dist + " Ls mutual2="
                            + mutual2);
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
        void a3aa_treatedAsMoon_withOrbitRingAndNearA3a() {
            int idA3a = id("A 3 a");
            int idA3aa = id("A 3 a a");
            assertTrue(SystemOrbitGeometry.isMoonSatelliteBody(bodies.get(idA3aa), bodies));
            assertTrue(model.hasOrbitRingForBody(idA3aa),
                    "sub-moon needs its own orbit stroke around A 3 a");
            double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
            double sep = Math.hypot(model.mapPlaneX(idA3aa) - model.mapPlaneX(idA3a),
                    model.mapPlaneY(idA3aa) - model.mapPlaneY(idA3a)) / ls;
            assertTrue(sep < 25.0,
                    "A 3 a a should stay near A 3 a, not on the A 3 guide ring; sep=" + sep + " Ls");
            OrbitGeometryTestSupport.assertBodyOnPerBodyOrbitRing(model, bodies, "A 3 a a", 5.0);
        }

        @Test
        void a3Moons_eachHaveOrbitRing() {
            for (String moon : new String[] { "A 3 a", "A 3 b", "A 3 c", "A 3 d", "A 3 e", "A 3 a a" }) {
                assertTrue(model.hasOrbitRingForBody(id(moon)), "missing orbit ring for " + moon);
            }
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
    @DisplayName("True-scale GUI layout (barycentre ring, A on rim)")
    class TrueScaleGuiLayout {

        @Test
        void innerStars_notParentedToStarA() {
            OrbitGeometryTestSupport.assertHierarchicalBarycentreRing(model, bodies, primaryId);
            for (String label : new String[] { "B", "C", "D" }) {
                assertNotEquals(primaryId, resolvedParent(label));
            }
        }

        @Test
        void barycentreRing_atOriginPrimaryOnRim() {
            OrbitGeometryTestSupport.assertHierarchicalBarycentreRing(model, bodies, primaryId);
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
            /*
             * Without ScanBaryCentre rows, companion-cluster snap onto the trunk ring is weaker; parent resolution
             * above is the regression guard (see EorAowsySystemMapValidationTest.WithoutScanBarycentreRows).
             */
            assertTrue(partialModel.hasBarycentreMutualRing());
            double distBa = Math.hypot(partialModel.mapPlaneX(id("B")) - partialModel.mapPlaneX(id("A")),
                    partialModel.mapPlaneY(id("B")) - partialModel.mapPlaneY(id("A")))
                    / SystemOrbitGeometry.LIGHT_SECOND_METRES;
            assertTrue(distBa >= 40_000.0 && distBa <= 52_000.0,
                    "B on true-scale trunk from A without bary rows; distBa=" + distBa + " Ls");
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
    @DisplayName("Map layout")
    class MapLayout {

        @Test
        void a2a_hasPerBodyOrbitRingAroundA2() {
            int idA2a = id("A 2 a");
            assertEquals(id("A 2"), resolvedParent("A 2 a"));
            assertTrue(model.hasOrbitRingForBody(idA2a),
                    "moon A 2 a needs a per-parent orbit ring around A 2, not only the A-branch guide rings");
            OrbitGeometryTestSupport.assertBodyOnPerBodyOrbitRing(model, bodies, "A 2 a", 12.0);
        }

        @Test
        void a2a_onPerBodyOrbitPolyline_atHighZoomRebuild() {
            OrbitGeometryTestSupport.assertPerBodyOrbitAlignedAfterHighZoomRebuild(model, bodies, "A 2 a",
                    8.0E-4, 0.08, 0.35);
        }

        @Test
        void a2a_moonOrbit_enforcesMinScreenRadiusWhenScaleKnown() {
            int idA2a = id("A 2 a");
            int idA2 = id("A 2");
            double scalePxPerM = 8.0E-4;
            var polys = SystemMapPipeline.rebuildOrbitPolylines(model, model.positionsMetres(), 256, scalePxPerM,
                    null, 0);
            OrbitPolylineWorldXY ring = null;
            for (OrbitPolylineWorldXY p : polys) {
                if (p != null && p.bodyId == idA2a) {
                    ring = p;
                    break;
                }
            }
            assertNotNull(ring, "A 2 a orbit polyline");
            double px = model.mapPlaneX(idA2);
            double py = model.mapPlaneY(idA2);
            double sum = 0.0;
            for (int i = 0; i < ring.wx.length; i++) {
                sum += Math.hypot(ring.wx[i] - px, ring.wy[i] - py);
            }
            double rPx = (sum / ring.wx.length) * scalePxPerM;
            assertTrue(rPx >= SystemOrbitGeometry.MIN_MOON_ORBIT_SCREEN_RADIUS_PX - 0.05,
                    "moon orbit must stay visible on screen; rPx=" + rPx);
        }

        @Test
        void a2a_nearA2_journalSeparation() {
            double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
            int idA2 = id("A 2");
            int idA2a = id("A 2 a");
            double sep = Math.hypot(model.mapPlaneX(idA2a) - model.mapPlaneX(idA2),
                    model.mapPlaneY(idA2a) - model.mapPlaneY(idA2)) / ls;
            double hint = Math.abs(bodies.get(idA2a).getDistanceLs() - bodies.get(idA2).getDistanceLs());
            assertTrue(sep < 30.0,
                    "A 2 a should stay near A 2 on the map; sep=" + sep + " Ls journalHint=" + hint + " Ls");
            assertTrue(Math.abs(sep - hint) <= Math.max(5.0, hint * 5.0),
                    "map separation should follow journal parent-relative distance at true scale");
        }

        @Test
        void a2_onBranchRingAtStarA() {
            double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
            int idA = id("A");
            int idA2 = id("A 2");
            double dist = Math.hypot(
                    model.mapPlaneX(idA2) - model.mapPlaneX(idA),
                    model.mapPlaneY(idA2) - model.mapPlaneY(idA)) / ls;
            double hint = Math.abs(bodies.get(idA2).getDistanceLs() - bodies.get(idA).getDistanceLs());
            assertTrue(Math.abs(dist - hint) <= hint * 0.06,
                    "A 2 should sit on branch guide ring at journal distance from A; dist=" + dist
                            + " Ls hint=" + hint + " Ls");
        }

        @Test
        void bcStars_separated_mutualOrbitAtNull3() {
            double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
            double sepBc = Math.hypot(
                    model.mapPlaneX(id("B")) - model.mapPlaneX(id("C")),
                    model.mapPlaneY(id("B")) - model.mapPlaneY(id("C")))
                    / ls;
            assertTrue(sepBc > 140.0,
                    "B and C should not stack (mutual orbit at Null:3); separation was " + sepBc + " Ls");
        }

        @Test
        void bc_onMutualRingAtNull3() {
            OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(model, bodies, "B", 3, 2.0);
            OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(model, bodies, "C", 3, 2.0);
        }

        @Test
        void d_onMutualRingAtNull2_oppositeBcBarycentre() {
            OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(model, bodies, "D", 2, 3.0);
            int null3Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(3);
            int a0 = model.projectionAxis0();
            int a1 = model.projectionAxis1();
            double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
            double[] dPos = model.positionsMetres().get(Integer.valueOf(id("D")));
            double[] bcPos = model.positionsMetres().get(Integer.valueOf(null3Key));
            assertNotNull(dPos);
            assertNotNull(bcPos);
            double dist = Math.hypot(
                    SystemOrbitGeometry.worldAxisMetres(dPos, a0) - SystemOrbitGeometry.worldAxisMetres(bcPos, a0),
                    SystemOrbitGeometry.worldAxisMetres(dPos, a1) - SystemOrbitGeometry.worldAxisMetres(bcPos, a1))
                    / ls;
            double mutual2 = SystemOrbitGeometry.planetBinaryMutualOrbitRadiusLsPublic(2, bodies);
            assertTrue(dist >= mutual2 * 0.85 && dist <= mutual2 * 2.2,
                    "D and B+C barycentre should be on opposite sides of Null:2 mutual orbit; dist=" + dist
                            + " Ls mutual2=" + mutual2);
        }

        @Test
        void playbackRefresh_preservesBcClusterAndA2aNearA2() {
            double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
            double bcBefore = Math.hypot(model.mapPlaneX(id("B")) - model.mapPlaneX(id("C")),
                    model.mapPlaneY(id("B")) - model.mapPlaneY(id("C"))) / ls;
            double a2aBefore = Math.hypot(model.mapPlaneX(id("A 2 a")) - model.mapPlaneX(id("A 2")),
                    model.mapPlaneY(id("A 2 a")) - model.mapPlaneY(id("A 2"))) / ls;
            Map<Integer, double[]> kepler = new HashMap<>(model.positionsMetres());
            Map<Integer, double[]> after = SystemMapPipeline.refreshPositionsForPlayback(model, kepler,
                    Instant.EPOCH, true);
            SystemMapModel playback = SystemMapPipeline.playbackBase(model.bodies(), model.projectionAxis0(),
                    model.projectionAxis1(), after, model.wideBinaryFlattenFrame());
            double bcAfter = Math.hypot(playback.mapPlaneX(id("B")) - playback.mapPlaneX(id("C")),
                    playback.mapPlaneY(id("B")) - playback.mapPlaneY(id("C"))) / ls;
            double a2aAfter = Math.hypot(playback.mapPlaneX(id("A 2 a")) - playback.mapPlaneX(id("A 2")),
                    playback.mapPlaneY(id("A 2 a")) - playback.mapPlaneY(id("A 2"))) / ls;
            assertTrue(bcAfter > 140.0 && bcAfter < 250.0, "playback must keep B+C mutual cluster; sep=" + bcAfter);
            assertTrue(a2aAfter < 30.0, "playback must keep A 2 a near A 2; sep=" + a2aAfter);
            assertTrue(Math.abs(bcAfter - bcBefore) < 5.0, "playback should not re-flatten B+C apart");
            assertTrue(Math.abs(a2aAfter - a2aBefore) < 3.0, "playback should not drift A 2 a away from A 2");
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
        void bcd4_onNull2Trunk_withMapPosition() {
            double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
            int null2 = SystemOrbitGeometry.planetBinaryBarycentreMapKey(2);
            int a0 = model.projectionAxis0();
            int a1 = model.projectionAxis1();
            double[] hub2 = model.positionsMetres().get(Integer.valueOf(null2));
            double[] p4 = model.positionsMetres().get(Integer.valueOf(id("BCD 4")));
            double d4 = Math.hypot(
                    SystemOrbitGeometry.worldAxisMetres(p4, a0) - SystemOrbitGeometry.worldAxisMetres(hub2, a0),
                    SystemOrbitGeometry.worldAxisMetres(p4, a1) - SystemOrbitGeometry.worldAxisMetres(hub2, a1)) / ls;
            double mutual2 = SystemOrbitGeometry.planetBinaryMutualOrbitRadiusLsPublic(2, bodies);
            assertTrue(d4 >= mutual2 * 0.35 && d4 <= mutual2 * 1.05,
                    "BCD 4 should sit on the Null:2 trunk ring, not at the hub; dist=" + d4 + " Ls mutual2="
                            + mutual2);
        }

        @Test
        void bcd2Cluster_nearBcStellarHub_notIsolated() {
            double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
            double bx = model.mapPlaneX(id("B"));
            double by = model.mapPlaneY(id("B"));
            double cx = model.mapPlaneX(id("C"));
            double cy = model.mapPlaneY(id("C"));
            double hubX = (bx + cx) * 0.5;
            double hubY = (by + cy) * 0.5;
            double d2 = Math.hypot(model.mapPlaneX(id("BCD 2")) - hubX, model.mapPlaneY(id("BCD 2")) - hubY) / ls;
            assertTrue(d2 < 250.0, "BCD 2 should stay near the B+C/D cluster, not float away; d=" + d2);
        }

        @Test
        void primaryBarycentricStar_onSystemBarycentreRing() {
            OrbitGeometryTestSupport.assertHierarchicalBarycentreRing(model, bodies, id("A"));
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
