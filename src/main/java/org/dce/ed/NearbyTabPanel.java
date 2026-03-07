package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

import org.dce.ed.cache.CachedBody;
import org.dce.ed.cache.CachedSystem;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.edsm.BodiesResponse;
import org.dce.ed.edsm.SphereSystemsResponse;
import org.dce.ed.edsm.SystemResponse;
import org.dce.ed.exobiology.BodyAttributes;
import org.dce.ed.exobiology.ExobiologyData;
import org.dce.ed.exobiology.ExobiologyData.AtmosphereType;
import org.dce.ed.exobiology.ExobiologyData.BioCandidate;
import org.dce.ed.exobiology.ExobiologyData.PlanetType;
import org.dce.ed.state.SystemState;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.SystemTableHoverCopyManager;
import org.dce.ed.util.EdsmClient;
import org.dce.ed.util.FirstBonusHelper;
import org.dce.ed.util.SpanshClient;
import org.dce.ed.util.SpanshBodyExobiologyInfo;
import org.dce.ed.util.SpanshLandmark;
import org.dce.ed.util.SpanshLandmarkCache;

import com.google.gson.Gson;

/**
 * Nearby tab: sphere search around current system, exobiology prediction on landable planets,
 * table of high-value systems. Respects overlay color and transparency (pass-through mode).
 */
public class NearbyTabPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final SystemTabPanel systemTabPanel;
    private final EdsmClient edsmClient = new EdsmClient();
    private final SpanshClient spanshClient = new SpanshClient();

    private final JLabel headerLabel;
    private final JPanel progressPanel;
    private final JLabel progressLabel;
    private final javax.swing.JProgressBar progressBar;
    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JScrollPane scrollPane;

    private static final int COL_SYSTEM = 0;

    /** Max concurrent EDSM/Spansh body queries per batch; results shown in table as each batch completes. */
    private static final int NEARBY_QUERY_BATCH_SIZE = 6;

    private final AtomicBoolean firstShowDone = new AtomicBoolean(false);
    private volatile boolean refreshRequested;
    private SystemTableHoverCopyManager systemTableHoverCopyManager;
    private final BooleanSupplier passThroughEnabledSupplier;

    public NearbyTabPanel(SystemTabPanel systemTabPanel) {
        this(systemTabPanel, null);
    }

    public NearbyTabPanel(SystemTabPanel systemTabPanel, BooleanSupplier passThroughEnabledSupplier) {
        this.systemTabPanel = systemTabPanel;
        this.passThroughEnabledSupplier = passThroughEnabledSupplier;

        setLayout(new BorderLayout());
        setOpaque(false);
        setBackground(EdoUi.Internal.TRANSPARENT);

        headerLabel = new JLabel("Nearby (exobiology)");
        headerLabel.setForeground(EdoUi.User.MAIN_TEXT);
        headerLabel.setHorizontalAlignment(SwingConstants.LEFT);
        headerLabel.setOpaque(false);
        Font base = OverlayPreferences.getUiFont();
        headerLabel.setFont(base.deriveFont(Font.BOLD, OverlayPreferences.getUiFontSize() + 2));

        tableModel = new DefaultTableModel(new Object[]{"System", "Planets", "Exobiology", "Rings", "Est. exobiology (cr)", "ValueCr", "Distance"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setOpaque(false);
        table.setBackground(EdoUi.Internal.TRANSPARENT);
        table.setGridColor(EdoUi.Internal.TRANSPARENT);
        table.setForeground(EdoUi.User.MAIN_TEXT);
        table.setDefaultEditor(Object.class, null);
        table.setFocusable(false);
        table.setRowSelectionAllowed(false);
        table.getTableHeader().setOpaque(true);
        table.getTableHeader().setForeground(EdoUi.User.MAIN_TEXT);
        table.getTableHeader().setBackground(EdoUi.User.BACKGROUND);
        table.getColumnModel().getColumn(5).setMinWidth(0);
        table.getColumnModel().getColumn(5).setMaxWidth(0);
        table.getColumnModel().getColumn(5).setWidth(0);
        table.getColumnModel().getColumn(6).setMinWidth(0);
        table.getColumnModel().getColumn(6).setMaxWidth(0);
        table.getColumnModel().getColumn(6).setWidth(0);
        DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer() {
            {
                setOpaque(false);
                setForeground(EdoUi.User.MAIN_TEXT);
            }

            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                if (c instanceof JLabel) {
                    ((JLabel) c).setOpaque(false);
                    ((JLabel) c).setBackground(EdoUi.Internal.TRANSPARENT);
                    long minValueCr = (long) (OverlayPreferences.getNearbyMinValueMillionCredits() * 1_000_000);
                    boolean highValue = false;
                    if (tableModel.getRowCount() > row && tableModel.getColumnCount() > 5) {
                        Object valObj = tableModel.getValueAt(row, 5);
                        if (valObj instanceof Number) {
                            highValue = ((Number) valObj).longValue() >= minValueCr;
                        }
                    }
                    ((JLabel) c).setForeground(highValue ? EdoUi.User.SUCCESS : EdoUi.User.MAIN_TEXT);
                }
                return c;
            }
        };
        table.setDefaultRenderer(Object.class, cellRenderer);
        table.setToolTipText("<html>Exobiology: predicted genus names. Est. exobiology (cr): sum of estimated credits for scanning/sampling all predicted species in the system (first-discovery bonus where applicable).</html>");

        scrollPane = new JScrollPane(table);
        scrollPane.setOpaque(false);
        JViewport mainViewport = scrollPane.getViewport();
        mainViewport.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        mainViewport.setOpaque(false);
        mainViewport.setBackground(EdoUi.Internal.TRANSPARENT);
        JViewport headerViewport = scrollPane.getColumnHeader();
        if (headerViewport != null) {
            headerViewport.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        }
        scrollPane.setBorder(null);
        scrollPane.setViewportBorder(null);

        // Keep table and header in sync when user resizes columns (avoids paint/layout corruption).
        table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            @Override
            public void columnMarginChanged(javax.swing.event.ChangeEvent e) {
                syncTableAndHeaderAfterResize();
            }
            @Override
            public void columnAdded(TableColumnModelEvent e) {}
            @Override
            public void columnRemoved(TableColumnModelEvent e) {}
            @Override
            public void columnMoved(TableColumnModelEvent e) {}
            @Override
            public void columnSelectionChanged(ListSelectionEvent e) {}
        });

        // Copy system name to clipboard: hover only in pass-through mode; double-click always copies.
        systemTableHoverCopyManager = new SystemTableHoverCopyManager(table, COL_SYSTEM, passThroughEnabledSupplier);
        systemTableHoverCopyManager.start();
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                int viewRow = table.rowAtPoint(e.getPoint());
                int viewCol = table.columnAtPoint(e.getPoint());
                if (viewRow < 0 || viewCol < 0) {
                    return;
                }
                int modelCol = table.convertColumnIndexToModel(viewCol);
                if (modelCol != COL_SYSTEM) {
                    return;
                }
                systemTableHoverCopyManager.copySystemNameAtViewRow(viewRow);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                handleRowContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleRowContextMenu(e);
            }

            private void handleRowContextMenu(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                if (passThroughEnabledSupplier != null && passThroughEnabledSupplier.getAsBoolean()) {
                    return;
                }
                int viewRow = table.rowAtPoint(e.getPoint());
                if (viewRow < 0) {
                    return;
                }
                int modelRow = table.convertRowIndexToModel(viewRow);
                if (modelRow < 0 || modelRow >= table.getModel().getRowCount()) {
                    return;
                }
                Object val = table.getModel().getValueAt(modelRow, COL_SYSTEM);
                String systemName = val != null ? val.toString().trim() : null;
                if (systemName == null || systemName.isEmpty()) {
                    return;
                }
                JPopupMenu menu = new JPopupMenu();
                JMenuItem item = new JMenuItem("View EDSM / Spansh / prediction data…");
                item.addActionListener(a -> showRowDataDialog(systemName));
                menu.add(item);
                menu.show(table, e.getX(), e.getY());
            }
        });

        progressPanel = new JPanel(new BorderLayout());
        progressPanel.setOpaque(false);
        progressLabel = new JLabel(" ");
        progressLabel.setForeground(EdoUi.User.MAIN_TEXT);
        progressLabel.setOpaque(false);
        progressBar = new javax.swing.JProgressBar(0, 100);
        progressBar.setIndeterminate(false);
        progressBar.setStringPainted(true);
        progressBar.setForeground(EdoUi.User.MAIN_TEXT);
        progressBar.setBackground(EdoUi.User.BACKGROUND);
        progressBar.setOpaque(false);
        progressPanel.add(progressLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.setVisible(false);

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(headerLabel, BorderLayout.NORTH);
        north.add(progressPanel, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Revalidate and repaint table, header, and viewports after column resize so the header and body stay aligned.
     */
    private void syncTableAndHeaderAfterResize() {
        table.revalidate();
        table.repaint();
        JTableHeader header = table.getTableHeader();
        if (header != null) {
            header.revalidate();
            header.repaint();
        }
        JViewport vp = scrollPane.getViewport();
        if (vp != null) {
            vp.revalidate();
            vp.repaint();
        }
        JViewport headerVp = scrollPane.getColumnHeader();
        if (headerVp != null) {
            headerVp.revalidate();
            headerVp.repaint();
        }
        scrollPane.revalidate();
        scrollPane.repaint();
    }

    /**
     * Called when the tab is first shown. Runs sphere search and populates the table if we have a current system.
     */
    public void onTabFirstShown() {
        if (firstShowDone.compareAndSet(false, true)) {
            refreshInBackground();
        }
    }

    /**
     * Called when the user jumps to another system. Refreshes the table for the new current system.
     */
    public void onCurrentSystemChanged(String systemName, long systemAddress) {
        refreshRequested = true;
        refreshInBackground();
    }

    private void refreshInBackground() {
        SystemState state = systemTabPanel != null ? systemTabPanel.getState() : null;
        String centerName = state != null ? state.getSystemName() : null;
        if (centerName == null)
        	centerName = "Tenjin";
        if (centerName == null || centerName.trim().isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                tableModel.setRowCount(0);
                tableModel.addRow(new Object[]{"—", "No current system", "—", "—", "—", Long.valueOf(0L), Double.valueOf(0.0)});
            });
            return;
        }

        int radiusLy = OverlayPreferences.getNearbySphereRadiusLy();
        long minValueCr = (long) (OverlayPreferences.getNearbyMinValueMillionCredits() * 1_000_000);

        final String finalCenterName = centerName;

        SwingWorker<List<Object[]>, Object[]> worker = new SwingWorker<List<Object[]>, Object[]>() {
            @Override
            protected List<Object[]> doInBackground() throws Exception {
                List<Object[]> rows = new ArrayList<>();
                Comparator<Object[]> nearbyRowOrder = null;
                try {
                    double cx = 0, cy = 0, cz = 0;
                    boolean haveCoords = false;
                    SystemState s = systemTabPanel != null ? systemTabPanel.getState() : null;
                    if (s != null && s.getStarPos() != null && s.getStarPos().length >= 3) {
                        cx = s.getStarPos()[0];
                        cy = s.getStarPos()[1];
                        cz = s.getStarPos()[2];
                        haveCoords = true;
                    }
                    if (!haveCoords) {
                        SystemResponse center = edsmClient.getSystem(finalCenterName.trim());
                        if (center != null && center.coords != null) {
                            cx = center.coords.x;
                            cy = center.coords.y;
                            cz = center.coords.z;
                            haveCoords = true;
                        }
                    }
                    SphereSystemsResponse[] systems = null;
                    if (finalCenterName != null && !finalCenterName.trim().isEmpty()) {
                        systems = edsmClient.sphereSystemsByName(finalCenterName.trim(), radiusLy);
                    }
                    if ((systems == null || systems.length == 0) && haveCoords) {
                        systems = edsmClient.sphereSystems(cx, cy, cz, radiusLy);
                    }
                    if (systems == null || systems.length == 0) {
                        return rows;
                    }
                    // Sort by distance (closest first). Process all systems; use cache when available,
                    // and fetch from EDSM/Spansh for any uncached system so we cache them for next run.
                    Arrays.sort(systems, Comparator.comparingDouble(sys -> sys.distance));
                    final int maxToScan = Math.min(systems.length, 1000);
                    SystemCache cache = SystemCache.getInstance();
                    boolean needBodiesFromApi = false;
                    for (int j = 0; j < maxToScan; j++) {
                        SphereSystemsResponse s0 = systems[j];
                        if (s0 == null || s0.name == null || s0.name.isEmpty()) continue;
                        CachedSystem cached = cache.get(0L, s0.name);
                        if (cached == null || cached.bodies == null || cached.bodies.isEmpty()) {
                            needBodiesFromApi = true;
                            break;
                        }
                    }
                    Map<String, List<BodiesResponse.Body>> bodiesBySystemFromSpansh = null;
                    if (needBodiesFromApi) {
                        bodiesBySystemFromSpansh = fetchSpanshBodiesInSphere(finalCenterName, radiusLy);
                    }
                    final Map<String, List<BodiesResponse.Body>> spanshBodies = bodiesBySystemFromSpansh;
                    long minValueCr = (long) (OverlayPreferences.getNearbyMinValueMillionCredits() * 1_000_000);
                    nearbyRowOrder = Comparator
                            .comparingInt((Object[] r) -> ((Number) r[5]).longValue() >= minValueCr ? 0 : 1)
                            .thenComparing((Object[] a, Object[] b) -> {
                                boolean aGreen = ((Number) a[5]).longValue() >= minValueCr;
                                boolean bGreen = ((Number) b[5]).longValue() >= minValueCr;
                                if (aGreen && bGreen) {
                                    int byVal = Long.compare(((Number) b[5]).longValue(), ((Number) a[5]).longValue());
                                    if (byVal != 0) return byVal;
                                    return Double.compare(((Number) a[6]).doubleValue(), ((Number) b[6]).doubleValue());
                                }
                                if (!aGreen && !bGreen) {
                                    return Double.compare(((Number) a[6]).doubleValue(), ((Number) b[6]).doubleValue());
                                }
                                return 0;
                            });
                    int fromCache = 0;
                    int queried = 0;
                    ExecutorService executor = Executors.newFixedThreadPool(NEARBY_QUERY_BATCH_SIZE);
                    try {
                        for (int start = 0; start < maxToScan; start += NEARBY_QUERY_BATCH_SIZE) {
                            int batchEnd = Math.min(start + NEARBY_QUERY_BATCH_SIZE, maxToScan);
                            List<Future<TaskResult>> futures = new ArrayList<>(NEARBY_QUERY_BATCH_SIZE);
                            for (int j = start; j < batchEnd; j++) {
                                final SphereSystemsResponse sys = systems[j];
                                if (sys == null || sys.name == null || sys.name.isEmpty()) {
                                    futures.add(CompletableFuture.completedFuture(new TaskResult(null, null, false)));
                                    continue;
                                }
                                futures.add(executor.submit(new Callable<TaskResult>() {
                                    @Override
                                    public TaskResult call() {
                                        return processOneSystem(sys, cache, spanshBodies, edsmClient);
                                    }
                                }));
                            }
                            for (Future<TaskResult> f : futures) {
                                try {
                                    TaskResult tr = f.get();
                                    if (tr.fromCache) fromCache++; else queried++;
                                    if (tr.cachePayload != null) {
                                        cache.put(0L, tr.cachePayload.systemName, tr.cachePayload.starPos,
                                                tr.cachePayload.bodyCount, null, null, null, tr.cachePayload.cachedBodies);
                                    }
                                    if (tr.row != null) rows.add(tr.row);
                                } catch (Exception e) {
                                    // skip
                                }
                            }
                            rows.sort(nearbyRowOrder);
                            int current = batchEnd;
                            setProgress(maxToScan > 0 ? (int) (100.0 * current / maxToScan) : 0);
                            publish(new Object[]{ Integer.valueOf(current), Integer.valueOf(maxToScan), new ArrayList<>(rows) });
                        }
                        System.out.println("Nearby panel: " + fromCache + " systems from cache, " + queried + " queried.");
                    } finally {
                        executor.shutdown();
                        try {
                            executor.awaitTermination(30, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    setProgress(100);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (nearbyRowOrder != null) {
                    rows.sort(nearbyRowOrder);
                }
                return rows;
            }

            @Override
            protected void done() {
                SwingUtilities.invokeLater(() -> {
                    progressPanel.setVisible(false);
                    progressBar.setValue(0);
                });
                try {
                    List<Object[]> result = get();
                    final List<Object[]> res = result;
                    SwingUtilities.invokeLater(() -> {
                        tableModel.setRowCount(0);
                        if (res == null || res.isEmpty()) {
                            tableModel.addRow(new Object[]{"—", "No systems with exobiology or rings in range", "—", "—", "—", Long.valueOf(0L), Double.valueOf(0.0)});
                        } else {
                            for (Object[] row : res) {
                                tableModel.addRow(row);
                            }
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    SwingUtilities.invokeLater(() -> {
                        tableModel.setRowCount(0);
                        tableModel.addRow(new Object[]{"—", "Interrupted", "—", "—", "—", Long.valueOf(0L), Double.valueOf(0.0)});
                    });
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    final String msg = cause != null ? cause.getMessage() : e.getMessage();
                    SwingUtilities.invokeLater(() -> {
                        tableModel.setRowCount(0);
                        tableModel.addRow(new Object[]{"—", "Error: " + msg, "—", "—", "—", Long.valueOf(0L), Double.valueOf(0.0)});
                    });
                }
            }

            @Override
            protected void process(java.util.List<Object[]> chunks) {
                if (chunks.isEmpty()) return;
                Object[] last = chunks.get(chunks.size() - 1);
                int current = ((Integer) last[0]).intValue();
                int total = ((Integer) last[1]).intValue();
                @SuppressWarnings("unchecked")
                List<Object[]> rowsSoFar = (List<Object[]>) last[2];
                int pct = total > 0 ? (int) (100.0 * current / total) : 0;
                progressBar.setValue(pct);
                progressBar.setString(pct + "%");
                progressLabel.setText("Scanning... " + current + " / " + total + " systems");
                tableModel.setRowCount(0);
                if (rowsSoFar != null && !rowsSoFar.isEmpty()) {
                    for (Object[] row : rowsSoFar) {
                        tableModel.addRow(row);
                    }
                }
            }
        };
        worker.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("progress".equals(evt.getPropertyName())) {
                    int p = (Integer) evt.getNewValue();
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(p);
                        progressBar.setString(p + "%");
                    });
                }
            }
        });
        progressPanel.setVisible(true);
        progressBar.setValue(0);
        progressBar.setString("0%");
        progressLabel.setText("Scanning... 0 / ? systems");
        worker.execute();
    }

    /**
     * Show a modal dialog with EDSM, Spansh, and prediction data for the given system (from cache).
     * Only used when not in pass-through mode via right-click menu.
     */
    private void showRowDataDialog(String systemName) {
        CachedSystem cs = SystemCache.getInstance().get(0L, systemName);
        String content;
        if (cs == null) {
            content = "No cached data for this system.";
        } else {
            content = buildRowDataText(cs);
        }
        JTextArea area = new JTextArea(content);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setLineWrap(false);
        area.setWrapStyleWord(false);
        area.setBackground(EdoUi.User.BACKGROUND);
        area.setForeground(EdoUi.User.MAIN_TEXT);
        area.setCaretColor(EdoUi.User.MAIN_TEXT);

        JScrollPane sp = new JScrollPane(area);
        sp.setPreferredSize(new Dimension(900, 600));

        JButton close = new JButton("Close");
        close.addActionListener(a -> {
            JDialog d = (JDialog) SwingUtilities.getWindowAncestor(close);
            if (d != null) d.dispose();
        });

        JPanel south = new JPanel(new BorderLayout());
        south.add(close, BorderLayout.EAST);

        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Row data: " + systemName, false);
        dlg.getContentPane().setLayout(new BorderLayout());
        dlg.getContentPane().add(sp, BorderLayout.CENTER);
        dlg.getContentPane().add(south, BorderLayout.SOUTH);
        dlg.getContentPane().setBackground(EdoUi.User.BACKGROUND);

        dlg.getRootPane().registerKeyboardAction(
                e -> dlg.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        dlg.pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        dlg.setLocation(
                Math.max(0, (screen.width - dlg.getWidth()) / 2),
                Math.max(0, (screen.height - dlg.getHeight()) / 2));
        dlg.setVisible(true);
        area.setCaretPosition(0);
    }

    private static String buildRowDataText(CachedSystem cs) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== System ===\n");
        sb.append("  systemName: ").append(str(cs.systemName)).append("\n");
        sb.append("  starPos: ");
        if (cs.starPos != null && cs.starPos.length >= 3) {
            sb.append(cs.starPos[0]).append(", ").append(cs.starPos[1]).append(", ").append(cs.starPos[2]);
        } else {
            sb.append("—");
        }
        sb.append("\n");
        sb.append("  totalBodies: ").append(cs.totalBodies != null ? cs.totalBodies : "—").append("\n");
        if (cs.bodies == null || cs.bodies.isEmpty()) {
            sb.append("\n(no bodies in cache)\n");
            return sb.toString();
        }
        for (CachedBody cb : cs.bodies) {
            sb.append("\n--- Body: ").append(str(cb.name)).append(" ---\n");
            sb.append("  bodyId: ").append(cb.bodyId).append("  distanceLs: ").append(cb.distanceLs).append("\n");
            sb.append("EDSM:\n");
            sb.append("  planetClass: ").append(str(cb.planetClass)).append("\n");
            sb.append("  atmosphere: ").append(str(cb.atmosphere)).append("  atmoOrType: ").append(str(cb.atmoOrType)).append("\n");
            sb.append("  gravityMS: ").append(cb.gravityMS != null ? cb.gravityMS : "—").append("  surfaceTempK: ").append(cb.surfaceTempK != null ? cb.surfaceTempK : "—").append("\n");
            sb.append("  surfacePressure: ").append(cb.surfacePressure != null ? cb.surfacePressure : "—").append("\n");
            sb.append("  volcanism: ").append(str(cb.volcanism)).append("\n");
            sb.append("  ringTypes: ").append(cb.ringTypes != null && !cb.ringTypes.isEmpty() ? String.join(", ", cb.ringTypes) : "—").append("\n");
            sb.append("  landable: ").append(cb.landable).append("\n");
            sb.append("  discoveryCommander: ").append(str(cb.discoveryCommander)).append("\n");
            sb.append("  wasDiscovered: ").append(cb.wasDiscovered != null ? cb.wasDiscovered : "—").append("  wasMapped: ").append(cb.wasMapped != null ? cb.wasMapped : "—").append("  wasFootfalled: ").append(cb.wasFootfalled != null ? cb.wasFootfalled : "—").append("\n");
            sb.append("Spansh:\n");
            if (cb.spanshLandmarks != null && !cb.spanshLandmarks.isEmpty()) {
                for (int i = 0; i < cb.spanshLandmarks.size(); i++) {
                    SpanshLandmark lm = cb.spanshLandmarks.get(i);
                    sb.append("  landmark ").append(i + 1).append(": type=").append(str(lm.getType())).append(" subtype=").append(str(lm.getSubtype())).append(" lat=").append(lm.getLatitude()).append(" lon=").append(lm.getLongitude()).append("\n");
                }
                sb.append("  spanshPredictedGenera: ").append(cb.spanshPredictedGenera != null && !cb.spanshPredictedGenera.isEmpty() ? String.join(", ", cb.spanshPredictedGenera) : "—").append("\n");
                sb.append("  spanshExcludeFromExobiology: ").append(cb.spanshExcludeFromExobiology != null ? cb.spanshExcludeFromExobiology : "—").append("\n");
            } else {
                sb.append("  No Spansh data\n");
            }
            sb.append("Predictions:\n");
            if (cb.predictions != null && !cb.predictions.isEmpty()) {
                boolean firstBonus = FirstBonusHelper.firstBonusApplies(cb);
                for (BioCandidate bc : cb.predictions) {
                    if (bc == null) continue;
                    long payout = bc.baseValue != null ? bc.getEstimatedPayout(firstBonus) : 0L;
                    sb.append("  ").append(str(bc.getGenus())).append(" ").append(str(bc.getSpecies())).append("  baseValue=").append(bc.baseValue != null ? bc.baseValue : "—").append("  estimatedPayout=").append(payout).append("  score=").append(bc.getScore()).append("\n");
                }
            } else {
                sb.append("  No predictions\n");
            }
        }
        return sb.toString();
    }

    private static String str(Object o) {
        if (o == null) return "—";
        String s = o.toString().trim();
        return s.isEmpty() ? "—" : s;
    }

    /**
     * One Spansh bodies/search call for the whole sphere; returns bodies grouped by system name.
     * Reduces N×EDSM showBodies to 1 Spansh query when the response contains enough body data.
     */
    private Map<String, List<BodiesResponse.Body>> fetchSpanshBodiesInSphere(String centerName, int radiusLy) {
        try {
            String json = spanshClient.queryBodiesSearch(centerName, (double) radiusLy, 2000);
            if (json == null || json.isBlank()) {
                return null;
            }
            return parseSpanshBodiesSearchBySystem(json);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse Spansh POST /api/bodies/search response into system name -> list of bodies.
     * Handles both flat result objects and "record" wrapper; tries snake_case and camelCase field names.
     */
    private static Map<String, List<BodiesResponse.Body>> parseSpanshBodiesSearchBySystem(String json) {
        Map<String, List<BodiesResponse.Body>> bySystem = new HashMap<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("results") || !root.get("results").isJsonArray()) {
                return bySystem;
            }
            JsonArray results = root.getAsJsonArray("results");
            for (JsonElement el : results) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject o = el.getAsJsonObject();
                JsonObject rec = o.has("record") && o.get("record").isJsonObject() ? o.get("record").getAsJsonObject() : o;
                String systemName = getStr(rec, "system_name", "systemName");
                String name = getStr(rec, "name");
                if (systemName == null || name == null || systemName.isBlank()) {
                    continue;
                }
                Boolean isLandable = getBool(rec, "is_landable", "isLandable");
                if (isLandable == null || !isLandable) {
                    continue;
                }
                BodiesResponse.Body body = new BodiesResponse.Body();
                body.name = name;
                body.type = getStr(rec, "type");
                body.subType = getStr(rec, "sub_type", "subType");
                body.atmosphereType = getStr(rec, "atmosphere_type", "atmosphereType");
                body.isLandable = Boolean.TRUE;
                body.gravity = getDouble(rec, "gravity", "surface_gravity");
                body.surfaceTemperature = getDouble(rec, "surface_temperature", "surfaceTemperature");
                body.volcanismType = getStr(rec, "volcanism_type", "volcanismType");
                if (rec.has("rings") && rec.get("rings").isJsonArray()) {
                    List<BodiesResponse.Body.Ring> rings = new ArrayList<>();
                    for (JsonElement re : rec.getAsJsonArray("rings")) {
                        if (re.isJsonObject()) {
                            String rType = getStr(re.getAsJsonObject(), "type");
                            if (rType != null && !rType.isBlank()) {
                                BodiesResponse.Body.Ring r = new BodiesResponse.Body.Ring();
                                r.type = rType;
                                rings.add(r);
                            }
                        }
                    }
                    if (!rings.isEmpty()) {
                        body.rings = rings;
                    }
                }
                bySystem.computeIfAbsent(systemName.trim(), k -> new ArrayList<>()).add(body);
            }
        } catch (Exception e) {
            return new HashMap<>();
        }
        return bySystem;
    }

    private static String getStr(JsonObject o, String... keys) {
        for (String k : keys) {
            if (o.has(k) && !o.get(k).isJsonNull()) {
                return o.get(k).getAsString();
            }
        }
        return null;
    }

    private static Double getDouble(JsonObject o, String... keys) {
        for (String k : keys) {
            if (o.has(k) && !o.get(k).isJsonNull()) {
                try {
                    return Double.valueOf(o.get(k).getAsDouble());
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static Boolean getBool(JsonObject o, String... keys) {
        for (String k : keys) {
            if (o.has(k) && !o.get(k).isJsonNull()) {
                try {
                    return Boolean.valueOf(o.get(k).getAsBoolean());
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static boolean isExcluded(CachedBody cb) {
        if (Boolean.TRUE.equals(cb.wasFootfalled)) {
            return true;
        }
        if (cb.bioSampleCountsByDisplayName != null && !cb.bioSampleCountsByDisplayName.isEmpty()) {
            return true;
        }
        return false;
    }

    private static boolean hasAtmosphere(CachedBody cb) {
        String a = cb.atmosphere != null ? cb.atmosphere : (cb.atmoOrType != null ? cb.atmoOrType : "");
        return hasAtmosphereString(a);
    }

    private static boolean hasAtmosphereEdsm(BodiesResponse.Body b) {
        String a = b.atmosphereType != null ? b.atmosphereType : "";
        return hasAtmosphereString(a);
    }

    /**
     * Build a list of CachedBody from an EDSM/Spansh BodiesResponse so we can store it in SystemCache.
     * Next time the Nearby tab runs, those systems will be "from cache" and we won't re-query EDSM.
     */
    private static List<CachedBody> buildCachedBodiesFromResponse(BodiesResponse bodiesResp, double[] starPos) {
        if (bodiesResp == null || bodiesResp.bodies == null || bodiesResp.bodies.isEmpty()) {
            return new ArrayList<>();
        }
        String systemName = bodiesResp.name != null ? bodiesResp.name : "";
        List<CachedBody> out = new ArrayList<>(bodiesResp.bodies.size());
        for (BodiesResponse.Body b : bodiesResp.bodies) {
            if (b == null || b.name == null) continue;
            CachedBody cb = new CachedBody();
            cb.name = b.name;
            cb.starSystem = systemName;
            cb.starPos = starPos != null && starPos.length >= 3 ? starPos : new double[]{0, 0, 0};
            long id = b.id;
            cb.bodyId = (id >= Integer.MIN_VALUE && id <= Integer.MAX_VALUE) ? (int) id : -1;
            cb.distanceLs = b.distanceToArrival != null ? b.distanceToArrival : 0;
            cb.landable = Boolean.TRUE.equals(b.isLandable);
            cb.atmosphere = b.atmosphereType;
            cb.atmoOrType = b.atmosphereType;
            cb.planetClass = b.subType;
            cb.volcanism = b.volcanismType;
            cb.surfaceTempK = b.surfaceTemperature;
            cb.orbitalPeriod = b.orbitalPeriod;
            cb.surfacePressure = b.getSurfacePressure();
            if (b.surfaceGravity != null) {
                cb.gravityMS = b.surfaceGravity;
            } else if (b.gravity != null) {
                cb.gravityMS = b.gravity;
            }
            if (b.rings != null && !b.rings.isEmpty()) {
                List<String> rts = new ArrayList<>();
                for (BodiesResponse.Body.Ring r : b.rings) {
                    if (r != null && r.type != null && !r.type.trim().isEmpty()) {
                        rts.add(r.type.trim());
                    }
                }
                if (!rts.isEmpty()) {
                    cb.ringTypes = rts;
                }
            }
            out.add(cb);
        }
        return out;
    }

    /** Derive genus/subtype names from Spansh landmarks for display. Kept separate from our predictions. */
    private static List<String> deriveSpanshPredictedGenera(List<SpanshLandmark> landmarks) {
        if (landmarks == null || landmarks.isEmpty()) {
            return null;
        }
        Set<String> genera = new LinkedHashSet<>();
        for (SpanshLandmark lm : landmarks) {
            if (lm == null) continue;
            String type = lm.getType();
            if (type != null && !type.trim().isEmpty()) {
                genera.add(type.trim());
            }
            String subtype = lm.getSubtype();
            if (subtype != null && !subtype.trim().isEmpty()) {
                genera.add(subtype.trim());
            }
        }
        return genera.isEmpty() ? null : new ArrayList<>(genera);
    }

    private static boolean hasAtmosphereString(String a) {
        if (a == null || a.trim().isEmpty()) {
            return false;
        }
        String lower = a.toLowerCase(Locale.ROOT);
        return !lower.equals("none") && !lower.contains("no atmosphere");
    }

    private static List<BioCandidate> predictFromCachedBody(CachedBody cb, double[] starPos, String systemName) {
        if (starPos == null || starPos.length < 3) {
            starPos = new double[]{0, 0, 0};
        }
        PlanetType pt = ExobiologyData.parsePlanetType(cb.planetClass);
        String atmoRaw = cb.atmoOrType != null ? cb.atmoOrType : (cb.atmosphere != null ? cb.atmosphere : "");
        AtmosphereType at = ExobiologyData.parseAtmosphere(atmoRaw);
        // Cache may store EDSM gravity (g, typically 0.04–2) or surfaceGravity (m/s², typically 0.4–30). BodyAttributes expects g.
        double gravityG = 0.0;
        if (cb.gravityMS != null && !Double.isNaN(cb.gravityMS) && cb.gravityMS > 0) {
            gravityG = (cb.gravityMS <= 2.5) ? cb.gravityMS : (cb.gravityMS / 9.80665);
        }
        double gravity = gravityG;
        double tempK = (cb.surfaceTempK != null && !Double.isNaN(cb.surfaceTempK))
                ? cb.surfaceTempK : 0.0;
        boolean hasVolc = cb.volcanism != null && !cb.volcanism.isEmpty()
                && !cb.volcanism.toLowerCase(Locale.ROOT).startsWith("no volcanism");
        BodyAttributes attrs = new BodyAttributes(
                cb.name,
                systemName != null ? systemName : (cb.starSystem != null ? cb.starSystem : ""),
                starPos,
                pt,
                gravity,
                at,
                tempK,
                tempK,
                cb.surfacePressure,
                hasVolc,
                cb.volcanism
        );
        List<BioCandidate> pred = ExobiologyData.predict(attrs);
        return pred != null ? pred : Collections.emptyList();
    }

    private static List<BioCandidate> predictFromEdsmBody(BodiesResponse.Body b, BodiesResponse bodies, double[] starPos) {
        double x = 0, y = 0, z = 0;
        if (bodies.coords != null) {
            x = bodies.coords.x != null ? bodies.coords.x : 0;
            y = bodies.coords.y != null ? bodies.coords.y : 0;
            z = bodies.coords.z != null ? bodies.coords.z : 0;
        }
        double[] coords = new double[]{x, y, z};
        PlanetType pt = ExobiologyData.parsePlanetType(b.subType);
        String atmoRaw = b.atmosphereType != null ? b.atmosphereType : "";
        AtmosphereType at = ExobiologyData.parseAtmosphere(atmoRaw);
        double gravity = b.gravity != null ? b.gravity : 0.0;
        double tempK = b.surfaceTemperature != null ? b.surfaceTemperature : 0.0;
        boolean hasVolc = b.volcanismType != null && !b.volcanismType.isEmpty()
                && !b.volcanismType.toLowerCase(Locale.ROOT).startsWith("no volcanism");
        BodyAttributes attrs = new BodyAttributes(
                b.name,
                bodies.name != null ? bodies.name : "",
                coords,
                pt,
                gravity,
                at,
                tempK,
                tempK,
                b.getSurfacePressure(),
                hasVolc,
                b.volcanismType
        );
        List<BioCandidate> pred = ExobiologyData.predict(attrs);
        return pred != null ? pred : Collections.emptyList();
    }

    private static final class BodyValue {
        final String bodyName;
        final long valueCr;

        BodyValue(String bodyName, long valueCr) {
            this.bodyName = bodyName;
            this.valueCr = valueCr;
        }
    }

    /** Result of processing one system in a worker thread. cachePayload is non-null only for uncached systems (main thread will call cache.put). */
    private static final class TaskResult {
        final Object[] row;
        final CachePayload cachePayload;
        final boolean fromCache;

        TaskResult(Object[] row, CachePayload cachePayload, boolean fromCache) {
            this.row = row;
            this.cachePayload = cachePayload;
            this.fromCache = fromCache;
        }
    }

    /** Data to store in cache; only applied on the main worker thread. */
    private static final class CachePayload {
        final String systemName;
        final double[] starPos;
        final int bodyCount;
        final List<CachedBody> cachedBodies;

        CachePayload(String systemName, double[] starPos, int bodyCount, List<CachedBody> cachedBodies) {
            this.systemName = systemName;
            this.starPos = starPos;
            this.bodyCount = bodyCount;
            this.cachedBodies = cachedBodies;
        }
    }

    /**
     * Process one system (cache hit or EDSM/Spansh fetch). Called from worker threads.
     * Does not call cache.put; returns CachePayload for the main thread to store.
     */
    private static TaskResult processOneSystem(
            SphereSystemsResponse sys,
            SystemCache cache,
            Map<String, List<BodiesResponse.Body>> bodiesBySystemFromSpansh,
            EdsmClient edsmClient) {
        List<BodyValue> bodyValues = new ArrayList<>();
        Map<String, Long> genusToMaxValue = new HashMap<>();
        Set<String> ringTypes = new LinkedHashSet<>();
        CachedSystem cs = cache.get(0L, sys.name);
        if (cs != null && cs.bodies != null) {
            double[] starPos = cs.starPos != null ? cs.starPos : new double[3];
            if (starPos.length >= 3 && starPos[0] == 0 && starPos[1] == 0 && starPos[2] == 0 && sys.coords != null) {
                starPos = new double[]{sys.coords.x, sys.coords.y, sys.coords.z};
            }
            for (CachedBody cb : cs.bodies) {
                if (cb.ringTypes != null) {
                    ringTypes.addAll(cb.ringTypes);
                }
            }
            for (CachedBody cb : cs.bodies) {
                if (!cb.landable) continue;
                if (isExcluded(cb)) continue;
                if (!hasAtmosphere(cb)) continue;
                List<BioCandidate> preds = cb.predictions != null && !cb.predictions.isEmpty()
                        ? cb.predictions : predictFromCachedBody(cb, starPos, cs.systemName);
                if (preds == null || preds.isEmpty()) continue;
                if (!Boolean.TRUE.equals(cb.wasFootfalled) && cb.spanshLandmarks == null) {
                    SpanshBodyExobiologyInfo info = SpanshLandmarkCache.getInstance().getOrFetch(cs.systemName, cb.name);
                    if (info != null) {
                        cb.spanshLandmarks = info.getLandmarks();
                        cb.spanshExcludeFromExobiology = info.isExcludeFromExobiology();
                    }
                }
                if (Boolean.TRUE.equals(cb.spanshExcludeFromExobiology)) continue;
                boolean firstBonus = FirstBonusHelper.firstBonusApplies(cb);
                long maxVal = 0;
                for (BioCandidate bc : preds) {
                    if (bc != null) {
                        String genus = bc.getGenus();
                        if (genus != null && !genus.isEmpty()) {
                            long v = bc.getEstimatedPayout(firstBonus);
                            genusToMaxValue.merge(genus, v, Long::max);
                            if (v > maxVal) maxVal = v;
                        }
                    }
                }
                if (cb.spanshPredictedGenera != null) {
                    for (String g : cb.spanshPredictedGenera) {
                        if (g != null && !g.isEmpty()) genusToMaxValue.putIfAbsent(g, 0L);
                    }
                }
                if (maxVal > 0) bodyValues.add(new BodyValue(cb.name, maxVal));
            }
            Object[] row = buildSystemRow(sys.name, bodyValues, genusToMaxValue, ringTypes, sys.distance);
            return new TaskResult(row, null, true);
        }
        // Uncached: fetch and build row + cache payload (no cache.put here)
        try {
            BodiesResponse bodiesResp = null;
            List<BodiesResponse.Body> spanshBodies = bodiesBySystemFromSpansh != null ? bodiesBySystemFromSpansh.get(sys.name) : null;
            if (spanshBodies != null && !spanshBodies.isEmpty() && sys.coords != null) {
                bodiesResp = new BodiesResponse();
                bodiesResp.name = sys.name;
                bodiesResp.coords = new BodiesResponse.Coords();
                bodiesResp.coords.x = Double.valueOf(sys.coords.x);
                bodiesResp.coords.y = Double.valueOf(sys.coords.y);
                bodiesResp.coords.z = Double.valueOf(sys.coords.z);
                bodiesResp.bodies = spanshBodies;
            }
            if (bodiesResp == null) {
                bodiesResp = edsmClient.showBodies(sys.name);
            }
            if (bodiesResp == null || bodiesResp.bodies == null) {
                return new TaskResult(null, null, false);
            }
            double x = 0, y = 0, z = 0;
            if (bodiesResp.coords != null) {
                x = bodiesResp.coords.x != null ? bodiesResp.coords.x : 0;
                y = bodiesResp.coords.y != null ? bodiesResp.coords.y : 0;
                z = bodiesResp.coords.z != null ? bodiesResp.coords.z : 0;
            }
            double[] starPos = new double[]{x, y, z};
            for (BodiesResponse.Body b : bodiesResp.bodies) {
                if (b != null && b.rings != null && !b.rings.isEmpty()) {
                    for (BodiesResponse.Body.Ring r : b.rings) {
                        if (r != null && r.type != null && !r.type.trim().isEmpty())
                            ringTypes.add(r.type.trim());
                    }
                }
                if (b == null || b.isLandable == null || !b.isLandable) continue;
                if (!hasAtmosphereEdsm(b)) continue;
                List<BioCandidate> preds = predictFromEdsmBody(b, bodiesResp, starPos);
                if (preds == null || preds.isEmpty()) continue;
                SpanshBodyExobiologyInfo info = SpanshLandmarkCache.getInstance().getOrFetch(sys.name, b.name);
                if (info != null && info.isExcludeFromExobiology()) continue;
                boolean firstBonus = FirstBonusHelper.firstBonusApplies(null, info != null ? info.getLandmarks() : null);
                long maxVal = 0;
                for (BioCandidate bc : preds) {
                    if (bc != null) {
                        String genus = bc.getGenus();
                        if (genus != null && !genus.isEmpty()) {
                            long v = bc.getEstimatedPayout(firstBonus);
                            genusToMaxValue.merge(genus, v, Long::max);
                            if (v > maxVal) maxVal = v;
                        }
                    }
                }
                if (maxVal > 0) bodyValues.add(new BodyValue(b.name, maxVal));
            }
            BodiesResponse bodiesForCache = bodiesResp;
            double[] starPosForCache = starPos;
            if (spanshBodies != null && !spanshBodies.isEmpty() && bodiesResp.bodies == spanshBodies) {
                BodiesResponse edsmForCache = edsmClient.showBodies(sys.name);
                if (edsmForCache != null && edsmForCache.bodies != null && !edsmForCache.bodies.isEmpty()) {
                    bodiesForCache = edsmForCache;
                    if (edsmForCache.coords != null) {
                        double ex = edsmForCache.coords.x != null ? edsmForCache.coords.x : 0;
                        double ey = edsmForCache.coords.y != null ? edsmForCache.coords.y : 0;
                        double ez = edsmForCache.coords.z != null ? edsmForCache.coords.z : 0;
                        starPosForCache = new double[]{ex, ey, ez};
                    }
                }
            }
            List<CachedBody> cachedBodies = buildCachedBodiesFromResponse(bodiesForCache, starPosForCache);
            for (CachedBody cb : cachedBodies) {
                SpanshBodyExobiologyInfo info = SpanshLandmarkCache.getInstance().getOrFetch(sys.name, cb.name);
                if (info != null) {
                    cb.spanshLandmarks = info.getLandmarks();
                    cb.spanshExcludeFromExobiology = info.isExcludeFromExobiology();
                    cb.spanshPredictedGenera = deriveSpanshPredictedGenera(info.getLandmarks());
                }
                if (cb.landable && hasAtmosphere(cb)) {
                    List<BioCandidate> preds = predictFromCachedBody(cb, starPosForCache, sys.name);
                    if (preds != null && !preds.isEmpty()) cb.predictions = preds;
                }
            }
            Object[] row = buildSystemRow(sys.name, bodyValues, genusToMaxValue, ringTypes, sys.distance);
            CachePayload payload = !cachedBodies.isEmpty()
                    ? new CachePayload(sys.name, starPosForCache, bodiesForCache.bodies.size(), cachedBodies)
                    : null;
            return new TaskResult(row, payload, false);
        } catch (Exception e) {
            return new TaskResult(null, null, false);
        }
    }

    private static Object[] buildSystemRow(String systemName, List<BodyValue> bodyValues, Map<String, Long> genusToMaxValue, Set<String> ringTypes, double distanceLy) {
        boolean hasExo = !bodyValues.isEmpty();
        boolean hasRings = !ringTypes.isEmpty();
        if (!hasExo && !hasRings) return null;
        long systemTotal = 0;
        List<String> names = new ArrayList<>();
        String systemPrefix = (systemName != null && !systemName.isEmpty()) ? systemName.trim() + " " : null;
        for (BodyValue bv : bodyValues) {
            systemTotal += bv.valueCr;
            String displayName = bv.bodyName;
            if (systemPrefix != null && displayName != null && displayName.startsWith(systemPrefix))
                displayName = displayName.substring(systemPrefix.length()).trim();
            names.add(displayName != null ? displayName : "");
        }
        String planetsCol = bodyValues.isEmpty() ? "—" : String.join(", ", names);
        String exobiologyCol;
        if (genusToMaxValue == null || genusToMaxValue.isEmpty()) {
            exobiologyCol = "—";
        } else {
            List<String> generaByValue = new ArrayList<>(genusToMaxValue.keySet());
            generaByValue.sort(Comparator.<String>comparingLong(genusToMaxValue::get).reversed().thenComparing(Comparator.naturalOrder()));
            exobiologyCol = String.join(", ", generaByValue);
        }
        String ringsCol = ringTypes.isEmpty() ? "—" : String.join(", ", ringTypes);
        String valueCol = bodyValues.isEmpty() ? "—" : String.format(Locale.ROOT, "%,d", systemTotal);
        return new Object[]{
                systemName,
                planetsCol,
                exobiologyCol,
                ringsCol,
                valueCol,
                Long.valueOf(systemTotal),
                Double.valueOf(distanceLy)
        };
    }

    public void applyOverlayBackground(Color bg) {
        if (bg == null) {
            bg = EdoUi.Internal.TRANSPARENT;
        }
        boolean opaque = bg.getAlpha() >= 255;
        setOpaque(opaque);
        setBackground(bg);
        table.setOpaque(false);
        table.setBackground(EdoUi.Internal.TRANSPARENT);
        table.getTableHeader().setOpaque(!opaque);
        if (!opaque) {
            table.getTableHeader().setBackground(EdoUi.Internal.TRANSPARENT);
        } else {
            table.getTableHeader().setBackground(EdoUi.User.BACKGROUND);
        }
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getViewport().setBackground(EdoUi.Internal.TRANSPARENT);
        headerLabel.setOpaque(false);
        headerLabel.setBackground(EdoUi.Internal.TRANSPARENT);
        if (progressPanel != null) progressPanel.setOpaque(false);
        if (progressLabel != null) progressLabel.setOpaque(false);
        repaint();
    }

    public void applyUiFont(Font font) {
        if (font != null && headerLabel != null) {
            headerLabel.setFont(font.deriveFont(Font.BOLD, font.getSize() + 2));
        }
        if (progressLabel != null && font != null) {
            progressLabel.setFont(font);
        }
        if (table != null) {
            table.setFont(font != null ? font : table.getFont());
            table.getTableHeader().setFont(font != null ? font.deriveFont(Font.BOLD) : table.getTableHeader().getFont());
        }
        revalidate();
        repaint();
    }

    public void applyUiFontPreferences() {
        Font font = OverlayPreferences.getUiFont();
        applyUiFont(font);
    }
}
