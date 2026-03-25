package org.dce.ed.route;

import java.util.List;
import java.util.function.Consumer;

/**
 * Builds the displayed route row list from the base NavRoute/Spansh list plus navigation state.
 */
public final class RouteLayoutEngine {

    private RouteLayoutEngine() {
    }

    public static void applySyntheticCurrentRow(List<RouteEntry> entries,
            String curName,
            long currentSystemAddress,
            double[] currentStarPos,
            RouteCoordsResolver coordsResolver) {
        if (entries == null || curName == null || curName.isBlank()) {
            return;
        }
        if (RouteGeometry.findSystemRow(entries, curName, currentSystemAddress) >= 0) {
            return;
        }
        Double[] coords = coordsResolver.resolve(curName, currentSystemAddress, currentStarPos);
        RouteEntry synthetic = RouteEntry.syntheticSystem(curName, currentSystemAddress, coords, RouteMarkerKind.CURRENT);
        int insertAt = RouteGeometry.bestInsertionIndexByCoords(entries, coords);
        entries.add(insertAt, synthetic);
    }

    public static void applySyntheticTargetRow(List<RouteEntry> entries,
            String targetSystemName,
            long targetSystemAddress,
            RouteCoordsResolver coordsResolver) {
        if (entries == null || targetSystemName == null || targetSystemName.isBlank()) {
            return;
        }
        if (RouteGeometry.findSystemRow(entries, targetSystemName, targetSystemAddress) >= 0) {
            return;
        }
        Double[] coords = coordsResolver.resolve(targetSystemName, targetSystemAddress, null);
        RouteEntry synthetic = RouteEntry.syntheticSystem(targetSystemName, targetSystemAddress, coords,
                RouteMarkerKind.TARGET);
        int insertAt = RouteGeometry.bestInsertionIndexByCoords(entries, coords);
        entries.add(insertAt, synthetic);
    }

    public static void applySyntheticDestinationBodyRow(List<RouteEntry> entries,
            String currentSystemName,
            long currentSystemAddress,
            String destinationName,
            Long destinationSystemAddress,
            Integer destinationBodyId,
            String targetSystemName) {
        if (entries == null || destinationName == null || destinationName.isBlank()) {
            return;
        }
        if (destinationBodyId == null) {
            return;
        }
        if (destinationSystemAddress == null) {
            return;
        }
        if (targetSystemName != null && destinationName.equals(targetSystemName)) {
            return;
        }
        if (destinationName.equals(currentSystemName)) {
            return;
        }
        for (RouteEntry e : entries) {
            if (e != null && e.isBodyRow && destinationName.equals(e.systemName)) {
                return;
            }
        }
        int currentRow = RouteGeometry.findSystemRow(entries, currentSystemName, currentSystemAddress);
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
        if (resolvedCurrentAddress != 0L) {
            if (destinationSystemAddress.longValue() != resolvedCurrentAddress) {
                return;
            }
        }
        int insertAt = (currentRow >= 0) ? currentRow + 1 : 0;
        RouteEntry body = RouteEntry.syntheticBody(destinationName);
        body.indentLevel = 1;
        body.markerKind = RouteMarkerKind.TARGET;
        entries.add(Math.min(insertAt, entries.size()), body);
    }

    /**
     * Full pipeline: copy base → optional hook (e.g. remembered scan status) → synthetics → leg distances → display # → markers.
     */
    public static List<RouteEntry> buildDisplayedEntries(List<RouteEntry> baseRouteEntries,
            Consumer<List<RouteEntry>> afterDeepCopyBeforeSynthetics,
            String currentSystemName,
            long currentSystemAddress,
            double[] currentStarPos,
            RouteTargetState targetState,
            String pendingJumpLockedName,
            long pendingJumpLockedAddress,
            RouteCoordsResolver coordsResolver,
            boolean chargingActive) {
        List<RouteEntry> working = RouteGeometry.deepCopy(baseRouteEntries);
        if (afterDeepCopyBeforeSynthetics != null) {
            afterDeepCopyBeforeSynthetics.accept(working);
        }
        String tgtName = targetState.getTargetSystemName();
        long tgtAddr = targetState.getTargetSystemAddress();
        applySyntheticCurrentRow(working, currentSystemName, currentSystemAddress, currentStarPos, coordsResolver);
        applySyntheticTargetRow(working, tgtName, tgtAddr, coordsResolver);
        applySyntheticDestinationBodyRow(working, currentSystemName, currentSystemAddress,
                targetState.getDestinationName(),
                targetState.getDestinationSystemAddress(),
                targetState.getDestinationBodyId(),
                tgtName);
        RouteGeometry.recomputeLegDistances(working);
        RouteGeometry.renumberDisplayIndexes(working);
        RouteMarkerAssignment.applyMarkerKinds(working,
                currentSystemName,
                currentSystemAddress,
                tgtName,
                tgtAddr,
                targetState.getDestinationSystemAddress(),
                targetState.getDestinationBodyId(),
                targetState.getDestinationName(),
                pendingJumpLockedName,
                pendingJumpLockedAddress,
                chargingActive);
        return working;
    }
}
