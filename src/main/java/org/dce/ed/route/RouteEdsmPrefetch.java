package org.dce.ed.route;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Selects unresolved route systems for background EDSM prefetch. */
public final class RouteEdsmPrefetch {
    private RouteEdsmPrefetch() {
    }

    public static boolean openingWindowBusy(List<RouteEntry> rows) {
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        for (RouteEntry entry : rows) {
            if (entry != null && !entry.isBodyRow && entry.status == RouteScanStatus.PENDING) {
                return true;
            }
        }
        return false;
    }

    /**
     * Unresolved plotted systems in route order: window {@code PENDING} hops first,
     * then {@code DEFERRED} prefetch targets. After the opening burst, both are
     * fetched by the same paced wave scheduler.
     */
    public static List<Integer> nextUnresolvedRowIndexes(List<RouteEntry> rows, int max,
            Predicate<RouteEntry> skip) {
        List<Integer> selected = new ArrayList<>();
        if (rows == null || max <= 0) {
            return selected;
        }
        for (int row = 0; row < rows.size(); row++) {
            RouteEntry entry = rows.get(row);
            if (entry == null || entry.isBodyRow || entry.isSynthetic) {
                continue;
            }
            if (entry.status == null || !entry.status.isUnresolved()) {
                continue;
            }
            if (skip != null && skip.test(entry)) {
                continue;
            }
            selected.add(Integer.valueOf(row));
            if (selected.size() >= max) {
                break;
            }
        }
        return selected;
    }

    public static RouteScanStatus statusFromBodies(org.dce.ed.edsm.BodiesResponse bodies, boolean visited) {
        if (bodies != null && bodies.bodies != null && !bodies.bodies.isEmpty()) {
            if (bodies.bodyCount != bodies.bodies.size()) {
                return visited
                        ? RouteScanStatus.BODYCOUNT_MISMATCH_VISITED
                        : RouteScanStatus.BODYCOUNT_MISMATCH_NOT_VISITED;
            }
            return visited
                    ? RouteScanStatus.FULLY_DISCOVERED_VISITED
                    : RouteScanStatus.FULLY_DISCOVERED_NOT_VISITED;
        }
        return visited ? RouteScanStatus.DISCOVERY_MISSING_VISITED : RouteScanStatus.UNKNOWN;
    }
}
