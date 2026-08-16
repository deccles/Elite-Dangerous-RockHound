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
    void liveLocationSelectsMatchingStationThenFallsBackToFirstSystemStop() {
        TransportRoutePlanPanel panel = panelWithTwoLaveStations();

        panel.updateCurrentLocation("Lave", "Station B");
        assertEquals(1, panel.highlightedRowForTests());

        panel.updateCurrentLocation("Lave", "Unknown Station");
        assertEquals(0, panel.highlightedRowForTests());

        panel.updateCurrentLocation("Sol", null);
        assertEquals(-1, panel.highlightedRowForTests());
    }

    @Test
    void highlightedStopCellsReceiveOrangeRowBorder() {
        TransportRoutePlanPanel panel = panelWithTwoLaveStations();
        panel.updateCurrentLocation("Lave", "Station B");
        JTable table = panel.tableForTests();

        Component rendered = table.prepareRenderer(table.getCellRenderer(1, 1), 1, 1);

        assertTrue(rendered instanceof JComponent);
        assertTrue(((JComponent) rendered).getBorder() instanceof CompoundBorder);
        CompoundBorder border = (CompoundBorder) ((JComponent) rendered).getBorder();
        assertTrue(border.getOutsideBorder() instanceof MatteBorder);
    }

    private static TransportRoutePlanPanel panelWithTwoLaveStations() {
        TransportPlanAction visit = new TransportPlanAction(
                TransportPlanAction.Kind.VISIT, 1L, "Courier", 0);
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(new TransportLocation("Lave", "Station A", 0, 0, 0),
                        List.of(visit), 0),
                new TransportPlanStop(new TransportLocation("Lave", "Station B", 0, 0, 0),
                        List.of(visit), 0)), 0.0, true);
        return new TransportRoutePlanPanel(plan, 64, systems -> { });
    }
}
