package org.dce.ed.mission;

import java.util.Locale;

/** User-selected ship and market-location constraints for commodity sources. */
public record CommoditySourceFilters(int minimumPadSize, boolean stations,
        boolean planetaryBases, boolean fleetCarriers) {

    public boolean matches(CommoditySourceChoice choice) {
        if (choice == null || choice.maxLandingPadSize() == null
                || choice.maxLandingPadSize() < minimumPadSize) return false;
        String type = choice.stationType() == null ? "" : choice.stationType().toLowerCase(Locale.ROOT);
        if (type.contains("fleetcarrier") || type.contains("fleet carrier")) return fleetCarriers;
        boolean planetary = type.contains("surface") || type.contains("settlement")
                || type.contains("planetary") || type.contains("crater");
        return planetary ? planetaryBases : stations;
    }
}
