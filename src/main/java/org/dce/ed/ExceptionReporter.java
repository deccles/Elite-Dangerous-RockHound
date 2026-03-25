package org.dce.ed;

@FunctionalInterface
public interface ExceptionReporter {
    /**
     * Report an exception to an appropriate surface (e.g., title-bar alert, logs).
     * <p>
     * Implementations must be resilient: they should not throw.
     */
    void report(Throwable t, String context);
}

