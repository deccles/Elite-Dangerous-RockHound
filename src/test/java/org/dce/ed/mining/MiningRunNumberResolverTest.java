package org.dce.ed.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Regression tests for mining run numbering. The rules are documented on {@link MiningRunNumberResolver};
 * if these fail, read that class before "fixing" tests — the product behavior is intentional.
 */
class MiningRunNumberResolverTest {

    private static final String CMDR = "Villunus";
    private static final String SYS = "Phraa Flyuae Wi 2";
    private static final String BODY = "B";
    private static final String LOC = SYS + " > " + BODY;

    private static ProspectorLogRow mat(
            int run,
            String asteroid,
            Instant rowTime,
            Instant runStart,
            Instant runEnd) {
        return new ProspectorLogRow(
                run,
                asteroid,
                LOC,
                rowTime,
                "Bromellite",
                10.0,
                0.0,
                1.0,
                1.0,
                CMDR,
                "",
                "",
                0,
                runStart,
                runEnd);
    }

    @Test
    void emptyLog_startsAtRun1() {
        assertEquals(1, MiningRunNumberResolver.compute(CMDR, SYS, BODY, false, List.of()));
        assertEquals(1, MiningRunNumberResolver.compute(CMDR, SYS, BODY, false, null));
    }

    @Test
    void openRun_oneRowStartNoEnd_continuesThatRun() {
        Instant t = Instant.parse("2026-04-02T15:00:00Z");
        Instant start = Instant.parse("2026-04-02T14:00:00Z");
        List<ProspectorLogRow> rows = List.of(mat(19, "A", t, start, null));
        assertEquals(19, MiningRunNumberResolver.compute(CMDR, SYS, BODY, false, rows));
    }

    /**
     * Only the canonical sheet row gets run end on dock; other rows may still have start filled and end blank.
     * The resolver must still treat the whole run as closed so we do not append forever to the same run id.
     */
    @Test
    void runClosedByEndOnAnyRow_siblingRowsWithStartNoEnd_doNotStayActive() {
        Instant t1 = Instant.parse("2026-04-02T15:19:04Z");
        Instant t2 = Instant.parse("2026-04-02T15:20:00Z");
        Instant t3 = Instant.parse("2026-04-02T15:25:00Z");
        Instant start = Instant.parse("2026-04-02T15:19:04Z");
        Instant end = Instant.parse("2026-04-02T15:40:27Z");
        List<ProspectorLogRow> rows = new ArrayList<>();
        rows.add(mat(19, "A", t1, start, end));
        rows.add(new ProspectorLogRow(19, "A", LOC, t2, "Tritium", 10, 0, 1, 1, CMDR, "", "", 0, start, null));
        rows.add(mat(19, "B", t3, null, null));
        assertEquals(20, MiningRunNumberResolver.compute(CMDR, SYS, BODY, false, rows));
    }

    @Test
    void latestRunAtLocation_isClosed_startsNextRunForCommander() {
        Instant t = Instant.parse("2026-04-02T16:00:00Z");
        Instant start = Instant.parse("2026-04-02T15:00:00Z");
        Instant end = Instant.parse("2026-04-02T15:30:00Z");
        List<ProspectorLogRow> rows = List.of(mat(19, "A", t, start, end));
        assertEquals(20, MiningRunNumberResolver.compute(CMDR, SYS, BODY, false, rows));
    }

    @Test
    void otherCommandersData_doesNotCloseOrContinueOurRun() {
        Instant t = Instant.parse("2026-04-02T15:00:00Z");
        List<ProspectorLogRow> rows = List.of(
                new ProspectorLogRow(18, "E", LOC, t, "X", 10, 0, 1, 1, "UkeBard", "", "", 0, t, null));
        // Per-commander numbering: no rows for Villunus → next run is 1 (UkeBard's 18 does not apply).
        assertEquals(1, MiningRunNumberResolver.compute(CMDR, SYS, BODY, false, rows));
    }

    @Test
    void twoCommanders_highRunNumbers_nextRunUsesOwnCommanderMax() {
        Instant t = Instant.parse("2026-04-02T15:00:00Z");
        Instant start = Instant.parse("2026-04-02T14:00:00Z");
        Instant end = Instant.parse("2026-04-02T15:30:00Z");
        List<ProspectorLogRow> rows = List.of(
                mat(50, "A", t, start, end),
                new ProspectorLogRow(3, "A", LOC, t, "X", 10, 0, 1, 1, "UkeBard", "", "", 0, start, end));
        assertEquals(51, MiningRunNumberResolver.compute(CMDR, SYS, BODY, false, rows));
        assertEquals(4, MiningRunNumberResolver.compute("UkeBard", SYS, BODY, false, rows));
    }

    @Test
    void forceNewRun_allocatesNextForCommanderWhenNothingActive() {
        Instant t = Instant.parse("2026-04-02T15:00:00Z");
        Instant start = Instant.parse("2026-04-02T14:00:00Z");
        Instant end = Instant.parse("2026-04-02T15:30:00Z");
        List<ProspectorLogRow> rows = List.of(mat(5, "A", t, start, end));
        assertEquals(6, MiningRunNumberResolver.compute(CMDR, SYS, BODY, true, rows));
    }

    @Test
    void activeRun_stillPreferredOverForceNewRun() {
        Instant t = Instant.parse("2026-04-02T15:00:00Z");
        Instant start = Instant.parse("2026-04-02T14:00:00Z");
        List<ProspectorLogRow> rows = List.of(mat(7, "A", t, start, null));
        assertEquals(7, MiningRunNumberResolver.compute(CMDR, SYS, BODY, true, rows));
    }

    @Test
    void continueLatestOpenRunAtSameBody_whenNoActiveRowButRunNotClosed() {
        Instant tOld = Instant.parse("2026-04-02T14:00:00Z");
        Instant tNew = Instant.parse("2026-04-02T15:00:00Z");
        List<ProspectorLogRow> rows = List.of(
                mat(4, "A", tOld, null, null),
                mat(4, "B", tNew, null, null));
        assertEquals(4, MiningRunNumberResolver.compute(CMDR, SYS, BODY, false, rows));
    }

    @Test
    void openRun_continuesAcrossJournalLocationWithoutDock() {
        Instant t = Instant.parse("2026-04-02T15:00:00Z");
        Instant start = Instant.parse("2026-04-02T14:00:00Z");
        String otherLoc = "OtherSys > OtherBody";
        List<ProspectorLogRow> rows = List.of(
                new ProspectorLogRow(6, "A", otherLoc, t, "X", 10, 0, 1, 1, CMDR, "", "", 0, start, null));
        assertEquals(6, MiningRunNumberResolver.compute(CMDR, SYS, BODY, false, rows));
    }

    @Test
    void normalizeCommander_blankBecomesDash() {
        assertEquals("-", MiningRunNumberResolver.normalizeCommander(null));
        assertEquals("-", MiningRunNumberResolver.normalizeCommander(""));
        assertEquals("-", MiningRunNumberResolver.normalizeCommander("   "));
    }

    @Test
    void normalizeCommander_doesNotTrimNonBlank() {
        assertEquals("  Villunus  ", MiningRunNumberResolver.normalizeCommander("  Villunus  "));
    }

    @Test
    void blankCommander_matchesRowsStoredAsDash() {
        Instant t = Instant.parse("2026-04-02T15:00:00Z");
        List<ProspectorLogRow> rows = List.of(
                new ProspectorLogRow(2, "A", LOC, t, "X", 10, 0, 1, 1, "-", "", "", 0, t, null));
        assertEquals(2, MiningRunNumberResolver.compute("", SYS, BODY, false, rows));
        assertEquals(2, MiningRunNumberResolver.compute("   ", SYS, BODY, false, rows));
    }

    @Test
    void differentLocation_onlyClosedRunsElsewhere_allocatesNextRun() {
        Instant t = Instant.parse("2026-04-02T15:00:00Z");
        Instant start = Instant.parse("2026-04-02T14:00:00Z");
        Instant end = Instant.parse("2026-04-02T14:30:00Z");
        String otherLoc = "OtherSys > OtherBody";
        List<ProspectorLogRow> rows = List.of(
                new ProspectorLogRow(3, "A", otherLoc, t, "X", 10, 0, 1, 1, CMDR, "", "", 0, start, end));
        assertEquals(4, MiningRunNumberResolver.compute(CMDR, SYS, BODY, false, rows));
    }

    @Test
    void forceNewRun_false_closedRunAtLocation_stillIncrements() {
        Instant t = Instant.parse("2026-04-02T16:00:00Z");
        Instant start = Instant.parse("2026-04-02T15:00:00Z");
        Instant end = Instant.parse("2026-04-02T15:30:00Z");
        List<ProspectorLogRow> rows = List.of(mat(8, "A", t, start, end));
        assertEquals(9, MiningRunNumberResolver.compute(CMDR, SYS, BODY, false, rows));
    }
}
