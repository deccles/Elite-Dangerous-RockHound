package org.dce.ed.logreader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OwnedFleetCarrierJournalBootstrapTest {

    @Test
    void replayFromSampleJournal_learnsOwnedCarrierIdAndLocation() throws Exception {
        Path journal = Path.of("Journal.2025-12-01T225133.01.log").toAbsolutePath().normalize();
        if (!java.nio.file.Files.isRegularFile(journal)) {
            return;
        }
        OwnedFleetCarrierTracker tracker = new OwnedFleetCarrierTracker();
        OwnedFleetCarrierJournalBootstrap.replayInto(tracker, journal.getParent());

        assertEquals(3714348544L, tracker.getOwnedCarrierId());
        assertEquals("Ploea Eurl TH-N c22-2", tracker.getOwnedSystemName());
        assertEquals(638709240514L, tracker.getOwnedSystemAddress());
        assertTrue(tracker.hasOwnedCarrierLocation());
    }
}
