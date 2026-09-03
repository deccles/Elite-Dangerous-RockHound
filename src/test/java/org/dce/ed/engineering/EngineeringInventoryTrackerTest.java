package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Instant;
import java.util.List;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.MaterialCollectedEvent;
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
    void materialCollected_consumerFirmware_mapsToCatalogKey() {
        EngineeringInventoryTracker tracker = new EngineeringInventoryTracker();
        String json = """
                {
                  "timestamp": "2026-07-13T19:55:49Z",
                  "event": "MaterialCollected",
                  "Category": "Encoded",
                  "Name": "consumerfirmware",
                  "Name_Localised": "Modified Consumer Firmware",
                  "Count": 3
                }
                """;
        MaterialCollectedEvent collected = assertInstanceOf(MaterialCollectedEvent.class, parser.parseRecord(json));
        tracker.applyEvent(collected);

        assertEquals(3, tracker.getCount("modifiedconsumerfirmware"));
        assertEquals(0, tracker.getCount("consumerfirmware"));
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

    @Test
    void engineerContribution_material_decrementsInventory() {
        EngineeringInventoryTracker tracker = new EngineeringInventoryTracker();
        tracker.applyEvent(materialsSnapshot(
                List.of(new MaterialStack("arsenic", "", 10)),
                List.of(),
                List.of()));

        String json = """
                {
                  "timestamp": "2017-05-24T10:41:51Z",
                  "event": "EngineerContribution",
                  "Engineer": "Elvira Martuuk",
                  "EngineerID": 300160,
                  "Type": "Material",
                  "Material": "arsenic",
                  "Quantity": 2,
                  "TotalQuantity": 3
                }
                """;
        tracker.applyEvent(assertInstanceOf(
                org.dce.ed.logreader.event.EngineerContributionEvent.class,
                parser.parseRecord(json)));

        assertEquals(8, tracker.getCount("arsenic"));
    }

    @Test
    void engineerContribution_commodity_doesNotChangeMaterialInventory() {
        EngineeringInventoryTracker tracker = new EngineeringInventoryTracker();
        tracker.applyEvent(materialsSnapshot(
                List.of(new MaterialStack("arsenic", "", 10)),
                List.of(),
                List.of()));

        String json = """
                {
                  "timestamp": "2017-05-24T10:41:51Z",
                  "event": "EngineerContribution",
                  "Engineer": "Elvira Martuuk",
                  "EngineerID": 300160,
                  "Type": "Commodity",
                  "Commodity": "soontillrelics",
                  "Quantity": 2,
                  "TotalQuantity": 3
                }
                """;
        tracker.applyEvent(parser.parseRecord(json));

        assertEquals(10, tracker.getCount("arsenic"));
    }

    @Test
    void statistics_setsMercCoinBalance() {
        EngineeringInventoryTracker tracker = new EngineeringInventoryTracker();
        tracker.applyEvent(parser.parseRecord("""
                {
                  "timestamp": "2026-09-01T15:30:12Z",
                  "event": "Statistics",
                  "Bank_Account": {
                    "Current_Wealth": 1,
                    "MercCoins_Current": 42
                  }
                }
                """));
        assertEquals(42, tracker.getCount("merccoins"));
    }

    @Test
    void materialsSnapshot_preservesMercCoinBalance() {
        EngineeringInventoryTracker tracker = new EngineeringInventoryTracker();
        tracker.applyEvent(parser.parseRecord("""
                {
                  "timestamp": "2026-09-01T15:30:12Z",
                  "event": "Statistics",
                  "Bank_Account": { "MercCoins_Current": 42 }
                }
                """));
        tracker.applyEvent(materialsSnapshot(
                List.of(new MaterialStack("arsenic", "", 3)),
                List.of(),
                List.of()));
        assertEquals(42, tracker.getCount("merccoins"));
        assertEquals(3, tracker.getCount("arsenic"));
    }

    @Test
    void statisticsWithoutMercCoinsField_leavesBalanceUnchanged() {
        EngineeringInventoryTracker tracker = new EngineeringInventoryTracker();
        tracker.applyEvent(parser.parseRecord("""
                {
                  "timestamp": "2026-09-01T15:30:12Z",
                  "event": "Statistics",
                  "Bank_Account": { "MercCoins_Current": 42 }
                }
                """));
        tracker.applyEvent(parser.parseRecord("""
                {
                  "timestamp": "2017-09-25T15:18:31Z",
                  "event": "Statistics",
                  "Bank_Account": { "Current_Wealth": 148827050 }
                }
                """));
        assertEquals(42, tracker.getCount("merccoins"));
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
