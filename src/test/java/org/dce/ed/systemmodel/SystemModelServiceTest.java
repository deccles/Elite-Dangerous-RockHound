package org.dce.ed.systemmodel;

import org.dce.ed.state.SystemState;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SystemModelServiceTest {

    @Test
    void rebuildReturnsErrorWithoutThrowing() {
        SystemState state = new SystemState();
        state.setSystemName("Bad");
        state.appendJournalEvent(new ScanRecord(
                Instant.EPOCH, 5, "Bad 7 d", "Planet", "Icy",
                100, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new ParentRef(ParentRef.ParentType.NULL, 99)),
                null, false, false));
        SystemModelService.ModelHandle h = SystemModelService.rebuild(state, true);
        assertEquals(SystemModelService.ModelState.INCOMPLETE, h.state());
        assertNotNull(h.statusMessage());
    }
}
