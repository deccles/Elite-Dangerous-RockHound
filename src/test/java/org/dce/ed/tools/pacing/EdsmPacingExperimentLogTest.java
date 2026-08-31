package org.dce.ed.tools.pacing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EdsmPacingExperimentLogTest {

    @Test
    void defaultFileIsInTheWorkingDirectory() {
        Path file = EdsmPacingExperimentLog.defaultFile();
        assertEquals("edsm-pacing-experiment.log", file.getFileName().toString());
        assertEquals(Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize(),
                file.toAbsolutePath().normalize().getParent());
    }

    @Test
    void runHeaderStatesStartTimeAndBatchParams() {
        LocalDateTime started = LocalDateTime.of(2026, 8, 30, 0, 5, 12, 345_000_000);
        String header = EdsmPacingExperimentLog.formatRunHeader(started, List.of(
                new EdsmPacingExperimentSettings.BatchSpec(4, 2, 8, 0, 3),
                new EdsmPacingExperimentSettings.BatchSpec(18, 18, 0, 0, 1)),
                50, 4);
        assertTrue(header.contains("RUN STARTED  2026-08-30 00:05:12.345"));
        assertTrue(header.contains("systems=50  configuredBatches=2  expandedWaves=4"));
        assertTrue(header.contains("batch 1: count=4 concurrent=2 rest=8s delay=0ms repeat=3"));
        assertTrue(header.contains("batch 2: count=18 concurrent=18 rest=0s delay=0ms repeat=1"));
    }

    @Test
    void appendsRunsWithoutOverwritingEarlierText(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("edsm-pacing-experiment.log");
        EdsmPacingExperimentLog log = new EdsmPacingExperimentLog(file);
        LocalDateTime first = LocalDateTime.of(2026, 8, 30, 0, 5, 0);
        LocalDateTime second = LocalDateTime.of(2026, 8, 30, 0, 12, 0);
        List<EdsmPacingExperimentSettings.BatchSpec> batches = List.of(
                new EdsmPacingExperimentSettings.BatchSpec(4, 2, 8, 0, 1));

        log.startRun(first, batches, 50, 1);
        log.append("[batch 1] start count=4");
        log.endRun(first.plusSeconds(40), 40_000L, "unused=46");
        log.startRun(second, batches, 50, 1);
        log.append("[batch 1] start count=4");
        log.endRun(second.plusSeconds(12), 12_000L, "unused=46");

        String text = Files.readString(file, StandardCharsets.UTF_8);
        int firstStart = text.indexOf("RUN STARTED  2026-08-30 00:05:00.000");
        int secondStart = text.indexOf("RUN STARTED  2026-08-30 00:12:00.000");
        assertTrue(firstStart >= 0);
        assertTrue(secondStart > firstStart);
        assertTrue(text.contains("RUN ENDED    2026-08-30 00:05:40.000  elapsed=00:40  unused=46"));
        assertTrue(text.contains("RUN ENDED    2026-08-30 00:12:12.000  elapsed=00:12  unused=46"));
        assertEquals(2, text.split("RUN STARTED", -1).length - 1);
    }
}
