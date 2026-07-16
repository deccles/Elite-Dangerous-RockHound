package org.dce.ed.logreader.event;

import java.time.Instant;
import java.util.Locale;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/**
 * Journal event: {@code EngineerContribution} — donate materials/commodities/credits to unlock an engineer.
 */
public class EngineerContributionEvent extends EliteLogEvent {

    private final String engineer;
    private final long engineerId;
    private final String contributionType;
    private final String material;
    private final String materialLocalised;
    private final String commodity;
    private final String commodityLocalised;
    private final int quantity;
    private final int totalQuantity;

    public EngineerContributionEvent(Instant timestamp,
                                     JsonObject rawJson,
                                     String engineer,
                                     long engineerId,
                                     String contributionType,
                                     String material,
                                     String materialLocalised,
                                     String commodity,
                                     String commodityLocalised,
                                     int quantity,
                                     int totalQuantity) {
        super(timestamp, EliteEventType.ENGINEER_CONTRIBUTION, rawJson);
        this.engineer = engineer != null ? engineer : "";
        this.engineerId = engineerId;
        this.contributionType = contributionType != null ? contributionType : "";
        this.material = material != null ? material : "";
        this.materialLocalised = materialLocalised != null ? materialLocalised : "";
        this.commodity = commodity != null ? commodity : "";
        this.commodityLocalised = commodityLocalised != null ? commodityLocalised : "";
        this.quantity = Math.max(0, quantity);
        this.totalQuantity = Math.max(0, totalQuantity);
    }

    public String getEngineer() {
        return engineer;
    }

    public long getEngineerId() {
        return engineerId;
    }

    public String getContributionType() {
        return contributionType;
    }

    public String getMaterial() {
        return material;
    }

    public String getMaterialLocalised() {
        return materialLocalised;
    }

    public String getCommodity() {
        return commodity;
    }

    public String getCommodityLocalised() {
        return commodityLocalised;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    /** True when this donation removes engineering materials from inventory. */
    public boolean isMaterialContribution() {
        String type = contributionType.trim().toLowerCase(Locale.ROOT);
        if (type.equals("material") || type.equals("materials")) {
            return true;
        }
        return !material.isBlank();
    }
}
