package org.dce.ed.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.dce.ed.logreader.event.FsdJumpEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class SystemEventProcessorFsdJumpTest {

    private SystemState state;
    private SystemEventProcessor processor;

    @BeforeEach
    void setUp() {
        state = new SystemState();
        processor = new SystemEventProcessor("test", state);
    }

    @Test
    void fsdJumpWithNullDocked_updatesSystem() {
        FsdJumpEvent e = new FsdJumpEvent(Instant.parse("2025-01-01T12:00:00Z"), new JsonObject(),
                "Sol", 10477373803L, new double[] { 0, 0, 0 },
                null, 0, null, 0, 0, 0, null);
        processor.handleEvent(e);
        assertEquals("Sol", state.getSystemName());
        assertEquals(10477373803L, state.getSystemAddress());
    }

    @Test
    void fsdJumpWithDockedFalse_doesNotUpdateSystem() {
        state.setSystemName("Old");
        state.setSystemAddress(1L);
        FsdJumpEvent e = new FsdJumpEvent(Instant.parse("2025-01-01T12:00:00Z"), new JsonObject(),
                "New", 2L, new double[] { 1, 1, 1 },
                null, 0, null, 0, 0, 0, Boolean.FALSE);
        processor.handleEvent(e);
        assertEquals("Old", state.getSystemName());
        assertEquals(1L, state.getSystemAddress());
        assertFalse(state.isDocked());
    }

    @Test
    void fsdJumpWithDockedTrue_updatesSystem() {
        FsdJumpEvent e = new FsdJumpEvent(Instant.parse("2025-01-01T12:00:00Z"), new JsonObject(),
                "Carrier", 99L, new double[] { 5, 5, 5 },
                null, 0, null, 0, 0, 0, Boolean.TRUE);
        processor.handleEvent(e);
        assertEquals("Carrier", state.getSystemName());
        assertEquals(99L, state.getSystemAddress());
        assertTrue(state.isDocked());
    }
}
