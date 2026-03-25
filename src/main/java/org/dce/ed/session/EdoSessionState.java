package org.dce.ed.session;

/**
 * Snapshot of overlay "session" state that can be persisted to edo-session.json
 * and restored on startup so in-progress navigation, carrier countdown, and
 * system tab selection survive restarts.
 */
public final class EdoSessionState {

    /** Schema version for future migration. */
    private int version = 1;

    // --- Route tab ---
    private String currentSystemName;
    private Long currentSystemAddress;
    private double[] currentStarPos;
    private String targetSystemName;
    private Long targetSystemAddress;
    private Long destinationSystemAddress;
    private Integer destinationBodyId;
    private String destinationName;
    private String pendingJumpLockedName;
    private Long pendingJumpLockedAddress;
    private Boolean inHyperspace;

    // --- Carrier countdown (OverlayFrame) ---
    /** ISO-8601 instant when carrier jump completes. */
    private String carrierJumpDepartureTime;
    private String carrierJumpTargetSystem;

    // --- System tab (target/near body, intermediate destination) ---
    private Integer targetBodyId;
    private String targetBodyName;
    private Integer nearBodyId;
    private String nearBodyName;
    private Integer targetDestinationParentBodyId;
    private String targetDestinationName;

    // --- Mining tab (run start time) ---
    /** ISO-8601 instant of last undock; used as run start for the first row of each run. */
    private String lastUndockTime;

    public EdoSessionState() {
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getCurrentSystemName() {
        return currentSystemName;
    }

    public void setCurrentSystemName(String currentSystemName) {
        this.currentSystemName = currentSystemName;
    }

    public Long getCurrentSystemAddress() {
        return currentSystemAddress;
    }

    public void setCurrentSystemAddress(Long currentSystemAddress) {
        this.currentSystemAddress = currentSystemAddress;
    }

    public double[] getCurrentStarPos() {
        return currentStarPos;
    }

    public void setCurrentStarPos(double[] currentStarPos) {
        this.currentStarPos = currentStarPos;
    }

    public String getTargetSystemName() {
        return targetSystemName;
    }

    public void setTargetSystemName(String targetSystemName) {
        this.targetSystemName = targetSystemName;
    }

    public Long getTargetSystemAddress() {
        return targetSystemAddress;
    }

    public void setTargetSystemAddress(Long targetSystemAddress) {
        this.targetSystemAddress = targetSystemAddress;
    }

    public Long getDestinationSystemAddress() {
        return destinationSystemAddress;
    }

    public void setDestinationSystemAddress(Long destinationSystemAddress) {
        this.destinationSystemAddress = destinationSystemAddress;
    }

    public Integer getDestinationBodyId() {
        return destinationBodyId;
    }

    public void setDestinationBodyId(Integer destinationBodyId) {
        this.destinationBodyId = destinationBodyId;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public String getPendingJumpLockedName() {
        return pendingJumpLockedName;
    }

    public void setPendingJumpLockedName(String pendingJumpLockedName) {
        this.pendingJumpLockedName = pendingJumpLockedName;
    }

    public Long getPendingJumpLockedAddress() {
        return pendingJumpLockedAddress;
    }

    public void setPendingJumpLockedAddress(Long pendingJumpLockedAddress) {
        this.pendingJumpLockedAddress = pendingJumpLockedAddress;
    }

    public Boolean getInHyperspace() {
        return inHyperspace;
    }

    public void setInHyperspace(Boolean inHyperspace) {
        this.inHyperspace = inHyperspace;
    }

    public String getCarrierJumpDepartureTime() {
        return carrierJumpDepartureTime;
    }

    public void setCarrierJumpDepartureTime(String carrierJumpDepartureTime) {
        this.carrierJumpDepartureTime = carrierJumpDepartureTime;
    }

    public String getCarrierJumpTargetSystem() {
        return carrierJumpTargetSystem;
    }

    public void setCarrierJumpTargetSystem(String carrierJumpTargetSystem) {
        this.carrierJumpTargetSystem = carrierJumpTargetSystem;
    }

    public Integer getTargetBodyId() {
        return targetBodyId;
    }

    public void setTargetBodyId(Integer targetBodyId) {
        this.targetBodyId = targetBodyId;
    }

    public String getTargetBodyName() {
        return targetBodyName;
    }

    public void setTargetBodyName(String targetBodyName) {
        this.targetBodyName = targetBodyName;
    }

    public Integer getNearBodyId() {
        return nearBodyId;
    }

    public void setNearBodyId(Integer nearBodyId) {
        this.nearBodyId = nearBodyId;
    }

    public String getNearBodyName() {
        return nearBodyName;
    }

    public void setNearBodyName(String nearBodyName) {
        this.nearBodyName = nearBodyName;
    }

    public Integer getTargetDestinationParentBodyId() {
        return targetDestinationParentBodyId;
    }

    public void setTargetDestinationParentBodyId(Integer targetDestinationParentBodyId) {
        this.targetDestinationParentBodyId = targetDestinationParentBodyId;
    }

    public String getTargetDestinationName() {
        return targetDestinationName;
    }

    public void setTargetDestinationName(String targetDestinationName) {
        this.targetDestinationName = targetDestinationName;
    }

    public String getLastUndockTime() {
        return lastUndockTime;
    }

    public void setLastUndockTime(String lastUndockTime) {
        this.lastUndockTime = lastUndockTime;
    }
}
