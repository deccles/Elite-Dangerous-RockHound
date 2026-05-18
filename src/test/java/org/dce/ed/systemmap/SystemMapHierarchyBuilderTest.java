package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
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
    void eorAowsy_bNotUnderA() {
        SystemMapHierarchyBuilder.Node b = findNode(graph.root, "B");
        assertNotNull(b);
        SystemMapHierarchyBuilder.Node parent = graph.nodeByKey.get(Integer.valueOf(b.parentKey));
        assertNotNull(parent);
        assertTrue(parent.label.contains("Null:3") || parent.label.startsWith("Null:"));
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
