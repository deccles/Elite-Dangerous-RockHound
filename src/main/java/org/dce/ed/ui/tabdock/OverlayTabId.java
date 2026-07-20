package org.dce.ed.ui.tabdock;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Detachable overlay tab identifiers (match {@code EliteOverlayTabbedPane} card names).
 */
public enum OverlayTabId {
    ROUTE("ROUTE", "Route"),
    SYSTEM("SYSTEM", "System"),
    BIOLOGY("BIOLOGY", "ExoBio"),
    MINING("MINING", "Mining"),
    MISSIONS("MISSIONS", "Missions"),
    FLEET_CARRIER("FLEET_CARRIER", "Fleet Carrier"),
    ENGINEERING("ENGINEERING", "Engineering"),
    CONTROL_PANEL("CONTROL_PANEL", "Control Panel");

    public static final String MAIN_DOCK_ID = "main";

    private final String cardName;
    private final String label;

    OverlayTabId(String cardName, String label) {
        this.cardName = cardName;
        this.label = label;
    }

    public String cardName() {
        return cardName;
    }

    public String label() {
        return label;
    }

    public static Optional<OverlayTabId> fromCardName(String cardName) {
        if (cardName == null || cardName.isBlank()) {
            return Optional.empty();
        }
        String key = cardName.trim().toUpperCase(Locale.ROOT);
        for (OverlayTabId id : values()) {
            if (id.cardName.equals(key)) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    public static Set<OverlayTabId> allDetachable() {
        return new LinkedHashSet<>(Arrays.asList(values()));
    }
}
