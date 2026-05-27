package org.dce.ed.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.Cursor;
import java.awt.HeadlessException;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

import java.time.Duration;
import java.time.Instant;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import org.dce.ed.OverlayPreferences;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmap.MapScaleMode;
import org.dce.ed.systemmap.MapViewProjection;
import org.dce.ed.systemmap.SystemMapClassification;
import org.dce.ed.systemmap.SystemMapModel;
import org.dce.ed.systemmap.SystemMapOrbitStrokePrinter;
import org.dce.ed.systemmap.SystemMapOrbitStrokePrinter.OrbitStrokeHitInfo;
import org.dce.ed.systemmap.SystemMapPipeline;
import org.dce.ed.systemmap.SystemMapRules;
import org.dce.ed.util.ExplorationBodyCredits;
import org.dce.ed.util.ExplorationBodyCredits.SystemMapDotKind;
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

    private static final double ZOOM_MIN_ABSOLUTE_FLOOR = 0.12;

    /**
     * When {@code false} (default): no automatic camera moves — wheel zoom is scale-about-cursor; middle- or
     * right-drag pans the view when {@link OverlayPreferences#isOverlayMousePassThroughToGame()} is {@code false}
     * (pass-through mode does not receive drag gestures); no subsystem hub/centroid nudging; no min-zoom resize snap;
     * no proximity hop; no automatic wheel recentre toward system centroid on zoom-out; high-zoom subsystem centre
     * lock still runs during schematic playback when {@link #zoomFactor} is past {@link #ZOOM_SUBSYSTEM_CENTER_LOCK};
     * no {@link #setScene} re-framing when
     * the body set changes (telemetry / new scans); no layout-growth zoom easing; no
     * {@link #inflateLayoutSpansUntilZoomMinFitAtMostOne} during wheel zoom (avoids scale jumping under the pointer).
     * The first scene ever still frames once ({@link #lastSceneBodyIdsSnapshot} empty).
     */
    private static final boolean MAP_AUTO_VIEW_PAN = false;
    /**
     * At {@link #zoomMinFit}, visible world width (and height) along each axis is at most this × {@link #layoutSpanX}
     * (resp. {@link #layoutSpanY}) — larger values allow zooming out farther so an off-centre view can still frame the
     * whole system before hitting the floor.
     */
    /** Visible span at {@link #zoomMinFit} is about this × {@link #layoutSpanX}/{@link #layoutSpanY} (margin for pan). */
    private static final double ZOOM_MIN_MAX_VISIBLE_SPAN_RATIO = 3.35;
    /**
     * Max zoom-in (× fit scale). Wide binaries use a huge {@link #layoutSpanX}/{@link #layoutSpanY}, so {@code scaleFit}
     * is tiny; {@link #computeMapPlotScale} adds a deep-zoom px/m floor so wheel zoom stays useful past this cap.
     */
    private static final double ZOOM_MAX = 262144.0;
    /**
     * Deep zoom: at {@link #ZOOM_DEEP_MAG_START}, aim for about this many light-seconds across the smaller plot axis;
     * scales down toward {@link #ZOOM_DEEP_MIN_VISIBLE_LIGHT_SECONDS} as {@link #zoomFactor} increases.
     */
    private static final double ZOOM_DEEP_REF_VISIBLE_LIGHT_SECONDS = 64.0;
    /** Smallest plot axis span at {@link #ZOOM_MAX} (~2 Ls across min dimension — planet/moon separation in a cluster). */
    private static final double ZOOM_DEEP_MIN_VISIBLE_LIGHT_SECONDS = 2.0;
    /** ~10% zoom change per mouse-wheel notch ({@link MouseWheelEvent#getWheelRotation}). */
    private static final double ZOOM_PER_NOTCH = 1.1;

    /** Minimum segments when zoomed out (legacy fallback if scale is unknown). */
    private static final int ORBIT_SEGMENTS_MIN = 72;
    /** Legacy ceiling when {@code scalePixelsPerMetre} is not used; actual per-orbit count can reach {@link SystemOrbitGeometry#ORBIT_POLYLINE_SEGMENTS_HARD_MAX}. */
    private static final int ORBIT_SEGMENTS_MAX = 768;

    /** Zoom (× fit scale) before moon designations ({@code 1 a}) are drawn; major / parent bodies always labeled. */
    private static final double ZOOM_SHOW_MOON_LABELS = 8.0;
    /** Past this zoom (× fit), every body label including moons is shown; below it only revolution centers + stars. */
    private static final double ZOOM_SHOW_ALL_BODY_LABELS = 14.0;
    /** Deep-zoom px/m floor begins at the same threshold as moon labels. */
    private static final double ZOOM_DEEP_MAG_START = ZOOM_SHOW_MOON_LABELS;

    /**
     * While the smaller plot axis spans more than this many light-seconds, a subsystem hub (star with planets, giant
     * with moons, etc.) keeps its own label but descendants’ labels are hidden so clustered text does not overlap.
     */
    private static final double SUBSYSTEM_CLUSTER_DETAIL_VISIBLE_LS = 96.0;
    /** Minimum screen px for companion-branch lump clustering (also scaled with plot width). */
    private static final float COMPANION_REVOLUTION_LUMP_SCREEN_PX_MIN = 44f;
    /** Moon designations ({@code 3 a}) need a closer view than major bodies in the same cluster. */
    private static final double SUBSYSTEM_MOON_LABEL_VISIBLE_LS = 40.0;
    /** Extra screen px to push a lumped cluster hub label outside the body blob. */
    private static final float SUBSYSTEM_LUMP_HUB_LABEL_GAP_PX = 26f;

    /** Trailing stellar branch letter on full body names ({@code … B}, not {@code B 3}). */
    private static final Pattern STAR_BRANCH_LETTER_TAIL = Pattern.compile(" ([A-Za-z])\\s*$");
    private static final Pattern TRAILING_STAR_BODY_DESIGNATION = Pattern
            .compile("([A-Za-z]+)\\s+(\\d+)(?:\\s+([a-z]+))?\\s*$");

    /**
     * Legacy alias for {@link #ZOOM_SHOW_MOON_LABELS} (subsystem hub “detail” zoom); kept for config parity with
     * centre-lock / moon-label thresholds.
     */
    private static final double ZOOM_SUBSYSTEM_HUB_DETAIL = ZOOM_SHOW_MOON_LABELS;

    /**
     * When zoom is at least this (× fit scale), keep a subsystem orbit-parent at the view centre while bodies move.
     * Matches {@link #ZOOM_SHOW_MOON_LABELS} so follow engages when moon detail is in use.
     */
    private static final double ZOOM_SUBSYSTEM_CENTER_LOCK = ZOOM_SHOW_MOON_LABELS;

    /**
     * Past this zoom (× fit scale), journal giants ({@code PlanetClass} contains {@code giant}) draw with 2× the normal
     * body dot radius (FSS-style scale cue when the map is moderately zoomed in).
     */
    private static final double ZOOM_MAP_GIANT_BODY_DOT = 5.0;
    /**
     * Ringed bodies that are not a subsystem hub, sole orbit cluster, or orbit-revolution centre: planetary ring art
     * only appears from this zoom (× fit) upward so distant moons stay uncluttered when zoomed out.
     */
    private static final double ZOOM_MAP_BODY_RINGS = 6.25;

    /** Outer stroke for schematic planetary rings (behind the body dot; above subsystem hub icon rings). */
    private static final Color MAP_PLANETARY_RING_OUTER = new Color(255, 45, 45, 245);
    /** Inner stroke for schematic planetary rings. */
    private static final Color MAP_PLANETARY_RING_INNER = new Color(255, 95, 95, 230);
    /** Earth-like and water-family worlds — saturated FSS blue (same on map as in-game scanner dot). */
    private static final Color MAP_FSS_HABITABLE_BLUE_DOT = new Color(0, 0, 255);
    /** Map star core radius vs {@link #mapBodyDotRadiusPx} base (branch stars still use {@code ×1.85} after this). */
    private static final float MAP_STAR_DOT_RADIUS_SCALE = 0.5f;

    /** Default schematic planet/moon fill — warm brown, distinct from habitables and UI chrome. */
    private static final Color MAP_PLANET_DEFAULT_DOT = new Color(154, 96, 44);
    /** Summary-cluster marker when zoomed out (one small dot at the label centroid). */
    private static final Color MAP_SUMMARY_CLUSTER_DOT = new Color(142, 90, 40);
    /** Max pointer travel (px) between press and release to count as a click (not pan/measure drag). */
    private static final int MAP_CLICK_DRAG_THRESHOLD_PX = 5;
    /** Extra px around body dots for hit testing (matches visible fill). */
    private static final float MAP_HIT_BODY_PAD_PX = 2f;
    /** Point-to-segment tolerance for orbit stroke hits (px). */
    private static final float MAP_HIT_ORBIT_STROKE_PX = 5f;
    /** Pad around label anchor rects for click hits. */
    private static final float MAP_HIT_LABEL_PAD_PX = 2f;

    /** Prefix glyph on commander labels / detached marker — fill colour. */
    private static final Color MAP_COMMANDER_TRIANGLE_FILL = new Color(255, 235, 75);
    /** Prefix glyph — red outline (drawn as offset passes around the triangle). */
    private static final Color MAP_COMMANDER_TRIANGLE_OUTLINE = new Color(195, 25, 25);
    /** Yellow ▲ (red outline) before commander body name — gap to text is {@link #MAP_COMMANDER_TRIANGLE_NAME_GAP_PX}. */
    private static final String MAP_COMMANDER_TRIANGLE_CHAR = "\u25B2";
    /** Pixels from triangle’s right edge to the start of the body short name. */
    private static final int MAP_COMMANDER_TRIANGLE_NAME_GAP_PX = 1;

    /** Pixels outside the body dot before the inner blue subsystem-hub ring. */
    private static final float MAP_SUBSYSTEM_HUB_RING_INNER_PAD_PX = 1.35f;
    /** Baseline extra pixels from inner ring radius to outer ring. */
    private static final float MAP_SUBSYSTEM_HUB_RING_OUTER_STEP_PX = 6.0f;
    /**
     * When the inner hub ring is small (far zoom), enforce at least this much radius delta before the outer ring so
     * two strokes do not read as one. Scales down as {@code innerR} grows (see {@link #subsystemMoonHubRingOuterRadiusPx}).
     */
    private static final float MAP_SUBSYSTEM_HUB_RING_MIN_OUTER_GAP_PX = 9.75f;

    /**
     * When the plotted body id set is unchanged but the layout bbox grows by more than this fraction, scale
     * {@link #zoomFactor} down proportionally so the map does not stay stuck over-zoomed while scans stream in.
     */
    private static final double LAYOUT_GROWTH_ZOOM_OUT_RATIO = 0.06;

    /**
     * Wide-binary fallback: when {@link #capRobBlendForLayoutBlend} applies, core width is capped to
     * {@code fitSpan / this}. Larger divisor → tighter default layout (higher px/m at zoom 1). {@code 5×(fit/div)}
     * must stay below {@code fit} (needs div {@code >5}); raise toward ~48 so blended layout can approach ~{@code fit/10}.
     */
    private static final double LAYOUT_BLEND_CORE_CAP_DIVISOR = 48.0;
    /**
     * After non-star hull tightening, layout span is floored to at least this fraction of the fitted span and (when
     * multiple stars project apart) of the projected star hull — otherwise the centroid can sit between stars while the
     * fitted window is planet-scale and the map looks empty.
     */
    private static final double RNS_TIGHTEN_MIN_FRAC_OF_FIT = 0.055;
    private static final double RNS_TIGHTEN_MIN_FRAC_OF_STAR_HULL = 0.42;
    /**
     * Floor span also uses this fraction of the all-body projected hull so companions mis-tagged as non-stars still
     * contribute separation (star-only hull would otherwise be ~0 and the map stays empty).
     */
    private static final double RNS_TIGHTEN_MIN_FRAC_OF_ALL_BODY_HULL = 0.11;
    /**
     * {@link #layoutSpanX}/{@link #layoutSpanY} must be at least this × the projected min–max span of <em>all</em> body
     * dots, or scale is computed for a window smaller than where dots actually lie (see debug H12: all dots off-screen
     * while {@code layoutSpanY} ≪ hull span).
     */
    private static final double LAYOUT_MIN_OVER_ALL_DOT_HULL = 1.06;
    /** Layout span must cover schematic orbit strokes, not only body dot centres (often ~2× parent–child distance). */
    private static final double LAYOUT_MIN_OVER_ORBIT_HULL = 1.10;

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
     * Zoom-out wheel (below {@link #ZOOM_SUBSYSTEM_CENTER_LOCK}): blend toward system centroid — min fraction per notch
     * just under the subsystem threshold (gentle).
     */
    private static final double WHEEL_SYSTEM_OUT_NUDGE_BLEND_MIN = 0.06;
    /** Zoom-out wheel at {@link #zoomMinFit}: max fraction per notch toward system centroid (strong recentre). */
    private static final double WHEEL_SYSTEM_OUT_NUDGE_BLEND_MAX = 0.88;
    /** Curves how zoom-out nudge ramps from min to max as zoom approaches {@link #zoomMinFit}. */
    private static final double WHEEL_SYSTEM_OUT_NUDGE_GAMMA = 1.42;
    /** Exponential boost for zoom-out centroid nudge: larger = stronger pull when {@link #zoomFactor} is near {@link #zoomMinFit}. */
    private static final double WHEEL_SYSTEM_OUT_EXPO_K = 4.2;
    /** Scales the exponential boost (keeps blend ≤ {@link #WHEEL_SYSTEM_OUT_EXPO_BLEND_MAX}). */
    private static final double WHEEL_SYSTEM_OUT_EXPO_GAIN = 0.55;
    /** Upper cap on centroid blend after exponential boost (per wheel notch). */
    private static final double WHEEL_SYSTEM_OUT_EXPO_BLEND_MAX = 0.97;
    /** Snap view to system centroid after a zoom-out nudge when within this screen error (px). */
    private static final double WHEEL_SYSTEM_CENTROID_SNAP_ERR_PX = 1.2;
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

    private final List<BodyDot> dots = new ArrayList<>();
    private int exobiologyLeafIconPx = -1;
    private Icon exobiologyLeafIcon = new LeafIcon(22, 22);
    private List<OrbitPolylineWorldXY> orbitLines = Collections.emptyList();
    /** Last sources passed to {@link SystemOrbitGeometry#orbitPolylinesWorldMetresXY}; rebuilt when zoom changes. */
    private Map<Integer, BodyInfo> orbitGeomBodies;
    private Map<Integer, double[]> orbitGeomPositions;
    /** Last {@link #orbitRebuildCacheKey()} used for a non-forced orbit rebuild (panel size / zoom / scale). */
    private long lastOrbitRebuildKey = Long.MIN_VALUE;
    /** Sorted orbiting-body ids for {@link #orbitLines}; used to log once per distinct ring set. */
    private int[] lastLoggedOrbitPolyBodyIds = new int[0];
    /** One-shot {@code [EDO][OrbitMap][Draw]} trace after the first schematic hub revolution ring stroke. */
    private boolean loggedHubRevolutionDrawTrace;
    /** Journal ids of bodies that have at least one moon (subsystem hubs); used for wide-zoom lump markers. */
    private Set<Integer> subsystemHubLumpBodyIds = Collections.emptySet();
    private boolean sceneEmpty = true;
    private boolean shipKnown;
    private double shipWx;
    private double shipWy;
    /** Last commander position in world metres from {@link #setScene}; used when view tilt changes. */
    private double[] commanderPositionMetres;
    /**
     * Commander reference body id (landed / surface body from the System tab). Shown with a yellow ▲ on the map
     * label (no cyan ring — triangle is the commander cue).
     */
    private Integer anchorBodyId;
    /**
     * Proximity / orbit UI highlight (e.g. body near ship for subsystem context). When not the same as
     * {@link #anchorBodyId}, the map draws a cyan ellipse around that body’s marker.
     */
    private Integer highlightNearBodyId;

    /** When true, schematic orbit playback is running (subsystem hop skipped; view centre snaps to follow hub). */
    private boolean orbitSchematicPlaybackActive;
    /** Sim epoch when schematic playback started (T+ base); cleared when playback stops. */
    private Instant orbitPlaybackBaseEpoch;
    /** Latest sim epoch from the System tab fast-forward timer. */
    private Instant orbitPlaybackEpoch;

    /**
     * When not {@code -1}, subsystem zoom-lock is active for this hub id; after a wheel nudge the hub still uses the
     * normal transform until within {@link #VIEW_CENTER_HUB_PIN_SCREEN_PX} px, then its marker + label are pinned to
     * the plot pixel centre. During schematic playback the hub is always snapped and pinned.
     */
    private int subsystemScreenLockHubId = -1;

    /** Multiplier on the automatic fit-all scale ({@code 1} = fit bounds). Clamped to [{@link #zoomMinFit}, {@link #ZOOM_MAX}]. */
    private double zoomFactor = 1.0;
    /**
     * World span (metres) used for {@code scaleFit} in paint and wheel zoom — set only in {@link #setScene}, not
     * while {@link #tryApplyPositionUpdate} moves bodies, so fast-forward animation does not “breathe” the zoom.
     */
    private double layoutSpanX = 1.0;
    private double layoutSpanY = 1.0;
    /**
     * Minimum {@link #zoomFactor} for the current scene and plot size; recomputed from layout spans and
     * {@link #ZOOM_MIN_MAX_VISIBLE_SPAN_RATIO} (see {@link #updateZoomMinFit}).
     */
    private double zoomMinFit = ZOOM_MIN_ABSOLUTE_FLOOR;
    /** World metres (X/Y) at the centre of the plot; updated when the scene loads and when zooming. */
    private double viewCenterWx;
    private double viewCenterWy;
    /**
     * Which world axes (0=x,1=y,2=z) feed the schematic horizontal / vertical map coordinates. Picked per scene so
     * systems that collapse along Z in raw X/Y still spread on screen; orbit polylines use the same pair.
     */
    private int mapProjA0 = 0;
    private int mapProjA1 = 1;

    /** Wide-binary flatten chord captured at {@link #setScene}; reused during schematic playback ticks. */
    private SystemOrbitGeometry.WideBinaryFlattenFrame wideBinaryFlattenFrame;
    /**
     * Rebuild {@link SystemMapPipeline} once when schematic playback starts so {@link #mapModel} matches the play
     * epoch and {@code freezeBarycentreStars}; cleared on {@link #setScene} / pause.
     */
    private boolean playbackPipelinePrimed;

    /**
     * Schematic topology + layout from {@link SystemMapPipeline}; parent links, hubs, and map-plane positions must
     * come from here — not re-derived in paint code.
     */
    private SystemMapModel mapModel;

    /** Schematic (default) vs journal Kepler positions at true scale. */
    private MapScaleMode mapScaleMode = MapScaleMode.SCHEMATIC;

    /** True-scale view tilt 0…90° ({@link MapViewProjection}); ignored in schematic mode. */
    private int viewTiltDegrees;

    /** When body IDs change (new system / new scan), reset pan & zoom; otherwise keep user zoom across telemetry refreshes. */
    private int[] lastSceneBodyIdsSnapshot = new int[0];
    /**
     * Last {@link #layoutSpanX}/{@link #layoutSpanY} after {@link #setScene} — used to ease zoom out when the same
     * body id set’s bounding box grows (scans filling in, new bodies, etc.) without resetting pan.
     */
    private double lastSceneLayoutSpanX = 1.0;
    private double lastSceneLayoutSpanY = 1.0;
    /** After at least one {@link #setScene} with a laid-out panel size, bbox-growth zoom easing may run. */
    private boolean lastLayoutZoomEaseReady;

    /** Next paint runs subsystem centre lock with pause-style centroid fallback (see {@link #syncViewCenterToSubsystemHubAfterOrbitPause}). */
    private boolean pendingSubsystemCenterPauseResync;

    /**
     * Last plot size when {@link #zoomEssentiallyAtMinFit()} — used so {@link #snapViewCenterToSystemCentroidWorld} runs
     * on resize at min zoom only, not every paint (which overwrote wheel-driven {@link #viewCenterWx}/{@link #viewCenterWy}).
     */
    private int lastMinZoomSnapPaintW = -1;
    private int lastMinZoomSnapPaintH = -1;

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

    /** Middle-button drag pans the schematic (wheel still zooms about the pointer). */
    private boolean mapPanDragActive;
    private int mapPanDragLastX;
    private int mapPanDragLastY;

    /**
     * Batched wheel notches flushed once per EDT pulse so rapid scrolling does not queue many expensive
     * {@link #applyWheelZoomAtComponent} + orbit rebuilds after the wheel has already stopped.
     */
    private int pendingWheelClicks;
    private int pendingWheelLocalX;
    private int pendingWheelLocalY;
    private boolean wheelZoomFlushScheduled;

    /** Right-button drag: line + distance label in Ls until release. */
    private boolean measureDragActive;

    /** Left-button press origin for click-vs-drag discrimination. */
    private int mapClickPressX = -1;
    private int mapClickPressY = -1;
    private int measureStartX;
    private int measureStartY;
    private int measureEndX;
    private int measureEndY;

    public MapScaleMode mapScaleMode() {
        return mapScaleMode;
    }

    /**
     * Wide-binary stars stay fixed only during schematic playback ({@code freezeBarycentreStars}); true-scale sim
     * advances them on the mutual barycentre ring.
     */
    private boolean freezeBarycentreStarsDuringPlayback() {
        return orbitSchematicPlaybackActive && !mapScaleMode.trueScale();
    }

    /**
     * Switches schematic vs true-scale layout; caller should refresh the scene (e.g. {@code refreshPlanMap}).
     */
    public void setMapScaleMode(MapScaleMode mode) {
        MapScaleMode next = mode != null ? mode : MapScaleMode.SCHEMATIC;
        if (next == mapScaleMode) {
            return;
        }
        mapScaleMode = next;
        OverlayPreferences.setSystemPlanMapScaleMode(next);
    }

    /** True-scale view tilt 0…90°; schematic mode ignores this. */
    public int viewTiltDegrees() {
        return viewTiltDegrees;
    }

    public void setViewTiltDegrees(int degrees) {
        setViewTiltDegrees(degrees, true);
    }

    /**
     * @param persistPrefs when false (slider drag), updates the view without writing preferences until release.
     */
    public void setViewTiltDegrees(int degrees, boolean persistPrefs) {
        int next = MapViewProjection.clampViewTiltDegrees(degrees);
        if (next == viewTiltDegrees) {
            return;
        }
        viewTiltDegrees = next;
        if (persistPrefs) {
            OverlayPreferences.setSystemPlanMapViewTiltDegrees(next);
        }
        applyViewTiltProjectionRefresh();
    }

    /**
     * Maps stored 3D world metres to the 2D view plane used for dots, orbits, pan, zoom, and hit tests.
     */
    private double[] mapViewCoordsFromWorldMetres(double[] positionMetres) {
        if (positionMetres == null || positionMetres.length < 2) {
            return new double[] { Double.NaN, Double.NaN };
        }
        if (!mapScaleMode.trueScale() || viewTiltDegrees <= 0) {
            return new double[] {
                    SystemOrbitGeometry.worldAxisMetres(positionMetres, mapProjA0),
                    SystemOrbitGeometry.worldAxisMetres(positionMetres, mapProjA1)
            };
        }
        return MapViewProjection.projectFromPositionMetres(positionMetres, mapProjA0, mapProjA1, viewTiltDegrees);
    }

    private void reprojectBodyDotsAndShipFromGeomPositions() {
        if (orbitGeomPositions == null) {
            return;
        }
        for (BodyDot d : dots) {
            double[] p = orbitGeomPositions.get(Integer.valueOf(d.bodyId));
            if (p == null) {
                continue;
            }
            double[] xy = mapViewCoordsFromWorldMetres(p);
            if (Double.isFinite(xy[0]) && Double.isFinite(xy[1])) {
                d.wx = xy[0];
                d.wy = xy[1];
            }
        }
        if (commanderPositionMetres != null) {
            double[] xy = mapViewCoordsFromWorldMetres(commanderPositionMetres);
            if (Double.isFinite(xy[0]) && Double.isFinite(xy[1])) {
                shipKnown = true;
                shipWx = xy[0];
                shipWy = xy[1];
            }
        }
    }

    private void applyViewTiltProjectionRefresh() {
        if (sceneEmpty) {
            repaint();
            return;
        }
        reprojectBodyDotsAndShipFromGeomPositions();
        lastOrbitRebuildKey = Long.MIN_VALUE;
        rebuildOrbitPolylines(true, !orbitSchematicPlaybackActive);
        if (mapScaleMode.trueScale()) {
            refreshTrueScaleLayoutSpansFromView();
        }
        repaint();
    }

    /** Recomputes {@link #layoutSpanX}/{@link #layoutSpanY} from tilted dots and orbit strokes. */
    private void refreshTrueScaleLayoutSpansFromView() {
        if (dots.isEmpty()) {
            return;
        }
        Bounds bb = computeBounds(dots,
                shipKnown ? shipWx : Double.NaN,
                shipKnown ? shipWy : Double.NaN);
        if (Double.isFinite(bb.minX) && Double.isFinite(bb.maxX)) {
            layoutSpanX = Math.max(1.0, layoutSpanAxisMetres(bb.minX, bb.maxX));
        }
        if (Double.isFinite(bb.minY) && Double.isFinite(bb.maxY)) {
            layoutSpanY = Math.max(1.0, layoutSpanAxisMetres(bb.minY, bb.maxY));
        }
        expandLayoutSpansForOrbitPolylines();
        int pw = getWidth();
        int ph = getHeight();
        if (pw > 0 && ph > 0) {
            int plotH = Math.max(88, ph - MAP_BOTTOM_INSET);
            double aw = pw - 2.0 * PAD;
            double ah = plotH - 2.0 * PAD;
            updateZoomMinFit(aw, ah);
        }
    }

    public SystemPlanMapPanel() {
        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, EdoUi.Internal.GRAY_180),
                new EmptyBorder(6, 0, 4, 0)));
        addMouseWheelListener(this::handleMouseWheel);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (OverlayPreferences.isOverlayMousePassThroughToGame()) {
                    return;
                }
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                    mapClickPressX = e.getX();
                    mapClickPressY = e.getY();
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    clearMeasureDrag();
                    measureDragActive = true;
                    measureStartX = e.getX();
                    measureStartY = e.getY();
                    measureEndX = measureStartX;
                    measureEndY = measureStartY;
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    e.consume();
                    repaint();
                    return;
                }
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    mapPanDragActive = true;
                    mapPanDragLastX = e.getX();
                    mapPanDragLastY = e.getY();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    e.consume();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (OverlayPreferences.isOverlayMousePassThroughToGame()) {
                    return;
                }
                if (SwingUtilities.isRightMouseButton(e) && measureDragActive) {
                    clearMeasureDrag();
                    setCursor(Cursor.getDefaultCursor());
                    e.consume();
                    repaint();
                    return;
                }
                if (SwingUtilities.isMiddleMouseButton(e) && mapPanDragActive) {
                    mapPanDragActive = false;
                    setCursor(Cursor.getDefaultCursor());
                    e.consume();
                    return;
                }
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1
                        && !mapPanDragActive && !measureDragActive) {
                    handleMapClickRelease(e);
                }
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (OverlayPreferences.isOverlayMousePassThroughToGame()) {
                    return;
                }
                if ((e.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) != 0 && measureDragActive) {
                    measureEndX = e.getX();
                    measureEndY = e.getY();
                    e.consume();
                    repaint();
                    return;
                }
                if ((e.getModifiersEx() & InputEvent.BUTTON2_DOWN_MASK) == 0) {
                    return;
                }
                if (!mapPanDragActive) {
                    return;
                }
                int x = e.getX();
                int y = e.getY();
                applyMapPanPixelDelta(x - mapPanDragLastX, y - mapPanDragLastY);
                mapPanDragLastX = x;
                mapPanDragLastY = y;
            }
        });
        System.out.println(
                "[EDO][OrbitMap] System plan map panel initialized; orbit ring bodies are listed when each system is first drawn. Map layout rev=v6-cluster-hull.");
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
        zoomMinFit = ZOOM_MIN_ABSOLUTE_FLOOR;
        viewCenterWx = 0.0;
        viewCenterWy = 0.0;
        mapProjA0 = 0;
        mapProjA1 = 1;
        lastSceneBodyIdsSnapshot = new int[0];
        lastSceneLayoutSpanX = 1.0;
        lastSceneLayoutSpanY = 1.0;
        lastLayoutZoomEaseReady = false;
        orbitLines = Collections.emptyList();
        subsystemHubLumpBodyIds = Collections.emptySet();
        orbitGeomBodies = null;
        orbitGeomPositions = null;
        wideBinaryFlattenFrame = null;
        playbackPipelinePrimed = false;
        lastOrbitRebuildKey = Long.MIN_VALUE;
        lastLoggedOrbitPolyBodyIds = new int[0];
        loggedHubRevolutionDrawTrace = false;
        pendingSubsystemCenterPauseResync = false;
        prevProximityHighlightBodyId = null;
        subsystemScreenLockHubId = -1;
        lastMinZoomSnapPaintW = -1;
        lastMinZoomSnapPaintH = -1;
        cancelSubsystemProximityHop();
        mapPanDragActive = false;
        clearMeasureDrag();
        resetPendingWheelZoom();
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    /**
     * Picks two distinct world axes for the schematic so the map is not stuck in X/Y when most separation is along Z
     * (common with journal inclinations / cache geometry).
     */
    private void chooseMapProjectionAxes(Map<Integer, BodyInfo> bodies, Map<Integer, double[]> positions) {
        mapProjA0 = 0;
        mapProjA1 = 1;
        if (bodies == null || positions == null) {
            return;
        }
        double mx = 0.0;
        double my = 0.0;
        double mz = 0.0;
        int n = 0;
        for (Integer k : bodies.keySet()) {
            if (k == null) {
                continue;
            }
            double[] p = positions.get(k);
            if (p == null || p.length < 2) {
                continue;
            }
            double x = SystemOrbitGeometry.worldAxisMetres(p, 0);
            double y = SystemOrbitGeometry.worldAxisMetres(p, 1);
            double z = p.length >= 3 ? SystemOrbitGeometry.worldAxisMetres(p, 2) : 0.0;
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                continue;
            }
            mx += x;
            my += y;
            mz += z;
            n++;
        }
        if (n < 3) {
            return;
        }
        mx /= n;
        my /= n;
        mz /= n;
        double vx = 0.0;
        double vy = 0.0;
        double vz = 0.0;
        for (Integer k : bodies.keySet()) {
            if (k == null) {
                continue;
            }
            double[] p = positions.get(k);
            if (p == null || p.length < 2) {
                continue;
            }
            double x = SystemOrbitGeometry.worldAxisMetres(p, 0);
            double y = SystemOrbitGeometry.worldAxisMetres(p, 1);
            double z = p.length >= 3 ? SystemOrbitGeometry.worldAxisMetres(p, 2) : 0.0;
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                continue;
            }
            double dx = x - mx;
            double dy = y - my;
            double dz = z - mz;
            vx += dx * dx;
            vy += dy * dy;
            vz += dz * dz;
        }
        vx /= n;
        vy /= n;
        vz /= n;
        double vmax = Math.max(1e-120, Math.max(vx, Math.max(vy, vz)));
        if (vz <= 0.02 * vmax && vx >= 0.02 * vmax && vy >= 0.02 * vmax) {
            mapProjA0 = 0;
            mapProjA1 = 1;
        } else if (vy <= 0.02 * vmax) {
            mapProjA0 = 0;
            mapProjA1 = 2;
        } else if (vx <= 0.02 * vmax) {
            mapProjA0 = 1;
            mapProjA1 = 2;
        } else {
            mapProjA0 = 0;
            mapProjA1 = 1;
        }
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
        setScene(bodies, positions, shipM, anchorBodyId, highlightNearBodyId, orbitSchematicPlaybackActive,
                Instant.now());
    }

    public void setScene(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> positions,
            double[] shipM,
            Integer anchorBodyId,
            Integer highlightNearBodyId,
            boolean orbitSchematicPlaybackActive,
            Instant orbitPositionEpoch) {

        dots.clear();
        this.anchorBodyId = anchorBodyId;
        this.highlightNearBodyId = highlightNearBodyId;
        this.orbitSchematicPlaybackActive = orbitSchematicPlaybackActive;
        syncOrbitPlaybackEpochTracking(orbitSchematicPlaybackActive, orbitPositionEpoch);
        sceneEmpty = bodies == null || bodies.isEmpty() || positions == null || positions.isEmpty();
        mapModel = null;
        playbackPipelinePrimed = false;

        if (!MAP_AUTO_VIEW_PAN) {
            cancelSubsystemProximityHop();
        }

        if (!sceneEmpty) {
            String mapSystemName = resolveMapSystemName(bodies);
            mapModel = SystemMapPipeline.build(mapSystemName, bodies, orbitPositionEpoch,
                    freezeBarycentreStarsDuringPlayback(), mapScaleMode);
            positions = new java.util.HashMap<>(mapModel.positionsMetres());
            mapProjA0 = mapModel.projectionAxis0();
            mapProjA1 = mapModel.projectionAxis1();
            wideBinaryFlattenFrame = mapModel.wideBinaryFlattenFrame();
            orbitLines = mapModel.orbitPolylines();
            lastOrbitRebuildKey = orbitRebuildCacheKey();
            playbackPipelinePrimed = orbitSchematicPlaybackActive;
            SystemMapClassification clf = mapModel.classification();
            int primaryAnch = clf.primaryAnchorBodyId() >= 0 ? clf.primaryAnchorBodyId()
                    : SystemOrbitGeometry.primaryAnchorBodyMapKey(bodies);
            boolean loneStarLayout = clf.layoutKind() == org.dce.ed.systemmap.SystemLayoutKind.SINGLE_STAR_SCHEMATIC;
            boolean wideBinaryMap = clf.wideBinary();
            int centralStarId = loneStarLayout && clf.schematicCentralStarId() >= 0
                    ? clf.schematicCentralStarId()
                    : SystemOrbitGeometry.schematicCentralStarMapKey(bodies);
            for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                double[] p = positions.get(e.getKey());
                if (p == null || p.length < 2) {
                    continue;
                }
                int needLen = Math.max(mapProjA0, mapProjA1) + 1;
                if (p.length < needLen) {
                    continue;
                }
                double[] xy = mapViewCoordsFromWorldMetres(p);
                double x = xy[0];
                double y = xy[1];
                if (!Double.isFinite(x) || !Double.isFinite(y)) {
                    continue;
                }
                BodyInfo b = e.getValue();
                if (b.isScanBarycentreRow()) {
                    continue;
                }
                int mapKey = e.getKey().intValue();
                boolean mapStellar = SystemMapRules.isMapStellarBody(b);
                /* Asterisk matches System tab: primary name = system name (FSS may omit starType). Not on wide binaries. */
                boolean primaryStarAsterisk = !wideBinaryMap
                        && (SystemOrbitGeometry.isPrimaryStarBodyByName(b)
                                || (loneStarLayout && centralStarId >= 0 && mapKey == centralStarId));
                boolean loneCentralPrimary = loneStarLayout && centralStarId >= 0 && mapKey == centralStarId;
                boolean star = mapStellar;
                String label;
                if (primaryStarAsterisk) {
                    label = "*";
                } else if (mapStellar) {
                    label = starMapLabel(b, mapKey, primaryAnch);
                } else {
                    label = planetMapLabel(b, mapKey);
                }
                boolean moon = !star && !isPrimaryStarBody(b)
                        && SystemOrbitGeometry.isMoonSatelliteBody(b, bodies);
                boolean giant = isGiantPlanetBody(b, star);
                boolean rings = hasPlanetaryRingsForMap(b, star);
                boolean soleOrbitCluster = !star && mapModel.directChildCount(mapKey) == 0;
                boolean hasExobiology = !star && !primaryStarAsterisk
                        && mapBodyHasOwnExobiology(mapKey);
                boolean estimatedPosition = SystemOrbitGeometry.bodyLayoutDataIsEstimated(b, mapKey, bodies);
                dots.add(new BodyDot(mapKey, x, y, label, star, primaryStarAsterisk, loneCentralPrimary, moon,
                        giant, rings, soleOrbitCluster, hasExobiology, estimatedPosition));
            }
            dots.sort(Comparator.comparingInt((BodyDot d) -> d.star ? 1 : 0).thenComparingInt(d -> d.bodyId));
        }

        if (shipM != null && shipM.length >= 2) {
            commanderPositionMetres = shipM;
            double[] sxy = mapViewCoordsFromWorldMetres(shipM);
            if (Double.isFinite(sxy[0]) && Double.isFinite(sxy[1])) {
                shipKnown = true;
                shipWx = sxy[0];
                shipWy = sxy[1];
            } else {
                shipKnown = false;
                shipWx = 0;
                shipWy = 0;
            }
        } else {
            commanderPositionMetres = null;
            shipKnown = false;
            shipWx = 0;
            shipWy = 0;
        }

        if (!sceneEmpty && bodies != null && positions != null) {
            orbitGeomBodies = bodies;
            orbitGeomPositions = positions;
            subsystemHubLumpBodyIds = mapModel.subsystemHubBodyIds();
        } else {
            orbitGeomBodies = null;
            orbitGeomPositions = null;
            subsystemHubLumpBodyIds = Collections.emptySet();
            orbitLines = Collections.emptyList();
            lastOrbitRebuildKey = Long.MIN_VALUE;
        }

        int[] sceneIdsSorted = null;
        boolean sameBodyIdSet = false;
        if (!sceneEmpty && !dots.isEmpty()) {
            Bounds bb = computeBounds(dots,
                    shipKnown ? shipWx : Double.NaN,
                    shipKnown ? shipWy : Double.NaN);
            Bounds bbDots = computeBoundsDotsOnly(dots);
            double[] spans = layoutFitSpansAndRobust(dots, bb, bbDots);
            double fitSpanX = spans[0];
            double fitSpanY = spans[1];
            double robRawX = spans[2];
            double robRawY = spans[3];
            double robBlendX = spans[4];
            double robBlendY = spans[5];
            MapLayoutSpanPick layoutPick;
            if (mapScaleMode.trueScale()) {
                layoutSpanX = Math.max(1.0, layoutSpanAxisMetres(bb.minX, bb.maxX));
                layoutSpanY = Math.max(1.0, layoutSpanAxisMetres(bb.minY, bb.maxY));
                layoutPick = new MapLayoutSpanPick(layoutSpanX, layoutSpanY, false, 0.0, 0.0);
            } else {
                layoutPick = pickMapLayoutSpans(dots, fitSpanX, fitSpanY, robBlendX, robBlendY);
                layoutSpanX = layoutPick.layoutSpanX();
                layoutSpanY = layoutPick.layoutSpanY();
            }
            double dotHullX = layoutHullSpanMetresPreferCluster(dots, true);
            double dotHullY = layoutHullSpanMetresPreferCluster(dots, false);
            boolean hullSpanRaised = false;
            if (dotHullX > 1.0) {
                double needX = dotHullX * LAYOUT_MIN_OVER_ALL_DOT_HULL;
                if (needX > layoutSpanX * 1.0000001) {
                    hullSpanRaised = true;
                }
                layoutSpanX = Math.max(layoutSpanX, needX);
            }
            if (dotHullY > 1.0) {
                double needY = dotHullY * LAYOUT_MIN_OVER_ALL_DOT_HULL;
                if (needY > layoutSpanY * 1.0000001) {
                    hullSpanRaised = true;
                }
                layoutSpanY = Math.max(layoutSpanY, needY);
            }
            sceneIdsSorted = new int[dots.size()];
            for (int i = 0; i < dots.size(); i++) {
                sceneIdsSorted[i] = dots.get(i).bodyId;
            }
            Arrays.sort(sceneIdsSorted);
            sameBodyIdSet = Arrays.equals(sceneIdsSorted, lastSceneBodyIdsSnapshot);
            if (!sameBodyIdSet) {
                cancelSubsystemProximityHop();
                lastSceneBodyIdsSnapshot = Arrays.copyOf(sceneIdsSorted, sceneIdsSorted.length);
                mapPanDragActive = false;
                measureDragActive = false;
                /* New bodies (FSS discovery, etc.): fit-all zoom and centre even when manual pan mode is on. */
                zoomFactor = 1.0;
                prevProximityHighlightBodyId = null;
                lastLayoutZoomEaseReady = false;
                if (hullSpanRaised) {
                    Bounds bbD = computeBoundsDotsOnly(dots);
                    if (Double.isFinite(bbD.minX) && Double.isFinite(bbD.maxX) && Double.isFinite(bbD.minY)
                            && Double.isFinite(bbD.maxY) && bbD.minX <= bbD.maxX && bbD.minY <= bbD.maxY) {
                        viewCenterWx = (bbD.minX + bbD.maxX) * 0.5;
                        viewCenterWy = (bbD.minY + bbD.maxY) * 0.5;
                    }
                } else {
                    applyClusterAwareViewCenter(bb, fitSpanX, fitSpanY, robRawX, robRawY, robBlendX, robBlendY,
                            spans[6], spans[7], dots);
                    /*
                     * When the hull tighten + floor still leaves a planet-scale window (not floored back to binary scale),
                     * the all-body robust centre can sit in empty space — use the non-star mean. When floored, cluster centre
                     * matches the wider span. {@link #snapViewCenterToSystemCentroidWorld} applies the same rule.
                     */
                    if (layoutPick.useNonStarCentroid()) {
                        double[] nsMean = dotArithmeticMeanWorldXYNonStars(dots);
                        if (Double.isFinite(nsMean[0]) && Double.isFinite(nsMean[1])) {
                            viewCenterWx = nsMean[0];
                            viewCenterWy = nsMean[1];
                        }
                    }
                }
                if (mapModel != null && mapModel.classification().wideBinary()) {
                    double[] bary = wideBinaryStellarCentroidWorldXY(dots);
                    if (Double.isFinite(bary[0]) && Double.isFinite(bary[1])) {
                        viewCenterWx = bary[0];
                        viewCenterWy = bary[1];
                    }
                }
            } else if (hullSpanRaised && MAP_AUTO_VIEW_PAN) {
                Bounds bbD = computeBoundsDotsOnly(dots);
                if (Double.isFinite(bbD.minX) && Double.isFinite(bbD.maxX) && Double.isFinite(bbD.minY)
                        && Double.isFinite(bbD.maxY) && bbD.minX <= bbD.maxX && bbD.minY <= bbD.maxY) {
                    viewCenterWx = (bbD.minX + bbD.maxX) * 0.5;
                    viewCenterWy = (bbD.minY + bbD.maxY) * 0.5;
                }
            }
        } else if (sceneEmpty || dots.isEmpty()) {
            layoutSpanX = 1.0;
            layoutSpanY = 1.0;
            zoomMinFit = ZOOM_MIN_ABSOLUTE_FLOOR;
        }

        rebuildOrbitPolylines(true, true);
        expandLayoutSpansForOrbitPolylines();
        if (!sceneEmpty && !dots.isEmpty() && bodies != null) {
            if (MAP_AUTO_VIEW_PAN) {
                maybeStartSubsystemProximityHop(highlightNearBodyId, bodies, orbitSchematicPlaybackActive);
            }
            prevProximityHighlightBodyId = highlightNearBodyId;
        } else {
            prevProximityHighlightBodyId = null;
        }
        int pw = getWidth();
        int ph = getHeight();
        if (pw > 0 && ph > 0 && !sceneEmpty && !dots.isEmpty()) {
            int plotHScene = Math.max(88, ph - MAP_BOTTOM_INSET);
            double aw = pw - 2.0 * PAD;
            double ah = plotHScene - 2.0 * PAD;
            inflateLayoutSpansUntilZoomMinFitAtMostOne(aw, ah);
            if (MAP_AUTO_VIEW_PAN && lastLayoutZoomEaseReady && sameBodyIdSet && sceneIdsSorted != null
                    && sceneIdsSorted.length > 0) {
                double prevM = Math.max(lastSceneLayoutSpanX, lastSceneLayoutSpanY);
                double nextM = Math.max(layoutSpanX, layoutSpanY);
                if (prevM >= 1.0 && nextM > prevM * (1.0 + LAYOUT_GROWTH_ZOOM_OUT_RATIO)) {
                    zoomFactor *= prevM / nextM;
                }
            }
            zoomFactor = clamp(zoomFactor, zoomMinFit, ZOOM_MAX);
            lastSceneLayoutSpanX = layoutSpanX;
            lastSceneLayoutSpanY = layoutSpanY;
            lastLayoutZoomEaseReady = true;
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
            int pId = ch != null ? mapResolvedParent(pl.bodyId) : -1;
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

        int frameHub = approachFrameHubId(bodyId, orbitGeomBodies, mapResolvedParents());
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
    private static int approachFrameHubId(int bodyId, Map<Integer, BodyInfo> bodies,
            Map<Integer, Integer> resolvedParents) {
        if (bodies == null || bodies.isEmpty() || bodyId < 0 || !bodies.containsKey(Integer.valueOf(bodyId))) {
            return -1;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (resolvedParentFromMap(e.getKey().intValue(), resolvedParents) == bodyId) {
                return bodyId;
            }
        }
        return subsystemFocusKeyForBody(bodyId, bodies, resolvedParents);
    }

    private static boolean orbitSubtreeContainsHub(int bodyId, int hubId, Map<Integer, BodyInfo> bodies,
            Map<Integer, Integer> resolvedParents) {
        int root = schematicRootBodyId(bodies, resolvedParents);
        int cur = bodyId;
        for (int g = 0; g < 64 && cur >= 0; g++) {
            if (cur == hubId) {
                return true;
            }
            if (cur == root) {
                return hubId == root;
            }
            int p = resolvedParentFromMap(cur, resolvedParents);
            if (p < 0 || p == cur) {
                break;
            }
            cur = p;
        }
        return false;
    }

    private static Set<Integer> membersUnderApproachHub(int hubId, Map<Integer, BodyInfo> bodies,
            Map<Integer, Integer> resolvedParents) {
        Set<Integer> out = new HashSet<>();
        if (bodies == null || hubId < 0) {
            return out;
        }
        for (Integer k : bodies.keySet()) {
            if (k == null) {
                continue;
            }
            int id = k.intValue();
            if (orbitSubtreeContainsHub(id, hubId, bodies, resolvedParents)) {
                out.add(k);
            }
        }
        return out;
    }

    /**
     * Orbit rings that belong in the “local” frame: moons of {@code hubId}, or direct stellar orbits when {@code hubId}
     * is {@code 0}. Omits the hub’s own orbit around the star so a gas-giant approach does not zoom to system scale.
     */
    private static boolean includeOrbitPolyForApproachFrame(int orbitingBodyId, int hubId, Map<Integer, BodyInfo> bodies,
            Map<Integer, Integer> resolvedParents) {
        if (bodies == null || orbitingBodyId == hubId) {
            return false;
        }
        int root = schematicRootBodyId(bodies, resolvedParents);
        int p = resolvedParentFromMap(orbitingBodyId, resolvedParents);
        if (hubId == root) {
            return p == root;
        }
        return p == hubId;
    }

    /**
     * Centre and zoom so the approached subsystem (hub + descendants + local orbit strokes) fits the plot.
     *
     * @return {@code [focusWx, focusWy, targetZoom]}
     */
    private double[] computeApproachSubsystemFrame(int bodyId, int frameHub, double[] approachedXY) {
        int rootId = orbitGeomBodies != null ? SystemOrbitGeometry.primaryAnchorBodyMapKey(orbitGeomBodies) : 0;
        double fallbackZ = (bodyId == rootId) ? SUBSYSTEM_HOP_TARGET_ZOOM_STAR : SUBSYSTEM_HOP_TARGET_ZOOM_MOON_CLUSTER;
        fallbackZ = clamp(fallbackZ, zoomMinFit, ZOOM_MAX);
        if (orbitGeomBodies == null || orbitGeomPositions == null || frameHub < 0 || approachedXY == null) {
            return new double[] { approachedXY[0], approachedXY[1], fallbackZ };
        }

        int w = getWidth();
        int h = getHeight();
        int plotH = Math.max(88, h - MAP_BOTTOM_INSET);
        double availW = w - 2.0 * PAD;
        double availH = plotH - 2.0 * PAD;
        if (availW < 32.0 || availH < 32.0
                || !Double.isFinite(layoutSpanX) || !Double.isFinite(layoutSpanY)
                || layoutSpanX <= 0.0 || layoutSpanY <= 0.0) {
            return new double[] { approachedXY[0], approachedXY[1], fallbackZ };
        }

        Set<Integer> members = membersUnderApproachHub(frameHub, orbitGeomBodies, mapResolvedParents());
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
        List<OrbitPolylineWorldXY> polys = mapModel != null
                ? SystemMapPipeline.rebuildOrbitPolylines(mapModel, orbitGeomPositions, ORBIT_SEGMENTS_MAX, scalePxPerM)
                : Collections.emptyList();
        Map<Integer, Integer> resolvedParents = mapResolvedParents();
        for (OrbitPolylineWorldXY pl : polys) {
            if (pl == null || pl.wx == null || pl.wy == null || pl.wx.length != pl.wy.length) {
                continue;
            }
            if (!includeOrbitPolyForApproachFrame(pl.bodyId, frameHub, orbitGeomBodies, resolvedParents)) {
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
        zoomFit = clamp(zoomFit, zoomMinFit, ZOOM_MAX);
        return new double[] { mx, my, zoomFit };
    }

    private static double layoutScaleFit(double availW, double availH, double spanX, double spanY) {
        if (availW < 32.0 || availH < 32.0
                || !Double.isFinite(spanX) || !Double.isFinite(spanY)
                || spanX <= 0.0 || spanY <= 0.0) {
            return Double.NaN;
        }
        double scaleFit = Math.min(availW / spanX, availH / spanY);
        return Double.isFinite(scaleFit) && scaleFit > 0.0 ? scaleFit : Double.NaN;
    }

    /**
     * Plot pixels per world metre. Wide binaries inflate {@link #layoutSpanX}/{@link #layoutSpanY}, so
     * {@code scaleFit * zoomFactor} barely changes until {@link #zoomFactor} is enormous. Interpolate visible
     * light-seconds log-linearly from the zoom-out floor ({@link #zoomMinFit}) to
     * {@link #ZOOM_DEEP_MIN_VISIBLE_LIGHT_SECONDS} at {@link #ZOOM_MAX}, and use whichever of that curve and the
     * layout fit path gives stronger magnification.
     */
    private double computeMapPlotScale(double availW, double availH, double spanX, double spanY) {
        double scaleFit = layoutScaleFit(availW, availH, spanX, spanY);
        if (!Double.isFinite(scaleFit)) {
            return Double.NaN;
        }
        double base = scaleFit * zoomFactor;
        if (mapScaleMode.trueScale()) {
            return base;
        }
        double minPlot = Math.min(availW, availH);
        if (!Double.isFinite(minPlot) || minPlot <= 0.0) {
            return base;
        }
        double spanLs = Math.min(spanX, spanY) / SystemOrbitGeometry.LIGHT_SECOND_METRES;
        if (!Double.isFinite(spanLs) || spanLs <= 0.0) {
            return base;
        }
        double minZ = Math.max(zoomMinFit, ZOOM_MIN_ABSOLUTE_FLOOR);
        double maxZ = ZOOM_MAX;
        if (maxZ <= minZ * (1.0 + 1e-9)) {
            return base;
        }
        double z = clamp(zoomFactor, minZ, maxZ);
        double visibleAtMinLs = spanLs * ZOOM_MIN_MAX_VISIBLE_SPAN_RATIO / minZ;
        if (!Double.isFinite(visibleAtMinLs) || visibleAtMinLs <= ZOOM_DEEP_MIN_VISIBLE_LIGHT_SECONDS) {
            return base;
        }
        double logT = Math.log(z / minZ) / Math.log(maxZ / minZ);
        logT = clamp(logT, 0.0, 1.0);
        double targetLs = visibleAtMinLs * Math.pow(ZOOM_DEEP_MIN_VISIBLE_LIGHT_SECONDS / visibleAtMinLs, logT);
        double deepScale = minPlot / (targetLs * SystemOrbitGeometry.LIGHT_SECOND_METRES);
        if (!Double.isFinite(deepScale) || deepScale <= 0.0) {
            return base;
        }
        return Math.max(base, deepScale);
    }

    private double estimateVisibleLightSecondsAcrossMinPlotAxis(double availW, double availH, double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0) {
            return Double.NaN;
        }
        double minPlot = Math.min(availW, availH);
        double visibleM = minPlot / scale;
        return visibleM / SystemOrbitGeometry.LIGHT_SECOND_METRES;
    }

    /** World metres (map X/Y) to screen pixels at map centre; matches {@link #paintComponent} and wheel zoom. */
    private double computeScalePixelsPerWorldMetre() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0
                || !Double.isFinite(layoutSpanX) || !Double.isFinite(layoutSpanY)
                || layoutSpanX <= 0.0 || layoutSpanY <= 0.0) {
            return Double.NaN;
        }
        int plotH = Math.max(88, h - MAP_BOTTOM_INSET);
        double availW = w - 2.0 * PAD;
        double availH = plotH - 2.0 * PAD;
        return computeMapPlotScale(availW, availH, layoutSpanX, layoutSpanY);
    }

    /**
     * Sets {@link #zoomMinFit} from {@link #layoutSpanX}/{@link #layoutSpanY} and plot size so zoom-out cannot exceed
     * about {@link #ZOOM_MIN_MAX_VISIBLE_SPAN_RATIO}× the layout span on each visible axis. When the layout is very
     * wide or tall relative to the plot, the computed floor can exceed {@code 1.0}× fit — it is no longer capped at
     * {@code 1.0} so wheel zoom is not stuck (binary / multi-branch systems).
     */
    private void updateZoomMinFit(double availW, double availH) {
        if (availW <= 32.0 || availH <= 32.0
                || !Double.isFinite(layoutSpanX) || !Double.isFinite(layoutSpanY)
                || layoutSpanX <= 0.0 || layoutSpanY <= 0.0) {
            return;
        }
        if (!Double.isFinite(availW) || !Double.isFinite(availH)) {
            return;
        }
        double r = ZOOM_MIN_MAX_VISIBLE_SPAN_RATIO;
        double scaleFit = Math.min(availW / layoutSpanX, availH / layoutSpanY);
        if (!Double.isFinite(scaleFit) || scaleFit <= 0.0) {
            return;
        }
        double zx = availW / (scaleFit * layoutSpanX * r);
        double zy = availH / (scaleFit * layoutSpanY * r);
        if (!Double.isFinite(zx) || !Double.isFinite(zy)) {
            return;
        }
        double need = Math.max(zx, zy);
        zoomMinFit = zoomMinimumFitFromNeed(need);
    }

    /**
     * {@link #updateZoomMinFit} can set {@link #zoomMinFit} above {@code 1} when the layout box is much wider on one
     * world axis than the other vs the plot aspect — that zoom-in shrinks the visible world below the padded dot hull
     * unless spans grow. Grow {@link #layoutSpanX}/{@link #layoutSpanY} until the policy no longer demands extra
     * zoom-in, so {@code zoomFactor=1} still frames all body dots.
     */
    private void inflateLayoutSpansUntilZoomMinFitAtMostOne(double availW, double availH) {
        if (availW <= 32.0 || availH <= 32.0
                || !Double.isFinite(layoutSpanX) || !Double.isFinite(layoutSpanY)
                || layoutSpanX <= 0.0 || layoutSpanY <= 0.0) {
            return;
        }
        double r = ZOOM_MIN_MAX_VISIBLE_SPAN_RATIO;
        for (int k = 0; k < 14; k++) {
            updateZoomMinFit(availW, availH);
            if (zoomMinFit <= 1.0 + 1e-9) {
                return;
            }
            double scaleFit = Math.min(availW / layoutSpanX, availH / layoutSpanY);
            if (!Double.isFinite(scaleFit) || scaleFit <= 0.0) {
                return;
            }
            double zx = availW / (scaleFit * layoutSpanX * r);
            double zy = availH / (scaleFit * layoutSpanY * r);
            if (!Double.isFinite(zx) || !Double.isFinite(zy)) {
                return;
            }
            boolean bumped = false;
            if (zx > 1.0 + 1e-9) {
                layoutSpanX *= zx;
                bumped = true;
            }
            if (zy > 1.0 + 1e-9) {
                layoutSpanY *= zy;
                bumped = true;
            }
            if (!bumped) {
                return;
            }
        }
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
        long tiltBits = mapScaleMode.trueScale() ? ((long) viewTiltDegrees & 0x7fL) : 0L;
        return ((long) w << 44) ^ ((long) h << 24) ^ (((long) zq & 0xfffffL) << 4) ^ (scBits >>> 1) ^ (tiltBits << 56);
    }

    /**
     * Grows {@link #layoutSpanX}/{@link #layoutSpanY} to include all closed orbit polylines. Body-dot hull alone is
     * often ~half the visible orbit diameter (star + planet at 16k Ls still has a ~32k Ls ring around the star).
     */
    private void expandLayoutSpansForOrbitPolylines() {
        if (orbitLines == null || orbitLines.isEmpty()) {
            return;
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (OrbitPolylineWorldXY poly : orbitLines) {
            if (poly == null || poly.wx == null || poly.wy == null) {
                continue;
            }
            int n = Math.min(poly.wx.length, poly.wy.length);
            for (int i = 0; i < n; i++) {
                if (Double.isFinite(poly.wx[i])) {
                    minX = Math.min(minX, poly.wx[i]);
                    maxX = Math.max(maxX, poly.wx[i]);
                }
                if (Double.isFinite(poly.wy[i])) {
                    minY = Math.min(minY, poly.wy[i]);
                    maxY = Math.max(maxY, poly.wy[i]);
                }
            }
        }
        if (!Double.isFinite(minX) || !Double.isFinite(maxX) || !Double.isFinite(minY) || !Double.isFinite(maxY)
                || minX > maxX || minY > maxY) {
            return;
        }
        double needX = layoutSpanAxisMetres(minX, maxX) * LAYOUT_MIN_OVER_ORBIT_HULL;
        double needY = layoutSpanAxisMetres(minY, maxY) * LAYOUT_MIN_OVER_ORBIT_HULL;
        if (needX > layoutSpanX * 1.0000001) {
            layoutSpanX = needX;
        }
        if (needY > layoutSpanY * 1.0000001) {
            layoutSpanY = needY;
        }
    }

    private void rebuildOrbitPolylines(boolean force) {
        rebuildOrbitPolylines(force, true, null);
    }

    private void rebuildOrbitPolylines(boolean force, boolean useScreenChordScaleForSegments) {
        rebuildOrbitPolylines(force, useScreenChordScaleForSegments, null);
    }

    /**
     * @param useScreenChordScaleForSegments when true, pass {@link #computeScalePixelsPerWorldMetre()} into geometry so
     *        per-orbit vertex count tracks screen chord (smoother curves when zoomed). When false (orbit playback
     *        ticks), use {@link Double#NaN} so segment counts stay on the legacy zoom curve only — avoids chord count
     *        flipping frame-to-frame when schematic fallback radii change.
     * @param ringRadiusReferencePositions when non-null, schematic ring radii are taken from this snapshot (play T+0
     *        layout) while centres follow {@link #orbitGeomPositions} so radii do not breathe each tick.
     */
    private void rebuildOrbitPolylines(boolean force, boolean useScreenChordScaleForSegments,
            Map<Integer, double[]> ringRadiusReferencePositions) {
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
        /*
         * Screen-chord segment count tracks how many pixels each orbit spans at the current zoom. Disabled during
         * schematic playback so vertex count stays fixed and rings do not tangentially stutter each tick.
         */
        boolean screenOrbitScale = useScreenChordScaleForSegments && !orbitSchematicPlaybackActive;
        double scalePxPerM = screenOrbitScale ? computeScalePixelsPerWorldMetre() : Double.NaN;
        int legacySeg = orbitSegmentsForZoom(zoomFactor);
        int tiltForRebuild = mapScaleMode.trueScale() ? viewTiltDegrees : 0;
        Instant strokeEpoch = orbitSchematicPlaybackActive && mapScaleMode.trueScale() ? orbitPlaybackEpoch : null;
        orbitLines = mapModel != null
                ? SystemMapPipeline.rebuildOrbitPolylines(mapModel, orbitGeomPositions, legacySeg, scalePxPerM,
                        false, ringRadiusReferencePositions, mapScaleMode, tiltForRebuild, strokeEpoch)
                : Collections.emptyList();
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
            int pId = mapResolvedParent(pl.bodyId);
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

    private BodyDot findBodyDotByNameSuffix(Map<Integer, BodyInfo> bodies, String suffix) {
        if (bodies == null || suffix == null) {
            return null;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String nm = e.getValue().getBodyName();
            if (nm != null && nm.endsWith(suffix)) {
                return findBodyDot(e.getKey().intValue());
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
            int needLen = Math.max(mapProjA0, mapProjA1) + 1;
            if (r != null && r.length >= needLen) {
                double px = SystemOrbitGeometry.worldAxisMetres(r, mapProjA0);
                double py = SystemOrbitGeometry.worldAxisMetres(r, mapProjA1);
                if (Double.isFinite(px) && Double.isFinite(py)) {
                    outXY[0] = px;
                    outXY[1] = py;
                    return true;
                }
            }
        }
        return false;
    }

    private static int subsystemFocusKeyForBody(int bodyId, Map<Integer, BodyInfo> bodies,
            Map<Integer, Integer> resolvedParents) {
        if (bodies == null || bodyId < 0 || !bodies.containsKey(Integer.valueOf(bodyId))) {
            return -1;
        }
        Set<Integer> one = Collections.singleton(Integer.valueOf(bodyId));
        return deepestCommonOrbitAncestor(one, bodies, resolvedParents);
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
        int k0 = subsystemFocusKeyForBody(prev.intValue(), bodies, mapResolvedParents());
        int k1 = subsystemFocusKeyForBody(newHighlightId.intValue(), bodies, mapResolvedParents());
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

        int rootId = SystemOrbitGeometry.primaryAnchorBodyMapKey(bodies);
        double targetZoom = (hubBodyId == rootId) ? SUBSYSTEM_HOP_TARGET_ZOOM_STAR : SUBSYSTEM_HOP_TARGET_ZOOM_MOON_CLUSTER;
        targetZoom = clamp(targetZoom, zoomMinFit, ZOOM_MAX);

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
            zoomFactor = clamp(subHopZ2, zoomMinFit, ZOOM_MAX);
            viewCenterWx = subHopX2;
            viewCenterWy = subHopY2;
            cancelSubsystemProximityHop();
        }
        zoomFactor = clamp(zoomFactor, zoomMinFit, ZOOM_MAX);
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
        return tryApplyPositionUpdate(bodies, positions, shipM, anchorBodyId, highlightNearBodyId,
                orbitSchematicPlaybackActive, Instant.now());
    }

    public boolean tryApplyPositionUpdate(Map<Integer, BodyInfo> bodies,
            Map<Integer, double[]> positions,
            double[] shipM,
            Integer anchorBodyId,
            Integer highlightNearBodyId,
            boolean orbitSchematicPlaybackActive,
            Instant orbitPositionEpoch) {
        if (sceneEmpty || bodies == null || positions == null || orbitGeomBodies == null) {
            return false;
        }
        if (!orbitGeomBodies.keySet().equals(bodies.keySet())) {
            return false;
        }
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (e.getValue().isScanBarycentreRow()) {
                continue;
            }
            double[] p = positions.get(e.getKey());
            if (p == null || p.length < 2 || !Double.isFinite(p[0]) || !Double.isFinite(p[1])) {
                continue;
            }
            if (findBodyDot(e.getKey().intValue()) == null) {
                /* New body now has coordinates — need full setScene to add a BodyDot. */
                return false;
            }
        }
        this.orbitSchematicPlaybackActive = orbitSchematicPlaybackActive;
        syncOrbitPlaybackEpochTracking(orbitSchematicPlaybackActive, orbitPositionEpoch);
        this.anchorBodyId = anchorBodyId;
        this.highlightNearBodyId = highlightNearBodyId;
        orbitGeomBodies = bodies;
        if (orbitSchematicPlaybackActive && !playbackPipelinePrimed && bodies != null && !bodies.isEmpty()) {
            String mapSystemName = resolveMapSystemName(bodies);
            Instant primeEpoch = orbitPlaybackBaseEpoch != null ? orbitPlaybackBaseEpoch : orbitPositionEpoch;
            mapModel = SystemMapPipeline.build(mapSystemName, bodies, primeEpoch,
                    freezeBarycentreStarsDuringPlayback(), mapScaleMode);
            mapProjA0 = mapModel.projectionAxis0();
            mapProjA1 = mapModel.projectionAxis1();
            wideBinaryFlattenFrame = mapModel.wideBinaryFlattenFrame();
            playbackPipelinePrimed = true;
        } else if (!orbitSchematicPlaybackActive) {
            playbackPipelinePrimed = false;
        }
        if (mapModel != null) {
            positions = SystemMapPipeline.refreshPositionsForPlayback(mapModel, positions, orbitPositionEpoch,
                    freezeBarycentreStarsDuringPlayback());
        }
        orbitGeomPositions = positions;
        for (BodyDot d : dots) {
            double[] p = positions.get(Integer.valueOf(d.bodyId));
            if (p == null || p.length < 2) {
                continue;
            }
            int needLen = Math.max(mapProjA0, mapProjA1) + 1;
            if (p.length < needLen) {
                continue;
            }
            double[] xy = mapViewCoordsFromWorldMetres(p);
            if (!Double.isFinite(xy[0]) || !Double.isFinite(xy[1])) {
                continue;
            }
            d.wx = xy[0];
            d.wy = xy[1];
        }
        if (shipM != null && shipM.length >= 2) {
            commanderPositionMetres = shipM;
            double[] sxy = mapViewCoordsFromWorldMetres(shipM);
            if (Double.isFinite(sxy[0]) && Double.isFinite(sxy[1])) {
                shipKnown = true;
                shipWx = sxy[0];
                shipWy = sxy[1];
            } else {
                shipKnown = false;
                shipWx = 0;
                shipWy = 0;
            }
        } else {
            commanderPositionMetres = null;
            shipKnown = false;
            shipWx = 0;
            shipWy = 0;
        }
        if (orbitSchematicPlaybackActive && zoomFactor >= ZOOM_SUBSYSTEM_CENTER_LOCK - 1e-6) {
            int pw = getWidth();
            int ph = getHeight();
            if (pw > 0 && ph > 0) {
                int plotH = Math.max(88, ph - MAP_BOTTOM_INSET);
                double aw = pw - 2.0 * PAD;
                double ah = plotH - 2.0 * PAD;
                updateZoomMinFit(aw, ah);
                double sc = computeMapPlotScale(aw, ah, layoutSpanX, layoutSpanY);
                if (Double.isFinite(sc) && sc > 0.0) {
                    followSubsystemHubDuringPlayback(sc);
                }
            }
        }
        /*
         * Rings must be rebuilt with current parent centres (e.g. moon paths around a moving giant). Cached strokes
         * from setScene stay fixed in world space and look like stationary breadcrumbs when the camera follows a hub.
         */
        if (orbitSchematicPlaybackActive) {
            /*
             * Frozen T+0 radii are schematic-only; true-scale playback uses Kepler/fallback radii from live positions
             * so elliptical paths stay tied to the moving direct parent.
             */
            Map<Integer, double[]> ringRadiusRef = mapModel != null && !mapScaleMode.trueScale()
                    ? mapModel.positionsMetres()
                    : null;
            rebuildOrbitPolylines(true, false, ringRadiusRef);
        }
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
        scheduleWheelZoom(e.getX(), e.getY(), e.getWheelRotation());
    }

    /**
     * Accumulates wheel notches and applies a single zoom on the next EDT flush. Multiple events before that flush
     * (common during a fast spin) become one {@link #applyWheelZoomAtComponent} call.
     */
    private void scheduleWheelZoom(int localX, int localY, int wheelRotation) {
        if (wheelRotation == 0) {
            return;
        }
        cancelSubsystemProximityHop();
        pendingWheelClicks += wheelRotation;
        pendingWheelLocalX = localX;
        pendingWheelLocalY = localY;
        if (!wheelZoomFlushScheduled) {
            wheelZoomFlushScheduled = true;
            SwingUtilities.invokeLater(this::flushPendingWheelZoom);
        }
    }

    private void flushPendingWheelZoom() {
        wheelZoomFlushScheduled = false;
        int clicks = pendingWheelClicks;
        pendingWheelClicks = 0;
        if (clicks == 0) {
            return;
        }
        applyWheelZoomAtComponent(pendingWheelLocalX, pendingWheelLocalY, clicks);
    }

    private void resetPendingWheelZoom() {
        pendingWheelClicks = 0;
        wheelZoomFlushScheduled = false;
    }

    /**
     * Drag pan: shift {@link #viewCenterWx}/{@link #viewCenterWy} using the same world scale as paint / wheel zoom
     * ({@code dx} right → map content moves right).
     */
    private void applyMapPanPixelDelta(int dxPix, int dyPix) {
        if (sceneEmpty || dots.isEmpty() || (dxPix == 0 && dyPix == 0)) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        int plotH = Math.max(88, h - MAP_BOTTOM_INSET);
        double availW = w - 2.0 * PAD;
        double availH = plotH - 2.0 * PAD;
        double spanX = layoutSpanX;
        double spanY = layoutSpanY;
        if (!Double.isFinite(spanX) || !Double.isFinite(spanY) || spanX <= 0.0 || spanY <= 0.0) {
            return;
        }
        if (!Double.isFinite(availW) || !Double.isFinite(availH) || availW <= 0.0 || availH <= 0.0) {
            return;
        }
        updateZoomMinFit(availW, availH);
        zoomFactor = clamp(zoomFactor, zoomMinFit, ZOOM_MAX);
        double scale = computeMapPlotScale(availW, availH, spanX, spanY);
        if (!Double.isFinite(scale) || scale <= 0.0) {
            return;
        }
        viewCenterWx -= dxPix / scale;
        viewCenterWy += dyPix / scale;
        repaint();
    }

    /**
     * Mouse pass-through: zoom from global screen coordinates when the pointer is over this panel (see
     * {@link org.dce.ed.SystemTabPanel#applyPassThroughWheelIfHit}). When OS-level pass-through is on, wheel events may
     * carry stale coordinates; this method prefers {@link MouseInfo#getPointerInfo()} so zoom stays anchored to the
     * actual cursor.
     *
     * @return {@code true} if the wheel was consumed here (pointer over the map with a non-empty scene), including
     *         when already at min/max zoom so the bodies table scroller does not also react
     */
    public boolean applyPassThroughWheelIfHit(int screenX, int screenY, int wheelRotation) {
        if (wheelRotation == 0 || !isShowing() || sceneEmpty || dots.isEmpty()) {
            return false;
        }
        Point p = new Point(screenX, screenY);
        if (OverlayPreferences.isOverlayMousePassThroughToGame()) {
            try {
                PointerInfo pi = MouseInfo.getPointerInfo();
                if (pi != null) {
                    Point loc = pi.getLocation();
                    if (loc != null) {
                        p.setLocation(loc);
                    }
                }
            } catch (HeadlessException | SecurityException ignored) {
                // keep screenX/screenY
            }
        }
        SwingUtilities.convertPointFromScreen(p, this);
        if (!contains(p)) {
            return false;
        }
        scheduleWheelZoom(p.x, p.y, wheelRotation);
        return true;
    }

    private void applyWheelZoomAtComponent(int localX, int localY, int wheelRotation) {
        if (sceneEmpty || dots.isEmpty()) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        int plotH = Math.max(88, h - MAP_BOTTOM_INSET);
        double availW = w - 2.0 * PAD;
        double availH = plotH - 2.0 * PAD;
        int clicks = wheelRotation;
        if (clicks == 0) {
            return;
        }
        if (MAP_AUTO_VIEW_PAN) {
            inflateLayoutSpansUntilZoomMinFitAtMostOne(availW, availH);
        }
        double spanX = layoutSpanX;
        double spanY = layoutSpanY;
        if (!Double.isFinite(spanX) || !Double.isFinite(spanY) || spanX <= 0.0 || spanY <= 0.0) {
            return;
        }
        updateZoomMinFit(availW, availH);
        double scale = computeMapPlotScale(availW, availH, spanX, spanY);
        if (!Double.isFinite(scale) || scale <= 0.0) {
            return;
        }
        double ex = localX;
        double ey = localY;
        double wx = viewCenterWx + (ex - PAD - availW / 2.0) / scale;
        double wy = viewCenterWy - (ey - PAD - availH / 2.0) / scale;

        double factor = Math.pow(ZOOM_PER_NOTCH, -clicks);
        double newZoom = clamp(zoomFactor * factor, zoomMinFit, ZOOM_MAX);
        double prevZoom = zoomFactor;
        zoomFactor = newZoom;
        double scaleNew = computeMapPlotScale(availW, availH, spanX, spanY);
        if (newZoom == prevZoom) {
            return;
        }
        viewCenterWx = wx - (ex - PAD - availW / 2.0) / scaleNew;
        viewCenterWy = wy + (ey - PAD - availH / 2.0) / scaleNew;
        if (zoomFactor >= ZOOM_SUBSYSTEM_CENTER_LOCK && zoomFactor > prevZoom) {
            ResolvedSubsystemHub hub = tryResolveSubsystemFollowHub(availW, availH, scaleNew, false);
            if (hub != null) {
                nudgeViewCenterTowardSubsystemHubOnWheel(hub, scaleNew);
            }
        } else if (MAP_AUTO_VIEW_PAN && !orbitSchematicPlaybackActive
                && zoomFactor < ZOOM_SUBSYSTEM_CENTER_LOCK && zoomFactor < prevZoom) {
            nudgeViewCenterTowardSystemCentroidOnWheel(scaleNew);
        }
        if (MAP_AUTO_VIEW_PAN && !orbitSchematicPlaybackActive && zoomEssentiallyAtMinFit()) {
            snapViewCenterToSystemCentroidWorld();
        }
        // Orbit stroke rebuild runs in paintComponent (one rebuild per visible frame, not per wheel notch).
        repaint();
    }

    /** True when {@link #zoomFactor} is at the per-scene floor (after clamp). */
    private boolean zoomEssentiallyAtMinFit() {
        return zoomFactor <= zoomMinFit + 1e-7 * Math.max(1.0, Math.abs(zoomMinFit));
    }

    /**
     * Sets view centre to the midpoint of the same padded bounds as {@link #computeBounds} (layout framing), not the
     * arithmetic mean of dots — wide / binary systems stay centred between branches instead of pulled into one cluster.
     */
    private void snapViewCenterToSystemCentroidWorld() {
        if (dots.isEmpty()) {
            return;
        }
        Bounds bb = computeBounds(dots, shipKnown ? shipWx : Double.NaN, shipKnown ? shipWy : Double.NaN);
        Bounds bbDots = computeBoundsDotsOnly(dots);
        double[] spans = layoutFitSpansAndRobust(dots, bb, bbDots);
        MapLayoutSpanPick pick = pickMapLayoutSpans(dots, spans[0], spans[1], spans[4], spans[5]);
        double lsx = pick.layoutSpanX();
        double lsy = pick.layoutSpanY();
        double dhx = layoutHullSpanMetresPreferCluster(dots, true);
        double dhy = layoutHullSpanMetresPreferCluster(dots, false);
        boolean hullSpanRaised = false;
        if (dhx > 1.0) {
            double needX = dhx * LAYOUT_MIN_OVER_ALL_DOT_HULL;
            if (needX > lsx * 1.0000001) {
                hullSpanRaised = true;
            }
            lsx = Math.max(lsx, needX);
        }
        if (dhy > 1.0) {
            double needY = dhy * LAYOUT_MIN_OVER_ALL_DOT_HULL;
            if (needY > lsy * 1.0000001) {
                hullSpanRaised = true;
            }
            lsy = Math.max(lsy, needY);
        }
        if (hullSpanRaised) {
            if (Double.isFinite(bbDots.minX) && Double.isFinite(bbDots.maxX) && Double.isFinite(bbDots.minY)
                    && Double.isFinite(bbDots.maxY) && bbDots.minX <= bbDots.maxX && bbDots.minY <= bbDots.maxY) {
                viewCenterWx = (bbDots.minX + bbDots.maxX) * 0.5;
                viewCenterWy = (bbDots.minY + bbDots.maxY) * 0.5;
            }
        } else {
            applyClusterAwareViewCenter(bb, spans[0], spans[1], spans[2], spans[3], spans[4], spans[5], spans[6],
                    spans[7], dots);
            if (pick.useNonStarCentroid()) {
                double[] nsMean = dotArithmeticMeanWorldXYNonStars(dots);
                if (Double.isFinite(nsMean[0]) && Double.isFinite(nsMean[1])) {
                    viewCenterWx = nsMean[0];
                    viewCenterWy = nsMean[1];
                }
            }
        }
        subsystemScreenLockHubId = -1;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Minimum zoom (× fit scale) from aspect policy. Upper bound is {@link #ZOOM_MAX} so {@code clamp(zoom, zoomMinFit,
     * ZOOM_MAX)} always has {@code zoomMinFit ≤ ZOOM_MAX}. When {@code need ≤ 1}, the effective cap stays {@code 1}
     * so extra zoom-out past fit is still allowed for normal systems.
     */
    private static double zoomMinimumFitFromNeed(double need) {
        if (!Double.isFinite(need) || need <= 0.0) {
            return ZOOM_MIN_ABSOLUTE_FLOOR;
        }
        double upper = Math.min(ZOOM_MAX, Math.max(1.0, need));
        double lower = ZOOM_MIN_ABSOLUTE_FLOOR;
        if (upper < lower) {
            return lower;
        }
        return clamp(need, lower, upper);
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
        return SystemOrbitGeometry.isPrimaryStarBodyByName(b);
    }

    private static String labelFor(BodyInfo b) {
        return labelFor(b, b.getBodyId());
    }

    /**
     * @param mapKey body id from the system map (journal); used when {@link BodyInfo#getBodyId()} is still unset ({@code -1})
     */
    private static String labelFor(BodyInfo b, int mapKey) {
        String s = b.getShortName();
        if (s == null || s.isBlank()) {
            s = b.getBodyName();
        }
        if (s == null || s.isBlank()) {
            int id = b.getBodyId();
            if (id < 0) {
                id = mapKey;
            }
            return "?" + id;
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

            double availW = w - 2.0 * PAD;
            double availH = plotH - 2.0 * PAD;
            if (MAP_AUTO_VIEW_PAN) {
                inflateLayoutSpansUntilZoomMinFitAtMostOne(availW, availH);
            } else {
                updateZoomMinFit(availW, availH);
            }
            zoomFactor = clamp(zoomFactor, zoomMinFit, ZOOM_MAX);
            double spanX = layoutSpanX;
            double spanY = layoutSpanY;
            if (MAP_AUTO_VIEW_PAN && !orbitSchematicPlaybackActive && zoomEssentiallyAtMinFit()) {
                if (lastMinZoomSnapPaintW >= 0 && (w != lastMinZoomSnapPaintW || h != lastMinZoomSnapPaintH)) {
                    snapViewCenterToSystemCentroidWorld();
                }
                lastMinZoomSnapPaintW = w;
                lastMinZoomSnapPaintH = h;
            } else {
                lastMinZoomSnapPaintW = w;
                lastMinZoomSnapPaintH = h;
            }
            double scale = computeMapPlotScale(availW, availH, spanX, spanY);
            if (!Double.isFinite(scale) || scale <= 0.0) {
                g2.setColor(EdoUi.Internal.GRAY_180);
                g2.drawString("Map scale unavailable", PAD, PAD + labelFm.getAscent());
                return;
            }
            boolean pauseResync = pendingSubsystemCenterPauseResync;
            if (pendingSubsystemCenterPauseResync) {
                pendingSubsystemCenterPauseResync = false;
            }
            if (subsystemProximityHopActive) {
                subsystemScreenLockHubId = -1;
            } else if (orbitSchematicPlaybackActive || MAP_AUTO_VIEW_PAN) {
                maybeApplySubsystemCenterLock(availW, availH, scale, pauseResync);
            } else {
                subsystemScreenLockHubId = -1;
            }
            double vcx = viewCenterWx;
            double vcy = viewCenterWy;

            final double plotCx = PAD + availW * 0.5;
            final double plotCy = PAD + availH * 0.5;

            rebuildOrbitPolylines(false, !orbitSchematicPlaybackActive);

            g2.setColor(EdoUi.Internal.GRAY_ALPHA_140);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(PAD, PAD, (int) Math.round(availW), (int) Math.round(availH), 6, 6);

            double visibleLsMinAxis = estimateVisibleLightSecondsAcrossMinPlotAxis(availW, availH, scale);
            boolean showClusterDetail = mapShowClusterDetail(visibleLsMinAxis);
            CompanionBranchLump companionLump = buildCompanionBranchRevolutionLump(dots, showClusterDetail,
                    visibleLsMinAxis);

            if (orbitLines != null && !orbitLines.isEmpty()) {
                /*
                 * Fill the stroked outline (not draw the raw path): shape AA applies reliably to the filled band.
                 * Single-pixel draw() on some Windows/Java2D pipelines stays visibly aliased even with AA hints on.
                 */
                Color orbitBlue = new Color(110, 165, 220);
                BasicStroke orbitStrokeThin = new BasicStroke(1.15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f);
                BasicStroke orbitStrokeMoon = new BasicStroke(1.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f);
                g2.setColor(orbitBlue);
                boolean detailOrbits = showClusterDetail;
                for (OrbitPolylineWorldXY poly : orbitLines) {
                    if (poly == null || poly.wx == null || poly.wy == null
                            || poly.wx.length < 3 || poly.wy.length != poly.wx.length) {
                        continue;
                    }
                    if (skipOrbitPolylineForCompanionLump(poly, companionLump, detailOrbits)) {
                        continue;
                    }
                    if (skipOrbitPolylineForSubsystemLump(poly, visibleLsMinAxis, showClusterDetail)) {
                        continue;
                    }
                    if (skipOversizeSchematicRingForDetailView(poly, visibleLsMinAxis, vcx, vcy, scale, availW,
                            availH, detailOrbits)) {
                        continue;
                    }
                    BasicStroke orbitStroke = detailOrbits && poly.bodyId > 0 && isMoonOrbitPolyline(poly.bodyId)
                            ? orbitStrokeMoon
                            : orbitStrokeThin;
                    if (poly.estimated) {
                        orbitStroke = new BasicStroke(orbitStroke.getLineWidth(), BasicStroke.CAP_ROUND,
                                BasicStroke.JOIN_ROUND, 10f, new float[] { 4f, 5f }, 0f);
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

            boolean showMoonLabels = mapShowMoonLabels(visibleLsMinAxis);
            boolean showAllBodyLabels = mapShowAllBodyLabels(visibleLsMinAxis);

            MapLabelDrawPlan labelPlan = buildCollisionAwareLabelPlan(dots, showClusterDetail, showMoonLabels,
                    showAllBodyLabels, companionLump, labelFm, vcx, vcy, scale, availW, availH, plotCx, plotCy);

            for (BodyDot d : dots) {
                boolean lockHub = subsystemScreenLockHubId >= 0 && d.bodyId == subsystemScreenLockHubId;
                double sx = lockHub ? plotCx : PAD + availW / 2.0 + (d.wx - vcx) * scale;
                double sy = lockHub ? plotCy : PAD + availH / 2.0 - (d.wy - vcy) * scale;
                if (!Double.isFinite(sx) || !Double.isFinite(sy)) {
                    continue;
                }
                if (!d.star && labelPlan.summaryClusterMemberIds.contains(Integer.valueOf(d.bodyId))
                        || (companionLump != null && companionLump.contains(d.bodyId))
                        || hideDotForSubsystemLumpView(d, visibleLsMinAxis)) {
                    continue;
                }
                float r = mapBodyDotRadiusPx(d, starR, bodyR, zoomFactor, showClusterDetail);
                boolean lumpHub = subsystemHubLump(visibleLsMinAxis, d);
                boolean moonHostHub = !d.star && subsystemHubLumpBodyIds.contains(Integer.valueOf(d.bodyId));
                boolean hubTwinBlueRings = moonHostHub && lumpHub;
                boolean ringHubOrSolo = !d.star
                        && (d.soleOrbitCluster || subsystemHubLumpBodyIds.contains(Integer.valueOf(d.bodyId)));
                boolean ringedOrbitCentre = !d.star && mapModel != null
                        && mapModel.isOrbitRevolutionCenter(d.bodyId);
                boolean ringsZoomOk = d.hasPlanetaryRings
                        && (ringHubOrSolo || ringedOrbitCentre
                                || (showClusterDetail && zoomFactor >= ZOOM_MAP_BODY_RINGS));
                boolean ringsAfterHubIcon = hubTwinBlueRings;
                if (ringsZoomOk && !ringsAfterHubIcon) {
                    float ringDecorR = r;
                    if (!showClusterDetail && (ringHubOrSolo || ringedOrbitCentre)) {
                        ringDecorR = Math.max(r, Math.max(bodyR * 0.92f, 4.5f));
                    }
                    drawPlanetaryRingsDecor(g2, sx, sy, ringDecorR);
                }
                BodyInfo mapBody = orbitGeomBodies != null ? orbitGeomBodies.get(Integer.valueOf(d.bodyId)) : null;
                boolean starHostedRevolutionCentre = ringedOrbitCentre && mapModel != null
                        && mapModel.resolveParentBodyId(d.bodyId) >= 0
                        && orbitGeomBodies != null
                        && SystemMapRules.isMapStellarBody(
                                orbitGeomBodies.get(Integer.valueOf(mapModel.resolveParentBodyId(d.bodyId))));
                if (moonHostHub) {
                    if (!lumpHub) {
                        Color fill = mapBodyFillColor(mapBody, d);
                        g2.setColor(fill);
                        g2.fill(new Ellipse2D.Double(sx - r, sy - r, r * 2, r * 2));
                    } else if (!showClusterDetail) {
                        float lr = Math.max(2.5f, bodyR * 0.52f);
                        g2.setColor(MAP_PLANET_DEFAULT_DOT);
                        g2.fill(new Ellipse2D.Double(sx - lr, sy - lr, lr * 2, lr * 2));
                    }
                    if (!mapScaleMode.trueScale() && (hubTwinBlueRings || starHostedRevolutionCentre)) {
                        drawSubsystemHubRevolutionPathRing(g2, d.bodyId, d.wx, d.wy, vcx, vcy, scale, availW, availH);
                        if (hubTwinBlueRings) {
                            drawSubsystemMoonHubDoubleRing(g2, sx, sy, r);
                            if (ringsZoomOk) {
                                float ringDecorR = Math.max(r, Math.max(bodyR * 0.92f, 4.5f));
                                drawPlanetaryRingsDecor(g2, sx, sy, ringDecorR);
                            }
                        }
                    }
                } else if (starHostedRevolutionCentre) {
                    Color fill = mapBodyFillColor(mapBody, d);
                    g2.setColor(fill);
                    g2.fill(new Ellipse2D.Double(sx - r, sy - r, r * 2, r * 2));
                    if (!mapScaleMode.trueScale()) {
                        drawSubsystemHubRevolutionPathRing(g2, d.bodyId, d.wx, d.wy, vcx, vcy, scale, availW, availH);
                    }
                } else if (d.star) {
                    float starDrawR = d.loneCentralPrimary ? Math.max(r, starR) : r;
                    drawStarBody(g2, sx, sy, starDrawR, mapStarCoreColor(mapBody), mapBody);
                } else {
                    Color fill = mapBodyFillColor(mapBody, d);
                    g2.setColor(fill);
                    g2.fill(new Ellipse2D.Double(sx - r, sy - r, r * 2, r * 2));
                }
                if (d.estimatedPosition) {
                    g2.setColor(new Color(220, 170, 80, 210));
                    g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(new Ellipse2D.Double(sx - r, sy - r, r * 2, r * 2));
                }

                boolean hi = highlightNearBodyId != null && highlightNearBodyId.intValue() == d.bodyId;
                boolean anch = anchorBodyId != null && anchorBodyId.intValue() == d.bodyId;
                if (hi && !anch) {
                    g2.setColor(new Color(0, 200, 255, 220));
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    float hr = hubTwinBlueRings
                            ? Math.max(11f, subsystemMoonHubRingOuterRadiusPx(r) + dotEm * 0.42f)
                            : r + Math.max(3.5f, dotEm * 0.55f);
                    g2.draw(new Ellipse2D.Double(sx - hr, sy - hr, hr * 2, hr * 2));
                }
            }

            if (companionLump != null) {
                drawCompanionBranchLumpHub(g2, companionLump, vcx, vcy, scale, availW, availH, bodyR);
            }

            drawSummaryClusterCenterDots(g2, labelPlan, bodyR, companionLump);

            drawBarycentreMarkers(g2, dotEm, vcx, vcy, scale, availW, availH, plotCx, plotCy, companionLump);

            /* Labels last so orbit strokes never paint over text. */
            for (BodyDot d : dots) {
                boolean lockHub = subsystemScreenLockHubId >= 0 && d.bodyId == subsystemScreenLockHubId;
                double sx = lockHub ? plotCx : PAD + availW / 2.0 + (d.wx - vcx) * scale;
                double sy = lockHub ? plotCy : PAD + availH / 2.0 - (d.wy - vcy) * scale;
                if (!Double.isFinite(sx) || !Double.isFinite(sy)) {
                    continue;
                }
                float r = mapBodyDotRadiusPx(d, starR, bodyR, zoomFactor, showClusterDetail);
                boolean lumpHub = subsystemHubLump(visibleLsMinAxis, d);
                boolean moonHostHub = !d.star && subsystemHubLumpBodyIds.contains(Integer.valueOf(d.bodyId));
                boolean hubTwinBlueRings = moonHostHub && lumpHub;
                float rLabel = hubTwinBlueRings
                        ? Math.max(7.5f, subsystemMoonHubRingOuterRadiusPx(r) + 2.5f)
                        : r;

                if (d.label == null || d.label.isEmpty()) {
                    continue;
                }

                boolean drawName = bodyDotLabelWouldDraw(d, showClusterDetail, showMoonLabels, showAllBodyLabels,
                        companionLump);
                if (!drawName || labelPlan.suppressedBodyIds.contains(Integer.valueOf(d.bodyId))) {
                    continue;
                }
                if (companionLump != null && companionLump.contains(d.bodyId)
                        && d.bodyId != companionLump.hubBodyId) {
                    continue;
                }

                boolean commanderHere = anchorBodyId != null && d.bodyId == anchorBodyId.intValue();
                String displayLabel = labelPlan.summaryTextByHubId.getOrDefault(Integer.valueOf(d.bodyId), d.label);
                String slotLabel = commanderHere ? MAP_COMMANDER_TRIANGLE_CHAR + displayLabel : displayLabel;
                int labelWidthPx = commanderHere
                        ? commanderPrefixedLabelWidth(labelFm, displayLabel)
                        : labelFm.stringWidth(displayLabel);
                int lumpHubId = !showClusterDetail && orbitGeomBodies != null
                        ? outermostSubsystemLumpHub(d.bodyId, orbitGeomBodies, subsystemHubLumpBodyIds)
                        : -1;
                boolean lumpHubLabel = lumpHubId >= 0 && d.bodyId == lumpHubId;
                if (commanderHere) {
                    int triW = labelFm.stringWidth(MAP_COMMANDER_TRIANGLE_CHAR);
                    float[] triLp = bodyLabelAnchor((float) sx, (float) sy, rLabel, d.bodyId, slotLabel, triW, labelFm,
                            d.moon);
                    drawCommanderPrefixedBodyLabel(g2, displayLabel, triLp[0], triLp[1], labelFm);
                } else {
                    float[] lp = labelPlan.anchors.get(Integer.valueOf(d.bodyId));
                    if (lp == null) {
                        lp = lumpHubLabel
                                ? lumpHubClusterLabelAnchor(dots, lumpHubId, (float) sx, (float) sy, rLabel, d.bodyId,
                                        slotLabel, labelWidthPx, labelFm, orbitGeomBodies, subsystemHubLumpBodyIds,
                                        vcx, vcy, scale, availW, availH, plotCx, plotCy, lockHub)
                                : labelAnchorForBodyDot((float) sx, (float) sy, rLabel, d, slotLabel, labelWidthPx,
                                        labelFm);
                    }
                    drawLabelOutlined(g2, displayLabel, lp[0], lp[1]);
                }
            }

            maybeDrawDetachedCommanderGlyph(g2, labelFont, labelFm, showClusterDetail, showMoonLabels, showAllBodyLabels,
                    companionLump, starR, bodyR, dotEm, vcx, vcy, scale, availW, availH, plotCx, plotCy);

            drawExobiologyLeafMarkers(g2, visibleLsMinAxis, showClusterDetail, showMoonLabels, showAllBodyLabels,
                    companionLump, starR, bodyR, dotEm, vcx, vcy, scale, availW, availH, plotCx, plotCy);

            drawMeasureDragOverlay(g2, labelFm, vcx, vcy, scale, availW, availH);
            drawOrbitPlaybackTimeReadout(g2, labelFm, availW, availH);

        } finally {
            g2.dispose();
        }
    }

    /**
     * Records the synthetic epoch for schematic fast-forward (T+ base on first active tick; cleared when not playing).
     */
    private void syncOrbitPlaybackEpochTracking(boolean playbackActive, Instant orbitPositionEpoch) {
        if (!playbackActive) {
            orbitPlaybackBaseEpoch = null;
            orbitPlaybackEpoch = null;
            return;
        }
        Instant epoch = orbitPositionEpoch != null ? orbitPositionEpoch : Instant.now();
        if (orbitPlaybackBaseEpoch == null) {
            orbitPlaybackBaseEpoch = epoch;
        }
        orbitPlaybackEpoch = epoch;
    }

    /** Elapsed schematic time since playback start, e.g. {@code T+42 days} or {@code T+1.6 years}. */
    static String formatSimulationElapsedTPlus(Instant baseEpoch, Instant currentEpoch) {
        if (baseEpoch == null || currentEpoch == null) {
            return "";
        }
        long millis = Duration.between(baseEpoch, currentEpoch).toMillis();
        if (millis < 0L) {
            millis = 0L;
        }
        double days = millis / 86_400_000.0;
        if (days >= 365.0) {
            double years = days / 365.25;
            return years >= 10.0
                    ? String.format(Locale.US, "T+%.0f years", years)
                    : String.format(Locale.US, "T+%.1f years", years);
        }
        if (days >= 30.0) {
            double months = days / 30.4375;
            return months >= 10.0
                    ? String.format(Locale.US, "T+%.0f months", months)
                    : String.format(Locale.US, "T+%.1f months", months);
        }
        if (days >= 10.0) {
            return String.format(Locale.US, "T+%.0f days", days);
        }
        if (days >= 1.0) {
            return String.format(Locale.US, "T+%.1f days", days);
        }
        return String.format(Locale.US, "T+%.1f days", Math.max(days, 0.1));
    }

    private void drawOrbitPlaybackTimeReadout(Graphics2D g2, FontMetrics fm, double availW, double availH) {
        if (!orbitSchematicPlaybackActive || orbitPlaybackBaseEpoch == null || orbitPlaybackEpoch == null) {
            return;
        }
        String text = formatSimulationElapsedTPlus(orbitPlaybackBaseEpoch, orbitPlaybackEpoch);
        if (text.isEmpty()) {
            return;
        }
        int tw = fm.stringWidth(text);
        float x = (float) (PAD + availW - 8.0 - tw);
        float y = (float) (PAD + availH - 8.0);
        drawLabelOutlined(g2, text, x, y);
    }

    private void clearMeasureDrag() {
        measureDragActive = false;
    }

    private record MapPlotTransform(double availW, double availH, double scale, double viewCenterWx, double viewCenterWy,
            double plotCx, double plotCy) {
    }

    private static double[] componentToWorldMetres(MapPlotTransform t, int componentX, int componentY) {
        double wx = t.viewCenterWx() + (componentX - t.plotCx()) / t.scale();
        double wy = t.viewCenterWy() - (componentY - t.plotCy()) / t.scale();
        return new double[] { wx, wy };
    }

    private static double measureDragDistanceLs(MapPlotTransform t, int x0, int y0, int x1, int y1) {
        double[] w0 = componentToWorldMetres(t, x0, y0);
        double[] w1 = componentToWorldMetres(t, x1, y1);
        double dx = w1[0] - w0[0];
        double dy = w1[1] - w0[1];
        return Math.hypot(dx, dy) / SystemOrbitGeometry.LIGHT_SECOND_METRES;
    }

    private static String formatMeasureDistanceLs(double ls) {
        if (!Double.isFinite(ls)) {
            return "? Ls";
        }
        if (ls < 10.0) {
            return String.format(Locale.ROOT, "%.2f Ls", ls);
        }
        if (ls < 1000.0) {
            return String.format(Locale.ROOT, "%.1f Ls", ls);
        }
        return String.format(Locale.ROOT, "%.0f Ls", ls);
    }

    private void drawMeasureDragOverlay(Graphics2D g2, FontMetrics fm, double vcx, double vcy, double scale,
            double availW, double availH) {
        if (!measureDragActive) {
            return;
        }
        float x0 = measureStartX;
        float y0 = measureStartY;
        float x1 = measureEndX;
        float y1 = measureEndY;
        MapPlotTransform t = new MapPlotTransform(availW, availH, scale, vcx, vcy, PAD + availW * 0.5,
                PAD + availH * 0.5);
        double distLs = measureDragDistanceLs(t, measureStartX, measureStartY, measureEndX, measureEndY);
        String label = formatMeasureDistanceLs(distLs);

        Stroke prevStroke = g2.getStroke();
        Color prevColor = g2.getColor();
        try {
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(255, 220, 120, 230));
            g2.draw(new Line2D.Float(x0, y0, x1, y1));
            float r = 3.5f;
            g2.fill(new Ellipse2D.Float(x0 - r, y0 - r, r * 2f, r * 2f));
            g2.fill(new Ellipse2D.Float(x1 - r, y1 - r, r * 2f, r * 2f));

            float mx = (x0 + x1) * 0.5f;
            float my = (y0 + y1) * 0.5f;
            int tw = fm.stringWidth(label);
            float tx = mx - tw * 0.5f;
            float ty = my - fm.getHeight() * 0.55f;
            drawLabelOutlined(g2, label, tx, ty);
        } finally {
            g2.setStroke(prevStroke);
            g2.setColor(prevColor);
        }
    }

    private static String mapLc(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static Color mapBodyFillColor(BodyInfo body, BodyDot d) {
        SystemMapDotKind kind = ExplorationBodyCredits.systemMapDotKind(body, d != null && d.star);
        if (kind == SystemMapDotKind.EARTH_LIKE || kind == SystemMapDotKind.WATER_LIKE) {
            return MAP_FSS_HABITABLE_BLUE_DOT;
        }
        return MAP_PLANET_DEFAULT_DOT;
    }

    private static String mapStarSpectralType(BodyInfo body) {
        if (body == null) {
            return null;
        }
        String st = body.getStarType();
        if (st == null || st.isBlank()) {
            String atmo = body.getAtmoOrType();
            if (atmo != null && atmo.trim().length() == 1) {
                st = atmo.trim();
            }
        }
        return st != null && !st.isBlank() ? st.trim() : null;
    }

    private static boolean isNeutronStar(BodyInfo body) {
        String st = mapStarSpectralType(body);
        if (st == null) {
            return false;
        }
        String u = st.toUpperCase(java.util.Locale.ROOT);
        return "N".equals(u) || u.startsWith("NEUTRON");
    }

    private static Color mapStarCoreColor(BodyInfo body) {
        String st = mapStarSpectralType(body);
        if (st == null) {
            return new Color(255, 220, 120);
        }
        if (isNeutronStar(body)) {
            return new Color(248, 250, 255);
        }
        char spectral = Character.toUpperCase(st.charAt(0));
        return switch (spectral) {
            case 'O' -> new Color(130, 175, 255);
            case 'B' -> new Color(175, 200, 255);
            case 'A' -> new Color(240, 245, 255);
            case 'F' -> new Color(255, 250, 240);
            case 'G' -> new Color(255, 235, 120);
            case 'K' -> new Color(255, 185, 75);
            case 'M' -> new Color(255, 105, 55);
            case 'L' -> new Color(188, 38, 34);
            case 'T' -> new Color(255, 72, 42);
            case 'Y' -> new Color(255, 58, 38);
            case 'D' -> new Color(250, 252, 255);
            case 'N' -> new Color(248, 250, 255);
            default -> new Color(255, 205, 100);
        };
    }

    /** Core disc plus a thin spectral halo ring (drawn outside the core, not under it). */
    private static void drawStarBody(Graphics2D g2, double sx, double sy, float coreR, Color core, BodyInfo body) {
        if (coreR <= 0f || !Double.isFinite(sx) || !Double.isFinite(sy)) {
            return;
        }
        float cx = (float) sx;
        float cy = (float) sy;
        boolean neutron = isNeutronStar(body);
        float haloPad = neutron ? Math.max(1.6f, coreR * 0.55f) : Math.max(1.35f, coreR * 0.42f);
        float haloR = coreR + haloPad;
        Color corona = neutron ? new Color(210, 228, 255, 175) : EdoUi.withAlpha(core, 175);
        g2.setColor(corona);
        g2.fill(new Ellipse2D.Double(cx - haloR, cy - haloR, haloR * 2.0, haloR * 2.0));
        g2.setColor(core);
        g2.fill(new Ellipse2D.Double(cx - coreR, cy - coreR, coreR * 2.0, coreR * 2.0));
        float ringR = coreR + haloPad * 0.55f;
        Stroke prevStroke = g2.getStroke();
        Color prevColor = g2.getColor();
        try {
            g2.setColor(neutron ? new Color(195, 218, 255, 215) : EdoUi.withAlpha(core.brighter(), 220));
            g2.setStroke(new BasicStroke(Math.max(0.75f, haloPad * 0.55f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Ellipse2D.Double(cx - ringR, cy - ringR, ringR * 2.0, ringR * 2.0));
        } finally {
            g2.setStroke(prevStroke);
            g2.setColor(prevColor);
        }
    }

    private static void drawSummaryClusterCenterDots(Graphics2D g2, MapLabelDrawPlan labelPlan, float bodyR,
            CompanionBranchLump companionLump) {
        if (companionLump != null || labelPlan == null || labelPlan.summaryClusterCentroids.isEmpty()) {
            return;
        }
        float r = Math.max(2.5f, bodyR * 0.52f);
        g2.setColor(MAP_SUMMARY_CLUSTER_DOT);
        for (float[] c : labelPlan.summaryClusterCentroids.values()) {
            if (c == null || c.length < 2 || !Double.isFinite(c[0]) || !Double.isFinite(c[1])) {
                continue;
            }
            g2.fill(new Ellipse2D.Double(c[0] - r, c[1] - r, r * 2.0, r * 2.0));
        }
    }

    private static boolean isGiantPlanetBody(BodyInfo b, boolean star) {
        if (b == null || star) {
            return false;
        }
        return mapLc(b.getPlanetClass()).contains("giant");
    }

    private static boolean hasPlanetaryRingsForMap(BodyInfo b, boolean star) {
        if (b == null || star || !b.isPlanetaryBodyForRingDisplay()) {
            return false;
        }
        if (!b.getRingSummaryLines().isEmpty()) {
            return true;
        }
        String rr = b.getRingReserveHumanized();
        return rr != null && !rr.isBlank();
    }

    private static float mapBodyDotRadiusPx(BodyDot d, float starR, float bodyR, double zoom, boolean showClusterDetail) {
        float r = d.star ? (d.loneCentralPrimary ? bodyR : starR) : bodyR;
        if (d.star && !d.loneCentralPrimary) {
            r *= 1.85f;
        }
        if (d.star) {
            r *= MAP_STAR_DOT_RADIUS_SCALE;
        }
        if (showClusterDetail && !d.star && !d.loneCentralPrimary && d.giantPlanet
                && zoom >= ZOOM_MAP_GIANT_BODY_DOT) {
            r *= 2f;
        }
        return r;
    }

    /** Zoomed-in: two tilted ellipses approximating ring bands in the schematic projection. */
    private static void drawPlanetaryRingsDecor(Graphics2D g2, double sx, double sy, float bodyRadiusPx) {
        if (bodyRadiusPx <= 0f || !Double.isFinite(sx) || !Double.isFinite(sy)) {
            return;
        }
        Stroke prevStroke = g2.getStroke();
        Color prevColor = g2.getColor();
        try {
            float cx = (float) sx;
            float cy = (float) sy;
            float rw = Math.max(5f, bodyRadiusPx * 2.85f);
            float rh = Math.max(2f, bodyRadiusPx * 0.92f);
            g2.setColor(MAP_PLANETARY_RING_OUTER);
            g2.setStroke(new BasicStroke(Math.max(1.05f, bodyRadiusPx * 0.14f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Ellipse2D.Float(cx - rw, cy - rh, rw * 2f, rh * 2f));
            g2.setColor(MAP_PLANETARY_RING_INNER);
            float rw2 = Math.max(3.5f, bodyRadiusPx * 2.05f);
            float rh2 = Math.max(1.4f, bodyRadiusPx * 0.62f);
            g2.setStroke(new BasicStroke(Math.max(0.95f, bodyRadiusPx * 0.11f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Ellipse2D.Float(cx - rw2, cy - rh2, rw2 * 2f, rh2 * 2f));
        } finally {
            g2.setStroke(prevStroke);
            g2.setColor(prevColor);
        }
    }

    private static String resolveMapSystemName(Map<Integer, BodyInfo> bodies) {
        if (bodies == null) {
            return null;
        }
        for (BodyInfo b : bodies.values()) {
            if (b == null) {
                continue;
            }
            String sys = b.getStarSystem();
            if (sys != null && !sys.isBlank()) {
                return sys.trim();
            }
        }
        return null;
    }

    private int mapResolvedParent(int bodyId) {
        return mapModel != null ? mapModel.resolveParentBodyId(bodyId) : -1;
    }

    private Map<Integer, Integer> mapResolvedParents() {
        return mapModel != null ? mapModel.resolvedParentByBodyId() : Map.of();
    }

    private static int resolvedParentFromMap(int bodyId, Map<Integer, Integer> resolvedParents) {
        if (resolvedParents == null) {
            return -1;
        }
        Integer p = resolvedParents.get(Integer.valueOf(bodyId));
        return p != null ? p.intValue() : -1;
    }

    private static int schematicRootBodyId(Map<Integer, BodyInfo> bodies, Map<Integer, Integer> resolvedParents) {
        if (resolvedParents != null) {
            for (Map.Entry<Integer, Integer> e : resolvedParents.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && e.getValue().intValue() < 0) {
                    BodyInfo b = bodies != null ? bodies.get(e.getKey()) : null;
                    if (b != null && SystemMapRules.isMapStellarBody(b)
                            && SystemOrbitGeometry.isPrimaryStarBodyByName(b)) {
                        return e.getKey().intValue();
                    }
                }
            }
        }
        return bodies != null ? SystemOrbitGeometry.primaryAnchorBodyMapKey(bodies) : 0;
    }

    private boolean subsystemHubLump(double visibleLsMinAxis, BodyDot d) {
        if (d == null || !subsystemHubLumpBodyIds.contains(Integer.valueOf(d.bodyId))) {
            return false;
        }
        if (zoomFactor >= ZOOM_SUBSYSTEM_HUB_DETAIL - 1e-6) {
            return false;
        }
        return !Double.isFinite(visibleLsMinAxis)
                || visibleLsMinAxis > SUBSYSTEM_CLUSTER_DETAIL_VISIBLE_LS;
    }

    /** True when {@code bodyId} has FSS/journal exobiology on the body itself (not “any moon under hub”). */
    private boolean mapBodyHasOwnExobiology(int bodyId) {
        if (orbitGeomBodies == null) {
            return false;
        }
        BodyInfo b = orbitGeomBodies.get(Integer.valueOf(bodyId));
        return b != null && b.showsExobiologyLeafIndicator();
    }

    /** Any direct or nested descendant of a subsystem hub carries exobiology. */
    private boolean subsystemHubHasExobiologyDescendant(int hubId) {
        if (orbitGeomBodies == null || hubId < 0) {
            return false;
        }
        for (Map.Entry<Integer, BodyInfo> e : orbitGeomBodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getKey().intValue() == hubId) {
                continue;
            }
            if (!isDescendantOfBody(e.getKey().intValue(), hubId)) {
                continue;
            }
            if (e.getValue().showsExobiologyLeafIndicator()) {
                return true;
            }
        }
        return false;
    }

    private boolean isDescendantOfBody(int bodyId, int ancestorId) {
        if (mapModel == null || bodyId < 0 || ancestorId < 0) {
            return false;
        }
        int cur = bodyId;
        for (int guard = 0; guard < 32 && cur >= 0; guard++) {
            if (cur == ancestorId) {
                return true;
            }
            cur = mapModel.resolveParentBodyId(cur);
        }
        return false;
    }

    /**
     * Zoomed-out subsystem lump: only the hub dot is drawn; moons and their orbit strokes stay hidden until cluster
     * detail zoom.
     */
    private int activeSubsystemLumpHubId(int bodyId, double visibleLsMinAxis) {
        int cur = bodyId;
        for (int guard = 0; guard < 32 && cur >= 0; guard++) {
            if (subsystemHubLumpBodyIds.contains(Integer.valueOf(cur))) {
                for (BodyDot d : dots) {
                    if (d != null && d.bodyId == cur && subsystemHubLump(visibleLsMinAxis, d)) {
                        return cur;
                    }
                }
            }
            if (mapModel == null) {
                break;
            }
            cur = mapModel.resolveParentBodyId(cur);
        }
        return -1;
    }

    private boolean hideDotForSubsystemLumpView(BodyDot d, double visibleLsMinAxis) {
        if (d == null || mapShowClusterDetail(visibleLsMinAxis)) {
            return false;
        }
        int hubId = activeSubsystemLumpHubId(d.bodyId, visibleLsMinAxis);
        return hubId >= 0 && d.bodyId != hubId;
    }

    private boolean skipOrbitPolylineForSubsystemLump(OrbitPolylineWorldXY poly, double visibleLsMinAxis,
            boolean showClusterDetail) {
        if (showClusterDetail || poly == null || poly.bodyId <= 0 || !isMoonOrbitPolyline(poly.bodyId)) {
            return false;
        }
        int hubId = activeSubsystemLumpHubId(poly.bodyId, visibleLsMinAxis);
        return hubId >= 0 && poly.bodyId != hubId;
    }

    /**
     * Subsystem hub detail: individual moon orbits instead of lump rings. Uses {@link #ZOOM_SUBSYSTEM_HUB_DETAIL}
     * (× fit) because wide-binary layout spans make the raw visible-Ls path unreachable before deep zoom.
     */
    private boolean mapShowClusterDetail(double visibleLsMinAxis) {
        if (zoomFactor >= ZOOM_SUBSYSTEM_HUB_DETAIL - 1e-6) {
            return true;
        }
        return Double.isFinite(visibleLsMinAxis)
                && visibleLsMinAxis <= SUBSYSTEM_CLUSTER_DETAIL_VISIBLE_LS;
    }

    /** Moon designations ({@code A 2 a}); requires deep zoom, not merely subsystem cluster detail. */
    private boolean mapShowMoonLabels(double visibleLsMinAxis) {
        return mapShowAllBodyLabels(visibleLsMinAxis);
    }

    /** All body labels (including moons); below this only stars and {@link SystemMapModel#isOrbitRevolutionCenter}. */
    private boolean mapShowAllBodyLabels(double visibleLsMinAxis) {
        if (zoomFactor >= ZOOM_SHOW_ALL_BODY_LABELS - 1e-6) {
            return true;
        }
        return Double.isFinite(visibleLsMinAxis)
                && visibleLsMinAxis <= SUBSYSTEM_MOON_LABEL_VISIBLE_LS;
    }

    /** Screen-space radius (px) of the outer blue subsystem-hub ring (twin-ring cue for moon hosts). */
    private static float subsystemMoonHubRingOuterRadiusPx(float bodyRadiusPx) {
        if (bodyRadiusPx <= 0f) {
            return MAP_SUBSYSTEM_HUB_RING_INNER_PAD_PX + MAP_SUBSYSTEM_HUB_RING_OUTER_STEP_PX;
        }
        float innerR = bodyRadiusPx + MAP_SUBSYSTEM_HUB_RING_INNER_PAD_PX;
        float gap = Math.max(MAP_SUBSYSTEM_HUB_RING_OUTER_STEP_PX,
                MAP_SUBSYSTEM_HUB_RING_MIN_OUTER_GAP_PX - 0.22f * innerR);
        return innerR + gap;
    }

    /**
     * Inner blue hub ring: halfway between the tight inner baseline (dot + pad) and the outer ring so both read
     * clearly at all zooms.
     */
    private static float subsystemMoonHubRingInnerRadiusPx(float bodyRadiusPx) {
        float innerBase = (bodyRadiusPx > 0f ? bodyRadiusPx : 0f) + MAP_SUBSYSTEM_HUB_RING_INNER_PAD_PX;
        float outerR = subsystemMoonHubRingOuterRadiusPx(bodyRadiusPx);
        return 0.5f * (innerBase + outerR);
    }

    /**
     * Twin thin orbit-blue circles for moon-host hubs in {@link #subsystemHubLump} (zoomed-out lump view). Zoomed-in
     * subsystem detail draws per-moon orbit strokes instead.
     */
    private void drawSubsystemMoonHubDoubleRing(Graphics2D g2, double sx, double sy, float bodyRadiusPx) {
        if (mapScaleMode.trueScale() || bodyRadiusPx <= 0f || !Double.isFinite(sx) || !Double.isFinite(sy)) {
            return;
        }
        Stroke prevStroke = g2.getStroke();
        Color prevColor = g2.getColor();
        try {
            float cx = (float) sx;
            float cy = (float) sy;
            float innerR = subsystemMoonHubRingInnerRadiusPx(bodyRadiusPx);
            float outerR = subsystemMoonHubRingOuterRadiusPx(bodyRadiusPx);
            /* Outer first so the gap reads clearly; slightly lighter outer helps at AA / far zoom. */
            g2.setStroke(new BasicStroke(1.12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(118, 176, 228, 236));
            g2.draw(new Ellipse2D.Double(cx - outerR, cy - outerR, outerR * 2.0, outerR * 2.0));
            g2.setStroke(new BasicStroke(1.02f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(105, 162, 218, 245));
            g2.draw(new Ellipse2D.Double(cx - innerR, cy - innerR, innerR * 2.0, innerR * 2.0));
        } finally {
            g2.setStroke(prevStroke);
            g2.setColor(prevColor);
        }
    }

    /**
     * True when this body’s short-name label is drawn: zoomed-out clusters show only the hub label; moons need a
     * closer view than majors.
     */
    private boolean bodyDotLabelWouldDraw(BodyDot d, boolean showClusterDetail, boolean showMoonLabels,
            boolean showAllBodyLabels, CompanionBranchLump companionLump) {
        if (d.label == null || d.label.isEmpty()) {
            return false;
        }
        /*
         * Wide-zoom companion lump (BCD 2–5): twin-ring hub only — no summary text until subsystem-detail zoom
         * (individual B/C/D star labels stay on via the star branch below).
         */
        if (companionLump != null && companionLump.contains(d.bodyId)) {
            return false;
        }
        if (d.moon && !showMoonLabels) {
            return false;
        }
        if (showAllBodyLabels) {
            return true;
        }
        if (d.star || d.primaryStarAsterisk) {
            return true;
        }
        if (mapModel != null && mapModel.isPrimaryBranchBody(d.bodyId) && !d.moon) {
            return true;
        }
        if (showClusterDetail && mapModel != null && mapModel.isOrbitRevolutionCenter(d.bodyId)) {
            return true;
        }
        if (!showClusterDetail && mapModel != null && !d.star) {
            if (mapModel.labelVisibleWhenZoomedOut(d.bodyId, d.star, d.moon, d.soleOrbitCluster)) {
                return true;
            }
        }
        if (!showClusterDetail && orbitGeomBodies != null && !subsystemHubLumpBodyIds.isEmpty()) {
            int outerHub = outermostSubsystemLumpHub(d.bodyId, orbitGeomBodies, subsystemHubLumpBodyIds);
            if (outerHub >= 0 && d.bodyId == outerHub) {
                return true;
            }
            if (outerHub >= 0 && d.bodyId != outerHub) {
                return false;
            }
        }
        return false;
    }

    /** Map label for stars: branch letter ({@code A}, {@code B}), not the full system name on the primary. */
    private static String starMapLabel(BodyInfo b, int mapKey, int primaryAnch) {
        String letter = starBranchLetterFromName(b);
        if (letter != null) {
            return letter;
        }
        if (mapKey == primaryAnch || isPrimaryStarBody(b)) {
            return "A";
        }
        return labelFor(b, mapKey);
    }

    /** Branch designation for planets/moons on the map ({@code A 3}, {@code B 4 a}), matching the System tab. */
    private static String planetMapLabel(BodyInfo b, int mapKey) {
        String s = firstNonBlankName(b.getShortName(), b.getBodyName());
        if (s != null && !s.isBlank()) {
            s = s.trim();
            Matcher desig = TRAILING_STAR_BODY_DESIGNATION.matcher(s);
            if (desig.find()) {
                StringBuilder label = new StringBuilder();
                label.append(desig.group(1).toUpperCase(Locale.ROOT));
                label.append(' ').append(desig.group(2));
                String moon = desig.group(3);
                if (moon != null && !moon.isEmpty()) {
                    label.append(' ').append(moon);
                }
                return label.toString();
            }
            Matcher tailNum = Pattern.compile("\\s+(\\d+)\\s*$").matcher(s);
            if (tailNum.find()) {
                return tailNum.group(1);
            }
        }
        return labelFor(b, mapKey);
    }

    private static String starBranchLetterFromName(BodyInfo b) {
        if (b == null) {
            return null;
        }
        String s = b.getShortName();
        if (s == null || s.isBlank()) {
            s = b.getBodyName();
        }
        if (s == null || s.isBlank()) {
            return null;
        }
        s = s.trim();
        if (TRAILING_STAR_BODY_DESIGNATION.matcher(s).find()) {
            return null;
        }
        Matcher m = STAR_BRANCH_LETTER_TAIL.matcher(s);
        if (m.find()) {
            return m.group(1).toUpperCase(Locale.ROOT);
        }
        return null;
    }

    /**
     * Outermost lump hub for a body: {@code B} for {@code B 3 a}, not the nested giant hub {@code B 3}, so only the
     * branch star label shows when zoomed out. Planets are not in {@code lumpHubs} (only parents are); walk ancestors.
     */
    private int outermostSubsystemLumpHub(int bodyId, Map<Integer, BodyInfo> bodies, Set<Integer> lumpHubs) {
        if (bodyId < 0 || bodies == null || lumpHubs == null || lumpHubs.isEmpty()) {
            return -1;
        }
        Map<Integer, Integer> resolvedParents = mapResolvedParents();
        int hub = nearestLumpHubAncestor(bodyId, lumpHubs, resolvedParents);
        if (hub < 0) {
            return -1;
        }
        return outermostLumpHubFromHub(hub, lumpHubs, resolvedParents);
    }

    /** Nearest orbit parent (walking up) that is a subsystem lump hub, or {@code -1}. */
    private int nearestLumpHubAncestor(int bodyId, Set<Integer> lumpHubs, Map<Integer, Integer> resolvedParents) {
        int cur = bodyId;
        for (int guard = 0; guard < 48; guard++) {
            int p = resolvedParentFromMap(cur, resolvedParents);
            if (p < 0) {
                return lumpHubs.contains(Integer.valueOf(cur)) ? cur : -1;
            }
            if (lumpHubs.contains(Integer.valueOf(p))) {
                return p;
            }
            cur = p;
        }
        return -1;
    }

    /** From a hub id, walk up through lump hubs to the topmost (e.g. star {@code B}, not giant {@code B 3}). */
    private int outermostLumpHubFromHub(int hubId, Set<Integer> lumpHubs, Map<Integer, Integer> resolvedParents) {
        int cur = hubId;
        int outermost = hubId;
        for (int guard = 0; guard < 48; guard++) {
            int p = resolvedParentFromMap(cur, resolvedParents);
            if (p < 0 || !lumpHubs.contains(Integer.valueOf(p))) {
                break;
            }
            outermost = p;
            cur = p;
        }
        return outermost;
    }

    /**
     * When a cluster is lumped, place the hub label outside the member centroid (e.g. {@code B} above the blob, not on it).
     */
    private float[] lumpHubClusterLabelAnchor(List<BodyDot> dots, int hubId, float hubSx, float hubSy,
            float hubR, int hubBodyId, String labelForSlot, int labelWidthPx, FontMetrics fm,
            Map<Integer, BodyInfo> bodies, Set<Integer> lumpHubs, double vcx, double vcy, double scale, double availW,
            double availH, double plotCx, double plotCy, boolean hubScreenLocked) {
        double sumX = 0.0;
        double sumY = 0.0;
        int n = 0;
        if (dots != null && bodies != null && lumpHubs != null) {
            for (BodyDot d : dots) {
                if (d == null) {
                    continue;
                }
                if (outermostSubsystemLumpHub(d.bodyId, bodies, lumpHubs) != hubId) {
                    continue;
                }
                boolean lock = hubScreenLocked && d.bodyId == hubId;
                double[] xy = bodyDotScreenMetres(d, lock, vcx, vcy, scale, availW, availH, plotCx, plotCy);
                if (Double.isFinite(xy[0]) && Double.isFinite(xy[1])) {
                    sumX += xy[0];
                    sumY += xy[1];
                    n++;
                }
            }
        }
        if (n <= 0) {
            return bodyLabelAnchor(hubSx, hubSy, hubR, hubBodyId, labelForSlot, labelWidthPx, fm, false);
        }
        float mx = (float) (sumX / n);
        float my = (float) (sumY / n);
        float dx = hubSx - mx;
        float dy = hubSy - my;
        double len = Math.hypot(dx, dy);
        float gap = Math.max(SUBSYSTEM_LUMP_HUB_LABEL_GAP_PX, hubR + 12f);
        if (len < 6.0) {
            return bodyLabelAnchor(hubSx, hubSy, hubR + gap * 0.4f, hubBodyId, labelForSlot, labelWidthPx, fm, false);
        }
        dx = (float) (dx / len);
        dy = (float) (dy / len);
        float vTweak = fm.getHeight() * 0.32f;
        return new float[] {
                hubSx + dx * gap,
                hubSy + dy * gap - vTweak
        };
    }

    /**
     * Nearest ancestor of {@code bodyId} (possibly itself) that hosts a child cluster and therefore appears as a
     * zoomed-out subsystem hub on the map; {@code -1} if none.
     */
    private int resolveLumpSubsystemHubContaining(int bodyId, Set<Integer> lumpHubs) {
        if (bodyId < 0 || lumpHubs == null || lumpHubs.isEmpty()) {
            return -1;
        }
        if (lumpHubs.contains(Integer.valueOf(bodyId))) {
            return bodyId;
        }
        Map<Integer, Integer> resolvedParents = mapResolvedParents();
        int curId = bodyId;
        for (int guard = 0; guard < 48; guard++) {
            int p = resolvedParentFromMap(curId, resolvedParents);
            if (p < 0) {
                return -1;
            }
            if (lumpHubs.contains(Integer.valueOf(p))) {
                return p;
            }
            curId = p;
        }
        return -1;
    }

    private static double[] bodyDotScreenMetres(BodyDot d, boolean lockHub, double vcx, double vcy, double scale,
            double availW, double availH, double plotCx, double plotCy) {
        double sx = lockHub ? plotCx : PAD + availW / 2.0 + (d.wx - vcx) * scale;
        double sy = lockHub ? plotCy : PAD + availH / 2.0 - (d.wy - vcy) * scale;
        return new double[] { sx, sy };
    }

    private static void drawOutlinedCommanderTriangleGlyph(Graphics2D g2, float baselineX, float baselineY, Font font) {
        Font prev = g2.getFont();
        try {
            g2.setFont(font);
            drawCommanderTriangleGlyphAtBaseline(g2, MAP_COMMANDER_TRIANGLE_CHAR, baselineX, baselineY);
        } finally {
            g2.setFont(prev);
        }
    }

    /**
     * When the commander’s body has no map label (e.g. moon while zoomed out), draw a lone ▲ at the same kind of
     * offset from the dot as other body labels ({@link #bodyLabelAnchor}).
     */
    private void maybeDrawDetachedCommanderGlyph(Graphics2D g2, Font labelFont, FontMetrics labelFm,
            boolean showClusterDetail, boolean showMoonLabels, boolean showAllBodyLabels,
            CompanionBranchLump companionLump, float starR, float bodyR, float dotEm, double vcx, double vcy,
            double scale, double availW, double availH, double plotCx, double plotCy) {
        if (anchorBodyId == null || orbitGeomBodies == null) {
            return;
        }
        int cmdId = anchorBodyId.intValue();
        BodyDot cmd = findBodyDot(cmdId);
        if (cmd == null || cmd.label == null || cmd.label.isEmpty()) {
            return;
        }
        if (bodyDotLabelWouldDraw(cmd, showClusterDetail, showMoonLabels, showAllBodyLabels, companionLump)) {
            return;
        }
        boolean lockCmd = subsystemScreenLockHubId >= 0 && cmdId == subsystemScreenLockHubId;
        double[] cxy = bodyDotScreenMetres(cmd, lockCmd, vcx, vcy, scale, availW, availH, plotCx, plotCy);
        float cx = (float) cxy[0];
        float cy = (float) cxy[1];
        float rr = mapBodyDotRadiusPx(cmd, starR, bodyR, zoomFactor, showClusterDetail);
        int triW = labelFm.stringWidth(MAP_COMMANDER_TRIANGLE_CHAR);
        String triSlot = MAP_COMMANDER_TRIANGLE_CHAR + cmdId;
        float[] triLp = bodyLabelAnchor(cx, cy, rr, cmdId, triSlot, triW, labelFm, cmd.moon);
        Font triFont = labelFont.deriveFont(Font.PLAIN, Math.max(9f, labelFont.getSize2D() * 0.95f));
        drawOutlinedCommanderTriangleGlyph(g2, triLp[0], triLp[1], triFont);
    }

    private static int commanderPrefixedLabelWidth(FontMetrics fm, String nameBody) {
        if (nameBody == null || nameBody.isEmpty()) {
            return 0;
        }
        return fm.stringWidth(MAP_COMMANDER_TRIANGLE_CHAR) + MAP_COMMANDER_TRIANGLE_NAME_GAP_PX + fm.stringWidth(nameBody);
    }

    private float[] labelAnchorForBodyDot(float sx, float sy, float r, BodyDot d, String labelForSlot,
            int labelWidthPx, FontMetrics fm) {
        float[] spread = hierarchicalCompanionStarLabelAnchor(sx, sy, r, d, labelForSlot, labelWidthPx, fm);
        if (spread != null) {
            return spread;
        }
        return bodyLabelAnchor(sx, sy, r, d.bodyId, labelForSlot, labelWidthPx, fm, d.moon);
    }

    /**
     * B/C/D branch stars often share one screen pixel when the map is zoomed into the inner cluster — fan labels out.
     */
    private float[] hierarchicalCompanionStarLabelAnchor(float sx, float sy, float r, BodyDot d, String labelForSlot,
            int labelWidthPx, FontMetrics fm) {
        if (d == null || !d.star || labelForSlot == null || labelForSlot.length() != 1) {
            return null;
        }
        if (orbitGeomBodies == null || !SystemOrbitGeometry.isHierarchicalWideBinary(orbitGeomBodies)) {
            return null;
        }
        if (SystemOrbitGeometry.isWideBinaryPrimaryBranchBody(d.bodyId, orbitGeomBodies)) {
            return null;
        }
        char letter = Character.toUpperCase(labelForSlot.charAt(0));
        if (letter != 'B' && letter != 'C' && letter != 'D') {
            return null;
        }
        int slot = letter == 'B' ? 0 : (letter == 'C' ? 4 : 6);
        float gap = Math.max(16f, r + 12f);
        int wlab = labelWidthPx;
        int hlab = fm.getHeight();
        float vTweak = hlab * 0.32f;
        return switch (slot) {
            case 0 -> new float[] { sx + gap, sy - vTweak };
            case 4 -> new float[] { sx - wlab * 0.5f, sy - (gap * 0.55f + hlab * 0.58f) };
            default -> new float[] { sx - wlab - gap, sy + gap * 0.22f };
        };
    }

    private boolean isMoonOrbitPolyline(int bodyId) {
        if (bodyId <= 0 || mapModel == null || orbitGeomBodies == null) {
            return false;
        }
        BodyInfo b = orbitGeomBodies.get(Integer.valueOf(bodyId));
        return b != null && SystemOrbitGeometry.isMoonSatelliteBody(b, orbitGeomBodies);
    }

    /**
     * Drops huge schematic concentric rings (Null:2 hub, A-branch at star A, system barycentre ~3750 Ls) when the
     * viewport is zoomed into a subsystem so B+C+D labels and mutual rings stay readable.
     */
    private boolean skipOversizeSchematicRingForDetailView(OrbitPolylineWorldXY poly, double visibleLsMinAxis,
            double viewCenterWx, double viewCenterWy, double scale, double availW, double availH,
            boolean detailOrbits) {
        if (mapScaleMode.trueScale()) {
            return false;
        }
        if (poly == null || poly.wx == null || poly.wy == null || poly.wx.length < 3) {
            return false;
        }
        /* Per-body and mutual rings always draw — zoom must not hide subsystem orbits. */
        if (poly.bodyId > 0 || SystemOrbitGeometry.isPlanetBinaryMutualOrbitRingBodyId(poly.bodyId)) {
            return false;
        }
        double rLs = polylineApproxRadiusLs(poly);
        if (!Double.isFinite(rLs) || rLs < SystemOrbitGeometry.SCHEMATIC_RING_DETAIL_VIEW_MIN_RADIUS_LS) {
            return false;
        }
        /* Only cull legacy heliocentric-scale schematic strokes; trunk/mutual rings stay visible at all zoom. */
        final double giantSchematicRingMinLs = 10_000.0;
        if (rLs < giantSchematicRingMinLs) {
            return false;
        }
        if (!Double.isFinite(visibleLsMinAxis) || visibleLsMinAxis < 24.0) {
            return false;
        }
        double[] centerM = polylineApproxCenterM(poly);
        double distCenterLs = Math.hypot(centerM[0] - viewCenterWx, centerM[1] - viewCenterWy)
                / SystemOrbitGeometry.LIGHT_SECOND_METRES;
        boolean ringLargerThanViewport = visibleLsMinAxis < rLs * 1.05;
        boolean centerFarFromView = Double.isFinite(distCenterLs)
                && distCenterLs > rLs + visibleLsMinAxis * 0.35;
        boolean detailZoom = visibleLsMinAxis < rLs * SystemOrbitGeometry.SCHEMATIC_RING_HIDE_WHEN_VIEW_SPAN_FRAC_OF_RADIUS;
        return (ringLargerThanViewport || centerFarFromView) && detailZoom;
    }

    /**
     * When the companion branch is zoomed out, merge its major planets (e.g. BCD 2–5) into one lump hub with twin
     * orbit-blue rings instead of overlapping dots, polylines, and barycentre crosses. Summary text is deferred until
     * subsystem-detail zoom ({@link #mapShowClusterDetail}); branch stars B/C/D keep their single-letter labels.
     */
    private CompanionBranchLump buildCompanionBranchRevolutionLump(List<BodyDot> dots, boolean showClusterDetail,
            double visibleLsMinAxis) {
        if (showClusterDetail || mapModel == null || orbitGeomBodies == null || dots == null || dots.isEmpty()) {
            return null;
        }
        if (!mapModel.classification().wideBinary()
                || !SystemOrbitGeometry.isHierarchicalWideBinary(orbitGeomBodies)) {
            return null;
        }
        List<BodyDot> candidates = new ArrayList<>();
        for (BodyDot d : dots) {
            if (d == null || d.star || d.moon) {
                continue;
            }
            if (mapModel.isPrimaryBranchBody(d.bodyId)) {
                continue;
            }
            if (!mapModel.isOrbitRevolutionCenter(d.bodyId)) {
                continue;
            }
            /*
             * Hierarchical A vs BCD: only lump the companion-trunk giants (BCD n), not branch stars B/C/D or
             * mis-clustered A-branch planets that screen-close to the BCD hub.
             */
            if (SystemOrbitGeometry.isHierarchicalWideBinary(orbitGeomBodies)) {
                String lbl = d.label != null ? d.label.trim() : "";
                if (lbl.isEmpty() || !lbl.startsWith("BCD")) {
                    continue;
                }
            }
            candidates.add(d);
        }
        if (candidates.size() < 2) {
            return null;
        }
        float[] screenX = new float[candidates.size()];
        float[] screenY = new float[candidates.size()];
        double vcx = viewCenterWx;
        double vcy = viewCenterWy;
        int pw = getWidth();
        int ph = getHeight();
        if (pw <= 0 || ph <= 0) {
            return null;
        }
        int plotH = Math.max(88, ph - MAP_BOTTOM_INSET);
        double availW = pw - 2.0 * PAD;
        double availH = plotH - 2.0 * PAD;
        double scale = computeMapPlotScale(availW, availH, layoutSpanX, layoutSpanY);
        if (!Double.isFinite(scale) || scale <= 0.0) {
            return null;
        }
        for (int i = 0; i < candidates.size(); i++) {
            BodyDot d = candidates.get(i);
            screenX[i] = (float) (PAD + availW / 2.0 + (d.wx - vcx) * scale);
            screenY[i] = (float) (PAD + availH / 2.0 - (d.wy - vcy) * scale);
        }
        float lumpPx = Math.max(COMPANION_REVOLUTION_LUMP_SCREEN_PX_MIN, (float) (availW * 0.095));
        double lumpConnectLs = Double.isFinite(visibleLsMinAxis)
                ? Math.min(2_500.0, Math.max(220.0, visibleLsMinAxis * 0.14))
                : 400.0;
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        boolean[] used = new boolean[candidates.size()];
        CompanionBranchLump best = null;
        for (int i = 0; i < candidates.size(); i++) {
            if (used[i]) {
                continue;
            }
            List<BodyDot> members = new ArrayList<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(Integer.valueOf(i));
            used[i] = true;
            while (!queue.isEmpty()) {
                int cur = queue.removeFirst().intValue();
                members.add(candidates.get(cur));
                BodyDot curDot = candidates.get(cur);
                for (int j = 0; j < candidates.size(); j++) {
                    if (used[j]) {
                        continue;
                    }
                    BodyDot other = candidates.get(j);
                    double screenDist = Math.hypot(screenX[cur] - screenX[j], screenY[cur] - screenY[j]);
                    double worldDistLs = Math.hypot(curDot.wx - other.wx, curDot.wy - other.wy) / ls;
                    if (screenDist <= lumpPx || worldDistLs <= lumpConnectLs) {
                        used[j] = true;
                        queue.add(Integer.valueOf(j));
                    }
                }
            }
            if (members.size() < 2) {
                continue;
            }
            members = pruneCompanionLumpOutliers(members);
            if (members.size() < 2) {
                continue;
            }
            double sumX = 0.0;
            double sumY = 0.0;
            List<String> labels = new ArrayList<>();
            for (BodyDot d : members) {
                sumX += d.wx;
                sumY += d.wy;
                if (d.label != null && !d.label.isEmpty()) {
                    labels.add(d.label);
                }
            }
            double centroidWx = sumX / members.size();
            double centroidWy = sumY / members.size();
            double clusterRadiusLs = 0.0;
            Set<Integer> memberIds = new HashSet<>();
            for (BodyDot d : members) {
                memberIds.add(Integer.valueOf(d.bodyId));
                clusterRadiusLs = Math.max(clusterRadiusLs,
                        Math.hypot(d.wx - centroidWx, d.wy - centroidWy) / ls);
            }
            String summary = clusterSummaryLabel(labels);
            int hubBodyId = companionLumpHubBodyId(members, summary);
            CompanionBranchLump lump = new CompanionBranchLump(hubBodyId, centroidWx, centroidWy, clusterRadiusLs,
                    memberIds, summary);
            if (best == null || lump.memberBodyIds.size() > best.memberBodyIds.size()) {
                best = lump;
            }
        }
        return best;
    }

    /**
     * Wide-zoom screen clustering can glue distant trunk planets onto a tight subsystem core; drop {@code BCD 1} when
     * the lump already contains {@code BCD 2+} so the summary reads {@code BCD 2–5}.
     */
    private static List<BodyDot> pruneCompanionLumpOutliers(List<BodyDot> members) {
        if (members == null || members.size() < 3) {
            return members;
        }
        boolean hasBcd1 = false;
        boolean hasBcd2Plus = false;
        for (BodyDot d : members) {
            if (d == null || d.label == null) {
                continue;
            }
            String label = d.label.trim();
            if ("BCD 1".equals(label)) {
                hasBcd1 = true;
            } else if (label.startsWith("BCD ")) {
                hasBcd2Plus = true;
            }
        }
        if (!hasBcd1 || !hasBcd2Plus) {
            return members;
        }
        List<BodyDot> trimmed = new ArrayList<>(members.size());
        for (BodyDot d : members) {
            if (d != null && !"BCD 1".equals(d.label != null ? d.label.trim() : "")) {
                trimmed.add(d);
            }
        }
        return trimmed.size() >= 2 ? trimmed : members;
    }

    /** Hub id for summary label anchor ({@code BCD 2–5} → body {@code BCD 2}). */
    private static int companionLumpHubBodyId(List<BodyDot> members, String summaryLabel) {
        if (members == null || members.isEmpty()) {
            return -1;
        }
        java.util.regex.Matcher range = java.util.regex.Pattern.compile("^(.+?)\\s+(\\d+)\u2013(\\d+)$")
                .matcher(summaryLabel != null ? summaryLabel.trim() : "");
        if (range.matches()) {
            String stem = range.group(1);
            int minNum = Integer.parseInt(range.group(2));
            for (BodyDot d : members) {
                if (d.label == null) {
                    continue;
                }
                java.util.regex.Matcher one = java.util.regex.Pattern.compile(
                        "^" + java.util.regex.Pattern.quote(stem) + "\\s+(\\d+)(\\s+.*)?$").matcher(d.label.trim());
                if (one.matches() && Integer.parseInt(one.group(1)) == minNum) {
                    return d.bodyId;
                }
            }
        }
        int hub = Integer.MAX_VALUE;
        for (BodyDot d : members) {
            hub = Math.min(hub, d.bodyId);
        }
        return hub;
    }

    private void drawCompanionBranchLumpHub(Graphics2D g2, CompanionBranchLump lump, double vcx, double vcy,
            double scale, double availW, double availH, float bodyR) {
        if (lump == null || !Double.isFinite(lump.centroidWx) || !Double.isFinite(lump.centroidWy)) {
            return;
        }
        drawSubsystemHubRevolutionPathRing(g2, lump.hubBodyId, lump.centroidWx, lump.centroidWy, vcx, vcy, scale,
                availW, availH);
        double sx = PAD + availW / 2.0 + (lump.centroidWx - vcx) * scale;
        double sy = PAD + availH / 2.0 - (lump.centroidWy - vcy) * scale;
        if (!Double.isFinite(sx) || !Double.isFinite(sy)) {
            return;
        }
        float lr = Math.max(2.5f, bodyR * 0.52f);
        g2.setColor(MAP_PLANET_DEFAULT_DOT);
        g2.fill(new Ellipse2D.Double(sx - lr, sy - lr, lr * 2, lr * 2));
        drawSubsystemMoonHubDoubleRing(g2, sx, sy, bodyR);
        for (BodyDot d : dots) {
            if (d != null && d.bodyId == lump.hubBodyId && d.hasPlanetaryRings) {
                float ringDecorR = Math.max(bodyR * 0.92f, 4.5f);
                drawPlanetaryRingsDecor(g2, sx, sy, ringDecorR);
                break;
            }
        }
    }

    /**
     * Schematic orbit path for a subsystem hub: circle centred on the resolved parent through the hub (same geometry
     * as {@link SystemOrbitGeometry} schematic rings). Drawn at lump zoom so majors still show the arc they follow.
     * Not used in true scale — journal-radius circles there duplicate Kepler {@link #orbitLines} strokes and are not
     * hit-testable as orbit polylines.
     */
    private void drawSubsystemHubRevolutionPathRing(Graphics2D g2, int bodyId, double bodyWx, double bodyWy,
            double vcx, double vcy, double scale, double availW, double availH) {
        if (mapScaleMode.trueScale()) {
            return;
        }
        if (mapModel == null || bodyId < 0 || !Double.isFinite(bodyWx) || !Double.isFinite(bodyWy)) {
            return;
        }
        int parentId = mapModel.resolveParentBodyId(bodyId);
        if (parentId < 0) {
            return;
        }
        double px = mapModel.mapPlaneX(parentId);
        double py = mapModel.mapPlaneY(parentId);
        if (!Double.isFinite(px) || !Double.isFinite(py)) {
            return;
        }
        double radM = Math.hypot(bodyWx - px, bodyWy - py);
        if (!Double.isFinite(radM) || radM < SystemOrbitGeometry.LIGHT_SECOND_METRES) {
            return;
        }
        double cx = PAD + availW / 2.0 + (px - vcx) * scale;
        double cy = PAD + availH / 2.0 - (py - vcy) * scale;
        double rPx = radM * scale;
        if (!Double.isFinite(cx) || !Double.isFinite(cy) || !Double.isFinite(rPx) || rPx < 3.0) {
            return;
        }
        Stroke prevStroke = g2.getStroke();
        Color prevColor = g2.getColor();
        try {
            g2.setColor(new Color(110, 165, 220));
            g2.setStroke(new BasicStroke(1.15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f));
            g2.draw(new Ellipse2D.Double(cx - rPx, cy - rPx, rPx * 2.0, rPx * 2.0));
            if (!loggedHubRevolutionDrawTrace) {
                loggedHubRevolutionDrawTrace = true;
                double radLs = radM / SystemOrbitGeometry.LIGHT_SECOND_METRES;
                BodyInfo body = orbitGeomBodies != null ? orbitGeomBodies.get(Integer.valueOf(bodyId)) : null;
                String label = body != null && body.getShortName() != null ? body.getShortName() : "?" + bodyId;
                System.out.println(String.format(Locale.US,
                        "[EDO][OrbitMap][Draw] schematic hub revolution ring body=%s id=%d parent=%d radiusLs=%.1f"
                                + " (live distance; not an orbit polyline)",
                        label, bodyId, parentId, radLs));
            }
        } finally {
            g2.setStroke(prevStroke);
            g2.setColor(prevColor);
        }
    }

    /**
     * Wide-zoom companion lump hides planet-binary mutual strokes only when every body on that ring is merged into
     * the lump (e.g. BCD 2–5 around Null:49). Stellar mutual rings (B+C around Null:3) stay visible because branch
     * stars are never lump members. Branch / trunk schematic paths must stay visible too.
     */
    private boolean skipOrbitPolylineForCompanionLump(OrbitPolylineWorldXY poly, CompanionBranchLump lump,
            boolean detailOrbits) {
        if (lump == null || detailOrbits || poly == null) {
            return false;
        }
        if (!SystemOrbitGeometry.isPlanetBinaryMutualOrbitRingBodyId(poly.bodyId)) {
            return false;
        }
        if (orbitGeomBodies == null || orbitGeomBodies.isEmpty()) {
            return true;
        }
        int nullId = SystemOrbitGeometry.PLANET_BINARY_MUTUAL_ORBIT_RING_ID_BASE - poly.bodyId;
        int orbiting = 0;
        int inLump = 0;
        for (BodyInfo b : orbitGeomBodies.values()) {
            if (b == null || b.isScanBarycentreRow()) {
                continue;
            }
            if (b.getImmediateParentBodyId() != nullId) {
                continue;
            }
            orbiting++;
            if (lump.contains(b.getBodyId())) {
                inLump++;
            }
        }
        return orbiting > 0 && inLump == orbiting;
    }

    private static boolean suppressBarycentreMarkerForCompanionLump(double mapX, double mapY,
            CompanionBranchLump lump) {
        if (lump == null || !Double.isFinite(mapX) || !Double.isFinite(mapY)) {
            return false;
        }
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        double distLs = Math.hypot(mapX - lump.centroidWx, mapY - lump.centroidWy) / ls;
        return distLs <= lump.clusterRadiusLs + 80.0;
    }

    /** Scan-barycentre rows and planet-binary map keys — drawn as {@code +} on the map, not body dots. */
    private void drawBarycentreMarkers(Graphics2D g2, float dotEm, double vcx, double vcy, double scale, double availW,
            double availH, double plotCx, double plotCy, CompanionBranchLump companionLump) {
        if (mapModel == null) {
            return;
        }
        Map<Integer, double[]> positions = mapModel.positionsMetres();
        if (positions == null || positions.isEmpty()) {
            return;
        }
        int p0 = mapModel.projectionAxis0();
        int p1 = mapModel.projectionAxis1();
        int needLen = Math.max(p0, p1) + 1;
        Set<Integer> drawn = new HashSet<>();
        List<double[]> markers = new ArrayList<>();
        if (orbitGeomBodies != null) {
            for (Map.Entry<Integer, BodyInfo> e : orbitGeomBodies.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || !e.getValue().isScanBarycentreRow()) {
                    continue;
                }
                int scanKey = e.getKey().intValue();
                if (shouldSuppressSystemBarycentreMarker(scanKey, positions, p0, p1, needLen)) {
                    continue;
                }
                collectBarycentreMarker(markers, drawn, positions, scanKey, p0, p1, needLen);
            }
        }
        for (Map.Entry<Integer, double[]> e : positions.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int key = e.getKey().intValue();
            if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(key)) {
                int nullId = SystemOrbitGeometry.PLANET_BINARY_BARYCENTRE_MAP_KEY_BASE - key;
                if (orbitGeomBodies != null && orbitGeomBodies.containsKey(Integer.valueOf(nullId))) {
                    continue;
                }
                collectBarycentreMarker(markers, drawn, positions, key, p0, p1, needLen);
            }
        }
        if (markers.isEmpty()) {
            return;
        }
        float half = Math.max(3.5f, dotEm * 0.55f);
        Stroke prevStroke = g2.getStroke();
        Color prevColor = g2.getColor();
        try {
            g2.setStroke(new BasicStroke(1.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(140, 200, 255, 230));
            for (double[] m : markers) {
                if (suppressBarycentreMarkerForCompanionLump(m[0], m[1], companionLump)) {
                    continue;
                }
                double sx = PAD + availW / 2.0 + (m[0] - vcx) * scale;
                double sy = PAD + availH / 2.0 - (m[1] - vcy) * scale;
                if (!Double.isFinite(sx) || !Double.isFinite(sy)) {
                    continue;
                }
                float cx = (float) sx;
                float cy = (float) sy;
                g2.draw(new Line2D.Float(cx - half, cy, cx + half, cy));
                g2.draw(new Line2D.Float(cx, cy - half, cx, cy + half));
            }
        } finally {
            g2.setStroke(prevStroke);
            g2.setColor(prevColor);
        }
    }

    /**
     * Hides the root {@code Null:0} / system-barycentre cross when it sits on the sole star — the star dot is enough.
     */
    private boolean shouldSuppressSystemBarycentreMarker(int scanMapKey, Map<Integer, double[]> positions,
            int p0, int p1, int needLen) {
        if (mapModel == null || orbitGeomBodies == null || positions == null) {
            return false;
        }
        if (SystemOrbitGeometry.countMapStellarBodies(orbitGeomBodies) != 1) {
            return false;
        }
        if (SystemOrbitGeometry.isHierarchicalWideBinary(orbitGeomBodies)) {
            return false;
        }
        int central = mapModel.classification().schematicCentralStarId();
        if (central < 0) {
            central = SystemOrbitGeometry.schematicCentralStarMapKey(orbitGeomBodies);
        }
        if (central < 0) {
            return false;
        }
        double[] starPos = positions.get(Integer.valueOf(central));
        double[] baryPos = positions.get(Integer.valueOf(scanMapKey));
        if (starPos == null || baryPos == null || starPos.length < needLen || baryPos.length < needLen) {
            return scanMapKey == 0;
        }
        double sx = SystemOrbitGeometry.worldAxisMetres(starPos, p0);
        double sy = SystemOrbitGeometry.worldAxisMetres(starPos, p1);
        double bx = SystemOrbitGeometry.worldAxisMetres(baryPos, p0);
        double by = SystemOrbitGeometry.worldAxisMetres(baryPos, p1);
        double dLs = Math.hypot(bx - sx, by - sy) / SystemOrbitGeometry.LIGHT_SECOND_METRES;
        if (dLs < 120.0) {
            return true;
        }
        if (scanMapKey != 0) {
            return false;
        }
        return !SystemOrbitGeometry.isPlanetBinaryNullParentId(0, orbitGeomBodies);
    }

    private static void collectBarycentreMarker(List<double[]> markers, Set<Integer> drawn,
            Map<Integer, double[]> positions, int mapKey, int p0, int p1, int needLen) {
        if (!drawn.add(Integer.valueOf(mapKey))) {
            return;
        }
        double[] p = positions.get(Integer.valueOf(mapKey));
        if (p == null || p.length < needLen) {
            return;
        }
        double x = SystemOrbitGeometry.worldAxisMetres(p, p0);
        double y = SystemOrbitGeometry.worldAxisMetres(p, p1);
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return;
        }
        markers.add(new double[] { x, y });
    }

    /**
     * Screen-space label collision: fan out when there is room; otherwise one summary label for the cluster.
     */
    private MapLabelDrawPlan buildCollisionAwareLabelPlan(List<BodyDot> dots, boolean showClusterDetail,
            boolean showMoonLabels, boolean showAllBodyLabels, CompanionBranchLump companionLump, FontMetrics fm,
            double vcx, double vcy, double scale, double availW, double availH, double plotCx, double plotCy) {
        Map<Integer, float[]> anchors = new HashMap<>();
        Map<Integer, String> summaryTextByHubId = new HashMap<>();
        Set<Integer> suppressedBodyIds = new HashSet<>();
        Set<Integer> summaryClusterMemberIds = new HashSet<>();
        Map<Integer, float[]> summaryClusterCentroids = new HashMap<>();
        if (mapModel == null || dots == null || dots.isEmpty()) {
            return new MapLabelDrawPlan(anchors, summaryTextByHubId, suppressedBodyIds, summaryClusterMemberIds,
                    summaryClusterCentroids);
        }
        List<LabelSlot> slots = new ArrayList<>();
        for (BodyDot d : dots) {
            if (d == null || d.label == null || d.label.isEmpty()) {
                continue;
            }
            if (!bodyDotLabelWouldDraw(d, showClusterDetail, showMoonLabels, showAllBodyLabels, companionLump)) {
                continue;
            }
            if (d.star && d.label.length() == 1) {
                continue;
            }
            boolean lockHub = subsystemScreenLockHubId >= 0 && d.bodyId == subsystemScreenLockHubId;
            double[] xy = bodyDotScreenMetres(d, lockHub, vcx, vcy, scale, availW, availH, plotCx, plotCy);
            if (!Double.isFinite(xy[0]) || !Double.isFinite(xy[1])) {
                continue;
            }
            float r = Math.max(6f, fm.getHeight() * 0.35f);
            int wlab = fm.stringWidth(d.label);
            float[] spread = hierarchicalCompanionStarLabelAnchor((float) xy[0], (float) xy[1], r, d, d.label, wlab, fm);
            float[] anchor = spread != null ? spread
                    : bodyLabelAnchor((float) xy[0], (float) xy[1], r, d.bodyId, d.label, wlab, fm, d.moon);
            slots.add(new LabelSlot(d, anchor[0], anchor[1], wlab, (float) xy[0], (float) xy[1]));
        }
        if (slots.size() < 2) {
            return new MapLabelDrawPlan(anchors, summaryTextByHubId, suppressedBodyIds, summaryClusterMemberIds,
                    summaryClusterCentroids);
        }
        final float pad = Math.max(4f, fm.getHeight() * 0.12f);
        boolean[] used = new boolean[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            if (used[i]) {
                continue;
            }
            List<Integer> cluster = new ArrayList<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(Integer.valueOf(i));
            used[i] = true;
            while (!queue.isEmpty()) {
                int cur = queue.removeFirst().intValue();
                cluster.add(Integer.valueOf(cur));
                java.awt.geom.Rectangle2D.Float curRect = slots.get(cur).screenRect(fm, pad);
                int clusterBranchKey = labelBranchGroupKey(slots.get(cur).dot.bodyId);
                for (int j = 0; j < slots.size(); j++) {
                    if (used[j]) {
                        continue;
                    }
                    if (labelBranchGroupKey(slots.get(j).dot.bodyId) != clusterBranchKey) {
                        continue;
                    }
                    if (labelRectsOverlap(curRect, slots.get(j).screenRect(fm, pad))) {
                        used[j] = true;
                        queue.add(Integer.valueOf(j));
                    }
                }
            }
            if (cluster.size() < 2) {
                continue;
            }
            cluster.sort(Comparator.comparingInt(idx -> slots.get(idx.intValue()).dot.bodyId));
            List<String> labels = new ArrayList<>();
            for (Integer idxObj : cluster) {
                labels.add(slots.get(idxObj.intValue()).dot.label);
            }
            int n = cluster.size();
            boolean fanOk = true;
            Map<Integer, float[]> fanAnchors = new HashMap<>();
            for (int slot = 0; slot < n; slot++) {
                LabelSlot s = slots.get(cluster.get(slot).intValue());
                float r = Math.max(6f, fm.getHeight() * 0.28f);
                float[] anchor = revolutionCenterClusterSlotAnchor(s.dotScreenX(), s.dotScreenY(), r, slot, n,
                        s.labelWidthPx, fm);
                fanAnchors.put(Integer.valueOf(s.dot.bodyId), anchor);
            }
            for (int a = 0; a < n && fanOk; a++) {
                LabelSlot sa = slots.get(cluster.get(a).intValue());
                float[] aa = fanAnchors.get(Integer.valueOf(sa.dot.bodyId));
                java.awt.geom.Rectangle2D.Float ra = anchorRect(aa[0], aa[1], sa.labelWidthPx, fm, pad);
                for (int b = a + 1; b < n; b++) {
                    LabelSlot sb = slots.get(cluster.get(b).intValue());
                    float[] ab = fanAnchors.get(Integer.valueOf(sb.dot.bodyId));
                    java.awt.geom.Rectangle2D.Float rb = anchorRect(ab[0], ab[1], sb.labelWidthPx, fm, pad);
                    if (labelRectsOverlap(ra, rb)) {
                        fanOk = false;
                        break;
                    }
                }
            }
            if (fanOk) {
                anchors.putAll(fanAnchors);
                continue;
            }
            boolean primaryBranchCluster = false;
            for (Integer idxObj : cluster) {
                if (mapModel.isPrimaryBranchBody(slots.get(idxObj.intValue()).dot.bodyId)) {
                    primaryBranchCluster = true;
                    break;
                }
            }
            if (primaryBranchCluster) {
                for (int slot = 0; slot < n; slot++) {
                    LabelSlot c = slots.get(cluster.get(slot).intValue());
                    float r = Math.max(6f, fm.getHeight() * 0.28f);
                    float[] anchor = revolutionCenterClusterSlotAnchor(c.dotScreenX(), c.dotScreenY(), r, slot, n,
                            c.labelWidthPx, fm);
                    anchors.put(Integer.valueOf(c.dot.bodyId), anchor);
                }
                continue;
            }
            LabelSlot hubSlot = slots.get(cluster.get(0).intValue());
            int hubId = hubSlot.dot.bodyId;
            String summary = clusterSummaryLabel(labels);
            int summaryW = fm.stringWidth(summary);
            float[] hubAnchor = revolutionCenterClusterSlotAnchor(hubSlot.dotScreenX(), hubSlot.dotScreenY(),
                    Math.max(6f, fm.getHeight() * 0.28f), 0, 1, summaryW, fm);
            summaryTextByHubId.put(Integer.valueOf(hubId), summary);
            anchors.put(Integer.valueOf(hubId), hubAnchor);
            summaryClusterCentroids.put(Integer.valueOf(hubId),
                    new float[] { hubSlot.dotScreenX(), hubSlot.dotScreenY() });
            for (Integer idxObj : cluster) {
                int memberId = slots.get(idxObj.intValue()).dot.bodyId;
                summaryClusterMemberIds.add(Integer.valueOf(memberId));
                if (memberId != hubId) {
                    suppressedBodyIds.add(Integer.valueOf(memberId));
                }
            }
        }
        return new MapLabelDrawPlan(anchors, summaryTextByHubId, suppressedBodyIds, summaryClusterMemberIds,
                summaryClusterCentroids);
    }

    /**
     * Screen-space label clusters must not merge A-branch with B/C/D-branch bodies — otherwise an {@code A 2–4}
     * summary can anchor beside star C when rects overlap at wide zoom.
     */
    private int labelBranchGroupKey(int bodyId) {
        if (mapModel == null || orbitGeomBodies == null) {
            return bodyId;
        }
        if (mapModel.isPrimaryBranchBody(bodyId)) {
            for (Map.Entry<Integer, BodyInfo> e : orbitGeomBodies.entrySet()) {
                if (e.getKey() != null && e.getValue() != null
                        && SystemOrbitGeometry.isWideBinaryPrimaryBranchBody(e.getKey().intValue(), orbitGeomBodies)) {
                    return e.getKey().intValue();
                }
            }
        }
        BodyInfo self = orbitGeomBodies.get(Integer.valueOf(bodyId));
        if (self != null && SystemMapRules.isMapStellarBody(self)) {
            return bodyId;
        }
        if (self != null) {
            String shortName = self.getShortName();
            if (shortName != null && shortName.startsWith("BCD")) {
                for (Map.Entry<Integer, BodyInfo> e : orbitGeomBodies.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null
                            && "B".equalsIgnoreCase(e.getValue().getShortName())) {
                        return e.getKey().intValue();
                    }
                }
            }
            String branchLetter = SystemOrbitGeometry.designationBranchLetter(self);
            if (branchLetter != null && branchLetter.length() == 1) {
                for (Map.Entry<Integer, BodyInfo> e : orbitGeomBodies.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null
                            && branchLetter.equalsIgnoreCase(e.getValue().getShortName())) {
                        return e.getKey().intValue();
                    }
                }
            }
        }
        int parent = mapModel.resolveParentBodyId(bodyId);
        if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(parent)) {
            return parent;
        }
        int branchStar = SystemMapRules.branchSchematicStarParentId(orbitGeomBodies, parent);
        if (branchStar >= 0) {
            return branchStar;
        }
        int walk = parent;
        for (int hop = 0; hop < 24 && walk >= 0; hop++) {
            BodyInfo pb = orbitGeomBodies.get(Integer.valueOf(walk));
            if (pb != null && SystemMapRules.isMapStellarBody(pb)) {
                return walk;
            }
            walk = mapModel.resolveParentBodyId(walk);
        }
        return bodyId;
    }

    private static boolean labelRectsOverlap(java.awt.geom.Rectangle2D.Float a, java.awt.geom.Rectangle2D.Float b) {
        return a != null && b != null && a.intersects(b);
    }

    private static java.awt.geom.Rectangle2D.Float anchorRect(float ax, float ay, int labelWidthPx, FontMetrics fm,
            float pad) {
        int h = fm.getHeight();
        return new java.awt.geom.Rectangle2D.Float(ax - pad, ay - fm.getAscent() - pad, labelWidthPx + pad * 2f,
                h + pad * 2f);
    }

    /** e.g. {@code BCD 2}, {@code BCD 5} → {@code BCD 2–5}; otherwise {@code Alpha ×3}. */
    private static String clusterSummaryLabel(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return "";
        }
        if (labels.size() == 1) {
            return labels.get(0);
        }
        java.util.regex.Pattern numTail = java.util.regex.Pattern.compile("^(.+?)\\s+(\\d+)(\\s+.*)?$");
        String commonStem = null;
        int minNum = Integer.MAX_VALUE;
        int maxNum = Integer.MIN_VALUE;
        int numericCount = 0;
        for (String raw : labels) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            java.util.regex.Matcher m = numTail.matcher(raw.trim());
            if (!m.matches()) {
                commonStem = null;
                break;
            }
            String stem = m.group(1);
            int num = Integer.parseInt(m.group(2));
            if (commonStem == null) {
                commonStem = stem;
            } else if (!commonStem.equals(stem)) {
                commonStem = null;
                break;
            }
            minNum = Math.min(minNum, num);
            maxNum = Math.max(maxNum, num);
            numericCount++;
        }
        if (commonStem != null && numericCount == labels.size()) {
            if (minNum == maxNum) {
                return commonStem + " " + minNum;
            }
            return commonStem + " " + minNum + "\u2013" + maxNum;
        }
        String shortest = labels.stream().filter(s -> s != null && !s.isBlank()).min(Comparator.comparingInt(String::length))
                .orElse(labels.get(0));
        return shortest + " \u00d7" + labels.size();
    }

    private static float[] revolutionCenterClusterSlotAnchor(float cx, float cy, float r, int slot, int clusterSize,
            int labelWidthPx, FontMetrics fm) {
        int wlab = labelWidthPx;
        int hlab = fm.getHeight();
        float gap = Math.max(14f, r + 10f);
        float vTweak = hlab * 0.32f;
        if (clusterSize <= 4) {
            return switch (slot) {
                case 0 -> new float[] { cx + gap, cy - vTweak };
                case 1 -> new float[] { cx - wlab - gap, cy - vTweak };
                case 2 -> new float[] { cx - wlab * 0.5f, cy - (gap * 0.55f + hlab * 0.58f) };
                default -> new float[] { cx - wlab - gap * 0.35f, cy + gap * 0.35f };
            };
        }
        int s = Math.floorMod(slot, 8);
        return switch (s) {
            case 0 -> new float[] { cx + gap, cy - vTweak };
            case 1 -> new float[] { cx - wlab - gap, cy - vTweak };
            case 2 -> new float[] { cx - wlab * 0.5f, cy - (gap + hlab * 0.55f) };
            case 3 -> new float[] { cx + gap * 0.35f, cy + gap * 0.35f };
            case 4 -> new float[] { cx - wlab - gap * 0.35f, cy + gap * 0.35f };
            case 5 -> new float[] { cx + gap, cy + gap * 0.25f };
            case 6 -> new float[] { cx - wlab - gap, cy + gap * 0.25f };
            default -> new float[] { cx - wlab * 0.5f, cy + gap + hlab * 0.35f };
        };
    }

    static final class CompanionBranchLump {
        final int hubBodyId;
        final double centroidWx;
        final double centroidWy;
        final double clusterRadiusLs;
        final Set<Integer> memberBodyIds;
        final String summaryLabel;

        CompanionBranchLump(int hubBodyId, double centroidWx, double centroidWy, double clusterRadiusLs,
                Set<Integer> memberBodyIds, String summaryLabel) {
            this.hubBodyId = hubBodyId;
            this.centroidWx = centroidWx;
            this.centroidWy = centroidWy;
            this.clusterRadiusLs = clusterRadiusLs;
            this.memberBodyIds = Set.copyOf(memberBodyIds);
            this.summaryLabel = summaryLabel != null ? summaryLabel : "";
        }

        boolean contains(int bodyId) {
            return memberBodyIds.contains(Integer.valueOf(bodyId));
        }
    }

    static final class MapLabelDrawPlan {
        final Map<Integer, float[]> anchors;
        final Map<Integer, String> summaryTextByHubId;
        final Set<Integer> suppressedBodyIds;
        /** All bodies in a summary-labelled cluster — individual dots are not drawn. */
        final Set<Integer> summaryClusterMemberIds;
        /** Screen position of the one small marker per summary cluster (hub id → cx, cy). */
        final Map<Integer, float[]> summaryClusterCentroids;

        MapLabelDrawPlan(Map<Integer, float[]> anchors, Map<Integer, String> summaryTextByHubId,
                Set<Integer> suppressedBodyIds, Set<Integer> summaryClusterMemberIds,
                Map<Integer, float[]> summaryClusterCentroids) {
            this.anchors = anchors;
            this.summaryTextByHubId = summaryTextByHubId;
            this.suppressedBodyIds = suppressedBodyIds;
            this.summaryClusterMemberIds = summaryClusterMemberIds != null ? summaryClusterMemberIds : Set.of();
            this.summaryClusterCentroids = summaryClusterCentroids != null ? summaryClusterCentroids : Map.of();
        }
    }

    private static final class LabelSlot {
        final BodyDot dot;
        final float anchorX;
        final float anchorY;
        final int labelWidthPx;
        final float dotSx;
        final float dotSy;

        LabelSlot(BodyDot dot, float anchorX, float anchorY, int labelWidthPx, float screenSx, float screenSy) {
            this.dot = dot;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.labelWidthPx = labelWidthPx;
            this.dotSx = screenSx;
            this.dotSy = screenSy;
        }

        float dotScreenX() {
            return dotSx;
        }

        float dotScreenY() {
            return dotSy;
        }

        java.awt.geom.Rectangle2D.Float screenRect(FontMetrics fm, float pad) {
            return anchorRect(anchorX, anchorY, labelWidthPx, fm, pad);
        }
    }

    private static double[] polylineApproxCenterM(OrbitPolylineWorldXY poly) {
        if (poly == null || poly.wx == null || poly.wy == null || poly.wx.length < 1) {
            return new double[] { Double.NaN, Double.NaN };
        }
        double cx = 0.0;
        double cy = 0.0;
        for (int i = 0; i < poly.wx.length; i++) {
            cx += poly.wx[i];
            cy += poly.wy[i];
        }
        return new double[] { cx / poly.wx.length, cy / poly.wy.length };
    }

    private static double polylineApproxRadiusLs(OrbitPolylineWorldXY poly) {
        if (poly == null || poly.wx == null || poly.wy == null || poly.wx.length < 3) {
            return Double.NaN;
        }
        double cx = 0.0;
        double cy = 0.0;
        for (int i = 0; i < poly.wx.length; i++) {
            cx += poly.wx[i];
            cy += poly.wy[i];
        }
        cx /= poly.wx.length;
        cy /= poly.wy.length;
        double maxR = 0.0;
        for (int i = 0; i < poly.wx.length; i++) {
            maxR = Math.max(maxR, Math.hypot(poly.wx[i] - cx, poly.wy[i] - cy));
        }
        return maxR / SystemOrbitGeometry.LIGHT_SECOND_METRES;
    }

    /**
     * Stagger label anchors so clustered bodies do not share one diagonal. Slot mixes body id and label text so
     * nearby moons (e.g. {@code 1 c} vs {@code 1 d}) rarely pick the same direction. Keeps text close to the dot.
     */
    private static float[] bodyLabelAnchor(float sx, float sy, float r, int bodyId, String labelForSlot, int labelWidthPx,
            FontMetrics fm, boolean moon) {
        int wlab = labelWidthPx;
        int hlab = fm.getHeight();
        int slot = Math.floorMod(bodyId * 17 + (labelForSlot != null ? labelForSlot.hashCode() : 0), 8);
        float gap = Math.max(2.5f, r * 0.48f + 2.5f);
        if (moon) {
            gap *= 0.78f;
        }
        float vTweak = hlab * 0.32f;
        switch (slot) {
            case 0:
                return new float[] { sx + gap, sy - vTweak };
            case 1:
                return new float[] { sx - wlab - gap, sy - vTweak };
            case 2:
                return new float[] { sx - wlab * 0.5f, sy - (gap * 0.62f + hlab * 0.55f) };
            case 3:
                return new float[] { sx + gap * 0.42f, sy - hlab * 0.36f };
            case 4:
                return new float[] { sx + gap * 0.55f, sy - (gap * 0.52f + hlab * 0.58f) };
            case 5:
                return new float[] { sx - wlab - gap * 0.55f, sy + gap * 0.34f };
            case 6:
                return new float[] { sx + gap * 0.92f, sy + gap * 0.28f };
            default:
                return new float[] { sx - wlab - gap * 0.92f, sy - (gap * 0.48f + hlab * 0.55f) };
        }
    }

    private void ensureExobiologyLeafIconSize(float dotEm) {
        int px = Math.max(18, Math.min(30, Math.round(dotEm * 2.1f)));
        if (px != exobiologyLeafIconPx) {
            exobiologyLeafIconPx = px;
            exobiologyLeafIcon = new LeafIcon(px, px);
        }
    }

    /**
     * Exobiology leaves: at subsystem lump zoom one leaf on the hub when any descendant has bio; at cluster detail
     * zoom individual leaves on bio bodies only (not on a hub that lacks bio).
     */
    private void drawExobiologyLeafMarkers(Graphics2D g2, double visibleLsMinAxis, boolean showClusterDetail,
            boolean showMoonLabels, boolean showAllBodyLabels, CompanionBranchLump companionLump, float starR,
            float bodyR, float dotEm, double vcx, double vcy, double scale, double availW, double availH, double plotCx,
            double plotCy) {
        ensureExobiologyLeafIconSize(dotEm);
        int leafW = exobiologyLeafIcon.getIconWidth();
        int leafH = exobiologyLeafIcon.getIconHeight();
        for (BodyDot d : dots) {
            if (d == null) {
                continue;
            }
            if (hideDotForSubsystemLumpView(d, visibleLsMinAxis)) {
                continue;
            }
            boolean lumpHub = subsystemHubLump(visibleLsMinAxis, d);
            boolean drawLeaf = lumpHub
                    ? subsystemHubHasExobiologyDescendant(d.bodyId)
                    : d.hasExobiology;
            if (!drawLeaf) {
                continue;
            }
            boolean lockHub = subsystemScreenLockHubId >= 0 && d.bodyId == subsystemScreenLockHubId;
            double[] cxy = bodyDotScreenMetres(d, lockHub, vcx, vcy, scale, availW, availH, plotCx, plotCy);
            float cx = (float) cxy[0];
            float cy = (float) cxy[1];
            float rr = lumpHub
                    ? Math.max(7.5f, subsystemMoonHubRingOuterRadiusPx(
                            mapBodyDotRadiusPx(d, starR, bodyR, zoomFactor, showClusterDetail)) + 2.5f)
                    : mapBodyDotRadiusPx(d, starR, bodyR, zoomFactor, showClusterDetail);
            boolean commanderHere = anchorBodyId != null && d.bodyId == anchorBodyId.intValue();
            String commanderSlotKey = null;
            if (commanderHere) {
                if (d.label != null && !d.label.isEmpty()
                        && bodyDotLabelWouldDraw(d, showClusterDetail, showMoonLabels, showAllBodyLabels,
                                companionLump)) {
                    commanderSlotKey = MAP_COMMANDER_TRIANGLE_CHAR + d.label;
                } else {
                    commanderSlotKey = MAP_COMMANDER_TRIANGLE_CHAR + d.bodyId;
                }
            }
            float[] lp = exobiologyLeafAnchor(cx, cy, rr, d.bodyId, leafW, leafH, commanderSlotKey,
                    lumpHub ? false : d.moon);
            exobiologyLeafIcon.paintIcon(this, g2, Math.round(lp[0]), Math.round(lp[1]));
        }
    }

    /**
     * Leaf anchor near the body dot; when the commander is on the same body, use the slot opposite the ▲ label
     * ({@link #bodyLabelAnchor}) so the two cues do not overlap.
     */
    private static float[] exobiologyLeafAnchor(float sx, float sy, float r, int bodyId, int leafW, int leafH,
            String commanderSlotKey, boolean moon) {
        int slot;
        if (commanderSlotKey != null) {
            int commanderSlot = Math.floorMod(bodyId * 17 + commanderSlotKey.hashCode(), 8);
            slot = (commanderSlot + 4) % 8;
        } else {
            slot = Math.floorMod(bodyId * 17 + ("bio:" + bodyId).hashCode(), 8);
        }
        float gap = Math.max(4f, r * 0.65f + 4f);
        if (moon) {
            gap *= 0.78f;
        }
        if (commanderSlotKey != null) {
            gap += Math.max(leafW, leafH) * 0.15f;
        }
        float vTweak = leafH * 0.12f;
        switch (slot) {
            case 0:
                return new float[] { sx + gap, sy - leafH - vTweak };
            case 1:
                return new float[] { sx - leafW - gap, sy - leafH - vTweak };
            case 2:
                return new float[] { sx - leafW * 0.5f, sy - (gap * 0.62f + leafH + vTweak) };
            case 3:
                return new float[] { sx + gap * 0.42f, sy - leafH * 0.85f - vTweak };
            case 4:
                return new float[] { sx + gap * 0.55f, sy - (gap * 0.52f + leafH + vTweak * 0.5f) };
            case 5:
                return new float[] { sx - leafW - gap * 0.55f, sy + gap * 0.28f };
            case 6:
                return new float[] { sx + gap * 0.92f, sy + gap * 0.22f };
            default:
                return new float[] { sx - leafW - gap * 0.92f, sy - (gap * 0.48f + leafH + vTweak * 0.5f) };
        }
    }


    private static void drawLabelOutlined(Graphics2D g2, String text, float x, float y) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Color fg = EdoUi.User.MAIN_TEXT;
        outlineLabelString(g2, text, x, y);
        g2.setColor(fg);
        g2.drawString(text, x, y);
    }

    /** Yellow ▲ with red outline plus body short name — commander location (no cyan ring). */
    private static void drawCommanderPrefixedBodyLabel(Graphics2D g2, String nameBody, float x, float y, FontMetrics fm) {
        if (nameBody == null || nameBody.isEmpty()) {
            return;
        }
        String tri = MAP_COMMANDER_TRIANGLE_CHAR;
        drawCommanderTriangleGlyphAtBaseline(g2, tri, x, y);
        int tw = fm.stringWidth(tri);
        float nameX = x + tw + MAP_COMMANDER_TRIANGLE_NAME_GAP_PX;
        outlineLabelString(g2, nameBody, nameX, y);
        g2.setColor(EdoUi.User.MAIN_TEXT);
        g2.drawString(nameBody, nameX, y);
    }

    /** Yellow ▲ with red outline (commander cue on map). */
    private static void drawCommanderTriangleGlyphAtBaseline(Graphics2D g2, String tri, float x, float y) {
        g2.setColor(MAP_COMMANDER_TRIANGLE_OUTLINE);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                g2.drawString(tri, x + dx, y + dy);
            }
        }
        g2.setColor(MAP_COMMANDER_TRIANGLE_FILL);
        g2.drawString(tri, x, y);
    }

    private static void outlineLabelString(Graphics2D g2, String s, float x, float y) {
        g2.setColor(new Color(0, 0, 0, 160));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    g2.drawString(s, x + dx, y + dy);
                }
            }
        }
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
            /* Only follow commander / highlight when that body is actually in the view window; otherwise zooming or
             * panning into another subsystem during playback would still snap to the highlight's parent hub. */
            Map<Integer, Integer> resolvedParents = mapResolvedParents();
            if (orbitDepthFromStar(hid, orbitGeomBodies, resolvedParents) >= 2 && visible.contains(Integer.valueOf(hid))) {
                hub = subsystemFocusKeyForBody(hid, orbitGeomBodies, resolvedParents);
            }
        }
        if (hub < 0) {
            if (pickedForHub.isEmpty()) {
                return null;
            }
            Map<Integer, Integer> resolvedParents = mapResolvedParents();
            hub = deepestCommonOrbitAncestor(pickedForHub, orbitGeomBodies, resolvedParents);
            int rootId = schematicRootBodyId(orbitGeomBodies, resolvedParents);
            if (hub == rootId && pickedForHub.size() > 1) {
                Set<Integer> withoutStar = new HashSet<>(pickedForHub);
                withoutStar.remove(Integer.valueOf(rootId));
                if (!withoutStar.isEmpty()) {
                    hub = deepestCommonOrbitAncestor(withoutStar, orbitGeomBodies, resolvedParents);
                }
            }
            if (hub == rootId && !pickedForHub.contains(Integer.valueOf(rootId))) {
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
     * Per mouse-wheel zoom-out step (below subsystem zoom): nudge {@link #viewCenterWx}/{@link #viewCenterWy} toward
     * the layout bounding-box centre (same framing as min-zoom snap) so wide / binary systems recentre between
     * branches. Blend ramps toward {@link #zoomMinFit} and gains an extra exponential pull when {@link #zoomFactor} is
     * near the floor (see {@link #WHEEL_SYSTEM_OUT_EXPO_K} / {@link #WHEEL_SYSTEM_OUT_EXPO_GAIN}).
     */
    private void nudgeViewCenterTowardSystemCentroidOnWheel(double scale) {
        if (dots.isEmpty()) {
            return;
        }
        Bounds bb = computeBounds(dots, shipKnown ? shipWx : Double.NaN, shipKnown ? shipWy : Double.NaN);
        double cx = (bb.minX + bb.maxX) * 0.5;
        double cy = (bb.minY + bb.maxY) * 0.5;
        double dwx = cx - viewCenterWx;
        double dwy = cy - viewCenterWy;
        double errPx = Math.hypot(dwx * scale, dwy * scale);
        if (!Double.isFinite(errPx)) {
            return;
        }
        if (errPx <= WHEEL_SYSTEM_CENTROID_SNAP_ERR_PX) {
            viewCenterWx = cx;
            viewCenterWy = cy;
            subsystemScreenLockHubId = -1;
            return;
        }
        double span = Math.max(1e-9, ZOOM_SUBSYSTEM_CENTER_LOCK - zoomMinFit);
        double u = (ZOOM_SUBSYSTEM_CENTER_LOCK - zoomFactor) / span;
        u = clamp(u, 0.0, 1.0);
        double blend = WHEEL_SYSTEM_OUT_NUDGE_BLEND_MIN
                + (WHEEL_SYSTEM_OUT_NUDGE_BLEND_MAX - WHEEL_SYSTEM_OUT_NUDGE_BLEND_MIN) * Math.pow(u, WHEEL_SYSTEM_OUT_NUDGE_GAMMA);
        blend = clamp(blend, WHEEL_SYSTEM_OUT_NUDGE_BLEND_MIN, WHEEL_SYSTEM_OUT_NUDGE_BLEND_MAX);
        double fracFromMin = (zoomFactor - zoomMinFit) / span;
        fracFromMin = clamp(fracFromMin, 0.0, 1.0);
        double nearFloor = 1.0 - fracFromMin;
        double expoNorm;
        if (WHEEL_SYSTEM_OUT_EXPO_K <= 1e-12) {
            expoNorm = nearFloor;
        } else {
            double denom = Math.exp(WHEEL_SYSTEM_OUT_EXPO_K) - 1.0;
            expoNorm = denom > 1e-15
                    ? (Math.exp(WHEEL_SYSTEM_OUT_EXPO_K * nearFloor) - 1.0) / denom
                    : nearFloor;
        }
        double boosted = blend * (1.0 + WHEEL_SYSTEM_OUT_EXPO_GAIN * expoNorm);
        blend = clamp(boosted, WHEEL_SYSTEM_OUT_NUDGE_BLEND_MIN, WHEEL_SYSTEM_OUT_EXPO_BLEND_MAX);
        viewCenterWx += dwx * blend;
        viewCenterWy += dwy * blend;
        double errAfter = Math.hypot((cx - viewCenterWx) * scale, (cy - viewCenterWy) * scale);
        if (errAfter <= WHEEL_SYSTEM_CENTROID_SNAP_ERR_PX) {
            viewCenterWx = cx;
            viewCenterWy = cy;
        }
        subsystemScreenLockHubId = -1;
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
     * already centred; schematic playback snaps the camera to the hub each frame so moons/planets orbit in place.
     * Gradual recentre toward the hub on zoom-in is driven by {@link #nudgeViewCenterTowardSubsystemHubOnWheel}.
     * After pausing orbit playback, {@code pauseResync} runs one eased step toward the hub when resolved.
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
        if (orbitSchematicPlaybackActive) {
            followSubsystemHubDuringPlayback(scale);
            return;
        }
        ResolvedSubsystemHub h = tryResolveSubsystemFollowHub(availW, availH, scale, pauseResync);
        if (h == null) {
            subsystemScreenLockHubId = -1;
            return;
        }
        if (pauseResync) {
            snapOrSmoothViewCenterToward(h.tx(), h.ty(), scale);
        }
        double errPx = Math.hypot((h.tx() - viewCenterWx) * scale, (h.ty() - viewCenterWy) * scale);
        subsystemScreenLockHubId = errPx <= VIEW_CENTER_HUB_PIN_SCREEN_PX ? h.hubId() : -1;
    }

    /**
     * Schematic playback + subsystem zoom: track the hub world position each tick so the cluster stays centred while
     * bodies move. When zoomed out below {@link #ZOOM_SUBSYSTEM_CENTER_LOCK}, leaves pan/zoom unchanged.
     */
    private void followSubsystemHubDuringPlayback(double scale) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || scale <= 0 || !Double.isFinite(scale)) {
            subsystemScreenLockHubId = -1;
            return;
        }
        int plotH = Math.max(88, h - MAP_BOTTOM_INSET);
        double availW = w - 2.0 * PAD;
        double availH = plotH - 2.0 * PAD;
        ResolvedSubsystemHub hub = tryResolveSubsystemFollowHub(availW, availH, scale, false);
        if (hub == null) {
            subsystemScreenLockHubId = -1;
            return;
        }
        snapOrSmoothViewCenterToward(hub.tx(), hub.ty(), scale);
        subsystemScreenLockHubId = hub.hubId();
    }

    private void applyViewCenterToDotsCentroid(double scale) {
        subsystemScreenLockHubId = -1;
        if (dots.isEmpty()) {
            return;
        }
        Bounds bb = computeBounds(dots, shipKnown ? shipWx : Double.NaN, shipKnown ? shipWy : Double.NaN);
        snapOrSmoothViewCenterToward((bb.minX + bb.maxX) * 0.5, (bb.minY + bb.maxY) * 0.5, scale);
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
    private static int deepestCommonOrbitAncestor(Set<Integer> visible, Map<Integer, BodyInfo> bodies,
            Map<Integer, Integer> resolvedParents) {
        if (visible == null || visible.isEmpty() || bodies == null || bodies.isEmpty()) {
            return -1;
        }
        Set<Integer> common = null;
        for (Integer idObj : visible) {
            if (idObj == null) {
                continue;
            }
            Set<Integer> chain = new HashSet<>();
            addOrbitAncestors(idObj.intValue(), bodies, resolvedParents, chain);
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
            return schematicRootBodyId(bodies, resolvedParents);
        }
        int best = -1;
        int bestDepth = -1;
        for (Integer cObj : common) {
            if (cObj == null) {
                continue;
            }
            int c = cObj.intValue();
            int d = orbitDepthFromStar(c, bodies, resolvedParents);
            if (d > bestDepth || (d == bestDepth && c > best)) {
                bestDepth = d;
                best = c;
            }
        }
        /* One visible leaf (e.g. a single moon): deepest-in-intersection is that body — follow its parent instead. */
        if (visible.size() == 1 && best >= 0 && visible.contains(Integer.valueOf(best))) {
            int p = resolvedParentFromMap(best, resolvedParents);
            if (p >= 0) {
                best = p;
            }
        }
        return best >= 0 ? best : schematicRootBodyId(bodies, resolvedParents);
    }

    private static void addOrbitAncestors(int bodyId, Map<Integer, BodyInfo> bodies, Map<Integer, Integer> resolvedParents,
            Set<Integer> into) {
        int root = schematicRootBodyId(bodies, resolvedParents);
        int cur = bodyId;
        for (int guard = 0; guard < 64 && cur >= 0; guard++) {
            into.add(Integer.valueOf(cur));
            if (cur == root) {
                break;
            }
            int p = resolvedParentFromMap(cur, resolvedParents);
            if (p < 0 || p == cur) {
                break;
            }
            cur = p;
        }
    }

    /** Number of orbit-parent hops from {@code bodyId} up to the schematic root (primary anchor). */
    private static int orbitDepthFromStar(int bodyId, Map<Integer, BodyInfo> bodies,
            Map<Integer, Integer> resolvedParents) {
        int root = schematicRootBodyId(bodies, resolvedParents);
        int hops = 0;
        int cur = bodyId;
        for (int guard = 0; guard < 64 && cur >= 0; guard++) {
            if (cur == root) {
                return hops;
            }
            int p = resolvedParentFromMap(cur, resolvedParents);
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
        if (dots != null) {
            for (BodyDot d : dots) {
                if (d == null || !Double.isFinite(d.wx) || !Double.isFinite(d.wy)) {
                    continue;
                }
                minX = Math.min(minX, d.wx);
                maxX = Math.max(maxX, d.wx);
                minY = Math.min(minY, d.wy);
                maxY = Math.max(maxY, d.wy);
            }
        }
        if (Double.isFinite(shipX) && Double.isFinite(shipY)) {
            minX = Math.min(minX, shipX);
            maxX = Math.max(maxX, shipX);
            minY = Math.min(minY, shipY);
            maxY = Math.max(maxY, shipY);
        }
        if (!Double.isFinite(minX) || !Double.isFinite(maxX) || !Double.isFinite(minY) || !Double.isFinite(maxY)
                || minX > maxX || minY > maxY) {
            /* Degenerate / all-NaN coordinates — keep a finite world box so scale and paint never go NaN (binary systems). */
            return new Bounds(-1.0e9, 1.0e9, -1.0e9, 1.0e9);
        }
        double spanW = maxX - minX;
        double spanH = maxY - minY;
        double padM = Math.max(spanW, spanH) * 0.06 + 1e3;
        return new Bounds(minX - padM, maxX + padM, minY - padM, maxY + padM);
    }

    /** Same padding rule as {@link #computeBounds} but from body dots only — omits ship so distant anchors do not blow up layout scale. */
    private static Bounds computeBoundsDotsOnly(List<BodyDot> dots) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        if (dots != null) {
            for (BodyDot d : dots) {
                if (d == null || !Double.isFinite(d.wx) || !Double.isFinite(d.wy)) {
                    continue;
                }
                minX = Math.min(minX, d.wx);
                maxX = Math.max(maxX, d.wx);
                minY = Math.min(minY, d.wy);
                maxY = Math.max(maxY, d.wy);
            }
        }
        if (!Double.isFinite(minX) || !Double.isFinite(maxX) || !Double.isFinite(minY) || !Double.isFinite(maxY)
                || minX > maxX || minY > maxY) {
            return new Bounds(-1.0e9, 1.0e9, -1.0e9, 1.0e9);
        }
        double spanW = maxX - minX;
        double spanH = maxY - minY;
        double padM = Math.max(spanW, spanH) * 0.06 + 1e3;
        return new Bounds(minX - padM, maxX + padM, minY - padM, maxY + padM);
    }

    /**
     * Padded bounds from non-star body dots only — binary primaries often sit far outside the schematic planet/moon
     * cluster; using their separation for {@code scaleFit} makes wheel zoom imperceptible.
     */
    private static Bounds computeBoundsDotsOnlyNonStars(List<BodyDot> dots) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        int n = 0;
        if (dots != null) {
            for (BodyDot d : dots) {
                if (d == null || d.star || !Double.isFinite(d.wx) || !Double.isFinite(d.wy)) {
                    continue;
                }
                minX = Math.min(minX, d.wx);
                maxX = Math.max(maxX, d.wx);
                minY = Math.min(minY, d.wy);
                maxY = Math.max(maxY, d.wy);
                n++;
            }
        }
        if (n <= 0 || !Double.isFinite(minX) || !Double.isFinite(maxX) || !Double.isFinite(minY) || !Double.isFinite(maxY)
                || minX > maxX || minY > maxY) {
            return null;
        }
        double spanW = maxX - minX;
        double spanH = maxY - minY;
        double padM = Math.max(spanW, spanH) * 0.06 + 1e3;
        return new Bounds(minX - padM, maxX + padM, minY - padM, maxY + padM);
    }

    /**
     * {@code [fitSpanX, fitSpanY, rob10x, rob10y, robBlendX, robBlendY, narrowX, narrowY]} for layout scale and logs.
     * {@code robBlend} widens the percentile window when 10–90 collapses so {@link #blendLayoutSpanForFit} can still
     * separate a distant outlier from a tight schematic core.
     */
    private static double[] layoutFitSpansAndRobust(List<BodyDot> dots, Bounds bbAll, Bounds bbDots) {
        double fullX = layoutSpanAxisMetres(bbAll.minX, bbAll.maxX);
        double fullY = layoutSpanAxisMetres(bbAll.minY, bbAll.maxY);
        double dx = layoutSpanAxisMetres(bbDots.minX, bbDots.maxX);
        double dy = layoutSpanAxisMetres(bbDots.minY, bbDots.maxY);
        double fitX = Math.min(fullX, dx);
        double fitY = Math.min(fullY, dy);
        Bounds ns = computeBoundsDotsOnlyNonStars(dots);
        if (ns != null) {
            fitX = Math.min(fitX, layoutSpanAxisMetres(ns.minX, ns.maxX));
            fitY = Math.min(fitY, layoutSpanAxisMetres(ns.minY, ns.maxY));
        }
        double r10x = robustPercentileSpanRawMetresPct(dots, true, 10, 90);
        double r10y = robustPercentileSpanRawMetresPct(dots, false, 10, 90);
        double rbx = widenRobustSpanIfFlat(dots, true, r10x);
        double rby = widenRobustSpanIfFlat(dots, false, r10y);
        /*
         * All-body robust spans often remain ~70–100% of the padded fit span in true binaries (two stellar outliers +
         * long planet chains), so blendLayoutSpanForFit's "core < full/3" rule never fires. Non-star percentile bands
         * (tight to wide) capture planet/moon structure without both stars; we take the smallest positive band that is
         * still below the widened robust span so blend can shrink layout toward a zoomable core.
         */
        double narrowX = minPositiveFinite(nonStarRobustPercentileSpanRawMetresPct(dots, true, 25, 75),
                nonStarRobustPercentileSpanRawMetresPct(dots, true, 40, 60),
                nonStarRobustPercentileSpanRawMetresPct(dots, true, 45, 55));
        double narrowY = minPositiveFinite(nonStarRobustPercentileSpanRawMetresPct(dots, false, 25, 75),
                nonStarRobustPercentileSpanRawMetresPct(dots, false, 40, 60),
                nonStarRobustPercentileSpanRawMetresPct(dots, false, 45, 55));
        if (narrowX > 0.0 && narrowX < rbx) {
            rbx = narrowX;
        }
        if (narrowY > 0.0 && narrowY < rby) {
            rby = narrowY;
        }
        rbx = capRobBlendForLayoutBlend(fitX, rbx);
        rby = capRobBlendForLayoutBlend(fitY, rby);
        return new double[] { fitX, fitY, r10x, r10y, rbx, rby, narrowX, narrowY };
    }

    /**
     * {@link #blendLayoutSpanForFit} only returns a span smaller than {@code fitSpan} when {@code robustRaw*5 < fitSpan}
     * and {@code robustRaw < fitSpan/3}. Wide binaries often yield a "core" ~70–90% of {@code fitSpan}, so the blend
     * step leaves layout at the full Gm-scale and wheel zoom looks broken. When {@code 5*robBlend >= fitSpan}, cap the
     * core toward {@code fitSpan / LAYOUT_BLEND_CORE_CAP_DIVISOR} so {@code min(fit, 5*core)} can approach
     * {@code fit/5} (see constant) and scale/zoom are usable on wide binaries.
     */
    private static double capRobBlendForLayoutBlend(double fitSpan, double robBlend) {
        if (!Double.isFinite(fitSpan) || fitSpan <= 0.0) {
            return robBlend;
        }
        if (!Double.isFinite(robBlend) || robBlend <= 0.0) {
            return robBlend;
        }
        if (robBlend * 5.0 < fitSpan * (1.0 - 1e-12)) {
            return robBlend;
        }
        return Math.min(robBlend, fitSpan / LAYOUT_BLEND_CORE_CAP_DIVISOR);
    }

    private static double widenRobustSpanIfFlat(List<BodyDot> dots, boolean horizontal, double r10) {
        if (r10 > 0.0) {
            return r10;
        }
        double r = robustPercentileSpanRawMetresPct(dots, horizontal, 15, 85);
        if (r > 0.0) {
            return r;
        }
        r = robustPercentileSpanRawMetresPct(dots, horizontal, 2, 98);
        return r > 0.0 ? r : 0.0;
    }

    /** Min–max projected coordinate span (m), optionally excluding star dots. */
    private static double rawDotProjectedMinMaxSpanMetres(List<BodyDot> dots, boolean horizontal, boolean excludeStars) {
        double minV = Double.POSITIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;
        if (dots == null) {
            return 0.0;
        }
        for (BodyDot d : dots) {
            if (d == null || (excludeStars && d.star)) {
                continue;
            }
            double v = horizontal ? d.wx : d.wy;
            if (!Double.isFinite(v)) {
                continue;
            }
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }
        if (!Double.isFinite(minV) || !Double.isFinite(maxV) || minV > maxV) {
            return 0.0;
        }
        return Math.max(0.0, maxV - minV);
    }

    /** Min–max span on star dots only (projected map coordinates, metres). */
    private static double rawDotProjectedMinMaxSpanMetresStarsOnly(List<BodyDot> dots, boolean horizontal) {
        double minV = Double.POSITIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;
        if (dots == null) {
            return 0.0;
        }
        for (BodyDot d : dots) {
            if (d == null || !d.star) {
                continue;
            }
            double v = horizontal ? d.wx : d.wy;
            if (!Double.isFinite(v)) {
                continue;
            }
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }
        if (!Double.isFinite(minV) || !Double.isFinite(maxV) || minV > maxV) {
            return 0.0;
        }
        return Math.max(0.0, maxV - minV);
    }

    /**
     * Percentile span on non-star body dots only (same rules as {@link #robustPercentileSpanRawMetresPct}). In
     * binaries, all-dot percentiles stay almost as wide as the stellar separation; the inner planet/moon band is much
     * tighter and makes a better {@link #blendLayoutSpanForFit} “core” so wheel zoom is visible.
     */
    private static double nonStarRobustPercentileSpanRawMetresPct(List<BodyDot> dots, boolean horizontal, int pLoPct,
            int pHiPct) {
        if (dots == null || dots.isEmpty() || pLoPct < 0 || pHiPct > 100 || pLoPct >= pHiPct) {
            return 0.0;
        }
        int n = 0;
        for (BodyDot d : dots) {
            if (d != null && !d.star && Double.isFinite(d.wx) && Double.isFinite(d.wy)) {
                n++;
            }
        }
        if (n < 4) {
            return 0.0;
        }
        double[] v = new double[n];
        int j = 0;
        for (BodyDot d : dots) {
            if (d == null || d.star || !Double.isFinite(d.wx) || !Double.isFinite(d.wy)) {
                continue;
            }
            v[j++] = horizontal ? d.wx : d.wy;
        }
        Arrays.sort(v);
        if (n < 8) {
            return Math.max(0.0, v[n - 1] - v[0]);
        }
        int lo = (n - 1) * pLoPct / 100;
        int hi = (n - 1) * pHiPct / 100;
        if (hi <= lo) {
            return Math.max(0.0, v[n - 1] - v[0]);
        }
        return Math.max(0.0, v[hi] - v[lo]);
    }

    private static double minPositiveFinite(double a, double b, double c) {
        double m = Double.POSITIVE_INFINITY;
        if (Double.isFinite(a) && a > 0.0) {
            m = Math.min(m, a);
        }
        if (Double.isFinite(b) && b > 0.0) {
            m = Math.min(m, b);
        }
        if (Double.isFinite(c) && c > 0.0) {
            m = Math.min(m, c);
        }
        return m < Double.POSITIVE_INFINITY ? m : 0.0;
    }

    /** Positive finite layout span (m) from bounds; never NaN so {@code scaleFit} stays valid for binary / wide maps. */
    private static double layoutSpanAxisMetres(double lo, double hi) {
        double raw = hi - lo;
        if (!Double.isFinite(lo) || !Double.isFinite(hi) || !Double.isFinite(raw) || raw <= 0.0) {
            return 1.0;
        }
        return Math.max(1.0, raw);
    }

    /**
     * Percentile band span on projected dot coordinates (raw metres, no floor). Zero means the band collapsed on that
     * axis for the chosen percentiles.
     */
    private static double robustPercentileSpanRawMetresPct(List<BodyDot> dots, boolean horizontal, int pLoPct,
            int pHiPct) {
        if (dots == null || dots.isEmpty() || pLoPct < 0 || pHiPct > 100 || pLoPct >= pHiPct) {
            return 0.0;
        }
        int n = dots.size();
        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            BodyDot d = dots.get(i);
            v[i] = horizontal ? d.wx : d.wy;
        }
        Arrays.sort(v);
        if (n < 8) {
            return Math.max(0.0, v[n - 1] - v[0]);
        }
        int lo = (n - 1) * pLoPct / 100;
        int hi = (n - 1) * pHiPct / 100;
        if (hi <= lo) {
            return Math.max(0.0, v[n - 1] - v[0]);
        }
        return Math.max(0.0, v[hi] - v[lo]);
    }

    /**
     * 10th–90th percentile span on projected dot coordinates (raw metres, no floor). Zero means the band collapsed
     * (e.g. duplicate coordinates) — must not be promoted to {@code 1.0} or {@link #blendLayoutSpanForFit} mis-detects
     * “one distant outlier vs 1 m cluster”.
     */
    private static double robustPercentileSpanRawMetres(List<BodyDot> dots, boolean horizontal) {
        return robustPercentileSpanRawMetresPct(dots, horizontal, 10, 90);
    }

    /**
     * Midpoint of the same robust band as {@link #robustPercentileSpanRawMetres} (10th–90th when enough samples), else
     * min–max midpoint — pairs with {@link #blendLayoutSpanForFit} when the layout span is tightened to that band.
     */
    private static double robustPercentileMidMetres(List<BodyDot> dots, boolean horizontal) {
        if (dots == null || dots.isEmpty()) {
            return 0.0;
        }
        int n = dots.size();
        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            BodyDot d = dots.get(i);
            v[i] = horizontal ? d.wx : d.wy;
        }
        Arrays.sort(v);
        if (n < 8) {
            return (v[0] + v[n - 1]) * 0.5;
        }
        int lo = (n - 1) * 10 / 100;
        int hi = (n - 1) * 90 / 100;
        return (v[lo] + v[hi]) * 0.5;
    }

    /**
     * When {@link #blendLayoutSpanForFit} uses the tight (robust-core) branch, the fitted span is much smaller than the
     * padded full bbox — centring on the full bbox would leave all dots off-screen. Use the robust band midpoint on
     * each axis where that branch applies; otherwise keep the padded-bounds centre (binary / wide systems).
     */
    private void applyClusterAwareViewCenter(Bounds bb, double refSpanX, double refSpanY, double robRawX,
            double robRawY, double robBlendX, double robBlendY, double narrowX, double narrowY, List<BodyDot> dots) {
        boolean tightX = tightLayoutAxis(refSpanX, robBlendX);
        boolean tightY = tightLayoutAxis(refSpanY, robBlendY);
        boolean degenerateRob = robRawX <= 0.0 && robRawY <= 0.0;
        double ncx;
        double ncy;
        if (degenerateRob) {
            /*
             * 10th–90th band has zero width on both axes (many bodies share schematic coordinates) while padded bbox is
             * still huge — (min+max)/2 sits in empty space between binary components; use the population mean instead.
             */
            double[] m = dotArithmeticMeanWorldXY(dots);
            ncx = m[0];
            ncy = m[1];
        } else {
            ncx = tightX ? robustPercentileMidMetres(dots, true) : (bb.minX + bb.maxX) * 0.5;
            ncy = tightY ? robustPercentileMidMetres(dots, false) : (bb.minY + bb.maxY) * 0.5;
        }
        if (Double.isFinite(ncx) && Double.isFinite(ncy)) {
            viewCenterWx = ncx;
            viewCenterWy = ncy;
        }
    }

    private static double[] dotArithmeticMeanWorldXY(List<BodyDot> dots) {
        double sx = 0.0;
        double sy = 0.0;
        int n = 0;
        if (dots != null) {
            for (BodyDot d : dots) {
                if (d == null || !Double.isFinite(d.wx) || !Double.isFinite(d.wy)) {
                    continue;
                }
                sx += d.wx;
                sy += d.wy;
                n++;
            }
        }
        if (n <= 0) {
            return new double[] { Double.NaN, Double.NaN };
        }
        return new double[] { sx / n, sy / n };
    }

    /** Mean projected position of non-star dots only; pairs with non-star-only layout span tightening. */
    /** After barycentre recenter, mean of A/B star dots — map view should not bias toward the arrival star. */
    private static double[] wideBinaryStellarCentroidWorldXY(List<BodyDot> dots) {
        double sx = 0.0;
        double sy = 0.0;
        int n = 0;
        if (dots != null) {
            for (BodyDot d : dots) {
                if (d == null || !d.star || !Double.isFinite(d.wx) || !Double.isFinite(d.wy)) {
                    continue;
                }
                sx += d.wx;
                sy += d.wy;
                n++;
            }
        }
        if (n <= 0) {
            return new double[] { Double.NaN, Double.NaN };
        }
        return new double[] { sx / n, sy / n };
    }

    private static double[] dotArithmeticMeanWorldXYNonStars(List<BodyDot> dots) {
        double sx = 0.0;
        double sy = 0.0;
        int n = 0;
        if (dots != null) {
            for (BodyDot d : dots) {
                if (d == null || d.star || !Double.isFinite(d.wx) || !Double.isFinite(d.wy)) {
                    continue;
                }
                sx += d.wx;
                sy += d.wy;
                n++;
            }
        }
        if (n <= 0) {
            return new double[] { Double.NaN, Double.NaN };
        }
        return new double[] { sx / n, sy / n };
    }

    /** True when {@link #blendLayoutSpanForFit} uses the tightened (robust-core) span on this axis. */
    private static boolean tightLayoutAxis(double fullSpan, double robustRaw) {
        if (!Double.isFinite(fullSpan) || fullSpan <= 0.0 || !Double.isFinite(robustRaw)) {
            return false;
        }
        if (robustRaw <= 0.0 || robustRaw < fullSpan * 1e-12) {
            return false;
        }
        return robustRaw < fullSpan / 3.0;
    }

    /**
     * When one outlier stretches the padded bbox, tighten the fit span so the main cluster is visible at min zoom;
     * keeps full bbox for {@link #computeBounds} / recentring. {@code robustRaw} must be the true percentile span (no
     * artificial 1 m floor) so a collapsed band does not look like a tiny cluster beside a huge outlier.
     */
    private static double blendLayoutSpanForFit(double fullSpan, double robustRaw) {
        if (!Double.isFinite(fullSpan) || fullSpan <= 0.0) {
            return 1.0;
        }
        if (!Double.isFinite(robustRaw) || robustRaw <= 0.0 || robustRaw < fullSpan * 1e-12) {
            return Math.max(1.0, fullSpan);
        }
        if (robustRaw >= fullSpan / 3.0) {
            return Math.max(1.0, fullSpan);
        }
        return Math.max(1.0, Math.min(fullSpan, robustRaw * 5.0));
    }

    /**
     * Span used to clamp layout so {@code zoom=1} frames the main population: full min–max with one distant body
     * (e.g. moon at thousands of Ls) is blended with the 10th–90th percentile band like {@link #blendLayoutSpanForFit}.
     */
    private static double layoutHullSpanMetresPreferCluster(List<BodyDot> dots, boolean horizontal) {
        double full = rawDotProjectedMinMaxSpanMetres(dots, horizontal, false);
        if (!Double.isFinite(full) || full <= 1.0) {
            return 1.0;
        }
        double rob = robustPercentileSpanRawMetres(dots, horizontal);
        rob = widenRobustSpanIfFlat(dots, horizontal, rob);
        if (!Double.isFinite(rob) || rob <= 0.0 || rob < full * 1e-12) {
            return Math.max(1.0, full * LAYOUT_MIN_OVER_ALL_DOT_HULL);
        }
        double blend = blendLayoutSpanForFit(full, rob);
        return Math.max(1.0, blend * LAYOUT_MIN_OVER_ALL_DOT_HULL);
    }

    /**
     * Blend + optional non-star hull tighten, then floor against fit and star hull so binaries stay framed. When the
     * floor widens the span back toward binary scale, the {@code useNonStarCentroid} flag is false and the cluster/bbox centre
     * from {@link #applyClusterAwareViewCenter} is correct.
     */
    private record MapLayoutSpanPick(double layoutSpanX, double layoutSpanY, boolean useNonStarCentroid, double rnsSpanX,
            double rnsSpanY) {
    }

    private static MapLayoutSpanPick pickMapLayoutSpans(List<BodyDot> dots, double fitSpanX, double fitSpanY,
            double robBlendX, double robBlendY) {
        double lx = blendLayoutSpanForFit(fitSpanX, robBlendX);
        double ly = blendLayoutSpanForFit(fitSpanY, robBlendY);
        double rnsX = rawDotProjectedMinMaxSpanMetres(dots, true, true);
        double rnsY = rawDotProjectedMinMaxSpanMetres(dots, false, true);
        final double rnsPad = 1.08;
        boolean rtx = false;
        boolean rty = false;
        if (rnsX > 1.0 && rnsX * rnsPad < lx) {
            lx = Math.max(1.0, rnsX * rnsPad);
            rtx = true;
        }
        if (rnsY > 1.0 && rnsY * rnsPad < ly) {
            ly = Math.max(1.0, rnsY * rnsPad);
            rty = true;
        }
        double larX = lx;
        double larY = ly;
        double starX = rawDotProjectedMinMaxSpanMetresStarsOnly(dots, true);
        double starY = rawDotProjectedMinMaxSpanMetresStarsOnly(dots, false);
        double clHullX = layoutHullSpanMetresPreferCluster(dots, true);
        double clHullY = layoutHullSpanMetresPreferCluster(dots, false);
        double floorX = Math.max(fitSpanX * RNS_TIGHTEN_MIN_FRAC_OF_FIT, Math.max(
                starX > 1.0 ? starX * RNS_TIGHTEN_MIN_FRAC_OF_STAR_HULL : 0.0,
                clHullX > 1.0 ? clHullX * RNS_TIGHTEN_MIN_FRAC_OF_ALL_BODY_HULL : 0.0));
        double floorY = Math.max(fitSpanY * RNS_TIGHTEN_MIN_FRAC_OF_FIT, Math.max(
                starY > 1.0 ? starY * RNS_TIGHTEN_MIN_FRAC_OF_STAR_HULL : 0.0,
                clHullY > 1.0 ? clHullY * RNS_TIGHTEN_MIN_FRAC_OF_ALL_BODY_HULL : 0.0));
        lx = Math.max(lx, floorX);
        ly = Math.max(ly, floorY);
        boolean floored = lx > larX * 1.0001 || ly > larY * 1.0001;
        boolean useNs = (rtx || rty) && !floored;
        return new MapLayoutSpanPick(lx, ly, useNs, rnsX, rnsY);
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

    private void handleMapClickRelease(MouseEvent e) {
        if (mapClickPressX < 0 || mapClickPressY < 0) {
            return;
        }
        double drag = Math.hypot(e.getX() - mapClickPressX, e.getY() - mapClickPressY);
        int pressX = mapClickPressX;
        int pressY = mapClickPressY;
        mapClickPressX = -1;
        mapClickPressY = -1;
        if (drag > MAP_CLICK_DRAG_THRESHOLD_PX) {
            return;
        }
        logMapClickHit(findMapClickHit(pressX, pressY), pressX, pressY);
    }

    private void logMapClickHit(MapClickHit hit, int px, int py) {
        if (hit == null) {
            String nearUntracked = describeNearUntrackedHubRevolutionRing(px, py);
            if (nearUntracked != null) {
                System.out.println("[EDO][OrbitMap][Click] (nothing) " + nearUntracked);
            } else {
                System.out.println("[EDO][OrbitMap][Click] (nothing)");
            }
            return;
        }
        System.out.println(hit.formatLine());
    }

    /**
     * When a click misses {@link #orbitLines}, detect proximity to the paint-only schematic hub circle
     * ({@link #drawSubsystemHubRevolutionPathRing}) so regressions are obvious in true scale.
     */
    private String describeNearUntrackedHubRevolutionRing(int px, int py) {
        MapClickPaintCtx ctx = buildMapClickPaintCtx();
        if (ctx == null || mapModel == null) {
            return null;
        }
        float tol = MAP_HIT_ORBIT_STROKE_PX + 2f;
        int bestId = -1;
        double bestDist = Double.POSITIVE_INFINITY;
        for (BodyDot d : dots) {
            if (d == null || !subsystemHubRevolutionPathRingEligibleForTests(d.bodyId, ctx.visibleLsMinAxis)) {
                continue;
            }
            double[] ring = hubRevolutionRingScreenMetres(d.bodyId, ctx);
            if (ring == null) {
                continue;
            }
            double cx = ring[0];
            double cy = ring[1];
            double rPx = ring[2];
            if (!Double.isFinite(cx) || !Double.isFinite(cy) || !Double.isFinite(rPx) || rPx < 3.0) {
                continue;
            }
            double dist = Math.abs(Math.hypot(px - cx, py - cy) - rPx);
            if (dist < tol && dist < bestDist) {
                bestDist = dist;
                bestId = d.bodyId;
            }
        }
        if (bestId < 0) {
            return null;
        }
        double radLs = hubRevolutionRingRadiusLsForTests(bestId);
        return String.format(Locale.US,
                "nearUntrackedHubRevolutionRing bodyId=%d radiusLs=%.1f trueScale=%s drawn=%s",
                bestId, radLs, mapScaleMode.trueScale(), subsystemHubRevolutionPathRingDrawnForTests(bestId,
                        ctx.visibleLsMinAxis));
    }

    /** Screen centre and radius (px) of the live-distance hub ring, or null when undefined. */
    private double[] hubRevolutionRingScreenMetres(int bodyId, MapClickPaintCtx ctx) {
        if (mapModel == null || ctx == null) {
            return null;
        }
        BodyDot dot = findBodyDot(bodyId);
        if (dot == null || !Double.isFinite(dot.wx) || !Double.isFinite(dot.wy)) {
            return null;
        }
        int parentId = mapModel.resolveParentBodyId(bodyId);
        if (parentId < 0) {
            return null;
        }
        double px = mapModel.mapPlaneX(parentId);
        double py = mapModel.mapPlaneY(parentId);
        if (!Double.isFinite(px) || !Double.isFinite(py)) {
            return null;
        }
        double radM = Math.hypot(dot.wx - px, dot.wy - py);
        if (!Double.isFinite(radM) || radM < SystemOrbitGeometry.LIGHT_SECOND_METRES) {
            return null;
        }
        double cx = PAD + ctx.availW / 2.0 + (px - ctx.vcx) * ctx.scale;
        double cy = PAD + ctx.availH / 2.0 - (py - ctx.vcy) * ctx.scale;
        return new double[] { cx, cy, radM * ctx.scale };
    }

    private MapClickHit findMapClickHit(int px, int py) {
        if (sceneEmpty || dots.isEmpty()) {
            return null;
        }
        MapClickPaintCtx ctx = buildMapClickPaintCtx();
        if (ctx == null) {
            return null;
        }
        MapClickHit hit = hitTestExobiologyLeaf(ctx, px, py);
        if (hit != null) {
            return hit;
        }
        hit = hitTestDetachedCommanderGlyph(ctx, px, py);
        if (hit != null) {
            return hit;
        }
        hit = hitTestBodyLabel(ctx, px, py);
        if (hit != null) {
            return hit;
        }
        hit = hitTestBarycentreMarker(ctx, px, py);
        if (hit != null) {
            return hit;
        }
        hit = hitTestSummaryClusterDot(ctx, px, py);
        if (hit != null) {
            return hit;
        }
        hit = hitTestCompanionLump(ctx, px, py);
        if (hit != null) {
            return hit;
        }
        hit = hitTestBodyDot(ctx, px, py);
        if (hit != null) {
            return hit;
        }
        return hitTestOrbitStroke(ctx, px, py);
    }

    private MapClickPaintCtx buildMapClickPaintCtx() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        Font base = getFont();
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
        }
        Font labelFont = base.deriveFont(Font.PLAIN, base.getSize2D());
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scratch.createGraphics();
        try {
            g2.setFont(labelFont);
            FontMetrics labelFm = g2.getFontMetrics(labelFont);
            int plotH = Math.max(88, h - MAP_BOTTOM_INSET);
            double availW = w - 2.0 * PAD;
            double availH = plotH - 2.0 * PAD;
            updateZoomMinFit(availW, availH);
            double scale = computeMapPlotScale(availW, availH, layoutSpanX, layoutSpanY);
            if (!Double.isFinite(scale) || scale <= 0.0) {
                return null;
            }
            rebuildOrbitPolylines(false, !orbitSchematicPlaybackActive);
            double vcx = viewCenterWx;
            double vcy = viewCenterWy;
            double plotCx = PAD + availW * 0.5;
            double plotCy = PAD + availH * 0.5;
            double visibleLsMinAxis = estimateVisibleLightSecondsAcrossMinPlotAxis(availW, availH, scale);
            boolean showClusterDetail = mapShowClusterDetail(visibleLsMinAxis);
            CompanionBranchLump companionLump = buildCompanionBranchRevolutionLump(dots, showClusterDetail,
                    visibleLsMinAxis);
            boolean showMoonLabels = mapShowMoonLabels(visibleLsMinAxis);
            boolean showAllBodyLabels = mapShowAllBodyLabels(visibleLsMinAxis);
            MapLabelDrawPlan labelPlan = buildCollisionAwareLabelPlan(dots, showClusterDetail, showMoonLabels,
                    showAllBodyLabels, companionLump, labelFm, vcx, vcy, scale, availW, availH, plotCx, plotCy);
            float dotEm = Math.max(8f, labelFm.getHeight() * 0.42f);
            float starR = Math.max(4.5f, dotEm * 1.05f);
            float bodyR = Math.max(3f, dotEm * 0.62f);
            List<BarycentreMarkerHit> baryMarkers = collectBarycentreMarkersForHit(companionLump);
            return new MapClickPaintCtx(labelFm, labelFont, availW, availH, plotCx, plotCy, scale, vcx, vcy,
                    visibleLsMinAxis, showClusterDetail, showMoonLabels, showAllBodyLabels, companionLump, labelPlan,
                    starR, bodyR, dotEm, baryMarkers);
        } finally {
            g2.dispose();
        }
    }

    private MapClickHit hitTestExobiologyLeaf(MapClickPaintCtx ctx, int px, int py) {
        if (exobiologyLeafIcon == null) {
            return null;
        }
        int leafW = exobiologyLeafIcon.getIconWidth();
        int leafH = exobiologyLeafIcon.getIconHeight();
        for (BodyDot d : dots) {
            if (d == null || hideDotForSubsystemLumpView(d, ctx.visibleLsMinAxis)) {
                continue;
            }
            boolean lumpHub = subsystemHubLump(ctx.visibleLsMinAxis, d);
            boolean drawLeaf = lumpHub
                    ? subsystemHubHasExobiologyDescendant(d.bodyId)
                    : d.hasExobiology;
            if (!drawLeaf) {
                continue;
            }
            boolean lockHub = subsystemScreenLockHubId >= 0 && d.bodyId == subsystemScreenLockHubId;
            double[] cxy = bodyDotScreenMetres(d, lockHub, ctx.vcx, ctx.vcy, ctx.scale, ctx.availW, ctx.availH,
                    ctx.plotCx, ctx.plotCy);
            float cx = (float) cxy[0];
            float cy = (float) cxy[1];
            float rr = lumpHub
                    ? Math.max(7.5f, subsystemMoonHubRingOuterRadiusPx(
                            mapBodyDotRadiusPx(d, ctx.starR, ctx.bodyR, zoomFactor, ctx.showClusterDetail)) + 2.5f)
                    : mapBodyDotRadiusPx(d, ctx.starR, ctx.bodyR, zoomFactor, ctx.showClusterDetail);
            boolean commanderHere = anchorBodyId != null && d.bodyId == anchorBodyId.intValue();
            String commanderSlotKey = null;
            if (commanderHere) {
                if (d.label != null && !d.label.isEmpty()
                        && bodyDotLabelWouldDraw(d, ctx.showClusterDetail, ctx.showMoonLabels, ctx.showAllBodyLabels,
                                ctx.companionLump)) {
                    commanderSlotKey = MAP_COMMANDER_TRIANGLE_CHAR + d.label;
                } else {
                    commanderSlotKey = MAP_COMMANDER_TRIANGLE_CHAR + d.bodyId;
                }
            }
            float[] lp = exobiologyLeafAnchor(cx, cy, rr, d.bodyId, leafW, leafH, commanderSlotKey,
                    lumpHub ? false : d.moon);
            java.awt.geom.Rectangle2D.Float rect = new java.awt.geom.Rectangle2D.Float(lp[0], lp[1], leafW, leafH);
            if (rect.contains(px, py)) {
                return MapClickHit.exobiology(d.bodyId, d.label);
            }
        }
        return null;
    }

    private MapClickHit hitTestDetachedCommanderGlyph(MapClickPaintCtx ctx, int px, int py) {
        if (anchorBodyId == null) {
            return null;
        }
        int cmdId = anchorBodyId.intValue();
        BodyDot cmd = findBodyDot(cmdId);
        if (cmd == null || cmd.label == null || cmd.label.isEmpty()
                || bodyDotLabelWouldDraw(cmd, ctx.showClusterDetail, ctx.showMoonLabels, ctx.showAllBodyLabels,
                        ctx.companionLump)) {
            return null;
        }
        boolean lockCmd = subsystemScreenLockHubId >= 0 && cmdId == subsystemScreenLockHubId;
        double[] cxy = bodyDotScreenMetres(cmd, lockCmd, ctx.vcx, ctx.vcy, ctx.scale, ctx.availW, ctx.availH,
                ctx.plotCx, ctx.plotCy);
        float cx = (float) cxy[0];
        float cy = (float) cxy[1];
        float rr = mapBodyDotRadiusPx(cmd, ctx.starR, ctx.bodyR, zoomFactor, ctx.showClusterDetail);
        int triW = ctx.labelFm.stringWidth(MAP_COMMANDER_TRIANGLE_CHAR);
        String triSlot = MAP_COMMANDER_TRIANGLE_CHAR + cmdId;
        float[] triLp = bodyLabelAnchor(cx, cy, rr, cmdId, triSlot, triW, ctx.labelFm, cmd.moon);
        java.awt.geom.Rectangle2D.Float rect = anchorRect(triLp[0], triLp[1], triW, ctx.labelFm, MAP_HIT_LABEL_PAD_PX);
        if (rect.contains(px, py)) {
            return MapClickHit.commanderGlyph(cmdId, cmd.label);
        }
        return null;
    }

    private MapClickHit hitTestBodyLabel(MapClickPaintCtx ctx, int px, int py) {
        for (BodyDot d : dots) {
            if (d == null || d.label == null || d.label.isEmpty()) {
                continue;
            }
            if (!bodyDotLabelWouldDraw(d, ctx.showClusterDetail, ctx.showMoonLabels, ctx.showAllBodyLabels,
                    ctx.companionLump)) {
                continue;
            }
            if (ctx.labelPlan.suppressedBodyIds.contains(Integer.valueOf(d.bodyId))) {
                continue;
            }
            if (ctx.companionLump != null && ctx.companionLump.contains(d.bodyId)
                    && d.bodyId != ctx.companionLump.hubBodyId) {
                continue;
            }
            boolean lockHub = subsystemScreenLockHubId >= 0 && d.bodyId == subsystemScreenLockHubId;
            double[] cxy = bodyDotScreenMetres(d, lockHub, ctx.vcx, ctx.vcy, ctx.scale, ctx.availW, ctx.availH,
                    ctx.plotCx, ctx.plotCy);
            float sx = (float) cxy[0];
            float sy = (float) cxy[1];
            float r = mapBodyDotRadiusPx(d, ctx.starR, ctx.bodyR, zoomFactor, ctx.showClusterDetail);
            boolean lumpHub = subsystemHubLump(ctx.visibleLsMinAxis, d);
            boolean moonHostHub = !d.star && subsystemHubLumpBodyIds.contains(Integer.valueOf(d.bodyId));
            float rLabel = moonHostHub && lumpHub
                    ? Math.max(7.5f, subsystemMoonHubRingOuterRadiusPx(r) + 2.5f)
                    : r;
            boolean commanderHere = anchorBodyId != null && d.bodyId == anchorBodyId.intValue();
            String displayLabel = ctx.labelPlan.summaryTextByHubId.getOrDefault(Integer.valueOf(d.bodyId), d.label);
            int labelWidthPx = commanderHere
                    ? commanderPrefixedLabelWidth(ctx.labelFm, displayLabel)
                    : ctx.labelFm.stringWidth(displayLabel);
            int lumpHubId = !ctx.showClusterDetail && orbitGeomBodies != null
                    ? outermostSubsystemLumpHub(d.bodyId, orbitGeomBodies, subsystemHubLumpBodyIds)
                    : -1;
            boolean lumpHubLabel = lumpHubId >= 0 && d.bodyId == lumpHubId;
            float[] lp;
            if (commanderHere) {
                int triW = ctx.labelFm.stringWidth(MAP_COMMANDER_TRIANGLE_CHAR);
                String slotLabel = MAP_COMMANDER_TRIANGLE_CHAR + displayLabel;
                lp = bodyLabelAnchor(sx, sy, rLabel, d.bodyId, slotLabel, triW, ctx.labelFm, d.moon);
                labelWidthPx = commanderPrefixedLabelWidth(ctx.labelFm, displayLabel);
            } else {
                lp = ctx.labelPlan.anchors.get(Integer.valueOf(d.bodyId));
                if (lp == null) {
                    String slotLabel = displayLabel;
                    lp = lumpHubLabel
                            ? lumpHubClusterLabelAnchor(dots, lumpHubId, sx, sy, rLabel, d.bodyId, slotLabel,
                                    labelWidthPx, ctx.labelFm, orbitGeomBodies, subsystemHubLumpBodyIds, ctx.vcx,
                                    ctx.vcy, ctx.scale, ctx.availW, ctx.availH, ctx.plotCx, ctx.plotCy, lockHub)
                            : labelAnchorForBodyDot(sx, sy, rLabel, d, slotLabel, labelWidthPx, ctx.labelFm);
                }
            }
            java.awt.geom.Rectangle2D.Float rect = anchorRect(lp[0], lp[1], labelWidthPx, ctx.labelFm,
                    MAP_HIT_LABEL_PAD_PX);
            if (rect.contains(px, py)) {
                return MapClickHit.label(d.bodyId, displayLabel);
            }
        }
        return null;
    }

    private MapClickHit hitTestBarycentreMarker(MapClickPaintCtx ctx, int px, int py) {
        float half = Math.max(3.5f, ctx.dotEm * 0.55f) + MAP_HIT_BODY_PAD_PX;
        for (BarycentreMarkerHit m : ctx.baryMarkers) {
            double sx = PAD + ctx.availW / 2.0 + (m.wx - ctx.vcx) * ctx.scale;
            double sy = PAD + ctx.availH / 2.0 - (m.wy - ctx.vcy) * ctx.scale;
            if (!Double.isFinite(sx) || !Double.isFinite(sy)) {
                continue;
            }
            if (Math.abs(px - sx) <= half && Math.abs(py - sy) <= half) {
                return MapClickHit.barycentre(m.mapKey, m.wx, m.wy);
            }
        }
        return null;
    }

    private MapClickHit hitTestSummaryClusterDot(MapClickPaintCtx ctx, int px, int py) {
        if (ctx.companionLump != null || ctx.labelPlan == null
                || ctx.labelPlan.summaryClusterCentroids.isEmpty()) {
            return null;
        }
        float r = Math.max(2.5f, ctx.bodyR * 0.52f) + MAP_HIT_BODY_PAD_PX;
        for (Map.Entry<Integer, float[]> e : ctx.labelPlan.summaryClusterCentroids.entrySet()) {
            float[] c = e.getValue();
            if (c == null || c.length < 2) {
                continue;
            }
            if (pointInCircle(px, py, c[0], c[1], r)) {
                int hubId = e.getKey().intValue();
                String text = ctx.labelPlan.summaryTextByHubId.getOrDefault(Integer.valueOf(hubId), "");
                return MapClickHit.summaryCluster(hubId, text);
            }
        }
        return null;
    }

    private MapClickHit hitTestCompanionLump(MapClickPaintCtx ctx, int px, int py) {
        CompanionBranchLump lump = ctx.companionLump;
        if (lump == null || !Double.isFinite(lump.centroidWx) || !Double.isFinite(lump.centroidWy)) {
            return null;
        }
        double sx = PAD + ctx.availW / 2.0 + (lump.centroidWx - ctx.vcx) * ctx.scale;
        double sy = PAD + ctx.availH / 2.0 - (lump.centroidWy - ctx.vcy) * ctx.scale;
        if (!Double.isFinite(sx) || !Double.isFinite(sy)) {
            return null;
        }
        float hitR = Math.max(subsystemMoonHubRingOuterRadiusPx(ctx.bodyR),
                Math.max(2.5f, ctx.bodyR * 0.52f)) + MAP_HIT_BODY_PAD_PX;
        if (pointInCircle(px, py, (float) sx, (float) sy, hitR)) {
            return MapClickHit.companionLump(lump.hubBodyId, lump.summaryLabel);
        }
        return null;
    }

    private MapClickHit hitTestBodyDot(MapClickPaintCtx ctx, int px, int py) {
        List<BodyDot> ordered = new ArrayList<>(dots);
        Collections.reverse(ordered);
        for (BodyDot d : ordered) {
            if (d == null) {
                continue;
            }
            if (!d.star && ctx.labelPlan.summaryClusterMemberIds.contains(Integer.valueOf(d.bodyId))
                    || (ctx.companionLump != null && ctx.companionLump.contains(d.bodyId))
                    || hideDotForSubsystemLumpView(d, ctx.visibleLsMinAxis)) {
                continue;
            }
            boolean lockHub = subsystemScreenLockHubId >= 0 && d.bodyId == subsystemScreenLockHubId;
            double[] cxy = bodyDotScreenMetres(d, lockHub, ctx.vcx, ctx.vcy, ctx.scale, ctx.availW, ctx.availH,
                    ctx.plotCx, ctx.plotCy);
            float sx = (float) cxy[0];
            float sy = (float) cxy[1];
            float r = bodyDotHitRadiusPx(ctx, d);
            if (pointInCircle(px, py, sx, sy, r)) {
                return MapClickHit.body(d, orbitGeomBodies != null ? orbitGeomBodies.get(Integer.valueOf(d.bodyId)) : null,
                        mapModel, planetaryRingsDecorWouldDrawForTests(d.bodyId, ctx.visibleLsMinAxis));
            }
        }
        return null;
    }

    private float bodyDotHitRadiusPx(MapClickPaintCtx ctx, BodyDot d) {
        float r = mapBodyDotRadiusPx(d, ctx.starR, ctx.bodyR, zoomFactor, ctx.showClusterDetail);
        boolean lumpHub = subsystemHubLump(ctx.visibleLsMinAxis, d);
        boolean moonHostHub = !d.star && subsystemHubLumpBodyIds.contains(Integer.valueOf(d.bodyId));
        if (moonHostHub && lumpHub) {
            r = Math.max(r, subsystemMoonHubRingOuterRadiusPx(r));
        }
        if (planetaryRingsDecorWouldDrawForTests(d.bodyId, ctx.visibleLsMinAxis)) {
            r = Math.max(r, r * 2.85f);
        }
        return r + MAP_HIT_BODY_PAD_PX;
    }

    private MapClickHit hitTestOrbitStroke(MapClickPaintCtx ctx, int px, int py) {
        if (orbitLines == null || orbitLines.isEmpty()) {
            return null;
        }
        boolean detailOrbits = ctx.showClusterDetail;
        double bestDistSq = MAP_HIT_ORBIT_STROKE_PX * MAP_HIT_ORBIT_STROKE_PX;
        OrbitPolylineWorldXY best = null;
        for (OrbitPolylineWorldXY poly : orbitLines) {
            if (poly == null || poly.wx == null || poly.wy == null
                    || poly.wx.length < 3 || poly.wy.length != poly.wx.length) {
                continue;
            }
            if (skipOrbitPolylineForCompanionLump(poly, ctx.companionLump, detailOrbits)) {
                continue;
            }
            if (skipOrbitPolylineForSubsystemLump(poly, ctx.visibleLsMinAxis, ctx.showClusterDetail)) {
                continue;
            }
            if (skipOversizeSchematicRingForDetailView(poly, ctx.visibleLsMinAxis, ctx.vcx, ctx.vcy, ctx.scale,
                    ctx.availW, ctx.availH, detailOrbits)) {
                continue;
            }
            double distSq = minDistSqToClosedPolylineScreen(poly, ctx, px, py);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = poly;
            }
        }
        if (best == null) {
            return null;
        }
        OrbitStrokeHitInfo info = SystemMapOrbitStrokePrinter.orbitStrokeHitInfo(best, orbitGeomBodies, mapModel);
        return MapClickHit.orbitStroke(info);
    }

    private static double minDistSqToClosedPolylineScreen(OrbitPolylineWorldXY poly, MapClickPaintCtx ctx, int px,
            int py) {
        int n = poly.wx.length;
        double minSq = Double.POSITIVE_INFINITY;
        double prevSx = Double.NaN;
        double prevSy = Double.NaN;
        double firstSx = Double.NaN;
        double firstSy = Double.NaN;
        for (int i = 0; i < n; i++) {
            double sx = PAD + ctx.availW / 2.0 + (poly.wx[i] - ctx.vcx) * ctx.scale;
            double sy = PAD + ctx.availH / 2.0 - (poly.wy[i] - ctx.vcy) * ctx.scale;
            if (!Double.isFinite(sx) || !Double.isFinite(sy)) {
                prevSx = Double.NaN;
                continue;
            }
            if (i == 0) {
                firstSx = sx;
                firstSy = sy;
            }
            if (Double.isFinite(prevSx)) {
                minSq = Math.min(minSq, distPointToSegmentSq(px, py, prevSx, prevSy, sx, sy));
            }
            prevSx = sx;
            prevSy = sy;
        }
        if (Double.isFinite(prevSx) && Double.isFinite(firstSx)) {
            minSq = Math.min(minSq, distPointToSegmentSq(px, py, prevSx, prevSy, firstSx, firstSy));
        }
        return minSq;
    }

    private static double distPointToSegmentSq(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;
        if (lenSq < 1e-12) {
            double ddx = px - x1;
            double ddy = py - y1;
            return ddx * ddx + ddy * ddy;
        }
        double t = clamp((px - x1) * dx + (py - y1) * dy, 0.0, 1.0) / lenSq;
        double projX = x1 + t * dx;
        double projY = y1 + t * dy;
        double ddx = px - projX;
        double ddy = py - projY;
        return ddx * ddx + ddy * ddy;
    }

    private static boolean pointInCircle(int px, int py, float cx, float cy, float r) {
        if (r <= 0f) {
            return false;
        }
        double dx = px - cx;
        double dy = py - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private List<BarycentreMarkerHit> collectBarycentreMarkersForHit(CompanionBranchLump companionLump) {
        List<BarycentreMarkerHit> out = new ArrayList<>();
        if (mapModel == null) {
            return out;
        }
        Map<Integer, double[]> positions = mapModel.positionsMetres();
        if (positions == null || positions.isEmpty()) {
            return out;
        }
        int p0 = mapModel.projectionAxis0();
        int p1 = mapModel.projectionAxis1();
        int needLen = Math.max(p0, p1) + 1;
        Set<Integer> drawn = new HashSet<>();
        if (orbitGeomBodies != null) {
            for (Map.Entry<Integer, BodyInfo> e : orbitGeomBodies.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || !e.getValue().isScanBarycentreRow()) {
                    continue;
                }
                int scanKey = e.getKey().intValue();
                if (shouldSuppressSystemBarycentreMarker(scanKey, positions, p0, p1, needLen)) {
                    continue;
                }
                appendBarycentreMarkerForHit(out, drawn, positions, scanKey, p0, p1, needLen, companionLump);
            }
        }
        for (Map.Entry<Integer, double[]> e : positions.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int key = e.getKey().intValue();
            if (!SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(key)) {
                continue;
            }
            int nullId = SystemOrbitGeometry.PLANET_BINARY_BARYCENTRE_MAP_KEY_BASE - key;
            if (orbitGeomBodies != null && orbitGeomBodies.containsKey(Integer.valueOf(nullId))) {
                continue;
            }
            appendBarycentreMarkerForHit(out, drawn, positions, key, p0, p1, needLen, companionLump);
        }
        return out;
    }

    private static void appendBarycentreMarkerForHit(List<BarycentreMarkerHit> out, Set<Integer> drawn,
            Map<Integer, double[]> positions, int mapKey, int p0, int p1, int needLen,
            CompanionBranchLump companionLump) {
        if (!drawn.add(Integer.valueOf(mapKey))) {
            return;
        }
        double[] p = positions.get(Integer.valueOf(mapKey));
        if (p == null || p.length < needLen) {
            return;
        }
        double x = SystemOrbitGeometry.worldAxisMetres(p, p0);
        double y = SystemOrbitGeometry.worldAxisMetres(p, p1);
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return;
        }
        if (suppressBarycentreMarkerForCompanionLump(x, y, companionLump)) {
            return;
        }
        out.add(new BarycentreMarkerHit(mapKey, x, y));
    }

    private static final class MapClickPaintCtx {
        final FontMetrics labelFm;
        final Font labelFont;
        final double availW;
        final double availH;
        final double plotCx;
        final double plotCy;
        final double scale;
        final double vcx;
        final double vcy;
        final double visibleLsMinAxis;
        final boolean showClusterDetail;
        final boolean showMoonLabels;
        final boolean showAllBodyLabels;
        final CompanionBranchLump companionLump;
        final MapLabelDrawPlan labelPlan;
        final float starR;
        final float bodyR;
        final float dotEm;
        final List<BarycentreMarkerHit> baryMarkers;

        MapClickPaintCtx(FontMetrics labelFm, Font labelFont, double availW, double availH, double plotCx,
                double plotCy, double scale, double vcx, double vcy, double visibleLsMinAxis,
                boolean showClusterDetail, boolean showMoonLabels, boolean showAllBodyLabels,
                CompanionBranchLump companionLump, MapLabelDrawPlan labelPlan, float starR, float bodyR, float dotEm,
                List<BarycentreMarkerHit> baryMarkers) {
            this.labelFm = labelFm;
            this.labelFont = labelFont;
            this.availW = availW;
            this.availH = availH;
            this.plotCx = plotCx;
            this.plotCy = plotCy;
            this.scale = scale;
            this.vcx = vcx;
            this.vcy = vcy;
            this.visibleLsMinAxis = visibleLsMinAxis;
            this.showClusterDetail = showClusterDetail;
            this.showMoonLabels = showMoonLabels;
            this.showAllBodyLabels = showAllBodyLabels;
            this.companionLump = companionLump;
            this.labelPlan = labelPlan;
            this.starR = starR;
            this.bodyR = bodyR;
            this.dotEm = dotEm;
            this.baryMarkers = baryMarkers != null ? baryMarkers : List.of();
        }
    }

    private static final class BarycentreMarkerHit {
        final int mapKey;
        final double wx;
        final double wy;

        BarycentreMarkerHit(int mapKey, double wx, double wy) {
            this.mapKey = mapKey;
            this.wx = wx;
            this.wy = wy;
        }
    }

    private static final class MapClickHit {
        enum Kind {
            BODY, ORBIT_STROKE, BARYCENTRE, LABEL, EXOBIOLOGY_LEAF, COMPANION_LUMP, SUMMARY_CLUSTER, COMMANDER_GLYPH
        }

        final Kind kind;
        final int bodyId;
        final String shortName;
        final String extra;

        private MapClickHit(Kind kind, int bodyId, String shortName, String extra) {
            this.kind = kind;
            this.bodyId = bodyId;
            this.shortName = shortName != null ? shortName : "";
            this.extra = extra != null ? extra : "";
        }

        static MapClickHit body(BodyDot d, BodyInfo b, SystemMapModel model, boolean ringDecor) {
            SystemMapDotKind dotKind = ExplorationBodyCredits.systemMapDotKind(b, d != null && d.star);
            int parent = model != null ? model.resolveParentBodyId(d.bodyId) : -1;
            double distLs = b != null && Double.isFinite(b.getDistanceLs()) ? b.getDistanceLs() : Double.NaN;
            StringJoiner j = new StringJoiner(" ");
            j.add("kind=" + dotKind.name());
            j.add("parent=" + parent);
            if (Double.isFinite(distLs)) {
                j.add(String.format(Locale.US, "distanceLs=%.0f", distLs));
            }
            if (d != null && d.moon) {
                j.add("moon=true");
            }
            if (d != null && d.star) {
                j.add("star=true");
            }
            if (ringDecor) {
                j.add("planetaryRings=true");
            }
            return new MapClickHit(Kind.BODY, d.bodyId, d.label, j.toString());
        }

        static MapClickHit orbitStroke(OrbitStrokeHitInfo info) {
            String extra = String.format(Locale.US, "polylineBodyId=%d type=%s estimated=%s",
                    info.polylineBodyId, info.type, info.estimated);
            if (info.parentBodyId >= 0) {
                extra += String.format(Locale.US, " parentBodyId=%d", info.parentBodyId);
            }
            if (info.curveKey != null) {
                extra += " curveKey=" + info.curveKey;
            }
            return new MapClickHit(Kind.ORBIT_STROKE, info.polylineBodyId, "", extra);
        }

        static MapClickHit barycentre(int mapKey, double wx, double wy) {
            String extra = String.format(Locale.US, "mapKey=%d wx=%.1f wy=%.1f", mapKey, wx, wy);
            return new MapClickHit(Kind.BARYCENTRE, mapKey, "Null", extra);
        }

        static MapClickHit label(int bodyId, String text) {
            return new MapClickHit(Kind.LABEL, bodyId, text, "clickableLabel=true");
        }

        static MapClickHit exobiology(int bodyId, String label) {
            return new MapClickHit(Kind.EXOBIOLOGY_LEAF, bodyId, label, "exobiologyLeaf=true");
        }

        static MapClickHit companionLump(int hubBodyId, String summary) {
            return new MapClickHit(Kind.COMPANION_LUMP, hubBodyId, summary, "companionBranchLump=true");
        }

        static MapClickHit summaryCluster(int hubBodyId, String text) {
            return new MapClickHit(Kind.SUMMARY_CLUSTER, hubBodyId, text, "summaryCluster=true");
        }

        static MapClickHit commanderGlyph(int bodyId, String label) {
            return new MapClickHit(Kind.COMMANDER_GLYPH, bodyId, label, "detachedCommanderGlyph=true");
        }

        String formatLine() {
            return switch (kind) {
                case BODY -> String.format(Locale.US,
                        "[EDO][OrbitMap][Click] body shortName=%s id=%d mapKey=%d %s",
                        shortName, bodyId, bodyId, extra);
                case ORBIT_STROKE -> String.format(Locale.US, "[EDO][OrbitMap][Click] orbitStroke %s", extra);
                case BARYCENTRE -> String.format(Locale.US,
                        "[EDO][OrbitMap][Click] barycentreMarker %s", extra);
                case LABEL -> String.format(Locale.US,
                        "[EDO][OrbitMap][Click] label bodyId=%d text=%s %s", bodyId, shortName, extra);
                case EXOBIOLOGY_LEAF -> String.format(Locale.US,
                        "[EDO][OrbitMap][Click] exobiologyLeaf bodyId=%d shortName=%s %s",
                        bodyId, shortName, extra);
                case COMPANION_LUMP -> String.format(Locale.US,
                        "[EDO][OrbitMap][Click] companionLump hubBodyId=%d summary=%s %s",
                        bodyId, shortName, extra);
                case SUMMARY_CLUSTER -> String.format(Locale.US,
                        "[EDO][OrbitMap][Click] summaryCluster hubBodyId=%d text=%s %s",
                        bodyId, shortName, extra);
                case COMMANDER_GLYPH -> String.format(Locale.US,
                        "[EDO][OrbitMap][Click] commanderGlyph bodyId=%d shortName=%s %s",
                        bodyId, shortName, extra);
            };
        }
    }

    // --- Package-private test hooks (R-DRAW translation contract; org.dce.ed.ui tests only) ---

    final int mapClickHitBodyIdForTests(int px, int py) {
        MapClickHit hit = findMapClickHit(px, py);
        if (hit == null || hit.kind != MapClickHit.Kind.BODY) {
            return -1;
        }
        return hit.bodyId;
    }

    /** Screen px of a body dot centre using the same layout as click hit-testing. */
    final float[] bodyScreenPxForClickHitTests(int bodyId) {
        MapClickPaintCtx ctx = buildMapClickPaintCtx();
        if (ctx == null) {
            return new float[] { Float.NaN, Float.NaN };
        }
        for (BodyDot d : dots) {
            if (d != null && d.bodyId == bodyId) {
                boolean lockHub = subsystemScreenLockHubId >= 0 && d.bodyId == subsystemScreenLockHubId;
                double[] cxy = bodyDotScreenMetres(d, lockHub, ctx.vcx, ctx.vcy, ctx.scale, ctx.availW, ctx.availH,
                        ctx.plotCx, ctx.plotCy);
                return new float[] { (float) cxy[0], (float) cxy[1] };
            }
        }
        return new float[] { Float.NaN, Float.NaN };
    }

    final SystemMapModel mapModelForTests() {
        return mapModel;
    }

    final List<OrbitPolylineWorldXY> orbitLinesForTests() {
        return orbitLines != null ? List.copyOf(orbitLines) : List.of();
    }

    final List<BodyDot> dotsForTests() {
        return dots != null ? List.copyOf(dots) : List.of();
    }

    final boolean hasBodyDotForTests(int bodyId) {
        for (BodyDot d : dots) {
            if (d.bodyId == bodyId) {
                return true;
            }
        }
        return false;
    }

    final double dotWorldXForTests(int bodyId) {
        for (BodyDot d : dots) {
            if (d.bodyId == bodyId) {
                return d.wx;
            }
        }
        return Double.NaN;
    }

    final double dotWorldYForTests(int bodyId) {
        for (BodyDot d : dots) {
            if (d.bodyId == bodyId) {
                return d.wy;
            }
        }
        return Double.NaN;
    }

    final boolean mapShowMoonLabelsForTests(double visibleLsMinAxis) {
        return mapShowMoonLabels(visibleLsMinAxis);
    }

    final boolean mapShowClusterDetailForTests(double visibleLsMinAxis) {
        return mapShowClusterDetail(visibleLsMinAxis);
    }

    /** Moon-host hub twin-ring cue ({@link #drawSubsystemMoonHubDoubleRing}) — lump view only. */
    final boolean hideDotForSubsystemLumpViewForTests(int bodyId, double visibleLsMinAxis) {
        for (BodyDot d : dots) {
            if (d != null && d.bodyId == bodyId) {
                return hideDotForSubsystemLumpView(d, visibleLsMinAxis);
            }
        }
        return false;
    }

    final boolean subsystemHubLumpLeafWouldDrawForTests(int hubBodyId, double visibleLsMinAxis) {
        for (BodyDot d : dots) {
            if (d != null && d.bodyId == hubBodyId) {
                return subsystemHubLump(visibleLsMinAxis, d) && subsystemHubHasExobiologyDescendant(hubBodyId);
            }
        }
        return false;
    }

    /**
     * Mirrors {@code starHostedRevolutionCentre} / moon-host hub branches that call
     * {@link #drawSubsystemHubRevolutionPathRing} in the body-dot paint loop (eligibility only).
     */
    final boolean subsystemHubRevolutionPathRingEligibleForTests(int bodyId, double visibleLsMinAxis) {
        if (mapModel == null || orbitGeomBodies == null) {
            return false;
        }
        for (BodyDot d : dots) {
            if (d == null || d.bodyId != bodyId || d.star) {
                continue;
            }
            boolean ringedOrbitCentre = mapModel.isOrbitRevolutionCenter(d.bodyId);
            int parentId = mapModel.resolveParentBodyId(d.bodyId);
            BodyInfo parent = parentId >= 0 ? orbitGeomBodies.get(Integer.valueOf(parentId)) : null;
            boolean starHostedRevolutionCentre = ringedOrbitCentre && parentId >= 0
                    && SystemMapRules.isMapStellarBody(parent);
            boolean moonHostHub = subsystemHubLumpBodyIds.contains(Integer.valueOf(d.bodyId));
            boolean lumpHub = subsystemHubLump(visibleLsMinAxis, d);
            boolean hubTwinBlueRings = moonHostHub && lumpHub;
            return hubTwinBlueRings || starHostedRevolutionCentre;
        }
        return false;
    }

    /** {@link #drawSubsystemHubRevolutionPathRing} is schematic-only. */
    final boolean subsystemHubRevolutionPathRingDrawnForTests(int bodyId, double visibleLsMinAxis) {
        return !mapScaleMode.trueScale()
                && subsystemHubRevolutionPathRingEligibleForTests(bodyId, visibleLsMinAxis);
    }

    /** Radius (Ls) of the paint-only hub ring through the body's current map position. */
    final double hubRevolutionRingRadiusLsForTests(int bodyId) {
        if (mapModel == null) {
            return Double.NaN;
        }
        BodyDot dot = findBodyDot(bodyId);
        if (dot == null || !Double.isFinite(dot.wx) || !Double.isFinite(dot.wy)) {
            return Double.NaN;
        }
        int parentId = mapModel.resolveParentBodyId(bodyId);
        if (parentId < 0) {
            return Double.NaN;
        }
        double px = mapModel.mapPlaneX(parentId);
        double py = mapModel.mapPlaneY(parentId);
        if (!Double.isFinite(px) || !Double.isFinite(py)) {
            return Double.NaN;
        }
        double radM = Math.hypot(dot.wx - px, dot.wy - py);
        return radM / SystemOrbitGeometry.LIGHT_SECOND_METRES;
    }

    final boolean hubTwinBlueRingsWouldDrawForTests(int bodyId, double visibleLsMinAxis) {
        boolean showClusterDetail = mapShowClusterDetail(visibleLsMinAxis);
        CompanionBranchLump lump = buildCompanionBranchRevolutionLump(dots, showClusterDetail, visibleLsMinAxis);
        if (lump != null && lump.contains(bodyId)) {
            return bodyId == lump.hubBodyId;
        }
        for (BodyDot d : dots) {
            if (d != null && d.bodyId == bodyId) {
                return !d.star && subsystemHubLumpBodyIds.contains(Integer.valueOf(bodyId))
                        && subsystemHubLump(visibleLsMinAxis, d);
            }
        }
        return false;
    }

    /** Mirrors red planetary-ring decor visibility in the body-dot paint loop. */
    final boolean planetaryRingsDecorWouldDrawForTests(int bodyId, double visibleLsMinAxis) {
        boolean showClusterDetail = mapShowClusterDetail(visibleLsMinAxis);
        CompanionBranchLump lump = buildCompanionBranchRevolutionLump(dots, showClusterDetail, visibleLsMinAxis);
        if (lump != null && lump.contains(bodyId) && bodyId != lump.hubBodyId) {
            return false;
        }
        for (BodyDot d : dots) {
            if (d == null || d.bodyId != bodyId) {
                continue;
            }
            if (hideDotForSubsystemLumpView(d, visibleLsMinAxis)) {
                return false;
            }
            if (!d.hasPlanetaryRings) {
                return false;
            }
            boolean ringHubOrSolo = !d.star
                    && (d.soleOrbitCluster || subsystemHubLumpBodyIds.contains(Integer.valueOf(bodyId)));
            boolean ringedOrbitCentre = !d.star && mapModel != null
                    && mapModel.isOrbitRevolutionCenter(bodyId);
            return ringHubOrSolo || ringedOrbitCentre
                    || (showClusterDetail && zoomFactor >= ZOOM_MAP_BODY_RINGS);
        }
        return false;
    }

    final CompanionBranchLump companionBranchLumpForTests(double visibleLsMinAxis) {
        return buildCompanionBranchRevolutionLump(dots, mapShowClusterDetail(visibleLsMinAxis), visibleLsMinAxis);
    }

    final boolean skipOrbitPolylineForCompanionLumpForTests(OrbitPolylineWorldXY poly,
            CompanionBranchLump lump, boolean detailOrbits) {
        return skipOrbitPolylineForCompanionLump(poly, lump, detailOrbits);
    }

    final void zoomFactorForTests(double zoom) {
        zoomFactor = zoom;
        lastOrbitRebuildKey = Long.MIN_VALUE;
    }

    final String dotLabelForTests(int bodyId) {
        for (BodyDot d : dots) {
            if (d.bodyId == bodyId) {
                return d.label;
            }
        }
        return null;
    }

    final void rebuildOrbitPolylinesForTests(boolean force, boolean useScreenChordScaleForSegments) {
        rebuildOrbitPolylines(force, useScreenChordScaleForSegments);
    }

    final boolean bodyLabelWouldDrawForTests(int bodyId, double visibleLsMinAxis) {
        BodyDot dot = null;
        for (BodyDot d : dots) {
            if (d.bodyId == bodyId) {
                dot = d;
                break;
            }
        }
        if (dot == null) {
            return false;
        }
        double visible = visibleLsMinAxis;
        boolean showClusterDetail = mapShowClusterDetail(visible);
        CompanionBranchLump lump = buildCompanionBranchRevolutionLump(dots, showClusterDetail, visible);
        return bodyDotLabelWouldDraw(dot, showClusterDetail, mapShowMoonLabels(visible),
                mapShowAllBodyLabels(visible), lump);
    }

    final boolean skipOversizeSchematicRingForTests(OrbitPolylineWorldXY poly, double visibleLsMinAxis,
            double viewCenterWx, double viewCenterWy) {
        return skipOversizeSchematicRingForTests(poly, visibleLsMinAxis, viewCenterWx, viewCenterWy, 0.02, 876.0, 676.0,
                true);
    }

    final boolean skipOversizeSchematicRingForTests(OrbitPolylineWorldXY poly, double visibleLsMinAxis,
            double viewCenterWx, double viewCenterWy, double scale, double availW, double availH,
            boolean detailOrbits) {
        return skipOversizeSchematicRingForDetailView(poly, visibleLsMinAxis, viewCenterWx, viewCenterWy, scale, availW,
                availH, detailOrbits);
    }

    final int barycentreMarkerCountForTests() {
        if (mapModel == null) {
            return 0;
        }
        Map<Integer, double[]> positions = mapModel.positionsMetres();
        if (positions == null || positions.isEmpty()) {
            return 0;
        }
        int p0 = mapModel.projectionAxis0();
        int p1 = mapModel.projectionAxis1();
        int needLen = Math.max(p0, p1) + 1;
        Set<Integer> drawn = new HashSet<>();
        List<double[]> markers = new ArrayList<>();
        if (orbitGeomBodies != null) {
            for (Map.Entry<Integer, BodyInfo> e : orbitGeomBodies.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || !e.getValue().isScanBarycentreRow()) {
                    continue;
                }
                collectBarycentreMarker(markers, drawn, positions, e.getKey().intValue(), p0, p1, needLen);
            }
        }
        for (Map.Entry<Integer, double[]> e : positions.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            int key = e.getKey().intValue();
            if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(key)) {
                collectBarycentreMarker(markers, drawn, positions, key, p0, p1, needLen);
            }
        }
        return markers.size();
    }

    final Map<Integer, float[]> revolutionCenterLabelAnchorsForTests(List<BodyDot> dots, double visibleLsMinAxis,
            FontMetrics fm, double vcx, double vcy, double scale, double availW, double availH, double plotCx,
            double plotCy) {
        boolean showClusterDetail = mapShowClusterDetail(visibleLsMinAxis);
        boolean showMoonLabels = mapShowMoonLabels(visibleLsMinAxis);
        boolean showAllBodyLabels = mapShowAllBodyLabels(visibleLsMinAxis);
        CompanionBranchLump lump = buildCompanionBranchRevolutionLump(dots, showClusterDetail, visibleLsMinAxis);
        return buildCollisionAwareLabelPlan(dots, showClusterDetail, showMoonLabels, showAllBodyLabels, lump, fm, vcx,
                vcy, scale, availW, availH, plotCx, plotCy).anchors;
    }

    final double mapPlotScaleForTests(double availW, double availH) {
        return computeMapPlotScale(availW, availH, layoutSpanX, layoutSpanY);
    }

    final MapLabelDrawPlan labelDrawPlanForTests(List<BodyDot> dots, double visibleLsMinAxis, FontMetrics fm,
            double vcx, double vcy, double scale, double availW, double availH, double plotCx, double plotCy) {
        boolean showClusterDetail = mapShowClusterDetail(visibleLsMinAxis);
        boolean showMoonLabels = mapShowMoonLabels(visibleLsMinAxis);
        boolean showAllBodyLabels = mapShowAllBodyLabels(visibleLsMinAxis);
        CompanionBranchLump lump = buildCompanionBranchRevolutionLump(dots, showClusterDetail, visibleLsMinAxis);
        return buildCollisionAwareLabelPlan(dots, showClusterDetail, showMoonLabels, showAllBodyLabels, lump, fm, vcx,
                vcy, scale, availW, availH, plotCx, plotCy);
    }

    private static final class BodyDot {
        final int bodyId;
        double wx;
        double wy;
        final String label;
        final boolean star;
        /** Short name equals system name — draw {@code *} only (matches System tab). */
        final boolean primaryStarAsterisk;
        /** Single-star anchor: small schematic dot + {@code *}, not a large branch-star glyph or hub rings. */
        final boolean loneCentralPrimary;

        final boolean moon;
        final boolean giantPlanet;
        final boolean hasPlanetaryRings;
        /** No other body orbits this one (no moons) — ring art always shown when this body has rings. */
        final boolean soleOrbitCluster;
        final boolean hasExobiology;
        /** Journal distance / SMA missing — position from pseudo layout. */
        final boolean estimatedPosition;

        BodyDot(int bodyId, double wx, double wy, String label, boolean star, boolean primaryStarAsterisk,
                boolean loneCentralPrimary, boolean moon, boolean giantPlanet, boolean hasPlanetaryRings,
                boolean soleOrbitCluster, boolean hasExobiology, boolean estimatedPosition) {
            this.bodyId = bodyId;
            this.wx = wx;
            this.wy = wy;
            this.label = label;
            this.star = star;
            this.primaryStarAsterisk = primaryStarAsterisk;
            this.loneCentralPrimary = loneCentralPrimary;
            this.moon = moon;
            this.giantPlanet = giantPlanet;
            this.hasPlanetaryRings = hasPlanetaryRings;
            this.soleOrbitCluster = soleOrbitCluster;
            this.hasExobiology = hasExobiology;
            this.estimatedPosition = estimatedPosition;
        }
    }
}
