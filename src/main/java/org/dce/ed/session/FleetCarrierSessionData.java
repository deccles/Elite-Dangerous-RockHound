package org.dce.ed.session;

import java.util.ArrayList;
import java.util.List;

/**
 * Fleet carrier tab {@link org.dce.ed.route.RouteSession} fields for {@link EdoSessionState}.
 */
public final class FleetCarrierSessionData {

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
    private List<RouteEntryPersisted> baseRouteEntries;

    /**
     * Last destination system name typed into the Fleet Carrier tab "Calculate" field (Spansh plot).
     */
    private String spanshDestinationQuery;

    public FleetCarrierSessionData() {
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

    public List<RouteEntryPersisted> getBaseRouteEntries() {
        return baseRouteEntries;
    }

    public void setBaseRouteEntries(List<RouteEntryPersisted> baseRouteEntries) {
        this.baseRouteEntries = baseRouteEntries;
    }

    public String getSpanshDestinationQuery() {
        return spanshDestinationQuery;
    }

    public void setSpanshDestinationQuery(String spanshDestinationQuery) {
        this.spanshDestinationQuery = spanshDestinationQuery;
    }

    /** Gson may deserialize null; normalize to mutable list. */
    public List<RouteEntryPersisted> baseRouteEntriesOrEmpty() {
        return baseRouteEntries != null ? baseRouteEntries : new ArrayList<>();
    }
}
