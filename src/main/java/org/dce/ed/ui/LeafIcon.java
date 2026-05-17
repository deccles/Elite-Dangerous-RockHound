package org.dce.ed.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;

import javax.swing.Icon;

public final class LeafIcon implements Icon {
    private final int w;
    private final int h;

    public LeafIcon(int w, int h) {
        this.w = w;
        this.h = h;
    }

    @Override
    public int getIconWidth() {
        return w;
    }

    @Override
    public int getIconHeight() {
        return h;
    }

    private final Color outlineColor = EdoUi.Internal.BLACK_ALPHA_180;
    private final Color stemColor = new Color(113, 76, 44);
    private final Color accentLineColor = new Color(194, 156, 112, 220);

    /** Leaf body / veins track {@link EdoUi.User#PRIMARY_HIGHLIGHT} (prefs). */
    private static Color leafFillFromPrimary(Color primary) {
        float[] hsb = Color.RGBtoHSB(primary.getRed(), primary.getGreen(), primary.getBlue(), null);
        float s = Math.min(1f, hsb[1] * 0.92f + 0.08f);
        float b = Math.min(1f, hsb[2] * 0.88f);
        return Color.getHSBColor(hsb[0], s, b);
    }

    private static Color leafHighlightFromPrimary(Color primary) {
        float[] hsb = Color.RGBtoHSB(primary.getRed(), primary.getGreen(), primary.getBlue(), null);
        float s = Math.min(1f, hsb[1] * 0.55f + 0.20f);
        float br = Math.min(1f, hsb[2] * 1.12f);
        return Color.getHSBColor(hsb[0], s, br);
    }

    private static Color leafVeinFromPrimary(Color primary) {
        float[] hsb = Color.RGBtoHSB(primary.getRed(), primary.getGreen(), primary.getBlue(), null);
        float s = Math.min(1f, hsb[1] * 1.05f);
        float br = Math.min(1f, hsb[2] * 0.58f);
        return EdoUi.withAlpha(Color.getHSBColor(hsb[0], s, br), 220);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color primary = EdoUi.User.PRIMARY_HIGHLIGHT;
        Color fillColor = leafFillFromPrimary(primary);
        Color highlightColor = leafHighlightFromPrimary(primary);
        Color veinColor = leafVeinFromPrimary(primary);

        // Inset drawing area by 1px so outline/strokes do not clip at icon bounds.
        // (ix, iy) is the top-left anchor of the drawable area.
        double ix = x + 1.0;
        double iy = y + 1.0;
        // Keep a minimum drawable size so the icon still reads if the configured icon size shrinks.
        double iw = Math.max(8.0, getIconWidth() - 2.0);
        double ih = Math.max(8.0, getIconHeight() - 2.0);

        Path2D leaf = new Path2D.Double();
        // Leaf outer silhouette:
        // - start at left base shoulder
        // - curve up left edge
        // - run into pointed tip
        // - curve down right edge
        // - return to base
        leaf.moveTo(ix + iw * 0.16, iy + ih * 0.62); // start: left base shoulder
        leaf.curveTo(
            ix + iw * 0.26, iy + ih * 0.30, // left-edge control 1
            ix + iw * 0.54, iy + ih * 0.10, // left-edge control 2
            ix + iw * 0.88, iy + ih * 0.25  // near-tip left endpoint (lowered again)
        );
        leaf.lineTo(ix + iw * 0.96, iy + ih * 0.30); // tip point
        leaf.curveTo(
            ix + iw * 0.82, iy + ih * 0.40, // right-edge control 1
            ix + iw * 0.74, iy + ih * 0.70, // right-edge control 2
            ix + iw * 0.56, iy + ih * 0.90  // lower-right body point
        );
        leaf.curveTo(
            ix + iw * 0.40, iy + ih * 0.98, // bottom control 1
            ix + iw * 0.22, iy + ih * 0.88, // bottom control 2
            ix + iw * 0.16, iy + ih * 0.62  // close back to left shoulder
        );
        leaf.closePath();

        // Base fill for the leaf body.
        g2.setColor(fillColor);
        g2.fill(leaf);

        Path2D highlight = new Path2D.Double();
        // Internal shape for soft top-left highlight on the leaf surface.
        highlight.moveTo(ix + iw * 0.30, iy + ih * 0.58); // highlight start (lower-left of highlight)
        highlight.curveTo(
            ix + iw * 0.36, iy + ih * 0.36, // highlight control 1 (upward lift)
            ix + iw * 0.54, iy + ih * 0.24, // highlight control 2 (toward tip)
            ix + iw * 0.72, iy + ih * 0.30  // highlight upper-right
        );
        highlight.curveTo(
            ix + iw * 0.58, iy + ih * 0.34, // return control 1
            ix + iw * 0.44, iy + ih * 0.44, // return control 2
            ix + iw * 0.36, iy + ih * 0.62  // highlight lower edge
        );
        highlight.closePath();
        g2.setColor(highlightColor);
        g2.fill(highlight);

        // Crisp outer border to keep shape legible on bright/dark backgrounds.
        g2.setColor(outlineColor);
        g2.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(leaf);

        // Main (central) vein running from lower-left base region toward tip.
        g2.setColor(veinColor);
        g2.setStroke(new BasicStroke(0.95f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new QuadCurve2D.Double(
            ix + iw * 0.22, iy + ih * 0.78, // central vein root (at stem/leaf junction)
            ix + iw * 0.52, iy + ih * 0.40, // central vein control (higher for more arc)
            ix + iw * 0.92, iy + ih * 0.30  // central vein near-tip termination
        ));

        // Lower-right offshoot vein:
        // root stays at the same central branch point; endpoint trends toward tip.
        drawTaperedVein(g2, veinColor,
            ix + iw * 0.44, iy + ih * 0.56, // offshoot root (aligned to central vein)
            ix + iw * 0.57, iy + ih * 0.67, // lower offshoot control
            ix + iw * 0.72, iy + ih * 0.58  // lower offshoot termination (right/lower)
        );

        // Upper-right offshoot vein:
        // same root as lower offshoot, terminating above it toward the tip.
        drawTaperedVein(g2, veinColor,
            ix + iw * 0.44, iy + ih * 0.56, // offshoot root (shared, aligned to central vein)
            ix + iw * 0.43, iy + ih * 0.34, // upper offshoot control (mid-height, shifted further left)
            ix + iw * 0.58, iy + ih * 0.24  // upper offshoot termination (near top, more vertical)
        );

        // Secondary (short) offshoot veins:
        // start about halfway between the primary offshoot root and stem root,
        // and use roughly half-length / half-curve versions of the two primary branches.
        drawTaperedVein(g2, veinColor,
            ix + iw * 0.30, iy + ih * 0.70, // secondary shared root (shifted ~25% toward stem)
            ix + iw * 0.43, iy + ih * 0.78, // lower secondary control
            ix + iw * 0.53, iy + ih * 0.75  // lower secondary termination (near lower edge)
        );
        drawTaperedVein(g2, veinColor,
            ix + iw * 0.30, iy + ih * 0.70, // secondary shared root (shifted ~25% toward stem)
            ix + iw * 0.25, iy + ih * 0.51, // upper secondary control (shifted with root)
            ix + iw * 0.37, iy + ih * 0.37  // upper secondary termination (shifted with root)
        );

        // Tiny dark connector near base.
        g2.draw(new Line2D.Double(
            ix + iw * 0.20, iy + ih * 0.74, // connector start (near central root)
            ix + iw * 0.10, iy + ih * 0.95  // connector end (toward stem area)
        ));

        // Brown stem line extending down-left from the leaf base.
        g2.setColor(stemColor);
        g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new QuadCurve2D.Double(
            ix + iw * 0.22, iy + ih * 0.78, // stem root at leaf base
            ix + iw * 0.13, iy + ih * 0.88, // stem control (gentle bend)
            ix + iw * 0.07, iy + ih * 0.99  // stem tip
        ));

        // Stem edge highlight (slightly lighter brown), drawn on top of stem.
        g2.setColor(accentLineColor);
        g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new QuadCurve2D.Double(
            ix + iw * 0.20, iy + ih * 0.80, // highlight root (offset from stem root)
            ix + iw * 0.12, iy + ih * 0.89, // highlight control
            ix + iw * 0.08, iy + ih * 0.97  // highlight tip
        ));
        } finally {
        g2.dispose();
        }
    }

    private void drawTaperedVein(Graphics2D g2,
                     Color veinColor,
                     double x0, double y0,
                     double cx, double cy,
                     double x1, double y1) {
        // Continuous taper along the full curve: wide at root -> narrow at tip.
        final int segments = 12;
        // Scale taper geometry with icon size so small-font icons don't look too chunky.
        final double iconScale = Math.max(0.65, Math.min(1.8, Math.min(getIconWidth(), getIconHeight()) / 18.0));
        final double taperScale = Math.pow(iconScale, 1.2);
        final double rootHalfWidth = 1.15 * taperScale;
        final double tipHalfWidth = Math.max(0.04, 0.06 * taperScale);
        final double tipAdvance = 1.15 * taperScale;

        double[] lx = new double[segments + 1];
        double[] ly = new double[segments + 1];
        double[] rx = new double[segments + 1];
        double[] ry = new double[segments + 1];
        double lastUx = 1.0;
        double lastUy = 0.0;

        for (int i = 0; i <= segments; i++) {
        double t = i / (double) segments;
        double omt = 1.0 - t;
        double px = omt * omt * x0 + 2.0 * omt * t * cx + t * t * x1;
        double py = omt * omt * y0 + 2.0 * omt * t * cy + t * t * y1;

        double dx = 2.0 * omt * (cx - x0) + 2.0 * t * (x1 - cx);
        double dy = 2.0 * omt * (cy - y0) + 2.0 * t * (y1 - cy);
        double dl = Math.sqrt(dx * dx + dy * dy);
        if (dl > 1e-6) {
            lastUx = dx / dl;
            lastUy = dy / dl;
        }

        double nx = -lastUy;
        double ny = lastUx;
        double hw = rootHalfWidth + (tipHalfWidth - rootHalfWidth) * t;
        lx[i] = px + nx * hw;
        ly[i] = py + ny * hw;
        rx[i] = px - nx * hw;
        ry[i] = py - ny * hw;
        }

        double tipX = x1 + lastUx * tipAdvance;
        double tipY = y1 + lastUy * tipAdvance;

        Path2D tapered = new Path2D.Double();
        tapered.moveTo(lx[0], ly[0]);
        for (int i = 1; i <= segments; i++) {
        tapered.lineTo(lx[i], ly[i]);
        }
        tapered.lineTo(tipX, tipY);
        for (int i = segments; i >= 0; i--) {
        tapered.lineTo(rx[i], ry[i]);
        }
        tapered.closePath();

        g2.setColor(veinColor);
        g2.fill(tapered);
    }
    }


