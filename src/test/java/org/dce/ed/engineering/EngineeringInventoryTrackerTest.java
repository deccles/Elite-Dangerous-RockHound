package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Instant;
import java.util.List;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.MaterialTradeEvent;
import org.dce.ed.logreader.event.MaterialsEvent;
import org.dce.ed.logreader.event.MaterialStack;
import org.junit.jupiter.api.Test;

class EngineeringInventoryTrackerTest {

    private final EliteLogParser parser = new EliteLogParser();

    @Test
    void materialTrade_updatesInventoryForPaidAndReceived() {
        EngineeringInventoryTracker tracker = new EngineeringInventoryTracker();
        tracker.applyEvent(materialsSnapshot(
                List.of(),
                List.of(),
                List.of(
                        new MaterialStack("eccentrichyperspacetrajectories", "", 33),
                        new MaterialStack("crackedindustrialfirmware", "", 0))));

        String json = """
                {
                  "timestamp": "2026-07-12T20:00:00Z",
                  "event": "MaterialTrade",
                  "TraderType": "encoded",
                  "Paid": {
                    "Material": "eccentrichyperspacetrajectories",
                    "Material_Localised": "Eccentric Hyperspace Trajectories",
                    "Category": "Encoded",
                    "Quantity": 36
                  },
                  "Received": {
                    "Material": "crackedindustrialfirmware",
                    "Material_Localised": "Cracked Industrial Firmware",
                    "Category": "Encoded",
                    "Quantity": 1
                  }
                }
                """;
        MaterialTradeEvent trade = assertInstanceOf(MaterialTradeEvent.class, parser.parseRecord(json));
        tracker.applyEvent(trade);

        assertEquals(0, tracker.getCount("eccentrichyperspacetrajectories"));
        assertEquals(1, tracker.getCount("crackedindustrialfirmware"));
    }

    @Test
    void materialTrade_localisedOnlyNames_stillResolveInventoryKeys() {
        EngineeringInventoryTracker tracker = new EngineeringInventoryTracker();
        tracker.applyEvent(materialsSnapshot(
                List.of(),
                List.of(),
                List.of(new MaterialStack("eccentrichyperspacetrajectories", "", 36))));

        String json = """
                {
                  "timestamp": "2026-07-12T20:01:00Z",
                  "event": "MaterialTrade",
                  "TraderType": "encoded",
                  "Paid": {
                    "Material_Localised": "Eccentric Hyperspace Trajectories",
                    "Category": "Encoded",
                    "Quantity": 36
                  },
                  "Received": {
                    "Material_Localised": "Cracked Industrial Firmware",
                    "Category": "Encoded",
                    "Quantity": 1
                  }
                }
                """;
        MaterialTradeEvent trade = assertInstanceOf(MaterialTradeEvent.class, parser.parseRecord(json));
        tracker.applyEvent(trade);

        assertEquals(0, tracker.getCount("eccentrichyperspacetrajectories"));
        assertEquals(1, tracker.getCount("crackedindustrialfirmware"));
    }

    private static MaterialsEvent materialsSnapshot(List<MaterialStack> raw,
                                                    List<MaterialStack> manufactured,
                                                    List<MaterialStack> encoded) {
        return new MaterialsEvent(
                Instant.parse("2026-07-12T19:00:00Z"),
                null,
                raw,
                manufactured,
                encoded);
    }
}
