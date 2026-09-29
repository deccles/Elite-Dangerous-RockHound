package org.dce.ed;

import org.dce.ed.logreader.event.StartJumpEvent;
import org.dce.ed.logreader.event.StatusEvent;

/**
 * Classifies hyperspace / FSD-charging activity for auto tab switching so fleet carrier jumps
 * stay on the Fleet Carrier tab and ship jumps stay on Route or System.
 */
public final class AutoTabJumpLogic {

    public enum JumpKind {
        FLEET_CARRIER,
        SHIP_HYPERSPACE,
        NONE
    }

    private AutoTabJumpLogic() {
    }

    /**
     * @param ownedCarrierJumpPending {@code CarrierJumpRequest} latched for the owned carrier, or route session pending jump
     * @param carrierJumpCountdownActive title-bar FC jump countdown is running
     * @param commanderAboardFleetCarrier commander is docked or on-foot aboard their fleet carrier
     */
    public static JumpKind classifyForAutoTabSwitch(
            boolean ownedCarrierJumpPending,
            boolean carrierJumpCountdownActive,
            boolean commanderAboardFleetCarrier,
            StatusEvent status,
            StartJumpEvent startJump) {
        if (!isHyperspaceJumpActivity(status, startJump)) {
            return JumpKind.NONE;
        }
        // Hyperspace charging while docked only happens on a fleet carrier (stations block ship FSD).
        if (status != null && status.isDocked()) {
            return JumpKind.FLEET_CARRIER;
        }
        boolean shipHyperspace = startJump != null
                && "Hyperspace".equalsIgnoreCase(trimOrEmpty(startJump.getJumpType()));
        // StartJump Hyperspace is the commander's ship when they are not aboard the carrier,
        // even if an owned carrier is jumping somewhere else.
        if (shipHyperspace && !commanderAboardFleetCarrier) {
            return JumpKind.SHIP_HYPERSPACE;
        }
        if (commanderAboardFleetCarrier
                && (ownedCarrierJumpPending || carrierJumpCountdownActive || shipHyperspace)) {
            return JumpKind.FLEET_CARRIER;
        }
        return JumpKind.SHIP_HYPERSPACE;
    }

    /**
     * Already on the Fleet Carrier tab during an owned-carrier jump (or while aboard that carrier).
     * Auto-switch must not leave for Route or System.
     */
    public static boolean retainFleetCarrierTab(
            boolean fleetCarrierTabShown,
            boolean ownedCarrierJumpPending,
            boolean carrierJumpCountdownActive,
            boolean commanderAboardFleetCarrier) {
        if (!fleetCarrierTabShown) {
            return false;
        }
        return ownedCarrierJumpPending || carrierJumpCountdownActive || commanderAboardFleetCarrier;
    }

    static boolean isHyperspaceJumpActivity(StatusEvent status, StartJumpEvent startJump) {
        if (startJump != null && "Hyperspace".equalsIgnoreCase(trimOrEmpty(startJump.getJumpType()))) {
            return true;
        }
        if (status != null) {
            return status.isFsdCharging() || status.isFsdHyperdriveCharging() || status.isFsdJump();
        }
        return false;
    }

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
