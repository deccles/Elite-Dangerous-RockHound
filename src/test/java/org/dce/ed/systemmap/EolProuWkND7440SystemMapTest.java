package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Eol Prou WK-N d7-440: nested four-star hierarchy (A+B, +C, +D). Regression for infinite recursion between
 * {@code nestedPlanetBinaryNullIdsUnderOuterTrunk} and {@code isPlanetBinaryNullParentId}.
 */
class EolProuWkND7440SystemMapTest {

    private static Map<Integer, BodyInfo> bodies;
    private static SystemMapModel model;

    @BeforeAll
    static void load() throws IOException {
        SystemMapFixture fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-wk-n-d7-440.json");
        bodies = fixture.toBodies();
        model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);
    }

    @Test
    void buildsWithoutRecursion() {
        assertTrue(SystemOrbitGeometry.isHierarchicalWideBinary(bodies));
        assertFalse(SystemOrbitGeometry.isHierarchicalTripleStarMap(bodies));
        assertTrue(model.orbitPolylines().size() >= 2);
    }

    @Test
    void planetBinaryNullChecksTerminate() {
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || !e.getValue().isScanBarycentreRow()) {
                continue;
            }
            SystemOrbitGeometry.isPlanetBinaryNullParentId(e.getKey().intValue(), bodies);
        }
    }
}
