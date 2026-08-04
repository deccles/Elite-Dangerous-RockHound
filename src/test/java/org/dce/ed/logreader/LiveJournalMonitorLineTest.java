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

    @Test
    void statusMeaningfulContentChanged_falseWhenOnlyTimestampWouldDiffer() {
        int[] pips = { 2, 2, 4 };
        assertFalse(LiveJournalMonitor.statusMeaningfulContentChanged(
                0x100, 0, pips, 0, 0,
                16.0, 0.5, 0.0, "Clean", 1_000_000L,
                null, null, null, null, null, null, null,
                null, null, null, null,
                0x100, 0, pips, 0, 0,
                16.0, 0.5, 0.0, "Clean", 1_000_000L,
                null, null, null, null, null, null, null,
                null, null, null, null));
    }

    @Test
    void statusMeaningfulContentChanged_trueOnDestinationChange() {
        int[] pips = { 2, 2, 4 };
        assertTrue(LiveJournalMonitor.statusMeaningfulContentChanged(
                0x100, 0, pips, 0, 0,
                16.0, 0.5, 0.0, "Clean", 1_000_000L,
                null, null, null, null, null, null, null,
                2557753660122L, 67, "MacLean Terminal", null,
                0x100, 0, pips, 0, 0,
                16.0, 0.5, 0.0, "Clean", 1_000_000L,
                null, null, null, null, null, null, null,
                null, null, null, null));
    }

    @Test
    void statusMeaningfulContentChanged_trueOnFlagsChange() {
        int[] pips = { 2, 2, 4 };
        assertTrue(LiveJournalMonitor.statusMeaningfulContentChanged(
                0x200, 0, pips, 0, 0,
                16.0, 0.5, 0.0, "Clean", 1_000_000L,
                null, null, null, null, null, null, null,
                null, null, null, null,
                0x100, 0, pips, 0, 0,
                16.0, 0.5, 0.0, "Clean", 1_000_000L,
                null, null, null, null, null, null, null,
                null, null, null, null));
    }

    @Test
    void statusMeaningfulContentChanged_trueOnFirstSampleWhenLastPipsNull() {
        int[] pips = { 2, 2, 4 };
        assertTrue(LiveJournalMonitor.statusMeaningfulContentChanged(
                0x100, 0, pips, 0, 0,
                16.0, 0.5, 0.0, "Clean", 1_000_000L,
                null, null, null, null, null, null, null,
                null, null, null, null,
                Integer.MIN_VALUE, Integer.MIN_VALUE, null, Integer.MIN_VALUE, Integer.MIN_VALUE,
                Double.NaN, Double.NaN, Double.NaN, null, Long.MIN_VALUE,
                null, null, null, null, null, null, null,
                null, null, null, null));
    }
}
