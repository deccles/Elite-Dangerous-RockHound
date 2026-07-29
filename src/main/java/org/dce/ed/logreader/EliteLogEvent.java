package org.dce.ed.logreader;

import java.time.Instant;

import com.google.gson.JsonObject;

/**
 * Base type for all parsed Elite Dangerous journal events.
 * Subclasses represent specific event types.
 */
public abstract class EliteLogEvent {

    private final Instant timestamp;
    private final EliteEventType type;
    private final JsonObject rawJson; // for anything we didn't model

    protected EliteLogEvent(Instant timestamp, EliteEventType type, JsonObject rawJson) {
        this.timestamp = timestamp;
        this.type = type;
        this.rawJson = rawJson;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public EliteEventType getType() {
        return type;
    }

    public JsonObject getRawJson() {
        return rawJson;
    }

    public static final class NavRouteEvent extends EliteLogEvent {
        public NavRouteEvent(Instant timestamp, JsonObject rawJson) {
            super(timestamp, EliteEventType.NAV_ROUTE, rawJson);
        }
    }

    public static final class NavRouteClearEvent extends EliteLogEvent {
        public NavRouteClearEvent(Instant timestamp, JsonObject rawJson) {
            super(timestamp, EliteEventType.NAV_ROUTE_CLEAR, rawJson);
        }
    }



  

    

    
   
    /**
     * Generic catch-all event when we don't have a specific subclass yet.
     */
    public static final class GenericEvent extends EliteLogEvent {
        public GenericEvent(Instant timestamp, EliteEventType type, JsonObject rawJson) {
            super(timestamp, type, rawJson);
        }
    }

}
