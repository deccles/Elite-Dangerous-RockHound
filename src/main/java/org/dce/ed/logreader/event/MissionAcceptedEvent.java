package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

public final class MissionAcceptedEvent extends EliteLogEvent {

    private final long missionId;
    private final String faction;
    private final String name;
    private final String localisedName;
    private final String commodity;
    private final String commodityLocalised;
    private final int count;
    private final String destinationSystem;
    private final String destinationStation;
    private final String destinationSettlement;
    private final String targetFaction;
    private final String target;
    private final String targetType;
    private final int killCount;
    private final long donation;
    private final String expiry;
    private final boolean wing;
    private final String influence;
    private final String reputation;
    private final long reward;

    public MissionAcceptedEvent(Instant timestamp, JsonObject rawJson) {
        super(timestamp, EliteEventType.MISSION_ACCEPTED, rawJson);
        missionId = longField(rawJson, "MissionID");
        faction = stringField(rawJson, "Faction");
        name = stringField(rawJson, "Name");
        localisedName = stringField(rawJson, "LocalisedName");
        commodity = stringField(rawJson, "Commodity");
        commodityLocalised = stringField(rawJson, "Commodity_Localised");
        count = intField(rawJson, "Count");
        destinationSystem = stringField(rawJson, "DestinationSystem");
        destinationStation = stringField(rawJson, "DestinationStation");
        destinationSettlement = stringField(rawJson, "DestinationSettlement");
        targetFaction = stringField(rawJson, "TargetFaction");
        target = stringField(rawJson, "Target");
        targetType = stringField(rawJson, "TargetType");
        killCount = intField(rawJson, "KillCount");
        donation = longField(rawJson, "Donation");
        expiry = stringField(rawJson, "Expiry");
        wing = rawJson.has("Wing") && rawJson.get("Wing").getAsBoolean();
        influence = stringField(rawJson, "Influence");
        reputation = stringField(rawJson, "Reputation");
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

    private static int intField(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return 0;
        }
        return o.get(key).getAsInt();
    }

    public long getMissionId() { return missionId; }
    public String getFaction() { return faction; }
    public String getName() { return name; }
    public String getLocalisedName() { return localisedName; }
    public String getCommodity() { return commodity; }
    public String getCommodityLocalised() { return commodityLocalised; }
    public int getCount() { return count; }
    public String getDestinationSystem() { return destinationSystem; }
    public String getDestinationStation() { return destinationStation; }
    public String getDestinationSettlement() { return destinationSettlement; }
    public String getTargetFaction() { return targetFaction; }
    public String getTarget() { return target; }
    public String getTargetType() { return targetType; }
    public int getKillCount() { return killCount; }
    public long getDonation() { return donation; }
    public String getExpiry() { return expiry; }
    public boolean isWing() { return wing; }
    public String getInfluence() { return influence; }
    public String getReputation() { return reputation; }
    public long getReward() { return reward; }
}
