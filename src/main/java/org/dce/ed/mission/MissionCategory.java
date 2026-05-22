package org.dce.ed.mission;

/**
 * High-level mission shape derived from journal {@code Name} (e.g. {@code Mission_Mining_Boom}).
 */
public enum MissionCategory {
    COMMODITY,
    COURIER,
    COMBAT,
    DONATION,
    PASSENGER,
    UNKNOWN;

    public static MissionCategory fromMissionName(String name) {
        if (name == null || name.isBlank()) {
            return UNKNOWN;
        }
        String n = name;
        if (n.endsWith("_name")) {
            n = n.substring(0, n.length() - 5);
        }
        if (n.startsWith("Mission_Mining_") || n.startsWith("Mission_Delivery_")
                || n.contains("Mission_Collect") || n.startsWith("Mission_Sourced")) {
            return COMMODITY;
        }
        if (n.startsWith("Mission_Courier")) {
            return COURIER;
        }
        if (n.startsWith("Mission_Altruism")) {
            return DONATION;
        }
        if (n.startsWith("Mission_Passenger") || n.contains("Passenger")) {
            return PASSENGER;
        }
        if (n.startsWith("Mission_Assassinate") || n.startsWith("Mission_Massacre")
                || n.startsWith("Mission_Combat") || n.contains("Kill")) {
            return COMBAT;
        }
        return UNKNOWN;
    }

    public String displayLabel() {
        return switch (this) {
            case COMMODITY -> "Cargo";
            case COURIER -> "Courier";
            case COMBAT -> "Combat";
            case DONATION -> "Donate";
            case PASSENGER -> "Passenger";
            case UNKNOWN -> "Other";
        };
    }
}
