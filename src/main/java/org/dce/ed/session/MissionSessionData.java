package org.dce.ed.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Missions tab state inside {@link EdoSessionState} {@code session_json}.
 */
public final class MissionSessionData {

    /** MissionID string → persisted fields. */
    private Map<String, MissionRecordPersisted> activeById = new HashMap<>();
    private List<Long> dismissedRedirectIds = new ArrayList<>();
    private String lastUpdated;

    public Map<String, MissionRecordPersisted> getActiveById() {
        return activeById;
    }

    public void setActiveById(Map<String, MissionRecordPersisted> activeById) {
        this.activeById = activeById != null ? activeById : new HashMap<>();
    }

    public List<Long> getDismissedRedirectIds() {
        return dismissedRedirectIds;
    }

    public void setDismissedRedirectIds(List<Long> dismissedRedirectIds) {
        this.dismissedRedirectIds = dismissedRedirectIds != null ? dismissedRedirectIds : new ArrayList<>();
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public Map<String, MissionRecordPersisted> activeByIdOrEmpty() {
        return activeById != null ? activeById : new HashMap<>();
    }

    public List<Long> dismissedRedirectIdsOrEmpty() {
        return dismissedRedirectIds != null ? dismissedRedirectIds : new ArrayList<>();
    }

    /** Gson-friendly mission row. */
    public static final class MissionRecordPersisted {
        private long missionId;
        private String faction;
        private String name;
        private String localisedName;
        private String category;
        private String commodityLocalised;
        private int countRequired;
        private String destinationSystem;
        private String destinationStation;
        private String destinationSettlement;
        private String targetFaction;
        private String target;
        private int killCount;
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
        private String acceptedAt;
        private boolean detailsPending;

        public long getMissionId() { return missionId; }
        public void setMissionId(long missionId) { this.missionId = missionId; }
        public String getFaction() { return faction; }
        public void setFaction(String faction) { this.faction = faction; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getLocalisedName() { return localisedName; }
        public void setLocalisedName(String localisedName) { this.localisedName = localisedName; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
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
        public String getTargetFaction() { return targetFaction; }
        public void setTargetFaction(String targetFaction) { this.targetFaction = targetFaction; }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
        public int getKillCount() { return killCount; }
        public void setKillCount(int killCount) { this.killCount = killCount; }
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
        public String getAcceptedAt() { return acceptedAt; }
        public void setAcceptedAt(String acceptedAt) { this.acceptedAt = acceptedAt; }
        public boolean isDetailsPending() { return detailsPending; }
        public void setDetailsPending(boolean detailsPending) { this.detailsPending = detailsPending; }
    }
}
