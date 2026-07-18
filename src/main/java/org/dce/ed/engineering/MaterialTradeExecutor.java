package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.dce.ed.logreader.event.MaterialTradeEvent;
import org.dce.ed.util.EliteKeySender;
import org.dce.ed.util.EliteWindowFocus;

/**
 * Drives a single material-trader exchange via keyboard and verifies the journal {@code MaterialTrade} event.
 */
public final class MaterialTradeExecutor {

    public static final long DEFAULT_TRADE_TIMEOUT_MS = 10_000L;
    /** How long to wait for the user to click Elite after pressing Go. */
    public static final long DEFAULT_USER_FOCUS_TIMEOUT_MS = 45_000L;
    private static final int RESET_PRESSES = 16;
    private static final long FOCUS_SETTLE_MS = 350L;
    /** Pause after arriving on a grid cell before Space (game needs a beat to register highlight). */
    private static final long PRE_SPACE_MS = 280L;
    /** Pause after Space selects a material (UI mode change). */
    private static final long AFTER_SELECT_MS = 450L;
    /** Pause after Space on give before quantity Rights (amount panel must appear). */
    private static final long PRE_QUANTITY_MS = 550L;
    /** Let the trader return to its material grid after the journal confirms a trade. */
    private static final long AFTER_TRADE_MS = 900L;
    private static final long FOCUS_POLL_MS = 150L;

    public enum Outcome {
        SUCCESS,
        NOT_WINDOWS,
        FOCUS_FAILED,
        LAYOUT_ERROR,
        MATERIALS_MISSING,
        KEY_ERROR,
        TIMEOUT,
        MISMATCH,
        INTERRUPTED
    }

    public record Result(Outcome outcome, String message) {
        public boolean ok() {
            return outcome == Outcome.SUCCESS;
        }
    }

    private final EngineeringDatabase database;
    private final MaterialTraderScreenLayout layout;
    private final EliteKeySender keys;
    private final long tradeTimeoutMs;
    private final long userFocusTimeoutMs;

    private final Object waitLock = new Object();
    private final AtomicReference<PendingTrade> pending = new AtomicReference<>();

    public MaterialTradeExecutor(EngineeringDatabase database) {
        this(database, MaterialTraderScreenLayout.getInstance(), new EliteKeySender(),
                DEFAULT_TRADE_TIMEOUT_MS, DEFAULT_USER_FOCUS_TIMEOUT_MS);
    }

    public MaterialTradeExecutor(EngineeringDatabase database,
                                 MaterialTraderScreenLayout layout,
                                 EliteKeySender keys,
                                 long tradeTimeoutMs) {
        this(database, layout, keys, tradeTimeoutMs, DEFAULT_USER_FOCUS_TIMEOUT_MS);
    }

    public MaterialTradeExecutor(EngineeringDatabase database,
                                 MaterialTraderScreenLayout layout,
                                 EliteKeySender keys,
                                 long tradeTimeoutMs,
                                 long userFocusTimeoutMs) {
        this.database = database != null ? database : EngineeringDatabase.getInstance();
        this.layout = layout != null ? layout : MaterialTraderScreenLayout.getInstance();
        this.keys = keys != null ? keys : new EliteKeySender();
        this.tradeTimeoutMs = Math.max(1_000L, tradeTimeoutMs);
        this.userFocusTimeoutMs = Math.max(5_000L, userFocusTimeoutMs);
    }

    /**
     * Called from the journal listener (any thread) when a {@link MaterialTradeEvent} arrives.
     */
    public void onMaterialTrade(MaterialTradeEvent event) {
        if (event == null) {
            return;
        }
        PendingTrade expect = pending.get();
        if (expect == null) {
            return;
        }
        if (!matches(expect.suggestion, event)) {
            return;
        }
        synchronized (waitLock) {
            PendingTrade current = pending.get();
            if (current == null || current != expect) {
                return;
            }
            current.observed = event;
            waitLock.notifyAll();
        }
    }

    public Result execute(TradeSuggestion suggestion) {
        return execute(suggestion, null);
    }

    /**
     * @param status optional UI/status callback (may be called off the EDT)
     */
    public Result execute(TradeSuggestion suggestion, Consumer<String> status) {
        return executeAll(suggestion != null ? List.of(suggestion) : List.of(), status);
    }

    /**
     * Executes a sequence of trades from one material-trader screen. The first trade
     * resets to upper-left; every later trade starts at the previous trade's give cell.
     * Each trade must produce its expected journal event before the next one starts.
     */
    public Result executeAll(List<TradeSuggestion> suggestions, Consumer<String> status) {
        if (suggestions == null || suggestions.isEmpty()) {
            return new Result(Outcome.MATERIALS_MISSING, "No trades selected");
        }
        if (!EliteKeySender.isWindows()) {
            return new Result(Outcome.NOT_WINDOWS, "Auto-trade requires Windows");
        }

        List<PlannedTrade> plans = new ArrayList<>();
        for (TradeSuggestion suggestion : suggestions) {
            ResultOrPlan prepared = prepare(suggestion);
            if (prepared.error() != null) {
                return prepared.error();
            }
            plans.add(prepared.plan());
        }
        try {
            // Do not auto-focus — wait until the user clicks Elite themselves.
            if (!EliteWindowFocus.isEliteForeground()) {
                report(status, "Click Elite Dangerous to start…");
                System.out.println("EDO auto-trade: waiting for user to focus Elite "
                        + "(currently " + EliteWindowFocus.foregroundProcessBaseName() + ")");
                boolean focused = EliteWindowFocus.waitUntilUserFocusesElite(
                        userFocusTimeoutMs, FOCUS_POLL_MS);
                if (!focused) {
                    return new Result(Outcome.FOCUS_FAILED,
                            "Timed out waiting for you to click Elite (last foreground: "
                                    + EliteWindowFocus.foregroundProcessBaseName() + ")");
                }
            }
            Thread.sleep(FOCUS_SETTLE_MS);
            if (!EliteWindowFocus.isEliteForeground()) {
                return new Result(Outcome.FOCUS_FAILED,
                        "Elite lost focus before keys were sent — click the game and try again");
            }

            MaterialTraderScreenLayout.GridPos currentPos = null;
            for (int i = 0; i < plans.size(); i++) {
                PlannedTrade plan = plans.get(i);
                int ordinal = i + 1;
                report(status, plans.size() == 1
                        ? "Running trade…"
                        : "Running trade " + ordinal + " of " + plans.size() + "…");
                System.out.println("EDO auto-trade: trade " + ordinal + "/" + plans.size()
                        + " " + plan.suggestion().summary()
                        + " rights=" + plan.rightPresses()
                        + " start=" + currentPos
                        + " recv=" + plan.receivePos()
                        + " give=" + plan.givePos());

                PendingTrade wait = new PendingTrade(plan.suggestion());
                pending.set(wait);
                runKeySequence(currentPos, plan.receivePos(), plan.givePos(), plan.rightPresses());

                MaterialTradeEvent observed = awaitMatch(wait, tradeTimeoutMs);
                pending.compareAndSet(wait, null);
                if (observed == null) {
                    return new Result(Outcome.TIMEOUT,
                            "Trade " + ordinal + " of " + plans.size()
                                    + " sent keys, but no matching MaterialTrade appeared in the journal");
                }
                if (!matches(plan.suggestion(), observed)) {
                    return new Result(Outcome.MISMATCH,
                            "Journal trade " + ordinal + " did not match the suggestion");
                }

                // The game returns to the grid with the give material highlighted.
                currentPos = plan.givePos();
                if (ordinal < plans.size()) {
                    Thread.sleep(AFTER_TRADE_MS);
                    if (!EliteWindowFocus.isEliteForeground()) {
                        return new Result(Outcome.FOCUS_FAILED,
                                "Elite lost focus after trade " + ordinal + " of " + plans.size());
                    }
                }
            }
            return new Result(Outcome.SUCCESS, plans.size() == 1
                    ? plans.get(0).suggestion().summary()
                    : "Completed all " + plans.size() + " trades");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return new Result(Outcome.INTERRUPTED, "Trade cancelled");
        } catch (IllegalStateException focusLost) {
            return new Result(Outcome.KEY_ERROR, focusLost.getMessage());
        } catch (RuntimeException ex) {
            return new Result(Outcome.KEY_ERROR, ex.getMessage() != null ? ex.getMessage() : "Key send failed");
        } finally {
            pending.set(null);
        }
    }

    private ResultOrPlan prepare(TradeSuggestion suggestion) {
        if (suggestion == null) {
            return ResultOrPlan.error(new Result(Outcome.MATERIALS_MISSING, "Invalid trade suggestion"));
        }
        Optional<EngineeringMaterial> fromOpt = database.material(suggestion.getFromKey());
        Optional<EngineeringMaterial> toOpt = database.material(suggestion.getToKey());
        if (fromOpt.isEmpty() || toOpt.isEmpty()) {
            return ResultOrPlan.error(
                    new Result(Outcome.MATERIALS_MISSING, "Unknown material in trade suggestion"));
        }
        EngineeringMaterial give = fromOpt.get();
        EngineeringMaterial receive = toOpt.get();
        Optional<MaterialTraderScreenLayout.GridPos> receivePos = layout.position(receive);
        Optional<MaterialTraderScreenLayout.GridPos> givePos = layout.position(give);
        if (receivePos.isEmpty() || givePos.isEmpty()) {
            return ResultOrPlan.error(new Result(Outcome.LAYOUT_ERROR,
                    "No screen position for " + (receivePos.isEmpty() ? receive.getName() : give.getName())));
        }
        int rightPresses = MaterialTradeRateCalculator.rightPressesFor(
                give, receive, suggestion.getFromCount(), suggestion.getToCount());
        return ResultOrPlan.plan(new PlannedTrade(
                suggestion, receivePos.get(), givePos.get(), rightPresses));
    }

    private static void report(Consumer<String> status, String message) {
        if (status != null && message != null) {
            status.accept(message);
        }
    }

    private void runKeySequence(MaterialTraderScreenLayout.GridPos startPos,
                                MaterialTraderScreenLayout.GridPos receivePos,
                                MaterialTraderScreenLayout.GridPos givePos,
                                int rightPresses) throws InterruptedException {
        MaterialTraderScreenLayout.GridPos navigationStart = startPos;
        if (navigationStart == null) {
            // Reset highlight to upper-left.
            keys.up(RESET_PRESSES);
            keys.left(RESET_PRESSES);
            navigationStart = ORIGIN;
        }

        // Select receive (wanted) material.
        navigateRelative(navigationStart, receivePos);
        Thread.sleep(PRE_SPACE_MS);
        keys.space();
        Thread.sleep(AFTER_SELECT_MS);

        // Highlight stays on receive; move to give (paid) material, then Space to open trade.
        navigateRelative(receivePos, givePos);
        Thread.sleep(PRE_SPACE_MS);
        keys.space();
        Thread.sleep(PRE_QUANTITY_MS);

        // Quantity: Cancel is selected; Left/Right adjust the amount, then Up + Space confirms.
        System.out.println("EDO auto-trade: quantity Right presses=" + rightPresses);
        if (rightPresses > 0) {
            keys.right(rightPresses);
            Thread.sleep(PRE_SPACE_MS);
        }

        // Leave Cancel, confirm trade.
        keys.up(1);
        Thread.sleep(PRE_SPACE_MS);
        keys.space();
    }

    private static final MaterialTraderScreenLayout.GridPos ORIGIN =
            new MaterialTraderScreenLayout.GridPos(0, 0);

    private void navigateRelative(MaterialTraderScreenLayout.GridPos from,
                                  MaterialTraderScreenLayout.GridPos to) throws InterruptedException {
        int dRow = to.row() - from.row();
        int dCol = to.col() - from.col();
        if (dRow > 0) {
            keys.down(dRow);
        } else if (dRow < 0) {
            keys.up(-dRow);
        }
        if (dCol > 0) {
            keys.right(dCol);
        } else if (dCol < 0) {
            keys.left(-dCol);
        }
    }

    private MaterialTradeEvent awaitMatch(PendingTrade wait, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        synchronized (waitLock) {
            while (wait.observed == null) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return null;
                }
                long ms = TimeUnit.NANOSECONDS.toMillis(remaining);
                waitLock.wait(Math.max(1L, ms));
            }
            return wait.observed;
        }
    }

    boolean matches(TradeSuggestion suggestion, MaterialTradeEvent event) {
        if (suggestion == null || event == null) {
            return false;
        }
        String paid = EngineeringMaterialKeys.resolveKey(
                event.getPaidName(), event.getPaidNameLocalised(), database);
        String received = EngineeringMaterialKeys.resolveKey(
                event.getReceivedName(), event.getReceivedNameLocalised(), database);
        if (!EngineeringMaterialKeys.canonicalKey(paid)
                .equals(EngineeringMaterialKeys.canonicalKey(suggestion.getFromKey()))) {
            return false;
        }
        if (!EngineeringMaterialKeys.canonicalKey(received)
                .equals(EngineeringMaterialKeys.canonicalKey(suggestion.getToKey()))) {
            return false;
        }
        if (event.getPaidCount() != suggestion.getFromCount()) {
            return false;
        }
        return event.getReceivedCount() == suggestion.getToCount();
    }

    private record PlannedTrade(TradeSuggestion suggestion,
                                MaterialTraderScreenLayout.GridPos receivePos,
                                MaterialTraderScreenLayout.GridPos givePos,
                                int rightPresses) {
    }

    private record ResultOrPlan(PlannedTrade plan, Result error) {
        static ResultOrPlan plan(PlannedTrade plan) {
            return new ResultOrPlan(plan, null);
        }

        static ResultOrPlan error(Result error) {
            return new ResultOrPlan(null, error);
        }
    }

    private static final class PendingTrade {
        final TradeSuggestion suggestion;
        volatile MaterialTradeEvent observed;

        PendingTrade(TradeSuggestion suggestion) {
            this.suggestion = suggestion;
        }
    }
}
