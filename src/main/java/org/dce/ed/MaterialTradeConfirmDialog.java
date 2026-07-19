package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
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

    private static final Pattern TRADE_PROGRESS =
            Pattern.compile("Running trade\\s+(\\d+)\\s+of\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final String CARD_BUTTONS = "buttons";
    private static final String CARD_PROGRESS = "progress";

    @FunctionalInterface
    interface TradeAction {
        MaterialTradeExecutor.Result execute(Consumer<String> status);
    }

    private MaterialTradeExecutor.Result result;
    private boolean running;

    private MaterialTradeConfirmDialog(Window owner, String traderType, String tradeSummary,
                                       int tradeCount, TradeAction action) {
        super(owner, "Material trade", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        int totalTrades = Math.max(1, tradeCount);

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

        JProgressBar progressBar = new JProgressBar(0, totalTrades);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setString("0 / " + totalTrades);
        progressBar.setIndeterminate(false);
        progressBar.setForeground(EdoUi.User.MAIN_TEXT);
        progressBar.setBackground(EdoUi.User.BACKGROUND);
        progressBar.setBorderPainted(true);
        progressBar.setPreferredSize(new Dimension(260, 22));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.setOpaque(false);
        buttons.add(goBtn);
        buttons.add(cancelBtn);

        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.setOpaque(false);
        progressPanel.setBorder(new EmptyBorder(4, 24, 4, 24));
        progressPanel.add(progressBar, BorderLayout.CENTER);

        CardLayout footerCards = new CardLayout();
        JPanel footer = new JPanel(footerCards);
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(8, 12, 16, 12));
        footer.add(buttons, CARD_BUTTONS);
        footer.add(progressPanel, CARD_PROGRESS);
        footerCards.show(footer, CARD_BUTTONS);

        goBtn.addActionListener(e -> {
            if (running) {
                return;
            }
            running = true;
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            goBtn.setEnabled(false);
            cancelBtn.setEnabled(false);
            footerCards.show(footer, CARD_PROGRESS);
            progressBar.setIndeterminate(true);
            progressBar.setString("Focusing…");
            status.setText("Focusing Elite Dangerous…");

            Thread worker = new Thread(() -> {
                MaterialTradeExecutor.Result completed;
                try {
                    completed = action.execute(msg -> SwingUtilities.invokeLater(() -> {
                        if (!isDisplayable()) {
                            return;
                        }
                        String text = msg != null && !msg.isBlank() ? msg : " ";
                        status.setText(text);
                        applyProgress(progressBar, text, totalTrades);
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

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(EdoUi.User.PANEL_BG);
        root.setBorder(BorderFactory.createLineBorder(EdoUi.User.MAIN_TEXT, 1));
        root.add(message, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(status, BorderLayout.NORTH);
        south.add(footer, BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(goBtn);
        // OverlayFrame is always-on-top; without this the dialog opens behind it and looks like a no-op.
        setAlwaysOnTop(true);
        pack();
        setLocationRelativeTo(owner);
    }

    private static void applyProgress(JProgressBar progressBar, String statusText, int totalTrades) {
        if (statusText == null) {
            return;
        }
        Matcher m = TRADE_PROGRESS.matcher(statusText);
        if (m.find()) {
            int current = parsePositive(m.group(1), 0);
            int total = parsePositive(m.group(2), totalTrades);
            if (total <= 0) {
                total = Math.max(1, totalTrades);
            }
            current = Math.min(Math.max(0, current), total);
            progressBar.setIndeterminate(false);
            progressBar.setMaximum(total);
            progressBar.setValue(current);
            progressBar.setString(current + " / " + total);
            return;
        }
        if (statusText.toLowerCase().contains("running trade")) {
            progressBar.setIndeterminate(false);
            progressBar.setMaximum(totalTrades);
            progressBar.setValue(0);
            progressBar.setString("1 / " + totalTrades);
            return;
        }
        progressBar.setIndeterminate(true);
        progressBar.setString(statusText.length() > 28 ? "Working…" : statusText.trim());
    }

    private static int parsePositive(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    static MaterialTradeExecutor.Result execute(Window owner, TradeSuggestion suggestion,
                                                TradeAction action) {
        if (suggestion == null) {
            return null;
        }
        return show(owner, suggestion.getTraderType(), suggestion.summary(), 1, action);
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
        return show(owner, traderType, summary, suggestions.size(), action);
    }

    private static MaterialTradeExecutor.Result show(Window owner, String traderType, String summary,
                                                     int tradeCount, TradeAction action) {
        MaterialTradeConfirmDialog dialog =
                new MaterialTradeConfirmDialog(owner, traderType, summary, tradeCount, action);
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
