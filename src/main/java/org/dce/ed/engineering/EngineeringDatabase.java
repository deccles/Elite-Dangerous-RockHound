package org.dce.ed.engineering;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.dce.ed.market.GalacticAveragePrices;

/**
 * Lazy-loaded engineering blueprint and material tables from bundled JSON.
 */
public final class EngineeringDatabase {

    private static final Gson GSON = new Gson();
    private static final String RES_BLUEPRINTS = "/engineering/blueprints.json";
    private static final String RES_MATERIALS = "/engineering/materials.json";

    private static volatile EngineeringDatabase INSTANCE;

    private final List<BlueprintGrade> allBlueprints;
    private final Map<String, BlueprintGrade> byId;
    private final Map<String, List<BlueprintGrade>> byModuleAndName;
    private final Map<String, EngineeringMaterial> materialsByKey;
    private final List<EngineeringMaterial> allMaterials;

    private EngineeringDatabase(List<BlueprintGrade> blueprints, List<EngineeringMaterial> materials) {
        this.allBlueprints = List.copyOf(blueprints);
        this.byId = new HashMap<>();
        this.byModuleAndName = new LinkedHashMap<>();
        for (BlueprintGrade bp : blueprints) {
            byId.put(bp.getId(), bp);
            String groupKey = groupKey(bp.getModuleType(), bp.getName());
            byModuleAndName.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(bp);
        }
        for (List<BlueprintGrade> list : byModuleAndName.values()) {
            list.sort((a, b) -> {
                if (a.isExperimental() != b.isExperimental()) {
                    return a.isExperimental() ? 1 : -1;
                }
                return Integer.compare(a.getGrade(), b.getGrade());
            });
        }

        Map<String, EngineeringMaterial> matMap = new HashMap<>();
        for (EngineeringMaterial m : materials) {
            matMap.put(m.getKey(), m);
        }
        this.materialsByKey = Collections.unmodifiableMap(matMap);
        this.allMaterials = List.copyOf(materials);
    }

    public static EngineeringDatabase getInstance() {
        EngineeringDatabase db = INSTANCE;
        if (db == null) {
            synchronized (EngineeringDatabase.class) {
                db = INSTANCE;
                if (db == null) {
                    INSTANCE = db = load();
                }
            }
        }
        return db;
    }

    /** Visible for tests. */
    static synchronized void resetForTests() {
        INSTANCE = null;
    }

    public List<BlueprintGrade> getAllBlueprints() {
        return allBlueprints;
    }

    public List<EngineeringMaterial> getAllMaterials() {
        return allMaterials;
    }

    public Optional<BlueprintGrade> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(id));
    }

    public List<BlueprintGrade> gradesFor(String moduleType, String blueprintName) {
        List<BlueprintGrade> list = byModuleAndName.get(groupKey(moduleType, blueprintName));
        return list == null ? List.of() : list;
    }

    public List<BlueprintGrade> experimentalsFor(String moduleType, String parentBlueprintName) {
        List<BlueprintGrade> out = new ArrayList<>();
        for (BlueprintGrade bp : allBlueprints) {
            if (!bp.isExperimental()) {
                continue;
            }
            if (!bp.getModuleType().equalsIgnoreCase(moduleType)) {
                continue;
            }
            String parent = bp.getParentBlueprint();
            if (parent.isBlank() || parent.equalsIgnoreCase(parentBlueprintName)) {
                out.add(bp);
            }
        }
        return out;
    }

    public Optional<EngineeringMaterial> material(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        EngineeringMaterial m = materialsByKey.get(key);
        if (m != null) {
            return Optional.of(m);
        }
        String norm = GalacticAveragePrices.normalizeMaterialKey(key);
        m = materialsByKey.get(norm);
        return Optional.ofNullable(m);
    }

    public String materialDisplayName(String key) {
        return material(key).map(EngineeringMaterial::getName).orElse(key);
    }

    public static String groupKey(String moduleType, String blueprintName) {
        return (moduleType + "\0" + blueprintName).toLowerCase(Locale.ROOT);
    }

    private static EngineeringDatabase load() {
        List<BlueprintGrade> blueprints = parseBlueprints(readJsonArray(RES_BLUEPRINTS));
        List<EngineeringMaterial> materials = parseMaterials(readJsonArray(RES_MATERIALS));
        return new EngineeringDatabase(blueprints, materials);
    }

    private static JsonArray readJsonArray(String resource) {
        try (InputStream in = EngineeringDatabase.class.getResourceAsStream(resource)) {
            if (in == null) {
                return new JsonArray();
            }
            JsonElement el = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonElement.class);
            if (el != null && el.isJsonArray()) {
                return el.getAsJsonArray();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return new JsonArray();
    }

    private static List<BlueprintGrade> parseBlueprints(JsonArray arr) {
        List<BlueprintGrade> out = new ArrayList<>();
        for (JsonElement el : arr) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            List<String> engineers = new ArrayList<>();
            if (o.has("engineers") && o.get("engineers").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("engineers")) {
                    if (e != null && e.isJsonPrimitive()) {
                        engineers.add(e.getAsString());
                    }
                }
            }
            List<MaterialRequirement> mats = new ArrayList<>();
            if (o.has("materials") && o.get("materials").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("materials")) {
                    if (e == null || !e.isJsonObject()) {
                        continue;
                    }
                    JsonObject mo = e.getAsJsonObject();
                    String key = text(mo, "key");
                    int count = mo.has("count") ? mo.get("count").getAsInt() : 1;
                    mats.add(new MaterialRequirement(key, count));
                }
            }
            List<BlueprintModifier> mods = new ArrayList<>();
            if (o.has("modifiers") && o.get("modifiers").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("modifiers")) {
                    if (e == null || !e.isJsonObject()) {
                        continue;
                    }
                    JsonObject mo = e.getAsJsonObject();
                    mods.add(new BlueprintModifier(
                            text(mo, "property"),
                            text(mo, "effect"),
                            mo.has("isGood") && mo.get("isGood").getAsBoolean()));
                }
            }
            out.add(new BlueprintGrade(
                    text(o, "id"),
                    o.has("inaraBlueprintId") ? o.get("inaraBlueprintId").getAsInt() : 0,
                    text(o, "moduleType"),
                    text(o, "name"),
                    o.has("grade") ? o.get("grade").getAsInt() : 0,
                    o.has("experimental") && o.get("experimental").getAsBoolean(),
                    text(o, "description"),
                    text(o, "parentBlueprint"),
                    engineers,
                    mats,
                    mods));
        }
        return out;
    }

    private static List<EngineeringMaterial> parseMaterials(JsonArray arr) {
        List<EngineeringMaterial> out = new ArrayList<>();
        for (JsonElement el : arr) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            out.add(new EngineeringMaterial(
                    text(o, "key"),
                    text(o, "name"),
                    text(o, "type"),
                    text(o, "subtype"),
                    o.has("grade") ? o.get("grade").getAsInt() : 1));
        }
        return out;
    }

    private static String text(JsonObject o, String field) {
        if (o == null || !o.has(field) || o.get(field).isJsonNull()) {
            return "";
        }
        return o.get(field).getAsString();
    }
}
