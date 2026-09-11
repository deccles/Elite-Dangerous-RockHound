package org.dce.ed.logreader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatusHistoryLogTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    @AfterEach
    void resetGuard() {
        StatusHistoryLog.resetDuplicateGuardForTests();
    }

    @Test
    void appendWritesOneJsonObjectPerLine() throws Exception {
        String first = "{ \"timestamp\":\"2026-09-10T17:02:00Z\", \"event\":\"Status\", \"GuiFocus\":1 }\n";
        String second = "{ \"timestamp\":\"2026-09-10T17:02:01Z\", \"event\":\"Status\", \"GuiFocus\":0 }";

        StatusHistoryLog.append(tempDir, first);
        StatusHistoryLog.append(tempDir, second);

        Path file = StatusHistoryLog.fileIn(tempDir);
        assertTrue(Files.isRegularFile(file));
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        assertEquals(StatusHistoryLog.toSingleLine(first), lines.get(0));
        assertEquals(StatusHistoryLog.toSingleLine(second), lines.get(1));
    }

    @Test
    void consecutiveDuplicateLineIsSkipped() throws Exception {
        String json = "{ \"timestamp\":\"2026-09-10T17:02:00Z\", \"event\":\"Status\", \"GuiFocus\":1 }";
        StatusHistoryLog.append(tempDir, json);
        StatusHistoryLog.append(tempDir, json);

        List<String> lines = Files.readAllLines(StatusHistoryLog.fileIn(tempDir), StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
    }

    @Test
    void toSingleLineStripsNewlines() {
        assertEquals("{ \"GuiFocus\":1 }", StatusHistoryLog.toSingleLine("{\n \"GuiFocus\":1 \n}"));
        assertEquals("", StatusHistoryLog.toSingleLine("  \n  "));
    }

    @Test
    void defaultFileIsUnderEdoLogsNotEliteJournalFolder() {
        Path file = StatusHistoryLog.defaultFile().toAbsolutePath().normalize();
        assertTrue(file.endsWith(Path.of(".edo", "logs", StatusHistoryLog.FILE_NAME)));
        assertEquals(StatusHistoryLog.FILE_NAME, file.getFileName().toString());
    }
}
