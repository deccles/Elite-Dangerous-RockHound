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
    private final String experimentalEffect;
    private final String experimentalEffectLocalised;
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
                              String experimentalEffect,
                              String experimentalEffectLocalised,
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
        this.experimentalEffect = experimentalEffect != null ? experimentalEffect : "";
        this.experimentalEffectLocalised = experimentalEffectLocalised != null ? experimentalEffectLocalised : "";
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

    /** Set when requesting a new experimental effect at the engineer. */
    public String getApplyExperimentalEffect() {
        return applyExperimentalEffect;
    }

    /** Set on the craft entry after an experimental effect is applied. */
    public String getExperimentalEffect() {
        return experimentalEffect;
    }

    public String getExperimentalEffectLocalised() {
        return experimentalEffectLocalised;
    }

    public List<MaterialStack> getIngredients() {
        return ingredients;
    }
}
