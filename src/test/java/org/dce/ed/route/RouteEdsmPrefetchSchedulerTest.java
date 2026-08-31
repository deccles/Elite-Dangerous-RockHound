package org.dce.ed.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RouteEdsmPrefetchSchedulerTest {

    @Test
    void waitsForOpeningWindowJumpAndInFlightLookupsBeforeStarting() {
        RouteEdsmPrefetchScheduler scheduler = new RouteEdsmPrefetchScheduler();
        long now = 1_000L;

        assertFalse(scheduler.canStartWave(true, false, false, true, now));
        assertFalse(scheduler.canStartWave(false, true, false, true, now));
        assertFalse(scheduler.canStartWave(false, false, true, true, now));
        assertFalse(scheduler.canStartWave(false, false, false, false, now));
        assertTrue(scheduler.canStartWave(false, false, false, true, now));
    }

    @Test
    void healthyWaveRestsSixSecondsAndKeepsSixWide() {
        CapturingLog log = new CapturingLog();
        RouteEdsmPrefetchScheduler scheduler = new RouteEdsmPrefetchScheduler(log);
        long now = 5_000L;

        scheduler.beginWave(6, now);
        assertFalse(scheduler.canStartWave(false, false, false, true, now));
        for (int i = 0; i < 5; i++) {
            assertFalse(scheduler.recordResult(RouteEdsmPrefetchScheduler.QueryOutcome.SUCCESS));
        }
        assertTrue(scheduler.recordResult(RouteEdsmPrefetchScheduler.QueryOutcome.SUCCESS));
        assertEquals(6_000L, scheduler.finishWave(now + 200L));

        assertEquals(6, log.startedCount);
        assertEquals(6, log.startedInFlight);
        assertEquals(6, log.finished200);
        assertEquals(0, log.finished429);
        assertEquals(6_000L, log.finishedRestMs);
        assertEquals(6, scheduler.plannedWaveSize());
        assertFalse(scheduler.canStartWave(false, false, false, true, now + 200L + 5_999L));
        assertTrue(scheduler.canStartWave(false, false, false, true, now + 200L + 6_000L));
    }

    @Test
    void rateLimitedWaveKeepsSixWideAndUsesFourteenSecondRest() {
        RouteEdsmPrefetchScheduler scheduler = new RouteEdsmPrefetchScheduler();
        long now = 0L;
        scheduler.beginWave(6, now);
        scheduler.recordResult(RouteEdsmPrefetchScheduler.QueryOutcome.SUCCESS);
        scheduler.recordResult(RouteEdsmPrefetchScheduler.QueryOutcome.RATE_LIMITED);
        scheduler.recordResult(RouteEdsmPrefetchScheduler.QueryOutcome.SUCCESS);
        scheduler.recordResult(RouteEdsmPrefetchScheduler.QueryOutcome.SUCCESS);
        scheduler.recordResult(RouteEdsmPrefetchScheduler.QueryOutcome.SUCCESS);
        assertTrue(scheduler.recordResult(RouteEdsmPrefetchScheduler.QueryOutcome.ERROR));
        assertEquals(14_000L, scheduler.finishWave(now));

        assertEquals(6, scheduler.plannedWaveSize());
        assertEquals(14_000L, scheduler.millisUntilRestElapsed(now));
        assertTrue(scheduler.canStartWave(false, false, false, true, now + 14_000L));

        scheduler.beginWave(6, now + 14_000L);
        for (int i = 0; i < 5; i++) {
            scheduler.recordResult(RouteEdsmPrefetchScheduler.QueryOutcome.SUCCESS);
        }
        assertTrue(scheduler.recordResult(RouteEdsmPrefetchScheduler.QueryOutcome.SUCCESS));
        scheduler.finishWave(now + 14_000L);
        assertEquals(6, scheduler.plannedWaveSize());
    }

    @Test
    void openingWindowRateLimitDelaysCruiseAndKeepsSixWide() {
        CapturingLog log = new CapturingLog();
        RouteEdsmPrefetchScheduler scheduler = new RouteEdsmPrefetchScheduler(log);
        long now = 1_000L;

        scheduler.noteRateLimit(now);

        assertEquals(6, scheduler.plannedWaveSize());
        assertEquals(14_000L, scheduler.millisUntilRestElapsed(now));
        assertEquals(14_000L, log.openingRestMs);
        assertEquals(6, log.openingNextWave);
        assertFalse(scheduler.canStartWave(false, false, false, true, now + 13_999L));
        assertTrue(scheduler.canStartWave(false, false, false, true, now + 14_000L));
    }

    @Test
    void openingDrainWaitsFourteenSecondsBeforeFirstCruiseWave() {
        CapturingLog log = new CapturingLog();
        RouteEdsmPrefetchScheduler scheduler = new RouteEdsmPrefetchScheduler(log);
        long now = 2_000L;

        scheduler.noteOpeningBurstDrained(now);

        assertEquals(14_000L, log.openingDrainedRestMs);
        assertEquals(6, scheduler.plannedWaveSize());
        assertFalse(scheduler.canStartWave(false, false, false, true, now + 13_999L));
        assertTrue(scheduler.canStartWave(false, false, false, true, now + 14_000L));
    }

    @Test
    void resetClearsOpeningRestSoANewRouteCanDumpImmediately() {
        RouteEdsmPrefetchScheduler scheduler = new RouteEdsmPrefetchScheduler();
        scheduler.noteOpeningBurstDrained(1_000L);
        scheduler.reset();
        assertEquals(0L, scheduler.millisUntilRestElapsed(1_000L));
        assertEquals(6, scheduler.plannedWaveSize());
        assertTrue(scheduler.canStartWave(false, false, false, true, 1_000L));
    }

    @Test
    void repeatedOpeningWindowRateLimitsLogAndExtendRestOnce() {
        CapturingLog log = new CapturingLog();
        RouteEdsmPrefetchScheduler scheduler = new RouteEdsmPrefetchScheduler(log);

        scheduler.noteRateLimit(1_000L);
        scheduler.noteRateLimit(1_100L);

        assertEquals(1, log.openingHoldCount);
        assertEquals(14_000L, log.openingRestMs);
        assertTrue(scheduler.millisUntilRestElapsed(1_100L) >= 13_900L);
    }

    private static final class CapturingLog implements RouteEdsmPrefetchScheduler.WaveLogger {
        int startedCount;
        int startedInFlight;
        int finished200;
        int finished429;
        long finishedRestMs;
        long openingRestMs;
        int openingNextWave;
        int openingHoldCount;
        long openingDrainedRestMs;

        @Override
        public void waveStarted(long atEpochMillis, int count, int inFlight) {
            startedCount = count;
            startedInFlight = inFlight;
        }

        @Override
        public void waveFinished(int count, int status200, int status429, int errors, long restMs) {
            finished200 = status200;
            finished429 = status429;
            finishedRestMs = restMs;
        }

        @Override
        public void openingBurstRateLimited(long restMs, int nextWaveSize) {
            openingRestMs = restMs;
            openingNextWave = nextWaveSize;
            openingHoldCount++;
        }

        @Override
        public void openingBurstDrained(long restMs) {
            openingDrainedRestMs = restMs;
        }
    }
}
