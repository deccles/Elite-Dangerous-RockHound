package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Window;
import java.io.IOException;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.dce.ed.exobiology.audit.ExoPredictionDebuggerMain;
import org.dce.ed.logreader.RescanJournalsMain;
import org.dce.ed.tools.EdoSqliteDatabaseFrame;
import org.dce.ed.tools.SystemHierarchyGraphFrame;
import org.dce.ed.ui.ShowConsoleAction;
import org.dce.ed.util.EdsmQueryTool;
import org.dce.ed.util.GithubMsiUpdater;
import org.dce.ed.util.OverlayAppRestart;

/**
 * Actions for the overlay {@code Menu → Tools} submenu (formerly the Preferences Tools tab).
 */
public final class OverlayToolsLaunchers {

    private OverlayToolsLaunchers() {
    }

    public static void rescanJournalFull(Component parent) {
        int choice = JOptionPane.showConfirmDialog(parent,
                "A full journal rescan wipes the local SQLite system cache and replays every "
                        + "Journal.*.log file from disk.\n\n"
                        + "This can take several minutes depending on how many log files you have.",
                "Rescan journal (full)",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog progressDialog = new JDialog(owner, "Rescan journal (full)", Dialog.ModalityType.APPLICATION_MODAL);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JLabel statusLabel = new JLabel("Preparing…");
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(true);

        progressDialog.getContentPane().setLayout(new BorderLayout(10, 10));
        progressDialog.getContentPane().add(statusLabel, BorderLayout.NORTH);
        progressDialog.getContentPane().add(progressBar, BorderLayout.CENTER);
        progressDialog.setSize(480, 130);
        progressDialog.setLocationRelativeTo(parent);

        SwingWorker<Void, RescanProgressUpdate> worker = new SwingWorker<Void, RescanProgressUpdate>() {

            private Exception failure;

            @Override
            protected Void doInBackground() {
                try {
                    RescanJournalsMain.rescanJournals(true, null, null,
                            (phase, percent, detail) -> publish(new RescanProgressUpdate(phase, percent, detail)));
                } catch (IOException ex) {
                    failure = ex;
                }
                return null;
            }

            @Override
            protected void process(List<RescanProgressUpdate> chunks) {
                if (chunks.isEmpty()) {
                    return;
                }
                RescanProgressUpdate last = chunks.get(chunks.size() - 1);
                applyRescanProgress(statusLabel, progressBar, last.phase, last.percent, last.detail);
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                if (failure != null) {
                    failure.printStackTrace();
                    String msg = failure.getMessage();
                    if (msg == null || msg.isBlank()) {
                        msg = failure.getClass().getSimpleName();
                    }
                    JOptionPane.showMessageDialog(parent,
                            "Full journal rescan failed:\n" + msg,
                            "Rescan journal (full)",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                int restartChoice = JOptionPane.showConfirmDialog(parent,
                        "Journal logs have been parsed and the local cache has been rebuilt.\n\n"
                                + "Please restart Elite Dangerous RockHound so the overlay reloads session state "
                                + "and live journal monitoring from a clean baseline.\n\n"
                                + "Restart now?",
                        "Rescan journal (full)",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.INFORMATION_MESSAGE);
                if (restartChoice == JOptionPane.YES_OPTION) {
                    try {
                        OverlayAppRestart.restart(parent);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        String msg = ex.getMessage();
                        if (msg == null || msg.isBlank()) {
                            msg = ex.getClass().getSimpleName();
                        }
                        JOptionPane.showMessageDialog(parent,
                                "Could not restart the overlay:\n" + msg
                                        + "\n\nPlease close and reopen RockHound manually.",
                                "Rescan journal (full)",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    SystemTabPanel.notifyAllInstancesReloadDisplayedSystemFromCache();
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    private static void applyRescanProgress(JLabel statusLabel, JProgressBar progressBar,
            String phase, int percent, String detail) {
        String phaseText = phase != null ? phase : "Working";
        if (detail != null && !detail.isBlank()) {
            statusLabel.setText(phaseText + " — " + detail);
        } else {
            statusLabel.setText(phaseText);
        }
        if (percent < 0) {
            progressBar.setIndeterminate(true);
            progressBar.setString(null);
            return;
        }
        progressBar.setIndeterminate(false);
        progressBar.setMinimum(0);
        progressBar.setMaximum(100);
        progressBar.setValue(Math.min(100, Math.max(0, percent)));
        progressBar.setString(percent + "%");
    }

    private static final class RescanProgressUpdate {
        private final String phase;
        private final int percent;
        private final String detail;

        private RescanProgressUpdate(String phase, int percent, String detail) {
            this.phase = phase;
            this.percent = percent;
            this.detail = detail;
        }
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

    public static void launchSqliteCacheBrowser(Component parent) {
        EdoSqliteDatabaseFrame.showDefaultOrBringToFront(parent);
    }

    public static void launchSystemHierarchyGraph(Component parent) {
        SystemHierarchyGraphFrame.showDefaultOrBringToFront(parent);
    }

    public static void launchSystemHierarchyGraphForSystem(Component parent, String systemName) {
        SystemHierarchyGraphFrame.showForSystem(parent, systemName);
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
}
