package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CommoditySourceSearchTest {
    @Test
    void parse_acceptsDataEnvelopeAndSortsNearestFirst() throws Exception {
        String json = "{\"data\":["
                + "{\"systemName\":\"Far\",\"stationName\":\"B\",\"distance\":9.0},"
                + "{\"systemName\":\"Near\",\"stationName\":\"A\",\"distance\":2.5,"
                + "\"distanceToArrival\":450,\"buyPrice\":9234,\"stock\":500,"
                + "\"updatedAt\":\"2026-08-12T12:00:00Z\"}]}";

        var rows = CommoditySourceSearch.parse(json);

        assertEquals("A", rows.get(0).station());
        assertEquals("Near", rows.get(0).system());
        assertEquals(9234, rows.get(0).price());
        assertEquals("B", rows.get(1).station());
    }
}
