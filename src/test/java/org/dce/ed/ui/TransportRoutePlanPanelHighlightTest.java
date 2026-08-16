package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.border.MatteBorder;
import javax.swing.border.CompoundBorder;

import org.dce.ed.mission.TransportLocation;
import org.dce.ed.mission.TransportPlanAction;
import org.dce.ed.mission.TransportPlanStop;
import org.dce.ed.mission.TransportRoutePlan;
import org.junit.jupiter.api.Test;

class TransportRoutePlanPanelHighlightTest {
    @Test
    void startRowPreventsCurrentSystemFromHighlightingAFutureRepeatedStop() {
        TransportRoutePlanPanel panel = alternatingPlanFromGliese();
        JTable table = panel.tableForTests();

        assertEquals(4, table.getRowCount());
        assertEquals(0, table.getValueAt(0, 0));
        assertEquals("Gliese 868 / MacLean Terminal", table.getValueAt(0, 1));
        assertEquals("", table.getValueAt(0, 2));
        assertEquals("0 / 1056 t", table.getValueAt(0, 3));
        assertEquals(0, panel.highlightedRowForTests());

        panel.updateCurrentLocation("Gliese 868", "MacLean Terminal");
        assertEquals(0, panel.highlightedRowForTests());

        panel.updateCurrentLocation("Unexpected", null);
        assertEquals(-1, panel.highlightedRowForTests());

        panel.updateCurrentLocation("Core Sys Sector FW-N a6-0", null);
        assertEquals(1, panel.highlightedRowForTests());

        panel.updateCurrentLocation("Gliese 868", null);
        assertEquals(2, panel.highlightedRowForTests());
    }

    @Test
    void highlightedStopCellsReceiveOrangeRowBorder() {
        TransportRoutePlanPanel panel = alternatingPlanFromGliese();
        panel.updateCurrentLocation("Core Sys Sector FW-N a6-0", null);
        JTable table = panel.tableForTests();

        Component rendered = table.prepareRenderer(table.getCellRenderer(1, 1), 1, 1);

        assertTrue(rendered instanceof JComponent);
        assertTrue(((JComponent) rendered).getBorder() instanceof CompoundBorder);
        CompoundBorder border = (CompoundBorder) ((JComponent) rendered).getBorder();
        assertTrue(border.getOutsideBorder() instanceof MatteBorder);
    }

    @Test
    void multipleActionsUseSeparateLinesAndExpandTheStopRow() {
        TransportPlanStop stop = new TransportPlanStop(
                new TransportLocation("Lave", "Lave Station", 10, 0, 0),
                List.of(
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP,
                                1L, "Animal Monitors", 740),
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP,
                                2L, "Gold", 138),
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP,
                                3L, "Water Purifiers", 106)),
                984);
        TransportRoutePlanPanel panel = new TransportRoutePlanPanel(
                new TransportRoutePlan(List.of(stop), 10.0, true), 1056, systems -> { });
        JTable table = panel.tableForTests();

        assertEquals("Pick up 740 t Animal Monitors\nPick up 138 t Gold\nPick up 106 t Water Purifiers",
                table.getValueAt(0, 2));
        assertTrue(table.getRowHeight(0) >= table.getRowHeight() * 3);
    }

    private static TransportRoutePlanPanel alternatingPlanFromGliese() {
        TransportPlanAction visit = new TransportPlanAction(
                TransportPlanAction.Kind.VISIT, 1L, "Courier", 0);
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(new TransportLocation(
                        "Core Sys Sector FW-N a6-0", "Davy Vision", 10, 0, 0),
                        List.of(visit), 0),
                new TransportPlanStop(new TransportLocation(
                        "Gliese 868", "MacLean Terminal", 0, 0, 0),
                        List.of(visit), 0),
                new TransportPlanStop(new TransportLocation(
                        "Core Sys Sector FW-N a6-0", "Davy Vision", 10, 0, 0),
                        List.of(visit), 0)), 20.0, true);
        return new TransportRoutePlanPanel(plan, 1056,
                new TransportLocation("Gliese 868", "MacLean Terminal", 0, 0, 0),
                0, systems -> { }, List.of());
    }
}
