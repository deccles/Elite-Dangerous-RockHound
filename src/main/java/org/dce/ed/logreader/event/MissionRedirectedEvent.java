package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

public final class MissionRedirectedEvent extends EliteLogEvent {

    private final long missionId;
    private final String name;
    private final String localisedName;
    private final String newDestinationSystem;
    private final String newDestinationStation;
    private final String oldDestinationSystem;
    private final String oldDestinationStation;

    public MissionRedirectedEvent(Instant timestamp, JsonObject rawJson) {
        super(timestamp, EliteEventType.MISSION_REDIRECTED, rawJson);
        missionId = longField(rawJson, "MissionID");
        name = stringField(rawJson, "Name");
        localisedName = stringField(rawJson, "LocalisedName");
        newDestinationSystem = stringField(rawJson, "NewDestinationSystem");
        newDestinationStation = stringField(rawJson, "NewDestinationStation");
        oldDestinationSystem = stringField(rawJson, "OldDestinationSystem");
        oldDestinationStation = stringField(rawJson, "OldDestinationStation");
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
    public String getNewDestinationSystem() { return newDestinationSystem; }
    public String getNewDestinationStation() { return newDestinationStation; }
    public String getOldDestinationSystem() { return oldDestinationSystem; }
    public String getOldDestinationStation() { return oldDestinationStation; }
}
