package org.dce.systemmodel.build;

import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.model.BodyKind;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitParentSelectorTest {

    @Test
    void sharedNull_twoPlanets_parentToNull() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        ScanRecord p7 = planet(t, 7, 32, orbit);
        ScanRecord p8 = planet(t, 8, 32, orbit);
        Map<Integer, List<Integer>> members = Map.of(32, List.of(7, 8));
        ParentRef parent = OrbitParentSelector.select(
                BodyKind.PLANET,
                p7,
                List.of(new ParentRef(ParentRef.ParentType.NULL, 32), new ParentRef(ParentRef.ParentType.STAR, 0)),
                Set.of(32),
                members,
                Map.of(7, p7, 8, p8));
        assertEquals(ParentRef.ParentType.NULL, parent.type());
        assertEquals(32, parent.bodyId());
        assertTrue(OrbitParentSelector.sharedNullHub(32, members, Map.of(7, p7, 8, p8)));
    }

    @Test
    void singlePlanetAtNull_heliocentricToStar() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        ScanRecord p7 = planet(t, 7, 32, orbit);
        Map<Integer, List<Integer>> members = Map.of(32, List.of(7));
        ParentRef parent = OrbitParentSelector.select(
                BodyKind.PLANET,
                p7,
                List.of(new ParentRef(ParentRef.ParentType.NULL, 32), new ParentRef(ParentRef.ParentType.STAR, 0)),
                Set.of(32),
                members,
                Map.of(7, p7));
        assertEquals(ParentRef.ParentType.STAR, parent.type());
        assertFalse(OrbitParentSelector.sharedNullHub(32, members, Map.of(7, p7)));
    }

    @Test
    void planetWithNullLaterInChain_heliocentricToStar() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        ScanRecord p3 = new ScanRecord(
                t, 30, "Test 3", "Planet", "Gas Giant", 100,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(
                        new ParentRef(ParentRef.ParentType.STAR, 2),
                        new ParentRef(ParentRef.ParentType.NULL, 44),
                        new ParentRef(ParentRef.ParentType.NULL, 0)),
                orbit, true, false);
        ScanRecord moonA = new ScanRecord(
                t, 21, "Test 2 a", "Planet", "Icy", 100,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(
                        new ParentRef(ParentRef.ParentType.NULL, 44),
                        new ParentRef(ParentRef.ParentType.PLANET, 20),
                        new ParentRef(ParentRef.ParentType.STAR, 2)),
                orbit, true, false);
        ScanRecord moonB = new ScanRecord(
                t, 22, "Test 2 b", "Planet", "Icy", 100,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(
                        new ParentRef(ParentRef.ParentType.NULL, 44),
                        new ParentRef(ParentRef.ParentType.PLANET, 20),
                        new ParentRef(ParentRef.ParentType.STAR, 2)),
                orbit, true, false);
        Map<Integer, ScanRecord> scans = Map.of(30, p3, 21, moonA, 22, moonB);
        Map<Integer, List<Integer>> members = Map.of(44, List.of(21, 22));
        ParentRef parent = OrbitParentSelector.select(
                BodyKind.PLANET,
                p3,
                p3.parents(),
                Set.of(44),
                members,
                scans);
        assertEquals(ParentRef.ParentType.STAR, parent.type());
        assertEquals(2, parent.bodyId());
        assertFalse(OrbitParentSelector.isCoOrbitMajorHub(44, members, scans));
    }

    private static ScanRecord planet(Instant t, int id, int nullId, OrbitalElements orbit) {
        return new ScanRecord(
                t, id, "Test " + id, "Planet", "Rocky", 100,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(
                        new ParentRef(ParentRef.ParentType.NULL, nullId),
                        new ParentRef(ParentRef.ParentType.STAR, 0)),
                orbit, true, false);
    }
}
