package org.dce.ed.ui;

import java.awt.Graphics;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ScrollPaneUI;
import javax.swing.plaf.basic.BasicScrollPaneUI;

/**
 * ScrollPaneUI that never paints an opaque background or LAF border chrome, for overlay tables.
 * <p>
 * Installs a non-{@code UIResource} empty border so {@code JTable.configureEnclosingScrollPane}
 * does not reapply {@code Table.scrollPaneBorder} (the etched white frame).
 */
public final class TransparentScrollPaneUI extends BasicScrollPaneUI {

    private static final EmptyBorder NO_CHROME = new EmptyBorder(0, 0, 0, 0);

    public static ScrollPaneUI createUI(JComponent c) {
        return new TransparentScrollPaneUI();
    }

    @Override
    protected void installDefaults(JScrollPane scrollpane) {
        super.installDefaults(scrollpane);
        scrollpane.setBorder(NO_CHROME);
        scrollpane.setViewportBorder(NO_CHROME);
    }

    @Override
    public void update(Graphics g, JComponent c) {
        paint(g, c);
    }
}
