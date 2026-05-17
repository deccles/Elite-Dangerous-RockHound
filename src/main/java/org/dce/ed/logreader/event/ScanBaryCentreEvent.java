package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/** Journal {@code ScanBaryCentre} — heliocentric orbit of a planet-binary barycentre ({@code Parents: Null:N}). */
public final class ScanBaryCentreEvent extends EliteLogEvent {

    private final int bodyId;
    private final String starSystem;
    private final long systemAddress;
    private final Double orbitalPeriod;
    private final Double semiMajorAxisM;
    private final Double eccentricity;
    private final Double orbitalInclination;
    private final Double periapsis;
    private final Double ascendingNode;
    private final Double meanAnomaly;

    public ScanBaryCentreEvent(Instant timestamp,
            JsonObject raw,
            int bodyId,
            String starSystem,
            long systemAddress,
            Double orbitalPeriod,
            Double semiMajorAxisM,
            Double eccentricity,
            Double orbitalInclination,
            Double periapsis,
            Double ascendingNode,
            Double meanAnomaly) {
        super(timestamp, EliteEventType.SCAN_BARYCENTRE, raw);
        this.bodyId = bodyId;
        this.starSystem = starSystem;
        this.systemAddress = systemAddress;
        this.orbitalPeriod = orbitalPeriod;
        this.semiMajorAxisM = semiMajorAxisM;
        this.eccentricity = eccentricity;
        this.orbitalInclination = orbitalInclination;
        this.periapsis = periapsis;
        this.ascendingNode = ascendingNode;
        this.meanAnomaly = meanAnomaly;
    }

    public int getBodyId() {
        return bodyId;
    }

    public String getStarSystem() {
        return starSystem;
    }

    public long getSystemAddress() {
        return systemAddress;
    }

    public Double getOrbitalPeriod() {
        return orbitalPeriod;
    }

    public Double getSemiMajorAxisM() {
        return semiMajorAxisM;
    }

    public Double getEccentricity() {
        return eccentricity;
    }

    public Double getOrbitalInclination() {
        return orbitalInclination;
    }

    public Double getPeriapsis() {
        return periapsis;
    }

    public Double getAscendingNode() {
        return ascendingNode;
    }

    public Double getMeanAnomaly() {
        return meanAnomaly;
    }
}
