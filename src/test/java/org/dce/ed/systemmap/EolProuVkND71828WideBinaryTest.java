package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Eol Prou VK-N d7-1828: simple wide binary (A+B) with planets on B only. Companion B must orbit the
 * barycentre on the mutual ring — not a giant Kepler stroke centred on star A.
 */
class EolProuVkND71828WideBinaryTest {

    private static final double LS = SystemOrbitGeometry.LIGHT_SECOND_METRES;

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static SystemMapModel model;
    private static int idA;
    private static int idB;

    @BeforeAll
    static void load() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-vk-n-d7-1828-wide-binary.json");
        bodies = fixture.toBodies();
        model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);
        idA = fixture.bodyIdByLabel("A");
        idB = fixture.bodyIdByLabel("B");
    }

    @Test
    void classifiedAsWideBinary() {
        assertTrue(model.classification().wideBinary());
        assertFalse(SystemOrbitGeometry.isHierarchicalWideBinary(bodies));
        assertEquals(2, model.classification().mapStellarCount());
    }

    @Test
    void companionStar_resolvesToBarycentre_notPrimaryA() {
        assertEquals(-1, model.resolveParentBodyId(idA), "A");
        assertEquals(-1, model.resolveParentBodyId(idB), "B");
    }

    @Test
    @DisplayName("No per-body Kepler ring for companion B (no loopy ellipse around A)")
    void companionB_hasNoPerBodyOrbitRing_usesMutualBarycentreRing() {
        OrbitGeometryTestSupport.assertNoPerBodyOrbitRing(model, idB);
        OrbitGeometryTestSupport.assertNoPerBodyOrbitRing(model, idA);
        assertTrue(model.hasBarycentreMutualRing());
        OrbitGeometryTestSupport.assertBodyOnBinaryBarycentreOrbitRing(model, bodies, "B", 0.02, 5.0);
    }

    @Test
    void bPlanets_orbitB_withLocalRings() {
        for (String label : new String[] { "B 1", "B 2" }) {
            int pid = fixture.bodyIdByLabel(label);
            assertEquals(idB, model.resolveParentBodyId(pid), label);
            assertTrue(model.hasOrbitRingForBody(pid), label + " orbit ring");
            double sep = Math.hypot(model.mapPlaneX(pid) - model.mapPlaneX(idB),
                    model.mapPlaneY(pid) - model.mapPlaneY(idB)) / LS;
            assertTrue(sep < 50.0, label + " near B; sep=" + sep + " Ls");
        }
    }

    @Test
    void bPlanets_notNearStarA() {
        for (String label : new String[] { "B 1", "B 2" }) {
            int pid = fixture.bodyIdByLabel(label);
            double sepA = Math.hypot(model.mapPlaneX(pid) - model.mapPlaneX(idA),
                    model.mapPlaneY(pid) - model.mapPlaneY(idA)) / LS;
            assertTrue(sepA > 5000.0, label + " should stay on B branch, not A; sepA=" + sepA + " Ls");
        }
    }
}
