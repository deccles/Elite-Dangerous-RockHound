package org.dce.ed;

/**
 * How the System tab orders body rows and labels the distance column (rocket / star / value toggles).
 */
public enum SystemTabTableSortMode {

    /** Closest to commander first when ship-centric distances are available. */
    FROM_SHIP,

    /** Closest to system entry / primary first ({@code DistanceFromArrivalLS}). */
    FROM_STAR,

    /** Highest exploration value first (exobiology, geo signals, high-value worlds). */
    BY_VALUE;

    public static SystemTabTableSortMode fromPrefsString(String s) {
        if (s == null) {
            return FROM_STAR;
        }
        switch (s.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "ship":
                return FROM_SHIP;
            case "value":
                return BY_VALUE;
            default:
                return FROM_STAR;
        }
    }

    public String toPrefsString() {
        switch (this) {
            case FROM_SHIP:
                return "ship";
            case BY_VALUE:
                return "value";
            default:
                return "star";
        }
    }
}
