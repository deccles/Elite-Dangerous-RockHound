package org.dce.ed.logreader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalImportCursorTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTrip_tailPositionWithFileAndOffset() throws Exception {
        Instant instant = Instant.parse("2026-06-05T19:48:18Z");
        JournalImportCursor.write(tempDir,
                new JournalImportCursor.TailPosition(instant, "Journal.2026-06-05T133231.01.log", 12840L));

        JournalImportCursor.TailPosition read = JournalImportCursor.readTailPosition(tempDir);
        assertNotNull(read);
        assertEquals(instant, read.instant);
        assertEquals("Journal.2026-06-05T133231.01.log", read.journalFileName);
        assertEquals(12840L, read.byteOffset);
        assertTrue(read.matchesFile(tempDir.resolve("Journal.2026-06-05T133231.01.log")));
    }

    @Test
    void read_legacyInstantOnlyCursor() throws Exception {
        Instant instant = Instant.parse("2026-06-05T19:48:18Z");
        Files.writeString(JournalImportCursor.getCursorFile(tempDir), instant.toString());

        JournalImportCursor.TailPosition read = JournalImportCursor.readTailPosition(tempDir);
        assertNotNull(read);
        assertEquals(instant, read.instant);
        assertNull(read.journalFileName);
        assertEquals(0L, read.byteOffset);
        assertEquals(instant, JournalImportCursor.read(tempDir));
    }
}
