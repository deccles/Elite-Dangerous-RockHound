package org.dce.systemmodel.build;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanBaryCentreRecord;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.model.HierarchyKeys;
import org.dce.systemmodel.model.SystemModel;
import org.junit.jupiter.api.Test;

/** Inner A+B arrival barycentre (Null:1) with outer C at Null:0 — Eol Prou TV-A c15-43 pattern. */
class InnerStellarBaryOrbitParentTest {

    @Test
    void innerPairBarycentre_orbitsSystemNull0() {
        Instant t = Instant.parse("2026-06-07T20:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e11, 0, 0, 0, 0, 0, 100_000, t);
        int idA = 2;
        int idB = 3;
        int idC = 4;
        SystemModel model = new SystemModelBuilder()
                .systemName("Eol Prou TV-A c15-43")
                .add(new ScanBaryCentreRecord(
                        t, 1, "barycentre 1",
                        List.of(new ParentRef(ParentRef.ParentType.NULL, 0)),
                        List.of(),
                        orbit))
                .add(star(t, idA, "A", "G", 0, List.of(new ParentRef(ParentRef.ParentType.NULL, 1))))
                .add(star(t, idB, "B", "K", 787, List.of(new ParentRef(ParentRef.ParentType.NULL, 1))))
                .add(star(t, idC, "C", "M", 69803, List.of(new ParentRef(ParentRef.ParentType.NULL, 0))))
                .buildPartial();

        int null1 = HierarchyKeys.baryMapKey(1);
        assertEquals(HierarchyKeys.baryMapKey(0), model.hierarchy().parentOf(null1).intValue());
        assertEquals(null1, model.hierarchy().parentOf(idA).intValue());
        assertEquals(null1, model.hierarchy().parentOf(idB).intValue());
        assertEquals(HierarchyKeys.baryMapKey(0), model.hierarchy().parentOf(idC).intValue());
    }

    private static ScanRecord star(
            Instant t, int id, String letter, String subType, double distLs, List<ParentRef> parents) {
        return new ScanRecord(
                t, id, "Test " + letter, "Star", subType, distLs,
                0, 0, 0, 0, 0, 0, 0, 0,
                parents, null, true, false);
    }
}
