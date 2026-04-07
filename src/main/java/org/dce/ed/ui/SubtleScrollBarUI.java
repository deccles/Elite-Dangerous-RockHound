package org.dce.ed.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * Minimal rounded thumb, transparent track — matches System tab overlay scroll bars; use with a slightly wide
 * preferred size for a comfortable hit target.
 */
public final class SubtleScrollBarUI extends BasicScrollBarUI {

    @Override
    protected Dimension getMinimumThumbSize() {
        return new Dimension(10, 24);
    }

    @Override
    protected void configureScrollBarColors() {
        trackColor = EdoUi.Internal.TRANSPARENT;
        thumbColor = EdoUi.withAlpha(EdoUi.User.MAIN_TEXT, 72);
        thumbDarkShadowColor = EdoUi.Internal.TRANSPARENT;
        thumbHighlightColor = EdoUi.Internal.TRANSPARENT;
        thumbLightShadowColor = EdoUi.Internal.TRANSPARENT;
        trackHighlightColor = EdoUi.Internal.TRANSPARENT;
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return createZeroButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return createZeroButton();
    }

    private static JButton createZeroButton() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        b.setMinimumSize(new Dimension(0, 0));
        b.setMaximumSize(new Dimension(0, 0));
        b.setOpaque(false);
        b.setFocusable(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        return b;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        // Transparent track for overlay look.
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        if (thumbBounds == null || thumbBounds.width <= 0 || thumbBounds.height <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(EdoUi.withAlpha(EdoUi.User.MAIN_TEXT, 90));
            int padX = 2;
            int padY = 1;
            int arc = Math.max(6, thumbBounds.width - padX * 2);
            g2.fillRoundRect(
                    thumbBounds.x + padX,
                    thumbBounds.y + padY,
                    Math.max(1, thumbBounds.width - padX * 2),
                    Math.max(1, thumbBounds.height - padY * 2),
                    arc,
                    arc);
        } finally {
            g2.dispose();
        }
    }
}
