package org.dce.ed.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BodyInfoTest {

    @Test
    void completingGenusRemovesOnlyThatGenusFromAbandonedSamplePins() {
        BodyInfo body = new BodyInfo();

        body.recordBioSample("Osseus Fractus", "Log");
        body.recordBioSamplePoint("Osseus Fractus", "Log", 1.0, 1.0);
        body.recordBioSample("Bacterium Tela", "Log");
        body.recordBioSamplePoint("Bacterium Tela", "Log", 2.0, 2.0);
        body.recordBioSample("Bacterium Tela", "Analyse");

        assertFalse(body.getAbandonedBioSamplePointsSnapshot().containsKey("Bacterium Tela"));
        assertTrue(body.getAbandonedBioSamplePointsSnapshot().containsKey("Osseus Fractus"));
    }

    @Test
    void resumingParkedGenusKeepsPreviousLocationAsMapHistory() {
        BodyInfo body = new BodyInfo();

        body.recordBioSample("Osseus Fractus", "Log");
        body.recordBioSamplePoint("Osseus Fractus", "Log", 1.0, 1.0);
        body.recordBioSample("Bacterium Tela", "Log");
        body.recordBioSamplePoint("Bacterium Tela", "Log", 2.0, 2.0);
        body.recordBioSample("Osseus Fractus", "Log");
        body.recordBioSamplePoint("Osseus Fractus", "Log", 3.0, 3.0);

        assertTrue(body.getAbandonedBioSamplePointsSnapshot().containsKey("Osseus Fractus"));
        assertEquals(1, body.getAbandonedBioSamplePointsSnapshot().get("Osseus Fractus").size());
        assertEquals(1, body.getBioSamplePointsSnapshot().get("Osseus Fractus").size());
    }

    @Test
    void analyseMarksSpeciesCompleteWithoutInflatingOthers() {
        BodyInfo body = new BodyInfo();
        body.recordBioSample("Osseus Spiralis", "Log");
        body.recordBioSample("Osseus Spiralis", "Sample");
        body.recordBioSample("Osseus Spiralis", "Sample");
        body.recordBioSample("Osseus Spiralis", "Analyse");
        assertTrue(body.isBioSpeciesAnalysed("Osseus Spiralis"));
        assertEquals(3, body.getBioSampleCount("Osseus Spiralis"));

        body.recordBioSample("Fungoida Setisis", "Log");
        body.recordBioSample("Fungoida Setisis", "Sample");
        assertFalse(body.isBioSpeciesAnalysed("Fungoida Setisis"));
        assertEquals(2, body.getBioSampleCount("Fungoida Setisis"));
    }

    @Test
    void switchingGenusInRecordBioSampleParksPinsAndClearsCountWithoutLatLon() {
        BodyInfo body = new BodyInfo();

        body.recordBioSample("Fungoida Setisis", "Log");
        body.recordBioSamplePoint("Fungoida Setisis", "Log", 10.0, 20.0);
        body.recordBioSample("Fungoida Setisis", "Sample");
        body.recordBioSamplePoint("Fungoida Setisis", "Sample", 11.0, 21.0);
        assertEquals(2, body.getBioSampleCount("Fungoida Setisis"));

        body.recordBioSample("Tussock Catena", "Log");

        assertEquals(0, body.getBioSampleCount("Fungoida Setisis"));
        assertEquals(1, body.getBioSampleCount("Tussock Catena"));
        assertTrue(body.getAbandonedBioSamplePointsSnapshot().containsKey("Fungoida Setisis"));
        assertEquals(2, body.getAbandonedBioSamplePointsSnapshot().get("Fungoida Setisis").size());
        assertFalse(body.getBioSamplePointsSnapshot().containsKey("Fungoida Setisis"));
    }

    @Test
    void reconcileStalePartialBioStateParksOnlyNonActivePartialCounts() {
        BodyInfo body = new BodyInfo();
        body.setBioSampleCounts(new java.util.HashMap<>(java.util.Map.of(
                "Fungoida Setisis", 2,
                "Tussock Catena", 1)));
        java.util.Map<String, java.util.List<BodyInfo.BioSamplePoint>> pts = new java.util.HashMap<>();
        pts.put("Fungoida Setisis", java.util.List.of(
                new BodyInfo.BioSamplePoint(1.0, 1.0),
                new BodyInfo.BioSamplePoint(2.0, 2.0)));
        pts.put("Tussock Catena", java.util.List.of(new BodyInfo.BioSamplePoint(3.0, 3.0)));
        body.setBioSamplePoints(pts);
        body.setActiveIncompleteBioKey("Tussock Catena");

        body.reconcileStalePartialBioState();

        assertEquals(0, body.getBioSampleCount("Fungoida Setisis"));
        assertEquals(1, body.getBioSampleCount("Tussock Catena"));
        assertTrue(body.getAbandonedBioSamplePointsSnapshot().containsKey("Fungoida Setisis"));
        assertFalse(body.getAbandonedBioSamplePointsSnapshot().containsKey("Tussock Catena"));
    }

    @Test
    void replayGenusSwitchParkingFromJournalParksSupersededPartial() {
        BodyInfo body = new BodyInfo();
        body.setBioSampleCounts(new java.util.HashMap<>(java.util.Map.of("Fungoida Setisis", 2)));
        java.util.Map<String, java.util.List<BodyInfo.BioSamplePoint>> pts = new java.util.HashMap<>();
        pts.put("Fungoida Setisis", java.util.List.of(
                new BodyInfo.BioSamplePoint(1.0, 1.0),
                new BodyInfo.BioSamplePoint(2.0, 2.0)));
        body.setBioSamplePoints(pts);

        body.replayGenusSwitchParkingFromJournal(java.util.List.of(
                new BodyInfo.BioScanReplayEntry("Fungoida Setisis", "Log"),
                new BodyInfo.BioScanReplayEntry("Fungoida Setisis", "Sample"),
                new BodyInfo.BioScanReplayEntry("Tussock Catena", "Log")));

        assertEquals(0, body.getBioSampleCount("Fungoida Setisis"));
        assertTrue(body.getAbandonedBioSamplePointsSnapshot().containsKey("Fungoida Setisis"));
        assertEquals(2, body.getAbandonedBioSamplePointsSnapshot().get("Fungoida Setisis").size());
    }

    @Test
    void sanitizeInflated_dropsFalseCompleteWithoutAnalyse() {
        BodyInfo body = new BodyInfo();
        body.setBioSampleCounts(new java.util.HashMap<>(java.util.Map.of("Fungoida Setisis", 3)));
        java.util.Map<String, java.util.List<BodyInfo.BioSamplePoint>> pts = new java.util.HashMap<>();
        pts.put("Fungoida Setisis", java.util.List.of(
                new BodyInfo.BioSamplePoint(1.0, 1.0),
                new BodyInfo.BioSamplePoint(2.0, 2.0)));
        body.setBioSamplePoints(pts);
        body.sanitizeInflatedBioSampleCounts();
        assertEquals(2, body.getBioSampleCount("Fungoida Setisis"));
        assertFalse(body.isBioSpeciesAnalysed("Fungoida Setisis"));
    }
}
