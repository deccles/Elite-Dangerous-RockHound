package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/**
 * Journal {@code Touchdown}: landed on a planetary surface (ship or SRV).
 * {@link #isPlayerControlled()} is {@code false} for some SRV landings.
 */
public final class TouchdownEvent extends EliteLogEvent {

    private final String starSystem;
    private final long systemAddress;
    private final String bodyName;
    private final int bodyId;
    private final boolean playerControlled;
    private final boolean onPlanet;
    private final Double latitude;
    private final Double longitude;

    public TouchdownEvent(Instant timestamp,
            JsonObject rawJson,
            String starSystem,
            long systemAddress,
            String bodyName,
            int bodyId,
            boolean playerControlled,
            boolean onPlanet,
            Double latitude,
            Double longitude) {
        super(timestamp, EliteEventType.TOUCHDOWN, rawJson);
        this.starSystem = starSystem != null ? starSystem : "";
        this.systemAddress = systemAddress;
        this.bodyName = bodyName != null ? bodyName : "";
        this.bodyId = bodyId;
        this.playerControlled = playerControlled;
        this.onPlanet = onPlanet;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getStarSystem() {
        return starSystem;
    }

    public long getSystemAddress() {
        return systemAddress;
    }

    public String getBodyName() {
        return bodyName;
    }

    public int getBodyId() {
        return bodyId;
    }

    public boolean isPlayerControlled() {
        return playerControlled;
    }

    public boolean isOnPlanet() {
        return onPlanet;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}
