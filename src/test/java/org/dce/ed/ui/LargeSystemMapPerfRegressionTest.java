package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.FontMetrics;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmap.MapScaleMode;
import org.dce.ed.systemmap.SystemMapFixture;
import org.dce.ed.systemmap.SystemMapFixtureLoader;
import org.dce.ed.systemmap.SystemMapModel;
import org.dce.ed.systemmap.SystemMapPipeline;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Headless performance baselines for large wide-binary system maps.
 *
 * <p>Classpath proxy: {@code Eor Aowsy RI-K c8-3670} (37 bodies). Production repro that motivated this test:
 * {@code Eol Prou WK-N d7-1482} (~71 bodies, ~61 orbit rings) — add a dedicated fixture when available.
 *
 * <p>Excluded from default {@code mvn test} via surefire {@code excludedGroups=perf}. Run explicitly:
 * {@code mvn test -Dsurefire.excludedGroups= -Dgroups=perf -Dtest=LargeSystemMapPerfRegressionTest}
 */
@Tag("perf")
class LargeSystemMapPerfRegressionTest {

    /** Largest wide-binary fixture on the classpath (see class javadoc for production repro). */
    private static final String FIXTURE_RESOURCE = "eor-aowsy-ri-k-c8-3670.json";

    /**
     * Generous ceiling for cold {@link SystemMapPipeline#build} on the proxy fixture (ms). Tighten when
     * {@code Eol Prou WK-N d7-1482} fixture lands.
     */
    private static final long MAX_PIPELINE_BUILD_MS = 8_000L;

    /** {@link SystemPlanMapPanel#setScene} + first orbit polyline build on EDT-sized panel (ms). */
    private static final long MAX_MAP_SET_SCENE_MS = 6_000L;

    /** Single {@link SystemPlanMapPanel#labelDrawPlanForTests} at subsystem-detail zoom (ms). */
    private static final long MAX_LABEL_PLAN_BUILD_MS = 3_000L;

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static int idA;

    @BeforeAll
    static void loadFixture() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath(FIXTURE_RESOURCE);
        bodies = fixture.toBodies();
        idA = fixture.bodyIdByLabel("A");
        assertTrue(bodies.size() >= 30, "proxy fixture should represent a large system");
    }

    @Test
    @DisplayName("SystemMapPipeline.build completes within baseline (large wide-binary proxy)")
    void pipelineBuild_underBaseline() {
        warmPipelineBuild(2);
        long elapsedMs = timePipelineBuild();
        assertTrue(elapsedMs < MAX_PIPELINE_BUILD_MS,
                () -> "pipeline build took " + elapsedMs + "ms (max " + MAX_PIPELINE_BUILD_MS + "ms); "
                        + fixtureSummary());
    }

    @Test
    @DisplayName("SystemPlanMapPanel setScene + orbit lines within baseline")
    void mapPanelSetScene_underBaseline() {
        warmMapSetScene(1);
        long elapsedMs = timeMapSetScene();
        assertTrue(elapsedMs < MAX_MAP_SET_SCENE_MS,
                () -> "setScene took " + elapsedMs + "ms (max " + MAX_MAP_SET_SCENE_MS + "ms); " + fixtureSummary());
    }

    @Test
    @DisplayName("Label collision plan at detail zoom within baseline")
    void labelPlanBuild_underBaseline() {
        SystemPlanMapPanel panel = newMapPanel();
        assertFalse(panel.dotsForTests().isEmpty());

        FontMetrics fm = panel.getFontMetrics(panel.getFont().deriveFont(Font.PLAIN, 11f));
        double visibleLs = 80.0;
        double availW = 876.0;
        double availH = 676.0;
        double plotCx = 12.0 + availW * 0.5;
        double plotCy = 12.0 + availH * 0.5;
        panel.zoomFactorForTests(8.0);
        double scale = panel.mapPlotScaleForTests(availW, availH);
        double vcx = panel.mapModelForTests().mapPlaneX(idA);
        double vcy = panel.mapModelForTests().mapPlaneY(idA);

        warmLabelPlan(panel, fm, visibleLs, vcx, vcy, scale, availW, availH, plotCx, plotCy, 2);
        long elapsedMs = timeLabelPlan(panel, fm, visibleLs, vcx, vcy, scale, availW, availH, plotCx, plotCy);
        assertTrue(elapsedMs < MAX_LABEL_PLAN_BUILD_MS,
                () -> "label plan took " + elapsedMs + "ms (max " + MAX_LABEL_PLAN_BUILD_MS + "ms); "
                        + fixtureSummary());
    }

    @Test
    @DisplayName("Orbit polyline count and vertex budget are within expected range for proxy fixture")
    void orbitGeometry_scaleSanity() {
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        assertNotNull(model);
        int ringCount = model.orbitPolylines().size();
        int vertexCount = countOrbitVertices(model.orbitPolylines());
        assertTrue(ringCount >= 20, () -> "expected many orbit rings, got " + ringCount);
        assertTrue(vertexCount >= 1_500, () -> "expected substantial vertex budget, got " + vertexCount);
        assertTrue(vertexCount <= 25_000,
                () -> "vertex explosion — got " + vertexCount + " vertices on " + ringCount + " rings");
    }

    private static void warmPipelineBuild(int iterations) {
        for (int i = 0; i < iterations; i++) {
            SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false, MapScaleMode.TRUE_SCALE);
        }
    }

    private static long timePipelineBuild() {
        long startNs = System.nanoTime();
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false,
                MapScaleMode.TRUE_SCALE);
        assertNotNull(model);
        return (System.nanoTime() - startNs) / 1_000_000L;
    }

    private static SystemPlanMapPanel newMapPanel() {
        SystemPlanMapPanel panel = new SystemPlanMapPanel();
        panel.setSize(900, 700);
        Map<Integer, double[]> kepler = SystemOrbitGeometry.bodyPositionsMetres(bodies, Instant.EPOCH, false);
        panel.setScene(bodies, kepler, null, null, null, false, Instant.EPOCH);
        return panel;
    }

    private static void warmMapSetScene(int iterations) {
        for (int i = 0; i < iterations; i++) {
            newMapPanel();
        }
    }

    private static long timeMapSetScene() {
        long startNs = System.nanoTime();
        SystemPlanMapPanel panel = newMapPanel();
        assertFalse(panel.orbitLinesForTests().isEmpty());
        return (System.nanoTime() - startNs) / 1_000_000L;
    }

    private static void warmLabelPlan(SystemPlanMapPanel panel, FontMetrics fm, double visibleLs, double vcx,
            double vcy, double scale, double availW, double availH, double plotCx, double plotCy, int iterations) {
        for (int i = 0; i < iterations; i++) {
            panel.labelDrawPlanForTests(panel.dotsForTests(), visibleLs, fm, vcx, vcy, scale, availW, availH, plotCx,
                    plotCy);
        }
    }

    private static long timeLabelPlan(SystemPlanMapPanel panel, FontMetrics fm, double visibleLs, double vcx,
            double vcy, double scale, double availW, double availH, double plotCx, double plotCy) {
        long startNs = System.nanoTime();
        SystemPlanMapPanel.MapLabelDrawPlan plan = panel.labelDrawPlanForTests(panel.dotsForTests(), visibleLs, fm, vcx,
                vcy, scale, availW, availH, plotCx, plotCy);
        assertNotNull(plan);
        return (System.nanoTime() - startNs) / 1_000_000L;
    }

    private static int countOrbitVertices(java.util.List<OrbitPolylineWorldXY> polys) {
        if (polys == null) {
            return 0;
        }
        int n = 0;
        for (OrbitPolylineWorldXY poly : polys) {
            if (poly != null && poly.wx != null) {
                n += poly.wx.length;
            }
        }
        return n;
    }

    private static String fixtureSummary() {
        return "fixture=" + fixture.name + " bodies=" + bodies.size();
    }
}
