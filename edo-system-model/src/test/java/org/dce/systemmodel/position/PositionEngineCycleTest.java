package org.dce.systemmodel.position;

import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.model.BodyKind;
import org.dce.systemmodel.model.BodyNode;
import org.dce.systemmodel.model.HierarchyGraph;
import org.dce.systemmodel.model.Vec3;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PositionEngineCycleTest {

    @Test
    void cyclicHierarchy_doesNotStackOverflow() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        BodyNode one = new BodyNode(1, "1", BodyKind.PLANET, "Planet", "Rocky", 100,
                null, List.of(), orbit, true);
        BodyNode two = new BodyNode(2, "2", BodyKind.PLANET, "Planet", "Rocky", 200,
                null, List.of(), orbit, true);
        HierarchyGraph cyclic = HierarchyGraph.builder()
                .addEdge(2, 1)
                .addEdge(1, 2)
                .build();
        PositionEngine engine = new PositionEngine(
                Map.of(1, one, 2, two),
                Map.of(),
                cyclic);

        assertDoesNotThrow(() -> {
            Vec3 p1 = engine.positionAt(1, t, false);
            Vec3 p2 = engine.positionAt(2, t, false);
            assertNotNull(p1);
            assertNotNull(p2);
            engine.orbitRingsAt(t);
        });
    }
}
