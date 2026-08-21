package org.dce.ed.ui;

import java.awt.Component;
import java.awt.IllegalComponentStateException;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

/**
 * Drives Swing tooltips from global pointer position while mouse pass-through is active (Swing does not receive
 * {@code MOUSE_MOVED} on the overlay). Cell renderers and {@code getToolTipText(MouseEvent)} overrides keep working
 * unchanged.
 */
public final class PassThroughTooltipSupport {

    private static JComponent lastTipComponent;
    private static Point lastTipScreenPoint;

    private PassThroughTooltipSupport() {
    }

    /**
     * @param root overlay content root (e.g. frame content pane)
     * @param passThroughActive when false, any visible pass-through tooltip is dismissed
     */
    public static void poll(Component root, boolean passThroughActive) {
        if (!passThroughActive) {
            clear();
            return;
        }
        if (root == null || !root.isShowing()) {
            clear();
            return;
        }
        java.awt.PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        if (pointerInfo == null) {
            clear();
            return;
        }
        Point screen = pointerInfo.getLocation();
        try {
            Point origin = root.getLocationOnScreen();
            if (!new Rectangle(origin.x, origin.y, root.getWidth(), root.getHeight()).contains(screen)) {
                clear();
                return;
            }
        } catch (IllegalComponentStateException ex) {
            clear();
            return;
        }

        Point local = new Point(screen);
        SwingUtilities.convertPointFromScreen(local, root);
        JComponent target = resolveTooltipComponent(root, local);
        if (target == null || !target.isShowing()) {
            clear();
            return;
        }
        if (!shouldDispatchMouseMoved(lastTipComponent, lastTipScreenPoint, target, screen)) {
            return;
        }

        Point targetLocal = new Point(screen);
        SwingUtilities.convertPointFromScreen(targetLocal, target);
        MouseEvent moved = new MouseEvent(
                target,
                MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(),
                0,
                targetLocal.x,
                targetLocal.y,
                screen.x,
                screen.y,
                0,
                false,
                MouseEvent.NOBUTTON);
        ToolTipManager.sharedInstance().mouseMoved(moved);
        lastTipComponent = target;
        lastTipScreenPoint = new Point(screen);
    }

    static boolean shouldDispatchMouseMoved(JComponent previousTarget, Point previousScreenPoint,
                                            JComponent target, Point screenPoint) {
        return previousTarget != target
                || previousScreenPoint == null
                || screenPoint == null
                || !previousScreenPoint.equals(screenPoint);
    }

    /**
     * {@link ToolTipManager} casts the event source to {@link JComponent}. Deep hits can be plain AWT
     * containers (e.g. {@link javax.swing.plaf.basic.BasicSplitPaneDivider}), so walk up to a Swing
     * component — preferring tables for cell tip text.
     */
    private static JComponent resolveTooltipComponent(Component root, Point localOnRoot) {
        Component hit = SwingUtilities.getDeepestComponentAt(root, localOnRoot.x, localOnRoot.y);
        if (hit == null) {
            return null;
        }
        if (hit instanceof JTable table) {
            return table;
        }
        for (Component c = hit; c != null && c != root; c = c.getParent()) {
            if (c instanceof JTable table) {
                return table;
            }
        }
        for (Component c = hit; c != null; c = c.getParent()) {
            if (c instanceof JComponent jc) {
                return jc;
            }
        }
        return null;
    }

    /** Hide any tooltip shown by {@link #poll(Component, boolean)}. */
    public static void clear() {
        if (lastTipComponent == null) {
            return;
        }
        MouseEvent exited = new MouseEvent(
                lastTipComponent,
                MouseEvent.MOUSE_EXITED,
                System.currentTimeMillis(),
                0,
                0,
                0,
                0,
                false);
        ToolTipManager.sharedInstance().mouseExited(exited);
        lastTipComponent = null;
        lastTipScreenPoint = null;
    }
}
