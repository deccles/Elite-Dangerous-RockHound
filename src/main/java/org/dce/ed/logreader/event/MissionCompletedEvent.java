package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

public final class MissionCompletedEvent extends EliteLogEvent {

    private final long missionId;
    private final String faction;
    private final String name;
    private final String localisedName;
    private final long reward;

    public MissionCompletedEvent(Instant timestamp, JsonObject rawJson) {
        super(timestamp, EliteEventType.MISSION_COMPLETED, rawJson);
        missionId = longField(rawJson, "MissionID");
        faction = stringField(rawJson, "Faction");
        name = stringField(rawJson, "Name");
        localisedName = stringField(rawJson, "LocalisedName");
        reward = longField(rawJson, "Reward");
    }

    private static String stringField(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        return o.get(key).getAsString();
    }

    private static long longField(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return 0L;
        }
        return o.get(key).getAsLong();
    }

    public long getMissionId() { return missionId; }
    public String getFaction() { return faction; }
    public String getName() { return name; }
    public String getLocalisedName() { return localisedName; }
    public long getReward() { return reward; }
}
