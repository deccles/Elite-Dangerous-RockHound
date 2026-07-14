package org.dce.ed.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.Icon;
import javax.swing.JButton;

/** Circle with an {@code i} for info / details buttons. */
public final class InfoCircleIcon implements Icon {

    public static final InfoCircleIcon DEFAULT = new InfoCircleIcon(16, EdoUi.Internal.MAIN_TEXT_ALPHA_220);

    private final int size;
    private final Color color;

    public InfoCircleIcon(int size, Color color) {
        this.size = Math.max(12, size);
        this.color = color != null ? color : EdoUi.User.MAIN_TEXT;
    }

    /** Icon-only chip button; keep a tooltip for discoverability. */
    public static void applyTo(JButton button) {
        if (button == null) {
            return;
        }
        button.setIcon(DEFAULT);
        button.setText("");
        button.setMargin(new Insets(2, 4, 2, 4));
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color stroke = c != null && !c.isEnabled() ? EdoUi.Internal.MAIN_TEXT_ALPHA_140 : color;
            g2.setColor(stroke);
            float sw = Math.max(1.2f, size / 10f);
            g2.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int d = size - 1;
            g2.drawOval(x, y, d, d);
            Font font = c != null && c.getFont() != null
                    ? c.getFont().deriveFont(Font.BOLD, Math.max(10f, size * 0.7f))
                    : new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, (int) (size * 0.7f)));
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            String symbol = "i";
            int tx = x + (size - fm.stringWidth(symbol)) / 2;
            int ty = y + (size + fm.getAscent()) / 2 - Math.max(1, size / 10);
            g2.drawString(symbol, tx, ty);
        } finally {
            g2.dispose();
        }
    }
}
