package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import javax.swing.JButton;

import org.junit.jupiter.api.Test;

class ExecOverlayButtonSupportTest {

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
}
