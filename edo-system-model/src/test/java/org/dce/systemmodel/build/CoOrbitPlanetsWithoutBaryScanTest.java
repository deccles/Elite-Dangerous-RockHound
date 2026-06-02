package org.dce.systemmodel.build;

import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.model.HierarchyKeys;
import org.dce.systemmodel.model.SystemModel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Co-orbit majors at {@code Null:N} without a {@code ScanBaryCentre} row must still get a barycentre node. */
class CoOrbitPlanetsWithoutBaryScanTest {

    @Test
    void twoPlanetsAtNull5_synthesizeBary_andHierarchyUsesMapKey() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        SystemModel model = new SystemModelBuilder()
                .systemName("Eol Prou NN-Y b31-0")
                .add(star(t))
                .add(planet(t, 10, 5, orbit))
                .add(planet(t, 11, 6, orbit))
                .buildPartial();

        assertTrue(model.barycentre(5).isPresent(), "synthetic Null:5 barycentre");
        assertEquals(HierarchyKeys.baryMapKey(5), model.hierarchy().parentOf(10).intValue());
        assertEquals(HierarchyKeys.baryMapKey(5), model.hierarchy().parentOf(11).intValue());
        assertNotNull(model.body(10));
        assertNotNull(model.body(11));
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
