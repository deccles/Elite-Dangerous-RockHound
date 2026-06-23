package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/**
 * Bounty – written when the commander is awarded a bounty for a kill.
 */
public final class BountyEvent extends EliteLogEvent {

    private final long totalReward;

    public BountyEvent(Instant timestamp, JsonObject rawJson, long totalReward) {
        super(timestamp, EliteEventType.BOUNTY, rawJson);
        this.totalReward = totalReward;
    }

    public long getTotalReward() {
        return totalReward;
    }
}
