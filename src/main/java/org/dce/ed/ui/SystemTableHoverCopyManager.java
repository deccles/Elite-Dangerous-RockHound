package org.dce.ed.ui;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.Window;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import java.util.function.BooleanSupplier;

/**
 * Safely implements "hover for N seconds to copy system name to clipboard"
 * without any global hooks or window mouse events.
 *
 * It polls MouseInfo.getPointerInfo() on a Swing Timer and checks
 * whether the pointer is over the given JTable.
 *
 * When the hover triggers, it:
 *  - Copies the system name to the clipboard
 *  - Shows a small "Copied: <system>" banner centered on the hovered row
 *
 * Hover-to-copy is only active when passThroughEnabledSupplier returns true
 * (overlay in mouse pass-through mode). When false, only double-click copies.
 */
public class SystemTableHoverCopyManager {

    private static final int POLL_INTERVAL_MS = 100;   // how often we poll mouse position
    private static final int HOVER_DELAY_MS   = 1500;  // how long to hover before copying (2 seconds)
    private static final int TOAST_DURATION_MS = 2000; // how long to show "Copied" banner

    private final JTable table;
    private final int systemNameModelColumnIndex;
    private final BooleanSupplier passThroughEnabledSupplier;

    private final Timer pollTimer;
    private final Timer hoverTimer;

    private int hoverViewRow = -1;

    /** Uses hover copy only when pass-through is enabled; pass null to always allow hover copy. */
    public SystemTableHoverCopyManager(JTable table, int systemNameModelColumnIndex, BooleanSupplier passThroughEnabledSupplier) {
        this.table = table;
        this.systemNameModelColumnIndex = systemNameModelColumnIndex;
        this.passThroughEnabledSupplier = passThroughEnabledSupplier;

        // Timer to check mouse position periodically
        this.pollTimer = new Timer(POLL_INTERVAL_MS, e -> pollMousePosition());

        // Timer that fires once after HOVER_DELAY_MS over the same row
        this.hoverTimer = new Timer(HOVER_DELAY_MS, e -> copySystemNameIfStillHovering());
        this.hoverTimer.setRepeats(false);
    }

    /** Backward compatibility: hover copy always active (no pass-through check). */
    public SystemTableHoverCopyManager(JTable table, int systemNameModelColumnIndex) {
        this(table, systemNameModelColumnIndex, null);
    }

    public void start() {
        pollTimer.start();
    }

    public void stop() {
        pollTimer.stop();
        hoverTimer.stop();
    }

    private void pollMousePosition() {
        if (!table.isShowing()) {
            hoverTimer.stop();
            hoverViewRow = -1;
            table.setToolTipText(null);
            return;
        }

        // Hover copy only when overlay is in mouse pass-through mode
        if (passThroughEnabledSupplier != null && !passThroughEnabledSupplier.getAsBoolean()) {
            hoverTimer.stop();
            hoverViewRow = -1;
            table.setToolTipText(null);
            return;
        }

        java.awt.PointerInfo info = MouseInfo.getPointerInfo();
        if (info == null) {
            // Can happen briefly if the mouse is being switched between devices, etc.
            hoverTimer.stop();
            hoverViewRow = -1;
            table.setToolTipText(null);
            return;
        }

        Point screenPoint = info.getLocation();
        Point tablePoint = new Point(screenPoint);
        SwingUtilities.convertPointFromScreen(tablePoint, table);

        if (tablePoint.x < 0 || tablePoint.y < 0
                || tablePoint.x >= table.getWidth()
                || tablePoint.y >= table.getHeight()) {
            // Mouse is not over the table
            hoverTimer.stop();
            hoverViewRow = -1;
            table.setToolTipText(null);
            return;
        }

        int viewRow = table.rowAtPoint(tablePoint);
        if (viewRow < 0) {
            hoverTimer.stop();
            hoverViewRow = -1;
            table.setToolTipText(null);
            return;
        }

        // If we moved to a different row, restart the hover timer
        if (viewRow != hoverViewRow) {
            hoverViewRow = viewRow;
            hoverTimer.restart();
            table.setToolTipText("Hold to copy system name…");
        }
    }

    private void copySystemNameIfStillHovering() {
        if (hoverViewRow < 0) {
            return;
        }
        copySystemNameAtViewRow(hoverViewRow);
    }

    /**
     * Copy the system name at the given view row to the clipboard and show the "Copied" toast.
     * Used for double-click-to-copy when the overlay is not in mouse-pass-through mode.
     */
    public void copySystemNameAtViewRow(int viewRow) {
        if (!table.isShowing()) {
            return;
        }

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
        Toolkit.getDefaultToolkit()
               .getSystemClipboard()
               .setContents(selection, null);

        // Visual feedback centered on the row
        showCopiedToast(systemName, viewRow);
    }

    /**
     * Shows a small semi-transparent "Copied: <system>" banner
     * centered on the specified view row in the table.
     */
    private void showCopiedToast(String systemName, int viewRow) {
        Window window = SwingUtilities.getWindowAncestor(table);
        if (!(window instanceof JFrame)) {
            return;
        }

        JFrame frame = (JFrame) window;
        JRootPane rootPane = frame.getRootPane();
        JLayeredPane layeredPane = rootPane.getLayeredPane();

        JLabel toast = new JLabel("Copied: " + systemName, SwingConstants.CENTER);
        toast.setOpaque(true);
        toast.setBackground(EdoUi.Internal.BLACK_ALPHA_180);
        toast.setForeground(Color.WHITE);
        toast.setBorder(new EmptyBorder(4, 8, 4, 8));

        toast.setSize(toast.getPreferredSize());
        Dimension size = toast.getSize();

        // Compute the center of the hovered row in the table
        Rectangle rowRect = table.getCellRect(viewRow, 0, true);
        int rowCenterYTable = rowRect.y + rowRect.height / 2;
        int rowCenterXTable = table.getWidth() / 2;

        // Convert that point to layeredPane coordinates
        Point tableCenterPoint = new Point(rowCenterXTable, rowCenterYTable);
        SwingUtilities.convertPointToScreen(tableCenterPoint, table);
        Point layeredCenterPoint = new Point(tableCenterPoint);
        SwingUtilities.convertPointFromScreen(layeredCenterPoint, layeredPane);

        int x = layeredCenterPoint.x - size.width / 2;
        int y = layeredCenterPoint.y - size.height / 2;

        toast.setLocation(x, y);

        layeredPane.add(toast, JLayeredPane.POPUP_LAYER);
        layeredPane.revalidate();
        layeredPane.repaint();

        Timer removeTimer = new Timer(TOAST_DURATION_MS, e -> {
            layeredPane.remove(toast);
            layeredPane.revalidate();
            layeredPane.repaint();
        });
        removeTimer.setRepeats(false);
        removeTimer.start();
    }
}
