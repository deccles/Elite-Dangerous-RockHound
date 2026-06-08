package org.dce.systemmodel.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.model.SystemModel;
import org.junit.jupiter.api.Test;

/** Eol Prou LS-T e3-1015: arrival A star is BodyID 0 with no {@code Parents[]} in journal. */
class EolProuLsTe31015ArrivalStarTest {

    @Test
    void arrivalStarWithoutParents_isSystemRootNotIncomplete() {
        Instant t = Instant.parse("2026-06-08T16:34:00Z");
        SystemModelBuilder builder = new SystemModelBuilder()
                .systemName("Eol Prou LS-T e3-1015")
                .add(arrivalStar(t))
                .add(planet(t, 11, List.of(new org.dce.systemmodel.journal.ParentRef(
                        org.dce.systemmodel.journal.ParentRef.ParentType.STAR, 0))));

        assertTrue(builder.incompleteReasons().isEmpty());
        SystemModel model = builder.build();
        var star = model.body(0).orElseThrow();
        assertNull(star.orbitParent());
        assertTrue(star.definitive());
        assertEquals(0.0, model.positionAt(0, t).x());
    }

    private static ScanRecord arrivalStar(Instant t) {
        return new ScanRecord(
                t, 0, "Eol Prou LS-T e3-1015", "Star", "A", 0,
                2.082031, 0, 8955, 0, 0, 0, 0, 0,
                List.of(), null, true, false);
    }

    private static ScanRecord planet(Instant t, int id, List<org.dce.systemmodel.journal.ParentRef> parents) {
        return new ScanRecord(
                t, id, "Eol Prou LS-T e3-1015 " + id, "Planet", "Sudarsky class III gas giant", 1188,
                107, 0, 322, 0, 0, 0, 0, 0,
                parents, null, true, false);
    }
}
