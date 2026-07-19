package org.dce.ed.logreader;

import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.LocationEvent;

import com.google.gson.JsonObject;

/**
 * Tracks whether the commander is aboard their fleet carrier (docked or on-foot on the carrier).
 * Used so off-carrier {@code CarrierLocation} jump completions do not move the System tab, and so the
 * departure-time {@code CarrierLocation} Elite writes during an aboard jump is not announced as
 * "Jump complete" (the real completion is the {@code CarrierJump} arrival event).
 */
public final class FleetCarrierCommanderPresence {

    private boolean aboard;

    public boolean isAboard() {
        return aboard;
    }

    public void setAboard(boolean aboard) {
        this.aboard = aboard;
    }

    /**
     * {@code Location} is an authoritative snapshot (login/respawn): aboard exactly when it reports a
     * FleetCarrier station. Without this, restarting the game or EDO while docked on the carrier leaves
     * {@code aboard} false until the next live Docked event.
     */
    public void onLocation(JsonObject raw) {
        aboard = isFleetCarrierStation(raw);
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

    /** CarrierJump is only logged for commanders riding the carrier (docked in ship or on foot). */
    public void onCarrierJump(CarrierJumpEvent e) {
        if (e.isDocked() || e.isOnFoot()) {
            aboard = true;
        }
    }

    /** Routes any journal event to the matching presence handler; used for startup journal replay. */
    public void applyJournalEvent(EliteLogEvent event) {
        if (event == null) {
            return;
        }
        if (event instanceof LocationEvent) {
            onLocation(event.getRawJson());
        } else if (event instanceof FsdJumpEvent e) {
            onPersonalFsdJump(e);
        } else if (event instanceof CarrierJumpEvent e) {
            onCarrierJump(e);
        } else if (event.getType() == EliteEventType.DOCKED) {
            onDocked(event.getRawJson());
        } else if (event.getType() == EliteEventType.UNDOCKED) {
            onUndocked(event.getRawJson());
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
