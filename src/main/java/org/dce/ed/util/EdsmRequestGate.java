package org.dce.ed.util;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

/** Coordinates and caches application-wide EDSM HTTP requests. */
public final class EdsmRequestGate {

    private final CacheStore store;
    private final Clock clock;
    private final Sleeper sleeper;
    private final long cacheTtlMillis;
    private final long minimumIntervalMillis;
    private final long rateLimitCooldownMillis;
    private long lastNetworkRequestAt = Long.MIN_VALUE;
    private long cooldownUntil;

    public record Response(int statusCode, String contentType, String body, boolean fromCache) {
        public Response(int statusCode, String contentType, String body) {
            this(statusCode, contentType, body, false);
        }

        Response cachedCopy() {
            return new Response(statusCode, contentType, body, true);
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

    public EdsmRequestGate(CacheStore store, Clock clock, Sleeper sleeper,
            Duration cacheTtl, Duration minimumInterval, Duration rateLimitCooldown) {
        this.store = store;
        this.clock = clock;
        this.sleeper = sleeper;
        this.cacheTtlMillis = cacheTtl.toMillis();
        this.minimumIntervalMillis = minimumInterval.toMillis();
        this.rateLimitCooldownMillis = rateLimitCooldown.toMillis();
    }

    public synchronized Response execute(String key, NetworkCall call) throws IOException {
        long now = clock.millis();
        CacheEntry cached = store.get(key);
        if (cached != null && now - cached.queriedAtEpochMillis() < cacheTtlMillis) {
            if (cached.response().statusCode() == 0) {
                throw new IOException("Cached EDSM request failure: " + cached.response().body());
            }
            return cached.response().cachedCopy();
        }
        if (now < cooldownUntil) {
            Response skipped = new Response(429, "text/plain",
                    "EDSM request skipped during rate-limit cooldown", true);
            store.put(key, new CacheEntry(now,
                    new Response(skipped.statusCode(), skipped.contentType(), skipped.body())));
            return skipped;
        }
        if (lastNetworkRequestAt != Long.MIN_VALUE) {
            long wait = minimumIntervalMillis - (now - lastNetworkRequestAt);
            if (wait > 0L) {
                try {
                    sleeper.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while pacing EDSM requests", e);
                }
                now = clock.millis();
            }
        }
        lastNetworkRequestAt = now;
        Response response;
        try {
            response = call.execute();
        } catch (IOException ex) {
            store.put(key, new CacheEntry(clock.millis(),
                    new Response(0, "text/plain", ex.getMessage())));
            throw ex;
        }
        store.put(key, new CacheEntry(clock.millis(), response));
        if (response.statusCode() == 429) {
            cooldownUntil = clock.millis() + rateLimitCooldownMillis;
        }
        return response;
    }
}
