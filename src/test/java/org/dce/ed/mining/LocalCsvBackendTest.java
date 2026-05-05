package org.dce.ed.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link LocalCsvBackend}: 16-column round-trip with comments, per-commander file routing,
 * upsert merge rules (shared with Google Sheets via {@link ProspectorRowMergeRules}), commander-scoped loads,
 * and run-end placement matching {@link ProspectorMiningLogPolicy}.
 *
 * <p>The backend has two modes: a base directory ({@link LocalCsvBackend#LocalCsvBackend(java.nio.file.Path)} with
 * a path that is a directory or does not yet exist) and a single-file legacy mode (path points at a regular file).
 * Production code uses directory mode; legacy single-file is exercised by the run-end test for parity with the
 * Google Sheets canonical-row contract.</p>
 */
class LocalCsvBackendTest {

    @Test
    void appendThenLoad_perCommanderFile_roundTripsRowsAndComments(@TempDir Path dir) throws Exception {
        LocalCsvBackend backend = new LocalCsvBackend(dir);
        Instant ts = Instant.parse("2026-02-16T14:30:00Z");
        ProspectorLogRow row = new ProspectorLogRow(
                1, "A", "Sol > Earth", ts, "Tritium",
                24.5, 10.0, 12.5, 2.5,
                "Commander One", "anaconda", "Painite", 2, ts, null,
                "first \"core\", with comma\nand newline");
        backend.appendRows(List.of(row));

        // File created with the canonical CMDR <name>.csv name.
        Path expected = dir.resolve("CMDR Commander One.csv");
        assertTrue(Files.exists(expected), "per-commander CSV must be created");

        ProspectorLoadResult loaded = backend.loadRowsWithStatus();
        assertEquals(ProspectorLoadResult.Status.OK, loaded.getStatus());
        assertEquals(1, loaded.getRows().size());
        ProspectorLogRow r = loaded.getRows().get(0);
        assertEquals(1, r.getRun());
        assertEquals("A", r.getAsteroidId());
        assertEquals("Tritium", r.getMaterial());
        assertEquals("Painite", r.getCoreType());
        assertEquals(2, r.getDuds());
        assertEquals("anaconda", r.getShipType());
        assertEquals("first \"core\", with comma\nand newline", r.getComments());
    }

    @Test
    void appendRows_routesByCommander_intoSeparateFiles(@TempDir Path dir) throws Exception {
        LocalCsvBackend backend = new LocalCsvBackend(dir);
        Instant ts = Instant.parse("2026-02-16T14:30:00Z");
        backend.appendRows(List.of(
                new ProspectorLogRow(1, "A", "S > B", ts, "Tritium", 1, 0, 1, 1, "Hadban",
                        "", "", 0, null, null, ""),
                new ProspectorLogRow(1, "A", "S > B", ts, "Painite", 1, 0, 1, 1, "Other",
                        "", "", 0, null, null, "")));
        assertTrue(Files.exists(dir.resolve("CMDR Hadban.csv")));
        assertTrue(Files.exists(dir.resolve("CMDR Other.csv")));
    }

    @Test
    void loadRows_unionAcrossPerCommanderFiles(@TempDir Path dir) throws Exception {
        LocalCsvBackend backend = new LocalCsvBackend(dir);
        Instant t1 = Instant.parse("2026-02-16T14:30:00Z");
        Instant t2 = Instant.parse("2026-02-16T14:31:00Z");
        backend.appendRows(List.of(
                new ProspectorLogRow(1, "A", "S > B", t1, "Tritium", 1, 0, 1, 1, "Hadban",
                        "", "", 0, null, null, ""),
                new ProspectorLogRow(1, "A", "S > B", t2, "Painite", 1, 0, 1, 1, "Other",
                        "", "", 0, null, null, "")));
        List<ProspectorLogRow> all = backend.loadRows();
        assertEquals(2, all.size());
    }

    @Test
    void loadRowsWithStatusForCommander_readsOnlyOneFile(@TempDir Path dir) throws Exception {
        LocalCsvBackend backend = new LocalCsvBackend(dir);
        Instant ts = Instant.parse("2026-02-16T14:30:00Z");
        backend.appendRows(List.of(
                new ProspectorLogRow(1, "A", "S", ts, "Tritium", 1, 0, 1, 1, "Hadban", "", "", 0, null, null, ""),
                new ProspectorLogRow(1, "A", "S", ts, "Painite", 1, 0, 1, 1, "Other", "", "", 0, null, null, "")));
        ProspectorLoadResult only = backend.loadRowsWithStatusForCommander("Hadban");
        assertEquals(ProspectorLoadResult.Status.OK, only.getStatus());
        assertEquals(1, only.getRows().size());
        assertEquals("Hadban", only.getRows().get(0).getCommanderName());
    }

    @Test
    void loadRowsWithStatus_emptyDir_returnsEmptySheet(@TempDir Path dir) {
        LocalCsvBackend backend = new LocalCsvBackend(dir);
        ProspectorLoadResult r = backend.loadRowsWithStatus();
        assertEquals(ProspectorLoadResult.Status.EMPTY_SHEET, r.getStatus());
        assertTrue(r.getRows().isEmpty());
    }

    @Test
    void loadRowsWithStatus_ignoresLegacyFlatFile(@TempDir Path dir) throws Exception {
        // Legacy ~/.edo/prospector_log.csv is intentionally ignored when in directory mode.
        Path legacy = dir.resolve("prospector_log.csv");
        Files.writeString(
                legacy,
                "run,asteroid,timestamp,material,percent,before amount,after amount,actual,core,body,duds,commander,ship,start time,end time\n"
                        + "1,A,2/16/2026 14:30:00,Tritium,10,0,1,1,-,Sol,0,Hadban,-,2/16/2026 14:30:00,\n",
                StandardCharsets.UTF_8);
        LocalCsvBackend backend = new LocalCsvBackend(dir);
        ProspectorLoadResult r = backend.loadRowsWithStatus();
        assertEquals(ProspectorLoadResult.Status.EMPTY_SHEET, r.getStatus());
    }

    @Test
    void upsertRows_mergesCoreCommentsDuds_doesNotOverwriteWithBlanks(@TempDir Path dir) throws Exception {
        LocalCsvBackend backend = new LocalCsvBackend(dir);
        Instant ts = Instant.parse("2026-02-16T14:30:00Z");

        // Initial row with core, comments, dud counter.
        backend.upsertRowsResult(List.of(new ProspectorLogRow(
                1, "A", "S > B", ts, "Painite", 50.0, 0, 1, 1, "C1",
                "anaconda", "Painite", 3, ts, null, "nice rock")));

        // Cargo-driven update with no core, no comments, dud counter back to 0 — must not overwrite.
        backend.upsertRowsResult(List.of(new ProspectorLogRow(
                1, "A", "S > B", ts, "Painite", 50.0, 0, 2, 2, "C1",
                "", "", 0, null, null, "")));

        ProspectorLogRow merged = backend.loadRowsWithStatusForCommander("C1").getRows().get(0);
        assertEquals("Painite", merged.getCoreType());
        assertEquals(3, merged.getDuds());
        assertEquals("nice rock", merged.getComments());
    }

    @Test
    void updateRunEndTimeResult_writesEndOnAsteroidA_singleFileMode(@TempDir Path dir) throws Exception {
        // Single-file legacy mode for back-compat with direct-file callers (e.g. tools).
        Path csv = dir.resolve("prospector.csv");
        String header = "run,asteroid,timestamp,material,percent,before amount,after amount,actual,core,body,duds,commander,ship,comments,start time,end time\n";
        String rA1 = "19,A,4/2/2026 15:19:04,Bromellite,10.00,0.00,1.00,1.00,-,Ring,0,Villunus,-,,4/2/2026 15:19:04,\n";
        String rA2 = "19,A,4/2/2026 15:19:10,Tritium,10.00,0.00,1.00,1.00,-,Ring,0,Villunus,-,,4/2/2026 15:19:04,\n";
        String rB = "19,B,4/2/2026 15:25:00,Bromellite,10.00,0.00,1.00,1.00,-,Ring,0,Villunus,-,,,\n";
        Files.writeString(csv, header + rA1 + rA2 + rB, StandardCharsets.UTF_8);

        LocalCsvBackend backend = new LocalCsvBackend(csv);
        Instant end = Instant.parse("2026-04-02T20:40:27Z");
        ProspectorWriteResult result = backend.updateRunEndTimeResult("Villunus", 19, end);
        assertNotNull(result);
        assertTrue(result.isOk(), "updateRunEndTimeResult must succeed");

        List<ProspectorLogRow> loaded = backend.loadRows();
        assertEquals(3, loaded.size());
        assertEquals(end, loaded.get(0).getRunEndTime());
        assertNull(loaded.get(1).getRunEndTime());
        assertNull(loaded.get(2).getRunEndTime());
    }

    @Test
    void displayName_andDebouncedRefreshDefaultsAreCorrect(@TempDir Path dir) {
        LocalCsvBackend backend = new LocalCsvBackend(dir);
        assertEquals("Local CSV", backend.displayName());
        assertEquals(false, backend.prefersDebouncedRefresh());
    }

    @Test
    void appendRows_legacy15ColFile_canStillBeRead(@TempDir Path dir) throws Exception {
        // A user upgrading from a previous build may keep around an existing CMDR <name>.csv with the old 15-col
        // header (no Comments). The reader must tolerate it without crashing.
        Path file = dir.resolve("CMDR Legacy.csv");
        Files.writeString(
                file,
                "run,asteroid,timestamp,material,percent,before amount,after amount,actual,core,body,duds,commander,ship,start time,end time\n"
                        + "1,A,2/16/2026 14:30:00,Tritium,10.00,0.00,1.00,1.00,-,Sol,0,Legacy,-,2/16/2026 14:30:00,\n",
                StandardCharsets.UTF_8);
        LocalCsvBackend backend = new LocalCsvBackend(dir);
        List<ProspectorLogRow> rows = backend.loadRows();
        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).getRun());
        assertEquals("Legacy", rows.get(0).getCommanderName());
    }
}
