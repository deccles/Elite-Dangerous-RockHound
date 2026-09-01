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
    private final String targetLocalised;
    private final String pilotLocalised;
    private final int sharedWithOthers;

    public BountyEvent(Instant timestamp, JsonObject rawJson, long totalReward) {
        super(timestamp, EliteEventType.BOUNTY, rawJson);
        this.totalReward = totalReward;
        this.victimFaction = stringField(rawJson, "VictimFaction");
        this.target = stringField(rawJson, "Target");
        this.targetLocalised = stringField(rawJson, "Target_Localised");
        this.pilotLocalised = stringField(rawJson, "PilotName_Localised");
        this.sharedWithOthers = intField(rawJson, "SharedWithOthers");
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

    /** Localised ship name when the journal writes {@code Target_Localised}. */
    public String getTargetLocalised() {
        return targetLocalised;
    }

    /** Victim pilot from the bounty line — present even when this commander never scanned them. */
    public String getPilotLocalised() {
        return pilotLocalised;
    }

    /**
     * Wing/team members who also received this bounty. {@code 0} when the kill was unshared.
     * Elite still writes a full {@code Bounty} for a wingmate's kill if this commander got a share.
     */
    public int getSharedWithOthers() {
        return sharedWithOthers;
    }

    public boolean isSharedWithWing() {
        return sharedWithOthers > 0;
    }

    private static int intField(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return 0;
        }
        try {
            return Math.max(0, o.get(key).getAsInt());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static String stringField(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        return o.get(key).getAsString();
    }
}
