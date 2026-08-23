package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;
import javax.swing.JTable;

import org.dce.ed.ui.SystemPlanMapPanel;
import org.junit.jupiter.api.Test;

class SystemPlanMapPreferenceTest {

    @Test
    void systemTabAddsAndRemovesPlanMapImmediatelyWhenPreferenceChanges() throws Exception {
        boolean saved = OverlayPreferences.isSystemPlanMapEnabled();
        try {
            OverlayPreferences.setSystemPlanMapEnabled(false);
            AtomicReference<SystemTabPanel> panelRef = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> panelRef.set(new SystemTabPanel()));
            SystemTabPanel panel = panelRef.get();
            assertFalse(containsComponent(panel, SystemPlanMapPanel.class));

            OverlayPreferences.setSystemPlanMapEnabled(true);
            SwingUtilities.invokeAndWait(panel::refreshFromSavedOverlayPreferences);
            Component map = findComponent(panel, SystemPlanMapPanel.class);
            assertTrue(map != null && map.isVisible());
            assertTrue(containsComponent(panel, JTable.class), "enabling the map must retain the body table");

            OverlayPreferences.setSystemPlanMapEnabled(false);
            SwingUtilities.invokeAndWait(panel::refreshFromSavedOverlayPreferences);
            assertFalse(containsComponent(panel, SystemPlanMapPanel.class));
            assertTrue(containsComponent(panel, JTable.class), "disabling the map must retain the body table");

            OverlayPreferences.setSystemPlanMapEnabled(true);
            SwingUtilities.invokeAndWait(panel::refreshFromSavedOverlayPreferences);
            Component restoredMap = findComponent(panel, SystemPlanMapPanel.class);
            assertTrue(restoredMap != null && restoredMap.isVisible(),
                    "re-enabling must restore a visible map after a disable cycle");
            assertTrue(containsComponent(panel, JTable.class));
        } finally {
            OverlayPreferences.setSystemPlanMapEnabled(saved);
        }
    }

    private static boolean containsComponent(Component component, Class<?> type) {
        return findComponent(component, type) != null;
    }

    private static Component findComponent(Component component, Class<?> type) {
        if (type.isInstance(component)) {
            return component;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component match = findComponent(child, type);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}
