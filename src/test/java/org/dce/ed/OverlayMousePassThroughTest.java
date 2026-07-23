package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.WNDENUMPROC;

/**
 * Verifies Win32 {@link WinUser#WS_EX_TRANSPARENT} is applied for mouse pass-through (MPT).
 * Requires a display and Windows; skipped headless or on non-Windows OS.
 */
class OverlayMousePassThroughTest {

    static {
        TestEnvironment.ensureTestIsolation();
    }

    private boolean savedMousePassThrough;

    @BeforeEach
    void assumeWindowsDisplayAndSavePrefs() {
        assumeTrue(!GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadless(),
                "Requires a display");
        String os = System.getProperty("os.name", "").toLowerCase();
        assumeTrue(os.contains("win"), "Win32 extended styles are Windows-only");
        savedMousePassThrough = OverlayPreferences.isOverlayMousePassThroughToGame();
    }

    @AfterEach
    void restorePrefs() {
        OverlayPreferences.setOverlayMousePassThroughToGame(savedMousePassThrough);
    }

    /** Realize HWND off-screen so tests do not flash over the user's desktop. */
    private static void showOffScreen(OverlayFrame frame, int width, int height) {
        frame.setBounds(-20_000, -20_000, width, height);
        frame.setVisible(true);
    }

    @Test
    void wsExTransparentSetWhenMousePassThroughEnabled() throws Exception {
        OverlayContentPanel content = new OverlayContentPanel(() -> true);
        OverlayFrame[] frameRef = new OverlayFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            OverlayFrame frame = new OverlayFrame(content);
            frameRef[0] = frame;
            frame.setPassThroughEnabled(true, false);
            showOffScreen(frame, 320, 240);
        });

        try {
            int exStyle = readExtendedStyle(frameRef[0]);
            assertNotEquals(0, exStyle & WinUser.WS_EX_TRANSPARENT,
                    "WS_EX_TRANSPARENT must be set while mouse pass-through is enabled");

            SwingUtilities.invokeAndWait(() -> frameRef[0].setPassThroughEnabled(false, false));
            int disabledStyle = readExtendedStyle(frameRef[0]);
            assertEquals(0, disabledStyle & WinUser.WS_EX_TRANSPARENT,
                    "WS_EX_TRANSPARENT must be cleared when mouse pass-through is disabled");

            SwingUtilities.invokeAndWait(() -> {
                frameRef[0].setPassThroughEnabled(true, false);
                frameRef[0].setBounds(-20_000, -20_000, 340, 260);
                frameRef[0].reapplyNativeMousePassThroughIfEnabled();
            });
            int afterBounds = readExtendedStyle(frameRef[0]);
            assertNotEquals(0, afterBounds & WinUser.WS_EX_TRANSPARENT,
                    "WS_EX_TRANSPARENT must be re-applied after setBounds when MPT is on");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frameRef[0] != null) {
                    frameRef[0].setVisible(false);
                    frameRef[0].dispose();
                }
            });
        }
    }

    @Test
    void wsExTransparentSurvivesRepaintBurst() throws Exception {
        OverlayContentPanel content = new OverlayContentPanel(() -> true);
        OverlayFrame[] frameRef = new OverlayFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            OverlayFrame frame = new OverlayFrame(content);
            frameRef[0] = frame;
            frame.setPassThroughEnabled(true, false);
            showOffScreen(frame, 320, 240);
        });

        try {
            SwingUtilities.invokeAndWait(() -> {
                OverlayFrame frame = frameRef[0];
                for (int i = 0; i < 40; i++) {
                    frame.repaint();
                    frame.revalidate();
                }
            });
            Thread.sleep(200);
            int style = readExtendedStyle(frameRef[0]);
            assertNotEquals(0, style & WinUser.WS_EX_TRANSPARENT,
                    "WS_EX_TRANSPARENT must survive repaint/revalidate burst");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frameRef[0] != null) {
                    frameRef[0].setVisible(false);
                    frameRef[0].dispose();
                }
            });
        }
    }

    @Test
    void wsExTransparentSurvivesCrosshairRepaintBurst() throws Exception {
        OverlayContentPanel content = new OverlayContentPanel(() -> true);
        OverlayFrame[] frameRef = new OverlayFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            OverlayFrame frame = new OverlayFrame(content);
            frameRef[0] = frame;
            frame.setPassThroughEnabled(true, false);
            showOffScreen(frame, 320, 240);
        });

        try {
            SwingUtilities.invokeAndWait(() -> {
                OverlayFrame frame = frameRef[0];
                for (int i = 0; i < 80; i++) {
                    frame.repaint();
                }
            });
            Thread.sleep(CROSSHAIR_POLL_MS * 4L);
            SwingUtilities.invokeAndWait(() -> frameRef[0].reapplyNativeMousePassThroughIfEnabled());
            int style = readExtendedStyle(frameRef[0]);
            assertNotEquals(0, style & WinUser.WS_EX_TRANSPARENT,
                    "WS_EX_TRANSPARENT must survive high-frequency layered repaints (crosshair glass pane)");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frameRef[0] != null) {
                    frameRef[0].setVisible(false);
                    frameRef[0].dispose();
                }
            });
        }
    }

    private static final int CROSSHAIR_POLL_MS = 8;

    /**
     * Full {@link OverlayFrame} with content can expose child HWNDs (e.g. SunAwtCanvas) that steal clicks
     * unless they also receive {@link WinUser#WS_EX_TRANSPARENT}.
     */
    @Test
    void allNativeDescendantHwndsTransparentWhenMousePassThroughEnabled() throws Exception {
        OverlayContentPanel content = new OverlayContentPanel(() -> true);
        OverlayFrame[] frameRef = new OverlayFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            OverlayFrame frame = new OverlayFrame(content);
            frameRef[0] = frame;
            frame.setPassThroughEnabled(true, false);
            showOffScreen(frame, 400, 300);
        });

        try {
            List<HWND> descendants = collectDescendantHwnds(frameRef[0]);
            assumeTrue(!descendants.isEmpty(), "Need at least the root HWND");
            for (HWND h : descendants) {
                int style = User32.INSTANCE.GetWindowLong(h, WinUser.GWL_EXSTYLE);
                assertNotEquals(0, style & WinUser.WS_EX_TRANSPARENT,
                        "WS_EX_TRANSPARENT required on every descendant HWND (found opaque child)");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frameRef[0] != null) {
                    frameRef[0].setVisible(false);
                    frameRef[0].dispose();
                }
            });
        }
    }

    private static List<HWND> collectDescendantHwnds(OverlayFrame frame) throws Exception {
        final List<HWND> out = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            Pointer ptr = Native.getWindowPointer(frame);
            HWND root = new HWND(ptr);
            out.add(root);
            WNDENUMPROC proc = (hWnd, data) -> {
                out.add(hWnd);
                return true;
            };
            User32.INSTANCE.EnumChildWindows(root, proc, null);
        });
        return out;
    }

    private static int readExtendedStyle(OverlayFrame frame) throws Exception {
        final int[] style = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            Pointer ptr = Native.getWindowPointer(frame);
            HWND hwnd = new HWND(ptr);
            style[0] = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE);
        });
        return style[0];
    }
}

