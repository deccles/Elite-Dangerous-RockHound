package org.dce.ed.util;

/**
 * One exobiology landmark from Spansh body API (record.landmarks).
 * Community-recorded biological sample: type, subtype, position.
 * POJO for Gson serialization in cache.
 */
public class SpanshLandmark {
    private String type;
    private String subtype;
    private double latitude;
    private double longitude;

    public SpanshLandmark() {
        this("", "", 0.0, 0.0);
    }

    public SpanshLandmark(String type, String subtype, double latitude, double longitude) {
        this.type = type != null ? type : "";
        this.subtype = subtype != null ? subtype : "";
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type != null ? type : "";
    }

    public String getSubtype() {
        return subtype;
    }

    public void setSubtype(String subtype) {
        this.subtype = subtype != null ? subtype : "";
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
}
