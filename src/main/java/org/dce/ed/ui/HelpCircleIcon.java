package org.dce.ed.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.SwingConstants;

/** Green circle with a {@code ?} for help buttons. */
public final class HelpCircleIcon implements Icon {

    public static final HelpCircleIcon DEFAULT = new HelpCircleIcon(16, EdoUi.User.SUCCESS);

    private final int size;
    private final Color circleColor;

    public HelpCircleIcon(int size, Color circleColor) {
        this.size = Math.max(12, size);
        this.circleColor = circleColor != null ? circleColor : EdoUi.User.SUCCESS;
    }

    public static void applyTo(JButton button) {
        if (button == null) {
            return;
        }
        button.setIcon(DEFAULT);
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setIconTextGap(6);
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
            int d = size - 1;
            g2.setColor(circleColor);
            g2.fillOval(x, y, d, d);
            g2.setColor(circleColor.darker());
            g2.drawOval(x, y, d, d);
            Font font = c != null && c.getFont() != null
                    ? c.getFont().deriveFont(Font.BOLD, Math.max(10f, size * 0.72f))
                    : new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, (int) (size * 0.72f)));
            g2.setFont(font);
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            String symbol = "?";
            int tx = x + (size - fm.stringWidth(symbol)) / 2;
            int ty = y + (size + fm.getAscent()) / 2 - 1;
            g2.drawString(symbol, tx, ty);
        } finally {
            g2.dispose();
        }
    }
}
