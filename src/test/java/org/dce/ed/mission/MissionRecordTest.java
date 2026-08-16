package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MissionRecordTest {

    @Test
    void shortSummaryLine_stripsCommoditySuffix() {
        MissionRecord r = new MissionRecord(1L);
        r.setLocalisedName("Mining Rush for 28 Units of Bromellite");
        assertEquals("Mining Rush", r.shortSummaryLine());
    }

    @Test
    void miningMissionCanUseAManuallySelectedCommoditySource() {
        MissionRecord r = new MissionRecord(2L);
        r.setName("Mission_Mining_Boom");

        assertTrue(r.isManuallySourceableCommodityMission());
    }
}
