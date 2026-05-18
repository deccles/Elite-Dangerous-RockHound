package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.dce.ed.TestEnvironment;
import org.dce.ed.cache.CachedSystem;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Processor/cache round-trip for Eor Aowsy: journal parents must survive {@link SystemCache#storeSystem} /
 * {@link SystemCache#loadInto} and yield the same schematic layout as the committed fixture.
 */
class EorAowsyCacheIntegrationTest {

    private static final String SYSTEM = "Eor Aowsy RI-K c8-3670";
    private static final long SYSTEM_ADDRESS = 1008877717987402L;
    private static final double MAX_PRIMARY_RING_LS = 12_000.0;

    static {
        TestEnvironment.ensureTestIsolation();
    }

    @BeforeEach
    void resetCache() {
        SystemCache.getInstance().clearAndDeleteOnDisk();
    }

    @Test
    void cacheRoundTrip_preservesJournalParentsAndLayout() throws IOException {
        SystemMapFixture fixture = SystemMapFixtureLoader.loadClasspath("eor-aowsy-ri-k-c8-3670.json");
        SystemState written = systemStateFromFixture(fixture);
        SystemCache cache = SystemCache.getInstance();
        cache.storeSystem(written);

        CachedSystem cs = cache.get(SYSTEM_ADDRESS, SYSTEM);
        assertTrue(cs != null && cs.bodies != null);
        SystemState loaded = new SystemState();
        cache.loadInto(loaded, cs);

        assertEquals(3, loaded.getBodies().get(Integer.valueOf(4)).getImmediateParentBodyId());
        assertEquals(3, loaded.getBodies().get(Integer.valueOf(5)).getImmediateParentBodyId());
        assertEquals(2, loaded.getBodies().get(Integer.valueOf(6)).getImmediateParentBodyId());
        assertEquals(49, loaded.getBodies().get(Integer.valueOf(50)).getImmediateParentBodyId());
        assertEquals(50, loaded.getBodies().get(Integer.valueOf(52)).getImmediateParentBodyId());

        Map<Integer, BodyInfo> bodies = loaded.getBodies();
        SystemMapModel model = SystemMapPipeline.build(SYSTEM, bodies, Instant.EPOCH, true);
        int idA = fixture.bodyIdByLabel("A");
        int null3Key = SystemOrbitGeometry.planetBinaryBarycentreMapKey(3);

        assertEquals(null3Key, model.resolveParentBodyId(4));
        assertEquals(null3Key, model.resolveParentBodyId(5));
        assertNotEquals(idA, model.resolveParentBodyId(4));
        assertNotEquals(idA, model.resolveParentBodyId(5));
        OrbitGeometryTestSupport.assertHierarchicalSchematicBarycentreRing(model, bodies, idA);
        OrbitGeometryTestSupport.assertNoHeliocentricRingAroundPrimaryStar(model, bodies, idA, MAX_PRIMARY_RING_LS);
    }

    private static SystemState systemStateFromFixture(SystemMapFixture fixture) {
        SystemState state = new SystemState();
        state.setSystemName(fixture.name);
        state.setSystemAddress(SYSTEM_ADDRESS);
        for (Map.Entry<Integer, BodyInfo> e : fixture.toBodies().entrySet()) {
            BodyInfo b = e.getValue();
            if (b == null) {
                continue;
            }
            int id = e.getKey().intValue();
            b.setBodyId(id);
            state.getBodies().put(Integer.valueOf(id), b);
        }
        return state;
    }
}
