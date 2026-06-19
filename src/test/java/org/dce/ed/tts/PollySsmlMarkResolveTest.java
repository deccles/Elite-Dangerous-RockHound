package org.dce.ed.tts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PollySsmlMarkResolveTest {

    @Test
    void missingFirstMarkDefaultsToZero() {
        Map<String, Integer> times = Map.of("C1", 120);
        assertEquals(0, PollyTtsCached.resolveSsmlMarkStartMs(0, "C0", List.of("C0", "C1"), times));
    }

    @Test
    void missingLaterMarkUsesPreviousKnownMark() {
        Map<String, Integer> times = new LinkedHashMap<>();
        times.put("C0", 0);
        times.put("C2", 200);
        assertEquals(0, PollyTtsCached.resolveSsmlMarkStartMs(1, "C1", List.of("C0", "C1", "C2"), times));
    }

    @Test
    void missingEndMarkUsesNextKnownOrTotal() {
        Map<String, Integer> times = Map.of("C0", 0, "C2", 300);
        assertEquals(300, PollyTtsCached.resolveSsmlMarkEndMs(0, List.of("C0", "C1", "C2"), times, 500));
        assertEquals(500, PollyTtsCached.resolveSsmlMarkEndMs(2, List.of("C0", "C1", "C2"), times, 500));
    }
}
