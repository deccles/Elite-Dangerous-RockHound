package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.ShipTargetedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end: journal line → {@link EliteLogParser} → {@link BountyScanTracker}.
 */
class BountyScanJournalFlowTest {

    private final EliteLogParser parser = new EliteLogParser();

    @BeforeEach
    void reset() {
        BountyScanTracker.getInstance().resetSession();
        OverlayPreferences.setBountyScanFirstAnnouncementEnabled(true);
        OverlayPreferences.setBountyScanAdditionalAnnouncementEnabled(true);
    }

    @Test
    void journalCarlosScanSequenceProducesFirstThenAdditionalSpeech() {
        String first = "{ \"timestamp\":\"2026-06-22T13:04:47Z\", \"event\":\"ShipTargeted\", "
                + "\"TargetLocked\":true, \"Ship\":\"viper\", \"ScanStage\":3, "
                + "\"PilotName\":\"$npc_name_decorate:#name=Carlos SpicyWeiner;\", "
                + "\"PilotName_Localised\":\"Carlos SpicyWeiner\", \"LegalStatus\":\"Wanted\", \"Bounty\":242475 }";
        String kws = "{ \"timestamp\":\"2026-06-22T13:05:14Z\", \"event\":\"ShipTargeted\", "
                + "\"TargetLocked\":true, \"Ship\":\"viper\", \"ScanStage\":3, "
                + "\"PilotName\":\"$npc_name_decorate:#name=Carlos SpicyWeiner;\", "
                + "\"PilotName_Localised\":\"Carlos SpicyWeiner\", \"LegalStatus\":\"Wanted\", \"Bounty\":305335 }";

        BountyScanTracker tracker = BountyScanTracker.getInstance();
        assertTrue(tracker.onShipTargeted(parseShipTargeted(first)).isPresent());
        assertTrue(tracker.onShipTargeted(parseShipTargeted(kws)).isPresent());
    }

    @Test
    void journalCleanStage3AndZeroBountyAreIgnored() {
        String clean = "{ \"timestamp\":\"2026-06-22T13:04:18Z\", \"event\":\"ShipTargeted\", "
                + "\"TargetLocked\":true, \"Ship\":\"type7\", \"ScanStage\":3, "
                + "\"PilotName_Localised\":\"Francisco Alberto Serrano Acosta\", \"LegalStatus\":\"Clean\" }";
        String zeroBounty = "{ \"timestamp\":\"2026-06-22T14:08:58Z\", \"event\":\"ShipTargeted\", "
                + "\"TargetLocked\":true, \"Ship\":\"asp_scout\", \"ScanStage\":3, "
                + "\"PilotName_Localised\":\"Wicks\", \"LegalStatus\":\"Clean\", \"Bounty\":0 }";

        BountyScanTracker tracker = BountyScanTracker.getInstance();
        assertFalse(tracker.onShipTargeted(parseShipTargeted(clean)).isPresent());
        assertFalse(tracker.onShipTargeted(parseShipTargeted(zeroBounty)).isPresent());
    }

    @Test
    void pilotKeyMatchesLocalisedAndDecoratedTokens() {
        assertEquals("Carlos SpicyWeiner", BountyScanTracker.pilotKey("Carlos SpicyWeiner"));
        assertEquals("Carlos SpicyWeiner",
                BountyScanTracker.pilotKey("$npc_name_decorate:#name=Carlos SpicyWeiner;"));
    }

    @Test
    void kwsAnnouncesWhenFirstAnnouncementPrefDisabled() {
        OverlayPreferences.setBountyScanFirstAnnouncementEnabled(false);
        OverlayPreferences.setBountyScanAdditionalAnnouncementEnabled(true);
        BountyScanTracker tracker = BountyScanTracker.getInstance();

        assertFalse(tracker.onShipTargeted(stage3("Stephanie Walruswings", 242_749L)).isPresent());
        assertTrue(tracker.onShipTargeted(stage3("Stephanie Walruswings", 352_283L)).isPresent());
    }

    private ShipTargetedEvent parseShipTargeted(String line) {
        EliteLogEvent event = parser.parseRecord(line);
        assertInstanceOf(ShipTargetedEvent.class, event);
        return (ShipTargetedEvent) event;
    }

    private static ShipTargetedEvent stage3(String pilot, long bounty) {
        return new ShipTargetedEvent(java.time.Instant.parse("2026-06-22T14:09:51Z"),
                new com.google.gson.JsonObject(), true, 3, pilot, Long.valueOf(bounty));
    }
}
