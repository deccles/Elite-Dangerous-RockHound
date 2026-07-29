package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.session.EdoSessionState;
import org.junit.jupiter.api.Test;

class CombatSessionTrackerTest {

    private final EliteLogParser parser = new EliteLogParser();

    @Test
    void supercruiseExitCreatesCandidateButNotDisplayedSession() {
        CombatSessionTracker tracker = new CombatSessionTracker();
        Instant exit = Instant.parse("2026-07-29T12:00:00Z");

        assertTrue(tracker.applyJournalEvent(event(exit, "SupercruiseExit", "")));

        CombatSessionTracker.Snapshot snapshot = tracker.snapshot(exit.plusSeconds(60));
        assertFalse(snapshot.active());
        assertFalse(snapshot.hasDisplayedSession());
    }

    @Test
    void firstBountyActivatesSessionAtCandidateExitTime() {
        CombatSessionTracker tracker = new CombatSessionTracker();
        Instant exit = Instant.parse("2026-07-29T12:00:00Z");
        tracker.applyJournalEvent(event(exit, "SupercruiseExit", ""));

        assertTrue(tracker.applyJournalEvent(event(exit.plusSeconds(30), "Bounty", "\"TotalReward\":120000")));

        CombatSessionTracker.Snapshot snapshot = tracker.snapshot(exit.plusSeconds(120));
        assertTrue(snapshot.active());
        assertTrue(snapshot.hasDisplayedSession());
        assertEquals(exit, snapshot.startedAt());
        assertEquals(120_000L, snapshot.earnedCredits());
        assertEquals(3_600_000L, snapshot.creditsPerHour());
    }

    @Test
    void laterShipRewardsAccumulateDuringActiveSession() {
        CombatSessionTracker tracker = activeTracker();
        Instant now = Instant.parse("2026-07-29T12:10:00Z");

        assertTrue(tracker.applyJournalEvent(event(now, "FactionKillBond", "\"Reward\":80000")));

        CombatSessionTracker.Snapshot snapshot = tracker.snapshot(now);
        assertEquals(200_000L, snapshot.earnedCredits());
        assertEquals(1_200_000L, snapshot.creditsPerHour());
    }

    @Test
    void supercruiseEntryFreezesActiveSessionRate() {
        CombatSessionTracker tracker = activeTracker();
        Instant entry = Instant.parse("2026-07-29T12:10:00Z");
        tracker.applyJournalEvent(event(entry, "SupercruiseEntry", ""));

        CombatSessionTracker.Snapshot snapshot = tracker.snapshot(entry.plusSeconds(600));
        assertFalse(snapshot.active());
        assertTrue(snapshot.hasDisplayedSession());
        assertEquals(120_000L, snapshot.earnedCredits());
        assertEquals(720_000L, snapshot.creditsPerHour());
    }

    @Test
    void rewardAfterSupercruiseEntryDoesNotRestartFrozenSession() {
        CombatSessionTracker tracker = activeTracker();
        Instant entry = Instant.parse("2026-07-29T12:10:00Z");
        tracker.applyJournalEvent(event(entry, "SupercruiseEntry", ""));

        assertFalse(tracker.applyJournalEvent(event(entry.plusSeconds(5), "Bounty", "\"TotalReward\":80000")));

        CombatSessionTracker.Snapshot snapshot = tracker.snapshot(entry.plusSeconds(5));
        assertFalse(snapshot.active());
        assertEquals(120_000L, snapshot.earnedCredits());
    }

    @Test
    void redeemVoucherClearsDisplayedSessionRegardlessOfVoucherType() {
        CombatSessionTracker tracker = activeTracker();
        Instant redeem = Instant.parse("2026-07-29T12:10:00Z");

        assertTrue(tracker.applyJournalEvent(event(redeem, "RedeemVoucher", "\"Type\":\"CombatBond\",\"Amount\":120000")));

        CombatSessionTracker.Snapshot snapshot = tracker.snapshot(redeem);
        assertFalse(snapshot.active());
        assertFalse(snapshot.hasDisplayedSession());
        assertEquals(0L, snapshot.earnedCredits());
    }

    @Test
    void ignoresIncomeWithoutCandidateAndNonCombatIncome() {
        CombatSessionTracker tracker = new CombatSessionTracker();
        Instant now = Instant.parse("2026-07-29T12:00:00Z");

        assertFalse(tracker.applyJournalEvent(event(now, "Bounty", "\"TotalReward\":120000")));
        assertFalse(tracker.applyJournalEvent(event(now, "MissionCompleted", "\"Reward\":999999")));
        assertFalse(tracker.snapshot(now).hasDisplayedSession());
    }

    @Test
    void parserReturnsTypedFactionKillBondWithReward() {
        assertEquals(80_000L, ((org.dce.ed.logreader.event.FactionKillBondEvent) parser.parseRecord(
                "{\"timestamp\":\"2026-07-29T12:00:00Z\",\"event\":\"FactionKillBond\",\"Reward\":80000}"))
                .getCombatReward());
    }

    @Test
    void restoresPendingSupercruiseExitFromSessionState() {
        Instant exit = Instant.parse("2026-07-29T12:00:00Z");
        CombatSessionTracker tracker = new CombatSessionTracker();
        tracker.applyJournalEvent(event(exit, "SupercruiseExit", ""));
        EdoSessionState state = new EdoSessionState();

        tracker.fillSessionState(state);

        CombatSessionTracker restored = new CombatSessionTracker();
        restored.applySessionState(state);
        CombatSessionTracker.Snapshot snapshot = restored.snapshot(exit.plusSeconds(60));
        assertEquals(exit, snapshot.candidateExitAt());
        assertFalse(snapshot.active());
        assertFalse(snapshot.hasDisplayedSession());
    }

    @Test
    void restoresActiveSessionCreditsAndStartTimeFromSessionState() {
        Instant exit = Instant.parse("2026-07-29T12:00:00Z");
        CombatSessionTracker tracker = activeTracker();
        tracker.applyJournalEvent(event(exit.plusSeconds(90), "FactionKillBond", "\"Reward\":80000"));
        EdoSessionState state = new EdoSessionState();

        tracker.fillSessionState(state);

        CombatSessionTracker restored = new CombatSessionTracker();
        restored.applySessionState(state);
        CombatSessionTracker.Snapshot snapshot = restored.snapshot(exit.plusSeconds(120));
        assertEquals(exit, snapshot.startedAt());
        assertTrue(snapshot.active());
        assertTrue(snapshot.hasDisplayedSession());
        assertEquals(200_000L, snapshot.earnedCredits());
        assertEquals(6_000_000L, snapshot.creditsPerHour());
    }

    private CombatSessionTracker activeTracker() {
        CombatSessionTracker tracker = new CombatSessionTracker();
        Instant exit = Instant.parse("2026-07-29T12:00:00Z");
        tracker.applyJournalEvent(event(exit, "SupercruiseExit", ""));
        tracker.applyJournalEvent(event(exit.plusSeconds(30), "Bounty", "\"TotalReward\":120000"));
        return tracker;
    }

    private org.dce.ed.logreader.EliteLogEvent event(Instant timestamp, String event, String fields) {
        return parser.parseRecord("{\"timestamp\":\"" + timestamp + "\",\"event\":\"" + event + "\""
                + (fields.isBlank() ? "" : "," + fields) + "}");
    }
}
