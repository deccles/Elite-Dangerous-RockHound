package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
}
