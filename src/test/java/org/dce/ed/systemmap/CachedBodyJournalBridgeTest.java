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
}
