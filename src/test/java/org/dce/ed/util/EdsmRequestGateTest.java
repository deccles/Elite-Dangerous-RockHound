package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    void rateLimitedQueryRetriesAfterCooldownInsteadOfPoisoningDailyCache() throws Exception {
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

        assertEquals(2, calls.get());
        assertEquals(429, first.statusCode());
        assertEquals(200, second.statusCode());
    }

    @Test
    void connectionFailureDoesNotCountAsTheDailyAttempt() throws Exception {
        MemoryStore store = new MemoryStore();
        MutableClock clock = new MutableClock();
        EdsmRequestGate gate = gate(store, clock);
        AtomicInteger calls = new AtomicInteger();

        assertThrows(IOException.class, () -> gate.execute("bodies:sol", () -> {
            calls.incrementAndGet();
            throw new IOException("timeout");
        }));
        EdsmRequestGate.Response recovered = gate.execute("bodies:sol", () -> {
            calls.incrementAndGet();
            return new EdsmRequestGate.Response(200, "application/json", "{}");
        });

        assertEquals(2, calls.get());
        assertEquals(200, recovered.statusCode());
    }

    @Test
    void staleFailureFromOlderVersionIsIgnored() throws Exception {
        MemoryStore store = new MemoryStore();
        MutableClock clock = new MutableClock();
        store.put("bodies:sol", new EdsmRequestGate.CacheEntry(clock.millis(),
                new EdsmRequestGate.Response(429, "text/plain", "limited")));
        EdsmRequestGate gate = gate(store, clock);
        AtomicInteger calls = new AtomicInteger();

        EdsmRequestGate.Response response = gate.execute("bodies:sol", () -> {
            calls.incrementAndGet();
            return new EdsmRequestGate.Response(200, "application/json", "{}");
        });

        assertEquals(1, calls.get());
        assertEquals(200, response.statusCode());
    }

    @Test
    void rateLimitWaitsThenExecutesDifferentQueryWithoutCachingASkip() throws Exception {
        MemoryStore store = new MemoryStore();
        MutableClock clock = new MutableClock();
        EdsmRequestGate gate = gate(store, clock);
        gate.execute("bodies:sol", () -> new EdsmRequestGate.Response(429, "text/plain", "limited"));
        AtomicInteger calls = new AtomicInteger();

        EdsmRequestGate.Response skipped = gate.execute("system:alpha", () -> {
            calls.incrementAndGet();
            return new EdsmRequestGate.Response(200, "application/json", "{}");
        });

        assertEquals(1, calls.get());
        assertEquals(200, skipped.statusCode());
        assertEquals(false, skipped.fromCache());
        assertEquals(200, store.get("system:alpha").response().statusCode());
    }

    @Test
    void pausesAtSeventyPercentCapacityUntilServerReset() throws Exception {
        MemoryStore store = new MemoryStore();
        MutableClock clock = new MutableClock();
        AtomicInteger sleptMillis = new AtomicInteger();
        EdsmRequestGate gate = new EdsmRequestGate(store, clock, millis -> {
            sleptMillis.addAndGet((int) millis);
            clock.advanceMillis(millis);
        }, Duration.ofDays(1), Duration.ZERO, Duration.ofMinutes(1), 2, 0.70);
        long resetEpochSeconds = clock.instant().plusSeconds(12).getEpochSecond();

        gate.execute("bodies:sol", () -> new EdsmRequestGate.Response(200, "application/json", "{}",
                Map.of("x-rate-limit-limit", "100", "x-rate-limit-remaining", "30",
                        "x-rate-limit-reset", Long.toString(resetEpochSeconds))));
        gate.execute("bodies:alpha", () -> new EdsmRequestGate.Response(200, "application/json", "{}"));

        assertEquals(12_000, sleptMillis.get());
    }

    @Test
    void allowsTwoNetworkRequestsInFlight() throws Exception {
        EdsmRequestGate gate = new EdsmRequestGate(new MemoryStore(), Clock.systemUTC(), Thread::sleep,
                Duration.ofDays(1), Duration.ZERO, Duration.ofMillis(50), 2, 0.70);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();
        Runnable query = () -> {
            try {
                gate.execute("key-" + Thread.currentThread().getName(), () -> {
                    entered.countDown();
                    try {
                        release.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("interrupted", e);
                    }
                    return new EdsmRequestGate.Response(200, "application/json", "{}");
                });
                completed.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        Thread first = new Thread(query, "one");
        Thread second = new Thread(query, "two");
        first.start();
        second.start();

        assertTrue(entered.await(1, TimeUnit.SECONDS));
        release.countDown();
        first.join(2_000);
        second.join(2_000);
        assertEquals(2, completed.get());
    }

    @Test
    void allowsEighteenHealthyRequestsThenRefillsFreedSlots() throws Exception {
        EdsmRequestGate gate = new EdsmRequestGate(new MemoryStore(), Clock.systemUTC(), Thread::sleep,
                Duration.ofDays(1), Duration.ZERO, Duration.ofSeconds(35), 18, 0.70);
        CountDownLatch firstBatchEntered = new CountDownLatch(18);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger entered = new AtomicInteger();
        Thread[] requests = new Thread[19];
        for (int i = 0; i < requests.length; i++) {
            int request = i;
            requests[i] = new Thread(() -> {
                try {
                    gate.execute("parallel-" + request, () -> {
                        entered.incrementAndGet();
                        firstBatchEntered.countDown();
                        try {
                            release.await(2, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return new EdsmRequestGate.Response(200, "application/json", "{}");
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            requests[i].start();
        }
        try {
            assertTrue(firstBatchEntered.await(1, TimeUnit.SECONDS));
            Thread.sleep(100L);
            assertEquals(18, entered.get());
        } finally {
            release.countDown();
            for (Thread request : requests) {
                request.join(2_000);
            }
        }
        assertEquals(19, entered.get());
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

    @Test
    void emergencyPacingDoesNotSurviveCreatingANewGate() throws Exception {
        MemoryStore cache = new MemoryStore();
        MemoryLearnedPacingStore learned = new MemoryLearnedPacingStore();
        MutableClock clock = new MutableClock();
        learned.saveIntervalMillis(10_000L);

        AtomicInteger sleptMillis = new AtomicInteger();
        EdsmRequestGate restarted = new EdsmRequestGate(cache, clock, millis -> {
            sleptMillis.addAndGet((int) millis);
            clock.advanceMillis(millis);
        }, Duration.ofDays(1), Duration.ofMillis(250), Duration.ZERO, 2, 0.70, learned);
        restarted.execute("success-1", () -> new EdsmRequestGate.Response(200, "application/json", "{}"));
        restarted.execute("success-2", () -> new EdsmRequestGate.Response(200, "application/json", "{}"));

        assertEquals(250, sleptMillis.get());
    }

    @Test
    void healthyResponsesQuicklyRestoreNormalPacingAfterRateLimit() throws Exception {
        MemoryStore cache = new MemoryStore();
        MutableClock clock = new MutableClock();
        java.util.List<Long> sleeps = new java.util.ArrayList<>();
        EdsmRequestGate gate = new EdsmRequestGate(cache, clock, millis -> {
            sleeps.add(Long.valueOf(millis));
            clock.advanceMillis(millis);
        }, Duration.ofDays(1), Duration.ofMillis(250), Duration.ZERO, 2, 0.70,
                new MemoryLearnedPacingStore());

        gate.execute("limited", () -> new EdsmRequestGate.Response(429, "text/plain", "limited"));
        gate.execute("success-1", () -> new EdsmRequestGate.Response(200, "application/json", "{}"));
        gate.execute("success-2", () -> new EdsmRequestGate.Response(200, "application/json", "{}"));
        gate.execute("success-3", () -> new EdsmRequestGate.Response(200, "application/json", "{}"));
        gate.execute("success-4", () -> new EdsmRequestGate.Response(200, "application/json", "{}"));

        assertEquals(250L, sleeps.get(sleeps.size() - 1).longValue());
    }

    @Test
    void clusteredRateLimitsTemporarilyReduceConcurrencyToOne() throws Exception {
        MemoryLearnedPacingStore learned = new MemoryLearnedPacingStore();
        EdsmRequestGate gate = new EdsmRequestGate(new MemoryStore(), Clock.systemUTC(), Thread::sleep,
                Duration.ofDays(1), Duration.ZERO, Duration.ZERO, 2, 0.70, learned);
        gate.execute("limited-1", () -> new EdsmRequestGate.Response(429, "text/plain", "limited"));

        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger entered = new AtomicInteger();
        Runnable query = () -> {
            try {
                gate.execute("serial-" + Thread.currentThread().getName(), () -> {
                    entered.incrementAndGet();
                    firstEntered.countDown();
                    try {
                        release.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new EdsmRequestGate.Response(200, "application/json", "{}");
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
        Thread one = new Thread(query, "one");
        Thread two = new Thread(query, "two");
        one.start();
        two.start();
        try {
            assertTrue(firstEntered.await(1, TimeUnit.SECONDS));
            Thread.sleep(100);
            assertEquals(1, entered.get());
        } finally {
            release.countDown();
            one.join(2_000);
            two.join(2_000);
        }
    }

    @Test
    void headerlessRateLimitTriesThirtyFiveSecondsThenDoublesAfterAnother429() throws Exception {
        MutableClock clock = new MutableClock();
        AtomicInteger sleptMillis = new AtomicInteger();
        EdsmRequestGate gate = new EdsmRequestGate(new MemoryStore(), clock, millis -> {
            sleptMillis.addAndGet((int) millis);
            clock.advanceMillis(millis);
        }, Duration.ofDays(1), Duration.ZERO, Duration.ofSeconds(35), 2, 0.70);

        gate.execute("limited-1", () -> new EdsmRequestGate.Response(429, "text/plain", "error code: 1015"));
        gate.execute("limited-2", () -> new EdsmRequestGate.Response(429, "text/plain", "error code: 1015"));
        gate.execute("recovered", () -> new EdsmRequestGate.Response(200, "application/json", "{}"));

        assertEquals(105_000, sleptMillis.get());
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

    private static final class MemoryLearnedPacingStore implements EdsmRequestGate.LearnedPacingStore {
        private long intervalMillis;

        @Override
        public long loadIntervalMillis() {
            return intervalMillis;
        }

        @Override
        public void saveIntervalMillis(long intervalMillis) {
            this.intervalMillis = intervalMillis;
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
