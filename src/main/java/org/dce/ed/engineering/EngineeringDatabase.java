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
    private final Map<String, EngineeringMaterial> traderRowByTypeSubtypeGrade;

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
        this.traderRowByTypeSubtypeGrade = buildTraderRowIndex(materials);
    }

    private static Map<String, EngineeringMaterial> buildTraderRowIndex(List<EngineeringMaterial> materials) {
        Map<String, EngineeringMaterial> index = new HashMap<>();
        for (EngineeringMaterial material : materials) {
            if (!MaterialTraderCatalog.isTradeableAtMaterialTrader(material)) {
                continue;
            }
            index.putIfAbsent(traderRowKey(material.getType(), material.getSubtype(), material.getGrade()), material);
        }
        return Collections.unmodifiableMap(index);
    }

    private static String traderRowKey(String type, String subtype, int grade) {
        return (type + "\0" + subtype + "\0" + grade).toLowerCase(Locale.ROOT);
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
            if (!EngineeringJournalBlueprintResolver.sameModuleType(bp.getModuleType(), moduleType)) {
                continue;
            }
            String parent = bp.getParentBlueprint();
            if (parent.isBlank() || parent.equalsIgnoreCase(parentBlueprintName)) {
                out.add(bp);
            }
        }
        return out;
    }

    /**
     * Hover text for a blueprint grade: description and/or modifier summary.
     * {@code gradeOrZeroForMax <= 0} uses the highest available grade.
     */
    public String blueprintEffectTooltip(String moduleType, String blueprintName, int gradeOrZeroForMax) {
        if (moduleType == null || moduleType.isBlank()
                || blueprintName == null || blueprintName.isBlank()
                || "—".equals(blueprintName.trim())) {
            return null;
        }
        List<BlueprintGrade> grades = gradesFor(moduleType, blueprintName);
        if (grades.isEmpty()) {
            // Tolerant fallback when loadout label differs slightly from catalog module type.
            for (Map.Entry<String, List<BlueprintGrade>> e : byModuleAndName.entrySet()) {
                List<BlueprintGrade> list = e.getValue();
                if (list == null || list.isEmpty()) {
                    continue;
                }
                BlueprintGrade sample = list.get(0);
                if (sample.isExperimental()) {
                    continue;
                }
                if (!EngineeringJournalBlueprintResolver.normalizeToken(sample.getName())
                        .equals(EngineeringJournalBlueprintResolver.normalizeToken(blueprintName))) {
                    continue;
                }
                if (!EngineeringJournalBlueprintResolver.sameModuleType(sample.getModuleType(), moduleType)
                        && !EngineeringJournalBlueprintResolver.normalizeToken(sample.getModuleType())
                                .contains(EngineeringJournalBlueprintResolver.normalizeToken(moduleType))
                        && !EngineeringJournalBlueprintResolver.normalizeToken(moduleType)
                                .contains(EngineeringJournalBlueprintResolver.normalizeToken(sample.getModuleType()))) {
                    continue;
                }
                grades = list;
                break;
            }
        }
        BlueprintGrade pick = null;
        int maxGrade = 0;
        for (BlueprintGrade g : grades) {
            if (g == null || g.isExperimental()) {
                continue;
            }
            if (g.getGrade() > maxGrade) {
                maxGrade = g.getGrade();
                pick = g;
            }
        }
        if (gradeOrZeroForMax > 0) {
            for (BlueprintGrade g : grades) {
                if (g != null && !g.isExperimental() && g.getGrade() == gradeOrZeroForMax) {
                    pick = g;
                    break;
                }
            }
        }
        return formatEffectTooltip(pick);
    }

    /** Hover text for an experimental effect (modifiers / description). */
    public String experimentalEffectTooltip(String moduleType, String parentBlueprintName, String experimentalName) {
        if (experimentalName == null || experimentalName.isBlank()
                || "—".equals(experimentalName.trim())
                || "(none)".equalsIgnoreCase(experimentalName.trim())) {
            return null;
        }
        String parent = parentBlueprintName != null ? parentBlueprintName : "";
        for (BlueprintGrade exp : experimentalsFor(moduleType, parent)) {
            if (exp.getName().equalsIgnoreCase(experimentalName.trim())) {
                return formatEffectTooltip(exp);
            }
        }
        String want = EngineeringJournalBlueprintResolver.normalizeToken(experimentalName);
        for (BlueprintGrade bp : allBlueprints) {
            if (!bp.isExperimental()) {
                continue;
            }
            if (!EngineeringJournalBlueprintResolver.normalizeToken(bp.getName()).equals(want)) {
                continue;
            }
            if (moduleType != null && !moduleType.isBlank()
                    && !EngineeringJournalBlueprintResolver.sameModuleType(bp.getModuleType(), moduleType)) {
                continue;
            }
            return formatEffectTooltip(bp);
        }
        return null;
    }

    /** HTML tooltip body for a blueprint grade or experimental. */
    public static String formatEffectTooltip(BlueprintGrade bp) {
        if (bp == null) {
            return null;
        }
        String desc = bp.getDescription() != null ? bp.getDescription().trim() : "";
        String mods = bp.modifierSummary();
        if (desc.isBlank() && (mods == null || mods.isBlank())) {
            return null;
        }
        String title = bp.isExperimental()
                ? bp.getName()
                : bp.getName() + " G" + bp.getGrade();
        StringBuilder body = new StringBuilder();
        body.append("<b>").append(htmlEscape(title)).append("</b>");
        if (!desc.isBlank()) {
            body.append("<br>").append(htmlEscape(desc));
        }
        if (mods != null && !mods.isBlank()) {
            body.append("<br>").append(htmlEscape(mods));
        }
        return "<html><body style='width:300px'>" + body + "</body></html>";
    }

    private static String htmlEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;");
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

    /** Material at a given grade within one material-trader row (type + subtype). */
    public Optional<EngineeringMaterial> traderRowMaterial(String type, String subtype, int grade) {
        if (type == null || type.isBlank() || subtype == null || subtype.isBlank() || grade < 1) {
            return Optional.empty();
        }
        return Optional.ofNullable(traderRowByTypeSubtypeGrade.get(traderRowKey(type, subtype, grade)));
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
