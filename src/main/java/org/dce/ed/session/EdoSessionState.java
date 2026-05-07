package org.dce.ed.session;

import java.util.Map;

/**
 * Commander / overlay session: routes, tab UI, mining anchor, carrier countdown, and globals
 * persisted in SQLite ({@code session_json} via {@link org.dce.ed.cache.SystemCache}).
 */
public final class EdoSessionState {

    /** DTO schema version (inside JSON). */
    private int version = 2;

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
    /** ISO-8601 instant when fleet-carrier jump cooldown ends (5 min window after jump). */
    private String carrierJumpCooldownEndTime;

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
    /** In-memory mining runtime snapshot for restart continuity mid-trip. */
    private MiningRuntimeState miningRuntime;

    // --- Commander globals (authoritative in session blob; not star-system domain) ---
    /** Unsold exobiology expected credits total (toolbar). */
    private Long exobiologyCreditsTotalUnsold;
    /** Running geo survey estimate total (toolbar). */
    private Long geoSurveyCreditsTotal;
    /** Last known docked flag from journals / Status. */
    private Boolean docked;
    /**
     * Cache bootstrap: which system row in {@code systems} to load first (was {@code overlay_global_state} header).
     */
    private Long cacheLastSystemAddress;
    private String cacheLastSystemName;

    // --- Fleet carrier tab ---
    private FleetCarrierSessionData fleetCarrier;

    /** Restart-safe snapshot of transient mining runtime state. */
    public static final class MiningRuntimeState {
        private Integer activeRun;
        private Integer asteroidIdCounter;
        private Integer dudCounter;
        private Boolean miningLoggingArmed;
        private Boolean haveActiveAsteroid;
        private Boolean prospectorLimpetSeenThisTrip;
        private Boolean loggedCargoSinceLastProspector;
        private Boolean nextMiningStartsNewRun;
        private Boolean wroteRowsThisRun;
        private String lastProspectedMotherlode;
        private Map<String, Double> lastInventoryTonsAtProspector;
        private Map<String, Double> lastPercentByMaterialAtProspector;
        private Map<String, Double> asteroidBaselineTons;
        private Map<String, Double> lastCargoTonsForLogging;

        public Integer getActiveRun() {
            return activeRun;
        }

        public void setActiveRun(Integer activeRun) {
            this.activeRun = activeRun;
        }

        public Integer getAsteroidIdCounter() {
            return asteroidIdCounter;
        }

        public void setAsteroidIdCounter(Integer asteroidIdCounter) {
            this.asteroidIdCounter = asteroidIdCounter;
        }

        public Integer getDudCounter() {
            return dudCounter;
        }

        public void setDudCounter(Integer dudCounter) {
            this.dudCounter = dudCounter;
        }

        public Boolean getMiningLoggingArmed() {
            return miningLoggingArmed;
        }

        public void setMiningLoggingArmed(Boolean miningLoggingArmed) {
            this.miningLoggingArmed = miningLoggingArmed;
        }

        public Boolean getHaveActiveAsteroid() {
            return haveActiveAsteroid;
        }

        public void setHaveActiveAsteroid(Boolean haveActiveAsteroid) {
            this.haveActiveAsteroid = haveActiveAsteroid;
        }

        public Boolean getProspectorLimpetSeenThisTrip() {
            return prospectorLimpetSeenThisTrip;
        }

        public void setProspectorLimpetSeenThisTrip(Boolean prospectorLimpetSeenThisTrip) {
            this.prospectorLimpetSeenThisTrip = prospectorLimpetSeenThisTrip;
        }

        public Boolean getLoggedCargoSinceLastProspector() {
            return loggedCargoSinceLastProspector;
        }

        public void setLoggedCargoSinceLastProspector(Boolean loggedCargoSinceLastProspector) {
            this.loggedCargoSinceLastProspector = loggedCargoSinceLastProspector;
        }

        public Boolean getNextMiningStartsNewRun() {
            return nextMiningStartsNewRun;
        }

        public void setNextMiningStartsNewRun(Boolean nextMiningStartsNewRun) {
            this.nextMiningStartsNewRun = nextMiningStartsNewRun;
        }

        public Boolean getWroteRowsThisRun() {
            return wroteRowsThisRun;
        }

        public void setWroteRowsThisRun(Boolean wroteRowsThisRun) {
            this.wroteRowsThisRun = wroteRowsThisRun;
        }

        public String getLastProspectedMotherlode() {
            return lastProspectedMotherlode;
        }

        public void setLastProspectedMotherlode(String lastProspectedMotherlode) {
            this.lastProspectedMotherlode = lastProspectedMotherlode;
        }

        public Map<String, Double> getLastInventoryTonsAtProspector() {
            return lastInventoryTonsAtProspector;
        }

        public void setLastInventoryTonsAtProspector(Map<String, Double> lastInventoryTonsAtProspector) {
            this.lastInventoryTonsAtProspector = lastInventoryTonsAtProspector;
        }

        public Map<String, Double> getLastPercentByMaterialAtProspector() {
            return lastPercentByMaterialAtProspector;
        }

        public void setLastPercentByMaterialAtProspector(Map<String, Double> lastPercentByMaterialAtProspector) {
            this.lastPercentByMaterialAtProspector = lastPercentByMaterialAtProspector;
        }

        public Map<String, Double> getAsteroidBaselineTons() {
            return asteroidBaselineTons;
        }

        public void setAsteroidBaselineTons(Map<String, Double> asteroidBaselineTons) {
            this.asteroidBaselineTons = asteroidBaselineTons;
        }

        public Map<String, Double> getLastCargoTonsForLogging() {
            return lastCargoTonsForLogging;
        }

        public void setLastCargoTonsForLogging(Map<String, Double> lastCargoTonsForLogging) {
            this.lastCargoTonsForLogging = lastCargoTonsForLogging;
        }
    }

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

    public String getCarrierJumpCooldownEndTime() {
        return carrierJumpCooldownEndTime;
    }

    public void setCarrierJumpCooldownEndTime(String carrierJumpCooldownEndTime) {
        this.carrierJumpCooldownEndTime = carrierJumpCooldownEndTime;
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

    public MiningRuntimeState getMiningRuntime() {
        return miningRuntime;
    }

    public void setMiningRuntime(MiningRuntimeState miningRuntime) {
        this.miningRuntime = miningRuntime;
    }

    public Long getExobiologyCreditsTotalUnsold() {
        return exobiologyCreditsTotalUnsold;
    }

    public void setExobiologyCreditsTotalUnsold(Long exobiologyCreditsTotalUnsold) {
        this.exobiologyCreditsTotalUnsold = exobiologyCreditsTotalUnsold;
    }

    public Long getGeoSurveyCreditsTotal() {
        return geoSurveyCreditsTotal;
    }

    public void setGeoSurveyCreditsTotal(Long geoSurveyCreditsTotal) {
        this.geoSurveyCreditsTotal = geoSurveyCreditsTotal;
    }

    public Boolean getDocked() {
        return docked;
    }

    public void setDocked(Boolean docked) {
        this.docked = docked;
    }

    public Long getCacheLastSystemAddress() {
        return cacheLastSystemAddress;
    }

    public void setCacheLastSystemAddress(Long cacheLastSystemAddress) {
        this.cacheLastSystemAddress = cacheLastSystemAddress;
    }

    public String getCacheLastSystemName() {
        return cacheLastSystemName;
    }

    public void setCacheLastSystemName(String cacheLastSystemName) {
        this.cacheLastSystemName = cacheLastSystemName;
    }

    public FleetCarrierSessionData getFleetCarrier() {
        return fleetCarrier;
    }

    public void setFleetCarrier(FleetCarrierSessionData fleetCarrier) {
        this.fleetCarrier = fleetCarrier;
    }
}
