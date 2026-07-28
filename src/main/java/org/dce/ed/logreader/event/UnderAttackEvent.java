package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/**
 * Journal {@code UnderAttack} — under fire (same moment as the in-game voice line).
 * Does not name the attacker; only {@code Target} (You / Fighter / Mothership).
 */
public final class UnderAttackEvent extends EliteLogEvent {
    private final String target;

    public UnderAttackEvent(Instant timestamp, JsonObject rawJson, String target) {
        super(timestamp, EliteEventType.UNDER_ATTACK, rawJson);
        this.target = target;
    }

    /** {@code You}, {@code Fighter}, or {@code Mothership}. */
    public String getTarget() {
        return target;
    }
}
