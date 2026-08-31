package org.dce.ed.tools.pacing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class EdsmPacingExperimentTest {

    @Test
    void sampleListHasFiftyNamedSystems() {
        assertEquals(50, EdsmPacingSampleSystems.NAMES.size());
        assertEquals(50, EdsmPacingSampleSystems.NAMES.stream().distinct().count());
    }

    @Test
    void expandsNamesToMatchBatchDemand() {
        assertEquals(18 + 8 * 20, EdsmPacingSampleSystems.demand(List.of(
                new EdsmPacingExperimentSettings.BatchSpec(18, 18, 16, 0, 1),
                new EdsmPacingExperimentSettings.BatchSpec(8, 8, 6, 0, 20))));
        List<String> names = EdsmPacingSampleSystems.namesFor(60);
        assertEquals(60, names.size());
        assertEquals(60, names.stream().distinct().count());
        assertEquals(EdsmPacingSampleSystems.NAMES, names.subList(0, 50));
        assertEquals(List.of("Eol Prou GW-U c3-58", "Eol Prou DL-X d1-1065"),
                EdsmPacingSampleSystems.namesFor(List.of(
                        "Eol Prou GW-U c3-58",
                        "Eol Prou DL-X d1-1065",
                        "Eol Prou DL-X d1-4"), 2));
    }

    @Test
    void classifyTreats429And1015AsRateLimited() {
        assertEquals(EdsmPacingExperiment.Outcome.RATE_LIMITED, EdsmPacingExperiment.classify(429, "html"));
        assertEquals(EdsmPacingExperiment.Outcome.RATE_LIMITED,
                EdsmPacingExperiment.classify(403, "error code: 1015"));
        assertEquals(EdsmPacingExperiment.Outcome.SUCCESS, EdsmPacingExperiment.classify(200, "[]"));
        assertEquals(EdsmPacingExperiment.Outcome.ERROR, EdsmPacingExperiment.classify(503, "busy"));
    }

    @Test
    void errorCatalogListsEachReceivedCodeOnceWithADescription() {
        assertEquals(List.of("1015", "403"),
                EdsmPacingErrorCatalog.codesFrom(403, "error code: 1015"));
        assertEquals(List.of("1015", "429"),
                EdsmPacingErrorCatalog.codesFrom(429, "error code: 1015"));
        assertEquals(List.of(), EdsmPacingErrorCatalog.codesFrom(200, "[]"));
        assertEquals("Too Many Requests", EdsmPacingErrorCatalog.describe("429"));
        assertEquals("Cloudflare: this IP sent too many requests", EdsmPacingErrorCatalog.describe("1015"));
    }

    @Test
    void liveHttpUrlsAreUniqueAndDoNotShareTheDailyCacheKey() {
        String first = EdsmPacingBodiesHttp.bodiesUrl("Eol Prou GW-U c3-58", 1L);
        String second = EdsmPacingBodiesHttp.bodiesUrl("Eol Prou GW-U c3-58", 2L);
        assertTrue(first.contains("edoPacing=1"));
        assertTrue(second.contains("edoPacing=2"));
        assertNotEquals(first, second);
        assertTrue(first.contains("showInformation=1"));
        assertTrue(first.startsWith("https://www.edsm.net/api-system-v1/bodies?"));
    }

    @Test
    void runsBatchesInOrderWithConfiguredRestAndLeavesUnusedSystems() throws Exception {
        List<String> systems = List.of("A", "B", "C", "D", "E", "F");
        List<EdsmPacingExperiment.Batch> batches = List.of(
                new EdsmPacingExperiment.Batch(3, 2, 250, 0),
                new EdsmPacingExperiment.Batch(2, 1, 0, 0));
        RecordingQuery query = new RecordingQuery();
        RecordingSleeper sleeper = new RecordingSleeper();

        EdsmPacingExperiment.RunResult result = EdsmPacingExperiment.run(systems, batches, query, sleeper, null);

        assertEquals(List.of("A", "B", "C", "D", "E"), query.names);
        assertEquals(1, result.unusedSystems());
        assertEquals(2, result.batches().size());
        assertEquals(3, result.batches().get(0).queried());
        assertEquals(3, result.batches().get(0).status200());
        assertEquals(2, result.batches().get(1).queried());
        assertEquals(List.of(250L), sleeper.sleeps);
    }

    @Test
    void launchDelaySleepsBetweenSubmits() throws Exception {
        RecordingSleeper sleeper = new RecordingSleeper();
        EdsmPacingExperiment.Batch batch = new EdsmPacingExperiment.Batch(3, 1, 0, 40);
        EdsmPacingExperiment.run(List.of("A", "B", "C"), List.of(batch), new RecordingQuery(), sleeper, null);
        assertEquals(List.of(40L, 40L), sleeper.sleeps);
    }

    @Test
    void concurrentCapIsHonored() throws Exception {
        AtomicInteger current = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        EdsmPacingExperiment.BodiesQuery query = name -> {
            int now = current.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(40);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            current.decrementAndGet();
            return new EdsmPacingExperiment.QueryResult(name, EdsmPacingExperiment.Outcome.SUCCESS, 200, 40, null);
        };
        EdsmPacingExperiment.run(List.of("A", "B", "C", "D"),
                List.of(new EdsmPacingExperiment.Batch(4, 2, 0, 0)), query, ms -> {
                }, null);
        assertTrue(peak.get() <= 2, "peak concurrent was " + peak.get());
        assertTrue(peak.get() >= 1);
    }

    private static final class RecordingQuery implements EdsmPacingExperiment.BodiesQuery {
        private final List<String> names = new ArrayList<>();

        @Override
        public synchronized EdsmPacingExperiment.QueryResult query(String systemName) {
            names.add(systemName);
            return new EdsmPacingExperiment.QueryResult(systemName, EdsmPacingExperiment.Outcome.SUCCESS, 200, 1, null);
        }
    }

    private static final class RecordingSleeper implements EdsmPacingExperiment.Sleeper {
        private final List<Long> sleeps = new ArrayList<>();

        @Override
        public void sleep(long millis) {
            sleeps.add(Long.valueOf(millis));
        }
    }
}
