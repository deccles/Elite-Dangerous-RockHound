package org.dce.ed.debug;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
import org.dce.ed.EliteOverlayTabbedPane;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent.MaterialProportion;
import org.dce.ed.logreader.event.StartJumpEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.logreader.event.SupercruiseExitEvent;

import com.google.gson.JsonObject;

/**
 * Simple test harness GUI that hosts an EliteOverlayTabbedPane and lets the user
 * push synthetic journal events (Location, Status, Prospector, etc.) to quickly
 * exercise mining run behavior without running the game.
 *
 * This is intended for developer/testing use only.
 */
public final class MiningDebugHarness {

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

        // System / Body
        JTextField systemField = new JTextField("Eol Prou LW-L c8-75");
        JTextField bodyField = new JTextField("6 B Ring");

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
                null        // destinationNameLocalised
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
        cargoArea.setText("Bromellite=110\nLimpet=18");
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

        return panel;
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

