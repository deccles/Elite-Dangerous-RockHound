package org.dce.ed.ui;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.JTableHeader;

/**
 * Poll-based hover activation for a table header column when mouse pass-through is enabled.
 */
public final class TableHeaderHoverActionSupport implements ActionListener {

    private static final int POLL_INTERVAL_MS = 40;

    private static final List<Entry> entries = new ArrayList<>();
    private static final Timer pollTimer;

    static {
        pollTimer = new Timer(POLL_INTERVAL_MS, new TableHeaderHoverActionSupport());
        pollTimer.start();
    }

    private static final class Entry {
        final JTable table;
        final int modelColumn;
        final int hoverDelayMs;
        final BooleanSupplier passThroughEnabled;
        final Runnable onActivate;

        boolean hovering;
        long hoverStartMs = -1L;
        boolean firedForCurrentHover;

        Entry(JTable table,
              int modelColumn,
              int hoverDelayMs,
              BooleanSupplier passThroughEnabled,
              Runnable onActivate) {
            this.table = table;
            this.modelColumn = modelColumn;
            this.hoverDelayMs = hoverDelayMs;
            this.passThroughEnabled = passThroughEnabled;
            this.onActivate = onActivate;
        }
    }

    private TableHeaderHoverActionSupport() {
    }

    public static void install(JTable table,
                               int modelColumn,
                               BooleanSupplier passThroughEnabled,
                               int hoverDelayMs,
                               Runnable onActivate) {
        if (table == null || onActivate == null || modelColumn < 0 || table.getTableHeader() == null) {
            return;
        }
        entries.removeIf(entry -> entry.table == table && entry.modelColumn == modelColumn);
        entries.add(new Entry(
                table,
                modelColumn,
                Math.max(0, hoverDelayMs),
                passThroughEnabled,
                onActivate));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (entries.isEmpty()) {
            return;
        }
        java.awt.PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        if (pointerInfo == null) {
            for (Entry entry : entries) {
                entry.hovering = false;
                entry.hoverStartMs = -1L;
                entry.firedForCurrentHover = false;
            }
            return;
        }
        Point mouseOnScreen = pointerInfo.getLocation();
        long now = System.currentTimeMillis();
        for (Entry entry : entries) {
            boolean mpt = entry.passThroughEnabled != null && entry.passThroughEnabled.getAsBoolean();
            if (!mpt || !entry.table.isShowing()) {
                entry.hovering = false;
                entry.hoverStartMs = -1L;
                entry.firedForCurrentHover = false;
                continue;
            }
            JTableHeader header = entry.table.getTableHeader();
            if (header == null || !header.isShowing()) {
                entry.hovering = false;
                entry.hoverStartMs = -1L;
                entry.firedForCurrentHover = false;
                continue;
            }
            Point local = new Point(mouseOnScreen);
            SwingUtilities.convertPointFromScreen(local, header);
            int viewCol = header.columnAtPoint(local);
            boolean over = false;
            if (viewCol >= 0) {
                int modelCol = entry.table.convertColumnIndexToModel(viewCol);
                over = modelCol == entry.modelColumn;
            }
            if (!over) {
                entry.hovering = false;
                entry.hoverStartMs = -1L;
                entry.firedForCurrentHover = false;
                continue;
            }
            if (!entry.hovering) {
                entry.hovering = true;
                entry.hoverStartMs = now;
                entry.firedForCurrentHover = false;
            }
            if (!entry.firedForCurrentHover && now - entry.hoverStartMs >= entry.hoverDelayMs) {
                entry.firedForCurrentHover = true;
                entry.onActivate.run();
            }
        }
    }
}
