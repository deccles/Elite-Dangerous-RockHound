package org.dce.ed.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.RenderingHints;

import javax.swing.Icon;

import org.dce.ed.OverlayPreferences;

/**
 * Route-style status badge: filled colored circle with a bold SansSerif symbol (check / X / !).
 * Uses logical SansSerif so dingbat glyphs are available even when the UI font lacks them.
 */
public final class StatusCircleIcon implements Icon {

    public static final String CHECK = "\u2713";
    public static final String CROSS = "X";

    private final Color circleColor;
    private final String symbol;
    private final boolean filled;
    private final float glowAlpha;

    public StatusCircleIcon(Color circleColor, String symbol) {
        this(circleColor, symbol, true, 0.0f);
    }

    public StatusCircleIcon(Color circleColor, String symbol, boolean filled, float glowAlpha) {
        this.circleColor = circleColor != null ? circleColor : EdoUi.User.MAIN_TEXT;
        this.symbol = symbol != null ? symbol : "";
        this.filled = filled;
        this.glowAlpha = Math.max(0.0f, Math.min(1.0f, glowAlpha));
    }

    public static StatusCircleIcon check(Color circleColor) {
        return new StatusCircleIcon(circleColor, CHECK);
    }

    public static StatusCircleIcon cross(Color circleColor) {
        return new StatusCircleIcon(circleColor, CROSS);
    }

    @Override
    public int getIconWidth() {
        return diameter();
    }

    @Override
    public int getIconHeight() {
        return diameter();
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int d = diameter() - 1;
            g2.setColor(circleColor);
            if (filled) {
                g2.fillOval(x, y, d, d);
            }
            g2.setColor(filled ? Color.BLACK : circleColor);
            g2.drawOval(x, y, d, d);
            if (glowAlpha > 0.0f) {
                g2.setComposite(AlphaComposite.SrcOver.derive(glowAlpha));
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2.4f));
                g2.drawOval(x + 1, y + 1, Math.max(0, d - 2), Math.max(0, d - 2));
                g2.setComposite(AlphaComposite.SrcOver);
            }
            if (!symbol.isEmpty()) {
                g2.setColor(Color.BLACK);
                Font font = iconFont();
                g2.setFont(font);
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int textWidth = fm.stringWidth(symbol);
                int textAscent = fm.getAscent();
                int tx = x + (diameter() - textWidth) / 2;
                int ty = y + (diameter() + textAscent) / 2 - 2;
                g2.drawString(symbol, tx, ty);
            }
        } finally {
            g2.dispose();
        }
    }

    private static int diameter() {
        return Math.max(14, OverlayPreferences.getUiFontSize());
    }

    private static Font iconFont() {
        Font uiFont = OverlayPreferences.getUiFont();
        int size = uiFont != null ? Math.max(10, uiFont.getSize() - 1) : 12;
        return new Font(Font.SANS_SERIF, Font.BOLD, size);
    }
}
