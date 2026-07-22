package org.dce.ed.ui;

import java.awt.Point;

import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Applies mouse-wheel deltas to a {@link JScrollPane} when the pointer is over it — used with a global
 * native hook while the overlay is in mouse pass-through mode (Swing does not receive wheel events).
 */
public final class PassThroughScrollSupport {

    private PassThroughScrollSupport() {
    }

    /** {@code true} when the vertical bar can move (content taller than the viewport). */
    public static boolean isVerticallyScrollable(JScrollPane scrollPane) {
        if (scrollPane == null || !scrollPane.isShowing()) {
            return false;
        }
        JScrollBar vsb = scrollPane.getVerticalScrollBar();
        if (vsb == null || !vsb.isEnabled()) {
            return false;
        }
        int max = Math.max(vsb.getMinimum(), vsb.getMaximum() - vsb.getVisibleAmount());
        return max > vsb.getMinimum();
    }

    /**
     * @param wheelRotation native wheel rotation (positive = scroll down)
     * @return {@code true} if the wheel was handled for this pane (caller may consume the native event)
     */
    public static boolean applyVerticalWheelIfHit(JScrollPane scrollPane, int screenX, int screenY, int wheelRotation) {
        if (scrollPane == null || !scrollPane.isShowing() || wheelRotation == 0) {
            return false;
        }
        Point p = new Point(screenX, screenY);
        SwingUtilities.convertPointFromScreen(p, scrollPane);
        // Whole pane (viewport + bars) — do not require the pointer to be on the scrollbar thumb.
        if (!scrollPane.contains(p)) {
            return false;
        }
        JScrollBar vsb = scrollPane.getVerticalScrollBar();
        if (vsb == null || !vsb.isEnabled()) {
            return false;
        }
        int max = Math.max(vsb.getMinimum(), vsb.getMaximum() - vsb.getVisibleAmount());
        if (max <= vsb.getMinimum()) {
            return false; // nothing to scroll
        }
        int unit = Math.max(1, vsb.getUnitIncrement(wheelRotation > 0 ? 1 : -1));
        if (unit <= 1) {
            unit = Math.max(16, vsb.getUnitIncrement());
        }
        int delta = wheelRotation * unit * 3;
        int v = vsb.getValue();
        int newV = Math.max(vsb.getMinimum(), Math.min(max, v + delta));
        if (newV != v) {
            vsb.setValue(newV);
        }
        // Consume even at the ends so the game does not also react while over an active scroller.
        return true;
    }
}
