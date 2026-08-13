package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CommoditySourceSearchTest {
    @Test
    void queryDoesNotExcludeSellersBelowMissionRequirement() {
        String query = CommoditySourceSearch.queryParams(5916).toQueryString();

        assertTrue(query.contains("minVolume=1"));
    }

    @Test
    void radiusQueryLimitsResultsBeforeArdentAppliesItsCap() {
        String query = CommoditySourceSearch.queryParams(5916, 50).toQueryString();

        assertTrue(query.contains("maxDistance=50"));
    }

    @Test
    void commodityApiName_convertsLocalizedDisplayNameToEliteKey() {
        assertEquals("nonlethalweapons", CommoditySourceSearch.commodityApiName("Non-lethal weapons"));
    }

    @Test
    void parse_acceptsDataEnvelopeAndSortsNearestFirst() throws Exception {
        String json = "{\"data\":["
                + "{\"systemName\":\"Far\",\"stationName\":\"B\",\"distance\":9.0},"
                + "{\"systemName\":\"Near\",\"stationName\":\"A\",\"distance\":2.5,"
                + "\"distanceToArrival\":450,\"buyPrice\":9234,\"stock\":500,"
                + "\"stationType\":\"Coriolis\",\"maxLandingPadSize\":3,\"bodyId\":null,"
                + "\"updatedAt\":\"2026-08-12T12:00:00Z\"}]}";

        var rows = CommoditySourceSearch.parse(json);

        assertEquals("A", rows.get(0).station());
        assertEquals("Near", rows.get(0).system());
        assertEquals(9234, rows.get(0).price());
        assertEquals("Coriolis", rows.get(0).stationType());
        assertEquals(3, rows.get(0).maxLandingPadSize());
        assertEquals("B", rows.get(1).station());
    }

    @Test
    void mergeIncludesOriginSystemSellerMissingFromNearbyResultsWithoutDuplicates() {
        var nearby = new CommoditySourceChoice("Lave", "Lave Station", 3.0, 100.0,
                100, 50, null, "Orbis", 3, null);
        var gilmore = new CommoditySourceChoice("Core Sys Sector EW-N a6-1", "Gilmore Legacy",
                null, 3684.8, 1699, 6225, null, "Coriolis", 3, null);

        var rows = CommoditySourceSearch.mergeNearbyAndOrigin(List.of(nearby, gilmore), List.of(gilmore));

        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(row -> row.station().equals("Gilmore Legacy")));
    }
}
