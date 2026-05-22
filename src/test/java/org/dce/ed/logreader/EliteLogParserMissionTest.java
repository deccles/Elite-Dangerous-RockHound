package org.dce.ed.logreader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.dce.ed.logreader.event.CargoDepotEvent;
import org.dce.ed.logreader.event.MissionAcceptedEvent;
import org.dce.ed.logreader.event.MissionRedirectedEvent;
import org.dce.ed.logreader.event.MissionsEvent;
import org.junit.jupiter.api.Test;

class EliteLogParserMissionTest {

    private final EliteLogParser parser = new EliteLogParser();

    @Test
    void parsesMissionAccepted() {
        String line = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":1055486629,\"Faction\":\"ICU Colonial\","
                + "\"Name\":\"Mission_Mining_Boom\",\"LocalisedName\":\"Mining rush\","
                + "\"Commodity_Localised\":\"Bromellite\",\"Count\":36,"
                + "\"DestinationSystem\":\"Coeus\",\"DestinationStation\":\"Foster Terminal\","
                + "\"Reward\":9200000}";
        MissionAcceptedEvent e = assertInstanceOf(MissionAcceptedEvent.class, parser.parseRecord(line));
        assertEquals(1055486629L, e.getMissionId());
        assertEquals("Bromellite", e.getCommodityLocalised());
        assertEquals(36, e.getCount());
        assertEquals("Coeus", e.getDestinationSystem());
    }

    @Test
    void parsesCargoDepot() {
        String line = "{\"timestamp\":\"2026-05-22T10:30:00Z\",\"event\":\"CargoDepot\","
                + "\"MissionID\":1055486629,\"UpdateType\":\"Collect\","
                + "\"ItemsCollected\":12,\"ItemsDelivered\":0,\"TotalItemsToDeliver\":36}";
        CargoDepotEvent e = assertInstanceOf(CargoDepotEvent.class, parser.parseRecord(line));
        assertEquals(1055486629L, e.getMissionId());
        assertEquals(12, e.getItemsCollected());
        assertEquals(36, e.getTotalItemsToDeliver());
    }

    @Test
    void parsesMissionRedirected() {
        String line = "{\"timestamp\":\"2026-05-22T11:00:00Z\",\"event\":\"MissionRedirected\","
                + "\"MissionID\":1046839756,\"NewDestinationSystem\":\"Colonia\","
                + "\"NewDestinationStation\":\"Jaques Station\"}";
        MissionRedirectedEvent e = assertInstanceOf(MissionRedirectedEvent.class, parser.parseRecord(line));
        assertEquals("Colonia", e.getNewDestinationSystem());
        assertEquals("Jaques Station", e.getNewDestinationStation());
    }

    @Test
    void parsesMissionsSnapshot() {
        String line = "{\"timestamp\":\"2026-05-22T12:00:00Z\",\"event\":\"Missions\","
                + "\"Active\":[{\"MissionID\":1,\"Name\":\"Mission_Mining_Boom\",\"PassengerMission\":false,\"Expires\":7200}]}";
        MissionsEvent e = assertInstanceOf(MissionsEvent.class, parser.parseRecord(line));
        assertEquals(1, e.getActive().size());
        assertEquals(1L, e.getActive().get(0).missionId);
    }
}
