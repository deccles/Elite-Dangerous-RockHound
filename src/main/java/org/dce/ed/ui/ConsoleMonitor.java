/*
This is the copyright work of The MITRE Corporation, and was produced for the U. S. Government under
Contract Number 693KA8-22-C-00001, and is subject to Federal Aviation Administration Acquisition Management System
Clause 3.5-13, Rights In Data-General (Oct. 2014), Alt. III and Alt. IV (Jan. 2009).  No other use other than that
granted to the U. S. Government, or to those acting on behalf of the U. S. Government, under that Clause is authorized
without the express written permission of The MITRE Corporation. For further information, please contact The MITRE
Corporation, Contracts Management Office, 7515 Colshire Drive, McLean, VA  22102-7539, (703) 983-6000.

(c) 2025 The MITRE Corporation. All Rights Reserved.
 */
package org.dce.ed.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import javax.swing.text.Element;

/**
 * ConsoleMonitor with per-run log file and bounded UI memory.
 * - One dated log file per run (override directory with -Dconsole.log.dir)
 * - Tees System.out/err to: all open consoles, the log file, and the original stdout/stderr
 * - Efficient appending + trimming honoring maxLines; CRLF normalized; no extra blank lines
 * - Sticky-scroll: stays put when you scroll up; re-sticks when you scroll back to bottom
 * - Opens normally (not minimized)
 */
public class ConsoleMonitor extends JPanel {

    private static final long serialVersionUID = 1L;

    // ---- Global redirection state ----
    private static final AtomicBoolean REDIRECTED = new AtomicBoolean(false);
    private static volatile PrintStream ORIGINAL_OUT;
    private static volatile PrintStream ORIGINAL_ERR;
    private static final LineBroadcaster BROADCAST = new LineBroadcaster();

    // ---- Logging config ----
    private static final DateTimeFormatter LOG_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String LOG_DIR_PROP = "console.log.dir"; // -Dconsole.log.dir=/path/to/logs
    private static volatile File logFile;
    private static volatile OutputStream logStream;

    // ---- UI ----
    private final JTextArea textArea = new JTextArea();
    private final JScrollPane scrollPane = new JScrollPane(textArea);
    private int maxLines;              // visual line cap (<=0 = unlimited)
    private int maxChars = 2_000_000;  // soft cap by characters

    // ---- Sticky scroll management ----
    private static final int BOTTOM_TOLERANCE_PX = 16;
    private volatile boolean stickToBottom = true;  // desired sticky state
    private volatile boolean userScrolling = false; // toggled only by user input listeners
    private final StringBuilder pending = new StringBuilder(4096);
    private final Timer flushTimer = new Timer(25, e -> flushPending()); // coalesce bursts (~40fps)
    private final Timer userScrollCooloff = new Timer(300, e -> userScrolling = false);
    private volatile boolean appendingNow = false;

    private static ConsoleMonitor instance = null;
    
    public static ConsoleMonitor getInstance(int maxLines) {
        if (instance == null)
            instance = new ConsoleMonitor(maxLines);
        return instance;
    }
    
    private ConsoleMonitor(int maxLines) {
        super(new BorderLayout());
        this.maxLines = maxLines;

        textArea.setEditable(false);
        textArea.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        // Original color scheme from your file
        textArea.setBackground(EdoUi.User.MAIN_TEXT);
        textArea.setForeground(Color.white);
        textArea.setLineWrap(false);
        textArea.setWrapStyleWord(false);
        ((DefaultCaret) textArea.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, java.awt.Color.DARK_GRAY));

        // Register this UI to receive live lines
        BROADCAST.register(this::appendLine);

        // timers setup
        flushTimer.setCoalesce(true);
        userScrollCooloff.setRepeats(false);

        // Detect user-initiated scroll via wheel
        scrollPane.addMouseWheelListener(e -> {
            userScrolling = true;
            userScrollCooloff.restart();
            if (!appendingNow) stickToBottom = isAtBottom();
        });

        // Detect user-initiated scroll via scrollbar drag
        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (e.getValueIsAdjusting()) {
                userScrolling = true;
                userScrollCooloff.restart();
            }
            if (!appendingNow) stickToBottom = isAtBottom();
        });

        // Detect user-initiated scroll via keys (PgUp/PgDn/Home/End/Arrows)
        textArea.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_PAGE_UP:
                    case KeyEvent.VK_PAGE_DOWN:
                    case KeyEvent.VK_HOME:
                    case KeyEvent.VK_END:
                    case KeyEvent.VK_UP:
                    case KeyEvent.VK_DOWN:
                        userScrolling = true;
                        userScrollCooloff.restart();
                        if (!appendingNow) stickToBottom = isAtBottom();
                        break;
                }
            }
        });
    }

    /** Start per-run logging and tee System.out/err to UI, file, and original console. Idempotent. */
    public void redirectOutput() {
        if (!REDIRECTED.compareAndSet(false, true)) {
            return; // already redirected
        }
        ensureLogFile();

        ORIGINAL_OUT = System.out;
        ORIGINAL_ERR = System.err;

        // Base chain goes to the UI broadcaster (+ log if present)
        OutputStream base = (logStream != null)
                ? new TeeOutputStream(BROADCAST, logStream)
                : BROADCAST;

        // IMPORTANT: keep stdout and stderr distinct to avoid cross-mirroring in IDE
        PrintStream outPs;
        PrintStream errPs;
        try {
            outPs = new PrintStream(new TeeOutputStream(base, ORIGINAL_OUT), true, StandardCharsets.UTF_8.name());
            errPs = new PrintStream(new TeeOutputStream(base, ORIGINAL_ERR), true, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            outPs = new PrintStream(new TeeOutputStream(base, ORIGINAL_OUT), true);
            errPs = new PrintStream(new TeeOutputStream(base, ORIGINAL_ERR), true);
        }

        System.setOut(outPs);
        System.setErr(errPs);
    }

    /** Coalesced append: buffer lines and flush in small bursts on the EDT. */
    void appendLine(String line) {
        if (line == null) return;
        synchronized (pending) {
            pending.append(line).append('\n'); // normalize to '\n' once
        }
        if (!flushTimer.isRunning()) flushTimer.start();
        else                         flushTimer.restart();
    }

    /** Called by flushTimer. Appends buffered content once and decides stickiness atomically. */
    private void flushPending() {
        final String chunk;
        synchronized (pending) {
            if (pending.length() == 0) return;
            chunk = pending.toString();
            pending.setLength(0);
        }

        SwingUtilities.invokeLater(() -> {
            appendingNow = true;
            try {
                // Only stick if the user is NOT actively scrolling and we were at bottom
                final boolean shouldStick = !userScrolling && stickToBottom && isAtBottom();

                var vp = scrollPane.getViewport();
                var view = vp.getViewRect();
                int oldY = view.y;

                textArea.append(chunk);

                int removed = trimToMax(); // trim top if needed

                if (shouldStick) {
                    textArea.setCaretPosition(textArea.getDocument().getLength());
                } else if (removed > 0) {
                    int lineH = getLineHeight();
                    int newY = Math.max(0, oldY - removed * lineH);
                    vp.setViewPosition(new Point(view.x, newY));
                }
            } finally {
                appendingNow = false;
                // If the user isn't actively scrolling now, update sticky-state based on where we ended up
                if (!userScrolling) stickToBottom = isAtBottom();
            }
        });
    }

    private int getLineHeight() {
        return textArea.getFontMetrics(textArea.getFont()).getHeight();
    }

    private boolean isAtBottom() {
        var vbar = scrollPane.getVerticalScrollBar();
        int value  = vbar.getValue();
        int extent = vbar.getModel().getExtent();
        int max    = vbar.getMaximum();
        return value + extent >= max - BOTTOM_TOLERANCE_PX;
    }

    /** Trim and return number of lines removed from the top. */
    private int trimToMax() {
        int removedLines = 0;
        Document doc = textArea.getDocument();

        if (maxLines > 0) {
            Element root = doc.getDefaultRootElement();
            while (root.getElementCount() > maxLines) {
                Element first = root.getElement(0);
                int end = first.getEndOffset(); // includes newline
                try {
                    doc.remove(0, end);
                    removedLines++;
                } catch (BadLocationException e) {
                    break;
                }
            }
        }
        if (maxChars > 0) {
            int over = doc.getLength() - maxChars;
            if (over > 0) {
                try {
                    int before = doc.getDefaultRootElement().getElementCount();
                    doc.remove(0, over);
                    int after = doc.getDefaultRootElement().getElementCount();
                    removedLines += Math.max(0, before - after);
                } catch (BadLocationException ignore) {}
            }
        }
        return removedLines;
    }

    /** Load the tail of the current log file into this UI (bounded by maxLines or 10k). */
    public void loadExistingLogIntoView() {
        ensureLogFile();
        if (logFile == null || !logFile.exists()) return;

        final int cap = (maxLines > 0) ? maxLines : 10_000;
        final java.util.ArrayDeque<String> tail = new java.util.ArrayDeque<>(Math.min(cap, 8192));

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(logFile), StandardCharsets.UTF_8), 8192)) {
            String ln;
            while ((ln = br.readLine()) != null) {
                if (tail.size() == cap) tail.removeFirst();
                tail.addLast(ln);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        final StringBuilder sb = new StringBuilder();
        for (String s : tail) sb.append(s).append('\n');

        SwingUtilities.invokeLater(() -> {
            textArea.setText(sb.toString());
            trimToMax();
            // start stuck to the bottom on open
            textArea.setCaretPosition(textArea.getDocument().getLength());
            stickToBottom = true;
        });
    }

    /** Create or reuse the per-run log file & stream. */
    private static synchronized void ensureLogFile() {
        if (logFile != null && logStream != null) return;

        String dirProp = System.getProperty(LOG_DIR_PROP);
        File dir = (dirProp != null && !dirProp.isBlank()) ? new File(dirProp) : new File("logs");

        if (!dir.exists()) {
        	dir.mkdirs();
        }

        // If creation failed (or path isn't a directory), fall back to a writable per-user location.
        if (!dir.exists() || !dir.isDirectory()) {
        	dir = new File(new File(System.getProperty("user.home"), ".edo"), "logs");
        	if (!dir.exists()) {
        		dir.mkdirs();
        	}
        }

        String stamp = LocalDateTime.now().format(LOG_TS);
        logFile = new File(dir, "console-" + stamp + ".log");

        try {
            logStream = new BufferedOutputStream(new FileOutputStream(logFile, true));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { logStream.flush(); logStream.close(); } catch (Exception ignore) {}
            }, "console-log-close"));
        } catch (IOException ioe) {
            ioe.printStackTrace();
            logFile = null;
            logStream = null;
        }
    }

    /** Optional: adjust max lines at runtime. */
    public void setMaxLines(int maxLines) {
        this.maxLines = maxLines;
        SwingUtilities.invokeLater(this::trimToMax);
    }
    public int getMaxLines() { return maxLines; }

    // ---- Help-menu utilities ----

    public static File getCurrentLogFile() {
        ensureLogFile();
        return logFile;
    }

    /** Reveal and HIGHLIGHT the log file in the OS file browser, if supported. */
    public static void revealCurrentLogFile(Component parent) {
        ensureLogFile();
        if (logFile == null) {
            JOptionPane.showMessageDialog(parent, "No log file created yet.", "Open Log", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", "/select,", logFile.getAbsolutePath()).start();
                return;
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", "-R", logFile.getAbsolutePath()).start();
                return;
            }
            if (Desktop.isDesktopSupported()) {
                try { Desktop.getDesktop().browseFileDirectory(logFile); return; } catch (Throwable ignored) {}
                Desktop.getDesktop().open(logFile.getParentFile());
                return;
            }
            new ProcessBuilder("xdg-open", logFile.getParent()).start();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Couldn't open log location:\n" + ex,
                    "Open Log", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- Streams & broadcaster ----

    /** OutputStream that assembles lines and broadcasts them to all registered listeners (all UIs). */
    private static class LineBroadcaster extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
        private final CopyOnWriteArrayList<java.util.function.Consumer<String>> listeners = new CopyOnWriteArrayList<>();

        void register(java.util.function.Consumer<String> listener) { if (listener != null) listeners.add(listener); }
        void unregister(java.util.function.Consumer<String> listener) { if (listener != null) listeners.remove(listener); }

        @Override
        public void write(int b) throws IOException {
            if (b == '\n') {
                flushBufferAsLine();
            } else {
                buffer.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            int start = off;
            int end = off + len;
            for (int i = off; i < end; i++) {
                if (b[i] == (byte) '\n') {
                    buffer.write(b, start, i - start);
                    flushBufferAsLine();
                    start = i + 1;
                }
            }
            if (start < end) {
                buffer.write(b, start, end - start);
            }
        }

        @Override
        public void flush() {
            // Do NOT emit partial lines on flush() to avoid extra blank lines
        }

        private void flushBufferAsLine() {
            String line = buffer.toString(StandardCharsets.UTF_8);
            buffer.reset();
            // Strip trailing \r from CRLF so we render as a single newline
            if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
                line = line.substring(0, line.length() - 1);
            }
            for (var l : listeners) {
                try { l.accept(line); } catch (Throwable ignore) {}
            }
        }
    }

    /** Simple tee that writes bytes to two streams. */
    private static class TeeOutputStream extends OutputStream {
        private final OutputStream a;
        private final OutputStream b;
        TeeOutputStream(OutputStream a, OutputStream b) { this.a = Objects.requireNonNull(a); this.b = b; }

        @Override public void write(int bt) throws IOException { a.write(bt); if (b != null) b.write(bt); }
        @Override public void write(byte[] buf, int off, int len) throws IOException { a.write(buf, off, len); if (b != null) b.write(buf, off, len); }
        @Override public void flush() throws IOException { a.flush(); if (b != null) b.flush(); }
        @Override public void close() throws IOException { try { a.close(); } finally { if (b != null) b.close(); } }
    }

    /** Multi-output wrapper to mirror to both original System.out and System.err. */
    private static class MultiOutputStream extends OutputStream {
        private final OutputStream primary;
        private final List<OutputStream> others;
        MultiOutputStream(OutputStream primary, List<OutputStream> others) {
            this.primary = Objects.requireNonNull(primary);
            this.others = (others == null) ? java.util.Collections.emptyList() : others;
        }
        @Override public void write(int b) throws IOException { primary.write(b); for (OutputStream o : others) if (o != null) o.write(b); }
        @Override public void write(byte[] b, int off, int len) throws IOException { primary.write(b, off, len); for (OutputStream o : others) if (o != null) o.write(b, off, len); }
        @Override public void flush() throws IOException { primary.flush(); for (OutputStream o : others) if (o != null) o.flush(); }
        @Override public void close() throws IOException { try { primary.close(); } finally { for (OutputStream o : others) if (o != null) o.close(); } }
    }

    // ---- Convenience for quick display ----

    /** Show the console in a JFrame. Opens normally, starts logging (if not already), loads tail. */
    public static ConsoleMonitor showConsoleMonitor(int maxLines) {
        JFrame frame = new JFrame("Console Monitor");
        frame.setExtendedState(JFrame.NORMAL);
        ConsoleMonitor consoleMonitor = new ConsoleMonitor(maxLines);
        consoleMonitor.redirectOutput();
        consoleMonitor.loadExistingLogIntoView();

        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationByPlatform(true);
        frame.add(consoleMonitor, BorderLayout.CENTER);
        frame.setVisible(true);
        return consoleMonitor;
    }
}
