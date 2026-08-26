package org.dce.ed.route;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Preserves completed route scan states across display snapshot rebuilds. */
public final class RouteScanStatusMemory {
    private final Map<Long, RouteScanStatus> byAddress = new ConcurrentHashMap<>();
    private final Map<String, RouteScanStatus> byName = new ConcurrentHashMap<>();

    public void remember(RouteEntry entry, RouteScanStatus status) {
        if (entry == null || status == null || status.needsEdsmQuery()) {
            return;
        }
        if (entry.systemAddress != 0L) {
            byAddress.put(Long.valueOf(entry.systemAddress), status);
        }
        String name = normalizedName(entry.systemName);
        if (name != null) {
            byName.put(name, status);
        }
    }

    public void applyTo(RouteEntry entry) {
        if (entry == null || (entry.status != null && !entry.status.needsEdsmQuery())) {
            return;
        }
        RouteScanStatus remembered = entry.systemAddress != 0L
                ? byAddress.get(Long.valueOf(entry.systemAddress)) : null;
        if (remembered == null) {
            String name = normalizedName(entry.systemName);
            remembered = name != null ? byName.get(name) : null;
        }
        if (remembered != null) {
            entry.status = remembered;
        }
    }

    private static String normalizedName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
