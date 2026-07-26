package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.dce.ed.logreader.event.LoadoutEvent;

/**
 * Glanceable ship engineering status from a fitted loadout: Gap / Partial / Done.
 * Used by the Loadout dialog UI and clipboard text.
 */
public final class ShipEngineeringSummary {

    public enum Band {
        GAP,
        PARTIAL,
        DONE
    }

    /**
     * One engineerable fitted module.
     *
     * @param count identical modules merged for goal quantity (same type/blueprint/grade/exp)
     */
    public record Row(
            long shipId,
            /** Raw journal slot key (e.g. {@code Slot08_Size4}); used for goal binding. */
            String slotKey,
            String slotLabel,
            String moduleLabel,
            String moduleType,
            /** Journal loadout {@code Item} id (for size/class rating). */
            String moduleItem,
            String blueprintLabel,
            String experimentalLabel,
            int level,
            int maxGrade,
            int count,
            Band band) {

        public String moduleDisplay() {
            String component = componentDisplay();
            String size = slotSizeDisplay();
            if (!component.isBlank() && !size.isBlank() && !"—".equals(size)) {
                return component + " · " + size;
            }
            return !component.isBlank() ? component : size;
        }

        /** Catalog component name (e.g. Shield Booster), preferred over slot-first labels. */
        public String componentDisplay() {
            // Armour rows store "Armour · Reactive Surface Composite" in moduleLabel.
            if (moduleLabel != null && !moduleLabel.isBlank()
                    && moduleLabel.regionMatches(true, 0, "Armour", 0, 6)) {
                return moduleLabel.trim();
            }
            if (moduleType != null && !moduleType.isBlank()) {
                return moduleType.trim();
            }
            return moduleLabel != null ? moduleLabel.trim() : "";
        }

        /**
         * Slot/size token plus module class rating when known: {@code Tiny · A}, {@code Size 4 · B},
         * or just {@code A} for core internals. Em dash when neither size nor rating applies.
         */
        public String slotSizeDisplay() {
            String shortSize = shortSlotSize(slotLabel);
            String rating = moduleClassRating(moduleItem);
            if (!shortSize.isBlank() && !rating.isBlank()) {
                return shortSize + " · " + rating;
            }
            if (!rating.isBlank()) {
                return rating;
            }
            return shortSize.isBlank() ? "—" : shortSize;
        }

        /** Blueprint only (no grade / experimental). */
        public String blueprintDisplay() {
            if (band == Band.GAP || blueprintLabel == null || blueprintLabel.isBlank()) {
                return "—";
            }
            return blueprintLabel.trim();
        }

        /** Experimental only. */
        public String experimentalDisplay() {
            if (experimentalLabel == null || experimentalLabel.isBlank()) {
                return "—";
            }
            return experimentalLabel.trim();
        }

        /** Grade token like {@code G5}, or em dash. */
        public String levelDisplay() {
            return levelDisplay(0);
        }

        /**
         * Grade token for the Level column / clipboard.
         * With a higher goal target, Partial rows show {@code G3→G5}; otherwise just {@code G3}.
         */
        public String levelDisplay(int goalTargetGrade) {
            if (band == Band.GAP || level <= 0) {
                return "—";
            }
            if (band == Band.PARTIAL && goalTargetGrade > level) {
                return "G" + level + "→G" + goalTargetGrade;
            }
            return "G" + level;
        }

        /** Blueprint + grade + experimental, or em dash when none. */
        public String engineeringDisplay() {
            if (band == Band.GAP || level <= 0) {
                return "—";
            }
            StringBuilder sb = new StringBuilder();
            if (blueprintLabel != null && !blueprintLabel.isBlank()) {
                sb.append(blueprintLabel.trim());
            }
            if (level > 0) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append('G').append(level);
            }
            if (experimentalLabel != null && !experimentalLabel.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append(experimentalLabel.trim());
            }
            return sb.length() > 0 ? sb.toString() : "—";
        }

        public boolean canUpgrade() {
            return band == Band.PARTIAL
                    && moduleType != null && !moduleType.isBlank()
                    && blueprintLabel != null && !blueprintLabel.isBlank()
                    && maxGrade > 0
                    && level < maxGrade;
        }

        public String bandLabel() {
            return switch (band) {
                case GAP -> "No Engineering";
                case PARTIAL -> "Partial";
                case DONE -> "Done";
            };
        }
    }

    /**
     * Fitted module that is not engineerable (or not mapped to an engineering catalog type).
     * Included in View Summary / clipboard text only.
     */
    public record OtherModule(String slotLabel, String label, String moduleItem, int count) {
        public String display() {
            String size = shortSlotSize(slotLabel);
            String rating = moduleClassRating(moduleItem);
            String base = label != null ? label.trim() : "";
            StringBuilder sb = new StringBuilder();
            if (!base.isBlank()) {
                sb.append(base);
            }
            if (!size.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append(size);
            }
            if (!rating.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append(rating);
            }
            return sb.length() > 0 ? sb.toString() : "";
        }
    }

    private final List<Row> rows;
    private final List<OtherModule> otherModules;
    private final int gapCount;
    private final int partialCount;
    private final int doneCount;

    private ShipEngineeringSummary(List<Row> rows) {
        this(rows, List.of());
    }

    private ShipEngineeringSummary(List<Row> rows, List<OtherModule> otherModules) {
        this.rows = List.copyOf(rows);
        this.otherModules = List.copyOf(otherModules != null ? otherModules : List.of());
        int g = 0;
        int p = 0;
        int d = 0;
        for (Row row : this.rows) {
            switch (row.band()) {
                case GAP -> g += Math.max(1, row.count());
                case PARTIAL -> p += Math.max(1, row.count());
                case DONE -> d += Math.max(1, row.count());
            }
        }
        this.gapCount = g;
        this.partialCount = p;
        this.doneCount = d;
    }

    public List<Row> rows() {
        return rows;
    }

    public List<OtherModule> otherModules() {
        return otherModules;
    }

    public int gapCount() {
        return gapCount;
    }

    public int partialCount() {
        return partialCount;
    }

    public int doneCount() {
        return doneCount;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public String countsLine() {
        return gapCount + " no engineering · " + partialCount + " partial · " + doneCount + " done";
    }

    public List<Row> rowsInBand(Band band) {
        if (band == null) {
            return List.of();
        }
        List<Row> out = new ArrayList<>();
        for (Row row : rows) {
            if (row.band() == band) {
                out.add(row);
            }
        }
        return out;
    }

    public String toClipboardText(String shipTitle) {
        return toClipboardText(shipTitle, null);
    }

    /**
     * @param goalTargetForRow optional; returns the goal target grade for a row (0 if none)
     * @param experimentalForRow optional; custom Experimental column text (e.g. {@code +Mass Manager})
     */
    public String toClipboardText(String shipTitle, java.util.function.ToIntFunction<Row> goalTargetForRow) {
        return toClipboardText(shipTitle, goalTargetForRow, null);
    }

    public String toClipboardText(String shipTitle,
            java.util.function.ToIntFunction<Row> goalTargetForRow,
            java.util.function.Function<Row, String> experimentalForRow) {
        StringBuilder sb = new StringBuilder(512);
        if (shipTitle != null && !shipTitle.isBlank()) {
            sb.append(shipTitle.trim()).append('\n');
        }
        sb.append(countsLine()).append('\n');
        appendBandSection(sb, Band.GAP, goalTargetForRow, experimentalForRow);
        appendBandSection(sb, Band.PARTIAL, goalTargetForRow, experimentalForRow);
        appendBandSection(sb, Band.DONE, goalTargetForRow, experimentalForRow);
        appendOtherSection(sb);
        return sb.toString().stripTrailing() + '\n';
    }

    private void appendOtherSection(StringBuilder sb) {
        if (otherModules.isEmpty()) {
            return;
        }
        sb.append('\n').append("Other").append('\n');
        for (OtherModule other : otherModules) {
            sb.append("  ").append(other.display());
            if (other.count() > 1) {
                sb.append(" ×").append(other.count());
            }
            sb.append('\n');
        }
    }

    private void appendBandSection(StringBuilder sb, Band band,
            java.util.function.ToIntFunction<Row> goalTargetForRow,
            java.util.function.Function<Row, String> experimentalForRow) {
        List<Row> section = rowsInBand(band);
        if (section.isEmpty()) {
            return;
        }
        sb.append('\n').append(section.get(0).bandLabel()).append('\n');
        for (Row row : section) {
            sb.append("  ").append(row.moduleDisplay());
            if (band != Band.GAP) {
                int target = goalTargetForRow != null ? goalTargetForRow.applyAsInt(row) : 0;
                String experimental = experimentalForRow != null
                        ? experimentalForRow.apply(row)
                        : row.experimentalDisplay();
                sb.append(" — ").append(row.blueprintDisplay())
                        .append(" · ").append(experimental)
                        .append(" — ").append(row.levelDisplay(target));
            }
            if (row.count() > 1) {
                sb.append(" ×").append(row.count());
            }
            sb.append('\n');
        }
    }

    public static ShipEngineeringSummary fromLoadout(LoadoutEvent loadout, EngineeringDatabase db) {
        if (loadout == null) {
            return new ShipEngineeringSummary(List.of());
        }
        long shipId = loadout.getShipId();
        List<Row> built = new ArrayList<>();
        List<OtherModule> otherBuilt = new ArrayList<>();
        for (LoadoutEvent.Module module : loadout.getModules()) {
            if (module == null || module.getItem() == null || module.getItem().isBlank()) {
                continue;
            }
            if (isCosmeticItem(module.getItem()) || isStructuralNoiseItem(module.getItem())) {
                continue;
            }
            String moduleType = EngineeringJournalBlueprintResolver.moduleItemToModuleType(module.getItem());
            if (moduleType == null || moduleType.isBlank()) {
                String label = nonEngineeringLabel(module.getItem());
                if (!label.isBlank()) {
                    otherBuilt.add(new OtherModule(
                            friendlifySlot(module.getSlot()),
                            label,
                            module.getItem(),
                            1));
                }
                continue;
            }
            String moduleLabel = EngineeringJournalBlueprintResolver.displayModuleName(module.getItem());
            String armourType = armourBulkheadName(module.getItem());
            if (!armourType.isBlank()) {
                moduleLabel = "Armour · " + armourType;
            }
            String slotKey = module.getSlot() != null ? module.getSlot().trim() : "";
            String slotLabel = friendlifySlot(module.getSlot());
            LoadoutEvent.Engineering engineering = module.getEngineering();
            if (engineering == null || engineering.getLevel() <= 0) {
                built.add(new Row(
                        shipId,
                        slotKey,
                        slotLabel,
                        moduleLabel,
                        moduleType,
                        module.getItem(),
                        "",
                        "",
                        0,
                        0,
                        1,
                        Band.GAP));
                continue;
            }
            String blueprint = friendlyBlueprint(module, engineering, db);
            String experimental = friendlyExperimental(engineering, db);
            int level = engineering.getLevel();
            int maxGrade = maxBlueprintGrade(db, moduleType, blueprint);
            Band band;
            if (maxGrade > 0) {
                band = level >= maxGrade ? Band.DONE : Band.PARTIAL;
            } else {
                // Unknown catalog ceiling (unresolved journal name): do not claim Done.
                band = Band.PARTIAL;
            }
            built.add(new Row(
                    shipId,
                    slotKey,
                    slotLabel,
                    moduleLabel,
                    moduleType,
                    module.getItem(),
                    blueprint,
                    experimental,
                    level,
                    maxGrade,
                    1,
                    band));
        }
        built.sort(Comparator
                .comparingInt((Row r) -> bandOrder(r.band()))
                .thenComparing(Row::slotLabel, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Row::moduleLabel, String.CASE_INSENSITIVE_ORDER));
        return new ShipEngineeringSummary(built, mergeOtherModules(otherBuilt));
    }

    private static int bandOrder(Band band) {
        return switch (band) {
            case GAP -> 0;
            case PARTIAL -> 1;
            case DONE -> 2;
        };
    }

    static boolean isCosmeticItem(String item) {
        String m = item.toLowerCase(Locale.ROOT);
        return m.startsWith("paintjob") || m.startsWith("decal") || m.startsWith("nameplate")
                || m.startsWith("voicepack") || m.startsWith("bobble") || m.contains("shipkit")
                || m.contains("weaponcustomisation") || m.contains("enginecustomisation");
    }

    /** Cockpit / hatch noise that should not appear in the summary. */
    static boolean isStructuralNoiseItem(String item) {
        if (item == null || item.isBlank()) {
            return true;
        }
        String m = item.toLowerCase(Locale.ROOT);
        return m.contains("cockpit")
                || m.contains("cargobaydoor")
                || m.contains("modularcargobay")
                || m.contains("cargohatch");
    }

    /**
     * Bulkhead grade name for armour item ids, e.g. {@code anaconda_armour_reactive}
     * → {@code Reactive Surface Composite}.
     */
    static String armourBulkheadName(String item) {
        if (item == null || item.isBlank()) {
            return "";
        }
        String m = item.toLowerCase(Locale.ROOT);
        if (!(m.contains("armour") || m.contains("armor"))) {
            return "";
        }
        if (m.contains("reactive")) {
            return "Reactive Surface Composite";
        }
        if (m.contains("mirrored")) {
            return "Mirrored Surface Composite";
        }
        if (m.contains("grade3") || m.contains("military")) {
            return "Military Grade Composite";
        }
        if (m.contains("grade2") || m.contains("reinforced")) {
            return "Reinforced Alloys";
        }
        if (m.contains("grade1") || m.contains("lightweight")) {
            return "Lightweight Alloys";
        }
        return "";
    }

    /** Human label for fitted modules that are not in the engineering catalog. */
    static String nonEngineeringLabel(String item) {
        if (item == null || item.isBlank()) {
            return "";
        }
        String m = item.toLowerCase(Locale.ROOT);
        if (m.contains("fighterbay") || m.contains("fighterhangar")) {
            return "Fighter Hangar";
        }
        if (m.contains("modulereinforcement")) {
            return "Module Reinforcement Package";
        }
        if (m.contains("cargorack")) {
            return "Cargo Rack";
        }
        if (m.contains("passengercabin") || m.contains("_cabin_")) {
            return "Passenger Cabin";
        }
        if (m.contains("dockingcomputer")) {
            return m.contains("advanced") ? "Advanced Docking Computer" : "Docking Computer";
        }
        if (m.contains("supercruiseassist")) {
            return "Supercruise Assist";
        }
        if (m.contains("buggybay") || m.contains("planetvehicle") || m.contains("srvhangar")
                || m.contains("vehiclehangar")) {
            return "Planetary Vehicle Hangar";
        }
        if (m.contains("planetapproach")) {
            return "Planetary Approach Suite";
        }
        if (m.contains("fueltank")) {
            return "Fuel Tank";
        }
        if (m.contains("fuelscoop")) {
            return "Fuel Scoop";
        }
        if (m.contains("repairer") || m.contains("autofield") || m.contains("afmu")) {
            return "Auto Field-Maintenance Unit";
        }
        if (m.contains("guardian")) {
            return guardianModuleLabel(item);
        }
        if (m.contains("dronecontrol") || m.contains("limpet")) {
            return limpetControllerLabel(item);
        }
        if (m.contains("fsdbooster") || m.contains("fsd_booster")) {
            return "FSD Booster";
        }
        // Fallback: strip int_/hpt_ and size/class tokens.
        return friendlifyModuleItemId(item);
    }

    private static String guardianModuleLabel(String item) {
        String m = item.toLowerCase(Locale.ROOT);
        if (m.contains("hullreinforcement")) {
            return "Guardian Hull Reinforcement";
        }
        if (m.contains("modulereinforcement") || m.contains("module_reinforcement")) {
            return "Guardian Module Reinforcement";
        }
        if (m.contains("shieldreinforcement") || m.contains("shield_reinforcement")) {
            return "Guardian Shield Reinforcement";
        }
        if (m.contains("fsdbooster") || m.contains("fsd_booster")) {
            return "Guardian FSD Booster";
        }
        if (m.contains("gausscannon") || m.contains("gauss")) {
            return "Guardian Gauss Cannon";
        }
        if (m.contains("plasmacharger") || m.contains("plasma")) {
            return "Guardian Plasma Charger";
        }
        if (m.contains("shardcannon") || m.contains("shard")) {
            return "Guardian Shard Cannon";
        }
        return "Guardian Module";
    }

    private static String limpetControllerLabel(String item) {
        String m = item.toLowerCase(Locale.ROOT);
        if (m.contains("collection") || m.contains("collector")) {
            return "Collector Limpet Controller";
        }
        if (m.contains("fueltransfer")) {
            return "Fuel Transfer Limpet Controller";
        }
        if (m.contains("prospector")) {
            return "Prospector Limpet Controller";
        }
        if (m.contains("hatchbreaker")) {
            return "Hatch Breaker Limpet Controller";
        }
        if (m.contains("repair")) {
            return "Repair Limpet Controller";
        }
        if (m.contains("decontamination")) {
            return "Decontamination Limpet Controller";
        }
        if (m.contains("recon")) {
            return "Recon Limpet Controller";
        }
        if (m.contains("research")) {
            return "Research Limpet Controller";
        }
        return "Limpet Controller";
    }

    private static String friendlifyModuleItemId(String item) {
        String s = item.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("hpt_") || s.startsWith("int_")) {
            s = s.substring(4);
        }
        StringBuilder out = new StringBuilder();
        for (String part : s.split("_+")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (part.matches("size\\d+") || part.matches("class\\d+")) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(titleCaseWords(part));
        }
        return out.toString().trim();
    }

    private static List<OtherModule> mergeOtherModules(List<OtherModule> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Map<String, OtherModule> merged = new LinkedHashMap<>();
        for (OtherModule other : raw) {
            if (other == null || other.label() == null || other.label().isBlank()) {
                continue;
            }
            String size = shortSlotSize(other.slotLabel());
            String rating = moduleClassRating(other.moduleItem());
            String key = other.label().trim().toLowerCase(Locale.ROOT)
                    + "\0" + size.toLowerCase(Locale.ROOT)
                    + "\0" + rating;
            OtherModule prev = merged.get(key);
            if (prev == null) {
                merged.put(key, other);
            } else {
                merged.put(key, new OtherModule(
                        prev.slotLabel(),
                        prev.label(),
                        prev.moduleItem(),
                        prev.count() + other.count()));
            }
        }
        List<OtherModule> out = new ArrayList<>(merged.values());
        out.sort(Comparator
                .comparing(OtherModule::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(o -> shortSlotSize(o.slotLabel()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(o -> moduleClassRating(o.moduleItem()), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    static String friendlyBlueprint(LoadoutEvent.Module module,
            LoadoutEvent.Engineering engineering,
            EngineeringDatabase db) {
        Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
                EngineeringJournalBlueprintResolver.resolve(
                        module.getSlot(), module.getItem(), engineering.getBlueprintName(), db);
        if (resolved.isPresent()) {
            String bp = resolved.get().blueprintName();
            if (bp != null && !bp.isBlank()) {
                return bp;
            }
        }
        return friendlifyJournalToken(engineering.getBlueprintName());
    }

    static String friendlyExperimental(LoadoutEvent.Engineering engineering, EngineeringDatabase db) {
        String localised = engineering.getExperimentalEffectLocalised();
        if (localised != null && !localised.isBlank()) {
            return localised.trim();
        }
        String effect = engineering.getExperimentalEffect();
        if (effect == null || effect.isBlank()) {
            return "";
        }
        if (db != null) {
            String norm = normalizeToken(effect);
            for (BlueprintGrade bp : db.getAllBlueprints()) {
                if (!bp.isExperimental()) {
                    continue;
                }
                String nName = normalizeToken(bp.getName());
                String nId = normalizeToken(bp.getId());
                if ((!nName.isEmpty() && (norm.contains(nName) || nName.contains(norm)))
                        || (!nId.isEmpty() && (norm.contains(nId) || nId.contains(norm)))) {
                    return bp.getName();
                }
            }
        }
        return friendlifyJournalToken(effect);
    }

    static int maxBlueprintGrade(EngineeringDatabase db, String moduleType, String blueprintName) {
        if (db == null || moduleType == null || moduleType.isBlank()
                || blueprintName == null || blueprintName.isBlank()) {
            return 0;
        }
        int max = 0;
        for (BlueprintGrade bp : db.gradesFor(moduleType, blueprintName)) {
            if (bp != null && !bp.isExperimental()) {
                max = Math.max(max, bp.getGrade());
            }
        }
        if (max > 0) {
            return max;
        }
        for (BlueprintGrade bp : db.getAllBlueprints()) {
            if (bp == null || bp.isExperimental()) {
                continue;
            }
            if (!EngineeringJournalBlueprintResolver.sameModuleType(moduleType, bp.getModuleType())) {
                continue;
            }
            if (!EngineeringJournalBlueprintResolver.normalizeToken(blueprintName)
                    .equals(EngineeringJournalBlueprintResolver.normalizeToken(bp.getName()))) {
                continue;
            }
            max = Math.max(max, bp.getGrade());
        }
        return max;
    }

    static String friendlifySlot(String slot) {
        if (slot == null || slot.isBlank()) {
            return "";
        }
        String s = slot.trim().replace('_', ' ');
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (i > 0) {
                char prev = s.charAt(i - 1);
                boolean boundary = (Character.isLowerCase(prev) && Character.isUpperCase(c))
                        || (Character.isLetter(prev) && Character.isDigit(c))
                        || (Character.isDigit(prev) && Character.isLetter(c));
                if (boundary && out.charAt(out.length() - 1) != ' ') {
                    out.append(' ');
                }
            }
            out.append(c);
        }
        return titleCaseWords(out.toString());
    }

    /**
     * Module class rating letter from a journal item id ({@code …_class5} → {@code A}).
     * Elite maps class 1–5 to E–A. Empty when the item has no class token.
     */
    static String moduleClassRating(String item) {
        if (item == null || item.isBlank()) {
            return "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:^|_)class([1-5])(?:_|$)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(item.trim());
        if (!m.find()) {
            return "";
        }
        int clazz = Integer.parseInt(m.group(1));
        return switch (clazz) {
            case 1 -> "E";
            case 2 -> "D";
            case 3 -> "C";
            case 4 -> "B";
            case 5 -> "A";
            default -> "";
        };
    }

    /**
     * Compact size/mount for Loadout UI: {@code HugeHardpoint1} → {@code Huge},
     * {@code Slot09_Size4} / {@code Slot 09 Size 4} → {@code Size 4}.
     */
    static String shortSlotSize(String slotOrLabel) {
        if (slotOrLabel == null || slotOrLabel.isBlank()) {
            return "";
        }
        String raw = slotOrLabel.trim();
        String compact = raw.replace('_', ' ');
        String lower = compact.toLowerCase(Locale.ROOT);

        // Hardpoints / utility: keep only the class word (Medium → Med).
        for (String size : List.of("Huge", "Large", "Medium", "Small", "Tiny")) {
            String needle = size.toLowerCase(Locale.ROOT);
            if (lower.contains(needle + "hardpoint")
                    || lower.contains(needle + " hardpoint")
                    || lower.startsWith(needle + " ")
                    || lower.equals(needle)) {
                return "Medium".equals(size) ? "Med" : size;
            }
        }

        // Optional internal: "Slot 09 Size 4" / "Slot09Size4"
        java.util.regex.Matcher sizeMatch = java.util.regex.Pattern
                .compile("(?:^|\\s)size\\s*(\\d+)\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(compact);
        if (sizeMatch.find()) {
            return "Size " + sizeMatch.group(1);
        }
        java.util.regex.Matcher camelSize = java.util.regex.Pattern
                .compile("Size(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(raw);
        if (camelSize.find()) {
            return "Size " + camelSize.group(1);
        }

        // Core internals (Armour, Power Plant, …): no size column value.
        if (lower.contains("hardpoint") || lower.startsWith("slot")) {
            String friend = friendlifySlot(raw);
            return friend.isBlank() ? "" : friend;
        }
        return "";
    }

    static String friendlifyJournalToken(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String s = value.trim();
        if (s.regionMatches(true, 0, "special_", 0, 8)) {
            s = s.substring(8);
        }
        return titleCaseWords(s.replace('_', ' '));
    }

    private static String titleCaseWords(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        boolean cap = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                out.append(c);
                cap = true;
            } else if (cap) {
                out.append(Character.toUpperCase(c));
                cap = false;
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString().trim();
    }

    private static String normalizeToken(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
