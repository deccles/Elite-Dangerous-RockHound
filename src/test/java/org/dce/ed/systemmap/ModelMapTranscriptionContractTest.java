package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.dce.systemmodel.model.HierarchyGraph;
import org.dce.systemmodel.model.SystemModel;
import org.junit.jupiter.api.Test;

/** Model hierarchy parent edges must match transcriber resolved parents. */
class ModelMapTranscriptionContractTest {

    @Test
    void eolProuNnYB310_moonBinary_parentsMatchHierarchy() throws IOException {
        SystemMapFixture fx = SystemMapFixtureLoader.loadClasspath("eol-prou-nn-y-b31-0-7-moons.json");
        SystemSession session = SystemTopologyParity.openSession(fx);
        assertTrue(session.hasModel());

        SystemModel model = session.model();
        Map<Integer, Integer> resolved = ModelMapTranscriber.hierarchyResolvedParents(model, fx.toBodies());
        HierarchyGraph hg = model.hierarchy();

        for (var e : hg.parentByChild().entrySet()) {
            int child = e.getKey();
            int parent = e.getValue();
            if (child < 0) {
                Integer mapParent = resolved.get(child);
                if (mapParent != null) {
                    assertEquals(parent, mapParent.intValue(), "hub " + child);
                }
            } else if (fx.toBodies().containsKey(child)) {
                assertEquals(parent, resolved.getOrDefault(child, -1).intValue(),
                        "body " + child);
            }
        }
    }

    @Test
    void transcriberBuild_producesPositionsAndRings() throws IOException {
        SystemMapFixture fx = SystemMapFixtureLoader.loadClasspath("eol-prou-nn-y-b31-0-7-moons.json");
        SystemSession session = SystemTopologyParity.openSession(fx);
        SystemMapModel model = SystemMapPipeline.build(fx.name, fx.toBodies(), Instant.EPOCH, false, session);
        assertNotNull(model);
        assertTrue(!model.positionsMetres().isEmpty(), "positions from model");
    }
}
