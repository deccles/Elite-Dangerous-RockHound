package org.dce.ed.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;

import javax.swing.AbstractButton;
import javax.swing.Icon;

/** Compact vector loop glyph that follows a toggle button's selected state. */
public final class CircularArrowIcon implements Icon {
    private final int size;

    public CircularArrowIcon(int size) {
        this.size = Math.max(10, size);
    }

    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean selected = c instanceof AbstractButton b && b.isSelected();
            Color ink = selected ? EdoUi.User.SECONDARY_HIGHLIGHT : EdoUi.User.MAIN_TEXT;
            g2.setColor(ink);
            g2.setStroke(new BasicStroke(2.15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double inset = 2.4;
            double d = size - inset * 2.0;
            // Nearly complete circle with a deliberate gap at upper-right for a bold tangent arrowhead.
            g2.draw(new Arc2D.Double(x + inset, y + inset, d, d, 52, 292, Arc2D.OPEN));
            Path2D head = new Path2D.Double();
            head.moveTo(x + size - 1.1, y + size * 0.48);
            head.lineTo(x + size - 7.0, y + size * 0.31);
            head.lineTo(x + size - 4.3, y + size * 0.72);
            head.closePath();
            g2.fill(head);
        } finally {
            g2.dispose();
        }
    }
}
