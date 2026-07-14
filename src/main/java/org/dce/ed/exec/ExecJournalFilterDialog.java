package org.dce.ed.exec;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import org.dce.ed.exec.ExecJournalAttributeFilter.MatchMode;
import org.dce.ed.ui.OverlayScrollPaneSupport;

/** Edit journal attribute filters on an {@link ExecBinding}. */
public final class ExecJournalFilterDialog extends JDialog {

    private static final int COL_FIELD = 0;
    private static final int COL_VALUE = 1;
    private static final int COL_MODE = 2;

    private final ExecBinding binding;
    private final FiltersTableModel filtersModel = new FiltersTableModel();
    private final JTable filtersTable = new JTable(filtersModel);
    private boolean saved;

    private ExecJournalFilterDialog(Window owner, ExecBinding binding) {
        super(owner, "Journal event filters", ModalityType.APPLICATION_MODAL);
        this.binding = binding;
        buildUi();
        filtersModel.setRows(new ArrayList<>(binding.getJournalAttributeFilters()));
        pack();
        setLocationRelativeTo(owner);
    }

    public static boolean edit(Window owner, ExecBinding binding) {
        if (binding == null) {
            return false;
        }
        ExecJournalFilterDialog dialog = new ExecJournalFilterDialog(owner, binding);
        dialog.setVisible(true);
        return dialog.saved;
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(new JLabel("<html>Filters for <b>" + binding.getJournalEventType()
                + "</b> (all must match; empty = any event of this type)</html>"), BorderLayout.NORTH);

        filtersTable.setRowHeight(22);
        JComboBox<MatchMode> modeCombo = new JComboBox<>(MatchMode.values());
        filtersTable.getColumnModel().getColumn(COL_MODE).setCellEditor(new DefaultCellEditor(modeCombo));
        JScrollPane filtersScroll = new JScrollPane(filtersTable);
        OverlayScrollPaneSupport.installSubtleScrollBars(filtersScroll);
        root.add(filtersScroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton add = new JButton("Add filter");
        add.addActionListener(e -> filtersModel.addRow(new ExecJournalAttributeFilter()));
        JButton remove = new JButton("Remove");
        remove.addActionListener(e -> {
            int row = filtersTable.getSelectedRow();
            if (row >= 0) {
                filtersModel.removeRow(row);
            }
        });
        JButton browse = new JButton("Browse journal examples…");
        browse.addActionListener(e -> ExecJournalExamplePickerDialog.show(this, binding.getJournalEventType(),
                filter -> {
                    int existing = filtersModel.indexOfField(filter.getField());
                    if (existing >= 0) {
                        if (JOptionPane.showConfirmDialog(this, "Replace filter for \"" + filter.getField() + "\"?",
                                "Replace", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                            filtersModel.setRow(existing, filter);
                        }
                    } else {
                        filtersModel.addRow(filter);
                    }
                }));
        buttons.add(add);
        buttons.add(remove);
        buttons.add(browse);
        root.add(buttons, BorderLayout.SOUTH);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        ok.addActionListener(e -> {
            commitPendingEdits();
            binding.setJournalAttributeFilters(filtersModel.getRows());
            saved = true;
            dispose();
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        south.add(ok);
        south.add(cancel);
        root.add(south, BorderLayout.PAGE_END);

        setContentPane(root);
    }

    private void commitPendingEdits() {
        if (filtersTable.isEditing()) {
            filtersTable.getCellEditor().stopCellEditing();
        }
    }

    private static final class FiltersTableModel extends AbstractTableModel {

        private final List<ExecJournalAttributeFilter> rows = new ArrayList<>();

        void setRows(List<ExecJournalAttributeFilter> filters) {
            rows.clear();
            if (filters != null) {
                rows.addAll(filters);
            }
            fireTableDataChanged();
        }

        List<ExecJournalAttributeFilter> getRows() {
            return new ArrayList<>(rows);
        }

        void addRow(ExecJournalAttributeFilter filter) {
            rows.add(filter != null ? filter : new ExecJournalAttributeFilter());
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        void setRow(int index, ExecJournalAttributeFilter filter) {
            if (index >= 0 && index < rows.size() && filter != null) {
                rows.set(index, filter);
                fireTableRowsUpdated(index, index);
            }
        }

        void removeRow(int index) {
            if (index >= 0 && index < rows.size()) {
                rows.remove(index);
                fireTableDataChanged();
            }
        }

        int indexOfField(String field) {
            for (int i = 0; i < rows.size(); i++) {
                if (field != null && field.equals(rows.get(i).getField())) {
                    return i;
                }
            }
            return -1;
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
                case COL_FIELD -> "Field";
                case COL_VALUE -> "Expected value";
                case COL_MODE -> "Mode";
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == COL_MODE) {
                return MatchMode.class;
            }
            return String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ExecJournalAttributeFilter f = rows.get(rowIndex);
            return switch (columnIndex) {
                case COL_FIELD -> f.getField();
                case COL_VALUE -> f.getExpectedValue();
                case COL_MODE -> f.getMatchMode() != null ? f.getMatchMode() : MatchMode.EQUALS;
                default -> "";
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            ExecJournalAttributeFilter f = rows.get(rowIndex);
            switch (columnIndex) {
                case COL_FIELD -> f.setField(value != null ? value.toString() : "");
                case COL_VALUE -> f.setExpectedValue(value != null ? value.toString() : "");
                case COL_MODE -> {
                    if (value instanceof MatchMode mode) {
                        f.setMatchMode(mode);
                    } else if (value != null) {
                        try {
                            f.setMatchMode(MatchMode.valueOf(value.toString().trim()));
                        } catch (IllegalArgumentException ex) {
                            f.setMatchMode(MatchMode.EQUALS);
                        }
                    } else {
                        f.setMatchMode(MatchMode.EQUALS);
                    }
                }
                default -> { }
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }
}
