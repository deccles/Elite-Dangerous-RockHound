package org.dce.ed.systemmodel.adapter;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.dce.ed.logreader.event.ScanEvent;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JournalEventAdapterTest {

    @Test
    void mapsParentsAndBodyId() {
        ScanEvent e = new ScanEvent(
                Instant.EPOCH,
                null,
                "Eol Prou NN-Y b31-0 7 d",
                33,
                "Eol Prou NN-Y b31-0",
                42L,
                1403.0,
                false,
                "Icy body",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1e9,
                0.01,
                0.1,
                0.2,
                0.3,
                0.4,
                null,
                null,
                null,
                null,
                Collections.emptyMap(),
                "M",
                List.of(new ScanEvent.ParentRef("Null", 32), new ScanEvent.ParentRef("Planet", 28)),
                List.of(),
                null,
                null,
                null,
                null);
        ScanRecord r = JournalEventAdapter.fromScanEvent(e);
        assertEquals(33, r.bodyId());
        assertEquals(ParentRef.ParentType.NULL, r.parents().getFirst().type());
        assertEquals(32, r.parents().getFirst().bodyId());
    }
}
