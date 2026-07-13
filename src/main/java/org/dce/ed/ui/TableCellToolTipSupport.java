package org.dce.ed.ui;

import java.awt.Point;
import java.awt.event.MouseEvent;

import javax.swing.JTable;

/**
 * Resolves full cell text for table tooltips (renderer tooltips are unreliable on overlay tables).
 */
public final class TableCellToolTipSupport {

    private TableCellToolTipSupport() {
    }

    public static String cellTextAt(JTable table, MouseEvent event) {
        if (table == null || event == null) {
            return null;
        }
        Point p = event.getPoint();
        int row = table.rowAtPoint(p);
        int col = table.columnAtPoint(p);
        if (row < 0 || col < 0) {
            return null;
        }
        Object value = table.getValueAt(row, col);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }
}
