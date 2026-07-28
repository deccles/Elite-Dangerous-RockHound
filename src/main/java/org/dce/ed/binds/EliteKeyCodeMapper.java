package org.dce.ed.binds;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps Elite {@code Key_*} bind tokens to AWT virtual-key codes and short display labels.
 */
public final class EliteKeyCodeMapper {

    private EliteKeyCodeMapper() {
    }

    public static final class MappedKey {
        private final int virtualKey;
        private final String displayLabel;

        public MappedKey(int virtualKey, String displayLabel) {
            this.virtualKey = virtualKey;
            this.displayLabel = displayLabel;
        }

        public int getVirtualKey() {
            return virtualKey;
        }

        public String getDisplayLabel() {
            return displayLabel;
        }
    }

    private static final Map<String, MappedKey> KEYS = buildMap();

    public static MappedKey mapKeyToken(String keyToken) {
        if (keyToken == null || keyToken.isBlank()) {
            return null;
        }
        String normalized = keyToken.trim();
        MappedKey direct = KEYS.get(normalized);
        if (direct != null) {
            return direct;
        }
        // Case-insensitive fallback
        for (Map.Entry<String, MappedKey> e : KEYS.entrySet()) {
            if (e.getKey().equalsIgnoreCase(normalized)) {
                return e.getValue();
            }
        }
        return null;
    }

    public static boolean isModifierToken(String keyToken) {
        if (keyToken == null) {
            return false;
        }
        String t = keyToken.trim().toLowerCase(Locale.ROOT);
        return t.equals("key_leftshift")
                || t.equals("key_rightshift")
                || t.equals("key_leftcontrol")
                || t.equals("key_rightcontrol")
                || t.equals("key_leftalt")
                || t.equals("key_rightalt")
                || t.equals("key_leftcmd")
                || t.equals("key_rightcmd")
                || t.equals("key_leftwin")
                || t.equals("key_rightwin");
    }

    private static Map<String, MappedKey> buildMap() {
        Map<String, MappedKey> m = new LinkedHashMap<>();
        put(m, "Key_Space", KeyEvent.VK_SPACE, "Space");
        put(m, "Key_Tab", KeyEvent.VK_TAB, "Tab");
        put(m, "Key_Enter", KeyEvent.VK_ENTER, "Enter");
        put(m, "Key_Backspace", KeyEvent.VK_BACK_SPACE, "Backspace");
        put(m, "Key_Escape", KeyEvent.VK_ESCAPE, "Esc");
        put(m, "Key_Insert", KeyEvent.VK_INSERT, "Ins");
        put(m, "Key_Delete", KeyEvent.VK_DELETE, "Del");
        put(m, "Key_Home", KeyEvent.VK_HOME, "Home");
        put(m, "Key_End", KeyEvent.VK_END, "End");
        put(m, "Key_PageUp", KeyEvent.VK_PAGE_UP, "PgUp");
        put(m, "Key_PageDown", KeyEvent.VK_PAGE_DOWN, "PgDn");
        put(m, "Key_UpArrow", KeyEvent.VK_UP, "Up");
        put(m, "Key_DownArrow", KeyEvent.VK_DOWN, "Down");
        put(m, "Key_LeftArrow", KeyEvent.VK_LEFT, "Left");
        put(m, "Key_RightArrow", KeyEvent.VK_RIGHT, "Right");
        put(m, "Key_LeftShift", KeyEvent.VK_SHIFT, "Shift");
        put(m, "Key_RightShift", KeyEvent.VK_SHIFT, "Shift");
        put(m, "Key_LeftControl", KeyEvent.VK_CONTROL, "Ctrl");
        put(m, "Key_RightControl", KeyEvent.VK_CONTROL, "Ctrl");
        put(m, "Key_LeftAlt", KeyEvent.VK_ALT, "Alt");
        put(m, "Key_RightAlt", KeyEvent.VK_ALT, "Alt");
        put(m, "Key_LeftWin", KeyEvent.VK_WINDOWS, "Win");
        put(m, "Key_RightWin", KeyEvent.VK_WINDOWS, "Win");
        put(m, "Key_Minus", KeyEvent.VK_MINUS, "-");
        put(m, "Key_Equals", KeyEvent.VK_EQUALS, "=");
        put(m, "Key_LeftBracket", KeyEvent.VK_OPEN_BRACKET, "[");
        put(m, "Key_RightBracket", KeyEvent.VK_CLOSE_BRACKET, "]");
        put(m, "Key_BackSlash", KeyEvent.VK_BACK_SLASH, "\\");
        put(m, "Key_SemiColon", KeyEvent.VK_SEMICOLON, ";");
        put(m, "Key_Apostrophe", KeyEvent.VK_QUOTE, "'");
        put(m, "Key_Comma", KeyEvent.VK_COMMA, ",");
        put(m, "Key_Period", KeyEvent.VK_PERIOD, ".");
        put(m, "Key_Slash", KeyEvent.VK_SLASH, "/");
        put(m, "Key_BackTick", KeyEvent.VK_BACK_QUOTE, "`");
        put(m, "Key_Numpad_0", KeyEvent.VK_NUMPAD0, "Num0");
        put(m, "Key_Numpad_1", KeyEvent.VK_NUMPAD1, "Num1");
        put(m, "Key_Numpad_2", KeyEvent.VK_NUMPAD2, "Num2");
        put(m, "Key_Numpad_3", KeyEvent.VK_NUMPAD3, "Num3");
        put(m, "Key_Numpad_4", KeyEvent.VK_NUMPAD4, "Num4");
        put(m, "Key_Numpad_5", KeyEvent.VK_NUMPAD5, "Num5");
        put(m, "Key_Numpad_6", KeyEvent.VK_NUMPAD6, "Num6");
        put(m, "Key_Numpad_7", KeyEvent.VK_NUMPAD7, "Num7");
        put(m, "Key_Numpad_8", KeyEvent.VK_NUMPAD8, "Num8");
        put(m, "Key_Numpad_9", KeyEvent.VK_NUMPAD9, "Num9");
        put(m, "Key_Numpad_Enter", KeyEvent.VK_ENTER, "NumEnter");
        put(m, "Key_Numpad_Add", KeyEvent.VK_ADD, "Num+");
        put(m, "Key_Numpad_Subtract", KeyEvent.VK_SUBTRACT, "Num-");
        put(m, "Key_Numpad_Multiply", KeyEvent.VK_MULTIPLY, "Num*");
        put(m, "Key_Numpad_Divide", KeyEvent.VK_DIVIDE, "Num/");
        put(m, "Key_Numpad_Decimal", KeyEvent.VK_DECIMAL, "Num.");
        for (int i = 1; i <= 12; i++) {
            put(m, "Key_F" + i, KeyEvent.VK_F1 + (i - 1), "F" + i);
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            put(m, "Key_" + c, KeyEvent.VK_A + (c - 'A'), String.valueOf(c));
        }
        for (char c = '0'; c <= '9'; c++) {
            put(m, "Key_" + c, KeyEvent.VK_0 + (c - '0'), String.valueOf(c));
        }
        return Collections.unmodifiableMap(m);
    }

    private static void put(Map<String, MappedKey> m, String token, int vk, String label) {
        m.put(token, new MappedKey(vk, label));
    }

    /** Builds a chord display like {@code Ctrl+Num1}. */
    public static String formatChord(List<String> modifierLabels, String keyLabel) {
        List<String> parts = new ArrayList<>();
        if (modifierLabels != null) {
            parts.addAll(modifierLabels);
        }
        if (keyLabel != null && !keyLabel.isBlank()) {
            parts.add(keyLabel);
        }
        return String.join("+", parts);
    }
}
