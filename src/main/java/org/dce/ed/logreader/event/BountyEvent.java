package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/**
 * Bounty – written when the commander is awarded a bounty for a kill.
 */
public final class BountyEvent extends EliteLogEvent implements CombatRewardEvent {

    private final long totalReward;
    private final String victimFaction;
    private final String target;

    public BountyEvent(Instant timestamp, JsonObject rawJson, long totalReward) {
        super(timestamp, EliteEventType.BOUNTY, rawJson);
        this.totalReward = totalReward;
        this.victimFaction = stringField(rawJson, "VictimFaction");
        this.target = stringField(rawJson, "Target");
    }

    public long getTotalReward() {
        return totalReward;
    }

    @Override
    public long getCombatReward() {
        return totalReward;
    }

    /** Faction of the destroyed ship, used to attribute massacre-mission kills. */
    public String getVictimFaction() {
        return victimFaction;
    }

    /** Ship/target type (e.g. {@code eagle}, {@code Skimmer}). */
    public String getTarget() {
        return target;
    }

    private static String stringField(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        return o.get(key).getAsString();
    }
}
