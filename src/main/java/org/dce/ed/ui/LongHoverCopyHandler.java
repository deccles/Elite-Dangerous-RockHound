package org.dce.ed.ui;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

import javax.swing.JTable;
import javax.swing.Timer;

public class LongHoverCopyHandler extends MouseAdapter implements MouseMotionListener {

    private static final int HOVER_DELAY_MS = 500;

    private final JTable table;
    private final int systemNameModelColumnIndex;
    private final Timer hoverTimer;

    private int hoverViewRow = -1;
    private int hoverViewColumn = -1;

    private LongHoverCopyHandler(JTable table, int systemNameModelColumnIndex) {
        this.table = table;
        this.systemNameModelColumnIndex = systemNameModelColumnIndex;

        hoverTimer = new Timer(HOVER_DELAY_MS, e -> copySystemNameIfStillHovering());
        hoverTimer.setRepeats(false);
    }

    /**
     * Install long-hover copy behavior on the given table.
     *
     * @param table                     The JTable to monitor.
     * @param systemNameModelColumnIndex The MODEL column index that holds the system name.
     */
    public static void install(JTable table, int systemNameModelColumnIndex) {
        LongHoverCopyHandler handler = new LongHoverCopyHandler(table, systemNameModelColumnIndex);
        table.addMouseMotionListener(handler);
        table.addMouseListener(handler);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        updateHoverLocation(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        // ignored – we only care about hover
    }

    @Override
    public void mouseExited(MouseEvent e) {
        hoverTimer.stop();
        hoverViewRow = -1;
        hoverViewColumn = -1;
        table.setCursor(Cursor.getDefaultCursor());
        table.setToolTipText(null);
    }

    private void updateHoverLocation(MouseEvent e) {
        Point p = e.getPoint();
        int viewRow = table.rowAtPoint(p);
        int viewCol = table.columnAtPoint(p);

        if (viewRow < 0 || viewCol < 0) {
            // Outside any cell – stop pending copy
            hoverTimer.stop();
            hoverViewRow = -1;
            hoverViewColumn = -1;
            table.setCursor(Cursor.getDefaultCursor());
            return;
        }

        // If we moved to a different cell, restart the timer
        if (viewRow != hoverViewRow || viewCol != hoverViewColumn) {
            hoverViewRow = viewRow;
            hoverViewColumn = viewCol;
            hoverTimer.restart();
            table.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
            table.setToolTipText("Hold to copy system name…");
        }
    }

    private void copySystemNameIfStillHovering() {
        if (hoverViewRow < 0) {
            return;
        }

        int viewRow = hoverViewRow;
        int modelRow = table.convertRowIndexToModel(viewRow);

        if (modelRow < 0 || modelRow >= table.getModel().getRowCount()) {
            return;
        }

        int modelCol = systemNameModelColumnIndex;
        if (modelCol < 0 || modelCol >= table.getModel().getColumnCount()) {
            return;
        }

        Object value = table.getModel().getValueAt(modelRow, modelCol);
        if (value == null) {
            return;
        }

        String systemName = value.toString().trim();
        if (systemName.isEmpty()) {
            return;
        }

        // Copy to clipboard
        StringSelection selection = new StringSelection(systemName);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);

        // Audible feedback
        Toolkit.getDefaultToolkit().beep();

        // Tooltip feedback – will show on next small mouse move
        table.setToolTipText("Copied: " + systemName);
        // Restore cursor
        table.setCursor(Cursor.getDefaultCursor());

        // You could also add a small status bar message elsewhere if you have one
    }
}
