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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HierarchyBodyBaryIdCollisionTest {

    @Test
    void planet5_andNull5_bothInHierarchyGraph() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        SystemModel model = new SystemModelBuilder()
                .systemName("Eol Prou NN-Y b31-0")
                .add(star(t))
                .add(planet(t, 5, orbit))
                .add(moon(t, 51, 5, 5, orbit))
                .add(moon(t, 52, 5, 5, orbit))
                .add(new ScanBaryCentreRecord(
                        t, 5, "Eol Prou NN-Y b31-0 barycentre 5",
                        List.of(new ParentRef(ParentRef.ParentType.STAR, 0)),
                        List.of(),
                        new OrbitalElements(1e11, 0, 0, 0, 0, 0, 100_000, t)))
                .buildPartial();

        assertNotNull(model.body(5));
        assertNotNull(model.barycentre(5));
        assertEquals(0, model.hierarchy().parentOf(5).intValue());
        assertEquals(HierarchyKeys.baryMapKey(5), model.hierarchy().parentOf(51).intValue());
        assertTrue(model.hierarchy().childrenOf(HierarchyKeys.baryMapKey(5)).contains(51));
    }

    private static ScanRecord star(Instant t) {
        return new ScanRecord(
                t, 0, "Eol Prou NN-Y b31-0", "Star", "M", 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), null, true, false);
    }

    private static ScanRecord planet(Instant t, int id, OrbitalElements orbit) {
        return new ScanRecord(
                t, id, "Eol Prou NN-Y b31-0 " + id, "Planet", "Rocky", 100,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new ParentRef(ParentRef.ParentType.STAR, 0)), orbit, true, false);
    }

    private static ScanRecord moon(Instant t, int id, int nullId, int planetId, OrbitalElements orbit) {
        return new ScanRecord(
                t, id, "Eol Prou NN-Y b31-0 " + planetId + " a", "Planet", "Icy", 100,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(
                        new ParentRef(ParentRef.ParentType.NULL, nullId),
                        new ParentRef(ParentRef.ParentType.PLANET, planetId),
                        new ParentRef(ParentRef.ParentType.STAR, 0)),
                orbit, true, false);
    }
}
