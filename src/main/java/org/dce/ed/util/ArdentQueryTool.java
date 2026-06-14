package org.dce.ed.util;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

import org.dce.ed.ardent.ArdentClient;
import org.dce.ed.ardent.ArdentQueryParams;
import org.dce.ed.edsm.UtilTable;
import org.dce.ed.logreader.RescanCurrentSystemFromJournal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Sample explorer for the public Ardent API (EDDN-backed commodity market data).
 * Open from overlay Tools → Ardent market query tool.
 */
public class ArdentQueryTool extends JFrame {

    private static ArdentQueryTool openInstance;

    private final ArdentClient client = new ArdentClient();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final JTextField commodityField = new JTextField("tritium", 18);
    private final JTextField systemField = new JTextField(24);
    private final JTextField marketIdField = new JTextField(14);

    private final JTextField minVolumeField = new JTextField("100", 6);
    private final JTextField minPriceField = new JTextField("50000", 8);
    private final JTextField maxPriceField = new JTextField("", 8);
    private final JTextField maxDistanceField = new JTextField("100", 6);
    private final JTextField maxDaysAgoField = new JTextField("7", 4);
    private final JComboBox<FleetCarrierFilter> fleetFilterCombo =
            new JComboBox<>(FleetCarrierFilter.values());

    private final ResultsPanel metaResults = new ResultsPanel();
    private final ResultsPanel commodityResults = new ResultsPanel();
    private final ResultsPanel nearbyResults = new ResultsPanel();
    private final ResultsPanel systemResults = new ResultsPanel();
    private final ResultsPanel marketResults = new ResultsPanel();

    private enum FleetCarrierFilter {
        BOTH("Both stations and carriers", null),
        STATIONS("Stations only", Boolean.FALSE),
        CARRIERS("Fleet carriers only", Boolean.TRUE);

        final String label;
        final Boolean fleetCarriers;

        FleetCarrierFilter(String label, Boolean fleetCarriers) {
            this.label = label;
            this.fleetCarriers = fleetCarriers;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public ArdentQueryTool() {
        super("Ardent Market Query Tool");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (openInstance == ArdentQueryTool.this) {
                    openInstance = null;
                }
            }
        });

        JLabel title = new JLabel("Ardent API — commodity market explorer (EDDN data)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));

        JLabel hint = new JLabel(
                "<html>Data is community-uploaded via EDMC; check <code>updatedAt</code> for freshness. "
                        + "On <b>imports</b> (buy orders), payout price is <code>sellPrice</code>.</html>");
        hint.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
        header.add(title, BorderLayout.NORTH);
        header.add(hint, BorderLayout.CENTER);

        JPanel shared = createSharedFieldsPanel();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Meta", createMetaTab());
        tabs.addTab("Commodity (galaxy)", createCommodityTab());
        tabs.addTab("Nearby", createNearbyTab());
        tabs.addTab("System", createSystemTab());
        tabs.addTab("Market ID", createMarketTab());

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        center.add(shared, BorderLayout.NORTH);
        center.add(tabs, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);

        setMinimumSize(new Dimension(960, 720));
        setSize(1050, 820);
        setLocationRelativeTo(null);

        prefillCurrentSystem();
    }

    public static void showDefaultOrBringToFront(Component parent) {
        SwingUtilities.invokeLater(() -> {
            if (openInstance != null && openInstance.isDisplayable()) {
                openInstance.toFront();
                openInstance.requestFocus();
                return;
            }
            openInstance = new ArdentQueryTool();
            openInstance.setLocationRelativeTo(parent);
            openInstance.setVisible(true);
        });
    }

    private void prefillCurrentSystem() {
        RescanCurrentSystemFromJournal.TargetSystem target = RescanCurrentSystemFromJournal.resolveTargetSystem();
        if (target != null && target.systemName() != null && !target.systemName().isBlank()) {
            systemField.setText(target.systemName());
        }
    }

    private JPanel createSharedFieldsPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Shared inputs"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 6, 4, 6);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        gc.gridx = 0;
        gc.gridy = 0;
        gc.weightx = 0;
        p.add(new JLabel("Commodity:"), gc);
        gc.gridx = 1;
        gc.weightx = 0.35;
        p.add(commodityField, gc);

        gc.gridx = 2;
        gc.weightx = 0;
        p.add(new JLabel("System:"), gc);
        gc.gridx = 3;
        gc.weightx = 0.65;
        p.add(systemField, gc);

        gc.gridx = 0;
        gc.gridy = 1;
        gc.weightx = 0;
        p.add(new JLabel("Market ID:"), gc);
        gc.gridx = 1;
        gc.weightx = 0.35;
        p.add(marketIdField, gc);

        gc.gridx = 2;
        gc.gridy = 1;
        gc.gridwidth = 2;
        gc.weightx = 1;
        JButton useCurrent = new JButton("Use overlay current system");
        useCurrent.addActionListener(e -> prefillCurrentSystem());
        p.add(useCurrent, gc);
        gc.gridwidth = 1;

        return p;
    }

    private JPanel createFilterPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Filters (trade endpoints)"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(3, 6, 3, 6);
        gc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addFilterRow(p, gc, row++, "Min volume:", minVolumeField);
        addFilterRow(p, gc, row++, "Min price:", minPriceField);
        addFilterRow(p, gc, row++, "Max price:", maxPriceField);
        addFilterRow(p, gc, row++, "Max distance (ly):", maxDistanceField);
        addFilterRow(p, gc, row++, "Max days ago:", maxDaysAgoField);

        gc.gridx = 0;
        gc.gridy = row;
        p.add(new JLabel("Fleet carriers:"), gc);
        gc.gridx = 1;
        p.add(fleetFilterCombo, gc);
        return p;
    }

    private static void addFilterRow(JPanel p, GridBagConstraints gc, int row, String label, JTextField field) {
        gc.gridx = 0;
        gc.gridy = row;
        p.add(new JLabel(label), gc);
        gc.gridx = 1;
        p.add(field, gc);
    }

    private JPanel createMetaTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));

        JButton versionBtn = new JButton("GET /v2/version");
        versionBtn.addActionListener(e -> runQuery(metaResults, "version", client::getVersion));
        buttons.add(versionBtn);

        JButton statsBtn = new JButton("GET /v2/stats");
        statsBtn.addActionListener(e -> runQuery(metaResults, "stats", client::getStats));
        buttons.add(Box.createVerticalStrut(6));
        buttons.add(statsBtn);

        panel.add(buttons, BorderLayout.NORTH);
        panel.add(metaResults, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createCommodityTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));

        buttons.add(labeledHint("Galaxy-wide lists return up to 100 rows, sorted by best price."));
        buttons.add(Box.createVerticalStrut(6));

        JButton summaryBtn = new JButton("Commodity summary");
        summaryBtn.addActionListener(e -> {
            String c = commodity();
            runQuery(commodityResults, "commodity summary: " + c,
                    () -> client.getCommoditySummary(c));
        });
        buttons.add(summaryBtn);

        JButton importsBtn = new JButton("Imports — buy orders (sell to them)");
        importsBtn.addActionListener(e -> {
            String c = commodity();
            runQuery(commodityResults, "imports: " + c,
                    () -> client.getCommodityImports(c, readFilters(false)));
        });
        buttons.add(Box.createVerticalStrut(4));
        buttons.add(importsBtn);

        JButton exportsBtn = new JButton("Exports — sell orders (buy from them)");
        exportsBtn.addActionListener(e -> {
            String c = commodity();
            runQuery(commodityResults, "exports: " + c,
                    () -> client.getCommodityExports(c, readFilters(true)));
        });
        buttons.add(Box.createVerticalStrut(4));
        buttons.add(exportsBtn);

        JPanel north = new JPanel(new BorderLayout());
        north.add(createFilterPanel(), BorderLayout.CENTER);
        north.add(buttons, BorderLayout.SOUTH);

        panel.add(north, BorderLayout.NORTH);
        panel.add(commodityResults, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createNearbyTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        buttons.add(labeledHint("Nearby queries use the System field; up to 1000 results within maxDistance ly."));
        buttons.add(Box.createVerticalStrut(6));

        JButton importsBtn = new JButton("Nearby imports (buy orders)");
        importsBtn.addActionListener(e -> {
            String sys = system();
            String c = commodity();
            runQuery(nearbyResults, "nearby imports: " + c + " @ " + sys,
                    () -> client.getNearbyImports(sys, c, readFilters(false)));
        });
        buttons.add(importsBtn);

        JButton exportsBtn = new JButton("Nearby exports (sell orders)");
        exportsBtn.addActionListener(e -> {
            String sys = system();
            String c = commodity();
            runQuery(nearbyResults, "nearby exports: " + c + " @ " + sys,
                    () -> client.getNearbyExports(sys, c, readFilters(true)));
        });
        buttons.add(Box.createVerticalStrut(4));
        buttons.add(exportsBtn);

        JPanel north = new JPanel(new BorderLayout());
        north.add(createFilterPanel(), BorderLayout.CENTER);
        north.add(buttons, BorderLayout.SOUTH);

        panel.add(north, BorderLayout.NORTH);
        panel.add(nearbyResults, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSystemTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));

        JButton infoBtn = new JButton("System info");
        infoBtn.addActionListener(e -> {
            String sys = system();
            runQuery(systemResults, "system: " + sys, () -> client.getSystem(sys));
        });
        buttons.add(infoBtn);

        JButton commoditiesBtn = new JButton("All commodities in system");
        commoditiesBtn.addActionListener(e -> {
            String sys = system();
            runQuery(systemResults, "system commodities: " + sys,
                    () -> client.getSystemCommodities(sys));
        });
        buttons.add(Box.createVerticalStrut(4));
        buttons.add(commoditiesBtn);

        JButton oneCommodityBtn = new JButton("One commodity in system (uses Commodity field)");
        oneCommodityBtn.addActionListener(e -> {
            String sys = system();
            String c = commodity();
            runQuery(systemResults, "system commodity: " + c + " @ " + sys,
                    () -> client.getSystemCommodity(sys, c, readFilters(false)));
        });
        buttons.add(Box.createVerticalStrut(4));
        buttons.add(oneCommodityBtn);

        panel.add(buttons, BorderLayout.NORTH);
        panel.add(systemResults, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createMarketTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        buttons.add(labeledHint("Look up a single market by numeric Market ID (station or carrier)."));
        buttons.add(Box.createVerticalStrut(6));

        JButton lookupBtn = new JButton("Market + commodity lookup");
        lookupBtn.addActionListener(e -> {
            long id = parseLong(marketIdField);
            if (id <= 0) {
                marketResults.showError("Enter a valid Market ID.");
                return;
            }
            String c = commodity();
            runQuery(marketResults, "market " + id + " / " + c,
                    () -> client.getMarketCommodity(id, c));
        });
        buttons.add(lookupBtn);

        panel.add(buttons, BorderLayout.NORTH);
        panel.add(marketResults, BorderLayout.CENTER);
        return panel;
    }

    private static JLabel labeledHint(String text) {
        JLabel l = new JLabel("<html>" + text + "</html>");
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        return l;
    }

    private String commodity() {
        return commodityField.getText().trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String system() {
        return systemField.getText().trim();
    }

    private ArdentQueryParams readFilters(boolean forExports) {
        ArdentQueryParams p = new ArdentQueryParams();
        Integer vol = parseInt(minVolumeField);
        if (vol != null) {
            p.minVolume(vol.intValue());
        }
        if (forExports) {
            Integer maxP = parseInt(maxPriceField);
            if (maxP != null) {
                p.maxPrice(maxP.intValue());
            }
        } else {
            Integer minP = parseInt(minPriceField);
            if (minP != null) {
                p.minPrice(minP.intValue());
            }
        }
        Integer dist = parseInt(maxDistanceField);
        if (dist != null) {
            p.maxDistance(dist.intValue());
        }
        Integer days = parseInt(maxDaysAgoField);
        if (days != null) {
            p.maxDaysAgo(days.intValue());
        }
        FleetCarrierFilter fc = (FleetCarrierFilter) fleetFilterCombo.getSelectedItem();
        if (fc != null) {
            p.fleetCarriers(fc.fleetCarriers);
        }
        return p;
    }

    private static Integer parseInt(JTextField field) {
        String t = field.getText().trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(t));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static long parseLong(JTextField field) {
        String t = field.getText().trim();
        if (t.isEmpty()) {
            return -1;
        }
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private interface JsonQuery {
        String run() throws Exception;
    }

    private void runQuery(ResultsPanel output, String label, JsonQuery query) {
        output.beginQuery(label);
        new SwingWorker<String, Void>() {
            private String url;

            @Override
            protected String doInBackground() throws Exception {
                String json = query.run();
                url = client.getLastUrl();
                return json;
            }

            @Override
            protected void done() {
                try {
                    output.finishQuery(url, get());
                } catch (Exception ex) {
                    output.finishQuery(url, "ERROR: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private final class ResultsPanel extends JPanel {

        private final JLabel urlLabel = new JLabel(" ");
        private final JTextArea textArea = new JTextArea();
        private final DefaultTableModel tableModel = new DefaultTableModel();
        private final JTable table = new JTable(tableModel);
        private final CardLayout cards = new CardLayout();
        private final JPanel cardPanel = new JPanel(cards);
        private final JButton toggleBtn = new JButton("Show JSON");
        private boolean showingTable = true;

        ResultsPanel() {
            super(new BorderLayout(6, 6));
            setBorder(BorderFactory.createTitledBorder("Results"));

            urlLabel.setFont(urlLabel.getFont().deriveFont(Font.PLAIN, 11f));
            urlLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));

            textArea.setEditable(false);
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);

            table.setAutoCreateRowSorter(true);
            table.setFillsViewportHeight(true);

            cardPanel.add(new JScrollPane(table), "table");
            cardPanel.add(new JScrollPane(textArea), "text");

            toggleBtn.addActionListener(e -> toggleView());

            JPanel top = new JPanel(new BorderLayout(4, 4));
            top.add(toggleBtn, BorderLayout.WEST);
            top.add(urlLabel, BorderLayout.CENTER);
            add(top, BorderLayout.NORTH);
            add(cardPanel, BorderLayout.CENTER);

            cards.show(cardPanel, "table");
        }

        void beginQuery(String label) {
            urlLabel.setText("Running: " + label + "…");
            textArea.setText("Loading…");
            tableModel.setRowCount(0);
            tableModel.setColumnCount(0);
        }

        void finishQuery(String url, String rawJson) {
            urlLabel.setText(url != null ? url : "(no URL)");
            if (rawJson == null || rawJson.isBlank()) {
                textArea.setText("(empty response)");
                showText();
                return;
            }
            try {
                JsonElement tree = JsonParser.parseString(rawJson);
                textArea.setText(gson.toJson(tree));
                if (tree.isJsonArray()) {
                    fillTableFromArray(tree.getAsJsonArray());
                    showTable();
                } else if (tree.isJsonObject()) {
                    fillTableFromObject(tree.getAsJsonObject());
                    showTable();
                } else {
                    showText();
                }
            } catch (Exception ex) {
                textArea.setText(rawJson);
                showText();
            }
        }

        void showError(String message) {
            urlLabel.setText(message);
            textArea.setText(message);
            tableModel.setRowCount(0);
            showText();
        }

        private void toggleView() {
            if (showingTable) {
                showText();
            } else {
                showTable();
            }
        }

        private void showTable() {
            showingTable = true;
            cards.show(cardPanel, "table");
            toggleBtn.setText("Show JSON");
        }

        private void showText() {
            showingTable = false;
            cards.show(cardPanel, "text");
            toggleBtn.setText("Show table");
        }

        private void fillTableFromArray(JsonArray array) {
            if (array.size() == 0) {
                tableModel.setRowCount(0);
                tableModel.setColumnCount(0);
                return;
            }
            Set<String> columns = new LinkedHashSet<>();
            for (JsonElement el : array) {
                if (el.isJsonObject()) {
                    for (String key : el.getAsJsonObject().keySet()) {
                        columns.add(key);
                    }
                }
            }
            List<String> cols = prioritizeTradeColumns(new ArrayList<>(columns));
            tableModel.setColumnIdentifiers(cols.toArray());
            for (JsonElement el : array) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject o = el.getAsJsonObject();
                Object[] row = new Object[cols.size()];
                for (int i = 0; i < cols.size(); i++) {
                    row[i] = jsonCell(o.get(cols.get(i)));
                }
                tableModel.addRow(row);
            }
            SwingUtilities.invokeLater(() -> UtilTable.autoSizeTableColumns(table));
        }

        private void fillTableFromObject(JsonObject obj) {
            tableModel.setColumnIdentifiers(new Object[] { "key", "value" });
            tableModel.setRowCount(0);
            for (String key : obj.keySet()) {
                tableModel.addRow(new Object[] { key, jsonCell(obj.get(key)) });
            }
            SwingUtilities.invokeLater(() -> UtilTable.autoSizeTableColumns(table));
        }

        private static List<String> prioritizeTradeColumns(List<String> cols) {
            String[] preferred = {
                    "commodityName", "systemName", "stationName", "stationType",
                    "sellPrice", "buyPrice", "demand", "stock", "distance", "distanceToArrival",
                    "maxLandingPadSize", "marketId", "updatedAt"
            };
            List<String> out = new ArrayList<>();
            for (String p : preferred) {
                if (cols.remove(p)) {
                    out.add(p);
                }
            }
            cols.sort(String.CASE_INSENSITIVE_ORDER);
            out.addAll(cols);
            return out;
        }

        private static String jsonCell(JsonElement el) {
            if (el == null || el.isJsonNull()) {
                return "";
            }
            if (el.isJsonPrimitive()) {
                return el.getAsString();
            }
            return el.toString();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ArdentQueryTool().setVisible(true));
    }
}
