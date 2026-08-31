package org.dce.ed.route;

/** Tracks prefetch waves and rest between waves. */
public final class RouteEdsmPrefetchScheduler {
    public enum QueryOutcome {
        SUCCESS,
        RATE_LIMITED,
        ERROR
    }

    public interface WaveLogger {
        void waveStarted(long atEpochMillis, int count, int inFlight);
        void waveFinished(int count, int status200, int status429, int errors, long restMs);
        default void openingBurstRateLimited(long restMs, int nextWaveSize) {
        }

        default void openingBurstDrained(long restMs) {
        }
    }

    private static final WaveLogger NO_LOG = new WaveLogger() {
        @Override
        public void waveStarted(long atEpochMillis, int count, int inFlight) {
        }

        @Override
        public void waveFinished(int count, int status200, int status429, int errors, long restMs) {
        }
    };

    private final WaveLogger logger;
    private boolean lastWaveRateLimited;
    private boolean waveActive;
    private int waveCount;
    private int remaining;
    private int status200;
    private int status429;
    private int errors;
    private long restUntilMillis;
    private long waveStartedAtMillis;

    public RouteEdsmPrefetchScheduler() {
        this(NO_LOG);
    }

    public RouteEdsmPrefetchScheduler(WaveLogger logger) {
        this.logger = logger != null ? logger : NO_LOG;
    }

    public boolean isWaveActive() {
        return waveActive;
    }

    public int plannedWaveSize() {
        return RouteEdsmPrefetchPolicy.waveSize(lastWaveRateLimited);
    }

    /** Clears wave and rest state so a new route can dump immediately. */
    public void reset() {
        lastWaveRateLimited = false;
        waveActive = false;
        waveCount = 0;
        remaining = 0;
        status200 = 0;
        status429 = 0;
        errors = 0;
        restUntilMillis = 0L;
        waveStartedAtMillis = 0L;
    }

    /**
     * An opening-window 429/1015 waits the opening rest before cruise resumes
     * at the same 6-wide wave size.
     */
    public void noteRateLimit(long nowMillis) {
        boolean alreadyHolding = restUntilMillis > nowMillis;
        lastWaveRateLimited = true;
        long restMs = RouteEdsmPrefetchPolicy.restAfterWave(true).toMillis();
        restUntilMillis = Math.max(restUntilMillis, nowMillis + restMs);
        if (!alreadyHolding) {
            logger.openingBurstRateLimited(restMs, plannedWaveSize());
        }
    }

    /** Quiet after the opening 18 drain before the first 6-wide cruise wave. */
    public void noteOpeningBurstDrained(long nowMillis) {
        long restMs = RouteEdsmPrefetchPolicy.OPENING_REST.toMillis();
        restUntilMillis = Math.max(restUntilMillis, nowMillis + restMs);
        logger.openingBurstDrained(restMs);
    }

    public long millisUntilRestElapsed(long nowMillis) {
        return Math.max(0L, restUntilMillis - nowMillis);
    }

    public boolean canStartWave(boolean openingWindowBusy, boolean jumpInProgress,
            boolean anyRouteLookupInFlight, boolean hasDeferred, long nowMillis) {
        if (waveActive) {
            return false;
        }
        if (openingWindowBusy || jumpInProgress || anyRouteLookupInFlight) {
            return false;
        }
        if (nowMillis < restUntilMillis) {
            return false;
        }
        return hasDeferred;
    }

    public void beginWave(int count, long nowMillis) {
        waveActive = true;
        waveCount = count;
        remaining = count;
        status200 = 0;
        status429 = 0;
        errors = 0;
        waveStartedAtMillis = nowMillis;
        logger.waveStarted(nowMillis, count, Math.min(count, RouteEdsmPrefetchPolicy.MAX_CONCURRENT));
    }

    /** @return true when this result was the last outstanding query in the wave */
    public boolean recordResult(QueryOutcome outcome) {
        if (!waveActive) {
            return false;
        }
        if (outcome == QueryOutcome.SUCCESS) {
            status200++;
        } else if (outcome == QueryOutcome.RATE_LIMITED) {
            status429++;
        } else {
            errors++;
        }
        remaining--;
        return remaining <= 0;
    }

    public long finishWave(long nowMillis) {
        boolean hitLimit = status429 > 0;
        lastWaveRateLimited = hitLimit;
        long restMs = RouteEdsmPrefetchPolicy.restAfterWave(hitLimit).toMillis();
        restUntilMillis = nowMillis + restMs;
        logger.waveFinished(waveCount, status200, status429, errors, restMs);
        waveActive = false;
        remaining = 0;
        return restMs;
    }

    long waveStartedAtMillis() {
        return waveStartedAtMillis;
    }
}
