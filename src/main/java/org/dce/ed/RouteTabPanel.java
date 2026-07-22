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
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.ConcurrentHashMap;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.BorderFactory;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import org.dce.ed.exec.ExecTriggerId;
import org.dce.ed.exec.ExecTriggerService;
import org.dce.ed.exec.FleetCooldownClipboardPrep;
import org.dce.ed.ui.OverlayScrollPaneSupport;
import org.dce.ed.ui.PassThroughScrollSupport;
import org.dce.ed.ui.SelectiveHitSupport;
import org.dce.ed.ui.SubtleScrollBarUI;
import org.dce.ed.ui.TransparentViewportUI;

import org.dce.ed.cache.CachedSystem;
import org.dce.ed.cache.CachedSystemSummary;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.edsm.BodiesResponse;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.EliteLogEvent.NavRouteClearEvent;
import org.dce.ed.logreader.EliteLogEvent.NavRouteEvent;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.CarrierLocationEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.FsdTargetEvent;
import org.dce.ed.logreader.event.FssAllBodiesFoundEvent;
import org.dce.ed.logreader.event.IFsdJump;
import org.dce.ed.logreader.event.LoadGameEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.state.SystemState;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.DistanceToggleIcons;
import org.dce.ed.ui.HoverCopyButtonSupport;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.StatusCircleIcon;
import org.dce.ed.ui.SystemTableHoverCopyManager;
import org.dce.ed.ui.EdoUi.User;
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
	private static final Icon ICON_FUEL_SCOOP = new FuelPumpIcon(FUEL_GAUGE_GREEN);
	private static final Icon ICON_FUEL_LAST_REACHABLE = new FuelPumpIcon(FUEL_PUMP_YELLOW);
	private static final Icon ICON_FUEL_OUT = new FuelPumpIcon(FUEL_PUMP_RED);
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
	/** Padding above “Copy next destination” under the route table. */
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
	private final JPanel routeCopyStrip;
	private final JButton copyNextDestinationButton;
	private final HoverCopyButtonSupport copyNextDestinationHoverCopySupport;
	private final Timer copyNextDestinationRefreshTimer;
	private final RouteTableModel tableModel;
	private SystemTableHoverCopyManager systemTableHoverCopyManager;
	private ExecTriggerService execTriggerService;
	private StatusHoverPopupManager statusHoverPopupManager;
	private StatusHoverPopupManager fuelHoverPopupManager;
	private final EdsmClient edsmClient;
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
		List<RouteEntry> entries = session.getBaseRouteEntries();
		if (entries == null || entries.isEmpty()) {
			return null;
		}
		String curName = session.getCurrentSystemName();
		long curAddr = session.getCurrentSystemAddress();
		int row = RouteGeometry.findSystemRow(entries, curName, curAddr);
		int start = row + 1;
		if (row < 0) {
			start = 0;
		}
		for (int i = start; i < entries.size(); i++) {
			RouteEntry e = entries.get(i);
			if (e == null || e.isBodyRow || e.systemName == null || e.systemName.isBlank()) {
				continue;
			}
			if (isSameRouteSystem(e, curName, curAddr)) {
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

	public void setSessionStateChangeCallback(Runnable callback) {
		this.sessionStateChangeCallback = callback;
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

	/** Selective mouse mode: distance toggles, Copy next destination, and system-name cells (double-click copy). */
	public boolean isPointerOverInteractiveRegion(Point screenPoint) {
		if (SelectiveHitSupport.containsScreenPoint(lyModeFromCurrentButton, screenPoint)) {
			return true;
		}
		if (SelectiveHitSupport.containsScreenPoint(lyModePerLegButton, screenPoint)) {
			return true;
		}
		if (SelectiveHitSupport.isOverModelColumnCell(table, screenPoint, COL_SYSTEM)) {
			return true;
		}
		return SelectiveHitSupport.containsScreenPoint(copyNextDestinationButton, screenPoint);
	}

	public void applySessionState(EdoSessionState state) {
		if (state == null) {
			return;
		}
		routeSession.applyPersistenceSnapshot(RoutePersistenceAdapter.fromEdoSession(state));
		if (state.getCurrentSystemName() != null && !state.getCurrentSystemName().isBlank()) {
			routeSession.setCurrentSystemName(state.getCurrentSystemName());
		}
		reconcileRouteCurrentWithPostRescanCache();
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
			}
		};
		// Belt-and-suspenders: remove editors so nothing can ever enter edit mode.
		table.setDefaultEditor(Object.class, null);
		table.setDefaultEditor(String.class, null);
		// Prevent focus/selection/edit initiation entirely (keeps look identical but stops click weirdness)
		table.setFocusable(false);
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
		routeScrollPane.setOpaque(false);
		routeScrollPane.getViewport().setOpaque(false);
		routeScrollPane.setBorder(null);
		routeScrollPane.setViewportBorder(null);
		if (routeScrollPane.getViewport() != null) {
			routeScrollPane.getViewport().setBorder(null);
			installViewportScrollListener(routeScrollPane.getViewport());
			installRouteTableColumnViewportListener(routeScrollPane.getViewport());
		}
		if (routeScrollPane.getColumnHeader() != null) {
			routeScrollPane.getColumnHeader().setBorder(null);
		}
		JTableHeader th = table.getTableHeader();
		if (th != null) {
			th.setBorder(null);
		}
		th.setBorder(null);

		if (routeScrollPane.getVerticalScrollBar() != null) {
			JScrollBar vsb = routeScrollPane.getVerticalScrollBar();
			vsb.setOpaque(false);
			vsb.setBackground(EdoUi.Internal.TRANSPARENT);
			vsb.setUI(new SubtleScrollBarUI());
			vsb.setPreferredSize(new Dimension(12, Integer.MAX_VALUE));
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
				Dimension stripPref = routeCopyStrip.getPreferredSize();
				int w = Math.max(tablePref.width, stripPref.width);
				return new Dimension(w, tablePref.height + stripPref.height);
			}

			@Override
			public Dimension getMinimumSize() {
				Dimension stripPref = routeCopyStrip.getPreferredSize();
				return new Dimension(120, stripPref.height + 40);
			}

			@Override
			public void doLayout() {
				layoutRouteTableAndCopyStrip();
			}
		};
		routeCenterWrapper.setOpaque(false);
		routeCenterWrapper.setBackground(EdoUi.Internal.TRANSPARENT);

		// Right-justified directly under the last table row; docks to the panel bottom only when the table scrolls.
		routeCopyStrip = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		routeCopyStrip.setOpaque(false);
		routeCopyStrip.setBorder(new EmptyBorder(ROUTE_COPY_STRIP_GAP_PX, 4, 4, 4));

		copyNextDestinationButton = new JButton("Copy next destination");
		styleCopyNextDestinationButton(copyNextDestinationButton, uiFont);
		copyNextDestinationButton.setToolTipText("Copy the next route system name to the clipboard (same as Route/Nearby copy)");
		copyNextDestinationButton.addActionListener(e -> copyNextRouteDestinationToClipboard());

		copyNextDestinationHoverCopySupport = new HoverCopyButtonSupport(copyNextDestinationButton,
				() -> nextRouteDestinationSystemName(routeSession),
				passThroughEnabledSupplier);

		routeCenterWrapper.add(routeScrollPane);
		routeCopyStrip.add(copyNextDestinationButton);
		routeCenterWrapper.add(routeCopyStrip);

		add(routeTitleRow, BorderLayout.NORTH);
		add(routeCenterWrapper, BorderLayout.CENTER);

		table.setAlignmentX(Component.LEFT_ALIGNMENT);
		JViewport vpRoute = routeScrollPane.getViewport();
		if (vpRoute != null) {
			vpRoute.setViewPosition(new Point(0, 0));
		}

		copyNextDestinationRefreshTimer = new Timer(1_000, e -> updateCopyNextDestinationButton());
		copyNextDestinationRefreshTimer.setRepeats(true);
		copyNextDestinationRefreshTimer.start();
		updateCopyNextDestinationButton();
		copyNextDestinationHoverCopySupport.start();

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
		});

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
	 * Place the copy button snug under the last table row when the table is short; when the table
	 * needs a scrollbar, keep the button pinned to the bottom of the visible panel.
	 */
	private void layoutRouteTableAndCopyStrip() {
		if (routeCenterWrapper == null || routeScrollPane == null || routeCopyStrip == null) {
			return;
		}
		int w = routeCenterWrapper.getWidth();
		int h = routeCenterWrapper.getHeight();
		if (w <= 0 || h <= 0) {
			return;
		}
		Dimension stripPref = routeCopyStrip.getPreferredSize();
		int stripH = Math.max(stripPref.height, copyNextDestinationButton != null
				? copyNextDestinationButton.getPreferredSize().height + 10
				: 36);
		int maxTableH = Math.max(0, h - stripH);
		int contentH = preferredRouteTableSize().height;
		int tableH = Math.min(contentH, maxTableH);
		routeScrollPane.setBounds(0, 0, w, tableH);
		routeCopyStrip.setBounds(0, tableH, w, stripH);
	}

	/**
	 * Selective (hybrid) mode: punch chrome left of “Copy next destination” and everything below
	 * that row fully transparent (same idea as Control Panel’s Kill scripts strip).
	 */
	@Override
	public void paint(Graphics g) {
		super.paint(g);
		clearAroundCopyNextDestinationInSelectiveMode(g);
	}

	private void clearAroundCopyNextDestinationInSelectiveMode(Graphics g) {
		if (g == null || !TransparentViewportUI.isSelectivePassThroughContext(this)) {
			return;
		}
		if (copyNextDestinationButton == null || !copyNextDestinationButton.isShowing()) {
			return;
		}
		// Punch strip padding / gaps so only Clear + Copy (and their fills) remain visible.
		TransparentViewportUI.clearPanelChromeExceptButtons(g, this, routeCopyStrip);
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
			Component row = routeCopyStrip != null && routeCopyStrip.isShowing()
					? routeCopyStrip
					: copyNextDestinationButton;
			Point rowBottom = SwingUtilities.convertPoint(row, 0, row.getHeight(), this);
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
			routeCopyStrip.revalidate();
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
		if (event instanceof NavRouteEvent
				|| event instanceof NavRouteClearEvent) {
			reloadFromNavRouteFile();
		}
		if (event instanceof NavRouteClearEvent) {
			routeSession.clearAfterNavRouteClearEvent();
			rebuildDisplayedEntries();
			table.repaint();
		}
		if (event instanceof FssAllBodiesFoundEvent) {
			reloadFromNavRouteFile();
		}
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
				List<EliteLogEvent> events = reader.readEventsFromLastNJournalFiles(
						12, Set.of("Loadout", "LoadGame"));
				LoadoutEvent lastLoadout = null;
				LoadGameEvent lastLoadGame = null;
				for (EliteLogEvent e : events) {
					if (e instanceof LoadoutEvent lo) {
						lastLoadout = lo;
					} else if (e instanceof LoadGameEvent lg) {
						lastLoadGame = lg;
					}
				}
				RouteFuelPrediction.ShipFuelProfile profile =
						RouteFuelPrediction.profileFromLoadout(lastLoadout);
				double loadGameFuel = lastLoadGame != null ? lastLoadGame.getFuelLevel() : Double.NaN;
				SwingUtilities.invokeLater(() -> {
					// Live events win: only fill in what hasn't arrived yet.
					if (shipFuelProfile == null && profile != null) {
						shipFuelProfile = profile;
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
			shipFuelProfile = RouteFuelPrediction.profileFromLoadout(lo);
			recomputeRouteFuelPrediction();
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
		}
	}

	/** Icon for the Class column: red/yellow fuel prediction overrides the green scoopable pump. */
	private Icon fuelIconForRow(RouteEntry e, int row) {
		RouteFuelPrediction.Result fp = routeFuelPrediction;
		RouteFuelPrediction.RowFuelState fs = fp != null ? fp.stateAt(row) : null;
		if (fs == RouteFuelPrediction.RowFuelState.UNREACHABLE) {
			return ICON_FUEL_OUT;
		}
		if (fs == RouteFuelPrediction.RowFuelState.LAST_REACHABLE) {
			return ICON_FUEL_LAST_REACHABLE;
		}
		if (e != null && FuelScoopStarClass.isFuelScoopable(e.starClass)) {
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
		RouteFuelPrediction.Result fp = routeFuelPrediction;
		RouteFuelPrediction.RowFuelState fs = fp != null ? fp.stateAt(modelRow) : null;
		StringBuilder sb = new StringBuilder("<html>");
		if (e.starClass != null && !e.starClass.isBlank()) {
			sb.append("Star class: ").append(escapeHtml(e.starClass)).append("<br>");
		}
		if (fs == RouteFuelPrediction.RowFuelState.UNREACHABLE) {
			sb.append("<b>Out of fuel before this system</b>")
					.append(fp.assumesScooping() ? " — even scooping at every scoopable star." : ".");
		} else if (fs == RouteFuelPrediction.RowFuelState.LAST_REACHABLE) {
			sb.append("<b>Last system you can reach on current fuel</b>")
					.append(fuelArrivalHtml(fp, modelRow))
					.append("<br>Red pumps beyond this point are out of range without refueling.");
		} else {
			sb.append("Scoopable star — you can refuel here").append(fuelArrivalHtml(fp, modelRow)).append(".");
		}
		return sb.append("</html>").toString();
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
			next = RouteFuelPrediction.simulate(rows, shipFuelProfile, shipFuelMainTons, shipCargoTons);
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

	public void setExecTriggerService(ExecTriggerService service) {
		this.execTriggerService = service;
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
		Path dir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
		if (dir == null) {
			headerLabel.setText("No journal directory.");
			routeSession.applyNavRouteReloadParsed(List.of());
			rebuildDisplayedEntries();
			return;
		}
		Path navRoute = dir.resolve("NavRoute.json");
		if (!Files.isRegularFile(navRoute)) {
			headerLabel.setText("No plotted route.");
			routeSession.applyNavRouteReloadParsed(List.of());
			rebuildDisplayedEntries();
			return;
		}
		List<RouteEntry> entries;
		try (Reader reader = Files.newBufferedReader(navRoute, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			entries = RouteNavRouteJson.parseNavRouteFromJson(root);
		} catch (Exception e) {
			e.printStackTrace();
			headerLabel.setText("Error reading NavRoute.json");
			routeSession.applyNavRouteReloadParsed(List.of());
			rebuildDisplayedEntries();
			return;
		}
		headerLabel.setText(entries.isEmpty()
				? "No plotted route."
						: "Route: " + entries.size() + " systems");
		routeSession.applyNavRouteReloadParsed(entries);
		rebuildDisplayedEntries();
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

		setHeaderLabelText("Route: " + entries.size() + " systems");
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
		setHeaderLabelText("Route: " + entries.size() + " systems");
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
		syncTableCurrentFromRouteSession();
		RouteDisplaySnapshot snap = routeSession.buildDisplaySnapshot(this::applyRememberedScanStatuses, this::resolveSystemCoords);
		tableModel.setEntries(snap.displayedEntries());
		recomputeRouteFuelPrediction();
		maybeScheduleTargetCoordsFetch(snap.displayedEntries());
		SwingUtilities.invokeLater(() -> {
			kickEdsmForBehindCurrentUnknownRows();
			startEdsmUpdatesForVisibleRows();
			scrollToKeepCurrentRowAtOffset();
		});
		updateCopyNextDestinationButton();
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

	private boolean resolveCurrentSystemFromJournal() {
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
				double total = 0.0;
				for (int i = from + 1; i <= to; i++) {
					Double d = entries.get(i).distanceLy;
					if (d == null) {
						return "";
					}
					total += d.doubleValue();
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
			if (icon != null) {
				int iy = ins.top + Math.max(0, (contentH - icon.getIconHeight()) / 2);
				icon.paintIcon(this, g2, x, iy);
				x += icon.getIconWidth() + getIconTextGap();
			}
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
				int indent = 0;
				try {
					RouteEntry e = tableModel.getEntries(row);
					indent = (e != null ? e.indentLevel : 0);
				} catch (Exception ex) {
					indent = 0;
				}
				int left = 6 + indent * 14;
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
			RouteMarkerKind kind = (entry != null ? entry.markerKind : RouteMarkerKind.NONE);

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
	 * Monochrome so the same shape reads as scoopable (green), last reachable (yellow),
	 * or out of fuel (red). Size scales with {@link OverlayPreferences#getUiFontSize()}.
	 */
	private static final class FuelPumpIcon implements Icon {
		private final Color color;
		FuelPumpIcon(Color color) {
			this.color = color;
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
			int[] xs = { x, x, x + w };
			int[] ys = { y, y + h, y + (h / 2) };
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
		if (copyNextDestinationButton != null) {
			styleCopyNextDestinationButton(copyNextDestinationButton, uiFont);
		}
		repaint();
	}

	public void applyOverlayBackground(Color bgWithAlpha, boolean treatAsTransparent) {
		if (copyNextDestinationButton != null) {
			styleCopyNextDestinationButton(copyNextDestinationButton, uiFont);
			updateCopyNextDestinationButton();
		}
		revalidate();
		repaint();
	}

	private void updateCopyNextDestinationButton() {
		if (copyNextDestinationButton == null) {
			return;
		}
		String next = nextRouteDestinationSystemName(routeSession);
		boolean hasNext = next != null && !next.isBlank();
		// Keep enabled so BasicButtonUI keeps painting label text on translucent hosts;
		// mute the ink when there is nothing to copy.
		copyNextDestinationButton.setEnabled(true);
		copyNextDestinationButton.setForeground(hasNext
				? EdoUi.User.MAIN_TEXT
				: EdoUi.Internal.MAIN_TEXT_ALPHA_180);
		if (routeCenterWrapper != null) {
			routeCenterWrapper.revalidate();
			routeCenterWrapper.repaint();
		}
	}

	private void copyNextRouteDestinationToClipboard() {
		String next = copyNextRouteDestinationForExec();
		if (next != null) {
			afterDestinationCopiedToClipboard(next);
		}
	}

	/**
	 * Prepares clipboard for fleet cooldown exec: copies the next hop, or clears the clipboard at end of route.
	 */
	public FleetCooldownClipboardPrep prepareFleetCooldownDestinationClipboard() {
		String next = nextRouteDestinationSystemName(routeSession);
		if (next == null || next.isBlank()) {
			clearRouteClipboard();
			return FleetCooldownClipboardPrep.cleared();
		}
		return FleetCooldownClipboardPrep.copied(copyDestinationNameToClipboard(next));
	}

	/**
	 * Copies the next plotted route system to the clipboard and shows the “Copied: …” toast.
	 * Does not fire exec bindings — callers attach their own trigger (e.g. fleet cooldown complete).
	 * Does not clear the clipboard when there is no next hop.
	 *
	 * @return trimmed system name, or {@code null} if there is no next hop
	 */
	public String copyNextRouteDestinationForExec() {
		String next = nextRouteDestinationSystemName(routeSession);
		if (next == null || next.isBlank()) {
			return null;
		}
		return copyDestinationNameToClipboard(next);
	}

	private String copyDestinationNameToClipboard(String systemName) {
		String trimmed = systemName.trim();
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(trimmed), null);
		Component anchor = copyNextDestinationButton != null ? copyNextDestinationButton : table;
		SystemTableHoverCopyManager.showCopiedToast((JComponent) anchor, trimmed);
		return trimmed;
	}

	private static void clearRouteClipboard() {
		try {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(""), null);
		} catch (IllegalStateException ignored) {
			// Clipboard busy (another app owns it); best-effort only.
		}
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
