package org.dce.ed.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;

/**
 * Edge/corner drag resize for undecorated windows (Preferences, floating tab docks, and similar).
 * Safe to call {@link #install(Window)} again after content is reparented — listeners are not duplicated.
 */
public final class WindowEdgeResizeSupport {

    private static final int DEFAULT_BORDER = 6;
    private static final String HANDLER_KEY = "edo.windowEdgeResizeHandler";

    private WindowEdgeResizeSupport() {
    }

    public static void install(Window window) {
        install(window, DEFAULT_BORDER);
    }

    public static void install(Window window, int borderDragThickness) {
        if (window == null) {
            return;
        }
        if (window instanceof java.awt.Frame frame) {
            frame.setResizable(true);
        } else if (window instanceof java.awt.Dialog dialog) {
            dialog.setResizable(true);
        }
        Handler handler = existingHandler(window);
        if (handler == null) {
            handler = new Handler(window, Math.max(4, borderDragThickness));
            storeHandler(window, handler);
        } else {
            handler.border = Math.max(4, borderDragThickness);
        }
        Component root = rootComponent(window);
        installRecursive(root, handler);
        if (root != window) {
            installOn(window, handler);
        }
    }

    public static boolean isInstalledFor(Window window) {
        return window != null && existingHandler(window) != null;
    }

    private static Handler existingHandler(Window window) {
        Component root = rootComponent(window);
        if (root instanceof JComponent jc) {
            Object v = jc.getClientProperty(HANDLER_KEY);
            if (v instanceof Handler h && h.window == window) {
                return h;
            }
        }
        return null;
    }

    private static void storeHandler(Window window, Handler handler) {
        Component root = rootComponent(window);
        if (root instanceof JComponent jc) {
            jc.putClientProperty(HANDLER_KEY, handler);
        }
    }

    private static Component rootComponent(Window window) {
        if (window instanceof RootPaneContainer rpc && rpc.getRootPane() != null) {
            return rpc.getRootPane();
        }
        return window;
    }

    private static void installRecursive(Component c, Handler handler) {
        if (c == null) {
            return;
        }
        installOn(c, handler);
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                installRecursive(child, handler);
            }
        }
    }

    private static void installOn(Component c, Handler handler) {
        if (c instanceof JComponent jc) {
            Object prev = jc.getClientProperty(HANDLER_KEY);
            if (prev == handler) {
                return;
            }
            if (prev instanceof Handler old) {
                c.removeMouseListener(old);
                c.removeMouseMotionListener(old);
            }
            c.addMouseListener(handler);
            c.addMouseMotionListener(handler);
            jc.putClientProperty(HANDLER_KEY, handler);
            return;
        }
        // Non-JComponent (e.g. Window): avoid stacking duplicates by removing first.
        c.removeMouseListener(handler);
        c.removeMouseMotionListener(handler);
        c.addMouseListener(handler);
        c.addMouseMotionListener(handler);
    }

    private static final class Handler extends MouseAdapter {
        private final Window window;
        private int border;
        private int dragCursor = Cursor.DEFAULT_CURSOR;
        private boolean dragging;
        private int pressScreenX;
        private int pressScreenY;
        private int startX;
        private int startY;
        private int startW;
        private int startH;

        Handler(Window window, int border) {
            this.window = window;
            this.border = border;
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            if (dragging) {
                return;
            }
            window.setCursor(Cursor.getPredefinedCursor(calcCursor(e)));
        }

        @Override
        public void mouseExited(MouseEvent e) {
            if (!dragging) {
                window.setCursor(Cursor.getDefaultCursor());
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            dragCursor = calcCursor(e);
            if (dragCursor == Cursor.DEFAULT_CURSOR || !SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            dragging = true;
            pressScreenX = e.getXOnScreen();
            pressScreenY = e.getYOnScreen();
            startX = window.getX();
            startY = window.getY();
            startW = window.getWidth();
            startH = window.getHeight();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            dragging = false;
            window.setCursor(Cursor.getDefaultCursor());
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (!dragging) {
                return;
            }
            int dx = e.getXOnScreen() - pressScreenX;
            int dy = e.getYOnScreen() - pressScreenY;
            int newX = startX;
            int newY = startY;
            int newW = startW;
            int newH = startH;
            switch (dragCursor) {
                case Cursor.E_RESIZE_CURSOR -> newW = startW + dx;
                case Cursor.S_RESIZE_CURSOR -> newH = startH + dy;
                case Cursor.SE_RESIZE_CURSOR -> {
                    newW = startW + dx;
                    newH = startH + dy;
                }
                case Cursor.W_RESIZE_CURSOR -> {
                    newX = startX + dx;
                    newW = startW - dx;
                }
                case Cursor.N_RESIZE_CURSOR -> {
                    newY = startY + dy;
                    newH = startH - dy;
                }
                case Cursor.NW_RESIZE_CURSOR -> {
                    newX = startX + dx;
                    newW = startW - dx;
                    newY = startY + dy;
                    newH = startH - dy;
                }
                case Cursor.NE_RESIZE_CURSOR -> {
                    newY = startY + dy;
                    newH = startH - dy;
                    newW = startW + dx;
                }
                case Cursor.SW_RESIZE_CURSOR -> {
                    newX = startX + dx;
                    newW = startW - dx;
                    newH = startH + dy;
                }
                default -> {
                }
            }
            Dimension min = window.getMinimumSize();
            int minW = Math.max(200, min != null ? min.width : 200);
            int minH = Math.max(160, min != null ? min.height : 160);
            if (newW < minW) {
                int diff = minW - newW;
                if (dragCursor == Cursor.W_RESIZE_CURSOR
                        || dragCursor == Cursor.NW_RESIZE_CURSOR
                        || dragCursor == Cursor.SW_RESIZE_CURSOR) {
                    newX -= diff;
                }
                newW = minW;
            }
            if (newH < minH) {
                int diff = minH - newH;
                if (dragCursor == Cursor.N_RESIZE_CURSOR
                        || dragCursor == Cursor.NE_RESIZE_CURSOR
                        || dragCursor == Cursor.NW_RESIZE_CURSOR) {
                    newY -= diff;
                }
                newH = minH;
            }
            window.setBounds(newX, newY, newW, newH);
        }

        private int calcCursor(MouseEvent e) {
            Component src = (Component) e.getSource();
            Component root = rootComponent(window);
            Point p = SwingUtilities.convertPoint(src, e.getPoint(), root);
            int x = p.x;
            int y = p.y;
            int w = root.getWidth();
            int h = root.getHeight();
            boolean left = x < border;
            boolean right = x >= w - border;
            boolean top = y < border;
            boolean bottom = y >= h - border;
            if (top && left) {
                return Cursor.NW_RESIZE_CURSOR;
            }
            if (top && right) {
                return Cursor.NE_RESIZE_CURSOR;
            }
            if (bottom && left) {
                return Cursor.SW_RESIZE_CURSOR;
            }
            if (bottom && right) {
                return Cursor.SE_RESIZE_CURSOR;
            }
            if (left) {
                return Cursor.W_RESIZE_CURSOR;
            }
            if (right) {
                return Cursor.E_RESIZE_CURSOR;
            }
            if (top) {
                return Cursor.N_RESIZE_CURSOR;
            }
            if (bottom) {
                return Cursor.S_RESIZE_CURSOR;
            }
            return Cursor.DEFAULT_CURSOR;
        }
    }
}
