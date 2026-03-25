package org.dce.ed.route;

/**
 * One row in the route table (plotted system, synthetic off-route system, or body).
 */
public final class RouteEntry {
    public RouteEntry() {
    }

    public RouteEntry(int i, String systemNameIn, long systemAddressIn, String starClassIn, double dLy,
            RouteScanStatus scanStatusIn) {
        index = i;
        systemName = systemNameIn;
        systemAddress = systemAddressIn;
        starClass = starClassIn;
        distanceLy = dLy;
        status = scanStatusIn;
    }

    public int index;
    public Integer displayIndex;
    public String systemName;
    public long systemAddress;
    public String starClass;
    public boolean isSynthetic;
    public boolean isBodyRow;
    public int indentLevel;
    public RouteMarkerKind markerKind = RouteMarkerKind.NONE;
    public Double x;
    public Double y;
    public Double z;
    /** Per-leg distance (Ly) from the previous entry; null for origin. */
    public Double distanceLy;
    public RouteScanStatus status;

    public RouteEntry copy() {
        RouteEntry e = new RouteEntry();
        e.index = index;
        e.displayIndex = displayIndex;
        e.systemName = systemName;
        e.systemAddress = systemAddress;
        e.starClass = starClass;
        e.x = x;
        e.y = y;
        e.z = z;
        e.distanceLy = distanceLy;
        e.status = status;
        e.isSynthetic = isSynthetic;
        e.isBodyRow = isBodyRow;
        e.indentLevel = indentLevel;
        e.markerKind = markerKind;
        return e;
    }

    public static RouteEntry syntheticSystem(String name, long address, Double[] coords, RouteMarkerKind markerKind) {
        RouteEntry e = new RouteEntry();
        e.isSynthetic = true;
        e.isBodyRow = false;
        e.systemName = (name != null ? name : "");
        e.systemAddress = address;
        e.starClass = "";
        e.status = RouteScanStatus.UNKNOWN;
        e.markerKind = (markerKind != null ? markerKind : RouteMarkerKind.NONE);
        if (coords != null && coords.length == 3 && coords[0] != null && coords[1] != null && coords[2] != null) {
            e.x = coords[0];
            e.y = coords[1];
            e.z = coords[2];
        }
        return e;
    }

    public static RouteEntry syntheticBody(String bodyName) {
        RouteEntry e = new RouteEntry();
        e.isSynthetic = true;
        e.isBodyRow = true;
        e.systemName = (bodyName != null ? bodyName : "");
        e.systemAddress = 0L;
        e.starClass = "";
        e.status = null;
        e.markerKind = RouteMarkerKind.NONE;
        return e;
    }
}
