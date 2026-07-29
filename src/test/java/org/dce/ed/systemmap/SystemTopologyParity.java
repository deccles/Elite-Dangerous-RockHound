package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.dce.ed.systemmap.SystemMapHierarchyBuilder.Graph;
import org.dce.ed.systemmap.SystemMapHierarchyBuilder.Node;
import org.dce.systemmodel.model.BodyKind;
import org.dce.systemmodel.model.BodyNode;
import org.dce.systemmodel.model.HierarchyKeys;

/**
 * Shared assertions: hierarchy graph, {@link ModelMapTranscriber}, and {@link SystemMapModel} parent edges align.
 */
final class SystemTopologyParity {

    private SystemTopologyParity() {
    }

    static void assertNoOrphanStars(SystemSession session, Graph graph) {
        assertTrue(session != null && session.hasModel(), "session has model");
        Set<Integer> pruned = SystemModelHierarchyBuilder.prunedStellarBodyIds(session.model(), graph);
        assertTrue(pruned.isEmpty(), "stars pruned from hierarchy: " + pruned);
        for (BodyNode b : session.model().bodies().values()) {
            if (b.kind() == BodyKind.STAR) {
                assertTrue(graph.nodeByKey.containsKey(b.bodyId()),
                        "star " + b.bodyId() + " in hierarchy graph");
            }
        }
    }

    static void assertParentAligned(
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
        assertTrue(mapParent != null, "transcriber parent for " + childId);
        assertEquals(
                normalizeRootKey(expectedParentKey),
                normalizeRootKey(mapParent.intValue()),
                "transcriber parent for " + childId);
        assertEquals(
                normalizeRootKey(expectedParentKey),
                normalizeRootKey(mapModel.resolveParentBodyId(childId)),
                "SystemMapModel parent for " + childId);
    }

    static SystemSession openSession(SystemMapFixture fx) {
        return SystemSessionFactory.open(new SystemMapSystemLoader.Loaded(
                fx.name, fx.toBodies(), "cache"));
    }

    static int normalizeRootKey(int key) {
        if (key == -1 || key == HierarchyKeys.baryMapKey(0)) {
            return -1;
        }
        return key;
    }

    static int baryKey(int journalNullId) {
        return org.dce.ed.util.SystemOrbitGeometry.planetBinaryBarycentreMapKey(journalNullId);
    }
}
