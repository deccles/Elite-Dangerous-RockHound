package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/** Journal event: "MaterialCollected". */
public class MaterialCollectedEvent extends EliteLogEvent {
    private final String category;
    private final String name;
    private final int count;

    public MaterialCollectedEvent(Instant timestamp, JsonObject rawJson, String category, String name, int count) {
        super(timestamp, EliteEventType.MATERIAL_COLLECTED, rawJson);
        this.category = category != null ? category : "";
        this.name = name != null ? name : "";
        this.count = Math.max(0, count);
    }

    public String getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public int getCount() {
        return count;
    }
}
