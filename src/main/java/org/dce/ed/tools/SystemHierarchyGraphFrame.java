package org.dce.ed.tools;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
import javax.swing.Timer;

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
    private static final String PREF_VIEW_SYSTEM = "viewSystemName";
    private static final String PREF_PAN_X = "prefPanX";
    private static final String PREF_PAN_Y = "prefPanY";
    private static final String PREF_SCALE = "prefScale";
    private static final String PREF_FRAME_X = "frameX";
    private static final String PREF_FRAME_Y = "frameY";
    private static final String PREF_FRAME_WIDTH = "frameWidth";
    private static final String PREF_FRAME_HEIGHT = "frameHeight";
    private static final String PREF_FRAME_MAXIMIZED = "frameMaximized";
    private static final String PREF_FRAME_GRAPHICS_DEVICE_ID = "frameGraphicsDeviceId";

    private static final int MIN_FRAME_SIZE = 200;
    private static final int MIN_FRAME_LOCATION = -100;
    private static final int FRAME_BOUNDS_SAVE_DEBOUNCE_MS = 400;

    private static SystemHierarchyGraphFrame openInstance;
    private boolean restoredFrameBounds;
    private String lastLoadedSystemName;

    private final JTextField systemField;
    private final JComboBox<Source> sourceBox;
    private final JLabel statusLabel;
    private final SystemHierarchyGraphPanel graphPanel;

    private SystemHierarchyGraphFrame() {
        super("System hierarchy graph");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(new Dimension(1000, 720));
        restoredFrameBounds = restoreFrameBoundsFromPrefs();
        if (!restoredFrameBounds) {
            setLocationByPlatform(true);
        }

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
        graphPanel.setViewChangeListener(this::saveViewPrefs);

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

        Timer boundsSaveTimer = new Timer(FRAME_BOUNDS_SAVE_DEBOUNCE_MS, e -> saveFrameBoundsToPrefs());
        boundsSaveTimer.setRepeats(false);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                scheduleBoundsSave(boundsSaveTimer);
            }

            @Override
            public void componentResized(ComponentEvent e) {
                scheduleBoundsSave(boundsSaveTimer);
            }
        });

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                boundsSaveTimer.stop();
                saveFrameBoundsToPrefs();
                saveViewPrefs();
            }

            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                boundsSaveTimer.stop();
                saveFrameBoundsToPrefs();
                saveViewPrefs();
                if (openInstance == SystemHierarchyGraphFrame.this) {
                    openInstance = null;
                }
            }
        });
    }

    private static void scheduleBoundsSave(Timer timer) {
        if (timer.isRunning()) {
            timer.restart();
        } else {
            timer.start();
        }
    }

    private static Rectangle deviceScreenBounds(GraphicsDevice device) {
        return device.getDefaultConfiguration().getBounds();
    }

    private static long intersectionArea(Rectangle a, Rectangle b) {
        Rectangle inter = a.intersection(b);
        if (inter.width <= 0 || inter.height <= 0) {
            return 0L;
        }
        return (long) inter.width * inter.height;
    }

    /**
     * Picks the monitor that held the window: saved {@link GraphicsDevice#getIDstring()} first,
     * else largest overlap with saved bounds, else primary.
     */
    private static GraphicsDevice resolveGraphicsDevice(String savedDeviceId, Rectangle savedFrame) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] devices = ge.getScreenDevices();
        if (devices == null || devices.length == 0) {
            return ge.getDefaultScreenDevice();
        }
        if (savedDeviceId != null && !savedDeviceId.isEmpty()) {
            for (GraphicsDevice gd : devices) {
                if (savedDeviceId.equals(gd.getIDstring())) {
                    return gd;
                }
            }
        }
        GraphicsDevice best = null;
        long bestArea = 0L;
        for (GraphicsDevice gd : devices) {
            long area = intersectionArea(savedFrame, deviceScreenBounds(gd));
            if (area > bestArea) {
                bestArea = area;
                best = gd;
            }
        }
        if (best != null && bestArea > 0L) {
            return best;
        }
        return ge.getDefaultScreenDevice();
    }

    private static GraphicsDevice graphicsDeviceForFrame(Rectangle frameOnScreen) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] devices = ge.getScreenDevices();
        if (devices == null || devices.length == 0) {
            return ge.getDefaultScreenDevice();
        }
        Point center = new Point(frameOnScreen.x + frameOnScreen.width / 2, frameOnScreen.y + frameOnScreen.height / 2);
        for (GraphicsDevice gd : devices) {
            if (deviceScreenBounds(gd).contains(center)) {
                return gd;
            }
        }
        GraphicsDevice best = null;
        long bestArea = 0L;
        for (GraphicsDevice gd : devices) {
            long area = intersectionArea(frameOnScreen, deviceScreenBounds(gd));
            if (area > bestArea) {
                bestArea = area;
                best = gd;
            }
        }
        return best != null ? best : ge.getDefaultScreenDevice();
    }

    private static Rectangle clampFrameToScreen(Rectangle frame, Rectangle screen) {
        int w = Math.max(MIN_FRAME_SIZE, Math.min(frame.width, screen.width));
        int h = Math.max(MIN_FRAME_SIZE, Math.min(frame.height, screen.height));
        int minX = screen.x + MIN_FRAME_LOCATION;
        int minY = screen.y + MIN_FRAME_LOCATION;
        int maxX = screen.x + screen.width - w;
        int maxY = screen.y + screen.height - h;
        int x = Math.max(minX, Math.min(frame.x, maxX));
        int y = Math.max(minY, Math.min(frame.y, maxY));
        return new Rectangle(x, y, w, h);
    }

    private static Rectangle centerOnScreen(int width, int height, Rectangle screen) {
        int w = Math.max(MIN_FRAME_SIZE, Math.min(width, screen.width));
        int h = Math.max(MIN_FRAME_SIZE, Math.min(height, screen.height));
        int x = screen.x + Math.max(0, (screen.width - w) / 2);
        int y = screen.y + Math.max(0, (screen.height - h) / 2);
        return new Rectangle(x, y, w, h);
    }

    private boolean restoreFrameBoundsFromPrefs() {
        int w = PREFS.getInt(PREF_FRAME_WIDTH, -1);
        int h = PREFS.getInt(PREF_FRAME_HEIGHT, -1);
        if (w <= MIN_FRAME_SIZE || h <= MIN_FRAME_SIZE) {
            return false;
        }

        int x = PREFS.getInt(PREF_FRAME_X, Integer.MIN_VALUE);
        int y = PREFS.getInt(PREF_FRAME_Y, Integer.MIN_VALUE);
        boolean hasLocation = x != Integer.MIN_VALUE && y != Integer.MIN_VALUE;
        if (!hasLocation) {
            setSize(w, h);
            return false;
        }

        Rectangle saved = new Rectangle(x, y, w, h);
        String savedDeviceId = PREFS.get(PREF_FRAME_GRAPHICS_DEVICE_ID, "");
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = resolveGraphicsDevice(savedDeviceId, saved);
        Rectangle screen = deviceScreenBounds(device);

        Rectangle applied;
        if (intersectionArea(saved, screen) > 0L) {
            applied = clampFrameToScreen(saved, screen);
        } else {
            GraphicsDevice primary = ge.getDefaultScreenDevice();
            applied = centerOnScreen(w, h, deviceScreenBounds(primary));
        }

        setLocationByPlatform(false);
        setBounds(applied);
        if (PREFS.getBoolean(PREF_FRAME_MAXIMIZED, false)) {
            setExtendedState(getExtendedState() | Frame.MAXIMIZED_BOTH);
        }
        return true;
    }

    private void saveFrameBoundsToPrefs() {
        boolean maximized = (getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
        PREFS.putBoolean(PREF_FRAME_MAXIMIZED, maximized);
        if (maximized) {
            return;
        }
        Rectangle b;
        try {
            Point loc = getLocationOnScreen();
            b = new Rectangle(loc.x, loc.y, getWidth(), getHeight());
        } catch (IllegalComponentStateException e) {
            b = getBounds();
        }
        if (b.width <= MIN_FRAME_SIZE || b.height <= MIN_FRAME_SIZE) {
            return;
        }
        GraphicsDevice device = graphicsDeviceForFrame(b);
        PREFS.put(PREF_FRAME_GRAPHICS_DEVICE_ID, device.getIDstring());
        PREFS.putInt(PREF_FRAME_X, b.x);
        PREFS.putInt(PREF_FRAME_Y, b.y);
        PREFS.putInt(PREF_FRAME_WIDTH, b.width);
        PREFS.putInt(PREF_FRAME_HEIGHT, b.height);
        try {
            PREFS.flush();
        } catch (Exception ignored) {
        }
    }

    private void saveViewPrefs() {
        if (lastLoadedSystemName == null || lastLoadedSystemName.isEmpty()) {
            return;
        }
        PREFS.put(PREF_VIEW_SYSTEM, lastLoadedSystemName);
        PREFS.putDouble(PREF_PAN_X, graphPanel.getPanX());
        PREFS.putDouble(PREF_PAN_Y, graphPanel.getPanY());
        PREFS.putDouble(PREF_SCALE, graphPanel.getScale());
    }

    private boolean restoreViewPrefsIfMatching(String systemName) {
        String savedSystem = PREFS.get(PREF_VIEW_SYSTEM, "");
        if (!systemName.equals(savedSystem)) {
            return false;
        }
        double scale = PREFS.getDouble(PREF_SCALE, Double.NaN);
        if (Double.isNaN(scale) || scale <= 0) {
            return false;
        }
        graphPanel.setViewTransform(scale, PREFS.getDouble(PREF_PAN_X, 40.0), PREFS.getDouble(PREF_PAN_Y, 40.0));
        return true;
    }

    private static String formatLoadedFrom(Loaded loaded) {
        if (loaded == null) {
            return "?";
        }
        if ("cache+journal".equals(loaded.loadedFrom) && loaded.cacheBodyCount >= 0) {
            return "cache+journal (" + loaded.cacheBodyCount + "+" + loaded.journalBodiesAdded + ")";
        }
        return loaded.loadedFrom;
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
        lastLoadedSystemName = null;
        graphPanel.setGraph(null);
        Source loadSource = source;
        SwingWorker<Graph, Void> worker = new SwingWorker<Graph, Void>() {
            private Loaded loadedResult;
            private String error;

            @Override
            protected Graph doInBackground() {
                try {
                    Loaded loaded = SystemMapSystemLoader.load(name.trim(), loadSource);
                    loadedResult = loaded;
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
                    lastLoadedSystemName = graph.systemName;
                    SwingUtilities.invokeLater(() -> {
                        if (!restoreViewPrefsIfMatching(graph.systemName)) {
                            graphPanel.fitToGraph();
                        }
                        saveViewPrefs();
                    });
                    int nodes = graph.nodeByKey.size();
                    statusLabel.setText(graph.systemName + " — " + nodes + " nodes from "
                            + formatLoadedFrom(loadedResult));
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
            if (!openInstance.restoredFrameBounds) {
                openInstance.setLocationRelativeTo(parent);
            }
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
