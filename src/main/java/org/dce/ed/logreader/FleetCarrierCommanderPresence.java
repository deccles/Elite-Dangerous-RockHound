package org.dce.ed.logreader;

import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;

import com.google.gson.JsonObject;

/**
 * Tracks whether the commander is aboard their fleet carrier (docked or on-foot on the carrier).
 * Used so off-carrier {@code CarrierLocation} jump completions do not move the System tab.
 */
public final class FleetCarrierCommanderPresence {

    private boolean aboard;

    public boolean isAboard() {
        return aboard;
    }

    public void setAboard(boolean aboard) {
        this.aboard = aboard;
    }

    public void onDocked(JsonObject raw) {
        if (isFleetCarrierStation(raw)) {
            aboard = true;
        }
    }

    public void onUndocked(JsonObject raw) {
        if (isFleetCarrierStation(raw)) {
            aboard = false;
        }
    }

    public void onPersonalFsdJump(FsdJumpEvent e) {
        if (e.getDocked() == null || !e.getDocked().booleanValue()) {
            aboard = false;
        }
    }

    public void onCarrierJump(CarrierJumpEvent e) {
        if (e.isOnFoot()) {
            aboard = true;
        }
    }

    public boolean shouldCarrierLocationMoveCommander() {
        return aboard;
    }

    public boolean shouldCarrierJumpMoveCommander(CarrierJumpEvent e) {
        return e.isDocked() || e.isOnFoot();
    }

    private static boolean isFleetCarrierStation(JsonObject raw) {
        if (raw == null) {
            return false;
        }
        String stationType = raw.has("StationType") && !raw.get("StationType").isJsonNull()
                ? raw.get("StationType").getAsString()
                : null;
        return stationType != null && "FleetCarrier".equalsIgnoreCase(stationType.trim());
    }
}
