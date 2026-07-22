package org.dce.ed.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.plaf.ViewportUI;
import javax.swing.plaf.basic.BasicViewportUI;

import org.dce.ed.MouseInteractionMode;
import org.dce.ed.OverlayPreferences;

/**
 * ViewportUI that does not paint an opaque LAF background. Fills or clears the viewport first so
 * unpainted regions (e.g. below short tables) do not read as a white frame on transparent overlays.
 */
public final class TransparentViewportUI extends BasicViewportUI {

    public static ViewportUI createUI(JComponent c) {
        return new TransparentViewportUI();
    }

    @Override
    public void update(Graphics g, JComponent c) {
        paint(g, c);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        paintViewportBackground(g, c);
        super.paint(g, c);
    }

    static void paintViewportBackground(Graphics g, JComponent c) {
        int w = c.getWidth();
        int h = c.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            if (OverlayPreferences.overlayChromeRequestsTransparency(c)) {
                fillSeeThroughChrome(g2, 0, 0, w, h, c);
            } else if (c.isOpaque()) {
                g2.setComposite(AlphaComposite.SrcOver);
                g2.setColor(c.getBackground());
                g2.fillRect(0, 0, w, h);
            }
        } finally {
            g2.dispose();
        }
    }

    /**
     * Paints theme background at the active transparency %, or CLEAR when fully transparent.
     * Shared by viewport / header painters so pass-through regions respect Preferences.
     */
    public static void fillSeeThroughChrome(Graphics2D g2, int x, int y, int width, int height) {
        fillSeeThroughChrome(g2, x, y, width, height, null);
    }

    public static void fillSeeThroughChrome(Graphics2D g2, int x, int y, int width, int height,
            JComponent under) {
        if (width <= 0 || height <= 0) {
            return;
        }
        Color fill = OverlayPreferences.getActiveOverlayChromeBackground(under);
        if (fill == null || fill.getAlpha() <= 0) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
            g2.fillRect(x, y, width, height);
        } else {
            g2.setComposite(AlphaComposite.Src);
            g2.setColor(fill);
            g2.fillRect(x, y, width, height);
        }
    }

    /**
     * Selective (hybrid) mode: tables that fill the viewport paint chrome below the last row.
     * Punch that strip fully transparent so the game shows through. CLEAR is only safe on the
     * undecorated pass-through host.
     */
    public static void clearBelowTableRowsInSelectiveMode(Graphics g, JTable table) {
        if (g == null || table == null || !isSelectivePassThroughContext(table)) {
            return;
        }
        int rowCount = table.getRowCount();
        int rowsBottom = 0;
        if (rowCount > 0) {
            Rectangle last = table.getCellRect(rowCount - 1, 0, true);
            rowsBottom = last.y + last.height;
        }
        int h = table.getHeight();
        if (rowsBottom >= h) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
            g2.fillRect(0, rowsBottom, table.getWidth(), h - rowsBottom);
        } finally {
            g2.dispose();
        }
    }

    public static boolean isSelectivePassThroughContext(JComponent under) {
        javax.swing.JRootPane rp = javax.swing.SwingUtilities.getRootPane(under);
        if (rp != null) {
            Object mode = rp.getClientProperty(OverlayPreferences.WINDOW_MOUSE_MODE_KEY);
            if (mode instanceof MouseInteractionMode m) {
                return m == MouseInteractionMode.SELECTIVE
                        && OverlayPreferences.overlayChromeRequestsTransparency(under);
            }
        }
        return OverlayPreferences.getOverlayMouseInteractionMode() == MouseInteractionMode.SELECTIVE
                && OverlayPreferences.isPassThroughWindowActive();
    }

    /**
     * Selective mode: punch {@code panel} fully transparent except descendant {@link JButton}s
     * (rounded hit plates keep their fill; gaps/padding lose the black halo).
     *
     * @param host coordinate space for {@code g} (usually the tab panel)
     */
    public static void clearPanelChromeExceptButtons(Graphics g, JComponent host, JComponent panel) {
        if (g == null || host == null || panel == null || !panel.isShowing()
                || !isSelectivePassThroughContext(host)) {
            return;
        }
        Rectangle area = javax.swing.SwingUtilities.convertRectangle(
                panel.getParent() != null ? panel.getParent() : panel,
                panel.getBounds(),
                host);
        if (area.width <= 0 || area.height <= 0) {
            return;
        }
        java.awt.geom.Area clear = new java.awt.geom.Area(area);
        collectButtonKeepAreas(panel, host, clear);
        if (clear.isEmpty()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
            g2.fill(clear);
        } finally {
            g2.dispose();
        }
    }

    private static void collectButtonKeepAreas(Component root, JComponent host, java.awt.geom.Area clear) {
        if (root == null || !root.isShowing()) {
            return;
        }
        if (root instanceof javax.swing.JButton) {
            Rectangle keep = javax.swing.SwingUtilities.convertRectangle(
                    root.getParent() != null ? root.getParent() : root,
                    root.getBounds(),
                    host);
            clear.subtract(new java.awt.geom.Area(keep));
            return;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectButtonKeepAreas(child, host, clear);
            }
        }
    }
}
