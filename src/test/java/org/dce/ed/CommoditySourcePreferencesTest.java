package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommoditySourcePreferencesTest {
    @Test
    void sourceSearchChoicesRoundTrip() {
        int pad = OverlayPreferences.getCommoditySourceMinimumPadSize();
        boolean stations = OverlayPreferences.isCommoditySourceStationsIncluded();
        boolean planetary = OverlayPreferences.isCommoditySourcePlanetaryBasesIncluded();
        boolean carriers = OverlayPreferences.isCommoditySourceFleetCarriersIncluded();
        try {
            OverlayPreferences.setCommoditySourceMinimumPadSize(2);
            OverlayPreferences.setCommoditySourceStationsIncluded(false);
            OverlayPreferences.setCommoditySourcePlanetaryBasesIncluded(true);
            OverlayPreferences.setCommoditySourceFleetCarriersIncluded(true);

            assertEquals(2, OverlayPreferences.getCommoditySourceMinimumPadSize());
            assertFalse(OverlayPreferences.isCommoditySourceStationsIncluded());
            assertTrue(OverlayPreferences.isCommoditySourcePlanetaryBasesIncluded());
            assertTrue(OverlayPreferences.isCommoditySourceFleetCarriersIncluded());
        } finally {
            OverlayPreferences.setCommoditySourceMinimumPadSize(pad);
            OverlayPreferences.setCommoditySourceStationsIncluded(stations);
            OverlayPreferences.setCommoditySourcePlanetaryBasesIncluded(planetary);
            OverlayPreferences.setCommoditySourceFleetCarriersIncluded(carriers);
            OverlayPreferences.flushBackingStore();
        }
    }
}
