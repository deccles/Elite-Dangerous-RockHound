package org.dce.ed.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

import javax.swing.Icon;

import org.dce.ed.OverlayPreferences;

/** Up/down arrow for goal priority cells; size tracks the UI font. */
public final class PriorityArrowIcon implements Icon {

    public static final PriorityArrowIcon HIGH = new PriorityArrowIcon(EdoUi.User.SUCCESS, true);
    public static final PriorityArrowIcon LOW = new PriorityArrowIcon(EdoUi.User.ERROR, false);

    private final Color color;
    private final boolean up;

    public PriorityArrowIcon(Color color, boolean up) {
        this.color = color != null ? color : EdoUi.User.MAIN_TEXT;
        this.up = up;
    }

    /** Prefer font-scaled icons; kept for callers that still pass an explicit size. */
    public PriorityArrowIcon(int size, Color color, boolean up) {
        this(color, up);
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
            Color ink = c != null && !c.isEnabled() ? EdoUi.Internal.MAIN_TEXT_ALPHA_140 : color;
            g2.setColor(ink);

            int size = iconSize();
            // Tight inset so the glyph fills most of the icon box.
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
        } finally {
            g2.dispose();
        }
    }

    /** Matches body text scale, slightly proud so arrows stay readable. */
    private static int iconSize() {
        return Math.max(14, OverlayPreferences.getUiFontSize() + 1);
    }
}
