package org.dce.ed.route;

import java.time.Duration;
import java.util.Locale;

import org.dce.ed.util.EdsmRequestPolicy;

/**
 * Wave size, concurrency, and rest for Route EDSM lookups.
 * Opening dump of 18, then a 6-wide cruise that stays under Cloudflare 1015.
 */
public final class RouteEdsmPrefetchPolicy {
    /** Cruise in-flight after the opening dump. */
    public static final int MAX_CONCURRENT = 6;
    /** Yellow PENDING lookahead; the opening dump queries this many at once. */
    public static final int OPENING_WINDOW_SIZE = 18;
    public static final int OPENING_BURST_CONCURRENCY = EdsmRequestPolicy.MAX_CONCURRENT_REQUESTS;
    public static final int HEALTHY_WAVE_SIZE = 6;
    /** Do not shrink after 429; resume the same 6-wide cruise after rest. */
    public static final int RATE_LIMITED_WAVE_SIZE = HEALTHY_WAVE_SIZE;
    public static final Duration HEALTHY_WAVE_REST = Duration.ofSeconds(6);
    /** Quiet after the opening 18 and after a 429 before the next cruise wave. */
    public static final Duration OPENING_REST = Duration.ofSeconds(14);
    public static final Duration RATE_LIMIT_WAVE_REST = OPENING_REST;

    private RouteEdsmPrefetchPolicy() {
    }

    public static int waveSize(boolean lastWaveRateLimited) {
        return lastWaveRateLimited ? RATE_LIMITED_WAVE_SIZE : HEALTHY_WAVE_SIZE;
    }

    public static Duration restAfterWave(boolean lastWaveRateLimited) {
        return lastWaveRateLimited ? RATE_LIMIT_WAVE_REST : HEALTHY_WAVE_REST;
    }

    public static boolean isRateLimited(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message == null) {
                continue;
            }
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("http 429") || lower.contains("error code: 1015")) {
                return true;
            }
        }
        return false;
    }
}
