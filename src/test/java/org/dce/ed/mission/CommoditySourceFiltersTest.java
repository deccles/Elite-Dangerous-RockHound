package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommoditySourceFiltersTest {
    @Test
    void largePadStationFilterAcceptsOnlyLargeOrbitalStations() {
        CommoditySourceFilters filters = new CommoditySourceFilters(3, true, false, false);

        assertTrue(filters.matches(choice("Coriolis", 3, null)));
        assertTrue(filters.matches(choice("Coriolis", 3, 4L)));
        assertFalse(filters.matches(choice("Outpost", 2, null)));
        assertFalse(filters.matches(choice("SurfaceStation", 3, 4L)));
        assertFalse(filters.matches(choice("FleetCarrier", 3, null)));
    }

    @Test
    void locationCategoriesCanBeCombined() {
        CommoditySourceFilters filters = new CommoditySourceFilters(1, false, true, true);

        assertTrue(filters.matches(choice("Odyssey Settlement", 1, 7L)));
        assertTrue(filters.matches(choice("FleetCarrier", 3, null)));
        assertFalse(filters.matches(choice("Orbis", 3, null)));
    }

    private static CommoditySourceChoice choice(String type, int pad, Long bodyId) {
        return new CommoditySourceChoice("System", "Station", 1.0, 100.0, 10, 20, "now",
                type, pad, bodyId);
    }
}
