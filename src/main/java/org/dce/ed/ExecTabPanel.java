package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;

import org.dce.ed.edsm.UtilTable;
import org.dce.ed.exec.ExecBinding;
import org.dce.ed.exec.ExecBindingsConfig;
import org.dce.ed.exec.ExecBindingsStore;
import org.dce.ed.exec.ExecJournalFilterDialog;
import org.dce.ed.exec.ExecOverlayButtonSupport;
import org.dce.ed.exec.ExecProgram;
import org.dce.ed.exec.ExecPrograms;
import org.dce.ed.exec.ExecProgramsDialog;
import org.dce.ed.exec.ExecShortcutKeys;
import org.dce.ed.exec.ExecTriggerId;
import org.dce.ed.exec.ExecReferenceHelp;
import org.dce.ed.exec.NameDescriptionHelpDialog;
import org.dce.ed.exec.ExecTriggerService;
import org.dce.ed.exec.placeholder.ExecPlaceholderId;
import org.dce.ed.exec.placeholder.ExecPlaceholderFieldSupport;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.ui.tabdock.OverlayTabId;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.HelpCircleIcon;
import org.dce.ed.ui.OverlayCheckBoxStyle;
import org.dce.ed.ui.OverlayComboBoxStyle;
import org.dce.ed.ui.OverlayFieldStyle;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.OverlayScrollPaneSupport;

/**
 * Exec tab: configure programs to run on overlay triggers (fleet cooldown complete, low tritium, etc.).
 */
public final class ExecTabPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int COL_NAME = 0;
    private static final int COL_ENABLED = 1;
    private static final int COL_CONTROL_PANEL = 2;
    private static final int COL_BUTTON_TAB = 3;
    private static final int COL_TRIGGER = 4;
    private static final int COL_JOURNAL_EVENT = 5;
    private static final int COL_DELAY_SEC = 6;
    private static final int COL_PROGRAM = 7;
    private static final int COL_ARGS = 8;

    private final ExecBindingsStore store = new ExecBindingsStore();
    private final ExecBindingsConfig config = store.load();
    private final BindingsTableModel tableModel = new BindingsTableModel(config.getBindings());
    private final JTable table = new JTable(tableModel);
    private final JScrollPane tableScroll = new JScrollPane(table);
    private final JLabel statusLabel = new JLabel(" ");
    private final JSpinner tritiumThresholdSpinner = new JSpinner(
            new SpinnerNumberModel(config.getFleetTritiumLowThreshold(), 0, 10_000, 10));
    private final JSpinner tritiumHysteresisSpinner = new JSpinner(
            new SpinnerNumberModel(config.getFleetTritiumLowHysteresis(), 0, 5_000, 10));
    private final JLabel fuelLevelLabel = new JLabel("Carrier fuel (last CarrierStats): —");
    private final JPanel tritiumPanel;
    private final java.util.List<JButton> actionButtons = new ArrayList<>();

    private ExecTriggerService triggerService;
    private boolean editorsInstalled;

    public ExecTabPanel() {
        super(new BorderLayout(8, 8));
        setOpaque(true);
        setBackground(EdoUi.User.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.setOpaque(true);
        center.setBackground(EdoUi.User.BACKGROUND);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setOpaque(true);
        table.getTableHeader().setReorderingAllowed(false);
        // Compact rows so tritium + action buttons stay visible under the table in Preferences.
        table.setRowHeight(20);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        installBindingRowReordering();
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                int row = table.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && col == COL_PROGRAM) {
                    openManageProgramsDialog();
                    return;
                }
                if (e.getClickCount() == 2 && col == COL_JOURNAL_EVENT && row >= 0 && row < config.getBindings().size()) {
                    ExecBinding binding = config.getBindings().get(row);
                    if (binding.getTrigger() == ExecTriggerId.JOURNAL_EVENT) {
                        if (ExecJournalFilterDialog.edit(SwingUtilities.getWindowAncestor(ExecTabPanel.this), binding)) {
                            persistConfig();
                            tableModel.fireTableDataChanged();
                        }
                    }
                }
            }
        });
        table.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                if (col != COL_ARGS) {
                    table.setToolTipText(null);
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0 || row >= config.getBindings().size()) {
                    table.setToolTipText(null);
                    return;
                }
                Object val = table.getValueAt(row, COL_ARGS);
                String text = val != null ? val.toString() : "";
                int colX = table.columnAtPoint(e.getPoint());
                int xInCell = e.getX() - table.getCellRect(row, colX, false).x;
                // Approximate character index for tooltip (Args uses monospace-ish default field width)
                Component renderer = table.prepareRenderer(table.getCellRenderer(row, col), row, col);
                if (renderer instanceof JLabel label) {
                    Font f = label.getFont();
                    int charIndex = Math.max(0, Math.min(text.length(), xInCell / Math.max(1, f.getSize())));
                    ExecPlaceholderId id = ExecPlaceholderFieldSupport.symbolAt(text, charIndex);
                    if (id == null) {
                        table.setToolTipText(null);
                        return;
                    }
                    Map<String, String> values = triggerService != null
                            ? triggerService.resolvePlaceholdersForUi() : Map.of();
                    table.setToolTipText(ExecPlaceholderFieldSupport.tooltipHtml(id, values));
                } else {
                    table.setToolTipText(null);
                }
            }
        });
        tableScroll.setOpaque(true);
        tableScroll.getViewport().setOpaque(true);
        OverlayScrollPaneSupport.installSubtleScrollBars(tableScroll);
        // Prefer a short viewport; BorderLayout.CENTER will grow it when space allows.
        int headerH = 24;
        int visibleRows = 6;
        tableScroll.setPreferredSize(new Dimension(640, headerH + visibleRows * table.getRowHeight() + 4));
        center.add(tableScroll, BorderLayout.CENTER);

        tritiumPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        tritiumPanel.setOpaque(true);
        tritiumPanel.setBackground(EdoUi.User.BACKGROUND);
        tritiumPanel.setBorder(BorderFactory.createTitledBorder("Fleet tritium low trigger"));
        tritiumPanel.add(new JLabel("Threshold (tons in tank):"));
        tritiumPanel.add(tritiumThresholdSpinner);
        tritiumPanel.add(new JLabel("Hysteresis:"));
        tritiumPanel.add(tritiumHysteresisSpinner);
        tritiumPanel.add(fuelLevelLabel);
        center.add(tritiumPanel, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        buttons.setOpaque(true);
        buttons.setBackground(EdoUi.User.BACKGROUND);
        JButton addButton = styleActionButton(new JButton("Add row"));
        JButton removeButton = styleActionButton(new JButton("Remove"));
        JButton manageProgramsButton = styleActionButton(new JButton("Manage programs…"));
        manageProgramsButton.setToolTipText("Edit named programs (path + unique name), or choose Add Program… in the Program column");
        JButton runNowButton = styleActionButton(new JButton("Run now"));
        JButton journalFiltersButton = styleActionButton(new JButton("Journal filters…"));
        journalFiltersButton.setToolTipText("Edit attribute filters for the selected Journal event row (or double-click Journal event column)");
        journalFiltersButton.addActionListener(e -> editJournalFiltersForSelectedRow());
        buttons.add(addButton);
        buttons.add(removeButton);
        buttons.add(manageProgramsButton);
        buttons.add(runNowButton);
        buttons.add(journalFiltersButton);

        JPanel helpRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        helpRow.setOpaque(true);
        helpRow.setBackground(EdoUi.User.BACKGROUND);
        JButton eventHelpButton = styleActionButton(new JButton("Event help"));
        HelpCircleIcon.applyTo(eventHelpButton);
        eventHelpButton.setToolTipText("Journal events available for the Journal event trigger");
        eventHelpButton.addActionListener(e -> NameDescriptionHelpDialog.show(this, "Journal events",
                "Event", "Description", ExecReferenceHelp.journalEventRows()));
        JButton variableHelpButton = styleActionButton(new JButton("Variable help"));
        HelpCircleIcon.applyTo(variableHelpButton);
        variableHelpButton.setToolTipText("$SYMBOL placeholders for Exec program args");
        variableHelpButton.addActionListener(e -> NameDescriptionHelpDialog.show(this, "Exec variables",
                "Variable", "Description", ExecReferenceHelp.variableRows()));
        helpRow.add(eventHelpButton);
        helpRow.add(variableHelpButton);

        JPanel south = new JPanel();
        south.setOpaque(true);
        south.setBackground(EdoUi.User.BACKGROUND);
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(buttons);
        south.add(helpRow);
        statusLabel.setOpaque(false);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        south.add(statusLabel);
        // Keep action rows from being compressed away when the dialog is short.
        south.setMinimumSize(new Dimension(0, 96));
        add(south, BorderLayout.SOUTH);

        addButton.addActionListener(e -> {
            config.getBindings().add(new ExecBinding());
            tableModel.fireTableDataChanged();
            int last = config.getBindings().size() - 1;
            if (last >= 0) {
                table.setRowSelectionInterval(last, last);
            }
            persistConfig();
            autoSizeExecTableColumns();
        });

        removeButton.addActionListener(e -> {
            int row = resolveSelectedModelRow(false);
            if (row < 0 || row >= config.getBindings().size()) {
                setStatus("Select a row to remove.");
                return;
            }
            config.getBindings().remove(row);
            tableModel.fireTableDataChanged();
            persistConfig();
            autoSizeExecTableColumns();
        });

        manageProgramsButton.addActionListener(e -> openManageProgramsDialog());
        runNowButton.addActionListener(e -> runSelectedRowNow());

        tritiumThresholdSpinner.addChangeListener(e -> {
            config.setFleetTritiumLowThreshold(((Number) tritiumThresholdSpinner.getValue()).intValue());
            persistConfig();
        });
        tritiumHysteresisSpinner.addChangeListener(e -> {
            config.setFleetTritiumLowHysteresis(((Number) tritiumHysteresisSpinner.getValue()).intValue());
            persistConfig();
        });

        if (config.getBindings().isEmpty()) {
            config.getBindings().add(new ExecBinding());
            tableModel.fireTableDataChanged();
        }
        if (table.getRowCount() > 0 && table.getSelectedRow() < 0) {
            table.setRowSelectionInterval(0, 0);
        }

        applyThemeColors();
    }

    /**
     * Match overlay / Preferences dark chrome: {@link EdoUi.User#MAIN_TEXT} on dark plate.
     * Call again after live color preview updates {@link EdoUi}.
     */
    public void applyThemeColors() {
        Color fg = EdoUi.User.MAIN_TEXT;
        Color bg = EdoUi.User.BACKGROUND;
        Color panel = EdoUi.User.PANEL_BG;

        setOpaque(true);
        setBackground(bg);
        statusLabel.setForeground(fg);
        fuelLevelLabel.setForeground(fg);
        themeLabelTree(tritiumPanel, fg);
        tritiumPanel.setOpaque(true);
        tritiumPanel.setBackground(bg);

        table.setForeground(fg);
        table.setBackground(bg);
        table.setSelectionForeground(bg);
        table.setSelectionBackground(fg);
        table.setGridColor(EdoUi.Internal.separatorLine());
        if (table.getTableHeader() != null) {
            table.getTableHeader().setForeground(fg);
            table.getTableHeader().setBackground(panel);
            table.getTableHeader().setOpaque(true);
        }

        Font fieldFont = OverlayPreferences.getUiFont();
        if (fieldFont == null) {
            fieldFont = getFont();
        }
        OverlayFieldStyle.applySpinner(tritiumThresholdSpinner, fieldFont);
        OverlayFieldStyle.applySpinner(tritiumHysteresisSpinner, fieldFont);

        Component scrollParent = table.getParent() != null ? table.getParent().getParent() : null;
        if (scrollParent instanceof JScrollPane pane) {
            pane.setBackground(bg);
            pane.getViewport().setBackground(bg);
            OverlayScrollPaneSupport.installSubtleScrollBars(pane);
        } else {
            tableScroll.setBackground(bg);
            tableScroll.getViewport().setBackground(bg);
            OverlayScrollPaneSupport.installSubtleScrollBars(tableScroll);
        }

        if (tritiumPanel.getBorder() instanceof javax.swing.border.TitledBorder titled) {
            titled.setTitleColor(fg);
        }
        for (JButton button : actionButtons) {
            styleActionButton(button);
        }
        revalidate();
        repaint();
    }

    private JButton styleActionButton(JButton button) {
        if (button == null) {
            return null;
        }
        Font base = OverlayPreferences.getUiFont();
        if (base == null) {
            base = getFont();
        }
        OverlayOutlineButtonStyle.applyChip(button, base, false);
        if (!actionButtons.contains(button)) {
            actionButtons.add(button);
        }
        return button;
    }

    private static void themeLabelTree(Component root, Color fg) {
        if (root instanceof JLabel label) {
            label.setForeground(fg);
        }
        if (root instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                themeLabelTree(child, fg);
            }
        }
    }

    public void setExecTriggerService(ExecTriggerService service) {
        this.triggerService = service;
        if (service != null) {
            service.setConfigSupplier(() -> config);
            service.setStatusListener(this::setStatus);
            refreshFuelLevelLabel();
        }
    }

    public void refreshFuelLevelLabel() {
        if (triggerService == null) {
            fuelLevelLabel.setText("Carrier fuel (last CarrierStats): —");
            return;
        }
        int fuel = triggerService.fuelTracker().getLastKnownFuelLevel();
        if (fuel < 0) {
            fuelLevelLabel.setText("Carrier fuel (last CarrierStats): — (open carrier management in-game)");
        } else {
            fuelLevelLabel.setText("Carrier fuel (last CarrierStats): " + fuel + " t");
        }
    }

    public void setStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(message != null ? message : " ");
            refreshFuelLevelLabel();
        });
    }

    /**
     * Commits an in-progress table edit (Program, Args, Name, etc.) into bindings and saves.
     * Needed when the user clicks OK without pressing Enter in the cell editor.
     */
    public void commitPendingEdits() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
    }

    public void applyOverlayBackground(Color bgWithAlpha, boolean treatAsTransparent) {
        boolean opaque = !treatAsTransparent;
        setOpaque(opaque);
        if (opaque && bgWithAlpha != null) {
            setBackground(bgWithAlpha);
        }
        applyThemeColors();
        revalidate();
        repaint();
    }

    public void applyUiFont(Font font) {
        if (font == null) {
            return;
        }
        setFont(font);
        statusLabel.setFont(font);
        fuelLevelLabel.setFont(font);
        table.setFont(font);
        table.getTableHeader().setFont(font);
        for (JButton button : actionButtons) {
            styleActionButton(button);
        }
        autoSizeExecTableColumns();
    }

    /** Re-measure Program / text columns after the dialog is shown or the Exec tab is selected. */
    public void refreshColumnLayout() {
        autoSizeExecTableColumns();
    }

    private void installBindingRowReordering() {
        table.setDropMode(DropMode.INSERT_ROWS);
        if (!GraphicsEnvironment.isHeadless()) {
            table.setDragEnabled(true);
        }
        table.setTransferHandler(new TransferHandler() {
            private static final long serialVersionUID = 1L;
            private int sourceModelRow = -1;

            @Override
            protected Transferable createTransferable(javax.swing.JComponent component) {
                commitPendingEdits();
                int sourceViewRow = table.getSelectedRow();
                sourceModelRow = sourceViewRow >= 0 ? table.convertRowIndexToModel(sourceViewRow) : -1;
                return sourceModelRow >= 0 ? new StringSelection(Integer.toString(sourceModelRow)) : null;
            }

            @Override
            public int getSourceActions(javax.swing.JComponent component) {
                return MOVE;
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return sourceModelRow >= 0 && support.isDrop()
                        && support.getComponent() == table
                        && support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)
                        && support.getDropLocation() instanceof JTable.DropLocation;
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                JTable.DropLocation drop = (JTable.DropLocation) support.getDropLocation();
                int insertionModelRow = drop.getRow() == table.getRowCount()
                        ? tableModel.getRowCount()
                        : table.convertRowIndexToModel(drop.getRow());
                int movedModelRow = ExecBindingOrder.move(config.getBindings(), sourceModelRow, insertionModelRow);
                if (movedModelRow == sourceModelRow) {
                    return false;
                }
                tableModel.fireTableDataChanged();
                persistConfig();
                int movedViewRow = table.convertRowIndexToView(movedModelRow);
                table.setRowSelectionInterval(movedViewRow, movedViewRow);
                table.scrollRectToVisible(table.getCellRect(movedViewRow, 0, true));
                sourceModelRow = movedModelRow;
                return true;
            }

            @Override
            protected void exportDone(javax.swing.JComponent source, Transferable data, int action) {
                sourceModelRow = -1;
            }
        });
    }

    private void openManageProgramsDialog() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
        ExecProgramsDialog.Result result = ExecProgramsDialog.show(owner, config.getPrograms());
        if (result == null) {
            return;
        }
        applyProgramsCatalog(result);
        persistConfig();
        tableModel.fireTableDataChanged();
        autoSizeExecTableColumns();
        setStatus(config.getPrograms().isEmpty()
                ? "No programs configured — choose Add Program… in the Program column."
                : "Programs updated (" + config.getPrograms().size() + ").");
    }

    private void applyProgramsCatalog(ExecProgramsDialog.Result result) {
        Map<String, String> renameMap = result.renameMap();
        config.setPrograms(new ArrayList<>(result.programs()));
        for (ExecBinding binding : config.getBindings()) {
            if (binding == null) {
                continue;
            }
            String name = binding.getProgramName();
            if (name != null && renameMap.containsKey(name)) {
                name = renameMap.get(name);
            }
            ExecProgram match = ExecPrograms.findByName(config.getPrograms(), name);
            if (match != null) {
                binding.setProgramName(match.getName());
                binding.setJarPath(match.getPath());
            } else {
                binding.setProgramName("");
                binding.setJarPath("");
            }
        }
    }

    /** @param allowDefaultToFirstRow when true and nothing is selected, use row 0 */
    private int resolveSelectedModelRow(boolean allowDefaultToFirstRow) {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0 && allowDefaultToFirstRow && !config.getBindings().isEmpty()) {
            return 0;
        }
        if (viewRow < 0) {
            return -1;
        }
        return table.convertRowIndexToModel(viewRow);
    }

    private void runSelectedRowNow() {
        int row = resolveSelectedModelRow(true);
        if (row < 0 || row >= config.getBindings().size()) {
            setStatus("Select a row to run.");
            return;
        }
        if (triggerService == null) {
            setStatus("Exec service not ready.");
            return;
        }
        triggerService.runBindingNow(config.getBindings().get(row));
    }

    private void editJournalFiltersForSelectedRow() {
        int row = resolveSelectedModelRow(false);
        if (row < 0 || row >= config.getBindings().size()) {
            setStatus("Select a Journal event row first.");
            return;
        }
        ExecBinding binding = config.getBindings().get(row);
        if (binding.getTrigger() != ExecTriggerId.JOURNAL_EVENT) {
            setStatus("Journal filters apply only to Journal event triggers.");
            return;
        }
        if (ExecJournalFilterDialog.edit(SwingUtilities.getWindowAncestor(this), binding)) {
            persistConfig();
            tableModel.fireTableDataChanged();
        }
    }

    private void persistConfig() {
        try {
            store.save(config);
            if (triggerService != null) {
                triggerService.fireBindingsChanged();
            }
        } catch (IOException e) {
            setStatus("Save failed: " + e.getMessage());
        }
    }

    private final class BindingsTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private final List<ExecBinding> rows;

        BindingsTableModel(List<ExecBinding> rows) {
            this.rows = rows;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 9;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case COL_NAME -> "Name";
                case COL_ENABLED -> "On";
                case COL_CONTROL_PANEL -> "Control Panel";
                case COL_BUTTON_TAB -> "Tab";
                case COL_TRIGGER -> "Trigger";
                case COL_JOURNAL_EVENT -> "Event / key";
                case COL_DELAY_SEC -> "Delay (s)";
                case COL_PROGRAM -> "Program";
                case COL_ARGS -> "Args";
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == COL_ENABLED || columnIndex == COL_CONTROL_PANEL) {
                return Boolean.class;
            }
            if (columnIndex == COL_DELAY_SEC) {
                return Integer.class;
            }
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= rows.size()) {
                return false;
            }
            if (columnIndex == COL_JOURNAL_EVENT) {
                ExecTriggerId trigger = rows.get(rowIndex).getTrigger();
                return trigger == ExecTriggerId.JOURNAL_EVENT || trigger == ExecTriggerId.SHORTCUT_KEY;
            }
            return true;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ExecBinding b = rows.get(rowIndex);
            return switch (columnIndex) {
                case COL_NAME -> b.getName();
                case COL_ENABLED -> b.isEnabled();
                case COL_CONTROL_PANEL -> b.isIncludeOnControlPanel();
                case COL_BUTTON_TAB -> {
                    OverlayTabId tab = ExecOverlayButtonSupport.parseButtonTab(b.getButtonTab());
                    yield tab != null ? tab : "";
                }
                case COL_TRIGGER -> b.getTrigger();
                case COL_JOURNAL_EVENT -> {
                    if (b.getTrigger() == ExecTriggerId.SHORTCUT_KEY) {
                        yield b.getShortcutKeyDisplay();
                    }
                    yield b.getJournalEventTypeEnum();
                }
                case COL_DELAY_SEC -> b.getDelayMs() / 1000;
                case COL_PROGRAM -> {
                    String programName = b.getProgramName();
                    yield programName != null ? programName : "";
                }
                case COL_ARGS -> b.getProgramArgs();
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            ExecBinding b = rows.get(rowIndex);
            switch (columnIndex) {
                case COL_NAME -> b.setName(value != null ? value.toString() : "");
                case COL_ENABLED -> b.setEnabled(Boolean.TRUE.equals(value));
                case COL_CONTROL_PANEL -> b.setIncludeOnControlPanel(Boolean.TRUE.equals(value));
                case COL_BUTTON_TAB -> {
                    if (value instanceof OverlayTabId tab) {
                        b.setButtonTab(tab.cardName());
                    } else if (value == null || value.toString().isBlank() || "None".equalsIgnoreCase(value.toString())) {
                        b.setButtonTab("");
                    } else {
                        OverlayTabId tab = ExecOverlayButtonSupport.parseButtonTab(value.toString());
                        b.setButtonTab(tab != null ? tab.cardName() : "");
                    }
                }
                case COL_TRIGGER -> {
                    if (value instanceof ExecTriggerId id) {
                        b.setTrigger(id);
                        if (id == ExecTriggerId.JOURNAL_EVENT && b.getJournalEventTypeEnum() == null) {
                            b.setJournalEventTypeEnum(EliteEventType.DOCKED);
                        }
                        if (id == ExecTriggerId.SHORTCUT_KEY && !ExecShortcutKeys.isSupported(b.getShortcutKeyCode())) {
                            b.setShortcutKeyCode(ExecShortcutKeys.DEFAULT_KEY_CODE);
                        }
                    }
                }
                case COL_JOURNAL_EVENT -> {
                    if (b.getTrigger() == ExecTriggerId.SHORTCUT_KEY) {
                        b.setShortcutKeyDisplay(value != null ? value.toString() : null);
                    } else if (value instanceof EliteEventType type) {
                        b.setJournalEventTypeEnum(type);
                    } else if (value != null) {
                        b.setJournalEventType(value.toString());
                    }
                }
                case COL_DELAY_SEC -> {
                    int sec = 0;
                    if (value instanceof Number n) {
                        sec = Math.max(0, n.intValue());
                    } else if (value != null) {
                        try {
                            sec = Math.max(0, Integer.parseInt(value.toString().trim()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    b.setDelayMs(sec * 1000);
                }
                case COL_PROGRAM -> applyProgramSelection(b, value != null ? value.toString() : "");
                case COL_ARGS -> b.setProgramArgs(value != null ? value.toString() : "");
                default -> {
                }
            }
            fireTableCellUpdated(rowIndex, columnIndex);
            if (columnIndex == COL_TRIGGER) {
                fireTableCellUpdated(rowIndex, COL_JOURNAL_EVENT);
            }
            persistConfig();
        }
    }

    private void applyProgramSelection(ExecBinding binding, String selection) {
        if (binding == null) {
            return;
        }
        if (selection == null || selection.isBlank() || ExecPrograms.ADD_PROGRAM_LABEL.equals(selection)) {
            return;
        }
        ExecProgram match = ExecPrograms.findByName(config.getPrograms(), selection);
        if (match == null) {
            return;
        }
        binding.setProgramName(match.getName());
        binding.setJarPath(match.getPath());
    }

    static {
        // placeholder — editors installed in addNotify
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!editorsInstalled) {
            installEditors();
            editorsInstalled = true;
        }
    }

    private void installEditors() {
        Font fieldFont = OverlayPreferences.getUiFont();
        if (fieldFont == null) {
            fieldFont = getFont();
        }

        JComboBox<ExecTriggerId> triggerCombo = new JComboBox<>(ExecTriggerId.configurableValues());
        OverlayComboBoxStyle.apply(triggerCombo, fieldFont);
        OverlayScrollPaneSupport.installSubtleScrollBarsOnComboPopup(triggerCombo);
        table.getColumnModel().getColumn(COL_TRIGGER).setCellEditor(new javax.swing.DefaultCellEditor(triggerCombo));
        table.getColumnModel().getColumn(COL_TRIGGER).setCellRenderer(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                if (value instanceof ExecTriggerId id) {
                    setText(id.getLabel());
                }
                return this;
            }
        });

        JComboBox<EliteEventType> journalEventCombo = new JComboBox<>(EliteEventType.execSelectableValues());
        OverlayComboBoxStyle.apply(journalEventCombo, fieldFont);
        OverlayScrollPaneSupport.installSubtleScrollBarsOnComboPopup(journalEventCombo);
        JComboBox<String> shortcutKeyCombo = new JComboBox<>(ExecShortcutKeys.displayChoices());
        OverlayComboBoxStyle.apply(shortcutKeyCombo, fieldFont);
        OverlayScrollPaneSupport.installSubtleScrollBarsOnComboPopup(shortcutKeyCombo);
        table.getColumnModel().getColumn(COL_JOURNAL_EVENT).setCellEditor(new BindingDetailCellEditor(journalEventCombo, shortcutKeyCombo));
        table.getColumnModel().getColumn(COL_JOURNAL_EVENT).setCellRenderer(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                int modelRow = t.convertRowIndexToModel(row);
                if (modelRow < 0 || modelRow >= config.getBindings().size()) {
                    setText("—");
                    return this;
                }
                ExecTriggerId trigger = config.getBindings().get(modelRow).getTrigger();
                if (trigger != ExecTriggerId.JOURNAL_EVENT && trigger != ExecTriggerId.SHORTCUT_KEY) {
                    setText("—");
                    return this;
                }
                if (trigger == ExecTriggerId.SHORTCUT_KEY) {
                    setText(value != null ? value.toString() : ExecShortcutKeys.toDisplayString(
                            config.getBindings().get(modelRow).getShortcutKeyCode()));
                    return this;
                }
                if (value instanceof EliteEventType type) {
                    setText(type.getJournalName());
                } else if (value != null) {
                    setText(value.toString());
                }
                return this;
            }
        });

        JCheckBox enabledCheck = new JCheckBox();
        OverlayCheckBoxStyle.apply(enabledCheck);
        table.setDefaultRenderer(Boolean.class, new OverlayBooleanCellRenderer());
        table.getColumnModel().getColumn(COL_ENABLED).setCellEditor(overlayCheckBoxEditor(enabledCheck));
        JCheckBox controlPanelCheck = new JCheckBox();
        OverlayCheckBoxStyle.apply(controlPanelCheck);
        table.getColumnModel().getColumn(COL_CONTROL_PANEL).setCellEditor(overlayCheckBoxEditor(controlPanelCheck));
        constrainCheckboxColumn(COL_ENABLED, 36);
        constrainCheckboxColumn(COL_CONTROL_PANEL, 96);

        JComboBox<Object> buttonTabCombo = new JComboBox<>();
        buttonTabCombo.addItem("");
        for (OverlayTabId tab : OverlayTabId.execButtonPlacementValues()) {
            buttonTabCombo.addItem(tab);
        }
        buttonTabCombo.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof OverlayTabId tab) {
                    setText(tab.label());
                } else {
                    setText("None");
                }
                return this;
            }
        });
        OverlayComboBoxStyle.apply(buttonTabCombo, fieldFont);
        OverlayScrollPaneSupport.installSubtleScrollBarsOnComboPopup(buttonTabCombo);
        table.getColumnModel().getColumn(COL_BUTTON_TAB).setCellEditor(new javax.swing.DefaultCellEditor(buttonTabCombo) {
            private static final long serialVersionUID = 1L;

            @Override
            public Object getCellEditorValue() {
                Object v = super.getCellEditorValue();
                return v instanceof OverlayTabId || v == null || "".equals(v) ? v : "";
            }
        });
        table.getColumnModel().getColumn(COL_BUTTON_TAB).setCellRenderer(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                if (value instanceof OverlayTabId tab) {
                    setText(tab.label());
                } else {
                    setText("None");
                }
                return this;
            }
        });

        table.getColumnModel().getColumn(COL_PROGRAM).setCellEditor(new ProgramCellEditor());
        table.getColumnModel().getColumn(COL_PROGRAM).setCellRenderer(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                String text = value != null ? value.toString() : "";
                setText(text.isBlank() ? "—" : text);
                return this;
            }
        });

        JTextField nameField = new JTextField();
        OverlayFieldStyle.applyTextField(nameField, fieldFont);
        table.getColumnModel().getColumn(COL_NAME).setCellEditor(new javax.swing.DefaultCellEditor(nameField));

        JTextField delayField = new JTextField();
        OverlayFieldStyle.applyTextField(delayField, fieldFont);
        table.getColumnModel().getColumn(COL_DELAY_SEC).setCellEditor(new javax.swing.DefaultCellEditor(delayField));

        JTextField argsField = new JTextField();
        OverlayFieldStyle.applyTextField(argsField, fieldFont);
        new ExecPlaceholderFieldSupport(argsField, () ->
                triggerService != null ? triggerService.resolvePlaceholdersForUi() : Map.of());
        argsField.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                int row = table.getEditingRow() >= 0 ? table.getEditingRow() : table.getSelectedRow();
                if (row >= 0 && row < config.getBindings().size()
                        && table.getEditingColumn() == COL_ARGS) {
                    persistConfig();
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                changed();
            }
        });
        table.getColumnModel().getColumn(COL_ARGS).setCellEditor(new javax.swing.DefaultCellEditor(argsField));
        autoSizeExecTableColumns();
    }

    private void autoSizeExecTableColumns() {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        SwingUtilities.invokeLater(() -> {
            UtilTable.autoSizeTableColumns(table);
            constrainCheckboxColumn(COL_ENABLED, 36);
            constrainCheckboxColumn(COL_CONTROL_PANEL, 96);
            table.revalidate();
            table.repaint();
        });
    }

    private void constrainCheckboxColumn(int modelColumn, int width) {
        if (modelColumn < 0 || modelColumn >= table.getColumnModel().getColumnCount()) {
            return;
        }
        javax.swing.table.TableColumn column = table.getColumnModel().getColumn(modelColumn);
        column.setMinWidth(width);
        column.setPreferredWidth(width);
        column.setMaxWidth(width);
    }

    private static javax.swing.DefaultCellEditor overlayCheckBoxEditor(JCheckBox checkBox) {
        return new javax.swing.DefaultCellEditor(checkBox) {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellEditorComponent(JTable table, Object value,
                    boolean isSelected, int row, int column) {
                Component c = super.getTableCellEditorComponent(table, value, isSelected, row, column);
                if (c instanceof JCheckBox editorCheck) {
                    OverlayCheckBoxStyle.apply(editorCheck);
                    editorCheck.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
                }
                return c;
            }
        };
    }

    private static final class OverlayBooleanCellRenderer extends JCheckBox
            implements javax.swing.table.TableCellRenderer {
        private static final long serialVersionUID = 1L;

        OverlayBooleanCellRenderer() {
            OverlayCheckBoxStyle.apply(this);
            setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            setSelected(value instanceof Boolean b && b);
            return this;
        }
    }

    private final class ProgramCellEditor extends AbstractCellEditor implements TableCellEditor {

        private static final long serialVersionUID = 1L;

        private final JComboBox<String> combo = new JComboBox<>();
        private String committedValue = "";
        private boolean ignoreAction;

        ProgramCellEditor() {
            Font fieldFont = OverlayPreferences.getUiFont();
            if (fieldFont == null) {
                fieldFont = ExecTabPanel.this.getFont();
            }
            OverlayComboBoxStyle.apply(combo, fieldFont);
            OverlayScrollPaneSupport.installSubtleScrollBarsOnComboPopup(combo);
            combo.addActionListener(e -> {
                if (ignoreAction) {
                    return;
                }
                Object selected = combo.getSelectedItem();
                if (selected == null) {
                    return;
                }
                String value = selected.toString();
                if (ExecPrograms.ADD_PROGRAM_LABEL.equals(value)) {
                    SwingUtilities.invokeLater(() -> {
                        fireEditingCanceled();
                        openManageProgramsDialog();
                    });
                    return;
                }
                committedValue = value;
                fireEditingStopped();
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable t, Object value, boolean isSelected, int row, int column) {
            ignoreAction = true;
            combo.removeAllItems();
            for (ExecProgram program : config.getPrograms()) {
                if (program != null && !program.getName().isBlank()) {
                    combo.addItem(program.getName());
                }
            }
            combo.addItem(ExecPrograms.ADD_PROGRAM_LABEL);
            String current = value != null ? value.toString() : "";
            committedValue = current;
            if (!current.isBlank()) {
                combo.setSelectedItem(current);
            } else if (combo.getItemCount() > 1) {
                combo.setSelectedIndex(-1);
            } else {
                combo.setSelectedItem(ExecPrograms.ADD_PROGRAM_LABEL);
            }
            ignoreAction = false;
            return combo;
        }

        @Override
        public Object getCellEditorValue() {
            return committedValue;
        }
    }

    private final class BindingDetailCellEditor extends AbstractCellEditor implements TableCellEditor {

        private static final long serialVersionUID = 1L;

        private final JComboBox<EliteEventType> journalCombo;
        private final JComboBox<String> shortcutCombo;
        private JComboBox<?> activeCombo;

        BindingDetailCellEditor(JComboBox<EliteEventType> journalCombo, JComboBox<String> shortcutCombo) {
            this.journalCombo = journalCombo;
            this.shortcutCombo = shortcutCombo;
            java.awt.event.ActionListener stop = e -> fireEditingStopped();
            journalCombo.addActionListener(stop);
            shortcutCombo.addActionListener(stop);
        }

        @Override
        public Component getTableCellEditorComponent(JTable t, Object value, boolean isSelected, int row, int column) {
            int modelRow = t.convertRowIndexToModel(row);
            ExecTriggerId trigger = modelRow >= 0 && modelRow < config.getBindings().size()
                    ? config.getBindings().get(modelRow).getTrigger() : ExecTriggerId.JOURNAL_EVENT;
            if (trigger == ExecTriggerId.SHORTCUT_KEY) {
                activeCombo = shortcutCombo;
                String display = value != null ? value.toString()
                        : config.getBindings().get(modelRow).getShortcutKeyDisplay();
                shortcutCombo.setSelectedItem(display);
                return shortcutCombo;
            }
            activeCombo = journalCombo;
            if (value instanceof EliteEventType type) {
                journalCombo.setSelectedItem(type);
            } else if (value != null) {
                EliteEventType parsed = EliteEventType.fromJournalName(value.toString());
                journalCombo.setSelectedItem(parsed != EliteEventType.UNKNOWN ? parsed : EliteEventType.DOCKED);
            }
            return journalCombo;
        }

        @Override
        public Object getCellEditorValue() {
            if (activeCombo == shortcutCombo) {
                return shortcutCombo.getSelectedItem();
            }
            return journalCombo.getSelectedItem();
        }
    }
}
