package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.logreader.EliteEventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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

    @Test
    void matchesJournalAttributes_withFilters() {
        ExecBinding binding = new ExecBinding();
        binding.setJournalEventType("CarrierJump");
        binding.getJournalAttributeFilters().add(
                new ExecJournalAttributeFilter("StarSystem", "Sol", ExecJournalAttributeFilter.MatchMode.EQUALS));

        var obj = com.google.gson.JsonParser.parseString(
                "{\"event\":\"CarrierJump\",\"StarSystem\":\"Sol\"}").getAsJsonObject();
        assertTrue(binding.matchesJournalAttributes(obj, Map.of()));
        assertFalse(binding.matchesJournalAttributes(
                com.google.gson.JsonParser.parseString(
                        "{\"event\":\"CarrierJump\",\"StarSystem\":\"Alpha Centauri\"}").getAsJsonObject(),
                Map.of()));
    }

    @Test
    void execJournalJsonMatcher_substitutesPlaceholder() {
        var obj = com.google.gson.JsonParser.parseString(
                "{\"event\":\"Docked\",\"StationName\":\"Jameson Memorial\"}").getAsJsonObject();
        var filter = new ExecJournalAttributeFilter("StationName", "$DEST", ExecJournalAttributeFilter.MatchMode.EQUALS);
        assertTrue(ExecJournalJsonMatcher.matches(obj, "Docked", List.of(filter), Map.of("DEST", "Jameson Memorial")));
    }
}
