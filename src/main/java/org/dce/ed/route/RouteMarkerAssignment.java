package org.dce.ed.route;

import java.util.List;

/**
 * Assigns {@link RouteMarkerKind} for current system, side-trip target, and pending jump.
 * {@code chargingActive} replaces reading a live Swing timer (e.g. pass {@code jumpFlashTimer.isRunning()} from UI).
 */
public final class RouteMarkerAssignment {

    private RouteMarkerAssignment() {
    }

    public static void applyMarkerKinds(List<RouteEntry> entries,
            String currentName,
            long currentSystemAddress,
            String targetSystemName,
            long targetSystemAddress,
            Long destinationSystemAddress,
            Integer destinationBodyId,
            String destinationName,
            String pendingJumpLockedName,
            long pendingJumpLockedAddress,
            boolean chargingActive) {
        if (entries == null) {
            return;
        }

        for (RouteEntry e : entries) {
            if (e == null) {
                continue;
            }
            if (e.isBodyRow) {
                continue;
            }
            e.markerKind = RouteMarkerKind.NONE;
        }

        int currentRow = RouteGeometry.findSystemRow(entries, currentName, currentSystemAddress);
        if (currentRow >= 0) {
            RouteEntry cur = entries.get(currentRow);
            if (cur != null && !cur.isBodyRow) {
                cur.markerKind = RouteMarkerKind.CURRENT;
            }
        }

        RouteEntry nextHop = null;
        if (currentRow >= 0) {
            for (int i = currentRow + 1; i < entries.size(); i++) {
                RouteEntry e = entries.get(i);
                if (e == null) {
                    continue;
                }
                if (e.isBodyRow) {
                    continue;
                }
                if (e.isSynthetic) {
                    continue;
                }
                nextHop = e;
                break;
            }
        }

        boolean hasSideTripTarget = (targetSystemName != null && !targetSystemName.isBlank());

        long resolvedCurrentAddress = 0L;
        if (currentRow >= 0) {
            RouteEntry cur = entries.get(currentRow);
            if (cur != null) {
                resolvedCurrentAddress = cur.systemAddress;
            }
        }
        if (resolvedCurrentAddress == 0L) {
            resolvedCurrentAddress = currentSystemAddress;
        }

        boolean hasLocalBodyDestination = false;
        if (destinationBodyId != null && destinationSystemAddress != null && resolvedCurrentAddress != 0L) {
            if (destinationSystemAddress.longValue() == resolvedCurrentAddress) {
                hasLocalBodyDestination = true;
            }
        }

        RouteEntry pending = null;

        if (!hasSideTripTarget) {
            if (chargingActive && destinationBodyId == null) {
                String destNameForPending = pendingJumpLockedName;
                long destAddrForPending = pendingJumpLockedAddress;

                if (destNameForPending == null || destNameForPending.isBlank()) {
                    destNameForPending = destinationName;
                }
                if (destAddrForPending == 0L && destinationSystemAddress != null) {
                    destAddrForPending = destinationSystemAddress.longValue();
                }

                if (destAddrForPending != 0L || (destNameForPending != null && !destNameForPending.isBlank())) {
                    int destRow = RouteGeometry.findSystemRow(entries, destNameForPending, destAddrForPending);
                    if (destRow >= 0 && destRow != currentRow) {
                        RouteEntry e = entries.get(destRow);
                        if (e != null && !e.isBodyRow) {
                            pending = e;
                        }
                    }
                }
            }

            if (pending == null && nextHop != null && !hasLocalBodyDestination) {
                pending = nextHop;
            }

            if (pending != null) {
                pending.markerKind = RouteMarkerKind.PENDING_JUMP;
            }
        }

        if (hasSideTripTarget) {
            for (RouteEntry e : entries) {
                if (!matchesTarget(e, targetSystemName, targetSystemAddress)) {
                    continue;
                }
                if (e.markerKind == RouteMarkerKind.NONE) {
                    if (chargingActive) {
                        e.markerKind = RouteMarkerKind.PENDING_JUMP;
                    } else {
                        e.markerKind = RouteMarkerKind.TARGET;
                    }
                }
                break;
            }
        }
    }

    private static boolean matchesTarget(RouteEntry e, String targetSystemName, long targetSystemAddress) {
        if (e == null) {
            return false;
        }
        if (e.isBodyRow) {
            return false;
        }

        if (targetSystemAddress != 0L && e.systemAddress != 0L) {
            if (e.systemAddress == targetSystemAddress) {
                return true;
            }
        }

        if (targetSystemName != null && !targetSystemName.isBlank() && e.systemName != null) {
            if (targetSystemName.equals(e.systemName)) {
                return true;
            }
        }

        return false;
    }
}
