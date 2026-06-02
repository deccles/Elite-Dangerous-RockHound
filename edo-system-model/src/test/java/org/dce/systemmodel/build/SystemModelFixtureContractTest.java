package org.dce.systemmodel.build;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.dce.systemmodel.journal.JournalRecord;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.snapshot.InstantTypeAdapter;
import org.dce.systemmodel.snapshot.JournalRecordTypeAdapter;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemModelFixtureContractTest {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
            .registerTypeAdapter(JournalRecord.class, new JournalRecordTypeAdapter())
            .create();

    @Test
    void eolProuBinaryMoonsFromEventLogFixture() throws Exception {
        JsonObject root = loadFixture("systemmap/eol-prou-nn-y-b31-0-7-moons-events.json");
        JsonObject expect = root.getAsJsonObject("expect");
        List<JournalRecord> events = parseEvents(root);
        var model = new SystemModelBuilder()
                .systemName(root.get("systemName").getAsString())
                .addAll(events)
                .build();
        var d = model.body(33).orElseThrow();
        var e = model.body(34).orElseThrow();
        assertEquals(ParentRef.ParentType.valueOf(expect.get("moon7dParentType").getAsString()), d.orbitParent().type());
        assertEquals(expect.get("moon7dParentId").getAsInt(), d.orbitParent().bodyId());
        assertEquals(ParentRef.ParentType.valueOf(expect.get("moon7eParentType").getAsString()), e.orbitParent().type());
        assertEquals(expect.get("moon7eParentId").getAsInt(), e.orbitParent().bodyId());
    }

    private static JsonObject loadFixture(String resource) {
        InputStream in = SystemModelFixtureContractTest.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("missing fixture: " + resource);
        }
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, JsonObject.class);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static List<JournalRecord> parseEvents(JsonObject root) {
        var arr = root.getAsJsonArray("events");
        List<JournalRecord> out = new java.util.ArrayList<>();
        for (var el : arr) {
            out.add(GSON.fromJson(el, JournalRecord.class));
        }
        return out;
    }
}
