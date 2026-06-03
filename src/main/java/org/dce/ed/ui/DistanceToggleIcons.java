package org.dce.ed.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;

/**
 * Icons for route distance mode toggles and system-tab distance-from-ship / distance-from-star toggles.
 */
public final class DistanceToggleIcons {

    private DistanceToggleIcons() {
    }

    private static int ir(double v) {
        return (int) Math.round(v);
    }

    /** Concentric ring + dot (route tab: cumulative Ly from current system). */
    public static final class RingAndDotIcon implements Icon {
        private final int size;

        public RingAndDotIcon(int size) {
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fg = c != null ? c.getForeground() : Color.WHITE;
                g2.setColor(fg);
                int pad = 2;
                int d = size - pad * 2;
                int ox = x + pad;
                int oy = y + pad;
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawOval(ox, oy, d, d);
                int inner = Math.max(4, d / 2);
                int cx = ox + d / 2 - inner / 2;
                int cy = oy + d / 2 - inner / 2;
                g2.fillOval(cx, cy, inner, inner);
            } finally {
                g2.dispose();
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
    }

    /** Two nodes and a link (route tab: leg Ly). */
    public static final class LinkedNodesIcon implements Icon {
        private final int size;

        public LinkedNodesIcon(int size) {
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fg = c != null ? c.getForeground() : Color.WHITE;
                g2.setColor(fg);
                g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int midY = y + size / 2;
                int x1 = x + size / 4;
                int x2 = x + 3 * size / 4;
                g2.drawLine(x1, midY, x2, midY);
                int r = Math.max(2, size / 7);
                g2.fillOval(x1 - r, midY - r, r * 2, r * 2);
                g2.fillOval(x2 - r, midY - r, r * 2, r * 2);
            } finally {
                g2.dispose();
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
    }

    /** System tab: ship-centric distances — ring around a small rocket silhouette. */
    public static final class CircleAroundRocketIcon implements Icon {
        private final int size;

        public CircleAroundRocketIcon(int size) {
            this.size = Math.max(14, size);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fg = c != null ? c.getForeground() : Color.WHITE;
                g2.setColor(fg);
                int pad = 2;
                int d = size - pad * 2;
                int ox = x + pad;
                int oy = y + pad;
                double cx = ox + d / 2.0;
                double cy = oy + d / 2.0;

                /* Nose toward upper-right (45°); tail fins + mid-body strakes. */
                double ax = Math.sqrt(0.5);
                double ay = -Math.sqrt(0.5);
                double px = -ay;
                double py = ax;

                /* Slightly smaller fuselage so fins + ring read clearly. */
                double L = d * 0.44;
                double wMid = d * 0.11;
                double wTail = d * 0.055;
                double finBack = d * 0.12;
                double finOut = d * 0.14;

                double sNose = L * 0.56;
                double sSh = -L * 0.06;
                double sTail = -L * 0.44;

                int nx = ir(cx + ax * sNose);
                int ny = ir(cy + ay * sNose);
                int r1x = ir(cx + ax * sSh + px * wMid);
                int r1y = ir(cy + ay * sSh + py * wMid);
                int rtx = ir(cx + ax * sTail + px * wTail);
                int rty = ir(cy + ay * sTail + py * wTail);
                int ltx = ir(cx + ax * sTail - px * wTail);
                int lty = ir(cy + ay * sTail - py * wTail);
                int l1x = ir(cx + ax * sSh - px * wMid);
                int l1y = ir(cy + ay * sSh - py * wMid);

                int[] hx = { nx, r1x, rtx, ltx, l1x };
                int[] hy = { ny, r1y, rty, lty, l1y };
                g2.fillPolygon(hx, hy, 5);

                /* Tail fins (aft, swept along body). */
                int frx = ir(rtx - ax * finBack + px * finOut);
                int fry = ir(rty - ay * finBack + py * finOut);
                int rhx = ir(rtx - ax * (finBack * 0.22) + px * (finOut * 0.38));
                int rhy = ir(rty - ay * (finBack * 0.22) + py * (finOut * 0.38));
                g2.fillPolygon(new int[] { rtx, frx, rhx }, new int[] { rty, fry, rhy }, 3);

                int flx = ir(ltx - ax * finBack - px * finOut);
                int fly = ir(lty - ay * finBack - py * finOut);
                int lhx = ir(ltx - ax * (finBack * 0.22) - px * (finOut * 0.38));
                int lhy = ir(lty - ay * (finBack * 0.22) - py * (finOut * 0.38));
                g2.fillPolygon(new int[] { ltx, flx, lhx }, new int[] { lty, fly, lhy }, 3);

                /* Mid-body fins (classic side strakes on the wide shoulder). */
                double midFin = d * 0.11;
                double along = d * 0.028;
                int mrx = ir(cx + ax * sSh * 0.55 + px * (wMid * 0.92 + midFin));
                int mry = ir(cy + ay * sSh * 0.55 + py * (wMid * 0.92 + midFin));
                int mrb1x = ir(cx + ax * sSh * 0.55 + px * wMid * 0.55 + ax * along);
                int mrb1y = ir(cy + ay * sSh * 0.55 + py * wMid * 0.55 + ay * along);
                int mrb2x = ir(cx + ax * sSh * 0.55 + px * wMid * 0.55 - ax * (along * 1.8));
                int mrb2y = ir(cy + ay * sSh * 0.55 + py * wMid * 0.55 - ay * (along * 1.8));
                g2.fillPolygon(new int[] { mrx, mrb1x, mrb2x }, new int[] { mry, mrb1y, mrb2y }, 3);

                int mlx = ir(cx + ax * sSh * 0.55 - px * (wMid * 0.92 + midFin));
                int mly = ir(cy + ay * sSh * 0.55 - py * (wMid * 0.92 + midFin));
                int mlb1x = ir(cx + ax * sSh * 0.55 - px * wMid * 0.55 + ax * along);
                int mlb1y = ir(cy + ay * sSh * 0.55 - py * wMid * 0.55 + ay * along);
                int mlb2x = ir(cx + ax * sSh * 0.55 - px * wMid * 0.55 - ax * (along * 1.8));
                int mlb2y = ir(cy + ay * sSh * 0.55 - py * wMid * 0.55 - ay * (along * 1.8));
                g2.fillPolygon(new int[] { mlx, mlb1x, mlb2x }, new int[] { mly, mlb1y, mlb2y }, 3);

                float sw = Math.max(1.15f, (float) (d * 0.072f));
                g2.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawOval(ox, oy, d, d);
            } finally {
                g2.dispose();
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
    }

    /** System tab: star-centric distances — ring around a small star silhouette. */
    public static final class CircleAroundStarIcon implements Icon {
        private final int size;

        public CircleAroundStarIcon(int size) {
            this.size = Math.max(14, size);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fg = c != null ? c.getForeground() : Color.WHITE;
                g2.setColor(fg);
                int pad = 2;
                int d = size - pad * 2;
                int ox = x + pad;
                int oy = y + pad;
                double cx = ox + d / 2.0;
                double cy = oy + d / 2.0;

                /* Classic five-point star (one spike up), not a four-point diamond. */
                double outerR = d * 0.26;
                double innerR = outerR * 0.42;
                int[] sx = new int[10];
                int[] sy = new int[10];
                for (int i = 0; i < 10; i++) {
                    double r = (i % 2 == 0) ? outerR : innerR;
                    double a = -Math.PI / 2.0 + i * Math.PI / 5.0;
                    sx[i] = ir(cx + r * Math.cos(a));
                    sy[i] = ir(cy + r * Math.sin(a));
                }
                g2.fillPolygon(sx, sy, 10);

                float sw = Math.max(1.15f, (float) (d * 0.072f));
                g2.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawOval(ox, oy, d, d);
            } finally {
                g2.dispose();
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
    }

    /** System tab: sort by exploration value — ring around a {@code $} mark. */
    public static final class CircleAroundDollarIcon implements Icon {
        private final int size;

        public CircleAroundDollarIcon(int size) {
            this.size = Math.max(14, size);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fg = c != null ? c.getForeground() : Color.WHITE;
                g2.setColor(fg);
                int pad = 2;
                int d = size - pad * 2;
                int ox = x + pad;
                int oy = y + pad;
                float sw = Math.max(1.15f, (float) (d * 0.072f));
                g2.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawOval(ox, oy, d, d);

                Font base = c != null ? c.getFont() : new Font("Dialog", Font.BOLD, 12);
                g2.setFont(base.deriveFont(Font.BOLD, Math.max(10f, d * 0.64f)));
                FontMetrics fm = g2.getFontMetrics();
                String mark = "$";
                int tx = ox + (d - fm.stringWidth(mark)) / 2;
                int ty = oy + (d + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(mark, tx, ty);
            } finally {
                g2.dispose();
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
    }
}
