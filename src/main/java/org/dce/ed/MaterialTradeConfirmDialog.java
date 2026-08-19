package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.dce.ed.engineering.MaterialTradeExecutor;
import org.dce.ed.engineering.TradeSuggestion;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.OverlayOutlineButtonStyle;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;

/**
 * Physical confirmation before auto-driving the material trader (not mouse pass-through).
 */
final class MaterialTradeConfirmDialog extends JDialog {

    private static final Pattern TRADE_PROGRESS =
            Pattern.compile("(?:Completed|Starting|Running) trade\\s+(\\d+)\\s+of\\s+(\\d+)",
                    Pattern.CASE_INSENSITIVE);
    private static final String CARD_BUTTONS = "buttons";
    private static final String CARD_PROGRESS = "progress";

    @FunctionalInterface
    interface TradeAction {
        MaterialTradeExecutor.Result execute(Consumer<String> status);
    }

    private MaterialTradeExecutor.Result result;
    private boolean running;
    private Thread worker;
    private Timer topmostTimer;
    private final Runnable cancelAction;
    private final Consumer<MaterialTradeExecutor.Result> completion;
    private final AtomicBoolean completionDelivered = new AtomicBoolean();

    MaterialTradeConfirmDialog(Window owner, String traderType, String tradeSummary,
                                       int tradeCount, String shipScopeLabel, TradeAction action,
                                       Runnable cancelAction,
                                       Consumer<MaterialTradeExecutor.Result> completion) {
        super(owner, "Material trade", ModalityType.MODELESS);
        this.cancelAction = cancelAction;
        this.completion = completion;
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

        JLabel mouseWarning = new JLabel(
                "<html><body style='text-align:center'>"
                        + "After hitting OK: DO NOT TOUCH THE CONTROLS"
                        + "</body></html>",
                SwingConstants.CENTER);
        mouseWarning.setFont(base.deriveFont(Font.BOLD, fontSize));
        mouseWarning.setForeground(EdoUi.User.ERROR);
        mouseWarning.setBorder(new EmptyBorder(4, 12, 4, 12));

        JButton goBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");
        JButton stopBtn = new JButton("Cancel");
        OverlayOutlineButtonStyle.applyPrimary(goBtn, base);
        OverlayOutlineButtonStyle.applyChip(cancelBtn, base, false);
        OverlayOutlineButtonStyle.applyChip(stopBtn, base, false);

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

        JPanel progressPanel = createProgressPanel(progressBar, stopBtn);

        CardLayout footerCards = new CardLayout();
        JPanel footer = new JPanel(footerCards);
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(8, 12, 16, 12));
        footer.add(buttons, CARD_BUTTONS);
        footer.add(progressPanel, CARD_PROGRESS);
        footerCards.show(footer, CARD_BUTTONS);

        JPanel briefing = new JPanel(new BorderLayout());
        briefing.setOpaque(false);

        goBtn.addActionListener(e -> {
            if (running) {
                return;
            }
            running = true;
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            goBtn.setEnabled(false);
            stopBtn.setEnabled(true);
            footerCards.show(footer, CARD_PROGRESS);
            keepWarningsVisibleWhileRunning(briefing, mouseWarning);
            progressBar.setIndeterminate(true);
            progressBar.setString("Focusing…");
            status.setText("Focusing Elite Dangerous…");
            startTopmostKeeper(owner);
            releaseFocusForTrade(this);

            worker = new Thread(() -> {
                MaterialTradeExecutor.Result completed;
                try {
                    completed = action.execute(msg -> SwingUtilities.invokeLater(() -> {
                        if (!isDisplayable()) {
                            return;
                        }
                        String text = msg != null && !msg.isBlank() ? msg : " ";
                        status.setText(text);
                        applyProgress(progressBar, text, totalTrades);
                        if (shouldStartTopmostKeeper(text)) {
                            startTopmostKeeper(owner);
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
                    stopTopmostKeeper();
                    restoreFocusAfterTrade(this);
                    running = false;
                    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                    progressBar.setIndeterminate(false);
                    if (finalResult != null && finalResult.ok()) {
                        progressBar.setValue(progressBar.getMaximum());
                        progressBar.setString("Complete");
                        progressBar.setForeground(EdoUi.User.SUCCESS);
                        status.setForeground(EdoUi.User.SUCCESS);
                        status.setText("Trading complete");
                    } else {
                        progressBar.setString(finalResult != null ? finalResult.message() : "Trade stopped");
                        status.setForeground(finalResult != null
                                && finalResult.outcome() == MaterialTradeExecutor.Outcome.INTERRUPTED
                                        ? EdoUi.User.MAIN_TEXT : EdoUi.User.ERROR);
                        status.setText(statusHtml(
                                finalResult != null ? finalResult.message() : "Trade stopped"));
                    }
                    stopBtn.setText(finalResult != null && finalResult.ok() ? "Complete" : "Close");
                    if (finalResult != null && finalResult.ok()) {
                        OverlayOutlineButtonStyle.applySuccess(stopBtn, base);
                    }
                    stopBtn.setEnabled(true);
                    completeOnce(finalResult);
                });
            }, "edo-material-trade");
            worker.setDaemon(true);
            worker.start();
        });
        cancelBtn.addActionListener(e -> dispose());
        stopBtn.addActionListener(e -> {
            if (!running) {
                dispose();
                return;
            }
            status.setText("Cancelling…");
            stopBtn.setEnabled(false);
            if (cancelAction != null) {
                cancelAction.run();
            }
            Thread active = worker;
            if (active != null) {
                active.interrupt();
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(EdoUi.User.PANEL_BG);
        root.setBorder(BorderFactory.createLineBorder(EdoUi.User.MAIN_TEXT, 1));

        boolean singleShip = shipScopeLabel != null && !shipScopeLabel.isBlank();
        Component footerWarning = mouseWarning;
        if (singleShip) {
            briefing.add(message, BorderLayout.NORTH);
            JLabel shipWarning = new JLabel(
                    "<html><body style='text-align:center;width:300px'>"
                            + "TRADING FOR "
                            + escapeHtml(shipScopeLabel.trim())
                            + " ONLY"
                            + "</body></html>",
                    SwingConstants.CENTER);
            shipWarning.setFont(base.deriveFont(Font.BOLD, fontSize));
            shipWarning.setForeground(EdoUi.User.SUCCESS);
            shipWarning.setOpaque(false);
            shipWarning.setBorder(new EmptyBorder(4, 12, 4, 12));
            briefing.add(mouseWarning, BorderLayout.CENTER);
            footerWarning = shipWarning;
        } else {
            briefing.add(message, BorderLayout.CENTER);
        }
        root.add(briefing, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(status, BorderLayout.NORTH);
        south.add(footerWarning, BorderLayout.CENTER);
        south.add(footer, BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(goBtn);
        // OverlayFrame is always-on-top; without this the dialog opens behind it and looks like a no-op.
        setAlwaysOnTop(true);
        pack();
        placeBesideOwner(owner);
    }

    static JPanel createProgressPanel(JProgressBar progressBar, JButton stopButton) {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(4, 12, 4, 12));
        panel.add(progressBar, BorderLayout.CENTER);
        panel.add(stopButton, BorderLayout.EAST);
        return panel;
    }

    static void keepWarningsVisibleWhileRunning(Component briefing, Component warning) {
        if (briefing != null) {
            briefing.setVisible(true);
        }
        if (warning != null) {
            warning.setVisible(true);
        }
    }

    static boolean shouldStartTopmostKeeper(String status) {
        if (status == null) {
            return false;
        }
        String lower = status.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("running trade") || lower.startsWith("starting trade");
    }

    static String statusHtml(String status) {
        return "<html><body style='text-align:center;width:400px'>"
                + escapeHtml(status != null ? status : "") + "</body></html>";
    }

    static Rectangle boundsBesideOwner(Rectangle owner, Rectangle screen, int width, int height) {
        int gap = 8;
        int x;
        if (owner.x - gap - width >= screen.x) {
            x = owner.x - gap - width;
        } else if (owner.x + owner.width + gap + width <= screen.x + screen.width) {
            x = owner.x + owner.width + gap;
        } else {
            x = Math.max(screen.x, Math.min(screen.x + screen.width - width, owner.x - width / 2));
        }
        int y = Math.max(screen.y, Math.min(screen.y + screen.height - height, owner.y));
        return new Rectangle(x, y, width, height);
    }

    private void placeBesideOwner(Window owner) {
        if (owner == null || owner.getGraphicsConfiguration() == null) {
            setLocationRelativeTo(owner);
            return;
        }
        Rectangle placed = boundsBesideOwner(owner.getBounds(),
                owner.getGraphicsConfiguration().getBounds(), getWidth(), getHeight());
        setLocation(placed.x, placed.y);
    }

    private void startTopmostKeeper(Window owner) {
        if (topmostTimer != null) {
            return;
        }
        topmostTimer = new Timer(250, e -> {
            pinTopmostWithoutFocus(owner);
            pinTopmostWithoutFocus(this);
        });
        topmostTimer.start();
        pinTopmostWithoutFocus(owner);
        pinTopmostWithoutFocus(this);
    }

    private void stopTopmostKeeper() {
        if (topmostTimer != null) {
            topmostTimer.stop();
            topmostTimer = null;
        }
    }

    private static void pinTopmostWithoutFocus(Window window) {
        if (window == null || !window.isDisplayable()) {
            return;
        }
        try {
            // Keep the owning RockHound host in the topmost band even when its normal
            // foreground watcher and the trade's Elite-focus transition race each other.
            // This changes Z order only; it does not activate or focus RockHound.
            if (!window.isAlwaysOnTop()) {
                window.setAlwaysOnTop(true);
            }
            Pointer pointer = Native.getComponentPointer(window);
            if (pointer == null) {
                return;
            }
            HWND hwnd = new HWND(pointer);
            HWND topmost = new HWND(Pointer.createConstant(-1));
            User32.INSTANCE.SetWindowPos(hwnd, topmost, 0, 0, 0, 0,
                    WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE
                            | WinUser.SWP_NOACTIVATE | WinUser.SWP_SHOWWINDOW);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void dispose() {
        stopTopmostKeeper();
        if (running) {
            if (cancelAction != null) {
                cancelAction.run();
            }
            Thread active = worker;
            if (active != null) {
                active.interrupt();
            }
        }
        completeOnce(result != null ? result : new MaterialTradeExecutor.Result(
                MaterialTradeExecutor.Outcome.INTERRUPTED, "Trade cancelled"));
        super.dispose();
    }

    private void completeOnce(MaterialTradeExecutor.Result completed) {
        if (completionDelivered.compareAndSet(false, true) && completion != null) {
            completion.accept(completed);
        }
    }

    static void releaseFocusForTrade(Window window) {
        if (window == null) {
            return;
        }
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
    }

    static void restoreFocusAfterTrade(Window window) {
        if (window == null) {
            return;
        }
        window.setFocusableWindowState(true);
        window.setAlwaysOnTop(true);
        window.toFront();
        window.requestFocus();
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
            // "Starting/Running trade N" = N-1 done; "Completed trade N" = N done.
            boolean completed = statusText.toLowerCase(Locale.ROOT).contains("completed");
            int value = completed ? current : Math.max(0, current - 1);
            value = Math.min(Math.max(0, value), total);
            progressBar.setIndeterminate(false);
            progressBar.setMaximum(total);
            progressBar.setValue(value);
            progressBar.setString(value + " / " + total);
            return;
        }
        if (statusText.toLowerCase(Locale.ROOT).contains("running trade")
                || statusText.toLowerCase(Locale.ROOT).contains("starting trade")) {
            // In-progress with no ordinal yet — keep the bar at completed count (usually 0).
            progressBar.setIndeterminate(false);
            progressBar.setMaximum(totalTrades);
            if (progressBar.getValue() < 0) {
                progressBar.setValue(0);
            }
            progressBar.setString(progressBar.getValue() + " / " + totalTrades);
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

    static MaterialTradeConfirmDialog execute(Window owner, TradeSuggestion suggestion,
                                                String shipScopeLabel, TradeAction action,
                                                Runnable cancelAction,
                                                Consumer<MaterialTradeExecutor.Result> completion) {
        if (suggestion == null) {
            return null;
        }
        return show(owner, suggestion.getTraderType(), suggestion.summary(), 1,
                shipScopeLabel, action, cancelAction, completion);
    }

    static MaterialTradeConfirmDialog executeAll(Window owner, String traderType,
                                                   List<TradeSuggestion> suggestions,
                                                   String shipScopeLabel, TradeAction action,
                                                   Runnable cancelAction,
                                                   Consumer<MaterialTradeExecutor.Result> completion) {
        if (suggestions == null || suggestions.isEmpty()) {
            return null;
        }
        int paid = suggestions.stream().mapToInt(TradeSuggestion::getFromCount).sum();
        int received = suggestions.stream().mapToInt(TradeSuggestion::getToCount).sum();
        String summary = suggestions.size() + " trades — give " + paid
                + " total materials, receive " + received + " total materials";
        return show(owner, traderType, summary, suggestions.size(), shipScopeLabel,
                action, cancelAction, completion);
    }

    private static MaterialTradeConfirmDialog show(Window owner, String traderType, String summary,
                                                     int tradeCount, String shipScopeLabel,
                                                     TradeAction action, Runnable cancelAction,
                                                     Consumer<MaterialTradeExecutor.Result> completion) {
        MaterialTradeConfirmDialog dialog =
                new MaterialTradeConfirmDialog(owner, traderType, summary, tradeCount,
                        shipScopeLabel, action, cancelAction, completion);
        dialog.setVisible(true);
        dialog.toFront();
        dialog.requestFocus();
        return dialog;
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
