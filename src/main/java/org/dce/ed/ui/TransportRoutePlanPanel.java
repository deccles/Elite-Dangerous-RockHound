package org.dce.ed.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import org.dce.ed.OverlayPreferences;
import org.dce.ed.mission.TransportPlanAction;
import org.dce.ed.mission.TransportPlanStop;
import org.dce.ed.mission.TransportRoutePlan;

/** Embedded preview and route handoff for an optimized Transport schedule. */
public final class TransportRoutePlanPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JButton applyButton = new JButton("Apply to Route");

    public TransportRoutePlanPanel(TransportRoutePlan plan, int capacity,
            Consumer<List<String>> onApply) {
        super(new BorderLayout(8, 8));
        setOpaque(false);
        DefaultTableModel model = new DefaultTableModel(
                new String[] { "#", "System / Station", "Action", "Hold" }, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        int index = 1;
        for (TransportPlanStop stop : plan.stops()) {
            model.addRow(new Object[] { index++,
                    stop.location().system() + " / " + stop.location().station(),
                    actionsText(stop.actions()), stop.holdAfterTons() + " / " + capacity + " t" });
        }
        JTable table = new JTable(model);
        CommoditySourceDialog.configureResultsTable(table, OverlayPreferences.getUiFont());
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
        add(scroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
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
        }).reduce((a, b) -> a + "; " + b).orElse("");
    }
}
