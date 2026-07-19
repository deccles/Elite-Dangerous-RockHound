package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.dce.ed.engineering.MaterialTradeExecutor;
import org.dce.ed.engineering.TradeSuggestion;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.OverlayOutlineButtonStyle;

/**
 * Physical confirmation before auto-driving the material trader (not mouse pass-through).
 */
final class MaterialTradeConfirmDialog extends JDialog {

    @FunctionalInterface
    interface TradeAction {
        MaterialTradeExecutor.Result execute(Consumer<String> status);
    }

    private MaterialTradeExecutor.Result result;
    private boolean running;

    private MaterialTradeConfirmDialog(Window owner, String traderType, String tradeSummary,
                                       TradeAction action) {
        super(owner, "Material trade", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        String displayTraderType = traderType != null && !traderType.isBlank()
                ? traderType
                : "Material";
        JLabel message = new JLabel(
                "<html><body style='text-align:center;width:280px'>"
                        + "Be sure the <b>" + escapeHtml(displayTraderType) + "</b> Material Trader screen is up."
                        + "<br><br>Press <b>OK</b> to focus Elite Dangerous and start the trade."
                        + "<br>If automatic focus fails, click the Elite Dangerous window."
                        + "<br><br><span style='font-size:90%'>"
                        + escapeHtml(tradeSummary)
                        + "</span></body></html>",
                SwingConstants.CENTER);
        Font base = OverlayPreferences.getUiFont();
        int fontSize = OverlayPreferences.getUiFontSize();
        message.setFont(base.deriveFont(Font.PLAIN, fontSize));
        message.setForeground(EdoUi.User.MAIN_TEXT);
        message.setBorder(new EmptyBorder(16, 18, 8, 18));

        JLabel status = new JLabel(" ", SwingConstants.CENTER);
        status.setFont(base.deriveFont(Font.PLAIN, Math.max(11f, fontSize - 1f)));
        status.setForeground(EdoUi.User.MAIN_TEXT);
        status.setBorder(new EmptyBorder(2, 12, 2, 12));

        JButton goBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");
        OverlayOutlineButtonStyle.applyPrimary(goBtn, base);
        OverlayOutlineButtonStyle.applyChip(cancelBtn, base, false);
        goBtn.addActionListener(e -> {
            if (running) {
                return;
            }
            running = true;
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            goBtn.setEnabled(false);
            cancelBtn.setEnabled(false);
            status.setText("Focusing Elite Dangerous…");

            Thread worker = new Thread(() -> {
                MaterialTradeExecutor.Result completed;
                try {
                    completed = action.execute(msg -> SwingUtilities.invokeLater(() -> {
                        if (isDisplayable()) {
                            status.setText(msg != null && !msg.isBlank() ? msg : " ");
                        }
                    }));
                } catch (RuntimeException ex) {
                    completed = new MaterialTradeExecutor.Result(
                            MaterialTradeExecutor.Outcome.KEY_ERROR,
                            ex.getMessage() != null ? ex.getMessage() : "Trade failed");
                }
                MaterialTradeExecutor.Result finalResult = completed;
                SwingUtilities.invokeLater(() -> {
                    result = finalResult;
                    dispose();
                });
            }, "edo-material-trade");
            worker.setDaemon(true);
            worker.start();
        });
        cancelBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.setOpaque(false);
        buttons.setBorder(new EmptyBorder(8, 12, 16, 12));
        buttons.add(goBtn);
        buttons.add(cancelBtn);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(EdoUi.User.PANEL_BG);
        root.setBorder(BorderFactory.createLineBorder(EdoUi.User.MAIN_TEXT, 1));
        root.add(message, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(status, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(goBtn);
        // OverlayFrame is always-on-top; without this the dialog opens behind it and looks like a no-op.
        setAlwaysOnTop(true);
        pack();
        setLocationRelativeTo(owner);
    }

    static MaterialTradeExecutor.Result execute(Window owner, TradeSuggestion suggestion,
                                                TradeAction action) {
        if (suggestion == null) {
            return null;
        }
        return show(owner, suggestion.getTraderType(), suggestion.summary(), action);
    }

    static MaterialTradeExecutor.Result executeAll(Window owner, String traderType,
                                                   List<TradeSuggestion> suggestions,
                                                   TradeAction action) {
        if (suggestions == null || suggestions.isEmpty()) {
            return null;
        }
        int paid = suggestions.stream().mapToInt(TradeSuggestion::getFromCount).sum();
        int received = suggestions.stream().mapToInt(TradeSuggestion::getToCount).sum();
        String summary = suggestions.size() + " trades — give " + paid
                + " total materials, receive " + received + " total materials";
        return show(owner, traderType, summary, action);
    }

    private static MaterialTradeExecutor.Result show(Window owner, String traderType, String summary,
                                                     TradeAction action) {
        MaterialTradeConfirmDialog dialog =
                new MaterialTradeConfirmDialog(owner, traderType, summary, action);
        SwingUtilities.invokeLater(() -> {
            if (dialog.isDisplayable()) {
                dialog.toFront();
                dialog.requestFocus();
            }
        });
        dialog.setVisible(true);
        return dialog.result;
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
