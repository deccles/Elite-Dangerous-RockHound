package org.dce.ed.engineering;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Maps journal {@code EngineerCraft} / loadout {@code BlueprintName} strings to catalog blueprints.
 */
public final class EngineeringJournalBlueprintResolver {

    private static final Map<String, ResolvedBlueprint> BY_JOURNAL_NAME = loadJournalMap();

    /**
     * Mount / hardpoint tokens kept when building a display name. Size/Class ratings are omitted.
     */
    private static final Set<String> DISPLAY_SUFFIX_TOKENS = Set.of(
            "fixed", "gimbal", "gimballed", "turret", "turreted",
            "small", "medium", "large", "huge",
            "dumbfire", "seeker");

    private EngineeringJournalBlueprintResolver() {
    }

    /**
     * Human-readable module label for UI: catalog type plus useful mount/hardpoint detail.
     * Examples: {@code Multi-cannon Gimbal Large}, {@code Life Support} (not Size/Class).
     */
    public static String displayModuleName(String moduleItem) {
        if (moduleItem == null || moduleItem.isBlank()) {
            return "";
        }
        String type = moduleItemToModuleType(moduleItem);
        String suffix = mountAndHardpointSuffix(moduleItem);
        if (type != null && !type.isBlank()) {
            return suffix.isBlank() ? type : type + " " + suffix;
        }
        return friendlifyItemIdWithoutSizeClass(moduleItem);
    }

    public record ResolvedBlueprint(String moduleType, String blueprintName) {
    }

    public static Optional<ResolvedBlueprint> resolve(String slot,
                                                        String journalBlueprintName,
                                                        EngineeringDatabase database) {
        return resolve(slot, null, journalBlueprintName, database);
    }

    /**
     * @param moduleItem journal {@code Module} / loadout {@code Item} id (e.g. {@code hpt_multicannon_turret_small});
     *                   required to disambiguate shared {@code Weapon_*} blueprint names
     */
    public static Optional<ResolvedBlueprint> resolve(String slot,
                                                        String moduleItem,
                                                        String journalBlueprintName,
                                                        EngineeringDatabase database) {
        if (journalBlueprintName != null && !journalBlueprintName.isBlank()) {
            ResolvedBlueprint mapped = BY_JOURNAL_NAME.get(journalBlueprintName);
            if (mapped != null) {
                return Optional.of(mapped);
            }
            if (database != null) {
                Optional<ResolvedBlueprint> derived =
                        deriveFromJournalName(slot, moduleItem, journalBlueprintName, database);
                if (derived.isPresent()) {
                    return derived;
                }
            }
        }
        return Optional.empty();
    }

    static Optional<ResolvedBlueprint> deriveFromJournalName(String slot,
                                                               String journalBlueprintName,
                                                               EngineeringDatabase database) {
        return deriveFromJournalName(slot, null, journalBlueprintName, database);
    }

    static Optional<ResolvedBlueprint> deriveFromJournalName(String slot,
                                                               String moduleItem,
                                                               String journalBlueprintName,
                                                               EngineeringDatabase database) {
        int underscore = journalBlueprintName.indexOf('_');
        if (underscore <= 0 || underscore >= journalBlueprintName.length() - 1) {
            return Optional.empty();
        }
        String journalSlot = journalBlueprintName.substring(0, underscore);
        String suffix = journalBlueprintName.substring(underscore + 1);
        String normSuffix = normalizeToken(suffix);
        if (normSuffix.isBlank()) {
            return Optional.empty();
        }

        String moduleType = moduleItemToModuleType(moduleItem);
        if (moduleType.isBlank()) {
            // "Weapon_Overcharged" is shared by every hardpoint weapon — never treat "Weapon" as a type.
            if (!isGenericWeaponJournalPrefix(journalSlot)) {
                moduleType = slotToModuleType(journalSlot);
            }
        }
        if (moduleType.isBlank()) {
            moduleType = slotToModuleType(slot);
            if (isHardpointSlotLabel(moduleType)) {
                moduleType = "";
            }
        }

        if (!moduleType.isBlank()) {
            return bestBlueprintForModule(database, moduleType, normSuffix);
        }
        // Last resort: suffix-only match across the catalog (ambiguous for Weapon_* without Module).
        return bestBlueprintAcrossCatalog(database, normSuffix);
    }

    private static Optional<ResolvedBlueprint> bestBlueprintForModule(
            EngineeringDatabase database, String moduleType, String normSuffix) {
        ResolvedBlueprint best = null;
        int bestScore = 0;
        for (String blueprintName : blueprintNamesForModule(database, moduleType)) {
            int score = matchScore(normSuffix, blueprintName);
            if (score > bestScore) {
                bestScore = score;
                best = new ResolvedBlueprint(moduleType, blueprintName);
            }
        }
        return bestScore > 0 ? Optional.of(best) : Optional.empty();
    }

    private static Optional<ResolvedBlueprint> bestBlueprintAcrossCatalog(
            EngineeringDatabase database, String normSuffix) {
        ResolvedBlueprint best = null;
        int bestScore = 0;
        int ties = 0;
        for (BlueprintGrade bp : database.getAllBlueprints()) {
            if (bp.isExperimental()) {
                continue;
            }
            int score = matchScore(normSuffix, bp.getName());
            if (score > bestScore) {
                bestScore = score;
                best = new ResolvedBlueprint(bp.getModuleType(), bp.getName());
                ties = 1;
            } else if (score > 0 && score == bestScore
                    && best != null
                    && (!best.moduleType().equalsIgnoreCase(bp.getModuleType())
                            || !best.blueprintName().equalsIgnoreCase(bp.getName()))) {
                ties++;
            }
        }
        // Ambiguous Weapon_* without Module/Item — refuse to guess.
        if (ties > 1 && bestScore < 100) {
            return Optional.empty();
        }
        if (ties > 1) {
            return Optional.empty();
        }
        return bestScore > 0 ? Optional.of(best) : Optional.empty();
    }

    private static Set<String> blueprintNamesForModule(EngineeringDatabase database, String moduleType) {
        Set<String> names = new HashSet<>();
        for (BlueprintGrade bp : database.getAllBlueprints()) {
            if (bp.isExperimental()) {
                continue;
            }
            if (bp.getModuleType().equalsIgnoreCase(moduleType)) {
                names.add(bp.getName());
            }
        }
        return names;
    }

    private static int matchScore(String normSuffix, String blueprintName) {
        String normName = normalizeToken(blueprintName);
        if (normName.startsWith(normSuffix)) {
            return 100;
        }
        if (normSuffix.length() >= 4 && normName.contains(normSuffix)) {
            return 80;
        }
        // Elite journal truncates / renames some blueprint suffixes.
        if (normSuffix.startsWith("thermic") && normName.contains("thermal")) {
            return 75;
        }
        if (normSuffix.startsWith("resistive") && normName.contains("resistance")) {
            return 75;
        }
        // Journal {@code FSD_LongRange} / {@code Hyperdrive_LongRange} → catalog "Increased FSD Range".
        if ("longrange".equals(normSuffix) && normName.contains("increasedfsdrange")) {
            return 90;
        }
        // Journal {@code HullReinforcement_Advanced} / {@code Armour_Advanced} → Lightweight.
        if ("advanced".equals(normSuffix) && normName.contains("lightweight")) {
            return 90;
        }
        // Journal truncates {@code HullReinforcement_Kinetic} (not KineticResistant).
        if ("kinetic".equals(normSuffix) && normName.contains("kineticresistant")) {
            return 90;
        }
        return 0;
    }

    public static String normalizeToken(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Module-family equality that ignores hyphens/spaces/case
     * ({@code Multi-cannon} vs {@code Multicannon}).
     */
    public static boolean sameModuleType(String a, String b) {
        String na = normalizeToken(a);
        String nb = normalizeToken(b);
        return !na.isEmpty() && na.equals(nb);
    }

    /**
     * Blueprint picker search: match catalog module/blueprint text against a free-form query,
     * including loadout component labels like {@code Multicannon Gimbal Large}.
     */
    public static boolean matchesModuleSearch(String query, String moduleType, String blueprintName) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String nq = normalizeToken(query);
        if (nq.isEmpty()) {
            return true;
        }
        String nModule = normalizeToken(moduleType);
        String nBlueprint = normalizeToken(blueprintName);
        String nhay = nModule + nBlueprint;
        if (nhay.contains(nq) || nq.contains(nhay)) {
            return true;
        }
        // Component labels often append mount/size after the family name.
        if (!nModule.isEmpty() && (nq.contains(nModule) || nModule.contains(nq))) {
            return true;
        }
        if (!nBlueprint.isEmpty() && (nq.contains(nBlueprint) || nBlueprint.contains(nq))) {
            return true;
        }
        for (String raw : query.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            String nt = normalizeToken(raw);
            if (nt.length() < 4) {
                continue;
            }
            if ((!nModule.isEmpty() && (nModule.contains(nt) || nt.contains(nModule)))
                    || (!nBlueprint.isEmpty() && nBlueprint.contains(nt))) {
                return true;
            }
        }
        String hay = ((moduleType != null ? moduleType : "") + " " + (blueprintName != null ? blueprintName : ""))
                .toLowerCase(Locale.ROOT);
        return hay.contains(query.toLowerCase(Locale.ROOT).trim());
    }

    static boolean isGenericWeaponJournalPrefix(String journalSlot) {
        return journalSlot != null && "weapon".equalsIgnoreCase(journalSlot.trim());
    }

    static boolean isHardpointSlotLabel(String moduleType) {
        if (moduleType == null || moduleType.isBlank()) {
            return false;
        }
        String n = normalizeToken(moduleType);
        return n.contains("hardpoint");
    }

    /**
     * Frontier {@code ExperimentalEffect} / {@code ApplyExperimentalEffect} codes whose catalog
     * display names do not appear in the journal id (e.g. {@code special_shieldcell_oversized} →
     * Boss Cells). Keys and values are {@link #normalizeToken(String)} form.
     */
    private static final Map<String, String> JOURNAL_EXPERIMENTAL_DISPLAY = Map.of(
            "specialshieldcelloversized", "bosscells",
            "specialshieldcellgradual", "recyclingcells",
            "specialshieldcellefficient", "flowcontrol",
            "specialshieldcelltoughened", "doublebraced",
            "specialshieldcelllightweight", "strippeddown");

    /**
     * Catalog experimental display name for a Frontier journal effect id, or empty when unknown.
     */
    public static String mappedExperimentalDisplayName(String journalEffect) {
        if (journalEffect == null || journalEffect.isBlank()) {
            return "";
        }
        String mapped = JOURNAL_EXPERIMENTAL_DISPLAY.get(normalizeToken(journalEffect));
        return mapped != null ? mapped : "";
    }

    /**
     * Maps a journal module / loadout item id to the catalog module type.
     * Hardpoint weapons share {@code Weapon_*} blueprint names; the item id is the disambiguator.
     */
    public static String moduleItemToModuleType(String moduleItem) {
        if (moduleItem == null || moduleItem.isBlank()) {
            return "";
        }
        String m = moduleItem.toLowerCase(Locale.ROOT);
        // Guardian / AX tech modules are not engineerable — never map them to a catalog type.
        if (m.contains("guardian") || m.contains("antixeno") || m.contains("anti_xeno")
                || m.contains("ax_") || m.contains("_ax") || m.contains("caustic")) {
            return "";
        }
        // More specific tokens first.
        if (m.contains("multicannon")) {
            return "Multi-cannon";
        }
        if (m.contains("pulselaserburst") || m.contains("burstlaser") || m.contains("pulseburst")) {
            return "Burst Laser";
        }
        if (m.contains("pulselaser")) {
            return "Pulse Laser";
        }
        if (m.contains("beamlaser")) {
            return "Beam Laser";
        }
        if (m.contains("slugshot") || m.contains("fragmentcannon")) {
            return "Fragment Cannon";
        }
        if (m.contains("plasmaaccelerator")) {
            return "Plasma Accelerator";
        }
        if (m.contains("railgun")) {
            return "Rail Gun";
        }
        if (m.contains("cannon")) {
            return "Cannon";
        }
        if (m.contains("mininglaser")) {
            return "Mining Laser";
        }
        if (m.contains("abrasionblaster") || m.contains("mining_abrblstr") || m.contains("abrblstr")) {
            return "Abrasion Blaster";
        }
        if (m.contains("enzyme")) {
            return "Enzyme Missile Rack";
        }
        if (m.contains("torpedo")) {
            return "Torpedo Pylon";
        }
        if (m.contains("dumbfire") || m.contains("missile") || m.contains("seekermissile")) {
            return "Missile Rack";
        }
        if (m.contains("minelauncher")) {
            return "Mine Launcher";
        }
        if (m.contains("shieldbooster")) {
            return "Shield Booster";
        }
        if (m.contains("shieldcellbank")) {
            return "Shield Cell Bank";
        }
        if (m.contains("shieldgenerator")) {
            return "Shield Generator";
        }
        if (m.contains("powerdistributor")) {
            return "Power Distributor";
        }
        if (m.contains("powerplant")) {
            return "Power Plant";
        }
        // Journal thrusters are {@code int_engine_*}; avoid matching power plant / beam laser "engine" tokens.
        if (m.contains("thruster") || m.startsWith("int_engine") || m.contains("_engine_")) {
            return "Thrusters";
        }
        if (m.contains("fsdinterdictor") || m.contains("hyperdriveinterdictor")) {
            return "Frame Shift Drive Interdictor";
        }
        // FSD booster (Guardian or otherwise) must not match the Frame Shift Drive family.
        if (m.contains("fsdbooster") || m.contains("fsd_booster")) {
            return "";
        }
        if (m.contains("fsd") || m.contains("hyperdrive")) {
            return "Frame Shift Drive";
        }
        if (m.contains("lifesupport")) {
            return "Life Support";
        }
        if (m.contains("detailedsurfacescanner") || m.contains("surfacescanner")) {
            return "Surface Scanner";
        }
        if (m.contains("modulereinforcement")) {
            return "Module Reinforcement Package";
        }
        if (m.contains("cargorack")) {
            return "Cargo Rack";
        }
        if (m.contains("sensors") || m.contains("sensor")) {
            return "Sensors";
        }
        if (m.contains("hullreinforcement")) {
            return "Hull Reinforcement Package";
        }
        if (m.contains("armour") || m.contains("armor")) {
            return "Armour";
        }
        if (m.contains("chafflauncher") || m.contains("chaff")) {
            return "Chaff Launcher";
        }
        if (m.contains("heatsink")) {
            return "Heat Sink Launcher";
        }
        if (m.contains("pointdefence") || m.contains("point defense")) {
            return "Point Defence";
        }
        if (m.contains("electroniccountermeasure") || m.contains("_ecm")) {
            return "Electronic Countermeasure";
        }
        if (m.contains("killwarrantscanner") || m.contains("killwarrant") || m.contains("crimescanner")) {
            return "Kill Warrant Scanner";
        }
        if (m.contains("cargoscanner") || m.contains("manifestscanner")) {
            return "Manifest Scanner";
        }
        if (m.contains("cloudscanner") || m.contains("wakescanner")) {
            return "Wake Scanner";
        }
        if (m.contains("fuelscoop")) {
            return "Fuel Scoop";
        }
        if (m.contains("repairer") || m.contains("autofield") || m.contains("afmu")) {
            return "Auto Field-Maintenance Unit";
        }
        if (m.contains("refinery")) {
            return "Refinery";
        }
        if (m.contains("dronecontrol_collection") || m.contains("collectorlimpet")) {
            return "Collector Limpet Controller";
        }
        if (m.contains("dronecontrol_fueltransfer") || m.contains("fueltransferlimpet")) {
            return "Fuel Transfer Limpet Controller";
        }
        if (m.contains("dronecontrol_prospector") || m.contains("prospectorlimpet")) {
            return "Prospector Limpet Controller";
        }
        if (m.contains("dronecontrol_hatchbreaker") || m.contains("hatchbreaker")) {
            return "Hatch Breaker Limpet Controller";
        }
        return "";
    }

    public static String slotToModuleType(String slot) {
        if (slot == null || slot.isBlank()) {
            return "";
        }
        String base = slot;
        int underscore = base.indexOf('_');
        if (underscore > 0) {
            base = base.substring(0, underscore);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(base.charAt(i - 1))) {
                out.append(' ');
            }
            out.append(c);
        }
        return out.toString().trim();
    }

    /**
     * Keep mount / hardpoint-size tokens only (Fixed, Gimbal, Large, …). Drop SizeN / ClassN.
     */
    private static String mountAndHardpointSuffix(String moduleItem) {
        List<String> kept = new ArrayList<>();
        for (String token : itemIdTokens(moduleItem)) {
            if (isSizeOrClassToken(token)) {
                continue;
            }
            if (!DISPLAY_SUFFIX_TOKENS.contains(token)) {
                continue;
            }
            kept.add(titleCaseDisplayToken(token));
        }
        return String.join(" ", kept);
    }

    private static String friendlifyItemIdWithoutSizeClass(String moduleItem) {
        List<String> kept = new ArrayList<>();
        for (String token : itemIdTokens(moduleItem)) {
            if (isSizeOrClassToken(token)) {
                continue;
            }
            kept.add(titleCaseDisplayToken(token));
        }
        return String.join(" ", kept);
    }

    private static List<String> itemIdTokens(String moduleItem) {
        String s = moduleItem.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("hpt_") || s.startsWith("int_")) {
            s = s.substring(4);
        }
        String[] parts = s.split("_+");
        List<String> tokens = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private static boolean isSizeOrClassToken(String token) {
        return token.matches("size\\d+") || token.matches("class\\d+");
    }

    private static String titleCaseDisplayToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        // Prefer short forms users expect in loadout UIs.
        if ("gimballed".equals(token)) {
            return "Gimbal";
        }
        if ("turreted".equals(token)) {
            return "Turret";
        }
        return Character.toUpperCase(token.charAt(0)) + token.substring(1).toLowerCase(Locale.ROOT);
    }

    private static Map<String, ResolvedBlueprint> loadJournalMap() {
        Map<String, ResolvedBlueprint> map = new HashMap<>();
        try (InputStream in = EngineeringJournalBlueprintResolver.class
                .getResourceAsStream("/engineering/journal_blueprint_map.json")) {
            if (in == null) {
                return map;
            }
            JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                return map;
            }
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                if (!e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getValue().getAsJsonObject();
                String moduleType = text(o, "moduleType");
                String blueprintName = text(o, "blueprintName");
                if (!moduleType.isBlank() && !blueprintName.isBlank()) {
                    map.put(e.getKey(), new ResolvedBlueprint(moduleType, blueprintName));
                }
            }
        } catch (Exception ignored) {
            // bundled map optional
        }
        return map;
    }

    private static String text(JsonObject o, String field) {
        if (o == null || !o.has(field) || o.get(field).isJsonNull()) {
            return "";
        }
        return o.get(field).getAsString();
    }
}
