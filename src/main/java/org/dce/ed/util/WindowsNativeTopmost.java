package org.dce.ed.util;

import java.awt.Dialog;
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
            // Changing an owner's topmost band dismisses active Swing popups in owned
            // dialogs (notably Preferences table combo editors). Defer until they close.
            if (hasVisibleOwnedWindow(window)) {
                return;
            }
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
                // AWT caches this property. Refresh both the peer and native z-order state;
                // visible owned dialogs were excluded above so their popups stay intact.
                window.setAlwaysOnTop(!topmost);
                window.setAlwaysOnTop(topmost);
            }
        } catch (Throwable ignored) {
            // Best effort: a window may be disposed while focus synchronization is running.
        }
    }

    private static boolean hasVisibleOwnedWindow(Window window) {
        for (Window owned : window.getOwnedWindows()) {
            if (owned instanceof Dialog && owned.isShowing()) {
                return true;
            }
        }
        return false;
    }
}
