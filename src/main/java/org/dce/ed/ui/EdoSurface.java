package org.dce.ed.ui;

import java.awt.Component;
import java.awt.Container;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Chrome surface mode for EDO Swing trees. A single Look&amp;Feel cannot mean “everything
 * transparent,” so UI delegates and helpers read this client property (or walk ancestors).
 *
 * <ul>
 *   <li>{@link #OVERLAY} — see-through overlay tabs / float frames</li>
 *   <li>{@link #DIALOG} — opaque dark dialogs (default; prefs, goals, confirm)</li>
 *   <li>{@link #DOCUMENT} — light document chrome (Log journal viewer)</li>
 * </ul>
 */
public final class EdoSurface {

    /** Client property key; value is one of {@link #OVERLAY}, {@link #DIALOG}, {@link #DOCUMENT}. */
    public static final String KEY = "edo.surface";

    public static final String OVERLAY = "overlay";
    public static final String DIALOG = "dialog";
    public static final String DOCUMENT = "document";

    private EdoSurface() {
    }

    /** Marks {@code root} and does not recurse — children inherit via ancestor walk. */
    public static void set(JComponent root, String surface) {
        if (root == null || surface == null || surface.isBlank()) {
            return;
        }
        root.putClientProperty(KEY, surface);
    }

    /** Marks {@code root} as overlay chrome. */
    public static void markOverlay(JComponent root) {
        set(root, OVERLAY);
    }

    /** Marks {@code root} as opaque dialog chrome. */
    public static void markDialog(JComponent root) {
        set(root, DIALOG);
    }

    /** Marks {@code root} as light document chrome (Log journal). */
    public static void markDocument(JComponent root) {
        set(root, DOCUMENT);
    }

    /**
     * Resolves surface for {@code c}: own property, then ancestor with {@link #KEY}, else
     * {@link #DIALOG} (safe opaque default for new controls).
     */
    public static String resolve(Component c) {
        if (c == null) {
            return DIALOG;
        }
        for (Component cur = c; cur != null; cur = parentOf(cur)) {
            if (cur instanceof JComponent jc) {
                Object v = jc.getClientProperty(KEY);
                if (v instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        }
        return DIALOG;
    }

    public static boolean isOverlay(Component c) {
        return OVERLAY.equals(resolve(c));
    }

    public static boolean isDialog(Component c) {
        return DIALOG.equals(resolve(c));
    }

    public static boolean isDocument(Component c) {
        return DOCUMENT.equals(resolve(c));
    }

    private static Component parentOf(Component c) {
        Container p = c.getParent();
        if (p != null) {
            return p;
        }
        return SwingUtilities.getWindowAncestor(c);
    }
}
