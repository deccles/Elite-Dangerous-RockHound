package org.dce.ed.route;

/** Bounded exponential delay for retrying a visible Route row after a transient EDSM failure. */
public final class RouteEdsmRetryPolicy {
    private static final int MAX_DELAY_MILLIS = 30_000;
    private static final int MAX_ATTEMPTS = 4;

    private RouteEdsmRetryPolicy() {
    }

    public static int delayMillis(int attempt) {
        int exponent = Math.max(0, Math.min(5, attempt - 1));
        return Math.min(MAX_DELAY_MILLIS, 1_000 << exponent);
    }

    public static int delayMillis(int attempt, boolean rateLimited) {
        if (rateLimited) {
            long restMs = RouteEdsmPrefetchPolicy.RATE_LIMIT_WAVE_REST.toMillis();
            return (int) Math.min(Integer.MAX_VALUE, restMs);
        }
        return delayMillis(attempt);
    }

    public static boolean shouldRetry(int attempt) {
        return attempt <= MAX_ATTEMPTS;
    }
}
