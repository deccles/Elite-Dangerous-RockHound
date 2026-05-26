package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.ScanEvent;
import org.junit.jupiter.api.Test;

class SystemTabFirstDiscoveryAnnouncementTest {

    private static final String ISO_TS = "2026-05-26T20:00:00Z";

    private final EliteLogParser parser = new EliteLogParser();

    @Test
    void firstDiscoveredPrimaryStarAtArrivalTriggersAnnouncement() {
        ScanEvent scan = parseScan("Dryi Aug AA-A h1 A", "Dryi Aug AA-A h1", 1, 12345L, 0.0, "K", false);

        assertTrue(SystemTabPanel.isFirstDiscoveredPrimaryStarScan(scan));
    }

    @Test
    void companionStarAwayFromArrivalDoesNotTriggerSystemAnnouncement() {
        ScanEvent scan = parseScan("Dryi Aug AA-A h1 B", "Dryi Aug AA-A h1", 8, 12345L, 930.0, "M", false);

        assertFalse(SystemTabPanel.isFirstDiscoveredPrimaryStarScan(scan));
    }

    @Test
    void discoveredPrimaryStarDoesNotTriggerAnnouncement() {
        ScanEvent scan = parseScan("Dryi Aug AA-A h1 A", "Dryi Aug AA-A h1", 1, 12345L, 0.0, "K", true);

        assertFalse(SystemTabPanel.isFirstDiscoveredPrimaryStarScan(scan));
    }

    @Test
    void planetWithFirstDiscoveredFlagDoesNotTriggerSystemAnnouncement() {
        ScanEvent scan = parseScan("Dryi Aug AA-A h1 1", "Dryi Aug AA-A h1", 9, 12345L, 500.0, null, false);

        assertFalse(SystemTabPanel.isFirstDiscoveredPrimaryStarScan(scan));
    }

    private ScanEvent parseScan(String bodyName,
                                String starSystem,
                                int bodyId,
                                long systemAddress,
                                double distanceFromArrivalLs,
                                String starType,
                                boolean wasDiscovered) {
        StringBuilder json = new StringBuilder();
        json.append("{\"event\":\"Scan\",\"timestamp\":\"").append(ISO_TS).append("\"")
                .append(",\"BodyName\":\"").append(escape(bodyName)).append("\"")
                .append(",\"BodyID\":").append(bodyId)
                .append(",\"StarSystem\":\"").append(escape(starSystem)).append("\"")
                .append(",\"SystemAddress\":").append(systemAddress)
                .append(",\"DistanceFromArrivalLS\":").append(distanceFromArrivalLs)
                .append(",\"WasDiscovered\":").append(wasDiscovered);
        if (starType == null) {
            json.append(",\"PlanetClass\":\"Icy body\"");
        } else {
            json.append(",\"StarType\":\"").append(escape(starType)).append("\"");
        }
        json.append("}");
        return (ScanEvent) parser.parseRecord(json.toString());
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
