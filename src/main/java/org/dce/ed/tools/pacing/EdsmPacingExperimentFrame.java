package org.dce.ed.tools.pacing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.dce.ed.route.RouteEdsmPrefetchPolicy;
import org.dce.ed.util.AppIconUtil;
import org.dce.ed.util.EdsmRequestPolicy;

/**
 * Standalone EDSM pacing tool: live {@code showBodies} only.
 * Never reads or writes EDO's daily query cache or request gate.
 * <p>
 * Launch without the overlay:
 * {@code script/edsm-pacing-experiment.bat} or
 * {@code mvn -q -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=org.dce.ed.tools.pacing.EdsmPacingExperimentFrame}
 */
public final class EdsmPacingExperimentFrame extends JFrame {
    private static EdsmPacingExperimentFrame instance;

    private static final Color LOG_SUCCESS = new Color(0, 140, 0);
    private static final Color LOG_FAILURE = new Color(196, 32, 32);
    private static final DateTimeFormatter LOG_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final JTextArea systemsArea = new JTextArea();
    private final JScrollPane systemsScroll = new JScrollPane(systemsArea);
    private final JPanel batchList = new JPanel();
    private final JTextPane logArea = new JTextPane();
    private final JLabel elapsedLabel = new JLabel("Since last run: —");
    private final JLabel statusLabel = new JLabel("Idle.");
    private final JButton runButton = new JButton("Run");
    private final JButton stopButton = new JButton("Stop");
    private final JButton openLogButton = new JButton("Open log");
    private final EdsmPacingExperimentLog sessionLog = new EdsmPacingExperimentLog();
    private final DefaultTableModel errorModel = new DefaultTableModel(new Object[] { "Code", "Description" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final Set<String> seenErrorCodes = new LinkedHashSet<>();
    private final List<BatchRow> rows = new ArrayList<>();
    private final Timer elapsedTimer = new Timer(250, e -> refreshElapsedLabel());
    private volatile Thread worker;
    private boolean persistSuppressed;
    private boolean runInProgress;
    private long runStartedAtMillis;
    private long lastRunEndedAtMillis;

    private EdsmPacingExperimentFrame() {
        super("EDSM pacing experiment");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        AppIconUtil.applyAppIcon(this, AppIconUtil.APP_ICON_RESOURCE);
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);
        restoreSavedState();
        elapsedTimer.start();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                persistSettings();
                elapsedTimer.stop();
            }
        });

        setPreferredSize(new Dimension(1080, 720));
        pack();
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            EdsmPacingExperimentFrame frame = new EdsmPacingExperimentFrame();
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });
    }

    public static void showOrBringToFront(Component parent) {
        SwingUtilities.invokeLater(() -> {
            if (instance == null || !instance.isDisplayable()) {
                instance = new EdsmPacingExperimentFrame();
                instance.setLocationRelativeTo(parent);
            }
            instance.setVisible(true);
            instance.toFront();
            instance.requestFocus();
        });
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 4));
        JLabel blurb = new JLabel("<html>Live EDSM <code>showBodies</code> only — no EDO cache read or write. "
                + "Each request is cache-busted. Batches consume the list in order. "
                + "Shares your public IP rate limit with the Route tab.<br>"
                + "Each Run is appended to <code>" + sessionLog.file() + "</code></html>");
        header.add(blurb, BorderLayout.CENTER);

        elapsedLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        elapsedLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        JPanel presets = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        presets.add(new JLabel("Preset:"));
        JComboBox<String> preset = new JComboBox<>(new DefaultComboBoxModel<>(new String[] {
                "Current route policy (18 then 6 / 6 / 6s)",
                "Previous prefetch (8 / 2 / 10s)",
                "Old opening burst (25 / 18 / 0s)",
                "Conservative (2 / 1 / 12s)"
        }));
        JButton apply = new JButton("Apply preset");
        apply.addActionListener(e -> {
            applyPreset(preset.getSelectedIndex());
            fillSystemsToDemand();
            persistSettings();
        });
        presets.add(preset);
        presets.add(apply);

        JPanel east = new JPanel(new BorderLayout(0, 4));
        east.add(elapsedLabel, BorderLayout.NORTH);
        east.add(presets, BorderLayout.SOUTH);
        header.add(east, BorderLayout.EAST);
        return header;
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel(new BorderLayout(8, 8));

        systemsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        systemsArea.setText(EdsmPacingSampleSystems.asEditableText());
        systemsScroll.setPreferredSize(new Dimension(280, 200));

        JButton fillList = new JButton("Fill to batches");
        fillList.addActionListener(e -> {
            fillSystemsToDemand();
            persistSettings();
        });
        systemsArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                persistSettings();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                persistSettings();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                persistSettings();
            }
        });
        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.add(systemsScroll, BorderLayout.CENTER);
        left.add(fillList, BorderLayout.SOUTH);

        batchList.setLayout(new BoxLayout(batchList, BoxLayout.Y_AXIS));
        JScrollPane batchScroll = new JScrollPane(batchList);
        batchScroll.setBorder(BorderFactory.createTitledBorder("Batches"));
        batchScroll.setPreferredSize(new Dimension(600, 180));
        batchScroll.setMinimumSize(new Dimension(200, 80));

        JButton addBatch = new JButton("Add batch");
        addBatch.addActionListener(e -> {
            addBatchRow(RouteEdsmPrefetchPolicy.HEALTHY_WAVE_SIZE, RouteEdsmPrefetchPolicy.MAX_CONCURRENT,
                    (int) RouteEdsmPrefetchPolicy.HEALTHY_WAVE_REST.toSeconds(), 0);
            persistSettings();
        });
        JPanel batchHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        batchHeader.add(addBatch);
        batchHeader.add(new JLabel("Count = systems in the wave. Concurrent = in-flight. "
                + "Rest = pause after the wave. Delay = stagger between launches. "
                + "Repeat = run this wave that many times."));

        JPanel batchPane = new JPanel(new BorderLayout(0, 4));
        batchPane.add(batchHeader, BorderLayout.NORTH);
        batchPane.add(batchScroll, BorderLayout.CENTER);
        batchPane.setMinimumSize(new Dimension(200, 100));

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setText("");
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log"));
        logScroll.setPreferredSize(new Dimension(600, 200));

        JTable errorTable = new JTable(errorModel);
        errorTable.setRowHeight(20);
        errorTable.getColumnModel().getColumn(0).setPreferredWidth(70);
        errorTable.getColumnModel().getColumn(1).setPreferredWidth(420);
        JScrollPane errorScroll = new JScrollPane(errorTable);
        errorScroll.setBorder(BorderFactory.createTitledBorder("Received error codes"));
        errorScroll.setPreferredSize(new Dimension(600, 110));

        JPanel logAndErrors = new JPanel(new BorderLayout(0, 8));
        logAndErrors.add(logScroll, BorderLayout.CENTER);
        logAndErrors.add(errorScroll, BorderLayout.SOUTH);
        logAndErrors.setMinimumSize(new Dimension(200, 80));

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, batchPane, logAndErrors);
        rightSplit.setResizeWeight(0.35);
        rightSplit.setContinuousLayout(true);
        rightSplit.setOneTouchExpandable(true);
        rightSplit.setBorder(null);

        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.add(rightSplit, BorderLayout.CENTER);

        center.add(left, BorderLayout.WEST);
        center.add(right, BorderLayout.CENTER);
        return center;
    }

    private JPanel buildButtons() {
        JPanel south = new JPanel(new BorderLayout());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        south.add(statusLabel, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        stopButton.setEnabled(false);
        runButton.addActionListener(e -> startRun());
        stopButton.addActionListener(e -> stopRun());
        openLogButton.addActionListener(e -> openSessionLog());
        buttons.add(openLogButton);
        buttons.add(runButton);
        buttons.add(stopButton);
        south.add(buttons, BorderLayout.EAST);
        return south;
    }

    private void applyPreset(int index) {
        clearBatches();
        if (index == 1) {
            addBatchRow(8, 2, 10, 0);
            addBatchRow(8, 2, 10, 0);
        } else if (index == 2) {
            addBatchRow(25, EdsmRequestPolicy.MAX_CONCURRENT_REQUESTS, 0, 0);
        } else if (index == 3) {
            addBatchRow(2, 1, 12, 0);
            addBatchRow(2, 1, 12, 0);
            addBatchRow(2, 1, 12, 0);
        } else {
            addCurrentRoutePolicyBatches();
        }
    }

    private void addCurrentRoutePolicyBatches() {
        int openingRest = (int) RouteEdsmPrefetchPolicy.OPENING_REST.toSeconds();
        int cruiseRest = (int) RouteEdsmPrefetchPolicy.HEALTHY_WAVE_REST.toSeconds();
        addBatchRow(RouteEdsmPrefetchPolicy.OPENING_WINDOW_SIZE,
                RouteEdsmPrefetchPolicy.OPENING_BURST_CONCURRENCY, openingRest, 0, 1);
        addBatchRow(RouteEdsmPrefetchPolicy.HEALTHY_WAVE_SIZE, RouteEdsmPrefetchPolicy.MAX_CONCURRENT,
                cruiseRest, 0, 20);
    }

    private void clearBatches() {
        rows.clear();
        batchList.removeAll();
        batchList.revalidate();
        batchList.repaint();
    }

    private void restoreSavedState() {
        persistSuppressed = true;
        try {
            String savedSystems = EdsmPacingExperimentSettings.loadSystemsText();
            if (savedSystems != null) {
                systemsArea.setText(savedSystems);
            }
            List<EdsmPacingExperimentSettings.BatchSpec> saved = EdsmPacingExperimentSettings.loadBatches();
            if (saved.isEmpty()) {
                addCurrentRoutePolicyBatches();
                return;
            }
            for (EdsmPacingExperimentSettings.BatchSpec spec : saved) {
                addBatchRow(spec.count(), spec.concurrent(), spec.restSeconds(), spec.delayMs(), spec.repeats());
            }
        } finally {
            persistSuppressed = false;
        }
        refreshSystemsTitle();
    }

    private void persistSettings() {
        if (persistSuppressed) {
            return;
        }
        List<EdsmPacingExperimentSettings.BatchSpec> specs = currentSpecs();
        EdsmPacingExperimentSettings.save(specs, systemsArea.getText());
        refreshSystemsTitle();
    }

    private List<EdsmPacingExperimentSettings.BatchSpec> currentSpecs() {
        List<EdsmPacingExperimentSettings.BatchSpec> specs = new ArrayList<>();
        for (BatchRow row : rows) {
            specs.add(row.toSpec());
        }
        return specs;
    }

    private void fillSystemsToDemand() {
        int needed = EdsmPacingSampleSystems.demand(currentSpecs());
        persistSuppressed = true;
        try {
            systemsArea.setText(EdsmPacingSampleSystems.asEditableText(
                    EdsmPacingSampleSystems.namesFor(parseSystems(), needed)));
        } finally {
            persistSuppressed = false;
        }
        refreshSystemsTitle();
    }

    private void refreshSystemsTitle() {
        int have = parseSystems().size();
        int need = EdsmPacingSampleSystems.demand(currentSpecs());
        systemsScroll.setBorder(BorderFactory.createTitledBorder(
                "Systems (" + have + " listed, " + need + " needed for batches)"));
    }

    private void addBatchRow(int count, int concurrent, int restSeconds, int delayMs) {
        addBatchRow(count, concurrent, restSeconds, delayMs, 1);
    }

    private void addBatchRow(int count, int concurrent, int restSeconds, int delayMs, int repeats) {
        BatchRow row = new BatchRow(rows.size() + 1, count, concurrent, restSeconds, delayMs, repeats,
                this::removeBatchRow, this::persistSettings);
        rows.add(row);
        batchList.add(row.panel);
        batchList.add(Box.createVerticalStrut(4));
        renumber();
        batchList.revalidate();
        batchList.repaint();
        refreshSystemsTitle();
    }

    private void removeBatchRow(BatchRow row) {
        if (rows.size() <= 1) {
            return;
        }
        rows.remove(row);
        batchList.removeAll();
        for (int i = 0; i < rows.size(); i++) {
            batchList.add(rows.get(i).panel);
            batchList.add(Box.createVerticalStrut(4));
        }
        renumber();
        batchList.revalidate();
        batchList.repaint();
        persistSettings();
    }

    private void renumber() {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setNumber(i + 1);
        }
    }

    private void startRun() {
        List<EdsmPacingExperimentSettings.BatchSpec> specs = currentSpecs();
        int needed = EdsmPacingSampleSystems.demand(specs);
        if (needed <= 0) {
            statusLabel.setText("Add at least one batch.");
            return;
        }
        fillSystemsToDemand();
        List<String> systems = parseSystems();
        if (systems.size() < needed) {
            statusLabel.setText("Need " + needed + " system names for these batches.");
            return;
        }
        List<EdsmPacingExperiment.Batch> batches = new ArrayList<>();
        for (BatchRow row : rows) {
            EdsmPacingExperiment.Batch batch = row.toBatch();
            int repeats = row.repeats();
            for (int i = 0; i < repeats; i++) {
                batches.add(batch);
            }
        }
        persistSettings();
        clearLog();
        clearErrorCodes();
        runInProgress = true;
        runStartedAtMillis = System.currentTimeMillis();
        refreshElapsedLabel();
        LocalDateTime startedAt = LocalDateTime.now();
        sessionLog.startRun(startedAt, specs, systems.size(), batches.size());
        appendLog("RUN STARTED  " + startedAt.format(LOG_TIME), null, true);
        for (int i = 0; i < specs.size(); i++) {
            appendLog("  " + EdsmPacingExperimentLog.formatBatchLine(i + 1, specs.get(i)));
        }
        appendLog("Starting " + batches.size() + " wave(s) over " + systems.size()
                + " systems (live HTTP, no EDO cache).");
        if (sessionLog.lastError() != null) {
            appendLog("File log write failed: " + sessionLog.lastError(), LOG_FAILURE, true);
        }
        runButton.setEnabled(false);
        stopButton.setEnabled(true);
        statusLabel.setText("Running…");
        Thread thread = new Thread(() -> {
            String outcome = "failed";
            try {
                EdsmPacingExperiment.RunResult result = EdsmPacingExperiment.run(
                        systems, batches, new EdsmPacingBodiesHttp(), Thread::sleep,
                        new EdsmPacingExperiment.Listener() {
                            @Override
                            public void batchStarted(int batchNumber, EdsmPacingExperiment.Batch batch, int queried,
                                    int inFlight) {
                                appendLog("[batch " + batchNumber + "] start count=" + queried
                                        + " inFlight=" + inFlight + " delayMs=" + batch.launchDelayMs());
                            }

                            @Override
                            public void queryFinished(EdsmPacingExperiment.QueryResult query) {
                                boolean failed = query.outcome() != EdsmPacingExperiment.Outcome.SUCCESS;
                                appendLog("  " + query.outcome() + " HTTP " + query.statusCode()
                                        + " " + query.systemName() + " " + query.elapsedMs() + "ms"
                                        + (query.detail() != null && !query.detail().isBlank()
                                                ? " — " + query.detail() : ""),
                                        failed ? LOG_FAILURE : null, false);
                                if (failed) {
                                    recordErrorCodes(query.statusCode(), query.detail());
                                }
                            }

                            @Override
                            public void batchFinished(EdsmPacingExperiment.BatchResult batch) {
                                boolean failed = batch.status429() > 0 || batch.errors() > 0;
                                appendLog("[batch " + batch.batchNumber() + "] done count=" + batch.queried()
                                        + " status200=" + batch.status200()
                                        + " status429=" + batch.status429()
                                        + " errors=" + batch.errors()
                                        + " elapsedMs=" + batch.elapsedMs()
                                        + " restMs=" + batch.batch().restAfterMs(),
                                        failed ? LOG_FAILURE : LOG_SUCCESS, true);
                            }
                        });
                outcome = "unused=" + result.unusedSystems();
                appendLog("Finished unusedSystems=" + result.unusedSystems());
                SwingUtilities.invokeLater(() -> statusLabel.setText("Done. unused=" + result.unusedSystems()));
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
                outcome = "stopped";
                appendLog("Stopped.", LOG_FAILURE, true);
                SwingUtilities.invokeLater(() -> statusLabel.setText("Stopped."));
            } catch (Exception ex) {
                outcome = "failed: " + ex.getClass().getSimpleName()
                        + (ex.getMessage() != null ? " — " + ex.getMessage() : "");
                appendLog("Failed: " + ex.getClass().getSimpleName()
                        + (ex.getMessage() != null ? " — " + ex.getMessage() : ""),
                        LOG_FAILURE, true);
                SwingUtilities.invokeLater(() -> statusLabel.setText("Failed."));
            } finally {
                long elapsedMs = Math.max(0L, System.currentTimeMillis() - runStartedAtMillis);
                sessionLog.endRun(LocalDateTime.now(), elapsedMs, outcome);
                SwingUtilities.invokeLater(() -> {
                    runInProgress = false;
                    lastRunEndedAtMillis = System.currentTimeMillis();
                    refreshElapsedLabel();
                    runButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    worker = null;
                });
            }
        }, "EdsmPacingExperiment");
        worker = thread;
        thread.setDaemon(true);
        thread.start();
    }

    private void stopRun() {
        Thread running = worker;
        if (running != null) {
            running.interrupt();
        }
    }

    private void openSessionLog() {
        try {
            Path file = sessionLog.file();
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!Files.exists(file)) {
                Files.writeString(file, "", StandardCharsets.UTF_8);
            }
            if (!Desktop.isDesktopSupported()) {
                statusLabel.setText("Log: " + file);
                return;
            }
            Desktop.getDesktop().open(file.toFile());
        } catch (Exception ex) {
            statusLabel.setText("Could not open log: " + sessionLog.file());
        }
    }

    private List<String> parseSystems() {
        List<String> names = new ArrayList<>();
        for (String line : systemsArea.getText().split("\\R")) {
            if (line != null && !line.isBlank()) {
                names.add(line.trim());
            }
        }
        return names;
    }

    private void clearLog() {
        logArea.setText("");
    }

    private void clearErrorCodes() {
        seenErrorCodes.clear();
        errorModel.setRowCount(0);
    }

    private void recordErrorCodes(int statusCode, String detail) {
        SwingUtilities.invokeLater(() -> {
            for (String code : EdsmPacingErrorCatalog.codesFrom(statusCode, detail)) {
                if (seenErrorCodes.add(code)) {
                    errorModel.addRow(new Object[] { code, EdsmPacingErrorCatalog.describe(code) });
                }
            }
        });
    }

    private void refreshElapsedLabel() {
        if (runInProgress && runStartedAtMillis > 0L) {
            elapsedLabel.setText("This run: " + formatMmSs(System.currentTimeMillis() - runStartedAtMillis));
            return;
        }
        if (lastRunEndedAtMillis <= 0L) {
            elapsedLabel.setText("Since last run: —");
            return;
        }
        elapsedLabel.setText("Since last run: " + formatMmSs(System.currentTimeMillis() - lastRunEndedAtMillis));
    }

    private static String formatMmSs(long elapsedMs) {
        long totalSeconds = Math.max(0L, elapsedMs) / 1_000L;
        return String.format("%02d:%02d", Long.valueOf(totalSeconds / 60L), Long.valueOf(totalSeconds % 60L));
    }

    private void appendLog(String line) {
        appendLog(line, null, false);
    }

    private void appendLog(String line, Color color, boolean bold) {
        sessionLog.append(line);
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = logArea.getStyledDocument();
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setFontFamily(attrs, Font.MONOSPACED);
            StyleConstants.setFontSize(attrs, 12);
            if (color != null) {
                StyleConstants.setForeground(attrs, color);
            }
            StyleConstants.setBold(attrs, bold);
            try {
                if (doc.getLength() > 0) {
                    doc.insertString(doc.getLength(), "\n", attrs);
                }
                doc.insertString(doc.getLength(), line, attrs);
                logArea.setCaretPosition(doc.getLength());
            } catch (BadLocationException ignored) {
                // keep existing log text
            }
        });
    }

    private static final class BatchRow {
        private final JPanel panel = new JPanel(new GridBagLayout());
        private final JLabel title = new JLabel();
        private final JSpinner count;
        private final JSpinner concurrent;
        private final JSpinner restSeconds;
        private final JSpinner delayMs;
        private final JSpinner repeats;

        BatchRow(int number, int countVal, int concurrentVal, int restVal, int delayVal, int repeatsVal,
                java.util.function.Consumer<BatchRow> remove, Runnable onChange) {
            count = spinner(countVal, 1, 200);
            concurrent = spinner(concurrentVal, 1, 18);
            restSeconds = spinner(restVal, 0, 120);
            delayMs = spinner(delayVal, 0, 5_000);
            repeats = spinner(Math.max(1, repeatsVal), 1, 99);
            count.addChangeListener(e -> onChange.run());
            concurrent.addChangeListener(e -> onChange.run());
            restSeconds.addChangeListener(e -> onChange.run());
            delayMs.addChangeListener(e -> onChange.run());
            repeats.addChangeListener(e -> onChange.run());
            setNumber(number);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(2, 4, 2, 4);
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.WEST;

            gbc.gridx = 0;
            panel.add(title, gbc);
            addLabeled(panel, gbc, 1, "Count", count);
            addLabeled(panel, gbc, 3, "Concurrent", concurrent);
            addLabeled(panel, gbc, 5, "Rest s", restSeconds);
            addLabeled(panel, gbc, 7, "Delay ms", delayMs);
            addLabeled(panel, gbc, 9, "Repeat", repeats);
            JButton removeButton = new JButton("Remove");
            removeButton.addActionListener(e -> remove.accept(this));
            gbc.gridx = 11;
            panel.add(removeButton, gbc);
            panel.setBorder(BorderFactory.createEtchedBorder());
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        void setNumber(int number) {
            title.setText("Batch " + number);
        }

        EdsmPacingExperiment.Batch toBatch() {
            EdsmPacingExperimentSettings.BatchSpec spec = toSpec();
            return new EdsmPacingExperiment.Batch(
                    spec.count(), spec.concurrent(), spec.restSeconds() * 1_000, spec.delayMs());
        }

        int repeats() {
            return ((Number) repeats.getValue()).intValue();
        }

        EdsmPacingExperimentSettings.BatchSpec toSpec() {
            return new EdsmPacingExperimentSettings.BatchSpec(
                    ((Number) count.getValue()).intValue(),
                    ((Number) concurrent.getValue()).intValue(),
                    ((Number) restSeconds.getValue()).intValue(),
                    ((Number) delayMs.getValue()).intValue(),
                    repeats());
        }

        private static void addLabeled(JPanel panel, GridBagConstraints gbc, int x, String label, JSpinner spinner) {
            gbc.gridx = x;
            panel.add(new JLabel(label), gbc);
            gbc.gridx = x + 1;
            panel.add(spinner, gbc);
        }

        private static JSpinner spinner(int value, int min, int max) {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
            spinner.setPreferredSize(new Dimension(64, 24));
            return spinner;
        }
    }
}
