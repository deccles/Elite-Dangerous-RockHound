package org.dce.ed;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Collections;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.DefaultComboBoxModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
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

import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent.MaterialProportion;
import org.dce.ed.logreader.event.StartJumpEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.market.GalacticAveragePrices;
import org.dce.ed.mining.ProspectorLogBackend;
import org.dce.ed.mining.ProspectorLogBackendFactory;
import org.dce.ed.mining.ProspectorLogRow;
import org.dce.ed.market.MaterialNameMatcher;
import org.dce.ed.tts.PollyTtsCached;
import org.dce.ed.tts.TtsSprintf;
import org.dce.ed.ui.EdoUi;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Overlay tab: Mining
 *
 * Shows the most recent ProspectedAsteroid materials sorted by galactic average value.
 */
public class MiningTabPanel extends JPanel {

	private final TtsSprintf tts = new TtsSprintf(new PollyTtsCached());
	private String lastProspectorAnnouncementSig;
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
	private final JTable spreadsheetTable;
	private final ProspectorLogTableModel spreadsheetModel;
	private final JScrollPane spreadsheetScroller;
	private final ProspectorLogScatterPanel spreadsheetScatterPanel;
	private final ProspectorLogScatterWrapperPanel spreadsheetScatterWrapper;
	private final JPanel spreadsheetCardPanel;
	private static final int SPREADSHEET_REFRESH_MS = 45_000;
	private final Timer spreadsheetRefreshTimer;

	private final Map<String, Long> lastCargoTonsByName = new HashMap<>();

	/** Inventory tons by commodity (display name) at the time of the previous ProspectedAsteroid event. */
	private Map<String, Double> lastInventoryTonsAtProspector = new HashMap<>();

	/** Last seen proportion (percent) per material from the previous ProspectedAsteroid event; used for CSV so we log the rock's % when the mining actually happened. */
	private Map<String, Double> lastPercentByMaterialAtProspector = new HashMap<>();

	/** Current system and body for prospector log rows (updated from LocationEvent / StatusEvent). */
	private volatile String currentSystemName = "";
	private volatile String currentBodyName = "";

	private final TableScanState prospectorScan;
private final TableScanState cargoScan;
private final JLayer<JTable> prospectorLayer;
private final JLayer<JTable> cargoLayer;


	private Font uiFont;
	private JLabel prospectorLabel;

	private static final int VISIBLE_ROWS = 10;

	// Row colors for mining tables.
	private static final Color CORE_COLOR = EdoUi.User.CORE_BLUE;
	private static final Color NON_CORE_GREEN = EdoUi.User.SUCCESS;

	public MiningTabPanel(GalacticAveragePrices prices) {
		this(prices, null);
	}

	public MiningTabPanel(GalacticAveragePrices prices, BooleanSupplier isDockedSupplier) {
		super(new BorderLayout());
		this.prices = prices;
		this.isDockedSupplier = isDockedSupplier;

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
			th.setUI(org.dce.ed.ui.TransparentTableHeaderUI.createUI(th));
			th.setOpaque(!OverlayPreferences.isOverlayTransparent());
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
		};
		spreadsheetTable.setDefaultEditor(Object.class, null);
		spreadsheetTable.setFocusable(false);
		spreadsheetTable.setRowSelectionAllowed(false);
		spreadsheetTable.setOpaque(false);
		spreadsheetTable.setBackground(EdoUi.Internal.TRANSPARENT);
		spreadsheetTable.setForeground(EdoUi.User.MAIN_TEXT);
		DefaultTableCellRenderer spreadCellRenderer = new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				if (c instanceof JComponent) {
					((JComponent) c).setOpaque(false);
				}
				c.setBackground(EdoUi.Internal.TRANSPARENT);
				if (spreadsheetModel != null && spreadsheetModel.isSummaryRow(row)) {
					c.setFont(c.getFont().deriveFont(Font.BOLD).deriveFont(c.getFont().getSize2D() + 1f));
				}
				return c;
			}
		};
		spreadsheetTable.setDefaultRenderer(Object.class, spreadCellRenderer);
		spreadsheetTable.setTableHeader(new org.dce.ed.ui.TransparentTableHeader(spreadsheetTable.getColumnModel()));
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
		JToggleButton tableViewBtn = new JToggleButton("Table", true);
		JToggleButton scatterViewBtn = new JToggleButton("Scatter", false);
		tableViewBtn.setOpaque(false);
		scatterViewBtn.setOpaque(false);
		ButtonGroup spreadsheetViewGroup = new ButtonGroup();
		spreadsheetViewGroup.add(tableViewBtn);
		spreadsheetViewGroup.add(scatterViewBtn);
		tableViewBtn.addActionListener(e -> ((CardLayout) spreadsheetCardPanel.getLayout()).show(spreadsheetCardPanel, "table"));
		scatterViewBtn.addActionListener(e -> ((CardLayout) spreadsheetCardPanel.getLayout()).show(spreadsheetCardPanel, "scatter"));
		JPanel spreadsheetToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		spreadsheetToolbar.setOpaque(false);
		spreadsheetToolbar.add(tableViewBtn);
		spreadsheetToolbar.add(scatterViewBtn);
		spreadsheetToolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
		spreadsheetLabel = new JLabel("Prospector log");
		spreadsheetLabel.setForeground(EdoUi.User.MAIN_TEXT);
		spreadsheetLabel.setOpaque(false);
		spreadsheetLabel.setBackground(EdoUi.Internal.TRANSPARENT);
		spreadsheetLabel.setFont(base.deriveFont(Font.BOLD, OverlayPreferences.getUiFontSize() + 4));
		spreadsheetLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		Dimension spreadPref = spreadsheetLabel.getPreferredSize();
		spreadsheetLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, spreadPref.height));

		// Leave about 10 rows for each table.
		updateScrollerHeights();

		JPanel centerPanel = new JPanel();
		centerPanel.setOpaque(false);
		centerPanel.setBackground(EdoUi.Internal.TRANSPARENT);
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
		// Order: Inventory, then Prospector, then Spreadsheet
		centerPanel.add(inventoryLabel);
		centerPanel.add(Box.createVerticalStrut(2));
		centerPanel.add(cargoScroller);
		centerPanel.add(Box.createVerticalStrut(8));
		centerPanel.add(prospectorLabel);
		centerPanel.add(Box.createVerticalStrut(4));
		centerPanel.add(materialsScroller);
		centerPanel.add(Box.createVerticalStrut(8));
		centerPanel.add(spreadsheetLabel);
		centerPanel.add(Box.createVerticalStrut(2));
		centerPanel.add(spreadsheetToolbar);
		centerPanel.add(Box.createVerticalStrut(2));
		centerPanel.add(spreadsheetCardPanel);

		add(centerPanel, BorderLayout.CENTER);

		refreshSpreadsheetFromBackend();
		spreadsheetRefreshTimer = new Timer(SPREADSHEET_REFRESH_MS, e -> refreshSpreadsheetFromBackend());
		spreadsheetRefreshTimer.setRepeats(true);
		spreadsheetRefreshTimer.start();

		CargoMonitor.getInstance().addListener(snap -> SwingUtilities.invokeLater(() -> updateFromCargoSnapshot(snap)));
		updateFromCargoSnapshot(CargoMonitor.getInstance().getSnapshot());


		applyUiFontPreferences();
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
					return NON_CORE_GREEN;
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

		updateScrollerHeights();

		revalidate();
		repaint();
	}



	private void updateScrollerHeights() {
		updateScrollerHeight(materialsScroller, table);
		updateScrollerHeight(cargoScroller, cargoTable);
		updateScrollerHeight(spreadsheetScroller, spreadsheetTable);
		if (spreadsheetScatterPanel != null && spreadsheetScroller != null) {
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

		int h = (tbl.getRowHeight() * VISIBLE_ROWS) + headerH;

		scroller.setPreferredSize(new Dimension(Integer.MAX_VALUE, h));
		scroller.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
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
				return;
			}

			JsonObject cargoObj = snap.getCargoJson();
			List<Row> rows = buildRowsFromCargo(cargoObj);
			Set<Integer> changedModelRows = computeChangedInventoryModelRows(rows);
			cargoModel.setRows(withTotalRow(rows));
			cargoScan.startInventoryScan(cargoLayer, changedModelRows);
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
	 * Builds a map of commodity display name -> total tons from Cargo.json Inventory.
	 * Used to compare inventory at each ProspectorEvent and compute ton deltas for CSV logging.
	 */
	private Map<String, Double> buildInventoryTonsFromCargo(JsonObject cargo) {
		return buildInventoryTonsFromCargo(cargo, this::toUiName);
	}

	/**
	 * Static variant for unit tests; nameResolver maps raw item name to display name.
	 */
	static Map<String, Double> buildInventoryTonsFromCargo(JsonObject cargo, Function<String, String> nameResolver) {
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
			String shownName = (localizedName != null && !localizedName.isBlank()) ? localizedName : nameResolver.apply(rawName);
			out.merge(shownName, (double) count, Double::sum);
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
	 * Called on FSD jump: flush any pending mining gains to CSV (using last-seen percent), then reset
	 * so the next prospector scan is treated like the first (new area).
	 */
	public void onStartJump(StartJumpEvent event) {
		Instant ts = (event != null && event.getTimestamp() != null) ? event.getTimestamp() : Instant.now();
		CargoMonitor.Snapshot snap = CargoMonitor.getInstance().getSnapshot();
		Map<String, Double> currentInventory = buildInventoryTonsFromCargo(snap != null ? snap.getCargoJson() : null);
		Set<String> materials = new HashSet<>(lastInventoryTonsAtProspector.keySet());
		materials.addAll(currentInventory.keySet());
		appendProspectorCsvRows(ts, currentInventory, materials, null);
		boolean wasInMiningRun = !lastInventoryTonsAtProspector.isEmpty();
		lastInventoryTonsAtProspector = new HashMap<>();
		lastPercentByMaterialAtProspector = new HashMap<>();
		// Only count a new run when we're actually leaving the area (we had prospector state). An asteroid isn't a run.
		if (wasInMiningRun) {
			OverlayPreferences.incrementMiningLogRunCounter();
		}
	}

	/** Update cached location from Location event (system + body). */
	public void updateFromLocation(LocationEvent event) {
		if (event == null) {
			return;
		}
		String sys = event.getStarSystem();
		String body = event.getBody();
		currentSystemName = (sys != null) ? sys : "";
		currentBodyName = (body != null) ? body : "";
	}

	/** Update cached body name from Status event. */
	public void updateFromStatus(StatusEvent event) {
		if (event == null) {
			return;
		}
		String body = event.getBodyName();
		if (body != null) {
			currentBodyName = body;
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
		}
		appendProspectorCsvRows(ts, currentInventory, materials, fallbackPct);
	}

	/** Write log rows for materials that increased; uses lastInventoryTonsAtProspector and lastPercentByMaterialAtProspector. */
	private void appendProspectorCsvRows(Instant ts, Map<String, Double> currentInventory, Set<String> materialsToConsider, Map<String, Double> fallbackPercentByMaterial) {
		if (materialsToConsider == null || materialsToConsider.isEmpty()) {
			return;
		}
		// Only write when undocked (mining happens in the ring, not while docked)
		if (isDockedSupplier != null && isDockedSupplier.getAsBoolean()) {
			return;
		}
		String sys = currentSystemName != null ? currentSystemName : "";
		String body = currentBodyName != null ? currentBodyName : "";
		String fullBodyName = sys.isEmpty() && body.isEmpty() ? "" : (sys.isEmpty() ? body : (body.isEmpty() ? sys : sys + " > " + body));
		int run = OverlayPreferences.getMiningLogRunCounter();
		String commander = OverlayPreferences.getMiningLogCommanderName();
		if (commander == null || commander.isBlank()) {
			commander = "-";
		}
		List<ProspectorLogRow> rows = new ArrayList<>();
		for (String material : materialsToConsider) {
			if (material == null || material.isBlank()) {
				material = "-";
			}
			double pct = lastPercentByMaterialAtProspector.getOrDefault(material,
				fallbackPercentByMaterial != null ? fallbackPercentByMaterial.getOrDefault(material, 0.0) : 0.0);
			// Do not log materials with zero yield (e.g. prospector shot at bad asteroid and we moved on)
			if (pct <= 0.0) {
				continue;
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
			rows.add(new ProspectorLogRow(run, fullBodyName, ts, material, pct, beforeAdjusted, afterAdjusted, difference, commander));
		}
		if (rows.isEmpty()) {
			return;
		}
		try {
			ProspectorLogBackend backend = ProspectorLogBackendFactory.create();
			backend.appendRows(rows);
			refreshSpreadsheetFromBackend();
		} catch (Exception e) {
			// don't break UI on log failure
		}
	}

	/** Load rows from backend and update spreadsheet table on EDT. */
	void refreshSpreadsheetFromBackend() {
		new javax.swing.SwingWorker<List<ProspectorLogRow>, Void>() {
			@Override
			protected List<ProspectorLogRow> doInBackground() {
				try {
					return ProspectorLogBackendFactory.create().loadRows();
				} catch (Exception e) {
					return Collections.emptyList();
				}
			}
			@Override
			protected void done() {
				try {
					List<ProspectorLogRow> rows = get();
					if (rows != null && spreadsheetModel != null) {
						spreadsheetModel.setRows(rows, matcher);
						if (spreadsheetScatterWrapper != null) {
							spreadsheetScatterWrapper.setRows(rows);
						}
					}
				} catch (Exception ignored) {
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
		if (event == null) {
			model.setRows(List.of());
			headerLabel.setText("Mining (latest prospector)");
			return;
		}

		// Snapshot current cargo so we can log inventory deltas since last ProspectorEvent (CargoMonitor already polls)
		CargoMonitor.Snapshot cargoSnap = CargoMonitor.getInstance().getSnapshot();
		Map<String, Double> currentInventory = buildInventoryTonsFromCargo(cargoSnap != null ? cargoSnap.getCargoJson() : null);

		boolean isFirstProspector = lastInventoryTonsAtProspector.isEmpty();
		if (!isFirstProspector) {
			appendProspectorCsv(event, currentInventory);
		}
		// Update saved values from current inventory and this scan (first time: seed from inventory instead of zero)
		lastInventoryTonsAtProspector = new HashMap<>(currentInventory);
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

		String motherlode = event.getMotherlodeMaterial();
		String content = event.getContent();

		List<Row> rows = new ArrayList<>();

		double totalTons = estimateTotalTons(content);
		for (MaterialProportion mp : event.getMaterials()) {
			if (mp == null || mp.getName() == null) {
				continue;
			}

			String rawName = mp.getName();
			String shownName = toUiName(rawName);

			int avg = lookupAvgSell(rawName, shownName);
			double tons = (mp.getProportion() / 100.0) * totalTons;
			double value = tons * avg;

			rows.add(new Row(shownName, mp.getProportion(), avg, tons, value));
		}

		if (motherlode != null && !motherlode.isBlank()) {
			String shownName = toUiName(motherlode);

			int avg = lookupAvgSell(motherlode, shownName);
			double tons = OverlayPreferences.getMiningEstimateTonsCore();
			double value = tons * avg;

			rows.add(new Row(shownName + " (Core)", Double.NaN, avg, tons, value, true));
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

	/**
	 * Use the INARA CSV display name if present; fall back to a friendly formatting of the journal token.
	 * This is what fixes "Crystals" (and lots of other tokens) showing inconsistently.
	 */    private String toUiName(String s) {
		 if (s == null || s.isBlank()) {
			 return "";
		 }

		 String norm = GalacticAveragePrices.normalizeMaterialKey(s);
		 if ("lowtemperaturediamonds".equals(norm)) {
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


	 private static double estimateTotalTons(String content) {
		 if (content == null) {
			 return OverlayPreferences.getMiningEstimateTonsMedium();
		 }
		 String c = content.trim().toLowerCase(Locale.US);
		 if (c.equals("high")) {
			 return OverlayPreferences.getMiningEstimateTonsHigh();
		 }
		 if (c.equals("low")) {
			 return OverlayPreferences.getMiningEstimateTonsLow();
		 }
		 return OverlayPreferences.getMiningEstimateTonsMedium();
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
			 boolean transparent = OverlayPreferences.isOverlayTransparent();
			 label.setOpaque(!transparent);
			 label.setBackground(transparent ? EdoUi.Internal.TRANSPARENT : EdoUi.User.BACKGROUND);
			 label.setForeground(EdoUi.Internal.tableHeaderForeground());
			 label.setFont(label.getFont().deriveFont(Font.BOLD));
			 label.setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.RIGHT);
			 label.setBorder(transparent
					 ? new EmptyBorder(2, 4, 0, 4)  // no MatteBorder when transparent so no opaque paint
					 : new CompoundBorder(
							 new MatteBorder(2, 0, 0, 0, EdoUi.Internal.tableHeaderTopBorder()),
							 new EmptyBorder(0, 4, 0, 4)));
			 return label;
		 }

		 @Override
		 protected void paintComponent(Graphics g) {
			 boolean transparent = OverlayPreferences.isOverlayTransparent();
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

		ProspectorLogScatterWrapperPanel(ProspectorLogScatterPanel scatterPanel) {
			super(new BorderLayout());
			this.scatterPanel = scatterPanel;
			modeCombo = new JComboBox<>(new String[] { MODE_ALL, MODE_BY_RUN, MODE_BY_COMMANDER });
			modeCombo.setOpaque(false);
			modeCombo.setBackground(EdoUi.Internal.TRANSPARENT);
			secondaryCombo = new JComboBox<>();
			secondaryCombo.setOpaque(false);
			secondaryCombo.setBackground(EdoUi.Internal.TRANSPARENT);
			secondaryCombo.setVisible(false);
			modeCombo.addActionListener(e -> onModeChanged());
			secondaryCombo.addActionListener(e -> onSecondaryChanged());
			JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
			top.setOpaque(false);
			top.setBackground(EdoUi.Internal.TRANSPARENT);
			top.add(new JLabel("View:"));
			top.add(modeCombo);
			top.add(secondaryCombo);
			add(top, BorderLayout.NORTH);
			add(scatterPanel, BorderLayout.CENTER);
		}

		void setRows(List<ProspectorLogRow> rows) {
			if (rows == null) rows = List.of();
			scatterPanel.setRows(rows);
			List<Integer> runs = rows.stream().mapToInt(ProspectorLogRow::getRun).distinct().sorted().boxed().toList();
			List<String> commanders = rows.stream().map(ProspectorLogRow::getCommanderName).distinct().sorted().toList();
			String mode = (String) modeCombo.getSelectedItem();
			modeCombo.setModel(new DefaultComboBoxModel<>(new String[] { MODE_ALL, MODE_BY_RUN, MODE_BY_COMMANDER }));
			modeCombo.setSelectedItem(mode != null ? mode : MODE_ALL);
			secondaryCombo.removeAllItems();
			if (MODE_BY_RUN.equals(mode)) {
				for (Integer r : runs) secondaryCombo.addItem(String.valueOf(r));
				if (!runs.isEmpty()) {
					scatterPanel.setSelectedRun(runs.get(0));
					secondaryCombo.setSelectedIndex(0);
				}
				secondaryCombo.setVisible(true);
			} else if (MODE_BY_COMMANDER.equals(mode)) {
				for (String c : commanders) secondaryCombo.addItem(c != null ? c : "");
				if (!commanders.isEmpty()) {
					scatterPanel.setSelectedCommander(commanders.get(0));
					secondaryCombo.setSelectedIndex(0);
				}
				secondaryCombo.setVisible(true);
			} else {
				secondaryCombo.setVisible(false);
			}
			onModeChanged();
		}

		private void onModeChanged() {
			String mode = (String) modeCombo.getSelectedItem();
			scatterPanel.setFilterMode(MODE_BY_RUN.equals(mode) ? ProspectorLogScatterPanel.FilterMode.BY_RUN
				: MODE_BY_COMMANDER.equals(mode) ? ProspectorLogScatterPanel.FilterMode.BY_COMMANDER
				: ProspectorLogScatterPanel.FilterMode.ALL);
			if (MODE_BY_RUN.equals(mode)) {
				Object sel = secondaryCombo.getSelectedItem();
				int run = 1;
				if (sel != null) try { run = Integer.parseInt(sel.toString()); } catch (NumberFormatException ignored) { }
				scatterPanel.setSelectedRun(run);
				secondaryCombo.setVisible(true);
			} else if (MODE_BY_COMMANDER.equals(mode)) {
				Object sel = secondaryCombo.getSelectedItem();
				scatterPanel.setSelectedCommander(sel != null ? sel.toString() : "");
				secondaryCombo.setVisible(true);
			} else {
				secondaryCombo.setVisible(false);
			}
		}

		private void onSecondaryChanged() {
			String mode = (String) modeCombo.getSelectedItem();
			if (MODE_BY_RUN.equals(mode)) {
				Object sel = secondaryCombo.getSelectedItem();
				if (sel != null) try { scatterPanel.setSelectedRun(Integer.parseInt(sel.toString())); } catch (NumberFormatException ignored) { }
			} else if (MODE_BY_COMMANDER.equals(mode)) {
				Object sel = secondaryCombo.getSelectedItem();
				scatterPanel.setSelectedCommander(sel != null ? sel.toString() : "");
			}
		}
	}

	/** Scatter plot panel: X = Percentage, Y = Actual (difference). Supports filter by run/commander and color by commander. */
	private static final class ProspectorLogScatterPanel extends JPanel {
		private static final int PAD = 24;
		private static final double POINT_RADIUS = 3.0;
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
		enum FilterMode { ALL, BY_RUN, BY_COMMANDER }
		private List<ProspectorLogRow> rows = new ArrayList<>();
		private FilterMode filterMode = FilterMode.ALL;
		private int selectedRun = 1;
		private String selectedCommander = "";

		void setRows(List<ProspectorLogRow> rows) {
			this.rows = (rows != null) ? new ArrayList<>(rows) : new ArrayList<>();
			repaint();
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

		private List<ProspectorLogRow> filteredRows() {
			if (rows.isEmpty()) return rows;
			switch (filterMode) {
				case BY_RUN:
					return rows.stream().filter(r -> r.getRun() == selectedRun).toList();
				case BY_COMMANDER:
					return rows.stream().filter(r -> selectedCommander.equals(r.getCommanderName())).toList();
				default:
					return rows;
			}
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			List<ProspectorLogRow> toPlot = filteredRows();
			if (toPlot.isEmpty()) {
				return;
			}
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
			int w = getWidth();
			int h = getHeight();
			if (w <= 2 * PAD || h <= 2 * PAD) {
				g2.dispose();
				return;
			}
			double minPct = Double.MAX_VALUE, maxPct = -Double.MAX_VALUE;
			double minAct = Double.MAX_VALUE, maxAct = -Double.MAX_VALUE;
			for (ProspectorLogRow r : toPlot) {
				double p = r.getPercent();
				double a = r.getDifference();
				if (p < minPct) minPct = p;
				if (p > maxPct) maxPct = p;
				if (a < minAct) minAct = a;
				if (a > maxAct) maxAct = a;
			}
			if (minPct >= maxPct) maxPct = minPct + 1.0;
			if (minAct >= maxAct) maxAct = minAct + 1.0;
			int plotW = w - 2 * PAD;
			int plotH = h - 2 * PAD;
			g2.setColor(EdoUi.User.MAIN_TEXT);
			g2.drawRect(PAD, PAD, plotW, plotH);
			g2.setFont(g2.getFont().deriveFont(10f));
			g2.drawString("Percentage", PAD + plotW / 2 - 25, h - 4);
			g2.drawString("Actual", 4, PAD + plotH / 2);
			List<String> commanderOrder = filterMode == FilterMode.ALL
				? toPlot.stream().map(ProspectorLogRow::getCommanderName).distinct().toList()
				: List.of();
			Map<String, Color> commanderColor = new HashMap<>();
			for (int i = 0; i < commanderOrder.size(); i++) {
				commanderColor.put(commanderOrder.get(i), COMMANDER_PALETTE[i % COMMANDER_PALETTE.length]);
			}
			for (ProspectorLogRow r : toPlot) {
				Color pointColor = filterMode == FilterMode.ALL && !commanderOrder.isEmpty()
					? commanderColor.getOrDefault(r.getCommanderName(), EdoUi.User.VALUABLE)
					: EdoUi.User.VALUABLE;
				g2.setColor(pointColor);
				double p = r.getPercent();
				double a = r.getDifference();
				double nx = (p - minPct) / (maxPct - minPct);
				double ny = 1.0 - (a - minAct) / (maxAct - minAct);
				int x = PAD + (int) (nx * plotW);
				int y = PAD + (int) (ny * plotH);
				g2.fillOval((int) (x - POINT_RADIUS), (int) (y - POINT_RADIUS), (int) (2 * POINT_RADIUS), (int) (2 * POINT_RADIUS));
			}
			g2.dispose();
		}
	}

	/** Table model for prospector log rows: Run, Timestamp, Type, Percentage, Before Amount, After Amount, Actual, Body, Commander. Supports run summary rows. */
	private static final class ProspectorLogTableModel extends AbstractTableModel {
		private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss", Locale.US);
		private static final String[] COLUMNS = { "Run", "Timestamp", "Type", "Percentage", "Before Amount", "After Amount", "Actual", "Body", "Commander" };
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
			Map<Integer, List<ProspectorLogRow>> byRun = new HashMap<>();
			for (ProspectorLogRow r : rows) {
				byRun.computeIfAbsent(r.getRun(), k -> new ArrayList<>()).add(r);
			}
			// Most recent first: runs descending, then within each run by timestamp descending
			List<Integer> runOrder = new ArrayList<>(byRun.keySet());
			runOrder.sort(Comparator.reverseOrder());
			Comparator<Instant> tsDesc = Comparator.nullsLast(Comparator.reverseOrder());
			for (Integer runNum : runOrder) {
				List<ProspectorLogRow> runRows = new ArrayList<>(byRun.get(runNum));
				runRows.sort(Comparator.comparing(ProspectorLogRow::getTimestamp, tsDesc));
				RunSummary summary = computeRunSummary(runNum, runRows, matcher);
				if (summary != null) {
					out.add(summary);
				}
				out.addAll(runRows);
			}
			return out;
		}

		private static RunSummary computeRunSummary(int runNum, List<ProspectorLogRow> runRows, MaterialNameMatcher matcher) {
			if (runRows == null || runRows.isEmpty()) return null;
			Instant first = null, last = null;
			double totalTons = 0.0;
			double totalCredits = 0.0;
			for (ProspectorLogRow r : runRows) {
				if (r.getTimestamp() != null) {
					if (first == null || r.getTimestamp().isBefore(first)) first = r.getTimestamp();
					if (last == null || r.getTimestamp().isAfter(last)) last = r.getTimestamp();
				}
				double diff = r.getDifference();
				totalTons += diff;
				if (matcher != null) {
					int price = matcher.lookupAvgSell(r.getMaterial(), r.getMaterial());
					totalCredits += diff * price;
				}
			}
			double durationHours = 0.0;
			if (first != null && last != null && !last.isBefore(first)) {
				durationHours = (last.toEpochMilli() - first.toEpochMilli()) / (1000.0 * 3600.0);
			}
			double tonsPerHour = durationHours > 0 ? totalTons / durationHours : 0.0;
			double creditsPerHour = durationHours > 0 ? totalCredits / durationHours : 0.0;
			return new RunSummary(runNum, tonsPerHour, creditsPerHour);
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
			if (item instanceof RunSummary s) {
				return columnIndex == 0 ? s.formatSummary() : "";
			}
			ProspectorLogRow r = (ProspectorLogRow) item;
			switch (columnIndex) {
				case 0: return r.getRun();
				case 1: return r.getTimestamp() != null ? r.getTimestamp().atZone(ZoneId.systemDefault()).format(TS_FMT) : "";
				case 2: return r.getMaterial();
				case 3: return String.format(Locale.US, "%.2f", r.getPercent());
				case 4: return String.format(Locale.US, "%.2f", r.getBeforeAmount());
				case 5: return String.format(Locale.US, "%.2f", r.getAfterAmount());
				case 6: return String.format(Locale.US, "%.2f", r.getDifference());
				case 7: return r.getFullBodyName();
				case 8: return r.getCommanderName();
				default: return "";
			}
		}
	}

	private static final class RunSummary {
		private final int runNumber;
		private final double tonsPerHour;
		private final double creditsPerHour;

		RunSummary(int runNumber, double tonsPerHour, double creditsPerHour) {
			this.runNumber = runNumber;
			this.tonsPerHour = tonsPerHour;
			this.creditsPerHour = creditsPerHour;
		}

		String formatSummary() {
			return String.format(Locale.US, "Run %d: %.1f t/hr · %.0f cr/hr", runNumber, tonsPerHour, creditsPerHour);
		}
	}
}