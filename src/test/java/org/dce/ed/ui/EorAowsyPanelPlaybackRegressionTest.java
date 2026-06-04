package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmap.SystemMapFixture;
import org.dce.ed.systemmap.SystemMapFixtureLoader;
import org.dce.ed.systemmap.SystemMapPipeline;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Mirrors {@link org.dce.ed.SystemTabPanel} map refresh with orbit playback (61 d/s) and live-cache parent corruption.
 */
class EorAowsyPanelPlaybackRegressionTest {

    private static final double LS = SystemOrbitGeometry.LIGHT_SECOND_METRES;

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> fixtureBodies;
    private static int idA;
    private static int idB;
    private static int idC;
    private static int idD;

    @BeforeAll
    static void load() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eor-aowsy-ri-k-c8-3670.json");
        fixtureBodies = fixture.toBodies();
        idA = fixture.bodyIdByLabel("A");
        idB = fixture.bodyIdByLabel("B");
        idC = fixture.bodyIdByLabel("C");
        idD = fixture.bodyIdByLabel("D");
    }

    private static Map<Integer, BodyInfo> copyBodies() {
        return fixture.toBodies();
    }

    /** Live cache: B/C/D parented to arrival star A with synced planet class (not map-stellar). */
    private static void corruptBcdParentedToA(Map<Integer, BodyInfo> bodies) {
        for (String label : new String[] { "B", "C", "D" }) {
            BodyInfo b = bodies.get(Integer.valueOf(fixture.bodyIdByLabel(label)));
            b.setImmediateParentBodyId(idA);
            b.setPlanetClass("High metal content body");
        }
    }

    @Test
    void refreshPositionsForPlayback_keepsBcClose_whenCacheParentsBcToA() {
        Map<Integer, BodyInfo> bodies = copyBodies();
        corruptBcdParentedToA(bodies);
        var model = SystemMapPipeline.build(fixture.name, bodies, java.time.Instant.EPOCH, true);
        Instant epoch = java.time.Instant.EPOCH;
        Map<Integer, double[]> kepler = new HashMap<>(model.positionsMetres());
        Map<Integer, double[]> after = SystemMapPipeline.refreshPositionsForPlayback(model, kepler, epoch, true);
        double dBc = Math.hypot(
                SystemOrbitGeometry.worldAxisMetres(after.get(Integer.valueOf(idB)), 0)
                        - SystemOrbitGeometry.worldAxisMetres(after.get(Integer.valueOf(idC)), 0),
                SystemOrbitGeometry.worldAxisMetres(after.get(Integer.valueOf(idB)), 1)
                        - SystemOrbitGeometry.worldAxisMetres(after.get(Integer.valueOf(idC)), 1))
                / LS;
        assertTrue(dBc < 500.0, "refresh should re-apply B+C layout; dBc=" + dBc + " Ls");
    }

    @Test
    void playbackTicks_keepBcdClusterSeparated() {
        Map<Integer, BodyInfo> bodies = copyBodies();
        corruptBcdParentedToA(bodies);
        SystemPlanMapPanel panel = new SystemPlanMapPanel();
        panel.setSize(900, 700);
        Instant epoch = Instant.EPOCH;
        Map<Integer, double[]> pos = SystemOrbitGeometry.bodyPositionsMetres(bodies, epoch, false);
        panel.setScene(bodies, pos, null, null, null, false, epoch);

        for (int tick = 0; tick < 8; tick++) {
            epoch = epoch.plusSeconds(86_400);
            pos = SystemOrbitGeometry.bodyPositionsMetres(bodies, epoch, true);
            assertTrue(panel.tryApplyPositionUpdate(bodies, pos, null, null, null, true, epoch),
                    "tick " + tick);
        }

        double dBc = Math.hypot(panel.dotWorldXForTests(idB) - panel.dotWorldXForTests(idC),
                panel.dotWorldYForTests(idB) - panel.dotWorldYForTests(idC)) / LS;
        double distBa = Math.hypot(panel.dotWorldXForTests(idB) - panel.dotWorldXForTests(idA),
                panel.dotWorldYForTests(idB) - panel.dotWorldYForTests(idA)) / LS;
        assertTrue(dBc < 500.0, "B and C not stacked after playback; dBc=" + dBc + " Ls");
        assertTrue(distBa >= 40_000.0 && distBa <= 52_000.0,
                "BCD on true-scale trunk from A after playback; distBa=" + distBa + " Ls");
        assertTrue(panel.mapModelForTests().hasBarycentreMutualRing(), "four-star system barycentre ring");
    }

    @Test
    void playbackTicks_barycentreMarkersTrackOrbitGeomPositions() {
        Map<Integer, BodyInfo> bodies = copyBodies();
        SystemPlanMapPanel panel = new SystemPlanMapPanel();
        panel.setSize(900, 700);
        Instant epoch = Instant.EPOCH;
        Map<Integer, double[]> pos = SystemOrbitGeometry.bodyPositionsMetres(bodies, epoch, false);
        panel.setScene(bodies, pos, null, null, null, true, epoch);

        Instant later = epoch.plus(java.time.Duration.ofDays(180));
        pos = SystemOrbitGeometry.bodyPositionsMetres(bodies, later, true);
        assertTrue(panel.tryApplyPositionUpdate(bodies, pos, null, null, null, true, later));

        var model = panel.mapModelForTests();
        Map<Integer, double[]> expected = SystemMapPipeline.refreshPositionsForPlayback(model, pos, later, false);
        int baryKey = 3;
        double[] marker = panel.barycentreMarkerMapXYForTests(baryKey);
        assertNotNull(marker, "Null:3 scan barycentre marker");
        double[] exp = expected.get(Integer.valueOf(baryKey));
        assertNotNull(exp);
        double[] expView = org.dce.ed.systemmap.MapViewProjection.projectFromPositionMetres(exp,
                model.projectionAxis0(), model.projectionAxis1(), panel.viewTiltDegrees());
        assertTrue(Math.hypot(marker[0] - expView[0], marker[1] - expView[1]) < 1.0,
                "barycentre + must use orbitGeomPositions (playback), not frozen pipeline snapshot");
    }

    @Test
    void playback_aBranchParentedToStarC_onMapNearAnotC() {
        Map<Integer, BodyInfo> bodies = copyBodies();
        corruptBcdParentedToA(bodies);
        for (String label : List.of("A 1", "A 2", "A 3", "A 4", "A 2 a", "A 3 a", "A 3 b", "A 3 e", "A 4 c")) {
            bodies.get(Integer.valueOf(fixture.bodyIdByLabel(label))).setImmediateParentBodyId(idC);
        }
        SystemPlanMapPanel panel = new SystemPlanMapPanel();
        panel.setSize(900, 700);
        Instant epoch = Instant.EPOCH;
        Map<Integer, double[]> pos = SystemOrbitGeometry.bodyPositionsMetres(bodies, epoch, false);
        panel.setScene(bodies, pos, null, null, null, true, epoch);
        for (int tick = 0; tick < 12; tick++) {
            epoch = epoch.plusSeconds(86_400);
            pos = SystemOrbitGeometry.bodyPositionsMetres(bodies, epoch, true);
            assertTrue(panel.tryApplyPositionUpdate(bodies, pos, null, null, null, true, epoch),
                    "tick " + tick);
        }
        OrbitGeometryTestSupport.assertNuclearDesignationBranchPlacement(panel.mapModelForTests(), bodies);
        for (String label : List.of("A 2", "A 3", "A 3 a", "A 3 b", "A 4 c")) {
            int bodyId = fixture.bodyIdByLabel(label);
            double nearA = Math.hypot(panel.dotWorldXForTests(bodyId) - panel.dotWorldXForTests(idA),
                    panel.dotWorldYForTests(bodyId) - panel.dotWorldYForTests(idA)) / LS;
            double nearC = Math.hypot(panel.dotWorldXForTests(bodyId) - panel.dotWorldXForTests(idC),
                    panel.dotWorldYForTests(bodyId) - panel.dotWorldYForTests(idC)) / LS;
            assertTrue(nearA + OrbitGeometryTestSupport.DESIGNATION_BRANCH_MIN_MARGIN_LS < nearC,
                    label + " must draw near A (" + nearA + " Ls) not C (" + nearC + " Ls)");
        }
    }
}
