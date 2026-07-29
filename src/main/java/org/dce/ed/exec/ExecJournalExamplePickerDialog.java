package org.dce.ed.exec;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.dce.ed.exec.ExecJournalAttributeFilter.MatchMode;
import org.dce.ed.exec.ExecJournalHistoryScanner.JournalExample;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.ui.OverlayScrollPaneSupport;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** Browse journal examples to build exec attribute filters. */
final class ExecJournalExamplePickerDialog extends JDialog {

    private static final Pattern JSON_KEY_AT_CARET = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:");

    interface Callbacks {
        void onAddFilter(ExecJournalAttributeFilter filter);
    }

    private final Callbacks callbacks;
    private final JLabel statusLabel = new JLabel(" ");
    private final JTree fieldTree = new JTree(new DefaultMutableTreeNode("(loading)"));
    private final JTextArea jsonArea = new JTextArea();
    private final JButton olderBtn = new JButton("← Older");
    private final JButton newerBtn = new JButton("Newer →");
    private final JButton addFilterBtn = new JButton("Add as filter…");

    private List<JournalExample> examples = List.of();
    private int exampleIndex;
    private String eventName;

    private ExecJournalExamplePickerDialog(Component parent, String eventName, Callbacks callbacks) {
        super(parent != null ? SwingUtilities.getWindowAncestor(parent) : null,
                "Journal examples — " + eventName, ModalityType.APPLICATION_MODAL);
        this.eventName = eventName != null ? eventName.trim() : "";
        this.callbacks = callbacks;
        buildUi();
        setPreferredSize(new Dimension(900, 560));
        pack();
        setLocationRelativeTo(parent);
        loadExamples();
    }

    static void show(Component parent, String eventName, Callbacks callbacks) {
        ExecJournalExamplePickerDialog dialog = new ExecJournalExamplePickerDialog(parent, eventName, callbacks);
        dialog.setVisible(true);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        nav.add(new JLabel("Event:"));
        JComboBox<String> eventCombo = new JComboBox<>(
                java.util.Arrays.stream(EliteEventType.execSelectableValues())
                        .map(EliteEventType::getJournalName)
                        .toArray(String[]::new));
        eventCombo.setEditable(true);
        eventCombo.setSelectedItem(eventName);
        eventCombo.addActionListener(e -> {
            Object sel = eventCombo.getSelectedItem();
            eventName = sel != null ? sel.toString().trim() : "";
            setTitle("Journal examples — " + eventName);
            loadExamples();
        });
        nav.add(eventCombo);
        olderBtn.addActionListener(e -> showExample(exampleIndex + 1));
        newerBtn.addActionListener(e -> showExample(exampleIndex - 1));
        nav.add(olderBtn);
        nav.add(newerBtn);
        nav.add(statusLabel);
        root.add(nav, BorderLayout.NORTH);

        fieldTree.setRootVisible(true);
        fieldTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        jsonArea.setEditable(false);
        jsonArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        jsonArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectFieldAtCaret();
            }
        });

        JScrollPane treeScroll = new JScrollPane(fieldTree);
        JScrollPane jsonScroll = new JScrollPane(jsonArea);
        OverlayScrollPaneSupport.installSubtleScrollBars(treeScroll);
        OverlayScrollPaneSupport.installSubtleScrollBars(jsonScroll);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, jsonScroll);
        split.setResizeWeight(0.35);
        root.add(split, BorderLayout.CENTER);

        addFilterBtn.addActionListener(e -> promptAddFilter());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        south.add(addFilterBtn);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        south.add(close);
        root.add(south, BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> showExample(exampleIndex + 1),
                KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> showExample(exampleIndex - 1),
                KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        setContentPane(root);
    }

    private void loadExamples() {
        try {
            examples = ExecJournalHistoryScanner.scan(eventName);
        } catch (Exception ex) {
            examples = List.of();
            statusLabel.setText("Could not read journals: " + ex.getMessage());
        }
        exampleIndex = 0;
        if (examples.isEmpty()) {
            jsonArea.setText("No " + eventName + " events found in recent journals.");
            olderBtn.setEnabled(false);
            newerBtn.setEnabled(false);
            addFilterBtn.setEnabled(false);
            statusLabel.setText("0 of 0");
            return;
        }
        addFilterBtn.setEnabled(true);
        showExample(0);
    }

    private void showExample(int index) {
        if (examples.isEmpty()) {
            return;
        }
        exampleIndex = Math.max(0, Math.min(index, examples.size() - 1));
        JournalExample ex = examples.get(exampleIndex);
        statusLabel.setText((exampleIndex + 1) + " of " + examples.size() + " — " + ex.timestamp());
        olderBtn.setEnabled(exampleIndex < examples.size() - 1);
        newerBtn.setEnabled(exampleIndex > 0);
        rebuildTree(ex.json());
        jsonArea.setText(new GsonBuilder().setPrettyPrinting().create().toJson(ex.json()));
        jsonArea.setCaretPosition(0);
    }

    private void rebuildTree(JsonObject obj) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(eventName);
        if (obj != null) {
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                root.add(new DefaultMutableTreeNode(new FieldNode(entry.getKey(), formatPreview(entry.getValue()))));
            }
        }
        fieldTree.setModel(new DefaultTreeModel(root));
        fieldTree.expandRow(0);
    }

    private static String formatPreview(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return "null";
        }
        if (el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isString()) {
                String s = p.getAsString();
                return s.length() > 40 ? s.substring(0, 37) + "…" : s;
            }
            return p.toString();
        }
        return el.isJsonArray() ? "[…]" : "{…}";
    }

    private void selectFieldAtCaret() {
        int pos = jsonArea.getCaretPosition();
        String text = jsonArea.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        int lineStart = text.lastIndexOf('\n', Math.max(0, pos - 1)) + 1;
        int lineEnd = text.indexOf('\n', pos);
        if (lineEnd < 0) {
            lineEnd = text.length();
        }
        Matcher m = JSON_KEY_AT_CARET.matcher(text.substring(lineStart, lineEnd));
        if (m.find()) {
            selectFieldInTree(m.group(1));
        }
    }

    private void selectFieldInTree(String fieldName) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) fieldTree.getModel().getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            Object user = child.getUserObject();
            if (user instanceof FieldNode fn && fn.field().equals(fieldName)) {
                TreePath path = new TreePath(new Object[] { root, child });
                fieldTree.setSelectionPath(path);
                return;
            }
        }
    }

    private String selectedFieldName() {
        TreePath path = fieldTree.getSelectionPath();
        if (path == null) {
            return null;
        }
        Object last = path.getLastPathComponent();
        if (!(last instanceof DefaultMutableTreeNode node)) {
            return null;
        }
        Object user = node.getUserObject();
        return user instanceof FieldNode fn ? fn.field() : null;
    }

    private void promptAddFilter() {
        String field = selectedFieldName();
        if (field == null || examples.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select a top-level field first.", "Add filter",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JsonObject obj = examples.get(exampleIndex).json();
        JsonElement el = obj.get(field);
        if (el != null && (el.isJsonObject() || el.isJsonArray())) {
            JOptionPane.showMessageDialog(this, "Only top-level primitive fields can be filters.",
                    "Add filter", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String defaultValue = ExecJournalJsonMatcher.jsonFieldAsString(obj, field);
        JTextField valueField = new JTextField(defaultValue != null ? defaultValue : "", 24);
        JComboBox<MatchMode> modeCombo = new JComboBox<>(MatchMode.values());
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new JLabel("Field: " + field), BorderLayout.NORTH);
        panel.add(valueField, BorderLayout.CENTER);
        panel.add(modeCombo, BorderLayout.SOUTH);
        if (JOptionPane.showConfirmDialog(this, panel, "Add filter", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        if (callbacks != null) {
            callbacks.onAddFilter(new ExecJournalAttributeFilter(field, valueField.getText(),
                    (MatchMode) modeCombo.getSelectedItem()));
        }
    }

    private record FieldNode(String field, String preview) {
        @Override
        public String toString() {
            return field + ": " + preview;
        }
    }
}
