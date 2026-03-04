package org.dce.ed.util;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import org.dce.ed.edsm.BodiesResponse;
import org.dce.ed.edsm.CmdrCreditsResponse;
import org.dce.ed.edsm.CmdrLastPositionResponse;
import org.dce.ed.edsm.CmdrRanksResponse;
import org.dce.ed.edsm.DeathsResponse;
import org.dce.ed.edsm.ExobiologyPanel;
import org.dce.ed.edsm.LogsResponse;
import org.dce.ed.edsm.ShowSystemResponse;
import org.dce.ed.edsm.SphereSystemsResponse;
import org.dce.ed.edsm.SystemResponse;
import org.dce.ed.edsm.SystemStationsResponse;
import org.dce.ed.edsm.TrafficResponse;
import org.dce.ed.edsm.UtilTable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class EdsmQueryTool extends JFrame {

    private boolean suppressAutoCompleteEvents = false;
	
    private static final String PREF_KEY_EDSM_API = "edsmApiKey";
    private static final String PREF_KEY_EDSM_CMDR = "edsmCommanderName";

    private static final String LOCATE_ICON_PATH = "/org/dce/ed/edsm/locate_icon.png";
    private static final int LOCATE_ICON_SIZE = 16;
    private static final ImageIcon LOCATE_ICON = loadLocateIcon();

    private final EdsmClient client;
    private final Gson gson;

    private final JLabel commanderStatusLabel;

    // Shared "system name" fields across tabs
    private final List<JTextField> systemNameFields = new ArrayList<>();
    private JTextField systemTabSystemField;
    private JTextField bodiesTabSystemField;
    private JTextField trafficTabSystemField;

    private String currentSystemName;

    // Sphere XYZ fields (for convenience)
    private JTextField sphereXField;
    private JTextField sphereYField;
    private JTextField sphereZField;

    // Autocomplete UI
    private Timer autoCompleteTimer;
    private JPopupMenu autoCompletePopup;
    private JList<String> autoCompleteList;
    private JTextField autoCompleteTargetField;
    private String pendingAutoCompletePrefix;
    private String autoCompleteOriginalText;

    // Per-tab output panels
    private TabOutputPanel systemOutputPanel;
    private TabOutputPanel bodiesOutputPanel;
    private TabOutputPanel trafficOutputPanel;
    private TabOutputPanel commanderOutputPanel;

    public EdsmQueryTool() {
        super("EDSM Query Tool");

        this.client = new EdsmClient();
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        systemOutputPanel = new TabOutputPanel();
        bodiesOutputPanel = new TabOutputPanel();
        trafficOutputPanel = new TabOutputPanel();
        commanderOutputPanel = new TabOutputPanel();

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JLabel titleLabel = new JLabel("Elite Dangerous Star Map (EDSM) Query Tool");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));

        commanderStatusLabel = new JLabel();
        updateCommanderStatusLabel();

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(commanderStatusLabel, BorderLayout.EAST);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("System", createSystemTab());
        tabs.addTab("Bodies", createBodiesTab());
        tabs.addTab("Traffic / Logs", createTrafficTab());
        tabs.addTab("Commander", createCommanderTab());
        tabs.addTab("Exobiology", new ExobiologyPanel(client));
        
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(headerPanel, BorderLayout.NORTH);
        topPanel.add(tabs, BorderLayout.CENTER);

        add(topPanel, BorderLayout.CENTER);

        setPreferredSize(new Dimension(1100, 900));
        
        pack();
        setLocationRelativeTo(null);

        initCommanderSystemAtStartup();
        
    }

    private static ImageIcon loadLocateIcon() {
        java.net.URL url = EdsmQueryTool.class.getResource(LOCATE_ICON_PATH);
        if (url != null) {
            ImageIcon raw = new ImageIcon(url);
            int w = raw.getIconWidth();
            int h = raw.getIconHeight();
            if (w > 0 && h > 0) {
                int size = LOCATE_ICON_SIZE;
                Image scaled = raw.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
                return new ImageIcon(scaled);
            }
            return raw;
        }
        return null;
    }

    // ============================================================
    // TABS
    // ============================================================

    private JPanel createSystemTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        systemTabSystemField = new JTextField(30);
        registerSystemNameField(systemTabSystemField);

        JTextField systemsField = new JTextField(30);

        JTextField xField = new JTextField(6);
        JTextField yField = new JTextField(6);
        JTextField zField = new JTextField(6);
        JTextField radiusField = new JTextField(6);

        sphereXField = xField;
        sphereYField = yField;
        sphereZField = zField;

        JButton getSystemButton = new JButton("Get System");
        JButton getSystemsButton = new JButton("Get Systems");
        JButton showSystemButton = new JButton("Show System (info + primary star)");
        JButton sphereSystemsButton = new JButton("Search Sphere");

        // ----- Single system -----
        JPanel singleSystemPanel = new JPanel();
        singleSystemPanel.setLayout(new BoxLayout(singleSystemPanel, BoxLayout.Y_AXIS));
        singleSystemPanel.setBorder(BorderFactory.createTitledBorder("Single system by name"));

        singleSystemPanel.add(makeLabeledWithLocate(systemTabSystemField, "System name:"));
        singleSystemPanel.add(Box.createVerticalStrut(6));

        JPanel singleButtons = new JPanel();
        singleButtons.setLayout(new BoxLayout(singleButtons, BoxLayout.X_AXIS));
        singleButtons.add(getSystemButton);
        singleButtons.add(Box.createHorizontalStrut(8));
        singleButtons.add(showSystemButton);
        singleButtons.add(Box.createHorizontalGlue());

        singleSystemPanel.add(singleButtons);

        // ----- Multiple systems -----
        JPanel multiSystemPanel = new JPanel();
        multiSystemPanel.setLayout(new BoxLayout(multiSystemPanel, BoxLayout.Y_AXIS));
        multiSystemPanel.setBorder(BorderFactory.createTitledBorder("Multiple systems"));

        multiSystemPanel.add(makeLabeled(systemsField, "System names (comma-separated):"));
        multiSystemPanel.add(Box.createVerticalStrut(6));

        JPanel multiButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        multiButtons.add(getSystemsButton);
        multiSystemPanel.add(multiButtons);

        // ----- Sphere search -----
        JPanel spherePanel = new JPanel();
        spherePanel.setLayout(new BoxLayout(spherePanel, BoxLayout.Y_AXIS));
        spherePanel.setBorder(BorderFactory.createTitledBorder("Sphere search (around system)"));

        JPanel coordsPanel = new JPanel();
        coordsPanel.setLayout(new BoxLayout(coordsPanel, BoxLayout.X_AXIS));
        coordsPanel.add(new JLabel("X:"));
        coordsPanel.add(xField);
        coordsPanel.add(Box.createHorizontalStrut(4));
        coordsPanel.add(new JLabel("Y:"));
        coordsPanel.add(yField);
        coordsPanel.add(Box.createHorizontalStrut(4));
        coordsPanel.add(new JLabel("Z:"));
        coordsPanel.add(zField);
        coordsPanel.add(Box.createHorizontalStrut(8));
        coordsPanel.add(new JLabel("Radius (ly):"));
        coordsPanel.add(radiusField);

        spherePanel.add(coordsPanel);
        spherePanel.add(Box.createVerticalStrut(6));

        JPanel sphereButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        sphereButtons.add(sphereSystemsButton);
        spherePanel.add(sphereButtons);

        spherePanel.add(sphereButtons);

        // lock spherePanel so it doesn't grow vertically
        Dimension spherePref = spherePanel.getPreferredSize();
        spherePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, spherePref.height));

     
        panel.add(singleSystemPanel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(multiSystemPanel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(spherePanel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(systemOutputPanel);

        // ----- Actions -----
        getSystemButton.addActionListener(e -> {
            String name = systemTabSystemField.getText().trim();
            if (name.isEmpty()) {
                appendOutput(systemOutputPanel, "Please enter a system name.\n");
                return;
            }
            setGlobalSystemName(name);
            runQueryAsync(systemOutputPanel, "getSystem(" + name + ")", () -> {
                SystemResponse resp = client.getSystem(name);
                String json = toJsonOrMessage(resp);
                SwingUtilities.invokeLater(() -> updateSphereCoordsFromJson(json));
                return json;
            });
        });

        showSystemButton.addActionListener(e -> {
            String name = systemTabSystemField.getText().trim();
            if (name.isEmpty()) {
                appendOutput(systemOutputPanel, "Please enter a system name.\n");
                return;
            }
            setGlobalSystemName(name);
            runQueryAsync(systemOutputPanel, "showSystem(" + name + ")", () -> {
                ShowSystemResponse resp = client.showSystem(name);
                String json = toJsonOrMessage(resp);
                SwingUtilities.invokeLater(() -> updateSphereCoordsFromJson(json));
                return json;
            });
        });

        getSystemsButton.addActionListener(e -> {
            String names = systemsField.getText().trim();
            if (names.isEmpty()) {
                appendOutput(systemOutputPanel, "Please enter one or more system names.\n");
                return;
            }
            runQueryAsync(systemOutputPanel, "getSystems(" + names + ")", () -> {
                String[] parts = Arrays.stream(names.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toArray(String[]::new);
                if (parts.length == 0) {
                    return "(no valid system names provided)";
                }
                SystemResponse[] resp = client.getSystems(parts);
                return toJsonOrMessage(resp);
            });
        });

        sphereSystemsButton.addActionListener(e -> {
            String rText = radiusField.getText().trim();
            if (rText.isEmpty()) {
                appendOutput(systemOutputPanel, "Please enter a radius.\n");
                return;
            }

            int radius;
            try {
                radius = Integer.parseInt(rText);
            } catch (NumberFormatException ex) {
                appendOutput(systemOutputPanel, "Invalid number format for radius.\n");
                return;
            }

            String xText = sphereXField.getText().trim();
            String yText = sphereYField.getText().trim();
            String zText = sphereZField.getText().trim();
            boolean hasCoords = !xText.isEmpty() && !yText.isEmpty() && !zText.isEmpty();

            if (hasCoords) {
                try {
                    double x = Double.parseDouble(xText);
                    double y = Double.parseDouble(yText);
                    double z = Double.parseDouble(zText);

                    final String preferredName = systemTabSystemField.getText().trim();
                    runQueryAsync(systemOutputPanel,
                            "sphereSystems(" + x + "," + y + "," + z + "," + radius + ")",
                            () -> {
                                SphereSystemsResponse[] resp = client.sphereSystems(x, y, z, radius, preferredName.isEmpty() ? null : preferredName);
                                return toJsonOrMessage(resp);
                            });
                } catch (NumberFormatException ex) {
                    appendOutput(systemOutputPanel, "Invalid number format for X, Y, or Z coordinates.\n");
                }
                return;
            }

            // No coords: use system name from "System name" field if present (e.g. after Locate)
            String systemName = systemTabSystemField.getText().trim();
            if (!systemName.isEmpty()) {
                final String nameForQuery = systemName;
                runQueryAsync(systemOutputPanel,
                        "sphereSystems(systemName=\"" + nameForQuery + "\", radius=" + radius + ")",
                        () -> {
                            SphereSystemsResponse[] resp = client.sphereSystems(nameForQuery, radius);
                            return toJsonOrMessage(resp);
                        });
                return;
            }

            appendOutput(systemOutputPanel,
                    "Please enter X, Y, and Z coordinates, or a system name above (e.g. use Locate to fill from EDSM).\n");
        });

        return panel;
    }

    private JPanel createBodiesTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        bodiesTabSystemField = new JTextField(30);
        registerSystemNameField(bodiesTabSystemField);

        JTextField systemIdField = new JTextField(20);

        JButton showBodiesByNameButton = new JButton("Show Bodies");
        JButton showBodiesByIdButton = new JButton("Show Bodies (by ID)");

        JPanel byNamePanel = new JPanel();
        byNamePanel.setLayout(new BoxLayout(byNamePanel, BoxLayout.Y_AXIS));
        byNamePanel.setBorder(BorderFactory.createTitledBorder("Bodies by system name"));

        byNamePanel.add(makeLabeledWithLocate(bodiesTabSystemField, "System name:"));
        byNamePanel.add(Box.createVerticalStrut(6));
        JPanel bodiesByNameButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        bodiesByNameButtons.add(showBodiesByNameButton);
        byNamePanel.add(bodiesByNameButtons);

        JPanel byIdPanel = new JPanel();
        byIdPanel.setLayout(new BoxLayout(byIdPanel, BoxLayout.Y_AXIS));
        byIdPanel.setBorder(BorderFactory.createTitledBorder("Bodies by system ID"));

        byIdPanel.add(makeLabeled(systemIdField, "System ID:"));
        byIdPanel.add(Box.createVerticalStrut(6));
        JPanel bodiesByIdButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        bodiesByIdButtons.add(showBodiesByIdButton);
        byIdPanel.add(bodiesByIdButtons);

        panel.add(byNamePanel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(byIdPanel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(bodiesOutputPanel);

        showBodiesByNameButton.addActionListener(e -> {
            String name = bodiesTabSystemField.getText().trim();
            if (name.isEmpty()) {
                appendOutput(bodiesOutputPanel, "Please enter a system name.\n");
                return;
            }
            setGlobalSystemName(name);
            runQueryAsync(bodiesOutputPanel, "showBodies(systemName=" + name + ")", () -> {
                BodiesResponse resp = client.showBodies(name);
                return toJsonOrMessage(resp);
            });
        });

        showBodiesByIdButton.addActionListener(e -> {
            String idText = systemIdField.getText().trim();
            if (idText.isEmpty()) {
                appendOutput(bodiesOutputPanel, "Please enter a system ID.\n");
                return;
            }
            try {
                long id = Long.parseLong(idText);
                runQueryAsync(bodiesOutputPanel, "showBodies(systemId=" + id + ")", () -> {
                    BodiesResponse resp = client.showBodies(id);
                    return toJsonOrMessage(resp);
                });
            } catch (NumberFormatException ex) {
                appendOutput(bodiesOutputPanel, "Invalid system ID (must be a number).\n");
            }
        });

        return panel;
    }

    private JPanel createTrafficTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        trafficTabSystemField = new JTextField(30);
        registerSystemNameField(trafficTabSystemField);

        JButton trafficButton = new JButton("System Traffic");
        JButton deathsButton = new JButton("System Deaths");
        JButton stationsButton = new JButton("System Stations");
        JButton logsButton = new JButton("System Logs");

        JPanel activityPanel = new JPanel();
        activityPanel.setLayout(new BoxLayout(activityPanel, BoxLayout.Y_AXIS));
        activityPanel.setBorder(BorderFactory.createTitledBorder("System activity"));

        activityPanel.add(makeLabeledWithLocate(trafficTabSystemField, "System name:"));
        activityPanel.add(Box.createVerticalStrut(6));

        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        buttonRow.add(trafficButton);
        buttonRow.add(Box.createHorizontalStrut(8));
        buttonRow.add(deathsButton);
        buttonRow.add(Box.createHorizontalStrut(16));
        buttonRow.add(stationsButton);
        buttonRow.add(Box.createHorizontalStrut(8));
        buttonRow.add(logsButton);
        buttonRow.add(Box.createHorizontalGlue());

        activityPanel.add(buttonRow);

        panel.add(activityPanel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(trafficOutputPanel);

        trafficButton.addActionListener(e -> {
            String name = trafficTabSystemField.getText().trim();
            if (name.isEmpty()) {
                appendOutput(trafficOutputPanel, "Please enter a system name.\n");
                return;
            }
            setGlobalSystemName(name);
            runQueryAsync(trafficOutputPanel, "showTraffic(" + name + ")", () -> {
                TrafficResponse resp = client.showTraffic(name);
                return toJsonOrMessage(resp);
            });
        });

        deathsButton.addActionListener(e -> {
            String name = trafficTabSystemField.getText().trim();
            if (name.isEmpty()) {
                appendOutput(trafficOutputPanel, "Please enter a system name.\n");
                return;
            }
            setGlobalSystemName(name);
            runQueryAsync(trafficOutputPanel, "showDeaths(" + name + ")", () -> {
                DeathsResponse resp = client.showDeaths(name);
                return toJsonOrMessage(resp);
            });
        });

        stationsButton.addActionListener(e -> {
            String name = trafficTabSystemField.getText().trim();
            if (name.isEmpty()) {
                appendOutput(trafficOutputPanel, "Please enter a system name.\n");
                return;
            }
            setGlobalSystemName(name);
            runQueryAsync(trafficOutputPanel, "getSystemStations(" + name + ")", () -> {
                SystemStationsResponse resp = client.getSystemStations(name);
                return toJsonOrMessage(resp);
            });
        });

        logsButton.addActionListener(e -> {
            String name = trafficTabSystemField.getText().trim();
            if (name.isEmpty()) {
                appendOutput(trafficOutputPanel, "Please enter a system name.\n");
                return;
            }

            if (!ensureCommanderPrefs()) {
                appendOutput(trafficOutputPanel, "Commander preferences not set; cannot query system logs.\n");
                return;
            }

            String[] creds = loadCommanderPrefs();
            String apiKey = creds[0];
            String commanderName = creds[1];

            setGlobalSystemName(name);
            runQueryAsync(trafficOutputPanel, "systemLogs(" + name + ")", () -> {
                LogsResponse resp = client.systemLogs(apiKey, commanderName, name);
                return toJsonOrMessage(resp);
            });
        });

        return panel;
    }

    private JPanel createCommanderTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JButton prefsButton = new JButton("EDSM Preferences…");
        JButton cmdrLogsButton = new JButton("Get Commander Logs");
        JButton cmdrLastPosButton = new JButton("Get Commander Last Position");
        JButton cmdrRanksButton = new JButton("Get Commander Ranks/Stats");
        JButton cmdrCreditsButton = new JButton("Get Commander Credits");

        JPanel credsPanel = new JPanel();
        credsPanel.setLayout(new BoxLayout(credsPanel, BoxLayout.Y_AXIS));
        credsPanel.setBorder(BorderFactory.createTitledBorder("Credentials"));

        JLabel hintLabel = new JLabel("Set your EDSM API key and commander name used for all commander lookups.");
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.ITALIC, 11f));

        credsPanel.add(hintLabel);
        credsPanel.add(Box.createVerticalStrut(4));
        credsPanel.add(prefsButton);

        JPanel queriesPanel = new JPanel();
        queriesPanel.setLayout(new BoxLayout(queriesPanel, BoxLayout.Y_AXIS));
        queriesPanel.setBorder(BorderFactory.createTitledBorder("Commander queries"));

        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.add(cmdrLogsButton);
        row.add(Box.createHorizontalStrut(8));
        row.add(cmdrLastPosButton);
        row.add(Box.createHorizontalStrut(16));
        row.add(cmdrRanksButton);
        row.add(Box.createHorizontalStrut(8));
        row.add(cmdrCreditsButton);
        row.add(Box.createHorizontalGlue());

        queriesPanel.add(row);

        panel.add(credsPanel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(queriesPanel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(commanderOutputPanel);

        prefsButton.addActionListener(e -> showCommanderPreferencesDialog());

        cmdrLogsButton.addActionListener(e -> {
            if (!ensureCommanderPrefs()) {
                appendOutput(commanderOutputPanel, "Commander preferences not set.\n");
                return;
            }
            String[] creds = loadCommanderPrefs();
            String apiKey = creds[0];
            String commanderName = creds[1];

            runQueryAsync(commanderOutputPanel, "getCmdrLogs()", () -> {
                LogsResponse resp = client.getCmdrLogs(apiKey, commanderName);
                return toJsonOrMessage(resp);
            });
        });

        cmdrLastPosButton.addActionListener(e -> {
            if (!ensureCommanderPrefs()) {
                appendOutput(commanderOutputPanel, "Commander preferences not set.\n");
                return;
            }
            String[] creds = loadCommanderPrefs();
            String apiKey = creds[0];
            String commanderName = creds[1];

            runQueryAsync(commanderOutputPanel, "getCmdrLastPosition()", () -> {
                CmdrLastPositionResponse resp = client.getCmdrLastPosition(apiKey, commanderName);
                if (resp != null && resp.getSystem() != null && !resp.getSystem().isEmpty()) {
                    String sys = resp.getSystem();
                    SwingUtilities.invokeLater(() -> setGlobalSystemName(sys));
                }
                return toJsonOrMessage(resp);
            });
        });

        cmdrRanksButton.addActionListener(e -> {
            if (!ensureCommanderPrefs()) {
                appendOutput(commanderOutputPanel, "Commander preferences not set.\n");
                return;
            }
            String[] creds = loadCommanderPrefs();
            String apiKey = creds[0];
            String commanderName = creds[1];

            runQueryAsync(commanderOutputPanel, "getCmdrRanks()", () -> {
                CmdrRanksResponse resp = client.getCmdrRanks(apiKey, commanderName);
                return toJsonOrMessage(resp);
            });
        });

        cmdrCreditsButton.addActionListener(e -> {
            if (!ensureCommanderPrefs()) {
                appendOutput(commanderOutputPanel, "Commander preferences not set.\n");
                return;
            }
            String[] creds = loadCommanderPrefs();
            String apiKey = creds[0];
            String commanderName = creds[1];

            runQueryAsync(commanderOutputPanel, "getCmdrCredits()", () -> {
                CmdrCreditsResponse resp = client.getCmdrCredits(apiKey, commanderName);
                return toJsonOrMessage(resp);
            });
        });

        return panel;
    }

    // ============================================================
    // STARTUP COMMANDER SYSTEM
    // ============================================================

    private void initCommanderSystemAtStartup() {
        SwingUtilities.invokeLater(() -> {
            if (!ensureCommanderPrefs()) {
                appendOutput(systemOutputPanel, "Commander preferences not set; cannot auto-populate system at startup.\n");
                return;
            }
            String[] creds = loadCommanderPrefs();
            String apiKey = creds[0];
            String commanderName = creds[1];

            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() {
                    try {
                        CmdrLastPositionResponse resp = client.getCmdrLastPosition(apiKey, commanderName);
                        if (resp != null) {
                            return resp.getSystem();
                        }
                    } catch (IOException | InterruptedException ex) {
                        return null;
                    }
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        String system = get();
                        if (system != null && !system.isEmpty()) {
                            appendOutput(systemOutputPanel, "Startup: commander last known system is " + system + "\n");
                            setGlobalSystemName(system);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }.execute();
        });
    }

    // ============================================================
    // PREFERENCES
    // ============================================================

    private boolean ensureCommanderPrefs() {
        String[] creds = loadCommanderPrefs();
        String apiKey = creds[0];
        String commanderName = creds[1];

        if (apiKey.isEmpty() || commanderName.isEmpty()) {
            showCommanderPreferencesDialog();
            creds = loadCommanderPrefs();
            apiKey = creds[0];
            commanderName = creds[1];
        }

        return !apiKey.isEmpty() && !commanderName.isEmpty();
    }

    private String[] loadCommanderPrefs() {
        Preferences prefs = Preferences.userNodeForPackage(EdsmQueryTool.class);
        String apiKey = prefs.get(PREF_KEY_EDSM_API, "").trim();
        String commanderName = prefs.get(PREF_KEY_EDSM_CMDR, "").trim();
        return new String[]{apiKey, commanderName};
    }

    private void saveCommanderPrefs(String apiKey, String commanderName) {
        Preferences prefs = Preferences.userNodeForPackage(EdsmQueryTool.class);
        if (apiKey != null) {
            prefs.put(PREF_KEY_EDSM_API, apiKey.trim());
        }
        if (commanderName != null) {
            prefs.put(PREF_KEY_EDSM_CMDR, commanderName.trim());
        }
    }

    private void updateCommanderStatusLabel() {
        String[] creds = loadCommanderPrefs();
        String apiKey = creds[0];
        String commanderName = creds[1];

        String statusText;
        if (commanderName.isEmpty() && apiKey.isEmpty()) {
            statusText = "Commander: not configured";
        } else if (commanderName.isEmpty()) {
            statusText = "Commander: (name missing)";
        } else if (apiKey.isEmpty()) {
            statusText = "Commander: " + commanderName + " (API key missing)";
        } else {
            statusText = "Commander: " + commanderName;
        }

        commanderStatusLabel.setText(statusText);
    }

    private void showCommanderPreferencesDialog() {
        String[] creds = loadCommanderPrefs();
        String currentKey = creds[0];
        String currentCmdr = creds[1];

        JTextField apiField = new JTextField(currentKey, 30);
        JTextField cmdrField = new JTextField(currentCmdr, 30);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(makeLabeled(apiField, "EDSM API Key:"));
        panel.add(Box.createVerticalStrut(8));
        panel.add(makeLabeled(cmdrField, "Commander Name:"));

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "EDSM Preferences",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            saveCommanderPrefs(apiField.getText(), cmdrField.getText());
            updateCommanderStatusLabel();
        }
    }

    // ============================================================
    // UI HELPERS
    // ============================================================

    private JComponent makeLabeled(JTextField field, String labelText) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        JLabel label = new JLabel(labelText);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
        p.add(label);

        Dimension pref = field.getPreferredSize();
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));

        p.add(field);
        return p;
    }

    private JComponent makeLabeledWithLocate(JTextField field, String labelText) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));

        JLabel label = new JLabel(labelText);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));

        JButton locateButton;
        if (LOCATE_ICON != null) {
            locateButton = new JButton(LOCATE_ICON);
            locateButton.setBorderPainted(false);
            locateButton.setContentAreaFilled(false);
            locateButton.setFocusPainted(false);

            Dimension iconSize = new Dimension(LOCATE_ICON_SIZE + 4, LOCATE_ICON_SIZE + 4);
            locateButton.setPreferredSize(iconSize);
            locateButton.setMaximumSize(iconSize);
            locateButton.setMinimumSize(iconSize);
        } else {
            locateButton = new JButton("Locate");
        }

        locateButton.setToolTipText("Fill with current commander system from EDSM");

        Dimension pref = field.getPreferredSize();
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));

        locateButton.addActionListener(e -> {
            if (!ensureCommanderPrefs()) {
                appendOutput(systemOutputPanel, "Commander preferences not set; cannot locate current system.\n");
                return;
            }

            String[] creds = loadCommanderPrefs();
            String apiKey = creds[0];
            String commanderName = creds[1];

            runQueryAsync(systemOutputPanel, "getCmdrLastPosition() for locate", () -> {
                CmdrLastPositionResponse resp = client.getCmdrLastPosition(apiKey, commanderName);
                if (resp == null) {
                    return "(no result from getCmdrLastPosition)";
                }
                String system = resp.getSystem();
                if (system != null && !system.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        setGlobalSystemName(system);
                        populateSphereCoordsFromSystemName(system);
                    });
                    return "Located commander in system: " + system;
                } else {
                    return "(getCmdrLastPosition returned no system)";
                }
            });
        });

        p.add(label);
        p.add(field);
        p.add(Box.createHorizontalStrut(4));
        p.add(locateButton);

        return p;
    }

    private void registerSystemNameField(JTextField field) {
        systemNameFields.add(field);

        Dimension pref = field.getPreferredSize();
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));

        field.addActionListener(e -> {
            String text = field.getText().trim();
            if (!text.isEmpty()) {
                setGlobalSystemName(text);
            }
        });

        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                handleSystemNameTyping(field);
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                handleSystemNameTyping(field);
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                handleSystemNameTyping(field);
            }
        });

        InputMap im = field.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = field.getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "autoCompleteHide");
        am.put("autoCompleteHide", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                hideAutoComplete();
            }
        });

        im.put(KeyStroke.getKeyStroke("DOWN"), "autoCompleteDown");
        am.put("autoCompleteDown", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (autoCompletePopup == null) {
                    return;
                }
                if (!autoCompletePopup.isVisible()) {
                    return;
                }
                if (autoCompleteList == null) {
                    return;
                }
                if (autoCompleteList.getModel().getSize() == 0) {
                    return;
                }

                autoCompleteTargetField = field;
                autoCompleteOriginalText = field.getText();

                autoCompleteList.setSelectedIndex(0);
                autoCompleteList.ensureIndexIsVisible(0);
                autoCompleteList.requestFocusInWindow();
            }
        });
    }

    private void handleSystemNameTyping(JTextField field) {
        if (suppressAutoCompleteEvents) {
            return;
        }

        String text = field.getText().trim();
        if (text.length() < 3) {
            pendingAutoCompletePrefix = null;
            hideAutoComplete();
            return;
        }

        autoCompleteTargetField = field;
        pendingAutoCompletePrefix = text;

        if (autoCompleteTimer == null) {
            autoCompleteTimer = new Timer(250, e -> runAutoComplete());
            autoCompleteTimer.setRepeats(false);
        }
        autoCompleteTimer.restart();
    }

    private void runAutoComplete() {
        final JTextField targetField = autoCompleteTargetField;
        final String prefix = pendingAutoCompletePrefix;

        if (targetField == null || prefix == null || prefix.length() < 3) {
            return;
        }

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                try {
                    return fetchSystemNameSuggestions(prefix);
                } catch (Exception e) {
                    return Collections.emptyList();
                }
            }

            @Override
            protected void done() {
                try {
                    List<String> suggestions = get();
                    if (prefix.equals(pendingAutoCompletePrefix)) {
                        showAutoCompleteSuggestions(targetField, suggestions);
                    }
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    private List<String> fetchSystemNameSuggestions(String prefix) throws Exception {
        String encoded = java.net.URLEncoder.encode(prefix, StandardCharsets.UTF_8.name());
        String urlStr = "https://www.edsm.net/api-v1/systems?systemName=" + encoded;

        java.net.HttpURLConnection conn = null;
        java.io.InputStream is = null;
        try {
            java.net.URL url = new java.net.URL(urlStr);
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int status = conn.getResponseCode();
            if (status >= 200 && status < 300) {
                is = conn.getInputStream();
            } else {
                is = conn.getErrorStream();
            }

            if (is == null) {
                return Collections.emptyList();
            }

            StringBuilder sb = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            String body = sb.toString().trim();
            if (body.isEmpty() || body.equals("[]")) {
                return Collections.emptyList();
            }

            AutoCompleteSystem[] systems = gson.fromJson(body, AutoCompleteSystem[].class);
            if (systems == null || systems.length == 0) {
                return Collections.emptyList();
            }

            List<String> names = new ArrayList<>();
            for (AutoCompleteSystem s : systems) {
                if (s == null || s.name == null || s.name.isEmpty()) {
                    continue;
                }
                names.add(s.name);
            }

            return names.stream()
                    .distinct()
                    .limit(20)
                    .collect(Collectors.toList());
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception ignored) {
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void showAutoCompleteSuggestions(JTextField field, List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            hideAutoComplete();
            return;
        }

        if (autoCompletePopup == null) {
            autoCompletePopup = new JPopupMenu();
            autoCompletePopup.setFocusable(false);

            autoCompleteList = new JList<>();
            autoCompleteList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            autoCompleteList.setFocusable(true);

            autoCompleteList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 1 && SwingUtilities.isLeftMouseButton(e)) {
                        applyAutoCompleteSelection();
                    }
                }
            });

            JScrollPane sp = new JScrollPane(autoCompleteList);
            sp.setBorder(null);
            sp.setFocusable(false);
            autoCompletePopup.add(sp);

            InputMap lim = autoCompleteList.getInputMap(JComponent.WHEN_FOCUSED);
            ActionMap lam = autoCompleteList.getActionMap();

            lim.put(KeyStroke.getKeyStroke("ENTER"), "autoCompleteAccept");
            lam.put("autoCompleteAccept", new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    applyAutoCompleteSelection();
                }
            });

            lim.put(KeyStroke.getKeyStroke("ESCAPE"), "autoCompleteEscape");
            lam.put("autoCompleteEscape", new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    hideAutoComplete();
                    if (autoCompleteTargetField != null) {
                        autoCompleteTargetField.requestFocusInWindow();
                        autoCompleteTargetField.setCaretPosition(
                                autoCompleteTargetField.getText().length()
                        );
                    }
                }
            });

            lim.put(KeyStroke.getKeyStroke("UP"), "autoCompleteUp");
            lam.put("autoCompleteUp", new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    int idx = autoCompleteList.getSelectedIndex();
                    if (idx > 0) {
                        int newIdx = idx - 1;
                        autoCompleteList.setSelectedIndex(newIdx);
                        autoCompleteList.ensureIndexIsVisible(newIdx);
                    } else {
                        if (autoCompleteTargetField != null) {
                            String restore = autoCompleteOriginalText;
                            if (restore == null) {
                                restore = autoCompleteTargetField.getText();
                            }
                            autoCompleteTargetField.setText(restore);
                            autoCompleteTargetField.requestFocusInWindow();
                            autoCompleteTargetField.setCaretPosition(restore.length());
                        }
                        autoCompleteOriginalText = null;
                        hideAutoComplete();
                    }
                }
            });

            lim.put(KeyStroke.getKeyStroke("DOWN"), "autoCompleteDownInList");
            lam.put("autoCompleteDownInList", new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    int size = autoCompleteList.getModel().getSize();
                    if (size == 0) {
                        return;
                    }
                    int idx = autoCompleteList.getSelectedIndex();
                    if (idx < 0) {
                        idx = 0;
                    }
                    if (idx < size - 1) {
                        int newIdx = idx + 1;
                        autoCompleteList.setSelectedIndex(newIdx);
                        autoCompleteList.ensureIndexIsVisible(newIdx);
                    }
                }
            });
        }

        autoCompleteList.setListData(suggestions.toArray(new String[0]));
        autoCompleteList.setVisibleRowCount(Math.min(8, suggestions.size()));

        int width = Math.max(field.getWidth(), 200);
        int listHeight = autoCompleteList.getPreferredScrollableViewportSize().height;
        if (listHeight <= 0) {
            listHeight = autoCompleteList.getPreferredSize().height;
        }
        if (listHeight <= 0) {
            listHeight = 120;
        }

        autoCompletePopup.setPopupSize(width, listHeight + 4);
        autoCompletePopup.show(field, 0, field.getHeight());

        field.requestFocusInWindow();
        field.setCaretPosition(field.getText().length());
    }

    private void applyAutoCompleteSelection() {
        if (autoCompleteTargetField == null || autoCompleteList == null) {
            return;
        }
        String selected = autoCompleteList.getSelectedValue();
        if (selected == null || selected.isEmpty()) {
            return;
        }

        // Stop any pending autocomplete run and hide popup
        if (autoCompleteTimer != null) {
            autoCompleteTimer.stop();
        }
        pendingAutoCompletePrefix = null;
        hideAutoComplete();

        // This will update all system fields, but with events suppressed
        setGlobalSystemName(selected);
    }

    private void hideAutoComplete() {
        if (autoCompletePopup != null && autoCompletePopup.isVisible()) {
            autoCompletePopup.setVisible(false);
        }
    }

    private void setGlobalSystemName(String name) {
        if (name == null) {
            return;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        currentSystemName = trimmed;

        suppressAutoCompleteEvents = true;
        try {
            for (JTextField f : systemNameFields) {
                if (f != null) {
                    f.setText(trimmed);
                }
            }
        } finally {
            suppressAutoCompleteEvents = false;
        }
    }

    private void populateSphereCoordsFromSystemName(String systemName) {
        if (systemName == null) {
            return;
        }
        String trimmed = systemName.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        // These fields exist only on the System tab; if the tab hasn't been built yet, do nothing.
        if (sphereXField == null && sphereYField == null && sphereZField == null) {
            return;
        }

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    SystemResponse resp = client.getSystem(trimmed);
                    return toJsonOrMessage(resp);
                } catch (Exception ex) {
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    String json = get();
                    if (json != null && !json.isBlank()) {
                        updateSphereCoordsFromJson(json);
                    }
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    private void updateSphereCoordsFromJson(String json) {
        if (json == null) {
            return;
        }
        json = json.trim();
        if (json.isEmpty()) {
            return;
        }

        char c = json.charAt(0);
        if (c != '{' && c != '[') {
            return;
        }

        try {
            JsonElement root = JsonParser.parseString(json);
            JsonObject obj = null;

            if (root.isJsonObject()) {
                obj = root.getAsJsonObject();
            } else if (root.isJsonArray() && root.getAsJsonArray().size() > 0) {
                JsonElement first = root.getAsJsonArray().get(0);
                if (first.isJsonObject()) {
                    obj = first.getAsJsonObject();
                }
            }

            if (obj == null) {
                return;
            }

            JsonObject coords = obj.getAsJsonObject("coords");
            if (coords == null) {
                return;
            }

            if (sphereXField != null && coords.has("x")) {
                sphereXField.setText(coords.get("x").getAsString());
            }
            if (sphereYField != null && coords.has("y")) {
                sphereYField.setText(coords.get("y").getAsString());
            }
            if (sphereZField != null && coords.has("z")) {
                sphereZField.setText(coords.get("z").getAsString());
            }
        } catch (Exception ignored) {
        }
    }

    // ============================================================
    // ASYNC + OUTPUT
    // ============================================================

    private void runQueryAsync(TabOutputPanel output, String label, QuerySupplier supplier) {
        output.clear();
        output.appendText("=== " + label + " ===\n");
        output.appendText("Running query...\n");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    return supplier.get();
                } catch (Exception ex) {
                    return "ERROR: " + ex.getClass().getSimpleName() + " - " + ex.getMessage();
                }
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    if (result == null) {
                        result = "(no result / empty response)";
                    }
                    output.appendText(result + "\n\n");
                    output.updateTableFromText(result);
                    output.appendText("Query complete.\n");
                } catch (Exception ex) {
                    output.appendText("ERROR retrieving result: " + ex.getMessage() + "\n");
                    output.appendText("Query complete.\n");
                }
            }
        }.execute();
    }

    private String toJsonOrMessage(Object obj) {
        // Prefer the raw JSON from the last EDSM call
        String raw = client.getLastRawJson();

        if (raw != null && !raw.isEmpty()) {
            try {
                // Pretty-print using Gson
                JsonElement tree = JsonParser.parseString(raw);
                return gson.toJson(tree);  // gson is already configured with pretty printing in this tool
            } catch (Exception ex) {
                // If parsing fails (rare), fall back to raw text
                return raw;
            }
        }

        // Fallback when raw JSON isn't available
        if (obj == null) {
            return "(no result / empty response)";
        }

        try {
            JsonElement tree = gson.toJsonTree(obj);
            return gson.toJson(tree);
        } catch (Exception ex) {
            return obj.toString();
        }
    }
    
    private void appendOutput(TabOutputPanel panel, String text) {
        panel.appendText(text);
    }

    private interface QuerySupplier {
        String get() throws Exception;
    }

    // Used only for autocomplete parsing
    private static class AutoCompleteSystem {
        String name;
    }

    // ============================================================
    // TAB OUTPUT PANEL (TABLE/TEXT TOGGLE)
    // ============================================================

    // ============================================================
    // TAB OUTPUT PANEL (TABLE/TEXT TOGGLE + TYPE-AWARE TABLES)
    // ============================================================

    private class TabOutputPanel extends JPanel {

        private final JTextArea textArea;
        private final JTable table;
        private final DefaultTableModel tableModel;
        private final CardLayout cardLayout;
        private final JPanel cardPanel;
        private final JButton toggleButton;
        private boolean showingTable = true;

        TabOutputPanel() {
            super(new BorderLayout());
            setBorder(BorderFactory.createTitledBorder("Results"));

            toggleButton = new JButton("Show Text");
            toggleButton.addActionListener(e -> toggleView());

            JPanel top = new JPanel();
            top.setLayout(new BoxLayout(top, BoxLayout.X_AXIS));
            top.add(toggleButton);
            top.add(Box.createHorizontalGlue());

            textArea = new JTextArea();
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

            tableModel = new DefaultTableModel();
            table = new JTable(tableModel);
            table.setFillsViewportHeight(true);
            table.setAutoCreateRowSorter(true);

            cardLayout = new CardLayout();
            cardPanel = new JPanel(cardLayout);
            cardPanel.add(new JScrollPane(table), "TABLE");
            cardPanel.add(new JScrollPane(textArea), "TEXT");

            add(top, BorderLayout.NORTH);
            add(cardPanel, BorderLayout.CENTER);

            cardLayout.show(cardPanel, "TABLE");
            showingTable = true;
        }

        void toggleView() {
            if (showingTable) {
                cardLayout.show(cardPanel, "TEXT");
                toggleButton.setText("Show Table");
                showingTable = false;
            } else {
                cardLayout.show(cardPanel, "TABLE");
                toggleButton.setText("Show Text");
                showingTable = true;
            }
        }

        void showTable() {
            cardLayout.show(cardPanel, "TABLE");
            toggleButton.setText("Show Text");
            showingTable = true;
        }

        void clear() {
            textArea.setText("");
            tableModel.setRowCount(0);
            tableModel.setColumnCount(0);
        }

        void appendText(String t) {
            textArea.append(t);
            textArea.setCaretPosition(textArea.getDocument().getLength());
        }

        /**
         * Entry point from runQueryAsync: we get the raw JSON/text string
         * and choose an appropriate table representation.
         */
        void updateTableFromText(String text) {
            if (text == null) {
                setSimpleTable("Value", Collections.singletonList(new Object[]{"(null)"}));
                return;
            }
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                setSimpleTable("Value", Collections.emptyList());
                return;
            }

            try {
                JsonElement root = JsonParser.parseString(trimmed);

                if (root.isJsonArray()) {
                    JsonArray arr = root.getAsJsonArray();
                    updateTableFromArraySpecial(arr);
                    return;
                }

                if (root.isJsonObject()) {
                    JsonObject obj = root.getAsJsonObject();

                    // Bodies: show only the "bodies" array, one body per row
                    if (obj.has("bodies") && obj.get("bodies").isJsonArray()) {
                        updateTableFromBodies(obj.getAsJsonArray("bodies"));
                        return;
                    }

                    // Stations: show only the "stations" array, one station per row
                    if (obj.has("stations") && obj.get("stations").isJsonArray()) {
                        updateTableFromStations(obj.getAsJsonArray("stations"));
                        return;
                    }

                    // Single system (getSystem / showSystem):
                    // object with id, name, coords
                    if (obj.has("id") && obj.has("name") && obj.has("coords")) {
                        updateTableFromSingleSystem(obj);
                        return;
                    }

                    // Fallback: generic "Field / Value" view
                    updateTableFromObjectGeneric(obj);
                    return;
                }

                // Scalar or something else: put whole thing in one column
                setSimpleTable("Value", Collections.singletonList(new Object[]{root.toString()}));
            } catch (Exception ex) {
                // Not JSON, or parse error: just show raw text
                setSimpleTable("Value", Collections.singletonList(new Object[]{text}));
            }
            
        }

        /**
         * Arrays: special handling for sphere search results (systems with coords),
         * otherwise generic row-per-element.
         */
        private void updateTableFromArraySpecial(JsonArray arr) {
            if (arr.size() == 0) {
                setSimpleTable("Value", Collections.emptyList());
                return;
            }

            JsonElement first = arr.get(0);
            if (first.isJsonObject()) {
                JsonObject firstObj = first.getAsJsonObject();

                // Sphere search results: objects with coords (and often distance)
                if (firstObj.has("coords")) {
                    updateTableFromSphereSystems(arr);
                    return;
                }

                // Generic array-of-objects
                updateTableFromArrayOfObjectsGeneric(arr);
                return;
            }

            // Non-object elements: generic single "Value" column
            List<Object[]> rows = new ArrayList<>();
            for (JsonElement el : arr) {
                rows.add(new Object[]{el.toString()});
            }
            setSimpleTable("Value", rows);
        }

        /**
         * Single system: show columns id, name, coords (coords as x,y,z tuple).
         */
        private void updateTableFromSingleSystem(JsonObject obj) {
            String idVal = obj.has("id") ? safeJsonPrimitiveToString(obj.get("id")) : "";
            String nameVal = obj.has("name") ? safeJsonPrimitiveToString(obj.get("name")) : "";
            String coordsVal = formatCoordsTuple(obj.getAsJsonObject("coords"));

            List<String> columns = Arrays.asList("id", "name", "coords");
            List<Object[]> rows = Collections.singletonList(
                    new Object[]{idVal, nameVal, coordsVal}
            );

            setTable(columns, rows);
        }

        /**
         * Sphere search: one row per system, columns id, name, coords, distance.
         */
        private void updateTableFromSphereSystems(JsonArray arr) {
            List<String> columns = new ArrayList<>();
            columns.add("id");
            columns.add("name");
            columns.add("coords");
            columns.add("distance");

            List<Object[]> rows = new ArrayList<>();

            for (JsonElement el : arr) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject obj = el.getAsJsonObject();

                String idVal = obj.has("id") ? safeJsonPrimitiveToString(obj.get("id")) : "";
                String nameVal = obj.has("name") ? safeJsonPrimitiveToString(obj.get("name")) : "";
                String coordsVal = "";
                if (obj.has("coords") && obj.get("coords").isJsonObject()) {
                    coordsVal = formatCoordsTuple(obj.getAsJsonObject("coords"));
                }
                String distVal = obj.has("distance") ? safeJsonPrimitiveToString(obj.get("distance")) : "";

                rows.add(new Object[]{idVal, nameVal, coordsVal, distVal});
            }

            setTable(columns, rows);
        }

        /**
         * Bodies: array of body objects; columns are all keys except id64,
         * including id and name.
         */
        /**
         * Bodies: array of body objects; columns are all keys except
         * noisy/star-centric ones. Includes id and name, but:
         *  - name is shown without the system prefix (just "1", "1 a", etc.)
         *  - negative values like "No volcanism" or "false" are blanked.
         */
        private void updateTableFromBodies(JsonArray bodies) {
            if (bodies.size() == 0) {
                setSimpleTable("Value", Collections.emptyList());
                return;
            }

            // Collect columns, skipping noisy ones
            Set<String> columnsSet = new LinkedHashSet<>();
            for (JsonElement el : bodies) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject obj = el.getAsJsonObject();
                for (String key : obj.keySet()) {
                    if (BODIES_SKIP_COLUMNS.contains(key)) {
                        continue;
                    }
                    columnsSet.add(key);
                }
            }
            if (columnsSet.isEmpty()) {
                columnsSet.add("Value");
            }

            // Order: id, name, type, subType, distanceToArrival, then the rest alphabetically
            List<String> columns = orderBodyColumns(columnsSet);

            List<Object[]> rows = new ArrayList<>();

            for (JsonElement el : bodies) {
                if (!el.isJsonObject()) {
                    rows.add(new Object[]{el.toString()});
                    continue;
                }
                JsonObject obj = el.getAsJsonObject();

                // Grab type once per row so we can tell stars vs planets
                String typeVal = "";
                JsonElement typeEl = obj.get("type");
                if (typeEl != null && typeEl.isJsonPrimitive()) {
                    typeVal = typeEl.getAsString();
                }

                Object[] row = new Object[columns.size()];

                for (int i = 0; i < columns.size(); i++) {
                    String col = columns.get(i);
                    JsonElement v = obj.get(col);

                    String value;
                    if (v == null || v.isJsonNull()) {
                        value = "";
                    } else if (v.isJsonPrimitive()) {
                        value = v.getAsString();
                    } else {
                        value = v.toString();
                    }

                    // Strip system prefix from the "name" column
                    if ("name".equals(col) && currentSystemName != null && !currentSystemName.isEmpty()) {
                        String prefix = currentSystemName.trim();
                        if (value.startsWith(prefix)) {
                            value = value.substring(prefix.length()).trim();
                        }

                        // Star naming:
                        // - Primary star: short name ends up empty -> show "*"
                        // - Other stars keep their letter (A, B, C, ...)
                        if ("Star".equalsIgnoreCase(typeVal)) {
                            if (value.isEmpty()) {
                                value = "*";
                            }
                        }
                    }

                    // Hide "negative" values like "No volcanism", "false", "0"
                    value = normalizeBodyValue(col, value);

                    row[i] = value;
                }
                rows.add(row);
            }

            setTable(columns, rows);
        }

        /**
         * Stations: array of station objects; columns are all keys seen on stations.
         */
        private void updateTableFromStations(JsonArray stations) {
            if (stations.size() == 0) {
                setSimpleTable("Value", Collections.emptyList());
                return;
            }

            Set<String> columnsSet = new LinkedHashSet<>();
            for (JsonElement el : stations) {
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    for (String key : obj.keySet()) {
                        columnsSet.add(key);
                    }
                }
            }
            if (columnsSet.isEmpty()) {
                columnsSet.add("Value");
            }

            List<String> columns = new ArrayList<>(columnsSet);
            List<Object[]> rows = new ArrayList<>();

            for (JsonElement el : stations) {
                if (!el.isJsonObject()) {
                    rows.add(new Object[]{el.toString()});
                    continue;
                }
                JsonObject obj = el.getAsJsonObject();
                Object[] row = new Object[columns.size()];
                for (int i = 0; i < columns.size(); i++) {
                    String col = columns.get(i);
                    JsonElement v = obj.get(col);
                    row[i] = v == null ? "" :
                            v.isJsonPrimitive() ? v.getAsString() : v.toString();
                }
                rows.add(row);
            }

            setTable(columns, rows);
        }

        /**
         * Generic array-of-objects fallback.
         */
        private void updateTableFromArrayOfObjectsGeneric(JsonArray arr) {
            Set<String> columnsSet = new LinkedHashSet<>();
            for (JsonElement el : arr) {
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    for (String key : obj.keySet()) {
                        columnsSet.add(key);
                    }
                }
            }
            if (columnsSet.isEmpty()) {
                columnsSet.add("Value");
            }

            List<String> columns = new ArrayList<>(columnsSet);
            List<Object[]> rows = new ArrayList<>();

            for (JsonElement el : arr) {
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    Object[] row = new Object[columns.size()];
                    for (int i = 0; i < columns.size(); i++) {
                        String col = columns.get(i);
                        JsonElement v = obj.get(col);
                        row[i] = v == null ? "" :
                                v.isJsonPrimitive() ? v.getAsString() : v.toString();
                    }
                    rows.add(row);
                } else {
                    rows.add(new Object[]{el.toString()});
                }
            }

            setTable(columns, rows);
        }

        /**
         * Generic object: "Field / Value" two-column view.
         */
        private void updateTableFromObjectGeneric(JsonObject obj) {
            List<String> columns = Arrays.asList("Field", "Value");
            List<Object[]> rows = new ArrayList<>();

            for (String key : obj.keySet()) {
                JsonElement v = obj.get(key);
                String value;
                if (v == null || v.isJsonNull()) {
                    value = "";
                } else if (v.isJsonPrimitive()) {
                    value = v.getAsString();
                } else {
                    value = v.toString();
                }
                rows.add(new Object[]{key, value});
            }

            setTable(columns, rows);
        }

        private void setSimpleTable(String colName, List<Object[]> rows) {
            List<String> cols = Collections.singletonList(colName);
            setTable(cols, rows);
        }

        private void setTable(List<String> columns, List<Object[]> rows) {
            tableModel.setRowCount(0);
            tableModel.setColumnCount(0);

            for (String col : columns) {
                tableModel.addColumn(col);
            }

            for (Object[] row : rows) {
                tableModel.addRow(row);
            }
            
            // Apply numeric renderer to numeric columns
            DecimalAlignRenderer numericRenderer = new DecimalAlignRenderer();

            for (int col = 0; col < table.getColumnCount(); col++) {
                if (isNumericColumn(col)) {
                    table.getColumnModel().getColumn(col).setCellRenderer(numericRenderer);

                    // Sort using numeric comparator
                    TableRowSorter<?> sorter = (TableRowSorter<?>) table.getRowSorter();
                    sorter.setComparator(col, (a, b) -> {
                        try {
                            double da = Double.parseDouble(a.toString());
                            double db = Double.parseDouble(b.toString());
                            return Double.compare(da, db);
                        } catch (Exception ex) {
                            return a.toString().compareTo(b.toString());
                        }
                    });
                }
            }

            SwingUtilities.invokeLater(() -> UtilTable.autoSizeTableColumns(table));
        }
        private boolean isNumericColumn(int col) {
            // Look at first non-empty value in this column
            for (int row = 0; row < tableModel.getRowCount(); row++) {
                Object v = tableModel.getValueAt(row, col);
                if (v == null) {
                    continue;
                }
                String s = v.toString().trim();
                if (s.isEmpty()) {
                    continue;
                }

                // Numeric? integer or decimal
                if (s.matches("[-+]?[0-9]*\\.?[0-9]+")) {
                    return true;
                }

                // Non-numeric means column is not numeric
                return false;
            }
            return false;
        }

        private String safeJsonPrimitiveToString(JsonElement el) {
            if (el == null || el.isJsonNull()) {
                return "";
            }
            if (!el.isJsonPrimitive()) {
                return el.toString();
            }
            return el.getAsString();
        }

        private String formatCoordsTuple(JsonObject coords) {
            if (coords == null) {
                return "";
            }
            String x = coords.has("x") ? safeJsonPrimitiveToString(coords.get("x")) : "";
            String y = coords.has("y") ? safeJsonPrimitiveToString(coords.get("y")) : "";
            String z = coords.has("z") ? safeJsonPrimitiveToString(coords.get("z")) : "";
            return x + ", " + y + ", " + z;
        }
        
        /**
         * Renderer that right-aligns numbers and aligns decimals using monospaced font.
         */
        private class DecimalAlignRenderer extends DefaultTableCellRenderer {
            private final Font monoFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);

            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {

                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);

                if (value == null) {
                    setHorizontalAlignment(SwingConstants.RIGHT);
                    setFont(monoFont);
                    setText("");
                    return c;
                }

                String text = value.toString().trim();

                // Detect numbers (integer or decimal)
                if (text.matches("[-+]?[0-9]*\\.?[0-9]+")) {
                    setHorizontalAlignment(SwingConstants.RIGHT);
                    setFont(monoFont);
                } else {
                    setHorizontalAlignment(SwingConstants.LEFT);
                }

                return c;
            }
        }

    }

    // ============================================================
    // MAIN
    // ============================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            EdsmQueryTool tool = new EdsmQueryTool();
            tool.setVisible(true);
        });
    }
    /**
     * Order body columns so the most important are first.
     */
    private List<String> orderBodyColumns(Set<String> columnsSet) {
        List<String> cols = new ArrayList<>(columnsSet);

        cols.sort((a, b) -> {
            int pa = bodyColumnPriority(a);
            int pb = bodyColumnPriority(b);
            if (pa != pb) {
                return Integer.compare(pa, pb);
            }
            return a.compareToIgnoreCase(b);
        });

        return cols;
    }

    private int bodyColumnPriority(String col) {
        if ("id".equals(col)) {
            return 0;
        }
        if ("name".equals(col)) {
            return 1;
        }
        if ("type".equals(col)) {
            return 2;
        }
        if ("subType".equals(col)) {
            return 3;
        }
        if ("distanceToArrival".equals(col)) {
            return 4;
        }
        return 10;
    }

    /**
     * Blank out negative / uninteresting values:
     *  - "false", "0"
     *  - strings starting with "No " (e.g. "No volcanism")
     */
    /**
     * Normalize body values:
     *  - distanceToArrival: show integer if value is effectively whole
     *  - subType: strip trailing " body"
     *  - terraforming state: map to simple forms, hide "Not terraformable"
     *  - generic negatives: "false", "0", and "No ..." become blank
     */
    /**
     * Normalize body values:
     *  - distanceToArrival: show integer if value is effectively whole
     *  - gravity: round to 2 decimal places
     *  - earthMasses: round to 3 decimal places
     *  - subType: strip trailing " body"
     *  - terraforming state: map to simple forms, hide "Not terraformable"
     *  - generic negatives: "false", "0", and "No ..." become blank
     */
    /**
     * Normalize body values:
     *  - distanceToArrival: integer if whole
     *  - gravity: round to 2 decimals
     *  - earthMasses: round to 3 decimals
     *  - radius: round to nearest integer
     *  - subType: strip trailing " body"
     *  - terraforming: simplify (Candidate / Terraforming / Terraformed)
     *  - negatives: false, 0, "No X" → blank
     */
    private String normalizeBodyValue(String col, String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return "";
        }

        String lower = v.toLowerCase(Locale.ROOT);

        // --- Terraforming state simplification ---
        if ("terraformingState".equals(col) || "terraforming".equals(col)) {
            if ("not terraformable".equals(lower)) {
                return "";
            }
            if ("candidate for terraforming".equals(lower)) {
                return "Candidate";
            }
            if ("terraforming".equals(lower)) {
                return "Terraforming";
            }
            if ("terraformed".equals(lower)) {
                return "Terraformed";
            }
            return v;
        }

        // --- distanceToArrival: integer if no fractional part ---
        if ("distanceToArrival".equals(col)) {
            try {
                double d = Double.parseDouble(v);
                long rounded = Math.round(d);
                if (Math.abs(d - rounded) < 1e-6) {
                    return Long.toString(rounded);
                }
            } catch (Exception ignore) {}
            return v;
        }

        // --- gravity: round to 2 decimals ---
        if ("gravity".equals(col)) {
            try {
                double d = Double.parseDouble(v);
                return String.format(Locale.ROOT, "%.2f", d);
            } catch (Exception ignore) {}
            return v;
        }

        // --- earthMasses: round to 3 decimals ---
        if ("earthMasses".equals(col)) {
            try {
                double d = Double.parseDouble(v);
                return String.format(Locale.ROOT, "%.3f", d);
            } catch (Exception ignore) {}
            return v;
        }

        // ⭐ --- radius: round to nearest integer ---
        if ("radius".equals(col)) {
            try {
                double d = Double.parseDouble(v);
                long rounded = Math.round(d);
                return Long.toString(rounded);
            } catch (Exception ignore) {}
            return v;
        }

        // ⭐ --- radius: round to nearest integer ---
        if ("surfaceTemp".equals(col)) {
            try {
                double d = Double.parseDouble(v);
                long rounded = Math.round(d);
                return Long.toString(rounded);
            } catch (Exception ignore) {}
            return v;
        }
        
        // --- subType: strip trailing " body" ---
        if ("subType".equals(col)) {
            if (lower.endsWith(" body")) {
                return v.substring(0, v.length() - " body".length()).trim();
            }
            return v;
        }

        // --- Generic negatives (No X / false / 0) ---
        if ("false".equals(lower) || "0".equals(lower)) {
            return "";
        }
        if (lower.startsWith("no ")) {
            return "";
        }

        return v;
    }

    // Columns we never want to show in the bodies table (too star-techy / noisy)
    private static final Set<String> BODIES_SKIP_COLUMNS = new LinkedHashSet<>(
            Arrays.asList(
            		"id",
                    "id64",
                    "isMainStar",
                    "isScoopable",
                    "age",
                    "luminosity",
                    "absoluteMagnitude",
                    "rings",
                    "orbitalEccentricity",
                    "orbitalInclination",
                    "orbitalPeriod",
                    "rotaionalPeriod",
                    "rotationalPeriodTidallyLocked",
                    "type",
                    "argOfPeriapsis",
                    "axialTilt",
                    "rotationalPeriod",
                    "semiMajorAxis",
                    "solarMasses",
                    "solarRadius"
            )
    );

}
