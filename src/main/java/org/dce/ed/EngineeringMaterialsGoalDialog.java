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
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

import org.dce.ed.engineering.EngineeringDatabase;
import org.dce.ed.engineering.EngineeringMaterial;
import org.dce.ed.engineering.EngineeringMaterialKeys;
import org.dce.ed.engineering.EngineeringShipCatalog;
import org.dce.ed.engineering.EngineeringShipRef;
import org.dce.ed.engineering.GoalPriority;
import org.dce.ed.engineering.MaterialRequirement;
import org.dce.ed.engineering.MaterialTraderCatalog;
import org.dce.ed.engineering.MaterialsGoal;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.HoverClickPoller;
import org.dce.ed.ui.OverlayCheckBoxStyle;
import org.dce.ed.ui.OverlayComboBoxStyle;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.TableHeaderSortSupport;

/**
 * Add / edit a materials reserve goal (mission requests, stockpile targets).
 */
final class EngineeringMaterialsGoalDialog extends JDialog {

    private enum Mode { ADD, EDIT }

    private static final int HOVER_CLICK_DELAY_MS = 500;
    private static final int HEADER_SORT_HOVER_MS = 500;
    private static final EngineeringShipRef NO_SHIP =
            new EngineeringShipRef(EngineeringShipRef.UNKNOWN_SHIP_ID, "", "(no ship)", "");

    private final EngineeringDatabase database;
    private final BooleanSupplier passThroughEnabledSupplier;
    private final Mode mode;
    private final MaterialsGoal editSource;
    private final EngineeringShipCatalog shipCatalog;
    private final Map<String, Integer> inventory;

    private final JTextField labelField = new JTextField(28);
    private final JComboBox<EngineeringShipRef> shipCombo = new JComboBox<>();
    private final JTextField filterField = new JTextField(20);
    private final JComboBox<String> typeFilter = new JComboBox<>(new String[] {
            "All", "Raw", "Manufactured", "Encoded"
    });
    private final JCheckBox showNonTradeableCheck = new JCheckBox("Show Guardian / other");
    private final JSpinner addQtySpinner =
            new JSpinner(new SpinnerNumberModel(1, 1, MaterialsGoal.MAX_MATERIAL_COUNT, 1));

    private final CatalogTableModel catalogModel = new CatalogTableModel();
    private final JTable catalogTable = new JTable(catalogModel);
    private final TableRowSorter<CatalogTableModel> catalogSorter = new TableRowSorter<>(catalogModel);

    private final SelectedTableModel selectedModel = new SelectedTableModel();
    private final JTable selectedTable = new JTable(selectedModel);

    private final JLabel hintLabel = new JLabel(" ");

    private MaterialsGoal result;

    private EngineeringMaterialsGoalDialog(Window owner,
                                           EngineeringDatabase database,
                                           BooleanSupplier passThroughEnabledSupplier,
                                           Mode mode,
                                           MaterialsGoal editSource,
                                           EngineeringShipCatalog shipCatalog,
                                           EngineeringShipRef defaultShip,
                                           Map<String, Integer> inventory) {
        super(owner, mode == Mode.EDIT ? "Edit materials goal" : "Add materials goal",
                ModalityType.APPLICATION_MODAL);
        this.database = database != null ? database : EngineeringDatabase.getInstance();
        this.passThroughEnabledSupplier = passThroughEnabledSupplier;
        this.mode = mode;
        this.editSource = editSource;
        this.shipCatalog = shipCatalog;
        this.inventory = inventory != null ? inventory : Map.of();
        buildUi(defaultShip);
        if (mode == Mode.EDIT && editSource != null) {
            applyEditSource(editSource);
        }
        reloadCatalog();
        pack();
        setLocationRelativeTo(owner);
        setAlwaysOnTop(true);
    }

    static MaterialsGoal showForAdd(Window owner,
                                    EngineeringDatabase database,
                                    BooleanSupplier passThroughEnabledSupplier,
                                    EngineeringShipCatalog shipCatalog,
                                    EngineeringShipRef defaultShip,
                                    Map<String, Integer> inventory) {
        EngineeringMaterialsGoalDialog dialog = new EngineeringMaterialsGoalDialog(
                owner, database, passThroughEnabledSupplier, Mode.ADD, null,
                shipCatalog, defaultShip, inventory);
        dialog.setVisible(true);
        return dialog.result;
    }

    static MaterialsGoal showForEdit(Window owner,
                                     EngineeringDatabase database,
                                     BooleanSupplier passThroughEnabledSupplier,
                                     EngineeringShipCatalog shipCatalog,
                                     MaterialsGoal existing,
                                     Map<String, Integer> inventory) {
        if (existing == null) {
            return null;
        }
        EngineeringShipRef def = existing.hasShip()
                ? (shipCatalog != null ? shipCatalog.get(existing.getShipId()) : null)
                : NO_SHIP;
        if (def == null && existing.hasShip()) {
            def = new EngineeringShipRef(existing.getShipId(), "", existing.getShipLabel(), "");
        }
        EngineeringMaterialsGoalDialog dialog = new EngineeringMaterialsGoalDialog(
                owner, database, passThroughEnabledSupplier, Mode.EDIT, existing,
                shipCatalog, def != null ? def : NO_SHIP, inventory);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void applyEditSource(MaterialsGoal source) {
        labelField.setText(source.getLabel());
        selectedModel.setRows(source.getMaterials());
    }

    private void buildUi(EngineeringShipRef defaultShip) {
        Font base = OverlayPreferences.getUiFont();
        int fontSize = OverlayPreferences.getUiFontSize();

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setOpaque(true);
        root.setBackground(EdoUi.User.BACKGROUND);
        root.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel intro = new JLabel(mode == Mode.EDIT
                ? "Update the label, ship, or target stock for this goal."
                : "Add materials you still need. Need is how many more to acquire (on top of Owned); "
                        + "targets also stack on engineering goals.");
        intro.setFont(base.deriveFont(Font.PLAIN, fontSize));
        intro.setForeground(EdoUi.User.MAIN_TEXT);

        JPanel top = new JPanel(new GridBagLayout());
        top.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        top.add(fieldLabel("Label:", base, fontSize), gbc);
        styleTextField(labelField, base);
        labelField.setToolTipText("Required name, e.g. Mission request");
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        top.add(labelField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        top.add(fieldLabel("Ship:", base, fontSize), gbc);
        OverlayComboBoxStyle.apply(shipCombo, base);
        shipCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof EngineeringShipRef ship) {
                    if (!ship.isKnown()) {
                        setText("(no ship)");
                    } else {
                        setText(shipCatalog != null
                                ? shipCatalog.displayLabel(ship)
                                : ship.displayLabel());
                    }
                }
                setForeground(EdoUi.User.MAIN_TEXT);
                setBackground(isSelected ? EdoUi.ED_ORANGE_LESS_TRANS : EdoUi.User.PANEL_BG);
                return c;
            }
        });
        populateShipCombo(defaultShip);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        top.add(shipCombo, gbc);

        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.setOpaque(false);
        north.add(intro, BorderLayout.NORTH);
        north.add(top, BorderLayout.CENTER);
        root.add(north, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(8, 6));
        center.setOpaque(false);

        JPanel picker = new JPanel(new BorderLayout(4, 4));
        picker.setOpaque(false);
        picker.add(sectionHeader("Materials", base, fontSize), BorderLayout.NORTH);

        OverlayCheckBoxStyle.apply(showNonTradeableCheck);
        showNonTradeableCheck.setFont(base.deriveFont(Font.PLAIN, fontSize));
        showNonTradeableCheck.setToolTipText("Include Guardian, Thargoid, and other non-trader materials");
        showNonTradeableCheck.addItemListener(e -> reloadCatalog());
        HoverClickPoller.register(
                showNonTradeableCheck,
                HOVER_CLICK_DELAY_MS,
                () -> showNonTradeableCheck.setSelected(!showNonTradeableCheck.isSelected()),
                passThroughEnabledSupplier);

        styleTextField(filterField, base);
        filterField.setToolTipText("Filter by material name");
        OverlayComboBoxStyle.apply(typeFilter, base);
        typeFilter.addActionListener(e -> reloadCatalog());
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { reloadCatalog(); }
            @Override public void removeUpdate(DocumentEvent e) { reloadCatalog(); }
            @Override public void changedUpdate(DocumentEvent e) { reloadCatalog(); }
        });

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterRow.setOpaque(false);
        filterRow.add(filterField);
        filterRow.add(typeFilter);
        filterRow.add(showNonTradeableCheck);
        picker.add(filterRow, BorderLayout.CENTER);

        configureCatalogTable(base, fontSize);
        JScrollPane catalogScroll = new JScrollPane(catalogTable);
        catalogScroll.setPreferredSize(new Dimension(520, 180));
        catalogScroll.setBorder(BorderFactory.createLineBorder(EdoUi.Internal.MAIN_TEXT_ALPHA_140));

        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        addRow.setOpaque(false);
        addRow.add(fieldLabel(mode == Mode.EDIT ? "Target:" : "Need:", base, fontSize));
        styleSpinner(addQtySpinner, base);
        addQtySpinner.setToolTipText(mode == Mode.EDIT
                ? "Absolute target stock to hold for this goal"
                : "How many more you need to acquire (target stock = Owned + Need)");
        addRow.add(addQtySpinner);
        JButton addMatBtn = new JButton("Add to goal");
        OverlayOutlineButtonStyle.applyChip(addMatBtn, base, false);
        addMatBtn.addActionListener(e -> addSelectedCatalogMaterial());
        HoverClickPoller.register(addMatBtn, HOVER_CLICK_DELAY_MS, this::addSelectedCatalogMaterial,
                passThroughEnabledSupplier);
        addRow.add(addMatBtn);

        JPanel catalogBlock = new JPanel(new BorderLayout(4, 4));
        catalogBlock.setOpaque(false);
        catalogBlock.add(picker, BorderLayout.NORTH);
        catalogBlock.add(catalogScroll, BorderLayout.CENTER);
        catalogBlock.add(addRow, BorderLayout.SOUTH);
        center.add(catalogBlock, BorderLayout.CENTER);

        configureSelectedTable(base, fontSize);
        JScrollPane selectedScroll = new JScrollPane(selectedTable);
        selectedScroll.setPreferredSize(new Dimension(520, 110));
        selectedScroll.setBorder(BorderFactory.createLineBorder(EdoUi.Internal.MAIN_TEXT_ALPHA_140));
        JButton removeBtn = new JButton("Remove selected");
        OverlayOutlineButtonStyle.applyChip(removeBtn, base, false);
        removeBtn.addActionListener(e -> removeSelectedMaterial());
        HoverClickPoller.register(removeBtn, HOVER_CLICK_DELAY_MS, this::removeSelectedMaterial,
                passThroughEnabledSupplier);
        JPanel selectedSouth = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        selectedSouth.setOpaque(false);
        selectedSouth.add(removeBtn);
        JPanel selectedBlock = new JPanel(new BorderLayout(4, 4));
        selectedBlock.setOpaque(false);
        selectedBlock.add(sectionHeader(
                mode == Mode.EDIT ? "Target stock for this goal" : "Materials to acquire",
                base, fontSize), BorderLayout.NORTH);
        selectedBlock.add(selectedScroll, BorderLayout.CENTER);
        selectedBlock.add(selectedSouth, BorderLayout.SOUTH);
        center.add(selectedBlock, BorderLayout.SOUTH);

        root.add(center, BorderLayout.CENTER);

        hintLabel.setFont(base.deriveFont(Font.PLAIN, fontSize));
        hintLabel.setForeground(EdoUi.User.ERROR);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton saveBtn = new JButton(mode == Mode.EDIT ? "Save" : "Add goal");
        OverlayOutlineButtonStyle.applyChip(saveBtn, base, false);
        saveBtn.addActionListener(e -> confirmSave());
        HoverClickPoller.register(saveBtn, HOVER_CLICK_DELAY_MS, this::confirmSave, passThroughEnabledSupplier);
        JButton cancelBtn = new JButton("Cancel");
        OverlayOutlineButtonStyle.applyChip(cancelBtn, base, false);
        cancelBtn.addActionListener(e -> dispose());
        HoverClickPoller.register(cancelBtn, HOVER_CLICK_DELAY_MS, this::dispose, passThroughEnabledSupplier);
        buttons.add(saveBtn);
        buttons.add(cancelBtn);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(hintLabel, BorderLayout.WEST);
        south.add(buttons, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        setMinimumSize(new Dimension(620, 560));
        getRootPane().setDefaultButton(saveBtn);
    }

    private void populateShipCombo(EngineeringShipRef defaultShip) {
        shipCombo.removeAllItems();
        shipCombo.addItem(NO_SHIP);
        if (shipCatalog != null) {
            for (EngineeringShipRef ref : shipCatalog.listSorted()) {
                shipCombo.addItem(ref);
            }
        }
        if (defaultShip != null && defaultShip.isKnown()) {
            boolean found = false;
            for (int i = 0; i < shipCombo.getItemCount(); i++) {
                EngineeringShipRef s = shipCombo.getItemAt(i);
                if (s != null && s.getShipId() == defaultShip.getShipId()) {
                    shipCombo.setSelectedIndex(i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                shipCombo.addItem(defaultShip);
                shipCombo.setSelectedItem(defaultShip);
            }
        } else {
            shipCombo.setSelectedItem(NO_SHIP);
        }
    }

    private void configureCatalogTable(Font base, int fontSize) {
        catalogTable.setFont(base.deriveFont(Font.PLAIN, fontSize));
        catalogTable.setRowHeight(Math.max(22, fontSize + 8));
        catalogTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        catalogTable.setAutoCreateRowSorter(false);
        catalogTable.setRowSorter(catalogSorter);
        catalogSorter.setComparator(0, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        catalogSorter.setComparator(1, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        catalogSorter.setComparator(2, Comparator.nullsLast(Comparator.naturalOrder()));
        catalogSorter.setComparator(3, Comparator.nullsLast(Comparator.naturalOrder()));
        TableHeaderSortSupport.install(catalogTable, passThroughEnabledSupplier, HEADER_SORT_HOVER_MS);
        DefaultTableCellRenderer left = new DefaultTableCellRenderer();
        left.setHorizontalAlignment(SwingConstants.LEFT);
        catalogTable.setDefaultRenderer(Object.class, left);
        catalogTable.setDefaultRenderer(Integer.class, new DefaultTableCellRenderer() {
            {
                setHorizontalAlignment(SwingConstants.RIGHT);
            }
        });
        catalogTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    addSelectedCatalogMaterial();
                }
            }
        });
    }

    private void configureSelectedTable(Font base, int fontSize) {
        selectedTable.setFont(base.deriveFont(Font.PLAIN, fontSize));
        selectedTable.setRowHeight(Math.max(22, fontSize + 8));
        selectedTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        selectedTable.getColumnModel().getColumn(1).setPreferredWidth(70);
        selectedTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer());
    }

    private void reloadCatalog() {
        String type = (String) typeFilter.getSelectedItem();
        String q = filterField.getText() != null ? filterField.getText().trim().toLowerCase(Locale.ROOT) : "";
        boolean includeNonTradeable = showNonTradeableCheck.isSelected();
        List<CatalogRow> rows = new ArrayList<>();
        for (EngineeringMaterial mat : database.getAllMaterials()) {
            if (mat == null) {
                continue;
            }
            boolean tradeable = MaterialTraderCatalog.isTradeableAtMaterialTrader(mat);
            if (!includeNonTradeable && !tradeable) {
                continue;
            }
            if (type != null && !"All".equals(type) && !type.equalsIgnoreCase(mat.getType())) {
                continue;
            }
            String name = mat.getName();
            if (!q.isEmpty()
                    && !name.toLowerCase(Locale.ROOT).contains(q)
                    && !mat.getKey().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            int owned = EngineeringMaterialKeys.countInInventory(inventory, mat.getKey());
            rows.add(new CatalogRow(mat.getKey(), name, mat.getType(), mat.getGrade(), owned));
        }
        rows.sort(Comparator
                .comparing(CatalogRow::type, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(CatalogRow::grade)
                .thenComparing(CatalogRow::name, String.CASE_INSENSITIVE_ORDER));
        catalogModel.setRows(rows);
    }

    private void addSelectedCatalogMaterial() {
        int viewRow = catalogTable.getSelectedRow();
        if (viewRow < 0) {
            hintLabel.setText("Select a material from the list.");
            return;
        }
        int modelRow = catalogTable.convertRowIndexToModel(viewRow);
        CatalogRow row = catalogModel.rowAt(modelRow);
        if (row == null) {
            return;
        }
        try {
            // JSpinner does not commit typed editor text until Enter/focus loss by default.
            addQtySpinner.commitEdit();
        } catch (ParseException ex) {
            hintLabel.setText("Enter a quantity from 1 to " + MaterialsGoal.MAX_MATERIAL_COUNT + ".");
            return;
        }
        int qty = ((Number) addQtySpinner.getValue()).intValue();
        String key = row.key();
        int owned = row.owned();
        int previousTarget = selectedModel.countForKey(key);
        int newTarget;
        if (mode == Mode.ADD) {
            // Spinner is "how many more to acquire". First add sets Owned+Need; later adds raise the target by Need.
            newTarget = previousTarget > 0
                    ? previousTarget + qty
                    : owned + qty;
        } else {
            // Edit: spinner is absolute target stock; Add merges by raising the target.
            newTarget = previousTarget > 0 ? previousTarget + qty : qty;
        }
        newTarget = Math.min(MaterialsGoal.MAX_MATERIAL_COUNT, newTarget);
        selectedModel.setCount(key, row.name(), newTarget);
        int stillNeed = Math.max(0, newTarget - owned);
        if (stillNeed <= 0) {
            hintLabel.setForeground(EdoUi.User.ERROR);
            hintLabel.setText("Already at target for " + row.name()
                    + " — raise Need to get trade suggestions.");
        } else {
            hintLabel.setForeground(EdoUi.User.MAIN_TEXT);
            hintLabel.setText(row.name() + ": own " + owned
                    + " → target " + newTarget
                    + " (need " + stillNeed + " more)");
        }
    }

    private void removeSelectedMaterial() {
        int viewRow = selectedTable.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        selectedModel.removeAt(viewRow);
    }

    private void confirmSave() {
        String label = labelField.getText() != null ? labelField.getText().trim() : "";
        if (label.isBlank()) {
            hintLabel.setForeground(EdoUi.User.ERROR);
            hintLabel.setText("Enter a label for this materials goal.");
            labelField.requestFocusInWindow();
            return;
        }
        List<MaterialRequirement> mats = selectedModel.toRequirements();
        if (mats.isEmpty()) {
            hintLabel.setForeground(EdoUi.User.ERROR);
            hintLabel.setText("Add at least one material.");
            return;
        }
        EngineeringShipRef ship = (EngineeringShipRef) shipCombo.getSelectedItem();
        long shipId = ship != null && ship.isKnown() ? ship.getShipId() : EngineeringShipRef.UNKNOWN_SHIP_ID;
        String shipLabel = "";
        if (ship != null && ship.isKnown()) {
            shipLabel = shipCatalog != null ? shipCatalog.displayLabel(ship) : ship.displayLabel();
        }
        GoalPriority priority = mode == Mode.EDIT && editSource != null
                ? editSource.getPriority()
                : GoalPriority.MEDIUM;
        boolean enabled = mode != Mode.EDIT || editSource == null || editSource.isEnabled();
        result = new MaterialsGoal(label, mats, priority, enabled, shipId, shipLabel);
        dispose();
    }

    private static JLabel fieldLabel(String text, Font base, int fontSize) {
        JLabel label = new JLabel(text);
        label.setFont(base.deriveFont(Font.PLAIN, fontSize));
        label.setForeground(EdoUi.User.MAIN_TEXT);
        return label;
    }

    private static JLabel sectionHeader(String text, Font base, int fontSize) {
        JLabel label = new JLabel(text);
        label.setFont(base.deriveFont(Font.BOLD, fontSize + 1));
        label.setForeground(EdoUi.User.MAIN_TEXT);
        label.setBorder(new EmptyBorder(4, 0, 2, 0));
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

    private static void styleSpinner(JSpinner spinner, Font base) {
        spinner.setFont(base);
        spinner.setForeground(EdoUi.User.MAIN_TEXT);
        spinner.setBackground(EdoUi.User.PANEL_BG);
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().setForeground(EdoUi.User.MAIN_TEXT);
            editor.getTextField().setBackground(EdoUi.User.PANEL_BG);
            editor.getTextField().setCaretColor(EdoUi.User.MAIN_TEXT);
        }
    }

    private record CatalogRow(String key, String name, String type, int grade, int owned) {
    }

    private static final class CatalogTableModel extends AbstractTableModel {
        private List<CatalogRow> rows = List.of();

        void setRows(List<CatalogRow> rows) {
            this.rows = rows != null ? List.copyOf(rows) : List.of();
            fireTableDataChanged();
        }

        CatalogRow rowAt(int index) {
            if (index < 0 || index >= rows.size()) {
                return null;
            }
            return rows.get(index);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex >= 2 ? Integer.class : String.class;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "Material";
                case 1 -> "Type";
                case 2 -> "Grade";
                case 3 -> "Owned";
                default -> "";
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            CatalogRow r = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> r.name();
                case 1 -> r.type();
                case 2 -> Integer.valueOf(r.grade());
                case 3 -> Integer.valueOf(r.owned());
                default -> "";
            };
        }
    }

    private final class SelectedTableModel extends AbstractTableModel {
        private final List<SelectedRow> rows = new ArrayList<>();

        void setRows(List<MaterialRequirement> requirements) {
            rows.clear();
            if (requirements != null) {
                for (MaterialRequirement req : requirements) {
                    if (req == null || req.getKey().isBlank() || req.getCount() <= 0) {
                        continue;
                    }
                    rows.add(new SelectedRow(
                            req.getKey(),
                            database.materialDisplayName(req.getKey()),
                            req.getCount()));
                }
            }
            fireTableDataChanged();
        }

        int countForKey(String key) {
            String canonical = EngineeringMaterialKeys.canonicalKey(key);
            for (SelectedRow row : rows) {
                if (row.key().equals(canonical)) {
                    return row.count();
                }
            }
            return 0;
        }

        void setCount(String key, String name, int count) {
            String canonical = EngineeringMaterialKeys.canonicalKey(key);
            int clamped = Math.max(1, Math.min(MaterialsGoal.MAX_MATERIAL_COUNT, count));
            for (int i = 0; i < rows.size(); i++) {
                SelectedRow existing = rows.get(i);
                if (existing.key().equals(canonical)) {
                    rows.set(i, new SelectedRow(canonical, existing.name(), clamped));
                    fireTableRowsUpdated(i, i);
                    return;
                }
            }
            rows.add(new SelectedRow(canonical, name, clamped));
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        void addOrMerge(String key, String name, int qty) {
            setCount(key, name, countForKey(key) + qty);
        }

        void removeAt(int index) {
            if (index < 0 || index >= rows.size()) {
                return;
            }
            rows.remove(index);
            fireTableDataChanged();
        }

        List<MaterialRequirement> toRequirements() {
            List<MaterialRequirement> out = new ArrayList<>(rows.size());
            for (SelectedRow row : rows) {
                out.add(new MaterialRequirement(row.key(), row.count()));
            }
            return out;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 1;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 1 ? Integer.class : String.class;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Material" : "Target";
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            SelectedRow r = rows.get(rowIndex);
            return columnIndex == 0 ? r.name() : Integer.valueOf(r.count());
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex != 1 || rowIndex < 0 || rowIndex >= rows.size()) {
                return;
            }
            int qty = 1;
            if (aValue instanceof Number n) {
                qty = n.intValue();
            } else if (aValue != null) {
                try {
                    qty = Integer.parseInt(aValue.toString().trim());
                } catch (NumberFormatException ignored) {
                    return;
                }
            }
            qty = Math.max(1, Math.min(MaterialsGoal.MAX_MATERIAL_COUNT, qty));
            SelectedRow prev = rows.get(rowIndex);
            rows.set(rowIndex, new SelectedRow(prev.key(), prev.name(), qty));
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }

    private record SelectedRow(String key, String name, int count) {
    }
}
