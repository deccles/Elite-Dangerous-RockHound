package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.CargoDepotEvent;
import org.dce.ed.logreader.event.MissionAcceptedEvent;
import org.dce.ed.logreader.event.MissionCompletedEvent;
import org.dce.ed.logreader.event.MissionsEvent;
import org.junit.jupiter.api.Test;

class MissionTrackerTest {

    private final EliteLogParser parser = new EliteLogParser();

    @Test
    void acceptAndComplete_removesMission() {
        MissionTracker tracker = new MissionTracker();
        String accept = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":1055486629,\"Name\":\"Mission_Mining_Boom\","
                + "\"LocalisedName\":\"Mining rush\",\"Commodity_Localised\":\"Bromellite\","
                + "\"Count\":28,\"DestinationSystem\":\"Coeus\",\"DestinationStation\":\"Foster Terminal\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(accept));
        assertEquals(1, tracker.getActive().size());

        String done = "{\"timestamp\":\"2026-05-22T11:00:00Z\",\"event\":\"MissionCompleted\","
                + "\"MissionID\":1055486629}";
        tracker.applyEvent((MissionCompletedEvent) parser.parseRecord(done));
        assertTrue(tracker.getActive().isEmpty());
    }

    @Test
    void cargoDepot_updatesProgress() {
        MissionTracker tracker = new MissionTracker();
        String accept = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":99,\"Commodity_Localised\":\"Osmium\",\"Count\":10,"
                + "\"DestinationSystem\":\"Coeus\",\"DestinationStation\":\"Foster Terminal\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(accept));
        String depot = "{\"timestamp\":\"2026-05-22T10:30:00Z\",\"event\":\"CargoDepot\","
                + "\"MissionID\":99,\"ItemsDelivered\":4,\"TotalItemsToDeliver\":10}";
        tracker.applyEvent((CargoDepotEvent) parser.parseRecord(depot));
        MissionRecord r = tracker.getActive().get(0);
        assertEquals(4, r.getItemsDelivered());
        assertEquals(10, r.getTotalItemsToDeliver());
    }

    @Test
    void missionsSnapshot_emptyActive_doesNotWipeTrackedMissions() {
        MissionTracker tracker = new MissionTracker();
        String accept = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":1,\"Name\":\"Mission_Mining_Boom\",\"Commodity_Localised\":\"Osmium\","
                + "\"Count\":28,\"DestinationSystem\":\"Coeus\",\"DestinationStation\":\"Foster Terminal\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(accept));
        String snap = "{\"timestamp\":\"2026-05-22T12:00:00Z\",\"event\":\"Missions\",\"Active\":[],\"Failed\":[],\"Complete\":[]}";
        tracker.applyEvent((MissionsEvent) parser.parseRecord(snap));
        assertEquals(1, tracker.getActive().size());
        assertEquals(1L, tracker.getActive().get(0).getMissionId());
    }

    @Test
    void missionsSnapshot_reconcilesActiveSet() {
        MissionTracker tracker = new MissionTracker();
        String accept = "{\"timestamp\":\"2026-05-22T10:00:00Z\",\"event\":\"MissionAccepted\","
                + "\"MissionID\":1,\"Name\":\"Mission_Courier\",\"DestinationSystem\":\"A\"}";
        tracker.applyEvent((MissionAcceptedEvent) parser.parseRecord(accept));
        String snap = "{\"timestamp\":\"2026-05-22T12:00:00Z\",\"event\":\"Missions\","
                + "\"Active\":[{\"MissionID\":2,\"Name\":\"Mission_Mining_Boom\",\"PassengerMission\":false,\"Expires\":3600}]}";
        tracker.applyEvent((MissionsEvent) parser.parseRecord(snap));
        assertEquals(1, tracker.getActive().size());
        assertEquals(2L, tracker.getActive().get(0).getMissionId());
    }
}
