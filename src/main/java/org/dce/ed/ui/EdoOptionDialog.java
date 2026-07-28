package org.dce.ed.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import org.dce.ed.OverlayPreferences;
import org.dce.ed.util.AppIconUtil;

/**
 * Themed modal messages/confirmations: EDO title bar + readable dark body (unlike stock
 * {@link JOptionPane}, which inherits a dark panel with hard-to-read ink and a native caption).
 */
public final class EdoOptionDialog {

    private EdoOptionDialog() {
    }

    public static void showMessage(Component parent, Object message, String title, int messageType) {
        show(parent, message, title, messageType, JOptionPane.DEFAULT_OPTION);
    }

    public static int showConfirm(Component parent, Object message, String title, int optionType) {
        return show(parent, message, title, JOptionPane.QUESTION_MESSAGE, optionType);
    }

    public static int showConfirm(Component parent, Object message, String title, int optionType, int messageType) {
        return show(parent, message, title, messageType, optionType);
    }

    private static int show(Component parent, Object message, String title, int messageType, int optionType) {
        Window owner = ownerWindow(parent);
        String dialogTitle = title != null && !title.isBlank() ? title.trim() : "EDO";

        JDialog dlg = new JDialog(owner, dialogTitle, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setUndecorated(true);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        AppIconUtil.applyAppIcon(dlg, AppIconUtil.APP_ICON_RESOURCE);
        dlg.getRootPane().setBorder(BorderFactory.createLineBorder(EdoUi.Internal.TITLEBAR_BG_HOVER, 1));

        AtomicInteger result = new AtomicInteger(JOptionPane.CLOSED_OPTION);

        JPanel root = new JPanel(new BorderLayout());
        root.setOpaque(true);
        root.setBackground(EdoUi.User.BACKGROUND);
        root.add(new EdoDialogTitleBar(dlg, dialogTitle), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(12, 12));
        body.setOpaque(true);
        body.setBackground(EdoUi.User.BACKGROUND);
        body.setBorder(new EmptyBorder(14, 16, 8, 16));
        body.add(messageComponent(message, messageType), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttons.setOpaque(false);
        buttons.setBorder(new EmptyBorder(4, 8, 14, 8));
        Font uiFont = OverlayPreferences.getUiFont();

        Runnable disposeOk = () -> {
            result.set(JOptionPane.OK_OPTION);
            dlg.dispose();
        };
        Runnable disposeCancel = () -> {
            result.set(JOptionPane.CANCEL_OPTION);
            dlg.dispose();
        };
        Runnable disposeYes = () -> {
            result.set(JOptionPane.YES_OPTION);
            dlg.dispose();
        };
        Runnable disposeNo = () -> {
            result.set(JOptionPane.NO_OPTION);
            dlg.dispose();
        };

        switch (optionType) {
            case JOptionPane.YES_NO_OPTION -> {
                buttons.add(primaryButton("Yes", uiFont, disposeYes));
                buttons.add(primaryButton("No", uiFont, disposeNo));
            }
            case JOptionPane.YES_NO_CANCEL_OPTION -> {
                buttons.add(primaryButton("Yes", uiFont, disposeYes));
                buttons.add(primaryButton("No", uiFont, disposeNo));
                buttons.add(primaryButton("Cancel", uiFont, disposeCancel));
            }
            case JOptionPane.OK_CANCEL_OPTION -> {
                buttons.add(primaryButton("OK", uiFont, disposeOk));
                buttons.add(primaryButton("Cancel", uiFont, disposeCancel));
            }
            default -> buttons.add(primaryButton("OK", uiFont, disposeOk));
        }
        body.add(buttons, BorderLayout.SOUTH);
        root.add(body, BorderLayout.CENTER);
        dlg.setContentPane(root);

        dlg.getRootPane().registerKeyboardAction(
                e -> {
                    result.set(JOptionPane.CLOSED_OPTION);
                    dlg.dispose();
                },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        dlg.pack();
        Dimension size = dlg.getSize();
        if (size.width < 360) {
            dlg.setSize(360, size.height);
        }
        dlg.setLocationRelativeTo(owner != null ? owner : parent);
        dlg.setVisible(true);
        return result.get();
    }

    private static JButton primaryButton(String label, Font uiFont, Runnable action) {
        JButton b = new JButton(label);
        OverlayOutlineButtonStyle.applyPrimaryHitSafe(b, uiFont);
        Dimension pref = b.getPreferredSize();
        b.setPreferredSize(new Dimension(Math.max(88, pref.width), Math.max(28, pref.height)));
        b.addActionListener(e -> action.run());
        return b;
    }

    private static Component messageComponent(Object message, int messageType) {
        Font uiFont = OverlayPreferences.getUiFont();
        ColorPair colors = colorsForType(messageType);

        if (message instanceof Component component) {
            styleTree(component);
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);
            wrap.add(component, BorderLayout.CENTER);
            return wrap;
        }

        String text = message != null ? String.valueOf(message) : "";
        if (text.indexOf('\n') >= 0 || text.length() > 120) {
            JTextArea area = new JTextArea(text);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setOpaque(true);
            area.setBackground(EdoUi.User.BACKGROUND);
            area.setForeground(colors.fg);
            area.setCaretColor(colors.fg);
            area.setFont(uiFont);
            area.setBorder(new EmptyBorder(4, 4, 4, 4));
            int rows = Math.min(12, Math.max(3, text.split("\n", -1).length + 1));
            area.setRows(rows);
            area.setColumns(42);
            JScrollPane scroll = new JScrollPane(area);
            scroll.setBorder(BorderFactory.createLineBorder(EdoUi.Internal.separatorLine(), 1));
            scroll.getViewport().setBackground(EdoUi.User.BACKGROUND);
            scroll.setOpaque(false);
            scroll.setPreferredSize(new Dimension(420, Math.min(220, 24 + rows * (uiFont.getSize() + 6))));
            return scroll;
        }

        JLabel label = new JLabel("<html><body style='width:320px'>" + escapeHtml(text).replace("\n", "<br>")
                + "</body></html>");
        label.setFont(uiFont);
        label.setForeground(colors.fg);
        label.setOpaque(false);
        label.setVerticalAlignment(SwingConstants.TOP);
        return label;
    }

    private static void styleTree(Component c) {
        if (c == null) {
            return;
        }
        c.setForeground(EdoUi.User.MAIN_TEXT);
        if (c instanceof JComponent jc) {
            if (!(c instanceof JButton)) {
                jc.setBackground(EdoUi.User.BACKGROUND);
                jc.setOpaque(c instanceof JScrollPane || c instanceof JTextArea || c instanceof JPanel);
            }
        }
        if (c instanceof JTextArea area) {
            area.setForeground(EdoUi.User.MAIN_TEXT);
            area.setBackground(EdoUi.User.BACKGROUND);
            area.setCaretColor(EdoUi.User.MAIN_TEXT);
        }
        if (c instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                styleTree(child);
            }
        }
    }

    private static ColorPair colorsForType(int messageType) {
        return switch (messageType) {
            case JOptionPane.ERROR_MESSAGE -> new ColorPair(EdoUi.User.ERROR);
            case JOptionPane.WARNING_MESSAGE -> new ColorPair(EdoUi.User.WARNING);
            case JOptionPane.QUESTION_MESSAGE -> new ColorPair(EdoUi.User.MAIN_TEXT);
            default -> new ColorPair(EdoUi.User.MAIN_TEXT);
        };
    }

    private static Window ownerWindow(Component parent) {
        if (parent == null) {
            return null;
        }
        Window w = SwingUtilities.getWindowAncestor(parent);
        if (w != null) {
            return w;
        }
        return parent instanceof Window window ? window : null;
    }

    private static String escapeHtml(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record ColorPair(java.awt.Color fg) {
    }
}
