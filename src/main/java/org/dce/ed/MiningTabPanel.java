package org.dce.ed;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.LinearGradientPaint;
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
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JLabel;
import javax.swing.JLayer;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.Window;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.LayerUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import org.dce.ed.edsm.UtilTable;
import org.dce.ed.logreader.EliteLogFileLocator;
import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent.MaterialProportion;
import org.dce.ed.logreader.event.StartJumpEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.logreader.event.SupercruiseExitEvent;
import org.dce.ed.market.GalacticAveragePrices;
import org.dce.ed.market.MaterialNameMatcher;
import org.dce.ed.OverlayFrame;
import org.dce.ed.mining.GoogleSheetsBackend;
import org.dce.ed.mining.GoogleSheetsReconnectDialog;
import org.dce.ed.mining.ProspectorLoadResult;
import org.dce.ed.mining.MiningRunNumberResolver;
import org.dce.ed.mining.ProspectorLogBackend;
import org.dce.ed.mining.ProspectorLogBackendFactory;
import org.dce.ed.mining.ProspectorLogRegression;
import org.dce.ed.mining.MiningLeaderSnapshot;
import org.dce.ed.mining.MiningScatterAsteroidModel;
import org.dce.ed.mining.ProspectorLogRow;
import org.dce.ed.mining.ProspectorWriteResult;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.state.SystemState;
import org.dce.ed.tts.PollyTtsCached;
import org.dce.ed.tts.TtsSprintf;
import org.dce.ed.ui.EdoMiningSplitPaneUi;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.SubtleScrollBarUI;
import org.dce.ed.ui.SystemTableHoverCopyManager;
import org.dce.ed.ui.TransparentTableHeaderUI;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Overlay tab: Mining
 *
 * Shows the most recent ProspectedAsteroid materials sorted by galactic average value.
 */
public class MiningTabPanel extends JPanel {

	private final TtsSprintf tts;
	private final Supplier<ProspectorLogBackend> prospectorBackendSupplier;
	private String lastProspectorAnnouncementSig;
	private volatile boolean lastProspectorUpdateWasOnEdt;
	private Set<String> prospectorHighlightNames = new HashSet<>();

	private final GalacticAveragePrices prices;
	private final MaterialNameMatcher matcher;
	/** When non-null, we only write prospector CSV rows when this returns false (undocked). */
	private final BooleanSupplier isDockedSupplier;

	private final JLabel headerLabel;
	private final JLabel inventoryLabel;
	private final JTable table;
	private final MiningTableModel model;

	private final JTable cargoTable;
	private final MiningTableModel cargoModel;

	private final JScrollPane materialsScroller;
	private final JScrollPane cargoScroller;

	private final JLabel spreadsheetLabel;
	/** Explains whether the prospector table reads local CSV or Google Sheets (from preferences). */
	private final JLabel prospectorLogSourceLabel;
	private final JTable spreadsheetTable;
	private final ProspectorLogTableModel spreadsheetModel;
	private java.util.List<ProspectorLogRow> lastGoodSpreadsheetRows = java.util.Collections.emptyList();
	private final JScrollPane spreadsheetScroller;
	private final ProspectorLogScatterPanel spreadsheetScatterPanel;
	private final ProspectorLogScatterWrapperPanel spreadsheetScatterWrapper;
	private final JPanel spreadsheetCardPanel;
	private final JToggleButton prospectorLogTableViewBtn;
	private final JToggleButton prospectorLogScatterViewBtn;
	/** Poll interval for Google Sheets prospector log (spreadsheet metadata + one batch values read per tick). */
	private static final int SPREADSHEET_REFRESH_MS = 30_000;
	private final Timer spreadsheetRefreshTimer;
	/** Coalesce overlapping refresh requests so timer + journal handlers cannot stack parallel Sheet loads. */
	private final Object spreadsheetRefreshLock = new Object();
	private boolean spreadsheetRefreshWorkerRunning;
	private boolean spreadsheetRefreshPending;
	/** Throttle modal "reconnect to Google" prompts on repeated refresh failures. */
	private long lastGoogleSheetsReconnectDialogMs;
	private final JSplitPane miningOuterSplit;
	private final JSplitPane miningInnerSplit;
	private SystemTableHoverCopyManager miningSystemCopyManager;

	private final Map<String, Long> lastCargoTonsByName = new HashMap<>();

	/** Inventory tons by commodity (display name) at the time of the previous ProspectedAsteroid event. */
	private Map<String, Double> lastInventoryTonsAtProspector = new HashMap<>();

	/** Last cargo snapshot used for mining-log comparison (per material display name). */
	private Map<String, Double> lastCargoTonsForLogging = new HashMap<>();

	/** Baseline cargo tons at the start of the current asteroid for each material. */
	private Map<String, Double> asteroidBaselineTons = new HashMap<>();

	/** Last seen proportion (percent) per material from the previous ProspectedAsteroid event; used for CSV so we log the rock's % when the mining actually happened. */
	private Map<String, Double> lastPercentByMaterialAtProspector = new HashMap<>();

	/** True if we wrote at least one prospector log row since the last dock; used to decide whether there was activity in this trip (for UI/asteroid reset purposes). */
	private boolean wroteRowsThisRun;

	/** Next asteroid ID index (0 = A, 1 = B, ..., 26 = AA, ...). Reset when run increments. */
	private int asteroidIdCounter;
	/** Number of prospector limpets fired (duds) since the last one that generated logged inventory. */
	private int dudCounter;
	/** True after we've synced the run counter from the spreadsheet once this session (so next write uses the right run number). */
	private boolean syncedRunCounterFromBackend;

	/**
	 * When non-null, spreadsheet System column and run resolution use {@link SystemState#getSystemName()} first
	 * (authoritative). Journal-derived {@link #currentSystemName} / {@link #lastNonEmptySystemName} are fallback only.
	 */
	private final Supplier<SystemState> systemStateSupplier;

	/** Current system and body for prospector log rows (journal / Status; system is superseded by SystemState when wired). */
	private volatile String currentSystemName = "";
	private volatile String currentBodyName = "";
	/** Last non-empty system/body we have seen; used to avoid regressions to blank. */
	private volatile String lastNonEmptySystemName = "";
	private volatile String lastNonEmptyBodyName = "";

	private static final EliteLogParser MINING_STATUS_PARSER = new EliteLogParser();
	private final Object statusBodyReadLock = new Object();
	private long statusBodyCacheMtime = -1L;
	private String statusBodyCacheValue = "";

	/** True once we've seen a prospector this trip; enables cargo-driven logging. */
	private boolean miningLoggingArmed;

	/** True if the current asteroid already has a row; controls update vs new row behavior. */
	private boolean haveActiveAsteroid;

	/**
	 * After the first ProspectedAsteroid of a trip, each new limpet advances the asteroid letter only if we logged
	 * cargo from the previous rock; dud limpets (no material gathered) do not consume the next letter.
	 */
	private boolean prospectorLimpetSeenThisTrip;

	/** True after a successful cargo-driven log write for the current rock; cleared on each new prospector event. */
	private boolean loggedCargoSinceLastProspector;

	/** Run number currently in use for cargo-driven logging in this system/body; 0 means \"not yet chosen\". */
	private int activeRun;

	/** True after we left the ring (FSD or location change); next mining write should start a new run. */
	private boolean nextMiningStartsNewRun;

	/** Time of last undock; used as run start time for the first row of each run. Cleared when docked. */
	private Instant lastUndockTime;

	/** Called when lastUndockTime changes so session state can be saved. */
	private Runnable sessionStateChangeCallback;

	private final TableScanState prospectorScan;
private final TableScanState cargoScan;
private final JLayer<JTable> prospectorLayer;
private final JLayer<JTable> cargoLayer;


	private Font uiFont;
	private JLabel prospectorLabel;

	private static final int VISIBLE_ROWS = 10;

	/**
	 * When set (tests), mining sheet status is sent here instead of {@link OverlayFrame#overlayFrame}.
	 */
	private static volatile Consumer<String> miningSheetsStatusErrorSinkForTests;
	private static volatile Runnable miningSheetsStatusClearSinkForTests;

	// Row colors for mining tables.
	private static final Color CORE_COLOR = EdoUi.User.CORE_BLUE;
	private static final Color NON_CORE_GREEN = EdoUi.User.SUCCESS;

	/** For tests: capture mining/Sheets status bar updates without a real {@link OverlayFrame}. */
	public static void setMiningSheetsStatusSinksForTests(Consumer<String> onError, Runnable onClear) {
		miningSheetsStatusErrorSinkForTests = onError;
		miningSheetsStatusClearSinkForTests = onClear;
	}

	public static void clearMiningSheetsStatusSinksForTests() {
		miningSheetsStatusErrorSinkForTests = null;
		miningSheetsStatusClearSinkForTests = null;
	}

	private static void applyMiningSheetsStatusError(String message) {
		Consumer<String> sink = miningSheetsStatusErrorSinkForTests;
		if (sink != null) {
			sink.accept(message != null ? message : "");
			return;
		}
		OverlayFrame frame = OverlayFrame.overlayFrame;
		if (frame != null) {
			frame.setMiningSheetsStatusError(message);
		}
	}

	private static void applyMiningSheetsStatusClear() {
		Runnable clear = miningSheetsStatusClearSinkForTests;
		if (clear != null) {
			clear.run();
			return;
		}
		OverlayFrame frame = OverlayFrame.overlayFrame;
		if (frame != null) {
			frame.clearMiningSheetsStatusError();
		}
	}

	/**
	 * When Google Sheets fails for auth/setup reasons, offer a reconnect dialog (throttled) so the user is not
	 * stuck with only the status-bar line.
	 */
	private void maybeOfferGoogleSheetsReconnectDialog(String detailMessage) {
		if (miningSheetsStatusErrorSinkForTests != null) {
			return;
		}
		if (!"google".equals(OverlayPreferences.getMiningLogBackend())) {
			return;
		}
		if (OverlayPreferences.getMiningGoogleSheetsUrl().isBlank()) {
			return;
		}
		if (!GoogleSheetsReconnectDialog.isLikelyAuthOrSetupFailure(detailMessage)) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastGoogleSheetsReconnectDialogMs < 90_000L) {
			return;
		}
		lastGoogleSheetsReconnectDialogMs = now;
		Window w = SwingUtilities.getWindowAncestor(this);
		if (w == null) {
			return;
		}
		// Defer so the error status line updates before the modal.
		SwingUtilities.invokeLater(() -> GoogleSheetsReconnectDialog.show(w, EliteDangerousOverlay.clientKey, detailMessage));
	}

	public MiningTabPanel(GalacticAveragePrices prices) {
		this(prices, null);
	}

	public MiningTabPanel(GalacticAveragePrices prices, BooleanSupplier isDockedSupplier) {
		this(prices, isDockedSupplier, new TtsSprintf(new PollyTtsCached()), ProspectorLogBackendFactory::create, null);
	}

	public MiningTabPanel(GalacticAveragePrices prices,
	                        BooleanSupplier isDockedSupplier,
	                        TtsSprintf tts,
	                        Supplier<ProspectorLogBackend> backendSupplier) {
		this(prices, isDockedSupplier, tts, backendSupplier, null);
	}

	public MiningTabPanel(GalacticAveragePrices prices,
	                        BooleanSupplier isDockedSupplier,
	                        TtsSprintf tts,
	                        Supplier<ProspectorLogBackend> backendSupplier,
	                        Supplier<SystemState> systemStateSupplier) {
		super(new BorderLayout());
		this.prices = prices;
		this.isDockedSupplier = isDockedSupplier;
		this.tts = Objects.requireNonNull(tts, "tts");
		this.prospectorBackendSupplier = Objects.requireNonNull(backendSupplier, "backendSupplier");
		this.systemStateSupplier = systemStateSupplier;

		this.matcher = new MaterialNameMatcher(prices);
// Always render transparent so passthrough mode looks right.
		setOpaque(false);
		setBackground(EdoUi.Internal.TRANSPARENT);

		headerLabel = new JLabel("Mining (latest prospector)");
		headerLabel.setForeground(EdoUi.User.MAIN_TEXT);
		headerLabel.setHorizontalAlignment(SwingConstants.LEFT);
		headerLabel.setOpaque(false);

		prospectorLabel = new JLabel("Prospector Limpet");
		prospectorLabel.setForeground(EdoUi.User.MAIN_TEXT);
		prospectorLabel.setFont(prospectorLabel.getFont().deriveFont(Font.BOLD));
		prospectorLabel.setHorizontalAlignment(SwingConstants.LEFT);
		prospectorLabel.setOpaque(false);
		prospectorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		Font base = OverlayPreferences.getUiFont();
		prospectorLabel.setFont(base.deriveFont(Font.BOLD, OverlayPreferences.getUiFontSize() + 4));

		// Let it span the width so BoxLayout doesn't center it
		Dimension pref = prospectorLabel.getPreferredSize();
		prospectorLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));

		
		inventoryLabel = new JLabel("Ship Inventory");
		inventoryLabel.setForeground(EdoUi.User.MAIN_TEXT);
		inventoryLabel.setFont(inventoryLabel.getFont().deriveFont(Font.BOLD));
		inventoryLabel.setHorizontalAlignment(SwingConstants.LEFT);
		inventoryLabel.setOpaque(false);
		inventoryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		inventoryLabel.setFont(base.deriveFont(Font.BOLD, OverlayPreferences.getUiFontSize() + 4));

		model = new MiningTableModel("Est. Tons");

		table = new JTable(model) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}

			@Override
			public boolean editCellAt(int row, int column, java.util.EventObject e) {
				return false;
			}

			@Override
			protected void configureEnclosingScrollPane() {
				super.configureEnclosingScrollPane();

				Container p = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
				if (p instanceof JScrollPane) {
					JScrollPane sp = (JScrollPane)p;
					sp.setBorder(BorderFactory.createEmptyBorder());
					sp.setViewportBorder(BorderFactory.createEmptyBorder());

					sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
					sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

					sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
					sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

					// Some Look-and-Feels still paint a faint outline when border is null; force an empty border.
					sp.setBorder(BorderFactory.createEmptyBorder());
					sp.setViewportBorder(BorderFactory.createEmptyBorder());

					JViewport hv = sp.getColumnHeader();
					if (hv != null) {
						hv.setOpaque(false);
						hv.setBackground(EdoUi.Internal.TRANSPARENT);
						hv.setBorder(null);
					}
				}
			}
		};

		// Hard-disable editing and selection (passthrough-friendly visuals).
		table.setDefaultEditor(Object.class, null);
		table.setDefaultEditor(String.class, null);
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
		table.setShowHorizontalLines(false);
		table.setShowVerticalLines(false);
		table.setIntercellSpacing(new java.awt.Dimension(0, 0));
		table.setGridColor(EdoUi.Internal.TRANSPARENT);

		table.setForeground(EdoUi.User.MAIN_TEXT);
		table.setBackground(EdoUi.Internal.TRANSPARENT);
		table.setRowHeight(22);

		table.setTableHeader(new org.dce.ed.ui.TransparentTableHeader(table.getColumnModel()));

		JTableHeader th = table.getTableHeader();
		if (th != null) {
			th.setUI(TransparentTableHeaderUI.createUI(th));
			th.setOpaque(!OverlayPreferences.overlayChromeRequestsTransparency());
			th.setForeground(EdoUi.User.MAIN_TEXT);
			th.setBackground(EdoUi.User.BACKGROUND);
			th.setBorder(null);
			th.setReorderingAllowed(false);
			th.setFocusable(false);
			th.putClientProperty("JTableHeader.focusCellBackground", null);
			th.putClientProperty("JTableHeader.cellBorder", null);
			th.setDefaultRenderer(new HeaderRenderer());

			th.setPreferredSize(new Dimension(pref.width, table.getRowHeight()));
			
			// Give the Material column more room (prevents truncation like "Grandidierite...").
			table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
			applyMiningColumnWidths(table);
			
		}

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

				if (c instanceof JLabel) {
					JLabel l = (JLabel)c;
					l.setFont(tbl.getFont());
					Color base = resolveRowForeground(tbl, row);
					float reveal = getRevealAlpha(tbl, row);
					float flare = getFlareAlpha(tbl, row);
					l.setForeground(applyRevealAndFlare(base, reveal, flare));
					if (isSummaryRow(tbl, row)) {
						l.setFont(l.getFont().deriveFont(Font.BOLD));
						c.setForeground(Color.green.darker());
					}
					l.setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.RIGHT);
					l.setBorder(new EmptyBorder(3, 4, 3, 4));
					l.setOpaque(false);
					l.setBackground(EdoUi.Internal.TRANSPARENT);
				}
				if (c instanceof JComponent) {
					((JComponent) c).setOpaque(false);
				}
				c.setBackground(EdoUi.Internal.TRANSPARENT);
				return c;
			}

			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D)g.create();
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
						RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);

				super.paintComponent(g2);

				g2.setColor(EdoUi.Internal.tableHeaderTopBorder());
				g2.drawLine(0, 0, getWidth(), 0);

				g2.dispose();
			}
		};
		table.setDefaultRenderer(Object.class, defaultRenderer);

		prospectorScan = new TableScanState(table);
		prospectorLayer = new JLayer<>(table, new ScanLayerUi(prospectorScan));

		materialsScroller = new JScrollPane(prospectorLayer);
		materialsScroller.setOpaque(false);
		materialsScroller.setBackground(EdoUi.Internal.TRANSPARENT);
		materialsScroller.getViewport().setOpaque(false);
		materialsScroller.getViewport().setBackground(EdoUi.Internal.TRANSPARENT);
		materialsScroller.setBorder(null);
		materialsScroller.setViewportBorder(null);

		JViewport headerViewport = materialsScroller.getColumnHeader();
		if (headerViewport != null) {
			headerViewport.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
			headerViewport.setOpaque(false);
			headerViewport.setBackground(EdoUi.Internal.TRANSPARENT);
			headerViewport.setBorder(null);
			headerViewport.setUI(org.dce.ed.ui.TransparentViewportUI.createUI(headerViewport));
		}

		configureOverlayScroller(materialsScroller);
		materialsScroller.setAlignmentX(Component.LEFT_ALIGNMENT);

		// ----- Cargo table -----
		cargoModel = new MiningTableModel("Tons");
		cargoTable = new JTable(cargoModel);

		cargoTable.setAutoCreateRowSorter(true);
		cargoTable.putClientProperty("JTable.autoStartsEdit", Boolean.FALSE);

		cargoTable.setOpaque(false);
		cargoTable.setBorder(null);
		cargoTable.setFillsViewportHeight(true);

		cargoTable.setShowGrid(false);
		cargoTable.setShowHorizontalLines(false);
		cargoTable.setShowVerticalLines(false);
		cargoTable.setIntercellSpacing(new java.awt.Dimension(0, 0));
		cargoTable.setGridColor(EdoUi.Internal.TRANSPARENT);

		cargoTable.setForeground(EdoUi.User.MAIN_TEXT);
		cargoTable.setBackground(EdoUi.Internal.TRANSPARENT);
		cargoTable.setRowHeight(22);

		cargoTable.setTableHeader(new org.dce.ed.ui.TransparentTableHeader(cargoTable.getColumnModel()));

		JTableHeader cargoHeader = cargoTable.getTableHeader();
		if (cargoHeader != null) {
			cargoHeader.setUI(org.dce.ed.ui.TransparentTableHeaderUI.createUI(cargoHeader));
			cargoHeader.setOpaque(false);
			cargoHeader.setForeground(EdoUi.User.MAIN_TEXT);
			cargoHeader.setBackground(EdoUi.Internal.TRANSPARENT);
			cargoHeader.setBorder(null);
			cargoHeader.setReorderingAllowed(false);
			cargoHeader.setFocusable(false);
			cargoHeader.putClientProperty("JTableHeader.focusCellBackground", null);
			cargoHeader.putClientProperty("JTableHeader.cellBorder", null);
			cargoHeader.setDefaultRenderer(new HeaderRenderer());

			cargoHeader.setPreferredSize(new Dimension(pref.width, cargoTable.getRowHeight()));
			
			cargoTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
			applyMiningColumnWidths(cargoTable);
		}

		for (int c = 0; c < cargoTable.getColumnModel().getColumnCount(); c++) {
			cargoTable.getColumnModel().getColumn(c).setCellRenderer(defaultRenderer);
		}

		cargoScan = new TableScanState(cargoTable);
		cargoLayer = new JLayer<>(cargoTable, new ScanLayerUi(cargoScan));

		cargoScroller = new JScrollPane(cargoLayer);
		cargoScroller.setOpaque(false);
		cargoScroller.setBackground(EdoUi.Internal.TRANSPARENT);
		cargoScroller.getViewport().setOpaque(false);
		cargoScroller.getViewport().setBackground(EdoUi.Internal.TRANSPARENT);
		cargoScroller.setBorder(null);
		cargoScroller.setViewportBorder(null);

		JViewport cargoHeaderViewport = cargoScroller.getColumnHeader();
		if (cargoHeaderViewport != null) {
			cargoHeaderViewport.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
			cargoHeaderViewport.setOpaque(false);
			cargoHeaderViewport.setBackground(EdoUi.Internal.TRANSPARENT);
			cargoHeaderViewport.setUI(org.dce.ed.ui.TransparentViewportUI.createUI(cargoHeaderViewport));
			cargoHeaderViewport.setBorder(null);
		}

		configureOverlayScroller(cargoScroller);
		cargoScroller.setAlignmentX(Component.LEFT_ALIGNMENT);
		inventoryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		Dimension invPref = inventoryLabel.getPreferredSize();
		inventoryLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, invPref.height));

		// Spreadsheet (prospector log) panel
		spreadsheetModel = new ProspectorLogTableModel();
		spreadsheetTable = new JTable(spreadsheetModel) {
			@Override
			public boolean isCellEditable(int row, int column) { return false; }
			@Override
			public boolean editCellAt(int row, int column, java.util.EventObject e) { return false; }

			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				try {
					g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					super.paintComponent(g2);

					// Draw run summary rows as a single row-spanning label after normal painting.
					if (spreadsheetModel != null) {
						int rows = getRowCount();
						for (int viewRow = 0; viewRow < rows; viewRow++) {
							if (!spreadsheetModel.isSummaryRow(viewRow)) continue;
							RunSummary rs = spreadsheetModel.getSummaryAt(viewRow);
							if (rs == null) continue;
							String text = rs.formatSummary();

							Rectangle cellRect = getCellRect(viewRow, 0, true);
							Rectangle rowRect = getCellRect(viewRow, 0, true);
							rowRect.width = getWidth();

							Rectangle clip = g2.getClipBounds();
							if (clip != null && !clip.intersects(rowRect)) {
								continue;
							}

							Graphics2D rg = (Graphics2D) g2.create();
							try {
								rg.setClip(rowRect);
								rg.setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() + 2f));
								rg.setColor(EdoUi.User.MAIN_TEXT);
								FontMetrics fm = rg.getFontMetrics();
								int x = cellRect.x + 4;
								int y = rowRect.y + (rowRect.height + fm.getAscent()) / 2 - 2;
								rg.drawString(text, x, y);
							} finally {
								rg.dispose();
							}
						}
					}
				} finally {
					g2.dispose();
				}
			}
		};
		spreadsheetTable.setDefaultEditor(Object.class, null);
		spreadsheetTable.setFocusable(false);
		spreadsheetTable.setRowSelectionAllowed(false);
		spreadsheetTable.setOpaque(false);
		spreadsheetTable.setBackground(EdoUi.Internal.TRANSPARENT);
		spreadsheetTable.setForeground(EdoUi.User.MAIN_TEXT);
		spreadsheetTable.setShowGrid(false);
		spreadsheetTable.setShowHorizontalLines(false);
		spreadsheetTable.setShowVerticalLines(false);
		spreadsheetTable.setIntercellSpacing(new java.awt.Dimension(0, 0));
		spreadsheetTable.setGridColor(EdoUi.Internal.TRANSPARENT);
		DefaultTableCellRenderer spreadCellRenderer = new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				if (c instanceof JComponent jc) {
					jc.setOpaque(false);
				}
				if (c instanceof JLabel lbl) {
					// Right-justify numeric prospector columns: Percentage (5), Tons (8), Duds (10)
					if (column == 5 || column == 8 || column == 10) {
						lbl.setHorizontalAlignment(SwingConstants.RIGHT);
					} else {
						lbl.setHorizontalAlignment(SwingConstants.LEFT);
					}
					// Space after Commander (column 0) so it does not touch Run.
					if (column == 0) {
						lbl.setBorder(new EmptyBorder(0, 0, 0, 6));
					}
				}
				c.setBackground(EdoUi.Internal.TRANSPARENT);
				if (spreadsheetModel != null && spreadsheetModel.isSummaryRow(row)) {
					c.setFont(c.getFont().deriveFont(Font.BOLD).deriveFont(c.getFont().getSize2D() + 2f));
				}
				return c;
			}
		};
		spreadsheetTable.setDefaultRenderer(Object.class, spreadCellRenderer);
		spreadsheetTable.setTableHeader(new ProspectorLogTableHeader(spreadsheetTable.getColumnModel()));
		applyProspectorLogColumnVisibility(spreadsheetTable);
		// Let UtilTable auto-size columns based on content and headers.
		UtilTable.autoSizeTableColumns(spreadsheetTable);
		JTableHeader spreadHeader = spreadsheetTable.getTableHeader();
		if (spreadHeader != null) {
			spreadHeader.setUI(org.dce.ed.ui.TransparentTableHeaderUI.createUI(spreadHeader));
			spreadHeader.setOpaque(false);
			spreadHeader.setBackground(EdoUi.Internal.TRANSPARENT);
			spreadHeader.setForeground(EdoUi.User.MAIN_TEXT);
			spreadHeader.putClientProperty("JTableHeader.focusCellBackground", null);
			spreadHeader.putClientProperty("JTableHeader.cellBorder", null);
			spreadHeader.setDefaultRenderer(new HeaderRenderer());
		}
		spreadsheetScroller = new JScrollPane(spreadsheetTable);
		spreadsheetScroller.setOpaque(false);
		spreadsheetScroller.setBackground(EdoUi.Internal.TRANSPARENT);
		spreadsheetScroller.getViewport().setOpaque(false);
		spreadsheetScroller.getViewport().setBackground(EdoUi.Internal.TRANSPARENT);
		spreadsheetScroller.setBorder(null);
		spreadsheetScroller.setViewportBorder(null);
		JViewport spreadHeaderViewport = spreadsheetScroller.getColumnHeader();
		if (spreadHeaderViewport != null) {
			spreadHeaderViewport.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
			spreadHeaderViewport.setOpaque(false);
			spreadHeaderViewport.setBackground(EdoUi.Internal.TRANSPARENT);
			spreadHeaderViewport.setUI(org.dce.ed.ui.TransparentViewportUI.createUI(spreadHeaderViewport));
			spreadHeaderViewport.setBorder(null);
		}
		configureOverlayScroller(spreadsheetScroller);
		installSubtleScrollBarsOnProspectorLog(spreadsheetScroller);
		// Install system-name copy behavior on the System column (model index 11).
		miningSystemCopyManager = new SystemTableHoverCopyManager(spreadsheetTable, 11, isDockedSupplier);
		miningSystemCopyManager.start();
		spreadsheetTable.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				if (e.getClickCount() != 2) {
					return;
				}
				int viewRow = spreadsheetTable.rowAtPoint(e.getPoint());
				int viewCol = spreadsheetTable.columnAtPoint(e.getPoint());
				if (viewRow < 0 || viewCol < 0) {
					return;
				}
				int modelCol = spreadsheetTable.convertColumnIndexToModel(viewCol);
				if (modelCol != 11) {
					return;
				}
				miningSystemCopyManager.copySystemNameAtViewRow(viewRow);
			}
		});
		spreadsheetScroller.setAlignmentX(Component.LEFT_ALIGNMENT);
		spreadsheetScatterPanel = new ProspectorLogScatterPanel();
		spreadsheetScatterPanel.setOpaque(false);
		spreadsheetScatterPanel.setBackground(EdoUi.Internal.TRANSPARENT);
		spreadsheetScatterPanel.setForeground(EdoUi.User.MAIN_TEXT);
		spreadsheetScatterWrapper = new ProspectorLogScatterWrapperPanel(spreadsheetScatterPanel);
		spreadsheetScatterWrapper.setOpaque(false);
		spreadsheetScatterWrapper.setBackground(EdoUi.Internal.TRANSPARENT);
		spreadsheetCardPanel = new JPanel(new CardLayout());
		spreadsheetCardPanel.setOpaque(false);
		spreadsheetCardPanel.setBackground(EdoUi.Internal.TRANSPARENT);
		spreadsheetCardPanel.add(spreadsheetScroller, "table");
		spreadsheetCardPanel.add(spreadsheetScatterWrapper, "scatter");
		spreadsheetCardPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		boolean initialScatter = OverlayPreferences.isMiningProspectorLogScatterView();
		prospectorLogTableViewBtn = new JToggleButton("Table", !initialScatter);
		prospectorLogScatterViewBtn = new JToggleButton("Scatter", initialScatter);
		prospectorLogTableViewBtn.setOpaque(false);
		prospectorLogScatterViewBtn.setOpaque(false);
		ButtonGroup spreadsheetViewGroup = new ButtonGroup();
		spreadsheetViewGroup.add(prospectorLogTableViewBtn);
		spreadsheetViewGroup.add(prospectorLogScatterViewBtn);
		((CardLayout) spreadsheetCardPanel.getLayout()).show(spreadsheetCardPanel, initialScatter ? "scatter" : "table");
		prospectorLogTableViewBtn.addActionListener(e -> {
			((CardLayout) spreadsheetCardPanel.getLayout()).show(spreadsheetCardPanel, "table");
			OverlayPreferences.setMiningProspectorLogScatterView(false);
		});
		prospectorLogScatterViewBtn.addActionListener(e -> {
			((CardLayout) spreadsheetCardPanel.getLayout()).show(spreadsheetCardPanel, "scatter");
			OverlayPreferences.setMiningProspectorLogScatterView(true);
		});
		// Hover-to-switch between Table and Scatter views (works in pass-through mode via global mouse polling).
		SpreadsheetViewHoverPoller.register(prospectorLogTableViewBtn, 500, () -> prospectorLogTableViewBtn.doClick());
		SpreadsheetViewHoverPoller.register(prospectorLogScatterViewBtn, 500, () -> prospectorLogScatterViewBtn.doClick());
		JPanel spreadsheetToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		spreadsheetToolbar.setOpaque(false);
		spreadsheetToolbar.add(prospectorLogTableViewBtn);
		spreadsheetToolbar.add(prospectorLogScatterViewBtn);
		spreadsheetToolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
		spreadsheetLabel = new JLabel("Prospector log");
		spreadsheetLabel.setForeground(EdoUi.User.MAIN_TEXT);
		spreadsheetLabel.setOpaque(false);
		spreadsheetLabel.setBackground(EdoUi.Internal.TRANSPARENT);
		spreadsheetLabel.setFont(base.deriveFont(Font.BOLD, OverlayPreferences.getUiFontSize() + 4));
		spreadsheetLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		Dimension spreadPref = spreadsheetLabel.getPreferredSize();
		spreadsheetLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, spreadPref.height));

		prospectorLogSourceLabel = new JLabel();
		prospectorLogSourceLabel.setOpaque(false);
		prospectorLogSourceLabel.setBackground(EdoUi.Internal.TRANSPARENT);
		prospectorLogSourceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		prospectorLogSourceLabel.setFont(base.deriveFont(Font.PLAIN, Math.max(10, OverlayPreferences.getUiFontSize() - 1)));
		updateProspectorLogSourceLabelText();

		// Leave about 10 rows for each table (preferred sizes; split panes allocate extra space).
		updateScrollerHeights();

		double outerRatio = OverlayPreferences.getMiningPanelSplitOuterRatio();
		double innerRatio = OverlayPreferences.getMiningPanelSplitInnerRatio();

		JPanel inventoryPanel = new JPanel(new BorderLayout());
		inventoryPanel.setOpaque(false);
		inventoryPanel.setBackground(EdoUi.Internal.TRANSPARENT);
		JPanel invHeader = new JPanel();
		invHeader.setOpaque(false);
		invHeader.setLayout(new BoxLayout(invHeader, BoxLayout.Y_AXIS));
		invHeader.add(inventoryLabel);
		invHeader.add(Box.createVerticalStrut(2));
		inventoryPanel.add(invHeader, BorderLayout.NORTH);
		inventoryPanel.add(cargoScroller, BorderLayout.CENTER);

		JPanel prospectorPanel = new JPanel(new BorderLayout());
		prospectorPanel.setOpaque(false);
		prospectorPanel.setBackground(EdoUi.Internal.TRANSPARENT);
		JPanel proHeader = new JPanel();
		proHeader.setOpaque(false);
		proHeader.setLayout(new BoxLayout(proHeader, BoxLayout.Y_AXIS));
		proHeader.add(prospectorLabel);
		proHeader.add(Box.createVerticalStrut(4));
		prospectorPanel.add(proHeader, BorderLayout.NORTH);
		prospectorPanel.add(materialsScroller, BorderLayout.CENTER);

		JPanel spreadsheetPanel = new JPanel(new BorderLayout());
		spreadsheetPanel.setOpaque(false);
		spreadsheetPanel.setBackground(EdoUi.Internal.TRANSPARENT);
		JPanel logSpreadsheetNorth = new JPanel();
		logSpreadsheetNorth.setOpaque(false);
		logSpreadsheetNorth.setLayout(new BoxLayout(logSpreadsheetNorth, BoxLayout.Y_AXIS));
		logSpreadsheetNorth.add(spreadsheetLabel);
		logSpreadsheetNorth.add(Box.createVerticalStrut(2));
		logSpreadsheetNorth.add(prospectorLogSourceLabel);
		logSpreadsheetNorth.add(Box.createVerticalStrut(2));
		logSpreadsheetNorth.add(spreadsheetToolbar);
		logSpreadsheetNorth.add(Box.createVerticalStrut(2));
		spreadsheetPanel.add(logSpreadsheetNorth, BorderLayout.NORTH);
		spreadsheetPanel.add(spreadsheetCardPanel, BorderLayout.CENTER);

		miningInnerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, prospectorPanel, spreadsheetPanel);
		EdoMiningSplitPaneUi.install(miningInnerSplit);
		configureMiningSplitPane(miningInnerSplit, innerRatio);
		miningOuterSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, inventoryPanel, miningInnerSplit);
		EdoMiningSplitPaneUi.install(miningOuterSplit);
		configureMiningSplitPane(miningOuterSplit, outerRatio);

		PropertyChangeListener miningSplitPersistence = evt -> {
			if (!JSplitPane.DIVIDER_LOCATION_PROPERTY.equals(evt.getPropertyName())) {
				return;
			}
			saveMiningSplitRatios();
		};
		miningOuterSplit.addPropertyChangeListener(miningSplitPersistence);
		miningInnerSplit.addPropertyChangeListener(miningSplitPersistence);

		spreadsheetCardPanel.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				syncScatterPanelSizeToSpreadsheetCard();
			}
		});

		JPanel centerPanel = new JPanel(new BorderLayout());
		centerPanel.setOpaque(false);
		centerPanel.setBackground(EdoUi.Internal.TRANSPARENT);
		centerPanel.add(miningOuterSplit, BorderLayout.CENTER);

		add(centerPanel, BorderLayout.CENTER);

		SwingUtilities.invokeLater(() -> {
			miningOuterSplit.setResizeWeight(outerRatio);
			miningInnerSplit.setResizeWeight(innerRatio);
			miningOuterSplit.setDividerLocation(outerRatio);
			miningInnerSplit.setDividerLocation(innerRatio);
			EdoMiningSplitPaneUi.applyDividerTheme(miningOuterSplit);
			EdoMiningSplitPaneUi.applyDividerTheme(miningInnerSplit);
			syncScatterPanelSizeToSpreadsheetCard();
		});

		refreshSpreadsheetFromBackend();
		spreadsheetRefreshTimer = new Timer(SPREADSHEET_REFRESH_MS, e -> refreshSpreadsheetFromBackend());
		spreadsheetRefreshTimer.setRepeats(true);
		spreadsheetRefreshTimer.start();

		CargoMonitor.getInstance().addListener(snap -> SwingUtilities.invokeLater(() -> updateFromCargoSnapshot(snap)));
		updateFromCargoSnapshot(CargoMonitor.getInstance().getSnapshot());


		applyUiFontPreferences();
	}

	private static void configureMiningSplitPane(JSplitPane split, double resizeWeight) {
		split.setOpaque(false);
		split.setBorder(null);
		split.setContinuousLayout(true);
		split.setOneTouchExpandable(false);
		split.setDividerSize(9);
		split.setResizeWeight(Math.max(0.05, Math.min(0.95, resizeWeight)));
	}

	private void saveMiningSplitRatios() {
		if (miningOuterSplit == null || miningInnerSplit == null) {
			return;
		}
		if (miningOuterSplit.getHeight() < 32 || miningInnerSplit.getHeight() < 32) {
			return;
		}
		double outer = computeVerticalSplitRatio(miningOuterSplit);
		double inner = computeVerticalSplitRatio(miningInnerSplit);
		OverlayPreferences.setMiningPanelSplitOuterRatio(outer);
		OverlayPreferences.setMiningPanelSplitInnerRatio(inner);
		miningOuterSplit.setResizeWeight(outer);
		miningInnerSplit.setResizeWeight(inner);
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
			r = 0.05;
		} else if (r > 0.95) {
			r = 0.95;
		}
		return r;
	}

	private void syncScatterPanelSizeToSpreadsheetCard() {
		if (spreadsheetScatterPanel == null || spreadsheetCardPanel == null) {
			return;
		}
		int h = spreadsheetCardPanel.getHeight();
		if (h <= 0) {
			return;
		}
		spreadsheetScatterPanel.setPreferredSize(new Dimension(Integer.MAX_VALUE, h));
		spreadsheetScatterPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
		spreadsheetScatterPanel.revalidate();
	}

	private boolean isHighlightedProspectorRow(Row r) {
		if (r == null) {
			return false;
		}
		return prospectorHighlightNames.contains(r.getName());
	}

	private static void applyMiningColumnWidths(JTable tbl) {
		if (tbl == null) {
			return;
		}

		TableColumnModel cm = tbl.getColumnModel();
		if (cm == null || cm.getColumnCount() < 5) {
			return;
		}

		// Material | Percent | Avg Cr/t | Tons | Est. Value
		cm.getColumn(0).setMinWidth(170);
		cm.getColumn(0).setPreferredWidth(260);

		cm.getColumn(1).setMinWidth(55);
		cm.getColumn(1).setPreferredWidth(70);

		cm.getColumn(2).setMinWidth(70);
		cm.getColumn(2).setPreferredWidth(85);

		cm.getColumn(3).setMinWidth(55);
		cm.getColumn(3).setPreferredWidth(70);

		cm.getColumn(4).setMinWidth(75);
		cm.getColumn(4).setPreferredWidth(95);
	}

	/** Hide Before Amount (6), After Amount (7); keep Body (12) visible narrow. Data still in model. */
	private static void applyProspectorLogColumnVisibility(JTable tbl) {
		if (tbl == null) return;
		TableColumnModel cm = tbl.getColumnModel();
		if (cm == null || cm.getColumnCount() < 13) return;
		for (int i : new int[] { 6, 7 }) {
			TableColumn col = cm.getColumn(i);
			col.setMinWidth(0);
			col.setMaxWidth(0);
			col.setPreferredWidth(0);
			col.setWidth(0);
		}
		TableColumn bodyCol = cm.getColumn(12);
		bodyCol.setMinWidth(40);
		bodyCol.setMaxWidth(220);
		bodyCol.setPreferredWidth(80);
	}

	private static final int GREEN_THRESHOLD_AVG_CR_PER_TON = 4_000_000;
	private Color resolveRowForeground(JTable tbl, int viewRow) {
		if (tbl == null || viewRow < 0) {
			return EdoUi.User.MAIN_TEXT;
		}

		if (tbl == table) {
			int modelRow = viewRow;
			if (tbl.getRowSorter() != null) {
				modelRow = tbl.convertRowIndexToModel(viewRow);
			}

			Row r = model.getRow(modelRow);
			if (r != null) {
				if (r.isCore()) {
					return CORE_COLOR;
				}
				if (isHighlightedProspectorRow(r)) {
					return EdoUi.User.PRIMARY_HIGHLIGHT;
				}
				if (r.getEstimatedValue() > GREEN_THRESHOLD_AVG_CR_PER_TON) {
					return NON_CORE_GREEN;
				}
			}
return EdoUi.User.MAIN_TEXT;
		}

		return EdoUi.User.MAIN_TEXT;
	}

	private boolean isSummaryRow(JTable tbl, int viewRow) {
		if (tbl == null || viewRow < 0) {
			return false;
		}
		if (tbl == cargoTable) {
			int modelRow = viewRow;
			if (tbl.getRowSorter() != null) {
				modelRow = tbl.convertRowIndexToModel(viewRow);
			}
			Row r = cargoModel.getRow(modelRow);
			return r != null && r.isSummary();
		}
		return false;
	}

	private float getRevealAlpha(JTable tbl, int viewRow) {
		if (tbl == table) {
			int modelRow = viewRow;
			if (tbl.getRowSorter() != null) {
				modelRow = tbl.convertRowIndexToModel(viewRow);
			}
			return prospectorScan.getRevealAlpha(modelRow);
		}
		if (tbl == cargoTable) {
			int modelRow = viewRow;
			if (tbl.getRowSorter() != null) {
				modelRow = tbl.convertRowIndexToModel(viewRow);
			}
			return cargoScan.getRevealAlpha(modelRow);
		}
		return 1.0f;
	}

	private float getFlareAlpha(JTable tbl, int viewRow) {
		if (tbl == table) {
			int modelRow = viewRow;
			if (tbl.getRowSorter() != null) {
				modelRow = tbl.convertRowIndexToModel(viewRow);
			}
			return prospectorScan.getFlareAlpha(modelRow);
		}
		if (tbl == cargoTable) {
			int modelRow = viewRow;
			if (tbl.getRowSorter() != null) {
				modelRow = tbl.convertRowIndexToModel(viewRow);
			}
			return cargoScan.getFlareAlpha(modelRow);
		}
		return 0.0f;
	}

	private static Color applyRevealAndFlare(Color base, float reveal, float flare) {
		reveal = Math.max(0.0f, Math.min(1.0f, reveal));
		flare = Math.max(0.0f, Math.min(1.0f, flare));

		int r = base.getRed();
		int g = base.getGreen();
		int b = base.getBlue();

		int add = (int) (180f * flare);   // HOTTER glow

		// Push red harder, suppress green/blue for orange rows
		r = Math.min(255, r + add);
		g = Math.min(255, g + (int) (add * 0.35f));
		b = Math.min(255, b + (int) (add * 0.20f));

		int a = (int)(255.0f * reveal);
		return EdoUi.rgba(r, g, b, a);
	}

	public void applyUiFontPreferences() {
		applyUiFont(OverlayPreferences.getUiFont());
	}

	public void applyUiFont(Font font) {
		if (font == null) {
			return;
		}

		Font base = OverlayPreferences.getUiFont();
		Font headerFont = base.deriveFont(Font.BOLD, OverlayPreferences.getUiFontSize() + 4);
		
		prospectorLabel.setFont(headerFont);
		inventoryLabel.setFont(headerFont);
		spreadsheetLabel.setFont(headerFont);
		prospectorLogSourceLabel.setFont(base.deriveFont(Font.PLAIN, Math.max(10, OverlayPreferences.getUiFontSize() - 1)));

		uiFont = font;

		headerLabel.setFont(uiFont.deriveFont(Font.BOLD));

		table.setFont(uiFont);
		if (table.getTableHeader() != null) {
			table.getTableHeader().setFont(uiFont.deriveFont(Font.BOLD));
		}

		cargoTable.setFont(uiFont);
		if (cargoTable.getTableHeader() != null) {
			cargoTable.getTableHeader().setFont(uiFont.deriveFont(Font.BOLD));
		}

		spreadsheetTable.setFont(uiFont);
		if (spreadsheetScatterPanel != null) {
			spreadsheetScatterPanel.setFont(uiFont);
		}

		int rowH = Math.max(18, uiFont.getSize() + 6);

		table.setRowHeight(rowH);
		cargoTable.setRowHeight(rowH);

		JTableHeader th = table.getTableHeader();
		if (th != null) {
			Dimension pref = th.getPreferredSize();
			th.setPreferredSize(new Dimension(pref.width, rowH));
		}

		JTableHeader cth = cargoTable.getTableHeader();
		if (cth != null) {
			Dimension pref = cth.getPreferredSize();
			cth.setPreferredSize(new Dimension(pref.width, rowH));
		}
		applyMiningColumnWidths(table);
		applyMiningColumnWidths(cargoTable);
		applyProspectorLogColumnVisibility(spreadsheetTable);

		updateScrollerHeights();

		EdoMiningSplitPaneUi.applyDividerTheme(miningOuterSplit);
		EdoMiningSplitPaneUi.applyDividerTheme(miningInnerSplit);

		revalidate();
		repaint();
	}



	private void updateScrollerHeights() {
		updateScrollerHeight(materialsScroller, table);
		updateScrollerHeight(cargoScroller, cargoTable);
		updateScrollerHeight(spreadsheetScroller, spreadsheetTable);
		syncScatterPanelSizeToSpreadsheetCard();
		if (spreadsheetScatterPanel != null && spreadsheetScroller != null && spreadsheetCardPanel != null
				&& spreadsheetCardPanel.getHeight() <= 0) {
			Dimension d = spreadsheetScroller.getPreferredSize();
			spreadsheetScatterPanel.setPreferredSize(new Dimension(Integer.MAX_VALUE, d.height));
			spreadsheetScatterPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, d.height));
		}
	}

	private static void updateScrollerHeight(JScrollPane scroller, JTable tbl) {
		if (scroller == null || tbl == null) {
			return;
		}

		int headerH = 0;
		JTableHeader th = tbl.getTableHeader();
		if (th != null) {
			headerH = th.getPreferredSize().height;
		}

		int rowH = tbl.getRowHeight();
		int h = (rowH * VISIBLE_ROWS) + headerH;
		int minH = headerH + rowH * 3;

		scroller.setMinimumSize(new Dimension(0, minH));
		scroller.setPreferredSize(new Dimension(Integer.MAX_VALUE, h));
		scroller.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		scroller.revalidate();
	}

	private List<Row> withTotalRow(List<Row> rows) {
		if (rows == null || rows.isEmpty()) {
			return List.of();
		}

		double totalTons = 0.0;
		double totalValue = 0.0;
		List<Row> out = new ArrayList<>();
		for (Row r : rows) {
			if (r == null || r.isSummary()) {
				continue;
			}
			out.add(r);
			totalTons += r.getExpectedTons();
			totalValue += r.getEstimatedValue();
		}

		out.add(new Row("Total", Double.NaN, 0, totalTons, totalValue, false, true));
		return out;
	}


	private void updateFromCargoSnapshot(CargoMonitor.Snapshot snap) {
		try {
			if (snap == null || snap.getCargoJson() == null) {
				cargoModel.setRows(List.of());
				lastCargoTonsForLogging = new HashMap<>();
				return;
			}

			JsonObject cargoObj = snap.getCargoJson();
			List<Row> rows = buildRowsFromCargo(cargoObj);
			Set<Integer> changedModelRows = computeChangedInventoryModelRows(rows);
			cargoModel.setRows(withTotalRow(rows));
			cargoScan.startInventoryScan(cargoLayer, changedModelRows);

			// Also drive mining-log cargo tracking.
			onCargoChanged(cargoObj);
		} catch (Exception ignored) {
		}
	}


	private List<Row> buildRowsFromCargo(JsonObject cargo) {
		if (cargo == null) {
			return List.of();
		}

		JsonArray inv = null;
		if (cargo.has("Inventory") && cargo.get("Inventory").isJsonArray()) {
			inv = cargo.getAsJsonArray("Inventory");
		} else if (cargo.has("inventory") && cargo.get("inventory").isJsonArray()) {
			inv = cargo.getAsJsonArray("inventory");
		}
		if (inv == null) {
			return List.of();
		}

		List<Row> rows = new ArrayList<>();
		for (JsonElement e : inv) {
			if (e == null || !e.isJsonObject()) {
				continue;
			}
			JsonObject o = e.getAsJsonObject();

			String rawName = null;
			if (o.has("Name") && !o.get("Name").isJsonNull()) {
				rawName = o.get("Name").getAsString();
			} else if (o.has("name") && !o.get("name").isJsonNull()) {
				rawName = o.get("name").getAsString();
			}
			if (rawName == null || rawName.isBlank()) {
				continue;
			}

			String localizedName = null;
			if (o.has("Name_Localised") && !o.get("Name_Localised").isJsonNull()) {
				localizedName = o.get("Name_Localised").getAsString();
			} else if (o.has("Name_Localized") && !o.get("Name_Localized").isJsonNull()) {
				localizedName = o.get("Name_Localized").getAsString();
			} else if (o.has("name_localised") && !o.get("name_localised").isJsonNull()) {
				localizedName = o.get("name_localised").getAsString();
			} else if (o.has("name_localized") && !o.get("name_localized").isJsonNull()) {
				localizedName = o.get("name_localized").getAsString();
			}
			long count = 0;
			if (o.has("Count") && !o.get("Count").isJsonNull()) {
				try {
					count = o.get("Count").getAsLong();
				} catch (Exception ignored) {
				}
			} else if (o.has("count") && !o.get("count").isJsonNull()) {
				try {
					count = o.get("count").getAsLong();
				} catch (Exception ignored) {
				}
			}
			if (count <= 0) {
				continue;
			}

			String shownName = (localizedName != null && !localizedName.isBlank()) ? localizedName : toUiName(rawName);
			int avg = lookupAvgSell(rawName, shownName);

			double tons = count;
			double value = (avg > 0) ? (tons * avg) : 0;
			rows.add(new Row(shownName, avg, tons, value));
		}

		rows.sort(Comparator
				.comparing(Row::isCore).reversed()
				.thenComparingDouble(Row::getEstimatedValue).reversed()
				.thenComparing(Row::getName, String.CASE_INSENSITIVE_ORDER));

		return rows;
	}

	/**
	 * Canonical material keys (journal/raw → {@link #toUiName}) for mining deltas and prospector % lookup.
	 * Cargo.json {@code Name_Localised} is ignored so keys match {@link ProspectedAsteroidEvent} material names.
	 */
	private Map<String, Double> buildInventoryTonsForMiningLog(JsonObject cargo) {
		return buildInventoryTonsFromCargo(cargo, this::toUiName, false);
	}

	/**
	 * Static variant for unit tests; {@code preferLocalizedDisplayName} true matches UI cargo table keys.
	 */
	static Map<String, Double> buildInventoryTonsFromCargo(JsonObject cargo, Function<String, String> nameResolver) {
		return buildInventoryTonsFromCargo(cargo, nameResolver, true);
	}

	static Map<String, Double> buildInventoryTonsFromCargo(JsonObject cargo, Function<String, String> nameResolver,
			boolean preferLocalizedDisplayName) {
		Map<String, Double> out = new HashMap<>();
		if (cargo == null || nameResolver == null) {
			return out;
		}
		JsonArray inv = null;
		if (cargo.has("Inventory") && cargo.get("Inventory").isJsonArray()) {
			inv = cargo.getAsJsonArray("Inventory");
		} else if (cargo.has("inventory") && cargo.get("inventory").isJsonArray()) {
			inv = cargo.get("inventory").getAsJsonArray();
		}
		if (inv == null) {
			return out;
		}
		for (JsonElement e : inv) {
			if (e == null || !e.isJsonObject()) {
				continue;
			}
			JsonObject o = e.getAsJsonObject();
			String rawName = null;
			if (o.has("Name") && !o.get("Name").isJsonNull()) {
				rawName = o.get("Name").getAsString();
			} else if (o.has("name") && !o.get("name").isJsonNull()) {
				rawName = o.get("name").getAsString();
			}
			if (rawName == null || rawName.isBlank()) {
				continue;
			}
			String localizedName = null;
			if (o.has("Name_Localised") && !o.get("Name_Localised").isJsonNull()) {
				localizedName = o.get("Name_Localised").getAsString();
			} else if (o.has("Name_Localized") && !o.get("Name_Localized").isJsonNull()) {
				localizedName = o.get("Name_Localized").getAsString();
			} else if (o.has("name_localised") && !o.get("name_localised").isJsonNull()) {
				localizedName = o.get("name_localised").getAsString();
			} else if (o.has("name_localized") && !o.get("name_localized").isJsonNull()) {
				localizedName = o.get("name_localized").getAsString();
			}
			long count = 0;
			if (o.has("Count") && !o.get("Count").isJsonNull()) {
				try {
					count = o.get("Count").getAsLong();
				} catch (Exception ignored) {
				}
			} else if (o.has("count") && !o.get("count").isJsonNull()) {
				try {
					count = o.get("count").getAsLong();
				} catch (Exception ignored) {
				}
			}
			if (count <= 0) {
				continue;
			}
			String key = (preferLocalizedDisplayName && localizedName != null && !localizedName.isBlank())
					? localizedName
					: nameResolver.apply(rawName);
			out.merge(key, (double) count, Double::sum);
		}
		return out;
	}

	public static String csvEscape(String s) {
		if (s == null) {
			return "";
		}
		if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
			return "\"" + s.replace("\"", "\"\"") + "\"";
		}
		return s;
	}

	/**
	 * Handle a cargo change for mining logging: detect positive deltas and either create
	 * or update rows for the current asteroid, depending on whether we've already logged
	 * gains since the last prospector boundary.
	 */
	private void onCargoChanged(JsonObject cargoObj) {
		try {
			// Only log when armed by a prospector and when undocked.
			if (!miningLoggingArmed) {
				return;
			}
			if (isDockedSupplier != null && isDockedSupplier.getAsBoolean()) {
				return;
			}
			Map<String, Double> current = buildInventoryTonsForMiningLog(cargoObj);
			if (lastCargoTonsForLogging == null || lastCargoTonsForLogging.isEmpty()) {
				lastCargoTonsForLogging = new HashMap<>(current);
				return;
			}

			// Compute positive deltas.
			Set<String> materials = new HashSet<>();
			materials.addAll(lastCargoTonsForLogging.keySet());
			materials.addAll(current.keySet());

			Map<String, Double> deltas = new HashMap<>();
			for (String m : materials) {
				double before = lastCargoTonsForLogging.getOrDefault(m, 0.0);
				double after = current.getOrDefault(m, 0.0);
				double diff = after - before;
				if (diff > 0) {
					deltas.put(m, diff);
				}
			}
			if (deltas.isEmpty()) {
				lastCargoTonsForLogging = new HashMap<>(current);
				return;
			}

		Instant ts = Instant.now();
		String commander = OverlayPreferences.getMiningLogCommanderName();
		if (commander == null || commander.isBlank()) {
			commander = "-";
		}
		String sys = resolveSystemNameForMiningLog();
		String body = resolveBodyNameForMiningLog();
		if (sys.isEmpty()) {
			System.out.println("[EDO][Mining] WARNING: mining cargo-driven log computed blank system; this should not happen.");
		}
		String fullBodyName = GoogleSheetsBackend.buildFullBodyNameForProspectorRow(sys, body);
		// Choose a run number once per system/body and reuse it for the rest of the trip so
		// repeated cargo gains update the same run instead of starting new ones. When we have
		// explicitly marked that the next mining event should start a new run (e.g. after
		// leaving and re-entering the ring), force a new run number even if activeRun > 0.
		// Also recompute when we haven't written any rows this run yet (e.g. after docking),
		// so we advance to the next run number instead of appending to the previous run.
		int previousRun = activeRun;
		if (activeRun <= 0 || nextMiningStartsNewRun || !wroteRowsThisRun) {
			int computed = computeRunNumberForWrite(commander, sys, body, nextMiningStartsNewRun);
			if (computed != activeRun) {
				activeRun = computed;
				// New run: first write should be treated as fresh so it gets a start time.
				wroteRowsThisRun = false;
			}
			if (nextMiningStartsNewRun) {
				nextMiningStartsNewRun = false;
			}
		}
		int run = activeRun;
		if (!wroteRowsThisRun && run > 0) {
			try {
				List<ProspectorLogRow> existing = loadRowsForRunResolution();
				int maxIdx = maxAsteroidIndexForRun(existing, run);
				if (maxIdx >= 0) {
					// Resume same run (e.g. overlay restarted mid-trip): continue lettering from the log.
					// Do not overwrite a higher counter (e.g. prospector already advanced for the next rock).
					if (asteroidIdCounter <= maxIdx) {
						asteroidIdCounter = maxIdx;
					}
				} else if (previousRun != activeRun) {
					// Brand-new run with no rows yet: start at A.
					asteroidIdCounter = 0;
				}
			} catch (Exception ignored) {
				if (previousRun != activeRun) {
					asteroidIdCounter = 0;
				}
			}
		}

		// Determine asteroid ID: first gain after a boundary starts at current counter;
		// subsequent gains before the next prospector reuse the same asteroid letter.
		if (!haveActiveAsteroid) {
			if (asteroidIdCounter < 0) {
				asteroidIdCounter = 0;
			}
		}
		String asteroidId = formatAsteroidId(asteroidIdCounter);

		List<ProspectorLogRow> rows = new ArrayList<>();
		for (Map.Entry<String, Double> e : deltas.entrySet()) {
			String material = e.getKey();
			if (material == null || material.isBlank()) {
				material = "-";
			}
			double afterTons = current.getOrDefault(material, 0.0);
			double baseline = asteroidBaselineTons.getOrDefault(material, lastCargoTonsForLogging.getOrDefault(material, 0.0));
			asteroidBaselineTons.put(material, baseline);

			double beforeTons = baseline;
			double difference = afterTons - beforeTons;
			if (difference <= 0) {
				continue;
			}

			double pct = lastPercentByMaterialAtProspector.getOrDefault(material, 0.0);
			if (Double.isNaN(pct) || pct < 0.0) {
				pct = 0.0;
			}

			// Add 0.5 only when we have material, to approximate refinery.
			double beforeAdjusted = Double.isNaN(beforeTons) ? 0.0 : (beforeTons > 0 ? beforeTons + 0.5 : beforeTons);
			double afterAdjusted = Double.isNaN(afterTons) ? 0.0 : (afterTons > 0 ? afterTons + 0.5 : afterTons);

			String coreType = "";
			int duds = dudCounter;

			// Run start time (sheet column "Start time"):
			// - Only the *first successful cargo batch of this run* sets runStart on each row in that batch
			//   (e.g. both 24 A Platinum and 24 A Painite get the same undock/start instant) so no material line
			//   is missing the trip start in the UI. Later asteroids in the same run use null here.
			// - Google Sheets upserts must NOT overwrite a non-blank start cell; that policy lives in
			//   ProspectorMiningLogPolicy.shouldWriteRunStartOnUpsertExistingRow (+ unit tests).
			// - Run *end* is written only on dock, on a single canonical row; see ProspectorMiningLogPolicy /
			//   MiningRunNumberResolver class docs and mining package tests.
			Instant runStart = !wroteRowsThisRun ? (lastUndockTime != null ? lastUndockTime : ts) : null;
			Instant runEnd = null;
			rows.add(new ProspectorLogRow(run, asteroidId, fullBodyName, ts, material, pct, beforeAdjusted, afterAdjusted, difference, commander, coreType, duds, runStart, runEnd));
		}

		if (!rows.isEmpty()) {
			ProspectorLogBackend backend = prospectorBackendSupplier.get();
			boolean ok;
			if (backend instanceof GoogleSheetsBackend sheetsBackend) {
				ProspectorWriteResult wr = sheetsBackend.upsertRowsResult(rows);
				ok = wr.isOk();
				if (!ok) {
					applyMiningSheetsStatusError(wr.getMessage());
				}
			} else {
				try {
					backend.appendRows(rows);
					ok = true;
				} catch (Exception ex) {
					ok = false;
					String m = ex.getMessage();
					applyMiningSheetsStatusError(m != null && !m.isBlank() ? m : "Mining log write failed.");
				}
			}
			if (ok) {
				applyMiningSheetsStatusClear();
				refreshSpreadsheetFromBackend();
				wroteRowsThisRun = true;
				haveActiveAsteroid = true;
				loggedCargoSinceLastProspector = true;
			}
		}

			lastCargoTonsForLogging = new HashMap<>(current);
		} finally {
			syncScatterActiveRun();
		}
	}

	/**
	 * Scatter asteroid markers only while a prospector trip is armed; uses {@link #activeRun} as the session run.
	 */
	private void syncScatterActiveRun() {
		if (spreadsheetScatterPanel == null) {
			return;
		}
		if (!miningLoggingArmed) {
			spreadsheetScatterPanel.clearScatterAsteroidModelForSessionEnd();
		}
		int r = activeRun;
		if (!miningLoggingArmed) {
			r = 0;
		}
		spreadsheetScatterPanel.setSessionActiveRun(r);
		syncScatterMarkerMaterials();
	}

	/**
	 * Scatter line-art rocks only for materials on the latest prospector readout (normalized keys),
	 * so stale sheet rows (e.g. old materials) do not get markers.
	 */
	private void syncScatterMarkerMaterials() {
		if (spreadsheetScatterPanel == null) {
			return;
		}
		if (!miningLoggingArmed) {
			spreadsheetScatterPanel.setProspectorMaterialKeysForMarkers(Set.of());
			return;
		}
		Set<String> keys = new HashSet<>();
		for (String name : lastPercentByMaterialAtProspector.keySet()) {
			if (name != null && !name.isBlank()) {
				keys.add(GalacticAveragePrices.normalizeMaterialKey(name));
			}
		}
		spreadsheetScatterPanel.setProspectorMaterialKeysForMarkers(keys);
	}

	/** Called when undocking is detected; records time so the first row of the next run gets it as run start. */
	public void onUndocked() {
		lastUndockTime = Instant.now();
		if (sessionStateChangeCallback != null) {
			sessionStateChangeCallback.run();
		}
	}

	/** Clear last undock time (e.g. when we transition to docked after restart). Invokes session-state callback so state is saved. */
	public void clearLastUndockTime() {
		lastUndockTime = null;
		if (sessionStateChangeCallback != null) {
			sessionStateChangeCallback.run();
		}
	}

	public void setSessionStateChangeCallback(Runnable callback) {
		this.sessionStateChangeCallback = callback;
	}

	public void fillSessionState(EdoSessionState state) {
		if (state == null) return;
		if (lastUndockTime != null) {
			state.setLastUndockTime(lastUndockTime.toString());
		}
	}

	public void applySessionState(EdoSessionState state) {
		if (state == null) return;
		String s = state.getLastUndockTime();
		if (s != null && !s.isBlank()) {
			try {
				lastUndockTime = Instant.parse(s);
			} catch (Exception ignored) {
			}
		}
	}

	/**
	 * Called when docking is detected: this now defines the end of a "trip" for mining runs.
	 * We flush any pending gains to CSV (using last-seen percent) so the last asteroid is recorded,
	 * but only if we have a previous prospector snapshot for this trip. Then we reset asteroid IDs
	 * so the next trip starts at A and clear cargo-driven logging state.
	 */
	public void onDocked() {
		// If we've never seen a prospector event in this trip, there is nothing to flush; avoid
		// logging fake "0 -> X" gains when docking with pre-existing cargo.
		if (lastInventoryTonsAtProspector == null || lastInventoryTonsAtProspector.isEmpty()) {
			asteroidIdCounter = 0;
			prospectorLimpetSeenThisTrip = false;
			loggedCargoSinceLastProspector = false;
			wroteRowsThisRun = false;
			miningLoggingArmed = false;
			haveActiveAsteroid = false;
			asteroidBaselineTons = new HashMap<>();
			lastCargoTonsForLogging = new HashMap<>();
			lastUndockTime = null;
			if (spreadsheetScatterPanel != null) {
				spreadsheetScatterPanel.clearScatterAsteroidModelForSessionEnd();
			}
			syncScatterActiveRun();
			return;
		}
		// Set run end time on the canonical row for the run we just finished.
		if (wroteRowsThisRun && activeRun > 0) {
			String commander = OverlayPreferences.getMiningLogCommanderName();
			if (commander != null && !commander.isBlank()) {
				ProspectorLogBackend backend = prospectorBackendSupplier.get();
				boolean ok;
				if (backend instanceof GoogleSheetsBackend gs) {
					ProspectorWriteResult wr = gs.updateRunEndTimeResult(commander, activeRun, Instant.now());
					ok = wr.isOk();
					if (!ok) {
						applyMiningSheetsStatusError(wr.getMessage());
					}
				} else {
					try {
						backend.updateRunEndTime(commander, activeRun, Instant.now());
						ok = true;
					} catch (Exception ex) {
						ok = false;
						String m = ex.getMessage();
						applyMiningSheetsStatusError(m != null && !m.isBlank() ? m : "Could not write run end time.");
					}
				}
				if (ok) {
					applyMiningSheetsStatusClear();
					refreshSpreadsheetFromBackend();
				}
			}
		}
		lastUndockTime = null;
		if (sessionStateChangeCallback != null) {
			sessionStateChangeCallback.run();
		}
		// With cargo-driven logging, we've already written rows as inventory changed.
		// Avoid appending a duplicate summary row on dock; just clear state for the next trip.
		lastInventoryTonsAtProspector = new HashMap<>();
		lastPercentByMaterialAtProspector = new HashMap<>();
		asteroidIdCounter = 0;
		prospectorLimpetSeenThisTrip = false;
		loggedCargoSinceLastProspector = false;
		wroteRowsThisRun = false;
		miningLoggingArmed = false;
		haveActiveAsteroid = false;
		asteroidBaselineTons = new HashMap<>();
		lastCargoTonsForLogging = new HashMap<>();
		if (spreadsheetScatterPanel != null) {
			spreadsheetScatterPanel.clearScatterAsteroidModelForSessionEnd();
		}
		syncScatterActiveRun();
	}

	/**
	 * Historically we treated the start of an FSD jump as the end of a run.
	 * Runs are now defined as \"from the first time we shoot a prospector limpet to the next dock\",
	 * so FSD jumps no longer advance the run counter.
	 */
	public void onStartJump(StartJumpEvent event) {
		// Intentionally no-op: FSD jumps no longer segment runs.
	}

	/**
	 * Compute the next run number for new log rows. Invariants and edge cases are documented and tested on
	 * {@link MiningRunNumberResolver}; do not duplicate that logic here.
	 */
	private int computeRunNumberForWrite(String commander, String system, String body, boolean forceNewRun) {
		try {
			List<ProspectorLogRow> existing = loadRowsForRunResolution();
			return MiningRunNumberResolver.compute(commander, system, body, forceNewRun, existing);
		} catch (Exception ignored) {
			return 1;
		}
	}

	/**
	 * Rows used for run allocation and asteroid resume: commander-scoped tab for Google Sheets, full log for CSV.
	 */
	private List<ProspectorLogRow> loadRowsForRunResolution() {
		try {
			ProspectorLogBackend backend = prospectorBackendSupplier.get();
			if (backend instanceof GoogleSheetsBackend gs) {
				String cmd = OverlayPreferences.getMiningLogCommanderName();
				if (cmd == null || cmd.isBlank()) {
					cmd = "-";
				}
				return gs.loadRowsWithStatusForCommander(cmd).getRows();
			}
			return backend.loadRows();
		} catch (Exception e) {
			return List.of();
		}
	}

	/** Format asteroid index as A, B, ..., Z, AA, AB, ... (package-private for tests). */
	static String formatAsteroidId(int index) {
		if (index < 0) return "";
		StringBuilder sb = new StringBuilder();
		int n = index;
		do {
			sb.insert(0, (char) ('A' + (n % 26)));
			n = n / 26 - 1;
		} while (n >= 0);
		return sb.toString();
	}

	/**
	 * Inverse of {@link #formatAsteroidId} for log-backed lettering; invalid or blank ids return -1.
	 * Package-private for unit tests (same package).
	 */
	static int parseAsteroidIdToIndex(String id) {
		if (id == null || id.isBlank()) {
			return -1;
		}
		int result = 0;
		for (int i = 0; i < id.length(); i++) {
			char c = Character.toUpperCase(id.charAt(i));
			if (c < 'A' || c > 'Z') {
				return -1;
			}
			result = result * 26 + (c - 'A' + 1);
		}
		return result - 1;
	}

	private static int maxAsteroidIndexForRun(List<ProspectorLogRow> rows, int runNum) {
		int max = -1;
		if (rows == null || rows.isEmpty()) {
			return -1;
		}
		for (ProspectorLogRow r : rows) {
			if (r == null || r.getRun() != runNum) {
				continue;
			}
			int idx = parseAsteroidIdToIndex(r.getAsteroidId());
			if (idx > max) {
				max = idx;
			}
		}
		return max;
	}

	/**
	 * System name for mining log rows and {@link MiningRunNumberResolver}: {@link SystemState} when the overlay
	 * supplies it; otherwise journal-cached {@link #currentSystemName} / {@link #lastNonEmptySystemName}.
	 */
	private String resolveSystemNameForMiningLog() {
		Supplier<SystemState> sup = systemStateSupplier;
		if (sup != null) {
			try {
				SystemState st = sup.get();
				if (st != null) {
					String n = st.getSystemName();
					if (n != null && !n.isBlank()) {
						return n.trim();
					}
				}
			} catch (RuntimeException ignored) {
			}
		}
		if (currentSystemName != null && !currentSystemName.isBlank()) {
			return currentSystemName.trim();
		}
		return lastNonEmptySystemName != null ? lastNonEmptySystemName.trim() : "";
	}

	/**
	 * Body column for mining log rows: journal/Status pipeline first, then a direct read of {@code Status.json}
	 * in the configured journal folder. The game often omits body on some journal lines while still writing
	 * {@code BodyName} in the live status file (e.g. ring mining), which previously left sheet Body blank.
	 */
	private String resolveBodyNameForMiningLog() {
		String body = currentBodyName != null && !currentBodyName.isBlank()
				? currentBodyName.trim()
				: (lastNonEmptyBodyName != null && !lastNonEmptyBodyName.isBlank() ? lastNonEmptyBodyName.trim() : "");
		if (!body.isEmpty()) {
			return body;
		}
		return readCachedBodyNameFromStatusFile();
	}

	/**
	 * Parses {@code Status.json} next to the commander journals (same path as {@link org.dce.ed.logreader.LiveJournalMonitor}).
	 * Cached by file mtime so rapid cargo-driven writes do not re-read every delta.
	 */
	private String readCachedBodyNameFromStatusFile() {
		synchronized (statusBodyReadLock) {
			try {
				Path dir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
				Path statusPath = EliteLogFileLocator.findStatusFile(dir);
				if (statusPath == null) {
					return "";
				}
				long m = Files.getLastModifiedTime(statusPath).toMillis();
				if (m == statusBodyCacheMtime && statusBodyCacheValue != null) {
					return statusBodyCacheValue;
				}
				StatusEvent se = MINING_STATUS_PARSER.parseStatusJsonFile(statusPath);
				String bn = "";
				if (se != null && se.getBodyName() != null && !se.getBodyName().isBlank()) {
					bn = se.getBodyName().trim();
				}
				statusBodyCacheMtime = m;
				statusBodyCacheValue = bn;
				return bn;
			} catch (IOException | RuntimeException ignored) {
				return "";
			}
		}
	}

	/**
	 * After restart, align the in-memory asteroid letter with rows already stored for the resolved run
	 * (continuing an open run). No-op once we have written this trip or when the backend has no rows for that run.
	 */
	private void syncAsteroidCounterFromBackendForCurrentLocation(boolean forceNewRun) {
		if (wroteRowsThisRun) {
			return;
		}
		String commander = OverlayPreferences.getMiningLogCommanderName();
		if (commander == null || commander.isBlank()) {
			commander = "-";
		}
		String sys = resolveSystemNameForMiningLog();
		String body = resolveBodyNameForMiningLog();
		int run = computeRunNumberForWrite(commander, sys, body, forceNewRun);
		try {
			List<ProspectorLogRow> existing = loadRowsForRunResolution();
			int maxIdx = maxAsteroidIndexForRun(existing, run);
			if (maxIdx >= 0) {
				asteroidIdCounter = maxIdx;
			}
		} catch (Exception ignored) {
		}
	}

	/** Update cached location from Location event (system + body). */
	public void updateFromLocation(LocationEvent event) {
		if (event == null) {
			return;
		}
		String sys = event.getStarSystem();
		String body = event.getBody();
		String newSystem = (sys != null) ? sys.trim() : "";
		String newBody = (body != null) ? body.trim() : "";

		if (newSystem.isEmpty()) {
			System.out.println("[EDO][Mining] LocationEvent produced blank system; keeping previous system="
					+ currentSystemName);
		}
		if (body == null || body.isBlank()) {
			System.out.println("[EDO][Mining] LocationEvent produced blank body; previous body="
					+ currentBodyName);
			// Reflect the "no body" state in the current field while preserving lastNonEmptyBodyName.
			currentBodyName = "";
		}
		// If we changed bodies or systems, mark that the next mining event should start
		// a fresh run in the new location. We intentionally do NOT reset the asteroid
		// letter counter here so that an in-progress run that spans a brief location
		// blip (e.g. ring vs. body) doesn't reuse asteroid ID \"A\" mid-run.
//		if (!java.util.Objects.equals(currentSystemName, newSystem) ||
//			!java.util.Objects.equals(currentBodyName, newBody)) {
//			activeRun = 0;
//			haveActiveAsteroid = false;
//			asteroidBaselineTons = new HashMap<>();
//			lastCargoTonsForLogging = new HashMap<>();
//			// Left previous location; next meaningful mining (even if we return to same ring)
//			// starts a new run once the current one has an end time.
//			nextMiningStartsNewRun = true;
//		}
		// Never regress to blank once we have a non-empty value. This keeps
		// mining logs from seeing "-" when journal briefly omits system/body.
		if (!newSystem.isEmpty()) {
			currentSystemName = newSystem;
			lastNonEmptySystemName = newSystem;
		}
		if (!newBody.isEmpty()) {
			currentBodyName = newBody;
			lastNonEmptyBodyName = newBody;
		}
	}

	/** Update cached body name from Status event. */
	public void updateFromStatus(StatusEvent event) {
		if (event == null) {
			return;
		}
		String body = event.getBodyName();
		if (body == null || body.isBlank()) {
//			System.out.println("[EDO][Mining] StatusEvent produced blank body; previous body="
//					+ currentBodyName);
			// Reflect the "no body" state while preserving lastNonEmptyBodyName.
			currentBodyName = "";
			return;
		}
		if (body != null && !body.isBlank()) {
			String b = body.trim();
			currentBodyName = b;
			lastNonEmptyBodyName = b;
		}
	}

	/** Update system and body from SupercruiseExit (e.g. when dropping at a ring) so spreadsheet body column populates. */
	public void updateFromSupercruiseExit(SupercruiseExitEvent event) {
		if (event == null) return;
		String sys = event.getStarSystem();
		if (sys == null || sys.isBlank()) {
			System.out.println("[EDO][Mining] SupercruiseExitEvent produced blank system; keeping previous system="
					+ currentSystemName);
		}
		if (sys != null && !sys.isBlank()) {
			String s = sys.trim();
			currentSystemName = s;
			lastNonEmptySystemName = s;
		}
		String body = event.getBody();
		if (body == null || body.isBlank()) {
			System.out.println("[EDO][Mining] SupercruiseExitEvent produced blank body; previous body="
					+ currentBodyName);
			currentBodyName = "";
		}
		if (body != null && !body.isBlank()) {
			String b = body.trim();
			currentBodyName = b;
			lastNonEmptyBodyName = b;
		}
	}

	private void appendProspectorCsv(ProspectedAsteroidEvent event, Map<String, Double> currentInventory) {
		Instant ts = event != null ? event.getTimestamp() : null;
		Set<String> materials = new HashSet<>();
		Map<String, Double> fallbackPct = new HashMap<>();
		if (event != null) {
			for (MaterialProportion mp : event.getMaterials()) {
				if (mp != null && mp.getName() != null) {
					String name = toUiName(mp.getName());
					if (name != null && !name.isBlank()) {
						materials.add(name);
						fallbackPct.put(name, mp.getProportion());
					}
				}
			}
			// Include motherlode (core) so we log it when inventory increases (journal lists it separately from Materials)
			String motherlode = event.getMotherlodeMaterial();
			if (motherlode != null && !motherlode.isBlank()) {
				String name = toUiName(motherlode);
				if (name != null && !name.isBlank()) {
					materials.add(name);
					fallbackPct.putIfAbsent(name, 100.0); // core is 100% that material
				}
			}
		}
		boolean wrote = appendProspectorCsvRows(ts, currentInventory, materials, fallbackPct, event);
		if (event != null && !wrote) {
			dudCounter++;
		}
	}

	/** Write log rows for materials that increased; uses lastInventoryTonsAtProspector and lastPercentByMaterialAtProspector.
	 * @param event when non-null, from a prospector limpet (assign asteroid ID, core type, duds; dud counter is updated by caller if no rows written).
	 * @return true if at least one row was written to the backend */
	private boolean appendProspectorCsvRows(Instant ts, Map<String, Double> currentInventory, Set<String> materialsToConsider, Map<String, Double> fallbackPercentByMaterial, ProspectedAsteroidEvent event) {
		if (materialsToConsider == null || materialsToConsider.isEmpty()) {
			return false;
		}
		// Only write prospector-originated rows when undocked (mining happens in the ring, not while docked).
		// For the final flush on docking (event == null), we always allow writing so the run closes correctly.
		if (event != null && isDockedSupplier != null && isDockedSupplier.getAsBoolean()) {
			return false;
		}
		String commander = OverlayPreferences.getMiningLogCommanderName();
		if (commander == null || commander.isBlank()) {
			commander = "-";
		}
		String sys = resolveSystemNameForMiningLog();
		String body = resolveBodyNameForMiningLog();
		String fullBodyName = GoogleSheetsBackend.buildFullBodyNameForProspectorRow(sys, body);

		// Determine run number from existing sheet rows for this commander and system/body.
		int run = computeRunNumberForWrite(commander, sys, body, false);
		String asteroidId = "";
		String coreType = "";
		int duds = 0;
		if (event != null) {
			asteroidId = formatAsteroidId(asteroidIdCounter);
			coreType = event.getMotherlodeMaterial() != null ? event.getMotherlodeMaterial() : "";
			duds = dudCounter;
		}
		List<ProspectorLogRow> rows = new ArrayList<>();
		for (String material : materialsToConsider) {
			if (material == null || material.isBlank()) {
				material = "-";
			}
			double pct = lastPercentByMaterialAtProspector.getOrDefault(material,
				fallbackPercentByMaterial != null ? fallbackPercentByMaterial.getOrDefault(material, 0.0) : 0.0);
			if (Double.isNaN(pct) || pct < 0.0) {
				pct = 0.0;
			}
			double beforeTons = lastInventoryTonsAtProspector.getOrDefault(material, 0.0);
			double afterTons = currentInventory.getOrDefault(material, 0.0);
			double difference = afterTons - beforeTons;
			if (difference <= 0) {
				continue;
			}
			// Add 0.5 only when we have material, to approximate amount still in refinery (avoids showing 0.5 "before" when inventory was 0)
			double beforeAdjusted = Double.isNaN(beforeTons) ? 0.0 : (beforeTons > 0 ? beforeTons + 0.5 : beforeTons);
			double afterAdjusted = Double.isNaN(afterTons) ? 0.0 : (afterTons > 0 ? afterTons + 0.5 : afterTons);
			// Same run-start semantics as the cargo-driven path (see comment above new ProspectorLogRow in onCargoChanged).
			Instant runStart = !wroteRowsThisRun ? (lastUndockTime != null ? lastUndockTime : ts) : null;
			Instant runEnd = null;
			rows.add(new ProspectorLogRow(run, asteroidId, fullBodyName, ts, material, pct, beforeAdjusted, afterAdjusted, difference, commander, coreType, duds, runStart, runEnd));
		}
		if (rows.isEmpty()) {
			return false;
		}
		if (event != null) {
			asteroidIdCounter++;
			dudCounter = 0;
		}
		ProspectorLogBackend backend = prospectorBackendSupplier.get();
		boolean ok;
		if (backend instanceof GoogleSheetsBackend sheetsBackend) {
			ProspectorWriteResult wr = sheetsBackend.upsertRowsResult(rows);
			ok = wr.isOk();
			if (!ok) {
				applyMiningSheetsStatusError(wr.getMessage());
			}
		} else {
			try {
				backend.appendRows(rows);
				ok = true;
			} catch (Exception e) {
				ok = false;
				String m = e.getMessage();
				applyMiningSheetsStatusError(m != null && !m.isBlank() ? m : "Mining log write failed.");
			}
		}
		if (ok) {
			applyMiningSheetsStatusClear();
			refreshSpreadsheetFromBackend();
			wroteRowsThisRun = true;
			return true;
		}
		return false;
	}

	private void updateProspectorLogSourceLabelText() {
		if (prospectorLogSourceLabel == null) {
			return;
		}
		String backend = OverlayPreferences.getMiningLogBackend();
		String url = OverlayPreferences.getMiningGoogleSheetsUrl();
		if ("google".equals(backend) && url != null && !url.isBlank()) {
			prospectorLogSourceLabel.setText("Log source: Google Sheets");
			prospectorLogSourceLabel.setForeground(EdoUi.User.MAIN_TEXT);
		} else {
			prospectorLogSourceLabel.setText(
				"Log source: local ~/.edo/prospector_log.csv (not the cloud sheet). Enable Google Sheets in Preferences → Mining to load your spreadsheet.");
			prospectorLogSourceLabel.setForeground(EdoUi.User.VALUABLE);
		}
	}

	/** Load rows from backend and update spreadsheet table on EDT. */
	void refreshSpreadsheetFromBackend() {
		synchronized (spreadsheetRefreshLock) {
			if (spreadsheetRefreshWorkerRunning) {
				spreadsheetRefreshPending = true;
				return;
			}
			spreadsheetRefreshWorkerRunning = true;
		}
		new javax.swing.SwingWorker<ProspectorLoadResult, Void>() {
			@Override
			protected ProspectorLoadResult doInBackground() {
				try {
					ProspectorLogBackend backend = prospectorBackendSupplier.get();
					if (backend instanceof GoogleSheetsBackend sheetsBackend) {
						return sheetsBackend.loadRowsWithStatus();
					}
					// Local CSV backend has no notion of "empty sheet" vs "error"; treat as OK.
					java.util.List<ProspectorLogRow> rows = backend.loadRows();
					return new ProspectorLoadResult(ProspectorLoadResult.Status.OK, rows);
				} catch (Exception e) {
					String m = e.getMessage();
					return new ProspectorLoadResult(ProspectorLoadResult.Status.ERROR, java.util.Collections.emptyList(),
							m != null && !m.isBlank() ? m : "Could not load mining log.");
				}
			}
			@Override
			protected void done() {
				try {
					ProspectorLoadResult result = get();
					updateProspectorLogSourceLabelText();
					if (result == null || spreadsheetModel == null) {
						return;
					}
					switch (result.getStatus()) {
						case OK -> {
							applyMiningSheetsStatusClear();
							java.util.List<ProspectorLogRow> rows = result.getRows();
							lastGoodSpreadsheetRows = rows != null ? rows : java.util.Collections.emptyList();
							spreadsheetModel.setRows(lastGoodSpreadsheetRows, matcher);
							scrollProspectorLogTableToTop();
							if (spreadsheetScatterWrapper != null) {
								spreadsheetScatterWrapper.setRows(lastGoodSpreadsheetRows);
							}
							syncScatterActiveRun();
						}
						case EMPTY_SHEET -> {
							applyMiningSheetsStatusClear();
							lastGoodSpreadsheetRows = java.util.Collections.emptyList();
							spreadsheetModel.setRows(lastGoodSpreadsheetRows, matcher);
							scrollProspectorLogTableToTop();
							if (spreadsheetScatterWrapper != null) {
								spreadsheetScatterWrapper.setRows(lastGoodSpreadsheetRows);
							}
							syncScatterActiveRun();
						}
						case ERROR -> {
							String detail = result.getDetailMessage();
							String shown = detail != null && !detail.isBlank()
									? detail
									: "Could not refresh mining log from Google Sheets.";
							applyMiningSheetsStatusError(shown);
							maybeOfferGoogleSheetsReconnectDialog(shown);
							// Keep showing the last good data when there is a transient error.
							if (lastGoodSpreadsheetRows != null) {
								spreadsheetModel.setRows(lastGoodSpreadsheetRows, matcher);
								scrollProspectorLogTableToTop();
								if (spreadsheetScatterWrapper != null) {
									spreadsheetScatterWrapper.setRows(lastGoodSpreadsheetRows);
								}
								syncScatterActiveRun();
							}
						}
					}
				} catch (Exception ignored) {
				} finally {
					boolean runAgain;
					synchronized (spreadsheetRefreshLock) {
						spreadsheetRefreshWorkerRunning = false;
						runAgain = spreadsheetRefreshPending;
						spreadsheetRefreshPending = false;
					}
					if (runAgain) {
						refreshSpreadsheetFromBackend();
					}
				}
			}
		}.execute();
	}

	private Set<Integer> computeChangedInventoryModelRows(List<Row> newRows) {
		if (newRows == null) {
			return Set.of();
		}

		Set<String> changedNames = new HashSet<>();
		Map<String, Long> now = new HashMap<>();

		for (Row r : newRows) {
			if (r == null) {
				continue;
			}
			String name = r.getName();
			if (name == null) {
				continue;
			}
			long tons = Math.round(r.getExpectedTons());
			now.put(name, tons);

			Long old = lastCargoTonsByName.get(name);
			if (old == null || old.longValue() != tons) {
				changedNames.add(name);
			}
		}

		boolean removedSomething = false;
		for (String oldName : lastCargoTonsByName.keySet()) {
			if (!now.containsKey(oldName)) {
				removedSomething = true;
				break;
			}
		}

		lastCargoTonsByName.clear();
		lastCargoTonsByName.putAll(now);

		Set<Integer> changedModelRows = new HashSet<>();
		for (int i = 0; i < newRows.size(); i++) {
			Row r = newRows.get(i);
			if (r == null) {
				continue;
			}
			if (changedNames.contains(r.getName())) {
				changedModelRows.add(i);
			}
		}

		// The Total row changes whenever any cargo line changes.
		if (!changedModelRows.isEmpty() || removedSomething) {
			changedModelRows.add(newRows.size());
		}

		return changedModelRows;
	}

    public void applyOverlayBackground(Color bg) {
        if (bg == null) {
            bg = EdoUi.Internal.TRANSPARENT;
        }

        boolean opaque = bg.getAlpha() >= 255;
        setOpaque(opaque);
        setBackground(bg);

        // Keep tables non-opaque so the panel background shows through.
        table.setOpaque(false);
        cargoTable.setOpaque(false);
        spreadsheetTable.setOpaque(false);
        // Keep headers non-opaque in transparent mode so backing shows through.
        if (!opaque) {
            if (table.getTableHeader() != null) table.getTableHeader().setOpaque(false);
            if (cargoTable.getTableHeader() != null) cargoTable.getTableHeader().setOpaque(false);
            if (spreadsheetTable.getTableHeader() != null) spreadsheetTable.getTableHeader().setOpaque(false);
        }
        if (spreadsheetScatterWrapper != null) {
            spreadsheetScatterWrapper.applyScatterControlOpacity(!opaque);
        }

        repaint();
    }

    public void applyOverlayTransparency(boolean transparent) {
        // Legacy wrapper
        Color bg = OverlayPreferences.buildOverlayBackgroundColor(
                OverlayPreferences.getOverlayBackgroundColor(),
                transparent ? 100 : OverlayPreferences.getOverlayTransparencyPercent()
        );
        applyOverlayBackground(bg);
    }

    private static final class ProspectorAnnouncement {
    	private final boolean single;
    	private final String material;
    	private final int pct;

    	private final String listText;
    	private final int minPct;
    	private final int maxPct;

    	private final String sig;

    	private ProspectorAnnouncement(String material, int pct, String sig) {
    		this.single = true;
    		this.material = material;
    		this.pct = pct;

    		this.listText = null;
    		this.minPct = 0;
    		this.maxPct = 0;

    		this.sig = sig;
    	}

    	private ProspectorAnnouncement(String listText, int minPct, int maxPct, String sig) {
    		this.single = false;
    		this.material = null;
    		this.pct = 0;

    		this.listText = listText;
    		this.minPct = minPct;
    		this.maxPct = maxPct;

    		this.sig = sig;
    	}
    }



    private ProspectorAnnouncement buildProspectorAnnouncement(ProspectedAsteroidEvent event, List<Row> rows) {
    	if (event == null || rows == null || rows.isEmpty()) {
    		prospectorHighlightNames.clear();
    		return null;
    	}

    	double minProp = OverlayPreferences.getProspectorMinProportionPercent();
    	if (minProp <= 0.0) {
    		prospectorHighlightNames.clear();
    		return null;
    	}

    	String materialsCsv = OverlayPreferences.getProspectorMaterialsCsv();
    	Set<String> allowed = new HashSet<>();
    	if (materialsCsv != null && !materialsCsv.isBlank()) {
    		for (String s : materialsCsv.split(",")) {
    			if (s == null) {
    				continue;
    			}
    			String norm = GalacticAveragePrices.normalizeMaterialKey(s.trim());
    			if (norm != null && !norm.isBlank()) {
    				allowed.add(norm);
    			}
    		}
    	}

    	double minEstValueForAnnounce = OverlayPreferences.getProspectorMinAvgValueCrPerTon();

    	
    	

		prospectorHighlightNames.clear();
    	Row best = null;
    	List<Row> matches = new ArrayList<>();
    	double minPct = Double.POSITIVE_INFINITY;
    	double maxPct = Double.NEGATIVE_INFINITY;

    	for (Row r : rows) {
    		if (r == null || r.isSummary() || r.isCore()) {
    			continue;
    		}

    		double pct = r.getProportionPercent();
			if (Double.isNaN(pct)) {
				continue;
			}

			boolean pctOk = !Double.isNaN(pct) && pct >= minProp;
boolean csvOk = false;
    		if (!allowed.isEmpty()) {
    			String norm = GalacticAveragePrices.normalizeMaterialKey(r.getName());
    			if (norm != null && !norm.isBlank()) {
    				for (String a : allowed) {
    					String needle = GalacticAveragePrices.normalizeMaterialKey(a);
    					if (needle != null && !needle.isBlank() && norm.contains(needle)) {
    						csvOk = true;
    						break;
    					}
    				}
    			}
    		}

    		boolean valueOk = false;
    		if (minEstValueForAnnounce > 0) {
    			valueOk = r.getEstimatedValue() >= minEstValueForAnnounce;
    		}

    		// RULE: include if (pctOk && csvOk) OR valueOk
			if (!((pctOk && csvOk) || valueOk)) {
				continue;
			}
matches.add(r);
			prospectorHighlightNames.add(r.getName());

    		if (pct < minPct) {
    			minPct = pct;
    		}
    		if (pct > maxPct) {
    			maxPct = pct;
    		}

    		if (best == null || r.getEstimatedValue() > best.getEstimatedValue()) {
    			best = r;
    		}
    	}

    	if (matches.isEmpty()) {
			prospectorHighlightNames.clear();
			return null;
		}
matches.sort(Comparator.comparingDouble(Row::getProportionPercent).reversed());
    	List<String> names = new ArrayList<>();
    	for (Row r : matches) {
    		names.add(r.getName());
    	}

    	int minRounded = (int) Math.round(minPct);
    	int maxRounded = (int) Math.round(maxPct);

    	String ts = event.getTimestamp().toString();
    	if (ts.length() > 19) {
    		ts = ts.substring(0, 19);
    	}

    	// SINGLE vs LIST
    	if (names.size() == 1) {
    		Row only = matches.get(0);
    		int pctRounded = (int) Math.round(only.getProportionPercent());

    		String sig = ts + "|" + only.getName() + "|" + pctRounded;
    		if (sig.equals(lastProspectorAnnouncementSig)) {
    			return null;
    		}

    		return new ProspectorAnnouncement(only.getName(), pctRounded, sig);
    	}

    	String sig = ts + "|" + String.join(",", names) + "|" + minRounded + "|" + maxRounded;
    	if (sig.equals(lastProspectorAnnouncementSig)) {
    		return null;
    	}

    	String listText = joinWithAnd(names);
    	return new ProspectorAnnouncement(listText, minRounded, maxRounded, sig);
    }

    
	public void updateFromProspector(ProspectedAsteroidEvent event) {
		lastProspectorUpdateWasOnEdt = SwingUtilities.isEventDispatchThread();
		if (event == null) {
			model.setRows(List.of());
			headerLabel.setText("Mining (latest prospector)");
			return;
		}

		// Prospector events now act as asteroid boundaries and update percent estimates,
		// but do not directly write spreadsheet rows. Cargo changes drive logging.
		miningLoggingArmed = true;
		// First limpet after overlay start (or mid-run resume): match lettering to the log so we do not restart at A
		// while {@link #computeRunNumberForWrite} continues an open run.
		if (!prospectorLimpetSeenThisTrip && !wroteRowsThisRun) {
			syncAsteroidCounterFromBackendForCurrentLocation(nextMiningStartsNewRun);
		}
		// New limpet after the first: advance the letter only if the previous rock produced logged cargo (not a dud).
		if (prospectorLimpetSeenThisTrip && loggedCargoSinceLastProspector) {
			asteroidIdCounter++;
		}
		prospectorLimpetSeenThisTrip = true;
		loggedCargoSinceLastProspector = false;

		asteroidBaselineTons = new HashMap<>();
		haveActiveAsteroid = false;

		// Snapshot current cargo so we can log inventory deltas at dock flush if needed.
		CargoMonitor.Snapshot cargoSnap = CargoMonitor.getInstance().getSnapshot();
		Map<String, Double> currentInventory = buildInventoryTonsForMiningLog(cargoSnap != null ? cargoSnap.getCargoJson() : null);
		lastInventoryTonsAtProspector = new HashMap<>(currentInventory);
		// Align cargo baseline with this rock so the next Cargo.json poll cannot miss the first fragment
		// (null/empty lastCargo + already-increased inventory used to absorb the gain without a row).
		lastCargoTonsForLogging = new HashMap<>(currentInventory);
		Map<String, Double> nextPercent = new HashMap<>();
		for (MaterialProportion mp : event.getMaterials()) {
			if (mp != null && mp.getName() != null) {
				String name = toUiName(mp.getName());
				if (name != null && !name.isBlank()) {
					nextPercent.put(name, mp.getProportion());
				}
			}
		}
		lastPercentByMaterialAtProspector = nextPercent;
		spreadsheetScatterPanel.onProspectorLimpetFromMining(nextPercent);
		syncScatterActiveRun();

		String motherlode = event.getMotherlodeMaterial();
		String content = event.getContent();

		List<Row> rows = new ArrayList<>();

		String commander = OverlayPreferences.getMiningLogCommanderName();
		for (MaterialProportion mp : event.getMaterials()) {
			if (mp == null || mp.getName() == null) {
				continue;
			}

			String rawName = mp.getName();
			String shownName = toUiName(rawName);

			int avg = lookupAvgSell(rawName, shownName);
			double tons = ProspectorLogRegression.estimateTonsForMaterialPercent(
				mp.getProportion(), commander, lastGoodSpreadsheetRows);
			double value = tons * avg;

			rows.add(new Row(shownName, mp.getProportion(), avg, tons, value));
		}

		if (motherlode != null && !motherlode.isBlank()) {
			String shownName = toUiName(motherlode);

			int avg = lookupAvgSell(motherlode, shownName);
			double tons = ProspectorLogRegression.estimateCoreTons(commander, lastGoodSpreadsheetRows);
			double value = tons * avg;

			rows.add(new Row(shownName + " (Core)", Double.NaN, avg, tons, value, true));
		}

		if (rows.isEmpty()) {
			System.err.println(
					"[EDO][Debug][MiningTabPanel] updateFromProspector built 0 rows. ts=" + event.getTimestamp()
							+ " materials=" + (event.getMaterials() != null ? event.getMaterials().size() : -1)
							+ " motherlode=" + motherlode
							+ " content=" + content
			);
		}

		ProspectorAnnouncement ann = buildProspectorAnnouncement(event, rows);

		rows.sort(Comparator
				.comparing(Row::isCore).reversed()
				.thenComparing(Comparator.comparing((Row r) -> isHighlightedProspectorRow(r)).reversed())
				.thenComparing(Comparator.comparingDouble(Row::getEstimatedValue).reversed())
				.thenComparing(Row::getName, String.CASE_INSENSITIVE_ORDER));

		model.setRows(rows);
		prospectorScan.startProspectorScan(prospectorLayer);

			if (!OverlayPreferences.isSpeechEnabled()) {
				return;
			}
			
			if (ann != null) {
				lastProspectorAnnouncementSig = ann.sig;

				if (ann.single) {
					tts.speakf("Prospector found {material} at {n} percent.", ann.material, ann.pct);
				} else {
					tts.speakf("Prospector found {list} from {min} to {max} percent.", ann.listText, ann.minPct, ann.maxPct);
				}
			}



				String hdr = "Mining (" + (content == null ? "" : content) + ")";
		if (motherlode != null && !motherlode.isBlank()) {
			hdr += " - Motherlode: " + motherlode;
		}
		headerLabel.setText(hdr);

	}

	/** For tests: number of rows in the prospector (latest scan) table. */
	public int getProspectorTableRowCount() {
		return model.getRowCount();
	}

	/** For tests: displayed cell value at (row, column) in the prospector table; columns 0=Material, 1=Percent, 2=Avg Cr/t, 3=Est. Tons, 4=Est. Value. */
	public String getProspectorTableValueAt(int row, int column) {
		if (row < 0 || row >= model.getRowCount() || column < 0 || column >= model.getColumnCount()) {
			return null;
		}
		Object v = model.getValueAt(row, column);
		return v != null ? v.toString() : null;
	}

	/** For prospector threading tests: last call to updateFromProspector happened on EDT. */
	public boolean wasLastProspectorUpdateOnEdtForTests() {
		return lastProspectorUpdateWasOnEdt;
	}

	/** For backend-failure tests: current spreadsheet table row count. */
	public int getProspectorSpreadsheetRowCountForTests() {
		return spreadsheetModel != null ? spreadsheetModel.getRowCount() : 0;
	}

	/**
	 * Use the INARA CSV display name if present; fall back to a friendly formatting of the journal token.
	 * This is what fixes "Crystals" (and lots of other tokens) showing inconsistently.
	 */    private String toUiName(String s) {
		 if (s == null || s.isBlank()) {
			 return "";
		 }

		 String norm = GalacticAveragePrices.normalizeMaterialKey(s);
		 // Handle journal/localized variants that differ only by pluralization.
		 // Examples seen in the wild:
		 //  - "LowTemperatureDiamonds"
		 //  - "Low Temperature Diamond"
		 if ("lowtemperaturediamonds".equals(norm) || "lowtemperaturediamond".equals(norm)) {
			 return "Low Temperature Diamonds";
		 }
		 if ("opal".equals(norm)) {
			 return "Void Opal";
		 }

		 String fromCsv = prices.getDisplayName(s);
		 if (fromCsv != null && !fromCsv.isBlank()) {
			 return fromCsv;
		 }

		 // Handle snake_case / kebab-case first
		 String out = s.replace('_', ' ').replace('-', ' ').trim();

		 // Friendly fallback (handles $..._Name; too)
		 if (out.startsWith("$")) {
			 out = out.substring(1);
		 }
		 out = out.replace("_name", "");
		 out = out.replace("_Name", "");
		 out = out.replace(";", "");
		 out = out.trim();

		 // Insert spaces for tokens like "LowTemperatureDiamonds"
		 out = out.replaceAll("(?<=[a-z])(?=[A-Z])", " ");
		 out = out.replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", " ");
		 out = out.trim();

		 if (out.isEmpty()) {
			 return "";
		 }

		 // Title-case
		 String[] parts = out.split("\\s+");
		 StringBuilder sb = new StringBuilder();
		 for (String p : parts) {
			 if (p.isBlank()) {
				 continue;
			 }
			 if (sb.length() > 0) {
				 sb.append(' ');
			 }
			 if (p.length() == 1) {
				 sb.append(p.toUpperCase());
			 } else {
				 sb.append(Character.toUpperCase(p.charAt(0)));
				 sb.append(p.substring(1).toLowerCase());
			 }
		 }
		 return sb.toString();
	 }
	 private static String joinWithAnd(List<String> items) {
		    if (items == null || items.isEmpty()) {
		        return "";
		    }
		    if (items.size() == 1) {
		        return items.get(0);
		    }
		    if (items.size() == 2) {
		        return items.get(0) + " and " + items.get(1);
		    }

		    StringBuilder sb = new StringBuilder();
		    for (int i = 0; i < items.size(); i++) {
		        if (i > 0) {
		            if (i == items.size() - 1) {
		                sb.append(", and ");
		            } else {
		                sb.append(", ");
		            }
		        }
		        sb.append(items.get(i));
		    }
		    return sb.toString();
		}


	 /**
	  * Price lookup should use the journal name (because GalacticAveragePrices normalizes keys already),
	  * with one alias to cover the "opal" token.
	  */

	 private static String splitCamelCase(String s) {
		 if (s == null || s.isBlank()) {
			 return "";
		 }
		 // Insert spaces for tokens like "LowTemperatureDiamonds".
		 String out = s.replaceAll("(?<=[a-z])(?=[A-Z])", " ");
		 out = out.replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", " ");
		 return out.trim();
	 }    

	 private int lookupAvgSell(String journalName, String uiName) {
		return matcher.lookupAvgSell(journalName, uiName);
	}


	 private static final class Row {
		 private final String name;
		 private final double proportionPercent;
		 private final int avgSell;
		 private final double expectedTons;
		 private final double estimatedValue;
		 private final boolean isCore;
		 private final boolean isSummary;

		 Row(String name, int avgSell, double expectedTons, double estimatedValue) {
			 this(name, Double.NaN, avgSell, expectedTons, estimatedValue, false, false);
		 }

		 Row(String name, int avgSell, double expectedTons, double estimatedValue, boolean isCore) {
			 this(name, Double.NaN, avgSell, expectedTons, estimatedValue, isCore, false);
		 }

		 Row(String name, double proportionPercent, int avgSell, double expectedTons, double estimatedValue) {
			 this(name, proportionPercent, avgSell, expectedTons, estimatedValue, false, false);
		 }

		 Row(String name, double proportionPercent, int avgSell, double expectedTons, double estimatedValue, boolean isCore) {
			 this(name, proportionPercent, avgSell, expectedTons, estimatedValue, isCore, false);
		 }

		 Row(String name, double proportionPercent, int avgSell, double expectedTons, double estimatedValue, boolean isCore, boolean isSummary) {
			 this.name = name;
			 this.proportionPercent = proportionPercent;
			 this.avgSell = avgSell;
			 this.expectedTons = expectedTons;
			 this.estimatedValue = estimatedValue;
			 this.isCore = isCore;
			 this.isSummary = isSummary;
		 }

String getName() {
			 return name;
		 }

		 double getProportionPercent() {
			 return proportionPercent;
		 }

		 int getAvgSell() {
			 return avgSell;
		 }

		 double getExpectedTons() {
			 return expectedTons;
		 }

		 double getEstimatedValue() {
			 return estimatedValue;
		 }

		 boolean isCore() {
			 return isCore;
		 }

		 boolean isSummary() {
			 return isSummary;
		 }
	 }

	 private static final class MiningTableModel extends AbstractTableModel {

		 private final String[] cols;

		 private final NumberFormat intFmt = NumberFormat.getIntegerInstance(Locale.US);
		 private final NumberFormat tonsFmt = NumberFormat.getNumberInstance(Locale.US);
		 private final NumberFormat pctFmt = NumberFormat.getNumberInstance(Locale.US);

		 private List<Row> rows = List.of();

		 MiningTableModel(String tonsLabel) {
			 String tl = (tonsLabel == null || tonsLabel.isBlank()) ? "Est. Tons" : tonsLabel;

			 cols = new String[] {
					 "Material",
					 "Percent",
					 "Avg Cr/t",
					 tl,
					 "Est. Value"
			 };

			 boolean estimated = tl.toLowerCase(Locale.US).contains("est");
			 tonsFmt.setMaximumFractionDigits(estimated ? 1 : 0);
			 tonsFmt.setMinimumFractionDigits(0);
		 
			 pctFmt.setMaximumFractionDigits(1);
			 pctFmt.setMinimumFractionDigits(0);
		 }

		 void setRows(List<Row> newRows) {
			 rows = (newRows == null) ? List.of() : List.copyOf(newRows);
			 fireTableDataChanged();
		 }

		 Row getRow(int modelRow) {
			 if (modelRow < 0 || modelRow >= rows.size()) {
				 return null;
			 }
			 return rows.get(modelRow);
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
		 public String getColumnName(int column) {
			 return cols[column];
		 }

		 @Override
		 public Object getValueAt(int rowIndex, int columnIndex) {
			 Row r = rows.get(rowIndex);
			 if (r.isSummary()) {
				 switch (columnIndex) {
				 case 0:
					 return r.getName();
				 case 1:
					 return "";
				 case 2:
					 return "";
				 case 3:
					 return tonsFmt.format(r.getExpectedTons());
				 case 4:
					 return intFmt.format(Math.round(r.getEstimatedValue()));
				 default:
					 return "";
				 }
			}

			 switch (columnIndex) {
			 case 0:
				 return r.getName();
			 case 1:
				 if (Double.isNaN(r.getProportionPercent()) || r.getProportionPercent() <= 0.0) {
					 return "";
				 }
				 return pctFmt.format(r.getProportionPercent());
			 case 2:
				 return r.getAvgSell() <= 0 ? "0" : intFmt.format(r.getAvgSell());
			 case 3:
				 return tonsFmt.format(r.getExpectedTons());
			 case 4:
				 return r.getAvgSell() <= 0 ? "0" : intFmt.format(Math.round(r.getEstimatedValue()));
			 default:
				 return "";
			 }
		 }

		 @Override
		 public Class<?> getColumnClass(int columnIndex) {
			 return String.class;
		 }
	 }



	/** Match Route/System tabs: subtle scrollbar UI on the prospector log table only. */
	private static void installSubtleScrollBarsOnProspectorLog(JScrollPane sp) {
		if (sp == null) {
			return;
		}
		JScrollBar vsb = sp.getVerticalScrollBar();
		if (vsb != null) {
			vsb.setOpaque(false);
			vsb.setBackground(EdoUi.Internal.TRANSPARENT);
			vsb.setUI(new SubtleScrollBarUI());
			vsb.setPreferredSize(new Dimension(12, Integer.MAX_VALUE));
			vsb.setUnitIncrement(16);
		}
		JScrollBar hsb = sp.getHorizontalScrollBar();
		if (hsb != null) {
			hsb.setOpaque(false);
			hsb.setBackground(EdoUi.Internal.TRANSPARENT);
			hsb.setUI(new SubtleScrollBarUI());
			hsb.setPreferredSize(new Dimension(Integer.MAX_VALUE, 12));
			hsb.setUnitIncrement(16);
		}
	}

	/**
	 * After the model refreshes (newest runs at the top), keep the viewport at the top instead of
	 * leaving the thumb at a stale position.
	 */
	private void scrollProspectorLogTableToTop() {
		if (spreadsheetScroller == null || spreadsheetTable == null) {
			return;
		}
		SwingUtilities.invokeLater(() -> {
			JViewport vp = spreadsheetScroller.getViewport();
			if (vp != null) {
				vp.setViewPosition(new Point(0, 0));
			}
			if (spreadsheetTable.getRowCount() > 0) {
				try {
					spreadsheetTable.scrollRectToVisible(spreadsheetTable.getCellRect(0, 0, true));
				} catch (Exception ignored) {
				}
			}
		});
	}

	 private static void configureOverlayScroller(JScrollPane sp) {
		 if (sp == null) {
			 return;
		 }

		 sp.setOpaque(false);
		 sp.setBackground(EdoUi.Internal.TRANSPARENT);
		 sp.setBorder(BorderFactory.createEmptyBorder());
		 sp.setViewportBorder(BorderFactory.createEmptyBorder());

		 if (sp.getViewport() != null) {
			 sp.getViewport().setOpaque(false);
			 sp.getViewport().setBackground(EdoUi.Internal.TRANSPARENT);
		 }

		 if (sp.getColumnHeader() != null) {
			 sp.getColumnHeader().setOpaque(false);
			 sp.getColumnHeader().setBackground(EdoUi.Internal.TRANSPARENT);
			 sp.getColumnHeader().setBorder(BorderFactory.createEmptyBorder());
		 }

		 if (sp.getHorizontalScrollBar() != null) {
			 sp.getHorizontalScrollBar().setOpaque(false);
			 sp.getHorizontalScrollBar().setBackground(EdoUi.Internal.TRANSPARENT);
		 }

		 if (sp.getVerticalScrollBar() != null) {
			 sp.getVerticalScrollBar().setOpaque(false);
			 sp.getVerticalScrollBar().setBackground(EdoUi.Internal.TRANSPARENT);
		 }

		 JPanel corner = new JPanel();
		 corner.setOpaque(false);
		 corner.setBackground(EdoUi.Internal.TRANSPARENT);

		 sp.setCorner(JScrollPane.UPPER_RIGHT_CORNER, corner);
		 sp.setCorner(JScrollPane.LOWER_RIGHT_CORNER, corner);
		 sp.setCorner(JScrollPane.UPPER_LEFT_CORNER, corner);
		 sp.setCorner(JScrollPane.LOWER_LEFT_CORNER, corner);
	 }

	 	private static final class TableScanState {
		private final JTable table;
		private final Map<Integer, Float> revealAlphaByModelRow = new HashMap<>();
		private final Map<Integer, Float> flareAlphaByModelRow = new HashMap<>();

		private int scanY = Integer.MIN_VALUE;
		private Timer scanTimer;

		private int lastRowCount = 0;
		private int scanEndY = 0;

		private TableScanState(JTable table) {
			this.table = table;
		}

		private float getRevealAlpha(int modelRow) {
			return revealAlphaByModelRow.getOrDefault(modelRow, 1.0f);
		}

		private float getFlareAlpha(int modelRow) {
			return flareAlphaByModelRow.getOrDefault(modelRow, 0.0f);
		}

		private boolean isScanning() {
			return scanTimer != null && scanTimer.isRunning();
		}

		private int getScanY() {
			return scanY;
		}

		private void startProspectorScan(JLayer<JTable> layer) {
			initRevealForAllRows(0.0f);
			flareAlphaByModelRow.clear();
			startScan(layer, true, null);
		}

		private void startInventoryScan(JLayer<JTable> layer, Set<Integer> flareModelRows) {
			initRevealForAllRows(1.0f);
			flareAlphaByModelRow.clear();
			startScan(layer, false, flareModelRows);
		}

		private void initRevealForAllRows(float alpha) {
			revealAlphaByModelRow.clear();
			int rc = table.getRowCount();
			for (int viewRow = 0; viewRow < rc; viewRow++) {
				int modelRow = viewRow;
				if (table.getRowSorter() != null) {
					modelRow = table.convertRowIndexToModel(viewRow);
				}
				revealAlphaByModelRow.put(modelRow, alpha);
			}
		}

		private void startScan(JLayer<JTable> layer, boolean revealOnCross, Set<Integer> flareOnlyModelRows) {
			if (scanTimer != null && scanTimer.isRunning()) {
				scanTimer.stop();
			}

			scanY = 0;

			int currentRowCount = table.getRowCount();
			int maxRows = Math.max(lastRowCount, currentRowCount);
			int rowHeight = table.getRowHeight();
			scanEndY = maxRows * rowHeight;
			lastRowCount = currentRowCount;

			final Set<Integer> flaredAlready = new HashSet<>();

			scanTimer = new Timer(16, e -> {
				scanY += 10;

				int y = 0;
				for (int viewRow = 0; viewRow < table.getRowCount(); viewRow++) {
					int h = table.getRowHeight(viewRow);
					int mid = y + (h / 2);

					if (scanY >= mid) {
						int modelRow = viewRow;
						if (table.getRowSorter() != null) {
							modelRow = table.convertRowIndexToModel(viewRow);
						}

						if (revealOnCross) {
							Float a = revealAlphaByModelRow.get(modelRow);
							if (a != null && a.floatValue() < 1.0f) {
								revealAlphaByModelRow.put(modelRow, 1.0f);
								triggerFlare(modelRow);
							}
						} else {
							if (flareOnlyModelRows != null && flareOnlyModelRows.contains(modelRow) && !flaredAlready.contains(modelRow)) {
								flaredAlready.add(modelRow);
								triggerFlare(modelRow);
							}
						}
					}

					y += h;
				}

				if (layer != null) {
					layer.repaint();
				}
				table.repaint();

				if (scanY > scanEndY + 10) {
					((Timer)e.getSource()).stop();
					return;
				}
			});

			scanTimer.start();
		}

		private void triggerFlare(int modelRow) {
			flareAlphaByModelRow.put(modelRow, 1.0f);

			Timer decay = new Timer(16, null);
			decay.addActionListener(ev -> {
				float v = flareAlphaByModelRow.getOrDefault(modelRow, 0.0f);
				v -= 0.08f;
				if (v <= 0.0f) {
					flareAlphaByModelRow.remove(modelRow);
					((Timer)ev.getSource()).stop();
				} else {
					flareAlphaByModelRow.put(modelRow, v);
				}
				table.repaint();
			});

			decay.start();
		}
	}

	private static final class ScanLayerUi extends LayerUI<JTable> {
		private static final long serialVersionUID = 1L;
		private final TableScanState scan;

		private ScanLayerUi(TableScanState scan) {
			this.scan = scan;
		}

		@Override
		public void paint(Graphics g, JComponent c) {
			super.paint(g, c);

			if (!scan.isScanning()) {
				return;
			}

			Graphics2D g2 = (Graphics2D)g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				int w = c.getWidth();
				int y = scan.getScanY();

				// Soft glow band
				g2.setColor(EdoUi.Internal.MAIN_TEXT_ALPHA_40);
				g2.fillRect(0, y - 6, w, 12);

				// Bright core scan line
				g2.setColor(EdoUi.Internal.MAIN_TEXT_ALPHA_180);
				g2.fillRect(0, y - 1, w, 3);

			} finally {
				g2.dispose();
			}
		}
	}

	/** Prospector log table header: one label per column (display order differs from sheet column order). */
	private static final class ProspectorLogTableHeader extends JTableHeader {
		private static final long serialVersionUID = 1L;

		ProspectorLogTableHeader(TableColumnModel cm) {
			super(cm);
			setOpaque(false);
			setBackground(EdoUi.Internal.TRANSPARENT);
		}

		@Override
		protected void paintComponent(Graphics g) {
			TableColumnModel cm = getColumnModel();
			int n = cm.getColumnCount();
			if (n <= 0) {
				return;
			}
			javax.swing.JTable tbl = getTable();
			TableCellRenderer renderer = getDefaultRenderer();
			if (renderer == null || tbl == null) {
				return;
			}
			boolean ltr = getComponentOrientation().isLeftToRight();
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
			for (int i = 0; i < n; i++) {
				int col = ltr ? i : (n - 1 - i);
				TableColumn tc = cm.getColumn(col);
				TableCellRenderer colRenderer = tc.getHeaderRenderer();
				if (colRenderer == null) {
					colRenderer = renderer;
				}
				java.awt.Rectangle r = getHeaderRect(col);
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
				g2.fillRect(r.x, r.y, r.width, r.height);
				g2.setComposite(AlphaComposite.SrcOver);
				Component cell = colRenderer.getTableCellRendererComponent(tbl, tc.getHeaderValue(), false, false, -1, col);
				cell.setBounds(0, 0, r.width, r.height);
				Graphics2D cg = (Graphics2D) g2.create(r.x, r.y, r.width, r.height);
				cell.paint(cg);
				cg.dispose();
			}
			g2.setColor(EdoUi.ED_ORANGE_TRANS);
			g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
			g2.dispose();
		}
	}

	 private static final class HeaderRenderer extends DefaultTableCellRenderer {
		 private static final long serialVersionUID = 1L;

		 @Override
		 public Component getTableCellRendererComponent(JTable table,
				 Object value,
				 boolean isSelected,
				 boolean hasFocus,
				 int row,
				 int column) {
			 JLabel label = (JLabel)super.getTableCellRendererComponent(table,
					 value,
					 false,
					 false,
					 row,
					 column);
			 boolean transparent = OverlayPreferences.overlayChromeRequestsTransparency();
			 label.setOpaque(!transparent);
			 label.setBackground(transparent ? EdoUi.Internal.TRANSPARENT : EdoUi.User.BACKGROUND);
			 label.setForeground(EdoUi.Internal.tableHeaderForeground());
			 label.setFont(label.getFont().deriveFont(Font.BOLD));
			 label.setHorizontalAlignment(column <= 1 ? SwingConstants.LEFT : SwingConstants.RIGHT);
			 label.setBorder(transparent
					 ? new EmptyBorder(2, 4, 0, 4)  // no MatteBorder when transparent so no opaque paint
					 : new CompoundBorder(
							 new MatteBorder(2, 0, 0, 0, EdoUi.Internal.tableHeaderTopBorder()),
							 new EmptyBorder(0, 4, 0, 4)));
			 return label;
		 }

		 @Override
		 protected void paintComponent(Graphics g) {
			 boolean transparent = OverlayPreferences.overlayChromeRequestsTransparency();
			 setOpaque(!transparent);
			 setBackground(transparent ? EdoUi.Internal.TRANSPARENT : EdoUi.User.BACKGROUND);
			 Graphics2D g2 = (Graphics2D) g.create();
			 g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					 RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
			 if (!transparent) {
				 g2.setColor(EdoUi.User.BACKGROUND);
				 g2.fillRect(0, 0, getWidth(), getHeight());
				 super.paintComponent(g2);
			 } else {
				 // Do not call super: avoid any LAF background. Paint only text and line.
				 String text = getText();
				 if (text != null && !text.isEmpty()) {
					 g2.setFont(getFont());
					 g2.setColor(getForeground());
					 FontMetrics fm = g2.getFontMetrics();
					 int top = 2;
					 int pad = 4;
					 int y = top + (getHeight() - top) / 2 + fm.getAscent() / 2 - fm.getDescent() / 2;
					 int x;
					 if (getHorizontalAlignment() == SwingConstants.RIGHT) {
						 x = getWidth() - pad - fm.stringWidth(text);
					 } else if (getHorizontalAlignment() == SwingConstants.CENTER) {
						 x = (getWidth() - fm.stringWidth(text)) / 2;
					 } else {
						 x = pad;
					 }
					 g2.drawString(text, x, y);
				 }
			 }
			 g2.setColor(EdoUi.ED_ORANGE_TRANS);
			 int y = getHeight() - 1;
			 g2.drawLine(0, y, getWidth(), y);

			 g2.dispose();
		 }
	 }

	/** Wrapper for scatter panel with View mode (All / By run / By commander) and run/commander selector. */
	private final class ProspectorLogScatterWrapperPanel extends JPanel {
		private static final String MODE_ALL = "All (color by commander)";
		private static final String MODE_BY_RUN = "By run";
		private static final String MODE_BY_COMMANDER = "By commander";
		private final ProspectorLogScatterPanel scatterPanel;
		private final JComboBox<String> modeCombo;
		private final JComboBox<String> secondaryCombo;
		private final JButton testGatherButton;
		private List<ProspectorLogRow> currentRows = new ArrayList<>();

		ProspectorLogScatterWrapperPanel(ProspectorLogScatterPanel scatterPanel) {
			super(new BorderLayout());
			this.scatterPanel = scatterPanel;
			modeCombo = new JComboBox<>(new String[] { MODE_ALL, MODE_BY_RUN, MODE_BY_COMMANDER });
			modeCombo.setOpaque(false);
			modeCombo.setBackground(EdoUi.Internal.TRANSPARENT);
			modeCombo.setForeground(EdoUi.User.MAIN_TEXT);
			modeCombo.setRenderer((ListCellRenderer<? super String>) new DefaultListCellRenderer() {
				@Override
				public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
					Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
					c.setForeground(EdoUi.User.MAIN_TEXT);
					c.setBackground(EdoUi.Internal.TRANSPARENT);
					return c;
				}
			});

			secondaryCombo = new JComboBox<>();
			secondaryCombo.setOpaque(false);
			secondaryCombo.setBackground(EdoUi.Internal.TRANSPARENT);
			secondaryCombo.setForeground(EdoUi.User.MAIN_TEXT);
			secondaryCombo.setRenderer((ListCellRenderer<? super String>) new DefaultListCellRenderer() {
				@Override
				public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
					Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
					c.setForeground(EdoUi.User.MAIN_TEXT);
					c.setBackground(EdoUi.Internal.TRANSPARENT);
					return c;
				}
			});
			secondaryCombo.setVisible(false);
			modeCombo.addActionListener(e -> onModeChanged());
			secondaryCombo.addActionListener(e -> onSecondaryChanged());
			JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
			top.setOpaque(false);
			top.setBackground(EdoUi.Internal.TRANSPARENT);
			JLabel viewLabel = new JLabel("View:");
			viewLabel.setForeground(EdoUi.User.MAIN_TEXT);
			top.add(viewLabel);
			top.add(modeCombo);
			top.add(secondaryCombo);
			testGatherButton = new JButton("Test gather");
			testGatherButton.setOpaque(false);
			testGatherButton.setForeground(EdoUi.User.MAIN_TEXT);
			testGatherButton.setBackground(EdoUi.Internal.TRANSPARENT);
			testGatherButton.addActionListener(e -> scatterPanel.triggerTestGatherAnimation());
			top.add(testGatherButton);
			add(top, BorderLayout.NORTH);
			add(scatterPanel, BorderLayout.CENTER);
		}

		void applyScatterControlOpacity(boolean transparent) {
			if (testGatherButton != null) {
				testGatherButton.setOpaque(!transparent);
				testGatherButton.setBackground(transparent ? EdoUi.Internal.TRANSPARENT : EdoUi.Internal.DARK_ALPHA_220);
				testGatherButton.setForeground(EdoUi.User.MAIN_TEXT);
			}
		}

		void setRows(List<ProspectorLogRow> rows) {
			if (rows == null) rows = List.of();
			currentRows = new ArrayList<>(rows);
			scatterPanel.setRows(rows);
			String mode = (String) modeCombo.getSelectedItem();
			modeCombo.setModel(new DefaultComboBoxModel<>(new String[] { MODE_ALL, MODE_BY_RUN, MODE_BY_COMMANDER }));
			modeCombo.setSelectedItem(mode != null ? mode : MODE_ALL);
			populateSecondaryComboAndUpdateFilter();
			updateScatterRunSummaryLines();
		}

		/** Populate run/commander combo from currentRows for the selected mode and refresh the scatter filter. */
		private void populateSecondaryComboAndUpdateFilter() {
			String mode = (String) modeCombo.getSelectedItem();
			secondaryCombo.removeAllItems();
			if (MODE_BY_RUN.equals(mode)) {
				// Distinct (commander, run) combinations.
				List<String> labels = currentRows.stream()
					.filter(r -> r != null)
					.map(r -> {
						int run = r.getRun();
						String commander = r.getCommanderName();
						if (commander == null || commander.isBlank()) {
							commander = "-";
						}
						return commander + " – Run " + run;
					})
					.distinct()
					.sorted(String.CASE_INSENSITIVE_ORDER)
					.toList();
				for (String label : labels) secondaryCombo.addItem(label);
				secondaryCombo.setVisible(true);
				if (!labels.isEmpty()) {
					secondaryCombo.setSelectedIndex(0);
					applySelectedRunLabel(labels.get(0));
				}
			} else if (MODE_BY_COMMANDER.equals(mode)) {
				List<String> commanders = currentRows.stream()
					.map(ProspectorLogRow::getCommanderName)
					.filter(c -> c != null && !c.isEmpty())
					.distinct().sorted().toList();
				for (String c : commanders) secondaryCombo.addItem(c);
				secondaryCombo.setVisible(true);
				if (!commanders.isEmpty()) {
					secondaryCombo.setSelectedIndex(0);
					scatterPanel.setSelectedCommander(commanders.get(0));
				}
			} else {
				secondaryCombo.setVisible(false);
			}
			onModeChanged();
		}

		private void updateScatterRunSummaryLines() {
			// Scatter's Run line(s) should reflect all active runs (same rule as the table),
			// regardless of current scatter filter or commander selection.
			List<RunSummary> summaries = ProspectorLogTableModel.getActiveRunSummaries(currentRows, matcher);
			scatterPanel.setRunSummaries(summaries);
		}

		private void onModeChanged() {
			String mode = (String) modeCombo.getSelectedItem();
			scatterPanel.setFilterMode(MODE_BY_RUN.equals(mode) ? ProspectorLogScatterPanel.FilterMode.BY_RUN
				: MODE_BY_COMMANDER.equals(mode) ? ProspectorLogScatterPanel.FilterMode.BY_COMMANDER
				: ProspectorLogScatterPanel.FilterMode.ALL);
			if (MODE_BY_RUN.equals(mode)) {
				secondaryCombo.setVisible(true);
				// When switching from All to By run, secondary combo is empty; populate from current rows
				if (secondaryCombo.getItemCount() == 0) {
					List<String> labels = currentRows.stream()
						.filter(r -> r != null)
						.map(r -> {
							int run = r.getRun();
							String commander = r.getCommanderName();
							if (commander == null || commander.isBlank()) {
								commander = "-";
							}
							return commander + " – Run " + run;
						})
						.distinct()
						.sorted(String.CASE_INSENSITIVE_ORDER)
						.toList();
					for (String label : labels) secondaryCombo.addItem(label);
					if (!labels.isEmpty()) secondaryCombo.setSelectedIndex(0);
				}
				Object sel = secondaryCombo.getSelectedItem();
				applySelectedRunLabel(sel);
			} else if (MODE_BY_COMMANDER.equals(mode)) {
				secondaryCombo.setVisible(true);
				if (secondaryCombo.getItemCount() == 0) {
					List<String> commanders = currentRows.stream()
						.map(ProspectorLogRow::getCommanderName)
						.filter(c -> c != null && !c.isEmpty())
						.distinct().sorted().toList();
					for (String c : commanders) secondaryCombo.addItem(c);
					if (!commanders.isEmpty()) secondaryCombo.setSelectedIndex(0);
				}
				Object sel = secondaryCombo.getSelectedItem();
				scatterPanel.setSelectedCommander(sel != null ? sel.toString() : "");
			} else {
				secondaryCombo.setVisible(false);
			}
			updateScatterRunSummaryLines();
		}

		private void onSecondaryChanged() {
			String mode = (String) modeCombo.getSelectedItem();
			if (MODE_BY_RUN.equals(mode)) {
				Object sel = secondaryCombo.getSelectedItem();
				applySelectedRunLabel(sel);
			} else if (MODE_BY_COMMANDER.equals(mode)) {
				Object sel = secondaryCombo.getSelectedItem();
				scatterPanel.setSelectedCommander(sel != null ? sel.toString() : "");
			}
			updateScatterRunSummaryLines();
		}

		private void applySelectedRunLabel(Object sel) {
			if (sel == null) {
				return;
			}
			String s = sel.toString();
			int sep = s.indexOf(" – Run ");
			if (sep <= 0) {
				return;
			}
			String commander = s.substring(0, sep).trim();
			String runPart = s.substring(sep + " – Run ".length()).trim();
			int run = 1;
			try {
				run = Integer.parseInt(runPart);
			} catch (NumberFormatException ignored) {
			}
			scatterPanel.setSelectedCommander(commander);
			scatterPanel.setSelectedRun(run);
		}
	}

	/** Scatter plot panel: X = Percentage (%), Y = Tons yield (t). Supports filter by run/commander and color by commander. */
		private static final class ProspectorLogScatterPanel extends JPanel {
		private static final class PointInfo {
			final int x;
			final int y;
			final ProspectorLogRow row;

			PointInfo(int x, int y, ProspectorLogRow row) {
				this.x = x;
				this.y = y;
				this.row = row;
			}
		}

		/** Screen-space trend segment + regression stats for hover tooltip. */
		private static final class TrendHoverState {
			final String commanderLabel;
			final Regression reg;
			final int sx1;
			final int sy1;
			final int sx2;
			final int sy2;
			final int anchorX;
			final int anchorY;

			TrendHoverState(String commanderLabel, Regression reg, int sx1, int sy1, int sx2, int sy2, int anchorX, int anchorY) {
				this.commanderLabel = commanderLabel != null ? commanderLabel : "";
				this.reg = reg;
				this.sx1 = sx1;
				this.sy1 = sy1;
				this.sx2 = sx2;
				this.sy2 = sy2;
				this.anchorX = anchorX;
				this.anchorY = anchorY;
			}
		}
		private static final int PAD_LEFT = 42;
		private static final int PAD_BOTTOM = 32;
		private static final int PAD_RIGHT = 16;
		private static final int PAD_TOP = 16;
		/** Cap run-summary header lines so padding does not consume the whole panel (merged multi-commander sheets). */
		private static final int MAX_SCATTER_RUN_SUMMARY_LINES = 16;
		private static final int SCATTER_MIN_PLOT_HEIGHT = 72;
		private static final double POINT_RADIUS = 3.0;
		private static final int TICK_LABELS = 5;
		private static final Color[] COMMANDER_PALETTE = {
			EdoUi.User.VALUABLE,
			Color.CYAN,
			Color.MAGENTA,
			Color.ORANGE,
			new Color(0x90, 0xEE, 0x90),
			new Color(0xFF, 0xB6, 0xC1),
			new Color(0x87, 0xCE, 0xEB),
			new Color(0xDD, 0xA0, 0xDD),
		};
		/** Cap gun platforms and palette slots in ALL mode (merged sheets can yield many distinct commander strings). */
		private static final int MAX_TOP_COMMANDERS_GUNS_PALETTE = 8;
		enum FilterMode { ALL, BY_RUN, BY_COMMANDER }
		private List<ProspectorLogRow> rows = new ArrayList<>();
		private FilterMode filterMode = FilterMode.ALL;
		private int selectedRun = 1;
		private String selectedCommander = "";
		private List<PointInfo> pointInfos = new ArrayList<>();
		private ProspectorLogRow hoverRow;
		/** When set, mouse is near a trend line (and not on a point). */
		private TrendHoverState hoverTrend;
		private List<RunSummary> runSummaries = new ArrayList<>();
		/**
		 * Current cargo-logging run for line-art rocks; 0 disables markers. Set from {@link MiningTabPanel#syncScatterActiveRun()}.
		 */
		private int sessionActiveRun;
		/** Normalized material keys from latest prospector; only these get line-art rocks (see {@link #setProspectorMaterialKeysForMarkers}). */
		private Set<String> prospectorMaterialKeysForMarkers = Set.of();
		private final javax.swing.Timer hoverPollTimer;
		private final javax.swing.Timer asteroidSpinTimer;
		/** Rotation angle for line-art asteroid markers on the most recent active asteroid */
		private double asteroidSpinAngle;

		private static final class PlotGeom {
			final int plotX;
			final int plotY;
			final int plotW;
			final int plotH;
			final int effectivePadTop;
			final double minPct;
			final double maxPct;
			final double minAct;
			final double maxAct;

			PlotGeom(int plotX, int plotY, int plotW, int plotH, int effectivePadTop,
					double minPct, double maxPct, double minAct, double maxAct) {
				this.plotX = plotX;
				this.plotY = plotY;
				this.plotW = plotW;
				this.plotH = plotH;
				this.effectivePadTop = effectivePadTop;
				this.minPct = minPct;
				this.maxPct = maxPct;
				this.minAct = minAct;
				this.maxAct = maxAct;
			}

			Point project(ProspectorLogRow r) {
				return projectValues(r.getPercent(), r.getDifference());
			}

			Point projectValues(double pct, double tons) {
				double nx = (maxPct > minPct) ? (pct - minPct) / (maxPct - minPct) : 0.5;
				double ny = (maxAct > minAct) ? 1.0 - (tons - minAct) / (maxAct - minAct) : 0.5;
				int x = plotX + (int) (nx * plotW);
				int y = plotY + (int) (ny * plotH);
				return new Point(x, y);
			}
		}

		private static final class OreParticle {
			double x;
			double y;
			double vx;
			double vy;
			int ageMs;
		}

		/** One queued gather (from prior leader snapshot → current); processed FIFO so nothing is dropped. */
		private static final class GatherAnimRequest {
			final Point from;
			final Point to;
			final Color color;
			final String skipKeyFrom;
			final String skipKeyTo;
			final String animCommander;
			/** Commander+material map key so two rocks never share a queue signature when skip keys are empty. */
			final String queueKey;

			GatherAnimRequest(Point from, Point to, Color color, String skipKeyFrom, String skipKeyTo, String animCommander, String queueKey) {
				this.from = new Point(from);
				this.to = new Point(to);
				this.color = color;
				this.skipKeyFrom = skipKeyFrom != null ? skipKeyFrom : "";
				this.skipKeyTo = skipKeyTo != null ? skipKeyTo : "";
				this.animCommander = normalizeCommanderKey(animCommander);
				this.queueKey = queueKey != null ? queueKey : "";
			}

			String signature() {
				String id = !queueKey.isEmpty() ? queueKey : animCommander;
				return id + "\t" + skipKeyFrom + "\t" + skipKeyTo;
			}
		}

		private final MiningScatterAsteroidModel scatterAsteroidModel = new MiningScatterAsteroidModel();
		private final ArrayDeque<GatherAnimRequest> gatherAnimQueue = new ArrayDeque<>();
		private javax.swing.Timer gatherAnimTimer;
		private float gatherAnimPhase;
		private boolean gatherAnimActive;
		/** After the rock reaches its end position: laser/animated rock off; particles run out. */
		private boolean gatherDebrisPhaseOnly;
		private Point gatherLaserFrom;
		private Point gatherMoveFrom;
		private Point gatherMoveTo;
		private Color gatherAsteroidColor;
		private double gatherPhaseSpin;
		private String gatherSkipRowKeyFrom;
		private String gatherSkipRowKeyTo;
		/** Same as {@link GatherAnimRequest#queueKey} for the running animation. */
		private String gatherQueueKey = "";
		/** Commander whose gather animation is running (for mid-flight retarget). */
		private String gatherAnimCommander = "";
		private final List<OreParticle> gatherParticles = new ArrayList<>();
		private final Random gatherRandom = new Random();
		/** Phase advance per timer tick (higher = faster overall). */
		private static final float GATHER_PHASE_DELTA = 1f / 36f;
		/** Gun platform X lerp per tick toward asteroid / home (~30% slower than earlier 0.22 / 0.18). */
		private static final double GUN_LERP_TO_ROCK = 0.22 * 0.7;
		private static final double GUN_LERP_TO_HOME = 0.18 * 0.7;
		/** End gather when gun is this close to home (avoids asymptotic crawl). */
		private static final double GUN_HOME_EPSILON = 0.45;
		/** Default laser emit height above plot bottom (scaled by {@link OverlayPreferences#getMiningAnimationGunSizePercent()}). */
		private static final int GUN_EMIT_UP_BASE = 27;

		private final Map<String, Double> gunPlatformCenterXByCommander = new HashMap<>();

		/**
		 * Cached animation scales — {@link java.util.prefs.Preferences} is too slow to read on every
		 * {@code gunPx}/{@code asteroidPx} call (dozens per frame). Refreshed once per paint and on gather ticks.
		 */
		private double animGunScaleCached = 1.0;
		private double animAsteroidScaleCached = 1.0;

		private void refreshAnimationScaleCache() {
			animGunScaleCached = OverlayPreferences.getMiningAnimationGunSizePercent() / 100.0;
			animAsteroidScaleCached = OverlayPreferences.getMiningAnimationAsteroidSizePercent() / 100.0;
		}

		private double gunDrawScale() {
			return animGunScaleCached;
		}

		private double asteroidDrawScale() {
			return animAsteroidScaleCached;
		}

		private int gunPx(double u) {
			return (int) Math.round(u * gunDrawScale());
		}

		private int asteroidPx(double u) {
			return (int) Math.round(u * asteroidDrawScale());
		}

		/** Laser fires from the bottom edge of the plot; X is ~30% across the plot width from the left. */
		private int gatherLaserOriginX(PlotGeom geom) {
			if (geom != null) {
				return geom.plotX + (int) (geom.plotW * 0.3);
			}
			int w = getWidth();
			int plotW = Math.max(0, w - PAD_LEFT - PAD_RIGHT);
			return PAD_LEFT + (int) (plotW * 0.3);
		}

		private int gunLaserEmitY(PlotGeom geom) {
			return geom.plotY + geom.plotH - gunPx(GUN_EMIT_UP_BASE);
		}

		/** Idle X center for gun {@code index} of {@code n} along the plot bottom (30% width when solo). */
		private static double gunHomeCenterX(PlotGeom geom, int index, int n) {
			if (n <= 1) {
				return geom.plotX + geom.plotW * 0.3;
			}
			double t = (index + 1.0) / (n + 1.0);
			return geom.plotX + t * geom.plotW;
		}

		private static String normalizeCommanderKey(String cmdr) {
			if (cmdr == null || cmdr.isBlank()) {
				return "-";
			}
			return cmdr.trim();
		}

		private List<String> allCommandersByRowCountDesc(List<ProspectorLogRow> toPlot) {
			Map<String, Long> counts = new HashMap<>();
			for (ProspectorLogRow r : toPlot) {
				counts.merge(normalizeCommanderKey(r.getCommanderName()), 1L, Long::sum);
			}
			return counts.entrySet().stream()
				.sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
					.thenComparing(Map.Entry::getKey))
				.map(Map.Entry::getKey)
				.toList();
		}

		private List<String> topCommandersForGunsAndPalette(List<ProspectorLogRow> toPlot) {
			List<String> all = allCommandersByRowCountDesc(toPlot);
			if (all.size() <= MAX_TOP_COMMANDERS_GUNS_PALETTE) {
				return all;
			}
			return all.subList(0, MAX_TOP_COMMANDERS_GUNS_PALETTE);
		}

		private static void minMaxPercent(List<ProspectorLogRow> rows, double[] outMinMax) {
			double minP = Double.POSITIVE_INFINITY;
			double maxP = Double.NEGATIVE_INFINITY;
			for (ProspectorLogRow r : rows) {
				double p = r.getPercent();
				if (p < minP) {
					minP = p;
				}
				if (p > maxP) {
					maxP = p;
				}
			}
			outMinMax[0] = minP;
			outMinMax[1] = maxP;
		}

		private List<String> platformCommandersForPlot(List<ProspectorLogRow> toPlot) {
			if (toPlot == null || toPlot.isEmpty()) {
				return List.of();
			}
			if (filterMode == FilterMode.ALL) {
				return topCommandersForGunsAndPalette(toPlot);
			}
			if (selectedCommander != null && !selectedCommander.isEmpty()) {
				return List.of(normalizeCommanderKey(selectedCommander));
			}
			return List.of();
		}

		private double homeXForCommander(PlotGeom geom, String cmdr, List<String> ordered) {
			String key = normalizeCommanderKey(cmdr);
			int idx = ordered.indexOf(key);
			if (idx < 0) {
				return gatherLaserOriginX(geom);
			}
			return gunHomeCenterX(geom, idx, ordered.size());
		}

		private boolean gatherGunWithinEpsilonOfHome(double epsilon) {
			if (gatherAnimCommander == null || gatherAnimCommander.isEmpty()) {
				return true;
			}
			PlotGeom g = computePlotGeom();
			if (g == null) {
				return true;
			}
			Double gx = gunPlatformCenterXByCommander.get(gatherAnimCommander);
			if (gx == null) {
				return true;
			}
			double home = homeXForCommander(g, gatherAnimCommander, platformCommandersForPlot(filteredRows()));
			return Math.abs(gx - home) < epsilon;
		}

		private void syncGunPlatformCenters(List<String> orderedCmdrs, PlotGeom geom) {
			gunPlatformCenterXByCommander.keySet().retainAll(new HashSet<>(orderedCmdrs));
			int n = orderedCmdrs.size();
			for (int i = 0; i < n; i++) {
				String c = orderedCmdrs.get(i);
				gunPlatformCenterXByCommander.putIfAbsent(c, gunHomeCenterX(geom, i, n));
			}
		}

		private void updateGatherLaserFromFromGun(PlotGeom geom, String cmdr) {
			if (gatherLaserFrom == null) {
				gatherLaserFrom = new Point(0, 0);
			}
			if (cmdr == null || cmdr.isBlank()) {
				gatherLaserFrom.x = (int) Math.round(gatherLaserOriginX(geom));
				gatherLaserFrom.y = gunLaserEmitY(geom);
				return;
			}
			String gk = normalizeCommanderKey(cmdr);
			double cx = gunPlatformCenterXByCommander.containsKey(gk)
				? gunPlatformCenterXByCommander.get(gk)
				: gatherLaserOriginX(geom);
			gatherLaserFrom.x = (int) Math.round(cx);
			gatherLaserFrom.y = gunLaserEmitY(geom);
		}

		/** Small retro treaded platform + turret; laser emits from barrel top (scaled above plot bottom). */
		private void drawMobileGunPlatform(Graphics2D g2, int centerX, int plotBottomY, Color accent) {
			int B = plotBottomY;
			int left = centerX - gunPx(12);
			Stroke strokeSave = g2.getStroke();
			float treadStroke = (float) Math.max(0.75, Math.min(2.0, gunDrawScale()));
			g2.setColor(new Color(38, 40, 44));
			g2.fillRoundRect(left - gunPx(2), B - gunPx(5), gunPx(28), gunPx(6), gunPx(3), gunPx(3));
			g2.setColor(new Color(22, 22, 26));
			g2.setStroke(new BasicStroke(treadStroke));
			for (int u = 0; u < 6; u++) {
				int gx = left + gunPx(u * 5);
				g2.drawLine(gx, B - gunPx(4), gx + gunPx(2), B - gunPx(2));
			}
			g2.setColor(new Color(58, 62, 68));
			g2.fillRect(left - gunPx(1), B - gunPx(14), gunPx(26), gunPx(9));
			g2.setColor(accent.darker());
			g2.drawRect(left - gunPx(1), B - gunPx(14), gunPx(26), gunPx(9));
			g2.setColor(new Color(48, 52, 58));
			g2.fillPolygon(
				new int[] { left + gunPx(2), left + gunPx(22), left + gunPx(20), left + gunPx(4) },
				new int[] { B - gunPx(14), B - gunPx(14), B - gunPx(19), B - gunPx(19) },
				4);
			int emitY = B - gunPx(GUN_EMIT_UP_BASE);
			int turretCx = centerX;
			int turretCy = emitY + gunPx(10);
			g2.setColor(accent);
			g2.fillOval(turretCx - gunPx(7), turretCy - gunPx(6), gunPx(14), gunPx(12));
			g2.setColor(accent.darker());
			g2.drawOval(turretCx - gunPx(7), turretCy - gunPx(6), gunPx(14), gunPx(12));
			g2.setColor(new Color(72, 74, 80));
			g2.fillRect(turretCx - gunPx(2), emitY, gunPx(4), gunPx(12));
			g2.setColor(new Color(90, 92, 98));
			g2.drawRect(turretCx - gunPx(2), emitY, gunPx(4), gunPx(12));
			g2.setStroke(strokeSave);
		}

		private void drawGunPlatforms(Graphics2D g2, PlotGeom geom, List<ProspectorLogRow> toPlot, Map<String, Color> commanderColor) {
			List<String> pcs = platformCommandersForPlot(toPlot);
			if (pcs.isEmpty()) {
				return;
			}
			syncGunPlatformCenters(pcs, geom);
			int plotBottom = geom.plotY + geom.plotH;
			for (int i = 0; i < pcs.size(); i++) {
				String cmdr = pcs.get(i);
				Color ac = commanderColor.getOrDefault(cmdr, EdoUi.User.VALUABLE);
				double cx = gunPlatformCenterXByCommander.getOrDefault(cmdr, gunHomeCenterX(geom, i, pcs.size()));
				drawMobileGunPlatform(g2, (int) Math.round(cx), plotBottom, ac);
			}
		}

		void triggerTestGatherAnimation() {
			gatherAnimQueue.clear();
			endGatherAnimation();
			PlotGeom geom = computePlotGeom();
			if (geom == null || getWidth() <= 0 || getHeight() <= 0) {
				return;
			}
			List<ProspectorLogRow> toPlot = filteredRows();
			if (toPlot.isEmpty()) {
				return;
			}
			ProspectorLogRow sample = toPlot.get(0);
			String cmdrName = sample.getCommanderName() != null ? sample.getCommanderName() : "";
			Color col = commanderColorFor(cmdrName, toPlot);
			int midX = geom.plotX + (int) (geom.plotW * 0.45);
			int dx = Math.max(18, (int) (geom.plotW * 0.06));
			int yHi = geom.plotY + (int) (geom.plotH * 0.30);
			int yLo = geom.plotY + (int) (geom.plotH * 0.64);

			// Two separate “materials” on the same commander: left rock then right rock, both fully collected.
			Point fromA = new Point(midX - dx, yHi);
			Point toA = new Point(midX - dx, yLo);
			Point fromB = new Point(midX + dx, yHi);
			Point toB = new Point(midX + dx, yLo);

			offerGatherAnimRequest(new GatherAnimRequest(fromA, toA, col, "__testFromA", "__testToA", cmdrName, cmdrName + "\t__testMatA"));
			offerGatherAnimRequest(new GatherAnimRequest(fromB, toB, col, "__testFromB", "__testToB", cmdrName, cmdrName + "\t__testMatB"));
			tryStartNextGatherFromQueue();
		}

		ProspectorLogScatterPanel() {
			// Normal Swing mouse events (non pass-through)
			addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
				@Override
				public void mouseMoved(java.awt.event.MouseEvent e) {
					handleMouseMoved(e.getX(), e.getY());
				}
			});
			// Global hover poller so hover works even in OS pass-through mode
			hoverPollTimer = new javax.swing.Timer(40, e -> pollGlobalMouse());
			hoverPollTimer.setRepeats(true);
			hoverPollTimer.start();
			asteroidSpinTimer = new javax.swing.Timer(45, e -> {
				if (!isVisible()) {
					return;
				}
				asteroidSpinAngle += 0.12;
				if (asteroidSpinAngle > Math.PI * 2) {
					asteroidSpinAngle -= Math.PI * 2;
				}
				repaint();
			});
			asteroidSpinTimer.setRepeats(true);
		}

		@Override
		public void addNotify() {
			super.addNotify();
			if (asteroidSpinTimer != null) {
				asteroidSpinTimer.start();
			}
		}

		@Override
		public void removeNotify() {
			if (asteroidSpinTimer != null) {
				asteroidSpinTimer.stop();
			}
			if (gatherAnimTimer != null) {
				gatherAnimTimer.stop();
			}
			super.removeNotify();
		}

		private static String rowKeyForAnim(ProspectorLogRow r) {
			return r.getRun() + "|" + Objects.toString(r.getAsteroidId(), "") + "|" + Objects.toString(r.getTimestamp(), "") + "|"
				+ Objects.toString(r.getMaterial(), "") + "|" + r.getPercent() + "|" + r.getDifference();
		}

		/** Squared distance <= radius^2 (integer px; avoids sqrt). */
		private static boolean screenWithinRadiusSq(int px, int py, int cx, int cy, int radiusSq) {
			int dx = px - cx;
			int dy = py - cy;
			return dx * dx + dy * dy <= radiusSq;
		}

		/** ~line-art asteroid footprint + rounding; keeps one rock visible during gather. */
		private static final int GATHER_SUPPRESS_RADIUS_BASE = 22;

		private int gatherSuppressRadiusSq() {
			int r = asteroidPx(GATHER_SUPPRESS_RADIUS_BASE);
			r = Math.max(4, r);
			return r * r;
		}

		/** Hide static line-art where the animated rock is drawn (avoids double asteroids). */
		private boolean gatherSuppressStaticAsteroidAtPlotPixel(int px, int py) {
			if (!gatherAnimActive) {
				return false;
			}
			int rsq = gatherSuppressRadiusSq();
			// End position: hide only during laser+move. During debris the animated rock is gone — show static
			// at the data point so the rock does not vanish until particles finish (smooth handoff).
			if (!gatherDebrisPhaseOnly && gatherMoveTo != null
				&& screenWithinRadiusSq(px, py, gatherMoveTo.x, gatherMoveTo.y, rsq)) {
				return true;
			}
			// Start position: hidden only during laser+move; may show again during debris.
			if (!gatherDebrisPhaseOnly && gatherMoveFrom != null
				&& screenWithinRadiusSq(px, py, gatherMoveFrom.x, gatherMoveFrom.y, rsq)) {
				return true;
			}
			return false;
		}

		/**
		 * Maps a leader snapshot to a row in the current plot list. Exact timestamp match often fails after cargo
		 * updates (new row appended with a new instant, or sheet refresh), so we fall back to run/asteroid/material/
		 * commander + same pct/tons (latest timestamp if duplicated).
		 */
		private static ProspectorLogRow findRowMatchingSnapshot(MiningLeaderSnapshot s, String cmdr, List<ProspectorLogRow> toPlot) {
			if (s == null || toPlot == null) {
				return null;
			}
			String c = cmdr != null ? cmdr : "";
			List<ProspectorLogRow> candidates = new ArrayList<>();
			for (ProspectorLogRow r : toPlot) {
				if (!Objects.equals(r.getCommanderName() != null ? r.getCommanderName() : "", c)) {
					continue;
				}
				if (r.getRun() != s.run()) {
					continue;
				}
				if (!Objects.equals(r.getAsteroidId() != null ? r.getAsteroidId() : "", s.asteroidId())) {
					continue;
				}
				if (!Objects.equals(r.getMaterial() != null ? r.getMaterial() : "", s.material())) {
					continue;
				}
				candidates.add(r);
			}
			for (ProspectorLogRow r : candidates) {
				if (!Objects.equals(r.getTimestamp(), s.instant())) {
					continue;
				}
				if (r.getPercent() != s.pct() || r.getDifference() != s.tons()) {
					continue;
				}
				return r;
			}
			ProspectorLogRow byAmounts = null;
			for (ProspectorLogRow r : candidates) {
				if (r.getPercent() != s.pct() || r.getDifference() != s.tons()) {
					continue;
				}
				if (byAmounts == null || (r.getTimestamp() != null && byAmounts.getTimestamp() != null
					&& r.getTimestamp().isAfter(byAmounts.getTimestamp()))) {
					byAmounts = r;
				}
			}
			return byAmounts;
		}

		private PlotGeom computePlotGeom() {
			List<ProspectorLogRow> toPlot = filteredRows();
			int w = getWidth();
			int h = getHeight();
			if (toPlot.isEmpty() || w <= PAD_LEFT + PAD_RIGHT) {
				return null;
			}
			int effectivePadTop = PAD_TOP;
			if (!runSummaries.isEmpty()) {
				Font runLineFont = getFont().deriveFont(Font.BOLD, getFont().getSize2D() + 2f);
				FontMetrics sumFm = getFontMetrics(runLineFont);
				int lineHeight = sumFm.getHeight();
				int runLineCount = 0;
				for (RunSummary summary : runSummaries) {
					if (summary != null && summary.formatSummary() != null && !summary.formatSummary().isEmpty()) {
						runLineCount++;
					}
				}
				int cappedLines = Math.min(runLineCount, MAX_SCATTER_RUN_SUMMARY_LINES);
				int runLineBlockHeight = 4 + cappedLines * lineHeight + 6;
				effectivePadTop = Math.max(PAD_TOP, runLineBlockHeight);
				int maxPadTop = h - PAD_BOTTOM - SCATTER_MIN_PLOT_HEIGHT;
				if (maxPadTop >= PAD_TOP) {
					effectivePadTop = Math.min(effectivePadTop, maxPadTop);
				}
			}
			if (h <= effectivePadTop + PAD_BOTTOM) {
				return null;
			}
			double minPct = Double.MAX_VALUE;
			double maxPct = -Double.MAX_VALUE;
			double minAct = Double.MAX_VALUE;
			double maxAct = -Double.MAX_VALUE;
			for (ProspectorLogRow r : toPlot) {
				double p = r.getPercent();
				double a = r.getDifference();
				if (p < minPct) {
					minPct = p;
				}
				if (p > maxPct) {
					maxPct = p;
				}
				if (a < minAct) {
					minAct = a;
				}
				if (a > maxAct) {
					maxAct = a;
				}
			}
			if (minPct >= maxPct) {
				maxPct = minPct + 1.0;
			}
			if (minAct >= maxAct) {
				maxAct = minAct + 1.0;
			}
			int plotW = w - PAD_LEFT - PAD_RIGHT;
			int plotH = h - effectivePadTop - PAD_BOTTOM;
			int plotX = PAD_LEFT;
			int plotY = effectivePadTop;
			return new PlotGeom(plotX, plotY, plotW, plotH, effectivePadTop, minPct, maxPct, minAct, maxAct);
		}

		private Color commanderColorFor(String cmdr, List<ProspectorLogRow> toPlot) {
			if (filterMode != FilterMode.ALL) {
				return EdoUi.User.VALUABLE;
			}
			List<String> top = topCommandersForGunsAndPalette(toPlot);
			Map<String, Color> commanderColor = new HashMap<>();
			for (int i = 0; i < top.size(); i++) {
				commanderColor.put(top.get(i), COMMANDER_PALETTE[i % COMMANDER_PALETTE.length]);
			}
			return commanderColor.getOrDefault(normalizeCommanderKey(cmdr), EdoUi.User.VALUABLE);
		}

		private static String commanderFromRockSnapshotKey(String rockKey) {
			if (rockKey == null) {
				return "";
			}
			int i = rockKey.indexOf('\t');
			return i < 0 ? rockKey : rockKey.substring(0, i);
		}

		/** One queued gather per commander+material rock; laser always tied to that rock's row transition. */
		private void enqueueGatherTransitionsForRockSnapshots(
				Map<String, MiningLeaderSnapshot> previousLeaders,
				Map<String, MiningLeaderSnapshot> nextLeaders,
				List<ProspectorLogRow> toPlot,
				PlotGeom geom) {
			for (Map.Entry<String, MiningLeaderSnapshot> e : nextLeaders.entrySet()) {
				String rockKey = e.getKey();
				String cmdr = commanderFromRockSnapshotKey(rockKey);
				MiningLeaderSnapshot now = e.getValue();
				MiningLeaderSnapshot prev = previousLeaders.get(rockKey);
				if (prev == null || !now.sameAsteroid(prev)) {
					continue;
				}
				if (now.instant() == null || prev.instant() == null || !now.instant().isAfter(prev.instant())) {
					continue;
				}
				if (now.pct() == prev.pct() && now.tons() == prev.tons()) {
					continue;
				}
				if (now.tons() + 1e-6 < prev.tons()) {
					continue;
				}
				boolean monotonicTonGain = now.tons() > prev.tons() + 1e-6;
				// Prospector often nudges % on every material when any cargo updates; skip only when there is no
				// ton gain (reset / re-read) so secondary materials still get laser+gather.
				final double pctEpsilon = 1e-3;
				if (!monotonicTonGain && Math.abs(now.pct() - prev.pct()) > pctEpsilon) {
					continue;
				}
				boolean materialChanged = !Objects.equals(prev.material(), now.material());
				Point from = geom.projectValues(prev.pct(), prev.tons());
				Point to = geom.projectValues(now.pct(), now.tons());
				Color col = commanderColorFor(cmdr, toPlot);
				ProspectorLogRow rowPrev = findRowMatchingSnapshot(prev, cmdr, toPlot);
				ProspectorLogRow rowNow = findRowMatchingSnapshot(now, cmdr, toPlot);
				String kFrom = rowPrev == null ? "" : rowKeyForAnim(rowPrev);
				String kTo = rowNow != null ? rowKeyForAnim(rowNow) : "";
				offerGatherAnimRequest(new GatherAnimRequest(from, to, col, kFrom, kTo, cmdr, rockKey));
			}
		}

		private void offerGatherAnimRequest(GatherAnimRequest req) {
			if (matchesCurrentGatherRequest(req)) {
				return;
			}
			String sig = req.signature();
			for (GatherAnimRequest q : gatherAnimQueue) {
				if (q.signature().equals(sig)) {
					return;
				}
			}
			if (!gatherAnimQueue.isEmpty() && gatherAnimQueue.peekLast().signature().equals(sig)) {
				return;
			}
			gatherAnimQueue.addLast(req);
		}

		private boolean matchesCurrentGatherRequest(GatherAnimRequest req) {
			if (!gatherAnimActive) {
				return false;
			}
			return Objects.equals(req.animCommander, gatherAnimCommander)
				&& Objects.equals(req.queueKey, gatherQueueKey)
				&& Objects.equals(req.skipKeyFrom, gatherSkipRowKeyFrom)
				&& Objects.equals(req.skipKeyTo, gatherSkipRowKeyTo);
		}

		private void tryStartNextGatherFromQueue() {
			if (gatherAnimActive || gatherAnimQueue.isEmpty()) {
				return;
			}
			GatherAnimRequest req = gatherAnimQueue.pollFirst();
			if (req == null) {
				return;
			}
			startGatherAnimation(req.from, req.to, req.color, req.skipKeyFrom, req.skipKeyTo, req.animCommander, req.queueKey);
		}

		private void startGatherAnimation(Point from, Point to, Color asteroidColor, String skipKeyFrom, String skipKeyTo, String animCommander, String queueKey) {
			if (gatherAnimTimer == null) {
				gatherAnimTimer = new javax.swing.Timer(30, e -> tickGatherAnimation());
			}
			refreshAnimationScaleCache();
			gatherAnimActive = true;
			gatherDebrisPhaseOnly = false;
			gatherAnimPhase = 0f;
			gatherPhaseSpin = asteroidSpinAngle;
			gatherMoveFrom = new Point(from);
			gatherMoveTo = new Point(to);
			gatherAnimCommander = normalizeCommanderKey(animCommander);
			List<String> pcs = platformCommandersForPlot(filteredRows());
			PlotGeom geom = computePlotGeom();
			if (geom != null) {
				syncGunPlatformCenters(pcs, geom);
				// Do not snap X to home here — that teleports the platform to the idle slot at every gather
				// start and ignores its current position (e.g. still near the last rock or partway home).
				// Motion is always via lerp in tickGatherAnimation from whatever X we have now.
				if (!gunPlatformCenterXByCommander.containsKey(gatherAnimCommander)) {
					gunPlatformCenterXByCommander.put(gatherAnimCommander, homeXForCommander(geom, gatherAnimCommander, pcs));
				}
				gatherLaserFrom = new Point(0, 0);
				updateGatherLaserFromFromGun(geom, gatherAnimCommander);
			} else {
				int fallbackY = Math.min(getHeight() - 4, Math.max(gatherMoveFrom.y, gatherMoveTo.y) + 40);
				gatherLaserFrom = new Point(gatherLaserOriginX(null), fallbackY);
			}
			gatherAsteroidColor = asteroidColor;
			gatherSkipRowKeyFrom = skipKeyFrom != null ? skipKeyFrom : "";
			gatherSkipRowKeyTo = skipKeyTo != null ? skipKeyTo : "";
			gatherQueueKey = queueKey != null ? queueKey : "";
			gatherParticles.clear();
			gatherAnimTimer.start();
		}

		private void tickGatherAnimation() {
			if (!gatherAnimActive) {
				return;
			}
			refreshAnimationScaleCache();
			if (gatherDebrisPhaseOnly) {
				for (OreParticle p : gatherParticles) {
					p.ageMs += 30;
					p.x += p.vx;
					p.y += p.vy;
					p.vy += 0.06;
				}
				gatherParticles.removeIf(p -> p.ageMs > 700);
				PlotGeom gDebris = computePlotGeom();
				if (gDebris != null && !gatherAnimCommander.isEmpty()) {
					List<String> pcsD = platformCommandersForPlot(filteredRows());
					double home = homeXForCommander(gDebris, gatherAnimCommander, pcsD);
					Double gx = gunPlatformCenterXByCommander.get(gatherAnimCommander);
					if (gx != null) {
						double d = home - gx;
						gunPlatformCenterXByCommander.put(gatherAnimCommander, gx + d * GUN_LERP_TO_HOME);
					}
				}
				if (gatherParticles.isEmpty() && gatherGunWithinEpsilonOfHome(GUN_HOME_EPSILON)) {
					endGatherAnimation();
				}
				repaint();
				return;
			}
			gatherAnimPhase += GATHER_PHASE_DELTA;
			gatherPhaseSpin += 0.14;
			if (gatherPhaseSpin > Math.PI * 2) {
				gatherPhaseSpin -= Math.PI * 2;
			}
			for (OreParticle p : gatherParticles) {
				p.ageMs += 30;
				p.x += p.vx;
				p.y += p.vy;
				p.vy += 0.06;
			}
			gatherParticles.removeIf(p -> p.ageMs > 700);
			while (gatherParticles.size() > 220) {
				gatherParticles.remove(0);
			}
			if (gatherAnimPhase > 0.02f && gatherAnimPhase < 1f) {
				Point rock = currentGatherAsteroidScreenPos();
				spawnOreTrailAtRock(rock.x, rock.y);
			}
			if (gatherAnimPhase >= 1f) {
				gatherAnimPhase = 1f;
				if (gatherAnimQueue.isEmpty()) {
					gatherDebrisPhaseOnly = true;
					// Keep gatherSkipRowKey* until endGatherAnimation — clearing here let static redraw during debris.
				} else {
					// Queue backlog: chaining directly avoids visible jump to latest static row between short gathers.
					endGatherAnimation();
					return;
				}
			}
			PlotGeom gTrack = computePlotGeom();
			if (gTrack != null && !gatherAnimCommander.isEmpty()) {
				Point rockScr = currentGatherAsteroidScreenPos();
				Double curX = gunPlatformCenterXByCommander.get(gatherAnimCommander);
				if (curX != null) {
					gunPlatformCenterXByCommander.put(gatherAnimCommander, curX + (rockScr.x - curX) * GUN_LERP_TO_ROCK);
				}
				updateGatherLaserFromFromGun(gTrack, gatherAnimCommander);
			}
			repaint();
		}

		private Point currentGatherAsteroidScreenPos() {
			if (gatherMoveFrom == null) {
				return new Point(0, 0);
			}
			float t = Math.min(1f, gatherAnimPhase);
			int ax = Math.round(gatherMoveFrom.x + (gatherMoveTo.x - gatherMoveFrom.x) * t);
			int ay = Math.round(gatherMoveFrom.y + (gatherMoveTo.y - gatherMoveFrom.y) * t);
			return new Point(ax, ay);
		}

		/** Ore chips ejected from the rock as it moves; called each animation tick. */
		private void spawnOreTrailAtRock(int cx, int cy) {
			int n = 3 + gatherRandom.nextInt(4);
			for (int i = 0; i < n; i++) {
				OreParticle p = new OreParticle();
				p.x = cx + gatherRandom.nextGaussian() * 2.5;
				p.y = cy + gatherRandom.nextGaussian() * 2.5;
				double ang = gatherRandom.nextDouble() * Math.PI * 2;
				double sp = 0.9 + gatherRandom.nextDouble() * 2.4;
				p.vx = Math.cos(ang) * sp;
				p.vy = Math.sin(ang) * sp - 0.9;
				p.ageMs = 0;
				gatherParticles.add(p);
			}
		}

		private void endGatherAnimation() {
			gatherAnimActive = false;
			gatherDebrisPhaseOnly = false;
			gatherAnimCommander = "";
			gatherQueueKey = "";
			gatherParticles.clear();
			if (gatherAnimTimer != null) {
				gatherAnimTimer.stop();
			}
			gatherSkipRowKeyFrom = "";
			gatherSkipRowKeyTo = "";
			tryStartNextGatherFromQueue();
			repaint();
		}

		private void drawPlasmaLaser(Graphics2D g2, int x0, int y0, int x1, int y1, float beamProgress) {
			if (beamProgress <= 0f) {
				return;
			}
			float t = Math.min(1f, beamProgress);
			int cx = x0 + (int) ((x1 - x0) * t);
			int cy = y0 + (int) ((y1 - y0) * t);
			float[] dist = { 0f, 0.35f, 0.65f, 1f };
			Color[] colors = {
				new Color(255, 40, 40, 220),
				new Color(255, 120, 80, 200),
				new Color(255, 200, 220, 160),
				new Color(255, 255, 255, 40)
			};
			LinearGradientPaint gp = new LinearGradientPaint(x0, y0, cx, cy, dist, colors);
			g2.setStroke(new BasicStroke(4.5f + 3f * (float) Math.sin(beamProgress * Math.PI * 3), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.setPaint(gp);
			g2.drawLine(x0, y0, cx, cy);
			g2.setColor(new Color(255, 80, 60, 110));
			g2.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.drawLine(x0, y0, cx, cy);
			g2.setColor(new Color(255, 200, 180, 90));
			g2.fillOval(cx - 6, cy - 6, 12, 12);
		}

		private void drawGatherAnimationOverlay(Graphics2D g2) {
			if (!gatherAnimActive) {
				return;
			}
			if (gatherDebrisPhaseOnly) {
				drawGatherParticlesOnly(g2);
				return;
			}
			Point rock = currentGatherAsteroidScreenPos();
			int ax = rock.x;
			int ay = rock.y;
			if (gatherLaserFrom != null && gatherAnimPhase > 0f) {
				// Beam always reaches the rock; do not shorten the beam while the rock moves (avoids rock sliding ahead of the laser).
				drawPlasmaLaser(g2, gatherLaserFrom.x, gatherLaserFrom.y, ax, ay, 1f);
			}
			drawLineArtAsteroid(g2, ax, ay, gatherAsteroidColor, gatherPhaseSpin, 0.02);

			drawGatherParticlesOnly(g2);
		}

		private void drawGatherParticlesOnly(Graphics2D g2) {
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
			for (OreParticle p : gatherParticles) {
				float life = 1f - p.ageMs / 700f;
				if (life <= 0) {
					continue;
				}
				int alpha = (int) (220 * life);
				g2.setColor(new Color(220, 180, 90, Math.min(255, alpha)));
				g2.fillOval((int) (p.x - 1.5), (int) (p.y - 1.5), 3, 3);
			}
			g2.setComposite(AlphaComposite.SrcOver);
		}

		/** Filled black body, commander-colored rim; rotated in place. */
		private void drawLineArtAsteroid(Graphics2D g2, int cx, int cy, Color color, double spinRadians, double phaseOffset) {
			double a = asteroidDrawScale();
			// Smooth polar rock: mean radius + a few harmonics (lumpy potato, not a spiky star).
			final int n = 32;
			final double rMean = 14.0 * a;
			double ph = phaseOffset * 19.0;
			Path2D.Double path = new Path2D.Double();
			for (int i = 0; i < n; i++) {
				double theta = (i / (double) n) * Math.PI * 2 + Math.PI / 2;
				double s = 0.0;
				s += 0.24 * Math.cos(2 * theta + ph);
				s += 0.20 * Math.cos(3 * theta + ph * 1.4);
				s += 0.14 * Math.cos(5 * theta + 0.35);
				s += 0.10 * Math.cos(7 * theta + ph * 0.55);
				s += 0.06 * Math.cos(11 * theta + ph * 0.9);
				double amp = 0.24 + 0.20 + 0.14 + 0.10 + 0.06;
				double norm = s / amp;
				// Map norm ∈ [-1,1] to radius band so outline stays lumpy without clamp artifacts
				double t = (norm + 1.0) * 0.5;
				double r = rMean * (0.60 + 0.40 * t);
				// Slight elongation (wider than tall)
				double ex = 1.08;
				double ey = 0.92;
				double x = Math.cos(theta) * r * ex;
				double y = Math.sin(theta) * r * ey;
				if (i == 0) {
					path.moveTo(x, y);
				} else {
					path.lineTo(x, y);
				}
			}
			path.closePath();
			Stroke strokeSave = g2.getStroke();
			AffineTransform old = g2.getTransform();
			g2.translate(cx, cy);
			g2.rotate(spinRadians + phaseOffset);
			g2.setColor(Color.BLACK);
			g2.fill(path);
			g2.setColor(color);
			float rimW = (float) Math.max(0.75, Math.min(4.0, 1.35 * a));
			g2.setStroke(new BasicStroke(rimW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.draw(path);
			// Crater bowl + facet lines (dark on black, still reads as texture)
			g2.setColor(new Color(28, 28, 28));
			float detailW = (float) Math.max(0.5, Math.min(3.0, 0.9 * a));
			g2.setStroke(new BasicStroke(detailW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.drawArc(asteroidPx(-7), asteroidPx(-3), asteroidPx(13), asteroidPx(10), 188, 98);
			g2.drawLine(asteroidPx(-6), asteroidPx(5), asteroidPx(2), asteroidPx(-1));
			g2.drawLine(asteroidPx(4), asteroidPx(6), asteroidPx(9), asteroidPx(2));
			g2.setTransform(old);
			g2.setStroke(strokeSave);
		}

		void setRows(List<ProspectorLogRow> rows) {
			this.rows = (rows != null) ? new ArrayList<>(rows) : new ArrayList<>();
			List<ProspectorLogRow> toPlot = filteredRows();
			PlotGeom geom = computePlotGeom();
			scatterAsteroidModel.updateLeadersFromPlotRows(toPlot);
			MiningScatterAsteroidModel.LeaderSnapshotUpdate rockUpdate = scatterAsteroidModel.updateRockSnapshotsForGather(
				toPlot, prospectorMaterialKeysForMarkers, sessionActiveRun);
			if (geom != null && !toPlot.isEmpty() && getWidth() > 0 && getHeight() > 0) {
				enqueueGatherTransitionsForRockSnapshots(rockUpdate.previous(), rockUpdate.next(), toPlot, geom);
			}
			if (!gatherAnimActive) {
				tryStartNextGatherFromQueue();
			}
			repaint();
		}

		void onProspectorLimpetFromMining(Map<String, Double> uiNameToPercent) {
			scatterAsteroidModel.onProspectorLimpet(uiNameToPercent);
		}

		void clearScatterAsteroidModelForSessionEnd() {
			scatterAsteroidModel.clearForMiningSessionEnd();
		}

		void setFilterMode(FilterMode mode) {
			this.filterMode = mode;
			repaint();
		}

		void setSelectedRun(int run) {
			this.selectedRun = run;
			repaint();
		}

		void setSelectedCommander(String commander) {
			this.selectedCommander = commander != null ? commander : "";
			repaint();
		}

		void setRunSummaries(List<RunSummary> summaries) {
			this.runSummaries = (summaries != null) ? new ArrayList<>(summaries) : new ArrayList<>();
			repaint();
		}

		void setSessionActiveRun(int run) {
			int v = run < 0 ? 0 : run;
			if (this.sessionActiveRun != v) {
				this.sessionActiveRun = v;
				repaint();
			}
		}

		void setProspectorMaterialKeysForMarkers(Set<String> normalizedKeys) {
			this.prospectorMaterialKeysForMarkers = normalizedKeys != null ? Set.copyOf(normalizedKeys) : Set.of();
			repaint();
		}

		List<ProspectorLogRow> getFilteredRows() {
			return filteredRows();
		}

		private List<ProspectorLogRow> filteredRows() {
			if (rows.isEmpty()) return rows;
			switch (filterMode) {
				case BY_RUN: {
					String want = normalizeCommanderKey(selectedCommander);
					return rows.stream()
						.filter(r -> r.getRun() == selectedRun
							&& java.util.Objects.equals(want, normalizeCommanderKey(r.getCommanderName())))
						.toList();
				}
				case BY_COMMANDER:
					return rows.stream().filter(r -> java.util.Objects.equals(selectedCommander, r.getCommanderName())).toList();
				default:
					return rows;
			}
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			refreshAnimationScaleCache();
			List<ProspectorLogRow> toPlot = filteredRows();
			pointInfos.clear();
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(EdoUi.User.MAIN_TEXT);
			if (toPlot.isEmpty()) {
				g2.setFont(g2.getFont().deriveFont(12f));
				String msg = rows.isEmpty() ? "No data" : "No data for this selection";
				int w = getWidth();
				int h = getHeight();
				FontMetrics fm = g2.getFontMetrics();
				int tw = fm.stringWidth(msg);
				g2.drawString(msg, Math.max(0, (w - tw) / 2), (h + fm.getAscent()) / 2 - fm.getDescent());
				g2.dispose();
				return;
			}
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
			int w = getWidth();
			int h = getHeight();
			PlotGeom geom = computePlotGeom();
			if (geom == null) {
				g2.setFont(g2.getFont().deriveFont(12f));
				String msg = w <= PAD_LEFT + PAD_RIGHT
						? "Widen the panel to show the chart."
						: "Not enough space for chart (many runs). Resize taller or use Table.";
				FontMetrics fm2 = g2.getFontMetrics();
				int tw = fm2.stringWidth(msg);
				g2.drawString(msg, Math.max(0, (w - tw) / 2), (h + fm2.getAscent()) / 2 - fm2.getDescent());
				g2.dispose();
				return;
			}
			int plotW = geom.plotW;
			int plotH = geom.plotH;
			int plotX = geom.plotX;
			int plotY = geom.plotY;
			double minPct = geom.minPct;
			double maxPct = geom.maxPct;
			double minAct = geom.minAct;
			double maxAct = geom.maxAct;
			g2.setColor(EdoUi.User.MAIN_TEXT);
			g2.drawRect(plotX, plotY, plotW, plotH);
			g2.setFont(g2.getFont().deriveFont(10f));
			FontMetrics fm = g2.getFontMetrics();
			// X axis label with unit (below plot)
			String xLabel = "Percentage (%)";
			int xLabelY = h - 4;
			g2.drawString(xLabel, plotX + plotW / 2 - fm.stringWidth(xLabel) / 2, xLabelY);
			// X tick labels (slightly above the axis label to avoid overlap)
			for (int i = 0; i < TICK_LABELS; i++) {
				double frac = (TICK_LABELS <= 1) ? 0.5 : (double) i / (TICK_LABELS - 1);
				double val = minPct + frac * (maxPct - minPct);
				String tick = (val == (long) val) ? String.valueOf((long) val) : String.format(Locale.US, "%.1f", val);
				int tx = plotX + (int) (frac * plotW);
				g2.drawString(tick, tx - fm.stringWidth(tick) / 2, h - 18);
			}
			// Y axis label with unit (vertical text on the left)
			String yLabel = "Tons";
			java.awt.geom.AffineTransform oldTx = g2.getTransform();
			g2.rotate(-Math.PI / 2.0);
			int yCenter = plotY + plotH / 2;
			int yLabelWidth = fm.stringWidth(yLabel);
			int xRot = -(yCenter + yLabelWidth / 2);
			int yRot = Math.max(10, plotX - 20);
			g2.drawString(yLabel, xRot, yRot);
			g2.setTransform(oldTx);
			// Y tick labels (left of plot)
			for (int i = 0; i < TICK_LABELS; i++) {
				double frac = (TICK_LABELS <= 1) ? 0.5 : (double) i / (TICK_LABELS - 1);
				double val = minAct + (1.0 - frac) * (maxAct - minAct); // bottom = min, top = max
				String tick = (val >= 100 || val == (long) val) ? String.valueOf((long) val) : String.format(Locale.US, "%.1f", val);
				int ty = plotY + (int) (frac * plotH);
				g2.drawString(tick, plotX - fm.stringWidth(tick) - 4, ty + fm.getAscent() / 2);
			}
			List<String> commanderOrder = filterMode == FilterMode.ALL
				? topCommandersForGunsAndPalette(toPlot)
				: List.of();
			Map<String, Color> commanderColor = new HashMap<>();
			for (int i = 0; i < commanderOrder.size(); i++) {
				commanderColor.put(commanderOrder.get(i), COMMANDER_PALETTE[i % COMMANDER_PALETTE.length]);
			}

			// Run summary lines at top (same text and size as Table tab Run row).
			if (!runSummaries.isEmpty()) {
				Font runLineFont = getFont().deriveFont(Font.BOLD, getFont().getSize2D() + 2f);
				g2.setFont(runLineFont);
				FontMetrics sumFm = g2.getFontMetrics();
				int lineHeight = sumFm.getHeight();
				int yTop = 4 + sumFm.getAscent();
				int drawn = 0;
				for (RunSummary summary : runSummaries) {
					if (drawn >= MAX_SCATTER_RUN_SUMMARY_LINES) {
						break;
					}
					if (summary == null) continue;
					String line = summary.formatSummary();
					if (line == null || line.isEmpty()) {
						continue;
					}
					Color lineColor = EdoUi.User.MAIN_TEXT;
					if (filterMode == FilterMode.ALL && summary.commanderName != null && !summary.commanderName.isBlank()) {
						lineColor = commanderColor.getOrDefault(normalizeCommanderKey(summary.commanderName), EdoUi.User.MAIN_TEXT);
					} else if (filterMode == FilterMode.BY_COMMANDER && selectedCommander != null && !selectedCommander.isEmpty()) {
						// In commander-only mode, points use the default highlight color.
						lineColor = EdoUi.User.VALUABLE;
					}
					g2.setColor(lineColor);
					g2.drawString(line, plotX, yTop);
					yTop += lineHeight;
					drawn++;
				}
				if (runSummaries.size() > MAX_SCATTER_RUN_SUMMARY_LINES) {
					g2.setColor(EdoUi.User.MAIN_TEXT);
					String more = "+ " + (runSummaries.size() - MAX_SCATTER_RUN_SUMMARY_LINES) + " more runs (see Table tab)";
					g2.drawString(more, plotX, yTop);
				}
				g2.setFont(getFont());
				g2.setColor(EdoUi.User.MAIN_TEXT);
			}
			for (ProspectorLogRow r : toPlot) {
				Color pointColor = filterMode == FilterMode.ALL && !commanderOrder.isEmpty()
					? commanderColor.getOrDefault(normalizeCommanderKey(r.getCommanderName()), EdoUi.User.VALUABLE)
					: EdoUi.User.VALUABLE;
				g2.setColor(pointColor);
				double p = r.getPercent();
				double a = r.getDifference();
				double nx = (maxPct > minPct) ? (p - minPct) / (maxPct - minPct) : 0.5;
				double ny = (maxAct > minAct) ? 1.0 - (a - minAct) / (maxAct - minAct) : 0.5;
				int x = plotX + (int) (nx * plotW);
				int y = plotY + (int) (ny * plotH);
				g2.fillOval((int) (x - POINT_RADIUS), (int) (y - POINT_RADIUS), (int) (2 * POINT_RADIUS), (int) (2 * POINT_RADIUS));
				pointInfos.add(new PointInfo(x, y, r));
			}

			// Trend lines by commander (best-fit line of Percentage vs Actual); X span is each series’ % range only.
			Stroke oldStroke = g2.getStroke();
			g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			double[] trendPctRange = new double[2];
			if (filterMode == FilterMode.ALL) {
				for (String cmdrKey : allCommandersByRowCountDesc(toPlot)) {
					List<ProspectorLogRow> rowsForCommander = toPlot.stream()
						.filter(r -> java.util.Objects.equals(cmdrKey, normalizeCommanderKey(r.getCommanderName())))
						.toList();
					Regression reg = computeRegression(rowsForCommander);
					if (!reg.valid) {
						continue;
					}
					minMaxPercent(rowsForCommander, trendPctRange);
					if (!Double.isFinite(trendPctRange[0]) || !Double.isFinite(trendPctRange[1])) {
						continue;
					}
					int[] seg = screenEndpointsForTrend(geom, reg, trendPctRange[0], trendPctRange[1]);
					Color c = commanderColor.getOrDefault(cmdrKey, EdoUi.User.VALUABLE);
					g2.setColor(c);
					g2.drawLine(seg[0], seg[1], seg[2], seg[3]);
				}
			} else if (filterMode == FilterMode.BY_COMMANDER && selectedCommander != null && !selectedCommander.isEmpty()) {
				Regression reg = computeRegression(toPlot);
				if (reg.valid) {
					minMaxPercent(toPlot, trendPctRange);
					int[] seg = screenEndpointsForTrend(geom, reg, trendPctRange[0], trendPctRange[1]);
					Color c = commanderColor.getOrDefault(normalizeCommanderKey(selectedCommander), EdoUi.User.VALUABLE);
					g2.setColor(c);
					g2.drawLine(seg[0], seg[1], seg[2], seg[3]);
				}
			}
			g2.setStroke(oldStroke);

			// Commander legend (only when coloring by commander: mode = ALL) — restored from pre-regression layout (boxed, lower-right inside plot).
			if (filterMode == FilterMode.ALL && !commanderOrder.isEmpty()) {
				int swatchSize = 10;
				int swatchTextGap = 4;
				int legendLineHeight = fm.getHeight();
				int maxLabelWidth = 0;
				for (String name : commanderOrder) {
					String label = (name == null || name.isEmpty()) ? "-" : name;
					maxLabelWidth = Math.max(maxLabelWidth, fm.stringWidth(label));
				}
				int legendWidth = swatchSize + swatchTextGap + maxLabelWidth + 8;
				int legendHeight = commanderOrder.size() * legendLineHeight + 8;
				int legendX = plotX + plotW - legendWidth - 4;
				int legendY = plotY + plotH - legendHeight - 4;

				g2.setColor(new Color(0, 0, 0, 160));
				g2.fillRect(legendX, legendY, legendWidth, legendHeight);
				g2.setColor(EdoUi.User.MAIN_TEXT);
				g2.drawRect(legendX, legendY, legendWidth, legendHeight);

				int entryY = legendY + 4;
				for (int i = 0; i < commanderOrder.size(); i++) {
					String name = commanderOrder.get(i);
					String label = (name == null || name.isEmpty()) ? "-" : name;
					Color c = commanderColor.getOrDefault(name, EdoUi.User.VALUABLE);
					int swatchX = legendX + 4;
					int swatchY = entryY + (legendLineHeight - swatchSize) / 2;
					g2.setColor(c);
					g2.fillRect(swatchX, swatchY, swatchSize, swatchSize);
					g2.setColor(EdoUi.User.MAIN_TEXT);
					g2.drawRect(swatchX, swatchY, swatchSize, swatchSize);
					int textX = swatchX + swatchSize + swatchTextGap;
					int textY = entryY + fm.getAscent();
					g2.drawString(label, textX, textY);
					entryY += legendLineHeight;
				}
			}

			// Line-art asteroids: {@link MiningScatterAsteroidModel#computeRockMarkerRows} — one rock per material
			// on the commander's current asteroid (latest row anchor), no duplicate rows for the same material.
			if (!pointInfos.isEmpty() && sessionActiveRun > 0) {
				List<ProspectorLogRow> inSession = toPlot.stream()
					.filter(r -> r.getRun() == sessionActiveRun)
					.toList();
				if (!inSession.isEmpty()) {
					List<ProspectorLogRow> rockRows = MiningScatterAsteroidModel.computeRockMarkerRows(
						inSession, prospectorMaterialKeysForMarkers);
					for (ProspectorLogRow leadRow : rockRows) {
						String cmdr = leadRow.getCommanderName() != null ? leadRow.getCommanderName() : "";
						Color markColor = commanderColor.getOrDefault(normalizeCommanderKey(cmdr), EdoUi.User.VALUABLE);
						String mat = leadRow.getMaterial() != null ? leadRow.getMaterial() : "";
						double phase = ((cmdr.hashCode() & 0xFFFF) ^ (mat.hashCode() & 0xFFFF)) * 0.001;
						String rk = rowKeyForAnim(leadRow);
						if (gatherAnimActive) {
							if (!gatherDebrisPhaseOnly && !gatherQueueKey.isEmpty()
								&& gatherQueueKey.equals(cmdr + "\t" + GalacticAveragePrices.normalizeMaterialKey(mat))) {
								continue;
							}
							if (!gatherDebrisPhaseOnly && !gatherSkipRowKeyTo.isEmpty() && rk.equals(gatherSkipRowKeyTo)) {
								continue;
							}
							if (!gatherDebrisPhaseOnly && !gatherSkipRowKeyFrom.isEmpty() && rk.equals(gatherSkipRowKeyFrom)) {
								continue;
							}
						}
						for (PointInfo pi : pointInfos) {
							if (pi.row == leadRow) {
								if (gatherSuppressStaticAsteroidAtPlotPixel(pi.x, pi.y)) {
									break;
								}
								drawLineArtAsteroid(g2, pi.x, pi.y, markColor, asteroidSpinAngle, phase);
								break;
							}
						}
					}
				}
			}

			// Optionally highlight hovered point and draw inline tooltip
			if (hoverRow != null && !pointInfos.isEmpty()) {
				PointInfo hovered = null;
				for (PointInfo pi : pointInfos) {
					if (pi.row == hoverRow) {
						hovered = pi;
						break;
					}
				}
				if (hovered != null) {
					// Highlight ring
					g2.setColor(Color.WHITE);
					int r = (int) (POINT_RADIUS + 2);
					g2.drawOval(hovered.x - r, hovered.y - r, 2 * r, 2 * r);

					// Tooltip contents
					int run = hoverRow.getRun();
					String asteroid = hoverRow.getAsteroidId();
					if (asteroid == null || asteroid.isBlank()) {
						asteroid = "-";
					}
					double pct = hoverRow.getPercent();
					String material = hoverRow.getMaterial();
					if (material == null || material.isBlank()) {
						material = "-";
					}
					double tons = hoverRow.getDifference();
					String commander = hoverRow.getCommanderName();
					if (commander == null || commander.isBlank()) {
						commander = "-";
					}
					String line1 = String.format(Locale.US, "Run %d %s", run, asteroid);
					String line2 = String.format(Locale.US, "%.1f%% %s", pct, material);
					String line3 = String.format(Locale.US, "%d Tons", Math.round(tons));
					String line4 = commander;

					String[] lines = { line1, line2, line3, line4 };
					g2.setFont(g2.getFont().deriveFont(10f));
					FontMetrics tfm = g2.getFontMetrics();
					int maxWidth = 0;
					for (String s : lines) {
						maxWidth = Math.max(maxWidth, tfm.stringWidth(s));
					}
					int lineHeight = tfm.getHeight();
					int boxPadding = 4;
					int boxW = maxWidth + boxPadding * 2;
					int boxH = lineHeight * lines.length + boxPadding * 2;

					int boxX = hovered.x + 8;
					int boxY = hovered.y - boxH - 8;
					// Clamp box inside plot area
					if (boxX + boxW > plotX + plotW) {
						boxX = plotX + plotW - boxW - 2;
					}
					if (boxY < plotY) {
						boxY = hovered.y + 8;
						if (boxY + boxH > plotY + plotH) {
							boxY = plotY + plotH - boxH - 2;
						}
					}

					g2.setColor(new Color(0, 0, 0, 200));
					g2.fillRect(boxX, boxY, boxW, boxH);
					g2.setColor(EdoUi.User.MAIN_TEXT);
					g2.drawRect(boxX, boxY, boxW, boxH);

					int textX = boxX + boxPadding;
					int textY = boxY + boxPadding + tfm.getAscent();
					for (String s : lines) {
						g2.drawString(s, textX, textY);
						textY += lineHeight;
					}
				}
			}

			// Trend line hover (same style as point tooltip); points take priority over trend
			if (hoverRow == null && hoverTrend != null && hoverTrend.reg != null && hoverTrend.reg.valid) {
				Regression tr = hoverTrend.reg;
				String cmdLabel = hoverTrend.commanderLabel.isEmpty() ? "-" : hoverTrend.commanderLabel;
				String eq = String.format(Locale.US, "tons = %.8f * pct %+.6f", tr.slope, tr.intercept);
				String nR2 = String.format(Locale.US, "n=%d", tr.n);
				if (!Double.isNaN(tr.rSquared)) {
					nR2 += String.format(Locale.US, "  R²=%.4f", tr.rSquared);
				}
				String range = String.format(Locale.US, "pct axis: %.3f … %.3f", minPct, maxPct);
				String copy = String.format(Locale.US, "slope=%.10f  intercept=%.10f", tr.slope, tr.intercept);
				String[] tlines = { "Trend (linear fit)", "Commander: " + cmdLabel, eq, nR2, range, copy };
				g2.setFont(g2.getFont().deriveFont(10f));
				FontMetrics tfm = g2.getFontMetrics();
				int maxWidth = 0;
				for (String s : tlines) {
					maxWidth = Math.max(maxWidth, tfm.stringWidth(s));
				}
				int tlineHeight = tfm.getHeight();
				int boxPadding = 4;
				int boxW = maxWidth + boxPadding * 2;
				int boxH = tlineHeight * tlines.length + boxPadding * 2;
				int ax = hoverTrend.anchorX;
				int ay = hoverTrend.anchorY;
				int boxX = ax + 10;
				int boxY = ay - boxH - 8;
				if (boxX + boxW > plotX + plotW) {
					boxX = plotX + plotW - boxW - 2;
				}
				if (boxX < plotX) {
					boxX = plotX + 2;
				}
				if (boxY < plotY) {
					boxY = ay + 10;
				}
				if (boxY + boxH > plotY + plotH) {
					boxY = plotY + plotH - boxH - 2;
				}
				g2.setColor(new Color(0, 0, 0, 200));
				g2.fillRect(boxX, boxY, boxW, boxH);
				g2.setColor(EdoUi.User.MAIN_TEXT);
				g2.drawRect(boxX, boxY, boxW, boxH);
				int textX = boxX + boxPadding;
				int textY = boxY + boxPadding + tfm.getAscent();
				for (String s : tlines) {
					g2.drawString(s, textX, textY);
					textY += tlineHeight;
				}
			}
			drawGunPlatforms(g2, geom, toPlot, commanderColor);
			drawGatherAnimationOverlay(g2);
			g2.dispose();
		}

		private void handleMouseMoved(int mx, int my) {
			PlotGeom geom = computePlotGeom();
			List<ProspectorLogRow> toPlot = filteredRows();
			if (geom == null || toPlot.isEmpty()) {
				hoverRow = null;
				hoverTrend = null;
				repaint();
				return;
			}
			final double hitRadius = 6.0;
			PointInfo closest = null;
			double closestDistSq = hitRadius * hitRadius;
			if (!pointInfos.isEmpty()) {
				for (PointInfo pi : pointInfos) {
					double dx = mx - pi.x;
					double dy = my - pi.y;
					double distSq = dx * dx + dy * dy;
					if (distSq <= closestDistSq) {
						closestDistSq = distSq;
						closest = pi;
					}
				}
			}
			if (closest != null) {
				hoverRow = closest.row;
				hoverTrend = null;
				repaint();
				return;
			}
			hoverRow = null;
			hoverTrend = findTrendHover(mx, my, geom, toPlot);
			repaint();
		}

		private void pollGlobalMouse() {
			if (!isShowing() || pointInfos.isEmpty()) {
				return;
			}
			java.awt.PointerInfo info = java.awt.MouseInfo.getPointerInfo();
			if (info == null) {
				return;
			}
			java.awt.Point mouseOnScreen = info.getLocation();
			java.awt.Point panelLoc;
			try {
				panelLoc = getLocationOnScreen();
			} catch (IllegalStateException ex) {
				return;
			}
			java.awt.Rectangle bounds = new java.awt.Rectangle(
				panelLoc.x,
				panelLoc.y,
				getWidth(),
				getHeight()
			);
			if (!bounds.contains(mouseOnScreen)) {
				if (hoverRow != null || hoverTrend != null) {
					hoverRow = null;
					hoverTrend = null;
					repaint();
				}
				return;
			}
			int mx = mouseOnScreen.x - panelLoc.x;
			int my = mouseOnScreen.y - panelLoc.y;
			handleMouseMoved(mx, my);
		}

		/**
		 * Screen segment for a linear trend over {@code dataMinPct…dataMaxPct} (that series’ % range),
		 * mapped through the plot axes (Y clamped to tons axis range).
		 */
		private static int[] screenEndpointsForTrend(PlotGeom geom, Regression reg, double dataMinPct, double dataMaxPct) {
			double minPctAxis = geom.minPct;
			double maxPctAxis = geom.maxPct;
			double minAct = geom.minAct;
			double maxAct = geom.maxAct;
			int plotX = geom.plotX;
			int plotY = geom.plotY;
			int plotW = geom.plotW;
			int plotH = geom.plotH;
			double x1 = dataMinPct;
			double x2 = dataMaxPct;
			if (x2 < x1) {
				double t = x1;
				x1 = x2;
				x2 = t;
			}
			x1 = Math.max(minPctAxis, Math.min(maxPctAxis, x1));
			x2 = Math.max(minPctAxis, Math.min(maxPctAxis, x2));
			double spanAxis = maxPctAxis - minPctAxis;
			if (!(spanAxis > 0)) {
				spanAxis = 1;
			}
			if (x2 - x1 < 1e-9 * spanAxis) {
				x2 = Math.min(maxPctAxis, x1 + Math.max(1e-6, spanAxis * 0.02));
				if (x2 <= x1) {
					x1 = minPctAxis;
					x2 = maxPctAxis;
				}
			}
			double y1 = reg.slope * x1 + reg.intercept;
			double y2 = reg.slope * x2 + reg.intercept;
			y1 = Math.max(minAct, Math.min(maxAct, y1));
			y2 = Math.max(minAct, Math.min(maxAct, y2));
			double nx1 = (maxPctAxis > minPctAxis) ? (x1 - minPctAxis) / (maxPctAxis - minPctAxis) : 0.5;
			double nx2 = (maxPctAxis > minPctAxis) ? (x2 - minPctAxis) / (maxPctAxis - minPctAxis) : 0.5;
			double ny1 = (maxAct > minAct) ? 1.0 - (y1 - minAct) / (maxAct - minAct) : 0.5;
			double ny2 = (maxAct > minAct) ? 1.0 - (y2 - minAct) / (maxAct - minAct) : 0.5;
			int sx1 = plotX + (int) (nx1 * plotW);
			int sy1 = plotY + (int) (ny1 * plotH);
			int sx2 = plotX + (int) (nx2 * plotW);
			int sy2 = plotY + (int) (ny2 * plotH);
			return new int[] { sx1, sy1, sx2, sy2 };
		}

		private static double pointToSegmentDistSq(double px, double py, double x1, double y1, double x2, double y2) {
			double vx = x2 - x1;
			double vy = y2 - y1;
			double wx = px - x1;
			double wy = py - y1;
			double c2 = vx * vx + vy * vy;
			double t = c2 < 1e-12 ? 0.0 : (vx * wx + vy * wy) / c2;
			if (t <= 0.0) {
				double dx = px - x1;
				double dy = py - y1;
				return dx * dx + dy * dy;
			}
			if (t >= 1.0) {
				double dx = px - x2;
				double dy = py - y2;
				return dx * dx + dy * dy;
			}
			double projX = x1 + t * vx;
			double projY = y1 + t * vy;
			double dx = px - projX;
			double dy = py - projY;
			return dx * dx + dy * dy;
		}

		private static void closestPointOnSegment(double px, double py, double x1, double y1, double x2, double y2, double[] outXY) {
			double vx = x2 - x1;
			double vy = y2 - y1;
			double wx = px - x1;
			double wy = py - y1;
			double c2 = vx * vx + vy * vy;
			double t = c2 < 1e-12 ? 0.0 : (vx * wx + vy * wy) / c2;
			t = Math.max(0.0, Math.min(1.0, t));
			outXY[0] = x1 + t * vx;
			outXY[1] = y1 + t * vy;
		}

		private TrendHoverState findTrendHover(int mx, int my, PlotGeom geom, List<ProspectorLogRow> toPlot) {
			final double hitTol = 10.0;
			final double hitTolSq = hitTol * hitTol;
			double bestDistSq = Double.POSITIVE_INFINITY;
			TrendHoverState best = null;
			double[] proj = new double[2];
			double[] trendPctRange = new double[2];
			if (filterMode == FilterMode.ALL) {
				for (String cmdrKey : allCommandersByRowCountDesc(toPlot)) {
					List<ProspectorLogRow> rowsForCommander = toPlot.stream()
						.filter(r -> java.util.Objects.equals(cmdrKey, normalizeCommanderKey(r.getCommanderName())))
						.toList();
					Regression reg = computeRegression(rowsForCommander);
					if (!reg.valid) {
						continue;
					}
					minMaxPercent(rowsForCommander, trendPctRange);
					if (!Double.isFinite(trendPctRange[0]) || !Double.isFinite(trendPctRange[1])) {
						continue;
					}
					int[] seg = screenEndpointsForTrend(geom, reg, trendPctRange[0], trendPctRange[1]);
					double dsq = pointToSegmentDistSq(mx, my, seg[0], seg[1], seg[2], seg[3]);
					if (dsq < bestDistSq) {
						closestPointOnSegment(mx, my, seg[0], seg[1], seg[2], seg[3], proj);
						best = new TrendHoverState(cmdrKey, reg, seg[0], seg[1], seg[2], seg[3],
							(int) Math.round(proj[0]), (int) Math.round(proj[1]));
						bestDistSq = dsq;
					}
				}
			} else if (filterMode == FilterMode.BY_COMMANDER && selectedCommander != null && !selectedCommander.isEmpty()) {
				Regression reg = computeRegression(toPlot);
				if (reg.valid) {
					minMaxPercent(toPlot, trendPctRange);
					int[] seg = screenEndpointsForTrend(geom, reg, trendPctRange[0], trendPctRange[1]);
					double dsq = pointToSegmentDistSq(mx, my, seg[0], seg[1], seg[2], seg[3]);
					if (dsq < bestDistSq) {
						closestPointOnSegment(mx, my, seg[0], seg[1], seg[2], seg[3], proj);
						best = new TrendHoverState(selectedCommander, reg, seg[0], seg[1], seg[2], seg[3],
							(int) Math.round(proj[0]), (int) Math.round(proj[1]));
						bestDistSq = dsq;
					}
				}
			}
			if (best != null && bestDistSq <= hitTolSq) {
				return best;
			}
			return null;
		}

		private static Regression computeRegression(List<ProspectorLogRow> rows) {
			ProspectorLogRegression.Result r = ProspectorLogRegression.compute(rows);
			return new Regression(r.valid, r.slope, r.intercept, r.n, r.rSquared);
		}

		private static final class Regression {
			final boolean valid;
			final double slope;
			final double intercept;
			final int n;
			final double rSquared;

			Regression(boolean valid, double slope, double intercept, int n, double rSquared) {
				this.valid = valid;
				this.slope = slope;
				this.intercept = intercept;
				this.n = n;
				this.rSquared = rSquared;
			}
		}
	}

	/**
	 * Global hover poller for the Mining tab's Table/Scatter view buttons.
	 * Uses OS-level mouse position so it works even when the overlay window
	 * is in mouse pass-through mode.
	 */
	private static final class SpreadsheetViewHoverPoller implements ActionListener {

		private static final int POLL_INTERVAL_MS = 40;

		private static final List<Entry> entries = new ArrayList<>();
		private static final javax.swing.Timer pollTimer;

		static {
			SpreadsheetViewHoverPoller listener = new SpreadsheetViewHoverPoller();
			pollTimer = new javax.swing.Timer(POLL_INTERVAL_MS, listener);
			pollTimer.start();
		}

		private static final class Entry {
			final AbstractButton button;
			final int delayMs;
			final Runnable action;

			long hoverStartMs = -1L;
			boolean firedForCurrentHover = false;

			Entry(AbstractButton button, int delayMs, Runnable action) {
				this.button = button;
				this.delayMs = delayMs;
				this.action = action;
			}
		}

		static void register(AbstractButton button, int delayMs, Runnable action) {
			if (button == null || action == null) {
				return;
			}
			entries.add(new Entry(button, delayMs, action));
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			if (entries.isEmpty()) {
				return;
			}

			java.awt.PointerInfo pointerInfo = java.awt.MouseInfo.getPointerInfo();
			if (pointerInfo == null) {
				resetAll();
				return;
			}

			java.awt.Point mouseOnScreen = pointerInfo.getLocation();
			long now = System.currentTimeMillis();

			for (Entry entry : entries) {
				AbstractButton button = entry.button;
				if (button == null || !button.isShowing()) {
					entry.hoverStartMs = -1L;
					entry.firedForCurrentHover = false;
					continue;
				}

				java.awt.Point buttonLoc;
				try {
					buttonLoc = button.getLocationOnScreen();
				} catch (IllegalStateException ex) {
					entry.hoverStartMs = -1L;
					entry.firedForCurrentHover = false;
					continue;
				}

				java.awt.Rectangle bounds = new java.awt.Rectangle(
					buttonLoc.x,
					buttonLoc.y,
					button.getWidth(),
					button.getHeight()
				);

				if (bounds.contains(mouseOnScreen)) {
					if (entry.hoverStartMs < 0L) {
						entry.hoverStartMs = now;
						entry.firedForCurrentHover = false;
					} else if (!entry.firedForCurrentHover && now - entry.hoverStartMs >= entry.delayMs) {
						if (entry.action != null) {
							SwingUtilities.invokeLater(entry.action);
						}
						entry.firedForCurrentHover = true;
					}
				} else {
					entry.hoverStartMs = -1L;
					entry.firedForCurrentHover = false;
				}
			}
		}

		private static void resetAll() {
			for (Entry entry : entries) {
				entry.hoverStartMs = -1L;
				entry.firedForCurrentHover = false;
			}
		}
	}

	/**
	 * Table model for prospector log rows. Display order: Commander first, then Run, …
	 * Sheet/API row layout is still 15 columns with Run in column A.
	 */
	private static final class ProspectorLogTableModel extends AbstractTableModel {
		private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("h:mma", Locale.US);
		private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US);
		private static final String[] COLUMNS = {
				"Commander", "Run", "Asteroid", "Time", "Type", "Percentage",
				"Before Amount", "After Amount", "Tons", "Core", "Duds", "System", "Body"
		};
		private List<Object> displayRows = new ArrayList<>();

		void setRows(List<ProspectorLogRow> rows, MaterialNameMatcher matcher) {
			this.displayRows = buildDisplayList(rows != null ? rows : List.of(), matcher);
			fireTableDataChanged();
		}

		private static List<Object> buildDisplayList(List<ProspectorLogRow> rows, MaterialNameMatcher matcher) {
			List<Object> out = new ArrayList<>();
			if (rows.isEmpty()) {
				return out;
			}
			// Group by (commander, run) so different commanders sharing a run number get their own block + summary.
			Map<RunKey, List<ProspectorLogRow>> byRun = new HashMap<>();
			for (ProspectorLogRow r : rows) {
				if (r == null) {
					continue;
				}
				int run = r.getRun();
				String commander = r.getCommanderName();
				if (commander == null || commander.isBlank()) {
					commander = "-";
				}
				RunKey key = new RunKey(run, commander);
				byRun.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
			}
			// Order runs by latest activity first (row timestamps, else run end/start), then run number and commander.
			List<RunKey> runOrder = new ArrayList<>(byRun.keySet());
			Comparator<Instant> newestFirst = Comparator.nullsLast(Comparator.reverseOrder());
			// Do not use .reversed() after thenComparingInt: that flips the *whole* comparator and would put
			// oldest runs first. Tie-break on higher run number only (newer trips within a commander).
			runOrder.sort(Comparator
				.<RunKey, Instant>comparing(k -> runRecencyForOrdering(byRun.get(k)), newestFirst)
				.thenComparing((RunKey a, RunKey b) -> Integer.compare(b.run, a.run))
				.thenComparing(k -> k.commander, String.CASE_INSENSITIVE_ORDER));
			Comparator<Instant> tsDesc = Comparator.nullsLast(Comparator.reverseOrder());
			Instant now = Instant.now();
			for (RunKey key : runOrder) {
				List<ProspectorLogRow> runRows = new ArrayList<>(byRun.get(key));
				runRows.sort(Comparator
					.comparing(ProspectorLogRow::getTimestamp, tsDesc)
					.thenComparing(ProspectorLogTableModel::compareAsteroidIdDescending));
				RunSummary summary = computeRunSummary(key, runRows, matcher, now);
				if (summary != null) {
					out.add(summary);
				}
				out.addAll(runRows);
			}
			return out;
		}

		/**
		 * Run start/end metadata: any row in the group may carry {@link ProspectorLogRow#getRunStartTime()}.
		 * (Display order is newest-first; scan does not depend on sort order.)
		 */
		private static ProspectorLogRow canonicalRunMetaRow(List<ProspectorLogRow> runRows) {
			if (runRows == null) {
				return null;
			}
			for (ProspectorLogRow r : runRows) {
				if (r != null && r.getRunStartTime() != null) {
					return r;
				}
			}
			return null;
		}

		/** True when the canonical run row has a start time and no end time (open run). */
		private static boolean isOpenRunForSummary(List<ProspectorLogRow> runRowsTimeAscending) {
			ProspectorLogRow c = canonicalRunMetaRow(runRowsTimeAscending);
			return c != null && c.getRunEndTime() == null;
		}

		/** Latest {@link ProspectorLogRow#getTimestamp()} in the run group; null if all rows lack timestamps. */
		private static Instant maxProspectingTimestamp(List<ProspectorLogRow> runRows) {
			if (runRows == null || runRows.isEmpty()) {
				return null;
			}
			Instant max = null;
			for (ProspectorLogRow r : runRows) {
				if (r == null) {
					continue;
				}
				Instant t = r.getTimestamp();
				if (t == null) {
					continue;
				}
				if (max == null || t.isAfter(max)) {
					max = t;
				}
			}
			return max;
		}

		/**
		 * Best instant for ordering runs in the table: latest prospect row time, else latest run end, else latest run start.
		 */
		private static Instant runRecencyForOrdering(List<ProspectorLogRow> runRows) {
			Instant best = maxProspectingTimestamp(runRows);
			if (best != null) {
				return best;
			}
			if (runRows == null) {
				return null;
			}
			for (ProspectorLogRow r : runRows) {
				if (r == null) {
					continue;
				}
				Instant e = r.getRunEndTime();
				if (e != null && (best == null || e.isAfter(best))) {
					best = e;
				}
			}
			if (best != null) {
				return best;
			}
			for (ProspectorLogRow r : runRows) {
				if (r == null) {
					continue;
				}
				Instant s = r.getRunStartTime();
				if (s != null && (best == null || s.isAfter(best))) {
					best = s;
				}
			}
			return best;
		}

		private static int compareAsteroidIdDescending(ProspectorLogRow a, ProspectorLogRow b) {
			int ia = parseAsteroidIdToIndex(a != null ? a.getAsteroidId() : "");
			int ib = parseAsteroidIdToIndex(b != null ? b.getAsteroidId() : "");
			return Integer.compare(ib, ia);
		}

		private static RunSummary computeRunSummary(RunKey key, List<ProspectorLogRow> runRows, MaterialNameMatcher matcher, Instant now) {
			if (runRows == null || runRows.isEmpty()) return null;
			Instant firstTs = null, lastTs = null;
			ProspectorLogRow canonicalRow = canonicalRunMetaRow(runRows);
			double totalTons = 0.0;
			double totalCredits = 0.0;
			for (ProspectorLogRow r : runRows) {
				if (r.getTimestamp() != null) {
					if (firstTs == null || r.getTimestamp().isBefore(firstTs)) firstTs = r.getTimestamp();
					if (lastTs == null || r.getTimestamp().isAfter(lastTs)) lastTs = r.getTimestamp();
				}
				double diff = r.getDifference();
				totalTons += diff;
				if (matcher != null) {
					int price = matcher.lookupAvgSell(r.getMaterial(), r.getMaterial());
					totalCredits += diff * price;
				}
			}
			Instant start = (canonicalRow != null && canonicalRow.getRunStartTime() != null) ? canonicalRow.getRunStartTime() : firstTs;
			Instant end = (canonicalRow != null && canonicalRow.getRunEndTime() != null) ? canonicalRow.getRunEndTime() : now;
			double durationHours = 0.0;
			if (start != null && end != null && !end.isBefore(start)) {
				durationHours = (end.toEpochMilli() - start.toEpochMilli()) / (1000.0 * 3600.0);
			}
			double tonsPerHour = durationHours > 0 ? totalTons / durationHours : 0.0;
			double creditsPerHour = durationHours > 0 ? totalCredits / durationHours : 0.0;
			String dateStr = start != null ? start.atZone(ZoneId.systemDefault()).format(DATE_FMT) : "";
			return new RunSummary(key.run, key.commander, dateStr, tonsPerHour, creditsPerHour);
		}

		/** Build run summary strings for every (run, commander) group (same format as Table Run row). */
		static List<String> getRunSummaryLines(List<ProspectorLogRow> rows, MaterialNameMatcher matcher) {
			if (rows == null || rows.isEmpty()) return List.of();
			Map<RunKey, List<ProspectorLogRow>> byRun = new HashMap<>();
			for (ProspectorLogRow r : rows) {
				if (r == null) continue;
				String commander = r.getCommanderName();
				if (commander == null || commander.isBlank()) commander = "-";
				byRun.computeIfAbsent(new RunKey(r.getRun(), commander), k -> new ArrayList<>()).add(r);
			}
			List<RunKey> runOrder = new ArrayList<>(byRun.keySet());
			Comparator<Instant> newestFirst = Comparator.nullsLast(Comparator.reverseOrder());
			// Do not use .reversed() after thenComparingInt: that flips the *whole* comparator and would put
			// oldest runs first. Tie-break on higher run number only (newer trips within a commander).
			runOrder.sort(Comparator
				.<RunKey, Instant>comparing(k -> runRecencyForOrdering(byRun.get(k)), newestFirst)
				.thenComparing((RunKey a, RunKey b) -> Integer.compare(b.run, a.run))
				.thenComparing(k -> k.commander, String.CASE_INSENSITIVE_ORDER));
			Comparator<Instant> tsDesc = Comparator.nullsLast(Comparator.reverseOrder());
			Instant now = Instant.now();
			List<String> lines = new ArrayList<>();
			for (RunKey key : runOrder) {
				List<ProspectorLogRow> runRows = new ArrayList<>(byRun.get(key));
				runRows.sort(Comparator
					.comparing(ProspectorLogRow::getTimestamp, tsDesc)
					.thenComparing(ProspectorLogTableModel::compareAsteroidIdDescending));
				RunSummary summary = computeRunSummary(key, runRows, matcher, now);
				if (summary != null) {
					lines.add(summary.formatSummary());
				}
			}
			return lines;
		}

		/**
		 * Active (in-progress) runs only: canonical row has run start and no run end.
		 * Aggregates to one combined line per commander.
		 */
		static List<RunSummary> getActiveRunSummaries(List<ProspectorLogRow> rows, MaterialNameMatcher matcher) {
			if (rows == null || rows.isEmpty()) return List.of();
			Map<RunKey, List<ProspectorLogRow>> byRun = new HashMap<>();
			for (ProspectorLogRow r : rows) {
				if (r == null) continue;
				String commander = r.getCommanderName();
				if (commander == null || commander.isBlank()) commander = "-";
				byRun.computeIfAbsent(new RunKey(r.getRun(), commander), k -> new ArrayList<>()).add(r);
			}
			if (byRun.isEmpty()) return List.of();

			Instant now = Instant.now();
			// Aggregate active runs per commander so the scatter run line shows
			// a single combined performance line for each commander.
			class CommanderAgg {
				Instant start;
				Instant end;
				double totalTons;
				double totalCredits;
				int maxRunNumber;
			}
			Map<String, CommanderAgg> byCommander = new HashMap<>();

			Comparator<Instant> tsAsc = Comparator.nullsLast(Comparator.naturalOrder());
			for (Map.Entry<RunKey, List<ProspectorLogRow>> e : byRun.entrySet()) {
				List<ProspectorLogRow> runRows = new ArrayList<>(e.getValue());
				if (runRows.isEmpty()) continue;
				runRows.sort(Comparator.comparing(ProspectorLogRow::getTimestamp, tsAsc));

				// Determine canonical row, totals, and span for this run.
				Instant firstTs = null, lastTs = null;
				ProspectorLogRow canonical = canonicalRunMetaRow(runRows);
				if (canonical == null || canonical.getRunEndTime() != null) {
					continue;
				}
				double totalTons = 0.0;
				double totalCredits = 0.0;
				for (ProspectorLogRow r : runRows) {
					Instant ts = r.getTimestamp();
					if (ts != null) {
						if (firstTs == null || ts.isBefore(firstTs)) firstTs = ts;
						if (lastTs == null || ts.isAfter(lastTs)) lastTs = ts;
					}
					double diff = r.getDifference();
					totalTons += diff;
					if (matcher != null) {
						int price = matcher.lookupAvgSell(r.getMaterial(), r.getMaterial());
						totalCredits += diff * price;
					}
				}
				Instant start = (canonical.getRunStartTime() != null) ? canonical.getRunStartTime() : firstTs;
				Instant end = now;
				if (start == null || end == null || end.isBefore(start)) {
					continue;
				}

				String commander = e.getKey().commander;
				CommanderAgg agg = byCommander.computeIfAbsent(commander, k -> new CommanderAgg());
				if (agg.start == null || start.isBefore(agg.start)) {
					agg.start = start;
				}
				if (agg.end == null || end.isAfter(agg.end)) {
					agg.end = end;
				}
				agg.totalTons += totalTons;
				agg.totalCredits += totalCredits;
				if (e.getKey().run > agg.maxRunNumber) {
					agg.maxRunNumber = e.getKey().run;
				}
			}

			if (byCommander.isEmpty()) {
				return List.of();
			}

			List<RunSummary> activeSummaries = new ArrayList<>();
			for (Map.Entry<String, CommanderAgg> e : byCommander.entrySet()) {
				String commander = e.getKey();
				CommanderAgg agg = e.getValue();
				if (agg.start == null || agg.end == null || agg.end.isBefore(agg.start)) {
					continue;
				}
				double durationHours = (agg.end.toEpochMilli() - agg.start.toEpochMilli()) / (1000.0 * 3600.0);
				if (durationHours <= 0.0) {
					continue;
				}
				double tonsPerHour = agg.totalTons / durationHours;
				double creditsPerHour = agg.totalCredits / durationHours;
				String dateStr = agg.start.atZone(ZoneId.systemDefault()).format(DATE_FMT);
				activeSummaries.add(new RunSummary(agg.maxRunNumber, commander, dateStr, tonsPerHour, creditsPerHour));
			}

			if (activeSummaries.isEmpty()) {
				return List.of();
			}

			// Sort so the player's commander (from preferences) is always listed first,
			// then other commanders by run number descending. This keeps \"my\" run
			// line visually on top in both the table and scatter views.
			String playerCommander = OverlayPreferences.getMiningLogCommanderName();
			activeSummaries.sort((a, b) -> {
				boolean aIsPlayer = playerCommander != null
					&& !playerCommander.isBlank()
					&& a.commanderName.equalsIgnoreCase(playerCommander);
				boolean bIsPlayer = playerCommander != null
					&& !playerCommander.isBlank()
					&& b.commanderName.equalsIgnoreCase(playerCommander);
				if (aIsPlayer != bIsPlayer) {
					return aIsPlayer ? -1 : 1;
				}
				// Within each group, newest (highest run number) first.
				return Integer.compare(b.runNumber, a.runNumber);
			});

			return activeSummaries;
		}

		boolean isSummaryRow(int rowIndex) {
			if (rowIndex < 0 || rowIndex >= displayRows.size()) return false;
			return displayRows.get(rowIndex) instanceof RunSummary;
		}

		@Override
		public int getRowCount() {
			return displayRows.size();
		}

		@Override
		public int getColumnCount() {
			return COLUMNS.length;
		}

		@Override
		public String getColumnName(int column) {
			return COLUMNS[column];
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			if (rowIndex < 0 || rowIndex >= displayRows.size()) return "";
			Object item = displayRows.get(rowIndex);
			if (item instanceof RunSummary) {
				// Summary rows are drawn manually in the table's paintComponent; cells stay logically empty.
				return "";
			}
			ProspectorLogRow r = (ProspectorLogRow) item;
			switch (columnIndex) {
				case 0: return r.getCommanderName();
				case 1: return r.getRun();
				case 2:
					String aid = r.getAsteroidId();
					return (aid != null && !aid.isEmpty() && !"-".equals(aid)) ? aid : "";
				case 3:
					if (r.getTimestamp() == null) return "";
					return r.getTimestamp().atZone(ZoneId.systemDefault()).format(TS_FMT).toLowerCase(Locale.US);
				case 4: return r.getMaterial();
				case 5: return String.format(Locale.US, "%.2f", r.getPercent());
				case 6: return String.format(Locale.US, "%.2f", r.getBeforeAmount());
				case 7: return String.format(Locale.US, "%.2f", r.getAfterAmount());
				// Tons: display as whole tons (no decimal places)
				case 8: return String.format(Locale.US, "%d", Math.round(r.getDifference()));
				case 9:
					String core = r.getCoreType();
					return (core != null && !core.isEmpty() && !"-".equals(core)) ? core : "";
				case 10: return r.getDuds() == 0 ? "" : r.getDuds();
				case 11: {
					String[] sb = splitSystemAndBodyForDisplay(r.getFullBodyName());
					return sb[0];
				}
				case 12: {
					String[] sb = splitSystemAndBodyForDisplay(r.getFullBodyName());
					return sb[1];
				}
				default: return "";
			}
		}

		RunSummary getSummaryAt(int rowIndex) {
			if (rowIndex < 0 || rowIndex >= displayRows.size()) return null;
			Object item = displayRows.get(rowIndex);
			return (item instanceof RunSummary) ? (RunSummary) item : null;
		}
		private static String[] splitSystemAndBodyForDisplay(String fullBodyName) {
			return GoogleSheetsBackend.splitSystemAndBody(fullBodyName);
		}
	}

	/** Key for grouping prospector log rows by (run, commander). */
	private static final class RunKey {
		final int run;
		final String commander;

		RunKey(int run, String commander) {
			this.run = run;
			this.commander = commander;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof RunKey)) return false;
			RunKey other = (RunKey) o;
			return run == other.run && java.util.Objects.equals(commander, other.commander);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(run, commander);
		}
	}

	private static final class RunSummary {
		private final int runNumber;
		private final String commanderName;
		private final String dateStr;
		private final double tonsPerHour;
		private final double creditsPerHour;

		RunSummary(int runNumber, String commanderName, String dateStr, double tonsPerHour, double creditsPerHour) {
			this.runNumber = runNumber;
			this.commanderName = commanderName != null ? commanderName : "";
			this.dateStr = dateStr != null ? dateStr : "";
			this.tonsPerHour = tonsPerHour;
			this.creditsPerHour = creditsPerHour;
		}

		String formatSummary() {
			String crHr;
			if (creditsPerHour >= 1_000_000) {
				crHr = String.format(Locale.US, "%.0fM cr/hr", creditsPerHour / 1_000_000);
			} else if (creditsPerHour >= 1_000) {
				crHr = String.format(Locale.US, "%.0fk cr/hr", creditsPerHour / 1_000);
			} else {
				crHr = String.format(Locale.US, "%.0f cr/hr", creditsPerHour);
			}
			String datePart = dateStr.isEmpty() ? "" : dateStr + " - ";
			String commanderPart = (commanderName == null || commanderName.isBlank()) ? "" : commanderName + " - ";
			// Align numeric columns (t/hr and cr/hr) across commanders using fixed-width fields.
			return String.format(
				Locale.US,
				"Run %d - %s%s%7.1f t/hr - %12s",
				runNumber,
				commanderPart,
				datePart,
				tonsPerHour,
				crHr
			);
		}
	}
}