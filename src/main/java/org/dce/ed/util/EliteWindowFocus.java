package org.dce.ed.util;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.Locale;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

/**
 * Detects and activates the Elite Dangerous game window on Windows.
 * <p>
 * Technique mirrors RoboHound's proven focus path (AttachThreadInput between the
 * <em>foreground thread</em> and Elite's thread — not process IDs), but is standalone EDO code.
 */
public final class EliteWindowFocus {

    private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;

    private EliteWindowFocus() {
    }

    public static boolean isEliteForeground() {
        HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        return isEliteGameWindow(hwnd);
    }

    /** Finds a visible Elite Dangerous main window, or {@code null}. */
    public static HWND findEliteWindow() {
        HWND[] found = new HWND[1];
        User32.INSTANCE.EnumWindows((hWnd, data) -> {
            if (isEliteGameWindow(hWnd)) {
                found[0] = hWnd;
                return false;
            }
            return true;
        }, null);
        return found[0];
    }

    public static boolean isGameRunning() {
        return findEliteWindow() != null;
    }

    /** Base name of the foreground process, e.g. {@code EliteDangerous64.exe}. */
    public static String foregroundProcessBaseName() {
        HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null) {
            return "(none)";
        }
        String image = processImageName(hwnd);
        if (image.isEmpty()) {
            return "(unknown)";
        }
        int slash = Math.max(image.lastIndexOf('\\'), image.lastIndexOf('/'));
        return slash >= 0 ? image.substring(slash + 1) : image;
    }

    public static String foregroundDescription() {
        HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null) {
            return "(none)";
        }
        return windowTitle(hwnd) + " [" + processImageName(hwnd) + "]";
    }

    /**
     * Brings Elite to the foreground when possible.
     * Prefer calling from the EDT right after a user click.
     */
    public static boolean focusEliteWindow() {
        if (isEliteForeground()) {
            return true;
        }
        return tryBringToForeground();
    }

    /**
     * Same approach as RoboHound: attach the current foreground window's thread to Elite's
     * thread, then {@code SetForegroundWindow}.
     */
    public static boolean tryBringToForeground() {
        HWND hwnd = findEliteWindow();
        if (hwnd == null) {
            System.out.println("EDO auto-trade: Elite window not found");
            return false;
        }
        User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);

        HWND previous = User32.INSTANCE.GetForegroundWindow();
        if (previous != null && !previous.equals(hwnd)) {
            // Return value is the *thread* id; second arg (null) skips process id out-param.
            int previousThread = User32.INSTANCE.GetWindowThreadProcessId(previous, null);
            int eliteThread = User32.INSTANCE.GetWindowThreadProcessId(hwnd, null);
            if (previousThread != 0 && eliteThread != 0 && previousThread != eliteThread) {
                DWORD previousThreadId = new DWORD(previousThread);
                DWORD eliteThreadId = new DWORD(eliteThread);
                User32.INSTANCE.AttachThreadInput(previousThreadId, eliteThreadId, true);
                try {
                    User32.INSTANCE.SetForegroundWindow(hwnd);
                } finally {
                    User32.INSTANCE.AttachThreadInput(previousThreadId, eliteThreadId, false);
                }
                if (isEliteForeground()) {
                    System.out.println("EDO auto-trade: focus via AttachThreadInput -> "
                            + "true; foreground=" + foregroundProcessBaseName());
                    return true;
                }
            }
        }
        User32.INSTANCE.SetForegroundWindow(hwnd);
        if (isEliteForeground()) {
            System.out.println("EDO auto-trade: focus via SetForegroundWindow -> "
                    + "true; foreground=" + foregroundProcessBaseName());
            return true;
        }
        boolean ok = altKeySetForeground(hwnd);
        System.out.println("EDO auto-trade: focus via Alt-key trick -> "
                + ok + "; foreground=" + foregroundProcessBaseName());
        return ok;
    }

    /**
     * Taps a real Alt key via {@link Robot} before {@code SetForegroundWindow}. Windows lifts the
     * foreground lock for the process that generated the last input, so the call is allowed.
     */
    private static boolean altKeySetForeground(HWND hwnd) {
        Robot robot;
        try {
            robot = new Robot();
        } catch (AWTException e) {
            return false;
        }
        robot.keyPress(KeyEvent.VK_ALT);
        try {
            User32.INSTANCE.SetForegroundWindow(hwnd);
        } finally {
            robot.keyRelease(KeyEvent.VK_ALT);
        }
        return isEliteForeground();
    }

    /**
     * Polls until Elite is foreground, calling {@link #tryBringToForeground()} between checks.
     */
    public static boolean waitForForeground(int maxAttempts, long retryDelayMs) throws InterruptedException {
        int attempts = Math.max(1, maxAttempts);
        for (int attempt = 0; attempt < attempts; attempt++) {
            if (isEliteForeground()) {
                return true;
            }
            tryBringToForeground();
            if (isEliteForeground()) {
                return true;
            }
            if (attempt + 1 < attempts && retryDelayMs > 0) {
                Thread.sleep(retryDelayMs);
            }
        }
        return isEliteForeground();
    }

    /**
     * Polls until the user focuses Elite — does <b>not</b> call {@link #tryBringToForeground()}.
     * Use when auto-focus is unreliable and the user must click the game themselves.
     */
    public static boolean waitUntilUserFocusesElite(long timeoutMs, long pollIntervalMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(
                Math.max(1_000L, timeoutMs));
        long interval = Math.max(50L, pollIntervalMs);
        while (System.nanoTime() < deadline) {
            if (isEliteForeground()) {
                return true;
            }
            Thread.sleep(interval);
        }
        return isEliteForeground();
    }

    static boolean isEliteGameWindow(HWND hwnd) {
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

    private static String windowTitle(HWND hwnd) {
        char[] title = new char[512];
        int len = User32.INSTANCE.GetWindowText(hwnd, title, title.length);
        if (len <= 0) {
            return "(untitled)";
        }
        return new String(title, 0, len);
    }
}
