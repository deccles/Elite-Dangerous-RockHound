package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Bodies named {@code A …} must orbit star A on the map, not B/C/D — regardless of cache parent corruption.
 */
class PlanetaryBranchConsistencyTest {

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static SystemMapModel model;
    private static int idA;
    private static int idC;

    @BeforeAll
    static void load() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eor-aowsy-ri-k-c8-3670.json");
        bodies = fixture.toBodies();
        model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);
        idA = fixture.bodyIdByLabel("A");
        idC = fixture.bodyIdByLabel("C");
    }

    @Test
    void fixture_journalTopology_planetaryBranchConsistency() {
        OrbitGeometryTestSupport.assertDesignationBranchInvariants(model, bodies);
    }

    @Test
    void cacheParentsAbranchToStarC_resolvesAndMapsToStarA() {
        Map<Integer, BodyInfo> cache = new HashMap<>(bodies);
        for (String label : List.of("A 1", "A 2", "A 3", "A 4", "A 2 a", "A 3 a", "A 3 e", "A 4 b")) {
            cache.get(Integer.valueOf(fixture.bodyIdByLabel(label))).setImmediateParentBodyId(idC);
        }
        SystemMapModel corrupt = SystemMapPipeline.build(fixture.name, cache, Instant.EPOCH, true);
        OrbitGeometryTestSupport.assertDesignationBranchInvariants(corrupt, cache);
    }

    @Test
    void cacheParentsAbranchToStarC_withPlanetClassOnC_resolvesToStarA() {
        Map<Integer, BodyInfo> cache = new HashMap<>(bodies);
        BodyInfo starC = cache.get(Integer.valueOf(idC));
        starC.setPlanetClass("High metal content body");
        starC.setStarType(null);
        for (String label : List.of("A 2", "A 3", "A 3 a", "A 3 e")) {
            cache.get(Integer.valueOf(fixture.bodyIdByLabel(label))).setImmediateParentBodyId(idC);
        }
        SystemMapModel corrupt = SystemMapPipeline.build(fixture.name, cache, Instant.EPOCH, true);
        for (String label : List.of("A 1", "A 2", "A 3", "A 4", "A 3 a", "A 3 e")) {
            assertTrue(corrupt.resolveParentBodyId(fixture.bodyIdByLabel(label)) != idC, label);
        }
        assertEquals(idA, corrupt.resolveParentBodyId(fixture.bodyIdByLabel("A 1")));
        assertEquals(fixture.bodyIdByLabel("A 3"),
                corrupt.resolveParentBodyId(fixture.bodyIdByLabel("A 3 a")));
        OrbitGeometryTestSupport.assertDesignationBranchInvariants(corrupt, cache);
    }

    @Test
    void playback_cacheAbranchOnC_staysNearStarA() {
        Map<Integer, BodyInfo> cache = new HashMap<>(bodies);
        for (String label : List.of("A 2", "A 3", "A 3 a", "A 3 b", "A 3 c", "A 3 e")) {
            cache.get(Integer.valueOf(fixture.bodyIdByLabel(label))).setImmediateParentBodyId(idC);
        }
        SystemMapModel base = SystemMapPipeline.build(fixture.name, cache, Instant.EPOCH, true);
        Map<Integer, double[]> after = SystemMapPipeline.refreshPositionsForPlayback(base,
                new HashMap<>(base.positionsMetres()), Instant.EPOCH, true);
        SystemMapModel playback = SystemMapPipeline.playbackBase(cache, base.projectionAxis0(),
                base.projectionAxis1(), after, base.wideBinaryFlattenFrame());
        OrbitGeometryTestSupport.assertDesignationBranchInvariants(playback, cache);
    }
}
