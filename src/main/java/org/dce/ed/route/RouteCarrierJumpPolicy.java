package org.dce.ed.route;

import org.dce.ed.logreader.event.CarrierJumpEvent;

@FunctionalInterface
public interface RouteCarrierJumpPolicy {
    boolean shouldUpdateCurrentSystem(CarrierJumpEvent jump);
}
