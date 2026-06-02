package org.dce.systemmodel.journal;

import java.time.Instant;
import java.util.List;

public record ScanBaryCentreRecord(
        Instant timestamp,
        int bodyId,
        String bodyName,
        List<ParentRef> parents,
        List<Integer> childBodyIds,
        OrbitalElements orbit) implements JournalRecord {
}
