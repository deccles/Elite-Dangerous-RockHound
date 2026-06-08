package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmap.SystemMapHierarchyBuilder.Graph;
import org.dce.ed.systemmap.SystemMapSystemLoader.Loaded;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.systemmodel.model.BodyKind;
import org.dce.systemmodel.model.BodyNode;
import org.dce.systemmodel.model.HierarchyKeys;
import org.dce.systemmodel.model.SystemModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Eol Prou TV-A c15-43: G+K inner binary at Null:1 (arrival barycentre), outer M at Null:0.
 * Hierarchy must list A, B, inner barycentre 1, and C — not only the C branch.
 */
class EolProuTvAC1543TripleStarTest {

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static SystemSession session;
    private static int idA;
    private static int idB;
    private static int idC;
    private static int null1Key;

    @BeforeAll
    static void load() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-tv-a-c15-43.json");
        bodies = fixture.toBodies();
        session = SystemSessionFactory.open(new Loaded(fixture.name, bodies, "cache"));
        idA = fixture.bodyIdByLabel("A");
        idB = fixture.bodyIdByLabel("B");
        idC = fixture.bodyIdByLabel("C");
        null1Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(1);
    }

    @Test
    void session_hasAllThreeStars() {
        assertTrue(session.hasModel(), "model from fixture");
        SystemModel model = session.model();
        assertTrue(model.bodies().containsKey(idA), "star A in model");
        assertTrue(model.bodies().containsKey(idB), "star B in model");
        assertTrue(model.bodies().containsKey(idC), "star C in model");
    }

    @Test
    void modelHierarchy_innerBaryUnderSystemBarycentre() {
        SystemModel model = session.model();
        int null1 = HierarchyKeys.baryMapKey(1);
        Integer baryParent = model.hierarchy().parentOf(null1);
        assertTrue(baryParent != null, "Null:1 has parent");
        assertEquals(HierarchyKeys.baryMapKey(0), baryParent.intValue(),
                "inner A-B barycentre must orbit system Null:0");
        assertEquals(null1, model.hierarchy().parentOf(idA).intValue(), "A orbits Null:1");
        assertEquals(null1, model.hierarchy().parentOf(idB).intValue(), "B orbits Null:1");
        assertEquals(HierarchyKeys.baryMapKey(0), model.hierarchy().parentOf(idC).intValue(), "C orbits Null:0");
    }

    @Test
    void hierarchyGraph_listsAllStarsAndInnerBarycentre() {
        Graph graph = SystemModelHierarchyBuilder.buildForSession(session);
        assertTrue(SystemModelHierarchyBuilder.isUsableHierarchy(graph));
        assertTrue(graph.nodeByKey.containsKey(idA), "star A in graph");
        assertTrue(graph.nodeByKey.containsKey(idB), "star B in graph");
        assertTrue(graph.nodeByKey.containsKey(idC), "star C in graph");
        assertTrue(graph.nodeByKey.containsKey(null1Key), "Null:1 in graph");

        assertTrue(graph.root.children.stream().anyMatch(n -> n.mapKey == idC || n.mapKey == null1Key),
                "root has C and/or inner barycentre");
        SystemMapHierarchyBuilder.Node innerBary = graph.nodeByKey.get(null1Key);
        assertTrue(innerBary != null);
        assertTrue(innerBary.children.stream().anyMatch(n -> n.mapKey == idA), "A under Null:1");
        assertTrue(innerBary.children.stream().anyMatch(n -> n.mapKey == idB), "B under Null:1");
    }

    @Test
    void noOrphanStarsInHierarchy() {
        Graph graph = SystemModelHierarchyBuilder.buildForSession(session);
        for (BodyNode b : session.model().bodies().values()) {
            if (b.kind() == BodyKind.STAR) {
                assertTrue(graph.nodeByKey.containsKey(b.bodyId()),
                        "star " + b.bodyId() + " must not be pruned");
            }
        }
    }

    @Test
    void journalDistances_innerCompanionNearerThanOuter() {
        double dAbJournal = Math.abs(bodies.get(idB).getDistanceLs() - bodies.get(idA).getDistanceLs());
        double dAcJournal = Math.abs(bodies.get(idC).getDistanceLs() - bodies.get(idA).getDistanceLs());
        assertTrue(dAbJournal < dAcJournal * 0.5,
                "B nearer A than C in journal; dAb=" + dAbJournal + " dAc=" + dAcJournal);
    }

    @Test
    void mapLayout_starsHaveFinitePositions() {
        SystemMapModel mapModel = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true, session);
        for (int id : new int[] { idA, idB, idC }) {
            assertTrue(Double.isFinite(mapModel.mapPlaneX(id)), "x for " + id);
            assertTrue(Double.isFinite(mapModel.mapPlaneY(id)), "y for " + id);
        }
    }

    @Test
    void modelHierarchy_c56CoOrbitHubUnderStarC() {
        int null37 = HierarchyKeys.baryMapKey(37);
        assertEquals(idC, session.model().hierarchy().parentOf(null37).intValue(),
                "C 5/C 6 co-orbit hub must parent to star C");
    }

    @Test
    void buildForLoaded_matchesSession() {
        Loaded loaded = new Loaded(fixture.name, bodies, "cache");
        Graph fromLoaded = SystemModelHierarchyBuilder.buildForLoaded(loaded);
        Graph fromSession = SystemModelHierarchyBuilder.buildForSession(session);
        assertTrue(fromLoaded.nodeByKey.containsKey(idA));
        assertTrue(fromLoaded.nodeByKey.containsKey(idB));
        assertEquals(fromSession.nodeByKey.size(), fromLoaded.nodeByKey.size());
    }
}
