package org.dce.ed.tools;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmap.SystemMapHierarchyBuilder;
import org.dce.ed.systemmap.SystemMapHierarchyBuilder.Graph;
import org.dce.ed.systemmap.SystemMapPipeline;
import org.dce.ed.systemmap.SystemMapSystemLoader;
import org.dce.ed.systemmap.SystemMapSystemLoader.Loaded;
import org.dce.ed.systemmap.SystemMapSystemLoader.Source;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.SystemHierarchyGraphPanel;

/**
 * Developer tool: top-down graph of orbital parent links for one star system.
 */
public final class SystemHierarchyGraphFrame extends JFrame {

    private static final Preferences PREFS = Preferences.userNodeForPackage(SystemHierarchyGraphFrame.class);
    private static final String PREF_LAST_SYSTEM = "lastSystemName";
    private static final String PREF_LAST_SOURCE = "lastSource";

    private static SystemHierarchyGraphFrame openInstance;

    private final JTextField systemField;
    private final JComboBox<Source> sourceBox;
    private final JLabel statusLabel;
    private final SystemHierarchyGraphPanel graphPanel;

    private SystemHierarchyGraphFrame() {
        super("System hierarchy graph");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(new Dimension(1000, 720));
        setLocationByPlatform(true);

        systemField = new JTextField(PREFS.get(PREF_LAST_SYSTEM, "Eor Aowsy RI-K c8-3670"), 36);
        sourceBox = new JComboBox<>(Source.values());
        String savedSource = PREFS.get(PREF_LAST_SOURCE, Source.AUTO.name());
        try {
            sourceBox.setSelectedItem(Source.valueOf(savedSource));
        } catch (IllegalArgumentException ignored) {
            sourceBox.setSelectedItem(Source.AUTO);
        }

        statusLabel = new JLabel(" ");
        statusLabel.setForeground(EdoUi.User.MAIN_TEXT);
        graphPanel = new SystemHierarchyGraphPanel();

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        top.setBackground(EdoUi.User.PANEL_BG);
        top.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        top.add(new JLabel("System:"));
        top.add(systemField);
        top.add(new JLabel("Source:"));
        top.add(sourceBox);
        JButton loadBtn = new JButton("Load");
        JButton fitBtn = new JButton("Fit view");
        top.add(loadBtn);
        top.add(fitBtn);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(EdoUi.User.PANEL_BG);
        statusBar.setBorder(BorderFactory.createEmptyBorder(0, 12, 6, 12));
        statusBar.add(statusLabel, BorderLayout.WEST);
        JLabel hint = new JLabel("Drag to pan · wheel to zoom");
        hint.setForeground(EdoUi.Internal.mainTextAlpha(160));
        statusBar.add(hint, BorderLayout.EAST);

        getContentPane().setBackground(EdoUi.User.BACKGROUND);
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(new JScrollPane(graphPanel), BorderLayout.CENTER);
        getContentPane().add(statusBar, BorderLayout.SOUTH);

        loadBtn.addActionListener(e -> loadSystem());
        fitBtn.addActionListener(e -> graphPanel.fitToGraph());
        systemField.addActionListener(e -> loadSystem());
        getRootPane().setDefaultButton(loadBtn);
        getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control F"), "fit");
        getRootPane().getActionMap().put("fit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                graphPanel.fitToGraph();
            }
        });

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (openInstance == SystemHierarchyGraphFrame.this) {
                    openInstance = null;
                }
            }
        });
    }

    private void loadSystem() {
        String name = systemField.getText();
        if (name == null || name.isBlank()) {
            JOptionPane.showMessageDialog(this, "Enter a system name.", "System hierarchy graph",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        Source source = (Source) sourceBox.getSelectedItem();
        if (source == null) {
            source = Source.AUTO;
        }
        PREFS.put(PREF_LAST_SYSTEM, name.trim());
        PREFS.put(PREF_LAST_SOURCE, source.name());

        statusLabel.setText("Loading…");
        graphPanel.setGraph(null);
        Source loadSource = source;
        SwingWorker<Graph, Void> worker = new SwingWorker<Graph, Void>() {
            private String loadedFrom;
            private String error;

            @Override
            protected Graph doInBackground() {
                try {
                    Loaded loaded = SystemMapSystemLoader.load(name.trim(), loadSource);
                    loadedFrom = loaded.loadedFrom;
                    Map<Integer, BodyInfo> bodies = loaded.bodies;
                    var model = SystemMapPipeline.build(loaded.systemName, bodies, Instant.EPOCH, true);
                    return SystemMapHierarchyBuilder.build(loaded.systemName, model, bodies);
                } catch (IOException ex) {
                    error = ex.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    Graph graph = get();
                    if (graph == null) {
                        statusLabel.setText("Load failed");
                        JOptionPane.showMessageDialog(SystemHierarchyGraphFrame.this,
                                error != null ? error : "No data",
                                "System hierarchy graph",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    graphPanel.setGraph(graph);
                    int nodes = graph.nodeByKey.size();
                    statusLabel.setText(graph.systemName + " — " + nodes + " nodes from " + loadedFrom);
                } catch (Exception ex) {
                    statusLabel.setText("Load failed");
                    JOptionPane.showMessageDialog(SystemHierarchyGraphFrame.this,
                            ex.getMessage(),
                            "System hierarchy graph",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    public static void showDefaultOrBringToFront(Component parent) {
        SwingUtilities.invokeLater(() -> {
            if (openInstance != null && openInstance.isDisplayable()) {
                openInstance.toFront();
                openInstance.requestFocus();
                return;
            }
            openInstance = new SystemHierarchyGraphFrame();
            openInstance.setLocationRelativeTo(parent);
            openInstance.setVisible(true);
            openInstance.loadSystem();
        });
    }

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            SystemHierarchyGraphFrame frame = new SystemHierarchyGraphFrame();
            openInstance = frame;
            if (args.length > 0) {
                frame.systemField.setText(String.join(" ", args));
            }
            frame.setVisible(true);
            frame.loadSystem();
        });
    }
}
