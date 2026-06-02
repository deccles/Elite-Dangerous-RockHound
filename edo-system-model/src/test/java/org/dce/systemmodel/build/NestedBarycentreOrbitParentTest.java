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

class NestedBarycentreOrbitParentTest {

    @Test
    void innerBarycentre_orbitsOuterNull_notStar() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e11, 0, 0, 0, 0, 0, 100_000, t);
        SystemModel model = new SystemModelBuilder()
                .systemName("Nested")
                .add(star(t))
                .add(new ScanBaryCentreRecord(
                        t, 67, "outer", List.of(), List.of(),
                        new OrbitalElements(2e11, 0, 0, 0, 0, 0, 100_000, t)))
                .add(new ScanBaryCentreRecord(
                        t, 32, "inner",
                        List.of(new ParentRef(ParentRef.ParentType.NULL, 67)),
                        List.of(),
                        new OrbitalElements(1e11, 0, 0, 0, 0, 0, 100_000, t)))
                .buildPartial();

        var inner = model.barycentre(32).orElseThrow();
        assertEquals(ParentRef.ParentType.NULL, inner.orbitParent().type());
        assertEquals(67, inner.orbitParent().bodyId());
        assertEquals(HierarchyKeys.baryMapKey(67), model.hierarchy().parentOf(HierarchyKeys.baryMapKey(32)).intValue());
        assertEquals(0, model.hierarchy().parentOf(HierarchyKeys.baryMapKey(67)).intValue());
    }

    private static ScanRecord star(Instant t) {
        return new ScanRecord(
                t, 0, "Nested", "Star", "M", 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), null, true, false);
    }
}
