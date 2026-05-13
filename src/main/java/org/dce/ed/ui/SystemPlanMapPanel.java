package org.dce.ed.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;

/**
 * Top-down (X/Y) schematic of approximate body positions from journal orbital geometry,
 * plus estimated commander position when available.
 */
public final class SystemPlanMapPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Tall enough that BorderLayout keeps a usable plot area (table scroll pane no longer steals almost all height). */
    private static final int PREFERRED_HEIGHT = 432;
    private static final int MIN_HEIGHT = 312;
    private static final int PAD = 12;
    /** Bottom inset under the plot (caption row removed). */
    private static final int MAP_BOTTOM_INSET = 8;
    private static final double LABEL_TRUNCATE = 14;

    private static final double ZOOM_MIN = 0.2;
    /** Max zoom-in (× fit scale); higher = closer inspection of tight moon clusters. */
    private static final double ZOOM_MAX = 512.0;
    /** ~10% zoom change per mouse-wheel notch ({@link MouseWheelEvent#getWheelRotation}). */
    private static final double ZOOM_PER_NOTCH = 1.1;

    /** Minimum segments when zoomed out (legacy fallback if scale is unknown). */
    private static final int ORBIT_SEGMENTS_MIN = 72;
    /** Legacy ceiling when {@code scalePixelsPerMetre} is not used; actual per-orbit count can reach {@link SystemOrbitGeometry#ORBIT_POLYLINE_SEGMENTS_HARD_MAX}. */
    private static final int ORBIT_SEGMENTS_MAX = 768;

    /** Zoom (× fit scale) before moon designations ({@code 1 a}) are drawn; major / parent bodies always labeled. */
    private static final double ZOOM_SHOW_MOON_LABELS = 8.0;

    /**
     * At zoom below this (same threshold as moon labels), subsystem parents (bodies that host moons) draw as twin
     * blue ring markers instead of filled dots; their orbit polylines around the star are still drawn.
     */
    private static final double ZOOM_SUBSYSTEM_HUB_DETAIL = ZOOM_SHOW_MOON_LABELS;

    /**
     * When zoom is at least this (× fit scale), keep a subsystem orbit-parent at the view centre while bodies move.
     * Matches {@link #ZOOM_SHOW_MOON_LABELS} so follow engages when moon detail is in use.
     */
    private static final double ZOOM_SUBSYSTEM_CENTER_LOCK = ZOOM_SHOW_MOON_LABELS;

    /** World half-extent multiplier when picking bodies for hub detection (avoids empty viewport → no pan). */
    private static final double SUBSYSTEM_FOLLOW_VISIBLE_MARGIN = 4.0;

    /** When hub is this close in screen px after easing, pin its marker to the plot centre (eliminates float drift). */
    private static final double VIEW_CENTER_HUB_PIN_SCREEN_PX = 1.5;
    /**
     * Zoom-in wheel: blend toward subsystem hub — minimum fraction of remaining hub offset per notch at the start of
     * {@link #ZOOM_SUBSYSTEM_CENTER_LOCK} range.
     */
    private static final double WHEEL_SUBSYSTEM_NUDGE_BLEND_MIN = 0.055;
    /**
     * Zoom-in wheel: blend toward subsystem hub — maximum fraction per notch at {@link #ZOOM_MAX} (almost one-step
     * recentre).
     */
    private static final double WHEEL_SUBSYSTEM_NUDGE_BLEND_MAX = 0.92;
    /** Curves how quickly wheel nudge ramps between min and max blend as zoom increases. */
    private static final double WHEEL_SUBSYSTEM_NUDGE_BLEND_GAMMA = 1.48;
    /** At or below this screen-space error (px), snap view centre exactly to the hub after a wheel nudge. */
    private static final double WHEEL_SUBSYSTEM_SNAP_ERR_PX = 1.15;
    /**
     * Screen pixels: corrections up to this size track in one step (moving hub while orbiting); larger gaps ease in
     * over more frames instead of jumping the map (same when not animating).
     */
    private static final double VIEW_CENTER_SMOOTH_FULL_BELOW_PX = 15.0;
    /** Max fraction of the remaining screen-space error to close per frame when easing a large lock correction. */
    private static final double VIEW_CENTER_SMOOTH_CAP = 0.14;
    /** Extra easing strength vs screen-space error (before {@link #VIEW_CENTER_SMOOTH_CAP}). */
    private static final double VIEW_CENTER_SMOOTH_GAIN = 0.0010;
    /** Hard cap on how many screen pixels of error may be closed in one frame (smoother than ratio-only easing). */
    private static final double VIEW_CENTER_SMOOTH_MAX_STEP_PX = 9.0;
    /** Screen px: snap view centre to target and stop repaint chaining once error is below this. */
    private static final double VIEW_CENTER_LOCK_EPS_PX = 0.35;

    /** When proximity moves to a different orbit subsystem while zoomed in, animate out then in (tick-based). */
    private static final int SUBSYSTEM_HOP_TICK_MS = 32;
    private static final int SUBSYSTEM_HOP_OUT_TICKS = 12;
    private static final int SUBSYSTEM_HOP_IN_TICKS = 14;
    private static final double SUBSYSTEM_HOP_TARGET_ZOOM_MOON_CLUSTER = 11.0;
    private static final double SUBSYSTEM_HOP_TARGET_ZOOM_STAR = 4.0;
    /** When framing an {@code ApproachBody} view, leave this fraction of the plot empty around the fitted subsystem. */
    private static final double APPROACH_SUBSYSTEM_FIT_MARGIN = 0.10;

    private static final Pattern MOON_NAME_COMPACT = Pattern.compile("^(\\d+)\\s*([A-Za-z])\\s*$");

    private final List<BodyDot> dots = new ArrayList<>();
    private List<OrbitPolylineWorldXY> orbitLines = Collections.emptyList();
    /** Last sources passed to {@link SystemOrbitGeometry#orbitPolylinesWorldMetresXY}; rebuilt when zoom changes. */
    private Map<Integer, BodyInfo> orbitGeomBodies;
    private Map<Integer, double[]> orbitGeomPositions;
    /** Last {@link #orbitRebuildCacheKey()} used for a non-forced orbit rebuild (panel size / zoom / scale). */
    private long lastOrbitRebuildKey = Long.MIN_VALUE;
    /** Sorted orbiting-body ids for {@link #orbitLines}; used to log once per distinct ring set. */
    private int[] lastLoggedOrbitPolyBodyIds = new int[0];
    /** Journal ids of bodies that have at least one moon (subsystem hubs); used for wide-zoom lump markers. */
    private Set<Integer> subsystemHubLumpBodyIds = Collections.emptySet();
    private boolean sceneEmpty = true;
    private boolean shipKnown;
    private double shipWx;
    private double shipWy;
    private Integer anchorBodyId;
    private Integer highlightNearBodyId;

    /** When true, schematic orbit playback is running (subsystem hop skipped; view centre snaps to follow hub). */
    private boolean orbitSchematicPlaybackActive;

    /**
     * When not {@code -1}, subsystem zoom-lock is active for this hub id; after a wheel nudge the hub still uses the
     * normal transform until within {@link #VIEW_CENTER_HUB_PIN_SCREEN_PX} px, then its marker + label are pinned to
     * the plot pixel centre. During schematic playback the hub is always snapped and pinned.
     */
    private int subsystemScreenLockHubId = -1;

    /** Multiplier on the automatic fit-all scale ({@code 1} = fit bounds). */
    private double zoomFactor = 1.0;
    /**
     * World span (metres) used for {@code scaleFit} in paint and wheel zoom — set only in {@link #setScene}, not
     * while {@link #tryApplyPositionUpdate} moves bodies, so fast-forward animation does not “breathe” the zoom.
     */
    private double layoutSpanX = 1.0;
    private double layoutSpanY = 1.0;
    /** World metres (X/Y) at the centre of the plot; updated when the scene loads and when zooming. */
    private double viewCenterWx;
    private double viewCenterWy;

    /** When body IDs change (new system / new scan), reset pan & zoom; otherwise keep user zoom across telemetry refreshes. */
    private int[] lastSceneBodyIdsSnapshot = new int[0];

    /** Next paint runs subsystem centre lock with pause-style centroid fallback (see {@link #syncViewCenterToSubsystemHubAfterOrbitPause}). */
    private boolean pendingSubsystemCenterPauseResync;

    /** Last {@link #setScene} proximity highlight; used to detect subsystem changes while zoomed in. */
    private Integer prevProximityHighlightBodyId;

    private Timer subsystemHopTimer;
    private boolean subsystemProximityHopActive;
    private int subsystemHopTick;
    private double subHopZ0;
    private double subHopZ1;
    private double subHopZ2;
    private double subHopX0;
    private double subHopY0;
    private double subHopX1;
    private double subHopY1;
    private double subHopX2;
    private double subHopY2;

    public SystemPlanMapPanel() {
        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, EdoUi.Internal.GRAY_180),
                new EmptyBorder(6, 0, 4, 0)));
        addMouseWheelListener(this::handleMouseWheel);
        System.out.println(
                "[EDO][OrbitMap] System plan map panel initialized; orbit ring bodies are listed when each system is first drawn.");
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        int w = d.width > 0 ? d.width : 400;
        return new Dimension(w, PREFERRED_HEIGHT);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(120, MIN_HEIGHT);
    }

    /**
     * Clears the map (no systems loaded or empty body list).
     */
    public void clearScene() {
        dots.clear();
        sceneEmpty = true;
        shipKnown = false;
        anchorBodyId = null;
        highlightNearBodyId = null;
        orbitSchematicPlaybackActive = false;
        zoomFactor = 1.0;
        layoutSpanX = 1.0;
        layoutSpanY = 1.0;
        viewCenterWx = 0.0;
        viewCenterWy = 0.0;
        lastSceneBodyIdsSnapshot = new int[0];
        orbitLines = Collections.emptyList();
        subsystemHubLumpBodyIds = Collections.emptySet();
        orbitGeomBodies = null;
        orbitGeomPositions = null;
        lastOrbitRebuildKey = Long.MIN_VALUE;
        lastLoggedOrbitPolyBodyIds = new int[0];
        pendingSubsystemCenterPauseResync = false;
        prevProximityHighlightBodyId = null;
        subsystemScreenLockHubId = -1;
        cancelSubsystemProximityHop();
        repaint();
    }

    /**
     * @param positions body centre positions in metres (same frame as {@link org.dce.ed.util.SystemOrbitGeometry})
     * @param shipM       commander position metres, or {@code null} if unknown
     * @param orbitSchematicPlaybackActive when true, schematic orbit playback is running; subsystem hop is skipped
     */
    public void setScene(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> positions,
            double[] shipM,
            Integer anchorBodyId,
            Integer highlightNearBodyId,
            boolean orbitSchematicPlaybackActive) {

        dots.clear();
        this.anchorBodyId = anchorBodyId;
        this.highlightNearBodyId = highlightNearBodyId;
        this.orbitSchematicPlaybackActive = orbitSchematicPlaybackActive;
        sceneEmpty = bodies == null || bodies.isEmpty() || positions == null || positions.isEmpty();

        if (!sceneEmpty) {
            for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                double[] p = positions.get(e.getKey());
                if (p == null || p.length < 2) {
                    continue;
                }
                double x = p[0];
                double y = p[1];
                if (!Double.isFinite(x) || !Double.isFinite(y)) {
                    continue;
                }
                BodyInfo b = e.getValue();
                boolean star = e.getKey().intValue() == 0;
                boolean primaryStar = isPrimaryStarBody(b);
                String label = primaryStar ? "" : labelFor(b);
                boolean moon = !star && !primaryStar && isMoonBody(b);
                dots.add(new BodyDot(e.getKey().intValue(), x, y, label, star, primaryStar, moon));
            }
            dots.sort(Comparator.comparingInt(d -> d.bodyId));
        }

        if (shipM != null && shipM.length >= 2
                && Double.isFinite(shipM[0]) && Double.isFinite(shipM[1])) {
            shipKnown = true;
            shipWx = shipM[0];
            shipWy = shipM[1];
        } else {
            shipKnown = false;
            shipWx = 0;
            shipWy = 0;
        }

        if (!sceneEmpty && bodies != null && positions != null) {
            orbitGeomBodies = bodies;
            orbitGeomPositions = positions;
            subsystemHubLumpBodyIds = collectSubsystemHubBodyIds(bodies);
        } else {
            orbitGeomBodies = null;
            orbitGeomPositions = null;
            subsystemHubLumpBodyIds = Collections.emptySet();
            orbitLines = Collections.emptyList();
            lastOrbitRebuildKey = Long.MIN_VALUE;
        }

        if (!sceneEmpty && !dots.isEmpty()) {
            Bounds bb = computeBounds(dots,
                    shipKnown ? shipWx : Double.NaN,
                    shipKnown ? shipWy : Double.NaN);
            layoutSpanX = Math.max(1.0, bb.maxX - bb.minX);
            layoutSpanY = Math.max(1.0, bb.maxY - bb.minY);
            int[] ids = new int[dots.size()];
            for (int i = 0; i < dots.size(); i++) {
                ids[i] = dots.get(i).bodyId;
            }
            Arrays.sort(ids);
            if (!Arrays.equals(ids, lastSceneBodyIdsSnapshot)) {
                cancelSubsystemProximityHop();
                lastSceneBodyIdsSnapshot = Arrays.copyOf(ids, ids.length);
                viewCenterWx = (bb.minX + bb.maxX) * 0.5;
                viewCenterWy = (bb.minY + bb.maxY) * 0.5;
                zoomFactor = 1.0;
                prevProximityHighlightBodyId = null;
            }
        } else if (sceneEmpty || dots.isEmpty()) {
            layoutSpanX = 1.0;
            layoutSpanY = 1.0;
        }

        rebuildOrbitPolylines(true, true);
        if (!sceneEmpty && !dots.isEmpty() && bodies != null) {
            maybeStartSubsystemProximityHop(highlightNearBodyId, bodies, orbitSchematicPlaybackActive);
            prevProximityHighlightBodyId = highlightNearBodyId;
        } else {
            prevProximityHighlightBodyId = null;
        }
        repaint();
    }

    /**
     * Debug: log each drawn orbit ring's polyline centroid and the resolved parent's world position (orbit focus),
     * in schematic metres (X/Y). Safe from any thread; marshals to the EDT if needed.
     *
     * @param approachedBodyId journal {@code ApproachBody} body id that triggered this log
     */
    public void logOrbitRingCentersForApproachDebug(int approachedBodyId) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> logOrbitRingCentersForApproachDebug(approachedBodyId));
            return;
        }
        BodyInfo approachedBi = orbitGeomBodies != null
                ? orbitGeomBodies.get(Integer.valueOf(approachedBodyId))
                : null;
        String approachedName = approachedBi != null ? labelFor(approachedBi) : "?" + approachedBodyId;
        if (orbitGeomBodies == null || orbitGeomPositions == null) {
            System.out.println(String.format(Locale.US,
                    "[EDO][OrbitMap] Approach body %s (id=%d) — no orbit geometry cached (map not built yet).",
                    approachedName, approachedBodyId));
            return;
        }
        if (orbitLines == null || orbitLines.isEmpty()) {
            System.out.println(String.format(Locale.US,
                    "[EDO][OrbitMap] Approach body %s (id=%d) — zero orbit polylines to draw.",
                    approachedName, approachedBodyId));
            return;
        }
        System.out.println(String.format(Locale.US,
                "[EDO][OrbitMap] Approach body %s (id=%d) — %d orbit ring(s), world metres (XY); "
                        + "centroid = mean of polyline vertices, parent = orbit focus:",
                approachedName, approachedBodyId, orbitLines.size()));
        for (OrbitPolylineWorldXY pl : orbitLines) {
            if (pl == null || pl.wx == null || pl.wy == null) {
                continue;
            }
            int n = Math.min(pl.wx.length, pl.wy.length);
            if (n <= 0) {
                continue;
            }
            double sumx = 0.0;
            double sumy = 0.0;
            for (int i = 0; i < n; i++) {
                sumx += pl.wx[i];
                sumy += pl.wy[i];
            }
            double cx = sumx / n;
            double cy = sumy / n;

            BodyInfo ch = orbitGeomBodies.get(Integer.valueOf(pl.bodyId));
            String ringName = ch != null ? labelFor(ch) : "?" + pl.bodyId;
            int pId = ch != null ? SystemOrbitGeometry.resolveOrbitParentBodyId(ch, orbitGeomBodies) : -1;
            BodyInfo par = pId >= 0 ? orbitGeomBodies.get(Integer.valueOf(pId)) : null;
            String parentName = par != null ? labelFor(par) : "?";
            double ppx = Double.NaN;
            double ppy = Double.NaN;
            if (pId >= 0) {
                double[] pp = orbitGeomPositions.get(Integer.valueOf(pId));
                if (pp != null && pp.length >= 2) {
                    ppx = pp[0];
                    ppy = pp[1];
                }
            }
            System.out.println(String.format(Locale.US,
                    "  ring %s (id=%d) centroid=(%.3f, %.3f) parent=%s (id=%d) parentPos=(%.3f, %.3f)",
                    ringName, pl.bodyId, cx, cy, parentName, pId, ppx, ppy));
        }
    }

    /**
     * Pan/zoom the schematic toward a journal {@code ApproachBody} world (two-phase ease: brief full-map context,
     * then zoom in on the body). Reuses the subsystem hop timer; cancels any in-progress hop.
     */
    public void focusCameraOnApproachedBody(int bodyId) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> focusCameraOnApproachedBody(bodyId));
            return;
        }
        cancelSubsystemProximityHop();
        if (sceneEmpty || dots.isEmpty() || bodyId < 0) {
            return;
        }
        if (orbitGeomBodies != null && !orbitGeomBodies.containsKey(Integer.valueOf(bodyId))) {
            return;
        }
        double[] target = new double[2];
        if (!worldXYForBody(bodyId, target)) {
            return;
        }
        Bounds bb = computeBounds(dots,
                shipKnown ? shipWx : Double.NaN,
                shipKnown ? shipWy : Double.NaN);
        double midCx = (bb.minX + bb.maxX) * 0.5;
        double midCy = (bb.minY + bb.maxY) * 0.5;

        int frameHub = approachFrameHubId(bodyId, orbitGeomBodies);
        double[] frame = computeApproachSubsystemFrame(bodyId, frameHub, target);
        double targetZoom = frame[2];
        double focusX = frame[0];
        double focusY = frame[1];

        subHopZ0 = zoomFactor;
        subHopZ1 = 1.0;
        subHopZ2 = targetZoom;
        subHopX0 = viewCenterWx;
        subHopY0 = viewCenterWy;
        subHopX1 = midCx;
        subHopY1 = midCy;
        subHopX2 = focusX;
        subHopY2 = focusY;
        subsystemHopTick = 0;
        subsystemProximityHopActive = true;
        ensureSubsystemHopTimer();
        subsystemHopTimer.start();
        repaint();
    }

    /**
     * Hub used to frame the map after {@code ApproachBody}: a body that hosts satellites uses itself so the view
     * includes that planet and its moons; otherwise the same key as subsystem proximity hops (moon → parent world).
     */
    private static int approachFrameHubId(int bodyId, Map<Integer, BodyInfo> bodies) {
        if (bodies == null || bodies.isEmpty() || bodyId < 0 || !bodies.containsKey(Integer.valueOf(bodyId))) {
            return -1;
        }
        for (BodyInfo bi : bodies.values()) {
            if (bi == null) {
                continue;
            }
            int p = SystemOrbitGeometry.resolveOrbitParentBodyId(bi, bodies);
            if (p == bodyId) {
                return bodyId;
            }
        }
        return subsystemFocusKeyForBody(bodyId, bodies);
    }

    private static boolean orbitSubtreeContainsHub(int bodyId, int hubId, Map<Integer, BodyInfo> bodies) {
        int cur = bodyId;
        for (int g = 0; g < 64 && cur >= 0; g++) {
            if (cur == hubId) {
                return true;
            }
            if (cur == 0) {
                return hubId == 0;
            }
            BodyInfo bi = bodies.get(Integer.valueOf(cur));
            if (bi == null) {
                break;
            }
            int p = SystemOrbitGeometry.resolveOrbitParentBodyId(bi, bodies);
            if (p < 0 || p == cur) {
                break;
            }
            cur = p;
        }
        return false;
    }

    private static Set<Integer> membersUnderApproachHub(int hubId, Map<Integer, BodyInfo> bodies) {
        Set<Integer> out = new HashSet<>();
        if (bodies == null || hubId < 0) {
            return out;
        }
        for (Integer k : bodies.keySet()) {
            if (k == null) {
                continue;
            }
            int id = k.intValue();
            if (orbitSubtreeContainsHub(id, hubId, bodies)) {
                out.add(k);
            }
        }
        return out;
    }

    /**
     * Orbit rings that belong in the “local” frame: moons of {@code hubId}, or direct stellar orbits when {@code hubId}
     * is {@code 0}. Omits the hub’s own orbit around the star so a gas-giant approach does not zoom to system scale.
     */
    private static boolean includeOrbitPolyForApproachFrame(int orbitingBodyId, int hubId, Map<Integer, BodyInfo> bodies) {
        if (bodies == null || orbitingBodyId == hubId) {
            return false;
        }
        BodyInfo bi = bodies.get(Integer.valueOf(orbitingBodyId));
        if (bi == null) {
            return false;
        }
        int p = SystemOrbitGeometry.resolveOrbitParentBodyId(bi, bodies);
        if (hubId == 0) {
            return p == 0;
        }
        return p == hubId;
    }

    /**
     * Centre and zoom so the approached subsystem (hub + descendants + local orbit strokes) fits the plot.
     *
     * @return {@code [focusWx, focusWy, targetZoom]}
     */
    private double[] computeApproachSubsystemFrame(int bodyId, int frameHub, double[] approachedXY) {
        double fallbackZ = (bodyId == 0) ? SUBSYSTEM_HOP_TARGET_ZOOM_STAR : SUBSYSTEM_HOP_TARGET_ZOOM_MOON_CLUSTER;
        fallbackZ = clamp(fallbackZ, ZOOM_MIN, ZOOM_MAX);
        if (orbitGeomBodies == null || orbitGeomPositions == null || frameHub < 0 || approachedXY == null) {
            return new double[] { approachedXY[0], approachedXY[1], fallbackZ };
        }

        int w = getWidth();
        int h = getHeight();
        int plotH = Math.max(88, h - MAP_BOTTOM_INSET);
        double availW = w - 2.0 * PAD;
        double availH = plotH - 2.0 * PAD;
        if (availW < 32.0 || availH < 32.0 || layoutSpanX <= 0.0 || layoutSpanY <= 0.0) {
            return new double[] { approachedXY[0], approachedXY[1], fallbackZ };
        }

        Set<Integer> members = membersUnderApproachHub(frameHub, orbitGeomBodies);
        if (members.isEmpty()) {
            return new double[] { approachedXY[0], approachedXY[1], fallbackZ };
        }

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (BodyDot d : dots) {
            if (!members.contains(Integer.valueOf(d.bodyId))) {
                continue;
            }
            if (Double.isFinite(d.wx) && Double.isFinite(d.wy)) {
                minX = Math.min(minX, d.wx);
                maxX = Math.max(maxX, d.wx);
                minY = Math.min(minY, d.wy);
                maxY = Math.max(maxY, d.wy);
            }
        }
        if (shipKnown && Double.isFinite(shipWx) && Double.isFinite(shipWy)) {
            minX = Math.min(minX, shipWx);
            maxX = Math.max(maxX, shipWx);
            minY = Math.min(minY, shipWy);
            maxY = Math.max(maxY, shipWy);
        }

        double scalePxPerM = computeScalePixelsPerWorldMetre();
        List<OrbitPolylineWorldXY> polys = SystemOrbitGeometry.orbitPolylinesWorldMetresXY(orbitGeomBodies,
                orbitGeomPositions, ORBIT_SEGMENTS_MAX, scalePxPerM);
        for (OrbitPolylineWorldXY pl : polys) {
            if (pl == null || pl.wx == null || pl.wy == null || pl.wx.length != pl.wy.length) {
                continue;
            }
            if (!includeOrbitPolyForApproachFrame(pl.bodyId, frameHub, orbitGeomBodies)) {
                continue;
            }
            for (int i = 0; i < pl.wx.length; i++) {
                double x = pl.wx[i];
                double y = pl.wy[i];
                if (Double.isFinite(x) && Double.isFinite(y)) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (!Double.isFinite(minX) || !Double.isFinite(maxX) || !Double.isFinite(minY) || !Double.isFinite(maxY)) {
            return new double[] { approachedXY[0], approachedXY[1], fallbackZ };
        }

        double mx = (minX + maxX) * 0.5;
        double my = (minY + maxY) * 0.5;
        double minSpan = Math.max(layoutSpanX, layoutSpanY) * 1e-5;
        double wSpan = Math.max(maxX - minX, minSpan) * (1.0 + 2.0 * APPROACH_SUBSYSTEM_FIT_MARGIN);
        double hSpan = Math.max(maxY - minY, minSpan) * (1.0 + 2.0 * APPROACH_SUBSYSTEM_FIT_MARGIN);

        double scaleFit = Math.min(availW / layoutSpanX, availH / layoutSpanY);
        if (!Double.isFinite(scaleFit) || scaleFit <= 0.0) {
            return new double[] { mx, my, fallbackZ };
        }

        double zoomFit = Math.min(availW / (scaleFit * wSpan), availH / (scaleFit * hSpan));
        if (!Double.isFinite(zoomFit) || zoomFit <= 0.0) {
            return new double[] { mx, my, fallbackZ };
        }
        zoomFit = clamp(zoomFit, ZOOM_MIN, ZOOM_MAX);
        return new double[] { mx, my, zoomFit };
    }

    /** World metres (map X/Y) to screen pixels at map centre; matches {@link #paintComponent} and wheel zoom. */
    private double computeScalePixelsPerWorldMetre() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || layoutSpanX <= 0.0 || layoutSpanY <= 0.0) {
            return Double.NaN;
        }
        int plotH = Math.max(88, h - MAP_BOTTOM_INSET);
        double availW = w - 2.0 * PAD;
        double availH = plotH - 2.0 * PAD;
        if (availW < 32.0 || availH < 32.0) {
            return Double.NaN;
        }
        double scaleFit = Math.min(availW / layoutSpanX, availH / layoutSpanY);
        if (!Double.isFinite(scaleFit) || scaleFit <= 0.0) {
            return Double.NaN;
        }
        return scaleFit * zoomFactor;
    }

    /**
     * Quantized key so {@link #rebuildOrbitPolylines(boolean, boolean)} {@code force=false} skips work when zoom, size, and
     * scale bucket are unchanged (scene/position updates still pass {@code force=true}).
     */
    private long orbitRebuildCacheKey() {
        int w = Math.max(0, getWidth());
        int h = Math.max(0, getHeight());
        int zq = (int) Math.round(zoomFactor * 8192.0);
        double sc = computeScalePixelsPerWorldMetre();
        long scBits;
        if (!Double.isFinite(sc) || sc <= 0.0) {
            scBits = 0L;
        } else {
            scBits = Double.doubleToLongBits(Math.scalb(Math.rint(Math.scalb(sc, 18)), -18));
        }
        return ((long) w << 44) ^ ((long) h << 24) ^ (((long) zq & 0xfffffL) << 4) ^ (scBits >>> 1);
    }

    private void rebuildOrbitPolylines(boolean force) {
        rebuildOrbitPolylines(force, true);
    }

    /**
     * @param useScreenChordScaleForSegments when true, pass {@link #computeScalePixelsPerWorldMetre()} into geometry so
     *        per-orbit vertex count tracks screen chord (smoother curves when zoomed). When false (orbit playback
     *        ticks), use {@link Double#NaN} so segment counts stay on the legacy zoom curve only — avoids chord count
     *        flipping frame-to-frame when schematic fallback radii change.
     */
    private void rebuildOrbitPolylines(boolean force, boolean useScreenChordScaleForSegments) {
        if (sceneEmpty || orbitGeomBodies == null || orbitGeomPositions == null) {
            orbitLines = Collections.emptyList();
            lastOrbitRebuildKey = Long.MIN_VALUE;
            return;
        }
        long key = orbitRebuildCacheKey();
        if (!force && key == lastOrbitRebuildKey) {
            return;
        }
        lastOrbitRebuildKey = key;
        double scalePxPerM = useScreenChordScaleForSegments ? computeScalePixelsPerWorldMetre() : Double.NaN;
        int legacySeg = orbitSegmentsForZoom(zoomFactor);
        orbitLines = SystemOrbitGeometry.orbitPolylinesWorldMetresXY(orbitGeomBodies, orbitGeomPositions, legacySeg,
                scalePxPerM);
        logOrbitLinesIfBodySetChanged(orbitLines);
    }

    /**
     * Prints which bodies get a closed orbit stroke (each ring is that body's path around its resolved parent).
     * Runs when the set of those body ids changes (first system after startup, jump to another system, or clear).
     */
    private void logOrbitLinesIfBodySetChanged(List<OrbitPolylineWorldXY> polys) {
        if (polys == null || polys.isEmpty() || orbitGeomBodies == null) {
            return;
        }
        int n = polys.size();
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = polys.get(i).bodyId;
        }
        Arrays.sort(ids);
        if (Arrays.equals(ids, lastLoggedOrbitPolyBodyIds)) {
            return;
        }
        lastLoggedOrbitPolyBodyIds = Arrays.copyOf(ids, ids.length);
        StringJoiner joiner = new StringJoiner(", ");
        for (OrbitPolylineWorldXY pl : polys) {
            if (pl == null) {
                continue;
            }
            BodyInfo ch = orbitGeomBodies.get(Integer.valueOf(pl.bodyId));
            if (ch == null) {
                joiner.add("?" + pl.bodyId + " (around ?)");
                continue;
            }
            int pId = SystemOrbitGeometry.resolveOrbitParentBodyId(ch, orbitGeomBodies);
            BodyInfo par = pId >= 0 ? orbitGeomBodies.get(Integer.valueOf(pId)) : null;
            String childName = labelFor(ch);
            String parName = par != null ? labelFor(par) : "?";
            joiner.add(childName + " (around " + parName + ")");
        }
        System.out.println("[EDO][OrbitMap] Drawing " + n + " orbit ring(s): " + joiner);
    }

    private void cancelSubsystemProximityHop() {
        subsystemProximityHopActive = false;
        subsystemHopTick = 0;
        if (subsystemHopTimer != null) {
            subsystemHopTimer.stop();
        }
    }

    private void ensureSubsystemHopTimer() {
        if (subsystemHopTimer == null) {
            subsystemHopTimer = new Timer(SUBSYSTEM_HOP_TICK_MS, e -> tickSubsystemProximityHop());
            subsystemHopTimer.setRepeats(true);
        }
    }

    private static double hopEaseInOut(double t) {
        if (t <= 0) {
            return 0.0;
        }
        if (t >= 1) {
            return 1.0;
        }
        return t * t * (3.0 - 2.0 * t);
    }

    private static double hopLerp(double a, double b, double u) {
        return a + (b - a) * u;
    }

    private BodyDot findBodyDot(int bodyId) {
        for (BodyDot d : dots) {
            if (d.bodyId == bodyId) {
                return d;
            }
        }
        return null;
    }

    private boolean worldXYForBody(int bodyId, double[] outXY) {
        BodyDot d = findBodyDot(bodyId);
        if (d != null && Double.isFinite(d.wx) && Double.isFinite(d.wy)) {
            outXY[0] = d.wx;
            outXY[1] = d.wy;
            return true;
        }
        if (orbitGeomPositions != null) {
            double[] r = orbitGeomPositions.get(Integer.valueOf(bodyId));
            if (r != null && r.length >= 2 && Double.isFinite(r[0]) && Double.isFinite(r[1])) {
                outXY[0] = r[0];
                outXY[1] = r[1];
                return true;
            }
        }
        return false;
    }

    private static int subsystemFocusKeyForBody(int bodyId, Map<Integer, BodyInfo> bodies) {
        if (bodies == null || bodyId < 0 || !bodies.containsKey(Integer.valueOf(bodyId))) {
            return -1;
        }
        Set<Integer> one = Collections.singleton(Integer.valueOf(bodyId));
        return deepestCommonOrbitAncestor(one, bodies);
    }

    private void maybeStartSubsystemProximityHop(Integer newHighlightId, Map<Integer, BodyInfo> bodies,
            boolean orbitSchematicPlaybackActive) {
        if (orbitSchematicPlaybackActive) {
            return;
        }
        if (subsystemProximityHopActive) {
            return;
        }
        if (newHighlightId == null || newHighlightId.intValue() < 0) {
            return;
        }
        if (!bodies.containsKey(newHighlightId)) {
            return;
        }
        if (zoomFactor < ZOOM_SUBSYSTEM_CENTER_LOCK - 1e-6) {
            return;
        }
        Integer prev = prevProximityHighlightBodyId;
        if (prev == null || Objects.equals(prev, newHighlightId)) {
            return;
        }
        int k0 = subsystemFocusKeyForBody(prev.intValue(), bodies);
        int k1 = subsystemFocusKeyForBody(newHighlightId.intValue(), bodies);
        if (k0 < 0 || k1 < 0 || k0 == k1) {
            return;
        }

        Bounds bb = computeBounds(dots,
                shipKnown ? shipWx : Double.NaN,
                shipKnown ? shipWy : Double.NaN);
        double midCx = (bb.minX + bb.maxX) * 0.5;
        double midCy = (bb.minY + bb.maxY) * 0.5;

        int hubBodyId = k1;
        double[] hub = new double[2];
        if (!worldXYForBody(hubBodyId, hub)) {
            return;
        }

        double targetZoom = (hubBodyId == 0) ? SUBSYSTEM_HOP_TARGET_ZOOM_STAR : SUBSYSTEM_HOP_TARGET_ZOOM_MOON_CLUSTER;
        targetZoom = clamp(targetZoom, ZOOM_MIN, ZOOM_MAX);

        subHopZ0 = zoomFactor;
        subHopZ1 = 1.0;
        subHopZ2 = targetZoom;
        subHopX0 = viewCenterWx;
        subHopY0 = viewCenterWy;
        subHopX1 = midCx;
        subHopY1 = midCy;
        subHopX2 = hub[0];
        subHopY2 = hub[1];
        subsystemHopTick = 0;
        subsystemProximityHopActive = true;
        ensureSubsystemHopTimer();
        subsystemHopTimer.start();
    }

    private void tickSubsystemProximityHop() {
        if (!subsystemProximityHopActive) {
            if (subsystemHopTimer != null) {
                subsystemHopTimer.stop();
            }
            return;
        }
        int out = SUBSYSTEM_HOP_OUT_TICKS;
        int inn = SUBSYSTEM_HOP_IN_TICKS;
        int total = out + inn;
        int tick = subsystemHopTick++;
        if (tick < out) {
            double u = hopEaseInOut((tick + 1.0) / out);
            zoomFactor = hopLerp(subHopZ0, subHopZ1, u);
            viewCenterWx = hopLerp(subHopX0, subHopX1, u);
            viewCenterWy = hopLerp(subHopY0, subHopY1, u);
        } else if (tick < total) {
            int lt = tick - out;
            double u = hopEaseInOut((lt + 1.0) / inn);
            zoomFactor = hopLerp(subHopZ1, subHopZ2, u);
            viewCenterWx = hopLerp(subHopX1, subHopX2, u);
            viewCenterWy = hopLerp(subHopY1, subHopY2, u);
        } else {
            zoomFactor = clamp(subHopZ2, ZOOM_MIN, ZOOM_MAX);
            viewCenterWx = subHopX2;
            viewCenterWy = subHopY2;
            cancelSubsystemProximityHop();
        }
        zoomFactor = clamp(zoomFactor, ZOOM_MIN, ZOOM_MAX);
        rebuildOrbitPolylines(true);
        repaint();
    }

    /**
     * More segments when zoomed in so Kepler/fallback rings stay visually smooth on screen (fewer polygon flats).
     */
    private static int orbitSegmentsForZoom(double zoom) {
        if (zoom <= 1.0) {
            return ORBIT_SEGMENTS_MIN;
        }
        double z = Math.min(zoom, ZOOM_MAX);
        double t = Math.sqrt((z - 1.0) / (ZOOM_MAX - 1.0));
        int n = (int) Math.round(ORBIT_SEGMENTS_MIN + t * (ORBIT_SEGMENTS_MAX - ORBIT_SEGMENTS_MIN));
        return Math.max(ORBIT_SEGMENTS_MIN, Math.min(ORBIT_SEGMENTS_MAX, n));
    }

    /**
     * Updates dot and ship world positions without resetting zoom or layout spans.
     * Rebuilds orbit polylines from the new positions so rings stay centred on moving parents (e.g. during Play).
     * Uses a fixed zoom-based segment count (not screen chord) each tick so schematic rings do not reshuffle vertex
     * count when their inferred radius breathes — that was causing tangential stutter along the stroke during playback.
     * Subsystem follow hub snapping during schematic playback runs on the next {@link #paintComponent}, not here, so
     * this stays cheap. Wheel-driven recentre is applied only in {@link #applyWheelZoomAtComponent}.
     * The “fit all” world span used for scale is not recomputed here (it is refreshed on {@link #setScene} only), so
     * fast-forward animation does not rescale the plot as the bounding box of bodies changes each frame.
     * Used while the System tab runs fast-forward orbit animation with an unchanged body id set.
     * <p>
     * Each successful call sets {@link #orbitSchematicPlaybackActive} from {@code orbitSchematicPlaybackActive}.
     * That flag is normally refreshed in {@link #setScene}, but the animation path skips {@code setScene} once
     * geometry matches — without this sync, paint-time follow would not snap the camera to the moving hub during
     * playback.
     *
     * @return false when the scene is empty, geometry is missing, or the body id set changed (caller should use
     *         {@link #setScene})
     */
    public boolean tryApplyPositionUpdate(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> positions,
            double[] shipM,
            Integer anchorBodyId,
            Integer highlightNearBodyId,
            boolean orbitSchematicPlaybackActive) {
        if (sceneEmpty || bodies == null || positions == null || orbitGeomBodies == null) {
            return false;
        }
        if (!orbitGeomBodies.keySet().equals(bodies.keySet())) {
            return false;
        }
        this.orbitSchematicPlaybackActive = orbitSchematicPlaybackActive;
        this.anchorBodyId = anchorBodyId;
        this.highlightNearBodyId = highlightNearBodyId;
        orbitGeomBodies = bodies;
        orbitGeomPositions = positions;
        for (BodyDot d : dots) {
            double[] p = positions.get(Integer.valueOf(d.bodyId));
            if (p == null || p.length < 2) {
                continue;
            }
            if (!Double.isFinite(p[0]) || !Double.isFinite(p[1])) {
                continue;
            }
            d.wx = p[0];
            d.wy = p[1];
        }
        if (shipM != null && shipM.length >= 2
                && Double.isFinite(shipM[0]) && Double.isFinite(shipM[1])) {
            shipKnown = true;
            shipWx = shipM[0];
            shipWy = shipM[1];
        } else {
            shipKnown = false;
            shipWx = 0;
            shipWy = 0;
        }
        rebuildOrbitPolylines(true, false);
        repaint();
        return true;
    }

    /**
     * After pausing schematic orbit playback, request that the next paint recentre the zoom focal point on the
     * subsystem follow hub (or dot centroid when the pause-style fallback applies) using current dot positions so the
     * view matches real-time geometry. The next paint runs {@link #maybeApplySubsystemCenterLock} with
     * {@code pauseResync}; hub follow uses {@link #snapOrSmoothViewCenterToward} once (wheel-only easing is not used).
     */
    public void syncViewCenterToSubsystemHubAfterOrbitPause() {
        cancelSubsystemProximityHop();
        pendingSubsystemCenterPauseResync = true;
        repaint();
    }

    private void handleMouseWheel(MouseWheelEvent e) {
        cancelSubsystemProximityHop();
        applyWheelZoomAtComponent(e.getX(), e.getY(), e.getWheelRotation());
    }

    /**
     * Mouse pass-through: zoom from global screen coordinates when the pointer is over this panel (see
     * {@link org.dce.ed.SystemTabPanel#applyPassThroughWheelIfHit}).
     *
     * @return {@code true} if the wheel was consumed here (pointer over the map with a non-empty scene), including
     *         when already at min/max zoom so the bodies table scroller does not also react
     */
    public boolean applyPassThroughWheelIfHit(int screenX, int screenY, int wheelRotation) {
        if (wheelRotation == 0 || !isShowing() || sceneEmpty || dots.isEmpty()) {
            return false;
        }
        Point p = new Point(screenX, screenY);
        SwingUtilities.convertPointFromScreen(p, this);
        if (!contains(p)) {
            return false;
        }
        applyWheelZoomAtComponent(p.x, p.y, wheelRotation);
        return true;
    }

    private void applyWheelZoomAtComponent(int localX, int localY, int wheelRotation) {
        cancelSubsystemProximityHop();
        if (sceneEmpty || dots.isEmpty()) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        double spanX = layoutSpanX;
        double spanY = layoutSpanY;

        Font base = getFont();
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
        }
        int plotH = Math.max(88, h - MAP_BOTTOM_INSET);
        double availW = w - 2.0 * PAD;
        double availH = plotH - 2.0 * PAD;
        double scaleFit = Math.min(availW / spanX, availH / spanY);
        double scale = scaleFit * zoomFactor;

        double ex = localX;
        double ey = localY;
        double wx = viewCenterWx + (ex - PAD - availW / 2.0) / scale;
        double wy = viewCenterWy - (ey - PAD - availH / 2.0) / scale;

        int clicks = wheelRotation;
        if (clicks == 0) {
            return;
        }
        double factor = Math.pow(ZOOM_PER_NOTCH, -clicks);
        double newZoom = clamp(zoomFactor * factor, ZOOM_MIN, ZOOM_MAX);
        double prevZoom = zoomFactor;
        if (newZoom == prevZoom) {
            return;
        }
        zoomFactor = newZoom;
        double scaleNew = scaleFit * zoomFactor;
        viewCenterWx = wx - (ex - PAD - availW / 2.0) / scaleNew;
        viewCenterWy = wy + (ey - PAD - availH / 2.0) / scaleNew;
        if (!orbitSchematicPlaybackActive
                && zoomFactor >= ZOOM_SUBSYSTEM_CENTER_LOCK
                && zoomFactor > prevZoom) {
            ResolvedSubsystemHub hub = tryResolveSubsystemFollowHub(availW, availH, scaleNew, false);
            if (hub != null) {
                nudgeViewCenterTowardSubsystemHubOnWheel(hub, scaleNew);
            }
        }
        rebuildOrbitPolylines(false);
        repaint();
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static boolean isMoonBody(BodyInfo b) {
        if (b == null) {
            return false;
        }
        String s = firstNonBlankName(b.getShortName(), b.getBodyName());
        if (s == null) {
            return false;
        }
        s = s.trim();
        if (MOON_NAME_COMPACT.matcher(s).matches()) {
            return true;
        }
        String[] parts = s.split("\\s+");
        if (parts.length >= 2) {
            String last = parts[parts.length - 1];
            String prev = parts[parts.length - 2];
            if (prev.matches("\\d+") && last.length() == 1 && Character.isLetter(last.charAt(0))) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonBlankName(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    /**
     * Same rule as {@link org.dce.ed.SystemTabPanel} Body column: primary star shows {@code *}, not the system name.
     */
    private static boolean isPrimaryStarBody(BodyInfo b) {
        if (b == null) {
            return false;
        }
        String shortName = b.getShortName();
        return shortName != null
                && b.getStarType() != null
                && b.getStarSystem() != null
                && shortName.equals(b.getStarSystem());
    }

    private static String labelFor(BodyInfo b) {
        String s = b.getShortName();
        if (s == null || s.isBlank()) {
            s = b.getBodyName();
        }
        if (s == null || s.isBlank()) {
            return "?" + b.getBodyId();
        }
        s = s.trim();
        if (s.length() > LABEL_TRUNCATE) {
            return s.substring(0, (int) LABEL_TRUNCATE - 1) + "\u2026";
        }
        return s;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            /* Match overlay frame: quality + AA + pure strokes so thin curves are actually smoothed on D3D. */
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                    RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }

            Font base = getFont();
            if (base == null) {
                base = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
            }
            Font labelFont = base.deriveFont(Font.PLAIN, base.getSize2D());
            g2.setFont(labelFont);
            FontMetrics labelFm = g2.getFontMetrics(labelFont);

            int plotH = Math.max(88, h - MAP_BOTTOM_INSET);

            if (sceneEmpty || dots.isEmpty()) {
                g2.setColor(EdoUi.Internal.GRAY_180);
                String msg = sceneEmpty ? "No bodies to plot" : "No position data";
                g2.drawString(msg, PAD, PAD + labelFm.getAscent());
                return;
            }

            double spanX = layoutSpanX;
            double spanY = layoutSpanY;

            double availW = w - 2.0 * PAD;
            double availH = plotH - 2.0 * PAD;
            double scaleFit = Math.min(availW / spanX, availH / spanY);
            double scale = scaleFit * zoomFactor;
            boolean pauseResync = pendingSubsystemCenterPauseResync;
            if (pendingSubsystemCenterPauseResync) {
                pendingSubsystemCenterPauseResync = false;
            }
            if (subsystemProximityHopActive) {
                subsystemScreenLockHubId = -1;
            } else {
                maybeApplySubsystemCenterLock(availW, availH, scale, pauseResync);
            }
            double vcx = viewCenterWx;
            double vcy = viewCenterWy;

            final double plotCx = PAD + availW * 0.5;
            final double plotCy = PAD + availH * 0.5;

            g2.setColor(EdoUi.Internal.GRAY_ALPHA_140);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(PAD, PAD, (int) Math.round(availW), (int) Math.round(availH), 6, 6);

            if (orbitLines != null && !orbitLines.isEmpty()) {
                /*
                 * Fill the stroked outline (not draw the raw path): shape AA applies reliably to the filled band.
                 * Single-pixel draw() on some Windows/Java2D pipelines stays visibly aliased even with AA hints on.
                 */
                Color orbitBlue = new Color(110, 165, 220);
                BasicStroke orbitStroke = new BasicStroke(1.15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f);
                g2.setColor(orbitBlue);
                for (OrbitPolylineWorldXY poly : orbitLines) {
                    if (poly == null || poly.wx == null || poly.wy == null
                            || poly.wx.length < 3 || poly.wy.length != poly.wx.length) {
                        continue;
                    }
                    Path2D path = new Path2D.Double();
                    boolean moved = false;
                    double prevSx = Double.NaN;
                    double prevSy = Double.NaN;
                    for (int i = 0; i < poly.wx.length; i++) {
                        double sx = PAD + availW / 2.0 + (poly.wx[i] - vcx) * scale;
                        double sy = PAD + availH / 2.0 - (poly.wy[i] - vcy) * scale;
                        if (!Double.isFinite(sx) || !Double.isFinite(sy)) {
                            moved = false;
                            break;
                        }
                        if (moved && sx == prevSx && sy == prevSy) {
                            continue;
                        }
                        if (!moved) {
                            path.moveTo(sx, sy);
                            moved = true;
                        } else {
                            path.lineTo(sx, sy);
                        }
                        prevSx = sx;
                        prevSy = sy;
                    }
                    if (moved) {
                        path.closePath();
                        g2.fill(orbitStroke.createStrokedShape(path));
                    }
                }
            }

            g2.setFont(labelFont);
            float dotEm = Math.max(8f, labelFm.getHeight() * 0.42f);
            float starR = Math.max(4.5f, dotEm * 1.05f);
            float bodyR = Math.max(3f, dotEm * 0.62f);

            boolean showMoonLabels = zoomFactor >= ZOOM_SHOW_MOON_LABELS;

            for (BodyDot d : dots) {
                boolean lockHub = subsystemScreenLockHubId >= 0 && d.bodyId == subsystemScreenLockHubId;
                double sx = lockHub ? plotCx : PAD + availW / 2.0 + (d.wx - vcx) * scale;
                double sy = lockHub ? plotCy : PAD + availH / 2.0 - (d.wy - vcy) * scale;
                if (!Double.isFinite(sx) || !Double.isFinite(sy)) {
                    continue;
                }
                float r = d.star ? starR : bodyR;
                boolean lumpHub = subsystemHubLump(zoomFactor, d);
                if (lumpHub) {
                    drawSubsystemHubLumpMarker(g2, sx, sy, dotEm);
                } else {
                    Color fill = d.star ? new Color(255, 200, 80) : EdoUi.User.MAIN_TEXT;
                    g2.setColor(fill);
                    g2.fill(new Ellipse2D.Double(sx - r, sy - r, r * 2, r * 2));
                }

                boolean hi = highlightNearBodyId != null && highlightNearBodyId.intValue() == d.bodyId;
                boolean anch = anchorBodyId != null && anchorBodyId.intValue() == d.bodyId;
                if (hi || anch) {
                    g2.setColor(new Color(0, 200, 255, hi ? 220 : 140));
                    g2.setStroke(new BasicStroke(hi ? 2f : 1.2f));
                    float hr = lumpHub ? Math.max(11f, r * 2.2f) : r + Math.max(3.5f, dotEm * 0.55f);
                    g2.draw(new Ellipse2D.Double(sx - hr, sy - hr, hr * 2, hr * 2));
                }
            }

            Path2D shipPath = null;
            double shipSx = 0;
            double shipSy = 0;
            if (shipKnown) {
                shipSx = PAD + availW / 2.0 + (shipWx - vcx) * scale;
                shipSy = PAD + availH / 2.0 - (shipWy - vcy) * scale;
                if (Double.isFinite(shipSx) && Double.isFinite(shipSy)) {
                    float triH = Math.max(10f, labelFm.getHeight() * 0.72f);
                    float triW = triH * 0.78f;
                    shipPath = new Path2D.Double();
                    shipPath.moveTo(shipSx, shipSy - triH * 0.55);
                    shipPath.lineTo(shipSx - triW, shipSy + triH * 0.45);
                    shipPath.lineTo(shipSx + triW, shipSy + triH * 0.45);
                    shipPath.closePath();
                    g2.setColor(new Color(0, 220, 120));
                    g2.fill(shipPath);
                    g2.setColor(EdoUi.User.MAIN_TEXT);
                    g2.setStroke(new BasicStroke(1f));
                    g2.draw(shipPath);
                }
            }

            /* Labels last so orbit strokes never paint over text. */
            for (BodyDot d : dots) {
                boolean lockHub = subsystemScreenLockHubId >= 0 && d.bodyId == subsystemScreenLockHubId;
                double sx = lockHub ? plotCx : PAD + availW / 2.0 + (d.wx - vcx) * scale;
                double sy = lockHub ? plotCy : PAD + availH / 2.0 - (d.wy - vcy) * scale;
                if (!Double.isFinite(sx) || !Double.isFinite(sy)) {
                    continue;
                }
                float r = d.star ? starR : bodyR;
                boolean lumpHub = subsystemHubLump(zoomFactor, d);
                float rLabel = lumpHub ? Math.max(9f, r * 1.9f) : r;

                if (d.primaryStarAsterisk) {
                    /* Dot only — matches System tab * column without duplicating a map label. */
                    continue;
                }

                boolean drawName = d.moon ? showMoonLabels : true;
                if (!drawName || d.label == null || d.label.isEmpty()) {
                    continue;
                }

                float[] lp = bodyLabelAnchor((float) sx, (float) sy, rLabel, d.bodyId, d.label, labelFm);
                drawLabelOutlined(g2, d.label, lp[0], lp[1]);
            }

            if (shipPath != null) {
                g2.setFont(labelFont);
                String you = "You";
                float yw = labelFm.stringWidth(you);
                float yx = (float) shipSx - yw / 2f;
                float yy = (float) shipSy - Math.max(9f, labelFm.getHeight() * 0.55f) - 2f;
                drawLabelOutlined(g2, you, yx, yy);
            }
        } finally {
            g2.dispose();
        }
    }

    private static Set<Integer> collectSubsystemHubBodyIds(Map<Integer, BodyInfo> bodies) {
        HashSet<Integer> hubs = new HashSet<>();
        if (bodies == null || bodies.isEmpty()) {
            return hubs;
        }
        for (BodyInfo child : bodies.values()) {
            if (child == null || !isMoonBody(child)) {
                continue;
            }
            int pId = SystemOrbitGeometry.resolveOrbitParentBodyId(child, bodies);
            if (pId < 0) {
                continue;
            }
            BodyInfo parent = bodies.get(Integer.valueOf(pId));
            if (parent == null || isPrimaryStarBody(parent)) {
                continue;
            }
            hubs.add(Integer.valueOf(pId));
        }
        return hubs;
    }

    private boolean subsystemHubLump(double zoom, BodyDot d) {
        return zoom < ZOOM_SUBSYSTEM_HUB_DETAIL && subsystemHubLumpBodyIds.contains(d.bodyId);
    }

    /** Twin concentric rings (orbit blue) in place of subsystem-parent dots when zoomed out. */
    private static void drawSubsystemHubLumpMarker(Graphics2D g2, double sx, double sy, float em) {
        Stroke prevStroke = g2.getStroke();
        Color prevColor = g2.getColor();
        try {
            float cx = (float) sx;
            float cy = (float) sy;
            g2.setColor(new Color(110, 165, 220, 225));
            g2.setStroke(new BasicStroke(1.15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float rInner = Math.max(3.5f, em * 0.52f);
            float rOuter = Math.max(6.5f, em * 0.98f);
            g2.draw(new Ellipse2D.Double(cx - rInner, cy - rInner, rInner * 2, rInner * 2));
            g2.draw(new Ellipse2D.Double(cx - rOuter, cy - rOuter, rOuter * 2, rOuter * 2));
        } finally {
            g2.setStroke(prevStroke);
            g2.setColor(prevColor);
        }
    }

    /**
     * Stagger label anchors so clustered bodies don't share one diagonal (deterministic by body id).
     */
    private static float[] bodyLabelAnchor(float sx, float sy, float r, int bodyId, String label, FontMetrics fm) {
        int wlab = (label != null) ? fm.stringWidth(label) : 0;
        int hlab = fm.getHeight();
        int slot = Math.floorMod(bodyId, 8);
        float gap = r + 5f;
        switch (slot) {
            case 0:
                return new float[] { sx + gap, sy - hlab * 0.35f };
            case 1:
                return new float[] { sx - wlab - gap, sy - hlab * 0.35f };
            case 2:
                return new float[] { sx - wlab * 0.5f, sy - gap - hlab };
            case 3:
                /* Tight east-north offset — old sy+gap read too far below the dot on wide orbits. */
                return new float[] { sx + gap * 0.55f, sy - hlab * 0.38f };
            case 4:
                return new float[] { sx + gap * 0.7f, sy - gap * 0.9f - hlab };
            case 5:
                return new float[] { sx - wlab - gap * 0.7f, sy + gap * 0.5f };
            case 6:
                return new float[] { sx + gap, sy + gap * 0.4f };
            default:
                return new float[] { sx - wlab - gap, sy - gap * 0.7f - hlab };
        }
    }

    private static void drawLabelOutlined(Graphics2D g2, String text, float x, float y) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Color fg = EdoUi.User.MAIN_TEXT;
        g2.setColor(new Color(0, 0, 0, 160));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    g2.drawString(text, x + dx, y + dy);
                }
            }
        }
        g2.setColor(fg);
        g2.drawString(text, x, y);
    }

    private Set<Integer> allPlottedBodyIds() {
        Set<Integer> s = new HashSet<>();
        for (BodyDot d : dots) {
            s.add(Integer.valueOf(d.bodyId));
        }
        return s;
    }

    /** Body ids whose dots fall inside an axis-aligned world window around {@link #viewCenterWx}/{@link #viewCenterWy}. */
    private Set<Integer> collectBodyIdsNearViewCenter(double halfW, double halfH, double margin) {
        double mw = halfW * margin;
        double mh = halfH * margin;
        double minWx = viewCenterWx - mw;
        double maxWx = viewCenterWx + mw;
        double minWy = viewCenterWy - mh;
        double maxWy = viewCenterWy + mh;
        Set<Integer> visible = new HashSet<>();
        for (BodyDot d : dots) {
            if (d.wx >= minWx && d.wx <= maxWx && d.wy >= minWy && d.wy <= maxWy) {
                visible.add(Integer.valueOf(d.bodyId));
            }
        }
        return visible;
    }

    /**
     * World-space subsystem follow hub resolved from viewport + orbit tree (centroid fallbacks applied inside when
     * needed).
     */
    private record ResolvedSubsystemHub(int hubId, double tx, double ty) {}

    /**
     * Resolves subsystem follow hub position. May call {@link #applyViewCenterToDotsCentroid} or
     * {@link #applyViewCenterToCentroidOfBodyIds} for special fallbacks.
     *
     * @return {@code null} when no hub applies
     */
    private ResolvedSubsystemHub tryResolveSubsystemFollowHub(double availW, double availH, double scale, boolean pauseResync) {
        if (sceneEmpty || dots.isEmpty() || orbitGeomBodies == null || orbitGeomBodies.isEmpty()) {
            return null;
        }
        if (scale <= 0 || !Double.isFinite(scale)) {
            return null;
        }
        double halfW = availW / (2.0 * scale);
        double halfH = availH / (2.0 * scale);
        if (!Double.isFinite(halfW) || !Double.isFinite(halfH)) {
            return null;
        }
        Set<Integer> visible = collectBodyIdsNearViewCenter(halfW, halfH, 1.0);
        if (visible.isEmpty()) {
            visible = collectBodyIdsNearViewCenter(halfW, halfH, SUBSYSTEM_FOLLOW_VISIBLE_MARGIN);
        }
        boolean fullSystemHubPick = false;
        if (visible.isEmpty()) {
            visible = allPlottedBodyIds();
            fullSystemHubPick = true;
        }
        if (visible.isEmpty()) {
            return null;
        }
        if (fullSystemHubPick) {
            if (pauseResync) {
                applyViewCenterToDotsCentroid(scale);
            }
            return null;
        }
        Set<Integer> pickedForHub = new HashSet<>();
        for (Integer idObj : visible) {
            if (idObj != null && orbitGeomBodies.containsKey(idObj)) {
                pickedForHub.add(idObj);
            }
        }
        int hub = -1;
        if (highlightNearBodyId != null && highlightNearBodyId.intValue() >= 0
                && orbitGeomBodies.containsKey(highlightNearBodyId)) {
            int hid = highlightNearBodyId.intValue();
            if (orbitDepthFromStar(hid, orbitGeomBodies) >= 2) {
                hub = subsystemFocusKeyForBody(hid, orbitGeomBodies);
            }
        }
        if (hub < 0) {
            if (pickedForHub.isEmpty()) {
                return null;
            }
            hub = deepestCommonOrbitAncestor(pickedForHub, orbitGeomBodies);
            if (hub == 0 && pickedForHub.size() > 1) {
                Set<Integer> withoutStar = new HashSet<>(pickedForHub);
                withoutStar.remove(Integer.valueOf(0));
                if (!withoutStar.isEmpty()) {
                    hub = deepestCommonOrbitAncestor(withoutStar, orbitGeomBodies);
                }
            }
            if (hub == 0 && !pickedForHub.contains(Integer.valueOf(0))) {
                applyViewCenterToCentroidOfBodyIds(pickedForHub, scale);
                return null;
            }
        }
        if (hub < 0) {
            return null;
        }
        double hx = Double.NaN;
        double hy = Double.NaN;
        for (BodyDot d : dots) {
            if (d.bodyId == hub) {
                hx = d.wx;
                hy = d.wy;
                break;
            }
        }
        if ((!Double.isFinite(hx) || !Double.isFinite(hy)) && orbitGeomPositions != null) {
            double[] raw = orbitGeomPositions.get(Integer.valueOf(hub));
            if (raw != null && raw.length >= 2) {
                hx = raw[0];
                hy = raw[1];
            }
        }
        BodyDot hubDot = findBodyDot(hub);
        double tx = hubDot != null && Double.isFinite(hubDot.wx) ? hubDot.wx : hx;
        double ty = hubDot != null && Double.isFinite(hubDot.wy) ? hubDot.wy : hy;
        if (!Double.isFinite(tx) || !Double.isFinite(ty)) {
            return null;
        }
        return new ResolvedSubsystemHub(hub, tx, ty);
    }

    /**
     * Per mouse-wheel zoom-in step: move {@link #viewCenterWx}/{@link #viewCenterWy} partway toward the subsystem hub.
     * Blend increases with {@link #zoomFactor} so shallow zoom-in is gentle and max zoom is almost a full snap.
     */
    private void nudgeViewCenterTowardSubsystemHubOnWheel(ResolvedSubsystemHub hub, double scale) {
        double dwx = hub.tx() - viewCenterWx;
        double dwy = hub.ty() - viewCenterWy;
        double errPx = Math.hypot(dwx * scale, dwy * scale);
        if (!Double.isFinite(errPx)) {
            return;
        }
        if (errPx <= WHEEL_SUBSYSTEM_SNAP_ERR_PX) {
            viewCenterWx = hub.tx();
            viewCenterWy = hub.ty();
            subsystemScreenLockHubId = hub.hubId();
            return;
        }
        double span = ZOOM_MAX - ZOOM_SUBSYSTEM_CENTER_LOCK;
        double u = span > 1e-12 ? (zoomFactor - ZOOM_SUBSYSTEM_CENTER_LOCK) / span : 1.0;
        u = clamp(u, 0.0, 1.0);
        double blend = WHEEL_SUBSYSTEM_NUDGE_BLEND_MIN
                + (WHEEL_SUBSYSTEM_NUDGE_BLEND_MAX - WHEEL_SUBSYSTEM_NUDGE_BLEND_MIN) * Math.pow(u, WHEEL_SUBSYSTEM_NUDGE_BLEND_GAMMA);
        blend = clamp(blend, WHEEL_SUBSYSTEM_NUDGE_BLEND_MIN, WHEEL_SUBSYSTEM_NUDGE_BLEND_MAX);
        viewCenterWx += dwx * blend;
        viewCenterWy += dwy * blend;
        double errAfter = Math.hypot((hub.tx() - viewCenterWx) * scale, (hub.ty() - viewCenterWy) * scale);
        if (errAfter <= WHEEL_SUBSYSTEM_SNAP_ERR_PX) {
            viewCenterWx = hub.tx();
            viewCenterWy = hub.ty();
            subsystemScreenLockHubId = hub.hubId();
        } else {
            subsystemScreenLockHubId = errAfter <= VIEW_CENTER_HUB_PIN_SCREEN_PX ? hub.hubId() : -1;
        }
    }

    /**
     * Ease {@link #viewCenterWx}/{@link #viewCenterWy} toward a world-space target (centroid / pause resync). Small
     * screen-space errors close in one step; larger gaps ease with a capped blend per frame. Schedules another paint
     * while the gap is still noticeable.
     */
    private void smoothViewCenterToward(double targetWx, double targetWy, double scale) {
        if (!Double.isFinite(scale) || scale <= 0 || !Double.isFinite(targetWx) || !Double.isFinite(targetWy)) {
            return;
        }
        double dwx = targetWx - viewCenterWx;
        double dwy = targetWy - viewCenterWy;
        if (!Double.isFinite(dwx) || !Double.isFinite(dwy)) {
            return;
        }
        double errPx = Math.hypot(dwx * scale, dwy * scale);
        if (errPx <= VIEW_CENTER_LOCK_EPS_PX) {
            viewCenterWx = targetWx;
            viewCenterWy = targetWy;
            return;
        }
        double alpha;
        if (errPx <= VIEW_CENTER_SMOOTH_FULL_BELOW_PX) {
            alpha = 1.0;
        } else {
            alpha = Math.min(VIEW_CENTER_SMOOTH_CAP, VIEW_CENTER_SMOOTH_GAIN * errPx);
            double stepCap = VIEW_CENTER_SMOOTH_MAX_STEP_PX / errPx;
            alpha = Math.min(alpha, stepCap);
            alpha = Math.max(1.2e-4, alpha);
        }
        if (alpha >= 1.0 - 1e-12) {
            viewCenterWx = targetWx;
            viewCenterWy = targetWy;
        } else {
            viewCenterWx += dwx * alpha;
            viewCenterWy += dwy * alpha;
        }
        double errAfter = Math.hypot((targetWx - viewCenterWx) * scale, (targetWy - viewCenterWy) * scale);
        if (errAfter > VIEW_CENTER_LOCK_EPS_PX) {
            SwingUtilities.invokeLater(() -> {
                if (isShowing()) {
                    repaint();
                }
            });
        }
    }

    /**
     * Subsystem follow: snap to target while schematic playback is active (no eased lag on a moving hub); otherwise
     * {@link #smoothViewCenterToward(double, double, double)}.
     */
    private void snapOrSmoothViewCenterToward(double targetWx, double targetWy, double scale) {
        if (orbitSchematicPlaybackActive) {
            if (Double.isFinite(targetWx) && Double.isFinite(targetWy)) {
                viewCenterWx = targetWx;
                viewCenterWy = targetWy;
            }
        } else {
            smoothViewCenterToward(targetWx, targetWy, scale);
        }
    }

    /**
     * When zoomed in past {@link #ZOOM_SUBSYSTEM_CENTER_LOCK}, keeps the subsystem hub marker pinned when the view is
     * already centred; schematic playback snaps the camera to the hub each paint. Gradual recentre toward the hub is
     * driven only by zoom-in mouse wheel via {@link #nudgeViewCenterTowardSubsystemHubOnWheel}. Resize does not pan the
     * view. After pausing orbit playback, {@code pauseResync} runs one eased step toward the hub when resolved.
     */
    private void maybeApplySubsystemCenterLock(double availW, double availH, double scale, boolean pauseResync) {
        if (sceneEmpty || dots.isEmpty() || orbitGeomBodies == null || orbitGeomBodies.isEmpty()) {
            subsystemScreenLockHubId = -1;
            return;
        }
        if (zoomFactor < ZOOM_SUBSYSTEM_CENTER_LOCK || scale <= 0 || !Double.isFinite(scale)) {
            subsystemScreenLockHubId = -1;
            return;
        }
        ResolvedSubsystemHub h = tryResolveSubsystemFollowHub(availW, availH, scale, pauseResync);
        if (h == null) {
            subsystemScreenLockHubId = -1;
            return;
        }
        if (orbitSchematicPlaybackActive) {
            viewCenterWx = h.tx();
            viewCenterWy = h.ty();
            subsystemScreenLockHubId = h.hubId();
            return;
        }
        if (pauseResync) {
            snapOrSmoothViewCenterToward(h.tx(), h.ty(), scale);
        }
        double errPx = Math.hypot((h.tx() - viewCenterWx) * scale, (h.ty() - viewCenterWy) * scale);
        subsystemScreenLockHubId = errPx <= VIEW_CENTER_HUB_PIN_SCREEN_PX ? h.hubId() : -1;
    }

    private void applyViewCenterToDotsCentroid(double scale) {
        subsystemScreenLockHubId = -1;
        double sx = 0.0;
        double sy = 0.0;
        int n = 0;
        for (BodyDot d : dots) {
            if (Double.isFinite(d.wx) && Double.isFinite(d.wy)) {
                sx += d.wx;
                sy += d.wy;
                n++;
            }
        }
        if (n > 0) {
            snapOrSmoothViewCenterToward(sx / n, sy / n, scale);
        }
    }

    /** Recentre on the average world position of the given body ids (subset of {@link #dots}). */
    private void applyViewCenterToCentroidOfBodyIds(Set<Integer> bodyIds, double scale) {
        subsystemScreenLockHubId = -1;
        if (bodyIds == null || bodyIds.isEmpty()) {
            return;
        }
        double sx = 0.0;
        double sy = 0.0;
        int n = 0;
        for (BodyDot d : dots) {
            if (!bodyIds.contains(Integer.valueOf(d.bodyId))) {
                continue;
            }
            if (Double.isFinite(d.wx) && Double.isFinite(d.wy)) {
                sx += d.wx;
                sy += d.wy;
                n++;
            }
        }
        if (n > 0) {
            snapOrSmoothViewCenterToward(sx / n, sy / n, scale);
        }
    }

    /** Deepest node in the intersection of orbit-parent chains; sole visible body uses its parent as follow hub. */
    private static int deepestCommonOrbitAncestor(Set<Integer> visible, Map<Integer, BodyInfo> bodies) {
        if (visible == null || visible.isEmpty() || bodies == null || bodies.isEmpty()) {
            return -1;
        }
        Set<Integer> common = null;
        for (Integer idObj : visible) {
            if (idObj == null) {
                continue;
            }
            Set<Integer> chain = new HashSet<>();
            addOrbitAncestors(idObj.intValue(), bodies, chain);
            if (chain.isEmpty()) {
                continue;
            }
            if (common == null) {
                common = new HashSet<>(chain);
            } else {
                common.retainAll(chain);
            }
        }
        if (common == null || common.isEmpty()) {
            return 0;
        }
        int best = 0;
        int bestDepth = -1;
        for (Integer cObj : common) {
            if (cObj == null) {
                continue;
            }
            int c = cObj.intValue();
            int d = orbitDepthFromStar(c, bodies);
            if (d > bestDepth || (d == bestDepth && c > best)) {
                bestDepth = d;
                best = c;
            }
        }
        /* One visible leaf (e.g. a single moon): deepest-in-intersection is that body — follow its parent instead. */
        if (visible.size() == 1 && visible.contains(Integer.valueOf(best))) {
            int p = orbitImmediateParent(best, bodies);
            if (p >= 0) {
                best = p;
            }
        }
        return best;
    }

    private static int orbitImmediateParent(int bodyId, Map<Integer, BodyInfo> bodies) {
        BodyInfo bi = bodies.get(Integer.valueOf(bodyId));
        if (bi == null) {
            return -1;
        }
        return SystemOrbitGeometry.resolveOrbitParentBodyId(bi, bodies);
    }

    private static void addOrbitAncestors(int bodyId, Map<Integer, BodyInfo> bodies, Set<Integer> into) {
        int cur = bodyId;
        for (int guard = 0; guard < 64 && cur >= 0; guard++) {
            into.add(Integer.valueOf(cur));
            if (cur == 0) {
                break;
            }
            BodyInfo bi = bodies.get(Integer.valueOf(cur));
            if (bi == null) {
                break;
            }
            int p = SystemOrbitGeometry.resolveOrbitParentBodyId(bi, bodies);
            if (p < 0 || p == cur) {
                break;
            }
            cur = p;
        }
    }

    /** Number of orbit-parent hops from {@code bodyId} up to body {@code 0} (the star is depth 0). */
    private static int orbitDepthFromStar(int bodyId, Map<Integer, BodyInfo> bodies) {
        int hops = 0;
        int cur = bodyId;
        for (int guard = 0; guard < 64 && cur >= 0; guard++) {
            if (cur == 0) {
                return hops;
            }
            BodyInfo bi = bodies.get(Integer.valueOf(cur));
            if (bi == null) {
                return hops;
            }
            int p = SystemOrbitGeometry.resolveOrbitParentBodyId(bi, bodies);
            if (p < 0 || p == cur) {
                return hops;
            }
            hops++;
            cur = p;
        }
        return hops;
    }

    private static Bounds computeBounds(List<BodyDot> dots, double shipX, double shipY) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (BodyDot d : dots) {
            minX = Math.min(minX, d.wx);
            maxX = Math.max(maxX, d.wx);
            minY = Math.min(minY, d.wy);
            maxY = Math.max(maxY, d.wy);
        }
        if (Double.isFinite(shipX) && Double.isFinite(shipY)) {
            minX = Math.min(minX, shipX);
            maxX = Math.max(maxX, shipX);
            minY = Math.min(minY, shipY);
            maxY = Math.max(maxY, shipY);
        }
        double padM = Math.max(maxX - minX, maxY - minY) * 0.06 + 1e3;
        return new Bounds(minX - padM, maxX + padM, minY - padM, maxY + padM);
    }

    private static final class Bounds {
        final double minX;
        final double maxX;
        final double minY;
        final double maxY;

        Bounds(double minX, double maxX, double minY, double maxY) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    private static final class BodyDot {
        final int bodyId;
        double wx;
        double wy;
        final String label;
        final boolean star;
        /** Short name equals system name — draw {@code *} only (matches System tab). */
        final boolean primaryStarAsterisk;

        final boolean moon;

        BodyDot(int bodyId, double wx, double wy, String label, boolean star, boolean primaryStarAsterisk, boolean moon) {
            this.bodyId = bodyId;
            this.wx = wx;
            this.wy = wy;
            this.label = label;
            this.star = star;
            this.primaryStarAsterisk = primaryStarAsterisk;
            this.moon = moon;
        }
    }
}
