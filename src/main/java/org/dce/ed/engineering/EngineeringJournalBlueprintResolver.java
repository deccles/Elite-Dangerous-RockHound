package org.dce.ed.engineering;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
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

    private EngineeringJournalBlueprintResolver() {
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
        return 0;
    }

    static String normalizeToken(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
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
     * Maps a journal module / loadout item id to the catalog module type.
     * Hardpoint weapons share {@code Weapon_*} blueprint names; the item id is the disambiguator.
     */
    public static String moduleItemToModuleType(String moduleItem) {
        if (moduleItem == null || moduleItem.isBlank()) {
            return "";
        }
        String m = moduleItem.toLowerCase(Locale.ROOT);
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
        if (m.contains("dumbfire") || m.contains("missile") || m.contains("seekermissile")) {
            return "Missile Rack";
        }
        if (m.contains("minelauncher")) {
            return "Mine Launcher";
        }
        if (m.contains("shieldbooster")) {
            return "Shield Booster";
        }
        if (m.contains("shieldgenerator")) {
            return "Shield Generator";
        }
        if (m.contains("powerdistributor")) {
            return "Power Distributor";
        }
        if (m.contains("powerplant") || m.contains("engine")) {
            // keep empty — not needed for Weapon_* case
        }
        if (m.contains("fsd") || m.contains("hyperdrive")) {
            return "Frame Shift Drive";
        }
        if (m.contains("thruster")) {
            return "Thrusters";
        }
        if (m.contains("lifesupport")) {
            return "Life Support";
        }
        if (m.contains("sensors") || m.contains("sensor")) {
            return "Sensors";
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
