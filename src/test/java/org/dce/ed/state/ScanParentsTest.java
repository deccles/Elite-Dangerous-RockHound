package org.dce.ed.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.logreader.event.ScanEvent.ParentRef;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class ScanParentsTest {

    @Test
    void nonStellar_prefersPlanetWhenStarListedFirst() {
        ScanEvent scan = minimalPlanetScan(List.of(new ParentRef("Star", 0), new ParentRef("Planet", 1)));
        assertEquals(1, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    @Test
    void nonStellar_prefersNullBarycentreOverPlanetHost() {
        ScanEvent scan = minimalPlanetScan(List.of(
                new ParentRef("Null", 12),
                new ParentRef("Planet", 10),
                new ParentRef("Star", 0)));
        assertEquals(12, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    @Test
    void nonStellar_prefersNullBarycentreWhenStarListedSecond() {
        ScanEvent scan = minimalPlanetScan(List.of(new ParentRef("Null", 13), new ParentRef("Star", 0)));
        assertEquals(13, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    @Test
    void nonStellar_prefersNullBarycentreWhenStarListedFirst() {
        ScanEvent scan = minimalPlanetScan(List.of(new ParentRef("Star", 0), new ParentRef("Null", 13)));
        assertEquals(13, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    @Test
    void nonStellar_planetOnly() {
        ScanEvent scan = minimalPlanetScan(List.of(new ParentRef("Planet", 2)));
        assertEquals(2, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    @Test
    void stellar_usesFirstEntryEvenIfPlanetWouldBeSecond() {
        ScanEvent scan = minimalStarScan(List.of(new ParentRef("Null", 0)));
        assertEquals(0, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    @Test
    void scanIndicatesStellarBody_planetClassWins() {
        ScanEvent scan = minimalPlanetScan(Collections.emptyList());
        assertFalse(ScanParents.scanIndicatesStellarBody(scan));
    }

    @Test
    void scanIndicatesStellarBody_starTypeOnly() {
        ScanEvent scan = minimalStarScan(List.of(new ParentRef("Null", 0)));
        assertTrue(ScanParents.scanIndicatesStellarBody(scan));
    }

    @Test
    void nonStellar_nullOnlyBeforeStar() {
        ScanEvent scan = minimalPlanetScan(List.of(new ParentRef("Null", 25), new ParentRef("Star", 0)));
        assertEquals(25, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    @Test
    void nonStellar_planetBeforeNull_prefersMoonHost() {
        ScanEvent scan = minimalPlanetScan(List.of(
                new ParentRef("Planet", 50),
                new ParentRef("Null", 49),
                new ParentRef("Null", 2),
                new ParentRef("Star", 0)));
        assertEquals(50, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    /** Eor Aowsy RI-K c8-3670 BCD 2 a: moon host before planet-binary Null:49 in Parents chain. */
    @Test
    void eorAowsy_bcd2Moon_planetBeforeNull49() {
        ScanEvent scan = minimalPlanetScan(List.of(
                new ParentRef("Planet", 50),
                new ParentRef("Null", 49),
                new ParentRef("Null", 2),
                new ParentRef("Star", 0)));
        assertEquals(50, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    /** Co-orbit moon: journal lists Null:N before Planet host — still parent to the gas giant. */
    @Test
    void nonStellar_coOrbitMoon_prefersPlanetOverNull() {
        ScanEvent scan = minimalPlanetScan("Wide A 3 e",
                List.of(new ParentRef("Null", 15), new ParentRef("Planet", 9), new ParentRef("Star", 1)));
        assertTrue(ScanParents.scanIndicatesMoonBody(scan));
        assertEquals(9, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    @Test
    void nonStellar_coOrbitPlanetBinaryMajor_stillPrefersNull() {
        ScanEvent scan = minimalPlanetScan("Wide 1 b",
                List.of(new ParentRef("Null", 12), new ParentRef("Planet", 10), new ParentRef("Star", 0)));
        assertFalse(ScanParents.scanIndicatesMoonBody(scan));
        assertEquals(12, ScanParents.immediateOrbitParentBodyId(scan.getParents(), scan));
    }

    private static ScanEvent minimalPlanetScan(List<ParentRef> parents) {
        return minimalPlanetScan("Sys 2", parents);
    }

    private static ScanEvent minimalPlanetScan(String bodyName, List<ParentRef> parents) {
        return new ScanEvent(
                Instant.EPOCH,
                new JsonObject(),
                bodyName,
                2,
                "Sys",
                0L,
                1000.0,
                false,
                "Class I gas giant",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Collections.emptyMap(),
                null,
                parents,
                Collections.emptyList(),
                null,
                null);
    }

    private static ScanEvent minimalStarScan(List<ParentRef> parents) {
        return new ScanEvent(
                Instant.EPOCH,
                new JsonObject(),
                "Sys",
                0,
                "Sys",
                0L,
                0.0,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Collections.emptyMap(),
                "K",
                parents,
                Collections.emptyList(),
                null,
                null);
    }
}
