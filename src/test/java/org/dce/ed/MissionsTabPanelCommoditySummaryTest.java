package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.dce.ed.logreader.EliteLogParser;
import org.junit.jupiter.api.Test;

class MissionsTabPanelCommoditySummaryTest {
    @Test
    void commoditySummaryIsConciseAndCappedAtFourVisibleRows() throws Exception {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 1056, systems -> { });
        EliteLogParser parser = new EliteLogParser();
        for (int i = 1; i <= 6; i++) {
            panel.getTracker().applyEvent(parser.parseRecord(
                    "{\"timestamp\":\"2026-08-16T10:00:00Z\",\"event\":\"MissionAccepted\","
                    + "\"MissionID\":" + i + ",\"Name\":\"Mission_Collect\","
                    + "\"Commodity_Localised\":\"Commodity " + i + "\",\"Count\":" + (i * 100) + ","
                    + "\"DestinationSystem\":\"System " + i + "\","
                    + "\"DestinationStation\":\"Station " + i + "\","
                    + "\"Expiry\":\"2026-08-17T10:00:00Z\"}"));
        }
        SwingUtilities.invokeAndWait(() -> { });

        JTable summary = findNamedTable(panel, "commoditySummaryTable");
        assertNotNull(summary);
        assertEquals(6, summary.getRowCount());
        assertEquals(5, summary.getColumnCount());
        assertEquals("Commodity", summary.getColumnName(0));
        assertEquals("Progress", summary.getColumnName(1));
        assertEquals("Cargo", summary.getColumnName(2));
        assertEquals("Turn-in", summary.getColumnName(3));
        assertEquals("Due", summary.getColumnName(4));
        assertEquals("Commodity 1 · 1", summary.getValueAt(0, 0));
        assertEquals("0/100 · 0%", summary.getValueAt(0, 1));
        assertEquals("H 0 · D 0", summary.getValueAt(0, 2));
        assertEquals("Station 1", summary.getValueAt(0, 3));

        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, summary);
        assertNotNull(scroll);
        int fourRowsAndHeader = summary.getRowHeight() * 4
                + summary.getTableHeader().getPreferredSize().height + 12;
        assertTrue(scroll.getMaximumSize().height <= fourRowsAndHeader);
    }

    @Test
    void clearingSourceKeepsMissionContentFullWidthWhenSourceAllAppears() throws Exception {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 1056, systems -> { });
        EliteLogParser parser = new EliteLogParser();
        panel.getTracker().applyEvent(parser.parseRecord(
                "{\"timestamp\":\"2026-08-16T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":42,\"Name\":\"Mission_Collect_Industrial\","
                + "\"Commodity_Localised\":\"Gold\",\"Count\":50,"
                + "\"DestinationSystem\":\"A\",\"DestinationStation\":\"B\"}"));
        assertTrue(panel.applySourcedFromSelection(42L, "Sol", "Galileo"));

        SwingUtilities.invokeAndWait(() -> {
            panel.setSize(new Dimension(1120, 900));
            layoutRecursively(panel);
        });
        assertTrue(panel.clearSourcedFromSelection(42L));
        SwingUtilities.invokeAndWait(() -> layoutRecursively(panel));

        JScrollPane missions = findNamedScrollPane(panel, "missionsTableScroll");
        assertNotNull(missions);
        assertTrue(missions.getX() <= 10, "mission table shifted right to x=" + missions.getX());
        assertTrue(missions.getWidth() >= 1000, "mission table narrowed to " + missions.getWidth());
    }

    private static void layoutRecursively(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container container) layoutRecursively(container);
        }
    }

    private static JScrollPane findNamedScrollPane(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (child instanceof JScrollPane scroll && name.equals(scroll.getName())) return scroll;
            if (child instanceof Container container) {
                JScrollPane found = findNamedScrollPane(container, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JTable findNamedTable(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (child instanceof JTable table && name.equals(table.getName())) return table;
            if (child instanceof Container container) {
                JTable found = findNamedTable(container, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
