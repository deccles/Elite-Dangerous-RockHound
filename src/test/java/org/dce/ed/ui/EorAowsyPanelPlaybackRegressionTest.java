package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
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
        return new HashMap<>(fixtureBodies);
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
        assertTrue(dBc < 500.0, "refresh should re-apply schematic B+C; dBc=" + dBc + " Ls");
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
        assertTrue(distBa >= 5_000.0 && distBa <= 15_000.0,
                "BCD on schematic trunk from A after playback; distBa=" + distBa + " Ls");
        assertTrue(panel.mapModelForTests().hasBarycentreMutualRing(), "four-star system barycentre ring");
    }

    @Test
    void playback_aBranchParentedToStarC_onMapNearAnotC() {
        Map<Integer, BodyInfo> bodies = copyBodies();
        corruptBcdParentedToA(bodies);
        for (String label : new String[] { "A 2", "A 3", "A 3 a", "A 3 b", "A 3 e" }) {
            bodies.get(Integer.valueOf(fixture.bodyIdByLabel(label))).setImmediateParentBodyId(idC);
        }
        SystemPlanMapPanel panel = new SystemPlanMapPanel();
        panel.setSize(900, 700);
        Instant epoch = Instant.EPOCH;
        Map<Integer, double[]> pos = SystemOrbitGeometry.bodyPositionsMetres(bodies, epoch, false);
        panel.setScene(bodies, pos, null, null, null, true, epoch);
        for (int tick = 0; tick < 6; tick++) {
            epoch = epoch.plusSeconds(86_400);
            pos = SystemOrbitGeometry.bodyPositionsMetres(bodies, epoch, true);
            panel.tryApplyPositionUpdate(bodies, pos, null, null, null, true, epoch);
        }
        OrbitGeometryTestSupport.assertPlanetaryBranchConsistency(panel.mapModelForTests(), bodies);
    }
}
