package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.systemmap.MapScaleMode;
import org.dce.ed.systemmap.SystemMapJournalEnricher;
import org.dce.ed.systemmap.SystemMapFixture;
import org.dce.ed.systemmap.SystemMapFixtureLoader;
import org.dce.ed.systemmap.SystemMapModel;
import org.dce.ed.systemmap.SystemMapPipeline;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.ed.util.SystemOrbitGeometry.OrbitPolylineWorldXY;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * R-DRAW: {@link SystemPlanMapPanel} must translate {@link SystemMapModel} without re-deriving topology in paint.
 */
class SystemPlanMapPanelDrawTranslationTest {

    private static final double MAX_PRIMARY_RING_LS = 12_000.0;
    private static final double EPS_M = 1.0;

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static SystemMapModel pipelineModel;
    private static int idA;
    private static int idB;
    private static int idC;

    @BeforeAll
    static void loadFixture() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eor-aowsy-ri-k-c8-3670.json");
        bodies = fixture.toBodies();
        pipelineModel = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false);
        idA = fixture.bodyIdByLabel("A");
        idB = fixture.bodyIdByLabel("B");
        idC = fixture.bodyIdByLabel("C");
    }

    private static SystemPlanMapPanel panelAfterSetScene() {
        SystemPlanMapPanel panel = new SystemPlanMapPanel();
        panel.setSize(900, 700);
        Map<Integer, double[]> kepler = SystemOrbitGeometry.bodyPositionsMetres(bodies, Instant.EPOCH, false);
        panel.setScene(bodies, kepler, null, null, null, false, Instant.EPOCH);
        return panel;
    }

    @Nested
    @DisplayName("R-DRAW C: orbit polylines after setScene")
    class OrbitPolylines {

        @Test
        void noHeliocentricRingAroundPrimaryStar() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            OrbitGeometryTestSupport.assertNoHeliocentricRingAroundPrimaryStar(
                    panel.mapModelForTests(), bodies, idA, MAX_PRIMARY_RING_LS, panel.orbitLinesForTests());
        }

        @Test
        void rebuildMatchesPipelineTopologyFlags() {
            assertTrue(pipelineModel.hasBarycentreMutualRing());
            SystemPlanMapPanel panel = panelAfterSetScene();
            assertTrue(panel.mapModelForTests().hasBarycentreMutualRing());
        }
    }

    @Nested
    @DisplayName("R-DRAW D: body dots match model map plane")
    class BodyDots {

        @Test
        void dotsAlignWithMapPlanePositions() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            SystemMapModel model = panel.mapModelForTests();
            for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
                if (e.getValue() == null || e.getValue().isScanBarycentreRow()) {
                    continue;
                }
                int id = e.getKey().intValue();
                assertTrue(panel.hasBodyDotForTests(id), "missing dot for id " + id);
                double mx = model.mapPlaneX(id);
                double my = model.mapPlaneY(id);
                assertEquals(mx, panel.dotWorldXForTests(id), EPS_M, "wx id " + id);
                assertEquals(my, panel.dotWorldYForTests(id), EPS_M, "wy id " + id);
            }
        }

        @Test
        void noDotsForScanBarycentreRows() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            assertFalse(panel.hasBodyDotForTests(2));
            assertFalse(panel.hasBodyDotForTests(3));
            assertFalse(panel.hasBodyDotForTests(49));
        }

        @Test
        void panelOrbitLines_includeMoonRingAroundA2a() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            int idA2a = fixture.bodyIdByLabel("A 2 a");
            boolean found = false;
            for (OrbitPolylineWorldXY p : panel.orbitLinesForTests()) {
                if (p != null && p.bodyId == idA2a) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "panel orbitLines after setScene must include per-body ring for moon A 2 a");
        }

        @Test
        void moonLabels_visibleAtSubsystemHubDetailZoom_notLayoutLsAlone() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            int idA2a = fixture.bodyIdByLabel("A 2 a");
            assertTrue(panel.hasBodyDotForTests(idA2a));
            double wideViewLs = 2_000.0;
            assertFalse(panel.mapShowMoonLabelsForTests(wideViewLs),
                    "layout-Ls alone must not enable moon labels at fit zoom");
            assertFalse(panel.bodyLabelWouldDrawForTests(idA2a, wideViewLs),
                    "A 2 a label hidden at fit-scale visible span");
            panel.zoomFactorForTests(8.0);
            assertTrue(panel.mapShowMoonLabelsForTests(wideViewLs),
                    "moon labels at subsystem-detail zoom (×8 fit)");
            assertTrue(panel.bodyLabelWouldDrawForTests(idA2a, wideViewLs),
                    "A 2 a label at subsystem-detail zoom");
            int idBcd2 = fixture.bodyIdByLabel("BCD 2");
            int idBcd2a = fixture.bodyIdByLabel("BCD 2 a");
            assertTrue(panel.bodyLabelWouldDrawForTests(idBcd2, 80.0),
                    "BCD 2 revolution center visible at cluster zoom");
            assertTrue(panel.bodyLabelWouldDrawForTests(idBcd2a, 80.0),
                    "BCD 2 a visible at subsystem-detail zoom");
            panel.zoomFactorForTests(14.0);
            assertTrue(panel.mapShowMoonLabelsForTests(wideViewLs),
                    "moon labels at deep zoom (× fit), not layout Ls alone");
            assertTrue(panel.bodyLabelWouldDrawForTests(idA2a, wideViewLs),
                    "A 2 a label should draw at deep zoom");
        }

        @Test
        void subsystemHub_lumpZoom_drawsRevolutionPathAndTwinRings() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            panel.zoomFactorForTests(1.0);
            double visibleLs = 8_000.0;
            int idA4 = fixture.bodyIdByLabel("A 4");
            assertTrue(panel.hubTwinBlueRingsWouldDrawForTests(idA4, visibleLs),
                    "A 4 moon-host lump should show twin-ring cue");
        }

        @Test
        void subsystemHub_lumpZoom_hidesMoons_showsSingleSubsystemLeafWhenDescendantsHaveBio() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            panel.zoomFactorForTests(1.0);
            double wideLs = 8_000.0;
            int idA2 = fixture.bodyIdByLabel("A 2");
            int idA2a = fixture.bodyIdByLabel("A 2 a");
            assertTrue(panel.mapModelForTests().subsystemHubBodyIds().contains(Integer.valueOf(idA2)));
            assertTrue(panel.hideDotForSubsystemLumpViewForTests(idA2a, wideLs),
                    "moons hidden at subsystem lump zoom");
            assertFalse(panel.hideDotForSubsystemLumpViewForTests(idA2, wideLs),
                    "hub centre still drawn");
            panel.zoomFactorForTests(12.0);
            assertFalse(panel.hideDotForSubsystemLumpViewForTests(idA2a, 400.0),
                    "moons visible again at cluster-detail zoom");
        }

        @Test
        void hubTwinBlueRings_whenZoomedOut_notWhenZoomedIn() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            int idA2 = fixture.bodyIdByLabel("A 2");
            assertTrue(panel.mapModelForTests().subsystemHubBodyIds().contains(Integer.valueOf(idA2)),
                    "A 2 is a moon-host subsystem hub");
            panel.zoomFactorForTests(1.0);
            assertFalse(panel.mapShowClusterDetailForTests(8_000.0),
                    "fit-scale view is lump mode, not cluster detail");
            assertTrue(panel.hubTwinBlueRingsWouldDrawForTests(idA2, 8_000.0),
                    "twin blue rings cue zoomed-out hubs (e.g. A 2 moons)");
            panel.zoomFactorForTests(12.0);
            assertTrue(panel.mapShowClusterDetailForTests(400.0),
                    "deep zoom enables cluster detail");
            assertFalse(panel.hubTwinBlueRingsWouldDrawForTests(idA2, 400.0),
                    "twin rings hidden when individual moon orbits are shown");
        }

        @Test
        void ringedOrbitCentre_showsPlanetaryRingsWhenZoomedOut() {
            Map<Integer, BodyInfo> copy = new HashMap<>(bodies);
            int idBcd2 = fixture.bodyIdByLabel("BCD 2");
            int idBcd2a = fixture.bodyIdByLabel("BCD 2 a");
            BodyInfo ringedGiant = copy.get(Integer.valueOf(idBcd2));
            ringedGiant.setRingSummaryLines(List.of("Metallic Ring", "Rocky Ring"));
            SystemPlanMapPanel panel = new SystemPlanMapPanel();
            panel.setSize(900, 700);
            Map<Integer, double[]> kepler = SystemOrbitGeometry.bodyPositionsMetres(copy, Instant.EPOCH, false);
            panel.setScene(copy, kepler, null, null, null, false, Instant.EPOCH);
            panel.zoomFactorForTests(1.0);
            double wideLs = 8_000.0;
            assertFalse(panel.mapShowClusterDetailForTests(wideLs));
            assertTrue(panel.mapModelForTests().isOrbitRevolutionCenter(idBcd2));
            assertTrue(panel.planetaryRingsDecorWouldDrawForTests(idBcd2, wideLs),
                    "ringed orbit centre (e.g. asteroid/planetary rings) visible when zoomed out");
            assertFalse(panel.planetaryRingsDecorWouldDrawForTests(idBcd2a, wideLs),
                    "moons without ring data stay uncluttered when zoomed out");
        }

        @Test
        void bAndCNearEachOther_notOnPrimaryRing() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            double bx = panel.dotWorldXForTests(idB);
            double by = panel.dotWorldYForTests(idB);
            double cx = panel.dotWorldXForTests(idC);
            double cy = panel.dotWorldYForTests(idC);
            double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
            double bcSep = Math.hypot(bx - cx, by - cy) / ls;
            assertTrue(bcSep < 500.0, "B and C should be a close binary pair; sep=" + bcSep + " Ls");

            double ax = panel.dotWorldXForTests(idA);
            double ay = panel.dotWorldYForTests(idA);
            double distBA = Math.hypot(bx - ax, by - ay) / ls;
            assertTrue(distBA >= 40_000.0 && distBA <= 52_000.0,
                    "BCD trunk true-scale distance from A; was " + distBA + " Ls");
        }
    }

    @Nested
    @DisplayName("Barycentre markers and ring cull")
    class BarycentreMarkersAndRingCull {

        @Test
        void barycentreMarkers_hiddenWhenZoomedOutToSubsystemLump() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            panel.zoomFactorForTests(1.0);
            assertEquals(0, panel.barycentreMarkerCountForTests(),
                    "barycentre + markers only when individual planets are visible");
        }

        @Test
        void barycentreMarkers_includeScanRowsAndPlanetBinaryKeys() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            panel.zoomFactorForTests(8.0);
            int count = panel.barycentreMarkerCountForTests();
            assertTrue(count >= 5,
                    "scan rows 2/3/49 plus planet-binary map keys should yield multiple + markers; count=" + count);
        }

        @Test
        void barycentreMarkers_followViewTiltProjection() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            panel.zoomFactorForTests(8.0);
            panel.setViewTiltDegrees(45, false);
            double[] marker = panel.barycentreMarkerMapXYForTests(3);
            assertNotNull(marker, "Null:3 barycentre marker");
            double mx = (panel.dotWorldXForTests(idB) + panel.dotWorldXForTests(idC)) * 0.5;
            double my = (panel.dotWorldYForTests(idB) + panel.dotWorldYForTests(idC)) * 0.5;
            double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
            double missLs = Math.hypot(marker[0] - mx, marker[1] - my) / ls;
            assertTrue(missLs < 800.0,
                    "tilted barycentre + should track projected B/C cluster; miss=" + missLs + " Ls");
        }

        @Test
        void barycentreMarkers_notStrandedAtMapOrigin_whenBcdOnTrunk() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            panel.zoomFactorForTests(8.0);
            double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
            double distBa = Math.hypot(panel.dotWorldXForTests(idB) - panel.dotWorldXForTests(idA),
                    panel.dotWorldYForTests(idB) - panel.dotWorldYForTests(idA)) / ls;
            assertTrue(distBa >= 40_000.0 && distBa <= 52_000.0);
            double bx = panel.dotWorldXForTests(idB);
            double by = panel.dotWorldYForTests(idB);
            double originSep = Math.hypot(bx, by) / ls;
            assertTrue(originSep >= 1_000.0,
                    "BCD cluster should not sit at map origin; sep from origin=" + originSep + " Ls");
        }

        @Test
        void mutualOrbitRing_stillDrawnWhenZoomedIn() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            OrbitPolylineWorldXY mutual2 = null;
            for (OrbitPolylineWorldXY p : panel.orbitLinesForTests()) {
                if (p != null && p.bodyId == SystemOrbitGeometry.PLANET_BINARY_MUTUAL_ORBIT_RING_ID_BASE - 2) {
                    mutual2 = p;
                    break;
                }
            }
            assertNotNull(mutual2, "Null:2 mutual ring");
            SystemMapModel model = panel.mapModelForTests();
            double vcx = model.mapPlaneX(fixture.bodyIdByLabel("BCD 2"));
            double vcy = model.mapPlaneY(fixture.bodyIdByLabel("BCD 2"));
            assertFalse(panel.skipOversizeSchematicRingForTests(mutual2, 80.0, vcx, vcy, 0.08, 876.0, 676.0, true),
                    "mutual orbit rings must not disappear when zooming in");
        }

        @Test
        void heliocentricGiantSchematic_stillCulledAtBcdZoom() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            SystemMapModel model = panel.mapModelForTests();
            double vcx = model.mapPlaneX(fixture.bodyIdByLabel("BCD 2"));
            double vcy = model.mapPlaneY(fixture.bodyIdByLabel("BCD 2"));
            double rM = 49_524.0 * SystemOrbitGeometry.LIGHT_SECOND_METRES;
            int n = 48;
            double[] wx = new double[n];
            double[] wy = new double[n];
            for (int i = 0; i < n; i++) {
                double theta = (Math.PI * 2.0 * i) / n;
                wx[i] = vcx + rM * Math.cos(theta);
                wy[i] = vcy + rM * Math.sin(theta);
            }
            OrbitPolylineWorldXY giant = new OrbitPolylineWorldXY(-153524, wx, wy);
            assertFalse(panel.skipOversizeSchematicRingForTests(giant, 80.0, vcx, vcy, 0.08, 876.0, 676.0, true),
                    "true-scale map does not cull legacy schematic ring geometry");
        }
    }

    @Nested
    @DisplayName("Map labels (BCD branch)")
    class MapLabels {

        @Test
        void bcdPlanets_keepBcdPrefix_notD() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            assertEquals("BCD 5 a", panel.dotLabelForTests(fixture.bodyIdByLabel("BCD 5 a")));
            assertEquals("BCD 2", panel.dotLabelForTests(fixture.bodyIdByLabel("BCD 2")));
            assertEquals("BCD 4", panel.dotLabelForTests(fixture.bodyIdByLabel("BCD 4")));
        }

        @Test
        void primaryBranchPlanets_labeledAtMediumZoom() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            panel.zoomFactorForTests(2.5);
            assertTrue(panel.bodyLabelWouldDrawForTests(fixture.bodyIdByLabel("A 2"), 5_000.0),
                    "A-branch giants should be labeled before deep zoom");
            assertTrue(panel.bodyLabelWouldDrawForTests(fixture.bodyIdByLabel("A 1"), 5_000.0),
                    "A-branch planets should be labeled before deep zoom");
        }

        @Test
        void abranchSummary_notAnchoredBesideStarC() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            panel.zoomFactorForTests(1.0);
            FontMetrics fm = panel.getFontMetrics(panel.getFont().deriveFont(Font.PLAIN, 11f));
            SystemMapModel model = panel.mapModelForTests();
            double vcx = model.mapPlaneX(idA);
            double vcy = model.mapPlaneY(idA);
            double availW = 876.0;
            double availH = 676.0;
            double plotCx = 12.0 + availW * 0.5;
            double plotCy = 12.0 + availH * 0.5;
            double scale = panel.mapPlotScaleForTests(availW, availH);
            SystemPlanMapPanel.MapLabelDrawPlan plan = panel.labelDrawPlanForTests(panel.dotsForTests(), 8_000.0, fm,
                    vcx, vcy, scale, availW, availH, plotCx, plotCy);
            for (Map.Entry<Integer, String> e : plan.summaryTextByHubId.entrySet()) {
                String text = e.getValue();
                if (text == null || !text.startsWith("A ")) {
                    continue;
                }
                float[] anchor = plan.anchors.get(e.getKey());
                assertNotNull(anchor, "anchor for " + text);
                float[] nearA = bodyScreenPx(panel, idA, vcx, vcy, scale, availW, availH);
                float[] nearC = bodyScreenPx(panel, idC, vcx, vcy, scale, availW, availH);
                double distA = Math.hypot(anchor[0] - nearA[0], anchor[1] - nearA[1]);
                double distC = Math.hypot(anchor[0] - nearC[0], anchor[1] - nearC[1]);
                assertTrue(distA + 24.0 < distC,
                        "A-branch summary " + text + " must anchor near star A, not C (dA=" + distA + " dC=" + distC
                                + ")");
            }
        }

        private static float[] bodyScreenPx(SystemPlanMapPanel panel, int bodyId, double vcx, double vcy,
                double scale, double availW, double availH) {
            double pad = 12.0;
            double wx = panel.dotWorldXForTests(bodyId);
            double wy = panel.dotWorldYForTests(bodyId);
            return new float[] {
                    (float) (pad + availW / 2.0 + (wx - vcx) * scale),
                    (float) (pad + availH / 2.0 - (wy - vcy) * scale)
            };
        }

        @Test
        void companionBcdCluster_lumpsIntoSingleTwinRingHubWhenZoomedOut() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            panel.zoomFactorForTests(1.0);
            double visibleLs = 8_000.0;
            assertFalse(panel.mapShowClusterDetailForTests(visibleLs));
            SystemPlanMapPanel.CompanionBranchLump lump = panel.companionBranchLumpForTests(visibleLs);
            assertNotNull(lump, "BCD companion majors should merge at wide zoom");
            int idBcd2 = fixture.bodyIdByLabel("BCD 2");
            int idBcd3 = fixture.bodyIdByLabel("BCD 3");
            int idBcd4 = fixture.bodyIdByLabel("BCD 4");
            int idBcd5 = fixture.bodyIdByLabel("BCD 5");
            assertTrue(lump.memberBodyIds.contains(Integer.valueOf(idBcd2)));
            assertTrue(lump.memberBodyIds.contains(Integer.valueOf(idBcd3)));
            assertTrue(lump.memberBodyIds.contains(Integer.valueOf(idBcd4)));
            assertTrue(lump.memberBodyIds.contains(Integer.valueOf(idBcd5)));
            assertEquals(idBcd2, lump.hubBodyId);
            assertTrue(lump.summaryLabel.contains("BCD"), lump.summaryLabel);
            assertTrue(lump.summaryLabel.contains("2"), lump.summaryLabel);
            assertTrue(lump.summaryLabel.contains("5"), lump.summaryLabel);
            assertTrue(panel.hubTwinBlueRingsWouldDrawForTests(idBcd2, visibleLs));
            assertFalse(panel.hubTwinBlueRingsWouldDrawForTests(idBcd5, visibleLs));
            OrbitPolylineWorldXY mutual49 = null;
            for (OrbitPolylineWorldXY p : panel.orbitLinesForTests()) {
                if (p != null && p.bodyId == SystemOrbitGeometry.PLANET_BINARY_MUTUAL_ORBIT_RING_ID_BASE - 49) {
                    mutual49 = p;
                    break;
                }
            }
            assertNotNull(mutual49);
            assertTrue(panel.skipOrbitPolylineForCompanionLumpForTests(mutual49, lump, false));
            OrbitPolylineWorldXY mutual3 = null;
            for (OrbitPolylineWorldXY p : panel.orbitLinesForTests()) {
                if (p != null && p.bodyId == SystemOrbitGeometry.PLANET_BINARY_MUTUAL_ORBIT_RING_ID_BASE - 3) {
                    mutual3 = p;
                    break;
                }
            }
            assertNotNull(mutual3, "B+C Null:3 mutual ring");
            assertFalse(panel.skipOrbitPolylineForCompanionLumpForTests(mutual3, lump, false),
                    "stellar B+C mutual ring must stay visible at companion-lump zoom");
            boolean branchPathStillVisible = false;
            for (OrbitPolylineWorldXY p : panel.orbitLinesForTests()) {
                if (p == null || p.wx == null || p.wx.length < 3 || p.bodyId >= 0) {
                    continue;
                }
                if (SystemOrbitGeometry.isPlanetBinaryMutualOrbitRingBodyId(p.bodyId)) {
                    continue;
                }
                if (!panel.skipOrbitPolylineForCompanionLumpForTests(p, lump, false)) {
                    branchPathStillVisible = true;
                    break;
                }
            }
            assertTrue(branchPathStillVisible,
                    "companion lump must keep branch schematic paths, not only twin-ring hub cue");
        }

        @Test
        void companionBcdCluster_showsSummaryLabelOnLumpHubAtFitZoom() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            panel.zoomFactorForTests(1.0);
            double wideLs = 8_000.0;
            SystemPlanMapPanel.CompanionBranchLump lump = panel.companionBranchLumpForTests(wideLs);
            assertNotNull(lump);
            assertFalse(lump.summaryLabel.isBlank(), "companion lump should carry a summary label");
            int idBcd2 = fixture.bodyIdByLabel("BCD 2");
            assertFalse(panel.bodyLabelWouldDrawForTests(idBcd2, wideLs),
                    "individual BCD 2 label hidden while lump is active");
        }

        @Test
        void companionBcdCluster_hidesLumpedPlanetLabelsAtFitZoom() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            panel.zoomFactorForTests(1.0);
            double wideLs = 8_000.0;
            assertNotNull(panel.companionBranchLumpForTests(wideLs));
            int idB = fixture.bodyIdByLabel("B");
            int idC = fixture.bodyIdByLabel("C");
            int idD = fixture.bodyIdByLabel("D");
            int idBcd2 = fixture.bodyIdByLabel("BCD 2");
            assertTrue(panel.bodyLabelWouldDrawForTests(idB, wideLs), "B star label at fit zoom");
            assertTrue(panel.bodyLabelWouldDrawForTests(idC, wideLs), "C star label at fit zoom");
            assertTrue(panel.bodyLabelWouldDrawForTests(idD, wideLs), "D star label at fit zoom");
            assertFalse(panel.bodyLabelWouldDrawForTests(idBcd2, wideLs),
                    "individual BCD 2 label hidden at fit zoom; summary is on the lump hub");
        }

        @Test
        void companionBcdCluster_expandsWhenZoomedIntoSubsystem() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            panel.zoomFactorForTests(12.0);
            assertNull(panel.companionBranchLumpForTests(80.0));
        }

        @Test
        void bcdCluster_usesSummaryOrFanOutWhenLabelsOverlap() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            panel.zoomFactorForTests(8.0);
            FontMetrics fm = panel.getFontMetrics(panel.getFont().deriveFont(Font.PLAIN, 11f));
            SystemMapModel model = panel.mapModelForTests();
            double vcx = model.mapPlaneX(fixture.bodyIdByLabel("BCD 2"));
            double vcy = model.mapPlaneY(fixture.bodyIdByLabel("BCD 2"));
            double availW = 876.0;
            double availH = 676.0;
            double plotCx = 12.0 + availW * 0.5;
            double plotCy = 12.0 + availH * 0.5;
            int idBcd2 = fixture.bodyIdByLabel("BCD 2");
            int idBcd3 = fixture.bodyIdByLabel("BCD 3");
            int idBcd4 = fixture.bodyIdByLabel("BCD 4");
            int idBcd5 = fixture.bodyIdByLabel("BCD 5");
            double sepM = Math.hypot(panel.dotWorldXForTests(idBcd2) - panel.dotWorldXForTests(idBcd3),
                    panel.dotWorldYForTests(idBcd2) - panel.dotWorldYForTests(idBcd3));
            double scale = Math.min(0.02, 18.0 / Math.max(sepM, 1.0));
            SystemPlanMapPanel.MapLabelDrawPlan plan = panel.labelDrawPlanForTests(panel.dotsForTests(), 80.0, fm, vcx,
                    vcy, scale, availW, availH, plotCx, plotCy);
            boolean summary = plan.summaryTextByHubId.containsKey(Integer.valueOf(idBcd2))
                    || plan.summaryTextByHubId.containsKey(Integer.valueOf(idBcd3));
            if (summary) {
                String text = plan.summaryTextByHubId.values().iterator().next();
                assertTrue(text.contains("BCD"), "summary should name the cluster: " + text);
                assertTrue(plan.suppressedBodyIds.contains(Integer.valueOf(idBcd4))
                        || plan.suppressedBodyIds.contains(Integer.valueOf(idBcd5)),
                        "overlapping BCD labels should collapse to one hub");
            } else {
                assertTrue(plan.anchors.containsKey(Integer.valueOf(idBcd2)), "expected fan-out for BCD 2");
                assertTrue(plan.anchors.containsKey(Integer.valueOf(idBcd3)), "expected fan-out for BCD 3");
                float[] a2 = plan.anchors.get(Integer.valueOf(idBcd2));
                float[] a3 = plan.anchors.get(Integer.valueOf(idBcd3));
                assertTrue(Math.hypot(a2[0] - a3[0], a2[1] - a3[1]) > 8.0,
                        "BCD 2 and BCD 3 labels should fan out when summary not needed");
            }
        }
    }

    @Nested
    @DisplayName("Paused zoom: stable orbit geometry")
    class PausedZoomStability {

        @Test
        void moonOrbitRadius_stableAcrossZoomRebuildWhenPlaybackPaused() {
            SystemPlanMapPanel panel = panelAfterSetScene();
            int idA2a = fixture.bodyIdByLabel("A 2 a");
            double r0 = moonOrbitMeanRadiusM(panel, idA2a);
            panel.zoomFactorForTests(12.0);
            panel.rebuildOrbitPolylinesForTests(false, false);
            double r1 = moonOrbitMeanRadiusM(panel, idA2a);
            assertTrue(Double.isFinite(r0) && r0 > 0.0);
            assertEquals(r0, r1, r0 * 0.02, "paused zoom rebuild must not rescale moon orbit world radius");
        }

        private static double moonOrbitMeanRadiusM(SystemPlanMapPanel panel, int moonBodyId) {
            int parentId = panel.mapModelForTests().resolveParentBodyId(moonBodyId);
            double px = panel.mapModelForTests().mapPlaneX(parentId);
            double py = panel.mapModelForTests().mapPlaneY(parentId);
            for (OrbitPolylineWorldXY poly : panel.orbitLinesForTests()) {
                if (poly != null && poly.bodyId == moonBodyId) {
                    double sum = 0.0;
                    for (int i = 0; i < poly.wx.length; i++) {
                        sum += Math.hypot(poly.wx[i] - px, poly.wy[i] - py);
                    }
                    return sum / poly.wx.length;
                }
            }
            return Double.NaN;
        }
    }

    @Nested
    @DisplayName("R-DRAW H: pipeline build vs panel rebuild")
    class PolylineParity {

        @Test
        void panelOrbitBodyIds_matchPipelineRebuild() {
            List<OrbitPolylineWorldXY> fromPipeline = SystemMapPipeline.rebuildOrbitPolylines(pipelineModel,
                    new HashMap<>(pipelineModel.positionsMetres()), 96, Double.NaN);
            SystemPlanMapPanel panel = panelAfterSetScene();
            Set<Integer> pipelineIds = polylineBodyIds(fromPipeline);
            Set<Integer> panelIds = polylineBodyIds(panel.orbitLinesForTests());
            assertEquals(pipelineIds, panelIds,
                    "panel orbitLines bodyId set must match pipeline rebuild with model parents");
        }

        @Test
        void firstDivergence_documentedAsBodyIdSetOnly() {
            List<OrbitPolylineWorldXY> atBuild = pipelineModel.orbitPolylines();
            List<OrbitPolylineWorldXY> afterRebuild = SystemMapPipeline.rebuildOrbitPolylines(pipelineModel,
                    new HashMap<>(pipelineModel.positionsMetres()), 96, Double.NaN);
            Set<Integer> buildIds = polylineBodyIds(atBuild);
            Set<Integer> rebuildIds = polylineBodyIds(afterRebuild);
            assertEquals(buildIds, rebuildIds,
                    "build vs rebuild should expose same synthetic ring ids when using model parents");
        }
    }

    private static Set<Integer> polylineBodyIds(List<OrbitPolylineWorldXY> polys) {
        Set<Integer> ids = new HashSet<>();
        if (polys == null) {
            return ids;
        }
        for (OrbitPolylineWorldXY p : polys) {
            if (p != null) {
                ids.add(Integer.valueOf(p.bodyId));
            }
        }
        return ids;
    }

    @Nested
    @DisplayName("HUD target subsystem frame")
    class HudTargetSubsystemFrame {

        @Test
        void gasGiant7_frameCentersOnMoonHostSubsystem() throws IOException {
            SystemMapFixture moons = SystemMapFixtureLoader.loadClasspath("eol-prou-nn-y-b31-0-7-moons.json");
            Map<Integer, BodyInfo> moonBodies = moons.toBodies();
            int giantKey = moons.bodyIdByLabel("7");
            int moonAKey = moons.bodyIdByLabel("7 a");
            SystemPlanMapPanel panel = new SystemPlanMapPanel();
            panel.setSize(900, 700);
            Map<Integer, double[]> kepler = SystemOrbitGeometry.bodyPositionsMetres(moonBodies, Instant.EPOCH, false);
            panel.setScene(moonBodies, kepler, null, null, null, false, Instant.EPOCH);
            assertTrue(panel.hasBodyDotForTests(giantKey));
            assertTrue(panel.hasBodyDotForTests(moonAKey));
            double gx = panel.dotWorldXForTests(giantKey);
            double gy = panel.dotWorldYForTests(giantKey);
            double ax = panel.dotWorldXForTests(moonAKey);
            double ay = panel.dotWorldYForTests(moonAKey);
            double[] frame = panel.hudTargetSubsystemFrameForTests(giantKey);
            assertNotNull(frame);
            double cx = (gx + ax) * 0.5;
            double cy = (gy + ay) * 0.5;
            assertEquals(cx, frame[0], Math.max(1.0, Math.abs(cx) * 0.05));
            assertEquals(cy, frame[1], Math.max(1.0, Math.abs(cy) * 0.05));
            assertTrue(frame[2] >= 8.0, "zoom should reach cluster detail");
        }

        @Test
        void binaryMoons7d7e_frameGasGiantMoonHostSubsystem() throws IOException {
            SystemMapFixture moons = SystemMapFixtureLoader.loadClasspath("eol-prou-nn-y-b31-0-7-moons.json");
            Map<Integer, BodyInfo> moonBodies = moons.toBodies();
            SystemMapJournalEnricher.prepareMapBodies(moonBodies);
            int giantKey = moons.bodyIdByLabel("7");
            int moonAKey = moons.bodyIdByLabel("7 a");
            int moonDKey = moons.bodyIdByLabel("7 d");
            int moonEKey = moons.bodyIdByLabel("7 e");
            SystemPlanMapPanel panel = new SystemPlanMapPanel();
            panel.setSize(900, 700);
            Map<Integer, double[]> kepler = SystemOrbitGeometry.bodyPositionsMetres(moonBodies, Instant.EPOCH, false);
            panel.setScene(moonBodies, kepler, null, null, null, false, Instant.EPOCH);

            assertEquals(giantKey, panel.hudTargetSubsystemHubForTests(moonDKey));
            assertEquals(giantKey, panel.hudTargetSubsystemHubForTests(moonEKey));
            var membersD = panel.hudTargetSubsystemMemberIdsForTests(moonDKey);
            assertTrue(membersD.contains(Integer.valueOf(giantKey)));
            assertTrue(membersD.contains(Integer.valueOf(moonAKey)));
            assertTrue(membersD.contains(Integer.valueOf(moonDKey)));
            assertTrue(membersD.contains(Integer.valueOf(moonEKey)));

            double gx = panel.dotWorldXForTests(giantKey);
            double gy = panel.dotWorldYForTests(giantKey);
            double ax = panel.dotWorldXForTests(moonAKey);
            double ay = panel.dotWorldYForTests(moonAKey);
            double dx = panel.dotWorldXForTests(moonDKey);
            double dy = panel.dotWorldYForTests(moonDKey);
            double ex = panel.dotWorldXForTests(moonEKey);
            double ey = panel.dotWorldYForTests(moonEKey);
            double[] frameD = panel.hudTargetSubsystemFrameForTests(moonDKey);
            double[] frameHost = panel.hudTargetSubsystemFrameForTests(giantKey);
            assertNotNull(frameD);
            assertNotNull(frameHost);
            double hostCx = (gx + ax + dx + ex) * 0.25;
            double hostCy = (gy + ay + dy + ey) * 0.25;
            assertEquals(hostCx, frameD[0], Math.max(1.0, Math.abs(hostCx) * 0.08));
            assertEquals(hostCy, frameD[1], Math.max(1.0, Math.abs(hostCy) * 0.08));
            assertEquals(frameHost[0], frameD[0], Math.max(1.0, Math.abs(hostCx) * 0.05));
            assertEquals(frameHost[1], frameD[1], Math.max(1.0, Math.abs(hostCy) * 0.05));
        }
    }

    @Nested
    @DisplayName("True-scale mode")
    class TrueScaleDraw {

        @Test
        void setScene_trueScale_usesPipelineMode() {
            SystemPlanMapPanel panel = new SystemPlanMapPanel();
            panel.setSize(900, 700);
            panel.setMapScaleMode(MapScaleMode.TRUE_SCALE);
            Map<Integer, double[]> kepler = SystemOrbitGeometry.bodyPositionsMetres(bodies, Instant.EPOCH, false);
            panel.setScene(bodies, kepler, null, null, null, false, Instant.EPOCH);
            assertNotNull(panel.mapModelForTests());
            assertTrue(panel.mapModelForTests().trueScale());
        }

        @Test
        void trueScale_zoomScaleIsLinearWithZoomFactor() {
            SystemPlanMapPanel panel = new SystemPlanMapPanel();
            panel.setSize(900, 700);
            panel.setMapScaleMode(MapScaleMode.TRUE_SCALE);
            Map<Integer, double[]> kepler = SystemOrbitGeometry.bodyPositionsMetres(bodies, Instant.EPOCH, false);
            panel.setScene(bodies, kepler, null, null, null, false, Instant.EPOCH);
            double base = panel.mapPlotScaleForTests(876.0, 676.0);
            panel.zoomFactorForTests(8.0);
            double zoomed = panel.mapPlotScaleForTests(876.0, 676.0);
            assertTrue(Double.isFinite(base) && base > 0.0);
            assertEquals(base * 8.0, zoomed, base * 0.02);
        }

        @Test
        void rulerPlaneDistance_matchesDotSeparationInTrueScale() {
            SystemPlanMapPanel panel = new SystemPlanMapPanel();
            panel.setSize(900, 700);
            panel.setMapScaleMode(MapScaleMode.TRUE_SCALE);
            Map<Integer, double[]> kepler = SystemOrbitGeometry.bodyPositionsMetres(bodies, Instant.EPOCH, false);
            panel.setScene(bodies, kepler, null, null, null, false, Instant.EPOCH);
            assertTrue(panel.mapModelForTests().trueScale());
            double ax = panel.dotWorldXForTests(idA);
            double ay = panel.dotWorldYForTests(idA);
            double bx = panel.dotWorldXForTests(idB);
            double by = panel.dotWorldYForTests(idB);
            double ls = Math.hypot(bx - ax, by - ay) / SystemOrbitGeometry.LIGHT_SECOND_METRES;
            SystemMapModel model = panel.mapModelForTests();
            double modelLs = Math.hypot(model.mapPlaneX(idB) - model.mapPlaneX(idA),
                    model.mapPlaneY(idB) - model.mapPlaneY(idA))
                    / SystemOrbitGeometry.LIGHT_SECOND_METRES;
            assertEquals(modelLs, ls, modelLs * 0.001, "dot chord should match model plane distance");
            assertTrue(ls > 38_000.0 && ls < 56_000.0, "ruler plane chord Ls=" + ls);
        }
    }
}
