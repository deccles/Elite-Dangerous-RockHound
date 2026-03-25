package org.dce.ed;

import java.awt.Component;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.dce.ed.exobiology.audit.ExoPredictionDebuggerMain;
import org.dce.ed.mining.GoogleSheetsBackend;
import org.dce.ed.tools.RunTimesBackfill;
import org.dce.ed.ui.ShowConsoleAction;
import org.dce.ed.util.EdsmQueryTool;
import org.dce.ed.util.GithubMsiUpdater;

/**
 * Actions for the overlay {@code Menu → Tools} submenu (formerly the Preferences Tools tab).
 */
public final class OverlayToolsLaunchers {

    private OverlayToolsLaunchers() {
    }

    public static void launchJournalMonitor(Component parent) {
        SwingUtilities.invokeLater(() -> {
            try {
                Class<?> clazz = Class.forName("org.dce.ed.StandaloneLogMonitor");
                clazz.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
                return;
            } catch (Exception ignore) {
                // fall through
            }

            try {
                StandaloneLogViewer.main(new String[0]);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent,
                        "Unable to launch the standalone log monitor:\n" + ex.getMessage(),
                        "Launch Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    public static void launchEdsmQueryTools(Component parent) {
        SwingUtilities.invokeLater(() -> {
            try {
                new EdsmQueryTool().setVisible(true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent,
                        "Unable to launch EDSM Query Tools:\n" + ex.getMessage(),
                        "Launch Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    public static void showConsole() {
        new ShowConsoleAction().actionPerformed(null);
    }

    public static void checkForUpdates(Component parent) {
        GithubMsiUpdater.checkAndUpdate(parent);
    }

    public static void fixMiningRunsInGoogleSheet(Component parent) {
        GoogleSheetsBackend.renumberRunsAndSortUsingPreferences(parent);
    }

    public static void backfillMiningRunTimes(Component parent) {
        RunTimesBackfill.backfillUsingPreferences(parent);
    }

    public static void launchExoPredictionDebugger(Component parent) {
        SwingUtilities.invokeLater(() -> {
            try {
                ExoPredictionDebuggerMain.main(new String[0]);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent,
                        "Unable to launch Exo Prediction Debugger:\n" + ex.getMessage(),
                        "Launch Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /** Label for the backfill action (includes commander name when configured). */
    public static String backfillMiningRunTimesMenuLabel() {
        String cmdrName = OverlayPreferences.getMiningLogCommanderName();
        if (cmdrName == null || cmdrName.isBlank()) {
            return "Backfill mining run times from journals";
        }
        return String.format("Backfill mining run times from %s's journals", cmdrName);
    }
}
