package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmap.MapScaleMode;
import org.dce.ed.systemmap.SystemMapFixture;
import org.dce.ed.systemmap.SystemMapFixtureLoader;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Single-click hit testing uses the same screen layout as {@link SystemPlanMapPanel#paintComponent}.
 */
class SystemPlanMapPanelClickHitTest {

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static int idA;

    @BeforeAll
    static void loadFixture() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eor-aowsy-ri-k-c8-3670.json");
        bodies = fixture.toBodies();
        idA = fixture.bodyIdByLabel("A");
    }

    @Test
    @DisplayName("Click on star A screen position resolves to body A")
    void clickOnStarA_resolvesBodyId() {
        SystemPlanMapPanel panel = new SystemPlanMapPanel();
        panel.setSize(900, 700);
        panel.setMapScaleMode(MapScaleMode.SCHEMATIC);
        Map<Integer, double[]> kepler = SystemOrbitGeometry.bodyPositionsMetres(bodies, Instant.EPOCH, false);
        panel.setScene(bodies, kepler, null, null, null, false, Instant.EPOCH);

        float[] screen = panel.bodyScreenPxForClickHitTests(idA);
        int px = Math.round(screen[0]);
        int py = Math.round(screen[1]);

        int hitId = panel.mapClickHitBodyIdForTests(px, py);
        assertEquals(idA, hitId, "expected star A at (" + px + "," + py + ")");
    }

    @Test
    @DisplayName("Click on empty plot margin returns no body hit")
    void clickOnMargin_misses() {
        SystemPlanMapPanel panel = new SystemPlanMapPanel();
        panel.setSize(900, 700);
        panel.setMapScaleMode(MapScaleMode.SCHEMATIC);
        Map<Integer, double[]> kepler = SystemOrbitGeometry.bodyPositionsMetres(bodies, Instant.EPOCH, false);
        panel.setScene(bodies, kepler, null, null, null, false, Instant.EPOCH);

        assertEquals(-1, panel.mapClickHitBodyIdForTests(2, 2));
    }

    @Test
    @DisplayName("Orbit stroke printer exposes classification for hit logging")
    void orbitStrokeHitInfo_classifiesKeplerEllipse() {
        SystemPlanMapPanel panel = new SystemPlanMapPanel();
        panel.setSize(900, 700);
        panel.setMapScaleMode(MapScaleMode.SCHEMATIC);
        Map<Integer, double[]> kepler = SystemOrbitGeometry.bodyPositionsMetres(bodies, Instant.EPOCH, false);
        panel.setScene(bodies, kepler, null, null, null, false, Instant.EPOCH);
        panel.rebuildOrbitPolylinesForTests(true, true);

        boolean foundBodyOrbit = false;
        for (var poly : panel.orbitLinesForTests()) {
            if (poly != null && poly.bodyId > 0) {
                var info = org.dce.ed.systemmap.SystemMapOrbitStrokePrinter.orbitStrokeHitInfo(poly, bodies,
                        panel.mapModelForTests());
                assertTrue(info.type != null && !info.type.isBlank());
                foundBodyOrbit = true;
                break;
            }
        }
        assertTrue(foundBodyOrbit, "fixture should include at least one body orbit polyline");
    }
}
