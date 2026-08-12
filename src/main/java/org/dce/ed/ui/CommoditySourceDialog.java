package org.dce.ed.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.List;
import java.util.function.BiConsumer;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import org.dce.ed.mission.CommoditySourceChoice;
import org.dce.ed.mission.CommoditySourceSearch;
import org.dce.ed.mission.MissionRecord;
import org.dce.ed.OverlayPreferences;
import org.dce.ed.edsm.UtilTable;

/** Modal selector for a self-sourced mission's purchase station. */
public final class CommoditySourceDialog extends JDialog {
    private final JTextField nearSystem = new JTextField(22);
    private final JTextField system = new JTextField(22);
    private final JTextField station = new JTextField(22);
    private final JLabel status = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel(
            new String[] { "Station", "System", "Ly", "Arrival Ls", "Price", "Supply", "Updated" }, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable results = new JTable(model);
    private int requestId;

    public CommoditySourceDialog(Window owner, MissionRecord mission, String currentSystem,
            CommoditySourceSearch search, BiConsumer<String, String> onSave) {
        super(owner, "Sourced from?", ModalityType.APPLICATION_MODAL);
        nearSystem.setText(currentSystem == null ? "" : currentSystem);
        system.setText(mission.getSourcedFromSystem());
        station.setText(mission.getSourcedFromStation());

        JPanel north = new JPanel(new BorderLayout(6, 6));
        north.add(new JLabel(mission.getCountRequired() + " " + mission.getCommodityLocalised()), BorderLayout.NORTH);
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchRow.add(new JLabel("Near system"));
        searchRow.add(nearSystem);
        JButton searchButton = new JButton("Search");
        searchRow.add(searchButton);
        north.add(searchRow, BorderLayout.CENTER);
        north.add(status, BorderLayout.SOUTH);

        JPanel manual = new JPanel(new GridLayout(2, 2, 6, 6));
        manual.add(new JLabel("System")); manual.add(system);
        manual.add(new JLabel("Station")); manual.add(station);
        JPanel south = new JPanel(new BorderLayout(6, 6));
        south.add(manual, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        JButton save = new JButton("Save");
        actions.add(cancel); actions.add(save);
        south.add(actions, BorderLayout.SOUTH);

        setLayout(new BorderLayout(8, 8));
        add(north, BorderLayout.NORTH);
        add(new JScrollPane(results), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        configureResultsTable(results, OverlayPreferences.getUiFont());
        configureSupplyRenderer(results, 5,
                Math.max(1, mission.getCountRequired() - mission.getItemsDelivered()));
        setSize(760, 430);
        setLocationRelativeTo(owner);

        searchButton.addActionListener(e -> runSearch(search, mission));
        results.getSelectionModel().addListSelectionListener(e -> {
            int row = results.getSelectedRow();
            if (!e.getValueIsAdjusting() && row >= 0) {
                station.setText(String.valueOf(model.getValueAt(row, 0)));
                system.setText(String.valueOf(model.getValueAt(row, 1)));
            }
        });
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> {
            String sys = system.getText().trim();
            String stn = station.getText().trim();
            if (sys.isBlank() || stn.isBlank()) {
                status.setText("Enter both a system and station.");
                return;
            }
            onSave.accept(sys, stn);
            dispose();
        });
        scheduleInitialSearch(currentSystem, () -> runSearch(search, mission));
    }

    static void scheduleInitialSearch(String currentSystem, Runnable search) {
        if (currentSystem == null || currentSystem.isBlank() || search == null) return;
        javax.swing.SwingUtilities.invokeLater(search);
    }

    private void runSearch(CommoditySourceSearch search, MissionRecord mission) {
        String near = nearSystem.getText().trim();
        if (near.isBlank()) { status.setText("Enter a system to search near."); return; }
        int id = ++requestId;
        status.setText("Searching Ardent…");
        new SwingWorker<List<CommoditySourceChoice>, Void>() {
            @Override protected List<CommoditySourceChoice> doInBackground() throws Exception {
                int remaining = Math.max(1, mission.getCountRequired() - mission.getItemsDelivered());
                return search.search(near, mission.getCommodityLocalised(), remaining);
            }
            @Override protected void done() {
                if (id != requestId || !isDisplayable()) return;
                try {
                    List<CommoditySourceChoice> rows = get();
                    model.setRowCount(0);
                    for (CommoditySourceChoice c : rows) model.addRow(new Object[] {
                            c.station(), c.system(), c.systemDistanceLy(), c.arrivalDistanceLs(),
                            c.price(), c.supply(), c.updatedAt() });
                    UtilTable.autoSizeTableColumns(results);
                    status.setText(rows.isEmpty() ? "No nearby sellers found; enter one manually."
                            : rows.size() + " nearby sellers found.");
                } catch (Exception ex) {
                    status.setText("Search failed; enter the source manually.");
                }
            }
        }.execute();
    }

    static void configureResultsTable(JTable table, Font font) {
        if (font == null) return;
        table.setFont(font);
        table.setRowHeight(Math.max(table.getRowHeight(), table.getFontMetrics(font).getHeight() + 6));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        Font headerFont = font.deriveFont(Font.BOLD);
        table.getTableHeader().setFont(headerFont);
        Dimension preferred = table.getTableHeader().getPreferredSize();
        table.getTableHeader().setPreferredSize(new Dimension(preferred.width,
                table.getTableHeader().getFontMetrics(headerFont).getHeight() + 6));
        UtilTable.autoSizeTableColumns(table);
    }

    static void configureSupplyRenderer(JTable table, int column, int required) {
        table.getColumnModel().getColumn(column).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable source, Object value, boolean selected,
                    boolean focused, int row, int viewColumn) {
                Component component = super.getTableCellRendererComponent(
                        source, value, selected, focused, row, viewColumn);
                if (!selected) {
                    component.setForeground(value instanceof Number number && number.longValue() < required
                            ? Color.RED : source.getForeground());
                }
                return component;
            }
        });
    }
}
