package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.PlanetaryRingBand;
import org.junit.jupiter.api.Test;

class RingGeometryUtilTest {

    @Test
    void fromJournal_keepsInnerAndOuterRadii() {
        List<PlanetaryRingBand> bands = RingGeometryUtil.fromJournal(List.of(
                new ScanEvent.RingInfo("Test 6 A Ring", "eRingClass_Rocky", 1.2e8, 1.8e8),
                new ScanEvent.RingInfo("Test 6 B Ring", "eRingClass_Icy", 2.0e8, 2.6e8)));
        assertEquals(2, bands.size());
        assertEquals(1.2e8, bands.get(0).innerRadM.doubleValue(), 1.0);
        assertEquals(1.8e8, bands.get(0).outerRadM.doubleValue(), 1.0);
    }

    @Test
    void mergeBandsInto_prefersJournalReplace() {
        BodyInfo body = new BodyInfo();
        body.setPlanetaryRingBands(List.of(new PlanetaryRingBand("old", "Rocky", 1, 2)));
        RingGeometryUtil.mergeBandsInto(body,
                List.of(new PlanetaryRingBand("new", "Icy", 10, 20)), true);
        assertEquals(1, body.getPlanetaryRingBands().size());
        assertEquals(10.0, body.getPlanetaryRingBands().get(0).innerRadM.doubleValue(), 0.001);
    }

    @Test
    void hasAccurateDrawGeometry_requiresValidBand() {
        BodyInfo body = new BodyInfo();
        assertFalse(RingGeometryUtil.hasAccurateDrawGeometry(body));
        body.setPlanetaryRingBands(List.of(new PlanetaryRingBand("A", "Rocky", 1e8, 2e8)));
        assertTrue(RingGeometryUtil.hasAccurateDrawGeometry(body));
    }
}
