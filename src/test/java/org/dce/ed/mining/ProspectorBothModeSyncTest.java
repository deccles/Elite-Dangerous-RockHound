package org.dce.ed.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Focused tests for {@link ProspectorBothModeSync} merge behavior. The actual {@code syncCommander} entrypoint
 * requires a {@link GoogleSheetsBackend} (network), so these tests target the package-private merge helpers,
 * which are the interesting logic — the I/O layer is just two upsert calls covered by the per-backend tests.
 */
class ProspectorBothModeSyncTest {

    private static ProspectorLogRow row(int run, String asteroid, String material, String commander,
            Instant ts, String core, int duds, String comments, Instant runStart, Instant runEnd) {
        return new ProspectorLogRow(run, asteroid, "Sol > Earth", ts, material,
                10.0, 0.0, 1.0, 1.0, commander, "anaconda", core, duds, runStart, runEnd, comments);
    }

    @Test
    void mergeForSync_csvOnlyAndSheetsOnly_unionedByKey() {
        Instant t = Instant.parse("2026-04-02T15:00:00Z");
        ProspectorLogRow csvOnly = row(1, "A", "Painite", "Cmdr", t, "Painite", 2, "csv comment", t, null);
        ProspectorLogRow sheetsOnly = row(2, "B", "Tritium", "Cmdr", t, "", 0, "", null, null);
        List<ProspectorLogRow> merged = ProspectorBothModeSync.mergeForSync(
                List.of(csvOnly), List.of(sheetsOnly));
        assertEquals(2, merged.size());

        Map<String, ProspectorLogRow> byMaterial = indexByMaterial(merged);
        assertNotNull(byMaterial.get("Painite"));
        assertNotNull(byMaterial.get("Tritium"));
    }

    @Test
    void mergeForSyncRow_keepsMaxDudsAndNonBlankCoreAndComments() {
        Instant t = Instant.parse("2026-04-02T15:00:00Z");
        ProspectorLogRow x = row(1, "A", "Painite", "Cmdr", t, "Painite", 4, "x rocks", t, null);
        ProspectorLogRow y = row(1, "A", "Painite", "Cmdr", t.plusSeconds(60), "", 1, "", null, null);

        ProspectorLogRow merged = ProspectorBothModeSync.mergeForSyncRow(x, y);
        assertEquals("Painite", merged.getCoreType());
        assertEquals(4, merged.getDuds());
        assertEquals("x rocks", merged.getComments());
        assertEquals(t, merged.getRunStartTime());
    }

    @Test
    void mergeForSyncRow_runEndPrefersNonNull() {
        Instant t = Instant.parse("2026-04-02T15:00:00Z");
        Instant end = Instant.parse("2026-04-02T16:00:00Z");
        ProspectorLogRow x = row(1, "A", "Painite", "Cmdr", t, "", 0, "", t, null);
        ProspectorLogRow y = row(1, "A", "Painite", "Cmdr", t, "", 0, "", null, end);

        ProspectorLogRow merged = ProspectorBothModeSync.mergeForSyncRow(x, y);
        assertEquals(end, merged.getRunEndTime());
        assertEquals(t, merged.getRunStartTime());
    }

    @Test
    void mergeForSync_conflictingKeys_appliesSharedMergeRulesNotNewestOnly() {
        Instant earlier = Instant.parse("2026-04-02T14:00:00Z");
        Instant later = Instant.parse("2026-04-02T15:00:00Z");
        // Earlier has core/comments/duds, later has none — newest-by-timestamp would lose data without merge rules.
        ProspectorLogRow earlyRich = row(1, "A", "Painite", "Cmdr", earlier, "Painite", 5, "good", earlier, null);
        ProspectorLogRow laterPoor = row(1, "A", "Painite", "Cmdr", later, "", 0, "", null, null);

        List<ProspectorLogRow> merged = ProspectorBothModeSync.mergeForSync(List.of(earlyRich), List.of(laterPoor));
        assertEquals(1, merged.size());
        ProspectorLogRow r = merged.get(0);
        assertEquals("Painite", r.getCoreType(), "non-blank core must be preserved across sync");
        assertEquals(5, r.getDuds(), "max duds must be preserved across sync");
        assertEquals("good", r.getComments(), "non-blank comments must be preserved across sync");
    }

    @Test
    void mergeForSync_keysAreCaseInsensitiveOnAsteroidMaterialCommander() {
        Instant t = Instant.parse("2026-04-02T15:00:00Z");
        ProspectorLogRow a = row(1, "a", "painite", "Cmdr", t, "Painite", 1, "", t, null);
        ProspectorLogRow b = row(1, "A", "PAINITE", "cmdr", t, "", 3, "x", t, null);

        List<ProspectorLogRow> merged = ProspectorBothModeSync.mergeForSync(List.of(a), List.of(b));
        assertEquals(1, merged.size(), "key must collapse case differences");
        ProspectorLogRow r = merged.get(0);
        assertEquals(3, r.getDuds());
        assertEquals("x", r.getComments());
    }

    @Test
    void mergeForSync_isolatesOtherCommandersData() {
        Instant t = Instant.parse("2026-04-02T15:00:00Z");
        ProspectorLogRow myRow = row(1, "A", "Painite", "Me", t, "", 0, "mine", t, null);
        ProspectorLogRow otherRow = row(1, "A", "Painite", "Other", t, "", 0, "theirs", t, null);

        List<ProspectorLogRow> merged = ProspectorBothModeSync.mergeForSync(List.of(myRow), List.of(otherRow));
        assertEquals(2, merged.size(), "different commanders are different keys");
    }

    @Test
    void syncCommander_blankCommander_returnsFailure() {
        // Passing real backends here would require a network/file fixture; we only need to assert the early-return.
        ProspectorWriteResult r = ProspectorBothModeSync.syncCommander(
                new GoogleSheetsBackend(""), null, "");
        assertTrue(r != null && !r.isOk());
    }

    private static Map<String, ProspectorLogRow> indexByMaterial(List<ProspectorLogRow> rows) {
        Map<String, ProspectorLogRow> out = new HashMap<>();
        for (ProspectorLogRow r : rows) {
            out.put(r.getMaterial(), r);
        }
        return out;
    }
}
