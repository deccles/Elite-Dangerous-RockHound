package org.dce.ed.logreader.event;

import java.time.Instant;
import java.util.List;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/**
 * Journal event: "Materials" — full engineering inventory snapshot.
 */
public class MaterialsEvent extends EliteLogEvent {
    private final List<MaterialStack> raw;
    private final List<MaterialStack> manufactured;
    private final List<MaterialStack> encoded;

    public MaterialsEvent(Instant timestamp,
                          JsonObject rawJson,
                          List<MaterialStack> raw,
                          List<MaterialStack> manufactured,
                          List<MaterialStack> encoded) {
        super(timestamp, EliteEventType.MATERIALS, rawJson);
        this.raw = raw == null ? List.of() : List.copyOf(raw);
        this.manufactured = manufactured == null ? List.of() : List.copyOf(manufactured);
        this.encoded = encoded == null ? List.of() : List.copyOf(encoded);
    }

    public List<MaterialStack> getRaw() {
        return raw;
    }

    public List<MaterialStack> getManufactured() {
        return manufactured;
    }

    public List<MaterialStack> getEncoded() {
        return encoded;
    }
}
