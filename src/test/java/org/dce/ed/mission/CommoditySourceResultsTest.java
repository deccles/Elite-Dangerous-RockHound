package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class CommoditySourceResultsTest {
    @Test
    void mergeDeduplicatesStationsAndSortsNearestFirst() {
        CommoditySourceResults results = new CommoditySourceResults();
        results.merge(List.of(choice("Far", "B", 20), choice("Near", "A", 5)));
        results.merge(List.of(choice("Near", "A", 5), choice("Middle", "C", 10)));

        assertEquals(List.of("A", "C", "B"), results.rows().stream().map(CommoditySourceChoice::station).toList());
    }

    @Test
    void cappedQueryRetriesAtHalfRadius() {
        assertEquals(25, CommoditySourceResults.radiusAfterCappedResponse(50));
    }

    @Test
    void scrollingExpandsRadiusByTwentyFiveLightYears() {
        assertEquals(75, CommoditySourceResults.nextRadius(50));
    }

    private static CommoditySourceChoice choice(String system, String station, double distance) {
        return new CommoditySourceChoice(system, station, distance, 100.0, 10, 20, "now",
                "Coriolis", 3, null);
    }
}
