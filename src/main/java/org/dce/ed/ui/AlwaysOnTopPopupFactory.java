package org.dce.ed.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Window;

import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/**
 * Ensures tooltip / combo popups stay above {@code setAlwaysOnTop(true)} dialogs. Default Swing
 * heavyweight popup windows are not always-on-top, so they render behind the dialog and look
 * "missing". Also doubles tooltip dismiss delay and applies a dark tip chrome while the host
 * dialog is showing so longer effect tips stay readable.
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

        Object previousTipBg = UIManager.get("ToolTip.background");
        Object previousTipFg = UIManager.get("ToolTip.foreground");
        Object previousTipBorder = UIManager.get("ToolTip.border");
        UIManager.put("ToolTip.background", EdoUi.User.PANEL_BG);
        UIManager.put("ToolTip.foreground", new Color(230, 230, 230));
        UIManager.put("ToolTip.border", new EmptyBorder(4, 8, 4, 8));

        host.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                restore(previous, previousDismissDelay, previousTipBg, previousTipFg, previousTipBorder);
            }

            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                restore(previous, previousDismissDelay, previousTipBg, previousTipFg, previousTipBorder);
            }
        });
    }

    private static void restore(PopupFactory previous,
            int previousDismissDelay,
            Object previousTipBg,
            Object previousTipFg,
            Object previousTipBorder) {
        if (previous != null) {
            PopupFactory.setSharedInstance(previous);
        }
        ToolTipManager.sharedInstance().setDismissDelay(previousDismissDelay);
        UIManager.put("ToolTip.background", previousTipBg);
        UIManager.put("ToolTip.foreground", previousTipFg);
        UIManager.put("ToolTip.border", previousTipBorder);
    }
}
