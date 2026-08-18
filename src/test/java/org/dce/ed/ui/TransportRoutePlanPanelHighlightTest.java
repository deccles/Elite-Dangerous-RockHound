package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Component;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.border.MatteBorder;
import javax.swing.border.CompoundBorder;

import org.dce.ed.market.CommodityMarketOrder;
import org.dce.ed.mission.TransportLocation;
import org.dce.ed.mission.TransportPlanAction;
import org.dce.ed.mission.TransportPlanStop;
import org.dce.ed.mission.TransportRoutePlan;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class TransportRoutePlanPanelHighlightTest {
    @Test
    void departureReminderDescribesIncompleteDeliveryAndDonationActions() {
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(new TransportLocation("Lave", "Lave Station", 10, 0, 0),
                        List.of(
                                new TransportPlanAction(TransportPlanAction.Kind.PICK_UP, 1L, "Gold", 8),
                                new TransportPlanAction(TransportPlanAction.Kind.VISIT, 2L, "Donate 500,000 Cr", 0)),
                        8)), 10.0, true);
        TransportRoutePlanPanel panel = new TransportRoutePlanPanel(plan, 64,
                new TransportLocation("Sol", "Galileo", 0, 0, 0), 0, systems -> { }, List.of());
        panel.updateCurrentLocation("Lave", "Lave Station");

        assertEquals("Did you forget your delivery and donations again, Commander?",
                panel.departureReminderAt("Lave", "Lave Station").orElseThrow());

        panel.updateMissionCompleted(2L);
        assertEquals("Did you forget your delivery again, Commander?",
                panel.departureReminderAt("Lave", "Lave Station").orElseThrow());
    }

    @Test
    void loadedPickupIsNotIncludedWhenOnlyDonationRemainsAtMixedStop() {
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(new TransportLocation("Lave", "Lave Station", 10, 0, 0),
                        List.of(
                                new TransportPlanAction(TransportPlanAction.Kind.PICK_UP, 1L, "Gold", 8),
                                new TransportPlanAction(TransportPlanAction.Kind.VISIT, 2L, "Donate 500,000 Cr", 0)),
                        8)), 10.0, true);
        TransportRoutePlanPanel panel = new TransportRoutePlanPanel(plan, 64,
                new TransportLocation("Sol", "Galileo", 0, 0, 0), 0, systems -> { }, List.of());
        panel.updateCurrentLocation("Lave", "Lave Station");
        panel.updateCurrentCargo(JsonParser.parseString("""
                {"Inventory":[{"Name_Localised":"Gold","Count":8,"MissionID":1}]}
                """).getAsJsonObject());

        assertEquals("Did you forget your donations again, Commander?",
                panel.departureReminderAt("Lave", "Lave Station").orElseThrow());
    }
    @Test
    void startRowPreventsCurrentSystemFromHighlightingAFutureRepeatedStop() {
        TransportRoutePlanPanel panel = alternatingPlanFromGliese();
        JTable table = panel.tableForTests();

        assertEquals(4, table.getRowCount());
        assertEquals(6, table.getColumnCount());
        assertEquals("", table.getColumnName(0));
        assertEquals("#", table.getColumnName(1));
        assertEquals(false, table.getValueAt(0, 0));
        assertEquals(0, table.getValueAt(0, 1));
        assertEquals("System", table.getColumnName(2));
        assertEquals("Station", table.getColumnName(3));
        assertEquals("Gliese 868", table.getValueAt(0, 2));
        assertEquals("MacLean Terminal", table.getValueAt(0, 3));
        assertEquals("", table.getValueAt(0, 4));
        assertEquals("0 / 1056 t\nFree 1056 t", table.getValueAt(0, 5));
        assertEquals(0, panel.highlightedRowForTests());
        assertEquals(false, table.getRowSelectionAllowed());
        assertEquals(false, table.getColumnSelectionAllowed());
        assertEquals(false, table.getCellSelectionEnabled());
        assertEquals("Gliese 868", panel.systemForTableRow(0));
        assertEquals("Core Sys Sector FW-N a6-0", panel.systemForTableRow(1));

        panel.updateCurrentLocation("Gliese 868", "MacLean Terminal");
        assertEquals(0, panel.highlightedRowForTests());

        panel.updateCurrentLocation("Unexpected", null);
        assertEquals(-1, panel.highlightedRowForTests());

        panel.updateCurrentLocation("Core Sys Sector FW-N a6-0", null);
        assertEquals(1, panel.highlightedRowForTests());

        panel.updateCurrentLocation("Gliese 868", null);
        assertEquals(2, panel.highlightedRowForTests());
    }

    @Test
    void highlightedStopCellsReceiveOrangeRowBorder() {
        TransportRoutePlanPanel panel = alternatingPlanFromGliese();
        panel.updateCurrentLocation("Core Sys Sector FW-N a6-0", null);
        JTable table = panel.tableForTests();

        Component rendered = table.prepareRenderer(table.getCellRenderer(1, 2), 1, 2);

        assertTrue(rendered instanceof JComponent);
        assertTrue(((JComponent) rendered).getBorder() instanceof CompoundBorder);
        CompoundBorder border = (CompoundBorder) ((JComponent) rendered).getBorder();
        assertTrue(border.getOutsideBorder() instanceof MatteBorder);
    }

    @Test
    void advancingToNextStopChecksOnlyTheCompletedOccurrence() {
        TransportRoutePlanPanel panel = alternatingPlanFromGliese();
        JTable table = panel.tableForTests();

        panel.updateCurrentLocation("Core Sys Sector FW-N a6-0", "Davy Vision");
        assertEquals(false, table.getValueAt(1, 0));
        assertEquals(1, table.getValueAt(1, 1));
        panel.updateMissionCompleted(1L);

        panel.updateCurrentLocation("Gliese 868", "MacLean Terminal");
        assertEquals(true, table.getValueAt(1, 0));
        assertEquals(1, table.getValueAt(1, 1));
        assertEquals(false, table.getValueAt(2, 0));
        assertEquals(2, table.getValueAt(2, 1));
        Component completed = table.prepareRenderer(table.getCellRenderer(1, 0), 1, 0);
        assertTrue(completed instanceof JLabel);
        assertTrue(((JLabel) completed).getIcon() != null);
        assertTrue(table.getColumnModel().getColumn(0).getPreferredWidth() >= 20);
        panel.updateMissionCompleted(1L);

        panel.updateCurrentLocation("Core Sys Sector FW-N a6-0", "Davy Vision");
        assertEquals(true, table.getValueAt(2, 0));
        assertEquals(2, table.getValueAt(2, 1));
        assertEquals(false, table.getValueAt(3, 0));
        assertEquals(3, table.getValueAt(3, 1));
        assertEquals(3, panel.highlightedRowForTests());
    }

    @Test
    void visitingLaterStationFirstDoesNotCompleteSkippedDelivery() {
        TransportLocation start = new TransportLocation(
                "Gliese 868", "Current position", 0, 0, 0);
        TransportPlanStop source = new TransportPlanStop(
                new TransportLocation("Core Sys Sector AQ-P a5-1", "Rock Vision", 10, 0, 0),
                List.of(
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP,
                                10L, "Clothing", 27),
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP,
                                20L, "Water Purifiers", 63)),
                90);
        TransportPlanStop macLean = new TransportPlanStop(
                new TransportLocation("Gliese 868", "MacLean Terminal", 0, 0, 0),
                List.of(new TransportPlanAction(TransportPlanAction.Kind.DELIVER,
                        10L, "Clothing", 27)), 63);
        TransportPlanStop braun = new TransportPlanStop(
                new TransportLocation("Gliese 868", "Braun Station", 0, 0, 0),
                List.of(new TransportPlanAction(TransportPlanAction.Kind.DELIVER,
                        20L, "Water Purifiers", 63)), 0);
        TransportRoutePlanPanel panel = new TransportRoutePlanPanel(
                new TransportRoutePlan(List.of(source, macLean, braun), 20.0, true), 1056,
                start, 0, systems -> { }, List.of());
        JTable table = panel.tableForTests();

        panel.updateCurrentLocation("Core Sys Sector AQ-P a5-1", "Rock Vision");
        panel.updateCurrentCargo(JsonParser.parseString("""
                {"Inventory":[
                  {"Name_Localised":"Clothing","Count":27,"MissionID":10},
                  {"Name_Localised":"Water Purifiers","Count":63,"MissionID":20}
                ]}
                """).getAsJsonObject());
        panel.updateCurrentLocation("Gliese 868", null);
        panel.updateCurrentLocation("Gliese 868", "Braun Station");

        assertEquals(3, panel.highlightedRowForTests());
        assertEquals(false, table.getValueAt(2, 0));
        assertTrue(actionStatusIcon(table, 2, 0) == null);

        panel.updateCargoDepotProgress(20L, "Deliver", 63);
        assertEquals(true, table.getValueAt(3, 0));
        assertTrue(actionStatusIcon(table, 3, 0) != null);
        assertEquals(false, table.getValueAt(2, 0));

        panel.updateCurrentLocation("Gliese 868", "MacLean Terminal");
        assertEquals(2, panel.highlightedRowForTests());
        panel.updateCargoDepotProgress(10L, "Deliver", 27);
        assertEquals(true, table.getValueAt(2, 0));
        assertTrue(actionStatusIcon(table, 2, 0) != null);
    }

    private static Icon actionStatusIcon(JTable table, int row, int actionLine) {
        JPanel rendered = (JPanel) table.prepareRenderer(table.getCellRenderer(row, 4), row, 4);
        JPanel line = (JPanel) rendered.getComponent(actionLine);
        return ((JLabel) line.getComponent(0)).getIcon();
    }

    @Test
    void commodityActionsFollowTheInGameMarketOrder() {
        TransportPlanStop stop = new TransportPlanStop(
                new TransportLocation("Lave", "Lave Station", 10, 0, 0),
                List.of(
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP,
                                1L, "Gold", 20),
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP,
                                2L, "Domestic Appliances", 48),
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP,
                                3L, "Mineral Oil", 18),
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP,
                                4L, "Water Purifiers", 27),
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP,
                                5L, "Agronomic Treatment", 14),
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP,
                                6L, "Food Cartridges", 18),
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP,
                                7L, "Clothing", 72)),
                217);
        TransportRoutePlanPanel panel = new TransportRoutePlanPanel(
                new TransportRoutePlan(List.of(stop), 10.0, true), 1056,
                null, 0, systems -> { }, List.of(), marketOrder());

        assertEquals("Pick up 14 t Agronomic Treatment\n"
                        + "Pick up 18 t Mineral Oil\n"
                        + "Pick up 72 t Clothing\n"
                        + "Pick up 48 t Domestic Appliances\n"
                        + "Pick up 18 t Food Cartridges\n"
                        + "Pick up 27 t Water Purifiers\n"
                        + "Pick up 20 t Gold",
                panel.tableForTests().getValueAt(0, 4));
    }

    @Test
    void multipleActionsUseSeparateLinesAndExpandTheStopRow() {
        TransportPlanStop stop = new TransportPlanStop(
                new TransportLocation("Lave", "Lave Station", 10, 0, 0),
                List.of(
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP,
                                1L, "Animal Monitors", 740),
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP,
                                2L, "Gold", 138),
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP,
                                3L, "Water Purifiers", 106)),
                984);
        TransportRoutePlanPanel panel = new TransportRoutePlanPanel(
                new TransportRoutePlan(List.of(stop), 10.0, true), 1056,
                null, 0, systems -> { }, List.of(), marketOrder());
        JTable table = panel.tableForTests();

        assertEquals("Pick up 740 t Animal Monitors\nPick up 106 t Water Purifiers\nPick up 138 t Gold",
                table.getValueAt(0, 4));
        assertTrue(table.getRowHeight(0) >= table.getRowHeight() * 3);
    }

    @Test
    void holdColumnShowsPredictedFreeSpaceOnASecondLine() {
        TransportLocation start = new TransportLocation("Sol", "Galileo", 0, 0, 0);
        TransportPlanStop stop = new TransportPlanStop(
                new TransportLocation("Lave", "Lave Station", 10, 0, 0),
                List.of(new TransportPlanAction(
                        TransportPlanAction.Kind.PICK_UP, 1L, "Gold", 884)),
                984);
        TransportRoutePlanPanel panel = new TransportRoutePlanPanel(
                new TransportRoutePlan(List.of(stop), 10.0, true), 1056,
                start, 100, systems -> { }, List.of());
        JTable table = panel.tableForTests();

        assertEquals("100 / 1056 t\nFree 956 t", table.getValueAt(0, 5));
        assertEquals("984 / 1056 t\nFree 72 t", table.getValueAt(1, 5));
        assertTrue(table.getRowHeight(0) >= table.getRowHeight() * 2);
        assertTrue(table.getRowHeight(1) >= table.getRowHeight() * 2);

        JPanel rendered = (JPanel) table.prepareRenderer(table.getCellRenderer(1, 5), 1, 5);
        assertEquals(2, rendered.getComponentCount());
        assertEquals("984 / 1056 t", ((JLabel) rendered.getComponent(0)).getText());
        assertEquals("Free 72 t", ((JLabel) rendered.getComponent(1)).getText());
    }

    @Test
    void sameCommodityActionsAreCombinedWithMissionCount() {
        TransportPlanStop stop = new TransportPlanStop(
                new TransportLocation("Lave", "Lave Station", 10, 0, 0),
                List.of(
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP, 1L, "Clothing", 72),
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP, 2L, "Clothing", 90),
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP, 3L, "Basic Medicines", 14),
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP, 4L, "Basic Medicines", 18),
                        new TransportPlanAction(TransportPlanAction.Kind.PICK_UP, 5L, "Gold", 20)),
                214);
        TransportRoutePlanPanel panel = new TransportRoutePlanPanel(
                new TransportRoutePlan(List.of(stop), 10.0, true), 1056,
                null, 0, systems -> { }, List.of(), marketOrder());

        assertEquals("Pick up 162 t Clothing (2 missions)\n"
                        + "Pick up 32 t Basic Medicines (2 missions)\n"
                        + "Pick up 20 t Gold",
                panel.tableForTests().getValueAt(0, 4));
    }

    private static CommodityMarketOrder marketOrder() {
        return CommodityMarketOrder.fromMarketSnapshot(JsonParser.parseString("""
                {"Items":[
                  {"Name_Localised":"Agronomic Treatment","Category":"$MARKET_category_chemicals;"},
                  {"Name_Localised":"Mineral Oil","Category":"$MARKET_category_chemicals;"},
                  {"Name_Localised":"Animal Monitors","Category":"$MARKET_category_consumer_items;"},
                  {"Name_Localised":"Clothing","Category":"$MARKET_category_consumer_items;"},
                  {"Name_Localised":"Domestic Appliances","Category":"$MARKET_category_consumer_items;"},
                  {"Name_Localised":"Food Cartridges","Category":"$MARKET_category_foods;"},
                  {"Name_Localised":"Water Purifiers","Category":"$MARKET_category_industrial_materials;"},
                  {"Name_Localised":"Basic Medicines","Category":"$MARKET_category_medicines;"},
                  {"Name_Localised":"Gold","Category":"$MARKET_category_metals;"}
                ]}
                """).getAsJsonObject());
    }

    @Test
    void currentDepotPickupChecksWhenMissionCargoAppearsAboard() throws Exception {
        TransportLocation depot = new TransportLocation("Gliese 868", "MacLean Terminal", 0, 0, 0);
        TransportPlanStop stop = new TransportPlanStop(depot,
                List.of(new TransportPlanAction(TransportPlanAction.Kind.PICK_UP,
                        42L, "Haematite", 196)), 196);
        TransportRoutePlanPanel panel = new TransportRoutePlanPanel(
                new TransportRoutePlan(List.of(stop), 0.0, true), 1056,
                depot, 0, systems -> { }, List.of());
        panel.updateCurrentLocation("Gliese 868", "MacLean Terminal");
        assertEquals(false, panel.tableForTests().getValueAt(1, 0));

        var cargo = JsonParser.parseString("""
                {"Inventory":[{"Name":"haematite","Name_Localised":"Haematite",
                  "Count":196,"MissionID":42}]}
                """).getAsJsonObject();
        var method = TransportRoutePlanPanel.class.getDeclaredMethod(
                "updateCurrentCargo", com.google.gson.JsonObject.class);
        method.setAccessible(true);
        method.invoke(panel, cargo);

        assertEquals(true, panel.tableForTests().getValueAt(1, 0));
        assertEquals("Pick up 196 t Haematite", panel.tableForTests().getValueAt(1, 4));
    }

    @Test
    void actionLinesReserveAlignedStatusSpaceAndCheckOnlyCompletedCargo() {
        TransportLocation depot = new TransportLocation("Gliese 868", "MacLean Terminal", 0, 0, 0);
        TransportPlanStop stop = new TransportPlanStop(depot, List.of(
                new TransportPlanAction(TransportPlanAction.Kind.PICK_UP, 42L, "Haematite", 196),
                new TransportPlanAction(TransportPlanAction.Kind.PICK_UP, 43L, "Indite", 108)), 304);
        TransportRoutePlanPanel panel = new TransportRoutePlanPanel(
                new TransportRoutePlan(List.of(stop), 0.0, true), 1056,
                depot, 0, systems -> { }, List.of());
        panel.updateCurrentLocation("Gliese 868", "MacLean Terminal");
        panel.updateCurrentCargo(JsonParser.parseString("""
                {"Inventory":[{"Name":"haematite","Name_Localised":"Haematite",
                  "Count":196,"MissionID":42}]}
                """).getAsJsonObject());

        JTable table = panel.tableForTests();
        Component rendered = table.prepareRenderer(table.getCellRenderer(1, 4), 1, 4);
        assertTrue(rendered instanceof JPanel);
        JPanel lines = (JPanel) rendered;
        JPanel first = (JPanel) lines.getComponent(0);
        JPanel second = (JPanel) lines.getComponent(1);
        JLabel firstStatus = (JLabel) first.getComponent(0);
        JLabel secondStatus = (JLabel) second.getComponent(0);
        assertEquals("", firstStatus.getText());
        assertTrue(firstStatus.getIcon() != null);
        assertEquals(EdoUi.User.SUCCESS, firstStatus.getForeground());
        assertEquals("", secondStatus.getText());
        assertTrue(secondStatus.getIcon() == null);
        assertEquals(firstStatus.getPreferredSize().width, secondStatus.getPreferredSize().width);
    }

    @Test
    void groupedActionChecksOnlyAfterEveryMissionInTheLineIsComplete() {
        TransportLocation depot = new TransportLocation("Gliese 868", "MacLean Terminal", 0, 0, 0);
        TransportPlanStop stop = new TransportPlanStop(depot, List.of(
                new TransportPlanAction(TransportPlanAction.Kind.PICK_UP, 42L, "Clothing", 72),
                new TransportPlanAction(TransportPlanAction.Kind.PICK_UP, 43L, "Clothing", 90)), 162);
        TransportRoutePlanPanel panel = new TransportRoutePlanPanel(
                new TransportRoutePlan(List.of(stop), 0.0, true), 1056,
                depot, 0, systems -> { }, List.of());
        panel.updateCurrentLocation("Gliese 868", "MacLean Terminal");
        panel.updateCurrentCargo(JsonParser.parseString("""
                {"Inventory":[{"Name_Localised":"Clothing","Count":72,"MissionID":42}]}
                """).getAsJsonObject());

        JPanel rendered = (JPanel) panel.tableForTests().prepareRenderer(
                panel.tableForTests().getCellRenderer(1, 4), 1, 4);
        assertTrue(((JLabel) ((JPanel) rendered.getComponent(0)).getComponent(0)).getIcon() == null);

        panel.updateCurrentCargo(JsonParser.parseString("""
                {"Inventory":[
                  {"Name_Localised":"Clothing","Count":72,"MissionID":42},
                  {"Name_Localised":"Clothing","Count":90,"MissionID":43}]}
                """).getAsJsonObject());
        rendered = (JPanel) panel.tableForTests().prepareRenderer(
                panel.tableForTests().getCellRenderer(1, 4), 1, 4);
        assertTrue(((JLabel) ((JPanel) rendered.getComponent(0)).getComponent(0)).getIcon() != null);
        assertEquals(true, panel.tableForTests().getValueAt(1, 0));
    }

    @Test
    void purchasedUntaggedCargoChecksPickupActionsByCommodityQuantity() {
        TransportLocation source = new TransportLocation("Lave", "Market", 0, 0, 0);
        TransportPlanStop stop = new TransportPlanStop(source, List.of(
                new TransportPlanAction(TransportPlanAction.Kind.PICK_UP, 42L, "Water Purifiers", 42),
                new TransportPlanAction(TransportPlanAction.Kind.PICK_UP, 43L, "Advanced Medicines", 891),
                new TransportPlanAction(TransportPlanAction.Kind.PICK_UP, 44L, "Clothing", 42),
                new TransportPlanAction(TransportPlanAction.Kind.PICK_UP, 45L, "Clothing", 81)), 1056);
        TransportRoutePlanPanel panel = new TransportRoutePlanPanel(
                new TransportRoutePlan(List.of(stop), 0.0, true), 1056,
                new TransportLocation("Start", "Port", 0, 0, 0), 0,
                systems -> { }, List.of());
        panel.updateCurrentLocation("Lave", "Market");

        panel.updateCurrentCargo(JsonParser.parseString("""
                {"Inventory":[
                  {"Name_Localised":"Water Purifiers","Count":42},
                  {"Name_Localised":"Advanced Medicines","Count":891},
                  {"Name_Localised":"Clothing","Count":123}]}
                """).getAsJsonObject());

        JPanel rendered = (JPanel) panel.tableForTests().prepareRenderer(
                panel.tableForTests().getCellRenderer(1, 4), 1, 4);
        assertTrue(((JLabel) ((JPanel) rendered.getComponent(0)).getComponent(0)).getIcon() != null);
        assertTrue(((JLabel) ((JPanel) rendered.getComponent(1)).getComponent(0)).getIcon() != null);
        assertTrue(((JLabel) ((JPanel) rendered.getComponent(2)).getComponent(0)).getIcon() != null);
    }

    @Test
    void currentDeliveryActionChecksFromCargoDepotProgress() throws Exception {
        TransportLocation destination = new TransportLocation("Lave", "Lave Station", 10, 0, 0);
        TransportPlanStop stop = new TransportPlanStop(destination,
                List.of(new TransportPlanAction(TransportPlanAction.Kind.DELIVER,
                        42L, "Haematite", 196)), 0);
        TransportRoutePlanPanel panel = new TransportRoutePlanPanel(
                new TransportRoutePlan(List.of(stop), 10.0, true), 1056,
                new TransportLocation("Sol", "Galileo", 0, 0, 0), 196,
                systems -> { }, List.of());
        panel.updateCurrentLocation("Lave", "Lave Station");

        var method = TransportRoutePlanPanel.class.getDeclaredMethod(
                "updateCargoDepotProgress", long.class, String.class, int.class);
        method.invoke(panel, 42L, "Deliver", 196);

        JPanel rendered = (JPanel) panel.tableForTests().prepareRenderer(
                panel.tableForTests().getCellRenderer(1, 4), 1, 4);
        assertTrue(((JLabel) ((JPanel) rendered.getComponent(0)).getComponent(0)).getIcon() != null);
        assertEquals(true, panel.tableForTests().getValueAt(1, 0));
    }

    private static TransportRoutePlanPanel alternatingPlanFromGliese() {
        TransportPlanAction visit = new TransportPlanAction(
                TransportPlanAction.Kind.VISIT, 1L, "Courier", 0);
        TransportRoutePlan plan = new TransportRoutePlan(List.of(
                new TransportPlanStop(new TransportLocation(
                        "Core Sys Sector FW-N a6-0", "Davy Vision", 10, 0, 0),
                        List.of(visit), 0),
                new TransportPlanStop(new TransportLocation(
                        "Gliese 868", "MacLean Terminal", 0, 0, 0),
                        List.of(visit), 0),
                new TransportPlanStop(new TransportLocation(
                        "Core Sys Sector FW-N a6-0", "Davy Vision", 10, 0, 0),
                        List.of(visit), 0)), 20.0, true);
        return new TransportRoutePlanPanel(plan, 1056,
                new TransportLocation("Gliese 868", "MacLean Terminal", 0, 0, 0),
                0, systems -> { }, List.of());
    }
}
