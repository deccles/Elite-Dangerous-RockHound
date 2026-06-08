package org.dce.ed.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.logreader.event.ScanEvent.ParentRef;
import org.dce.ed.testutil.ScanEventFixtures;
import org.junit.jupiter.api.Test;

/** M-0: {@code parents[0]} is immediate orbit parent. */
class ScanParentsTest {

    private static final String SYS = "Test System";
    private static final long ADDR = 1L;

    @Test
    void parents0_isImmediateParent_planet() {
        ScanEvent scan = planet(List.of(new ParentRef("Star", 0), new ParentRef("Planet", 1)));
        assertEquals(0, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    @Test
    void parents0_null() {
        ScanEvent scan = planet(List.of(
                new ParentRef("Null", 12),
                new ParentRef("Planet", 10),
                new ParentRef("Star", 0)));
        assertEquals(12, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    @Test
    void parents0_starBeforeNull() {
        ScanEvent scan = planet(List.of(new ParentRef("Star", 0), new ParentRef("Null", 13)));
        assertEquals(0, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    @Test
    void parents0_planetOnly() {
        ScanEvent scan = planet(List.of(new ParentRef("Planet", 2)));
        assertEquals(2, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    @Test
    void stellar_usesFirstEntry() {
        ScanEvent scan = star(List.of(new ParentRef("Null", 0)));
        assertEquals(0, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    @Test
    void scanIndicatesStellarBody_planetClassWins() {
        ScanEvent scan = planet(Collections.emptyList());
        assertFalse(ScanParents.scanIndicatesStellarBody(scan));
    }

    @Test
    void scanIndicatesStellarBody_starTypeOnly() {
        ScanEvent scan = star(List.of(new ParentRef("Null", 0)));
        assertTrue(ScanParents.scanIndicatesStellarBody(scan));
    }

    @Test
    void eolProu_b310_7d_parents0IsNull32() {
        ScanEvent scan = ScanEventFixtures.planetScan(33, "Eol Prou NN-Y b31-0 7 d", SYS, ADDR, 1000,
                "Icy body",
                List.of(new ParentRef("Null", 32), new ParentRef("Planet", 28), new ParentRef("Star", 0)));
        assertTrue(ScanParents.scanIndicatesMoonBody(scan));
        assertEquals(32, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    @Test
    void coOrbitMoon_parents0IsNull() {
        ScanEvent scan = ScanEventFixtures.planetScan(2, "Wide A 3 e", SYS, ADDR, 1000,
                "Icy body",
                List.of(new ParentRef("Null", 15), new ParentRef("Planet", 9), new ParentRef("Star", 1)));
        assertTrue(ScanParents.scanIndicatesMoonBody(scan));
        assertEquals(15, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    @Test
    void emptyParents_returnsMinusOne() {
        ScanEvent scan = planet(Collections.emptyList());
        assertEquals(-1, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    private static ScanEvent planet(List<ParentRef> parents) {
        return ScanEventFixtures.planetScan(2, "Sys 2", SYS, ADDR, 1000, "Class I gas giant", parents);
    }

    private static ScanEvent star(List<ParentRef> parents) {
        return ScanEventFixtures.starScan(0, SYS, SYS, ADDR, "K", parents);
    }
}
