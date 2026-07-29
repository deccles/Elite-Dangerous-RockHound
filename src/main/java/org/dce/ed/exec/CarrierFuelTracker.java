package org.dce.ed.exec;

import com.google.gson.JsonObject;

/**
 * Tracks owned fleet carrier fuel tank level from journal {@code CarrierStats.FuelLevel}
 * and detects crossing below a configurable threshold. Also caches carrier {@code Name} and
 * {@code Callsign} from the same events for Exec placeholders.
 */
public final class CarrierFuelTracker {

    private int lastKnownFuelLevel = -1;
    private String lastKnownCarrierName;
    private String lastKnownCallsign;
    private boolean lowLatched;

    public int getLastKnownFuelLevel() {
        return lastKnownFuelLevel;
    }

    public String getLastKnownCarrierName() {
        return lastKnownCarrierName;
    }

    public String getLastKnownCallsign() {
        return lastKnownCallsign;
    }

    public boolean isLowLatched() {
        return lowLatched;
    }

    /**
     * Records {@code Name}, {@code Callsign}, and {@code FuelLevel} from a {@code CarrierStats} event
     * for the owned carrier (or any carrier when owned id is not yet known).
     */
    public boolean ingestCarrierStats(JsonObject raw, long ownedCarrierId) {
        if (!matchesOwnedCarrier(raw, ownedCarrierId)) {
            return false;
        }
        applyCarrierStatsFields(raw);
        return true;
    }

    /** @deprecated use {@link #ingestCarrierStats}; kept for bootstrap call sites */
@Deprecated
    public boolean recordFuelFromCarrierStats(JsonObject raw, long ownedCarrierId) {
        return ingestCarrierStats(raw, ownedCarrierId);
    }

    /**
     * @return {@code true} when fuel crosses from not-low to low (edge trigger)
     */
    public boolean updateFromCarrierStats(JsonObject raw, long ownedCarrierId, int threshold, int hysteresis) {
        if (!ingestCarrierStats(raw, ownedCarrierId)) {
            return false;
        }
        if (threshold < 0) {
            return false;
        }
        int fuel = lastKnownFuelLevel;

        int clearLevel = threshold + Math.max(0, hysteresis);
        if (fuel >= clearLevel) {
            lowLatched = false;
            return false;
        }
        if (fuel < threshold && !lowLatched) {
            lowLatched = true;
            return true;
        }
        return false;
    }

    private static boolean matchesOwnedCarrier(JsonObject raw, long ownedCarrierId) {
        if (raw == null) {
            return false;
        }
        long carrierId = fuelCarrierIdFromJson(raw);
        if (carrierId == 0L) {
            return false;
        }
        return ownedCarrierId == 0L || carrierId == ownedCarrierId;
    }

    private void applyCarrierStatsFields(JsonObject raw) {
        String name = carrierNameFromJson(raw);
        if (name != null && !name.isBlank()) {
            lastKnownCarrierName = name.trim();
        }
        String callsign = callsignFromJson(raw);
        if (callsign != null && !callsign.isBlank()) {
            lastKnownCallsign = callsign.trim();
        }
        int fuel = fuelLevelFromJson(raw);
        if (fuel >= 0) {
            lastKnownFuelLevel = fuel;
        }
    }

    public static int fuelLevelFromJson(JsonObject raw) {
        if (raw == null || !raw.has("FuelLevel") || raw.get("FuelLevel").isJsonNull()) {
            return -1;
        }
        try {
            return (int) Math.round(raw.get("FuelLevel").getAsDouble());
        } catch (Exception ignored) {
            return -1;
        }
    }

    public static long fuelCarrierIdFromJson(JsonObject raw) {
        if (raw == null || !raw.has("CarrierID") || raw.get("CarrierID").isJsonNull()) {
            return 0L;
        }
        try {
            return raw.get("CarrierID").getAsLong();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public static String callsignFromJson(JsonObject raw) {
        if (raw == null || !raw.has("Callsign") || raw.get("Callsign").isJsonNull()) {
            return null;
        }
        try {
            return raw.get("Callsign").getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String carrierNameFromJson(JsonObject raw) {
        if (raw == null || !raw.has("Name") || raw.get("Name").isJsonNull()) {
            return null;
        }
        try {
            return raw.get("Name").getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }
}
