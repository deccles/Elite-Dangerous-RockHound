package org.dce.ed.route;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded, FIFO route lookup queue. Submissions start in route-row order. */
public final class RouteEdsmWorkQueue implements AutoCloseable {
    private final ExecutorService executor;

    public RouteEdsmWorkQueue(int workers, String threadNamePrefix) {
        AtomicInteger nextThread = new AtomicInteger(1);
        ThreadFactory threads = task -> {
            Thread thread = new Thread(task, threadNamePrefix + "-" + nextThread.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
        executor = Executors.newFixedThreadPool(workers, threads);
    }

    public void submit(Runnable work) {
        executor.execute(work);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
