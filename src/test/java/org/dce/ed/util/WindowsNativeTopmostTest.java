package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.lang.reflect.Field;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;
import sun.misc.Unsafe;

@EnabledOnOs(OS.WINDOWS)
class WindowsNativeTopmostTest {

    @Test
    void restoresNativeTopmostWhenJavaStillBelievesWindowIsTopmost() throws Exception {
        if (GraphicsEnvironment.isHeadless()) return;

        JFrame[] holder = new JFrame[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                JFrame frame = new JFrame("native-topmost-repair-test");
                frame.setSize(180, 100);
                frame.setAlwaysOnTop(true);
                frame.setVisible(true);
                holder[0] = frame;
            });

            HWND[] hwndHolder = new HWND[1];
            SwingUtilities.invokeAndWait(() -> {
                holder[0].setAlwaysOnTop(false);
                hwndHolder[0] = new HWND(Native.getWindowPointer(holder[0]));
            });
            HWND hwnd = hwndHolder[0];
            assertTrue(User32.INSTANCE.IsWindow(hwnd), "JNA must return a valid top-level HWND");
            setCachedAlwaysOnTop(holder[0], true);

            assertTrue(holder[0].isAlwaysOnTop(), "Java should retain its stale cached state");
            assertFalse(isNativeTopmost(hwnd), "the test must reproduce the Java/native mismatch");

            SwingUtilities.invokeAndWait(() -> WindowsNativeTopmost.apply(holder[0], true));

            assertTrue(isNativeTopmost(hwnd), "reconciliation must repair the native topmost flag");
        } finally {
            if (holder[0] != null) {
                SwingUtilities.invokeAndWait(holder[0]::dispose);
            }
        }
    }

    private static boolean isNativeTopmost(HWND hwnd) {
        int style = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE);
        return (style & 0x00000008) != 0;
    }

    private static void setCachedAlwaysOnTop(Window window, boolean value) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Field alwaysOnTop = Window.class.getDeclaredField("alwaysOnTop");
        unsafe.putBoolean(window, unsafe.objectFieldOffset(alwaysOnTop), value);
    }
}
