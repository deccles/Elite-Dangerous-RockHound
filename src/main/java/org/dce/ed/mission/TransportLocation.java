package org.dce.ed.mission;

/** A station-level Transport stop with galactic coordinates for route distance. */
public record TransportLocation(String system, String station, double x, double y, double z) {
    public TransportLocation {
        system = system == null ? "" : system.trim();
        station = station == null ? "" : station.trim();
        if (system.isBlank() || station.isBlank()) {
            throw new IllegalArgumentException("Transport locations require a system and station");
        }
    }

    public double distanceTo(TransportLocation other) {
        if (other == null || system.equalsIgnoreCase(other.system)) {
            return 0.0;
        }
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
