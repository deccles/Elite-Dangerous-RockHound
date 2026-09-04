package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/**
 * Journal event: {@code ModuleRetrieve} — fetching a stored module into a slot in Outfitting.
 * Engineered parts include {@code EngineerModifications}, {@code Level}, and {@code Quality}.
 */
public final class ModuleRetrieveEvent extends EliteLogEvent {

    private final String slot;
    private final String ship;
    private final long shipId;
    private final String retrievedItem;
    private final String engineerModifications;
    private final int level;
    private final double quality;
    private final String swapOutItem;

    public ModuleRetrieveEvent(Instant timestamp,
                               JsonObject rawJson,
                               String slot,
                               String ship,
                               long shipId,
                               String retrievedItem,
                               String engineerModifications,
                               int level,
                               double quality,
                               String swapOutItem) {
        super(timestamp, EliteEventType.MODULE_RETRIEVE, rawJson);
        this.slot = slot != null ? slot : "";
        this.ship = ship != null ? ship : "";
        this.shipId = shipId;
        this.retrievedItem = retrievedItem != null ? retrievedItem : "";
        this.engineerModifications = engineerModifications != null ? engineerModifications : "";
        this.level = Math.max(0, level);
        this.quality = quality;
        this.swapOutItem = swapOutItem != null ? swapOutItem : "";
    }

    public String getSlot() {
        return slot;
    }

    public String getShip() {
        return ship;
    }

    public long getShipId() {
        return shipId;
    }

    public String getRetrievedItem() {
        return retrievedItem;
    }

    public String getEngineerModifications() {
        return engineerModifications;
    }

    /** True when this retrieve named a blueprint and/or grade. */
    public boolean hasJournalEngineering() {
        return level > 0 || !engineerModifications.isBlank();
    }

    public int getLevel() {
        return level;
    }

    public double getQuality() {
        return quality;
    }

    public String getSwapOutItem() {
        return swapOutItem;
    }
}
