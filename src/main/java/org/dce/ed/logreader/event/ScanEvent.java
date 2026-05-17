package org.dce.ed.logreader.event;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/**
 * Scan event for stars and bodies.
 * Backed by the journal "Scan" event.
 */
public final class ScanEvent extends EliteLogEvent {

    /**
     * One entry from the journal Parents[] array.
     *
     * Example JSON element: {"Star": 5}
     */
    public static final class ParentRef {
        private final String type;
        private final int bodyId;

        public ParentRef(String type, int bodyId) {
            this.type = type;
            this.bodyId = bodyId;
        }

        public String getType() {
            return type;
        }

        public int getBodyId() {
            return bodyId;
        }

        @Override
        public String toString() {
            return type + ":" + bodyId;
        }
    }

    /** One ring from the journal {@code Rings} array. */
    public static final class RingInfo {
        private final String name;
        private final String ringClass;

        public RingInfo(String name, String ringClass) {
            this.name = name;
            this.ringClass = ringClass;
        }

        /** Journal {@code Name} (may be null). */
        public String getName() {
            return name;
        }

        /** Journal {@code RingClass} (e.g. MetalRich, Icy). */
        public String getRingClass() {
            return ringClass;
        }
    }

    private final String bodyName;
    private final int bodyId;
    private final String starSystem;
    public String getStarSystem() {
		return starSystem;
	}

	private final long systemAddress;
    private final double distanceFromArrivalLs;
    private final boolean landable;
    private final String planetClass;
    private final String atmosphere;
    private final Map<String, Double> atmosphereComposition;
private final String terraformState;
    /** Earth masses ({@code MassEM}); planets/moons only. */
    private final Double massEm;
    private final Double surfaceGravity;
    private final Double surfaceTemperature;
    private final Double orbitalPeriod;

    /**
     * Journal {@code SemiMajorAxis}: semi-major axis of this body's orbit around its parent, metres.
     */
    private final Double semiMajorAxisM;
    /** Journal {@code Eccentricity}. */
    private final Double eccentricity;
    /** Journal {@code OrbitalInclination} (radians in current journal docs). */
    private final Double orbitalInclination;
    /** Journal {@code Periapsis} (argument of periapsis; radians in current journal docs). */
    private final Double periapsis;
    /** Journal {@code AscendingNode} when present (radians in current journal docs). */
    private final Double ascendingNode;
    /** Journal {@code MeanAnomaly} when present (radians in current journal docs). */
    private final Double meanAnomaly;

    private final String volcanism;
    private final Boolean wasDiscovered;
    private final Boolean wasMapped;
    private final Boolean wasFootfalled;

	private final String starType;
    private final List<ParentRef> parents;
	private Double surfacePressure;
    private final List<RingInfo> rings;
    private final String reserveLevel;
    /** Journal {@code ScanType} (e.g. {@code AutoScan}, {@code Detailed}); may be null on older lines. */
    private final String scanType;

    public ScanEvent(Instant timestamp,
                     JsonObject rawJson,
                     String bodyName,
                     int bodyId,
                     String starSystem,
                     long systemAddress,
                     double distanceFromArrivalLs,
                     boolean landable,
                     String planetClass,
                     String atmosphere,
                     String terraformState,
                     Double massEm,
                     Double surfaceGravity,
                     Double surfacePressure,
                     Double surfaceTemperature,
                     Double orbitalPeriod,
                     Double semiMajorAxisM,
                     Double eccentricity,
                     Double orbitalInclination,
                     Double periapsis,
                     Double ascendingNode,
                     Double meanAnomaly,
                     String volcanism,
                     Boolean wasDiscovered,
                     Boolean wasMapped,
                     Boolean wasFootfalled,
                     Map<String, Double> atmosphereComposition,
                     String starType,
                     List<ParentRef> parents,
                     List<RingInfo> rings,
                     String reserveLevel,
                     String scanType) {

        super(timestamp, EliteEventType.SCAN, rawJson);
        this.bodyName = bodyName;
        this.bodyId = bodyId;
        this.starSystem = starSystem;
        this.systemAddress = systemAddress;
        this.distanceFromArrivalLs = distanceFromArrivalLs;
        this.landable = landable;
        this.planetClass = planetClass;
        this.atmosphere = atmosphere;
        this.terraformState = terraformState;
        this.massEm = massEm;
        this.surfaceGravity = surfaceGravity;
        this.surfacePressure = surfacePressure;
        this.surfaceTemperature = surfaceTemperature;
        this.orbitalPeriod = orbitalPeriod;
        this.semiMajorAxisM = semiMajorAxisM;
        this.eccentricity = eccentricity;
        this.orbitalInclination = orbitalInclination;
        this.periapsis = periapsis;
        this.ascendingNode = ascendingNode;
        this.meanAnomaly = meanAnomaly;
        this.volcanism = volcanism;
        this.wasDiscovered = wasDiscovered;
        this.wasMapped = wasMapped;
        this.wasFootfalled = wasFootfalled;
        this.atmosphereComposition = (atmosphereComposition == null) ? Collections.emptyMap() : atmosphereComposition;
        
        this.starType = starType;
        this.parents = (parents == null) ? Collections.emptyList() : parents;
        this.rings = (rings == null) ? Collections.emptyList() : rings;
        this.reserveLevel = reserveLevel;
        this.scanType = scanType;
    }

    /** Raw journal {@code ScanType}, or null if absent. */
    public String getScanType() {
        return scanType;
    }

    public String getBodyName() {
        return bodyName;
    }

    public int getBodyId() {
        return bodyId;
    }

    public long getSystemAddress() {
        return systemAddress;
    }

    public double getDistanceFromArrivalLs() {
        return distanceFromArrivalLs;
    }

    public boolean isLandable() {
        return landable;
    }

    public String getPlanetClass() {
        return planetClass;
    }

    public String getAtmosphere() {
        return atmosphere;
    }

    public Map<String, Double> getAtmosphereComposition() {
        return atmosphereComposition;
    }

    public String getTerraformState() {
        return terraformState;
    }

    public Double getMassEm() {
        return massEm;
    }

    public Double getSurfaceGravity() {
        return surfaceGravity;
    }

    /** Surface temperature in Kelvin (may be null if not present). */
    public Double getSurfaceTemperature() {
        return surfaceTemperature;
    }

    /** Raw Volcanism string from the journal (may be null/empty). */
    public String getVolcanism() {
        return volcanism;
    }

    public Boolean getWasDiscovered() {
        return wasDiscovered;
    }

    public Boolean getWasMapped() {
        return wasMapped;
    }

    public String getStarType() {
        return starType;
    }

    public List<ParentRef> getParents() {
        return parents;
    }

	public Double getSurfacePressure() {
		return surfacePressure;
	}

	public Double getOrbitalPeriod() {
		return orbitalPeriod;
	}

	/** Journal {@code SemiMajorAxis} in metres, or null if absent. */
	public Double getSemiMajorAxisM() {
		return semiMajorAxisM;
	}

	/** Journal {@code Eccentricity}, or null if absent. */
	public Double getEccentricity() {
		return eccentricity;
	}

	/** Journal {@code OrbitalInclination}, or null if absent. */
	public Double getOrbitalInclination() {
		return orbitalInclination;
	}

	/** Journal {@code Periapsis}, or null if absent. */
	public Double getPeriapsis() {
		return periapsis;
	}

	/** Journal {@code AscendingNode}, or null if absent. */
	public Double getAscendingNode() {
		return ascendingNode;
	}

	/** Journal {@code MeanAnomaly}, or null if absent. */
	public Double getMeanAnomaly() {
		return meanAnomaly;
	}

	/**
	 * @return the wasFootfalled
	 */
	public Boolean getWasFootfalled() {
        return wasFootfalled;
    }

    /** Rings orbiting this body (empty if none in the journal). */
    public List<RingInfo> getRings() {
        return rings;
    }

    /** Raw journal {@code ReserveLevel} for the body's ring system (may be null). */
    public String getReserveLevel() {
        return reserveLevel;
    }

}