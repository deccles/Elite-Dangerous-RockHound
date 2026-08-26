package org.dce.ed.route;

import java.util.List;

/** Applies the rolling EDSM lookup window to unresolved plotted route systems. */
public final class RouteEdsmWindow {
    private RouteEdsmWindow() {
    }

    public static void apply(List<RouteEntry> rows, int windowSize) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        int currentRow = findCurrentRow(rows);
        int eligibleSystems = 0;
        for (int row = 0; row < rows.size(); row++) {
            RouteEntry entry = rows.get(row);
            if (entry == null || entry.isBodyRow || entry.isSynthetic) {
                continue;
            }
            boolean eligible = row >= currentRow && eligibleSystems < Math.max(0, windowSize);
            if (row >= currentRow) {
                eligibleSystems++;
            }
            if (entry.status == RouteScanStatus.PENDING || entry.status == RouteScanStatus.DEFERRED) {
                entry.status = eligible ? RouteScanStatus.PENDING : RouteScanStatus.DEFERRED;
            }
        }
    }

    private static int findCurrentRow(List<RouteEntry> rows) {
        for (int row = 0; row < rows.size(); row++) {
            RouteEntry entry = rows.get(row);
            if (entry != null && !entry.isBodyRow && !entry.isSynthetic
                    && entry.markerKind == RouteMarkerKind.CURRENT) {
                return row;
            }
        }
        return 0;
    }
}
