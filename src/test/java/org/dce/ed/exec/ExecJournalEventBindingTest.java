package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.logreader.EliteEventType;
import org.junit.jupiter.api.Test;

class ExecJournalEventBindingTest {

    @Test
    void matchesJournalEvent_byJournalName() {
        ExecBinding binding = new ExecBinding();
        binding.setTrigger(ExecTriggerId.JOURNAL_EVENT);
        binding.setJournalEventType("Docked");

        assertTrue(binding.matchesJournalEvent(EliteEventType.DOCKED));
        assertFalse(binding.matchesJournalEvent(EliteEventType.UNDOCKED));
    }

    @Test
    void matchesJournalEvent_byEnumName() {
        ExecBinding binding = new ExecBinding();
        binding.setTrigger(ExecTriggerId.JOURNAL_EVENT);
        binding.setJournalEventType("FSD_JUMP");

        assertTrue(binding.matchesJournalEvent(EliteEventType.FSD_JUMP));
    }

    @Test
    void setJournalEventType_normalizesToJournalName() {
        ExecBinding binding = new ExecBinding();
        binding.setJournalEventType("FSDJump");

        assertEquals("FSDJump", binding.getJournalEventType());
        assertEquals(EliteEventType.FSD_JUMP, binding.getJournalEventTypeEnum());
    }

    @Test
    void execSelectableValues_excludesMetaTypes() {
        for (EliteEventType type : EliteEventType.execSelectableValues()) {
            assertTrue(type != EliteEventType.FILEHEADER && type != EliteEventType.UNKNOWN);
        }
    }

    @Test
    void launchContext_includesJournalEventEnv() {
        ExecLaunchContext context = ExecLaunchContext.builder(ExecTriggerId.JOURNAL_EVENT)
                .journalEventType(EliteEventType.CARRIER_JUMP_REQUEST)
                .build();

        assertEquals("CarrierJumpRequest", context.toEnvironment().get("EDO_JOURNAL_EVENT"));
    }
}
