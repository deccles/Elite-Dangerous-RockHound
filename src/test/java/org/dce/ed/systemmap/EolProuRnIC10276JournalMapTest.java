package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Live journal replay for {@code Eol Prou RN-I c10-276}: wide binary A+B, A 3 gas giant with six moons.
 */
class EolProuRnIC10276JournalMapTest {

    private static final String SYSTEM = "Eol Prou RN-I c10-276";
    private static final double LS = SystemOrbitGeometry.LIGHT_SECOND_METRES;

    private static SystemState state;
    private static Map<Integer, BodyInfo> bodies;
    private static SystemMapModel model;
    private static int idA3;
    private static int idB;

    @BeforeAll
    static void loadJournal() throws IOException {
        Path journalDir = JournalSystemMapLoader.defaultJournalDirectory();
        assumeTrue(Files.isDirectory(journalDir), "Elite journal directory not found");
        state = JournalSystemMapLoader.loadFromJournal(journalDir, SYSTEM);
        assumeTrue(state.getBodies().size() >= 15, "need FSS scans in journal for " + SYSTEM);
        bodies = state.getBodies();
        model = SystemMapPipeline.build(SYSTEM, bodies, Instant.EPOCH, true);
        idA3 = OrbitGeometryTestSupport.findByShortName(bodies, "A 3");
        idB = OrbitGeometryTestSupport.findByShortName(bodies, "B");
        assumeTrue(idA3 >= 0 && idB >= 0);
    }

    @Test
    void journalMoons_parentToA3() {
        for (String moon : List.of("A 3 a", "A 3 b", "A 3 c", "A 3 d", "A 3 e", "A 3 f")) {
            int mid = OrbitGeometryTestSupport.findByShortName(bodies, moon);
            assumeTrue(mid >= 0, moon);
            assertEquals(idA3, model.resolveParentBodyId(mid), moon);
        }
    }

    @Test
    void journalMoons_nearA3_notCompanionStarB() {
        for (String moon : List.of("A 3 a", "A 3 b", "A 3 c", "A 3 d", "A 3 e", "A 3 f")) {
            int mid = OrbitGeometryTestSupport.findByShortName(bodies, moon);
            double sepA3 = Math.hypot(model.mapPlaneX(mid) - model.mapPlaneX(idA3),
                    model.mapPlaneY(mid) - model.mapPlaneY(idA3)) / LS;
            double sepB = Math.hypot(model.mapPlaneX(mid) - model.mapPlaneX(idB),
                    model.mapPlaneY(mid) - model.mapPlaneY(idB)) / LS;
            assertTrue(sepA3 < 25.0, moon + " near A 3; sep=" + sepA3 + " Ls");
            assertTrue(sepB > 500.0, moon + " must not sit on B; sepB=" + sepB + " Ls");
        }
    }

    @Test
    void playbackRefresh_keepsMoonsOnA3() {
        Map<Integer, double[]> kepler = SystemOrbitGeometry.bodyPositionsMetres(bodies, Instant.EPOCH, true);
        Map<Integer, double[]> after = SystemMapPipeline.refreshPositionsForPlayback(model, kepler, Instant.EPOCH, true);
        SystemMapModel playbackModel = SystemMapPipeline.playbackBase(bodies, model.projectionAxis0(),
                model.projectionAxis1(), after, model.wideBinaryFlattenFrame());
        for (String moon : List.of("A 3 a", "A 3 e", "A 3 f")) {
            int mid = OrbitGeometryTestSupport.findByShortName(bodies, moon);
            double sepA3 = Math.hypot(playbackModel.mapPlaneX(mid) - playbackModel.mapPlaneX(idA3),
                    playbackModel.mapPlaneY(mid) - playbackModel.mapPlaneY(idA3)) / LS;
            assertTrue(sepA3 < 25.0, "playback " + moon + " near A 3; sep=" + sepA3 + " Ls");
        }
    }

    @Test
    void notHierarchicalWideBinary() {
        assertFalse(SystemOrbitGeometry.isHierarchicalWideBinary(bodies));
        assertTrue(model.classification().wideBinary());
    }
}
