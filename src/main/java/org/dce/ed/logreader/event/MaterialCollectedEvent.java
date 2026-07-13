package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/** Journal event: "MaterialCollected". */
public class MaterialCollectedEvent extends EliteLogEvent {
    private final String category;
    private final String name;
    private final String nameLocalised;
    private final int count;

    public MaterialCollectedEvent(Instant timestamp, JsonObject rawJson, String category, String name, int count) {
        this(timestamp, rawJson, category, name, "", count);
    }

    public MaterialCollectedEvent(Instant timestamp,
                                  JsonObject rawJson,
                                  String category,
                                  String name,
                                  String nameLocalised,
                                  int count) {
        super(timestamp, EliteEventType.MATERIAL_COLLECTED, rawJson);
        this.category = category != null ? category : "";
        this.name = name != null ? name : "";
        this.nameLocalised = nameLocalised != null ? nameLocalised : "";
        this.count = Math.max(0, count);
    }

    public String getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getNameLocalised() {
        return nameLocalised;
    }

    public int getCount() {
        return count;
    }
}
