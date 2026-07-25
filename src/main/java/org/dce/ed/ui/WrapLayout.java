package org.dce.ed.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * {@link FlowLayout} that wraps to the next line when children do not fit the container width.
 */
public final class WrapLayout extends FlowLayout {

    public WrapLayout() {
        super(LEFT);
    }

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getWidth();
            if (targetWidth <= 0 && target.getParent() != null) {
                targetWidth = target.getParent().getWidth();
            }
            if (targetWidth <= 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int maxWidth = targetWidth - insets.left - insets.right - hgap * 2;

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;
            int nmembers = target.getComponentCount();
            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (!m.isVisible()) {
                    continue;
                }
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                if (rowWidth > 0 && rowWidth + hgap + d.width > maxWidth) {
                    dim.width = Math.max(dim.width, rowWidth);
                    dim.height += rowHeight + vgap;
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth > 0) {
                    rowWidth += hgap;
                }
                rowWidth += d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            dim.width = Math.max(dim.width, rowWidth);
            dim.height += rowHeight;

            dim.width += insets.left + insets.right + hgap * 2;
            dim.height += insets.top + insets.bottom + vgap * 2;
            return dim;
        }
    }

    @Override
    public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
            Insets insets = target.getInsets();
            int maxWidth = target.getWidth() - insets.left - insets.right - getHgap() * 2;
            int x = insets.left + getHgap();
            int y = insets.top + getVgap();
            int rowHeight = 0;
            int start = 0;
            int nmembers = target.getComponentCount();
            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (!m.isVisible()) {
                    continue;
                }
                Dimension d = m.getPreferredSize();
                m.setSize(d.width, d.height);
                if (x > insets.left + getHgap() && x + d.width > maxWidth + insets.left + getHgap()) {
                    moveComponents(target, insets.left + getHgap(), y, maxWidth, rowHeight, start, i);
                    x = insets.left + getHgap();
                    y += getVgap() + rowHeight;
                    rowHeight = 0;
                    start = i;
                }
                x += d.width + getHgap();
                rowHeight = Math.max(rowHeight, d.height);
            }
            moveComponents(target, insets.left + getHgap(), y, maxWidth, rowHeight, start, nmembers);
        }
    }

    private void moveComponents(Container target, int x, int y, int width, int height,
            int rowStart, int rowEnd) {
        switch (getAlignment()) {
            case LEFT -> {
                // x already set
            }
            case CENTER -> x += width / 2;
            case RIGHT -> x += width;
            default -> {
            }
        }
        for (int i = rowStart; i < rowEnd; i++) {
            Component m = target.getComponent(i);
            if (!m.isVisible()) {
                continue;
            }
            int cy = y + (height - m.getHeight()) / 2;
            m.setLocation(x, cy);
            x += m.getWidth() + getHgap();
        }
    }
}
