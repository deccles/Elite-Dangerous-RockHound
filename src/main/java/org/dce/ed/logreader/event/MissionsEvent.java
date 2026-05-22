package org.dce.ed.logreader.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Load-game snapshot of active, failed, and complete mission ids. */
public final class MissionsEvent extends EliteLogEvent {

    public static final class MissionSnapshotEntry {
        public final long missionId;
        public final String name;
        public final boolean passengerMission;
        public final long expiresSeconds;

        public MissionSnapshotEntry(long missionId, String name, boolean passengerMission, long expiresSeconds) {
            this.missionId = missionId;
            this.name = name;
            this.passengerMission = passengerMission;
            this.expiresSeconds = expiresSeconds;
        }
    }

    private final List<MissionSnapshotEntry> active;
    private final List<MissionSnapshotEntry> failed;
    private final List<MissionSnapshotEntry> complete;

    public MissionsEvent(Instant timestamp, JsonObject rawJson) {
        super(timestamp, EliteEventType.MISSIONS, rawJson);
        this.active = parseList(rawJson, "Active");
        this.failed = parseList(rawJson, "Failed");
        this.complete = parseList(rawJson, "Complete");
    }

    private static List<MissionSnapshotEntry> parseList(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray()) {
            return Collections.emptyList();
        }
        List<MissionSnapshotEntry> out = new ArrayList<>();
        JsonArray arr = obj.getAsJsonArray(key);
        for (JsonElement el : arr) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            long id = o.has("MissionID") && !o.get("MissionID").isJsonNull()
                    ? o.get("MissionID").getAsLong() : 0L;
            String name = stringOrNull(o, "Name");
            boolean passenger = o.has("PassengerMission") && o.get("PassengerMission").getAsBoolean();
            long expires = o.has("Expires") && !o.get("Expires").isJsonNull()
                    ? o.get("Expires").getAsLong() : 0L;
            if (id != 0L) {
                out.add(new MissionSnapshotEntry(id, name, passenger, expires));
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static String stringOrNull(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        return o.get(key).getAsString();
    }

    public List<MissionSnapshotEntry> getActive() {
        return active;
    }

    public List<MissionSnapshotEntry> getFailed() {
        return failed;
    }

    public List<MissionSnapshotEntry> getComplete() {
        return complete;
    }
}
