package org.dce.systemmodel.snapshot;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.dce.systemmodel.journal.JournalRecord;
import org.dce.systemmodel.journal.ScanBaryCentreRecord;
import org.dce.systemmodel.journal.ScanRecord;

import java.lang.reflect.Type;

public final class JournalRecordTypeAdapter implements JsonSerializer<JournalRecord>, JsonDeserializer<JournalRecord> {

    @Override
    public JsonElement serialize(JournalRecord src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject o = new JsonObject();
        if (src instanceof ScanRecord) {
            o.addProperty("kind", "Scan");
            o.add("data", context.serialize(src, ScanRecord.class));
        } else if (src instanceof ScanBaryCentreRecord) {
            o.addProperty("kind", "ScanBaryCentre");
            o.add("data", context.serialize(src, ScanBaryCentreRecord.class));
        }
        return o;
    }

    @Override
    public JournalRecord deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject o = json.getAsJsonObject();
        String kind = o.get("kind").getAsString();
        JsonElement data = o.get("data");
        return switch (kind) {
            case "Scan" -> context.deserialize(data, ScanRecord.class);
            case "ScanBaryCentre" -> context.deserialize(data, ScanBaryCentreRecord.class);
            default -> throw new JsonParseException("Unknown journal record kind: " + kind);
        };
    }
}
