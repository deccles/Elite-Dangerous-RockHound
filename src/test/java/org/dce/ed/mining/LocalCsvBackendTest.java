package org.dce.ed.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link LocalCsvBackend}: write then read, missing file, empty file.
 */
class LocalCsvBackendTest {

    @Test
    void appendThenLoad_roundTripsRows(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("log.csv");
        LocalCsvBackend backend = new LocalCsvBackend(csv);
        Instant ts = Instant.parse("2026-02-16T14:30:00Z");
        List<ProspectorLogRow> rows = List.of(
            new ProspectorLogRow(1, "Sol > Earth", ts, "Tritium", 24.5, 10.0, 12.5, 2.5, "Commander One")
        );
        backend.appendRows(rows);
        List<ProspectorLogRow> loaded = backend.loadRows();
         ProspectorLogRow r = loaded.get(0);
        assertEquals(1, r.getRun());
        assertEquals("Sol > Earth", r.getFullBodyName());
        assertEquals("Tritium", r.getMaterial());
        assertEquals(24.5, r.getPercent(), 1e-6);
        assertEquals(10.0, r.getBeforeAmount(), 1e-6);
        assertEquals(12.5, r.getAfterAmount(), 1e-6);
        assertEquals(2.5, r.getDifference(), 1e-6);
        assertEquals("Commander One", r.getCommanderName());
    }

    @Test
    void loadRows_missingFile_returnsEmpty(@TempDir Path dir) {
        LocalCsvBackend backend = new LocalCsvBackend(dir.resolve("nonexistent.csv"));
        List<ProspectorLogRow> loaded = backend.loadRows();
        assertTrue(loaded.isEmpty());
    }

    @Test
    void loadRows_emptyFile_returnsEmpty(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("empty.csv");
        Files.writeString(csv, "run,timestamp,material,percent,before amount,after amount,difference,body,commander\n");
        LocalCsvBackend backend = new LocalCsvBackend(csv);
        List<ProspectorLogRow> loaded = backend.loadRows();
        assertTrue(loaded.isEmpty());
    }

    @Test
    void loadRows_legacy7Column_infersRunFromGaps(@TempDir Path dir) throws Exception {
        Path csv = dir.resolve("legacy.csv");
        // Legacy: no "run"/"body" header; 7 columns: timestamp,material,percent,before,after,difference,email
        String content = "2/16/2026 14:30:00,Tritium,24.5,10.0,12.5,2.5,cmdr1@ex.com\n"
            + "2/16/2026 14:35:00,Platinum,10.0,0.0,1.0,1.0,cmdr1@ex.com\n"
            + "2/16/2026 15:00:00,Tritium,20.0,1.0,2.0,1.0,cmdr1@ex.com\n"; // >10 min gap -> run 2
        Files.writeString(csv, content, StandardCharsets.UTF_8);
        LocalCsvBackend backend = new LocalCsvBackend(csv);
        List<ProspectorLogRow> loaded = backend.loadRows();
        assertEquals(3, loaded.size());
        assertEquals(1, loaded.get(0).getRun());
        assertEquals(1, loaded.get(1).getRun());
        assertEquals(2, loaded.get(2).getRun());
        assertEquals("", loaded.get(0).getFullBodyName());
        assertEquals("cmdr1@ex.com", loaded.get(0).getCommanderName());
    }
}
