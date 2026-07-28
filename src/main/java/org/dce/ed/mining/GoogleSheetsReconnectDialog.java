package org.dce.ed.mining;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import org.dce.ed.OverlayFrame;
import org.dce.ed.OverlayPreferences;
import org.dce.ed.PreferencesDialog;
import org.dce.ed.ui.EdoDialogTitleBar;
import org.dce.ed.ui.EdoOptionDialog;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.util.AppIconUtil;

/**
 * When Google Sheets cannot be used (typically OAuth), offers one-click reconnect and a path to Mining preferences.
 */
public final class GoogleSheetsReconnectDialog {

    private GoogleSheetsReconnectDialog() {
    }

    /**
     * True when the failure is likely fixable by signing in again (vs a transient network error).
     */
    public static boolean isLikelyAuthOrSetupFailure(String detailMessage) {
        if (detailMessage == null || detailMessage.isBlank()) {
            return false;
        }
        String s = detailMessage.toLowerCase(Locale.ROOT);
        if (s.contains("not signed in")) {
            return true;
        }
        if (s.contains("invalid_grant")) {
            return true;
        }
        if (s.contains("invalid_client")) {
            return true;
        }
        if (s.contains("unauthorized_client")) {
            return true;
        }
        if (s.contains("401") && (s.contains("unauthorized") || s.contains("credentials"))) {
            return true;
        }
        if (s.contains("403") && (s.contains("access") || s.contains("forbidden") || s.contains("denied"))) {
            return true;
        }
        return false;
    }

    /**
     * Modal dialog with a primary "Connect to Google" action and optional Mining preferences.
     */
    public static void show(java.awt.Window owner, String clientKey, String detailMessage) {
        Font uiFont = OverlayPreferences.getUiFont();

        JLabel heading = new JLabel(
                "<html><b>Google Sheets</b> could not be reached or is not authorized.</html>");
        heading.setFont(uiFont);
        heading.setForeground(EdoUi.User.MAIN_TEXT);

        JTextArea desc = new JTextArea(
                "Click \"Connect to Google\" to sign in again (uses your saved Client ID and Secret from Mining preferences).\n\n"
                        + "If you have not set those up yet, use \"Open Mining preferences…\" instead.",
                5, 42);
        desc.setEditable(false);
        desc.setOpaque(false);
        desc.setLineWrap(true);
        desc.setWrapStyleWord(true);
        desc.setBorder(null);
        desc.setFont(uiFont);
        desc.setForeground(EdoUi.User.MAIN_TEXT);

        JPanel north = new JPanel(new BorderLayout(0, 8));
        north.setOpaque(false);
        north.add(heading, BorderLayout.NORTH);
        north.add(desc, BorderLayout.CENTER);

        JTextArea detail = new JTextArea(detailMessage != null ? detailMessage : "", 4, 42);
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setFont(uiFont);
        detail.setForeground(EdoUi.User.MAIN_TEXT);
        detail.setBackground(EdoUi.User.PANEL_BG);
        detail.setCaretColor(EdoUi.User.MAIN_TEXT);
        detail.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane sp = new JScrollPane(detail);
        sp.setPreferredSize(new Dimension(420, 100));
        sp.setBorder(BorderFactory.createLineBorder(EdoUi.Internal.separatorLine(), 1));
        sp.getViewport().setBackground(EdoUi.User.PANEL_BG);
        sp.setOpaque(false);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(true);
        body.setBackground(EdoUi.User.BACKGROUND);
        body.setBorder(new EmptyBorder(14, 16, 8, 16));
        body.add(north, BorderLayout.NORTH);
        body.add(sp, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        buttons.setOpaque(false);
        buttons.setBorder(new EmptyBorder(4, 0, 12, 0));
        JButton connect = new JButton("Connect to Google");
        JButton openPrefs = new JButton("Open Mining preferences…");
        JButton close = new JButton("Close");
        OverlayOutlineButtonStyle.applyPrimaryHitSafe(connect, uiFont);
        OverlayOutlineButtonStyle.applyPrimaryHitSafe(openPrefs, uiFont);
        OverlayOutlineButtonStyle.applyPrimaryHitSafe(close, uiFont);
        buttons.add(connect);
        buttons.add(openPrefs);
        buttons.add(close);
        body.add(buttons, BorderLayout.SOUTH);

        JDialog dlg = new JDialog(owner, "Google Sheets — connection", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setUndecorated(true);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        AppIconUtil.applyAppIcon(dlg, AppIconUtil.APP_ICON_RESOURCE);
        dlg.getRootPane().setBorder(BorderFactory.createLineBorder(EdoUi.Internal.TITLEBAR_BG_HOVER, 1));

        JPanel root = new JPanel(new BorderLayout());
        root.setOpaque(true);
        root.setBackground(EdoUi.User.BACKGROUND);
        root.add(new EdoDialogTitleBar(dlg, "Google Sheets — connection"), BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);
        dlg.setContentPane(root);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);

        connect.addActionListener(e -> {
            String cid = OverlayPreferences.getMiningGoogleSheetsClientId();
            String sec = OverlayPreferences.getMiningGoogleSheetsClientSecret();
            if (cid.trim().isEmpty() || sec.trim().isEmpty()) {
                EdoOptionDialog.showMessage(dlg,
                        "Set Client ID and Client Secret in Mining preferences first.",
                        "Setup required",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            boolean ok = GoogleSheetsAuth.runOAuthFlowAndStoreToken(cid, sec);
            OverlayPreferences.flushBackingStore();
            if (ok) {
                EdoOptionDialog.showMessage(dlg,
                        "Connected. The mining log will refresh on the next sync.",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                dlg.dispose();
            } else {
                String failureMsg = "Could not complete sign-in. Check Client ID and Secret in Mining preferences.";
                OverlayFrame frame = OverlayFrame.overlayFrame;
                if (frame != null) {
                    frame.setMiningSheetsStatusError("Google Sheets reconnect: " + failureMsg);
                    EdoOptionDialog.showMessage(dlg,
                            "Could not complete sign-in. Details are shown in the overlay status bar.",
                            "Connection failed",
                            JOptionPane.WARNING_MESSAGE);
                } else {
                    EdoOptionDialog.showMessage(dlg, failureMsg, "Connection failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        openPrefs.addActionListener(e -> {
            dlg.dispose();
            PreferencesDialog.show(owner, clientKey, PreferencesDialog.MINING_TAB_INDEX);
        });

        close.addActionListener(e -> dlg.dispose());

        dlg.setVisible(true);
    }
}
