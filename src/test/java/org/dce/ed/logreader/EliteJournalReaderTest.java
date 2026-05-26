package org.dce.ed.logreader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.dce.ed.TestEnvironment;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EliteJournalReaderTest {

    static {
        TestEnvironment.ensureTestIsolation();
    }

    @TempDir
    Path tempDir;

    @Test
    void findMostRecentSystemTransitionEvent_expandsPastInitialWindow() throws Exception {
        // Last 4 files contain no transition events; the newest transition is in the 5th.
        writeJournal(1, eventLine("ReceiveText", "2026-03-27T12:01:00Z", "\"Message\":\"a\""));
        writeJournal(2, eventLine("ReceiveText", "2026-03-27T12:02:00Z", "\"Message\":\"b\""));
        writeJournal(3, eventLine("ReceiveText", "2026-03-27T12:03:00Z", "\"Message\":\"c\""));
        writeJournal(4, eventLine("ReceiveText", "2026-03-27T12:04:00Z", "\"Message\":\"d\""));
        writeJournal(5, eventLine("FSDJump", "2026-03-27T12:05:00Z",
                "\"StarSystem\":\"New System\",\"SystemAddress\":12345,\"StarPos\":[1.0,2.0,3.0]"));

        EliteJournalReader reader = new EliteJournalReader(tempDir);
        Instant cutoff = Instant.parse("2026-03-27T11:00:00Z");
        EliteLogEvent event = reader.findMostRecentSystemTransitionEvent(cutoff);

        assertNotNull(event);
        assertInstanceOf(FsdJumpEvent.class, event);
        FsdJumpEvent jump = (FsdJumpEvent) event;
        assertEquals("New System", jump.getStarSystem());
        assertEquals(12345L, jump.getSystemAddress());
    }

    @Test
    void readEventsSince_includesEventsAtSameInstantAsCursor() throws Exception {
        writeJournal(1,
                eventLine("ReceiveText", "2026-03-27T12:04:00Z", "\"Message\":\"before\""),
                eventLine("FSDJump", "2026-03-27T12:05:00Z",
                        "\"StarSystem\":\"At Cursor\",\"SystemAddress\":999,\"StarPos\":[1.0,2.0,3.0]"),
                eventLine("ReceiveText", "2026-03-27T12:05:00Z", "\"Message\":\"same second\""));

        EliteJournalReader reader = new EliteJournalReader(tempDir);
        Instant cursor = Instant.parse("2026-03-27T12:05:00Z");
        List<EliteLogEvent> events = reader.readEventsSince(cursor);

        assertEquals(2, events.size());
        assertInstanceOf(FsdJumpEvent.class, events.get(0));
        assertEquals("At Cursor", ((FsdJumpEvent) events.get(0)).getStarSystem());
    }

    @Test
    void findMostRecentSystemTransitionEvent_returnsCarrierJumpWhenNewest() throws Exception {
        writeJournal(1, eventLine("FSDJump", "2026-03-27T12:01:00Z",
                "\"StarSystem\":\"Before\",\"SystemAddress\":111,\"StarPos\":[0.0,0.0,0.0]"));
        writeJournal(2, eventLine("CarrierJump", "2026-03-27T12:02:00Z",
                "\"Docked\":true,\"StarSystem\":\"Carrier Dest\",\"SystemAddress\":222,\"StarPos\":[9.0,8.0,7.0]"));

        EliteJournalReader reader = new EliteJournalReader(tempDir);
        EliteLogEvent event = reader.findMostRecentSystemTransitionEvent(Instant.parse("2026-03-27T11:30:00Z"));

        assertNotNull(event);
        assertInstanceOf(CarrierJumpEvent.class, event);
        CarrierJumpEvent jump = (CarrierJumpEvent) event;
        assertEquals("Carrier Dest", jump.getStarSystem());
        assertEquals(222L, jump.getSystemAddress());
    }

    @Test
    void readAllEvents_skipsTransientInvalidStatusJsonAfterRetry() throws Exception {
        writeJournal(1, eventLine("ReceiveText", "2026-03-27T12:01:00Z", "\"Message\":\"journal event\""));
        Files.writeString(tempDir.resolve("Status.json"), "null", StandardCharsets.UTF_8);

        EliteJournalReader reader = new EliteJournalReader(tempDir);
        List<EliteLogEvent> events = reader.readAllEvents();

        assertEquals(1, events.size());
        assertEquals(EliteEventType.RECEIVE_TEXT, events.get(0).getType());
    }

    private void writeJournal(int seq, String... lines) throws IOException {
        String filename = String.format("Journal.2026-03-27T1200%02d.01.log", Integer.valueOf(seq));
        Path file = tempDir.resolve(filename);
        Files.writeString(file, String.join(System.lineSeparator(), lines) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static String eventLine(String event, String timestamp, String extraFields) {
        String suffix = (extraFields == null || extraFields.isBlank()) ? "" : "," + extraFields;
        return "{\"timestamp\":\"" + timestamp + "\",\"event\":\"" + event + "\"" + suffix + "}";
    }
}
