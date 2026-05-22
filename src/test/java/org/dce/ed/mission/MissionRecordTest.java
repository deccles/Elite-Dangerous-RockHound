package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MissionRecordTest {

    @Test
    void shortSummaryLine_stripsCommoditySuffix() {
        MissionRecord r = new MissionRecord(1L);
        r.setLocalisedName("Mining Rush for 28 Units of Bromellite");
        assertEquals("Mining Rush", r.shortSummaryLine());
    }
}
