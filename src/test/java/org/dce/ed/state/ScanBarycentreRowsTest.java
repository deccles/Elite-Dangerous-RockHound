package org.dce.ed.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.systemmap.SystemMapFixture;
import org.dce.ed.systemmap.SystemMapFixtureLoader;
import org.dce.ed.systemmap.SystemMapPipeline;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.Test;

class ScanBarycentreRowsTest {

    @Test
    void replayFixture_wiresNull32ToPlanet7() throws Exception {
        SystemMapFixture fx = SystemMapFixtureLoader.loadClasspath("eol-prou-nn-y-b31-0-7-moons.json");
        Map<Integer, BodyInfo> bodies = new HashMap<>(fx.toBodies());
        BodyInfo bary = bodies.get(Integer.valueOf(32));
        bary.setScanBarycentreRow(true);
        bary.setImmediateParentBodyId(-1);
        bary.setJournalParentRefs(List.of());

        BodyInfo d = bodies.get(Integer.valueOf(33));
        BodyInfo e = bodies.get(Integer.valueOf(34));
        List<ScanEvent.ParentRef> parentsD = List.of(
                new ScanEvent.ParentRef("Null", 32),
                new ScanEvent.ParentRef("Planet", 28),
                new ScanEvent.ParentRef("Star", 0));
        d.setJournalParentRefs(JournalParentRefs.fromScanParents(parentsD));
        ScanBarycentreRows.linkPlanetHostedBarycentreFromMoonScan(d, parentsD, bodies);
        ScanBarycentreRows.linkPlanetHostedBarycentreFromMoonScan(e, parentsD, bodies);

        assertEquals(28, bary.getImmediateParentBodyId());
        assertTrue(bary.getJournalParentRefs().contains("Planet:28"));
        assertTrue(bary.getJournalParentRefs().contains("Star:0"));
        assertTrue(bary.getDistanceLs() > 1403.0 && bary.getDistanceLs() < 1404.0);

        var model = SystemMapPipeline.build(fx.name, bodies, java.time.Instant.EPOCH, true);
        int null32Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(32);
        assertEquals(null32Key, model.resolveParentBodyId(33));
        assertEquals(null32Key, model.resolveParentBodyId(34));
    }

    @Test
    void backfillNullRefFromCoOrbitSibling() throws Exception {
        SystemMapFixture fx = SystemMapFixtureLoader.loadClasspath("eol-prou-nn-y-b31-0-7-moons.json");
        Map<Integer, BodyInfo> bodies = new HashMap<>(fx.toBodies());
        BodyInfo bary = bodies.get(Integer.valueOf(32));
        bary.setImmediateParentBodyId(28);
        BodyInfo d = bodies.get(Integer.valueOf(33));
        d.setJournalParentRefs(List.of("Planet:28", "Star:0"));
        d.setImmediateParentBodyId(28);
        BodyInfo e = bodies.get(Integer.valueOf(34));
        e.setJournalParentRefs(List.of("Null:32", "Planet:28", "Star:0"));

        ScanBarycentreRows.backfillBinaryMoonNullRefsFromCoOrbitPartners(bodies);

        assertTrue(d.getJournalParentRefs().contains("Null:32"));
        var model = SystemMapPipeline.build(fx.name, bodies, java.time.Instant.EPOCH, true);
        int null32Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(32);
        assertEquals(null32Key, model.resolveParentBodyId(33));
    }
}
