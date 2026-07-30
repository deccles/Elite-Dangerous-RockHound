package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class MaterialTradeConfirmDialogFocusTest {

    @Test
    void releasesTopmostAndFocusableStateBeforeTrading() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        AtomicReference<JDialog> dialogRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            JDialog dialog = new JDialog();
            dialog.setAlwaysOnTop(true);
            dialog.setFocusableWindowState(true);
            dialogRef.set(dialog);
            MaterialTradeConfirmDialog.releaseFocusForTrade(dialog);
        });

        JDialog dialog = dialogRef.get();
        try {
            assertFalse(dialog.isAlwaysOnTop());
            assertFalse(dialog.getFocusableWindowState());
        } finally {
            SwingUtilities.invokeAndWait(dialog::dispose);
        }
    }
}
