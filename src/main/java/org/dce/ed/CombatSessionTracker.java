package org.dce.ed;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.CombatRewardEvent;
import org.dce.ed.session.CombatSessionData;
import org.dce.ed.session.EdoSessionState;

/**
 * Tracks combat credits earned after dropping from supercruise until returning to it.
 * A session is only displayed once it receives its first qualifying ship-kill reward.
 */
public final class CombatSessionTracker {

    /** Immutable state used by the Combat tab and persistence wiring. */
    public record Snapshot(
            Instant candidateExitAt,
            Instant startedAt,
            Instant endedAt,
            long earnedCredits,
            long creditsPerHour,
            boolean active,
            boolean hasDisplayedSession) {

        public Instant getCandidateExitAt() { return candidateExitAt; }
        public Instant getStartedAt() { return startedAt; }
        public Instant getEndedAt() { return endedAt; }
        public long getEarnedCredits() { return earnedCredits; }
        public long getCreditsPerHour() { return creditsPerHour; }
        public boolean isActive() { return active; }
        public boolean hasDisplayedSession() { return hasDisplayedSession; }
    }

    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private Instant candidateExitAt;
    private Instant startedAt;
    private Instant endedAt;
    private long earnedCredits;
    private boolean active;

    public void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Applies the subset of journal events that affects a combat session.
     *
     * @return {@code true} when the externally visible session state changed
     */
    public synchronized boolean applyJournalEvent(EliteLogEvent event) {
        if (event == null) {
            return false;
        }
        if (event.getType() == EliteEventType.SUPERCRUISE_EXIT) {
            candidateExitAt = event.getTimestamp();
            notifyListeners();
            return true;
        }
        if (event.getType() == EliteEventType.SUPERCRUISE_ENTRY) {
            if (!active) {
                return false;
            }
            active = false;
            candidateExitAt = null;
            endedAt = event.getTimestamp();
            notifyListeners();
            return true;
        }
        if (event.getType() == EliteEventType.REDEEM_VOUCHER) {
            if (!hasDisplayedSessionInternal() && candidateExitAt == null) {
                return false;
            }
            clear();
            notifyListeners();
            return true;
        }
        if (!(event instanceof CombatRewardEvent reward)) {
            return false;
        }
        long amount = Math.max(0L, reward.getCombatReward());
        if (amount <= 0L) {
            return false;
        }
        if (active) {
            earnedCredits = saturatedAdd(earnedCredits, amount);
            notifyListeners();
            return true;
        }
        if (candidateExitAt == null) {
            return false;
        }
        startedAt = candidateExitAt;
        endedAt = null;
        earnedCredits = amount;
        active = true;
        notifyListeners();
        return true;
    }

    /** Returns a deterministic snapshot calculated against the supplied instant. */
    public synchronized Snapshot snapshot(Instant now) {
        Instant effectiveNow = now != null ? now : Instant.EPOCH;
        boolean displayed = hasDisplayedSessionInternal();
        Instant end = active ? effectiveNow : endedAt;
        long rate = displayed ? creditsPerHour(startedAt, end, earnedCredits) : 0L;
        return new Snapshot(candidateExitAt, startedAt, end, earnedCredits, rate, active, displayed);
    }

    /** Adds this tracker’s restart-safe state to the shared combat session data. */
    public synchronized void fillSessionState(EdoSessionState state) {
        if (state == null) {
            return;
        }
        CombatSessionData combat = state.getCombat();
        if (combat == null) {
            combat = new CombatSessionData();
            state.setCombat(combat);
        }
        combat.setCreditsSessionCandidateExitAt(formatInstant(candidateExitAt));
        combat.setCreditsSessionStartedAt(formatInstant(startedAt));
        combat.setCreditsSessionEndedAt(formatInstant(endedAt));
        combat.setCreditsSessionEarnedCredits(earnedCredits);
        combat.setCreditsSessionActive(active);
    }

    /** Restores this tracker from the shared combat session data, tolerating older or malformed data. */
    public synchronized void applySessionState(EdoSessionState state) {
        clear();
        if (state == null || state.getCombat() == null) {
            return;
        }
        CombatSessionData combat = state.getCombat();
        candidateExitAt = parseInstant(combat.getCreditsSessionCandidateExitAt());
        startedAt = parseInstant(combat.getCreditsSessionStartedAt());
        endedAt = parseInstant(combat.getCreditsSessionEndedAt());
        earnedCredits = Math.max(0L, combat.getCreditsSessionEarnedCredits());
        active = combat.isCreditsSessionActive() && startedAt != null;
        if (active) {
            endedAt = null;
        }
        notifyListeners();
    }

    private boolean hasDisplayedSessionInternal() {
        return startedAt != null;
    }

    private void clear() {
        candidateExitAt = null;
        startedAt = null;
        endedAt = null;
        earnedCredits = 0L;
        active = false;
    }

    private static long creditsPerHour(Instant start, Instant end, long earned) {
        if (start == null || end == null || earned <= 0L) {
            return 0L;
        }
        long elapsedSeconds = Math.max(1L, Duration.between(start, end).getSeconds());
        if (elapsedSeconds < 1L) {
            elapsedSeconds = 1L;
        }
        if (earned > Long.MAX_VALUE / 3_600L) {
            return Long.MAX_VALUE;
        }
        return earned * 3_600L / elapsedSeconds;
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // Listener failures must not interrupt journal delivery.
            }
        }
    }
}
