package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class EdsmRequestGateTest {

    @Test
    void repeatedQueryWithinOneDayUsesPersistedResponse() throws Exception {
        MemoryStore store = new MemoryStore();
        MutableClock clock = new MutableClock();
        EdsmRequestGate first = gate(store, clock);
        AtomicInteger calls = new AtomicInteger();

        EdsmRequestGate.Response original = first.execute("bodies:sol", () -> {
            calls.incrementAndGet();
            return new EdsmRequestGate.Response(200, "application/json", "{\"name\":\"Sol\"}");
        });
        EdsmRequestGate second = gate(store, clock);
        EdsmRequestGate.Response cached = second.execute("bodies:sol", () -> {
            calls.incrementAndGet();
            return new EdsmRequestGate.Response(200, "application/json", "{}");
        });

        assertEquals(1, calls.get());
        assertEquals(original.body(), cached.body());
    }

    @Test
    void failedQueryIsNotRetriedWithinOneDay() throws Exception {
        MemoryStore store = new MemoryStore();
        MutableClock clock = new MutableClock();
        EdsmRequestGate gate = gate(store, clock);
        AtomicInteger calls = new AtomicInteger();

        EdsmRequestGate.Response first = gate.execute("bodies:sol", () -> {
            calls.incrementAndGet();
            return new EdsmRequestGate.Response(429, "text/plain", "error code: 1015");
        });
        EdsmRequestGate.Response second = gate.execute("bodies:sol", () -> {
            calls.incrementAndGet();
            return new EdsmRequestGate.Response(200, "application/json", "{}");
        });

        assertEquals(1, calls.get());
        assertEquals(429, first.statusCode());
        assertEquals(429, second.statusCode());
    }

    @Test
    void connectionFailureCountsAsTheDailyAttempt() {
        MemoryStore store = new MemoryStore();
        MutableClock clock = new MutableClock();
        EdsmRequestGate gate = gate(store, clock);
        AtomicInteger calls = new AtomicInteger();

        assertThrows(IOException.class, () -> gate.execute("bodies:sol", () -> {
            calls.incrementAndGet();
            throw new IOException("timeout");
        }));
        assertThrows(IOException.class, () -> gate.execute("bodies:sol", () -> {
            calls.incrementAndGet();
            return new EdsmRequestGate.Response(200, "application/json", "{}");
        }));

        assertEquals(1, calls.get());
    }

    @Test
    void rateLimitCachesSkippedQueriesDuringGlobalCooldown() throws Exception {
        MemoryStore store = new MemoryStore();
        MutableClock clock = new MutableClock();
        EdsmRequestGate gate = gate(store, clock);
        gate.execute("bodies:sol", () -> new EdsmRequestGate.Response(429, "text/plain", "limited"));
        AtomicInteger calls = new AtomicInteger();

        EdsmRequestGate.Response skipped = gate.execute("system:alpha", () -> {
            calls.incrementAndGet();
            return new EdsmRequestGate.Response(200, "application/json", "{}");
        });

        assertEquals(0, calls.get());
        assertEquals(429, skipped.statusCode());
        assertEquals(true, skipped.fromCache());
        assertEquals(429, store.get("system:alpha").response().statusCode());
    }

    @Test
    void distinctQueriesArePacedSequentially() throws Exception {
        MemoryStore store = new MemoryStore();
        MutableClock clock = new MutableClock();
        AtomicInteger sleptMillis = new AtomicInteger();
        EdsmRequestGate gate = new EdsmRequestGate(store, clock, millis -> {
            sleptMillis.addAndGet((int) millis);
            clock.advanceMillis(millis);
        }, Duration.ofDays(1), Duration.ofSeconds(1), Duration.ofMinutes(1));

        gate.execute("bodies:sol", () -> new EdsmRequestGate.Response(200, "application/json", "{}"));
        gate.execute("bodies:alpha", () -> new EdsmRequestGate.Response(200, "application/json", "{}"));

        assertEquals(1000, sleptMillis.get());
    }

    private static EdsmRequestGate gate(MemoryStore store, MutableClock clock) {
        return new EdsmRequestGate(store, clock, clock::advanceMillis,
                Duration.ofDays(1), Duration.ZERO, Duration.ofMinutes(1));
    }

    private static final class MemoryStore implements EdsmRequestGate.CacheStore {
        private final Map<String, EdsmRequestGate.CacheEntry> entries = new HashMap<>();

        @Override
        public EdsmRequestGate.CacheEntry get(String key) {
            return entries.get(key);
        }

        @Override
        public void put(String key, EdsmRequestGate.CacheEntry entry) {
            entries.put(key, entry);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-08-20T00:00:00Z");

        void advanceMillis(long millis) {
            now = now.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
