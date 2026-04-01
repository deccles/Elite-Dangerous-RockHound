package org.dce.ed.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;

import org.dce.ed.OverlayFrame;
import org.dce.ed.edsm.BodiesResponse;
import org.dce.ed.exobiology.ExobiologyData.BioCandidate;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.util.RingSummaryFormatter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
/**
 * SQLite-backed cache of star systems (bodies in {@code systems.payload_json}),
 * plus a singleton {@code overlay_global_state} row for commander-global values and the
 * current-system pointer (see {@link #getCacheDataDirectory()}).
 * <p>
 * <b>Current system:</b> {@code overlay_global_state} holds {@code current_system_address} /
 * {@code current_system_name}, updated on {@link #storeSystem} (unless bulk rescan).
 * {@link #loadLastSystem()} uses that pointer first, then the newest {@code systems} row.
 * Commander / overlay session is stored in {@code overlay_global_state.session_json} (see {@link #loadEdoSessionState()}).
 */



public final class SystemCache implements SystemStore {
    private CachedSystem lastLoadedSystem;

    private static final String CACHE_DB_FILE_NAME = "ed-overlay-systems-v1.db";

    /**
     * Single-row table for app-wide cache fields (not per-star-system), e.g. unsold exobiology credits.
     */
    private static final String SQLITE_GLOBAL_TABLE = "overlay_global_state";

    public static final String CACHE_DB_PATH_PROPERTY = "edo.cacheDbFile";
    /**
     * Optional directory for cache data; default is the parent of the SQLite DB file.
     */
    public static final String CACHE_DATA_DIR_PROPERTY = "edo.cacheDir";

    private static final String LEGACY_SESSION_FILE_NAME = "edo-session.json";
    private static final String PREF_KEY_EXO_CREDITS_TOTAL = "exo.creditsTotal";
    /** After migration, global row only stores schema_version + session_json. */
    private static final int GLOBAL_SCHEMA_SLIM = 3;

    private final Gson sessionGson = new GsonBuilder().create();
    /**
     * When {@code true}, {@link #storeSystem} skips updating the session header in {@code overlay_global_state}
     * (bulk journal rescan).
     */
    public static final String CACHE_BULK_SYSTEM_WRITE_PROPERTY = "edo.cache.bulkSystemWrite";

    private static final SystemCache INSTANCE = new SystemCache();

    private final Gson gson;
    private final Path cacheDbPath;
    private Connection sqliteConnection;
    private boolean sqliteReady;
    private long lastUpdatedAtMs;
    private boolean sessionBlobMigrationChecked;

    private SystemCache() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .serializeSpecialFloatingPointValues()
                .create();
        this.cacheDbPath = resolveDbPath();
        this.sqliteReady = initializeSqlite();
    }

    /**
     * Deletes the SQLite cache database and resets the singleton connection.
     */
    @Override
    public synchronized void clearAndDeleteOnDisk() {
        System.out.println("Delete cache DB " + cacheDbPath);
        lastLoadedSystem = null;
        try {
            if (sqliteConnection != null) {
                sqliteConnection.close();
            }
        } catch (SQLException ignored) {
        }
        sqliteConnection = null;
        sqliteReady = false;
        sessionBlobMigrationChecked = false;
        try {
            Files.deleteIfExists(cacheDbPath);
        } catch (IOException ex) {
            System.err.println("SystemCache: failed to delete sqlite cache file " + cacheDbPath + ": " + ex.getMessage());
        }
        sqliteReady = initializeSqlite();
    }

    public static SystemCache getInstance() {
        return INSTANCE;
    }

    public static CachedSystem load() throws IOException {
        return getInstance().loadLastSystem();
    }

    @Override
    public synchronized CachedSystem loadLastSystem() throws IOException {
        if (!sqliteReady) {
            return null;
        }
        CachedSystem cs = sqliteLoadLastSystem();
        lastLoadedSystem = cs;
        return cs;
    }

    /**
     * Unsold exobiology credits total as persisted on disk ({@code overlay_global_state}).
     */
    public synchronized Long getPersistedExobiologyCreditsTotalUnsold() {
        if (!sqliteReady) {
            return null;
        }
        EdoSessionState s = loadEdoSessionState();
        return s.getExobiologyCreditsTotalUnsold();
    }

    /**
     * Load commander/overlay session from SQLite ({@code session_json}), migrating once from legacy file/columns if needed.
     */
    public synchronized EdoSessionState loadEdoSessionState() {
        if (!sqliteReady) {
            return new EdoSessionState();
        }
        try {
            ensureSessionMigratedAndLoaded();
            String json = sqliteReadSessionJsonRaw();
            if (json == null || json.isBlank()) {
                EdoSessionState empty = new EdoSessionState();
                empty.setVersion(2);
                writeSessionJsonToDb(empty);
                tryRebuildSlimGlobalTableIfNeeded();
                return empty;
            }
            EdoSessionState parsed = sessionGson.fromJson(json, EdoSessionState.class);
            return parsed != null ? parsed : new EdoSessionState();
        } catch (Exception e) {
            System.err.println("SystemCache: loadEdoSessionState failed: " + e.getMessage());
            return new EdoSessionState();
        }
    }

    /**
     * Persist full commander/overlay session (replaces {@code session_json}).
     */
    public synchronized void saveEdoSessionState(EdoSessionState state) {
        if (!sqliteReady || state == null) {
            return;
        }
        try {
            ensureSessionMigratedAndLoaded();
            writeSessionJsonToDb(state);
            tryRebuildSlimGlobalTableIfNeeded();
        } catch (Exception e) {
            System.err.println("SystemCache: saveEdoSessionState failed: " + e.getMessage());
        }
    }

    public static Path getLegacySessionJsonPath() {
        return getCacheDataDirectory().resolve(LEGACY_SESSION_FILE_NAME);
    }

    private static String canonicalName(String name) {
        return (name == null) ? null : name.toLowerCase(Locale.ROOT);
    }
    
    @Override
    public synchronized CachedSystem get(long systemAddress, String systemName) {
        if (!sqliteReady) {
            return null;
        }
        return sqliteGet(systemAddress, systemName);
    }

    /**
     * Stores/updates a cached system and persists to disk.
     */
    @Override
    public synchronized void put(long systemAddress,
            String systemName,
            double starPos[],
            Integer totalBodies,
            Integer nonBodyCount,
            Double fssProgress,
            Boolean allBodiesFound,
            Long exobiologyCreditsTotalUnsold,
            List<CachedBody> bodies) {
        if (!sqliteReady) {
            return;
        }
        if ((systemAddress == 0L) && (systemName == null || systemName.isEmpty())) {
            return;
        }
        CachedSystem cs = sqliteGet(systemAddress, systemName);
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
                    boolean idMatch = (newBody.bodyId >= 0 && existing.bodyId >= 0 && newBody.bodyId == existing.bodyId);
                    boolean nameMatch = (newBody.name != null && !newBody.name.isEmpty() && newBody.name.equals(existing.name));
                    if (idMatch || nameMatch) {
                        cs.bodies.set(i, newBody);
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    cs.bodies.add(newBody);
                }
            }
        }
        sqliteUpsert(cs);
        lastLoadedSystem = cs;
    }

    
    @Override
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
        if (sqliteReady) {
            EdoSessionState sess = loadEdoSessionState();
            if (sess.getExobiologyCreditsTotalUnsold() != null) {
                state.setExobiologyCreditsTotalUnsold(sess.getExobiologyCreditsTotalUnsold());
            }
            if (sess.getDocked() != null) {
                state.setDocked(sess.getDocked().booleanValue());
            }
        }
        if (cs.bodies == null) {
            return;
        }
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
            if (cb.spanshExcludeFromExobiology != null) {
                info.setSpanshExcludeFromExobiology(cb.spanshExcludeFromExobiology);
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
            if (info.isPlanetaryBodyForRingDisplay()
                    && cb.ringReserveHumanized != null
                    && !cb.ringReserveHumanized.isBlank()) {
                info.setRingReserveHumanized(cb.ringReserveHumanized);
            }
            if (info.isPlanetaryBodyForRingDisplay()
                    && cb.ringTypes != null
                    && !cb.ringTypes.isEmpty()) {
                info.setRingSummaryLines(new ArrayList<>(cb.ringTypes));
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
    @Override
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

            if (remote.type != null && remote.type.equalsIgnoreCase("Star")) {
                info.setRingSummaryLines(null);
                info.setRingReserveHumanized(null);
            } else if (remote.rings != null && !remote.rings.isEmpty()) {
                List<String> ringLines = RingSummaryFormatter.fromEdsmRings(remote.rings, info.getRingReserveHumanized());
                if (!ringLines.isEmpty() && info.getRingSummaryLines().isEmpty()) {
                    info.setRingSummaryLines(ringLines);
                }
            }
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

    @Override
    public void storeSystem(SystemState state) {
        if (state == null || state.getSystemName() == null || state.getSystemAddress() == 0L) {
            return;
        }

        // Merge-on-save: preserve certain fields from the existing on-disk cache when the
        // current in-memory SystemState does not currently have them populated.
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
            cb.spanshExcludeFromExobiology = b.getSpanshExcludeFromExobiology();
            if (b.isPlanetaryBodyForRingDisplay()
                    && b.getRingSummaryLines() != null
                    && !b.getRingSummaryLines().isEmpty()) {
                cb.ringTypes = new ArrayList<>(b.getRingSummaryLines());
            }
            if (!b.isPlanetaryBodyForRingDisplay()) {
                cb.ringReserveHumanized = null;
            } else if (b.getRingReserveHumanized() != null && !b.getRingReserveHumanized().isEmpty()) {
                cb.ringReserveHumanized = b.getRingReserveHumanized();
            }

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
                if (cb.spanshExcludeFromExobiology == null && prev.spanshExcludeFromExobiology != null) {
                    cb.spanshExcludeFromExobiology = prev.spanshExcludeFromExobiology;
                }
                if (b.isPlanetaryBodyForRingDisplay()
                        && (cb.ringTypes == null || cb.ringTypes.isEmpty())
                        && prev.ringTypes != null && !prev.ringTypes.isEmpty()) {
                    cb.ringTypes = new ArrayList<>(prev.ringTypes);
                }
                if (b.isPlanetaryBodyForRingDisplay()
                        && (cb.ringReserveHumanized == null || cb.ringReserveHumanized.isBlank())
                        && prev.ringReserveHumanized != null && !prev.ringReserveHumanized.isBlank()) {
                    cb.ringReserveHumanized = prev.ringReserveHumanized;
                }
                if (cb.spanshPredictedGenera == null && prev.spanshPredictedGenera != null) {
                    cb.spanshPredictedGenera = new ArrayList<>(prev.spanshPredictedGenera);
                }
                if (cb.gravityMS == null && prev.gravityMS != null) {
                    cb.gravityMS = prev.gravityMS;
                }
                if (cb.surfaceTempK == null && prev.surfaceTempK != null) {
                    cb.surfaceTempK = prev.surfaceTempK;
                }
            }

            cb.setNumberOfBioSignals(b.getNumberOfBioSignals());
            if (b.getPredictions() != null && !b.getPredictions().isEmpty()) {
                cb.predictions = b.getPredictions();
            } else if (prev != null && prev.predictions != null && !prev.predictions.isEmpty()) {
                cb.predictions = new ArrayList<>(prev.predictions);
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
                state.getExobiologyCreditsTotalUnsold(),
                list);
        if (sqliteReady && !isBulkSystemWrite()) {
            mergeEdoSessionBlobFromStoreSystem(state);
        }
    }

    private static boolean isBulkSystemWrite() {
        return Boolean.parseBoolean(System.getProperty(CACHE_BULK_SYSTEM_WRITE_PROPERTY, "false"));
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
    @Override
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

    @Override
    public synchronized CachedSystemSummary getSummary(long systemAddress, String systemName) {
        if (!sqliteReady) {
            return null;
        }
        return sqliteGetSummary(systemAddress, systemName);
    }

    /**
     * Absolute path to the SQLite system-cache database file (same rules as the internal cache).
     * Intended for developer tools; does not open a connection.
     * <p>
     * Tables include {@code systems} (per-system {@code payload_json}) and {@code overlay_global_state}
     * (singleton row for app-wide values such as unsold exobiology credits).
     */
    public static Path getSqliteCacheDbPath() {
        String override = System.getProperty(CACHE_DB_PATH_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Paths.get(override).toAbsolutePath().normalize();
        }
        String home = System.getProperty("user.home");
        if (home == null || home.isEmpty()) {
            home = ".";
        }
        return Paths.get(home, ".edo", CACHE_DB_FILE_NAME).toAbsolutePath().normalize();
    }

    /**
     * Directory for {@code edo-session.json} and related files (parent of DB unless {@link #CACHE_DATA_DIR_PROPERTY} is set).
     */
    public static Path getCacheDataDirectory() {
        String override = System.getProperty(CACHE_DATA_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Paths.get(override).toAbsolutePath().normalize();
        }
        Path db = getSqliteCacheDbPath();
        Path parent = db.getParent();
        if (parent != null) {
            return parent;
        }
        String home = System.getProperty("user.home");
        if (home == null || home.isEmpty()) {
            return Paths.get(".").toAbsolutePath().normalize();
        }
        return Paths.get(home);
    }

    private Path resolveDbPath() {
        return getSqliteCacheDbPath();
    }

    private boolean initializeSqlite() {
        try {
            Path parent = cacheDbPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            sqliteConnection = DriverManager.getConnection("jdbc:sqlite:" + cacheDbPath.toString());
            try (PreparedStatement ps = sqliteConnection.prepareStatement("PRAGMA journal_mode=WAL")) {
                ps.execute();
            }
            try (PreparedStatement ps = sqliteConnection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS systems (" +
                    "cache_key TEXT PRIMARY KEY," +
                    "system_address INTEGER," +
                    "canonical_name TEXT," +
                    "system_name TEXT," +
                    "total_bodies INTEGER," +
                    "fss_progress REAL," +
                    "all_bodies_found INTEGER," +
                    "cached_body_count INTEGER," +
                    "updated_at INTEGER," +
                    "payload_json TEXT NOT NULL" +
                    ")")) {
                ps.execute();
            }
            try (PreparedStatement ps = sqliteConnection.prepareStatement(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_systems_canonical_name ON systems(canonical_name) WHERE canonical_name IS NOT NULL")) {
                ps.execute();
            }
            try (PreparedStatement ps = sqliteConnection.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_systems_address ON systems(system_address)")) {
                ps.execute();
            }
            try (PreparedStatement ps = sqliteConnection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + SQLITE_GLOBAL_TABLE + " (" +
                    "singleton INTEGER PRIMARY KEY CHECK (singleton = 1)," +
                    "schema_version INTEGER NOT NULL DEFAULT " + GLOBAL_SCHEMA_SLIM + "," +
                    "session_json TEXT" +
                    ")")) {
                ps.execute();
            }
            migrateOverlayGlobalStateSchema();
            migrateExobiologyCreditsIntoGlobalTableIfNeeded();
            System.out.println("[EDO][Cache] sqlite path=" + cacheDbPath.toAbsolutePath());
            return true;
        } catch (Exception ex) {
            System.err.println("SystemCache: sqlite init failed (cache disabled): " + ex.getMessage());
            try {
                if (sqliteConnection != null) {
                    sqliteConnection.close();
                }
            } catch (SQLException ignored) {
            }
            sqliteConnection = null;
            return false;
        }
    }

    private void migrateOverlayGlobalStateSchema() throws SQLException {
        ensureGlobalStateColumn("schema_version", "INTEGER NOT NULL DEFAULT 1");
        if (isSlimGlobalTable()) {
            return;
        }
        ensureGlobalStateColumn("exobiology_credits_total_unsold", "INTEGER");
        ensureGlobalStateColumn("current_system_address", "INTEGER NOT NULL DEFAULT 0");
        ensureGlobalStateColumn("current_system_name", "TEXT NOT NULL DEFAULT ''");
        ensureGlobalStateColumn("docked", "INTEGER NOT NULL DEFAULT 0");
        ensureGlobalStateColumn("session_json", "TEXT");
    }

    private boolean isSlimGlobalTable() throws SQLException {
        return globalStateHasColumn("session_json")
                && !globalStateHasColumn("exobiology_credits_total_unsold")
                && !globalStateHasColumn("current_system_address");
    }

    private void ensureGlobalStateColumn(String column, String typeAndConstraints) throws SQLException {
        if (globalStateHasColumn(column)) {
            return;
        }
        try (PreparedStatement ps = sqliteConnection.prepareStatement(
                "ALTER TABLE " + SQLITE_GLOBAL_TABLE + " ADD COLUMN " + column + " " + typeAndConstraints)) {
            ps.execute();
        }
    }

    private boolean globalStateHasColumn(String column) throws SQLException {
        try (PreparedStatement ps = sqliteConnection.prepareStatement("PRAGMA table_info(" + SQLITE_GLOBAL_TABLE + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString(2))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String toSqlitePayloadJson(CachedSystem cs) {
        Long exo = cs.exobiologyCreditsTotalUnsold;
        cs.exobiologyCreditsTotalUnsold = null;
        try {
            return gson.toJson(cs);
        } finally {
            cs.exobiologyCreditsTotalUnsold = exo;
        }
    }

    private boolean sqliteGlobalStateTableEmpty() throws SQLException {
        try (PreparedStatement ps = sqliteConnection.prepareStatement(
                "SELECT COUNT(*) FROM " + SQLITE_GLOBAL_TABLE);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) == 0;
        }
    }

    private void migrateExobiologyCreditsIntoGlobalTableIfNeeded() {
        if (sqliteConnection == null) {
            return;
        }
        try {
            if (!globalStateHasColumn("exobiology_credits_total_unsold")) {
                return;
            }
            if (!sqliteGlobalStateTableEmpty()) {
                return;
            }
            try (PreparedStatement ps = sqliteConnection.prepareStatement(
                    "SELECT payload_json FROM systems ORDER BY updated_at DESC LIMIT 1");
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                String payload = rs.getString(1);
                if (payload == null) {
                    return;
                }
                CachedSystem parsed = gson.fromJson(payload, CachedSystem.class);
                if (parsed != null && parsed.exobiologyCreditsTotalUnsold != null) {
                    sqliteWriteGlobalExobiologyCredits(parsed.exobiologyCreditsTotalUnsold.longValue());
                }
            }
        } catch (SQLException ex) {
            System.err.println("SystemCache: migrate exobiology global row failed: " + ex.getMessage());
        }
    }

    private Long sqliteReadGlobalExobiologyCredits() {
        if (sqliteConnection == null) {
            return null;
        }
        try (PreparedStatement ps = sqliteConnection.prepareStatement(
                "SELECT exobiology_credits_total_unsold FROM " + SQLITE_GLOBAL_TABLE + " WHERE singleton = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            long v = rs.getLong(1);
            if (rs.wasNull()) {
                return null;
            }
            return Long.valueOf(v);
        } catch (SQLException ex) {
            System.err.println("SystemCache: sqlite read global exobiology credits failed: " + ex.getMessage());
            return null;
        }
    }

    private void sqliteWriteGlobalExobiologyCredits(long credits) {
        if (sqliteConnection == null) {
            return;
        }
        try (PreparedStatement ps = sqliteConnection.prepareStatement(
                "INSERT INTO " + SQLITE_GLOBAL_TABLE + " (singleton, exobiology_credits_total_unsold) VALUES (1, ?) " +
                "ON CONFLICT(singleton) DO UPDATE SET exobiology_credits_total_unsold = excluded.exobiology_credits_total_unsold")) {
            ps.setLong(1, credits);
            ps.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("SystemCache: sqlite write global exobiology credits failed: " + ex.getMessage());
        }
    }

    private static final class SessionHeader {
        final long currentSystemAddress;
        final String currentSystemName;
        final boolean docked;

        SessionHeader(long addr, String name, boolean docked) {
            this.currentSystemAddress = addr;
            this.currentSystemName = name != null ? name : "";
            this.docked = docked;
        }
    }

    private SessionHeader sqliteReadSessionHeader() {
        if (sqliteConnection == null) {
            return new SessionHeader(0L, "", false);
        }
        try (PreparedStatement ps = sqliteConnection.prepareStatement(
                "SELECT current_system_address, current_system_name, docked FROM " + SQLITE_GLOBAL_TABLE + " WHERE singleton = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return new SessionHeader(0L, "", false);
            }
            long addr = rs.getLong(1);
            String name = rs.getString(2);
            if (name == null) {
                name = "";
            }
            return new SessionHeader(addr, name, rs.getInt(3) != 0);
        } catch (SQLException ex) {
            System.err.println("SystemCache: sqlite read session header failed: " + ex.getMessage());
            return new SessionHeader(0L, "", false);
        }
    }

    private void sqliteWriteSessionHeader(long systemAddress, String systemName, boolean docked) {
        if (sqliteConnection == null) {
            return;
        }
        String name = systemName != null ? systemName : "";
        try (PreparedStatement ps = sqliteConnection.prepareStatement(
                "INSERT INTO " + SQLITE_GLOBAL_TABLE
                        + " (singleton, current_system_address, current_system_name, docked) VALUES (1, ?, ?, ?) "
                        + "ON CONFLICT(singleton) DO UPDATE SET "
                        + "current_system_address = excluded.current_system_address, "
                        + "current_system_name = excluded.current_system_name, "
                        + "docked = excluded.docked")) {
            ps.setLong(1, systemAddress);
            ps.setString(2, name);
            ps.setInt(3, docked ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("SystemCache: sqlite write session header failed: " + ex.getMessage());
        }
    }

    private static String sqliteKey(long systemAddress, String systemName) {
        if (systemAddress != 0L) {
            return "addr:" + systemAddress;
        }
        String canonical = canonicalName(systemName);
        if (canonical != null && !canonical.isBlank()) {
            return "name:" + canonical;
        }
        return null;
    }

    private CachedSystem sqliteGet(long systemAddress, String systemName) {
        if (sqliteConnection == null) {
            return null;
        }
        try {
            if (systemAddress != 0L) {
                try (PreparedStatement ps = sqliteConnection.prepareStatement(
                        "SELECT payload_json FROM systems WHERE system_address=? ORDER BY updated_at DESC LIMIT 1")) {
                    ps.setLong(1, systemAddress);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            CachedSystem cs = gson.fromJson(rs.getString(1), CachedSystem.class);
                            lastLoadedSystem = cs;
                            return cs;
                        }
                    }
                }
            }
            String canonical = canonicalName(systemName);
            if (canonical != null && !canonical.isBlank()) {
                try (PreparedStatement ps = sqliteConnection.prepareStatement(
                        "SELECT payload_json FROM systems WHERE canonical_name=? LIMIT 1")) {
                    ps.setString(1, canonical);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            CachedSystem cs = gson.fromJson(rs.getString(1), CachedSystem.class);
                            lastLoadedSystem = cs;
                            return cs;
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            System.err.println("SystemCache: sqlite get failed: " + ex.getMessage());
        }
        return null;
    }

    private void ensureSessionMigratedAndLoaded() {
        if (!sqliteReady || sessionBlobMigrationChecked) {
            return;
        }
        synchronized (this) {
            if (sessionBlobMigrationChecked) {
                return;
            }
            try {
                if (!globalStateHasColumn("session_json")) {
                    return;
                }
                String j = sqliteReadSessionJsonRaw();
                if (j == null || j.isBlank()) {
                    migrateSessionBlobFromLegacy();
                    tryRebuildSlimGlobalTableIfNeeded();
                }
            } catch (Exception ex) {
                System.err.println("SystemCache: session blob migration failed: " + ex.getMessage());
            } finally {
                sessionBlobMigrationChecked = true;
            }
        }
    }

    private String sqliteReadSessionJsonRaw() throws SQLException {
        if (sqliteConnection == null || !globalStateHasColumn("session_json")) {
            return null;
        }
        try (PreparedStatement ps = sqliteConnection.prepareStatement(
                "SELECT session_json FROM " + SQLITE_GLOBAL_TABLE + " WHERE singleton = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            return rs.getString(1);
        }
    }

    private void writeSessionJsonToDb(EdoSessionState state) throws SQLException {
        if (sqliteConnection == null || state == null) {
            return;
        }
        String json = sessionGson.toJson(state);
        if (isSlimGlobalTable()) {
            try (PreparedStatement ps = sqliteConnection.prepareStatement(
                    "INSERT INTO " + SQLITE_GLOBAL_TABLE + " (singleton, schema_version, session_json) VALUES (1, ?, ?) "
                            + "ON CONFLICT(singleton) DO UPDATE SET session_json = excluded.session_json, "
                            + "schema_version = excluded.schema_version")) {
                ps.setInt(1, GLOBAL_SCHEMA_SLIM);
                ps.setString(2, json);
                ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = sqliteConnection.prepareStatement(
                    "INSERT INTO " + SQLITE_GLOBAL_TABLE + " (singleton, session_json) VALUES (1, ?) "
                            + "ON CONFLICT(singleton) DO UPDATE SET session_json = excluded.session_json")) {
                ps.setString(1, json);
                ps.executeUpdate();
            }
        }
    }

    private void migrateSessionBlobFromLegacy() throws Exception {
        EdoSessionState merged = new EdoSessionState();
        merged.setVersion(2);
        Path legacyPath = getLegacySessionJsonPath();
        if (Files.isRegularFile(legacyPath)) {
            try {
                String fileJson = Files.readString(legacyPath, java.nio.charset.StandardCharsets.UTF_8);
                if (fileJson != null && !fileJson.isBlank()) {
                    EdoSessionState fromFile = sessionGson.fromJson(fileJson, EdoSessionState.class);
                    if (fromFile != null) {
                        merged = fromFile;
                        if (merged.getVersion() < 2) {
                            merged.setVersion(2);
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("SystemCache: legacy edo-session.json parse failed: " + ex.getMessage());
            }
        }
        SessionHeader legHead = safeSqliteReadSessionHeader();
        merged.setCacheLastSystemAddress(legHead.currentSystemAddress != 0L ? Long.valueOf(legHead.currentSystemAddress) : null);
        merged.setCacheLastSystemName(legHead.currentSystemName.isEmpty() ? null : legHead.currentSystemName);
        merged.setDocked(Boolean.valueOf(legHead.docked));
        Long exoCol = safeSqliteReadGlobalExobiologyCredits();
        if (exoCol != null) {
            merged.setExobiologyCreditsTotalUnsold(exoCol);
        }
        if (merged.getExobiologyCreditsTotalUnsold() == null) {
            try {
                long p = Preferences.userNodeForPackage(OverlayFrame.class).getLong(PREF_KEY_EXO_CREDITS_TOTAL, Long.MIN_VALUE);
                if (p != Long.MIN_VALUE) {
                    merged.setExobiologyCreditsTotalUnsold(Long.valueOf(p));
                }
            } catch (Exception ignored) {
            }
        }
        writeSessionJsonToDb(merged);
        try {
            Files.deleteIfExists(legacyPath);
        } catch (IOException io) {
            System.err.println("SystemCache: could not delete legacy session file: " + io.getMessage());
        }
    }

    private SessionHeader safeSqliteReadSessionHeader() {
        try {
            if (sqliteConnection != null && globalStateHasColumn("current_system_address")) {
                return sqliteReadSessionHeader();
            }
        } catch (SQLException e) {
            System.err.println("SystemCache: legacy session header read failed: " + e.getMessage());
        }
        return new SessionHeader(0L, "", false);
    }

    private Long safeSqliteReadGlobalExobiologyCredits() {
        try {
            if (sqliteConnection != null && globalStateHasColumn("exobiology_credits_total_unsold")) {
                return sqliteReadGlobalExobiologyCredits();
            }
        } catch (SQLException e) {
            System.err.println("SystemCache: legacy exo credits read failed: " + e.getMessage());
        }
        return null;
    }

    private SessionHeader readCachePointerForLoadLastSystem() {
        if (sqliteConnection == null) {
            return new SessionHeader(0L, "", false);
        }
        try {
            ensureSessionMigratedAndLoaded();
            if (globalStateHasColumn("session_json")) {
                String json = sqliteReadSessionJsonRaw();
                if (json != null && !json.isBlank()) {
                    EdoSessionState s = sessionGson.fromJson(json, EdoSessionState.class);
                    if (s != null) {
                        Long ca = s.getCacheLastSystemAddress();
                        String cn = s.getCacheLastSystemName();
                        if ((ca != null && ca.longValue() != 0L)
                                || (cn != null && !cn.isEmpty())) {
                            long addr = ca != null ? ca.longValue() : 0L;
                            String name = cn != null ? cn : "";
                            boolean docked = Boolean.TRUE.equals(s.getDocked());
                            return new SessionHeader(addr, name, docked);
                        }
                    }
                }
            }
            if (globalStateHasColumn("current_system_address")) {
                return sqliteReadSessionHeader();
            }
        } catch (Exception ex) {
            System.err.println("SystemCache: readCachePointerForLoadLastSystem failed: " + ex.getMessage());
        }
        return new SessionHeader(0L, "", false);
    }

    private void mergeEdoSessionBlobFromStoreSystem(SystemState state) {
        if (!sqliteReady || state == null) {
            return;
        }
        try {
            EdoSessionState s = loadEdoSessionState();
            if (state.getSystemAddress() != 0L) {
                s.setCacheLastSystemAddress(Long.valueOf(state.getSystemAddress()));
            }
            if (state.getSystemName() != null && !state.getSystemName().isEmpty()) {
                s.setCacheLastSystemName(state.getSystemName());
            }
            s.setDocked(Boolean.valueOf(state.isDocked()));
            if (state.getExobiologyCreditsTotalUnsold() != null) {
                s.setExobiologyCreditsTotalUnsold(state.getExobiologyCreditsTotalUnsold());
            }
            saveEdoSessionState(s);
        } catch (Exception ex) {
            System.err.println("SystemCache: mergeEdoSessionBlobFromStoreSystem failed: " + ex.getMessage());
        }
    }

    private void tryRebuildSlimGlobalTableIfNeeded() throws SQLException {
        if (sqliteConnection == null || !globalStateHasColumn("exobiology_credits_total_unsold")) {
            return;
        }
        String json = sqliteReadSessionJsonRaw();
        if (json == null || json.isBlank()) {
            return;
        }
        String temp = SQLITE_GLOBAL_TABLE + "_slim_rebuild";
        boolean ac = sqliteConnection.getAutoCommit();
        try {
            sqliteConnection.setAutoCommit(false);
            try (java.sql.Statement st = sqliteConnection.createStatement()) {
                st.executeUpdate("DROP TABLE IF EXISTS " + temp);
                st.executeUpdate("CREATE TABLE " + temp + " ("
                        + "singleton INTEGER PRIMARY KEY CHECK (singleton = 1),"
                        + "schema_version INTEGER NOT NULL DEFAULT " + GLOBAL_SCHEMA_SLIM + ","
                        + "session_json TEXT NOT NULL)");
            }
            try (PreparedStatement ps = sqliteConnection.prepareStatement(
                    "INSERT INTO " + temp + " (singleton, schema_version, session_json) VALUES (1, ?, ?)")) {
                ps.setInt(1, GLOBAL_SCHEMA_SLIM);
                ps.setString(2, json);
                ps.executeUpdate();
            }
            try (java.sql.Statement st = sqliteConnection.createStatement()) {
                st.executeUpdate("DROP TABLE " + SQLITE_GLOBAL_TABLE);
                st.executeUpdate("ALTER TABLE " + temp + " RENAME TO " + SQLITE_GLOBAL_TABLE);
            }
            sqliteConnection.commit();
            sessionBlobMigrationChecked = false;
            migrateOverlayGlobalStateSchema();
            sessionBlobMigrationChecked = true;
        } catch (SQLException e) {
            try {
                sqliteConnection.rollback();
            } catch (SQLException ignored) {
            }
            throw e;
        } finally {
            sqliteConnection.setAutoCommit(ac);
        }
    }

    private CachedSystem sqliteLoadLastSystem() {
        if (sqliteConnection == null) {
            return null;
        }
        try {
            SessionHeader head = readCachePointerForLoadLastSystem();
            if (head.currentSystemAddress != 0L
                    || (head.currentSystemName != null && !head.currentSystemName.isEmpty())) {
                CachedSystem byPtr = sqliteGet(head.currentSystemAddress, head.currentSystemName);
                if (byPtr != null) {
                    return byPtr;
                }
            }
            try (PreparedStatement ps = sqliteConnection.prepareStatement(
                    "SELECT payload_json FROM systems ORDER BY updated_at DESC LIMIT 1");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return gson.fromJson(rs.getString(1), CachedSystem.class);
                }
            }
        } catch (SQLException ex) {
            System.err.println("SystemCache: sqlite load last failed: " + ex.getMessage());
        }
        return null;
    }

    private void sqliteUpsert(CachedSystem cs) {
        if (sqliteConnection == null || cs == null) {
            return;
        }
        String key = sqliteKey(cs.systemAddress, cs.systemName);
        if (key == null) {
            return;
        }
        int cachedBodyCount = (cs.bodies == null) ? 0 : cs.bodies.size();
        String payload = toSqlitePayloadJson(cs);
        long now = nextMonotonicUpdateMillis();
        String sql = "INSERT INTO systems (cache_key, system_address, canonical_name, system_name, total_bodies, fss_progress, all_bodies_found, cached_body_count, updated_at, payload_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(cache_key) DO UPDATE SET " +
                "system_address=excluded.system_address, canonical_name=excluded.canonical_name, system_name=excluded.system_name, " +
                "total_bodies=excluded.total_bodies, fss_progress=excluded.fss_progress, all_bodies_found=excluded.all_bodies_found, " +
                "cached_body_count=excluded.cached_body_count, updated_at=excluded.updated_at, payload_json=excluded.payload_json";
        try (PreparedStatement ps = sqliteConnection.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setLong(2, cs.systemAddress);
            String canonical = canonicalName(cs.systemName);
            if (canonical == null || canonical.isBlank()) {
                ps.setNull(3, java.sql.Types.VARCHAR);
            } else {
                ps.setString(3, canonical);
            }
            ps.setString(4, cs.systemName);
            if (cs.totalBodies == null) ps.setNull(5, java.sql.Types.INTEGER); else ps.setInt(5, cs.totalBodies.intValue());
            if (cs.fssProgress == null) ps.setNull(6, java.sql.Types.DOUBLE); else ps.setDouble(6, cs.fssProgress.doubleValue());
            if (cs.allBodiesFound == null) ps.setNull(7, java.sql.Types.INTEGER); else ps.setInt(7, cs.allBodiesFound.booleanValue() ? 1 : 0);
            ps.setInt(8, cachedBodyCount);
            ps.setLong(9, now);
            ps.setString(10, payload);
            ps.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("SystemCache: sqlite upsert failed: " + ex.getMessage());
        }
    }

    private synchronized long nextMonotonicUpdateMillis() {
        long now = System.currentTimeMillis();
        if (now <= lastUpdatedAtMs) {
            now = lastUpdatedAtMs + 1L;
        }
        lastUpdatedAtMs = now;
        return now;
    }

    private CachedSystemSummary sqliteGetSummary(long systemAddress, String systemName) {
        if (sqliteConnection == null) {
            return null;
        }
        try {
            if (systemAddress != 0L) {
                try (PreparedStatement ps = sqliteConnection.prepareStatement(
                        "SELECT system_address, system_name, total_bodies, fss_progress, all_bodies_found, cached_body_count FROM systems WHERE system_address=? ORDER BY updated_at DESC LIMIT 1")) {
                    ps.setLong(1, systemAddress);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return readSummary(rs);
                        }
                    }
                }
            }
            String canonical = canonicalName(systemName);
            if (canonical != null && !canonical.isBlank()) {
                try (PreparedStatement ps = sqliteConnection.prepareStatement(
                        "SELECT system_address, system_name, total_bodies, fss_progress, all_bodies_found, cached_body_count FROM systems WHERE canonical_name=? LIMIT 1")) {
                    ps.setString(1, canonical);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return readSummary(rs);
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            System.err.println("SystemCache: sqlite summary failed: " + ex.getMessage());
        }
        return null;
    }

    private static CachedSystemSummary readSummary(ResultSet rs) throws SQLException {
        long addr = rs.getLong(1);
        String name = rs.getString(2);
        Integer total = rs.getObject(3) != null ? Integer.valueOf(rs.getInt(3)) : null;
        Double progress = rs.getObject(4) != null ? Double.valueOf(rs.getDouble(4)) : null;
        Boolean all = rs.getObject(5) != null ? Boolean.valueOf(rs.getInt(5) == 1) : null;
        int bodyCount = rs.getInt(6);
        return new CachedSystemSummary(addr, name, total, progress, all, bodyCount);
    }

}
