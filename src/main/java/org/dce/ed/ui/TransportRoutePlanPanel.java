package org.dce.ed.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import org.dce.ed.OverlayPreferences;
import org.dce.ed.mission.TransportPlanAction;
import org.dce.ed.mission.TransportLocation;
import org.dce.ed.mission.TransportPlanProblem;
import org.dce.ed.mission.TransportPlanStop;
import org.dce.ed.mission.TransportRoutePlan;

/** Embedded preview and route handoff for an optimized Transport schedule. */
public final class TransportRoutePlanPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JButton applyButton = new JButton("Apply to Route");
    private final TransportRoutePlan plan;
    private final TransportLocation start;
    private final JTable table;
    private int highlightedRow = -1;
    private int reachedPlanStop = -1;

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
                new String[] { "#", "System / Station", "Action", "Hold" }, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        if (start != null) {
            model.addRow(new Object[] { 0,
                    start.system() + " / " + start.station(), "",
                    initialHoldTons + " / " + capacity + " t" });
            highlightedRow = 0;
        }
        int index = 1;
        for (TransportPlanStop stop : plan.stops()) {
            model.addRow(new Object[] { index++,
                    stop.location().system() + " / " + stop.location().station(),
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
        table.getColumnModel().getColumn(2).setCellRenderer(new MultilineActionRenderer());
        CommoditySourceDialog.configureResultsTable(table, OverlayPreferences.getUiFont());
        int baseRowHeight = table.getRowHeight();
        for (int row = 0; row < table.getRowCount(); row++) {
            String actions = String.valueOf(table.getValueAt(row, 2));
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
        return actions.stream().map(action -> switch (action.kind()) {
            case PICK_UP -> "Pick up " + action.tons() + " t " + action.commodity();
            case DELIVER -> "Deliver " + action.tons() + " t " + action.commodity();
            case VISIT -> action.commodity();
        }).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private static final class MultilineActionRenderer extends JTextArea
            implements TableCellRenderer {
        private static final long serialVersionUID = 1L;

        MultilineActionRenderer() {
            setEditable(false);
            setLineWrap(false);
            setWrapStyleWord(false);
            setBorder(BorderFactory.createEmptyBorder(3, 1, 3, 1));
        }

        @Override
        public Component getTableCellRendererComponent(JTable source, Object value,
                boolean selected, boolean focused, int row, int column) {
            setText(value == null ? "" : value.toString());
            setFont(source.getFont());
            setForeground(selected ? source.getSelectionForeground() : source.getForeground());
            setBackground(selected ? source.getSelectionBackground() : source.getBackground());
            setOpaque(selected || source.isOpaque());
            setBorder(BorderFactory.createEmptyBorder(3, 1, 3, 1));
            return this;
        }
    }
}
