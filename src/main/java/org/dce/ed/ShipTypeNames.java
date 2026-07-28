package org.dce.ed;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps Elite journal ship type ids ({@code cobramkiv}, {@code type9_military}) to display names
 * ({@code Cobra MkIV}, {@code Type-10 Defender}). Also learns localised names from journal when seen.
 */
public final class ShipTypeNames {

    /** Canonical display names keyed by normalised journal id (lowercase, no separators). */
    private static final Map<String, String> KNOWN = Map.ofEntries(
            Map.entry(key("sidewinder"), "Sidewinder"),
            Map.entry(key("eagle"), "Eagle"),
            Map.entry(key("hauler"), "Hauler"),
            Map.entry(key("adder"), "Adder"),
            Map.entry(key("viper"), "Viper MkIII"),
            Map.entry(key("viper_mkiii"), "Viper MkIII"),
            Map.entry(key("cobramkiii"), "Cobra MkIII"),
            Map.entry(key("type6"), "Type-6 Transporter"),
            Map.entry(key("dolphin"), "Dolphin"),
            Map.entry(key("type7"), "Type-7 Transporter"),
            Map.entry(key("asp"), "Asp Explorer"),
            Map.entry(key("vulture"), "Vulture"),
            Map.entry(key("empire_trader"), "Imperial Clipper"),
            Map.entry(key("federation_dropship"), "Federal Dropship"),
            Map.entry(key("orca"), "Orca"),
            Map.entry(key("type9"), "Type-9 Heavy"),
            Map.entry(key("python"), "Python"),
            Map.entry(key("belugaliner"), "Beluga Liner"),
            Map.entry(key("ferdelance"), "Fer-de-Lance"),
            Map.entry(key("anaconda"), "Anaconda"),
            Map.entry(key("federation_corvette"), "Federal Corvette"),
            Map.entry(key("cutter"), "Imperial Cutter"),
            Map.entry(key("diamondback"), "Diamondback Scout"),
            Map.entry(key("empire_courier"), "Imperial Courier"),
            Map.entry(key("diamondbackxl"), "Diamondback Explorer"),
            Map.entry(key("empire_eagle"), "Imperial Eagle"),
            Map.entry(key("federation_dropship_mkii"), "Federal Assault Ship"),
            Map.entry(key("federation_gunship"), "Federal Gunship"),
            Map.entry(key("viper_mkiv"), "Viper MkIV"),
            Map.entry(key("cobramkiv"), "Cobra MkIV"),
            Map.entry(key("independant_trader"), "Keelback"),
            Map.entry(key("asp_scout"), "Asp Scout"),
            Map.entry(key("type9_military"), "Type-10 Defender"),
            Map.entry(key("krait_mkii"), "Krait MkII"),
            Map.entry(key("typex"), "Alliance Chieftain"),
            Map.entry(key("typex_2"), "Alliance Crusader"),
            Map.entry(key("typex_3"), "Alliance Challenger"),
            Map.entry(key("krait_light"), "Krait Phantom"),
            Map.entry(key("mamba"), "Mamba"),
            Map.entry(key("python_nx"), "Python MkII"),
            Map.entry(key("type8"), "Type-8 Transporter"),
            Map.entry(key("mandalay"), "Mandalay"),
            Map.entry(key("cobramkv"), "Cobra MkV"),
            Map.entry(key("corsair"), "Corsair"),
            Map.entry(key("panthermkii"), "Panther Clipper MkII"),
            Map.entry(key("lakonminer"), "Type-11 Prospector"),
            Map.entry(key("explorer_nx"), "Caspian Explorer"),
            Map.entry(key("smallcombat01_nx"), "Kestrel Mk II"),
            Map.entry(key("mediumtransport01"), "Lynx Highliner"),
            Map.entry(key("empire_fighter"), "Imperial Fighter"),
            Map.entry(key("federation_fighter"), "Federal Fighter"),
            Map.entry(key("independent_fighter"), "Taipan Fighter"),
            Map.entry(key("gdn_hybrid_fighter_v1"), "Guardian Fighter"),
            Map.entry(key("gdn_hybrid_fighter_v2"), "Guardian Fighter"),
            Map.entry(key("gdn_hybrid_fighter_v3"), "Guardian Fighter"));

    private static final ConcurrentHashMap<String, String> LEARNED = new ConcurrentHashMap<>();

    private ShipTypeNames() {
    }

    /** Remember a journal id → localised display mapping (e.g. from StoredShips / ShipTargeted). */
    public static void learn(String internalId, String localised) {
        String k = key(internalId);
        if (k.isEmpty() || localised == null || localised.isBlank()) {
            return;
        }
        String display = localised.trim();
        if (display.isEmpty()) {
            return;
        }
        // Ignore localised that is just the same internal token (e.g. "CobraMkIV" for cobramkiv).
        if (key(display).equals(k) && looksInternal(display)) {
            return;
        }
        LEARNED.put(k, display);
    }

    /**
     * Human-readable ship type for UI. Accepts internal ids or already-localised names.
     */
    public static String display(String typeOrLocalised) {
        if (typeOrLocalised == null || typeOrLocalised.isBlank()) {
            return "";
        }
        String raw = typeOrLocalised.trim();
        String k = key(raw);
        if (k.isEmpty()) {
            return raw;
        }
        String learned = LEARNED.get(k);
        if (learned != null && !learned.isBlank()) {
            return learned;
        }
        // Already a spaced / hyphenated localised name — keep the caller's wording.
        if (raw.indexOf(' ') >= 0 || (raw.indexOf('-') >= 0 && !looksInternal(raw))) {
            return raw;
        }
        String known = KNOWN.get(k);
        if (known != null && !known.isBlank()) {
            return known;
        }
        return titleCaseTokens(raw);
    }

    /** True when {@code value} looks like a journal ship id rather than a display label. */
    public static boolean looksInternal(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String raw = value.trim();
        if (raw.indexOf(' ') >= 0) {
            return false;
        }
        String k = key(raw);
        return KNOWN.containsKey(k) || LEARNED.containsKey(k) || raw.indexOf('_') >= 0
                || raw.equals(raw.toLowerCase(Locale.ROOT));
    }

    /**
     * When merging catalog updates, keep a journal internal id over a display-name string so
     * {@link #display(String)} can resolve it consistently.
     */
    public static String preferType(String incoming, String previous) {
        String a = incoming != null ? incoming.trim() : "";
        String b = previous != null ? previous.trim() : "";
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        boolean aInternal = looksInternal(a);
        boolean bInternal = looksInternal(b);
        if (aInternal) {
            return a;
        }
        if (bInternal) {
            return b;
        }
        return a;
    }

    public static void clearLearnedForTests() {
        LEARNED.clear();
    }

    private static String key(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }

    private static String titleCaseTokens(String shipId) {
        StringBuilder out = new StringBuilder(shipId.length());
        boolean cap = true;
        for (int i = 0; i < shipId.length(); i++) {
            char c = shipId.charAt(i);
            if (c == '_' || c == ' ') {
                out.append(' ');
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
}
