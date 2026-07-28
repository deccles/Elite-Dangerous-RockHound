package org.dce.ed.logreader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.logreader.event.ShipTargetedEvent;
import org.junit.jupiter.api.Test;

class ShipTargetedEventParsingTest {

    private final EliteLogParser parser = new EliteLogParser();

    @Test
    void parsesStage3BountyFromJournalLine() {
        String line = "{ \"timestamp\":\"2026-06-22T13:04:47Z\", \"event\":\"ShipTargeted\", "
                + "\"TargetLocked\":true, \"Ship\":\"viper\", \"ScanStage\":3, "
                + "\"PilotName\":\"$npc_name_decorate:#name=Carlos SpicyWeiner;\", "
                + "\"PilotName_Localised\":\"Carlos SpicyWeiner\", \"Bounty\":242475 }";
        EliteLogEvent event = parser.parseRecord(line);
        assertInstanceOf(ShipTargetedEvent.class, event);
        ShipTargetedEvent st = (ShipTargetedEvent) event;
        assertEquals(3, st.getScanStage());
        assertEquals("Carlos SpicyWeiner", st.getPilotName());
        assertEquals(242_475L, st.getBounty().longValue());
        assertEquals("viper", st.getShip());
        assertFalse(st.isPlayer());
    }

    @Test
    void parsesStage3WithoutBounty() {
        String line = "{ \"timestamp\":\"2026-06-22T13:07:57Z\", \"event\":\"ShipTargeted\", "
                + "\"TargetLocked\":true, \"Ship\":\"adder\", \"ScanStage\":3, "
                + "\"PilotName_Localised\":\"DJNoNo Ulysses\", \"LegalStatus\":\"Clean\" }";
        ShipTargetedEvent st = (ShipTargetedEvent) parser.parseRecord(line);
        assertNull(st.getBounty());
        assertEquals("Clean", st.getLegalStatus());
    }

    @Test
    void parsesExplicitZeroBountyAsNull() {
        String line = "{ \"timestamp\":\"2026-06-22T14:08:58Z\", \"event\":\"ShipTargeted\", "
                + "\"TargetLocked\":true, \"Ship\":\"asp_scout\", \"ScanStage\":3, "
                + "\"PilotName_Localised\":\"Wicks\", \"LegalStatus\":\"Clean\", \"Bounty\":0 }";
        ShipTargetedEvent st = (ShipTargetedEvent) parser.parseRecord(line);
        assertNull(st.getBounty());
    }

    @Test
    void parsesHealthFactionRankAndHostile() {
        String line = "{ \"timestamp\":\"2026-06-22T13:04:47Z\", \"event\":\"ShipTargeted\", "
                + "\"TargetLocked\":true, \"Ship\":\"ferdelance\", \"Ship_Localised\":\"Fer-de-Lance\", "
                + "\"ScanStage\":3, \"PilotName\":\"$npc_name_decorate:#name=Hostile Pilot;\", "
                + "\"PilotName_Localised\":\"Hostile Pilot\", \"PilotRank\":\"Dangerous\", "
                + "\"ShieldHealth\":42.5, \"HullHealth\":88.0, \"Faction\":\"Some Faction\", "
                + "\"LegalStatus\":\"Hostile\", \"Bounty\":100000 }";
        ShipTargetedEvent st = (ShipTargetedEvent) parser.parseRecord(line);
        assertEquals("Fer-de-Lance", st.getShipDisplayName());
        assertEquals("Dangerous", st.getPilotRank());
        assertEquals("Some Faction", st.getFaction());
        assertEquals("Hostile", st.getLegalStatus());
        assertEquals(42.5, st.getShieldHealth(), 0.01);
        assertEquals(88.0, st.getHullHealth(), 0.01);
        assertFalse(st.isPlayer());
    }

    @Test
    void detectsPlayerFromPlainPilotName() {
        String line = "{ \"timestamp\":\"2026-06-22T13:04:47Z\", \"event\":\"ShipTargeted\", "
                + "\"TargetLocked\":true, \"Ship\":\"anaconda\", \"ScanStage\":2, "
                + "\"PilotName\":\"CMDR Example\", \"PilotName_Localised\":\"CMDR Example\", "
                + "\"ShieldHealth\":100.0, \"HullHealth\":100.0 }";
        ShipTargetedEvent st = (ShipTargetedEvent) parser.parseRecord(line);
        assertTrue(st.isPlayer());
    }

    @Test
    void detectPlayerHelperUsesSquadronId() {
        assertTrue(ShipTargetedEvent.detectPlayer("$npc_name_decorate:#name=X;", "SQUAD"));
        assertFalse(ShipTargetedEvent.detectPlayer("$npc_name_decorate:#name=X;", null));
        assertTrue(ShipTargetedEvent.detectPlayer("Real Player", null));
    }
}
