package org.dce.ed.ui;

import java.awt.Component;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.JTableHeader;

/**
 * Screen-coordinate hit tests for Selective mouse mode (clear {@code WS_EX_TRANSPARENT} over controls).
 */
public final class SelectiveHitSupport {

    private SelectiveHitSupport() {
    }

    public static boolean containsScreenPoint(Component component, Point screenPoint) {
        if (component == null || !component.isShowing() || screenPoint == null) {
            return false;
        }
        try {
            Point origin = component.getLocationOnScreen();
            return new Rectangle(origin.x, origin.y, component.getWidth(), component.getHeight())
                    .contains(screenPoint);
        } catch (IllegalComponentStateException ex) {
            return false;
        }
    }

    public static boolean isOverTableHeader(JTable table, Point screenPoint) {
        if (table == null || screenPoint == null) {
            return false;
        }
        JTableHeader header = table.getTableHeader();
        return containsScreenPoint(header, screenPoint);
    }

    /**
     * True when the pointer is over a table body cell whose model column is one of {@code modelColumns}.
     */
    public static boolean isOverModelColumnCell(JTable table, Point screenPoint, int... modelColumns) {
        if (table == null || !table.isShowing() || screenPoint == null || modelColumns == null
                || modelColumns.length == 0) {
            return false;
        }
        Point local = new Point(screenPoint);
        SwingUtilities.convertPointFromScreen(local, table);
        int viewRow = table.rowAtPoint(local);
        int viewCol = table.columnAtPoint(local);
        if (viewRow < 0 || viewCol < 0) {
            return false;
        }
        int modelCol = table.convertColumnIndexToModel(viewCol);
        for (int c : modelColumns) {
            if (c == modelCol) {
                return true;
            }
        }
        return false;
    }
}
