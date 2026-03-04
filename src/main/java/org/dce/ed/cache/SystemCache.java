package org.dce.ed.cache;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dce.ed.edsm.BodiesResponse;
import org.dce.ed.exobiology.ExobiologyData.BioCandidate;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Simple on-disk cache of system bodies, similar in spirit to what
 * tools like Exploration Buddy do. The cache is keyed by system
 * address (when available) and falls back to system name.
 *
 * Data is persisted as JSON in the user's home directory so that
 * previously scanned systems can be shown immediately on future runs.
 */



public final class SystemCache {
    private CachedSystem lastLoadedSystem;

    private static final String CACHE_FILE_NAME = ".edOverlaySystems.json";

    /**
     * Optional override for where the cache JSON is stored.
     * Used by tools like RescanJournalsMain when --cache is provided.
     */
    public static final String CACHE_PATH_PROPERTY = "edo.cacheFile";

    private static final SystemCache INSTANCE = new SystemCache();

    private final Gson gson;
    private final Path cachePath;

    private final Map<Long, CachedSystem> byAddress = new HashMap<>();
    private final Map<String, CachedSystem> byName = new HashMap<>();

    private boolean loaded = false;
    
    private SystemCache() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .serializeSpecialFloatingPointValues()
                .create();

        String override = System.getProperty(CACHE_PATH_PROPERTY);
        if (override != null && !override.isBlank()) {
            this.cachePath = Paths.get(override).toAbsolutePath().normalize();
        } else {
            String home = System.getProperty("user.home");
            if (home == null || home.isEmpty()) {
                home = ".";
            }
            this.cachePath = Paths.get(home, CACHE_FILE_NAME);
        }
    }

    /**
     * Clears the in-memory cache and deletes the persisted JSON cache file.
     *
     * Intended for tools that want a true "start from scratch" rebuild.
     */
    public synchronized void clearAndDeleteOnDisk() {
    	System.out.println("Delete cachefile " + cachePath);
        // Mark loaded so ensureLoaded() won't re-load from disk later in this JVM.
        loaded = true;

        byAddress.clear();
        byName.clear();
        lastLoadedSystem = null;

        try {
            Files.deleteIfExists(cachePath);
        } catch (IOException ex) {
            System.err.println("SystemCache: failed to delete cache file " + cachePath + ": " + ex.getMessage());
        }
    }

    public static SystemCache getInstance() {
        return INSTANCE;
    }

    public static CachedSystem load() throws IOException {
        SystemCache cache = getInstance();
        cache.ensureLoaded();
        return cache.lastLoadedSystem;
    }


    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;

        if (!Files.isRegularFile(cachePath)) {
            return;
        }

        System.out.println("SystemCache: loading from " + cachePath.toAbsolutePath());

        try (BufferedReader reader = Files.newBufferedReader(cachePath, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<CachedSystem>>() {}.getType();
            List<CachedSystem> systems = gson.fromJson(reader, type);
            if (systems == null) {
                return;
            }
            for (CachedSystem cs : systems) {
                if (cs == null) {
                    continue;
                }
                if (cs.systemAddress != 0L) {
                    byAddress.put(cs.systemAddress, cs);
                }
                if (cs.systemName != null && !cs.systemName.isEmpty()) {
                    byName.put(canonicalName(cs.systemName), cs);
                }
                lastLoadedSystem = cs;
            }
        } catch (IOException ex) {
            // ignore; cache will just start empty
        }
    }

    private static String canonicalName(String name) {
        return (name == null) ? null : name.toLowerCase(Locale.ROOT);
    }
    
    public synchronized CachedSystem get(long systemAddress, String systemName) {
        ensureLoaded();

        CachedSystem cs = null;
        if (systemAddress != 0L) {
            cs = byAddress.get(systemAddress);
        }
        if (cs == null && systemName != null && !systemName.isEmpty()) {
            cs = byName.get(canonicalName(systemName));
        }
        return cs;
    }

    /**
     * Stores/updates a cached system and persists to disk.
     */
    public synchronized void put(long systemAddress,
            String systemName,
            double starPos[],
            Integer totalBodies,
            Integer nonBodyCount,
            Double fssProgress,
            Boolean allBodiesFound,
            List<CachedBody> bodies) {
        ensureLoaded();

//        System.out.println("systemcache.put " + starPos[0] + "," + starPos[1] + "," + starPos[2]);
        if ((systemAddress == 0L) && (systemName == null || systemName.isEmpty())) {
            return;
        }

        CachedSystem cs = get(systemAddress, systemName);
        if (cs == null) {
            cs = new CachedSystem();
        }
        cs.starPos = starPos;
        cs.systemAddress = systemAddress;
        cs.systemName = systemName;
        cs.totalBodies = totalBodies;
        cs.nonBodyCount = nonBodyCount;
        cs.fssProgress = fssProgress;
        cs.allBodiesFound = allBodiesFound;
        if (cs.bodies == null) {
            cs.bodies = new ArrayList<>();
        }

        if (bodies != null && !bodies.isEmpty()) {
            for (CachedBody newBody : bodies) {
                boolean merged = false;
                for (int i = 0; i < cs.bodies.size(); i++) {
                    CachedBody existing = cs.bodies.get(i);
                    // Prefer matching by bodyId when available, otherwise fall back to name.
                    boolean idMatch = (newBody.bodyId >= 0 && existing.bodyId >= 0 && newBody.bodyId == existing.bodyId);
                    boolean nameMatch = (newBody.name != null && !newBody.name.isEmpty()
                            && newBody.name.equals(existing.name));
                    if (idMatch || nameMatch) {
                        cs.bodies.set(i, newBody);
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                	if (newBody.bodyId == -1) {
                		new Throwable().printStackTrace();
                	}
                    cs.bodies.add(newBody);
                }
            }
        }

        if (systemAddress != 0L) {
            byAddress.put(systemAddress, cs);
        }
        if (systemName != null && !systemName.isEmpty()) {
            byName.put(canonicalName(systemName), cs);
        }

        save();
    }

    
    public void loadInto(SystemState state, CachedSystem cs) {
        if (state == null || cs == null) {
            return;
        }

        state.setSystemName(cs.systemName);
        state.setSystemAddress(cs.systemAddress);
        state.setVisitedByMe(true);
        state.setStarPos(cs.starPos);
        state.resetBodies();

        state.setTotalBodies(cs.totalBodies);
        state.setNonBodyCount(cs.nonBodyCount);
        state.setFssProgress(cs.fssProgress);
        state.setAllBodiesFound(cs.allBodiesFound);
        
        for (CachedBody cb : cs.bodies) {
            BodyInfo info = new BodyInfo();
            info.setBodyName(cb.name);
            info.setStarSystem(cb.starSystem);
            info.setStarPos(state.getStarPos());
            info.setBodyShortName(state.computeShortName(cb.starSystem, cb.name));
            info.setBodyId(cb.bodyId);
            info.setDistanceLs(cb.distanceLs);
            info.setGravityMS(cb.gravityMS);
            info.setSurfacePressure(cb.surfacePressure);
            
            info.setLandable(cb.landable);
            info.setHasBio(cb.hasBio);
            info.setHasGeo(cb.hasGeo);
            info.setHighValue(cb.highValue);
            info.setAtmoOrType(cb.atmoOrType);
            info.setPlanetClass(cb.planetClass);
            info.setAtmosphere(cb.atmosphere);
            if (cb.atmosphereComposition != null && !cb.atmosphereComposition.isEmpty()) {
                info.setAtmosphereComposition(cb.atmosphereComposition);
            }
            info.setSurfaceTempK(cb.surfaceTempK);
            info.setOrbitalPeriod(cb.orbitalPeriod);
            info.setVolcanism(cb.volcanism);
            info.setNumberOfBioSignals(cb.getNumberOfBioSignals());
            info.setDiscoveryCommander(cb.discoveryCommander);

            info.setNebula(cb.nebula);
            info.setParentStar(cb.parentStar);
            info.setParentStarBodyId(cb.parentStarBodyId);
            info.setStarType(cb.starType);
            
            info.setWasMapped(cb.wasMapped);
            info.setWasDiscovered(cb.wasDiscovered);
            info.setWasFootfalled(cb.wasFootfalled);
            if (cb.spanshLandmarks != null) {
                info.setSpanshLandmarks(new ArrayList<>(cb.spanshLandmarks));
            }
            if (cb.bioSampleCountsByDisplayName != null && !cb.bioSampleCountsByDisplayName.isEmpty()) {
                info.setBioSampleCounts(cb.bioSampleCountsByDisplayName);
            if (cb.bioSamplePointsByDisplayName != null && !cb.bioSamplePointsByDisplayName.isEmpty()) {
                Map<String, List<BodyInfo.BioSamplePoint>> pts = new HashMap<>();
                for (Map.Entry<String, List<CachedBody.BioSamplePoint>> e : cb.bioSamplePointsByDisplayName.entrySet()) {
                    if (e.getValue() == null || e.getValue().isEmpty()) {
                        continue;
                    }
                    List<BodyInfo.BioSamplePoint> out = new ArrayList<>();
                    for (CachedBody.BioSamplePoint p : e.getValue()) {
                        out.add(new BodyInfo.BioSamplePoint(p.latitude, p.longitude));
                    }
                    pts.put(e.getKey(), out);
                }
                info.setBioSamplePoints(pts);
            }

            }
            
            if (cb.predictions != null && !cb.predictions.isEmpty()) {
                info.setPredictions(new ArrayList<BioCandidate>(cb.predictions));
            }
            
            if (cb.observedGenusPrefixes != null && !cb.observedGenusPrefixes.isEmpty()) {
                info.setObservedGenusPrefixes(new java.util.HashSet<>(cb.observedGenusPrefixes));
            }
            if (cb.observedBioDisplayNames != null && !cb.observedBioDisplayNames.isEmpty()) {
                info.setObservedBioDisplayNames(new java.util.HashSet<>(cb.observedBioDisplayNames));
            }
            state.getBodies().put(info.getBodyId(), info);
        }
        
    }
   
    /**
     * Merge EDSM bodies information into the current SystemState.
     *
     * If allowEdsmStandalone is true, EDSM bodies may create new BodyInfo entries.
     * If allowEdsmStandalone is false, EDSM bodies ONLY supplement existing local bodies:
     *   - We match by EDSM bodyId (remote.id) / journal BodyID.
     *   - If there is no local body with the same BodyID, the EDSM body is ignored.
     */
    public void mergeBodiesFromEdsm(SystemState state, BodiesResponse edsm) {
        if (state == null || edsm == null || edsm.bodies == null || edsm.bodies.isEmpty()) {
            return;
        }

        boolean allowEdsmStandalone=false;
        
        // If we don't already know how many bodies there are, use EDSM's list size.
        if (state.getTotalBodies() == null) {
            state.setTotalBodies(edsm.bodies.size());
        }

        // Build a lookup of star name by EDSM *bodyId* (NOT EDSM "id")
        Map<Long, String> starNameByBodyId = new HashMap<>();
        for (BodiesResponse.Body b : edsm.bodies) {
            if (b == null) {
                continue;
            }
            if (b.type != null && b.type.equalsIgnoreCase("Star")) {
                starNameByBodyId.put(b.id, b.name);
            }
        }

        Map<Integer, BodyInfo> local = state.getBodies();
        if (local == null) {
            return;
        }

        for (BodiesResponse.Body remote : edsm.bodies) {
            if (remote == null || remote.name == null || remote.name.isEmpty()) {
                continue;
            }

            // Prefer EDSM bodyId as the key (matches journal BodyID in practice)
            Integer remoteBodyId = toBodyKey(remote.id);
            if (remoteBodyId == null) {
                // If we can't determine a BodyID, we can't "same-id" match.
                if (!allowEdsmStandalone) {
                    continue;
                }
            }

            BodyInfo info = null;

            if (remoteBodyId != null) {
                info = local.get(remoteBodyId);

                if (info == null) {
                    if (!allowEdsmStandalone) {
                        // EDSM can only supplement existing local bodies: drop unmatched.
                        continue;
                    }
                    info = new BodyInfo();
                    info.setBodyId(remoteBodyId);
                    local.put(remoteBodyId, info);
                }
            } else {
                // Fallback: id is missing/unusable
                if (!allowEdsmStandalone) {
                    continue;
                }

                // Standalone allowed: match by name or create synthetic id
                info = findBodyByName(local, remote.name);
                if (info == null) {
                    info = new BodyInfo();
                    info.setBodyId(-1 * (local.size() + 1));
                    local.put(info.getBodyId(), info);
                }
            }

            // Basic identity fields
            if (info.getBodyName() == null || info.getBodyName().isEmpty()) {
                info.setBodyName(remote.name);
            }

            String sysName = state.getSystemName();
            if (sysName == null || sysName.isEmpty()) {
                sysName = edsm.name;
                if (sysName != null && !sysName.isEmpty()) {
                    state.setSystemName(sysName);
                }
            }
            if (sysName != null && !sysName.isEmpty()) {
                info.setStarSystem(sysName);
                if (info.getShortName() == null || info.getShortName().isEmpty()) {
                    info.setBodyShortName(state.computeShortName(sysName, remote.name));
                }
            }

            // Copy system starPos onto bodies (EDSM won't provide per-body starPos)
            if (info.getStarPos() == null && state.getStarPos() != null) {
                info.setStarPos(state.getStarPos());
            }

            if (remote.type != null && remote.type.equalsIgnoreCase("Star")) {
                if (info.getStarType() == null || info.getStarType().isEmpty()) {
                    String sc = parseStarClassFromEdsmSubType(remote.subType);
                    if (sc != null && !sc.isEmpty()) {
                        info.setStarType(sc);
                    }
                }
            }

            // Landable / gravity / radius / surface pressure
            if (remote.isLandable != null) {
                info.setLandable(remote.isLandable.booleanValue());
            }
            if (remote.gravity != null) {
                info.setGravityMS(remote.gravity);
            }
            if (remote.radius != null) {
                info.setRadius(remote.radius);
            }
            if (remote.getSurfacePressure() != null) {
                info.setSurfacePressure(remote.getSurfacePressure());
            }

            // Atmosphere / planet class
            if (remote.atmosphereType != null && !remote.atmosphereType.isEmpty()) {
                info.setAtmosphere(remote.atmosphereType);
                if (info.getAtmoOrType() == null || info.getAtmoOrType().isEmpty()) {
                    info.setAtmoOrType(remote.atmosphereType);
                }
            }
            if (remote.subType != null && !remote.subType.isEmpty()
                    && remote.type != null && remote.type.equalsIgnoreCase("Planet")) {
                info.setPlanetClass(remote.subType);
                if (info.getAtmoOrType() == null || info.getAtmoOrType().isEmpty()) {
                    info.setAtmoOrType(remote.subType);
                }
            }

            if ((info.getDistanceLs() <= 0 || Double.isNaN(info.getDistanceLs()))
                    && remote.distanceToArrival != null) {
                info.setDistanceLs(remote.distanceToArrival);
            }

            // Parent star: EDSM parents list uses {"Star": <bodyId>}
            if ((info.getParentStar() == null || info.getParentStar().isEmpty())
                    && remote.parents != null && !remote.parents.isEmpty()) {
                Integer parentStarBodyId = null;
                for (BodiesResponse.ParentRef p : remote.parents) {
                    if (p != null && p.Star != null) {
                        parentStarBodyId = p.Star;
                        break;
                    }
                }
                if (parentStarBodyId != null) {
                    String parentStarName = starNameByBodyId.get(parentStarBodyId.longValue());
                    if (parentStarName != null && !parentStarName.isEmpty()) {
                        info.setParentStar(parentStarName);
                    }
                }
            }

            // High value (EDSM-only bodies won't hit your ScanEvent logic)
            String pc = toLower(remote.subType);
            String tf = toLower(remote.terraformingState);
            boolean highValue =
                    pc.contains("earth-like")
                            || pc.contains("water world")
                            || pc.contains("ammonia world")
                            || tf.contains("terraformable");
            info.setHighValue(highValue);
        }
    }

    private static BodyInfo findBodyByName(Map<Integer, BodyInfo> bodies, String name) {
        for (BodyInfo b : bodies.values()) {
            if (b != null && name.equals(b.getBodyName())) {
                return b;
            }
        }
        return null;
    }

    private static String toLower(String s) {
        return (s == null) ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static String parseStarClassFromEdsmSubType(String subType) {
        if (subType == null) {
            return null;
        }
        String s = subType.trim();
        if (s.isEmpty()) {
            return null;
        }
        // Common EDSM format: "M (Red dwarf) Star", "G (White-Yellow) Star", etc.
        char c = s.charAt(0);
        if (Character.isLetter(c)) {
            return String.valueOf(Character.toUpperCase(c));
        }
        return null;
    }

    public void storeSystem(SystemState state) {
        if (state == null || state.getSystemName() == null || state.getSystemAddress() == 0L) {
            return;
        }

        // Merge-on-save: preserve certain fields from the existing on-disk cache when the
        // current in-memory SystemState does not currently have them populated.
        ensureLoaded();
        CachedSystem existing = get(state.getSystemAddress(), state.getSystemName());

        Map<Integer, CachedBody> existingBodies = new HashMap<>();
        Map<String, CachedBody> existingBodiesByName = new HashMap<>();
        if (existing != null && existing.bodies != null) {
            for (CachedBody eb : existing.bodies) {
                if (eb == null) {
                    continue;
                }
                existingBodies.put(Integer.valueOf(eb.bodyId), eb);
                if (eb.name != null && !eb.name.isEmpty()) {
                    existingBodiesByName.put(canonicalName(eb.name), eb);
                }
            }
        }

        List<CachedBody> list = new ArrayList<>();

        for (BodyInfo b : state.getBodies().values()) {
            if (b.getBodyId() == -1) {
//                System.out.println("Skipping body with id -1");
                continue;
            }

            CachedBody prev = existingBodies.get(Integer.valueOf(b.getBodyId()));
            if (prev == null && b.getBodyName() != null && !b.getBodyName().isEmpty()) {
                prev = existingBodiesByName.get(canonicalName(b.getBodyName()));
            }

            CachedBody cb = new CachedBody();
            cb.name = b.getBodyName();
            cb.bodyId = b.getBodyId();
            cb.starSystem = b.getStarSystem();

            cb.starPos = state.getStarPos();
            cb.distanceLs = b.getDistanceLs();
            cb.gravityMS = b.getGravityMS();
            cb.landable = b.isLandable();
            cb.hasBio = b.hasBio();
            cb.hasGeo = b.hasGeo();
            cb.highValue = b.isHighValue();
            cb.atmoOrType = b.getAtmoOrType();
            cb.planetClass = b.getPlanetClass();
            cb.atmosphere = b.getAtmosphere();
            if (b.getAtmosphereComposition() != null && !b.getAtmosphereComposition().isEmpty()) {
                cb.atmosphereComposition = new HashMap<>(b.getAtmosphereComposition());
            }
            cb.surfaceTempK = b.getSurfaceTempK();
            cb.orbitalPeriod = b.getOrbitalPeriod();
            cb.volcanism = b.getVolcanism();
            cb.discoveryCommander = b.getDiscoveryCommander();
            cb.surfacePressure = b.getSurfacePressure();
            cb.nebula = b.getNebula();
            cb.parentStar = b.getParentStar();
            cb.parentStarBodyId = b.getParentStarBodyId();
            cb.starType = b.getStarType();

            cb.wasMapped = b.getWasMapped();
            cb.wasDiscovered = b.getWasDiscovered();
            cb.wasFootfalled = b.getWasFootfalled();
            cb.spanshLandmarks = b.getSpanshLandmarks() != null ? new ArrayList<>(b.getSpanshLandmarks()) : null;

            // Preserve cache truth when the current session hasn't learned these flags yet.
            // These flags are monotonic in practice (once true, they don't become false).
            if (prev != null) {
                if (cb.atmosphereComposition == null || cb.atmosphereComposition.isEmpty()) {
                    cb.atmosphereComposition = prev.atmosphereComposition;
                }
                if (cb.wasMapped == null || (Boolean.FALSE.equals(cb.wasMapped) && Boolean.TRUE.equals(prev.wasMapped))) {
                    cb.wasMapped = prev.wasMapped;
                }
                if (cb.wasDiscovered == null || (Boolean.FALSE.equals(cb.wasDiscovered) && Boolean.TRUE.equals(prev.wasDiscovered))) {
                    cb.wasDiscovered = prev.wasDiscovered;
                }
                if (cb.wasFootfalled == null || (Boolean.FALSE.equals(cb.wasFootfalled) && Boolean.TRUE.equals(prev.wasFootfalled))) {
                    cb.wasFootfalled = prev.wasFootfalled;
                }
                if (cb.spanshLandmarks == null && prev.spanshLandmarks != null) {
                    cb.spanshLandmarks = new ArrayList<>(prev.spanshLandmarks);
                }
            }

            cb.setNumberOfBioSignals(b.getNumberOfBioSignals());
            if (b.getPredictions() != null && !b.getPredictions().isEmpty()) {
                cb.predictions = b.getPredictions();
            }

            Map<String, Integer> counts = b.getBioSampleCountsSnapshot();
            if (counts != null && !counts.isEmpty()) {
                cb.bioSampleCountsByDisplayName = counts;

                Map<String, List<BodyInfo.BioSamplePoint>> points = b.getBioSamplePointsSnapshot();
                if (points != null && !points.isEmpty()) {
                    Map<String, List<CachedBody.BioSamplePoint>> out = new HashMap<>();
                    for (Map.Entry<String, List<BodyInfo.BioSamplePoint>> e : points.entrySet()) {
                        if (e.getValue() == null || e.getValue().isEmpty()) {
                            continue;
                        }
                        List<CachedBody.BioSamplePoint> pts = new ArrayList<>();
                        for (BodyInfo.BioSamplePoint p : e.getValue()) {
                            pts.add(new CachedBody.BioSamplePoint(p.getLatitude(), p.getLongitude()));
                        }
                        out.put(e.getKey(), pts);
                    }
                    cb.bioSamplePointsByDisplayName = out;
                } else if (prev != null
                        && prev.bioSamplePointsByDisplayName != null
                        && !prev.bioSamplePointsByDisplayName.isEmpty()) {
                    cb.bioSamplePointsByDisplayName = prev.bioSamplePointsByDisplayName;
                } else {
                    cb.bioSamplePointsByDisplayName = null;
                }
            } else if (prev != null
                    && prev.bioSampleCountsByDisplayName != null
                    && !prev.bioSampleCountsByDisplayName.isEmpty()) {
                // Preserve rescan-built history when live session doesn't have it populated.
                cb.bioSampleCountsByDisplayName = prev.bioSampleCountsByDisplayName;
                cb.bioSamplePointsByDisplayName = prev.bioSamplePointsByDisplayName;
            } else {
                cb.bioSampleCountsByDisplayName = null;
                cb.bioSamplePointsByDisplayName = null;
            }

            if (b.getObservedGenusPrefixes() != null && !b.getObservedGenusPrefixes().isEmpty()) {
                cb.observedGenusPrefixes = new java.util.HashSet<>(b.getObservedGenusPrefixes());
            } else {
                cb.observedGenusPrefixes = null;
            }

            if (b.getObservedBioDisplayNames() != null && !b.getObservedBioDisplayNames().isEmpty()) {
                cb.observedBioDisplayNames = new java.util.HashSet<>(b.getObservedBioDisplayNames());
            } else {
                cb.observedBioDisplayNames = null;
            }

            list.add(cb);
        }

        put(state.getSystemAddress(),
                state.getSystemName(),
                state.getStarPos(),
                state.getTotalBodies(),
                state.getNonBodyCount(),
                state.getFssProgress(),
                state.getAllBodiesFound(),
                list);
    }

    private synchronized void save() {
        try {
            List<CachedSystem> systems = new ArrayList<>();

            Map<String, CachedSystem> unique = new HashMap<>();
            for (CachedSystem cs : byAddress.values()) {
                if (cs.systemName != null) {
                    unique.put(cs.systemName, cs);
                } else {
                    unique.put("addr:" + cs.systemAddress, cs);
                }
            }
            for (CachedSystem cs : byName.values()) {
                if (cs.systemName != null) {
                    unique.put(cs.systemName, cs);
                }
            }
            systems.addAll(unique.values());

            try (BufferedWriter writer = Files.newBufferedWriter(cachePath, StandardCharsets.UTF_8)) {
                gson.toJson(systems, writer);
            }
        } catch (IOException ex) {
            // ignore; cache is best-effort
        }
    }
    private static Integer toBodyKey(Long id) {
        if (id == null) {
            return null;
        }
        long v = id.longValue();
        if (v <= 0L) { // treat 0 / negatives as unusable
            return null;
        }
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            return null;
        }
        return (int) v;
    }

    /**
     * Merge discovery information (from EDSM or other sources) into the
     * current SystemState, keyed by full body name.
     *
     * If a body already has a discoveryCommander set, we leave it alone.
     * If EDSM says "this body has some discovery commander", we set a
     * placeholder so that downstream logic can treat it as "discovered".
     */
    public void mergeDiscoveryFlags(SystemState state, Map<String, Boolean> discoveryFlagsByBodyName) {
        if (state == null || discoveryFlagsByBodyName == null || discoveryFlagsByBodyName.isEmpty()) {
            return;
        }

        for (BodyInfo b : state.getBodies().values()) {
            Boolean has = discoveryFlagsByBodyName.get(b.getBodyName());
            if (has == null || !has.booleanValue()) {
                continue;
            }

            String existing = b.getDiscoveryCommander();
            if (existing == null || existing.isEmpty()) {
                // We don't know the actual name from EDSM and don't care;
                // we just need "some discovery commander exists".
                b.setDiscoveryCommander("EDSM");
            }
        }
    }

    
}
