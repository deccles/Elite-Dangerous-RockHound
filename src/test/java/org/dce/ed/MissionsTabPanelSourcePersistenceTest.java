package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.MissionAcceptedEvent;
import org.junit.jupiter.api.Test;

class MissionsTabPanelSourcePersistenceTest {

    @Test
    void choosingSource_requestsImmediatePersistence() {
        MissionsTabPanel panel = new MissionsTabPanel(() -> false, () -> false, () -> "Sol", () -> null);
        MissionAcceptedEvent accepted = (MissionAcceptedEvent) new EliteLogParser().parseRecord(
                "{\"timestamp\":\"2026-08-12T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":42,\"Name\":\"Mission_Collect_Industrial\","
                + "\"Commodity_Localised\":\"Gold\",\"Count\":50,"
                + "\"DestinationSystem\":\"A\",\"DestinationStation\":\"B\"}");
        panel.getTracker().applyEvent(accepted);
        AtomicInteger immediateSaves = new AtomicInteger();
        panel.setImmediateSessionStateChangeCallback(immediateSaves::incrementAndGet);

        assertTrue(panel.applySourcedFromSelection(42L, "Sol", "Galileo"));
        assertEquals(1, immediateSaves.get());
        assertEquals("Sol", panel.getTracker().findById(42L).getSourcedFromSystem());
        assertEquals("Galileo", panel.getTracker().findById(42L).getSourcedFromStation());
    }
}
