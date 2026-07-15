package org.dce.ed.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

import javax.swing.Icon;

import org.dce.ed.OverlayPreferences;

/**
 * Priority column header: green up-arrow (upper-left) overlapping a red down-arrow (lower-right).
 * Green is painted last so it sits on top in the overlap.
 */
public final class PriorityHeaderIcon implements Icon {

    public static final PriorityHeaderIcon INSTANCE = new PriorityHeaderIcon();

    private PriorityHeaderIcon() {
    }

    @Override
    public int getIconWidth() {
        return iconSize();
    }

    @Override
    public int getIconHeight() {
        return iconSize();
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = iconSize();
            // Each mini-arrow is ~70% of the cell; offsets create UL / LR placement with overlap.
            double arrowSize = size * 0.72;
            double upOx = size * 0.02;
            double upOy = size * 0.00;
            double downOx = size * 0.26;
            double downOy = size * 0.26;

            paintArrow(g2, x + downOx, y + downOy, arrowSize, EdoUi.User.ERROR, false);
            paintArrow(g2, x + upOx, y + upOy, arrowSize, EdoUi.User.SUCCESS, true);
        } finally {
            g2.dispose();
        }
    }

    private static void paintArrow(Graphics2D g2, double x, double y, double size, Color color, boolean up) {
        g2.setColor(color);
        double pad = size * 0.06;
        double left = x + pad;
        double right = x + size - pad;
        double midX = x + size / 2.0;
        double top = y + pad;
        double bottom = y + size - pad;
        double shaftHalf = size * 0.16;
        double headDepth = size * 0.38;

        Path2D arrow = new Path2D.Double();
        if (up) {
            double headBaseY = top + headDepth;
            arrow.moveTo(midX, top);
            arrow.lineTo(right, headBaseY);
            arrow.lineTo(midX + shaftHalf, headBaseY);
            arrow.lineTo(midX + shaftHalf, bottom);
            arrow.lineTo(midX - shaftHalf, bottom);
            arrow.lineTo(midX - shaftHalf, headBaseY);
            arrow.lineTo(left, headBaseY);
            arrow.closePath();
        } else {
            double headBaseY = bottom - headDepth;
            arrow.moveTo(midX, bottom);
            arrow.lineTo(right, headBaseY);
            arrow.lineTo(midX + shaftHalf, headBaseY);
            arrow.lineTo(midX + shaftHalf, top);
            arrow.lineTo(midX - shaftHalf, top);
            arrow.lineTo(midX - shaftHalf, headBaseY);
            arrow.lineTo(left, headBaseY);
            arrow.closePath();
        }
        g2.fill(arrow);
    }

    private static int iconSize() {
        return Math.max(16, OverlayPreferences.getUiFontSize() + 4);
    }
}
