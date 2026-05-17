package org.dce.ed.logreader;

import java.time.Instant;

/**
 * Shared fleet-carrier jump cooldown timing (journal {@code CarrierJump} anchor).
 */
public final class CarrierJumpCooldown {

    public static final int COOLDOWN_SECONDS = 5 * 60;
    /** Empirical correction vs in-game UI when anchored to {@code CarrierJump} journal time. */
    public static final int COOLDOWN_END_CORRECTION_SECONDS = -(10 + 60);
    public static final int COOLDOWN_SECONDS_EFFECTIVE = COOLDOWN_SECONDS + COOLDOWN_END_CORRECTION_SECONDS;

    /** Restore in-flight countdown after restart for this long past {@code DepartureTime}. */
    public static final long HYPERSPACE_RESTORE_MAX_AGE_SECONDS = 20L * 60L;

    /**
     * Start cooldown on {@code CarrierJump} when the journal timestamp is this recent, even if we never had an
     * in-memory {@code CarrierJumpRequest} countdown.
     */
    public static final long LIVE_JUMP_MAX_AGE_SECONDS = 15L * 60L;
    /** Allow journal timestamps slightly ahead of the local clock. */
    public static final long LIVE_JUMP_FUTURE_SKEW_SECONDS = 120L;

    private CarrierJumpCooldown() {
    }

    public static Instant cooldownEndFromJump(Instant jumpTimestamp) {
        if (jumpTimestamp == null) {
            return null;
        }
        return jumpTimestamp.plusSeconds(COOLDOWN_SECONDS_EFFECTIVE);
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
        if (hadPendingCountdown) {
            return true;
        }
        if (jumpTimestamp == null || now == null) {
            return false;
        }
        if (isJumpTimestampLive(jumpTimestamp, now)) {
            return true;
        }
        Instant newEnd = cooldownEndFromJump(jumpTimestamp);
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
