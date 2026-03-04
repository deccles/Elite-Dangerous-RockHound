package org.dce.ed.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.dce.ed.exobiology.BodyAttributes;
import org.dce.ed.exobiology.ExobiologyData;
import org.dce.ed.exobiology.ExobiologyData.AtmosphereType;
import org.dce.ed.exobiology.ExobiologyData.BioCandidate;
import org.dce.ed.exobiology.ExobiologyData.PlanetType;
import org.dce.ed.util.SpanshLandmark;

/**
 * Pure domain representation of a single stellar body.
 * 
 * NO Swing logic.
 * NO rendering logic.
 * 
 * This class holds:
 *  - Physical attributes
 *  - Biological flags
 *  - Observed genus list (from DSS / ScanOrganic)
 *  - Predicted biological candidates (computed in SystemState)
 */
public class BodyInfo {


	private static String canonBioName(String raw) {
		if (raw == null) {
			return "";
		}
		String s = raw.trim();
		if (s.isEmpty()) {
			return s;
		}

		String[] parts = s.split("\\s+");
		if (parts.length >= 3 && parts[0].equalsIgnoreCase(parts[1])) {
			StringBuilder sb = new StringBuilder(parts[0]);
			for (int i = 2; i < parts.length; i++) {
				sb.append(' ').append(parts[i]);
			}
			return sb.toString();
		}

		return s;
	}
	private static String toLower(String s) {
		return s == null ? "" : s.toLowerCase(Locale.ROOT);
	}
	private String atmoOrType;    // primarily for display
	private String atmosphere;
	private Map<String, Double> atmosphereComposition = Collections.emptyMap();
	Double axialTilt;

	// Odyssey exobiology sample progress per species (1..3).
	// Key should be the display name you show in the Bio table (canonicalized if you do that elsewhere).
	private final Map<String, Integer> bioSampleCountsByDisplayName = new HashMap<>();
	// NEW: sample point locations (lat/lon) recorded for each species display name.
	// We keep up to 3 points per species, matching Odyssey's 3-sample requirement.
	private final Map<String, List<BioSamplePoint>> bioSamplePointsByDisplayName = new HashMap<>();

	// Track the currently-in-progress bio (1/3 or 2/3). When sampling a different species,
	// the in-game behavior discards the incomplete progress from the old species.
	private String activeIncompleteBioKey;

	public static final class BioSamplePoint {
		private final double latitude;
		private final double longitude;

		public BioSamplePoint(double latitude, double longitude) {
			this.latitude = latitude;
			this.longitude = longitude;
		}

		public double getLatitude() {
			return latitude;
		}

		public double getLongitude() {
			return longitude;
		}
	}


	private int bodyId = -1;

	private String discoveryCommander;  // may be null or empty
	private double distanceLs = Double.NaN;
	private Double gravityMS;     // in m/s^2

	private Boolean guardianSystem = false;
	private boolean hasBio;
	private boolean hasGeo;

	private boolean highValue;
	private boolean landable;
	private String name;

	private String nebula;
	private int numberOfBioSignals;

	private java.util.Set<String> observedBioDisplayNames;
	// Observed genera from DSS or ScanOrganic
	private Set<String> observedGenusPrefixes;
	private String parentStar;

	private int parentStarBodyId = -1;

	private String planetClass;

	// Derived biological prediction data
	private List<BioCandidate> predictions;

	Double radius;
	private String shortName;
	double starPos[];
	private String starSystem;
	private String starType;
	private Double surfacePressure;
	
	private Double surfaceTempK;
	private String volcanism;

	private Boolean wasDiscovered = null;

	private Boolean wasFootfalled = null;

	private Boolean wasMapped = null;
	private Double orbitalPeriod;

	/** Spansh exobiology landmarks for this body (null = not fetched). Used to derive first-bonus. */
	private List<SpanshLandmark> spanshLandmarks = null;

	public void addObservedBioDisplayName(String name) {
		if (name == null || name.isEmpty()) {
			return;
		}
		if (getObservedBioDisplayNames() == null) {
			setObservedBioDisplayNames(new java.util.HashSet<>());
		}
		getObservedBioDisplayNames().add(name);
	}
	public void addObservedGenus(String genusPrefix) {
		if (genusPrefix == null || genusPrefix.isEmpty()) {
			return;
		}
		if (getObservedGenusPrefixes() == null) {
			setObservedGenusPrefixes(new HashSet<>());
		}
		getObservedGenusPrefixes().add(toLower(genusPrefix));
	}
	
	public void addObservedGenusPrefix(String genus) {
		if (genus == null || genus.isEmpty()) {
			return;
		}
		if (getObservedGenusPrefixes() == null) {
			setObservedGenusPrefixes(new java.util.HashSet<>());
		}
		getObservedGenusPrefixes().add(genus.toLowerCase(java.util.Locale.ROOT));
	}

	/**
	 * Convert this body into ExobiologyData.BodyAttributes.
	 * Returns null if insufficient data is present.
	 */
	public BodyAttributes buildBodyAttributes() {
		return buildBodyAttributes(null);
	}

	public BodyAttributes buildBodyAttributes(SystemState state) {
		double gravityG = Double.NaN;
		if (getGravityMS() != null && !Double.isNaN(getGravityMS())) {
			gravityG = getGravityMS() / 9.80665;
		}

		if ((getPlanetClass() == null || getPlanetClass().isEmpty())
				&& (getAtmosphere() == null || getAtmosphere().isEmpty())
				&& Double.isNaN(gravityG)) {
			return null; // Not enough info to predict
		}
		String pc = getPlanetClass();
		PlanetType pt = ExobiologyData.parsePlanetType(pc);
		AtmosphereType at = ExobiologyData.parseAtmosphere(getAtmosphere());

		double tempMin = getSurfaceTempK() != null ? getSurfaceTempK() : Double.NaN;
		double tempMax = tempMin;

		
		boolean hasVolc = getVolcanism() != null && !getVolcanism().isEmpty() && !getVolcanism().toLowerCase().startsWith("no volcanism");

		String resolvedParentStar = parentStar;
		String resolvedStarClass = null;
		if (state != null && parentStarBodyId >= 0) {
			BodyInfo star = state.getBodies().get(Integer.valueOf(parentStarBodyId));
			if (star != null) {
				if (resolvedParentStar == null || resolvedParentStar.isEmpty()) {
					resolvedParentStar = star.getBodyName();
				}
				resolvedStarClass = star.getStarType();
			}
		}

		BodyAttributes attr = new BodyAttributes(
				getBodyName(),
				getStarSystem(),
				starPos,
				pt,
				gravityG,
				at,
				tempMin,
				tempMax,
				surfacePressure,
				hasVolc,
				getVolcanism(),
				getAtmosphereComposition(),
				orbitalPeriod,
				distanceLs,
				getGuardianSystem(),
				nebula,
				resolvedParentStar,
				null,
				resolvedStarClass
				);
		//        public BodyAttributes(String bodyName,
		//                PlanetType planetType,
		//                double gravity,
		//                AtmosphereType atmosphere,
		//                double tempKMin,
		//                double tempKMax,
		//                double pressure,
		//                boolean hasVolcanism,
		//                String volcanismType,
		//                Map<String, Double> atmosphereComponents,
		//                Double orbitalPeriod,
		//                Double distance,
		//                Boolean guardian,
		//                String nebula,
		//                String parentStar,
		//                String region,
		//                String starClass) {




		return attr;
	}
	
	public void clearPredictions() {
		if (getPredictions() != null) {
			getPredictions().clear();
		}
	}

	public String getAtmoOrType() {
		return atmoOrType;
	}


	public String getAtmosphere() {
		return atmosphere;
	}

	public Map<String, Double> getAtmosphereComposition() {
		return atmosphereComposition;
	}

	public void setAtmosphereComposition(Map<String, Double> atmosphereComposition) {
		this.atmosphereComposition = (atmosphereComposition == null || atmosphereComposition.isEmpty())
				? Collections.emptyMap()
				: new HashMap<>(atmosphereComposition);
	}

	// ------------------------------------------------------------
	// Accessors
	// ------------------------------------------------------------

	public int getBioSampleCount(String displayName) {
		if (displayName == null) {
			return 0;
		}
		String key = canonBioName(displayName);
		return bioSampleCountsByDisplayName.getOrDefault(key, 0);
	}

	public Map<String, Integer> getBioSampleCountsSnapshot() {
		if (bioSampleCountsByDisplayName.isEmpty()) {
			return Collections.emptyMap();
		}
		return new HashMap<>(bioSampleCountsByDisplayName);
	}

	public int getBodyId() {
		return bodyId;
	}

	public String getBodyName() {
		return name;
	}

	public String getDiscoveryCommander() {
		return discoveryCommander;
	}

	public double getDistanceLs() {
		return distanceLs;
	}

	public Double getGravityMS() {
		return gravityMS;
	}

	public Boolean getGuardianSystem() {
		return guardianSystem;
	}

	public String getNebula() {
		return nebula;
	}

	public Integer getNumberOfBioSignals() {
		return numberOfBioSignals;
	}

	public java.util.Set<String> getObservedBioDisplayNames() {
		return observedBioDisplayNames;
	}

	public Set<String> getObservedGenusPrefixes() {
		return observedGenusPrefixes;
	}

	public String getParentStar() {
		return parentStar;
	}

	public int getParentStarBodyId() {
		return parentStarBodyId;
	}

	public String getPlanetClass() {
		return planetClass;
	}

	public List<ExobiologyData.BioCandidate> getPredictions() {
		return predictions;
	}

	public String getShortName() {
		return shortName;
	}

	public double[] getStarPos() {
		return starPos;
	}

	public String getStarSystem() {
		return starSystem;
	}

	public String getStarType() {
		return starType;
	}

	public Double getSurfacePressure() {
		return surfacePressure;
	}

	public Double getSurfaceTempK() {
		return surfaceTempK;
	}

	public String getVolcanism() {
		return volcanism;
	}

	public Boolean getWasDiscovered() {
		return wasDiscovered;
	}

	public Boolean getWasFootfalled() {
		return wasFootfalled;
	}

	public List<SpanshLandmark> getSpanshLandmarks() {
		return spanshLandmarks;
	}

	public void setSpanshLandmarks(List<SpanshLandmark> spanshLandmarks) {
		this.spanshLandmarks = spanshLandmarks;
	}

	public Boolean getWasMapped() {
		return wasMapped;
	}

	public boolean hasAnyBioSamples() {
		for (Integer v : bioSampleCountsByDisplayName.values()) {
			if (v != null && v.intValue() > 0) {
				return true;
			}
		}
		return false;
	}
	public boolean hasBio() {
		return isHasBio();
	}

	/**
	 * Convenience helper: true if EDSM reports a non-empty discovery.commander
	 * for this body.
	 */
	public boolean hasDiscoveryCommander() {
		return getDiscoveryCommander() != null && !getDiscoveryCommander().isBlank();
	}

	public boolean hasGeo() {
		return isHasGeo();
	}

	public boolean isHasBio() {
		return hasBio;
	}

	public boolean isHasGeo() {
		return hasGeo;
	}

	public boolean isHighValue() {
		return highValue;
	}

	// ------------------------------------------------------------
	// Mutators
	// ------------------------------------------------------------

	public boolean isLandable() {
		return landable;
	}

	/**
	 * Records a new sample for this species.
	 *
	 * ScanType behavior:
	 *  - "Log" typically occurs for each sample taken.
	 *  - "Analyse" indicates completion (3/3).
	 */
	public void recordBioSample(String displayName, String scanType) {
		String key = canonBioName(displayName);

	    if (key.isBlank()) {
	        return;
	    }

	    // Completion event: force to 3/3
	    if (scanType != null) {
	        String st = scanType.trim().toLowerCase(Locale.ROOT);
	        if (st.equals("analyse") || st.equals("analyze")) {
	            bioSampleCountsByDisplayName.put(key, Integer.valueOf(3));
	            observedBioDisplayNames.add(displayName);
	            return;
	        }
	    }

	    Integer cur = bioSampleCountsByDisplayName.get(key);
	    int next = (cur == null ? 0 : cur.intValue()) + 1;

	    if (next > 3) {
	        next = 3;
	    }

	    bioSampleCountsByDisplayName.put(key, Integer.valueOf(next));
	}

	/**
	 * Snapshot of recorded sample points for this body.
	 * Keys are canonicalized display names (same keys used by {@link #recordBioSample(String, String)}).
	 */
	public Map<String, List<BioSamplePoint>> getBioSamplePointsSnapshot() {
		Map<String, List<BioSamplePoint>> out = new HashMap<>();
		for (Map.Entry<String, List<BioSamplePoint>> e : bioSamplePointsByDisplayName.entrySet()) {
			List<BioSamplePoint> pts = e.getValue();
			if (pts == null || pts.isEmpty()) {
				continue;
			}
			out.put(e.getKey(), new ArrayList<>(pts));
		}
		return out;
	}

	/**
	 * Replace all stored sample points (used when loading from cache).
	 */
	public void setBioSamplePoints(Map<String, List<BioSamplePoint>> pointsByDisplayName) {
		bioSamplePointsByDisplayName.clear();
		activeIncompleteBioKey = null;

		if (pointsByDisplayName == null || pointsByDisplayName.isEmpty()) {
			return;
		}

		for (Map.Entry<String, List<BioSamplePoint>> e : pointsByDisplayName.entrySet()) {
			String key = canonBioName(e.getKey());
			List<BioSamplePoint> pts = e.getValue();
			if (pts == null || pts.isEmpty()) {
				continue;
			}
			List<BioSamplePoint> copy = new ArrayList<>(pts);
			if (copy.size() > 3) {
				copy = copy.subList(0, 3);
			}
			bioSamplePointsByDisplayName.put(key, copy);

			int cnt = bioSampleCountsByDisplayName.getOrDefault(key, 0);
			if (cnt > 0 && cnt < 3) {
				activeIncompleteBioKey = key;
			}
		}
	}

	/**
	 * Record the lat/lon of the current sample point for the given species display name.
	 *
	 * @param displayName species display name (genus + species, or genus)
	 * @param scanType    ScanOrganic scan type ("Log" increments; "Analyse" completes)
	 * @param latitude    degrees
	 * @param longitude   degrees
	 */
	public void recordBioSamplePoint(String displayName, String scanType, double latitude, double longitude) {
		if (displayName == null || displayName.isBlank()) {
			return;
		}

		String key = canonBioName(displayName);

		// If the player begins sampling a different species while incomplete, discard the old incomplete samples.
		int currentCount = bioSampleCountsByDisplayName.getOrDefault(key, 0);
		if (currentCount < 3) {
			if (activeIncompleteBioKey != null && !activeIncompleteBioKey.equals(key)) {
				bioSamplePointsByDisplayName.remove(activeIncompleteBioKey);
				// also clear the old sample count if it wasn't complete
				int oldCnt = bioSampleCountsByDisplayName.getOrDefault(activeIncompleteBioKey, 0);
				if (oldCnt > 0 && oldCnt < 3) {
					bioSampleCountsByDisplayName.remove(activeIncompleteBioKey);
				}
			}
			activeIncompleteBioKey = key;
		}

		List<BioSamplePoint> pts = bioSamplePointsByDisplayName.computeIfAbsent(key, k -> new ArrayList<>());
		int max = 3;

		String st = scanType == null ? null : scanType.toLowerCase(Locale.ROOT);
		if ("analyse".equals(st) || "analyze".equals(st)) {
			// When analyzed, the sample is complete; keep whatever points we already captured.
			activeIncompleteBioKey = null;
			return;
		}

		// For a "Log" point, append if we have room.
		if (pts.size() < max) {
			pts.add(new BioSamplePoint(latitude, longitude));
		} else {
			// If somehow we get extra, overwrite the last one.
			pts.set(max - 1, new BioSamplePoint(latitude, longitude));
		}
	}


	public void setAtmoOrType(String atmoOrType) {
		this.atmoOrType = atmoOrType;
	}

	public void setAtmosphere(String atmosphere) {
		this.atmosphere = atmosphere;
	}

	public void setAxialTilt(Double axialTilt) {
		this.axialTilt = axialTilt;
	}

	public void setBioSampleCounts(Map<String, Integer> counts) {
		bioSampleCountsByDisplayName.clear();

		if (counts == null || counts.isEmpty()) {
			return;
		}

		for (Map.Entry<String, Integer> e : counts.entrySet()) {
			String key = e.getKey();
			if (key == null || key.isBlank()) {
				continue;
			}

			Integer vObj = e.getValue();
			if (vObj == null) {
				continue;
			}

			int v = vObj.intValue();
			if (v <= 0) {
				continue;
			}

			// Clamp to 1..3
			if (v > 3) {
				v = 3;
			}

			bioSampleCountsByDisplayName.put(key, Integer.valueOf(v));
		}
	}

	public void setBodyId(int bodyId) {
		this.bodyId = bodyId;
	}

	public void setBodyName(String name) {
		this.name = name;
	}

	public void setBodyShortName(String shortName) {
		this.shortName = shortName;
	}

	public void setDiscoveryCommander(String discoveryCommander) {
		this.discoveryCommander = discoveryCommander;
	}

	public void setDistanceLs(double distanceLs) {
		this.distanceLs = distanceLs;
	}

	public void setGravityMS(Double gravityMS) {
		this.gravityMS = gravityMS;
	}

	public void setGuardianSystem(Boolean guardianSystem) {
		this.guardianSystem = guardianSystem;
	}

	public void setHasBio(boolean hasBio) {
		this.hasBio = hasBio;
	}

	public void setHasGeo(boolean hasGeo) {
		this.hasGeo = hasGeo;
	}

	public void setHighValue(boolean highValue) {
		this.highValue = highValue;
	}
	public void setLandable(boolean landable) {
		this.landable = landable;
	}

	public void setNebula(String nebula) {
		this.nebula = nebula;
	}

	// ------------------------------------------------------------
	// Genus observation handling
	// ------------------------------------------------------------

	public void setNumberOfBioSignals(int num) {
		numberOfBioSignals = num;
	}

	// ------------------------------------------------------------
	// Build exobiology prediction attributes
	// ------------------------------------------------------------

	public void setObservedBioDisplayNames(java.util.Set<String> observedBioDisplayNames) {
		this.observedBioDisplayNames = observedBioDisplayNames;
	}

	public void setObservedGenusPrefixes(Set<String> observedGenusPrefixes) {
		this.observedGenusPrefixes = observedGenusPrefixes;
	}

	public void setParentStar(String parentStar) {
		this.parentStar = parentStar;
	}

	public void setParentStarBodyId(int parentStarBodyId) {
		this.parentStarBodyId = parentStarBodyId;
	}


	// ------------------------------------------------------------
	// Utils
	// ------------------------------------------------------------

	public void setPlanetClass(String planetClass) {
		this.planetClass = planetClass;
	}

	public void setPredictions(ArrayList<BioCandidate> predictions) {
		this.predictions = predictions;		
	}

	public void setPredictions(List<ExobiologyData.BioCandidate> predictions) {
		this.predictions = predictions;
	}

	public void setRadius(Double radius) {
		this.radius = radius;
	}
	public Double getRadius() {
	    return radius;
	}

	public void setStarPos(double[] starPos) {
		this.starPos = starPos;
	}

	public void setStarSystem(String starSystem) {
		this.starSystem = starSystem;
	}

	public void setStarType(String starType) {
		this.starType = starType;
	}

	public void setSurfacePressure(Double surfacePressure) {
		this.surfacePressure = surfacePressure;
	}

	public void setSurfaceTempK(Double surfaceTempK) {
		this.surfaceTempK = surfaceTempK;
	}

	public void setVolcanism(String volcanism) {
		this.volcanism = volcanism;
	}

	public void setWasDiscovered(Boolean wasDiscovered) {
		this.wasDiscovered = wasDiscovered;
	}
	public void setWasFootfalled(Boolean waasFootfalled) {
		this.wasFootfalled = waasFootfalled;
	}

	public void setWasMapped(Boolean wasMapped) {
		this.wasMapped = wasMapped;
	}
	public void setOrbitalPeriod(Double orbitalPeriod) {
		this.orbitalPeriod = orbitalPeriod;
	}
	public Double getOrbitalPeriod() {
		return orbitalPeriod;
	}
}
