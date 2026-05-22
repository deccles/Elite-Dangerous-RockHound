package org.dce.ed.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.BasicStroke;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;

import org.dce.ed.OverlayPreferences;

/**
 * Outline text button styling (same look as Route tab "Copy next destination").
 */
public final class OverlayOutlineButtonStyle {

    private static final int DEFAULT_ARC = 12;

    private OverlayOutlineButtonStyle() {
    }

    /** Full-size primary action button (e.g. copy strip). */
    public static void applyPrimary(JButton b, Font uiFont) {
        apply(b, uiFont, true, new Insets(8, 18, 8, 18), EdoUi.User.MAIN_TEXT);
    }

    /** Compact chip (filter tabs, dismiss). */
    public static void applyChip(JButton b, Font uiFont, boolean selected) {
        Color border = selected ? EdoUi.User.MAIN_TEXT : EdoUi.Internal.MAIN_TEXT_ALPHA_220;
        apply(b, uiFont, selected, new Insets(4, 10, 4, 10), border);
    }

    private static void apply(JButton b, Font uiFont, boolean bold, Insets padding, Color borderColor) {
        if (b == null || uiFont == null) {
            return;
        }
        int size = OverlayPreferences.getUiFontSize();
        b.setFocusable(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(uiFont.deriveFont(bold ? Font.BOLD : Font.PLAIN, size));
        b.setForeground(EdoUi.User.MAIN_TEXT);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setBorderPainted(true);
        b.setBackground(EdoUi.Internal.TRANSPARENT);
        b.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(borderColor, 2, DEFAULT_ARC),
                new EmptyBorder(padding.top, padding.left, padding.bottom, padding.right)));
    }

    public static final class RoundedLineBorder extends AbstractBorder {
        private static final long serialVersionUID = 1L;
        private final Color color;
        private final int thickness;
        private final int arc;

        public RoundedLineBorder(Color color, int thickness, int arc) {
            this.color = color;
            this.thickness = Math.max(1, thickness);
            this.arc = Math.max(2, arc);
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(thickness));
                int inset = thickness / 2;
                g2.drawRoundRect(x + inset, y + inset, width - thickness - 1, height - thickness - 1, arc, arc);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness, thickness, thickness, thickness);
        }
    }
}
