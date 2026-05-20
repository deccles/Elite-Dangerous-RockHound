package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.junit.jupiter.api.Test;

/**
 * Mining run end time is written on dock via {@link EliteOverlayTabbedPane}'s docked transition.
 */
class DockedMiningRunEndTest {

    @Test
    void journalDocked_afterProspecting_notifiesDockedListeners() throws Exception {
        try (MiningSheetPrefsTestGuard ignored = new MiningSheetPrefsTestGuard()) {
            EliteOverlayTabbedPane tabs = new EliteOverlayTabbedPane(() -> false);
            AtomicInteger dockedTrueCallbacks = new AtomicInteger();

            tabs.addDockedStateListener(docked -> {
                if (docked) {
                    dockedTrueCallbacks.incrementAndGet();
                }
            });

            EliteLogParser parser = new EliteLogParser();
            String prospectorJson = """
                    {"timestamp":"2026-03-25T10:15:30Z","event":"ProspectedAsteroid","Content":"High",
                     "Materials":[{"Name":"platinum","Proportion":20.0}]}
                    """;
            ProspectedAsteroidEvent prospector = (ProspectedAsteroidEvent) parser.parseRecord(prospectorJson);
            tabs.processJournalEvent(prospector);
            assertFalse(tabs.isCurrentlyDocked(), "Prospector implies in-space");

            EliteLogEvent docked = new EliteLogEvent.GenericEvent(
                    Instant.parse("2026-03-25T11:00:00Z"),
                    EliteEventType.DOCKED,
                    new com.google.gson.JsonObject());
            tabs.processJournalEvent(docked);

            assertTrue(tabs.isCurrentlyDocked());
            assertEquals(1, dockedTrueCallbacks.get(),
                    "Journal Docked must fire docked transition even before Status.json repeats docked=true");

            // Status Flags docked bit afterward must not double-close the run.
            StatusEvent statusDocked = (StatusEvent) parser.parseRecord("""
                    {"timestamp":"2026-03-25T11:00:01Z","event":"Status","Flags":1,"Flags2":0,"GuiFocus":0}
                    """);
            tabs.processJournalEvent(statusDocked);
            assertEquals(1, dockedTrueCallbacks.get(), "Repeated docked=true Status must not re-notify listeners");
        }
    }
}
