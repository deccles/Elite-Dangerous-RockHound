package org.dce.systemmodel.build;

import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ScanBaryCentreRecord;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.model.SystemModel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrphanBarycentreOmitTest {

    @Test
    void orphanScanBarycentre_notInModel() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        SystemModel model = new SystemModelBuilder()
                .systemName("Test")
                .add(star(t))
                .add(new ScanBaryCentreRecord(
                        t, 99, "Test bary 99", List.of(), List.of(),
                        new OrbitalElements(1e11, 0, 0, 0, 0, 0, 100_000, t)))
                .buildPartial();
        assertFalse(model.barycentres().containsKey(99));
    }

    @Test
    void nestedBarycentre_outerKept() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        SystemModel model = new SystemModelBuilder()
                .systemName("Nested")
                .add(star(t))
                .add(new ScanBaryCentreRecord(
                        t, 67, "Nested bary 67", List.of(), List.of(),
                        new OrbitalElements(2e11, 0, 0, 0, 0, 0, 100_000, t)))
                .add(new ScanBaryCentreRecord(
                        t, 32, "Nested bary 32",
                        List.of(new org.dce.systemmodel.journal.ParentRef(
                                org.dce.systemmodel.journal.ParentRef.ParentType.NULL, 67)),
                        List.of(),
                        new OrbitalElements(1e11, 0, 0, 0, 0, 0, 100_000, t)))
                .buildPartial();
        assertTrue(model.barycentres().containsKey(67));
        assertTrue(model.barycentres().containsKey(32));
    }

    private static ScanRecord star(Instant t) {
        return new ScanRecord(
                t, 0, "Nested", "Star", "M", 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), null, true, false);
    }
}
