package org.dce.ed.engineering;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estimates Merc Coins paid by Operations between the game's balance reports.
 *
 * <p>Elite writes {@code MercCoins_Current} only in the {@code Statistics} event after loading
 * into the main game. Operations pay out without any journal entry, so the balance lags until the
 * commander leaves Operations. The credit reward does show up: it is the change in
 * {@code LoadGame.Credits} across the Operation session, and 0 credits means 0 Merc Coins.
 *
 * <p>Payouts fitted from this commander's journals (Sept 2026, about 30 Operations): about 88
 * for the first full-credit Operation of the week, about 70 after that, about 30 for
 * partial-credit runs, and roughly 40% of those once about 1,000 coins have been earned since
 * the weekly reset (Thursday 07:00 UTC). Each estimate is replaced by the real balance at the next
 * {@code Statistics}.
 */
public final class MercCoinOperationEstimator {

    /** Operations paying at least half the top credit reward (9.1M) pay the full Merc Coin rate. */
    static final long FULL_CREDIT_REWARD = 4_550_000L;
    static final int FIRST_FULL_PAYOUT = 88;
    static final int FULL_PAYOUT = 70;
    static final int PARTIAL_PAYOUT = 30;
    static final int WEEKLY_SOFT_CAP = 1_000;
    static final double OVER_CAP_FACTOR = 0.4;

    /** LoadGame and GameModeChange for the same load land within the same second or two. */
    private static final long SAME_LOAD_SECONDS = 2;

    private final Map<Instant, Integer> earnedByWeek = new HashMap<>();
    private final Map<Instant, Integer> fullOperationsByWeek = new HashMap<>();
    private final List<PendingOperation> pending = new ArrayList<>();

    private Instant sessionStart;
    private long sessionCredits = -1L;
    private boolean sessionIsOperation;
    private String stashedMode;
    private Instant stashedModeAt;

    private Integer lastReportedBalance;
    private Integer lastReportedSpent;
    private Instant lastReportedAt;

    /** One Operation paid since the last balance report. */
    public record PendingOperation(Instant weekStart, Instant completedAt, long creditReward, int estimate) {
    }

    /**
     * A load ends the previous session. When that session was an Operation that paid credits,
     * returns the estimated Merc Coin payout (already counted toward this week's earnings).
     */
    public int onLoadGame(Instant timestamp, long credits) {
        int payout = 0;
        if (timestamp != null && sessionStart != null && sessionIsOperation
                && sessionCredits >= 0 && credits > sessionCredits) {
            long reward = credits - sessionCredits;
            Instant week = weekStart(timestamp);
            payout = estimatePayout(reward,
                    earnedByWeek.getOrDefault(week, 0),
                    fullOperationsByWeek.getOrDefault(week, 0));
            if (reward >= FULL_CREDIT_REWARD) {
                fullOperationsByWeek.merge(week, 1, Integer::sum);
            }
            if (payout > 0) {
                earnedByWeek.merge(week, payout, Integer::sum);
                pending.add(new PendingOperation(week, timestamp, reward, payout));
            }
        }
        sessionStart = timestamp;
        sessionCredits = credits;
        sessionIsOperation = stashedMode != null && withinSameLoad(stashedModeAt, timestamp)
                && isOperationMode(stashedMode);
        stashedMode = null;
        stashedModeAt = null;
        return payout;
    }

    /**
     * {@code GameModeChange} is written just before or just after its {@code LoadGame}. After the
     * load it describes the current session; before it, the session about to start.
     */
    public void onGameModeChange(Instant timestamp, String gameMode) {
        if (sessionStart != null && withinSameLoad(sessionStart, timestamp)) {
            sessionIsOperation = isOperationMode(gameMode);
            return;
        }
        stashedMode = gameMode;
        stashedModeAt = timestamp;
    }

    /**
     * Real balance from {@code Statistics}. Pending estimates are replaced by the actual coins
     * earned since the previous report ({@code balance change + spend change}).
     */
    public void onStatistics(Instant timestamp, int balance, Integer totalSpent) {
        if (lastReportedBalance != null) {
            int actual = balance - lastReportedBalance;
            if (totalSpent != null && lastReportedSpent != null) {
                actual += totalSpent - lastReportedSpent;
            }
            reconcile(Math.max(0, actual), timestamp);
        }
        pending.clear();
        lastReportedBalance = balance;
        lastReportedSpent = totalSpent;
        lastReportedAt = timestamp;
    }

    private void reconcile(int actual, Instant timestamp) {
        int estimated = 0;
        for (PendingOperation op : pending) {
            estimated += op.estimate();
            earnedByWeek.merge(op.weekStart(), -op.estimate(), Integer::sum);
        }
        if (actual <= 0) {
            return;
        }
        if (estimated <= 0 || pending.isEmpty()) {
            if (timestamp != null) {
                earnedByWeek.merge(weekStart(timestamp), actual, Integer::sum);
            }
            return;
        }
        int assigned = 0;
        for (int i = 0; i < pending.size(); i++) {
            PendingOperation op = pending.get(i);
            int share = i == pending.size() - 1
                    ? actual - assigned
                    : (int) Math.round(actual * (op.estimate() / (double) estimated));
            assigned += share;
            earnedByWeek.merge(op.weekStart(), share, Integer::sum);
        }
    }

    /** Coins added by estimates since the last real balance. */
    public int pendingEstimate() {
        int sum = 0;
        for (PendingOperation op : pending) {
            sum += op.estimate();
        }
        return sum;
    }

    public int pendingOperations() {
        return pending.size();
    }

    public List<PendingOperation> pendingOperationList() {
        return List.copyOf(pending);
    }

    /** When the game last reported the real balance, or null. */
    public Instant lastReportedAt() {
        return lastReportedAt;
    }

    /** Coins earned (real where reported, estimated otherwise) in the week containing {@code at}. */
    public int earnedInWeekOf(Instant at) {
        return at == null ? 0 : Math.max(0, earnedByWeek.getOrDefault(weekStart(at), 0));
    }

    /**
     * Estimated Merc Coins for one completed Operation.
     *
     * @param creditReward credits the Operation paid; 0 means it was not completed
     * @param earnedThisWeek coins already earned since the weekly reset
     * @param fullOperationsThisWeek full-credit Operations already completed this week
     */
    static int estimatePayout(long creditReward, int earnedThisWeek, int fullOperationsThisWeek) {
        if (creditReward <= 0) {
            return 0;
        }
        int base;
        if (creditReward >= FULL_CREDIT_REWARD) {
            base = fullOperationsThisWeek <= 0 ? FIRST_FULL_PAYOUT : FULL_PAYOUT;
        } else {
            base = PARTIAL_PAYOUT;
        }
        int earned = Math.max(0, earnedThisWeek);
        int underCap = Math.max(0, Math.min(base, WEEKLY_SOFT_CAP - earned));
        int overCap = base - underCap;
        return underCap + (int) Math.round(overCap * OVER_CAP_FACTOR);
    }

    /** Start of the Elite week containing {@code at}: the latest Thursday 07:00 UTC at or before it. */
    static Instant weekStart(Instant at) {
        ZonedDateTime utc = at.atZone(ZoneOffset.UTC);
        ZonedDateTime reset = utc.with(TemporalAdjusters.previousOrSame(DayOfWeek.THURSDAY))
                .truncatedTo(ChronoUnit.DAYS)
                .plusHours(7);
        if (reset.isAfter(utc)) {
            reset = reset.minusWeeks(1);
        }
        return reset.toInstant();
    }

    private static boolean withinSameLoad(Instant a, Instant b) {
        return a != null && b != null && Math.abs(Duration.between(a, b).getSeconds()) <= SAME_LOAD_SECONDS;
    }

    private static boolean isOperationMode(String gameMode) {
        return gameMode != null && "Operation".equalsIgnoreCase(gameMode.trim());
    }
}
