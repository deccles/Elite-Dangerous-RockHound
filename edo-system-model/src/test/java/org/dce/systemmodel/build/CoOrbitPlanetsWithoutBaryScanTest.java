package org.dce.systemmodel.build;

import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.model.SystemModel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Co-orbit majors at {@code Null:N} without {@code ScanBaryCentre} are non-definitive (M-0). */
class CoOrbitPlanetsWithoutBaryScanTest {

    @Test
    void twoPlanetsAtNull5_noBary_nonDefinitive() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        SystemModel model = new SystemModelBuilder()
                .systemName("Eol Prou NN-Y b31-0")
                .add(star(t))
                .add(planet(t, 10, 5, orbit))
                .add(planet(t, 11, 6, orbit))
                .buildPartial();

        assertFalse(model.barycentre(5).isPresent(), "no synthetic Null:5 barycentre");
        assertFalse(model.body(10).orElseThrow().definitive());
        assertFalse(model.body(11).orElseThrow().definitive());
        assertTrue(new SystemModelBuilder()
                .systemName("Eol Prou NN-Y b31-0")
                .add(star(t))
                .add(planet(t, 10, 5, orbit))
                .add(planet(t, 11, 6, orbit))
                .incompleteReasons()
                .stream()
                .anyMatch(r -> r.contains("ScanBaryCentre")));
    }

    @Test
    void withBaryScan_definitive() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        SystemModel model = new SystemModelBuilder()
                .systemName("Test")
                .add(star(t))
                .add(planet(t, 10, 5, orbit))
                .add(planet(t, 11, 6, orbit))
                .add(new org.dce.systemmodel.journal.ScanBaryCentreRecord(
                        t, 5, "Test barycentre 5",
                        List.of(new ParentRef(ParentRef.ParentType.STAR, 0)),
                        List.of(),
                        orbit))
                .buildPartial();

        assertTrue(model.barycentre(5).isPresent());
        assertTrue(model.body(10).orElseThrow().definitive());
        assertEquals(org.dce.systemmodel.model.HierarchyKeys.baryMapKey(5),
                model.hierarchy().parentOf(10).intValue());
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
                        new ParentRef(ParentRef.ParentType.NULL, 5),
                        new ParentRef(ParentRef.ParentType.STAR, 0)),
                orbit, true, false);
    }
}
