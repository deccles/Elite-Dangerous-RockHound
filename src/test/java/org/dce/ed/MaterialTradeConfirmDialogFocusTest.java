package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Component;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JDialog;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.dce.ed.engineering.MaterialTradeExecutor;

class MaterialTradeConfirmDialogFocusTest {

    @Test
    void tradeDialogIsModelessAndClosingBeforeStartCompletesAsCancelled() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        AtomicReference<MaterialTradeExecutor.Result> completed = new AtomicReference<>();
        AtomicReference<MaterialTradeConfirmDialog> dialogRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> dialogRef.set(new MaterialTradeConfirmDialog(
                null, "Manufactured", "1 trade", 1, "Federal Corvette",
                status -> new MaterialTradeExecutor.Result(
                        MaterialTradeExecutor.Outcome.SUCCESS, "done"),
                null, completed::set)));

        MaterialTradeConfirmDialog dialog = dialogRef.get();
        try {
            assertEquals(JDialog.ModalityType.MODELESS, dialog.getModalityType());
            SwingUtilities.invokeAndWait(dialog::dispose);
            assertEquals(MaterialTradeExecutor.Outcome.INTERRUPTED, completed.get().outcome());
        } finally {
            if (dialog.isDisplayable()) {
                SwingUtilities.invokeAndWait(dialog::dispose);
            }
        }
    }

    @Test
    void progressViewKeepsCancelButtonReachable() {
        JButton cancel = new JButton("Cancel");
        JPanel panel = MaterialTradeConfirmDialog.createProgressPanel(
                new JProgressBar(), cancel);

        boolean found = false;
        for (Component component : panel.getComponents()) {
            if (component == cancel) {
                found = true;
            }
        }
        assertTrue(found, "Cancel must remain in the visible progress card");
    }

    @Test
    void warningAndScopeRemainVisibleWhileTradeRuns() {
        JPanel briefing = new JPanel();
        JLabel warning = new JLabel();
        briefing.setVisible(false);
        warning.setVisible(false);

        MaterialTradeConfirmDialog.keepWarningsVisibleWhileRunning(briefing, warning);

        assertTrue(briefing.isVisible());
        assertTrue(warning.isVisible());
    }

    @Test
    void topmostEnforcementStartsOnlyAfterEliteFocusIsConfirmed() {
        assertFalse(MaterialTradeConfirmDialog.shouldStartTopmostKeeper("Focusing Elite Dangerous…"));
        assertFalse(MaterialTradeConfirmDialog.shouldStartTopmostKeeper(
                "Automatic focus failed — click Elite Dangerous to start…"));
        assertTrue(MaterialTradeConfirmDialog.shouldStartTopmostKeeper("Running trade…"));
        assertTrue(MaterialTradeConfirmDialog.shouldStartTopmostKeeper("Starting trade 1 of 23…"));
    }

    @Test
    void longFailureStatusWrapsWithoutLosingForegroundName() {
        String html = MaterialTradeConfirmDialog.statusHtml(
                "Elite lost focus before keys were sent (foreground: javaw.exe)");

        assertTrue(html.contains("width:400px"));
        assertTrue(html.contains("foreground: javaw.exe"));
    }

    @Test
    void dialogIsPlacedBesideRockHoundInsteadOfOverIt() {
        Rectangle placed = MaterialTradeConfirmDialog.boundsBesideOwner(
                new Rectangle(1200, 100, 600, 900),
                new Rectangle(0, 0, 1920, 1080),
                480, 360);

        assertFalse(placed.intersects(new Rectangle(1200, 100, 600, 900)));
        assertEquals(712, placed.x);
    }

    @Test
    void remainsTopmostWithoutTakingGameFocusDuringAutomation() throws Exception {
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
            assertTrue(dialog.isAlwaysOnTop());
            assertFalse(dialog.getFocusableWindowState());
        } finally {
            SwingUtilities.invokeAndWait(dialog::dispose);
        }
    }

    @Test
    void completionRestoresDialogFocusSoCloseIsUsable() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        AtomicReference<JDialog> dialogRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JDialog dialog = new JDialog();
            dialog.setFocusableWindowState(false);
            MaterialTradeConfirmDialog.restoreFocusAfterTrade(dialog);
            dialogRef.set(dialog);
        });
        JDialog dialog = dialogRef.get();
        try {
            assertTrue(dialog.getFocusableWindowState());
            assertTrue(dialog.isAlwaysOnTop());
        } finally {
            SwingUtilities.invokeAndWait(dialog::dispose);
        }
    }
}
