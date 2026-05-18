package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Ground-truth validation for {@code Eor Aowsy RI-K c8-3670}: journal hierarchy vs {@link SystemMapModel} topology
 * and schematic positions. Encodes screenshot + journal negatives explicitly (B/C/D must not orbit star A).
 */
class EorAowsySystemMapValidationTest {

    private static final double LS = SystemOrbitGeometry.LIGHT_SECOND_METRES;

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static SystemMapModel model;
    private static int idA;
    private static int null2Key;
    private static int null3Key;

    @BeforeAll
    static void loadFullFixture() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eor-aowsy-ri-k-c8-3670.json");
        bodies = fixture.toBodies();
        model = SystemMapPipeline.build(fixture.name, bodies, java.time.Instant.EPOCH, true);
        idA = fixture.bodyIdByLabel("A");
        null2Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(2);
        null3Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(3);
    }

    private static int id(String label) {
        return fixture.bodyIdByLabel(label);
    }

    private static int parent(String label) {
        return model.resolveParentBodyId(id(label));
    }

    private static double distLs(int fromId, int toId) {
        double dx = model.mapPlaneX(toId) - model.mapPlaneX(fromId);
        double dy = model.mapPlaneY(toId) - model.mapPlaneY(fromId);
        return Math.hypot(dx, dy) / LS;
    }

    private static int branchStarForBody(String label) {
        int p = parent(label);
        return SystemMapRules.branchSchematicStarParentId(bodies, p);
    }

    @Nested
    @DisplayName("Journal ground truth (Parents / ScanBaryCentre)")
    class JournalGroundTruth {

        @Test
        void starA_parentsNull0_systemBarycentre() {
            assertTrue(parent("A") < 0);
            assertFalse(SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(parent("A")));
        }

        @Test
        void starsBAndC_immediateParentNull3() {
            assertEquals(3, bodies.get(id("B")).getImmediateParentBodyId());
            assertEquals(3, bodies.get(id("C")).getImmediateParentBodyId());
        }

        @Test
        void starD_immediateParentNull2() {
            assertEquals(2, bodies.get(id("D")).getImmediateParentBodyId());
        }

        @Test
        void scanBarycentreRows_presentForNull2And3() {
            assertTrue(bodies.get(Integer.valueOf(2)).isScanBarycentreRow());
            assertTrue(bodies.get(Integer.valueOf(3)).isScanBarycentreRow());
        }

        @Test
        void bcdGiants_parentNull49() {
            assertEquals(49, bodies.get(id("BCD 2")).getImmediateParentBodyId());
            assertEquals(49, bodies.get(id("BCD 3")).getImmediateParentBodyId());
        }
    }

    @Nested
    @DisplayName("Resolved topology (must match Elite tree, not one ring around A)")
    class ResolvedTopology {

        @Test
        void b_c_d_notParentedToStarA() {
            for (String star : List.of("B", "C", "D")) {
                assertNotEquals(idA, parent(star), star + " must not orbit A");
            }
        }

        @Test
        void b_c_parentNull3_notA() {
            assertEquals(null3Key, parent("B"));
            assertEquals(null3Key, parent("C"));
        }

        @Test
        void d_parentNull2_notA() {
            assertEquals(null2Key, parent("D"));
        }

        @Test
        void bcdPlanets_notUnderA() {
            for (String label : List.of("BCD 1", "BCD 2", "BCD 3", "BCD 4", "BCD 5")) {
                int p = parent(label);
                assertNotEquals(idA, p, label);
                assertNotEquals(null3Key, p, label + " not on BC-only hub");
            }
            assertEquals(null2Key, parent("BCD 1"));
            assertEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(49), parent("BCD 2"));
            assertEquals(null2Key, parent("BCD 4"));
        }

        @Test
        void onlyA_isWideBinarySystemBarycentreStar() {
            Set<Integer> baryStars = model.wideBinarySystemBarycentreStarIds();
            assertEquals(Set.of(Integer.valueOf(idA)), baryStars);
            assertEquals(1, model.classification().barycentricStarIds().size());
        }

        @Test
        void branchStar_forBcdBodies_notA() {
            for (String label : List.of("B", "C", "D", "BCD 1", "BCD 4", "BCD 5")) {
                int bs = branchStarForBody(label);
                assertNotEquals(idA, bs, label + " branch schematic must not be A");
            }
        }

        @Test
        void aBranchPlanets_branchStarIsA() {
            for (String label : List.of("A 1", "A 2", "A 3", "A 4")) {
                assertEquals(idA, parent(label));
                assertEquals(idA, branchStarForBody(label));
            }
        }
    }

    @Nested
    @DisplayName("Map-plane layout (two trunks, BCD cluster grouped)")
    class MapPlaneLayout {

        @Test
        void bcdStars_clustered_nearEachOther_notAtAInnerRing() {
            double sepBc = distLs(id("B"), id("C"));
            double dA1 = distLs(idA, id("A 1"));
            double dBfromA = distLs(idA, id("B"));
            assertTrue(sepBc < 500.0, "B–C separation Ls=" + sepBc);
            assertTrue(dBfromA > dA1 + 1000.0, "B must be far from A vs A-branch; dB=" + dBfromA + " dA1=" + dA1);
            assertTrue(distLs(id("B"), id("D")) < 2000.0, "BCD stars should be grouped");
        }

        @Test
        void bcd2_notOnSameRadiusFromA_asA3() {
            double rA3 = distLs(idA, id("A 3"));
            double rBcd2 = distLs(idA, id("BCD 2"));
            assertTrue(Math.abs(rBcd2 - rA3) > 500.0,
                    "BCD 2 must not share A-centric ring radius with A 3; rA3=" + rA3 + " rBcd2=" + rBcd2);
        }

        @Test
        void bcd2_nearBcd3_notOppositeOnAWideRing() {
            double sep23 = distLs(id("BCD 2"), id("BCD 3"));
            double sep2A = distLs(idA, id("BCD 2"));
            assertTrue(sep23 < 100.0, "BCD 2–3 binary pair should be close; sep=" + sep23);
            assertTrue(sep2A > sep23 * 10.0, "BCD 2 should not sit on A wide-binary chord with BCD 3 at opposite ends");
        }

        @Test
        void companionSubsystem_separateTrunkWithoutGiantFourStarRing() {
            double dB = distLs(idA, id("B"));
            double dA1 = distLs(idA, id("A 1"));
            assertTrue(dB > dA1 + 1000.0, "BCD trunk must be outside A inner planets; dB=" + dB);
            assertFalse(model.hasBarycentreMutualRing(),
                    "must not draw one Null:0 ring through A+B+C+D at heliocentric radius");
        }
    }

    @Nested
    @DisplayName("Orbit strokes (rings must not collapse to one A-centric circle)")
    class OrbitStrokes {

        @Test
        void noSystemBarycentreMutualRingThroughFourStars() {
            assertFalse(model.hasBarycentreMutualRing());
        }

        @Test
        void atMostTwoStarsOnSystemBarycentreRingLogic() {
            long baryStellar = bodies.entrySet().stream()
                    .filter(e -> e.getValue() != null && SystemMapRules.isMapStellarBody(e.getValue()))
                    .filter(e -> SystemOrbitGeometry.orbitsWideBinarySystemBarycentre(
                            e.getValue(), bodies, e.getKey().intValue()))
                    .count();
            assertTrue(baryStellar <= 2, "Only A (+ optional single companion) orbit system barycentre; count=" + baryStellar);
        }

        @Test
        void branchSchematicRings_atLeastA_andBcdHub() {
            assertTrue(model.schematicBranchRingCount() >= 1,
                    "Expect concentric branch rings at A; count=" + model.schematicBranchRingCount());
        }

        @Test
        void planetBinary49_mutualRingPresent() {
            assertTrue(model.hasPlanetBinaryMutualRing(49));
        }

        @Test
        void noPerBodyKeplerRingOnBcdGiants() {
            for (String label : List.of("BCD 2", "BCD 3")) {
                assertFalse(model.hasOrbitRingForBody(id(label)), label);
            }
        }
    }

    @Nested
    @DisplayName("Partial FSS (no ScanBaryCentre rows — regression)")
    class WithoutScanBarycentreRows {

        private static SystemMapModel partialModel;

        @BeforeAll
        static void buildWithoutBaryRows() throws IOException {
            Map<Integer, BodyInfo> copy = new HashMap<>();
            for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
                if (e.getValue() != null && e.getValue().isScanBarycentreRow()) {
                    continue;
                }
                copy.put(e.getKey(), e.getValue());
            }
            partialModel = SystemMapPipeline.build(fixture.name, copy, java.time.Instant.EPOCH, true);
        }

        @Test
        void b_stillNotParentedToA_withoutBaryRows() {
            int bId = id("B");
            int p = partialModel.resolveParentBodyId(bId);
            assertNotEquals(idA, p);
            assertEquals(null3Key, p);
        }

        @Test
        void bcdCluster_stillFarFromA_withoutBaryRows() {
            double dBcd2 = Math.hypot(
                    partialModel.mapPlaneX(id("BCD 2")) - partialModel.mapPlaneX(idA),
                    partialModel.mapPlaneY(id("BCD 2")) - partialModel.mapPlaneY(idA)) / LS;
            double dA1 = Math.hypot(
                    partialModel.mapPlaneX(id("A 1")) - partialModel.mapPlaneX(idA),
                    partialModel.mapPlaneY(id("A 1")) - partialModel.mapPlaneY(idA)) / LS;
            assertTrue(dBcd2 > dA1 + 1000.0,
                    "BCD 2 must stay on companion trunk, not A inner ring; dBcd2=" + dBcd2 + " dA1=" + dA1);
        }
    }

    @Nested
    @DisplayName("Structured comparison dump (Phase 2 audit trail)")
    class TopologyDump {

        @Test
        void printResolvedParentsAndPositions() {
            System.out.println(SystemMapTreePrinter.formatTree(model, bodies, false));
            assertTrue(SystemMapTreePrinter.formatTree(model, bodies, false).contains("BCD 2 a"));
        }

        @Test
        void generateValidationChecklist() throws IOException {
            SystemMapExpectedTree tree = SystemMapExpectedTreeLoader
                    .loadClasspath("eor-aowsy-ri-k-c8-3670-expected-tree.json");
            StringBuilder sb = new StringBuilder("| body | journal parent | expected | model | pass |\n");
            sb.append("|------|----------------|----------|-------|------|\n");
            int pass = 0;
            int total = 0;
            for (SystemMapExpectedTree.BodyEntry entry : tree.bodies) {
                total++;
                BodyInfo b = bodies.get(Integer.valueOf(entry.id));
                String journal = b != null ? journalParentLabel(b) : "?";
                String expected = entry.expectedResolve;
                String resolved = SystemMapTreePrinter.formatResolvedParent(model, bodies, entry.id, idA);
                boolean ok = expected.equals(resolved);
                if (ok) {
                    pass++;
                }
                sb.append(String.format("| %s | %s | %s | %s | %s |%n",
                        entry.shortName, journal, expected, resolved, ok ? "yes" : "NO"));
            }
            sb.append(String.format("%n%d/%d passed%n", pass, total));
            System.out.println(sb);
            assertEquals(total, pass, "all bodies must match expected-tree contract");
        }

        private static String journalParentLabel(BodyInfo b) {
            if (b.getImmediateParentBodyId() == 0) {
                return "Null:0";
            }
            if (b.getImmediateParentBodyId() > 0) {
                return "id:" + b.getImmediateParentBodyId();
            }
            return "unset";
        }
    }
}
