package org.dce.ed.logreader;

import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.CarrierJumpRequestEvent;
import org.dce.ed.logreader.event.CarrierLocationEvent;

import com.google.gson.JsonObject;

/**
 * Tracks the commander's <em>owned</em> fleet carrier (by journal {@code CarrierID}), distinct from any carrier
 * they are currently aboard (friend's carrier, etc.).
 */
public final class OwnedFleetCarrierTracker {

    private long ownedCarrierId;
    private String ownedSystemName;
    private long ownedSystemAddress;
    private double[] ownedStarPos;
    private long lastCarrierLocationId;
    /** Most recent {@code CarrierStats} id — used to accept the next owned {@code CarrierJumpRequest}. */
    private long lastCarrierStatsId;
    /** {@code CarrierLocation} seen before {@code CarrierStats} established owned id (common on LoadGame). */
    private CarrierLocationEvent deferredOwnedLocation;

    public long getOwnedCarrierId() {
        return ownedCarrierId;
    }

    public String getOwnedSystemName() {
        return ownedSystemName;
    }

    public long getOwnedSystemAddress() {
        return ownedSystemAddress;
    }

    public double[] getOwnedStarPos() {
        return ownedStarPos;
    }

    public boolean hasOwnedCarrierId() {
        return ownedCarrierId != 0L;
    }

    public boolean hasOwnedCarrierLocation() {
        return ownedSystemAddress != 0L
                || (ownedSystemName != null && !ownedSystemName.isBlank());
    }

    public boolean isOwnedCarrierId(long carrierId) {
        return ownedCarrierId != 0L && carrierId == ownedCarrierId;
    }

    public void applyPersisted(Long carrierId, String systemName, Long systemAddress, double[] starPos) {
        ownedCarrierId = carrierId != null ? carrierId.longValue() : 0L;
        ownedSystemName = systemName;
        ownedSystemAddress = systemAddress != null ? systemAddress.longValue() : 0L;
        if (starPos != null && starPos.length >= 3) {
            ownedStarPos = starPos.clone();
        } else {
            ownedStarPos = null;
        }
    }

    /**
     * {@code CarrierStats} is emitted only when the owner opens carrier management — authoritative owned id.
     */
    public void onCarrierStats(long carrierId) {
        if (carrierId != 0L) {
            lastCarrierStatsId = carrierId;
            ownedCarrierId = carrierId;
            applyDeferredOwnedLocationIfMatching(carrierId);
        }
    }

    /**
     * Scheduling a jump from carrier management follows {@code CarrierStats} with the same {@code CarrierID}.
     */
    public void onCarrierJumpRequest(CarrierJumpRequestEvent req) {
        if (req == null) {
            return;
        }
        long carrierId = req.getCarrierId();
        if (carrierId == 0L) {
            return;
        }
        if (ownedCarrierId == 0L && lastCarrierStatsId != 0L && carrierId == lastCarrierStatsId) {
            ownedCarrierId = carrierId;
        }
    }

    public void onCarrierLocation(CarrierLocationEvent loc) {
        if (loc == null) {
            return;
        }
        long carrierId = loc.getCarrierId();
        if (carrierId != 0L) {
            lastCarrierLocationId = carrierId;
        }
        if (isOwnedCarrierId(carrierId)) {
            applyOwnedLocation(loc.getStarSystem(), loc.getSystemAddress(), null);
        } else if (ownedCarrierId == 0L && carrierId != 0L) {
            deferredOwnedLocation = loc;
        }
    }

    public void onDockedFleetCarrier(long marketId, String systemName, long systemAddress) {
        if (isOwnedCarrierId(marketId)) {
            applyOwnedLocation(systemName, systemAddress, null);
        }
    }

    /**
     * @param pendingJumpFromOwned {@code CarrierJumpRequest} for the owned carrier is still pending
     */
    public boolean isOwnedCarrierJump(CarrierJumpEvent jump, boolean pendingJumpFromOwned) {
        if (ownedCarrierId == 0L || jump == null) {
            return false;
        }
        if (pendingJumpFromOwned) {
            return true;
        }
        return lastCarrierLocationId == ownedCarrierId;
    }

    public void onOwnedCarrierJumpCompleted(CarrierJumpEvent jump) {
        if (jump == null) {
            return;
        }
        applyOwnedLocation(jump.getStarSystem(), jump.getSystemAddress(), jump.getStarPos());
    }

    public void onOwnedCarrierLocationArrival(CarrierLocationEvent loc) {
        if (loc == null) {
            return;
        }
        applyOwnedLocation(loc.getStarSystem(), loc.getSystemAddress(), null);
    }

    public static long carrierIdFromJson(JsonObject raw) {
        if (raw == null || !raw.has("CarrierID") || raw.get("CarrierID").isJsonNull()) {
            return 0L;
        }
        try {
            return raw.get("CarrierID").getAsLong();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public static long marketIdFromDockedJson(JsonObject raw) {
        if (raw == null || !raw.has("MarketID") || raw.get("MarketID").isJsonNull()) {
            return 0L;
        }
        try {
            return raw.get("MarketID").getAsLong();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void applyDeferredOwnedLocationIfMatching(long carrierId) {
        if (deferredOwnedLocation == null || deferredOwnedLocation.getCarrierId() != carrierId) {
            return;
        }
        applyOwnedLocation(
                deferredOwnedLocation.getStarSystem(),
                deferredOwnedLocation.getSystemAddress(),
                null);
        deferredOwnedLocation = null;
    }

    private void applyOwnedLocation(String systemName, long systemAddress, double[] starPos) {
        if (systemName != null && !systemName.isBlank()) {
            ownedSystemName = systemName;
        }
        if (systemAddress != 0L) {
            ownedSystemAddress = systemAddress;
        }
        if (starPos != null && starPos.length >= 3) {
            ownedStarPos = starPos.clone();
        }
    }
}
