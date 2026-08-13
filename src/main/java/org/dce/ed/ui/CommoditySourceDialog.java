package org.dce.ed.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JScrollBar;
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
import org.dce.ed.mission.CommoditySourceFilters;
import org.dce.ed.mission.CommoditySourceResults;
import org.dce.ed.mission.CommoditySourceSearch;
import org.dce.ed.mission.MissionRecord;
import org.dce.ed.OverlayPreferences;
import org.dce.ed.edsm.UtilTable;

/** Modal selector for a self-sourced mission's purchase station. */
public final class CommoditySourceDialog extends JDialog {
    private final JTextField nearSystem = new JTextField(22);
    private final JLabel status = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel(
            new String[] { "Station", "System", "Type", "Ly", "Arrival Ls", "Price", "Supply", "Updated" }, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable results = new JTable(model);
    private final CommoditySourceResults accumulated = new CommoditySourceResults();
    private final JComboBox<String> padSize = new JComboBox<>(new String[] { "Small", "Medium", "Large" });
    private final JCheckBox includeStations = new JCheckBox("Stations");
    private final JCheckBox includePlanetary = new JCheckBox("Planetary bases");
    private final JCheckBox includeCarriers = new JCheckBox("Fleet carriers");
    private final JButton save = new JButton("Save");
    private List<CommoditySourceChoice> displayedRows = List.of();
    private int requestId;
    private int loadedRadius;
    private boolean loading;

    public CommoditySourceDialog(Window owner, MissionRecord mission, String currentSystem,
            CommoditySourceSearch search, BiConsumer<String, String> onSave) {
        super(owner, "Sourced from?", ModalityType.APPLICATION_MODAL);
        nearSystem.setText(currentSystem == null ? "" : currentSystem);

        JPanel north = new JPanel(new BorderLayout(6, 6));
        north.add(new JLabel(mission.getCountRequired() + " " + mission.getCommodityLocalised()), BorderLayout.NORTH);
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchRow.add(new JLabel("Near system"));
        searchRow.add(nearSystem);
        JButton searchButton = new JButton("Search");
        searchRow.add(searchButton);
        padSize.setSelectedIndex(OverlayPreferences.getCommoditySourceMinimumPadSize() - 1);
        includeStations.setSelected(OverlayPreferences.isCommoditySourceStationsIncluded());
        includePlanetary.setSelected(OverlayPreferences.isCommoditySourcePlanetaryBasesIncluded());
        includeCarriers.setSelected(OverlayPreferences.isCommoditySourceFleetCarriersIncluded());
        configureLocationCheckboxes(includeStations, includePlanetary, includeCarriers);
        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterRow.add(new JLabel("Pad"));
        filterRow.add(padSize);
        filterRow.add(includeStations);
        filterRow.add(includePlanetary);
        filterRow.add(includeCarriers);
        JPanel searchControls = new JPanel(new GridLayout(2, 1, 0, 2));
        searchControls.add(searchRow);
        searchControls.add(filterRow);
        north.add(searchControls, BorderLayout.CENTER);
        north.add(status, BorderLayout.SOUTH);

        JPanel south = new JPanel(new BorderLayout(6, 6));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        actions.add(cancel); actions.add(save);
        south.add(actions, BorderLayout.SOUTH);

        setLayout(new BorderLayout(8, 8));
        add(north, BorderLayout.NORTH);
        JScrollPane resultsScroll = new JScrollPane(results);
        add(resultsScroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        configureResultsTable(results, OverlayPreferences.getUiFont());
        configureSupplyRenderer(results, 6,
                Math.max(1, mission.getCountRequired() - mission.getItemsDelivered()));
        configureActionButtons(searchButton, cancel, save, OverlayPreferences.getUiFont());
        updateSaveEnabled(save, -1);
        setSize(defaultDialogSize());
        setLocationRelativeTo(owner);

        searchButton.addActionListener(e -> runSearch(search, mission));
        Runnable filtersChanged = () -> {
            ensureLocationSelected();
            saveFilterPreferences();
            runSearch(search, mission);
        };
        padSize.addActionListener(e -> filtersChanged.run());
        includeStations.addActionListener(e -> filtersChanged.run());
        includePlanetary.addActionListener(e -> filtersChanged.run());
        includeCarriers.addActionListener(e -> filtersChanged.run());
        configureLoadMore(resultsScroll.getVerticalScrollBar(), () -> loadMore(search, mission));
        results.getSelectionModel().addListSelectionListener(e -> {
            int row = results.getSelectedRow();
            if (!e.getValueIsAdjusting()) updateSaveEnabled(save, row);
        });
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> {
            CommoditySourceChoice selected = selectedChoice(displayedRows, results.getSelectedRow());
            if (selected == null) return;
            onSave.accept(selected.system(), selected.station());
            dispose();
        });
        scheduleInitialSearch(currentSystem, () -> runSearch(search, mission));
    }

    static void scheduleInitialSearch(String currentSystem, Runnable search) {
        if (currentSystem == null || currentSystem.isBlank() || search == null) return;
        javax.swing.SwingUtilities.invokeLater(search);
    }

    static Dimension defaultDialogSize() {
        return new Dimension(760, 850);
    }

    private void runSearch(CommoditySourceSearch search, MissionRecord mission) {
        String near = nearSystem.getText().trim();
        if (near.isBlank()) { status.setText("Enter a system to search near."); return; }
        ++requestId;
        accumulated.clear();
        loadedRadius = 0;
        loading = false;
        model.setRowCount(0);
        displayedRows = List.of();
        results.clearSelection();
        updateSaveEnabled(save, -1);
        searchRadius(search, mission, 50);
    }

    private void runSingleSearch(CommoditySourceSearch search, MissionRecord mission) {
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

    private void loadMore(CommoditySourceSearch search, MissionRecord mission) {
        if (loading || loadedRadius <= 0 || loadedRadius >= 500) return;
        searchRadius(search, mission, CommoditySourceResults.nextRadius(loadedRadius));
    }

    private void searchRadius(CommoditySourceSearch search, MissionRecord mission, int radius) {
        String near = nearSystem.getText().trim();
        int id = ++requestId;
        loading = true;
        status.setText("Searching within " + radius + " ly...");
        new SwingWorker<List<CommoditySourceChoice>, Void>() {
            @Override protected List<CommoditySourceChoice> doInBackground() throws Exception {
                int remaining = Math.max(1, mission.getCountRequired() - mission.getItemsDelivered());
                return search.search(near, mission.getCommodityLocalised(), remaining, radius);
            }

            @Override protected void done() {
                if (id != requestId || !isDisplayable()) return;
                try {
                    List<CommoditySourceChoice> raw = get();
                    if (raw.size() >= CommoditySourceResults.ARDENT_RESULT_CAP) {
                        loading = false;
                        int retry = loadedRadius == 0 ? CommoditySourceResults.radiusAfterCappedResponse(radius)
                                : loadedRadius + Math.max(1, (radius - loadedRadius) / 2);
                        if (retry > loadedRadius && retry < radius) searchRadius(search, mission, retry);
                        else status.setText("More sellers exist beyond " + loadedRadius
                                + " ly; Ardent's result limit was reached.");
                        return;
                    }
                    CommoditySourceFilters filters = currentFilters();
                    accumulated.merge(raw.stream().filter(filters::matches).toList());
                    loadedRadius = Math.max(loadedRadius, radius);
                    List<CommoditySourceChoice> rows = accumulated.rows();
                    displayedRows = rows;
                    results.clearSelection();
                    updateSaveEnabled(save, -1);
                    model.setRowCount(0);
                    Instant now = Instant.now();
                    for (CommoditySourceChoice c : rows) model.addRow(new Object[] {
                            c.station(), c.system(), c.stationType(), c.systemDistanceLy(),
                            formatArrivalDistance(c.arrivalDistanceLs()), c.price(), c.supply(),
                            formatUpdated(c.updatedAt(), now) });
                    UtilTable.autoSizeTableColumns(results);
                    status.setText(rows.isEmpty() ? "No matching sellers within " + loadedRadius + " ly."
                            : rows.size() + " sellers within " + loadedRadius + " ly.");
                    loading = false;
                    int visibleRows = Math.max(1,
                            results.getVisibleRect().height / Math.max(1, results.getRowHeight()));
                    if (rows.size() < visibleRows && loadedRadius < 500) loadMore(search, mission);
                } catch (Exception ex) {
                    loading = false;
                    status.setText("Search failed; try again.");
                }
            }
        }.execute();
    }

    private CommoditySourceFilters currentFilters() {
        return new CommoditySourceFilters(padSize.getSelectedIndex() + 1, includeStations.isSelected(),
                includePlanetary.isSelected(), includeCarriers.isSelected());
    }

    private void ensureLocationSelected() {
        if (!includeStations.isSelected() && !includePlanetary.isSelected() && !includeCarriers.isSelected()) {
            includeStations.setSelected(true);
        }
    }

    private void saveFilterPreferences() {
        OverlayPreferences.setCommoditySourceMinimumPadSize(padSize.getSelectedIndex() + 1);
        OverlayPreferences.setCommoditySourceStationsIncluded(includeStations.isSelected());
        OverlayPreferences.setCommoditySourcePlanetaryBasesIncluded(includePlanetary.isSelected());
        OverlayPreferences.setCommoditySourceFleetCarriersIncluded(includeCarriers.isSelected());
        OverlayPreferences.flushBackingStore();
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

    static void configureLocationCheckboxes(JCheckBox... checkBoxes) {
        if (checkBoxes == null) return;
        for (JCheckBox checkBox : checkBoxes) OverlayCheckBoxStyle.apply(checkBox);
    }

    static String formatArrivalDistance(Double distanceLs) {
        return distanceLs == null ? "" : String.format(Locale.ROOT, "%,.1f", distanceLs);
    }

    static String formatUpdated(String timestamp, Instant now) {
        if (timestamp == null || timestamp.isBlank() || now == null) return "";
        try {
            long minutes = Math.max(0, Duration.between(Instant.parse(timestamp), now).toMinutes());
            if (minutes < 60) return minutes + " min ago";
            long hours = minutes / 60;
            if (hours < 48) return hours + " hr ago";
            return (hours / 24) + " days ago";
        } catch (Exception ex) {
            return timestamp;
        }
    }

    static void configureActionButtons(JButton search, JButton cancel, JButton save, Font font) {
        OverlayOutlineButtonStyle.applyChipHitSafe(search, font, false);
        OverlayOutlineButtonStyle.applyChipHitSafe(cancel, font, false);
        OverlayOutlineButtonStyle.applyPrimaryHitSafe(save, font);
    }

    static void updateSaveEnabled(JButton save, int selectedRow) {
        if (save != null) save.setEnabled(selectedRow >= 0);
    }

    static CommoditySourceChoice selectedChoice(List<CommoditySourceChoice> rows, int selectedRow) {
        return rows != null && selectedRow >= 0 && selectedRow < rows.size() ? rows.get(selectedRow) : null;
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

    static void configureLoadMore(JScrollBar bar, Runnable loadMore) {
        final boolean[] wasAtBottom = { false };
        bar.getModel().addChangeListener(e -> {
            boolean atBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum();
            if (atBottom && !wasAtBottom[0]) loadMore.run();
            wasAtBottom[0] = atBottom;
        });
    }
}
