package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class EliteWindowFocusPollingTest {

    @Test
    void acceptsConditionThatBecomesTrueDuringBoundedWait() {
        AtomicInteger checks = new AtomicInteger();

        assertTrue(EliteWindowFocus.waitForCondition(
                () -> checks.incrementAndGet() >= 3, 500L, 1L));
    }

    @Test
    void returnsFalseWhenConditionNeverBecomesTrue() {
        assertFalse(EliteWindowFocus.waitForCondition(() -> false, 15L, 1L));
    }
}
