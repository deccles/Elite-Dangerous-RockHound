package org.dce.ed;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
import org.dce.ed.engineering.EngineeringGoalMerger;
import org.dce.ed.engineering.EngineeringJournalBlueprintResolver;
import org.dce.ed.engineering.EngineeringLoadoutExperimentalPatch;
import org.dce.ed.engineering.EngineeringShipCatalog;
import org.dce.ed.engineering.EngineeringShipRef;
import org.dce.ed.engineering.EngineeringGoalProgress;
import org.dce.ed.engineering.EngineeringGradeProgress;
import org.dce.ed.engineering.EngineeringInventoryTracker;
import org.dce.ed.engineering.EngineerReputationTracker;
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
import org.dce.ed.ui.WrapLayout;
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
import org.dce.ed.ui.TableHeaderHoverActionSupport;
import org.dce.ed.ui.TableCellToolTipSupport;
import org.dce.ed.ui.TableHeaderSortSupport;
import org.dce.ed.ui.TransparentTableHeader;
import org.dce.ed.ui.TransparentTableHeaderUI;
import org.dce.ed.ui.TransparentViewportUI;

/**
 * Engineering tab: goals, shopping list, and trade suggestions.
 */
public class EngineeringTabPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int HOVER_CLICK_DELAY_MS = 500;
    private static final int INCLUDE_HOVER_DELAY_MS = 350;
    private static int priorityColumnWidth() {
        // Just wide enough for the font-scaled glyph plus a slim pad; the cell spans the full
        // row height, so it stays a workable click target in Selective mouse mode.
        int iconSize = Math.max(14, OverlayPreferences.getUiFontSize() + 1);
        return iconSize + 12;
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
    private static final int COL_TRADE_RECEIVE = 3;
    private static final int COL_TRADE_ACTION = 4;
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
    private final EngineerReputationTracker reputationTracker = new EngineerReputationTracker();
    private final EngineeringPlanner planner = new EngineeringPlanner(database);
    private final MaterialTradePlanner tradePlanner = new MaterialTradePlanner(database);

    private final List<EngineeringGoal> goals = new ArrayList<>();
    private final List<MaterialsGoal> materialsGoals = new ArrayList<>();
    private final EngineeringShipCatalog shipCatalog = new EngineeringShipCatalog();
    /** null = show / plan for all ships. */
    private Long goalsShipFilterId;
    private JComboBox<ShipFilterItem> shipFilterCombo;
    /**
     * When true and a ship is selected, materials/trades only include that ship's goals.
     * Goals table filtering is independent (already ship-scoped via the chooser).
     */
    private boolean hideMatsFromOtherShips = true;
    private JCheckBox hideMatsFromOtherShipsCheckBox;

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
    private JPanel materialsPanel;
    private JLabel materialsHeaderLabel;
    private JPanel materialsContent;
    private JPanel materialsButtonRow;
    private JButton materialsToggleButton;
    /** Materials Required expanded, or collapsed to just the Show button. */
    private boolean materialsSectionVisible = OverlayPreferences.isEngineeringMaterialsSectionVisible();
    /** Saved trade/materials divider still needs restoring once the split has a real height. */
    private boolean lowerSplitDividerRestorePending;
    /** Saved Goals/(trades+mats) divider still needs restoring once the split has a real height. */
    private boolean mainSplitDividerRestorePending;

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
        // Two-line Blueprint cells (module + blueprint name).
        goalsTable.setRowHeight(Math.max(36, fontSize * 2 + 12));
        goalsTable.getColumnModel().getColumn(COL_GOAL_BLUEPRINT).setCellRenderer(new BlueprintNameCellRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_EXP).setCellRenderer(new ExperimentalEffectCellRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_TARGET).setCellRenderer(new GoalTargetCellRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_STATUS).setCellRenderer(new GoalStatusCellRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_ENABLED).setCellRenderer(new GoalEnabledCellRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_ENABLED).setHeaderRenderer(new GoalEnabledHeaderRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_PRIORITY).setCellRenderer(new GoalPriorityCellRenderer());
        goalsTable.getColumnModel().getColumn(COL_GOAL_PRIORITY).setHeaderRenderer(new GoalPriorityHeaderRenderer());
        goalsTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (passThroughEnabledSupplier != null && passThroughEnabledSupplier.getAsBoolean()) {
                    return;
                }
                JTableHeader header = goalsTable.getTableHeader();
                int viewCol = header.columnAtPoint(e.getPoint());
                if (viewCol < 0) {
                    return;
                }
                int modelCol = goalsTable.convertColumnIndexToModel(viewCol);
                if (modelCol != COL_GOAL_ENABLED) {
                    return;
                }
                e.consume();
                toggleAllVisibleGoalsEnabled();
            }
        });
        TableHeaderHoverActionSupport.install(
                goalsTable,
                COL_GOAL_ENABLED,
                passThroughEnabledSupplier,
                INCLUDE_HOVER_DELAY_MS,
                this::toggleAllVisibleGoalsEnabled);
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
        int tradeActionWidth = tradeActionColumnWidth();
        tradeActionCol.setMinWidth(tradeActionWidth);
        tradeActionCol.setMaxWidth(tradeActionWidth);
        tradeActionCol.setPreferredWidth(tradeActionWidth);
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
        lowerSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            // Only user/layout moves while expanded count as "where it was last time".
            if (materialsSectionVisible && !lowerSplitDividerRestorePending
                    && lowerSplit.getDividerSize() > 0 && lowerSplit.isShowing()
                    && lowerSplit.getDividerLocation() > 0) {
                OverlayPreferences.setEngineeringLowerSplitDividerLocation(lowerSplit.getDividerLocation());
            }
        });
        lowerSplit.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (lowerSplit.getHeight() <= 0) {
                    return;
                }
                if (materialsSectionVisible) {
                    if (lowerSplitDividerRestorePending) {
                        restoreLowerSplitDividerLocation();
                    }
                } else {
                    pinCollapsedLowerSplitDivider();
                }
            }
        });
        lowerSplitDividerRestorePending = materialsSectionVisible;
        applyLowerSplitForMaterialsSection();

        mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, goalsPanel, lowerSplit);
        configureSplitPane(mainSplit, 0.28);
        mainSplit.setFocusable(false);
        EdoMiningSplitPaneUi.install(mainSplit);
        mainSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            if (!mainSplitDividerRestorePending
                    && mainSplit.getDividerSize() > 0 && mainSplit.isShowing()
                    && mainSplit.getDividerLocation() > 0) {
                OverlayPreferences.setEngineeringMainSplitDividerLocation(mainSplit.getDividerLocation());
            }
        });
        mainSplit.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (mainSplit.getHeight() <= 0) {
                    return;
                }
                if (mainSplitDividerRestorePending) {
                    restoreMainSplitDividerLocation();
                }
            }
        });
        mainSplitDividerRestorePending = true;
        SwingUtilities.invokeLater(() -> {
            if (mainSplitDividerRestorePending && mainSplit.getHeight() > 0) {
                restoreMainSplitDividerLocation();
            }
        });

        add(mainSplit, BorderLayout.CENTER);

        SwingUtilities.invokeLater(() -> {
            stripAllEngineeringScrollChrome();
            OverlayTransparentChrome.applyToSubtree(this);
            logComponentColorAuditIfRequested();
            EdoMiningSplitPaneUi.applyDividerTheme(mainSplit);
            EdoMiningSplitPaneUi.applyDividerTheme(lowerSplit);
        });

        inventoryTracker.setChangeCallback(this::scheduleRefresh);
        reputationTracker.setChangeCallback(this::scheduleRefresh);
        installEngineeringTableLayoutListeners();
        refreshUi();
    }

    private JPanel buildGoalsPanel(Font base, int fontSize) {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setOpaque(false);

        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));

        JPanel shipRow = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 4));
        shipRow.setOpaque(false);
        shipRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        shipRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        shipRow.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                shipRow.revalidate();
            }
        });
        JLabel shipLbl = new JLabel("Ship:");
        shipLbl.setFont(base.deriveFont(Font.PLAIN, fontSize));
        shipLbl.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
        shipRow.add(shipLbl);
        shipFilterCombo = new JComboBox<>();
        styleShipFilterCombo(shipFilterCombo, base);
        shipFilterCombo.setToolTipText("Filter which goals are listed");
        shipFilterCombo.addActionListener(e -> onShipFilterChanged());
        shipRow.add(shipFilterCombo);
        hideMatsFromOtherShipsCheckBox = new JCheckBox("Hide other ship Mats");
        OverlayCheckBoxStyle.apply(hideMatsFromOtherShipsCheckBox);
        hideMatsFromOtherShipsCheckBox.setFont(base.deriveFont(Font.PLAIN, fontSize));
        hideMatsFromOtherShipsCheckBox.setSelected(hideMatsFromOtherShips);
        hideMatsFromOtherShipsCheckBox.setToolTipText(
                "When a ship is selected, Materials Required and Trade Suggestions show only that ship's goals — "
                        + "other ships' priorities still reserve materials so this ship cannot trade them away");
        hideMatsFromOtherShipsCheckBox.addActionListener(e -> {
            hideMatsFromOtherShips = hideMatsFromOtherShipsCheckBox.isSelected();
            fireSessionChanged();
            refreshUi();
        });
        HoverClickPoller.register(hideMatsFromOtherShipsCheckBox, INCLUDE_HOVER_DELAY_MS, () -> {
            if (hideMatsFromOtherShipsCheckBox.isEnabled()) {
                hideMatsFromOtherShipsCheckBox.doClick();
            }
        }, passThroughEnabledSupplier);
        shipRow.add(hideMatsFromOtherShipsCheckBox);
        syncHideMatsCheckboxEnabled();
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
        JButton loadoutBtn = new JButton("Loadout");
        OverlayOutlineButtonStyle.applyChip(loadoutBtn, base, false);
        loadoutBtn.setToolTipText(
                "View this ship's engineering status (gap / partial / done) and add goals from fitted modules");
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
        materialsPanel = new JPanel(new BorderLayout(4, 4));
        materialsPanel.setOpaque(false);
        materialsHeaderLabel = sectionHeader("Materials Required", base, fontSize);
        materialsContent = new JPanel(new BorderLayout(4, 4));
        materialsContent.setOpaque(false);
        materialsEmptyLabel.setBorder(new EmptyBorder(8, 4, 8, 4));
        materialsContent.add(materialsEmptyLabel, BorderLayout.NORTH);
        shoppingScroll = wrapScroll(shoppingTable, 160);
        materialsContent.add(shoppingScroll, BorderLayout.CENTER);

        materialsToggleButton = new JButton();
        // Hit-safe plate (like Kill scripts) so clicks land in hybrid / transparent modes.
        OverlayOutlineButtonStyle.applyPrimaryHitSafe(materialsToggleButton, base);
        materialsToggleButton.addActionListener(e -> toggleMaterialsSection());
        HoverClickPoller.register(materialsToggleButton, HOVER_CLICK_DELAY_MS,
                this::toggleMaterialsSection, passThroughEnabledSupplier);
        materialsButtonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        materialsButtonRow.setOpaque(false);
        materialsButtonRow.add(materialsToggleButton);

        applyMaterialsSectionLayout();
        return materialsPanel;
    }

    private void toggleMaterialsSection() {
        materialsSectionVisible = !materialsSectionVisible;
        OverlayPreferences.setEngineeringMaterialsSectionVisible(materialsSectionVisible);
        applyMaterialsSectionLayout();
        scheduleRefresh();
    }

    /**
     * Expanded: header + list with "Hide Required Materials" at the bottom.
     * Collapsed: just a "Show Required Materials" button directly below Trade Suggestions;
     * the rest of the area is empty (and punched fully transparent in hybrid mode —
     * see {@link #paint(Graphics)}).
     */
    private void applyMaterialsSectionLayout() {
        if (materialsPanel == null) {
            return;
        }
        materialsPanel.removeAll();
        if (materialsSectionVisible) {
            materialsToggleButton.setText("Hide Required Materials");
            materialsToggleButton.setToolTipText("Collapse the Materials Required list");
            materialsPanel.add(materialsHeaderLabel, BorderLayout.NORTH);
            materialsPanel.add(materialsContent, BorderLayout.CENTER);
            materialsPanel.add(materialsButtonRow, BorderLayout.SOUTH);
        } else {
            materialsToggleButton.setText("Show Required Materials");
            materialsToggleButton.setToolTipText("Show the Materials Required list");
            materialsPanel.add(materialsButtonRow, BorderLayout.NORTH);
        }
        applyLowerSplitForMaterialsSection();
        materialsPanel.revalidate();
        materialsPanel.repaint();
        revalidate();
        repaint();
    }

    /**
     * Collapsed: hide the divider and pin the materials area to the button strip so it sits
     * directly below Trade Suggestions (extra space goes to the trade panel). Expanded: bring the
     * orange divider back and restore its last position (persisted across runs).
     */
    private void applyLowerSplitForMaterialsSection() {
        if (lowerSplit == null) {
            return;
        }
        if (materialsSectionVisible) {
            lowerSplit.setDividerSize(9);
            lowerSplit.setResizeWeight(0.45);
            EdoMiningSplitPaneUi.applyDividerTheme(lowerSplit);
            lowerSplitDividerRestorePending = true;
            SwingUtilities.invokeLater(() -> {
                if (lowerSplitDividerRestorePending && lowerSplit.getHeight() > 0) {
                    restoreLowerSplitDividerLocation();
                }
            });
        } else {
            // Remember where the divider was before collapsing.
            if (!lowerSplitDividerRestorePending && lowerSplit.isShowing()
                    && lowerSplit.getDividerLocation() > 0) {
                OverlayPreferences.setEngineeringLowerSplitDividerLocation(lowerSplit.getDividerLocation());
            }
            lowerSplitDividerRestorePending = false;
            lowerSplit.setDividerSize(0);
            // All extra space goes below the divider (the punched-transparent band), so the
            // trade panel hugs its content and the Show button sits directly under it.
            lowerSplit.setResizeWeight(0.0);
            SwingUtilities.invokeLater(this::pinCollapsedLowerSplitDivider);
        }
    }

    /**
     * While collapsed, pin the divider to the trade panel's preferred height: the Show button
     * lands directly below the trade content and everything under it is empty (punched
     * transparent in hybrid mode).
     */
    private void pinCollapsedLowerSplitDivider() {
        if (lowerSplit == null || materialsSectionVisible || lowerSplit.getHeight() <= 0) {
            return;
        }
        Component top = lowerSplit.getTopComponent();
        if (top == null) {
            return;
        }
        int pref = top.getPreferredSize().height;
        // The trade scroller reports a fixed preferred height; substitute the table's real
        // content height so the panel shrinks to hug a short list and grows to fit a long one.
        // The max-divider clamp below means the scrollbar only appears once the content would
        // exceed the space the user has given the dialog.
        if (tradeScroll != null && tradeScroll.isVisible()) {
            int scrollPref = tradeScroll.getPreferredSize().height;
            JTableHeader header = tradeTable.getTableHeader();
            int contentH = tradeTable.getPreferredSize().height
                    + (header != null ? header.getPreferredSize().height : 0) + 4;
            pref = pref - scrollPref + contentH;
        }
        int min = lowerSplit.getMinimumDividerLocation();
        int max = lowerSplit.getMaximumDividerLocation();
        lowerSplit.setDividerLocation(Math.max(min, Math.min(pref, max)));
    }

    private void restoreLowerSplitDividerLocation() {
        lowerSplitDividerRestorePending = false;
        int saved = OverlayPreferences.getEngineeringLowerSplitDividerLocation();
        if (saved <= 0) {
            lowerSplit.resetToPreferredSizes();
            return;
        }
        int min = lowerSplit.getMinimumDividerLocation();
        int max = lowerSplit.getMaximumDividerLocation();
        lowerSplit.setDividerLocation(Math.max(min, Math.min(saved, max)));
    }

    private void restoreMainSplitDividerLocation() {
        mainSplitDividerRestorePending = false;
        if (mainSplit == null) {
            return;
        }
        int saved = OverlayPreferences.getEngineeringMainSplitDividerLocation();
        if (saved <= 0) {
            mainSplit.resetToPreferredSizes();
            return;
        }
        int min = mainSplit.getMinimumDividerLocation();
        int max = mainSplit.getMaximumDividerLocation();
        mainSplit.setDividerLocation(Math.max(min, Math.min(saved, max)));
    }

    /**
     * Selective (hybrid) mode with Materials Required collapsed: punch the whole materials area
     * fully transparent except the Show button (same idea as Control Panel's Kill scripts strip).
     */
    @Override
    public void paint(Graphics g) {
        super.paint(g);
        clearCollapsedMaterialsAreaInSelectiveMode(g);
    }

    private void clearCollapsedMaterialsAreaInSelectiveMode(Graphics g) {
        if (materialsSectionVisible || g == null
                || !TransparentViewportUI.isSelectivePassThroughContext(this)) {
            return;
        }
        if (materialsPanel == null || !materialsPanel.isShowing()
                || materialsToggleButton == null || !materialsToggleButton.isShowing()) {
            return;
        }
        Rectangle area = SwingUtilities.convertRectangle(
                materialsPanel.getParent(), materialsPanel.getBounds(), this);
        Rectangle keep = SwingUtilities.convertRectangle(
                materialsToggleButton.getParent(), materialsToggleButton.getBounds(), this);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
            // Full-width band from the top of the materials area to the tab's bottom edge,
            // minus the toggle button itself.
            if (keep.y > area.y) {
                g2.fillRect(0, area.y, getWidth(), keep.y - area.y);
            }
            if (keep.x > 0) {
                g2.fillRect(0, keep.y, keep.x, keep.height);
            }
            int keepRight = keep.x + keep.width;
            if (keepRight < getWidth()) {
                g2.fillRect(keepRight, keep.y, getWidth() - keepRight, keep.height);
            }
            int keepBottom = keep.y + keep.height;
            if (getHeight() > keepBottom) {
                g2.fillRect(0, keepBottom, getWidth(), getHeight() - keepBottom);
            }
        } finally {
            g2.dispose();
        }
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
            wTarget = clampColumnWidth(goalsTable, COL_GOAL_TARGET, 28, 80, rowSample);
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
                int take = Math.min(overflow, Math.max(0, wTarget - 28));
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
        installCenteredRightNumberColumn(goalsTable, COL_GOAL_TARGET, false, 8);
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
        // Progress bars need a stable minimum width; text statuses (Complete / Ready) are narrower.
        int width = Math.max(header.getPreferredSize().width + 10, 72);
        int rows = goalsTable.getRowCount();
        for (int row = 0; row < rows; row++) {
            Object value = goalsTable.getValueAt(row, COL_GOAL_STATUS);
            Component cell = cellRenderer.getTableCellRendererComponent(
                    goalsTable, value, false, false, row, COL_GOAL_STATUS);
            width = Math.max(width, cell.getPreferredSize().width + 10);
        }
        return Math.max(header.getPreferredSize().width + 10, Math.min(Math.max(maxWidth, 80), width));
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

    /** Wide enough for the bold "Trade All" label at the current font. */
    private int tradeActionColumnWidth() {
        if (tradeTable == null) {
            return TRADE_ACTION_COL_WIDTH;
        }
        Font bold = tradeTable.getFont().deriveFont(Font.BOLD);
        java.awt.FontMetrics fm = tradeTable.getFontMetrics(bold);
        return Math.max(TRADE_ACTION_COL_WIDTH, fm.stringWidth("Trade All") + 16);
    }

    private void applyTradeTableColumnLayout() {
        int avail = viewportWidth(tradeScroll);
        if (avail <= 0 || tradeTable == null) {
            return;
        }
        int wAction = tradeActionColumnWidth();
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
        installCenteredRightNumberColumn(table, column, boldDigits, 28);
    }

    /** @param centerSlackPx extra width beyond the digit block so centering is visible */
    private void installCenteredRightNumberColumn(JTable table, int column, boolean boldDigits, int centerSlackPx) {
        if (table == null || column < 0 || column >= table.getColumnCount()) {
            return;
        }
        TableColumn col = table.getColumnModel().getColumn(column);
        int block = measureMaxDigitBlockWidth(table, column, boldDigits);
        table.putClientProperty(digitBlockKey(column), block);
        // Leave room so the centered digit block is visible (not flush against column edges).
        int minForCenter = block + NUMBER_COL_EDGE_PAD * 2 + centerSlackPx;
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
                    if (modelCol == COL_GOAL_BLUEPRINT || modelCol == COL_GOAL_EXP) {
                        String effectTip = engineeringEffectTipAt(row, modelCol);
                        if (effectTip != null && !effectTip.isBlank()) {
                            return effectTip;
                        }
                    }
                    // Prefer renderer tooltip (effect descriptions) over raw cell text.
                    Component renderer = prepareRenderer(getCellRenderer(row, col), row, col);
                    if (renderer instanceof JComponent jc) {
                        String rendererTip = jc.getToolTipText();
                        if (rendererTip != null && !rendererTip.isBlank()) {
                            return rendererTip;
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
        if (materialsToggleButton != null) {
            // Hit plate color depends on the transparency state; re-apply like Control Panel does.
            OverlayOutlineButtonStyle.applyPrimaryHitSafe(materialsToggleButton, OverlayPreferences.getUiFont());
        }
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

    /**
     * Selective mouse mode: goals section, trade scroller, Trade action cells, and the Materials
     * toggle button. The Materials Required list is display-only, so it stays pass-through —
     * only its scrollbar (when present) accepts real clicks.
     */
    public boolean isPointerOverInteractiveRegion(Point screenPoint) {
        if (SelectiveHitSupport.containsScreenPoint(goalsPanel, screenPoint)) {
            return true;
        }
        if (SelectiveHitSupport.containsScreenPoint(tradeScroll, screenPoint)) {
            return true;
        }
        if (SelectiveHitSupport.containsScreenPoint(materialsToggleButton, screenPoint)) {
            return true;
        }
        if (isPointerOverSplitDivider(mainSplit, screenPoint)
                || isPointerOverSplitDivider(lowerSplit, screenPoint)) {
            return true;
        }
        if (shoppingScroll != null
                && OverlayScrollPaneSupport.isPointerOverScrollBar(shoppingScroll, screenPoint)) {
            return true;
        }
        return SelectiveHitSupport.isOverModelColumnCell(tradeTable, screenPoint, COL_TRADE_ACTION);
    }

    /** True when the pointer is over a visible split divider (orange bar), with a little slack. */
    private static boolean isPointerOverSplitDivider(JSplitPane split, Point screenPoint) {
        if (split == null || screenPoint == null || split.getDividerSize() <= 0
                || !split.isShowing()
                || !(split.getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI ui)) {
            return false;
        }
        Component divider = ui.getDivider();
        if (divider == null || !divider.isShowing()) {
            return false;
        }
        try {
            Point origin = divider.getLocationOnScreen();
            Rectangle r = new Rectangle(origin.x, origin.y, divider.getWidth(), divider.getHeight());
            r.grow(0, 3);
            return r.contains(screenPoint);
        } catch (java.awt.IllegalComponentStateException ex) {
            return false;
        }
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
        // Models that report Integer.class (e.g. trade Need/Receive) would otherwise fall back to
        // JTable's built-in Number renderer and skip the centered-right number treatment.
        table.setDefaultRenderer(Number.class, renderer);
        table.setDefaultRenderer(Integer.class, renderer);
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
        reputationTracker.bootstrapFromJournal(clientKey);
        shipCatalog.bootstrapFromJournal(clientKey);
        // Rebuild craft/loadout store before goal bootstrap so post-craft patches apply when
        // Elite never emitted a fresh Loadout (common after engineer sessions).
        if (clientKey != null && !clientKey.isBlank()) {
            EngineeringCraftStore.reparseFromJournal(clientKey);
        }
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
        // Same rule as Add Goal: honor the Engineering tab ship filter when set; only fall
        // back to the currently equipped ship when the filter is All.
        Long initialShipId = goalsShipFilterId;
        if (initialShipId == null) {
            EngineeringShipRef currentShip = currentShipRef();
            if (currentShip != null && currentShip.isKnown()) {
                initialShipId = Long.valueOf(currentShip.getShipId());
            }
        }
        // Dialog loads journal progress on a background thread; don't block the EDT with a full rescan first.
        EngineeringBuildProgressDialog.show(
                owner,
                List.copyOf(goals),
                shipCatalog,
                initialShipId,
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
        String slotKey = request.slotKey() != null ? request.slotKey().trim() : "";
        EngineeringGoal existing = request.existingGoal();
        if (existing == null) {
            existing = findReusableLoadoutGoal(request);
        }
        if (existing != null) {
            int prefillQty = request.existingGoal() != null
                    ? request.quantity()
                    : Math.max(1, existing.getQuantity()) + 1;
            EngineeringGoalDialog.EditResult edit = EngineeringGoalDialog.showForEdit(
                    owner,
                    database,
                    passThroughEnabledSupplier,
                    existing,
                    shipCatalog,
                    new EngineeringGoalDialog.AddPrefill(
                            request.moduleType(),
                            request.blueprintName(),
                            request.moduleType(),
                            request.experimentalName(),
                            prefillQty,
                            request.preferredTargetGrade() > 0
                                    ? request.preferredTargetGrade()
                                    : existing.getTargetGrade()));
            if (edit.deleted()) {
                int idx = indexOfGoalInstance(existing);
                if (idx >= 0) {
                    goals.remove(idx);
                }
                consolidateIdenticalGoals();
                rebuildShipFilterCombo();
                refreshGoalProgressFromJournal();
                fireSessionChanged();
                refreshUi();
                return null;
            }
            EngineeringGoal updated = edit.goal();
            if (updated != null) {
                if (updated.getQuantity() > 1) {
                    updated = updated.withTargetSlot("");
                } else if (!slotKey.isBlank()) {
                    updated = updated.withTargetSlot(slotKey);
                } else if (existing.hasTargetSlot()) {
                    updated = updated.withTargetSlot(existing.getTargetSlot());
                }
                int idx = indexOfGoalInstance(existing);
                if (idx >= 0) {
                    goals.set(idx, updated);
                } else {
                    goals.add(updated);
                }
                shipCatalog.rememberGoal(updated);
                consolidateIdenticalGoals();
                rebuildShipFilterCombo();
                refreshGoalProgressFromJournal();
                fireSessionChanged();
                refreshUi();
            }
            return updated;
        }
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
                        request.quantity(),
                        request.preferredTargetGrade()));
        if (goal != null) {
            if (!slotKey.isBlank() && goal.getQuantity() <= 1) {
                goal = goal.withTargetSlot(slotKey);
            } else if (goal.getQuantity() > 1) {
                goal = goal.withTargetSlot("");
            }
            goals.add(goal);
            shipCatalog.rememberGoal(goal);
            consolidateIdenticalGoals();
            rebuildShipFilterCombo();
            refreshGoalProgressFromJournal();
            fireSessionChanged();
            refreshUi();
        }
        return goal;
    }

    /**
     * When adding from Loadout without an existing row match, reuse a goal that already
     * plans the same module/blueprint/experimental/target on that ship (bump quantity).
     */
    private EngineeringGoal findReusableLoadoutGoal(EngineeringBuildProgressDialog.AddGoalRequest request) {
        if (request == null || request.ship() == null || !request.ship().isKnown()) {
            return null;
        }
        int targetGrade = request.preferredTargetGrade() > 0 ? request.preferredTargetGrade() : 1;
        String experimentalId = resolveExperimentalIdForRequest(
                request.moduleType(), request.blueprintName(), request.experimentalName());
        return EngineeringGoalMerger.findMatching(
                goals,
                request.ship().getShipId(),
                request.moduleType(),
                request.blueprintName(),
                experimentalId,
                targetGrade);
    }

    private String resolveExperimentalIdForRequest(String moduleType, String blueprintName, String experimentalName) {
        if (experimentalName == null || experimentalName.isBlank()
                || "(none)".equalsIgnoreCase(experimentalName.trim())) {
            return "";
        }
        String want = experimentalName.trim();
        for (BlueprintGrade exp : database.experimentalsFor(moduleType, blueprintName)) {
            if (exp.getName().equalsIgnoreCase(want)) {
                return exp.getId();
            }
        }
        String wantNorm = EngineeringJournalBlueprintResolver.normalizeToken(want);
        for (BlueprintGrade exp : database.experimentalsFor(moduleType, blueprintName)) {
            String nameNorm = EngineeringJournalBlueprintResolver.normalizeToken(exp.getName());
            String idNorm = EngineeringJournalBlueprintResolver.normalizeToken(exp.getId());
            if (nameNorm.equals(wantNorm) || idNorm.equals(wantNorm)
                    || nameNorm.contains(wantNorm) || wantNorm.contains(nameNorm)) {
                return exp.getId();
            }
        }
        return "";
    }

    /** Collapse duplicate plan identities into quantity; no-op when already unique. */
    private boolean consolidateIdenticalGoals() {
        return EngineeringGoalMerger.mergeInPlace(goals);
    }

    private int indexOfGoalInstance(EngineeringGoal existing) {
        if (existing == null) {
            return -1;
        }
        for (int i = 0; i < goals.size(); i++) {
            if (goals.get(i) == existing) {
                return i;
            }
        }
        // Progress replay may have replaced the instance; match by ship + module + blueprint (+ slot).
        for (int i = 0; i < goals.size(); i++) {
            EngineeringGoal g = goals.get(i);
            if (g == null) {
                continue;
            }
            if (existing.hasShip() && g.hasShip() && existing.getShipId() != g.getShipId()) {
                continue;
            }
            if (!EngineeringJournalBlueprintResolver.sameModuleType(existing.getModuleType(), g.getModuleType())) {
                continue;
            }
            if (!EngineeringJournalBlueprintResolver.normalizeToken(existing.getBlueprintName())
                    .equals(EngineeringJournalBlueprintResolver.normalizeToken(g.getBlueprintName()))) {
                continue;
            }
            if (existing.hasTargetSlot() || g.hasTargetSlot()) {
                if (!existing.getTargetSlot().equalsIgnoreCase(g.getTargetSlot())) {
                    continue;
                }
            }
            return i;
        }
        return -1;
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
        if (type == EliteEventType.ENGINEER_PROGRESS) {
            if (reputationTracker.applyEvent(event)) {
                scheduleRefresh();
            }
        }
        if (type == EliteEventType.ENGINEER_CRAFT && event instanceof EngineerCraftEvent craft) {
            long shipId = -1L;
            LoadoutEvent latest = EliteOverlayTabbedPane.getLatestLoadout();
            if (latest != null && latest.getShipId() >= 0) {
                shipId = latest.getShipId();
            }
            String clientKey = EliteDangerousOverlay.clientKey;
            boolean loadoutPatched = false;
            if (clientKey != null && !clientKey.isBlank() && shipId >= 0) {
                loadoutPatched = EngineeringCraftStore.rememberCraft(clientKey, craft, shipId);
            }
            // Live Loadout is patched in EliteOverlayTabbedPane.handleLogEvent; treat grade/experimental
            // crafts as a UI refresh even when the stored snapshot was already current.
            if (EngineeringLoadoutExperimentalPatch.shouldPatchLoadout(craft)) {
                loadoutPatched = true;
            }
            boolean goalsChanged = EngineeringGoalProgress.applyCraft(goals, craft, database, shipId);
            // Elite often skips a fresh Loadout after craft; sync goals from the patched snapshot too.
            LoadoutEvent patchedLoadout = EliteOverlayTabbedPane.getLatestLoadout();
            if (patchedLoadout != null
                    && EngineeringGoalProgress.applyLoadout(goals, patchedLoadout, database)) {
                goalsChanged = true;
            }
            if (loadoutPatched || goalsChanged) {
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
            if (g.hasTargetSlot()) {
                p.setTargetSlot(g.getTargetSlot());
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
        data.setHideMatsFromOtherShips(Boolean.valueOf(hideMatsFromOtherShips));
        state.setEngineering(data);
    }

    public void applySessionState(EdoSessionState state) {
        goals.clear();
        materialsGoals.clear();
        shipCatalog.clear();
        goalsShipFilterId = null;
        hideMatsFromOtherShips = true;
        if (state != null && state.getEngineering() != null) {
            EngineeringSessionData eng = state.getEngineering();
            for (ShipPersisted sp : eng.knownShipsOrEmpty()) {
                if (sp != null && sp.getShipId() >= 0) {
                    shipCatalog.remember(new EngineeringShipRef(
                            sp.getShipId(), sp.getShipType(), sp.getShipName(), sp.getShipIdent()));
                }
            }
            goalsShipFilterId = eng.getGoalsShipFilterId();
            hideMatsFromOtherShips = eng.hideMatsFromOtherShipsOrDefault();
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
                        p.includeInPlanningOrDefault(),
                        p.getTargetSlot()));
                if (!goals.isEmpty()) {
                    shipCatalog.rememberGoal(goals.get(goals.size() - 1));
                }
            }
            consolidateIdenticalGoals();
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
        if (hideMatsFromOtherShipsCheckBox != null) {
            hideMatsFromOtherShipsCheckBox.setSelected(hideMatsFromOtherShips);
        }
        syncHideMatsCheckboxEnabled();
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
        syncHideMatsCheckboxEnabled();
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
        syncHideMatsCheckboxEnabled();
        fireSessionChanged();
        refreshUi();
    }

    private void syncHideMatsCheckboxEnabled() {
        if (hideMatsFromOtherShipsCheckBox == null) {
            return;
        }
        // Always clickable so the control stays visible; planning only scopes when a ship is selected.
        hideMatsFromOtherShipsCheckBox.setEnabled(true);
        hideMatsFromOtherShipsCheckBox.setToolTipText(goalsShipFilterId != null
                ? "Materials Required and Trade Suggestions show this ship's goals only; "
                        + "other ships' priorities still reserve materials"
                : "Select a ship above to scope Materials Required and Trade Suggestions to that ship");
    }

    private void styleShipFilterCombo(JComboBox<ShipFilterItem> combo, Font base) {
        OverlayComboBoxStyle.apply(combo, base);
        combo.setMaximumRowCount(24);
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

    /**
     * Master toggle for the Goals enabled header: applies only to rows currently shown
     * (respects the ship filter). If every visible goal is enabled, disable them; otherwise
     * enable all visible goals.
     */
    private void toggleAllVisibleGoalsEnabled() {
        List<GoalUiRow> visible = goalsForUi();
        if (visible.isEmpty()) {
            return;
        }
        boolean allEnabled = true;
        for (GoalUiRow row : visible) {
            if (!row.isEnabled()) {
                allEnabled = false;
                break;
            }
        }
        boolean next = !allEnabled;
        boolean changed = false;
        for (GoalUiRow row : visible) {
            if (row.isEnabled() == next) {
                continue;
            }
            if (row.isMaterials()) {
                MaterialsGoal goal = row.materials();
                int fullIdx = materialsGoals.indexOf(goal);
                if (fullIdx >= 0) {
                    materialsGoals.set(fullIdx, goal.withEnabled(next));
                    changed = true;
                }
            } else {
                EngineeringGoal goal = row.blueprint();
                int fullIdx = goals.indexOf(goal);
                if (fullIdx >= 0) {
                    goals.set(fullIdx, goal.withEnabled(next));
                    changed = true;
                }
            }
        }
        if (changed) {
            fireSessionChanged();
            scheduleRefresh();
        }
    }

    private boolean allVisibleGoalsEnabled() {
        List<GoalUiRow> visible = goalsForUi();
        if (visible.isEmpty()) {
            return true;
        }
        for (GoalUiRow row : visible) {
            if (!row.isEnabled()) {
                return false;
            }
        }
        return true;
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
            consolidateIdenticalGoals();
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
        EngineeringShipRef defaultShip = resolveAddGoalDefaultShip(equipped);
        MaterialsGoal goal = EngineeringMaterialsGoalDialog.showForAdd(
                owner,
                database,
                passThroughEnabledSupplier,
                shipCatalog,
                defaultShip,
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
     * Default ship for Add Goal / Add Materials Goal / Loadout:
     * when the Engineering tab filter is a specific ship, use that. When the filter is All,
     * prefer the last manually chosen Add Goal ship while still on the same equipped hull;
     * when the equipped ship changes (or first time), follow the loadout.
     */
    private EngineeringShipRef resolveAddGoalDefaultShip(EngineeringShipRef equipped) {
        if (goalsShipFilterId != null) {
            EngineeringShipRef filtered = shipCatalog.get(goalsShipFilterId.longValue());
            if (filtered != null && filtered.isKnown()) {
                return filtered;
            }
        }
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
        EngineeringGoalDialog.EditResult edit = EngineeringGoalDialog.showForEdit(
                owner, database, passThroughEnabledSupplier, existing, shipCatalog);
        if (edit.deleted()) {
            int fullIdx = goals.indexOf(existing);
            if (fullIdx >= 0) {
                goals.remove(fullIdx);
            }
            rebuildShipFilterCombo();
            refreshGoalProgressFromJournal();
            fireSessionChanged();
            refreshUi();
            return;
        }
        EngineeringGoal updated = edit.goal();
        if (updated != null && !updated.equals(existing)) {
            int fullIdx = goals.indexOf(existing);
            if (fullIdx >= 0) {
                goals.set(fullIdx, updated);
            }
            if (updated.getQuantity() > 1) {
                updated = updated.withTargetSlot("");
                if (fullIdx >= 0) {
                    goals.set(fullIdx, updated);
                }
            }
            shipCatalog.rememberGoal(updated);
            consolidateIdenticalGoals();
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
        // Always plan against every included goal so priorities / reservations stay global.
        List<EngineeringGoal> planningGoals = activeGoalsForPlanning();
        List<MaterialsGoal> planningMats = activeMaterialsGoalsForPlanning();
        // Display + trade suggestions may be scoped to one ship without dropping other ships
        // from the priority/reservation plan.
        List<EngineeringGoal> displayGoals = displayScopedGoals(planningGoals);
        List<MaterialsGoal> displayMats = displayScopedMaterialsGoals(planningMats);
        boolean shipScopedDisplay = planningShipScopeId() != null;
        Map<String, Integer> shortfalls = planner.shortfalls(displayGoals, displayMats, inv);
        EngineeringPlanner.PriorityPlanResult plan = shipScopedDisplay
                ? planner.planByPriority(planningGoals, planningMats, inv, tradePlanner,
                        displayGoals, displayMats)
                : planner.planByPriority(planningGoals, planningMats, inv, tradePlanner);
        List<TradeSuggestion> trades = new ArrayList<>(plan.trades());
        Map<String, Integer> invAfterTrades = plan.inventoryAfterTrades();
        List<ShoppingListRow> shopping =
                planner.buildShoppingList(displayGoals, displayMats, inv, invAfterTrades);

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
                planner.shortfalls(displayGoals, displayMats, invAfterTrades);
        // Prefer remaining Short-goal gaps so Trade Suggestions matches Goals status; fall back
        // to aggregate post-trade shortfalls for materials that only become short after pays.
        Map<String, Integer> uncoveredShortfalls =
                mergeShortfallMaps(plan.shortfallsRemainingAfterPlan(), shortfallsAfterTrades);
        updateTradeTable(trades, shortfalls, uncoveredShortfalls);
        goalsModel.fireTableDataChanged();

        boolean hasVisibleGoals = !visibleGoals.isEmpty();
        boolean hasGoals = !goals.isEmpty() || !materialsGoals.isEmpty();
        boolean hasActiveGoals = !displayGoals.isEmpty() || !displayMats.isEmpty();
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
        if (!materialsSectionVisible) {
            // Trade content may have grown/shrunk; keep the Show button hugging it.
            SwingUtilities.invokeLater(this::pinCollapsedLowerSplitDivider);
        }

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

    /**
     * All included goals for priority planning and material reservations (every ship).
     */
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

    /**
     * Goals shown in Materials Required / Trade Suggestions. When Hide other ship Mats is on
     * with a ship selected, only that ship's goals appear — planning still uses all ships.
     */
    private List<EngineeringGoal> displayScopedGoals(List<EngineeringGoal> planningGoals) {
        Long shipScope = planningShipScopeId();
        if (shipScope == null) {
            return planningGoals;
        }
        List<EngineeringGoal> out = new ArrayList<>();
        for (EngineeringGoal g : planningGoals) {
            if (g != null && g.hasShip() && g.getShipId() == shipScope.longValue()) {
                out.add(g);
            }
        }
        return out;
    }

    private List<MaterialsGoal> displayScopedMaterialsGoals(List<MaterialsGoal> planningMats) {
        Long shipScope = planningShipScopeId();
        if (shipScope == null) {
            return planningMats;
        }
        List<MaterialsGoal> out = new ArrayList<>();
        for (MaterialsGoal g : planningMats) {
            if (isMaterialsGoalVisibleForShipFilter(g, shipScope)) {
                out.add(g);
            }
        }
        return out;
    }

    /** Ship id to scope materials/trades display, or null for all ships. */
    private Long planningShipScopeId() {
        if (!hideMatsFromOtherShips || goalsShipFilterId == null) {
            return null;
        }
        return goalsShipFilterId;
    }

    /** Display name when trades are scoped to one ship; null when trading for all ships. */
    private String singleShipTradeScopeLabel() {
        if (planningShipScopeId() == null || shipFilterCombo == null) {
            return null;
        }
        Object selected = shipFilterCombo.getSelectedItem();
        if (selected instanceof ShipFilterItem item
                && item.shipId() != null
                && item.label() != null
                && !item.label().isBlank()) {
            return item.label();
        }
        return null;
    }

    private void updateTradeTable(List<TradeSuggestion> trades,
                                  Map<String, Integer> shortfalls,
                                  Map<String, Integer> uncoveredShortfalls) {
        List<TradeTableRow> rows = new ArrayList<>();
        Map<String, List<TradeTargetGroup>> grouped =
                MaterialTradePlanner.groupByTraderTypeAndTarget(trades, shortfalls);
        Map<String, List<TradeTableRow>> untradeable =
                untradeableShortfallRows(grouped, shortfalls, uncoveredShortfalls);
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
                boolean uncovered = shortfallRemaining(uncoveredShortfalls, group.getToKey()) > 0;
                List<TradeSuggestion> options = group.getOptions();
                for (int i = 0; i < options.size(); i++) {
                    TradeSuggestion option = options.get(i);
                    boolean firstOption = i == 0;
                    boolean lastOption = i == options.size() - 1;
                    // Need column: prefer remaining Short amount when still uncovered after the plan.
                    Integer needDisplay = null;
                    if (firstOption) {
                        int remaining = shortfallRemaining(uncoveredShortfalls, group.getToKey());
                        needDisplay = Integer.valueOf(remaining > 0 ? remaining : group.getShortfall());
                    }
                    rows.add(TradeTableRow.data(
                            firstOption ? group.getToName() : "",
                            needDisplay,
                            formatTradeGive(option),
                            option.getToCount(),
                            uncovered,
                            lastOption,
                            option));
                }
            }
            List<TradeTableRow> extras = untradeable.remove(entry.getKey());
            if (extras != null) {
                rows.addAll(extras);
            }
        }
        // Short materials with no trade options at all get their own (red) rows, so the user can
        // see what "Short" refers to even when nothing is tradeable toward it.
        for (Map.Entry<String, List<TradeTableRow>> entry : untradeable.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            if (!rows.isEmpty()) {
                rows.add(TradeTableRow.gap());
            }
            rows.add(TradeTableRow.section(traderTypeSectionTitle(entry.getKey())));
            rows.addAll(entry.getValue());
        }
        // Sort red groups to the top within each section already happens in groupByTarget using
        // option-sum coverage; re-sort here using post-trade shortfall so priority-planned rows match.
        tradeModel.setRows(reorderTradeRowsUncoveredFirst(rows));
    }

    /** Union of shortfall maps; when both have a key, keep the larger remaining Need. */
    private static Map<String, Integer> mergeShortfallMaps(Map<String, Integer> primary,
                                                           Map<String, Integer> secondary) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (primary != null) {
            for (Map.Entry<String, Integer> e : primary.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue() <= 0) {
                    continue;
                }
                out.put(EngineeringMaterialKeys.canonicalKey(e.getKey()), e.getValue());
            }
        }
        if (secondary != null) {
            for (Map.Entry<String, Integer> e : secondary.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue() <= 0) {
                    continue;
                }
                String key = EngineeringMaterialKeys.canonicalKey(e.getKey());
                out.merge(key, e.getValue(), Math::max);
            }
        }
        return out;
    }

    /**
     * Rows for materials that stay short after the priority plan and have no trade suggestion
     * of their own — rendered red with no Trade action, keyed by trader-type section.
     */
    private Map<String, List<TradeTableRow>> untradeableShortfallRows(
            Map<String, List<TradeTargetGroup>> grouped,
            Map<String, Integer> shortfalls,
            Map<String, Integer> uncoveredShortfalls) {
        Map<String, List<TradeTableRow>> out = new LinkedHashMap<>();
        if (uncoveredShortfalls == null || uncoveredShortfalls.isEmpty()) {
            return out;
        }
        Set<String> suggested = new HashSet<>();
        for (List<TradeTargetGroup> targets : grouped.values()) {
            for (TradeTargetGroup group : targets) {
                suggested.add(EngineeringMaterialKeys.canonicalKey(group.getToKey()));
            }
        }
        // Prefer uncovered (plan remaining / after trades); fall back to initial Need for display.
        for (Map.Entry<String, Integer> e : uncoveredShortfalls.entrySet()) {
            String key = e.getKey();
            int need = e.getValue() != null ? e.getValue() : 0;
            if (key == null || need <= 0
                    || suggested.contains(EngineeringMaterialKeys.canonicalKey(key))) {
                continue;
            }
            if (shortfalls != null) {
                int initial = shortfallRemaining(shortfalls, key);
                if (initial > need) {
                    need = initial;
                }
            }
            String traderType = database.material(key)
                    .map(m -> m.getType())
                    .orElse("");
            out.computeIfAbsent(traderType, k -> new ArrayList<>())
                    .add(TradeTableRow.data(
                            database.materialDisplayName(key),
                            Integer.valueOf(need),
                            "No trades available",
                            0,
                            true,
                            true,
                            null));
        }
        for (List<TradeTableRow> list : out.values()) {
            list.sort(Comparator.comparing(TradeTableRow::materialName, String.CASE_INSENSITIVE_ORDER));
        }
        return out;
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
            String shipScopeLabel = singleShipTradeScopeLabel();
            result = tradeAll
                    ? MaterialTradeConfirmDialog.executeAll(
                            owner, suggestion.getTraderType(), toRun, shipScopeLabel, action)
                    : MaterialTradeConfirmDialog.execute(owner, suggestion, shipScopeLabel, action);
        } finally {
            tradeAutomationRunning = false;
        }
        if (result == null || result.ok()) {
            // Success needs no banner; the tables refreshing is the feedback.
            setTradeStatus(" ", false);
        } else {
            setTradeStatus(result.message(), false);
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
        if (goalsTable != null) {
            goalsTable.setRowHeight(Math.max(36, fontSize * 2 + 12));
        }
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
        // Materials reserves have no craft step — stocked means Ready (not Complete; that implied
        // the goal auto-finished and confused people who had just added an acquisition target).
        return switch (readiness) {
            case READY -> STATUS_READY;
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

    private final class GoalEnabledHeaderRenderer extends DefaultTableCellRenderer {
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
                boolean allOn = allVisibleGoalsEnabled();
                label.setIcon(allOn
                        ? OverlayCheckBoxStyle.selectedIcon()
                        : OverlayCheckBoxStyle.unselectedIcon());
                label.setText(null);
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setToolTipText(allOn
                        ? "Disable all visible goals (materials / trades)"
                        : "Enable all visible goals (materials / trades)");
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

    private final class ExperimentalEffectCellRenderer extends EdoTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
            if (!(c instanceof JLabel label)) {
                return c;
            }
            String text = value != null ? value.toString() : "";
            String tip = null;
            int modelRow = table.convertRowIndexToModel(row);
            GoalUiRow goalRow = goalRowAtUi(modelRow);
            if (goalRow != null && goalRow.blueprint() != null && !"—".equals(text) && !text.isBlank()) {
                EngineeringGoal g = goalRow.blueprint();
                tip = database.experimentalEffectTooltip(
                        g.getModuleType(), g.getBlueprintName(), text);
            }
            if (tip == null && !text.isBlank() && !"—".equals(text)) {
                tip = text;
            }
            label.setToolTipText(tip);
            return c;
        }
    }

    private int bestEngineerRankForGoal(EngineeringGoal goal) {
        if (goal == null || database == null || reputationTracker == null) {
            return 0;
        }
        return database.findById(goal.getBlueprintId())
                .map(bp -> reputationTracker.bestRank(bp.getEngineers()))
                .orElse(0);
    }

    private final class GoalStatusCellRenderer extends JPanel implements javax.swing.table.TableCellRenderer {
        private static final long serialVersionUID = 1L;
        private final JLabel label = new JLabel();
        /** {@code < 0} = text/icon mode; otherwise 0..1 craft fill. */
        private double barFill = -1.0;
        private Color barColor = EdoUi.User.MAIN_TEXT;

        GoalStatusCellRenderer() {
            setLayout(new BorderLayout());
            setOpaque(false);
            label.setOpaque(false);
            label.setBorder(new EmptyBorder(2, 6, 2, 6));
            label.setHorizontalTextPosition(SwingConstants.RIGHT);
            label.setVerticalTextPosition(SwingConstants.CENTER);
            label.setIconTextGap(6);
            add(label, BorderLayout.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            barFill = -1.0;
            String text = value != null ? value.toString() : "";
            label.setText(text);
            label.setIcon(null);
            label.setForeground(EdoUi.User.MAIN_TEXT);
            setToolTipText(null);

            int modelRow = table != null ? table.convertRowIndexToModel(row) : row;
            GoalUiRow goalRow = goalRowAtUi(modelRow);
            GoalReadiness readiness = modelRow >= 0 && modelRow < goalReadiness.size()
                    ? goalReadiness.get(modelRow)
                    : null;

            if (goalRow != null && goalRow.blueprint() != null
                    && !text.isBlank()
                    && !STATUS_HIDDEN.equals(text)
                    && !STATUS_COMPLETE.equals(text)
                    && !goalRow.blueprint().isComplete()) {
                EngineeringGoal goal = goalRow.blueprint();
                LoadoutEvent loadout = EliteOverlayTabbedPane.getLatestLoadout();
                if (EngineeringGoalProgress.hasDisplayCraftProgress(goal, loadout, database)) {
                    int rank = bestEngineerRankForGoal(goal);
                    barFill = EngineeringGoalProgress.displayCompletionFraction(
                            goal, loadout, database, rank);
                    barColor = readinessColor(readiness, text);
                    label.setText("");
                    label.setIcon(null);
                    String progress = EngineeringGradeProgress.progressLabel(goal, rank);
                    String tip = text;
                    if (goal.getQuantity() > 1) {
                        tip = text + " · " + goal.getCompletedUnits() + "/" + goal.getQuantity()
                                + " units";
                        // Shared fromGrade is the worst incomplete unit; tip the bar % instead.
                        tip += " · " + Math.round(barFill * 100.0) + "%";
                    } else if (progress != null && !progress.isBlank()) {
                        tip = text + " · " + progress;
                    }
                    setToolTipText(tip);
                    return this;
                }
            }

            if (table != null && table.getFont() != null) {
                label.setFont(table.getFont());
            } else {
                label.setFont(OverlayPreferences.getUiFont());
            }

            if (text.isBlank()) {
                setToolTipText(null);
            } else if (STATUS_TRADES.equals(text)) {
                setToolTipText("Trades available to complete");
            } else {
                setToolTipText(text);
            }

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
            } else if (readiness != null) {
                label.setForeground(readinessColor(readiness, text));
                label.setIcon(switch (readiness) {
                    case READY -> STATUS_ICON_OK;
                    case READY_WITH_TRADES -> STATUS_ICON_TRADES;
                    case STILL_SHORT -> STATUS_ICON_SHORT;
                });
            } else if (GOAL_STATUS_WIDTH_CAP_TEXT.equals(text)) {
                label.setIcon(STATUS_ICON_TRADES);
                label.setForeground(Color.YELLOW);
            }
            return this;
        }

        private Color readinessColor(GoalReadiness readiness, String text) {
            if (readiness != null) {
                return switch (readiness) {
                    case READY -> EdoUi.User.SUCCESS;
                    case READY_WITH_TRADES -> Color.YELLOW;
                    case STILL_SHORT -> EdoUi.User.ERROR;
                };
            }
            if (STATUS_READY.equals(text) || STATUS_COMPLETE.equals(text)) {
                return EdoUi.User.SUCCESS;
            }
            if (STATUS_TRADES.equals(text)) {
                return Color.YELLOW;
            }
            if (STATUS_SHORT.equals(text)) {
                return EdoUi.User.ERROR;
            }
            return EdoUi.User.MAIN_TEXT;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (barFill < 0.0 || !(g instanceof Graphics2D g2)) {
                return;
            }
            int padX = 6;
            int padY = 8;
            int w = Math.max(0, getWidth() - padX * 2);
            int h = Math.max(8, getHeight() - padY * 2);
            int x = padX;
            int y = Math.max(0, (getHeight() - h) / 2);
            Graphics2D gPaint = (Graphics2D) g2.create();
            try {
                gPaint.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                gPaint.setColor(EdoUi.Internal.MAIN_TEXT_ALPHA_40);
                gPaint.fillRoundRect(x, y, w, h, 6, 6);
                int fillW = (int) Math.round(w * Math.max(0.0, Math.min(1.0, barFill)));
                if (fillW > 0) {
                    gPaint.setColor(barColor);
                    gPaint.fillRoundRect(x, y, Math.max(fillW, Math.min(4, w)), h, 6, 6);
                }
            } finally {
                gPaint.dispose();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            if (barFill >= 0.0) {
                return new Dimension(80, 24);
            }
            Dimension labelPref = label.getPreferredSize();
            return new Dimension(Math.max(48, labelPref.width + 12), Math.max(24, labelPref.height));
        }
    }

    private final class BlueprintNameCellRenderer implements javax.swing.table.TableCellRenderer {
        private final JPanel panel = new JPanel();
        private final JLabel topLine = new JLabel();
        private final JLabel bottomLine = new JLabel();

        BlueprintNameCellRenderer() {
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setOpaque(false);
            panel.setBorder(new EmptyBorder(1, 6, 1, 6));
            topLine.setOpaque(false);
            bottomLine.setOpaque(false);
            topLine.setAlignmentX(Component.LEFT_ALIGNMENT);
            bottomLine.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(topLine);
            panel.add(bottomLine);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Font base = OverlayPreferences.getUiFont();
            int size = OverlayPreferences.getUiFontSize();
            topLine.setFont(base.deriveFont(Font.BOLD, size));
            bottomLine.setFont(base.deriveFont(Font.PLAIN, size));
            topLine.setForeground(EdoUi.User.MAIN_TEXT);
            bottomLine.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);

            int modelRow = table.convertRowIndexToModel(row);
            GoalUiRow goalRow = goalRowAtUi(modelRow);
            String top = "";
            String bottom = "";
            String tip = null;
            if (goalRow != null && goalRow.isMaterials()) {
                MaterialsGoal g = goalRow.materials();
                top = goalPrimaryLine(
                        g.hasShip() ? g.getShipId() : -1L, g.getShipLabel(), g.getLabel(), 1);
                bottom = "";
                tip = g.materialsTooltip(database);
            } else if (goalRow != null && goalRow.blueprint() != null) {
                EngineeringGoal g = goalRow.blueprint();
                top = goalPrimaryLine(g.getShipId(), g.getShipLabel(), g.getModuleType(), g.getQuantity());
                bottom = g.getBlueprintName() != null ? g.getBlueprintName().trim() : "";
                String effects = database.blueprintEffectTooltip(
                        g.getModuleType(), g.getBlueprintName(), g.getTargetGrade());
                tip = effects != null && !effects.isBlank()
                        ? effects
                        : top + (bottom.isBlank() ? "" : " — " + bottom);
            } else if (value != null) {
                top = value.toString();
                tip = top;
            }
            topLine.setText(top);
            bottomLine.setText(bottom);
            bottomLine.setVisible(!bottom.isBlank());
            panel.setToolTipText(tip != null && !tip.isBlank() ? tip : null);
            return panel;
        }
    }

    /**
     * Effect-description tooltip for goals table blueprint / experimental columns.
     */
    private String engineeringEffectTipAt(int viewRow, int modelCol) {
        int modelRow = convertGoalsViewRowToModel(viewRow);
        GoalUiRow goalRow = goalRowAtUi(modelRow);
        if (goalRow == null || goalRow.blueprint() == null || database == null) {
            return null;
        }
        EngineeringGoal g = goalRow.blueprint();
        if (modelCol == COL_GOAL_BLUEPRINT) {
            return database.blueprintEffectTooltip(
                    g.getModuleType(), g.getBlueprintName(), g.getTargetGrade());
        }
        if (modelCol == COL_GOAL_EXP) {
            if (g.getExperimentalId().isBlank()) {
                return null;
            }
            return database.findById(g.getExperimentalId())
                    .map(EngineeringDatabase::formatEffectTooltip)
                    .orElseGet(() -> database.experimentalEffectTooltip(
                            g.getModuleType(),
                            g.getBlueprintName(),
                            g.getExperimentalId()));
        }
        return null;
    }

    private int convertGoalsViewRowToModel(int viewRow) {
        if (viewRow < 0) {
            return -1;
        }
        try {
            return goalsTable.convertRowIndexToModel(viewRow);
        } catch (RuntimeException ex) {
            return viewRow;
        }
    }

    /**
     * Top line: module/label, and when the ship filter is All, {@code "Thrusters - Anaconda"}.
     */
    private String goalPrimaryLine(long shipId, String shipLabel, String moduleOrLabel, int quantity) {
        String body = moduleOrLabel != null ? moduleOrLabel.trim() : "";
        if (goalsShipFilterId == null) {
            String ship = shortShipNameForGoal(shipId, shipLabel);
            if (!ship.isBlank()) {
                body = body.isBlank() ? ship : body + " - " + ship;
            }
        }
        if (quantity > 1 && !body.isBlank()) {
            return quantity + "x " + body;
        }
        return body;
    }

    /** Hull type only (e.g. "Anaconda") — not custom name / callsign. */
    private String shortShipNameForGoal(long shipId, String shipLabel) {
        if (shipId >= 0) {
            EngineeringShipRef ref = shipCatalog.get(shipId);
            if (ref != null && !ref.getShipType().isBlank()) {
                return new EngineeringShipRef(ref.getShipId(), ref.getShipType(), "", "").baseDisplayLabel();
            }
        }
        if (shipLabel != null && !shipLabel.isBlank()) {
            String s = shipLabel.trim();
            int sep = s.indexOf('·');
            if (sep > 0) {
                s = s.substring(0, sep).trim();
            }
            return s;
        }
        return "";
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

    private String blueprintDisplayName(EngineeringGoal goal) {
        if (goal == null) {
            return "";
        }
        String top = goalPrimaryLine(goal.getShipId(), goal.getShipLabel(), goal.getModuleType(), goal.getQuantity());
        String bottom = goal.getBlueprintName() != null ? goal.getBlueprintName().trim() : "";
        if (bottom.isBlank()) {
            return top;
        }
        return top.isBlank() ? bottom : top + " / " + bottom;
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
                case COL_GOAL_TARGET -> "Tgt";
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
         * A data row runs one trade when that shortfall is coverable. A trader-type section row
         * runs every displayed coverable trade below it, in display order, up to the next
         * section/gap. Red (uncovered) shortfalls are skipped — there is not enough inventory
         * to complete those trades.
         */
        List<TradeSuggestion> tradesForActionAt(int rowIndex) {
            if (rowIndex < 0 || rowIndex >= rows.size()) {
                return List.of();
            }
            TradeTableRow row = rows.get(rowIndex);
            if (!row.section()) {
                if (row.shortfallUncovered() || row.suggestion() == null) {
                    return List.of();
                }
                return List.of(row.suggestion());
            }
            List<TradeSuggestion> trades = new ArrayList<>();
            for (int i = rowIndex + 1; i < rows.size(); i++) {
                TradeTableRow candidate = rows.get(i);
                if (candidate.section() || candidate.gapRow()) {
                    break;
                }
                if (candidate.shortfallUncovered() || candidate.suggestion() == null) {
                    continue;
                }
                trades.add(candidate.suggestion());
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
                case COL_TRADE_ACTION -> tradesForActionAt(rowIndex).isEmpty() ? "" : "Trade";
                case COL_TRADE_RECEIVE -> row.receive() > 0 ? Integer.valueOf(row.receive()) : null;
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
                    if (tradeModel.suggestionAt(modelRow) != null) {
                        applyTradeGiveNumberPadding(label, table);
                    } else {
                        // Info-only row (e.g. "No trades available") — plain text, no qty alignment.
                        label.setHorizontalAlignment(SwingConstants.LEFT);
                        label.setBorder(new EmptyBorder(2, TRADE_GIVE_CELL_LEFT_PAD, 2, 6));
                    }
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
