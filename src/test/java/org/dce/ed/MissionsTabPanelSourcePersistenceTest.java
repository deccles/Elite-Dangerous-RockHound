package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JTable;

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

    @Test
    void assignedSourceShowsClearActionAndClearingPersistsImmediately() {
        MissionsTabPanel panel = new MissionsTabPanel(() -> false, () -> false, () -> "Sol", () -> null);
        MissionAcceptedEvent accepted = (MissionAcceptedEvent) new EliteLogParser().parseRecord(
                "{\"timestamp\":\"2026-08-12T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":42,\"Name\":\"Mission_Collect_Industrial\","
                + "\"Commodity_Localised\":\"Gold\",\"Count\":50,"
                + "\"DestinationSystem\":\"A\",\"DestinationStation\":\"B\"}");
        panel.getTracker().applyEvent(accepted);
        assertTrue(panel.applySourcedFromSelection(42L, "Sol", "Galileo"));
        AtomicInteger immediateSaves = new AtomicInteger();
        panel.setImmediateSessionStateChangeCallback(immediateSaves::incrementAndGet);

        JTable table = findMissionsTable(panel);
        Component places = table.getCellRenderer(0, 3)
                .getTableCellRendererComponent(table, table.getValueAt(0, 3), false, false, 0, 3);
        assertNotNull(findButton((Container) places, "Clear Source"));

        assertTrue(panel.clearSourcedFromSelection(42L));
        assertNull(panel.getTracker().findById(42L).getSourcedFromSystem());
        assertNull(panel.getTracker().findById(42L).getSourcedFromStation());
        assertEquals(1, immediateSaves.get());
    }

    private static JTable findMissionsTable(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JTable table && table.getColumnCount() == 4
                    && "Places".equals(table.getColumnName(3))) return table;
            if (child instanceof Container container) {
                JTable found = findMissionsTable(container);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JButton findButton(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return button;
            if (child instanceof Container container) {
                JButton found = findButton(container, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
