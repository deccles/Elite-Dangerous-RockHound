package org.dce.ed.util;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Deduplicates Spansh lookups and prevents immediate retries after a failure. */
final class SpanshRequestTracker {
    private final Clock clock;
    private final long failureCooldownMillis;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> retryAfter = new ConcurrentHashMap<>();

    SpanshRequestTracker(Clock clock, Duration failureCooldown) {
        this.clock = clock;
        this.failureCooldownMillis = Math.max(0L, failureCooldown.toMillis());
    }

    boolean tryStart(String key) {
        long now = clock.millis();
        Long blockedUntil = retryAfter.get(key);
        if (blockedUntil != null) {
            if (now < blockedUntil.longValue()) {
                return false;
            }
            retryAfter.remove(key, blockedUntil);
        }
        return inFlight.add(key);
    }

    void succeeded(String key) {
        retryAfter.remove(key);
        inFlight.remove(key);
    }

    void failed(String key) {
        retryAfter.put(key, Long.valueOf(clock.millis() + failureCooldownMillis));
        inFlight.remove(key);
    }
}
