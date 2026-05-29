package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    @DisplayName("RockHound-style cache: B/C/D parented to A with planet class still schematic layout")
    void noHeliocentricRing_whenCompanionsWronglyParentedToArrivalStar() throws IOException {
        Map<Integer, BodyInfo> copy = fixture.toBodies();
        int aId = fixture.bodyIdByLabel("A");
        for (String label : new String[] { "B", "C", "D" }) {
            BodyInfo b = copy.get(Integer.valueOf(fixture.bodyIdByLabel(label)));
            if (b != null) {
                b.setImmediateParentBodyId(aId);
                b.setPlanetClass("High metal content body");
                b.setAtmosphere("thin");
            }
        }
        SystemMapModel broken = SystemMapPipeline.build(fixture.name, copy, java.time.Instant.EPOCH, true);
        int null3 = SystemOrbitGeometry.planetBinaryBarycentreMapKey(3);
        assertEquals(null3, broken.resolveParentBodyId(fixture.bodyIdByLabel("B")));
        assertEquals(null3, broken.resolveParentBodyId(fixture.bodyIdByLabel("C")));
        assertEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(2),
                broken.resolveParentBodyId(fixture.bodyIdByLabel("D")));
        OrbitGeometryTestSupport.assertHierarchicalSchematicBarycentreRing(broken, copy, idA);
        OrbitGeometryTestSupport.assertNoHeliocentricRingAroundPrimaryStar(broken, copy, idA, MAX_PRIMARY_RING_LS);
        assertFalse(SystemOrbitGeometry.isHierarchicalTripleStarMap(copy), "four-star, not triple");
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        int idB = fixture.bodyIdByLabel("B");
        int idC = fixture.bodyIdByLabel("C");
        int idD = fixture.bodyIdByLabel("D");
        double dBc = Math.hypot(broken.mapPlaneX(idB) - broken.mapPlaneX(idC),
                broken.mapPlaneY(idB) - broken.mapPlaneY(idC)) / ls;
        double dBd = Math.hypot(broken.mapPlaneX(idB) - broken.mapPlaneX(idD),
                broken.mapPlaneY(idB) - broken.mapPlaneY(idD)) / ls;
        assertTrue(dBc < 500.0, "B and C on inner mutual orbit, not stacked; dBc=" + dBc + " Ls");
        assertTrue(dBd > dBc * 1.2, "D separated from B+C inner pair; dBd=" + dBd + " dBc=" + dBc + " Ls");
        double distBa = Math.hypot(broken.mapPlaneX(idB) - broken.mapPlaneX(idA),
                broken.mapPlaneY(idB) - broken.mapPlaneY(idA)) / ls;
        assertTrue(distBa >= 40_000.0 && distBa <= 52_000.0,
                "BCD trunk true-scale distance from A; was " + distBa + " Ls");
    }

    @Test
    @DisplayName("live cache: B/C/D with planet class still use wide-binary schematic trunk")
    void noHeliocentricRing_whenCompanionStarsExcludedFromMapStellarCount() {
        Map<Integer, BodyInfo> copy = fixture.toBodies();
        for (String label : new String[] { "B", "C", "D" }) {
            BodyInfo b = copy.get(Integer.valueOf(fixture.bodyIdByLabel(label)));
            if (b != null) {
                b.setPlanetClass("High metal content body");
                b.setAtmosphere("thin");
            }
        }
        SystemMapModel liveLike = SystemMapPipeline.build(fixture.name, copy, java.time.Instant.EPOCH, true);
        assertEquals(org.dce.ed.systemmap.SystemLayoutKind.WIDE_BINARY,
                liveLike.classification().layoutKind());
        OrbitGeometryTestSupport.assertNoHeliocentricRingAroundPrimaryStar(liveLike, copy, idA,
                MAX_PRIMARY_RING_LS);
        OrbitGeometryTestSupport.assertHierarchicalSchematicBarycentreRing(liveLike, copy, idA);
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        int idB = fixture.bodyIdByLabel("B");
        double distBa = Math.hypot(liveLike.mapPlaneX(idB) - liveLike.mapPlaneX(idA),
                liveLike.mapPlaneY(idB) - liveLike.mapPlaneY(idA)) / ls;
        org.junit.jupiter.api.Assertions.assertTrue(distBa >= 40_000.0 && distBa <= 52_000.0,
                "BCD trunk true-scale distance from A; was " + distBa + " Ls");
        boolean hasMutual3 = false;
        boolean hasMutual2 = false;
        boolean hasMutual49 = false;
        for (var poly : liveLike.orbitPolylines()) {
            if (poly == null) {
                continue;
            }
            if (poly.bodyId == SystemOrbitGeometry.PLANET_BINARY_MUTUAL_ORBIT_RING_ID_BASE - 3) {
                hasMutual3 = true;
            }
            if (poly.bodyId == SystemOrbitGeometry.PLANET_BINARY_MUTUAL_ORBIT_RING_ID_BASE - 2) {
                hasMutual2 = true;
            }
            if (poly.bodyId == SystemOrbitGeometry.PLANET_BINARY_MUTUAL_ORBIT_RING_ID_BASE - 49) {
                hasMutual49 = true;
            }
        }
        assertTrue(hasMutual3, "B+C mutual orbit at Null:3");
        assertTrue(hasMutual2, "BCD cluster mutual orbit at Null:2");
        assertTrue(hasMutual49, "BCD 2+3 mutual orbit at Null:49");
        OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(liveLike, copy, "B", 3, 2.5);
        OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(liveLike, copy, "C", 3, 2.5);
        OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(liveLike, copy, "D", 2, 3.5);
        assertTrue(liveLike.orbitPolylines().size() >= 26,
                "hierarchical companion needs schematic + mutual rings; had " + liveLike.orbitPolylines().size());
    }

    @Test
    @DisplayName("GUI rebuild path (SystemPlanMapPanel.rebuildOrbitPolylines) — schematic barycentre ring")
    void rebuildOrbitPolylines_schematicBarycentreRing() {
        var rebuilt = SystemMapPipeline.rebuildOrbitPolylines(model,
                new HashMap<>(model.positionsMetres()), 96, Double.NaN);
        assertFalse(rebuilt.isEmpty());
        OrbitGeometryTestSupport.assertNoHeliocentricRingAroundPrimaryStar(model, bodies, idA, MAX_PRIMARY_RING_LS,
                rebuilt);
        boolean hasBaryRing = false;
        for (var poly : rebuilt) {
            if (poly != null && poly.bodyId == SystemOrbitGeometry.BINARY_BARYCENTRE_ORBIT_RING_BODY_ID) {
                hasBaryRing = true;
            }
        }
        assertTrue(hasBaryRing, "rebuild should keep schematic system barycentre ring");
    }
}
