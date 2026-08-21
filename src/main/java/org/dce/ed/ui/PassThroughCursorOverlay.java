package org.dce.ed.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.JComponent;
import javax.swing.JWindow;

import org.dce.ed.MouseInteractionMode;
import org.dce.ed.util.WindowsNativeMousePassThrough;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;

/** A tiny visual cursor proxy that remains visible over alpha-zero click-through pixels. */
public final class PassThroughCursorOverlay extends JWindow {

    private static final int WS_EX_NOACTIVATE = 0x08000000;
    private static final int CURSOR_SIZE = 32;
    private static final long FALLBACK_MOVE_NANOS = 100_000_000L;
    private static final CopyOnWriteArrayList<PassThroughCursorOverlay> INSTANCES =
            new CopyOnWriteArrayList<>();
    private static final WindowsCursorImageLoader.CursorImage SYSTEM_ARROW =
            WindowsCursorImageLoader.loadStandardArrow();

    private volatile boolean tracking;
    private volatile HWND hwnd;
    private volatile long lastNativeMoveNanos;

    public PassThroughCursorOverlay(Window owner) {
        super(owner);
        setBackground(new Color(0, 0, 0, 0));
        setFocusableWindowState(false);
        setAutoRequestFocus(false);
        setAlwaysOnTop(true);
        BufferedImage image = SYSTEM_ARROW != null ? SYSTEM_ARROW.image() : null;
        setSize(image != null ? image.getWidth() : CURSOR_SIZE,
                image != null ? image.getHeight() : CURSOR_SIZE);
        setContentPane(new Arrow(image));
        INSTANCES.add(this);
    }

    /**
     * Updates this proxy from a host's already-resolved pointer state.
     * The pointer location is the arrow hot spot (top-left tip).
     */
    public void update(MouseInteractionMode mode, boolean pointerInsideHost, Point pointerOnScreen) {
        boolean show = WindowsNativeMousePassThrough.isWindows()
                && mode != null
                && mode.isPassThroughLike()
                && pointerInsideHost
                && pointerOnScreen != null;
        if (!show) {
            tracking = false;
            setVisible(false);
            return;
        }

        tracking = true;
        int x = pointerOnScreen.x - hotspotX();
        int y = pointerOnScreen.y - hotspotY();
        if (!isVisible()) {
            setLocation(x, y);
            setVisible(true);
            Pointer pointer = Native.getWindowPointer(this);
            hwnd = pointer != null ? new HWND(pointer) : null;
            applyNonActivatingWindowStyle();
        } else if (System.nanoTime() - lastNativeMoveNanos > FALLBACK_MOVE_NANOS) {
            setLocation(x, y);
        }
        WindowsNativeMousePassThrough.applyToWindowTree(this, true);
    }

    private void applyNonActivatingWindowStyle() {
        HWND target = hwnd;
        if (target == null) {
            return;
        }
        int style = User32.INSTANCE.GetWindowLong(target, WinUser.GWL_EXSTYLE);
        int next = style | WS_EX_NOACTIVATE;
        if (next != style) {
            User32.INSTANCE.SetWindowLong(target, WinUser.GWL_EXSTYLE, next);
            User32.INSTANCE.SetWindowPos(target, null, 0, 0, 0, 0,
                    WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE | WinUser.SWP_NOZORDER
                            | WinUser.SWP_NOACTIVATE | WinUser.SWP_FRAMECHANGED);
        }
    }

    /** Called directly from JNativeHook's mouse thread; bypasses Swing's event queue. */
    public static void dispatchNativeMouseMoved(int screenX, int screenY) {
        for (PassThroughCursorOverlay overlay : INSTANCES) {
            overlay.moveNative(screenX, screenY);
        }
    }

    private void moveNative(int screenX, int screenY) {
        HWND target = hwnd;
        if (!tracking || target == null) {
            return;
        }
        User32.INSTANCE.SetWindowPos(target, null,
                screenX - hotspotX(), screenY - hotspotY(), 0, 0,
                WinUser.SWP_NOSIZE | WinUser.SWP_NOZORDER | WinUser.SWP_NOACTIVATE);
        lastNativeMoveNanos = System.nanoTime();
    }

    private static int hotspotX() {
        return SYSTEM_ARROW != null ? SYSTEM_ARROW.hotspot().x : 0;
    }

    private static int hotspotY() {
        return SYSTEM_ARROW != null ? SYSTEM_ARROW.hotspot().y : 0;
    }

    @Override
    public void dispose() {
        tracking = false;
        hwnd = null;
        INSTANCES.remove(this);
        super.dispose();
    }

    private static final class Arrow extends JComponent {

        private static final Polygon FALLBACK_SHAPE = new Polygon(
                new int[] { 1, 1, 18, 12, 16, 12, 8 },
                new int[] { 1, 23, 17, 16, 25, 27, 17 },
                7);

        private final BufferedImage image;

        Arrow(BufferedImage image) {
            this.image = image;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                if (image != null) {
                    g.drawImage(image, 0, 0, null);
                    return;
                }
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Color.WHITE);
                g.fillPolygon(FALLBACK_SHAPE);
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(Color.BLACK);
                g.drawPolygon(FALLBACK_SHAPE);
            } finally {
                g.dispose();
            }
        }
    }
}
