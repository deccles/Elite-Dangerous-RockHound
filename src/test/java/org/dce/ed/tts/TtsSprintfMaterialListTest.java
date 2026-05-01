package org.dce.ed.tts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TtsSprintfMaterialListTest {

    @Test
    void materialSplitsOnWhitespaceLikeSpecies() {
        TtsSprintf sp = new TtsSprintf(new PollyTtsCached());
        // {material} is split on whitespace; {n} expands to word tokens (e.g. 12 -> "twelve").
        List<String> chunks = sp.formatToUtteranceChunks("Prospector found {material} at {n} percent.",
                "Low Temperature Diamonds", 12);
        String diag = "chunks=" + chunks;
        assertTrue(chunks.contains("Low"), diag);
        assertTrue(chunks.contains("Temperature"), diag);
        assertTrue(chunks.contains("Diamonds"), diag);
        assertTrue(chunks.contains("twelve"), diag);
    }

    @Test
    void listStaysSingleChunkSoProsodyMatchesJoinWithAnd() {
        TtsSprintf sp = new TtsSprintf(new PollyTtsCached());
        List<String> chunks = sp.formatToUtteranceChunks("Prospector found {list} from {min} to {max} percent.",
                "Tritium and Platinum", 10, 90);
        assertTrue(chunks.stream().anyMatch(c -> c.contains("Tritium") && c.contains("Platinum")),
                "chunks=" + chunks);
        assertTrue(chunks.contains("ten"), "chunks=" + chunks);
        assertTrue(chunks.contains("ninety"), "chunks=" + chunks);
    }

    @Test
    void singleMaterialPercentUsesWordFifteenNotDigitChunks() {
        TtsSprintf sp = new TtsSprintf(new PollyTtsCached());
        List<String> chunks = sp.formatToUtteranceChunks("Prospector found {material} at {n} percent.", "Gold", 15);
        assertTrue(chunks.contains("fifteen"), "chunks=" + chunks);
        assertFalse(chunks.contains("10"), "should not split into digit-style tens chunk, chunks=" + chunks);
    }
}
