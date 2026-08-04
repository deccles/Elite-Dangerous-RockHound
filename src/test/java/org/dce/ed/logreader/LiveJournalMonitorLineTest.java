package org.dce.ed.logreader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LiveJournalMonitorLineTest {

    @Test
    void completeJournalLine_requiresBracedJsonObject() {
        assertTrue(LiveJournalMonitor.looksLikeCompleteJournalLine(
                "{\"timestamp\":\"2026-08-01T20:00:00Z\",\"event\":\"Bounty\"}"));
        assertFalse(LiveJournalMonitor.looksLikeCompleteJournalLine(
                "{\"timestamp\":\"2026-08-01T20:00:00Z\",\"event\":\"Bounty\""));
        assertFalse(LiveJournalMonitor.looksLikeCompleteJournalLine(""));
        assertFalse(LiveJournalMonitor.looksLikeCompleteJournalLine(null));
    }
}
