package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;

import org.dce.ed.cache.CachedSystem;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.edsm.SystemResponse;
import org.dce.ed.edsm.UtilTable;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.BountyEvent;
import org.dce.ed.logreader.event.CargoDepotEvent;
import org.dce.ed.logreader.event.MissionAbandonedEvent;
import org.dce.ed.logreader.event.MissionAcceptedEvent;
import org.dce.ed.logreader.event.MissionCompletedEvent;
import org.dce.ed.logreader.event.MissionFailedEvent;
import org.dce.ed.logreader.event.MissionRedirectedEvent;
import org.dce.ed.logreader.event.MissionsEvent;
import org.dce.ed.mission.CommodityMissionGroup;
import org.dce.ed.mission.MissionCategory;
import org.dce.ed.mission.MissionDestination;
import org.dce.ed.mission.MissionDestinationResolver;
import org.dce.ed.mission.MissionRecord;
import org.dce.ed.mission.MissionSpeechTracker;
import org.dce.ed.mission.MissionTracker;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.ui.DestinationCopySupport;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.HoverClickPoller;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.TableHeaderSortSupport;
import org.dce.ed.ui.TransparentTableHeader;
import org.dce.ed.ui.TransparentTableHeaderUI;
import org.dce.ed.ui.TransparentViewportUI;
import org.dce.ed.util.EdsmClient;

/**
 * Missions tab: commodity group cards, active missions table, filters, destination copy.
 */
public class MissionsTabPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private enum Filter { ALL, COMMODITY, COMBAT, OTHER }

    private final MissionTracker tracker = new MissionTracker();
    private final BooleanSupplier passThroughEnabledSupplier;
    private final Supplier<Boolean> dockedSupplier;
    private final Supplier<String> currentSystemSupplier;
    private final Supplier<String> currentStationSupplier;
    private final Supplier<double[]> currentStarPosSupplier;
    private final EdsmClient edsmClient = new EdsmClient();
    private final Map<String, double[]> resolvedCoordsCache = new ConcurrentHashMap<>();
    private final Set<String> coordsFetchInProgress = ConcurrentHashMap.newKeySet();

    private Runnable sessionStateChangeCallback;
    private Filter filter = Filter.ALL;

    private static final int FILTER_HOVER_DELAY_MS = 500;
    private static final int HEADER_SORT_HOVER_MS = 500;

    private final JLabel activeCountLabel = new JLabel("Active: 0");
    private final JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    private final List<JButton> filterButtons = new ArrayList<>();
    private final List<Filter> filterButtonFilters = new ArrayList<>();
    private final JPanel redirectBanner = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
    private final JLabel redirectLabel = new JLabel();
    private final JButton redirectDismiss = new JButton("Dismiss");
    private final JPanel commodityGroupsPanel = new JPanel();
    private final MissionsTableModel tableModel = new MissionsTableModel();
    private final JTable missionsTable = new JTable(tableModel);
    private final TableRowSorter<MissionsTableModel> missionsSorter = new TableRowSorter<>(tableModel);
    /** Created after the custom header is installed (see constructor). */
    private JScrollPane tableScroll;
    private final Timer refreshTimer;

    public MissionsTabPanel(BooleanSupplier passThroughEnabledSupplier,
            Supplier<Boolean> dockedSupplier,
            Supplier<String> currentSystemSupplier,
            Supplier<String> currentStationSupplier,
            Supplier<double[]> currentStarPosSupplier) {
        super(new BorderLayout());
        this.passThroughEnabledSupplier = passThroughEnabledSupplier;
        this.dockedSupplier = dockedSupplier;
        this.currentSystemSupplier = currentSystemSupplier;
        this.currentStationSupplier = currentStationSupplier;
        this.currentStarPosSupplier = currentStarPosSupplier;
        tracker.setCurrentSystemSupplier(currentSystemSupplier);

        setOpaque(false);
        setBackground(EdoUi.User.BACKGROUND);

        Font base = OverlayPreferences.getUiFont();
        activeCountLabel.setFont(base.deriveFont(Font.BOLD, OverlayPreferences.getUiFontSize()));
        activeCountLabel.setForeground(EdoUi.User.MAIN_TEXT);

        buildFilterBar(base);
        buildRedirectBanner(base);

        commodityGroupsPanel.setLayout(new BoxLayout(commodityGroupsPanel, BoxLayout.Y_AXIS));
        commodityGroupsPanel.setOpaque(false);

        configureMissionsTable(base);

        DefaultTableCellRenderer renderer = new MissionCellRenderer();
        missionsTable.setDefaultRenderer(Object.class, renderer);

        Comparator<String> textCmp = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
        missionsSorter.setComparator(MissionsTableModel.COL_TYPE, textCmp);
        missionsSorter.setComparator(MissionsTableModel.COL_SUMMARY, textCmp);
        missionsSorter.setComparator(MissionsTableModel.COL_OBJECTIVE, textCmp);
        missionsSorter.setComparator(MissionsTableModel.COL_TURNIN, textCmp);
        missionsSorter.setComparator(MissionsTableModel.COL_DISTANCE, Comparator.nullsLast(Comparator.naturalOrder()));
        missionsSorter.setComparator(MissionsTableModel.COL_EXPIRY, Comparator.nullsLast(Comparator.naturalOrder()));
        missionsTable.setAutoCreateRowSorter(false);
        missionsTable.setRowSorter(missionsSorter);
        TableHeaderSortSupport.install(missionsTable, passThroughEnabledSupplier, HEADER_SORT_HOVER_MS);

        DestinationCopySupport.install(
                missionsTable,
                MissionsTableModel.COL_OBJECTIVE,
                MissionsTableModel.COL_TURNIN,
                tableModel::objectiveCopyText,
                tableModel::turnInCopyText,
                passThroughEnabledSupplier);

        // Create scroll pane after the custom header is installed so the column header
        // viewport binds to TransparentTableHeader (not the default LAF header).
        tableScroll = new JScrollPane(missionsTable);
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);
        center.add(commodityGroupsPanel);
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
        center.add(tableScroll);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(4, 4, 0, 4));
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.add(filterBar, BorderLayout.WEST);
        topRow.add(activeCountLabel, BorderLayout.EAST);
        top.add(topRow, BorderLayout.NORTH);
        top.add(redirectBanner, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);

        tracker.setChangeCallback(this::scheduleRefresh);
        CargoMonitor.getInstance().addListener(s -> scheduleRefresh());

        refreshTimer = new Timer(30_000, e -> refreshUi());
        refreshTimer.setRepeats(true);
        refreshTimer.start();

        refreshUi();
    }

    private void buildFilterBar(Font base) {
        filterBar.setOpaque(false);
        addFilterChip("All", Filter.ALL, base);
        addFilterChip("Commodity", Filter.COMMODITY, base);
        addFilterChip("Combat", Filter.COMBAT, base);
        addFilterChip("Other", Filter.OTHER, base);
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
        int rowH = Math.max(22, OverlayPreferences.getUiFontSize() + 10);
        missionsTable.setRowHeight(rowH);
        missionsTable.setFont(base.deriveFont(Font.PLAIN, OverlayPreferences.getUiFontSize()));
        missionsTable.setFillsViewportHeight(false);
        missionsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        missionsTable.setTableHeader(new TransparentTableHeader(missionsTable.getColumnModel()));
        JTableHeader th = missionsTable.getTableHeader();
        if (th != null) {
            th.setUI(TransparentTableHeaderUI.createUI(th));
            th.setOpaque(!OverlayPreferences.overlayChromeRequestsTransparency());
            th.setForeground(EdoUi.User.MAIN_TEXT);
            th.setBackground(OverlayPreferences.overlayChromeRequestsTransparency()
                    ? EdoUi.Internal.TRANSPARENT : EdoUi.User.BACKGROUND);
            th.setFont(base.deriveFont(Font.BOLD, OverlayPreferences.getUiFontSize()));
            th.setBorder(null);
            th.setReorderingAllowed(false);
            th.setFocusable(false);
            th.putClientProperty("JTableHeader.focusCellBackground", null);
            th.putClientProperty("JTableHeader.cellBorder", null);
            th.setDefaultRenderer(new MissionsHeaderRenderer());
            th.setPreferredSize(new Dimension(Math.max(1, th.getPreferredSize().width), rowH));
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
        boolean transparent = OverlayPreferences.overlayChromeRequestsTransparency();
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

    public void fillSessionState(EdoSessionState state) {
        tracker.fillSessionState(state);
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
            // Current system moved — recompute Dist column.
            scheduleRefresh();
        }
        if (event instanceof MissionAcceptedEvent
                || event instanceof MissionCompletedEvent
                || event instanceof MissionFailedEvent
                || event instanceof MissionAbandonedEvent
                || event instanceof MissionRedirectedEvent
                || event instanceof CargoDepotEvent
                || event instanceof MissionsEvent
                || event instanceof BountyEvent) {
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
            // Measure content widths first, then fit all columns into the viewport
            // so nothing is clipped off-screen in the overlay.
            missionsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            UtilTable.autoSizeTableColumns(missionsTable);
            fitMissionsColumnsToViewport();
            missionsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            missionsTable.revalidate();
            missionsTable.repaint();
            if (missionsTable.getTableHeader() != null) {
                missionsTable.getTableHeader().repaint();
            }
        });
    }

    /**
     * Shrinks flexible text columns when UtilTable preferred widths exceed the viewport.
     * Keeps Type / Dist / Exp closer to their content size.
     */
    private void fitMissionsColumnsToViewport() {
        int avail = tableScroll.getViewport() != null ? tableScroll.getViewport().getWidth() : 0;
        if (avail <= 0) {
            avail = missionsTable.getWidth();
        }
        if (avail <= 0) {
            return;
        }
        var cm = missionsTable.getColumnModel();
        int cols = cm.getColumnCount();
        if (cols == 0) {
            return;
        }
        int total = 0;
        for (int i = 0; i < cols; i++) {
            total += cm.getColumn(i).getPreferredWidth();
        }
        if (total <= avail) {
            // Give leftover space to Summary so the table fills the pane cleanly.
            if (cols > MissionsTableModel.COL_SUMMARY) {
                var summary = cm.getColumn(MissionsTableModel.COL_SUMMARY);
                summary.setPreferredWidth(summary.getPreferredWidth() + (avail - total));
            }
            return;
        }
        int overflow = total - avail;
        int[] flexible = {
                MissionsTableModel.COL_SUMMARY,
                MissionsTableModel.COL_OBJECTIVE,
                MissionsTableModel.COL_TURNIN
        };
        int flexTotal = 0;
        for (int c : flexible) {
            if (c < cols) {
                flexTotal += cm.getColumn(c).getPreferredWidth();
            }
        }
        if (flexTotal <= 0) {
            return;
        }
        int minFlex = 52;
        for (int c : flexible) {
            if (c >= cols) {
                continue;
            }
            var col = cm.getColumn(c);
            int pref = col.getPreferredWidth();
            int shrink = (int) Math.round(overflow * (pref / (double) flexTotal));
            col.setPreferredWidth(Math.max(minFlex, pref - shrink));
        }
        // If still over (rare: many narrow mins), scale every column.
        total = 0;
        for (int i = 0; i < cols; i++) {
            total += cm.getColumn(i).getPreferredWidth();
        }
        if (total > avail) {
            double scale = avail / (double) total;
            for (int i = 0; i < cols; i++) {
                var col = cm.getColumn(i);
                col.setPreferredWidth(Math.max(40, (int) Math.floor(col.getPreferredWidth() * scale)));
            }
        }
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
        return switch (filter) {
            case ALL -> true;
            case COMMODITY -> r.getCategory() == MissionCategory.COMMODITY;
            case COMBAT -> r.getCategory() == MissionCategory.COMBAT;
            case OTHER -> r.getCategory() != MissionCategory.COMMODITY
                    && r.getCategory() != MissionCategory.COMBAT;
        };
    }

    private void rebuildCommodityGroups() {
        commodityGroupsPanel.removeAll();
        if (filter != Filter.ALL && filter != Filter.COMMODITY) {
            commodityGroupsPanel.setVisible(false);
            return;
        }
        List<CommodityMissionGroup> groups = tracker.getCommodityGroups(MissionTracker::commodityInHold);
        if (groups.isEmpty()) {
            commodityGroupsPanel.setVisible(false);
            return;
        }
        commodityGroupsPanel.setVisible(true);
        Font base = OverlayPreferences.getUiFont();
        for (CommodityMissionGroup g : groups) {
            commodityGroupsPanel.add(buildGroupCard(g, base));
            commodityGroupsPanel.add(Box.createVerticalStrut(4));
        }
        commodityGroupsPanel.revalidate();
        commodityGroupsPanel.repaint();
    }

    private static final Color PROGRESS_COMPLETE_GREEN = new Color(100, 220, 130);

    private JPanel buildGroupCard(CommodityMissionGroup g, Font base) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        boolean transparent = OverlayPreferences.overlayChromeRequestsTransparency();
        card.setOpaque(!transparent);
        card.setBackground(transparent ? EdoUi.Internal.TRANSPARENT : EdoUi.Internal.BLACK_ALPHA_140);
        card.setBorder(new EmptyBorder(6, 8, 6, 8));
        boolean enough = g.hasEnoughGathered();
        if (isGroupReady(g) || enough) {
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(PROGRESS_COMPLETE_GREEN, 1),
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
        progress.setForeground(enough ? PROGRESS_COMPLETE_GREEN : EdoUi.User.MAIN_TEXT);
        progress.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel holdDelivered = new JLabel("Hold " + g.getTotalInHold() + " t · Delivered "
                + g.getTotalDelivered() + " t");
        holdDelivered.setFont(base.deriveFont(Font.PLAIN, OverlayPreferences.getUiFontSize() - 1));
        holdDelivered.setForeground(enough ? PROGRESS_COMPLETE_GREEN : EdoUi.User.MAIN_TEXT);
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
        List<MissionRecord> redirected = tracker.getRedirectedNotDismissed();
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

    private final class MissionRow {
        final MissionRecord record;
        final MissionCategory category;
        final String summary;
        final MissionDestination objective;
        final MissionDestination turnIn;
        final DistanceDisplay distance;
        final ExpiryDisplay expiry;
        final boolean ready;
        final boolean expiring;
        final boolean urgent;
        final boolean redirected;

        MissionRow(MissionRecord r) {
            record = r;
            category = r.getCategory();
            summary = r.shortSummaryLine();
            objective = MissionDestinationResolver.objectiveFor(r);
            turnIn = MissionDestinationResolver.turnInFor(r);
            distance = distanceFor(r, turnIn);
            expiry = ExpiryDisplay.from(r);
            ready = isRowReady(r);
            expiring = MissionTracker.isExpiringSoon(r);
            urgent = MissionTracker.isUrgent(r);
            redirected = r.isRedirected();
        }
    }

    private DistanceDisplay distanceFor(MissionRecord r, MissionDestination turnIn) {
        String destSystem = turnIn != null ? turnIn.getSystem() : null;
        if (destSystem == null || destSystem.isBlank()) {
            destSystem = r != null ? r.getDestinationSystem() : null;
        }
        if (destSystem == null || destSystem.isBlank()) {
            return DistanceDisplay.unknown();
        }
        String current = currentSystemSupplier != null ? currentSystemSupplier.get() : null;
        if (current != null && current.equalsIgnoreCase(destSystem.trim())) {
            return DistanceDisplay.of(0.0);
        }
        double[] from = resolveCurrentStarPos(current);
        double[] to = resolveSystemCoords(destSystem.trim());
        if (from == null || to == null) {
            return DistanceDisplay.unknown();
        }
        return DistanceDisplay.of(distanceLy(from, to));
    }

    private double[] resolveCurrentStarPos(String currentSystemName) {
        if (currentStarPosSupplier != null) {
            double[] pos = currentStarPosSupplier.get();
            if (isValidStarPos(pos)) {
                return pos;
            }
        }
        if (currentSystemName != null && !currentSystemName.isBlank()) {
            return resolveSystemCoords(currentSystemName.trim());
        }
        return null;
    }

    private double[] resolveSystemCoords(String systemName) {
        if (systemName == null || systemName.isBlank()) {
            return null;
        }
        double[] cached = resolvedCoordsCache.get(systemName);
        if (isValidStarPos(cached)) {
            return cached;
        }
        try {
            CachedSystem cs = SystemCache.getInstance().get(0L, systemName);
            if (cs != null && isValidStarPos(cs.starPos)) {
                double[] copy = cs.starPos.clone();
                resolvedCoordsCache.put(systemName, copy);
                return copy;
            }
        } catch (Exception ignored) {
        }
        scheduleCoordsFetch(systemName);
        return null;
    }

    private void scheduleCoordsFetch(String systemName) {
        if (systemName == null || systemName.isBlank()) {
            return;
        }
        if (!coordsFetchInProgress.add(systemName)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                SystemResponse sys = edsmClient.getSystem(systemName);
                if (sys != null && sys.coords != null) {
                    double[] coords = new double[] { sys.coords.x, sys.coords.y, sys.coords.z };
                    resolvedCoordsCache.put(systemName, coords);
                    SwingUtilities.invokeLater(this::refreshUi);
                }
            } catch (Exception ignored) {
            } finally {
                coordsFetchInProgress.remove(systemName);
            }
        }, "MissionDistCoords-" + systemName);
        t.setDaemon(true);
        t.start();
    }

    private static boolean isValidStarPos(double[] pos) {
        return pos != null && pos.length >= 3
                && Double.isFinite(pos[0]) && Double.isFinite(pos[1]) && Double.isFinite(pos[2]);
    }

    private static double distanceLy(double[] from, double[] to) {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double dz = to[2] - from[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Formatted Ly distance that sorts numerically (unknown last). */
    private static final class DistanceDisplay implements Comparable<DistanceDisplay> {
        final String label;
        final double ly;

        DistanceDisplay(String label, double ly) {
            this.label = label != null ? label : "—";
            this.ly = ly;
        }

        static DistanceDisplay of(double ly) {
            if (!Double.isFinite(ly)) {
                return unknown();
            }
            return new DistanceDisplay(String.format(Locale.US, "%.2f Ly", ly), ly);
        }

        static DistanceDisplay unknown() {
            return new DistanceDisplay("—", Double.POSITIVE_INFINITY);
        }

        @Override
        public int compareTo(DistanceDisplay o) {
            return Double.compare(ly, o.ly);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Formatted expiry label that sorts by remaining seconds. */
    private static final class ExpiryDisplay implements Comparable<ExpiryDisplay> {
        final String label;
        final long remainingSec;

        ExpiryDisplay(String label, long remainingSec) {
            this.label = label != null ? label : "—";
            this.remainingSec = remainingSec;
        }

        static ExpiryDisplay from(MissionRecord r) {
            String label = MissionTracker.formatExpiryRemaining(r);
            java.time.Instant exp = MissionTracker.expiryInstant(r);
            if (exp == null) {
                return new ExpiryDisplay(label, Long.MAX_VALUE);
            }
            long sec = exp.getEpochSecond() - java.time.Instant.now().getEpochSecond();
            if (sec <= 0) {
                return new ExpiryDisplay(label, -1L);
            }
            return new ExpiryDisplay(label, sec);
        }

        @Override
        public int compareTo(ExpiryDisplay o) {
            return Long.compare(remainingSec, o.remainingSec);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final class MissionsTableModel extends AbstractTableModel {
        static final int COL_TYPE = 0;
        static final int COL_SUMMARY = 1;
        static final int COL_OBJECTIVE = 2;
        static final int COL_TURNIN = 3;
        static final int COL_DISTANCE = 4;
        static final int COL_EXPIRY = 5;

        private final String[] columns = { "Type", "Summary", "Objective", "Turn-in", "Dist", "Exp" };
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

        String turnInCopyText(int modelRow) {
            if (modelRow < 0 || modelRow >= rows.size()) {
                return "";
            }
            return rows.get(modelRow).turnIn.copyLine();
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
                case COL_TURNIN -> r.turnIn.displayLine();
                case COL_DISTANCE -> r.distance;
                case COL_EXPIRY -> r.expiry;
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
                label.setBorder(new EmptyBorder(3, 4, 3, 4));
                int modelCol = table.convertColumnIndexToModel(column);
                label.setHorizontalAlignment(modelCol == MissionsTableModel.COL_DISTANCE
                        || modelCol == MissionsTableModel.COL_EXPIRY
                        ? SwingConstants.RIGHT
                        : SwingConstants.LEFT);
            }
            Color bg = EdoUi.Internal.TRANSPARENT;
            if (row >= 0 && row < tableModel.getRowCount()) {
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
            }
            return c;
        }
    }

    private static final class MissionsHeaderRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, false, false, row, column);
            boolean transparent = OverlayPreferences.overlayChromeRequestsTransparency();
            label.setOpaque(!transparent);
            label.setBackground(transparent ? EdoUi.Internal.TRANSPARENT : EdoUi.User.BACKGROUND);
            label.setForeground(EdoUi.Internal.tableHeaderForeground());
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            int modelCol = table != null ? table.convertColumnIndexToModel(column) : column;
            label.setHorizontalAlignment(modelCol == MissionsTableModel.COL_DISTANCE
                    || modelCol == MissionsTableModel.COL_EXPIRY
                    ? SwingConstants.RIGHT
                    : SwingConstants.LEFT);
            label.setBorder(transparent
                    ? new EmptyBorder(2, 4, 0, 4)
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
