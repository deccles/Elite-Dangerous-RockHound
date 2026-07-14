package org.dce.ed.exec;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;

import org.dce.ed.ui.OverlayScrollPaneSupport;

/**
 * Manage the shared Exec program catalog (name + path). Names must be unique on save.
 */
public final class ExecProgramsDialog extends JDialog {

    private static final int COL_NAME = 0;
    private static final int COL_PATH = 1;

    private final ProgramsTableModel tableModel;
    private final JTable table = new JTable();
    private boolean saved;
    private List<ExecProgram> resultPrograms = List.of();
    /** Old display name → new display name for bindings that must follow renames. */
    private Map<String, String> renameMap = Map.of();

    private ExecProgramsDialog(Window owner, List<ExecProgram> existing) {
        super(owner, "Manage programs", ModalityType.APPLICATION_MODAL);
        List<ExecProgram> copy = new ArrayList<>();
        if (existing != null) {
            for (ExecProgram program : existing) {
                if (program != null) {
                    copy.add(program.copy());
                }
            }
        }
        tableModel = new ProgramsTableModel(copy);
        table.setModel(tableModel);
        buildUi();
        pack();
        setMinimumSize(new Dimension(560, 280));
        setLocationRelativeTo(owner);
    }

    /**
     * @return result when the user saves; {@code null} if cancelled
     */
    public static Result show(Window owner, List<ExecProgram> existing) {
        ExecProgramsDialog dialog = new ExecProgramsDialog(owner, existing);
        dialog.setVisible(true);
        if (!dialog.saved) {
            return null;
        }
        return new Result(dialog.resultPrograms, dialog.renameMap);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(new JLabel("<html>Name programs used by Exec bindings. Names must be unique. "
                + "Choose a path to a <code>.exe</code> or <code>.jar</code>.</html>"), BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(22);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(COL_NAME).setPreferredWidth(140);
        table.getColumnModel().getColumn(COL_PATH).setPreferredWidth(360);
        JScrollPane tableScroll = new JScrollPane(table);
        OverlayScrollPaneSupport.installSubtleScrollBars(tableScroll);
        root.add(tableScroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton add = new JButton("Add");
        add.addActionListener(e -> {
            String suggested = ExecPrograms.nextRoboHoundName(tableModel.names());
            tableModel.addRow(new ExecProgram(suggested, ""));
            int last = tableModel.getRowCount() - 1;
            table.setRowSelectionInterval(last, last);
            table.editCellAt(last, COL_PATH);
        });
        JButton remove = new JButton("Remove");
        remove.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                tableModel.removeRow(row);
            }
        });
        JButton browse = new JButton("Browse path…");
        browse.addActionListener(e -> browsePathForSelectedRow());
        buttons.add(add);
        buttons.add(remove);
        buttons.add(browse);

        JPanel south = new JPanel(new BorderLayout());
        south.add(buttons, BorderLayout.WEST);
        JPanel okCancel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> {
            saved = false;
            dispose();
        });
        JButton ok = new JButton("OK");
        ok.addActionListener(e -> trySave());
        okCancel.add(cancel);
        okCancel.add(ok);
        south.add(okCancel, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(ok);
    }

    private void browsePathForSelectedRow() {
        int row = table.getSelectedRow();
        if (row < 0) {
            if (tableModel.getRowCount() == 0) {
                tableModel.addRow(new ExecProgram(ExecPrograms.nextRoboHoundName(tableModel.names()), ""));
                row = 0;
                table.setRowSelectionInterval(0, 0);
            } else {
                JOptionPane.showMessageDialog(this, "Select a program row first.", "Manage programs",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select program");
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("Windows executable (*.exe)", "exe"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("Java JAR (*.jar)", "jar"));
        chooser.setAcceptAllFileFilterUsed(true);
        String existing = tableModel.getPathAt(row);
        if (existing != null && !existing.isBlank()) {
            File file = new File(existing);
            if (file.getParentFile() != null && file.getParentFile().isDirectory()) {
                chooser.setCurrentDirectory(file.getParentFile());
            }
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (file == null) {
            return;
        }
        tableModel.setPathAt(row, file.getAbsolutePath());
        if (tableModel.getNameAt(row).isBlank()) {
            tableModel.setNameAt(row, ExecPrograms.nextRoboHoundName(tableModel.namesExcept(row)));
        }
    }

    private void trySave() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        List<ExecProgram> draft = tableModel.snapshot();
        String error = ExecPrograms.validateForSave(draft);
        if (error != null) {
            JOptionPane.showMessageDialog(this, error, "Manage programs", JOptionPane.WARNING_MESSAGE);
            return;
        }
        renameMap = tableModel.buildRenameMap();
        resultPrograms = draft;
        saved = true;
        dispose();
    }

    public static final class Result {
        private final List<ExecProgram> programs;
        private final Map<String, String> renameMap;

        Result(List<ExecProgram> programs, Map<String, String> renameMap) {
            this.programs = List.copyOf(programs);
            this.renameMap = Map.copyOf(renameMap != null ? renameMap : Map.of());
        }

        public List<ExecProgram> programs() {
            return programs;
        }

        public Map<String, String> renameMap() {
            return renameMap;
        }
    }

    private static final class ProgramsTableModel extends AbstractTableModel {

        private final List<ExecProgram> rows;
        private final List<String> originalNames;

        ProgramsTableModel(List<ExecProgram> rows) {
            this.rows = rows;
            this.originalNames = new ArrayList<>();
            for (ExecProgram row : rows) {
                originalNames.add(row.getName());
            }
        }

        List<ExecProgram> snapshot() {
            List<ExecProgram> copy = new ArrayList<>();
            for (ExecProgram row : rows) {
                copy.add(row.copy());
            }
            return copy;
        }

        List<String> names() {
            List<String> names = new ArrayList<>();
            for (ExecProgram row : rows) {
                names.add(row.getName());
            }
            return names;
        }

        List<String> namesExcept(int skipRow) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                if (i != skipRow) {
                    names.add(rows.get(i).getName());
                }
            }
            return names;
        }

        Map<String, String> buildRenameMap() {
            Map<String, String> map = new HashMap<>();
            int n = Math.min(originalNames.size(), rows.size());
            for (int i = 0; i < n; i++) {
                String from = originalNames.get(i);
                String to = rows.get(i).getName();
                if (from != null && !from.isBlank() && to != null && !to.isBlank()
                        && !from.equals(to)) {
                    map.put(from, to);
                }
            }
            return map;
        }

        void addRow(ExecProgram program) {
            rows.add(program);
            originalNames.add("");
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        void removeRow(int row) {
            if (row < 0 || row >= rows.size()) {
                return;
            }
            rows.remove(row);
            if (row < originalNames.size()) {
                originalNames.remove(row);
            }
            fireTableRowsDeleted(row, row);
        }

        String getNameAt(int row) {
            return rows.get(row).getName();
        }

        void setNameAt(int row, String name) {
            rows.get(row).setName(name);
            fireTableCellUpdated(row, COL_NAME);
        }

        String getPathAt(int row) {
            return rows.get(row).getPath();
        }

        void setPathAt(int row, String path) {
            rows.get(row).setPath(path);
            fireTableCellUpdated(row, COL_PATH);
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
        public String getColumnName(int column) {
            return column == COL_NAME ? "Name" : "Path";
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ExecProgram row = rows.get(rowIndex);
            return columnIndex == COL_NAME ? row.getName() : row.getPath();
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            ExecProgram row = rows.get(rowIndex);
            String text = value != null ? value.toString() : "";
            if (columnIndex == COL_NAME) {
                row.setName(text);
            } else {
                row.setPath(text);
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }
}
