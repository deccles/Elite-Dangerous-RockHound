package org.dce.systemmodel.build;

import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanBaryCentreRecord;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.model.HierarchyKeys;
import org.dce.systemmodel.model.SystemModel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Co-orbit majors at {@code Null:20} — bary parent from journal {@code parents[0]}. */
class CoOrbitBaryOrbitParentTest {

    @Test
    void null20_withMoonsAndCoOrbitPlanets_baryOrbitsStar() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        SystemModel model = new SystemModelBuilder()
                .systemName("Eol Prou NN-Y b31-0")
                .add(star(t))
                .add(planet(t, 21, 5, orbit))
                .add(planet(t, 25, 6, orbit))
                .add(moon(t, 210, 5, 21, "a", orbit))
                .add(new ScanBaryCentreRecord(
                        t, 20, "Eol Prou NN-Y b31-0 barycentre 20",
                        List.of(new ParentRef(ParentRef.ParentType.STAR, 0)),
                        List.of(),
                        new OrbitalElements(1e11, 0, 0, 0, 0, 0, 100_000, t)))
                .buildPartial();

        int null20Key = HierarchyKeys.baryMapKey(20);
        assertEquals(ParentRef.ParentType.STAR, model.barycentre(20).orElseThrow().orbitParent().type());
        assertEquals(0, model.hierarchy().parentOf(null20Key).intValue());
        assertEquals(null20Key, model.hierarchy().parentOf(21).intValue());
        assertEquals(null20Key, model.hierarchy().parentOf(25).intValue());
        assertEquals(List.of(21, 25), model.hierarchy().childrenOf(null20Key));
        assertEquals(21, model.hierarchy().parentOf(210).intValue());
    }

    @Test
    void null20_moonsAndRings_parentToHostPlanetsNotNull20() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        int null20Key = HierarchyKeys.baryMapKey(20);
        SystemModel model = new SystemModelBuilder()
                .systemName("Eol Prou NN-Y b31-0")
                .add(star(t))
                .add(planet(t, 21, 5, orbit))
                .add(planet(t, 25, 6, orbit))
                .add(moon(t, 211, 5, 21, "a", orbit))
                .add(moon(t, 212, 5, 21, "b", orbit))
                .add(ring(t, 301, 21, orbit))
                .add(ring(t, 302, 25, orbit))
                .add(ring(t, 303, 25, orbit))
                .add(new ScanBaryCentreRecord(
                        t, 20, "Eol Prou NN-Y b31-0 barycentre 20",
                        List.of(new ParentRef(ParentRef.ParentType.STAR, 0)),
                        List.of(),
                        new OrbitalElements(1e11, 0, 0, 0, 0, 0, 100_000, t)))
                .buildPartial();

        assertEquals(List.of(21, 25), model.hierarchy().childrenOf(null20Key));
        assertEquals(21, model.hierarchy().parentOf(211).intValue());
        assertEquals(21, model.hierarchy().parentOf(212).intValue());
        assertEquals(21, model.hierarchy().parentOf(301).intValue());
        assertEquals(25, model.hierarchy().parentOf(302).intValue());
        assertEquals(25, model.hierarchy().parentOf(303).intValue());
        assertEquals(List.of(211, 212, 301), model.hierarchy().childrenOf(21));
        assertEquals(List.of(302, 303), model.hierarchy().childrenOf(25));
    }

    @Test
    void null20_planetaryRingScanWithPlanetBodyType_parentToHostPlanet() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        int null20Key = HierarchyKeys.baryMapKey(20);
        SystemModel model = new SystemModelBuilder()
                .systemName("Eol Prou NN-Y b31-0")
                .add(star(t))
                .add(planet(t, 21, 5, orbit))
                .add(planet(t, 25, 6, orbit))
                .add(ring(t, 401, 21, orbit))
                .add(new ScanBaryCentreRecord(
                        t, 20, "Eol Prou NN-Y b31-0 barycentre 20",
                        List.of(new ParentRef(ParentRef.ParentType.STAR, 0)),
                        List.of(),
                        new OrbitalElements(1e11, 0, 0, 0, 0, 0, 100_000, t)))
                .buildPartial();

        assertEquals(21, model.hierarchy().parentOf(401).intValue());
        assertEquals(null20Key, model.hierarchy().parentOf(21).intValue());
    }

    @Test
    void baryWithEmptyParents_derivesFromCoOrbitMemberChains() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        var bc = new SystemModelBuilder()
                .systemName("Test")
                .add(star(t))
                .add(planet(t, 21, 5, orbit))
                .add(planet(t, 25, 6, orbit))
                .add(new ScanBaryCentreRecord(t, 20, "barycentre 20", List.of(), List.of(), orbit))
                .buildPartial()
                .barycentre(20)
                .orElseThrow();
        assertEquals(ParentRef.ParentType.STAR, bc.orbitParent().type());
        assertEquals(0, bc.orbitParent().bodyId());
    }

    private static ScanRecord star(Instant t) {
        return new ScanRecord(
                t, 0, "Eol Prou NN-Y b31-0", "Star", "M", 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), null, true, false);
    }

    private static ScanRecord planet(Instant t, int bodyId, int designation, OrbitalElements orbit) {
        return new ScanRecord(
                t, bodyId, "Eol Prou NN-Y b31-0 " + designation, "Planet", "Rocky", 100,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(
                        new ParentRef(ParentRef.ParentType.NULL, 20),
                        new ParentRef(ParentRef.ParentType.STAR, 0)),
                orbit, true, false);
    }

    private static ScanRecord moon(
            Instant t, int bodyId, int designation, int hostId, String letter, OrbitalElements orbit) {
        return new ScanRecord(
                t, bodyId, "Eol Prou NN-Y b31-0 " + designation + " " + letter, "Planet", "Rocky", 99,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(
                        new ParentRef(ParentRef.ParentType.PLANET, hostId),
                        new ParentRef(ParentRef.ParentType.NULL, 20),
                        new ParentRef(ParentRef.ParentType.STAR, 0)),
                orbit, true, false);
    }

    private static ScanRecord ring(Instant t, int bodyId, int hostId, OrbitalElements orbit) {
        return new ScanRecord(
                t, bodyId, "Eol Prou NN-Y b31-0 5 A Ring", "Ring", "", 99,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(
                        new ParentRef(ParentRef.ParentType.PLANET, hostId),
                        new ParentRef(ParentRef.ParentType.NULL, 20),
                        new ParentRef(ParentRef.ParentType.STAR, 0)),
                orbit, true, false);
    }
}
