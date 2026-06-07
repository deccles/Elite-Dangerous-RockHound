package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Eol Prou WK-N d7-539: five stars, A-branch planets, B/C/D/E distant cluster. */
class EolProuWkND7539SystemMapTest {

    private static final double LS = SystemOrbitGeometry.LIGHT_SECOND_METRES;
    private static final double MAX_PRIMARY_HELIO_RING_LS = 2_000.0;

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static SystemMapModel model;
    private static int idA;
    private static int idB;
    private static int idC;
    private static int idD;
    private static int idE;

    @BeforeAll
    static void load() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-wk-n-d7-539.json");
        bodies = fixture.toBodies();
        model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);
        idA = fixture.bodyIdByLabel("A");
        idB = fixture.bodyIdByLabel("B");
        idC = fixture.bodyIdByLabel("C");
        idD = fixture.bodyIdByLabel("D");
        idE = fixture.bodyIdByLabel("E");
    }

    @Test
    void classification() {
        System.out.println("wideBinary=" + SystemOrbitGeometry.isHierarchicalWideBinary(bodies));
        System.out.println("triple=" + SystemOrbitGeometry.isHierarchicalTripleStarMap(bodies));
        System.out.println("cohesive=" + SystemOrbitGeometry.hierarchicalCompanionBranchStarsCohesive(bodies));
        System.out.println("nonPrimaryStars=" + model.classification());
        double dAb = dist(idA, idB) / LS;
        double dAc = dist(idA, idC) / LS;
        double dBc = dist(idB, idC) / LS;
        double dBe = dist(idB, idE) / LS;
        System.out.printf("dAB=%.1f dAC=%.1f dBC=%.1f dBE=%.1f%n", dAb, dAc, dBc, dBe);
        for (OrbitPolylineWorldXY p : model.orbitPolylines()) {
            if (p == null) {
                continue;
            }
            double cx = OrbitGeometryTestSupport.ringCentroid(p.wx);
            double cy = OrbitGeometryTestSupport.ringCentroid(p.wy);
            double r = OrbitGeometryTestSupport.meanRadius(p.wx, p.wy, cx, cy) / LS;
            System.out.printf("poly id=%d est=%s r=%.1f Ls%n", p.bodyId, p.estimated, r);
        }
    }

    @Test
    void noHeliocentricRingAroundA() {
        OrbitGeometryTestSupport.assertNoHeliocentricRingAroundPrimaryStar(model, bodies, idA,
                MAX_PRIMARY_HELIO_RING_LS);
    }

    @Test
    void aBranchPlanetsOrbitA_notWideBinaryGuide() {
        for (String label : new String[] { "A 1", "A 2", "A 3" }) {
            int id = fixture.bodyIdByLabel(label);
            assertTrue(model.resolveParentBodyId(id) == idA, label + " parent");
            double sep = dist(id, idA) / LS;
            assertTrue(sep < 900.0, label + " near A; sep=" + sep);
        }
    }

    @Test
    void companionCluster_notCollapsedOntoA() {
        double dAb = dist(idA, idB) / LS;
        assertTrue(dAb > 3_000.0, "B on companion trunk not beside A; dAb=" + dAb);
        double dBe = dist(idB, idE) / LS;
        assertTrue(dBe > 500.0, "E offset from B/C/D cluster by journal spread; dBe=" + dBe);
    }

    @Test
    void noInnerStellarPairRingWhenAHasPlanetBranch() {
        boolean hasInner = false;
        for (OrbitPolylineWorldXY p : model.orbitPolylines()) {
            if (p != null && p.bodyId == SystemOrbitGeometry.HIERARCHICAL_INNER_STELLAR_PAIR_POLYLINE_ID) {
                hasInner = true;
            }
        }
        assertFalse(hasInner, "A-branch system must not show misleading inner A-companion ring");
    }

    private static double dist(int a, int b) {
        return Math.hypot(model.mapPlaneX(b) - model.mapPlaneX(a), model.mapPlaneY(b) - model.mapPlaneY(a));
    }
}
