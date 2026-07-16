package org.dce.ed.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

import javax.swing.Icon;

import org.dce.ed.OverlayPreferences;

/** Up/down/mid markers for goal priority cells; size tracks the UI font. */
public final class PriorityArrowIcon implements Icon {

    public static final PriorityArrowIcon HIGH = new PriorityArrowIcon(Kind.HIGH, EdoUi.User.SUCCESS);
    public static final PriorityArrowIcon MEDIUM = new PriorityArrowIcon(Kind.MEDIUM, EdoUi.User.MAIN_TEXT);
    public static final PriorityArrowIcon LOW = new PriorityArrowIcon(Kind.LOW, EdoUi.User.ERROR);

    private enum Kind { HIGH, MEDIUM, LOW }

    private final Kind kind;
    private final Color color;

    public PriorityArrowIcon(Color color, boolean up) {
        this(up ? Kind.HIGH : Kind.LOW, color != null ? color : EdoUi.User.MAIN_TEXT);
    }

    private PriorityArrowIcon(Kind kind, Color color) {
        this.kind = kind != null ? kind : Kind.MEDIUM;
        this.color = color != null ? color : EdoUi.User.MAIN_TEXT;
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
            double pad = size * 0.06;
            double left = x + pad;
            double right = x + size - pad;
            double midX = x + size / 2.0;
            double top = y + pad;
            double bottom = y + size - pad;

            if (kind == Kind.MEDIUM) {
                // Two horizontal bars — distinct from the enabled checkbox.
                double barH = Math.max(1.5, size * 0.14);
                double gap = size * 0.16;
                double midY = y + size / 2.0;
                double barLeft = x + size * 0.18;
                double barRight = x + size * 0.82;
                g2.fill(new java.awt.geom.Rectangle2D.Double(
                        barLeft, midY - gap / 2.0 - barH, barRight - barLeft, barH));
                g2.fill(new java.awt.geom.Rectangle2D.Double(
                        barLeft, midY + gap / 2.0, barRight - barLeft, barH));
                return;
            }

            double shaftHalf = size * 0.16;
            double headDepth = size * 0.38;
            Path2D arrow = new Path2D.Double();
            if (kind == Kind.HIGH) {
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
