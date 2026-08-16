package org.dce.ed.mission;

/** Remaining work for one cargo-bearing Transport mission. */
public record TransportShipment(long missionId, String commodity, int tonsRemaining, int tonsAboard,
        TransportLocation pickup, TransportLocation delivery) {

    public TransportShipment {
        commodity = commodity == null ? "Cargo" : commodity.trim();
        if (tonsRemaining <= 0 || tonsAboard < 0 || tonsAboard > tonsRemaining || delivery == null) {
            throw new IllegalArgumentException("Invalid transport shipment");
        }
        if (tonsAboard < tonsRemaining && pickup == null) {
            throw new IllegalArgumentException("Cargo not aboard requires a pickup location");
        }
    }

    public static TransportShipment cargo(long missionId, String commodity, int tonsRemaining,
            int tonsAboard, TransportLocation pickup, TransportLocation delivery) {
        return new TransportShipment(missionId, commodity, tonsRemaining, tonsAboard, pickup, delivery);
    }
}
