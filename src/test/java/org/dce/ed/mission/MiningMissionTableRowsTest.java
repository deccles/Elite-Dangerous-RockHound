package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.MissionAcceptedEvent;
import org.junit.jupiter.api.Test;

class MiningMissionTableRowsTest {

    private final EliteLogParser parser = new EliteLogParser();

    @Test
    void build_groupsByTurnInAndSumsRewards() {
        MissionTracker tracker = new MissionTracker();
        String a = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":1,\"Name\":\"Mission_Mining_Boom\","
                + "\"LocalisedName\":\"Mining Rush for 28 Units of Osmium\","
                + "\"Commodity_Localised\":\"Osmium\",\"Count\":28,"
                + "\"DestinationSystem\":\"Coeus\",\"DestinationStation\":\"Foster Terminal\",\"Reward\":1000000}";
        String b = "{\"timestamp\":\"2026-05-22T10:01:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":2,\"Name\":\"Mission_Mining_Boom\","
                + "\"LocalisedName\":\"Mining Rush for 20 Units of Osmium\","
                + "\"Commodity_Localised\":\"Osmium\",\"Count\":20,"
                + "\"DestinationSystem\":\"Coeus\",\"DestinationStation\":\"Foster Terminal\",\"Reward\":500000}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(a));
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(b));

        var rows = MiningMissionTableRows.build(tracker);
        assertEquals(1, rows.size());
        assertEquals("Osmium", rows.get(0).getMaterial());
        assertEquals(48, rows.get(0).getRemainingTons());
        var display = MiningMissionTableRows.allocateDisplayForModelOrder(rows, List.of(0), commodity -> 0);
        assertEquals("0/48 t", display.get(0).getQuantityDisplay());
        assertEquals(1_500_000L, rows.get(0).getRewardCredits());
        assertTrue(rows.get(0).getTurnInDisplay().contains("Coeus"));
    }

    @Test
    void allocateDisplayForModelOrder_fillsFirstRowBeforeSecond() {
        MissionDestination stationA = new MissionDestination("Alpha", "Port A", null);
        MissionDestination stationB = new MissionDestination("Beta", "Port B", null);
        var rows = List.of(
                new MiningMissionTableRows.Row("Osmium", 20, 80, 100, 1L, stationA),
                new MiningMissionTableRows.Row("Osmium", 0, 50, 50, 2L, stationB));
        var display = MiningMissionTableRows.allocateDisplayForModelOrder(rows, List.of(0, 1), commodity -> 30);
        assertEquals(30, display.get(0).getInHoldTons());
        assertEquals(50.0, display.get(0).getPercentComplete(), 0.01);
        assertEquals(0, display.get(1).getInHoldTons());
        assertEquals(0.0, display.get(1).getPercentComplete(), 0.01);
        assertEquals("30/80 t", display.get(0).getQuantityDisplay());
        assertEquals("0/50 t", display.get(1).getQuantityDisplay());
    }

    @Test
    void allocateDisplayForModelOrder_secondRowZeroPercentUntilFirstComplete() {
        MissionDestination oxley = new MissionDestination("Coeus", "Oxley Orbital", null);
        MissionDestination foster = new MissionDestination("Coeus", "Foster Terminal", null);
        var rows = List.of(
                new MiningMissionTableRows.Row("Osmium", 0, 137, 137, 1L, oxley),
                new MiningMissionTableRows.Row("Osmium", 15, 85, 100, 2L, foster));
        var display = MiningMissionTableRows.allocateDisplayForModelOrder(rows, List.of(0, 1), commodity -> 78);
        assertEquals(78, display.get(0).getInHoldTons());
        assertEquals(57.0, display.get(0).getPercentComplete(), 0.5);
        assertEquals(0, display.get(1).getInHoldTons());
        assertEquals(0.0, display.get(1).getPercentComplete(), 0.01);
    }

    @Test
    void allocateDisplayForModelOrder_respectsViewOrderWhenReversed() {
        MissionDestination stationA = new MissionDestination("Alpha", "Port A", null);
        MissionDestination stationB = new MissionDestination("Beta", "Port B", null);
        var rows = List.of(
                new MiningMissionTableRows.Row("Osmium", 20, 80, 100, 1L, stationA),
                new MiningMissionTableRows.Row("Osmium", 0, 50, 50, 2L, stationB));
        var display = MiningMissionTableRows.allocateDisplayForModelOrder(rows, List.of(1, 0), commodity -> 30);
        assertEquals(0, display.get(0).getInHoldTons());
        assertEquals(30, display.get(1).getInHoldTons());
    }

    @Test
    void build_ignoresNonMiningCommodityMissions() {
        MissionTracker tracker = new MissionTracker();
        String courier = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":9,\"Name\":\"Mission_Courier\","
                + "\"Commodity_Localised\":\"Gold\",\"Count\":10,"
                + "\"DestinationSystem\":\"A\",\"DestinationStation\":\"B\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(courier));
        assertTrue(MiningMissionTableRows.build(tracker).isEmpty());
    }
}
