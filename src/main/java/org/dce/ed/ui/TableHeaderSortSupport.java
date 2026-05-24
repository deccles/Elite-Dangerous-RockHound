package org.dce.ed.ui;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;

/**
 * Column sorting via header click (normal overlay mode) or header hover (mouse pass-through / MPT).
 */
public final class TableHeaderSortSupport implements ActionListener {

    private static final int POLL_INTERVAL_MS = 40;

    private static final List<Entry> entries = new ArrayList<>();
    private static final Timer pollTimer;

    static {
        pollTimer = new Timer(POLL_INTERVAL_MS, new TableHeaderSortSupport());
        pollTimer.start();
    }

    private static final class Entry {
        final JTable table;
        final int hoverDelayMs;
        final BooleanSupplier passThroughEnabled;

        int hoverColumn = -1;
        long hoverStartMs = -1L;
        boolean firedForCurrentHover;

        Entry(JTable table, int hoverDelayMs, BooleanSupplier passThroughEnabled) {
            this.table = table;
            this.hoverDelayMs = hoverDelayMs;
            this.passThroughEnabled = passThroughEnabled;
        }
    }

    private TableHeaderSortSupport() {
    }

    /**
     * Registers hover-to-sort when pass-through is on. Normal clicks are handled by the table header UI
     * ({@code BasicTableHeaderUI}) so each click toggles asc/desc once; do not add a second click listener.
     */
    public static void install(JTable table, BooleanSupplier passThroughEnabled, int hoverDelayMs) {
        if (table == null || table.getRowSorter() == null) {
            return;
        }
        if (table.getTableHeader() == null) {
            return;
        }
        entries.add(new Entry(table, Math.max(0, hoverDelayMs), passThroughEnabled));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (entries.isEmpty()) {
            return;
        }
        java.awt.PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        if (pointerInfo == null) {
            resetAll();
            return;
        }
        Point mouseOnScreen = pointerInfo.getLocation();
        long now = System.currentTimeMillis();

        for (Entry entry : entries) {
            if (entry.passThroughEnabled == null || !entry.passThroughEnabled.getAsBoolean()) {
                entry.hoverColumn = -1;
                entry.hoverStartMs = -1L;
                entry.firedForCurrentHover = false;
                continue;
            }
            JTable table = entry.table;
            if (table == null || !table.isShowing()) {
                entry.hoverColumn = -1;
                entry.hoverStartMs = -1L;
                entry.firedForCurrentHover = false;
                continue;
            }
            JTableHeader header = table.getTableHeader();
            if (header == null || !header.isShowing()) {
                entry.hoverColumn = -1;
                entry.hoverStartMs = -1L;
                entry.firedForCurrentHover = false;
                continue;
            }
            Point headerLoc;
            try {
                headerLoc = header.getLocationOnScreen();
            } catch (IllegalStateException ex) {
                entry.hoverColumn = -1;
                entry.hoverStartMs = -1L;
                entry.firedForCurrentHover = false;
                continue;
            }
            Rectangle bounds = new Rectangle(
                    headerLoc.x, headerLoc.y, header.getWidth(), header.getHeight());
            if (!bounds.contains(mouseOnScreen)) {
                entry.hoverColumn = -1;
                entry.hoverStartMs = -1L;
                entry.firedForCurrentHover = false;
                continue;
            }
            Point local = new Point(mouseOnScreen);
            SwingUtilities.convertPointFromScreen(local, header);
            int col = header.columnAtPoint(local);
            if (col < 0) {
                entry.hoverColumn = -1;
                entry.hoverStartMs = -1L;
                entry.firedForCurrentHover = false;
                continue;
            }
            if (col != entry.hoverColumn) {
                entry.hoverColumn = col;
                entry.hoverStartMs = now;
                entry.firedForCurrentHover = false;
            } else if (!entry.firedForCurrentHover && now - entry.hoverStartMs >= entry.hoverDelayMs) {
                entry.firedForCurrentHover = true;
                int modelCol = table.convertColumnIndexToModel(col);
                SwingUtilities.invokeLater(() -> toggleSortOrder(table, modelCol));
            }
        }
    }

    private static void resetAll() {
        for (Entry entry : entries) {
            entry.hoverColumn = -1;
            entry.hoverStartMs = -1L;
            entry.firedForCurrentHover = false;
        }
    }

    private static void toggleSortOrder(JTable table, int modelColumn) {
        RowSorter<? extends javax.swing.table.TableModel> sorter = table.getRowSorter();
        if (!(sorter instanceof TableRowSorter<?> trs)) {
            return;
        }
        if (modelColumn < 0 || modelColumn >= table.getModel().getColumnCount()) {
            return;
        }
        trs.toggleSortOrder(modelColumn);
        JTableHeader header = table.getTableHeader();
        if (header != null) {
            header.repaint();
        }
        table.repaint();
    }
}
