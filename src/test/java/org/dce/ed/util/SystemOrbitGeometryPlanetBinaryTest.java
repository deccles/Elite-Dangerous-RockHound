package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.dce.ed.TestEnvironment;
import org.dce.ed.cache.CachedSystem;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;
import org.dce.ed.systemmap.SystemMapPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Planet–planet binaries via journal {@code Parents:[{"Null":N},{"Star":0}]} (Byua Aim TT-X c15-210 8 / 9). */
class SystemOrbitGeometryPlanetBinaryTest {

    private static final long C15210_SYSTEM_ADDRESS = 42_152_10L;

    static {
        TestEnvironment.ensureTestIsolation();
    }

    @BeforeEach
    void resetCache() {
        SystemCache.getInstance().clearAndDeleteOnDisk();
    }

    /** ScanBaryCentre BodyID 13 — heliocentric barycentre orbit (journal). */
    private static final double C15210_P_OUTER_SEC = 903_926_539.421082;
    /** Bodies 8 / 9 mutual orbit (journal). */
    private static final double C15210_P_MUTUAL_SEC = 51_606_037.020683;
    private static final double C15210_PERIOD_RATIO = C15210_P_OUTER_SEC / C15210_P_MUTUAL_SEC;
    private static final Instant C15210_EPOCH = Instant.parse("2020-01-01T00:00:00Z");

    @Test
    void resolveOrbitParent_usesBarycentreNotStarWhenNullParentShared() {
        Map<Integer, BodyInfo> bodies = c15210PlanetBinaryPair();
        int p8 = findByShortName(bodies, "8");
        int p9 = findByShortName(bodies, "9");
        int star = SystemOrbitGeometry.centralStarMapKey(bodies);

        int parent8 = SystemOrbitGeometry.resolveOrbitParentBodyId(bodies.get(Integer.valueOf(p8)), bodies, p8);
        int parent9 = SystemOrbitGeometry.resolveOrbitParentBodyId(bodies.get(Integer.valueOf(p9)), bodies, p9);

        assertTrue(SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(parent8));
        assertEquals(parent8, parent9);
        assertNotEquals(star, parent8);
    }

    @Test
    void pipeline_placesPairAroundBarycentreNotOnStarRays() {
        Map<Integer, BodyInfo> bodies = c15210PlanetBinaryPair();
        var model = SystemMapPipeline.build("Byua Aim TT-X c15-210", bodies, Instant.EPOCH, true);
        assertPairOnBarycentreRingNotOnStar(model, bodies);
    }

    @Test
    void pipeline_fssDiscoveryDistances_placeOnInnerRingNotOnStar() {
        Map<Integer, BodyInfo> bodies = c15210PlanetBinaryPairFssDistances();
        var model = SystemMapPipeline.build("Byua Aim TT-X c15-210", bodies, Instant.EPOCH, true);
        assertPairOnBarycentreRingNotOnStar(model, bodies);
    }

    @Test
    void singleStarMap_mutualOrbitKeepsConstantRadiusFromBarycentre() {
        Map<Integer, BodyInfo> bodies = c15210PlanetBinaryPairFssDistances();
        Instant t0 = Instant.parse("2020-01-01T00:00:00Z");
        Instant t1 = t0.plusSeconds(2_000_000);
        int p8 = findByShortName(bodies, "8");
        int bKey = SystemOrbitGeometry.planetBinaryBarycentreMapKey(13);
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        Map<Integer, double[]> pos0 = SystemOrbitGeometry.bodyPositionsMetres(bodies, t0, false);
        Map<Integer, double[]> pos1 = SystemOrbitGeometry.bodyPositionsMetres(bodies, t1, false);
        double r0 = distOnAxes(pos0.get(Integer.valueOf(p8)), pos0.get(Integer.valueOf(bKey)), 0, 1) / ls;
        double r1 = distOnAxes(pos1.get(Integer.valueOf(p8)), pos1.get(Integer.valueOf(bKey)), 0, 1) / ls;
        assertTrue(r0 > 0.5, "body 8 should sit on mutual ring, not at barycentre");
        assertEquals(r0, r1, r0 * 0.02 + 0.05, "distance from bary should stay constant on the mutual circle");
    }

    @Test
    void singleStarMap_mutualOrbitAdvancesWhenPlaybackFrozen() {
        Map<Integer, BodyInfo> bodies = c15210PlanetBinaryPairFssDistances();
        Instant t0 = Instant.parse("2020-01-01T00:00:00Z");
        Instant t1 = t0.plusSeconds(2_000_000);
        Map<Integer, double[]> pos0 = SystemOrbitGeometry.bodyPositionsMetres(bodies, t0, true);
        Map<Integer, double[]> pos1 = SystemOrbitGeometry.bodyPositionsMetres(bodies, t1, true);
        int p8 = findByShortName(bodies, "8");
        int p9 = findByShortName(bodies, "9");
        double sep0 = distOnAxes(pos0.get(Integer.valueOf(p8)), pos0.get(Integer.valueOf(p9)), 0, 1);
        double sep1 = distOnAxes(pos1.get(Integer.valueOf(p8)), pos1.get(Integer.valueOf(p9)), 0, 1);
        double dx = pos1.get(Integer.valueOf(p8))[0] - pos0.get(Integer.valueOf(p8))[0];
        double dy = pos1.get(Integer.valueOf(p8))[1] - pos0.get(Integer.valueOf(p8))[1];
        double moved = Math.hypot(dx, dy);
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        assertTrue(sep0 > ls, "pair should stay separated on mutual orbit");
        assertTrue(moved > ls * 0.5, "body 8 should move along mutual orbit during orbit playback");
        assertEquals(sep0, sep1, sep0 * 0.08 + ls * 0.5,
                "mutual separation should stay ~constant while revolving");
    }

    @Test
    void singleStarMap_oneMutualPeriod_body8CompletesOneRevolutionAroundBary() {
        Map<Integer, BodyInfo> bodies = c15210WithJournalOrbits();
        int samples = samplesAcrossDuration(C15210_P_MUTUAL_SEC, C15210_P_MUTUAL_SEC);
        double revolutions = unwrappedRevolutions(
                sampleMutualAngleRad8(bodies, C15210_EPOCH, C15210_P_MUTUAL_SEC, samples));
        assertEquals(1.0, revolutions, 0.10,
                "body 8 should complete ~1 mutual revolution per P_mutual");
    }

    @Test	
    void singleStarMap_oneOuterPeriod_mutualPhaseCompletesSeventeenRevolutions() {
        Map<Integer, BodyInfo> bodies = c15210WithJournalOrbits();
        int samples = samplesAcrossDuration(C15210_P_OUTER_SEC, C15210_P_MUTUAL_SEC);
        double revolutions = unwrappedRevolutions(
                sampleMutualAngleRad8(bodies, C15210_EPOCH, C15210_P_OUTER_SEC, samples));
        assertEquals(C15210_PERIOD_RATIO, revolutions, C15210_PERIOD_RATIO * 0.10,
                "mutual phase should advance ~P_outer/P_mutual turns per outer period");
    }

    @Test
    void singleStarMap_oneOuterPeriod_afterCacheReload_mutualPhaseCompletesSeventeenRevolutions() {
        Map<Integer, BodyInfo> bodies = c15210BodiesAfterCacheReload();
        int samples = samplesAcrossDuration(C15210_P_OUTER_SEC, C15210_P_MUTUAL_SEC);
        double revolutions = unwrappedRevolutions(
                sampleMutualAngleRad8(bodies, C15210_EPOCH, C15210_P_OUTER_SEC, samples));
        assertEquals(C15210_PERIOD_RATIO, revolutions, C15210_PERIOD_RATIO * 0.10,
                "after cache reload, mutual phase should advance ~P_outer/P_mutual per outer period");
    }

    @Test
    void singleStarMap_oneMutualPeriod_barycentreAdvancesFractionOfOuterOrbit() {
        Map<Integer, BodyInfo> bodies = c15210WithJournalOrbits();
        int samples = samplesAcrossDuration(C15210_P_MUTUAL_SEC, C15210_P_OUTER_SEC);
        double revolutions = unwrappedRevolutions(
                sampleBaryHeliocentricAngleRad(bodies, C15210_EPOCH, C15210_P_MUTUAL_SEC, samples));
        assertEquals(1.0 / C15210_PERIOD_RATIO, revolutions, (1.0 / C15210_PERIOD_RATIO) * 0.10,
                "barycentre heliocentric angle should advance ~1/17.5 turn per P_mutual");
    }

    @Test
    void outerToMutualPeriodRatio_matchesJournal() {
        Map<Integer, BodyInfo> bodies = c15210WithJournalOrbits();
        double ratio = SystemOrbitGeometry.planetBinaryOuterToMutualPeriodRatio(13, bodies);
        assertEquals(C15210_PERIOD_RATIO, ratio, 0.01);
    }

    @Test
    void outerToMutualPeriodRatio_withoutScanBaryCentre_isUnavailable() {
        Map<Integer, BodyInfo> bodies = c15210FullScanWithoutScanBarycentre();
        double ratio = SystemOrbitGeometry.planetBinaryOuterToMutualPeriodRatio(13, bodies);
        assertTrue(Double.isNaN(ratio), "without persisted ScanBaryCentre row, P_outer must not be guessed");
    }

    @Test
    void cacheRoundTrip_preservesScanBarycentreRowAndOuterPeriod() {
        SystemState written = systemStateFromBodies(c15210WithJournalOrbits());
        SystemCache cache = SystemCache.getInstance();
        cache.storeSystem(written);

        CachedSystem cs = cache.get(C15210_SYSTEM_ADDRESS, "Byua Aim TT-X c15-210");
        assertTrue(cs != null && cs.bodies != null);
        SystemState loaded = new SystemState();
        cache.loadInto(loaded, cs);

        BodyInfo bary = loaded.getBodies().get(Integer.valueOf(13));
        assertTrue(bary != null && bary.isScanBarycentreRow(), "ScanBaryCentre row must survive cache reload");
        assertEquals(C15210_P_OUTER_SEC, bary.getOrbitalPeriod().doubleValue(), 1.0);
        BodyInfo p8 = loaded.getBodies().get(Integer.valueOf(14));
        assertEquals("8", p8.getShortName());
        assertEquals(13, p8.getImmediateParentBodyId());
        assertEquals(C15210_P_MUTUAL_SEC, p8.getOrbitalPeriod().doubleValue(), 1.0);
        double ratio = SystemOrbitGeometry.planetBinaryOuterToMutualPeriodRatio(13, loaded.getBodies());
        assertEquals(C15210_PERIOD_RATIO, ratio, 0.01);
    }

    @Test
    void cacheRoundTrip_reAddsScanBarycentreWhenSessionBodiesOmitIt() {
        SystemState withBary = systemStateFromBodies(c15210WithJournalOrbits());
        SystemCache.getInstance().storeSystem(withBary);

        SystemState planetsOnly = systemStateFromBodies(c15210FullScanWithoutScanBarycentre());
        SystemCache.getInstance().storeSystem(planetsOnly);

        CachedSystem cs = SystemCache.getInstance().get(C15210_SYSTEM_ADDRESS, "Byua Aim TT-X c15-210");
        SystemState loaded = new SystemState();
        SystemCache.getInstance().loadInto(loaded, cs);
        assertTrue(loaded.getBodies().containsKey(Integer.valueOf(13)));
        assertTrue(loaded.getBodies().get(Integer.valueOf(13)).isScanBarycentreRow());
    }

    @Test
    void guiPlaybackPath_oneOuterPeriod_mutualPhaseCompletesSeventeenRevolutions() {
        Map<Integer, BodyInfo> bodies = c15210BodiesAfterCacheReload();
        int samples = samplesAcrossDuration(C15210_P_OUTER_SEC, C15210_P_MUTUAL_SEC);
        double revolutions = unwrappedRevolutions(
                sampleMutualAngleRad8GuiPlayback(bodies, C15210_EPOCH, C15210_P_OUTER_SEC, samples));
        assertEquals(C15210_PERIOD_RATIO, revolutions, C15210_PERIOD_RATIO * 0.20,
                "GUI playback path (freeze=true) should advance mutual phase ~P_outer/P_mutual per outer period");
    }

    @Test
    void guiPlaybackPath_steppedTicks_matchContinuousOuterMutualRatio() {
        Map<Integer, BodyInfo> bodies = c15210BodiesAfterCacheReload();
        final double daysPerWallSecond = 36.0;
        final int timerMs = 33;
        Instant t = C15210_EPOCH;
        double simDays = (timerMs / 1000.0) * daysPerWallSecond;
        long nanosPerTick = Math.max(1L, Math.round(simDays * 86400e9));
        double outerSec = C15210_P_OUTER_SEC;
        int ticks = (int) Math.ceil(outerSec / (simDays * 86400.0));
        double[] angles = new double[ticks + 1];
        for (int i = 0; i <= ticks; i++) {
            angles[i] = mutualAngleRad8GuiPlayback(bodies, t);
            t = t.plusNanos(nanosPerTick);
        }
        double revolutions = unwrappedRevolutions(angles);
        assertEquals(C15210_PERIOD_RATIO, revolutions, C15210_PERIOD_RATIO * 0.22,
                "33 ms ticks at 36 d/s should match continuous ratio over one P_outer");
    }

    @Test
    void pipeline_refreshPositionsForPlayback_matchesGuiRefine() {
        Map<Integer, BodyInfo> bodies = c15210BodiesAfterCacheReload();
        Instant t = C15210_EPOCH.plusSeconds(1_000_000);
        var model = SystemMapPipeline.build("Byua Aim TT-X c15-210", bodies, C15210_EPOCH, true);
        Map<Integer, double[]> kepler = SystemOrbitGeometry.bodyPositionsMetres(bodies, t, true);
        Map<Integer, double[]> playback = SystemMapPipeline.refreshPositionsForPlayback(model, kepler, t, true);
        int p8 = findByShortName(bodies, "8");
        int bKey = SystemOrbitGeometry.planetBinaryBarycentreMapKey(13);
        assertEquals(
                distOnAxes(playback.get(Integer.valueOf(p8)), playback.get(Integer.valueOf(bKey)),
                        model.projectionAxis0(), model.projectionAxis1()),
                distOnAxes(playback.get(Integer.valueOf(p8)), playback.get(Integer.valueOf(bKey)),
                        model.projectionAxis0(), model.projectionAxis1()),
                SystemOrbitGeometry.LIGHT_SECOND_METRES * 0.05,
                "refreshPositionsForPlayback should match refineSingleStarMapPositions");
    }

    @Test
    void pipeline_emitsMutualOrbitRingAroundBarycentre() {
        Map<Integer, BodyInfo> bodies = c15210PlanetBinaryPairFssDistances();
        var model = SystemMapPipeline.build("Byua Aim TT-X c15-210", bodies, Instant.EPOCH, true);
        boolean mutual = model.orbitPolylines().stream()
                .anyMatch(p -> p != null && SystemOrbitGeometry.isPlanetBinaryMutualOrbitRingBodyId(p.bodyId));
        assertTrue(mutual, "expected mutual orbit ring at planet-binary barycentre");
    }

    @Test
    void resolveOrbitParent_infersNullParentWhenCacheStillSaysStar() {
        Map<Integer, BodyInfo> bodies = c15210PlanetBinaryPairFssDistances();
        BodyInfo p8 = bodies.get(Integer.valueOf(findByShortName(bodies, "8")));
        p8.setImmediateParentBodyId(0);
        int p8id = findByShortName(bodies, "8");
        int parent = SystemOrbitGeometry.resolveOrbitParentBodyId(p8, bodies, p8id);
        assertTrue(SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(parent));
    }

    /**
     * Byua Aim SZ-G d10-2113: journal {@code ScanBaryCentre} BodyID 12 with {@code 1 b}/{@code 1 c} on Null:12.
     * The sentinel row must not anchor the pair at its tiny heliocentric SMA (~7 Ls) next to the star.
     */
    @Test
    void pipeline_szG_d10_2113_planetBinaryBarycentreNotAtStar() {
        Map<Integer, BodyInfo> bodies = szG_d10_2113FromJournal();
        int pB = findByShortName(bodies, "1 b");
        int parentB = SystemOrbitGeometry.resolveOrbitParentBodyId(
                bodies.get(Integer.valueOf(pB)), bodies, pB);
        assertTrue(SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(parentB));
        assertFalse(bodies.containsKey(Integer.valueOf(parentB)));

        var model = SystemMapPipeline.build("Byua Aim SZ-G d10-2113", bodies, Instant.EPOCH, true);
        int bKey = SystemOrbitGeometry.planetBinaryBarycentreMapKey(12);
        Map<Integer, double[]> pos = model.positionsMetres();
        assertNotNull(pos.get(Integer.valueOf(bKey)),
                "barycentre map key missing from positions; keys=" + pos.keySet());
        int star = SystemOrbitGeometry.centralStarMapKey(bodies);
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;
        double dBaryStar = distOnAxes(pos.get(Integer.valueOf(bKey)), pos.get(Integer.valueOf(star)),
                model.projectionAxis0(), model.projectionAxis1()) / ls;
        assertTrue(dBaryStar > 500.0,
                "1 b/1 c barycentre should orbit near the gas giant (~2250 Ls), not beside the star; was "
                        + dBaryStar + " Ls");

        int pC = findByShortName(bodies, "1 c");
        double d8Bary = distOnAxes(pos.get(Integer.valueOf(pB)), pos.get(Integer.valueOf(bKey)),
                model.projectionAxis0(), model.projectionAxis1()) / ls;
        double d9Bary = distOnAxes(pos.get(Integer.valueOf(pC)), pos.get(Integer.valueOf(bKey)),
                model.projectionAxis0(), model.projectionAxis1()) / ls;
        assertTrue(d8Bary > 0.5 && d9Bary > 0.5, "1 b and 1 c should orbit the mutual barycentre ring");
        double d89 = distOnAxes(pos.get(Integer.valueOf(pB)), pos.get(Integer.valueOf(pC)),
                model.projectionAxis0(), model.projectionAxis1()) / ls;
        assertTrue(d89 > 1.0, "1 b and 1 c should be separated on the mutual orbit");
    }

    private static void assertPairOnBarycentreRingNotOnStar(org.dce.ed.systemmap.SystemMapModel model,
            Map<Integer, BodyInfo> bodies) {
        Map<Integer, double[]> pos = model.positionsMetres();
        int a0 = model.projectionAxis0();
        int a1 = model.projectionAxis1();
        int p8 = findByShortName(bodies, "8");
        int p9 = findByShortName(bodies, "9");
        int bKey = SystemOrbitGeometry.planetBinaryBarycentreMapKey(13);

        double[] star = pos.get(Integer.valueOf(SystemOrbitGeometry.centralStarMapKey(bodies)));
        double[] bary = pos.get(Integer.valueOf(bKey));
        double[] pos8 = pos.get(Integer.valueOf(p8));
        double[] pos9 = pos.get(Integer.valueOf(p9));

        assertTrue(star != null && bary != null && pos8 != null && pos9 != null);
        double dBaryStar = distOnAxes(bary, star, a0, a1);
        double d8Star = distOnAxes(pos8, star, a0, a1);
        double d9Star = distOnAxes(pos9, star, a0, a1);
        double d89 = distOnAxes(pos8, pos9, a0, a1);
        double ls = SystemOrbitGeometry.LIGHT_SECOND_METRES;

        double d8 = bodies.get(Integer.valueOf(p8)).getDistanceLs();
        double d9 = bodies.get(Integer.valueOf(p9)).getDistanceLs();
        double dBaryLs = (d8 + d9) * 0.5;
        double mutualLs = Math.max(Math.abs(d8 - dBaryLs), Math.abs(d9 - dBaryLs));
        if (mutualLs < 0.5) {
            mutualLs = Math.max(0.5, Math.abs(d8 - d9) * 0.5);
        }

        assertTrue(dBaryStar > 2.0 * ls, "barycentre ring should not sit on the star");
        assertTrue(d8Star > 2.0 * ls, "body 8 should not sit on the star");
        assertTrue(d9Star > 2.0 * ls, "body 9 should not sit on the star");
        double d8Bary = distOnAxes(pos8, bary, a0, a1);
        double d9Bary = distOnAxes(pos9, bary, a0, a1);
        assertTrue(Math.abs(d8Bary - d9Bary) < mutualLs * ls * 0.15,
                "8 and 9 should sit at the same mutual-orbit radius from the barycentre");
        assertTrue(d89 >= mutualLs * ls * 1.6,
                "8 and 9 should be on opposite sides of the mutual orbit (~" + (2 * mutualLs) + " Ls apart)");
        assertTrue(Math.abs(d8Star - d9Star) < Math.max(mutualLs * ls * 2.5, 0.02 * Math.min(d8Star, d9Star)),
                "8 and 9 should sit at similar heliocentric distance (binary pair, not radial spokes)");
        assertTrue(Math.abs(d8Star - dBaryStar) < 0.65 * dBaryStar,
                "body 8 should orbit near the barycentre, not at the star");
        assertTrue(Math.abs(d9Star - dBaryStar) < 0.65 * dBaryStar,
                "body 9 should orbit near the barycentre, not at the star");
    }

    /** Journal snapshot from Byua Aim SZ-G d10-2113 (2026-05-17). */
    private static Map<Integer, BodyInfo> szG_d10_2113FromJournal() {
        Map<Integer, BodyInfo> bodies = new HashMap<>();
        BodyInfo star = new BodyInfo();
        star.setBodyName("Byua Aim SZ-G d10-2113");
        star.setBodyShortName("Byua Aim SZ-G d10-2113");
        star.setStarType("F");
        star.setDistanceLs(0);
        bodies.put(Integer.valueOf(0), star);

        BodyInfo giant = new BodyInfo();
        giant.setBodyShortName("1");
        giant.setPlanetClass("Sudarsky class I gas giant");
        giant.setDistanceLs(2250.910720);
        giant.setImmediateParentBodyId(0);
        bodies.put(Integer.valueOf(10), giant);

        BodyInfo bary = new BodyInfo();
        bary.setBodyId(12);
        bary.setBodyName("Byua Aim SZ-G d10-2113 barycentre 12");
        bary.setScanBarycentreRow(true);
        bary.setSemiMajorAxisM(2_030_467_450.618744);
        bary.setOrbitalPeriod(2_386_382.281780);
        bary.setMeanAnomaly(106.643193);
        bodies.put(Integer.valueOf(12), bary);

        BodyInfo pB = new BodyInfo();
        pB.setBodyShortName("1 b");
        pB.setPlanetClass("Icy body");
        pB.setDistanceLs(2250.794836);
        pB.setImmediateParentBodyId(12);
        bodies.put(Integer.valueOf(13), pB);

        BodyInfo pC = new BodyInfo();
        pC.setBodyShortName("1 c");
        pC.setPlanetClass("Icy body");
        pC.setDistanceLs(2250.820447);
        pC.setImmediateParentBodyId(12);
        bodies.put(Integer.valueOf(14), pC);

        return bodies;
    }

    private static Map<Integer, BodyInfo> c15210PlanetBinaryPair() {
        Map<Integer, BodyInfo> bodies = new HashMap<>();
        BodyInfo star = new BodyInfo();
        star.setStarSystem("Byua Aim TT-X c15-210");
        star.setBodyShortName("Byua Aim TT-X c15-210");
        star.setStarType("K");
        star.setDistanceLs(0);
        bodies.put(Integer.valueOf(0), star);

        BodyInfo p8 = new BodyInfo();
        p8.setBodyShortName("8");
        p8.setPlanetClass("Icy body");
        p8.setDistanceLs(4112.715489);
        p8.setImmediateParentBodyId(13);
        p8.setSemiMajorAxisM(1392328739.166260);
        p8.setOrbitalPeriod(51606037.020683);
        p8.setMeanAnomaly(264.985915);
        p8.setOrbitalEpochMillis(Long.valueOf(Instant.parse("2020-01-01T00:00:00Z").toEpochMilli()));
        bodies.put(Integer.valueOf(14), p8);

        BodyInfo p9 = new BodyInfo();
        p9.setBodyShortName("9");
        p9.setPlanetClass("Icy body");
        p9.setDistanceLs(4096.395045);
        p9.setImmediateParentBodyId(13);
        p9.setSemiMajorAxisM(3484212040.901184);
        p9.setOrbitalPeriod(51606037.020683);
        p9.setMeanAnomaly(264.985876);
        p9.setOrbitalEpochMillis(Long.valueOf(Instant.parse("2020-01-01T00:00:00Z").toEpochMilli()));
        bodies.put(Integer.valueOf(15), p9);

        BodyInfo outer = new BodyInfo();
        outer.setBodyShortName("1");
        outer.setPlanetClass("High metal content body");
        outer.setDistanceLs(394.257639);
        outer.setImmediateParentBodyId(0);
        bodies.put(Integer.valueOf(5), outer);

        return bodies;
    }

    /** FSS-style heliocentric distances (5 / 12 Ls) as shown in the System tab before detailed scan. */
    private static Map<Integer, BodyInfo> c15210PlanetBinaryPairFssDistances() {
        Map<Integer, BodyInfo> bodies = c15210PlanetBinaryPair();
        bodies.get(Integer.valueOf(findByShortName(bodies, "8"))).setDistanceLs(5.0);
        bodies.get(Integer.valueOf(findByShortName(bodies, "9"))).setDistanceLs(12.0);
        return bodies;
    }

    private static Map<Integer, BodyInfo> c15210WithJournalOrbits() {
        Map<Integer, BodyInfo> bodies = c15210FullScanWithoutScanBarycentre();
        addC15210ScanBarycentreRow(bodies);
        return bodies;
    }

    /** Detailed-scan distances and elements, but no in-memory {@code ScanBaryCentre} row (typical after cache reload). */
    private static Map<Integer, BodyInfo> c15210FullScanWithoutScanBarycentre() {
        Map<Integer, BodyInfo> bodies = c15210PlanetBinaryPair();
        Long epoch = Long.valueOf(C15210_EPOCH.toEpochMilli());
        bodies.get(Integer.valueOf(findByShortName(bodies, "8"))).setOrbitalEpochMillis(epoch);
        bodies.get(Integer.valueOf(findByShortName(bodies, "9"))).setOrbitalEpochMillis(epoch);
        return bodies;
    }

    private static void addC15210ScanBarycentreRow(Map<Integer, BodyInfo> bodies) {
        BodyInfo bary = new BodyInfo();
        bary.setBodyId(13);
        bary.setBodyName("Byua Aim TT-X c15-210 barycentre 13");
        bary.setScanBarycentreRow(true);
        bary.setOrbitalPeriod(C15210_P_OUTER_SEC);
        bary.setSemiMajorAxisM(1.264_899_619_601_6e12);
        bary.setMeanAnomaly(200.0);
        bary.setOrbitalEpochMillis(Long.valueOf(C15210_EPOCH.toEpochMilli()));
        bodies.put(Integer.valueOf(13), bary);
    }

    private static SystemState systemStateFromBodies(Map<Integer, BodyInfo> bodies) {
        SystemState state = new SystemState();
        state.setSystemName("Byua Aim TT-X c15-210");
        state.setSystemAddress(C15210_SYSTEM_ADDRESS);
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            BodyInfo b = e.getValue();
            if (b == null) {
                continue;
            }
            int id = e.getKey().intValue();
            b.setBodyId(id);
            state.getBodies().put(Integer.valueOf(id), b);
        }
        return state;
    }

    /** Simulates GUI {@code loadSystem} after journal scan + cache persist. */
    private static Map<Integer, BodyInfo> c15210BodiesAfterCacheReload() {
        SystemCache cache = SystemCache.getInstance();
        cache.storeSystem(systemStateFromBodies(c15210WithJournalOrbits()));
        CachedSystem cs = cache.get(C15210_SYSTEM_ADDRESS, "Byua Aim TT-X c15-210");
        SystemState loaded = new SystemState();
        cache.loadInto(loaded, cs);
        return loaded.getBodies();
    }

    /** ~16 samples per {@code referencePeriodSec} across {@code durationSec} (capped for runtime). */
    private static int samplesAcrossDuration(double durationSec, double referencePeriodSec) {
        int perPeriod = 16;
        int n = (int) Math.ceil(durationSec / referencePeriodSec * perPeriod) + 1;
        return Math.max(8, Math.min(n, 400));
    }

    private static double[] sampleMutualAngleRad8(Map<Integer, BodyInfo> bodies, Instant t0,
            double durationSec, int samples) {
        double[] angles = new double[samples];
        for (int i = 0; i < samples; i++) {
            double frac = samples <= 1 ? 0.0 : i / (double) (samples - 1);
            angles[i] = mutualAngleRad8(bodies, instantAfterSeconds(t0, durationSec * frac));
        }
        return angles;
    }

    private static double[] sampleBaryHeliocentricAngleRad(Map<Integer, BodyInfo> bodies, Instant t0,
            double durationSec, int samples) {
        double[] angles = new double[samples];
        for (int i = 0; i < samples; i++) {
            double frac = samples <= 1 ? 0.0 : i / (double) (samples - 1);
            angles[i] = baryHeliocentricAngleRad(bodies, instantAfterSeconds(t0, durationSec * frac));
        }
        return angles;
    }

    private static Instant instantAfterSeconds(Instant t0, double seconds) {
        long whole = (long) seconds;
        double frac = seconds - whole;
        return t0.plusSeconds(whole).plusNanos((long) (frac * 1_000_000_000L));
    }

    private static double mutualAngleRad8(Map<Integer, BodyInfo> bodies, Instant when) {
        return mutualAngleRad8FromPositions(
                bodyPositionsSingleStarMap(bodies, when, false), bodies);
    }

    private static double mutualAngleRad8GuiPlayback(Map<Integer, BodyInfo> bodies, Instant when) {
        return mutualAngleRad8FromPositions(guiPlaybackPositions(bodies, when), bodies);
    }

    /** Same as {@code SystemPlanMapPanel.refineSingleStarMapPositions} during orbit playback. */
    private static Map<Integer, double[]> guiPlaybackPositions(Map<Integer, BodyInfo> bodies, Instant when) {
        return bodyPositionsSingleStarMap(bodies, when, true);
    }

    private static Map<Integer, double[]> bodyPositionsSingleStarMap(Map<Integer, BodyInfo> bodies, Instant when,
            boolean freeze) {
        return SystemOrbitGeometry.bodyPositionsMetres(bodies, when, freeze);
    }

    private static double[] sampleMutualAngleRad8GuiPlayback(Map<Integer, BodyInfo> bodies, Instant t0,
            double durationSec, int samples) {
        double[] angles = new double[samples];
        for (int i = 0; i < samples; i++) {
            double frac = samples <= 1 ? 0.0 : i / (double) (samples - 1);
            angles[i] = mutualAngleRad8GuiPlayback(bodies, instantAfterSeconds(t0, durationSec * frac));
        }
        return angles;
    }

    private static double mutualAngleRad8FromPositions(Map<Integer, double[]> pos, Map<Integer, BodyInfo> bodies) {
        int p8 = findByShortName(bodies, "8");
        int bKey = SystemOrbitGeometry.planetBinaryBarycentreMapKey(13);
        double[] bary = pos.get(Integer.valueOf(bKey));
        double[] pos8 = pos.get(Integer.valueOf(p8));
        return Math.atan2(
                axisCoord(pos8, 1) - axisCoord(bary, 1),
                axisCoord(pos8, 0) - axisCoord(bary, 0));
    }

    private static double baryHeliocentricAngleRad(Map<Integer, BodyInfo> bodies, Instant when) {
        int bKey = SystemOrbitGeometry.planetBinaryBarycentreMapKey(13);
        int starKey = SystemOrbitGeometry.centralStarMapKey(bodies);
        Map<Integer, double[]> pos = SystemOrbitGeometry.bodyPositionsMetres(bodies, when, false);
        double[] star = pos.get(Integer.valueOf(starKey));
        double[] bary = pos.get(Integer.valueOf(bKey));
        return Math.atan2(
                axisCoord(bary, 1) - axisCoord(star, 1),
                axisCoord(bary, 0) - axisCoord(star, 0));
    }

    /** Net signed revolutions from angle samples (radians), allowing multiple turns between samples. */
    private static double unwrappedRevolutions(double[] anglesRad) {
        double sum = 0.0;
        for (int i = 1; i < anglesRad.length; i++) {
            double d = anglesRad[i] - anglesRad[i - 1];
            sum += d - Math.round(d / (Math.PI * 2.0)) * (Math.PI * 2.0);
        }
        return sum / (Math.PI * 2.0);
    }

    private static int findByShortName(Map<Integer, BodyInfo> bodies, String sn) {
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getValue() != null && sn.equals(e.getValue().getShortName())) {
                return e.getKey().intValue();
            }
        }
        return -1;
    }

    private static double distOnAxes(double[] a, double[] b, int axis0, int axis1) {
        double dx = axisCoord(a, axis0) - axisCoord(b, axis0);
        double dy = axisCoord(a, axis1) - axisCoord(b, axis1);
        return Math.hypot(dx, dy);
    }

    private static double axisCoord(double[] p, int axis) {
        return p != null && axis >= 0 && axis < p.length ? p[axis] : 0.0;
    }

    /**
     * Regression: {@code ScanBaryCentre} rows plus corrupt parent chains must not recurse until stack overflow
     * when the system map opens (live cache / EDSM sync).
     */
    @Test
    void bodyPositionsMetres_noStackOverflow_scanBarycentreAndCyclicParents() {
        Map<Integer, BodyInfo> bodies = new HashMap<>();
        BodyInfo star = new BodyInfo();
        star.setBodyId(0);
        star.setBodyShortName("Test System");
        star.setStarType("G");
        star.setDistanceLs(0.0);
        bodies.put(Integer.valueOf(0), star);

        BodyInfo bary12 = new BodyInfo();
        bary12.setBodyId(12);
        bary12.setScanBarycentreRow(true);
        bary12.setDistanceLs(7.0);
        bodies.put(Integer.valueOf(12), bary12);

        BodyInfo oneB = new BodyInfo();
        oneB.setBodyId(13);
        oneB.setBodyShortName("1 b");
        oneB.setPlanetClass("Icy body");
        oneB.setDistanceLs(2250.0);
        oneB.setImmediateParentBodyId(12);
        bodies.put(Integer.valueOf(13), oneB);

        BodyInfo oneC = new BodyInfo();
        oneC.setBodyId(14);
        oneC.setBodyShortName("1 c");
        oneC.setPlanetClass("Icy body");
        oneC.setDistanceLs(2251.0);
        oneC.setImmediateParentBodyId(13);
        bodies.put(Integer.valueOf(14), oneC);

        Map<Integer, double[]> pos = SystemOrbitGeometry.bodyPositionsMetres(bodies, Instant.EPOCH, false);
        assertNotNull(pos);
        assertTrue(pos.size() >= 2);
        assertNotNull(pos.get(Integer.valueOf(13)));
    }
}
