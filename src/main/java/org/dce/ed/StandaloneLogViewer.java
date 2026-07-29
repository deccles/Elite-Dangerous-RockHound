package org.dce.ed;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.LiveJournalMonitor;

public class StandaloneLogViewer {

    public static String clientKey = "LOG";

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            LogTabPanel panel = new LogTabPanel();

            JFrame frame = new JFrame("Elite Dangerous - Journal Log Viewer");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.getContentPane().setBackground(new java.awt.Color(0xF0, 0xF0, 0xF0));
            frame.getContentPane().add(panel);
            frame.setPreferredSize(new Dimension(1100, 800));
            frame.pack();
            frame.setLocationRelativeTo(null);

            // Live tail of the current journal file; feed into the same handler LogTabPanel uses.
            LiveJournalMonitor monitor = LiveJournalMonitor.getInstance(clientKey);
            monitor.addListener((EliteLogEvent e) -> {
                // Ensure all UI updates occur on the EDT
                SwingUtilities.invokeLater(() -> panel.handleLogEvent(e));
            });

            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    monitor.shutdown();
                }
            });

            frame.setVisible(true);
        });
    }
}
