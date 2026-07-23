package org.dce.ed.ui;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.plaf.basic.BasicSliderUI;

/**
 * Theme-colored slider for preferences / overlay chrome (track + thumb use {@link EdoUi.User#MAIN_TEXT}).
 */
public final class OverlaySliderUI extends BasicSliderUI {

    private static final int TRACK_THICKNESS = 4;
    private static final int THUMB_W = 12;
    private static final int THUMB_H = 18;

    public OverlaySliderUI(JSlider slider) {
        super(slider);
    }

    /** Installs this UI and clears LAF opaque chrome so the theme shows through. */
    public static void apply(JSlider slider) {
        if (slider == null) {
            return;
        }
        slider.setOpaque(false);
        slider.setBackground(EdoUi.Internal.TRANSPARENT);
        slider.setForeground(EdoUi.User.MAIN_TEXT);
        slider.setUI(new OverlaySliderUI(slider));
    }

    @Override
    protected Dimension getThumbSize() {
        if (slider.getOrientation() == JSlider.HORIZONTAL) {
            return new Dimension(THUMB_W, THUMB_H);
        }
        return new Dimension(THUMB_H, THUMB_W);
    }

    @Override
    public void paintTrack(Graphics g) {
        Rectangle track = trackRect;
        if (track == null || track.width <= 0 || track.height <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(EdoUi.withAlpha(EdoUi.User.MAIN_TEXT, 55));
            if (slider.getOrientation() == JSlider.HORIZONTAL) {
                int y = track.y + (track.height - TRACK_THICKNESS) / 2;
                g2.fill(new RoundRectangle2D.Float(track.x, y, track.width, TRACK_THICKNESS, 4, 4));
            } else {
                int x = track.x + (track.width - TRACK_THICKNESS) / 2;
                g2.fill(new RoundRectangle2D.Float(x, track.y, TRACK_THICKNESS, track.height, 4, 4));
            }
        } finally {
            g2.dispose();
        }
    }

    @Override
    public void paintThumb(Graphics g) {
        Rectangle thumb = thumbRect;
        if (thumb == null || thumb.width <= 0 || thumb.height <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(EdoUi.User.MAIN_TEXT);
            int pad = 1;
            g2.fill(new RoundRectangle2D.Float(
                    thumb.x + pad,
                    thumb.y + pad,
                    Math.max(1, thumb.width - pad * 2),
                    Math.max(1, thumb.height - pad * 2),
                    4,
                    4));
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(EdoUi.withAlpha(EdoUi.User.MAIN_TEXT, 180));
            g2.draw(new RoundRectangle2D.Float(
                    thumb.x + pad,
                    thumb.y + pad,
                    Math.max(1, thumb.width - pad * 2),
                    Math.max(1, thumb.height - pad * 2),
                    4,
                    4));
        } finally {
            g2.dispose();
        }
    }

    @Override
    public void paintFocus(Graphics g) {
        // No default focus ring — theme thumb is enough.
    }

    @Override
    public void paintTicks(Graphics g) {
        Rectangle track = trackRect;
        if (track == null || slider.getMajorTickSpacing() <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setColor(EdoUi.withAlpha(EdoUi.User.MAIN_TEXT, 90));
            int min = slider.getMinimum();
            int max = slider.getMaximum();
            int major = slider.getMajorTickSpacing();
            if (slider.getOrientation() == JSlider.HORIZONTAL) {
                int y0 = track.y + track.height / 2 + TRACK_THICKNESS;
                for (int v = min; v <= max; v += major) {
                    int x = xPositionForValue(v);
                    g2.drawLine(x, y0, x, y0 + 4);
                }
            } else {
                int x0 = track.x + track.width / 2 + TRACK_THICKNESS;
                for (int v = min; v <= max; v += major) {
                    int y = yPositionForValue(v);
                    g2.drawLine(x0, y, x0 + 4, y);
                }
            }
        } finally {
            g2.dispose();
        }
    }
}
