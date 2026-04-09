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
import java.awt.RenderingHints;
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
import java.util.function.BooleanSupplier;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.ConcurrentHashMap;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;

import org.dce.ed.ui.PassThroughScrollSupport;
import org.dce.ed.ui.SubtleScrollBarUI;

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
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.state.SystemState;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.SystemTableHoverCopyManager;
import org.dce.ed.ui.EdoUi.User;
import org.dce.ed.util.EdsmClient;
import org.dce.ed.route.FuelScoopStarClass;
import org.dce.ed.route.RouteEntry;
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
	// Orange / gray checkmarks for fully discovered systems.
	private static final Icon ICON_FULLY_DISCOVERED_VISITED =
			new StatusCircleIcon(EdoUi.User.MAIN_TEXT, "\u2713");
	private static final Icon ICON_FULLY_DISCOVERED_NOT_VISITED =
			new StatusCircleIcon(EdoUi.STATUS_GRAY, "\u2713");
	// Crossed-out eye equivalents when any body is missing discovery.commander.
	// (Rendered as an X in a colored circle; you can swap to a real eye icon later.)
	private static final Icon ICON_DISCOVERY_MISSING_VISITED =
			new StatusCircleIcon(EdoUi.STATUS_BLUE, "X");
	private static final Icon ICON_DISCOVERY_MISSING_NOT_VISITED =
			new StatusCircleIcon(EdoUi.STATUS_GRAY, "X");
	// BodyCount mismatch between EDSM bodyCount and the number of bodies returned.
	private static final Icon ICON_BODYCOUNT_MISMATCH_VISITED =
			new StatusCircleIcon(EdoUi.STATUS_YELLOW, "!");
	private static final Icon ICON_BODYCOUNT_MISMATCH_NOT_VISITED =
			new StatusCircleIcon(EdoUi.STATUS_GRAY, "!");
	private static final Icon ICON_UNKNOWN =
			new StatusCircleIcon(EdoUi.STATUS_GRAY, "?");
	/** Monochrome green fuel gauge for scoopable stars (Class column). */
	private static final Color FUEL_GAUGE_GREEN = new Color(0x90, 0xC3, 0x8A);
	private static final Icon ICON_FUEL_SCOOP = new FuelGaugeIcon(FUEL_GAUGE_GREEN);
	// Column indexes
	private static final int COL_MARKER    = 0;
	private static final int COL_INDEX    = 1;
	private static final int COL_SYSTEM   = 2;
	private static final int COL_CLASS    = 3;
	private static final int COL_STATUS   = 4;
	private static final int COL_DISTANCE = 5;
	/** Keep current system row at this offset from top when auto-scrolling (e.g. one jump = one row scroll). */
	private static final int TARGET_CURRENT_ROW_OFFSET = 4;
	private final JLabel headerLabel;
	private JTable table=null;
	private JScrollPane routeScrollPane;
	private final RouteTableModel tableModel;
	private SystemTableHoverCopyManager systemTableHoverCopyManager;
	private StatusHoverPopupManager statusHoverPopupManager;
	private final EdsmClient edsmClient;
	private final BooleanSupplier passThroughEnabledSupplier;
	// Caches coordinates we resolved from EDSM (used for inserting synthetic rows).
	private final java.util.Map<String, Double[]> resolvedCoordsCache = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<String> edsmCoordsFetchInProgress = java.util.concurrent.ConcurrentHashMap.newKeySet();
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
		int row = RouteGeometry.findSystemRow(entries, session.getCurrentSystemName(), session.getCurrentSystemAddress());
		int start = row + 1;
		if (row < 0) {
			start = 0;
		}
		for (int i = start; i < entries.size(); i++) {
			RouteEntry e = entries.get(i);
			if (e != null && !e.isBodyRow && e.systemName != null && !e.systemName.isBlank()) {
				return e.systemName.trim();
			}
		}
		return null;
	}

	private final Map<Long, RouteScanStatus> lastKnownScanStatusByAddress = new ConcurrentHashMap<>();
	private final Map<Long, EdsmScanSummary> edsmSummaryByAddress = new ConcurrentHashMap<>();

	/** Same-package test access (not part of public API). */
	RouteSession routeSessionForTests() {
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

	public void applySessionState(EdoSessionState state) {
		if (state == null) {
			return;
		}
		routeSession.applyPersistenceSnapshot(RoutePersistenceAdapter.fromEdoSession(state));
		if (state.getCurrentSystemName() != null) {
			setCurrentSystemName(state.getCurrentSystemName());
		} else if (routeSession.getCurrentSystemName() != null) {
			tableModel.setCurrentSystemName(routeSession.getCurrentSystemName());
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
		table.getColumnModel().getColumn(COL_MARKER).setMaxWidth(20);
		table.getColumnModel().getColumn(COL_MARKER).setPreferredWidth(20);
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
		// Column widths
		TableColumnModel columns = table.getColumnModel();
		columns.getColumn(COL_INDEX).setPreferredWidth(40);   // #
		columns.getColumn(COL_SYSTEM).setPreferredWidth(260); // system name
		columns.getColumn(COL_CLASS).setPreferredWidth(routeClassColumnPreferredWidthPx()); // class + gauge
		columns.getColumn(COL_STATUS).setPreferredWidth(40);  // check/? status
		columns.getColumn(COL_DISTANCE).setPreferredWidth(60); // Ly
		routeScrollPane = new JScrollPane(table);
		routeScrollPane.setOpaque(false);
		routeScrollPane.getViewport().setOpaque(false);
		routeScrollPane.setBorder(null);
		routeScrollPane.setViewportBorder(null);
		if (routeScrollPane.getViewport() != null) {
			routeScrollPane.getViewport().setBorder(null);
			installViewportScrollListener(routeScrollPane.getViewport());
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

		add(headerLabel, BorderLayout.NORTH);
		add(routeScrollPane, BorderLayout.CENTER);

		routeScrollPane.setColumnHeaderView(null);
		table.setTableHeader(null);

		SwingUtilities.invokeLater(() -> {
			TableColumnModel cols = table.getColumnModel();
			cols.getColumn(COL_MARKER).setMinWidth(20);
			cols.getColumn(COL_MARKER).setMaxWidth(20);
			cols.getColumn(COL_MARKER).setPreferredWidth(20);
		});
		// Copy-to-clipboard: hover only in pass-through mode; double-click always copies.
		systemTableHoverCopyManager = new SystemTableHoverCopyManager(table, COL_SYSTEM, passThroughEnabledSupplier);
		systemTableHoverCopyManager.start();
		// Status hover popup: works in both pass-through and non-pass-through modes,
		// and only when hovering directly over the status symbol column.
		statusHoverPopupManager = new StatusHoverPopupManager();
		statusHoverPopupManager.start();
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
	 * Start blinking the hollow triangle for an upcoming jump to the given destination.
	 * <p>
	 * Used by {@link FleetCarrierTabPanel} when a {@code CarrierJumpRequest} is logged
	 * (mirrors FSD “charging” behavior driven by {@link org.dce.ed.logreader.event.StatusEvent}).
	 */
	protected void startPendingJumpBlink(String destName, long destAddress) {
		if (jumpFlashTimer == null) {
			return;
		}
		routeSession.startCarrierPendingJumpBlink(destName, destAddress);
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
	}

	protected void setHeaderLabelText(String text) {
		if (headerLabel != null) {
			headerLabel.setText(text);
		}
	}
	public void setDistanceSumMode(boolean sum) {
		tableModel.setSumDistances(sum);
	}
	public boolean isDistanceSumMode() {
		return tableModel.isSumDistances();
	}
	private void reloadFromNavRouteFile() {
		Path dir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
		if (dir == null) {
			headerLabel.setText("No journal directory.");
			tableModel.setEntries(new ArrayList<>());
			return;
		}
		Path navRoute = dir.resolve("NavRoute.json");
		if (!Files.isRegularFile(navRoute)) {
			headerLabel.setText("No plotted route.");
			tableModel.setEntries(new ArrayList<>());
			return;
		}
		List<RouteEntry> entries;
		try (Reader reader = Files.newBufferedReader(navRoute, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			entries = RouteNavRouteJson.parseNavRouteFromJson(root);
		} catch (Exception e) {
			e.printStackTrace();
			headerLabel.setText("Error reading NavRoute.json");
			tableModel.setEntries(new ArrayList<>());
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
		RouteDisplaySnapshot snap = routeSession.buildDisplaySnapshot(this::applyRememberedScanStatuses, this::resolveSystemCoords);
		tableModel.setEntries(snap.displayedEntries());
		maybeScheduleTargetCoordsFetch(snap.displayedEntries());
		SwingUtilities.invokeLater(() -> {
			startEdsmUpdatesForVisibleRows();
			scrollToKeepCurrentRowAtOffset();
		});
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
		java.awt.Component p = table.getParent();
		if (!(p instanceof javax.swing.JViewport)) {
			return;
		}
		javax.swing.JViewport vp = (javax.swing.JViewport) p;
		int currentRow = getCurrentSystemRowIndex();
		if (currentRow < 0) {
			return;
		}
		int rowHeight = table.getRowHeight();
		int tableHeight = table.getHeight();
		int viewHeight = vp.getExtentSize().height;
		int viewY = (currentRow - TARGET_CURRENT_ROW_OFFSET) * rowHeight;
		viewY = Math.max(0, Math.min(viewY, Math.max(0, tableHeight - viewHeight)));
		java.awt.Point pos = vp.getViewPosition();
		vp.setViewPosition(new java.awt.Point(pos.x != 0 ? pos.x : 0, viewY));
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
		for (RouteEntry e : entries) {
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
		if (entry == null) {
			return;
		}
		// Prefer LOCAL scan state first (this matches in-game "Bodies: N of N Complete")
		RouteScanStatus local = getLocalScanStatus(entry);
		if (local != RouteScanStatus.UNKNOWN) {
			entry.status = local;
			rememberScanStatus(entry, local);
			SwingUtilities.invokeLater(() -> tableModel.fireRowChanged(row));
			return;
		}
		boolean v = isVisited(entry);
		try {
			BodiesResponse bodies = edsmClient.showBodies(entry.systemName);
			RouteScanStatus newStatus = RouteScanStatus.UNKNOWN;
			if (bodies != null && bodies.bodies != null) {
				int returnedBodies = bodies.bodies.size();
				boolean hasBodies = returnedBodies > 0;
				Integer bodyCount = Integer.valueOf(bodies.bodyCount);
				if (entry.systemAddress != 0L) {
					edsmSummaryByAddress.put(Long.valueOf(entry.systemAddress),
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
				} else {
					newStatus = RouteScanStatus.UNKNOWN;
				}
			}
			if (newStatus != RouteScanStatus.UNKNOWN) {
				entry.status = newStatus;
				rememberScanStatus(entry, newStatus);
				SwingUtilities.invokeLater(() -> tableModel.fireRowChanged(row));
			} else {
				// Don't downgrade a known status back to UNKNOWN.
				if (entry.status == null || entry.status == RouteScanStatus.UNKNOWN) {
					entry.status = RouteScanStatus.UNKNOWN;
					SwingUtilities.invokeLater(() -> tableModel.fireRowChanged(row));
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
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
		// If we know the system body count and we don't have them all locally -> X
		if (totalBodies != null && totalBodies > 0 && knownBodies > 0 && knownBodies < totalBodies) {
			return RouteScanStatus.DISCOVERY_MISSING_VISITED;
		}
		// If we know counts match and FSS progress says complete -> checkmark
		Double progress = summary.fssProgress;
		if (totalBodies != null && totalBodies > 0 && knownBodies >= totalBodies
				&& progress != null && progress >= 1.0) {
			return RouteScanStatus.FULLY_DISCOVERED_VISITED;
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
		return cache.getSummary(entry.systemAddress, entry.systemName) != null;
	}

	private String getCurrentSystemName() {
		if (routeSession.getCurrentSystemName() != null && !routeSession.getCurrentSystemName().isBlank()) {
			return routeSession.getCurrentSystemName();
		}
		// Best source: recent journals (works at startup, no live events required)
		String fromJournal = resolveCurrentSystemNameFromJournal();
		if (fromJournal != null && !fromJournal.isBlank()) {
			routeSession.setCurrentSystemName(fromJournal);
			return fromJournal;
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
			tableModel.setCurrentSystemName(cs.systemName);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String resolveCurrentSystemNameFromJournal() {
	    try {
	        Path journalDir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
	        if (journalDir == null || !java.nio.file.Files.isDirectory(journalDir)) {
	            return null;
	        }
	        EliteJournalReader reader = new EliteJournalReader(journalDir);
	        String systemName = null;
	        List<EliteLogEvent> events = reader.readEventsFromLastNJournalFiles(3);
	        for (EliteLogEvent event : events) {
	            if (event instanceof LocationEvent e) {
	                systemName = e.getStarSystem();
	            } else if (event instanceof IFsdJump e) {
	                systemName = e.getStarSystem();
	            }
	        }
	        return systemName;
	    } catch (Exception e) {
	        e.printStackTrace();
	        return null;
	    }
	}
	private void setCurrentSystemName(String name) {
		if (name == null) {
			return;
		}
		routeSession.setCurrentSystemName(name);
		tableModel.setCurrentSystemName(name);
	}
	// ---------------------------------------------------------------------
	// Model + table
	// ---------------------------------------------------------------------
	private static final class RouteTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;
		private final List<RouteEntry> entries = new ArrayList<>();
		private boolean sumDistances = true;
		private String currentSystemName;
		void setCurrentSystemName(String currentSystemName) {
			this.currentSystemName = currentSystemName;
			fireTableDataChanged();
		}

		private int findCurrentSystemRow() {
			if (currentSystemName == null) {
				return -1;
			}
			for (int i = 0; i < entries.size(); i++) {
				String name = entries.get(i).systemName;
				if (currentSystemName.equals(name)) {
					return i;
				}
			}
			return -1;
		}

		void setSumDistances(boolean sumDistances) {
			if (this.sumDistances != sumDistances) {
				this.sumDistances = sumDistances;
				fireTableDataChanged();
			}
		}
		boolean isSumDistances() {
			return sumDistances;
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
				// Toggle locally while you iterate:
				// true  = along-track distance (sum of legs between current row and this row)
				// false = straight-line distance from current system to this system (uses StarPos)
				final boolean useAlongTrackDistance = true;
				int currentRow = findCurrentSystemRow();
				// If we truly don't know where we are, don't guess.
				if (currentRow < 0) {
					return "";
				}
				// Current system row: show blank
				if (rowIndex == currentRow) {
					return "";
				}
				if (!useAlongTrackDistance) {
					RouteEntry cur = entries.get(currentRow);
					RouteEntry dst = entries.get(rowIndex);
					if (cur.x == null || cur.y == null || cur.z == null
							|| dst.x == null || dst.y == null || dst.z == null) {
						return "";
					}
					double dx = dst.x.doubleValue() - cur.x.doubleValue();
					double dy = dst.y.doubleValue() - cur.y.doubleValue();
					double dz = dst.z.doubleValue() - cur.z.doubleValue();
					double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
					return String.format("%.2f Ly", dist);
				}
				int from = Math.min(rowIndex, currentRow);
				int to = Math.max(rowIndex, currentRow);
				double total = 0.0;
				// distanceLy at index i is the distance from (i-1) -> i
				for (int i = from + 1; i <= to; i++) {
					Double d = entries.get(i).distanceLy;
					if (d == null) {
						// If any leg along the path is unknown, we can't compute the total
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
			 * If we just plotted a route, we still want a deterministic "current row"
			 * even before we have a Location/FSD event to tell us where we are.
			 *
			 * - If currentSystemName is null, default to the origin (row 0).
			 * - If currentSystemName doesn't exist in the new route, also default to row 0.
			 */
			if (!entries.isEmpty()) {
				if (currentSystemName == null || findCurrentSystemRow() < 0) {
					currentSystemName = entries.get(0).systemName;
				}
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
	private static final class StatusCircleIcon implements Icon {
		private final Color circleColor;
		private final String symbol;
		StatusCircleIcon(Color circleColor, String symbol) {
			this(circleColor, symbol, 18);
		}
		StatusCircleIcon(Color circleColor, String symbol, int size) {
			this.circleColor = circleColor;
			this.symbol = symbol;
			//            this.size = size;
		}
		@Override
		public int getIconWidth() {
			return getFontSize();
		}
		@Override
		public int getIconHeight() {
			return getFontSize();
		}
		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);
				int d = getFontSize() - 1;
				g2.setColor(circleColor);
				g2.fillOval(x, y, d, d);
				g2.setColor(Color.BLACK);
				g2.drawOval(x, y, d, d);
				if (symbol != null && !symbol.isEmpty()) {
					Font font = iconFont();
					if (font != null) {
						font = font.deriveFont(Font.BOLD); // 
						//                                               Math.max(10f, font.getSize2D()));
						g2.setFont(font);
					}
					java.awt.FontMetrics fm = g2.getFontMetrics();
					int textWidth = fm.stringWidth(symbol);
					int textAscent = fm.getAscent();
					int tx = x + (getFontSize() - textWidth) / 2;
					int ty = y + (getFontSize() + textAscent) / 2 - 2;
					g2.drawString(symbol, tx, ty);
				}
			} finally {
				g2.dispose();
			}
		}
		/**
		 * @return the size
		 */
		private int getFontSize() {
			return OverlayPreferences.getUiFontSize();
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
			if (Boolean.TRUE.equals(all)) {
				journalStatus = "Complete";
			} else if (knownBodies > 0) {
				journalStatus = "In progress";
			}
			if (totalBodies != null && totalBodies.intValue() > 0) {
				// Prefer observed/expected so users can see progress and target totals.
				journalBodies = knownBodies + " / " + totalBodies.intValue();
			} else {
				journalBodies = Integer.toString(knownBodies);
			}
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

	private static String escapeHtml(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;");
	}
	private class StatusHoverPopupManager {
		private static final int POLL_INTERVAL_MS = 100;
		private static final int HOVER_DELAY_MS = 400;

		private final javax.swing.Timer pollTimer =
				new javax.swing.Timer(POLL_INTERVAL_MS, e -> pollMousePosition());
		private final javax.swing.Timer hoverTimer =
				new javax.swing.Timer(HOVER_DELAY_MS, e -> showPopupIfStillHovering());

		private int hoverViewRow = -1;
		private javax.swing.JComponent currentPopup;

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

			int statusViewCol = table.convertColumnIndexToView(COL_STATUS);
			if (viewCol != statusViewCol) {
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

			RouteEntry entry;
			try {
				entry = tableModel.getEntries(table.convertRowIndexToModel(viewRow));
			} catch (Exception ex) {
				return;
			}
			if (entry == null) {
				return;
			}

			String html = buildStatusHoverHtml(entry);
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
			java.awt.Rectangle cellRect = table.getCellRect(viewRow, table.convertColumnIndexToView(COL_STATUS), true);
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
			String text = value != null ? value.toString() : "";
			l.setText(text);
			if (e != null && FuelScoopStarClass.isFuelScoopable(e.starClass)) {
				l.setIcon(ICON_FUEL_SCOOP);
				l.setIconTextGap(4);
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
			super.paintComponent(g2);
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

	private static int routeClassColumnPreferredWidthPx() {
		return Math.max(40, fuelGaugeIconSizePx() + 22);
	}

	/**
	 * Fuel gauge: rim, ticks, hub, and needle in one green; needle pivots at bottom-center.
	 * Size scales with {@link OverlayPreferences#getUiFontSize()}.
	 */
	private static final class FuelGaugeIcon implements Icon {
		private final Color green;
		FuelGaugeIcon(Color green) {
			this.green = green;
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
			int sz = fuelGaugeIconSizePx();
			Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				float cx = x + sz / 2f;
				float cy = y + sz / 2f;
				float margin = Math.max(0.65f, sz * 0.042f);
				float outerR = sz / 2f - margin;
				float rimW = Math.max(0.75f, sz * 0.048f);
				float tickW = Math.max(0.7f, sz * 0.048f);
				float needleW = Math.max(0.9f, sz * 0.058f);
				g2.setColor(green);
				g2.setStroke(new BasicStroke(rimW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.draw(new Ellipse2D.Float(cx - outerR, cy - outerR, 2 * outerR, 2 * outerR));
				float pad = Math.max(0.85f, sz * 0.05f);
				float rTickOut = outerR - pad;
				float rTickInFull = outerR * 0.36f;
				// Half the radial span of the original tick marks
				float rTickIn = rTickOut - (rTickOut - rTickInFull) * 0.5f;
				g2.setStroke(new BasicStroke(tickW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				for (int k = 0; k < 5; k++) {
					double ang = Math.PI + k * (Math.PI / 4);
					float x1 = (float) (cx + rTickIn * Math.cos(ang));
					float y1 = (float) (cy + rTickIn * Math.sin(ang));
					float x2 = (float) (cx + rTickOut * Math.cos(ang));
					float y2 = (float) (cy + rTickOut * Math.sin(ang));
					g2.draw(new Line2D.Float(x1, y1, x2, y2));
				}
				// Pivot hub at bottom-center; needle angled up-left of the old 45° (tip nudged left & up).
				float px = cx;
				float py = cy + outerR * 0.62f;
				float pivotR = Math.max(0.65f, sz * 0.085f);
				double nAng = Math.toRadians(-52);
				float nx = (float) Math.cos(nAng);
				float ny = (float) Math.sin(nAng);
				float needleLen = outerR * 0.78f;
				float tipX = px + needleLen * nx - sz * 0.035f;
				float tipY = py + needleLen * ny - sz * 0.04f;
				// Stem on hub circle toward needle; slight inward bias at small sizes so AA doesn’t look offset
				float stemScale = pivotR - Math.min(0.55f, needleW * 0.45f)
						- Math.max(0f, (28 - sz) * 0.028f);
				stemScale = Math.max(pivotR * 0.78f, stemScale);
				float stemX = px + stemScale * nx;
				float stemY = py + stemScale * ny;
				g2.fill(new Ellipse2D.Float(px - pivotR, py - pivotR, 2 * pivotR, 2 * pivotR));
				g2.setStroke(new BasicStroke(needleW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.draw(new Line2D.Float(stemX, stemY, tipX, tipY));
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
		if (table != null) {
			table.setFont(uiFont);
			table.setRowHeight(computeRowHeight(table, uiFont, 6));
			if (table.getTableHeader() != null) {
				table.getTableHeader().setFont(uiFont.deriveFont(Font.BOLD));
			}
			TableColumnModel tcm = table.getColumnModel();
			if (tcm.getColumnCount() > COL_CLASS) {
				tcm.getColumn(COL_CLASS).setPreferredWidth(routeClassColumnPreferredWidthPx());
			}
			table.revalidate();
		}
		repaint();
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
	private static Font iconFont() {
		// Consolas exists on most Windows installs.
		// If it’s missing, fall back to logical Monospaced.
		String family = "SansSerif";
		Font uiFont = OverlayPreferences.getUiFont();
		Font f = new Font(family, Font.BOLD, uiFont.getSize() -1);
		if (!family.equalsIgnoreCase(f.getFamily())) {
			f = new Font(Font.MONOSPACED, Font.PLAIN, uiFont.getSize());
		}
		return f;
	}
	
}
