package org.dce.ed.ardent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArdentQueryParamsTest {

    @Test
    void toQueryString_buildsExpectedParams() {
        String q = new ArdentQueryParams()
                .minVolume(100)
                .minPrice(50000)
                .maxDistance(50)
                .maxDaysAgo(7)
                .fleetCarriers(Boolean.TRUE)
                .toQueryString();
        assertTrue(q.startsWith("?"));
        assertTrue(q.contains("minVolume=100"));
        assertTrue(q.contains("minPrice=50000"));
        assertTrue(q.contains("maxDistance=50"));
        assertTrue(q.contains("maxDaysAgo=7"));
        assertTrue(q.contains("fleetCarriers=1"));
    }

    @Test
    void encodePathSegment_spacesBecomePercent20() {
        assertEquals("Colonia", ArdentQueryParams.encodePathSegment("Colonia"));
        assertEquals("C%20Velorum", ArdentQueryParams.encodePathSegment("C Velorum"));
    }
}
