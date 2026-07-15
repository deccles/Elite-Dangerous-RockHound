package org.dce.ed.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;

/** Small trash-can glyph for inline delete affordances. */
public final class TrashIcon implements Icon {

    public static final TrashIcon DEFAULT = new TrashIcon(17, EdoUi.Internal.MAIN_TEXT_ALPHA_220);

    private final int size;
    private final Color color;

    public TrashIcon(int size, Color color) {
        this.size = Math.max(14, size);
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
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            Color stroke = c != null && !c.isEnabled() ? EdoUi.Internal.MAIN_TEXT_ALPHA_140 : color;
            g2.setColor(stroke);
            float lw = Math.max(1.35f, size / 11f);
            g2.setStroke(new BasicStroke(lw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            double pad = Math.max(1.2, size * 0.12);
            double left = x + pad;
            double top = y + pad;
            double w = size - 2 * pad;
            double h = size - 2 * pad;

            double handleW = w * 0.30;
            double handleH = h * 0.11;
            double handleLeft = left + (w - handleW) / 2.0;
            double lidY = top + handleH + h * 0.06;
            double bodyTop = lidY + h * 0.10;
            double bodyH = h - (bodyTop - top) - h * 0.04;
            double bodyW = w * 0.78;
            double bodyLeft = left + (w - bodyW) / 2.0;

            // Lid handle
            g2.draw(new RoundRectangle2D.Double(handleLeft, top, handleW, handleH, 1.8, 1.8));
            // Lid
            g2.draw(new Line2D.Double(left + w * 0.05, lidY, left + w * 0.95, lidY));
            // Can body (slightly trapezoid-looking via rounded rect)
            g2.draw(new RoundRectangle2D.Double(bodyLeft, bodyTop, bodyW, bodyH, 2.2, 2.2));
            // Ribs
            double ribPad = bodyW * 0.24;
            double ribTop = bodyTop + bodyH * 0.20;
            double ribBot = bodyTop + bodyH * 0.84;
            g2.draw(new Line2D.Double(bodyLeft + ribPad, ribTop, bodyLeft + ribPad, ribBot));
            g2.draw(new Line2D.Double(bodyLeft + bodyW / 2.0, ribTop, bodyLeft + bodyW / 2.0, ribBot));
            g2.draw(new Line2D.Double(bodyLeft + bodyW - ribPad, ribTop, bodyLeft + bodyW - ribPad, ribBot));
        } finally {
            g2.dispose();
        }
    }
}
