package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Regression: high-eccentricity lone-star planets must not draw Kepler spirograph strokes through the star.
 */
class EolProuWbZC15363OrbitDisplayTest {

    private static SystemMapFixture fixture;
    private static SystemSession session;
    private static int idStar;
    private static int idPlanet;

    @BeforeAll
    static void load() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-wb-z-c15-363.json");
        session = SystemSessionFactory.open(
                new SystemMapSystemLoader.Loaded(fixture.name, fixture.toBodies(), "cache"));
        idStar = fixture.bodyIdByLabel("Eol Prou WB-Z c15-363");
        idPlanet = fixture.bodyIdByLabel("1");
    }

    @Test
    void planetOrbit_isGuideCircleNotSpirographAtViewTilt() {
        SystemMapModel map = SystemMapPipeline.build(fixture.name, fixture.toBodies(), Instant.EPOCH, false, session);
        List<OrbitPolylineWorldXY> polys = SystemMapPipeline.rebuildOrbitPolylines(
                map, map.positionsMetres(), 128, Double.NaN, null, 23, Instant.EPOCH, session);
        OrbitPolylineWorldXY planetRing = polys.stream().filter(p -> p.bodyId == idPlanet).findFirst().orElse(null);
        assertNotNull(planetRing, "planet orbit stroke");

        BodyInfo planetBody = map.bodies().get(Integer.valueOf(idPlanet));
        assertNotNull(planetBody);
        assertNotNull(planetBody.getEccentricity());
        assertTrue(planetBody.getEccentricity().doubleValue() > 0.75);

        assertTrue(planetRing.estimated, "high-e lone-star planet uses journal guide circle");

        int a0 = map.projectionAxis0();
        int a1 = map.projectionAxis1();
        double[] starPos = map.positionsMetres().get(Integer.valueOf(idStar));
        assertNotNull(starPos);
        double[] starView = org.dce.ed.systemmap.MapViewProjection.projectFromPositionMetres(starPos, a0, a1, 23);
        double pcx = starView[0];
        double pcy = starView[1];
        double minR = Double.POSITIVE_INFINITY;
        double maxR = 0.0;
        for (int i = 0; i < planetRing.wx.length; i++) {
            double r = Math.hypot(planetRing.wx[i] - pcx, planetRing.wy[i] - pcy);
            minR = Math.min(minR, r);
            maxR = Math.max(maxR, r);
        }
        assertTrue(maxR / minR < 1.12, "guide circle is round, not spirograph; ratio=" + (maxR / minR));
        assertTrue(minR > maxR * 0.9,
                "ring is a closed circle around the star, not a star-piercing spirograph; minR="
                        + (minR / SystemOrbitGeometry.LIGHT_SECOND_METRES) + " Ls maxR="
                        + (maxR / SystemOrbitGeometry.LIGHT_SECOND_METRES) + " Ls");
    }
}
