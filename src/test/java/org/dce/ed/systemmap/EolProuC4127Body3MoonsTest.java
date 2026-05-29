package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.Test;

/**
 * Regression for {@code Eol Prou ZH-T c4-127} body 3 moons (journal 2026-05-19): gas-giant satellites must sit on
 * schematic rings around the host, not at raw Kepler SMA which scatters 3 d / 3 e / 3 f far from the inner cluster.
 */
class EolProuC4127Body3MoonsTest {

    @Test
    void singleStarMap_body3Moons_onSchematicRingsNearHost() throws IOException {
        SystemMapFixture fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-zh-t-c4-127-body3-moons.json");
        Map<Integer, BodyInfo> bodies = fixture.toBodies();
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        int id3 = fixture.bodyIdByLabel("3");
        for (String moon : List.of("3 a", "3 b", "3 c", "3 d", "3 e", "3 f")) {
            int mid = fixture.bodyIdByLabel(moon);
            double sep = Math.hypot(model.mapPlaneX(mid) - model.mapPlaneX(id3),
                    model.mapPlaneY(mid) - model.mapPlaneY(id3)) / ls;
            double hint = Math.abs(bodies.get(mid).getDistanceLs() - bodies.get(id3).getDistanceLs());
            assertTrue(sep < 30.0,
                    moon + " should stay near host 3; sep=" + sep + " Ls journalHint=" + hint + " Ls");
            assertTrue(Math.abs(sep - hint) <= Math.max(5.0, hint * 5.0),
                    moon + " map separation should follow journal parent-relative distance at true scale");
            OrbitGeometryTestSupport.assertBodyOnPerBodyOrbitRing(model, bodies, moon, 12.0);
        }
        assertTrue(model.subsystemHubBodyIds().contains(Integer.valueOf(id3)),
                "gas giant 3 is a moon-host subsystem hub");
    }
}
