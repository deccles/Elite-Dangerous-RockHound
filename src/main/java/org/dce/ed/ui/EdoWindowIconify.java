package org.dce.ed.ui;

import java.awt.Frame;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import javax.swing.JDialog;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * Iconifies every visible EDO top-level window together, and restores them together when any
 * frame is restored from the taskbar.
 */
public final class EdoWindowIconify {

    private static final String WATCH_KEY = "edo.iconifyWatch";
    private static final Object LOCK = new Object();
    private static final Set<Window> HIDDEN_DIALOGS =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean restoring;
    private static boolean groupIconified;

    private EdoWindowIconify() {
    }

    /** Minimize all visible EDO windows (frames iconify; dialogs hide until restore). */
    public static void iconifyAll() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(EdoWindowIconify::iconifyAll);
            return;
        }
        synchronized (LOCK) {
            groupIconified = true;
            HIDDEN_DIALOGS.clear();
            for (Window w : Window.getWindows()) {
                if (w == null || !w.isDisplayable() || !w.isVisible()) {
                    continue;
                }
                if (!isEdoOwnedWindow(w)) {
                    continue;
                }
                if (w instanceof Frame frame) {
                    watch(frame);
                    int state = frame.getExtendedState();
                    if ((state & Frame.ICONIFIED) == 0) {
                        frame.setExtendedState(state | Frame.ICONIFIED);
                    }
                } else if (w instanceof JDialog) {
                    HIDDEN_DIALOGS.add(w);
                    w.setVisible(false);
                }
            }
        }
    }

    /** Minimize only the requested frame. It restores independently from the group. */
    public static void iconifyOne(Window window) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> iconifyOne(window));
            return;
        }
        if (!(window instanceof Frame frame) || !frame.isDisplayable()) {
            return;
        }
        synchronized (LOCK) {
            int state = frame.getExtendedState();
            if ((state & Frame.ICONIFIED) == 0) {
                frame.setExtendedState(state | Frame.ICONIFIED);
            }
        }
    }

    /** Attach restore-together behavior to an EDO frame (call when creating floating docks). */
    public static void watch(Window window) {
        if (!(window instanceof Frame frame)) {
            return;
        }
        JRootPane root = null;
        if (frame instanceof javax.swing.RootPaneContainer rpc) {
            root = rpc.getRootPane();
        }
        if (root != null && Boolean.TRUE.equals(root.getClientProperty(WATCH_KEY))) {
            return;
        }
        if (root != null) {
            root.putClientProperty(WATCH_KEY, Boolean.TRUE);
        }
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowDeiconified(WindowEvent e) {
                synchronized (LOCK) {
                    if (groupIconified) {
                        restoreAll();
                    }
                }
            }
        });
    }

    private static void restoreAll() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(EdoWindowIconify::restoreAll);
            return;
        }
        synchronized (LOCK) {
            if (restoring) {
                return;
            }
            restoring = true;
            try {
                for (Frame frame : Frame.getFrames()) {
                    if (frame == null || !frame.isDisplayable() || !isEdoOwnedWindow(frame)) {
                        continue;
                    }
                    int state = frame.getExtendedState();
                    if ((state & Frame.ICONIFIED) != 0) {
                        frame.setExtendedState(state & ~Frame.ICONIFIED);
                    }
                    if (!frame.isVisible()) {
                        frame.setVisible(true);
                    }
                }
                List<Window> dialogs = new ArrayList<>(HIDDEN_DIALOGS);
                HIDDEN_DIALOGS.clear();
                for (Window dialog : dialogs) {
                    if (dialog != null && dialog.isDisplayable()) {
                        dialog.setVisible(true);
                    }
                }
            } finally {
                groupIconified = false;
                restoring = false;
            }
        }
    }

    private static boolean isEdoOwnedWindow(Window w) {
        if (w == null) {
            return false;
        }
        Package p = w.getClass().getPackage();
        if (p != null && p.getName() != null && p.getName().startsWith("org.dce.ed")) {
            return true;
        }
        Window owner = w.getOwner();
        return owner != null && isEdoOwnedWindow(owner);
    }
}
