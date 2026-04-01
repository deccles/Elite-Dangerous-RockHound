package org.dce.ed.session;

/**
 * Gson-friendly snapshot of {@link org.dce.ed.route.RouteEntry} for fleet carrier session persistence.
 */
public final class RouteEntryPersisted {
    private int index;
    private Integer displayIndex;
    private String systemName;
    private long systemAddress;
    private String starClass;
    private boolean synthetic;
    private boolean bodyRow;
    private int indentLevel;
    private String markerKind;
    private Double x;
    private Double y;
    private Double z;
    private Double distanceLy;
    private String scanStatus;

    public RouteEntryPersisted() {
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public Integer getDisplayIndex() {
        return displayIndex;
    }

    public void setDisplayIndex(Integer displayIndex) {
        this.displayIndex = displayIndex;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public long getSystemAddress() {
        return systemAddress;
    }

    public void setSystemAddress(long systemAddress) {
        this.systemAddress = systemAddress;
    }

    public String getStarClass() {
        return starClass;
    }

    public void setStarClass(String starClass) {
        this.starClass = starClass;
    }

    public boolean isSynthetic() {
        return synthetic;
    }

    public void setSynthetic(boolean synthetic) {
        this.synthetic = synthetic;
    }

    public boolean isBodyRow() {
        return bodyRow;
    }

    public void setBodyRow(boolean bodyRow) {
        this.bodyRow = bodyRow;
    }

    public int getIndentLevel() {
        return indentLevel;
    }

    public void setIndentLevel(int indentLevel) {
        this.indentLevel = indentLevel;
    }

    public String getMarkerKind() {
        return markerKind;
    }

    public void setMarkerKind(String markerKind) {
        this.markerKind = markerKind;
    }

    public Double getX() {
        return x;
    }

    public void setX(Double x) {
        this.x = x;
    }

    public Double getY() {
        return y;
    }

    public void setY(Double y) {
        this.y = y;
    }

    public Double getZ() {
        return z;
    }

    public void setZ(Double z) {
        this.z = z;
    }

    public Double getDistanceLy() {
        return distanceLy;
    }

    public void setDistanceLy(Double distanceLy) {
        this.distanceLy = distanceLy;
    }

    public String getScanStatus() {
        return scanStatus;
    }

    public void setScanStatus(String scanStatus) {
        this.scanStatus = scanStatus;
    }
}
