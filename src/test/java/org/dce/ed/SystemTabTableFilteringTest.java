package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.junit.jupiter.api.Test;

class SystemTabTableFilteringTest {

    @Test
    void systemTableBodiesExcludeScanBarycentresButDoNotMutateStateMap() {
        BodyInfo star = body(0, "Test System");
        star.setOrbitalEpochMillis(Long.valueOf(1_700_000_000_000L));
        BodyInfo barycentre = body(2, "Null:2");
        barycentre.setScanBarycentreRow(true);
        BodyInfo planet = body(3, "Test System 1");
        planet.setOrbitalEpochMillis(Long.valueOf(1_700_000_000_000L));

        Map<Integer, BodyInfo> bodies = new LinkedHashMap<>();
        bodies.put(Integer.valueOf(0), star);
        bodies.put(Integer.valueOf(2), barycentre);
        bodies.put(Integer.valueOf(3), planet);

        Map<Integer, BodyInfo> tableBodies = SystemTabPanel.systemTableBodiesExcludingBarycentres(bodies);

        assertEquals(2, tableBodies.size());
        assertSame(star, tableBodies.get(Integer.valueOf(0)));
        assertSame(planet, tableBodies.get(Integer.valueOf(3)));
        assertFalse(tableBodies.containsKey(Integer.valueOf(2)));

        assertTrue(bodies.containsKey(Integer.valueOf(2)), "Map geometry should keep barycentre rows in system state");
    }

    @Test
    void systemTableBodiesExcludeEdsmOnlyPlanets() {
        BodyInfo star = body(0, "Test System");
        star.setOrbitalEpochMillis(Long.valueOf(1_700_000_000_000L));

        BodyInfo edsmPlanet = body(4, "Test System 2");
        edsmPlanet.setPlanetClass("High metal content world");
        edsmPlanet.setWasDiscovered(Boolean.TRUE);
        edsmPlanet.setDistanceLs(1200.0);

        BodyInfo scannedPlanet = body(5, "Test System 3");
        scannedPlanet.setPlanetClass("Rocky body");
        scannedPlanet.setWasDiscovered(Boolean.FALSE);
        scannedPlanet.setWasMapped(Boolean.FALSE);
        scannedPlanet.setWasFootfalled(Boolean.FALSE);

        Map<Integer, BodyInfo> bodies = new LinkedHashMap<>();
        bodies.put(Integer.valueOf(0), star);
        bodies.put(Integer.valueOf(4), edsmPlanet);
        bodies.put(Integer.valueOf(5), scannedPlanet);

        Map<Integer, BodyInfo> tableBodies = SystemTabPanel.systemTableBodiesExcludingBarycentres(bodies);

        assertEquals(2, tableBodies.size());
        assertSame(star, tableBodies.get(Integer.valueOf(0)));
        assertSame(scannedPlanet, tableBodies.get(Integer.valueOf(5)));
        assertFalse(tableBodies.containsKey(Integer.valueOf(4)));
        assertTrue(bodies.containsKey(Integer.valueOf(4)), "EDSM-only rows may remain in state for map enrichment");
    }

    private static BodyInfo body(int bodyId, String name) {
        BodyInfo body = new BodyInfo();
        body.setBodyId(bodyId);
        body.setBodyName(name);
        body.setBodyShortName(name);
        return body;
    }
}
