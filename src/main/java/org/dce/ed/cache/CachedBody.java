package org.dce.ed.cache;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dce.ed.exobiology.ExobiologyData.BioCandidate;
import org.dce.ed.util.SpanshLandmark;

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

    public String planetClass;
    public String atmosphere;
    public String atmoOrType;

    /** Atmosphere composition percent map (e.g., SulphurDioxide -> 12.3). */
    public Map<String, Double> atmosphereComposition;

    public Double surfaceTempK;
    public Double orbitalPeriod;
    
    public String volcanism;

    public String bodyName;
    public String parentStar;
    public int parentStarBodyId;
    public String starType;
    public String nebula; 
    
    public Double surfacePressure;
    
    // NEW: raw EDSM discovery info
    public String discoveryCommander;

    public Map<String, Integer> bioSampleCountsByDisplayName;

    // NEW: lat/lon positions for each logged sample point (up to 3) per species display name.
    public Map<String, List<BioSamplePoint>> bioSamplePointsByDisplayName;

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
	public Boolean wasMapped;
	public Boolean wasDiscovered;
	public Boolean wasFootfalled;

	/** Spansh exobiology landmarks (null = not fetched). First bonus does not apply when non-empty. */
	public List<SpanshLandmark> spanshLandmarks;

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