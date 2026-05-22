package org.dce.ed.mission;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Aggregate progress for active commodity missions sharing one commodity name. */
public final class CommodityMissionGroup {

    private final String commodityLocalised;
    private final int missionCount;
    private final int totalRequired;
    private final int totalInHold;
    private final int totalDelivered;
    private final MissionDestination turnInDest;
    private final boolean multipleTurnIns;
    private final Instant soonestExpiry;
    private final List<MissionRecord> missions;

    public CommodityMissionGroup(String commodityLocalised,
            int missionCount,
            int totalRequired,
            int totalInHold,
            int totalDelivered,
            MissionDestination turnInDest,
            boolean multipleTurnIns,
            Instant soonestExpiry,
            List<MissionRecord> missions) {
        this.commodityLocalised = commodityLocalised;
        this.missionCount = missionCount;
        this.totalRequired = totalRequired;
        this.totalInHold = totalInHold;
        this.totalDelivered = totalDelivered;
        this.turnInDest = turnInDest;
        this.multipleTurnIns = multipleTurnIns;
        this.soonestExpiry = soonestExpiry;
        this.missions = Collections.unmodifiableList(new ArrayList<>(missions));
    }

    public String getCommodityLocalised() { return commodityLocalised; }
    public int getMissionCount() { return missionCount; }
    public int getTotalRequired() { return totalRequired; }
    public int getTotalInHold() { return totalInHold; }
    public int getTotalDelivered() { return totalDelivered; }
    public MissionDestination getTurnInDest() { return turnInDest; }
    public boolean isMultipleTurnIns() { return multipleTurnIns; }
    public Instant getSoonestExpiry() { return soonestExpiry; }
    public List<MissionRecord> getMissions() { return missions; }

    /** Tons gathered toward the group total (in hold + already delivered to depots). */
    public int totalGathered() {
        if (totalRequired <= 0) {
            return 0;
        }
        return Math.min(totalRequired, totalInHold + totalDelivered);
    }

    public boolean hasEnoughGathered() {
        return totalRequired > 0 && (totalInHold + totalDelivered) >= totalRequired;
    }

    public double progressFraction() {
        if (totalRequired <= 0) {
            return 0.0;
        }
        return (double) totalGathered() / (double) totalRequired;
    }
}
