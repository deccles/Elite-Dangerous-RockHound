package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/**
 * Journal event: {@code SetUserShipName} — custom name / ident assigned in livery.
 */
public final class SetUserShipNameEvent extends EliteLogEvent {

    private final String shipType;
    private final long shipId;
    private final String userShipName;
    private final String userShipId;

    public SetUserShipNameEvent(Instant timestamp,
                                JsonObject rawJson,
                                String shipType,
                                long shipId,
                                String userShipName,
                                String userShipId) {
        super(timestamp, EliteEventType.SET_USER_SHIP_NAME, rawJson);
        this.shipType = shipType != null ? shipType : "";
        this.shipId = shipId;
        this.userShipName = userShipName != null ? userShipName : "";
        this.userShipId = userShipId != null ? userShipId : "";
    }

    public String getShipType() {
        return shipType;
    }

    public long getShipId() {
        return shipId;
    }

    public String getUserShipName() {
        return userShipName;
    }

    public String getUserShipId() {
        return userShipId;
    }
}
