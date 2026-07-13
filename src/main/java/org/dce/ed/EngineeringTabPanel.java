package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.RowSorter;
import javax.swing.ScrollPaneConstants;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.dce.ed.engineering.BlueprintGrade;
import org.dce.ed.engineering.EngineeringDatabase;
import org.dce.ed.engineering.EngineeringGradeProgress;
import org.dce.ed.engineering.EngineeringGoal;
import org.dce.ed.engineering.EngineeringGoalProgress;
import org.dce.ed.engineering.EngineeringInventoryTracker;
import org.dce.ed.engineering.EngineeringPlanner;
import org.dce.ed.engineering.GoalReadiness;
import org.dce.ed.engineering.MaterialTradePlanner;
import org.dce.ed.engineering.ShoppingListRow;
import org.dce.ed.engineering.TradeSuggestion;
import org.dce.ed.engineering.TradeTargetGroup;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.session.EngineeringSessionData;
import org.dce.ed.session.EngineeringSessionData.EngineeringGoalPersisted;
import org.dce.ed.ui.EdoMiningSplitPaneUi;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.HoverClickPoller;
import org.dce.ed.ui.OverlayScrollPaneSupport;
import org.dce.ed.ui.PassThroughScrollSupport;
import org.dce.ed.ui.OverlayCheckBoxStyle;
import org.dce.ed.ui.OverlayComponentColorAnalyzer;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.OverlayTransparentChrome;
import org.dce.ed.ui.PencilIcon;
import org.dce.ed.ui.TableCellHoverClickSupport;
import org.dce.ed.ui.TableCellHoverToggleSupport;
import org.dce.ed.ui.TableCellToolTipSupport;
import org.dce.ed.ui.TableHeaderSortSupport;
import org.dce.ed.ui.TransparentTableHeader;
import org.dce.ed.ui.TransparentTableHeaderUI;

/**
 * Engineering tab: goals, shopping list, and trade suggestions.
 */
public class EngineeringTabPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int HOVER_CLICK_DELAY_MS = 500;
    private static final int HEADER_SORT_HOVER_MS = 500;

    private static final int COL_MATERIAL = 0;
    private static final int COL_TYPE = 1;
    private static final int COL_NEED = 2;
    private static final int COL_HAVE = 3;
    private static final int COL_SHORT = 4;

    private static final int COL_GOAL_INCLUDE = 0;
    private static final int COL_GOAL_BLUEPRINT = 1;
    private static final int COL_GOAL_TARGET = 2;
    private static final int COL_GOAL_EXP = 3;
    private static final int COL_GOAL_STATUS = 4;
    private static final int COL_GOAL_EDIT = 5;

    /** Status column is capped at this width; longer values ellipsize with a tooltip. */
    private static final String GOAL_STATUS_WIDTH_CAP_TEXT = "Ready w/ trades";

    private static final int COL_TRADE_MATERIAL = 0;
    private static final int COL_TRADE_NEED = 1;
    private static final int COL_TRADE_SUGGESTION = 2;
    private static final int TRADE_NEED_CELL_LEFT_PAD = 10;
    private static final int TRADE_NEED_CELL_RIGHT_PAD = 22;
    private static final int TRADE_SUGGESTION_CELL_LEFT_PAD = 18;

    private final BooleanSupplier passThroughEnabledSupplier;

    private final EngineeringDatabase database = EngineeringDatabase.getInstance();
    private final EngineeringInventoryTracker inventoryTracker = new EngineeringInventoryTracker();
    private final EngineeringPlanner planner = new EngineeringPlanner(database);
    private final MaterialTradePlanner tradePlanner = new MaterialTradePlanner(database);

    private final List<EngineeringGoal> goals = new ArrayList<>();
    private final List<GoalReadiness> goalReadiness = new ArrayList<>();
    private final List<String> goalStatusText = new ArrayList<>();
    private Runnable sessionStateChangeCallback;

    private final JLabel materialsEmptyLabel = new JLabel();
    private final JLabel tradeEmptyLabel = new JLabel();
    private final GoalsTableModel goalsModel = new GoalsTableModel();
    private final JTable goalsTable = createOverlayTable(goalsModel);
    private final ShoppingTableModel shoppingModel = new ShoppingTableModel();
    private final JTable shoppingTable = createOverlayTable(shoppingModel);
    private final TradeTableModel tradeModel = new TradeTableModel();
    private final JTable tradeTable = createOverlayTable(tradeModel);
    private JScrollPane goalsScroll;
    private TableRowSorter<ShoppingTableModel> shoppingSorter;
    private JScrollPane shoppingScroll;
    private JScrollPane tradeScroll;
    private JSplitPane mainSplit;
    private JSplitPane lowerSplit;

    public EngineeringTabPanel(BooleanSupplier passThroughEnabledSupplier) {
        super(new BorderLayout(6, 6));
        this.passThroughEnabledSupplier = passThroughEnabledSupplier;
        setOpaque(false);
        setFocusable(false);
        setRequestFocusEnabled(false);
        setBorder(new EmptyBorder(4, 6, 4, 6));

        Font base = OverlayPreferences.getUiFont();
        int fontSize = OverlayPreferences.getUiFontSize();

        styleMutedLabel(materialsEmptyLabel, base, fontSize);
        styleMutedLabel(tradeEmptyLabel, base, fontSize);

        configureTable(goalsTable, base, new EllipsisTextCellRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_BLUEPRINT).setCellRenderer(new BlueprintNameCellRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_EXP).setCellRenderer(new EllipsisTextCellRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_TARGET).setCellRenderer(new EllipsisTextCellRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_STATUS).setCellRenderer(new GoalStatusCellRenderer());
        goalsTable.setDefaultRenderer(Boolean.class, new GoalIncludeCellRenderer());
        JCheckBox includeEditor = new JCheckBox();
        OverlayCheckBoxStyle.apply(includeEditor);
        goalsTable.getColumnModel().getColumn(0).setCellRenderer(goalsTable.getDefaultRenderer(Boolean.class));
        goalsTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(includeEditor) {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellEditorComponent(JTable table, Object value,
                    boolean isSelected, int row, int column) {
                Component c = super.getTableCellEditorComponent(table, value, isSelected, row, column);
                if (c instanceof JCheckBox checkBox) {
                    OverlayCheckBoxStyle.apply(checkBox);
                }
                return c;
            }
        });
        goalsTable.getColumnModel().getColumn(0).setMaxWidth(36);
        TableColumn editCol = goalsTable.getColumnModel().getColumn(COL_GOAL_EDIT);
        editCol.setMaxWidth(32);
        editCol.setMinWidth(28);
        editCol.setPreferredWidth(28);
        goalsTable.getColumnModel().getColumn(COL_GOAL_EDIT).setCellRenderer(new GoalEditCellRenderer());
        goalsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                int row = goalsTable.rowAtPoint(e.getPoint());
                int col = goalsTable.columnAtPoint(e.getPoint());
                if (row >= 0 && col >= 0
                        && goalsTable.convertColumnIndexToModel(col) == COL_GOAL_EDIT) {
                    openEditGoalDialog(goalsTable.convertRowIndexToModel(row));
                }
            }
        });
        TableCellHoverClickSupport.install(
                goalsTable,
                COL_GOAL_EDIT,
                passThroughEnabledSupplier,
                HOVER_CLICK_DELAY_MS,
                this::openEditGoalDialog);
        TableCellHoverToggleSupport.install(
                goalsTable,
                0,
                passThroughEnabledSupplier,
                HOVER_CLICK_DELAY_MS,
                modelRow -> {
                    if (modelRow < 0 || modelRow >= goals.size()) {
                        return;
                    }
                    boolean include = goals.get(modelRow).isIncludeInPlanning();
                    goalsModel.setValueAt(!include, modelRow, 0);
                });
        configureTable(shoppingTable, base, new ShoppingCellRenderer());
        configureTable(tradeTable, base, new TradeCellRenderer());
        configureTradeNeedColumn();
        tradeTable.setRowHeight(Math.max(18, fontSize + 6));

        goalsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        shoppingTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tradeTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        shoppingSorter = createShoppingSorter();
        installSortableTable(shoppingTable, shoppingSorter);

        JPanel goalsPanel = buildGoalsPanel(base, fontSize);
        JPanel materialsPanel = buildMaterialsPanel(base, fontSize);
        JPanel tradePanel = buildTradePanel(base, fontSize);

        lowerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, materialsPanel, tradePanel);
        configureSplitPane(lowerSplit, 0.55);
        lowerSplit.setFocusable(false);
        EdoMiningSplitPaneUi.install(lowerSplit);

        mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, goalsPanel, lowerSplit);
        configureSplitPane(mainSplit, 0.28);
        mainSplit.setFocusable(false);
        EdoMiningSplitPaneUi.install(mainSplit);

        add(mainSplit, BorderLayout.CENTER);

        SwingUtilities.invokeLater(() -> {
            stripAllEngineeringScrollChrome();
            OverlayTransparentChrome.applyToSubtree(this);
            logComponentColorAuditIfRequested();
            EdoMiningSplitPaneUi.applyDividerTheme(mainSplit);
            EdoMiningSplitPaneUi.applyDividerTheme(lowerSplit);
        });

        inventoryTracker.setChangeCallback(this::scheduleRefresh);
        installEngineeringTableLayoutListeners();
        refreshUi();
    }

    private JPanel buildGoalsPanel(Font base, int fontSize) {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setOpaque(false);

        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.setOpaque(false);
        header.add(sectionHeader("Goals", base, fontSize), BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        JButton addBtn = new JButton("Add a goal");
        OverlayOutlineButtonStyle.applyChip(addBtn, base, false);
        addBtn.addActionListener(e -> openAddGoalDialog());
        HoverClickPoller.register(addBtn, HOVER_CLICK_DELAY_MS, this::openAddGoalDialog, passThroughEnabledSupplier);
        JButton removeBtn = new JButton("Remove");
        OverlayOutlineButtonStyle.applyChip(removeBtn, base, false);
        removeBtn.addActionListener(e -> removeSelectedGoal());
        HoverClickPoller.register(removeBtn, HOVER_CLICK_DELAY_MS, this::removeSelectedGoal, passThroughEnabledSupplier);
        buttons.add(addBtn);
        buttons.add(removeBtn);
        header.add(buttons, BorderLayout.EAST);
        p.add(header, BorderLayout.NORTH);

        p.add(goalsScroll = wrapScroll(goalsTable, 120), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildMaterialsPanel(Font base, int fontSize) {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setOpaque(false);
        p.add(sectionHeader("Materials", base, fontSize), BorderLayout.NORTH);
        JPanel content = new JPanel(new BorderLayout(4, 4));
        content.setOpaque(false);
        materialsEmptyLabel.setBorder(new EmptyBorder(8, 4, 8, 4));
        content.add(materialsEmptyLabel, BorderLayout.NORTH);
        shoppingScroll = wrapScroll(shoppingTable, 160);
        content.add(shoppingScroll, BorderLayout.CENTER);
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildTradePanel(Font base, int fontSize) {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setOpaque(false);
        p.add(sectionHeader("Trade Suggestions", base, fontSize), BorderLayout.NORTH);
        JPanel content = new JPanel(new BorderLayout(4, 4));
        content.setOpaque(false);
        tradeEmptyLabel.setBorder(new EmptyBorder(4, 4, 4, 4));
        content.add(tradeEmptyLabel, BorderLayout.NORTH);
        tradeScroll = wrapScroll(tradeTable, 140);
        content.add(tradeScroll, BorderLayout.CENTER);
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    private static void configureSplitPane(JSplitPane split, double resizeWeight) {
        split.setOpaque(false);
        split.setBorder(null);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(false);
        split.setDividerSize(9);
        split.setResizeWeight(Math.max(0.05, Math.min(0.95, resizeWeight)));
    }

    private static JLabel sectionHeader(String text, Font base, int fontSize) {
        JLabel label = new JLabel(text);
        label.setFont(base.deriveFont(Font.BOLD, fontSize + 1));
        label.setForeground(EdoUi.User.MAIN_TEXT);
        label.setBorder(new EmptyBorder(0, 0, 4, 0));
        return label;
    }

    private static void styleMutedLabel(JLabel label, Font base, int fontSize) {
        label.setFont(base.deriveFont(Font.PLAIN, fontSize));
        label.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
        label.setOpaque(false);
    }

    private TableRowSorter<ShoppingTableModel> createShoppingSorter() {
        TableRowSorter<ShoppingTableModel> sorter = new TableRowSorter<>(shoppingModel);
        sorter.setComparator(COL_MATERIAL, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        sorter.setComparator(COL_TYPE, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        sorter.setComparator(COL_NEED, Comparator.nullsLast(Comparator.naturalOrder()));
        sorter.setComparator(COL_HAVE, Comparator.nullsLast(Comparator.naturalOrder()));
        sorter.setComparator(COL_SHORT, Comparator.nullsLast(Comparator.naturalOrder()));
        return sorter;
    }

    private void installSortableTable(JTable table, TableRowSorter<?> sorter) {
        table.setAutoCreateRowSorter(false);
        table.setRowSorter(sorter);
        restoreSavedSort(sorter);
        sorter.addRowSorterListener(e -> persistSort(sorter));
        TableHeaderSortSupport.install(table, passThroughEnabledSupplier, HEADER_SORT_HOVER_MS);
    }

    private void restoreSavedSort(TableRowSorter<?> sorter) {
        int column = OverlayPreferences.getEngineeringMaterialsSortColumn();
        boolean descending = OverlayPreferences.isEngineeringMaterialsSortDescending();
        if (column < 0 || column >= sorter.getModel().getColumnCount()) {
            return;
        }
        sorter.setSortKeys(List.of(new RowSorter.SortKey(
                column,
                descending ? SortOrder.DESCENDING : SortOrder.ASCENDING)));
    }

    private static void persistSort(TableRowSorter<?> sorter) {
        List<? extends RowSorter.SortKey> keys = sorter.getSortKeys();
        if (keys == null || keys.isEmpty()) {
            OverlayPreferences.setEngineeringMaterialsSortColumn(-1);
            return;
        }
        RowSorter.SortKey key = keys.get(0);
        OverlayPreferences.setEngineeringMaterialsSortColumn(key.getColumn());
        OverlayPreferences.setEngineeringMaterialsSortDescending(key.getSortOrder() == SortOrder.DESCENDING);
    }

    private JScrollPane wrapScroll(Component content, int height) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setPreferredSize(new Dimension(400, height));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setFocusable(false);
        scroll.setRequestFocusEnabled(false);
        OverlayScrollPaneSupport.configureOverlayTableScroller(scroll);
        stripOverlayScrollChrome(scroll);
        if (scroll.getViewport() != null) {
            scroll.getViewport().setFocusable(false);
            scroll.getViewport().setRequestFocusEnabled(false);
        }
        return scroll;
    }

    /** Clears LAF scroll-pane chrome that can read as a white frame on transparent overlays. */
    private static void stripOverlayScrollChrome(JScrollPane scroll) {
        OverlayTransparentChrome.configureScrollPane(scroll);
        OverlayScrollPaneSupport.installSubtleScrollBars(scroll);
    }

    private void stripAllEngineeringScrollChrome() {
        stripOverlayScrollChrome(goalsScroll);
        stripOverlayScrollChrome(shoppingScroll);
        stripOverlayScrollChrome(tradeScroll);
    }

    private void installEngineeringTableLayoutListeners() {
        ComponentAdapter resize = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(EngineeringTabPanel.this::applyEngineeringTableColumnLayouts);
            }
        };
        if (goalsScroll != null && goalsScroll.getViewport() != null) {
            goalsScroll.getViewport().addComponentListener(resize);
        }
        if (shoppingScroll != null && shoppingScroll.getViewport() != null) {
            shoppingScroll.getViewport().addComponentListener(resize);
        }
        if (tradeScroll != null && tradeScroll.getViewport() != null) {
            tradeScroll.getViewport().addComponentListener(resize);
        }
        if (mainSplit != null) {
            mainSplit.addComponentListener(resize);
        }
        if (lowerSplit != null) {
            lowerSplit.addComponentListener(resize);
        }
    }

    private void applyEngineeringTableColumnLayouts() {
        applyGoalsTableColumnLayout();
        applyShoppingTableColumnLayout();
        applyTradeTableColumnLayout();
    }

    private void applyGoalsTableColumnLayout() {
        int avail = viewportWidth(goalsScroll);
        if (avail <= 0 || goalsTable == null) {
            return;
        }
        int wCheck = 36;
        int wEdit = 28;
        int rowSample = Math.max(goalsTable.getRowCount(), 1);
        int wTarget = clampColumnWidth(goalsTable, COL_GOAL_TARGET, 40, 80, rowSample);
        int wExp = clampColumnWidth(goalsTable, COL_GOAL_EXP, 76, 220, rowSample);
        int wStatus = measureGoalStatusColumnWidth();
        int minBlueprint = 40;
        int wBlueprint = avail - wCheck - wEdit - wTarget - wExp - wStatus;
        if (wBlueprint < minBlueprint) {
            wBlueprint = minBlueprint;
            int overflow = wCheck + wEdit + wTarget + wExp + wStatus + wBlueprint - avail;
            if (overflow > 0) {
                int take = Math.min(overflow, wTarget - 40);
                wTarget -= take;
                overflow -= take;
                take = Math.min(overflow, wExp - 76);
                wExp -= take;
                wBlueprint = Math.max(minBlueprint, avail - wCheck - wEdit - wTarget - wExp - wStatus);
            }
        }
        setColumnPixelWidth(goalsTable, COL_GOAL_INCLUDE, wCheck);
        setColumnPixelWidth(goalsTable, COL_GOAL_BLUEPRINT, wBlueprint);
        setColumnPixelWidth(goalsTable, COL_GOAL_TARGET, wTarget);
        setColumnPixelWidth(goalsTable, COL_GOAL_EXP, wExp);
        setColumnPixelWidth(goalsTable, COL_GOAL_STATUS, wStatus);
        setColumnPixelWidth(goalsTable, COL_GOAL_EDIT, wEdit);
        pinScrollLeft(goalsScroll);
    }

    private int measureGoalStatusColumnWidth() {
        if (goalsTable == null) {
            return 92;
        }
        TableColumn col = goalsTable.getColumnModel().getColumn(COL_GOAL_STATUS);
        javax.swing.table.TableCellRenderer headerRenderer = goalsTable.getTableHeader().getDefaultRenderer();
        Component header = headerRenderer.getTableCellRendererComponent(
                goalsTable, col.getHeaderValue(), false, false, 0, COL_GOAL_STATUS);
        GoalStatusCellRenderer cellRenderer = new GoalStatusCellRenderer();
        Component cell = cellRenderer.getTableCellRendererComponent(
                goalsTable, GOAL_STATUS_WIDTH_CAP_TEXT, false, false, 0, COL_GOAL_STATUS);
        return Math.max(header.getPreferredSize().width, cell.getPreferredSize().width) + 10;
    }

    private void applyShoppingTableColumnLayout() {
        int avail = viewportWidth(shoppingScroll);
        if (avail <= 0 || shoppingTable == null) {
            return;
        }
        int wType = clampColumnWidth(shoppingTable, COL_TYPE, 48, 88, 8);
        int wNeed = clampColumnWidth(shoppingTable, COL_NEED, 40, 56, 4);
        int wHave = clampColumnWidth(shoppingTable, COL_HAVE, 40, 56, 4);
        int wShort = clampColumnWidth(shoppingTable, COL_SHORT, 52, 72, 8);
        int minMaterial = Math.max(56, OverlayPreferences.getUiFontSize() * 5);
        int wMaterial = avail - wType - wNeed - wHave - wShort;
        if (wMaterial < minMaterial) {
            int deficit = minMaterial - wMaterial;
            int take = Math.min(deficit, wShort - 52);
            wShort -= take;
            deficit -= take;
            take = Math.min(deficit, wType - 48);
            wType -= take;
            wMaterial = avail - wType - wNeed - wHave - wShort;
        }
        wMaterial = Math.max(minMaterial, wMaterial);
        setColumnPixelWidth(shoppingTable, COL_MATERIAL, wMaterial);
        setColumnPixelWidth(shoppingTable, COL_TYPE, wType);
        setColumnPixelWidth(shoppingTable, COL_NEED, wNeed);
        setColumnPixelWidth(shoppingTable, COL_HAVE, wHave);
        setColumnPixelWidth(shoppingTable, COL_SHORT, wShort);
        pinScrollLeft(shoppingScroll);
    }

    private void applyTradeTableColumnLayout() {
        int avail = viewportWidth(tradeScroll);
        if (avail <= 0 || tradeTable == null) {
            return;
        }
        int rowSample = Math.max(tradeTable.getRowCount(), 1);
        int wNeed = measureTradeDataColumnWidth(COL_TRADE_NEED, 42, 68, rowSample);
        int wTrade = measureTradeDataColumnWidth(COL_TRADE_SUGGESTION, 64, 360, rowSample);
        int minMaterial = 72;
        int wMaterial = avail - wNeed - wTrade;
        if (wMaterial < minMaterial) {
            wTrade = Math.max(64, wTrade - (minMaterial - wMaterial));
            wMaterial = avail - wNeed - wTrade;
        }
        wMaterial = Math.max(minMaterial, wMaterial);
        setColumnPixelWidth(tradeTable, COL_TRADE_MATERIAL, wMaterial);
        setColumnPixelWidth(tradeTable, COL_TRADE_NEED, wNeed);
        setColumnPixelWidth(tradeTable, COL_TRADE_SUGGESTION, wTrade);
        pinScrollLeft(tradeScroll);
    }

    private static int viewportWidth(JScrollPane scroll) {
        if (scroll == null) {
            return 0;
        }
        JViewport viewport = scroll.getViewport();
        if (viewport == null) {
            return 0;
        }
        return viewport.getExtentSize().width;
    }

    private int measureTradeDataColumnWidth(int column, int min, int max, int sampleRows) {
        if (tradeTable == null || column < 0 || column >= tradeTable.getColumnCount()) {
            return min;
        }
        TableColumn col = tradeTable.getColumnModel().getColumn(column);
        javax.swing.table.TableCellRenderer headerRenderer = tradeTable.getTableHeader().getDefaultRenderer();
        Component header = headerRenderer.getTableCellRendererComponent(
                tradeTable, col.getHeaderValue(), false, false, 0, column);
        int width = header.getPreferredSize().width + 10;
        int rows = Math.min(sampleRows, tradeModel.getRowCount());
        for (int row = 0; row < rows; row++) {
            if (tradeModel.isSectionRow(row)) {
                continue;
            }
            javax.swing.table.TableCellRenderer renderer = tradeTable.getCellRenderer(row, column);
            Component cell = renderer.getTableCellRendererComponent(
                    tradeTable, tradeModel.getValueAt(row, column), false, false, row, column);
            width = Math.max(width, cell.getPreferredSize().width + 10);
        }
        return Math.max(min, Math.min(max, width));
    }

    private static int clampColumnWidth(JTable table, int column, int min, int max, int sampleRows) {
        int measured = measureColumnWidth(table, column, sampleRows);
        return Math.max(min, Math.min(max, measured));
    }

    private static int measureColumnWidth(JTable table, int column, int sampleRows) {
        if (table == null || column < 0 || column >= table.getColumnCount()) {
            return 0;
        }
        TableColumn col = table.getColumnModel().getColumn(column);
        javax.swing.table.TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
        Component header = headerRenderer.getTableCellRendererComponent(
                table, col.getHeaderValue(), false, false, 0, column);
        int width = header.getPreferredSize().width + 10;
        int rows = Math.min(sampleRows, table.getRowCount());
        for (int row = 0; row < rows; row++) {
            javax.swing.table.TableCellRenderer renderer = table.getCellRenderer(row, column);
            Component cell = renderer.getTableCellRendererComponent(
                    table, table.getValueAt(row, column), false, false, row, column);
            width = Math.max(width, cell.getPreferredSize().width + 10);
        }
        return width;
    }

    private static void setColumnPixelWidth(JTable table, int column, int width) {
        if (table == null || column < 0 || column >= table.getColumnCount()) {
            return;
        }
        TableColumn col = table.getColumnModel().getColumn(column);
        col.setMinWidth(width);
        col.setMaxWidth(width);
        col.setPreferredWidth(width);
        col.setWidth(width);
    }

    private static void pinScrollLeft(JScrollPane scroll) {
        if (scroll == null || scroll.getViewport() == null) {
            return;
        }
        Point view = scroll.getViewport().getViewPosition();
        if (view.x != 0) {
            scroll.getViewport().setViewPosition(new Point(0, view.y));
        }
    }

    /** Clears LAF table-scroll chrome that can read as a white frame on transparent overlays. */
    private static JTable createOverlayTable(TableModel model) {
        return new JTable(model) {
            private static final long serialVersionUID = 1L;

            @Override
            public String getToolTipText(MouseEvent event) {
                String tip = TableCellToolTipSupport.cellTextAt(this, event);
                return tip != null ? tip : super.getToolTipText(event);
            }

            @Override
            protected void configureEnclosingScrollPane() {
                super.configureEnclosingScrollPane();
                Container parent = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
                if (parent instanceof JScrollPane sp) {
                    stripOverlayScrollChrome(sp);
                }
            }
        };
    }

    public void applyOverlayBackground(Color bgWithAlpha, boolean treatAsTransparent) {
        boolean opaque = !treatAsTransparent;
        setOpaque(opaque);
        if (opaque && bgWithAlpha != null) {
            setBackground(bgWithAlpha);
        }
        stripAllEngineeringScrollChrome();
        OverlayTransparentChrome.applyToSubtree(this);
        if (mainSplit != null) {
            mainSplit.setOpaque(false);
            EdoMiningSplitPaneUi.applyDividerTheme(mainSplit);
        }
        if (lowerSplit != null) {
            lowerSplit.setOpaque(false);
            EdoMiningSplitPaneUi.applyDividerTheme(lowerSplit);
        }
        goalsTable.setOpaque(false);
        shoppingTable.setOpaque(false);
        tradeTable.setOpaque(false);
        logComponentColorAuditIfRequested();
        revalidate();
        repaint();
    }

    private void logComponentColorAuditIfRequested() {
        if (!Boolean.getBoolean("edo.debug.engineeringColors")) {
            return;
        }
        OverlayComponentColorAnalyzer.logWhiteComponents(this, "EngineeringTab");
    }

    /**
     * Mouse wheel while OS pass-through is active (see {@link PassThroughScrollSupport}).
     *
     * @return {@code true} if a scroll pane under the pointer was adjusted
     */
    public boolean applyPassThroughWheelIfHit(int screenX, int screenY, int wheelRotation) {
        return OverlayScrollPaneSupport.applyPassThroughWheelIfHit(
                scrollPanesForPassThrough(), screenX, screenY, wheelRotation);
    }

    /** {@code true} when the pointer is over a visible scroll bar on this tab. */
    public boolean isPointerOverScrollBar(Point screenPoint) {
        for (JScrollPane sp : scrollPanesForPassThrough()) {
            if (OverlayScrollPaneSupport.isPointerOverScrollBar(sp, screenPoint)) {
                return true;
            }
        }
        return false;
    }

    private JScrollPane[] scrollPanesForPassThrough() {
        List<JScrollPane> panes = new ArrayList<>(3);
        if (goalsScroll != null) {
            panes.add(goalsScroll);
        }
        if (shoppingScroll != null) {
            panes.add(shoppingScroll);
        }
        if (tradeScroll != null) {
            panes.add(tradeScroll);
        }
        return panes.toArray(JScrollPane[]::new);
    }

    private void configureTradeNeedColumn() {
        TableColumn needCol = tradeTable.getColumnModel().getColumn(COL_TRADE_NEED);
        needCol.setHeaderRenderer(new TradeNeedHeaderRenderer());
    }

    private void configureTable(JTable table, Font base, DefaultTableCellRenderer renderer) {
        table.setOpaque(false);
        table.setBackground(EdoUi.Internal.TRANSPARENT);
        table.setForeground(EdoUi.User.MAIN_TEXT);
        table.setBorder(null);
        table.setFocusable(false);
        table.setRequestFocusEnabled(false);
        table.putClientProperty("JTable.autoStartsEdit", Boolean.FALSE);
        table.setShowGrid(false);
        table.setShowHorizontalLines(false);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setGridColor(EdoUi.Internal.TRANSPARENT);
        table.setRowHeight(Math.max(22, OverlayPreferences.getUiFontSize() + 10));
        table.setSelectionBackground(EdoUi.Internal.TRANSPARENT);
        table.setSelectionForeground(EdoUi.User.MAIN_TEXT);
        table.setFont(base.deriveFont(Font.PLAIN, OverlayPreferences.getUiFontSize()));
        table.setFillsViewportHeight(true);
        table.setDefaultRenderer(Object.class, renderer);
        table.setTableHeader(new TransparentTableHeader(table.getColumnModel()));
        JTableHeader th = table.getTableHeader();
        if (th != null) {
            th.setUI(TransparentTableHeaderUI.createUI(th));
            th.setOpaque(!OverlayPreferences.overlayChromeRequestsTransparency());
            th.setForeground(EdoUi.User.MAIN_TEXT);
            th.setBackground(OverlayPreferences.overlayChromeRequestsTransparency()
                    ? EdoUi.Internal.TRANSPARENT : EdoUi.User.BACKGROUND);
            th.setFont(base.deriveFont(Font.BOLD, OverlayPreferences.getUiFontSize()));
            th.setBorder(null);
            th.putClientProperty("JTableHeader.focusCellBackground", null);
            th.putClientProperty("JTableHeader.cellBorder", null);
            th.setDefaultRenderer(new EdoHeaderRenderer());
        }
    }

    public EngineeringInventoryTracker getInventoryTracker() {
        return inventoryTracker;
    }

    public void setSessionStateChangeCallback(Runnable callback) {
        this.sessionStateChangeCallback = callback;
    }

    public void hydrateFromJournalIfNeeded(String clientKey) {
        inventoryTracker.bootstrapFromJournal(clientKey);
        if (EngineeringGoalProgress.bootstrapFromJournal(goals, clientKey, database)) {
            fireSessionChanged();
        }
        scheduleRefresh();
    }

    public void handleLogEvent(EliteLogEvent event) {
        if (event == null) {
            return;
        }
        EliteEventType type = event.getType();
        if (type == EliteEventType.MATERIALS
                || type == EliteEventType.MATERIAL_COLLECTED
                || type == EliteEventType.MATERIAL_DISCARDED
                || type == EliteEventType.MATERIAL_TRADE
                || type == EliteEventType.ENGINEER_CRAFT) {
            inventoryTracker.applyEvent(event);
            scheduleRefresh();
        }
        if (type == EliteEventType.ENGINEER_CRAFT && event instanceof EngineerCraftEvent craft) {
            if (EngineeringGoalProgress.applyCraft(goals, craft, database)) {
                fireSessionChanged();
                scheduleRefresh();
            }
        }
        if (type == EliteEventType.LOADOUT && event instanceof LoadoutEvent loadout) {
            if (EngineeringGoalProgress.applyLoadout(goals, loadout, database)) {
                fireSessionChanged();
                scheduleRefresh();
            }
        }
    }

    public void fillSessionState(EdoSessionState state) {
        if (state == null) {
            return;
        }
        EngineeringSessionData data = new EngineeringSessionData();
        List<EngineeringGoalPersisted> persisted = new ArrayList<>();
        for (EngineeringGoal g : goals) {
            if ("__inventory_consolidation__".equals(g.getBlueprintId())) {
                continue;
            }
            EngineeringGoalPersisted p = new EngineeringGoalPersisted();
            p.setBlueprintId(g.getBlueprintId());
            p.setModuleType(g.getModuleType());
            p.setBlueprintName(g.getBlueprintName());
            p.setFromGrade(g.getFromGrade());
            p.setCraftsAtCurrentGrade(g.getCraftsAtCurrentGrade());
            p.setTargetGrade(g.getTargetGrade());
            p.setExperimentalId(g.getExperimentalId());
            p.setIncludeInPlanning(g.isIncludeInPlanning());
            p.setExperimentalApplied(g.isExperimentalApplied());
            p.setQuantity(g.getQuantity());
            p.setCompletedUnits(g.getCompletedUnits());
            persisted.add(p);
        }
        data.setGoals(persisted);
        state.setEngineering(data);
    }

    public void applySessionState(EdoSessionState state) {
        goals.clear();
        if (state != null && state.getEngineering() != null) {
            for (EngineeringGoalPersisted p : state.getEngineering().goalsOrEmpty()) {
                if ("__inventory_consolidation__".equals(p.getBlueprintId())) {
                    continue;
                }
                goals.add(new EngineeringGoal(
                        p.getBlueprintId(),
                        p.getModuleType(),
                        p.getBlueprintName(),
                        p.getFromGrade(),
                        p.getCraftsAtCurrentGrade(),
                        p.getTargetGrade(),
                        p.getExperimentalId() != null ? p.getExperimentalId() : "",
                        p.includeInPlanningOrDefault(),
                        p.isExperimentalApplied(),
                        p.getQuantity(),
                        p.getCompletedUnits()));
            }
        }
        scheduleRefresh();
    }

    private void openAddGoalDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        EngineeringGoal goal = EngineeringGoalDialog.showForAdd(owner, database, passThroughEnabledSupplier);
        if (goal != null) {
            goals.add(goal);
            fireSessionChanged();
            scheduleRefresh();
        }
    }

    private void openEditGoalDialog(int modelRow) {
        if (modelRow < 0 || modelRow >= goals.size()) {
            return;
        }
        EngineeringGoal existing = goals.get(modelRow);
        Window owner = SwingUtilities.getWindowAncestor(this);
        EngineeringGoal updated = EngineeringGoalDialog.showForEdit(owner, database, passThroughEnabledSupplier, existing);
        if (updated != null && !updated.equals(existing)) {
            goals.set(modelRow, updated);
            fireSessionChanged();
            scheduleRefresh();
        }
    }

    private void removeSelectedGoal() {
        int row = goalsTable.getSelectedRow();
        if (row >= 0 && row < goals.size()) {
            goals.remove(row);
            fireSessionChanged();
            scheduleRefresh();
        }
    }

    private void scheduleRefresh() {
        SwingUtilities.invokeLater(this::refreshUi);
    }

    private void refreshUi() {
        Map<String, Integer> inv = inventoryTracker.snapshot();
        List<EngineeringGoal> activeGoals = goals.stream()
                .filter(g -> g != null && g.isIncludeInPlanning())
                .toList();
        Map<String, Integer> required = planner.requiredMaterials(activeGoals);
        Map<String, Integer> shortfalls = planner.shortfalls(activeGoals, inv);
        List<TradeSuggestion> trades = new ArrayList<>(tradePlanner.suggest(shortfalls, inv, required));
        Map<String, Integer> invAfterTrades = tradePlanner.inventoryAfterTrades(inv, trades);
        List<ShoppingListRow> shopping = planner.buildShoppingList(activeGoals, inv, invAfterTrades);

        goalReadiness.clear();
        goalStatusText.clear();
        for (EngineeringGoal goal : goals) {
            if (!goal.isIncludeInPlanning()) {
                goalReadiness.add(GoalReadiness.READY);
                goalStatusText.add("Hidden");
                continue;
            }
            GoalReadiness readiness = planner.goalReadiness(goal, inv, invAfterTrades);
            goalReadiness.add(readiness);
            goalStatusText.add(formatStatusText(goal, readiness, invAfterTrades));
        }

        shoppingModel.setRows(shopping);
        updateTradeTable(trades, shortfalls);
        goalsModel.fireTableDataChanged();

        boolean hasGoals = !goals.isEmpty();
        boolean hasActiveGoals = !activeGoals.isEmpty();
        boolean showShopping = hasActiveGoals && !shopping.isEmpty();
        boolean showTrades = !trades.isEmpty();
        materialsEmptyLabel.setVisible(!showShopping);
        tradeEmptyLabel.setVisible(!showTrades);
        if (shoppingScroll != null) {
            shoppingScroll.setVisible(showShopping);
        }
        if (tradeScroll != null) {
            tradeScroll.setVisible(showTrades);
        }

        if (!hasGoals) {
            materialsEmptyLabel.setText("<html><body style='color:#ffcc88'>Add a goal to see required materials.</body></html>");
            tradeEmptyLabel.setText("<html><body style='color:#ffcc88'>Trade Suggestions appear when you have material shortfalls.</body></html>");
        } else if (shopping.isEmpty()) {
            materialsEmptyLabel.setText("<html><body style='color:#ffcc88'>No materials computed for the current goals. "
                    + "Check that target grade is above your starting grade (G0).</body></html>");
        } else {
            materialsEmptyLabel.setText("");
        }
        if (hasGoals && trades.isEmpty() && !shortfalls.isEmpty()) {
            tradeEmptyLabel.setText("<html><body style='color:#ffcc88'>No material-trader swaps found from current inventory.</body></html>");
        } else if (hasGoals && trades.isEmpty() && hasActiveGoals) {
            tradeEmptyLabel.setText("");
        } else if (hasGoals && trades.isEmpty()) {
            tradeEmptyLabel.setText("<html><body style='color:#ffcc88'>Enable a goal (checkbox) to see materials and trades.</body></html>");
        }
        applyEngineeringTableColumnLayouts();
    }

    private void updateTradeTable(List<TradeSuggestion> trades, Map<String, Integer> shortfalls) {
        List<TradeTableRow> rows = new ArrayList<>();
        Map<String, List<TradeTargetGroup>> grouped =
                MaterialTradePlanner.groupByTraderTypeAndTarget(trades, shortfalls);
        for (Map.Entry<String, List<TradeTargetGroup>> entry : grouped.entrySet()) {
            List<TradeTargetGroup> targets = entry.getValue();
            if (targets == null || targets.isEmpty()) {
                continue;
            }
            rows.add(TradeTableRow.section(traderTypeSectionTitle(entry.getKey())));
            for (TradeTargetGroup group : targets) {
                for (TradeSuggestion option : group.getOptions()) {
                    rows.add(TradeTableRow.data(
                            group.getToName(),
                            group.getShortfall(),
                            formatTradeSuggestion(option)));
                }
            }
        }
        tradeModel.setRows(rows);
    }

    private static String traderTypeSectionTitle(String type) {
        if (type == null || type.isBlank()) {
            return "Material trader";
        }
        return switch (type) {
            case "Raw" -> "Raw";
            case "Manufactured" -> "Manufactured";
            case "Encoded" -> "Encoded";
            default -> type;
        };
    }

    private static String formatTradeSuggestion(TradeSuggestion trade) {
        return trade.getFromCount() + "× " + trade.getFromName()
                + " → get " + trade.getToCount() + "× " + trade.getToName();
    }

    private void fireSessionChanged() {
        Runnable cb = sessionStateChangeCallback;
        if (cb != null) {
            cb.run();
        }
    }

    public void applyUiFontPreferences() {
        applyUiFont(OverlayPreferences.getUiFont());
    }

    public void applyUiFont(Font font) {
        if (font == null) {
            return;
        }
        int fontSize = OverlayPreferences.getUiFontSize();
        EdoMiningSplitPaneUi.applyDividerTheme(mainSplit);
        EdoMiningSplitPaneUi.applyDividerTheme(lowerSplit);
        stripAllEngineeringScrollChrome();
        applyEngineeringTableColumnLayouts();
        scheduleRefresh();
    }

    private String formatStatusText(EngineeringGoal goal, GoalReadiness readiness, Map<String, Integer> invAfterTrades) {
        if (goal.isComplete()) {
            return "Complete";
        }
        return switch (readiness) {
            case READY -> "Ready";
            case READY_WITH_TRADES -> "Ready w/ trades";
            case STILL_SHORT -> {
                Map<String, Integer> missing = planner.goalMaterialShortfalls(goal, invAfterTrades);
                if (missing.isEmpty()) {
                    missing = planner.goalMaterialShortfalls(goal, inventoryTracker.snapshot());
                }
                if (missing.isEmpty()) {
                    yield "Short";
                }
                String first = missing.entrySet().iterator().next().getKey();
                int extra = missing.size() - 1;
                String name = database.materialDisplayName(first);
                yield extra > 0 ? "Short: " + name + " +" + extra : "Short: " + name;
            }
        };
    }

    private static class EdoTableCellRenderer extends DefaultTableCellRenderer {
        EdoTableCellRenderer() {
            setOpaque(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
            if (c instanceof JLabel label) {
                label.setOpaque(false);
                label.setForeground(EdoUi.User.MAIN_TEXT);
                label.setBackground(EdoUi.Internal.TRANSPARENT);
                label.setBorder(new EmptyBorder(2, 6, 2, 6));
            }
            return c;
        }
    }

    private static final class EdoHeaderRenderer extends DefaultTableCellRenderer {
        EdoHeaderRenderer() {
            setOpaque(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
            if (c instanceof JLabel label) {
                label.setOpaque(false);
                label.setForeground(EdoUi.User.MAIN_TEXT);
                label.setBorder(new EmptyBorder(2, 6, 4, 6));
            }
            return c;
        }
    }

    private final class GoalIncludeCellRenderer extends JCheckBox implements javax.swing.table.TableCellRenderer {
        private static final long serialVersionUID = 1L;

        GoalIncludeCellRenderer() {
            OverlayCheckBoxStyle.apply(this);
            setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            setSelected(value instanceof Boolean b && b);
            return this;
        }
    }

    private static final class EllipsisTextCellRenderer extends EdoTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
            if (c instanceof JLabel label && value != null) {
                String text = value.toString();
                label.setToolTipText(text.isBlank() || "—".equals(text) ? null : text);
            }
            return c;
        }
    }

    private final class GoalStatusCellRenderer extends EdoTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
            if (c instanceof JLabel label && value != null) {
                String text = value.toString();
                label.setToolTipText(text.isBlank() ? null : text);
            }
            int modelRow = table.convertRowIndexToModel(row);
            int modelCol = table.convertColumnIndexToModel(column);
            if (!isSelected && modelCol == COL_GOAL_STATUS && modelRow < goalReadiness.size()) {
                if (modelRow < goalStatusText.size() && "Hidden".equals(goalStatusText.get(modelRow))) {
                    c.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
                } else if (modelRow < goalStatusText.size() && "Complete".equals(goalStatusText.get(modelRow))) {
                    c.setForeground(EdoUi.User.SUCCESS);
                } else {
                Color color = switch (goalReadiness.get(modelRow)) {
                    case READY -> EdoUi.User.SUCCESS;
                    case READY_WITH_TRADES -> Color.YELLOW;
                    case STILL_SHORT -> EdoUi.User.ERROR;
                };
                c.setForeground(color);
                }
            }
            return c;
        }
    }

    private final class BlueprintNameCellRenderer extends EdoTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
            if (c instanceof JLabel label && value != null) {
                String text = value.toString();
                label.setToolTipText(text);
            }
            return c;
        }
    }

    private final class GoalEditCellRenderer extends JLabel implements javax.swing.table.TableCellRenderer {
        private static final long serialVersionUID = 1L;

        GoalEditCellRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(false);
            setIcon(PencilIcon.DEFAULT);
            setToolTipText("Edit goal");
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            int modelRow = table.convertRowIndexToModel(row);
            boolean editable = modelRow >= 0 && modelRow < goals.size();
            setEnabled(editable);
            setIcon(editable ? PencilIcon.DEFAULT : null);
            return this;
        }
    }

    private static String blueprintDisplayName(EngineeringGoal goal) {
        String name = goal.getModuleType() + ": " + goal.getBlueprintName();
        if (goal.getQuantity() > 1) {
            return goal.getQuantity() + "x " + name;
        }
        return name;
    }

    private final class GoalsTableModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return goals.size();
        }

        @Override
        public int getColumnCount() {
            return 6;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) {
                return Boolean.class;
            }
            if (columnIndex == COL_GOAL_EDIT) {
                return Object.class;
            }
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columnIndex != 0 || rowIndex < 0 || rowIndex >= goals.size()) {
                return;
            }
            boolean include = value instanceof Boolean b && b;
            EngineeringGoal updated = goals.get(rowIndex).withIncludeInPlanning(include);
            if (!updated.equals(goals.get(rowIndex))) {
                goals.set(rowIndex, updated);
                fireSessionChanged();
                scheduleRefresh();
            }
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "";
                case 1 -> "Blueprint";
                case 2 -> "Lvl";
                case 3 -> "Experimental";
                case 4 -> "Status";
                case COL_GOAL_EDIT -> "";
                default -> "";
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            EngineeringGoal g = goals.get(rowIndex);
            if (columnIndex == 0) {
                return g.isIncludeInPlanning();
            }
            if (columnIndex == COL_GOAL_EDIT) {
                return "";
            }
            return switch (columnIndex) {
                case 1 -> blueprintDisplayName(g);
                case 2 -> EngineeringGradeProgress.progressLabel(g);
                case 3 -> g.getExperimentalId().isBlank() ? "—" : experimentalName(g.getExperimentalId());
                case 4 -> rowIndex < goalStatusText.size() ? goalStatusText.get(rowIndex) : "";
                default -> "";
            };
        }

        private String experimentalName(String id) {
            return database.findById(id).map(BlueprintGrade::getName).orElse("Yes");
        }
    }

    private final class ShoppingTableModel extends AbstractTableModel {
        private List<ShoppingListRow> rows = List.of();

        void setRows(List<ShoppingListRow> rows) {
            this.rows = rows != null ? List.copyOf(rows) : List.of();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 5;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "Material";
                case 1 -> "Type";
                case 2 -> "Need";
                case 3 -> "Have";
                case 4 -> "Still need";
                default -> "";
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ShoppingListRow r = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> r.getDisplayName();
                case 1 -> r.getType();
                case 2 -> r.getRequired();
                case 3 -> r.getOwned();
                case 4 -> r.getShortfall();
                default -> "";
            };
        }
    }

    private static final class ShoppingCellRenderer extends EdoTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
            if (!isSelected && column == 4 && value instanceof Integer shortfall) {
                if (shortfall > 0) {
                    c.setForeground(new Color(255, 160, 120));
                } else {
                    c.setForeground(EdoUi.User.SUCCESS);
                }
            }
            return c;
        }
    }

    private record TradeTableRow(String materialName, int need, String suggestion, boolean section) {
        static TradeTableRow section(String title) {
            return new TradeTableRow(title, 0, "", true);
        }

        static TradeTableRow data(String materialName, int need, String suggestion) {
            return new TradeTableRow(materialName, need, suggestion, false);
        }
    }

    private final class TradeTableModel extends AbstractTableModel {
        private List<TradeTableRow> rows = List.of();

        void setRows(List<TradeTableRow> rows) {
            this.rows = rows != null ? List.copyOf(rows) : List.of();
            fireTableDataChanged();
        }

        boolean isSectionRow(int rowIndex) {
            return rowIndex >= 0 && rowIndex < rows.size() && rows.get(rowIndex).section();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case COL_TRADE_MATERIAL -> "Material";
                case COL_TRADE_NEED -> "Need";
                case COL_TRADE_SUGGESTION -> "Trade";
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == COL_TRADE_NEED ? Integer.class : String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            TradeTableRow row = rows.get(rowIndex);
            if (row.section()) {
                return columnIndex == COL_TRADE_MATERIAL ? row.materialName() : null;
            }
            return switch (columnIndex) {
                case COL_TRADE_MATERIAL -> row.materialName();
                case COL_TRADE_NEED -> row.need();
                case COL_TRADE_SUGGESTION -> row.suggestion();
                default -> "";
            };
        }
    }

    private static final class TradeNeedHeaderRenderer extends DefaultTableCellRenderer {
        TradeNeedHeaderRenderer() {
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
            if (c instanceof JLabel label) {
                label.setOpaque(false);
                label.setForeground(EdoUi.User.MAIN_TEXT);
                label.setBorder(new EmptyBorder(2, 6, 4, 6));
            }
            return c;
        }
    }

    private final class TradeCellRenderer extends EdoTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            int modelRow = table.convertRowIndexToModel(row);
            boolean section = tradeModel.isSectionRow(modelRow);
            Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
            if (c instanceof JLabel label) {
                if (section) {
                    if (column == COL_TRADE_MATERIAL) {
                        label.setFont(label.getFont().deriveFont(Font.BOLD));
                        label.setForeground(EdoUi.User.MAIN_TEXT);
                        label.setBorder(new EmptyBorder(8, 6, 2, 6));
                    } else {
                        label.setText("");
                        label.setBorder(new EmptyBorder(8, 0, 2, 0));
                    }
                    return c;
                }
                if (column == COL_TRADE_MATERIAL && value != null) {
                    String text = value.toString();
                    label.setToolTipText(text.isBlank() ? null : text);
                } else if (column == COL_TRADE_NEED) {
                    label.setHorizontalAlignment(SwingConstants.RIGHT);
                    label.setBorder(new EmptyBorder(2, TRADE_NEED_CELL_LEFT_PAD, 2, TRADE_NEED_CELL_RIGHT_PAD));
                } else if (column == COL_TRADE_SUGGESTION) {
                    label.setHorizontalAlignment(SwingConstants.LEFT);
                    label.setBorder(new EmptyBorder(2, TRADE_SUGGESTION_CELL_LEFT_PAD, 2, 6));
                    if (value != null) {
                        label.setToolTipText(value.toString());
                    }
                }
            }
            if (!isSelected && column == COL_TRADE_NEED && value instanceof Integer need && need > 0) {
                c.setForeground(new Color(255, 160, 120));
            } else if (!isSelected && column == COL_TRADE_SUGGESTION) {
                c.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
            }
            return c;
        }
    }
}
