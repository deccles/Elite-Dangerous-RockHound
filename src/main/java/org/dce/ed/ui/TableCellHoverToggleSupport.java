package org.dce.ed.ui;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Poll-based hover-to-toggle for a single table column when mouse pass-through is enabled.
 */
public final class TableCellHoverToggleSupport implements ActionListener {

    private static final int POLL_INTERVAL_MS = 40;

    private static final List<Entry> entries = new ArrayList<>();
    private static final Timer pollTimer;

    static {
        pollTimer = new Timer(POLL_INTERVAL_MS, new TableCellHoverToggleSupport());
        pollTimer.start();
    }

    private static final class Entry {
        final JTable table;
        final int modelColumn;
        final int hoverDelayMs;
        final int expandRightPx;
        final BooleanSupplier passThroughEnabled;
        final IntConsumer onToggleModelRow;

        int hoverModelRow = -1;
        long hoverStartMs = -1L;
        boolean firedForCurrentHover;

        Entry(JTable table,
              int modelColumn,
              int hoverDelayMs,
              int expandRightPx,
              BooleanSupplier passThroughEnabled,
              IntConsumer onToggleModelRow) {
            this.table = table;
            this.modelColumn = modelColumn;
            this.hoverDelayMs = hoverDelayMs;
            this.expandRightPx = expandRightPx;
            this.passThroughEnabled = passThroughEnabled;
            this.onToggleModelRow = onToggleModelRow;
        }
    }

    private TableCellHoverToggleSupport() {
    }

    /**
     * Poll-based hover-to-toggle for a single table column when mouse pass-through is enabled.
     *
     * @param expandRightPx extra hit area into the next column (helps skinny checkbox columns)
     */
    public static void install(JTable table,
                               int modelColumn,
                               BooleanSupplier passThroughEnabled,
                               int hoverDelayMs,
                               IntConsumer onToggleModelRow) {
        install(table, modelColumn, passThroughEnabled, hoverDelayMs, 0, onToggleModelRow);
    }

    public static void install(JTable table,
                               int modelColumn,
                               BooleanSupplier passThroughEnabled,
                               int hoverDelayMs,
                               int expandRightPx,
                               IntConsumer onToggleModelRow) {
        if (table == null || onToggleModelRow == null || modelColumn < 0) {
            return;
        }
        entries.removeIf(entry -> entry.table == table && entry.modelColumn == modelColumn);
        entries.add(new Entry(
                table,
                modelColumn,
                Math.max(0, hoverDelayMs),
                Math.max(0, expandRightPx),
                passThroughEnabled,
                onToggleModelRow));
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
                resetEntry(entry);
                continue;
            }
            JTable table = entry.table;
            if (table == null || !table.isShowing()) {
                resetEntry(entry);
                continue;
            }
            Point tableLoc;
            try {
                tableLoc = table.getLocationOnScreen();
            } catch (IllegalStateException ex) {
                resetEntry(entry);
                continue;
            }
            Point local = new Point(mouseOnScreen);
            SwingUtilities.convertPointFromScreen(local, table);
            int viewRow = table.rowAtPoint(local);
            int viewColumn = table.columnAtPoint(local);
            if (viewRow < 0 || viewColumn < 0) {
                resetEntry(entry);
                continue;
            }
            int modelColumn = table.convertColumnIndexToModel(viewColumn);
            boolean inTargetColumn = modelColumn == entry.modelColumn;
            boolean inExpandedHit = false;
            if (!inTargetColumn && entry.expandRightPx > 0) {
                int targetViewCol = -1;
                for (int c = 0; c < table.getColumnCount(); c++) {
                    if (table.convertColumnIndexToModel(c) == entry.modelColumn) {
                        targetViewCol = c;
                        break;
                    }
                }
                if (targetViewCol >= 0) {
                    Rectangle targetRect = table.getCellRect(viewRow, targetViewCol, true);
                    Rectangle expanded = new Rectangle(
                            targetRect.x,
                            targetRect.y,
                            targetRect.width + entry.expandRightPx,
                            targetRect.height);
                    inExpandedHit = expanded.contains(local);
                }
            }
            if (!inTargetColumn && !inExpandedHit) {
                resetEntry(entry);
                continue;
            }
            int hitViewColumn = inTargetColumn ? viewColumn : /* use target */ -1;
            if (hitViewColumn < 0) {
                for (int c = 0; c < table.getColumnCount(); c++) {
                    if (table.convertColumnIndexToModel(c) == entry.modelColumn) {
                        hitViewColumn = c;
                        break;
                    }
                }
            }
            if (hitViewColumn < 0) {
                resetEntry(entry);
                continue;
            }
            Rectangle cellRect = table.getCellRect(viewRow, hitViewColumn, true);
            Rectangle screenRect = new Rectangle(
                    tableLoc.x + cellRect.x,
                    tableLoc.y + cellRect.y,
                    cellRect.width + entry.expandRightPx,
                    cellRect.height);
            if (!screenRect.contains(mouseOnScreen)) {
                resetEntry(entry);
                continue;
            }
            int modelRow = table.convertRowIndexToModel(viewRow);
            if (modelRow < 0 || modelRow >= table.getModel().getRowCount()) {
                resetEntry(entry);
                continue;
            }
            if (modelRow != entry.hoverModelRow) {
                entry.hoverModelRow = modelRow;
                entry.hoverStartMs = now;
                entry.firedForCurrentHover = false;
            } else if (!entry.firedForCurrentHover && now - entry.hoverStartMs >= entry.hoverDelayMs) {
                entry.firedForCurrentHover = true;
                int rowToToggle = modelRow;
                SwingUtilities.invokeLater(() -> entry.onToggleModelRow.accept(rowToToggle));
            }
        }
    }

    private static void resetEntry(Entry entry) {
        entry.hoverModelRow = -1;
        entry.hoverStartMs = -1L;
        entry.firedForCurrentHover = false;
    }

    private static void resetAll() {
        for (Entry entry : entries) {
            resetEntry(entry);
        }
    }
}
