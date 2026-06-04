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

class StellarCoOrbitOrbitParentTest {

    @Test
    void branchStarAtSharedNull_parentsToNullNotArrivalStar() {
        Instant t = Instant.parse("2026-06-01T12:00:00Z");
        int idA = 1;
        int null2 = HierarchyKeys.baryMapKey(2);
        SystemModel model = new SystemModelBuilder()
                .systemName("Eol Prou UP-N d7-288")
                .add(star(t, idA, "Eol Prou UP-N d7-288 A", 0,
                        List.of(new ParentRef(ParentRef.ParentType.NULL, 0)), null))
                .add(new ScanBaryCentreRecord(
                        t, 2, "Eol Prou UP-N d7-288 barycentre 2",
                        List.of(new ParentRef(ParentRef.ParentType.STAR, idA)),
                        List.of(),
                        new OrbitalElements(5.9E11, 0, 0, 0, 0, 0, 100_000, t)))
                .add(star(t, 3, "Eol Prou UP-N d7-288 B", 1957,
                        List.of(
                                new ParentRef(ParentRef.ParentType.NULL, 2),
                                new ParentRef(ParentRef.ParentType.STAR, idA)),
                        new OrbitalElements(5.87E11, 0, 0, 0, 0, 0, 100_000, t)))
                .buildPartial();

        assertEquals(null2, model.hierarchy().parentOf(3).intValue());
        assertEquals(idA, model.hierarchy().parentOf(null2).intValue());
    }

    private static ScanRecord star(Instant t, int id, String name, double distLs, List<ParentRef> parents,
            OrbitalElements orbit) {
        return new ScanRecord(
                t, id, name, "Star", "K", distLs,
                0, 0, 0, 0, 0, 0, 0, 0,
                parents, orbit, true, false);
    }
}
