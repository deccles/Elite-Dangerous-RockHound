package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/**
 * Journal event: {@code ModuleStore} — storing a fitted module from Outfitting.
 * Core internals include {@code ReplacementItem} (stock filler in the emptied slot).
 */
public final class ModuleStoreEvent extends EliteLogEvent {

    private final String slot;
    private final String ship;
    private final long shipId;
    private final String storedItem;
    private final String engineerModifications;
    private final int level;
    private final double quality;
    private final String replacementItem;

    public ModuleStoreEvent(Instant timestamp,
                            JsonObject rawJson,
                            String slot,
                            String ship,
                            long shipId,
                            String storedItem,
                            String engineerModifications,
                            int level,
                            double quality,
                            String replacementItem) {
        super(timestamp, EliteEventType.MODULE_STORE, rawJson);
        this.slot = slot != null ? slot : "";
        this.ship = ship != null ? ship : "";
        this.shipId = shipId;
        this.storedItem = storedItem != null ? storedItem : "";
        this.engineerModifications = engineerModifications != null ? engineerModifications : "";
        this.level = Math.max(0, level);
        this.quality = quality;
        this.replacementItem = replacementItem != null ? replacementItem : "";
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

    public String getStoredItem() {
        return storedItem;
    }

    public String getEngineerModifications() {
        return engineerModifications;
    }

    public int getLevel() {
        return level;
    }

    public double getQuality() {
        return quality;
    }

    public String getReplacementItem() {
        return replacementItem;
    }
}
