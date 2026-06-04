package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmap.SystemMapSystemLoader.Loaded;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.systemmodel.build.SystemModelBuilder;
import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanBaryCentreRecord;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.model.HierarchyKeys;
import org.dce.systemmodel.model.SystemModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Eol Prou UP-N d7-288: F+K+G with inner B at Null:2 and outer C at Null:0. Must not merge B and C on the map;
 * hierarchy graph must list A, inner barycentre, B, and C.
 */
class EolProuUpND7288TripleStarTest {

    private static final double LS = SystemOrbitGeometry.LIGHT_SECOND_METRES;

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static SystemMapModel model;
    private static int idA;
    private static int idB;
    private static int idC;
    private static int null2Key;

    @BeforeAll
    static void load() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-up-n-d7-288.json");
        bodies = fixture.toBodies();
        model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);
        idA = fixture.bodyIdByLabel("A");
        idB = fixture.bodyIdByLabel("B");
        idC = fixture.bodyIdByLabel("C");
        null2Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(2);
    }

    @Test
    void notTightTriple_wideBcSpacing() {
        assertTrue(SystemOrbitGeometry.isHierarchicalWideBinary(bodies));
        assertFalse(SystemOrbitGeometry.isHierarchicalTripleStarMap(bodies),
                "B ~2k Ls and C ~21k Ls must not use tight B+C display cluster");
    }

    @Test
    void bAndC_separatedOnMap() {
        double dBc = Math.hypot(model.mapPlaneX(idB) - model.mapPlaneX(idC),
                model.mapPlaneY(idB) - model.mapPlaneY(idC)) / LS;
        assertTrue(dBc > 1500.0, "B and C must not overlap; dBc=" + dBc + " Ls");
        double dAb = distLs(idA, idB);
        double dAc = distLs(idA, idC);
        assertTrue(dAb < dAc * 0.5, "B nearer A than C; dAb=" + dAb + " dAc=" + dAc);
        assertTrue(dAc > 5000.0, "outer C on wide trunk; dAc=" + dAc);
    }

    @Test
    void journalModelHierarchy_listsAllStars() {
        Instant t = Instant.parse("2026-06-01T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        String sys = fixture.name;
        SystemModel journalModel = new SystemModelBuilder()
                .systemName(sys)
                .add(scan(t, idA, sys + " A", "F", 0,
                        List.of(new ParentRef(ParentRef.ParentType.NULL, 0)), null))
                .add(new ScanBaryCentreRecord(
                        t, 2, sys + " barycentre 2",
                        List.of(new ParentRef(ParentRef.ParentType.STAR, idA)),
                        List.of(),
                        new OrbitalElements(5.9E11, 0, 0, 0, 0, 0, 100_000, t)))
                .add(scan(t, idB, sys + " B", "K", 1957,
                        List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 2),
                                new ParentRef(ParentRef.ParentType.STAR, idA)),
                        orbit))
                .add(scan(t, idC, sys + " C", "G", 21067,
                        List.of(new ParentRef(ParentRef.ParentType.NULL, 0)), orbit))
                .buildPartial();

        int null2 = HierarchyKeys.baryMapKey(2);
        assertEquals(idA, journalModel.hierarchy().parentOf(null2).intValue());
        assertEquals(null2, journalModel.hierarchy().parentOf(idB).intValue());
        assertEquals(HierarchyKeys.baryMapKey(0), journalModel.hierarchy().parentOf(idC).intValue());

        Loaded loaded = new SystemMapSystemLoader.Loaded(sys, bodies, "cache");
        SystemMapHierarchyBuilder.Graph graph = SystemModelHierarchyBuilder.buildForLoaded(loaded);
        assertTrue(SystemModelHierarchyBuilder.isUsableHierarchy(graph));
        assertTrue(graph.nodeByKey.containsKey(idA), "star A");
        assertTrue(graph.nodeByKey.containsKey(idB), "star B");
        assertTrue(graph.nodeByKey.containsKey(idC), "star C");
        assertTrue(graph.nodeByKey.containsKey(null2), "Null:2");
        assertTrue(graph.root.children.stream().anyMatch(n -> n.mapKey == idA));
        assertTrue(graph.root.children.stream().anyMatch(n -> n.mapKey == idC));
        SystemMapHierarchyBuilder.Node starA = graph.nodeByKey.get(idA);
        assertTrue(starA.children.stream().anyMatch(n -> n.mapKey == null2));
        SystemMapHierarchyBuilder.Node nullHub = graph.nodeByKey.get(null2);
        assertTrue(nullHub.children.stream().anyMatch(n -> n.mapKey == idB));
    }

    @Test
    void cacheOnlyBodies_buildsUsableHierarchyGraph() {
        Loaded loaded = new SystemMapSystemLoader.Loaded(fixture.name, bodies, "cache");
        SystemMapHierarchyBuilder.Graph graph = SystemModelHierarchyBuilder.buildForLoaded(loaded);
        assertTrue(SystemModelHierarchyBuilder.isUsableHierarchy(graph));
        assertTrue(graph.nodeByKey.size() >= 5, "root + A + Null:2 + B + C");
        assertTrue(graph.nodeByKey.values().stream().anyMatch(n -> "A".equals(n.label)));
        assertTrue(graph.nodeByKey.values().stream().anyMatch(n -> "B".equals(n.label)));
        assertTrue(graph.nodeByKey.values().stream().anyMatch(n -> "C".equals(n.label)));
    }

    private static double distLs(int fromId, int toId) {
        return Math.hypot(model.mapPlaneX(toId) - model.mapPlaneX(fromId),
                model.mapPlaneY(toId) - model.mapPlaneY(fromId)) / LS;
    }

    private static ScanRecord scan(
            Instant t, int id, String name, String subType, double distLs,
            List<ParentRef> parents, OrbitalElements orbit) {
        return new ScanRecord(
                t, id, name, "Star", subType, distLs,
                0, 0, 0, 0, 0, 0, 0, 0,
                parents, orbit, true, false);
    }
}
