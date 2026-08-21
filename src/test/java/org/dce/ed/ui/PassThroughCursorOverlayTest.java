package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.dce.ed.MouseInteractionMode;
import org.junit.jupiter.api.Test;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinUser;

class PassThroughCursorOverlayTest {

    private static final int WS_EX_NOACTIVATE = 0x08000000;
    private static final int WS_EX_TOOLWINDOW = 0x00000080;

    @Test
    void visibleCursorWindowRemainsNativeMousePassThrough() throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"));
        assumeTrue(!GraphicsEnvironment.isHeadless());

        Frame owner = new Frame();
        PassThroughCursorOverlay[] overlayRef = new PassThroughCursorOverlay[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                owner.setBounds(-20_000, -20_000, 200, 200);
                owner.setVisible(true);
                PassThroughCursorOverlay overlay = new PassThroughCursorOverlay(owner);
                overlayRef[0] = overlay;
                overlay.update(MouseInteractionMode.SELECTIVE, true, new Point(-19_950, -19_950));
            });

            PassThroughCursorOverlay overlay = overlayRef[0];
            assertTrue(overlay.isVisible(), "cursor proxy must be visible inside a selective overlay");
            Pointer pointer = Native.getWindowPointer(overlay);
            int style = User32.INSTANCE.GetWindowLong(new HWND(pointer), WinUser.GWL_EXSTYLE);
            assertNotEquals(0, style & WinUser.WS_EX_TRANSPARENT,
                    "cursor proxy must never consume mouse input");
            assertNotEquals(0, style & WS_EX_NOACTIVATE,
                    "cursor proxy must never become the foreground window");
            assertEquals(0, style & WS_EX_TOOLWINDOW,
                    "cursor proxy must not become a tool window that can intercept overlay controls");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (overlayRef[0] != null) {
                    overlayRef[0].dispose();
                }
                owner.dispose();
            });
        }
    }

    @Test
    void nativeMouseMotionMovesVisibleCursorWindowImmediately() throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"));
        assumeTrue(!GraphicsEnvironment.isHeadless());

        Frame owner = new Frame();
        PassThroughCursorOverlay[] overlayRef = new PassThroughCursorOverlay[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                owner.setBounds(-20_000, -20_000, 200, 200);
                owner.setVisible(true);
                PassThroughCursorOverlay overlay = new PassThroughCursorOverlay(owner);
                overlayRef[0] = overlay;
                overlay.update(MouseInteractionMode.SELECTIVE, true, new Point(-19_950, -19_950));
            });

            PassThroughCursorOverlay.dispatchNativeMouseMoved(-19_925, -19_920);

            Pointer pointer = Native.getWindowPointer(overlayRef[0]);
            RECT bounds = new RECT();
            assertTrue(User32.INSTANCE.GetWindowRect(new HWND(pointer), bounds));
            assertTrue(bounds.left == -19_925 && bounds.top == -19_920,
                    "native motion must move the proxy synchronously, was " + bounds);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (overlayRef[0] != null) {
                    overlayRef[0].dispose();
                }
                owner.dispose();
            });
        }
    }

    @Test
    void clickReachesMovedOwnerThroughVisibleCursorProxy() throws Exception {
        Point originalPointer = MouseInfo.getPointerInfo().getLocation();
        JFrame owner = new JFrame();
        PassThroughCursorOverlay[] overlayRef = new PassThroughCursorOverlay[1];
        AtomicInteger clicks = new AtomicInteger();
        Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(() -> {
                JButton button = new JButton("target");
                button.addActionListener(e -> clicks.incrementAndGet());
                owner.setContentPane(button);
                owner.setBounds(80, 80, 240, 120);
                owner.setVisible(true);
                PassThroughCursorOverlay overlay = new PassThroughCursorOverlay(owner);
                overlayRef[0] = overlay;
                owner.setLocation(300, 200);
                overlay.update(MouseInteractionMode.SELECTIVE, true, new Point(420, 260));
            });

            robot.mouseMove(420, 260);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            Thread.sleep(100);

            assertEquals(1, clicks.get(), "the visible cursor proxy must not swallow the owner's click");
        } finally {
            robot.mouseMove(originalPointer.x, originalPointer.y);
            SwingUtilities.invokeAndWait(() -> {
                if (overlayRef[0] != null) {
                    overlayRef[0].dispose();
                }
                owner.dispose();
            });
        }
    }
}
