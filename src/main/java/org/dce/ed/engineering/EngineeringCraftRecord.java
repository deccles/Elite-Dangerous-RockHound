package org.dce.ed.engineering;

import java.time.Instant;

/**
 * One engineering craft attributed to a hull ({@code ShipID}), persisted after journal parse.
 */
public final class EngineeringCraftRecord {

    private final String clientKey;
    private final Instant timestamp;
    private final long shipId;
    private final String slot;
    private final String module;
    private final String blueprintName;
    private final int level;
    private final double quality;
    private final String rawJson;

    public EngineeringCraftRecord(String clientKey,
                                  Instant timestamp,
                                  long shipId,
                                  String slot,
                                  String module,
                                  String blueprintName,
                                  int level,
                                  double quality,
                                  String rawJson) {
        this.clientKey = clientKey != null ? clientKey : "";
        this.timestamp = timestamp != null ? timestamp : Instant.EPOCH;
        this.shipId = shipId;
        this.slot = slot != null ? slot : "";
        this.module = module != null ? module : "";
        this.blueprintName = blueprintName != null ? blueprintName : "";
        this.level = Math.max(0, level);
        this.quality = quality;
        this.rawJson = rawJson != null ? rawJson : "";
    }

    public String getClientKey() {
        return clientKey;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public long getShipId() {
        return shipId;
    }

    public String getSlot() {
        return slot;
    }

    public String getModule() {
        return module;
    }

    public String getBlueprintName() {
        return blueprintName;
    }

    public int getLevel() {
        return level;
    }

    public double getQuality() {
        return quality;
    }

    public String getRawJson() {
        return rawJson;
    }
}
