package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.dce.systemmodel.model.HierarchyKeys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Eol Prou OR-V d2-399: A at Null:0, B+C co-orbit at Null:2 (not the same heliocentric ring as A).
 */
class EolProuOrVD2399TopologyTest {

    private static SystemMapFixture fixture;
    private static SystemSession session;
    private static int idA;
    private static int idB;
    private static int idC;
    private static int null2Key;

    @BeforeAll
    static void load() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-or-v-d2-399.json");
        session = SystemSessionFactory.open(
                new SystemMapSystemLoader.Loaded(fixture.name, fixture.toBodies(), "cache"));
        idA = fixture.bodyIdByLabel("A");
        idB = fixture.bodyIdByLabel("B");
        idC = fixture.bodyIdByLabel("C");
        null2Key = HierarchyKeys.baryMapKey(2);
    }

    @Test
    void hierarchy_aAndBcHubOrbitSystemBarycentre() {
        var hg = session.model().hierarchy();
        assertEquals(HierarchyKeys.baryMapKey(0), hg.parentOf(idA).intValue(), "A orbits Null:0");
        assertEquals(null2Key, hg.parentOf(idB).intValue(), "B orbits Null:2");
        assertEquals(null2Key, hg.parentOf(idC).intValue(), "C orbits Null:2");
        assertEquals(HierarchyKeys.baryMapKey(0), hg.parentOf(null2Key).intValue(), "Null:2 hub orbits Null:0");
    }

    @Test
    void positions_aFartherFromOriginThanBcHub() {
        SystemMapModel map = SystemMapPipeline.build(fixture.name, fixture.toBodies(), Instant.EPOCH, false, session);
        double[] posA = map.positionsMetres().get(idA);
        double[] posB = map.positionsMetres().get(idB);
        double[] posC = map.positionsMetres().get(idC);
        double[] posHub = map.positionsMetres().get(null2Key);
        assertNotNull(posA);
        assertNotNull(posB);
        assertNotNull(posC);
        double rA = Math.hypot(posA[0], posA[1]);
        double rHub = posHub != null ? Math.hypot(posHub[0], posHub[1]) : Math.hypot(
                (posB[0] + posC[0]) * 0.5, (posB[1] + posC[1]) * 0.5);
        assertTrue(rA > 0 && rHub > 0);
        assertTrue(Math.abs(rA - rHub) / SystemOrbitGeometry.LIGHT_SECOND_METRES > 10_000,
                "A and B+C hub are on different heliocentric radii (Ls)");
    }

    @Test
    void rings_hubHeliocentricAndPerMemberRingsAroundNull2() {
        SystemMapModel map = SystemMapPipeline.build(fixture.name, fixture.toBodies(), Instant.EPOCH, false, session);
        List<OrbitPolylineWorldXY> polys = map.orbitPolylines();
        int null2RingCount = 0;
        boolean hasAHelio = false;
        boolean hasBRing = false;
        boolean hasCRing = false;
        for (OrbitPolylineWorldXY p : polys) {
            if (p.bodyId == null2Key) {
                null2RingCount++;
            }
            if (p.bodyId == idA) {
                hasAHelio = true;
            }
            if (p.bodyId == idB) {
                hasBRing = true;
            }
            if (p.bodyId == idC) {
                hasCRing = true;
            }
        }
        assertEquals(1, null2RingCount, "Null:2 hub heliocentric ring only");
        assertTrue(hasAHelio, "A heliocentric ring around Null:0");
        assertTrue(hasBRing, "B journal ring around Null:2");
        assertTrue(hasCRing, "C journal ring around Null:2");
        assertEquals(HierarchyKeys.baryMapKey(0), map.resolveParentBodyId(null2Key));
    }
}
