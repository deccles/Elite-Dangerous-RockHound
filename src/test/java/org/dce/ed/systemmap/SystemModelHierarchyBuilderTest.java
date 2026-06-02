package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.dce.ed.systemmap.SystemMapSystemLoader.Loaded;
import org.dce.systemmodel.build.SystemModelBuilder;
import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanBaryCentreRecord;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.model.SystemModel;
import org.junit.jupiter.api.Test;

class SystemModelHierarchyBuilderTest {

    @Test
    void singleMemberNull_planetOnStar_baryNotInTree() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        SystemModel model = new SystemModelBuilder()
                .systemName("Test")
                .add(scan(0, "Test", "Star", "M", 0, List.of(), null))
                .add(scan(7, "Test 7", "Planet", "Rocky", 100,
                        List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 32),
                                new ParentRef(ParentRef.ParentType.STAR, 0)),
                        orbit))
                .add(new ScanBaryCentreRecord(
                        t, 32, "bary 32",
                        List.of(new ParentRef(ParentRef.ParentType.STAR, 0)),
                        List.of(),
                        new OrbitalElements(1e11, 0, 0, 0, 0, 0, 100_000, t)))
                .buildPartial();

        SystemMapHierarchyBuilder.Graph graph = SystemModelHierarchyBuilder.buildGraph(
                "Test", model, model.hierarchy());

        assertTrue(model.barycentres().containsKey(32));
        int null32Key = org.dce.systemmodel.model.HierarchyKeys.baryMapKey(32);
        assertTrue(graph.nodeByKey.containsKey(null32Key));
        SystemMapHierarchyBuilder.Node star = graph.root.children.stream()
                .filter(n -> n.mapKey == 0)
                .findFirst()
                .orElseThrow();
        assertTrue(star.children.stream().anyMatch(n -> n.mapKey == 7));
        assertTrue(star.children.stream().anyMatch(n -> n.mapKey == null32Key));
    }

    @Test
    void coOrbitAtNull20_withMoon_doesNotParentBaryToPlanet21() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        int null20Key = org.dce.systemmodel.model.HierarchyKeys.baryMapKey(20);
        SystemModel model = new SystemModelBuilder()
                .systemName("Eol Prou NN-Y b31-0")
                .add(scan(0, "Eol Prou NN-Y b31-0", "Star", "M", 0, List.of(), null))
                .add(scan(21, "Eol Prou NN-Y b31-0 5", "Planet", "Rocky", 100,
                        List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 20),
                                new ParentRef(ParentRef.ParentType.STAR, 0)),
                        orbit))
                .add(scan(25, "Eol Prou NN-Y b31-0 6", "Planet", "Rocky", 100,
                        List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 20),
                                new ParentRef(ParentRef.ParentType.STAR, 0)),
                        orbit))
                .add(scan(210, "Eol Prou NN-Y b31-0 5 a", "Planet", "Icy", 100,
                        List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 20),
                                new ParentRef(ParentRef.ParentType.PLANET, 21),
                                new ParentRef(ParentRef.ParentType.STAR, 0)),
                        orbit))
                .add(new ScanBaryCentreRecord(
                        t, 20, "Eol Prou NN-Y b31-0 barycentre 20",
                        List.of(), List.of(),
                        new OrbitalElements(1e11, 0, 0, 0, 0, 0, 100_000, t)))
                .buildPartial();

        assertEquals(0, model.hierarchy().parentOf(null20Key).intValue());
        SystemMapHierarchyBuilder.Graph graph = SystemModelHierarchyBuilder.buildGraph(
                "Eol Prou NN-Y b31-0", model, model.hierarchy());
        assertTrue(graph.nodeByKey.containsKey(null20Key));
        assertTrue(graph.nodeByKey.containsKey(21));
        assertTrue(graph.nodeByKey.containsKey(25));
    }

    @Test
    void coOrbitAtNull20_moonsAndRingsUnderPlanetsNotUnderNullHub() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        int null20Key = org.dce.systemmodel.model.HierarchyKeys.baryMapKey(20);
        SystemModel model = new SystemModelBuilder()
                .systemName("Eol Prou NN-Y b31-0")
                .add(scan(0, "Eol Prou NN-Y b31-0", "Star", "M", 0, List.of(), null))
                .add(scan(21, "Eol Prou NN-Y b31-0 5", "Planet", "Rocky", 100,
                        List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 20),
                                new ParentRef(ParentRef.ParentType.STAR, 0)),
                        orbit))
                .add(scan(25, "Eol Prou NN-Y b31-0 6", "Planet", "Rocky", 100,
                        List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 20),
                                new ParentRef(ParentRef.ParentType.STAR, 0)),
                        orbit))
                .add(scan(211, "Eol Prou NN-Y b31-0 5 a", "Planet", "Icy", 100,
                        List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 20),
                                new ParentRef(ParentRef.ParentType.PLANET, 21),
                                new ParentRef(ParentRef.ParentType.STAR, 0)),
                        orbit))
                .add(scan(301, "Eol Prou NN-Y b31-0 Ring", "Ring", "", 100,
                        List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 20),
                                new ParentRef(ParentRef.ParentType.PLANET, 21),
                                new ParentRef(ParentRef.ParentType.STAR, 0)),
                        orbit))
                .add(new ScanBaryCentreRecord(
                        t, 20, "Eol Prou NN-Y b31-0 barycentre 20",
                        List.of(), List.of(),
                        new OrbitalElements(1e11, 0, 0, 0, 0, 0, 100_000, t)))
                .buildPartial();

        SystemMapHierarchyBuilder.Graph graph = SystemModelHierarchyBuilder.buildGraph(
                "Eol Prou NN-Y b31-0", model, model.hierarchy());
        SystemMapHierarchyBuilder.Node null20 = graph.nodeByKey.get(null20Key);
        SystemMapHierarchyBuilder.Node planet5 = graph.nodeByKey.get(21);
        assertEquals(2, null20.children.size());
        assertTrue(null20.children.contains(planet5));
        assertTrue(planet5.children.stream().anyMatch(n -> n.mapKey == 211));
        assertTrue(planet5.children.stream().anyMatch(n -> n.mapKey == 301));
    }

    @Test
    void coOrbitAtNull20_baryAndPlanetsReachableFromRoot() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        int null20Key = org.dce.systemmodel.model.HierarchyKeys.baryMapKey(20);
        SystemModel model = new SystemModelBuilder()
                .systemName("Eol Prou NN-Y b31-0")
                .add(scan(0, "Eol Prou NN-Y b31-0", "Star", "M", 0, List.of(), null))
                .add(scan(21, "Eol Prou NN-Y b31-0 5", "Planet", "Rocky", 100,
                        List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 20),
                                new ParentRef(ParentRef.ParentType.STAR, 0)),
                        orbit))
                .add(scan(25, "Eol Prou NN-Y b31-0 6", "Planet", "Rocky", 100,
                        List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 20),
                                new ParentRef(ParentRef.ParentType.STAR, 0)),
                        orbit))
                .add(new ScanBaryCentreRecord(
                        t, 20, "Eol Prou NN-Y b31-0 barycentre 20",
                        List.of(new ParentRef(ParentRef.ParentType.STAR, 0)),
                        List.of(),
                        new OrbitalElements(1e11, 0, 0, 0, 0, 0, 100_000, t)))
                .buildPartial();

        assertEquals(0, model.hierarchy().parentOf(null20Key).intValue());
        SystemMapHierarchyBuilder.Graph graph = SystemModelHierarchyBuilder.buildGraph(
                "Eol Prou NN-Y b31-0", model, model.hierarchy());

        assertTrue(graph.nodeByKey.containsKey(21));
        assertTrue(graph.nodeByKey.containsKey(25));
        assertTrue(graph.nodeByKey.containsKey(null20Key));
        SystemMapHierarchyBuilder.Node bary = graph.nodeByKey.get(null20Key);
        assertTrue(bary.children.stream().anyMatch(n -> n.mapKey == 21));
        assertTrue(bary.children.stream().anyMatch(n -> n.mapKey == 25));
    }

    @Test
    void nestedBarycentre_planetsUnderInnerHub_visibleInTree() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        int null67 = org.dce.systemmodel.model.HierarchyKeys.baryMapKey(67);
        int null20 = org.dce.systemmodel.model.HierarchyKeys.baryMapKey(20);
        SystemModel model = new SystemModelBuilder()
                .systemName("Eol Prou NN-Y b31-0")
                .add(scan(0, "Eol Prou NN-Y b31-0", "Star", "M", 0, List.of(), null))
                .add(new ScanBaryCentreRecord(
                        t, 67, "outer", List.of(), List.of(),
                        new OrbitalElements(2e11, 0, 0, 0, 0, 0, 100_000, t)))
                .add(new ScanBaryCentreRecord(
                        t, 20, "inner",
                        List.of(new ParentRef(ParentRef.ParentType.NULL, 67)),
                        List.of(),
                        new OrbitalElements(1e11, 0, 0, 0, 0, 0, 100_000, t)))
                .add(scan(21, "Eol Prou NN-Y b31-0 5", "Planet", "Rocky", 100,
                        List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 20),
                                new ParentRef(ParentRef.ParentType.STAR, 0)),
                        orbit))
                .add(scan(25, "Eol Prou NN-Y b31-0 6", "Planet", "Rocky", 100,
                        List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 20),
                                new ParentRef(ParentRef.ParentType.STAR, 0)),
                        orbit))
                .buildPartial();

        SystemMapHierarchyBuilder.Graph graph = SystemModelHierarchyBuilder.buildGraph(
                "Eol Prou NN-Y b31-0", model, model.hierarchy());

        assertTrue(graph.nodeByKey.containsKey(21));
        assertTrue(graph.nodeByKey.containsKey(25));
        assertTrue(graph.nodeByKey.containsKey(null20));
        assertTrue(graph.nodeByKey.containsKey(null67));
        SystemMapHierarchyBuilder.Node inner = graph.nodeByKey.get(null20);
        assertTrue(inner.children.stream().anyMatch(n -> n.mapKey == 21));
        assertTrue(inner.children.stream().anyMatch(n -> n.mapKey == 25));
    }

    @Test
    void coOrbitPlanetsWithoutBaryScan_visibleInHierarchyTree() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        SystemModel model = new SystemModelBuilder()
                .systemName("Eol Prou NN-Y b31-0")
                .add(scan(0, "Eol Prou NN-Y b31-0", "Star", "M", 0, List.of(), null))
                .add(scan(10, "Eol Prou NN-Y b31-0 5", "Planet", "Rocky", 100,
                        List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 5),
                                new ParentRef(ParentRef.ParentType.STAR, 0)),
                        orbit))
                .add(scan(11, "Eol Prou NN-Y b31-0 6", "Planet", "Rocky", 100,
                        List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 5),
                                new ParentRef(ParentRef.ParentType.STAR, 0)),
                        orbit))
                .buildPartial();

        SystemMapHierarchyBuilder.Graph graph = SystemModelHierarchyBuilder.buildGraph(
                "Eol Prou NN-Y b31-0", model, model.hierarchy());

        assertTrue(graph.nodeByKey.containsKey(10), "planet designation 5");
        assertTrue(graph.nodeByKey.containsKey(11), "planet designation 6");
        int null5Key = org.dce.systemmodel.model.HierarchyKeys.baryMapKey(5);
        assertTrue(graph.nodeByKey.containsKey(null5Key));
        SystemMapHierarchyBuilder.Node bary = graph.nodeByKey.get(null5Key);
        assertTrue(bary.children.stream().anyMatch(n -> n.mapKey == 10));
        assertTrue(bary.children.stream().anyMatch(n -> n.mapKey == 11));
    }

    @Test
    void structuralBarycentre_omittedFromTree() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        SystemModel model = new SystemModelBuilder()
                .systemName("Test")
                .add(scan(0, "Test", "Star", "M", 0, List.of(), null))
                .add(new ScanBaryCentreRecord(
                        t, 99, "bary 99", List.of(), List.of(),
                        new OrbitalElements(1e11, 0, 0, 0, 0, 0, 100_000, t)))
                .buildPartial();

        SystemMapHierarchyBuilder.Graph graph = SystemModelHierarchyBuilder.buildGraph(
                "Test", model, model.hierarchy());

        assertFalse(model.barycentres().containsKey(99));
        assertFalse(graph.nodeByKey.containsKey(org.dce.systemmodel.model.HierarchyKeys.baryMapKey(99)));
        assertTrue(graph.root.children.stream().anyMatch(n -> n.mapKey == 0));
    }

    @Test
    void twoStarBinary_cacheBodies_buildsUsableHierarchy() throws Exception {
        SystemMapFixture fx = SystemMapFixtureLoader.loadClasspath("tt-x-c15-29-two-star-binary.json");
        SystemMapSystemLoader.Loaded loaded = new SystemMapSystemLoader.Loaded(
                fx.name, fx.toBodies(), "cache");
        SystemMapHierarchyBuilder.Graph graph = SystemModelHierarchyBuilder.buildForLoaded(loaded);
        assertTrue(SystemModelHierarchyBuilder.isUsableHierarchy(graph), "usable tree");
        assertEquals(2, graph.root.children.size(), "both stars under Null:0");
    }

    @Test
    void wideBinary_nullInChainNotMembership_planetsUnderStar_binaryMoonsUnderPlanet2() {
        String sys = "Eol Prou YF-N d7-1186";
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        int null44Key = org.dce.systemmodel.model.HierarchyKeys.baryMapKey(44);
        SystemModel model = new SystemModelBuilder()
                .systemName(sys)
                .add(scan(0, sys, "Star", "G", 0, List.of(new ParentRef(ParentRef.ParentType.NULL, 0)), null))
                .add(scan(2, sys + " B", "Star", "K", 27000,
                        List.of(new ParentRef(ParentRef.ParentType.NULL, 0)), null))
                .add(scan(10, sys + " 1", "Planet", "Sudarsky class I gas giant", 100,
                        List.of(
                                new ParentRef(ParentRef.ParentType.STAR, 2),
                                new ParentRef(ParentRef.ParentType.NULL, 0)),
                        orbit))
                .add(scan(20, sys + " 2", "Planet", "Sudarsky class I gas giant", 200,
                        List.of(
                                new ParentRef(ParentRef.ParentType.STAR, 2),
                                new ParentRef(ParentRef.ParentType.NULL, 0)),
                        orbit))
                .add(scan(21, sys + " 2 a", "Planet", "Icy", 201,
                        List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 44),
                                new ParentRef(ParentRef.ParentType.PLANET, 20),
                                new ParentRef(ParentRef.ParentType.STAR, 2)),
                        orbit))
                .add(scan(22, sys + " 2 b", "Planet", "Icy", 202,
                        List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 44),
                                new ParentRef(ParentRef.ParentType.PLANET, 20),
                                new ParentRef(ParentRef.ParentType.STAR, 2)),
                        orbit))
                .add(scan(23, sys + " 2 c", "Planet", "Icy", 203,
                        List.of(
                                new ParentRef(ParentRef.ParentType.PLANET, 20),
                                new ParentRef(ParentRef.ParentType.STAR, 2)),
                        orbit))
                .add(scan(30, sys + " 3", "Planet", "Sudarsky class I gas giant", 300,
                        List.of(
                                new ParentRef(ParentRef.ParentType.STAR, 2),
                                new ParentRef(ParentRef.ParentType.NULL, 44),
                                new ParentRef(ParentRef.ParentType.NULL, 0)),
                        orbit))
                .buildPartial();

        SystemMapHierarchyBuilder.Graph graph = SystemModelHierarchyBuilder.buildGraph(
                sys, model, model.hierarchy());
        assertTrue(SystemModelHierarchyBuilder.isUsableHierarchy(graph));
        assertEquals(2, graph.root.children.size(), "both stars under Null:0");
        assertEquals(2, model.hierarchy().parentOf(20).intValue(), "planet 2 under star B");
        assertEquals(2, model.hierarchy().parentOf(30).intValue(), "planet 3 under star B, not Null:44");
        assertEquals(20, model.hierarchy().parentOf(null44Key).intValue(), "Null:44 under planet 2");
        assertEquals(null44Key, model.hierarchy().parentOf(21).intValue(), "moon 2a under Null:44");
        assertEquals(null44Key, model.hierarchy().parentOf(22).intValue(), "moon 2b under Null:44");
        assertEquals(20, model.hierarchy().parentOf(23).intValue(), "moon 2c direct under planet 2");
        SystemMapHierarchyBuilder.Node starB = graph.nodeByKey.get(2);
        assertTrue(starB.children.stream().anyMatch(n -> n.mapKey == 20));
        assertTrue(starB.children.stream().anyMatch(n -> n.mapKey == 30));
        assertFalse(starB.children.stream().anyMatch(n -> n.mapKey == null44Key));
    }

    private static ScanRecord scan(
            int id, String name, String bodyType, String subType, double distLs,
            List<ParentRef> parents, OrbitalElements orbit) {
        return new ScanRecord(
                Instant.EPOCH, id, name, bodyType, subType, distLs,
                0, 0, 0, 0, 0, 0, 0, 0,
                parents, orbit, true, false);
    }
}
