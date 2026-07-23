package org.dce.ed.ui;

import java.awt.Component;
import java.awt.Window;

import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

/**
 * Ensures tooltip / combo popups stay above {@code setAlwaysOnTop(true)} dialogs. Default Swing
 * heavyweight popup windows are not always-on-top, so they render behind the dialog and look
 * "missing". Also doubles tooltip dismiss delay while the host dialog is showing so longer
 * effect tips are readable.
 */
public final class AlwaysOnTopPopupFactory extends PopupFactory {

    private final PopupFactory delegate;

    public AlwaysOnTopPopupFactory(PopupFactory delegate) {
        this.delegate = delegate != null ? delegate : getSharedInstance();
    }

    @Override
    public Popup getPopup(Component owner, Component contents, int x, int y)
            throws IllegalArgumentException {
        Popup popup = delegate.getPopup(owner, contents, x, y);
        SwingUtilities.invokeLater(() -> raiseTooltipWindows());
        return popup;
    }

    private static void raiseTooltipWindows() {
        for (Window w : Window.getWindows()) {
            if (w == null || !w.isDisplayable() || !w.isVisible()) {
                continue;
            }
            String name = w.getClass().getName();
            // Swing heavyweight tooltip / combo popups.
            if (name.contains("HeavyWeightWindow") || name.contains("Popup$")) {
                try {
                    w.setAlwaysOnTop(true);
                } catch (Exception ignored) {
                    // Best-effort only.
                }
            }
        }
    }

    /** Install while {@code host} is showing; restore the previous factory when it hides. */
    public static void installWhileShowing(Window host) {
        if (host == null) {
            return;
        }
        PopupFactory previous = PopupFactory.getSharedInstance();
        if (previous instanceof AlwaysOnTopPopupFactory) {
            return;
        }
        AlwaysOnTopPopupFactory factory = new AlwaysOnTopPopupFactory(previous);
        PopupFactory.setSharedInstance(factory);

        ToolTipManager tipManager = ToolTipManager.sharedInstance();
        int previousDismissDelay = tipManager.getDismissDelay();
        tipManager.setDismissDelay(Math.max(1, previousDismissDelay * 2));

        host.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                restore(previous, previousDismissDelay);
            }

            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                restore(previous, previousDismissDelay);
            }
        });
    }

    private static void restore(PopupFactory previous, int previousDismissDelay) {
        if (previous != null) {
            PopupFactory.setSharedInstance(previous);
        }
        ToolTipManager.sharedInstance().setDismissDelay(previousDismissDelay);
    }
}
