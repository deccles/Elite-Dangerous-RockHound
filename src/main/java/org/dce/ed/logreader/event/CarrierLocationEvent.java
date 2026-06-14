package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/**
 * CarrierLocation is emitted when you're on / associated with a Fleet Carrier.
 * In some situations (especially docked or on-foot), Elite may emit this but not a normal Location event.
 */
public class CarrierLocationEvent extends EliteLogEvent implements IFsdJump {

    private final long carrierId;
    private final String starSystem;
    private final long systemAddress;
    private final int bodyId;

    public CarrierLocationEvent(Instant timestamp,
                                JsonObject json,
                                long carrierId,
                                String starSystem,
                                long systemAddress,
                                int bodyId) {
        super(timestamp, EliteEventType.CARRIER_LOCATION, json);
        this.carrierId = carrierId;
        this.starSystem = starSystem;
        this.systemAddress = systemAddress;
        this.bodyId = bodyId;
    }

    public long getCarrierId() {
        return carrierId;
    }

    @Override
    public String getBody() {
        // CarrierLocation does not include a "Body" string, only BodyID.
        return null;
    }

    @Override
    public int getBodyId() {
        return bodyId;
    }

    @Override
    public String getStarSystem() {
        return starSystem;
    }

    @Override
    public long getSystemAddress() {
        return systemAddress;
    }
}
