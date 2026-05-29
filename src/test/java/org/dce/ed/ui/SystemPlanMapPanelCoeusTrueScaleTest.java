package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.systemmap.MapScaleMode;
import org.dce.ed.systemmap.SystemMapFixture;
import org.dce.ed.systemmap.SystemMapFixtureLoader;
import org.dce.ed.systemmap.SystemMapModel;
import org.dce.ed.systemmap.SystemMapPipeline;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Coeus wide-binary true scale: only model Kepler polylines draw blue orbit strokes — no schematic hub circles.
 */
class SystemPlanMapPanelCoeusTrueScaleTest {

    private static final double DETAIL_VISIBLE_LS = 80.0;

    @Test
    @DisplayName("True scale: no schematic journal-radius circle at star A for star-hosted majors")
    void coeus_trueScale_noSubsystemHubRevolutionRing() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        int idA4 = coeus.bodyIdByLabel("A 4");
        assertTrue(model.isOrbitRevolutionCenter(idA4), "A 4 is a star-hosted revolution centre");

        SystemPlanMapPanel panel = new SystemPlanMapPanel();
        panel.setSize(900, 700);
        panel.setMapScaleMode(MapScaleMode.TRUE_SCALE);
        Map<Integer, double[]> kepler = SystemOrbitGeometry.bodyPositionsMetres(bodies, Instant.EPOCH, false);
        panel.setScene(bodies, kepler, null, null, null, false, Instant.EPOCH);
        panel.zoomFactorForTests(12.0);

        assertTrue(panel.subsystemHubRevolutionPathRingEligibleForTests(idA4, DETAIL_VISIBLE_LS),
                "schematic paint loop would draw hub revolution ring for A 4");
        assertFalse(panel.subsystemHubRevolutionPathRingDrawnForTests(idA4, DETAIL_VISIBLE_LS),
                "true scale must not draw journal-radius schematic circle at star A");
        OrbitGeometryTestSupport.assertExactlyOneDirectParentOrbitStroke(panel.mapModelForTests(), bodies, "A 4",
                panel.orbitLinesForTests(), 200.0);
    }

    @Test
    @DisplayName("True scale playback: no live-distance hub ring through A 1")
    void coeus_trueScale_playback_noLiveDistanceHubRing() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        applyCoeusHighInclinationKeplerElements(bodies);
        int idA1 = coeus.bodyIdByLabel("A 1");

        SystemPlanMapPanel panel = new SystemPlanMapPanel();
        panel.setSize(900, 700);
        panel.setMapScaleMode(MapScaleMode.TRUE_SCALE);
        Instant t0 = Instant.EPOCH;
        Instant t1 = Instant.EPOCH.plus(java.time.temporal.ChronoUnit.DAYS.getDuration().multipliedBy(402));
        Map<Integer, double[]> pos0 = SystemOrbitGeometry.bodyPositionsMetres(bodies, t0, false);
        panel.setScene(bodies, pos0, null, null, null, false, t0);
        panel.zoomFactorForTests(12.0);

        assertTrue(panel.subsystemHubRevolutionPathRingEligibleForTests(idA1, DETAIL_VISIBLE_LS),
                "A 1 is star-hosted and eligible for hub ring in schematic");
        assertFalse(panel.subsystemHubRevolutionPathRingDrawnForTests(idA1, DETAIL_VISIBLE_LS));
        double r0 = panel.hubRevolutionRingRadiusLsForTests(idA1);
        assertTrue(Double.isFinite(r0) && r0 > 100.0);

        Map<Integer, double[]> pos1 = SystemOrbitGeometry.bodyPositionsMetres(bodies, t1, false);
        assertTrue(panel.tryApplyPositionUpdate(bodies, pos1, null, null, null, true, t1));
        double r1 = panel.hubRevolutionRingRadiusLsForTests(idA1);
        assertTrue(Double.isFinite(r1) && r1 > 100.0);
        assertNotEquals(r0, r1, 0.5, "ellipse motion changes live hub-ring radius each tick");
        assertFalse(panel.subsystemHubRevolutionPathRingDrawnForTests(idA1, DETAIL_VISIBLE_LS),
                "true-scale playback must not paint the breathing schematic circle");
    }

    @Test
    @DisplayName("True scale: companion B on wide-binary mutual orbit stroke")
    void coeus_trueScale_companionB_onBinaryBarycentreRing() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        SystemMapModel model = SystemMapPipeline.build(coeus.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        OrbitGeometryTestSupport.assertBodyOnBinaryBarycentreOrbitRing(model, bodies, "B", 0.02, 5.0);
        OrbitGeometryTestSupport.assertNoPerBodyOrbitRing(model, coeus.bodyIdByLabel("B"));
    }

    @Test
    @DisplayName("True scale playback: companion B stays on mutual ring after tick")
    void coeus_trueScale_playback_companionB_onBinaryBarycentreRing() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        Instant t1 = Instant.EPOCH.plus(java.time.temporal.ChronoUnit.DAYS.getDuration().multipliedBy(402));
        SystemPlanMapPanel panel = new SystemPlanMapPanel();
        panel.setSize(900, 700);
        panel.setMapScaleMode(MapScaleMode.TRUE_SCALE);
        Map<Integer, double[]> pos0 = SystemOrbitGeometry.bodyPositionsMetres(bodies, Instant.EPOCH, false);
        panel.setScene(bodies, pos0, null, null, null, false, Instant.EPOCH);
        Map<Integer, double[]> pos1 = SystemOrbitGeometry.bodyPositionsMetres(bodies, t1, false);
        assertTrue(panel.tryApplyPositionUpdate(bodies, pos1, null, null, null, true, t1));
        SystemMapModel model = panel.mapModelForTests();
        OrbitGeometryTestSupport.assertBodyOnBinaryBarycentreOrbitRing(model, bodies, "B", 0.02, 5.0);
    }

    private static void applyCoeusHighInclinationKeplerElements(Map<Integer, BodyInfo> bodies) {
        BodyInfo a1 = bodies.get(Integer.valueOf(OrbitGeometryTestSupport.findByShortName(bodies, "A 1")));
        if (a1 != null) {
            a1.setSemiMajorAxisM(8.93e10);
            a1.setEccentricity(0.22);
            a1.setOrbitalInclination(1.2);
            a1.setAscendingNode(45.0);
            a1.setPeriapsis(10.0);
            a1.setMeanAnomaly(0.0);
            a1.setOrbitalPeriod(1.5e7);
        }
        BodyInfo a4 = bodies.get(Integer.valueOf(OrbitGeometryTestSupport.findByShortName(bodies, "A 4")));
        if (a4 != null) {
            a4.setSemiMajorAxisM(2.298e11);
            a4.setEccentricity(0.35);
            a4.setOrbitalInclination(89.0);
            a4.setAscendingNode(120.0);
            a4.setPeriapsis(200.0);
            a4.setMeanAnomaly(1.0);
            a4.setOrbitalPeriod(2.2e7);
        }
        BodyInfo a5 = bodies.get(Integer.valueOf(OrbitGeometryTestSupport.findByShortName(bodies, "A 5")));
        if (a5 != null) {
            a5.setSemiMajorAxisM(1.85e11);
            a5.setEccentricity(0.28);
            a5.setOrbitalInclination(88.5);
            a5.setAscendingNode(30.0);
            a5.setPeriapsis(90.0);
            a5.setMeanAnomaly(2.0);
            a5.setOrbitalPeriod(1.9e7);
        }
    }
}
