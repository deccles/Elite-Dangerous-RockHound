package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.dce.ed.TestEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalSystemVisitHistoryTest {

    static {
        TestEnvironment.ensureTestIsolation();
    }

    @TempDir
    Path tempDir;

    @Test
    void loadTransitionSystemNames_dedupesConsecutiveSameSystem() throws IOException {
        writeJournal(1,
                eventLine("Location", "2026-03-27T12:00:00Z",
                        "\"StarSystem\":\"Alpha\",\"SystemAddress\":1,\"Docked\":false"),
                eventLine("FSDJump", "2026-03-27T12:01:00Z",
                        "\"StarSystem\":\"Alpha\",\"SystemAddress\":1,\"StarPos\":[0.0,0.0,0.0]"),
                eventLine("FSDJump", "2026-03-27T12:02:00Z",
                        "\"StarSystem\":\"Beta\",\"SystemAddress\":2,\"StarPos\":[1.0,0.0,0.0]"),
                eventLine("CarrierJump", "2026-03-27T12:03:00Z",
                        "\"Docked\":true,\"StarSystem\":\"Gamma\",\"SystemAddress\":3,\"StarPos\":[2.0,0.0,0.0]"));

        List<String> names = JournalSystemVisitHistory.loadTransitionSystemNames(tempDir);

        assertEquals(List.of("Alpha", "Beta", "Gamma"), names);
    }

    @Test
    void loadViewableTransitionSystemNames_skipsUnscannedSystems() throws IOException {
        writeJournal(1,
                eventLine("Location", "2026-03-27T12:00:00Z",
                        "\"StarSystem\":\"Alpha\",\"SystemAddress\":1,\"Docked\":false"),
                eventLine("FSDJump", "2026-03-27T12:01:00Z",
                        "\"StarSystem\":\"Beta\",\"SystemAddress\":2,\"StarPos\":[1.0,0.0,0.0]"),
                eventLine("Scan", "2026-03-27T12:02:00Z",
                        "\"StarSystem\":\"Beta\",\"SystemAddress\":2,\"BodyName\":\"Beta A\",\"BodyID\":0,"
                                + "\"StarType\":\"M\",\"DistanceFromArrivalLS\":0.0"),
                eventLine("FSDJump", "2026-03-27T12:03:00Z",
                        "\"StarSystem\":\"Gamma\",\"SystemAddress\":3,\"StarPos\":[2.0,0.0,0.0]"));

        List<String> names = JournalSystemVisitHistory.loadViewableTransitionSystemNames(tempDir);

        assertEquals(List.of("Beta"), names);
    }

    private void writeJournal(int seq, String... lines) throws IOException {
        String filename = String.format("Journal.2026-03-27T1200%02d.01.log", Integer.valueOf(seq));
        Path file = tempDir.resolve(filename);
        Files.writeString(file, String.join(System.lineSeparator(), lines) + System.lineSeparator(),
                StandardCharsets.UTF_8);
    }

    private static String eventLine(String event, String timestamp, String extraFields) {
        String suffix = (extraFields == null || extraFields.isBlank()) ? "" : "," + extraFields;
        return "{\"timestamp\":\"" + timestamp + "\",\"event\":\"" + event + "\"" + suffix + "}";
    }
}
