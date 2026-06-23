package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/**
 * Journal event: ShipTargeted (combat target lock / scan progress).
 */
public final class ShipTargetedEvent extends EliteLogEvent {

    private final boolean targetLocked;
    private final int scanStage;
    private final String pilotName;
    private final Long bounty;

    public ShipTargetedEvent(Instant timestamp,
            JsonObject rawJson,
            boolean targetLocked,
            int scanStage,
            String pilotName,
            Long bounty) {
        super(timestamp, EliteEventType.SHIP_TARGETED, rawJson);
        this.targetLocked = targetLocked;
        this.scanStage = scanStage;
        this.pilotName = pilotName;
        this.bounty = bounty;
    }

    public boolean isTargetLocked() {
        return targetLocked;
    }

    public int getScanStage() {
        return scanStage;
    }

    /** Localised pilot name when present, otherwise journal {@code PilotName}. */
    public String getPilotName() {
        return pilotName;
    }

    /** Bounty credits at scan stage 3 when the target is wanted; otherwise {@code null}. */
    public Long getBounty() {
        return bounty;
    }
}
