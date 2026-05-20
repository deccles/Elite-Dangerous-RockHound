package org.dce.ed.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.logreader.event.ScanEvent.ParentRef;
import org.dce.ed.systemmap.SystemMapFixture;
import org.dce.ed.systemmap.SystemMapFixtureLoader;
import org.dce.ed.systemmap.SystemMapHierarchyBuilder;
import org.dce.ed.systemmap.SystemMapModel;
import org.dce.ed.systemmap.SystemMapPipeline;
import org.junit.jupiter.api.Test;

class JournalParentRefsTest {

    @Test
    void formatParentsLine_resolvesStarAndNullNames() {
        BodyInfo star = new BodyInfo();
        star.setBodyId(1);
        star.setBodyShortName("A");
        star.setStarType("M");
        BodyInfo a2 = new BodyInfo();
        a2.setBodyId(21);
        a2.setBodyShortName("A 2");
        a2.setJournalParentRefs(List.of("Null:14", "Star:1", "Null:0"));
        Map<Integer, BodyInfo> bodies = Map.of(
                Integer.valueOf(1), star,
                Integer.valueOf(21), a2);
        String line = JournalParentRefs.formatParentsLine(a2.getJournalParentRefs(), bodies);
        assertEquals("Parents: Null:14 → A → Null:0", line);
    }

    @Test
    void fromScanParents_preservesOrder() {
        List<String> refs = JournalParentRefs.fromScanParents(List.of(
                new ParentRef("Null", 14),
                new ParentRef("Star", 1),
                new ParentRef("Null", 0)));
        assertEquals(List.of("Null:14", "Star:1", "Null:0"), refs);
    }

    @Test
    void formatParentsLine_resolvesPlanetParentByJournalBodyId() {
        BodyInfo a4 = new BodyInfo();
        a4.setBodyId(19);
        a4.setBodyShortName("A 4");
        BodyInfo moon = new BodyInfo();
        moon.setBodyId(25);
        moon.setJournalParentRefs(List.of("Planet:19"));
        Map<Integer, BodyInfo> bodies = Map.of(
                Integer.valueOf(19), a4,
                Integer.valueOf(25), moon);
        assertEquals("Parents: A 4", JournalParentRefs.formatParentsLine(moon.getJournalParentRefs(), bodies));
    }

    @Test
    void formatImmediateParentOnly_resolvesPlanetParentByJournalBodyId() {
        BodyInfo a4 = new BodyInfo();
        a4.setBodyId(19);
        a4.setBodyShortName("A 4");
        Map<Integer, BodyInfo> bodies = Map.of(Integer.valueOf(19), a4);
        assertEquals("Parents: A 4", JournalParentRefs.formatImmediateParentOnly(19, bodies));
    }

    @Test
    void formatParentsLineForMapBody_moonParent19_resolvesA4WhenMapKey24() {
        BodyInfo a4 = new BodyInfo();
        a4.setBodyId(24);
        a4.setBodyShortName("A 4");
        BodyInfo moon = new BodyInfo();
        moon.setBodyId(25);
        moon.setBodyShortName("A 4 a");
        moon.setImmediateParentBodyId(19);
        Map<Integer, BodyInfo> bodies = Map.of(
                Integer.valueOf(24), a4,
                Integer.valueOf(25), moon);
        SystemMapModel model = SystemMapPipeline.build("Coeus", bodies, Instant.EPOCH, true);
        assertEquals("Parents: A 4",
                JournalParentRefs.formatParentsLineForMapBody(moon, 25, bodies, model));
    }

    @Test
    void formatPlanetBinaryHubParentsLine_stripsSelfNull_andShowsStarA() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, true);
        String line = JournalParentRefs.formatPlanetBinaryHubParentsLine(14, bodies, model);
        assertEquals("Parents: A → Null:0", line);
        assertTrue(!line.contains("Null:14 → Null:14"));
    }

    @Test
    void formatParentsLine_findsParentWhenMapKeyDiffersFromBodyId() {
        BodyInfo a4 = new BodyInfo();
        a4.setBodyId(19);
        a4.setBodyShortName("A 4");
        BodyInfo moon = new BodyInfo();
        moon.setJournalParentRefs(List.of("Planet:19"));
        Map<Integer, BodyInfo> bodies = new java.util.HashMap<>();
        bodies.put(Integer.valueOf(24), a4);
        assertEquals("Parents: A 4", JournalParentRefs.formatParentsLine(moon.getJournalParentRefs(), bodies));
    }

    @Test
    void coeus_hierarchyGraph_showsParentsOnA2() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, true);
        SystemMapHierarchyBuilder.Graph g = SystemMapHierarchyBuilder.build(coeus.name, model, bodies);
        SystemMapHierarchyBuilder.Node a2 = findNode(g.root, "A 2");
        assertNotNull(a2);
        assertNotNull(a2.parentsLine);
        assertTrue(a2.parentsLine.contains("Null:14"));
        assertTrue(a2.parentsLine.contains("→ A →"));
    }

    @Test
    void coeus_hierarchyGraph_moonA4a_parentsA4_not19() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, true);
        SystemMapHierarchyBuilder.Graph g = SystemMapHierarchyBuilder.build(coeus.name, model, bodies);
        SystemMapHierarchyBuilder.Node moon = findNode(g.root, "A 4 a");
        assertNotNull(moon);
        assertEquals("Parents: A 4", moon.parentsLine);
    }

    @Test
    void formatMapParentLabel_planetBinaryHub_usesNullN() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, true);
        int a2 = coeus.bodyIdByLabel("A 2");
        assertEquals("Null:14",
                JournalParentRefs.formatMapParentLabel(model, bodies, a2,
                        org.dce.ed.util.SystemOrbitGeometry.primaryAnchorBodyMapKey(bodies)));
    }

    @Test
    void coeus_hierarchyGraph_null14Hub_parentsA_notSelf() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, true);
        SystemMapHierarchyBuilder.Graph g = SystemMapHierarchyBuilder.build(coeus.name, model, bodies);
        SystemMapHierarchyBuilder.Node starA = findNode(g.root, "A");
        SystemMapHierarchyBuilder.Node null14 = findNode(starA, "Null:14");
        assertNotNull(null14);
        assertEquals("Parents: A → Null:0", null14.parentsLine);
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
