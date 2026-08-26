package org.dce.ed.util;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Coordinates, adapts, and caches application-wide EDSM HTTP requests. */
public final class EdsmRequestGate {

    private static final long MAX_ADAPTIVE_INTERVAL_MILLIS = 10_000L;
    private static final int SUCCESSES_TO_RECOVER = 3;

    private final CacheStore store;
    private final Clock clock;
    private final Sleeper sleeper;
    private final long cacheTtlMillis;
    private final long baseMinimumIntervalMillis;
    private final long rateLimitCooldownMillis;
    private final double capacityUseThreshold;
    private final int configuredMaxConcurrentRequests;
    private final LearnedPacingStore learnedPacingStore;
    private final Object stateLock = new Object();
    private long currentMinimumIntervalMillis;
    private long lastNetworkRequestAt = Long.MIN_VALUE;
    private long cooldownUntil;
    private int activeNetworkRequests;
    private long currentRateLimitCooldownMillis;
    private boolean singleRequestMode;
    private int recoverySuccesses;

    public record Response(int statusCode, String contentType, String body,
            Map<String, String> headers, boolean fromCache) {
        public Response {
            headers = normalizedHeaders(headers);
        }

        public Response(int statusCode, String contentType, String body) {
            this(statusCode, contentType, body, Map.of(), false);
        }

        public Response(int statusCode, String contentType, String body, boolean fromCache) {
            this(statusCode, contentType, body, Map.of(), fromCache);
        }

        public Response(int statusCode, String contentType, String body, Map<String, String> headers) {
            this(statusCode, contentType, body, headers, false);
        }

        public String header(String name) {
            return name == null ? null : headers.get(name.toLowerCase(Locale.ROOT));
        }

        Response cachedCopy() {
            return new Response(statusCode, contentType, body, headers, true);
        }

        private static Map<String, String> normalizedHeaders(Map<String, String> input) {
            if (input == null || input.isEmpty()) {
                return Map.of();
            }
            Map<String, String> normalized = new HashMap<>();
            input.forEach((key, value) -> {
                if (key != null && value != null) {
                    normalized.put(key.toLowerCase(Locale.ROOT), value);
                }
            });
            return Collections.unmodifiableMap(normalized);
        }
    }

    public record CacheEntry(long queriedAtEpochMillis, Response response) {
    }

    public interface CacheStore {
        CacheEntry get(String key);
        void put(String key, CacheEntry entry);
    }

    @FunctionalInterface
    public interface NetworkCall {
        Response execute() throws IOException;
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public interface LearnedPacingStore {
        long loadIntervalMillis();
        void saveIntervalMillis(long intervalMillis);
    }

    private static final LearnedPacingStore NO_LEARNED_PACING = new LearnedPacingStore() {
        @Override public long loadIntervalMillis() { return 0L; }
        @Override public void saveIntervalMillis(long intervalMillis) { }
    };

    public EdsmRequestGate(CacheStore store, Clock clock, Sleeper sleeper,
            Duration cacheTtl, Duration minimumInterval, Duration rateLimitCooldown) {
        this(store, clock, sleeper, cacheTtl, minimumInterval, rateLimitCooldown, 1, 0.70);
    }

    public EdsmRequestGate(CacheStore store, Clock clock, Sleeper sleeper,
            Duration cacheTtl, Duration minimumInterval, Duration rateLimitCooldown,
            int maxConcurrentRequests, double capacityUseThreshold) {
        this(store, clock, sleeper, cacheTtl, minimumInterval, rateLimitCooldown,
                maxConcurrentRequests, capacityUseThreshold, NO_LEARNED_PACING);
    }

    public EdsmRequestGate(CacheStore store, Clock clock, Sleeper sleeper,
            Duration cacheTtl, Duration minimumInterval, Duration rateLimitCooldown,
            int maxConcurrentRequests, double capacityUseThreshold,
            LearnedPacingStore learnedPacingStore) {
        this.store = store;
        this.clock = clock;
        this.sleeper = sleeper;
        this.cacheTtlMillis = cacheTtl.toMillis();
        this.baseMinimumIntervalMillis = Math.max(0L, minimumInterval.toMillis());
        this.learnedPacingStore = learnedPacingStore != null ? learnedPacingStore : NO_LEARNED_PACING;
        // Rate-limit backoff is deliberately session-local. Carrying an emergency
        // interval into a later launch can make a healthy route take minutes to load.
        this.currentMinimumIntervalMillis = this.baseMinimumIntervalMillis;
        this.rateLimitCooldownMillis = Math.max(0L, rateLimitCooldown.toMillis());
        this.currentRateLimitCooldownMillis = this.rateLimitCooldownMillis;
        this.capacityUseThreshold = Math.max(0.0, Math.min(1.0, capacityUseThreshold));
        this.configuredMaxConcurrentRequests = Math.max(1, maxConcurrentRequests);
    }

    public Response execute(String key, NetworkCall call) throws IOException {
        Response cached = cachedResponse(key);
        if (cached != null) {
            return cached;
        }
        acquireNetworkPermit();
        try {
            cached = cachedResponse(key);
            if (cached != null) {
                return cached;
            }
            awaitLaunchWindow();
            Response response = call.execute();
            recordResponse(key, response);
            return response;
        } finally {
            releaseNetworkPermit();
        }
    }

    private Response cachedResponse(String key) {
        synchronized (stateLock) {
            long now = clock.millis();
            CacheEntry cached = store.get(key);
            if (cached == null || now - cached.queriedAtEpochMillis() >= cacheTtlMillis) {
                return null;
            }
            if (cached.response().statusCode() < 200 || cached.response().statusCode() >= 300) {
                return null;
            }
            return cached.response().cachedCopy();
        }
    }

    private void acquireNetworkPermit() throws IOException {
        synchronized (stateLock) {
            try {
                while (activeNetworkRequests >= effectiveMaxConcurrentRequests()) {
                    stateLock.wait();
                }
                activeNetworkRequests++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for an EDSM request slot", e);
            }
        }
    }

    private void releaseNetworkPermit() {
        synchronized (stateLock) {
            activeNetworkRequests--;
            stateLock.notifyAll();
        }
    }

    private int effectiveMaxConcurrentRequests() {
        return singleRequestMode ? 1 : configuredMaxConcurrentRequests;
    }

    private void awaitLaunchWindow() throws IOException {
        synchronized (stateLock) {
            while (true) {
                long now = clock.millis();
                long cooldownWait = cooldownUntil - now;
                long pacingWait = lastNetworkRequestAt == Long.MIN_VALUE
                        ? 0L : currentMinimumIntervalMillis - (now - lastNetworkRequestAt);
                long wait = Math.max(cooldownWait, pacingWait);
                if (wait <= 0L) {
                    lastNetworkRequestAt = now;
                    return;
                }
                try {
                    sleeper.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while pacing EDSM requests", e);
                }
            }
        }
    }

    private void recordResponse(String key, Response response) {
        synchronized (stateLock) {
            long now = clock.millis();
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                store.put(key, new CacheEntry(now, response));
                recoverySuccesses++;
                if (recoverySuccesses >= SUCCESSES_TO_RECOVER) {
                    singleRequestMode = false;
                    recoverySuccesses = 0;
                    currentMinimumIntervalMillis = baseMinimumIntervalMillis;
                    learnedPacingStore.saveIntervalMillis(baseMinimumIntervalMillis);
                    stateLock.notifyAll();
                }
                currentRateLimitCooldownMillis = rateLimitCooldownMillis;
            } else {
                recoverySuccesses = 0;
            }

            applyServerCapacityHeaders(response, now);
            if (response.statusCode() == 429) {
                currentMinimumIntervalMillis = Math.min(MAX_ADAPTIVE_INTERVAL_MILLIS,
                        Math.max(baseMinimumIntervalMillis, Math.max(250L, currentMinimumIntervalMillis * 2L)));
                learnedPacingStore.saveIntervalMillis(currentMinimumIntervalMillis);
                cooldownUntil = Math.max(cooldownUntil, cooldownFromResponse(response, now));
                currentRateLimitCooldownMillis = Math.min(Duration.ofMinutes(10).toMillis(),
                        Math.max(rateLimitCooldownMillis, currentRateLimitCooldownMillis * 2L));
                singleRequestMode = true;
                recoverySuccesses = 0;
            }
        }
    }

    private void applyServerCapacityHeaders(Response response, long now) {
        Long limit = parseLong(response.header("x-rate-limit-limit"));
        Long remaining = parseLong(response.header("x-rate-limit-remaining"));
        if (limit == null || limit.longValue() <= 0L || remaining == null) {
            return;
        }
        double usedFraction = 1.0 - Math.max(0.0,
                Math.min(1.0, (double) remaining.longValue() / limit.longValue()));
        if (usedFraction + 1.0e-9 < capacityUseThreshold) {
            return;
        }
        Long resetEpochSeconds = parseLong(response.header("x-rate-limit-reset"));
        long resetAt = resetEpochSeconds != null
                ? resetEpochSeconds.longValue() * 1_000L
                : now + rateLimitCooldownMillis;
        cooldownUntil = Math.max(cooldownUntil, resetAt);
    }

    private long cooldownFromResponse(Response response, long now) {
        Long retryAfterSeconds = parseLong(response.header("retry-after"));
        if (retryAfterSeconds != null && retryAfterSeconds.longValue() >= 0L) {
            return now + retryAfterSeconds.longValue() * 1_000L;
        }
        Long resetEpochSeconds = parseLong(response.header("x-rate-limit-reset"));
        if (resetEpochSeconds != null) {
            return resetEpochSeconds.longValue() * 1_000L;
        }
        return now + currentRateLimitCooldownMillis;
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
