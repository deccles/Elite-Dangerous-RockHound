package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MissionDestinationResolverTest {

    @Test
    void commodityObjective_isAmountAndCommodity() {
        MissionRecord r = new MissionRecord(1L);
        r.setName("Mission_Mining_Boom");
        r.setCommodityLocalised("Bromellite");
        r.setCountRequired(36);
        MissionDestination obj = MissionDestinationResolver.objectiveFor(r);
        assertEquals("36 Bromellite", obj.displayLine());
    }

    @Test
    void commodityTurnIn_usesDestinationStation() {
        MissionRecord r = new MissionRecord(2L);
        r.setName("Mission_Mining_Boom");
        r.setCommodityLocalised("Osmium");
        r.setDestinationSystem("Coeus");
        r.setDestinationStation("Foster Terminal");
        MissionDestination turnIn = MissionDestinationResolver.turnInFor(r);
        assertEquals("Coeus / Foster Terminal", turnIn.displayLine());
    }

    @Test
    void courierUsesSameDestinationForBoth() {
        MissionRecord r = new MissionRecord(3L);
        r.setName("Mission_Courier");
        r.setDestinationSystem("Tenjin");
        r.setDestinationStation("Balakor's Beacon");
        MissionDestination obj = MissionDestinationResolver.objectiveFor(r);
        MissionDestination turnIn = MissionDestinationResolver.turnInFor(r);
        assertEquals("Tenjin / Balakor's Beacon", obj.displayLine());
        assertEquals(turnIn.displayLine(), obj.displayLine());
    }
}
