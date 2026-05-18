package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression for the May 2026 screenshot: one giant ring around star A at ~49k Ls with B/C on the rim and BCD labels
 * on that circle. Tests mirror {@link org.dce.ed.ui.SystemPlanMapPanel} ({@link SystemMapPipeline} + polylines).
 */
class EorAowsyHeliocentricRingRegressionTest {

    private static final double MAX_PRIMARY_RING_LS = 12_000.0;

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static SystemMapModel model;
    private static int idA;

    @BeforeAll
    static void load() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eor-aowsy-ri-k-c8-3670.json");
        bodies = fixture.toBodies();
        model = SystemMapPipeline.build(fixture.name, bodies, java.time.Instant.EPOCH, true);
        idA = fixture.bodyIdByLabel("A");
    }

    @Test
    @DisplayName("noHeliocentricRingAroundPrimaryStar_eorAowsy (journal fixture)")
    void noHeliocentricRingAroundPrimaryStar_eorAowsy() {
        OrbitGeometryTestSupport.assertNoHeliocentricRingAroundPrimaryStar(model, bodies, idA, MAX_PRIMARY_RING_LS);
    }

    @Test
    @DisplayName("RockHound-style cache: B/C parented to A still no heliocentric ring")
    void noHeliocentricRing_whenCompanionsWronglyParentedToArrivalStar() throws IOException {
        Map<Integer, BodyInfo> copy = fixture.toBodies();
        int aId = fixture.bodyIdByLabel("A");
        for (String label : new String[] { "B", "C" }) {
            BodyInfo b = copy.get(Integer.valueOf(fixture.bodyIdByLabel(label)));
            if (b != null) {
                b.setImmediateParentBodyId(aId);
            }
        }
        SystemMapModel broken = SystemMapPipeline.build(fixture.name, copy, java.time.Instant.EPOCH, true);
        int null3 = SystemOrbitGeometry.planetBinaryBarycentreMapKey(3);
        assertEquals(null3, broken.resolveParentBodyId(fixture.bodyIdByLabel("B")));
        assertEquals(null3, broken.resolveParentBodyId(fixture.bodyIdByLabel("C")));
        assertFalse(broken.hasBarycentreMutualRing());
        OrbitGeometryTestSupport.assertNoHeliocentricRingAroundPrimaryStar(broken, copy, idA, MAX_PRIMARY_RING_LS);
    }

    @Test
    @DisplayName("GUI rebuild path (SystemPlanMapPanel.rebuildOrbitPolylines) — no barycentre ring")
    void rebuildOrbitPolylines_noHeliocentricRing() {
        var rebuilt = SystemMapPipeline.rebuildOrbitPolylines(model,
                new HashMap<>(model.positionsMetres()), 96, Double.NaN);
        assertFalse(rebuilt.isEmpty());
        for (var poly : rebuilt) {
            assertFalse(poly != null
                    && poly.bodyId == SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID);
        }
        OrbitGeometryTestSupport.assertNoHeliocentricRingAroundPrimaryStar(model, bodies, idA, MAX_PRIMARY_RING_LS,
                rebuilt);
    }
}
