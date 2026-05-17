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
    void cooldownEnd_usesEffectiveDuration() {
        Instant jump = Instant.parse("2026-05-17T12:00:00Z");
        assertEquals(
                jump.plusSeconds(CarrierJumpCooldown.COOLDOWN_SECONDS_EFFECTIVE),
                CarrierJumpCooldown.cooldownEndFromJump(jump));
    }

    @Test
    void departureRestorable_inHyperspaceWindow() {
        Instant now = Instant.parse("2026-05-17T12:00:00Z");
        Instant departure = now.minusSeconds(5 * 60);
        assertTrue(CarrierJumpCooldown.isDepartureRestorable(departure, now));
    }

    @Test
    void carrierLocationArrival_matchesDepartureTime() {
        Instant departure = Instant.parse("2026-05-17T20:51:10Z");
        Instant location = Instant.parse("2026-05-17T20:51:10Z");
        assertTrue(CarrierJumpCooldown.isCarrierLocationJumpArrival(location, departure));
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
