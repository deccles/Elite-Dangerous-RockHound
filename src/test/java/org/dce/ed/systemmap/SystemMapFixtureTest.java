package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Fixture-driven regression tests for {@link SystemMapRules} and {@link SystemMapPipeline}.
 * Each JSON file under {@code src/test/resources/systemmap/} documents one real system shape from playtesting.
 */
class SystemMapFixtureTest {

    static Stream<String> fixtureFiles() {
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
                "eor-aowsy-ri-k-c8-3670.json");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtureFiles")
    void fixture_matchesDocumentedRules(String resource) throws IOException {
        SystemMapFixture fixture = SystemMapFixtureLoader.loadClasspath(resource);
        Map<Integer, BodyInfo> bodies = fixture.toBodies();
        SystemMapClassification clf = SystemMapRules.classify(bodies);
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);

        SystemMapFixture.Expect exp = fixture.expect;
        assertNotNull(exp, "fixture must declare expect: " + resource);

        if (exp.layoutKind != null) {
            assertEquals(SystemLayoutKind.valueOf(exp.layoutKind), clf.layoutKind(),
                    "layoutKind — " + fixture.notes);
        }
        if (exp.mapStellarCount != null) {
            assertEquals(exp.mapStellarCount.intValue(), clf.mapStellarCount(), "mapStellarCount");
        }
        if (exp.barycentricStarLabels != null) {
            assertEquals(exp.barycentricStarLabels.size(), clf.barycentricStarIds().size());
            for (String label : exp.barycentricStarLabels) {
                int id = fixture.bodyIdByLabel(label);
                assertTrue(id >= 0, "unknown label in fixture: " + label);
                assertTrue(clf.barycentricStarIds().contains(Integer.valueOf(id)),
                        "expected barycentric star: " + label);
            }
        }
        // True-scale wide-binary maps keep journal Kepler positions; barycentre is not forced to origin.
        if (Boolean.TRUE.equals(exp.hasBarycentreMutualRing)) {
            assertTrue(model.hasBarycentreMutualRing(), "mutual barycentre ring");
        }
        if (exp.planetsRequiringRings != null) {
            for (String label : exp.planetsRequiringRings) {
                int id = fixture.bodyIdByLabel(label);
                assertTrue(id >= 0, label);
                assertTrue(model.syntheticGuideRingCount() > 0 || model.hasOrbitRingForBody(id),
                        "expected a visible orbit ring for " + label + " in " + resource);
            }
        }
        if (exp.parents != null) {
            for (SystemMapFixture.ParentExpect pe : exp.parents) {
                int childId = fixture.bodyIdByLabel(pe.body);
                assertTrue(childId >= 0, pe.body);
                BodyInfo child = bodies.get(Integer.valueOf(childId));
                int pId = SystemMapRules.resolveOrbitParentBodyId(child, bodies, childId);
                if ("barycentre".equalsIgnoreCase(pe.resolvesTo)) {
                    assertTrue(pId < 0, pe.body + " should orbit barycentre, got parent " + pId);
                } else if (pe.resolvesTo != null && pe.resolvesTo.startsWith("planetBinary:")) {
                    int nullId = Integer.parseInt(pe.resolvesTo.substring("planetBinary:".length()));
                    assertTrue(SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(pId),
                            pe.body + " should orbit planet-binary barycentre");
                    assertEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(nullId), pId);
                } else {
                    int expParentId = fixture.bodyIdByLabel(pe.resolvesTo);
                    assertTrue(expParentId >= 0, pe.resolvesTo);
                    assertEquals(expParentId, pId, "parent of " + pe.body);
                }
            }
        }
        if (Boolean.TRUE.equals(exp.hasPlanetBinaryMutualRing) && exp.planetBinaryNullId != null) {
            assertNotNull(OrbitGeometryTestSupport.findPlanetBinaryMutualRing(model, exp.planetBinaryNullId.intValue()),
                    "planet-binary mutual ring Null:" + exp.planetBinaryNullId);
        }
        if (exp.barycentreMinDistanceFromStarLs != null && exp.planetBinaryNullId != null) {
            OrbitGeometryTestSupport.assertBarycentreFarFromStar(model, bodies, exp.planetBinaryNullId.intValue(),
                    exp.barycentreMinDistanceFromStarLs.doubleValue());
        }
        if (exp.bodiesOnMutualRing != null && exp.planetBinaryNullId != null) {
            for (String label : exp.bodiesOnMutualRing) {
                OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(model, bodies, label,
                        exp.planetBinaryNullId.intValue(), 0.3);
            }
        }
        if (exp.bodiesWithoutOwnOrbitRing != null) {
            for (String label : exp.bodiesWithoutOwnOrbitRing) {
                int id = fixture.bodyIdByLabel(label);
                assertTrue(id >= 0, label);
                assertFalse(model.hasOrbitRingForBody(id), label + " uses mutual ring, not per-body stroke");
            }
        }
        if (exp.labelsWhenZoomedOut != null) {
            for (SystemMapFixture.LabelExpect le : exp.labelsWhenZoomedOut) {
                int id = fixture.bodyIdByLabel(le.body);
                assertTrue(id >= 0, le.body);
                BodyInfo b = bodies.get(Integer.valueOf(id));
                boolean star = SystemMapRules.isMapStellarBody(b);
                boolean visible = SystemMapRules.bodyLabelVisibleWhenZoomedOut(b, id, bodies, star, false, false);
                assertEquals(le.visible, visible, "label visibility @ cluster zoom for " + le.body);
            }
        }
    }

  @ParameterizedTest(name = "companion-not-on-primary-{0}")
  @MethodSource("wideBinaryFixtures")
  void wideBinary_companionStar_doesNotOrbitPrimary(String resource) throws IOException {
        SystemMapFixture fixture = SystemMapFixtureLoader.loadClasspath(resource);
        Map<Integer, BodyInfo> bodies = fixture.toBodies();
        SystemMapClassification clf = SystemMapRules.classify(bodies);
        if (!clf.wideBinary()) {
            return;
        }
        for (Integer sid : clf.barycentricStarIds()) {
            if (sid.intValue() == clf.primaryAnchorBodyId()) {
                continue;
            }
            BodyInfo companion = bodies.get(sid);
            int pId = SystemMapRules.resolveOrbitParentBodyId(companion, bodies, sid.intValue());
            assertTrue(pId < 0, "companion star must orbit barycentre, not body " + pId);
        }
    }

    static Stream<String> wideBinaryFixtures() {
        return Stream.of(
                "tt-x-c15-29-two-star-binary.json",
                "two-star-primary-parents-to-companion.json",
                "st-x-c15-294-wide-binary-planets.json",
                "tt-x-c15-283-binary-elw.json");
    }

}
