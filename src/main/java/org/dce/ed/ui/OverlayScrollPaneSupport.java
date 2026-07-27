package org.dce.ed.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;

import javax.accessibility.Accessible;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * Overlay table scroll panes: transparent chrome and {@link SubtleScrollBarUI} thumb styling.
 */
public final class OverlayScrollPaneSupport {

    private OverlayScrollPaneSupport() {
    }

    public static void configure(JScrollPane sp) {
        if (sp == null) {
            return;
        }

        sp.setOpaque(false);
        sp.setBackground(EdoUi.Internal.TRANSPARENT);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setViewportBorder(BorderFactory.createEmptyBorder());

        if (sp.getViewport() != null) {
            sp.getViewport().setOpaque(false);
            sp.getViewport().setBackground(EdoUi.Internal.TRANSPARENT);
        }

        if (sp.getColumnHeader() != null) {
            sp.getColumnHeader().setOpaque(false);
            sp.getColumnHeader().setBackground(EdoUi.Internal.TRANSPARENT);
            sp.getColumnHeader().setBorder(BorderFactory.createEmptyBorder());
        }

        JPanel corner = new JPanel();
        corner.setOpaque(false);
        corner.setBackground(EdoUi.Internal.TRANSPARENT);
        sp.setCorner(JScrollPane.UPPER_RIGHT_CORNER, corner);
        sp.setCorner(JScrollPane.LOWER_RIGHT_CORNER, corner);
        sp.setCorner(JScrollPane.UPPER_LEFT_CORNER, corner);
        sp.setCorner(JScrollPane.LOWER_LEFT_CORNER, corner);
    }

    public static void installSubtleScrollBars(JScrollPane sp) {
        if (sp == null) {
            return;
        }
        JScrollBar vsb = sp.getVerticalScrollBar();
        if (vsb != null) {
            vsb.setOpaque(false);
            vsb.setBackground(EdoUi.Internal.TRANSPARENT);
            vsb.setUI(new SubtleScrollBarUI());
            vsb.setPreferredSize(new Dimension(9, Integer.MAX_VALUE));
            vsb.setUnitIncrement(16);
        }
        JScrollBar hsb = sp.getHorizontalScrollBar();
        if (hsb != null) {
            hsb.setOpaque(false);
            hsb.setBackground(EdoUi.Internal.TRANSPARENT);
            hsb.setUI(new SubtleScrollBarUI());
            hsb.setPreferredSize(new Dimension(Integer.MAX_VALUE, 9));
            hsb.setUnitIncrement(16);
        }
    }

    /**
     * Applies {@link SubtleScrollBarUI} to a combo box dropdown scroller when the popup opens.
     */
    public static void installSubtleScrollBarsOnComboPopup(JComboBox<?> combo) {
        if (combo == null) {
            return;
        }
        Object tagged = combo.getClientProperty("edo.subtleComboScroll");
        if (Boolean.TRUE.equals(tagged)) {
            return;
        }
        combo.putClientProperty("edo.subtleComboScroll", Boolean.TRUE);
        combo.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                SwingUtilities.invokeLater(() -> styleComboPopupScrollBars(combo));
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                // no-op
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                // no-op
            }
        });
    }

    private static void styleComboPopupScrollBars(JComboBox<?> combo) {
        if (combo == null || combo.getUI() == null) {
            return;
        }
        Accessible child = combo.getUI().getAccessibleChild(combo, 0);
        if (!(child instanceof JPopupMenu popup)) {
            return;
        }
        for (Component c : popup.getComponents()) {
            if (c instanceof JScrollPane sp) {
                installSubtleScrollBars(sp);
            }
        }
    }

    public static void configureOverlayTableScroller(JScrollPane sp) {
        configure(sp);
        installSubtleScrollBars(sp);
    }

    /** {@code true} when {@code screenPoint} is over a visible scroll bar on {@code scrollPane}. */
    public static boolean isPointerOverScrollBar(JScrollPane scrollPane, Point screenPoint) {
        if (scrollPane == null || screenPoint == null || !scrollPane.isShowing()) {
            return false;
        }
        if (isPointerOverBar(scrollPane.getVerticalScrollBar(), screenPoint)) {
            return true;
        }
        return isPointerOverBar(scrollPane.getHorizontalScrollBar(), screenPoint);
    }

    private static boolean isPointerOverBar(JScrollBar bar, Point screenPoint) {
        if (bar == null || !bar.isShowing() || !bar.isVisible()) {
            return false;
        }
        Point local = new Point(screenPoint);
        SwingUtilities.convertPointFromScreen(local, bar);
        return bar.contains(local);
    }

    /** Applies pass-through wheel to the first matching scroll pane in {@code scrollPanes}. */
    public static boolean applyPassThroughWheelIfHit(JScrollPane[] scrollPanes,
                                                     int screenX,
                                                     int screenY,
                                                     int wheelRotation) {
        if (scrollPanes == null) {
            return false;
        }
        for (JScrollPane sp : scrollPanes) {
            if (PassThroughScrollSupport.applyVerticalWheelIfHit(sp, screenX, screenY, wheelRotation)) {
                return true;
            }
        }
        return false;
    }
}
