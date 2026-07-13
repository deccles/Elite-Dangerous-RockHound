package org.dce.ed.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;

/** Small pencil glyph for inline edit affordances. */
public final class PencilIcon implements Icon {

    public static final PencilIcon DEFAULT = new PencilIcon(14, EdoUi.Internal.MAIN_TEXT_ALPHA_220);

    private final int size;
    private final Color color;

    public PencilIcon(int size, Color color) {
        this.size = Math.max(12, size);
        this.color = color != null ? color : EdoUi.User.MAIN_TEXT;
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
            Color stroke = c != null && !c.isEnabled() ? EdoUi.Internal.MAIN_TEXT_ALPHA_140 : color;
            g2.setColor(stroke);
            g2.setStroke(new BasicStroke(Math.max(1.2f, size / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int pad = Math.max(2, size / 6);
            int x1 = x + pad;
            int y1 = y + size - pad;
            int x2 = x + size - pad;
            int y2 = y + pad;
            g2.drawLine(x1, y1, x2, y2);
            g2.drawLine(x2 - size / 5, y2, x2, y2);
            g2.drawLine(x2, y2, x2, y2 + size / 5);
        } finally {
            g2.dispose();
        }
    }
}
