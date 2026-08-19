package org.dce.ed.util;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinDef.WORD;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * Detects and activates the Elite Dangerous game window on Windows.
 * <p>
 * Technique mirrors RoboHound's proven focus path (AttachThreadInput between the
 * <em>foreground thread</em> and Elite's thread — not process IDs), but is standalone EDO code.
 * <p>
 * Avoids the Alt-key SetForeground trick: injecting Alt while stealing focus can leave Elite
 * looking focused while keyboard/joystick input is dead until a real focus cycle (click away
 * and back). Prefer {@code SwitchToThisWindow}, then a Ctrl tap as last resort, and always
 * flush stuck modifiers afterward.
 */
public final class EliteWindowFocus {

    private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;
    private static final int VK_LSHIFT = 0xA0;
    private static final int VK_RSHIFT = 0xA1;
    private static final int VK_LCONTROL = 0xA2;
    private static final int VK_RCONTROL = 0xA3;
    private static final int VK_LMENU = 0xA4;
    private static final int VK_RMENU = 0xA5;
    private static final int VK_LWIN = 0x5B;
    private static final int VK_RWIN = 0x5C;
    private static final long ACTIVATION_TIMEOUT_MS = 750L;
    private static final long ACTIVATION_POLL_MS = 20L;

    /** User32 calls not exposed by JNA's stock {@link User32} mapping. */
    private interface User32Ext extends StdCallLibrary {
        User32Ext INSTANCE = Native.load("user32", User32Ext.class, W32APIOptions.DEFAULT_OPTIONS);

        /** Undocumented but stable since XP; activates like Alt-Tab, ignoring foreground lock. */
        void SwitchToThisWindow(HWND hWnd, boolean fAltTab);

        boolean BringWindowToTop(HWND hWnd);

        boolean IsIconic(HWND hWnd);
    }

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

    /** Screen bounds of the visible Elite window, or {@code null} when unavailable. */
    public static Rectangle eliteWindowBounds() {
        HWND hwnd = findEliteWindow();
        RECT rect = new RECT();
        if (hwnd == null || !User32.INSTANCE.GetWindowRect(hwnd, rect)) {
            return null;
        }
        return new Rectangle(rect.left, rect.top,
                Math.max(0, rect.right - rect.left), Math.max(0, rect.bottom - rect.top));
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
     * Escalates through activation techniques that avoid leaving Elite in a "visually focused
     * but dead input" state. Never uses an Alt tap — that can stick menu/modifier state and
     * break both keyboard and joystick until a real focus cycle.
     */
    public static boolean tryBringToForeground() {
        HWND hwnd = findEliteWindow();
        if (hwnd == null) {
            System.out.println("EDO auto-trade: Elite window not found");
            return false;
        }
        // Only restore when minimized; unconditional SW_RESTORE can bounce window state.
        if (User32Ext.INSTANCE.IsIconic(hwnd)) {
            User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);
        }

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
                    User32Ext.INSTANCE.BringWindowToTop(hwnd);
                } finally {
                    User32.INSTANCE.AttachThreadInput(previousThreadId, eliteThreadId, false);
                }
                if (finishIfEliteForeground("AttachThreadInput")) {
                    return true;
                }
            }
        }
        User32.INSTANCE.SetForegroundWindow(hwnd);
        if (finishIfEliteForeground("SetForegroundWindow")) {
            return true;
        }

        // Alt-Tab-style activation tends to deliver a full WM_ACTIVATE cycle games need for
        // DirectInput/Raw Input — without injecting Alt into the game.
        User32Ext.INSTANCE.SwitchToThisWindow(hwnd, true);
        if (finishIfEliteForeground("SwitchToThisWindow")) {
            return true;
        }

        boolean ok = ctrlKeySetForeground(hwnd);
        System.out.println("EDO auto-trade: focus via Ctrl-key trick -> "
                + ok + "; foreground=" + foregroundProcessBaseName());
        if (ok) {
            releaseStuckModifiers();
        }
        return ok;
    }

    private static boolean finishIfEliteForeground(String technique) {
        if (!waitForCondition(EliteWindowFocus::isEliteForeground,
                ACTIVATION_TIMEOUT_MS, ACTIVATION_POLL_MS)) {
            return false;
        }
        releaseStuckModifiers();
        System.out.println("EDO auto-trade: focus via " + technique + " -> "
                + "true; foreground=" + foregroundProcessBaseName());
        return true;
    }

    /**
     * Taps Ctrl (not Alt) via {@link Robot} before {@code SetForegroundWindow}. Windows lifts the
     * foreground lock for the process that generated the last input. Ctrl avoids Elite menu /
     * stuck-Alt input death that the old Alt trick could leave behind.
     */
    private static boolean ctrlKeySetForeground(HWND hwnd) {
        Robot robot;
        try {
            robot = new Robot();
            robot.setAutoDelay(0);
        } catch (AWTException e) {
            return false;
        }
        // Press+release Ctrl *before* switching focus so Elite never sees the key held.
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        User32.INSTANCE.SetForegroundWindow(hwnd);
        User32Ext.INSTANCE.BringWindowToTop(hwnd);
        return waitForCondition(EliteWindowFocus::isEliteForeground,
                ACTIVATION_TIMEOUT_MS, ACTIVATION_POLL_MS);
    }

    static boolean waitForCondition(BooleanSupplier condition, long timeoutMs, long pollIntervalMs) {
        if (condition == null) {
            return false;
        }
        long timeoutNanos = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(
                Math.max(0L, timeoutMs));
        long deadline = System.nanoTime() + timeoutNanos;
        long interval = Math.max(1L, pollIntervalMs);
        while (true) {
            if (condition.getAsBoolean()) {
                return true;
            }
            if (System.nanoTime() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return condition.getAsBoolean();
            }
        }
    }

    /**
     * Force KEYUP for common modifiers so a prior failed Alt/Ctrl steal (or interrupted chord)
     * cannot leave the OS or game thinking a modifier is still down.
     */
    static void releaseStuckModifiers() {
        int[] keys = {
                VK_LMENU, VK_RMENU, KeyEvent.VK_ALT,
                VK_LCONTROL, VK_RCONTROL, KeyEvent.VK_CONTROL,
                VK_LSHIFT, VK_RSHIFT, KeyEvent.VK_SHIFT,
                VK_LWIN, VK_RWIN
        };
        WinUser.INPUT[] batch = (WinUser.INPUT[]) new WinUser.INPUT().toArray(keys.length);
        for (int i = 0; i < keys.length; i++) {
            batch[i].type = new DWORD(WinUser.INPUT.INPUT_KEYBOARD);
            batch[i].input.setType("ki");
            batch[i].input.ki.wVk = new WORD(keys[i]);
            batch[i].input.ki.wScan = new WORD(0);
            batch[i].input.ki.dwFlags = new DWORD(WinUser.KEYBDINPUT.KEYEVENTF_KEYUP);
            batch[i].input.ki.time = new DWORD(0);
            batch[i].input.ki.dwExtraInfo = new ULONG_PTR(0);
        }
        User32.INSTANCE.SendInput(new DWORD(batch.length), batch, batch[0].size());
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
