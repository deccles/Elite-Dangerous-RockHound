package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

import org.dce.ed.edsm.UtilTable;
import org.dce.ed.engineering.BlueprintGrade;
import org.dce.ed.engineering.EngineeringDatabase;
import org.dce.ed.engineering.EngineeringGradeProgress;
import org.dce.ed.engineering.EngineeringGoal;
import org.dce.ed.engineering.EngineeringMaterialKeys;
import org.dce.ed.engineering.MaterialRequirement;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.HoverClickPoller;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.TableHeaderSortSupport;

/**
 * Modal picker for a new engineering goal (blueprint, grade, experimental).
 */
final class AddEngineeringGoalDialog extends JDialog {

    private static final int HOVER_CLICK_DELAY_MS = 500;
    private static final int HEADER_SORT_HOVER_MS = 500;
    private static final int COL_MODULE = 0;
    private static final int COL_BLUEPRINT = 1;
    private static final int COL_GRADES = 2;

    private final EngineeringDatabase database;
    private final BooleanSupplier passThroughEnabledSupplier;
    private final JTextField filterField = new JTextField(24);
    private final BlueprintTableModel blueprintModel = new BlueprintTableModel();
    private final JTable blueprintTable = new JTable(blueprintModel);
    private final TableRowSorter<BlueprintTableModel> blueprintSorter = new TableRowSorter<>(blueprintModel);
    private final JLabel effectsLabel = new JLabel(" ");
    private final JLabel materialsLabel = new JLabel(" ");
    private final JComboBox<String> gradeCombo = new JComboBox<>();
    private final JComboBox<String> experimentalCombo = new JComboBox<>();

    private EngineeringGoal result;

    private AddEngineeringGoalDialog(Window owner, EngineeringDatabase database, BooleanSupplier passThroughEnabledSupplier) {
        super(owner, "Add engineering goal", ModalityType.APPLICATION_MODAL);
        this.database = database;
        this.passThroughEnabledSupplier = passThroughEnabledSupplier;
        buildUi();
        reloadBlueprintList();
        pack();
        setMinimumSize(new Dimension(520, 420));
        setLocationRelativeTo(owner);
    }

    static EngineeringGoal show(Window owner, EngineeringDatabase database, BooleanSupplier passThroughEnabledSupplier) {
        if (database == null) {
            return null;
        }
        AddEngineeringGoalDialog dialog = new AddEngineeringGoalDialog(owner, database, passThroughEnabledSupplier);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void buildUi() {
        Font base = OverlayPreferences.getUiFont();
        int fontSize = OverlayPreferences.getUiFontSize();

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setOpaque(true);
        root.setBackground(EdoUi.User.BACKGROUND);
        root.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel intro = new JLabel("Search and select a blueprint, then choose grade and optional experimental.");
        intro.setFont(base.deriveFont(Font.PLAIN, fontSize));
        intro.setForeground(EdoUi.User.MAIN_TEXT);
        root.add(intro, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.setOpaque(false);
        styleTextField(filterField, base);
        filterField.setToolTipText("Filter by module or blueprint name");
        center.add(filterField, BorderLayout.NORTH);

        configureBlueprintTable(base, fontSize);
        JScrollPane tableScroll = new JScrollPane(blueprintTable);
        tableScroll.setPreferredSize(new Dimension(480, 200));
        tableScroll.setBorder(BorderFactory.createLineBorder(EdoUi.Internal.MAIN_TEXT_ALPHA_140));
        center.add(tableScroll, BorderLayout.CENTER);

        effectsLabel.setFont(base.deriveFont(Font.ITALIC, fontSize));
        effectsLabel.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
        effectsLabel.setBorder(new EmptyBorder(2, 0, 0, 0));
        materialsLabel.setFont(base.deriveFont(Font.PLAIN, fontSize));
        materialsLabel.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
        JPanel detailPanel = new JPanel(new BorderLayout(0, 4));
        detailPanel.setOpaque(false);
        detailPanel.add(effectsLabel, BorderLayout.NORTH);
        detailPanel.add(materialsLabel, BorderLayout.SOUTH);
        center.add(detailPanel, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel gradeLabel = fieldLabel("Target grade:", base, fontSize);
        gbc.gridx = 0;
        gbc.gridy = 0;
        form.add(gradeLabel, gbc);
        styleCombo(gradeCombo, base);
        constrainComboHeight(gradeCombo);
        gradeCombo.addActionListener(e -> updateGradeDetails());
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        form.add(gradeCombo, gbc);

        JLabel expLabel = fieldLabel("Experimental:", base, fontSize);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        form.add(expLabel, gbc);
        styleCombo(experimentalCombo, base);
        constrainComboHeight(experimentalCombo);
        experimentalCombo.addItem("(none)");
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        form.add(experimentalCombo, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton addBtn = new JButton("Add goal");
        OverlayOutlineButtonStyle.applyChip(addBtn, base, false);
        addBtn.addActionListener(e -> confirmAdd());
        HoverClickPoller.register(addBtn, HOVER_CLICK_DELAY_MS, this::confirmAdd, passThroughEnabledSupplier);
        JButton cancelBtn = new JButton("Cancel");
        OverlayOutlineButtonStyle.applyChip(cancelBtn, base, false);
        cancelBtn.addActionListener(e -> dispose());
        HoverClickPoller.register(cancelBtn, HOVER_CLICK_DELAY_MS, this::dispose, passThroughEnabledSupplier);
        buttons.add(addBtn);
        buttons.add(cancelBtn);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(form, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        root.add(south, BorderLayout.PAGE_END);

        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                reloadBlueprintList();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                reloadBlueprintList();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                reloadBlueprintList();
            }
        });

        setContentPane(root);
        getRootPane().setDefaultButton(addBtn);
    }

    private void configureBlueprintTable(Font base, int fontSize) {
        blueprintTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        blueprintTable.setFont(base.deriveFont(Font.PLAIN, fontSize));
        blueprintTable.setForeground(EdoUi.User.MAIN_TEXT);
        blueprintTable.setBackground(EdoUi.User.PANEL_BG);
        blueprintTable.setGridColor(EdoUi.Internal.mainTextAlpha(48));
        blueprintTable.setRowHeight(Math.max(22, fontSize + 10));
        blueprintTable.setShowHorizontalLines(true);
        blueprintTable.setShowVerticalLines(false);
        blueprintTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        blueprintTable.setDefaultRenderer(Object.class, new BlueprintCellRenderer());
        blueprintTable.getTableHeader().setFont(base.deriveFont(Font.BOLD, fontSize));
        blueprintTable.getTableHeader().setForeground(EdoUi.User.MAIN_TEXT);
        blueprintTable.getTableHeader().setBackground(EdoUi.User.PANEL_BG);
        blueprintTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateSelectionDetails();
            }
        });

        blueprintSorter.setComparator(COL_MODULE, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        blueprintSorter.setComparator(COL_BLUEPRINT, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        blueprintSorter.setComparator(COL_GRADES, Comparator.nullsLast(Comparator.naturalOrder()));
        blueprintTable.setAutoCreateRowSorter(false);
        blueprintTable.setRowSorter(blueprintSorter);
        restoreSavedSort();
        blueprintSorter.addRowSorterListener(e -> persistSort());
        TableHeaderSortSupport.install(blueprintTable, passThroughEnabledSupplier, HEADER_SORT_HOVER_MS);
    }

    private void restoreSavedSort() {
        int column = OverlayPreferences.getEngineeringBlueprintPickerSortColumn();
        boolean descending = OverlayPreferences.isEngineeringBlueprintPickerSortDescending();
        if (column < 0 || column >= blueprintModel.getColumnCount()) {
            return;
        }
        blueprintSorter.setSortKeys(List.of(new RowSorter.SortKey(
                column,
                descending ? SortOrder.DESCENDING : SortOrder.ASCENDING)));
    }

    private void persistSort() {
        List<? extends RowSorter.SortKey> keys = blueprintSorter.getSortKeys();
        if (keys == null || keys.isEmpty()) {
            OverlayPreferences.setEngineeringBlueprintPickerSortColumn(-1);
            return;
        }
        RowSorter.SortKey key = keys.get(0);
        OverlayPreferences.setEngineeringBlueprintPickerSortColumn(key.getColumn());
        OverlayPreferences.setEngineeringBlueprintPickerSortDescending(key.getSortOrder() == SortOrder.DESCENDING);
    }

    private void reloadBlueprintList() {
        String q = filterField.getText().trim().toLowerCase();
        BlueprintOption selected = selectedOption();
        Map<String, BlueprintOption> unique = new LinkedHashMap<>();
        for (BlueprintGrade bp : database.getAllBlueprints()) {
            if (bp.isExperimental()) {
                continue;
            }
            if (!q.isEmpty()) {
                String hay = (bp.getModuleType() + " " + bp.getName()).toLowerCase();
                if (!hay.contains(q)) {
                    continue;
                }
            }
            String key = EngineeringDatabase.groupKey(bp.getModuleType(), bp.getName());
            BlueprintOption existing = unique.get(key);
            if (existing == null) {
                unique.put(key, new BlueprintOption(bp.getModuleType(), bp.getName(), bp.getGrade()));
            } else {
                unique.put(key, existing.withMaxGrade(Math.max(existing.maxGrade(), bp.getGrade())));
            }
        }
        blueprintModel.setRows(new ArrayList<>(unique.values()));
        selectOption(selected);
        SwingUtilities.invokeLater(() -> UtilTable.autoSizeTableColumns(blueprintTable));
    }

    private void selectOption(BlueprintOption selected) {
        if (selected == null) {
            if (blueprintTable.getRowCount() > 0) {
                blueprintTable.setRowSelectionInterval(0, 0);
            } else {
                updateSelectionDetails();
            }
            return;
        }
        for (int modelRow = 0; modelRow < blueprintModel.getRowCount(); modelRow++) {
            BlueprintOption row = blueprintModel.rowAt(modelRow);
            if (row != null
                    && row.moduleType().equals(selected.moduleType())
                    && row.blueprintName().equals(selected.blueprintName())) {
                int viewRow = blueprintTable.convertRowIndexToView(modelRow);
                if (viewRow >= 0) {
                    blueprintTable.setRowSelectionInterval(viewRow, viewRow);
                    return;
                }
            }
        }
        if (blueprintTable.getRowCount() > 0) {
            blueprintTable.setRowSelectionInterval(0, 0);
        } else {
            updateSelectionDetails();
        }
    }

    private BlueprintOption selectedOption() {
        int viewRow = blueprintTable.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        int modelRow = blueprintTable.convertRowIndexToModel(viewRow);
        return blueprintModel.rowAt(modelRow);
    }

    private void updateSelectionDetails() {
        BlueprintOption selected = selectedOption();
        gradeCombo.removeAllItems();
        experimentalCombo.removeAllItems();
        experimentalCombo.addItem("(none)");
        if (selected == null) {
            effectsLabel.setText(" ");
            materialsLabel.setText(" ");
            return;
        }
        for (int g = 1; g <= selected.maxGrade(); g++) {
            gradeCombo.addItem("G" + g);
        }
        gradeCombo.setSelectedItem("G" + selected.maxGrade());
        updateGradeDetails();

        for (BlueprintGrade exp : database.experimentalsFor(selected.moduleType(), selected.blueprintName())) {
            experimentalCombo.addItem(exp.getName());
        }
    }

    private void updateGradeDetails() {
        BlueprintOption selected = selectedOption();
        if (selected == null) {
            return;
        }
        int targetGrade = selectedTargetGrade(selected);
        BlueprintGrade preview = database.gradesFor(selected.moduleType(), selected.blueprintName()).stream()
                .filter(g -> g.getGrade() == targetGrade)
                .findFirst()
                .orElse(null);
        if (preview != null) {
            effectsLabel.setText("<html><body style='color:#ffcc88; width:420px'>G"
                    + preview.getGrade() + " effects: " + htmlEscape(preview.modifierSummary()) + "</body></html>");
        }
        materialsLabel.setText(buildMaterialsHtml(selected, targetGrade));
    }

    private int selectedTargetGrade(BlueprintOption selected) {
        Object gradeSel = gradeCombo.getSelectedItem();
        if (gradeSel != null) {
            return Integer.parseInt(gradeSel.toString().substring(1));
        }
        return selected.maxGrade();
    }

    private String buildMaterialsHtml(BlueprintOption selected, int targetGrade) {
        Map<String, Integer> required = new LinkedHashMap<>();
        for (BlueprintGrade grade : database.gradesFor(selected.moduleType(), selected.blueprintName())) {
            if (grade.isExperimental()) {
                continue;
            }
            int g = grade.getGrade();
            if (g < 1 || g > targetGrade) {
                continue;
            }
            int rolls = EngineeringGradeProgress.ROLLS_PER_GRADE;
            for (MaterialRequirement mat : grade.getMaterials()) {
                required.merge(EngineeringMaterialKeys.canonicalKey(mat.getKey()), mat.getCount() * rolls, Integer::sum);
            }
        }
        if (required.isEmpty()) {
            return "<html><body style='color:#ffcc88'>No materials listed for this grade range.</body></html>";
        }
        StringBuilder sb = new StringBuilder("<html><body style='color:#ffcc88; width:420px'>Materials (G1–G")
                .append(targetGrade).append("): ");
        boolean first = true;
        for (Map.Entry<String, Integer> e : required.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(e.getValue()).append("× ").append(htmlEscape(database.materialDisplayName(e.getKey())));
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private void confirmAdd() {
        BlueprintOption selected = selectedOption();
        if (selected == null) {
            effectsLabel.setText("<html><body style='color:#ffaa66'>Select a blueprint from the list.</body></html>");
            return;
        }
        Object gradeSel = gradeCombo.getSelectedItem();
        if (gradeSel == null) {
            return;
        }
        int targetGrade = Integer.parseInt(gradeSel.toString().substring(1));
        BlueprintGrade bp = database.gradesFor(selected.moduleType(), selected.blueprintName()).stream()
                .filter(g -> g.getGrade() == targetGrade)
                .findFirst()
                .orElse(null);
        if (bp == null) {
            return;
        }
        String experimentalId = "";
        Object expSel = experimentalCombo.getSelectedItem();
        if (expSel != null && !"(none)".equals(expSel.toString())) {
            for (BlueprintGrade e : database.experimentalsFor(bp.getModuleType(), bp.getName())) {
                if (e.getName().equals(expSel.toString())) {
                    experimentalId = e.getId();
                    break;
                }
            }
        }
        result = new EngineeringGoal(
                bp.getId(),
                bp.getModuleType(),
                bp.getName(),
                0,
                targetGrade,
                experimentalId);
        dispose();
    }

    private static JLabel fieldLabel(String text, Font base, int fontSize) {
        JLabel label = new JLabel(text);
        label.setFont(base.deriveFont(Font.PLAIN, fontSize));
        label.setForeground(EdoUi.User.MAIN_TEXT);
        return label;
    }

    private static void styleTextField(JTextField field, Font base) {
        field.setFont(base);
        field.setForeground(EdoUi.User.MAIN_TEXT);
        field.setBackground(EdoUi.User.PANEL_BG);
        field.setCaretColor(EdoUi.User.MAIN_TEXT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(EdoUi.Internal.MAIN_TEXT_ALPHA_140),
                new EmptyBorder(3, 6, 3, 6)));
    }

    private static void styleCombo(JComboBox<?> combo, Font base) {
        combo.setFont(base);
        combo.setForeground(EdoUi.User.MAIN_TEXT);
        combo.setBackground(EdoUi.User.PANEL_BG);
        combo.setMaximumRowCount(12);
    }

    private static void constrainComboHeight(JComboBox<?> combo) {
        Dimension pref = combo.getPreferredSize();
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
    }

    private static String htmlEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }

    private record BlueprintOption(String moduleType, String blueprintName, int maxGrade) {
        BlueprintOption withMaxGrade(int grade) {
            return new BlueprintOption(moduleType, blueprintName, grade);
        }
    }

    private static final class BlueprintCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setForeground(EdoUi.User.MAIN_TEXT);
            setBackground(isSelected ? EdoUi.ED_ORANGE_LESS_TRANS : EdoUi.User.PANEL_BG);
            if (column == COL_GRADES && value instanceof Integer maxGrade) {
                setText("G1–G" + maxGrade);
            }
            return c;
        }
    }

    private final class BlueprintTableModel extends AbstractTableModel {
        private List<BlueprintOption> rows = List.of();

        void setRows(List<BlueprintOption> rows) {
            this.rows = rows != null ? List.copyOf(rows) : List.of();
            fireTableDataChanged();
        }

        BlueprintOption rowAt(int modelRow) {
            if (modelRow < 0 || modelRow >= rows.size()) {
                return null;
            }
            return rows.get(modelRow);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case COL_MODULE -> "Module";
                case COL_BLUEPRINT -> "Blueprint";
                case COL_GRADES -> "Grades";
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == COL_GRADES) {
                return Integer.class;
            }
            return String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            BlueprintOption opt = rows.get(rowIndex);
            return switch (columnIndex) {
                case COL_MODULE -> opt.moduleType();
                case COL_BLUEPRINT -> opt.blueprintName();
                case COL_GRADES -> opt.maxGrade();
                default -> "";
            };
        }
    }
}
