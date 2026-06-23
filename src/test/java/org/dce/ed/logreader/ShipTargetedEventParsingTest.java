package org.dce.ed.logreader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    }

    @Test
    void parsesStage3WithoutBounty() {
        String line = "{ \"timestamp\":\"2026-06-22T13:07:57Z\", \"event\":\"ShipTargeted\", "
                + "\"TargetLocked\":true, \"Ship\":\"adder\", \"ScanStage\":3, "
                + "\"PilotName_Localised\":\"DJNoNo Ulysses\", \"LegalStatus\":\"Clean\" }";
        ShipTargetedEvent st = (ShipTargetedEvent) parser.parseRecord(line);
        assertNull(st.getBounty());
    }

    @Test
    void parsesExplicitZeroBountyAsNull() {
        String line = "{ \"timestamp\":\"2026-06-22T14:08:58Z\", \"event\":\"ShipTargeted\", "
                + "\"TargetLocked\":true, \"Ship\":\"asp_scout\", \"ScanStage\":3, "
                + "\"PilotName_Localised\":\"Wicks\", \"LegalStatus\":\"Clean\", \"Bounty\":0 }";
        ShipTargetedEvent st = (ShipTargetedEvent) parser.parseRecord(line);
        assertNull(st.getBounty());
    }
}
