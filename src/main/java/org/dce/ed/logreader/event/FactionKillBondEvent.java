package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/** Combat bond awarded for a ship kill in a conflict zone. */
public final class FactionKillBondEvent extends EliteLogEvent implements CombatRewardEvent {

    private final long reward;

    public FactionKillBondEvent(Instant timestamp, JsonObject rawJson, long reward) {
        super(timestamp, EliteEventType.FACTION_KILL_BOND, rawJson);
        this.reward = Math.max(0L, reward);
    }

    public long getReward() {
        return reward;
    }

    @Override
    public long getCombatReward() {
        return reward;
    }

    /** Extracts the positive {@code Reward} value written by the journal. */
    public static long rewardFromJson(JsonObject rawJson) {
        if (rawJson == null || !rawJson.has("Reward") || rawJson.get("Reward").isJsonNull()) {
            return 0L;
        }
        try {
            return Math.max(0L, Math.round(rawJson.get("Reward").getAsDouble()));
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }
}
