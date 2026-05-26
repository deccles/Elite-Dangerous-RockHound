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
}
