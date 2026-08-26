package org.dce.ed.route;

/** Visual semantics shared by the Route status renderer and its tests. */
public record RouteStatusPresentation(String symbol, boolean filled) {
    private static final long GLOW_RAMP_MS = 100L;
    private static final long GLOW_DURATION_MS = 500L;

    public static RouteStatusPresentation forStatus(RouteScanStatus status) {
        if (status == RouteScanStatus.PENDING || status == RouteScanStatus.DEFERRED) {
            return new RouteStatusPresentation("", false);
        }
        if (status == RouteScanStatus.UNKNOWN) {
            return new RouteStatusPresentation("?", true);
        }
        return new RouteStatusPresentation("", true);
    }

    public static float glowAlpha(long elapsedMs) {
        if (elapsedMs <= 0L || elapsedMs >= GLOW_DURATION_MS) {
            return 0.0f;
        }
        if (elapsedMs < GLOW_RAMP_MS) {
            return (float) elapsedMs / GLOW_RAMP_MS;
        }
        return 1.0f - (float) (elapsedMs - GLOW_RAMP_MS) / (GLOW_DURATION_MS - GLOW_RAMP_MS);
    }
}
