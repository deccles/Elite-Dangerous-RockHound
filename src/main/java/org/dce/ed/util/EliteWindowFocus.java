package org.dce.ed.util;

import java.util.Locale;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

/** Detects whether Elite Dangerous is the foreground window on Windows. */
public final class EliteWindowFocus {

    private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;

    private EliteWindowFocus() {
    }

    public static boolean isEliteForeground() {
        HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        return isEliteGameWindow(hwnd);
    }

    private static boolean isEliteGameWindow(HWND hwnd) {
        if (hwnd == null || !User32.INSTANCE.IsWindowVisible(hwnd)) {
            return false;
        }
        String image = processImageName(hwnd);
        if (image.isEmpty()) {
            return false;
        }
        String lower = image.toLowerCase(Locale.ROOT);
        return lower.endsWith("\\elitedangerous64.exe")
                || lower.endsWith("\\elitedangerous32.exe");
    }

    private static String processImageName(HWND hwnd) {
        IntByReference processId = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, processId);
        int pid = processId.getValue();
        if (pid == 0) {
            return "";
        }
        WinNT.HANDLE process = Kernel32.INSTANCE.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid);
        if (process == null) {
            return "";
        }
        try {
            char[] buffer = new char[1024];
            IntByReference size = new IntByReference(buffer.length);
            if (!Kernel32.INSTANCE.QueryFullProcessImageName(process, 0, buffer, size)) {
                return "";
            }
            return Native.toString(buffer);
        } finally {
            Kernel32.INSTANCE.CloseHandle(process);
        }
    }
}
