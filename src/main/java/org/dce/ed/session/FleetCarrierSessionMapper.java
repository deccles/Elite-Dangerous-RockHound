package org.dce.ed.session;

import java.util.ArrayList;
import java.util.List;

import org.dce.ed.route.RouteEntry;
import org.dce.ed.route.RouteGeometry;
import org.dce.ed.route.RouteMarkerKind;
import org.dce.ed.route.RoutePersistenceSnapshot;
import org.dce.ed.route.RouteScanStatus;
import org.dce.ed.route.RouteSession;

/**
 * Maps fleet carrier {@link RouteSession} to/from {@link FleetCarrierSessionData}.
 */
public final class FleetCarrierSessionMapper {

    private FleetCarrierSessionMapper() {
    }

    public static FleetCarrierSessionData fromRouteSession(RouteSession session) {
        if (session == null) {
            return null;
        }
        RoutePersistenceSnapshot snap = session.toPersistenceSnapshot();
        FleetCarrierSessionData d = new FleetCarrierSessionData();
        d.setCurrentSystemName(snap.currentSystemName());
        d.setCurrentSystemAddress(snap.currentSystemAddress());
        d.setCurrentStarPos(snap.currentStarPos());
        d.setTargetSystemName(snap.targetSystemName());
        d.setTargetSystemAddress(snap.targetSystemAddress());
        d.setDestinationSystemAddress(snap.destinationSystemAddress());
        d.setDestinationBodyId(snap.destinationBodyId());
        d.setDestinationName(snap.destinationName());
        d.setPendingJumpLockedName(snap.pendingJumpLockedName());
        d.setPendingJumpLockedAddress(snap.pendingJumpLockedAddress());
        d.setInHyperspace(snap.inHyperspace());
        List<RouteEntryPersisted> rows = new ArrayList<>();
        for (RouteEntry e : session.getBaseRouteEntries()) {
            rows.add(toPersisted(e));
        }
        d.setBaseRouteEntries(rows);
        return d;
    }

    public static void applyToRouteSession(RouteSession session, FleetCarrierSessionData d) {
        if (session == null || d == null) {
            return;
        }
        RoutePersistenceSnapshot snap = new RoutePersistenceSnapshot(
                d.getCurrentSystemName(),
                d.getCurrentSystemAddress(),
                d.getCurrentStarPos(),
                d.getTargetSystemName(),
                d.getTargetSystemAddress(),
                d.getDestinationSystemAddress(),
                d.getDestinationBodyId(),
                d.getDestinationName(),
                d.getPendingJumpLockedName(),
                d.getPendingJumpLockedAddress(),
                d.getInHyperspace());
        session.applyPersistenceSnapshot(snap);
        List<RouteEntry> entries = new ArrayList<>();
        for (RouteEntryPersisted p : d.baseRouteEntriesOrEmpty()) {
            RouteEntry e = fromPersisted(p);
            if (e != null) {
                entries.add(e);
            }
        }
        session.replaceBaseRouteEntries(entries);
    }

    public static RouteEntryPersisted toPersisted(RouteEntry e) {
        if (e == null) {
            return null;
        }
        RouteEntryPersisted p = new RouteEntryPersisted();
        p.setIndex(e.index);
        p.setDisplayIndex(e.displayIndex);
        p.setSystemName(e.systemName);
        p.setSystemAddress(e.systemAddress);
        p.setStarClass(e.starClass);
        p.setSynthetic(e.isSynthetic);
        p.setBodyRow(e.isBodyRow);
        p.setIndentLevel(e.indentLevel);
        p.setMarkerKind(e.markerKind != null ? e.markerKind.name() : RouteMarkerKind.NONE.name());
        p.setX(e.x);
        p.setY(e.y);
        p.setZ(e.z);
        p.setDistanceLy(e.distanceLy);
        p.setScanStatus(e.status != null ? e.status.name() : RouteScanStatus.UNKNOWN.name());
        return p;
    }

    public static RouteEntry fromPersisted(RouteEntryPersisted p) {
        if (p == null) {
            return null;
        }
        RouteEntry e = new RouteEntry();
        e.index = p.getIndex();
        e.displayIndex = p.getDisplayIndex();
        e.systemName = p.getSystemName() != null ? p.getSystemName() : "";
        e.systemAddress = p.getSystemAddress();
        e.starClass = p.getStarClass() != null ? p.getStarClass() : "";
        e.isSynthetic = p.isSynthetic();
        e.isBodyRow = p.isBodyRow();
        e.indentLevel = p.getIndentLevel();
        e.markerKind = parseMarkerKind(p.getMarkerKind());
        e.x = p.getX();
        e.y = p.getY();
        e.z = p.getZ();
        e.distanceLy = p.getDistanceLy();
        e.status = parseScanStatus(p.getScanStatus());
        return e;
    }

    private static RouteMarkerKind parseMarkerKind(String s) {
        if (s == null || s.isBlank()) {
            return RouteMarkerKind.NONE;
        }
        try {
            return RouteMarkerKind.valueOf(s.trim());
        } catch (Exception e) {
            return RouteMarkerKind.NONE;
        }
    }

    private static RouteScanStatus parseScanStatus(String s) {
        if (s == null || s.isBlank()) {
            return RouteScanStatus.UNKNOWN;
        }
        try {
            return RouteScanStatus.valueOf(s.trim());
        } catch (Exception e) {
            return RouteScanStatus.UNKNOWN;
        }
    }
}
