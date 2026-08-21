package org.dce.ed.util;

import java.awt.Window;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;

/** Reconciles Swing's cached always-on-top state with the actual Win32 z-order band. */
public final class WindowsNativeTopmost {

    private static final int WS_EX_TOPMOST = 0x00000008;

    private WindowsNativeTopmost() {
    }

    public static void apply(Window window, boolean topmost) {
        if (window == null || !window.isDisplayable()) {
            return;
        }
        try {
            boolean javaTopmost = window.isAlwaysOnTop();
            if (javaTopmost != topmost) {
                window.setAlwaysOnTop(topmost);
            }
            if (!WindowsNativeMousePassThrough.isWindows()) {
                return;
            }
            Pointer pointer = Native.getWindowPointer(window);
            if (pointer == null || Pointer.nativeValue(pointer) == 0L) {
                return;
            }
            HWND hwnd = new HWND(pointer);
            boolean nativeTopmost = (User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
                    & WS_EX_TOPMOST) != 0;
            if (nativeTopmost != topmost) {
                // AWT caches this property. Force a peer update when its cache disagrees
                // with Windows instead of letting setAlwaysOnTop(desired) be a no-op.
                window.setAlwaysOnTop(!topmost);
                window.setAlwaysOnTop(topmost);
            }
        } catch (Throwable ignored) {
            // Best effort: a window may be disposed while focus synchronization is running.
        }
    }
}
