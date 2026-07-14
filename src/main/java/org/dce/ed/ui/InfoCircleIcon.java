package org.dce.ed.ui;

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

/** Times New Roman lowercase {@code i} for info / details buttons (no circle). */
public final class InfoCircleIcon implements Icon {

    private static final Font LETTER_FONT = new Font("Times New Roman", Font.PLAIN, 18);

    public static final InfoCircleIcon DEFAULT = new InfoCircleIcon(18, EdoUi.User.MAIN_TEXT);

    private final int size;
    private final Color color;

    public InfoCircleIcon(int size, Color color) {
        this.size = Math.max(14, size);
        this.color = color != null ? color : EdoUi.User.MAIN_TEXT;
    }

    /** Icon-only chip button; keep a tooltip for discoverability. */
    public static void applyTo(JButton button) {
        if (button == null) {
            return;
        }
        button.setIcon(DEFAULT);
        button.setText("");
        button.setMargin(new Insets(2, 5, 2, 5));
        if (button.getToolTipText() == null || button.getToolTipText().isBlank()) {
            button.setToolTipText("Info");
        }
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
            Color ink = c != null && !c.isEnabled() ? EdoUi.Internal.MAIN_TEXT_ALPHA_140 : color;
            g2.setColor(ink);

            float pointSize = Math.max(14f, size * 1.15f);
            Font font = LETTER_FONT.deriveFont(Font.PLAIN, pointSize);
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            String symbol = "i";
            int tx = x + (size - fm.stringWidth(symbol)) / 2;
            int ty = y + (size + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(symbol, tx, ty);
        } finally {
            g2.dispose();
        }
    }
}
