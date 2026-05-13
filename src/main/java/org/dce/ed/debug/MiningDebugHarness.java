package org.dce.ed.debug;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.dce.ed.CargoMonitor;
import org.dce.ed.EliteDangerousOverlay;
import org.dce.ed.EliteOverlayTabbedPane;
import org.dce.ed.OverlayPreferences;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent.MaterialProportion;
import org.dce.ed.logreader.event.StartJumpEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.logreader.event.SupercruiseExitEvent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Simple test harness GUI that hosts an EliteOverlayTabbedPane and lets the user
 * push synthetic journal events (Location, Status, Prospector, etc.) to quickly
 * exercise mining run behavior without running the game.
 *
 * This is intended for developer/testing use only.
 */
public final class MiningDebugHarness {

    private static final EliteLogParser STATUS_PARSER = new EliteLogParser();

    private MiningDebugHarness() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MiningDebugHarness::createAndShow);
    }

    private static void createAndShow() {
        JFrame frame = new JFrame("EDO Mining Debug Harness");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        EliteOverlayTabbedPane tabs = new EliteOverlayTabbedPane(() -> false);

        JPanel controls = buildControlPanel(tabs);

        frame.setLayout(new BorderLayout());
        frame.add(tabs, BorderLayout.CENTER);
        frame.add(controls, BorderLayout.EAST);

        frame.setSize(new Dimension(1400, 900));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static JPanel buildControlPanel(EliteOverlayTabbedPane tabs) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;

        int row = 0;

        // System / Body (filled from live cache / Status.json / journal after controls are built)
        JTextField systemField = new JTextField();
        JTextField bodyField = new JTextField();

        gc.gridx = 0;
        gc.gridy = row++;
        panel.add(new JLabel("System"), gc);
        gc.gridx = 1;
        panel.add(systemField, gc);

        gc.gridx = 0;
        gc.gridy = row++;
        panel.add(new JLabel("Body"), gc);
        gc.gridx = 1;
        panel.add(bodyField, gc);

        // Dock / undock via Location event
        JButton btnLocationDocked = new JButton("Location: Docked");
        btnLocationDocked.addActionListener(e -> {
            EliteLogEvent ev = new LocationEvent(
                Instant.now(),
                new JsonObject(),
                true,  // docked
                false, // taxi
                false, // multicrew
                systemField.getText().trim(),
                0L,
                null,
                bodyField.getText().trim(),
                0,
                "Station"
            );
            tabs.processJournalEvent(ev);
        });

        JButton btnLocationUndocked = new JButton("Location: Undocked");
        btnLocationUndocked.addActionListener(e -> {
            EliteLogEvent ev = new LocationEvent(
                Instant.now(),
                new JsonObject(),
                false, // docked
                false,
                false,
                systemField.getText().trim(),
                0L,
                null,
                bodyField.getText().trim(),
                0,
                "Ring"
            );
            tabs.processJournalEvent(ev);
        });

        gc.gridx = 0;
        gc.gridy = row;
        panel.add(btnLocationDocked, gc);
        gc.gridx = 1;
        panel.add(btnLocationUndocked, gc);
        row++;

        // Status: toggle docked flag only (for mining tab dock tracking).
        JButton btnStatusDocked = new JButton("Status: Docked");
        btnStatusDocked.addActionListener(e -> {
            JsonObject raw = new JsonObject();
            raw.addProperty("Flags", 0x00000001); // docked bit
            EliteLogEvent ev = new StatusEvent(
                Instant.now(),
                raw,
                0x00000001, // flags (docked)
                0,          // flags2
                new int[] {4, 4, 4}, // pips
                0,          // fireGroup
                0,          // guiFocus
                0.0,        // fuelMain
                0.0,        // fuelReservoir
                0.0,        // cargo
                "Clean",    // legalState
                0L,         // balance
                null,       // latitude
                null,       // longitude
                null,       // altitude
                null,       // heading
                bodyField.getText().trim(), // bodyName
                null,       // planetRadius
                null,       // destinationSystem
                null,       // destinationBody
                null,       // destinationName
                null,       // destinationNameLocalised
                null,       // bodyNamePhysical
                null        // statusBodyId
            );
            tabs.processJournalEvent(ev);
        });

        JButton btnStatusUndocked = new JButton("Status: Undocked");
        btnStatusUndocked.addActionListener(e -> {
            JsonObject raw = new JsonObject();
            raw.addProperty("Flags", 0); // none set
            EliteLogEvent ev = new StatusEvent(
                Instant.now(),
                raw,
                0,          // flags
                0,          // flags2
                new int[] {4, 4, 4},
                0,
                0,
                0.0,
                0.0,
                0.0,
                "Clean",
                0L,
                null,
                null,
                null,
                null,
                bodyField.getText().trim(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );
            tabs.processJournalEvent(ev);
        });

        gc.gridx = 0;
        gc.gridy = row;
        panel.add(btnStatusDocked, gc);
        gc.gridx = 1;
        panel.add(btnStatusUndocked, gc);
        row++;

        // Supercruise exit to ring
        JButton btnSupercruiseExit = new JButton("Supercruise Exit (Ring)");
        btnSupercruiseExit.addActionListener(e -> {
            EliteLogEvent ev = new SupercruiseExitEvent(
                Instant.now(),
                new JsonObject(),
                false,
                false,
                systemField.getText().trim(),
                0L,
                bodyField.getText().trim(),
                0,
                "Ring"
            );
            tabs.processJournalEvent(ev);
        });

        gc.gridx = 0;
        gc.gridy = row++;
        gc.gridwidth = 2;
        panel.add(btnSupercruiseExit, gc);
        gc.gridwidth = 1;

        // Start jump (for completeness, mining no longer ends runs on jump)
        JButton btnStartJump = new JButton("Start Jump");
        btnStartJump.addActionListener(e -> {
            EliteLogEvent ev = new StartJumpEvent(
                Instant.now(),
                new JsonObject(),
                "Hyperspace",
                false,
                systemField.getText().trim(),
                0L,
                "K"
            );
            tabs.processJournalEvent(ev);
        });

        gc.gridx = 0;
        gc.gridy = row++;
        gc.gridwidth = 2;
        panel.add(btnStartJump, gc);
        gc.gridwidth = 1;

        // Prospector controls
        gc.gridx = 0;
        gc.gridy = row++;
        gc.gridwidth = 2;
        panel.add(new JLabel("Prospector materials (name=percent per line)"), gc);
        gc.gridwidth = 1;

        JTextArea prospectorArea = new JTextArea(6, 20);
        prospectorArea.setText("Bromellite=33.7\nMethane Clathrate=11.6\nLiquid oxygen=4.8");
        JScrollPane prospectorScroll = new JScrollPane(prospectorArea);
        prospectorScroll.setPreferredSize(new Dimension(220, 120));

        gc.gridx = 0;
        gc.gridy = row++;
        gc.gridwidth = 2;
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1.0;
        panel.add(prospectorScroll, gc);
        gc.gridwidth = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weighty = 0.0;

        JTextField contentField = new JTextField("High");
        JTextField motherlodeField = new JTextField("");

        gc.gridx = 0;
        gc.gridy = row;
        panel.add(new JLabel("Content (Low/Med/High)"), gc);
        gc.gridx = 1;
        panel.add(contentField, gc);
        row++;

        gc.gridx = 0;
        gc.gridy = row;
        panel.add(new JLabel("Motherlode material"), gc);
        gc.gridx = 1;
        panel.add(motherlodeField, gc);
        row++;

        JButton btnProspector = new JButton("Fire Prospector");
        btnProspector.addActionListener(e -> {
            // Sync SystemState / mining location with the text fields (same as "Location: Undocked").
            EliteLogEvent locEv = new LocationEvent(
                Instant.now(),
                new JsonObject(),
                false,
                false,
                false,
                systemField.getText().trim(),
                0L,
                null,
                bodyField.getText().trim(),
                0,
                "Ring"
            );
            tabs.processJournalEvent(locEv);

            List<MaterialProportion> mats = parseMaterials(prospectorArea.getText());
            String motherlode = motherlodeField.getText().trim();
            if (motherlode.isEmpty()) {
                motherlode = null;
            }
            EliteLogEvent ev = new ProspectedAsteroidEvent(
                Instant.now(),
                new JsonObject(),
                mats,
                motherlode,
                contentField.getText().trim()
            );
            tabs.processJournalEvent(ev);
        });

        gc.gridx = 0;
        gc.gridy = row++;
        gc.gridwidth = 2;
        panel.add(btnProspector, gc);
        gc.gridwidth = 1;

        // Cargo controls (debug-only): name=count per line
        gc.gridx = 0;
        gc.gridy = row++;
        gc.gridwidth = 2;
        panel.add(new JLabel("Cargo (name=count per line)"), gc);
        gc.gridwidth = 1;

        JTextArea cargoArea = new JTextArea(6, 20);
        JScrollPane cargoScroll = new JScrollPane(cargoArea);
        cargoScroll.setPreferredSize(new Dimension(220, 120));

        gc.gridx = 0;
        gc.gridy = row++;
        gc.gridwidth = 2;
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1.0;
        panel.add(cargoScroll, gc);
        gc.gridwidth = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weighty = 0.0;

        JButton btnSetCargo = new JButton("Set Cargo Snapshot");
        btnSetCargo.addActionListener(e -> {
            JsonObject cargoJson = buildCargoJsonFromText(cargoArea.getText());
            CargoMonitor.getInstance().setDebugSnapshot(cargoJson);
        });

        gc.gridx = 0;
        gc.gridy = row++;
        gc.gridwidth = 2;
        panel.add(btnSetCargo, gc);
        gc.gridwidth = 1;

        populateHarnessFieldsFromLiveState(tabs, systemField, bodyField, cargoArea);

        return panel;
    }

    /**
     * Prefill system, body, and cargo text areas from the same sources as the overlay:
     * {@link org.dce.ed.SystemTabPanel} cache, {@code Status.json}, latest journal {@link LocationEvent},
     * and {@link CargoMonitor} (live {@code Cargo.json} poll).
     */
    private static void populateHarnessFieldsFromLiveState(EliteOverlayTabbedPane tabs,
            JTextField systemField, JTextField bodyField, JTextArea cargoArea) {
        String system = "";
        String body = "";
        if (tabs != null) {
            var stp = tabs.getSystemTabPanel();
            if (stp != null && stp.getState() != null) {
                String n = stp.getState().getSystemName();
                if (n != null && !n.isBlank()) {
                    system = n.trim();
                }
            }
        }
        StatusEvent status = readStatusSnapshotFromDisk();
        if (status != null) {
            String bn = status.getBodyName();
            if (bn != null && !bn.isBlank()) {
                body = bn.trim();
            }
        }
        if (system.isEmpty() || body.isEmpty()) {
            try {
                Path dir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
                if (dir != null && Files.isDirectory(dir)) {
                    EliteJournalReader reader = new EliteJournalReader(dir);
                    EliteLogEvent trans = reader.findMostRecentSystemTransitionEvent(null);
                    if (trans instanceof LocationEvent le) {
                        if (system.isEmpty()) {
                            String sn = le.getStarSystem();
                            if (sn != null && !sn.isBlank()) {
                                system = sn.trim();
                            }
                        }
                        if (body.isEmpty()) {
                            String b = le.getBody();
                            if (b != null && !b.isBlank()) {
                                body = b.trim();
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        systemField.setText(system);
        bodyField.setText(body);
        cargoArea.setText(formatCargoInventoryLines(CargoMonitor.getInstance().getSnapshot()));
    }

    private static StatusEvent readStatusSnapshotFromDisk() {
        try {
            String home = System.getProperty("user.home");
            Path p = Path.of(home, "Saved Games", "Frontier Developments", "Elite Dangerous", "Status.json");
            if (!Files.exists(p)) {
                return null;
            }
            return STATUS_PARSER.parseStatusJsonFile(p);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static String formatCargoInventoryLines(CargoMonitor.Snapshot snap) {
        if (snap == null) {
            return "";
        }
        JsonObject cargo = snap.getCargoJson();
        if (cargo == null) {
            return "";
        }
        JsonArray inv = null;
        if (cargo.has("Inventory") && cargo.get("Inventory").isJsonArray()) {
            inv = cargo.getAsJsonArray("Inventory");
        } else if (cargo.has("inventory") && cargo.get("inventory").isJsonArray()) {
            inv = cargo.getAsJsonArray("inventory");
        }
        if (inv == null || inv.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonElement el : inv) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            String localised = jsonString(o, "Name_Localised");
            String name = jsonString(o, "Name");
            if (name == null) {
                name = jsonString(o, "name");
            }
            String display = (localised != null && !localised.isBlank()) ? localised.trim()
                    : (name != null ? name.trim() : "");
            if (display.isEmpty()) {
                continue;
            }
            long count = jsonLong(o, "Count");
            if (count <= 0L) {
                count = jsonLong(o, "count");
            }
            if (count <= 0L) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(display).append('=').append(count);
        }
        return sb.toString();
    }

    private static String jsonString(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        try {
            return o.get(key).getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static long jsonLong(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return 0L;
        }
        try {
            return o.get(key).getAsLong();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static List<MaterialProportion> parseMaterials(String text) {
        List<MaterialProportion> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split("=");
            if (parts.length != 2) continue;
            String name = parts[0].trim();
            String pctStr = parts[1].trim();
            if (name.isEmpty() || pctStr.isEmpty()) continue;
            try {
                double pct = Double.parseDouble(pctStr);
                out.add(new MaterialProportion(name, pct));
            } catch (NumberFormatException ex) {
                // ignore malformed line
            }
        }
        return out;
    }

    /**
     * Build a minimal Cargo.json-like object from lines of \"name=count\".
     * Example:
     * Bromellite=110
     * Limpet=18
     */
    private static JsonObject buildCargoJsonFromText(String text) {
        JsonObject root = new JsonObject();
        com.google.gson.JsonArray inv = new com.google.gson.JsonArray();
        if (text != null && !text.isBlank()) {
            String[] lines = text.split("\\r?\\n");
            for (String line : lines) {
                if (line == null) continue;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                String[] parts = trimmed.split("=");
                if (parts.length != 2) continue;
                String name = parts[0].trim();
                String countStr = parts[1].trim();
                if (name.isEmpty() || countStr.isEmpty()) continue;
                try {
                    long count = Long.parseLong(countStr);
                    if (count <= 0) continue;
                    JsonObject item = new JsonObject();
                    item.addProperty("Name", name);
                    item.addProperty("Name_Localised", name);
                    item.addProperty("Count", count);
                    inv.add(item);
                } catch (NumberFormatException ex) {
                    // ignore malformed line
                }
            }
        }
        root.add("Inventory", inv);
        return root;
    }
}

