package org.dce.systemmodel.position;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.dce.systemmodel.build.SystemModelBuilder;
import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanBaryCentreRecord;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.model.HierarchyKeys;
import org.dce.systemmodel.model.OrbitRing;
import org.dce.systemmodel.model.SystemModel;
import org.junit.jupiter.api.Test;

class EolProuOrVD2399RingsTest {

    @Test
    void stellarPairAtNull2_emitsMutualAndHubHeliocentricRings() {
        Instant t = Instant.EPOCH;
        OrbitalElements aOrbit = new OrbitalElements(1.4701533913612365E13, 0, 0, 0, 0, 0, 0, t);
        OrbitalElements hubOrbit = new OrbitalElements(2.1547659039497375E13, 0, 0, 0, 0, 0, 0, t);
        OrbitalElements bOrbit = new OrbitalElements(1.66662949323654175E11, 0, 0, 0, 0, 0, 0, t);
        OrbitalElements cOrbit = new OrbitalElements(5.52619284391403198E11, 0, 0, 0, 0, 0, 0, t);
        int null2 = HierarchyKeys.baryMapKey(2);
        SystemModel model = new SystemModelBuilder()
                .systemName("Eol Prou OR-V d2-399")
                .add(star(t, 1, "A", "K", List.of(new ParentRef(ParentRef.ParentType.NULL, 0)), aOrbit))
                .add(new ScanBaryCentreRecord(
                        t, 2, "barycentre 2",
                        List.of(new ParentRef(ParentRef.ParentType.NULL, 0)),
                        List.of(),
                        hubOrbit))
                .add(star(t, 3, "B", "M", List.of(new ParentRef(ParentRef.ParentType.NULL, 2)), bOrbit))
                .add(star(t, 4, "C", "L", List.of(new ParentRef(ParentRef.ParentType.NULL, 2)), cOrbit))
                .buildPartial();

        List<OrbitRing> rings = model.orbitRingsAt(t);
        int null2Rings = 0;
        boolean hasA = false;
        boolean hasB = false;
        for (OrbitRing ring : rings) {
            if (ring.bodyId() == null2) {
                null2Rings++;
            }
            if (ring.bodyId() == 1) {
                hasA = true;
            }
            if (ring.bodyId() == 3 || ring.bodyId() == 4) {
                hasB = true;
            }
        }
        assertEquals(1, null2Rings, "hub heliocentric ring at Null:2 only");
        assertTrue(hasA, "A ring at Null:0");
        assertTrue(hasB, "B and C each have journal Kepler rings around Null:2");
    }

    private static ScanRecord star(
            Instant t, int id, String letter, String subType, List<ParentRef> parents, OrbitalElements orbit) {
        return new ScanRecord(
                t, id, "Test " + letter, "Star", subType, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                parents, orbit, true, false);
    }
}
