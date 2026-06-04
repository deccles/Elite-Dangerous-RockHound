package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.junit.jupiter.api.Test;

/**
 * Regression for {@code Eol Prou RL-Z c15-127}: lone star + one distant gas giant must not draw Kepler spokes
 * through the star from barycentric FSS elements.
 */
class EolProuRlZc15127OrbitMapTest {

    @Test
    void singleStar_gasGiantOrbit_doesNotSpikeThroughStar() throws IOException {
        SystemMapFixture fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-rl-z-c15-127.json");
        Map<Integer, BodyInfo> bodies = fixture.toBodies();
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);
        int starId = fixture.bodyIdByLabel("Eol Prou RL-Z c15-127");
        int planetId = fixture.bodyIdByLabel("1");
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        double starX = model.mapPlaneX(starId);
        double starY = model.mapPlaneY(starId);
        double planetX = model.mapPlaneX(planetId);
        double planetY = model.mapPlaneY(planetId);
        double sepLs = Math.hypot(planetX - starX, planetY - starY) / ls;
        assertTrue(sepLs > 15_000.0, "planet should be far from star; sepLs=" + sepLs);

        List<OrbitPolylineWorldXY> polys = model.orbitPolylines();
        OrbitPolylineWorldXY planetRing = null;
        for (OrbitPolylineWorldXY poly : polys) {
            if (poly != null && poly.bodyId == planetId) {
                planetRing = poly;
                break;
            }
        }
        assertTrue(planetRing != null, "gas giant should have an orbit stroke");
        double hintM = bodies.get(planetId).getDistanceLs() * ls;
        double minR = Double.POSITIVE_INFINITY;
        double maxR = 0.0;
        for (int i = 0; i < planetRing.wx.length; i++) {
            double r = Math.hypot(planetRing.wx[i] - starX, planetRing.wy[i] - starY);
            minR = Math.min(minR, r);
            maxR = Math.max(maxR, r);
        }
        assertTrue(minR > hintM * 0.35,
                "orbit must not pass near star; minR=" + (minR / ls) + " Ls hint=" + (hintM / ls) + " Ls");
        assertTrue(maxR / Math.max(minR, 1.0) < 2.5,
                "stroke should be near-circular, not a star polygon; ratio=" + (maxR / minR));
    }
}
