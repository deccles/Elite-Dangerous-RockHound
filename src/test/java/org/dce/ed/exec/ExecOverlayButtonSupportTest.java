package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import javax.swing.JButton;

import org.dce.ed.ui.tabdock.OverlayTabId;
import org.junit.jupiter.api.Test;

class ExecOverlayButtonSupportTest {

    @Test
    void tabButtonsKeepTheConfiguredBindingOrder() {
        ExecBinding firstRoute = bindingForTab("First", OverlayTabId.ROUTE);
        ExecBinding controlOnly = bindingForTab("Control", null);
        ExecBinding secondRoute = bindingForTab("Second", OverlayTabId.ROUTE);

        List<ExecBinding> result = ExecOverlayButtonSupport.bindingsForButtonTab(
                List.of(firstRoute, controlOnly, secondRoute), OverlayTabId.ROUTE);

        assertEquals(List.of(firstRoute, secondRoute), result);
    }

    @Test
    void disablesButtonAndExplainsEveryUnsetRequiredPlaceholder() {
        ExecBinding binding = new ExecBinding();
        binding.setProgramArgs("--mode route $ROUTE_SELECTED_DESTINATION $STATUS_DEST_NAME");
        JButton button = new JButton("Run");

        ExecOverlayButtonSupport.applyRequiredPlaceholderAvailability(button, binding, Map.of(
                "ROUTE_SELECTED_DESTINATION", "Unknown",
                "STATUS_DEST_NAME", ""));

        assertFalse(button.isEnabled());
        assertEquals("$ROUTE_SELECTED_DESTINATION not set; $STATUS_DEST_NAME not set",
                button.getToolTipText());
    }

    @Test
    void enablesButtonWhenRequiredPlaceholdersAreAvailable() {
        ExecBinding binding = new ExecBinding();
        binding.setProgramArgs("$ROUTE_SELECTED_DESTINATION --literal Unknown");
        JButton button = new JButton("Run");

        ExecOverlayButtonSupport.applyRequiredPlaceholderAvailability(button, binding,
                Map.of("ROUTE_SELECTED_DESTINATION", "Sol"));

        assertTrue(button.isEnabled());
        assertNull(button.getToolTipText());
    }

    @Test
    void manualTriggerAndTimestampAreAvailableAtButtonLaunch() {
        ExecBinding binding = new ExecBinding();
        binding.setProgramArgs("$TRIGGER $TIMESTAMP");
        JButton button = new JButton("Run");

        ExecOverlayButtonSupport.applyRequiredPlaceholderAvailability(button, binding, Map.of(
                "TRIGGER", "Unknown",
                "TIMESTAMP", "Unknown"));

        assertTrue(button.isEnabled());
    }

    private static ExecBinding bindingForTab(String name, OverlayTabId tab) {
        ExecBinding binding = new ExecBinding();
        binding.setName(name);
        binding.setButtonTab(tab != null ? tab.cardName() : "");
        return binding;
    }
}
