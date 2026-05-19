package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Nuclear rule: a body named {@code X …} must be modeled on the map at least
 * {@link OrbitGeometryTestSupport#DESIGNATION_BRANCH_MIN_MARGIN_LS} Ls closer to star {@code X} than to any other
 * branch star {@code Y}. Runs on every system-map fixture; corrupt-parent cases only where A/B/C/D all exist.
 */
class AllFixturesDesignationBranchPlacementTest {

    static Stream<String> systemMapFixtures() {
        return Stream.of(
                "tt-x-c15-29-two-star-binary.json",
                "two-star-primary-parents-to-companion.json",
                "st-x-c15-294-wide-binary-planets.json",
                "tt-x-c15-283-binary-elw.json",
                "c16-241-single-k-star.json",
                "sz-g-d10-2113-planet-binary.json",
                "gas-giant-2-binary-moons.json",
                "eol-prou-zh-t-c4-127-body3-moons.json",
                "eol-prou-or-v-d2-399.json",
                "eol-prou-rn-i-c10-276-wide-binary.json",
                "eor-aowsy-ri-k-c8-3670.json");
    }

    private static boolean hasFourBranchStars(Map<Integer, BodyInfo> bodies) {
        Map<String, Integer> stars = OrbitGeometryTestSupport.branchStarsByLetter(bodies);
        return stars.containsKey("A") && stars.containsKey("B") && stars.containsKey("C")
                && stars.containsKey("D");
    }

    @ParameterizedTest(name = "pipeline {0}")
    @MethodSource("systemMapFixtures")
    @DisplayName("SystemMapPipeline.build — branch letter map placement")
    void pipeline_build_branchLetterMapPlacement(String resource) throws IOException {
        SystemMapFixture fixture = SystemMapFixtureLoader.loadClasspath(resource);
        Map<Integer, BodyInfo> bodies = fixture.toBodies();
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);
        OrbitGeometryTestSupport.assertBranchLetterMapPlacement(model, bodies);
    }

    static Stream<Arguments> corruptAbranchToWrongStar() throws IOException {
        Stream.Builder<Arguments> out = Stream.builder();
        for (String resource : systemMapFixtures().toList()) {
            SystemMapFixture fixture = SystemMapFixtureLoader.loadClasspath(resource);
            Map<Integer, BodyInfo> bodies = fixture.toBodies();
            if (!hasFourBranchStars(bodies)) {
                continue;
            }
            List<String> aBranchBodies = new ArrayList<>();
            for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
                if (e.getValue() == null || e.getValue().isScanBarycentreRow()) {
                    continue;
                }
                if (SystemOrbitGeometry.isMapStellarBody(e.getValue())) {
                    continue;
                }
                String branch = SystemOrbitGeometry.designationBranchLetter(e.getValue());
                if (!"A".equalsIgnoreCase(branch)) {
                    continue;
                }
                aBranchBodies.add(e.getValue().getShortName());
            }
            for (String label : aBranchBodies) {
                for (String wrongStar : List.of("B", "C", "D")) {
                    out.add(Arguments.of(resource, label, wrongStar));
                }
            }
        }
        return out.build();
    }

    @ParameterizedTest(name = "{0}: {1} cache-parent → star {2}")
    @MethodSource("corruptAbranchToWrongStar")
    @DisplayName("A-branch corrupt cache parent — must still map on star A")
    void pipeline_aBranchCorruptParent_stillOnStarA(String resource, String bodyLabel, String wrongStarLetter)
            throws IOException {
        SystemMapFixture fixture = SystemMapFixtureLoader.loadClasspath(resource);
        Map<Integer, BodyInfo> bodies = new HashMap<>(fixture.toBodies());
        int wrongStarId = fixture.bodyIdByLabel(wrongStarLetter);
        int bodyId = fixture.bodyIdByLabel(bodyLabel);
        assertTrue(wrongStarId >= 0);
        assertTrue(bodyId >= 0);
        bodies.get(Integer.valueOf(bodyId)).setImmediateParentBodyId(wrongStarId);
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);
        OrbitGeometryTestSupport.assertBranchLetterMapPlacement(model, bodies);
    }

    @ParameterizedTest(name = "entire A-branch → C {0}")
    @MethodSource("systemMapFixtures")
    @DisplayName("All A-designation bodies cache-parented to C — still map on A")
    void pipeline_entireAbranchParentedToC(String resource) throws IOException {
        SystemMapFixture fixture = SystemMapFixtureLoader.loadClasspath(resource);
        Map<Integer, BodyInfo> bodies = new HashMap<>(fixture.toBodies());
        if (!hasFourBranchStars(bodies)) {
            return;
        }
        int idC = OrbitGeometryTestSupport.branchStarsByLetter(bodies).get("C").intValue();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getValue() == null || e.getValue().isScanBarycentreRow()) {
                continue;
            }
            String branch = SystemOrbitGeometry.designationBranchLetter(e.getValue());
            if (branch != null && "A".equalsIgnoreCase(branch)) {
                e.getValue().setImmediateParentBodyId(idC);
            }
        }
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);
        OrbitGeometryTestSupport.assertBranchLetterMapPlacement(model, bodies);
    }
}
