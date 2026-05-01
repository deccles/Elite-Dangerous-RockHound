package org.dce.ed.tts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TtsSprintfMaterialListTest {

    @Test
    void materialSplitsOnWhitespaceLikeSpecies() {
        TtsSprintf sp = new TtsSprintf(new PollyTtsCached());
        // {material} is split on whitespace; {n} may expand to several chunks (e.g. 12 -> "10","2"), so do not
        // assert a single literal digit string for the percent value.
        List<String> chunks = sp.formatToUtteranceChunks("Prospector found {material} at {n} percent.",
                "Low Temperature Diamonds", 12);
        String diag = "chunks=" + chunks;
        assertTrue(chunks.contains("Low"), diag);
        assertTrue(chunks.contains("Temperature"), diag);
        assertTrue(chunks.contains("Diamonds"), diag);
    }

    @Test
    void listStaysSingleChunkSoProsodyMatchesJoinWithAnd() {
        TtsSprintf sp = new TtsSprintf(new PollyTtsCached());
        List<String> chunks = sp.formatToUtteranceChunks("Prospector found {list} from {min} to {max} percent.",
                "Tritium and Platinum", 10, 90);
        assertTrue(chunks.stream().anyMatch(c -> c.contains("Tritium") && c.contains("Platinum")),
                "chunks=" + chunks);
    }
}
