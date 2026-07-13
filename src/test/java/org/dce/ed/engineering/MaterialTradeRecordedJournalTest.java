package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Validates {@link MaterialTradeRateCalculator} against real {@code MaterialTrade} journal
 * events recorded in-game (fixture data; does not read live journal files).
 */
class MaterialTradeRecordedJournalTest {

    private record JournalTrade(
            String paidKey,
            String paidName,
            int paidQty,
            String receivedKey,
            String receivedName,
            int receivedQty) {
    }

    private static JournalTrade trade(
            String paidKey, String paidName, int paidQty,
            String receivedKey, String receivedName, int receivedQty) {
        return new JournalTrade(paidKey, paidName, paidQty, receivedKey, receivedName, receivedQty);
    }

    private static final JournalTrade[] RECORDED_TRADES = {
            trade("protolightalloys", "Proto Light Alloys", 3, "salvagedalloys", "Salvaged Alloys", 81), // Proto Light Alloys -> Salvaged Alloys (2025-10-23T04:00:57Z)
            trade("salvagedalloys", "Salvaged Alloys", 18, "gridresistors", "Grid Resistors", 3), // Salvaged Alloys -> Grid Resistors (2025-10-23T04:01:24Z)
            trade("salvagedalloys", "Salvaged Alloys", 36, "hybridcapacitors", "Hybrid Capacitors", 1), // Salvaged Alloys -> Hybrid Capacitors (2025-10-23T04:02:08Z)
            trade("protolightalloys", "Proto Light Alloys", 10, "salvagedalloys", "Salvaged Alloys", 270), // Proto Light Alloys -> Salvaged Alloys (2025-10-23T04:03:14Z)
            trade("protoradiolicalloys", "Proto Radiolic Alloys", 23, "protolightalloys", "Proto Light Alloys", 69), // Proto Radiolic Alloys -> Proto Light Alloys (2025-10-23T04:04:21Z)
            trade("salvagedalloys", "Salvaged Alloys", 36, "heatdispersionplate", "Heat Dispersion Plate", 1), // Salvaged Alloys -> Heat Dispersion Plate (2025-10-23T04:05:54Z)
            trade("salvagedalloys", "Salvaged Alloys", 216, "electrochemicalarrays", "Electrochemical Arrays", 1), // Salvaged Alloys -> Electrochemical Arrays (2025-10-23T04:06:49Z)
            trade("protolightalloys", "Proto Light Alloys", 36, "heatvanes", "Heat Vanes", 6), // Proto Light Alloys -> Heat Vanes (2025-10-23T04:07:24Z)
            trade("protolightalloys", "Proto Light Alloys", 12, "heatexchangers", "Heat Exchangers", 6), // Proto Light Alloys -> Heat Exchangers (2025-10-23T04:07:48Z)
            trade("protoradiolicalloys", "Proto Radiolic Alloys", 24, "chemicalprocessors", "Chemical Processors", 108), // Proto Radiolic Alloys -> Chemical Processors (2025-10-23T18:25:05Z)
            trade("chemicalprocessors", "Chemical Processors", 86, "salvagedalloys", "Salvaged Alloys", 43), // Chemical Processors -> Salvaged Alloys (2025-10-23T18:27:18Z)
            trade("protolightalloys", "Proto Light Alloys", 7, "salvagedalloys", "Salvaged Alloys", 189), // Proto Light Alloys -> Salvaged Alloys (2025-10-23T18:27:43Z)
            trade("salvagedalloys", "Salvaged Alloys", 216, "chemicaldistillery", "Chemical Distillery", 1), // Salvaged Alloys -> Chemical Distillery (2025-10-23T18:28:26Z)
            trade("chromium", "chromium", 7, "phosphorus", "phosphorus", 21), // chromium -> phosphorus (2025-10-24T19:35:38Z)
            trade("antimony", "antimony", 6, "arsenic", "arsenic", 9), // antimony -> arsenic (2025-10-24T20:42:40Z)
            trade("protolightalloys", "Proto Light Alloys", 16, "chemicaldistillery", "Chemical Distillery", 8), // Proto Light Alloys -> Chemical Distillery (2025-10-24T20:55:01Z)
            trade("protolightalloys", "Proto Light Alloys", 36, "chemicalmanipulators", "Chemical Manipulators", 6), // Proto Light Alloys -> Chemical Manipulators (2025-10-24T20:55:17Z)
            trade("protolightalloys", "Proto Light Alloys", 1, "galvanisingalloys", "Galvanising Alloys", 9), // Proto Light Alloys -> Galvanising Alloys (2025-10-24T21:37:58Z)
            trade("protolightalloys", "Proto Light Alloys", 40, "focuscrystals", "Focus Crystals", 20), // Proto Light Alloys -> Focus Crystals (2025-10-28T00:28:29Z)
            trade("heatvanes", "Heat Vanes", 2, "focuscrystals", "Focus Crystals", 1), // Heat Vanes -> Focus Crystals (2025-10-28T00:28:53Z)
            trade("salvagedalloys", "Salvaged Alloys", 36, "chemicalprocessors", "Chemical Processors", 1), // Salvaged Alloys -> Chemical Processors (2026-01-03T23:07:03Z)
            trade("fedproprietarycomposites", "Proprietary Composites", 8, "chemicalprocessors", "Chemical Processors", 12), // Proprietary Composites -> Chemical Processors (2026-01-04T20:02:12Z)
            trade("fedproprietarycomposites", "Proprietary Composites", 16, "chemicaldistillery", "Chemical Distillery", 8), // Proprietary Composites -> Chemical Distillery (2026-01-04T20:04:51Z)
            trade("fedproprietarycomposites", "Proprietary Composites", 24, "chemicalmanipulators", "Chemical Manipulators", 4), // Proprietary Composites -> Chemical Manipulators (2026-01-04T21:02:04Z)
            trade("chromium", "chromium", 24, "arsenic", "arsenic", 4), // chromium -> arsenic (2026-01-04T22:33:39Z)
            trade("fedproprietarycomposites", "Proprietary Composites", 36, "chemicalmanipulators", "Chemical Manipulators", 6), // Proprietary Composites -> Chemical Manipulators (2026-01-04T22:42:44Z)
            trade("fedcorecomposites", "Core Dynamics Composites", 16, "chemicalmanipulators", "Chemical Manipulators", 8), // Core Dynamics Composites -> Chemical Manipulators (2026-01-04T22:43:01Z)
            trade("fedcorecomposites", "Core Dynamics Composites", 8, "chemicaldistillery", "Chemical Distillery", 12), // Core Dynamics Composites -> Chemical Distillery (2026-01-05T20:12:15Z)
            trade("niobium", "niobium", 12, "manganese", "manganese", 6), // niobium -> manganese (2026-01-05T20:23:54Z)
            trade("tungsten", "tungsten", 8, "arsenic", "arsenic", 4), // tungsten -> arsenic (2026-01-05T20:24:18Z)
            trade("antimony", "antimony", 6, "arsenic", "arsenic", 9), // antimony -> arsenic (2026-01-05T20:24:52Z)
            trade("hyperspacetrajectories", "Eccentric Hyperspace Trajectories", 4, "legacyfirmware", "Specialised Legacy Firmware", 18), // Eccentric Hyperspace Trajectories -> Specialised Legacy Firmware (2026-02-03T19:05:18Z)
            trade("hyperspacetrajectories", "Eccentric Hyperspace Trajectories", 24, "dataminedwake", "Datamined Wake Exceptions", 4), // Eccentric Hyperspace Trajectories -> Datamined Wake Exceptions (2026-02-04T03:42:05Z)
            trade("fedcorecomposites", "Core Dynamics Composites", 8, "electrochemicalarrays", "Electrochemical Arrays", 12), // Core Dynamics Composites -> Electrochemical Arrays (2026-02-04T03:51:28Z)
            trade("fedcorecomposites", "Core Dynamics Composites", 4, "chemicalprocessors", "Chemical Processors", 18), // Core Dynamics Composites -> Chemical Processors (2026-02-04T03:52:49Z)
            trade("fedcorecomposites", "Core Dynamics Composites", 20, "chemicalmanipulators", "Chemical Manipulators", 10), // Core Dynamics Composites -> Chemical Manipulators (2026-02-04T03:54:52Z)
            trade("protoradiolicalloys", "Proto Radiolic Alloys", 1, "galvanisingalloys", "Galvanising Alloys", 27), // Proto Radiolic Alloys -> Galvanising Alloys (2026-02-05T15:36:22Z)
            trade("heatvanes", "Heat Vanes", 4, "mechanicalequipment", "Mechanical Equipment", 6), // Heat Vanes -> Mechanical Equipment (2026-02-05T18:52:19Z)
            trade("fedcorecomposites", "Core Dynamics Composites", 6, "mechanicalcomponents", "Mechanical Components", 9), // Core Dynamics Composites -> Mechanical Components (2026-02-05T18:52:35Z)
            trade("fedcorecomposites", "Core Dynamics Composites", 2, "mechanicalequipment", "Mechanical Equipment", 9), // Core Dynamics Composites -> Mechanical Equipment (2026-02-05T18:52:56Z)
            trade("hyperspacetrajectories", "Eccentric Hyperspace Trajectories", 4, "disruptedwakeechoes", "Atypical Disrupted Wake Echoes", 108), // Eccentric Hyperspace Trajectories -> Atypical Disrupted Wake Echoes (2026-02-06T03:56:52Z)
            trade("decodedemissiondata", "Decoded Emission Data", 6, "securityfirmware", "Security Firmware Patch", 1), // Decoded Emission Data -> Security Firmware Patch (2026-02-06T04:55:52Z)
            trade("shieldpatternanalysis", "Aberrant Shield Pattern Analysis", 6, "securityfirmware", "Security Firmware Patch", 1), // Aberrant Shield Pattern Analysis -> Security Firmware Patch (2026-02-06T04:56:09Z)
            trade("hyperspacetrajectories", "Eccentric Hyperspace Trajectories", 6, "securityfirmware", "Security Firmware Patch", 1), // Eccentric Hyperspace Trajectories -> Security Firmware Patch (2026-02-06T06:31:42Z)
            trade("fedproprietarycomposites", "Proprietary Composites", 18, "chemicalmanipulators", "Chemical Manipulators", 3), // Proprietary Composites -> Chemical Manipulators (2026-02-06T06:48:59Z)
            trade("fedcorecomposites", "Core Dynamics Composites", 4, "chemicalmanipulators", "Chemical Manipulators", 2), // Core Dynamics Composites -> Chemical Manipulators (2026-02-06T06:49:08Z)
            trade("fedcorecomposites", "Core Dynamics Composites", 2, "hybridcapacitors", "Hybrid Capacitors", 9), // Core Dynamics Composites -> Hybrid Capacitors (2026-02-06T06:52:03Z)
            trade("fedcorecomposites", "Core Dynamics Composites", 2, "mechanicalcomponents", "Mechanical Components", 3), // Core Dynamics Composites -> Mechanical Components (2026-02-06T06:54:35Z)
            trade("disruptedwakeechoes", "Atypical Disrupted Wake Echoes", 18, "legacyfirmware", "Specialised Legacy Firmware", 3), // Atypical Disrupted Wake Echoes -> Specialised Legacy Firmware (2026-02-06T07:06:13Z)
            trade("disruptedwakeechoes", "Atypical Disrupted Wake Echoes", 60, "legacyfirmware", "Specialised Legacy Firmware", 10), // Atypical Disrupted Wake Echoes -> Specialised Legacy Firmware (2026-02-06T07:59:31Z)
            trade("hyperspacetrajectories", "Eccentric Hyperspace Trajectories", 20, "industrialfirmware", "Cracked Industrial Firmware", 10), // Eccentric Hyperspace Trajectories -> Cracked Industrial Firmware (2026-07-12T19:14:39Z)
            trade("emissiondata", "Unexpected Emission Data", 10, "consumerfirmware", "Modified Consumer Firmware", 5), // Unexpected Emission Data -> Modified Consumer Firmware (2026-07-12T19:18:23Z)
            trade("hyperspacetrajectories", "Eccentric Hyperspace Trajectories", 6, "securityfirmware", "Security Firmware Patch", 1), // Eccentric Hyperspace Trajectories -> Security Firmware Patch (2026-07-12T19:25:16Z)
            trade("phasealloys", "Phase Alloys", 6, "chemicaldistillery", "Chemical Distillery", 1), // Phase Alloys -> Chemical Distillery (2026-07-12T19:41:11Z)
            trade("fedcorecomposites", "Core Dynamics Composites", 6, "exquisitefocuscrystals", "Exquisite Focus Crystals", 1), // Core Dynamics Composites -> Exquisite Focus Crystals (2026-07-12T19:42:11Z)
            trade("electrochemicalarrays", "Electrochemical Arrays", 1, "gridresistors", "Grid Resistors", 9), // Electrochemical Arrays -> Grid Resistors (2026-07-12T19:43:04Z)
            trade("precipitatedalloys", "Precipitated Alloys", 1, "heatresistantceramics", "Heat Resistant Ceramics", 3), // Precipitated Alloys -> Heat Resistant Ceramics (2026-07-12T19:44:57Z)
            trade("heatexchangers", "Heat Exchangers", 6, "hybridcapacitors", "Hybrid Capacitors", 3), // Heat Exchangers -> Hybrid Capacitors (2026-07-12T19:45:39Z)
            trade("galvanisingalloys", "Galvanising Alloys", 6, "hybridcapacitors", "Hybrid Capacitors", 1), // Galvanising Alloys -> Hybrid Capacitors (2026-07-12T19:46:33Z)
            trade("protolightalloys", "Proto Light Alloys", 60, "chemicalmanipulators", "Chemical Manipulators", 10), // Proto Light Alloys -> Chemical Manipulators (2026-07-13T03:08:47Z)
            trade("protoheatradiators", "Proto Heat Radiators", 24, "exquisitefocuscrystals", "Exquisite Focus Crystals", 4), // Proto Heat Radiators -> Exquisite Focus Crystals (2026-07-13T03:09:24Z)
            trade("emissiondata", "Unexpected Emission Data", 2, "legacyfirmware", "Specialised Legacy Firmware", 3), // Unexpected Emission Data -> Specialised Legacy Firmware (2026-07-13T03:21:50Z)
            trade("salvagedalloys", "Salvaged Alloys", 24, "galvanisingalloys", "Galvanising Alloys", 4), // Salvaged Alloys -> Galvanising Alloys (2026-07-13T05:29:36Z)
            trade("protolightalloys", "Proto Light Alloys", 8, "focuscrystals", "Focus Crystals", 4), // Proto Light Alloys -> Focus Crystals (2026-07-13T05:33:44Z)
            trade("protolightalloys", "Proto Light Alloys", 2, "focuscrystals", "Focus Crystals", 1), // Proto Light Alloys -> Focus Crystals (2026-07-13T05:34:00Z)
            trade("protoheatradiators", "Proto Heat Radiators", 10, "refinedfocuscrystals", "Refined Focus Crystals", 5) // Proto Heat Radiators -> Refined Focus Crystals (2026-07-13T05:34:49Z)
    };

    @Test
    void recordedJournalTrades_matchCalculatorRules() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        for (JournalTrade trade : RECORDED_TRADES) {
            String paidKey = EngineeringMaterialKeys.resolveKey(trade.paidKey(), trade.paidName(), db);
            String receivedKey = EngineeringMaterialKeys.resolveKey(
                    trade.receivedKey(), trade.receivedName(), db);

            EngineeringMaterial from = db.material(paidKey)
                    .orElseThrow(() -> new AssertionError("unknown paid material: " + trade.paidKey()));
            EngineeringMaterial to = db.material(receivedKey)
                    .orElseThrow(() -> new AssertionError("unknown received material: " + trade.receivedKey()));

            Optional<MaterialTradeRateCalculator.Exchange> planned =
                    MaterialTradeRateCalculator.planExchange(
                            from, to, trade.paidQty(), trade.receivedQty());

            assertTrue(planned.isPresent(),
                    () -> trade.paidName() + " -> " + trade.receivedName() + " not tradable");
            MaterialTradeRateCalculator.Exchange exchange = planned.get();
            assertEquals(trade.paidQty(), exchange.getFromCount(),
                    () -> trade.paidName() + " -> " + trade.receivedName() + " paid count");
            assertEquals(trade.receivedQty(), exchange.getToCount(),
                    () -> trade.paidName() + " -> " + trade.receivedName() + " received count");
        }
    }
}
