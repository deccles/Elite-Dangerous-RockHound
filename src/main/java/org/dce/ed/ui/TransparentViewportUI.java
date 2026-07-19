package org.dce.ed.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
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
            if (OverlayPreferences.overlayChromeRequestsTransparency()) {
                fillSeeThroughChrome(g2, 0, 0, w, h);
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
        if (width <= 0 || height <= 0) {
            return;
        }
        Color fill = OverlayPreferences.getActiveOverlayChromeBackground();
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
        if (g == null || table == null
                || OverlayPreferences.getOverlayMouseInteractionMode() != MouseInteractionMode.SELECTIVE
                || !OverlayPreferences.isPassThroughWindowActive()) {
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
}
