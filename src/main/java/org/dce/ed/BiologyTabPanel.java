package org.dce.ed;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

import org.dce.ed.exobiology.ExobiologyData.BioCandidate;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.BioScanPredictionEvent;
import org.dce.ed.logreader.event.SaasignalsFoundEvent;
import org.dce.ed.logreader.event.ScanOrganicEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.logreader.event.TouchdownEvent;
import org.dce.ed.exobiology.BioGenusSwitchRestorer;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.session.EdoSessionState.BiologyMapBookmarkEntry;
import org.dce.ed.session.EdoSessionState.BiologySrvMarkerEntry;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;
import org.dce.ed.util.FirstBonusHelper;
import org.dce.ed.util.SpanshBodyExobiologyInfo;
import org.dce.ed.util.SpanshLandmark;
import org.dce.ed.util.SpanshLandmarkCache;
import org.dce.ed.tts.PollyTtsCached;
import org.dce.ed.tts.TtsSprintf;
import org.dce.ed.ui.EdoMiningSplitPaneUi;
import org.dce.ed.ui.EdoUi;

public class BiologyTabPanel extends JPanel {

    private static final long serialVersionUID = 1L;



    private final JLabel header = new JLabel("ExoBio");
    private final BioTableModel model = new BioTableModel();
    private final JTable table = new JTable(model) {
        private static final long serialVersionUID = 1L;

        @Override
        protected void configureEnclosingScrollPane() {
            super.configureEnclosingScrollPane();
            Container p = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
            if (p instanceof JScrollPane sp) {
                sp.setBorder(null);
                sp.setViewportBorder(null);
            }
        }
    };
    private final JScrollPane scroll = new JScrollPane(table);

    private final BioMapPanel mapPanel = new BioMapPanel();

    private final JSplitPane tableMapSplit;

    private static final int REQUIRED_SAMPLES = 3;

    /** User bookmark pins on the biology map. */
    private static final Color BIO_MAP_BOOKMARK_FILL = new Color(255, 210, 50);
    private static final Color BIO_MAP_BOOKMARK_OUTLINE = new Color(0, 0, 0, 210);
    /** Biology map: lines to the active incomplete species (matches in-game “current scan” emphasis). */
    private static final Color BIO_MAP_ACTIVE_SAMPLE = new Color(0x40, 0xE0, 0x70);
    /** Biology map: parked pins from a species left incomplete after switching genus (in-game purple cue). */
    private static final Color BIO_MAP_ABANDONED_SAMPLE = new Color(0xB8, 0x70, 0xE8);
    /** Fully sampled species (3/3): small light gray triangles on the map, no emphasis rays. */
    private static final Color BIO_MAP_COMPLETE_SAMPLE = new Color(200, 200, 205);
    private static final Color BIO_MAP_COMPLETE_SAMPLE_OUTLINE = new Color(150, 150, 158, 200);
    /** Parked pins for the genus currently being sampled again: useful history, but not current progress. */
    private static final Color BIO_MAP_RESUMED_GENUS_HISTORY = EdoUi.Internal.GRAY_180;
    /** Biology map: commander ship cue (heading-relative radar, points up). */
    private static final Color BIO_MAP_SHIP_FILL = new Color(220, 35, 35);
    private static final Color BIO_MAP_SHIP_DETAIL = new Color(255, 100, 100);
    private static final Color BIO_MAP_SHIP_OUTLINE = new Color(0, 0, 0, 200);
    private static final Color BIO_MAP_SHIP_RAY = new Color(150, 40, 40);
    /** Parked SRV (last fix while {@code inSrv}). */
    private static final Color BIO_MAP_SRV_FILL = new Color(60, 140, 255);
    private static final Color BIO_MAP_SRV_DETAIL = new Color(140, 190, 255);
    private static final Color BIO_MAP_SRV_OUTLINE = new Color(0, 0, 0, 200);
    private static final Color BIO_MAP_SRV_RAY = new Color(45, 95, 185);
    /** Genus / distance labels on sample rays (readable on dark map). */
    private static final Color BIO_MAP_RAY_LABEL = new Color(245, 245, 245);
    /** Lat/lon grid on the biology map (minor / origin / major). */
    private static final Color BIO_MAP_GRID_MINOR = new Color(255, 255, 255, 40);
    private static final Color BIO_MAP_GRID_ORIGIN = new Color(255, 255, 255, 75);
    private static final Color BIO_MAP_GRID_MAJOR = new Color(255, 255, 255, 110);
    /** After a Sample scan, skip "Entering clonal colony" (new sample point lands at your feet). */
    private static final EliteLogParser STATUS_SNAPSHOT_PARSER = new EliteLogParser();
    private static final long SUPPRESS_ENTER_AFTER_SAMPLE_MS = 5_000L;
    /** If distance to last sample drops by this much in one update and we end up near the point, treat as teleport / scan pin. */
    private static final double SUDDEN_DISTANCE_COLLAPSE_METERS = 55.0;
    /** "Near" the last sample point (meters) for sudden-collapse detection. */
    private static final double NEAR_LAST_SAMPLE_METERS = 25.0;

private static final class MovementSample {
    private final Instant t;
    private final double lat;
    private final double lon;
    private final double radiusM;

    private MovementSample(Instant t, double lat, double lon, double radiusM) {
        this.t = t;
        this.lat = lat;
        this.lon = lon;
        this.radiusM = radiusM;
    }
}

private final Deque<MovementSample> movement = new ArrayDeque<>();
private Double movementHeadingDeg; // 0=N, clockwise. null until we have enough movement.
/** Last travel direction on foot / in SRV — keeps map up while stationary. */
private Double lastFootTravelUpDeg;


    private SystemTabPanel systemTab;
    private Runnable sessionStateChangeCallback;

    private final TtsSprintf tts = new TtsSprintf(new PollyTtsCached());

    private Double currentLat;
    private Double currentLon;
    private Double currentPlanetRadius;
    private String currentBodyName;
    private Integer currentBodyId;
    /** Last commander mode from {@link StatusEvent} — classifies {@code Touchdown} when {@code PlayerControlled:false}. */
    private boolean commanderInSrv;
    private boolean lastCommanderAwayFromShip;

    /** Last surface fix while in the main ship (not on foot / SRV); map anchor when away from ship. */
    private Double parkedShipLat;
    private Double parkedShipLon;
    private Double parkedShipRadiusM;
    /** Ship nose heading (degrees, 0=N clockwise) when parked; Status {@code Heading} is radians. */
    private Double parkedShipHeadingDeg;

    /** Bodies for which genus-switch parking was replayed from journal this session. */
    private final java.util.Set<String> genusSwitchReplayBodies = new java.util.HashSet<>();

    /** Last surface fix while in the SRV; map anchor when back in ship or on foot. */
    private Double parkedSrvLat;
    private Double parkedSrvLon;
    private Double parkedSrvHeadingDeg;
    /** Per-body parked SRV markers (persisted in session). */
    private final java.util.Map<String, BioSrvMarker> parkedSrvByBodyToken = new java.util.LinkedHashMap<>();

    private final Map<String, Boolean> insideStateByBioKey = new HashMap<>();
    /** Previous great-circle distance (m) to the active row's last sample — for sudden-collapse detection. */
    private final Map<String, Double> lastDistMByBioKey = new HashMap<>();
    /** After a Sample scan for this bio, suppress "Entering" until this epoch ms. */
    private final Map<String, Long> suppressEnterUntilMsByBioKey = new HashMap<>();

    private final List<BioMapBookmark> mapBookmarks = new ArrayList<>();
    private long passThroughBookmarkHoverStartMs = -1L;
    private long passThroughZoomInHoverStartMs = -1L;
    private long passThroughZoomOutHoverStartMs = -1L;
    private long passThroughCompassHoverStartMs = -1L;
    private static final long PASS_THROUGH_MAP_CONTROL_DWELL_MS = 700L;

    public BiologyTabPanel() {
        super(new BorderLayout());
        setOpaque(false);

        header.setOpaque(false);
        header.setForeground(EdoUi.User.MAIN_TEXT);
        add(header, BorderLayout.NORTH);

        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.setShowGrid(false);
        table.setBorder(null);

        table.setOpaque(false);
        table.setBackground(EdoUi.Internal.TRANSPARENT);
        table.setDefaultRenderer(Object.class, new BioTextCellRenderer(model));

        // Columns: Bio | Credits | Min (m) | Samples
        table.getColumnModel().getColumn(0).setPreferredWidth(260); // Bio
        table.getColumnModel().getColumn(1).setPreferredWidth(110); // Credits
        table.getColumnModel().getColumn(2).setPreferredWidth(90);  // Min (m)
        table.getColumnModel().getColumn(3).setPreferredWidth(340); // Samples

        table.getColumnModel().getColumn(3).setCellRenderer(new SamplePillsRenderer());

        styleHeader(table);

        scroll.setBorder(null);
        scroll.setViewportBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getViewport().setBackground(EdoUi.Internal.TRANSPARENT);
        if (scroll.getColumnHeader() != null) {
            scroll.getColumnHeader().setBorder(null);
            scroll.getColumnHeader().setOpaque(false);
            scroll.getColumnHeader().setBackground(EdoUi.Internal.TRANSPARENT);
            scroll.getColumnHeader().setUI(org.dce.ed.ui.TransparentViewportUI.createUI(scroll.getColumnHeader()));
        }

        mapPanel.setOpaque(false);

        double tableSplitRatio = OverlayPreferences.getBiologyPanelTableSplitRatio();
        tableMapSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll, mapPanel);
        EdoMiningSplitPaneUi.install(tableMapSplit);
        configureBioTableMapSplit(tableMapSplit, tableSplitRatio);
        tableMapSplit.addPropertyChangeListener(evt -> {
            if (!JSplitPane.DIVIDER_LOCATION_PROPERTY.equals(evt.getPropertyName())) {
                return;
            }
            saveBioTableMapSplitRatio();
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (tableMapSplit == null || tableMapSplit.getHeight() < 32) {
                    return;
                }
                double ratio = OverlayPreferences.getBiologyPanelTableSplitRatio();
                tableMapSplit.setResizeWeight(ratio);
                tableMapSplit.setDividerLocation(ratio);
                EdoMiningSplitPaneUi.applyDividerTheme(tableMapSplit);
            }
        });

        add(tableMapSplit, BorderLayout.CENTER);
        setPreferredSize(new Dimension(560, 320));
    }

    private static void configureBioTableMapSplit(JSplitPane split, double resizeWeight) {
        split.setOpaque(false);
        split.setBorder(null);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(false);
        split.setDividerSize(9);
        split.setResizeWeight(Math.max(0.05, Math.min(0.95, resizeWeight)));
        split.setDividerLocation(resizeWeight);
    }

    private void saveBioTableMapSplitRatio() {
        if (tableMapSplit == null || tableMapSplit.getHeight() < 32) {
            return;
        }
        double ratio = computeVerticalSplitRatio(tableMapSplit);
        OverlayPreferences.setBiologyPanelTableSplitRatio(ratio);
        tableMapSplit.setResizeWeight(ratio);
    }

    private static double computeVerticalSplitRatio(JSplitPane split) {
        if (split == null) {
            return 0.42;
        }
        int h = split.getHeight();
        if (h <= 0) {
            return 0.42;
        }
        int d = split.getDividerSize();
        int usable = Math.max(1, h - d);
        int loc = split.getDividerLocation();
        double r = loc / (double) usable;
        return Math.max(0.05, Math.min(0.95, r));
    }

    public void setSystemTabPanel(SystemTabPanel systemTab) {
        this.systemTab = systemTab;
    }

    public void setSessionStateChangeCallback(Runnable callback) {
        this.sessionStateChangeCallback = callback;
    }

    public void fillSessionState(EdoSessionState state) {
        if (state == null) {
            return;
        }
        state.setBiologyParkedShipLat(parkedShipLat);
        state.setBiologyParkedShipLon(parkedShipLon);
        state.setBiologyParkedShipRadiusM(parkedShipRadiusM);
        state.setBiologyParkedShipHeadingDeg(parkedShipHeadingDeg);
        state.setBiologyParkedShipBodyName(currentBodyName);
        state.setBiologyParkedShipBodyId(currentBodyId);
        saveSrvMarkerForBody(currentBodyName, currentBodyId);
        if (parkedSrvByBodyToken.isEmpty()) {
            state.setBiologyParkedSrvMarkers(null);
            state.setBiologyParkedSrvLat(null);
            state.setBiologyParkedSrvLon(null);
            state.setBiologyParkedSrvHeadingDeg(null);
        } else {
            List<BiologySrvMarkerEntry> srvOut = new ArrayList<>(parkedSrvByBodyToken.size());
            for (BioSrvMarker m : parkedSrvByBodyToken.values()) {
                srvOut.add(m.toSessionEntry());
            }
            state.setBiologyParkedSrvMarkers(srvOut);
            state.setBiologyParkedSrvLat(parkedSrvLat);
            state.setBiologyParkedSrvLon(parkedSrvLon);
            state.setBiologyParkedSrvHeadingDeg(parkedSrvHeadingDeg);
        }
        if (mapBookmarks.isEmpty()) {
            state.setBiologyMapBookmarks(null);
        } else {
            List<BiologyMapBookmarkEntry> out = new ArrayList<>(mapBookmarks.size());
            for (BioMapBookmark b : mapBookmarks) {
                out.add(b.toSessionEntry());
            }
            state.setBiologyMapBookmarks(out);
        }
    }

    public void applySessionState(EdoSessionState state) {
        if (state == null) {
            return;
        }
        parkedShipLat = state.getBiologyParkedShipLat();
        parkedShipLon = state.getBiologyParkedShipLon();
        parkedShipRadiusM = state.getBiologyParkedShipRadiusM();
        parkedShipHeadingDeg = state.getBiologyParkedShipHeadingDeg();
        if (state.getBiologyParkedShipBodyName() != null && !state.getBiologyParkedShipBodyName().isBlank()) {
            currentBodyName = state.getBiologyParkedShipBodyName();
        }
        currentBodyId = state.getBiologyParkedShipBodyId();
        parkedSrvByBodyToken.clear();
        List<BiologySrvMarkerEntry> srvMarkers = state.getBiologyParkedSrvMarkers();
        if (srvMarkers != null) {
            for (BiologySrvMarkerEntry e : srvMarkers) {
                if (e == null) {
                    continue;
                }
                BioSrvMarker m = BioSrvMarker.fromSessionEntry(e);
                parkedSrvByBodyToken.put(bodyToken(m.bodyName, m.bodyId), m);
            }
        }
        if (parkedSrvByBodyToken.isEmpty()
                && state.getBiologyParkedSrvLat() != null
                && state.getBiologyParkedSrvLon() != null) {
            String legacyBody = state.getBiologyParkedShipBodyName();
            Integer legacyBodyId = state.getBiologyParkedShipBodyId();
            BioSrvMarker legacy = new BioSrvMarker(
                    legacyBody,
                    legacyBodyId,
                    state.getBiologyParkedSrvLat().doubleValue(),
                    state.getBiologyParkedSrvLon().doubleValue(),
                    state.getBiologyParkedSrvHeadingDeg());
            parkedSrvByBodyToken.put(bodyToken(legacyBody, legacyBodyId), legacy);
        }
        loadSrvMarkerForBody(currentBodyName, currentBodyId);
        mapBookmarks.clear();
        List<BiologyMapBookmarkEntry> restored = state.getBiologyMapBookmarks();
        if (restored != null) {
            for (BiologyMapBookmarkEntry e : restored) {
                if (e == null) {
                    continue;
                }
                mapBookmarks.add(BioMapBookmark.fromSessionEntry(e));
            }
        }
        syncMapBookmarksToPanel();
        syncMapToPanel();
        tryRestoreParkedShipFromJournal();
        refreshTableForCurrentBody(false);
    }

    private void notifySessionStateChanged() {
        if (sessionStateChangeCallback != null) {
            sessionStateChangeCallback.run();
        }
    }

    /** Selective mouse mode: ExoBio map control hit rects (compass, bookmark, zoom ±). */
    public boolean isPointerOverInteractiveRegion(Point screenPoint) {
        if (screenPoint == null || !isShowing() || mapPanel == null) {
            return false;
        }
        int x = screenPoint.x;
        int y = screenPoint.y;
        return mapPanel.isCompassAtScreen(x, y)
                || mapPanel.isBookmarkButtonAtScreen(x, y)
                || mapPanel.isZoomInButtonAtScreen(x, y)
                || mapPanel.isZoomOutButtonAtScreen(x, y);
    }

    /**
     * Pass-through mode: dwell over map controls (compass / bookmark / zoom), same timing as title-bar toggle.
     *
     * @return {@code true} while the pointer is over one of those controls
     */
    public boolean applyPassThroughMapControlsAtScreen(int screenX, int screenY) {
        if (!isShowing()) {
            resetPassThroughMapControlsHover();
            return false;
        }
        long now = System.currentTimeMillis();
        if (mapPanel.isCompassAtScreen(screenX, screenY)) {
            resetPassThroughBookmarkHoverOnly();
            passThroughZoomInHoverStartMs = -1L;
            passThroughZoomOutHoverStartMs = -1L;
            mapPanel.setCompassButtonHovered(true);
            if (passThroughCompassHoverStartMs < 0L) {
                passThroughCompassHoverStartMs = now;
            } else if (now - passThroughCompassHoverStartMs >= PASS_THROUGH_MAP_CONTROL_DWELL_MS) {
                mapPanel.toggleHeadingUpMode();
                passThroughCompassHoverStartMs = -1L;
            }
            return true;
        }
        passThroughCompassHoverStartMs = -1L;
        mapPanel.setCompassButtonHovered(false);
        if (mapPanel.isZoomInButtonAtScreen(screenX, screenY)) {
            resetPassThroughBookmarkHoverOnly();
            if (passThroughZoomInHoverStartMs < 0L) {
                passThroughZoomInHoverStartMs = now;
            } else if (now - passThroughZoomInHoverStartMs >= PASS_THROUGH_MAP_CONTROL_DWELL_MS) {
                mapPanel.adjustMapZoom(true);
                passThroughZoomInHoverStartMs = -1L;
            }
            passThroughZoomOutHoverStartMs = -1L;
            return true;
        }
        passThroughZoomInHoverStartMs = -1L;
        if (mapPanel.isZoomOutButtonAtScreen(screenX, screenY)) {
            resetPassThroughBookmarkHoverOnly();
            if (passThroughZoomOutHoverStartMs < 0L) {
                passThroughZoomOutHoverStartMs = now;
            } else if (now - passThroughZoomOutHoverStartMs >= PASS_THROUGH_MAP_CONTROL_DWELL_MS) {
                mapPanel.adjustMapZoom(false);
                passThroughZoomOutHoverStartMs = -1L;
            }
            return true;
        }
        passThroughZoomOutHoverStartMs = -1L;
        if (mapPanel.isBookmarkButtonAtScreen(screenX, screenY)) {
            mapPanel.setBookmarkButtonHovered(true);
            if (passThroughBookmarkHoverStartMs < 0L) {
                passThroughBookmarkHoverStartMs = now;
            } else if (now - passThroughBookmarkHoverStartMs >= PASS_THROUGH_MAP_CONTROL_DWELL_MS) {
                addMapBookmarkAtCurrentPosition();
                passThroughBookmarkHoverStartMs = -1L;
            }
            return true;
        }
        resetPassThroughMapControlsHover();
        return false;
    }

    /**
     * Pass-through wheel over the biology map (scroll up = zoom in).
     *
     * @return {@code true} if the wheel was consumed
     */
    public boolean applyPassThroughWheelIfHit(int screenX, int screenY, int wheelRotation) {
        if (!isShowing() || wheelRotation == 0) {
            return false;
        }
        return mapPanel.applyPassThroughWheelIfHit(screenX, screenY, wheelRotation);
    }

    public void resetPassThroughMapControlsHover() {
        passThroughBookmarkHoverStartMs = -1L;
        passThroughZoomInHoverStartMs = -1L;
        passThroughZoomOutHoverStartMs = -1L;
        passThroughCompassHoverStartMs = -1L;
        mapPanel.setBookmarkButtonHovered(false);
        mapPanel.setCompassButtonHovered(false);
    }

    private void resetPassThroughBookmarkHoverOnly() {
        passThroughBookmarkHoverStartMs = -1L;
        mapPanel.setBookmarkButtonHovered(false);
    }

    private void addMapBookmarkAtCurrentPosition() {
        double[] anchor = mapPanel.getMapAnchorLatLon();
        if (anchor == null) {
            return;
        }
        mapBookmarks.add(new BioMapBookmark(
                currentBodyName,
                currentBodyId,
                anchor[0],
                anchor[1]));
        syncMapBookmarksToPanel();
        notifySessionStateChanged();
    }

    private void syncMapBookmarksToPanel() {
        mapPanel.setMapBookmarks(bookmarksForCurrentBody());
    }

    private List<BioMapBookmark> bookmarksForCurrentBody() {
        if (mapBookmarks.isEmpty()) {
            return Collections.emptyList();
        }
        List<BioMapBookmark> out = new ArrayList<>();
        for (BioMapBookmark b : mapBookmarks) {
            if (b.matchesBody(currentBodyName, currentBodyId)) {
                out.add(b);
            }
        }
        return out;
    }

    private static final class BioMapBookmark {
        final String bodyName;
        final Integer bodyId;
        final double lat;
        final double lon;

        BioMapBookmark(String bodyName, Integer bodyId, double lat, double lon) {
            this.bodyName = bodyName;
            this.bodyId = bodyId;
            this.lat = lat;
            this.lon = lon;
        }

        static BioMapBookmark fromSessionEntry(BiologyMapBookmarkEntry e) {
            return new BioMapBookmark(e.getBodyName(), e.getBodyId(), e.getLat(), e.getLon());
        }

        BiologyMapBookmarkEntry toSessionEntry() {
            return new BiologyMapBookmarkEntry(bodyName, bodyId, lat, lon);
        }

        boolean matchesBody(String name, Integer id) {
            if (bodyId != null && id != null) {
                return bodyId.equals(id);
            }
            if (bodyName != null && !bodyName.isBlank() && name != null && !name.isBlank()) {
                return bodyName.equalsIgnoreCase(name);
            }
            return bodyId == null && id == null
                    && (bodyName == null || bodyName.isBlank())
                    && (name == null || name.isBlank());
        }
    }

    private static final class BioSrvMarker {
        final String bodyName;
        final Integer bodyId;
        final double lat;
        final double lon;
        final Double headingDeg;

        BioSrvMarker(String bodyName, Integer bodyId, double lat, double lon, Double headingDeg) {
            this.bodyName = bodyName;
            this.bodyId = bodyId;
            this.lat = lat;
            this.lon = lon;
            this.headingDeg = headingDeg;
        }

        static BioSrvMarker fromSessionEntry(BiologySrvMarkerEntry e) {
            return new BioSrvMarker(
                    e.getBodyName(),
                    e.getBodyId(),
                    e.getLat(),
                    e.getLon(),
                    e.getHeadingDeg());
        }

        BiologySrvMarkerEntry toSessionEntry() {
            return new BiologySrvMarkerEntry(bodyName, bodyId, lat, lon, headingDeg);
        }
    }

    private void applyParkedShipSurfaceFix(
            double lat,
            double lon,
            Double radiusM,
            Double headingDeg,
            String bodyName,
            Integer bodyId) {
        parkedShipLat = lat;
        parkedShipLon = lon;
        if (radiusM != null && radiusM.doubleValue() > 1.0) {
            parkedShipRadiusM = radiusM;
            currentPlanetRadius = radiusM;
        }
        if (headingDeg != null) {
            parkedShipHeadingDeg = headingDeg;
        }
        if (bodyName != null && !bodyName.isBlank()) {
            currentBodyName = bodyName;
        }
        if (bodyId != null) {
            currentBodyId = bodyId;
        }
        syncMapToPanel();
        notifySessionStateChanged();
    }

    /** Dismissed / recalled ship took off: no parked ship until the next {@code Touchdown}. */
    private void clearParkedShipSurfaceFix() {
        if (parkedShipLat == null && parkedShipLon == null && parkedShipHeadingDeg == null) {
            return;
        }
        parkedShipLat = null;
        parkedShipLon = null;
        parkedShipHeadingDeg = null;
        syncMapToPanel();
        notifySessionStateChanged();
    }

    private void applyParkedSrvSurfaceFix(double lat, double lon, Double headingDeg) {
        parkedSrvLat = lat;
        parkedSrvLon = lon;
        if (headingDeg != null) {
            parkedSrvHeadingDeg = headingDeg;
        }
        saveSrvMarkerForBody(currentBodyName, currentBodyId);
        syncMapToPanel();
        notifySessionStateChanged();
    }

    private void clearParkedSrvSurfaceFix() {
        if (parkedSrvLat == null && parkedSrvLon == null && parkedSrvHeadingDeg == null) {
            return;
        }
        parkedSrvLat = null;
        parkedSrvLon = null;
        parkedSrvHeadingDeg = null;
        removeSrvMarkerForBody(currentBodyName, currentBodyId);
        syncMapToPanel();
        notifySessionStateChanged();
    }

    private static String bodyToken(String bodyName, Integer bodyId) {
        if (bodyId != null) {
            return "id:" + bodyId;
        }
        if (bodyName != null && !bodyName.isBlank()) {
            return "name:" + bodyName.toLowerCase(java.util.Locale.ROOT);
        }
        return "unknown";
    }

    private void saveSrvMarkerForBody(String bodyName, Integer bodyId) {
        if (parkedSrvLat == null || parkedSrvLon == null) {
            return;
        }
        parkedSrvByBodyToken.put(
                bodyToken(bodyName, bodyId),
                new BioSrvMarker(bodyName, bodyId, parkedSrvLat, parkedSrvLon, parkedSrvHeadingDeg));
    }

    private void removeSrvMarkerForBody(String bodyName, Integer bodyId) {
        parkedSrvByBodyToken.remove(bodyToken(bodyName, bodyId));
    }

    private void loadSrvMarkerForBody(String bodyName, Integer bodyId) {
        BioSrvMarker m = parkedSrvByBodyToken.get(bodyToken(bodyName, bodyId));
        if (m == null) {
            parkedSrvLat = null;
            parkedSrvLon = null;
            parkedSrvHeadingDeg = null;
            return;
        }
        parkedSrvLat = m.lat;
        parkedSrvLon = m.lon;
        parkedSrvHeadingDeg = m.headingDeg;
    }

    /**
     * When the overlay restarts while on foot, recover the last player-controlled {@code Touchdown}
     * from recent journal files (same session, ship still on the surface).
     */
    private void tryRestoreParkedShipFromJournal() {
        if (parkedShipLat != null && parkedShipLon != null && parkedShipRadiusM != null) {
            return;
        }
        if (!OverlayPreferences.isJournalDirectoryAvailable(EliteDangerousOverlay.clientKey)) {
            return;
        }
        try {
            Path dir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
            EliteJournalReader reader = new EliteJournalReader(dir);
            List<EliteLogEvent> events = reader.readEventsFromLastNJournalFiles(8);
            EliteLogEvent raw = null;
            for (int i = events.size() - 1; i >= 0; i--) {
                EliteLogEvent candidate = events.get(i);
                if (candidate == null) {
                    continue;
                }
                // A Liftoff newer than the last Touchdown means the ship is airborne (player or dismissed).
                if (candidate.getType() == EliteEventType.LIFTOFF) {
                    return;
                }
                if (candidate instanceof TouchdownEvent) {
                    raw = candidate;
                    break;
                }
            }
            if (!(raw instanceof TouchdownEvent)) {
                return;
            }
            TouchdownEvent td = (TouchdownEvent) raw;
            if (!td.isOnPlanet()) {
                return;
            }
            Double lat = td.getLatitude();
            Double lon = td.getLongitude();
            if (lat == null || lon == null) {
                return;
            }
            if (!td.isPlayerControlled()
                    && !isUnoccupiedShipTouchdown(
                            lastCommanderAwayFromShip,
                            commanderInSrv,
                            currentLat,
                            currentLon,
                            lat.doubleValue(),
                            lon.doubleValue(),
                            currentPlanetRadius != null ? currentPlanetRadius.doubleValue() : 0.0)) {
                return;
            }
            String bodyName = td.getBodyName();
            Integer bodyId = td.getBodyId() >= 0 ? Integer.valueOf(td.getBodyId()) : null;
            if (currentBodyName != null && !currentBodyName.isBlank()
                    && bodyName != null && !bodyName.isBlank()
                    && !currentBodyName.equalsIgnoreCase(bodyName)) {
                return;
            }
            Double radiusM = resolvePlanetRadiusMetres(bodyName, td.getBodyId());
            applyParkedShipSurfaceFix(
                    lat.doubleValue(),
                    lon.doubleValue(),
                    radiusM,
                    null,
                    bodyName,
                    bodyId);
        } catch (IOException ignored) {
        }
    }

    /**
     * When away from the SRV, recover the last SRV {@code Touchdown} ({@code PlayerControlled:false}).
     * A newer {@code DockSRV} / {@code SRVDestroyed} means the SRV is aboard — do not resurrect the marker.
     */
    private void tryRestoreParkedSrvFromJournal() {
        if (parkedSrvLat != null && parkedSrvLon != null) {
            return;
        }
        if (!OverlayPreferences.isJournalDirectoryAvailable(EliteDangerousOverlay.clientKey)) {
            return;
        }
        try {
            Path dir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
            EliteJournalReader reader = new EliteJournalReader(dir);
            List<EliteLogEvent> events = reader.readEventsFromLastNJournalFiles(8);
            for (int i = events.size() - 1; i >= 0; i--) {
                EliteLogEvent raw = events.get(i);
                if (raw != null && (raw.getType() == EliteEventType.DOCK_SRV
                        || raw.getType() == EliteEventType.SRV_DESTROYED)) {
                    return;
                }
                if (!(raw instanceof TouchdownEvent)) {
                    continue;
                }
                TouchdownEvent td = (TouchdownEvent) raw;
                if (td.isPlayerControlled() || !td.isOnPlanet()) {
                    continue;
                }
                Double lat = td.getLatitude();
                Double lon = td.getLongitude();
                if (lat == null || lon == null) {
                    continue;
                }
                String bodyName = td.getBodyName();
                Integer bodyId = td.getBodyId() >= 0 ? Integer.valueOf(td.getBodyId()) : null;
                if (!touchdownMatchesCurrentBody(bodyName, bodyId)) {
                    continue;
                }
                Double radiusM = resolvePlanetRadiusMetres(bodyName, td.getBodyId());
                double radius = radiusM != null && radiusM.doubleValue() > 1.0
                        ? radiusM.doubleValue()
                        : (currentPlanetRadius != null ? currentPlanetRadius.doubleValue() : 0.0);
                if (isUnoccupiedShipTouchdown(
                        lastCommanderAwayFromShip,
                        commanderInSrv,
                        currentLat,
                        currentLon,
                        lat.doubleValue(),
                        lon.doubleValue(),
                        radius)) {
                    continue;
                }
                applyParkedSrvSurfaceFix(lat.doubleValue(), lon.doubleValue(), null);
                return;
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Parked ship fix updates only while commanding the main ship on a body surface.
     * On foot or in an SRV, Status lat/lon is the commander — not the ship.
     */
    private static boolean shouldUpdateParkedShipPosition(StatusEvent e) {
        if (e == null || e.getDecodedFlags() == null || !e.getDecodedFlags().hasLatLong) {
            return false;
        }
        return e.getDecodedFlags().inMainShip && !e.isOnFoot() && !e.isInSrv();
    }

    /** Status lat/lon tracks the commander, not the parked ship. */
    private static boolean isCommanderAwayFromShip(StatusEvent e) {
        return e != null && (e.isOnFoot() || e.isInSrv());
    }

    /**
     * {@code Liftoff.PlayerControlled}: {@code false} when a dismissed / recalled ship departs on its own.
     * Defaults to {@code true} (player takeoff) when the field is absent.
     */
    private static boolean liftoffIsPlayerControlled(EliteLogEvent event) {
        if (event == null || event.getRawJson() == null) {
            return true;
        }
        com.google.gson.JsonElement pc = event.getRawJson().get("PlayerControlled");
        return pc == null || pc.isJsonNull() || pc.getAsBoolean();
    }

    /** SRV position updates only while driving the SRV (not on foot in suit). */
    private static boolean shouldUpdateParkedSrvPosition(StatusEvent e) {
        if (e == null || e.getDecodedFlags() == null || !e.getDecodedFlags().hasLatLong) {
            return false;
        }
        return e.isInSrv() && !e.isOnFoot();
    }


    /**
     * Status.json {@code Heading}: usually radians (0=north, clockwise). Values outside
     * a plausible radian range are treated as degrees already (some builds use degrees on foot).
     */
    private static Double statusHeadingToDegrees(Double heading) {
        if (heading == null) {
            return null;
        }
        double v = heading.doubleValue();
        double deg = Math.abs(v) <= 2.0 * Math.PI + 0.05 ? Math.toDegrees(v) : v;
        deg = deg % 360.0;
        if (deg < 0.0) {
            deg += 360.0;
        }
        return Double.valueOf(deg);
    }

    /**
     * True when the commander moved to a different planetary body (id or name).
     * Avoids clearing the parked-ship fix when {@link StatusEvent} and journal names differ cosmetically.
     */
    private boolean surfaceBodyChanged(String newBodyName, Integer newBodyId) {
        if (newBodyId != null && currentBodyId != null) {
            return !newBodyId.equals(currentBodyId);
        }
        if (newBodyName != null && !newBodyName.isBlank()
                && currentBodyName != null && !currentBodyName.isBlank()) {
            return !newBodyName.equalsIgnoreCase(currentBodyName);
        }
        // First body lock-on after restart (null → name) is not a body hop — keep parked ship.
        return false;
    }

    /** Journal / touchdown body must match the active planetary body when either side has an id or name. */
    private boolean touchdownMatchesCurrentBody(String touchdownBodyName, Integer touchdownBodyId) {
        if (currentBodyId != null && touchdownBodyId != null) {
            return currentBodyId.equals(touchdownBodyId);
        }
        if (currentBodyName != null && !currentBodyName.isBlank()
                && touchdownBodyName != null && !touchdownBodyName.isBlank()) {
            return currentBodyName.equalsIgnoreCase(touchdownBodyName);
        }
        return currentBodyId == null
                && (currentBodyName == null || currentBodyName.isBlank())
                && touchdownBodyId == null
                && (touchdownBodyName == null || touchdownBodyName.isBlank());
    }

    /**
     * Minimum separation (m) between commander and {@code PlayerControlled:false} touchdown to treat it as
     * recalled ship rather than SRV surface contact while driving.
     */
    static final double SHIP_RECALL_TOUCHDOWN_MIN_SEPARATION_M = 75.0;

    /**
     * {@code PlayerControlled:false} touchdowns are SRV contacts or unoccupied ship recall/dismiss landings.
     */
    static boolean isUnoccupiedShipTouchdown(
            boolean commanderAwayFromShip,
            boolean commanderInSrv,
            Double commanderLat,
            Double commanderLon,
            double touchdownLat,
            double touchdownLon,
            double planetRadiusM) {
        if (!commanderInSrv && commanderAwayFromShip) {
            return true;
        }
        if (commanderInSrv
                && commanderLat != null
                && commanderLon != null
                && planetRadiusM > 1.0) {
            double d = greatCircleMeters(
                    commanderLat.doubleValue(),
                    commanderLon.doubleValue(),
                    touchdownLat,
                    touchdownLon,
                    planetRadiusM);
            return d > SHIP_RECALL_TOUCHDOWN_MIN_SEPARATION_M;
        }
        return false;
    }

    /**
     * Journal {@code Touchdown} — authoritative surface fix for ship (player landing or recall) or SRV.
     */
    private void handleTouchdown(TouchdownEvent e) {
        if (e == null || !e.isOnPlanet()) {
            return;
        }
        Double lat = e.getLatitude();
        Double lon = e.getLongitude();
        if (lat == null || lon == null) {
            return;
        }

        String bodyName = e.getBodyName();
        Integer bodyId = e.getBodyId() >= 0 ? Integer.valueOf(e.getBodyId()) : null;
        boolean bodyChanged = surfaceBodyChanged(bodyName, bodyId);
        if (bodyChanged) {
            saveSrvMarkerForBody(currentBodyName, currentBodyId);
            parkedShipHeadingDeg = null;
            parkedSrvHeadingDeg = null;
            if (bodyName != null && !bodyName.isBlank()) {
                currentBodyName = bodyName;
            }
            if (bodyId != null) {
                currentBodyId = bodyId;
            }
            loadSrvMarkerForBody(currentBodyName, currentBodyId);
        }

        boolean shipTouchdown = e.isPlayerControlled();
        if (!shipTouchdown) {
            Double radiusM = resolvePlanetRadiusMetres(bodyName, e.getBodyId());
            double radius = radiusM != null && radiusM.doubleValue() > 1.0
                    ? radiusM.doubleValue()
                    : (currentPlanetRadius != null ? currentPlanetRadius.doubleValue() : 0.0);
            shipTouchdown = isUnoccupiedShipTouchdown(
                    lastCommanderAwayFromShip,
                    commanderInSrv,
                    currentLat,
                    currentLon,
                    lat.doubleValue(),
                    lon.doubleValue(),
                    radius);
        }

        if (shipTouchdown) {
            if (bodyChanged) {
                parkedShipHeadingDeg = null;
            }
            if (e.isPlayerControlled()) {
                currentLat = lat;
                currentLon = lon;
            }
            Double radiusM = resolvePlanetRadiusMetres(bodyName, e.getBodyId());
            applyParkedShipSurfaceFix(lat, lon, radiusM, null, bodyName, bodyId);
            refreshCommanderDistancesAndRepaint(bodyChanged, false);
            return;
        }

        applyParkedSrvSurfaceFix(lat.doubleValue(), lon.doubleValue(), null);
        if (currentLat != null && currentLon != null && currentPlanetRadius != null) {
            model.updateDistances(
                    currentLat.doubleValue(),
                    currentLon.doubleValue(),
                    currentPlanetRadius.doubleValue());
            table.repaint();
        }
    }

    /**
     * Prefer live {@link StatusEvent#getPlanetRadius()}, then cached body scan radius.
     * Status radius matches in-game surface distance math; cached body radius can be absent or stale.
     */
    private Double resolvePlanetRadiusMetres(String bodyName, int bodyId) {
        if (currentPlanetRadius != null && currentPlanetRadius.doubleValue() > 1.0) {
            return currentPlanetRadius;
        }
        if (parkedShipRadiusM != null && parkedShipRadiusM.doubleValue() > 1.0) {
            return parkedShipRadiusM;
        }
        if (systemTab != null) {
            SystemState state = systemTab.getState();
            if (state != null && state.getBodies() != null) {
                BodyInfo body = null;
                if (bodyId >= 0) {
                    body = state.getBodies().get(Integer.valueOf(bodyId));
                }
                if (body == null && bodyName != null && !bodyName.isBlank()) {
                    body = findBodyByName(state, bodyName);
                }
                if (body != null && body.getRadius() != null && body.getRadius().doubleValue() > 1.0) {
                    return body.getRadius();
                }
            }
        }
        return null;
    }

    private void refreshCommanderDistancesAndRepaint(boolean bodyChanged, boolean bodyNameFirstLock) {
        if (bodyChanged) {
            refreshTableForCurrentBody(true);
            return;
        }
        if (bodyNameFirstLock || model.getRowCount() == 0) {
            refreshTableForCurrentBody(false);
        }
        if (currentLat != null && currentLon != null && currentPlanetRadius != null) {
            model.updateDistances(
                    currentLat.doubleValue(),
                    currentLon.doubleValue(),
                    currentPlanetRadius.doubleValue());
            table.repaint();
        }
    }

    private void syncMapToPanel() {
        if (parkedShipLat != null && parkedShipLon != null && parkedShipRadiusM != null) {
            mapPanel.setShipLatLon(
                    parkedShipLat.doubleValue(),
                    parkedShipLon.doubleValue(),
                    parkedShipRadiusM.doubleValue());
        } else {
            mapPanel.clearShipPosition();
        }
        Double srvRadiusM = resolveSrvMapRadiusMetres();
        if (parkedSrvLat != null && parkedSrvLon != null && srvRadiusM != null) {
            mapPanel.setSrvLatLon(
                    parkedSrvLat.doubleValue(),
                    parkedSrvLon.doubleValue(),
                    srvRadiusM.doubleValue());
            mapPanel.setSrvGlyphHeadingDeg(parkedSrvHeadingDeg);
        } else {
            mapPanel.clearSrvPosition();
        }
    }

    private Double resolveSrvMapRadiusMetres() {
        if (parkedShipRadiusM != null && parkedShipRadiusM.doubleValue() > 1.0) {
            return parkedShipRadiusM;
        }
        if (currentPlanetRadius != null && currentPlanetRadius.doubleValue() > 1.0) {
            return currentPlanetRadius;
        }
        return resolvePlanetRadiusMetres(currentBodyName, currentBodyId != null ? currentBodyId.intValue() : -1);
    }

    public void handleLogEvent(EliteLogEvent event) {
        if (event == null) {
            return;
        }

        if (event instanceof StatusEvent) {
            StatusEvent e = (StatusEvent) event;

            Double newLat = e.getLatitude();
            Double newLon = e.getLongitude();
            Double newRadius = e.getPlanetRadius();
            String newBodyName = resolveSurfaceBodyName(e);
            Integer newBodyId = e.getStatusBodyId();

            boolean bodyChanged = surfaceBodyChanged(newBodyName, newBodyId);
            boolean bodyNameFirstLock = (currentBodyName == null || currentBodyName.isBlank())
                    && newBodyName != null && !newBodyName.isBlank();

            if (bodyChanged) {
                saveSrvMarkerForBody(currentBodyName, currentBodyId);
            }

            if (newLat != null) {
                currentLat = newLat;
            }
            if (newLon != null) {
                currentLon = newLon;
            }
            if (newRadius != null && newRadius.doubleValue() > 1.0) {
                currentPlanetRadius = newRadius;
                if (parkedShipLat != null && parkedShipLon != null
                        && (parkedShipRadiusM == null || parkedShipRadiusM.doubleValue() <= 1.0)) {
                    parkedShipRadiusM = newRadius;
                }
            }
            if (newBodyName != null && !newBodyName.isBlank()) {
                currentBodyName = newBodyName;
            }
            if (newBodyId != null) {
                currentBodyId = newBodyId;
            }

            if (bodyChanged) {
                parkedShipLat = null;
                parkedShipLon = null;
                parkedShipRadiusM = null;
                parkedShipHeadingDeg = null;
                loadSrvMarkerForBody(currentBodyName, currentBodyId);
                syncMapBookmarksToPanel();
                syncMapToPanel();
            }

            if (shouldUpdateParkedShipPosition(e)
                    && newLat != null && newLon != null && newRadius != null) {
                applyParkedShipSurfaceFix(
                        newLat.doubleValue(),
                        newLon.doubleValue(),
                        newRadius,
                        statusHeadingToDegrees(e.getHeading()),
                        newBodyName,
                        newBodyId);
            } else if (isCommanderAwayFromShip(e) && parkedShipLat == null) {
                tryRestoreParkedShipFromJournal();
            }

            commanderInSrv = e.isInSrv() && !e.isOnFoot();
            lastCommanderAwayFromShip = isCommanderAwayFromShip(e);

            if (shouldUpdateParkedSrvPosition(e) && newLat != null && newLon != null) {
                applyParkedSrvSurfaceFix(
                        newLat.doubleValue(),
                        newLon.doubleValue(),
                        statusHeadingToDegrees(e.getHeading()));
            } else if (lastCommanderAwayFromShip && !commanderInSrv && parkedSrvLat == null) {
                tryRestoreParkedSrvFromJournal();
            }

            boolean awayFromShip = lastCommanderAwayFromShip;
            if (currentLat != null && currentLon != null && currentPlanetRadius != null) {
                recordMovementSample(
                        e.getTimestamp(),
                        currentLat.doubleValue(),
                        currentLon.doubleValue(),
                        currentPlanetRadius.doubleValue(),
                        awayFromShip);
            }

            Double statusHdgDeg = statusHeadingToDegrees(e.getHeading());
            double mapUpDeg;
            if (!awayFromShip) {
                lastFootTravelUpDeg = null;
                // Heading-up map: "up" is ship nose — hull and white V stay fixed pointing up.
                Double shipNoseDeg = statusHdgDeg != null ? statusHdgDeg : parkedShipHeadingDeg;
                if (shipNoseDeg != null) {
                    mapUpDeg = shipNoseDeg.doubleValue();
                } else if (movementHeadingDeg != null) {
                    mapUpDeg = movementHeadingDeg.doubleValue();
                } else {
                    mapUpDeg = 0.0;
                }
                mapPanel.setShipGlyphHeadingDeg(
                        shipNoseDeg != null ? shipNoseDeg : Double.valueOf(mapUpDeg));
                mapPanel.setPlayerHeadingDeg(
                        shipNoseDeg != null ? shipNoseDeg : Double.valueOf(mapUpDeg));
            } else {
                mapPanel.setShipGlyphHeadingDeg(parkedShipHeadingDeg);
                // On foot / in SRV: map "up" is travel direction only (not Status body facing).
                if (movementHeadingDeg != null) {
                    lastFootTravelUpDeg = movementHeadingDeg;
                    mapUpDeg = movementHeadingDeg.doubleValue();
                } else if (lastFootTravelUpDeg != null) {
                    mapUpDeg = lastFootTravelUpDeg.doubleValue();
                } else {
                    mapUpDeg = 0.0;
                }
                Double playerHdgDeg = statusHdgDeg != null ? statusHdgDeg : movementHeadingDeg;
                mapPanel.setPlayerHeadingDeg(playerHdgDeg);
            }
            mapPanel.setShipHeadingDeg(mapUpDeg);
            mapPanel.setCommanderInSrv(commanderInSrv);

            syncMapToPanel();

            if (isCommanderAwayFromShip(e) && currentLat != null && currentLon != null) {
                mapPanel.setCommanderCentered(
                        true,
                        currentLat.doubleValue(),
                        currentLon.doubleValue(),
                        commanderInSrv);
            } else {
                mapPanel.clearCommanderCentered();
            }

            // Only rebuild rows when the body changes; otherwise just update distances/heading/map.
            refreshCommanderDistancesAndRepaint(bodyChanged, bodyNameFirstLock);

            updateVoiceTransitions();
            return;
        }

        if (event instanceof TouchdownEvent) {
            handleTouchdown((TouchdownEvent) event);
            return;
        }

        if (event.getType() == EliteEventType.DOCK_SRV || event.getType() == EliteEventType.SRV_DESTROYED) {
            clearParkedSrvSurfaceFix();
            return;
        }

        if (event.getType() == EliteEventType.LIFTOFF && !liftoffIsPlayerControlled(event)) {
            clearParkedShipSurfaceFix();
            return;
        }

        if (event instanceof ScanOrganicEvent) {
            ScanOrganicEvent so = (ScanOrganicEvent) event;
            String st = so.getScanType();
            if (st != null && "Sample".equalsIgnoreCase(st.trim())) {
                String dn = displayNameFromScanOrganic(so);
                if (dn != null && !dn.isBlank()) {
                    suppressEnterUntilMsByBioKey.put(
                            canonicalBioKey(dn),
                            Long.valueOf(System.currentTimeMillis() + SUPPRESS_ENTER_AFTER_SAMPLE_MS));
                }
            }
            syncCurrentBodyFromScanOrganic(so);
        }

        if (event instanceof BioScanPredictionEvent || event instanceof ScanOrganicEvent || event instanceof SaasignalsFoundEvent) {
            refreshTableForCurrentBody();
        }
    }

    private void refreshTableForCurrentBody() {
        refreshTableForCurrentBody(true);
    }

    /**
     * @param clearIfBodyMissing when {@code false}, keep existing rows if the system tab has not
     *     indexed this body yet (common when journal replays {@code Touchdown} before scan data).
     */
    private void refreshTableForCurrentBody(boolean clearIfBodyMissing) {
        if (systemTab == null) {
            return;
        }

        if (currentBodyName == null || currentBodyName.isBlank()) {
            model.setRows(Collections.emptyList());
            mapPanel.setAbandonedSamplePins(Collections.emptyMap());
            mapPanel.setActiveIncompleteBioKey(null);
            mapPanel.setShowParkedPins(false);
            return;
        }

        SystemState state = systemTab.getState();
        if (state == null) {
            if (clearIfBodyMissing) {
                clearBioTableAndMapPins();
            }
            return;
        }

        BodyInfo body = findBodyForExoBio(state, currentBodyName, currentBodyId);
        if (body == null) {
            if (clearIfBodyMissing) {
                clearBioTableAndMapPins();
            }
            return;
        }

        maybeReplayGenusSwitchParkingFromJournal(body);
        syncMapBookmarksToPanel();
        List<BioRow> rows = buildRows(body);
        boolean anyPartialScan = false;
        for (BioRow r : rows) {
            if (r != null && !r.analysed && r.sampleCount > 0) {
                anyPartialScan = true;
                break;
            }
        }
        model.setRows(rows);
        Map<String, List<BodyInfo.BioSamplePoint>> abandonedPins = body.getAbandonedBioSamplePointsSnapshot();
        mapPanel.setAbandonedSamplePins(abandonedPins);
        mapPanel.setActiveIncompleteBioKey(body.getActiveIncompleteBioKey());
        mapPanel.setShowParkedPins(anyPartialScan || !abandonedPins.isEmpty());

        if (currentLat != null && currentLon != null && currentPlanetRadius != null) {
            model.updateDistances(currentLat.doubleValue(), currentLon.doubleValue(), currentPlanetRadius.doubleValue());
        }
    }

    private void clearBioTableAndMapPins() {
        model.setRows(Collections.emptyList());
        mapPanel.setAbandonedSamplePins(Collections.emptyMap());
        mapPanel.setActiveIncompleteBioKey(null);
        mapPanel.setShowParkedPins(false);
    }

    private void maybeReplayGenusSwitchParkingFromJournal(BodyInfo body) {
        if (body == null) {
            return;
        }
        String token = body.getStarSystem() + "\0"
                + (body.getBodyId() > 0 ? Integer.toString(body.getBodyId()) : currentBodyName);
        if (!genusSwitchReplayBodies.add(token)) {
            return;
        }
        if (BioGenusSwitchRestorer.replayFromJournal(body) && systemTab != null) {
            systemTab.persistSystemStateIfPossible();
        }
    }

private static List<BioRow> buildRows(BodyInfo body) {
        List<BioRow> rows = new ArrayList<>();

        Map<String, BioCandidate> candByKey = new HashMap<>();

        // If DSS has revealed which genera are present, only show predictions for those genera.
        // If we've scanned species on the planet, for that genus only show the identified species (not other predictions).
        Set<String> observedGenusLower = observedGenusSet(body);
        Map<String, Set<String>> observedSpeciesByGenus = observedSpeciesByGenus(body);
        List<BioCandidate> preds = body.getPredictions();
        if (preds != null) {
            for (BioCandidate c : preds) {
                if (c == null) {
                    continue;
                }
                String dn = c.getDisplayName();
                if (dn == null || dn.isBlank()) {
                    continue;
                }
                String predGenus = genusFromDisplayName(dn);
                String predGenusNorm = normalizeGenus(predGenus);
                if (!observedGenusLower.isEmpty()) {
                    boolean genusMatch = false;
                    for (String obs : observedGenusLower) {
                        if (predGenusNorm.equals(normalizeGenus(obs))) {
                            genusMatch = true;
                            break;
                        }
                    }
                    if (!genusMatch) {
                        continue;
                    }
                }
                // For genera where we've identified species by scanning, only show those species.
                Set<String> identifiedForGenus = observedSpeciesByGenus.get(predGenusNorm);
                if (identifiedForGenus != null && !identifiedForGenus.isEmpty()) {
                    if (!identifiedForGenus.contains(canonicalBioKey(dn))) {
                        continue;
                    }
                }
                rows.add(new BioRow(dn));
                candByKey.put(canonicalBioKey(dn), c);
            }
        }

        // Ensure observed entries appear even if they weren't predicted
        if (body.getObservedBioDisplayNames() != null) {
            for (String observed : body.getObservedBioDisplayNames()) {
                if (observed == null || observed.isBlank()) {
                    continue;
                }

                String ok = canonicalBioKey(observed);
                boolean exists = false;
                for (BioRow r : rows) {
                    if (canonicalBioKey(r.displayName).equals(ok)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    rows.add(new BioRow(observed));
                }
            }
        }

        Map<String, Integer> counts = body.getBioSampleCountsSnapshot();
        Map<String, List<BodyInfo.BioSamplePoint>> points = body.getBioSamplePointsSnapshot();

        if (!Boolean.TRUE.equals(body.getWasFootfalled()) && body.getSpanshLandmarks() == null) {
            SpanshBodyExobiologyInfo info = SpanshLandmarkCache.getInstance().getOrFetch(body.getStarSystem(), body.getBodyName());
            if (info != null) {
                body.setSpanshLandmarks(info.getLandmarks());
                body.setSpanshExcludeFromExobiology(info.isExcludeFromExobiology());
            }
        }
        boolean firstBonus = FirstBonusHelper.firstBonusApplies(body);

        body.reconcileStalePartialBioState();
        body.sanitizeInflatedBioSampleCounts();
        body.inferLegacyAnalysedFromFullPins();
        counts = body.getBioSampleCountsSnapshot();
        points = body.getBioSamplePointsSnapshot();

        for (BioRow r : rows) {

            // Elite only tracks ONE active genus at a time; journal counts can reset when switching.
            // Recorded sample points (and the table) use overlay state; abandoned pins stay for the bio map.
            List<BodyInfo.BioSamplePoint> pts = lookupPoints(points, r.displayName);
            int ptsCount = (pts == null) ? 0 : pts.size();
            int countFromSnapshot = lookupCount(counts, r.displayName);

            r.analysed = body.isBioSpeciesAnalysed(r.displayName);
            if (r.analysed) {
                r.sampleCount = REQUIRED_SAMPLES;
                if (ptsCount > 0) {
                    r.points = new ArrayList<>(pts);
                }
            } else {
                r.sampleCount = Math.min(REQUIRED_SAMPLES, Math.max(0, countFromSnapshot));
                if (ptsCount > 0) {
                    r.points = new ArrayList<>(pts);
                }
            }

            BioCandidate cand = candByKey.get(canonicalBioKey(r.displayName));
            r.genusKey = genusKeyForRow(r.displayName, cand);
            r.requiredMeters = BioColonyDistance.metersForBio(r.genusKey);

            if (cand != null) {
                long cr = cand.getEstimatedPayout(firstBonus);
                long millions = Math.round(cr / 1_000_000.0);
                r.creditsText = String.format(Locale.US, "%dM Cr", millions);
            } else {
                r.creditsText = "";
            }
        }

        collapseRowsByGenus(body, rows, candByKey);

        // Complete first, then in-progress, then unstarted; within each group sort by value (credits) desc, then name.
        final boolean firstBonusForSort = firstBonus;
        final Map<String, BioCandidate> candByKeyForSort = candByKey;
        Collections.sort(rows, new Comparator<BioRow>() {
            @Override
            public int compare(BioRow a, BioRow b) {
                int ra = rank(a);
                int rb = rank(b);
                if (ra != rb) {
                    return Integer.compare(ra, rb);
                }
                long aCr = creditsForRow(a, candByKeyForSort, firstBonusForSort);
                long bCr = creditsForRow(b, candByKeyForSort, firstBonusForSort);
                int cmp = Long.compare(bCr, aCr);
                if (cmp != 0) {
                    return cmp;
                }
                return a.displayName.compareToIgnoreCase(b.displayName);
            }

            private int rank(BioRow row) {
                if (row != null && row.analysed) {
                    return 0;
                }
                if (row != null && row.sampleCount > 0) {
                    return 1;
                }
                return 2;
            }
        });

        return rows;
    }

    private static long creditsForRow(BioRow r, Map<String, BioCandidate> candByKey, boolean firstBonus) {
        if (r == null || candByKey == null) {
            return Long.MIN_VALUE;
        }
        BioCandidate c = candByKey.get(canonicalBioKey(r.displayName));
        return (c != null) ? c.getEstimatedPayout(firstBonus) : Long.MIN_VALUE;
    }

    private static Set<String> observedGenusSet(BodyInfo body) {
        Set<String> set = new HashSet<>();
        if (body.getObservedGenusPrefixes() == null) {
            return set;
        }
        for (String g : body.getObservedGenusPrefixes()) {
            if (g != null && !g.isBlank()) {
                set.add(g.trim().toLowerCase(Locale.ROOT));
            }
        }
        return set;
    }

    /** Genus (normalized) -> canonical display names of species we've scanned on the planet. */
    private static Map<String, Set<String>> observedSpeciesByGenus(BodyInfo body) {
        Map<String, Set<String>> byGenus = new HashMap<>();
        if (body.getObservedBioDisplayNames() == null) {
            return byGenus;
        }
        for (String displayName : body.getObservedBioDisplayNames()) {
            if (displayName == null || displayName.isBlank()) {
                continue;
            }
            String genus = genusFromDisplayName(displayName);
            String genusNorm = normalizeGenus(genus);
            byGenus.computeIfAbsent(genusNorm, k -> new HashSet<>()).add(canonicalBioKey(displayName));
        }
        return byGenus;
    }

    private static String normalizeGenus(String s) {
        if (s == null) {
            return "";
        }
        String x = s.trim().toLowerCase(Locale.ROOT);
        if (x.equals("bacteria")) {
            return "bacterium";
        }
        if (x.equals("strata")) {
            return "stratum";
        }
        return x;
    }

    private static void collapseRowsByGenus(
            BodyInfo body,
            List<BioRow> rows,
            Map<String, BioCandidate> candByKey) {
        // Rule (2): always show one row per species prediction / observed entry.
        // Do not collapse by genus here; the earlier construction of `rows`
        // already handles de-duplicating exact display names.
        // This method is intentionally a no-op to preserve all species rows.
    }
    

    private static boolean isObservedDisplayName(BodyInfo body, String displayName) {
        if (body.getObservedBioDisplayNames() == null) {
            return false;
        }
        String want = canonicalBioKey(displayName);
        for (String s : body.getObservedBioDisplayNames()) {
            if (want.equals(canonicalBioKey(s))) {
                return true;
            }
        }
        return false;
    }

    private static double scoreFor(BioRow r, Map<String, BioCandidate> candByKey) {
        BioCandidate c = candByKey.get(canonicalBioKey(r.displayName));
        if (c == null) {
            return -1.0;
        }
        return c.getScore();
    }

    private static String genusKeyForRow(String displayName, BioCandidate c) {
        if (c != null && c.getGenus() != null && !c.getGenus().isBlank()) {
            return c.getGenus().trim().toLowerCase(Locale.ROOT);
        }
        return genusFromDisplayName(displayName);
    }

    private static String genusFromDisplayName(String displayName) {
        if (displayName == null) {
            return "";
        }
        String s = displayName.trim();
        if (s.isEmpty()) {
            return "";
        }
        int idx = s.indexOf(' ');
        String genus = (idx < 0) ? s : s.substring(0, idx);
        return genus.trim().toLowerCase(Locale.ROOT);
    }

    private static int lookupCount(Map<String, Integer> counts, String displayName) {
        if (counts == null || counts.isEmpty()) {
            return 0;
        }
        if (displayName == null || displayName.isBlank()) {
            return 0;
        }

        Integer v = counts.get(displayName);
        if (v == null) {
            v = counts.get(canonicalBioKey(displayName));
        }
        if (v != null) {
            return v.intValue();
        }

        String want = canonicalBioKey(displayName);
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (want.equals(canonicalBioKey(e.getKey()))) {
                return (e.getValue() == null) ? 0 : e.getValue().intValue();
            }
        }
        return 0;
    }

    private static List<BodyInfo.BioSamplePoint> lookupPoints(
            Map<String, List<BodyInfo.BioSamplePoint>> points,
            String displayName) {

        if (points == null || points.isEmpty()) {
            return null;
        }
        if (displayName == null || displayName.isBlank()) {
            return null;
        }

        List<BodyInfo.BioSamplePoint> v = points.get(displayName);
        if (v == null) {
            v = points.get(canonicalBioKey(displayName));
        }
        if (v != null) {
            return v;
        }

        String want = canonicalBioKey(displayName);
        for (Map.Entry<String, List<BodyInfo.BioSamplePoint>> e : points.entrySet()) {
            if (want.equals(canonicalBioKey(e.getKey()))) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * Prefer {@code BodyID} from Status / journal — Status {@code BodyName} is sometimes the star system name
     * (e.g. after FSD) and does not match any {@link BodyInfo} entry by name alone.
     */
    private static BodyInfo findBodyForExoBio(SystemState state, String bodyName, Integer bodyId) {
        if (state == null || state.getBodies() == null || state.getBodies().isEmpty()) {
            return null;
        }
        if (bodyId != null && bodyId.intValue() >= 0) {
            BodyInfo byId = state.getBodies().get(bodyId);
            if (byId != null) {
                return byId;
            }
        }
        return findBodyByName(state, bodyName);
    }

    private String resolveSurfaceBodyName(StatusEvent e) {
        if (e == null) {
            return null;
        }
        String name = e.getBodyNamePhysical();
        if (name == null || name.isBlank()) {
            name = e.getBodyName();
        }
        Integer bodyId = e.getStatusBodyId();
        if (systemTab != null && bodyId != null && bodyId.intValue() >= 0) {
            SystemState state = systemTab.getState();
            if (state != null) {
                BodyInfo known = state.getBodies().get(bodyId);
                if (known != null && known.getBodyName() != null && !known.getBodyName().isBlank()) {
                    return known.getBodyName();
                }
            }
        }
        return name;
    }

    private void syncCurrentBodyFromScanOrganic(ScanOrganicEvent so) {
        if (so == null || so.getBodyId() < 0 || systemTab == null) {
            return;
        }
        SystemState state = systemTab.getState();
        if (state == null) {
            return;
        }
        BodyInfo body = state.getBodies().get(so.getBodyId());
        if (body == null) {
            return;
        }
        currentBodyId = Integer.valueOf(so.getBodyId());
        if (body.getBodyName() != null && !body.getBodyName().isBlank()) {
            currentBodyName = body.getBodyName();
        }
    }

    private static BodyInfo findBodyByName(SystemState state, String bodyName) {
        if (bodyName == null || bodyName.isBlank()) {
            return null;
        }
        for (BodyInfo b : state.getBodies().values()) {
            if (b == null || b.getBodyName() == null) {
                continue;
            }
            if (b.getBodyName().equalsIgnoreCase(bodyName)) {
                return b;
            }
        }
        return null;
    }

    private static String canonicalBioKey(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a.trim();
        }
        if (b != null && !b.trim().isEmpty()) {
            return b.trim();
        }
        return "";
    }

    /** Same display name construction as {@link org.dce.ed.state.SystemEventProcessor#handleScanOrganic}. */
    private static String displayNameFromScanOrganic(ScanOrganicEvent e) {
        if (e == null) {
            return null;
        }
        String genusName = firstNonBlank(e.getGenusLocalised(), e.getGenus());
        String speciesName = firstNonBlank(e.getSpeciesLocalised(), e.getSpecies());
        if (genusName.isEmpty()) {
            return null;
        }
        if (speciesName.startsWith(genusName + " ")) {
            speciesName = speciesName.replace(genusName, "").trim();
        }
        if (!speciesName.isEmpty()) {
            return genusName + " " + speciesName;
        }
        return genusName;
    }

    /** Genus word only (first token of display name / bio key). */
    private static String mapPinGenusLabel(String displayNameOrKey) {
        if (displayNameOrKey == null) {
            return "";
        }
        String s = displayNameOrKey.trim();
        if (s.isEmpty()) {
            return "";
        }
        int idx = s.indexOf(' ');
        return idx < 0 ? s : s.substring(0, idx);
    }

    /** Distance and true-north bearing toward the sample, e.g. {@code 450m, 127°}. */
    private static String mapPinRayMetaLabel(double distanceM, double bearingDegNorth) {
        String dist = formatMetersFixed(distanceM);
        int brng = (int) Math.round((bearingDegNorth % 360.0 + 360.0) % 360.0);
        return dist + ", " + brng + "°";
    }

    private static double greatCircleMeters(double lat1Deg, double lon1Deg, double lat2Deg, double lon2Deg, double radiusM) {
        double lat1 = Math.toRadians(lat1Deg);
        double lon1 = Math.toRadians(lon1Deg);
        double lat2 = Math.toRadians(lat2Deg);
        double lon2 = Math.toRadians(lon2Deg);

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a =
                Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return radiusM * c;
    }

    private static String formatMetersFixed(double meters) {
        if (Double.isNaN(meters) || Double.isInfinite(meters)) {
            return "";
        }

        if (meters < 1000.0) {
            return String.format(java.util.Locale.ROOT, "%.0fm", meters);
        }

        double km = meters / 1000.0;
        // nearest tenth
        km = Math.round(km * 10.0) / 10.0;
        return String.format(java.util.Locale.ROOT, "%.1fkm", km);
    }

    public void applyUiFontPreferences() {
        applyUiFont(OverlayPreferences.getUiFont());
    }

    public void applyUiFont(Font font) {
        if (font != null) {
            setFont(font);
            header.setFont(font);
            table.setFont(font);
            table.setRowHeight(Math.max(24, font.getSize() + 8));

            JTableHeader th = table.getTableHeader();
            if (th != null) {
                th.setFont(font);
            }
        }
        EdoMiningSplitPaneUi.applyDividerTheme(tableMapSplit);

        SwingUtilities.invokeLater(() -> {
            revalidate();
            repaint();
        });
    }

    private static void styleHeader(JTable table) {
        table.setTableHeader(new org.dce.ed.ui.TransparentTableHeader(table.getColumnModel()));
        JTableHeader th = table.getTableHeader();
        if (th == null) {
            return;
        }

        th.setOpaque(false);
        th.setForeground(EdoUi.User.MAIN_TEXT);
        th.setBackground(EdoUi.User.BACKGROUND);
        th.setBorder(null);

        th.setDefaultRenderer(new DefaultTableCellRenderer() {

            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(
                    JTable table,
                    Object value,
                    boolean isSelected,
                    boolean hasFocus,
                    int row,
                    int column) {

                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, false, false, row, column);
                boolean transparent = OverlayPreferences.overlayChromeRequestsTransparency();
                label.setOpaque(!transparent);
                label.setBackground(transparent ? EdoUi.Internal.TRANSPARENT : EdoUi.User.BACKGROUND);
                label.setForeground(EdoUi.User.MAIN_TEXT);
                label.setBorder(new EmptyBorder(0, 6, 0, 6));
                return label;
            }
        });
    }

    private static Color colorForSamples(BioRow row) {
        if (row != null && row.analysed) {
            return EdoUi.User.PRIMARY_HIGHLIGHT;
        }
        if (row != null && row.sampleCount > 0) {
            return EdoUi.User.SECONDARY_HIGHLIGHT;
        }
        return EdoUi.User.MAIN_TEXT;
    }

    // ------------------------------------------------------------
    // Table model + rendering
    // ------------------------------------------------------------

    private static final class BioRow {
        private final String displayName;
        private int sampleCount;
        private boolean analysed;
        private String creditsText = "";
        private String genusKey = "";
        private int requiredMeters;
        private List<BodyInfo.BioSamplePoint> points = Collections.emptyList();
        private final List<Double> distancesM = new ArrayList<>();

        private BioRow(String displayName) {
            this.displayName = displayName;
        }

        private void recomputeDistances(double curLat, double curLon, double radiusM) {
            distancesM.clear();

            // Complete: don’t track distances anymore
            if (analysed) {
                return;
            }

            if (points == null || points.isEmpty()) {
                return;
            }

            for (BodyInfo.BioSamplePoint p : points) {
                if (p == null) {
                    continue;
                }

                distancesM.add(Double.valueOf(greatCircleMeters(curLat, curLon, p.getLatitude(), p.getLongitude(), radiusM)));
            }
        }
    }

    private static final class BioTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private final String[] cols = { "Bio", "Credits", "Min (m)", "Samples" };
        private final List<BioRow> rows = new ArrayList<>();

        // Track the maximum row count we've ever shown so the table height doesn't shrink.
        private int maxRowsSeen = 0;

        void setRows(List<BioRow> newRows) {
            rows.clear();

            if (newRows != null) {
                rows.addAll(newRows);
            }

            if (rows.size() > maxRowsSeen) {
                maxRowsSeen = rows.size();
            }

            fireTableDataChanged();
        }

        int getMaxRowsSeen() {
            return Math.max(maxRowsSeen, rows.size());
        }

        BioRow getRowAt(int row) {
            if (row < 0 || row >= rows.size()) {
                return null;
            }
            return rows.get(row);
        }

        List<BioRow> getRowsSnapshot() {
            return new ArrayList<>(rows);
        }

        void updateDistances(double curLat, double curLon, double radiusM) {
            for (BioRow r : rows) {
                r.recomputeDistances(curLat, curLon, radiusM);
            }

            if (!rows.isEmpty()) {
                fireTableRowsUpdated(0, rows.size() - 1);
            }
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int col) {
            return cols[col];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            BioRow r = rows.get(rowIndex);

            if (columnIndex == 0) {
                return r.displayName;
            }
            if (columnIndex == 1) {
                return r.creditsText;
            }
            if (columnIndex == 2) {
                if (r.requiredMeters <= 0) {
                    return "";
                }
                return Integer.valueOf(r.requiredMeters);
            }
            if (columnIndex == 3) {
                return r;
            }
            return "";
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }

    private static final class BioTextCellRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;

        private final BioTableModel model;

        private BioTextCellRenderer(BioTableModel model) {
            this.model = model;
            setOpaque(false);
            setForeground(EdoUi.User.MAIN_TEXT);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column) {

            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            label.setOpaque(false);
            label.setBackground(EdoUi.Internal.TRANSPARENT);

            if (isSelected) {
                label.setBackground(EdoUi.Internal.WHITE_ALPHA_64);
                label.setOpaque(true);
            }

            BioRow r = model.getRowAt(row);
            if (r != null) {
                // Color Bio, Credits, Min columns by status.
                if (column == 0 || column == 1 || column == 2) {
                    label.setForeground(colorForSamples(r));
                } else {
                    label.setForeground(EdoUi.User.MAIN_TEXT);
                }
            } else {
                label.setForeground(EdoUi.User.MAIN_TEXT);
            }

            label.setBorder(new EmptyBorder(0, 6, 0, 6));
            return label;
        }
    }

    private static final class SamplePillsRenderer implements TableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column) {

            if (!(value instanceof BioRow)) {
                DefaultTableCellRenderer fallback = new DefaultTableCellRenderer();
                fallback.setOpaque(false);
                fallback.setBackground(EdoUi.Internal.TRANSPARENT);
                fallback.setForeground(EdoUi.User.MAIN_TEXT);
                Component comp = fallback.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (comp instanceof JComponent) {
                    ((JComponent) comp).setOpaque(false);
                }
                comp.setBackground(EdoUi.Internal.TRANSPARENT);
                return comp;
            }

            BioRow r = (BioRow) value;
            return new SamplePillsComponent(r, table.getFont());
        }
    }

    private static final class SamplePillsComponent extends JPanel {

        private static final long serialVersionUID = 1L;

        private final BioRow row;
        private final Font font;

        SamplePillsComponent(BioRow row, Font font) {
            this.row = row;
            this.font = font;
            setOpaque(false);
            setPreferredSize(new Dimension(340, 24));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setFont(font);

                FontMetrics fm = g2.getFontMetrics();

                // Fixed width bubble sized for up to "999m" (and "9.9km" if wider)
                int bubbleW = Math.max(fm.stringWidth("999m"), fm.stringWidth("9.9km")) + 14;
                int bubbleH = 16;

                int x = 8;
                int yMid = getHeight() / 2;
                int y = yMid - (bubbleH / 2);
                int gap = 8;

                Color c = colorForSamples(row);

                // Complete (Analyse): show checkmarks only and no distances.
                if (row.analysed) {
                    g2.setColor(c);

                    Font oldFont = g2.getFont();
                    try {
                        Font checkFont = iconFont(font);
                        g2.setFont(checkFont);

                        FontMetrics checkFm = g2.getFontMetrics();
                        String check = "\u2713";
                        int checkW = checkFm.stringWidth(check);

                        for (int i = 0; i < 3; i++) {
                            int tx = x + (bubbleW - checkW) / 2;
                            int ty = yMid + (checkFm.getAscent() / 2) - 1;
                            g2.drawString(check, tx, ty);
                            x += bubbleW + gap;
                        }
                    } finally {
                        g2.setFont(oldFont);
                    }
                    return;
                }

                int have = Math.min(3, Math.max(0, row.sampleCount));
                int slots = 3;

                for (int i = 0; i < slots; i++) {
                    boolean filled = i < have;

                    String txt = null;
                    if (row.distancesM != null && i < row.distancesM.size()) {
                        txt = formatMetersFixed(row.distancesM.get(i).doubleValue());
                    }

                    g2.setColor(c);
                    g2.drawRoundRect(x, y, bubbleW, bubbleH, bubbleH, bubbleH);

                    if (filled) {
                        g2.setColor(c);
                        g2.fillRoundRect(x + 1, y + 1, bubbleW - 1, bubbleH - 1, bubbleH, bubbleH);
                    }

                    if (txt != null && !txt.isBlank()) {
                        g2.setColor(EdoUi.TEXT_BLACK);
                        int tx = x + (bubbleW - fm.stringWidth(txt)) / 2;
                        int ty = yMid + (fm.getAscent() / 2) - 1;
                        g2.drawString(txt, tx, ty);
                    }

                    x += bubbleW + gap;
                }

            } finally {
                g2.dispose();
            }
        }
        private static Font iconFont(Font uiFont) {
            int size = uiFont.getSize() - 1;
            if (size < 10) {
                size = uiFont.getSize();
            }

            String family = "SansSerif";
            Font f = new Font(family, Font.BOLD, size);

            // If that didn't actually give us SansSerif (odd platform/font issues), fall back.
            if (!family.equalsIgnoreCase(f.getFamily())) {
                f = new Font(Font.MONOSPACED, Font.PLAIN, size);
            }

            return f;
        }

        // Fixed formatting:
        // - meters: integer up to 999m display
        // - km: nearest tenth
        private static String formatMetersFixed(double m) {
            if (Double.isNaN(m) || Double.isInfinite(m)) {
                return "";
            }

            if (m < 1000.0) {
                long mm = Math.round(m);
                if (mm > 999) {
                    mm = 999;
                }
                return String.format(Locale.US, "%dm", Long.valueOf(mm));
            }

            double km = m / 1000.0;
            // nearest tenth: String.format rounds
            return String.format(Locale.US, "%.1fkm", Double.valueOf(km));
        }
    }
    private void updateVoiceTransitions() {
        if (currentLat == null || currentLon == null || currentPlanetRadius == null) {
            return;
        }
        if (systemTab == null) {
            return;
        }
        if (currentBodyName == null || currentBodyName.isBlank()) {
            return;
        }

        SystemState state = systemTab.getState();
        if (state == null) {
            return;
        }

        BodyInfo body = findBodyForExoBio(state, currentBodyName, currentBodyId);
        if (body == null) {
            return;
        }

        // Build the same rows the UI uses so we pick the same “active” in-progress item.
        List<BioRow> rows = buildRows(body);
        if (rows == null || rows.isEmpty()) {
            return;
        }

        // Find the first in-progress row (1/3 or 2/3) that has at least one recorded sample point.
        BioRow active = null;
        for (BioRow r : rows) {
            if (r == null) {
                continue;
            }
            if (r.analysed || r.sampleCount <= 0) {
                continue;
            }
            if (r.points == null || r.points.isEmpty()) {
                continue;
            }
            active = r;
            break;
        }

        if (active == null) {
            return;
        }

        int needed = active.requiredMeters;
        if (needed <= 0) {
            // Fall back to lookup by genus if the row didn’t get it for some reason.
            needed = BioColonyDistance.metersForBio(active.genusKey);
        }
        if (needed <= 0) {
            return;
        }

        BodyInfo.BioSamplePoint last = active.points.get(active.points.size() - 1);

        double distM = greatCircleMeters(
                currentLat.doubleValue(),
                currentLon.doubleValue(),
                last.getLatitude(),
                last.getLongitude(),
                currentPlanetRadius.doubleValue());

        boolean inside = distM < needed;

        // Use canonical key to avoid duplicate state for casing differences.
        String bioKey = canonicalBioKey(active.displayName);

        Double prevDist = lastDistMByBioKey.get(bioKey);
        Boolean prev = insideStateByBioKey.put(bioKey, Boolean.valueOf(inside));
        if (prev == null) {
            // First time we’ve evaluated this target; don’t speak.
            lastDistMByBioKey.put(bioKey, Double.valueOf(distM));
            return;
        }

        if (prev.booleanValue() == inside) {
            lastDistMByBioKey.put(bioKey, Double.valueOf(distM));
            return;
        }

        // Announce transition. Replaceables wrapped in {} for caching.
        if (inside) {
            boolean suppressByRecentSample = System.currentTimeMillis()
                    < suppressEnterUntilMsByBioKey.getOrDefault(bioKey, 0L).longValue();
            boolean suddenCollapseNearSample = prevDist != null
                    && (prevDist.doubleValue() - distM) >= SUDDEN_DISTANCE_COLLAPSE_METERS
                    && distM <= NEAR_LAST_SAMPLE_METERS;
            if (!suppressByRecentSample && !suddenCollapseNearSample) {
                tts.speakf("Entering clonal colony range of {species}. Minimum {meters} meters.",
                        active.displayName,
                        Integer.valueOf(needed));
            }
        } else {
            tts.speakf("Leaving clonal colony range of {species}. Minimum {meters} meters.",
                    active.displayName,
                    Integer.valueOf(needed));
        }
        lastDistMByBioKey.put(bioKey, Double.valueOf(distM));
    }



private final class BioMapPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private double shipLat;
    private double shipLon;
    private double shipRadiusM;
    private boolean haveShip;

    private double srvLat;
    private double srvLon;
    private boolean haveSrv;
    /** SRV nose heading (degrees); frozen when not driving. */
    private Double srvGlyphHeadingDeg;

    /** Player heading (0=N, clockwise): ship nose in hull; movement direction on foot / in SRV. */
    private double shipHeadingDeg;
    /** When true, map rotates so player heading points up; when false, north points up. */
    private boolean headingUpMode = true;
    /** Ship nose heading (degrees); from Status while in ship, frozen when parked. */
    private Double shipGlyphHeadingDeg;
    /** Commander / player nose heading (degrees) for the white V — Status heading, else movement. */
    private Double playerHeadingDeg;

    /** When true, map center follows the commander; parked ship is drawn at an offset. */
    private boolean commanderCentered;
    /** Commander is driving the SRV (no separate parked SRV glyph at center). */
    private boolean commanderInSrv;
    private double commanderLat;
    private double commanderLon;

    /** Parked sample locations (canonical display-name key → points). */
    private Map<String, List<BodyInfo.BioSamplePoint>> abandonedByKey = Collections.emptyMap();
    private String activeIncompleteBioKey;
    /** Purple “parked” pins from incomplete genera left behind when switching scans. */
    private boolean showParkedPins;

    /** Content-relative zoom floor (zoom 1 = auto-fit all pins from the map anchor). */
    private static final double BIO_MAP_ZOOM_MIN = 0.008;
    /** Hard floor so wheel/button zoom can reach full-planet overview on very large bodies. */
    private static final double BIO_MAP_ZOOM_ABSOLUTE_FLOOR = 1e-6;
    /** Deep zoom for tight sample clusters when a far pin/ship/bookmark sets the auto-fit span. */
    private static final double BIO_MAP_ZOOM_MAX = 48.0;
    private static final double BIO_MAP_ZOOM_STEP = 1.18;
    private double mapZoomFactor = 1.0;
    private final Rectangle zoomInHit = new Rectangle();
    private final Rectangle zoomOutHit = new Rectangle();
    private final Rectangle bookmarkHit = new Rectangle();
    private final Rectangle compassHit = new Rectangle();
    private boolean bookmarkButtonHovered;
    private boolean compassButtonHovered;
    private List<BioMapBookmark> mapBookmarks = Collections.emptyList();

    /** Drag pan offset from commander/ship-centred view (pixels). */
    private int mapPanPxX;
    private int mapPanPxY;
    private boolean mapPanDragActive;
    private int mapPanDragLastX;
    private int mapPanDragLastY;

    private BioMapPanel() {
        setOpaque(false);
        MouseAdapter mapMouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (OverlayPreferences.isOverlayFullMousePassThrough()) {
                    return;
                }
                Point p = e.getPoint();
                if (compassHit.contains(p)) {
                    toggleHeadingUpMode();
                    return;
                }
                if (bookmarkHit.contains(p)) {
                    BiologyTabPanel.this.addMapBookmarkAtCurrentPosition();
                    return;
                }
                if (zoomInHit.contains(p)) {
                    adjustMapZoom(true);
                    return;
                }
                if (zoomOutHit.contains(p)) {
                    adjustMapZoom(false);
                    return;
                }
                if (SwingUtilities.isMiddleMouseButton(e)
                        || (SwingUtilities.isLeftMouseButton(e) && canPaintMap())) {
                    mapPanDragActive = true;
                    mapPanDragLastX = e.getX();
                    mapPanDragLastY = e.getY();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    e.consume();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (OverlayPreferences.isOverlayFullMousePassThrough()) {
                    return;
                }
                if (mapPanDragActive
                        && (SwingUtilities.isMiddleMouseButton(e) || SwingUtilities.isLeftMouseButton(e))) {
                    mapPanDragActive = false;
                    setCursor(Cursor.getDefaultCursor());
                    e.consume();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (OverlayPreferences.isOverlayFullMousePassThrough() || !mapPanDragActive) {
                    return;
                }
                int x = e.getX();
                int y = e.getY();
                applyMapPanPixelDelta(x - mapPanDragLastX, y - mapPanDragLastY);
                mapPanDragLastX = x;
                mapPanDragLastY = y;
                e.consume();
            }
        };
        addMouseListener(mapMouse);
        addMouseMotionListener(mapMouse);
        addMouseWheelListener(this::handleMouseWheel);
    }

    private void handleMouseWheel(MouseWheelEvent e) {
        if (!canPaintMap()) {
            return;
        }
        int rot = e.getWheelRotation();
        if (rot == 0) {
            return;
        }
        adjustMapZoom(rot < 0);
        e.consume();
    }

    void setMapBookmarks(List<BioMapBookmark> bookmarks) {
        if (bookmarks == null || bookmarks.isEmpty()) {
            mapBookmarks = Collections.emptyList();
        } else {
            mapBookmarks = new ArrayList<>(bookmarks);
        }
        repaint();
    }

    void setBookmarkButtonHovered(boolean hovered) {
        if (this.bookmarkButtonHovered != hovered) {
            this.bookmarkButtonHovered = hovered;
            repaint();
        }
    }

    void setCompassButtonHovered(boolean hovered) {
        if (this.compassButtonHovered != hovered) {
            this.compassButtonHovered = hovered;
            repaint();
        }
    }

    void toggleHeadingUpMode() {
        headingUpMode = !headingUpMode;
        repaint();
    }

    boolean isCompassAtScreen(int screenX, int screenY) {
        return isMapControlAtScreen(screenX, screenY, compassHit);
    }

    boolean isBookmarkButtonAtScreen(int screenX, int screenY) {
        return isMapControlAtScreen(screenX, screenY, bookmarkHit);
    }

    boolean isZoomInButtonAtScreen(int screenX, int screenY) {
        return isMapControlAtScreen(screenX, screenY, zoomInHit);
    }

    boolean isZoomOutButtonAtScreen(int screenX, int screenY) {
        return isMapControlAtScreen(screenX, screenY, zoomOutHit);
    }

    boolean applyPassThroughWheelIfHit(int screenX, int screenY, int wheelRotation) {
        if (!isShowing() || wheelRotation == 0 || !canPaintMap()) {
            return false;
        }
        Point p = new Point(screenX, screenY);
        SwingUtilities.convertPointFromScreen(p, this);
        if (!contains(p)) {
            return false;
        }
        if (wheelRotation < 0) {
            adjustMapZoom(true);
        } else {
            adjustMapZoom(false);
        }
        return true;
    }

    private boolean isMapControlAtScreen(int screenX, int screenY, Rectangle hit) {
        if (!isShowing() || hit == null || hit.isEmpty()) {
            return false;
        }
        Point p = new Point(screenX, screenY);
        SwingUtilities.convertPointFromScreen(p, this);
        return hit.contains(p);
    }

    /** Map anchor (commander or ship) for a new bookmark; {@code null} if no fix. */
    double[] getMapAnchorLatLon() {
        if (!canPaintMap()) {
            return null;
        }
        return new double[] { mapOriginLat(), mapOriginLon() };
    }

    void adjustMapZoom(boolean zoomIn) {
        double next = mapZoomFactor * (zoomIn ? BIO_MAP_ZOOM_STEP : 1.0 / BIO_MAP_ZOOM_STEP);
        mapZoomFactor = Math.max(resolveBioMapZoomMin(), Math.min(BIO_MAP_ZOOM_MAX, next));
        repaint();
    }

    /**
     * Minimum zoom factor for the current body and pin spread. Allows zooming out until the view span reaches
     * the body's full surface circumference ({@code 2π × radius}).
     */
    private double resolveBioMapZoomMin() {
        double maxDistM = computeMaxDistM(model.getRowsSnapshot(), haveAbandonedSamplePins());
        double min = BIO_MAP_ZOOM_MIN;
        if (shipRadiusM > 100.0) {
            double planetCircumferenceM = 2.0 * Math.PI * shipRadiusM;
            if (planetCircumferenceM > maxDistM) {
                min = Math.min(min, maxDistM / planetCircumferenceM);
            }
        }
        return Math.max(BIO_MAP_ZOOM_ABSOLUTE_FLOOR, min);
    }

    private boolean haveAbandonedSamplePins() {
        if (!showParkedPins || abandonedByKey == null) {
            return false;
        }
        for (Map.Entry<String, List<BodyInfo.BioSamplePoint>> e : abandonedByKey.entrySet()) {
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Drag pan: shift the map view in pixels ({@code dx} right moves map content right with the cursor).
     */
    private void applyMapPanPixelDelta(int dxPix, int dyPix) {
        if (!canPaintMap() || (dxPix == 0 && dyPix == 0)) {
            return;
        }
        mapPanPxX += dxPix;
        mapPanPxY += dyPix;
        repaint();
    }

    /** True when the commander/ship anchor (map origin) is outside the plot due to pan offset. */
    private boolean isMapAnchorOffScreen() {
        if (!canPaintMap() || (mapPanPxX == 0 && mapPanPxY == 0)) {
            return false;
        }
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return false;
        }
        int ax = w / 2 + mapPanPxX;
        int ay = h / 2 + mapPanPxY;
        return ax < 0 || ax >= w || ay < 0 || ay >= h;
    }

    /** After a position update, recenter only if the player anchor was panned off the map. */
    private void maybeRecenterPanIfAnchorOffMap() {
        if (isMapAnchorOffScreen()) {
            mapPanPxX = 0;
            mapPanPxY = 0;
        }
    }

    private static boolean surfaceFixChanged(double prevLat, double prevLon, double lat, double lon) {
        return Double.compare(prevLat, lat) != 0 || Double.compare(prevLon, lon) != 0;
    }

    void setShowParkedPins(boolean show) {
        this.showParkedPins = show;
        repaint();
    }

    void setAbandonedSamplePins(Map<String, List<BodyInfo.BioSamplePoint>> byKey) {
        if (byKey == null || byKey.isEmpty()) {
            abandonedByKey = Collections.emptyMap();
        } else {
            Map<String, List<BodyInfo.BioSamplePoint>> copy = new HashMap<>();
            for (Map.Entry<String, List<BodyInfo.BioSamplePoint>> e : byKey.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue().isEmpty()) {
                    continue;
                }
                copy.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
            abandonedByKey = copy.isEmpty() ? Collections.emptyMap() : copy;
        }
        repaint();
    }

    void setActiveIncompleteBioKey(String key) {
        this.activeIncompleteBioKey = (key == null || key.isBlank()) ? null : key;
        repaint();
    }

    private void setShipLatLon(double lat, double lon, double radiusM) {
        boolean posMoved = haveShip && !commanderCentered
                && surfaceFixChanged(shipLat, shipLon, lat, lon);
        this.shipLat = lat;
        this.shipLon = lon;
        this.shipRadiusM = radiusM;
        this.haveShip = true;
        if (posMoved) {
            maybeRecenterPanIfAnchorOffMap();
        }
        repaint();
    }

    private void clearShipPosition() {
        this.haveShip = false;
        repaint();
    }

    private void setSrvLatLon(double lat, double lon, double radiusM) {
        this.srvLat = lat;
        this.srvLon = lon;
        this.shipRadiusM = radiusM;
        this.haveSrv = true;
        repaint();
    }

    private void clearSrvPosition() {
        this.haveSrv = false;
        repaint();
    }

    private void setSrvGlyphHeadingDeg(Double headingDeg) {
        this.srvGlyphHeadingDeg = headingDeg;
        repaint();
    }

    private void setCommanderInSrv(boolean inSrv) {
        this.commanderInSrv = inSrv;
        repaint();
    }

    private void setShipHeadingDeg(double headingDeg) {
        this.shipHeadingDeg = headingDeg;
        repaint();
    }

    private void setShipGlyphHeadingDeg(Double headingDeg) {
        this.shipGlyphHeadingDeg = headingDeg;
        repaint();
    }

    private void setPlayerHeadingDeg(Double headingDeg) {
        this.playerHeadingDeg = headingDeg;
        repaint();
    }

    /** Map rotation for projection and glyph alignment (0 when north-up). */
    private double mapProjectionHeadingDeg() {
        return headingUpMode ? shipHeadingDeg : 0.0;
    }

    /** Degrees to rotate ship glyph so nose matches true heading on the heading-up map. */
    private double shipGlyphRotationDeg() {
        if (shipGlyphHeadingDeg == null) {
            return 0.0;
        }
        double rot = shipGlyphHeadingDeg.doubleValue() - mapProjectionHeadingDeg();
        rot %= 360.0;
        if (rot < 0.0) {
            rot += 360.0;
        }
        return rot;
    }

    private double srvGlyphRotationDeg() {
        if (srvGlyphHeadingDeg == null) {
            return 0.0;
        }
        double rot = srvGlyphHeadingDeg.doubleValue() - mapProjectionHeadingDeg();
        rot %= 360.0;
        if (rot < 0.0) {
            rot += 360.0;
        }
        return rot;
    }

    /** True when the parked SRV should appear as a separate blue marker (not while driving it). */
    private boolean showParkedSrvMarker() {
        return haveSrv && !commanderInSrv;
    }

    /** True when the commander is driving the SRV at map center (blue hull under the white V). */
    private boolean showDrivingSrvAtCenter() {
        return commanderCentered && commanderInSrv && haveSrv;
    }

    /** White V rotation at map center — player true heading on the heading-up map. */
    private double playerVNoseRotationDeg() {
        if (playerHeadingDeg == null) {
            return 0.0;
        }
        double rot = playerHeadingDeg.doubleValue() - mapProjectionHeadingDeg();
        rot %= 360.0;
        if (rot < 0.0) {
            rot += 360.0;
        }
        return rot;
    }

    private void setCommanderCentered(boolean centered, double lat, double lon, boolean inSrv) {
        boolean posMoved = centered && commanderCentered
                && surfaceFixChanged(commanderLat, commanderLon, lat, lon);
        this.commanderCentered = centered;
        this.commanderInSrv = inSrv;
        this.commanderLat = lat;
        this.commanderLon = lon;
        if (posMoved) {
            maybeRecenterPanIfAnchorOffMap();
        }
        repaint();
    }

    private void clearCommanderCentered() {
        this.commanderCentered = false;
        this.commanderInSrv = false;
        repaint();
    }

    private double mapOriginLat() {
        return commanderCentered ? commanderLat : shipLat;
    }

    private double mapOriginLon() {
        return commanderCentered ? commanderLon : shipLon;
    }

    private boolean canPaintMap() {
        return commanderCentered || haveShip || haveSrv;
    }

    /** Map pixels for a world fix relative to the current map origin and heading. */
    private int[] worldToMapPx(int cx, int cy, double scale, double worldLat, double worldLon) {
        double oLat = mapOriginLat();
        double oLon = mapOriginLon();
        double d = greatCircleMeters(oLat, oLon, worldLat, worldLon, shipRadiusM);
        double brng = bearingDeg(oLat, oLon, worldLat, worldLon);
        double rel = Math.toRadians(brng - mapProjectionHeadingDeg());
        int px = cx + (int) Math.round(Math.sin(rel) * d * scale);
        int py = cy + (int) Math.round(-Math.cos(rel) * d * scale);
        return new int[] { px, py };
    }

    private void layoutCompassHit(int x0, int y0, int plotW) {
        int pad = 10;
        int compR = 22;
        int compCx = x0 + plotW - pad - compR;
        int compCy = y0 + pad + compR;
        compassHit.setBounds(compCx - compR, compCy - compR, compR * 2, compR * 2);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int plotW = Math.max(1, w);
            int plotH = Math.max(1, h);
            int x0 = 0;
            int y0 = 0;
            int fit = Math.min(plotW, plotH);

            // Border / background
            if (!OverlayPreferences.isOverlayTransparent()) {
                g2.setColor(EdoUi.Internal.BLACK_ALPHA_80);
                g2.fillRect(x0, y0, plotW, plotH);
            }
            g2.setColor(EdoUi.Internal.MAIN_TEXT_ALPHA_140);

            if (!canPaintMap()) {
                g2.setColor(Color.WHITE);
                String msg = "No position";
                FontMetrics fm = g2.getFontMetrics();
                int tx = x0 + (plotW - fm.stringWidth(msg)) / 2;
                int ty = y0 + (plotH + fm.getAscent()) / 2;
                g2.drawString(msg, tx, ty);
                return;
            }

            int cx = x0 + plotW / 2;
            int cy = y0 + plotH / 2;
            int viewCx = cx + mapPanPxX;
            int viewCy = cy + mapPanPxY;

            // Sample pins: parked history first, then active incomplete scans.
            java.util.List<BioRow> rows = model.getRowsSnapshot();
            boolean haveAbandonedPins = haveAbandonedSamplePins();
            boolean haveActivePins = false;
            if (rows != null) {
                for (BioRow row : rows) {
                    if (row == null || row.analysed || row.points == null || row.points.isEmpty()) {
                        continue;
                    }
                    haveActivePins = true;
                    break;
                }
            }
            boolean haveSamplePins = haveActivePins || haveAbandonedPins;
            if ((rows == null || rows.isEmpty()) && !haveAbandonedPins && !commanderCentered) {
                g2.setColor(Color.WHITE);
                String msg = "No specimens detected";
                FontMetrics fm = g2.getFontMetrics();
                int tx = x0 + (plotW - fm.stringWidth(msg)) / 2;
                int ty = y0 + (plotH + fm.getAscent()) / 2;
                g2.drawString(msg, tx, ty);
            }

            double maxDistM = computeMaxDistM(rows, haveAbandonedPins);
            double fitSpanM = Math.max(25.0, maxDistM / mapZoomFactor);
            double scale = (fit * 0.42) / fitSpanM;
            int plotRx2 = x0 + plotW;
            int plotRy2 = y0 + plotH;
            boolean pointsMode =
                    OverlayPreferences.getBiologyMapDisplayMode() == OverlayPreferences.BiologyMapDisplayMode.POINTS;

            java.util.List<BioMapRayLabel> rayLabels = new ArrayList<>();

            java.awt.Shape oldClip = g2.getClip();
            g2.setClip(x0, y0, plotW, plotH);
            drawLatLonGrid(g2, viewCx, viewCy, scale, fitSpanM, x0, y0, plotW, plotH);
            g2.setClip(oldClip);

            double shipRot = shipGlyphRotationDeg();
            double srvRot = srvGlyphRotationDeg();
            int[] shipPx = null;
            int[] srvPx = null;
            if (commanderCentered && haveShip) {
                shipPx = worldToMapPx(viewCx, viewCy, scale, shipLat, shipLon);
                drawVehicleRayLine(g2, viewCx, viewCy, shipPx[0], shipPx[1], BIO_MAP_SHIP_RAY);
                queueVehicleRayLabel(
                        rayLabels, viewCx, viewCy, shipPx[0], shipPx[1],
                        commanderLat, commanderLon, shipLat, shipLon, 1);
            }
            if (showParkedSrvMarker()) {
                srvPx = worldToMapPx(viewCx, viewCy, scale, srvLat, srvLon);
                if (commanderCentered) {
                    drawVehicleRayLine(g2, viewCx, viewCy, srvPx[0], srvPx[1], BIO_MAP_SRV_RAY);
                    queueVehicleRayLabel(
                            rayLabels, viewCx, viewCy, srvPx[0], srvPx[1],
                            commanderLat, commanderLon, srvLat, srvLon, 1);
                }
            }

            if (rows != null) {
                for (BioRow row : rows) {
                    if (row == null || !row.analysed || row.points == null || row.points.isEmpty()) {
                        continue;
                    }
                    for (BodyInfo.BioSamplePoint p : row.points) {
                        int[] end = sampleRayEndPx(viewCx, viewCy, scale, p);
                        drawBioSampleCompleteMarker(g2, end[0], end[1]);
                    }
                }
            }

            if (haveSamplePins) {
                if (pointsMode) {
                    if (showParkedPins && abandonedByKey != null) {
                        for (Map.Entry<String, List<BodyInfo.BioSamplePoint>> e : abandonedByKey.entrySet()) {
                            if (e.getValue() == null || e.getValue().isEmpty()) {
                                continue;
                            }
                            Color parkedColor = activeIncompleteBioKey != null && activeIncompleteBioKey.equals(e.getKey())
                                    ? BIO_MAP_RESUMED_GENUS_HISTORY
                                    : BIO_MAP_ABANDONED_SAMPLE;
                            for (BodyInfo.BioSamplePoint p : e.getValue()) {
                                int[] clipped = clipSampleDisplayPoint(
                                        viewCx, viewCy, scale, p, x0, y0, plotRx2, plotRy2);
                                drawBioSamplePointMarker(g2, clipped[0], clipped[1], parkedColor);
                                queueBioSamplePointLabel(
                                        rayLabels, viewCx, viewCy, scale, p, e.getKey(), 2,
                                        x0, y0, plotRx2, plotRy2);
                            }
                        }
                    }
                    if (rows != null) {
                        for (BioRow row : rows) {
                            if (row == null || row.analysed || row.points == null || row.points.isEmpty()) {
                                continue;
                            }
                            for (BodyInfo.BioSamplePoint p : row.points) {
                                int[] clipped = clipSampleDisplayPoint(
                                        viewCx, viewCy, scale, p, x0, y0, plotRx2, plotRy2);
                                drawBioSamplePointMarker(g2, clipped[0], clipped[1], BIO_MAP_ACTIVE_SAMPLE);
                                queueBioSamplePointLabel(
                                        rayLabels, viewCx, viewCy, scale, p, row.displayName, 3,
                                        x0, y0, plotRx2, plotRy2);
                            }
                        }
                    }
                } else {
                    g2.setStroke(new BasicStroke(2f));
                    if (showParkedPins && abandonedByKey != null) {
                        for (Map.Entry<String, List<BodyInfo.BioSamplePoint>> e : abandonedByKey.entrySet()) {
                            if (e.getValue() == null || e.getValue().isEmpty()) {
                                continue;
                            }
                            Color parkedColor = activeIncompleteBioKey != null && activeIncompleteBioKey.equals(e.getKey())
                                    ? BIO_MAP_RESUMED_GENUS_HISTORY
                                    : BIO_MAP_ABANDONED_SAMPLE;
                            for (BodyInfo.BioSamplePoint p : e.getValue()) {
                                drawBioSampleRayLine(g2, viewCx, viewCy, scale, parkedColor, p);
                                queueBioSampleRayLabel(rayLabels, viewCx, viewCy, scale, p, e.getKey(), 2);
                            }
                        }
                    }
                    if (rows != null) {
                        for (BioRow row : rows) {
                            if (row == null || row.analysed || row.points == null || row.points.isEmpty()) {
                                continue;
                            }
                            for (BodyInfo.BioSamplePoint p : row.points) {
                                drawBioSampleRayLine(g2, viewCx, viewCy, scale, BIO_MAP_ACTIVE_SAMPLE, p);
                                queueBioSampleRayLabel(rayLabels, viewCx, viewCy, scale, p, row.displayName, 3);
                            }
                        }
                    }
                }
            }

            if (haveShip && showParkedSrvMarker() && !commanderCentered) {
                if (srvPx == null) {
                    srvPx = worldToMapPx(viewCx, viewCy, scale, srvLat, srvLon);
                }
                drawVehicleRayLine(g2, viewCx, viewCy, srvPx[0], srvPx[1], BIO_MAP_SRV_RAY);
                queueVehicleRayLabel(rayLabels, viewCx, viewCy, srvPx[0], srvPx[1], shipLat, shipLon, srvLat, srvLon, 1);
            }

            deconflictAndDrawRayLabels(g2, rayLabels, x0, y0, plotRx2, plotRy2);

            if (!mapBookmarks.isEmpty()) {
                for (BioMapBookmark b : mapBookmarks) {
                    int[] px = worldToMapPx(viewCx, viewCy, scale, b.lat, b.lon);
                    drawMapBookmarkStar(g2, px[0], px[1]);
                }
            }

            if (commanderCentered) {
                if (shipPx != null) {
                    drawShipLocationMarker(g2, shipPx[0], shipPx[1], shipRot);
                }
                if (srvPx != null) {
                    drawSrvLocationMarker(g2, srvPx[0], srvPx[1], srvRot);
                } else if (showDrivingSrvAtCenter()) {
                    drawSrvLocationMarker(g2, viewCx, viewCy, playerVNoseRotationDeg());
                }
                drawHeadingVNose(g2, viewCx, viewCy, playerVNoseRotationDeg());
            } else if (haveShip) {
                drawShipLocationMarker(g2, viewCx, viewCy, shipRot);
                drawHeadingVNose(g2, viewCx, viewCy, shipRot);
                if (showParkedSrvMarker() && srvPx != null) {
                    drawSrvLocationMarker(g2, srvPx[0], srvPx[1], srvRot);
                }
            }

            // Compass (upper right) — click toggles heading-up / north-up
            layoutCompassHit(x0, y0, plotW);
            int compCx = compassHit.x + compassHit.width / 2;
            int compCy = compassHit.y + compassHit.height / 2;
            int compR = compassHit.width / 2;

            g2.setColor(compassButtonHovered ? new Color(70, 70, 90, 200) : EdoUi.Internal.BLACK_ALPHA_140);
            g2.fill(new Ellipse2D.Double(compCx - compR, compCy - compR, compR * 2, compR * 2));
            g2.setColor(compassButtonHovered ? new Color(255, 255, 255, 240) : EdoUi.Internal.WHITE_ALPHA_200);
            g2.draw(new Ellipse2D.Double(compCx - compR, compCy - compR, compR * 2, compR * 2));

            double relN = Math.toRadians(0.0 - mapProjectionHeadingDeg());
            double nx = Math.sin(relN);
            double ny = -Math.cos(relN);

            int nx2 = compCx + (int) Math.round(nx * (compR - 4));
            int ny2 = compCy + (int) Math.round(ny * (compR - 4));

            g2.draw(new Line2D.Double(compCx, compCy, nx2, ny2));
            g2.drawString("N", nx2 - 4, ny2 - 2);

            if (canPaintMap()) {
                drawMapZoomControls(g2, x0, y0, plotW, plotH);
            }
        } finally {
            g2.dispose();
        }
        OverlayFrame frame = OverlayFrame.overlayFrame;
        if (frame != null) {
            frame.scheduleNativePassThroughReapplyAfterPaint();
        }
    }

    /**
     * White V at the nose of the ship triangle (or player position when on foot / in SRV).
     * {@code rotationDeg} is nose heading relative to map up, same as {@link #drawShipLocationMarker}.
     */
    private static void drawHeadingVNose(Graphics2D g2, float cx, float cy, double rotationDeg) {
        AffineTransform saved = g2.getTransform();
        if (Math.abs(rotationDeg) > 0.05) {
            g2.rotate(Math.toRadians(rotationDeg), cx, cy);
        }
        try {
            float halfW = 5f;
            float apexY = cy - 9f;
            float legY = cy + 2f;

            Path2D.Float v = new Path2D.Float();
            v.moveTo(cx - halfW, legY);
            v.lineTo(cx, apexY);
            v.lineTo(cx + halfW, legY);

            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(v);
        } finally {
            g2.setTransform(saved);
        }
    }

    /**
     * Grid line spacing in metres — tighter when tracked features are farther from the map centre
     * (more lines across the same view).
     */
    private static double chooseGridSpacingMeters(double maxDistM) {
        double span = Math.max(50.0, maxDistM);
        double raw = span / 5.0;
        double mag = Math.pow(10.0, Math.floor(Math.log10(raw)));
        double norm = raw / mag;
        double nice;
        if (norm <= 1.5) {
            nice = 1.0;
        } else if (norm <= 3.5) {
            nice = 2.0;
        } else if (norm <= 7.5) {
            nice = 5.0;
        } else {
            nice = 10.0;
        }
        return Math.max(10.0, nice * mag);
    }

    /** Local lat/lon grid aligned to the map origin; drawn in heading-up map space. */
    private void drawLatLonGrid(
            Graphics2D g2,
            int cx,
            int cy,
            double scale,
            double maxDistM,
            int x0,
            int y0,
            int plotW,
            int plotH) {
        double oLat = mapOriginLat();
        double oLon = mapOriginLon();
        double spacingM = chooseGridSpacingMeters(maxDistM);
        double metersPerDegLat = shipRadiusM * Math.PI / 180.0;
        double cosLat = Math.cos(Math.toRadians(oLat));
        double metersPerDegLon = metersPerDegLat * Math.max(0.01, Math.abs(cosLat));
        double deltaLatDeg = spacingM / metersPerDegLat;
        double deltaLonDeg = spacingM / metersPerDegLon;

        int clipX1 = x0;
        int clipY1 = y0;
        int clipX2 = x0 + plotW;
        int clipY2 = y0 + plotH;
        int fit = Math.min(plotW, plotH);
        int maxSegmentPx = Math.max(48, (int) Math.round(fit * 0.55));
        int stepsAlong = Math.max(32, Math.min(128, fit / 3));

        double[] bounds = mapPlotLatLonBounds(cx, cy, scale, x0, y0, plotW, plotH, spacingM);
        double latMin = bounds[0];
        double latMax = bounds[1];
        double lonMin = bounds[2];
        double lonMax = bounds[3];

        double latStart = oLat + Math.floor((latMin - oLat) / deltaLatDeg) * deltaLatDeg;
        double lonStart = oLon + Math.floor((lonMin - oLon) / deltaLonDeg) * deltaLonDeg;

        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int latIndex = 0;
        for (double lat = latStart; lat <= latMax + deltaLatDeg * 0.001; lat += deltaLatDeg) {
            g2.setColor(gridLineColor(latIndex, 5));
            drawLatitudeGridLine(
                    g2, cx, cy, scale, lat, lonMin, lonMax, stepsAlong,
                    clipX1, clipY1, clipX2, clipY2, maxSegmentPx);
            latIndex++;
        }
        int lonIndex = 0;
        for (double lon = lonStart; lon <= lonMax + deltaLonDeg * 0.001; lon += deltaLonDeg) {
            g2.setColor(gridLineColor(lonIndex, 5));
            drawLongitudeGridLine(
                    g2, cx, cy, scale, lon, latMin, latMax, stepsAlong,
                    clipX1, clipY1, clipX2, clipY2, maxSegmentPx);
            lonIndex++;
        }

        g2.setStroke(oldStroke);

        String spacingLabel = formatMetersFixed(spacingM) + " grid";
        Font oldFont = g2.getFont();
        Font small = oldFont.deriveFont(Math.max(9f, oldFont.getSize2D() * 0.85f));
        g2.setFont(small);
        g2.setColor(BIO_MAP_GRID_MAJOR);
        FontMetrics fm = g2.getFontMetrics();
        int lx = x0 + 6;
        int ly = y0 + plotH - 6;
        g2.drawString(spacingLabel, lx, ly - fm.getDescent());
        g2.setFont(oldFont);
    }

    /**
     * Lat/lon range that covers the map plot in heading-up projection (corners + edge midpoints).
     */
    private double[] mapPlotLatLonBounds(
            int cx, int cy, double scale, int x0, int y0, int plotW, int plotH, double spacingM) {
        double latMin = Double.POSITIVE_INFINITY;
        double latMax = Double.NEGATIVE_INFINITY;
        double lonMin = Double.POSITIVE_INFINITY;
        double lonMax = Double.NEGATIVE_INFINITY;
        int x1 = x0;
        int y1 = y0;
        int x2 = x0 + plotW;
        int y2 = y0 + plotH;
        int[][] samples = {
                { x1, y1 },
                { x2, y1 },
                { x1, y2 },
                { x2, y2 },
                { cx, y1 },
                { cx, y2 },
                { x1, cy },
                { x2, cy },
                { cx, cy },
        };
        for (int[] p : samples) {
            double[] ll = mapPxToWorldLatLon(p[0], p[1], cx, cy, scale);
            latMin = Math.min(latMin, ll[0]);
            latMax = Math.max(latMax, ll[0]);
            lonMin = Math.min(lonMin, ll[1]);
            lonMax = Math.max(lonMax, ll[1]);
        }
        double padLat = Math.max(spacingM / (shipRadiusM * Math.PI / 180.0), 1e-9);
        double cosLat = Math.cos(Math.toRadians(mapOriginLat()));
        double padLon = Math.max(spacingM / (shipRadiusM * Math.PI / 180.0 * Math.max(0.01, Math.abs(cosLat))), 1e-9);
        return new double[] { latMin - padLat, latMax + padLat, lonMin - padLon, lonMax + padLon };
    }

    private double[] mapPxToWorldLatLon(double px, double py, int cx, int cy, double scale) {
        double dxPx = px - cx;
        double dyPx = py - cy;
        if (scale <= 1e-12) {
            return new double[] { mapOriginLat(), mapOriginLon() };
        }
        double distM = Math.hypot(dxPx, dyPx) / scale;
        double relDeg = Math.toDegrees(Math.atan2(dxPx, -dyPx));
        double brng = mapProjectionHeadingDeg() + relDeg;
        return surfacePointAtBearing(mapOriginLat(), mapOriginLon(), brng, distM, shipRadiusM);
    }

    private static Color gridLineColor(int index, int majorEvery) {
        if (index == 0) {
            return BIO_MAP_GRID_ORIGIN;
        }
        if (majorEvery > 0 && index % majorEvery == 0) {
            return BIO_MAP_GRID_MAJOR;
        }
        return BIO_MAP_GRID_MINOR;
    }

    private void drawLatitudeGridLine(
            Graphics2D g2,
            int cx,
            int cy,
            double scale,
            double lat,
            double lonMin,
            double lonMax,
            int steps,
            int clipX1,
            int clipY1,
            int clipX2,
            int clipY2,
            int maxSegmentPx) {
        Integer prevX = null;
        Integer prevY = null;
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / (double) steps;
            double lon = lonMin + (lonMax - lonMin) * t;
            int[] px = worldToMapPx(cx, cy, scale, lat, lon);
            if (prevX != null) {
                appendClippedGridSegment(
                        g2,
                        prevX.intValue(),
                        prevY.intValue(),
                        px[0],
                        px[1],
                        clipX1,
                        clipY1,
                        clipX2,
                        clipY2,
                        maxSegmentPx);
            }
            prevX = Integer.valueOf(px[0]);
            prevY = Integer.valueOf(px[1]);
        }
    }

    private void drawLongitudeGridLine(
            Graphics2D g2,
            int cx,
            int cy,
            double scale,
            double lon,
            double latMin,
            double latMax,
            int steps,
            int clipX1,
            int clipY1,
            int clipX2,
            int clipY2,
            int maxSegmentPx) {
        Integer prevX = null;
        Integer prevY = null;
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / (double) steps;
            double lat = latMin + (latMax - latMin) * t;
            int[] px = worldToMapPx(cx, cy, scale, lat, lon);
            if (prevX != null) {
                appendClippedGridSegment(
                        g2,
                        prevX.intValue(),
                        prevY.intValue(),
                        px[0],
                        px[1],
                        clipX1,
                        clipY1,
                        clipX2,
                        clipY2,
                        maxSegmentPx);
            }
            prevX = Integer.valueOf(px[0]);
            prevY = Integer.valueOf(px[1]);
        }
    }

    /**
     * Stroke one grid segment; skip only true projection jumps (not gentle curves near the clip edge)
     * and clip to the map square.
     */
    private static void appendClippedGridSegment(
            Graphics2D g2,
            int x1,
            int y1,
            int x2,
            int y2,
            int clipX1,
            int clipY1,
            int clipX2,
            int clipY2,
            int maxSegmentPx) {
        long dx = x2 - x1;
        long dy = y2 - y1;
        if (dx * dx + dy * dy > (long) maxSegmentPx * maxSegmentPx) {
            return;
        }
        int[] seg = clipSegmentToRect(x1, y1, x2, y2, clipX1, clipY1, clipX2, clipY2);
        if (seg != null) {
            g2.drawLine(seg[0], seg[1], seg[2], seg[3]);
        }
    }

    /** Cohen–Sutherland clip; returns {@code {x1,y1,x2,y2}} or null if fully outside. */
    private static int[] clipSegmentToRect(
            int x1, int y1, int x2, int y2, int rx1, int ry1, int rx2, int ry2) {
        int out1 = segmentOutCode(x1, y1, rx1, ry1, rx2, ry2);
        int out2 = segmentOutCode(x2, y2, rx1, ry1, rx2, ry2);
        while (true) {
            if ((out1 | out2) == 0) {
                return new int[] { x1, y1, x2, y2 };
            }
            if ((out1 & out2) != 0) {
                return null;
            }
            int out = out1 != 0 ? out1 : out2;
            double dx = x2 - x1;
            double dy = y2 - y1;
            int x;
            int y;
            if ((out & 8) != 0) {
                if (Math.abs(dy) < 1e-6) {
                    return null;
                }
                x = (int) Math.round(x1 + dx * (ry2 - y1) / dy);
                y = ry2;
            } else if ((out & 4) != 0) {
                if (Math.abs(dy) < 1e-6) {
                    return null;
                }
                x = (int) Math.round(x1 + dx * (ry1 - y1) / dy);
                y = ry1;
            } else if ((out & 2) != 0) {
                if (Math.abs(dx) < 1e-6) {
                    return null;
                }
                y = (int) Math.round(y1 + dy * (rx2 - x1) / dx);
                x = rx2;
            } else {
                if (Math.abs(dx) < 1e-6) {
                    return null;
                }
                y = (int) Math.round(y1 + dy * (rx1 - x1) / dx);
                x = rx1;
            }
            if (out == out1) {
                x1 = x;
                y1 = y;
                out1 = segmentOutCode(x1, y1, rx1, ry1, rx2, ry2);
            } else {
                x2 = x;
                y2 = y;
                out2 = segmentOutCode(x2, y2, rx1, ry1, rx2, ry2);
            }
        }
    }

    private static int segmentOutCode(int x, int y, int rx1, int ry1, int rx2, int ry2) {
        int code = 0;
        if (x < rx1) {
            code |= 1;
        } else if (x > rx2) {
            code |= 2;
        }
        if (y < ry1) {
            code |= 4;
        } else if (y > ry2) {
            code |= 8;
        }
        return code;
    }

    private double computeMaxDistM(java.util.List<BioRow> rows, boolean haveAbandonedPins) {
        double maxDistM = 1.0;
        double oLat = mapOriginLat();
        double oLon = mapOriginLon();
        if (commanderCentered && haveShip) {
            double d = greatCircleMeters(oLat, oLon, shipLat, shipLon, shipRadiusM);
            if (d > maxDistM) {
                maxDistM = d;
            }
        }
        if (showParkedSrvMarker()) {
            double d = commanderCentered
                    ? greatCircleMeters(oLat, oLon, srvLat, srvLon, shipRadiusM)
                    : greatCircleMeters(shipLat, shipLon, srvLat, srvLon, shipRadiusM);
            if (d > maxDistM) {
                maxDistM = d;
            }
        }
        if (showParkedPins && haveAbandonedPins && abandonedByKey != null) {
            for (Map.Entry<String, List<BodyInfo.BioSamplePoint>> e : abandonedByKey.entrySet()) {
                if (e.getValue() == null || e.getValue().isEmpty()) {
                    continue;
                }
                for (BodyInfo.BioSamplePoint p : e.getValue()) {
                    if (p == null) {
                        continue;
                    }
                    double d = greatCircleMeters(oLat, oLon, p.getLatitude(), p.getLongitude(), shipRadiusM);
                    if (d > maxDistM) {
                        maxDistM = d;
                    }
                }
            }
        }
        if (rows != null) {
            for (BioRow row : rows) {
                if (row == null || row.points == null) {
                    continue;
                }
                for (BodyInfo.BioSamplePoint p : row.points) {
                    if (p == null) {
                        continue;
                    }
                    double d = greatCircleMeters(oLat, oLon, p.getLatitude(), p.getLongitude(), shipRadiusM);
                    if (d > maxDistM) {
                        maxDistM = d;
                    }
                }
            }
        }
        if (!mapBookmarks.isEmpty()) {
            for (BioMapBookmark b : mapBookmarks) {
                double d = greatCircleMeters(oLat, oLon, b.lat, b.lon, shipRadiusM);
                if (d > maxDistM) {
                    maxDistM = d;
                }
            }
        }
        return maxDistM;
    }

    /** Red ship glyph; {@code rotationDeg} is nose heading relative to map up (0 = forward on map). */
    private static void drawShipLocationMarker(Graphics2D g2, float cx, float cy, double rotationDeg) {
        AffineTransform saved = g2.getTransform();
        if (Math.abs(rotationDeg) > 0.05) {
            g2.rotate(Math.toRadians(rotationDeg), cx, cy);
        }
        try {
            float halfW = 8f;
            float top = cy - 10f;
            float base = cy + 5f;

            Path2D.Float hull = new Path2D.Float();
            hull.moveTo(cx, top);
            hull.lineTo(cx + halfW, base);
            hull.lineTo(cx - halfW, base);
            hull.closePath();

            g2.setColor(BIO_MAP_SHIP_FILL);
            g2.fill(hull);
            g2.setColor(BIO_MAP_SHIP_OUTLINE);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(hull);

            g2.setColor(BIO_MAP_SHIP_DETAIL);
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float innerHalf = halfW * 0.62f;
            float innerBase = base - 1f;
            float apex = cy - 1f;
            g2.draw(new Line2D.Float(cx - innerHalf, innerBase, cx, apex));
            g2.draw(new Line2D.Float(cx + innerHalf, innerBase, cx, apex));
            g2.draw(new Line2D.Float(cx, apex, cx, top + 1f));
        } finally {
            g2.setTransform(saved);
        }
    }

    private static void drawVehicleRayLine(Graphics2D g2, int cx, int cy, int vx, int vy, Color lineColor) {
        Stroke oldStroke = g2.getStroke();
        try {
            g2.setColor(lineColor);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Double(cx, cy, vx, vy));
        } finally {
            g2.setStroke(oldStroke);
        }
    }

    private void queueVehicleRayLabel(
            java.util.List<BioMapRayLabel> queue,
            int cx,
            int cy,
            int vx,
            int vy,
            double fromLat,
            double fromLon,
            double toLat,
            double toLon,
            int priority) {
        double d = greatCircleMeters(fromLat, fromLon, toLat, toLon, shipRadiusM);
        double brng = bearingDeg(fromLat, fromLon, toLat, toLon);
        queue.add(new BioMapRayLabel(cx, cy, vx, vy, "", mapPinRayMetaLabel(d, brng), BIO_MAP_RAY_LABEL, priority, false));
    }

    private void drawBioSampleRayLine(Graphics2D g2, int cx, int cy, double scale, Color lineColor, BodyInfo.BioSamplePoint p) {
        if (p == null) {
            return;
        }
        int[] end = sampleRayEndPx(cx, cy, scale, p);
        g2.setColor(lineColor);
        g2.draw(new Line2D.Double(cx, cy, end[0], end[1]));
        drawBioSamplePointMarker(g2, end[0], end[1], lineColor);
    }

    private static void drawBioSamplePointMarker(Graphics2D g2, int px, int py, Color color) {
        int r = 4;
        g2.setColor(color);
        g2.fill(new Ellipse2D.Double(px - r, py - r, r * 2, r * 2));
    }

    /** Clip an off-screen sample pin to the nearest map edge along the line from the view centre. */
    private int[] clipSampleDisplayPoint(
            int cx,
            int cy,
            double scale,
            BodyInfo.BioSamplePoint p,
            int rx1,
            int ry1,
            int rx2,
            int ry2) {
        int[] end = sampleRayEndPx(cx, cy, scale, p);
        return clipDisplayPointToMapPlot(cx, cy, end[0], end[1], rx1, ry1, rx2, ry2);
    }

    private static int[] clipDisplayPointToMapPlot(
            int cx, int cy, int px, int py, int rx1, int ry1, int rx2, int ry2) {
        if (px >= rx1 && px <= rx2 && py >= ry1 && py <= ry2) {
            return new int[] { px, py };
        }
        int[] seg = clipSegmentToRect(cx, cy, px, py, rx1, ry1, rx2, ry2);
        if (seg == null) {
            return new int[] {
                    Math.max(rx1, Math.min(rx2, px)),
                    Math.max(ry1, Math.min(ry2, py))
            };
        }
        return new int[] { seg[2], seg[3] };
    }

    /** Small upward triangle for a species completed to 3/3 samples (no ray from map centre). */
    private static void drawBioSampleCompleteMarker(Graphics2D g2, int px, int py) {
        float halfW = 3.5f;
        float top = py - 4.5f;
        float base = py + 2.5f;
        Path2D.Float tri = new Path2D.Float();
        tri.moveTo(px, top);
        tri.lineTo(px + halfW, base);
        tri.lineTo(px - halfW, base);
        tri.closePath();
        g2.setColor(BIO_MAP_COMPLETE_SAMPLE);
        g2.fill(tri);
        g2.setColor(BIO_MAP_COMPLETE_SAMPLE_OUTLINE);
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(tri);
    }

    private void queueBioSampleRayLabel(
            java.util.List<BioMapRayLabel> queue,
            int cx,
            int cy,
            double scale,
            BodyInfo.BioSamplePoint p,
            String displayNameOrKey,
            int priority) {
        if (p == null) {
            return;
        }
        int[] end = sampleRayEndPx(cx, cy, scale, p);
        double oLat = mapOriginLat();
        double oLon = mapOriginLon();
        double d = greatCircleMeters(oLat, oLon, p.getLatitude(), p.getLongitude(), shipRadiusM);
        double brng = bearingDeg(oLat, oLon, p.getLatitude(), p.getLongitude());
        String genus = BiologyTabPanel.mapPinGenusLabel(displayNameOrKey);
        String meta = BiologyTabPanel.mapPinRayMetaLabel(d, brng);
        queue.add(new BioMapRayLabel(cx, cy, end[0], end[1], genus, meta, BIO_MAP_RAY_LABEL, priority, false));
    }

    private void queueBioSamplePointLabel(
            java.util.List<BioMapRayLabel> queue,
            int cx,
            int cy,
            double scale,
            BodyInfo.BioSamplePoint p,
            String displayNameOrKey,
            int priority,
            int rx1,
            int ry1,
            int rx2,
            int ry2) {
        if (p == null) {
            return;
        }
        int[] clipped = clipSampleDisplayPoint(cx, cy, scale, p, rx1, ry1, rx2, ry2);
        double oLat = mapOriginLat();
        double oLon = mapOriginLon();
        double d = greatCircleMeters(oLat, oLon, p.getLatitude(), p.getLongitude(), shipRadiusM);
        double brng = bearingDeg(oLat, oLon, p.getLatitude(), p.getLongitude());
        String genus = BiologyTabPanel.mapPinGenusLabel(displayNameOrKey);
        String meta = BiologyTabPanel.mapPinRayMetaLabel(d, brng);
        queue.add(new BioMapRayLabel(
                cx, cy, clipped[0], clipped[1], genus, meta, BIO_MAP_RAY_LABEL, priority, true));
    }

    private int[] sampleRayEndPx(int cx, int cy, double scale, BodyInfo.BioSamplePoint p) {
        double oLat = mapOriginLat();
        double oLon = mapOriginLon();
        double d = greatCircleMeters(oLat, oLon, p.getLatitude(), p.getLongitude(), shipRadiusM);
        double brng = bearingDeg(oLat, oLon, p.getLatitude(), p.getLongitude());
        double rel = Math.toRadians(brng - mapProjectionHeadingDeg());
        int tx = cx + (int) Math.round(Math.sin(rel) * d * scale);
        int ty = cy + (int) Math.round(-Math.cos(rel) * d * scale);
        return new int[] { tx, ty };
    }

    private void drawMapZoomControls(Graphics2D g2, int x0, int y0, int plotW, int plotH) {
        int pad = 8;
        int btn = 22;
        int gap = 3;
        int bx = x0 + plotW - pad - btn;
        int byMinus = y0 + plotH - pad - btn;
        int byPlus = byMinus - gap - btn;
        int bxBookmark = bx - gap - btn;
        zoomInHit.setBounds(bx, byPlus, btn, btn);
        zoomOutHit.setBounds(bx, byMinus, btn, btn);
        bookmarkHit.setBounds(bxBookmark, byMinus, btn, btn);

        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 14f));
        FontMetrics fm = g2.getFontMetrics();
        drawMapControlButton(g2, bookmarkHit, bookmarkButtonHovered);
        drawBookmarkStarOnButton(g2, bookmarkHit);
        drawZoomButton(g2, zoomInHit, "+", fm);
        drawZoomButton(g2, zoomOutHit, "−", fm);
    }

    private static void drawMapControlButton(Graphics2D g2, Rectangle r, boolean hovered) {
        g2.setColor(hovered ? new Color(70, 70, 90, 200) : EdoUi.Internal.BLACK_ALPHA_140);
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6);
        g2.setColor(hovered ? new Color(255, 255, 255, 240) : EdoUi.Internal.WHITE_ALPHA_200);
        g2.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 6, 6);
    }

    private static void drawBookmarkStarOnButton(Graphics2D g2, Rectangle r) {
        float cx = r.x + r.width * 0.5f;
        float cy = r.y + r.height * 0.52f;
        Path2D.Double star = fivePointStarPath(cx, cy, 7.5, 3.2);
        g2.setColor(BIO_MAP_BOOKMARK_OUTLINE);
        g2.setStroke(new BasicStroke(1.4f));
        g2.draw(star);
        g2.setColor(BIO_MAP_BOOKMARK_FILL);
        g2.fill(star);
    }

    private static void drawMapBookmarkStar(Graphics2D g2, int px, int py) {
        float cx = px;
        float cy = py;
        Path2D.Double star = fivePointStarPath(cx, cy, 9.0, 4.0);
        g2.setColor(BIO_MAP_BOOKMARK_OUTLINE);
        g2.setStroke(new BasicStroke(1.6f));
        g2.draw(star);
        g2.setColor(BIO_MAP_BOOKMARK_FILL);
        g2.fill(star);
    }

    private static Path2D.Double fivePointStarPath(double cx, double cy, double outerR, double innerR) {
        Path2D.Double p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(-90.0 + i * 36.0);
            double r = (i % 2 == 0) ? outerR : innerR;
            double x = cx + r * Math.cos(a);
            double y = cy + r * Math.sin(a);
            if (i == 0) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        p.closePath();
        return p;
    }

    private static void drawZoomButton(Graphics2D g2, Rectangle r, String glyph, FontMetrics fm) {
        drawMapControlButton(g2, r, false);
        g2.setColor(Color.WHITE);
        int tx = r.x + (r.width - fm.stringWidth(glyph)) / 2;
        int ty = r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(glyph, tx, ty);
    }

    private static final class BioMapRayLabel {
        final int cx;
        final int cy;
        final int tx;
        final int ty;
        final String genus;
        final String meta;
        final Color color;
        final int priority;
        /** When true, label sits beside the sample point (points display mode). */
        final boolean pointStyle;

        BioMapRayLabel(
                int cx,
                int cy,
                int tx,
                int ty,
                String genus,
                String meta,
                Color color,
                int priority,
                boolean pointStyle) {
            this.cx = cx;
            this.cy = cy;
            this.tx = tx;
            this.ty = ty;
            this.genus = genus != null ? genus : "";
            this.meta = meta != null ? meta : "";
            this.color = color;
            this.priority = priority;
            this.pointStyle = pointStyle;
        }
    }

    private static final double[] BIO_LABEL_T_SLOTS = {
            0.50, 0.42, 0.58, 0.35, 0.65, 0.28, 0.72, 0.22, 0.78, 0.15, 0.85
    };
    private static final int[] BIO_LABEL_NORMAL_OFFSETS_PX = { 0, 12, -12, 22, -22, 34, -34 };

    private void deconflictAndDrawRayLabels(
            Graphics2D g2,
            java.util.List<BioMapRayLabel> labels,
            int plotRx1,
            int plotRy1,
            int plotRx2,
            int plotRy2) {
        if (labels == null || labels.isEmpty()) {
            return;
        }
        labels.sort(Comparator.comparingInt((BioMapRayLabel l) -> l.priority).reversed());
        FontMetrics fm = g2.getFontMetrics();
        java.util.List<Rectangle> placed = new ArrayList<>();
        for (BioMapRayLabel lab : labels) {
            LabelPlacement pick = lab.pointStyle
                    ? findPointLabelPlacement(lab, fm, placed, plotRx1, plotRy1, plotRx2, plotRy2)
                    : findRayLabelPlacement(lab, fm, placed);
            if (pick == null) {
                if (lab.pointStyle) {
                    int[] shifted = shiftLabelCenterInsidePlot(
                            lab.tx, lab.ty, 0, fm, lab.genus, lab.meta, plotRx1, plotRy1, plotRx2, plotRy2);
                    Rectangle bounds = labelBlockBounds(shifted[0], shifted[1], 0, fm, lab.genus, lab.meta);
                    pick = new LabelPlacement(shifted[0], shifted[1], 0, bounds);
                } else {
                    int mx = (lab.cx + lab.tx) / 2;
                    int my = (lab.cy + lab.ty) / 2;
                    double angleRad = readableRayLabelAngleRad(lab.cx, lab.cy, lab.tx, lab.ty);
                    pick = new LabelPlacement(mx, my, angleRad, labelBlockBounds(mx, my, angleRad, fm, lab.genus, lab.meta));
                }
            }
            placed.add(pick.bounds);
            drawSplitLabelsAt(g2, pick.mx, pick.my, pick.angleRad, lab.genus, lab.meta, lab.color, fm);
        }
    }

    private static final class LabelPlacement {
        final int mx;
        final int my;
        final double angleRad;
        final Rectangle bounds;

        LabelPlacement(int mx, int my, double angleRad, Rectangle bounds) {
            this.mx = mx;
            this.my = my;
            this.angleRad = angleRad;
            this.bounds = bounds;
        }
    }

    private static LabelPlacement findRayLabelPlacement(BioMapRayLabel lab, FontMetrics fm, java.util.List<Rectangle> placed) {
        double baseAngle = readableRayLabelAngleRad(lab.cx, lab.cy, lab.tx, lab.ty);
        int dx = lab.tx - lab.cx;
        int dy = lab.ty - lab.cy;
        double len = Math.hypot(dx, dy);
        if (len < 1.0) {
            return null;
        }
        double nx = -dy / len;
        double ny = dx / len;
        for (double t : BIO_LABEL_T_SLOTS) {
            for (int normOff : BIO_LABEL_NORMAL_OFFSETS_PX) {
                int mx = (int) Math.round(lab.cx + dx * t + nx * normOff);
                int my = (int) Math.round(lab.cy + dy * t + ny * normOff);
                Rectangle bounds = labelBlockBounds(mx, my, baseAngle, fm, lab.genus, lab.meta);
                if (!intersectsAny(bounds, placed)) {
                    return new LabelPlacement(mx, my, baseAngle, bounds);
                }
            }
        }
        return null;
    }

    private LabelPlacement findPointLabelPlacement(
            BioMapRayLabel lab,
            FontMetrics fm,
            java.util.List<Rectangle> placed,
            int rx1,
            int ry1,
            int rx2,
            int ry2) {
        double dx = lab.cx - lab.tx;
        double dy = lab.cy - lab.ty;
        double len = Math.hypot(dx, dy);
        if (len < 1.0) {
            dx = 0.0;
            dy = -1.0;
            len = 1.0;
        }
        double ux = dx / len;
        double uy = dy / len;
        double nx = -uy;
        double ny = ux;
        int[] inward = { 12, 18, 26, 34, 42, 50, 58 };
        for (int off : inward) {
            for (int normOff : BIO_LABEL_NORMAL_OFFSETS_PX) {
                int mx = (int) Math.round(lab.tx + ux * off + nx * normOff);
                int my = (int) Math.round(lab.ty + uy * off + ny * normOff);
                int[] shifted = shiftLabelCenterInsidePlot(mx, my, 0, fm, lab.genus, lab.meta, rx1, ry1, rx2, ry2);
                mx = shifted[0];
                my = shifted[1];
                Rectangle bounds = labelBlockBounds(mx, my, 0, fm, lab.genus, lab.meta);
                if (bounds.x >= rx1
                        && bounds.y >= ry1
                        && bounds.x + bounds.width <= rx2
                        && bounds.y + bounds.height <= ry2
                        && !intersectsAny(bounds, placed)) {
                    return new LabelPlacement(mx, my, 0, bounds);
                }
            }
        }
        return null;
    }

    private static int[] shiftLabelCenterInsidePlot(
            int mx,
            int my,
            double angleRad,
            FontMetrics fm,
            String genus,
            String meta,
            int rx1,
            int ry1,
            int rx2,
            int ry2) {
        for (int pass = 0; pass < 4; pass++) {
            Rectangle b = labelBlockBounds(mx, my, angleRad, fm, genus, meta);
            int dx = 0;
            int dy = 0;
            if (b.x < rx1) {
                dx = rx1 - b.x;
            } else if (b.x + b.width > rx2) {
                dx = rx2 - (b.x + b.width);
            }
            if (b.y < ry1) {
                dy = ry1 - b.y;
            } else if (b.y + b.height > ry2) {
                dy = ry2 - (b.y + b.height);
            }
            if (dx == 0 && dy == 0) {
                return new int[] { mx, my };
            }
            mx += dx;
            my += dy;
        }
        return new int[] { mx, my };
    }

    private static double readableRayLabelAngleRad(int cx, int cy, int tx, int ty) {
        double angleRad = Math.atan2(ty - cy, tx - cx);
        if (angleRad > Math.PI / 2.0 || angleRad < -Math.PI / 2.0) {
            angleRad += Math.PI;
        }
        return angleRad;
    }

    private static Rectangle labelBlockBounds(int mx, int my, double angleRad, FontMetrics fm, String genus, String meta) {
        boolean haveGenus = genus != null && !genus.isEmpty();
        boolean haveMeta = meta != null && !meta.isEmpty();
        if (!haveGenus && !haveMeta) {
            return new Rectangle(mx, my, 0, 0);
        }
        int pad = 4;
        int blockW = 0;
        int blockH = pad;
        if (haveGenus) {
            blockW = Math.max(blockW, fm.stringWidth(genus));
            blockH += fm.getHeight();
        }
        if (haveMeta) {
            blockW = Math.max(blockW, fm.stringWidth(meta));
            blockH += fm.getHeight();
        }
        blockW += pad * 2;
        blockH += pad;

        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        double hw = blockW * 0.5;
        double hh = blockH * 0.5;
        double[][] corners = {
                { -hw, -hh }, { hw, -hh }, { hw, hh }, { -hw, hh }
        };
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (double[] c : corners) {
            int px = (int) Math.round(mx + c[0] * cos - c[1] * sin);
            int py = (int) Math.round(my + c[0] * sin + c[1] * cos);
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            maxX = Math.max(maxX, px);
            maxY = Math.max(maxY, py);
        }
        return new Rectangle(minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY));
    }

    private static boolean intersectsAny(Rectangle candidate, java.util.List<Rectangle> placed) {
        for (Rectangle r : placed) {
            if (r != null && r.intersects(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static void drawSplitLabelsAt(
            Graphics2D g2,
            int mx,
            int my,
            double angleRad,
            String genusLabel,
            String metaLabel,
            Color color,
            FontMetrics fm) {
        boolean haveGenus = genusLabel != null && !genusLabel.isEmpty();
        boolean haveMeta = metaLabel != null && !metaLabel.isEmpty();
        if (!haveGenus && !haveMeta) {
            return;
        }
        int pad = 3;
        int aboveY = -pad - fm.getDescent();
        int belowY = pad + fm.getAscent();
        AffineTransform saved = g2.getTransform();
        try {
            g2.translate(mx, my);
            g2.rotate(angleRad);
            g2.setColor(color);
            if (haveGenus) {
                int genusW = fm.stringWidth(genusLabel);
                g2.drawString(genusLabel, -genusW / 2, aboveY);
            }
            if (haveMeta) {
                int metaW = fm.stringWidth(metaLabel);
                g2.drawString(metaLabel, -metaW / 2, belowY);
            }
        } finally {
            g2.setTransform(saved);
        }
    }

    /** Blue SRV glyph; {@code rotationDeg} is nose heading relative to map up. */
    private static void drawSrvLocationMarker(Graphics2D g2, float cx, float cy, double rotationDeg) {
        AffineTransform saved = g2.getTransform();
        if (Math.abs(rotationDeg) > 0.05) {
            g2.rotate(Math.toRadians(rotationDeg), cx, cy);
        }
        try {
            float halfW = 8f;
            float top = cy - 10f;
            float base = cy + 5f;

            Path2D.Float hull = new Path2D.Float();
            hull.moveTo(cx, top);
            hull.lineTo(cx + halfW, base);
            hull.lineTo(cx - halfW, base);
            hull.closePath();

            g2.setColor(BIO_MAP_SRV_FILL);
            g2.fill(hull);
            g2.setColor(BIO_MAP_SRV_OUTLINE);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(hull);

            g2.setColor(BIO_MAP_SRV_DETAIL);
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float innerHalf = halfW * 0.62f;
            float innerBase = base - 1f;
            float apex = cy - 1f;
            g2.draw(new Line2D.Float(cx - innerHalf, innerBase, cx, apex));
            g2.draw(new Line2D.Float(cx + innerHalf, innerBase, cx, apex));
            g2.draw(new Line2D.Float(cx, apex, cx, top + 1f));
        } finally {
            g2.setTransform(saved);
        }
    }

}

private void recordMovementSample(Instant t, double lat, double lon, double radiusM, boolean onFootOrSrv) {
    if (t == null) {
        return;
    }

    double minStepM = onFootOrSrv ? 1.0 : 2.0;
    MovementSample last = movement.peekLast();
    if (last != null) {
        double d = greatCircleMeters(last.lat, last.lon, lat, lon, radiusM);
        // Ignore tiny jitter; do not let "stopped" updates influence heading.
        if (d < minStepM) {
            return;
        }
        if (onFootOrSrv) {
            movementHeadingDeg = bearingDeg(last.lat, last.lon, lat, lon);
        }
    }

    movement.addLast(new MovementSample(t, lat, lon, radiusM));
    while (movement.size() > 3) {
        movement.removeFirst();
    }

    if (!onFootOrSrv && movement.size() >= 2) {
        MovementSample a = movement.peekFirst();
        MovementSample b = movement.peekLast();
        if (a != null && b != null) {
            movementHeadingDeg = bearingDeg(a.lat, a.lon, b.lat, b.lon);
        }
    }
}
private static void drawCheckMark(Graphics2D g2, int x, int y, int w, int h) {
    // Vector checkmark so we don't depend on font glyph availability.
    float stroke = Math.max(2f, Math.min(w, h) / 6f);

    java.awt.Stroke oldStroke = g2.getStroke();
    try {
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        Path2D.Float p = new Path2D.Float();

     // More acute angle, longer + more upright long stem
     p.moveTo(x + w * 0.24, y + h * 0.60);  // start (lower-left)
     p.lineTo(x + w * 0.40, y + h * 0.78);  // knee (lower-ish, a bit right)
     p.lineTo(x + w * 0.66, y + h * 0.20);  // tip (higher, less right -> more upright)

     g2.draw(p);


        g2.draw(p);
    } finally {
        g2.setStroke(oldStroke);
    }
}

    /** Surface fix at {@code bearingDegNorth} and {@code distM} from a start lat/lon (spherical). */
    private static double[] surfacePointAtBearing(
            double latDeg, double lonDeg, double bearingDegNorth, double distM, double radiusM) {
        double lat = Math.toRadians(latDeg);
        double lon = Math.toRadians(lonDeg);
        double br = Math.toRadians(bearingDegNorth);
        double angDist = distM / radiusM;
        double lat2 = Math.asin(
                Math.sin(lat) * Math.cos(angDist) + Math.cos(lat) * Math.sin(angDist) * Math.cos(br));
        double lon2 = lon
                + Math.atan2(
                        Math.sin(br) * Math.sin(angDist) * Math.cos(lat),
                        Math.cos(angDist) - Math.sin(lat) * Math.sin(lat2));
        return new double[] { Math.toDegrees(lat2), Math.toDegrees(lon2) };
    }

    private static double bearingDeg(double lat1, double lon1, double lat2, double lon2) {
    double phi1 = Math.toRadians(lat1);
    double phi2 = Math.toRadians(lat2);
    double dLambda = Math.toRadians(lon2 - lon1);

    double y = Math.sin(dLambda) * Math.cos(phi2);
    double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda);

    double theta = Math.atan2(y, x);
    double deg = Math.toDegrees(theta);
    deg = (deg + 360.0) % 360.0;
    return deg;
}

}