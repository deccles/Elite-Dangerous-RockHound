package org.dce.systemmodel.model;

import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.build.SystemModelBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemModelPositionApiTest {

    @Test
    void positionsAtAndReferenceEpoch() {
        Instant epoch = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e11, 0, 0, 0, 0, 0, 100_000, epoch);
        var model = new SystemModelBuilder()
                .systemName("Test")
                .add(new ScanRecord(epoch, 0, "Test", "Star", "M", 0,
                        0, 0, 0, 0, 0, 0, 0, 0, List.of(), null, false, false))
                .add(new ScanRecord(epoch, 1, "Test 1", "Planet", "Rocky", 100,
                        0, 0, 0, 0, 0, 0, 0, 0,
                        List.of(new ParentRef(ParentRef.ParentType.STAR, 0)), orbit, false, false))
                .build();
        assertEquals(epoch, model.referenceEpoch());
        Map<Integer, Position3d> all = model.positionsAt(epoch);
        assertTrue(all.containsKey(1));
        assertTrue(all.get(1).length() >= 0);

        Position3d moonPos = model.positionAt(1, epoch);
        var rings = model.orbitRingsAt(epoch);
        assertTrue(rings.stream().anyMatch(r -> r.bodyId() == 1));
        OrbitRing moonRing = rings.stream().filter(r -> r.bodyId() == 1).findFirst().orElseThrow();
        assertTrue(moonRing.pointsMetres().size() >= 8);
        double minDist = Double.POSITIVE_INFINITY;
        for (double[] pt : moonRing.pointsMetres()) {
            double dx = pt[0] - moonPos.x();
            double dy = pt[1] - moonPos.y();
            double dz = pt[2] - moonPos.z();
            minDist = Math.min(minDist, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        assertTrue(minDist < moonPos.length() * 0.05 + 1e6,
                "moon should lie near its Kepler ring stroke");
    }
}
