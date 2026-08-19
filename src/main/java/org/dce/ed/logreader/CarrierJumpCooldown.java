package org.dce.ed.logreader;

import java.time.Instant;

/**
 * Shared fleet-carrier jump cooldown timing.
 * <p>
 * Aboard-carrier jumps log {@code CarrierJump} at arrival; off-carrier owners get {@code CarrierLocation} at
 * {@code DepartureTime}. Each anchor aligns differently with the in-game 5-minute cooldown UI.
 */
public final class CarrierJumpCooldown {

    public static final int COOLDOWN_SECONDS = 5 * 60;
    /** Brief lockout imposed after cancelling a scheduled carrier jump. */
    public static final int CANCELLATION_COOLDOWN_SECONDS = 60;
    /** Empirical correction vs in-game UI when anchored to aboard {@code CarrierJump} journal time. */
    public static final int COOLDOWN_END_CORRECTION_SECONDS = -(10 + 60);
    /** Aboard {@code CarrierJump} completion (legacy effective duration). */
    public static final int COOLDOWN_SECONDS_ABOARD_EFFECTIVE = COOLDOWN_SECONDS + COOLDOWN_END_CORRECTION_SECONDS;
    /** @deprecated use {@link #COOLDOWN_SECONDS_ABOARD_EFFECTIVE} or {@link #cooldownDurationSeconds(boolean)} */
    @Deprecated
    public static final int COOLDOWN_SECONDS_EFFECTIVE = COOLDOWN_SECONDS_ABOARD_EFFECTIVE;

    /**
     * Restore in-flight countdown after restart only this long past {@code DepartureTime}.
     * Real FC jumps finish in about a minute; a long window left "FC jump 0:00" stuck after
     * restart because journal completion events are not replayed.
     */
    public static final long HYPERSPACE_RESTORE_MAX_AGE_SECONDS = 3L * 60L;

    /**
     * If no {@code CarrierJump} / matching {@code CarrierLocation} arrives within this many seconds
     * after {@code DepartureTime}, treat the countdown as complete (same-system hops and missed
     * journal events otherwise leave the title bar on "FC jump" forever).
     */
    public static final long COUNTDOWN_FORCE_COMPLETE_SECONDS = 3L * 60L;

    /**
     * Start cooldown on {@code CarrierJump} when the journal timestamp is this recent, even if we never had an
     * in-memory {@code CarrierJumpRequest} countdown.
     */
    public static final long LIVE_JUMP_MAX_AGE_SECONDS = 15L * 60L;
    /** Allow journal timestamps slightly ahead of the local clock. */
    public static final long LIVE_JUMP_FUTURE_SKEW_SECONDS = 120L;

    private CarrierJumpCooldown() {
    }

    public static int cooldownDurationSeconds(boolean offCarrierCompletion) {
        return offCarrierCompletion ? COOLDOWN_SECONDS : COOLDOWN_SECONDS_ABOARD_EFFECTIVE;
    }

    /** Aboard {@code CarrierJump} anchor (applies empirical correction). */
    public static Instant cooldownEndFromJump(Instant jumpTimestamp) {
        return cooldownEndFromJump(jumpTimestamp, false);
    }

    public static Instant cooldownEndFromJump(Instant jumpTimestamp, boolean offCarrierCompletion) {
        if (jumpTimestamp == null) {
            return null;
        }
        return jumpTimestamp.plusSeconds(cooldownDurationSeconds(offCarrierCompletion));
    }

    public static Instant cooldownEndFromCancellation(Instant cancellationTimestamp) {
        if (cancellationTimestamp == null) {
            return null;
        }
        return cancellationTimestamp.plusSeconds(CANCELLATION_COOLDOWN_SECONDS);
    }

    public static Instant execTriggerTimeFromCooldownEnd(Instant cooldownEnd) {
        if (cooldownEnd == null) {
            return null;
        }
        return cooldownEnd;
    }

    public static boolean isExecTriggerDue(Instant cooldownEnd, Instant now) {
        Instant triggerTime = execTriggerTimeFromCooldownEnd(cooldownEnd);
        return triggerTime != null && now != null && !now.isBefore(triggerTime);
    }

    public static boolean isJumpTimestampLive(Instant jumpTimestamp, Instant now) {
        if (jumpTimestamp == null || now == null) {
            return false;
        }
        return !jumpTimestamp.isBefore(now.minusSeconds(LIVE_JUMP_MAX_AGE_SECONDS))
                && !jumpTimestamp.isAfter(now.plusSeconds(LIVE_JUMP_FUTURE_SKEW_SECONDS));
    }

    public static boolean shouldStartOrResyncCooldown(
            boolean hadPendingCountdown,
            Instant jumpTimestamp,
            Instant existingCooldownEnd,
            Instant now) {
        return shouldStartOrResyncCooldown(hadPendingCountdown, jumpTimestamp, existingCooldownEnd, now, false);
    }

    public static boolean shouldStartOrResyncCooldown(
            boolean hadPendingCountdown,
            Instant jumpTimestamp,
            Instant existingCooldownEnd,
            Instant now,
            boolean offCarrierCompletion) {
        if (hadPendingCountdown) {
            return true;
        }
        if (jumpTimestamp == null || now == null) {
            return false;
        }
        if (isJumpTimestampLive(jumpTimestamp, now)) {
            return true;
        }
        Instant newEnd = cooldownEndFromJump(jumpTimestamp, offCarrierCompletion);
        return existingCooldownEnd != null && newEnd != null && newEnd.isBefore(existingCooldownEnd);
    }

    public static boolean isDepartureRestorable(Instant departure, Instant now) {
        if (departure == null || now == null) {
            return false;
        }
        if (departure.isAfter(now)) {
            return true;
        }
        return departure.plusSeconds(HYPERSPACE_RESTORE_MAX_AGE_SECONDS).isAfter(now);
    }

    /**
     * True when the scheduled departure is old enough that a live countdown should be forced into
     * cooldown even without a journal completion event.
     */
    public static boolean shouldForceCompleteCountdown(Instant departure, Instant now) {
        if (departure == null || now == null) {
            return false;
        }
        return !now.isBefore(departure.plusSeconds(COUNTDOWN_FORCE_COMPLETE_SECONDS));
    }

    /**
     * When the commander is not aboard the carrier, Elite logs {@code CarrierLocation} at
     * {@code DepartureTime} instead of {@code CarrierJump}. While aboard, wait for {@code CarrierJump} at arrival.
     */
    public static boolean shouldTreatCarrierLocationAsJumpCompletion(
            boolean commanderAboardFleetCarrier,
            Instant locationTimestamp,
            Instant departureTime,
            String locationSystem,
            long locationSystemAddress,
            String pendingTargetSystem,
            Long pendingTargetSystemAddress) {
        if (commanderAboardFleetCarrier) {
            return false;
        }
        if (!isCarrierLocationJumpArrival(locationTimestamp, departureTime)) {
            return false;
        }
        return carrierLocationMatchesPendingJump(
                locationSystem, locationSystemAddress, pendingTargetSystem, pendingTargetSystemAddress);
    }

    /**
     * When the commander is not aboard the carrier, Elite logs {@code CarrierLocation} at
     * {@code DepartureTime} instead of {@code CarrierJump}.
     */
    public static boolean isCarrierLocationJumpArrival(Instant locationTimestamp, Instant departureTime) {
        if (locationTimestamp == null || departureTime == null) {
            return false;
        }
        return !locationTimestamp.isBefore(departureTime.minusSeconds(5L))
                && !locationTimestamp.isAfter(departureTime.plusSeconds(120L));
    }

    public static boolean carrierLocationMatchesPendingJump(
            String locationSystem,
            long locationSystemAddress,
            String pendingTargetSystem,
            Long pendingTargetSystemAddress) {
        if (pendingTargetSystemAddress != null && pendingTargetSystemAddress.longValue() > 0L
                && locationSystemAddress == pendingTargetSystemAddress.longValue()) {
            return true;
        }
        if (pendingTargetSystem == null || pendingTargetSystem.isBlank()) {
            return false;
        }
        return locationSystem != null
                && locationSystem.trim().equalsIgnoreCase(pendingTargetSystem.trim());
    }
}
