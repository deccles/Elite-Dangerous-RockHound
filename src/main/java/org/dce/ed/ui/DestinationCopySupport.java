package org.dce.ed.ui;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Double-click and pass-through hover copy for destination columns in mission tables.
 */
public final class DestinationCopySupport {

    private static final int POLL_INTERVAL_MS = 100;
    private static final int HOVER_DELAY_MS = 1500;

    /**
     * Places / turn-in copy text. {@code preferFrom} is true when the pointer is in the upper half
     * of the cell (From row in Transport Places); callers that only have a single destination
     * may ignore it.
     */
    @FunctionalInterface
    public interface PlacesCopyText {
        String apply(int modelRow, boolean preferFrom);
    }

    private DestinationCopySupport() {
    }

    public static void install(JTable table,
            int objectiveColumn,
            int placesColumn,
            Function<Integer, String> objectiveText,
            PlacesCopyText placesText,
            BooleanSupplier passThroughEnabledSupplier) {
        if (objectiveColumn >= 0) {
            new ColumnHoverCopier(table, objectiveColumn,
                    (row, preferFrom) -> objectiveText.apply(row),
                    passThroughEnabledSupplier).start();
        }
        if (placesColumn >= 0) {
            new ColumnHoverCopier(table, placesColumn, placesText, passThroughEnabledSupplier).start();
        }
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row < 0 || col < 0) {
                    return;
                }
                int modelCol = table.convertColumnIndexToModel(col);
                int modelRow = table.convertRowIndexToModel(row);
                String text = null;
                if (modelCol == objectiveColumn) {
                    text = objectiveText.apply(modelRow);
                } else if (modelCol == placesColumn) {
                    text = placesText.apply(modelRow, preferFromHalf(table, row, col, e.getPoint()));
                }
                copyIfValid(table, text);
            }
        });
    }

    /** Upper half of the cell → From; lower half → To. */
    public static boolean preferFromHalf(JTable table, int viewRow, int viewCol, Point tablePoint) {
        if (table == null || tablePoint == null || viewRow < 0 || viewCol < 0) {
            return false;
        }
        Rectangle cell = table.getCellRect(viewRow, viewCol, true);
        if (cell == null || cell.height <= 0) {
            return false;
        }
        return tablePoint.y < cell.y + cell.height / 2;
    }

    private static void copyIfValid(JTable table, String text) {
        if (text == null || text.isBlank() || "—".equals(text.trim())) {
            return;
        }
        text = text.trim();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        SystemTableHoverCopyManager.showCopiedToast(table, text);
    }

    private static final class ColumnHoverCopier {
        private final JTable table;
        private final int modelColumnIndex;
        private final PlacesCopyText textForRow;
        private final BooleanSupplier passThroughEnabledSupplier;
        private final Timer pollTimer;
        private final Timer hoverTimer;
        private int hoverViewRow = -1;
        private boolean hoverPreferFrom;

        ColumnHoverCopier(JTable table,
                int modelColumnIndex,
                PlacesCopyText textForRow,
                BooleanSupplier passThroughEnabledSupplier) {
            this.table = table;
            this.modelColumnIndex = modelColumnIndex;
            this.textForRow = textForRow;
            this.passThroughEnabledSupplier = passThroughEnabledSupplier;
            this.pollTimer = new Timer(POLL_INTERVAL_MS, e -> pollMousePosition());
            this.hoverTimer = new Timer(HOVER_DELAY_MS, e -> copyIfStillHovering());
            this.hoverTimer.setRepeats(false);
        }

        void start() {
            pollTimer.start();
        }

        private void pollMousePosition() {
            if (!table.isShowing()) {
                hoverTimer.stop();
                hoverViewRow = -1;
                return;
            }
            if (passThroughEnabledSupplier != null && !passThroughEnabledSupplier.getAsBoolean()) {
                hoverTimer.stop();
                hoverViewRow = -1;
                return;
            }
            java.awt.PointerInfo info = MouseInfo.getPointerInfo();
            if (info == null) {
                hoverTimer.stop();
                hoverViewRow = -1;
                return;
            }
            Point screenPoint = info.getLocation();
            Point tablePoint = new Point(screenPoint);
            SwingUtilities.convertPointFromScreen(tablePoint, table);
            if (tablePoint.x < 0 || tablePoint.y < 0
                    || tablePoint.x >= table.getWidth()
                    || tablePoint.y >= table.getHeight()) {
                hoverTimer.stop();
                hoverViewRow = -1;
                return;
            }
            int viewRow = table.rowAtPoint(tablePoint);
            int viewCol = table.columnAtPoint(tablePoint);
            if (viewRow < 0 || viewCol < 0) {
                hoverTimer.stop();
                hoverViewRow = -1;
                return;
            }
            if (table.convertColumnIndexToModel(viewCol) != modelColumnIndex) {
                hoverTimer.stop();
                hoverViewRow = -1;
                return;
            }
            boolean preferFrom = preferFromHalf(table, viewRow, viewCol, tablePoint);
            if (viewRow != hoverViewRow || preferFrom != hoverPreferFrom) {
                hoverViewRow = viewRow;
                hoverPreferFrom = preferFrom;
                hoverTimer.restart();
            }
        }

        private void copyIfStillHovering() {
            if (hoverViewRow < 0) {
                return;
            }
            int modelRow = table.convertRowIndexToModel(hoverViewRow);
            if (modelRow < 0) {
                return;
            }
            copyIfValid(table, textForRow.apply(modelRow, hoverPreferFrom));
        }
    }
}
