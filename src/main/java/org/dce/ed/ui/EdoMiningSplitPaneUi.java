package org.dce.ed.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JSplitPane;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/**
 * Themed {@link JSplitPane} UI for Mining and System tabs: semi-opaque accent divider with up/down (or
 * left/right) grab glyphs so the bar reads as draggable.
 * <p>
 * {@link java.awt.Container#update} clears the divider with {@code clearRect} before {@code paint} for some
 * peers; over a transparent overlay that reads as twin hairlines or a hollow band. We override {@code update}
 * to match {@link javax.swing.JComponent}: only {@code paint}, no clear.
 */
public final class EdoMiningSplitPaneUi {

    /** Main-text alpha for the draggable divider bar (0–255). Lower = more transparent. */
    private static final int DIVIDER_MAIN_TEXT_ALPHA = 110;

    private EdoMiningSplitPaneUi() {
    }

    public static void install(JSplitPane split) {
        if (split == null) {
            return;
        }
        split.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                ThemedDivider d = new ThemedDivider(this);
                d.setBorder(BorderFactory.createEmptyBorder());
                applyDividerTheme(d);
                return d;
            }
        });
    }

    /**
     * Updates divider background when {@link EdoUi.User#MAIN_TEXT} / derived colors change.
     */
    public static void applyDividerTheme(JSplitPane split) {
        if (split == null) {
            return;
        }
        if (split.getUI() instanceof BasicSplitPaneUI) {
            BasicSplitPaneDivider d = ((BasicSplitPaneUI) split.getUI()).getDivider();
            if (d != null) {
                applyDividerTheme(d);
            }
        }
    }

    static void applyDividerTheme(BasicSplitPaneDivider divider) {
        if (divider == null) {
            return;
        }
        divider.setBackground(EdoUi.Internal.mainTextAlpha(DIVIDER_MAIN_TEXT_ALPHA));
        divider.setBorder(BorderFactory.createEmptyBorder());
        divider.repaint();
    }

    private static final class ThemedDivider extends BasicSplitPaneDivider {

        private static final long serialVersionUID = 1L;

        ThemedDivider(BasicSplitPaneUI ui) {
            super(ui);
        }

        @Override
        public void update(Graphics g) {
            paint(g);
        }

        @Override
        public void paint(Graphics g) {
            Dimension size = getSize();
            if (size.width <= 0 || size.height <= 0) {
                return;
            }
            Color bg = getBackground();
            if (bg == null) {
                bg = EdoUi.Internal.mainTextAlpha(DIVIDER_MAIN_TEXT_ALPHA);
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setComposite(AlphaComposite.SrcOver);
                g2.setColor(bg);
                g2.fillRect(0, 0, size.width, size.height);
                // Do not call BasicSplitPaneDivider.paint / Container.paint: that forwards to lightweight
                // children (one-touch arrows, etc.) which draw extra glyphs on top of the bar.
                paintGrabAffordance(g2, size);
            } finally {
                g2.dispose();
            }
            Border border = getBorder();
            if (border != null) {
                border.paintBorder(this, g, 0, 0, size.width, size.height);
            }
        }

        private void paintGrabAffordance(Graphics2D g2, Dimension size) {
            if (splitPane == null || size.width < 12 || size.height < 12) {
                return;
            }
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(EdoUi.ED_ORANGE_LESS_TRANS);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (splitPane.getOrientation() == JSplitPane.VERTICAL_SPLIT) {
                paintUpDownGrabGlyphs(g2, size.width, size.height);
            } else {
                paintLeftRightGrabGlyphs(g2, size.width, size.height);
            }
        }

        /** Up/down chevrons on a horizontal divider (vertical split). */
        private static void paintUpDownGrabGlyphs(Graphics2D g2, int w, int h) {
            int cx = w / 2;
            int cy = h / 2;
            int triW = 8;
            int triH = 4;
            int gap = 2;
            drawOutlineTriangle(g2, cx, cy - gap - triH, triW, triH, true);
            drawOutlineTriangle(g2, cx, cy + gap + triH, triW, triH, false);
        }

        /** Left/right chevrons on a vertical divider (horizontal split). */
        private static void paintLeftRightGrabGlyphs(Graphics2D g2, int w, int h) {
            int cx = w / 2;
            int cy = h / 2;
            int triW = 4;
            int triH = 8;
            int gap = 2;
            drawOutlineTriangleLeft(g2, cx - gap - triW, cy, triW, triH);
            drawOutlineTriangleRight(g2, cx + gap + triW, cy, triW, triH);
        }

        private static void drawOutlineTriangle(Graphics2D g2, int apexX, int apexY, int triW, int triH,
                boolean pointsUp) {
            int half = triW / 2;
            int baseY = pointsUp ? apexY + triH : apexY - triH;
            int[] xs = {apexX, apexX - half, apexX + half};
            int[] ys = {apexY, baseY, baseY};
            g2.drawPolygon(xs, ys, 3);
        }

        private static void drawOutlineTriangleLeft(Graphics2D g2, int apexX, int apexY, int triW, int triH) {
            int half = triH / 2;
            int baseX = apexX + triW;
            int[] xs = {apexX, baseX, baseX};
            int[] ys = {apexY, apexY - half, apexY + half};
            g2.drawPolygon(xs, ys, 3);
        }

        private static void drawOutlineTriangleRight(Graphics2D g2, int apexX, int apexY, int triW, int triH) {
            int half = triH / 2;
            int baseX = apexX - triW;
            int[] xs = {apexX, baseX, baseX};
            int[] ys = {apexY, apexY - half, apexY + half};
            g2.drawPolygon(xs, ys, 3);
        }
    }
}
