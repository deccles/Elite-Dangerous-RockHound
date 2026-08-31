package org.dce.ed.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.dce.ed.util.EdsmRequestPolicy;
import org.junit.jupiter.api.Test;

class RouteEdsmPrefetchPolicyTest {

    @Test
    void openingDumpIsEighteenWideThenCruiseUsesSix() {
        assertEquals(18, RouteEdsmPrefetchPolicy.OPENING_WINDOW_SIZE);
        assertEquals(EdsmRequestPolicy.MAX_CONCURRENT_REQUESTS,
                RouteEdsmPrefetchPolicy.OPENING_BURST_CONCURRENCY);
        assertEquals(18, RouteEdsmPrefetchPolicy.OPENING_BURST_CONCURRENCY);
        assertEquals(6, RouteEdsmPrefetchPolicy.MAX_CONCURRENT);
        assertEquals(14_000L, RouteEdsmPrefetchPolicy.OPENING_REST.toMillis());
    }

    @Test
    void healthyWaveUsesSixSystemsSixConcurrentAndSixSecondRest() {
        assertEquals(6, RouteEdsmPrefetchPolicy.waveSize(false));
        assertEquals(6, RouteEdsmPrefetchPolicy.MAX_CONCURRENT);
        assertEquals(6_000L, RouteEdsmPrefetchPolicy.restAfterWave(false).toMillis());
    }

    @Test
    void rateLimitedWaveKeepsSixWideAndWaitsFourteenSeconds() {
        assertEquals(6, RouteEdsmPrefetchPolicy.waveSize(true));
        assertEquals(14_000L, RouteEdsmPrefetchPolicy.restAfterWave(true).toMillis());
        assertEquals(RouteEdsmPrefetchPolicy.OPENING_REST, RouteEdsmPrefetchPolicy.RATE_LIMIT_WAVE_REST);
    }

    @Test
    void rateLimitDetectionReadsHttp429AndCloudflare1015() {
        assertTrue(RouteEdsmPrefetchPolicy.isRateLimited(
                new IOException("EDSM HTTP 429 from https://www.edsm.net/api-system-v1/bodies: limited")));
        assertTrue(RouteEdsmPrefetchPolicy.isRateLimited(
                new IOException("EDSM HTTP 429 from https://edsm.test: error code: 1015")));
        assertFalse(RouteEdsmPrefetchPolicy.isRateLimited(new IOException("EDSM HTTP 503 from https://edsm.test")));
    }
}
