package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JDialog;
import javax.swing.JComboBox;
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
    void repairDoesNotCloseComboPopupInOwnedDialog() throws Exception {
        if (GraphicsEnvironment.isHeadless()) return;

        JFrame[] owner = new JFrame[1];
        JDialog[] dialog = new JDialog[1];
        JComboBox<String>[] combo = new JComboBox[1];
        boolean[] popupVisibleAfterRepair = new boolean[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                JFrame frame = new JFrame("native-topmost-popup-owner-test");
                frame.setSize(240, 140);
                frame.setAlwaysOnTop(true);
                frame.setVisible(true);

                frame.setAlwaysOnTop(false);
                try {
                    setCachedAlwaysOnTop(frame, true);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }

                JDialog preferences = new JDialog(frame, "Preferences", false);
                JComboBox<String> choices = new JComboBox<>(new String[] { "Trigger", "Tab" });
                preferences.add(choices);
                preferences.pack();
                preferences.setVisible(true);
                choices.showPopup();

                owner[0] = frame;
                dialog[0] = preferences;
                combo[0] = choices;
                WindowsNativeTopmost.apply(frame, true);
                popupVisibleAfterRepair[0] = choices.isPopupVisible();
            });

            HWND hwnd = new HWND(Native.getWindowPointer(owner[0]));
            assertTrue(dialog[0].isShowing(), "the Preferences-like dialog must be showing");
            assertTrue(Arrays.asList(owner[0].getOwnedWindows()).contains(dialog[0]),
                    "the Preferences-like dialog must be owned by the repaired window");

            assertTrue(popupVisibleAfterRepair[0], "reconciliation must not close the owned dialog popup");
            assertFalse(isNativeTopmost(hwnd), "repair must be deferred while an owned dialog is visible");

            SwingUtilities.invokeAndWait(dialog[0]::dispose);
            SwingUtilities.invokeAndWait(() -> WindowsNativeTopmost.apply(owner[0], true));

            assertTrue(isNativeTopmost(hwnd), "reconciliation must resume after the owned dialog closes");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (dialog[0] != null) dialog[0].dispose();
                if (owner[0] != null) owner[0].dispose();
            });
        }
    }

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

            List<Boolean> javaStateChanges = new ArrayList<>();
            holder[0].addPropertyChangeListener("alwaysOnTop",
                    event -> javaStateChanges.add((Boolean) event.getNewValue()));

            SwingUtilities.invokeAndWait(() -> WindowsNativeTopmost.apply(holder[0], true));

            assertTrue(isNativeTopmost(hwnd), "reconciliation must repair the native topmost flag");
            assertTrue(holder[0].isAlwaysOnTop(), "Java and Windows must agree after reconciliation");
            org.junit.jupiter.api.Assertions.assertEquals(List.of(), javaStateChanges,
                    "native repair must not toggle AWT state because that can activate the window");
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
