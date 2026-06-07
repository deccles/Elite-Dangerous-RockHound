package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;
import org.dce.ed.systemmap.SystemMapSystemLoader.Loaded;
import org.dce.ed.systemmodel.SystemModelService;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.systemmodel.journal.ParentRef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Eol Prou SV-A c15-56: simple wide binary A+B (~163k Ls) with four majors on B and B 1 a moon.
 */
class EolProuSvAC1556WideBinaryTest {

    private static final double LS = SystemOrbitGeometry.LIGHT_SECOND_METRES;

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static SystemMapModel model;
    private static int idA;
    private static int idB;

    @BeforeAll
    static void load() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-sv-a-c15-56.json");
        bodies = fixture.toBodies();
        model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);
        idA = fixture.bodyIdByLabel("A");
        idB = fixture.bodyIdByLabel("B");
    }

    @Test
    void classifiedAsWideBinary_withMutualRing() {
        assertTrue(model.classification().wideBinary());
        assertFalseHierarchical();
        assertTrue(model.hasBarycentreMutualRing());
    }

    @Test
    void mutualRing_spansJournalAbSeparation_notTinyAroundA() {
        double abLs = Math.hypot(model.mapPlaneX(idB) - model.mapPlaneX(idA),
                model.mapPlaneY(idB) - model.mapPlaneY(idA)) / LS;
        assertTrue(abLs > 100_000.0, "A–B map chord should reflect ~163k Ls journal separation; got " + abLs);
        OrbitGeometryTestSupport.assertBodyOnBinaryBarycentreOrbitRing(model, bodies, "B", 0.05, 8.0);
    }

    @Test
    void starB_hasMajorsOneThroughFour_inHierarchy() {
        Loaded loaded = new SystemMapSystemLoader.Loaded(fixture.name, bodies, "cache");
        SystemMapHierarchyBuilder.Graph graph = SystemModelHierarchyBuilder.buildForLoaded(loaded);
        assertTrue(SystemModelHierarchyBuilder.isUsableHierarchy(graph));
        SystemMapHierarchyBuilder.Node starB = graph.nodeByKey.get(idB);
        List<String> labels = starB.children.stream().map(n -> n.label).sorted().toList();
        assertTrue(labels.contains("1"), "B 1 under B; labels=" + labels);
        assertTrue(labels.contains("2"), "B 2 under B");
        assertTrue(labels.contains("3"), "B 3 under B");
        assertTrue(labels.contains("4"), "B 4 under B");

        int b1 = fixture.bodyIdByLabel("B 1");
        SystemMapHierarchyBuilder.Node planet1 = graph.nodeByKey.get(b1);
        assertTrue(planet1.children.stream().anyMatch(n -> "a".equals(n.label)),
                "B 1 a under B 1");
    }

    @Test
    void liveSession_staleJournalNullParent_stillShowsB1InHierarchy() {
        SystemState state = new SystemState();
        state.setSystemName(fixture.name);
        for (BodyInfo body : bodies.values()) {
            state.getBodies().put(Integer.valueOf(body.getBodyId()), body);
        }
        int idB1 = fixture.bodyIdByLabel("B 1");
        state.appendJournalEvent(new ScanRecord(
                Instant.EPOCH,
                idB1,
                bodies.get(idB1).getBodyName(),
                "Planet",
                bodies.get(idB1).getPlanetClass(),
                bodies.get(idB1).getDistanceLs(),
                0, 0, 0, 0, 0, 0, 0, 0,
                java.util.List.of(new ParentRef(ParentRef.ParentType.NULL, idB)),
                null,
                true,
                false));

        SystemSession session = SystemSessionFactory.open(state);
        assertTrue(session.hasModel());
        assertTrue(session.model().hierarchy().childrenOf(idB).contains(idB1), "B 1 under star B");

        SystemMapHierarchyBuilder.Graph graph = SystemModelHierarchyBuilder.buildForSession(session);
        SystemMapHierarchyBuilder.Node starB = graph.nodeByKey.get(idB);
        assertTrue(starB.children.stream().anyMatch(n -> "1".equals(n.label)),
                "hierarchy graph shows B 1");
    }

    @Test
    void cacheOnlyPlanetUnderStar_usesStarParent_notNullHub() {
        BodyInfo starB = bodies.get(idB);
        BodyInfo b1 = bodies.get(fixture.bodyIdByLabel("B 1"));
        java.util.Map<Integer, BodyInfo> map = Map.of(idB, starB, b1.getBodyId(), b1);
        List<org.dce.systemmodel.journal.JournalRecord> merged =
                CachedBodyJournalBridge.mergeMissingFromBodyInfo(fixture.name, List.of(), map);
        assertEquals(2, merged.size());
        org.dce.systemmodel.journal.ScanRecord planet = (org.dce.systemmodel.journal.ScanRecord) merged.stream()
                .filter(r -> r instanceof org.dce.systemmodel.journal.ScanRecord s && s.bodyId() == b1.getBodyId())
                .findFirst()
                .orElseThrow();
        assertEquals(ParentRef.ParentType.STAR, planet.parents().get(0).type());
        assertEquals(idB, planet.parents().get(0).bodyId());
    }

    private static void assertFalseHierarchical() {
        assertTrue(!SystemOrbitGeometry.isHierarchicalWideBinary(bodies));
    }
}
