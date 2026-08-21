package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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

    @Test
    void geometrySnapshotIsUnaffectedByBodiesDiscoveredAfterCapture() {
        BodyInfo star = body(0, "Test System");
        Map<Integer, BodyInfo> liveBodies = new LinkedHashMap<>();
        liveBodies.put(Integer.valueOf(0), star);

        Map<Integer, BodyInfo> snapshot = SystemTabPanel.snapshotBodiesForGeometry(liveBodies);
        liveBodies.put(Integer.valueOf(1), body(1, "Test System 1"));

        assertEquals(1, snapshot.size());
        assertSame(star, snapshot.get(Integer.valueOf(0)));
        assertFalse(snapshot.containsKey(Integer.valueOf(1)),
                "one geometry calculation must see one stable set of bodies");
    }

    @Test
    void geometrySnapshotRetriesWhenABodyArrivesDuringCapture() {
        Map<Integer, BodyInfo> liveBodies = new MutatesDuringFirstIterationMap();
        liveBodies.put(Integer.valueOf(0), body(0, "Test System"));
        liveBodies.put(Integer.valueOf(1), body(1, "Test System 1"));

        Map<Integer, BodyInfo> snapshot = SystemTabPanel.snapshotBodiesForGeometry(liveBodies);

        assertEquals(3, snapshot.size());
        assertTrue(snapshot.containsKey(Integer.valueOf(2)));
    }

    private static final class MutatesDuringFirstIterationMap extends LinkedHashMap<Integer, BodyInfo> {
        private boolean mutated;

        @Override
        public Set<Map.Entry<Integer, BodyInfo>> entrySet() {
            Set<Map.Entry<Integer, BodyInfo>> entries = super.entrySet();
            return new AbstractSet<>() {
                @Override
                public Iterator<Map.Entry<Integer, BodyInfo>> iterator() {
                    Iterator<Map.Entry<Integer, BodyInfo>> delegate = entries.iterator();
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return delegate.hasNext();
                        }

                        @Override
                        public Map.Entry<Integer, BodyInfo> next() {
                            Map.Entry<Integer, BodyInfo> next = delegate.next();
                            if (!mutated) {
                                mutated = true;
                                MutatesDuringFirstIterationMap.this.put(
                                        Integer.valueOf(2), body(2, "Test System 2"));
                            }
                            return next;
                        }
                    };
                }

                @Override
                public int size() {
                    return entries.size();
                }
            };
        }
    }

    private static BodyInfo body(int bodyId, String name) {
        BodyInfo body = new BodyInfo();
        body.setBodyId(bodyId);
        body.setBodyName(name);
        body.setBodyShortName(name);
        return body;
    }
}
