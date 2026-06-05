package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmap.SystemMapHierarchyBuilder.Graph;
import org.dce.ed.systemmap.SystemMapHierarchyBuilder.Node;
import org.dce.systemmodel.model.HierarchyKeys;
import org.junit.jupiter.api.Test;

/**
 * Hierarchy graph, {@link ModelMapTopology}, and {@link SystemMapPipeline} must share the same parent edges.
 */
class SystemTopologyContractTest {

    @Test
    void upND7288_modelTopology_matchesHierarchyGraph() throws IOException {
        SystemMapFixture fx = SystemMapFixtureLoader.loadClasspath("eol-prou-up-n-d7-288.json");
        SystemSession session = SystemSessionFactory.open(new SystemMapSystemLoader.Loaded(
                fx.name, fx.toBodies(), "cache"));
        assertTrue(session.hasModel(), "model from fixture bodies");

        Graph graph = SystemModelHierarchyBuilder.buildForSession(session);
        assertTrue(SystemModelHierarchyBuilder.isUsableHierarchy(graph));

        Map<Integer, Integer> mapParents = ModelMapTopology.resolvedParents(session.model(), fx.toBodies());
        SystemMapModel mapModel = SystemMapPipeline.build(fx.name, fx.toBodies(), Instant.EPOCH, false, session);

        int idA = fx.bodyIdByLabel("A");
        int idB = fx.bodyIdByLabel("B");
        int idC = fx.bodyIdByLabel("C");
        int null2Key = org.dce.ed.util.SystemOrbitGeometry.planetBinaryBarycentreMapKey(2);

        assertParentAligned(graph, mapParents, mapModel, idA, -1);
        assertParentAligned(graph, mapParents, mapModel, idB, null2Key);
        assertParentAligned(graph, mapParents, mapModel, idC, -1);

        Node starA = graph.nodeByKey.get(idA);
        assertTrue(starA.children.stream().anyMatch(n -> n.mapKey == null2Key));
        Node nullHub = graph.nodeByKey.get(null2Key);
        assertTrue(nullHub.children.stream().anyMatch(n -> n.mapKey == idB));
    }

    private static void assertParentAligned(
            Graph graph,
            Map<Integer, Integer> mapParents,
            SystemMapModel mapModel,
            int childId,
            int expectedParentKey) {
        Node child = graph.nodeByKey.get(childId);
        assertTrue(child != null, "graph contains " + childId);
        assertEquals(
                normalizeRootKey(expectedParentKey),
                normalizeRootKey(child.parentKey),
                "graph parent for " + childId);
        Integer mapParent = mapParents.get(Integer.valueOf(childId));
        assertTrue(mapParent != null, "ModelMapTopology parent for " + childId);
        assertEquals(
                normalizeRootKey(expectedParentKey),
                normalizeRootKey(mapParent.intValue()),
                "ModelMapTopology parent for " + childId);
        assertEquals(
                normalizeRootKey(expectedParentKey),
                normalizeRootKey(mapModel.resolveParentBodyId(childId)),
                "SystemMapModel parent for " + childId);
    }

    private static int normalizeRootKey(int key) {
        if (key == -1 || key == HierarchyKeys.baryMapKey(0)) {
            return -1;
        }
        return key;
    }
}
