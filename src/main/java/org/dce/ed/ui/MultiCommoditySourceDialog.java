package org.dce.ed.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

import org.dce.ed.OverlayPreferences;
import org.dce.ed.edsm.UtilTable;
import org.dce.ed.mission.CommoditySourceChoice;
import org.dce.ed.mission.CommoditySourceFilters;
import org.dce.ed.mission.CommoditySourceResults;
import org.dce.ed.mission.CommoditySourceSearch;
import org.dce.ed.mission.MissionRecord;
import org.dce.ed.mission.MissionTracker;
import org.dce.ed.mission.MultiCommodityMissionNeed;
import org.dce.ed.mission.MultiCommoditySourcePlanner;
import org.dce.ed.mission.MultiCommodityStationAssessment;

/** Finds stations that can satisfy the greatest number of outstanding commodity missions. */
public final class MultiCommoditySourceDialog extends JDialog {
    private final List<MissionRecord> missions;
    private final List<MultiCommodityMissionNeed> needs;
    private final Map<String, Integer> inHold;
    private final CommoditySourceSearch search;
    private final Consumer<MultiCommodityStationAssessment> onSave;
    private final JTextField nearSystem = new JTextField(22);
    private final JComboBox<String> padSize = new JComboBox<>(new String[] { "Small", "Medium", "Large" });
    private final JCheckBox stations = new JCheckBox("Stations");
    private final JCheckBox planetary = new JCheckBox("Planetary bases");
    private final JCheckBox carriers = new JCheckBox("Fleet carriers");
    private final JLabel status = new JLabel(" ");
    private final JLabel selectionDetail = new JLabel("Select a station to see assigned missions.");
    private final JButton save = new JButton("Save");
    private final DefaultTableModel model = new DefaultTableModel(new String[] {
            "Station", "System", "Type", "Ly", "Arrival Ls", "Missions", "Commodities", "Buy", "Updated" }, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<MultiCommodityStationAssessment> displayed = List.of();
    private int radius;
    private int requestId;
    private boolean loading;

    public MultiCommoditySourceDialog(Window owner, List<MissionRecord> missions, String currentSystem,
            Map<String, Integer> inHold, CommoditySourceSearch search,
            Consumer<MultiCommodityStationAssessment> onSave) {
        super(owner, "Source all commodities", ModalityType.APPLICATION_MODAL);
        this.missions = missions == null ? List.of() : List.copyOf(missions);
        this.needs = buildNeeds(this.missions);
        this.inHold = inHold == null ? Map.of() : Map.copyOf(inHold);
        this.search = search;
        this.onSave = onSave;
        nearSystem.setText(currentSystem == null ? "" : currentSystem);

        padSize.setSelectedIndex(OverlayPreferences.getCommoditySourceMinimumPadSize() - 1);
        stations.setSelected(OverlayPreferences.isCommoditySourceStationsIncluded());
        planetary.setSelected(OverlayPreferences.isCommoditySourcePlanetaryBasesIncluded());
        carriers.setSelected(OverlayPreferences.isCommoditySourceFleetCarriersIncluded());
        CommoditySourceDialog.configureLocationCheckboxes(stations, planetary, carriers);

        JButton searchButton = new JButton("Search");
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchRow.add(new JLabel("Near system")); searchRow.add(nearSystem); searchRow.add(searchButton);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filters.add(new JLabel("Pad")); filters.add(padSize); filters.add(stations); filters.add(planetary); filters.add(carriers);
        JPanel north = new JPanel(new GridLayout(3, 1, 0, 2));
        north.add(searchRow); north.add(filters); north.add(status);

        JScrollPane scroll = new JScrollPane(table);
        CommoditySourceDialog.configureResultsTable(table, OverlayPreferences.getUiFont());
        JButton cancel = new JButton("Cancel");
        CommoditySourceDialog.configureActionButtons(searchButton, cancel, save, OverlayPreferences.getUiFont());
        save.setEnabled(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(cancel); actions.add(save);
        JPanel south = new JPanel(new BorderLayout(6, 6));
        south.add(selectionDetail, BorderLayout.CENTER); south.add(actions, BorderLayout.SOUTH);

        setLayout(new BorderLayout(8, 8));
        add(north, BorderLayout.NORTH); add(scroll, BorderLayout.CENTER); add(south, BorderLayout.SOUTH);
        setSize(new Dimension(1100, 850));
        setLocationRelativeTo(owner);

        searchButton.addActionListener(e -> restartSearch());
        Runnable changed = () -> { savePreferences(); restartSearch(); };
        padSize.addActionListener(e -> changed.run());
        stations.addActionListener(e -> { ensureLocation(); changed.run(); });
        planetary.addActionListener(e -> { ensureLocation(); changed.run(); });
        carriers.addActionListener(e -> { ensureLocation(); changed.run(); });
        CommoditySourceDialog.configureLoadMore(scroll.getVerticalScrollBar(), this::loadMore);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            MultiCommodityStationAssessment selected = selected();
            save.setEnabled(selected != null && !selected.allocation().missionIds().isEmpty());
            selectionDetail.setText(detailFor(selected));
        });
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> {
            MultiCommodityStationAssessment selected = selected();
            if (selected == null || selected.allocation().missionIds().isEmpty()) return;
            onSave.accept(selected);
            dispose();
        });
        javax.swing.SwingUtilities.invokeLater(this::restartSearch);
    }

    public static List<MultiCommodityMissionNeed> buildNeeds(List<MissionRecord> missions) {
        List<MultiCommodityMissionNeed> out = new ArrayList<>();
        if (missions == null) return List.of();
        for (MissionRecord mission : missions) {
            if (mission == null || !mission.isManuallySourceableCommodityMission()) continue;
            if ((mission.getSourcedFromSystem() != null && !mission.getSourcedFromSystem().isBlank())
                    || (mission.getSourcedFromStation() != null && !mission.getSourcedFromStation().isBlank())) continue;
            int required = mission.getCountRequired() > 0 ? mission.getCountRequired() : mission.getTotalItemsToDeliver();
            int outstanding = Math.max(0, required - mission.getItemsDelivered());
            if (outstanding > 0 && mission.getCommodityLocalised() != null && !mission.getCommodityLocalised().isBlank())
                out.add(new MultiCommodityMissionNeed(mission.getMissionId(), mission.getCommodityLocalised(),
                        outstanding, MissionTracker.expiryInstant(mission)));
        }
        return List.copyOf(out);
    }

    private void restartSearch() {
        if (nearSystem.getText().trim().isBlank() || needs.isEmpty()) return;
        radius = 0;
        displayed = List.of();
        model.setRowCount(0);
        searchAt(50);
    }

    private void loadMore() {
        if (!loading && radius > 0 && radius < 500) searchAt(CommoditySourceResults.nextRadius(radius));
    }

    private void searchAt(int requestedRadius) {
        if (loading) return;
        loading = true;
        int id = ++requestId;
        status.setText("Searching " + needs.stream().map(MultiCommodityMissionNeed::commodity).distinct().count()
                + " commodities within " + requestedRadius + " ly...");
        new SwingWorker<SearchBatch, Void>() {
            @Override protected SearchBatch doInBackground() throws Exception {
                Map<String, List<CommoditySourceChoice>> offers = new LinkedHashMap<>();
                boolean capped = false;
                CommoditySourceFilters filter = currentFilters();
                for (String commodity : needs.stream().map(MultiCommodityMissionNeed::commodity).distinct().toList()) {
                    List<CommoditySourceChoice> raw = search.search(nearSystem.getText().trim(), commodity, 1, requestedRadius);
                    capped |= raw.size() >= CommoditySourceResults.ARDENT_RESULT_CAP;
                    offers.put(commodity, raw.stream().filter(filter::matches).toList());
                }
                return new SearchBatch(offers, capped);
            }
            @Override protected void done() {
                if (id != requestId || !isDisplayable()) return;
                try {
                    SearchBatch batch = get();
                    loading = false;
                    if (batch.capped) {
                        int retry = radius == 0 ? CommoditySourceResults.radiusAfterCappedResponse(requestedRadius)
                                : radius + Math.max(1, (requestedRadius - radius) / 2);
                        if (retry > radius && retry < requestedRadius) searchAt(retry);
                        else status.setText("Ardent's result limit was reached beyond " + radius + " ly.");
                        return;
                    }
                    radius = requestedRadius;
                    displayed = MultiCommoditySourcePlanner.assess(needs, inHold, batch.offers);
                    render();
                    int visibleRows = Math.max(1,
                            table.getVisibleRect().height / Math.max(1, table.getRowHeight()));
                    if (displayed.size() < visibleRows && radius < 500) loadMore();
                } catch (Exception ex) {
                    loading = false;
                    status.setText("Search failed; try again.");
                }
            }
        }.execute();
    }

    private void render() {
        model.setRowCount(0);
        Instant now = Instant.now();
        for (MultiCommodityStationAssessment row : displayed) model.addRow(new Object[] {
                row.station().station(), row.station().system(), row.station().stationType(),
                row.station().systemDistanceLy(), CommoditySourceDialog.formatArrivalDistance(row.station().arrivalDistanceLs()),
                row.allocation().missionIds().size() + " / " + needs.size(), row.commoditiesText(),
                row.allocation().purchaseTons(), CommoditySourceDialog.formatUpdated(row.oldestUpdatedAt(), now) });
        table.clearSelection(); save.setEnabled(false);
        UtilTable.autoSizeTableColumns(table);
        status.setText(displayed.size() + " stations assessed within " + radius + " ly.");
    }

    private MultiCommodityStationAssessment selected() {
        int row = table.getSelectedRow();
        return row >= 0 && row < displayed.size() ? displayed.get(row) : null;
    }

    private String detailFor(MultiCommodityStationAssessment assessment) {
        if (assessment == null) return "Select a station to see assigned missions.";
        List<String> lines = new ArrayList<>();
        for (Long id : assessment.allocation().missionIds()) for (MissionRecord mission : missions)
            if (mission.getMissionId() == id.longValue()) lines.add(mission.getCommodityLocalised() + " — " + mission.summaryLine());
        return "<html>Will assign " + lines.size() + " mission(s): " + String.join("; ", lines) + "</html>";
    }

    private CommoditySourceFilters currentFilters() {
        return new CommoditySourceFilters(padSize.getSelectedIndex() + 1, stations.isSelected(),
                planetary.isSelected(), carriers.isSelected());
    }

    private void ensureLocation() {
        if (!stations.isSelected() && !planetary.isSelected() && !carriers.isSelected()) stations.setSelected(true);
    }

    private void savePreferences() {
        OverlayPreferences.setCommoditySourceMinimumPadSize(padSize.getSelectedIndex() + 1);
        OverlayPreferences.setCommoditySourceStationsIncluded(stations.isSelected());
        OverlayPreferences.setCommoditySourcePlanetaryBasesIncluded(planetary.isSelected());
        OverlayPreferences.setCommoditySourceFleetCarriersIncluded(carriers.isSelected());
        OverlayPreferences.flushBackingStore();
    }

    private record SearchBatch(Map<String, List<CommoditySourceChoice>> offers, boolean capped) { }
}
