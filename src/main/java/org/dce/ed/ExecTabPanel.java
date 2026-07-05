package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.dce.ed.edsm.UtilTable;
import org.dce.ed.exec.ExecBinding;
import org.dce.ed.exec.ExecBindingsConfig;
import org.dce.ed.exec.ExecBindingsStore;
import org.dce.ed.exec.ExecReferenceHelp;
import org.dce.ed.exec.ExecTriggerId;
import org.dce.ed.exec.ExecTriggerService;
import org.dce.ed.exec.NameDescriptionHelpDialog;
import org.dce.ed.ui.HelpCircleIcon;
import org.dce.ed.exec.placeholder.ExecPlaceholderFieldSupport;
import org.dce.ed.exec.placeholder.ExecPlaceholderId;
import org.dce.ed.logreader.EliteEventType;

/**
 * Exec tab: configure programs to run on overlay triggers (fleet cooldown complete, low tritium, etc.).
 */
public final class ExecTabPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int COL_ENABLED = 0;
    private static final int COL_TRIGGER = 1;
    private static final int COL_JOURNAL_EVENT = 2;
    private static final int COL_DELAY_SEC = 3;
    private static final int COL_PROGRAM = 4;
    private static final int COL_ARGS = 5;

    private final ExecBindingsStore store = new ExecBindingsStore();
    private final ExecBindingsConfig config = store.load();
    private final BindingsTableModel tableModel = new BindingsTableModel(config.getBindings());
    private final JTable table = new JTable(tableModel);
    private final JLabel statusLabel = new JLabel(" ");
    private final JSpinner tritiumThresholdSpinner = new JSpinner(
            new SpinnerNumberModel(config.getFleetTritiumLowThreshold(), 0, 10_000, 10));
    private final JSpinner tritiumHysteresisSpinner = new JSpinner(
            new SpinnerNumberModel(config.getFleetTritiumLowHysteresis(), 0, 5_000, 10));
    private final JLabel fuelLevelLabel = new JLabel("Carrier fuel (last CarrierStats): —");

    private ExecTriggerService triggerService;
    private boolean editorsInstalled;

    public ExecTabPanel() {
        super(new BorderLayout(8, 8));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel intro = new JLabel(
                "<html>Run external programs on journal/overlay events. Pick a <code>.exe</code> "
                        + "(e.g. RoboHound) or a <code>.jar</code> (runs with bundled/Java <code>java -jar</code>). "
                        + "Use trigger <b>Journal event</b> and pick any journal event from the list. "
                        + "Type <code>$</code> in Args for game-state symbols; hover a symbol to preview its current value. "
                        + "Order must match RoboHound macro arguments after <code>--play</code>.</html>");
        intro.setOpaque(false);
        add(intro, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.setOpaque(false);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setOpaque(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.setRowHeight(Math.max(22, table.getRowHeight()));
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && table.columnAtPoint(e.getPoint()) == COL_PROGRAM) {
                    browseProgramForRow(table.rowAtPoint(e.getPoint()));
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
        JScrollPane scroll = new JScrollPane(table);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        center.add(scroll, BorderLayout.CENTER);

        JPanel tritiumPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        tritiumPanel.setOpaque(false);
        tritiumPanel.setBorder(BorderFactory.createTitledBorder("Fleet tritium low trigger"));
        tritiumPanel.add(new JLabel("Threshold (tons in tank):"));
        tritiumPanel.add(tritiumThresholdSpinner);
        tritiumPanel.add(new JLabel("Hysteresis:"));
        tritiumPanel.add(tritiumHysteresisSpinner);
        tritiumPanel.add(fuelLevelLabel);
        center.add(tritiumPanel, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        buttons.setOpaque(false);
        JButton addButton = new JButton("Add row");
        JButton removeButton = new JButton("Remove");
        JButton browseButton = new JButton("Browse program…");
        browseButton.setToolTipText("Select a table row, then pick a .exe or .jar (or double-click the Program cell)");
        JButton runNowButton = new JButton("Run now");
        buttons.add(addButton);
        buttons.add(removeButton);
        buttons.add(browseButton);
        buttons.add(runNowButton);

        JPanel helpRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        helpRow.setOpaque(false);
        JButton eventHelpButton = new JButton("Event help");
        HelpCircleIcon.applyTo(eventHelpButton);
        eventHelpButton.setToolTipText("Journal events available for the Journal event trigger");
        eventHelpButton.addActionListener(e -> NameDescriptionHelpDialog.show(this, "Journal events",
                "Event", "Description", ExecReferenceHelp.journalEventRows()));
        JButton variableHelpButton = new JButton("Variable help");
        HelpCircleIcon.applyTo(variableHelpButton);
        variableHelpButton.setToolTipText("$SYMBOL placeholders for Exec program args");
        variableHelpButton.addActionListener(e -> NameDescriptionHelpDialog.show(this, "Exec variables",
                "Variable", "Description", ExecReferenceHelp.variableRows()));
        helpRow.add(eventHelpButton);
        helpRow.add(variableHelpButton);

        JPanel south = new JPanel();
        south.setOpaque(false);
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(buttons);
        south.add(helpRow);
        statusLabel.setOpaque(false);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        south.add(statusLabel);
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

        browseButton.addActionListener(e -> browseProgramForSelectedRow());
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

    public void applyOverlayBackground(Color bgWithAlpha, boolean treatAsTransparent) {
        boolean opaque = !treatAsTransparent;
        setOpaque(opaque);
        if (opaque && bgWithAlpha != null) {
            setBackground(bgWithAlpha);
        }
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
        autoSizeExecTableColumns();
    }

    private void browseProgramForSelectedRow() {
        browseProgramForRow(resolveSelectedModelRow(true));
    }

    private void browseProgramForRow(int modelRow) {
        if (modelRow < 0 || modelRow >= config.getBindings().size()) {
            setStatus("Select a table row first (click the row), then Browse program…");
            return;
        }
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select program to run");
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("Windows executable (*.exe)", "exe"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("Java JAR (*.jar)", "jar"));
        chooser.setAcceptAllFileFilterUsed(true);
        ExecBinding binding = config.getBindings().get(modelRow);
        if (binding.getJarPath() != null && !binding.getJarPath().isBlank()) {
            File existing = new File(binding.getJarPath());
            if (existing.getParentFile() != null && existing.getParentFile().isDirectory()) {
                chooser.setCurrentDirectory(existing.getParentFile());
            }
        }
        java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
        int result = owner != null
                ? chooser.showOpenDialog(owner)
                : chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (file == null) {
            return;
        }
        binding.setJarPath(file.getAbsolutePath());
        tableModel.fireTableCellUpdated(modelRow, COL_PROGRAM);
        table.setRowSelectionInterval(
                table.convertRowIndexToView(modelRow),
                table.convertRowIndexToView(modelRow));
        table.repaint();
        persistConfig();
        setStatus("Program set: " + file.getName());
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

    private void persistConfig() {
        try {
            store.save(config);
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
            return 6;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case COL_ENABLED -> "On";
                case COL_TRIGGER -> "Trigger";
                case COL_JOURNAL_EVENT -> "Journal event";
                case COL_DELAY_SEC -> "Delay (s)";
                case COL_PROGRAM -> "Program";
                case COL_ARGS -> "Args";
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == COL_ENABLED) {
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
                return rows.get(rowIndex).getTrigger() == ExecTriggerId.JOURNAL_EVENT;
            }
            return true;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ExecBinding b = rows.get(rowIndex);
            return switch (columnIndex) {
                case COL_ENABLED -> b.isEnabled();
                case COL_TRIGGER -> b.getTrigger();
                case COL_JOURNAL_EVENT -> b.getJournalEventTypeEnum();
                case COL_DELAY_SEC -> b.getDelayMs() / 1000;
                case COL_PROGRAM -> b.getJarPath();
                case COL_ARGS -> b.getProgramArgs();
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            ExecBinding b = rows.get(rowIndex);
            switch (columnIndex) {
                case COL_ENABLED -> b.setEnabled(Boolean.TRUE.equals(value));
                case COL_TRIGGER -> {
                    if (value instanceof ExecTriggerId id) {
                        b.setTrigger(id);
                        if (id == ExecTriggerId.JOURNAL_EVENT && b.getJournalEventTypeEnum() == null) {
                            b.setJournalEventTypeEnum(EliteEventType.DOCKED);
                        }
                    }
                }
                case COL_JOURNAL_EVENT -> {
                    if (value instanceof EliteEventType type) {
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
                case COL_PROGRAM -> b.setJarPath(value != null ? value.toString() : "");
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
        JComboBox<ExecTriggerId> triggerCombo = new JComboBox<>(ExecTriggerId.configurableValues());
        triggerCombo.setOpaque(false);
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
        journalEventCombo.setOpaque(false);
        table.getColumnModel().getColumn(COL_JOURNAL_EVENT).setCellEditor(new javax.swing.DefaultCellEditor(journalEventCombo));
        table.getColumnModel().getColumn(COL_JOURNAL_EVENT).setCellRenderer(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                int modelRow = t.convertRowIndexToModel(row);
                if (modelRow >= 0 && modelRow < config.getBindings().size()
                        && config.getBindings().get(modelRow).getTrigger() != ExecTriggerId.JOURNAL_EVENT) {
                    setText("—");
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

        JCheckBox check = new JCheckBox();
        check.setOpaque(false);
        table.getColumnModel().getColumn(COL_ENABLED).setCellEditor(new javax.swing.DefaultCellEditor(check));

        JTextField programField = new JTextField();
        programField.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                int row = table.getEditingRow() >= 0 ? table.getEditingRow() : table.getSelectedRow();
                if (row >= 0 && row < config.getBindings().size()
                        && table.getEditingColumn() == COL_PROGRAM) {
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
        table.getColumnModel().getColumn(COL_PROGRAM).setCellEditor(new javax.swing.DefaultCellEditor(programField));

        JTextField argsField = new JTextField();
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
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        SwingUtilities.invokeLater(() -> UtilTable.autoSizeTableColumns(table));
    }
}
