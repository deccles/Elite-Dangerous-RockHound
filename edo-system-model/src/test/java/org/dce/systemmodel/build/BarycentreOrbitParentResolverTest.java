package org.dce.systemmodel.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanBaryCentreRecord;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.model.HierarchyKeys;
import org.dce.systemmodel.model.SystemModel;
import org.junit.jupiter.api.Test;

class BarycentreOrbitParentResolverTest {

    @Test
    void eolProuSvAC1533_null13_derivesStar2FromCoOrbitPlanets() {
        Instant t = Instant.parse("2026-06-08T04:42:37Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        SystemModel model = new SystemModelBuilder()
                .systemName("Eol Prou SV-A c15-33")
                .add(star(t, 1, "A", List.of(new ParentRef(ParentRef.ParentType.NULL, 0))))
                .add(star(t, 2, "B", List.of(new ParentRef(ParentRef.ParentType.NULL, 0))))
                .add(planet(t, 14, "B 7", List.of(
                        new ParentRef(ParentRef.ParentType.NULL, 13),
                        new ParentRef(ParentRef.ParentType.STAR, 2),
                        new ParentRef(ParentRef.ParentType.NULL, 0)), orbit))
                .add(planet(t, 15, "B 8", List.of(
                        new ParentRef(ParentRef.ParentType.NULL, 13),
                        new ParentRef(ParentRef.ParentType.STAR, 2),
                        new ParentRef(ParentRef.ParentType.NULL, 0)), orbit))
                .add(new ScanBaryCentreRecord(
                        t, 13, "Eol Prou SV-A c15-33 barycentre 13",
                        List.of(),
                        List.of(),
                        new OrbitalElements(3.326005935668945E10, 0, 0, 0, 0, 0, 6_466_135.919, t)))
                .build();

        int null13 = HierarchyKeys.baryMapKey(13);
        var bc = model.barycentre(13).orElseThrow();
        assertEquals(ParentRef.ParentType.STAR, bc.orbitParent().type());
        assertEquals(2, bc.orbitParent().bodyId());
        assertEquals(2, model.hierarchy().parentOf(null13).intValue());
        assertEquals(null13, model.hierarchy().parentOf(14).intValue());
        assertEquals(null13, model.hierarchy().parentOf(15).intValue());
    }

    @Test
    void binaryMoons_derivesPlanetHostFromMemberChain() {
        Instant t = Instant.EPOCH;
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        SystemModel model = new SystemModelBuilder()
                .systemName("Eol Prou NN-Y b31-0")
                .add(star(t, 0, "Star", List.of()))
                .add(planet(t, 28, "7", List.of(new ParentRef(ParentRef.ParentType.STAR, 0)), orbit))
                .add(moon(t, 33, List.of(
                        new ParentRef(ParentRef.ParentType.NULL, 32),
                        new ParentRef(ParentRef.ParentType.PLANET, 28),
                        new ParentRef(ParentRef.ParentType.STAR, 0)), orbit))
                .add(moon(t, 34, List.of(
                        new ParentRef(ParentRef.ParentType.NULL, 32),
                        new ParentRef(ParentRef.ParentType.PLANET, 28),
                        new ParentRef(ParentRef.ParentType.STAR, 0)), orbit))
                .add(new ScanBaryCentreRecord(t, 32, "barycentre 32", List.of(), List.of(), orbit))
                .buildPartial();

        var bc = model.barycentre(32).orElseThrow();
        assertEquals(ParentRef.ParentType.PLANET, bc.orbitParent().type());
        assertEquals(28, bc.orbitParent().bodyId());
    }

    @Test
    void stellarPair_derivesStar0FromMemberChain() {
        Instant t = Instant.EPOCH;
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        SystemModelBuilder builder = new SystemModelBuilder()
                .systemName("Stellar pair")
                .add(star(t, 0, "A", List.of()))
                .add(star(t, 10, "B", List.of(
                        new ParentRef(ParentRef.ParentType.NULL, 16),
                        new ParentRef(ParentRef.ParentType.STAR, 0)), orbit))
                .add(star(t, 11, "C", List.of(
                        new ParentRef(ParentRef.ParentType.NULL, 16),
                        new ParentRef(ParentRef.ParentType.STAR, 0)), orbit))
                .add(new ScanBaryCentreRecord(t, 16, "barycentre 16", List.of(), List.of(), orbit));

        assertFalse(builder.incompleteReasons().stream().anyMatch(r -> r.contains("barycentre 16")));
        var bc = builder.buildPartial().barycentre(16).orElseThrow();
        assertEquals(ParentRef.ParentType.STAR, bc.orbitParent().type());
        assertEquals(0, bc.orbitParent().bodyId());
    }

    @Test
    void conflictingMemberChains_returnsNull() {
        ParentRef a = BarycentreOrbitParentResolver.orbitParentAfterHub(13, List.of(
                new ParentRef(ParentRef.ParentType.NULL, 13),
                new ParentRef(ParentRef.ParentType.STAR, 2)));
        ParentRef b = BarycentreOrbitParentResolver.orbitParentAfterHub(13, List.of(
                new ParentRef(ParentRef.ParentType.NULL, 13),
                new ParentRef(ParentRef.ParentType.STAR, 3)));
        assertEquals(ParentRef.ParentType.STAR, a.type());
        assertEquals(2, a.bodyId());
        assertNull(BarycentreOrbitParentResolver.fromMemberChains(
                13,
                List.of(14, 15),
                Map.of(
                        14, planet(Instant.EPOCH, 14, "a", List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 13),
                                new ParentRef(ParentRef.ParentType.STAR, 2)), null),
                        15, planet(Instant.EPOCH, 15, "b", List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 13),
                                new ParentRef(ParentRef.ParentType.STAR, 3)), null))));
    }

    private static ScanRecord star(Instant t, int id, String label, List<ParentRef> parents) {
        return star(t, id, label, parents, null);
    }

    private static ScanRecord star(
            Instant t, int id, String label, List<ParentRef> parents, OrbitalElements orbit) {
        return new ScanRecord(
                t, id, "Test " + label, "Star", "K", 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                parents, orbit, true, false);
    }

    private static ScanRecord planet(Instant t, int id, String label, List<ParentRef> parents, OrbitalElements orbit) {
        return new ScanRecord(
                t, id, "Test " + label, "Planet", "Rocky", 100,
                0, 0, 0, 0, 0, 0, 0, 0,
                parents, orbit, true, false);
    }

    private static ScanRecord moon(Instant t, int id, List<ParentRef> parents, OrbitalElements orbit) {
        return new ScanRecord(
                t, id, "Test 7 d", "Planet", "Icy", 100,
                0, 0, 0, 0, 0, 0, 0, 0,
                parents, orbit, true, false);
    }
}
