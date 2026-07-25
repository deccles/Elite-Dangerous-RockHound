package org.dce.ed.engineering;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.EliteLogParser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Loads classpath fixtures under {@code /engineering/journal/}. */
final class JournalFixtureSupport {

    private JournalFixtureSupport() {
    }

    static JsonObject readObject(String resource) {
        try (Reader reader = new InputStreamReader(
                JournalFixtureSupport.class.getResourceAsStream(resource),
                StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("missing fixture " + resource, e);
        }
    }

    static List<String> stringList(JsonObject root, String field) {
        JsonArray arr = root.getAsJsonArray(field);
        List<String> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (JsonElement el : arr) {
            out.add(el.getAsString());
        }
        return out;
    }

    static List<EliteLogEvent> parseLines(EliteLogParser parser, List<String> lines) {
        List<EliteLogEvent> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(parser.parseRecord(line));
        }
        return out;
    }

    static Map<String, Integer> loadInventoryMap(String resource) {
        JsonObject root = readObject(resource);
        JsonObject inv = root.getAsJsonObject("inventory");
        java.util.LinkedHashMap<String, Integer> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : inv.entrySet()) {
            out.put(EngineeringMaterialKeys.canonicalKey(e.getKey()), e.getValue().getAsInt());
        }
        return out;
    }

    static List<EngineeringGoal> loadGoals(String resource) {
        JsonObject root = readObject(resource);
        JsonArray arr = root.getAsJsonArray("goals");
        List<EngineeringGoal> out = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject g = el.getAsJsonObject();
            out.add(new EngineeringGoal(
                    text(g, "blueprintId"),
                    text(g, "moduleType"),
                    text(g, "blueprintName"),
                    g.get("fromGrade").getAsInt(),
                    g.get("craftsAtCurrentGrade").getAsInt(),
                    g.get("targetGrade").getAsInt(),
                    text(g, "experimentalId"),
                    GoalPriority.valueOf(text(g, "priority")),
                    g.get("experimentalApplied").getAsBoolean(),
                    g.get("quantity").getAsInt(),
                    g.get("completedUnits").getAsInt(),
                    g.get("shipId").getAsLong(),
                    text(g, "shipLabel"),
                    g.get("includeInPlanning").getAsBoolean()));
        }
        return out;
    }

    static String text(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el == null || el.isJsonNull() ? "" : el.getAsString();
    }
}
