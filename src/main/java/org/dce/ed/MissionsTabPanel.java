package org.dce.ed;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

import org.dce.ed.edsm.UtilTable;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.BountyEvent;
import org.dce.ed.logreader.event.CargoDepotEvent;
import org.dce.ed.logreader.event.FactionKillBondEvent;
import org.dce.ed.logreader.event.MissionAbandonedEvent;
import org.dce.ed.logreader.event.MissionAcceptedEvent;
import org.dce.ed.logreader.event.MissionCompletedEvent;
import org.dce.ed.logreader.event.MissionFailedEvent;
import org.dce.ed.logreader.event.MissionRedirectedEvent;
import org.dce.ed.logreader.event.MissionsEvent;
import org.dce.ed.mission.CommodityMissionGroup;
import org.dce.ed.mission.CommoditySourceSearch;
import org.dce.ed.mission.MissionCategory;
import org.dce.ed.mission.MissionDestination;
import org.dce.ed.mission.MissionDestinationResolver;
import org.dce.ed.mission.MissionRecord;
import org.dce.ed.mission.MissionSpeechTracker;
import org.dce.ed.mission.MissionTracker;
import org.dce.ed.mission.TransportPlanPreparation;
import org.dce.ed.mission.TransportPlanPreparer;
import org.dce.ed.mission.TransportPlanProblem;
import org.dce.ed.mission.TransportRoutePlan;
import org.dce.ed.mission.TransportRoutePlanner;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.ui.DestinationCopySupport;
import org.dce.ed.ui.CommoditySourceDialog;
import org.dce.ed.ui.MultiCommoditySourceDialog;
import org.dce.ed.ui.TransportRoutePlanDialog;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.HoverClickPoller;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.SelectiveHitSupport;
import org.dce.ed.exec.ExecTabButtonStrip;
import org.dce.ed.exec.ExecTriggerService;
import org.dce.ed.ui.tabdock.OverlayTabId;
import org.dce.ed.ui.TableCellToolTipSupport;
import org.dce.ed.ui.TableHeaderSortSupport;
import org.dce.ed.ui.TransparentTableHeader;
import org.dce.ed.ui.TransparentTableHeaderUI;
import org.dce.ed.ui.TransparentViewportUI;
import org.dce.ed.util.EdsmClient;

/**
 * Transport tab: trucking-style missions (cargo / courier / passenger), From/To places, filters.
 */
public class MissionsTabPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private enum Filter { ALL, CARGO, COURIER, PASSENGER }

    private final MissionTracker tracker = new MissionTracker();
    private final BooleanSupplier passThroughEnabledSupplier;
    private ExecTabButtonStrip execButtonStrip;
    private final Supplier<Boolean> dockedSupplier;
    private final Supplier<String> currentSystemSupplier;
    private final Supplier<String> currentStationSupplier;
    private final IntSupplier cargoCapacitySupplier;
    private final Consumer<List<String>> optimizedRouteConsumer;

    private Runnable sessionStateChangeCallback;
    private Runnable immediateSessionStateChangeCallback;
    private Filter filter = Filter.ALL;

    private static final int FILTER_HOVER_DELAY_MS = 500;
    private static final int HEADER_SORT_HOVER_MS = 500;

    private final JLabel activeCountLabel = new JLabel("Active: 0");
    private final JButton optimizeStopsButton = new JButton("Optimize Stops");
    private final JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    private final List<JButton> filterButtons = new ArrayList<>();
    private final List<Filter> filterButtonFilters = new ArrayList<>();
    private final JPanel redirectBanner = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
    private final JLabel redirectLabel = new JLabel();
    private final JButton redirectDismiss = new JButton("Dismiss");
    private final JPanel commodityGroupsPanel = new JPanel();
    private final JPanel sourceAllBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
    private final JButton sourceAllButton = new JButton("Source all");
    private final JPanel contentCenter = new JPanel() {
        private static final long serialVersionUID = 1L;

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (mouseInteractionModeForHost() != MouseInteractionMode.NORMAL) {
                return;
            }
            Color background = EdoUi.User.BACKGROUND;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setComposite(AlphaComposite.Src);
                g2.setColor(new Color(background.getRed(), background.getGreen(), background.getBlue(), 255));
                g2.fillRect(0, 0, getWidth(), getHeight());
            } finally {
                g2.dispose();
            }
        }
    };
    private final MissionsTableModel tableModel = new MissionsTableModel();
    /**
     * Full-text tooltips on Summary / Objective / Places (renderer tips are unreliable on overlay).
     */
    private final JTable missionsTable = new JTable(tableModel) {
        private static final long serialVersionUID = 1L;

        @Override
        public String getToolTipText(MouseEvent event) {
            int row = rowAtPoint(event.getPoint());
            int col = columnAtPoint(event.getPoint());
            if (row < 0 || col < 0) {
                return null;
            }
            int modelCol = convertColumnIndexToModel(col);
            int modelRow = convertRowIndexToModel(row);
            if (modelCol == MissionsTableModel.COL_PLACES) {
                MissionRow mr = tableModel.rowAt(modelRow);
                return mr != null ? mr.placesTooltip() : null;
            }
            if (modelCol == MissionsTableModel.COL_SUMMARY
                    || modelCol == MissionsTableModel.COL_OBJECTIVE) {
                String tip = TableCellToolTipSupport.cellTextAt(this, event);
                if (tip != null && !tip.isBlank() && !"—".equals(tip)) {
                    return tip;
                }
            }
            return null;
        }
    };
    private final TableRowSorter<MissionsTableModel> missionsSorter = new TableRowSorter<>(tableModel);
    /** Created after the custom header is installed (see constructor). */
    private JScrollPane tableScroll;
    private final Timer refreshTimer;
    private TransportRoutePlan lastOptimizedPlan;
    private int optimizeRequestId;

    public MissionsTabPanel(BooleanSupplier passThroughEnabledSupplier,
            Supplier<Boolean> dockedSupplier,
            Supplier<String> currentSystemSupplier,
            Supplier<String> currentStationSupplier) {
        this(passThroughEnabledSupplier, dockedSupplier, currentSystemSupplier, currentStationSupplier,
                () -> 0, systems -> { });
    }

    public MissionsTabPanel(BooleanSupplier passThroughEnabledSupplier,
            Supplier<Boolean> dockedSupplier,
            Supplier<String> currentSystemSupplier,
            Supplier<String> currentStationSupplier,
            IntSupplier cargoCapacitySupplier,
            Consumer<List<String>> optimizedRouteConsumer) {
        super(new BorderLayout());
        this.passThroughEnabledSupplier = passThroughEnabledSupplier;
        this.dockedSupplier = dockedSupplier;
        this.currentSystemSupplier = currentSystemSupplier;
        this.currentStationSupplier = currentStationSupplier;
        this.cargoCapacitySupplier = cargoCapacitySupplier;
        this.optimizedRouteConsumer = optimizedRouteConsumer;
        tracker.setCurrentSystemSupplier(currentSystemSupplier);
        tracker.setCurrentStationSupplier(currentStationSupplier);

        setOpaque(false);
        setBackground(EdoUi.User.BACKGROUND);

        Font base = OverlayPreferences.getUiFont();
        activeCountLabel.setFont(base.deriveFont(Font.BOLD, OverlayPreferences.getUiFontSize()));
        activeCountLabel.setForeground(EdoUi.User.MAIN_TEXT);
        OverlayOutlineButtonStyle.applyPrimaryHitSafe(optimizeStopsButton, base);
        optimizeStopsButton.addActionListener(e -> optimizeStops());

        buildFilterBar(base);
        buildRedirectBanner(base);

        commodityGroupsPanel.setLayout(new BoxLayout(commodityGroupsPanel, BoxLayout.Y_AXIS));
        commodityGroupsPanel.setOpaque(false);
        sourceAllBar.setOpaque(false);
        OverlayOutlineButtonStyle.applyPrimaryHitSafe(sourceAllButton, base);
        sourceAllButton.addActionListener(e -> openMultiCommoditySourceDialog());
        sourceAllBar.add(sourceAllButton);

        configureMissionsTable(base);

        DefaultTableCellRenderer renderer = new MissionCellRenderer();
        missionsTable.setDefaultRenderer(Object.class, renderer);
        missionsTable.getColumnModel().getColumn(MissionsTableModel.COL_PLACES)
                .setCellRenderer(new PlacesCellRenderer());
        missionsTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int viewRow = missionsTable.rowAtPoint(e.getPoint());
                int viewCol = missionsTable.columnAtPoint(e.getPoint());
                if (viewRow < 0 || viewCol < 0
                        || missionsTable.convertColumnIndexToModel(viewCol) != MissionsTableModel.COL_PLACES) return;
                Rectangle cell = missionsTable.getCellRect(viewRow, viewCol, true);
                if (e.getY() > cell.y + cell.height / 2 || e.getX() < cell.x + cell.width - 125) return;
                MissionRow row = tableModel.rowAt(missionsTable.convertRowIndexToModel(viewRow));
                if (row != null && row.record.isSelfSourcedCommodityMission()) {
                    openCommoditySourceDialog(row.record);
                    e.consume();
                }
            }
        });

        Comparator<String> textCmp = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
        missionsSorter.setComparator(MissionsTableModel.COL_TYPE, textCmp);
        missionsSorter.setComparator(MissionsTableModel.COL_SUMMARY, textCmp);
        missionsSorter.setComparator(MissionsTableModel.COL_OBJECTIVE, textCmp);
        missionsSorter.setComparator(MissionsTableModel.COL_PLACES, textCmp);
        missionsTable.setAutoCreateRowSorter(false);
        missionsTable.setRowSorter(missionsSorter);
        TableHeaderSortSupport.install(missionsTable, passThroughEnabledSupplier, HEADER_SORT_HOVER_MS);

        DestinationCopySupport.install(
                missionsTable,
                MissionsTableModel.COL_OBJECTIVE,
                MissionsTableModel.COL_PLACES,
                tableModel::objectiveCopyText,
                tableModel::placesCopyText,
                passThroughEnabledSupplier);

        // Create scroll pane after the custom header is installed so the column header
        // viewport binds to TransparentTableHeader (not the default LAF header).
        tableScroll = new JScrollPane(missionsTable);
        contentCenter.setLayout(new BoxLayout(contentCenter, BoxLayout.Y_AXIS));
        contentCenter.setOpaque(false);
        contentCenter.add(commodityGroupsPanel);
        contentCenter.add(sourceAllBar);
        tableScroll.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        tableScroll.setOpaque(false);
        tableScroll.setBackground(EdoUi.Internal.TRANSPARENT);
        tableScroll.getViewport().setOpaque(false);
        tableScroll.getViewport().setBackground(EdoUi.Internal.TRANSPARENT);
        tableScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        tableScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        // Height hint only — width follows the overlay; columns are fitted to the viewport.
        tableScroll.setPreferredSize(new Dimension(100, 280));
        tableScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        installMissionsColumnHeaderViewport();
        tableScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                autoSizeMissionsColumns();
            }
        });
        contentCenter.add(tableScroll);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(4, 4, 0, 4));
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.add(filterBar, BorderLayout.WEST);
        JPanel optimizeAndCount = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        optimizeAndCount.setOpaque(false);
        optimizeAndCount.add(optimizeStopsButton);
        optimizeAndCount.add(activeCountLabel);
        topRow.add(optimizeAndCount, BorderLayout.EAST);
        top.add(topRow, BorderLayout.NORTH);
        top.add(redirectBanner, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(contentCenter, BorderLayout.CENTER);
        execButtonStrip = new ExecTabButtonStrip(OverlayTabId.MISSIONS, passThroughEnabledSupplier);
        add(execButtonStrip, BorderLayout.SOUTH);

        tracker.setChangeCallback(() -> { invalidateOptimizedPlan(); scheduleRefresh(); });
        CargoMonitor.getInstance().addListener(s -> { invalidateOptimizedPlan(); scheduleRefresh(); });

        refreshTimer = new Timer(30_000, e -> refreshUi());
        refreshTimer.setRepeats(true);
        refreshTimer.start();

        refreshUi();
    }

    private void optimizeStops() {
        if (lastOptimizedPlan != null) {
            showOptimizedPlan(lastOptimizedPlan);
            return;
        }
        List<MissionRecord> active = tracker.getActive();
        List<MissionRecord> missing = active.stream()
                .filter(MissionRecord::isSelfSourcedCommodityMission)
                .filter(m -> remainingCargoRequirement(m) > 0)
                .filter(m -> (m.getSourcedFromSystem() == null || m.getSourcedFromSystem().isBlank())
                        || (m.getSourcedFromStation() == null || m.getSourcedFromStation().isBlank()))
                .toList();
        if (!missing.isEmpty()) {
            String names = missing.stream().map(MissionRecord::summaryLine)
                    .reduce((a, b) -> a + "\n• " + b).orElse("");
            Object[] options = { "Set Sources", "Close" };
            int choice = JOptionPane.showOptionDialog(this,
                    "Every sourced mission needs a system and station before optimizing.\n\n• " + names,
                    "Sources required", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]);
            if (choice == 0) openMultiCommoditySourceDialog();
            return;
        }
        int capacity = cargoCapacitySupplier.getAsInt();
        var snapshot = CargoMonitor.getInstance().getSnapshot();
        var cargo = snapshot != null ? snapshot.getCargoJson() : null;
        String currentSystem = currentSystemSupplier.get();
        String currentStation = currentStationSupplier.get();
        int request = ++optimizeRequestId;
        optimizeStopsButton.setEnabled(false);
        optimizeStopsButton.setText("Optimizing…");
        new SwingWorker<OptimizationResult, Void>() {
            @Override protected OptimizationResult doInBackground() {
                EdsmClient edsm = new EdsmClient();
                TransportPlanPreparation prepared = TransportPlanPreparer.prepare(active,
                        currentSystem, currentStation, capacity, cargo, system -> {
                            var response = edsm.getSystem(system);
                            return response != null && response.coords != null
                                    ? new double[] { response.coords.x, response.coords.y, response.coords.z }
                                    : null;
                        });
                if (prepared.request() == null) return new OptimizationResult(prepared, null);
                return new OptimizationResult(prepared, TransportRoutePlanner.plan(prepared.request()));
            }

            @Override protected void done() {
                if (request != optimizeRequestId) return;
                optimizeStopsButton.setEnabled(true);
                optimizeStopsButton.setText("Optimize Stops");
                try {
                    OptimizationResult result = get();
                    TransportPlanPreparation prepared = result.preparation();
                    if (!prepared.problems().isEmpty()) {
                        showPlanProblems(prepared.problems());
                        return;
                    }
                    lastOptimizedPlan = result.plan();
                    optimizeStopsButton.setText("View Optimized Plan");
                    showOptimizedPlan(lastOptimizedPlan);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MissionsTabPanel.this,
                            "The Transport plan could not be calculated. " + ex.getMessage(),
                            "Optimization failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void showOptimizedPlan(TransportRoutePlan plan) {
        java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
        new TransportRoutePlanDialog(owner, plan, cargoCapacitySupplier.getAsInt(),
                optimizedRouteConsumer).setVisible(true);
    }

    private void showPlanProblems(List<TransportPlanProblem> problems) {
        String message = problems.stream().map(TransportPlanProblem::message)
                .reduce((a, b) -> a + "\n• " + b).orElse("The plan is incomplete.");
        JOptionPane.showMessageDialog(this, "• " + message, "Cannot optimize stops",
                JOptionPane.WARNING_MESSAGE);
    }

    private void invalidateOptimizedPlan() {
        optimizeRequestId++;
        lastOptimizedPlan = null;
        optimizeStopsButton.setEnabled(true);
        optimizeStopsButton.setText("Optimize Stops");
    }

    private static int remainingCargoRequirement(MissionRecord mission) {
        int required = mission.getCountRequired() > 0
                ? mission.getCountRequired() : mission.getTotalItemsToDeliver();
        return Math.max(0, required - mission.getItemsDelivered());
    }

    private record OptimizationResult(TransportPlanPreparation preparation,
            TransportRoutePlan plan) { }

    public void setExecTriggerService(ExecTriggerService service) {
        if (execButtonStrip != null) {
            execButtonStrip.setExecTriggerService(service);
        }
    }

    private void openCommoditySourceDialog(MissionRecord mission) {
        String current = currentSystemSupplier != null ? currentSystemSupplier.get() : null;
        java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
        new CommoditySourceDialog(owner, mission, current, new CommoditySourceSearch(), (system, station) -> {
            applySourcedFromSelection(mission.getMissionId(), system, station);
        }).setVisible(true);
    }

    private void openMultiCommoditySourceDialog() {
        List<MissionRecord> missions = tracker.getActive();
        List<org.dce.ed.mission.MultiCommodityMissionNeed> needs = MultiCommoditySourceDialog.buildNeeds(missions);
        if (needs.isEmpty()) return;
        Map<String, Integer> hold = new LinkedHashMap<>();
        for (org.dce.ed.mission.MultiCommodityMissionNeed need : needs)
            hold.putIfAbsent(need.commodity(), MissionTracker.commodityInHold(need.commodity()));
        String current = currentSystemSupplier != null ? currentSystemSupplier.get() : null;
        java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
        new MultiCommoditySourceDialog(owner, missions, current, hold, new CommoditySourceSearch(), assessment -> {
            int changed = tracker.setSourcedFromIfUnassigned(assessment.allocation().missionIds(),
                    assessment.station().system(), assessment.station().station());
            if (changed <= 0) return;
            refreshUi();
            if (immediateSessionStateChangeCallback != null) immediateSessionStateChangeCallback.run();
            else if (sessionStateChangeCallback != null) sessionStateChangeCallback.run();
        }).setVisible(true);
    }

    boolean applySourcedFromSelection(long missionId, String system, String station) {
        if (!tracker.setSourcedFrom(missionId, system, station)) {
            return false;
        }
        refreshUi();
        if (immediateSessionStateChangeCallback != null) {
            immediateSessionStateChangeCallback.run();
        } else if (sessionStateChangeCallback != null) {
            sessionStateChangeCallback.run();
        }
        return true;
    }

    private void buildFilterBar(Font base) {
        filterBar.setOpaque(false);
        addFilterChip("All", Filter.ALL, base);
        addFilterChip("Cargo", Filter.CARGO, base);
        addFilterChip("Courier", Filter.COURIER, base);
        addFilterChip("Passenger", Filter.PASSENGER, base);
    }

    private void addFilterChip(String label, Filter f, Font base) {
        JButton b = new JButton(label);
        OverlayOutlineButtonStyle.applyChip(b, base, filter == f);
        b.addActionListener(e -> selectFilter(f));
        HoverClickPoller.register(b, FILTER_HOVER_DELAY_MS, () -> selectFilter(f), passThroughEnabledSupplier);
        filterButtons.add(b);
        filterButtonFilters.add(f);
        filterBar.add(b);
    }

    private void selectFilter(Filter f) {
        filter = f;
        updateFilterChipStyles();
        refreshUi();
    }

    private void updateFilterChipStyles() {
        Font base = OverlayPreferences.getUiFont();
        for (int i = 0; i < filterButtons.size(); i++) {
            JButton b = filterButtons.get(i);
            Filter f = filterButtonFilters.get(i);
            OverlayOutlineButtonStyle.applyChip(b, base, filter == f);
        }
    }

    private void configureMissionsTable(Font base) {
        missionsTable.setOpaque(false);
        missionsTable.setBackground(EdoUi.Internal.TRANSPARENT);
        missionsTable.setForeground(EdoUi.User.MAIN_TEXT);
        missionsTable.setShowGrid(false);
        missionsTable.setShowHorizontalLines(false);
        missionsTable.setShowVerticalLines(false);
        missionsTable.setIntercellSpacing(new Dimension(0, 0));
        missionsTable.setGridColor(EdoUi.Internal.TRANSPARENT);
        int fontPx = OverlayPreferences.getUiFontSize();
        int rowH = Math.max(40, (fontPx + 10) * 2);
        int headerH = Math.max(22, fontPx + 10);
        missionsTable.setRowHeight(rowH);
        missionsTable.setFont(base.deriveFont(Font.PLAIN, fontPx));
        missionsTable.setFillsViewportHeight(false);
        missionsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        missionsTable.setTableHeader(new TransparentTableHeader(missionsTable.getColumnModel()));
        JTableHeader th = missionsTable.getTableHeader();
        if (th != null) {
            th.setUI(TransparentTableHeaderUI.createUI(th));
            th.setOpaque(!OverlayPreferences.overlayChromeRequestsTransparency(this));
            th.setForeground(EdoUi.User.MAIN_TEXT);
            th.setBackground(OverlayPreferences.overlayChromeRequestsTransparency(this)
                    ? EdoUi.Internal.TRANSPARENT : EdoUi.User.BACKGROUND);
            th.setFont(base.deriveFont(Font.BOLD, fontPx));
            th.setBorder(null);
            th.setReorderingAllowed(false);
            th.setFocusable(false);
            th.putClientProperty("JTableHeader.focusCellBackground", null);
            th.putClientProperty("JTableHeader.cellBorder", null);
            th.setDefaultRenderer(new MissionsHeaderRenderer());
            th.setPreferredSize(new Dimension(Math.max(1, th.getPreferredSize().width), headerH));
        }
        // If the scroll pane already exists (prefs refresh), re-bind the column header view.
        if (tableScroll != null) {
            tableScroll.setColumnHeaderView(th);
            installMissionsColumnHeaderViewport();
        }
    }

    private void installMissionsColumnHeaderViewport() {
        if (tableScroll == null) {
            return;
        }
        JTableHeader th = missionsTable.getTableHeader();
        if (th != null && tableScroll.getColumnHeader() != null
                && tableScroll.getColumnHeader().getView() != th) {
            tableScroll.setColumnHeaderView(th);
        }
        JViewport headerViewport = tableScroll.getColumnHeader();
        if (headerViewport != null) {
            headerViewport.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
            headerViewport.setOpaque(false);
            headerViewport.setBackground(EdoUi.Internal.TRANSPARENT);
            headerViewport.setUI(TransparentViewportUI.createUI(headerViewport));
            headerViewport.setBorder(null);
        }
    }

    private void buildRedirectBanner(Font base) {
        boolean transparent = OverlayPreferences.overlayChromeRequestsTransparency(this);
        redirectBanner.setOpaque(!transparent);
        redirectBanner.setBackground(transparent ? EdoUi.Internal.TRANSPARENT : EdoUi.ED_ORANGE_LESS_TRANS);
        redirectBanner.setVisible(false);
        redirectLabel.setFont(base.deriveFont(Font.PLAIN, OverlayPreferences.getUiFontSize()));
        redirectLabel.setForeground(EdoUi.User.MAIN_TEXT);
        redirectLabel.setOpaque(false);
        OverlayOutlineButtonStyle.applyChip(redirectDismiss, base, false);
        HoverClickPoller.register(redirectDismiss, FILTER_HOVER_DELAY_MS, () -> {
            tracker.dismissRedirectBanner();
            refreshUi();
        }, passThroughEnabledSupplier);
        redirectDismiss.addActionListener(e -> {
            tracker.dismissRedirectBanner();
            refreshUi();
        });
        redirectBanner.add(redirectLabel);
        redirectBanner.add(redirectDismiss);
    }

    /** Re-apply fonts and chrome when overlay preferences change. */
    public void refreshFromSavedOverlayPreferences() {
        Font base = OverlayPreferences.getUiFont();
        activeCountLabel.setFont(base.deriveFont(Font.BOLD, OverlayPreferences.getUiFontSize()));
        updateFilterChipStyles();
        OverlayOutlineButtonStyle.applyChip(redirectDismiss, base, false);
        redirectLabel.setFont(base.deriveFont(Font.PLAIN, OverlayPreferences.getUiFontSize()));
        configureMissionsTable(base);
        if (missionsTable.getColumnCount() > MissionsTableModel.COL_PLACES) {
            missionsTable.getColumnModel().getColumn(MissionsTableModel.COL_PLACES)
                    .setCellRenderer(new PlacesCellRenderer());
        }
        autoSizeMissionsColumns();
        repaint();
    }

    public MissionTracker getTracker() {
        return tracker;
    }

    public void setSessionStateChangeCallback(Runnable callback) {
        this.sessionStateChangeCallback = callback;
        tracker.setChangeCallback(() -> {
            scheduleRefresh();
            if (sessionStateChangeCallback != null) {
                sessionStateChangeCallback.run();
            }
        });
    }

    /** Persist manual mission annotations before an IDE/process stop can bypass the debounce timer. */
    public void setImmediateSessionStateChangeCallback(Runnable callback) {
        this.immediateSessionStateChangeCallback = callback;
    }

    public void fillSessionState(EdoSessionState state) {
        tracker.fillSessionState(state);
    }

    /** Selective mouse mode: filters, Dismiss, sort headers, Objective/Places cells. */
    public boolean isPointerOverInteractiveRegion(Point screenPoint) {
        if (execButtonStrip != null && execButtonStrip.isPointerOverActionButton(screenPoint)) {
            return true;
        }
        if (SelectiveHitSupport.containsScreenPoint(filterBar, screenPoint)) {
            return true;
        }
        if (SelectiveHitSupport.containsScreenPoint(redirectDismiss, screenPoint)) {
            return true;
        }
        if (SelectiveHitSupport.containsScreenPoint(optimizeStopsButton, screenPoint)) {
            return true;
        }
        if (SelectiveHitSupport.isOverTableHeader(missionsTable, screenPoint)) {
            return true;
        }
        return SelectiveHitSupport.isOverModelColumnCell(
                missionsTable,
                screenPoint,
                MissionsTableModel.COL_OBJECTIVE,
                MissionsTableModel.COL_PLACES);
    }

    /**
     * Selective (hybrid) mode: the missions table does not fill the viewport, so chrome below the
     * last row (and the scroll padding under it) stays tinted. Punch that strip fully transparent.
     */
    @Override
    public void paint(Graphics g) {
        super.paint(g);
        clearBelowMissionsTableInSelectiveMode(g);
    }

    private void clearBelowMissionsTableInSelectiveMode(Graphics g) {
        if (g == null
                || OverlayPreferences.getOverlayMouseInteractionMode() != MouseInteractionMode.SELECTIVE
                || !OverlayPreferences.isPassThroughWindowActive()
                || missionsTable == null || !missionsTable.isShowing()) {
            return;
        }
        int rowCount = missionsTable.getRowCount();
        int rowsBottomInTable = 0;
        if (rowCount > 0) {
            Rectangle last = missionsTable.getCellRect(rowCount - 1, 0, true);
            rowsBottomInTable = last.y + last.height;
        }
        Point tableBottom = SwingUtilities.convertPoint(missionsTable, 0, rowsBottomInTable, this);
        int yStart = Math.max(0, tableBottom.y);
        // Tall lists: do not start clearing past the visible scroll bottom.
        if (tableScroll != null && tableScroll.isShowing()) {
            Point scrollBottom = SwingUtilities.convertPoint(tableScroll, 0, tableScroll.getHeight(), this);
            yStart = Math.min(yStart, Math.max(0, scrollBottom.y));
        }
        int yEnd = getHeight();
        if (yEnd <= yStart) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
            g2.fillRect(0, yStart, getWidth(), yEnd - yStart);
        } finally {
            g2.dispose();
        }
    }

    private MouseInteractionMode mouseInteractionModeForHost() {
        javax.swing.JRootPane root = getRootPane();
        if (root != null) {
            Object local = root.getClientProperty(OverlayPreferences.WINDOW_MOUSE_MODE_KEY);
            if (local instanceof MouseInteractionMode mode) {
                return mode;
            }
        }
        return OverlayPreferences.getOverlayMouseInteractionMode();
    }

    public void applySessionState(EdoSessionState state) {
        tracker.applySessionState(state);
        scheduleRefresh();
    }

    /**
     * After tab rebuild or sparse persisted state, rebuild the board from journals.
     */
    public void hydrateTrackerFromJournalIfNeeded(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return;
        }
        boolean changed = false;
        if (tracker.hasDetailsPending()) {
            changed = tracker.replayMissionEventsFromJournals(clientKey, true);
        } else {
            changed = tracker.replayMissionEventsFromJournals(clientKey, false);
            if (tracker.hasDetailsPending()) {
                changed = tracker.replayMissionEventsFromJournals(clientKey, true) || changed;
            }
        }
        // Rebuild massacre kill estimates from Bounty history (covers overlay-off kills / bad system gate).
        changed = tracker.rebuildMassacreKillProgressFromJournals(clientKey) || changed;
        if (changed) {
            scheduleRefresh();
        }
    }

    public void handleLogEvent(EliteLogEvent event) {
        if (event == null) {
            return;
        }
        if (event instanceof org.dce.ed.logreader.event.LoadGameEvent) {
            MissionSpeechTracker.getInstance().resetSession();
        }
        EliteEventType type = event.getType();
        if (type == EliteEventType.LOCATION
                || type == EliteEventType.FSD_JUMP
                || type == EliteEventType.CARRIER_JUMP) {
            // Current system/station moved — refresh ready-state highlighting.
            scheduleRefresh();
        }
        if (event instanceof MissionAcceptedEvent
                || event instanceof MissionCompletedEvent
                || event instanceof MissionFailedEvent
                || event instanceof MissionAbandonedEvent
                || event instanceof MissionRedirectedEvent
                || event instanceof CargoDepotEvent
                || event instanceof MissionsEvent
                || event instanceof BountyEvent
                || event instanceof FactionKillBondEvent) {
            MissionRecord completedPrior = null;
            if (event instanceof MissionCompletedEvent completed) {
                completedPrior = tracker.findById(completed.getMissionId());
            }
            tracker.applyEvent(event);
            MissionSpeechTracker.getInstance().announceAfterLiveApply(
                    tracker, event, completedPrior, true);
            scheduleRefresh();
        }
    }

    private void scheduleRefresh() {
        SwingUtilities.invokeLater(this::refreshUi);
    }

    private void refreshUi() {
        updateFilterChipStyles();
        List<MissionRecord> active = filterActive(tracker.getActive());
        activeCountLabel.setText("Active: " + active.size());
        rebuildCommodityGroups();
        tableModel.setRows(buildTableRows(active));
        autoSizeMissionsColumns();
        updateRedirectBanner();
    }

    private void autoSizeMissionsColumns() {
        SwingUtilities.invokeLater(() -> {
            // Content-size first, then fit into the viewport. Stay on AUTO_RESIZE_OFF
            // (Route/Exec pattern) so our preferred widths are not redistributed.
            missionsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            UtilTable.autoSizeTableColumns(missionsTable);
            fitMissionsColumnsToViewport();
            missionsTable.revalidate();
            missionsTable.repaint();
            if (missionsTable.getTableHeader() != null) {
                missionsTable.getTableHeader().repaint();
            }
        });
    }

    /**
     * Fits missions columns into the viewport without crushing text to ~50px.
     * <ul>
     *   <li>Type stays near content width.</li>
     *   <li>Summary / Objective / Places share leftover space with higher mins.</li>
     *   <li>Overflow shrinks Summary first, then Objective, then Places.</li>
     *   <li>Hover shows full Summary / Objective / Places via tooltips.</li>
     * </ul>
     */
    private void fitMissionsColumnsToViewport() {
        int avail = tableScroll != null && tableScroll.getViewport() != null
                ? tableScroll.getViewport().getWidth() : 0;
        if (avail <= 0) {
            avail = missionsTable.getWidth();
        }
        if (avail <= 0) {
            return;
        }
        var cm = missionsTable.getColumnModel();
        int cols = cm.getColumnCount();
        if (cols <= MissionsTableModel.COL_PLACES) {
            return;
        }

        // Cap Type so it does not steal space from text columns.
        clampMissionsColumn(cm.getColumn(MissionsTableModel.COL_TYPE), 48, 72);

        int minSummary = 110;
        int minObjective = 88;
        int minTurnIn = 100;
        TableColumn summary = cm.getColumn(MissionsTableModel.COL_SUMMARY);
        TableColumn objective = cm.getColumn(MissionsTableModel.COL_OBJECTIVE);
        TableColumn turnIn = cm.getColumn(MissionsTableModel.COL_PLACES);
        summary.setPreferredWidth(Math.max(minSummary, summary.getPreferredWidth()));
        objective.setPreferredWidth(Math.max(minObjective, objective.getPreferredWidth()));
        turnIn.setPreferredWidth(Math.max(minTurnIn, turnIn.getPreferredWidth()));

        int fixedTotal = columnPreferred(cm, MissionsTableModel.COL_TYPE);
        int flexAvail = Math.max(0, avail - fixedTotal);
        int flexWanted = summary.getPreferredWidth() + objective.getPreferredWidth() + turnIn.getPreferredWidth();

        if (flexWanted <= flexAvail) {
            // Leftover → Summary, then Turn-in (names / destinations benefit most).
            int leftover = flexAvail - flexWanted;
            int toSummary = (leftover * 2) / 3;
            summary.setPreferredWidth(summary.getPreferredWidth() + toSummary);
            turnIn.setPreferredWidth(turnIn.getPreferredWidth() + (leftover - toSummary));
            return;
        }

        // Shrink Summary first (long mission titles; tooltip covers full text), then Objective, then Turn-in.
        int overflow = flexWanted - flexAvail;
        overflow = shrinkMissionsColumn(summary, minSummary, overflow);
        overflow = shrinkMissionsColumn(objective, minObjective, overflow);
        shrinkMissionsColumn(turnIn, minTurnIn, overflow);

        // Last resort: proportional scale of flexible columns only.
        int flexNow = summary.getPreferredWidth() + objective.getPreferredWidth() + turnIn.getPreferredWidth();
        if (flexNow > flexAvail && flexNow > 0) {
            double scale = flexAvail / (double) flexNow;
            summary.setPreferredWidth(Math.max(minSummary, (int) Math.floor(summary.getPreferredWidth() * scale)));
            objective.setPreferredWidth(Math.max(minObjective, (int) Math.floor(objective.getPreferredWidth() * scale)));
            turnIn.setPreferredWidth(Math.max(minTurnIn, (int) Math.floor(turnIn.getPreferredWidth() * scale)));
        }
    }

    private static int columnPreferred(TableColumnModel cm, int index) {
        return cm.getColumn(index).getPreferredWidth();
    }

    private static void clampMissionsColumn(TableColumn col, int min, int max) {
        int w = Math.max(min, Math.min(max, col.getPreferredWidth()));
        col.setMinWidth(min);
        col.setMaxWidth(max);
        col.setPreferredWidth(w);
    }

    /** @return remaining overflow after shrinking */
    private static int shrinkMissionsColumn(TableColumn col, int minWidth, int overflow) {
        if (overflow <= 0) {
            return 0;
        }
        int pref = col.getPreferredWidth();
        int canShrink = Math.max(0, pref - minWidth);
        int shrink = Math.min(canShrink, overflow);
        col.setPreferredWidth(pref - shrink);
        return overflow - shrink;
    }

    private List<MissionRecord> filterActive(List<MissionRecord> all) {
        List<MissionRecord> out = new ArrayList<>();
        for (MissionRecord r : all) {
            if (matchesFilter(r)) {
                out.add(r);
            }
        }
        return out;
    }

    private boolean matchesFilter(MissionRecord r) {
        if (r == null || !r.getCategory().isTransport()) {
            return false;
        }
        return switch (filter) {
            case ALL -> true;
            case CARGO -> r.getCategory() == MissionCategory.COMMODITY;
            case COURIER -> r.getCategory() == MissionCategory.COURIER;
            case PASSENGER -> r.getCategory() == MissionCategory.PASSENGER;
        };
    }

    private void rebuildCommodityGroups() {
        commodityGroupsPanel.removeAll();
        sourceAllBar.setVisible(false);
        if (filter != Filter.ALL && filter != Filter.CARGO) {
            commodityGroupsPanel.setVisible(false);
            return;
        }
        List<CommodityMissionGroup> groups = tracker.getCommodityGroups(MissionTracker::commodityInHold);
        if (groups.isEmpty()) {
            commodityGroupsPanel.setVisible(false);
            return;
        }
        commodityGroupsPanel.setVisible(true);
        sourceAllBar.setVisible(!MultiCommoditySourceDialog.buildNeeds(tracker.getActive()).isEmpty());
        Font base = OverlayPreferences.getUiFont();
        for (CommodityMissionGroup g : groups) {
            commodityGroupsPanel.add(buildGroupCard(g, base));
            commodityGroupsPanel.add(Box.createVerticalStrut(4));
        }
        commodityGroupsPanel.revalidate();
        commodityGroupsPanel.repaint();
    }

    private JPanel buildGroupCard(CommodityMissionGroup g, Font base) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        boolean transparent = OverlayPreferences.overlayChromeRequestsTransparency(this);
        card.setOpaque(!transparent);
        card.setBackground(transparent ? EdoUi.Internal.TRANSPARENT : EdoUi.Internal.BLACK_ALPHA_140);
        card.setBorder(new EmptyBorder(6, 8, 6, 8));
        boolean enough = g.hasEnoughGathered();
        if (isGroupReady(g) || enough) {
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(EdoUi.User.PRIMARY_HIGHLIGHT, 1),
                    new EmptyBorder(6, 8, 6, 8)));
        }
        int y = g.totalGathered();
        int x = g.getTotalRequired();
        int pct = (int) Math.round(g.progressFraction() * 100.0);

        JLabel title = new JLabel(g.getCommodityLocalised() + "  ·  " + g.getMissionCount() + " missions");
        title.setFont(base.deriveFont(Font.BOLD, OverlayPreferences.getUiFontSize()));
        title.setForeground(EdoUi.User.MAIN_TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel progress = new JLabel(y + " / " + x + " t required  ·  " + pct + "%");
        progress.setFont(base.deriveFont(Font.BOLD, OverlayPreferences.getUiFontSize()));
        progress.setForeground(enough ? EdoUi.User.PRIMARY_HIGHLIGHT : EdoUi.User.MAIN_TEXT);
        progress.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel holdDelivered = new JLabel("Hold " + g.getTotalInHold() + " t · Delivered "
                + g.getTotalDelivered() + " t");
        holdDelivered.setFont(base.deriveFont(Font.PLAIN, OverlayPreferences.getUiFontSize() - 1));
        holdDelivered.setForeground(enough ? EdoUi.User.PRIMARY_HIGHLIGHT : EdoUi.User.MAIN_TEXT);
        holdDelivered.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(title);
        card.add(progress);
        card.add(holdDelivered);
        addTurnInLines(card, g, base);
        if (g.getSoonestExpiry() != null) {
            JLabel exp = new JLabel("⏱ " + formatExpiry(g.getSoonestExpiry()));
            exp.setFont(base.deriveFont(Font.PLAIN, OverlayPreferences.getUiFontSize() - 1));
            exp.setForeground(EdoUi.User.MAIN_TEXT);
            exp.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(exp);
        }
        return card;
    }

    private void addTurnInLines(JPanel card, CommodityMissionGroup g, Font base) {
        Font turnInFont = base.deriveFont(Font.PLAIN, OverlayPreferences.getUiFontSize() - 1);
        if (g.isMultipleTurnIns()) {
            JLabel header = new JLabel("Turn-in: Multiple destinations");
            header.setFont(turnInFont);
            header.setForeground(EdoUi.User.MAIN_TEXT);
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(header);
            Set<String> seen = new LinkedHashSet<>();
            for (MissionRecord m : g.getMissions()) {
                MissionDestination dest = MissionDestinationResolver.turnInFor(m);
                String line = dest != null ? dest.displayLine() : "—";
                if (line.equals("—") || !seen.add(line)) {
                    continue;
                }
                JLabel sub = new JLabel("    " + line);
                sub.setFont(turnInFont);
                sub.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
                sub.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.add(sub);
            }
            return;
        }
        MissionDestination turnIn = g.getTurnInDest();
        JLabel turnInLine = new JLabel("Turn-in: " + (turnIn != null ? turnIn.displayLine() : "—"));
        turnInLine.setFont(turnInFont);
        turnInLine.setForeground(EdoUi.User.MAIN_TEXT);
        turnInLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(turnInLine);
    }

    private static String formatExpiry(java.time.Instant exp) {
        long sec = exp.getEpochSecond() - java.time.Instant.now().getEpochSecond();
        if (sec <= 0) {
            return "expired";
        }
        if (sec < 3600) {
            return (sec / 60) + "m";
        }
        if (sec < 86400) {
            return (sec / 3600) + "h";
        }
        return (sec / 86400) + "d";
    }

    private boolean isGroupReady(CommodityMissionGroup g) {
        if (!Boolean.TRUE.equals(dockedSupplier.get())) {
            return false;
        }
        String sys = currentSystemSupplier.get();
        String station = currentStationSupplier.get();
        MissionDestination dest = g.getTurnInDest();
        if (dest == null || g.isMultipleTurnIns()) {
            return false;
        }
        boolean atDest = stationMatches(station, dest.getStation())
                || (sys != null && sys.equalsIgnoreCase(dest.getSystem()) && dest.getStation() == null);
        if (!atDest) {
            return false;
        }
        return g.hasEnoughGathered();
    }

    private List<MissionRow> buildTableRows(List<MissionRecord> active) {
        List<MissionRow> rows = new ArrayList<>();
        for (MissionRecord r : active) {
            rows.add(new MissionRow(r));
        }
        return rows;
    }

    private void updateRedirectBanner() {
        List<MissionRecord> redirected = new ArrayList<>();
        for (MissionRecord r : tracker.getRedirectedNotDismissed()) {
            if (r != null && r.getCategory().isTransport()) {
                redirected.add(r);
            }
        }
        if (redirected.isEmpty()) {
            redirectBanner.setVisible(false);
            return;
        }
        MissionRecord first = redirected.get(0);
        MissionDestination dest = MissionDestinationResolver.turnInFor(first);
        String destLine = dest != null ? dest.displayLine() : "destination";
        String msg = redirected.size() == 1
                ? "Mission redirected → " + destLine
                : redirected.size() + " missions redirected → " + destLine;
        redirectLabel.setText("⚠ " + msg);
        redirectBanner.setVisible(true);
    }

    private boolean isRowReady(MissionRecord r) {
        if (!Boolean.TRUE.equals(dockedSupplier.get())) {
            return false;
        }
        MissionDestination turnIn = MissionDestinationResolver.turnInFor(r);
        String sys = currentSystemSupplier.get();
        String station = currentStationSupplier.get();
        boolean atDest = stationMatches(station, turnIn.getStation())
                || (sys != null && turnIn.getSystem() != null && sys.equalsIgnoreCase(turnIn.getSystem()));
        if (!atDest) {
            return false;
        }
        if (r.isCommodityMission()) {
            int req = r.getCountRequired() > 0 ? r.getCountRequired() : r.getTotalItemsToDeliver();
            if (r.getItemsDelivered() >= req && req > 0) {
                return true;
            }
            return MissionTracker.commodityInHold(r.getCommodityLocalised()) >= req && req > 0;
        }
        return r.getItemsDelivered() >= r.getTotalItemsToDeliver() && r.getTotalItemsToDeliver() > 0;
    }

    private static boolean stationMatches(String current, String missionStation) {
        if (missionStation == null || missionStation.isBlank()) {
            return false;
        }
        return current != null && current.equalsIgnoreCase(missionStation.trim());
    }

    /**
     * Chooses Places clipboard text: upper half prefers From, lower half prefers To.
     * When From is hidden (courier), always To.
     */
    static String selectPlacesCopyLine(String from, String to, boolean showsFrom, boolean preferFrom) {
        boolean toOk = to != null && !to.isBlank() && !"—".equals(to.trim());
        boolean fromOk = from != null && !from.isBlank() && !"—".equals(from.trim());
        if (!showsFrom) {
            return toOk ? to : "";
        }
        if (preferFrom) {
            if (fromOk) {
                return from;
            }
            return toOk ? to : "";
        }
        if (toOk) {
            return to;
        }
        return fromOk ? from : "";
    }

    private final class MissionRow {
        final MissionRecord record;
        final MissionCategory category;
        final String summary;
        final MissionDestination objective;
        final MissionDestination origin;
        final MissionDestination turnIn;
        final boolean ready;
        final boolean expiring;
        final boolean urgent;
        final boolean redirected;

        MissionRow(MissionRecord r) {
            record = r;
            category = r.getCategory();
            summary = r.shortSummaryLine();
            objective = MissionDestinationResolver.objectiveFor(r);
            origin = MissionDestinationResolver.originFor(r);
            turnIn = MissionDestinationResolver.turnInFor(r);
            ready = isRowReady(r);
            expiring = MissionTracker.isExpiringSoon(r);
            urgent = MissionTracker.isUrgent(r);
            redirected = r.isRedirected();
        }

        String placesTooltip() {
            String to = turnIn != null && !turnIn.isEmpty() ? turnIn.displayLine() : "—";
            if (!showsFromPlace()) {
                return "To: " + to;
            }
            String from = origin != null && !origin.isEmpty() ? origin.displayLine() : "—";
            return "From: " + from + "\nTo: " + to;
        }

        /** Courier data is already aboard at accept — From is not useful. */
        boolean showsFromPlace() {
            return category != MissionCategory.COURIER;
        }

        String placesSortKey() {
            String to = turnIn != null ? turnIn.displayLine() : "";
            if (!showsFromPlace()) {
                return to;
            }
            String from = origin != null ? origin.displayLine() : "";
            return to + "\n" + from;
        }
    }

    private final class MissionsTableModel extends AbstractTableModel {
        static final int COL_TYPE = 0;
        static final int COL_SUMMARY = 1;
        static final int COL_OBJECTIVE = 2;
        static final int COL_PLACES = 3;

        private final String[] columns = { "Type", "Summary", "Objective", "Places" };
        private List<MissionRow> rows = List.of();

        void setRows(List<MissionRow> rows) {
            this.rows = rows != null ? rows : List.of();
            fireTableDataChanged();
        }

        String objectiveCopyText(int modelRow) {
            if (modelRow < 0 || modelRow >= rows.size()) {
                return "";
            }
            return rows.get(modelRow).objective.copyLine();
        }

        String placesCopyText(int modelRow, boolean preferFrom) {
            if (modelRow < 0 || modelRow >= rows.size()) {
                return "";
            }
            MissionRow r = rows.get(modelRow);
            String to = r.turnIn != null ? r.turnIn.copyLine() : "";
            String from = r.origin != null ? r.origin.copyLine() : "";
            return selectPlacesCopyLine(from, to, r.showsFromPlace(), preferFrom);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        MissionRow rowAt(int index) {
            return index >= 0 && index < rows.size() ? rows.get(index) : null;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            MissionRow r = rows.get(rowIndex);
            return switch (columnIndex) {
                case COL_TYPE -> r.category.displayLabel();
                case COL_SUMMARY -> r.summary;
                case COL_OBJECTIVE -> r.objective.displayLine();
                case COL_PLACES -> r.placesSortKey();
                default -> "";
            };
        }
    }

    private final class MissionCellRenderer extends DefaultTableCellRenderer {
        MissionCellRenderer() {
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
                label.setHorizontalAlignment(SwingConstants.LEFT);
                label.setVerticalAlignment(SwingConstants.CENTER);
                label.setBorder(missionRowCellBorder(table, row));
            }
            applyMissionRowBackground(c, table, row);
            return c;
        }
    }

    /**
     * Two-line From/To places cell with equal-width outlined badges so labels align.
     */
    private final class PlacesCellRenderer implements javax.swing.table.TableCellRenderer {
        private final JPanel panel = new JPanel();
        private final JLabel fromBadge = new JLabel("From", SwingConstants.CENTER);
        private final JLabel toBadge = new JLabel("To", SwingConstants.CENTER);
        private final JLabel fromText = new JLabel();
        private final JLabel toText = new JLabel();
        private final JButton sourcedFromButton = new JButton("Sourced from?");
        private int badgeWidthPx = -1;

        PlacesCellRenderer() {
            panel.setLayout(new java.awt.GridBagLayout());
            panel.setOpaque(false);
            fromText.setOpaque(false);
            toText.setOpaque(false);
            fromText.setForeground(EdoUi.User.MAIN_TEXT);
            toText.setForeground(EdoUi.User.MAIN_TEXT);
            styleBadge(fromBadge);
            styleBadge(toBadge);
            java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
            gc.insets = new java.awt.Insets(1, 2, 1, 4);
            gc.anchor = java.awt.GridBagConstraints.WEST;
            gc.gridx = 0;
            gc.gridy = 0;
            gc.fill = java.awt.GridBagConstraints.NONE;
            panel.add(fromBadge, gc);
            gc.gridx = 1;
            gc.weightx = 1;
            gc.fill = java.awt.GridBagConstraints.HORIZONTAL;
            panel.add(fromText, gc);
            gc.gridx = 2;
            gc.weightx = 0;
            gc.fill = java.awt.GridBagConstraints.NONE;
            OverlayOutlineButtonStyle.applyChipHitSafe(sourcedFromButton, OverlayPreferences.getUiFont(), false);
            sourcedFromButton.setToolTipText("Set the station where this commodity will be sourced");
            panel.add(sourcedFromButton, gc);
            gc.gridx = 0;
            gc.gridy = 1;
            gc.weightx = 0;
            gc.fill = java.awt.GridBagConstraints.NONE;
            panel.add(toBadge, gc);
            gc.gridx = 1;
            gc.weightx = 1;
            gc.fill = java.awt.GridBagConstraints.HORIZONTAL;
            panel.add(toText, gc);
        }

        private void styleBadge(JLabel badge) {
            badge.setOpaque(false);
            badge.setForeground(EdoUi.User.MAIN_TEXT);
            badge.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(EdoUi.Internal.MAIN_TEXT_ALPHA_180, 1),
                    new EmptyBorder(0, 4, 0, 4)));
            badge.setHorizontalAlignment(SwingConstants.CENTER);
        }

        private void ensureEqualBadgeWidth(Font font) {
            FontMetrics fm = panel.getFontMetrics(font != null ? font : OverlayPreferences.getUiFont());
            int w = Math.max(fm.stringWidth("From"), fm.stringWidth("To")) + 12;
            if (w == badgeWidthPx) {
                return;
            }
            badgeWidthPx = w;
            Dimension size = new Dimension(w, Math.max(16, fm.getHeight() + 2));
            fromBadge.setPreferredSize(size);
            fromBadge.setMinimumSize(size);
            fromBadge.setMaximumSize(size);
            toBadge.setPreferredSize(size);
            toBadge.setMinimumSize(size);
            toBadge.setMaximumSize(size);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Font base = table != null ? table.getFont() : OverlayPreferences.getUiFont();
            fromBadge.setFont(base.deriveFont(Font.BOLD, Math.max(9f, base.getSize2D() - 1f)));
            toBadge.setFont(fromBadge.getFont());
            fromText.setFont(base);
            toText.setFont(base);
            ensureEqualBadgeWidth(fromBadge.getFont());

            String from = "—";
            String to = "—";
            boolean showFrom = true;
            boolean showSourcedAction = false;
            if (row >= 0 && table != null) {
                int modelRow = table.convertRowIndexToModel(row);
                MissionRow mr = tableModel.rowAt(modelRow);
                if (mr != null) {
                    showFrom = mr.showsFromPlace();
                    showSourcedAction = mr.record.isSelfSourcedCommodityMission();
                    if (mr.origin != null && !mr.origin.isEmpty()) {
                        from = mr.origin.displayLine();
                    }
                    if (mr.turnIn != null && !mr.turnIn.isEmpty()) {
                        to = mr.turnIn.displayLine();
                    }
                }
            }
            fromBadge.setVisible(showFrom);
            fromText.setVisible(showFrom);
            sourcedFromButton.setVisible(showFrom && showSourcedAction);
            fromText.setText(from);
            toText.setText(to);
            panel.setBorder(missionRowCellBorder(table, row));
            applyMissionRowBackground(panel, table, row);
            return panel;
        }
    }

    /** Thin orange line between jobs (not after the last row). */
    private static javax.swing.border.Border missionRowCellBorder(JTable table, int viewRow) {
        EmptyBorder pad = new EmptyBorder(3, 4, 3, 4);
        if (table != null && viewRow >= 0 && viewRow < table.getRowCount() - 1) {
            return new CompoundBorder(new MatteBorder(0, 0, 1, 0, EdoUi.ED_ORANGE_TRANS), pad);
        }
        return pad;
    }

    private void applyMissionRowBackground(Component c, JTable table, int row) {
        Color bg = EdoUi.Internal.TRANSPARENT;
        if (row >= 0 && table != null && row < tableModel.getRowCount()) {
            MissionRow mr = tableModel.rowAt(table.convertRowIndexToModel(row));
            if (mr != null) {
                if (mr.ready) {
                    bg = new Color(40, 80, 50, 100);
                } else if (mr.urgent) {
                    bg = new Color(120, 40, 40, 100);
                } else if (mr.expiring) {
                    bg = EdoUi.ED_ORANGE_TRANS;
                } else if (mr.redirected) {
                    bg = EdoUi.ED_ORANGE_LESS_TRANS;
                }
            }
        }
        c.setBackground(bg);
        if (c instanceof JLabel label) {
            label.setOpaque(bg.getAlpha() > 0);
        } else if (c instanceof JPanel p) {
            p.setOpaque(bg.getAlpha() > 0);
        }
    }

    private static final class MissionsHeaderRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, false, false, row, column);
            boolean transparent = OverlayPreferences.overlayChromeRequestsTransparency(this);
            label.setOpaque(!transparent);
            label.setBackground(transparent ? EdoUi.Internal.TRANSPARENT : EdoUi.User.BACKGROUND);
            label.setForeground(EdoUi.Internal.tableHeaderForeground());
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            label.setHorizontalAlignment(SwingConstants.LEFT);
            label.setBorder(transparent
                    ? new EmptyBorder(2, 4, 0, 4)
                    : new CompoundBorder(
                            new MatteBorder(2, 0, 0, 0, EdoUi.Internal.tableHeaderTopBorder()),
                            new EmptyBorder(0, 4, 0, 4)));
            return label;
        }

        @Override
        protected void paintComponent(Graphics g) {
            boolean transparent = OverlayPreferences.overlayChromeRequestsTransparency(this);
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
                // Avoid LAF/LCD fill on transparent chrome — paint ink only (same as Mining headers).
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
            int lineY = getHeight() - 1;
            g2.drawLine(0, lineY, getWidth(), lineY);
            g2.dispose();
        }
    }
}
