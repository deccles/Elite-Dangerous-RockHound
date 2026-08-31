package org.dce.ed.route.pacing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs sequenced EDSM {@code showBodies} batches so pacing knobs can be compared
 * without using the Route tab or the shared daily cache.
 */
public final class EdsmPacingExperiment {

    public enum Outcome {
        SUCCESS,
        RATE_LIMITED,
        ERROR
    }

    public record Batch(int count, int concurrent, int restAfterMs, int launchDelayMs) {
        public Batch {
            if (count < 1) {
                throw new IllegalArgumentException("count must be >= 1");
            }
            if (concurrent < 1) {
                throw new IllegalArgumentException("concurrent must be >= 1");
            }
            if (restAfterMs < 0) {
                throw new IllegalArgumentException("restAfterMs must be >= 0");
            }
            if (launchDelayMs < 0) {
                throw new IllegalArgumentException("launchDelayMs must be >= 0");
            }
        }
    }

    public record QueryResult(String systemName, Outcome outcome, int statusCode, long elapsedMs, String detail) {
    }

    public record BatchResult(int batchNumber, Batch batch, int queried, int status200, int status429, int errors,
            long elapsedMs, List<QueryResult> queries) {
    }

    public record RunResult(List<BatchResult> batches, int unusedSystems) {
    }

    public interface BodiesQuery {
        QueryResult query(String systemName);
    }

    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public interface Listener {
        default void batchStarted(int batchNumber, Batch batch, int queried, int inFlight) {
        }

        default void queryFinished(QueryResult result) {
        }

        default void batchFinished(BatchResult result) {
        }
    }

    private EdsmPacingExperiment() {
    }

    public static RunResult run(List<String> systems, List<Batch> batches, BodiesQuery query, Sleeper sleeper,
            Listener listener) throws InterruptedException {
        Objects.requireNonNull(systems, "systems");
        Objects.requireNonNull(batches, "batches");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(sleeper, "sleeper");
        Listener log = listener != null ? listener : new Listener() {
        };
        List<String> names = new ArrayList<>();
        for (String system : systems) {
            if (system != null && !system.isBlank()) {
                names.add(system.trim());
            }
        }
        int cursor = 0;
        List<BatchResult> results = new ArrayList<>();
        for (int i = 0; i < batches.size(); i++) {
            throwIfInterrupted();
            Batch batch = batches.get(i);
            int remaining = names.size() - cursor;
            if (remaining <= 0) {
                break;
            }
            int queried = Math.min(batch.count(), remaining);
            List<String> slice = List.copyOf(names.subList(cursor, cursor + queried));
            cursor += queried;
            int inFlight = Math.min(batch.concurrent(), queried);
            log.batchStarted(i + 1, batch, queried, inFlight);
            BatchResult result = runBatch(i + 1, batch, slice, query, sleeper, log);
            results.add(result);
            log.batchFinished(result);
            if (i + 1 < batches.size() && batch.restAfterMs() > 0 && cursor < names.size()) {
                sleeper.sleep(batch.restAfterMs());
            }
        }
        return new RunResult(List.copyOf(results), names.size() - cursor);
    }

    public static Outcome classify(int statusCode, String body) {
        if (statusCode == 429) {
            return Outcome.RATE_LIMITED;
        }
        String text = body != null ? body.toLowerCase(Locale.ROOT) : "";
        if (text.contains("error code: 1015") || text.contains("http 429")) {
            return Outcome.RATE_LIMITED;
        }
        if (statusCode >= 200 && statusCode < 300) {
            return Outcome.SUCCESS;
        }
        return Outcome.ERROR;
    }

    private static BatchResult runBatch(int batchNumber, Batch batch, List<String> names, BodiesQuery query,
            Sleeper sleeper, Listener listener) throws InterruptedException {
        long started = System.currentTimeMillis();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(batch.concurrent(), names.size()),
                experimentThreads(batchNumber));
        List<Future<QueryResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < names.size(); i++) {
                throwIfInterrupted();
                if (i > 0 && batch.launchDelayMs() > 0) {
                    sleeper.sleep(batch.launchDelayMs());
                }
                String systemName = names.get(i);
                Callable<QueryResult> task = () -> {
                    QueryResult result = query.query(systemName);
                    listener.queryFinished(result);
                    return result;
                };
                futures.add(pool.submit(task));
            }
            List<QueryResult> queries = new ArrayList<>();
            int status200 = 0;
            int status429 = 0;
            int errors = 0;
            for (Future<QueryResult> future : futures) {
                throwIfInterrupted();
                QueryResult result = await(future);
                queries.add(result);
                if (result.outcome() == Outcome.SUCCESS) {
                    status200++;
                } else if (result.outcome() == Outcome.RATE_LIMITED) {
                    status429++;
                } else {
                    errors++;
                }
            }
            return new BatchResult(batchNumber, batch, names.size(), status200, status429, errors,
                    System.currentTimeMillis() - started, List.copyOf(queries));
        } finally {
            pool.shutdownNow();
        }
    }

    private static QueryResult await(Future<QueryResult> future) throws InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return new QueryResult("?", Outcome.ERROR, 0, 0L, cause.getClass().getSimpleName()
                    + (cause.getMessage() != null ? " — " + cause.getMessage() : ""));
        }
    }

    private static void throwIfInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("EDSM pacing experiment cancelled");
        }
    }

    private static ThreadFactory experimentThreads(int batchNumber) {
        AtomicInteger next = new AtomicInteger(1);
        return task -> {
            Thread thread = new Thread(task, "EdsmPacing-b" + batchNumber + "-" + next.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
