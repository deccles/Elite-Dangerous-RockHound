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
        assertEquals(List.of(MultiCommodityCoverage.Status.COMPLETE,
                        MultiCommodityCoverage.Status.COMPLETE),
                rows.get(0).coverages().stream().map(MultiCommodityCoverage::status).toList());
        assertEquals(1, rows.get(1).allocation().missionIds().size());
    }

    @Test
    void coverageUsesHoldAndDistinguishesCompletePartialAndMissing() {
        var needs = List.of(new MultiCommodityMissionNeed(1, "Gold", 100, null),
                new MultiCommodityMissionNeed(2, "Silver", 50, null),
                new MultiCommodityMissionNeed(3, "Water Purifiers", 25, null));
        var rows = MultiCommoditySourcePlanner.assess(needs, Map.of("gold", 40), Map.of(
                "gold", List.of(choice("Sol", "Complete Port", "Gold", 60, 4.0),
                        choice("Lave", "Partial Port", "Gold", 30, 2.0)),
                "silver", List.of(choice("Sol", "Complete Port", "Silver", 50, 4.0),
                        choice("Lave", "Partial Port", "Silver", 10, 2.0)),
                "water purifiers", List.of(choice("Sol", "Complete Port", "Water Purifiers", 25, 4.0))));

        assertEquals("Complete Port", rows.get(0).station().station());
        assertEquals(3, rows.get(0).completeCommodityCount());
        assertEquals(0, rows.get(0).partialCommodityCount());
        assertEquals(0, rows.get(0).missingCommodityCount());
        assertEquals(List.of(MultiCommodityCoverage.Status.PARTIAL,
                        MultiCommodityCoverage.Status.PARTIAL,
                        MultiCommodityCoverage.Status.MISSING),
                rows.get(1).coverages().stream().map(MultiCommodityCoverage::status).toList());
        assertEquals(40, rows.get(1).coverages().get(0).heldTons());
        assertEquals(30, rows.get(1).coverages().get(0).stationTons());
        assertEquals(100, rows.get(1).coverages().get(0).requiredTons());
    }

    @Test
    void stationsRankByCompleteThenPartialThenMissingBeforeDistance() {
        var needs = List.of(new MultiCommodityMissionNeed(1, "Gold", 20, null),
                new MultiCommodityMissionNeed(2, "Silver", 30, null));
        var rows = MultiCommoditySourcePlanner.assess(needs, Map.of(), Map.of(
                "gold", List.of(choice("Far", "One Complete", "Gold", 20, 20.0),
                        choice("Near", "Two Partial", "Gold", 10, 1.0)),
                "silver", List.of(choice("Near", "Two Partial", "Silver", 10, 1.0))));

        assertEquals(List.of("One Complete", "Two Partial"),
                rows.stream().map(row -> row.station().station()).toList());
    }

    private static CommoditySourceChoice choice(String system, String station, String commodity,
            int stock, double distance) {
        return new CommoditySourceChoice(system, station, distance, 100.0, 10, stock,
                "2026-08-13T00:00:00Z", "Orbis", 3, null);
    }
}
