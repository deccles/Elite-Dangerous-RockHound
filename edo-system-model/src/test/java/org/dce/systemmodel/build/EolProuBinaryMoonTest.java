package org.dce.systemmodel.build;

import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanBaryCentreRecord;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.model.BodyNode;
import org.dce.systemmodel.model.SystemModel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EolProuBinaryMoonTest {

    @Test
    void binaryMoonsOrbitNullBarycentreNotPlanet() {
        Instant t = Instant.parse("2026-05-27T12:00:00Z");
        OrbitalElements orbit = new OrbitalElements(
                5e8, 0.01, 0.1, 0.2, 0.3, 0.4, 50_000, t);

        SystemModel model = new SystemModelBuilder()
                .systemName("Eol Prou NN-Y b31-0")
                .add(star())
                .add(giant7())
                .add(moon7a())
                .add(moon7d())
                .add(moon7e())
                .add(bary32())
                .build();

        BodyNode d = model.body(33).orElseThrow();
        BodyNode e = model.body(34).orElseThrow();
        assertEquals(ParentRef.ParentType.NULL, d.orbitParent().type());
        assertEquals(32, d.orbitParent().bodyId());
        assertEquals(ParentRef.ParentType.NULL, e.orbitParent().type());
        assertEquals(32, e.orbitParent().bodyId());
        assertTrue(d.definitive());
        assertTrue(e.definitive());
        var bc = model.barycentre(32).orElseThrow();
        assertEquals(ParentRef.ParentType.PLANET, bc.orbitParent().type());
        assertEquals(28, bc.orbitParent().bodyId());
    }

    @Test
    void stellarBaryWithoutExplicitParents_derivesFromMemberChains() {
        SystemModelBuilder builder = new SystemModelBuilder()
                .systemName("Stellar pair")
                .add(star())
                .add(starB())
                .add(starC())
                .add(stellarBary16());
        assertFalse(builder.incompleteReasons().stream().anyMatch(r -> r.contains("barycentre 16")));
        var bc = builder.buildPartial().barycentre(16).orElseThrow();
        assertEquals(ParentRef.ParentType.STAR, bc.orbitParent().type());
        assertEquals(0, bc.orbitParent().bodyId());
    }

    @Test
    void incompleteUntilBarycentreArrives() {
        SystemModel partial = new SystemModelBuilder()
                .systemName("Eol Prou NN-Y b31-0")
                .add(giant7())
                .add(moon7d())
                .buildPartial();

        BodyNode d = partial.body(33).orElseThrow();
        assertEquals(false, d.definitive());
    }

    private static ScanRecord star() {
        return scan(0, "Eol Prou NN-Y b31-0", "Star", "M", 0, List.of(), null);
    }

    private static ScanRecord giant7() {
        return scan(28, "Eol Prou NN-Y b31-0 7", "Planet", "Sudarsky class I gas giant", 1410.41,
                List.of(new ParentRef(ParentRef.ParentType.STAR, 0)), orbit());
    }

    private static ScanRecord moon7a() {
        return scan(29, "Eol Prou NN-Y b31-0 7 a", "Planet", "Icy body", 1408.95,
                List.of(
                        new ParentRef(ParentRef.ParentType.PLANET, 28),
                        new ParentRef(ParentRef.ParentType.STAR, 0)),
                orbit());
    }

    private static ScanRecord moon7d() {
        return scan(33, "Eol Prou NN-Y b31-0 7 d", "Planet", "Icy body", 1403.67,
                List.of(
                        new ParentRef(ParentRef.ParentType.NULL, 32),
                        new ParentRef(ParentRef.ParentType.PLANET, 28),
                        new ParentRef(ParentRef.ParentType.STAR, 0)),
                orbit());
    }

    private static ScanRecord moon7e() {
        return scan(34, "Eol Prou NN-Y b31-0 7 e", "Planet", "Icy body", 1403.66,
                List.of(
                        new ParentRef(ParentRef.ParentType.NULL, 32),
                        new ParentRef(ParentRef.ParentType.PLANET, 28),
                        new ParentRef(ParentRef.ParentType.STAR, 0)),
                orbit());
    }

    private static ScanRecord scan(
            int id, String name, String bodyType, String subType, double distLs,
            List<ParentRef> parents, OrbitalElements orbit) {
        return new ScanRecord(
                Instant.EPOCH, id, name, bodyType, subType, distLs,
                0, 0, 0, 0, 0, 0, 0, 0,
                parents, orbit, false, false);
    }

    private static ScanBaryCentreRecord bary32() {
        return new ScanBaryCentreRecord(
                Instant.EPOCH, 32, "Eol Prou NN-Y b31-0 barycentre 32",
                List.of(new ParentRef(ParentRef.ParentType.PLANET, 28)),
                List.of(),
                orbit());
    }

    private static ScanRecord starB() {
        return scan(10, "Test B", "Star", "M", 50000,
                List.of(
                        new ParentRef(ParentRef.ParentType.NULL, 16),
                        new ParentRef(ParentRef.ParentType.STAR, 0)),
                orbit());
    }

    private static ScanRecord starC() {
        return scan(11, "Test C", "Star", "M", 50001,
                List.of(
                        new ParentRef(ParentRef.ParentType.NULL, 16),
                        new ParentRef(ParentRef.ParentType.STAR, 0)),
                orbit());
    }

    private static ScanBaryCentreRecord stellarBary16() {
        return new ScanBaryCentreRecord(
                Instant.EPOCH, 16, "Test barycentre 16",
                List.of(),
                List.of(),
                orbit());
    }

    private static OrbitalElements orbit() {
        return new OrbitalElements(1e9, 0, 0, 0, 0, 0, 100_000, Instant.EPOCH);
    }
}
