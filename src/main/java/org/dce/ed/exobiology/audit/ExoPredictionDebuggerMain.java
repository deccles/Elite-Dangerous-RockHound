package org.dce.ed.exobiology.audit;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.dce.ed.cache.CachedSystem;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.exobiology.BodyAttributes;
import org.dce.ed.exobiology.ExobiologyData;
import org.dce.ed.exobiology.ExobiologyData.BioCandidate;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;

public class ExoPredictionDebuggerMain {

    public static void main(String[] args) throws Exception {
        // CLI:
        //   <systemName> <bodyName> [--lastN 8]
        if (args != null && args.length >= 2) {
            String system = args[0];
            String body = args[1];

            int lastN = 8;
            for (int i = 2; i < args.length; i++) {
                if ("--lastN".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                    lastN = Integer.parseInt(args[++i]);
                }
            }

            runCli(system, body);
            return;
        }

        SwingUtilities.invokeLater(ExoPredictionDebuggerMain::showGui);
    }

    private static void runCli(String system, String body) throws Exception {
        SystemState state = loadSystemStatePreferCache(system);
        if (state == null) {
            System.out.println("Could not load system state for: " + system);
            return;
        }

        BodyInfo b = findBodyByName(state, body);
        if (b == null) {
            System.out.println("Body not found: " + body);
            System.out.println("Bodies present:");
            for (BodyInfo bi : state.getBodies().values()) {
                System.out.println(" - " + bi.getBodyName());
            }
            return;
        }

        System.out.println(buildPredictionText(state, b));
    }

    private static void showGui() {
        JFrame f = new JFrame("Exobiology Prediction Debugger");
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTextField systemField = new JTextField(28);
        JComboBox<String> bodyCombo = new JComboBox<>();
        bodyCombo.setPreferredSize(new Dimension(340, bodyCombo.getPreferredSize().height));

        JTextField lastNField = new JTextField("8", 4);

        JButton load = new JButton("Load bodies");
        JButton predict = new JButton("Predict");

        JTextArea out = new JTextArea();
        out.setEditable(false);
        out.setLineWrap(false);
        JScrollPane scroll = new JScrollPane(out);

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;

        gc.gridx = 0;
        gc.gridy = 0;
        top.add(new JLabel("System:"), gc);

        gc.gridx = 1;
        top.add(systemField, gc);

        gc.gridx = 2;
        top.add(new JLabel("Last N journals:"), gc);

        gc.gridx = 3;
        top.add(lastNField, gc);

        gc.gridx = 0;
        gc.gridy = 1;
        top.add(new JLabel("Body:"), gc);

        gc.gridx = 1;
        gc.gridwidth = 2;
        top.add(bodyCombo, gc);
        gc.gridwidth = 1;

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.add(load);
        buttons.add(predict);

        gc.gridx = 3;
        top.add(buttons, gc);

        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);

        load.addActionListener(e -> {
            out.setText("");

            String system = safe(systemField.getText());
            if (system.isEmpty()) {
                out.setText("Enter a system name.\n");
                return;
            }

            try {
                SystemState state = loadSystemStatePreferCache(system);
                if (state == null) {
                    out.setText("Could not load state for system: " + system + "\n");
                    return;
                }

                List<BodyInfo> bodies = new ArrayList<>(state.getBodies().values());
                bodies.sort(Comparator.comparing(BodyInfo::getBodyName, String.CASE_INSENSITIVE_ORDER));

                bodyCombo.removeAllItems();
                for (BodyInfo b : bodies) {
                    if (b.getBodyName() != null && !b.getBodyName().isBlank()) {
                        bodyCombo.addItem(b.getBodyName());
                    }
                }

                // stash state
                bodyCombo.putClientProperty("state", state);

                out.setText("Loaded " + bodies.size() + " bodies for system: " + system + "\n");
                out.append("Source: " + String.valueOf(bodyCombo.getClientProperty("stateSource")) + "\n");
                out.append("Pick a body and click Predict.\n");

            } catch (Exception ex) {
                ex.printStackTrace();
                out.setText("Error: " + ex.getMessage() + "\n");
            }
        });

        predict.addActionListener(e -> {
            out.setText("");

            Object stObj = bodyCombo.getClientProperty("state");
            if (!(stObj instanceof SystemState)) {
                out.setText("Click Load bodies first.\n");
                return;
            }
            SystemState state = (SystemState) stObj;

            Object sel = bodyCombo.getSelectedItem();
            if (sel == null) {
                out.setText("Pick a body.\n");
                return;
            }

            String bodyName = String.valueOf(sel);
            BodyInfo b = findBodyByName(state, bodyName);
            if (b == null) {
                out.setText("Body not found in state: " + bodyName + "\n");
                return;
            }

            out.setText(buildPredictionText(state, b));
            out.setCaretPosition(0);
        });

        f.setContentPane(root);
        f.setSize(950, 700);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Prefer cache for body-loading. Fall back to journal replay if the system isn't cached.
     */
    private static SystemState loadSystemStatePreferCache(String systemName) throws Exception {
        // Force cache-only for debugging cache contents
        return loadSystemStateFromCache(systemName);
    }


    private static SystemState loadSystemStateFromCache(String systemName) {
        if (systemName == null || systemName.isBlank()) {
            return null;
        }

        SystemCache cache = SystemCache.getInstance();

        // We usually don't know systemAddress here, so pass 0 and match by name.
        CachedSystem cs = cache.get(0L, systemName);
        if (cs == null) {
            return null;
        }

        SystemState state = new SystemState();
        cache.loadInto(state, cs);
        return state;
    }

    private static BodyInfo findBodyByName(SystemState state, String bodyName) {
        if (state == null || bodyName == null) {
            return null;
        }
        for (BodyInfo b : state.getBodies().values()) {
            if (b == null) {
                continue;
            }
            if (bodyName.equals(b.getBodyName())) {
                return b;
            }
        }
        return null;
    }

    private static String buildPredictionText(SystemState state, BodyInfo body) {
        StringBuilder sb = new StringBuilder();
        sb.append("System: ").append(state.getSystemName()).append("\n");
        sb.append("Body:   ").append(body.getBodyName()).append("\n");
        sb.append("BodyId: ").append(body.getBodyId()).append("\n\n");

        BodyAttributes attrs = body.buildBodyAttributes(state);
        if (attrs == null) {
            sb.append("buildBodyAttributes(state) returned null (insufficient data)\n");
            return sb.toString();
        }

        sb.append("Attributes used for prediction:\n");
        sb.append("planetType: ").append(attrs.planetType).append("\n");
        sb.append("atmosphere: ").append(attrs.atmosphere).append("\n");
        sb.append("gravity(g): ").append(attrs.gravity).append("\n");
        sb.append("tempKMin: ").append(attrs.tempKMin).append("\n");
        sb.append("pressure: ").append(attrs.pressure).append("\n");
        sb.append("volcanism: ").append(attrs.volcanismType).append("\n");
        sb.append("distanceLs: ").append(attrs.distance).append("\n");
        sb.append("parentStar: ").append(attrs.parentStar).append("\n");
        sb.append("starClass: ").append(attrs.starType).append("\n");
        sb.append("guardian: ").append(attrs.guardian).append("\n");
        sb.append("nebula: ").append(attrs.nebula).append("\n");
        sb.append("atmoComponents size: ").append(attrs.atmosphereComponents == null ? 0 : attrs.atmosphereComponents.size()).append("\n\n");

        sb.append("Predictions (ExobiologyData.predict):\n");
        List<BioCandidate> preds = ExobiologyData.predict(attrs);
        if (preds == null || preds.isEmpty()) {
            sb.append("(none)\n");
            return sb.toString();
        }

        preds.stream()
                .filter(Objects::nonNull)
                .map(BioCandidate::getDisplayName)
                .filter(Objects::nonNull)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(p -> sb.append(" - ").append(p).append("\n"));

        return sb.toString();
    }
}
