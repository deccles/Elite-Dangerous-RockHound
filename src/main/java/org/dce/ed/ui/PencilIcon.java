package org.dce.ed.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

import javax.swing.Icon;

/** Small pencil glyph for inline edit affordances. */
public final class PencilIcon implements Icon {

    public static final PencilIcon DEFAULT = new PencilIcon(16, EdoUi.Internal.MAIN_TEXT_ALPHA_220);

    private final int size;
    private final Color color;

    public PencilIcon(int size, Color color) {
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
            Color ink = c != null && !c.isEnabled() ? EdoUi.Internal.MAIN_TEXT_ALPHA_140 : color;
            g2.setColor(ink);

            AffineTransform prior = g2.getTransform();
            double cx = x + size / 2.0;
            double cy = y + size / 2.0;
            g2.translate(cx, cy);
            // Mild tilt — reads as a writing pencil, not a stick/wand.
            g2.rotate(Math.toRadians(-38));
            g2.translate(-cx, -cy);

            double pad = size * 0.10;
            double left = x + pad;
            double top = y + pad;
            double w = size - 2 * pad;
            double h = size - 2 * pad;

            // Proportions for a short pencil — slim enough to read as a writing tool.
            double shaftW = w * 0.32;
            double midX = left + w / 2.0;
            double shaftLeft = midX - shaftW / 2.0;
            double shaftRight = midX + shaftW / 2.0;

            double eraserH = h * 0.18;
            double ferruleH = h * 0.08;
            double tipH = h * 0.26;
            double bodyTop = top + eraserH;
            double tipTop = top + h - tipH;

            // Full silhouette: eraser → body → tip.
            Path2D shape = new Path2D.Double();
            // Eraser top (slightly rounded via short flat)
            shape.moveTo(shaftLeft + shaftW * 0.08, top + eraserH * 0.25);
            shape.quadTo(midX, top - pad * 0.15, shaftRight - shaftW * 0.08, top + eraserH * 0.25);
            shape.lineTo(shaftRight, bodyTop);
            shape.lineTo(shaftRight, tipTop);
            // Tip
            shape.lineTo(midX, top + h);
            shape.lineTo(shaftLeft, tipTop);
            shape.lineTo(shaftLeft, bodyTop);
            shape.closePath();

            float lw = Math.max(1.2f, size / 12.5f);
            g2.setStroke(new BasicStroke(lw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(shape);

            // Ferrule band between eraser and shaft.
            double ferruleY = bodyTop + ferruleH * 0.35;
            g2.draw(new Line2D.Double(shaftLeft, ferruleY, shaftRight, ferruleY));
            g2.draw(new Line2D.Double(shaftLeft, ferruleY + ferruleH * 0.85, shaftRight, ferruleY + ferruleH * 0.85));

            // Tip wood cut — two short lines into the point.
            double cutY = tipTop + tipH * 0.28;
            g2.draw(new Line2D.Double(shaftLeft + shaftW * 0.12, cutY, shaftRight - shaftW * 0.12, cutY));
            g2.draw(new Line2D.Double(midX, tipTop + tipH * 0.08, midX, tipTop + tipH * 0.55));

            g2.setTransform(prior);
        } finally {
            g2.dispose();
        }
    }
}
