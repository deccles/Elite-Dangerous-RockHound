package org.dce.ed.exec;

import java.awt.Component;
import java.awt.Window;

import javax.swing.SwingUtilities;

/**
 * Decides whether global Exec shortcut keys should run. Elite may be visually focused while Swing
 * still reports overlay keyboard focus in pass-through mode.
 */
public final class ExecShortcutKeyDispatch {

    private ExecShortcutKeyDispatch() {
    }

    public static boolean shouldDispatch(boolean eliteForeground, boolean overlayHasKeyboardFocus, Component focusOwner) {
        if (eliteForeground) {
            return true;
        }
        if (overlayHasKeyboardFocus) {
            return false;
        }
        if (focusOwner == null) {
            return true;
        }
        Window focusedWindow = SwingUtilities.getWindowAncestor(focusOwner);
        if (focusedWindow == null) {
            return true;
        }
        String name = focusedWindow.getClass().getName();
        return !name.endsWith("PreferencesDialog");
    }
}
