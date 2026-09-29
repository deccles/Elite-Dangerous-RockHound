package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.dce.ed.logreader.EliteLogParser;
import org.junit.jupiter.api.Test;

/**
 * Operation payouts are not in the journal until the next Statistics. The estimate follows the
 * commander's Sept 2026 history: ~88 first full-credit run of the week, ~70 after, ~40% past ~1,000.
 */
class MercCoinOperationEstimatorTest {

    private final EliteLogParser parser = new EliteLogParser();

    @Test
    void payout_followsWeeklyEarnings() {
        assertEquals(88, MercCoinOperationEstimator.estimatePayout(9_100_000, 0, 0));
        assertEquals(70, MercCoinOperationEstimator.estimatePayout(9_100_000, 88, 1));
        assertEquals(70, MercCoinOperationEstimator.estimatePayout(4_550_000, 400, 3));
        assertEquals(30, MercCoinOperationEstimator.estimatePayout(2_730_000, 100, 0));
        // 40 under the cap plus 40% of the 30 over it.
        assertEquals(52, MercCoinOperationEstimator.estimatePayout(9_100_000, 960, 12));
        assertEquals(28, MercCoinOperationEstimator.estimatePayout(9_100_000, 1_058, 14));
        assertEquals(0, MercCoinOperationEstimator.estimatePayout(0, 0, 0));
    }

    @Test
    void weekStartsThursdayAtSevenUtc() {
        assertEquals(Instant.parse("2026-09-24T07:00:00Z"),
                MercCoinOperationEstimator.weekStart(Instant.parse("2026-09-28T21:32:49Z")));
        assertEquals(Instant.parse("2026-09-24T07:00:00Z"),
                MercCoinOperationEstimator.weekStart(Instant.parse("2026-09-24T07:00:00Z")));
        assertEquals(Instant.parse("2026-09-17T07:00:00Z"),
                MercCoinOperationEstimator.weekStart(Instant.parse("2026-09-24T06:59:59Z")));
    }

    /** Journal.2026-09-27T212436.01.log, Sept 28: six Operations, balances 241 → 109 → 197 → 97 → 307. */
    @Test
    void todaysJournal_estimatesThenReconcilesToReportedBalance() {
        EngineeringInventoryTracker tracker = new EngineeringInventoryTracker();
        stats(tracker, "2026-09-28T01:25:50Z", 241, 1100);

        load(tracker, "2026-09-28T16:52:49Z", 2_894_798_968L);
        mode(tracker, "2026-09-28T16:52:49Z", "Operation");
        load(tracker, "2026-09-28T17:14:05Z", 2_899_348_968L);
        mode(tracker, "2026-09-28T17:14:05Z", "MainGame");
        assertEquals(241 + 88, tracker.getCount("merccoins"), "first full-credit run of the week");
        assertTrue(tracker.isMercCoinBalanceEstimated());
        stats(tracker, "2026-09-28T17:14:11Z", 109, 1320);
        assertFalse(tracker.isMercCoinBalanceEstimated());

        load(tracker, "2026-09-28T17:25:55Z", 2_899_348_968L);
        mode(tracker, "2026-09-28T17:25:55Z", "Operation");
        // Mode change written before its LoadGame.
        mode(tracker, "2026-09-28T17:46:53Z", "MainGame");
        load(tracker, "2026-09-28T17:46:53Z", 2_908_448_968L);
        assertEquals(109 + 70, tracker.getCount("merccoins"));
        stats(tracker, "2026-09-28T17:46:57Z", 197, 1320);

        load(tracker, "2026-09-28T20:21:25Z", 2_908_430_096L);
        mode(tracker, "2026-09-28T20:21:25Z", "Operation");
        load(tracker, "2026-09-28T20:39:06Z", 2_917_530_096L);
        mode(tracker, "2026-09-28T20:39:06Z", "MainGame");
        stats(tracker, "2026-09-28T20:39:12Z", 97, 1490);

        // Three Operations in a row with no balance report between them.
        load(tracker, "2026-09-28T20:48:36Z", 2_917_530_096L);
        mode(tracker, "2026-09-28T20:48:36Z", "Operation");
        load(tracker, "2026-09-28T21:03:05Z", 2_926_630_096L);
        mode(tracker, "2026-09-28T21:03:05Z", "Operation");
        assertEquals(97 + 70, tracker.getCount("merccoins"));
        load(tracker, "2026-09-28T21:17:09Z", 2_935_730_096L);
        mode(tracker, "2026-09-28T21:17:09Z", "Operation");
        load(tracker, "2026-09-28T21:32:49Z", 2_944_830_096L);
        mode(tracker, "2026-09-28T21:32:49Z", "MainGame");
        assertEquals(307, tracker.getCount("merccoins"), "matches the balance the game reported next");
        assertTrue(tracker.isMercCoinBalanceEstimated());
        assertEquals(3, tracker.mercCoinPendingOperations());
        assertEquals(210, tracker.mercCoinPendingEstimate());

        stats(tracker, "2026-09-28T21:32:56Z", 307, 1490);
        assertEquals(307, tracker.getCount("merccoins"));
        assertFalse(tracker.isMercCoinBalanceEstimated());
        assertEquals(88 + 88 + 70 + 210,
                tracker.mercCoinsEarnedThisWeek(Instant.parse("2026-09-28T21:40:00Z")));
    }

    @Test
    void abandonedOperation_addsNothing() {
        EngineeringInventoryTracker tracker = new EngineeringInventoryTracker();
        stats(tracker, "2026-09-08T15:50:00Z", 263, 0);
        load(tracker, "2026-09-08T15:55:00Z", 1_000_000L);
        mode(tracker, "2026-09-08T15:55:00Z", "Operation");
        load(tracker, "2026-09-08T15:59:06Z", 1_000_000L);
        mode(tracker, "2026-09-08T15:59:06Z", "MainGame");
        assertEquals(263, tracker.getCount("merccoins"));
        assertFalse(tracker.isMercCoinBalanceEstimated());
    }

    @Test
    void mainGameSession_creditsDoNotCountAsOperationPay() {
        EngineeringInventoryTracker tracker = new EngineeringInventoryTracker();
        stats(tracker, "2026-09-28T01:25:50Z", 241, 1100);
        load(tracker, "2026-09-28T01:25:15Z", 2_893_803_369L);
        mode(tracker, "2026-09-28T01:25:15Z", "MainGame");
        load(tracker, "2026-09-28T16:52:49Z", 2_894_798_968L);
        assertEquals(241, tracker.getCount("merccoins"));
    }

    private void stats(EngineeringInventoryTracker tracker, String ts, int current, int spent) {
        tracker.applyEvent(parser.parseRecord("""
                {"timestamp": "%s", "event": "Statistics",
                 "Bank_Account": {"MercCoins_Current": %d, "MercCoins_Total_Earned": 0,
                                  "MercCoins_Total_Spent": %d}}
                """.formatted(ts, current, spent)));
    }

    private void load(EngineeringInventoryTracker tracker, String ts, long credits) {
        tracker.applyEvent(parser.parseRecord("""
                {"timestamp": "%s", "event": "LoadGame", "FID": "F2015064", "Commander": "Villunus",
                 "Horizons": true, "Odyssey": true, "Ship": "Federation_Corvette", "ShipID": 23,
                 "ShipName": "", "ShipIdent": "", "FuelLevel": 32.0, "FuelCapacity": 32.0,
                 "GameMode": "Open", "Credits": %d, "Loan": 0}
                """.formatted(ts, credits)));
    }

    private void mode(EngineeringInventoryTracker tracker, String ts, String gameMode) {
        tracker.applyEvent(parser.parseRecord("""
                {"timestamp": "%s", "event": "GameModeChange", "GameMode": "%s"}
                """.formatted(ts, gameMode)));
    }
}
