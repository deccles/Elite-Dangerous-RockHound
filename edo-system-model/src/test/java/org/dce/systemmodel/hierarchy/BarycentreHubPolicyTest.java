package org.dce.systemmodel.hierarchy;

import org.dce.systemmodel.build.SystemModelBuilder;
import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanBaryCentreRecord;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.model.SystemModel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarycentreHubPolicyTest {

    @Test
    void emptyBarycentre_notAHub() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        SystemModel model = new SystemModelBuilder()
                .systemName("Test")
                .add(star(t))
                .add(baryOnly(t, 99))
                .buildPartial();
        assertFalse(model.barycentres().containsKey(99));
    }

    @Test
    void singleMoonUnderBary_isTreeParent() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        SystemModel model = new SystemModelBuilder()
                .systemName("Test")
                .add(star(t))
                .add(planet(t, 7, orbit))
                .add(moon(t, 33, 32, 7, orbit))
                .add(baryOnly(t, 32))
                .buildPartial();
        assertTrue(BarycentreHubPolicy.showAsHierarchyHub(model, 32));
        assertFalse(BarycentreHubPolicy.isPlanetBinaryHub(model, 32));
    }

    @Test
    void planetBinary_withTwoMoons_isHub() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, t);
        SystemModel model = new SystemModelBuilder()
                .systemName("Test")
                .add(star(t))
                .add(planet(t, 7, orbit))
                .add(moon(t, 33, 32, 7, orbit))
                .add(moon(t, 34, 32, 7, orbit))
                .add(baryOnly(t, 32))
                .buildPartial();
        assertTrue(BarycentreHubPolicy.showAsHierarchyHub(model, 32));
        assertTrue(BarycentreHubPolicy.isPlanetBinaryHub(model, 32));
    }

    private static ScanRecord star(Instant t) {
        return scan(0, "Test", "Star", "M", 0, List.of(), null, t);
    }

    private static ScanRecord planet(Instant t, int id, OrbitalElements orbit) {
        return scan(id, "Test " + id, "Planet", "Rocky", 100,
                List.of(new ParentRef(ParentRef.ParentType.STAR, 0)), orbit, t);
    }

    private static ScanRecord moon(Instant t, int id, int nullId, int planetId, OrbitalElements orbit) {
        return scan(id, "Test " + planetId + " d", "Planet", "Icy", 100,
                List.of(
                        new ParentRef(ParentRef.ParentType.NULL, nullId),
                        new ParentRef(ParentRef.ParentType.PLANET, planetId),
                        new ParentRef(ParentRef.ParentType.STAR, 0)),
                orbit, t);
    }

    private static ScanBaryCentreRecord baryOnly(Instant t, int id) {
        return new ScanBaryCentreRecord(
                t, id, "bary " + id, List.of(), List.of(),
                new OrbitalElements(1e11, 0, 0, 0, 0, 0, 100_000, t));
    }

    private static ScanRecord scan(
            int id, String name, String bodyType, String subType, double distLs,
            List<ParentRef> parents, OrbitalElements orbit, Instant t) {
        return new ScanRecord(
                t, id, name, bodyType, subType, distLs,
                0, 0, 0, 0, 0, 0, 0, 0,
                parents, orbit, true, false);
    }
}
