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

    /**
     * @param wheelRotation native wheel rotation (positive = scroll down)
     * @return {@code true} if the scroll bar was adjusted (caller may consume the native event)
     */
    public static boolean applyVerticalWheelIfHit(JScrollPane scrollPane, int screenX, int screenY, int wheelRotation) {
        if (scrollPane == null || !scrollPane.isShowing() || wheelRotation == 0) {
            return false;
        }
        Point p = new Point(screenX, screenY);
        SwingUtilities.convertPointFromScreen(p, scrollPane);
        if (!scrollPane.contains(p)) {
            return false;
        }
        JScrollBar vsb = scrollPane.getVerticalScrollBar();
        if (vsb == null || !vsb.isVisible()) {
            return false;
        }
        int unit = Math.max(1, vsb.getUnitIncrement());
        int delta = wheelRotation * unit * 3;
        int v = vsb.getValue();
        int max = Math.max(vsb.getMinimum(), vsb.getMaximum() - vsb.getVisibleAmount());
        int newV = Math.max(vsb.getMinimum(), Math.min(max, v + delta));
        vsb.setValue(newV);
        return true;
    }
}
