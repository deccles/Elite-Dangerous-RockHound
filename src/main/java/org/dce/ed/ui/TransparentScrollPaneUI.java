package org.dce.ed.ui;

import java.awt.Graphics;

import javax.swing.JComponent;
import javax.swing.plaf.ScrollPaneUI;
import javax.swing.plaf.basic.BasicScrollPaneUI;

/**
 * ScrollPaneUI that never paints an opaque background or LAF border chrome, for overlay tables.
 */
public final class TransparentScrollPaneUI extends BasicScrollPaneUI {

    public static ScrollPaneUI createUI(JComponent c) {
        return new TransparentScrollPaneUI();
    }

    @Override
    public void update(Graphics g, JComponent c) {
        paint(g, c);
    }
}
