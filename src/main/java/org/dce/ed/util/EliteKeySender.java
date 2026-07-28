package org.dce.ed.util;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WORD;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;

/**
 * Sends keyboard input for Elite Dangerous UI navigation.
 * <p>
 * Standalone EDO implementation of the approach proven in RoboHound:
 * scan-code {@code SendInput} (hardcoded extended arrow scan codes), then
 * {@code PostMessage} to the foreground HWND, then {@link Robot} as last resort.
 */
public final class EliteKeySender {

    public static final int VK_LEFT = KeyEvent.VK_LEFT;
    public static final int VK_UP = KeyEvent.VK_UP;
    public static final int VK_RIGHT = KeyEvent.VK_RIGHT;
    public static final int VK_DOWN = KeyEvent.VK_DOWN;
    public static final int VK_SPACE = KeyEvent.VK_SPACE;

    private static final int MAPVK_VK_TO_VSC = 0;

    private final int interKeyDelayMs;
    private final boolean requireEliteForeground;
    private final Robot robot;

    public EliteKeySender() {
        this(100, true);
    }

    public EliteKeySender(int interKeyDelayMs, boolean requireEliteForeground) {
        this.interKeyDelayMs = Math.max(0, interKeyDelayMs);
        this.requireEliteForeground = requireEliteForeground;
        Robot r = null;
        try {
            r = new Robot();
            r.setAutoDelay(0);
        } catch (AWTException ignored) {
            // Robot fallback unavailable
        }
        this.robot = r;
    }

    public void tap(int virtualKey) throws InterruptedException {
        requireEliteForegroundForInput();
        sendGameplayKey(virtualKey, true);
        sleepInterKey();
        sendGameplayKey(virtualKey, false);
        sleepInterKey();
    }

    public void tapTimes(int virtualKey, int times) throws InterruptedException {
        for (int i = 0; i < times; i++) {
            tap(virtualKey);
        }
    }

    public void left(int times) throws InterruptedException {
        tapTimes(VK_LEFT, times);
    }

    public void right(int times) throws InterruptedException {
        tapTimes(VK_RIGHT, times);
    }

    public void up(int times) throws InterruptedException {
        tapTimes(VK_UP, times);
    }

    public void down(int times) throws InterruptedException {
        tapTimes(VK_DOWN, times);
    }

    public void space() throws InterruptedException {
        tap(VK_SPACE);
    }

    /**
     * Taps a key chord: modifiers down, main key tap, modifiers up.
     */
    public void tapChord(int virtualKey, Iterable<Integer> modifierVirtualKeys) throws InterruptedException {
        requireEliteForegroundForInput();
        List<Integer> mods = new ArrayList<>();
        if (modifierVirtualKeys != null) {
            for (Integer m : modifierVirtualKeys) {
                if (m != null) {
                    mods.add(m);
                }
            }
        }
        for (Integer mod : mods) {
            sendGameplayKey(mod.intValue(), true);
            sleepInterKey();
        }
        sendGameplayKey(virtualKey, true);
        sleepInterKey();
        sendGameplayKey(virtualKey, false);
        sleepInterKey();
        for (int i = mods.size() - 1; i >= 0; i--) {
            sendGameplayKey(mods.get(i).intValue(), false);
            sleepInterKey();
        }
    }

    public void tapBinding(org.dce.ed.binds.EliteKeyBinding binding) throws InterruptedException {
        if (binding == null) {
            return;
        }
        tapChord(binding.getVirtualKey(), binding.getModifierVirtualKeys());
    }

    private void requireEliteForegroundForInput() throws InterruptedException {
        if (!requireEliteForeground) {
            return;
        }
        if (EliteWindowFocus.isEliteForeground()) {
            return;
        }
        EliteWindowFocus.tryBringToForeground();
        Thread.sleep(80);
        if (EliteWindowFocus.isEliteForeground()) {
            return;
        }
        throw new IllegalStateException(
                "Elite Dangerous is not focused (foreground: "
                        + EliteWindowFocus.foregroundProcessBaseName()
                        + "). Click the game window, then retry.");
    }

    private void sendGameplayKey(int virtualKey, boolean keyDown) {
        if (isExtendedVirtualKey(virtualKey)) {
            if (sendSingleInput(buildKeyboardInput(virtualKey, keyDown, InputMode.SCAN_CODE))) {
                return;
            }
            if (sendForegroundWindowKey(virtualKey, keyDown)) {
                return;
            }
            if (sendSingleInput(buildKeyboardInput(virtualKey, keyDown, InputMode.VK_WITH_SCAN))) {
                return;
            }
            sendRobotKey(virtualKey, keyDown);
            return;
        }
        if (!sendSingleInput(buildKeyboardInput(virtualKey, keyDown, InputMode.SCAN_CODE))) {
            if (!sendForegroundWindowKey(virtualKey, keyDown)) {
                sendRobotKey(virtualKey, keyDown);
            }
        }
    }

    /** Post WM_KEY* to the current foreground window (RoboHound fallback for arrows). */
    private boolean sendForegroundWindowKey(int virtualKey, boolean keyDown) {
        HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null) {
            return false;
        }
        ScanCodeInfo scan = scanCodeForVirtualKey(virtualKey);
        long lParam = 1L | ((long) scan.scanCode() << 16);
        if (scan.extended()) {
            lParam |= (1L << 24);
        }
        if (!keyDown) {
            lParam |= (1L << 31) | (1L << 30);
        }
        int message = keyDown ? WinUser.WM_KEYDOWN : WinUser.WM_KEYUP;
        User32.INSTANCE.PostMessage(hwnd, message, new WPARAM(virtualKey), new LPARAM(lParam));
        return true;
    }

    private WinUser.INPUT buildKeyboardInput(int virtualKey, boolean keyDown, InputMode mode) {
        WinUser.INPUT input = new WinUser.INPUT();
        input.type = new DWORD(WinUser.INPUT.INPUT_KEYBOARD);
        input.input.setType("ki");
        input.input.ki.time = new DWORD(0);
        input.input.ki.dwExtraInfo = new ULONG_PTR(0);

        int flags = 0;
        if (!keyDown) {
            flags |= WinUser.KEYBDINPUT.KEYEVENTF_KEYUP;
        }

        if (mode == InputMode.SCAN_CODE) {
            ScanCodeInfo scan = scanCodeForVirtualKey(virtualKey);
            input.input.ki.wVk = new WORD(0);
            input.input.ki.wScan = new WORD(scan.scanCode());
            flags |= WinUser.KEYBDINPUT.KEYEVENTF_SCANCODE;
            if (scan.extended()) {
                flags |= WinUser.KEYBDINPUT.KEYEVENTF_EXTENDEDKEY;
            }
        } else {
            ScanCodeInfo scan = scanCodeForVirtualKey(virtualKey);
            input.input.ki.wVk = new WORD(virtualKey);
            input.input.ki.wScan = new WORD(scan.scanCode());
            if (scan.extended()) {
                flags |= WinUser.KEYBDINPUT.KEYEVENTF_EXTENDEDKEY;
            }
        }

        input.input.ki.dwFlags = new DWORD(flags);
        return input;
    }

    private boolean sendSingleInput(WinUser.INPUT input) {
        WinUser.INPUT[] batch = (WinUser.INPUT[]) input.toArray(1);
        copyKeyboardInput(input, batch[0]);
        DWORD sent = User32.INSTANCE.SendInput(new DWORD(1), batch, batch[0].size());
        return sent != null && sent.intValue() == 1;
    }

    private static void copyKeyboardInput(WinUser.INPUT src, WinUser.INPUT dst) {
        dst.type = src.type;
        dst.input.setType("ki");
        dst.input.ki.wVk = src.input.ki.wVk;
        dst.input.ki.wScan = src.input.ki.wScan;
        dst.input.ki.dwFlags = src.input.ki.dwFlags;
        dst.input.ki.time = src.input.ki.time;
        dst.input.ki.dwExtraInfo = src.input.ki.dwExtraInfo;
    }

    private static ScanCodeInfo scanCodeForVirtualKey(int virtualKey) {
        ScanCodeInfo arrow = arrowScanCode(virtualKey);
        if (arrow != null) {
            return arrow;
        }
        int mapped = User32.INSTANCE.MapVirtualKeyEx(virtualKey, MAPVK_VK_TO_VSC, null);
        int scanCode = mapped & 0xFF;
        int prefix = (mapped >> 8) & 0xFF;
        boolean extended = prefix == 0xE0 || prefix == 0xE1 || isExtendedVirtualKey(virtualKey);
        return new ScanCodeInfo(scanCode, extended);
    }

    /** Hardware scan codes for navigation arrows (E0-prefixed / extended). */
    private static ScanCodeInfo arrowScanCode(int virtualKey) {
        return switch (virtualKey) {
            case KeyEvent.VK_UP -> new ScanCodeInfo(0x48, true);
            case KeyEvent.VK_DOWN -> new ScanCodeInfo(0x50, true);
            case KeyEvent.VK_LEFT -> new ScanCodeInfo(0x4B, true);
            case KeyEvent.VK_RIGHT -> new ScanCodeInfo(0x4D, true);
            default -> null;
        };
    }

    private static boolean isExtendedVirtualKey(int virtualKey) {
        return virtualKey == KeyEvent.VK_LEFT
                || virtualKey == KeyEvent.VK_UP
                || virtualKey == KeyEvent.VK_RIGHT
                || virtualKey == KeyEvent.VK_DOWN
                || virtualKey == KeyEvent.VK_INSERT
                || virtualKey == KeyEvent.VK_DELETE
                || virtualKey == KeyEvent.VK_HOME
                || virtualKey == KeyEvent.VK_END
                || virtualKey == KeyEvent.VK_PAGE_UP
                || virtualKey == KeyEvent.VK_PAGE_DOWN
                || virtualKey == KeyEvent.VK_NUM_LOCK
                || virtualKey == KeyEvent.VK_DIVIDE;
    }

    private void sendRobotKey(int virtualKey, boolean keyDown) {
        if (robot == null) {
            return;
        }
        if (keyDown) {
            robot.keyPress(virtualKey);
        } else {
            robot.keyRelease(virtualKey);
        }
    }

    private void sleepInterKey() throws InterruptedException {
        if (interKeyDelayMs > 0) {
            Thread.sleep(interKeyDelayMs);
        }
    }

    public static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os != null && os.toLowerCase(Locale.ROOT).contains("win");
    }

    private enum InputMode {
        SCAN_CODE,
        VK_WITH_SCAN
    }

    private record ScanCodeInfo(int scanCode, boolean extended) {
    }
}
