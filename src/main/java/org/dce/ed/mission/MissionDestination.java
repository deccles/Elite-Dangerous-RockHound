package org.dce.ed.mission;

import java.util.Objects;

/** System / station / settlement destination for display and clipboard copy. */
public final class MissionDestination {

    private final String system;
    private final String station;
    private final String settlement;

    public MissionDestination(String system, String station, String settlement) {
        this.system = blankToNull(system);
        this.station = blankToNull(station);
        this.settlement = blankToNull(settlement);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public String getSystem() {
        return system;
    }

    public String getStation() {
        return station;
    }

    public String getSettlement() {
        return settlement;
    }

    public boolean isEmpty() {
        return system == null && station == null && settlement == null;
    }

    /** Single-line label for table cells. */
    public String displayLine() {
        if (isEmpty()) {
            return "—";
        }
        if (settlement != null && system != null) {
            return system + " / " + settlement;
        }
        if (system != null && station != null) {
            return system + " / " + station;
        }
        if (system != null) {
            return system;
        }
        if (station != null) {
            return station;
        }
        return settlement != null ? settlement : "—";
    }

    /** Clipboard text (system only, or full line). */
    public String copyLine() {
        return displayLine();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MissionDestination that)) {
            return false;
        }
        return Objects.equals(system, that.system)
                && Objects.equals(station, that.station)
                && Objects.equals(settlement, that.settlement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(system, station, settlement);
    }
}
