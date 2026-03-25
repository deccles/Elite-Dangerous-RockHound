package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import javax.swing.SwingUtilities;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent.MaterialProportion;
import org.junit.jupiter.api.Test;

class ProspectorEventFlowThreadingTest {

    @Test
    void prospectedAsteroidFlow_fromNonEdt_updatesMiningOnEdt() throws Exception {
        EliteOverlayTabbedPane tabs = new EliteOverlayTabbedPane(() -> false);
        MiningTabPanel mining = tabs.getMiningTabPanel();

        assertTrue(!mining.wasLastProspectorUpdateOnEdtForTests(), "Sanity: last flag should start false");

        EliteLogParser parser = new EliteLogParser();
        String json = """
                {"timestamp":"2026-03-25T10:15:30Z","event":"ProspectedAsteroid","Content":"High",
                 "Materials":[{"Name":"platinum","Proportion":20.0}]}
                """;
        ProspectedAsteroidEvent event = (ProspectedAsteroidEvent) parser.parseRecord(json);

        Thread worker = new Thread(() -> tabs.processJournalEvent(event), "test-prospector-worker");
        worker.start();
        worker.join();

        long deadlineMs = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadlineMs) {
            // Mining update should happen via SwingUtilities.invokeLater now.
            if (mining.wasLastProspectorUpdateOnEdtForTests() && mining.getProspectorTableRowCount() > 0) {
                break;
            }
            Thread.sleep(20);
        }

        // Ensure Swing-thread correctness and functional update.
        assertTrue(mining.wasLastProspectorUpdateOnEdtForTests(), "updateFromProspector must execute on the EDT");
        assertTrue(mining.getProspectorTableRowCount() > 0, "Prospector table should populate");

        SwingUtilities.invokeAndWait(() -> {
        });
    }
}

