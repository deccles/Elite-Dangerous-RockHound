package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmap.SystemMapFixture;
import org.dce.ed.systemmap.SystemMapFixtureLoader;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Painted dot coordinates after {@link SystemPlanMapPanel} setScene + playback must match nuclear branch placement.
 */
class AllFixturesPanelBranchPlacementTest {

    static Stream<String> multiBranchFixtures() {
        return Stream.of(
                "eol-prou-or-v-d2-399.json",
                "eor-aowsy-ri-k-c8-3670.json");
    }

    @ParameterizedTest(name = "playback {0}")
    @MethodSource("multiBranchFixtures")
    @DisplayName("Panel draw + playback — branch letter placement")
    void panel_playback_branchLetterPlacement(String resource) throws IOException {
        SystemMapFixture fixture = SystemMapFixtureLoader.loadClasspath(resource);
        Map<Integer, BodyInfo> bodies = fixture.toBodies();
        SystemPlanMapPanel panel = new SystemPlanMapPanel();
        panel.setSize(900, 700);
        Instant epoch = Instant.EPOCH;
        Map<Integer, double[]> pos = SystemOrbitGeometry.bodyPositionsMetres(bodies, epoch, false);
        panel.setScene(bodies, pos, null, null, null, true, epoch);
        for (int tick = 0; tick < 12; tick++) {
            epoch = epoch.plusSeconds(86_400);
            pos = SystemOrbitGeometry.bodyPositionsMetres(bodies, epoch, true);
            panel.tryApplyPositionUpdate(bodies, pos, null, null, null, true, epoch);
        }
        assertPanelNuclearBranchPlacement(panel, bodies);
        OrbitGeometryTestSupport.assertBranchLetterMapPlacement(panel.mapModelForTests(), bodies);
    }

    @ParameterizedTest(name = "playback corrupt A→C {0}")
    @MethodSource("multiBranchFixtures")
    @DisplayName("Entire A-branch cache-parented to C — drawn dots still on A")
    void panel_playback_entireAbranchParentedToC(String resource) throws IOException {
        SystemMapFixture fixture = SystemMapFixtureLoader.loadClasspath(resource);
        Map<Integer, BodyInfo> bodies = new HashMap<>(fixture.toBodies());
        Map<String, Integer> stars = OrbitGeometryTestSupport.branchStarsByLetter(bodies);
        if (!stars.containsKey("A") || !stars.containsKey("C")) {
            return;
        }
        int idC = stars.get("C").intValue();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getValue() == null || e.getValue().isScanBarycentreRow()) {
                continue;
            }
            String branch = SystemOrbitGeometry.designationBranchLetter(e.getValue());
            if (branch != null && "A".equalsIgnoreCase(branch)) {
                e.getValue().setImmediateParentBodyId(idC);
            }
        }
        SystemPlanMapPanel panel = new SystemPlanMapPanel();
        panel.setSize(900, 700);
        Instant epoch = Instant.EPOCH;
        Map<Integer, double[]> pos = SystemOrbitGeometry.bodyPositionsMetres(bodies, epoch, false);
        panel.setScene(bodies, pos, null, null, null, true, epoch);
        for (int tick = 0; tick < 16; tick++) {
            epoch = epoch.plusSeconds(86_400);
            pos = SystemOrbitGeometry.bodyPositionsMetres(bodies, epoch, true);
            panel.tryApplyPositionUpdate(bodies, pos, null, null, null, true, epoch);
        }
        assertPanelNuclearBranchPlacement(panel, bodies);
    }

    private static void assertPanelNuclearBranchPlacement(SystemPlanMapPanel panel, Map<Integer, BodyInfo> bodies) {
        double marginLs = OrbitGeometryTestSupport.DESIGNATION_BRANCH_MIN_MARGIN_LS;
        Map<String, Integer> stars = OrbitGeometryTestSupport.branchStarsByLetter(bodies);
        if (stars.size() < 2) {
            return;
        }
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isScanBarycentreRow()) {
                continue;
            }
            if (SystemOrbitGeometry.isMapStellarBody(e.getValue())) {
                continue;
            }
            String branch = SystemOrbitGeometry.designationBranchLetter(e.getValue());
            if (branch == null || branch.length() != 1) {
                continue;
            }
            branch = branch.toUpperCase(Locale.ROOT);
            Integer ownStarObj = stars.get(branch);
            assertTrue(ownStarObj != null, "missing branch star " + branch);
            int bodyId = e.getKey().intValue();
            if (!Double.isFinite(panel.dotWorldXForTests(bodyId)) || !Double.isFinite(panel.dotWorldYForTests(bodyId))) {
                continue;
            }
            double nearOwn = panelSepLs(panel, bodyId, ownStarObj.intValue(), ls);
            double bx = panel.dotWorldXForTests(bodyId);
            double by = panel.dotWorldYForTests(bodyId);
            for (Map.Entry<String, Integer> star : stars.entrySet()) {
                if (star.getKey().equals(branch)) {
                    continue;
                }
                double nearOther = panelSepLs(panel, bodyId, star.getValue().intValue(), ls);
                assertTrue(nearOwn + marginLs < nearOther,
                        "drawn " + e.getValue().getShortName() + " at (" + (bx / ls) + "," + (by / ls)
                                + ") Ls must be >= " + marginLs + " Ls nearer star " + branch + " (" + nearOwn
                                + " Ls) than star " + star.getKey() + " (" + nearOther + " Ls)");
            }
        }
    }

    private static double panelSepLs(SystemPlanMapPanel panel, int fromId, int toId, double ls) {
        return Math.hypot(panel.dotWorldXForTests(fromId) - panel.dotWorldXForTests(toId),
                panel.dotWorldYForTests(fromId) - panel.dotWorldYForTests(toId)) / ls;
    }
}
