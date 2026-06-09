package org.dce.ed.edsm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EdsmJournalBodyIdBridgeTest {

    @Test
    void resolveJournalBodyId_mapsArrivalStarToZero() {
        BodiesResponse.Body star = star(9781, "Eol Prou LW-L c8-75");
        assertEquals(0, EdsmJournalBodyIdBridge.resolveJournalBodyId(star, "Eol Prou LW-L c8-75"));
    }

    @Test
    void resolveJournalBodyId_keepsCompanionStarEdsmId() {
        BodiesResponse.Body companion = star(42, "Eol Prou LW-L c8-75 B");
        assertEquals(42, EdsmJournalBodyIdBridge.resolveJournalBodyId(companion, "Eol Prou LW-L c8-75"));
    }

    @Test
    void buildEdsmToJournalIdMap_onlyArrivalStar() {
        Map<Integer, Integer> map = EdsmJournalBodyIdBridge.buildEdsmToJournalIdMap(
                "Eol Prou LW-L c8-75",
                List.of(star(9781, "Eol Prou LW-L c8-75"), star(42, "Eol Prou LW-L c8-75 B")));
        assertEquals(1, map.size());
        assertEquals(0, map.get(9781));
        assertNull(map.get(42));
    }

    @Test
    void remapStarParentId_rewritesArrivalStarReference() {
        Map<Integer, Integer> map = Map.of(9781, 0);
        assertEquals(0, EdsmJournalBodyIdBridge.remapStarParentId(9781, map));
        assertEquals(42, EdsmJournalBodyIdBridge.remapStarParentId(42, map));
    }

    @Test
    void isArrivalStar_requiresStarTypeAndSystemName() {
        BodiesResponse.Body star = star(9781, "Eol Prou LW-L c8-75");
        assertTrue(EdsmJournalBodyIdBridge.isArrivalStar(star, "Eol Prou LW-L c8-75"));
        assertFalse(EdsmJournalBodyIdBridge.isArrivalStar(star, "Other System"));
        BodiesResponse.Body planet = new BodiesResponse.Body();
        planet.id = 6;
        planet.name = "Eol Prou LW-L c8-75 1";
        planet.type = "Planet";
        assertFalse(EdsmJournalBodyIdBridge.isArrivalStar(planet, "Eol Prou LW-L c8-75"));
    }

    private static BodiesResponse.Body star(long id, String name) {
        BodiesResponse.Body b = new BodiesResponse.Body();
        b.id = id;
        b.name = name;
        b.type = "Star";
        return b;
    }
}
