package org.dce.ed.ui;

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JComponent;
import javax.swing.plaf.ViewportUI;
import javax.swing.plaf.basic.BasicViewportUI;

import org.dce.ed.OverlayPreferences;

/**
 * ViewportUI that does not paint an opaque LAF background. Clears or fills the viewport first so
 * unpainted regions (e.g. below short tables) do not read as a white frame on transparent overlays.
 */
public final class TransparentViewportUI extends BasicViewportUI {

    public static ViewportUI createUI(JComponent c) {
        return new TransparentViewportUI();
    }

    @Override
    public void update(Graphics g, JComponent c) {
        paint(g, c);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        paintViewportBackground(g, c);
        super.paint(g, c);
    }

    static void paintViewportBackground(Graphics g, JComponent c) {
        int w = c.getWidth();
        int h = c.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            if (OverlayPreferences.overlayChromeRequestsTransparency()) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
                g2.fillRect(0, 0, w, h);
            } else if (c.isOpaque()) {
                g2.setComposite(AlphaComposite.SrcOver);
                g2.setColor(c.getBackground());
                g2.fillRect(0, 0, w, h);
            }
        } finally {
            g2.dispose();
        }
    }
}
