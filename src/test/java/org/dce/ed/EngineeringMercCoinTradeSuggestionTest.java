package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableModel;

import org.dce.ed.engineering.EngineeringDatabase;
import org.dce.ed.engineering.EngineeringGoal;
import org.dce.ed.engineering.EngineeringMaterialKeys;
import org.dce.ed.logreader.EliteLogParser;
import org.junit.jupiter.api.Test;

/**
 * Merc Coins cannot be swapped at a material trader. When they are the only remaining need,
 * Trade Suggestions must still list them.
 */
class EngineeringMercCoinTradeSuggestionTest {

    @Test
    void mercCoinOnlyShortfallStaysListedWhenNoTradesExist() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EngineeringDatabase db = EngineeringDatabase.getInstance();
            EngineeringGoal cargo = new EngineeringGoal(
                    "cargo-rack-extended-g2",
                    "Cargo Rack",
                    "Extended",
                    1,
                    2,
                    "");
            Map<String, Integer> required = new org.dce.ed.engineering.EngineeringPlanner(db)
                    .materialsForGoal(cargo);
            assertTrue(required.containsKey(EngineeringMaterialKeys.MERC_COINS));
            assertTrue(required.get(EngineeringMaterialKeys.MERC_COINS) > 0);

            Map<String, Integer> inventory = new HashMap<>();
            for (Map.Entry<String, Integer> entry : required.entrySet()) {
                if (!EngineeringMaterialKeys.isMercCoins(entry.getKey())) {
                    inventory.put(entry.getKey(), entry.getValue());
                }
            }

            EngineeringTabPanel panel = new EngineeringTabPanel(() -> false);
            panel.refreshTradePlanForTest(List.of(cargo), inventory);

            assertTrue(panel.tradeSuggestionsVisibleForTest(),
                    "Merc Coin need must keep Trade Suggestions visible");
            assertFalse(panel.tradeEmptyLabelVisibleForTest());

            TableModel model = panel.tradeTableForTest().getModel();
            Integer mercNeed = null;
            String mercGive = null;
            for (int row = 0; row < model.getRowCount(); row++) {
                Object material = model.getValueAt(row, 0);
                if (material != null && material.toString().equals("Merc Coins")) {
                    mercNeed = (Integer) model.getValueAt(row, 1);
                    mercGive = String.valueOf(model.getValueAt(row, 2));
                }
            }
            assertEquals(required.get(EngineeringMaterialKeys.MERC_COINS), mercNeed);
            assertEquals("No trades available", mercGive);
        });
    }

    @Test
    void twoCargoRacksToG5_listsMercCoinsBesideOtherShortages() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EngineeringDatabase db = EngineeringDatabase.getInstance();
            EngineeringGoal cargo = new EngineeringGoal(
                    "cargo-rack-extended-g5",
                    "Cargo Rack",
                    "Extended",
                    0,
                    0,
                    5,
                    "",
                    true,
                    false,
                    2,
                    0,
                    19L,
                    "Panther Clipper Mk II");
            Map<String, Integer> required = new org.dce.ed.engineering.EngineeringPlanner(db)
                    .materialsForGoal(cargo);
            int mercNeedExpected = required.get(EngineeringMaterialKeys.MERC_COINS);
            assertTrue(mercNeedExpected > 241);

            Map<String, Integer> inventory = new HashMap<>();
            for (Map.Entry<String, Integer> entry : required.entrySet()) {
                if (EngineeringMaterialKeys.isMercCoins(entry.getKey())) {
                    inventory.put(entry.getKey(), 241);
                } else {
                    inventory.put(entry.getKey(), 0);
                }
            }

            EngineeringTabPanel panel = new EngineeringTabPanel(() -> false);
            panel.refreshTradePlanForTest(List.of(cargo), inventory);

            assertTrue(panel.tradeSuggestionsVisibleForTest());
            TableModel model = panel.tradeTableForTest().getModel();
            Integer mercNeed = null;
            boolean sawOtherShort = false;
            for (int row = 0; row < model.getRowCount(); row++) {
                Object material = model.getValueAt(row, 0);
                if (material == null) {
                    continue;
                }
                if (material.toString().equals("Merc Coins")) {
                    mercNeed = (Integer) model.getValueAt(row, 1);
                } else if (model.getValueAt(row, 1) instanceof Integer need && need > 0) {
                    sawOtherShort = true;
                }
            }
            assertEquals(mercNeedExpected - 241, mercNeed);
            assertTrue(sawOtherShort, "other cargo materials should still be listed");
            String firstDataMaterial = null;
            for (int row = 0; row < model.getRowCount(); row++) {
                if (model.getValueAt(row, 1) instanceof Integer) {
                    Object material = model.getValueAt(row, 0);
                    firstDataMaterial = material != null ? material.toString() : "";
                    break;
                }
            }
            assertEquals("Merc Coins", firstDataMaterial);
        });
    }

    /** Between balance reports the Merc Coin Need is an estimate and must read "~N". */
    @Test
    void estimatedBalance_marksMercCoinNeedApproximate() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EngineeringGoal cargo = new EngineeringGoal(
                    "cargo-rack-extended-g5", "Cargo Rack", "Extended", 0, 0, 5, "",
                    true, false, 2, 0, 19L, "Panther Clipper Mk II");
            EngineeringTabPanel panel = new EngineeringTabPanel(() -> false);
            panel.refreshTradePlanForTest(List.of(cargo), Map.of(EngineeringMaterialKeys.MERC_COINS, 97));

            JTable trade = panel.tradeTableForTest();
            int mercRow = -1;
            for (int row = 0; row < trade.getRowCount(); row++) {
                Object material = trade.getModel().getValueAt(row, 0);
                if (material != null && material.toString().equals("Merc Coins")) {
                    mercRow = row;
                }
            }
            assertTrue(mercRow >= 0);
            assertFalse(needCellText(trade, mercRow).startsWith("~"), "a reported balance is exact");

            EliteLogParser parser = new EliteLogParser();
            var tracker = panel.getInventoryTracker();
            tracker.applyEvent(parser.parseRecord("""
                    {"timestamp": "2026-09-28T20:39:12Z", "event": "Statistics",
                     "Bank_Account": {"MercCoins_Current": 97, "MercCoins_Total_Spent": 1490}}
                    """));
            tracker.applyEvent(parser.parseRecord("""
                    {"timestamp": "2026-09-28T20:48:36Z", "event": "LoadGame", "Commander": "Villunus",
                     "Ship": "Federation_Corvette", "ShipID": 23, "Credits": 2917530096}
                    """));
            tracker.applyEvent(parser.parseRecord("""
                    {"timestamp": "2026-09-28T20:48:36Z", "event": "GameModeChange", "GameMode": "Operation"}
                    """));
            tracker.applyEvent(parser.parseRecord("""
                    {"timestamp": "2026-09-28T21:03:05Z", "event": "LoadGame", "Commander": "Villunus",
                     "Ship": "Federation_Corvette", "ShipID": 23, "Credits": 2926630096}
                    """));
            assertTrue(tracker.isMercCoinBalanceEstimated());
            assertTrue(needCellText(trade, mercRow).startsWith("~"),
                    "estimated Need reads ~N, was " + needCellText(trade, mercRow));
        });
    }

    private static String needCellText(JTable trade, int row) {
        Component c = trade.prepareRenderer(trade.getCellRenderer(row, 1), row, 1);
        return c instanceof JLabel label ? label.getText() : "";
    }
}
