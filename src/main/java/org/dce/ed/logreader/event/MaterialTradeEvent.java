package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/** Journal event: "MaterialTrade". */
public class MaterialTradeEvent extends EliteLogEvent {
    private final String category;
    private final String paidName;
    private final String paidNameLocalised;
    private final int paidCount;
    private final String receivedName;
    private final String receivedNameLocalised;
    private final int receivedCount;

    public MaterialTradeEvent(Instant timestamp,
                              JsonObject rawJson,
                              String category,
                              String paidName,
                              int paidCount,
                              String receivedName,
                              int receivedCount) {
        this(timestamp, rawJson, category, paidName, "", paidCount, receivedName, "", receivedCount);
    }

    public MaterialTradeEvent(Instant timestamp,
                              JsonObject rawJson,
                              String category,
                              String paidName,
                              String paidNameLocalised,
                              int paidCount,
                              String receivedName,
                              String receivedNameLocalised,
                              int receivedCount) {
        super(timestamp, EliteEventType.MATERIAL_TRADE, rawJson);
        this.category = category != null ? category : "";
        this.paidName = paidName != null ? paidName : "";
        this.paidNameLocalised = paidNameLocalised != null ? paidNameLocalised : "";
        this.paidCount = Math.max(0, paidCount);
        this.receivedName = receivedName != null ? receivedName : "";
        this.receivedNameLocalised = receivedNameLocalised != null ? receivedNameLocalised : "";
        this.receivedCount = Math.max(0, receivedCount);
    }

    public String getCategory() {
        return category;
    }

    public String getPaidName() {
        return paidName;
    }

    public String getPaidNameLocalised() {
        return paidNameLocalised;
    }

    public int getPaidCount() {
        return paidCount;
    }

    public String getReceivedName() {
        return receivedName;
    }

    public String getReceivedNameLocalised() {
        return receivedNameLocalised;
    }

    public int getReceivedCount() {
        return receivedCount;
    }
}
