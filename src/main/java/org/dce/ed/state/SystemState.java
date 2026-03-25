package org.dce.ed.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dce.ed.exobiology.BodyAttributes;
import org.dce.ed.exobiology.ExobiologyData;

/**
 * Pure domain model describing a star system and all bodies within it.
 * 
 * This class has ZERO Swing dependencies and no GUI logic.
 * It is the authoritative representation of system state.
 */
public class SystemState {

    private String systemName;
    private long systemAddress;
	private double[] starPos;
	
    /**
     * Exobiology running total (expected credits, unsold).
     *
     * This is a global "session across systems" number, persisted via the system cache
     * so the toolbar survives tool restarts and reboots.
     */
    private Long exobiologyCreditsTotalUnsold;

    private boolean visitedByMe;
    /**
     * Total number of bodies reported by EDSM for this system (may be null if unknown).
     */
    private Integer edsmBodyCount;
    
    
    private Integer totalBodies;
    private Integer nonBodyCount;
    private Double fssProgress;
    /** Last known docked state from journal/status events. */
    private volatile boolean docked;

    // null = unknown, true = yes, false = known not complete (if you want that later)
    private Boolean allBodiesFound;
    
    /** Genera we know are present on this body (lower-cased, e.g. "bacterium", "stratum"). */
    private java.util.Set<String> observedGenusPrefixes;

    /** Human-readable names for actually observed biology, e.g. "Bacterium Nebulus". */
    private java.util.Set<String> observedBioDisplayNames;
    
    // Body ID → BodyInfo
    private final Map<Integer, BodyInfo> bodies = new HashMap<>();


    public SystemState() {
    }

    public void clear() {
        systemName = null;
        systemAddress = 0L;
        totalBodies = null;
        nonBodyCount = null;
        fssProgress = null;
        allBodiesFound = null;
        edsmBodyCount = null;
        docked = false;
        
        bodies.clear();
    }

    public Long getExobiologyCreditsTotalUnsold() {
        return exobiologyCreditsTotalUnsold;
    }

    public void setExobiologyCreditsTotalUnsold(Long exobiologyCreditsTotalUnsold) {
        this.exobiologyCreditsTotalUnsold = exobiologyCreditsTotalUnsold;
    }

    // ------------------------------------------------------------
    // Basic system metadata
    // ------------------------------------------------------------

    public String getSystemName() {
        return systemName;
    }

    public long getSystemAddress() {
        return systemAddress;
    }

    public Integer getTotalBodies() {
        return totalBodies;
    }

    public Integer getNonBodyCount() {
        return nonBodyCount;
    }

    public Double getFssProgress() {
        return fssProgress;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public void setSystemAddress(long systemAddress) {
        this.systemAddress = systemAddress;
    }

    public void setTotalBodies(Integer totalBodies) {
        this.totalBodies = totalBodies;
    }

    public void setNonBodyCount(Integer nonBodyCount) {
        this.nonBodyCount = nonBodyCount;
    }

    public void setFssProgress(Double fssProgress) {
        this.fssProgress = fssProgress;
    }

    public boolean isDocked() {
        return docked;
    }

    public void setDocked(boolean docked) {
        this.docked = docked;
    }

    // ------------------------------------------------------------
    // Body access
    // ------------------------------------------------------------

    public Map<Integer, BodyInfo> getBodies() {
        return bodies;
    }

    public BodyInfo getOrCreateBody(int bodyId) {
        BodyInfo info = bodies.computeIfAbsent(bodyId, id -> new BodyInfo());
        
        if (info.getStarPos() == null && getStarPos() != null) {
            info.setStarPos(getStarPos());
        }
        
        return info;
    }

    // ------------------------------------------------------------
    // Predictions + evaluation logic lives HERE, not in the GUI.
    // ------------------------------------------------------------

    public java.util.Set<String> getObservedGenusPrefixes() {
        return observedGenusPrefixes;
    }

    public void setObservedGenusPrefixes(java.util.Set<String> observedGenusPrefixes) {
        this.observedGenusPrefixes = observedGenusPrefixes;
    }

    public void addObservedGenusPrefix(String genusPrefix) {
        if (genusPrefix == null || genusPrefix.isEmpty()) {
            return;
        }
        if (observedGenusPrefixes == null) {
            observedGenusPrefixes = new java.util.HashSet<>();
        }
        observedGenusPrefixes.add(genusPrefix.toLowerCase(java.util.Locale.ROOT));
    }

    public java.util.Set<String> getObservedBioDisplayNames() {
        return observedBioDisplayNames;
    }

    public void setObservedBioDisplayNames(java.util.Set<String> observedBioDisplayNames) {
        this.observedBioDisplayNames = observedBioDisplayNames;
    }

    public void addObservedBioDisplayName(String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            return;
        }
        if (observedBioDisplayNames == null) {
            observedBioDisplayNames = new java.util.HashSet<>();
        }
        observedBioDisplayNames.add(displayName);
    }

    
    /**
     * For each body, compute or update prediction lists based on ExobiologyData.
     */
    public void recomputePredictions() {
        for (BodyInfo b : bodies.values()) {
            if (!b.hasBio()) {
                b.clearPredictions();
                continue;
            }

            BodyAttributes attrs = b.buildBodyAttributes();
            if (attrs == null) {
                b.clearPredictions();
                continue;
            }

            List<ExobiologyData.BioCandidate> preds = ExobiologyData.predict(attrs);
            if (preds == null) {
                preds = new ArrayList<>();
            }

            // Filter by observed genus prefixes (from DSS or ScanOrganic)
            if (b.getObservedGenusPrefixes() != null && !b.getObservedGenusPrefixes().isEmpty()) {
                List<ExobiologyData.BioCandidate> filtered = new ArrayList<>();
                for (ExobiologyData.BioCandidate cand : preds) {
                    String nameLower = toLower(cand.getDisplayName());
                    for (String genus : b.getObservedGenusPrefixes()) {
                        if (nameLower.startsWith(genus + " ") || nameLower.equals(genus)) {
                            filtered.add(cand);
                            break;
                        }
                    }
                }
                if (!filtered.isEmpty()) {
                    preds = filtered;
                }
            }

            b.setPredictions(preds);
        }
    }
    
    public String computeShortName(String starSystem, String name) {
        if (name == null) {
            return "";
        }

        String sys = starSystem;
        if (sys == null || sys.isEmpty()) {
            sys = this.systemName;
        }

        if (sys != null && !sys.isEmpty()) {
            String prefix = sys + " ";
            if (name.startsWith(prefix)) {
                String suffix = name.substring(prefix.length());
                if (!suffix.isEmpty()) {
                    return suffix;
                }
            }
        }

        return name;
    }

    public void resetBodies() {
        bodies.clear();
        totalBodies = null;
        nonBodyCount = null;
        fssProgress = null;
        allBodiesFound = null;
    }

//    public void setSystemNameIfEmptyFromBodyName(String bodyName) {
//        if (systemName != null && !systemName.isEmpty()) {
//            return;
//        }
//        if (bodyName == null || bodyName.isEmpty()) {
//            return;
//        }
//
//        // Heuristic: bodyName is usually "SystemName <suffix>".
//        // Strip the last token to get a reasonable systemName guess.
//        int idx = bodyName.lastIndexOf(' ');
//        if (idx > 0) {
//            String candidate = bodyName.substring(0, idx).trim();
//            if (!candidate.isEmpty()) {
//                systemName = candidate;
//            }
//        }
//    }

    
    private static String toLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
    
    public Boolean getAllBodiesFound() {
        return allBodiesFound;
    }

    public void setAllBodiesFound(Boolean allBodiesFound) {
        this.allBodiesFound = allBodiesFound;
    }
    /**
     * True if this system appears in the local SystemCache (i.e. you have visited it).
     */
    public boolean isVisitedByMe() {
        return visitedByMe;
    }

    public void setVisitedByMe(boolean visitedByMe) {
        this.visitedByMe = visitedByMe;
    }

    /**
     * Total number of bodies reported by EDSM for this system (may be null if unknown).
     */
    public Integer getEdsmBodyCount() {
        return edsmBodyCount;
    }

    public void setEdsmBodyCount(Integer edsmBodyCount) {
        this.edsmBodyCount = edsmBodyCount;
    }

	public void setStarPos(double[] starPos) {
		this.starPos = starPos;
	}

	public double[] getStarPos() {
		return starPos;
	}

}
