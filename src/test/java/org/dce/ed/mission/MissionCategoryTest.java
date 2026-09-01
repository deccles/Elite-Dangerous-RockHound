package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MissionCategoryTest {
    @Test
    void fromMissionName_suffixlessMiningIsCommodity() {
        assertEquals(MissionCategory.COMMODITY, MissionCategory.fromMissionName("Mission_Mining"));
        assertEquals(MissionCategory.COMMODITY, MissionCategory.fromMissionName("Mission_Mining_name"));
    }

    @Test
    void fromMissionName_suffixedMiningIsCommodity() {
        assertEquals(MissionCategory.COMMODITY, MissionCategory.fromMissionName("Mission_Mining_Boom"));
        assertEquals(MissionCategory.COMMODITY, MissionCategory.fromMissionName("Mission_Mining_Boom_name"));
    }

    @Test
    void fromMissionName_deliveryIsCommodityWithOrWithoutSuffix() {
        assertEquals(MissionCategory.COMMODITY, MissionCategory.fromMissionName("Mission_Delivery"));
        assertEquals(MissionCategory.COMMODITY, MissionCategory.fromMissionName("Mission_Delivery_RankEmp"));
    }

    @Test
    void fromMissionName_collectAndSourcedRemainCommodity() {
        assertEquals(MissionCategory.COMMODITY, MissionCategory.fromMissionName("Mission_Collect_Outbreak"));
        assertEquals(MissionCategory.COMMODITY, MissionCategory.fromMissionName("Mission_Sourced_Boom"));
    }

    @Test
    void fromMissionName_nonCommodityCategoriesUnaffected() {
        assertEquals(MissionCategory.COURIER, MissionCategory.fromMissionName("Mission_Courier_Boom"));
        assertEquals(MissionCategory.DONATION, MissionCategory.fromMissionName("Mission_AltruismCredits_Outbreak"));
        assertEquals(MissionCategory.PASSENGER, MissionCategory.fromMissionName("Mission_PassengerBulk"));
        assertEquals(MissionCategory.COMBAT, MissionCategory.fromMissionName("Mission_Massacre"));
        assertEquals(MissionCategory.COMBAT, MissionCategory.fromMissionName("Mission_MassacreWing_Legal_Military"));
        assertEquals(MissionCategory.COMBAT, MissionCategory.fromMissionName("Mission_Assassinate_Planetary"));
        assertEquals(MissionCategory.UNKNOWN, MissionCategory.fromMissionName("Mission_Salvage_Wing"));
        assertEquals(MissionCategory.UNKNOWN, MissionCategory.fromMissionName(null));
    }

    @Test
    void isTransport_includesCargoCourierDonationAndPassenger() {
        assertTrue(MissionCategory.COMMODITY.isTransport());
        assertTrue(MissionCategory.COURIER.isTransport());
        assertTrue(MissionCategory.PASSENGER.isTransport());
        assertTrue(!MissionCategory.COMBAT.isTransport());
        assertTrue(MissionCategory.DONATION.isTransport());
        assertTrue(!MissionCategory.UNKNOWN.isTransport());
    }

    @Test
    void killCountOnUnknownNameIsCombat() {
        MissionRecord r = new MissionRecord(1063825808L);
        r.setName("Mission_UnknownStrike");
        r.setKillCount(42);
        assertEquals(MissionCategory.COMBAT, r.getCategory());
    }

    /** A bare {@code Mission_Mining} must reach the commodity summary panel, which gates on this flag. */
    @Test
    void suffixlessMiningMissionCountsAsCommodityMission() {
        MissionRecord r = new MissionRecord(1062189448L);
        r.setName("Mission_Mining");
        r.setLocalisedName("Mine 175 Units of Indite");
        r.setCommodityLocalised("Indite");
        r.setCountRequired(175);

        assertEquals(MissionCategory.COMMODITY, r.getCategory());
        assertEquals("Cargo", r.getCategory().displayLabel());
        assertTrue(r.isCommodityMission());
    }
}
