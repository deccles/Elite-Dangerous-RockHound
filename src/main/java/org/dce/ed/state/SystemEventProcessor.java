package org.dce.ed.state;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.dce.ed.EliteDangerousOverlay;
import org.dce.ed.cache.CachedSystem;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.edsm.BodiesResponse;
import org.dce.ed.exobiology.BodyAttributes;
import org.dce.ed.exobiology.ExobiologyData;
import org.dce.ed.exobiology.ExobiologyData.BioCandidate;
import org.dce.ed.exobiology.NebulaGuardianClassifier;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.LiveJournalMonitor;
import org.dce.ed.logreader.event.BioScanPredictionEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.FssAllBodiesFoundEvent;
import org.dce.ed.logreader.event.FssBodySignalsEvent;
import org.dce.ed.logreader.event.FssDiscoveryScanEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.SaasignalsFoundEvent;
import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.logreader.event.ScanOrganicEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.util.EdsmClient;
import org.dce.ed.util.FirstBonusHelper;
import org.dce.ed.util.SpanshLandmark;
import org.dce.ed.util.SpanshLandmarkCache;

/**
 * Consumes Elite Dangerous journal events and mutates a SystemState.
 *
 * All GUI logic has been removed.
 * This engine may be invoked by Swing, CLI tools, or background processors.
 */
public class SystemEventProcessor {

    private final SystemState state;
    private final SystemCache systemCache;
    private final EdsmClient edsmClient; // optional; may be null
	private String clientKey;

    // Last known surface position from Status.json (used to pin ScanOrganic sample points).
    private Double lastLatitude;
    private Double lastLongitude;
    private String lastBodyName;
    private boolean lastOnFootOrSrv;

    public SystemEventProcessor(String clientKey, SystemState state) {
        this(clientKey, state, null);
    }

    public SystemEventProcessor(String clientKey, SystemState state, EdsmClient edsmClient) {
    	this.clientKey = clientKey;
        this.state = state;
        this.edsmClient = edsmClient;
        this.systemCache = SystemCache.getInstance();
    }

    /**
     * Entry point: consume any event and update SystemState.
     */
    public void handleEvent(EliteLogEvent event) {
        if (event instanceof StatusEvent) {
            handleStatus((StatusEvent) event);
            return;
        }

        if (event instanceof LocationEvent) {
            LocationEvent e = (LocationEvent) event;
            enterSystem(e.getStarSystem(), e.getSystemAddress(), e.getStarPos());
            return;
        }

        if (event instanceof FsdJumpEvent) {
            FsdJumpEvent e = (FsdJumpEvent) event;

            // Normal ship FSD jumps have docked == null => always update system.
            // CarrierJump may include Docked=true/false => only update if Docked==true.
            if (e.getDocked() == null || e.getDocked()) {
                enterSystem(e.getStarSystem(), e.getSystemAddress(), e.getStarPos());
            }
            return;
        }

        if (event instanceof FssDiscoveryScanEvent) {
            handleFssDiscovery((FssDiscoveryScanEvent) event);
            return;
        }

        if (event instanceof ScanEvent) {
            handleScan((ScanEvent) event);
            return;
        }

        if (event instanceof SaasignalsFoundEvent) {
            handleSaaSignals((SaasignalsFoundEvent) event);
            return;
        }

        if (event instanceof FssBodySignalsEvent) {
            handleFssBodySignals((FssBodySignalsEvent) event);
            return;
        }

        if (event instanceof FssAllBodiesFoundEvent) {
            handleFssAllBodiesFound((FssAllBodiesFoundEvent) event);
            return;
        }

        if (event instanceof ScanOrganicEvent) {
            handleScanOrganic((ScanOrganicEvent) event);
            return;
        }
    }


    // ---------------------------------------------------------------------
    // Nebula + Guardian system flags (ported from BioScan)
    // ---------------------------------------------------------------------

    private void applySystemClassifiersToAllBodies() {
        String nebulaTag = NebulaGuardianClassifier.determineNebulaTag(
                state.getSystemName(),
                state.getStarPos(),
                "all");

        boolean guardian = NebulaGuardianClassifier.isGuardianSystem(state.getStarPos());

        for (BodyInfo b : state.getBodies().values()) {
            b.setNebula(nebulaTag);
            b.setGuardianSystem(Boolean.valueOf(guardian));
        }
    }

    private void applySystemClassifiersToBody(BodyInfo b) {
        if (b == null) {
            return;
        }

        String nebulaTag = NebulaGuardianClassifier.determineNebulaTag(
                state.getSystemName(),
                state.getStarPos(),
                "all");

        boolean guardian = NebulaGuardianClassifier.isGuardianSystem(state.getStarPos());

        b.setNebula(nebulaTag);
        b.setGuardianSystem(Boolean.valueOf(guardian));
    }

    // ---------------------------------------------------------------------
    // System transition handling
    // ---------------------------------------------------------------------

    private void enterSystem(String name, long addr, double[] starPos) {
        boolean sameName = name != null && name.equals(state.getSystemName());
        boolean sameAddr = addr != 0L && addr == state.getSystemAddress();

        if (sameName || sameAddr) {
            if (name != null) {
                state.setSystemName(name);
            }
            if (addr != 0L) {
                state.setSystemAddress(addr);
            }
            if (starPos != null) {
                state.setStarPos(starPos);
            }
            applySystemClassifiersToAllBodies();

            return;
        }

        // New system: clear old one
        state.setSystemName(name);
        state.setSystemAddress(addr);
        state.setStarPos(starPos);

        state.resetBodies();
        state.setTotalBodies(null);
        state.setNonBodyCount(null);
        state.setFssProgress(null);
        state.setAllBodiesFound(null);

        // 1) Load from local cache (fast; gives you a body list even when FSS won't re-fire)
        CachedSystem cs = systemCache.get(addr, name);
        if (cs != null) {
            systemCache.loadInto(state, cs);
        }

        // 2) Enrich from EDSM (best-effort; fills legacy gaps)
        if (edsmClient != null && name != null && !name.isEmpty()) {
            try {
                BodiesResponse edsmBodies = edsmClient.showBodies(name);
                if (edsmBodies != null) {
                    systemCache.mergeBodiesFromEdsm(state, edsmBodies);
                }
            } catch (Exception ex) {
                // best-effort only; never block system state updates
                ex.printStackTrace();
            }
        }

        applySystemClassifiersToAllBodies();
    }

    // ---------------------------------------------------------------------
    // FSS Discovery (honk)
    // ---------------------------------------------------------------------

    private void handleFssDiscovery(FssDiscoveryScanEvent e) {
        if (state.getSystemName() == null) {
            state.setSystemName(e.getSystemName());
        }
        if (e.getSystemAddress() != 0L) {
            state.setSystemAddress(e.getSystemAddress());
        }

        state.setFssProgress(e.getProgress());
        state.setTotalBodies(e.getBodyCount());
        state.setNonBodyCount(e.getNonBodyCount());
    }

    // ---------------------------------------------------------------------
    // SCAN event (detailed body scan)
    // ---------------------------------------------------------------------

    private void handleScan(ScanEvent e) {
        // Scans can appear before the Location/FsdJump event that establishes the active system
        // (e.g., carrier-related event ordering). Make sure we're in the scan's system first,
        // otherwise later enterSystem(...) will resetBodies() and discard this scan.
        String scanSystemName = e.getStarSystem();
        long scanSystemAddress = e.getSystemAddress();

        boolean sameName = scanSystemName != null
                && scanSystemName.equals(state.getSystemName());
        boolean sameAddr = scanSystemAddress != 0L
                && scanSystemAddress == state.getSystemAddress();

        if (!sameName || !sameAddr) {
            enterSystem(scanSystemName, scanSystemAddress, state.getStarPos());
        }

        if (isBeltOrRing(e.getBodyName())) {
            return;
        }

        // IMPORTANT:
        // If BodyID is missing (-1), never use -1 as the map key (or it will be skipped by SystemCache.storeSystem()).
        // Use a stable temp key derived from body name instead.
        int key =
                e.getBodyId() >= 0
                        ? e.getBodyId()
                        : tempBodyKey(e.getBodyName());

        BodyInfo info;

        if (e.getBodyId() >= 0) {
            // If we previously created a temp entry for this same body name, migrate it now.
            migrateTempBodyIfPresent(e.getBodyId(), e.getBodyName());
            info = state.getOrCreateBody(e.getBodyId());
        } else {
            info = state.getOrCreateBody(key);
        }

        if (info.getStarPos() == null && state.getStarPos() != null) {
            info.setStarPos(state.getStarPos());
        }

        // Store a non--1 id so persistence works. If/when we later learn the real BodyID,
        // migrateTempBodyIfPresent(...) will move/merge into the real ID entry.
        info.setBodyId(key);

        info.setBodyName(e.getBodyName());
        info.setStarSystem(e.getStarSystem());
        info.setBodyShortName(state.computeShortName(e.getStarSystem(), e.getBodyName()));

        info.setDistanceLs(e.getDistanceFromArrivalLs());
        info.setLandable(e.isLandable());
        info.setGravityMS(e.getSurfaceGravity());

        Double pPa = e.getSurfacePressure();
        if (pPa != null && !Double.isNaN(pPa)) {
            // Journal SurfacePressure is in Pascals; store in atmospheres for rules/UI
            info.setSurfacePressure(pPa / 101325.0);
        }

        info.setAtmoOrType(chooseAtmoOrType(e));
        info.setHighValue(isHighValue(e));

        info.setPlanetClass(e.getPlanetClass());
        info.setAtmosphere(e.getAtmosphere());

        if (e.getAtmosphereComposition() != null && !e.getAtmosphereComposition().isEmpty()) {
            info.setAtmosphereComposition(e.getAtmosphereComposition());
        }

        if (e.getSurfaceTemperature() != null) {
            info.setSurfaceTempK(e.getSurfaceTemperature());
        }

        if (e.getVolcanism() != null && !e.getVolcanism().isEmpty()) {
            info.setVolcanism(e.getVolcanism());
        }

        if (e.getStarType() != null && !e.getStarType().isEmpty()) {
        	info.setStarType(e.getStarType());
        }

        if (e.getWasMapped() != null)
        {
            info.setWasMapped(e.getWasMapped());
        }
        if (e.getWasDiscovered() != null)
        {
            info.setWasDiscovered(e.getWasDiscovered());
        }
        if (e.getWasFootfalled() != null)
        {
            info.setWasFootfalled(e.getWasFootfalled());
        }
        
        info.setOrbitalPeriod(e.getOrbitalPeriod());
        
        int parentStarBodyId = findParentStarBodyId(e);
        if (parentStarBodyId >= 0) {
            info.setParentStarBodyId(parentStarBodyId);

            BodyInfo parentStar = state.getBodies().get(Integer.valueOf(parentStarBodyId));
            if (parentStar != null
                    && parentStar.getBodyName() != null
                    && !parentStar.getBodyName().isEmpty()) {
                info.setParentStar(parentStar.getBodyName());
                info.setStarType(parentStar.getStarType());
            }
        }

        // Use the stable key, never e.getBodyId() when it is -1.
        state.getBodies().put(key, info);

        for (BodyInfo body : state.getBodies().values()) {
            updatePredictions(body);
        }
    }

    private static int findParentStarBodyId(ScanEvent e) {
        if (e == null) {
            return -1;
        }

        List<ScanEvent.ParentRef> parents = e.getParents();
        if (parents == null || parents.isEmpty()) {
            return -1;
        }

        for (ScanEvent.ParentRef p : parents) {
            if (p == null || p.getType() == null) {
                continue;
            }
            if ("Star".equalsIgnoreCase(p.getType())) {
                return p.getBodyId();
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------------
    // SAASignalsFound (DSS results)
    // ---------------------------------------------------------------------

    private void handleSaaSignals(SaasignalsFoundEvent e) {
    	BodyInfo info = getOrCreateBody(e.getBodyId(), e.getBodyName());

        List<SaasignalsFoundEvent.Signal> signals = e.getSignals();
        if (signals != null) {
            for (SaasignalsFoundEvent.Signal s : signals) {
                String type = toLower(s.getType());
                String loc = toLower(s.getTypeLocalised());

                if (type.contains("biological") || loc.contains("biological")) {
                    info.setHasBio(true);
                } else if (type.contains("geological") || loc.contains("geological")) {
                    info.setHasGeo(true);
                }
            }
        }

        List<SaasignalsFoundEvent.Genus> genuses = e.getGenuses();
        if (genuses != null) {
            for (SaasignalsFoundEvent.Genus g : genuses) {
                String genusName = toLower(g.getGenusLocalised());
                if (genusName.isEmpty()) {
                    genusName = toLower(g.getGenus());
                }
                if (!genusName.isEmpty()) {
                    info.addObservedGenus(genusName);
                }
            }
            updatePredictions(info);
        }


    }

    // ---------------------------------------------------------------------
    // FSSBodySignalsEvent (FSS version of DSS signals)
    // ---------------------------------------------------------------------

    private void handleFssBodySignals(FssBodySignalsEvent e) {
    	BodyInfo info = getOrCreateBody(e.getBodyId(), e.getBodyName());

        List<SaasignalsFoundEvent.Signal> signals = e.getSignals();
        if (signals != null) {
            for (SaasignalsFoundEvent.Signal s : signals) {
                String type = toLower(s.getType());
                String loc = toLower(s.getTypeLocalised());

                if (type.contains("biological") || loc.contains("biological")) {
                    info.setHasBio(true);
                    applyBioSignalCount(info,  s.getCount());
                    
                } else if (type.contains("geological") || loc.contains("geological")) {
                    info.setHasGeo(true);
                }
            }

        }
    }

    // ---------------------------------------------------------------------
    // FSSAllBodiesFound – all bodies in system discovered
    // ---------------------------------------------------------------------

    private void handleFssAllBodiesFound(FssAllBodiesFoundEvent e) {
        if (state.getSystemName() == null) {
            state.setSystemName(e.getSystemName());
        }
        if (e.getSystemAddress() != 0L) {
            state.setSystemAddress(e.getSystemAddress());
        }

        if (state.getTotalBodies() == null && e.getBodyCount() > 0) {
            state.setTotalBodies(e.getBodyCount());
        }

        state.setAllBodiesFound(Boolean.TRUE);
    }

    // ---------------------------------------------------------------------
    // ScanOrganic – the most important exobiology event
    // ---------------------------------------------------------------------

    private void handleScanOrganic(ScanOrganicEvent e) {
        if (e.getSystemAddress() != 0L && state.getSystemAddress() == 0L) {
            state.setSystemAddress(e.getSystemAddress());
        }

        BodyInfo info = getOrCreateBody(e.getBodyId(), e.getBodyName());
        info.setHasBio(true);

        CachedSystem system = SystemCache.getInstance().get(e.getSystemAddress(), null);

        String bodyName = e.getBodyName();
        if (bodyName != null && !bodyName.isEmpty()) {
            if (info.getBodyName() == null || info.getBodyName().isEmpty()) {
                info.setBodyName(bodyName);
            }
            if (info.getShortName() == null || info.getShortName().isEmpty()) {
                info.setBodyShortName(state.computeShortName(system.systemName, bodyName));
            }
        }

        String genusName = firstNonBlank(e.getGenusLocalised(), e.getGenus());
        String speciesName = firstNonBlank(e.getSpeciesLocalised(), e.getSpecies());

        if (speciesName.startsWith(genusName + " ")) {
            speciesName = speciesName.replace(genusName,"").trim(); 
        }

        if (genusName != null && !genusName.isEmpty()) {
            info.addObservedGenusPrefix(genusName);

            String displayName;
            if (speciesName != null && !speciesName.isEmpty()) {
                displayName = genusName + " " + speciesName;
            } else {
                displayName = genusName;
            }
            info.addObservedBioDisplayName(displayName);
            info.recordBioSample(displayName, e.getScanType());

            if (lastOnFootOrSrv && lastLatitude != null && lastLongitude != null) {
                if (lastBodyName == null
                        || lastBodyName.isBlank()
                        || bodyName == null
                        || bodyName.isBlank()
                        || lastBodyName.equals(bodyName)) {
                	System.out.println("record bio sample point");
                    info.recordBioSamplePoint(
                            displayName,
                            e.getScanType(),
                            lastLatitude.doubleValue(),
                            lastLongitude.doubleValue());
                }
            }
        }
    }

    
    private void handleStatus(StatusEvent e) {
        if (e == null) {
            return;
        }

        lastLatitude = e.getLatitude();
        lastLongitude = e.getLongitude();
        lastBodyName = e.getBodyName();

        // This is the ONLY flag that matters for "we have real surface coordinates".
        lastOnFootOrSrv = e.getDecodedFlags() != null && e.getDecodedFlags().hasLatLong;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a.trim();
        }
        if (b != null && !b.trim().isEmpty()) {
            return b.trim();
        }
        return null;
    }

    private static String toLower(String s) {
        return (s == null) ? "" : s.toLowerCase(java.util.Locale.ROOT);
    }

    // ---------------------------------------------------------------------
    // Prediction handling
    // ---------------------------------------------------------------------

    private void updatePredictions(BodyInfo info) {
        if (!info.hasBio()) {
            return;
        }

        // If DSS/SAASignalsFound gave us observed genus, use it to FILTER existing predictions
        // even if we already predicted earlier.
        if (info.getPredictions() != null && !info.getPredictions().isEmpty()
                && info.getObservedGenusPrefixes() != null && !info.getObservedGenusPrefixes().isEmpty()) {

            List<BioCandidate> filtered = new java.util.ArrayList<>();

            for (BioCandidate cand : info.getPredictions()) {
                String display = toLower(cand.getDisplayName());

                // Take the "genus" as the first token of the prediction display name.
                // e.g. "Bacterium ..." -> "bacterium"
                String predictedGenus = display;
                int idx = display.indexOf(' ');
                if (idx > 0) {
                    predictedGenus = display.substring(0, idx).trim();
                }

                boolean match = false;
                for (String observed : info.getObservedGenusPrefixes()) {
                    String obs = toLower(observed).trim();

                    // Normalize a bit (some events/strings are inconsistent: bacterium/bacteria, stratum/strata)
                    obs = normalizeGenus(obs);
                    String pred = normalizeGenus(predictedGenus);

                    if (pred.equals(obs)) {
                        match = true;
                        break;
                    }
                }

                if (match) {
                    filtered.add(cand);
                }
            }

            // If we found any matches, replace the list with only observed-genus matches.
            // If we found none, fall through and allow fresh prediction computation below.
            if (!filtered.isEmpty()) {
                info.setPredictions(filtered);
                return;
            }
        }

        // If we already have predictions and no observed-genus filtering changed anything, keep them.
        if (info.getPredictions() != null && !info.getPredictions().isEmpty()) {
            return;
        }

        BodyAttributes attrs = info.buildBodyAttributes(state);
        if (attrs == null) {
            // This is the normal case when FSSBodySignals arrives before the Detailed Scan.
            return;
        }

        List<BioCandidate> candidates = ExobiologyData.predict(attrs);
        if (candidates == null || candidates.isEmpty()) {
            info.clearPredictions();
            return;
        }

        // If we have observed genus, filter fresh candidates too.
        if (info.getObservedGenusPrefixes() != null && !info.getObservedGenusPrefixes().isEmpty()) {
            List<BioCandidate> filtered = new java.util.ArrayList<>();

            for (BioCandidate cand : candidates) {
                String display = toLower(cand.getDisplayName());
                String predictedGenus = display;
                int idx = display.indexOf(' ');
                if (idx > 0) {
                    predictedGenus = display.substring(0, idx).trim();
                }

                boolean match = false;
                for (String observed : info.getObservedGenusPrefixes()) {
                    String obs = normalizeGenus(toLower(observed).trim());
                    String pred = normalizeGenus(predictedGenus);

                    if (pred.equals(obs)) {
                        match = true;
                        break;
                    }
                }

                if (match) {
                    filtered.add(cand);
                }
            }

            if (!filtered.isEmpty()) {
                // Publish first (so UI can render immediately), then return.
                info.setPredictions(filtered);

                if (!Boolean.TRUE.equals(info.getWasFootfalled()) && info.getSpanshLandmarks() == null) {
                    List<SpanshLandmark> landmarks = SpanshLandmarkCache.getInstance().getOrFetch(info.getStarSystem(), info.getBodyName());
                    if (landmarks != null) {
                        info.setSpanshLandmarks(landmarks);
                    }
                }
                boolean bonusApplies = FirstBonusHelper.firstBonusApplies(info);

                BioScanPredictionEvent bioScanPredictionEvent = new BioScanPredictionEvent(
                        Instant.now(),
                        null,
                        info.getBodyName(),
                        info.getBodyId(),
                        info.getStarSystem(),
                        bonusApplies,
                        filtered,
                        BioScanPredictionEvent.PredictionKind.UPDATE);

                LiveJournalMonitor.getInstance(EliteDangerousOverlay.clientKey).dispatch(bioScanPredictionEvent);
                return;
            }
        }

        // *** FIX: publish to BodyInfo BEFORE dispatching the event ***
        info.setPredictions(candidates);
        if (!Boolean.TRUE.equals(info.getWasFootfalled()) && info.getSpanshLandmarks() == null) {
            List<SpanshLandmark> landmarks = SpanshLandmarkCache.getInstance().getOrFetch(info.getStarSystem(), info.getBodyName());
            if (landmarks != null) {
                info.setSpanshLandmarks(landmarks);
            }
        }
        boolean bonusApplies = FirstBonusHelper.firstBonusApplies(info);

        BioScanPredictionEvent bioScanPredictionEvent = new BioScanPredictionEvent(
                Instant.now(),
                null,
                info.getBodyName(),
                info.getBodyId(),
                info.getStarSystem(),
                bonusApplies,
                candidates,
                BioScanPredictionEvent.PredictionKind.INITIAL);

        LiveJournalMonitor.getInstance(EliteDangerousOverlay.clientKey).dispatch(bioScanPredictionEvent);
    }

    private static String normalizeGenus(String s) {
        if (s == null) {
            return "";
        }

        String x = s.trim().toLowerCase();

        // Common genus plural/singular inconsistencies you will see in various sources
        if (x.equals("bacteria")) {
            return "bacterium";
        }
        if (x.equals("strata")) {
            return "stratum";
        }

        return x;
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private boolean isBeltOrRing(String bodyName) {
        if (bodyName == null) {
            return false;
        }
        String n = bodyName.toLowerCase(Locale.ROOT);
        return n.contains("belt cluster")
                || n.contains("ring")
                || n.contains("belt ");
    }

    private String chooseAtmoOrType(ScanEvent e) {
        if (e.getAtmosphere() != null && !e.getAtmosphere().isEmpty()) {
            return e.getAtmosphere();
        }
        if (e.getPlanetClass() != null && !e.getPlanetClass().isEmpty()) {
            return e.getPlanetClass();
        }
        if (e.getStarType() != null) {
            return e.getStarType();
        }
        return "";
    }

    private boolean isHighValue(ScanEvent e) {
        String pc = toLower(e.getPlanetClass());
        String tf = toLower(e.getTerraformState());
        return pc.contains("earth-like")
                || pc.contains("water world")
                || pc.contains("ammonia world")
                || tf.contains("terraformable");
    }
    
    private static int tempBodyKey(String bodyName) {
        if (bodyName == null) {
            return Integer.MIN_VALUE;
        }

        // Force negative so we never collide with real bodyIds (which are >= 0 in practice).
        int h = bodyName.trim().toLowerCase(Locale.ROOT).hashCode();
        return h | 0x80000000;
    }

    private BodyInfo getOrCreateBody(int bodyId, String bodyName) {
        BodyInfo info;

        if (bodyId >= 0) {
            // If we previously created a temp entry for this same body name, migrate it now.
            migrateTempBodyIfPresent(bodyId, bodyName);
            info = state.getOrCreateBody(bodyId);
        } else {
            int key = tempBodyKey(bodyName);
            info = state.getOrCreateBody(key);
        }

        applySystemClassifiersToBody(info);
        return info;
    }

    private void migrateTempBodyIfPresent(int realBodyId, String bodyName) {
        if (bodyName == null || bodyName.isBlank()) {
            return;
        }

        int tmpKey = tempBodyKey(bodyName);

        BodyInfo tmp = state.getBodies().get(tmpKey);
        if (tmp == null) {
            return;
        }

        BodyInfo real = state.getBodies().get(realBodyId);

        if (real == null) {
            // Move temp -> real
            state.getBodies().remove(tmpKey);
            tmp.setBodyId(realBodyId);
            state.getBodies().put(realBodyId, tmp);
            return;
        }

        // Real already exists: merge a few fields then drop temp
        // (Keep it minimal; merge only "additive" fields so we don't overwrite good data.)

        if (real.getBodyName() == null && tmp.getBodyName() != null) {
            real.setBodyName(tmp.getBodyName());
        }
        if (real.getStarSystem() == null && tmp.getStarSystem() != null) {
            real.setStarSystem(tmp.getStarSystem());
        }
        if (Double.isNaN(real.getDistanceLs()) && !Double.isNaN(tmp.getDistanceLs())) {
            real.setDistanceLs(tmp.getDistanceLs());
        }
        if (real.getGravityMS() == null && tmp.getGravityMS() != null) {
            real.setGravityMS(tmp.getGravityMS());
        }
        if (real.getSurfaceTempK() == null && tmp.getSurfaceTempK() != null) {
            real.setSurfaceTempK(tmp.getSurfaceTempK());
        }
        if (real.getSurfacePressure() == null && tmp.getSurfacePressure() != null) {
            real.setSurfacePressure(tmp.getSurfacePressure());
        }

        if (!real.hasBio() && tmp.hasBio()) {
            real.setHasBio(true);
        }
        if (!real.hasGeo() && tmp.hasGeo()) {
            real.setHasGeo(true);
        }
        if (!real.isLandable() && tmp.isLandable()) {
            real.setLandable(true);
        }

        if (real.getObservedGenusPrefixes() == null || real.getObservedGenusPrefixes().isEmpty()) {
            if (tmp.getObservedGenusPrefixes() != null && !tmp.getObservedGenusPrefixes().isEmpty()) {
                real.setObservedGenusPrefixes(new java.util.HashSet<>(tmp.getObservedGenusPrefixes()));
            }
        } else if (tmp.getObservedGenusPrefixes() != null) {
            for (String g : tmp.getObservedGenusPrefixes()) {
                real.addObservedGenus(g);
            }
        }

        if (tmp.getObservedBioDisplayNames() != null) {
            for (String n : tmp.getObservedBioDisplayNames()) {
                real.addObservedBioDisplayName(n);
            }
        }

        // If temp had predictions and real doesn't yet, copy them
        if ((real.getPredictions() == null || real.getPredictions().isEmpty())
                && tmp.getPredictions() != null
                && !tmp.getPredictions().isEmpty()) {
            real.setPredictions(tmp.getPredictions());
        }

        state.getBodies().remove(tmpKey);
    }
    private void applyBioSignalCount(BodyInfo info, int count) {
        if (info == null) {
            return;
        }
        if (count <= 0) {
            return;
        }

        int existing = info.getNumberOfBioSignals();
        if (existing <= 0) {
            info.setNumberOfBioSignals(count);
            return;
        }

        // Keep the best/most complete value we’ve seen.
        info.setNumberOfBioSignals(Math.max(existing, count));
    }

}
