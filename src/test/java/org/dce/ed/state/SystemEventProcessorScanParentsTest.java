package org.dce.ed.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.ScanEvent.ParentRef;
import org.dce.ed.testutil.ScanEventFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

/** End-to-end: journal Scan → {@link BodyInfo#getImmediateParentBodyId()} via {@link ScanParents}. */
class SystemEventProcessorScanParentsTest {

    private static final String SYS = "Byua Aim SZ-G d10-2113";
    private static final long ADDR = 42424242L;

    private SystemState state;
    private SystemEventProcessor processor;

    @BeforeEach
    void setUp() {
        state = new SystemState();
        processor = new SystemEventProcessor("test", state);
        processor.handleEvent(new FsdJumpEvent(
                java.time.Instant.EPOCH,
                new JsonObject(),
                SYS,
                ADDR,
                new double[] { 0, 0, 0 },
                SYS,
                0,
                "Star",
                0.0,
                0.0,
                32.0,
                null));
    }

    @Test
    void scan_planetBinaryCoOrbiter_parentsNullBeforePlanet() {
        processor.handleEvent(ScanEventFixtures.planetScan(
                13,
                SYS + " 1 b",
                SYS,
                ADDR,
                2250.79,
                "Icy body",
                List.of(
                        new ParentRef("Null", 12),
                        new ParentRef("Planet", 10),
                        new ParentRef("Star", 0))));
        BodyInfo body = state.getBodies().get(Integer.valueOf(13));
        assertEquals(12, body.getImmediateParentBodyId());
    }

    @Test
    void scan_regularMoon_parentsPlanetOnly() {
        processor.handleEvent(ScanEventFixtures.planetScan(
                21,
                SYS + " 2 a",
                SYS,
                ADDR,
                3130.0,
                "Icy body",
                List.of(new ParentRef("Planet", 20), new ParentRef("Star", 0))));
        BodyInfo body = state.getBodies().get(Integer.valueOf(21));
        assertEquals(20, body.getImmediateParentBodyId());
    }

    @Test
    void scan_moonOfMoon_threePartName_stillPlanetParent() {
        processor.handleEvent(ScanEventFixtures.planetScan(
                30,
                SYS + " 1 e a",
                SYS,
                ADDR,
                2251.0,
                "Icy body",
                List.of(new ParentRef("Planet", 17), new ParentRef("Planet", 10), new ParentRef("Star", 0))));
        assertEquals(17, state.getBodies().get(Integer.valueOf(30)).getImmediateParentBodyId());
    }

    @Test
    void scanBaryCentre_thenPair_bothResolveToMutualBarycentreOnMap() {
        processor.handleEvent(ScanEventFixtures.planetScan(
                21, SYS + " 2 a", SYS, ADDR, 3130.0, "Icy body",
                List.of(new ParentRef("Null", 25), new ParentRef("Planet", 20), new ParentRef("Star", 0))));
        processor.handleEvent(ScanEventFixtures.planetScan(
                22, SYS + " 2 b", SYS, ADDR, 3127.0, "Icy body",
                List.of(new ParentRef("Null", 25), new ParentRef("Planet", 20), new ParentRef("Star", 0))));

        Map<Integer, BodyInfo> bodies = state.getBodies();
        int pA = 21;
        int pB = 22;
        int parentA = org.dce.ed.util.SystemOrbitGeometry.resolveOrbitParentBodyId(
                bodies.get(Integer.valueOf(pA)), bodies, pA);
        int parentB = org.dce.ed.util.SystemOrbitGeometry.resolveOrbitParentBodyId(
                bodies.get(Integer.valueOf(pB)), bodies, pB);
        assertTrue(org.dce.ed.util.SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(parentA));
        assertEquals(parentA, parentB);
        assertFalse(org.dce.ed.util.SystemOrbitGeometry.isMoonSatelliteBody(bodies.get(Integer.valueOf(pA)), bodies));
    }
}
