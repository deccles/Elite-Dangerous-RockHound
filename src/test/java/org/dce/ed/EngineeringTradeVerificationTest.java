package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.dce.ed.engineering.MaterialTradeExecutor;
import org.dce.ed.engineering.TradeSuggestion;

class EngineeringTradeVerificationTest {

    @Test
    void traderTypeMatchingIgnoresCaseAndWhitespace() {
        assertTrue(EngineeringTabPanel.sameTraderType(" Manufactured ", "manufactured"));
    }

    @Test
    void traderTypeMatchingRejectsAnotherTraderCategory() {
        assertFalse(EngineeringTabPanel.sameTraderType("Raw", "Manufactured"));
    }

    @Test
    void tradeAllDoesNotReportSuccessWhenSameTraderTradesRemain() {
        MaterialTradeExecutor.Result executed = new MaterialTradeExecutor.Result(
                MaterialTradeExecutor.Outcome.SUCCESS, "Trading complete");
        TradeSuggestion remaining = new TradeSuggestion(
                "iron", "Iron", 6, "nickel", "Nickel", 1, true, "Manufactured");

        MaterialTradeExecutor.Result verified = EngineeringTabPanel.verifiedTradeAllResult(
                executed, "Manufactured", List.of(remaining));

        assertEquals(MaterialTradeExecutor.Outcome.VERIFICATION_FAILED, verified.outcome());
        assertFalse(verified.ok());
        assertTrue(verified.message().contains("Keep this trader screen open"));
    }

    @Test
    void tradeAllReportsSuccessWhenOnlyOtherTraderCategoriesRemain() {
        MaterialTradeExecutor.Result executed = new MaterialTradeExecutor.Result(
                MaterialTradeExecutor.Outcome.SUCCESS, "Trading complete");
        TradeSuggestion raw = new TradeSuggestion(
                "iron", "Iron", 6, "nickel", "Nickel", 1, true, "Raw");

        assertEquals(executed, EngineeringTabPanel.verifiedTradeAllResult(
                executed, "Manufactured", List.of(raw)));
    }
}
