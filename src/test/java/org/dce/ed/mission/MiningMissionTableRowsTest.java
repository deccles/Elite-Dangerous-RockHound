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
        int inHold = rows.get(0).getInHoldTons();
        assertEquals(inHold + "/48 t", rows.get(0).getQuantityDisplay());
        assertTrue(inHold >= 0 && inHold <= 48);
        assertEquals(1_500_000L, rows.get(0).getRewardCredits());
        assertTrue(rows.get(0).getTurnInDisplay().contains("Coeus"));
    }

    @Test
    void allocateInHoldAcrossMaterialRows_fillsFirstRowBeforeSecond() {
        MissionDestination stationA = new MissionDestination("Alpha", "Port A", null);
        MissionDestination stationB = new MissionDestination("Beta", "Port B", null);
        var pending = List.of(
                new MiningMissionTableRows.PendingRow("Osmium", 100, 20, 80, 1L, stationA),
                new MiningMissionTableRows.PendingRow("Osmium", 50, 0, 50, 2L, stationB));
        var rows = MiningMissionTableRows.allocateInHoldAcrossMaterialRows(pending, commodity -> 30);
        assertEquals(2, rows.size());
        assertEquals(30, rows.get(0).getInHoldTons());
        assertEquals(50.0, rows.get(0).getPercentComplete(), 0.01);
        assertEquals(0, rows.get(1).getInHoldTons());
        assertEquals(0.0, rows.get(1).getPercentComplete(), 0.01);
        assertEquals("30/80 t", rows.get(0).getQuantityDisplay());
        assertEquals("0/50 t", rows.get(1).getQuantityDisplay());
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
