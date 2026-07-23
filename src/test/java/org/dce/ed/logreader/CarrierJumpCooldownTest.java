package org.dce.ed.logreader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class CarrierJumpCooldownTest {

    @Test
    void shouldStart_whenPendingCountdownEvenIfJumpTimestampStale() {
        Instant now = Instant.parse("2026-05-17T12:00:00Z");
        Instant oldJump = now.minusSeconds(3600);
        assertTrue(CarrierJumpCooldown.shouldStartOrResyncCooldown(true, oldJump, null, now));
    }

    @Test
    void shouldStart_whenJumpTimestampLiveWithoutCountdown() {
        Instant now = Instant.parse("2026-05-17T12:00:00Z");
        Instant jump = now.minusSeconds(60);
        assertTrue(CarrierJumpCooldown.shouldStartOrResyncCooldown(false, jump, null, now));
    }

    @Test
    void shouldNotStart_whenJumpTooOldAndNoCountdown() {
        Instant now = Instant.parse("2026-05-17T12:00:00Z");
        Instant jump = now.minusSeconds(CarrierJumpCooldown.LIVE_JUMP_MAX_AGE_SECONDS + 1);
        assertFalse(CarrierJumpCooldown.shouldStartOrResyncCooldown(false, jump, null, now));
    }

    @Test
    void shouldResync_whenNewEndIsEarlier() {
        Instant now = Instant.parse("2026-05-17T12:00:00Z");
        Instant jump = now.minusSeconds(30);
        Instant existingEnd = now.plusSeconds(400);
        assertTrue(CarrierJumpCooldown.shouldStartOrResyncCooldown(false, jump, existingEnd, now));
    }

    @Test
    void cooldownEnd_aboardCarrierJump_usesEffectiveDuration() {
        Instant jump = Instant.parse("2026-05-17T12:00:00Z");
        assertEquals(
                jump.plusSeconds(CarrierJumpCooldown.COOLDOWN_SECONDS_ABOARD_EFFECTIVE),
                CarrierJumpCooldown.cooldownEndFromJump(jump));
        assertEquals(
                jump.plusSeconds(CarrierJumpCooldown.COOLDOWN_SECONDS_ABOARD_EFFECTIVE),
                CarrierJumpCooldown.cooldownEndFromJump(jump, false));
    }

    @Test
    void cooldownEnd_offCarrierLocation_usesFullFiveMinutes() {
        Instant location = Instant.parse("2026-06-15T21:14:06Z");
        Instant end = CarrierJumpCooldown.cooldownEndFromJump(location, true);
        assertEquals(location.plusSeconds(5 * 60), end);
        // Regression: off-carrier CarrierLocation aligns with full in-game cooldown (~17:19 local).
        assertEquals(Instant.parse("2026-06-15T21:19:06Z"), end);
    }

    @Test
    void cooldownDuration_offCarrierIsLongerThanAboard() {
        assertTrue(CarrierJumpCooldown.cooldownDurationSeconds(true)
                > CarrierJumpCooldown.cooldownDurationSeconds(false));
    }

    @Test
    void execTrigger_firesTwentySecondsAfterCooldownEnd() {
        Instant end = Instant.parse("2026-06-15T21:19:06Z");
        Instant trigger = CarrierJumpCooldown.execTriggerTimeFromCooldownEnd(end);
        assertEquals(Instant.parse("2026-06-15T21:19:26Z"), trigger);
        assertFalse(CarrierJumpCooldown.isExecTriggerDue(end, Instant.parse("2026-06-15T21:19:25Z")));
        assertTrue(CarrierJumpCooldown.isExecTriggerDue(end, Instant.parse("2026-06-15T21:19:26Z")));
    }

    @Test
    void departureRestorable_inHyperspaceWindow() {
        Instant now = Instant.parse("2026-05-17T12:00:00Z");
        Instant departure = now.minusSeconds(2 * 60);
        assertTrue(CarrierJumpCooldown.isDepartureRestorable(departure, now));
        assertFalse(CarrierJumpCooldown.isDepartureRestorable(
                now.minusSeconds(CarrierJumpCooldown.HYPERSPACE_RESTORE_MAX_AGE_SECONDS + 1), now));
    }

    @Test
    void forceCompleteCountdown_afterGracePastDeparture() {
        Instant departure = Instant.parse("2026-05-17T12:00:00Z");
        assertFalse(CarrierJumpCooldown.shouldForceCompleteCountdown(
                departure, departure.plusSeconds(CarrierJumpCooldown.COUNTDOWN_FORCE_COMPLETE_SECONDS - 1)));
        assertTrue(CarrierJumpCooldown.shouldForceCompleteCountdown(
                departure, departure.plusSeconds(CarrierJumpCooldown.COUNTDOWN_FORCE_COMPLETE_SECONDS)));
    }

    @Test
    void carrierLocationArrival_matchesDepartureTime() {
        Instant departure = Instant.parse("2026-05-17T20:51:10Z");
        Instant location = Instant.parse("2026-05-17T20:51:10Z");
        assertTrue(CarrierJumpCooldown.isCarrierLocationJumpArrival(location, departure));
    }

    /**
     * Aboard-carrier arrivals are logged as {@code CarrierJump} many minutes after {@code DepartureTime}.
     * Do not treat elapsed time past departure as jump completion (regression: premature "Jump complete" TTS).
     */
    @Test
    void carrierLocationArrival_doesNotMatchRealAboardArrivalTime() {
        Instant departure = Instant.parse("2026-05-17T12:00:00Z");
        Instant aboardArrival = departure.plusSeconds(15 * 60);
        assertFalse(CarrierJumpCooldown.isCarrierLocationJumpArrival(aboardArrival, departure));
    }

    @Test
    void carrierLocationCompletion_ignoredWhenAboard() {
        Instant departure = Instant.parse("2026-05-17T12:00:00Z");
        Instant atDeparture = departure;
        assertFalse(CarrierJumpCooldown.shouldTreatCarrierLocationAsJumpCompletion(
                true,
                atDeparture,
                departure,
                "Target",
                1L,
                "Target",
                1L));
        assertTrue(CarrierJumpCooldown.shouldTreatCarrierLocationAsJumpCompletion(
                false,
                atDeparture,
                departure,
                "Target",
                1L,
                "Target",
                1L));
    }

    @Test
    void carrierLocationMatches_byNameOrAddress() {
        assertTrue(CarrierJumpCooldown.carrierLocationMatchesPendingJump(
                "Eor Aowsy EW-O b6-163", 359052958122041L, "Eor Aowsy EW-O b6-163", 359052958122041L));
        assertTrue(CarrierJumpCooldown.carrierLocationMatchesPendingJump(
                "Eor Aowsy EW-O b6-163", 0L, "eor aowsy ew-o b6-163", null));
        assertFalse(CarrierJumpCooldown.carrierLocationMatchesPendingJump(
                "Other", 1L, "Eor Aowsy EW-O b6-163", 359052958122041L));
    }
}
