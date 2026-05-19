package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Eol Prou RN-I c10-276: wide binary A+B. Live cache can parent A 3 moons to companion star B; the map must keep
 * satellites on gas giant A 3 on the primary branch.
 */
class EolProuRnIC10276WideBinaryTest {

    private static final double LS = SystemOrbitGeometry.LIGHT_SECOND_METRES;

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static SystemMapModel model;
    private static int idB;
    private static int idA3;

    @BeforeAll
    static void load() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-rn-i-c10-276-wide-binary.json");
        bodies = fixture.toBodies();
        model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);
        idB = fixture.bodyIdByLabel("B");
        idA3 = fixture.bodyIdByLabel("A 3");
    }

    @Test
    void classifiedAsWideBinary() {
        assertTrue(model.classification().wideBinary());
        assertFalse(SystemOrbitGeometry.isHierarchicalWideBinary(bodies));
        assertEquals(2, model.classification().mapStellarCount());
    }

    @Test
    void a3Moons_resolveToA3_notCompanionStarB() {
        for (String moon : List.of("A 3 a", "A 3 b", "A 3 c", "A 3 d", "A 3 e", "A 3 f")) {
            int mid = fixture.bodyIdByLabel(moon);
            assertEquals(idB, bodies.get(mid).getImmediateParentBodyId(),
                    "fixture simulates cache parenting " + moon + " to B");
            assertEquals(idA3, model.resolveParentBodyId(mid), moon);
        }
    }

    @Test
    void a3Moons_nearA3_notAtStarB() {
        for (String moon : List.of("A 3 a", "A 3 b", "A 3 c", "A 3 d", "A 3 e", "A 3 f")) {
            int mid = fixture.bodyIdByLabel(moon);
            double sepHost = Math.hypot(model.mapPlaneX(mid) - model.mapPlaneX(idA3),
                    model.mapPlaneY(mid) - model.mapPlaneY(idA3)) / LS;
            double sepB = Math.hypot(model.mapPlaneX(mid) - model.mapPlaneX(idB),
                    model.mapPlaneY(mid) - model.mapPlaneY(idB)) / LS;
            double hint = Math.abs(bodies.get(mid).getDistanceLs() - bodies.get(idA3).getDistanceLs());
            assertTrue(sepHost <= Math.max(25.0, hint * 1.15),
                    moon + " should orbit A 3; sep=" + sepHost + " Ls journalHint=" + hint + " Ls");
            assertTrue(sepB > 500.0, moon + " must not sit on companion star B; sepB=" + sepB + " Ls");
            OrbitGeometryTestSupport.assertBodyOnPerBodyOrbitRing(model, bodies, moon, 4.0);
        }
    }

    @Test
    void a3_isSubsystemHub() {
        assertTrue(model.subsystemHubBodyIds().contains(Integer.valueOf(idA3)));
    }

    @Test
    void bBranchPlanets_parentToB() {
        for (String label : List.of("B 1", "B 2", "B 3")) {
            assertEquals(idB, model.resolveParentBodyId(fixture.bodyIdByLabel(label)), label);
        }
    }

}
