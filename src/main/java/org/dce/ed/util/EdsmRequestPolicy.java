package org.dce.ed.util;

import java.time.Duration;

/** Shared request limits for normal EDSM traffic and Route lookups. */
public final class EdsmRequestPolicy {
    public static final int MAX_CONCURRENT_REQUESTS = 18;
    public static final Duration HEALTHY_MINIMUM_INTERVAL = Duration.ZERO;
    public static final Duration HEADERLESS_RATE_LIMIT_COOLDOWN = Duration.ofSeconds(35);

    private EdsmRequestPolicy() {
    }
}
