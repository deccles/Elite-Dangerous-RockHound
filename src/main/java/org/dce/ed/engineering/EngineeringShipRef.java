package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.dce.ed.ShipTypeNames;

/**
 * A known commander hull for engineering goal association / filtering.
 */
public final class EngineeringShipRef {

    public static final long UNKNOWN_SHIP_ID = -1L;

    /** Elite ship IDs look like {@code VI0-2A} / {@code ABC-123}. */
    private static final Pattern SHIP_IDENT_PATTERN =
            Pattern.compile("(?i)^[A-Z0-9]{2,4}-[A-Z0-9]{1,4}$");

    private final long shipId;
    private final String shipType;
    private final String shipName;
    private final String shipIdent;

    public EngineeringShipRef(long shipId, String shipType, String shipName, String shipIdent) {
        this.shipId = shipId;
        this.shipType = shipType != null ? shipType : "";
        this.shipName = shipName != null ? shipName : "";
        this.shipIdent = shipIdent != null ? shipIdent : "";
    }

    public long getShipId() {
        return shipId;
    }

    public String getShipType() {
        return shipType;
    }

    public String getShipName() {
        return shipName;
    }

    public String getShipIdent() {
        return shipIdent;
    }

    public boolean isKnown() {
        return shipId >= 0;
    }

    /**
     * Default label without callsign. Prefer {@link EngineeringShipCatalog#displayLabel} when peers
     * are known so identical hulls can be disambiguated.
     */
    public String displayLabel() {
        return baseDisplayLabel();
    }

    /**
     * Type, plus a custom name only when it differs from the type. Never includes ship ident.
     */
    public String baseDisplayLabel() {
        String type = prettyType(shipType);
        String custom = extractCustomName(shipType, shipName, shipIdent);
        if (!type.isEmpty() && !custom.isEmpty()) {
            return type + " · " + custom;
        }
        if (!custom.isEmpty()) {
            return custom;
        }
        if (!type.isEmpty()) {
            return type;
        }
        return shipId >= 0 ? "Ship #" + shipId : "Unknown ship";
    }

    /** Callsign / id used only when {@link #baseDisplayLabel()} collides with another ship. */
    public String disambiguator() {
        String ident = trimToEmpty(shipIdent);
        String type = prettyType(shipType);
        if (!ident.isEmpty() && !sameLabel(ident, type) && !sameLabel(ident, shipType)) {
            return ident;
        }
        return shipId >= 0 ? "#" + shipId : "";
    }

    /**
     * Labels for a set of ships: base name, plus ident only when multiple ships share that base.
     */
    public static String displayLabelAmong(EngineeringShipRef ship, Collection<EngineeringShipRef> peers) {
        if (ship == null) {
            return "Unknown ship";
        }
        String base = ship.baseDisplayLabel();
        if (peers == null || peers.isEmpty()) {
            return base;
        }
        int sameBase = 0;
        for (EngineeringShipRef peer : peers) {
            if (peer != null && base.equalsIgnoreCase(peer.baseDisplayLabel())) {
                sameBase++;
                if (sameBase > 1) {
                    break;
                }
            }
        }
        if (sameBase <= 1) {
            return base;
        }
        String tag = ship.disambiguator();
        return tag.isEmpty() ? base : base + " · " + tag;
    }

    /**
     * True custom name only — strips type echoes, callsigns, and previously composed labels.
     */
    static String extractCustomName(String shipType, String shipName, String shipIdent) {
        String type = prettyType(shipType);
        String ident = trimToEmpty(shipIdent);
        String raw = trimToEmpty(shipName);
        if (raw.isEmpty()) {
            return "";
        }
        if (sameLabel(raw, type) || sameLabel(raw, shipType) || sameLabel(raw, ident)) {
            return "";
        }
        if (looksLikeShipIdent(raw)) {
            return "";
        }

        List<String> parts = splitLabelParts(raw);
        List<String> kept = new ArrayList<>(parts.size());
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sameLabel(part, type) || sameLabel(part, shipType)) {
                continue;
            }
            if (sameLabel(part, ident) || looksLikeShipIdent(part)) {
                continue;
            }
            if (!kept.isEmpty() && sameLabel(part, kept.get(kept.size() - 1))) {
                continue;
            }
            kept.add(part);
        }
        if (kept.isEmpty()) {
            return "";
        }
        // Single leftover that is only the hull type in disguise.
        if (kept.size() == 1 && (sameLabel(kept.get(0), type) || sameLabel(kept.get(0), shipType))) {
            return "";
        }
        return String.join(" · ", kept);
    }

    private static List<String> splitLabelParts(String raw) {
        String[] split = raw.split("\\s*[·•\\-–—|/]+\\s*");
        List<String> parts = new ArrayList<>(split.length);
        for (String s : split) {
            String t = s.trim();
            if (!t.isEmpty()) {
                parts.add(t);
            }
        }
        return parts;
    }

    private static boolean looksLikeShipIdent(String value) {
        return value != null && SHIP_IDENT_PATTERN.matcher(value.trim()).matches();
    }

    private static boolean sameLabel(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        return normalizeKey(a).equals(normalizeKey(b));
    }

    private static String normalizeKey(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String prettyType(String type) {
        return ShipTypeNames.display(type);
    }
}
