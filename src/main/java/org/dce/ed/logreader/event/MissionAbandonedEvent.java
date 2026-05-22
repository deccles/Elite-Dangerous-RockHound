package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

public final class MissionAbandonedEvent extends EliteLogEvent {

    private final long missionId;
    private final String name;
    private final String localisedName;

    public MissionAbandonedEvent(Instant timestamp, JsonObject rawJson) {
        super(timestamp, EliteEventType.MISSION_ABANDONED, rawJson);
        missionId = longField(rawJson, "MissionID");
        name = stringField(rawJson, "Name");
        localisedName = stringField(rawJson, "LocalisedName");
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
    public String getName() { return name; }
    public String getLocalisedName() { return localisedName; }
}
