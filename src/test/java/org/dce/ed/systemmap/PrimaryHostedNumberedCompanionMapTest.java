package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Arrival {@code *} systems with numbered stellar companions ({@code 1}, {@code 2}) — host-centred layout, not
 * wide-binary barycentre mutual rings (e.g. Eol Prou LS-T e3-3428).
 */
class PrimaryHostedNumberedCompanionMapTest {

    private static final double LS = SystemOrbitGeometry.LIGHT_SECOND_METRES;

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static SystemMapModel model;
    private static int primaryId;
    private static int companionId;

    @BeforeAll
    static void load() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-ls-t-e3-3428-numbered-companion.json");
        bodies = fixture.toBodies();
        model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false);
        primaryId = fixture.bodyIdByLabel("Eol Prou LS-T e3-3428");
        companionId = fixture.bodyIdByLabel("1");
    }

    @Test
    void classifiedAsSingleStarHostLayout() {
        assertTrue(SystemOrbitGeometry.isPrimaryHostedNumberedStellarCompanionMap(bodies));
        assertEquals(SystemLayoutKind.SINGLE_STAR, model.classification().layoutKind());
        assertFalse(model.classification().wideBinary());
        assertEquals(2, model.classification().mapStellarCount());
    }

    @Test
    void noBarycentreMutualRing() {
        assertFalse(model.hasBarycentreMutualRing());
        assertTrue(model.classification().barycentricStarIds().size() < 2);
    }

    @Test
    void numberedCompanionOrbitsPrimary() {
        BodyInfo companion = bodies.get(Integer.valueOf(companionId));
        int parent = SystemMapRules.resolveOrbitParentBodyId(companion, bodies, companionId);
        assertEquals(primaryId, parent, "companion star 1 must orbit primary *");
    }

    @Test
    void companionNearJournalDistanceFromPrimary() {
        double sep = Math.hypot(model.mapPlaneX(companionId) - model.mapPlaneX(primaryId),
                model.mapPlaneY(companionId) - model.mapPlaneY(primaryId)) / LS;
        assertTrue(sep > 2_500.0 && sep < 4_500.0, "companion at journal ~3338 Ls; was " + sep);
    }

    @Test
    void primaryNearMapOrigin() {
        double r = Math.hypot(model.mapPlaneX(primaryId), model.mapPlaneY(primaryId)) / LS;
        assertTrue(r < 500.0, "primary * at map centre; radius Ls=" + r);
    }

    @Test
    @DisplayName("journal Null:0 parent on numbered companion still hosts on *")
    void journalNullZeroParentOnNumberedCompanion() throws IOException {
        SystemMapFixture live = SystemMapFixtureLoader.loadClasspath(
                "eol-prou-ls-t-e3-3428-numbered-companion-journal-null0.json");
        Map<Integer, BodyInfo> liveBodies = live.toBodies();
        assertTrue(SystemOrbitGeometry.isPrimaryHostedNumberedStellarCompanionMap(liveBodies));
        SystemMapModel liveModel = SystemMapPipeline.build(live.name, liveBodies, Instant.EPOCH, false);
        int livePrimary = live.bodyIdByLabel("Eol Prou LS-T e3-3428");
        int liveCompanion = live.bodyIdByLabel("1");
        assertEquals(SystemLayoutKind.SINGLE_STAR, liveModel.classification().layoutKind());
        assertFalse(liveModel.hasBarycentreMutualRing());
        assertEquals(livePrimary,
                SystemMapRules.resolveOrbitParentBodyId(liveBodies.get(Integer.valueOf(liveCompanion)), liveBodies,
                        liveCompanion));
    }

    @Test
    @DisplayName("letter-branch wide binaries stay barycentric")
    void letterBranchTwoStarRemainsWideBinary() throws IOException {
        SystemMapFixture wide = SystemMapFixtureLoader.loadClasspath("tt-x-c15-29-two-star-binary.json");
        Map<Integer, BodyInfo> wideBodies = wide.toBodies();
        assertFalse(SystemOrbitGeometry.isPrimaryHostedNumberedStellarCompanionMap(wideBodies));
        SystemMapClassification clf = SystemMapRules.classify(wideBodies);
        assertEquals(SystemLayoutKind.WIDE_BINARY, clf.layoutKind());
    }
}
