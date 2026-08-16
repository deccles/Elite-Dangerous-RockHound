package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Component;
import java.awt.Container;

import javax.swing.JButton;

import org.junit.jupiter.api.Test;

class MissionsTabPanelOptimizerTest {
    @Test
    void transportTabProvidesOptimizeStopsButton() {
        MissionsTabPanel panel = new MissionsTabPanel(
                () -> false, () -> false, () -> "Sol", () -> "Galileo",
                () -> 128, systems -> { });

        assertNotNull(findButton(panel, "Optimize Stops"));
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
