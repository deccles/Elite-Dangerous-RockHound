package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.dce.ed.engineering.BlueprintGrade;
import org.dce.ed.engineering.EngineeringCraftStore;
import org.dce.ed.engineering.EngineeringDatabase;
import org.dce.ed.engineering.EngineeringGoal;
import org.dce.ed.engineering.EngineeringShipCatalog;
import org.dce.ed.engineering.EngineeringShipRef;
import org.dce.ed.engineering.EngineeringGoalProgress;
import org.dce.ed.engineering.EngineeringInventoryTracker;
import org.dce.ed.engineering.EngineeringMaterialKeys;
import org.dce.ed.engineering.EngineeringPlanner;
import org.dce.ed.engineering.GoalPriority;
import org.dce.ed.engineering.GoalReadiness;
import org.dce.ed.engineering.MaterialRequirement;
import org.dce.ed.engineering.MaterialTradeExecutor;
import org.dce.ed.engineering.MaterialTradePlanner;
import org.dce.ed.engineering.MaterialsGoal;
import org.dce.ed.engineering.ShoppingListRow;
import org.dce.ed.engineering.TradeSuggestion;
import org.dce.ed.engineering.TradeTargetGroup;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.logreader.event.MaterialTradeEvent;
import org.dce.ed.logreader.event.SetUserShipNameEvent;
import org.dce.ed.logreader.event.StoredShipsEvent;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.session.EngineeringSessionData;
import org.dce.ed.session.EngineeringSessionData.EngineeringGoalPersisted;
import org.dce.ed.session.EngineeringSessionData.MaterialNeedPersisted;
import org.dce.ed.session.EngineeringSessionData.MaterialsGoalPersisted;
import org.dce.ed.session.EngineeringSessionData.ShipPersisted;
import org.dce.ed.ui.EdoMiningSplitPaneUi;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.HoverClickPoller;
import org.dce.ed.ui.OverlayScrollPaneSupport;
import org.dce.ed.ui.SelectiveHitSupport;
import org.dce.ed.ui.PassThroughScrollSupport;
import org.dce.ed.ui.OverlayCheckBoxStyle;
import org.dce.ed.ui.OverlayComboBoxStyle;
import org.dce.ed.ui.OverlayComponentColorAnalyzer;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.OverlayTransparentChrome;
import org.dce.ed.ui.PriorityArrowIcon;
import org.dce.ed.ui.PriorityHeaderIcon;
import org.dce.ed.ui.PencilIcon;
import org.dce.ed.ui.TrashIcon;
import org.dce.ed.ui.StatusCircleIcon;
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
    private static final int INCLUDE_HOVER_DELAY_MS = 350;
    private static final int INCLUDE_COL_MIN_WIDTH = 44;

    private static int priorityColumnWidth() {
        // Arrow/checkbox glyphs scale with UI font; generous padding keeps the whole cell a usable
        // click target for real clicks in Selective mouse mode.
        return Math.max(INCLUDE_COL_MIN_WIDTH, OverlayPreferences.getUiFontSize() + 26);
    }
    private static final int INCLUDE_HIT_EXPAND_PX = 28;
    private static final int EDIT_COL_WIDTH = 30;
    private static final int DELETE_COL_WIDTH = 30;
    private static final int EDIT_HIT_EXPAND_LEFT_PX = 12;
    private static final int HEADER_SORT_HOVER_MS = 500;

    private static final int COL_MATERIAL = 0;
    private static final int COL_TYPE = 1;
    private static final int COL_NEED = 2;
    private static final int COL_HAVE = 3;
    private static final int COL_SHORT = 4;

    private static final int COL_GOAL_ENABLED = 0;
    private static final int COL_GOAL_PRIORITY = 1;
    private static final int COL_GOAL_BLUEPRINT = 2;
    private static final int COL_GOAL_TARGET = 3;
    private static final int COL_GOAL_EXP = 4;
    private static final int COL_GOAL_STATUS = 5;
    private static final int COL_GOAL_EDIT = 6;
    private static final int COL_GOAL_DELETE = 7;

    /** Status column is capped at this width; longer values ellipsize with a tooltip. */
    private static final String GOAL_STATUS_WIDTH_CAP_TEXT = "w/ Trades";
    private static final String STATUS_COMPLETE = "Complete";
    private static final String STATUS_READY = "Ready";
    private static final String STATUS_TRADES = "w/ Trades";
    private static final String STATUS_SHORT = "Short";
    private static final String STATUS_HIDDEN = "Hidden";

    private static final Icon STATUS_ICON_OK = StatusCircleIcon.check(EdoUi.User.SUCCESS);
    private static final Icon STATUS_ICON_TRADES = StatusCircleIcon.check(Color.YELLOW);
    private static final Icon STATUS_ICON_SHORT = StatusCircleIcon.cross(EdoUi.User.ERROR);

    private static final int COL_TRADE_MATERIAL = 0;
    private static final int COL_TRADE_NEED = 1;
    private static final int COL_TRADE_GIVE = 2;
    private static final int COL_TRADE_ACTION = 3;
    private static final int COL_TRADE_RECEIVE = 4;
    private static final int TRADE_ACTION_COL_WIDTH = 64;
    private static final int TRADE_GIVE_CELL_LEFT_PAD = 8;
    private static final String TRADE_GIVE_QTY_WIDTH_PROP = "edo.tradeGiveQtyWidth";
    /** Client property on {@link TableColumn}: pixel width of widest digit/string block in that column. */
    private static final String DIGIT_BLOCK_WIDTH_PROP = "edo.digitBlockWidth";
    private static final String MEASURING_COLUMNS_PROP = "edo.measuringColumns";
    /** When true on a renderer label, {@link EdoTableCellRenderer} paints digits via custom X (not JLabel align). */
    private static final String PAINT_CENTERED_RIGHT_PROP = "edo.paintCenteredRightNumber";
    private static final String PAINT_DIGIT_BLOCK_PROP = "edo.paintDigitBlock";
    private static final int NUMBER_COL_EDGE_PAD = 4;
    /** Extra header width so sort chrome cannot clip labels like "Still need". */
    private static final int HEADER_SORT_CHROME_PAD = 28;

    private static final int TRADE_SECTION_GAP_PX = 8;

    private final BooleanSupplier passThroughEnabledSupplier;

    private final EngineeringDatabase database = EngineeringDatabase.getInstance();
    private final EngineeringInventoryTracker inventoryTracker = new EngineeringInventoryTracker();
    private final EngineeringPlanner planner = new EngineeringPlanner(database);
    private final MaterialTradePlanner tradePlanner = new MaterialTradePlanner(database);

    private final List<EngineeringGoal> goals = new ArrayList<>();
    private final List<MaterialsGoal> materialsGoals = new ArrayList<>();
    private final EngineeringShipCatalog shipCatalog = new EngineeringShipCatalog();
    /** null = show / plan for all ships. */
    private Long goalsShipFilterId;
    private JComboBox<ShipFilterItem> shipFilterCombo;

    private final List<GoalReadiness> goalReadiness = new ArrayList<>();
    private final List<String> goalStatusText = new ArrayList<>();
    private Runnable sessionStateChangeCallback;

    private final JLabel materialsEmptyLabel = new JLabel();
    private final JLabel tradeEmptyLabel = new JLabel();
    private final JLabel tradeStatusLabel = new JLabel(" ");
    private final MaterialTradeExecutor tradeExecutor = new MaterialTradeExecutor(database);
    private volatile boolean tradeAutomationRunning;
    private final GoalsTableModel goalsModel = new GoalsTableModel();
    private final JTable goalsTable = createGoalsOverlayTable(goalsModel);
    private final ShoppingTableModel shoppingModel = new ShoppingTableModel();
    private final JTable shoppingTable = createOverlayTable(shoppingModel);
    private final TradeTableModel tradeModel = new TradeTableModel();
    private final JTable tradeTable;
    private JScrollPane goalsScroll;
    private TableRowSorter<ShoppingTableModel> shoppingSorter;
    private JScrollPane shoppingScroll;
    private JScrollPane tradeScroll;
    private JSplitPane mainSplit;
    private JPanel goalsPanel;
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

        tradeTable = createTradeOverlayTable(tradeModel);

        configureTable(goalsTable, base, new EllipsisTextCellRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_BLUEPRINT).setCellRenderer(new BlueprintNameCellRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_EXP).setCellRenderer(new EllipsisTextCellRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_TARGET).setCellRenderer(new GoalTargetCellRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_STATUS).setCellRenderer(new GoalStatusCellRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_ENABLED).setCellRenderer(new GoalEnabledCellRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_ENABLED).setHeaderRenderer(new GoalEnabledHeaderRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_PRIORITY).setCellRenderer(new GoalPriorityCellRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_PRIORITY).setHeaderRenderer(new GoalPriorityHeaderRenderer());
        int includeW = priorityColumnWidth();
        goalsTable.getColumnModel().getColumn(COL_GOAL_ENABLED).setMinWidth(includeW);
        goalsTable.getColumnModel().getColumn(COL_GOAL_ENABLED).setMaxWidth(includeW);
        goalsTable.getColumnModel().getColumn(COL_GOAL_ENABLED).setPreferredWidth(includeW);
        goalsTable.getColumnModel().getColumn(COL_GOAL_PRIORITY).setMinWidth(includeW);
        goalsTable.getColumnModel().getColumn(COL_GOAL_PRIORITY).setMaxWidth(includeW);
        goalsTable.getColumnModel().getColumn(COL_GOAL_PRIORITY).setPreferredWidth(includeW);
        goalsTable.setRowSelectionAllowed(false);
        goalsTable.setColumnSelectionAllowed(false);
        goalsTable.setCellSelectionEnabled(false);
        TableColumn editCol = goalsTable.getColumnModel().getColumn(COL_GOAL_EDIT);
        editCol.setMinWidth(EDIT_COL_WIDTH);
        editCol.setMaxWidth(EDIT_COL_WIDTH);
        editCol.setPreferredWidth(EDIT_COL_WIDTH);
        goalsTable.getColumnModel().getColumn(COL_GOAL_EDIT).setCellRenderer(new GoalEditCellRenderer());
        TableColumn deleteCol = goalsTable.getColumnModel().getColumn(COL_GOAL_DELETE);
        deleteCol.setMinWidth(DELETE_COL_WIDTH);
        deleteCol.setMaxWidth(DELETE_COL_WIDTH);
        deleteCol.setPreferredWidth(DELETE_COL_WIDTH);
        goalsTable.getColumnModel().getColumn(COL_GOAL_DELETE).setCellRenderer(new GoalDeleteCellRenderer());
        goalsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                // Cell editors fight overlay styling / MPT hover; handle via press when not MPT.
                if (passThroughEnabledSupplier != null && passThroughEnabledSupplier.getAsBoolean()) {
                    return;
                }
                int row = rowAtPointWithSlack(goalsTable, e.getPoint());
                if (row < 0) {
                    return;
                }
                int modelRow = goalsTable.convertRowIndexToModel(row);
                int viewCol = goalsTable.columnAtPoint(e.getPoint());
                int modelCol = viewCol >= 0 ? goalsTable.convertColumnIndexToModel(viewCol) : -1;
                if (modelCol == COL_GOAL_ENABLED) {
                    toggleGoalEnabled(modelRow);
                    e.consume();
                    return;
                }
                if (modelCol == COL_GOAL_PRIORITY) {
                    showGoalPriorityChooser(modelRow);
                    e.consume();
                    return;
                }
                if (isEditHit(goalsTable, e.getPoint(), row)) {
                    openEditGoalDialog(modelRow);
                    e.consume();
                    return;
                }
                if (isDeleteHit(goalsTable, e.getPoint(), row)) {
                    removeGoalAt(modelRow);
                    e.consume();
                }
            }
        });
        TableCellHoverClickSupport.install(
                goalsTable,
                COL_GOAL_EDIT,
                passThroughEnabledSupplier,
                HOVER_CLICK_DELAY_MS,
                EDIT_HIT_EXPAND_LEFT_PX,
                this::openEditGoalDialog);
        TableCellHoverClickSupport.install(
                goalsTable,
                COL_GOAL_DELETE,
                passThroughEnabledSupplier,
                HOVER_CLICK_DELAY_MS,
                0,
                this::removeGoalAt);
        TableCellHoverToggleSupport.install(
                goalsTable,
                COL_GOAL_ENABLED,
                passThroughEnabledSupplier,
                INCLUDE_HOVER_DELAY_MS,
                0,
                this::toggleGoalEnabled);
        TableCellHoverToggleSupport.install(
                goalsTable,
                COL_GOAL_PRIORITY,
                passThroughEnabledSupplier,
                INCLUDE_HOVER_DELAY_MS,
                0,
                this::showGoalPriorityChooser);
        configureTable(shoppingTable, base, new ShoppingCellRenderer());
        configureTable(tradeTable, base, new TradeCellRenderer());
        configureTradeNumberColumns();
        tradeTable.setRowHeight(Math.max(18, fontSize + 6));
        TableColumn tradeActionCol = tradeTable.getColumnModel().getColumn(COL_TRADE_ACTION);
        tradeActionCol.setMinWidth(TRADE_ACTION_COL_WIDTH);
        tradeActionCol.setMaxWidth(TRADE_ACTION_COL_WIDTH);
        tradeActionCol.setPreferredWidth(TRADE_ACTION_COL_WIDTH);
        tradeTable.getColumnModel().getColumn(COL_TRADE_ACTION).setCellRenderer(new TradeActionCellRenderer());
        tradeTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleTradeActionMouse(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                // Some look-and-feels deliver activation on click rather than press.
                handleTradeActionMouse(e);
            }
        });
        TableCellHoverClickSupport.install(
                tradeTable,
                COL_TRADE_ACTION,
                passThroughEnabledSupplier,
                HOVER_CLICK_DELAY_MS,
                Math.max(12, TRADE_ACTION_COL_WIDTH / 2),
                this::startAutoTradeAt);

        goalsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        shoppingTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tradeTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        shoppingSorter = createShoppingSorter();
        installSortableTable(shoppingTable, shoppingSorter);

        JPanel goalsPanel = buildGoalsPanel(base, fontSize);
        this.goalsPanel = goalsPanel;
        JPanel materialsPanel = buildMaterialsPanel(base, fontSize);
        JPanel tradePanel = buildTradePanel(base, fontSize);

        lowerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tradePanel, materialsPanel);
        configureSplitPane(lowerSplit, 0.45);
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

        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));

        JPanel shipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        shipRow.setOpaque(false);
        shipRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel shipLbl = new JLabel("Ship:");
        shipLbl.setFont(base.deriveFont(Font.PLAIN, fontSize));
        shipLbl.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
        shipRow.add(shipLbl);
        shipFilterCombo = new JComboBox<>();
        styleShipFilterCombo(shipFilterCombo, base);
        shipFilterCombo.setToolTipText("Filter which goals are listed (materials and trades always use all ships)");
        shipFilterCombo.addActionListener(e -> onShipFilterChanged());
        shipRow.add(shipFilterCombo);
        north.add(shipRow);
        north.add(Box.createVerticalStrut(4));

        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(sectionHeader("Goals", base, fontSize), BorderLayout.WEST);
        north.add(header);

        JPanel buttonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        buttonsRow.setOpaque(false);
        buttonsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton addMatsBtn = new JButton("Add Materials Goal");
        OverlayOutlineButtonStyle.applyChip(addMatsBtn, base, false);
        addMatsBtn.setToolTipText("Reserve materials (mission requests, stockpile) beyond engineering crafts");
        addMatsBtn.addActionListener(e -> openAddMaterialsGoalDialog());
        HoverClickPoller.register(addMatsBtn, HOVER_CLICK_DELAY_MS, this::openAddMaterialsGoalDialog,
                passThroughEnabledSupplier);
        buttonsRow.add(addMatsBtn);
        JButton loadoutBtn = new JButton("Add Goal via Loadout");
        OverlayOutlineButtonStyle.applyChip(loadoutBtn, base, false);
        loadoutBtn.setToolTipText("Build progress — grade / multi-module craft status and fitted engineering");
        loadoutBtn.addActionListener(e -> openBuildProgressDialog());
        HoverClickPoller.register(loadoutBtn, HOVER_CLICK_DELAY_MS, this::openBuildProgressDialog, passThroughEnabledSupplier);
        buttonsRow.add(loadoutBtn);
        JButton addBtn = new JButton("Add a goal");
        OverlayOutlineButtonStyle.applyChip(addBtn, base, false);
        addBtn.addActionListener(e -> openAddGoalDialog());
        HoverClickPoller.register(addBtn, HOVER_CLICK_DELAY_MS, this::openAddGoalDialog, passThroughEnabledSupplier);
        buttonsRow.add(addBtn);
        north.add(buttonsRow);

        p.add(north, BorderLayout.NORTH);
        p.add(goalsScroll = wrapScroll(goalsTable, 120), BorderLayout.CENTER);

        rebuildShipFilterCombo();
        return p;
    }

    private JPanel buildMaterialsPanel(Font base, int fontSize) {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setOpaque(false);
        p.add(sectionHeader("Materials Required", base, fontSize), BorderLayout.NORTH);
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
        styleMutedLabel(tradeStatusLabel, base, fontSize);
        tradeStatusLabel.setBorder(new EmptyBorder(2, 4, 0, 4));
        content.add(tradeStatusLabel, BorderLayout.SOUTH);
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
        int wEnabled = priorityColumnWidth();
        int wPriority = priorityColumnWidth();
        int wEdit = EDIT_COL_WIDTH;
        int wDelete = DELETE_COL_WIDTH;
        int rowSample = Math.max(goalsTable.getRowCount(), 1);
        int wTarget;
        int wExp;
        goalsTable.putClientProperty(MEASURING_COLUMNS_PROP, Boolean.TRUE);
        try {
            wTarget = clampColumnWidth(goalsTable, COL_GOAL_TARGET, 40, 80, rowSample);
            wExp = clampColumnWidth(goalsTable, COL_GOAL_EXP, 76, 220, rowSample);
        } finally {
            goalsTable.putClientProperty(MEASURING_COLUMNS_PROP, null);
        }
        int wStatus = measureGoalStatusColumnWidth();
        int minBlueprint = 40;
        int fixed = wEnabled + wPriority + wEdit + wDelete + wTarget + wExp + wStatus;
        int wBlueprint = avail - fixed;
        if (wBlueprint < minBlueprint) {
            wBlueprint = minBlueprint;
            int overflow = fixed + wBlueprint - avail;
            if (overflow > 0) {
                int take = Math.min(overflow, wTarget - 40);
                wTarget -= take;
                overflow -= take;
                take = Math.min(overflow, wExp - 76);
                wExp -= take;
                wBlueprint = Math.max(minBlueprint,
                        avail - wEnabled - wPriority - wEdit - wDelete - wTarget - wExp - wStatus);
            }
        }
        setColumnPixelWidth(goalsTable, COL_GOAL_ENABLED, wEnabled);
        setColumnPixelWidth(goalsTable, COL_GOAL_PRIORITY, wPriority);
        setColumnPixelWidth(goalsTable, COL_GOAL_BLUEPRINT, wBlueprint);
        setColumnPixelWidth(goalsTable, COL_GOAL_TARGET, wTarget);
        setColumnPixelWidth(goalsTable, COL_GOAL_EXP, wExp);
        setColumnPixelWidth(goalsTable, COL_GOAL_STATUS, wStatus);
        setColumnPixelWidth(goalsTable, COL_GOAL_EDIT, wEdit);
        setColumnPixelWidth(goalsTable, COL_GOAL_DELETE, wDelete);
        installCenteredRightNumberColumn(goalsTable, COL_GOAL_TARGET, false);
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
        Component capCell = cellRenderer.getTableCellRendererComponent(
                goalsTable, GOAL_STATUS_WIDTH_CAP_TEXT, false, false, 0, COL_GOAL_STATUS);
        int maxWidth = Math.max(header.getPreferredSize().width, capCell.getPreferredSize().width) + 10;
        int width = header.getPreferredSize().width + 10;
        int rows = goalsTable.getRowCount();
        for (int row = 0; row < rows; row++) {
            Object value = goalsTable.getValueAt(row, COL_GOAL_STATUS);
            Component cell = cellRenderer.getTableCellRendererComponent(
                    goalsTable, value, false, false, row, COL_GOAL_STATUS);
            width = Math.max(width, cell.getPreferredSize().width + 10);
        }
        return Math.max(header.getPreferredSize().width + 10, Math.min(maxWidth, width));
    }

    private void applyShoppingTableColumnLayout() {
        int avail = viewportWidth(shoppingScroll);
        if (avail <= 0 || shoppingTable == null) {
            return;
        }
        // Unlock so previous locks do not block re-measure.
        for (int c = 0; c < shoppingTable.getColumnCount(); c++) {
            TableColumn col = shoppingTable.getColumnModel().getColumn(c);
            col.setMinWidth(15);
            col.setMaxWidth(Integer.MAX_VALUE);
        }
        int rowSample = Math.max(shoppingTable.getRowCount(), 1);
        int wType;
        int wNeed;
        int wHave;
        int wShort;
        shoppingTable.putClientProperty(MEASURING_COLUMNS_PROP, Boolean.TRUE);
        try {
            wType = clampColumnWidth(shoppingTable, COL_TYPE, 48, 120, rowSample);
            wNeed = clampColumnWidth(shoppingTable, COL_NEED, 40, 72, rowSample);
            wHave = clampColumnWidth(shoppingTable, COL_HAVE, 40, 72, rowSample);
            wShort = clampColumnWidth(shoppingTable, COL_SHORT, 56, 100, rowSample);
        } finally {
            shoppingTable.putClientProperty(MEASURING_COLUMNS_PROP, null);
        }
        wNeed = Math.max(wNeed, headerMinWidth(shoppingTable, "Need"));
        wHave = Math.max(wHave, headerMinWidth(shoppingTable, "Have"));
        wShort = Math.max(wShort, headerMinWidth(shoppingTable, "Still need"));

        int minMaterial = 72;
        int wMaterial = avail - wType - wNeed - wHave - wShort;
        if (wMaterial < minMaterial) {
            wMaterial = minMaterial;
            int overflow = wType + wNeed + wHave + wShort + wMaterial - avail;
            if (overflow > 0) {
                int take = Math.min(overflow, Math.max(0, wType - 48));
                wType -= take;
                overflow -= take;
                // Prefer keeping number headers fully visible; Material can scroll horizontally if tiny.
                if (overflow > 0) {
                    wMaterial = Math.max(40, wMaterial - overflow);
                }
            }
        }
        setColumnPixelWidth(shoppingTable, COL_MATERIAL, wMaterial);
        setColumnPixelWidth(shoppingTable, COL_TYPE, wType);
        setColumnPixelWidth(shoppingTable, COL_NEED, wNeed);
        setColumnPixelWidth(shoppingTable, COL_HAVE, wHave);
        setColumnPixelWidth(shoppingTable, COL_SHORT, wShort);
        installCenteredRightNumberColumn(shoppingTable, COL_NEED, false);
        installCenteredRightNumberColumn(shoppingTable, COL_HAVE, false);
        installCenteredRightNumberColumn(shoppingTable, COL_SHORT, false);
        pinScrollLeft(shoppingScroll);
    }

    private static int headerMinWidth(JTable table, String headerText) {
        if (table == null || table.getTableHeader() == null || headerText == null) {
            return 40;
        }
        JTableHeader header = table.getTableHeader();
        java.awt.FontMetrics fm = header.getFontMetrics(header.getFont());
        return fm.stringWidth(headerText) + HEADER_SORT_CHROME_PAD;
    }

    private void applyTradeTableColumnLayout() {
        int avail = viewportWidth(tradeScroll);
        if (avail <= 0 || tradeTable == null) {
            return;
        }
        int wAction = TRADE_ACTION_COL_WIDTH;
        int flexAvail = Math.max(0, avail - wAction);
        int rowSample = Math.max(tradeTable.getRowCount(), 1);
        int wNeed;
        int wReceive;
        int minMaterial = 72;
        int wMaterial;
        int minGive = 96;
        int wGive;
        tradeTable.putClientProperty(MEASURING_COLUMNS_PROP, Boolean.TRUE);
        try {
            wNeed = measureTradeDataColumnWidth(COL_TRADE_NEED, 42, 68, rowSample);
            wReceive = measureTradeDataColumnWidth(COL_TRADE_RECEIVE, 48, 72, rowSample);
            wMaterial = measureTradeDataColumnWidth(COL_TRADE_MATERIAL, minMaterial, 220, rowSample);
            wGive = measureTradeDataColumnWidth(COL_TRADE_GIVE, minGive, 280, rowSample);
        } finally {
            tradeTable.putClientProperty(MEASURING_COLUMNS_PROP, null);
        }
        int used = wNeed + wReceive + wMaterial + wGive;
        if (used < flexAvail) {
            wGive += flexAvail - used;
        } else if (used > flexAvail) {
            int overflow = used - flexAvail;
            int take = Math.min(overflow, Math.max(0, wGive - minGive));
            wGive -= take;
            overflow -= take;
            take = Math.min(overflow, Math.max(0, wMaterial - minMaterial));
            wMaterial -= take;
        }
        setColumnPixelWidth(tradeTable, COL_TRADE_MATERIAL, wMaterial);
        setColumnPixelWidth(tradeTable, COL_TRADE_NEED, wNeed);
        setColumnPixelWidth(tradeTable, COL_TRADE_GIVE, wGive);
        setColumnPixelWidth(tradeTable, COL_TRADE_ACTION, wAction);
        setColumnPixelWidth(tradeTable, COL_TRADE_RECEIVE, wReceive);
        tradeTable.getColumnModel().getColumn(COL_TRADE_ACTION).setMinWidth(wAction);
        tradeTable.getColumnModel().getColumn(COL_TRADE_ACTION).setMaxWidth(wAction);
        ensureHeaderFullyVisible(tradeTable, COL_TRADE_NEED, "Need");
        ensureHeaderFullyVisible(tradeTable, COL_TRADE_RECEIVE, "Receive");
        // Header bumps may overflow viewport — shrink Give/Material only.
        int afterHeader = tradeTable.getColumnModel().getColumn(COL_TRADE_NEED).getPreferredWidth()
                + tradeTable.getColumnModel().getColumn(COL_TRADE_RECEIVE).getPreferredWidth()
                + tradeTable.getColumnModel().getColumn(COL_TRADE_MATERIAL).getPreferredWidth()
                + tradeTable.getColumnModel().getColumn(COL_TRADE_GIVE).getPreferredWidth()
                + wAction;
        if (afterHeader > avail) {
            int overflow = afterHeader - avail;
            int giveW = tradeTable.getColumnModel().getColumn(COL_TRADE_GIVE).getPreferredWidth();
            int take = Math.min(overflow, Math.max(0, giveW - minGive));
            if (take > 0) {
                setColumnPixelWidth(tradeTable, COL_TRADE_GIVE, giveW - take);
                overflow -= take;
            }
            int matW = tradeTable.getColumnModel().getColumn(COL_TRADE_MATERIAL).getPreferredWidth();
            take = Math.min(overflow, Math.max(0, matW - minMaterial));
            if (take > 0) {
                setColumnPixelWidth(tradeTable, COL_TRADE_MATERIAL, matW - take);
            }
        }
        installCenteredRightNumberColumn(tradeTable, COL_TRADE_NEED, false);
        installCenteredRightNumberColumn(tradeTable, COL_TRADE_RECEIVE, false);
        // Centering widening may overflow — shrink Give/Material only.
        int afterCenter = tradeTable.getColumnModel().getColumn(COL_TRADE_NEED).getPreferredWidth()
                + tradeTable.getColumnModel().getColumn(COL_TRADE_RECEIVE).getPreferredWidth()
                + tradeTable.getColumnModel().getColumn(COL_TRADE_MATERIAL).getPreferredWidth()
                + tradeTable.getColumnModel().getColumn(COL_TRADE_GIVE).getPreferredWidth()
                + wAction;
        if (afterCenter > avail) {
            int overflow = afterCenter - avail;
            int giveW = tradeTable.getColumnModel().getColumn(COL_TRADE_GIVE).getPreferredWidth();
            int take = Math.min(overflow, Math.max(0, giveW - minGive));
            if (take > 0) {
                setColumnPixelWidth(tradeTable, COL_TRADE_GIVE, giveW - take);
                overflow -= take;
            }
            int matW = tradeTable.getColumnModel().getColumn(COL_TRADE_MATERIAL).getPreferredWidth();
            take = Math.min(overflow, Math.max(0, matW - minMaterial));
            if (take > 0) {
                setColumnPixelWidth(tradeTable, COL_TRADE_MATERIAL, matW - take);
            }
        }
        tradeTable.putClientProperty(TRADE_GIVE_QTY_WIDTH_PROP, Integer.valueOf(measureTradeGiveQtyDigitWidth()));
        pinScrollLeft(tradeScroll);
    }

    private int measureTradeGiveQtyDigitWidth() {
        if (tradeTable == null) {
            return 0;
        }
        java.awt.FontMetrics fm = tradeTable.getFontMetrics(tradeTable.getFont());
        int max = fm.stringWidth("0");
        for (int r = 0; r < tradeTable.getRowCount(); r++) {
            int modelRow = tradeTable.convertRowIndexToModel(r);
            if (tradeModel.isGapRow(modelRow) || tradeModel.isSectionRow(modelRow)) {
                continue;
            }
            Object give = tradeTable.getValueAt(r, COL_TRADE_GIVE);
            if (!(give instanceof String text) || text.isBlank()) {
                continue;
            }
            int space = text.indexOf(' ');
            String qty = space > 0 ? text.substring(0, space) : text.trim();
            if (!qty.isEmpty()) {
                max = Math.max(max, fm.stringWidth(qty));
            }
        }
        return max;
    }

    private void configureTradeNumberColumns() {
        if (tradeTable == null) {
            return;
        }
        TableColumnModel cm = tradeTable.getColumnModel();
        cm.getColumn(COL_TRADE_NEED).setHeaderRenderer(new CenteredNumberHeaderRenderer());
        cm.getColumn(COL_TRADE_RECEIVE).setHeaderRenderer(new CenteredNumberHeaderRenderer());
    }

    /**
     * Numbers are right-aligned within a shared digit block; that block is centered in the column.
     */
    private void installCenteredRightNumberColumn(JTable table, int column, boolean boldDigits) {
        if (table == null || column < 0 || column >= table.getColumnCount()) {
            return;
        }
        TableColumn col = table.getColumnModel().getColumn(column);
        int block = measureMaxDigitBlockWidth(table, column, boldDigits);
        table.putClientProperty(digitBlockKey(column), block);
        // Leave room so the centered digit block is visible (not flush against column edges).
        int minForCenter = block + NUMBER_COL_EDGE_PAD * 2 + 28;
        int w = Math.max(col.getPreferredWidth(), minForCenter);
        setColumnPixelWidth(table, column, w);
        col.setHeaderRenderer(new CenteredNumberHeaderRenderer());
    }

    private static String digitBlockKey(int column) {
        return DIGIT_BLOCK_WIDTH_PROP + "." + column;
    }

    private static int measureMaxDigitBlockWidth(JTable table, int column, boolean boldDigits) {
        Font font = table.getFont();
        if (boldDigits) {
            font = font.deriveFont(Font.BOLD);
        }
        java.awt.FontMetrics fm = table.getFontMetrics(font);
        int max = fm.stringWidth("0");
        for (int r = 0; r < table.getRowCount(); r++) {
            Object v = table.getValueAt(r, column);
            if (!(v instanceof Number)) {
                continue;
            }
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) {
                continue;
            }
            max = Math.max(max, fm.stringWidth(s));
        }
        return max + 2;
    }

    private void ensureHeaderFullyVisible(JTable table, int column, String headerText) {
        if (table == null || table.getTableHeader() == null
                || column < 0 || column >= table.getColumnCount()) {
            return;
        }
        JTableHeader header = table.getTableHeader();
        java.awt.FontMetrics fm = header.getFontMetrics(header.getFont());
        int need = fm.stringWidth(headerText) + HEADER_SORT_CHROME_PAD;
        TableColumn col = table.getColumnModel().getColumn(column);
        int w = Math.max(col.getPreferredWidth(), need);
        setColumnPixelWidth(table, column, w);
    }

    private static void clearCenteredRightNumberPaint(JLabel label) {
        label.putClientProperty(PAINT_CENTERED_RIGHT_PROP, null);
        label.putClientProperty(PAINT_DIGIT_BLOCK_PROP, null);
    }

    /**
     * Center a digit block in the column; right-align each value inside that block.
     * Uses custom paint (see {@link EdoTableCellRenderer}) — JLabel align/borders are unreliable
     * on Windows for this effect.
     */
    private static void applyCenteredRightNumberPadding(JLabel label, JTable table, int column) {
        String text = label.getText() != null ? label.getText().trim() : "";
        label.setText(text);
        // While auto-sizing, keep tight pads so column width tracks content, not prior column width.
        if (Boolean.TRUE.equals(table.getClientProperty(MEASURING_COLUMNS_PROP)) || text.isEmpty()) {
            clearCenteredRightNumberPaint(label);
            label.setHorizontalAlignment(SwingConstants.RIGHT);
            label.setBorder(new EmptyBorder(2, NUMBER_COL_EDGE_PAD + 2, 2, NUMBER_COL_EDGE_PAD + 2));
            return;
        }
        int modelCol = table.convertColumnIndexToModel(column);
        Integer blockObj = (Integer) table.getClientProperty(digitBlockKey(modelCol));
        java.awt.FontMetrics fm = label.getFontMetrics(label.getFont());
        int textW = fm.stringWidth(text);
        int block = blockObj != null
                ? Math.max(blockObj, textW)
                : Math.max(fm.stringWidth("000"), textW);
        label.putClientProperty(PAINT_CENTERED_RIGHT_PROP, Boolean.TRUE);
        label.putClientProperty(PAINT_DIGIT_BLOCK_PROP, Integer.valueOf(block));
        label.setHorizontalAlignment(SwingConstants.LEFT);
        label.setBorder(new EmptyBorder(2, NUMBER_COL_EDGE_PAD, 2, NUMBER_COL_EDGE_PAD));
    }

    /**
     * Give column: material name flows left; qty digits share a left-edge block and right-align
     * with each other inside that block.
     */
    private static void applyTradeGiveNumberPadding(JLabel label, JTable table) {
        label.setHorizontalAlignment(SwingConstants.LEFT);
        String text = label.getText() != null ? label.getText() : "";
        if (Boolean.TRUE.equals(table.getClientProperty(MEASURING_COLUMNS_PROP)) || text.isBlank()) {
            label.setBorder(new EmptyBorder(2, TRADE_GIVE_CELL_LEFT_PAD, 2, 6));
            return;
        }
        Integer maxQtyWObj = (Integer) table.getClientProperty(TRADE_GIVE_QTY_WIDTH_PROP);
        java.awt.FontMetrics fm = label.getFontMetrics(label.getFont());
        int space = text.indexOf(' ');
        String qty = space > 0 ? text.substring(0, space) : text;
        int qtyW = fm.stringWidth(qty);
        int maxQtyW = maxQtyWObj != null ? maxQtyWObj : qtyW;
        int left = TRADE_GIVE_CELL_LEFT_PAD + Math.max(0, maxQtyW - qtyW);
        label.setBorder(new EmptyBorder(2, left, 2, 6));
    }

    private static final class CenteredNumberHeaderRenderer extends DefaultTableCellRenderer {
        CenteredNumberHeaderRenderer() {
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            if (c instanceof JLabel label) {
                label.setOpaque(false);
                label.setForeground(EdoUi.User.MAIN_TEXT);
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setBorder(new EmptyBorder(2, 6, 4, 6));
            }
            return c;
        }
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
            if (tradeModel.isSectionRow(row) || tradeModel.isGapRow(row)) {
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

    private JTable createTradeOverlayTable(TradeTableModel model) {
        return new JTable(model) {
            private static final long serialVersionUID = 1L;

            @Override
            public int getRowHeight(int row) {
                int base = getRowHeight();
                if (row < 0) {
                    return base;
                }
                int modelRow = convertRowIndexToModel(row);
                if (model.isGapRow(modelRow)) {
                    return TRADE_SECTION_GAP_PX;
                }
                return base;
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Full-width rule under the last option of each material group (never mid-text).
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setColor(EdoUi.ED_ORANGE_LESS_TRANS);
                    int x2 = Math.max(getWidth(), getColumnModel().getTotalColumnWidth()) - 1;
                    for (int viewRow = 0; viewRow < getRowCount(); viewRow++) {
                        int modelRow = convertRowIndexToModel(viewRow);
                        if (!model.hasSeparatorAfter(modelRow)) {
                            continue;
                        }
                        Rectangle r = getCellRect(viewRow, 0, true);
                        int y = r.y + r.height - 1;
                        g2.drawLine(0, y, x2, y);
                    }
                } finally {
                    g2.dispose();
                }
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

    private JTable createGoalsOverlayTable(TableModel model) {
        return new JTable(model) {
            private static final long serialVersionUID = 1L;

            @Override
            public String getToolTipText(MouseEvent event) {
                Point p = event.getPoint();
                int row = rowAtPoint(p);
                int col = columnAtPoint(p);
                if (row >= 0 && col >= 0) {
                    int modelCol = convertColumnIndexToModel(col);
                    if (modelCol == COL_GOAL_ENABLED) {
                        Object value = getValueAt(row, col);
                        if (value instanceof Boolean enabled) {
                            return enabled
                                    ? "Included in materials and trades"
                                    : "Hidden from materials and trades";
                        }
                    }
                    if (modelCol == COL_GOAL_PRIORITY) {
                        Object value = getValueAt(row, col);
                        if (value instanceof GoalPriority priority) {
                            return priority.tooltip();
                        }
                    }
                    if (modelCol == COL_GOAL_STATUS) {
                        Object value = getValueAt(row, col);
                        if (value != null && STATUS_TRADES.equals(value.toString())) {
                            return "Trades available to complete";
                        }
                    }
                }
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

    /** Selective mouse mode: goals section, all table scrollers, and Trade action cells. */
    public boolean isPointerOverInteractiveRegion(Point screenPoint) {
        if (SelectiveHitSupport.containsScreenPoint(goalsPanel, screenPoint)) {
            return true;
        }
        for (JScrollPane sp : scrollPanesForPassThrough()) {
            if (SelectiveHitSupport.containsScreenPoint(sp, screenPoint)) {
                return true;
            }
        }
        return SelectiveHitSupport.isOverModelColumnCell(tradeTable, screenPoint, COL_TRADE_ACTION);
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
            th.setOpaque(!OverlayPreferences.overlayChromeRequestsTransparency(this));
            th.setForeground(EdoUi.User.MAIN_TEXT);
            th.setBackground(OverlayPreferences.overlayChromeRequestsTransparency(this)
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
        shipCatalog.bootstrapFromJournal(clientKey);
        rememberCurrentShipFromLoadout();
        for (EngineeringGoal g : goals) {
            shipCatalog.rememberGoal(g);
        }
        assignLegacyGoalsToCurrentShip();
        for (EngineeringShipRef ref : shipCatalog.all()) {
            refreshGoalShipLabels(ref.getShipId());
        }
        rebuildShipFilterCombo();
        if (EngineeringGoalProgress.bootstrapFromJournal(goals, clientKey, database)) {
            fireSessionChanged();
        }
        scheduleRefresh();
    }

    /** Re-read craft/loadout store into goals (e.g. after external journal changes). */
    public void refreshGoalProgressFromJournal() {
        String clientKey = EliteDangerousOverlay.clientKey;
        if (clientKey == null || clientKey.isBlank()) {
            return;
        }
        EngineeringCraftStore.reparseFromJournal(clientKey);
        if (EngineeringGoalProgress.bootstrapFromJournal(goals, clientKey, database)) {
            fireSessionChanged();
        }
        scheduleRefresh();
    }

    private void openBuildProgressDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        String clientKey = EliteDangerousOverlay.clientKey;
        // Dialog loads journal progress on a background thread; don't block the EDT with a full rescan first.
        EngineeringBuildProgressDialog.show(
                owner,
                List.copyOf(goals),
                shipCatalog,
                goalsShipFilterId,
                database,
                clientKey,
                passThroughEnabledSupplier,
                this::addGoalFromBuildProgress,
                () -> List.copyOf(goals));
    }

    private EngineeringGoal addGoalFromBuildProgress(EngineeringBuildProgressDialog.AddGoalRequest request) {
        if (request == null) {
            return null;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        EngineeringGoal goal = EngineeringGoalDialog.showForAdd(
                owner,
                database,
                passThroughEnabledSupplier,
                shipCatalog,
                request.ship(),
                new EngineeringGoalDialog.AddPrefill(
                        request.moduleType(),
                        request.blueprintName(),
                        request.moduleType(),
                        request.experimentalName(),
                        request.quantity()));
        if (goal != null) {
            goals.add(goal);
            shipCatalog.rememberGoal(goal);
            rebuildShipFilterCombo();
            refreshGoalProgressFromJournal();
            fireSessionChanged();
            refreshUi();
        }
        return goal;
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
                || type == EliteEventType.ENGINEER_CRAFT
                || type == EliteEventType.ENGINEER_CONTRIBUTION) {
            inventoryTracker.applyEvent(event);
            scheduleRefresh();
            if (type == EliteEventType.MATERIAL_TRADE && event instanceof MaterialTradeEvent tradeEvent) {
                tradeExecutor.onMaterialTrade(tradeEvent);
            }
        }
        if (type == EliteEventType.ENGINEER_CRAFT && event instanceof EngineerCraftEvent craft) {
            long shipId = -1L;
            LoadoutEvent latest = EliteOverlayTabbedPane.getLatestLoadout();
            if (latest != null && latest.getShipId() >= 0) {
                shipId = latest.getShipId();
            }
            String clientKey = EliteDangerousOverlay.clientKey;
            if (clientKey != null && !clientKey.isBlank() && shipId >= 0) {
                EngineeringCraftStore.rememberCraft(clientKey, craft, shipId);
            }
            if (EngineeringGoalProgress.applyCraft(goals, craft, database, shipId)) {
                fireSessionChanged();
                scheduleRefresh();
            }
        }
        if (type == EliteEventType.LOADOUT && event instanceof LoadoutEvent loadout) {
            shipCatalog.rememberLoadout(loadout);
            String clientKey = EliteDangerousOverlay.clientKey;
            if (clientKey != null && !clientKey.isBlank()) {
                EngineeringCraftStore.rememberLoadout(clientKey, loadout);
            }
            SwingUtilities.invokeLater(() -> {
                refreshGoalShipLabels(loadout.getShipId());
                rebuildShipFilterCombo();
                EngineeringGoalDialog.refreshActiveShipChoices();
                if (EngineeringGoalProgress.applyLoadout(goals, loadout, database)) {
                    fireSessionChanged();
                    refreshUi();
                } else {
                    fireSessionChanged();
                }
            });
        }
        if (type == EliteEventType.STORED_SHIPS && event instanceof StoredShipsEvent stored) {
            shipCatalog.rememberStoredShips(stored);
            rebuildShipFilterCombo();
            fireSessionChanged();
        }
        if (type == EliteEventType.SET_USER_SHIP_NAME && event instanceof SetUserShipNameEvent renamed) {
            shipCatalog.rememberSetUserShipName(renamed);
            SwingUtilities.invokeLater(() -> {
                refreshGoalShipLabels(renamed.getShipId());
                rebuildShipFilterCombo();
                EngineeringGoalDialog.refreshActiveShipChoices();
                fireSessionChanged();
                refreshUi();
            });
        }
    }

    private void refreshGoalShipLabels(long shipId) {
        if (shipId < 0) {
            return;
        }
        EngineeringShipRef ref = shipCatalog.get(shipId);
        if (ref == null) {
            return;
        }
        String label = shipCatalog.displayLabel(ref);
        for (int i = 0; i < goals.size(); i++) {
            EngineeringGoal g = goals.get(i);
            if (g != null && g.getShipId() == shipId) {
                goals.set(i, g.withShip(shipId, label));
            }
        }
        for (int i = 0; i < materialsGoals.size(); i++) {
            MaterialsGoal g = materialsGoals.get(i);
            if (g != null && g.getShipId() == shipId) {
                materialsGoals.set(i, g.withShip(shipId, label));
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
            p.setPriority(g.getPriority().name());
            p.setIncludeInPlanning(Boolean.valueOf(g.isIncludeInPlanning()));
            p.setExperimentalApplied(g.isExperimentalApplied());
            p.setQuantity(g.getQuantity());
            p.setCompletedUnits(g.getCompletedUnits());
            if (g.hasShip()) {
                p.setShipId(Long.valueOf(g.getShipId()));
                p.setShipLabel(g.getShipLabel());
            }
            persisted.add(p);
        }
        data.setGoals(persisted);
        List<MaterialsGoalPersisted> matsPersisted = new ArrayList<>();
        for (MaterialsGoal g : materialsGoals) {
            if (g == null || !g.isValid()) {
                continue;
            }
            MaterialsGoalPersisted p = new MaterialsGoalPersisted();
            p.setLabel(g.getLabel());
            p.setPriority(g.getPriority().name());
            p.setIncludeInPlanning(Boolean.valueOf(g.isIncludeInPlanning()));
            List<MaterialNeedPersisted> needs = new ArrayList<>();
            for (MaterialRequirement req : g.getMaterials()) {
                needs.add(new MaterialNeedPersisted(req.getKey(), req.getCount()));
            }
            p.setMaterials(needs);
            if (g.hasShip()) {
                p.setShipId(Long.valueOf(g.getShipId()));
                p.setShipLabel(g.getShipLabel());
            }
            matsPersisted.add(p);
        }
        data.setMaterialGoals(matsPersisted);
        List<ShipPersisted> ships = new ArrayList<>();
        for (EngineeringShipRef ref : shipCatalog.listSorted()) {
            ShipPersisted sp = new ShipPersisted();
            sp.setShipId(ref.getShipId());
            sp.setShipType(ref.getShipType());
            sp.setShipName(ref.getShipName());
            sp.setShipIdent(ref.getShipIdent());
            ships.add(sp);
        }
        data.setKnownShips(ships);
        data.setGoalsShipFilterId(goalsShipFilterId);
        state.setEngineering(data);
    }

    public void applySessionState(EdoSessionState state) {
        goals.clear();
        materialsGoals.clear();
        shipCatalog.clear();
        goalsShipFilterId = null;
        if (state != null && state.getEngineering() != null) {
            EngineeringSessionData eng = state.getEngineering();
            for (ShipPersisted sp : eng.knownShipsOrEmpty()) {
                if (sp != null && sp.getShipId() >= 0) {
                    shipCatalog.remember(new EngineeringShipRef(
                            sp.getShipId(), sp.getShipType(), sp.getShipName(), sp.getShipIdent()));
                }
            }
            goalsShipFilterId = eng.getGoalsShipFilterId();
            for (EngineeringGoalPersisted p : eng.goalsOrEmpty()) {
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
                        p.priorityOrDefault(),
                        p.isExperimentalApplied(),
                        p.getQuantity(),
                        p.getCompletedUnits(),
                        p.shipIdOrUnknown(),
                        p.getShipLabel(),
                        p.includeInPlanningOrDefault()));
                if (!goals.isEmpty()) {
                    shipCatalog.rememberGoal(goals.get(goals.size() - 1));
                }
            }
            for (MaterialsGoalPersisted p : eng.materialGoalsOrEmpty()) {
                if (p == null) {
                    continue;
                }
                List<MaterialRequirement> reqs = new ArrayList<>();
                for (MaterialNeedPersisted need : p.materialsOrEmpty()) {
                    if (need == null || need.getKey().isBlank() || need.getCount() <= 0) {
                        continue;
                    }
                    reqs.add(new MaterialRequirement(need.getKey(), need.getCount()));
                }
                MaterialsGoal goal = new MaterialsGoal(
                        p.getLabel(),
                        reqs,
                        p.priorityOrDefault(),
                        p.includeInPlanningOrDefault(),
                        p.shipIdOrUnknown(),
                        p.getShipLabel());
                if (goal.isValid()) {
                    materialsGoals.add(goal);
                    if (goal.hasShip()) {
                        EngineeringShipRef known = shipCatalog.get(goal.getShipId());
                        if (known == null) {
                            shipCatalog.remember(new EngineeringShipRef(
                                    goal.getShipId(), "", goal.getShipLabel(), ""));
                        }
                    }
                }
            }
        }
        rebuildShipFilterCombo();
        scheduleRefresh();
    }


    private List<GoalUiRow> goalsForUi() {
        List<GoalUiRow> visible = new ArrayList<>();
        for (int i = 0; i < goals.size(); i++) {
            EngineeringGoal g = goals.get(i);
            if (g == null) {
                continue;
            }
            if (goalsShipFilterId == null
                    || (g.hasShip() && g.getShipId() == goalsShipFilterId.longValue())) {
                visible.add(GoalUiRow.blueprint(g, i));
            }
        }
        for (int i = 0; i < materialsGoals.size(); i++) {
            MaterialsGoal g = materialsGoals.get(i);
            if (g == null) {
                continue;
            }
            // Unassigned materials goals are commander-wide and always visible.
            if (isMaterialsGoalVisibleForShipFilter(g, goalsShipFilterId)) {
                visible.add(GoalUiRow.materials(g, goals.size() + i));
            }
        }
        visible.sort(Comparator
                .comparingInt((GoalUiRow r) -> r.priority().sortRank())
                .thenComparingInt(GoalUiRow::sortIndex));
        return visible;
    }

    private GoalUiRow goalRowAtUi(int uiRow) {
        List<GoalUiRow> visible = goalsForUi();
        if (uiRow < 0 || uiRow >= visible.size()) {
            return null;
        }
        return visible.get(uiRow);
    }

    private record GoalUiRow(EngineeringGoal blueprint, MaterialsGoal materials, int sortIndex) {
        static GoalUiRow blueprint(EngineeringGoal goal, int sortIndex) {
            return new GoalUiRow(goal, null, sortIndex);
        }

        static GoalUiRow materials(MaterialsGoal goal, int sortIndex) {
            return new GoalUiRow(null, goal, sortIndex);
        }

        boolean isMaterials() {
            return materials != null;
        }

        GoalPriority priority() {
            if (blueprint != null) {
                return blueprint.getPriority();
            }
            return materials != null ? materials.getPriority() : GoalPriority.MEDIUM;
        }

        boolean isIncludeInPlanning() {
            if (blueprint != null) {
                return blueprint.isIncludeInPlanning();
            }
            return materials != null && materials.isIncludeInPlanning();
        }

        boolean isEnabled() {
            return isIncludeInPlanning();
        }
    }

    /**
     * Ship filter visibility for materials goals: unassigned (commander-wide) always show;
     * assigned show when filter is All or matches that ship.
     */
    static boolean isMaterialsGoalVisibleForShipFilter(MaterialsGoal goal, Long goalsShipFilterId) {
        if (goal == null) {
            return false;
        }
        if (!goal.hasShip() || goalsShipFilterId == null) {
            return true;
        }
        return goal.getShipId() == goalsShipFilterId.longValue();
    }

    private EngineeringShipRef currentShipRef() {
        LoadoutEvent loadout = EliteOverlayTabbedPane.getLatestLoadout();
        EngineeringShipRef ref = EngineeringShipCatalog.fromLoadout(loadout);
        if (ref.isKnown()) {
            shipCatalog.remember(ref);
        }
        return ref;
    }

    private void rememberCurrentShipFromLoadout() {
        EngineeringShipRef ref = currentShipRef();
        if (ref.isKnown()) {
            shipCatalog.remember(ref);
        }
    }

    private void assignLegacyGoalsToCurrentShip() {
        EngineeringShipRef current = currentShipRef();
        if (!current.isKnown()) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < goals.size(); i++) {
            EngineeringGoal g = goals.get(i);
            if (g != null && !g.hasShip()) {
                goals.set(i, g.withShip(current.getShipId(), shipCatalog.displayLabel(current)));
                changed = true;
            }
        }
        if (changed) {
            fireSessionChanged();
        }
    }

    private void rebuildShipFilterCombo() {
        if (shipFilterCombo == null) {
            return;
        }
        Long keep = goalsShipFilterId;
        Set<Long> shipsWithGoals = new HashSet<>();
        for (EngineeringGoal g : goals) {
            if (g != null && g.hasShip()) {
                shipsWithGoals.add(Long.valueOf(g.getShipId()));
            }
        }
        for (MaterialsGoal g : materialsGoals) {
            if (g != null && g.hasShip()) {
                shipsWithGoals.add(Long.valueOf(g.getShipId()));
            }
        }
        List<EngineeringShipRef> withGoals = new ArrayList<>();
        List<EngineeringShipRef> withoutGoals = new ArrayList<>();
        for (EngineeringShipRef ref : shipCatalog.listSorted()) {
            if (shipsWithGoals.contains(Long.valueOf(ref.getShipId()))) {
                withGoals.add(ref);
            } else {
                withoutGoals.add(ref);
            }
        }
        shipFilterCombo.removeAllItems();
        shipFilterCombo.addItem(ShipFilterItem.all());
        for (EngineeringShipRef ref : withGoals) {
            shipFilterCombo.addItem(ShipFilterItem.ship(ref, shipCatalog.displayLabel(ref)));
        }
        if (!withGoals.isEmpty() && !withoutGoals.isEmpty()) {
            shipFilterCombo.addItem(ShipFilterItem.separator());
        }
        for (EngineeringShipRef ref : withoutGoals) {
            shipFilterCombo.addItem(ShipFilterItem.ship(ref, shipCatalog.displayLabel(ref)));
        }
        ShipFilterItem select = findShipFilterItem(keep);
        shipFilterCombo.setSelectedItem(select);
        goalsShipFilterId = select.shipId();
        widenShipFilterComboToFitItems();
    }

    private void widenShipFilterComboToFitItems() {
        if (shipFilterCombo == null) {
            return;
        }
        Font font = shipFilterCombo.getFont();
        int maxText = 0;
        java.awt.FontMetrics fm = shipFilterCombo.getFontMetrics(font != null ? font : new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int i = 0; i < shipFilterCombo.getItemCount(); i++) {
            ShipFilterItem item = shipFilterCombo.getItemAt(i);
            if (item == null || item.isSeparator()) {
                continue;
            }
            maxText = Math.max(maxText, fm.stringWidth(item.label()));
        }
        Dimension pref = shipFilterCombo.getPreferredSize();
        int width = Math.max(260, maxText + 48);
        shipFilterCombo.setPreferredSize(new Dimension(width, pref.height));
        shipFilterCombo.revalidate();
    }

    private ShipFilterItem findShipFilterItem(Long shipId) {
        if (shipFilterCombo == null) {
            return ShipFilterItem.all();
        }
        if (shipId == null) {
            return ShipFilterItem.all();
        }
        for (int i = 0; i < shipFilterCombo.getItemCount(); i++) {
            ShipFilterItem item = shipFilterCombo.getItemAt(i);
            if (item != null && !item.isSeparator() && shipId.equals(item.shipId())) {
                return item;
            }
        }
        return ShipFilterItem.all();
    }

    private void onShipFilterChanged() {
        if (shipFilterCombo == null) {
            return;
        }
        ShipFilterItem item = (ShipFilterItem) shipFilterCombo.getSelectedItem();
        if (item != null && item.isSeparator()) {
            shipFilterCombo.setSelectedItem(findShipFilterItem(goalsShipFilterId));
            return;
        }
        Long next = item != null ? item.shipId() : null;
        if ((goalsShipFilterId == null && next == null)
                || (goalsShipFilterId != null && goalsShipFilterId.equals(next))) {
            return;
        }
        goalsShipFilterId = next;
        fireSessionChanged();
        refreshUi();
    }

    private void styleShipFilterCombo(JComboBox<ShipFilterItem> combo, Font base) {
        OverlayComboBoxStyle.apply(combo, base);
        combo.setMaximumRowCount(12);
        Dimension pref = combo.getPreferredSize();
        combo.setPreferredSize(new Dimension(Math.max(260, pref.width), pref.height));
        OverlayScrollPaneSupport.installSubtleScrollBarsOnComboPopup(combo);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                if (value instanceof ShipFilterItem item && item.isSeparator()) {
                    JPanel line = new JPanel() {
                        @Override
                        protected void paintComponent(java.awt.Graphics g) {
                            super.paintComponent(g);
                            g.setColor(EdoUi.Internal.separatorLineStrong());
                            int y = getHeight() / 2;
                            g.drawLine(6, y, getWidth() - 6, y);
                        }
                    };
                    line.setOpaque(true);
                    line.setBackground(EdoUi.User.PANEL_BG);
                    line.setPreferredSize(new Dimension(1, Math.max(8, base.getSize() / 2)));
                    return line;
                }
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                // Keep orange ink even when the combo is temporarily disabled.
                setEnabled(true);
                if (value instanceof ShipFilterItem item) {
                    setText(item.label());
                }
                setForeground(EdoUi.User.MAIN_TEXT);
                setBackground(isSelected ? EdoUi.ED_ORANGE_LESS_TRANS : EdoUi.User.PANEL_BG);
                setOpaque(true);
                return c;
            }
        });
    }

    private void showGoalPriorityChooser(int modelRow) {
        GoalUiRow row = goalRowAtUi(modelRow);
        if (row == null || goalsTable == null) {
            return;
        }
        GoalPriority current = row.priority();
        int viewRow = -1;
        for (int r = 0; r < goalsTable.getRowCount(); r++) {
            if (goalsTable.convertRowIndexToModel(r) == modelRow) {
                viewRow = r;
                break;
            }
        }
        if (viewRow < 0) {
            return;
        }
        int viewCol = -1;
        for (int c = 0; c < goalsTable.getColumnCount(); c++) {
            if (goalsTable.convertColumnIndexToModel(c) == COL_GOAL_PRIORITY) {
                viewCol = c;
                break;
            }
        }
        if (viewCol < 0) {
            return;
        }

        JPopupMenu menu = new JPopupMenu();
        menu.setOpaque(true);
        menu.setBackground(EdoUi.User.PANEL_BG);
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(EdoUi.ED_ORANGE_TRANS, 1),
                new EmptyBorder(2, 2, 2, 2)));
        for (GoalPriority option : GoalPriority.chooserValues()) {
            JMenuItem item = new JMenuItem(option.menuLabel(), iconForGoalPriority(option));
            item.setOpaque(true);
            item.setBackground(EdoUi.User.PANEL_BG);
            item.setForeground(EdoUi.User.MAIN_TEXT);
            item.setEnabled(option != current);
            final GoalPriority chosen = option;
            item.addActionListener(e -> goalsModel.setValueAt(chosen, modelRow, COL_GOAL_PRIORITY));
            menu.add(item);
        }
        Rectangle cell = goalsTable.getCellRect(viewRow, viewCol, true);
        menu.show(goalsTable, cell.x, cell.y + cell.height);
    }

    private void toggleGoalEnabled(int modelRow) {
        GoalUiRow row = goalRowAtUi(modelRow);
        if (row == null) {
            return;
        }
        boolean next = !row.isEnabled();
        if (row.isMaterials()) {
            MaterialsGoal goal = row.materials();
            MaterialsGoal updated = goal.withEnabled(next);
            int fullIdx = materialsGoals.indexOf(goal);
            if (fullIdx >= 0) {
                materialsGoals.set(fullIdx, updated);
            }
        } else {
            EngineeringGoal goal = row.blueprint();
            EngineeringGoal updated = goal.withEnabled(next);
            int fullIdx = goals.indexOf(goal);
            if (fullIdx >= 0) {
                goals.set(fullIdx, updated);
            }
        }
        fireSessionChanged();
        scheduleRefresh();
    }

    private static Icon iconForGoalPriority(GoalPriority priority) {
        GoalPriority p = GoalPriority.normalize(priority);
        return switch (p) {
            case HIGH -> PriorityArrowIcon.HIGH;
            case MEDIUM, DISABLED -> PriorityArrowIcon.MEDIUM;
            case LOW -> PriorityArrowIcon.LOW;
        };
    }

    /**
     * Like {@link JTable#rowAtPoint} but forgiving of a slight vertical miss below the last row
     * (small tables make the toggle cells easy to miss when clicking in Selective mouse mode).
     */
    private static int rowAtPointWithSlack(JTable table, Point point) {
        int row = table.rowAtPoint(point);
        if (row >= 0) {
            return row;
        }
        int rowCount = table.getRowCount();
        if (rowCount <= 0 || point.x < 0 || point.x >= table.getWidth()) {
            return -1;
        }
        Rectangle lastRowRect = table.getCellRect(rowCount - 1, 0, true);
        int bottom = lastRowRect.y + lastRowRect.height;
        int slack = Math.max(6, table.getRowHeight() / 2);
        if (point.y >= bottom && point.y < bottom + slack) {
            return rowCount - 1;
        }
        return -1;
    }

    /** Pencil column, or a short strip into the status column (larger click/hover target). */
    private static boolean isEditHit(JTable table, Point point, int viewRow) {
        if (table == null || point == null || viewRow < 0) {
            return false;
        }
        int editViewCol = -1;
        for (int c = 0; c < table.getColumnCount(); c++) {
            if (table.convertColumnIndexToModel(c) == COL_GOAL_EDIT) {
                editViewCol = c;
                break;
            }
        }
        if (editViewCol < 0) {
            return false;
        }
        Rectangle cell = table.getCellRect(viewRow, editViewCol, true);
        Rectangle hit = new Rectangle(
                cell.x - EDIT_HIT_EXPAND_LEFT_PX,
                cell.y,
                cell.width + EDIT_HIT_EXPAND_LEFT_PX,
                cell.height);
        return hit.contains(point);
    }

    private static boolean isDeleteHit(JTable table, Point point, int viewRow) {
        if (table == null || point == null || viewRow < 0) {
            return false;
        }
        int deleteViewCol = -1;
        for (int c = 0; c < table.getColumnCount(); c++) {
            if (table.convertColumnIndexToModel(c) == COL_GOAL_DELETE) {
                deleteViewCol = c;
                break;
            }
        }
        if (deleteViewCol < 0) {
            return false;
        }
        return table.getCellRect(viewRow, deleteViewCol, true).contains(point);
    }

    private void openAddGoalDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        EngineeringShipRef equipped = currentShipRef();
        EngineeringShipRef defaultShip = resolveAddGoalDefaultShip(equipped);
        EngineeringGoal goal = EngineeringGoalDialog.showForAdd(
                owner, database, passThroughEnabledSupplier, shipCatalog, defaultShip);
        if (goal != null) {
            goals.add(goal);
            shipCatalog.rememberGoal(goal);
            rebuildShipFilterCombo();
            // Same as edit: replay crafts/loadouts so G-level + materials match the journal.
            refreshGoalProgressFromJournal();
            fireSessionChanged();
            refreshUi();
        }
    }

    private void openAddMaterialsGoalDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        EngineeringShipRef equipped = currentShipRef();
        MaterialsGoal goal = EngineeringMaterialsGoalDialog.showForAdd(
                owner,
                database,
                passThroughEnabledSupplier,
                shipCatalog,
                equipped,
                inventoryTracker.snapshot());
        if (goal != null && goal.isValid()) {
            materialsGoals.add(goal);
            if (goal.hasShip()) {
                EngineeringShipRef known = shipCatalog.get(goal.getShipId());
                if (known == null) {
                    shipCatalog.remember(new EngineeringShipRef(
                            goal.getShipId(), "", goal.getShipLabel(), ""));
                }
            }
            rebuildShipFilterCombo();
            fireSessionChanged();
            refreshUi();
        }
    }

    /**
     * Prefer the last manually chosen Add Goal ship while still in the same equipped ship;
     * when the equipped ship changes (or first time), follow the current loadout.
     */
    private EngineeringShipRef resolveAddGoalDefaultShip(EngineeringShipRef equipped) {
        long equippedId = equipped != null && equipped.isKnown() ? equipped.getShipId() : -1L;
        Long baseline = OverlayPreferences.getEngineeringAddGoalEquippedBaselineId();
        if (equippedId >= 0L && (baseline == null || baseline.longValue() != equippedId)) {
            OverlayPreferences.setEngineeringAddGoalPreferredShipId(equippedId);
            OverlayPreferences.setEngineeringAddGoalEquippedBaselineId(equippedId);
            return equipped;
        }
        Long preferred = OverlayPreferences.getEngineeringAddGoalPreferredShipId();
        if (preferred != null) {
            EngineeringShipRef fromCatalog = shipCatalog.get(preferred.longValue());
            if (fromCatalog != null && fromCatalog.isKnown()) {
                return fromCatalog;
            }
        }
        return equipped;
    }

    private void openEditGoalDialog(int modelRow) {
        GoalUiRow row = goalRowAtUi(modelRow);
        if (row == null) {
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        if (row.isMaterials()) {
            MaterialsGoal existing = row.materials();
            MaterialsGoal updated = EngineeringMaterialsGoalDialog.showForEdit(
                    owner,
                    database,
                    passThroughEnabledSupplier,
                    shipCatalog,
                    existing,
                    inventoryTracker.snapshot());
            if (updated != null && updated.isValid() && !updated.equals(existing)) {
                int fullIdx = materialsGoals.indexOf(existing);
                if (fullIdx >= 0) {
                    materialsGoals.set(fullIdx, updated);
                }
                rebuildShipFilterCombo();
                fireSessionChanged();
                refreshUi();
            }
            return;
        }
        EngineeringGoal existing = row.blueprint();
        EngineeringGoal updated = EngineeringGoalDialog.showForEdit(
                owner, database, passThroughEnabledSupplier, existing, shipCatalog);
        if (updated != null && !updated.equals(existing)) {
            int fullIdx = goals.indexOf(existing);
            if (fullIdx >= 0) {
                goals.set(fullIdx, updated);
            }
            shipCatalog.rememberGoal(updated);
            rebuildShipFilterCombo();
            // Recompute craft/unit progress for the new target so Status + materials stay in sync.
            refreshGoalProgressFromJournal();
            fireSessionChanged();
            refreshUi();
        }
    }

    private void removeGoalAt(int modelRow) {
        GoalUiRow row = goalRowAtUi(modelRow);
        if (row == null) {
            return;
        }
        if (row.isMaterials()) {
            materialsGoals.remove(row.materials());
        } else {
            goals.remove(row.blueprint());
        }
        rebuildShipFilterCombo();
        fireSessionChanged();
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        SwingUtilities.invokeLater(this::refreshUi);
    }

    private void refreshUi() {
        Map<String, Integer> inv = inventoryTracker.snapshot();
        List<GoalUiRow> visibleGoals = goalsForUi();
        // Materials / trades / priority claim always use every active goal (all ships).
        // Ship filter only changes which goals appear in the Goals table.
        List<EngineeringGoal> planningGoals = activeGoalsForPlanning();
        List<MaterialsGoal> planningMats = activeMaterialsGoalsForPlanning();
        Map<String, Integer> shortfalls = planner.shortfalls(planningGoals, planningMats, inv);
        EngineeringPlanner.PriorityPlanResult plan =
                planner.planByPriority(planningGoals, planningMats, inv, tradePlanner);
        List<TradeSuggestion> trades = new ArrayList<>(plan.trades());
        Map<String, Integer> invAfterTrades = plan.inventoryAfterTrades();
        List<ShoppingListRow> shopping =
                planner.buildShoppingList(planningGoals, planningMats, inv, invAfterTrades);

        Map<EngineeringGoal, GoalReadiness> readinessByBlueprint = plan.readinessByBlueprintGoal();
        Map<MaterialsGoal, GoalReadiness> readinessByMaterials = plan.readinessByMaterialsGoal();
        goalReadiness.clear();
        goalStatusText.clear();
        for (GoalUiRow row : visibleGoals) {
            if (!row.isIncludeInPlanning()) {
                goalReadiness.add(GoalReadiness.READY);
                goalStatusText.add(STATUS_HIDDEN);
                continue;
            }
            if (row.isMaterials()) {
                MaterialsGoal goal = row.materials();
                GoalReadiness readiness = readinessByMaterials.getOrDefault(goal,
                        planner.goalReadiness(goal, inv, invAfterTrades));
                goalReadiness.add(readiness);
                goalStatusText.add(formatMaterialsGoalStatusText(readiness));
            } else {
                EngineeringGoal goal = row.blueprint();
                GoalReadiness readiness = readinessByBlueprint.getOrDefault(goal,
                        planner.goalReadiness(goal, inv, invAfterTrades));
                goalReadiness.add(readiness);
                goalStatusText.add(formatStatusText(goal, readiness, invAfterTrades));
            }
        }

        shoppingModel.setRows(shopping);
        Map<String, Integer> shortfallsAfterTrades =
                planner.shortfalls(planningGoals, planningMats, invAfterTrades);
        updateTradeTable(trades, shortfalls, shortfallsAfterTrades);
        goalsModel.fireTableDataChanged();

        boolean hasVisibleGoals = !visibleGoals.isEmpty();
        boolean hasGoals = !goals.isEmpty() || !materialsGoals.isEmpty();
        boolean hasActiveGoals = !planningGoals.isEmpty() || !planningMats.isEmpty();
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
        // Grade edits can flip shopping from empty→populated; force layout so the list appears.
        revalidate();
        repaint();

        if (!hasGoals) {
            materialsEmptyLabel.setText("<html><body style='color:#ffcc88'>Add a goal to see required materials.</body></html>");
            tradeEmptyLabel.setText("<html><body style='color:#ffcc88'>Trade Suggestions appear when you have material shortfalls.</body></html>");
        } else if (!hasVisibleGoals) {
            materialsEmptyLabel.setText("<html><body style='color:#ffcc88'>No goals for the selected ship.</body></html>");
            tradeEmptyLabel.setText("<html><body style='color:#ffcc88'>No goals for the selected ship.</body></html>");
        } else if (shopping.isEmpty()) {
            materialsEmptyLabel.setForeground(EdoUi.User.MAIN_TEXT);
            materialsEmptyLabel.setText("No materials required");
        } else {
            materialsEmptyLabel.setText("");
        }
        if (hasGoals && trades.isEmpty() && !shortfalls.isEmpty()) {
            tradeEmptyLabel.setText("<html><body style='color:#ffcc88'>No material-trader swaps found from current inventory.</body></html>");
        } else if (hasGoals && trades.isEmpty()) {
            tradeEmptyLabel.setForeground(EdoUi.User.MAIN_TEXT);
            tradeEmptyLabel.setText("No trades required");
        }
        applyEngineeringTableColumnLayouts();
    }

    /** Active (non-disabled) goals across all ships — used for materials, trades, and priority claim. */
    private List<EngineeringGoal> activeGoalsForPlanning() {
        List<EngineeringGoal> out = new ArrayList<>();
        for (EngineeringGoal g : goals) {
            if (g != null && g.isIncludeInPlanning()) {
                out.add(g);
            }
        }
        return out;
    }

    private List<MaterialsGoal> activeMaterialsGoalsForPlanning() {
        List<MaterialsGoal> out = new ArrayList<>();
        for (MaterialsGoal g : materialsGoals) {
            if (g != null && g.isIncludeInPlanning() && g.isValid()) {
                out.add(g);
            }
        }
        return out;
    }

    private void updateTradeTable(List<TradeSuggestion> trades,
                                  Map<String, Integer> shortfalls,
                                  Map<String, Integer> shortfallsAfterTrades) {
        List<TradeTableRow> rows = new ArrayList<>();
        Map<String, List<TradeTargetGroup>> grouped =
                MaterialTradePlanner.groupByTraderTypeAndTarget(trades, shortfalls);
        for (Map.Entry<String, List<TradeTargetGroup>> entry : grouped.entrySet()) {
            List<TradeTargetGroup> targets = entry.getValue();
            if (targets == null || targets.isEmpty()) {
                continue;
            }
            if (!rows.isEmpty()) {
                rows.add(TradeTableRow.gap());
            }
            rows.add(TradeTableRow.section(traderTypeSectionTitle(entry.getKey())));
            for (TradeTargetGroup group : targets) {
                boolean uncovered = shortfallRemaining(shortfallsAfterTrades, group.getToKey()) > 0;
                List<TradeSuggestion> options = group.getOptions();
                for (int i = 0; i < options.size(); i++) {
                    TradeSuggestion option = options.get(i);
                    boolean firstOption = i == 0;
                    boolean lastOption = i == options.size() - 1;
                    rows.add(TradeTableRow.data(
                            firstOption ? group.getToName() : "",
                            firstOption ? Integer.valueOf(group.getShortfall()) : null,
                            formatTradeGive(option),
                            option.getToCount(),
                            uncovered,
                            lastOption,
                            option));
                }
            }
        }
        // Sort red groups to the top within each section already happens in groupByTarget using
        // option-sum coverage; re-sort here using post-trade shortfall so priority-planned rows match.
        tradeModel.setRows(reorderTradeRowsUncoveredFirst(rows));
    }

    private static int shortfallRemaining(Map<String, Integer> shortfallsAfterTrades, String materialKey) {
        if (shortfallsAfterTrades == null || materialKey == null || materialKey.isBlank()) {
            return 0;
        }
        Integer exact = shortfallsAfterTrades.get(materialKey);
        if (exact != null && exact > 0) {
            return exact;
        }
        String want = EngineeringMaterialKeys.canonicalKey(materialKey);
        for (Map.Entry<String, Integer> e : shortfallsAfterTrades.entrySet()) {
            if (e.getKey() != null
                    && EngineeringMaterialKeys.canonicalKey(e.getKey()).equals(want)
                    && e.getValue() != null
                    && e.getValue() > 0) {
                return e.getValue();
            }
        }
        return 0;
    }

    /** Keep section/gap structure; within each data block put still-short targets first. */
    private static List<TradeTableRow> reorderTradeRowsUncoveredFirst(List<TradeTableRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<TradeTableRow> out = new ArrayList<>(rows.size());
        int i = 0;
        while (i < rows.size()) {
            TradeTableRow row = rows.get(i);
            if (row.section() || row.gapRow()) {
                out.add(row);
                i++;
                continue;
            }
            int start = i;
            while (i < rows.size() && !rows.get(i).section() && !rows.get(i).gapRow()) {
                i++;
            }
            List<TradeTableRow> block = new ArrayList<>(rows.subList(start, i));
            // Group consecutive option rows that share a material (Need only on first).
            List<List<TradeTableRow>> groups = new ArrayList<>();
            List<TradeTableRow> current = new ArrayList<>();
            for (TradeTableRow r : block) {
                if (r.need() != null || current.isEmpty()) {
                    if (!current.isEmpty()) {
                        groups.add(current);
                    }
                    current = new ArrayList<>();
                }
                current.add(r);
            }
            if (!current.isEmpty()) {
                groups.add(current);
            }
            groups.sort(Comparator.comparingInt((List<TradeTableRow> g) ->
                    g.get(0).shortfallUncovered() ? 0 : 1));
            for (List<TradeTableRow> g : groups) {
                out.addAll(g);
            }
        }
        return out;
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

    private static String formatTradeGive(TradeSuggestion trade) {
        return trade.getFromCount() + " " + trade.getFromName();
    }

    private void handleTradeActionMouse(MouseEvent e) {
        if (e == null || !SwingUtilities.isLeftMouseButton(e) || e.isConsumed()) {
            return;
        }
        if (passThroughEnabledSupplier != null && passThroughEnabledSupplier.getAsBoolean()) {
            // OS click-through: real clicks never reach Swing; hover-click handles activation.
            return;
        }
        int row = tradeTable.rowAtPoint(e.getPoint());
        if (row < 0) {
            return;
        }
        int modelRow = tradeTable.convertRowIndexToModel(row);
        int viewCol = tradeTable.columnAtPoint(e.getPoint());
        int modelCol = viewCol >= 0 ? tradeTable.convertColumnIndexToModel(viewCol) : -1;
        if (modelCol != COL_TRADE_ACTION) {
            return;
        }
        e.consume();
        startAutoTradeAt(modelRow);
    }

    private void startAutoTradeAt(int modelRow) {
        if (tradeAutomationRunning) {
            setTradeStatus("Trade already in progress…", false);
            return;
        }
        List<TradeSuggestion> suggestions = tradeModel.tradesForActionAt(modelRow);
        if (suggestions.isEmpty()) {
            setTradeStatus("No trade on this row", false);
            return;
        }
        TradeSuggestion suggestion = suggestions.get(0);
        boolean tradeAll = suggestions.size() > 1 || tradeModel.isSectionRow(modelRow);
        Window owner = SwingUtilities.getWindowAncestor(this);
        setTradeStatus("Confirm trade…", false);
        tradeAutomationRunning = true;
        List<TradeSuggestion> toRun = List.copyOf(suggestions);
        MaterialTradeExecutor.Result result;
        try {
            MaterialTradeConfirmDialog.TradeAction action = dialogStatus ->
                    tradeExecutor.executeAll(toRun, msg -> {
                        dialogStatus.accept(msg);
                        SwingUtilities.invokeLater(() -> setTradeStatus(msg, false));
                    });
            result = tradeAll
                    ? MaterialTradeConfirmDialog.executeAll(
                            owner, suggestion.getTraderType(), toRun, action)
                    : MaterialTradeConfirmDialog.execute(owner, suggestion, action);
        } finally {
            tradeAutomationRunning = false;
        }
        if (result == null) {
            setTradeStatus(" ", false);
        } else {
            setTradeStatus(result.message(), result.ok());
        }
    }

    private void setTradeStatus(String text, boolean success) {
        String msg = text != null && !text.isBlank() ? text : " ";
        tradeStatusLabel.setText(msg);
        if (success) {
            tradeStatusLabel.setForeground(EdoUi.User.SUCCESS);
        } else if (msg.contains("Could not") || msg.contains("not focused") || msg.contains("Timed out")
                || msg.contains("failed") || msg.contains("Error") || msg.contains("KEY")) {
            tradeStatusLabel.setForeground(EdoUi.User.ERROR);
        } else {
            tradeStatusLabel.setForeground(EdoUi.User.MAIN_TEXT);
        }
        System.out.println("EDO auto-trade status: " + msg);
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
            return STATUS_COMPLETE;
        }
        return switch (readiness) {
            case READY -> STATUS_READY;
            case READY_WITH_TRADES -> STATUS_TRADES;
            case STILL_SHORT -> STATUS_SHORT;
        };
    }

    private static String formatMaterialsGoalStatusText(GoalReadiness readiness) {
        // Materials reserves have no craft step — covered stock is "Complete" until the user deletes the row.
        return switch (readiness) {
            case READY -> STATUS_COMPLETE;
            case READY_WITH_TRADES -> STATUS_TRADES;
            case STILL_SHORT -> STATUS_SHORT;
        };
    }

    private static class EdoTableCellRenderer extends DefaultTableCellRenderer {
        EdoTableCellRenderer() {
            setOpaque(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            // Shared renderer instance: always clear number-paint mode before column-specific setup.
            clearCenteredRightNumberPaint(this);
            Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
            if (c instanceof JLabel label) {
                label.setOpaque(false);
                label.setForeground(EdoUi.User.MAIN_TEXT);
                label.setBackground(EdoUi.Internal.TRANSPARENT);
                label.setBorder(new EmptyBorder(2, 6, 2, 6));
            }
            return c;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (!Boolean.TRUE.equals(getClientProperty(PAINT_CENTERED_RIGHT_PROP))) {
                super.paintComponent(g);
                return;
            }
            if (isOpaque()) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            String text = getText();
            if (text == null || text.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setFont(getFont());
                g2.setColor(getForeground());
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int textW = fm.stringWidth(text);
                Integer blockObj = (Integer) getClientProperty(PAINT_DIGIT_BLOCK_PROP);
                int block = blockObj != null ? Math.max(blockObj, textW) : textW;
                java.awt.Insets in = getInsets();
                int innerW = Math.max(0, getWidth() - in.left - in.right);
                int x = in.left + Math.max(0, (innerW - block) / 2 + (block - textW));
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(text, x, y);
            } finally {
                g2.dispose();
            }
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

    private static final class GoalEnabledHeaderRenderer extends DefaultTableCellRenderer {
        GoalEnabledHeaderRenderer() {
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, "", false, false, row, column);
            if (c instanceof JLabel label) {
                label.setOpaque(false);
                label.setIcon(OverlayCheckBoxStyle.selectedIcon());
                label.setText(null);
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setToolTipText("Enabled — include in materials and trades");
                label.setBorder(new EmptyBorder(2, 2, 4, 2));
            }
            return c;
        }
    }

    private final class GoalEnabledCellRenderer extends JLabel implements javax.swing.table.TableCellRenderer {
        private static final long serialVersionUID = 1L;

        GoalEnabledCellRenderer() {
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            boolean enabled = !(value instanceof Boolean b) || b;
            setIcon(enabled ? OverlayCheckBoxStyle.selectedIcon() : OverlayCheckBoxStyle.unselectedIcon());
            setToolTipText(enabled
                    ? "Included in materials and trades"
                    : "Hidden from materials and trades");
            return this;
        }
    }

    private static final class GoalPriorityHeaderRenderer extends DefaultTableCellRenderer {
        GoalPriorityHeaderRenderer() {
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, "", false, false, row, column);
            if (c instanceof JLabel label) {
                label.setOpaque(false);
                label.setIcon(PriorityHeaderIcon.INSTANCE);
                label.setText(null);
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setToolTipText("Priority");
                label.setBorder(new EmptyBorder(2, 2, 4, 2));
            }
            return c;
        }
    }

    private final class GoalPriorityCellRenderer extends JLabel implements javax.swing.table.TableCellRenderer {
        private static final long serialVersionUID = 1L;

        GoalPriorityCellRenderer() {
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            GoalPriority priority = value instanceof GoalPriority p
                    ? GoalPriority.normalize(p)
                    : GoalPriority.MEDIUM;
            setIcon(iconForGoalPriority(priority));
            setToolTipText(priority.tooltip());
            return this;
        }
    }

    private static final class GoalTargetCellRenderer extends EdoTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
            if (c instanceof JLabel label) {
                if (value != null) {
                    String text = value.toString();
                    label.setToolTipText(text.isBlank() || "—".equals(text) ? null : text);
                }
                applyCenteredRightNumberPadding(label, table, column);
            }
            return c;
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
            if (!(c instanceof JLabel label)) {
                return c;
            }
            String text = value != null ? value.toString() : "";
            label.setIconTextGap(6);
            label.setHorizontalTextPosition(SwingConstants.RIGHT);
            label.setVerticalTextPosition(SwingConstants.CENTER);

            if (text.isBlank()) {
                label.setToolTipText(null);
                label.setIcon(null);
            } else if (STATUS_TRADES.equals(text)) {
                label.setToolTipText("Trades available to complete");
            } else {
                label.setToolTipText(text);
            }

            int modelRow = table.convertRowIndexToModel(row);
            int modelCol = table.convertColumnIndexToModel(column);
            if (!isSelected && modelCol == COL_GOAL_STATUS) {
                if (STATUS_HIDDEN.equals(text) || (modelRow >= 0 && modelRow < goalStatusText.size()
                        && STATUS_HIDDEN.equals(goalStatusText.get(modelRow)))) {
                    label.setIcon(null);
                    label.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
                } else if (STATUS_COMPLETE.equals(text)) {
                    label.setIcon(STATUS_ICON_OK);
                    label.setForeground(EdoUi.User.SUCCESS);
                } else if (STATUS_READY.equals(text)) {
                    label.setIcon(STATUS_ICON_OK);
                    label.setForeground(EdoUi.User.SUCCESS);
                } else if (STATUS_TRADES.equals(text)) {
                    label.setIcon(STATUS_ICON_TRADES);
                    label.setForeground(Color.YELLOW);
                } else if (STATUS_SHORT.equals(text)) {
                    label.setIcon(STATUS_ICON_SHORT);
                    label.setForeground(EdoUi.User.ERROR);
                } else if (modelRow >= 0 && modelRow < goalReadiness.size()) {
                    // Cap-width / unknown sample text — use readiness when available.
                    Color color = switch (goalReadiness.get(modelRow)) {
                        case READY -> EdoUi.User.SUCCESS;
                        case READY_WITH_TRADES -> Color.YELLOW;
                        case STILL_SHORT -> EdoUi.User.ERROR;
                    };
                    label.setForeground(color);
                    label.setIcon(switch (goalReadiness.get(modelRow)) {
                        case READY -> STATUS_ICON_OK;
                        case READY_WITH_TRADES -> STATUS_ICON_TRADES;
                        case STILL_SHORT -> STATUS_ICON_SHORT;
                    });
                } else if (GOAL_STATUS_WIDTH_CAP_TEXT.equals(text)) {
                    // Measurement sample for "w/ Trades".
                    label.setIcon(STATUS_ICON_TRADES);
                    label.setForeground(Color.YELLOW);
                } else {
                    label.setIcon(null);
                }
            } else {
                label.setIcon(null);
            }
            return c;
        }
    }

    private final class BlueprintNameCellRenderer extends EdoTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
            if (c instanceof JLabel label) {
                int modelRow = table.convertRowIndexToModel(row);
                GoalUiRow goalRow = goalRowAtUi(modelRow);
                if (goalRow != null && goalRow.isMaterials()) {
                    label.setToolTipText(goalRow.materials().materialsTooltip(database));
                } else if (value != null) {
                    label.setToolTipText(value.toString());
                } else {
                    label.setToolTipText(null);
                }
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
            boolean editable = goalRowAtUi(modelRow) != null;
            setEnabled(editable);
            setIcon(editable ? PencilIcon.DEFAULT : null);
            return this;
        }
    }

    private final class GoalDeleteCellRenderer extends JLabel implements javax.swing.table.TableCellRenderer {
        private static final long serialVersionUID = 1L;

        GoalDeleteCellRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(false);
            setIcon(TrashIcon.DEFAULT);
            setToolTipText("Remove goal");
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            int modelRow = table.convertRowIndexToModel(row);
            boolean removable = goalRowAtUi(modelRow) != null;
            setEnabled(removable);
            setIcon(removable ? TrashIcon.DEFAULT : null);
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
            return goalsForUi().size();
        }

        @Override
        public int getColumnCount() {
            return 8;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == COL_GOAL_ENABLED) {
                return Boolean.class;
            }
            if (columnIndex == COL_GOAL_PRIORITY) {
                return GoalPriority.class;
            }
            if (columnIndex == COL_GOAL_EDIT || columnIndex == COL_GOAL_DELETE) {
                return Object.class;
            }
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            GoalUiRow row = goalRowAtUi(rowIndex);
            if (row == null) {
                return;
            }
            if (columnIndex == COL_GOAL_ENABLED) {
                boolean next = value instanceof Boolean b ? b : true;
                if (row.isMaterials()) {
                    MaterialsGoal goal = row.materials();
                    MaterialsGoal updated = goal.withEnabled(next);
                    if (!updated.equals(goal)) {
                        int fullIdx = materialsGoals.indexOf(goal);
                        if (fullIdx >= 0) {
                            materialsGoals.set(fullIdx, updated);
                        }
                        fireSessionChanged();
                        scheduleRefresh();
                    }
                } else {
                    EngineeringGoal goal = row.blueprint();
                    EngineeringGoal updated = goal.withEnabled(next);
                    if (!updated.equals(goal)) {
                        int fullIdx = goals.indexOf(goal);
                        if (fullIdx >= 0) {
                            goals.set(fullIdx, updated);
                        }
                        fireSessionChanged();
                        scheduleRefresh();
                    }
                }
                return;
            }
            if (columnIndex != COL_GOAL_PRIORITY) {
                return;
            }
            GoalPriority next = value instanceof GoalPriority p ? p : GoalPriority.MEDIUM;
            if (row.isMaterials()) {
                MaterialsGoal goal = row.materials();
                MaterialsGoal updated = goal.withPriority(next);
                if (!updated.equals(goal)) {
                    int fullIdx = materialsGoals.indexOf(goal);
                    if (fullIdx >= 0) {
                        materialsGoals.set(fullIdx, updated);
                    }
                    fireSessionChanged();
                    scheduleRefresh();
                }
                return;
            }
            EngineeringGoal goal = row.blueprint();
            EngineeringGoal updated = goal.withPriority(next);
            if (!updated.equals(goal)) {
                int fullIdx = goals.indexOf(goal);
                if (fullIdx >= 0) {
                    goals.set(fullIdx, updated);
                }
                fireSessionChanged();
                scheduleRefresh();
            }
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case COL_GOAL_ENABLED -> "";
                case COL_GOAL_PRIORITY -> "";
                case COL_GOAL_BLUEPRINT -> "Blueprint";
                case COL_GOAL_TARGET -> "Target";
                case COL_GOAL_EXP -> "Experimental";
                case COL_GOAL_STATUS -> "Status";
                case COL_GOAL_EDIT -> "";
                case COL_GOAL_DELETE -> "";
                default -> "";
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            GoalUiRow row = goalRowAtUi(rowIndex);
            if (row == null) {
                return "";
            }
            if (columnIndex == COL_GOAL_ENABLED) {
                return Boolean.valueOf(row.isEnabled());
            }
            if (columnIndex == COL_GOAL_PRIORITY) {
                return row.priority();
            }
            if (columnIndex == COL_GOAL_EDIT || columnIndex == COL_GOAL_DELETE) {
                return "";
            }
            if (row.isMaterials()) {
                MaterialsGoal g = row.materials();
                return switch (columnIndex) {
                    case COL_GOAL_BLUEPRINT -> g.getLabel();
                    case COL_GOAL_TARGET -> g.targetSummary(database);
                    case COL_GOAL_EXP -> "—";
                    case COL_GOAL_STATUS -> rowIndex < goalStatusText.size() ? goalStatusText.get(rowIndex) : "";
                    default -> "";
                };
            }
            EngineeringGoal g = row.blueprint();
            return switch (columnIndex) {
                case COL_GOAL_BLUEPRINT -> blueprintDisplayName(g);
                case COL_GOAL_TARGET -> "G" + g.getTargetGrade();
                case COL_GOAL_EXP -> g.getExperimentalId().isBlank() ? "—" : experimentalName(g.getExperimentalId());
                case COL_GOAL_STATUS -> rowIndex < goalStatusText.size() ? goalStatusText.get(rowIndex) : "";
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
            if (c instanceof JLabel label) {
                if (column == COL_NEED || column == COL_HAVE || column == COL_SHORT) {
                    applyCenteredRightNumberPadding(label, table, column);
                } else {
                    // Shared renderer: reset after number columns so Material/Type stay left-aligned.
                    clearCenteredRightNumberPaint(label);
                    label.setHorizontalAlignment(SwingConstants.LEFT);
                    label.setBorder(new EmptyBorder(2, 6, 2, 6));
                    if (column == COL_MATERIAL && value != null) {
                        String text = value.toString();
                        label.setToolTipText(text.isBlank() ? null : text);
                    }
                }
            }
            if (!isSelected && column == COL_SHORT && value instanceof Integer shortfall) {
                if (shortfall > 0) {
                    c.setForeground(EdoUi.User.MAIN_TEXT);
                } else {
                    c.setForeground(EdoUi.User.SUCCESS);
                }
            }
            return c;
        }
    }

    private record TradeTableRow(String materialName, Integer need, String give, int receive, boolean section,
                                 boolean gapRow, boolean shortfallUncovered, boolean separatorAfter,
                                 TradeSuggestion suggestion) {
        static TradeTableRow section(String title) {
            return new TradeTableRow(title, null, "", 0, true, false, false, false, null);
        }

        static TradeTableRow gap() {
            return new TradeTableRow("", null, "", 0, false, true, false, false, null);
        }

        static TradeTableRow data(String materialName, Integer need, String give, int receive,
                boolean shortfallUncovered, boolean separatorAfter, TradeSuggestion suggestion) {
            return new TradeTableRow(materialName, need, give, receive, false, false,
                    shortfallUncovered, separatorAfter, suggestion);
        }
    }

    private final class TradeTableModel extends AbstractTableModel {
        private List<TradeTableRow> rows = List.of();

        void setRows(List<TradeTableRow> rows) {
            this.rows = rows != null ? List.copyOf(rows) : List.of();
            fireTableDataChanged();
        }

        TradeSuggestion suggestionAt(int rowIndex) {
            if (rowIndex < 0 || rowIndex >= rows.size()) {
                return null;
            }
            return rows.get(rowIndex).suggestion();
        }

        /**
         * A data row runs one trade. A trader-type section row runs every displayed
         * trade below it, in display order, up to the next section/gap.
         */
        List<TradeSuggestion> tradesForActionAt(int rowIndex) {
            if (rowIndex < 0 || rowIndex >= rows.size()) {
                return List.of();
            }
            TradeTableRow row = rows.get(rowIndex);
            if (!row.section()) {
                return row.suggestion() != null ? List.of(row.suggestion()) : List.of();
            }
            List<TradeSuggestion> trades = new ArrayList<>();
            for (int i = rowIndex + 1; i < rows.size(); i++) {
                TradeTableRow candidate = rows.get(i);
                if (candidate.section() || candidate.gapRow()) {
                    break;
                }
                if (candidate.suggestion() != null) {
                    trades.add(candidate.suggestion());
                }
            }
            return List.copyOf(trades);
        }

        boolean isSectionRow(int rowIndex) {
            return rowIndex >= 0 && rowIndex < rows.size() && rows.get(rowIndex).section();
        }

        boolean isGapRow(int rowIndex) {
            return rowIndex >= 0 && rowIndex < rows.size() && rows.get(rowIndex).gapRow();
        }

        boolean isShortfallUncovered(int rowIndex) {
            return rowIndex >= 0 && rowIndex < rows.size() && rows.get(rowIndex).shortfallUncovered();
        }

        /** True on the last trade option for a material — draw a full-width rule under this row. */
        boolean hasSeparatorAfter(int rowIndex) {
            return rowIndex >= 0 && rowIndex < rows.size() && rows.get(rowIndex).separatorAfter();
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
                case COL_TRADE_MATERIAL -> "Material";
                case COL_TRADE_NEED -> "Need";
                case COL_TRADE_GIVE -> "Give";
                case COL_TRADE_ACTION -> "Trade";
                case COL_TRADE_RECEIVE -> "Receive";
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == COL_TRADE_NEED || columnIndex == COL_TRADE_RECEIVE) {
                return Integer.class;
            }
            if (columnIndex == COL_TRADE_ACTION) {
                return Object.class;
            }
            return String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            TradeTableRow row = rows.get(rowIndex);
            if (row.gapRow()) {
                return null;
            }
            if (row.section()) {
                return switch (columnIndex) {
                    case COL_TRADE_MATERIAL -> row.materialName();
                    case COL_TRADE_ACTION -> tradesForActionAt(rowIndex).isEmpty() ? "" : "Trade All";
                    default -> null;
                };
            }
            return switch (columnIndex) {
                case COL_TRADE_MATERIAL -> row.materialName();
                case COL_TRADE_NEED -> row.need();
                case COL_TRADE_GIVE -> row.give();
                case COL_TRADE_ACTION -> row.suggestion() != null ? "Trade" : "";
                case COL_TRADE_RECEIVE -> row.receive();
                default -> "";
            };
        }
    }

    private final class TradeActionCellRenderer extends JLabel implements javax.swing.table.TableCellRenderer {
        private static final long serialVersionUID = 1L;

        TradeActionCellRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(false);
            setForeground(EdoUi.User.MAIN_TEXT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            int modelRow = table.convertRowIndexToModel(row);
            boolean section = tradeModel.isSectionRow(modelRow);
            boolean actionable = !tradeModel.tradesForActionAt(modelRow).isEmpty()
                    && !tradeModel.isGapRow(modelRow);
            setText(actionable ? (section ? "Trade All" : "Trade") : "");
            setEnabled(actionable && !tradeAutomationRunning);
            boolean mpt = passThroughEnabledSupplier != null && passThroughEnabledSupplier.getAsBoolean();
            setToolTipText(actionable
                    ? (mpt
                            ? "Hover ~0.5s to run "
                                    + (section ? "all trades in this section" : "this trade")
                                    + " (mouse pass-through is on)"
                            : "Click to run "
                                    + (section ? "all trades in this section" : "this trade")
                                    + " in the material trader UI")
                    : null);
            setFont(table.getFont().deriveFont(Font.BOLD));
            setForeground(EdoUi.User.MAIN_TEXT);
            return this;
        }
    }

    private final class TradeCellRenderer extends EdoTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            int modelRow = table.convertRowIndexToModel(row);
            boolean gap = tradeModel.isGapRow(modelRow);
            boolean section = tradeModel.isSectionRow(modelRow);
            Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
            if (c instanceof JLabel label) {
                if (gap) {
                    label.setText("");
                    clearCenteredRightNumberPaint(label);
                    label.setBorder(new EmptyBorder(0, 0, 0, 0));
                    return c;
                }
                if (section) {
                    clearCenteredRightNumberPaint(label);
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
                if (column == COL_TRADE_ACTION) {
                    clearCenteredRightNumberPaint(label);
                    label.setText("");
                    label.setBorder(new EmptyBorder(0, 0, 0, 0));
                    return c;
                }
                if (column == COL_TRADE_MATERIAL && value != null) {
                    clearCenteredRightNumberPaint(label);
                    String text = value.toString();
                    label.setHorizontalAlignment(SwingConstants.LEFT);
                    label.setToolTipText(text.isBlank() ? null : text);
                    label.setBorder(new EmptyBorder(2, 6, 2, 6));
                } else if (column == COL_TRADE_NEED || column == COL_TRADE_RECEIVE) {
                    if (value == null || (value instanceof String s && s.isBlank())) {
                        label.setText("");
                        clearCenteredRightNumberPaint(label);
                        label.setHorizontalAlignment(SwingConstants.RIGHT);
                        label.setBorder(new EmptyBorder(2, NUMBER_COL_EDGE_PAD, 2, NUMBER_COL_EDGE_PAD));
                    } else {
                        applyCenteredRightNumberPadding(label, table, column);
                    }
                } else if (column == COL_TRADE_GIVE) {
                    clearCenteredRightNumberPaint(label);
                    applyTradeGiveNumberPadding(label, table);
                    if (value != null) {
                        label.setToolTipText(value.toString());
                    }
                } else {
                    clearCenteredRightNumberPaint(label);
                    label.setHorizontalAlignment(SwingConstants.LEFT);
                    label.setBorder(new EmptyBorder(2, 6, 2, 6));
                }
            }
            if (!isSelected && !gap && !section && tradeModel.isShortfallUncovered(modelRow)) {
                c.setForeground(EdoUi.User.ERROR);
            } else if (!isSelected && column == COL_TRADE_NEED && value instanceof Integer need && need > 0) {
                c.setForeground(new Color(255, 160, 120));
            } else if (!isSelected && (column == COL_TRADE_GIVE || column == COL_TRADE_RECEIVE)) {
                c.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
            }
            return c;
        }
    }

    private record ShipFilterItem(Long shipId, String label, boolean isSeparator) {
        static ShipFilterItem all() {
            return new ShipFilterItem(null, "All", false);
        }

        static ShipFilterItem ship(EngineeringShipRef ref, String label) {
            String text = label != null && !label.isBlank() ? label : ref.displayLabel();
            return new ShipFilterItem(Long.valueOf(ref.getShipId()), text, false);
        }

        static ShipFilterItem ship(EngineeringShipRef ref) {
            return ship(ref, ref.displayLabel());
        }

        static ShipFilterItem separator() {
            return new ShipFilterItem(null, "", true);
        }

        @Override
        public String toString() {
            return isSeparator ? "" : label;
        }
    }
}
