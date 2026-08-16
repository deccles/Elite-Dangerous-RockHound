package org.dce.ed.mission;

import java.time.Instant;
import java.util.Locale;

/**
 * Active mission state merged from {@code MissionAccepted}, {@code CargoDepot}, redirects, and snapshots.
 */
public final class MissionRecord {

    private final long missionId;
    private String faction;
    private String name;
    private String localisedName;
    private MissionCategory category;
    private String commodityLocalised;
    private int countRequired;
    private String destinationSystem;
    private String destinationStation;
    private String destinationSettlement;
    /** System where the mission was accepted (pickup / From). */
    private String originSystem;
    /** Station where the mission was accepted, when known. */
    private String originStation;
    private String sourcedFromSystem;
    private String sourcedFromStation;
    private String targetFaction;
    private String target;
    private String targetType;
    private String targetTypeLocalised;
    private int killCount;
    /** Estimated massacre progress from matching {@code Bounty} events (capped at {@link #killCount}). */
    private int killsCompleted;
    private long donation;
    private long reward;
    private String expiryIso;
    private long expiresSeconds;
    private boolean wing;
    private boolean passengerMission;
    private String influence;
    private String reputation;
    private int itemsDelivered;
    private int totalItemsToDeliver;
    private String cargoType;
    private boolean redirected;
    private Instant acceptedAt;
    private boolean detailsPending;

    public MissionRecord(long missionId) {
        this.missionId = missionId;
    }

    public long getMissionId() {
        return missionId;
    }

    public String getFaction() { return faction; }
    public void setFaction(String faction) { this.faction = faction; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; this.category = MissionCategory.fromMissionName(name); }

    public String getLocalisedName() { return localisedName; }
    public void setLocalisedName(String localisedName) { this.localisedName = localisedName; }

    public MissionCategory getCategory() { return category != null ? category : MissionCategory.UNKNOWN; }
    public void setCategory(MissionCategory category) { this.category = category; }

    public String getCommodityLocalised() { return commodityLocalised; }
    public void setCommodityLocalised(String commodityLocalised) { this.commodityLocalised = commodityLocalised; }

    public int getCountRequired() { return countRequired; }
    public void setCountRequired(int countRequired) { this.countRequired = countRequired; }

    public String getDestinationSystem() { return destinationSystem; }
    public void setDestinationSystem(String destinationSystem) { this.destinationSystem = destinationSystem; }

    public String getDestinationStation() { return destinationStation; }
    public void setDestinationStation(String destinationStation) { this.destinationStation = destinationStation; }

    public String getDestinationSettlement() { return destinationSettlement; }
    public void setDestinationSettlement(String destinationSettlement) { this.destinationSettlement = destinationSettlement; }

    public String getOriginSystem() { return originSystem; }
    public void setOriginSystem(String originSystem) { this.originSystem = originSystem; }

    public String getOriginStation() { return originStation; }
    public void setOriginStation(String originStation) { this.originStation = originStation; }

    public String getSourcedFromSystem() { return sourcedFromSystem; }
    public void setSourcedFromSystem(String sourcedFromSystem) { this.sourcedFromSystem = sourcedFromSystem; }
    public String getSourcedFromStation() { return sourcedFromStation; }
    public void setSourcedFromStation(String sourcedFromStation) { this.sourcedFromStation = sourcedFromStation; }

    public boolean isSelfSourcedCommodityMission() {
        if (name == null) {
            return false;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.startsWith("mission_sourced") || normalized.startsWith("mission_collect");
    }

    public boolean isManuallySourceableCommodityMission() {
        if (isSelfSourcedCommodityMission()) return true;
        return name != null && name.toLowerCase(Locale.ROOT).startsWith("mission_mining");
    }

    public String getTargetFaction() { return targetFaction; }
    public void setTargetFaction(String targetFaction) { this.targetFaction = targetFaction; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetTypeLocalised() { return targetTypeLocalised; }
    public void setTargetTypeLocalised(String targetTypeLocalised) { this.targetTypeLocalised = targetTypeLocalised; }

    public int getKillCount() { return killCount; }
    public void setKillCount(int killCount) { this.killCount = killCount; }

    public int getKillsCompleted() { return killsCompleted; }
    public void setKillsCompleted(int killsCompleted) { this.killsCompleted = Math.max(0, killsCompleted); }

    public long getDonation() { return donation; }
    public void setDonation(long donation) { this.donation = donation; }

    public long getReward() { return reward; }
    public void setReward(long reward) { this.reward = reward; }

    public String getExpiryIso() { return expiryIso; }
    public void setExpiryIso(String expiryIso) { this.expiryIso = expiryIso; }

    public long getExpiresSeconds() { return expiresSeconds; }
    public void setExpiresSeconds(long expiresSeconds) { this.expiresSeconds = expiresSeconds; }

    public boolean isWing() { return wing; }
    public void setWing(boolean wing) { this.wing = wing; }

    public boolean isPassengerMission() { return passengerMission; }
    public void setPassengerMission(boolean passengerMission) { this.passengerMission = passengerMission; }

    public String getInfluence() { return influence; }
    public void setInfluence(String influence) { this.influence = influence; }

    public String getReputation() { return reputation; }
    public void setReputation(String reputation) { this.reputation = reputation; }

    public int getItemsDelivered() { return itemsDelivered; }
    public void setItemsDelivered(int itemsDelivered) { this.itemsDelivered = itemsDelivered; }

    public int getTotalItemsToDeliver() { return totalItemsToDeliver; }
    public void setTotalItemsToDeliver(int totalItemsToDeliver) { this.totalItemsToDeliver = totalItemsToDeliver; }

    public String getCargoType() { return cargoType; }
    public void setCargoType(String cargoType) { this.cargoType = cargoType; }

    public boolean isRedirected() { return redirected; }
    public void setRedirected(boolean redirected) { this.redirected = redirected; }

    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }

    public boolean isDetailsPending() { return detailsPending; }
    public void setDetailsPending(boolean detailsPending) { this.detailsPending = detailsPending; }

    public boolean isCommodityMission() {
        return getCategory() == MissionCategory.COMMODITY
                && commodityLocalised != null && !commodityLocalised.isBlank();
    }

    public String summaryLine() {
        if (localisedName != null && !localisedName.isBlank()) {
            return localisedName;
        }
        return name != null ? name : "Mission " + missionId;
    }

    /**
     * Mission type label without per-mission commodity/count suffix
     * (e.g. "Mining Rush for 28 Units of Bromellite" → "Mining Rush").
     */
    public String shortSummaryLine() {
        String line = summaryLine();
        if (line == null || line.isBlank()) {
            return "Mission";
        }
        String trimmed = line.replaceAll("(?i) for \\d+ units of .+$", "").trim();
        return trimmed.isEmpty() ? line : trimmed;
    }
}
