package org.dce.ed.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

import javax.swing.Icon;

/** Small transport glyphs for the System tab guide orbit toolbar (play, pause, stop, speed chevrons). */
public final class OrbitPlaybackTransportIcons {

    private OrbitPlaybackTransportIcons() {
    }

    /** Right-pointing triangle (play). */
    public static final class PlayTriangleIcon implements Icon {
        private final int size;

        public PlayTriangleIcon(int size) {
            this.size = Math.max(10, size);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fg = c != null ? c.getForeground() : Color.WHITE;
                g2.setColor(fg);
                // Slight left/up nudge so the glyph reads centered inside square toggle borders (optical balance).
                double ox = -size * 0.035;
                double oy = -size * 0.035;
                double pad = size * 0.22;
                double midY = y + size * 0.5 + oy;
                double h = size - 2.0 * pad;
                double x0 = x + pad + ox;
                double x1 = x + size - pad + ox;
                Path2D tri = new Path2D.Double();
                tri.moveTo(x0, midY - h * 0.5);
                tri.lineTo(x1, midY);
                tri.lineTo(x0, midY + h * 0.5);
                tri.closePath();
                g2.fill(tri);
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

    /** Two vertical bars (pause). */
    public static final class PauseBarsIcon implements Icon {
        private final int size;

        public PauseBarsIcon(int size) {
            this.size = Math.max(10, size);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fg = c != null ? c.getForeground() : Color.WHITE;
                g2.setColor(fg);
                double ox = -size * 0.035;
                double oy = -size * 0.035;
                double pad = size * 0.20;
                double barW = Math.max(1.8, size * 0.20);
                double gap = size * 0.14;
                double cx = x + size * 0.5 + ox;
                double top = y + pad + oy;
                double h = size - 2.0 * pad;
                double xL = cx - gap * 0.5 - barW;
                double xR = cx + gap * 0.5;
                g2.fillRoundRect((int) Math.round(xL), (int) Math.round(top), (int) Math.round(barW), (int) Math.round(h),
                        1, 1);
                g2.fillRoundRect((int) Math.round(xR), (int) Math.round(top), (int) Math.round(barW), (int) Math.round(h),
                        1, 1);
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

    /** Filled square (stop / return to live journal positions). */
    public static final class StopSquareIcon implements Icon {
        private final int size;

        public StopSquareIcon(int size) {
            this.size = Math.max(10, size);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fg = c != null ? c.getForeground() : Color.WHITE;
                g2.setColor(fg);
                double ox = -size * 0.035;
                double oy = -size * 0.035;
                double pad = size * 0.26;
                double side = size - 2.0 * pad;
                g2.fillRoundRect((int) Math.round(x + pad + ox), (int) Math.round(y + pad + oy),
                        (int) Math.round(side), (int) Math.round(side), 1, 1);
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

    /** Two wedges pointing left (slower / fewer model days per second). */
    public static final class DoubleChevronLeftIcon implements Icon {
        private final int size;

        public DoubleChevronLeftIcon(int size) {
            this.size = Math.max(10, size);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fg = c != null ? c.getForeground() : Color.WHITE;
                g2.setColor(fg);
                double pad = size * 0.14;
                double w = size * 0.20;
                double gap = size * 0.08;
                double midY = y + size * 0.5;
                double h = size - 2.0 * pad;
                double x0 = x + pad;
                fillLeftWedge(g2, x0, midY, w, h);
                fillLeftWedge(g2, x0 + w + gap, midY, w, h);
            } finally {
                g2.dispose();
            }
        }

        private static void fillLeftWedge(Graphics2D g2, double apexX, double midY, double w, double h) {
            Path2D tri = new Path2D.Double();
            tri.moveTo(apexX, midY);
            tri.lineTo(apexX + w, midY - h * 0.5);
            tri.lineTo(apexX + w, midY + h * 0.5);
            tri.closePath();
            g2.fill(tri);
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

    /** Two wedges pointing right (faster / more model days per second). */
    public static final class DoubleChevronRightIcon implements Icon {
        private final int size;

        public DoubleChevronRightIcon(int size) {
            this.size = Math.max(10, size);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fg = c != null ? c.getForeground() : Color.WHITE;
                g2.setColor(fg);
                double pad = size * 0.14;
                double w = size * 0.20;
                double gap = size * 0.08;
                double midY = y + size * 0.5;
                double h = size - 2.0 * pad;
                double rightInner = x + size - pad;
                fillRightWedge(g2, rightInner, midY, w, h);
                fillRightWedge(g2, rightInner - w - gap, midY, w, h);
            } finally {
                g2.dispose();
            }
        }

        private static void fillRightWedge(Graphics2D g2, double apexX, double midY, double w, double h) {
            Path2D tri = new Path2D.Double();
            tri.moveTo(apexX, midY);
            tri.lineTo(apexX - w, midY - h * 0.5);
            tri.lineTo(apexX - w, midY + h * 0.5);
            tri.closePath();
            g2.fill(tri);
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

    /** Single wedge pointing down (collapse map toward bottom of tab). */
    public static final class ChevronDownIcon implements Icon {
        private final int size;

        public ChevronDownIcon(int size) {
            this.size = Math.max(10, size);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fg = c != null ? c.getForeground() : Color.WHITE;
                g2.setColor(fg);
                double pad = size * 0.22;
                double w = size * 0.36;
                double midX = x + size * 0.5;
                double apexY = y + size - pad;
                double h = size - 2.0 * pad;
                Path2D tri = new Path2D.Double();
                tri.moveTo(midX, apexY);
                tri.lineTo(midX - w * 0.5, apexY - h);
                tri.lineTo(midX + w * 0.5, apexY - h);
                tri.closePath();
                g2.fill(tri);
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

    /** Single wedge pointing up (restore map height). */
    public static final class ChevronUpIcon implements Icon {
        private final int size;

        public ChevronUpIcon(int size) {
            this.size = Math.max(10, size);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fg = c != null ? c.getForeground() : Color.WHITE;
                g2.setColor(fg);
                double pad = size * 0.22;
                double w = size * 0.36;
                double midX = x + size * 0.5;
                double apexY = y + pad;
                double h = size - 2.0 * pad;
                Path2D tri = new Path2D.Double();
                tri.moveTo(midX, apexY);
                tri.lineTo(midX - w * 0.5, apexY + h);
                tri.lineTo(midX + w * 0.5, apexY + h);
                tri.closePath();
                g2.fill(tri);
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
