package org.dce.ed.exec.placeholder;

import java.util.function.Supplier;

import org.dce.ed.RouteTabPanel;
import org.dce.ed.exec.CarrierFuelTracker;
import org.dce.ed.exec.ExecBindingsConfig;
import org.dce.ed.logreader.OwnedFleetCarrierTracker;
import org.dce.ed.route.RouteSession;
import org.dce.ed.state.SystemState;

/** Live overlay suppliers used to resolve {@link ExecPlaceholderId} values. */
public final class ExecPlaceholderContext {

    private volatile Supplier<String> carrierJumpTargetSupplier;
    private volatile Supplier<RouteSession> shipRouteSessionSupplier;
    private volatile Supplier<RouteSession> fleetRouteSessionSupplier;
    private volatile Supplier<SystemState> systemStateSupplier;
    private volatile Supplier<String> targetBodyNameSupplier;
    private volatile Supplier<String> nearBodyNameSupplier;
    private volatile Supplier<Long> exobiologyCreditsSupplier;
    private volatile Supplier<Long> geoSurveyCreditsSupplier;
    private volatile Supplier<Long> bountyCreditsSupplier;
    private volatile Supplier<ExecBindingsConfig> execConfigSupplier;
    private volatile Supplier<OwnedFleetCarrierTracker> ownedCarrierTrackerSupplier;
    private volatile Supplier<CarrierFuelTracker> carrierFuelTrackerSupplier;
    private final CommanderSnapshot commanderSnapshot = new CommanderSnapshot();

    public CommanderSnapshot commanderSnapshot() {
        return commanderSnapshot;
    }

    public void setCarrierJumpTargetSupplier(Supplier<String> carrierJumpTargetSupplier) {
        this.carrierJumpTargetSupplier = carrierJumpTargetSupplier;
    }

    public void setShipRouteSessionSupplier(Supplier<RouteSession> shipRouteSessionSupplier) {
        this.shipRouteSessionSupplier = shipRouteSessionSupplier;
    }

    public void setFleetRouteSessionSupplier(Supplier<RouteSession> fleetRouteSessionSupplier) {
        this.fleetRouteSessionSupplier = fleetRouteSessionSupplier;
    }

    public void setSystemStateSupplier(Supplier<SystemState> systemStateSupplier) {
        this.systemStateSupplier = systemStateSupplier;
    }

    public void setTargetBodyNameSupplier(Supplier<String> targetBodyNameSupplier) {
        this.targetBodyNameSupplier = targetBodyNameSupplier;
    }

    public void setNearBodyNameSupplier(Supplier<String> nearBodyNameSupplier) {
        this.nearBodyNameSupplier = nearBodyNameSupplier;
    }

    public void setExobiologyCreditsSupplier(Supplier<Long> exobiologyCreditsSupplier) {
        this.exobiologyCreditsSupplier = exobiologyCreditsSupplier;
    }

    public void setGeoSurveyCreditsSupplier(Supplier<Long> geoSurveyCreditsSupplier) {
        this.geoSurveyCreditsSupplier = geoSurveyCreditsSupplier;
    }

    public void setBountyCreditsSupplier(Supplier<Long> bountyCreditsSupplier) {
        this.bountyCreditsSupplier = bountyCreditsSupplier;
    }

    public void setExecConfigSupplier(Supplier<ExecBindingsConfig> execConfigSupplier) {
        this.execConfigSupplier = execConfigSupplier;
    }

    public void setOwnedCarrierTrackerSupplier(Supplier<OwnedFleetCarrierTracker> ownedCarrierTrackerSupplier) {
        this.ownedCarrierTrackerSupplier = ownedCarrierTrackerSupplier;
    }

    public void setCarrierFuelTrackerSupplier(Supplier<CarrierFuelTracker> carrierFuelTrackerSupplier) {
        this.carrierFuelTrackerSupplier = carrierFuelTrackerSupplier;
    }

    RouteSession shipRoute() {
        Supplier<RouteSession> s = shipRouteSessionSupplier;
        return s != null ? s.get() : null;
    }

    RouteSession fleetRoute() {
        Supplier<RouteSession> s = fleetRouteSessionSupplier;
        return s != null ? s.get() : null;
    }

    SystemState systemState() {
        Supplier<SystemState> s = systemStateSupplier;
        return s != null ? s.get() : null;
    }

    String carrierJumpTarget() {
        Supplier<String> s = carrierJumpTargetSupplier;
        return s != null ? s.get() : null;
    }

    String targetBodyName() {
        Supplier<String> s = targetBodyNameSupplier;
        return s != null ? s.get() : null;
    }

    String nearBodyName() {
        Supplier<String> s = nearBodyNameSupplier;
        return s != null ? s.get() : null;
    }

    long exobiologyCredits() {
        Supplier<Long> s = exobiologyCreditsSupplier;
        return s != null && s.get() != null ? s.get().longValue() : 0L;
    }

    long geoSurveyCredits() {
        Supplier<Long> s = geoSurveyCreditsSupplier;
        return s != null && s.get() != null ? s.get().longValue() : 0L;
    }

    long bountyCredits() {
        Supplier<Long> s = bountyCreditsSupplier;
        return s != null && s.get() != null ? s.get().longValue() : 0L;
    }

    int tritiumThreshold() {
        Supplier<ExecBindingsConfig> s = execConfigSupplier;
        if (s == null || s.get() == null) {
            return 0;
        }
        return s.get().getFleetTritiumLowThreshold();
    }

    OwnedFleetCarrierTracker ownedTracker() {
        Supplier<OwnedFleetCarrierTracker> s = ownedCarrierTrackerSupplier;
        return s != null ? s.get() : null;
    }

    CarrierFuelTracker fuelTracker() {
        Supplier<CarrierFuelTracker> s = carrierFuelTrackerSupplier;
        return s != null ? s.get() : null;
    }

    static String fleetNextDestination(ExecPlaceholderContext ctx) {
        return RouteTabPanel.nextRouteDestinationSystemName(ctx.fleetRoute());
    }

    static String shipNextDestination(ExecPlaceholderContext ctx) {
        RouteSession session = ctx.shipRoute();
        if (session == null) {
            return null;
        }
        boolean loopEnabled = session.isCustomRouteLoopEnabledForArrivals();
        // Prefer live System tab position — route session current can lag one hop behind arrival,
        // which makes $ROUTE_NEXT_DESTINATION return the system you are already in.
        SystemState live = ctx.systemState();
        if (live != null) {
            String liveName = live.getSystemName();
            if (liveName != null && !liveName.isBlank()) {
                return RouteTabPanel.nextRouteDestinationSystemName(
                        session.getBaseRouteEntries(),
                        liveName,
                        live.getSystemAddress(),
                        session.getCurrentBaseIndex(),
                        loopEnabled);
            }
        }
        return RouteTabPanel.nextRouteDestinationSystemName(session, true, loopEnabled);
    }
}
