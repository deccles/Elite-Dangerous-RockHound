package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MultiCommoditySourcePlannerTest {
    @Test
    void groupsCommodityOffersByStationAndRanksMostMissionsFirst() {
        var needs = List.of(new MultiCommodityMissionNeed(1, "Gold", 20, null),
                new MultiCommodityMissionNeed(2, "Silver", 30, null));
        var gold = choice("Sol", "Galileo", "Gold", 20, 2.0);
        var silverSame = choice("Sol", "Galileo", "Silver", 30, 2.0);
        var silverOnly = choice("Lave", "Lave Station", "Silver", 30, 1.0);

        var rows = MultiCommoditySourcePlanner.assess(needs, Map.of(), Map.of(
                "gold", List.of(gold), "silver", List.of(silverSame, silverOnly)));

        assertEquals("Galileo", rows.get(0).station().station());
        assertEquals(2, rows.get(0).allocation().missionIds().size());
        assertEquals("Gold 20/20; Silver 30/30", rows.get(0).commoditiesText());
        assertEquals(1, rows.get(1).allocation().missionIds().size());
    }

    private static CommoditySourceChoice choice(String system, String station, String commodity,
            int stock, double distance) {
        return new CommoditySourceChoice(system, station, distance, 100.0, 10, stock,
                "2026-08-13T00:00:00Z", "Orbis", 3, null);
    }
}
