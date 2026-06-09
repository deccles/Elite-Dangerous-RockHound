package org.dce.ed.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.dce.ed.BioColonyDistance;
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

	private static String canonBioGenusKey(String raw) {
		String key = canonBioName(raw);
		if (key.isEmpty()) {
			return key;
		}
		int idx = key.indexOf(' ');
		return (idx < 0 ? key : key.substring(0, idx)).toLowerCase(Locale.ROOT);
	}

	private void removeAbandonedBioSamplePointsForGenus(String displayName) {
		String genus = canonBioGenusKey(displayName);
		if (genus.isEmpty() || abandonedBioSamplePointsByDisplayName.isEmpty()) {
			return;
		}
		abandonedBioSamplePointsByDisplayName.entrySet()
				.removeIf(e -> e.getKey() != null && genus.equals(canonBioGenusKey(e.getKey())));
	}

	/**
	 * When the journal sends a genus-only display name (e.g. "Osseus" with no species),
	 * resolve to an existing incomplete same-genus key if one exists, so we add the
	 * sample to that row instead of creating a new key and wiping previous points.
	 */
	private String resolveBioKeyForSample(String displayName) {
		String key = canonBioName(displayName);
		if (key.isEmpty()) {
			return key;
		}
		// Full genus+species: use as-is.
		if (key.contains(" ")) {
			return key;
		}
		// Genus-only: look for an existing incomplete key with this genus.
		String genusPrefix = key + " ";
		String best = null;
		int bestCount = -1;
		for (String k : bioSamplePointsByDisplayName.keySet()) {
			if (k == null || (!k.equals(key) && !k.startsWith(genusPrefix))) {
				continue;
			}
			int cnt = bioSampleCountsByDisplayName.getOrDefault(k, 0);
			if (cnt >= 3) {
				continue;
			}
			List<BioSamplePoint> pts = bioSamplePointsByDisplayName.get(k);
			int n = (pts == null) ? 0 : pts.size();
			int total = Math.max(cnt, n);
			if (total > bestCount) {
				bestCount = total;
				best = k;
			}
		}
		if (best != null) {
			return best;
		}
		for (Map.Entry<String, Integer> e : bioSampleCountsByDisplayName.entrySet()) {
			String k = e.getKey();
			if (k == null || (!k.equals(key) && !k.startsWith(genusPrefix))) {
				continue;
			}
			int cnt = e.getValue() == null ? 0 : e.getValue().intValue();
			if (cnt >= 3) {
				continue;
			}
			if (cnt > bestCount) {
				bestCount = cnt;
				best = k;
			}
		}
		return best != null ? best : key;
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

	/**
	 * Sample pins for a species the player left incomplete by switching to another genus (game resets progress).
	 * Retained for map display (e.g. purple) while a different species is active.
	 */
	private final Map<String, List<BioSamplePoint>> abandonedBioSamplePointsByDisplayName = new HashMap<>();

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
	/** Cached exploration total for high-value bodies; UI recomputes via {@link org.dce.ed.util.ExplorationBodyCredits} when possible. */
	private Long valuableBodyExplorationCredits;
	private boolean landable;
	private String name;

	private String nebula;
	private int numberOfBioSignals;

	private java.util.Set<String> observedBioDisplayNames;
	/** Species that received ScanOrganic {@code Analyse} on this body (true 3/3 completion). */
	private Set<String> analysedBioDisplayNames;
	// Observed genera from DSS or ScanOrganic
	private Set<String> observedGenusPrefixes;
	private String parentStar;

	private int parentStarBodyId = -1;

	/** Immediate orbital parent {@link org.dce.ed.logreader.event.ScanEvent.Parents}[0] body id; -1 if unknown. */
	private int immediateParentBodyId = -1;

	/**
	 * Full journal {@code Parents[]} chain, innermost first (e.g. {@code Null:14}, {@code Star:1}, {@code Null:0}).
	 */
	private List<String> journalParentRefs;

	private String planetClass;

	/** Journal {@code TerraformState} (e.g. Terraformable); used for exploration k and WW terraform tier. */
	private String terraformState;

	/** Journal {@code MassEM} (Earth masses); used for exploration credit estimates. */
	private Double massEm;

	// Derived biological prediction data
	private List<BioCandidate> predictions;

	/** Ring summary lines for the system table (from journal scan or EDSM). */
	private List<String> ringSummaryLines;

	/** Humanized {@code ReserveLevel} from journal (e.g. Pristine); used to annotate EDSM ring lines that omit quality. */
	private String ringReserveHumanized;

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

	/** Journal {@code SemiMajorAxis} (m); orbit around parent. */
	private Double semiMajorAxisM;
	private Double eccentricity;
	private Double orbitalInclination;
	private Double periapsis;
	private Double ascendingNode;
	private Double meanAnomaly;

	/**
	 * Real time ({@code System.currentTimeMillis()} / UTC) of the journal {@code Scan} that supplied this body's
	 * orbital elements; used with {@link #getOrbitalPeriod()} and {@link #getMeanAnomaly()} to evolve mean anomaly.
	 */
	private Long orbitalEpochMillis;

	/**
	 * {@code ScanBaryCentre} row keyed by journal {@code BodyID} (same id as {@code Parents:[{"Null":N}]}).
	 * Supplies heliocentric elements for planet-binary barycentre motion; not a map-visible body.
	 */
	private boolean scanBarycentreRow;

	/** Spansh exobiology landmarks for this body (null = not fetched). Used to derive first-bonus. */
	private List<SpanshLandmark> spanshLandmarks = null;

	/** True when Spansh has signals for this body but none are Biological; body should be excluded from exobiology lists. */
	private Boolean spanshExcludeFromExobiology = null;

	public void addObservedBioDisplayName(String name) {
		if (name == null || name.isEmpty()) {
			return;
		}
		if (getObservedBioDisplayNames() == null) {
			setObservedBioDisplayNames(new java.util.HashSet<>());
		}
		getObservedBioDisplayNames().add(name);
	}

	private void markBioSpeciesAnalysed(String canonicalDisplayName) {
		if (canonicalDisplayName == null || canonicalDisplayName.isBlank()) {
			return;
		}
		if (analysedBioDisplayNames == null) {
			analysedBioDisplayNames = new HashSet<>();
		}
		analysedBioDisplayNames.add(canonicalDisplayName);
	}

	public boolean isBioSpeciesAnalysed(String displayName) {
		if (displayName == null || displayName.isBlank() || analysedBioDisplayNames == null) {
			return false;
		}
		String key = canonBioName(displayName);
		if (analysedBioDisplayNames.contains(key)) {
			return true;
		}
		for (String analysed : analysedBioDisplayNames) {
			if (analysed != null && canonBioName(analysed).equals(key)) {
				return true;
			}
		}
		return false;
	}

	public Set<String> getAnalysedBioDisplayNames() {
		return analysedBioDisplayNames;
	}

	public void setAnalysedBioDisplayNames(Set<String> names) {
		if (names == null || names.isEmpty()) {
			analysedBioDisplayNames = null;
			return;
		}
		analysedBioDisplayNames = new HashSet<>();
		for (String n : names) {
			if (n == null || n.isBlank()) {
				continue;
			}
			analysedBioDisplayNames.add(canonBioName(n));
		}
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

	public int getImmediateParentBodyId() {
		return immediateParentBodyId;
	}

	public void setImmediateParentBodyId(int immediateParentBodyId) {
		this.immediateParentBodyId = immediateParentBodyId;
	}

	public List<String> getJournalParentRefs() {
		return journalParentRefs != null ? journalParentRefs : Collections.emptyList();
	}

	public void setJournalParentRefs(List<String> journalParentRefs) {
		this.journalParentRefs = journalParentRefs == null || journalParentRefs.isEmpty()
				? null
				: List.copyOf(journalParentRefs);
	}

	public String getPlanetClass() {
		return planetClass;
	}

	public String getTerraformState() {
		return terraformState;
	}

	public Double getMassEm() {
		return massEm;
	}

	public List<ExobiologyData.BioCandidate> getPredictions() {
		return predictions;
	}

	/**
	 * Display lines for rings (composition and reserve), e.g. from journal or EDSM.
	 */
	public List<String> getRingSummaryLines() {
		return ringSummaryLines != null ? ringSummaryLines : Collections.emptyList();
	}

	public void setRingSummaryLines(List<String> lines) {
		if (lines == null || lines.isEmpty()) {
			this.ringSummaryLines = null;
		} else {
			this.ringSummaryLines = new ArrayList<>(lines);
		}
	}

	public String getRingReserveHumanized() {
		return ringReserveHumanized;
	}

	public void setRingReserveHumanized(String ringReserveHumanized) {
		this.ringReserveHumanized = (ringReserveHumanized == null || ringReserveHumanized.isBlank())
				? null
				: ringReserveHumanized.trim();
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

	public Boolean getSpanshExcludeFromExobiology() {
		return spanshExcludeFromExobiology;
	}
	public void setSpanshExcludeFromExobiology(Boolean spanshExcludeFromExobiology) {
		this.spanshExcludeFromExobiology = spanshExcludeFromExobiology;
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
	 * Whether the system-tab bio column would show the leaf icon (FSS/journal bio, not Spansh-excluded).
	 */
	public boolean showsExobiologyLeafIndicator() {
		if (!hasBio()) {
			return false;
		}
		if (!Boolean.TRUE.equals(getSpanshExcludeFromExobiology())) {
			return true;
		}
		Integer sig = getNumberOfBioSignals();
		if (sig != null && sig.intValue() > 0) {
			return true;
		}
		if (getObservedBioDisplayNames() != null && !getObservedBioDisplayNames().isEmpty()) {
			return true;
		}
		return getObservedGenusPrefixes() != null && !getObservedGenusPrefixes().isEmpty();
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

	public Long getValuableBodyExplorationCredits() {
		return valuableBodyExplorationCredits;
	}

	/**
	 * True for planets/moons that carry a {@link #planetClass} from scan or EDSM.
	 * Stars do not — stellar ring belts are not mineable planetary rings, so we hide ring summaries for them.
	 */
	public boolean isPlanetaryBodyForRingDisplay() {
		return planetClass != null && !planetClass.trim().isEmpty();
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
		String key = resolveBioKeyForSample(displayName);

	    if (key.isBlank()) {
	        return;
	    }

	    // Completion event: force to 3/3
	    if (scanType != null) {
	        String st = scanType.trim().toLowerCase(Locale.ROOT);
	        if (st.equals("analyse") || st.equals("analyze")) {
	            bioSampleCountsByDisplayName.put(key, Integer.valueOf(3));
	            addObservedBioDisplayName(displayName);
	            markBioSpeciesAnalysed(key);
	            if (activeIncompleteBioKey != null
	            		&& canonBioGenusKey(activeIncompleteBioKey).equals(canonBioGenusKey(key))) {
	            	activeIncompleteBioKey = null;
	            }
	            removeAbandonedBioSamplePointsForGenus(key);
	            return;
	        }
	    }

	    Integer cur = bioSampleCountsByDisplayName.get(key);
	    int next = (cur == null ? 0 : cur.intValue()) + 1;

	    if (next > 3) {
	        next = 3;
	    }

	    parkPreviousIncompleteIfSwitchingTo(key);

	    bioSampleCountsByDisplayName.put(key, Integer.valueOf(next));

	    if (next < 3) {
	    	activeIncompleteBioKey = key;
	    }
	}

	/**
	 * Elite allows only one incomplete genus at a time. When switching, park sample pins and clear the old count.
	 */
	private void parkPreviousIncompleteIfSwitchingTo(String newKey) {
		if (newKey == null || newKey.isBlank()) {
			return;
		}
		if (activeIncompleteBioKey == null || activeIncompleteBioKey.equals(newKey)) {
			return;
		}
		parkAbandonedSpecies(activeIncompleteBioKey);
		activeIncompleteBioKey = newKey;
	}

	private void parkAbandonedSpecies(String key) {
		if (key == null || key.isBlank()) {
			return;
		}
		List<BioSamplePoint> parked = bioSamplePointsByDisplayName.remove(key);
		if (parked != null && !parked.isEmpty()) {
			abandonedBioSamplePointsByDisplayName.put(key, new ArrayList<>(parked));
		}
		int oldCnt = bioSampleCountsByDisplayName.getOrDefault(key, 0);
		if (oldCnt > 0 && oldCnt < 3) {
			bioSampleCountsByDisplayName.remove(key);
		}
	}

	/**
	 * After cache load or journal replay without lat/lon, partial counts for non-active species are stale:
	 * park their pins (purple) and clear counts so the table matches in-game genus switching.
	 */
	public void reconcileStalePartialBioState() {
		List<String> partial = new ArrayList<>();
		for (Map.Entry<String, Integer> e : bioSampleCountsByDisplayName.entrySet()) {
			String key = e.getKey();
			if (key == null || key.isBlank() || isBioSpeciesAnalysed(key)) {
				continue;
			}
			int cnt = e.getValue() == null ? 0 : e.getValue().intValue();
			if (cnt > 0 && cnt < 3) {
				partial.add(key);
			}
		}
		if (partial.isEmpty()) {
			if (activeIncompleteBioKey != null) {
				int c = bioSampleCountsByDisplayName.getOrDefault(activeIncompleteBioKey, 0);
				if (c <= 0 || c >= 3 || isBioSpeciesAnalysed(activeIncompleteBioKey)) {
					activeIncompleteBioKey = null;
				}
			}
			return;
		}
		if (partial.size() > 1 && activeIncompleteBioKey == null) {
			for (String key : partial) {
				parkAbandonedSpecies(key);
			}
			return;
		}
		if (partial.size() == 1 && activeIncompleteBioKey == null) {
			activeIncompleteBioKey = partial.get(0);
			return;
		}
		String active = activeIncompleteBioKey;
		for (String key : partial) {
			if (active != null && active.equals(key)) {
				continue;
			}
			parkAbandonedSpecies(key);
		}
	}

	/**
	 * Correct cached counts that reached 3/3 without a journal {@code Analyse} (replay/cache inflation).
	 * Three map pins without Analyse are kept — the player may still need to analyse at Vista.
	 */
	public void sanitizeInflatedBioSampleCounts() {
		for (String key : new ArrayList<>(bioSampleCountsByDisplayName.keySet())) {
			if (key == null || key.isBlank() || isBioSpeciesAnalysed(key)) {
				continue;
			}
			int count = bioSampleCountsByDisplayName.getOrDefault(key, 0);
			if (count < 3) {
				continue;
			}
			List<BioSamplePoint> pts = bioSamplePointsByDisplayName.get(key);
			int pinCount = pts == null ? 0 : pts.size();
			if (pinCount >= 3) {
				continue;
			}
			int corrected;
			if (pinCount > 0) {
				corrected = Math.min(2, Math.max(pinCount, Math.min(count, pinCount + 1)));
			} else {
				corrected = 2;
			}
			bioSampleCountsByDisplayName.put(key, Integer.valueOf(corrected));
		}
	}

	/**
	 * Bodies completed before {@link #analysedBioDisplayNames} existed: three recorded pins implies Analyse done.
	 */
	public void inferLegacyAnalysedFromFullPins() {
		for (String key : new ArrayList<>(bioSampleCountsByDisplayName.keySet())) {
			if (key == null || key.isBlank() || isBioSpeciesAnalysed(key)) {
				continue;
			}
			if (bioSampleCountsByDisplayName.getOrDefault(key, 0) < 3) {
				continue;
			}
			List<BioSamplePoint> pts = bioSamplePointsByDisplayName.get(key);
			if (pts != null && pts.size() >= 3) {
				markBioSpeciesAnalysed(key);
			}
		}
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
	 * Snapshot of sample pins left behind when switching to another incomplete species (still on the map in purple).
	 */
	public Map<String, List<BioSamplePoint>> getAbandonedBioSamplePointsSnapshot() {
		Map<String, List<BioSamplePoint>> out = new HashMap<>();
		for (Map.Entry<String, List<BioSamplePoint>> e : abandonedBioSamplePointsByDisplayName.entrySet()) {
			List<BioSamplePoint> pts = e.getValue();
			if (pts == null || pts.isEmpty()) {
				continue;
			}
			out.put(e.getKey(), new ArrayList<>(pts));
		}
		return out;
	}

	/** Canonical key of the species currently being sampled (1/3 or 2/3), or null. */
	public String getActiveIncompleteBioKey() {
		return activeIncompleteBioKey;
	}

	public void setActiveIncompleteBioKey(String key) {
		activeIncompleteBioKey = (key == null || key.isBlank()) ? null : canonBioName(key);
	}

	public static final class BioScanReplayEntry {
		public final String displayName;
		public final String scanType;

		public BioScanReplayEntry(String displayName, String scanType) {
			this.displayName = displayName;
			this.scanType = scanType;
		}

		boolean isAnalyse() {
			if (scanType == null) {
				return false;
			}
			String st = scanType.trim().toLowerCase(Locale.ROOT);
			return "analyse".equals(st) || "analyze".equals(st);
		}
	}

	/**
	 * Re-apply genus-switch parking from journal {@code ScanOrganic} order (no count changes).
	 * Fixes stale partial counts when replay did not have commander lat/lon for sample points.
	 */
	public void replayGenusSwitchParkingFromJournal(List<BioScanReplayEntry> entries) {
		if (entries == null || entries.isEmpty()) {
			return;
		}
		String simActive = null;
		for (BioScanReplayEntry entry : entries) {
			if (entry == null || entry.displayName == null || entry.displayName.isBlank()) {
				continue;
			}
			String key = resolveBioKeyForSample(entry.displayName);
			if (key.isBlank()) {
				continue;
			}
			if (entry.isAnalyse()) {
				if (simActive != null
						&& canonBioGenusKey(simActive).equals(canonBioGenusKey(key))) {
					simActive = null;
				}
				continue;
			}
			if (simActive != null && !simActive.equals(key)) {
				parkAbandonedSpecies(simActive);
			}
			simActive = key;
		}
		if (simActive != null) {
			int c = bioSampleCountsByDisplayName.getOrDefault(simActive, 0);
			if (c > 0 && c < 3 && !isBioSpeciesAnalysed(simActive)) {
				activeIncompleteBioKey = simActive;
			} else {
				activeIncompleteBioKey = null;
			}
		} else {
			activeIncompleteBioKey = null;
		}
	}

	/**
	 * Drop parked (purple) pins and the active-incomplete marker when nothing is mid-scan (no 1/3 or 2/3).
	 * Prevents stale purple rays when the table shows only complete or untouched species.
	 */
	public void clearParkedBioSampleStateWhenIdle() {
		abandonedBioSamplePointsByDisplayName.clear();
		activeIncompleteBioKey = null;
	}

	/**
	 * Replace abandoned pins (used when loading from cache).
	 */
	public void setAbandonedBioSamplePoints(Map<String, List<BioSamplePoint>> pointsByDisplayName) {
		abandonedBioSamplePointsByDisplayName.clear();
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
				copy = new ArrayList<>(copy.subList(0, 3));
			}
			abandonedBioSamplePointsByDisplayName.put(key, copy);
		}
	}

	/**
	 * Replace all stored sample points (used when loading from cache).
	 */
	public void setBioSamplePoints(Map<String, List<BioSamplePoint>> pointsByDisplayName) {
		bioSamplePointsByDisplayName.clear();

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
			parkPreviousIncompleteIfSwitchingTo(key);
			activeIncompleteBioKey = key;
		}

		List<BioSamplePoint> pts = bioSamplePointsByDisplayName.computeIfAbsent(key, k -> new ArrayList<>());
		int max = 3;

		String st = scanType == null ? null : scanType.toLowerCase(Locale.ROOT);
		if ("analyse".equals(st) || "analyze".equals(st)) {
			// When analyzed, the sample is complete; keep whatever points we already captured.
			activeIncompleteBioKey = null;
			removeAbandonedBioSamplePointsForGenus(key);
			return;
		}

		int ccr = BioColonyDistance.metersForBio(key);
		if (ccr > 0
				&& isWithinColonyRangeOfAnyRecordedPoint(key, latitude, longitude, pts, ccr)) {
			// Same colony as an existing or parked pin — skip duplicate map pin only.
			// Do not undo the journal count: Status.json lat/lon can lag ScanOrganic, which falsely
			// looked like a duplicate colony and made the ExoBio table stop registering samples.
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

	private boolean isWithinColonyRangeOfAnyRecordedPoint(
			String key,
			double lat,
			double lon,
			List<BioSamplePoint> activePts,
			int ccrMeters) {
		Double radObj = radius;
		if (radObj == null || ccrMeters <= 0) {
			return false;
		}
		double rad = radObj.doubleValue();
		if (!(rad > 0.0) || Double.isNaN(rad) || Double.isInfinite(rad)) {
			return false;
		}
		if (activePts != null) {
			for (BioSamplePoint p : activePts) {
				if (p == null) {
					continue;
				}
				double d = BioColonyDistance.greatCircleMeters(
						lat, lon, p.getLatitude(), p.getLongitude(), rad);
				if (d < ccrMeters) {
					return true;
				}
			}
		}
		List<BioSamplePoint> ab = abandonedBioSamplePointsByDisplayName.get(key);
		if (ab != null) {
			for (BioSamplePoint p : ab) {
				if (p == null) {
					continue;
				}
				double d = BioColonyDistance.greatCircleMeters(
						lat, lon, p.getLatitude(), p.getLongitude(), rad);
				if (d < ccrMeters) {
					return true;
				}
			}
		}
		return false;
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

	public void setValuableBodyExplorationCredits(Long valuableBodyExplorationCredits) {
		this.valuableBodyExplorationCredits = valuableBodyExplorationCredits;
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

	public void setTerraformState(String terraformState) {
		this.terraformState = terraformState;
	}

	public void setMassEm(Double massEm) {
		this.massEm = massEm;
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

	public void setSemiMajorAxisM(Double semiMajorAxisM) {
		this.semiMajorAxisM = semiMajorAxisM;
	}

	public Double getSemiMajorAxisM() {
		return semiMajorAxisM;
	}

	public void setEccentricity(Double eccentricity) {
		this.eccentricity = eccentricity;
	}

	public Double getEccentricity() {
		return eccentricity;
	}

	public void setOrbitalInclination(Double orbitalInclination) {
		this.orbitalInclination = orbitalInclination;
	}

	public Double getOrbitalInclination() {
		return orbitalInclination;
	}

	public void setPeriapsis(Double periapsis) {
		this.periapsis = periapsis;
	}

	public Double getPeriapsis() {
		return periapsis;
	}

	public void setAscendingNode(Double ascendingNode) {
		this.ascendingNode = ascendingNode;
	}

	public Double getAscendingNode() {
		return ascendingNode;
	}

	public void setMeanAnomaly(Double meanAnomaly) {
		this.meanAnomaly = meanAnomaly;
	}

	public Double getMeanAnomaly() {
		return meanAnomaly;
	}

	public void setOrbitalEpochMillis(Long orbitalEpochMillis) {
		this.orbitalEpochMillis = orbitalEpochMillis;
	}

	public Long getOrbitalEpochMillis() {
		return orbitalEpochMillis;
	}

	public boolean isScanBarycentreRow() {
		return scanBarycentreRow;
	}

	public void setScanBarycentreRow(boolean scanBarycentreRow) {
		this.scanBarycentreRow = scanBarycentreRow;
	}
}
