package org.dce.ed;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Global exception reporting hook for cross-cutting error surfacing.
 *
 * <p>Production sets this from {@link OverlayFrame}; tests can swap it out.</p>
 */
public final class ExceptionReporting {

    private static final ExceptionReporter NOOP = (t, ctx) -> {
        // Intentionally silent by default; OverlayFrame will replace this.
    };

    private static final AtomicReference<ExceptionReporter> reporterRef =
            new AtomicReference<>(NOOP);

    private ExceptionReporting() {
    }

    public static void setReporter(ExceptionReporter reporter) {
        reporterRef.set(Objects.requireNonNullElse(reporter, NOOP));
    }

    public static void report(Throwable t, String context) {
        if (t == null) {
            return;
        }
        ExceptionReporter r = reporterRef.get();
        if (r == null) {
            return;
        }
        try {
            r.report(t, context);
        } catch (Exception ignored) {
            // Never let reporting crash the caller.
        }
    }
}

