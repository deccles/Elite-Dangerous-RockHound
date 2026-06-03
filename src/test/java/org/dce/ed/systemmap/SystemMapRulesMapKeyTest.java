package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SystemMapRulesMapKeyTest {

    private static Map<Integer, BodyInfo> coOrbitBodies;
    private static int planet5Key;
    private static int bary20ScanKey;

    @BeforeAll
    static void loadCoOrbit() throws IOException {
        SystemMapFixture fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-nn-y-b31-0-co-orbit.json");
        coOrbitBodies = fixture.toBodies();
        planet5Key = fixture.bodyIdByLabel("5");
        bary20ScanKey = fixture.bodyIdByLabel("Null:20");
    }

    @Test
    @DisplayName("journal id prefers planet map key over Null barycentre scan row")
    void mapKeyForJournal_prefersPlanetOverBarycentreScanRow() {
        BodyInfo planet = coOrbitBodies.get(Integer.valueOf(planet5Key));
        assertNotNull(planet);
        int journalId = planet.getBodyId();
        assertEquals(planet5Key, SystemMapRules.mapKeyForJournalBodyId(coOrbitBodies, journalId).intValue());
    }

    @Test
    @DisplayName("Null parent journal id resolves to scan barycentre row when no planet shares it")
    void mapKeyForJournal_nullParentFallsBackToBarycentreRow() {
        assertEquals(bary20ScanKey, SystemMapRules.mapKeyForJournalBodyId(coOrbitBodies, 20).intValue());
    }
}
