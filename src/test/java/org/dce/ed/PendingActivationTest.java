package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class PendingActivationTest {

    @Test
    void activationRequestedBeforeStartupRunsWhenTheWindowRegisters() {
        PendingActivation activation = new PendingActivation();
        AtomicInteger activations = new AtomicInteger();

        activation.request();
        activation.register(activations::incrementAndGet);

        assertEquals(1, activations.get());
    }

    @Test
    void activationRequestedAfterStartupRunsImmediately() {
        PendingActivation activation = new PendingActivation();
        AtomicInteger activations = new AtomicInteger();
        activation.register(activations::incrementAndGet);

        activation.request();

        assertEquals(1, activations.get());
    }
}
