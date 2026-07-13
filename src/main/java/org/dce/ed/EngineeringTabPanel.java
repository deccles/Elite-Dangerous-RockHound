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
import java.util.function.BooleanSupplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
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
import org.dce.ed.engineering.InventoryConsolidationPlanner;
import org.dce.ed.engineering.MaterialTradePlanner;
import org.dce.ed.engineering.ShoppingListRow;
import org.dce.ed.engineering.TradeSuggestion;
import org.dce.ed.engineering.TradeTargetGroup;
import org.dce.ed.edsm.UtilTable;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.session.EngineeringSessionData;
import org.dce.ed.session.EngineeringSessionData.EngineeringGoalPersisted;
import org.dce.ed.ui.EdoMiningSplitPaneUi;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.HoverClickPoller;
import org.dce.ed.ui.OverlayScrollPaneSupport;
import org.dce.ed.ui.PassThroughScrollSupport;
import org.dce.ed.ui.OverlayCheckBoxStyle;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
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

    /** Fixed width for the receive-material column in trade suggestion rows. */
    private static final int TRADE_TARGET_COLUMN_WIDTH_PX = 148;

    private final BooleanSupplier passThroughEnabledSupplier;

    private final EngineeringDatabase database = EngineeringDatabase.getInstance();
    private final EngineeringInventoryTracker inventoryTracker = new EngineeringInventoryTracker();
    private final EngineeringPlanner planner = new EngineeringPlanner(database);
    private final MaterialTradePlanner tradePlanner = new MaterialTradePlanner(database);
    private final InventoryConsolidationPlanner consolidationPlanner = new InventoryConsolidationPlanner(database);

    private final List<EngineeringGoal> goals = new ArrayList<>();
    private final List<GoalReadiness> goalReadiness = new ArrayList<>();
    private final List<String> goalStatusText = new ArrayList<>();
    private Runnable sessionStateChangeCallback;

    private final JLabel goalsSummaryLabel = new JLabel(" ");
    private final JLabel tradeSummaryLabel = new JLabel(" ");
    private final JLabel materialsEmptyLabel = new JLabel();
    private final JLabel materialsCollectLabel = new JLabel(" ");
    private final JLabel tradeEmptyLabel = new JLabel();
    private final GoalsTableModel goalsModel = new GoalsTableModel();
    private final JTable goalsTable = createOverlayTable(goalsModel);
    private final ShoppingTableModel shoppingModel = new ShoppingTableModel();
    private final JTable shoppingTable = createOverlayTable(shoppingModel);
    private final JPanel tradeSectionsPanel = new JPanel();
    private JScrollPane goalsScroll;
    private TableRowSorter<ShoppingTableModel> shoppingSorter;
    private Font tradeSectionBaseFont;
    private int tradeSectionFontSize;
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
        this.tradeSectionBaseFont = base;
        this.tradeSectionFontSize = fontSize;

        styleMutedLabel(materialsEmptyLabel, base, fontSize);
        styleMutedLabel(materialsCollectLabel, base, fontSize);
        styleMutedLabel(tradeEmptyLabel, base, fontSize);
        styleSummaryLabel(goalsSummaryLabel, base, fontSize);
        styleSummaryLabel(tradeSummaryLabel, base, fontSize);

        configureTable(goalsTable, base, new GoalStatusCellRenderer());
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
        configureTable(shoppingTable, base, new ShoppingCellRenderer());

        goalsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        shoppingTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

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
            EdoMiningSplitPaneUi.applyDividerTheme(mainSplit);
            EdoMiningSplitPaneUi.applyDividerTheme(lowerSplit);
        });

        inventoryTracker.setChangeCallback(this::scheduleRefresh);
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
        JButton reduceBtn = new JButton("Reduce commons");
        OverlayOutlineButtonStyle.applyChip(reduceBtn, base, false);
        reduceBtn.addActionListener(e -> openAddInventoryGoalDialog());
        HoverClickPoller.register(reduceBtn, HOVER_CLICK_DELAY_MS, this::openAddInventoryGoalDialog, passThroughEnabledSupplier);
        JButton removeBtn = new JButton("Remove");
        OverlayOutlineButtonStyle.applyChip(removeBtn, base, false);
        removeBtn.addActionListener(e -> removeSelectedGoal());
        HoverClickPoller.register(removeBtn, HOVER_CLICK_DELAY_MS, this::removeSelectedGoal, passThroughEnabledSupplier);
        buttons.add(addBtn);
        buttons.add(reduceBtn);
        buttons.add(removeBtn);
        header.add(buttons, BorderLayout.EAST);
        p.add(header, BorderLayout.NORTH);

        goalsSummaryLabel.setBorder(new EmptyBorder(0, 0, 4, 0));
        JPanel northStack = new JPanel(new BorderLayout());
        northStack.setOpaque(false);
        northStack.add(header, BorderLayout.NORTH);
        northStack.add(goalsSummaryLabel, BorderLayout.SOUTH);
        p.add(northStack, BorderLayout.NORTH);

        p.add(goalsScroll = wrapScroll(goalsTable, 120), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildMaterialsPanel(Font base, int fontSize) {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setOpaque(false);
        p.add(sectionHeader("Materials", base, fontSize), BorderLayout.NORTH);
        JPanel content = new JPanel(new BorderLayout(4, 4));
        content.setOpaque(false);
        materialsCollectLabel.setBorder(new EmptyBorder(0, 4, 4, 4));
        materialsEmptyLabel.setBorder(new EmptyBorder(8, 4, 8, 4));
        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(materialsCollectLabel, BorderLayout.NORTH);
        north.add(materialsEmptyLabel, BorderLayout.SOUTH);
        content.add(north, BorderLayout.NORTH);
        shoppingScroll = wrapScroll(shoppingTable, 160);
        content.add(shoppingScroll, BorderLayout.CENTER);
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildTradePanel(Font base, int fontSize) {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setOpaque(false);
        tradeSummaryLabel.setBorder(new EmptyBorder(0, 0, 4, 0));
        JPanel northStack = new JPanel(new BorderLayout());
        northStack.setOpaque(false);
        northStack.add(sectionHeader("Trade suggestions", base, fontSize), BorderLayout.NORTH);
        northStack.add(tradeSummaryLabel, BorderLayout.SOUTH);
        p.add(northStack, BorderLayout.NORTH);
        JPanel content = new JPanel(new BorderLayout(4, 4));
        content.setOpaque(false);
        tradeEmptyLabel.setBorder(new EmptyBorder(8, 4, 8, 4));
        content.add(tradeEmptyLabel, BorderLayout.NORTH);
        tradeSectionsPanel.setLayout(new BoxLayout(tradeSectionsPanel, BoxLayout.Y_AXIS));
        tradeSectionsPanel.setOpaque(false);
        tradeScroll = wrapScroll(tradeSectionsPanel, 160);
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
    }

    private static void styleSummaryLabel(JLabel label, Font base, int fontSize) {
        label.setFont(base.deriveFont(Font.PLAIN, fontSize));
        label.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
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
        scroll.setFocusable(false);
        scroll.setRequestFocusEnabled(false);
        OverlayScrollPaneSupport.configureOverlayTableScroller(scroll);
        if (scroll.getViewport() != null) {
            scroll.getViewport().setFocusable(false);
            scroll.getViewport().setRequestFocusEnabled(false);
        }
        return scroll;
    }

    /** Clears LAF table-scroll chrome that can read as a white frame on transparent overlays. */
    private static JTable createOverlayTable(TableModel model) {
        return new JTable(model) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void configureEnclosingScrollPane() {
                super.configureEnclosingScrollPane();
                Container parent = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
                if (parent instanceof JScrollPane sp) {
                    sp.setBorder(BorderFactory.createEmptyBorder());
                    sp.setViewportBorder(BorderFactory.createEmptyBorder());
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
        revalidate();
        repaint();
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

    private void configureTable(JTable table, Font base, DefaultTableCellRenderer renderer) {
        table.setOpaque(false);
        table.setBackground(EdoUi.Internal.TRANSPARENT);
        table.setForeground(EdoUi.User.MAIN_TEXT);
        table.setBorder(null);
        table.setFocusable(false);
        table.setRequestFocusEnabled(false);
        table.putClientProperty("JTable.autoStartsEdit", Boolean.FALSE);
        table.setGridColor(EdoUi.Internal.mainTextAlpha(48));
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setRowHeight(Math.max(22, OverlayPreferences.getUiFontSize() + 10));
        table.setFont(base.deriveFont(Font.PLAIN, OverlayPreferences.getUiFontSize()));
        table.setFillsViewportHeight(false);
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
    }

    public void fillSessionState(EdoSessionState state) {
        if (state == null) {
            return;
        }
        EngineeringSessionData data = new EngineeringSessionData();
        List<EngineeringGoalPersisted> persisted = new ArrayList<>();
        for (EngineeringGoal g : goals) {
            EngineeringGoalPersisted p = new EngineeringGoalPersisted();
            p.setBlueprintId(g.getBlueprintId());
            p.setModuleType(g.getModuleType());
            p.setBlueprintName(g.getBlueprintName());
            p.setFromGrade(g.getFromGrade());
            p.setCraftsAtCurrentGrade(g.getCraftsAtCurrentGrade());
            p.setTargetGrade(g.getTargetGrade());
            p.setExperimentalId(g.getExperimentalId());
            p.setIncludeInPlanning(g.isIncludeInPlanning());
            persisted.add(p);
        }
        data.setGoals(persisted);
        state.setEngineering(data);
    }

    public void applySessionState(EdoSessionState state) {
        goals.clear();
        if (state != null && state.getEngineering() != null) {
            for (EngineeringGoalPersisted p : state.getEngineering().goalsOrEmpty()) {
                goals.add(new EngineeringGoal(
                        p.getBlueprintId(),
                        p.getModuleType(),
                        p.getBlueprintName(),
                        p.getFromGrade(),
                        p.getCraftsAtCurrentGrade(),
                        p.getTargetGrade(),
                        p.getExperimentalId() != null ? p.getExperimentalId() : "",
                        p.includeInPlanningOrDefault()));
            }
        }
        scheduleRefresh();
    }

    private void openAddGoalDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        EngineeringGoal goal = AddEngineeringGoalDialog.show(owner, database, passThroughEnabledSupplier);
        if (goal != null) {
            goals.add(goal);
            fireSessionChanged();
            scheduleRefresh();
        }
    }

    private void openAddInventoryGoalDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        EngineeringGoal goal = AddInventoryConsolidationGoalDialog.show(owner, passThroughEnabledSupplier);
        if (goal != null) {
            goals.add(goal);
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
        List<EngineeringGoal> activeBlueprintGoals = goals.stream()
                .filter(g -> g != null && !g.isInventoryConsolidation() && g.isIncludeInPlanning())
                .toList();
        Map<String, Integer> required = planner.requiredMaterials(activeBlueprintGoals);
        Map<String, Integer> shortfalls = planner.shortfalls(activeBlueprintGoals, inv);
        List<TradeSuggestion> engineeringTrades = new ArrayList<>(tradePlanner.suggest(shortfalls, inv, required));
        Map<String, Integer> invAfterTrades = tradePlanner.inventoryAfterTrades(inv, engineeringTrades);
        List<TradeSuggestion> consolidationTrades = new ArrayList<>();
        for (EngineeringGoal goal : goals) {
            if (goal != null && goal.isInventoryConsolidation() && goal.isIncludeInPlanning()) {
                List<TradeSuggestion> forGoal = consolidationPlanner.suggest(goal, invAfterTrades, required);
                consolidationTrades.addAll(forGoal);
                invAfterTrades = tradePlanner.inventoryAfterTrades(invAfterTrades, forGoal);
            }
        }
        List<TradeSuggestion> trades = new ArrayList<>(engineeringTrades);
        trades.addAll(consolidationTrades);

        Map<String, Integer> shortfallsAfterTrades = planner.shortfalls(activeBlueprintGoals, invAfterTrades);
        Map<String, Integer> tradeShortfalls = new LinkedHashMap<>(shortfalls);
        tradeShortfalls.putAll(consolidationPlanner.consolidationTargets(consolidationTrades));
        List<ShoppingListRow> shopping = planner.buildShoppingList(activeBlueprintGoals, inv, invAfterTrades);

        goalReadiness.clear();
        goalStatusText.clear();
        for (EngineeringGoal goal : goals) {
            if (!goal.isIncludeInPlanning()) {
                goalReadiness.add(GoalReadiness.READY);
                goalStatusText.add("Hidden");
                continue;
            }
            if (goal.isInventoryConsolidation()) {
                int excess = consolidationPlanner.excessCommonUnits(goal, inv, required);
                if (excess <= 0) {
                    goalReadiness.add(GoalReadiness.READY);
                    goalStatusText.add("No excess commons");
                } else if (!consolidationTrades.isEmpty()) {
                    goalReadiness.add(GoalReadiness.READY_WITH_TRADES);
                    goalStatusText.add("Frees ~" + estimateSlotsFreed(consolidationTrades) + " slots");
                } else {
                    goalReadiness.add(GoalReadiness.STILL_SHORT);
                    goalStatusText.add(excess + " excess, no trades");
                }
            } else {
                GoalReadiness readiness = planner.goalReadiness(goal, inv, invAfterTrades);
                goalReadiness.add(readiness);
                goalStatusText.add(formatStatusText(goal, readiness, invAfterTrades));
            }
        }

        shoppingModel.setRows(shopping);
        updateTradeSections(trades, tradeShortfalls);
        goalsModel.fireTableDataChanged();

        updateGoalsSummary(inv, invAfterTrades);
        updateMaterialsCollectHint(shortfallsAfterTrades);
        updateTradeSummary(trades, shortfalls, shortfallsAfterTrades);

        boolean hasBlueprintGoals = goals.stream()
                .anyMatch(g -> g != null && !g.isInventoryConsolidation());
        boolean hasActiveBlueprintGoals = !activeBlueprintGoals.isEmpty();
        boolean hasConsolidationGoals = goals.stream().anyMatch(EngineeringGoal::isInventoryConsolidation);
        boolean hasActiveConsolidationGoals = goals.stream()
                .anyMatch(g -> g != null && g.isInventoryConsolidation() && g.isIncludeInPlanning());
        boolean hasAnyGoals = hasBlueprintGoals || hasConsolidationGoals;
        boolean showShopping = hasActiveBlueprintGoals && !shopping.isEmpty();
        boolean showTrades = !trades.isEmpty();
        materialsEmptyLabel.setVisible(!showShopping);
        tradeEmptyLabel.setVisible(!showTrades);
        if (shoppingScroll != null) {
            shoppingScroll.setVisible(showShopping);
        }
        if (tradeScroll != null) {
            tradeScroll.setVisible(showTrades);
        }

        if (!hasAnyGoals) {
            materialsEmptyLabel.setText("<html><body style='color:#ffcc88'>Add a goal to see required materials.</body></html>");
            tradeEmptyLabel.setText("<html><body style='color:#ffcc88'>Trade suggestions appear when you have material shortfalls.</body></html>");
        } else if (!hasBlueprintGoals) {
            materialsEmptyLabel.setText("<html><body style='color:#ffcc88'>Inventory goal only — see trade suggestions below.</body></html>");
        } else if (shopping.isEmpty()) {
            materialsEmptyLabel.setText("<html><body style='color:#ffcc88'>No materials computed for the current goals. "
                    + "Check that target grade is above your starting grade (G0).</body></html>");
        } else {
            materialsEmptyLabel.setText("");
        }
        if (hasAnyGoals && trades.isEmpty() && !shortfalls.isEmpty()) {
            tradeEmptyLabel.setText("<html><body style='color:#ffcc88'>No material-trader swaps found from current inventory.</body></html>");
        } else if (hasConsolidationGoals && trades.isEmpty()) {
            tradeEmptyLabel.setText("<html><body style='color:#ffcc88'>No same-row upgrades available for excess commons.</body></html>");
        } else if (hasAnyGoals && trades.isEmpty() && hasActiveBlueprintGoals) {
            tradeEmptyLabel.setText("");
        } else if (hasAnyGoals && trades.isEmpty() && hasActiveConsolidationGoals) {
            tradeEmptyLabel.setText("<html><body style='color:#ffcc88'>No trade suggestions for active goals.</body></html>");
        } else if (hasAnyGoals && trades.isEmpty()) {
            tradeEmptyLabel.setText("<html><body style='color:#ffcc88'>Enable a goal (checkbox) to see materials and trades.</body></html>");
        }
        autoSizeEngineeringTables();
    }

    private static int estimateSlotsFreed(List<TradeSuggestion> trades) {
        if (trades == null) {
            return 0;
        }
        int freed = 0;
        for (TradeSuggestion trade : trades) {
            freed += trade.getFromCount() - trade.getToCount();
        }
        return Math.max(0, freed);
    }

    private void autoSizeEngineeringTables() {
        SwingUtilities.invokeLater(() -> {
            UtilTable.autoSizeTableColumns(goalsTable);
            UtilTable.autoSizeTableColumns(shoppingTable);
        });
    }

    private void updateTradeSections(List<TradeSuggestion> trades, Map<String, Integer> shortfalls) {
        tradeSectionsPanel.removeAll();
        Map<String, List<TradeTargetGroup>> grouped =
                MaterialTradePlanner.groupByTraderTypeAndTarget(trades, shortfalls);
        Font base = tradeSectionBaseFont != null ? tradeSectionBaseFont : OverlayPreferences.getUiFont();
        int fontSize = tradeSectionFontSize > 0 ? tradeSectionFontSize : OverlayPreferences.getUiFontSize();
        for (Map.Entry<String, List<TradeTargetGroup>> entry : grouped.entrySet()) {
            JLabel header = traderTypeHeader(entry.getKey(), base, fontSize);
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            tradeSectionsPanel.add(header);
            for (TradeTargetGroup target : entry.getValue()) {
                JPanel row = buildTradeTargetRow(target, base, fontSize);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                tradeSectionsPanel.add(row);
            }
            tradeSectionsPanel.add(Box.createVerticalStrut(8));
        }
        tradeSectionsPanel.revalidate();
        tradeSectionsPanel.repaint();
    }

    private static JPanel buildTradeTargetRow(TradeTargetGroup group, Font base, int fontSize) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, EdoUi.Internal.mainTextAlpha(48)),
                new EmptyBorder(8, 4, 8, 4)));

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);
        left.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, EdoUi.Internal.mainTextAlpha(96)),
                new EmptyBorder(0, 0, 0, 10)));
        Dimension leftCol = new Dimension(TRADE_TARGET_COLUMN_WIDTH_PX, 0);
        left.setPreferredSize(leftCol);
        left.setMinimumSize(new Dimension(TRADE_TARGET_COLUMN_WIDTH_PX, 0));
        left.setMaximumSize(new Dimension(TRADE_TARGET_COLUMN_WIDTH_PX, Integer.MAX_VALUE));

        JLabel targetName = new JLabel(wrapTradeTargetHtml(group.getToName()));
        targetName.setFont(base.deriveFont(Font.BOLD, fontSize + 1));
        targetName.setForeground(EdoUi.User.MAIN_TEXT);
        targetName.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(targetName);

        if (group.getShortfall() > 0) {
            JLabel need = new JLabel("Need ×" + group.getShortfall());
            need.setFont(base.deriveFont(Font.PLAIN, fontSize));
            need.setForeground(new Color(255, 160, 120));
            need.setAlignmentX(Component.LEFT_ALIGNMENT);
            need.setBorder(new EmptyBorder(2, 0, 0, 0));
            left.add(need);
        }

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setOpaque(false);

        List<TradeSuggestion> options = group.getOptions();
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) {
                right.add(Box.createVerticalStrut(4));
            }
            right.add(payOptionLine(options.get(i), base, fontSize));
        }

        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.CENTER);
        return row;
    }

    private static String wrapTradeTargetHtml(String text) {
        String safe = text == null ? "" : text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        int wrapPx = TRADE_TARGET_COLUMN_WIDTH_PX - 12;
        return "<html><body style='width:" + wrapPx + "px'>" + safe + "</body></html>";
    }

    private static JLabel payOptionLine(TradeSuggestion trade, Font base, int fontSize) {
        String text = "Pay " + trade.getFromCount() + "× " + trade.getFromName()
                + "  →  get " + trade.getToCount() + "×";
        JLabel label = new JLabel(text);
        label.setFont(base.deriveFont(Font.PLAIN, fontSize));
        label.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
        label.setBorder(new EmptyBorder(2, 8, 2, 4));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel traderTypeHeader(String traderType, Font base, int fontSize) {
        JLabel label = new JLabel(traderTypeLabel(traderType));
        label.setFont(base.deriveFont(Font.BOLD, fontSize));
        label.setForeground(EdoUi.User.MAIN_TEXT);
        label.setBorder(new EmptyBorder(6, 4, 2, 4));
        return label;
    }

    private static String traderTypeLabel(String type) {
        if (type == null || type.isBlank()) {
            return "Material trader";
        }
        return switch (type) {
            case "Raw" -> "Raw material trader";
            case "Manufactured" -> "Manufactured material trader";
            case "Encoded" -> "Encoded material trader";
            default -> type + " material trader";
        };
    }

    private void updateGoalsSummary(Map<String, Integer> inv, Map<String, Integer> invAfterTrades) {
        if (goals.isEmpty()) {
            goalsSummaryLabel.setText("<html><body style='color:#ffcc88'>No goals yet — click <b>Add a goal</b> to start planning.</body></html>");
            return;
        }
        List<EngineeringGoal> counted = goals.stream()
                .filter(g -> g != null && g.isIncludeInPlanning() && !g.isInventoryConsolidation())
                .toList();
        if (counted.isEmpty()) {
            goalsSummaryLabel.setText("<html><body style='color:#ffcc88'>"
                    + goals.size() + " goal(s) listed — enable a checkbox to include in materials and trades."
                    + "</body></html>");
            return;
        }
        int readyNow = planner.countGoalsWithReadiness(counted, inv, invAfterTrades, GoalReadiness.READY);
        int readyWithTrades = planner.countGoalsWithReadiness(counted, inv, invAfterTrades, GoalReadiness.READY_WITH_TRADES);
        int stillShort = planner.countGoalsWithReadiness(counted, inv, invAfterTrades, GoalReadiness.STILL_SHORT);
        int readyAfterTrades = readyNow + readyWithTrades;

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='color:#ffcc88'>");
        sb.append(readyNow).append(" of ").append(counted.size()).append(" active goals ready now");
        if (readyWithTrades > 0) {
            sb.append("; ").append(readyWithTrades).append(" more if you follow suggested trades");
        }
        if (stillShort > 0) {
            sb.append("; ").append(stillShort).append(" still short");
        }
        sb.append(" (").append(readyAfterTrades).append(" completable after trades).</body></html>");
        goalsSummaryLabel.setText(sb.toString());
    }

    private void updateMaterialsCollectHint(Map<String, Integer> shortfallsAfterTrades) {
        if (goals.isEmpty() || shortfallsAfterTrades == null || shortfallsAfterTrades.isEmpty()) {
            materialsCollectLabel.setText(" ");
            return;
        }
        materialsCollectLabel.setText("<html><body style='color:#ffb380'>"
                + "<b>Collect manually</b> (after trades): "
                + formatMaterialCounts(shortfallsAfterTrades)
                + "<br><span style='color:#ffcc88'>Type: Raw = planets/SRV · Manufactured = combat/drops · "
                + "Encoded = scans/wakes</span></body></html>");
    }

    private String formatMaterialCounts(Map<String, Integer> counts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(htmlEscape(database.materialDisplayName(e.getKey())))
                    .append(" ×").append(e.getValue());
            var mat = database.material(e.getKey());
            if (mat.isPresent() && !mat.get().getType().isBlank()) {
                sb.append(" (").append(htmlEscape(mat.get().getType())).append(")");
            }
        }
        return sb.toString();
    }

    private static String htmlEscape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void updateTradeSummary(List<TradeSuggestion> trades,
                                    Map<String, Integer> shortfalls,
                                    Map<String, Integer> shortfallsAfterTrades) {
        if (goals.isEmpty()) {
            tradeSummaryLabel.setText(" ");
            return;
        }
        if (shortfalls.isEmpty()) {
            tradeSummaryLabel.setText("<html><body style='color:#90ee90'>No trades needed — inventory covers all goals.</body></html>");
            return;
        }
        if (trades.isEmpty()) {
            tradeSummaryLabel.setText("<html><body style='color:#ffcc88'>"
                    + shortfalls.size() + " material type(s) short; no trader swaps found. Collect: "
                    + formatMaterialCounts(shortfalls) + ".</body></html>");
            return;
        }
        int remainingTypes = shortfallsAfterTrades.size();
        int targetCount = groupedTargetCount(trades);
        if (remainingTypes == 0) {
            tradeSummaryLabel.setText("<html><body style='color:#90ee90'>Picking one route per receive material below "
                    + "could cover every shortfall.</body></html>");
        } else {
            tradeSummaryLabel.setText("<html><body style='color:#ffcc88'>Pick <b>one</b> pay option per receive material "
                    + "(not all). " + targetCount + " material(s) have trader routes; "
                    + remainingTypes + " type(s) still need manual collection: "
                    + formatMaterialCounts(shortfallsAfterTrades) + ".</body></html>");
        }
    }

    private static int groupedTargetCount(List<TradeSuggestion> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0;
        }
        return (int) trades.stream().map(TradeSuggestion::getToKey).distinct().count();
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
        tradeSectionBaseFont = font;
        tradeSectionFontSize = fontSize;
        EdoMiningSplitPaneUi.applyDividerTheme(mainSplit);
        EdoMiningSplitPaneUi.applyDividerTheme(lowerSplit);
        scheduleRefresh();
    }

    private String formatStatusText(EngineeringGoal goal, GoalReadiness readiness, Map<String, Integer> invAfterTrades) {
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

    private final class GoalStatusCellRenderer extends EdoTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
            if (!isSelected && goalsTable.getColumnName(column).equals("Status") && row < goalReadiness.size()) {
                if (row < goalStatusText.size() && "Hidden".equals(goalStatusText.get(row))) {
                    c.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
                } else {
                Color color = switch (goalReadiness.get(row)) {
                    case READY -> EdoUi.User.SUCCESS;
                    case READY_WITH_TRADES -> new Color(200, 220, 100);
                    case STILL_SHORT -> new Color(255, 160, 120);
                };
                c.setForeground(color);
                }
            }
            return c;
        }
    }

    private final class GoalsTableModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return goals.size();
        }

        @Override
        public int getColumnCount() {
            return 5;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : String.class;
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
                case 2 -> "Target";
                case 3 -> "Experimental";
                case 4 -> "Status";
                default -> "";
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            EngineeringGoal g = goals.get(rowIndex);
            if (columnIndex == 0) {
                return g.isIncludeInPlanning();
            }
            if (g.isInventoryConsolidation()) {
                return switch (columnIndex) {
                    case 1 -> "Inventory: reduce commons";
                    case 2 -> g.getFromGrade() <= 1
                            ? "G1 → G" + g.getTargetGrade()
                            : "G1–G" + g.getFromGrade() + " → G" + g.getTargetGrade();
                    case 3 -> "—";
                    case 4 -> rowIndex < goalStatusText.size() ? goalStatusText.get(rowIndex) : "";
                    default -> "";
                };
            }
            return switch (columnIndex) {
                case 1 -> g.getModuleType() + ": " + g.getBlueprintName();
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
}
