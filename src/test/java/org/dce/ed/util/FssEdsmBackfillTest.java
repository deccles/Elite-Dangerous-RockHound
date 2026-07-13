package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.state.SystemState;
import org.junit.jupiter.api.Test;

class FssEdsmBackfillTest {

    @Test
    void needsStandaloneEdsmBackfill_whenFssCompleteAndNoBodies() {
        SystemState state = new SystemState();
        state.setFssProgress(1.0);
        state.setTotalBodies(40);
        assertTrue(FssEdsmBackfill.needsStandaloneEdsmBackfill(state));
    }

    @Test
    void needsStandaloneEdsmBackfill_falseWhenBodiesPresent() {
        SystemState state = new SystemState();
        state.setFssProgress(1.0);
        state.getOrCreateBody(0);
        assertFalse(FssEdsmBackfill.needsStandaloneEdsmBackfill(state));
    }

    @Test
    void isFssComplete_allBodiesFoundFlag() {
        SystemState state = new SystemState();
        state.setAllBodiesFound(Boolean.TRUE);
        assertTrue(FssEdsmBackfill.isFssComplete(state));
    }
}
