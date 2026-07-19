package org.dce.ed.logreader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FleetCarrierPresenceJournalBootstrapTest {

    @Test
    void replay_loginLocationDockedAtCarrier_endsAboard(@TempDir Path dir) throws Exception {
        writeJournal(dir, "Journal.2026-07-18T120000.01.log",
                "{ \"timestamp\":\"2026-07-18T12:00:00Z\", \"event\":\"Location\", \"Docked\":true, "
                        + "\"StationName\":\"JFZ-93T\", \"StationType\":\"FleetCarrier\", "
                        + "\"StarSystem\":\"Sifeae EX-Z c1-3\", \"SystemAddress\":913117352722, "
                        + "\"Body\":\"Sifeae EX-Z c1-3 A\", \"BodyID\":1, \"BodyType\":\"Star\" }");

        FleetCarrierCommanderPresence presence = new FleetCarrierCommanderPresence();
        FleetCarrierPresenceJournalBootstrap.replayInto(presence, dir);

        assertTrue(presence.isAboard());
    }

    @Test
    void replay_dockedThenUndockedFromCarrier_endsOffCarrier(@TempDir Path dir) throws Exception {
        writeJournal(dir, "Journal.2026-07-18T120000.01.log",
                "{ \"timestamp\":\"2026-07-18T12:00:00Z\", \"event\":\"Docked\", "
                        + "\"StationName\":\"JFZ-93T\", \"StationType\":\"FleetCarrier\", "
                        + "\"StarSystem\":\"Sifeae EX-Z c1-3\", \"SystemAddress\":913117352722 }",
                "{ \"timestamp\":\"2026-07-18T12:05:00Z\", \"event\":\"Undocked\", "
                        + "\"StationName\":\"JFZ-93T\", \"StationType\":\"FleetCarrier\" }");

        FleetCarrierCommanderPresence presence = new FleetCarrierCommanderPresence();
        FleetCarrierPresenceJournalBootstrap.replayInto(presence, dir);

        assertFalse(presence.isAboard());
    }

    @Test
    void replay_dockedAtCarrierInEarlierJournalFile_endsAboard(@TempDir Path dir) throws Exception {
        writeJournal(dir, "Journal.2026-07-17T220000.01.log",
                "{ \"timestamp\":\"2026-07-17T22:00:00Z\", \"event\":\"Docked\", "
                        + "\"StationName\":\"JFZ-93T\", \"StationType\":\"FleetCarrier\", "
                        + "\"StarSystem\":\"Sifeae EX-Z c1-3\", \"SystemAddress\":913117352722 }");
        writeJournal(dir, "Journal.2026-07-18T120000.01.log",
                "{ \"timestamp\":\"2026-07-18T12:00:00Z\", \"event\":\"Fileheader\", \"part\":1 }");

        FleetCarrierCommanderPresence presence = new FleetCarrierCommanderPresence();
        FleetCarrierPresenceJournalBootstrap.replayInto(presence, dir);

        assertTrue(presence.isAboard());
    }

    private static void writeJournal(Path dir, String fileName, String... lines) throws Exception {
        Files.writeString(dir.resolve(fileName), String.join("\n", lines) + "\n");
    }
}
