package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/**
 * No {@code A …} body may resolve, parent, or draw on the map around star/planet {@code C} (or any other letter).
 */
class DesignationBranchInvariantTest {

    private static final List<String> A_BRANCH_LABELS = List.of(
            "A 1", "A 2", "A 3", "A 4",
            "A 2 a", "A 3 a", "A 3 b", "A 3 c", "A 3 d", "A 3 e",
            "A 4 a", "A 4 b", "A 4 c");

    private static final List<String> A_MOON_LABELS = List.of(
            "A 2 a", "A 3 a", "A 3 b", "A 3 c", "A 3 d", "A 3 e", "A 4 a", "A 4 b", "A 4 c");

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static SystemMapModel model;
    private static int idA;
    private static int idB;
    private static int idC;
    private static int idD;

    @BeforeAll
    static void load() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eor-aowsy-ri-k-c8-3670.json");
        bodies = fixture.toBodies();
        model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);
        idA = fixture.bodyIdByLabel("A");
        idB = fixture.bodyIdByLabel("B");
        idC = fixture.bodyIdByLabel("C");
        idD = fixture.bodyIdByLabel("D");
    }

    private static Map<Integer, BodyInfo> copyBodies() {
        return new HashMap<>(bodies);
    }

    @Test
    void fixture_journalTopology_designationBranchInvariants() {
        OrbitGeometryTestSupport.assertDesignationBranchInvariants(model, bodies);
    }

    @ParameterizedTest(name = "A-branch body {0} parented to star C")
    @ValueSource(strings = {
            "A 1", "A 2", "A 3", "A 4", "A 2 a", "A 3 a", "A 3 b", "A 3 e", "A 4 b"
    })
    void aBranch_corruptParentToStarC_stillMapsOnBranchA(String label) {
        Map<Integer, BodyInfo> cache = copyBodies();
        cache.get(Integer.valueOf(fixture.bodyIdByLabel(label))).setImmediateParentBodyId(idC);
        SystemMapModel corrupt = SystemMapPipeline.build(fixture.name, cache, Instant.EPOCH, true);
        OrbitGeometryTestSupport.assertDesignationBranchInvariants(corrupt, cache);
    }

    static Stream<Arguments> aBranchWrongStarParents() {
        Stream.Builder<Arguments> out = Stream.builder();
        for (String label : A_BRANCH_LABELS) {
            for (String wrongStar : List.of("B", "C", "D")) {
                out.add(Arguments.of(label, wrongStar));
            }
        }
        return out.build();
    }

    @ParameterizedTest(name = "{0} parented to star {1}")
    @MethodSource("aBranchWrongStarParents")
    void aBranch_corruptParentToWrongStar_invariantsHold(String label, String wrongStar) {
        Map<Integer, BodyInfo> cache = copyBodies();
        int wrongId = fixture.bodyIdByLabel(wrongStar);
        cache.get(Integer.valueOf(fixture.bodyIdByLabel(label))).setImmediateParentBodyId(wrongId);
        SystemMapModel corrupt = SystemMapPipeline.build(fixture.name, cache, Instant.EPOCH, true);
        OrbitGeometryTestSupport.assertDesignationBranchInvariants(corrupt, cache);
    }

    static Stream<Arguments> aMoonWrongHosts() {
        Stream.Builder<Arguments> out = Stream.builder();
        for (String moon : A_MOON_LABELS) {
            out.add(Arguments.of(moon, "C"));
            out.add(Arguments.of(moon, "B"));
            out.add(Arguments.of(moon, "D"));
            out.add(Arguments.of(moon, "BCD 2"));
            out.add(Arguments.of(moon, "BCD 3"));
        }
        return out.build();
    }

    @ParameterizedTest(name = "moon {0} parented to {1}")
    @MethodSource("aMoonWrongHosts")
    void aMoon_corruptParentToWrongHost_resolvesToAbranch(String moonLabel, String wrongHostLabel) {
        Map<Integer, BodyInfo> cache = copyBodies();
        int moonId = fixture.bodyIdByLabel(moonLabel);
        int wrongHostId = fixture.bodyIdByLabel(wrongHostLabel);
        cache.get(Integer.valueOf(moonId)).setImmediateParentBodyId(wrongHostId);
        SystemMapModel corrupt = SystemMapPipeline.build(fixture.name, cache, Instant.EPOCH, true);
        OrbitGeometryTestSupport.assertDesignationBranchInvariants(corrupt, cache);
        int resolved = corrupt.resolveParentBodyId(moonId);
        assertTrue(resolved != wrongHostId,
                moonLabel + " must not stay parented to " + wrongHostLabel);
        assertTrue(resolved != idC && resolved != idB && resolved != idD,
                moonLabel + " must not resolve to wrong branch star");
    }

    @Test
    void aBranch_parentedToBcNull3_resolvesToStarA_notPlanetBinary3() {
        Map<Integer, BodyInfo> cache = copyBodies();
        int null3 = 3;
        for (String label : List.of("A 1", "A 2", "A 3", "A 4")) {
            cache.get(Integer.valueOf(fixture.bodyIdByLabel(label))).setImmediateParentBodyId(null3);
        }
        SystemMapModel corrupt = SystemMapPipeline.build(fixture.name, cache, Instant.EPOCH, true);
        for (String label : List.of("A 2", "A 3", "A 4")) {
            int id = fixture.bodyIdByLabel(label);
            int resolved = corrupt.resolveParentBodyId(id);
            assertTrue(resolved == idA,
                    label + " on Null:3 must resolve to star A, was " + resolved);
        }
        SystemMapHierarchyBuilder.Graph graph = SystemMapHierarchyBuilder.build(fixture.name, corrupt, cache);
        SystemMapHierarchyBuilder.Node starA = findNode(graph.root, "A");
        assertNotNull(starA);
        for (String label : List.of("A 2", "A 3", "A 4")) {
            SystemMapHierarchyBuilder.Node body = findNode(graph.root, label);
            assertNotNull(body);
            assertSame(starA, ancestorStar(graph, body, "A"), label + " hierarchy must hang under star A");
        }
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

    private static SystemMapHierarchyBuilder.Node ancestorStar(SystemMapHierarchyBuilder.Graph graph,
            SystemMapHierarchyBuilder.Node start, String letter) {
        for (SystemMapHierarchyBuilder.Node cur = start; cur != null; cur = parentOf(graph, cur)) {
            if (cur.kind == SystemMapHierarchyBuilder.NodeKind.STAR
                    && letter.equalsIgnoreCase(cur.label.trim())) {
                return cur;
            }
        }
        return null;
    }

    private static SystemMapHierarchyBuilder.Node parentOf(SystemMapHierarchyBuilder.Graph graph,
            SystemMapHierarchyBuilder.Node node) {
        if (node.parentKey == Integer.MIN_VALUE) {
            return null;
        }
        return graph.nodeByKey.get(Integer.valueOf(node.parentKey));
    }

    @Test
    void a2ParentedToStarA_mustNotResolveToCompanionNull3() {
        Map<Integer, BodyInfo> cache = copyBodies();
        int idA2 = fixture.bodyIdByLabel("A 2");
        cache.get(Integer.valueOf(idA2)).setImmediateParentBodyId(idA);
        SystemMapModel model = SystemMapPipeline.build(fixture.name, cache, Instant.EPOCH, true);
        int resolved = model.resolveParentBodyId(idA2);
        assertTrue(resolved == idA,
                "A 2 on star A must resolve to star A, not companion null (was " + resolved + ")");
        assertTrue(resolved != SystemOrbitGeometry.planetBinaryBarycentreMapKey(3),
                "A 2 must not resolve to Null:3 barycentre");
    }

    @Test
    void a2ParentedToStarC_resolvedParentMustNotParkOnCompanionBarycentre() {
        Map<Integer, BodyInfo> cache = copyBodies();
        BodyInfo a2 = cache.get(Integer.valueOf(fixture.bodyIdByLabel("A 2")));
        a2.setImmediateParentBodyId(idC);
        int resolved = SystemOrbitGeometry.resolveOrbitParentBodyId(a2, cache, fixture.bodyIdByLabel("A 2"));
        assertTrue(resolved != idC,
                "A 2 cache-parent C must not resolve to star C for placement (was " + resolved + ")");
        SystemMapModel corrupt = SystemMapPipeline.build(fixture.name, cache, Instant.EPOCH, true);
        OrbitGeometryTestSupport.assertNuclearDesignationBranchPlacement(corrupt, cache);
    }

    @Test
    void playback_entireAbranchParentedToC_refreshPositionsInvariants() {
        Map<Integer, BodyInfo> cache = copyBodies();
        for (String label : A_BRANCH_LABELS) {
            cache.get(Integer.valueOf(fixture.bodyIdByLabel(label))).setImmediateParentBodyId(idC);
        }
        SystemMapModel base = SystemMapPipeline.build(fixture.name, cache, Instant.EPOCH, true);
        Instant epoch = Instant.EPOCH.plusSeconds(86_400L * 30);
        Map<Integer, double[]> after = SystemMapPipeline.refreshPositionsForPlayback(base,
                new HashMap<>(base.positionsMetres()), epoch, true);
        SystemMapModel playback = SystemMapPipeline.playbackBase(cache, base.projectionAxis0(),
                base.projectionAxis1(), after, base.wideBinaryFlattenFrame());
        OrbitGeometryTestSupport.assertDesignationBranchInvariants(playback, cache);
    }
}
