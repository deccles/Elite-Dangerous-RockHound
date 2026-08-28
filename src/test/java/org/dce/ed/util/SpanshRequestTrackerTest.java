package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class SpanshRequestTrackerTest {

    @Test
    void deduplicatesInFlightRequestAndAppliesFailureCooldown() {
        MutableClock clock = new MutableClock();
        SpanshRequestTracker tracker = new SpanshRequestTracker(clock, Duration.ofMinutes(5));

        assertTrue(tracker.tryStart("system\tbody"));
        assertFalse(tracker.tryStart("system\tbody"));

        tracker.failed("system\tbody");
        assertFalse(tracker.tryStart("system\tbody"));

        clock.advance(Duration.ofMinutes(5));
        assertTrue(tracker.tryStart("system\tbody"));
    }

    @Test
    void successfulRequestAllowsFutureRefreshWithoutCooldown() {
        MutableClock clock = new MutableClock();
        SpanshRequestTracker tracker = new SpanshRequestTracker(clock, Duration.ofMinutes(5));

        assertTrue(tracker.tryStart("system\tbody"));
        tracker.succeeded("system\tbody");

        assertTrue(tracker.tryStart("system\tbody"));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-08-26T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
