package org.dce.ed.ui;

import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Component;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Icon;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import org.dce.ed.OverlayPreferences;
import org.dce.ed.CargoMonitor;
import org.dce.ed.mission.TransportPlanAction;
import org.dce.ed.mission.TransportLocation;
import org.dce.ed.mission.TransportPlanProblem;
import org.dce.ed.mission.TransportPlanStop;
import org.dce.ed.mission.TransportRoutePlan;

import com.google.gson.JsonObject;

/** Embedded preview and route handoff for an optimized Transport schedule. */
public final class TransportRoutePlanPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JButton applyButton = new JButton("Apply to Route");
    private final TransportRoutePlan plan;
    private final TransportLocation start;
    private final JTable table;
    private int highlightedRow = -1;
    private int reachedPlanStop = -1;
    private final Set<Integer> cargoCompletedStops = new HashSet<>();
    private final Set<ActionKey> completedActions = new HashSet<>();

    public TransportRoutePlanPanel(TransportRoutePlan plan, int capacity,
            Consumer<List<String>> onApply) {
        this(plan, capacity, onApply, List.of());
    }

    public TransportRoutePlanPanel(TransportRoutePlan plan, int capacity,
            Consumer<List<String>> onApply, List<TransportPlanProblem> warnings) {
        this(plan, capacity, null, 0, onApply, warnings);
    }

    public TransportRoutePlanPanel(TransportRoutePlan plan, int capacity,
            TransportLocation start, int initialHoldTons,
            Consumer<List<String>> onApply, List<TransportPlanProblem> warnings) {
        super(new BorderLayout(8, 8));
        this.plan = plan;
        this.start = start;
        setOpaque(false);
        DefaultTableModel model = new DefaultTableModel(
                new String[] { "", "#", "System", "Station", "Action", "Hold" }, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        if (start != null) {
            model.addRow(new Object[] { false, 0, start.system(), start.station(), "",
                    initialHoldTons + " / " + capacity + " t" });
            highlightedRow = 0;
        }
        int index = 1;
        for (TransportPlanStop stop : plan.stops()) {
            model.addRow(new Object[] { false, index++, stop.location().system(), stop.location().station(),
                    actionsText(stop.actions()), stop.holdAfterTons() + " / " + capacity + " t" });
        }
        table = new JTable(model) {
            private static final long serialVersionUID = 1L;

            @Override public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component component = super.prepareRenderer(renderer, row, column);
                if (row == highlightedRow && component instanceof JComponent cell) {
                    int left = column == 0 ? 2 : 0;
                    int right = column == getColumnCount() - 1 ? 2 : 0;
                    cell.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(2, left, 2, right,
                                    EdoUi.User.PRIMARY_HIGHLIGHT),
                            cell.getBorder()));
                }
                return component;
            }
        };
        table.getColumnModel().getColumn(0).setCellRenderer(new CompletionRenderer());
        table.getColumnModel().getColumn(1).setCellRenderer(new RouteNumberRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(new ActionStatusRenderer());
        CommoditySourceDialog.configureResultsTable(table, OverlayPreferences.getUiFont());
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(false);
        table.setCellSelectionEnabled(false);
        table.setFocusable(false);
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() != 2 || event.getButton() != MouseEvent.BUTTON1) return;
                int row = table.rowAtPoint(event.getPoint());
                int column = table.columnAtPoint(event.getPoint());
                if (row < 0 || column != 2) return;
                String system = systemForTableRow(row);
                if (system == null || system.isBlank()) return;
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(system), null);
                SystemTableHoverCopyManager.showCopiedToast(table, system);
            }
        });
        table.getColumnModel().getColumn(0).setMinWidth(22);
        table.getColumnModel().getColumn(0).setPreferredWidth(22);
        table.getColumnModel().getColumn(0).setMaxWidth(22);
        table.getColumnModel().getColumn(1).setMinWidth(30);
        table.getColumnModel().getColumn(1).setPreferredWidth(34);
        table.getColumnModel().getColumn(1).setMaxWidth(48);
        int baseRowHeight = table.getRowHeight();
        for (int row = 0; row < table.getRowCount(); row++) {
            String actions = String.valueOf(table.getValueAt(row, 4));
            int lines = actions.isEmpty() ? 1 : actions.split("\\R", -1).length;
            table.setRowHeight(row, baseRowHeight * lines);
        }
        JLabel summary = new JLabel((plan.optimal() ? "Optimal route" : "Best route found")
                + "  ·  " + String.format("%.2f Ly", plan.totalDistanceLy())
                + "  ·  " + plan.stops().size() + " station visits");
        OverlayOutlineButtonStyle.applyPrimaryHitSafe(applyButton, OverlayPreferences.getUiFont());
        applyButton.addActionListener(e -> onApply.accept(routeSystems(plan)));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        actions.add(applyButton);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(summary, BorderLayout.CENTER);
        south.add(actions, BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        if (warnings != null && !warnings.isEmpty()) {
            JPanel warningPanel = new JPanel();
            warningPanel.setLayout(new BoxLayout(warningPanel, BoxLayout.Y_AXIS));
            warningPanel.setOpaque(false);
            for (TransportPlanProblem warning : warnings) {
                JLabel label = new JLabel("⚠ " + warning.message());
                label.setForeground(EdoUi.User.WARNING);
                warningPanel.add(label);
            }
            add(warningPanel, BorderLayout.NORTH);
        }
        add(scroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    /** Advances the plan-row border in schedule order as the live location reaches each stop. */
    public void updateCurrentLocation(String system, String station) {
        int rowOffset = start == null ? 0 : 1;
        int next = reachedPlanStop + 1;
        if (next < plan.stops().size()) {
            TransportLocation nextLocation = plan.stops().get(next).location();
            boolean nextSystem = sameLocationText(system, nextLocation.system());
            boolean nextStation = sameLocationText(station, nextLocation.station());
            if (nextSystem && (reachedPlanStop < 0 || nextStation
                    || !sameLocationText(system, currentReachedSystem()))) {
                reachedPlanStop = next;
                highlightedRow = rowOffset + reachedPlanStop;
                refreshCompletionMarkers();
                table.repaint();
                return;
            }
        }
        if (reachedPlanStop >= 0 && sameLocationText(system, currentReachedSystem())) {
            highlightedRow = rowOffset + reachedPlanStop;
        } else if (reachedPlanStop < 0 && start != null
                && sameLocationText(system, start.system())) {
            highlightedRow = 0;
        } else {
            highlightedRow = -1;
        }
        table.repaint();
    }

    /** Restores monotonic progress so repeated systems remain unambiguous after restart. */
    public void restoreReachedPlanStop(int stopIndex) {
        reachedPlanStop = Math.max(-1, Math.min(stopIndex, plan.stops().size() - 1));
        highlightedRow = reachedPlanStop >= 0
                ? (start == null ? 0 : 1) + reachedPlanStop
                : (start != null ? 0 : -1);
        refreshCompletionMarkers();
        table.repaint();
    }

    public int reachedPlanStop() {
        return reachedPlanStop;
    }

    /** Checks the current depot pickup as soon as its mission cargo is aboard. */
    public void updateCurrentCargo(JsonObject cargo) {
        if (cargo == null || reachedPlanStop < 0 || reachedPlanStop >= plan.stops().size()) return;
        TransportPlanStop stop = plan.stops().get(reachedPlanStop);
        if (stop.actions().stream().noneMatch(a -> a.kind() == TransportPlanAction.Kind.PICK_UP)) return;
        Map<Long, Integer> requiredByMission = new LinkedHashMap<>();
        for (TransportPlanAction action : stop.actions()) {
            requiredByMission.merge(action.missionId(), action.tons(), Integer::sum);
        }
        for (Map.Entry<Long, Integer> entry : requiredByMission.entrySet()) {
            if (CargoMonitor.countMissionCargoTons(cargo, entry.getKey()) >= entry.getValue()) {
                for (TransportPlanAction action : stop.actions()) {
                    if (action.kind() == TransportPlanAction.Kind.PICK_UP
                            && action.missionId() == entry.getKey()) {
                        completedActions.add(new ActionKey(reachedPlanStop, action.kind(), action.missionId()));
                    }
                }
            }
        }
        Map<String, List<TransportPlanAction>> pickupsByCommodity = new LinkedHashMap<>();
        for (TransportPlanAction action : stop.actions()) {
            if (action.kind() == TransportPlanAction.Kind.PICK_UP) {
                pickupsByCommodity.computeIfAbsent(
                        action.commodity().trim().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                        .add(action);
            }
        }
        for (List<TransportPlanAction> commodityActions : pickupsByCommodity.values()) {
            int required = commodityActions.stream().mapToInt(TransportPlanAction::tons).sum();
            String commodity = commodityActions.get(0).commodity();
            if (CargoMonitor.countCommodityTons(cargo, commodity) >= required) {
                for (TransportPlanAction action : commodityActions) {
                    completedActions.add(new ActionKey(reachedPlanStop, action.kind(), action.missionId()));
                }
            }
        }
        boolean complete = stop.actions().stream().allMatch(action -> completedActions.contains(
                new ActionKey(reachedPlanStop, action.kind(), action.missionId())));
        if (complete) {
            cargoCompletedStops.add(reachedPlanStop);
        }
        refreshCompletionMarkers();
        table.repaint();
    }

    /** Checks the current collect/deliver action from its CargoDepot transaction. */
    public void updateCargoDepotProgress(long missionId, String updateType, int count) {
        if (reachedPlanStop < 0 || reachedPlanStop >= plan.stops().size() || count <= 0) return;
        TransportPlanAction.Kind kind = "Collect".equalsIgnoreCase(updateType)
                ? TransportPlanAction.Kind.PICK_UP
                : "Deliver".equalsIgnoreCase(updateType) ? TransportPlanAction.Kind.DELIVER : null;
        if (kind == null) return;
        for (TransportPlanAction action : plan.stops().get(reachedPlanStop).actions()) {
            if (action.kind() == kind && action.missionId() == missionId && count >= action.tons()) {
                completedActions.add(new ActionKey(reachedPlanStop, kind, missionId));
            }
        }
        TransportPlanStop stop = plan.stops().get(reachedPlanStop);
        if (!stop.actions().isEmpty() && stop.actions().stream().allMatch(action ->
                completedActions.contains(new ActionKey(
                        reachedPlanStop, action.kind(), action.missionId())))) {
            cargoCompletedStops.add(reachedPlanStop);
        }
        refreshCompletionMarkers();
        table.repaint();
    }

    /** Marks courier, passenger, or donation work complete when Elite completes the mission. */
    public void updateMissionCompleted(long missionId) {
        if (reachedPlanStop < 0 || reachedPlanStop >= plan.stops().size()) return;
        for (TransportPlanAction action : plan.stops().get(reachedPlanStop).actions()) {
            if (action.missionId() == missionId) {
                completedActions.add(new ActionKey(reachedPlanStop, action.kind(), missionId));
            }
        }
        markCurrentStopCompleteIfNeeded();
        refreshCompletionMarkers();
        table.repaint();
    }

    /** Returns the prerecorded reminder appropriate for unfinished work at the current plan stop. */
    public Optional<String> departureReminderAt(String system, String station) {
        if (reachedPlanStop < 0 || reachedPlanStop >= plan.stops().size()) return Optional.empty();
        TransportPlanStop stop = plan.stops().get(reachedPlanStop);
        if (!sameLocationText(system, stop.location().system())
                || !sameLocationText(station, stop.location().station())) return Optional.empty();
        Set<Long> deliveries = new HashSet<>();
        boolean donations = false;
        for (TransportPlanAction action : stop.actions()) {
            if (completedActions.contains(new ActionKey(reachedPlanStop, action.kind(), action.missionId()))) continue;
            if (isDonation(action)) donations = true;
            else deliveries.add(action.missionId());
        }
        if (deliveries.isEmpty() && !donations) return Optional.empty();
        String work = deliveries.isEmpty() ? "donations"
                : deliveries.size() == 1 ? (donations ? "delivery and donations" : "delivery")
                : (donations ? "deliveries and donations" : "deliveries");
        return Optional.of("Did you forget your " + work + " again, Commander?");
    }

    private static boolean isDonation(TransportPlanAction action) {
        return action.kind() == TransportPlanAction.Kind.VISIT
                && action.commodity().toLowerCase(Locale.ROOT).startsWith("donate");
    }

    private void markCurrentStopCompleteIfNeeded() {
        TransportPlanStop stop = plan.stops().get(reachedPlanStop);
        if (!stop.actions().isEmpty() && stop.actions().stream().allMatch(action ->
                completedActions.contains(new ActionKey(
                        reachedPlanStop, action.kind(), action.missionId())))) {
            cargoCompletedStops.add(reachedPlanStop);
        }
    }

    private void refreshCompletionMarkers() {
        int rowOffset = start == null ? 0 : 1;
        for (int stopIndex = 0; stopIndex < plan.stops().size(); stopIndex++) {
            table.setValueAt(Boolean.valueOf(stopIndex < reachedPlanStop
                            || cargoCompletedStops.contains(stopIndex)),
                    rowOffset + stopIndex, 0);
        }
    }

    private String currentReachedSystem() {
        return reachedPlanStop >= 0 && reachedPlanStop < plan.stops().size()
                ? plan.stops().get(reachedPlanStop).location().system() : null;
    }

    private static boolean sameLocationText(String left, String right) {
        return left != null && right != null && !left.isBlank() && !right.isBlank()
                && left.trim().equalsIgnoreCase(right.trim());
    }

    int highlightedRowForTests() {
        return highlightedRow;
    }

    JTable tableForTests() {
        return table;
    }

    String systemForTableRow(int row) {
        if (row < 0) return null;
        if (start != null) {
            if (row == 0) return start.system();
            row--;
        }
        return row < plan.stops().size() ? plan.stops().get(row).location().system() : null;
    }

    public boolean isPointerOverInteractiveRegion(Point screenPoint) {
        return SelectiveHitSupport.containsScreenPoint(applyButton, screenPoint);
    }

    public static List<String> routeSystems(TransportRoutePlan plan) {
        List<String> systems = new ArrayList<>();
        String previous = null;
        if (plan != null) for (TransportPlanStop stop : plan.stops()) {
            String system = stop.location().system();
            if (previous == null || !previous.equalsIgnoreCase(system)) systems.add(system);
            previous = system;
        }
        return List.copyOf(systems);
    }

    private static String actionsText(List<TransportPlanAction> actions) {
        return actionGroups(actions).stream().map(ActionGroup::text)
                .reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private static List<ActionGroup> actionGroups(List<TransportPlanAction> actions) {
        Map<String, ActionGroup> groups = new LinkedHashMap<>();
        int visitIndex = 0;
        for (TransportPlanAction action : actions) {
            String key = action.kind() == TransportPlanAction.Kind.VISIT
                    ? "visit:" + visitIndex++
                    : action.kind() + ":" + action.commodity().toLowerCase(Locale.ROOT);
            groups.computeIfAbsent(key, ignored -> new ActionGroup(action)).add(action);
        }
        return List.copyOf(groups.values());
    }

    private static final class ActionGroup {
        private final TransportPlanAction.Kind kind;
        private final String commodity;
        private final Set<Long> missionIds = new HashSet<>();
        private final List<TransportPlanAction> actions = new ArrayList<>();
        private int tons;

        ActionGroup(TransportPlanAction action) {
            kind = action.kind();
            commodity = action.commodity();
        }

        void add(TransportPlanAction action) {
            tons += action.tons();
            missionIds.add(action.missionId());
            actions.add(action);
        }

        String text() {
            if (kind == TransportPlanAction.Kind.VISIT) return commodity;
            String prefix = kind == TransportPlanAction.Kind.PICK_UP ? "Pick up " : "Deliver ";
            String missions = missionIds.size() > 1 ? " (" + missionIds.size() + " missions)" : "";
            return prefix + tons + " t " + commodity + missions;
        }
    }

    private boolean actionGroupComplete(int stopIndex, ActionGroup group) {
        if (stopIndex < reachedPlanStop) return true;
        return !group.actions.isEmpty() && group.actions.stream().allMatch(action ->
                completedActions.contains(new ActionKey(stopIndex, action.kind(), action.missionId())));
    }

    private record ActionKey(int stopIndex, TransportPlanAction.Kind kind, long missionId) { }

    private final class ActionStatusRenderer extends JPanel implements TableCellRenderer {
        private static final long serialVersionUID = 1L;

        ActionStatusRenderer() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(3, 1, 3, 1));
        }

        @Override
        public Component getTableCellRendererComponent(JTable source, Object value,
                boolean selected, boolean focused, int row, int column) {
            removeAll();
            setFont(source.getFont());
            setBackground(selected ? source.getSelectionBackground() : source.getBackground());
            setOpaque(selected || source.isOpaque());
            setBorder(BorderFactory.createEmptyBorder(3, 1, 3, 1));
            int stopIndex = row - (start == null ? 0 : 1);
            if (stopIndex < 0 || stopIndex >= plan.stops().size()) return this;
            for (ActionGroup group : actionGroups(plan.stops().get(stopIndex).actions())) {
                JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                line.setOpaque(false);
                JLabel status = new JLabel("");
                if (actionGroupComplete(stopIndex, group)) {
                    status.setIcon(CompletedCheckIcon.INSTANCE);
                }
                status.setForeground(EdoUi.User.SUCCESS);
                status.setFont(source.getFont());
                int statusWidth = Math.max(16, CompletedCheckIcon.INSTANCE.getIconWidth() + 3);
                status.setPreferredSize(new java.awt.Dimension(statusWidth,
                        source.getFontMetrics(source.getFont()).getHeight()));
                JLabel text = new JLabel(group.text());
                text.setFont(source.getFont());
                text.setForeground(selected ? source.getSelectionForeground() : source.getForeground());
                line.add(status);
                line.add(text);
                add(line);
            }
            return this;
        }
    }

    private static final class CompletionRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        CompletionRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable source, Object value,
                boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(source, "", selected, focused, row, column);
            setIcon(Boolean.TRUE.equals(value) ? CompletedCheckIcon.INSTANCE : null);
            setHorizontalAlignment(SwingConstants.CENTER);
            return this;
        }
    }

    private static final class RouteNumberRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        RouteNumberRenderer() {
            setHorizontalAlignment(SwingConstants.LEFT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable source, Object value,
                boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(source, value, selected, focused, row, column);
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
            return this;
        }
    }

    private enum CompletedCheckIcon implements Icon {
        INSTANCE;

        @Override public int getIconWidth() { return 13; }
        @Override public int getIconHeight() { return 13; }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(EdoUi.User.SUCCESS);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                g2.drawLine(x + 1, y + 7, x + 5, y + 11);
                g2.drawLine(x + 5, y + 11, x + 12, y + 2);
            } finally {
                g2.dispose();
            }
        }
    }
}
