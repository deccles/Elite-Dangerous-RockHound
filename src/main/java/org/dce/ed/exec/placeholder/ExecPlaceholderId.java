package org.dce.ed.exec.placeholder;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Game-state symbols expandable as {@code $SYMBOL} in Exec program args. */
public enum ExecPlaceholderId {

    FLEET_CARRIER_DESTINATION("Next system hop on the Fleet Carrier route tab"),
    FLEET_ROUTE_CURRENT_SYSTEM("Current system on the Fleet Carrier route tab"),
    ROUTE_NEXT_DESTINATION("Next system hop on the ship Route tab"),
    ROUTE_CURRENT_SYSTEM("Current system on the route tab"),
    ROUTE_TARGET_SYSTEM("Active route target system"),
    ROUTE_DEST_NAME("Route destination body or name"),
    CARRIER_JUMP_TARGET("In-flight fleet carrier jump target (countdown)"),
    PENDING_JUMP_SYSTEM("Latched pending carrier jump system"),
    STATUS_DEST_SYSTEM("Status panel destination system"),
    STATUS_DEST_NAME("Status destination display name"),
    FSD_TARGET("Galactic route FSD target system"),
    FSD_REMAINING_JUMPS("Remaining jumps in plotted galactic route"),
    CLIPBOARD("Clipboard text at trigger time"),
    TRIGGER("Exec trigger id"),
    TIMESTAMP("Trigger fire time (ISO-8601)"),
    DESTINATION("Alias for fleet carrier next destination"),

    CARRIER_SYSTEM("Owned fleet carrier current system"),
    CARRIER_NAME("Fleet carrier name"),
    CARRIER_CALLSIGN("Fleet carrier callsign"),
    CARRIER_FUEL_LEVEL("Tritium in carrier tank (tons)"),
    CARRIER_FUEL_THRESHOLD("Configured low-tritium threshold (tons)"),
    CARRIER_PARKED_BODY_ID("Body id the carrier is parked at"),
    COMMANDER_ABOARD_CARRIER("Whether commander is aboard the fleet carrier (true/false)"),

    SYSTEM_NAME("Current system name"),
    SYSTEM_ADDRESS("Current system address"),
    STAR_POS("Current system coordinates x,y,z (ly)"),
    TARGET_BODY_NAME("System tab target body name"),
    NEAR_BODY_NAME("Nearest body name on system tab"),
    BODY_NAME("Current body from Status"),
    FSS_PROGRESS("FSS scan progress in current system"),
    TOTAL_BODIES("Total bodies in current system"),
    DOCKED("Whether docked (true/false)"),
    VISITED_BY_ME("Whether system was visited before (true/false)"),

    COMMANDER("Commander name"),
    GAME_MODE("Game mode (Solo/Open/Group)"),
    CREDITS("Bank balance (Cr)"),
    EXOBIOLOGY_CREDITS("Unsold exobiology estimate (Cr)"),
    GEO_SURVEY_CREDITS("Geo survey estimate total (Cr)"),
    BOUNTY_CREDITS("Unclaimed bounty total (Cr)"),
    CARGO("Cargo mass (t)"),
    SHIP_TYPE("Active ship internal type id"),
    SHIP_NAME("Custom ship name"),
    SHIP_IDENT("Ship nameplate ident"),
    SHIP_ID("Active ship id"),
    SHIP_FUEL("Main fuel tank amount"),
    SHIP_FUEL_CAPACITY("Main fuel tank capacity"),
    SHIP_FUEL_PERCENT("Main fuel percent (0-100)"),
    LEGAL_STATE("Legal state (Clean/Wanted/etc.)");

    private final String description;

    ExecPlaceholderId(String description) {
        this.description = description;
    }

    public String token() {
        return "$" + name();
    }

    public String getDescription() {
        return description;
    }

    public static Optional<ExecPlaceholderId> fromToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String trimmed = token.trim();
        if (trimmed.startsWith("$")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isBlank()) {
            return Optional.empty();
        }
        String id = trimmed.toUpperCase(Locale.ROOT);
        try {
            return Optional.of(valueOf(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static List<ExecPlaceholderId> sortedCatalog() {
        return Arrays.stream(values())
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    public static List<ExecPlaceholderId> matchingPrefix(String prefixAfterDollar) {
        if (prefixAfterDollar == null) {
            prefixAfterDollar = "";
        }
        String upper = prefixAfterDollar.toUpperCase(Locale.ROOT);
        return sortedCatalog().stream()
                .filter(id -> id.name().startsWith(upper))
                .toList();
    }
}
