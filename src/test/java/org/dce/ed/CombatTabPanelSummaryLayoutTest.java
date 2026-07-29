package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Font;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class CombatTabPanelSummaryLayoutTest {

    @Test
    void metricChipsGrowToFitTheAppliedUiFont() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CombatTabPanel panel = new CombatTabPanel(() -> false);
            JPanel summary = summaryOf(panel);
            int before = summary.getComponent(0).getPreferredSize().height;

            panel.applyUiFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));

            int after = summary.getComponent(0).getPreferredSize().height;
            assertTrue(after > before,
                    "the metric chip must grow after its caption and value font grow");
            assertTrue(summary.getMaximumSize().height >= summary.getPreferredSize().height,
                    "the summary row must be tall enough for the resized metric chips");
        });
    }

    private static JPanel summaryOf(CombatTabPanel panel) {
        JScrollPane scroll = (JScrollPane) panel.getComponent(0);
        JPanel content = (JPanel) scroll.getViewport().getView();
        Component summary = content.getComponent(0);
        return (JPanel) summary;
    }
}
