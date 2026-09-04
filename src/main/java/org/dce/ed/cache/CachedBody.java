package org.dce.ed.cache;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dce.ed.exobiology.ExobiologyData.BioCandidate;
import org.dce.ed.state.PlanetaryRingBand;

/**
 * Represents one body as stored in the cache.
 */
public class CachedBody {
    public String name;
    public int bodyId;
    public String starSystem;
	public double[] starPos;
	
    public double distanceLs;
    public Double gravityMS;
    public boolean landable;
    public boolean hasBio;
    public boolean hasGeo;
    public boolean highValue;
    /** Typical exploration payout hint when {@link #highValue}; see {@code ValuableBodyExplorationEstimate}. */
    public Long valuableBodyExplorationCredits;

    public String planetClass;
    /** Journal / EDSM terraform state (e.g. Terraformable). */
    public String terraformState;
    /** Earth masses (journal MassEM / EDSM earthMasses). */
    public Double massEm;
    public String atmosphere;
    public String atmoOrType;

    /** Atmosphere composition percent map (e.g., SulphurDioxide -> 12.3). */
    public Map<String, Double> atmosphereComposition;

    public Double surfaceTempK;
    public Double orbitalPeriod;

    /**
     * {@code true} for journal {@code ScanBaryCentre} rows ({@code Parents:[{"Null":N}]} heliocentric orbit), not a
     * landable body. Required so reload uses {@code P_outer} on this row vs mutual period on planet siblings.
     */
    public boolean scanBarycentreRow;

    /**
     * {@code true} when this row was hydrated from EDSM because FSS is complete but the journal has no
     * per-body {@code Scan} events for this visit.
     */
    public boolean edsmFssBackfill;

    /** Journal {@code SemiMajorAxis} in metres. */
    public Double semiMajorAxisM;
    public Double eccentricity;
    public Double orbitalInclination;
    public Double periapsis;
    public Double ascendingNode;
    public Double meanAnomaly;

    /** UTC millis of journal Scan that fixed orbital elements ({@link BodyInfo#setOrbitalEpochMillis}). */
    public Long orbitalEpochMillis;
    
    public String volcanism;

    public String bodyName;
    public String parentStar;
    public int parentStarBodyId;
    /** Journal Parents[0] body id; -1 if unknown. */
    public int immediateParentBodyId = -1;
    /** Journal Parents[] inner-to-outer (e.g. Null:14, Star:1, Null:0). */
    public List<String> journalParentRefs;
    public String starType;
    public String nebula; 
    
    public Double surfacePressure;
    
    // NEW: raw EDSM discovery info
    public String discoveryCommander;

    public Map<String, Integer> bioSampleCountsByDisplayName;

    // NEW: lat/lon positions for each logged sample point (up to 3) per species display name.
    public Map<String, List<BioSamplePoint>> bioSamplePointsByDisplayName;

    /** Parked pins when the player switched away from an incomplete species (shown e.g. purple on the bio map). */
    public Map<String, List<BioSamplePoint>> abandonedBioSamplePointsByDisplayName;

    /** Species display name key for the genus currently being sampled (1/3 or 2/3), if any. */
    public String activeIncompleteBioKey;

    public static class BioSamplePoint {
        public double latitude;
        public double longitude;

        public BioSamplePoint() {
        }

        public BioSamplePoint(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    
    public List<BioCandidate> predictions;
    public int numberOfBioSignals;
    
    // NEW: confirmed genera observed (ScanOrganic / DSS)
    public Set<String> observedGenusPrefixes;   // may be null if none known
    
    // Full "truth" names like "Bacterium Nebulus", "Stratum Tectonicas", etc.
    public Set<String> observedBioDisplayNames;  // may be null

    /** Species that received ScanOrganic Analyse on this body. */
    public Set<String> analysedBioDisplayNames;
	public Boolean wasMapped;
	public Boolean wasDiscovered;
	public Boolean wasFootfalled;

	/** Ring type names for this body (e.g. "Icy pristine"). Stored so cached systems show rings in Nearby. */
	public List<String> ringTypes;

	/** Journal reserve (Pristine, …) for annotating ring lines that lack quality. */
	public String ringReserveHumanized;

	/** Journal / EDSM ring band geometry (metres from host centre). */
	public List<PlanetaryRingBand> planetaryRingBands;

	public Double radius;

	public Double axialTilt;

	public int getNumberOfBioSignals() {
		return numberOfBioSignals;
	}
	public void setNumberOfBioSignals(int i) {
		this.numberOfBioSignals = i;
	}
	public Map<String, Integer> getBioSampleCountsSnapshot() {
	    if (bioSampleCountsByDisplayName == null || bioSampleCountsByDisplayName.isEmpty()) {
	        return Collections.emptyMap();
	    }
	    return new HashMap<>(bioSampleCountsByDisplayName);
	}

	public void setBioSampleCounts(Map<String, Integer> counts) {
	    bioSampleCountsByDisplayName.clear();

	    if (counts == null || counts.isEmpty()) {
	        return;
	    }

	    for (Map.Entry<String, Integer> e : counts.entrySet()) {
	        if (e.getKey() == null || e.getKey().isBlank()) {
	            continue;
	        }
	        int v = (e.getValue() == null) ? 0 : e.getValue().intValue();
	        if (v <= 0) {
	            continue;
	        }
	        bioSampleCountsByDisplayName.put(e.getKey(), Integer.valueOf(Math.min(3, v)));
	    }
	}

}