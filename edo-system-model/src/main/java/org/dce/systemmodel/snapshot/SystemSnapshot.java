package org.dce.systemmodel.snapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.dce.systemmodel.build.SystemModelBuilder;
import org.dce.systemmodel.journal.JournalRecord;
import org.dce.systemmodel.model.SystemModel;

import java.time.Instant;
import java.util.List;

public record SystemSnapshot(
        String systemName,
        long systemAddress,
        String schemaVersion,
        List<JournalRecord> eventLog) {

    public static final String SCHEMA_V1 = "1";

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
            .registerTypeAdapter(JournalRecord.class, new JournalRecordTypeAdapter())
            .create();

    public String toJson() {
        return GSON.toJson(this);
    }

    public static SystemSnapshot fromJson(String json) {
        return GSON.fromJson(json, SystemSnapshot.class);
    }

    public SystemModel toModel() {
        return new SystemModelBuilder()
                .systemName(systemName)
                .systemAddress(systemAddress)
                .addAll(eventLog)
                .buildPartial();
    }

    public static SystemSnapshot fromModel(SystemModel model, List<JournalRecord> eventLog) {
        return new SystemSnapshot(model.systemName(), model.systemAddress(), SCHEMA_V1, List.copyOf(eventLog));
    }
}
