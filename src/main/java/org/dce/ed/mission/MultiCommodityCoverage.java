package org.dce.ed.mission;

/** How well one station, together with cargo already aboard, covers one commodity need. */
public record MultiCommodityCoverage(String commodity, int heldTons, int stationTons,
        int requiredTons, Status status) {
    public enum Status { COMPLETE, PARTIAL, MISSING }

    public int availableTons() {
        return heldTons + stationTons;
    }

    public int shortTons() {
        return Math.max(0, requiredTons - availableTons());
    }
}
