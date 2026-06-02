package org.dce.ed.util;

import java.awt.Window;
import java.util.Locale;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.WNDENUMPROC;

/**
 * Applies Win32 click-through ({@link WinUser#WS_EX_TRANSPARENT}) to an AWT window and every descendant HWND.
 * Layered-window repaints (Swing translucency, {@code AlphaComposite.Clear}, glass panes) can clear the flag;
 * callers must re-stamp after paints while mouse pass-through is active.
 */
public final class WindowsNativeMousePassThrough {

    private WindowsNativeMousePassThrough() {
    }

    public static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os != null && os.toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * @return {@code false} if the window has no native peer yet
     */
    public static boolean applyToWindowTree(Window window, boolean enable) {
        if (window == null || !window.isDisplayable() || !isWindows()) {
            return false;
        }
        Pointer ptr;
        try {
            ptr = Native.getWindowPointer(window);
        } catch (Throwable ex) {
            return false;
        }
        if (ptr == null) {
            return false;
        }
        HWND root = new HWND(ptr);
        applyToHwndAndDescendants(root, enable);
        return true;
    }

    static void applyToHwndAndDescendants(HWND root, boolean enable) {
        if (root == null) {
            return;
        }
        applyNativePassThroughToHwnd(root, enable);
        WNDENUMPROC childProc = (hWnd, data) -> {
            applyNativePassThroughToHwnd(hWnd, enable);
            return true;
        };
        User32.INSTANCE.EnumChildWindows(root, childProc, null);
    }

    /**
     * Applies or clears {@link WinUser#WS_EX_TRANSPARENT} on one Win32 HWND and commits with {@code SetWindowPos}.
     */
    static void applyNativePassThroughToHwnd(HWND target, boolean enable) {
        if (target == null || Pointer.nativeValue(target.getPointer()) == 0L) {
            return;
        }
        int exStyle = User32.INSTANCE.GetWindowLong(target, WinUser.GWL_EXSTYLE);
        if (enable) {
            exStyle = exStyle | WinUser.WS_EX_LAYERED | WinUser.WS_EX_TRANSPARENT;
        } else {
            exStyle = exStyle | WinUser.WS_EX_LAYERED;
            exStyle = exStyle & ~WinUser.WS_EX_TRANSPARENT;
        }
        User32.INSTANCE.SetWindowLong(target, WinUser.GWL_EXSTYLE, exStyle);
        User32.INSTANCE.SetWindowPos(target, null, 0, 0, 0, 0,
                WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE | WinUser.SWP_NOZORDER | WinUser.SWP_FRAMECHANGED);
    }
}
