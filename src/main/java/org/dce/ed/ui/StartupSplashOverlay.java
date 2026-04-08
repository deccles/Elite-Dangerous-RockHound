package org.dce.ed.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.dce.ed.util.AppIconUtil;

/**
 * Brief full-window splash: large centered app icon over a light dim, fading out (glass pane).
 */
public final class StartupSplashOverlay {

    /** Initial fully-opaque hold before fading starts (ms). */
    private static final int HOLD_MS = 3000;
    /** Fade duration (ms), after the hold period. */
    private static final int FADE_MS = 6000;
    private static final int TICK_MS = 16;

    private StartupSplashOverlay() {
    }

    /**
     * Installs a fading splash on {@code frame} if the app icon resource loads. Safe to call from any thread;
     * installation runs on the EDT once the frame is showing.
     */
    public static void install(JFrame frame) {
        if (frame == null) {
            return;
        }
        BufferedImage img = AppIconUtil.loadAppIconForSplash();
        if (img == null) {
            return;
        }
        // Defer one frame so the root pane has real bounds before painting the glass pane.
        SwingUtilities.invokeLater(() -> {
            if (!frame.isShowing()) {
                return;
            }
            JRootPane root = frame.getRootPane();
            SplashPanel panel = new SplashPanel(root, img);
            root.setGlassPane(panel);
            panel.setVisible(true);
            panel.startFade();
        });
    }

    private static final class SplashPanel extends JPanel {

        private final JRootPane root;
        private final BufferedImage image;
        private final Timer timer;
        private long fadeStartNanos;
        private volatile boolean dismissed;

        SplashPanel(JRootPane root, BufferedImage image) {
            this.root = root;
            this.image = image;
            setOpaque(false);
            setLayout(null);
            // Block clicks to underlying UI until the splash is gone.
            MouseAdapter block = new MouseAdapter() {
            };
            addMouseListener(block);
            addMouseMotionListener(block);

            timer = new Timer(TICK_MS, e -> onTick());
        }

        void startFade() {
            fadeStartNanos = System.nanoTime();
            timer.setRepeats(true);
            timer.start();
        }

        private void onTick() {
            if (isFadeComplete()) {
                timer.stop();
                dismiss();
                return;
            }
            repaint();
        }

        private void dismiss() {
            if (dismissed) {
                return;
            }
            dismissed = true;
            timer.stop();
            Runnable clear = () -> {
                JPanel empty = new JPanel();
                empty.setOpaque(false);
                root.setGlassPane(empty);
                empty.setVisible(false);
            };
            if (SwingUtilities.isEventDispatchThread()) {
                clear.run();
            } else {
                SwingUtilities.invokeLater(clear);
            }
        }

        /** Opacity 1 → 0 for icon/dim, based on elapsed time. */
        private float splashOpacity() {
            float t = fadeProgress();
            if (t >= 1f) {
                return 0f;
            }
            // Icon fades out by ~90% of the full animation so title can linger slightly longer.
            float iconT = Math.min(1f, t / 0.90f);
            float easedT = easeOutCubic(iconT);
            return Math.max(0f, 1f - easedT);
        }

        /** Title opacity lingers longer than icon opacity and uses the same gentle cubic easing. */
        private float titleOpacity() {
            float t = fadeProgress();
            if (t >= 1f) {
                return 0f;
            }
            float easedT = easeOutCubic(t);
            return Math.max(0f, 1f - easedT);
        }

        private float fadeProgress() {
            if (fadeStartNanos <= 0L) {
                return 0f;
            }
            long elapsedNanos = System.nanoTime() - fadeStartNanos;
            double holdNanos = HOLD_MS * 1_000_000.0;
            if (elapsedNanos <= holdNanos) {
                return 0f;
            }
            double durationNanos = FADE_MS * 1_000_000.0;
            if (durationNanos <= 0.0) {
                return 1f;
            }
            double fadeElapsedNanos = elapsedNanos - holdNanos;
            return (float) Math.max(0.0, Math.min(1.0, fadeElapsedNanos / durationNanos));
        }

        private boolean isFadeComplete() {
            if (fadeStartNanos <= 0L) {
                return false;
            }
            long elapsedNanos = System.nanoTime() - fadeStartNanos;
            long totalNanos = (long) ((HOLD_MS + (double) FADE_MS) * 1_000_000.0);
            return elapsedNanos >= totalNanos;
        }

        private float easeOutCubic(float t) {
            float clamped = Math.max(0f, Math.min(1f, t));
            float u = 1f - clamped;
            return 1f - (u * u * u);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            float a = splashOpacity();
            if (a <= 0.001f) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a * 0.4f));
                g2.setColor(Color.BLACK);
                g2.fillRect(0, 0, w, h);

                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
                int iw = image.getWidth();
                int ih = image.getHeight();
                int maxSide = (int) Math.round(Math.min(w, h) * 0.58);
                double scale = Math.min((double) maxSide / iw, (double) maxSide / ih);
                int tw = Math.max(1, (int) Math.round(iw * scale));
                int th = Math.max(1, (int) Math.round(ih * scale));
                int x = (w - tw) / 2;
                int y = (h - th) / 2;
                g2.drawImage(image, x, y, tw, th, null);

                drawTitle(g2, w, h, y + th, titleOpacity());
            } finally {
                g2.dispose();
            }
        }

        private void drawTitle(Graphics2D g2, int w, int h, int imageBottomY, float alpha) {
            float textAlpha = Math.max(0f, Math.min(1f, alpha));
            if (textAlpha <= 0.001f) {
                return;
            }

            int mainFontSize = Math.max(30, Math.min(84, (int) Math.round(Math.min(w, h) * 0.074)));
            int subFontSize = Math.max(16, Math.min(42, (int) Math.round(mainFontSize * 0.55)));

            Font mainFont = new Font("Segoe UI", Font.BOLD, mainFontSize);
            Font subFont = new Font("Segoe UI", Font.BOLD, subFontSize);
            if (!mainFont.getFamily().toLowerCase().contains("segoe")) {
                mainFont = new Font(Font.SANS_SERIF, Font.BOLD, mainFontSize);
                subFont = new Font(Font.SANS_SERIF, Font.BOLD, subFontSize);
            }

            GlyphVector rhGv = mainFont.createGlyphVector(g2.getFontRenderContext(), "RockHound");
            java.awt.geom.Rectangle2D rhBounds = rhGv.getOutline().getBounds2D();
            double rhLeftX = (w - rhBounds.getWidth()) / 2.0;
            GlyphVector rGv = mainFont.createGlyphVector(g2.getFontRenderContext(), "R");
            double rWidth = rGv.getOutline().getBounds2D().getWidth();
            GlyphVector edGv = subFont.createGlyphVector(g2.getFontRenderContext(), "Elite Dangerous");
            java.awt.geom.Rectangle2D edBounds = edGv.getOutline().getBounds2D();

            // Move Elite Dangerous substantially upward into the icon region.
            int eliteBaseline = imageBottomY - (int) Math.round(subFontSize * 1.35);
            eliteBaseline = Math.max(eliteBaseline, subFontSize + 8);
            double edLeftX = rhLeftX + rWidth - 8;

            // Keep exactly a 20px gap from ED bottom to RH top.
            int maxY = h - 16;
            int edBottomY = eliteBaseline + (int) Math.round(edBounds.getHeight());
            int rockhoundBaseline = Math.min(maxY, edBottomY -6);

            drawStyledLine(g2, "Elite Dangerous", subFont, eliteBaseline, w, textAlpha,
                    new Color(78, 82, 90), new Color(28, 31, 36), 0.12f, edLeftX);
            drawStyledLine(g2, "RockHound", mainFont, rockhoundBaseline, w, textAlpha,
                    new Color(255, 234, 150), new Color(255, 112, 24), 0.38f, rhLeftX);
        }

        private void drawStyledLine(Graphics2D g2, String text, Font font, int baseline, int width, float alpha,
                Color topFill, Color bottomFill, float glowAlpha, double leftXOrNegativeCenter) {
            GlyphVector gv = font.createGlyphVector(g2.getFontRenderContext(), text);
            java.awt.Shape shape = gv.getOutline();
            java.awt.geom.Rectangle2D bounds = shape.getBounds2D();
            double tx = (width - bounds.getWidth()) / 2.0 - bounds.getX();
            if (leftXOrNegativeCenter >= 0) {
                tx = leftXOrNegativeCenter - bounds.getX();
            }
            double ty = baseline - bounds.getY();
            g2.translate(tx, ty);
            try {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * glowAlpha));
                g2.setColor(new Color(255, 145, 36));
                g2.setStroke(new java.awt.BasicStroke(Math.max(2f, font.getSize2D() * 0.10f),
                        java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                g2.draw(shape);

                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.90f));
                g2.setColor(new Color(14, 16, 20));
                g2.setStroke(new java.awt.BasicStroke(Math.max(1.6f, font.getSize2D() * 0.045f),
                        java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                g2.draw(shape);

                GradientPaint gp = new GradientPaint(0, (float) bounds.getMinY(), topFill, 0, (float) bounds.getMaxY(),
                        bottomFill);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.setPaint(gp);
                g2.fill(shape);
            } finally {
                g2.translate(-tx, -ty);
            }
        }
    }
}
