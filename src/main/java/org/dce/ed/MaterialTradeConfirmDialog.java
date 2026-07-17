package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.dce.ed.engineering.TradeSuggestion;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.OverlayOutlineButtonStyle;

/**
 * Physical confirmation before auto-driving the material trader (not mouse pass-through).
 */
final class MaterialTradeConfirmDialog extends JDialog {

    private boolean go;

    private MaterialTradeConfirmDialog(Window owner, TradeSuggestion suggestion) {
        super(owner, "Material trade", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        String traderType = suggestion.getTraderType() != null && !suggestion.getTraderType().isBlank()
                ? suggestion.getTraderType()
                : "Material";
        JLabel message = new JLabel(
                "<html><body style='text-align:center;width:280px'>"
                        + "Be sure the <b>" + escapeHtml(traderType) + "</b> Material Trader screen is up."
                        + "<br><br>After <b>Go</b>, click the Elite Dangerous window to start the trade."
                        + "<br><br><span style='font-size:90%'>"
                        + escapeHtml(suggestion.summary())
                        + "</span></body></html>",
                SwingConstants.CENTER);
        Font base = OverlayPreferences.getUiFont();
        int fontSize = OverlayPreferences.getUiFontSize();
        message.setFont(base.deriveFont(Font.PLAIN, fontSize));
        message.setForeground(EdoUi.User.MAIN_TEXT);
        message.setBorder(new EmptyBorder(16, 18, 8, 18));

        JButton goBtn = new JButton("Go");
        JButton cancelBtn = new JButton("Cancel");
        OverlayOutlineButtonStyle.applyPrimary(goBtn, base);
        OverlayOutlineButtonStyle.applyChip(cancelBtn, base, false);
        goBtn.addActionListener(e -> {
            go = true;
            dispose();
        });
        cancelBtn.addActionListener(e -> {
            go = false;
            dispose();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.setOpaque(false);
        buttons.setBorder(new EmptyBorder(8, 12, 16, 12));
        buttons.add(goBtn);
        buttons.add(cancelBtn);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(EdoUi.User.PANEL_BG);
        root.setBorder(BorderFactory.createLineBorder(EdoUi.User.MAIN_TEXT, 1));
        root.add(message, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(goBtn);
        // OverlayFrame is always-on-top; without this the dialog opens behind it and looks like a no-op.
        setAlwaysOnTop(true);
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * @return {@code true} if the user chose Go
     */
    static boolean confirm(Window owner, TradeSuggestion suggestion) {
        if (suggestion == null) {
            return false;
        }
        MaterialTradeConfirmDialog dialog = new MaterialTradeConfirmDialog(owner, suggestion);
        SwingUtilities.invokeLater(() -> {
            if (dialog.isDisplayable()) {
                dialog.toFront();
                dialog.requestFocus();
            }
        });
        dialog.setVisible(true);
        return dialog.go;
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
