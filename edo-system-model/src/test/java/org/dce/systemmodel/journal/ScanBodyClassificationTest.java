package org.dce.systemmodel.journal;

import org.dce.systemmodel.journal.ScanRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanBodyClassificationTest {

    @Test
    void detectsRing_fromBodyTypeNameOrPlanetClass() {
        assertTrue(ScanBodyClassification.isRing(scan("Ring", "Metal-rich")));
        assertTrue(ScanBodyClassification.isRing(scan("Planet", "Planetary Ring")));
        assertTrue(ScanBodyClassification.isRing(
                new ScanRecord(
                        java.time.Instant.EPOCH, 1, "Eol Prou NN-Y b31-0 5 A Ring", "Planet", "",
                        0, 0, 0, 0, 0, 0, 0, 0, 0, java.util.List.of(), null, true, false)));
        assertFalse(ScanBodyClassification.isRing(scan("Planet", "Sudarsky class I gas giant")));
    }

    private static ScanRecord scan(String bodyType, String subType) {
        return new ScanRecord(
                java.time.Instant.EPOCH, 1, "Test", bodyType, subType,
                0, 0, 0, 0, 0, 0, 0, 0, 0, java.util.List.of(), null, true, false);
    }
}
