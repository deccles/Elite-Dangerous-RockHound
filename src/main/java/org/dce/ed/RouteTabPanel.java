package org.dce.ed;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;

import org.dce.ed.cache.CachedSystem;
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
	// Column indexes
	private static final int COL_MARKER    = 0;
	private static final int COL_INDEX    = 1;
	private static final int COL_SYSTEM   = 2;
	private static final int COL_CLASS    = 3;
	private static final int COL_STATUS   = 4;
	private static final int COL_DISTANCE = 5;
	private final JLabel headerLabel;
	private JTable table=null;
	private final RouteTableModel tableModel;
	private final EdsmClient edsmClient;
	// Caches coordinates we resolved from EDSM (used for inserting synthetic rows).
	private final java.util.Map<String, Double[]> resolvedCoordsCache = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<String> edsmCoordsFetchInProgress = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private String currentSystemName = null;
	private long currentSystemAddress = 0L;
	private double[] currentStarPos = null;
	private String pendingJumpSystemName = null;
	// Latched pending-jump destination (so Status destination changes during hyperspace don't move the marker).
	private String pendingJumpLockedName = null;
	private long pendingJumpLockedAddress = 0L;
	private boolean inHyperspace = false;
	private String targetSystemName = null;
	private long targetSystemAddress = 0L;
	// Destination can be either a system (FSD target), or a body in the current system.
	private Long destinationSystemAddress = null;
	private Integer destinationBodyId = null;
	private String destinationName = null;
	// Last raw navroute entries (no synthetic rows). We rebuild the displayed list from this.
	private List<RouteEntry> baseRouteEntries = new ArrayList<>();
	private boolean jumpFlashOn = true;
	private final Timer jumpFlashTimer = new Timer(500, e -> {
		jumpFlashOn = !jumpFlashOn;
		table.repaint();
	});

	/** Optional callback when route state changes (for debounced session persist). */
	private Runnable sessionStateChangeCallback;

	public void setSessionStateChangeCallback(Runnable callback) {
		this.sessionStateChangeCallback = callback;
	}

	private void fireSessionStateChanged() {
		if (sessionStateChangeCallback != null) {
			sessionStateChangeCallback.run();
		}
	}

	/** Fill route-related fields of the given session state (for save). */
	public void fillSessionState(EdoSessionState state) {
		if (state == null) return;
		state.setCurrentSystemName(currentSystemName);
		state.setCurrentSystemAddress(currentSystemAddress != 0L ? Long.valueOf(currentSystemAddress) : null);
		state.setCurrentStarPos(currentStarPos != null && currentStarPos.length > 0 ? currentStarPos : null);
		state.setTargetSystemName(targetSystemName);
		state.setTargetSystemAddress(targetSystemAddress != 0L ? Long.valueOf(targetSystemAddress) : null);
		state.setDestinationSystemAddress(destinationSystemAddress);
		state.setDestinationBodyId(destinationBodyId);
		state.setDestinationName(destinationName);
		state.setPendingJumpLockedName(pendingJumpLockedName);
		state.setPendingJumpLockedAddress(pendingJumpLockedAddress != 0L ? Long.valueOf(pendingJumpLockedAddress) : null);
		state.setInHyperspace(inHyperspace);
	}

	/** Apply persisted route state (for restore on startup). */
	public void applySessionState(EdoSessionState state) {
		if (state == null) return;
		if (state.getCurrentSystemName() != null) {
			setCurrentSystemName(state.getCurrentSystemName());
		}
		if (state.getCurrentSystemAddress() != null) {
			currentSystemAddress = state.getCurrentSystemAddress().longValue();
		}
		if (state.getCurrentStarPos() != null && state.getCurrentStarPos().length >= 3) {
			currentStarPos = state.getCurrentStarPos();
		}
		if (state.getTargetSystemName() != null) {
			targetSystemName = state.getTargetSystemName();
		} else {
			targetSystemName = null;
		}
		if (state.getTargetSystemAddress() != null) {
			targetSystemAddress = state.getTargetSystemAddress().longValue();
		} else {
			targetSystemAddress = 0L;
		}
		destinationSystemAddress = state.getDestinationSystemAddress();
		destinationBodyId = state.getDestinationBodyId();
		destinationName = state.getDestinationName();
		pendingJumpLockedName = state.getPendingJumpLockedName();
		pendingJumpLockedAddress = (state.getPendingJumpLockedAddress() != null) ? state.getPendingJumpLockedAddress().longValue() : 0L;
		if (state.getInHyperspace() != null) {
			inHyperspace = state.getInHyperspace().booleanValue();
		}
		rebuildDisplayedEntries();
	}

	public RouteTabPanel() {
		super(new BorderLayout());
		setOpaque(false);
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
		columns.getColumn(COL_CLASS).setPreferredWidth(40);   // class
		columns.getColumn(COL_STATUS).setPreferredWidth(40);  // check/? status
		columns.getColumn(COL_DISTANCE).setPreferredWidth(60); // Ly
		JScrollPane scroll = new JScrollPane(table);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setBorder(null);
		scroll.setViewportBorder(null);
		if (scroll.getViewport() != null) {
			scroll.getViewport().setBorder(null);
		}
		if (scroll.getColumnHeader() != null) {
			scroll.getColumnHeader().setBorder(null);
		}
		JTableHeader th = table.getTableHeader();
		if (th != null) {
			th.setBorder(null);
		}
		th.setBorder(null);

		add(headerLabel, BorderLayout.NORTH);
		add(scroll, BorderLayout.CENTER);

		scroll.setColumnHeaderView(null);
		table.setTableHeader(null);

		SwingUtilities.invokeLater(() -> {
			TableColumnModel cols = table.getColumnModel();
			cols.getColumn(COL_MARKER).setMinWidth(20);
			cols.getColumn(COL_MARKER).setMaxWidth(20);
			cols.getColumn(COL_MARKER).setPreferredWidth(20);
		});
		// Copy-to-clipboard behavior on hover for the system name column,
		// consistent with SystemTabPanel.
		SystemTableHoverCopyManager systemTableHoverCopyManager = new SystemTableHoverCopyManager(table, COL_SYSTEM);
		systemTableHoverCopyManager.start();

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
			// Route cleared: no active FSD target anymore
			targetSystemName = null;
			targetSystemAddress = 0L;
			// Clear Status.json destination state too; otherwise we can display stale synthetic rows.
			destinationSystemAddress = null;
			destinationBodyId = null;
			destinationName = null;
			baseRouteEntries.clear();
			pendingJumpSystemName = null;
			pendingJumpLockedName = null;
			pendingJumpLockedAddress = 0L;
			inHyperspace = false;
			if (jumpFlashTimer != null && jumpFlashTimer.isRunning()) {
				jumpFlashTimer.stop();
			}
			jumpFlashOn = true;
			rebuildDisplayedEntries();
			table.repaint();
		}
		if (event instanceof FsdTargetEvent target) {
			// Elite can emit FSDTarget updates during the hyperspace animation (often for the *next* hop).
			// We must not let that override the blinking pending-jump marker or shift the hollow triangle.
			if (inHyperspace || jumpFlashTimer.isRunning()) {
				return;
			}

			// FSD target selected or cleared: remember (or clear) the target system for the crosshair
			String newName = target.getName();
			long newAddr = target.getSystemAddress();

			if (newName == null || newName.isBlank() || newAddr == 0L) {
				targetSystemName = null;
				targetSystemAddress = 0L;
			} else {
				targetSystemName = newName;
				targetSystemAddress = newAddr;
			}

			rebuildDisplayedEntries();
		}

		if (event instanceof LocationEvent loc) {
			setCurrentSystemName(loc.getStarSystem());
			currentSystemAddress = loc.getSystemAddress();
			currentStarPos = loc.getStarPos();
			pendingJumpSystemName = null;
			pendingJumpLockedName = null;
			pendingJumpLockedAddress = 0L;
			inHyperspace = false;
			rebuildDisplayedEntries();
		}
		if (event instanceof FsdJumpEvent jump) {
			setCurrentSystemName(jump.getStarSystem());
			Long currentSystemAddress = jump.getSystemAddress();
			this.currentSystemAddress = currentSystemAddress != null ? currentSystemAddress.longValue() : 0L;
			this.currentStarPos = jump.getStarPos();

			pendingJumpSystemName = null;
			pendingJumpLockedName = null;
			pendingJumpLockedAddress = 0L;
			inHyperspace = false;

			if (jumpFlashTimer != null && jumpFlashTimer.isRunning()) {
				jumpFlashTimer.stop();
			}
			jumpFlashOn = true;
			pendingJumpSystemName = null;
			
			setCurrentSystemIfEmpty(getCurrentSystemName(), currentSystemAddress);
			rebuildDisplayedEntries();
		}
		if (event instanceof CarrierJumpEvent jump) {
		    setCurrentSystemName(jump.getStarSystem());
		    this.currentSystemAddress = jump.getSystemAddress();
		    this.currentStarPos = jump.getStarPos();

		    pendingJumpSystemName = null;
		    pendingJumpLockedName = null;
		    pendingJumpLockedAddress = 0L;
		    inHyperspace = false;

		    if (jumpFlashTimer != null && jumpFlashTimer.isRunning()) {
		        jumpFlashTimer.stop();
		    }
		    jumpFlashOn = true;

		    rebuildDisplayedEntries();
		}
		if (event instanceof CarrierLocationEvent loc) {
		    // Some sessions (especially when docked/on-foot in a carrier) may emit CarrierLocation but not Location.
		    setCurrentSystemName(loc.getStarSystem());
		    this.currentSystemAddress = loc.getSystemAddress();

		    pendingJumpSystemName = null;
		    pendingJumpLockedName = null;
		    pendingJumpLockedAddress = 0L;
		    inHyperspace = false;

		    rebuildDisplayedEntries();
		}

		if (event instanceof FssAllBodiesFoundEvent) {
			FssAllBodiesFoundEvent fss = (FssAllBodiesFoundEvent)event;

			reloadFromNavRouteFile();
		}
		if (event instanceof StatusEvent sj) {
			StatusEvent se = (StatusEvent)sj;
			boolean hyperdriveCharging = se.isFsdHyperdriveCharging();
			boolean inHyperspaceNow = se.isFsdJump();
			inHyperspace = inHyperspaceNow;
			boolean preJumpCharging = hyperdriveCharging && !inHyperspaceNow;
			boolean timerRunning = jumpFlashTimer.isRunning();

			// Remember destination fields (they may refer to either a target system or a body).
			destinationSystemAddress = se.getDestinationSystem();
			destinationBodyId = se.getDestinationBody();
			if (destinationBodyId != null && destinationBodyId.intValue() == 0) {
			    destinationBodyId = null;
			}
			destinationName = se.getDestinationDisplayName();

			// Side-trip clearing:
			// When the side-trip target is cleared in-game, Status 'Destination' typically snaps back to the plotted route
			// (next hop or final destination), and Elite may not emit a dedicated "target cleared" journal event.
			// If Status destination is blank OR refers to a system on the plotted route, drop any latched off-route FsdTarget.
			String statusDestName = destinationName;
			boolean clearedSideTrip = false;

			if (statusDestName == null || statusDestName.isBlank()) {
				if (targetSystemName != null) {
					targetSystemName = null;
					targetSystemAddress = 0L;
					clearedSideTrip = true;
				}
			} else {
				boolean statusDestIsOnRoute = false;
				boolean targetIsOnRoute = false;

				if (baseRouteEntries != null && !baseRouteEntries.isEmpty()) {
					for (RouteEntry e : baseRouteEntries) {
						if (e == null) {
							continue;
						}

						if (statusDestName.equals(e.systemName)) {
							statusDestIsOnRoute = true;
						}

						if (targetSystemName != null && !targetSystemName.isBlank()) {
							if (targetSystemName.equals(e.systemName)) {
								targetIsOnRoute = true;
							}
						}

						if (targetSystemAddress != 0L && e.systemAddress != 0L) {
							if (e.systemAddress == targetSystemAddress) {
								targetIsOnRoute = true;
							}
						}

						if (statusDestIsOnRoute && targetIsOnRoute) {
							break;
						}
					}
				}

				if (statusDestIsOnRoute) {
					// Only clear the latched target if it was an *off-route* side-trip target.
					if (targetSystemName != null && !targetIsOnRoute) {
						targetSystemName = null;
						targetSystemAddress = 0L;
						clearedSideTrip = true;
					}
				}
			}

			if (clearedSideTrip) {
				rebuildDisplayedEntries();
				return;
			}
			if (preJumpCharging && !timerRunning) {
				// Latch the destination at the moment charging begins. Status destination can change mid-jump.
				pendingJumpLockedName = destinationName;
				pendingJumpLockedAddress = (destinationSystemAddress != null) ? destinationSystemAddress.longValue() : 0L;
				pendingJumpSystemName = se.getDestinationDisplayName();
				jumpFlashOn = true;
				jumpFlashTimer.start();
			}
			if (!preJumpCharging && !inHyperspaceNow && timerRunning) {
				// Charging was canceled (or we returned to normal space without an FSDJump).
				jumpFlashTimer.stop();
				pendingJumpSystemName = null;
				pendingJumpLockedName = null;
				pendingJumpLockedAddress = 0L;
				jumpFlashOn = true;
			}

			rebuildDisplayedEntries();
		}
		fireSessionStateChanged();
	}
	private void setCurrentSystemIfEmpty(String systemName, long systemAddress) {
		if (tableModel.getRowCount() > 0) {
			return; // route exists, nothing to do
		}
		RouteEntry entry = new RouteEntry(
				0,                    // index
				systemName,
				systemAddress,
				"?",                  // class until EDSM loads
				0.0,                   // Ly
				ScanStatus.UNKNOWN       // use whatever your enum is
				);
		//        int index;
		//        String systemName;
		//        long systemAddress;
		//        String starClass;
		//        Double distanceLy;
		//        ScanStatus status;

		List<RouteEntry> list = new ArrayList<>();
		list.add(entry);
		tableModel.setEntries(list);
		tableModel.fireTableDataChanged();
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
		targetSystemName = null;
		targetSystemAddress = 0L;
		destinationSystemAddress = null;
		destinationBodyId = null;
		destinationName = null;

		List<RouteEntry> entries;
		try (Reader reader = Files.newBufferedReader(navRoute, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			entries = parseNavRouteFromJson(root);
		} catch (Exception e) {
			e.printStackTrace();
			headerLabel.setText("Error reading NavRoute.json");
			tableModel.setEntries(new ArrayList<>());
			return;
		}
		headerLabel.setText(entries.isEmpty()
				? "No plotted route."
						: "Route: " + entries.size() + " systems");
		// Save the raw NavRoute entries (no synthetic rows). We'll rebuild the displayed list
		// whenever current/destination changes.
		baseRouteEntries = deepCopy(entries);
		rebuildDisplayedEntries();
	}

	/**
	 * Parses NavRoute.json-style JSON (root with "Route" array) into a list of RouteEntry with distances.
	 * Package-visible for unit tests.
	 */
	static List<RouteEntry> parseNavRouteFromJson(JsonObject root) {
		List<RouteEntry> entries = new ArrayList<>();
		if (root == null || !root.has("Route") || !root.get("Route").isJsonArray()) {
			return entries;
		}
		JsonArray route = root.getAsJsonArray("Route");
		List<double[]> coords = new ArrayList<>();
		for (JsonElement elem : route) {
			if (!elem.isJsonObject()) {
				continue;
			}
			JsonObject obj = elem.getAsJsonObject();
			String systemName    = safeString(obj, "StarSystem");
			long systemAddress   = safeLong(obj, "SystemAddress");
			String starClass     = safeString(obj, "StarClass");
			JsonArray pos        = obj.getAsJsonArray("StarPos");
			RouteEntry entry = new RouteEntry();
			entry.index = entries.size();
			entry.systemName    = systemName;
			entry.systemAddress = systemAddress;
			entry.starClass     = starClass;
			entry.status        = ScanStatus.UNKNOWN;
			entries.add(entry);
			if (pos != null && pos.size() == 3) {
				double x = pos.get(0).getAsDouble();
				double y = pos.get(1).getAsDouble();
				double z = pos.get(2).getAsDouble();
				entry.x = Double.valueOf(x);
				entry.y = Double.valueOf(y);
				entry.z = Double.valueOf(z);
				coords.add(new double[] { x, y, z });
			} else {
				entry.x = null;
				entry.y = null;
				entry.z = null;
				coords.add(null);
			}
		}
		// Compute per-jump distances (Ly) from the StarPos coordinates
		for (int i = 0; i < entries.size(); i++) {
			if (i == 0) {
				entries.get(i).distanceLy = null; // origin system
			} else {
				double[] prev = coords.get(i - 1);
				double[] cur  = coords.get(i);
				if (prev == null || cur == null) {
					entries.get(i).distanceLy = null;
				} else {
					double dx = cur[0] - prev[0];
					double dy = cur[1] - prev[1];
					double dz = cur[2] - prev[2];
					double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
					entries.get(i).distanceLy = dist;
				}
			}
		}
		return entries;
	}
	private void rebuildDisplayedEntries() {
		List<RouteEntry> working = deepCopy(baseRouteEntries);
		applyRememberedScanStatuses(working);
		
		applySyntheticCurrentRow(working);
		applySyntheticTargetRow(working);
		applySyntheticDestinationBodyRow(working);
		recomputeLegDistances(working);
		renumberDisplayIndexes(working);
		applyMarkerKinds(working);
		tableModel.setEntries(working);
		// Async EDSM lookups to refine status icons (skip body rows).
		for (int row = 0; row < working.size(); row++) {
			RouteEntry entry = working.get(row);
			if (entry == null || entry.isBodyRow) {
				continue;
			}
			final int r = row;
			new Thread(() -> updateStatusFromEdsm(entry, r),
					"RouteEdsm-" + entry.systemName).start();
		}
	}
	static List<RouteEntry> deepCopy(List<RouteEntry> entries) {
		List<RouteEntry> out = new ArrayList<>();
		if (entries == null) {
			return out;
		}
		for (RouteEntry e : entries) {
			if (e == null) {
				continue;
			}
			out.add(e.copy());
		}
		return out;
	}
	private void applySyntheticCurrentRow(List<RouteEntry> entries) {
		String curName = getCurrentSystemName();
		if (curName == null || curName.isBlank()) {
			return;
		}
		if (findSystemRow(entries, curName, currentSystemAddress) >= 0) {
			return;
		}
		Double[] coords = resolveSystemCoords(curName, currentSystemAddress, currentStarPos);
		RouteEntry synthetic = RouteEntry.syntheticSystem(curName, currentSystemAddress, coords, MarkerKind.CURRENT);
		int insertAt = bestInsertionIndexByCoords(entries, coords);
		entries.add(insertAt, synthetic);
	}
	private void applySyntheticTargetRow(List<RouteEntry> entries) {
		if (targetSystemName == null || targetSystemName.isBlank()) {
			return;
		}
		if (findSystemRow(entries, targetSystemName, targetSystemAddress) >= 0) {
			return;
		}
		// For targets we may not know coordinates yet; try cache first, then kick off an async EDSM lookup.
		Double[] coords = resolveSystemCoords(targetSystemName, targetSystemAddress, null);
		RouteEntry synthetic = RouteEntry.syntheticSystem(targetSystemName, targetSystemAddress, coords, MarkerKind.TARGET);
		int insertAt = bestInsertionIndexByCoords(entries, coords);
		entries.add(insertAt, synthetic);
		if (coords == null && targetSystemName != null) {
			final String targetName = targetSystemName;
			if (edsmCoordsFetchInProgress.add(targetName)) {
				Thread t = new Thread(() -> {
					try {
						Double[] fetched = resolveSystemCoordsFromEdsm(targetName);
						if (fetched == null) {
							return;
						}
						SwingUtilities.invokeLater(() -> {
							// Rebuild again now that we have coordinates; this will relocate the row to a better position.
							// (Also avoids mutating the table model list in-place from a background thread.)
							rebuildDisplayedEntries();
						});
					} finally {
						edsmCoordsFetchInProgress.remove(targetName);
					}
				}, "RouteTargetCoords-" + targetName);
				t.setDaemon(true);
				t.start();
			}
		}
	}
	private void applySyntheticDestinationBodyRow(List<RouteEntry> entries) {
		if (destinationName == null || destinationName.isBlank()) {
			return;
		}
		if (destinationBodyId == null) {
			return; // not a body destination
		}
		// We only add a synthetic *body* row when Status.json tells us which system the body is in.
		// If we don't have a system address, it's too easy to accidentally display an extra row for a
		// totally unrelated target (or for the plotted destination system).
		if (destinationSystemAddress == null) {
			return;
		}
		// If the destination name is already being treated as a system target, don't also add a body row.
		if (targetSystemName != null && destinationName.equals(targetSystemName)) {
			return;
		}
		// If the destination name is the system name, treat it as a system target (handled elsewhere).
		if (destinationName.equals(getCurrentSystemName())) {
			return;
		}
		// Avoid duplicates on rebuild.
		for (RouteEntry e : entries) {
			if (e != null && e.isBodyRow && destinationName.equals(e.systemName)) {
				return;
			}
		}
		int currentRow = findSystemRow(entries, getCurrentSystemName(), currentSystemAddress);
		// Prefer the address from the actual row (it might exist even if currentSystemAddress is still 0).
		long resolvedCurrentAddress = 0L;
		if (currentRow >= 0) {
			RouteEntry cur = entries.get(currentRow);
			if (cur != null) {
				resolvedCurrentAddress = cur.systemAddress;
			}
		}
		if (resolvedCurrentAddress == 0L) {
			resolvedCurrentAddress = currentSystemAddress;
		}
		// Enforce "same system" for a body destination.
		if (resolvedCurrentAddress != 0L) {
			if (destinationSystemAddress.longValue() != resolvedCurrentAddress) {
				return;
			}
		}
		int insertAt = (currentRow >= 0) ? currentRow + 1 : 0;
		RouteEntry body = RouteEntry.syntheticBody(destinationName);
		body.indentLevel = 1;
		body.markerKind = MarkerKind.TARGET; // empty triangle
		entries.add(Math.min(insertAt, entries.size()), body);
	}
	static int findSystemRow(List<RouteEntry> entries, String systemName, long systemAddress) {
		if (entries == null) {
			return -1;
		}
		for (int i = 0; i < entries.size(); i++) {
			RouteEntry e = entries.get(i);
			if (e == null || e.isBodyRow) {
				continue;
			}
			if (systemAddress != 0L && e.systemAddress == systemAddress) {
				return i;
			}
			if (systemName != null && systemName.equals(e.systemName)) {
				return i;
			}
		}
		return -1;
	}
	static int bestInsertionIndexByCoords(List<RouteEntry> entries, Double[] coords) {
		if (entries == null || entries.isEmpty()) {
			return 0;
		}
		if (coords == null || coords[0] == null || coords[1] == null || coords[2] == null) {
			return entries.size();
		}
		double[] p = new double[] { coords[0].doubleValue(), coords[1].doubleValue(), coords[2].doubleValue() };
		// Find the closest segment in 3D between consecutive entries that have coords.
		double best = Double.POSITIVE_INFINITY;
		int bestAfter = entries.size();
		for (int i = 0; i < entries.size() - 1; i++) {
			RouteEntry a = entries.get(i);
			RouteEntry b = entries.get(i + 1);
			if (a == null || b == null || a.isBodyRow || b.isBodyRow) {
				continue;
			}
			if (a.x == null || a.y == null || a.z == null || b.x == null || b.y == null || b.z == null) {
				continue;
			}
			double[] v = new double[] { a.x.doubleValue(), a.y.doubleValue(), a.z.doubleValue() };
			double[] w = new double[] { b.x.doubleValue(), b.y.doubleValue(), b.z.doubleValue() };
			double d = pointToSegmentDistanceSquared(p, v, w);
			if (d < best) {
				best = d;
				bestAfter = i + 1;
			}
		}
		// If we never found any segment with coords, fall back to the end.
		return bestAfter;
	}
	private static double pointToSegmentDistanceSquared(double[] p, double[] v, double[] w) {
		double[] vw = new double[] { w[0] - v[0], w[1] - v[1], w[2] - v[2] };
		double[] vp = new double[] { p[0] - v[0], p[1] - v[1], p[2] - v[2] };
		double c1 = vp[0] * vw[0] + vp[1] * vw[1] + vp[2] * vw[2];
		if (c1 <= 0) {
			return squaredDistance(p, v);
		}
		double c2 = vw[0] * vw[0] + vw[1] * vw[1] + vw[2] * vw[2];
		if (c2 <= c1) {
			return squaredDistance(p, w);
		}
		double t = c1 / c2;
		double[] proj = new double[] { v[0] + t * vw[0], v[1] + t * vw[1], v[2] + t * vw[2] };
		return squaredDistance(p, proj);
	}
	private static double squaredDistance(double[] a, double[] b) {
		double dx = a[0] - b[0];
		double dy = a[1] - b[1];
		double dz = a[2] - b[2];
		return dx * dx + dy * dy + dz * dz;
	}
	static void recomputeLegDistances(List<RouteEntry> entries) {
		if (entries == null) {
			return;
		}
		for (int i = 0; i < entries.size(); i++) {
			RouteEntry cur = entries.get(i);
			if (cur == null || cur.isBodyRow) {
				continue;
			}
			if (i == 0) {
				cur.distanceLy = null;
				continue;
			}
			RouteEntry prev = entries.get(i - 1);
			if (prev == null || prev.isBodyRow) {
				cur.distanceLy = null;
				continue;
			}
			if (prev.x == null || prev.y == null || prev.z == null
					|| cur.x == null || cur.y == null || cur.z == null) {
				cur.distanceLy = null;
				continue;
			}
			double dx = cur.x.doubleValue() - prev.x.doubleValue();
			double dy = cur.y.doubleValue() - prev.y.doubleValue();
			double dz = cur.z.doubleValue() - prev.z.doubleValue();
			cur.distanceLy = Math.sqrt(dx * dx + dy * dy + dz * dz);
		}
	}
	static void renumberDisplayIndexes(List<RouteEntry> entries) {
		int n = 1;
		for (RouteEntry e : entries) {
			if (e == null) {
				continue;
			}
			if (e.isSynthetic || e.isBodyRow) {
				e.displayIndex = null;
				continue;
			}
			e.displayIndex = Integer.valueOf(n);
			n++;
		}
	}
	private void applyMarkerKinds(List<RouteEntry> entries) {
		if (entries == null) {
			return;
		}

		String currentName = getCurrentSystemName();

		// Clear marker kinds first (body rows manage their own markerKind).
		for (RouteEntry e : entries) {
			if (e == null) {
				continue;
			}
			if (e.isBodyRow) {
				continue;
			}
			e.markerKind = MarkerKind.NONE;
		}

		// Mark current system row.
		int currentRow = findSystemRow(entries, currentName, currentSystemAddress);
		if (currentRow >= 0) {
			RouteEntry cur = entries.get(currentRow);
			if (cur != null && !cur.isBodyRow) {
				cur.markerKind = MarkerKind.CURRENT;
			}
		}

		// Identify the next plotted hop (first non-synthetic, non-body row after current).
		RouteEntry nextHop = null;
		if (currentRow >= 0) {
			for (int i = currentRow + 1; i < entries.size(); i++) {
				RouteEntry e = entries.get(i);
				if (e == null) {
					continue;
				}
				if (e.isBodyRow) {
					continue;
				}
				if (e.isSynthetic) {
					continue;
				}
				nextHop = e;
				break;
			}
		}

		// Side-trip target overrides the normal next-hop marker.
		boolean hasSideTripTarget = (targetSystemName != null && !targetSystemName.isBlank());

		long resolvedCurrentAddress = 0L;
		if (currentRow >= 0) {
			RouteEntry cur = entries.get(currentRow);
			if (cur != null) {
				resolvedCurrentAddress = cur.systemAddress;
			}
		}
		if (resolvedCurrentAddress == 0L) {
			resolvedCurrentAddress = currentSystemAddress;
		}

		boolean hasLocalBodyDestination = false;
		if (destinationBodyId != null && destinationSystemAddress != null && resolvedCurrentAddress != 0L) {
			if (destinationSystemAddress.longValue() == resolvedCurrentAddress) {
				hasLocalBodyDestination = true;
			}
		}

		// While charging, the most reliable "next jump" is Status.json's destination system (if any).
		boolean charging = jumpFlashTimer.isRunning();
		RouteEntry pending = null;

		if (!hasSideTripTarget) {
			if (charging && destinationBodyId == null) {
				String destNameForPending = pendingJumpLockedName;
				long destAddrForPending = pendingJumpLockedAddress;

				if (destNameForPending == null || destNameForPending.isBlank()) {
					destNameForPending = destinationName;
				}
				if (destAddrForPending == 0L && destinationSystemAddress != null) {
					destAddrForPending = destinationSystemAddress.longValue();
				}

				// Only try to use the Status destination if it looks like a real system target.
				if (destAddrForPending != 0L || (destNameForPending != null && !destNameForPending.isBlank())) {
					int destRow = findSystemRow(entries, destNameForPending, destAddrForPending);
					if (destRow >= 0 && destRow != currentRow) {
						RouteEntry e = entries.get(destRow);
						if (e != null && !e.isBodyRow) {
							pending = e;
						}
					}
				}
			}

			// Fallback to the plotted route's next hop.
			// If we have a LOCAL body destination selected, the empty triangle belongs ONLY to that body row.
			if (pending == null && nextHop != null && !hasLocalBodyDestination) {
				pending = nextHop;
			}

			if (pending != null) {
				pending.markerKind = MarkerKind.PENDING_JUMP;
			}
		}

		// Mark side-trip target system row. If we are charging, blink it as the pending jump.
		if (hasSideTripTarget) {
			for (RouteEntry e : entries) {
				if (!matchesTarget(e)) {
					continue;
				}
				if (e.markerKind == MarkerKind.NONE) {
					if (charging) {
						e.markerKind = MarkerKind.PENDING_JUMP;
					} else {
						e.markerKind = MarkerKind.TARGET;
					}
				}
				break;
			}
		}

	}

	private boolean matchesTarget(RouteEntry e) {
		if (e == null) {
			return false;
		}
		if (e.isBodyRow) {
			return false;
		}

		if (targetSystemAddress != 0L && e.systemAddress != 0L) {
			if (e.systemAddress == targetSystemAddress) {
				return true;
			}
		}

		if (targetSystemName != null && !targetSystemName.isBlank() && e.systemName != null) {
			if (targetSystemName.equals(e.systemName)) {
				return true;
			}
		}

		return false;
	}
	
	private final Map<Long, ScanStatus> lastKnownScanStatusByAddress = new ConcurrentHashMap<>();

	private void rememberScanStatus(RouteEntry entry, ScanStatus status) {
		if (entry == null) {
			return;
		}
		if (entry.systemAddress == 0L) {
			return;
		}
		if (status == null || status == ScanStatus.UNKNOWN) {
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
			ScanStatus local = getLocalScanStatus(e);
			if (local != ScanStatus.UNKNOWN) {
				e.status = local;
				rememberScanStatus(e, local);
				continue;
			}

			// If we already knew something, don't reset back to UNKNOWN.
			if (e.status == null || e.status == ScanStatus.UNKNOWN) {
				ScanStatus remembered = lastKnownScanStatusByAddress.get(e.systemAddress);
				if (remembered != null && remembered != ScanStatus.UNKNOWN) {
					e.status = remembered;
				}
			}
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
		ScanStatus local = getLocalScanStatus(entry);
		if (local != ScanStatus.UNKNOWN) {
			entry.status = local;
			rememberScanStatus(entry, local);
			SwingUtilities.invokeLater(() -> tableModel.fireRowChanged(row));
			return;
		}
		boolean v = isVisited(entry);
		try {
			BodiesResponse bodies = edsmClient.showBodies(entry.systemName);
			ScanStatus newStatus = ScanStatus.UNKNOWN;
			if (bodies != null && bodies.bodies != null) {
				int returnedBodies = bodies.bodies.size();
				boolean hasBodies = returnedBodies > 0;
				if (hasBodies) {
					if (bodies.bodyCount != returnedBodies) {
						newStatus = v
								? ScanStatus.BODYCOUNT_MISMATCH_VISITED
										: ScanStatus.BODYCOUNT_MISMATCH_NOT_VISITED;
					} else {
						newStatus = v
								? ScanStatus.FULLY_DISCOVERED_VISITED
										: ScanStatus.FULLY_DISCOVERED_NOT_VISITED;
					}
				} else {
					newStatus = ScanStatus.UNKNOWN;
				}
			}
			if (newStatus != ScanStatus.UNKNOWN) {
				entry.status = newStatus;
				rememberScanStatus(entry, newStatus);
				SwingUtilities.invokeLater(() -> tableModel.fireRowChanged(row));
			} else {
				// Don't downgrade a known status back to UNKNOWN.
				if (entry.status == null || entry.status == ScanStatus.UNKNOWN) {
					entry.status = ScanStatus.UNKNOWN;
					SwingUtilities.invokeLater(() -> tableModel.fireRowChanged(row));
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private ScanStatus getLocalScanStatus(RouteEntry entry) {
		if (entry == null) {
			return ScanStatus.UNKNOWN;
		}
		SystemCache cache = SystemCache.getInstance();
		CachedSystem cs = cache.get(entry.systemAddress, entry.systemName);
		if (cs == null) {
			return ScanStatus.UNKNOWN; // not visited / no local info
		}
		SystemState tmp = new SystemState();
		cache.loadInto(tmp, cs);
		Boolean all = tmp.getAllBodiesFound();
		if (Boolean.TRUE.equals(all)) {
			return ScanStatus.FULLY_DISCOVERED_VISITED;
		}
		Integer totalBodies = tmp.getTotalBodies();
		int knownBodies = tmp.getBodies().size();
		// If we know the system body count and we don't have them all locally -> X
		if (totalBodies != null && totalBodies > 0 && knownBodies > 0 && knownBodies < totalBodies) {
			return ScanStatus.DISCOVERY_MISSING_VISITED;
		}
		// If we know counts match and FSS progress says complete -> checkmark
		Double progress = tmp.getFssProgress();
		if (totalBodies != null && totalBodies > 0 && knownBodies >= totalBodies
				&& progress != null && progress >= 1.0) {
			return ScanStatus.FULLY_DISCOVERED_VISITED;
		}
		return ScanStatus.UNKNOWN;
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
		CachedSystem cs = cache.get(entry.systemAddress, entry.systemName);
		if (cs == null) {
			// Nothing cached locally → definitely not "fully scanned by me"
			return false;
		}
		// Load into a temporary SystemState so we can inspect metadata
		SystemState tmp = new SystemState();
		cache.loadInto(tmp, cs);
		// 1) If we have an explicit "all bodies found" flag, trust that first.
		Boolean all = tmp.getAllBodiesFound();
		if (Boolean.TRUE.equals(all)) {
			return true;
		}
		//        // 2) Otherwise, fall back to counts / progress.
		Integer totalBodies = tmp.getTotalBodies();
		//        if (totalBodies == null || totalBodies == 0) {
		//            // We don't know how many bodies there should be; can't claim "fully scanned".
		//            return false;
		//        }
		int knownBodies = tmp.getBodies().size();
		if (totalBodies != null && knownBodies < totalBodies) {
			// We've seen some bodies but not all → not fully scanned.
			return false;
		}
		// If FSS progress exists, require it to be ~100%.
		Double progress = tmp.getFssProgress();
		if (progress != null && progress == 1.0) {// 0.999) {
			return true;
		}
		//         At this point, cache says we know all bodies and FSS is effectively complete.
		return false;
	}
	private static String safeString(JsonObject obj, String key) {
		JsonElement el = obj.get(key);
		return (el != null && !el.isJsonNull()) ? el.getAsString() : "";
	}
	private static long safeLong(JsonObject obj, String key) {
		JsonElement el = obj.get(key);
		try {
			return (el != null && !el.isJsonNull()) ? el.getAsLong() : 0L;
		} catch (Exception e) {
			return 0L;
		}
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
		CachedSystem cs = cache.get(entry.systemAddress, entry.systemName);
		return cs != null;
	}

	private String getCurrentSystemName() {
		if (currentSystemName != null && !currentSystemName.isBlank()) {
			return currentSystemName;
		}
		// Best source: recent journals (works at startup, no live events required)
		String fromJournal = resolveCurrentSystemNameFromJournal();
		if (fromJournal != null && !fromJournal.isBlank()) {
			currentSystemName = fromJournal;
			return currentSystemName;
		}
		// Fallback: whatever SystemCache persisted last
		try {
			String fromCache = SystemCache.load().systemName;
			if (fromCache != null && !fromCache.isBlank()) {
				currentSystemName = fromCache;
				return currentSystemName;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		// Never return null (renderer comparisons should not explode or behave oddly)
		return "";
	}
	private String resolveCurrentSystemNameFromJournal() {
	    try {
	        EliteJournalReader reader = new EliteJournalReader(EliteDangerousOverlay.clientKey);
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
	private void setCurrentSystemName(String currentSystemName) {
		if (currentSystemName == null) {
			return;
		}
		this.currentSystemName = currentSystemName;
		tableModel.setCurrentSystemName(currentSystemName);
	}
	// ---------------------------------------------------------------------
	// Model + table
	// ---------------------------------------------------------------------
	enum ScanStatus {
		// Any body missing discovery.commander and you have visited the system.
		DISCOVERY_MISSING_VISITED,
		// Any body missing discovery.commander and you have NOT visited the system.
		DISCOVERY_MISSING_NOT_VISITED,
		// EDSM bodyCount does not match the number of bodies returned, and you have visited.
		BODYCOUNT_MISMATCH_VISITED,
		// EDSM bodyCount does not match the number of bodies returned, and you have NOT visited.
		BODYCOUNT_MISMATCH_NOT_VISITED,
		// All bodies accounted for in EDSM and each has discovery.commander, and you have visited.
		FULLY_DISCOVERED_VISITED,
		// All bodies accounted for in EDSM and each has discovery.commander, and you have NOT visited.
		FULLY_DISCOVERED_NOT_VISITED,
		// Anything else / no data.
		UNKNOWN
	}
	private enum MarkerKind {
		NONE,
		CURRENT,
		TARGET,
		PENDING_JUMP
	}
	static final class RouteEntry {
		public RouteEntry() {
		}
		public RouteEntry(int i, String systemNameIn, long systemAddressIn, String starClassIn, double dLy, ScanStatus scanStatusIn) {
			index = i;
			systemName = systemNameIn;
			systemAddress = systemAddressIn;
			starClass = starClassIn;
			distanceLy = dLy;
			status = scanStatusIn;
		}
		int index;
		Integer displayIndex;
		String systemName;
		long systemAddress;
		String starClass;
		boolean isSynthetic;
		boolean isBodyRow;
		int indentLevel;
		MarkerKind markerKind = MarkerKind.NONE;
		/**
		 * StarPos coordinates (x,y,z) for this system, in Ly, when available (NavRoute.json provides these).
		 * Used for "straight line" distance calculations.
		 */
		Double x;
		Double y;
		Double z;
		/**
		 * Per-leg distance (Ly) from the previous entry to this entry.
		 * Null for the origin row.
		 */
		Double distanceLy;
		ScanStatus status;
		RouteEntry copy() {
			RouteEntry e = new RouteEntry();
			e.index = index;
			e.displayIndex = displayIndex;
			e.systemName = systemName;
			e.systemAddress = systemAddress;
			e.starClass = starClass;
			e.x = x;
			e.y = y;
			e.z = z;
			e.distanceLy = distanceLy;
			e.status = status;
			e.isSynthetic = isSynthetic;
			e.isBodyRow = isBodyRow;
			e.indentLevel = indentLevel;
			e.markerKind = markerKind;
			return e;
		}
		static RouteEntry syntheticSystem(String name, long address, Double[] coords, MarkerKind markerKind) {
			RouteEntry e = new RouteEntry();
			e.isSynthetic = true;
			e.isBodyRow = false;
			e.systemName = (name != null ? name : "");
			e.systemAddress = address;
			e.starClass = "";
			e.status = ScanStatus.UNKNOWN;
			e.markerKind = (markerKind != null ? markerKind : MarkerKind.NONE);
			if (coords != null && coords.length == 3 && coords[0] != null && coords[1] != null && coords[2] != null) {
				e.x = coords[0];
				e.y = coords[1];
				e.z = coords[2];
			}
			return e;
		}
		static RouteEntry syntheticBody(String bodyName) {
			RouteEntry e = new RouteEntry();
			e.isSynthetic = true;
			e.isBodyRow = true;
			e.systemName = (bodyName != null ? bodyName : "");
			e.systemAddress = 0L;
			e.starClass = "";
			e.status = null;
			e.markerKind = MarkerKind.NONE;
			return e;
		}
	}
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
			case COL_SYSTEM:
				return e.systemName != null ? e.systemName : "";
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

			if (value instanceof ScanStatus) {
				ScanStatus status = (ScanStatus) value;
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
			MarkerKind kind = (entry != null ? entry.markerKind : MarkerKind.NONE);

			if (kind == MarkerKind.CURRENT) {
				icon = new TriangleIcon(EdoUi.User.MAIN_TEXT, 10, 10);

			} else if (kind == MarkerKind.PENDING_JUMP) {
				// Blink the "next jump" empty triangle.
				if (jumpFlashOn)
				{
					icon = new OutlineTriangleIcon(EdoUi.ED_ORANGE_LESS_TRANS, 10, 10, 2f);
				}

			} else if (kind == MarkerKind.TARGET) {
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
