package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

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
import org.dce.ed.util.EdsmClient;
import org.dce.ed.util.FirstBonusHelper;
import org.dce.ed.util.SpanshLandmark;
import org.dce.ed.util.SpanshLandmarkCache;

/**
 * Nearby tab: sphere search around current system, exobiology prediction on landable planets,
 * table of high-value systems. Respects overlay color and transparency (pass-through mode).
 */
public class NearbyTabPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final SystemTabPanel systemTabPanel;
    private final EdsmClient edsmClient = new EdsmClient();

    private final JLabel headerLabel;
    private final JPanel progressPanel;
    private final JLabel progressLabel;
    private final javax.swing.JProgressBar progressBar;
    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JScrollPane scrollPane;

    private final AtomicBoolean firstShowDone = new AtomicBoolean(false);
    private volatile boolean refreshRequested;

    public NearbyTabPanel(SystemTabPanel systemTabPanel) {
        this.systemTabPanel = systemTabPanel;

        setLayout(new BorderLayout());
        setOpaque(false);
        setBackground(EdoUi.Internal.TRANSPARENT);

        headerLabel = new JLabel("Nearby (exobiology)");
        headerLabel.setForeground(EdoUi.User.MAIN_TEXT);
        headerLabel.setHorizontalAlignment(SwingConstants.LEFT);
        headerLabel.setOpaque(false);
        Font base = OverlayPreferences.getUiFont();
        headerLabel.setFont(base.deriveFont(Font.BOLD, OverlayPreferences.getUiFontSize() + 2));

        tableModel = new DefaultTableModel(new Object[]{"System", "Planets", "Exobiology", "Rings", "Est. exobiology (cr)", "ValueCr"}, 0) {
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
        table.getTableHeader().setForeground(EdoUi.User.MAIN_TEXT);
        table.getTableHeader().setBackground(EdoUi.User.BACKGROUND);
        table.getColumnModel().getColumn(5).setMinWidth(0);
        table.getColumnModel().getColumn(5).setMaxWidth(0);
        table.getColumnModel().getColumn(5).setWidth(0);
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
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getViewport().setBackground(EdoUi.Internal.TRANSPARENT);
        scrollPane.setBorder(null);
        scrollPane.setViewportBorder(null);

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
                tableModel.addRow(new Object[]{"—", "No current system", "—", "—", "—", Long.valueOf(0L)});
            });
            return;
        }

        int radiusLy = OverlayPreferences.getNearbySphereRadiusLy();
        long minValueCr = (long) (OverlayPreferences.getNearbyMinValueMillionCredits() * 1_000_000);

        final String finalCenterName = centerName;

        SwingWorker<List<Object[]>, int[]> worker = new SwingWorker<List<Object[]>, int[]>() {
            @Override
            protected List<Object[]> doInBackground() throws Exception {
                List<Object[]> rows = new ArrayList<>();
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
                    final int totalSystems = systems.length;
                    SystemCache cache = SystemCache.getInstance();
                    int sysIndex = 0;
                    for (SphereSystemsResponse sys : systems) {
                        if (sys == null || sys.name == null || sys.name.isEmpty()) {
                            continue;
                        }
                        sysIndex++;
                        setProgress(totalSystems > 0 ? (int) (100.0 * sysIndex / totalSystems) : 0);
                        publish(new int[]{sysIndex, totalSystems});
                        CachedSystem cs = cache.get(0L, sys.name);
                        List<BodyValue> bodyValues = new ArrayList<>();
                        Set<String> predictedGenera = new LinkedHashSet<>();
                        Set<String> ringTypes = new LinkedHashSet<>();
                        if (cs != null && cs.bodies != null) {
                            double[] starPos = cs.starPos != null ? cs.starPos : new double[3];
                            for (CachedBody cb : cs.bodies) {
                                if (!cb.landable) {
                                    continue;
                                }
                                if (isExcluded(cb)) {
                                    continue;
                                }
                                if (!hasAtmosphere(cb)) {
                                    continue;
                                }
                                List<BioCandidate> preds = cb.predictions != null && !cb.predictions.isEmpty()
                                        ? cb.predictions
                                        : predictFromCachedBody(cb, starPos, cs.systemName);
                                if (preds == null || preds.isEmpty()) {
                                    continue;
                                }
                                if (!Boolean.TRUE.equals(cb.wasFootfalled) && cb.spanshLandmarks == null) {
                                    List<SpanshLandmark> landmarks = SpanshLandmarkCache.getInstance().getOrFetch(cs.systemName, cb.name);
                                    if (landmarks != null) {
                                        cb.spanshLandmarks = landmarks;
                                    }
                                }
                                boolean firstBonus = FirstBonusHelper.firstBonusApplies(cb);
                                long maxVal = 0;
                                for (BioCandidate bc : preds) {
                                    if (bc != null) {
                                        if (bc.getGenus() != null && !bc.getGenus().isEmpty()) {
                                            predictedGenera.add(bc.getGenus());
                                        }
                                        long v = bc.getEstimatedPayout(firstBonus);
                                        if (v > maxVal) {
                                            maxVal = v;
                                        }
                                    }
                                }
                                if (maxVal > 0) {
                                    bodyValues.add(new BodyValue(cb.name, maxVal));
                                }
                            }
                        } else {
                            try {
                                BodiesResponse bodiesResp = edsmClient.showBodies(sys.name);
                                if (bodiesResp != null && bodiesResp.bodies != null) {
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
                                                if (r != null && r.type != null && !r.type.trim().isEmpty()) {
                                                    ringTypes.add(r.type.trim());
                                                }
                                            }
                                        }
                                        if (b == null || b.isLandable == null || !b.isLandable) {
                                            continue;
                                        }
                                        if (!hasAtmosphereEdsm(b)) {
                                            continue;
                                        }
                                        List<BioCandidate> preds = predictFromEdsmBody(b, bodiesResp, starPos);
                                        if (preds == null || preds.isEmpty()) {
                                            continue;
                                        }
                                        List<SpanshLandmark> landmarks = SpanshLandmarkCache.getInstance().getOrFetch(sys.name, b.name);
                                        boolean firstBonus = FirstBonusHelper.firstBonusApplies(null, landmarks);
                                        long maxVal = 0;
                                        for (BioCandidate bc : preds) {
                                            if (bc != null) {
                                                if (bc.getGenus() != null && !bc.getGenus().isEmpty()) {
                                                    predictedGenera.add(bc.getGenus());
                                                }
                                                long v = bc.getEstimatedPayout(firstBonus);
                                                if (v > maxVal) {
                                                    maxVal = v;
                                                }
                                            }
                                        }
                                        if (maxVal > 0) {
                                            bodyValues.add(new BodyValue(b.name, maxVal));
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                // skip this system
                            }
                        }
                        boolean hasExo = !bodyValues.isEmpty();
                        boolean hasRings = !ringTypes.isEmpty();
                        if (!hasExo && !hasRings) {
                            continue;
                        }
                        long systemTotal = 0;
                        List<String> names = new ArrayList<>();
                        for (BodyValue bv : bodyValues) {
                            systemTotal += bv.valueCr;
                            names.add(bv.bodyName);
                        }
                        String planetsCol = bodyValues.isEmpty() ? "—" : String.join(", ", names);
                        String exobiologyCol = predictedGenera.isEmpty() ? "—" : String.join(", ", predictedGenera);
                        String ringsCol = ringTypes.isEmpty() ? "—" : String.join(", ", ringTypes);
                        String valueCol = bodyValues.isEmpty() ? "—" : String.format(Locale.ROOT, "%,d", systemTotal);
                        rows.add(new Object[]{
                                sys.name,
                                planetsCol,
                                exobiologyCol,
                                ringsCol,
                                valueCol,
                                Long.valueOf(systemTotal)
                        });
                    }
                    setProgress(100);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                rows.sort(Comparator.comparingLong((Object[] row) -> ((Long) row[5]).longValue()).reversed());
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
                            tableModel.addRow(new Object[]{"—", "No systems with exobiology or rings in range", "—", "—", "—", Long.valueOf(0L)});
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
                        tableModel.addRow(new Object[]{"—", "Interrupted", "—", "—", "—", Long.valueOf(0L)});
                    });
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    final String msg = cause != null ? cause.getMessage() : e.getMessage();
                    SwingUtilities.invokeLater(() -> {
                        tableModel.setRowCount(0);
                        tableModel.addRow(new Object[]{"—", "Error: " + msg, "—", "—", "—", Long.valueOf(0L)});
                    });
                }
            }

            @Override
            protected void process(java.util.List<int[]> chunks) {
                if (chunks.isEmpty()) return;
                int[] last = chunks.get(chunks.size() - 1);
                int current = last[0];
                int total = last[1];
                int pct = total > 0 ? (int) (100.0 * current / total) : 0;
                progressBar.setValue(pct);
                progressBar.setString(pct + "%");
                progressLabel.setText("Scanning... " + current + " / " + total + " systems");
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
        double gravity = (cb.gravityMS != null && !Double.isNaN(cb.gravityMS)) ? cb.gravityMS / 9.80665 : 0.0;
        double tempK = cb.surfaceTempK != null ? cb.surfaceTempK : 0.0;
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
