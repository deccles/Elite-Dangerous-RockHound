package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import org.dce.ed.logreader.EliteLogParser;
import org.junit.jupiter.api.Test;

class EngineeringJournalRefreshThreadingTest {

    @Test
    void backgroundJournalEventMutatesEngineeringStateOnEdt() throws Exception {
        EngineeringTabPanel panel = new EngineeringTabPanel(() -> false);
        CountDownLatch changed = new CountDownLatch(1);
        AtomicBoolean changedOnEdt = new AtomicBoolean();
        panel.getInventoryTracker().setChangeCallback(() -> {
            changedOnEdt.set(SwingUtilities.isEventDispatchThread());
            changed.countDown();
        });
        var event = new EliteLogParser().parseRecord("""
                {"timestamp":"2026-08-19T21:00:00Z","event":"MaterialCollected",
                 "Category":"Manufactured","Name":"mechanicalcomponents","Count":1}
                """);

        Thread journalThread = new Thread(() -> panel.handleLogEvent(event), "test-journal");
        journalThread.start();
        journalThread.join();

        assertTrue(changed.await(5, TimeUnit.SECONDS));
        assertTrue(changedOnEdt.get());
    }
}
