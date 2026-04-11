package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.CommanderEvent;
import org.dce.ed.logreader.event.FileheaderEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.FsdTargetEvent;
import org.dce.ed.logreader.event.LoadGameEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.ReceiveTextEvent;
import org.dce.ed.logreader.event.SaasignalsFoundEvent;
import org.dce.ed.logreader.event.StartJumpEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.logreader.sim.JournalSimulator;
import org.dce.ed.logreader.sim.JournalSimulatorPreferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.dce.ed.ui.EdoUi;

/**
 * Tab that displays Elite Dangerous journal events in a table:
 *
 *   Column 1: Date/Time (local time)
 *   Column 2: Event type (journal "event" name)
 *   Column 3: Details (remaining attributes in human-readable form)
 *
 * Features:
 * - Sortable columns (click the table header)
 * - Date and Event columns sized “just enough”; Details fills the rest
 * - Right-click on a row -> "Exclude \"EventName\" events"
 * - Filter dialog with two lists (Excluded/Included) and buttons to move selected
 * - Excluded event names persisted via Preferences
 *
 * Navigation:
 * - Previous/Next day buttons using the set of dates that actually have journal files.
 * - Current date shown between the arrows.
 */
public class LogTabPanel extends JPanel {

    /** Journal viewer uses fixed black text; it does not follow overlay {@link org.dce.ed.ui.EdoUi.User} theme colors. */
    private static final Color JOURNAL_TEXT = Color.BLACK;

    private static final String PREF_KEY_EXCLUDED_EVENT_NAMES = "log.excludedEventNames";

    private static final ZoneId LOCAL_ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(LOCAL_ZONE);

    private final Preferences prefs;

    private EliteJournalReader journalReader;
    private boolean journalReaderAvailable = false;
    private String journalReaderErrorMessage = null;

    // Available dates that actually have journal files
    private List<LocalDate> availableDates = new ArrayList<>();
    private LocalDate currentDate;

    private final JLabel dateLabel;

    /** Journal.*.log files for {@link #currentDate}; the selected item drives Copy path. */
    private final JComboBox<Path> journalFileCombo;

    private JTextField searchField;
    private TableRowSorter<LogTableModel> rowSorter;

    private RowFilter<TableModel, Integer> baseRowFilter;   // whatever your existing "Filter..." button sets
    private RowFilter<LogTableModel, Integer> searchRowFilter; // regex search filter

 // --- Simulator ---
    private JournalSimulator simulator;

    private JButton simSetStartButton;
    private JButton simStepButton;
    private JButton simPlayButton;
    private JButton simPauseButton;

    private int simCurrentViewRow = -1;

    
    private ScheduledExecutorService simExecutor;
    private volatile boolean simRunning;

    /** A single row in the table: either an event or a message row (event == null). */
    private static class LogRow {
        final EliteLogEvent event; // may be null for info/error row
        final String detailsText;  // for event rows: human-readable details; for message rows: full message

        LogRow(EliteLogEvent event, String detailsText) {
            this.event = event;
            this.detailsText = detailsText;
        }
    }

    /** Table model wrapping a list of LogRow items. */
    private static class LogTableModel extends AbstractTableModel {

        private final List<LogRow> rows = new ArrayList<>();

        void setRows(List<LogRow> newRows) {
            rows.clear();
            if (newRows != null) {
                rows.addAll(newRows);
            }
            fireTableDataChanged();
        }

        LogRow getRow(int modelIndex) {
            if (modelIndex < 0 || modelIndex >= rows.size()) {
                return null;
            }
            return rows.get(modelIndex);
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
            switch (column) {
                case 0:
                    return "Date/Time";
                case 1:
                    return "Event";
                case 2:
                    return "Details";
                default:
                    return "";
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            LogRow row = rows.get(rowIndex);
            if (row.event == null) {
                // Info/error/no-events row: only show text in Details
                if (columnIndex == 2) {
                    return row.detailsText;
                }
                return "";
            }

            EliteLogEvent e = row.event;
            switch (columnIndex) {
                case 0:
                    return formatLocalTime(e.getTimestamp());
                case 1:
                    return extractEventName(e);
                case 2:
                    return row.detailsText;
                default:
                    return "";
            }
        }
        
        void addRow(LogRow row) {
            if (row == null) {
                return;
            }
            int idx = rows.size();
            rows.add(row);
            fireTableRowsInserted(idx, idx);
        }
    	public String getRawLineAt(int modelRow) {
    	    return extractRawJournalLine(rows.get(modelRow).event);
    	}
    }

    private final LogTableModel tableModel;
    private final JTable logTable;

    /** Event names (journal "event" field) that are currently excluded. */
    private Set<String> excludedEventNames;

    /** All event names seen in the last reload (for the filter dialog). */
    private Set<String> knownEventNames = new HashSet<>();

    public LogTabPanel() {
        super(new BorderLayout());
        this.prefs = Preferences.userNodeForPackage(LogTabPanel.class);
        this.excludedEventNames = loadExcludedEventNamesFromPreferences();

        // Try to initialize the journal reader, but don't die if it fails.
        try {
            this.journalReader = new EliteJournalReader(StandaloneLogViewer.clientKey);
            this.journalReaderAvailable = true;
        } catch (Exception ex) {
            this.journalReaderAvailable = false;
            this.journalReaderErrorMessage = "Log reader not available: " + ex.getMessage();
            System.err.println("[LogTabPanel] Failed to initialize EliteJournalReader: " + ex);
        }

        // Toolbar (Prev/Next day + current date + Reload + Filter)
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(new EmptyBorder(4, 4, 4, 4));

        JButton prevDayButton = new JButton("<<");
        JButton nextDayButton = new JButton(">>");
        dateLabel = new JLabel("-");
        dateLabel.setBorder(new EmptyBorder(0, 8, 0, 8));
        dateLabel.setForeground(JOURNAL_TEXT);

        journalFileCombo = new JComboBox<>();
        journalFileCombo.setEnabled(false);
        journalFileCombo.setToolTipText("Journal log file for the selected date.");
        journalFileCombo.setMaximumRowCount(16);
        Dimension comboPref = journalFileCombo.getPreferredSize();
        journalFileCombo.setPreferredSize(new Dimension(280, comboPref.height));
        journalFileCombo.setMaximumSize(new Dimension(400, comboPref.height));
        journalFileCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                Object label = (value instanceof Path p) ? p.getFileName().toString() : value;
                return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
            }
        });

        JButton reloadButton = new JButton("Reload");
        JButton copyJournalPathButton = new JButton("Copy path");
        copyJournalPathButton.setToolTipText(
                "Copy the selected journal file's path (text and file reference where supported).");
        JButton filterButton = new JButton("Filter...");

        toolBar.add(prevDayButton);
        toolBar.add(dateLabel);
        toolBar.add(nextDayButton);
        toolBar.add(Box.createHorizontalStrut(8));
        toolBar.add(journalFileCombo);
        toolBar.add(Box.createHorizontalStrut(16));
        
        
        toolBar.add(reloadButton);
        toolBar.add(Box.createHorizontalStrut(8));
        toolBar.add(copyJournalPathButton);
        toolBar.add(Box.createHorizontalStrut(8));
        toolBar.add(filterButton);
        toolBar.add(Box.createHorizontalStrut(8));

        searchField = new JTextField(24);
        searchField.setForeground(JOURNAL_TEXT);
        searchField.setCaretColor(JOURNAL_TEXT);
        searchField.setToolTipText("Regex search (press Enter). Empty = show all.");
        searchField.setMaximumSize(searchField.getPreferredSize()); // keeps toolbar height sane
        searchField.addActionListener(e -> applySearchFromField());
        toolBar.add(searchField);

        
        toolBar.add(Box.createHorizontalStrut(16));
        toolBar.add(new JSeparator(SwingConstants.VERTICAL));
        toolBar.add(Box.createHorizontalStrut(8));

        simSetStartButton = new JButton("Set Start");
        simStepButton = new JButton("Step");
        simPlayButton = new JButton("Play");
        simPauseButton = new JButton("Pause");

        simPauseButton.setEnabled(false);

        toolBar.add(simSetStartButton);
        toolBar.add(Box.createHorizontalStrut(4));
        toolBar.add(simStepButton);
        toolBar.add(Box.createHorizontalStrut(4));
        toolBar.add(simPlayButton);
        toolBar.add(Box.createHorizontalStrut(4));
        toolBar.add(simPauseButton);

        
        
        
        add(toolBar, BorderLayout.NORTH);


        // JTable-based log view
        tableModel = new LogTableModel();
        logTable = new JTable(tableModel);
//        logTable.setFont(createRoundedLogFont());
        logTable.setRowHeight(logTable.getRowHeight() + 4); // a bit more vertical space
        logTable.setFillsViewportHeight(true);

        // Sortable columns
        rowSorter = new TableRowSorter<>(tableModel);
        rowSorter.setComparator(0, Comparator.naturalOrder());
        rowSorter.setComparator(1, String.CASE_INSENSITIVE_ORDER);
        rowSorter.setComparator(2, String.CASE_INSENSITIVE_ORDER);
        logTable.setRowSorter(rowSorter);
        logTable.setForeground(JOURNAL_TEXT);
        logTable.setSelectionForeground(JOURNAL_TEXT);
        logTable.getTableHeader().setForeground(JOURNAL_TEXT);

        // Column widths: Date and Event narrow, Details fills the rest
        logTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        if (logTable.getColumnModel().getColumnCount() >= 3) {
            TableColumn dateCol = logTable.getColumnModel().getColumn(0);
            dateCol.setPreferredWidth(150);
            dateCol.setMinWidth(140);
            dateCol.setResizable(true);

            TableColumn eventCol = logTable.getColumnModel().getColumn(1);
            eventCol.setPreferredWidth(140);
            eventCol.setMinWidth(120);
            eventCol.setResizable(true);

            TableColumn detailsCol = logTable.getColumnModel().getColumn(2);
            detailsCol.setPreferredWidth(600);
            detailsCol.setMinWidth(200);
            detailsCol.setResizable(true);
        }

        // Right-click context menu for excluding this event name
        logTable.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    Point p = e.getPoint();
                    int viewRow = logTable.rowAtPoint(p);
                    if (viewRow < 0)
                        return;

                    logTable.setRowSelectionInterval(viewRow, viewRow);

                    int modelRow = logTable.convertRowIndexToModel(viewRow);
                    LogRow row = tableModel.getRow(modelRow);
                    if (row == null || row.event == null)
                        return;

                    showJsonPopup(row.event);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                handlePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopup(e);
            }

            private void handlePopup(MouseEvent e) {
                if (!e.isPopupTrigger())
                    return;

                Point p = e.getPoint();
                int viewRow = logTable.rowAtPoint(p);
                if (viewRow < 0)
                    return;

                logTable.setRowSelectionInterval(viewRow, viewRow);
                int modelRow = logTable.convertRowIndexToModel(viewRow);
                LogRow row = tableModel.getRow(modelRow);
                if (row == null || row.event == null)
                    return;

                String eventName = extractEventName(row.event);
                if (eventName == null || eventName.isEmpty())
                    return;

                // ... keep your existing popup-menu logic here unchanged ...
                // (whatever code you already had after this point)
            }
        });

        logTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table,
                    Object value,
                    boolean isSelected,
                    boolean hasFocus,
                    int row,
                    int column) {

                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);

                c.setForeground(isSelected ? table.getSelectionForeground() : JOURNAL_TEXT);

                if (!isSelected) {
                    if (row == simCurrentViewRow) {
                        c.setBackground(EdoUi.Internal.LOG_LIGHT_ORANGE); // simulator cursor
                    } else {
                        c.setBackground(Color.WHITE);
                    }
                }

                return c;
            }
        });

        
        simSetStartButton.addActionListener(e -> {
            buildSimulatorFromView();

            int viewRow = logTable.getSelectedRow();
            if (viewRow < 0)
                return;

            simulator.setCurrentIndex(viewRow);
            syncCursorToSimulator();
        });

simStepButton.addActionListener(e -> {
            try {
                if (simulator.emitNext()) {
                    syncCursorToSimulator();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                stopSimulation();
            }
        });

simPlayButton.addActionListener(e -> startSimulation());
        simPauseButton.addActionListener(e -> stopSimulation());

        
        JScrollPane scrollPane = new JScrollPane(logTable);
        scrollPane.setPreferredSize(new Dimension(400, 600));
        add(scrollPane, BorderLayout.CENTER);

        // Wire actions
        reloadButton.addActionListener(e -> reloadLogs());
        copyJournalPathButton.addActionListener(e -> copyJournalFileReferencesToClipboard());
        filterButton.addActionListener(e -> showFilterDialog());

        prevDayButton.addActionListener(e -> moveToRelativeDate(-1));
        nextDayButton.addActionListener(e -> moveToRelativeDate(+1));

        lockFirstTwoColumnsAndStretchLast(logTable, scrollPane);
        
        buildSimulatorFromView();
        
        // Initial date setup & load
        initAvailableDates();
        reloadLogs();
    }
    private void buildSimulatorFromView() {
        List<String> lines = new ArrayList<>();

        int viewRowCount = logTable.getRowCount();
        for (int viewRow = 0; viewRow < viewRowCount; viewRow++) {
            int modelRow = logTable.convertRowIndexToModel(viewRow);
            LogRow row = tableModel.getRow(modelRow);

            if (row == null || row.event == null)
                continue;

            lines.add(extractRawJournalLine(row.event));
        }

        simulator = new JournalSimulator(lines);

        try {
            Path outDir = Paths.get(JournalSimulatorPreferences.getSimulatorOutputDir());
            simulator.setOutputDirectory(outDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
        simCurrentViewRow = -1;
        logTable.repaint();
    }

    private void startSimulation() {
        if (simRunning)
            return;

        simRunning = true;

        simPlayButton.setEnabled(false);
        simPauseButton.setEnabled(true);
        simStepButton.setEnabled(false);

        double seconds = JournalSimulatorPreferences.getSimulatorIntervalSeconds();
        long delayMs = Math.max(1L, (long) (seconds * 1000.0));

        simExecutor = Executors.newSingleThreadScheduledExecutor();
        simExecutor.scheduleAtFixedRate(() -> {
            try {
                if (simulator.emitNext()) {
                    SwingUtilities.invokeLater(this::syncCursorToSimulator);
                } else {
                    SwingUtilities.invokeLater(this::stopSimulation);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(this::stopSimulation);
            }
        }, 0, delayMs, TimeUnit.MILLISECONDS);

}

    private void stopSimulation() {
        simRunning = false;

        if (simExecutor != null) {
            simExecutor.shutdownNow();
            simExecutor = null;
        }

        simPlayButton.setEnabled(true);
        simPauseButton.setEnabled(false);
        simStepButton.setEnabled(true);
    }

    
    private void showJsonPopup(EliteLogEvent event) {
        String pretty = toPrettyJson(event);

        JTextArea area = new JTextArea(pretty);
        area.setEditable(false);
        area.setForeground(JOURNAL_TEXT);
        area.setCaretColor(JOURNAL_TEXT);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setLineWrap(false);
        area.setWrapStyleWord(false);

        JScrollPane sp = new JScrollPane(area);
        sp.setPreferredSize(new Dimension(900, 600));

        JButton close = new JButton("Close");
        close.addActionListener(a -> {
            JDialog d = (JDialog) SwingUtilities.getWindowAncestor(close);
            d.dispose();
        });

        JPanel south = new JPanel(new BorderLayout());
        south.add(close, BorderLayout.EAST);

        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Event JSON", false);
        dlg.getContentPane().setLayout(new BorderLayout());
        dlg.getContentPane().add(sp, BorderLayout.CENTER);
        dlg.getContentPane().add(south, BorderLayout.SOUTH);

        // ESC closes
        dlg.getRootPane().registerKeyboardAction(
                e -> dlg.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        dlg.pack();

        // place near center of screen
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        dlg.setLocation(
                Math.max(0, (screen.width - dlg.getWidth()) / 2),
                Math.max(0, (screen.height - dlg.getHeight()) / 2));

        dlg.setVisible(true);

        // put caret at top
        area.setCaretPosition(0);
    }

    private String toPrettyJson(EliteLogEvent event) {
        String raw = tryExtractRawJson(event);
        if (raw == null || raw.isBlank()) {
            return "{\n  \"error\": \"No raw JSON available on event type: " + event.getClass().getName() + "\"\n}";
        }

        try {
            JsonElement el = JsonParser.parseString(raw);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            return gson.toJson(el);
        } catch (Exception ex) {
            // raw wasn't valid JSON; show it as a string payload so the popup is still useful
            return "{\n  \"raw\": " + new GsonBuilder().setPrettyPrinting().create().toJson(raw) + "\n}";
        }
    }

    /**
     * Uses reflection so this works no matter what your EliteLogEvent implementation calls it.
     * Add/remove method names here if your model differs.
     */
    private String tryExtractRawJson(EliteLogEvent event) {
        // Common getter names to try
        String[] candidates = {
                "getRawJson",
                "getRawLine",
                "getRawRecord",
                "getJson",
                "getJsonText",
                "getJsonString",
                "toJson",
                "toJsonString"
        };

        for (String name : candidates) {
            try {
                Method m = event.getClass().getMethod(name);
                Object v = m.invoke(event);
                if (v instanceof String) {
                    return (String) v;
                }
                if (v != null) {
                    return v.toString();
                }
            } catch (Exception ignored) {
                // try next
            }
        }

        // Last-ditch: sometimes toString() is the raw json
        try {
            String s = event.toString();
            if (s != null && s.trim().startsWith("{")) {
                return s;
            }
        } catch (Exception ignored) {
        }

        return null;
    }
    
    /* ---------- Dates & timestamps helpers ---------- */

    private static String formatLocalTime(Instant instant) {
        if (instant == null) {
            return "";
        }
        return TS_FORMAT.format(instant);
    }

    /**
     * Try to get the raw journal "event" field; fall back to enum type.
     */
    private static String extractEventName(EliteLogEvent event) {
        if (event == null) {
            return "";
        }
        JsonObject raw = event.getRawJson();
        if (raw != null && raw.has("event") && !raw.get("event").isJsonNull()) {
            try {
                return raw.get("event").getAsString();
            } catch (Exception ignored) {
                // fall through
            }
        }
        return event.getType() != null ? event.getType().name() : "";
    }
    
    private void initAvailableDates() {
        if (!journalReaderAvailable) {
            availableDates = new ArrayList<>();
            currentDate = null;
            dateLabel.setText("-");
            return;
        }
        try {
            availableDates = journalReader.listAvailableDates();
            if (availableDates.isEmpty()) {
                currentDate = null;
                dateLabel.setText("-");
            } else {
                // Default: most recent date with logs
                currentDate = availableDates.get(availableDates.size() - 1);
                dateLabel.setText(currentDate.toString());
            }
        } catch (Exception ex) {
            System.err.println("[LogTabPanel] Failed to list available dates: " + ex.getMessage());
            ex.printStackTrace();
            
            availableDates = new ArrayList<>();
            currentDate = null;
            dateLabel.setText("-");
        }
    }
    
    private static void lockFirstTwoColumnsAndStretchLast(JTable table, JScrollPane scrollPane) {
        // Never let JTable redistribute widths on its own.
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        JViewport viewport = scrollPane.getViewport();

        Runnable resizeLast = () -> {
            if (table.getColumnModel().getColumnCount() < 3)
                return;

            int viewportWidth = viewport.getWidth();
            if (viewportWidth <= 0)
                return;

            TableColumn c0 = table.getColumnModel().getColumn(0);
            TableColumn c1 = table.getColumnModel().getColumn(1);
            TableColumn c2 = table.getColumnModel().getColumn(2);

            int fixed = c0.getWidth() + c1.getWidth();
            int target = viewportWidth - fixed;

            int min = Math.max(200, c2.getMinWidth());
            if (target < min)
                target = min;

            // Update only the last column. Keep 0/1 exactly as-is.
            c2.setPreferredWidth(target);
            c2.setWidth(target);

            table.revalidate();
            table.repaint();
        };

        viewport.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(resizeLast);
            }
        });

        // Also run once after initial layout/pack.
        SwingUtilities.invokeLater(resizeLast);
    }


    private void moveToRelativeDate(int offset) {
        if (availableDates == null || availableDates.isEmpty() || currentDate == null) {
            return;
        }
        int idx = availableDates.indexOf(currentDate);
        if (idx < 0) {
            return;
        }
        int newIdx = idx + offset;
        if (newIdx < 0 || newIdx >= availableDates.size()) {
            return; // no earlier/later date
        }
        currentDate = availableDates.get(newIdx);
        dateLabel.setText(currentDate.toString());
        reloadLogs();
    }

    /* ---------- Preferences for excluded events ---------- */

    private Set<String> loadExcludedEventNamesFromPreferences() {
        String raw = prefs.get(PREF_KEY_EXCLUDED_EVENT_NAMES, "");
        Set<String> set = new HashSet<>();
        if (raw == null || raw.isEmpty()) {
            return set;
        }

        String[] tokens = raw.split(",");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return set;
    }

    private void saveExcludedEventNamesToPreferences() {
        String value = excludedEventNames.stream()
                .sorted()
                .collect(Collectors.joining(","));
        prefs.put(PREF_KEY_EXCLUDED_EVENT_NAMES, value);
    }

    /* ---------- Reload & formatting of rows ---------- */

    private void copyJournalFileReferencesToClipboard() {
        final String title = "Copy path";
        Path selected = getSelectedJournalFilePath();
        if (selected == null) {
            JOptionPane.showMessageDialog(this,
                    "Choose a journal file in the drop-down (next to the date).",
                    title,
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new JournalPathTransferable(selected), null);
    }

    private Path getSelectedJournalFilePath() {
        if (!journalFileCombo.isEnabled()) {
            return null;
        }
        Object item = journalFileCombo.getSelectedItem();
        return (item instanceof Path p) ? p : null;
    }

    private void clearJournalFileCombo() {
        journalFileCombo.removeAllItems();
        journalFileCombo.setEnabled(false);
    }

    private void repopulateJournalFileCombo(EliteJournalReader reader) throws IOException {
        journalFileCombo.removeAllItems();
        if (currentDate == null) {
            journalFileCombo.setEnabled(false);
            return;
        }
        List<Path> paths = reader.listJournalPathsForDate(currentDate);
        if (paths.isEmpty()) {
            journalFileCombo.setEnabled(false);
            return;
        }
        for (Path p : paths) {
            journalFileCombo.addItem(p);
        }
        journalFileCombo.setSelectedIndex(paths.size() - 1);
        journalFileCombo.setEnabled(true);
    }

    private void reloadLogs() {
        knownEventNames.clear();

        // Recreate the journal reader so Logging preferences (auto vs custom folder)
        // take effect each time we reload.
        try {
            this.journalReader = new EliteJournalReader(StandaloneLogViewer.clientKey);
            this.journalReaderAvailable = true;
            this.journalReaderErrorMessage = null;
        } catch (Exception ex) {
            this.journalReaderAvailable = false;
            this.journalReaderErrorMessage = "Log reader not available: " + ex.getMessage();
            System.err.println("[LogTabPanel] Failed to initialize EliteJournalReader: " + ex);
        }

        if (!journalReaderAvailable) {
            String msg = (journalReaderErrorMessage != null)
                    ? journalReaderErrorMessage
                    : "Elite Dangerous logs not found.";
            List<LogRow> rows = new ArrayList<>();
            rows.add(new LogRow(null, msg));
            tableModel.setRows(rows);
            dateLabel.setText("-");
            clearJournalFileCombo();
            return;
        }

        if (currentDate == null) {
            // Try to re-init dates (e.g., first run or after an error)
            initAvailableDates();
            if (currentDate == null) {
                List<LogRow> rows = new ArrayList<>();
                rows.add(new LogRow(null, "No Elite Dangerous journal files found."));
                tableModel.setRows(rows);
                clearJournalFileCombo();
                return;
            }
        }

        dateLabel.setText(currentDate.toString());

        List<EliteLogEvent> events;
        try {
            events = journalReader.readEventsForDate(currentDate);
        } catch (Exception ex) {
            String msg = "Error reading Elite Dangerous logs: " + ex.getMessage();
            System.err.println("[LogTabPanel] " + msg);
            List<LogRow> rows = new ArrayList<>();
            rows.add(new LogRow(null, msg));
            tableModel.setRows(rows);
            try {
                repopulateJournalFileCombo(journalReader);
            } catch (IOException ioe) {
                clearJournalFileCombo();
            }
            return;
        }

        List<LogRow> visibleRows = new ArrayList<>();

        for (EliteLogEvent event : events) {
            String eventName = extractEventName(event);
            if (eventName != null && !eventName.isEmpty()) {
                knownEventNames.add(eventName);
            }

            if (eventName != null && excludedEventNames.contains(eventName)) {
                // filtered out
                continue;
            }

            String details = formatDetails(event);
            visibleRows.add(new LogRow(event, details));
        }

        if (visibleRows.isEmpty()) {
            visibleRows.add(new LogRow(null, "No events found for " + currentDate.toString() + "."));
        }

        tableModel.setRows(visibleRows);

        try {
            repopulateJournalFileCombo(journalReader);
        } catch (IOException ex) {
            clearJournalFileCombo();
        }
        
        SwingUtilities.invokeLater(() -> {
            int last = logTable.getRowCount() - 1;
            if (last >= 0) {
                logTable.scrollRectToVisible(logTable.getCellRect(last, 0, true));
            }
        });
        buildSimulatorFromView();
        
    }

    /**
     * Format the "details" column for a given event.
     * Known event types get structured text; others get key=value pairs
     * of remaining attributes (minus timestamp/event).
     */
    private static String formatDetails(EliteLogEvent e) {
        if (e == null) {
            return "";
        }

        EliteEventType type = e.getType();
        switch (type) {
            case FILEHEADER: {
                FileheaderEvent fe = (FileheaderEvent) e;
                return "part=" + fe.getPart()
                        + ", odyssey=" + fe.isOdyssey()
                        + ", gameVersion=" + safe(fe.getGameVersion())
                        + ", build=" + safe(fe.getBuild());
            }
            case COMMANDER: {
                CommanderEvent ce = (CommanderEvent) e;
                return "name=" + safe(ce.getName())
                        + ", fid=" + safe(ce.getFid());
            }
            case LOAD_GAME: {
                LoadGameEvent lg = (LoadGameEvent) e;
                return "commander=" + safe(lg.getCommander())
                        + ", ship=" + safe(lg.getShip())
                        + ", shipName=" + safe(lg.getShipName())
                        + ", fuel=" + lg.getFuelLevel() + "/" + lg.getFuelCapacity()
                        + ", mode=" + safe(lg.getGameMode())
                        + ", credits=" + lg.getCredits();
            }
            case LOCATION: {
                LocationEvent le = (LocationEvent) e;
                return "system=" + safe(le.getStarSystem())
                        + ", body=" + safe(le.getBody())
                        + ", bodyType=" + safe(le.getBodyType())
                        + ", docked=" + le.isDocked()
                        + ", taxi=" + le.isTaxi()
                        + ", multicrew=" + le.isMulticrew();
            }
            case START_JUMP: {
                StartJumpEvent sj = (StartJumpEvent) e;
                return "type=" + safe(sj.getJumpType())
                        + ", system=" + safe(sj.getStarSystem())
                        + ", starClass=" + safe(sj.getStarClass())
                        + ", taxi=" + sj.isTaxi();
            }
            case FSD_JUMP: {
                FsdJumpEvent fj = (FsdJumpEvent) e;
                return "system=" + safe(fj.getStarSystem())
                        + ", body=" + safe(fj.getBody())
                        + ", bodyType=" + safe(fj.getBodyType())
                        + ", jumpDist=" + fj.getJumpDist()
                        + ", fuelUsed=" + fj.getFuelUsed()
                        + ", fuelLevel=" + fj.getFuelLevel();
            }
            case FSD_TARGET: {
                FsdTargetEvent ft = (FsdTargetEvent) e;
                return "name=" + safe(ft.getName())
                        + ", starClass=" + safe(ft.getStarClass())
                        + ", remainingJumps=" + ft.getRemainingJumpsInRoute();
            }
            case SAASIGNALS_FOUND: {
                SaasignalsFoundEvent sa = (SaasignalsFoundEvent) e;
                StringBuilder sb = new StringBuilder();
                sb.append("body=").append(safe(sa.getBodyName()));

                if (sa.getSignals() != null && !sa.getSignals().isEmpty()) {
                    sb.append(", signals=");
                    boolean first = true;
                    for (SaasignalsFoundEvent.Signal s : sa.getSignals()) {
                        if (!first) {
                            sb.append("; ");
                        }
                        first = false;
                        sb.append(s.getType()).append("(").append(s.getCount()).append(")");
                    }
                }

                if (sa.getGenuses() != null && !sa.getGenuses().isEmpty()) {
                    sb.append(", genuses=");
                    boolean first = true;
                    for (SaasignalsFoundEvent.Genus g : sa.getGenuses()) {
                        if (!first) {
                            sb.append("; ");
                        }
                        first = false;
                        String name = g.getGenusLocalised() != null
                                ? g.getGenusLocalised()
                                : g.getGenus();
                        sb.append(name);
                    }
                }

                return sb.toString();
            }
            case STATUS: {
                StatusEvent st = (StatusEvent) e;
                StringBuilder sb = new StringBuilder();
                sb.append("flags=").append(st.getFlags())
                        .append(", flags2=").append(st.getFlags2())
                        .append(", guiFocus=").append(st.getGuiFocus())
                        .append(", fuelMain=").append(st.getFuelMain())
                        .append(", fuelRes=").append(st.getFuelReservoir())
                        .append(", cargo=").append(st.getCargo())
                        .append(", legal=").append(safe(st.getLegalState()))
                        .append(", balance=").append(st.getBalance());
                int[] pips = st.getPips();
                if (pips != null && pips.length == 3) {
                    sb.append(", pips=[")
                            .append(pips[0]).append(',')
                            .append(pips[1]).append(',')
                            .append(pips[2]).append(']');
                }
                return sb.toString();
            }
            case RECEIVE_TEXT: {
                ReceiveTextEvent rt = (ReceiveTextEvent) e;
                String msg = rt.getMessageLocalised() != null
                        ? rt.getMessageLocalised()
                        : rt.getMessage();
                return "from=" + safe(rt.getFrom())
                        + ", channel=" + safe(rt.getChannel())
                        + ", msg=" + safe(msg);
            }
            case NAV_ROUTE:
                return "Nav route updated";
            case NAV_ROUTE_CLEAR:
                return "Nav route cleared";
            default:
                // Generic/unknown: pretty-print remaining attributes as key=value pairs
                return formatGenericAttributes(e);
        }
    }

    /**
     * For generic/unknown events: return a "key=value, key2=value2" string
     * built from raw JSON minus "timestamp" and "event".
     */
    private static String formatGenericAttributes(EliteLogEvent event) {
        JsonObject raw = event.getRawJson();
        if (raw == null || raw.entrySet().isEmpty()) {
            return "";
        }

        JsonObject copy = raw.deepCopy();
        copy.remove("timestamp");
        copy.remove("event");
        if (copy.entrySet().isEmpty()) {
            return "";
        }

        List<String> parts = new ArrayList<>();
        for (String key : copy.keySet()) {
            JsonElement el = copy.get(key);
            if (el == null || el.isJsonNull()) {
                continue;
            }
            parts.add(key + "=" + el.toString());
        }
        return String.join(", ", parts);
    }

    private static String safe(String s) {
        return s == null ? "<null>" : s;
    }

    /* ---------- Filter dialog ---------- */

    private void showFilterDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);

        List<String> sortedNames = new ArrayList<>(knownEventNames);
        Collections.sort(sortedNames, String.CASE_INSENSITIVE_ORDER);

        LogFilterDialog dialog = new LogFilterDialog(owner, sortedNames, excludedEventNames);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        if (dialog.isOkPressed()) {
            this.excludedEventNames = dialog.getExcludedEventNames();
            saveExcludedEventNamesToPreferences();
            reloadLogs();
        }
    }

    /**
     * Modal dialog to choose which journal "event" names are excluded vs included.
     *
     * Layout:
     *
     *   Excluded:
     *   [excluded list]
     *   [Include Selected]
     *
     *   Included:
     *   [included list]
     *   [Exclude Selected]
     */
    private static class LogFilterDialog extends JDialog {

        private boolean okPressed = false;
        private final List<String> eventNames;

        private final DefaultListModel<String> excludedModel = new DefaultListModel<>();
        private final DefaultListModel<String> includedModel = new DefaultListModel<>();

        private final JList<String> excludedList = new JList<>(excludedModel);
        private final JList<String> includedList = new JList<>(includedModel);

        private Set<String> excludedEventNames;

        LogFilterDialog(Window owner, List<String> eventNames, Set<String> initiallyExcluded) {
            super(owner, "Log Filter", ModalityType.APPLICATION_MODAL);
            this.eventNames = eventNames;

            setLayout(new BorderLayout());
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);

            initModels(initiallyExcluded);
            add(buildContentPanel(), BorderLayout.CENTER);
            add(buildButtonPanel(), BorderLayout.SOUTH);

            pack();
        }

        private void initModels(Set<String> initiallyExcluded) {
            // Fill excluded / included models from eventNames + initiallyExcluded
            for (String name : eventNames) {
                if (initiallyExcluded.contains(name)) {
                    excludedModel.addElement(name);
                } else {
                    includedModel.addElement(name);
                }
            }
        }

        private JPanel buildContentPanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(new EmptyBorder(8, 8, 8, 8));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.insets = new java.awt.Insets(4, 4, 4, 4);

            // Row 0: Excluded label
            gbc.gridy = 0;
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.WEST;
            JLabel excludedLabel = new JLabel("Excluded:");
            excludedLabel.setForeground(JOURNAL_TEXT);
            panel.add(excludedLabel, gbc);

            // Row 1: Excluded list
            gbc.gridy = 1;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.anchor = GridBagConstraints.CENTER;
            excludedList.setForeground(JOURNAL_TEXT);
            JScrollPane excludedScroll = new JScrollPane(excludedList);
            excludedScroll.setPreferredSize(new Dimension(260, 120));
            panel.add(excludedScroll, gbc);

            // Row 2: "Include Selected" button
            gbc.gridy = 2;
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.EAST;
            JPanel includeSelectedPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            JButton includeSelectedButton = new JButton("Include Selected");
            includeSelectedButton.addActionListener(e ->
                    moveSelected(excludedList, excludedModel, includedModel));
            includeSelectedPanel.add(includeSelectedButton);
            panel.add(includeSelectedPanel, gbc);

            // Row 3: Included label
            gbc.gridy = 3;
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.WEST;
            JLabel includedLabel = new JLabel("Included:");
            includedLabel.setForeground(JOURNAL_TEXT);
            panel.add(includedLabel, gbc);

            // Row 4: Included list
            gbc.gridy = 4;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.anchor = GridBagConstraints.CENTER;
            includedList.setForeground(JOURNAL_TEXT);
            JScrollPane includedScroll = new JScrollPane(includedList);
            includedScroll.setPreferredSize(new Dimension(260, 140));
            panel.add(includedScroll, gbc);

            // Row 5: "Exclude Selected" button
            gbc.gridy = 5;
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.EAST;
            JPanel excludeSelectedPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            JButton excludeSelectedButton = new JButton("Exclude Selected");
            excludeSelectedButton.addActionListener(e ->
                    moveSelected(includedList, includedModel, excludedModel));
            excludeSelectedPanel.add(excludeSelectedButton);
            panel.add(excludeSelectedPanel, gbc);

            return panel;
        }

        private void moveSelected(JList<String> fromList,
                                  DefaultListModel<String> fromModel,
                                  DefaultListModel<String> toModel) {
            List<String> selected = fromList.getSelectedValuesList();
            if (selected.isEmpty()) {
                return;
            }
            for (String s : selected) {
                if (!toModel.contains(s)) {
                    toModel.addElement(s);
                }
                fromModel.removeElement(s);
            }
        }

        private JPanel buildButtonPanel() {
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

            JButton includeAllButton = new JButton("Include All");
            JButton excludeAllButton = new JButton("Exclude All");
            JButton okButton = new JButton("OK");
            JButton cancelButton = new JButton("Cancel");

            includeAllButton.addActionListener(e -> {
                // move everything into Included
                copyModel(excludedModel, includedModel, true);
            });

            excludeAllButton.addActionListener(e -> {
                // move everything into Excluded
                copyModel(includedModel, excludedModel, true);
            });

            okButton.addActionListener(e -> {
                okPressed = true;
                computeExcludedEventNamesFromUI();
                dispose();
            });

            cancelButton.addActionListener(e -> dispose());

            buttonPanel.add(includeAllButton);
            buttonPanel.add(excludeAllButton);
            buttonPanel.add(cancelButton);
            buttonPanel.add(okButton);

            return buttonPanel;
        }

        private void copyModel(DefaultListModel<String> from,
                               DefaultListModel<String> to,
                               boolean clearFrom) {
            List<String> all = new ArrayList<>();
            for (int i = 0; i < from.size(); i++) {
                all.add(from.getElementAt(i));
            }
            for (String s : all) {
                if (!to.contains(s)) {
                    to.addElement(s);
                }
            }
            if (clearFrom) {
                from.clear();
            }
        }

        private void computeExcludedEventNamesFromUI() {
            Set<String> excluded = new HashSet<>();
            for (int i = 0; i < excludedModel.size(); i++) {
                excluded.add(excludedModel.getElementAt(i));
            }
            this.excludedEventNames = excluded;
        }

        boolean isOkPressed() {
            return okPressed;
        }

        Set<String> getExcludedEventNames() {
            return excludedEventNames == null ? new HashSet<>() : excludedEventNames;
        }
    }
//    private static Font createLogFont() {
//        // Prefer Segoe UI (Windows), fall back to other common sans-serifs
//        String[] preferred = {
//            "Segoe UI",
//            "Calibri",
//            "Tahoma",
//            "Arial",
//            "SansSerif"
//        };
//
//        for (String name : preferred) {
//            Font f = new Font(name, Font.PLAIN, 13);
//            if (f.getFamily().equals(name)) {
//                return f;
//            }
//        }
//
//        // Last resort
//        return new Font("SansSerif", Font.PLAIN, 13);
//    }

    private static Font createRoundedLogFont() {
        // Prefer a very rounded font first
        String[] preferred = {
            "Consolas",  // nice, chonky, rounded
            "Calibri",
            "Verdana",
            "Tahoma",
            "SansSerif"
        };

        for (String name : preferred) {
            Font f = new Font(name, Font.PLAIN, 13);
            // getFamily() returns a real family name if it's actually available
            if (f.getFamily().equals(name)) {
                return f;
            }
        }

        return new Font("SansSerif", Font.PLAIN, 13);
    }

	public void handleLogEvent(EliteLogEvent event) {
		if (event == null) {
	        return;
	    }

	    // Only show events for the currently selected date
	    if (currentDate != null) {
	        LocalDate eventDate = event.getTimestamp()
	                .atZone(LOCAL_ZONE)
	                .toLocalDate();
	        if (!eventDate.equals(currentDate)) {
	            // Different day – ignore for this tab's view
	            return;
	        }
	    }

	    // Respect excluded-event filters
	    String eventName = extractEventName(event);
	    if (eventName != null && !eventName.isEmpty()) {
	        knownEventNames.add(eventName);
	    }
	    if (eventName != null && excludedEventNames.contains(eventName)) {
	        return;
	    }

	    String details = formatDetails(event);
	    LogRow row = new LogRow(event, details);
	    tableModel.addRow(row);

	    // Always jump to bottom when a new live entry shows up
	    SwingUtilities.invokeLater(() -> {
	        int last = logTable.getRowCount() - 1;
	        if (last >= 0) {
	            logTable.scrollRectToVisible(logTable.getCellRect(last, 0, true));
	        }
	    });	
	}
	private void applySearchFromField() {
	    if (rowSorter == null || searchField == null)
	        return;

	    String text = searchField.getText();
	    if (text == null)
	        text = "";

	    text = text.trim();

	    if (text.isEmpty()) {
            searchRowFilter = null;
            rowSorter.setRowFilter(null);
            buildSimulatorFromView();
            return;
        }

	    final Pattern p;
	    try {
	        p = Pattern.compile(text, Pattern.CASE_INSENSITIVE);
	    } catch (Exception ex) {
	        Toolkit.getDefaultToolkit().beep();
	        JOptionPane.showMessageDialog(
	                this,
	                "Invalid regex:\n" + ex.getMessage(),
	                "Search",
	                JOptionPane.ERROR_MESSAGE);
	        return;
	    }

	    searchRowFilter = new RowFilter<LogTableModel, Integer>() {
	        @Override
	        public boolean include(Entry<? extends LogTableModel, ? extends Integer> entry) {
	            // Match any column text
	            for (int c = 0; c < entry.getValueCount(); c++) {
	                String s = entry.getStringValue(c);
	                if (s == null)
	                    continue;

	                if (p.matcher(s).find())
	                    return true;
	            }
	            return false;
	        }
	    };

	    rowSorter.setRowFilter(searchRowFilter);
        buildSimulatorFromView();
    }
	
	private static final Gson GSON = new Gson();
	private static String extractRawJournalLine(EliteLogEvent event) {
		return GSON.toJson(event.getRawJson());
	}
	private void syncCursorToSimulator() {
        if (simulator == null)
            return;

        int idx = simulator.getCurrentIndex();
        if (idx < 0 || idx >= logTable.getRowCount()) {
            simCurrentViewRow = -1;
            logTable.repaint();
            return;
        }

        setSimulatorCursor(idx);
    }

    private void setSimulatorCursor(int viewRow) {
	    simCurrentViewRow = viewRow;
	    repaintSimulatorCursor();
	}

	private void advanceSimulatorCursor() {
	    if (simCurrentViewRow < 0)
	        return;

	    simCurrentViewRow++;

	    if (simCurrentViewRow >= logTable.getRowCount()) {
	        stopSimulation();
	        return;
	    }

	    repaintSimulatorCursor();
	}

	private void repaintSimulatorCursor() {
	    logTable.repaint();

	    if (simCurrentViewRow >= 0 && simCurrentViewRow < logTable.getRowCount()) {
	        Rectangle r = logTable.getCellRect(simCurrentViewRow, 0, true);
	        logTable.scrollRectToVisible(r);
	    }
	}

    /**
     * Clipboard payload for one journal file: absolute path as text and, when possible,
     * {@link DataFlavor#javaFileListFlavor} (e.g. paste into File Explorer).
     */
    private static final class JournalPathTransferable implements Transferable {

        private final List<File> files;
        private final String pathLine;

        JournalPathTransferable(Path path) {
            Path abs = path.toAbsolutePath().normalize();
            this.pathLine = abs.toString();
            File f = abs.toFile();
            this.files = f.isFile() ? List.of(f) : List.of();
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            if (files.isEmpty()) {
                return new DataFlavor[] { DataFlavor.stringFlavor };
            }
            return new DataFlavor[] { DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            if (DataFlavor.stringFlavor.equals(flavor)) {
                return true;
            }
            return !files.isEmpty() && DataFlavor.javaFileListFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (DataFlavor.stringFlavor.equals(flavor)) {
                return pathLine;
            }
            if (DataFlavor.javaFileListFlavor.equals(flavor)) {
                if (files.isEmpty()) {
                    throw new UnsupportedFlavorException(flavor);
                }
                return files;
            }
            throw new UnsupportedFlavorException(flavor);
        }
    }

}