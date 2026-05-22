package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

public final class CargoDepotEvent extends EliteLogEvent {

    private final long missionId;
    private final String updateType;
    private final String cargoType;
    private final int count;
    private final int itemsCollected;
    private final int itemsDelivered;
    private final int totalItemsToDeliver;
    private final double progress;

    public CargoDepotEvent(Instant timestamp, JsonObject rawJson) {
        super(timestamp, EliteEventType.CARGO_DEPOT, rawJson);
        missionId = longField(rawJson, "MissionID");
        updateType = stringField(rawJson, "UpdateType");
        cargoType = stringField(rawJson, "CargoType");
        count = intField(rawJson, "Count");
        itemsCollected = intField(rawJson, "ItemsCollected");
        itemsDelivered = intField(rawJson, "ItemsDelivered");
        totalItemsToDeliver = intField(rawJson, "TotalItemsToDeliver");
        progress = rawJson.has("Progress") && !rawJson.get("Progress").isJsonNull()
                ? rawJson.get("Progress").getAsDouble() : 0.0;
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

    private static int intField(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return 0;
        }
        return o.get(key).getAsInt();
    }

    public long getMissionId() { return missionId; }
    public String getUpdateType() { return updateType; }
    public String getCargoType() { return cargoType; }
    public int getCount() { return count; }
    public int getItemsCollected() { return itemsCollected; }
    public int getItemsDelivered() { return itemsDelivered; }
    public int getTotalItemsToDeliver() { return totalItemsToDeliver; }
    public double getProgress() { return progress; }
}
