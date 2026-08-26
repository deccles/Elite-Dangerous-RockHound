package org.dce.ed.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class RouteEdsmWorkQueueTest {

    @Test
    void onlyTopmostPairStartsWhileWorkersAreOccupied() throws Exception {
        RouteEdsmWorkQueue queue = new RouteEdsmWorkQueue(2, "test-route-edsm");
        CountDownLatch firstPairStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        List<Integer> started = java.util.Collections.synchronizedList(new ArrayList<>());
        try {
            for (int row = 0; row < 6; row++) {
                int captured = row;
                queue.submit(() -> {
                    started.add(Integer.valueOf(captured));
                    firstPairStarted.countDown();
                    try {
                        release.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            assertTrue(firstPairStarted.await(1, TimeUnit.SECONDS));
            assertEquals(Set.of(0, 1), Set.copyOf(started));
        } finally {
            release.countDown();
            queue.close();
        }
    }
}
