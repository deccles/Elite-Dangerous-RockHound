package org.dce.ed.exec;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;

/** JNativeHook function-key codes supported for global Exec shortcut triggers. */
public final class ExecShortcutKeys {

    public static final int DEFAULT_KEY_CODE = NativeKeyEvent.VC_F10;

    private ExecShortcutKeys() {
    }

    public static String[] displayChoices() {
        String[] keys = new String[12];
        for (int i = 0; i < 12; i++) {
            keys[i] = "F" + (i + 1);
        }
        return keys;
    }

    public static boolean isSupported(int keyCode) {
        return keyCode >= NativeKeyEvent.VC_F1 && keyCode <= NativeKeyEvent.VC_F12;
    }

    public static String toDisplayString(int keyCode) {
        return switch (keyCode) {
            case NativeKeyEvent.VC_F1 -> "F1";
            case NativeKeyEvent.VC_F2 -> "F2";
            case NativeKeyEvent.VC_F3 -> "F3";
            case NativeKeyEvent.VC_F4 -> "F4";
            case NativeKeyEvent.VC_F5 -> "F5";
            case NativeKeyEvent.VC_F6 -> "F6";
            case NativeKeyEvent.VC_F7 -> "F7";
            case NativeKeyEvent.VC_F8 -> "F8";
            case NativeKeyEvent.VC_F9 -> "F9";
            case NativeKeyEvent.VC_F10 -> "F10";
            case NativeKeyEvent.VC_F11 -> "F11";
            case NativeKeyEvent.VC_F12 -> "F12";
            default -> "F10";
        };
    }

    public static int fromDisplayString(String display) {
        if (display == null) {
            return DEFAULT_KEY_CODE;
        }
        return switch (display.trim().toUpperCase()) {
            case "F1" -> NativeKeyEvent.VC_F1;
            case "F2" -> NativeKeyEvent.VC_F2;
            case "F3" -> NativeKeyEvent.VC_F3;
            case "F4" -> NativeKeyEvent.VC_F4;
            case "F5" -> NativeKeyEvent.VC_F5;
            case "F6" -> NativeKeyEvent.VC_F6;
            case "F7" -> NativeKeyEvent.VC_F7;
            case "F8" -> NativeKeyEvent.VC_F8;
            case "F9" -> NativeKeyEvent.VC_F9;
            case "F10" -> NativeKeyEvent.VC_F10;
            case "F11" -> NativeKeyEvent.VC_F11;
            case "F12" -> NativeKeyEvent.VC_F12;
            default -> DEFAULT_KEY_CODE;
        };
    }

    /** Normalize persisted or user-supplied key codes to a supported function key. */
    public static int normalizeKeyCode(int keyCode) {
        return isSupported(keyCode) ? keyCode : DEFAULT_KEY_CODE;
    }
}
