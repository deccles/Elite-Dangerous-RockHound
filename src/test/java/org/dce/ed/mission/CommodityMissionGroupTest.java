package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CommodityMissionGroupTest {

    @Test
    void totalGathered_sumsHoldAndDelivered() {
        CommodityMissionGroup g = new CommodityMissionGroup(
                "Bromellite", 2, 100, 10, 11,
                new MissionDestination("Coeus", "Foster Terminal", null),
                false, null, List.of());
        assertEquals(21, g.totalGathered());
        assertFalse(g.hasEnoughGathered());
    }

    @Test
    void hasEnough_whenHoldPlusDeliveredMeetsTotal() {
        CommodityMissionGroup g = new CommodityMissionGroup(
                "Osmium", 1, 50, 40, 10,
                new MissionDestination("Coeus", "Foster Terminal", null),
                false, null, List.of());
        assertEquals(50, g.totalGathered());
        assertTrue(g.hasEnoughGathered());
    }
}
