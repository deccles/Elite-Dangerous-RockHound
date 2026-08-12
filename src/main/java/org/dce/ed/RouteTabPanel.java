package org.dce.ed;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.concurrent.ConcurrentHashMap;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.BorderFactory;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import org.dce.ed.exec.ExecBinding;
import org.dce.ed.exec.ExecOverlayButtonSupport;
import org.dce.ed.exec.ExecTriggerId;
import org.dce.ed.exec.ExecTriggerService;
import org.dce.ed.ui.tabdock.OverlayTabId;
import org.dce.ed.ui.OverlayScrollPaneSupport;
import org.dce.ed.ui.OverlayTransparentChrome;
import org.dce.ed.ui.PassThroughScrollSupport;
import org.dce.ed.ui.SelectiveHitSupport;
import org.dce.ed.ui.TransparentViewportUI;

import org.dce.ed.cache.CachedSystem;
import org.dce.ed.cache.CachedSystemSummary;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.session.FleetCarrierSessionMapper;
import org.dce.ed.session.RouteEntryPersisted;
import org.dce.ed.edsm.BodiesResponse;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogFileLocator;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.EliteLogEvent.NavRouteClearEvent;
import org.dce.ed.logreader.EliteLogEvent.NavRouteEvent;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.FssAllBodiesFoundEvent;
import org.dce.ed.logreader.event.IFsdJump;
import org.dce.ed.logreader.event.LoadGameEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.state.SystemState;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.DistanceToggleIcons;
import org.dce.ed.ui.CircularArrowIcon;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.StatusCircleIcon;
import org.dce.ed.ui.SystemTableHoverCopyManager;
import org.dce.ed.util.EdsmClient;
import org.dce.ed.route.FuelScoopStarClass;
import org.dce.ed.route.RouteEntry;
import org.dce.ed.route.RouteFuelPrediction;
import org.dce.ed.route.RouteGeometry;
import org.dce.ed.route.RouteJournalApplyOutcome;
import org.dce.ed.route.RouteMarkerKind;
import org.dce.ed.route.RouteNavRouteJson;
import org.dce.ed.route.RouteScanStatus;
import org.dce.ed.route.RouteDisplaySnapshot;
import org.dce.ed.route.RouteSession;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Route tab that visualizes the current plotted route from NavRoute.json.
 * This is styled to match the Elite Dangerous UI and SystemTabPanel for the
 * overlay: the panel and scrollpane are non-opaque, and all text uses the
 * same orange as SystemTabPanel.
 */
public class RouteTabPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private Font uiFont = OverlayPreferences.getUiFont();
	private static final Icon ICON_FULLY_DISCOVERED_VISITED =
			StatusCircleIcon.check(EdoUi.User.MAIN_TEXT);
	private static final Icon ICON_FULLY_DISCOVERED_NOT_VISITED =
			StatusCircleIcon.check(EdoUi.STATUS_GRAY);
	// Crossed-out eye equivalents when any body is missing discovery.commander.
	private static final Icon ICON_DISCOVERY_MISSING_VISITED =
			StatusCircleIcon.cross(EdoUi.STATUS_BLUE);
	private static final Icon ICON_DISCOVERY_MISSING_NOT_VISITED =
			StatusCircleIcon.cross(EdoUi.STATUS_GRAY);
	// BodyCount mismatch between EDSM bodyCount and the number of bodies returned.
	private static final Icon ICON_BODYCOUNT_MISMATCH_VISITED =
			new StatusCircleIcon(EdoUi.STATUS_YELLOW, "!");
	private static final Icon ICON_BODYCOUNT_MISMATCH_NOT_VISITED =
			new StatusCircleIcon(EdoUi.STATUS_GRAY, "!");
	private static final Icon ICON_UNKNOWN =
			new StatusCircleIcon(EdoUi.STATUS_GRAY, "?");
	/** Monochrome green fuel pump for scoopable stars (Class column). */
	private static final Color FUEL_GAUGE_GREEN = new Color(0x90, 0xC3, 0x8A);
	/** Fuel prediction: last system reachable on current fuel. */
	private static final Color FUEL_PUMP_YELLOW = new Color(0xE8, 0xC5, 0x4A);
	/** Fuel prediction: out of fuel before this system. */
	private static final Color FUEL_PUMP_RED = new Color(0xDC, 0x40, 0x30);
	/** Green = scoopable star class (original meaning). */
	private static final Icon ICON_FUEL_SCOOP = new FuelPumpIcon(FUEL_GAUGE_GREEN, null);
	/** Yellow = last reachable on a scoopable star (e.g. no scoop fitted). */
	private static final Icon ICON_FUEL_LAST_SCOOPABLE = new FuelPumpIcon(FUEL_PUMP_YELLOW, null);
	/** Red = unreachable / beyond range on a scoopable star. */
	private static final Icon ICON_FUEL_OUT_SCOOPABLE = new FuelPumpIcon(FUEL_PUMP_RED, null);
	/** Yellow + red slash = last reachable on a non-scoopable star. */
	private static final Icon ICON_FUEL_LAST_UNSCOOPABLE =
			new FuelPumpIcon(FUEL_PUMP_YELLOW, FUEL_PUMP_RED);
	/** Red + yellow slash = unreachable on a non-scoopable star. */
	private static final Icon ICON_FUEL_OUT_UNSCOOPABLE =
			new FuelPumpIcon(FUEL_PUMP_RED, FUEL_PUMP_YELLOW);
	// Column indexes
	private static final int COL_MARKER    = 0;
	private static final int COL_INDEX    = 1;
	private static final int COL_SYSTEM   = 2;
	private static final int COL_CLASS    = 3;
	private static final int COL_STATUS   = 4;
	private static final int COL_DISTANCE = 5;
	/** Route marker column is fixed width (arrows). */
	private static final int ROUTE_COL_WIDTH_MARKER = 20;
	/** Minimum width for the # column (route index). */
	private static final int ROUTE_COL_MIN_INDEX = 36;
	private static final int ROUTE_COL_PREF_INDEX = 44;
	/** System name shrinks first; this is the smallest useful width before stealing from Class. */
	private static final int ROUTE_COL_MIN_SYSTEM = 40;
	/** Keep status glyphs readable; {@link StatusCircleIcon} scales with UI font. */
	private static final int ROUTE_COL_MIN_STATUS_EXTRA = 12;
	/** Horizontal padding around measured Ly text (renderer borders). */
	private static final int ROUTE_COL_DISTANCE_PAD = 20;
	/** Padding around Class cell content (matches {@link StarClassRenderer} borders + slack). */
	private static final int ROUTE_COL_CLASS_HORIZONTAL_PAD = 10;
	/** Gap between fuel gauge icon and star-type letter in the Class column. */
	private static final int ROUTE_COL_CLASS_ICON_TEXT_GAP = 4;
	/** Keep current system row at this offset from top when auto-scrolling (e.g. one jump = one row scroll). */
	private static final int TARGET_CURRENT_ROW_OFFSET = 4;
	/** Padding above the Exec-script / Clear strip under the route table. */
	private static final int ROUTE_COPY_STRIP_GAP_PX = 6;
	private final JLabel headerLabel;
	/** Title strip: route summary (west) + Ly column mode toggles (east). */
	private final JPanel routeTitleRow;
	private final JButton lyModeFromCurrentButton;
	private final JButton lyModePerLegButton;
	private boolean lyModeFromCurrentHovered;
	private boolean lyModePerLegHovered;
	protected JTable table=null;
	protected JScrollPane routeScrollPane;
	/** Holds {@link #routeScrollPane} and the copy strip (same structure on Route and Fleet Carrier tabs). */
	private final JPanel routeCenterWrapper;
	/**
	 * Right-aligned strip under the table for Exec script buttons (and Fleet Carrier Clear).
	 * The built-in “Copy next destination” control was removed; clipboard copy remains available to
	 * scripts via {@link #copyNextRouteDestinationForExec()} and jump auto-copy.
	 */
	private final JPanel routeCopyStrip;
	/** Red “Custom Route” label + Clear under the table when the route was customized (paste / reorder). */
	private final JPanel customRouteWarningStrip;
	private final JLabel customRouteWarningLabel;
	private final JButton clearCustomRouteButton;
	private final JToggleButton loopCustomRouteButton;
	/** {@code true} after paste/reorder until a game {@code NavRoute} reload or explicit clear. */
	private boolean customRouteActive;
	private final List<JButton> execTabButtons = new ArrayList<>();
	private ExecTriggerService execTriggerService;
	private final RouteTableModel tableModel;
	private SystemTableHoverCopyManager systemTableHoverCopyManager;
	private StatusHoverPopupManager statusHoverPopupManager;
	private StatusHoverPopupManager fuelHoverPopupManager;
	private final EdsmClient edsmClient;
	/** Display-row index being dragged for reorder; {@code -1} when idle. */
	private int routeDragFromDisplayRow = -1;
	/** Base-route index captured at press (avoids remapping after display rebuilds). */
	private int routeDragFromBaseIndex = -1;
	/** Insertion line as a display-row index ({@code 0..rowCount}); {@code -1} when hidden. */
	private int routeDropInsertDisplayRow = -1;
	private boolean routeDragArmed;
	private boolean routeDragActive;
	private Point routeDragStartPoint;
	private final BooleanSupplier passThroughEnabledSupplier;
	// Caches coordinates we resolved from EDSM (used for inserting synthetic rows).
	private final java.util.Map<String, Double[]> resolvedCoordsCache = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<String> edsmCoordsFetchInProgress = java.util.concurrent.ConcurrentHashMap.newKeySet();
	// Route fuel prediction: ship/FSD snapshot from Loadout, live fuel/cargo from Status.
	private volatile RouteFuelPrediction.ShipFuelProfile shipFuelProfile;
	private volatile double shipFuelMainTons = Double.NaN;
	private volatile double shipCargoTons = 0.0;
	/** Result indexes match {@link #tableModel} rows; null = prediction off / no data. */
	private RouteFuelPrediction.Result routeFuelPrediction;
	private boolean jumpFlashOn = true;
	private final Timer jumpFlashTimer = new Timer(500, e -> {
		jumpFlashOn = !jumpFlashOn;
		table.repaint();
	});
	private final org.dce.ed.route.RouteJumpFlashHandle routeJumpFlashHandle = new org.dce.ed.route.RouteJumpFlashHandle() {
		@Override
		public boolean isTimerRunning() {
			return jumpFlashTimer.isRunning();
		}
		@Override
		public void startTimer() {
			jumpFlashOn = true;
			jumpFlashTimer.start();
		}
		@Override
		public void stopTimer() {
			jumpFlashTimer.stop();
			jumpFlashOn = true;
		}
	};
	protected final RouteSession routeSession = new RouteSession(routeJumpFlashHandle, this::shouldUpdateOnCarrierJump);

	/**
	 * Next plotted route system after the current system; {@code null} if unknown or at end.
	 * Used by route-adjacent tabs (e.g. Fleet Carrier) for clipboard copy.
	 */
	public static String nextRouteDestinationSystemName(RouteSession session) {
		if (session == null) {
			return null;
		}
		return nextRouteDestinationSystemName(
				session.getBaseRouteEntries(),
				session.getCurrentSystemName(),
				session.getCurrentSystemAddress(),
				session.getCurrentBaseIndex());
	}

	public static String nextRouteDestinationSystemName(RouteSession session,
			boolean customRouteActive,
			boolean loopEnabled) {
		String next = nextRouteDestinationSystemName(session);
		if (next != null || session == null || !customRouteActive || !loopEnabled) {
			return next;
		}
		List<RouteEntry> entries = session.getBaseRouteEntries();
		if (entries == null || entries.size() < 2 || session.getCurrentBaseIndex() != entries.size() - 1) {
			return null;
		}
		RouteEntry first = entries.get(0);
		return first != null && !first.isBodyRow && first.systemName != null && !first.systemName.isBlank()
				? first.systemName.trim()
				: null;
	}

	/**
	 * Next hop after {@code currentName}/{@code currentAddress} on the given route list.
	 * Prefer the live commander position when the session current can lag one hop behind.
	 */
	public static String nextRouteDestinationSystemName(List<RouteEntry> entries,
			String currentName,
			long currentAddress) {
		return nextRouteDestinationSystemName(entries, currentName, currentAddress, 0);
	}

	/**
	 * Next hop after the CURRENT occurrence at/after {@code fromIndexInclusive}.
	 * Used so custom-route loops do not snap next-dest back to the first duplicate.
	 */
	public static String nextRouteDestinationSystemName(List<RouteEntry> entries,
			String currentName,
			long currentAddress,
			int fromIndexInclusive) {
		if (entries == null || entries.isEmpty()) {
			return null;
		}
		int row = RouteGeometry.findSystemRowFrom(entries, currentName, currentAddress, fromIndexInclusive);
		if (row < 0) {
			row = RouteGeometry.findSystemRow(entries, currentName, currentAddress);
		}
		int start = row + 1;
		if (row < 0) {
			start = 0;
		}
		for (int i = start; i < entries.size(); i++) {
			RouteEntry e = entries.get(i);
			if (e == null || e.isBodyRow || e.systemName == null || e.systemName.isBlank()) {
				continue;
			}
			if (isSameRouteSystem(e, currentName, currentAddress)) {
				continue;
			}
			return e.systemName.trim();
		}
		return null;
	}

	static boolean isSameRouteSystem(RouteEntry entry, String systemName, long systemAddress) {
		if (entry == null) {
			return false;
		}
		if (systemAddress != 0L && entry.systemAddress != 0L && entry.systemAddress == systemAddress) {
			return true;
		}
		return systemName != null && !systemName.isBlank() && systemName.equals(entry.systemName);
	}

	private final Map<Long, RouteScanStatus> lastKnownScanStatusByAddress = new ConcurrentHashMap<>();
	private final Map<Long, EdsmScanSummary> edsmSummaryByAddress = new ConcurrentHashMap<>();
	/** Dedupe concurrent EDSM body fetches per system address while updating route row status. */
	private final Set<Long> edsmRouteStatusInFlight = ConcurrentHashMap.newKeySet();

	/** Same-package test access (not part of public API). */
	RouteSession routeSessionForTests() {
		return routeSession;
	}

	/** Live route session for exec placeholders and adjacent tabs. */
	public RouteSession getRouteSession() {
		return routeSession;
	}

	/** EDSM client used for route resolution (subclasses may reuse for autocomplete, etc.). */
	protected EdsmClient edsmClient() {
		return edsmClient;
	}

	/** Optional callback when route state changes (for debounced session persist). */
	private Runnable sessionStateChangeCallback;

	/**
	 * Live commander position from the System tab. Prefer this over a stale session
	 * {@code currentSystem*} that can lag one hop when an {@code FSDJump}/{@code Location} was missed
	 * or restored from persistence before journal reconcile finished.
	 */
	private Supplier<SystemState> liveSystemStateSupplier;

	public void setSessionStateChangeCallback(Runnable callback) {
		this.sessionStateChangeCallback = callback;
	}

	/** Ship Route tab: System tab state. Fleet Carrier overrides reconciliation and should not set this. */
	public void setLiveSystemStateSupplier(Supplier<SystemState> liveSystemStateSupplier) {
		this.liveSystemStateSupplier = liveSystemStateSupplier;
	}

	protected void fireSessionStateChanged() {
		if (sessionStateChangeCallback != null) {
			sessionStateChangeCallback.run();
		}
	}

	/** Fill route-related fields of the given session state (for save). */
	public void fillSessionState(EdoSessionState state) {
		if (state == null) {
			return;
		}
		RoutePersistenceAdapter.fillEdoSession(state, routeSession.toPersistenceSnapshot());
		if (customRouteActive && routeSession != null && !routeSession.getBaseRouteEntries().isEmpty()) {
			state.setCustomRouteActive(Boolean.TRUE);
			List<RouteEntryPersisted> rows = new ArrayList<>();
			for (RouteEntry e : routeSession.getBaseRouteEntries()) {
				RouteEntryPersisted p = FleetCarrierSessionMapper.toPersisted(e);
				if (p != null) {
					rows.add(p);
				}
			}
			state.setCustomRouteEntries(rows);
		} else {
			state.setCustomRouteActive(Boolean.FALSE);
			state.setCustomRouteEntries(null);
		}
	}

	/** Apply persisted route state (for restore on startup). */
	/**
	 * Mouse pass-through: apply global wheel to the route table scroller when the pointer is over it and the
	 * vertical bar is visible.
	 */
	public boolean applyPassThroughWheelIfHit(int screenX, int screenY, int wheelRotation) {
		return PassThroughScrollSupport.applyVerticalWheelIfHit(routeScrollPane, screenX, screenY, wheelRotation);
	}

	public boolean isPointerOverScrollBar(Point screenPoint) {
		return OverlayScrollPaneSupport.isPointerOverScrollBar(routeScrollPane, screenPoint);
	}

	/** Selective mouse mode: distance toggles, route table (paste / drag reorder / copy), Exec strip. */
	public boolean isPointerOverInteractiveRegion(Point screenPoint) {
		if (isRouteReorderGestureActive()) {
			return true;
		}
		if (SelectiveHitSupport.containsScreenPoint(lyModeFromCurrentButton, screenPoint)) {
			return true;
		}
		if (SelectiveHitSupport.containsScreenPoint(lyModePerLegButton, screenPoint)) {
			return true;
		}
		if (SelectiveHitSupport.containsScreenPoint(table, screenPoint)) {
			return true;
		}
		if (ExecOverlayButtonSupport.anyButtonContains(execTabButtons, screenPoint)) {
			return true;
		}
		if (clearCustomRouteButton != null && clearCustomRouteButton.isVisible()
				&& SelectiveHitSupport.containsScreenPoint(clearCustomRouteButton, screenPoint)) {
			return true;
		}
		if (loopCustomRouteButton != null && loopCustomRouteButton.isVisible()
				&& SelectiveHitSupport.containsScreenPoint(loopCustomRouteButton, screenPoint)) {
			return true;
		}
		return routeCopyStrip != null && routeCopyStrip.isVisible()
				&& SelectiveHitSupport.containsScreenPoint(routeCopyStrip, screenPoint);
	}

	/** True while a route-row drag is armed or in progress (keeps mouse pass-through disabled). */
	public boolean isRouteReorderGestureActive() {
		return routeDragArmed || routeDragActive || routeDropInsertDisplayRow >= 0;
	}

	public void applySessionState(EdoSessionState state) {
		if (state == null) {
			return;
		}
		// Restore custom hops before the nav snapshot so currentBaseIndex clamps against the list.
		if (Boolean.TRUE.equals(state.getCustomRouteActive())
				&& !state.customRouteEntriesOrEmpty().isEmpty()) {
			List<RouteEntry> entries = new ArrayList<>();
			for (RouteEntryPersisted p : state.customRouteEntriesOrEmpty()) {
				RouteEntry e = FleetCarrierSessionMapper.fromPersisted(p);
				if (e != null) {
					entries.add(e);
				}
			}
			if (!entries.isEmpty()) {
				routeSession.replaceBaseRouteEntries(entries);
				setCustomRouteActive(true);
				setHeaderLabelText(routeJumpHeader(entries));
			} else {
				setCustomRouteActive(false);
			}
		} else {
			setCustomRouteActive(false);
		}
		routeSession.applyPersistenceSnapshot(RoutePersistenceAdapter.fromEdoSession(state));
		if (state.getCurrentSystemName() != null && !state.getCurrentSystemName().isBlank()) {
			routeSession.setCurrentSystemName(state.getCurrentSystemName());
		}
		reconcileRouteCurrentWithPostRescanCache();
		reconcileRouteDestinationWithStatusSnapshot();
		rebuildDisplayedEntries();
	}

	public RouteTabPanel() {
		this(null);
	}

	public RouteTabPanel(BooleanSupplier passThroughEnabledSupplier) {
		super(new BorderLayout());
		setOpaque(false);
		this.passThroughEnabledSupplier = passThroughEnabledSupplier;
		this.edsmClient = new EdsmClient();
		headerLabel = new JLabel("Route: (no data)");
		headerLabel.setForeground(EdoUi.User.MAIN_TEXT);
		headerLabel.setBorder(new EmptyBorder(4, 4, 4, 4));
		headerLabel.setFont(uiFont.deriveFont(Font.BOLD));
		tableModel = new RouteTableModel();

		routeTitleRow = new JPanel(new BorderLayout(0, 0));
		routeTitleRow.setOpaque(false);
		routeTitleRow.add(headerLabel, BorderLayout.WEST);
		JPanel lyToggleEast = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		lyToggleEast.setOpaque(false);
		lyModeFromCurrentButton = new JButton(new DistanceToggleIcons.RingAndDotIcon(18));
		lyModePerLegButton = new JButton(new DistanceToggleIcons.LinkedNodesIcon(18));
		configureLyModeToggleButton(lyModeFromCurrentButton, "Show distance from your current system along the route");
		configureLyModeToggleButton(lyModePerLegButton, "Show each jump length from the previous system");
		lyModeFromCurrentButton.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				lyModeFromCurrentHovered = true;
				updateLyModeToggleAppearance();
			}

			@Override
			public void mouseExited(MouseEvent e) {
				lyModeFromCurrentHovered = false;
				updateLyModeToggleAppearance();
			}
		});
		lyModePerLegButton.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				lyModePerLegHovered = true;
				updateLyModeToggleAppearance();
			}

			@Override
			public void mouseExited(MouseEvent e) {
				lyModePerLegHovered = false;
				updateLyModeToggleAppearance();
			}
		});
		lyModeFromCurrentButton.addActionListener(e -> {
			tableModel.setLyFromCurrentSystem(true);
			updateLyModeToggleAppearance();
		});
		lyModePerLegButton.addActionListener(e -> {
			tableModel.setLyFromCurrentSystem(false);
			updateLyModeToggleAppearance();
		});
		lyToggleEast.add(lyModeFromCurrentButton);
		lyToggleEast.add(lyModePerLegButton);
		routeTitleRow.add(lyToggleEast, BorderLayout.EAST);
		updateLyModeToggleAppearance();
		table = new JTable(tableModel) {
			private static final long serialVersionUID = 1L;
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
			@Override
			public boolean editCellAt(int row, int column, EventObject e) {
				// Hard-disable editing. Some LAF / editor paths can still try to start an editor,
				// and with a non-opaque table that can look like rows "disappear".
				return false;
			}
			@Override
			protected void configureEnclosingScrollPane() {
				super.configureEnclosingScrollPane();
				// LAF can install a shadow/outline for tables inside scroll panes.
				// Clear it AFTER JTable is actually attached to the JScrollPane.
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
				TransparentViewportUI.clearBelowTableRowsInSelectiveMode(g, this);
				paintRouteRowDropLine(g);
			}
		};
		// Belt-and-suspenders: remove editors so nothing can ever enter edit mode.
		table.setDefaultEditor(Object.class, null);
		table.setDefaultEditor(String.class, null);
		// Focusable for Ctrl+V paste; editing stays disabled. Selection stays off (drag uses custom gesture).
		table.setFocusable(true);
		table.setRowSelectionAllowed(false);
		table.setColumnSelectionAllowed(false);
		table.setCellSelectionEnabled(false);
		table.setSurrendersFocusOnKeystroke(false);
		table.putClientProperty("JTable.autoStartsEdit", Boolean.FALSE);
		table.setOpaque(false);
		table.setBorder(null);
		table.setFillsViewportHeight(true);
		table.setShowGrid(false);
		table.setRowHeight(computeRowHeight(table, uiFont, 6));
		table.setForeground(EdoUi.User.MAIN_TEXT);
		table.setBackground(EdoUi.Internal.TRANSPARENT);
		table.setSelectionForeground(Color.BLACK);
		table.setSelectionBackground(EdoUi.Internal.WHITE_ALPHA_64);
		table.setFont(uiFont);
		table.getTableHeader().setReorderingAllowed(false);
		table.getTableHeader().setResizingAllowed(true);
		JTableHeader routeHeader = table.getTableHeader();
		routeHeader.setOpaque(false);
		routeHeader.setForeground(EdoUi.User.MAIN_TEXT);
		routeHeader.setBackground(EdoUi.Internal.TRANSPARENT);
		routeHeader.setFont(uiFont.deriveFont(Font.BOLD));
		routeHeader.setDefaultRenderer(new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;
			@Override
			public Component getTableCellRendererComponent(JTable tbl,
					Object value,
					boolean isSelected,
					boolean hasFocus,
					int row,
					int column) {
				JLabel l = (JLabel) super.getTableCellRendererComponent(tbl, value, false, false, row, column);
				l.setOpaque(false);
				l.setBackground(EdoUi.Internal.TRANSPARENT);
				l.setForeground(EdoUi.User.MAIN_TEXT);
				l.setFont(uiFont.deriveFont(Font.BOLD));
				l.setBorder(new EmptyBorder(3, 4, 3, 4));
				return l;
			}
		});
		// Default renderer that gives us consistent orange text + padding
		DefaultTableCellRenderer defaultRenderer = new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;
			{
				setOpaque(false);
				setForeground(EdoUi.User.MAIN_TEXT);
			}
			@Override
			public Component getTableCellRendererComponent(JTable tbl,
					Object value,
					boolean isSelected,
					boolean hasFocus,
					int row,
					int column) {
				Component c = super.getTableCellRendererComponent(tbl,
						value,
						false,
						false,
						row,
						column);
				if (c instanceof JComponent) {
					((JComponent) c).setOpaque(false);
				}
				c.setBackground(EdoUi.Internal.TRANSPARENT);
				if (c instanceof JLabel) {
					c.setForeground(EdoUi.User.MAIN_TEXT);
					// Add a bit of vertical padding for readability
					((JLabel) c).setBorder(new EmptyBorder(3, 4, 3, 4));
				}
				return c;
			}
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
						RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);
				super.paintComponent(g2);
				// ED_ORANGE separator line at the bottom of each row
				g2.setColor(EdoUi.ED_ORANGE_TRANS);
				int y = getHeight() - 1;
				g2.drawLine(0, y, getWidth(), y);
				g2.dispose();
			}
		};
		table.setDefaultRenderer(Object.class, defaultRenderer);
		table.getColumnModel().getColumn(COL_MARKER).setCellRenderer(new MarkerRenderer());
		table.getColumnModel().getColumn(COL_MARKER).setMinWidth(ROUTE_COL_WIDTH_MARKER);
		table.getColumnModel().getColumn(COL_MARKER).setMaxWidth(ROUTE_COL_WIDTH_MARKER);
		table.getColumnModel().getColumn(COL_MARKER).setPreferredWidth(ROUTE_COL_WIDTH_MARKER);
		// System column needs indentation support for synthetic destination-body rows.
		table.getColumnModel().getColumn(COL_SYSTEM).setCellRenderer(new SystemNameRenderer());
		table.getColumnModel().getColumn(COL_CLASS).setCellRenderer(new StarClassRenderer());

		// Status column uses a special renderer for the check / ? glyphs
		table.getColumnModel()
		.getColumn(COL_STATUS)
		.setCellRenderer(new StatusRenderer());
		// Distance column right-aligned
		DefaultTableCellRenderer distanceRenderer = new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;
			{
				setOpaque(false);
				setHorizontalAlignment(SwingConstants.RIGHT);
				setForeground(EdoUi.User.MAIN_TEXT);
			}
			@Override
			public Component getTableCellRendererComponent(JTable tbl,
					Object value,
					boolean isSelected,
					boolean hasFocus,
					int row,
					int column) {
				Component c = super.getTableCellRendererComponent(tbl,
						value,
						false,
						false,
						row,
						column);
				if (c instanceof JComponent) {
					((JComponent) c).setOpaque(false);
				}
				c.setBackground(EdoUi.Internal.TRANSPARENT);
				c.setForeground(EdoUi.User.MAIN_TEXT);
				if (c instanceof JLabel) {
					// Slight right padding for numbers
					((JLabel) c).setBorder(new EmptyBorder(3, 4, 3, 8));
				}
				return c;
			}
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
						RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);
				super.paintComponent(g2);
				g2.setColor(EdoUi.ED_ORANGE_TRANS);
				int y = getHeight() - 1;
				g2.drawLine(0, y, getWidth(), y);
				g2.dispose();
			}
		};
		table.getColumnModel()
		.getColumn(COL_DISTANCE)
		.setCellRenderer(distanceRenderer);
		// Column widths: layout is driven by the viewport (see applyRouteTableColumnLayout).
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		configureRouteTableColumnResizePolicy();
		routeScrollPane = new JScrollPane(table);
		routeScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		// Clears LAF scroll-pane chrome that can read as a dark/black frame on transparent overlays.
		OverlayTransparentChrome.configureScrollPane(routeScrollPane);
		if (routeScrollPane.getViewport() != null) {
			installViewportScrollListener(routeScrollPane.getViewport());
			installRouteTableColumnViewportListener(routeScrollPane.getViewport());
		}
		JTableHeader th = table.getTableHeader();
		if (th != null) {
			th.setBorder(null);
		}
		if (routeScrollPane.getVerticalScrollBar() != null) {
			routeScrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(9, Integer.MAX_VALUE));
		}
		routeScrollPane.getVerticalScrollBar().setUnitIncrement(16);

		routeCenterWrapper = new JPanel(null) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isOptimizedDrawingEnabled() {
				return true;
			}

			@Override
			public Dimension getPreferredSize() {
				Dimension tablePref = preferredRouteTableSize();
				int stripH = preferredRouteCopyStripHeight();
				int warnH = preferredCustomRouteWarningHeight();
				int stripW = (routeCopyStrip != null && routeCopyStrip.isVisible())
						? routeCopyStrip.getPreferredSize().width
						: 0;
				int w = Math.max(tablePref.width, stripW);
				return new Dimension(w, tablePref.height + warnH + stripH);
			}

			@Override
			public Dimension getMinimumSize() {
				return new Dimension(120, preferredRouteCopyStripHeight() + preferredCustomRouteWarningHeight() + 40);
			}

			@Override
			public void doLayout() {
				layoutRouteTableAndCopyStrip();
			}
		};
		routeCenterWrapper.setOpaque(false);
		routeCenterWrapper.setBackground(EdoUi.Internal.TRANSPARENT);

		customRouteWarningLabel = new JLabel("Custom Route");
		customRouteWarningLabel.setOpaque(false);
		customRouteWarningLabel.setForeground(EdoUi.User.ERROR);
		customRouteWarningLabel.setFont(uiFont.deriveFont(Font.BOLD));
		customRouteWarningLabel.setBorder(new EmptyBorder(0, 0, 0, 0));

		clearCustomRouteButton = new JButton("Clear");
		OverlayOutlineButtonStyle.applyPrimaryHitSafeCompact(clearCustomRouteButton, uiFont);
		clearCustomRouteButton.setToolTipText(
				"Clear the custom route and reload NavRoute.json, or show the current system if none is plotted");
		clearCustomRouteButton.addActionListener(e -> clearCustomRoute());

		loopCustomRouteButton = new JToggleButton(new CircularArrowIcon(16));
		loopCustomRouteButton.setToolTipText("Loop");
		loopCustomRouteButton.setSelected(OverlayPreferences.isCustomRouteLoopEnabled());
		styleLoopCustomRouteButton();
		loopCustomRouteButton.addActionListener(e -> {
			OverlayPreferences.setCustomRouteLoopEnabled(loopCustomRouteButton.isSelected());
			styleLoopCustomRouteButton();
			fireSessionStateChanged();
		});

		customRouteWarningStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		customRouteWarningStrip.setOpaque(false);
		customRouteWarningStrip.setBackground(EdoUi.Internal.TRANSPARENT);
		customRouteWarningStrip.setBorder(new EmptyBorder(4, 4, 0, 4));
		customRouteWarningStrip.add(customRouteWarningLabel);
		if (supportsCustomRouteLoop()) {
			customRouteWarningStrip.add(loopCustomRouteButton);
		}
		customRouteWarningStrip.add(clearCustomRouteButton);
		customRouteWarningStrip.setVisible(false);

		// Right-justified under the last table row; hosts Exec script buttons (and Fleet Carrier Clear).
		routeCopyStrip = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		routeCopyStrip.setOpaque(false);
		routeCopyStrip.setBorder(new EmptyBorder(ROUTE_COPY_STRIP_GAP_PX, 4, 4, 4));
		routeCopyStrip.setVisible(false);

		routeCenterWrapper.add(routeScrollPane);
		routeCenterWrapper.add(customRouteWarningStrip);
		routeCenterWrapper.add(routeCopyStrip);

		add(routeTitleRow, BorderLayout.NORTH);
		add(routeCenterWrapper, BorderLayout.CENTER);

		table.setAlignmentX(Component.LEFT_ALIGNMENT);
		JViewport vpRoute = routeScrollPane.getViewport();
		if (vpRoute != null) {
			vpRoute.setViewPosition(new Point(0, 0));
		}

		routeScrollPane.setColumnHeaderView(null);
		table.setTableHeader(null);

		SwingUtilities.invokeLater(this::applyRouteTableColumnLayout);
		// Copy-to-clipboard: hover only in pass-through mode; double-click always copies.
		systemTableHoverCopyManager = new SystemTableHoverCopyManager(table, COL_SYSTEM, passThroughEnabledSupplier);
		systemTableHoverCopyManager.start();
		// Status hover popup: works in both pass-through and non-pass-through modes,
		// and only when hovering directly over the status symbol column.
		statusHoverPopupManager = new StatusHoverPopupManager(COL_STATUS, modelRow -> {
			RouteEntry entry = tableModel.getEntries(modelRow);
			return entry != null ? buildStatusHoverHtml(entry) : null;
		});
		statusHoverPopupManager.start();
		// Fuel pump hover popup on the Class column (tooltips never fire in pass-through/hybrid).
		fuelHoverPopupManager = new StatusHoverPopupManager(COL_CLASS, this::buildFuelHoverHtml);
		fuelHoverPopupManager.start();
		if (routeFuelPredictionApplies()) {
			bootstrapShipFuelProfileFromJournals();
		}
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (routeDragActive) {
					return;
				}
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
				if (!SwingUtilities.isLeftMouseButton(e)) {
					return;
				}
				table.requestFocusInWindow();
				int viewRow = table.rowAtPoint(e.getPoint());
				int fromBase = baseIndexForDisplayRow(viewRow);
				if (viewRow < 0 || fromBase < 0) {
					clearRouteDragState();
					return;
				}
				routeDragArmed = true;
				routeDragActive = false;
				routeDragFromDisplayRow = viewRow;
				routeDragFromBaseIndex = fromBase;
				routeDragStartPoint = e.getPoint();
				routeDropInsertDisplayRow = -1;
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				boolean wasDragging = routeDragActive && routeDragFromBaseIndex >= 0 && routeDropInsertDisplayRow >= 0;
				int fromBase = routeDragFromBaseIndex;
				int insertDisplay = routeDropInsertDisplayRow;
				clearRouteDragState();
				table.setCursor(Cursor.getDefaultCursor());
				table.repaint();
				if (!wasDragging) {
					return;
				}
				applyRouteRowReorderFromBase(fromBase, insertDisplay);
			}
		});
		table.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				if (!routeDragArmed || routeDragFromBaseIndex < 0 || routeDragStartPoint == null) {
					return;
				}
				int dx = e.getX() - routeDragStartPoint.x;
				int dy = e.getY() - routeDragStartPoint.y;
				if (!routeDragActive && (dx * dx + dy * dy) < 25) {
					return;
				}
				routeDragActive = true;
				table.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
				int insertAt = dropInsertDisplayRowAt(e.getPoint());
				if (insertAt != routeDropInsertDisplayRow) {
					routeDropInsertDisplayRow = insertAt;
					table.repaint();
				}
			}
		});
		installRouteTablePasteBinding();

		reloadFromNavRouteFile();
	}

	/** Natural pixel height of the route table contents (all rows), not the scroll viewport. */
	private Dimension preferredRouteTableSize() {
		if (table == null) {
			return new Dimension(200, 80);
		}
		int rows = Math.max(0, table.getRowCount());
		int rowH = Math.max(1, table.getRowHeight());
		int tableH = rows * rowH;
		// Prefer the table's own preferred width so columns aren't clipped early.
		Dimension tablePref = table.getPreferredSize();
		int w = tablePref != null ? Math.max(120, tablePref.width) : 200;
		return new Dimension(w, tableH);
	}

	/**
	 * Place the Exec/Clear strip snug under the last table row when the table is short; when the table
	 * needs a scrollbar, keep the strip pinned to the bottom of the visible panel.
	 */
	/** Immediate strip/table bounds after row-count changes (body insert/remove). */
	private void relayoutRouteCenterAfterRowChange() {
		if (routeCenterWrapper == null) {
			return;
		}
		if (table != null) {
			table.revalidate();
		}
		if (routeScrollPane != null) {
			routeScrollPane.revalidate();
		}
		routeCenterWrapper.invalidate();
		routeCenterWrapper.revalidate();
		if (routeCenterWrapper.getWidth() > 0 && routeCenterWrapper.getHeight() > 0) {
			layoutRouteTableAndCopyStrip();
		}
		java.awt.Container parent = routeCenterWrapper.getParent();
		if (parent != null) {
			parent.revalidate();
		}
		routeCenterWrapper.repaint();
	}

	private void layoutRouteTableAndCopyStrip() {
		if (routeCenterWrapper == null || routeScrollPane == null || routeCopyStrip == null) {
			return;
		}
		int w = routeCenterWrapper.getWidth();
		int h = routeCenterWrapper.getHeight();
		if (w <= 0 || h <= 0) {
			return;
		}
		int stripH = preferredRouteCopyStripHeight();
		int warnH = preferredCustomRouteWarningHeight();
		int maxTableH = Math.max(0, h - stripH - warnH);
		int contentH = preferredRouteTableSize().height;
		int tableH = Math.min(contentH, maxTableH);
		routeScrollPane.setBounds(0, 0, w, tableH);
		if (customRouteWarningStrip != null) {
			customRouteWarningStrip.setBounds(0, tableH, w, warnH);
		}
		routeCopyStrip.setBounds(0, tableH + warnH, w, stripH);
	}

	private int preferredRouteCopyStripHeight() {
		if (routeCopyStrip == null || !routeCopyStrip.isVisible()) {
			return 0;
		}
		Dimension stripPref = routeCopyStrip.getPreferredSize();
		return Math.max(stripPref != null ? stripPref.height : 0, 36);
	}

	private int preferredCustomRouteWarningHeight() {
		if (customRouteWarningStrip == null || !customRouteWarningStrip.isVisible()) {
			return 0;
		}
		Dimension pref = customRouteWarningStrip.getPreferredSize();
		return Math.max(pref != null ? pref.height : 0, 18);
	}

	/** Shows or hides the red “Custom Route” warning + Clear under the table. */
	protected void setCustomRouteActive(boolean active) {
		customRouteActive = active;
		if (customRouteWarningStrip == null) {
			return;
		}
		boolean show = active && routeSession != null && !routeSession.getBaseRouteEntries().isEmpty();
		if (customRouteWarningStrip.isVisible() == show) {
			if (routeCenterWrapper != null) {
				routeCenterWrapper.revalidate();
				routeCenterWrapper.repaint();
			}
			return;
		}
		customRouteWarningStrip.setVisible(show);
		if (routeCenterWrapper != null) {
			routeCenterWrapper.revalidate();
			routeCenterWrapper.repaint();
		}
	}

	/**
	 * Drops a paste/reorder custom list: reload {@code NavRoute.json} when present, otherwise seed the
	 * current system as the solitary “no plotted route” row.
	 */
	protected void clearCustomRoute() {
		replaceCustomRouteFromGamePlot();
		seedCurrentSystemIfRouteEmptyAfterCustomClear();
		fireSessionStateChanged();
	}

	/**
	 * Force-reloads the game plot over any custom list ({@code NavRoute.json}, or empty when missing).
	 * Overridable in tests to avoid touching a live journal directory.
	 */
	protected void replaceCustomRouteFromGamePlot() {
		reloadFromNavRouteFile(true, true);
	}

	/** When Clear left no plotted hops, keep a one-row “you are here” placeholder. */
	void seedCurrentSystemIfRouteEmptyAfterCustomClear() {
		if (routeSession == null || !routeSession.getBaseRouteEntries().isEmpty()) {
			return;
		}
		reconcileRouteCurrentWithLiveCommanderPosition();
		String name = routeSession.getCurrentSystemName();
		long addr = routeSession.getCurrentSystemAddress();
		if (name == null || name.isBlank()) {
			resolveCurrentSystemFromJournal();
			name = routeSession.getCurrentSystemName();
			addr = routeSession.getCurrentSystemAddress();
		}
		routeSession.ensureSingleSystemRowIfBaseEmpty(name, addr);
		rebuildDisplayedEntries();
	}

	/** {@code true} when the Route tab is showing a paste/reorder custom route. */
	protected boolean isCustomRouteActive() {
		return customRouteActive;
	}

	/** Ship custom routes can loop; fleet-carrier routes retain their existing terminal behavior. */
	protected boolean supportsCustomRouteLoop() {
		return true;
	}

	private void styleLoopCustomRouteButton() {
		OverlayOutlineButtonStyle.applyChipHitSafe(
				loopCustomRouteButton, uiFont, loopCustomRouteButton.isSelected());
		loopCustomRouteButton.repaint();
	}

	JToggleButton loopButtonForTests() {
		return loopCustomRouteButton;
	}

	JButton clearButtonForTests() {
		return clearCustomRouteButton;
	}

	JPanel customRouteWarningStripForTests() {
		return customRouteWarningStrip;
	}

	/**
	 * Selective (hybrid) mode: punch chrome around the Exec/Clear strip and everything below
	 * that row fully transparent (same idea as Control Panel’s Kill scripts strip).
	 */
	@Override
	public void paint(Graphics g) {
		super.paint(g);
		clearAroundRouteCopyStripInSelectiveMode(g);
	}

	private void clearAroundRouteCopyStripInSelectiveMode(Graphics g) {
		if (g == null || !TransparentViewportUI.isSelectivePassThroughContext(this)) {
			return;
		}
		if (routeCopyStrip == null || !routeCopyStrip.isShowing() || !routeCopyStrip.isVisible()) {
			return;
		}
		// Punch strip padding / gaps so only strip buttons (and their fills) remain visible.
		TransparentViewportUI.clearPanelChromeExceptButtons(g, this, routeCopyStrip);
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
			Point rowBottom = SwingUtilities.convertPoint(routeCopyStrip, 0, routeCopyStrip.getHeight(), this);
			int yStart = Math.max(0, rowBottom.y);
			int yEnd = getHeight();
			if (yEnd > yStart) {
				g2.fillRect(0, yStart, getWidth(), yEnd - yStart);
			}
		} finally {
			g2.dispose();
		}
	}

	/** Inserts a control at the left end of the copy strip (e.g. Fleet Carrier's Clear button). */
	protected void addCopyStripComponentLeft(Component c) {
		if (routeCopyStrip != null && c != null) {
			routeCopyStrip.add(c, 0);
			updateRouteCopyStripVisibility();
			routeCopyStrip.revalidate();
		}
	}

	/** Shows the strip only when it hosts Exec buttons and/or Fleet Carrier Clear. */
	private void updateRouteCopyStripVisibility() {
		if (routeCopyStrip == null) {
			return;
		}
		boolean show = routeCopyStrip.getComponentCount() > 0;
		if (routeCopyStrip.isVisible() != show) {
			routeCopyStrip.setVisible(show);
			if (routeCenterWrapper != null) {
				routeCenterWrapper.revalidate();
				routeCenterWrapper.repaint();
			}
		}
	}

	static void styleCopyNextDestinationButton(JButton b, Font uiFont) {
		if (b == null || uiFont == null) {
			return;
		}
		// Same hit-safe rounded plate as Control Panel / Kill scripts; allow muted foreground
		// when there is nothing to copy.
		OverlayOutlineButtonStyle.applyPrimaryHitSafe(b, uiFont, false);
	}
	/**
	 * Entry point from LiveJournalMonitor.
	 *
	 * We only care about NavRoute / FSDTarget / NavRouteClear; they all
	 * indicate the plotted route has changed and we should re-read
	 * NavRoute.json from the journal directory.
	 */
	public void handleLogEvent(EliteLogEvent event) {
		if (event == null) {
			return;
		}
		trackShipFuelState(event);
		if (event instanceof NavRouteEvent) {
			// Galaxy-map plot replaces a paste/reorder custom list unless the destination
			// is already a hop on that custom route (or NavRoute is empty after a hop).
			reloadFromNavRouteFile(true);
		}
		if (event instanceof NavRouteClearEvent) {
			if (isCustomRouteActive()) {
				// Game clears its NavRoute when you arrive at a hop; keep the custom list.
				routeSession.getTargetState().applyNavRouteClear();
			} else {
				reloadFromNavRouteFile(true, true);
				routeSession.clearAfterNavRouteClearEvent();
				setCustomRouteActive(false);
				rebuildDisplayedEntries();
				table.repaint();
			}
		}
		if (event instanceof FssAllBodiesFoundEvent) {
			// Status refresh only — do not wipe an active custom route.
			reloadFromNavRouteFile(false);
		}
		routeSession.setCustomRouteLoopEnabledForArrivals(supportsCustomRouteLoop()
				&& isCustomRouteActive() && OverlayPreferences.isCustomRouteLoopEnabled());
		RouteJournalApplyOutcome outcome = routeSession.applySecondaryJournalEvent(event);
		if (outcome.refreshDisplayedRows()) {
			rebuildDisplayedEntries();
		} else {
			// Session current system can move without a full row rebuild; keep Ly column / blank-current in sync.
			syncTableCurrentFromRouteSession();
		}
		if (outcome.exitHandleLogWithoutSessionPersist()) {
			return;
		}
		fireSessionStateChanged();
		if (event instanceof FsdJumpEvent) {
			notifyShipJumpComplete();
		}
	}

	/**
	 * Fires Exec {@link ExecTriggerId#SHIP_JUMP_COMPLETE} after the ship Route session has
	 * advanced on {@code FSDJump}. When a custom (paste/reorder) route is active, also fires
	 * {@link ExecTriggerId#CUSTOM_ROUTE_JUMP_COMPLETE}. Fleet Carrier jumps use {@code CarrierJump}
	 * and do not reach here on {@link FleetCarrierTabPanel}.
	 */
	private void notifyShipJumpComplete() {
		if (execTriggerService == null || !firesShipJumpCompleteTrigger()) {
			return;
		}
		String next = nextRouteDestinationSystemName(routeSession,
				supportsCustomRouteLoop() && isCustomRouteActive(),
				OverlayPreferences.isCustomRouteLoopEnabled());
		execTriggerService.onShipJumpComplete(next);
		if (isCustomRouteActive()) {
			execTriggerService.onCustomRouteJumpComplete(next);
		}
	}

	/** Ship Route tab only; Fleet Carrier overrides to {@code false}. */
	protected boolean firesShipJumpCompleteTrigger() {
		return true;
	}

	/** Called after paste / drag reorder so subclasses (Fleet Carrier) can latch custom-route state. */
	protected void onCustomRouteMutated() {
		setCustomRouteActive(true);
	}

	/**
	 * Whether this tab should update its “current system” marker/path on carrier jumps.
	 * <p>
	 * Default behavior: only update when the player is docked on the carrier.
	 * FleetCarrierTabPanel overrides this to always update.
	 */
	protected boolean shouldUpdateOnCarrierJump(CarrierJumpEvent jump) {
		return jump != null && jump.isDocked();
	}

	/**
	 * Whether route rows should show fuel-exhaustion prediction. FleetCarrierTabPanel overrides to
	 * false: carriers burn tritium, not the ship's main tank.
	 */
	protected boolean routeFuelPredictionApplies() {
		return true;
	}

	/**
	 * {@link org.dce.ed.logreader.LiveJournalMonitor} does not replay history, so without this the
	 * FSD profile stays unknown until the player swaps ships/modules mid-session. Reads the most
	 * recent Loadout (and LoadGame fuel level as a Status fallback) off the EDT.
	 */
	private void bootstrapShipFuelProfileFromJournals() {
		Thread t = new Thread(() -> {
			try {
				Path dir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
				if (dir == null || !Files.isDirectory(dir)) {
					return;
				}
				EliteJournalReader reader = new EliteJournalReader(dir);
				// EngineerCraft often upgrades the FSD without a follow-up Loadout (Farseer grade crafts).
				List<EliteLogEvent> events = reader.readEventsFromLastNJournalFiles(
						12, Set.of("Loadout", "LoadGame", "EngineerCraft"));
				LoadoutEvent lastLoadout = null;
				LoadGameEvent lastLoadGame = null;
				List<EngineerCraftEvent> fsdCraftsAfterLoadout = new ArrayList<>();
				for (EliteLogEvent e : events) {
					if (e instanceof LoadoutEvent lo) {
						lastLoadout = lo;
						fsdCraftsAfterLoadout.clear();
					} else if (e instanceof LoadGameEvent lg) {
						lastLoadGame = lg;
					} else if (e instanceof EngineerCraftEvent craft
							&& RouteFuelPrediction.isFsdCraft(craft)
							&& lastLoadout != null) {
						fsdCraftsAfterLoadout.add(craft);
					}
				}
				RouteFuelPrediction.ShipFuelProfile profile =
						RouteFuelPrediction.profileFromLoadout(lastLoadout);
				if (profile != null) {
					for (EngineerCraftEvent craft : fsdCraftsAfterLoadout) {
						profile = RouteFuelPrediction.applyFsdCraft(profile, craft);
					}
				}
				double loadGameFuel = lastLoadGame != null ? lastLoadGame.getFuelLevel() : Double.NaN;
				RouteFuelPrediction.ShipFuelProfile profileFinal = profile;
				SwingUtilities.invokeLater(() -> {
					// Prefer the journal snapshot at startup (includes FSD crafts after the last
					// Loadout). Live Loadout/EngineerCraft events keep updating afterward.
					if (profileFinal != null) {
						shipFuelProfile = profileFinal;
					}
					if (Double.isNaN(shipFuelMainTons) && !Double.isNaN(loadGameFuel)) {
						shipFuelMainTons = loadGameFuel;
					}
					recomputeRouteFuelPrediction();
				});
			} catch (Exception ignored) {
				// Prediction simply stays off until a live Loadout arrives.
			}
		}, "RouteFuelProfileBootstrap");
		t.setDaemon(true);
		t.start();
	}

	/** Keeps the ship/FSD fuel snapshot current from journal + Status events. */
	private void trackShipFuelState(EliteLogEvent event) {
		if (event instanceof LoadoutEvent lo) {
			applyShipFuelProfile(RouteFuelPrediction.profileFromLoadout(lo));
		} else if (event instanceof EngineerCraftEvent craft
				&& RouteFuelPrediction.isFsdCraft(craft)) {
			// EliteOverlayTabbedPane patches getLatestLoadout() before this runs. Rebuild from that
			// so UnladenMass + FSDOptimalMass match the craft (not a prior ship's stale profile).
			RouteFuelPrediction.ShipFuelProfile rebuilt =
					RouteFuelPrediction.profileFromLoadout(EliteOverlayTabbedPane.getLatestLoadout());
			if (rebuilt != null) {
				applyShipFuelProfile(rebuilt);
			} else if (shipFuelProfile != null) {
				applyShipFuelProfile(RouteFuelPrediction.applyFsdCraft(shipFuelProfile, craft));
			}
		} else if (event instanceof LoadGameEvent lg) {
			shipFuelMainTons = lg.getFuelLevel();
			recomputeRouteFuelPrediction();
		} else if (event instanceof FsdJumpEvent fj) {
			// True post-jump tank; re-anchors the model after every hop (rebuild recomputes).
			shipFuelMainTons = fj.getFuelLevel();
		} else if (event instanceof StatusEvent st) {
			double fuel = st.getFuelMain();
			double cargo = st.getCargo();
			boolean fuelChanged = Double.isNaN(shipFuelMainTons) || Math.abs(fuel - shipFuelMainTons) > 0.01;
			boolean cargoChanged = Math.abs(cargo - shipCargoTons) > 0.01;
			if (fuelChanged || cargoChanged) {
				shipFuelMainTons = fuel;
				shipCargoTons = cargo;
				recomputeRouteFuelPrediction();
			}
			// Lazy Loadout fill (e.g. overlay started mid-session) so FSD crafts aren't ignored.
			if (shipFuelProfile == null) {
				refreshShipFuelProfileFromLatestLoadout();
			}
		}
	}

	/**
	 * Rebuilds the fuel-gauge FSD profile from {@link EliteOverlayTabbedPane#getLatestLoadout()},
	 * including in-memory patches after {@code EngineerCraft} when Elite omits a fresh Loadout.
	 */
	void refreshShipFuelProfileFromLatestLoadout() {
		applyShipFuelProfile(RouteFuelPrediction.profileFromLoadout(
				EliteOverlayTabbedPane.getLatestLoadout()));
	}

	private void applyShipFuelProfile(RouteFuelPrediction.ShipFuelProfile profile) {
		if (profile == null) {
			return;
		}
		shipFuelProfile = profile;
		recomputeRouteFuelPrediction();
	}

	/**
	 * Class-column fuel pump:
	 * <ul>
	 *   <li>Green — scoopable star (KGBFOAM)</li>
	 *   <li>Yellow / red — fuel warning on a scoopable star (often: scoopable but no scoop fitted)</li>
	 *   <li>Yellow+red slash / red+yellow slash — fuel warning on a non-scoopable star</li>
	 * </ul>
	 */
	private Icon fuelIconForRow(RouteEntry e, int row) {
		boolean scoopable = e != null && FuelScoopStarClass.isFuelScoopable(e.starClass);
		RouteFuelPrediction.Result fp = routeFuelPrediction;
		RouteFuelPrediction.RowFuelState fs = fp != null ? fp.stateAt(row) : null;
		if (fs == RouteFuelPrediction.RowFuelState.UNREACHABLE
				|| fs == RouteFuelPrediction.RowFuelState.BEYOND_JUMP_RANGE) {
			return scoopable ? ICON_FUEL_OUT_SCOOPABLE : ICON_FUEL_OUT_UNSCOOPABLE;
		}
		if (fs == RouteFuelPrediction.RowFuelState.LAST_REACHABLE) {
			return scoopable ? ICON_FUEL_LAST_SCOOPABLE : ICON_FUEL_LAST_UNSCOOPABLE;
		}
		if (scoopable) {
			return ICON_FUEL_SCOOP;
		}
		return null;
	}

	/** Hover popup HTML for Class-column rows that carry a fuel pump icon; null otherwise. */
	private String buildFuelHoverHtml(int modelRow) {
		RouteEntry e;
		try {
			e = tableModel.getEntries(modelRow);
		} catch (Exception ex) {
			return null;
		}
		if (e == null || e.isBodyRow || fuelIconForRow(e, modelRow) == null) {
			return null;
		}
		boolean scoopable = FuelScoopStarClass.isFuelScoopable(e.starClass);
		boolean hasScoop = shipFuelProfile != null && shipFuelProfile.hasFuelScoop();
		RouteFuelPrediction.Result fp = routeFuelPrediction;
		RouteFuelPrediction.RowFuelState fs = fp != null ? fp.stateAt(modelRow) : null;
		StringBuilder sb = new StringBuilder("<html>");
		if (e.starClass != null && !e.starClass.isBlank()) {
			sb.append("Star class: ").append(escapeHtml(e.starClass));
			if (scoopable) {
				sb.append(" (scoopable)");
			} else {
				sb.append(" (not scoopable)");
			}
			sb.append("<br>");
		}
		if (fs == RouteFuelPrediction.RowFuelState.BEYOND_JUMP_RANGE) {
			sb.append("<b>Beyond FSD jump range</b> — this hop needs more fuel than your frame shift ")
					.append("drive can burn in one jump");
			if (fp != null && fp.maxFuelPerJump() > 0) {
				sb.append(String.format(Locale.US, " (max %.1f t/jump)", fp.maxFuelPerJump()));
			}
			if (fp != null && !Double.isNaN(fp.maxJumpRangeLy()) && fp.maxJumpRangeLy() > 0) {
				sb.append(String.format(Locale.US, "; loadout max range %.2f Ly", fp.maxJumpRangeLy()));
			}
			sb.append(", even with a full tank.");
			sb.append("<br>Neutron (4×) / white-dwarf (1.5×) jet-cone boosts are included when leaving those stars.");
			appendSlashLegend(sb, scoopable);
		} else if (fs == RouteFuelPrediction.RowFuelState.UNREACHABLE) {
			sb.append("<b>Out of fuel before this system</b>")
					.append(fp.assumesScooping() ? " — even scooping at every scoopable star." : ".");
			appendSlashLegend(sb, scoopable);
		} else if (fs == RouteFuelPrediction.RowFuelState.LAST_REACHABLE) {
			if (fp != null && fp.blockReason() == RouteFuelPrediction.BlockReason.JUMP_TOO_FAR) {
				sb.append("<b>Last system before a jump that exceeds your FSD range</b>")
						.append(fuelArrivalHtml(fp, modelRow));
				if (!Double.isNaN(fp.maxJumpRangeLy()) && fp.maxJumpRangeLy() > 0) {
					sb.append(String.format(Locale.US, "<br>Loadout max jump range: %.2f Ly.", fp.maxJumpRangeLy()));
				}
			} else {
				sb.append("<b>Last system you can reach on current fuel</b>")
						.append(fuelArrivalHtml(fp, modelRow));
			}
			if (scoopable && !hasScoop) {
				sb.append("<br>Star is scoopable, but this ship has no fuel scoop.");
			} else if (scoopable && hasScoop && fp != null && !fp.assumesScooping()) {
				sb.append("<br>Fuel estimate ignores scooping (preference).");
			}
			appendSlashLegend(sb, scoopable);
		} else {
			if (hasScoop && fp != null && fp.assumesScooping()) {
				sb.append("Scoopable star — you can refuel here")
						.append(fuelArrivalHtml(fp, modelRow)).append(".");
			} else if (hasScoop && fp != null && !fp.assumesScooping()) {
				sb.append("Scoopable star — fuel estimate ignores scooping (preference)")
						.append(fuelArrivalHtml(fp, modelRow)).append(".");
			} else if (shipFuelProfile != null) {
				sb.append("Scoopable star class — this ship has no fuel scoop.")
						.append(fuelArrivalHtml(fp, modelRow));
			} else {
				sb.append("Scoopable star class (KGBFOAM)")
						.append(fuelArrivalHtml(fp, modelRow)).append(".");
			}
		}
		return sb.append("</html>").toString();
	}

	/** Explain slash vs plain yellow/red so the pump doesn't read as “scoopable”. */
	private static void appendSlashLegend(StringBuilder sb, boolean scoopable) {
		if (scoopable) {
			sb.append("<br>Yellow/red pump (no slash) = fuel warning on a scoopable star.");
		} else {
			sb.append("<br>Slashed pump = fuel warning on a non-scoopable star.");
		}
	}

	/** "&lt;br&gt;Predicted fuel on arrival: X.X t of YY t" when the simulation has a number for this row. */
	private static String fuelArrivalHtml(RouteFuelPrediction.Result fp, int row) {
		if (fp == null) {
			return "";
		}
		double arr = fp.fuelOnArrivalAt(row);
		if (Double.isNaN(arr)) {
			return "";
		}
		return String.format(Locale.US, "<br>Predicted fuel on arrival: %.1f t of %.0f t",
				arr, fp.fuelCapacityMain());
	}

	/** Re-simulates fuel over the displayed route; cheap (O(rows)), called on fuel/route changes. */
	private void recomputeRouteFuelPrediction() {
		RouteFuelPrediction.Result next = null;
		if (routeFuelPredictionApplies()
				&& OverlayPreferences.isRouteFuelPredictionEnabled()
				&& shipFuelProfile != null
				&& !Double.isNaN(shipFuelMainTons)
				&& tableModel != null) {
			int n = tableModel.getRowCount();
			List<RouteEntry> rows = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				rows.add(tableModel.getEntries(i));
			}
			next = RouteFuelPrediction.simulate(
					rows,
					shipFuelProfile,
					shipFuelMainTons,
					shipCargoTons,
					OverlayPreferences.isRouteFuelPredictionConsiderScoop());
		}
		routeFuelPrediction = next;
		if (table != null) {
			table.repaint();
		}
	}

	/**
	 * Start blinking the hollow triangle for an upcoming jump to the given destination.
	 * <p>
	 * Used by {@link FleetCarrierTabPanel} when a {@code CarrierJumpRequest} is logged
	 * (mirrors FSD “charging” behavior driven by {@link org.dce.ed.logreader.event.StatusEvent}).
	 */
	protected void startPendingJumpBlink(String destName, long destAddress) {
		startPendingJumpBlink(destName, destAddress, null);
	}

	protected void startPendingJumpBlink(String destName, long destAddress, java.time.Instant departureTime) {
		if (jumpFlashTimer == null) {
			return;
		}
		routeSession.startCarrierPendingJumpBlink(destName, destAddress, departureTime);
		rebuildDisplayedEntries();
		fireSessionStateChanged();
	}

	/**
	 * Stop the pending-jump blink (e.g. carrier jump cancelled before departure).
	 */
	protected void stopPendingJumpBlink() {
		routeSession.stopCarrierPendingJumpBlink();
		rebuildDisplayedEntries();
		fireSessionStateChanged();
	}

	int getRowForSystem(String systemName) {
		for (int row=0; row < table.getModel().getRowCount(); row++) {
			String system = (String) table.getValueAt(row, COL_SYSTEM); // YOUR system column
			if (system.equals(getCurrentSystemName())) {
				return row;
			}
		}
		return -1;
	}

	/**
	 * Copies the system name of the next hop in {@link #baseRouteEntries} after the provided current system address.
	 * Uses the existing copy-to-clipboard + “Copied: …” toast rendering via {@link SystemTableHoverCopyManager}.
	 */
	protected void copyNextSystemFromBaseRoute(long currentSystemAddressOverride) {
		if (table == null || tableModel == null) {
			return;
		}
		if (systemTableHoverCopyManager == null) {
			return;
		}
		List<RouteEntry> baseRouteEntries = routeSession.getBaseRouteEntries();
		if (baseRouteEntries == null || baseRouteEntries.isEmpty()) {
			return;
		}
		if (currentSystemAddressOverride == 0L) {
			return;
		}

		int curIdx = -1;
		for (int i = 0; i < baseRouteEntries.size(); i++) {
			RouteEntry e = baseRouteEntries.get(i);
			if (e == null) {
				continue;
			}
			if (e.systemAddress == currentSystemAddressOverride) {
				curIdx = i;
				break;
			}
		}
		if (curIdx < 0) {
			return;
		}
		int nextIdx = curIdx + 1;
		if (nextIdx < 0 || nextIdx >= baseRouteEntries.size()) {
			return; // no next system
		}
		RouteEntry next = baseRouteEntries.get(nextIdx);
		if (next == null || next.isBodyRow) {
			return;
		}

		long nextAddr = next.systemAddress;
		String nextName = next.systemName;

		int modelRow = -1;
		for (int i = 0; i < tableModel.getRowCount(); i++) {
			RouteEntry e = tableModel.getEntries(i);
			if (e == null || e.isBodyRow) {
				continue;
			}
			if (nextAddr != 0L && e.systemAddress == nextAddr) {
				modelRow = i;
				break;
			}
		}
		if (modelRow < 0 && nextName != null && !nextName.isBlank()) {
			for (int i = 0; i < tableModel.getRowCount(); i++) {
				RouteEntry e = tableModel.getEntries(i);
				if (e == null || e.isBodyRow) {
					continue;
				}
				if (nextName.equals(e.systemName)) {
					modelRow = i;
					break;
				}
			}
		}
		if (modelRow < 0) {
			return;
		}

		int viewRow = table.convertRowIndexToView(modelRow);
		systemTableHoverCopyManager.copySystemNameAtViewRow(viewRow);
		afterDestinationCopiedToClipboard(nextName);
	}

	/** Override on {@link FleetCarrierTabPanel} for auto-copy after carrier jumps. */
	protected ExecTriggerId copyNextDestinationTrigger() {
		return ExecTriggerId.ROUTE_COPY_NEXT_DESTINATION;
	}

	/** Which overlay tab this panel hosts Exec buttons for (Fleet Carrier overrides). */
	protected OverlayTabId execButtonTabId() {
		return OverlayTabId.ROUTE;
	}

	public void setExecTriggerService(ExecTriggerService service) {
		if (this.execTriggerService != null) {
			this.execTriggerService.removeBindingsChangedListener(this::refreshExecTabButtons);
		}
		this.execTriggerService = service;
		if (service != null) {
			service.addBindingsChangedListener(this::refreshExecTabButtons);
		}
		refreshExecTabButtons();
	}

	public void refreshExecTabButtons() {
		SwingUtilities.invokeLater(this::rebuildExecTabButtons);
	}

	private void rebuildExecTabButtons() {
		if (routeCopyStrip == null) {
			return;
		}
		for (JButton button : execTabButtons) {
			routeCopyStrip.remove(button);
		}
		execTabButtons.clear();
		List<ExecBinding> bindings = ExecOverlayButtonSupport.loadBindingsForButtonTab(execTriggerService,
				execButtonTabId());
		// Append script buttons on the right (Fleet Carrier Clear stays on the left via addCopyStripComponentLeft).
		for (ExecBinding binding : bindings) {
			JButton button = ExecOverlayButtonSupport.createActionButton(binding, execTriggerService,
					passThroughEnabledSupplier);
			routeCopyStrip.add(button);
			execTabButtons.add(button);
		}
		updateRouteCopyStripVisibility();
		routeCopyStrip.revalidate();
		routeCopyStrip.repaint();
		if (routeCenterWrapper != null) {
			routeCenterWrapper.revalidate();
			routeCenterWrapper.repaint();
		}
	}

	protected void afterDestinationCopiedToClipboard(String destination) {
		if (execTriggerService != null) {
			execTriggerService.onCopyNextDestination(copyNextDestinationTrigger(), destination);
		}
	}

	protected void setHeaderLabelText(String text) {
		if (headerLabel != null) {
			headerLabel.setText(text);
		}
	}
	/**
	 * @param fromCurrent {@code true} = cumulative Ly from your current system along the route;
	 *                    {@code false} = Ly of each hop from the previous row.
	 */
	public void setDistanceSumMode(boolean fromCurrent) {
		tableModel.setLyFromCurrentSystem(fromCurrent);
		updateLyModeToggleAppearance();
	}

	public boolean isDistanceSumMode() {
		return tableModel.isLyFromCurrentSystem();
	}

	private void configureLyModeToggleButton(JButton b, String tooltip) {
		b.setToolTipText(tooltip);
		b.setMargin(new java.awt.Insets(2, 4, 2, 4));
		b.setBorderPainted(true);
		b.setContentAreaFilled(false);
		b.setFocusPainted(false);
		b.setOpaque(false);
		b.setForeground(EdoUi.User.MAIN_TEXT);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
	}

	private void applyLyModeToggleButtonHoverChrome(JButton b, boolean selected, boolean hovered) {
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

	private void updateLyModeToggleAppearance() {
		if (lyModeFromCurrentButton == null || lyModePerLegButton == null || tableModel == null) {
			return;
		}
		boolean fromCur = tableModel.isLyFromCurrentSystem();
		applyLyModeToggleButtonHoverChrome(lyModeFromCurrentButton, fromCur, lyModeFromCurrentHovered);
		applyLyModeToggleButtonHoverChrome(lyModePerLegButton, !fromCur, lyModePerLegHovered);
		lyModeFromCurrentButton.repaint();
		lyModePerLegButton.repaint();
	}
	private void reloadFromNavRouteFile() {
		reloadFromNavRouteFile(false);
	}

	private void reloadFromNavRouteFile(boolean replaceCustomRoute) {
		reloadFromNavRouteFile(replaceCustomRoute, false);
	}

	/**
	 * @param replaceCustomRoute when {@code false}, leave an active paste/reorder custom list alone
	 *        (e.g. FSS status refresh). When {@code true}, a galaxy-map {@code NavRoute} replaces the
	 *        custom list unless its destination is already on that list; empty/missing NavRoute keeps
	 *        an active custom list (game clears NavRoute on hop arrival).
	 * @param forceReplaceCustomRoute when {@code true}, always replace even if the NavRoute destination
	 *        is on the custom list (used for the Clear button).
	 */
	private void reloadFromNavRouteFile(boolean replaceCustomRoute, boolean forceReplaceCustomRoute) {
		if (customRouteActive && !replaceCustomRoute) {
			return;
		}
		Path dir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
		if (dir == null) {
			if (keepCustomRouteAgainstEmptyNavRoute(forceReplaceCustomRoute)) {
				return;
			}
			headerLabel.setText("No journal directory.");
			routeSession.applyNavRouteReloadParsed(List.of());
			setCustomRouteActive(false);
			rebuildDisplayedEntries();
			return;
		}
		Path navRoute = dir.resolve("NavRoute.json");
		if (!Files.isRegularFile(navRoute)) {
			if (keepCustomRouteAgainstEmptyNavRoute(forceReplaceCustomRoute)) {
				return;
			}
			headerLabel.setText("No plotted route.");
			routeSession.applyNavRouteReloadParsed(List.of());
			setCustomRouteActive(false);
			rebuildDisplayedEntries();
			return;
		}
		List<RouteEntry> entries;
		try (Reader reader = Files.newBufferedReader(navRoute, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			entries = RouteNavRouteJson.parseNavRouteFromJson(root);
		} catch (Exception e) {
			e.printStackTrace();
			if (keepCustomRouteAgainstEmptyNavRoute(forceReplaceCustomRoute)) {
				return;
			}
			headerLabel.setText("Error reading NavRoute.json");
			routeSession.applyNavRouteReloadParsed(List.of());
			setCustomRouteActive(false);
			rebuildDisplayedEntries();
			return;
		}
		if (customRouteActive && replaceCustomRoute && !forceReplaceCustomRoute
				&& (entries.isEmpty()
						|| RouteGeometry.navRouteDestinationOnCustomRoute(entries, routeSession.getBaseRouteEntries()))) {
			// Keep paste/reorder list; FSDTarget/Status still update via applySecondaryJournalEvent.
			return;
		}
		headerLabel.setText(entries.isEmpty()
				? "No plotted route."
						: routeJumpHeader(entries));
		routeSession.applyNavRouteReloadParsed(entries);
		setCustomRouteActive(false);
		rebuildDisplayedEntries();
	}

	/** Empty/missing game NavRoute must not wipe an active custom list unless Clear forced it. */
	private boolean keepCustomRouteAgainstEmptyNavRoute(boolean forceReplaceCustomRoute) {
		return customRouteActive && !forceReplaceCustomRoute;
	}

	/**
	 * Imports a Spansh fleet-carrier route JSON into this tab.
	 * <p>
	 * Does not read/overwrite {@code NavRoute.json}. It only updates this panel's in-memory route backing list.
	 */
	public boolean importSpanshFleetCarrierRouteFile(Path file) {
		if (file == null || !Files.isRegularFile(file)) {
			setHeaderLabelText("Error reading Spansh fleet-carrier file (missing file).");
			routeSession.applyNavRouteReloadParsed(new ArrayList<>());
			tableModel.setEntries(new ArrayList<>());
			return false;
		}

		String name = file.getFileName().toString();
		boolean csv = name.toLowerCase(Locale.US).endsWith(".csv");

		List<RouteEntry> entries;
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			if (csv) {
				entries = RouteNavRouteJson.parseSpanshFleetCarrierRouteFromCsv(reader);
			} else {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				entries = RouteNavRouteJson.parseSpanshFleetCarrierRouteFromJson(root);
			}
		} catch (Exception e) {
			e.printStackTrace();
			setHeaderLabelText("Error reading Spansh JSON or CSV");
			routeSession.applyNavRouteReloadParsed(new ArrayList<>());
			tableModel.setEntries(new ArrayList<>());
			return false;
		}

		if (entries == null || entries.isEmpty()) {
			setHeaderLabelText("No jumps found in Spansh JSON/CSV.");
			routeSession.applyNavRouteReloadParsed(new ArrayList<>());
			tableModel.setEntries(new ArrayList<>());
			return false;
		}

		jumpFlashOn = true;
		if (jumpFlashTimer != null && jumpFlashTimer.isRunning()) {
			jumpFlashTimer.stop();
		}

		setHeaderLabelText(routeJumpHeader(entries));
		routeSession.applySpanshImport(entries);
		rebuildDisplayedEntries();
		fireSessionStateChanged();
		return true;
	}

	/**
	 * Imports a Spansh fleet-carrier route from JSON (e.g. GET /api/results/{job} after fleet carrier plot).
	 */
	public boolean importSpanshFleetCarrierRouteFromResultsJson(JsonObject root) {
		if (root == null) {
			setHeaderLabelText("Error: empty Spansh response.");
			routeSession.applyNavRouteReloadParsed(new ArrayList<>());
			tableModel.setEntries(new ArrayList<>());
			return false;
		}
		List<RouteEntry> entries;
		try {
			entries = RouteNavRouteJson.parseSpanshFleetCarrierRouteFromJson(root);
		} catch (Exception e) {
			e.printStackTrace();
			setHeaderLabelText("Error parsing Spansh fleet-carrier JSON.");
			routeSession.applyNavRouteReloadParsed(new ArrayList<>());
			tableModel.setEntries(new ArrayList<>());
			return false;
		}
		if (entries == null || entries.isEmpty()) {
			setHeaderLabelText("No jumps found in Spansh response.");
			routeSession.applyNavRouteReloadParsed(new ArrayList<>());
			tableModel.setEntries(new ArrayList<>());
			return false;
		}
		jumpFlashOn = true;
		if (jumpFlashTimer != null && jumpFlashTimer.isRunning()) {
			jumpFlashTimer.stop();
		}
		setHeaderLabelText(routeJumpHeader(entries));
		routeSession.applySpanshImport(entries);
		rebuildDisplayedEntries();
		fireSessionStateChanged();
		return true;
	}

	/**
	 * Parses NavRoute.json-style JSON (root with "Route" array) into a list of RouteEntry with distances.
	 * Package-visible for unit tests.
	 */
	static List<RouteEntry> parseNavRouteFromJson(JsonObject root) {
		return RouteNavRouteJson.parseNavRouteFromJson(root);
	}

	/**
	 * Parses Spansh fleet-carrier export JSON.
	 * <p>
	 * Spansh exports usually store jumps under one of:
	 * <ul>
	 *   <li>`result.jumps`</li>
	 *   <li>`parameters.jumps`</li>
	 *   <li>fallback: top-level `jumps`</li>
	 * </ul>
	 * into a list of {@link RouteEntry} objects.
	 */
	static List<RouteEntry> parseSpanshFleetCarrierRouteFromJson(JsonObject root) {
		return RouteNavRouteJson.parseSpanshFleetCarrierRouteFromJson(root);
	}

	protected void rebuildDisplayedEntries() {
		if (rebuildingDisplayedEntries) {
			// A reconcile hook moved route current and asked for another rebuild; this pass covers it.
			return;
		}
		rebuildingDisplayedEntries = true;
		try {
			rebuildDisplayedEntriesOnce();
		} finally {
			rebuildingDisplayedEntries = false;
		}
	}

	/** Guards against a reconcile hook re-entering {@link #rebuildDisplayedEntries()} (EDT-only state). */
	private boolean rebuildingDisplayedEntries;

	private void rebuildDisplayedEntriesOnce() {
		reconcileRouteCurrentWithLiveCommanderPosition();
		syncTableCurrentFromRouteSession();
		RouteDisplaySnapshot snap = routeSession.buildDisplaySnapshot(
				this::applyRememberedScanStatuses, this::resolveSystemCoords, customRouteActive);
		tableModel.setEntries(snap.displayedEntries());
		// Body-row insert/remove changes preferred table height. Apply strip bounds
		// synchronously — revalidate alone can paint one frame with a stale scroll height
		// (blank gap above Custom Route after a station row is removed).
		relayoutRouteCenterAfterRowChange();
		recomputeRouteFuelPrediction();
		maybeScheduleTargetCoordsFetch(snap.displayedEntries());
		SwingUtilities.invokeLater(() -> {
			kickEdsmForBehindCurrentUnknownRows();
			startEdsmUpdatesForVisibleRows();
			scrollToKeepCurrentRowAtOffset();
		});
	}

	private void installRouteTablePasteBinding() {
		InputMap im = table.getInputMap(JComponent.WHEN_FOCUSED);
		ActionMap am = table.getActionMap();
		int shortcutMask;
		try {
			shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		} catch (java.awt.HeadlessException ignored) {
			// CI / headless tests construct RouteTabPanel without a display.
			shortcutMask = java.awt.event.InputEvent.CTRL_DOWN_MASK;
		}
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, shortcutMask), "routePasteSystems");
		am.put("routePasteSystems", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				pasteSystemsFromClipboard();
			}
		});
	}

	private void pasteSystemsFromClipboard() {
		String text = readClipboardText();
		if (text == null || text.isBlank()) {
			setHeaderLabelText("Clipboard is empty.");
			return;
		}
		List<String> names = parsePastedSystemNames(text);
		if (names.isEmpty()) {
			setHeaderLabelText("No system names found on clipboard.");
			return;
		}
		setHeaderLabelText("Adding " + names.size() + " system" + (names.size() == 1 ? "" : "s") + "…");
		final List<String> namesFinal = List.copyOf(names);
		Thread t = new Thread(() -> {
			List<RouteEntry> resolved = new ArrayList<>();
			for (String name : namesFinal) {
				RouteEntry entry = resolvePastedSystem(name);
				if (entry != null) {
					resolved.add(entry);
				}
			}
			SwingUtilities.invokeLater(() -> applyPastedRouteEntries(resolved, namesFinal.size()));
		}, "RoutePasteSystems");
		t.setDaemon(true);
		t.start();
	}

	private void applyPastedRouteEntries(List<RouteEntry> resolved, int requestedCount) {
		if (resolved == null || resolved.isEmpty()) {
			setHeaderLabelText("Could not resolve pasted system name" + (requestedCount == 1 ? "" : "s") + ".");
			return;
		}
		routeSession.ensureCurrentSystemAtStartIfMissing(
				routeSession.getCurrentSystemName(),
				routeSession.getCurrentSystemAddress(),
				routeSession.getCurrentStarPos());
		for (RouteEntry entry : resolved) {
			routeSession.appendBaseRouteEntry(entry);
			if (entry.systemName != null && entry.x != null) {
				resolvedCoordsCache.put(entry.systemName, new Double[] { entry.x, entry.y, entry.z });
			}
		}
		onCustomRouteMutated();
		rebuildDisplayedEntries();
		fireSessionStateChanged();
		String msg = routeJumpHeader(routeSession.getBaseRouteEntries());
		if (resolved.size() < requestedCount) {
			msg += " (added " + resolved.size() + " of " + requestedCount + ")";
		}
		setHeaderLabelText(msg);
	}

	private RouteEntry resolvePastedSystem(String rawName) {
		if (rawName == null || rawName.isBlank()) {
			return null;
		}
		String name = rawName.trim();
		RouteEntry entry = new RouteEntry();
		entry.systemName = name;
		entry.systemAddress = 0L;
		entry.starClass = "?";
		entry.status = RouteScanStatus.UNKNOWN;
		try {
			org.dce.ed.edsm.SystemResponse sys = edsmClient.getSystem(name);
			if (sys != null && sys.name != null && !sys.name.isBlank()) {
				entry.systemName = sys.name;
				if (sys.id64 != null) {
					entry.systemAddress = sys.id64.longValue();
				}
				if (sys.coords != null) {
					entry.x = Double.valueOf(sys.coords.x);
					entry.y = Double.valueOf(sys.coords.y);
					entry.z = Double.valueOf(sys.coords.z);
				}
			}
		} catch (Exception ignored) {
			// Keep the pasted name even if EDSM is unreachable.
		}
		return entry;
	}

	/** Splits clipboard text into candidate system names (newlines / commas / semicolons). */
	static List<String> parsePastedSystemNames(String text) {
		List<String> out = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return out;
		}
		String[] parts = text.split("[\\n\\r,;]+");
		for (String part : parts) {
			if (part == null) {
				continue;
			}
			String name = part.trim();
			if (name.isEmpty()) {
				continue;
			}
			// Ignore obvious non-system lines (headers, distances).
			if (name.equalsIgnoreCase("system") || name.equalsIgnoreCase("star system")) {
				continue;
			}
			out.add(name);
		}
		return out;
	}

	private static String readClipboardText() {
		try {
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			Transferable contents = clipboard.getContents(null);
			if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
				Object data = contents.getTransferData(DataFlavor.stringFlavor);
				return data != null ? data.toString() : null;
			}
		} catch (Exception ignored) {
			// Clipboard access can fail in locked-down environments.
		}
		return null;
	}

	private void clearRouteDragState() {
		routeDragArmed = false;
		routeDragActive = false;
		routeDragFromDisplayRow = -1;
		routeDragFromBaseIndex = -1;
		routeDropInsertDisplayRow = -1;
		routeDragStartPoint = null;
	}

	private void paintRouteRowDropLine(Graphics g) {
		if (routeDropInsertDisplayRow < 0 || table == null) {
			return;
		}
		int y;
		int rows = table.getRowCount();
		if (routeDropInsertDisplayRow >= rows) {
			y = rows <= 0 ? 0 : rows * table.getRowHeight();
		} else {
			y = routeDropInsertDisplayRow * table.getRowHeight();
		}
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setColor(EdoUi.User.MAIN_TEXT);
			g2.setStroke(new BasicStroke(2f));
			g2.drawLine(0, y, table.getWidth(), y);
		} finally {
			g2.dispose();
		}
	}

	private int dropInsertDisplayRowAt(Point point) {
		if (table == null || table.getRowCount() == 0) {
			return 0;
		}
		int row = table.rowAtPoint(point);
		if (row < 0) {
			return point.y < 0 ? 0 : table.getRowCount();
		}
		Rectangle rect = table.getCellRect(row, 0, true);
		if (point.y >= rect.y + rect.height / 2) {
			return row + 1;
		}
		return row;
	}

	/**
	 * Maps a displayed table row to a base-route index, or {@code -1} for synthetic / body rows.
	 */
	private int baseIndexForDisplayRow(int displayRow) {
		if (tableModel == null || displayRow < 0 || displayRow >= tableModel.getRowCount()) {
			return -1;
		}
		RouteEntry displayed = tableModel.getEntries(displayRow);
		if (displayed == null || displayed.isSynthetic || displayed.isBodyRow) {
			return -1;
		}
		List<RouteEntry> base = routeSession.getBaseRouteEntries();
		for (int i = 0; i < base.size(); i++) {
			RouteEntry e = base.get(i);
			if (e == null || e.isBodyRow) {
				continue;
			}
			if (isSameRouteSystem(e, displayed.systemName, displayed.systemAddress)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Converts a display insertion index ({@code 0..displayRowCount}) into a base-list insertion
	 * index ({@code 0..baseSize}) by counting plotted (non-synthetic) rows before the line.
	 */
	private int baseInsertIndexForDisplayInsert(int displayInsertRow) {
		List<RouteEntry> base = routeSession.getBaseRouteEntries();
		if (tableModel == null || displayInsertRow <= 0) {
			return 0;
		}
		int plottedBefore = 0;
		int limit = Math.min(displayInsertRow, tableModel.getRowCount());
		for (int r = 0; r < limit; r++) {
			RouteEntry e = tableModel.getEntries(r);
			if (e == null || e.isSynthetic || e.isBodyRow) {
				continue;
			}
			plottedBefore++;
		}
		return Math.min(plottedBefore, base.size());
	}

	private void applyRouteRowReorderFromBase(int fromBase, int insertDisplayRow) {
		if (fromBase < 0) {
			return;
		}
		int toBase = baseInsertIndexForDisplayInsert(insertDisplayRow);
		if (!routeSession.moveBaseRouteEntry(fromBase, toBase)) {
			return;
		}
		onCustomRouteMutated();
		rebuildDisplayedEntries();
		fireSessionStateChanged();
		setHeaderLabelText(routeJumpHeader(routeSession.getBaseRouteEntries()));
	}

	/**
	 * Fleet / FSD progress: systems at or before the current row have been reached, so EDSM rows should use
	 * {@code *_VISITED} variants even when {@link SystemCache} has no entry yet for that hop.
	 */
	private void kickEdsmForBehindCurrentUnknownRows() {
		if (tableModel == null) {
			return;
		}
		int cur = tableModel.getCurrentSystemRowIndex();
		if (cur < 0) {
			return;
		}
		for (int r = 0; r <= cur; r++) {
			RouteEntry e = tableModel.getEntries(r);
			if (e == null || e.isBodyRow) {
				continue;
			}
			if (e.status != RouteScanStatus.UNKNOWN) {
				continue;
			}
			updateStatusFromEdsm(e, r);
		}
	}

	private void maybeScheduleTargetCoordsFetch(List<RouteEntry> displayed) {
		String targetSystemName = routeSession.getTargetState().getTargetSystemName();
		if (targetSystemName == null || targetSystemName.isBlank()) {
			return;
		}
		for (RouteEntry e : displayed) {
			if (e == null || !e.isSynthetic || e.isBodyRow) {
				continue;
			}
			if (!targetSystemName.equals(e.systemName)) {
				continue;
			}
			if (e.x != null) {
				return;
			}
			final String targetName = targetSystemName;
			if (edsmCoordsFetchInProgress.add(targetName)) {
				Thread t = new Thread(() -> {
					try {
						Double[] fetched = resolveSystemCoordsFromEdsm(targetName);
						if (fetched == null) {
							return;
						}
						SwingUtilities.invokeLater(() -> rebuildDisplayedEntries());
					} finally {
						edsmCoordsFetchInProgress.remove(targetName);
					}
				}, "RouteTargetCoords-" + targetName);
				t.setDaemon(true);
				t.start();
			}
			break;
		}
	}

	/** Call on EDT. Starts EDSM status updates only for rows currently visible in the viewport. */
	private void startEdsmUpdatesForVisibleRows() {
		if (table == null || tableModel == null) {
			return;
		}
		int first = getFirstVisibleRow();
		int last = getLastVisibleRow();
		if (first < 0 || last < 0) {
			return;
		}
		for (int row = first; row <= last; row++) {
			if (row >= tableModel.getRowCount()) {
				break;
			}
			RouteEntry entry = tableModel.getEntries(row);
			if (entry == null || entry.isBodyRow) {
				continue;
			}
			if (entry.status != null && entry.status != RouteScanStatus.UNKNOWN) {
				continue;
			}
			final int r = row;
			new Thread(() -> updateStatusFromEdsm(entry, r),
					"RouteEdsm-" + (entry.systemName != null ? entry.systemName : "row" + r)).start();
		}
	}

	private int getFirstVisibleRow() {
		if (table == null || table.getRowCount() == 0) {
			return -1;
		}
		java.awt.Rectangle visible = table.getVisibleRect();
		if (visible == null || visible.height <= 0) {
			return 0;
		}
		int row = table.rowAtPoint(new java.awt.Point(0, visible.y));
		return row >= 0 ? row : 0;
	}

	private int getLastVisibleRow() {
		if (table == null) {
			return -1;
		}
		int rowCount = table.getRowCount();
		if (rowCount == 0) {
			return -1;
		}
		java.awt.Rectangle visible = table.getVisibleRect();
		if (visible == null || visible.height <= 0) {
			return rowCount - 1;
		}
		int row = table.rowAtPoint(new java.awt.Point(0, visible.y + visible.height - 1));
		return row >= 0 ? row : (rowCount - 1);
	}

	private int getCurrentSystemRowIndex() {
		if (tableModel == null) {
			return -1;
		}
		String cur = getCurrentSystemName();
		if (cur == null || cur.isEmpty()) {
			return -1;
		}
		int n = tableModel.getRowCount();
		for (int i = 0; i < n; i++) {
			RouteEntry e = tableModel.getEntries(i);
			if (e != null && cur.equals(e.systemName)) {
				return i;
			}
		}
		return -1;
	}

	/** Call on EDT. Scrolls the viewport so the current system row sits at TARGET_CURRENT_ROW_OFFSET from the top. */
	private void scrollToKeepCurrentRowAtOffset() {
		if (table == null || routeScrollPane == null) {
			return;
		}
		JViewport vp = null;
		for (java.awt.Container walk = table.getParent(); walk != null; walk = walk.getParent()) {
			if (walk instanceof JViewport) {
				vp = (JViewport) walk;
				break;
			}
		}
		if (vp == null) {
			return;
		}
		int currentRow = getCurrentSystemRowIndex();
		if (currentRow < 0) {
			return;
		}
		int rowHeight = table.getRowHeight();
		Component scrollView = vp.getView();
		int viewTotalH = scrollView != null ? scrollView.getHeight() : table.getHeight();
		int viewHeight = vp.getExtentSize().height;
		int tableYInView = 0;
		if (scrollView != null) {
			java.awt.Point tableOrigin = SwingUtilities.convertPoint(table, 0, 0, scrollView);
			tableYInView = tableOrigin.y;
		}
		int viewY = tableYInView + (currentRow - TARGET_CURRENT_ROW_OFFSET) * rowHeight;
		viewY = Math.max(0, Math.min(viewY, Math.max(0, viewTotalH - viewHeight)));
		java.awt.Point pos = vp.getViewPosition();
		int extentW = vp.getExtentSize().width;
		int viewW = scrollView != null ? scrollView.getWidth() : table.getWidth();
		int maxScrollX = Math.max(0, viewW - extentW);
		int newX = maxScrollX == 0 ? 0 : Math.min(Math.max(0, pos.x), maxScrollX);
		vp.setViewPosition(new java.awt.Point(newX, viewY));
	}

	/**
	 * Min / max constraints for route columns. Actual pixel widths are assigned in
	 * {@link #applyRouteTableColumnLayout()} from the scroll viewport width.
	 */
	private void configureRouteTableColumnResizePolicy() {
		if (table == null) {
			return;
		}
		TableColumnModel cm = table.getColumnModel();
		cm.getColumn(COL_MARKER).setMinWidth(ROUTE_COL_WIDTH_MARKER);
		cm.getColumn(COL_MARKER).setMaxWidth(ROUTE_COL_WIDTH_MARKER);
		cm.getColumn(COL_INDEX).setMinWidth(ROUTE_COL_MIN_INDEX);
		cm.getColumn(COL_SYSTEM).setMinWidth(ROUTE_COL_MIN_SYSTEM);
		cm.getColumn(COL_CLASS).setMinWidth(measureRouteClassColumnMinWidthPx());
		cm.getColumn(COL_STATUS).setMinWidth(routeStatusColumnMinWidthPx());
		cm.getColumn(COL_DISTANCE).setMinWidth(measureRouteDistanceColumnMinWidthPx());
	}

	private void installRouteTableColumnViewportListener(JViewport viewport) {
		if (viewport == null) {
			return;
		}
		viewport.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				SwingUtilities.invokeLater(() -> applyRouteTableColumnLayout());
			}
		});
	}

	/**
	 * Sizes route table columns to the viewport: marker, #, status, and Ly keep at least their minimum widths;
	 * the Class column may shrink after the System column hits its minimum; extra width goes to System.
	 */
	private void applyRouteTableColumnLayout() {
		if (table == null || routeScrollPane == null) {
			return;
		}
		JViewport viewport = routeScrollPane.getViewport();
		if (viewport == null) {
			return;
		}
		// Extent width matches the painted viewport area (accounts for vertical scrollbar).
		int avail = viewport.getExtentSize().width;
		if (avail <= 0) {
			return;
		}
		TableColumnModel cm = table.getColumnModel();
		int wMark = ROUTE_COL_WIDTH_MARKER;
		int wIdx = Math.max(ROUTE_COL_MIN_INDEX, ROUTE_COL_PREF_INDEX);
		int wStat = routeStatusColumnMinWidthPx();
		int wDist = measureRouteDistanceColumnMinWidthPx();
		int wClassPref = measureRouteClassColumnPreferredWidthPx();
		int wClassMin = measureRouteClassColumnMinWidthPx();
		int wClass = wClassPref;
		int wSys = avail - wMark - wIdx - wClass - wStat - wDist;
		if (wSys < ROUTE_COL_MIN_SYSTEM) {
			int deficit = ROUTE_COL_MIN_SYSTEM - wSys;
			int steal = Math.min(deficit, Math.max(0, wClass - wClassMin));
			wClass -= steal;
			wSys = avail - wMark - wIdx - wClass - wStat - wDist;
		}
		if (wSys < ROUTE_COL_MIN_SYSTEM) {
			wSys = ROUTE_COL_MIN_SYSTEM;
			wClass = avail - wMark - wIdx - wSys - wStat - wDist;
			if (wClass < wClassMin) {
				wClass = wClassMin;
				wSys = avail - wMark - wIdx - wClass - wStat - wDist;
			}
		}
		wSys = Math.max(0, wSys);
		int total = wMark + wIdx + wSys + wClass + wStat + wDist;
		if (total < avail) {
			wSys += avail - total;
			total = wMark + wIdx + wSys + wClass + wStat + wDist;
		}
		// Never exceed viewport width: with HORIZONTAL_SCROLLBAR_NEVER a wider view leaves a bogus
		// horizontal origin and empty space on the left.
		if (total > avail) {
			int slack = total - avail;
			int take = Math.min(slack, Math.max(0, wSys - ROUTE_COL_MIN_SYSTEM));
			wSys -= take;
			slack -= take;
			if (slack > 0) {
				take = Math.min(slack, Math.max(0, wClass - wClassMin));
				wClass -= take;
				slack -= take;
			}
			if (slack > 0) {
				take = Math.min(slack, Math.max(0, wIdx - ROUTE_COL_MIN_INDEX));
				wIdx -= take;
				slack -= take;
			}
			if (slack > 0) {
				take = Math.min(slack, Math.max(0, wDist - 56));
				wDist -= take;
				slack -= take;
			}
			if (slack > 0) {
				take = Math.min(slack, Math.max(0, wStat - 28));
				wStat -= take;
				slack -= take;
			}
			if (slack > 0) {
				wSys = Math.max(0, wSys - slack);
			}
		}
		setRouteTableColumnPixelWidth(cm.getColumn(COL_MARKER), wMark);
		setRouteTableColumnPixelWidth(cm.getColumn(COL_INDEX), wIdx);
		setRouteTableColumnPixelWidth(cm.getColumn(COL_SYSTEM), wSys);
		setRouteTableColumnPixelWidth(cm.getColumn(COL_CLASS), wClass);
		setRouteTableColumnPixelWidth(cm.getColumn(COL_STATUS), wStat);
		setRouteTableColumnPixelWidth(cm.getColumn(COL_DISTANCE), wDist);
		table.revalidate();
		table.repaint();
		// Horizontal scrollbar is off: always pin x=0 after layout so we never show a stale offset
		// (empty band on the left, content clipped on the right).
		SwingUtilities.invokeLater(() -> {
			if (routeScrollPane == null) {
				return;
			}
			routeScrollPane.validate();
			JViewport vp = routeScrollPane.getViewport();
			if (vp == null) {
				return;
			}
			java.awt.Point p = vp.getViewPosition();
			if (p.x != 0) {
				vp.setViewPosition(new java.awt.Point(0, p.y));
			}
		});
	}

	private static void setRouteTableColumnPixelWidth(TableColumn col, int w) {
		col.setPreferredWidth(w);
		col.setWidth(w);
	}

	private int routeStatusColumnMinWidthPx() {
		return Math.max(36, OverlayPreferences.getUiFontSize() + ROUTE_COL_MIN_STATUS_EXTRA);
	}

	private int measureRouteDistanceColumnMinWidthPx() {
		if (table == null) {
			return 72;
		}
		FontMetrics fm = table.getFontMetrics(table.getFont());
		int w = fm.stringWidth("999999.99 Ly") + ROUTE_COL_DISTANCE_PAD;
		return Math.max(72, w);
	}

	/**
	 * Minimum Class column width for one star-type letter plus optional fuel gauge icon.
	 */
	private int measureRouteClassColumnMinWidthPx() {
		int icon = fuelGaugeIconSizePx();
		int borders = 8;
		int charW = 10;
		if (table != null) {
			FontMetrics fm = table.getFontMetrics(table.getFont());
			charW = Math.max(fm.charWidth('M'), fm.charWidth('?'));
		}
		return Math.max(40, icon + ROUTE_COL_CLASS_ICON_TEXT_GAP + charW + borders + ROUTE_COL_CLASS_HORIZONTAL_PAD);
	}

	private int measureRouteClassColumnPreferredWidthPx() {
		return measureRouteClassColumnMinWidthPx() + 6;
	}

	/** One-letter route table label; full {@code StarClass} stays in tooltip / model. */
	private static String routeStarClassColumnText(String starClass) {
		if (starClass == null) {
			return "";
		}
		String s = starClass.trim();
		if (s.isEmpty()) {
			return "";
		}
		if (s.length() == 1) {
			return s.toUpperCase(Locale.ROOT);
		}
		return String.valueOf(Character.toUpperCase(s.charAt(0)));
	}

	private static final int VIEWPORT_EDSM_DEBOUNCE_MS = 200;
	private Timer viewportEdsmDebounceTimer;

	/** Install a change listener on the viewport that triggers EDSM updates for newly visible rows (debounced). */
	private void installViewportScrollListener(javax.swing.JViewport viewport) {
		if (viewport == null) {
			return;
		}
		viewport.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				if (viewportEdsmDebounceTimer != null) {
					viewportEdsmDebounceTimer.stop();
				}
				viewportEdsmDebounceTimer = new Timer(VIEWPORT_EDSM_DEBOUNCE_MS, ev -> {
					if (viewportEdsmDebounceTimer != null) {
						viewportEdsmDebounceTimer.stop();
						viewportEdsmDebounceTimer = null;
					}
					SwingUtilities.invokeLater(() -> startEdsmUpdatesForVisibleRows());
				});
				viewportEdsmDebounceTimer.setRepeats(false);
				viewportEdsmDebounceTimer.start();
			}
		});
	}

	static List<RouteEntry> deepCopy(List<RouteEntry> entries) {
		return RouteGeometry.deepCopy(entries);
	}

	static int findSystemRow(List<RouteEntry> entries, String systemName, long systemAddress) {
		return RouteGeometry.findSystemRow(entries, systemName, systemAddress);
	}

	static int bestInsertionIndexByCoords(List<RouteEntry> entries, Double[] coords) {
		return RouteGeometry.bestInsertionIndexByCoords(entries, coords);
	}

	static void recomputeLegDistances(List<RouteEntry> entries) {
		RouteGeometry.recomputeLegDistances(entries);
	}

	static void renumberDisplayIndexes(List<RouteEntry> entries) {
		RouteGeometry.renumberDisplayIndexes(entries);
	}

	static String routeJumpHeader(List<RouteEntry> entries) {
		int jumps = Math.max(0, RouteGeometry.realSystemCount(entries) - 1);
		return "Route: " + jumps + " jumps";
	}

	private void rememberScanStatus(RouteEntry entry, RouteScanStatus status) {
		if (entry == null) {
			return;
		}
		if (entry.systemAddress == 0L) {
			return;
		}
		if (status == null || status == RouteScanStatus.UNKNOWN) {
			return;
		}
		lastKnownScanStatusByAddress.put(entry.systemAddress, status);
	}

	private void applyRememberedScanStatuses(List<RouteEntry> entries) {
		if (entries == null) {
			return;
		}
		int curRow = RouteGeometry.findSystemRow(entries,
				routeSession.getCurrentSystemName(),
				routeSession.getCurrentSystemAddress());
		for (int i = 0; i < entries.size(); i++) {
			RouteEntry e = entries.get(i);
			if (e == null) {
				continue;
			}
			if (e.isBodyRow) {
				continue;
			}

			// Prefer LOCAL scan state first (if known).
			RouteScanStatus local = getLocalScanStatus(e);
			if (local != RouteScanStatus.UNKNOWN) {
				e.status = local;
				rememberScanStatus(e, local);
				continue;
			}

			// If we already knew something, don't reset back to UNKNOWN.
			if (e.status == null || e.status == RouteScanStatus.UNKNOWN) {
				RouteScanStatus remembered = lastKnownScanStatusByAddress.get(e.systemAddress);
				if (remembered != null && remembered != RouteScanStatus.UNKNOWN) {
					e.status = remembered;
				}
			}
		}
	}

	private static RouteScanStatus promoteNotVisitedToVisitedForRouteProgress(RouteScanStatus s) {
		if (s == null) {
			return RouteScanStatus.UNKNOWN;
		}
		switch (s) {
		case FULLY_DISCOVERED_NOT_VISITED:
			return RouteScanStatus.FULLY_DISCOVERED_VISITED;
		case DISCOVERY_MISSING_NOT_VISITED:
			return RouteScanStatus.DISCOVERY_MISSING_VISITED;
		case BODYCOUNT_MISMATCH_NOT_VISITED:
			return RouteScanStatus.BODYCOUNT_MISMATCH_VISITED;
		default:
			return s;
		}
	}

	private static boolean routeEntryMatches(RouteEntry e, long expectedAddress, String expectedName) {
		if (e == null) {
			return false;
		}
		if (expectedAddress != 0L) {
			return e.systemAddress == expectedAddress;
		}
		return Objects.equals(e.systemName, expectedName);
	}

	private static final class EdsmScanSummary {
		final Integer bodyCount;
		final Integer returnedBodies;

		EdsmScanSummary(Integer bodyCount, Integer returnedBodies) {
			this.bodyCount = bodyCount;
			this.returnedBodies = returnedBodies;
		}
	}

	private Double[] resolveSystemCoords(String systemName, long systemAddress, double[] preferred) {
		if (preferred != null && preferred.length == 3) {
			return new Double[] { preferred[0], preferred[1], preferred[2] };
		}
		if (systemName != null) {
			Double[] cached = resolvedCoordsCache.get(systemName);
			if (cached != null) {
				return cached;
			}
		}
		SystemCache cache = SystemCache.getInstance();
		if (systemAddress != 0L) {
			CachedSystem cs = cache.get(systemAddress, systemName);
			if (cs != null && cs.starPos != null && cs.starPos.length == 3) {
				return new Double[] { cs.starPos[0], cs.starPos[1], cs.starPos[2] };
			}
		}
		// We do NOT synchronously call EDSM here; this method is used during rebuilds.
		return null;
	}
	private Double[] resolveSystemCoordsFromEdsm(String systemName) {
		try {
			org.dce.ed.edsm.SystemResponse sys = edsmClient.getSystem(systemName);
			if (sys != null && sys.coords != null) {
				Double[] coords = new Double[] { sys.coords.x, sys.coords.y, sys.coords.z };
				if (systemName != null) {
					resolvedCoordsCache.put(systemName, coords);
				}
				return coords;
			}
		} catch (Exception e) {
			// ignore
		}
		return null;
	}
	private void updateStatusFromEdsm(RouteEntry entry, int row) {
		if (entry == null || row < 0) {
			return;
		}
		RouteScanStatus local = getLocalScanStatus(entry);
		if (local != RouteScanStatus.UNKNOWN) {
			SwingUtilities.invokeLater(() -> applyEdsmDerivedStatusToRow(row, entry.systemAddress, entry.systemName, local));
			return;
		}
		final long addr = entry.systemAddress;
		final String sysName = entry.systemName;
		if (addr != 0L && !edsmRouteStatusInFlight.add(Long.valueOf(addr))) {
			return;
		}
		new Thread(() -> {
			BodiesResponse bodies = null;
			try {
				bodies = edsmClient.showBodies(sysName);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			final BodiesResponse bodiesFinal = bodies;
			SwingUtilities.invokeLater(() -> {
				try {
					applyBodiesResponseToRouteRow(row, addr, sysName, bodiesFinal);
				} finally {
					if (addr != 0L) {
						edsmRouteStatusInFlight.remove(Long.valueOf(addr));
					}
				}
			});
		}, "RouteEdsm-" + (sysName != null ? sysName : "row" + row)).start();
	}

	private void applyEdsmDerivedStatusToRow(int row, long expectedAddress, String expectedName, RouteScanStatus status) {
		if (tableModel == null || row < 0 || row >= tableModel.getRowCount()) {
			return;
		}
		RouteEntry live = tableModel.getEntries(row);
		if (!routeEntryMatches(live, expectedAddress, expectedName)) {
			return;
		}
		live.status = status;
		rememberScanStatus(live, status);
		tableModel.fireRowChanged(row);
	}

	private void applyBodiesResponseToRouteRow(int row, long expectedAddress, String expectedName, BodiesResponse bodies) {
		if (tableModel == null || row < 0 || row >= tableModel.getRowCount()) {
			return;
		}
		RouteEntry live = tableModel.getEntries(row);
		if (!routeEntryMatches(live, expectedAddress, expectedName)) {
			return;
		}
		RouteScanStatus local = getLocalScanStatus(live);
		if (local != RouteScanStatus.UNKNOWN) {
			live.status = local;
			rememberScanStatus(live, local);
			tableModel.fireRowChanged(row);
			return;
		}
		boolean v = isVisited(live);
		RouteScanStatus newStatus = RouteScanStatus.UNKNOWN;
		if (bodies != null && bodies.bodies != null) {
			int returnedBodies = bodies.bodies.size();
			boolean hasBodies = returnedBodies > 0;
			Integer bodyCount = Integer.valueOf(bodies.bodyCount);
			if (live.systemAddress != 0L) {
				edsmSummaryByAddress.put(Long.valueOf(live.systemAddress),
						new EdsmScanSummary(bodyCount, Integer.valueOf(returnedBodies)));
			}
			if (hasBodies) {
				if (bodies.bodyCount != returnedBodies) {
					newStatus = v
							? RouteScanStatus.BODYCOUNT_MISMATCH_VISITED
							: RouteScanStatus.BODYCOUNT_MISMATCH_NOT_VISITED;
				} else {
					newStatus = v
							? RouteScanStatus.FULLY_DISCOVERED_VISITED
							: RouteScanStatus.FULLY_DISCOVERED_NOT_VISITED;
				}
			}
		}
		if (newStatus != RouteScanStatus.UNKNOWN) {
			live.status = newStatus;
			rememberScanStatus(live, newStatus);
			tableModel.fireRowChanged(row);
		} else if (live.status == null || live.status == RouteScanStatus.UNKNOWN) {
			// EDSM gave nothing useful. Only show a visited-incomplete glyph when local journals/cache say visited.
			if (v) {
				live.status = RouteScanStatus.DISCOVERY_MISSING_VISITED;
				rememberScanStatus(live, RouteScanStatus.DISCOVERY_MISSING_VISITED);
			} else {
				live.status = RouteScanStatus.UNKNOWN;
			}
			tableModel.fireRowChanged(row);
		}
	}
	private RouteScanStatus getLocalScanStatus(RouteEntry entry) {
		if (entry == null) {
			return RouteScanStatus.UNKNOWN;
		}
		SystemCache cache = SystemCache.getInstance();
		CachedSystemSummary summary = cache.getSummary(entry.systemAddress, entry.systemName);
		if (summary == null) {
			return RouteScanStatus.UNKNOWN; // not visited / no local info
		}
		Boolean all = summary.allBodiesFound;
		if (Boolean.TRUE.equals(all)) {
			return RouteScanStatus.FULLY_DISCOVERED_VISITED;
		}
		Integer totalBodies = summary.totalBodies;
		int knownBodies = summary.cachedBodyCount;
		Double progress = summary.fssProgress;
		if (progress != null && progress.doubleValue() > 0.0 && progress.doubleValue() < 1.0) {
			return RouteScanStatus.DISCOVERY_MISSING_VISITED;
		}
		// If we know the system body count and we don't have them all locally -> X
		if (totalBodies != null && totalBodies > 0 && knownBodies > 0 && knownBodies < totalBodies) {
			return RouteScanStatus.DISCOVERY_MISSING_VISITED;
		}
		// If we know counts match and FSS progress says complete -> checkmark
		if (totalBodies != null && totalBodies > 0 && knownBodies >= totalBodies
				&& progress != null && progress >= 1.0) {
			return RouteScanStatus.FULLY_DISCOVERED_VISITED;
		}
		if (knownBodies > 0 && (totalBodies == null || totalBodies <= 0)) {
			return RouteScanStatus.DISCOVERY_MISSING_VISITED;
		}
		return RouteScanStatus.UNKNOWN;
	}
	/**
	 * Hook for your local cache: return true if you consider the system
	 * fully scanned locally (all bodies discovered/mapped).
	 *
	 * Right now this is a stub so that only EDSM can produce a checkmark.
	 * Replace with your own integration against SystemState / DB, etc.
	 */
	/**
	 * Returns true if this system is fully scanned in our local cache
	 * (all bodies known and FSS progress ~100%).
	 */
	/**
	 * Returns true if this system is fully scanned *by you* according to the
	 * local cache.
	 *
	 * Uses the new SystemState fields:
	 *   - allBodiesFound (from FSSAllBodiesFound)
	 *   - totalBodies
	 *   - fssProgress
	 * and the number of cached bodies.
	 */
	private boolean isLocallyFullyScanned(RouteEntry entry) {
		if (entry == null) {
			return false;
		}
		// Look up cached system by address/name (same pattern as SystemTabPanel)
		SystemCache cache = SystemCache.getInstance();
		CachedSystemSummary summary = cache.getSummary(entry.systemAddress, entry.systemName);
		if (summary == null) {
			// Nothing cached locally → definitely not "fully scanned by me"
			return false;
		}
		// 1) If we have an explicit "all bodies found" flag, trust that first.
		Boolean all = summary.allBodiesFound;
		if (Boolean.TRUE.equals(all)) {
			return true;
		}
		//        // 2) Otherwise, fall back to counts / progress.
		Integer totalBodies = summary.totalBodies;
		//        if (totalBodies == null || totalBodies == 0) {
		//            // We don't know how many bodies there should be; can't claim "fully scanned".
		//            return false;
		//        }
		int knownBodies = summary.cachedBodyCount;
		if (totalBodies != null && knownBodies < totalBodies) {
			// We've seen some bodies but not all → not fully scanned.
			return false;
		}
		// If FSS progress exists, require it to be ~100%.
		Double progress = summary.fssProgress;
		if (progress != null && progress == 1.0) {// 0.999) {
			return true;
		}
		//         At this point, cache says we know all bodies and FSS is effectively complete.
		return false;
	}
	/**
	 * Returns true if the system for this route entry appears in the local cache.
	 * This is the only "me-related" state: it means you have visited the system.
	 */
	private boolean isVisited(RouteEntry entry) {
		if (entry == null) {
			return false;
		}
		SystemCache cache = SystemCache.getInstance();
		CachedSystemSummary summary = cache.getSummary(entry.systemAddress, entry.systemName);
		return hasLocalJournalEvidence(summary);
	}

	private static boolean hasLocalJournalEvidence(CachedSystemSummary summary) {
		if (summary == null) {
			return false;
		}
		if (summary.cachedBodyCount > 0) {
			return true;
		}
		if (Boolean.TRUE.equals(summary.allBodiesFound)) {
			return true;
		}
		Double progress = summary.fssProgress;
		if (progress != null && progress.doubleValue() > 0.0) {
			return true;
		}
		return false;
	}

	private String getCurrentSystemName() {
		if (routeSession.getCurrentSystemName() != null && !routeSession.getCurrentSystemName().isBlank()) {
			return routeSession.getCurrentSystemName();
		}
		// Best source: recent journals (works at startup, no live events required)
		if (resolveCurrentSystemFromJournal()) {
			return routeSession.getCurrentSystemName();
		}
		// Fallback: whatever SystemCache persisted last
		try {
			CachedSystem cached = SystemCache.load();
			String fromCache = (cached != null) ? cached.systemName : null;
			if (fromCache != null && !fromCache.isBlank()) {
				routeSession.applyKnownCurrentSystem(fromCache, cached.systemAddress,
						cached.starPos != null ? cached.starPos : null);
				return fromCache;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		// Never return null (renderer comparisons should not explode or behave oddly)
		return "";
	}

	/**
	 * After {@link #applySessionState}, persisted route "current" can lag journal reality because
	 * {@link org.dce.ed.logreader.RescanJournalsMain} already advanced {@link SystemCache} from the
	 * same journals. Align route markers with that cache when it disagrees with the restored session.
	 */
	protected void reconcileRouteCurrentWithPostRescanCache() {
		if (resolveCurrentSystemFromJournal()) {
			tableModel.setCurrentSystemIdentity(
					routeSession.getCurrentSystemName(),
					routeSession.getCurrentSystemAddress());
			return;
		}
		try {
			CachedSystem cs = SystemCache.load();
			if (cs == null || cs.systemName == null || cs.systemName.isBlank() || cs.systemAddress == 0L) {
				return;
			}
			String sn = routeSession.getCurrentSystemName();
			long sa = routeSession.getCurrentSystemAddress();
			boolean same = cs.systemName.equals(sn) && cs.systemAddress == sa;
			if (same) {
				return;
			}
			routeSession.applyKnownCurrentSystem(cs.systemName, cs.systemAddress, cs.starPos);
			tableModel.setCurrentSystemIdentity(cs.systemName, cs.systemAddress);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

    /**
     * Keeps the solid “you are here” marker aligned with the System tab’s live commander position.
     * Session/journal current can sit one hop behind (solid on the previous system, hollow on the
     * system you already arrived in); SystemState is updated on the same {@code FSDJump} path and
     * is the authoritative in-memory location while the overlay is running.
     * <p>
     * Never moves the route cursor backward. Forward matches at/after the cursor are adopted even
     * when the same system appeared earlier (custom-route loops). Also re-syncs when the session
     * name already matches live but {@code currentBaseIndex} is still on a different hop.
     */
	protected void reconcileRouteCurrentWithLiveCommanderPosition() {
		if (liveSystemStateSupplier == null) {
			return;
		}
		SystemState live = liveSystemStateSupplier.get();
		if (live == null) {
			return;
		}
		String liveName = live.getSystemName();
		if (liveName == null || liveName.isBlank()) {
			return;
		}
		long liveAddr = live.getSystemAddress();
		List<RouteEntry> base = routeSession.getBaseRouteEntries();
		int cursor = routeSession.getCurrentBaseIndex();
		boolean cursorMatchesLive = base != null && !base.isEmpty()
				&& cursor >= 0 && cursor < base.size()
				&& RouteGeometry.rowMatchesSystem(base.get(cursor), liveName, liveAddr);
		String sessionName = routeSession.getCurrentSystemName();
		long sessionAddr = routeSession.getCurrentSystemAddress();
		boolean sameName = liveName.equals(sessionName);
		boolean sameAddr = liveAddr == 0L || sessionAddr == 0L || liveAddr == sessionAddr;
		if (sameName && sameAddr && cursorMatchesLive) {
			return;
		}
		if (base != null && !base.isEmpty()) {
			int liveAtOrAfter = RouteGeometry.findSystemRowFrom(base, liveName, liveAddr, cursor);
			if (liveAtOrAfter >= 0) {
				routeSession.applyKnownCurrentSystem(liveName, liveAddr, live.getStarPos());
				return;
			}
			int liveAny = RouteGeometry.findSystemRow(base, liveName, liveAddr);
			if (liveAny >= 0 && liveAny < cursor) {
				return;
			}
		}
		routeSession.applyKnownCurrentSystem(liveName, liveAddr, live.getStarPos());
	}

	protected boolean resolveCurrentSystemFromJournal() {
	    try {
	        Path journalDir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
	        if (journalDir == null || !java.nio.file.Files.isDirectory(journalDir)) {
	            return false;
	        }
	        EliteJournalReader reader = new EliteJournalReader(journalDir);
	        String systemName = null;
	        long systemAddress = 0L;
	        double[] starPos = null;
	        List<EliteLogEvent> events = reader.readEventsFromLastNJournalFiles(3);
	        for (EliteLogEvent event : events) {
	            if (event instanceof LocationEvent e) {
	                systemName = e.getStarSystem();
	                systemAddress = e.getSystemAddress();
	                starPos = e.getStarPos();
	            } else if (event instanceof IFsdJump e) {
	                systemName = e.getStarSystem();
	                systemAddress = e.getSystemAddress();
	                if (e instanceof FsdJumpEvent fj) {
	                    starPos = fj.getStarPos();
	                }
	            }
	        }
	        if (systemName == null || systemName.isBlank()) {
	            return false;
	        }
	        routeSession.applyKnownCurrentSystem(systemName, systemAddress, starPos);
	        return true;
	    } catch (Exception e) {
	        e.printStackTrace();
	        return false;
	    }
	}

	protected void reconcileRouteDestinationWithStatusSnapshot() {
		try {
			Path journalDir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
			if (journalDir == null) {
				return;
			}
			Path statusFile = EliteLogFileLocator.findStatusFile(journalDir);
			if (statusFile == null || !Files.isRegularFile(statusFile)) {
				return;
			}
			StatusEvent status = new EliteLogParser().parseStatusJsonFile(statusFile);
			if (status != null) {
				routeSession.applySecondaryJournalEvent(status);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void setCurrentSystemName(String name) {
		if (name == null) {
			return;
		}
		routeSession.setCurrentSystemName(name);
		tableModel.setCurrentSystemName(name);
	}

	/** Copies {@link RouteSession}'s current system into the table model so distance / “you are here” use live journal state. */
	private void syncTableCurrentFromRouteSession() {
		if (tableModel == null) {
			return;
		}
		String n = routeSession.getCurrentSystemName();
		if (n == null || n.isBlank()) {
			return;
		}
		tableModel.setCurrentSystemIdentity(n, routeSession.getCurrentSystemAddress());
	}
	// ---------------------------------------------------------------------
	// Model + table
	// ---------------------------------------------------------------------
	private static final class RouteTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;
		private final List<RouteEntry> entries = new ArrayList<>();
		/** {@code true} = cumulative Ly from current system; {@code false} = per-leg Ly from previous hop. */
		private boolean lyFromCurrentSystem = true;
		private String currentSystemName;
		private long currentSystemAddress;

		void setCurrentSystemIdentity(String name, long systemAddress) {
			if (Objects.equals(this.currentSystemName, name) && this.currentSystemAddress == systemAddress) {
				return;
			}
			this.currentSystemName = name;
			this.currentSystemAddress = systemAddress;
			fireTableDataChanged();
		}

		void setCurrentSystemName(String currentSystemName) {
			if (Objects.equals(this.currentSystemName, currentSystemName)) {
				return;
			}
			this.currentSystemName = currentSystemName;
			fireTableDataChanged();
		}

		private int findCurrentSystemRow() {
			// Prefer the CURRENT marker (set with monotonic base index) so loops do not
			// scroll/Ly-measure against the first name match.
			for (int i = 0; i < entries.size(); i++) {
				RouteEntry e = entries.get(i);
				if (e != null && !e.isBodyRow && e.markerKind == RouteMarkerKind.CURRENT) {
					return i;
				}
			}
			if (currentSystemName == null || currentSystemName.isBlank()) {
				return -1;
			}
			return RouteGeometry.findSystemRow(entries, currentSystemName, currentSystemAddress);
		}

		int getCurrentSystemRowIndex() {
			return findCurrentSystemRow();
		}

		void setLyFromCurrentSystem(boolean lyFromCurrentSystem) {
			if (this.lyFromCurrentSystem != lyFromCurrentSystem) {
				this.lyFromCurrentSystem = lyFromCurrentSystem;
				fireTableDataChanged();
			}
		}

		boolean isLyFromCurrentSystem() {
			return lyFromCurrentSystem;
		}
		@Override
		public int getRowCount() {
			return entries.size();
		}
		@Override
		public int getColumnCount() {
			return 6; // +1 for marker column
		}
		@Override
		public String getColumnName(int column) {
			switch (column) {
			case COL_MARKER: 
				return "";
			case COL_INDEX:
				return "#";
			case COL_SYSTEM:
				return "System";
			case COL_CLASS:
				return "Class";
			case COL_STATUS:
				return "";
			case COL_DISTANCE:
				return "Ly";
			default:
				return "";
			}
		}
		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			RouteEntry e = entries.get(rowIndex);
			switch (columnIndex) {
			case COL_MARKER:
				return null;
			case COL_INDEX:
				if (e.displayIndex == null) {
					return "";
				}
				return e.displayIndex;
			case COL_SYSTEM: {
				String name = e.systemName != null ? e.systemName : "";
				// For body rows, strip the current system name prefix so we show e.g. "A 3" not "Sol A 3"
				if (e.isBodyRow && currentSystemName != null && !currentSystemName.isEmpty()) {
					String prefix = currentSystemName.trim() + " ";
					if (name.startsWith(prefix)) {
						name = name.substring(prefix.length()).trim();
					}
				}
				return name;
			}
			case COL_CLASS:
				if (e.isBodyRow) {
					return "";
				}
				return e.starClass != null ? e.starClass : "";
			case COL_STATUS:
				if (e.isBodyRow) {
					return null;
				}
				return e.status;
			case COL_DISTANCE: {
				if (e.isBodyRow) {
					return "";
				}
				if (!lyFromCurrentSystem) {
					Double leg = e.distanceLy;
					if (leg == null) {
						return "";
					}
					return String.format("%.2f Ly", leg.doubleValue());
				}
				int currentRow = findCurrentSystemRow();
				if (currentRow < 0) {
					return "";
				}
				if (rowIndex == currentRow) {
					return "";
				}
				int from = Math.min(rowIndex, currentRow);
				int to = Math.max(rowIndex, currentRow);
				double total = RouteGeometry.cumulativeDistanceLy(entries, from, to);
				if (!Double.isFinite(total)) {
					return "";
				}
				return String.format("%.2f Ly", total);
			}
			default:
				return "";
			}
		}
		void setEntries(List<RouteEntry> newEntries) {
			entries.clear();
			if (newEntries != null) {
				entries.addAll(newEntries);
			}
			/*
			 * If we just plotted a route before any Location/FSD event, default the Ly
			 * baseline to the origin. Do not overwrite a known current identity when that
			 * system is merely off-route — synthetics / markers use RouteSession instead.
			 */
			if (!entries.isEmpty() && (currentSystemName == null || currentSystemName.isBlank())) {
				RouteEntry z = entries.get(0);
				currentSystemName = z.systemName;
				currentSystemAddress = z.systemAddress;
			}
			fireTableDataChanged();
		}
		RouteEntry getEntries(int row) {
			return entries.get(row);
		}
		void fireRowChanged(int row) {
			if (row >= 0 && row < entries.size()) {
				fireTableRowsUpdated(row, row);
			}
		}
	}
	private static final class StatusRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;
		StatusRenderer() {
			setOpaque(false);
			setHorizontalAlignment(SwingConstants.CENTER);
		}
		@Override
		public Component getTableCellRendererComponent(JTable table,
				Object value,
				boolean isSelected,
				boolean hasFocus,
				int row,
				int column) {
			JLabel label = (JLabel) super.getTableCellRendererComponent(table,
					"",
					false,
					false,
					row,
					column);
			label.setOpaque(false);
			label.setBackground(EdoUi.Internal.TRANSPARENT);
			label.setBorder(new EmptyBorder(3, 0, 3, 0));
			label.setText("");
			label.setIcon(null);

			if (value instanceof RouteScanStatus) {
				RouteScanStatus status = (RouteScanStatus) value;
				switch (status) {
				case FULLY_DISCOVERED_VISITED:
					label.setIcon(ICON_FULLY_DISCOVERED_VISITED);
					break;
				case FULLY_DISCOVERED_NOT_VISITED:
					label.setIcon(ICON_FULLY_DISCOVERED_NOT_VISITED);
					break;
				case DISCOVERY_MISSING_VISITED:
					label.setIcon(ICON_DISCOVERY_MISSING_VISITED);
					break;
				case DISCOVERY_MISSING_NOT_VISITED:
					label.setIcon(ICON_DISCOVERY_MISSING_NOT_VISITED);
					break;
				case BODYCOUNT_MISMATCH_VISITED:
					label.setIcon(ICON_BODYCOUNT_MISMATCH_VISITED);
					break;
				case BODYCOUNT_MISMATCH_NOT_VISITED:
					label.setIcon(ICON_BODYCOUNT_MISMATCH_NOT_VISITED);
					break;
				case UNKNOWN:
				default:
					label.setIcon(ICON_UNKNOWN);
					break;
				}
			}
			return label;
		}
		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			super.paintComponent(g2);
			g2.setColor(EdoUi.ED_ORANGE_TRANS);
			int y = getHeight() - 1;
			g2.drawLine(0, y, getWidth(), y);
			g2.dispose();
		}
	}

	private String buildStatusHoverHtml(RouteEntry entry) {
		if (entry == null) {
			return null;
		}

		// Journal / local cache summary
		String journalStatus = "Unknown";
		String journalBodies = "0";
		SystemCache cache = SystemCache.getInstance();
		CachedSystem cs = cache.get(entry.systemAddress, entry.systemName);
		if (cs != null) {
			SystemState tmp = new SystemState();
			cache.loadInto(tmp, cs);
			Integer totalBodies = tmp.getTotalBodies();
			int knownBodies = tmp.getBodies().size();
			Boolean all = tmp.getAllBodiesFound();
			Double progress = tmp.getFssProgress();
            if (Boolean.TRUE.equals(all) && totalBodies != null && totalBodies > 0 && knownBodies < totalBodies) {
                journalStatus = "FSS complete (count mismatch)";
            } else if (Boolean.TRUE.equals(all)) {
                journalStatus = "Complete";
            } else if (progress != null && progress.doubleValue() > 0.0 && progress.doubleValue() < 1.0) {
				journalStatus = "In progress";
			} else if (knownBodies > 0) {
				journalStatus = "In progress";
			}
			journalBodies = formatJournalBodyProgress(knownBodies, totalBodies, progress);
		}

		// EDSM summary (only based on cached results from previous EDSM calls)
		String edsmStatus = "Unknown";
		String edsmBodies = "—";
		if (entry.systemAddress != 0L) {
			EdsmScanSummary s = edsmSummaryByAddress.get(Long.valueOf(entry.systemAddress));
			if (s != null) {
				Integer bc = s.bodyCount;
				Integer ret = s.returnedBodies;
				// Prefer "observed/expected" form when we know both.
				if (bc != null && bc.intValue() > 0 && ret != null && ret.intValue() >= 0) {
					edsmBodies = ret.intValue() + " / " + bc.intValue();
				} else if (bc != null && bc.intValue() > 0) {
					edsmBodies = "— / " + bc.intValue();
				} else if (ret != null && ret.intValue() > 0) {
					edsmBodies = Integer.toString(ret.intValue());
				}
				if (ret != null && ret.intValue() > 0) {
					if (bc != null && !bc.equals(ret)) {
						edsmStatus = "Body count mismatch";
						if (bc.intValue() > 0) {
							edsmStatus += " (" + ret.intValue() + " / " + bc.intValue() + ")";
						}
					} else {
						edsmStatus = "Complete";
					}
				}
			}
		}

		return "<html>"
				+ "<b>Status:</b> " + escapeHtml(entry.status != null ? entry.status.name() : "Unknown") + "<br>"
				+ "<br>"
				+ "<b>Journal</b><br>"
				+ "Status: " + escapeHtml(journalStatus) + "<br>"
				+ "Body count: " + escapeHtml(journalBodies) + "<br>"
				+ "<br>"
				+ "<b>EDSM</b><br>"
				+ "Status: " + escapeHtml(edsmStatus) + "<br>"
				+ "Body count: " + escapeHtml(edsmBodies)
				+ "</html>";
	}

	private static String formatJournalBodyProgress(int knownBodies, Integer totalBodies, Double progress) {
		if (totalBodies != null && totalBodies.intValue() > 0) {
			int total = totalBodies.intValue();
			if (progress != null && progress.doubleValue() > 0.0 && progress.doubleValue() < 1.0) {
				int progressBodies = (int) Math.floor(progress.doubleValue() * total + 0.000001);
				progressBodies = Math.max(0, Math.min(total, progressBodies));
				int pct = (int) Math.round(progress.doubleValue() * 100.0);
				return progressBodies + " / " + total + " (" + pct + "%)";
			}
			return knownBodies + " / " + total;
		}
		if (progress != null && progress.doubleValue() > 0.0 && progress.doubleValue() < 1.0) {
			int pct = (int) Math.round(progress.doubleValue() * 100.0);
			return Integer.toString(knownBodies) + " (" + pct + "%)";
		}
		return Integer.toString(knownBodies);
	}

	private static String escapeHtml(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;");
	}
	/**
	 * Hover popup for one table column, driven by {@link java.awt.MouseInfo} polling so it works in
	 * every mouse mode (pass-through / hybrid never deliver real mouse events to Swing).
	 */
	private class StatusHoverPopupManager {
		private static final int POLL_INTERVAL_MS = 100;
		private static final int HOVER_DELAY_MS = 400;

		private final javax.swing.Timer pollTimer =
				new javax.swing.Timer(POLL_INTERVAL_MS, e -> pollMousePosition());
		private final javax.swing.Timer hoverTimer =
				new javax.swing.Timer(HOVER_DELAY_MS, e -> showPopupIfStillHovering());

		/** Model column this popup watches. */
		private final int modelColumn;
		/** Builds the popup HTML for a model row; null/blank = no popup. */
		private final java.util.function.IntFunction<String> htmlForModelRow;

		private int hoverViewRow = -1;
		private javax.swing.JComponent currentPopup;

		StatusHoverPopupManager(int modelColumn, java.util.function.IntFunction<String> htmlForModelRow) {
			this.modelColumn = modelColumn;
			this.htmlForModelRow = htmlForModelRow;
		}

		void start() {
			pollTimer.start();
		}

		void stop() {
			pollTimer.stop();
			hoverTimer.stop();
			hidePopup();
			hoverViewRow = -1;
		}

		private void pollMousePosition() {
			if (table == null || !table.isShowing()) {
				hoverTimer.stop();
				hoverViewRow = -1;
				hidePopup();
				return;
			}

			java.awt.PointerInfo info = java.awt.MouseInfo.getPointerInfo();
			if (info == null) {
				hoverTimer.stop();
				hoverViewRow = -1;
				hidePopup();
				return;
			}

			java.awt.Point screenPoint = info.getLocation();
			java.awt.Point tablePoint = new java.awt.Point(screenPoint);
			SwingUtilities.convertPointFromScreen(tablePoint, table);

			if (tablePoint.x < 0 || tablePoint.y < 0
					|| tablePoint.x >= table.getWidth()
					|| tablePoint.y >= table.getHeight()) {
				hoverTimer.stop();
				hoverViewRow = -1;
				hidePopup();
				return;
			}

			int viewRow = table.rowAtPoint(tablePoint);
			int viewCol = table.columnAtPoint(tablePoint);
			if (viewRow < 0 || viewCol < 0) {
				hoverTimer.stop();
				hoverViewRow = -1;
				hidePopup();
				return;
			}

			int watchedViewCol = table.convertColumnIndexToView(modelColumn);
			if (viewCol != watchedViewCol) {
				hoverTimer.stop();
				hoverViewRow = -1;
				hidePopup();
				return;
			}

			if (viewRow != hoverViewRow) {
				hoverViewRow = viewRow;
				hoverTimer.restart();
			}
		}

		private void showPopupIfStillHovering() {
			if (hoverViewRow < 0 || table == null || !table.isShowing()) {
				return;
			}
			showPopupForRow(hoverViewRow);
		}

		private void showPopupForRow(int viewRow) {
			if (viewRow < 0 || viewRow >= table.getRowCount()) {
				return;
			}

			String html;
			try {
				html = htmlForModelRow.apply(table.convertRowIndexToModel(viewRow));
			} catch (Exception ex) {
				return;
			}
			if (html == null || html.isBlank()) {
				return;
			}

			java.awt.Window window = SwingUtilities.getWindowAncestor(table);
			if (!(window instanceof javax.swing.JFrame)) {
				return;
			}

			javax.swing.JFrame frame = (javax.swing.JFrame) window;
			javax.swing.JRootPane rootPane = frame.getRootPane();
			javax.swing.JLayeredPane layeredPane = rootPane.getLayeredPane();

			hidePopup();

			javax.swing.JLabel label = new javax.swing.JLabel(html);
			label.setOpaque(true);
			label.setBackground(EdoUi.Internal.BLACK_ALPHA_180);
			label.setForeground(EdoUi.User.MAIN_TEXT);
			// Outer stroke uses the same orange accent as the rest of the UI,
			// inner padding keeps the text away from the border.
			javax.swing.border.Border padding = new EmptyBorder(4, 8, 4, 8);
			javax.swing.border.Border outline =
					javax.swing.BorderFactory.createLineBorder(EdoUi.ED_ORANGE_TRANS, 1, true);
			label.setBorder(javax.swing.BorderFactory.createCompoundBorder(outline, padding));

			label.setSize(label.getPreferredSize());
			java.awt.Rectangle cellRect = table.getCellRect(viewRow, table.convertColumnIndexToView(modelColumn), true);
			java.awt.Point cellCenter = new java.awt.Point(
					cellRect.x + cellRect.width / 2,
					cellRect.y + cellRect.height / 2);
			SwingUtilities.convertPointToScreen(cellCenter, table);
			java.awt.Point layeredPoint = new java.awt.Point(cellCenter);
			SwingUtilities.convertPointFromScreen(layeredPoint, layeredPane);

			int x = layeredPoint.x + cellRect.width / 2 + 8;
			int y = layeredPoint.y - label.getHeight() / 2;

			// Clamp inside the layered pane so the popup never goes off-screen.
			int minX = 4;
			int minY = 4;
			int maxX = Math.max(minX, layeredPane.getWidth() - label.getWidth() - 4);
			int maxY = Math.max(minY, layeredPane.getHeight() - label.getHeight() - 4);
			if (x > maxX) {
				// If we hit the right edge, flip to the left side of the cell.
				x = layeredPoint.x - label.getWidth() - 8;
			}
			x = Math.max(minX, Math.min(x, maxX));
			y = Math.max(minY, Math.min(y, maxY));

			label.setLocation(x, y);
			layeredPane.add(label, javax.swing.JLayeredPane.POPUP_LAYER);
			layeredPane.revalidate();
			layeredPane.repaint();

			currentPopup = label;
		}

		private void hidePopup() {
			if (currentPopup == null) {
				return;
			}
			java.awt.Component c = currentPopup;
			currentPopup = null;
			java.awt.Container parent = c.getParent();
			if (parent instanceof javax.swing.JLayeredPane) {
				javax.swing.JLayeredPane lp = (javax.swing.JLayeredPane) parent;
				lp.remove(c);
				lp.revalidate();
				lp.repaint();
			}
		}
	}
	private class StarClassRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;
		StarClassRenderer() {
			setOpaque(false);
			setHorizontalAlignment(SwingConstants.LEADING);
		}
		@Override
		public Component getTableCellRendererComponent(JTable table,
				Object value,
				boolean isSelected,
				boolean hasFocus,
				int row,
				int column) {
			JLabel l = (JLabel) super.getTableCellRendererComponent(table,
					value,
					false,
					false,
					row,
					column);
			l.setOpaque(false);
			l.setBackground(EdoUi.Internal.TRANSPARENT);
			l.setForeground(EdoUi.User.MAIN_TEXT);
			l.setIcon(null);
			l.setToolTipText(null);
			RouteEntry e = null;
			try {
				e = tableModel.getEntries(row);
			} catch (Exception ex) {
				e = null;
			}
			if (e != null && e.isBodyRow) {
				l.setText("");
				return l;
			}
			String full = value != null ? value.toString() : "";
			String display = routeStarClassColumnText(full);
			l.setText(display);
			if (!full.isBlank() && !full.equals(display)) {
				l.setToolTipText(full);
			}
			Icon icon = fuelIconForRow(e, row);
			if (icon != null) {
				l.setIcon(icon);
				l.setIconTextGap(ROUTE_COL_CLASS_ICON_TEXT_GAP);
			}
			l.setBorder(new EmptyBorder(3, 4, 3, 4));
			return l;
		}
		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			java.awt.Insets ins = getInsets();
			int x = ins.left + 4;
			int contentH = getHeight() - ins.top - ins.bottom;
			Icon icon = getIcon();
			int iconSlot = fuelGaugeIconSizePx();
			if (icon != null) {
				int iy = ins.top + Math.max(0, (contentH - icon.getIconHeight()) / 2);
				icon.paintIcon(this, g2, x, iy);
			}
			// Always reserve the fuel-pump slot so non-scoopable letters (Y, L, …) line up
			// under scoopable letters rather than under the pumps.
			x += iconSlot + ROUTE_COL_CLASS_ICON_TEXT_GAP;
			String text = getText();
			if (text != null && !text.isEmpty()) {
				g2.setColor(getForeground());
				FontMetrics fm = g2.getFontMetrics();
				int ty = ins.top + Math.max(0, (contentH - fm.getHeight()) / 2) + fm.getAscent();
				g2.drawString(text, x, ty);
			}
			g2.setColor(EdoUi.ED_ORANGE_TRANS);
			int y = getHeight() - 1;
			g2.drawLine(0, y, getWidth(), y);
			g2.dispose();
		}
	}
	private class SystemNameRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;
		@Override
		public Component getTableCellRendererComponent(JTable table,
				Object value,
				boolean isSelected,
				boolean hasFocus,
				int row,
				int column) {
			Component c = super.getTableCellRendererComponent(table,
					value,
					false,
					false,
					row,
					column);
			if (c instanceof JComponent) {
				((JComponent) c).setOpaque(false);
			}
			c.setBackground(EdoUi.Internal.TRANSPARENT);
			if (c instanceof JLabel) {
				JLabel l = (JLabel) c;
				l.setOpaque(false);
				l.setForeground(EdoUi.User.MAIN_TEXT);
				l.setIcon(null);
				l.setIconTextGap(4);
				int indent = 0;
				RouteEntry e = null;
				try {
					e = tableModel.getEntries(row);
					indent = (e != null ? e.indentLevel : 0);
				} catch (Exception ex) {
					indent = 0;
				}
				boolean bodyChevron = false;
				// Body/station target: draw the next-stop chevron beside the name, not in COL_MARKER.
				if (e != null && e.isBodyRow) {
					RouteMarkerKind kind = e.markerKind;
					if (kind == RouteMarkerKind.TARGET
							|| (kind == RouteMarkerKind.PENDING_JUMP && jumpFlashOn)) {
						l.setIcon(new OutlineTriangleIcon(EdoUi.ED_ORANGE_LESS_TRANS, 10, 10, 2f));
						// Keep a clear gap; outline stroke can otherwise crowd the name.
						l.setIconTextGap(6);
						bodyChevron = true;
					}
				}
				// With a chevron, stay near the left of the name cell (not full body indent).
				int left = bodyChevron ? 2 : (6 + indent * 14);
				l.setBorder(new EmptyBorder(3, left, 3, 4));
			}
			return c;
		}
		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			super.paintComponent(g2);
			g2.setColor(EdoUi.ED_ORANGE_TRANS);
			int y = getHeight() - 1;
			g2.drawLine(0, y, getWidth(), y);
			g2.dispose();
		}
	}
	private class MarkerRenderer extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(
				JTable table, Object value, boolean isSelected,
				boolean hasFocus, int row, int column) {
			JLabel l = (JLabel) super.getTableCellRendererComponent(
					table, "", false, false, row, column);
			l.setOpaque(false);
			l.setBackground(EdoUi.Internal.TRANSPARENT);
			l.setHorizontalAlignment(SwingConstants.CENTER);
			l.setBorder(new EmptyBorder(3, 0, 3, 0));
			Icon icon = null;
			RouteEntry entry = null;
			try {
				entry = tableModel.getEntries(row);
			} catch (Exception e) {
				entry = null;
			}
			// Body rows show their target chevron beside the indented name instead.
			RouteMarkerKind kind = (entry != null && !entry.isBodyRow) ? entry.markerKind : RouteMarkerKind.NONE;

			if (kind == RouteMarkerKind.CURRENT) {
				icon = new TriangleIcon(EdoUi.User.MAIN_TEXT, 10, 10);

			} else if (kind == RouteMarkerKind.PENDING_JUMP) {
				// Blink the "next jump" empty triangle.
				if (jumpFlashOn)
				{
					icon = new OutlineTriangleIcon(EdoUi.ED_ORANGE_LESS_TRANS, 10, 10, 2f);
				}

			} else if (kind == RouteMarkerKind.TARGET) {
				// Keep target visible regardless of pending jump.
				icon = new OutlineTriangleIcon(EdoUi.ED_ORANGE_LESS_TRANS, 10, 10, 2f);
			}

			l.setIcon(icon);
			return l;
		}
		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			super.paintComponent(g2);
			g2.setColor(EdoUi.ED_ORANGE_TRANS);
			int y = getHeight() - 1;
			g2.drawLine(0, y, getWidth(), y);
			g2.dispose();
		}
	}
	private static class TriangleIcon implements Icon {
		private final Color color;
		private final int w, h;
		TriangleIcon(Color c, int w, int h) { this.color = c; this.w = w; this.h = h; }
		public int getIconWidth() { return w; }
		public int getIconHeight() { return h; }
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2 = (Graphics2D) g;
			g2.setColor(color);
			int[] xs = { x, x, x + w };
			int[] ys = { y, y + h, y + h/2 };
			g2.fillPolygon(xs, ys, 3);
		}
	}
	/** Same nominal size as {@link StatusCircleIcon} (Class column aligns with status column). */
	private static int fuelGaugeIconSizePx() {
		return OverlayPreferences.getUiFontSize();
	}

	/**
	 * Clean line-art fuel pump (body + window, hose elbow to a nozzle on the right).
	 * Monochrome body color: green = scoopable, yellow = last reachable, red = out of fuel.
	 * Optional contrasting diagonal slash marks a fuel warning on a non-scoopable star.
	 */
	private static final class FuelPumpIcon implements Icon {
		private final Color color;
		/** Contrasting slash color, or null for no slash. */
		private final Color slashColor;
		FuelPumpIcon(Color color, Color slashColor) {
			this.color = color;
			this.slashColor = slashColor;
		}
		@Override
		public int getIconWidth() {
			return fuelGaugeIconSizePx();
		}
		@Override
		public int getIconHeight() {
			return fuelGaugeIconSizePx();
		}
		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			float s = fuelGaugeIconSizePx();
			Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
				g2.setColor(color);
				float w = Math.max(1.1f, s * 0.085f);
				g2.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				// Pump body
				g2.draw(new java.awt.geom.RoundRectangle2D.Float(
						x + s * 0.10f, y + s * 0.16f, s * 0.46f, s * 0.74f, s * 0.16f, s * 0.16f));
				// Display window (filled so the glyph stays legible at small sizes)
				g2.fill(new java.awt.geom.RoundRectangle2D.Float(
						x + s * 0.19f, y + s * 0.26f, s * 0.28f, s * 0.16f, s * 0.08f, s * 0.08f));
				// Ground base, slightly wider than the body
				g2.draw(new Line2D.Float(x + s * 0.04f, y + s * 0.94f, x + s * 0.62f, y + s * 0.94f));
				// Hose: elbow from the body's upper right, down to the nozzle
				java.awt.geom.Path2D.Float hose = new java.awt.geom.Path2D.Float();
				hose.moveTo(x + s * 0.56f, y + s * 0.30f);
				hose.lineTo(x + s * 0.68f, y + s * 0.30f);
				hose.quadTo(x + s * 0.86f, y + s * 0.30f, x + s * 0.86f, y + s * 0.48f);
				hose.lineTo(x + s * 0.86f, y + s * 0.62f);
				g2.draw(hose);
				// Nozzle tip
				g2.fill(new Ellipse2D.Float(
						x + s * 0.86f - s * 0.075f, y + s * 0.62f - s * 0.03f, s * 0.15f, s * 0.15f));
				if (slashColor != null) {
					// Diagonal slash (upper-left → lower-right) in the contrasting fuel color.
					float slashW = Math.max(1.6f, s * 0.14f);
					g2.setColor(slashColor);
					g2.setStroke(new BasicStroke(slashW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g2.draw(new Line2D.Float(
							x + s * 0.08f, y + s * 0.12f,
							x + s * 0.92f, y + s * 0.88f));
				}
			} finally {
				g2.dispose();
			}
		}
	}
	private static class OutlineTriangleIcon implements Icon {
		private final Color color;
		private final int w;
		private final int h;
		private final float strokeWidth;
		OutlineTriangleIcon(Color color, int w, int h, float strokeWidth) {
			this.color = color;
			this.w = w;
			this.h = h;
			this.strokeWidth = strokeWidth;
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
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(color);
			g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			// Inset by half the stroke so the tip does not spill into the label text gap.
			float inset = strokeWidth * 0.5f;
			int left = Math.round(x + inset);
			int top = Math.round(y + inset);
			int right = Math.round(x + w - inset);
			int bottom = Math.round(y + h - inset);
			int midY = (top + bottom) / 2;
			int[] xs = { left, left, right };
			int[] ys = { top, bottom, midY };
			g2.drawPolygon(xs, ys, 3);
			g2.dispose();
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
		// Apply recursively so labels/buttons/etc. stay consistent with the table.
		applyFontRecursively(this, uiFont);
		if (headerLabel != null) {
			headerLabel.setFont(uiFont.deriveFont(Font.BOLD));
		}
		if (customRouteWarningLabel != null) {
			customRouteWarningLabel.setFont(uiFont.deriveFont(Font.BOLD));
			customRouteWarningLabel.setForeground(EdoUi.User.ERROR);
		}
		if (clearCustomRouteButton != null) {
			OverlayOutlineButtonStyle.applyPrimaryHitSafeCompact(clearCustomRouteButton, uiFont);
		}
		if (loopCustomRouteButton != null) {
			styleLoopCustomRouteButton();
		}
		if (lyModeFromCurrentButton != null) {
			lyModeFromCurrentButton.setForeground(EdoUi.User.MAIN_TEXT);
			lyModePerLegButton.setForeground(EdoUi.User.MAIN_TEXT);
			updateLyModeToggleAppearance();
		}
		if (table != null) {
			table.setFont(uiFont);
			table.setRowHeight(computeRowHeight(table, uiFont, 6));
			if (table.getTableHeader() != null) {
				table.getTableHeader().setFont(uiFont.deriveFont(Font.BOLD));
			}
			configureRouteTableColumnResizePolicy();
			applyRouteTableColumnLayout();
		}
		for (JButton execButton : execTabButtons) {
			if (execButton != null) {
				styleCopyNextDestinationButton(execButton, uiFont);
			}
		}
		repaint();
	}

	public void applyOverlayBackground(Color bgWithAlpha, boolean treatAsTransparent) {
		boolean opaque = !treatAsTransparent;
		setOpaque(opaque);
		if (opaque && bgWithAlpha != null) {
			setBackground(bgWithAlpha);
		}
		if (routeScrollPane != null) {
			OverlayTransparentChrome.configureScrollPane(routeScrollPane);
			if (routeScrollPane.getVerticalScrollBar() != null) {
				routeScrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(9, Integer.MAX_VALUE));
			}
		}
		OverlayTransparentChrome.applyToSubtree(this);
		if (clearCustomRouteButton != null) {
			OverlayOutlineButtonStyle.applyPrimaryHitSafeCompact(clearCustomRouteButton, uiFont);
		}
		revalidate();
		repaint();
	}

	/**
	 * Copies the next plotted route system to the clipboard and shows the “Copied: …” toast.
	 * Does not fire exec bindings — callers attach their own trigger (e.g. fleet cooldown complete).
	 * Does not clear the clipboard when there is no next hop.
	 *
	 * @return trimmed system name, or {@code null} if there is no next hop
	 */
	public String copyNextRouteDestinationForExec() {
		// Session current can lag one hop; align with System tab before resolving next.
		reconcileRouteCurrentWithLiveCommanderPosition();
		String next = nextRouteDestinationSystemName(routeSession,
				supportsCustomRouteLoop() && isCustomRouteActive(),
				OverlayPreferences.isCustomRouteLoopEnabled());
		if (next == null || next.isBlank()) {
			return null;
		}
		return copyDestinationNameToClipboard(next);
	}

	private String copyDestinationNameToClipboard(String systemName) {
		String trimmed = systemName.trim();
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(trimmed), null);
		Component anchor = (routeCopyStrip != null && routeCopyStrip.isShowing()) ? routeCopyStrip : table;
		SystemTableHoverCopyManager.showCopiedToast((JComponent) anchor, trimmed);
		return trimmed;
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
	private static int computeRowHeight(JTable table, Font font, int verticalPaddingPx) {
		if (table == null || font == null) {
			return 24;
		}
		FontMetrics fm = table.getFontMetrics(font);
		int h = fm.getAscent() + fm.getDescent() + verticalPaddingPx;
		if (h < 18) {
			h = 18;
		}
		return h;
	}
	
}
