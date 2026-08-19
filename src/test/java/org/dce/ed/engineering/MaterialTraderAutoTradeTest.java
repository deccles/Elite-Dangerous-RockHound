package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.dce.ed.logreader.event.MaterialTradeEvent;

import com.google.gson.JsonObject;

class MaterialTraderAutoTradeTest {

    @Test
    void encoded_emissionDataGrade1_isOrigin() {
        MaterialTraderScreenLayout layout = MaterialTraderScreenLayout.getInstance();
        EngineeringMaterial mat = new EngineeringMaterial(
                "unexpectedemissiondata", "x", "Encoded", "EmissionData", 1);
        var pos = layout.position(mat).orElseThrow();
        assertEquals(0, pos.row());
        assertEquals(0, pos.col());
    }

    @Test
    void encoded_firmwareGrade5_isLastRowLastCol() {
        MaterialTraderScreenLayout layout = MaterialTraderScreenLayout.getInstance();
        EngineeringMaterial mat = new EngineeringMaterial(
                "modifiedembeddedfirmware", "x", "Encoded", "EncodedFirmware", 5);
        var pos = layout.position(mat).orElseThrow();
        assertEquals(5, pos.row());
        assertEquals(4, pos.col());
        assertEquals(6, layout.rows("Encoded").size());
        assertEquals(5, layout.grades("Encoded"));
    }

    @Test
    void raw_category3Grade2() {
        MaterialTraderScreenLayout layout = MaterialTraderScreenLayout.getInstance();
        EngineeringMaterial mat = new EngineeringMaterial("iron", "Iron", "Raw", "Category3", 2);
        var pos = layout.position(mat).orElseThrow();
        assertEquals(2, pos.row());
        assertEquals(1, pos.col());
        assertEquals(4, layout.grades("Raw"));
    }

    @Test
    void manufactured_alloysGrade1_isLastRow() {
        MaterialTraderScreenLayout layout = MaterialTraderScreenLayout.getInstance();
        EngineeringMaterial mat = new EngineeringMaterial(
                "carbonfibreplating", "x", "Manufactured", "Alloys", 1);
        var pos = layout.position(mat).orElseThrow();
        assertEquals(9, pos.row());
        assertEquals(0, pos.col());
    }

    private static EngineeringMaterial mat(String key, String type, String subtype, int grade) {
        return new EngineeringMaterial(key, key, type, subtype, grade);
    }

    @Test
    void sameGroup_oneBatch_needsOneRight() {
        EngineeringMaterial g1 = mat("a", "Encoded", "EncodedFirmware", 1);
        EngineeringMaterial g2 = mat("b", "Encoded", "EncodedFirmware", 2);
        assertEquals(1, MaterialTradeRateCalculator.rightPressesFor(g1, g2, 6, 1));
    }

    @Test
    void sameGroup_sixBatches_needsSixRights() {
        EngineeringMaterial g1 = mat("a", "Encoded", "EncodedFirmware", 1);
        EngineeringMaterial g2 = mat("b", "Encoded", "EncodedFirmware", 2);
        assertEquals(6, MaterialTradeRateCalculator.rightPressesFor(g1, g2, 36, 6));
    }

    @Test
    void crossCategory_twoForThree_batchCount() {
        EngineeringMaterial dmwe = mat("dataminedwakeexceptions", "Encoded", "WakeScans", 5);
        EngineeringMaterial firmware = mat("crackedindustrialfirmware", "Encoded", "EncodedFirmware", 3);
        assertEquals(3, MaterialTradeRateCalculator.rightPressesFor(dmwe, firmware, 6, 9));
    }

    @Test
    void executor_matchesJournalEvent() {
        EngineeringDatabase db = EngineeringDatabase.getInstance();
        MaterialTradeExecutor executor = new MaterialTradeExecutor(db);
        TradeSuggestion suggestion = new TradeSuggestion(
                "specialisedlegacyfirmware", "Specialised Legacy Firmware", 6,
                "modifiedconsumerfirmware", "Modified Consumer Firmware", 1,
                true, "Encoded");
        MaterialTradeEvent event = new MaterialTradeEvent(
                Instant.now(),
                new JsonObject(),
                "Encoded",
                "specialisedlegacyfirmware",
                "Specialised Legacy Firmware",
                6,
                "modifiedconsumerfirmware",
                "Modified Consumer Firmware",
                1);
        assertTrue(executor.matches(suggestion, event));
    }

    @Test
    void cancellationRequestedBeforeRunStopsWithoutSendingATrade() {
        MaterialTradeExecutor executor = new MaterialTradeExecutor(EngineeringDatabase.getInstance());
        executor.requestCancel();
        TradeSuggestion suggestion = new TradeSuggestion(
                "specialisedlegacyfirmware", "Specialised Legacy Firmware", 6,
                "modifiedconsumerfirmware", "Modified Consumer Firmware", 1,
                true, "Encoded");

        MaterialTradeExecutor.Result result = executor.execute(suggestion);

        assertEquals(MaterialTradeExecutor.Outcome.INTERRUPTED, result.outcome());
        assertEquals("Trade cancelled", result.message());
    }

    @Test
    void focusLossMessageIdentifiesTheForegroundApplication() {
        assertEquals(
                "Elite lost focus before keys were sent (foreground: javaw.exe)",
                MaterialTradeExecutor.focusLostBeforeKeysMessage("javaw.exe"));
    }
}
