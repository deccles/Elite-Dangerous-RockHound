package org.dce.ed;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import javax.swing.BorderFactory;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import org.dce.ed.cache.CachedSystem;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.edsm.BodiesResponse;
import org.dce.ed.exobiology.ExobiologyData.BioCandidate;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.CarrierLocationEvent;
import org.dce.ed.logreader.event.BioScanPredictionEvent;
import org.dce.ed.logreader.event.BioScanPredictionEvent.PredictionKind;
import org.dce.ed.logreader.event.ApproachBodyEvent;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.IFsdJump;
import org.dce.ed.logreader.event.LeaveBodyEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.logreader.event.SupercruiseExitEvent;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.ScanParents;
import org.dce.ed.util.ExplorationBodyCredits;
import org.dce.ed.util.ValuableBodyExplorationEstimate;
import org.dce.ed.state.SystemEventProcessor;
import org.dce.ed.state.SystemState;
import org.dce.ed.tts.PollyTtsCached;
import org.dce.ed.tts.TtsSprintf;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.PassThroughScrollSupport;
import org.dce.ed.ui.SubtleScrollBarUI;
import org.dce.ed.util.EdsmClient;
import org.dce.ed.util.FirstBonusHelper;
import org.dce.ed.systemmap.SystemMapRules;
import org.dce.ed.systemmap.SystemSession;
import org.dce.ed.systemmap.SystemSessionFactory;
import org.dce.ed.systemmodel.SystemModelService;
import org.dce.ed.util.SystemOrbitGeometry;

import org.dce.ed.ui.DistanceToggleIcons;
import org.dce.ed.ui.EdoMiningSplitPaneUi;
import org.dce.ed.ui.HoverClickPoller;
import org.dce.ed.ui.OrbitPlaybackTransportIcons;
import org.dce.ed.ui.LeafIcon;
import org.dce.ed.ui.SystemPlanMapPanel;
/**
 * System tab – now a *pure UI* renderer.
 *
 * All parsing, prediction, and system-state logic lives in:
 *   SystemState
 *   SystemEventProcessor
 *   SystemCache
 */
public class SystemTabPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int ORBIT_ANIM_SPEED_STEP = 5;
    /**
     * Wall period between orbit-playback ticks; sim advance uses this fixed interval (not variable EDT gaps) so a
     * delayed paint cannot advance many in-game days in one frame at high d/s. Kept well below video frame rate:
     * map positions change slowly on screen and each tick rebuilds orbit polylines.
     */
    private static final int ORBIT_ANIM_TIMER_MS = 150;

    /** Debounce before recomputing ship-centric table distances after Status telemetry (no near-body change). */
    private static final int SHIP_TELEMETRY_REBUILD_DEBOUNCE_MS = 2_000;

    /**
     * Background check for ship-centric distances while the commander is stationary; journal orbits move bodies
     * slowly relative to displayed whole-number Ls.
     */
    private static final int ORBIT_EVOLUTION_REBUILD_INTERVAL_MS = 30_000;

    // Bio column icons (painted, no external resources) - scaled from current UI font.
    private Icon bioLeafIcon = new LeafIcon(18, 18);
    private Icon bioDollarIcon = new DollarIcon(16, 16);
    private Icon bioGeoIcon = new RingedPlanetIcon(16, 16);
    private Icon landSneakerIcon = new SneakerIcon(16, 10);
    /** Earth-like marble cue for high exploration-value bodies (ELW / WW / AW / terraformable). */
    private Icon valuableEarthBodyIcon = new CachedIcon(new EarthLikeBodyIcon(16, 16));

    // NEW: semi-transparent orange for separators, similar to RouteTabPanel
    // NEW: shared ED font (similar to Route tab)
        private Font uiFont = OverlayPreferences.getUiFont();

    private final JTable table;
    /** Main bodies table scroller (pass-through wheel forwarding). */
    private final JScrollPane systemBodyScrollPane;
    /** Resizable split between bodies table (top) and plan map (bottom), like Mining tab dividers. */
    private JSplitPane systemTableMapSplit;
    /** Map toolbar (kept visible when the plan map canvas is collapsed to the tab bottom). */
    private JPanel systemPlanMapToolbar;
    /** Collapse / restore plan map height (toolbar stays docked at bottom). */
    private JButton systemPlanMapCollapseButton;
    private JButton systemPlanMapExpandButton;
    private boolean systemPlanMapCollapseHovered;
    private boolean systemPlanMapExpandHovered;
    private boolean systemPlanMapCollapsed;
    /** View tilt label + slider + value (pass-through hover hit target). */
    private JPanel mapViewTiltCluster;
    private boolean mapViewTiltHovered;
    /** Pass-through scrub on the tilt slider ({@link JSlider#setValueIsAdjusting}). */
    private boolean mapViewTiltPassThroughAdjusting;
    /** Table/map split ratio saved before collapse; restored by expand. */
    private double systemPlanMapSplitRatioBeforeCollapse = OverlayPreferences.getSystemTabPanelTableSplitRatio();
    /** Bottom panel: top-down plan of approximate body and ship positions. */
    private final SystemPlanMapPanel systemPlanMapPanel = new SystemPlanMapPanel();
    private final JTextField headerLabel;
    private final SystemBodiesTableModel tableModel;
    private DefaultTableCellRenderer systemBodiesTextCellRenderer;

    /** Body column is never truncated; atmo column shrinks first when the viewport is narrow. */
    private static final int SYSTEM_TABLE_BODY_COL_PAD_PX = 12;
    private static final int SYSTEM_TABLE_ATMO_COL_MIN_PX = 40;
    private static final int SYSTEM_TABLE_BIO_COL_MIN_PX = 145;
    private static final int SYSTEM_TABLE_VALUE_COL_MIN_PX = 44;
    private static final int SYSTEM_TABLE_LAND_COL_MIN_PX = 28;
    private static final int SYSTEM_TABLE_DIST_COL_MIN_PX = 56;

    private final SystemState state = new SystemState();
    private volatile SystemSession systemSession;
    private final SystemEventProcessor processor = new SystemEventProcessor(EliteDangerousOverlay.clientKey, state, new EdsmClient());

    private final EdsmClient edsmClient = new EdsmClient();
    private final TtsSprintf firstDiscoveredSystemTts = new TtsSprintf(new PollyTtsCached());
    private final Set<String> announcedFirstDiscoveredSystemKeys = Collections.synchronizedSet(new HashSet<>());

    // When we receive Status.json indicating we're near/on a body, we highlight that body and its bio rows.
    // Stored as bodyId so it remains stable even if the display name changes slightly.
    private volatile Integer nearBodyId;
    private volatile String nearBodyName;
    private volatile Consumer<BodyInfo> nearBodyChangedListener;
    
    // When a body is actively targeted (Status.json Destination.Body), we outline that body block.
    private volatile Integer targetBodyId;
    private volatile String targetBodyName;

    // When a station/carrier is targeted, Destination.Body is the parent body and Destination.DisplayName is the station/carrier.
    private volatile Integer targetDestinationParentBodyId;
    private volatile String targetDestinationName;

    /** System tab sort toggles: rocket = from ship, star = from arrival, $ = by exploration value. */
    private JButton distFromShipButton;
    private JButton distFromStarButton;
    private JButton distByValueButton;
    private boolean distFromShipHovered;
    private boolean distFromStarHovered;
    private boolean distByValueHovered;

    /** Latest Status.json geometry when near a body (for ship-centric distances). */
    private volatile Double statusLatitude;
    private volatile Double statusLongitude;
    private volatile Double statusAltitude;
    private volatile Double statusPlanetRadius;

    /**
     * Journal {@link ApproachBodyEvent}: ship entered a body's orbital-cruise zone — best anchor for
     * ship-centric distances while supercruising near that world. Never set from FSS/DSS {@code Scan} lines.
     */
    private volatile Integer approachReferenceBodyId;

    /**
     * Journal {@code Scan} with {@code ScanType: Detailed} (DSS mapping) — ship-ref anchor only, not table proximity.
     */
    private volatile Integer dssDetailedScanReferenceBodyId;

    /**
     * Journal {@link SupercruiseExitEvent}: major body (or ring parent) you dropped to from system supercruise.
     * Used when you never get {@code ApproachBody} (wide orbit) but Status still omits a stable near-body id.
     * Cleared on system supercruise ({@link StatusEvent#isSupercruise()}) and on system-change events.
     */
    private volatile Integer supercruiseDropReferenceBodyId;

    /**
     * Last non-primary (non-star) body we treated as proximity — kept across Status ticks so HUD destination
     * mirroring does not yank "You" off a world until ApproachBody / telemetry / physical name updates.
     * Persisted per {@link SystemState#getSystemAddress()} via {@link OverlayPreferences} so it survives app restarts.
     */
    private volatile Integer lastVisitedNonStarBodyId;

    /**
     * Last HUD navigation body id for {@link SystemTabShipRefMode#TARGETED_BODY}: kept after the HUD target clears
     * until another in-system body is targeted. Persisted per {@link SystemState#getSystemAddress()}.
     */
    private volatile Integer stickyHudTargetBodyId;

    /**
     * After {@link #refreshPlanMap()}, animate the plan map to this body’s orbit cluster (HUD target auto-zoom).
     */
    private volatile Integer pendingHudTargetMapZoomBodyId;

    /**
     * Last {@link SystemState#isDocked()} seen from Status (after {@link SystemEventProcessor}); used to refresh
     * ship-anchor distances and the plan map when docked toggles without a near-body name/id change.
     */
    private Boolean lastStatusDockedForShipAnchorUi;

    /** Debounces table rebuilds when Status telemetry streams in ship-distance mode. */
    private Timer shipTelemetryRebuildTimer;

    /** Periodically refreshes ship-distance ordering / displayed Ls as mean anomaly evolves (wall-clock). */
    private Timer orbitEvolutionTimer;

    /** Last ship-centric distances used for table sort; avoids rebuild when rounded Ls are unchanged. */
    private Map<Integer, Double> lastShipCentricDistLsSnapshot = Collections.emptyMap();

    /**
     * When selected, advances a synthetic {@link Instant} for the plan map so animated orbits move at a visible rate
     * (journal elements; not real-time flight).
     */
    private Timer orbitAnimDemoTimer;
    private volatile boolean orbitAnimDemoActive;
    /** Frozen frozen map epoch while orbit playback is paused (also used before first Play). */
    private Instant orbitAnimFreezeEpoch;
    /** T+0 epoch for the current play session (set on first Play; cleared on Stop). */
    private Instant orbitAnimPlayBaseEpoch;
    private Instant orbitAnimSimInstant;
    private JToggleButton orbitAnimPlayButton;
    private JButton orbitAnimStopButton;
    /** Avoid pause handler when Stop programmatically deselects Play. */
    private boolean orbitAnimSuppressPlayToggleHandler;
    private JSlider mapViewTiltSlider;
    private JLabel mapViewTiltLabel;
    private JLabel mapViewTiltValueLabel;
    private JButton orbitAnimSpeedDownButton;
    private JButton orbitAnimSpeedUpButton;
    private JLabel orbitAnimSpeedValueLabel;
    /** Simulated time advance: orbit-model days per one wall-clock second (map toolbar << / >>; persisted). */
    private double orbitAnimDaysPerWallSecond = OverlayPreferences.getSystemTabOrbitAnimDaysPerWallSecond();
    /** Body IDs whose exobiology detail rows are hidden (body + ring lines remain). */
    private final Set<Integer> bioDetailsCollapsedBodyIds = new HashSet<>();
    /**
     * Pass-through hover latch: body ids whose bio details stay peek-open until collapse-all, manual row toggle,
     * or system load — multiple bodies may be latched. Survives tab switches ({@code !table.isShowing()}).
     */
    private final Set<Integer> passthroughHoverExpandBodyIds = new HashSet<>();
    /** Mouse pass-through: pointer over Bio header −/+ (Swing never gets motion); drives header cue repaint. */
    private volatile boolean bioColumnHeaderExpandCueHover;
    /** Polls global pointer position (pass-through does not deliver Swing mouse motion reliably). */
    private final Timer bioExpandCueHoverPollTimer;
    /** One-shot: after dwell on body Bio cue, expand (latch) or collapse per {@link #bioExpandCueDelayedPendingClose}. */
    private final Timer bioExpandCueDelayedOpenTimer;
    /** One-shot: after dwell on Bio column header −/+ (expand/collapse all), same delay as body rows. */
    private final Timer bioHeaderAllDwellTimer;
    /** If true, pending header dwell is collapse all; if false, expand all. */
    private boolean bioHeaderAllDwellPendingCollapse;
    /** After a header dwell commit, block another until pointer leaves the header cue hit region. */
    private boolean bioHeaderAllDwellArmUntilCueExit;
    private Integer bioExpandCueDelayedOpenPendingBodyId;
    /** If true, pending action is collapse (−); if false, expand (+ / latch). */
    private boolean bioExpandCueDelayedPendingClose;
    /**
     * After a dwell commit on a body's Bio cue, block further dwell toggles for that body until the pointer
     * leaves the cue hit region or hovers another body's cue (avoids open/close oscillation while stationary).
     */
    private Integer bioExpandCueDwellArmBodyUntilCueExit;
    private static final int BIO_EXPAND_HOVER_OPEN_DELAY_MS = 400;
    /** Pass-through hover-to-activate for map toolbar icon buttons (matches tab bar). */
    private static final int MAP_TOOLBAR_HOVER_CLICK_DELAY_MS = 500;
    /** After a system load, seed "all bio sections collapsed" once bodies exist. */
    private boolean bioCollapsedDefaultsSeededForCurrentSystem;
    /** Body id expanded only because it was targeted (eligible for auto-collapse on untarget). */
    private Integer bioAutoExpandedForTargetBodyId;
    private int bioExpandCuePx = 12;
    /** Horizontal space between the −/+ cue and the leaf (Eclipse-style tree gap). */
    private int bioExpandToLeafGapPx = 5;
    private static final int EXPAND_HIT_SLOP_PX = 2;

	private JLabel headerSummaryLabel;
	private JLabel systemModelStatusLabel;

	/** Optional callback when system tab target/near/destination state changes (for debounced session persist). */
	private Runnable sessionStateChangeCallback;

	public void setSessionStateChangeCallback(Runnable callback) {
	    this.sessionStateChangeCallback = callback;
	}

	/**
	 * Rebuild bodies table and plan map after preferences change (ship reference mode, etc.).
	 */
	public void refreshFromSavedOverlayPreferences() {
	    requestRebuild();
	}

	private void fireSessionStateChanged() {
	    if (sessionStateChangeCallback != null) {
	        sessionStateChangeCallback.run();
	    }
	}

	/** Fill system-tab-related fields of the given session state (for save). */
	public void fillSessionState(EdoSessionState state) {
	    if (state == null) return;
	    state.setDocked(Boolean.valueOf(this.state.isDocked()));
	    state.setTargetBodyId(targetBodyId);
	    state.setTargetBodyName(targetBodyName);
	    state.setNearBodyId(nearBodyId);
	    state.setNearBodyName(nearBodyName);
	    state.setTargetDestinationParentBodyId(targetDestinationParentBodyId);
	    state.setTargetDestinationName(targetDestinationName);
	    Integer ramParkedBody = this.state.getCarrierParkedBodyId();
	    long ramParkedSys = this.state.getCarrierParkedSystemAddress();
	    if (ramParkedBody != null && ramParkedBody.intValue() > 0) {
	        state.setCarrierParkedBodyId(ramParkedBody);
	        state.setCarrierParkedSystemAddress(ramParkedSys != 0L ? Long.valueOf(ramParkedSys) : null);
	    }
	    // else: keep carrierParked* already on `state` from EdoSessionPersistence.load() — do not wipe with empty SystemState.
	    state.setSystemTabTableSortMode(OverlayPreferences.getSystemTabTableSortMode().toPrefsString());
	}

	/**
	 * Mouse pass-through: apply global wheel to the orbital map (zoom) when the pointer is over the map, else to the
	 * bodies table when the pointer is over the scroll area and the vertical bar is visible.
	 */
	public boolean applyPassThroughWheelIfHit(int screenX, int screenY, int wheelRotation) {
		if (applyPassThroughMapViewTiltWheelIfHit(screenX, screenY, wheelRotation)) {
			return true;
		}
		if (systemPlanMapPanel.applyPassThroughWheelIfHit(screenX, screenY, wheelRotation)) {
			return true;
		}
		return PassThroughScrollSupport.applyVerticalWheelIfHit(systemBodyScrollPane, screenX, screenY, wheelRotation);
	}

	/** Apply persisted system tab state (for restore on startup). */
	public void applySessionState(EdoSessionState state) {
	    if (state == null) return;
	    if (state.getDocked() != null) {
	        this.state.setDocked(state.getDocked().booleanValue());
	    }
	    if (state.getTargetBodyId() != null) {
	        targetBodyId = state.getTargetBodyId();
	        targetBodyName = state.getTargetBodyName();
	        long addr = this.state.getSystemAddress();
	        if (addr != 0L) {
	            stickyHudTargetBodyId = targetBodyId;
	            OverlayPreferences.setSystemTabStickyHudTargetBodyId(addr, targetBodyId);
	        }
	    }
	    if (state.getNearBodyId() != null || state.getNearBodyName() != null) {
	        nearBodyId = state.getNearBodyId();
	        nearBodyName = state.getNearBodyName();
	    }
	    if (state.getTargetDestinationParentBodyId() != null || state.getTargetDestinationName() != null) {
	        targetDestinationParentBodyId = state.getTargetDestinationParentBodyId();
	        targetDestinationName = state.getTargetDestinationName();
	    }
	    Integer pb = state.getCarrierParkedBodyId();
	    Long ps = state.getCarrierParkedSystemAddress();
	    this.state.setCarrierParkedBodyId(pb);
	    if (pb == null) {
	        this.state.setCarrierParkedSystemAddress(0L);
	    } else {
	        this.state.setCarrierParkedSystemAddress(ps != null ? ps.longValue() : 0L);
	    }
	    String sortMode = state.getSystemTabTableSortMode();
	    if (sortMode != null && !sortMode.isBlank()) {
	        OverlayPreferences.setSystemTabTableSortMode(SystemTabTableSortMode.fromPrefsString(sortMode));
	        updateDistModeToggleAppearance();
	    }
	    // Session restore runs after refreshFromCache() already seeded "all bio collapsed" with no target yet.
	    // reconcileAutoExpandBioForCurrentTargetBody() only runs on that seed pass; run it here too so a
	    // persisted target + pref reliably expands on cold start.
	    reconcileAutoExpandBioForCurrentTargetBody();
	    requestRebuild();
	}
    
	public void setNearBodyChangedListener(Consumer<BodyInfo> listener) {
	    this.nearBodyChangedListener = listener;
	}
	
    public SystemTabPanel() {
        super(new BorderLayout());
        setOpaque(false);
//        setBackground(Color.BLACK);
        // Header label
        headerLabel = new JTextField("Waiting for system data…");
        headerLabel.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (e.isTemporary()) {
                    return;
                }
                syncHeaderLabelFromState();
            }
        });
        headerLabel.addKeyListener(new KeyListener() {
            public void keyTyped(KeyEvent e) {}
            public void keyReleased(KeyEvent e) {}
            
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    String text = headerLabel.getText();
                    if (text == null) {
                        return;
                    }
                    String trimmed = text.trim();
                    if (trimmed.isEmpty()) {
                        reloadCommanderSystem();
                        syncHeaderLabelFromState();
                        return;
                    }

                    System.out.println("User hit enter for system: '" + trimmed + "'");

                    // User is specifying by name; let loadSystem resolve address
                    state.setSystemName(trimmed);
                    state.setSystemAddress(0L);

                    loadSystem(trimmed, 0L, true);
                    syncHeaderLabelFromState();
                }
            }
        });
        headerLabel.setForeground(EdoUi.User.MAIN_TEXT);
//        headerLabel.setBorder(new EmptyBorder(4, 8, 4, 8));
        headerLabel.setOpaque(false);
        headerLabel.setBorder(null);
        headerLabel.setFont(uiFont.deriveFont(Font.BOLD));

        headerSummaryLabel = new JLabel();
        headerSummaryLabel.setForeground(EdoUi.User.MAIN_TEXT);
        headerSummaryLabel.setFont(uiFont.deriveFont(Font.BOLD));
//        headerSummaryLabel.setBorder(new EmptyBorder(4, 8, 4, 8));
        headerSummaryLabel.setOpaque(false);

        systemModelStatusLabel = new JLabel(" ");
        systemModelStatusLabel.setForeground(EdoUi.User.MAIN_TEXT);
        systemModelStatusLabel.setFont(uiFont.deriveFont(Font.PLAIN, uiFont.getSize2D() - 1f));
        systemModelStatusLabel.setOpaque(false);
        
        // Table setup
        tableModel = new SystemBodiesTableModel();
        table = new SystemBodiesTable(tableModel);
        table.setOpaque(false);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        
        table.setBorder(null);//new EmptyBorder(0,0,0,0));
        table.setShowHorizontalLines(false);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setGridColor(EdoUi.Internal.TRANSPARENT);
        table.setBackground(EdoUi.Internal.TRANSPARENT);
        
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Leave more vertical room for the orbital plan map (BorderLayout.SOUTH) on typical overlay heights.
        table.setPreferredScrollableViewportSize(new Dimension(500, 240));
        // NEW: apply ED font to table cells
        table.setFont(uiFont);
        refreshBioIcons();
        table.setRowHeight(computeRowHeight(table, uiFont, 8));

        table.setFocusable(false);
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(false);
        table.setCellSelectionEnabled(false);
        
        table.setTableHeader(new org.dce.ed.ui.TransparentTableHeader(table.getColumnModel()));
        JTableHeader header = table.getTableHeader();
        header.setUI(org.dce.ed.ui.TransparentTableHeaderUI.createUI(header));
        header.setOpaque(false);
        header.setForeground(EdoUi.User.MAIN_TEXT);
        header.setBackground(EdoUi.User.BACKGROUND);
        header.setFont(uiFont.deriveFont(Font.BOLD));
        header.setBorder(null);
        
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table,
                                                           Object value,
                                                           boolean isSelected,
                                                           boolean hasFocus,
                                                           int row,
                                                           int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(
                        table, value, false, false, row, column);
                boolean transparent = OverlayPreferences.overlayChromeRequestsTransparency();
                label.setOpaque(!transparent);
                label.setBackground(transparent ? EdoUi.Internal.TRANSPARENT : EdoUi.User.BACKGROUND);
                label.setForeground(EdoUi.User.MAIN_TEXT);
                label.setFont(uiFont.deriveFont(Font.BOLD));
                label.setHorizontalAlignment(LEFT);
//                label.setBorder(new EmptyBorder(0, 4, 0, 4));

                return label;
            }
        });
        systemBodiesTextCellRenderer = new DefaultTableCellRenderer() {
            {
                setOpaque(false);
                setForeground(EdoUi.User.MAIN_TEXT);
            }

            @Override
            public Component getTableCellRendererComponent(JTable table,
                                                           Object value,
                                                           boolean isSelected,
                                                           boolean hasFocus,
                                                           int row,
                                                           int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                applySystemBodiesTextCellStyle(c, tableModel.getRowAt(row), isSelected);
                return c;
            }
        };

        table.setDefaultRenderer(Object.class, systemBodiesTextCellRenderer);

        DefaultTableCellRenderer atmoCellRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl,
                                                           Object value,
                                                           boolean isSelected,
                                                           boolean hasFocus,
                                                           int row,
                                                           int column) {
                String full = value != null ? String.valueOf(value) : "";
                Component c = systemBodiesTextCellRenderer.getTableCellRendererComponent(
                        tbl, full, isSelected, hasFocus, row, column);
                if (c instanceof JLabel label) {
                    int colW = tbl.getColumnModel().getColumn(column).getWidth();
                    FontMetrics fm = label.getFontMetrics(label.getFont());
                    label.setText(ellipsizeTextToPixelWidth(full, fm, Math.max(0, colW - 8)));
                }
                return c;
            }
        };
        table.getColumnModel().getColumn(1).setCellRenderer(atmoCellRenderer);


        DefaultTableCellRenderer valueRightRenderer = new DefaultTableCellRenderer() {
            {
                setOpaque(false);
                setForeground(EdoUi.User.MAIN_TEXT);
            }

            @Override
            public Component getTableCellRendererComponent(JTable table,
                                                           Object value,
                                                           boolean isSelected,
                                                           boolean hasFocus,
                                                           int row,
                                                           int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table,
                                                                  value,
                                                                  isSelected,
                                                                  hasFocus,
                                                                  row,
                                                                  column);

                setHorizontalAlignment(SwingConstants.RIGHT);

                Row r = tableModel.getRowAt(row);
                boolean isBioRow = r != null && r.detail && !r.destinationRow && !r.isRingDetail()
                        && (r.bioText != null || r.bioValue != null);
                boolean valuableBody = r != null && !r.detail && r.body != null && r.body.isHighValue();

                if (valuableBody) {
                    label.setIcon(valuableEarthBodyIcon);
                    label.setHorizontalTextPosition(SwingConstants.LEFT);
                    label.setIconTextGap(4);
                } else {
                    label.setIcon(null);
                    label.setHorizontalTextPosition(SwingConstants.TRAILING);
                    label.setIconTextGap(4);
                }

                if (isSelected) {
                    label.setForeground(Color.BLACK);
                } else if (r != null && r.detail && r.isRingDetail()) {
                    label.setForeground(EdoUi.Internal.GRAY_180);
                } else if (isBioRow) {
                    label.setForeground(EdoUi.Internal.GRAY_180);
                } else {
                    label.setForeground(EdoUi.User.MAIN_TEXT);
                }

                label.setOpaque(false);
                label.setBackground(EdoUi.Internal.TRANSPARENT);
                return label;
            }
        };
        DefaultTableCellRenderer landRenderer = new DefaultTableCellRenderer() {
            {
                setOpaque(false);
                setHorizontalAlignment(SwingConstants.CENTER);
                setForeground(EdoUi.User.MAIN_TEXT);
            }
            @Override
            public Component getTableCellRendererComponent(JTable table,
                                                           Object value,
                                                           boolean isSelected,
                                                           boolean hasFocus,
                                                           int row,
                                                           int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(table,
                                                                        "",
                                                                        isSelected,
                                                                        hasFocus,
                                                                        row,
                                                                        column);
                Row r = tableModel.getRowAt(row);
                if (isSelected) {
                    c.setForeground(Color.BLACK);
                } else if (r != null && r.detail && r.isRingDetail()) {
                    c.setForeground(EdoUi.Internal.GRAY_180);
                } else {
                    c.setForeground(EdoUi.User.MAIN_TEXT);
                }
                if (c instanceof JComponent) {
                    ((JComponent) c).setOpaque(false);
                }
                c.setBackground(EdoUi.Internal.TRANSPARENT);
                boolean showSneaker = false;
                if (r != null && !r.detail && r.body != null) {
                    showSneaker = r.body.isLandable();
                }
                c.setIcon(showSneaker ? landSneakerIcon : null);
                c.setText("");
                c.setHorizontalTextPosition(SwingConstants.RIGHT);
                c.setIconTextGap(0);
                return c;
            }
        };

        // Column index 3 is "Value"
        table.getColumnModel().getColumn(2).setCellRenderer(new BioCellRenderer());
        table.getColumnModel().getColumn(2).setHeaderRenderer(createBioColumnHeaderRenderer());

        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                JTableHeader h = table.getTableHeader();
                int col = h.columnAtPoint(e.getPoint());
                if (col != 2) {
                    return;
                }
                Set<Integer> ex = collectExpandableBioBodyIds();
                if (ex.isEmpty()) {
                    return;
                }
                Rectangle hr = h.getHeaderRect(col);
                int relX = e.getX() - hr.x;
                int hitW = bioColumnBioLeadingSlotWidthPx() + EXPAND_HIT_SLOP_PX * 2;
                if (relX < 0 || relX > hitW) {
                    return;
                }
                toggleCollapseAllExpandableBioDetails();
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (OverlayPreferences.isOverlayMousePassThroughToGame()) {
                    return;
                }
                tryToggleBioExpandAt(e.getPoint());
            }
        });

        table.getColumnModel().getColumn(3).setCellRenderer(valueRightRenderer);
        table.getColumnModel().getColumn(4).setCellRenderer(landRenderer);

        systemBodyScrollPane = new JScrollPane(table);
        systemBodyScrollPane.setBorder(null);
        systemBodyScrollPane.setOpaque(false);
        systemBodyScrollPane.getViewport().setOpaque(false);
        systemBodyScrollPane.getViewport().setBackground(EdoUi.Internal.TRANSPARENT);
        // Prevent LAF default white corner/scrollbar paints in transparent overlay mode.
        javax.swing.JPanel upperRightCorner = new javax.swing.JPanel();
        upperRightCorner.setOpaque(false);
        upperRightCorner.setBackground(EdoUi.Internal.TRANSPARENT);
        javax.swing.JPanel lowerRightCorner = new javax.swing.JPanel();
        lowerRightCorner.setOpaque(false);
        lowerRightCorner.setBackground(EdoUi.Internal.TRANSPARENT);
        systemBodyScrollPane.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, upperRightCorner);
        systemBodyScrollPane.setCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER, lowerRightCorner);
        if (systemBodyScrollPane.getVerticalScrollBar() != null) {
            javax.swing.JScrollBar vsb = systemBodyScrollPane.getVerticalScrollBar();
            vsb.setOpaque(false);
            vsb.setBackground(EdoUi.Internal.TRANSPARENT);
            vsb.setUI(new SubtleScrollBarUI());
            // Slightly wider hit area while keeping a subtle visual thumb.
            vsb.setPreferredSize(new Dimension(12, Integer.MAX_VALUE));
        }
        
        JViewport headerViewport = systemBodyScrollPane.getColumnHeader();
        if (headerViewport != null) {
            headerViewport.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
            headerViewport.setOpaque(false);
            headerViewport.setBackground(EdoUi.Internal.TRANSPARENT);
            headerViewport.setUI(org.dce.ed.ui.TransparentViewportUI.createUI(headerViewport));
        }

        systemBodyScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        systemBodyScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        setBorder(new EmptyBorder(4, 4, 4, 4));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JPanel headerNorth = new JPanel(new BorderLayout());
        headerNorth.setOpaque(false);
        headerNorth.add(headerLabel, BorderLayout.WEST);
        headerNorth.add(headerSummaryLabel, BorderLayout.CENTER);
        headerPanel.add(headerNorth, BorderLayout.NORTH);
        headerPanel.add(systemModelStatusLabel, BorderLayout.SOUTH);

        JPanel distToggleEast = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        distToggleEast.setOpaque(false);
        distFromShipButton = new JButton();
        distFromStarButton = new JButton();
        distByValueButton = new JButton();
        configureDistModeToggleButton(distFromShipButton,
                "Sort by approximate distance from your ship (orbital model + Status near-body / surface fix)");
        configureDistModeToggleButton(distFromStarButton,
                "Sort by distance from system entry / star (journal DistanceFromArrivalLS)");
        configureDistModeToggleButton(distByValueButton,
                "Sort by exploration value (exobiology, geological signals, high-value worlds)");
        distFromShipButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                distFromShipHovered = true;
                updateDistModeToggleAppearance();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                distFromShipHovered = false;
                updateDistModeToggleAppearance();
            }
        });
        distFromStarButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                distFromStarHovered = true;
                updateDistModeToggleAppearance();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                distFromStarHovered = false;
                updateDistModeToggleAppearance();
            }
        });
        distByValueButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                distByValueHovered = true;
                updateDistModeToggleAppearance();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                distByValueHovered = false;
                updateDistModeToggleAppearance();
            }
        });
        distFromShipButton.addActionListener(e -> selectSystemTabTableSortMode(SystemTabTableSortMode.FROM_SHIP));
        distFromStarButton.addActionListener(e -> selectSystemTabTableSortMode(SystemTabTableSortMode.FROM_STAR));
        distByValueButton.addActionListener(e -> selectSystemTabTableSortMode(SystemTabTableSortMode.BY_VALUE));
        distToggleEast.add(distFromShipButton);
        distToggleEast.add(distFromStarButton);
        distToggleEast.add(distByValueButton);
        headerPanel.add(distToggleEast, BorderLayout.EAST);
        headerPanel.setBorder(null);

        BooleanSupplier tableSortPassThrough = OverlayPreferences::isOverlayMousePassThroughToGame;
        HoverClickPoller.register(distFromShipButton, MAP_TOOLBAR_HOVER_CLICK_DELAY_MS,
                () -> applySystemTabTableSortMode(SystemTabTableSortMode.FROM_SHIP), tableSortPassThrough);
        HoverClickPoller.register(distFromStarButton, MAP_TOOLBAR_HOVER_CLICK_DELAY_MS,
                () -> applySystemTabTableSortMode(SystemTabTableSortMode.FROM_STAR), tableSortPassThrough);
        HoverClickPoller.register(distByValueButton, MAP_TOOLBAR_HOVER_CLICK_DELAY_MS,
                () -> applySystemTabTableSortMode(SystemTabTableSortMode.BY_VALUE), tableSortPassThrough);

        applyDistanceToggleIcons();
        updateDistModeToggleAppearance();

        add(headerPanel, BorderLayout.NORTH);

        JPanel tablePane = new JPanel(new BorderLayout());
        tablePane.setOpaque(false);
        tablePane.add(systemBodyScrollPane, BorderLayout.CENTER);

        JPanel mapColumn = new JPanel(new BorderLayout());
        mapColumn.setOpaque(false);
        JPanel mapToolbarEast = new JPanel(new FlowLayout(FlowLayout.TRAILING, 4, 2));
        mapToolbarEast.setOpaque(false);
        systemPlanMapCollapseButton = new JButton();
        systemPlanMapCollapseButton.setToolTipText("Collapse the plan map to the bottom (toolbar stays visible).");
        systemPlanMapCollapseButton.addActionListener(e -> collapseSystemPlanMap());
        systemPlanMapExpandButton = new JButton();
        systemPlanMapExpandButton.setToolTipText("Restore the plan map to its previous height.");
        systemPlanMapExpandButton.addActionListener(e -> expandSystemPlanMap());
        BooleanSupplier mapToolbarPassThrough = OverlayPreferences::isOverlayMousePassThroughToGame;
        HoverClickPoller.register(systemPlanMapCollapseButton, MAP_TOOLBAR_HOVER_CLICK_DELAY_MS,
                this::collapseSystemPlanMap, mapToolbarPassThrough);
        HoverClickPoller.register(systemPlanMapExpandButton, MAP_TOOLBAR_HOVER_CLICK_DELAY_MS,
                this::expandSystemPlanMap, mapToolbarPassThrough);
        installMapToolbarButtonHoverListeners(systemPlanMapCollapseButton, () -> systemPlanMapCollapseHovered,
                v -> systemPlanMapCollapseHovered = v);
        installMapToolbarButtonHoverListeners(systemPlanMapExpandButton, () -> systemPlanMapExpandHovered,
                v -> systemPlanMapExpandHovered = v);
        mapToolbarEast.add(systemPlanMapCollapseButton);
        mapToolbarEast.add(systemPlanMapExpandButton);
        JPanel mapToolbar = new JPanel(new BorderLayout());
        mapToolbar.setOpaque(true);
        mapToolbar.setBackground(EdoUi.User.PANEL_BG);
        JPanel mapToolbarMain = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 2));
        mapToolbarMain.setOpaque(false);
        systemPlanMapToolbar = mapToolbar;
        int savedViewTilt = OverlayPreferences.getSystemPlanMapViewTiltDegrees();
        systemPlanMapPanel.setViewTiltDegrees(savedViewTilt, false);
        mapViewTiltLabel = new JLabel("View:");
        mapViewTiltLabel.setForeground(EdoUi.User.MAIN_TEXT);
        mapViewTiltSlider = new JSlider(0, 90, savedViewTilt);
        mapViewTiltSlider.setOpaque(false);
        mapViewTiltSlider.setPreferredSize(new Dimension(110, 22));
        mapViewTiltSlider.setMajorTickSpacing(45);
        mapViewTiltSlider.setPaintTicks(true);
        mapViewTiltSlider.setToolTipText(
                "Tilt the 3D view from top-down (0°) toward edge-on (90°). "
                        + "In mouse pass-through mode, move the pointer along the slider to scrub tilt; "
                        + "use the wheel over this control for fine steps.");
        mapViewTiltValueLabel = new JLabel(savedViewTilt + "°");
        mapViewTiltValueLabel.setForeground(EdoUi.User.MAIN_TEXT);
        mapViewTiltSlider.addChangeListener(e -> onMapViewTiltSliderChanged());
        mapViewTiltCluster = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        mapViewTiltCluster.setOpaque(false);
        mapViewTiltCluster.add(mapViewTiltLabel);
        mapViewTiltCluster.add(mapViewTiltSlider);
        mapViewTiltCluster.add(mapViewTiltValueLabel);
        mapViewTiltCluster.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (OverlayPreferences.isOverlayMousePassThroughToGame()) {
                    return;
                }
                mapViewTiltHovered = true;
                updateMapViewTiltHoverAppearance();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (OverlayPreferences.isOverlayMousePassThroughToGame()) {
                    return;
                }
                mapViewTiltHovered = false;
                updateMapViewTiltHoverAppearance();
            }
        });
        mapToolbarMain.add(mapViewTiltCluster);
        updateMapToolbarHoverAppearance();
        JButton hierarchyGraphButton = new JButton("Graph");
        hierarchyGraphButton.setForeground(EdoUi.User.MAIN_TEXT);
        hierarchyGraphButton.setOpaque(false);
        hierarchyGraphButton.setContentAreaFilled(false);
        hierarchyGraphButton.setBorderPainted(true);
        hierarchyGraphButton.setFocusable(false);
        hierarchyGraphButton.setFocusPainted(false);
        hierarchyGraphButton.setToolTipText(
                "Open orbital hierarchy graph for this system (parent links, collapse groups)");
        hierarchyGraphButton.addActionListener(e -> {
            String name = state.getSystemName();
            if (name == null || name.isBlank()) {
                return;
            }
            OverlayToolsLaunchers.launchSystemHierarchyGraphForSystem(SystemTabPanel.this, name);
        });
        mapToolbarMain.add(hierarchyGraphButton);
        orbitAnimPlayButton = new JToggleButton();
        orbitAnimPlayButton.setText(null);
        orbitAnimPlayButton.setForeground(EdoUi.User.MAIN_TEXT);
        orbitAnimPlayButton.setOpaque(false);
        orbitAnimPlayButton.setContentAreaFilled(false);
        orbitAnimPlayButton.setBorderPainted(true);
        orbitAnimPlayButton.setFocusable(false);
        orbitAnimPlayButton.setFocusPainted(false);
        orbitAnimPlayButton.setToolTipText(
                "Fast-forward true-scale orbits (approximate journal elements; not real flight time). "
                        + "Use the chevron buttons to change how fast model time runs.");
        orbitAnimPlayButton.addItemListener(ev -> {
            if (orbitAnimSuppressPlayToggleHandler) {
                return;
            }
            if (ev.getStateChange() == ItemEvent.SELECTED) {
                orbitAnimDemoActive = true;
                if (orbitAnimFreezeEpoch == null) {
                    orbitAnimFreezeEpoch = Instant.now();
                }
                if (orbitAnimPlayBaseEpoch == null) {
                    orbitAnimPlayBaseEpoch = orbitAnimFreezeEpoch;
                }
                if (orbitAnimSimInstant == null) {
                    orbitAnimSimInstant = orbitAnimFreezeEpoch;
                }
                orbitAnimDemoTimer.start();
                refreshPlanMap();
            } else if (ev.getStateChange() == ItemEvent.DESELECTED) {
                pauseOrbitAnimPlayback();
            }
        });
        mapToolbarMain.add(orbitAnimPlayButton);
        orbitAnimStopButton = new JButton();
        orbitAnimStopButton.setText(null);
        orbitAnimStopButton.setForeground(EdoUi.User.MAIN_TEXT);
        orbitAnimStopButton.setOpaque(false);
        orbitAnimStopButton.setContentAreaFilled(false);
        orbitAnimStopButton.setBorderPainted(true);
        orbitAnimStopButton.setFocusable(false);
        orbitAnimStopButton.setFocusPainted(false);
        orbitAnimStopButton.setToolTipText(
                "Stop orbit simulation and return bodies to real-time journal positions (now).");
        orbitAnimStopButton.addActionListener(e -> stopOrbitAnimSimulation());
        mapToolbarMain.add(orbitAnimStopButton);
        String orbitSpeedTt = "Orbit model days advanced per second of real time while playing.";
        orbitAnimSpeedDownButton = new JButton();
        orbitAnimSpeedDownButton.setText(null);
        orbitAnimSpeedDownButton.addActionListener(e ->
                setOrbitAnimSpeedValue((int) Math.round(orbitAnimDaysPerWallSecond) - ORBIT_ANIM_SPEED_STEP));

        orbitAnimSpeedValueLabel = new JLabel(formatOrbitAnimSpeedLabel(orbitAnimDaysPerWallSecond));
        orbitAnimSpeedValueLabel.setHorizontalAlignment(SwingConstants.CENTER);
        orbitAnimSpeedValueLabel.setForeground(EdoUi.User.MAIN_TEXT);
        orbitAnimSpeedValueLabel.setToolTipText(orbitSpeedTt);

        orbitAnimSpeedUpButton = new JButton();
        orbitAnimSpeedUpButton.setText(null);
        orbitAnimSpeedUpButton.addActionListener(e ->
                setOrbitAnimSpeedValue((int) Math.round(orbitAnimDaysPerWallSecond) + ORBIT_ANIM_SPEED_STEP));

        applyOrbitMapToolbarTypography("Slower: fewer model days per second of real time.",
                "Faster: more model days per second of real time.",
                orbitSpeedTt);
        mapToolbarMain.add(orbitAnimSpeedDownButton);
        mapToolbarMain.add(orbitAnimSpeedValueLabel);
        mapToolbarMain.add(orbitAnimSpeedUpButton);
        mapToolbar.add(mapToolbarMain, BorderLayout.WEST);
        mapToolbar.add(mapToolbarEast, BorderLayout.EAST);
        mapColumn.add(mapToolbar, BorderLayout.NORTH);
        mapColumn.add(systemPlanMapPanel, BorderLayout.CENTER);

        double tableSplitRatio = OverlayPreferences.getSystemTabPanelTableSplitRatio();
        systemTableMapSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tablePane, mapColumn);
        EdoMiningSplitPaneUi.install(systemTableMapSplit);
        configureSystemTableMapSplit(systemTableMapSplit, tableSplitRatio);
        systemTableMapSplit.addPropertyChangeListener(evt -> {
            if (!JSplitPane.DIVIDER_LOCATION_PROPERTY.equals(evt.getPropertyName())) {
                return;
            }
            onSystemTableMapDividerMoved();
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (systemTableMapSplit == null || systemTableMapSplit.getHeight() < 32) {
                    return;
                }
                if (systemPlanMapCollapsed) {
                    applySystemPlanMapCollapsedDivider();
                } else {
                    double ratio = OverlayPreferences.getSystemTabPanelTableSplitRatio();
                    systemTableMapSplit.setResizeWeight(ratio);
                    systemTableMapSplit.setDividerLocation(ratio);
                }
                EdoMiningSplitPaneUi.applyDividerTheme(systemTableMapSplit);
            }
        });
        updateSystemPlanMapCollapseButtons();
        add(systemTableMapSplit, BorderLayout.CENTER);

        refreshFromCache();

        configureSystemBodiesTableColumnResize();
        SwingUtilities.invokeLater(this::applySystemBodiesTableColumnLayout);

        bioExpandCueHoverPollTimer = new Timer(40, e -> pollBioExpandCueHoverFromGlobalMouse());
        bioExpandCueHoverPollTimer.setRepeats(true);

        bioExpandCueDelayedOpenTimer = new Timer(BIO_EXPAND_HOVER_OPEN_DELAY_MS, e -> commitBioExpandCueDelayedAction());
        bioExpandCueDelayedOpenTimer.setRepeats(false);
        bioHeaderAllDwellTimer = new Timer(BIO_EXPAND_HOVER_OPEN_DELAY_MS, e -> commitBioHeaderAllDwellAction());
        bioHeaderAllDwellTimer.setRepeats(false);

        shipTelemetryRebuildTimer = new Timer(SHIP_TELEMETRY_REBUILD_DEBOUNCE_MS, e -> {
            if (!OverlayPreferences.isSystemTabDistanceFromShip() || orbitAnimDemoActive) {
                return;
            }
            Map<Integer, Double> next = computeShipCentricDistancesLs();
            if (!shipCentricDistancesMeaningfullyChanged(lastShipCentricDistLsSnapshot, next)) {
                return;
            }
            requestRebuild();
        });
        shipTelemetryRebuildTimer.setRepeats(false);

        orbitEvolutionTimer = new Timer(ORBIT_EVOLUTION_REBUILD_INTERVAL_MS, e -> {
            if (!OverlayPreferences.isSystemTabDistanceFromShip() || orbitAnimDemoActive) {
                return;
            }
            Map<Integer, Double> next = computeShipCentricDistancesLs();
            if (!shipCentricDistancesMeaningfullyChanged(lastShipCentricDistLsSnapshot, next)) {
                return;
            }
            requestRebuild();
        });
        orbitEvolutionTimer.setRepeats(true);
        refreshOrbitEvolutionTimerRunning();

        orbitAnimDemoTimer = new Timer(ORBIT_ANIM_TIMER_MS, e -> tickOrbitAnimDemo());
        orbitAnimDemoTimer.setRepeats(true);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (bioExpandCueHoverPollTimer != null) {
            bioExpandCueHoverPollTimer.start();
        }
        refreshOrbitEvolutionTimerRunning();
    }

    @Override
    public void removeNotify() {
        if (bioExpandCueHoverPollTimer != null) {
            bioExpandCueHoverPollTimer.stop();
        }
        if (bioExpandCueDelayedOpenTimer != null) {
            bioExpandCueDelayedOpenTimer.stop();
        }
        if (bioHeaderAllDwellTimer != null) {
            bioHeaderAllDwellTimer.stop();
        }
        if (orbitEvolutionTimer != null) {
            orbitEvolutionTimer.stop();
        }
        if (orbitAnimDemoTimer != null) {
            orbitAnimDemoTimer.stop();
        }
        orbitAnimDemoActive = false;
        super.removeNotify();
    }

    // ---------------------------------------------------------------------
    // Bio payout range (FSS signal count × predicted species): min sum vs max sum of k payouts.
    // Used for System tab body-row Bio column and INITIAL bio TTS.
    // ---------------------------------------------------------------------

    /**
     * {@code payoutRange[2]} should be a small FSS signal count; if it is ever corrupted or widened to a huge
     * credit-like value, casting to {@code int} can yield {@code -1294967296} and poison TTS manifests.
     */
    private static int safeBioSignalCountForSpeech(long raw) {
        if (raw < 0L || raw > 100_000L) {
            return 0;
        }
        return (int) raw;
    }

    /**
     * @return {@code [minCredits, maxCredits, signalCountUsed]} or {@code null}
     */
    private static long[] bioPayoutRangeForCandidates(List<BioCandidate> candidates,
            boolean firstBonusApplies,
            Integer fssBioSignalCount) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        Map<String, List<Long>> byGenus = new LinkedHashMap<>();
        for (BioCandidate bio : candidates) {
            if (bio == null) {
                continue;
            }
            String genus = firstWord(canonicalBioName(bio.getDisplayName())).toLowerCase(Locale.ROOT);
            long p = bio.getEstimatedPayout(firstBonusApplies);
            byGenus.computeIfAbsent(genus, k -> new ArrayList<>()).add(Long.valueOf(p));
        }
        List<Long> mins = new ArrayList<>(byGenus.size());
        List<Long> maxs = new ArrayList<>(byGenus.size());
        for (List<Long> payouts : byGenus.values()) {
            long minV = Long.MAX_VALUE;
            long maxV = Long.MIN_VALUE;
            for (Long c : payouts) {
                if (c == null) {
                    continue;
                }
                long v = c.longValue();
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
            }
            if (minV != Long.MAX_VALUE) {
                mins.add(Long.valueOf(minV));
                maxs.add(Long.valueOf(maxV));
            }
        }
        if (mins.isEmpty()) {
            return null;
        }
        return BioTableBuilder.bioPayoutRangeFromMinMaxLists(mins, maxs, fssBioSignalCount);
    }

    /** Drops species the player has fully analysed (3/3) so TTS matches the remaining pool. */
    private static List<BioCandidate> filterBioCandidatesExcludingFullySampled(
            BodyInfo body, List<BioCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        if (body == null) {
            return new ArrayList<>(candidates);
        }
        List<BioCandidate> out = new ArrayList<>(candidates.size());
        for (BioCandidate c : candidates) {
            if (c == null) {
                continue;
            }
            String name = canonicalBioName(c.getDisplayName());
            if (body.getBioSampleCount(name) >= 3) {
                continue;
            }
            out.add(c);
        }
        return out;
    }

    /** Fallback when {@link Row#getBioHeaderSummary()} is blank (e.g. stale model). */
    private static String formatBioHeaderValueOrRange(BodyInfo b) {
        return BioTableBuilder.computeBioHeaderSummary(b);
    }

    static boolean isFirstDiscoveredPrimaryStarScan(ScanEvent e) {
        if (e == null || !Boolean.FALSE.equals(e.getWasDiscovered()) || isBlank(e.getStarType())) {
            return false;
        }

        String bodyName = e.getBodyName();
        String starSystem = e.getStarSystem();
        if (!isBlank(bodyName) && !isBlank(starSystem) && bodyName.trim().equalsIgnoreCase(starSystem.trim())) {
            return true;
        }

        if (e.getBodyId() == 0) {
            return true;
        }

        double distanceLs = e.getDistanceFromArrivalLs();
        return Double.isFinite(distanceLs) && Math.abs(distanceLs) <= 1.0;
    }

    // ---------------------------------------------------------------------
    // Event forwarding
    // ---------------------------------------------------------------------

    public void handleLogEvent(EliteLogEvent event) {
        if (event == null) {
            return;
        }

        // 1) Mutate domain state (can be on background thread)
        processor.handleEvent(event);

        if (event instanceof ApproachBodyEvent) {
            handleApproachBodyEvent((ApproachBodyEvent) event);
        } else if (event instanceof LeaveBodyEvent) {
            handleLeaveBodyEvent((LeaveBodyEvent) event);
        } else if (event instanceof SupercruiseExitEvent) {
            handleSupercruiseExitEvent((SupercruiseExitEvent) event);
        } else if (event instanceof FsdJumpEvent || event instanceof LocationEvent
                || event instanceof CarrierJumpEvent) {
            approachReferenceBodyId = null;
            dssDetailedScanReferenceBodyId = null;
            supercruiseDropReferenceBodyId = null;
        }

        if (event instanceof ScanEvent) {
            ScanEvent scan = (ScanEvent) event;
            handleDetailedSurfaceScanProximity(scan);
            handleFirstDiscoveredSystemAnnouncement(scan);
        }

        if (shouldRefreshFleetCarrierProximity(event)) {
            SwingUtilities.invokeLater(this::applyFleetCarrierDockedProximity);
        }

        // StatusEvent is very high frequency; avoid table rebuilds.
        // We only use it to track which body the player is currently near, and which body is currently targeted.
        if (event instanceof StatusEvent) {
            StatusEvent e = (StatusEvent) event;
            updateTargetBodyFromStatus(e);
            updateNearBodyFromStatus(e);
            return;
        }

        // 2) If we jumped, do the heavy load/merge off the EDT,
        //    then refresh UI on the EDT.
        if (event instanceof BioScanPredictionEvent) {
            BioScanPredictionEvent e = (BioScanPredictionEvent) event;

            // Journal full rescan replays FSS/SAA history; do not announce every rediscovered body.
            if (SystemCache.isBulkSystemWrite()) {
                return;
            }

            List<BioCandidate> candidates = e.getCandidates();
            if (candidates != null && !candidates.isEmpty()) {
                BodyInfo bodyForBio = null;
                Integer signals = null;
                try {
                    if (state != null) {
                        bodyForBio = state.getBodies().get(e.getBodyId());
                        if (bodyForBio != null) {
                            signals = bodyForBio.getNumberOfBioSignals();
                        }
                    }
                } catch (Exception ignored) {
                    // best-effort; fall back below
                }

                List<BioCandidate> candidatesForSpeech =
                        filterBioCandidatesExcludingFullySampled(bodyForBio, candidates);
                long[] payoutRange = bioPayoutRangeForCandidates(
                        candidatesForSpeech, e.getBonusApplies(), signals);
                if (payoutRange != null && e.getKind() == PredictionKind.INITIAL) {
                    long minTotal = payoutRange[0];
                    long maxTotal = payoutRange[1];
                    int signalCount = safeBioSignalCountForSpeech(payoutRange[2]);

                    long thresholdCr = OverlayPreferences.getMiningExobiologyValuableBioThresholdCredits();
                    long maxSingleSpecies = 0L;
                    for (BioCandidate c : candidatesForSpeech) {
                        if (c == null) {
                            continue;
                        }
                        long p = c.getEstimatedPayout(e.getBonusApplies());
                        if (p > maxSingleSpecies) {
                            maxSingleSpecies = p;
                        }
                    }
                    if (maxSingleSpecies >= thresholdCr) {
                        TtsSprintf ttsSprintf = new TtsSprintf(new PollyTtsCached());
                        long speakMin = TtsSprintf.roundCreditsForSpeech(minTotal);
                        long speakMax = TtsSprintf.roundCreditsForSpeech(maxTotal);
                        boolean singleValue = speakMin == speakMax;
                        boolean rangeAsWholeMillions = !singleValue
                                && speakMin >= 1_000_000L
                                && speakMax >= 1_000_000L
                                && speakMin % 1_000_000L == 0L
                                && speakMax % 1_000_000L == 0L;

                        if (singleValue) {
                            ttsSprintf.speakf(
                                    "{n} signals on planetary body {body} with guaranteed exobiology value of {credits} credits",
                                    Integer.valueOf(signalCount),
                                    e.getBodyName(),
                                    Long.valueOf(speakMax));
                        } else if (rangeAsWholeMillions) {
                            ttsSprintf.speakf(
                                    "{n} signals on planetary body {body} with estimated exobiology value from {mm} to {mm} million credits",
                                    Integer.valueOf(signalCount),
                                    e.getBodyName(),
                                    Long.valueOf(speakMin / 1_000_000L),
                                    Long.valueOf(speakMax / 1_000_000L));
                        } else {
                            ttsSprintf.speakf(
                                    "{n} signals on planetary body {body} with estimated value between {credits} and {credits} credits",
                                    Integer.valueOf(signalCount),
                                    e.getBodyName(),
                                    Long.valueOf(speakMin),
                                    Long.valueOf(speakMax));
                        }
                    }
                }
            }
        }
        if (event instanceof IFsdJump) {
            IFsdJump e = (IFsdJump) event;
            if (event instanceof CarrierLocationEvent && !state.isCommanderAboardFleetCarrier()) {
                requestRebuild();
                persistIfPossible();
                return;
            }
            if (event instanceof CarrierJumpEvent cj
                    && !cj.isDocked()
                    && !cj.isOnFoot()) {
                requestRebuild();
                persistIfPossible();
                return;
            }
            new Thread(() -> {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    /*
                     * Commander ship FSD: Status.json often still reports the previous system's near-body / HUD mirror
                     * for a short time after {@code FSDJump}, so {@link #resolveCommanderRefBodyId()} could pin "You" on
                     * the wrong id in the new system. Clear proximity and this destination's per-system sticky so we
                     * default to the primary until telemetry catches up. Game startup restores via
                     * {@link #applySessionState(EdoSessionState)} / {@link #refreshFromCache()} without this branch;
                     * {@link CarrierJumpEvent} (also {@link IFsdJump}) keeps near-body + sticky so a carrier can sit
                     * on a non-primary star.
                     */
                    if (e instanceof FsdJumpEvent) {
                        nearBodyId = null;
                        nearBodyName = null;
                        lastVisitedNonStarBodyId = null;
                        stickyHudTargetBodyId = null;
                        long addr = e.getSystemAddress();
                        if (addr != 0L) {
                            OverlayPreferences.setSystemTabStickyLastVisitedBodyId(addr, null);
                            OverlayPreferences.setSystemTabStickyHudTargetBodyId(addr, null);
                        }
                    }
                    loadSystem(e.getStarSystem(), e.getSystemAddress(), true);
                    if (e instanceof FsdJumpEvent) {
                        fireSessionStateChanged();
                    }
                    requestRebuild();
                    persistIfPossible();
                });
            }, "SystemTabPanel-loadSystem").start();

            return;
        }

        // 3) Normal events: just refresh UI on EDT
            requestRebuild();
            persistIfPossible();
    }

    private void handleFirstDiscoveredSystemAnnouncement(ScanEvent e) {
        if (SystemCache.isBulkSystemWrite()
                || !OverlayPreferences.isFirstDiscoveredSystemAnnouncementEnabled()
                || !isFirstDiscoveredPrimaryStarScan(e)) {
            return;
        }

        String key = firstDiscoveredSystemAnnouncementKey(e);
        if (key == null || !announcedFirstDiscoveredSystemKeys.add(key)) {
            return;
        }

        firstDiscoveredSystemTts.speakf("First Discovered System");
    }

    private static String firstDiscoveredSystemAnnouncementKey(ScanEvent e) {
        if (e == null) {
            return null;
        }
        long address = e.getSystemAddress();
        if (address != 0L) {
            return Long.toString(address);
        }
        String system = e.getStarSystem();
        if (!isBlank(system)) {
            return system.trim().toLowerCase(Locale.ROOT);
        }
        String body = e.getBodyName();
        if (!isBlank(body)) {
            return body.trim().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // ---------------------------------------------------------------------
    // Cache loading at startup
    // ---------------------------------------------------------------------

    /**
     * Invoked after tools such as {@link org.dce.ed.logreader.RescanJournalsMain} update SQLite: each open
     * {@link SystemTabPanel} reloads its currently displayed system from {@link SystemCache} on the EDT.
     */
    public static void notifyAllInstancesReloadDisplayedSystemFromCache() {
        SwingUtilities.invokeLater(() -> {
            for (Window w : Window.getWindows()) {
                if (!w.isDisplayable()) {
                    continue;
                }
                collectSystemTabPanels(w).forEach(SystemTabPanel::reloadDisplayedSystemFromCache);
            }
        });
    }

    private static List<SystemTabPanel> collectSystemTabPanels(Container root) {
        List<SystemTabPanel> out = new ArrayList<>();
        walkComponentsForSystemTabPanel(root, out);
        return out;
    }

    private static void walkComponentsForSystemTabPanel(Component c, List<SystemTabPanel> acc) {
        if (c instanceof SystemTabPanel) {
            acc.add((SystemTabPanel) c);
            return;
        }
        if (c instanceof Container) {
            for (Component ch : ((Container) c).getComponents()) {
                walkComponentsForSystemTabPanel(ch, acc);
            }
        }
    }

    /**
     * Re-reads the bodies map for {@link #state}'s current system from {@link SystemCache} (no EDSM fetch).
     * Use after a journal replay refreshed SQLite while this tab still held older RAM state.
     */
    public void reloadDisplayedSystemFromCache() {
        String systemName = state.getSystemName();
        long systemAddress = state.getSystemAddress();
        if ((systemName == null || systemName.isBlank()) && systemAddress == 0L) {
            return;
        }
        loadSystem(systemName == null || systemName.isBlank() ? "" : systemName, systemAddress, false);
    }

    public void refreshFromCache() {
        long startedAtMs = System.currentTimeMillis();
        try {
            // Primary startup source of truth:
            // RescanJournalsMain runs before UI startup and persists the latest system snapshot.
            CachedSystem last = SystemCache.load();
            if (last != null && last.systemName != null && !last.systemName.isBlank() && last.systemAddress != 0L) {
                loadSystem(last.systemName, last.systemAddress, false);
                System.out.println("[EDO][Cache] refreshFromCache: loaded from rescan cache " + last.systemName
                        + " in " + (System.currentTimeMillis() - startedAtMs) + "ms");
                return;
            }

            // Fallback for edge cases where cache has not been populated yet.
            java.nio.file.Path journalDir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
            if (journalDir == null || !java.nio.file.Files.isDirectory(journalDir)) {
                rebuildTable();
                return;
            }
            EliteJournalReader reader = new EliteJournalReader(journalDir);

            String systemName = null;
            long systemAddress = 0L;
            EliteLogEvent transition = reader.findMostRecentSystemTransitionEvent(null);
            if (transition instanceof LocationEvent) {
                LocationEvent e = (LocationEvent) transition;
                systemName = e.getStarSystem();
                systemAddress = e.getSystemAddress();
            } else if (transition instanceof FsdJumpEvent) {
                FsdJumpEvent e = (FsdJumpEvent) transition;
                systemName = e.getStarSystem();
                systemAddress = e.getSystemAddress();
            } else if (transition instanceof CarrierJumpEvent) {
                CarrierJumpEvent e = (CarrierJumpEvent) transition;
                if (e.isDocked() || e.isOnFoot()) {
                    systemName = e.getStarSystem();
                    systemAddress = e.getSystemAddress();
                }
            }

            if ((systemName == null || systemName.isEmpty()) && systemAddress == 0L) {
                rebuildTable();
                System.out.println("[EDO][Cache] refreshFromCache: no current system, took " + (System.currentTimeMillis() - startedAtMs) + "ms");
                return;
            }
            
            loadSystem(systemName, systemAddress, false);
            System.out.println("[EDO][Cache] refreshFromCache: loaded " + systemName + " in " + (System.currentTimeMillis() - startedAtMs) + "ms");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Restores the system tab to the commander's real location after a manual header lookup.
     * Journal transition is preferred (live while playing); cache is the fallback.
     */
    private void reloadCommanderSystem() {
        String systemName = null;
        long systemAddress = 0L;

        try {
            java.nio.file.Path journalDir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
            if (journalDir != null && java.nio.file.Files.isDirectory(journalDir)) {
                EliteJournalReader reader = new EliteJournalReader(journalDir);
                EliteLogEvent transition = reader.findMostRecentSystemTransitionEvent(null);
                if (transition instanceof LocationEvent le) {
                    systemName = le.getStarSystem();
                    systemAddress = le.getSystemAddress();
                } else if (transition instanceof FsdJumpEvent fj) {
                    systemName = fj.getStarSystem();
                    systemAddress = fj.getSystemAddress();
                } else if (transition instanceof CarrierJumpEvent cj) {
                    if (cj.isDocked() || cj.isOnFoot()) {
                        systemName = cj.getStarSystem();
                        systemAddress = cj.getSystemAddress();
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        if (systemName == null || systemName.isBlank()) {
            try {
                CachedSystem last = SystemCache.load();
                if (last != null && last.systemName != null && !last.systemName.isBlank() && last.systemAddress != 0L) {
                    systemName = last.systemName;
                    systemAddress = last.systemAddress;
                }
            } catch (java.io.IOException ex) {
                ex.printStackTrace();
            }
        }

        if (systemName == null || systemName.isBlank()) {
            return;
        }

        loadSystem(systemName, systemAddress, false);
    }

    private void updateTargetBodyFromStatus(StatusEvent e) {
    	// Called from handleLogEvent; may be on a background thread.
    	final long currentSystemAddress = state.getSystemAddress();
    	final Integer destBody = SystemTabTargetLogic.effectiveDestBody(e, currentSystemAddress);
    	final String destName = SystemTabTargetLogic.effectiveDestName(e, currentSystemAddress);

        // Body highlighting:
        // - For planet/body targets, DestinationDisplayName matches a body name and we can map it to a stable bodyId.
        // - For station/fleet carrier targets, DestinationDisplayName is the station/carrier name; Destination.Body is
        //   the parent body id (so we highlight the parent body block).
        Integer highlightBodyId = null;
        if (destName != null) {
            highlightBodyId = findBodyIdByName(destName);
        }
        if (highlightBodyId == null && destBody != null) {
            highlightBodyId = SystemMapRules.mapKeyForJournalBodyId(state.getBodies(), destBody.intValue());
        }

        final Integer newBodyId = highlightBodyId;
        final String newBodyName = null;

        // Intermediate destination (station/fleet carrier):
        // Show an indented row under the parent body when DestinationDisplayName is NOT a body name.
        final Integer newDestParentBodyId;
        final String newDestName;

        boolean destNameIsBody = false;
        if (destName != null) {
            Integer bodyId = findBodyIdByName(destName);
            if (bodyId != null) {
                destNameIsBody = true;
            }
        }

        if (!destNameIsBody && destBody != null && destName != null) {
            BodyInfo parent = null;
            for (BodyInfo bi : state.getBodies().values()) {
                if (bi == null) {
                    continue;
                }
                if (bi.getBodyId() == destBody.intValue()) {
                    parent = bi;
                    break;
                }
            }

            boolean sameAsBody = false;
            if (parent != null) {
                String bodyName = parent.getBodyName();
                String shortName = parent.getShortName();
                if (bodyName != null && destName.equalsIgnoreCase(bodyName)) {
                    sameAsBody = true;
                }
                if (!sameAsBody && shortName != null && destName.equalsIgnoreCase(shortName)) {
                    sameAsBody = true;
                }
            }

            if (!sameAsBody) {
                newDestParentBodyId = destBody;
                newDestName = destName;
            } else {
                newDestParentBodyId = null;
                newDestName = null;
            }
        } else {
            newDestParentBodyId = null;
            newDestName = null;
        }

        SwingUtilities.invokeLater(() -> {
            Integer previousTargetBodyId = targetBodyId;
            boolean changed = false;

            if (newBodyId == null) {
                if (targetBodyId != null) {
                    targetBodyId = null;
                    targetBodyName = null;
                    changed = true;
                }
            } else {
                if (targetBodyId == null || newBodyId.intValue() != targetBodyId.intValue()) {
                    targetBodyId = newBodyId;
                    targetBodyName = newBodyName;
                    changed = true;
                }
            }

            long sysAddr = state.getSystemAddress();
            Map<Integer, BodyInfo> bodiesMap = state.getBodies();
            if (newBodyId != null && sysAddr != 0L && bodiesMap != null && bodiesMap.containsKey(newBodyId)) {
                if (!Objects.equals(stickyHudTargetBodyId, newBodyId)) {
                    stickyHudTargetBodyId = newBodyId;
                    OverlayPreferences.setSystemTabStickyHudTargetBodyId(sysAddr, newBodyId);
                }
            }

            if (newDestParentBodyId == null) {
                if (targetDestinationParentBodyId != null || targetDestinationName != null) {
                    targetDestinationParentBodyId = null;
                    targetDestinationName = null;
                    changed = true;
                }
            } else {
                if (targetDestinationParentBodyId == null
                        || !newDestParentBodyId.equals(targetDestinationParentBodyId)
                        || (targetDestinationName == null && newDestName != null)
                        || (targetDestinationName != null && newDestName == null)
                        || (targetDestinationName != null && newDestName != null && !targetDestinationName.equals(newDestName))) {
                    targetDestinationParentBodyId = newDestParentBodyId;
                    targetDestinationName = newDestName;
                    changed = true;
                }
            }

            boolean bioMutated = applyBioExpandCollapseForTargetChange(previousTargetBodyId, targetBodyId);

            if (changed && newBodyId != null
                    && OverlayPreferences.getSystemTabShipRefMode() == SystemTabShipRefMode.TARGETED_BODY
                    && OverlayPreferences.isSystemPlanMapAutoZoomHudTargetSubsystem()) {
                pendingHudTargetMapZoomBodyId = newBodyId;
            }

            if (changed || bioMutated) {
                requestRebuild();
                fireSessionStateChanged();
            } else {
                table.repaint();
            }
        });
    }

    private void updateNearBodyFromStatus(StatusEvent e) {
        // Called from handleLogEvent; may be on a background thread.
        final long currentSystemAddress = state.getSystemAddress();
        final Integer destBody = SystemTabTargetLogic.effectiveDestBody(e, currentSystemAddress);
        final String destName = SystemTabTargetLogic.effectiveDestName(e, currentSystemAddress);
        final String newBodyName = (e != null) ? e.getBodyName() : null;
        final Double lat = (e != null) ? e.getLatitude() : null;
        final Double lon = (e != null) ? e.getLongitude() : null;
        final Double alt = (e != null) ? e.getAltitude() : null;
        final Double rad = (e != null) ? e.getPlanetRadius() : null;
        final Integer statusSid = (e != null) ? e.getStatusBodyId() : null;
        final String physicalBodyName = (e != null) ? e.getBodyNamePhysical() : null;
        final boolean statusSupercruise = e != null && e.isSupercruise();

        SwingUtilities.invokeLater(() -> {
            if (statusSupercruise) {
                supercruiseDropReferenceBodyId = null;
            }
            statusLatitude = lat;
            statusLongitude = lon;
            statusAltitude = alt;
            statusPlanetRadius = rad;

            String trimmed = (newBodyName != null) ? newBodyName.trim() : null;
            if (trimmed != null && trimmed.isEmpty()) {
                trimmed = null;
            }

            Map<Integer, BodyInfo> bodies = state.getBodies();

            boolean hudBodyTarget = false;
            if (destBody != null && destName != null) {
                Integer idFromDestName = findBodyIdByName(destName);
                hudBodyTarget = idFromDestName != null && idFromDestName.equals(destBody);
            }
            boolean trimmedIsDestOnly = hudBodyTarget && trimmed != null && destName != null
                    && trimmed.equalsIgnoreCase(destName);
            /* Lat/lon only — PlanetRadius alone is set while analysing bodies in FSS and must not imply proximity. */
            boolean hasBodyTelemetryHints = lat != null && lon != null;
            /*
             * Elite can mirror the HUD destination into Status BodyID. When ApproachBody names another world,
             * ignore that BodyID. When there is no ApproachBody line, prefer {@link #lastVisitedNonStarBodyId} over
             * blindly following the mirrored id so "You" stays on the last non-star body until telemetry catches up.
             */
            boolean ignoreStatusBodyIdAsHudMirror = hudBodyTarget
                    && destBody != null
                    && statusSid != null
                    && statusSid.equals(destBody)
                    && (physicalBodyName == null || physicalBodyName.isBlank())
                    && !hasBodyTelemetryHints
                    && approachReferenceBodyId != null
                    && !approachReferenceBodyId.equals(statusSid);

            boolean destMirrorSuspiciousNoApproach = hudBodyTarget
                    && destBody != null
                    && statusSid != null
                    && statusSid.equals(destBody)
                    && (physicalBodyName == null || physicalBodyName.isBlank())
                    && !hasBodyTelemetryHints
                    && approachReferenceBodyId == null;

            Integer resolvedId = null;
            if (hasBodyTelemetryHints) {
                if (physicalBodyName != null && !physicalBodyName.isBlank()) {
                    resolvedId = findBodyIdByName(physicalBodyName.trim());
                }
                if (resolvedId == null && statusSid != null && statusSid.intValue() > 0 && bodies.containsKey(statusSid)) {
                    if (!ignoreStatusBodyIdAsHudMirror) {
                        resolvedId = statusSid;
                    }
                }
                if (resolvedId == null && trimmed != null && !trimmedIsDestOnly) {
                    resolvedId = findBodyIdByName(trimmed);
                }
            } else if (!statusSupercruise) {
                /*
                 * Normal space without lat/lon: ignore Status BodyID/BodyName (FSS discovery mirrors). Proximity
                 * outline only from journal ApproachBody or a supercruise drop onto a body.
                 */
                if (approachReferenceBodyId != null && approachReferenceBodyId.intValue() > 0
                        && bodies.containsKey(approachReferenceBodyId)) {
                    resolvedId = approachReferenceBodyId;
                }
                if (resolvedId == null && supercruiseDropReferenceBodyId != null
                        && supercruiseDropReferenceBodyId.intValue() > 0
                        && bodies.containsKey(supercruiseDropReferenceBodyId)) {
                    resolvedId = supercruiseDropReferenceBodyId;
                }
            } else {
                /* Supercruise without surface fix: ApproachBody or drop only — not FSS-analysed body mirrors. */
                if (approachReferenceBodyId != null && approachReferenceBodyId.intValue() > 0
                        && bodies.containsKey(approachReferenceBodyId)) {
                    resolvedId = approachReferenceBodyId;
                }
                if (resolvedId == null && supercruiseDropReferenceBodyId != null
                        && supercruiseDropReferenceBodyId.intValue() > 0
                        && bodies.containsKey(supercruiseDropReferenceBodyId)) {
                    resolvedId = supercruiseDropReferenceBodyId;
                }
            }
            if (destMirrorSuspiciousNoApproach && resolvedId != null && resolvedId.equals(destBody)) {
                Integer sticky = lastVisitedNonStarBodyId;
                if (sticky != null && sticky.intValue() > 0 && bodies.containsKey(sticky)
                        && !sticky.equals(destBody)) {
                    resolvedId = sticky;
                }
            }

            /*
             * Docked on a fleet carrier: no surface lat/lon; Status BodyID/BodyName often mirror the HUD station
             * target, not the orbit body. Use journal parked orbit (CarrierLocation / Location / Docked) and
             * fall back to Destination.Body (parent world) when parked id is not in the body map yet.
             */
            if (state.isDocked()) {
                Integer fcBody = resolveFleetCarrierParkedBodyForAnchor(bodies);
                if (fcBody != null) {
                    resolvedId = fcBody;
                } else if (!hasBodyTelemetryHints) {
                    Integer dockedDestBody = SystemTabTargetLogic.effectiveDestBody(e, currentSystemAddress);
                    if (dockedDestBody != null) {
                        Integer mapKey = bodyMapKeyForJournalId(bodies, dockedDestBody.intValue());
                        if (mapKey != null) {
                            resolvedId = mapKey;
                        }
                    }
                }
            }

            String nameSignal = (physicalBodyName != null && !physicalBodyName.isBlank())
                    ? physicalBodyName.trim()
                    : (trimmedIsDestOnly ? null : trimmed);
            if (nameSignal == null && resolvedId != null) {
                BodyInfo bi = bodies.get(resolvedId);
                if (bi != null && bi.getBodyName() != null && !bi.getBodyName().isBlank()) {
                    nameSignal = bi.getBodyName().trim();
                }
            }

            if (resolvedId != null && resolvedId.intValue() > 0 && bodies.containsKey(resolvedId)) {
                Integer stickyBefore = lastVisitedNonStarBodyId;
                lastVisitedNonStarBodyId = resolvedId;
                if (!Objects.equals(stickyBefore, resolvedId)) {
                    OverlayPreferences.setSystemTabStickyLastVisitedBodyId(state.getSystemAddress(), resolvedId);
                }
            }

            boolean identityChanged =
                    (nameSignal == null && nearBodyName != null)
                    || (nameSignal != null && nearBodyName == null)
                    || (nameSignal != null && nearBodyName != null && !nameSignal.equalsIgnoreCase(nearBodyName));

            boolean idChanged = !Objects.equals(nearBodyId, resolvedId);

            if (identityChanged) {
                nearBodyName = nameSignal;
            }
            nearBodyId = resolvedId;

            boolean nearRefDirty = identityChanged || idChanged;
            if (nearRefDirty) {
                Consumer<BodyInfo> listener = nearBodyChangedListener;
                if (listener != null) {
                    BodyInfo nearBody = (nearBodyId != null) ? state.getBodies().get(nearBodyId) : null;
                    listener.accept(nearBody);
                }
                fireSessionStateChanged();
                // Status is high-frequency; full rebuild only when near-body identity changes so distance column,
                // sort order, and plan-map “You” (via {@link #refreshPlanMap}) stay in sync with the new proximity.
                requestRebuild();
            }

            boolean dockedChanged = lastStatusDockedForShipAnchorUi != null
                    && lastStatusDockedForShipAnchorUi.booleanValue() != state.isDocked();
            lastStatusDockedForShipAnchorUi = Boolean.valueOf(state.isDocked());
            if (dockedChanged && !nearRefDirty) {
                // Undock/dock toggles ship anchor (e.g. carrier parked vs near-body) without necessarily changing
                // BodyName / BodyId in the same Status tick.
                requestRebuild();
            }

            if (OverlayPreferences.isSystemTabDistanceFromShip() && !nearRefDirty) {
                scheduleShipTelemetryRebuild();
            }
        });
    }

    /**
     * Journal {@code Scan} with {@code ScanType: Detailed} (DSS / probe mapping): optional ship-ref anchor only.
     * Does not update {@link #nearBodyId} — table proximity outline stays on real approach / surface telemetry.
     */
    private void handleDetailedSurfaceScanProximity(ScanEvent se) {
        if (se == null) {
            return;
        }
        String scanType = se.getScanType();
        if (scanType == null || !"Detailed".equalsIgnoreCase(scanType.trim())) {
            return;
        }
        if (ScanParents.scanIndicatesStellarBody(se)) {
            return;
        }
        if (scanBodyNameLooksLikeBeltOrRing(se.getBodyName())) {
            return;
        }
        long cur = state.getSystemAddress();
        if (cur != 0L && se.getSystemAddress() != 0L && se.getSystemAddress() != cur) {
            return;
        }
        int id = se.getBodyId();
        if (id < 0 && se.getBodyName() != null && !se.getBodyName().isBlank()) {
            Integer found = findBodyIdByName(se.getBodyName().trim());
            id = found != null ? found.intValue() : -1;
        }
        if (id < 0) {
            return;
        }
        Integer previousDss = dssDetailedScanReferenceBodyId;
        dssDetailedScanReferenceBodyId = Integer.valueOf(id);
        supercruiseDropReferenceBodyId = null;

        boolean dssRefChanged = !Objects.equals(previousDss, dssDetailedScanReferenceBodyId);
        if (OverlayPreferences.isSystemTabDistanceFromShip() || dssRefChanged) {
            requestRebuild();
        }
    }

    private static boolean scanBodyNameLooksLikeBeltOrRing(String bodyName) {
        if (bodyName == null) {
            return false;
        }
        String n = bodyName.toLowerCase(Locale.ROOT);
        return n.contains("belt cluster")
                || n.contains("ring")
                || n.contains("belt ");
    }

    private void handleApproachBodyEvent(ApproachBodyEvent ab) {
        if (ab == null) {
            return;
        }
        long cur = state.getSystemAddress();
        if (cur != 0L && ab.getSystemAddress() != 0L && ab.getSystemAddress() != cur) {
            return;
        }
        Integer previousApproach = approachReferenceBodyId;
        int id = ab.getBodyId();
        if (id < 0 && ab.getBodyName() != null && !ab.getBodyName().isBlank()) {
            Integer found = findBodyIdByName(ab.getBodyName().trim());
            id = found != null ? found.intValue() : -1;
        }
        if (id >= 0) {
            approachReferenceBodyId = Integer.valueOf(id);
            if (id > 0) {
                Integer prev = lastVisitedNonStarBodyId;
                lastVisitedNonStarBodyId = Integer.valueOf(id);
                if (!Objects.equals(prev, lastVisitedNonStarBodyId)) {
                    OverlayPreferences.setSystemTabStickyLastVisitedBodyId(cur, lastVisitedNonStarBodyId);
                }
            }
        } else {
            approachReferenceBodyId = null;
        }
        boolean approachRefChanged = !Objects.equals(previousApproach, approachReferenceBodyId);
        if (OverlayPreferences.isSystemTabDistanceFromShip() || approachRefChanged) {
            requestRebuild();
        }
        if (id >= 0 && approachRefChanged) {
            final int logId = id;
            SwingUtilities.invokeLater(() -> {
                systemPlanMapPanel.focusCameraOnApproachedBody(logId);
                systemPlanMapPanel.logOrbitRingCentersForApproachDebug(logId);
            });
        }
    }

    private void handleLeaveBodyEvent(LeaveBodyEvent lb) {
        if (lb == null) {
            return;
        }
        long cur = state.getSystemAddress();
        if (cur != 0L && lb.getSystemAddress() != 0L && lb.getSystemAddress() != cur) {
            return;
        }
        approachReferenceBodyId = null;
        dssDetailedScanReferenceBodyId = null;
        if (OverlayPreferences.isSystemTabDistanceFromShip()) {
            requestRebuild();
        }
    }

    private void handleSupercruiseExitEvent(SupercruiseExitEvent se) {
        if (se == null || se.isTaxi()) {
            return;
        }
        long cur = state.getSystemAddress();
        if (cur != 0L && se.getSystemAddress() != 0L && se.getSystemAddress() != cur) {
            return;
        }
        int id = se.getBodyId();
        if (id < 0 && se.getBody() != null && !se.getBody().isBlank()) {
            Integer found = findBodyIdByName(se.getBody().trim());
            id = found != null ? found.intValue() : -1;
        }
        if (id > 0) {
            supercruiseDropReferenceBodyId = Integer.valueOf(id);
            Integer prev = lastVisitedNonStarBodyId;
            lastVisitedNonStarBodyId = Integer.valueOf(id);
            if (!Objects.equals(prev, lastVisitedNonStarBodyId)) {
                OverlayPreferences.setSystemTabStickyLastVisitedBodyId(cur, lastVisitedNonStarBodyId);
            }
        } else {
            supercruiseDropReferenceBodyId = null;
        }
        if (OverlayPreferences.isSystemTabDistanceFromShip()) {
            requestRebuild();
        }
    }

    
    private Integer findBodyIdByName(String bodyName) {
        if (bodyName == null || bodyName.isBlank()) {
            return null;
        }

        // 1) Prefer exact match on BodyName (map key, not journal BodyID)
        for (Map.Entry<Integer, BodyInfo> e : state.getBodies().entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String nm = e.getValue().getBodyName();
            if (nm != null && bodyName.equalsIgnoreCase(nm)) {
                return e.getKey();
            }
        }

        // 2) Fallback: match on "short name" (e.g., system + body index)
        for (Map.Entry<Integer, BodyInfo> e : state.getBodies().entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String shortName = e.getValue().getShortName();
            if (shortName != null && bodyName.equalsIgnoreCase(shortName)) {
                return e.getKey();
            }
        }

        return null;
    }

    /** Restore {@link #lastVisitedNonStarBodyId} from prefs after bodies for {@code systemAddress} are loaded. */
    private void hydrateLastVisitedStickyFromPrefs(long systemAddress) {
        Integer stored = OverlayPreferences.getSystemTabStickyLastVisitedBodyId(systemAddress);
        if (stored == null) {
            lastVisitedNonStarBodyId = null;
            return;
        }
        Map<Integer, BodyInfo> bodies = state.getBodies();
        if (bodies != null && stored.intValue() > 0 && bodies.containsKey(stored)) {
            lastVisitedNonStarBodyId = stored;
        } else {
            lastVisitedNonStarBodyId = null;
            OverlayPreferences.setSystemTabStickyLastVisitedBodyId(systemAddress, null);
        }
    }

    private void hydrateStickyHudTargetFromPrefs(long systemAddress) {
        Integer stored = OverlayPreferences.getSystemTabStickyHudTargetBodyId(systemAddress);
        if (stored == null) {
            stickyHudTargetBodyId = null;
            return;
        }
        Map<Integer, BodyInfo> bodies = state.getBodies();
        Integer mapKey = bodies != null ? SystemMapRules.mapKeyForJournalBodyId(bodies, stored.intValue()) : null;
        if (mapKey != null && bodies.containsKey(mapKey)) {
            stickyHudTargetBodyId = mapKey;
        } else {
            stickyHudTargetBodyId = null;
            OverlayPreferences.setSystemTabStickyHudTargetBodyId(systemAddress, null);
        }
    }

    private void loadSystem(String systemName, long systemAddress, boolean allowEdsmEnrichment) {
        long startedAtMs = System.currentTimeMillis();
        SystemCache cache = SystemCache.getInstance();
        CachedSystem cs = cache.get(systemAddress, systemName);

        // Start from a clean state for this system.
        state.setSystemName(systemName);
        state.setSystemAddress(systemAddress);
        orbitAnimFreezeEpoch = null;
        orbitAnimPlayBaseEpoch = null;
        orbitAnimSimInstant = null;
        state.resetBodies();
        state.setTotalBodies(null);
        state.setNonBodyCount(null);
        state.setFssProgress(null);
        state.setAllBodiesFound(null);
        bioCollapsedDefaultsSeededForCurrentSystem = false;
        cancelBioExpandDelayedOpenPending();
        cancelBioHeaderAllDwellPending();
        passthroughHoverExpandBodyIds.clear();
        bioColumnHeaderExpandCueHover = false;
        bioAutoExpandedForTargetBodyId = null;
        approachReferenceBodyId = null;
        dssDetailedScanReferenceBodyId = null;
        supercruiseDropReferenceBodyId = null;
        lastVisitedNonStarBodyId = null;
        stickyHudTargetBodyId = null;
        pendingHudTargetMapZoomBodyId = null;
        lastStatusDockedForShipAnchorUi = null;

        // 1) Load from cache if we have it
        if (cs != null) {
            cache.loadInto(state, cs);
        }
        int bodiesAfterCache = state.getBodies() != null ? state.getBodies().size() : 0;
        // Cache-only loads pass allowEdsmEnrichment=false; if SQLite still has only a trivial slice of the system
        // (e.g. one Detailed scan) we still ask EDSM once so the map is usable without a full journal replay.
        boolean sparseEdsmBackfill = !allowEdsmEnrichment
                && systemAddress != 0L
                && systemName != null && !systemName.isBlank()
                && bodiesAfterCache <= 1;
        // Binary companion "… <systemName> B" can be present in SQLite with empty starType / NaN distance (never
        // EDSM-merged after a multi-body cache write). Re-fetch EDSM once so orbit geometry + distances match journal.
        boolean companionEdsmBackfill = !allowEdsmEnrichment
                && systemAddress != 0L
                && systemName != null && !systemName.isBlank()
                && companionLetterStarLooksIncomplete(state, systemName);

        // 2) Optionally enrich with EDSM via a single bodies call.
        if (allowEdsmEnrichment || sparseEdsmBackfill || companionEdsmBackfill) {
            try {
                BodiesResponse edsmBodies = edsmClient.showBodies(systemName);
                if (edsmBodies != null) {
                    edsmClient.mergeBodiesFromEdsm(state, edsmBodies);
                }
            } catch (Exception ex) {
                // EDSM is best-effort; overlay should still work from cache/logs.
                ex.printStackTrace();
            }
        }
        hydrateLastVisitedStickyFromPrefs(systemAddress);
        hydrateStickyHudTargetFromPrefs(systemAddress);

        // 3) Refresh UI and persist merged result
        rebuildTable();
        persistIfPossible();
        System.out.println("[EDO][Cache] loadSystem lookup+hydrate for " + systemName + " took " + (System.currentTimeMillis() - startedAtMs) + "ms");
    }

    /**

     * True when the canonical secondary-star row {@code "<systemName> B"} exists but still lacks data EDSM can
     * supply (star class, distance to arrival), which breaks binary orbit layout after a partial journal cache.
     */
    private static boolean companionLetterStarLooksIncomplete(SystemState state, String systemName) {
        if (state == null || state.getBodies() == null || systemName == null || systemName.isBlank()) {
            return false;
        }
        String keyName = systemName.trim() + " B";
        BodyInfo bStar = null;
        for (BodyInfo b : state.getBodies().values()) {
            if (b == null || b.getBodyName() == null) {
                continue;
            }
            if (keyName.equals(b.getBodyName().trim())) {
                bStar = b;
                break;
            }
        }
        if (bStar == null) {
            return false;
        }
        boolean missingType = bStar.getStarType() == null || bStar.getStarType().isBlank();
        boolean missingDist = Double.isNaN(bStar.getDistanceLs()) || bStar.getDistanceLs() <= 0.0;
        return missingType || missingDist;
    }

    private final AtomicBoolean rebuildPending = new AtomicBoolean(false);

    // ---------------------------------------------------------------------
    // UI rebuild from SystemState
    // ---------------------------------------------------------------------
    private void requestRebuild() {
        if (!rebuildPending.compareAndSet(false, true)) {
            return; // already queued
        }

        SwingUtilities.invokeLater(() -> {
            try {
                rebuildTable();
            } finally {
                rebuildPending.set(false);
            }
        });
    }

    private void rebuildTable() {
        systemSession = SystemSessionFactory.open(state);
        dedupeBodiesByName();
        updateHeaderLabel();

        boolean justSeededBioCollapseDefaults = false;
        if (!bioCollapsedDefaultsSeededForCurrentSystem) {
            long addr = state.getSystemAddress();
            if (addr != 0L && state.getBodies() != null && !state.getBodies().isEmpty()) {
                bioDetailsCollapsedBodyIds.clear();
                bioDetailsCollapsedBodyIds.addAll(collectExpandableBioBodyIds());
                cancelBioExpandDelayedOpenPending();
                cancelBioHeaderAllDwellPending();
                passthroughHoverExpandBodyIds.clear();
                bioColumnHeaderExpandCueHover = false;
                bioAutoExpandedForTargetBodyId = null;
                bioCollapsedDefaultsSeededForCurrentSystem = true;
                justSeededBioCollapseDefaults = true;
            }
        }
        if (justSeededBioCollapseDefaults) {
            reconcileAutoExpandBioForCurrentTargetBody();
        }

        Set<Integer> hiddenBioDetails = new HashSet<>(bioDetailsCollapsedBodyIds);
        for (Integer latched : passthroughHoverExpandBodyIds) {
            hiddenBioDetails.remove(latched);
        }
        SystemTabTableSortMode tableSortMode = OverlayPreferences.getSystemTabTableSortMode();
        boolean sortByValue = tableSortMode == SystemTabTableSortMode.BY_VALUE;
        boolean shipDistMode = !sortByValue && tableSortMode == SystemTabTableSortMode.FROM_SHIP;
        Map<Integer, Double> shipCentric = shipDistMode ? computeShipCentricDistancesLs() : null;
        boolean shipAnchorMissing = shipDistMode && (shipCentric == null || shipCentric.isEmpty());
        if (shipAnchorMissing) {
            shipDistMode = false;
            shipCentric = null;
        }
        Map<Integer, BodyInfo> bodies = state.getBodies();
        Map<Integer, Double> geometryFallbackDistLs = null;
        if (!shipDistMode && bodies != null && !bodies.isEmpty()) {
            int anchKey = SystemOrbitGeometry.primaryAnchorBodyMapKey(bodies);
            Map<Integer, double[]> posGeom = SystemOrbitGeometry.bodyPositionsMetres(bodies, tableDistanceEpoch(),
                    freezeBarycentreStarsDuringOrbitAnim());
            double[] anchPos = posGeom != null ? posGeom.get(Integer.valueOf(anchKey)) : null;
            if (anchPos != null && anchPos.length >= 3) {
                geometryFallbackDistLs = SystemOrbitGeometry.distancesFromPointLs(bodies, anchPos);
            }
        }

        List<Row> rows = BioTableBuilder.buildRows(systemTableBodiesExcludingBarycentres(bodies), false,
                hiddenBioDetails.isEmpty() ? null : hiddenBioDetails,
                shipDistMode,
                shipCentric,
                false,
                sortByValue,
                geometryFallbackDistLs);
        injectIntermediateDestinationRow(rows);
        tableModel.setRows(rows);
        if (shipDistMode && shipCentric != null) {
            lastShipCentricDistLsSnapshot = new HashMap<>(shipCentric);
        } else {
            lastShipCentricDistLsSnapshot = Collections.emptyMap();
        }
        table.getTableHeader().repaint();
        refreshPlanMap();
        SwingUtilities.invokeLater(this::applySystemBodiesTableColumnLayout);

        // Debug only:
        // debugDumpBioRowsToConsole();
    }

    static Map<Integer, BodyInfo> systemTableBodiesExcludingBarycentres(Map<Integer, BodyInfo> bodies) {
        if (bodies == null || bodies.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, BodyInfo> tableBodies = new LinkedHashMap<>(bodies.size());
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            BodyInfo body = e.getValue();
            if (body == null || body.isScanBarycentreRow()) {
                continue;
            }
            tableBodies.put(e.getKey(), body);
        }
        return tableBodies;
    }

    private void updateSystemModelStatus(SystemModelService.ModelHandle handle) {
        if (systemModelStatusLabel == null) {
            return;
        }
        if (handle == null || state == null) {
            systemModelStatusLabel.setText(" ");
            return;
        }
        if (handle.state() == SystemModelService.ModelState.OK) {
            systemModelStatusLabel.setText(" ");
            systemModelStatusLabel.setForeground(EdoUi.User.MAIN_TEXT);
            return;
        }
        if (handle.statusMessage() != null) {
            systemModelStatusLabel.setText(handle.statusMessage());
        }
        systemModelStatusLabel.setForeground(
                handle.state() == SystemModelService.ModelState.ERROR
                        ? EdoUi.User.ERROR
                        : EdoUi.User.WARNING);
    }

    private void updateSystemModelStatus() {
        if (systemSession != null) {
            updateSystemModelStatus(systemSession.handle());
            return;
        }
        if (state == null) {
            updateSystemModelStatus(null);
            return;
        }
        updateSystemModelStatus(SystemModelService.rebuild(state, false));
    }

    /** Latest journal-authoritative session for this tab (shared with hierarchy graph via registry). */
    public SystemSession getSystemSession() {
        return systemSession;
    }

    /** Updates the orbital plan map (journal-derived X/Y projection). */
    private void refreshPlanMap() {
        Map<Integer, BodyInfo> bodies = state.getBodies();
        if (bodies == null || bodies.isEmpty()) {
            if (orbitAnimDemoTimer != null) {
                orbitAnimDemoTimer.stop();
            }
            orbitAnimDemoActive = false;
            orbitAnimFreezeEpoch = null;
            orbitAnimPlayBaseEpoch = null;
            orbitAnimSimInstant = null;
            orbitAnimSuppressPlayToggleHandler = true;
            try {
                if (orbitAnimPlayButton != null) {
                    orbitAnimPlayButton.setSelected(false);
                }
            } finally {
                orbitAnimSuppressPlayToggleHandler = false;
            }
            systemPlanMapPanel.clearScene();
            return;
        }
        Instant mapEpoch;
        if (orbitAnimDemoActive) {
            mapEpoch = orbitAnimSimInstant;
        } else {
            if (orbitAnimFreezeEpoch == null) {
                orbitAnimFreezeEpoch = Instant.now();
            }
            mapEpoch = orbitAnimFreezeEpoch;
        }
        Map<Integer, double[]> pos = SystemOrbitGeometry.bodyPositionsMetres(bodies, mapEpoch,
                freezeBarycentreStarsDuringOrbitAnim());
        updateSystemModelStatus();
        Integer shipAnchorMap = resolvePlanMapShipAnchorBodyId();
        double[] ship = null;
        if (shipAnchorMap != null) {
            ship = SystemOrbitGeometry.shipPositionMetres(
                    bodies,
                    pos,
                    shipAnchorMap.intValue(),
                    statusLatitude,
                    statusLongitude,
                    statusAltitude,
                    statusPlanetRadius);
        }
        Integer planMapTriangleAnchor = resolvePlanMapTriangleAnchorBodyId();
        Integer proximityHighlight = null;
        if (nearBodyId != null && bodies.containsKey(nearBodyId)
                && !Objects.equals(nearBodyId, planMapTriangleAnchor)) {
            proximityHighlight = nearBodyId;
        }
        if (orbitAnimDemoActive) {
            if (systemPlanMapPanel.tryApplyPositionUpdate(
                    bodies, pos, ship, planMapTriangleAnchor, proximityHighlight, orbitAnimDemoActive, mapEpoch)) {
                return;
            }
            orbitAnimDemoActive = false;
            if (orbitAnimDemoTimer != null) {
                orbitAnimDemoTimer.stop();
            }
            if (orbitAnimPlayButton != null) {
                orbitAnimPlayButton.setSelected(false);
            }
            boolean hudZoomPending = pendingHudTargetMapZoomBodyId != null;
            systemPlanMapPanel.setSkipProximityHopForHudTarget(hudZoomPending);
            systemPlanMapPanel.setScene(bodies, pos, ship, planMapTriangleAnchor, proximityHighlight,
                    orbitAnimDemoActive, mapEpoch, systemSession);
            systemPlanMapPanel.setSkipProximityHopForHudTarget(false);
            systemPlanMapPanel.syncViewCenterToSubsystemHubAfterOrbitPause();
            finishPendingHudTargetMapZoom();
            return;
        }
        boolean hudZoomPending = pendingHudTargetMapZoomBodyId != null;
        systemPlanMapPanel.setSkipProximityHopForHudTarget(hudZoomPending);
        systemPlanMapPanel.setScene(bodies, pos, ship, planMapTriangleAnchor, proximityHighlight,
                orbitAnimDemoActive, mapEpoch, systemSession);
        systemPlanMapPanel.setSkipProximityHopForHudTarget(false);
        finishPendingHudTargetMapZoom();
    }

    private void finishPendingHudTargetMapZoom() {
        Integer zoomBody = pendingHudTargetMapZoomBodyId;
        pendingHudTargetMapZoomBodyId = null;
        if (zoomBody == null || orbitAnimDemoActive) {
            return;
        }
        Map<Integer, BodyInfo> bodies = state.getBodies();
        Integer mapKey = bodyMapKeyForJournalId(bodies, zoomBody.intValue());
        if (mapKey == null) {
            return;
        }
        systemPlanMapPanel.focusCameraOnHudTargetSubsystem(mapKey.intValue());
    }

    /** True-scale orbit sim advances wide-binary stars on the mutual barycentre ring. */
    private boolean freezeBarycentreStarsDuringOrbitAnim() {
        return false;
    }

    /** Pause fast-forward: freeze map at current sim instant (do not rewind to play T+0). */
    private void pauseOrbitAnimPlayback() {
        orbitAnimDemoActive = false;
        if (orbitAnimDemoTimer != null) {
            orbitAnimDemoTimer.stop();
        }
        if (orbitAnimSimInstant != null) {
            orbitAnimFreezeEpoch = orbitAnimSimInstant;
        }
        refreshPlanMap();
        systemPlanMapPanel.syncViewCenterToSubsystemHubAfterOrbitPause();
    }

    /**
     * Stop simulation and rebuild the map at {@link Instant#now()} (live journal geometry), clearing T+ state.
     */
    private void stopOrbitAnimSimulation() {
        orbitAnimDemoActive = false;
        if (orbitAnimDemoTimer != null) {
            orbitAnimDemoTimer.stop();
        }
        orbitAnimFreezeEpoch = null;
        orbitAnimPlayBaseEpoch = null;
        orbitAnimSimInstant = null;
        orbitAnimSuppressPlayToggleHandler = true;
        try {
            if (orbitAnimPlayButton != null && orbitAnimPlayButton.isSelected()) {
                orbitAnimPlayButton.setSelected(false);
            }
        } finally {
            orbitAnimSuppressPlayToggleHandler = false;
        }
        refreshPlanMap();
        systemPlanMapPanel.syncViewCenterToSubsystemHubAfterOrbitPause();
    }

    private void tickOrbitAnimDemo() {
        if (!orbitAnimDemoActive) {
            return;
        }
        /* Fixed wall step = timer period. Variable EDT deltas (e.g. 250 ms after a hitch) were advancing huge jumps
         * of model time in one frame at high d/s, which reads as violent hopping along orbits. */
        double simDays = (ORBIT_ANIM_TIMER_MS / 1000.0) * orbitAnimDaysPerWallSecond;
        long nanos = Math.max(1L, Math.round(simDays * 86400e9));
        orbitAnimSimInstant = orbitAnimSimInstant.plusNanos(nanos);
        refreshPlanMap();
    }

    private static String formatOrbitAnimSpeedLabel(double daysPerWallSec) {
        return String.format(Locale.US, "%.0f d/s", Double.valueOf(daysPerWallSec));
    }

    private void setOrbitAnimSpeedValue(int daysPerWallSecond) {
        int min = OverlayPreferences.getSystemTabOrbitAnimDaysPerWallSecondMin();
        int max = OverlayPreferences.getSystemTabOrbitAnimDaysPerWallSecondMax();
        int v = Math.max(min, Math.min(max, daysPerWallSecond));
        orbitAnimDaysPerWallSecond = v;
        OverlayPreferences.setSystemTabOrbitAnimDaysPerWallSecond(v);
        if (orbitAnimSpeedValueLabel != null) {
            orbitAnimSpeedValueLabel.setText(formatOrbitAnimSpeedLabel(orbitAnimDaysPerWallSecond));
        }
        updateOrbitAnimSpeedChevrons();
    }

    private void updateOrbitAnimSpeedChevrons() {
        if (orbitAnimSpeedDownButton == null || orbitAnimSpeedUpButton == null) {
            return;
        }
        int min = OverlayPreferences.getSystemTabOrbitAnimDaysPerWallSecondMin();
        int max = OverlayPreferences.getSystemTabOrbitAnimDaysPerWallSecondMax();
        int v = (int) Math.round(orbitAnimDaysPerWallSecond);
        orbitAnimSpeedDownButton.setEnabled(v > min);
        orbitAnimSpeedUpButton.setEnabled(v < max);
    }

    /**
     * Orbit orbit playback toolbar: scales with {@link #uiFont} (slightly larger than the table), play/pause icons sized
     * to the computed row height.
     */
    private void applyOrbitMapToolbarTypography(String slowerTt, String fasterTt, String speedValueTt) {
        if (orbitAnimPlayButton == null || orbitAnimStopButton == null || orbitAnimSpeedDownButton == null
                || orbitAnimSpeedUpButton == null || orbitAnimSpeedValueLabel == null) {
            return;
        }
        Font base = uiFont != null ? uiFont : getFont();
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        float toolbarPt = Math.max(12f, base.getSize2D() * 1.15f + 1f);
        Font toolbarFont = base.deriveFont(Font.PLAIN, toolbarPt);

        FontMetrics fm;
        if (isDisplayable()) {
            fm = getFontMetrics(toolbarFont);
        } else {
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            try {
                fm = g2.getFontMetrics(toolbarFont);
            } finally {
                g2.dispose();
            }
        }
        int rowH = fm.getHeight();
        int iconSize = Math.max(14, Math.min(46, Math.round(rowH * 0.95f + 2)));

        orbitAnimPlayButton.setFont(toolbarFont);
        orbitAnimPlayButton.setText(null);
        orbitAnimPlayButton.setIcon(new OrbitPlaybackTransportIcons.PlayTriangleIcon(iconSize));
        orbitAnimPlayButton.setSelectedIcon(new OrbitPlaybackTransportIcons.PauseBarsIcon(iconSize));
        orbitAnimPlayButton.setHorizontalAlignment(SwingConstants.CENTER);
        orbitAnimPlayButton.setVerticalAlignment(SwingConstants.CENTER);
        orbitAnimPlayButton.setIconTextGap(0);
        orbitAnimPlayButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        int btnH = iconSize + Math.max(4, (int) Math.ceil(Math.max(2, fm.getDescent())));
        int btnW = iconSize + Math.max(8, (int) Math.round(iconSize * 0.15) + 6);
        int playSide = Math.max(btnW, btnH);
        orbitAnimPlayButton.setPreferredSize(new Dimension(playSide, playSide));

        orbitAnimStopButton.setFont(toolbarFont);
        orbitAnimStopButton.setText(null);
        orbitAnimStopButton.setIcon(new OrbitPlaybackTransportIcons.StopSquareIcon(iconSize));
        orbitAnimStopButton.setHorizontalAlignment(SwingConstants.CENTER);
        orbitAnimStopButton.setVerticalAlignment(SwingConstants.CENTER);
        orbitAnimStopButton.setIconTextGap(0);
        orbitAnimStopButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        orbitAnimStopButton.setPreferredSize(new Dimension(playSide, playSide));

        orbitAnimSpeedValueLabel.setFont(toolbarFont);
        orbitAnimSpeedValueLabel.setToolTipText(speedValueTt);

        int chevSize = Math.max(12, Math.min(40, Math.round(rowH * 0.88f)));
        Icon chevL = new OrbitPlaybackTransportIcons.DoubleChevronLeftIcon(chevSize);
        Icon chevR = new OrbitPlaybackTransportIcons.DoubleChevronRightIcon(chevSize);
        orbitAnimSpeedDownButton.setText(null);
        orbitAnimSpeedDownButton.setIcon(chevL);
        orbitAnimSpeedDownButton.setIconTextGap(0);
        orbitAnimSpeedUpButton.setText(null);
        orbitAnimSpeedUpButton.setIcon(chevR);
        orbitAnimSpeedUpButton.setIconTextGap(0);

        configureOrbitSpeedChevronButton(orbitAnimSpeedDownButton, toolbarFont, slowerTt);
        configureOrbitSpeedChevronButton(orbitAnimSpeedUpButton, toolbarFont, fasterTt);

        int chevPrefW = chevL.getIconWidth() + 8;
        int chevPrefH = Math.max(playSide, rowH + 4);
        orbitAnimSpeedDownButton.setPreferredSize(new Dimension(chevPrefW, chevPrefH));
        orbitAnimSpeedUpButton.setPreferredSize(new Dimension(chevPrefW, chevPrefH));

        updateOrbitAnimSpeedChevrons();
        applySystemPlanMapCollapseButtonTypography(toolbarFont, fm, iconSize);
    }

    private void applySystemPlanMapCollapseButtonTypography(Font toolbarFont, FontMetrics fm, int iconSize) {
        if (systemPlanMapCollapseButton == null || systemPlanMapExpandButton == null) {
            return;
        }
        int chevSize = Math.max(12, Math.min(40, Math.round(fm.getHeight() * 0.88f)));
        Icon down = new OrbitPlaybackTransportIcons.ChevronDownIcon(chevSize);
        Icon up = new OrbitPlaybackTransportIcons.ChevronUpIcon(chevSize);
        int btnH = iconSize + Math.max(4, (int) Math.ceil(Math.max(2, fm.getDescent())));
        int btnW = chevSize + Math.max(8, (int) Math.round(chevSize * 0.15) + 6);
        int side = Math.max(btnW, btnH);
        Dimension pref = new Dimension(side, side);
        configureSystemPlanMapChromeButton(systemPlanMapCollapseButton, toolbarFont, down, pref);
        configureSystemPlanMapChromeButton(systemPlanMapExpandButton, toolbarFont, up, pref);
        updateMapToolbarHoverAppearance();
    }

    private static void configureSystemPlanMapChromeButton(JButton b, Font font, Icon icon, Dimension pref) {
        if (b == null) {
            return;
        }
        b.setFont(font);
        b.setText(null);
        b.setIcon(icon);
        b.setIconTextGap(0);
        b.setMargin(new java.awt.Insets(0, 0, 0, 0));
        b.setHorizontalAlignment(SwingConstants.CENTER);
        b.setVerticalAlignment(SwingConstants.CENTER);
        b.setForeground(EdoUi.User.MAIN_TEXT);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(true);
        b.setFocusable(false);
        b.setFocusPainted(false);
        if (pref != null) {
            b.setPreferredSize(pref);
        }
    }

    private void collapseSystemPlanMap() {
        if (systemTableMapSplit == null || systemPlanMapCollapsed) {
            return;
        }
        systemPlanMapSplitRatioBeforeCollapse = computeVerticalSplitRatio(systemTableMapSplit);
        systemPlanMapCollapsed = true;
        systemPlanMapPanel.setVisible(false);
        applySystemPlanMapCollapsedDivider();
        updateSystemPlanMapCollapseButtons();
        revalidate();
        repaint();
    }

    private void expandSystemPlanMap() {
        if (systemTableMapSplit == null || !systemPlanMapCollapsed) {
            return;
        }
        systemPlanMapCollapsed = false;
        systemPlanMapPanel.setVisible(true);
        double ratio = systemPlanMapSplitRatioBeforeCollapse;
        if (ratio < 0.05 || ratio > 0.95) {
            ratio = OverlayPreferences.getSystemTabPanelTableSplitRatio();
        }
        configureSystemTableMapSplit(systemTableMapSplit, ratio);
        updateSystemPlanMapCollapseButtons();
        revalidate();
        repaint();
    }

    private void updateSystemPlanMapCollapseButtons() {
        if (systemPlanMapCollapseButton == null || systemPlanMapExpandButton == null) {
            return;
        }
        systemPlanMapCollapseButton.setVisible(!systemPlanMapCollapsed);
        systemPlanMapExpandButton.setVisible(systemPlanMapCollapsed);
    }

    private void applySystemPlanMapCollapsedDivider() {
        if (systemTableMapSplit == null) {
            return;
        }
        int splitH = systemTableMapSplit.getHeight();
        if (splitH < 32) {
            return;
        }
        int toolbarH = 26;
        if (systemPlanMapToolbar != null) {
            toolbarH = Math.max(22, systemPlanMapToolbar.getPreferredSize().height);
        }
        int divider = systemTableMapSplit.getDividerSize();
        int loc = splitH - divider - toolbarH;
        systemTableMapSplit.setResizeWeight(1.0);
        systemTableMapSplit.setDividerLocation(Math.max(0, loc));
    }

    private void onSystemTableMapDividerMoved() {
        if (systemTableMapSplit == null) {
            return;
        }
        if (systemPlanMapCollapsed) {
            int splitH = systemTableMapSplit.getHeight();
            if (splitH < 32) {
                return;
            }
            int toolbarH = systemPlanMapToolbar != null
                    ? Math.max(22, systemPlanMapToolbar.getPreferredSize().height)
                    : 26;
            int divider = systemTableMapSplit.getDividerSize();
            int collapsedLoc = splitH - divider - toolbarH;
            if (systemTableMapSplit.getDividerLocation() < collapsedLoc - 8) {
                expandSystemPlanMap();
            }
            return;
        }
        saveSystemTableMapSplitRatio();
    }

    /** Ship / star distance toggles: icon size tracks {@link #uiFont}. */
    private void applyDistanceToggleIcons() {
        if (distFromShipButton == null || distFromStarButton == null || distByValueButton == null) {
            return;
        }
        Font f = uiFont != null ? uiFont : getFont();
        int sz = 22;
        if (f != null) {
            FontMetrics fm;
            if (isDisplayable()) {
                fm = getFontMetrics(f);
            } else {
                BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = img.createGraphics();
                try {
                    fm = g2.getFontMetrics(f);
                } finally {
                    g2.dispose();
                }
            }
            sz = Math.max(18, Math.min(30, (int) Math.round(fm.getHeight() * 1.05)));
        }
        distFromShipButton.setIcon(new DistanceToggleIcons.CircleAroundRocketIcon(sz));
        distFromStarButton.setIcon(new DistanceToggleIcons.CircleAroundStarIcon(sz));
        distByValueButton.setIcon(new DistanceToggleIcons.CircleAroundDollarIcon(sz));
        int btn = sz + 4;
        distFromShipButton.setPreferredSize(new Dimension(btn, btn));
        distFromStarButton.setPreferredSize(new Dimension(btn, btn));
        distByValueButton.setPreferredSize(new Dimension(btn, btn));
    }

    private static void configureOrbitSpeedChevronButton(JButton b, Font font, String toolTipText) {
        if (b == null) {
            return;
        }
        b.setToolTipText(toolTipText);
        b.setMargin(new java.awt.Insets(2, 4, 2, 4));
        b.setHorizontalAlignment(SwingConstants.CENTER);
        b.setVerticalAlignment(SwingConstants.CENTER);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusable(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setForeground(EdoUi.User.MAIN_TEXT);
        b.setFont(font != null ? font : b.getFont());
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static String canonicalBioName(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return s;
        }

        String[] parts = s.split("\\s+");
        // Collapse "Genus Genus Species..." -> "Genus Species..."
        if (parts.length >= 3 && parts[0].equalsIgnoreCase(parts[1])) {
            StringBuilder sb = new StringBuilder(parts[0]);
            for (int i = 2; i < parts.length; i++) {
                sb.append(' ').append(parts[i]);
            }
            return sb.toString();
        }

        return s;
    }


    public static String firstWord(String s) {
        if (s == null) return "";
        String[] parts = s.trim().split("\\s+");
        return parts.length > 0 ? parts[0] : "";
    }

    private void syncHeaderLabelFromState() {
        String systemName = state.getSystemName();
        headerLabel.setText(systemName != null ? systemName : "");
    }

    private void updateHeaderLabel() {
        if (!headerLabel.hasFocus()) {
            syncHeaderLabelFromState();
        }

        StringBuilder sb = new StringBuilder();

        if (state.getTotalBodies() != null) {
            int scanned = state.getBodies().size();
            sb.append("  |  Bodies: ").append(scanned)
              .append('/').append(state.getTotalBodies());

            if (state.getFssProgress() != null) {
                sb.append("  (")
                  .append(Math.round(state.getFssProgress() * 100.0))
                  .append("%)");
            }
        }

        if (state.getNonBodyCount() != null) {
            sb.append("  |  Non-bodies: ").append(state.getNonBodyCount());
        }

        headerSummaryLabel.setText(sb.toString());
    }
    public static boolean bodyIssues = false;
    private void persistIfPossible() {
        if (state.getSystemName() == null
                || state.getSystemAddress() == 0L
                || state.getBodies().isEmpty()) {
            return;
        }

        boolean hasAnyRealBodies = false;

        for (BodyInfo x : state.getBodies().values()) {
            if (x == null) {
                continue;
            }

            // Temp bodies created before we learn BodyID are allowed.
            if (x.getBodyId() >= 0) {
                hasAnyRealBodies = true;
            }
        }

        if (!hasAnyRealBodies) {
            return;
        }

        SystemCache.getInstance().storeSystem(state);
    }

    // ---------------------------------------------------------------------
    // Exobiology expand/collapse (Bio column body rows)
    // ---------------------------------------------------------------------

    private Set<Integer> collectExpandableBioBodyIds() {
        Set<Integer> ids = new HashSet<>();
        Map<Integer, BodyInfo> bodies = state.getBodies();
        if (bodies == null || bodies.isEmpty()) {
            return ids;
        }
        for (BodyInfo b : bodies.values()) {
            if (b != null && BioTableBuilder.hasExpandableBioDetails(b)) {
                ids.add(Integer.valueOf(b.getBodyId()));
            }
        }
        return ids;
    }

    /**
     * After default "all bio collapsed" seed, expand the currently targeted body when the preference is on.
     */
    private void reconcileAutoExpandBioForCurrentTargetBody() {
        if (!OverlayPreferences.isAutoExpandBioOnTargetedBody() || targetBodyId == null) {
            return;
        }
        Map<Integer, BodyInfo> bodies = state.getBodies();
        BodyInfo b = bodies != null ? bodies.get(targetBodyId) : null;
        if (b == null || !BioTableBuilder.hasExpandableBioDetails(b)) {
            return;
        }
        Integer key = Integer.valueOf(targetBodyId.intValue());
        if (!bioDetailsCollapsedBodyIds.contains(key)) {
            return;
        }
        bioDetailsCollapsedBodyIds.remove(key);
        bioAutoExpandedForTargetBodyId = targetBodyId;
    }

    /**
     * Expand/collapse bio details from targeting changes. Only collapses a body that was opened by auto-expand.
     *
     * @return true if {@link #bioDetailsCollapsedBodyIds} changed
     */
    private boolean applyBioExpandCollapseForTargetChange(Integer previousTarget, Integer newTarget) {
        if (!OverlayPreferences.isAutoExpandBioOnTargetedBody()) {
            bioAutoExpandedForTargetBodyId = null;
            return false;
        }

        boolean mutated = false;
        Map<Integer, BodyInfo> bodies = state.getBodies();

        if (previousTarget != null
                && !Objects.equals(previousTarget, newTarget)
                && Objects.equals(bioAutoExpandedForTargetBodyId, previousTarget)) {
            BodyInfo pb = bodies != null ? bodies.get(previousTarget) : null;
            if (pb != null && BioTableBuilder.hasExpandableBioDetails(pb)) {
                bioDetailsCollapsedBodyIds.add(Integer.valueOf(previousTarget.intValue()));
                mutated = true;
            }
            bioAutoExpandedForTargetBodyId = null;
        }

        if (newTarget == null) {
            if (bioAutoExpandedForTargetBodyId != null) {
                BodyInfo ab = bodies != null ? bodies.get(bioAutoExpandedForTargetBodyId) : null;
                if (ab != null && BioTableBuilder.hasExpandableBioDetails(ab)) {
                    bioDetailsCollapsedBodyIds.add(Integer.valueOf(bioAutoExpandedForTargetBodyId.intValue()));
                    mutated = true;
                }
                bioAutoExpandedForTargetBodyId = null;
            }
            return mutated;
        }

        if (!Objects.equals(newTarget, previousTarget)) {
            BodyInfo nb = bodies != null ? bodies.get(newTarget) : null;
            if (nb != null && BioTableBuilder.hasExpandableBioDetails(nb)) {
                Integer nk = Integer.valueOf(newTarget.intValue());
                if (bioDetailsCollapsedBodyIds.contains(nk)) {
                    bioDetailsCollapsedBodyIds.remove(nk);
                    bioAutoExpandedForTargetBodyId = newTarget;
                    mutated = true;
                } else {
                    bioAutoExpandedForTargetBodyId = null;
                }
            }
        }

        return mutated;
    }

    /** True if exobiology lines for this body are shown (includes pass-through hover latch on −/+). */
    private boolean isBioDetailRowsVisibleForBodyId(int bodyId) {
        Integer key = Integer.valueOf(bodyId);
        if (!bioDetailsCollapsedBodyIds.contains(key)) {
            return true;
        }
        return passthroughHoverExpandBodyIds.contains(key);
    }

    private boolean areAnyExpandableBioDetailsVisible() {
        for (Integer id : collectExpandableBioBodyIds()) {
            if (isBioDetailRowsVisibleForBodyId(id.intValue())) {
                return true;
            }
        }
        return false;
    }

    private void toggleCollapseAllExpandableBioDetails() {
        Set<Integer> ex = collectExpandableBioBodyIds();
        if (ex.isEmpty()) {
            return;
        }
        boolean anyVisible = areAnyExpandableBioDetailsVisible();
        cancelBioExpandDelayedOpenPending();
        cancelBioHeaderAllDwellPending();
        passthroughHoverExpandBodyIds.clear();
        bioColumnHeaderExpandCueHover = false;
        bioAutoExpandedForTargetBodyId = null;
        if (anyVisible) {
            bioDetailsCollapsedBodyIds.addAll(ex);
        } else {
            bioDetailsCollapsedBodyIds.removeAll(ex);
        }
        rebuildTable();
    }

    private TableCellRenderer createBioColumnHeaderRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                JLabel lab = (JLabel) super.getTableCellRendererComponent(tbl, value, false, false, row, column);
                boolean transparent = OverlayPreferences.overlayChromeRequestsTransparency();
                lab.setOpaque(!transparent);
                lab.setBackground(transparent ? EdoUi.Internal.TRANSPARENT : EdoUi.User.BACKGROUND);
                lab.setForeground(EdoUi.User.MAIN_TEXT);
                lab.setFont(uiFont.deriveFont(Font.BOLD));
                lab.setHorizontalAlignment(SwingConstants.LEFT);
                Set<Integer> ex = collectExpandableBioBodyIds();
                if (!ex.isEmpty()) {
                    Icon cue = new ExpandCueIcon(
                            areAnyExpandableBioDetailsVisible() || bioColumnHeaderExpandCueHover, bioExpandCuePx);
                    lab.setIcon(new FixedWidthIcon(cue, bioColumnBioLeadingSlotWidthPx()));
                    lab.setText(value != null ? String.valueOf(value) : "");
                    lab.setHorizontalTextPosition(SwingConstants.RIGHT);
                    lab.setIconTextGap(4);
                } else {
                    lab.setIcon(null);
                    lab.setText(value != null ? String.valueOf(value) : "");
                }
                return lab;
            }
        };
    }

    private static Integer bodyBlockIdForTableRow(SystemBodiesTableModel model, int row) {
        if (row < 0 || row >= model.getRowCount()) {
            return null;
        }
        Row r = model.getRowAt(row);
        if (r == null) {
            return null;
        }
        if (!r.detail) {
            return r.body != null ? Integer.valueOf(r.body.getBodyId()) : null;
        }
        return Integer.valueOf(r.parentId);
    }

    private void tryToggleBioExpandAt(Point p) {
        int row = table.rowAtPoint(p);
        int col = table.columnAtPoint(p);
        if (row < 0 || col != 2) {
            return;
        }
        Row r = tableModel.getRowAt(row);
        if (r == null || r.detail || r.body == null) {
            return;
        }
        if (!BioTableBuilder.hasExpandableBioDetails(r.body)) {
            return;
        }
        Rectangle cell = table.getCellRect(row, col, false);
        int relX = p.x - cell.x;
        int toggleW = bioExpandCuePx + EXPAND_HIT_SLOP_PX * 2;
        if (relX < 0 || relX > toggleW) {
            return;
        }
        int bid = r.body.getBodyId();
        Integer key = Integer.valueOf(bid);
        if (bioDetailsCollapsedBodyIds.contains(key)) {
            bioDetailsCollapsedBodyIds.remove(key);
        } else {
            bioDetailsCollapsedBodyIds.add(key);
        }
        passthroughHoverExpandBodyIds.remove(key);
        if (bioAutoExpandedForTargetBodyId != null && bioAutoExpandedForTargetBodyId.intValue() == bid) {
            bioAutoExpandedForTargetBodyId = null;
        }
        rebuildTable();
    }

    private void cancelBioExpandDelayedOpenPending() {
        bioExpandCueDelayedOpenPendingBodyId = null;
        bioExpandCueDelayedPendingClose = false;
        bioExpandCueDwellArmBodyUntilCueExit = null;
        if (bioExpandCueDelayedOpenTimer != null) {
            bioExpandCueDelayedOpenTimer.stop();
        }
    }

    private void scheduleBioExpandDelayedAction(Integer bodyId, boolean closeAfterDwell) {
        if (bodyId == null) {
            return;
        }
        // Do not restart every poll tick — that resets the delay and the timer never fires.
        if (Objects.equals(bioExpandCueDelayedOpenPendingBodyId, bodyId)
                && bioExpandCueDelayedPendingClose == closeAfterDwell
                && bioExpandCueDelayedOpenTimer != null
                && bioExpandCueDelayedOpenTimer.isRunning()) {
            return;
        }
        bioExpandCueDelayedOpenPendingBodyId = bodyId;
        bioExpandCueDelayedPendingClose = closeAfterDwell;
        bioExpandCueDelayedOpenTimer.restart();
    }

    private void commitBioExpandCueDelayedAction() {
        Integer pending = bioExpandCueDelayedOpenPendingBodyId;
        boolean closing = bioExpandCueDelayedPendingClose;
        bioExpandCueDelayedOpenPendingBodyId = null;
        bioExpandCueDelayedPendingClose = false;
        if (pending == null || !OverlayPreferences.isOverlayMousePassThroughToGame()) {
            return;
        }
        bioExpandCueDwellArmBodyUntilCueExit = pending;
        if (closing) {
            boolean changed = passthroughHoverExpandBodyIds.remove(pending);
            if (bioDetailsCollapsedBodyIds.add(pending)) {
                changed = true;
            }
            if (Objects.equals(bioAutoExpandedForTargetBodyId, pending)) {
                bioAutoExpandedForTargetBodyId = null;
            }
            if (changed) {
                rebuildTable();
            }
        } else if (passthroughHoverExpandBodyIds.add(pending)) {
            rebuildTable();
        }
    }

    private boolean isPointerOverBioExpandCue(Point tableLocal, int row) {
        if (row < 0 || table.columnAtPoint(tableLocal) != 2) {
            return false;
        }
        Row r = tableModel.getRowAt(row);
        if (r == null || r.detail || r.body == null) {
            return false;
        }
        if (!BioTableBuilder.hasExpandableBioDetails(r.body)) {
            return false;
        }
        Rectangle cell = table.getCellRect(row, 2, false);
        int relX = tableLocal.x - cell.x;
        // Match the full leading icon slot (expand + leaf + optional bag), not just the small box.
        int toggleW = bioColumnBioLeadingSlotWidthPx() + EXPAND_HIT_SLOP_PX * 2;
        return relX >= 0 && relX <= toggleW;
    }

    /** Hit test for Bio column header −/+ (expand/collapse all), using screen coordinates. */
    private boolean isGlobalPointerOverBioHeaderExpandCue(Point screen) {
        if (screen == null) {
            return false;
        }
        JTableHeader hdr = table.getTableHeader();
        if (hdr == null || !hdr.isShowing()) {
            return false;
        }
        try {
            Point hpt = new Point(screen);
            SwingUtilities.convertPointFromScreen(hpt, hdr);
            if (!hdr.contains(hpt)) {
                return false;
            }
            int c = hdr.columnAtPoint(hpt);
            if (c != 2) {
                return false;
            }
            Rectangle rr = hdr.getHeaderRect(2);
            int rx = hpt.x - rr.x;
            int hw = bioColumnBioLeadingSlotWidthPx() + EXPAND_HIT_SLOP_PX * 2;
            return rx >= 0 && rx <= hw;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private void cancelBioHeaderAllDwellPending() {
        bioHeaderAllDwellPendingCollapse = false;
        bioHeaderAllDwellArmUntilCueExit = false;
        if (bioHeaderAllDwellTimer != null) {
            bioHeaderAllDwellTimer.stop();
        }
    }

    private void scheduleBioHeaderAllDwell(boolean closeAfterDwell) {
        if (bioHeaderAllDwellTimer == null) {
            return;
        }
        if (bioHeaderAllDwellPendingCollapse == closeAfterDwell && bioHeaderAllDwellTimer.isRunning()) {
            return;
        }
        bioHeaderAllDwellPendingCollapse = closeAfterDwell;
        bioHeaderAllDwellTimer.restart();
    }

    private void commitBioHeaderAllDwellAction() {
        boolean closing = bioHeaderAllDwellPendingCollapse;
        bioHeaderAllDwellPendingCollapse = false;
        if (!OverlayPreferences.isOverlayMousePassThroughToGame()) {
            return;
        }
        Set<Integer> ex = collectExpandableBioBodyIds();
        if (ex.isEmpty()) {
            return;
        }
        boolean anyVisible = areAnyExpandableBioDetailsVisible();
        if (closing && !anyVisible) {
            return;
        }
        if (!closing && anyVisible) {
            return;
        }
        bioHeaderAllDwellArmUntilCueExit = true;
        cancelBioExpandDelayedOpenPending();
        passthroughHoverExpandBodyIds.clear();
        bioAutoExpandedForTargetBodyId = null;
        if (anyVisible) {
            bioDetailsCollapsedBodyIds.addAll(ex);
        } else {
            bioDetailsCollapsedBodyIds.removeAll(ex);
        }
        rebuildTable();
    }

    /**
     * Pass-through: Bio column header −/+ expand cue hover from global pointer (Swing motion is unreliable).
     */
    private void syncBioColumnHeaderExpandHoverFromScreen(Point screen, boolean allowHeaderHitTest) {
        boolean want = allowHeaderHitTest && isGlobalPointerOverBioHeaderExpandCue(screen);
        if (bioColumnHeaderExpandCueHover != want) {
            bioColumnHeaderExpandCueHover = want;
            SwingUtilities.invokeLater(() -> {
                JTableHeader h = table.getTableHeader();
                if (h != null) {
                    h.repaint();
                }
            });
        }
    }

    /**
     * Global mouse poll for pass-through (see {@link MiningTabPanel}'s scatter plot). Updates Bio header −/+ hover;
     * body dwell on + latches open; dwell on − collapses (removes latch and ensures collapsed set). Also updates
     * table sort toggle (rocket / star / $) hover when pass-through is on (Swing does not receive motion).
     */
    private void pollBioExpandCueHoverFromGlobalMouse() {
        if (!table.isShowing()) {
            syncBioColumnHeaderExpandHoverFromScreen(null, false);
            syncDistModeToggleHoverFromScreen(null);
            syncMapToolbarHoverFromScreen(null);
            cancelBioExpandDelayedOpenPending();
            cancelBioHeaderAllDwellPending();
            return;
        }
        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi == null) {
            syncBioColumnHeaderExpandHoverFromScreen(null, false);
            syncDistModeToggleHoverFromScreen(null);
            syncMapToolbarHoverFromScreen(null);
            cancelBioHeaderAllDwellPending();
            return;
        }
        Point screen = pi.getLocation();
        if (!OverlayPreferences.isOverlayMousePassThroughToGame()) {
            syncBioColumnHeaderExpandHoverFromScreen(null, false);
            syncDistModeToggleHoverFromScreen(null);
            syncMapToolbarHoverFromScreen(null);
            cancelBioExpandDelayedOpenPending();
            cancelBioHeaderAllDwellPending();
            return;
        }
        syncDistModeToggleHoverFromScreen(screen);
        syncMapToolbarHoverFromScreen(screen);
        syncMapViewTiltPassThroughFromScreen(screen);
        syncBioColumnHeaderExpandHoverFromScreen(screen, true);

        boolean overHeaderCue = isGlobalPointerOverBioHeaderExpandCue(screen);
        if (overHeaderCue) {
            cancelBioExpandDelayedOpenPending();
            Set<Integer> hex = collectExpandableBioBodyIds();
            if (hex.isEmpty()) {
                cancelBioHeaderAllDwellPending();
            } else if (bioHeaderAllDwellArmUntilCueExit) {
                return;
            } else if (areAnyExpandableBioDetailsVisible()) {
                scheduleBioHeaderAllDwell(true);
            } else {
                scheduleBioHeaderAllDwell(false);
            }
            return;
        }
        cancelBioHeaderAllDwellPending();

        Point local = new Point(screen);
        try {
            SwingUtilities.convertPointFromScreen(local, table);
        } catch (IllegalStateException ignored) {
            cancelBioHeaderAllDwellPending();
            return;
        }
        if (!table.contains(local)) {
            cancelBioExpandDelayedOpenPending();
            // Do not clear latched peek — pointer often leaves the table (game UI, title bar) while reading.
            return;
        }

        int row = table.rowAtPoint(local);
        Integer here = row >= 0 ? bodyBlockIdForTableRow(tableModel, row) : null;
        if (row < 0 || here == null) {
            cancelBioExpandDelayedOpenPending();
            return;
        }

        boolean overCue = isPointerOverBioExpandCue(local, row);

        if (bioExpandCueDwellArmBodyUntilCueExit != null && overCue
                && !Objects.equals(here, bioExpandCueDwellArmBodyUntilCueExit)) {
            bioExpandCueDwellArmBodyUntilCueExit = null;
        }

        if (overCue) {
            if (bioExpandCueDwellArmBodyUntilCueExit != null
                    && Objects.equals(here, bioExpandCueDwellArmBodyUntilCueExit)) {
                return;
            }
            boolean detailsVisible = isBioDetailRowsVisibleForBodyId(here.intValue());
            if (detailsVisible) {
                scheduleBioExpandDelayedAction(here, true);
            } else {
                scheduleBioExpandDelayedAction(here, false);
            }
        } else {
            cancelBioExpandDelayedOpenPending();
        }
    }

    /**
     * Pass-through: drive table-sort toggle hover chrome from {@link MouseInfo} (Swing hover is unreliable).
     * Activation on dwell uses {@link HoverClickPoller} on each sort button.
     *
     * @param screen global pointer, or {@code null} to clear programmatic hover
     */
    private void syncDistModeToggleHoverFromScreen(Point screen) {
        if (distFromShipButton == null || distFromStarButton == null || distByValueButton == null) {
            return;
        }
        if (screen == null || !OverlayPreferences.isOverlayMousePassThroughToGame()) {
            clearDistModeToggleProgrammaticHover();
            return;
        }
        if (!distFromShipButton.isShowing() || !distFromStarButton.isShowing() || !distByValueButton.isShowing()) {
            clearDistModeToggleProgrammaticHover();
            return;
        }
        boolean overShip = isGlobalPointerOverComponent(screen, distFromShipButton);
        boolean overStar = !overShip && isGlobalPointerOverComponent(screen, distFromStarButton);
        boolean overValue = !overShip && !overStar && isGlobalPointerOverComponent(screen, distByValueButton);
        if (overShip == distFromShipHovered && overStar == distFromStarHovered && overValue == distByValueHovered) {
            return;
        }
        distFromShipHovered = overShip;
        distFromStarHovered = overStar;
        distByValueHovered = overValue;
        updateDistModeToggleAppearance();
    }

    private void clearDistModeToggleProgrammaticHover() {
        if (!distFromShipHovered && !distFromStarHovered && !distByValueHovered) {
            return;
        }
        distFromShipHovered = false;
        distFromStarHovered = false;
        distByValueHovered = false;
        updateDistModeToggleAppearance();
    }

    /**
     * Pass-through: map toolbar collapse/expand and view-tilt cluster hover from {@link MouseInfo}.
     */
    private void syncMapToolbarHoverFromScreen(Point screen) {
        if (screen == null || !OverlayPreferences.isOverlayMousePassThroughToGame()) {
            clearMapToolbarProgrammaticHover();
            endMapViewTiltPassThroughAdjusting();
            return;
        }
        boolean overCollapse = systemPlanMapCollapseButton != null && systemPlanMapCollapseButton.isShowing()
                && isGlobalPointerOverComponent(screen, systemPlanMapCollapseButton);
        boolean overExpand = !overCollapse && systemPlanMapExpandButton != null && systemPlanMapExpandButton.isShowing()
                && isGlobalPointerOverComponent(screen, systemPlanMapExpandButton);
        boolean overTilt = mapViewTiltCluster != null && mapViewTiltCluster.isShowing()
                && isGlobalPointerOverComponent(screen, mapViewTiltCluster);
        if (overCollapse == systemPlanMapCollapseHovered && overExpand == systemPlanMapExpandHovered
                && overTilt == mapViewTiltHovered) {
            return;
        }
        systemPlanMapCollapseHovered = overCollapse;
        systemPlanMapExpandHovered = overExpand;
        mapViewTiltHovered = overTilt;
        updateMapToolbarHoverAppearance();
    }

    private void clearMapToolbarProgrammaticHover() {
        if (!systemPlanMapCollapseHovered && !systemPlanMapExpandHovered && !mapViewTiltHovered) {
            return;
        }
        systemPlanMapCollapseHovered = false;
        systemPlanMapExpandHovered = false;
        mapViewTiltHovered = false;
        updateMapToolbarHoverAppearance();
    }

    /**
     * Pass-through: horizontal pointer position on the tilt slider sets view tilt (clicks do not reach Swing).
     */
    private void syncMapViewTiltPassThroughFromScreen(Point screen) {
        if (!OverlayPreferences.isOverlayMousePassThroughToGame() || mapViewTiltSlider == null) {
            endMapViewTiltPassThroughAdjusting();
            return;
        }
        boolean overSlider = screen != null && mapViewTiltSlider.isShowing()
                && isGlobalPointerOverComponent(screen, mapViewTiltSlider);
        if (!overSlider) {
            endMapViewTiltPassThroughAdjusting();
            return;
        }
        int degrees = mapViewTiltDegreesForPassThroughPointer(screen, mapViewTiltSlider);
        if (!mapViewTiltPassThroughAdjusting) {
            mapViewTiltPassThroughAdjusting = true;
            mapViewTiltSlider.setValueIsAdjusting(true);
        }
        if (degrees != mapViewTiltSlider.getValue()) {
            mapViewTiltSlider.setValue(degrees);
        }
    }

    private void endMapViewTiltPassThroughAdjusting() {
        if (!mapViewTiltPassThroughAdjusting) {
            return;
        }
        mapViewTiltPassThroughAdjusting = false;
        if (mapViewTiltSlider != null) {
            mapViewTiltSlider.setValueIsAdjusting(false);
        }
        onMapViewTiltSliderChanged();
    }

    private static int mapViewTiltDegreesForPassThroughPointer(Point screen, JSlider slider) {
        if (screen == null || slider == null) {
            return 0;
        }
        Point local = new Point(screen);
        try {
            SwingUtilities.convertPointFromScreen(local, slider);
        } catch (IllegalStateException ignored) {
            return slider.getValue();
        }
        int w = slider.getWidth();
        if (w <= 1) {
            return slider.getValue();
        }
        double frac = local.x / (double) (w - 1);
        frac = Math.max(0.0, Math.min(1.0, frac));
        int min = slider.getMinimum();
        int max = slider.getMaximum();
        return min + (int) Math.round(frac * (max - min));
    }

    /**
     * Pass-through wheel over the view-tilt cluster nudges tilt before map zoom / table scroll.
     */
    private boolean applyPassThroughMapViewTiltWheelIfHit(int screenX, int screenY, int wheelRotation) {
        if (wheelRotation == 0 || !OverlayPreferences.isOverlayMousePassThroughToGame()
                || mapViewTiltSlider == null || mapViewTiltCluster == null || !mapViewTiltCluster.isShowing()) {
            return false;
        }
        Point screen = new Point(screenX, screenY);
        if (!isGlobalPointerOverComponent(screen, mapViewTiltCluster)) {
            return false;
        }
        int min = mapViewTiltSlider.getMinimum();
        int max = mapViewTiltSlider.getMaximum();
        int cur = mapViewTiltSlider.getValue();
        int step = wheelRotation > 0 ? -2 : 2;
        int next = Math.max(min, Math.min(max, cur + step));
        if (next == cur) {
            return true;
        }
        mapViewTiltSlider.setValue(next);
        return true;
    }

    private void installMapToolbarButtonHoverListeners(JButton button, Supplier<Boolean> hoveredGetter,
            Consumer<Boolean> hoveredSetter) {
        if (button == null || hoveredGetter == null || hoveredSetter == null) {
            return;
        }
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (OverlayPreferences.isOverlayMousePassThroughToGame()) {
                    return;
                }
                hoveredSetter.accept(true);
                updateMapToolbarHoverAppearance();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (OverlayPreferences.isOverlayMousePassThroughToGame()) {
                    return;
                }
                hoveredSetter.accept(false);
                updateMapToolbarHoverAppearance();
            }
        });
    }

    private void updateMapToolbarHoverAppearance() {
        if (systemPlanMapCollapseButton != null) {
            applyMapToolbarIconButtonHoverChrome(systemPlanMapCollapseButton, systemPlanMapCollapseHovered);
            systemPlanMapCollapseButton.repaint();
        }
        if (systemPlanMapExpandButton != null) {
            applyMapToolbarIconButtonHoverChrome(systemPlanMapExpandButton, systemPlanMapExpandHovered);
            systemPlanMapExpandButton.repaint();
        }
        updateMapViewTiltHoverAppearance();
    }

    private static void applyMapToolbarIconButtonHoverChrome(JButton b, boolean hovered) {
        Color hoverLine = EdoUi.Internal.MAIN_TEXT_ALPHA_200;
        Color hoverFill = EdoUi.Internal.MAIN_TEXT_ALPHA_40;
        if (hovered) {
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(hoverLine, 1),
                    new EmptyBorder(2, 4, 2, 4)));
            b.setOpaque(true);
            b.setBackground(hoverFill);
        } else {
            b.setBorder(new EmptyBorder(3, 5, 3, 5));
            b.setOpaque(false);
        }
        b.setCursor(Cursor.getPredefinedCursor(hovered ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private void updateMapViewTiltHoverAppearance() {
        if (mapViewTiltCluster == null) {
            return;
        }
        Color labelFg = mapViewTiltHovered ? EdoUi.Internal.MAIN_TEXT_ALPHA_220 : EdoUi.User.MAIN_TEXT;
        if (mapViewTiltLabel != null) {
            mapViewTiltLabel.setForeground(labelFg);
        }
        if (mapViewTiltValueLabel != null) {
            mapViewTiltValueLabel.setForeground(labelFg);
        }
        if (mapViewTiltHovered) {
            mapViewTiltCluster.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(EdoUi.Internal.MAIN_TEXT_ALPHA_200, 1),
                    new EmptyBorder(2, 4, 2, 4)));
            mapViewTiltCluster.setOpaque(true);
            mapViewTiltCluster.setBackground(EdoUi.Internal.MAIN_TEXT_ALPHA_40);
        } else {
            mapViewTiltCluster.setBorder(new EmptyBorder(3, 5, 3, 5));
            mapViewTiltCluster.setOpaque(false);
        }
        Cursor c = Cursor.getPredefinedCursor(mapViewTiltHovered ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR);
        mapViewTiltCluster.setCursor(c);
        if (mapViewTiltSlider != null) {
            mapViewTiltSlider.setCursor(c);
        }
        mapViewTiltCluster.repaint();
    }

    private static boolean isGlobalPointerOverComponent(Point screen, Component comp) {
        if (screen == null || comp == null || !comp.isShowing()) {
            return false;
        }
        Point local = new Point(screen);
        try {
            SwingUtilities.convertPointFromScreen(local, comp);
        } catch (IllegalStateException ignored) {
            return false;
        }
        return comp.contains(local);
    }

    /** Invisible fixed width/height for spacing between stacked icons. */
    private static final class SpacerIcon implements Icon {
        private final int width;
        private final int height;

        SpacerIcon(int width, int height) {
            this.width = Math.max(0, width);
            this.height = Math.max(1, height);
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            // intentional blank
        }
    }

    /**
     * Disclosure control: same grey as unscanned exobiology names ({@link EdoUi.Internal#GRAY_180}).
     */
    private static final class ExpandCueIcon implements Icon {
        private static final Color CUE = EdoUi.Internal.GRAY_180;

        private final boolean expanded;
        private final int size;

        ExpandCueIcon(boolean expanded, int size) {
            this.expanded = expanded;
            this.size = Math.max(9, size);
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

                g2.setColor(CUE);
                g2.drawRect(x, y, size - 1, size - 1);

                int midX = x + size / 2;
                int midY = y + size / 2;
                // Half-length of each bar (classic ~9px box uses ~2px from center)
                int arm = Math.max(2, (size - 4) / 2 - 1);

                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
                g2.drawLine(midX - arm, midY, midX + arm, midY);
                if (!expanded) {
                    g2.drawLine(midX, midY - arm, midX, midY + arm);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    /** Max height of Bio column leading icons (for blank spacer rows). */
    private int bioColumnLeadingStackHeightPx() {
        int h = Math.max(bioExpandCuePx, bioLeafIcon != null ? bioLeafIcon.getIconHeight() : bioExpandCuePx);
        if (bioGeoIcon != null) {
            h = Math.max(h, bioGeoIcon.getIconHeight());
        }
        if (bioDollarIcon != null) {
            h = Math.max(h, bioDollarIcon.getIconHeight());
        }
        return h;
    }

    /**
     * Width reserved left of Bio column text: full expand + leaf + money-bag stack so rows align with or without the bag.
     */
    private int bioColumnBioLeadingSlotWidthPx() {
        int stackH = bioColumnLeadingStackHeightPx();
        HorizontalIconStack leafMoneyMax = new HorizontalIconStack(-4);
        leafMoneyMax.add(bioLeafIcon);
        leafMoneyMax.add(bioDollarIcon);
        HorizontalIconStack maxStack = new HorizontalIconStack(0);
        maxStack.add(new ExpandCueIcon(false, bioExpandCuePx));
        maxStack.add(new SpacerIcon(bioExpandToLeafGapPx, stackH));
        if (leafMoneyMax.getIconWidth() > 0) {
            maxStack.add(leafMoneyMax);
        } else if (bioLeafIcon != null) {
            maxStack.add(bioLeafIcon);
        }
        int w = maxStack.getIconWidth();
        if (bioGeoIcon != null) {
            w = Math.max(w, bioGeoIcon.getIconWidth());
        }
        return w;
    }

    // ---------------------------------------------------------------------
    // Table model
    // ---------------------------------------------------------------------

    private final class BioCellRenderer extends DefaultTableCellRenderer {
        BioCellRenderer() {
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.LEFT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {

            JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            c.setOpaque(false);
            c.setBackground(EdoUi.Internal.TRANSPARENT);

            Row r = tableModel.getRowAt(row);
            boolean isDetailRow = (r != null && r.detail);

            // Preserve the existing coloring semantics used by the default renderer.
            if (isSelected) {
                c.setForeground(Color.BLACK);
            } else if (isDetailRow) {
                if (r.isRingDetail()) {
                    c.setForeground(EdoUi.Internal.GRAY_180);
                } else {
                    int samples = r.getBioSampleCount();
                    if (samples >= 3) {
                        c.setForeground(EdoUi.User.PRIMARY_HIGHLIGHT);
                    } else if (samples > 0) {
                        c.setForeground(EdoUi.User.SECONDARY_HIGHLIGHT);
                    } else {
                        c.setForeground(EdoUi.Internal.GRAY_180);
                    }
                }
            } else {
                c.setForeground(EdoUi.User.MAIN_TEXT);
            }

            if (r == null) {
                return c;
            }

            // Detail rows: ring geo icon; non-ring high-value lines get money bag only (leaf stays on summary row).
            if (r.detail) {
                Icon icon = null;
                if (!r.destinationRow) {
                    if (r.isRingDetail()) {
                        icon = bioGeoIcon;
                    } else if (r.bioText != null && !r.bioText.isBlank()) {
                        Long rowCr = r.bioEstimatedPayoutCredits;
                        if (rowCr != null
                                && rowCr.longValue()
                                        >= OverlayPreferences.getMiningExobiologyValuableBioThresholdCredits()) {
                            icon = bioDollarIcon;
                        }
                    }
                }
                int slotW = bioColumnBioLeadingSlotWidthPx();
                int stackH = bioColumnLeadingStackHeightPx();
                Icon leading = icon != null ? new FixedWidthIcon(icon, slotW, true) : new SpacerIcon(slotW, stackH);
                c.setIcon(leading);
                c.setText(value != null ? String.valueOf(value) : "");
                c.setHorizontalAlignment(SwingConstants.LEFT);
                c.setHorizontalTextPosition(SwingConstants.RIGHT);
                c.setIconTextGap(4);
                return c;
            }

            BodyInfo b = r.body;
            if (b == null) {
                c.setIcon(null);
                c.setText("");
                return c;
            }

            boolean hasBio = !BioTableBuilder.spanshExobiologyExclusionActive(b) && b.hasBio();
            boolean showBioExpand = BioTableBuilder.hasExpandableBioDetails(b);
            boolean bioLinesVisible = isBioDetailRowsVisibleForBodyId(b.getBodyId());

            c.setHorizontalAlignment(SwingConstants.LEFT);

            // Remaining payout range + (Xm scanned) + optional FSS signal count (see
            // {@link BioTableBuilder#formatBodyBioColumnText}); fallback if stale row model.
            String text = "";
            if (hasBio) {
                String cell = BioTableBuilder.formatBodyBioColumnText(b);
                if (cell != null && !cell.isEmpty()) {
                    text = cell;
                }
            }
            if (text.isEmpty()) {
                String valueOrRange = r.getBioHeaderSummary();
                if (valueOrRange == null || valueOrRange.isEmpty()) {
                    valueOrRange = formatBioHeaderValueOrRange(b);
                }
                if (valueOrRange != null && !valueOrRange.isEmpty()) {
                    text = valueOrRange;
                }
            }

            long bioValuableThresholdCr = OverlayPreferences.getMiningExobiologyValuableBioThresholdCredits();
            String displayText = text;
            if (!isSelected && hasBio && !text.isEmpty()) {
                String html = BioTableBuilder.formatBodyBioColumnHtml(b, bioValuableThresholdCr);
                if (html != null) {
                    displayText = html;
                }
            }

            int slotW = bioColumnBioLeadingSlotWidthPx();
            Icon composite = null;
            if (showBioExpand) {
                int stackH = Math.max(bioExpandCuePx, bioLeafIcon != null ? bioLeafIcon.getIconHeight() : bioExpandCuePx);
                HorizontalIconStack leafMoney = new HorizontalIconStack(-4);
                leafMoney.add(bioLeafIcon);
                long rowCr = BioTableBuilder.getMaxBioEstimatedCredits(b);
                if (rowCr != Long.MIN_VALUE
                        && rowCr >= bioValuableThresholdCr) {
                    leafMoney.add(bioDollarIcon);
                }
                HorizontalIconStack stack = new HorizontalIconStack(0);
                stack.add(new ExpandCueIcon(bioLinesVisible, bioExpandCuePx));
                stack.add(new SpacerIcon(bioExpandToLeafGapPx, stackH));
                stack.add(leafMoney.getIconWidth() > 0 ? leafMoney : bioLeafIcon);
                if (stack.getIconWidth() > 0) {
                    composite = new FixedWidthIcon(stack, slotW);
                }
            } else if (!text.isEmpty()) {
                composite = new SpacerIcon(slotW, bioColumnLeadingStackHeightPx());
            }

            c.setIcon(composite);
            c.setText(displayText);
            c.setHorizontalTextPosition(SwingConstants.RIGHT);
            c.setIconTextGap(4);
            return c;
        }
    }

    private static final class HorizontalIconStack implements Icon {
        private final java.util.List<Icon> icons = new java.util.ArrayList<>();
        private final int gap;

        HorizontalIconStack(int gap) {
            this.gap = gap;
        }

        void add(Icon icon) {
            if (icon != null) {
                icons.add(icon);
            }
        }

        @Override
        public int getIconWidth() {
            if (icons.isEmpty()) {
                return 0;
            }
            int w = 0;
            for (int i = 0; i < icons.size(); i++) {
                w += icons.get(i).getIconWidth();
                if (i < icons.size() - 1) {
                    w += gap;
                }
            }
            return w;
        }

        @Override
        public int getIconHeight() {
            int h = 0;
            for (Icon ic : icons) {
                h = Math.max(h, ic.getIconHeight());
            }
            return h;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            int xx = x;
            int h = getIconHeight();
            for (int i = 0; i < icons.size(); i++) {
                Icon ic = icons.get(i);
                int yy = y + Math.max(0, (h - ic.getIconHeight()) / 2);
                ic.paintIcon(c, g, xx, yy);
                xx += ic.getIconWidth();
                if (i < icons.size() - 1) {
                    xx += gap;
                }
            }
        }
    }

    private static final class FixedWidthIcon implements Icon {
        private final Icon delegate;
        private final int width;
        /** If true, paint the delegate flush right in {@link #width} (detail-row bag/geo next to text). */
        private final boolean alignTrailing;

        FixedWidthIcon(Icon delegate, int width) {
            this(delegate, width, false);
        }

        FixedWidthIcon(Icon delegate, int width, boolean alignTrailing) {
            this.delegate = delegate;
            this.width = Math.max(0, width);
            this.alignTrailing = alignTrailing;
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return delegate != null ? delegate.getIconHeight() : 0;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (delegate == null) {
                return;
            }
            int dx = 0;
            if (alignTrailing) {
                dx = width - delegate.getIconWidth();
                if (dx < 0) {
                    dx = 0;
                }
            }
            delegate.paintIcon(c, g, x + dx, y);
        }
    }

    private static final class CachedIcon implements Icon {
        private final Icon delegate;
        private transient BufferedImage cachedImage;
        private transient int cachedW = -1;
        private transient int cachedH = -1;

        CachedIcon(Icon delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getIconWidth() {
            return delegate != null ? delegate.getIconWidth() : 0;
        }

        @Override
        public int getIconHeight() {
            return delegate != null ? delegate.getIconHeight() : 0;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (delegate == null) {
                return;
            }
            int w = Math.max(1, delegate.getIconWidth());
            int h = Math.max(1, delegate.getIconHeight());
            if (cachedImage == null || cachedW != w || cachedH != h) {
                cachedW = w;
                cachedH = h;
                cachedImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = cachedImage.createGraphics();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    delegate.paintIcon(c, g2, 0, 0);
                } finally {
                    g2.dispose();
                }
            }
            g.drawImage(cachedImage, x, y, null);
        }
    }

    private static final class DollarIcon implements Icon {
        private final int w;
        private final int h;

        DollarIcon(int w, int h) {
            this.w = w;
            this.h = h;
        }

        @Override
        public int getIconWidth() {
            return w;
        }

        @Override
        public int getIconHeight() {
            return h;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                double ix = x + 1.0;
                double iy = y + 1.0;
                double iw = Math.max(8.0, w - 2.0);
                double ih = Math.max(8.0, h - 2.0);

                Color bagFill = new Color(194, 154, 72, 235);
                Color bagDark = new Color(128, 95, 36, 220);
                Color tie = new Color(88, 62, 24, 235);

                // Main bag body.
                Path2D bag = new Path2D.Double();
                bag.moveTo(ix + iw * 0.50, iy + ih * 0.30);
                bag.curveTo(ix + iw * 0.28, iy + ih * 0.34, ix + iw * 0.18, iy + ih * 0.56, ix + iw * 0.26, iy + ih * 0.77);
                bag.curveTo(ix + iw * 0.34, iy + ih * 0.93, ix + iw * 0.66, iy + ih * 0.93, ix + iw * 0.74, iy + ih * 0.77);
                bag.curveTo(ix + iw * 0.82, iy + ih * 0.56, ix + iw * 0.72, iy + ih * 0.34, ix + iw * 0.50, iy + ih * 0.30);
                bag.closePath();
                g2.setColor(bagFill);
                g2.fill(bag);
                g2.setColor(bagDark);
                g2.setStroke(new BasicStroke(Math.max(0.8f, (float) (w * 0.06f)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(bag);

                // Inverted triangle "flap" above the tie (user requested shape cue).
                Path2D flap = new Path2D.Double();
                flap.moveTo(ix + iw * 0.35, iy + ih * 0.14);
                flap.lineTo(ix + iw * 0.65, iy + ih * 0.14);
                flap.lineTo(ix + iw * 0.50, iy + ih * 0.28);
                flap.closePath();
                g2.setColor(EdoUi.withAlpha(new Color(222, 186, 102), 230));
                g2.fill(flap);
                g2.setColor(bagDark);
                g2.draw(flap);

                // Tie band directly under the flap.
                g2.setColor(tie);
                g2.setStroke(new BasicStroke(Math.max(0.9f, (float) (w * 0.08f))));
                g2.draw(new java.awt.geom.Line2D.Double(ix + iw * 0.34, iy + ih * 0.30, ix + iw * 0.66, iy + ih * 0.30));
                g2.fill(new java.awt.geom.Ellipse2D.Double(ix + iw * 0.45, iy + ih * 0.27, iw * 0.10, ih * 0.08));

                // Visible $ mark on the body.
                g2.setColor(new Color(84, 56, 18, 235));
                Font f = c != null ? c.getFont() : new Font("Dialog", Font.BOLD, 12);
                g2.setFont(f.deriveFont(Font.BOLD, Math.max(8f, (float) (h * 0.46f))));
                FontMetrics fm = g2.getFontMetrics();
                String s = "$";
                int tx = (int) Math.round(ix + (iw - fm.stringWidth(s)) * 0.50);
                int ty = (int) Math.round(iy + ih * 0.70);
                g2.drawString(s, tx, ty);
            } finally {
                g2.dispose();
            }
        }
    }

    private static final class RingedPlanetIcon implements Icon {
        private final int w;
        private final int h;

        RingedPlanetIcon(int w, int h) {
            this.w = w;
            this.h = h;
        }

        @Override
        public int getIconWidth() {
            return w;
        }

        @Override
        public int getIconHeight() {
            return h;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                double ix = x + 1.0;
                double iy = y + 1.0;
                double iw = Math.max(8.0, w - 2.0);
                double ih = Math.max(8.0, h - 2.0);

                Color planetFill = new Color(70, 130, 210, 235);   // blue
                Color planetShadow = new Color(35, 76, 140, 210);  // darker blue edge/shade
                Color ringColor = new Color(220, 72, 72, 220);     // red
                Color ringShadow = new Color(130, 35, 35, 200);    // darker red edge

                // Shared center: keep planet centered inside the ring.
                double cx = ix + iw * 0.50;
                double cy = iy + ih * 0.50;

                // Planet body geometry (centered on ring center).
                double planetD = Math.min(iw, ih) * 0.62;
                double planetX = cx - planetD * 0.50;
                double planetY = cy - planetD * 0.50;
                java.awt.geom.Ellipse2D planetCircle = new java.awt.geom.Ellipse2D.Double(planetX, planetY, planetD, planetD);

                // Build ring as a true annulus (outer ellipse minus inner ellipse), then rotate.
                double ringW = iw * 0.98;
                double ringH = ih * 0.42;
                double ringThickness = Math.max(1.2, Math.min(iw, ih) * 0.10);
                java.awt.geom.Ellipse2D outer = new java.awt.geom.Ellipse2D.Double(
                        cx - ringW * 0.50, cy - ringH * 0.50, ringW, ringH);
                java.awt.geom.Ellipse2D inner = new java.awt.geom.Ellipse2D.Double(
                        cx - (ringW - ringThickness * 2.0) * 0.50,
                        cy - (ringH - ringThickness * 2.0) * 0.50,
                        Math.max(1.0, ringW - ringThickness * 2.0),
                        Math.max(1.0, ringH - ringThickness * 2.0));
                java.awt.geom.Area ringArea = new java.awt.geom.Area(outer);
                ringArea.subtract(new java.awt.geom.Area(inner));
                java.awt.geom.AffineTransform ringTx = java.awt.geom.AffineTransform.getRotateInstance(
                        Math.toRadians(-22), cx, cy);
                ringArea.transform(ringTx);

                // Split into back/front halves, then layer around the planet.
                java.awt.geom.Area frontHalf = new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Double(
                        cx - ringW * 0.75, cy, ringW * 1.50, ringH * 1.20));
                java.awt.geom.Area backHalf = new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Double(
                        cx - ringW * 0.75, cy - ringH * 1.20, ringW * 1.50, ringH * 1.20));
                frontHalf.transform(ringTx);
                backHalf.transform(ringTx);
                java.awt.geom.Area ringFront = new java.awt.geom.Area(ringArea);
                ringFront.intersect(frontHalf);
                java.awt.geom.Area ringBack = new java.awt.geom.Area(ringArea);
                ringBack.intersect(backHalf);

                // Back half first (behind planet).
                g2.setColor(ringShadow);
                g2.fill(ringBack);
                g2.setColor(ringColor);
                g2.setStroke(new BasicStroke(Math.max(0.55f, (float) (ringThickness * 0.16f)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(ringBack);

                // Planet body on top.
                g2.setColor(planetFill);
                g2.fill(planetCircle);
                g2.setColor(planetShadow);
                g2.setStroke(new BasicStroke(Math.max(0.8f, (float) (w * 0.05))));
                g2.draw(planetCircle);
                g2.setColor(EdoUi.withAlpha(new Color(164, 207, 255), 170));
                g2.fill(new java.awt.geom.Ellipse2D.Double(
                        planetX + planetD * 0.16, planetY + planetD * 0.14, planetD * 0.30, planetD * 0.22));

                // Front half last (in front of planet).
                g2.setColor(ringColor);
                g2.fill(ringFront);
                g2.setColor(ringShadow);
                g2.setStroke(new BasicStroke(Math.max(0.55f, (float) (ringThickness * 0.16f)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(ringFront);
            } finally {
                g2.dispose();
            }
        }
    }

    /** Small blue-green “marble” for terraformable / ELW-class valuable worlds. */
    private static final class EarthLikeBodyIcon implements Icon {
        private final int w;
        private final int h;

        EarthLikeBodyIcon(int w, int h) {
            this.w = w;
            this.h = h;
        }

        @Override
        public int getIconWidth() {
            return w;
        }

        @Override
        public int getIconHeight() {
            return h;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                double ix = x + 1.0;
                double iy = y + 1.0;
                double iw = Math.max(8.0, w - 2.0);
                double ih = Math.max(8.0, h - 2.0);
                double cx = ix + iw * 0.5;
                double cy = iy + ih * 0.5;
                double d = Math.min(iw, ih) * 0.92;
                double px = cx - d * 0.5;
                double py = cy - d * 0.5;

                java.awt.geom.Ellipse2D sphere = new java.awt.geom.Ellipse2D.Double(px, py, d, d);
                float[] dist = { 0f, 0.55f, 1f };
                Color[] colors = {
                        new Color(120, 200, 255),
                        new Color(45, 140, 200),
                        new Color(20, 70, 120)
                };
                g2.setPaint(new java.awt.RadialGradientPaint(
                        (float) (px + d * 0.33),
                        (float) (py + d * 0.30),
                        (float) (d * 0.58),
                        dist,
                        colors));
                g2.fill(sphere);

                g2.setColor(new Color(72, 160, 95, 200));
                g2.fill(new java.awt.geom.Ellipse2D.Double(px + d * 0.18, py + d * 0.42, d * 0.35, d * 0.22));
                g2.setColor(new Color(110, 140, 70, 190));
                g2.fill(new java.awt.geom.Ellipse2D.Double(px + d * 0.48, py + d * 0.22, d * 0.28, d * 0.20));

                g2.setColor(EdoUi.withAlpha(Color.WHITE, 200));
                g2.fill(new java.awt.geom.Ellipse2D.Double(px + d * 0.22, py + d * 0.20, d * 0.14, d * 0.10));

                g2.setColor(EdoUi.Internal.BLACK_ALPHA_180);
                g2.setStroke(new BasicStroke(Math.max(0.75f, (float) (w * 0.05f)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(sphere);
            } finally {
                g2.dispose();
            }
        }
    }

    private static final class SneakerIcon implements Icon {
        private final int w;
        private final int h;

        SneakerIcon(int w, int h) {
            this.w = w;
            this.h = h;
        }

        @Override
        public int getIconWidth() {
            return w;
        }

        @Override
        public int getIconHeight() {
            return h;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                double ix = x + 0.5;
                double iy = y + 0.5;
                double iw = Math.max(8.0, w - 1.0);
                double ih = Math.max(6.0, h - 1.0);

                Color sneakerBase = EdoUi.User.SNEAKER;
                Color upper = EdoUi.withAlpha(sneakerBase, 245);
                Color upperShade = new Color(
                        clamp255((int) Math.round(sneakerBase.getRed() * 0.797f)),
                        clamp255((int) Math.round(sneakerBase.getGreen() * 0.682f)),
                        clamp255((int) Math.round(sneakerBase.getBlue() * 0.682f)),
                        220);
                Color outline = new Color(50, 50, 50, 235);
                Color sole = new Color(252, 252, 252, 250);
                Color trim = new Color(150, 150, 150, 230);
                Color stripe = new Color(35, 35, 35, 245);
                Color lace = new Color(238, 238, 238, 245);

                // Exaggerated Chuck-style high-top silhouette for readability.
                Path2D shoe = new Path2D.Double();
                shoe.moveTo(ix + iw * 0.05, iy + ih * 0.72); // heel bottom (closer to sole)
                shoe.lineTo(ix + iw * 0.06, iy + ih * 0.10); // high collar back (taller)
                shoe.lineTo(ix + iw * 0.34, iy + ih * 0.11); // collar top (flat/high)
                shoe.lineTo(ix + iw * 0.42, iy + ih * 0.40); // lace throat
                shoe.lineTo(ix + iw * 0.72, iy + ih * 0.42); // vamp
                shoe.curveTo(ix + iw * 0.95, iy + ih * 0.44, ix + iw * 1.00, iy + ih * 0.62, ix + iw * 0.87, iy + ih * 0.72);
                shoe.lineTo(ix + iw * 0.75, iy + ih * 0.73);
                shoe.lineTo(ix + iw * 0.05, iy + ih * 0.73);
                shoe.closePath();

                g2.setColor(upper);
                g2.fill(shoe);
                g2.setColor(outline);
                g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(shoe);
                g2.setColor(EdoUi.withAlpha(new Color(upperShade.getRed(), upperShade.getGreen(), upperShade.getBlue()), 180));
                g2.draw(new java.awt.geom.Line2D.Double(ix + iw * 0.16, iy + ih * 0.22, ix + iw * 0.30, iy + ih * 0.70));

                // Rubber toe cap: 90deg corner + quarter-arc to the right.
                Path2D toeCap = new Path2D.Double();
                double toeLeft = ix + iw * 0.74;
                double toeTop = iy + ih * 0.52;
                double toeBottom = iy + ih * 0.76;
                double toeRight = ix + iw * 0.95;
                toeCap.moveTo(toeLeft, toeBottom);
                toeCap.lineTo(toeLeft, toeTop); // vertical edge (90deg corner)
                toeCap.lineTo(ix + iw * 0.86, toeTop); // top flat
                toeCap.curveTo(
                        ix + iw * 0.93, toeTop,      // arc control 1
                        toeRight, iy + ih * 0.59,    // arc control 2
                        toeRight, toeBottom          // arc end
                );
                toeCap.closePath();
                g2.setColor(sole);
                g2.fill(toeCap);
                g2.setColor(trim);
                g2.draw(toeCap);

                // Sole and foxing stripe.
                int sx = (int) Math.round(ix + iw * 0.03);
                int sy = (int) Math.round(iy + ih * 0.73);
                int sw = (int) Math.round(iw * 0.92);
                int sh = Math.max(2, (int) Math.round(ih * 0.15));
                g2.setColor(sole);
                g2.fillRoundRect(sx, sy, sw, sh, 3, 3);
                g2.setColor(trim);
                g2.drawRoundRect(sx, sy, sw, sh, 3, 3);
                g2.setColor(stripe);
                g2.setStroke(new BasicStroke(0.9f));
                g2.draw(new java.awt.geom.Line2D.Double(ix + iw * 0.10, iy + ih * 0.81, ix + iw * 0.84, iy + ih * 0.81));
                g2.setColor(outline);
                g2.setStroke(new BasicStroke(0.75f));
                g2.draw(new java.awt.geom.Line2D.Double(ix + iw * 0.10, iy + ih * 0.83, ix + iw * 0.84, iy + ih * 0.83));

                // Circular ankle patch + eyelets.
                double patchD = Math.min(iw, ih) * 0.21;
                java.awt.geom.Ellipse2D patchOuter = new java.awt.geom.Ellipse2D.Double(ix + iw * 0.15, iy + ih * 0.22, patchD, patchD);
                java.awt.geom.Ellipse2D patchInner = new java.awt.geom.Ellipse2D.Double(ix + iw * 0.19, iy + ih * 0.26, patchD * 0.58, patchD * 0.58);
                g2.setColor(sole);
                g2.fill(patchOuter);
                g2.setColor(trim);
                g2.draw(patchOuter);
                g2.setColor(new Color(58, 84, 170, 235));
                g2.fill(patchInner);
                for (int i = 0; i < 4; i++) {
                    double ex = ix + iw * (0.42 + i * 0.07);
                    double ey = iy + ih * 0.49;
                    g2.fill(new java.awt.geom.Ellipse2D.Double(ex, ey, iw * 0.025, ih * 0.05));
                }
                g2.setColor(lace);
                g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new java.awt.geom.Line2D.Double(ix + iw * 0.43, iy + ih * 0.47, ix + iw * 0.53, iy + ih * 0.39));
                g2.draw(new java.awt.geom.Line2D.Double(ix + iw * 0.50, iy + ih * 0.50, ix + iw * 0.60, iy + ih * 0.42));
                g2.draw(new java.awt.geom.Line2D.Double(ix + iw * 0.57, iy + ih * 0.53, ix + iw * 0.67, iy + ih * 0.45));
            } finally {
                g2.dispose();
            }
        }

        private static int clamp255(int v) {
            return Math.max(0, Math.min(255, v));
        }
    }

static class Row {
        final BodyInfo body;
        final boolean detail;
        final boolean destinationRow;
        /** True for ring summary lines under a body (not exobiology). */
        final boolean ringDetail;
        final int parentId;
        final String bioText;
        final String bioValue;
        /** Estimated Vista Genomics payout for this row (for money-bag vs prefs threshold); null if unknown. */
        final Long bioEstimatedPayoutCredits;
        /** Body row: precomputed {@code NN–MMM} summary; null if none. */
        final String bioHeaderSummary;
        /** When non-null, overrides journal distance in the Dist column (ship-centric mode). */
        final Double distanceColumnLs;
        /** Ship-centric mode but commander position unknown — Dist column shows an em dash, not arrival Ls. */
        final boolean shipCentricDistanceUnknown;
        private int bioSampleCount;
        
        private boolean observedGenusHeader;

        String getBioHeaderSummary() {
            return bioHeaderSummary;
        }

        boolean isRingDetail() {
            return ringDetail;
        }

        boolean isObservedGenusHeader() {
            return observedGenusHeader;
        }

        void setObservedGenusHeader(boolean observedGenusHeader) {
            this.observedGenusHeader = observedGenusHeader;
        }
        private Row(BodyInfo body,
                    boolean detail,
                    boolean destinationRow,
                    boolean ringDetail,
                    int parentId,
                    String bioText,
                    String bioValue,
                    Long bioEstimatedPayoutCredits,
                    String bioHeaderSummary,
                    Double distanceColumnLs,
                    boolean shipCentricDistanceUnknown) {
            this.body = body;
            this.detail = detail;
            this.destinationRow = destinationRow;
            this.ringDetail = ringDetail;
            this.parentId = parentId;
            this.bioText = bioText;
            this.bioValue = bioValue;
            this.bioEstimatedPayoutCredits = bioEstimatedPayoutCredits;
            this.bioHeaderSummary = bioHeaderSummary;
            this.distanceColumnLs = distanceColumnLs;
            this.shipCentricDistanceUnknown = shipCentricDistanceUnknown;
            this.bioSampleCount = 0;
        }
        int getBioSampleCount() {
            return bioSampleCount;
        }

        void setBioSampleCount(int bioSampleCount) {
            this.bioSampleCount = bioSampleCount;
        }

        static Row bio(int parentId, String text, String val, int bioSampleCount) {
            return bio(parentId, text, val, bioSampleCount, null);
        }

        static Row bio(int parentId, String text, String val, int bioSampleCount, Long estimatedCredits) {
            Row r = new Row(null, true, false, false, parentId, text, val, estimatedCredits, null, null, false);
            r.setBioSampleCount(bioSampleCount);
            return r;
        }

        static Row body(BodyInfo b) {
            return body(b, null);
        }

        static Row body(BodyInfo b, String bioHeaderSummary) {
            return body(b, bioHeaderSummary, null);
        }

        static Row body(BodyInfo b, String bioHeaderSummary, Double distanceColumnLs) {
            return body(b, bioHeaderSummary, distanceColumnLs, false);
        }

        static Row body(BodyInfo b, String bioHeaderSummary, Double distanceColumnLs, boolean shipCentricDistanceUnknown) {
            return new Row(b, false, false, false, -1, null, null, null, bioHeaderSummary, distanceColumnLs,
                    shipCentricDistanceUnknown);
        }

        static Row bio(int parentId, String text, String val) {
            return new Row(null, true, false, false, parentId, text, val, null, null, null, false);
        }

        static Row bio(int parentId, String text, String val, Long estimatedCredits) {
            return new Row(null, true, false, false, parentId, text, val, estimatedCredits, null, null, false);
        }

        static Row ring(int parentId, String text) {
            String t = (text != null) ? text : "";
            return new Row(null, true, false, true, parentId, t, "", null, null, null, false);
        }

        static Row destination(int parentId, String destinationName) {
            String name = (destinationName != null) ? destinationName : "";
            return new Row(null, true, true, false, parentId, name, null, null, null, null, false);
        }
    }

    // NEW: custom JTable to draw separators only between systems
    private class SystemBodiesTable extends JTable {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
        SystemBodiesTable(SystemBodiesTableModel model) {
            super(model);
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            Point p = event.getPoint();
            int viewRow = rowAtPoint(p);
            int col = columnAtPoint(p);
            if (viewRow < 0 || col < 0) {
                return super.getToolTipText(event);
            }
            if (col == 2) {
                Row r = tableModel.getRowAt(viewRow);
                if (r != null && !r.detail && r.body != null) {
                    String hdr = BioTableBuilder.formatBodyBioColumnText(r.body);
                    if (hdr == null || hdr.isBlank()) {
                        hdr = r.getBioHeaderSummary();
                    }
                    if (hdr == null || hdr.isBlank()) {
                        hdr = formatBioHeaderValueOrRange(r.body);
                    }
                    if (hdr != null && !hdr.isBlank()) {
                        return "<html><body style='font-size:11px'>Estimated exobiology on this body: "
                                + "remaining payout range (from FSS signal count and plausible species), "
                                + "plus credits for fully scanned species in parentheses, as shown: "
                                + hdr + " (million-credit scale).</body></html>";
                    }
                }
            }
            if (col == 1) {
                Object v = getValueAt(viewRow, col);
                if (v != null) {
                    String text = String.valueOf(v);
                    if (!text.isBlank()) {
                        return text;
                    }
                }
            }
            if (col == 3) {
                Row r = tableModel.getRowAt(viewRow);
                if (r != null && !r.detail && r.body != null && r.body.isHighValue()) {
                    String html = ExplorationBodyCredits.formatExplorationTooltipHtml(r.body);
                    if (html != null) {
                        return html;
                    }
                    String fallback = ValuableBodyExplorationEstimate.formatHighValueFallbackTooltipHtml(r.body);
                    if (fallback != null) {
                        return fallback;
                    }
                    return "<html><body style='font-size:11px;text-align:left'>High-value world.</body></html>";
                }
                if (r != null && r.detail && !r.destinationRow && !r.isRingDetail()
                        && r.bioValue != null && !r.bioValue.isBlank()) {
                    return "<html><body style='font-size:11px'>Exobiology value (approximate payout tier).</body></html>";
                }
            }
            return super.getToolTipText(event);
        }

        @Override
        protected void configureEnclosingScrollPane() {
            super.configureEnclosingScrollPane();

            // JTable may have just installed a LAF "table scrollpane" border with a shadow.
            Container p = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
            if (p instanceof JScrollPane) {
                JScrollPane sp = (JScrollPane)p;
                sp.setBorder(null);
                sp.setViewportBorder(null);
            }
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(EdoUi.ED_ORANGE_TRANS);

                int rowCount = tableModel.getRowCount();
                boolean firstBodySeen = false;

                for (int row = 0; row < rowCount; row++) {
                    Row r = tableModel.getRowAt(row);
                    if (!r.detail) { // body row
                        if (firstBodySeen) {
                            Rectangle rect = getCellRect(row, 0, true);
                            int y = rect.y;
                            g2.setColor(EdoUi.ED_ORANGE_TRANS);
                            g2.drawLine(0, y, getWidth(), y);
                        } else {
                            firstBodySeen = true;
                        }
                    }
                }

                paintTargetBodyOutline(g2);
                paintNearBodyOutline(g2);
                paintDestinationRowText(g2);
            } finally {
                g2.dispose();
            }
        }
        private void paintDestinationRowText(Graphics2D g2) {
            Integer parentId = SystemTabPanel.this.targetDestinationParentBodyId;
            String destName = SystemTabPanel.this.targetDestinationName;

            if (parentId == null || destName == null || destName.isBlank()) {
                return;
            }

            int rowCount = tableModel.getRowCount();
            for (int row = 0; row < rowCount; row++) {
                Row r = tableModel.getRowAt(row);
                if (r == null || !r.detail || !r.destinationRow) {
                    continue;
                }
                if (r.parentId != parentId.intValue()) {
                    continue;
                }

                String text = r.bioText;
                if (text == null || text.isBlank()) {
                    return;
                }

                // Destination text is displayed in column 2 in the model.
                Rectangle cellRect = getCellRect(row, 2, true);

                // Extend drawing across the whole row (so it can spill into later columns).
                Rectangle rowRect = getCellRect(row, 0, true);
                rowRect.width = getWidth();

                Rectangle clip = g2.getClipBounds();
                if (clip != null && !clip.intersects(rowRect)) {
                    return;
                }

                Graphics2D g = (Graphics2D) g2.create();
                try {
                    g.setClip(rowRect);

                    g.setFont(getFont());
                    FontMetrics fm = g.getFontMetrics();

                    // Use the same gray tone as the target outline so it looks intentional.
                    g.setColor(EdoUi.Internal.GRAY_ALPHA_200);

                    int x = cellRect.x + 6;
                    int y = rowRect.y + (rowRect.height + fm.getAscent()) / 2 - 2;

                    g.drawString(text, x, y);
                } finally {
                    g.dispose();
                }

                // Only one destination row is injected, so we can stop.
                return;
            }
        }
        private void paintTargetBodyOutline(Graphics2D g2) {
            Integer targetBodyId = SystemTabPanel.this.targetBodyId;
            if (targetBodyId == null) {
                return;
            }

            int rowCount = tableModel.getRowCount();
            int first = -1;
            int last = -1;

            for (int row = 0; row < rowCount; row++) {
                Row r = tableModel.getRowAt(row);
                if (r == null) {
                    continue;
                }

                boolean match = false;
                if (!r.detail) {
                    if (r.body != null && r.body.getBodyId() == targetBodyId.intValue()) {
                        match = true;
                    }
                } else {
                    if (r.parentId == targetBodyId.intValue()) {
                        match = true;
                    }
                }

                if (match) {
                    if (first < 0) {
                        first = row;
                    }
                    last = row;
                } else if (first >= 0) {
                    // Rows for a body are contiguous; once we leave the block we can stop.
                    break;
                }
            }

            if (first < 0 || last < 0) {
                return;
            }

            Rectangle top = getCellRect(first, 0, true);
            Rectangle bottom = getCellRect(last, getColumnCount() - 1, true);

            int y = top.y;
            int h = (bottom.y + bottom.height) - y;

            Rectangle block = new Rectangle(0, y, getWidth(), h);
            Rectangle clip = g2.getClipBounds();
            if (clip != null && !clip.intersects(block)) {
                return;
            }

            Object oldAA = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Gray dashed outline for the currently targeted body.
            Color outline = EdoUi.Internal.GRAY_ALPHA_140;
            g2.setColor(outline);
            float[] dash = new float[] { 6.0f, 6.0f };
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10.0f, dash, 0.0f));

            int inset = 2;
            int arc = 12;
            g2.drawRoundRect(inset,
                    y + inset,
                    getWidth() - (inset * 2) - 1,
                    h - (inset * 2) - 1,
                    arc,
                    arc);

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
        }

        private void paintNearBodyOutline(Graphics2D g2) {
            Integer targetBodyId = SystemTabPanel.this.nearBodyId;
            if (targetBodyId == null) {
                return;
            }

            int rowCount = tableModel.getRowCount();
            int first = -1;
            int last = -1;

            for (int row = 0; row < rowCount; row++) {
                Row r = tableModel.getRowAt(row);
                if (r == null) {
                    continue;
                }

                boolean match = false;
                if (!r.detail) {
                    if (r.body != null && r.body.getBodyId() == targetBodyId.intValue()) {
                        match = true;
                    }
                } else {
                    if (r.parentId == targetBodyId.intValue()) {
                        match = true;
                    }
                }

                if (match) {
                    if (first < 0) {
                        first = row;
                    }
                    last = row;
                } else if (first >= 0) {
                    // Rows for a body are contiguous; once we leave the block we can stop.
                    break;
                }
            }

            if (first < 0 || last < 0) {
                return;
            }

            Rectangle top = getCellRect(first, 0, true);
            Rectangle bottom = getCellRect(last, getColumnCount() - 1, true);

            int y = top.y;
            int h = (bottom.y + bottom.height) - y;

            Rectangle block = new Rectangle(0, y, getWidth(), h);
            Rectangle clip = g2.getClipBounds();
            if (clip != null && !clip.intersects(block)) {
                return;
            }

            Object oldAA = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color outline = EdoUi.Internal.MAIN_TEXT_ALPHA_200;
            g2.setColor(outline);
            g2.setStroke(new BasicStroke(2.0f));

            int inset = 2;
            int arc = 12;
            g2.drawRoundRect(inset,
                    y + inset,
                    getWidth() - (inset * 2) - 1,
                    h - (inset * 2) - 1,
                    arc,
                    arc);

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
        }
    }

    class SystemBodiesTableModel extends AbstractTableModel {

        private final String[] columns = {
                "Body",
                                "Atmo / Body",
                "Bio",
                "Value",
                "Land",
                "Dist (Ls)"
        };

        private final List<Row> rows = new ArrayList<>();

        void setRows(List<Row> newRows) {
            rows.clear();
            if (newRows != null) {
                rows.addAll(newRows);
            }
            fireTableDataChanged();
        }

        // NEW: allow table to inspect rows (for separators)
        Row getRowAt(int index) {
            return rows.get(index);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int col) {
            return columns[col];
        }

        @Override
        public Object getValueAt(int rowIndex, int col) {
            Row r = rows.get(rowIndex);

            if (r.detail) {
                if (r.destinationRow) {
                	return "";
                }
                switch (col) {
                    case 2: return r.bioText != null ? r.bioText : "";
                    case 3: return r.bioValue != null ? r.bioValue : "";
                    default: return "";
                }
            }

            BodyInfo b = r.body;
            switch (col) {
                case 0:
                	String shortName = b.getShortName();
                    if (shortName != null
                            && b.getStarType() != null
                            && b.getStarSystem() != null
                            && shortName.equals(b.getStarSystem())) {
                        return "*";
                    }
                    return shortName != null ? shortName : "";
                case 1:
                    String atmo = b.getAtmoOrType() != null ? b.getAtmoOrType() : "";
                    atmo = atmo.replaceAll("content body",  "body");
                    atmo = atmo.replaceAll("No atmosphere",  "");
                    atmo = atmo.replaceAll("atmosphere",  "");
                    return atmo;
                case 2:
                    // Spansh “no bio signals” unless journal/FSS contradicts
                    if (BioTableBuilder.spanshExobiologyExclusionActive(b)) {
                        return "";
                    }
                    if (b.hasBio() && b.hasGeo()) return "Bio + Geo";
                    if (b.hasGeo()) return "Geo";
                    return "";
                case 3:
                    // Main body row: typical exploration payout (FSS+DSS) for ELW/WW/AW/terraformable.
                    // Detail rows carry exobiology M Cr values.
                    if (b.isHighValue()) {
                        long cr = ValuableBodyExplorationEstimate.resolveCreditsForDisplay(b);
                        return ValuableBodyExplorationEstimate.formatCredits(cr);
                    }
                    return "";
                case 4:
                    return b.isLandable() ? "Yes" : "";
                case 5:
                    if (r.shipCentricDistanceUnknown) {
                        return "\u2014";
                    }
                    double distLsCol;
                    if (r.distanceColumnLs != null && Double.isFinite(r.distanceColumnLs.doubleValue())) {
                        distLsCol = r.distanceColumnLs.doubleValue();
                    } else {
                        distLsCol = b.getDistanceLs();
                    }
                    if (Double.isNaN(distLsCol)) {
                        return "";
                    }
                    return String.format(Locale.US, "%.0f Ls", distLsCol);
                default:
                    return "";
            }
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    }
    private void onMapViewTiltSliderChanged() {
        if (mapViewTiltSlider == null || mapViewTiltValueLabel == null) {
            return;
        }
        int deg = mapViewTiltSlider.getValue();
        mapViewTiltValueLabel.setText(deg + "°");
        if (!mapViewTiltSlider.isEnabled()) {
            return;
        }
        boolean persist = !mapViewTiltSlider.getValueIsAdjusting();
        systemPlanMapPanel.setViewTiltDegrees(deg, persist);
    }

    private void configureSystemBodiesTableColumnResize() {
        if (table == null) {
            return;
        }
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        if (systemBodyScrollPane == null) {
            return;
        }
        JViewport viewport = systemBodyScrollPane.getViewport();
        if (viewport == null) {
            return;
        }
        viewport.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(SystemTabPanel.this::applySystemBodiesTableColumnLayout);
            }
        });
    }

    /**
     * Body names stay fully visible; atmo/type text yields width first, then dist/value/land; bio keeps a floor.
     */
    private void applySystemBodiesTableColumnLayout() {
        if (table == null || systemBodyScrollPane == null) {
            return;
        }
        JViewport viewport = systemBodyScrollPane.getViewport();
        if (viewport == null) {
            return;
        }
        int avail = viewport.getExtentSize().width;
        if (avail <= 0) {
            return;
        }

        int wBody = measureSystemTableColumnContentPx(0, SYSTEM_TABLE_BODY_COL_PAD_PX);
        int wAtmoPref = measureSystemTableColumnContentPx(1, SYSTEM_TABLE_BODY_COL_PAD_PX);
        int wBio = measureSystemTableBioColumnContentPx();
        int wValue = Math.max(SYSTEM_TABLE_VALUE_COL_MIN_PX, measureSystemTableColumnContentPx(3, 8));
        int wLand = Math.max(SYSTEM_TABLE_LAND_COL_MIN_PX, measureSystemTableColumnContentPx(4, 8));
        int wDist = Math.max(SYSTEM_TABLE_DIST_COL_MIN_PX, measureSystemTableColumnContentPx(5, 8));

        int wAtmo = Math.max(SYSTEM_TABLE_ATMO_COL_MIN_PX, avail - wBody - wBio - wValue - wLand - wDist);
        if (wAtmo > wAtmoPref) {
            wDist += wAtmo - wAtmoPref;
            wAtmo = wAtmoPref;
        }

        int total = wBody + wAtmo + wBio + wValue + wLand + wDist;
        if (total > avail) {
            int slack = total - avail;
            int take = Math.min(slack, Math.max(0, wAtmo - SYSTEM_TABLE_ATMO_COL_MIN_PX));
            wAtmo -= take;
            slack -= take;
            if (slack > 0) {
                take = Math.min(slack, Math.max(0, wDist - SYSTEM_TABLE_DIST_COL_MIN_PX));
                wDist -= take;
                slack -= take;
            }
            if (slack > 0) {
                take = Math.min(slack, Math.max(0, wValue - SYSTEM_TABLE_VALUE_COL_MIN_PX));
                wValue -= take;
                slack -= take;
            }
            if (slack > 0) {
                take = Math.min(slack, Math.max(0, wLand - SYSTEM_TABLE_LAND_COL_MIN_PX));
                wLand -= take;
                slack -= take;
            }
            // Bio and Body widths are content-sized; do not truncate payout/signal text.
        } else if (total < avail) {
            wDist += avail - total;
        }

        TableColumnModel cm = table.getColumnModel();
        setSystemTableColumnFixedWidth(cm.getColumn(0), wBody);
        setSystemTableColumnFlexibleWidth(cm.getColumn(1), wAtmo, SYSTEM_TABLE_ATMO_COL_MIN_PX);
        setSystemTableColumnFixedWidth(cm.getColumn(2), wBio);
        setSystemTableColumnFlexibleWidth(cm.getColumn(3), wValue, SYSTEM_TABLE_VALUE_COL_MIN_PX);
        setSystemTableColumnFlexibleWidth(cm.getColumn(4), wLand, SYSTEM_TABLE_LAND_COL_MIN_PX);
        setSystemTableColumnFlexibleWidth(cm.getColumn(5), wDist, SYSTEM_TABLE_DIST_COL_MIN_PX);
        table.revalidate();
        table.repaint();
        SwingUtilities.invokeLater(() -> {
            if (systemBodyScrollPane == null) {
                return;
            }
            JViewport vp = systemBodyScrollPane.getViewport();
            if (vp == null) {
                return;
            }
            Point p = vp.getViewPosition();
            if (p.x != 0) {
                vp.setViewPosition(new Point(0, p.y));
            }
        });
    }

    /**
     * Bio column display width: leading icon stack + plain text from {@link BioTableBuilder#formatBodyBioColumnText}
     * (model column 2 is often empty on body rows).
     */
    private int measureSystemTableBioColumnContentPx() {
        if (table == null || tableModel == null) {
            return SYSTEM_TABLE_BIO_COL_MIN_PX;
        }
        FontMetrics fm = table.getFontMetrics(table.getFont());
        int max = fm.stringWidth("Bio");
        int leadingPad = bioColumnBioLeadingSlotWidthPx() + 4;
        int rows = tableModel.getRowCount();
        for (int r = 0; r < rows; r++) {
            Row row = tableModel.getRowAt(r);
            String text = systemTableBioCellPlainText(row);
            if (text == null || text.isEmpty()) {
                continue;
            }
            max = Math.max(max, leadingPad + fm.stringWidth(text));
        }
        return Math.max(SYSTEM_TABLE_BIO_COL_MIN_PX, max + 8);
    }

    private String systemTableBioCellPlainText(Row r) {
        if (r == null) {
            return "";
        }
        if (r.detail) {
            if (!r.destinationRow && r.bioText != null && !r.bioText.isBlank()) {
                return r.bioText;
            }
            return "";
        }
        BodyInfo b = r.body;
        if (b == null || BioTableBuilder.spanshExobiologyExclusionActive(b) || !b.hasBio()) {
            return "";
        }
        String cell = BioTableBuilder.formatBodyBioColumnText(b);
        if (cell != null && !cell.isEmpty()) {
            return cell;
        }
        String valueOrRange = r.getBioHeaderSummary();
        if (valueOrRange == null || valueOrRange.isEmpty()) {
            valueOrRange = formatBioHeaderValueOrRange(b);
        }
        return valueOrRange != null ? valueOrRange : "";
    }

    private int measureSystemTableColumnContentPx(int modelColumn, int padPx) {
        if (table == null || tableModel == null) {
            return padPx;
        }
        FontMetrics fm = table.getFontMetrics(table.getFont());
        int max = 0;
        String header = table.getColumnName(modelColumn);
        if (header != null) {
            max = fm.stringWidth(header);
        }
        int rows = tableModel.getRowCount();
        for (int r = 0; r < rows; r++) {
            Object v = tableModel.getValueAt(r, modelColumn);
            if (v == null) {
                continue;
            }
            String s = String.valueOf(v);
            if (!s.isEmpty()) {
                max = Math.max(max, fm.stringWidth(s));
            }
        }
        return max + padPx;
    }

    private static void setSystemTableColumnFixedWidth(TableColumn col, int w) {
        col.setMinWidth(w);
        col.setMaxWidth(w);
        col.setPreferredWidth(w);
        col.setWidth(w);
    }

    private static void setSystemTableColumnFlexibleWidth(TableColumn col, int w, int minWidthPx) {
        col.setMinWidth(minWidthPx);
        col.setMaxWidth(Integer.MAX_VALUE);
        col.setPreferredWidth(w);
        col.setWidth(w);
    }

    private static void applySystemBodiesTextCellStyle(Component c, Row r, boolean isSelected) {
        if (isSelected) {
            c.setForeground(Color.BLACK);
        } else if (r != null && r.detail && r.isRingDetail()) {
            c.setForeground(EdoUi.Internal.GRAY_180);
        } else if (r != null && r.detail && !r.destinationRow && !r.isRingDetail()
                && (r.bioText != null || r.bioValue != null)) {
            int samples = r.getBioSampleCount();
            if (samples >= 3) {
                c.setForeground(EdoUi.User.PRIMARY_HIGHLIGHT);
            } else if (samples > 0) {
                c.setForeground(EdoUi.User.SECONDARY_HIGHLIGHT);
            } else {
                c.setForeground(EdoUi.Internal.GRAY_180);
            }
        } else {
            c.setForeground(EdoUi.User.MAIN_TEXT);
        }
        if (c instanceof JComponent) {
            ((JComponent) c).setOpaque(false);
        }
        c.setBackground(EdoUi.Internal.TRANSPARENT);
    }

    private static String ellipsizeTextToPixelWidth(String text, FontMetrics fm, int maxPx) {
        if (text == null || text.isEmpty() || maxPx <= 0) {
            return text != null ? text : "";
        }
        if (fm.stringWidth(text) <= maxPx) {
            return text;
        }
        String ell = "\u2026";
        int ellW = fm.stringWidth(ell);
        int budget = Math.max(0, maxPx - ellW);
        for (int end = text.length(); end > 0; end--) {
            if (fm.stringWidth(text.substring(0, end)) <= budget) {
                return text.substring(0, end) + ell;
            }
        }
        return ell;
    }

    private void selectSystemTabTableSortMode(SystemTabTableSortMode mode) {
        if (mode == null) {
            return;
        }
        OverlayPreferences.setSystemTabTableSortMode(mode);
        updateDistModeToggleAppearance();
        fireSessionStateChanged();
        requestRebuild();
    }

    /** Pass-through hover dwell: switch only when the mode actually changes. */
    private void applySystemTabTableSortMode(SystemTabTableSortMode mode) {
        if (mode == null || OverlayPreferences.getSystemTabTableSortMode() == mode) {
            return;
        }
        selectSystemTabTableSortMode(mode);
    }

    private void configureDistModeToggleButton(JButton b, String tooltip) {
        b.setToolTipText(tooltip);
        b.setMargin(new java.awt.Insets(2, 4, 2, 4));
        b.setBorderPainted(true);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setForeground(EdoUi.User.MAIN_TEXT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    private void applyDistModeToggleButtonHoverChrome(JButton b, boolean selected, boolean hovered) {
        Color ink = EdoUi.User.MAIN_TEXT;
        Color hoverLine = EdoUi.Internal.MAIN_TEXT_ALPHA_200;
        Color hoverFill = EdoUi.Internal.MAIN_TEXT_ALPHA_40;

        if (selected) {
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ink, hovered ? 2 : 1),
                    new EmptyBorder(hovered ? 1 : 2, 3, hovered ? 1 : 2, 3)));
            if (hovered) {
                b.setOpaque(true);
                b.setBackground(hoverFill);
            } else {
                b.setOpaque(false);
            }
        } else if (hovered) {
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(hoverLine, 1),
                    new EmptyBorder(2, 4, 2, 4)));
            b.setOpaque(true);
            b.setBackground(hoverFill);
        } else {
            b.setBorder(new EmptyBorder(3, 5, 3, 5));
            b.setOpaque(false);
        }
        b.setCursor(Cursor.getPredefinedCursor(hovered ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private void updateDistModeToggleAppearance() {
        if (distFromShipButton == null || distFromStarButton == null || distByValueButton == null) {
            return;
        }
        SystemTabTableSortMode mode = OverlayPreferences.getSystemTabTableSortMode();
        applyDistModeToggleButtonHoverChrome(distFromShipButton, mode == SystemTabTableSortMode.FROM_SHIP,
                distFromShipHovered);
        applyDistModeToggleButtonHoverChrome(distFromStarButton, mode == SystemTabTableSortMode.FROM_STAR,
                distFromStarHovered);
        applyDistModeToggleButtonHoverChrome(distByValueButton, mode == SystemTabTableSortMode.BY_VALUE,
                distByValueHovered);
        distFromShipButton.repaint();
        distFromStarButton.repaint();
        distByValueButton.repaint();
        refreshOrbitEvolutionTimerRunning();
    }

    private void refreshOrbitEvolutionTimerRunning() {
        if (orbitEvolutionTimer == null) {
            return;
        }
        if (OverlayPreferences.isSystemTabDistanceFromShip()) {
            orbitEvolutionTimer.start();
        } else {
            orbitEvolutionTimer.stop();
        }
    }

    private void scheduleShipTelemetryRebuild() {
        if (shipTelemetryRebuildTimer != null) {
            shipTelemetryRebuildTimer.restart();
        }
    }

    private static boolean shouldRefreshFleetCarrierProximity(EliteLogEvent event) {
        if (event instanceof CarrierLocationEvent) {
            return true;
        }
        if (event instanceof LocationEvent le) {
            return le.isDocked();
        }
        if (event instanceof CarrierJumpEvent) {
            return true;
        }
        return event != null && event.getType() == EliteEventType.DOCKED;
    }

    /**
     * After carrier orbit journal lines, sync table/map proximity to the parked body without waiting for Status.
     */
    private void applyFleetCarrierDockedProximity() {
        if (!state.isDocked()) {
            return;
        }
        Map<Integer, BodyInfo> bodies = state.getBodies();
        Integer fcBody = resolveFleetCarrierParkedBodyForAnchor(bodies);
        if (fcBody == null) {
            return;
        }
        if (Objects.equals(nearBodyId, fcBody)) {
            return;
        }
        nearBodyId = fcBody;
        BodyInfo bi = bodies.get(fcBody);
        if (bi != null && bi.getBodyName() != null && !bi.getBodyName().isBlank()) {
            nearBodyName = bi.getBodyName().trim();
        }
        Consumer<BodyInfo> listener = nearBodyChangedListener;
        if (listener != null) {
            listener.accept(bi);
        }
        fireSessionStateChanged();
        requestRebuild();
    }

    /** Map key for a journal {@code BodyID} (may differ from map key on barycentre / ring rows). */
    private static Integer bodyMapKeyForJournalId(Map<Integer, BodyInfo> bodies, int journalBodyId) {
        return SystemMapRules.mapKeyForJournalBodyId(bodies, journalBodyId);
    }

    /**
     * Fleet carrier parked orbit body while docked: used as commander anchor for both ship-ref modes when in scope.
     */
    private Integer resolveFleetCarrierParkedBodyForAnchor(Map<Integer, BodyInfo> bodies) {
        if (!state.isDocked()) {
            return null;
        }
        Integer parked = state.getCarrierParkedBodyId();
        long parkedSys = state.getCarrierParkedSystemAddress();
        long curSys = state.getSystemAddress();
        if (parked == null || parked.intValue() <= 0) {
            return null;
        }
        if (parkedSys != 0L && parkedSys != curSys) {
            return null;
        }
        return bodyMapKeyForJournalId(bodies, parked.intValue());
    }

    /**
     * Body whose centre (plus optional Status lat/lon/alt) anchors ship-centric distances and the distance column
     * when “from ship” / targeted mode applies. See {@link SystemTabShipRefMode} (Overlay preferences → System tab).
     * <p>
     * The orbit plan map’s ▲ “You” label is {@link #resolvePlanMapTriangleAnchorBodyId()} in
     * {@link SystemTabShipRefMode#TARGETED_BODY} (HUD navigation target, not FSS destination rows). Ship position on
     * the map still uses {@link #resolvePlanMapShipAnchorBodyId()} (physical anchor only).
     * </p>
     * <p>
     * Both modes: docked on a fleet carrier (journal parked body in scope) → that parked body. Otherwise a surface
     * fix ties to {@link #nearBodyId}. {@link SystemTabShipRefMode#APPROACH_BODY}: ApproachBody, journal
     * {@code Scan} with {@code ScanType: Detailed} (DSS ship-ref only), supercruise exit drop, Status near-body, persisted
     * last-visited sticky, primary star. {@link SystemTabShipRefMode#TARGETED_BODY}: active ApproachBody / DSS
     * detailed scan first, then HUD body target, then sticky last HUD target until another is chosen, then the same
     * proximity fallbacks as approach mode.
     * </p>
     * The table proximity outline still follows {@link #nearBodyId} only.
     */
    private Integer resolveCommanderRefBodyId() {
        Map<Integer, BodyInfo> bodies = state.getBodies();
        if (bodies == null || bodies.isEmpty()) {
            return null;
        }
        Integer fc = resolveFleetCarrierParkedBodyForAnchor(bodies);
        if (fc != null) {
            return fc;
        }
        boolean hasSurfaceFix = statusLatitude != null && statusLongitude != null
                && statusAltitude != null && statusPlanetRadius != null
                && statusPlanetRadius.doubleValue() > 1.0;
        if (hasSurfaceFix && nearBodyId != null && bodies.containsKey(nearBodyId)) {
            return nearBodyId;
        }

        SystemTabShipRefMode mode = OverlayPreferences.getSystemTabShipRefMode();
        if (mode == SystemTabShipRefMode.TARGETED_BODY) {
            Integer ap = resolveJournalShipRefBodyId(bodies);
            if (ap != null) {
                return ap;
            }
            if (targetBodyId != null && bodies.containsKey(targetBodyId)) {
                return targetBodyId;
            }
            Integer stickyTgt = stickyHudTargetBodyId;
            if (stickyTgt != null && bodies.containsKey(stickyTgt)) {
                return stickyTgt;
            }
            Integer drop = supercruiseDropReferenceBodyId;
            if (drop != null && drop.intValue() > 0 && bodies.containsKey(drop)) {
                return drop;
            }
            Integer nb = nearBodyId;
            if (nb != null && nb.intValue() >= 0 && bodies.containsKey(nb)) {
                return nb;
            }
            Integer sticky = lastVisitedNonStarBodyId;
            if (sticky != null && sticky.intValue() > 0 && bodies.containsKey(sticky)) {
                return sticky;
            }
            if (bodies.containsKey(Integer.valueOf(0))) {
                return Integer.valueOf(0);
            }
            int anch = SystemOrbitGeometry.primaryAnchorBodyMapKey(bodies);
            if (bodies.containsKey(Integer.valueOf(anch))) {
                return Integer.valueOf(anch);
            }
            return null;
        }

        Integer ap = resolveJournalShipRefBodyId(bodies);
        if (ap != null) {
            return ap;
        }
        Integer drop = supercruiseDropReferenceBodyId;
        if (drop != null && drop.intValue() > 0 && bodies.containsKey(drop)) {
            return drop;
        }
        Integer nb = nearBodyId;
        if (nb != null && nb.intValue() >= 0 && bodies.containsKey(nb)) {
            return nb;
        }
        Integer sticky = lastVisitedNonStarBodyId;
        if (sticky != null && sticky.intValue() > 0 && bodies.containsKey(sticky)) {
            return sticky;
        }
        if (bodies.containsKey(Integer.valueOf(0))) {
            return Integer.valueOf(0);
        }
        int anch = SystemOrbitGeometry.primaryAnchorBodyMapKey(bodies);
        if (bodies.containsKey(Integer.valueOf(anch))) {
            return Integer.valueOf(anch);
        }
        return null;
    }

    /**
     * Plan-map ▲ label anchor: in {@link SystemTabShipRefMode#TARGETED_BODY}, follows HUD body target (sticky) after
     * journal ApproachBody / DSS; otherwise same fallbacks as {@link #resolvePlanMapShipAnchorBodyId()}.
     */
    private Integer resolvePlanMapTriangleAnchorBodyId() {
        Map<Integer, BodyInfo> bodies = state.getBodies();
        if (bodies == null || bodies.isEmpty()) {
            return null;
        }
        Integer fc = resolveFleetCarrierParkedBodyForAnchor(bodies);
        if (fc != null) {
            return fc;
        }
        boolean hasSurfaceFix = statusLatitude != null && statusLongitude != null
                && statusAltitude != null && statusPlanetRadius != null
                && statusPlanetRadius.doubleValue() > 1.0;
        if (hasSurfaceFix && nearBodyId != null && bodies.containsKey(nearBodyId)) {
            return nearBodyId;
        }
        Integer ap = resolveJournalShipRefBodyId(bodies);
        if (ap != null) {
            return ap;
        }
        if (OverlayPreferences.getSystemTabShipRefMode() == SystemTabShipRefMode.TARGETED_BODY) {
            if (targetBodyId != null && bodies.containsKey(targetBodyId)) {
                return targetBodyId;
            }
            Integer stickyTgt = stickyHudTargetBodyId;
            if (stickyTgt != null && bodies.containsKey(stickyTgt)) {
                return stickyTgt;
            }
        }
        return resolvePlanMapShipAnchorBodyId();
    }

    /**
     * Physical ship anchor for plan-map ship glyph and Status lat/lon offset — never HUD navigation target alone.
     */
    private Integer resolvePlanMapShipAnchorBodyId() {
        Map<Integer, BodyInfo> bodies = state.getBodies();
        if (bodies == null || bodies.isEmpty()) {
            return null;
        }
        Integer fc = resolveFleetCarrierParkedBodyForAnchor(bodies);
        if (fc != null) {
            return fc;
        }
        boolean hasSurfaceFix = statusLatitude != null && statusLongitude != null
                && statusAltitude != null && statusPlanetRadius != null
                && statusPlanetRadius.doubleValue() > 1.0;
        if (hasSurfaceFix && nearBodyId != null && bodies.containsKey(nearBodyId)) {
            return nearBodyId;
        }
        Integer ap = resolveJournalShipRefBodyId(bodies);
        if (ap != null) {
            return ap;
        }
        Integer drop = supercruiseDropReferenceBodyId;
        if (drop != null && drop.intValue() > 0 && bodies.containsKey(drop)) {
            return drop;
        }
        Integer nb = nearBodyId;
        if (nb != null && nb.intValue() >= 0 && bodies.containsKey(nb)) {
            return nb;
        }
        Integer sticky = lastVisitedNonStarBodyId;
        if (sticky != null && sticky.intValue() > 0 && bodies.containsKey(sticky)) {
            return sticky;
        }
        if (bodies.containsKey(Integer.valueOf(0))) {
            return Integer.valueOf(0);
        }
        int anch = SystemOrbitGeometry.primaryAnchorBodyMapKey(bodies);
        if (bodies.containsKey(Integer.valueOf(anch))) {
            return Integer.valueOf(anch);
        }
        return null;
    }

    /** ApproachBody first, then DSS detailed scan — never used for {@link #nearBodyId} / table proximity outline. */
    private Integer resolveJournalShipRefBodyId(Map<Integer, BodyInfo> bodies) {
        if (bodies == null || bodies.isEmpty()) {
            return null;
        }
        Integer ap = approachReferenceBodyId;
        if (ap != null && ap.intValue() > 0 && bodies.containsKey(ap)) {
            return ap;
        }
        Integer dss = dssDetailedScanReferenceBodyId;
        if (dss != null && dss.intValue() > 0 && bodies.containsKey(dss)) {
            return dss;
        }
        return null;
    }

    /**
     * Approximate distance from the commander to each body centre (Ls), using orbital elements +
     * Status near-body / lat-lon when available.
     */
    private Instant tableDistanceEpoch() {
        if (orbitAnimDemoActive && orbitAnimSimInstant != null) {
            return orbitAnimSimInstant;
        }
        if (orbitAnimFreezeEpoch != null) {
            return orbitAnimFreezeEpoch;
        }
        return Instant.now();
    }

    private static boolean shipCentricDistancesMeaningfullyChanged(Map<Integer, Double> previous,
            Map<Integer, Double> next) {
        if (next == null || next.isEmpty()) {
            return previous != null && !previous.isEmpty();
        }
        if (previous == null || previous.isEmpty()) {
            return true;
        }
        if (previous.size() != next.size()) {
            return true;
        }
        for (Map.Entry<Integer, Double> e : next.entrySet()) {
            Double p = previous.get(e.getKey());
            if (p == null) {
                return true;
            }
            if (Math.round(p.doubleValue()) != Math.round(e.getValue().doubleValue())) {
                return true;
            }
        }
        return false;
    }

    private Map<Integer, Double> computeShipCentricDistancesLs() {
        Map<Integer, BodyInfo> bodies = state.getBodies();
        if (bodies == null || bodies.isEmpty()) {
            return Collections.emptyMap();
        }
        Integer shipRef = resolveCommanderRefBodyId();
        if (shipRef == null) {
            return Collections.emptyMap();
        }
        Map<Integer, double[]> pos = SystemOrbitGeometry.bodyPositionsMetres(bodies, tableDistanceEpoch(),
                freezeBarycentreStarsDuringOrbitAnim());
        double[] ship = SystemOrbitGeometry.shipPositionMetres(
                bodies,
                pos,
                shipRef.intValue(),
                statusLatitude,
                statusLongitude,
                statusAltitude,
                statusPlanetRadius);
        if (ship == null) {
            return Collections.emptyMap();
        }
        return SystemOrbitGeometry.distancesFromPointLs(bodies, ship);
    }

    private void dedupeBodiesByName() {

        Map<Integer, BodyInfo> bodies = state.getBodies();
        if (bodies == null || bodies.isEmpty()) {
            return;
        }

        Map<String, Integer> nameToKey = new HashMap<>();
        List<Integer> keysToRemove = new ArrayList<>();

        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {

            Integer key = e.getKey();
            BodyInfo bi = e.getValue();

            if (bi == null) {
                continue;
            }

            String name = bi.getBodyName();
            if (name == null) {
                continue;
            }

            String canon = name.trim().toLowerCase(Locale.ROOT);
            if (canon.isEmpty()) {
                continue;
            }

            Integer existingKey = nameToKey.get(canon);
            if (existingKey == null) {
                nameToKey.put(canon, key);
                continue;
            }

            BodyInfo keep = bodies.get(existingKey);
            BodyInfo drop = bi;

            if (keep == null) {
                nameToKey.put(canon, key);
                continue;
            }

            // Prefer the entry with a non-negative bodyId as the "keeper"
            // (some paths still create temp/unknown ids during rescan).
            if (keep.getBodyId() < 0 && drop.getBodyId() >= 0) {
                BodyInfo tmp = keep;
                keep = drop;
                drop = tmp;

                // Swap which key is considered the keeper for later duplicates
                nameToKey.put(canon, key);
                keysToRemove.add(existingKey);
            } else {
                keysToRemove.add(key);
            }

            mergeBodiesKeepBest(keep, drop);

            // Useful debug to prove what's happening (leave it in until stable)
//            System.out.println("DEDUP body name='" + name + "' keepId=" + keep.getBodyId()
//                    + " dropId=" + drop.getBodyId());
        }

        for (Integer k : keysToRemove) {
            bodies.remove(k);
        }
    }

    /**
     * Journal {@code Scan} parents ({@code Null:N}, moon hosts) beat EDSM rows that only know the arrival star.
     * EDSM used to store {@code Null} refs as parent id {@code 0}, which then collapsed to star A in the map.
     */
    private static boolean preferImmediateParentForMerge(BodyInfo keep, BodyInfo drop) {
        if (keep == null || drop == null) {
            return false;
        }
        int candidate = drop.getImmediateParentBodyId();
        int current = keep.getImmediateParentBodyId();
        if (candidate < 0) {
            return false;
        }
        if (current < 0) {
            return true;
        }
        if (candidate == current) {
            return false;
        }
        int anchorStar = keep.getParentStarBodyId();
        if (anchorStar < 0) {
            anchorStar = drop.getParentStarBodyId();
        }
        boolean candidateIsNullRef = candidate > 0 && candidate != anchorStar
                && !isLikelyPlanetHostBodyId(candidate);
        boolean currentIsWeak = current == 0 || current == anchorStar;
        return candidateIsNullRef && currentIsWeak;
    }

    /** Planet hosts are usually high journal body ids; Null barycentre refs are small scan row ids. */
    private static boolean isLikelyPlanetHostBodyId(int bodyId) {
        return bodyId >= 12;
    }

    private static void mergeBodiesKeepBest(BodyInfo keep, BodyInfo drop) {

        if (keep == null || drop == null) {
            return;
        }

        if (keep.getStarSystem() == null && drop.getStarSystem() != null) {
            keep.setStarSystem(drop.getStarSystem());
        }
        if (keep.getBodyName() == null && drop.getBodyName() != null) {
            keep.setBodyName(drop.getBodyName());
        }

        if (keep.getStarPos() == null && drop.getStarPos() != null) {
            keep.setStarPos(drop.getStarPos());
        }

        if (Double.isNaN(keep.getDistanceLs()) && !Double.isNaN(drop.getDistanceLs())) {
            keep.setDistanceLs(drop.getDistanceLs());
        }

        if (keep.getGravityMS() == null && drop.getGravityMS() != null) {
            keep.setGravityMS(drop.getGravityMS());
        }
        if (keep.getSurfaceTempK() == null && drop.getSurfaceTempK() != null) {
            keep.setSurfaceTempK(drop.getSurfaceTempK());
        }
        if (keep.getSurfacePressure() == null && drop.getSurfacePressure() != null) {
            keep.setSurfacePressure(drop.getSurfacePressure());
        }

        if (keep.getPlanetClass() == null && drop.getPlanetClass() != null) {
            keep.setPlanetClass(drop.getPlanetClass());
        }
        if (keep.getAtmosphere() == null && drop.getAtmosphere() != null) {
            keep.setAtmosphere(drop.getAtmosphere());
        }
        if (keep.getAtmoOrType() == null && drop.getAtmoOrType() != null) {
            keep.setAtmoOrType(drop.getAtmoOrType());
        }

        if (!keep.isLandable() && drop.isLandable()) {
            keep.setLandable(true);
        }
        if (!keep.hasBio() && drop.hasBio()) {
            keep.setHasBio(true);
        }
        if (!keep.hasGeo() && drop.hasGeo()) {
            keep.setHasGeo(true);
        }

        if (drop.isHighValue()) {
            keep.setHighValue(true);
        }
        Long kCr = keep.getValuableBodyExplorationCredits();
        Long dCr = drop.getValuableBodyExplorationCredits();
        if (dCr != null && (kCr == null || dCr.longValue() > kCr.longValue())) {
            keep.setValuableBodyExplorationCredits(dCr);
        }

        if ((keep.getTerraformState() == null || keep.getTerraformState().isBlank())
                && drop.getTerraformState() != null && !drop.getTerraformState().isBlank()) {
            keep.setTerraformState(drop.getTerraformState());
        }
        if (keep.getMassEm() == null && drop.getMassEm() != null) {
            keep.setMassEm(drop.getMassEm());
        }

        if (preferImmediateParentForMerge(keep, drop)) {
            keep.setImmediateParentBodyId(drop.getImmediateParentBodyId());
        } else if (keep.getImmediateParentBodyId() < 0 && drop.getImmediateParentBodyId() >= 0) {
            keep.setImmediateParentBodyId(drop.getImmediateParentBodyId());
        }

        if (keep.getOrbitalPeriod() == null && drop.getOrbitalPeriod() != null) {
            keep.setOrbitalPeriod(drop.getOrbitalPeriod());
        }
        if (keep.getSemiMajorAxisM() == null && drop.getSemiMajorAxisM() != null) {
            keep.setSemiMajorAxisM(drop.getSemiMajorAxisM());
        }
        if (keep.getEccentricity() == null && drop.getEccentricity() != null) {
            keep.setEccentricity(drop.getEccentricity());
        }
        if (keep.getOrbitalInclination() == null && drop.getOrbitalInclination() != null) {
            keep.setOrbitalInclination(drop.getOrbitalInclination());
        }
        if (keep.getPeriapsis() == null && drop.getPeriapsis() != null) {
            keep.setPeriapsis(drop.getPeriapsis());
        }
        if (keep.getAscendingNode() == null && drop.getAscendingNode() != null) {
            keep.setAscendingNode(drop.getAscendingNode());
        }
        if (keep.getMeanAnomaly() == null && drop.getMeanAnomaly() != null) {
            keep.setMeanAnomaly(drop.getMeanAnomaly());
        }
        if (keep.getOrbitalEpochMillis() == null && drop.getOrbitalEpochMillis() != null) {
            keep.setOrbitalEpochMillis(drop.getOrbitalEpochMillis());
        }

        // numberOfBioSignals uses primitive int (default 0); getter never returns null, so the old
        // null-check merge never copied FSS counts from a duplicate body entry onto the keeper.
        {
            int kb = keep.getNumberOfBioSignals() != null ? keep.getNumberOfBioSignals().intValue() : 0;
            int db = drop.getNumberOfBioSignals() != null ? drop.getNumberOfBioSignals().intValue() : 0;
            if (db > kb) {
                keep.setNumberOfBioSignals(db);
            }
        }

        // Merge observed genus/species if present
        if (drop.getObservedGenusPrefixes() != null) {
            for (String g : drop.getObservedGenusPrefixes()) {
                keep.addObservedGenus(g);
            }
        }
        if (drop.getObservedBioDisplayNames() != null) {
            for (String n : drop.getObservedBioDisplayNames()) {
                keep.addObservedBioDisplayName(n);
            }
        }

        // Keep predictions if keep doesn't have them yet
        if ((keep.getPredictions() == null || keep.getPredictions().isEmpty())
                && drop.getPredictions() != null
                && !drop.getPredictions().isEmpty()) {
            keep.setPredictions(drop.getPredictions());
        }

        if (keep.isPlanetaryBodyForRingDisplay()
                && keep.getRingSummaryLines().isEmpty()
                && !drop.getRingSummaryLines().isEmpty()) {
            keep.setRingSummaryLines(new ArrayList<>(drop.getRingSummaryLines()));
        }
        String kRes = keep.getRingReserveHumanized();
        String dRes = drop.getRingReserveHumanized();
        if (keep.isPlanetaryBodyForRingDisplay()
                && (kRes == null || kRes.isEmpty())
                && dRes != null
                && !dRes.isEmpty()) {
            keep.setRingReserveHumanized(dRes);
        }
    }


    public void applyUiFontPreferences() {
        applyUiFont(OverlayPreferences.getUiFont());
    }

    public void applyUiFont(Font font) {
        if (font == null) {
            return;
        }

        uiFont = font;
        refreshBioIcons();

        // Apply recursively so all labels/etc. stay consistent.
        applyFontRecursively(this, uiFont);

        if (headerLabel != null) {
            headerLabel.setFont(uiFont.deriveFont(Font.BOLD));
        }
        if (headerSummaryLabel != null) {
            headerSummaryLabel.setFont(uiFont.deriveFont(Font.BOLD));
        }
        if (distFromShipButton != null && distFromStarButton != null && distByValueButton != null) {
            distFromShipButton.setForeground(EdoUi.User.MAIN_TEXT);
            distFromStarButton.setForeground(EdoUi.User.MAIN_TEXT);
            distByValueButton.setForeground(EdoUi.User.MAIN_TEXT);
            applyDistanceToggleIcons();
            updateDistModeToggleAppearance();
        }
        if (table != null) {
            table.setFont(uiFont);
            table.setRowHeight(computeRowHeight(table, uiFont, 8));
            if (table.getTableHeader() != null) {
                table.getTableHeader().setFont(uiFont.deriveFont(Font.BOLD));
            }
            SwingUtilities.invokeLater(this::applySystemBodiesTableColumnLayout);
        }
        applyOrbitMapToolbarTypography(
                "Slower: fewer model days per second of real time.",
                "Faster: more model days per second of real time.",
                "Orbit model days advanced per second of real time while playing.");
        EdoMiningSplitPaneUi.applyDividerTheme(systemTableMapSplit);
        revalidate();
        repaint();
    }

    private static void configureSystemTableMapSplit(JSplitPane split, double resizeWeight) {
        split.setOpaque(false);
        split.setBorder(null);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(false);
        split.setDividerSize(9);
        split.setResizeWeight(Math.max(0.05, Math.min(0.95, resizeWeight)));
        split.setDividerLocation(resizeWeight);
    }

    private void saveSystemTableMapSplitRatio() {
        if (systemPlanMapCollapsed || systemTableMapSplit == null || systemTableMapSplit.getHeight() < 32) {
            return;
        }
        double ratio = computeVerticalSplitRatio(systemTableMapSplit);
        OverlayPreferences.setSystemTabPanelTableSplitRatio(ratio);
        systemTableMapSplit.setResizeWeight(ratio);
    }

    private static double computeVerticalSplitRatio(JSplitPane split) {
        if (split == null) {
            return 0.5;
        }
        int h = split.getHeight();
        if (h <= 0) {
            return 0.5;
        }
        int d = split.getDividerSize();
        int usable = Math.max(1, h - d);
        int loc = split.getDividerLocation();
        double r = loc / (double) usable;
        if (r < 0.05) {
            return 0.05;
        }
        if (r > 0.95) {
            return 0.95;
        }
        return r;
    }

    private static void applyFontRecursively(Component c, Font font) {
        if (c == null || font == null) {
            return;
        }

        try {
            c.setFont(font);
        } catch (Exception e) {
            // ignore
        }

        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                applyFontRecursively(child, font);
            }
        }
    }

    private int computeRowHeight(JTable table, Font font, int verticalPaddingPx) {
        if (table == null || font == null) {
            return 24;
        }
        FontMetrics fm = table.getFontMetrics(font);
        int iconHeight = Math.max(
                bioLeafIcon != null ? bioLeafIcon.getIconHeight() : 0,
                Math.max(
                        bioDollarIcon != null ? bioDollarIcon.getIconHeight() : 0,
                        bioGeoIcon != null ? bioGeoIcon.getIconHeight() : 0
                )
        );
        int textHeight = fm.getAscent() + fm.getDescent();
        int h = Math.max(textHeight, iconHeight) + verticalPaddingPx;
        if (h < 24) {
            h = 24;
        }
        return h;
    }

    private void refreshBioIcons() {
        int fontSize = (uiFont != null) ? uiFont.getSize() : 14;
        int leafSize = Math.max(14, Math.round(fontSize * 1.15f));
        bioExpandCuePx = Math.max(10, Math.round(fontSize * 0.7f));
        bioExpandToLeafGapPx = Math.max(4, Math.round(fontSize * 0.32f));
        int dollarSize = Math.max(16, Math.round(fontSize * 1.45f));
        int geoSize = Math.max(14, Math.round(fontSize * 1.35f));
        int sneakerW = Math.max(20, Math.round(fontSize * 1.55f));
        int sneakerH = Math.max(12, Math.round(fontSize * 0.90f));
        bioLeafIcon = new CachedIcon(new LeafIcon(leafSize, leafSize));
        bioDollarIcon = new CachedIcon(new DollarIcon(dollarSize, dollarSize));
        bioGeoIcon = new CachedIcon(new RingedPlanetIcon(geoSize, geoSize));
        int earthSize = Math.max(14, Math.round(fontSize * 1.35f));
        valuableEarthBodyIcon = new CachedIcon(new EarthLikeBodyIcon(earthSize, earthSize));
        // Not wrapped in CachedIcon: sneaker color comes from theme prefs and must repaint when it changes.
        landSneakerIcon = new SneakerIcon(sneakerW, sneakerH);
    }


    public SystemState getState() {
        return state;
    }

    private void injectIntermediateDestinationRow(List<Row> rows) {
        Integer parentId = targetDestinationParentBodyId;
        String name = targetDestinationName;

        if (parentId == null || name == null || name.isBlank()) {
            return;
        }

        int insertAt = -1;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (!r.detail && r.body != null && r.body.getBodyId() == parentId.intValue()) {
                insertAt = i + 1;
                while (insertAt < rows.size() && rows.get(insertAt).detail) {
                    insertAt++;
                }
                break;
            }
        }

        if (insertAt >= 0) {
            rows.add(insertAt, Row.destination(parentId.intValue(), "> " + name));
        }
    }
}