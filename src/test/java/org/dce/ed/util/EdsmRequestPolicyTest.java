package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EdsmRequestPolicyTest {

    @Test
    void healthyTrafficMatchesExplorationBuddyConcurrencyWithoutLaunchDelay() {
        assertEquals(18, EdsmRequestPolicy.MAX_CONCURRENT_REQUESTS);
        assertEquals(0L, EdsmRequestPolicy.HEALTHY_MINIMUM_INTERVAL.toMillis());
    }

    @Test
    void headerlessRateLimitUsesMeasuredRecoveryCooldown() {
        assertEquals(35L, EdsmRequestPolicy.HEADERLESS_RATE_LIMIT_COOLDOWN.toSeconds());
    }
}
