package org.dce.ed.logreader.event;

import java.time.Instant;
import java.util.List;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/** Journal event: "EngineerCraft" — module engineered at an engineer. */
public final class EngineerCraftEvent extends EliteLogEvent {
    private final String slot;
    private final String module;
    private final String engineer;
    private final long engineerId;
    private final String blueprintName;
    private final long blueprintId;
    private final int level;
    private final double quality;
    private final String applyExperimentalEffect;
    private final List<MaterialStack> ingredients;

    public EngineerCraftEvent(Instant timestamp,
                              JsonObject rawJson,
                              String slot,
                              String module,
                              String engineer,
                              long engineerId,
                              String blueprintName,
                              long blueprintId,
                              int level,
                              double quality,
                              String applyExperimentalEffect,
                              List<MaterialStack> ingredients) {
        super(timestamp, EliteEventType.ENGINEER_CRAFT, rawJson);
        this.slot = slot != null ? slot : "";
        this.module = module != null ? module : "";
        this.engineer = engineer != null ? engineer : "";
        this.engineerId = engineerId;
        this.blueprintName = blueprintName != null ? blueprintName : "";
        this.blueprintId = blueprintId;
        this.level = Math.max(0, level);
        this.quality = quality;
        this.applyExperimentalEffect = applyExperimentalEffect != null ? applyExperimentalEffect : "";
        this.ingredients = ingredients != null ? List.copyOf(ingredients) : List.of();
    }

    public String getSlot() {
        return slot;
    }

    public String getModule() {
        return module;
    }

    public String getEngineer() {
        return engineer;
    }

    public long getEngineerId() {
        return engineerId;
    }

    public String getBlueprintName() {
        return blueprintName;
    }

    public long getBlueprintId() {
        return blueprintId;
    }

    public int getLevel() {
        return level;
    }

    public double getQuality() {
        return quality;
    }

    public String getApplyExperimentalEffect() {
        return applyExperimentalEffect;
    }

    public List<MaterialStack> getIngredients() {
        return ingredients;
    }
}
