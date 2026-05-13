package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

public class StatusEvent extends EliteLogEvent {
    private final int flags;
    private final int flags2;
    private final int[] pips;
    private final int fireGroup;
    private final int guiFocus;
    private final double fuelMain;
    private final double fuelReservoir;
    private final double cargo;
    private final String legalState;
    private final long balance;

    // Extra Status.json fields
    private final Double latitude;
    private final Double longitude;
    private final Double altitude;
    private final Double heading;
    private final String bodyName;
    private final Double planetRadius;

    /** Raw {@code BodyName} from Status.json before destination-name fallback (may be null/blank). */
    private final String bodyNamePhysical;
    /** Optional top-level {@code BodyID} from Status.json when the game provides it. */
    private final Integer statusBodyId;

    // Travel destination (added in later journal versions)
    private final Long destinationSystem;
    private final Integer destinationBody;
    private final String destinationName;

    /**
     * Human-readable destination name (when provided by Elite).
     * In Status.json this appears as Destination.Name_Localised.
     */
    private final String destinationNameLocalised;

    private final DecodedFlags decodedFlags;

    public StatusEvent(Instant timestamp,
                       JsonObject rawJson,
                       int flags,
                       int flags2,
                       int[] pips,
                       int fireGroup,
                       int guiFocus,
                       double fuelMain,
                       double fuelReservoir,
                       double cargo,
                       String legalState,
                       long balance,
                       Double latitude,
                       Double longitude,
                       Double altitude,
                       Double heading,
                       String bodyName,
                       Double planetRadius,
                       Long destinationSystem,
                       Integer destinationBody,
                       String destinationName,
                       String destinationNameLocalised,
                       String bodyNamePhysical,
                       Integer statusBodyId) {

        super(timestamp, EliteEventType.STATUS, rawJson);
        this.flags = flags;
        this.flags2 = flags2;
        this.pips = (pips != null ? pips : new int[3]);
        this.fireGroup = fireGroup;
        this.guiFocus = guiFocus;
        this.fuelMain = fuelMain;
        this.fuelReservoir = fuelReservoir;
        this.cargo = cargo;
        this.legalState = legalState;
        this.balance = balance;

        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.heading = heading;
        this.bodyName = bodyName;
        this.planetRadius = planetRadius;
        this.bodyNamePhysical = bodyNamePhysical;
        this.statusBodyId = statusBodyId;

        this.destinationSystem = destinationSystem;
        this.destinationBody = destinationBody;
        this.destinationName = destinationName;
        this.destinationNameLocalised = destinationNameLocalised;

        this.decodedFlags = new DecodedFlags(flags, flags2);
    }

    // ---- Raw values ----

    public int getFlags() {
        return flags;
    }

    public int getFlags2() {
        return flags2;
    }

    public int[] getPips() {
        return pips;
    }

    public int getFireGroup() {
        return fireGroup;
    }

    public int getGuiFocus() {
        return guiFocus;
    }

    public double getFuelMain() {
        return fuelMain;
    }

    public double getFuelReservoir() {
        return fuelReservoir;
    }

    public double getCargo() {
        return cargo;
    }

    public String getLegalState() {
        return legalState;
    }

    public long getBalance() {
        return balance;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Double getAltitude() {
        return altitude;
    }

    public Double getHeading() {
        return heading;
    }

    public String getBodyName() {
        return bodyName;
    }

    /**
     * {@code BodyName} field only (no destination fallback). Prefer for resolving which world you are
     * gravitationally near vs which body is selected in the nav panel.
     */
    public String getBodyNamePhysical() {
        return bodyNamePhysical;
    }

    /** Top-level {@code BodyID} from Status.json when present. */
    public Integer getStatusBodyId() {
        return statusBodyId;
    }

    public Double getPlanetRadius() {
        return planetRadius;
    }

    public Long getDestinationSystem() {
        return destinationSystem;
    }

    public Integer getDestinationBody() {
        return destinationBody;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public String getDestinationNameLocalised() {
        return destinationNameLocalised;
    }

    /**
     * Preferred display name for the destination.
     *
     * Elite sometimes reports Destination.Name as an internal localisation key (often starting with '$').
     * When Name_Localised exists, it is the best player-facing text.
     */
    public String getDestinationDisplayName() {
        return toDisplayName(destinationName, destinationNameLocalised);
    }

    private static String toDisplayName(String rawName, String localisedName) {
        if (localisedName != null && !localisedName.isBlank()) {
            return localisedName;
        }
        if (rawName == null || rawName.isBlank()) {
            return null;
        }

        // Many non-body targets come through as localisation keys like "$SOME_KEY;".
        // Some include parameters like ":#name=Foo;"; extract that if present.
        if (rawName.startsWith("$")) {
            String extracted = extractNameFromKey(rawName);
            if (extracted != null) {
                return extracted;
            }
            return deKey(rawName);
        }

        return rawName;
    }

    private static String extractNameFromKey(String key) {
        // Example: "$COMMS_entered:#name=Sol;" -> "Sol"
        int idx = key.indexOf("#name=");
        if (idx < 0) {
            return null;
        }
        int start = idx + "#name=".length();
        int end = key.indexOf(';', start);
        if (end < 0) {
            end = key.length();
        }
        String v = key.substring(start, end);
        if (v.isBlank()) {
            return null;
        }
        return v;
    }

    private static String deKey(String key) {
        String s = key;
        if (s.startsWith("$")) {
            s = s.substring(1);
        }
        if (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1);
        }
        // Drop any ":..." suffix which is usually parameters.
        int colon = s.indexOf(':');
        if (colon >= 0) {
            s = s.substring(0, colon);
        }
        s = s.replace('_', ' ').trim();
        if (s.isBlank()) {
            return null;
        }
        return s;
    }

    // ---- Convenience accessors for flags ----

    public DecodedFlags getDecodedFlags() {
        return decodedFlags;
    }

    public boolean isFsdCharging() {
        return decodedFlags.fsdCharging;
    }
    /**
     * True while the ship is in an FSD jump (hyperspace).
     *
     * In Status.json this corresponds to the 'FsdJump' flag.
     */
    public boolean isFsdJump() {
        return decodedFlags.fsdJump;
    }

    public boolean isFsdHyperdriveCharging() {
        return decodedFlags.fsdHyperdriveCharging;
    }

    public boolean isSupercruise() {
        return decodedFlags.supercruise;
    }

    public boolean isDocked() {
        return decodedFlags.docked;
    }

    public boolean isOnFoot() {
        return decodedFlags.onFoot;
    }

    public boolean isInSrv() {
        return decodedFlags.inSrv;
    }

    // Add more one-liners as needed (inFighter, nightVision, etc)

    public static final class DecodedFlags {

        // Flags (bitfield #1)
        public final boolean docked;
        public final boolean landed;
        public final boolean landingGearDown;
        public final boolean shieldsUp;
        public final boolean supercruise;
        public final boolean flightAssistOff;
        public final boolean hardpointsDeployed;
        public final boolean inWing;
        public final boolean lightsOn;
        public final boolean cargoScoopDeployed;
        public final boolean silentRunning;
        public final boolean scoopingFuel;
        public final boolean srvHandbrake;
        public final boolean srvUsingTurretView;
        public final boolean srvTurretRetracted;
        public final boolean srvDriveAssist;
        public final boolean fsdMassLocked;
        public final boolean fsdCharging;
        public final boolean fsdCooldown;
        public final boolean lowFuel;
        public final boolean overHeating;
        public final boolean hasLatLong;
        public final boolean isInDanger;
        public final boolean beingInterdicted;
        public final boolean inMainShip;
        public final boolean inFighter;
        public final boolean inSrv;
        public final boolean hudInAnalysisMode;
        public final boolean nightVision;
        public final boolean altitudeFromAverageRadius;
        public final boolean fsdJump;
        public final boolean srvHighBeam;

        // Flags2 (bitfield #2)
        public final boolean onFoot;
        public final boolean inTaxi;
        public final boolean inMulticrew;
        public final boolean onFootInStation;
        public final boolean onFootOnPlanet;
        public final boolean aimDownSight;
        public final boolean lowOxygen;
        public final boolean lowHealth;
        public final boolean cold;
        public final boolean hot;
        public final boolean veryCold;
        public final boolean veryHot;
        public final boolean glideMode;
        public final boolean onFootInHangar;
        public final boolean onFootSocialSpace;
        public final boolean onFootExterior;
        public final boolean breathableAtmosphere;
        public final boolean telepresenceMulticrew;
        public final boolean physicalMulticrew;
        public final boolean fsdHyperdriveCharging;

        private DecodedFlags(int flags, int flags2) {
            // Flags
            docked                    = (flags & 0x00000001) != 0;
            landed                    = (flags & 0x00000002) != 0;
            landingGearDown           = (flags & 0x00000004) != 0;
            shieldsUp                 = (flags & 0x00000008) != 0;
            supercruise               = (flags & 0x00000010) != 0;
            flightAssistOff           = (flags & 0x00000020) != 0;
            hardpointsDeployed        = (flags & 0x00000040) != 0;
            inWing                    = (flags & 0x00000080) != 0;
            lightsOn                  = (flags & 0x00000100) != 0;
            cargoScoopDeployed        = (flags & 0x00000200) != 0;
            silentRunning             = (flags & 0x00000400) != 0;
            scoopingFuel              = (flags & 0x00000800) != 0;
            srvHandbrake              = (flags & 0x00001000) != 0;
            srvUsingTurretView        = (flags & 0x00002000) != 0;
            srvTurretRetracted        = (flags & 0x00004000) != 0;
            srvDriveAssist            = (flags & 0x00008000) != 0;
            fsdMassLocked             = (flags & 0x00010000) != 0;
            fsdCharging               = (flags & 0x00020000) != 0;
            fsdCooldown               = (flags & 0x00040000) != 0;
            lowFuel                   = (flags & 0x00080000) != 0;
            overHeating               = (flags & 0x00100000) != 0;
            hasLatLong                = (flags & 0x00200000) != 0;
            isInDanger                = (flags & 0x00400000) != 0;
            beingInterdicted          = (flags & 0x00800000) != 0;
            inMainShip                = (flags & 0x01000000) != 0;
            inFighter                 = (flags & 0x02000000) != 0;
            inSrv                     = (flags & 0x04000000) != 0;
            hudInAnalysisMode         = (flags & 0x08000000) != 0;
            nightVision               = (flags & 0x10000000) != 0;
            altitudeFromAverageRadius = (flags & 0x20000000) != 0;
            fsdJump                   = (flags & 0x40000000) != 0;
            srvHighBeam               = (flags & 0x80000000) != 0;

            // Flags2
            onFoot                    = (flags2 & 0x00000001) != 0;
            inTaxi                    = (flags2 & 0x00000002) != 0;
            inMulticrew               = (flags2 & 0x00000004) != 0;
            onFootInStation           = (flags2 & 0x00000008) != 0;
            onFootOnPlanet            = (flags2 & 0x00000010) != 0;
            aimDownSight              = (flags2 & 0x00000020) != 0;
            lowOxygen                 = (flags2 & 0x00000040) != 0;
            lowHealth                 = (flags2 & 0x00000080) != 0;
            cold                      = (flags2 & 0x00000100) != 0;
            hot                       = (flags2 & 0x00000200) != 0;
            veryCold                  = (flags2 & 0x00000400) != 0;
            veryHot                   = (flags2 & 0x00000800) != 0;
            glideMode                 = (flags2 & 0x00001000) != 0;
            onFootInHangar            = (flags2 & 0x00002000) != 0;
            onFootSocialSpace         = (flags2 & 0x00004000) != 0;
            onFootExterior            = (flags2 & 0x00008000) != 0;
            breathableAtmosphere      = (flags2 & 0x00010000) != 0;
            telepresenceMulticrew     = (flags2 & 0x00020000) != 0;
            physicalMulticrew         = (flags2 & 0x00040000) != 0;
            fsdHyperdriveCharging     = (flags2 & 0x00080000) != 0;
        }

        public static DecodedFlags decode(int flags, int flags2) {
            return new DecodedFlags(flags, flags2);
        }
    }

}
