package org.dce.systemmodel.journal;

import org.dce.systemmodel.journal.ParentRef;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JournalEventLogUtilTest {

    @Test
    void forSystem_dropsOtherSystemNames() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        List<JournalRecord> log = List.of(
                scan(t, 0, "Eol Prou NN-Y b31-0", "Star"),
                scan(t, 1, "Other System A", "Planet"));
        List<JournalRecord> kept = JournalEventLogUtil.forSystem("Eol Prou NN-Y b31-0", log);
        assertEquals(1, kept.size());
        assertEquals(0, ((ScanRecord) kept.get(0)).bodyId());
    }

    @Test
    void latestPerBodyId_scanAndBarycentreSameNumericId_bothKept() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        ScanRecord planet = scan(t, 5, "Eol Prou NN-Y b31-0 5", "Rocky");
        ScanBaryCentreRecord bary = new ScanBaryCentreRecord(
                t, 5, "Eol Prou NN-Y b31-0 barycentre 5", List.of(), List.of(), null);
        List<JournalRecord> latest = JournalEventLogUtil.latestPerBodyId(List.of(planet, bary));
        assertEquals(2, latest.size());
    }

    @Test
    void latestPerBodyId_keepsNewestTimestamp() {
        Instant older = Instant.parse("2026-05-27T12:00:00Z");
        Instant newer = Instant.parse("2026-05-27T13:00:00Z");
        List<JournalRecord> log = List.of(
                scan(older, 7, "Eol Prou NN-Y b31-0 7", "Planet"),
                scan(newer, 7, "Eol Prou NN-Y b31-0 7", "Gas Giant"));
        List<JournalRecord> latest = JournalEventLogUtil.latestPerBodyId(log);
        assertEquals(1, latest.size());
        assertEquals("Gas Giant", ((ScanRecord) latest.get(0)).bodyType());
    }

    @Test
    void dedupeScansByDesignation_collapsesSamePlanetDifferentBodyIds() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        String sys = "Eol Prou UQ-A c15-29";
        ScanRecord cacheRow = new ScanRecord(
                t, 21, sys + " 1", "Planet", "Class I gas giant", 100,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), null, true, false);
        ScanRecord journalRow = new ScanRecord(
                t, 55, sys + " 1", "Planet", "Sudarsky class I gas giant", 100,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new ParentRef(ParentRef.ParentType.STAR, 0)), null, true, false);
        List<JournalRecord> out = JournalEventLogUtil.dedupeScansByDesignation(
                sys, List.of(cacheRow, journalRow));
        assertEquals(1, out.size());
        assertEquals(55, ((ScanRecord) out.get(0)).bodyId());
    }

    @Test
    void dedupeScansByDesignation_collapsesSameStarDifferentBodyIds() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        String sys = "Eol Prou AE-R c5-465";
        ScanRecord cacheB = new ScanRecord(
                t, 2, sys + " B", "Star", "K", 2236,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new ParentRef(ParentRef.ParentType.NULL, 1)), null, true, false);
        ScanRecord journalB = new ScanRecord(
                t, 8, sys + " B", "Star", "K5 VA", 2236,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new ParentRef(ParentRef.ParentType.NULL, 1),
                        new ParentRef(ParentRef.ParentType.NULL, 0)), null, true, false);
        List<JournalRecord> out = JournalEventLogUtil.dedupeScansByDesignation(
                sys, List.of(cacheB, journalB));
        assertEquals(1, out.size());
        assertEquals(8, ((ScanRecord) out.get(0)).bodyId());
    }

    @Test
    void stripDuplicateArrivalStarScans_dropsSystemNameWhenLetterBranchAExists() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        String sys = "Eol Prou AE-R c5-465";
        ScanRecord edsmArrival = new ScanRecord(
                t, 9781, sys, "Star", "K", 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new ParentRef(ParentRef.ParentType.NULL, 0)), null, true, false);
        ScanRecord starA = new ScanRecord(
                t, 2, sys + " A", "Star", "K5 VA", 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new ParentRef(ParentRef.ParentType.NULL, 1)), null, true, false);
        List<JournalRecord> out = JournalEventLogUtil.stripDuplicateArrivalStarScans(
                sys, List.of(edsmArrival, starA));
        assertEquals(1, out.size());
        assertEquals(sys + " A", ((ScanRecord) out.get(0)).bodyName());
    }

    @Test
    void dedupeScansByDesignation_keepsBothStarsByBodyId() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        String sys = "Eol Prou YF-N d7-1186";
        ScanRecord starA = new ScanRecord(
                t, 0, sys, "Star", "G", 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new ParentRef(ParentRef.ParentType.NULL, 0)), null, true, false);
        ScanRecord starB = new ScanRecord(
                t, 2, sys + " B", "Star", "K", 27000,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new ParentRef(ParentRef.ParentType.NULL, 0)), null, true, false);
        List<JournalRecord> out = JournalEventLogUtil.dedupeScansByDesignation(
                sys, List.of(starA, starB));
        assertEquals(2, out.size());
    }

    @Test
    void stripDuplicateArrivalStarScans_dropsEdsmIdWhenJournalZeroPresent() {
        Instant journalTs = Instant.parse("2026-03-06T22:40:09Z");
        Instant synthTs = Instant.EPOCH;
        String sys = "Eol Prou LW-L c8-75";
        ScanRecord journalStar = new ScanRecord(
                journalTs, 0, sys, "Star", "M", 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), null, true, false);
        ScanRecord edsmStar = new ScanRecord(
                synthTs, 9781, sys, "Star", "M", 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), null, true, false);
        List<JournalRecord> out = JournalEventLogUtil.stripDuplicateArrivalStarScans(
                sys, List.of(journalStar, edsmStar));
        assertEquals(1, out.size());
        assertEquals(0, ((ScanRecord) out.get(0)).bodyId());
    }

    @Test
    void normalizeForSystemBuild_stripsEdsmArrivalStarDuplicate() {
        Instant journalTs = Instant.parse("2026-03-06T22:40:09Z");
        String sys = "Eol Prou LW-L c8-75";
        ScanRecord journalStar = new ScanRecord(
                journalTs, 0, sys, "Star", "M", 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), null, true, false);
        ScanRecord edsmStar = new ScanRecord(
                Instant.EPOCH, 9781, sys, "Star", "M", 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), null, true, false);
        List<JournalRecord> out = JournalEventLogUtil.normalizeForSystemBuild(
                sys, List.of(journalStar, edsmStar));
        assertEquals(1, out.size());
        assertEquals(0, ((ScanRecord) out.get(0)).bodyId());
    }

    @Test
    void normalizeForSystemBuild_collapsesAeRStyleDuplicateStars() {
        Instant t = Instant.parse("2026-06-01T12:00:00Z");
        String sys = "Eol Prou AE-R c5-465";
        List<JournalRecord> log = List.of(
                new ScanBaryCentreRecord(
                        t, 1, sys + " barycentre 1", List.of(new ParentRef(ParentRef.ParentType.NULL, 0)),
                        List.of(), null),
                scan(t, 9781, sys, "Star"),
                scan(t, 2, sys + " A", "Star"),
                scan(t, 3, sys + " B", "Star"),
                scan(t, 9, sys + " B", "Star"),
                scan(t, 4, sys + " C", "Star"),
                scan(t, 10, sys + " C", "Star"));
        List<JournalRecord> out = JournalEventLogUtil.normalizeForSystemBuild(sys, log);
        long stars = out.stream().filter(r -> r instanceof ScanRecord).count();
        assertEquals(3, stars);
    }

    private static ScanRecord scan(Instant t, int id, String name, String type) {
        return new ScanRecord(
                t, id, name, type, "", 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), null, true, false);
    }
}
