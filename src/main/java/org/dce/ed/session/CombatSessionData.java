package org.dce.ed.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Combat tab scanned-wanted / kills snapshot inside {@link EdoSessionState} {@code session_json}.
 * Survives overlay restart until bounty redeem (or LoadGame for scanned).
 */
public final class CombatSessionData {

    private List<ScannedWantedPersisted> scanned = new ArrayList<>();
    private List<KillPersisted> kills = new ArrayList<>();
    private long totalBountiesEarned;
    private long totalOtherBounties;
    /** ISO-8601 supercruise-exit timestamp awaiting the first qualifying reward. */
    private String creditsSessionCandidateExitAt;
    /** ISO-8601 timestamp at which the displayed credits session started. */
    private String creditsSessionStartedAt;
    /** ISO-8601 timestamp at which the displayed credits session ended. */
    private String creditsSessionEndedAt;
    private long creditsSessionEarnedCredits;
    private boolean creditsSessionActive;
    /** Internal ship id → localised display (helps name kills after restart). */
    private Map<String, String> shipDisplayById = new LinkedHashMap<>();
    /** Internal ship id → pilot display name. */
    private Map<String, String> pilotByShipId = new LinkedHashMap<>();

    public List<ScannedWantedPersisted> getScanned() {
        return scanned;
    }

    public void setScanned(List<ScannedWantedPersisted> scanned) {
        this.scanned = scanned != null ? scanned : new ArrayList<>();
    }

    public List<KillPersisted> getKills() {
        return kills;
    }

    public void setKills(List<KillPersisted> kills) {
        this.kills = kills != null ? kills : new ArrayList<>();
    }

    public long getTotalBountiesEarned() {
        return totalBountiesEarned;
    }

    public void setTotalBountiesEarned(long totalBountiesEarned) {
        this.totalBountiesEarned = totalBountiesEarned;
    }

    public long getTotalOtherBounties() {
        return totalOtherBounties;
    }

    public void setTotalOtherBounties(long totalOtherBounties) {
        this.totalOtherBounties = totalOtherBounties;
    }

    public String getCreditsSessionCandidateExitAt() {
        return creditsSessionCandidateExitAt;
    }

    public void setCreditsSessionCandidateExitAt(String creditsSessionCandidateExitAt) {
        this.creditsSessionCandidateExitAt = creditsSessionCandidateExitAt;
    }

    public String getCreditsSessionStartedAt() {
        return creditsSessionStartedAt;
    }

    public void setCreditsSessionStartedAt(String creditsSessionStartedAt) {
        this.creditsSessionStartedAt = creditsSessionStartedAt;
    }

    public String getCreditsSessionEndedAt() {
        return creditsSessionEndedAt;
    }

    public void setCreditsSessionEndedAt(String creditsSessionEndedAt) {
        this.creditsSessionEndedAt = creditsSessionEndedAt;
    }

    public long getCreditsSessionEarnedCredits() {
        return creditsSessionEarnedCredits;
    }

    public void setCreditsSessionEarnedCredits(long creditsSessionEarnedCredits) {
        this.creditsSessionEarnedCredits = creditsSessionEarnedCredits;
    }

    public boolean isCreditsSessionActive() {
        return creditsSessionActive;
    }

    public void setCreditsSessionActive(boolean creditsSessionActive) {
        this.creditsSessionActive = creditsSessionActive;
    }

    public Map<String, String> getShipDisplayById() {
        return shipDisplayById;
    }

    public void setShipDisplayById(Map<String, String> shipDisplayById) {
        this.shipDisplayById = shipDisplayById != null ? shipDisplayById : new LinkedHashMap<>();
    }

    public Map<String, String> getPilotByShipId() {
        return pilotByShipId;
    }

    public void setPilotByShipId(Map<String, String> pilotByShipId) {
        this.pilotByShipId = pilotByShipId != null ? pilotByShipId : new LinkedHashMap<>();
    }

    public List<ScannedWantedPersisted> scannedOrEmpty() {
        return scanned != null ? scanned : List.of();
    }

    public List<KillPersisted> killsOrEmpty() {
        return kills != null ? kills : List.of();
    }

    public Map<String, String> shipDisplayByIdOrEmpty() {
        return shipDisplayById != null ? shipDisplayById : Map.of();
    }

    public Map<String, String> pilotByShipIdOrEmpty() {
        return pilotByShipId != null ? pilotByShipId : Map.of();
    }

    /** Gson-friendly scanned wanted row. */
    public static final class ScannedWantedPersisted {
        private String pilotKey;
        private String pilotName;
        private String shipDisplay;
        private String legalStatus;
        private long firstBounty;
        private long currentBounty;
        private boolean warrantScanned;
        private boolean player;

        public String getPilotKey() { return pilotKey; }
        public void setPilotKey(String pilotKey) { this.pilotKey = pilotKey; }
        public String getPilotName() { return pilotName; }
        public void setPilotName(String pilotName) { this.pilotName = pilotName; }
        public String getShipDisplay() { return shipDisplay; }
        public void setShipDisplay(String shipDisplay) { this.shipDisplay = shipDisplay; }
        public String getLegalStatus() { return legalStatus; }
        public void setLegalStatus(String legalStatus) { this.legalStatus = legalStatus; }
        public long getFirstBounty() { return firstBounty; }
        public void setFirstBounty(long firstBounty) { this.firstBounty = firstBounty; }
        public long getCurrentBounty() { return currentBounty; }
        public void setCurrentBounty(long currentBounty) { this.currentBounty = currentBounty; }
        public boolean isWarrantScanned() { return warrantScanned; }
        public void setWarrantScanned(boolean warrantScanned) { this.warrantScanned = warrantScanned; }
        public boolean isPlayer() { return player; }
        public void setPlayer(boolean player) { this.player = player; }
    }

    /** Gson-friendly kill row. */
    public static final class KillPersisted {
        /** ISO-8601 instant. */
        private String timestamp;
        private String target;
        private String shipDisplay;
        private String pilotName;
        private String victimFaction;
        private long totalReward;
        private long otherReward;
        private int sharedWithOthers;

        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
        public String getShipDisplay() { return shipDisplay; }
        public void setShipDisplay(String shipDisplay) { this.shipDisplay = shipDisplay; }
        public String getPilotName() { return pilotName; }
        public void setPilotName(String pilotName) { this.pilotName = pilotName; }
        public String getVictimFaction() { return victimFaction; }
        public void setVictimFaction(String victimFaction) { this.victimFaction = victimFaction; }
        public long getTotalReward() { return totalReward; }
        public void setTotalReward(long totalReward) { this.totalReward = totalReward; }
        public long getOtherReward() { return otherReward; }
        public void setOtherReward(long otherReward) { this.otherReward = otherReward; }
        public int getSharedWithOthers() { return sharedWithOthers; }
        public void setSharedWithOthers(int sharedWithOthers) { this.sharedWithOthers = sharedWithOthers; }
    }
}
