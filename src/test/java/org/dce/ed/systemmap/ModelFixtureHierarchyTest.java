package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.stream.Stream;

import org.dce.systemmodel.model.HierarchyGraph;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Fixture journal → {@link org.dce.systemmodel.model.SystemModel#hierarchy()} contract. */
class ModelFixtureHierarchyTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixturesWithParents")
    void hierarchyParent_matchesExpect(String fixtureName, String bodyLabel, int expectedParentKey)
            throws IOException {
        SystemMapFixture fx = SystemMapFixtureLoader.loadClasspath(fixtureName);
        SystemSession session = SystemTopologyParity.openSession(fx);
        assertTrue(session.hasModel(), fixtureName);

        int bodyId = fx.bodyIdByLabel(bodyLabel);
        HierarchyGraph hg = session.model().hierarchy();
        Integer parent = hg.parentOf(bodyId);
        int actual = parent != null ? parent.intValue() : -1;
        assertEquals(expectedParentKey, actual, bodyLabel + " parent in " + fixtureName);
    }

    static Stream<Arguments> fixturesWithParents() {
        return Stream.of(
                Arguments.of("eol-prou-nn-y-b31-0-7-moons.json", "7 d",
                        org.dce.ed.util.SystemOrbitGeometry.planetBinaryBarycentreMapKey(32)),
                Arguments.of("eol-prou-nn-y-b31-0-7-moons.json", "7 e",
                        org.dce.ed.util.SystemOrbitGeometry.planetBinaryBarycentreMapKey(32)));
    }
}
