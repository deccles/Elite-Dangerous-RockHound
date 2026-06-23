package org.dce.ed.session;

import java.util.Map;

/**
 * Commander / overlay session: routes, tab UI, mining anchor, carrier countdown, and globals
 * persisted in SQLite ({@code session_json} via {@link org.dce.ed.cache.SystemCache}).
 */
public final class EdoSessionState {

    /** DTO schema version (inside JSON). */
    private int version = 3;

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
    /**
     * Journal {@code BodyID} of the body a fleet carrier is parked at (Location / CarrierJump / CarrierLocation).
     * Pair with {@link #carrierParkedSystemAddress}. Persisted so map anchoring survives restart when Status omits it.
     */
    private Integer carrierParkedBodyId;
    /**
     * {@code SystemAddress} of the system where {@link #carrierParkedBodyId} applies. Null in older session JSON.
     */
    private Long carrierParkedSystemAddress;
    /**
     * System tab body table sort ({@link org.dce.ed.SystemTabTableSortMode#toPrefsString()}:
     * {@code ship}, {@code star}, or {@code value}).
     */
    private String systemTabTableSortMode;

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
    /** Unclaimed combat bounty credits total (toolbar). */
    private Long bountyCreditsTotalUnclaimed;
    /** Last known docked flag from journals / Status. */
    private Boolean docked;
    /**
     * Cache bootstrap: which system row in {@code systems} to load first (was {@code overlay_global_state} header).
     */
    private Long cacheLastSystemAddress;
    private String cacheLastSystemName;

    // --- Fleet carrier tab ---
    private FleetCarrierSessionData fleetCarrier;

    // --- Missions tab ---
    private MissionSessionData missions;

    // --- Biology tab (parked ship anchor on a planetary surface) ---
    private Double biologyParkedShipLat;
    private Double biologyParkedShipLon;
    private Double biologyParkedShipRadiusM;
    private Double biologyParkedShipHeadingDeg;
    private String biologyParkedShipBodyName;
    private Integer biologyParkedShipBodyId;
    private Double biologyParkedSrvLat;
    private Double biologyParkedSrvLon;
    private Double biologyParkedSrvHeadingDeg;
    /** Parked SRV surface fixes keyed by body (survives body hops and restart). */
    private java.util.List<BiologySrvMarkerEntry> biologyParkedSrvMarkers;
    /** User-placed stars on the biology surface map (per body). */
    private java.util.List<BiologyMapBookmarkEntry> biologyMapBookmarks;

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

    public Integer getCarrierParkedBodyId() {
        return carrierParkedBodyId;
    }

    public void setCarrierParkedBodyId(Integer carrierParkedBodyId) {
        this.carrierParkedBodyId = carrierParkedBodyId;
    }

    public Long getCarrierParkedSystemAddress() {
        return carrierParkedSystemAddress;
    }

    public void setCarrierParkedSystemAddress(Long carrierParkedSystemAddress) {
        this.carrierParkedSystemAddress = carrierParkedSystemAddress;
    }

    public String getSystemTabTableSortMode() {
        return systemTabTableSortMode;
    }

    public void setSystemTabTableSortMode(String systemTabTableSortMode) {
        this.systemTabTableSortMode = systemTabTableSortMode;
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

    public Long getBountyCreditsTotalUnclaimed() {
        return bountyCreditsTotalUnclaimed;
    }

    public void setBountyCreditsTotalUnclaimed(Long bountyCreditsTotalUnclaimed) {
        this.bountyCreditsTotalUnclaimed = bountyCreditsTotalUnclaimed;
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

    public MissionSessionData getMissions() {
        return missions;
    }

    public void setMissions(MissionSessionData missions) {
        this.missions = missions;
    }

    public Double getBiologyParkedShipLat() {
        return biologyParkedShipLat;
    }

    public void setBiologyParkedShipLat(Double biologyParkedShipLat) {
        this.biologyParkedShipLat = biologyParkedShipLat;
    }

    public Double getBiologyParkedShipLon() {
        return biologyParkedShipLon;
    }

    public void setBiologyParkedShipLon(Double biologyParkedShipLon) {
        this.biologyParkedShipLon = biologyParkedShipLon;
    }

    public Double getBiologyParkedShipRadiusM() {
        return biologyParkedShipRadiusM;
    }

    public void setBiologyParkedShipRadiusM(Double biologyParkedShipRadiusM) {
        this.biologyParkedShipRadiusM = biologyParkedShipRadiusM;
    }

    public Double getBiologyParkedShipHeadingDeg() {
        return biologyParkedShipHeadingDeg;
    }

    public void setBiologyParkedShipHeadingDeg(Double biologyParkedShipHeadingDeg) {
        this.biologyParkedShipHeadingDeg = biologyParkedShipHeadingDeg;
    }

    public String getBiologyParkedShipBodyName() {
        return biologyParkedShipBodyName;
    }

    public void setBiologyParkedShipBodyName(String biologyParkedShipBodyName) {
        this.biologyParkedShipBodyName = biologyParkedShipBodyName;
    }

    public Integer getBiologyParkedShipBodyId() {
        return biologyParkedShipBodyId;
    }

    public void setBiologyParkedShipBodyId(Integer biologyParkedShipBodyId) {
        this.biologyParkedShipBodyId = biologyParkedShipBodyId;
    }

    public Double getBiologyParkedSrvLat() {
        return biologyParkedSrvLat;
    }

    public void setBiologyParkedSrvLat(Double biologyParkedSrvLat) {
        this.biologyParkedSrvLat = biologyParkedSrvLat;
    }

    public Double getBiologyParkedSrvLon() {
        return biologyParkedSrvLon;
    }

    public void setBiologyParkedSrvLon(Double biologyParkedSrvLon) {
        this.biologyParkedSrvLon = biologyParkedSrvLon;
    }

    public Double getBiologyParkedSrvHeadingDeg() {
        return biologyParkedSrvHeadingDeg;
    }

    public void setBiologyParkedSrvHeadingDeg(Double biologyParkedSrvHeadingDeg) {
        this.biologyParkedSrvHeadingDeg = biologyParkedSrvHeadingDeg;
    }

    public java.util.List<BiologyMapBookmarkEntry> getBiologyMapBookmarks() {
        return biologyMapBookmarks;
    }

    public void setBiologyMapBookmarks(java.util.List<BiologyMapBookmarkEntry> biologyMapBookmarks) {
        this.biologyMapBookmarks = biologyMapBookmarks;
    }

    public java.util.List<BiologySrvMarkerEntry> getBiologyParkedSrvMarkers() {
        return biologyParkedSrvMarkers;
    }

    public void setBiologyParkedSrvMarkers(java.util.List<BiologySrvMarkerEntry> biologyParkedSrvMarkers) {
        this.biologyParkedSrvMarkers = biologyParkedSrvMarkers;
    }

    /** Parked SRV position on a planetary body. */
    public static final class BiologySrvMarkerEntry {
        private String bodyName;
        private Integer bodyId;
        private double lat;
        private double lon;
        private Double headingDeg;

        public BiologySrvMarkerEntry() {
        }

        public BiologySrvMarkerEntry(String bodyName, Integer bodyId, double lat, double lon, Double headingDeg) {
            this.bodyName = bodyName;
            this.bodyId = bodyId;
            this.lat = lat;
            this.lon = lon;
            this.headingDeg = headingDeg;
        }

        public String getBodyName() {
            return bodyName;
        }

        public void setBodyName(String bodyName) {
            this.bodyName = bodyName;
        }

        public Integer getBodyId() {
            return bodyId;
        }

        public void setBodyId(Integer bodyId) {
            this.bodyId = bodyId;
        }

        public double getLat() {
            return lat;
        }

        public void setLat(double lat) {
            this.lat = lat;
        }

        public double getLon() {
            return lon;
        }

        public void setLon(double lon) {
            this.lon = lon;
        }

        public Double getHeadingDeg() {
            return headingDeg;
        }

        public void setHeadingDeg(Double headingDeg) {
            this.headingDeg = headingDeg;
        }
    }

    /** Lat/lon pin on the biology map for a planetary body. */
    public static final class BiologyMapBookmarkEntry {
        private String bodyName;
        private Integer bodyId;
        private double lat;
        private double lon;

        public BiologyMapBookmarkEntry() {
        }

        public BiologyMapBookmarkEntry(String bodyName, Integer bodyId, double lat, double lon) {
            this.bodyName = bodyName;
            this.bodyId = bodyId;
            this.lat = lat;
            this.lon = lon;
        }

        public String getBodyName() {
            return bodyName;
        }

        public void setBodyName(String bodyName) {
            this.bodyName = bodyName;
        }

        public Integer getBodyId() {
            return bodyId;
        }

        public void setBodyId(Integer bodyId) {
            this.bodyId = bodyId;
        }

        public double getLat() {
            return lat;
        }

        public void setLat(double lat) {
            this.lat = lat;
        }

        public double getLon() {
            return lon;
        }

        public void setLon(double lon) {
            this.lon = lon;
        }
    }
}
