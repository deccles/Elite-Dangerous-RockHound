package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmap.SystemMapFixture;
import org.dce.ed.systemmap.SystemMapFixtureLoader;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SystemPlanMapPanelAutoZoomTest {

    private static Map<Integer, BodyInfo> twoStarBodies;
    private static Map<Integer, BodyInfo> wideBinaryPlanetsBodies;
    private static Map<Integer, BodyInfo> innerAbBareCBodies;

    @BeforeAll
    static void loadFixtures() throws Exception {
        SystemMapFixture twoStar = SystemMapFixtureLoader.loadClasspath("tt-x-c15-29-two-star-binary.json");
        twoStarBodies = twoStar.toBodies();
        SystemMapFixture withPlanets = SystemMapFixtureLoader.loadClasspath("st-x-c15-294-wide-binary-planets.json");
        wideBinaryPlanetsBodies = withPlanets.toBodies();
        SystemMapFixture innerAb = SystemMapFixtureLoader.loadClasspath("inner-arrival-ab-bare-c-with-planets.json");
        innerAbBareCBodies = innerAb.toBodies();
    }

    private static SystemPlanMapPanel panelFor(Map<Integer, BodyInfo> bodies, String systemName) {
        SystemPlanMapPanel panel = new SystemPlanMapPanel();
        panel.setSize(900, 700);
        Map<Integer, double[]> kepler = SystemOrbitGeometry.bodyPositionsMetres(bodies, Instant.EPOCH, false);
        panel.setScene(bodies, kepler, null, null, null, false, Instant.EPOCH);
        return panel;
    }

    @Test
    void bareCompanionStar_framesBothBarycentricStars() {
        SystemPlanMapPanel panel = panelFor(twoStarBodies, "Byua Aim TT-X c15-29");
        assertTrue(panel.hudTargetAutoZoomBroadContextForTests(1), "bare B star should frame A+B pair");
        Set<Integer> members = panel.hudTargetSubsystemMemberIdsForTests(1);
        assertTrue(members.contains(0));
        assertTrue(members.contains(1));
    }

    @Test
    void branchStarWithPlanets_framesBranchOnly() {
        SystemPlanMapPanel panel = panelFor(wideBinaryPlanetsBodies, "Byua Aim ST-X c15-294");
        assertFalse(panel.hudTargetAutoZoomBroadContextForTests(0),
                "arrival star with A 1 / A 2 should frame A branch only");
        Set<Integer> members = panel.hudTargetSubsystemMemberIdsForTests(0);
        assertTrue(members.contains(0));
        assertTrue(members.contains(2));
        assertTrue(members.contains(3));
        assertFalse(members.contains(1), "B star should not be in A-branch framing");
    }

    @Test
    void planetOnBranch_framesBranchSubtreeNotPair() {
        SystemPlanMapPanel panel = panelFor(wideBinaryPlanetsBodies, "Byua Aim ST-X c15-294");
        assertFalse(panel.hudTargetAutoZoomBroadContextForTests(2), "A 1 should stay on A branch");
        Set<Integer> members = panel.hudTargetSubsystemMemberIdsForTests(4);
        assertTrue(members.contains(1));
        assertTrue(members.contains(4));
        assertFalse(members.contains(2), "A-branch planet should not appear in B 1 framing");
    }

    @Test
    void branchPlanet_includesStarAndPlanetInMembers() {
        SystemPlanMapPanel panel = panelFor(wideBinaryPlanetsBodies, "Byua Aim ST-X c15-294");
        Set<Integer> members = panel.hudTargetSubsystemMemberIdsForTests(2);
        assertTrue(members.size() >= 2, "A 1 framing should include at least star A and planet A 1");
        assertTrue(members.contains(0));
        assertTrue(members.contains(2));
    }

    @Test
    void innerArrivalBareAOrB_framesAbPairNotOuterC() {
        SystemPlanMapPanel panel = panelFor(innerAbBareCBodies, "Inner Arrival AB Bare C Planets");
        for (int starId : new int[] { 2, 3 }) {
            Set<Integer> members = panel.hudTargetSubsystemMemberIdsForTests(starId);
            assertTrue(members.contains(2), "A should be framed");
            assertTrue(members.contains(3), "B should be framed");
            assertFalse(members.contains(4), "outer star C should not be in AB framing");
            assertFalse(members.contains(5), "C 1 should not be in AB framing");
        }
    }

    @Test
    void innerArrivalCWithPlanet_framesCBranchOnly() {
        SystemPlanMapPanel panel = panelFor(innerAbBareCBodies, "Inner Arrival AB Bare C Planets");
        Set<Integer> members = panel.hudTargetSubsystemMemberIdsForTests(4);
        assertTrue(members.contains(4));
        assertTrue(members.contains(5));
        assertFalse(members.contains(2));
        assertFalse(members.contains(3));
    }
}
