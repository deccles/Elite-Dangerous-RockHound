package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/**
 * Journal {@code LeaveBody}: left a body's orbital cruise zone.
 */
public final class LeaveBodyEvent extends EliteLogEvent {

    private final String starSystem;
    private final long systemAddress;
    private final String bodyName;
    private final int bodyId;

    public LeaveBodyEvent(Instant timestamp,
            JsonObject rawJson,
            String starSystem,
            long systemAddress,
            String bodyName,
            int bodyId) {
        super(timestamp, EliteEventType.LEAVE_BODY, rawJson);
        this.starSystem = starSystem != null ? starSystem : "";
        this.systemAddress = systemAddress;
        this.bodyName = bodyName != null ? bodyName : "";
        this.bodyId = bodyId;
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
}
