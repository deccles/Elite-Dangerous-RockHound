package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
            String slotLabel,
            String moduleLabel,
            String moduleType,
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
            if (moduleType != null && !moduleType.isBlank()) {
                return moduleType.trim();
            }
            return moduleLabel != null ? moduleLabel.trim() : "";
        }

        /**
         * Short slot/size token for its own column: {@code Huge}, {@code Size 4}, or em dash
         * for core modules without a size (e.g. Armour).
         */
        public String slotSizeDisplay() {
            String shortSize = shortSlotSize(slotLabel);
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
            if (band == Band.GAP || level <= 0) {
                return "—";
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

    private final List<Row> rows;
    private final int gapCount;
    private final int partialCount;
    private final int doneCount;

    private ShipEngineeringSummary(List<Row> rows) {
        this.rows = List.copyOf(rows);
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
        StringBuilder sb = new StringBuilder(512);
        if (shipTitle != null && !shipTitle.isBlank()) {
            sb.append(shipTitle.trim()).append('\n');
        }
        sb.append(countsLine()).append('\n');
        appendBandSection(sb, Band.GAP);
        appendBandSection(sb, Band.PARTIAL);
        appendBandSection(sb, Band.DONE);
        return sb.toString().stripTrailing() + '\n';
    }

    private void appendBandSection(StringBuilder sb, Band band) {
        List<Row> section = rowsInBand(band);
        if (section.isEmpty()) {
            return;
        }
        sb.append('\n').append(section.get(0).bandLabel()).append('\n');
        for (Row row : section) {
            sb.append("  ").append(row.moduleDisplay());
            if (band != Band.GAP) {
                sb.append(" — ").append(row.blueprintDisplay())
                        .append(" · ").append(row.experimentalDisplay())
                        .append(" — ").append(row.levelDisplay());
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
        for (LoadoutEvent.Module module : loadout.getModules()) {
            if (module == null || module.getItem() == null || module.getItem().isBlank()) {
                continue;
            }
            if (isCosmeticItem(module.getItem())) {
                continue;
            }
            String moduleType = EngineeringJournalBlueprintResolver.moduleItemToModuleType(module.getItem());
            if (moduleType == null || moduleType.isBlank()) {
                continue;
            }
            String moduleLabel = EngineeringJournalBlueprintResolver.displayModuleName(module.getItem());
            String slotLabel = friendlifySlot(module.getSlot());
            LoadoutEvent.Engineering engineering = module.getEngineering();
            if (engineering == null || engineering.getLevel() <= 0) {
                built.add(new Row(
                        shipId,
                        slotLabel,
                        moduleLabel,
                        moduleType,
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
            Band band = (maxGrade > 0 && level >= maxGrade) ? Band.DONE : Band.PARTIAL;
            // Unknown max grade but engineered: treat as done at current level if quality complete.
            if (maxGrade <= 0) {
                band = Band.DONE;
            }
            built.add(new Row(
                    shipId,
                    slotLabel,
                    moduleLabel,
                    moduleType,
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
        return new ShipEngineeringSummary(built);
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
