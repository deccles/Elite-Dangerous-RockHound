package org.dce.ed.tools.pacing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EdsmPacingExperimentSettingsTest {

    @Test
    void encodesAndDecodesBatches() {
        List<EdsmPacingExperimentSettings.BatchSpec> batches = List.of(
                new EdsmPacingExperimentSettings.BatchSpec(25, 18, 0, 0),
                new EdsmPacingExperimentSettings.BatchSpec(8, 2, 10, 0));
        String encoded = EdsmPacingExperimentSettings.encodeBatches(batches);
        assertEquals("25,18,0,0,1;8,2,10,0,1", encoded);
        assertEquals(batches, EdsmPacingExperimentSettings.decodeBatches(encoded));
    }

    @Test
    void decodesLegacyFourFieldCsvAsOneRepeat() {
        assertEquals(List.of(new EdsmPacingExperimentSettings.BatchSpec(4, 2, 8, 0, 1)),
                EdsmPacingExperimentSettings.decodeBatches("4,2,8,0"));
        assertEquals(List.of(new EdsmPacingExperimentSettings.BatchSpec(4, 2, 8, 0, 5)),
                EdsmPacingExperimentSettings.decodeBatches("4,2,8,0,5"));
    }

    @Test
    void skipsBrokenTokensAndClampsRanges() {
        List<EdsmPacingExperimentSettings.BatchSpec> decoded =
                EdsmPacingExperimentSettings.decodeBatches("nope;25,18,0;0,0,-1,-5;3,2,8,0");
        assertEquals(2, decoded.size());
        assertEquals(new EdsmPacingExperimentSettings.BatchSpec(1, 1, 0, 0), decoded.get(0));
        assertEquals(new EdsmPacingExperimentSettings.BatchSpec(3, 2, 8, 0), decoded.get(1));
    }

    @Test
    void emptyEncodedListIsEmpty() {
        assertTrue(EdsmPacingExperimentSettings.decodeBatches("").isEmpty());
        assertTrue(EdsmPacingExperimentSettings.decodeBatches(null).isEmpty());
    }
}
