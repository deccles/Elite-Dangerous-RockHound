package org.dce.ed.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JPanel;

import org.dce.ed.OverlayPreferences;

/**
 * Fills the panel bounds with {@link #setPaintColor(Color)} so themed overlay backgrounds
 * remain visible even when child components are non-opaque (same role as in {@code OverlayFrame}).
 */
public final class OverlayBackgroundPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private Color paintColor = new Color(0, 0, 0, 0);

    public OverlayBackgroundPanel() {
        setOpaque(false);
    }

    public void setPaintColor(Color paintColor) {
        if (paintColor == null) {
            paintColor = new Color(0, 0, 0, 0);
        }
        this.paintColor = paintColor;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            if (paintColor == null || paintColor.getAlpha() <= 0) {
                // AlphaComposite.Clear corrupts to lime green on decorated JFrames; keep pass-through only.
                if (OverlayPreferences.overlayChromeRequestsTransparency()) {
                    g2.setComposite(AlphaComposite.Clear);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                } else {
                    Color b = EdoUi.User.BACKGROUND;
                    g2.setComposite(AlphaComposite.SrcOver);
                    g2.setColor(new Color(b.getRed(), b.getGreen(), b.getBlue(), 255));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
                return;
            }
            g2.setColor(paintColor);
            g2.fillRect(0, 0, getWidth(), getHeight());
        } finally {
            g2.dispose();
        }
    }
}
