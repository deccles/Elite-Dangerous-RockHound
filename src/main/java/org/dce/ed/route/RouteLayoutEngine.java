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
        int insertAt;
        if (entries.size() <= 1) {
            // Keep "you are here" ahead of a lone pasted/plotted hop so destinations stay at the end.
            insertAt = 0;
        } else {
            insertAt = RouteGeometry.bestInsertionIndexByCoords(entries, coords);
        }
        entries.add(insertAt, synthetic);
    }

    public static void applySyntheticTargetRow(List<RouteEntry> entries,
            String targetSystemName,
            long targetSystemAddress,
            String targetStarClass,
            String currentSystemName,
            long currentSystemAddress,
            boolean customRouteActive,
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
        if (targetStarClass != null && !targetStarClass.isBlank()) {
            synthetic.starClass = targetStarClass.trim();
        }
        // Custom routes: insert immediately after CURRENT so multi-jump NavRoute intermediates land
        // before the next custom destination (galaxy paths bend; geometric insert can go after it).
        // Normal NavRoute: keep 3D polyline placement so intentional side trips sit along the path.
        int insertAt = customRouteActive
                ? insertionIndexAfterCurrent(entries, currentSystemName, currentSystemAddress, coords)
                : RouteGeometry.bestInsertionIndexByCoords(entries, coords);
        entries.add(insertAt, synthetic);
    }

    /**
     * Index after the current system row (and any body rows under it); falls back to coord-based insert.
     */
    static int insertionIndexAfterCurrent(List<RouteEntry> entries,
            String currentSystemName,
            long currentSystemAddress,
            Double[] coords) {
        int currentRow = RouteGeometry.findSystemRow(entries, currentSystemName, currentSystemAddress);
        if (currentRow < 0) {
            return RouteGeometry.bestInsertionIndexByCoords(entries, coords);
        }
        int insertAt = currentRow + 1;
        while (insertAt < entries.size()) {
            RouteEntry e = entries.get(insertAt);
            if (e != null && e.isBodyRow) {
                insertAt++;
                continue;
            }
            break;
        }
        return insertAt;
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
        // Attach under the destination system hop (not only when it is the current system),
        // so a plotted station like Ray Gateway shows as soon as Status reports it.
        int destSystemRow = RouteGeometry.findSystemRow(entries, null, destinationSystemAddress.longValue());
        if (destSystemRow < 0) {
            return;
        }
        RouteEntry parent = entries.get(destSystemRow);
        // Status often sets Destination.Body to the primary star when locking a system jump;
        // Destination.Name is then the system name — that is not a station/body row.
        if (parent != null && parent.systemName != null
                && destinationName.equalsIgnoreCase(parent.systemName)) {
            return;
        }
        for (RouteEntry e : entries) {
            if (e != null && !e.isBodyRow && e.systemName != null
                    && destinationName.equalsIgnoreCase(e.systemName)) {
                return;
            }
        }
        int insertAt = destSystemRow + 1;
        RouteEntry body = RouteEntry.syntheticBody(destinationName);
        body.indentLevel = 1;
        body.markerKind = RouteMarkerKind.TARGET;
        entries.add(Math.min(insertAt, entries.size()), body);
    }

    /**
     * Full pipeline: copy base → optional hook (e.g. remembered scan status) → synthetics → leg distances → display # → markers.
     *
     * @param customRouteActive when true, off-list FSD targets insert after CURRENT (custom multi-hop);
     *        when false, use 3D polyline insertion (normal NavRoute side trips).
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
            boolean customRouteActive,
            boolean chargingActive) {
        List<RouteEntry> working = RouteGeometry.deepCopy(baseRouteEntries);
        if (afterDeepCopyBeforeSynthetics != null) {
            afterDeepCopyBeforeSynthetics.accept(working);
        }
        String tgtName = targetState.getTargetSystemName();
        long tgtAddr = targetState.getTargetSystemAddress();
        applySyntheticCurrentRow(working, currentSystemName, currentSystemAddress, currentStarPos, coordsResolver);
        applySyntheticTargetRow(working, tgtName, tgtAddr, targetState.getTargetStarClass(),
                currentSystemName, currentSystemAddress, customRouteActive, coordsResolver);
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
