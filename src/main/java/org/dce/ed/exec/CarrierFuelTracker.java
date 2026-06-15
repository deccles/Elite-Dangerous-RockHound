package org.dce.ed.exec;

import com.google.gson.JsonObject;

/**
 * Tracks owned fleet carrier fuel tank level from journal {@code CarrierStats.FuelLevel}
 * and detects crossing below a configurable threshold.
 */
public final class CarrierFuelTracker {

    private int lastKnownFuelLevel = -1;
    private boolean lowLatched;

    public int getLastKnownFuelLevel() {
        return lastKnownFuelLevel;
    }

    public boolean isLowLatched() {
        return lowLatched;
    }

    /**
     * @return {@code true} when fuel crosses from not-low to low (edge trigger)
     */
    public boolean updateFromCarrierStats(JsonObject raw, long ownedCarrierId, int threshold, int hysteresis) {
        if (raw == null || threshold < 0) {
            return false;
        }
        long carrierId = fuelCarrierIdFromJson(raw);
        if (carrierId == 0L) {
            return false;
        }
        if (ownedCarrierId != 0L && carrierId != ownedCarrierId) {
            return false;
        }
        int fuel = fuelLevelFromJson(raw);
        if (fuel < 0) {
            return false;
        }
        lastKnownFuelLevel = fuel;

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

    public static int fuelLevelFromJson(JsonObject raw) {
        if (raw == null || !raw.has("FuelLevel") || raw.get("FuelLevel").isJsonNull()) {
            return -1;
        }
        try {
            return raw.get("FuelLevel").getAsInt();
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
