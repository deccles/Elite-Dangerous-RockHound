package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.dce.ed.cache.CachedBody;
import org.dce.ed.cache.CachedSystem;
import org.dce.ed.state.BodyInfo;
import org.dce.systemmodel.journal.JournalRecord;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanRecord;
import org.junit.jupiter.api.Test;

class CachedBodyJournalBridgeTest {

    @Test
    void mergeMissingFromCache_addsPlanetNotInJournalLog() {
        CachedSystem cs = new CachedSystem();
        cs.bodies = new java.util.ArrayList<>();
        CachedBody planet5 = new CachedBody();
        planet5.bodyId = 42;
        planet5.bodyName = "Eol Prou NN-Y b31-0 5";
        planet5.planetClass = "High metal content body";
        planet5.distanceLs = 100;
        planet5.immediateParentBodyId = 0;
        planet5.journalParentRefs = List.of("Star:0");
        cs.bodies.add(planet5);

        List<JournalRecord> merged = CachedBodyJournalBridge.mergeMissingFromCache(
                "Eol Prou NN-Y b31-0", List.of(), cs);

        assertEquals(1, merged.size());
        ScanRecord scan = (ScanRecord) merged.get(0);
        assertEquals(42, scan.bodyId());
        assertTrue(scan.bodyName().endsWith(" 5"));
    }

    @Test
    void mergeMissingFromBodyInfo_addsScanFromBodyInfoMap() {
        BodyInfo star = new BodyInfo();
        star.setBodyId(0);
        star.setBodyName("Test System");
        star.setStarType("M");
        star.setImmediateParentBodyId(-1);
        star.setJournalParentRefs(List.of());

        java.util.Map<Integer, BodyInfo> bodies = java.util.Map.of(Integer.valueOf(0), star);
        List<JournalRecord> merged = CachedBodyJournalBridge.mergeMissingFromBodyInfo(
                "Test System", List.of(), bodies);

        assertEquals(1, merged.size());
        assertEquals(0, ((ScanRecord) merged.get(0)).bodyId());
    }

    @Test
    void parentsFromCache_immediateParentZero_isNullZeroNotStarZero() {
        CachedBody star = new CachedBody();
        star.bodyId = 1;
        star.bodyName = "Binary B";
        star.starType = "M";
        star.immediateParentBodyId = 0;
        ScanRecord scan = CachedBodyJournalBridge.toScanRecord(star);
        assertEquals(1, scan.parents().size());
        assertEquals(ParentRef.ParentType.NULL, scan.parents().get(0).type());
        assertEquals(0, scan.parents().get(0).bodyId());
    }

    @Test
    void mergeMissingFromBodyInfo_refreshesStaleNullParentOnExistingJournalScan() {
        org.dce.ed.state.BodyInfo starB = new org.dce.ed.state.BodyInfo();
        starB.setBodyId(2);
        starB.setBodyName("Eol Prou SV-A c15-56 B");
        starB.setStarType("K");
        starB.setImmediateParentBodyId(0);
        org.dce.ed.state.BodyInfo b1 = new org.dce.ed.state.BodyInfo();
        b1.setBodyId(19);
        b1.setBodyName("Eol Prou SV-A c15-56 B 1");
        b1.setPlanetClass("Sudarsky class II gas giant");
        b1.setImmediateParentBodyId(2);
        java.util.Map<Integer, org.dce.ed.state.BodyInfo> bodies = java.util.Map.of(
                Integer.valueOf(2), starB, Integer.valueOf(19), b1);
        ScanRecord stale = new ScanRecord(
                java.time.Instant.EPOCH,
                19,
                b1.getBodyName(),
                "Planet",
                "Sudarsky class II gas giant",
                163373.0,
                0, 0, 0, 0, 0, 0, 0, 0,
                java.util.List.of(new ParentRef(ParentRef.ParentType.NULL, 2)),
                null,
                true,
                false);
        java.util.List<org.dce.systemmodel.journal.JournalRecord> merged =
                CachedBodyJournalBridge.mergeMissingFromBodyInfo(
                        "Eol Prou SV-A c15-56", java.util.List.of(stale), bodies);
        assertEquals(2, merged.size());
        ScanRecord out = merged.stream()
                .filter(r -> r instanceof ScanRecord s && s.bodyId() == 19)
                .map(r -> (ScanRecord) r)
                .findFirst()
                .orElseThrow();
        assertEquals(ParentRef.ParentType.STAR, out.parents().get(0).type());
        assertEquals(2, out.parents().get(0).bodyId());
    }

    @Test
    void parentsFromCache_wrongJournalNullParentRef_correctsToStar() {
        CachedBody starB = new CachedBody();
        starB.bodyId = 2;
        starB.bodyName = "Eol Prou SV-A c15-56 B";
        starB.starType = "K";
        starB.immediateParentBodyId = 0;
        CachedBody b1 = new CachedBody();
        b1.bodyId = 19;
        b1.bodyName = "Eol Prou SV-A c15-56 B 1";
        b1.planetClass = "Sudarsky class II gas giant";
        b1.immediateParentBodyId = 2;
        b1.journalParentRefs = List.of("Null:2", "Null:0");
        java.util.Map<Integer, CachedBody> cacheById = java.util.Map.of(
                Integer.valueOf(2), starB, Integer.valueOf(19), b1);
        ScanRecord scan = CachedBodyJournalBridge.toScanRecord(b1, null, cacheById);
        assertEquals(ParentRef.ParentType.STAR, scan.parents().get(0).type());
        assertEquals(2, scan.parents().get(0).bodyId());
    }

    @Test
    void mergeMissingFromBodyInfo_wrongJournalRefsOnBodyInfo_refreshesStaleNullScan() {
        BodyInfo starB = new BodyInfo();
        starB.setBodyId(2);
        starB.setBodyName("Eol Prou SV-A c15-56 B");
        starB.setStarType("K");
        starB.setImmediateParentBodyId(0);
        BodyInfo b1 = new BodyInfo();
        b1.setBodyId(19);
        b1.setBodyName("Eol Prou SV-A c15-56 B 1");
        b1.setPlanetClass("Sudarsky class II gas giant");
        b1.setImmediateParentBodyId(2);
        b1.setJournalParentRefs(List.of("Null:2", "Null:0"));
        java.util.Map<Integer, BodyInfo> bodies = java.util.Map.of(
                Integer.valueOf(2), starB, Integer.valueOf(19), b1);
        ScanRecord stale = new ScanRecord(
                java.time.Instant.EPOCH,
                19,
                b1.getBodyName(),
                "Planet",
                "Sudarsky class II gas giant",
                163373.0,
                0, 0, 0, 0, 0, 0, 0, 0,
                java.util.List.of(new ParentRef(ParentRef.ParentType.NULL, 2)),
                null,
                true,
                false);
        List<JournalRecord> merged = CachedBodyJournalBridge.mergeMissingFromBodyInfo(
                "Eol Prou SV-A c15-56", List.of(stale), bodies);
        ScanRecord out = merged.stream()
                .filter(r -> r instanceof ScanRecord s && s.bodyId() == 19)
                .map(r -> (ScanRecord) r)
                .findFirst()
                .orElseThrow();
        assertEquals(ParentRef.ParentType.STAR, out.parents().get(0).type());
        assertEquals(2, out.parents().get(0).bodyId());
    }

    @Test
    void parentsFromCache_planetUnderStar_usesStarNotNull() {
        CachedBody starB = new CachedBody();
        starB.bodyId = 2;
        starB.bodyName = "Eol Prou SV-A c15-56 B";
        starB.starType = "K";
        starB.immediateParentBodyId = 0;
        CachedBody b1 = new CachedBody();
        b1.bodyId = 19;
        b1.bodyName = "Eol Prou SV-A c15-56 B 1";
        b1.planetClass = "Sudarsky class II gas giant";
        b1.immediateParentBodyId = 2;
        java.util.Map<Integer, CachedBody> cacheById = java.util.Map.of(
                Integer.valueOf(2), starB, Integer.valueOf(19), b1);
        ScanRecord scan = CachedBodyJournalBridge.toScanRecord(b1, null, cacheById);
        assertEquals(ParentRef.ParentType.STAR, scan.parents().get(0).type());
        assertEquals(2, scan.parents().get(0).bodyId());
    }
}
