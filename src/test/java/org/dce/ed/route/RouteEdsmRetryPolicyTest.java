package org.dce.ed.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RouteEdsmRetryPolicyTest {

    @Test
    void retryDelayBacksOffAndEventuallyAbandonsPendingRow() {
        assertEquals(1_000, RouteEdsmRetryPolicy.delayMillis(1));
        assertEquals(2_000, RouteEdsmRetryPolicy.delayMillis(2));
        assertEquals(4_000, RouteEdsmRetryPolicy.delayMillis(3));
        assertEquals(true, RouteEdsmRetryPolicy.shouldRetry(4));
        assertEquals(false, RouteEdsmRetryPolicy.shouldRetry(5));
    }

    @Test
    void rateLimitedRetryUsesFourteenSecondRestInsteadOfOneSecond() {
        assertEquals(14_000, RouteEdsmRetryPolicy.delayMillis(1, true));
        assertEquals(1_000, RouteEdsmRetryPolicy.delayMillis(1, false));
    }
}
