package org.dce.ed.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Locks sheet-related mining log policies documented on {@link ProspectorMiningLogPolicy}.
 */
class ProspectorMiningLogPolicyTest {

    @Test
    void shouldWriteRunStart_nullIncoming_neverWrites() {
        assertFalse(ProspectorMiningLogPolicy.shouldWriteRunStartOnUpsertExistingRow("A", "", null));
        assertFalse(ProspectorMiningLogPolicy.shouldWriteRunStartOnUpsertExistingRow("A", "4/1/2026 10:00:00", null));
    }

    @Test
    void shouldWriteRunStart_blankExisting_writes() {
        Instant st = Instant.parse("2026-04-02T15:19:04Z");
        assertTrue(ProspectorMiningLogPolicy.shouldWriteRunStartOnUpsertExistingRow("A", "", st));
        assertTrue(ProspectorMiningLogPolicy.shouldWriteRunStartOnUpsertExistingRow("A", "   ", st));
        assertTrue(ProspectorMiningLogPolicy.shouldWriteRunStartOnUpsertExistingRow("A", null, st));
    }

    @Test
    void shouldWriteRunStart_nonBlankExisting_preservesCanonicalStart() {
        Instant st = Instant.parse("2026-04-02T15:19:04Z");
        assertFalse(ProspectorMiningLogPolicy.shouldWriteRunStartOnUpsertExistingRow("A", "4/2/2026 15:19:04", st));
    }

    @Test
    void shouldWriteRunStart_shipNameInStartSlot_overwritesSoSheetCanHeal() {
        Instant st = Instant.parse("2026-04-02T15:19:04Z");
        assertTrue(ProspectorMiningLogPolicy.shouldWriteRunStartOnUpsertExistingRow("A", "lakonminer", st));
    }

    @Test
    void shouldWriteRunStart_dashPlaceholderExisting_writes() {
        Instant st = Instant.parse("2026-04-02T15:19:04Z");
        assertTrue(ProspectorMiningLogPolicy.shouldWriteRunStartOnUpsertExistingRow("A", "-", st));
        assertTrue(ProspectorMiningLogPolicy.shouldWriteRunStartOnUpsertExistingRow("A", "  -  ", st));

    }

    @Test
    void shouldWriteRunStart_nonA_asteroidNeverWrites() {
        Instant st = Instant.parse("2026-04-02T15:19:04Z");
        assertFalse(ProspectorMiningLogPolicy.shouldWriteRunStartOnUpsertExistingRow("D", "", st));
    }

    @Test
    void findCanonicalRunEndRow_prefersAsteroidAWithStart() {
        List<List<Object>> values = new ArrayList<>();
        values.add(Arrays.asList("Run", "Asteroid", "T", "Mat", "P", "Bef", "Af", "Act", "Core", "D", "Sys", "Body", "Cmdr", "Start", "End"));
        values.add(Arrays.asList(19, "B", "t", "m", 0, 0, 0, 0, "-", 0, "S", "B", "Villunus", "4/2/2026 15:00:00", ""));
        values.add(Arrays.asList(19, "A", "t", "m", 0, 0, 0, 0, "-", 0, "S", "B", "Villunus", "4/2/2026 15:19:04", ""));
        int idx = ProspectorMiningLogPolicy.findDataRowIndexForCanonicalRunEnd(values, 19, "Villunus");
        assertEquals(2, idx);
    }

    @Test
    void findCanonicalRunEndRow_fallsBackToFirstRowWithStart_whenNoA() {
        List<List<Object>> values = new ArrayList<>();
        values.add(Arrays.asList("Run", "Asteroid", "T", "Mat", "P", "Bef", "Af", "Act", "Core", "D", "Sys", "Body", "Cmdr", "Start", "End"));
        values.add(Arrays.asList(2, "X", "t", "m", 0, 0, 0, 0, "-", 0, "S", "B", "Z", "4/1/2026 10:00:00", ""));
        int idx = ProspectorMiningLogPolicy.findDataRowIndexForCanonicalRunEnd(values, 2, "Z");
        assertEquals(1, idx);
    }

    @Test
    void findCanonicalRunEndRow_noMatch_returnsMinusOne() {
        List<List<Object>> values = new ArrayList<>();
        values.add(Arrays.asList("Run", "Asteroid", "T", "Mat", "P", "Bef", "Af", "Act", "Core", "D", "Sys", "Body", "Cmdr", "Start", "End"));
        values.add(Arrays.asList(9, "A", "t", "m", 0, 0, 0, 0, "-", 0, "S", "B", "Other", "4/1/2026 10:00:00", ""));
        assertEquals(-1, ProspectorMiningLogPolicy.findDataRowIndexForCanonicalRunEnd(values, 9, "Villunus"));
    }

    @Test
    void findCanonicalRunEndRow_nullValues_returnsMinusOne() {
        assertEquals(-1, ProspectorMiningLogPolicy.findDataRowIndexForCanonicalRunEnd(null, 1, "Villunus"));
    }

    @Test
    void findCanonicalRunEndRow_skipsRowsShorterThan13Columns() {
        List<List<Object>> values = new ArrayList<>();
        values.add(Arrays.asList("Run", "Asteroid", "T", "Mat", "P", "Bef", "Af", "Act", "Core", "D", "Sys", "Body", "Cmdr", "Start", "End"));
        values.add(Arrays.asList(3, "A", "t", "m", 0, 0, 0, 0, "-", 0, "S", "B"));
        values.add(Arrays.asList(3, "A", "t", "m", 0, 0, 0, 0, "-", 0, "S", "B", "Villunus", "4/1/2026 10:00:00", ""));
        assertEquals(2, ProspectorMiningLogPolicy.findDataRowIndexForCanonicalRunEnd(values, 3, "Villunus"));
    }

    @Test
    void findCanonicalRunEndRow_asteroidACaseInsensitive() {
        List<List<Object>> values = new ArrayList<>();
        values.add(Arrays.asList("Run", "Asteroid", "T", "Mat", "P", "Bef", "Af", "Act", "Core", "D", "Sys", "Body", "Cmdr", "Start", "End"));
        values.add(Arrays.asList(5, "a", "t", "m", 0, 0, 0, 0, "-", 0, "S", "B", "Z", "4/1/2026 11:00:00", ""));
        assertEquals(1, ProspectorMiningLogPolicy.findDataRowIndexForCanonicalRunEnd(values, 5, "Z"));
    }

    @Test
    void findCanonicalRunEndRow_requiresNonBlankStartCell() {
        List<List<Object>> values = new ArrayList<>();
        values.add(Arrays.asList("Run", "Asteroid", "T", "Mat", "P", "Bef", "Af", "Act", "Core", "D", "Sys", "Body", "Cmdr", "Start", "End"));
        values.add(Arrays.asList(6, "A", "t", "m", 0, 0, 0, 0, "-", 0, "S", "B", "Z", "", ""));
        assertEquals(1, ProspectorMiningLogPolicy.findDataRowIndexForCanonicalRunEnd(values, 6, "Z"));
    }

    @Test
    void findCanonicalRunEndRow_shipNameInStartSlot_skipped() {
        List<List<Object>> values = new ArrayList<>();
        values.add(Arrays.asList("Run", "Asteroid", "T", "Mat", "P", "Bef", "Af", "Act", "Core", "D", "Sys", "Body", "Cmdr", "Ship", "Start", "End"));
        values.add(Arrays.asList(7, "A", "t", "m", 0, 0, 0, 0, "-", 0, "S", "B", "Z", "lak", "lakonminer", ""));
        assertEquals(1, ProspectorMiningLogPolicy.findDataRowIndexForCanonicalRunEnd(values, 7, "Z"));
    }

    /** Legacy "-" in Start time is not a real run anchor; do not pick that row for run end. */
    @Test
    void findCanonicalRunEndRow_dashStartCell_skipped() {
        List<List<Object>> values = new ArrayList<>();
        values.add(Arrays.asList("Run", "Asteroid", "T", "Mat", "P", "Bef", "Af", "Act", "Core", "D", "Sys", "Body", "Cmdr", "Start", "End"));
        values.add(Arrays.asList(7, "A", "t", "m", 0, 0, 0, 0, "-", 0, "S", "B", "Z", "-", ""));
        assertEquals(1, ProspectorMiningLogPolicy.findDataRowIndexForCanonicalRunEnd(values, 7, "Z"));
    }
}
