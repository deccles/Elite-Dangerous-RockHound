package org.dce.ed.exobiology.audit;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.dce.ed.EliteDangerousOverlay;
import org.dce.ed.cache.CachedBody;
import org.dce.ed.cache.CachedSystem;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.exobiology.BodyAttributes;
import org.dce.ed.exobiology.ExobiologyData.AtmosphereType;
import org.dce.ed.exobiology.ExobiologyData.BioCandidate;
import org.dce.ed.exobiology.ExobiologyData.PlanetType;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.FssDiscoveryScanEvent;
import org.dce.ed.logreader.event.IFsdJump;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.logreader.event.ScanOrganicEvent;
import org.dce.ed.state.BodyInfo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Standalone utility with a main() that scans all Elite Dangerous journal files
 * and audits exobiology predictions vs. observed biology.
 *
 * It mimics the iteration pattern used in RescanJournalsMain:
 * - Uses EliteJournalReader to find and read journals
 * - Tracks current system name/address as events are processed
 * - Aggregates per-system, per-body info in a SystemAccumulator
 *
 * For each body with observed biology ("truth" from ScanOrganic),
 * it builds a BodyAttributes from the Scan data, runs ExobiologyData.predict,
 * and records a "fail" case whenever an observed species is NOT in the
 * set of predicted species.
 *
 * Output is a JSON array of audit cases written to
 *   exobiology_audit_cases.json
 * in the current working directory, unless you override with:
 *
 *   java ... org.dce.ed.logreader.ExobiologyAuditMain path/to/output.json
 */
public class ExobiologyAuditMain {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public static void main(String[] args) throws IOException {
        String outputPath = args != null && args.length >= 1
                ? args[0]
                : "exobiology_audit_cases.json";

        System.out.println("Auditing exobiology predictions vs observed biology...");
        System.out.println("Using EliteJournalReader to scan all available journals...");

        EliteJournalReader reader = new EliteJournalReader(EliteDangerousOverlay.clientKey);
        List<EliteLogEvent> events = reader.readEventsFromLastNJournalFiles(Integer.MAX_VALUE);

        System.out.println("Loaded " + events.size() + " events from journal files.");

        Map<SystemKey, SystemAccumulator> systems = new HashMap<>();
        String currentSystemName = null;
        long currentSystemAddress = 0L;

        // --- Event loop: mirrors RescanJournalsMain's main() ---
        for (EliteLogEvent event : events) {

            if (event instanceof LocationEvent) {
                LocationEvent le = (LocationEvent) event;
                currentSystemName = le.getStarSystem();
                currentSystemAddress = le.getSystemAddress();
                SystemKey key = new SystemKey(currentSystemAddress, currentSystemName);
                SystemAccumulator acc = systems.computeIfAbsent(
                        key, k -> new SystemAccumulator(k.systemName, k.systemAddress));
                acc.applyLocation(le);

            } else if (event instanceof IFsdJump) {
            	// carrierJumpEvent and fsdJumpEvent
                IFsdJump je = (IFsdJump) event;
                currentSystemName = je.getStarSystem();
                currentSystemAddress = je.getSystemAddress();
                SystemKey key = new SystemKey(currentSystemAddress, currentSystemName);
                SystemAccumulator acc = systems.computeIfAbsent(
                        key, k -> new SystemAccumulator(k.systemName, k.systemAddress));
                acc.applyFsdJump(je);
            } else if (event instanceof FssDiscoveryScanEvent) {
                FssDiscoveryScanEvent fds = (FssDiscoveryScanEvent) event;
                String name = fds.getSystemName();
                long addr = fds.getSystemAddress();
                if (addr != 0L || name != null) {
                    if (name != null) {
                        currentSystemName = name;
                    }
                    if (addr != 0L) {
                        currentSystemAddress = addr;
                    }
                }
                SystemKey key = new SystemKey(currentSystemAddress, currentSystemName);
                SystemAccumulator acc = systems.computeIfAbsent(
                        key, k -> new SystemAccumulator(k.systemName, k.systemAddress));
                acc.applyFssDiscovery(fds);

            } else if (event instanceof ScanEvent) {
                ScanEvent se = (ScanEvent) event;
                long addr = se.getSystemAddress();
                String name = currentSystemName;
                if (addr == 0L) {
                    addr = currentSystemAddress;
                }
                SystemKey key = new SystemKey(addr, name);
                SystemAccumulator acc = systems.computeIfAbsent(
                        key, k -> new SystemAccumulator(k.systemName, k.systemAddress));
                acc.applyScan(se);

            } else if (event instanceof ScanOrganicEvent) {
                ScanOrganicEvent so = (ScanOrganicEvent) event;
                long addr = so.getSystemAddress();
                String name = currentSystemName;
                if (addr == 0L) {
                    addr = currentSystemAddress;
                }
                SystemKey key = new SystemKey(addr, name);
                SystemAccumulator acc = systems.computeIfAbsent(
                        key, k -> new SystemAccumulator(k.systemName, k.systemAddress));
                acc.applyScanOrganic(so);
            }
        }

        // --- Build audit cases ---
        List<ExobiologyAuditCase> allCases = new ArrayList<>();
        int systemCount = 0;
        int bodyCount = 0;

        for (SystemAccumulator acc : systems.values()) {
            if (acc.bodies.isEmpty()) {
                continue;
            }
            systemCount++;
            for (BodyInfo b : acc.bodies.values()) {
                if (b.getObservedBioDisplayNames() == null || b.getObservedBioDisplayNames().isEmpty()) {
                    continue; // no truth data; nothing to audit
                }
                bodyCount++;

                CachedSystem cachedSystem = SystemCache.getInstance() .get(acc.systemAddress, acc.systemName);
                CachedBody cachedBody = findCachedBody(cachedSystem, b);
                List<BioCandidate> preds = cachedBody == null ? null : cachedBody.predictions;

                ExobiologyAuditCase caseObj = buildAuditCase(
                        acc.systemName,
                        acc.systemAddress,
                        b,
                        preds
                );

                if (caseObj != null && caseObj.isFail()) {
                    allCases.add(caseObj);
                }
            }
        }

        // --- Write JSON ---
        Path out = Path.of(outputPath);
        try (Writer w = Files.newBufferedWriter(out)) {
            GSON.toJson(allCases, w);
        }

        System.out.println("Audit complete.");
        System.out.println("Systems with bodies: " + systemCount);
        System.out.println("Bodies with observed biology: " + bodyCount);
        System.out.println("Failing bodies (at least one observed species not predicted): "
                + allCases.size());
        
        for (ExobiologyAuditCase x : allCases) {
        	System.out.println(x.bodyName + " " +  x.missingObservedDisplayNames);
        }
        System.out.println("Audit cases written to: " + out.toAbsolutePath());
    }

    // ------------------------------------------------------------------------
    // System key & accumulator (mirrors RescanJournalsMain pattern)
    // ------------------------------------------------------------------------

    private static final class SystemKey {
        final long systemAddress;
        final String systemName;

        SystemKey(long systemAddress, String systemName) {
            this.systemAddress = systemAddress;
            this.systemName = systemName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SystemKey)) return false;
            SystemKey that = (SystemKey) o;
            if (systemAddress != 0L && that.systemAddress != 0L) {
                return systemAddress == that.systemAddress;
            }
            return Objects.equals(systemName, that.systemName);
        }

        @Override
        public int hashCode() {
            if (systemAddress != 0L) {
                return Long.hashCode(systemAddress);
            }
            return systemName != null ? systemName.hashCode() : 0;
        }
    }

    /**
     * Minimal body info, mirrors the fields used in RescanJournalsMain.BodyInfo
     * and the live overlay. This is where we keep both:
     * - physical attributes (planetClass, atmosphere, gravity, temp, volcanism)
     * - truth data (observed species from ScanOrganic).
     */
//    private static final class BodyInfo {
//        String name;
//        int bodyId = -1;
//        Double gravityMS = null;
//        boolean landable = false;
//
//        // Fields used for prediction/exobiology
//        String planetClass;
//        String atmosphere;
//        Double surfaceTempK;
//        String volcanism;
//
//        // Truth data from DSS / ScanOrganic
//        Set<String> observedGenusPrefixes;    // e.g. "bacterium", "stratum"
//        Set<String> observedBioDisplayNames;  // e.g. "Bacterium Nebulus"
//    }

    /**
     * Accumulates events for a single system and converts them to BodyInfo entries.
     */
    private static final class SystemAccumulator {
        String systemName;
        long systemAddress;

        final Map<Integer, BodyInfo> bodies = new HashMap<>();

        SystemAccumulator(String systemName, long systemAddress) {
            this.systemName = systemName;
            this.systemAddress = systemAddress;
        }

        void applyLocation(LocationEvent e) {
            if (e.getStarSystem() != null) {
                systemName = e.getStarSystem();
            }
            if (e.getSystemAddress() != 0L) {
                systemAddress = e.getSystemAddress();
            }
        }

        void applyFsdJump(IFsdJump e) {
            if (e.getStarSystem() != null) {
                systemName = e.getStarSystem();
            }
            if (e.getSystemAddress() != 0L) {
                systemAddress = e.getSystemAddress();
            }
        }

        void applyFssDiscovery(FssDiscoveryScanEvent e) {
            // We don't need any fields from this for the audit,
            // but we keep the method so the pattern matches RescanJournalsMain.
            if (e.getSystemName() != null && !e.getSystemName().isEmpty()) {
                systemName = e.getSystemName();
            }
            if (e.getSystemAddress() != 0L) {
                systemAddress = e.getSystemAddress();
            }
        }

        void applyScan(ScanEvent e) {
            String bodyName = e.getBodyName();
            if (isBeltOrRing(bodyName)) {
                return;
            }
            int id = e.getBodyId();
            if (id < 0) {
                return;
            }

            BodyInfo info = bodies.computeIfAbsent(id, ignored -> new BodyInfo());
            info.setBodyId(id);
            info.setBodyName(bodyName);
            info.setLandable(e.isLandable());
            info.setGravityMS(e.getSurfaceGravity());
            info.setSurfacePressure(e.getSurfacePressure());
            
            // Prediction-related attributes
            info.setPlanetClass(e.getPlanetClass());
            info.setAtmosphere(e.getAtmosphere());

            Double temp = e.getSurfaceTemperature();
            if (temp != null) {
                info.setSurfaceTempK(temp);
            }

            Double pressure = e.getSurfacePressure();
            if (pressure != null) {
                info.setSurfacePressure(pressure);
            }
            String volc = e.getVolcanism();
            if (volc != null && !volc.isEmpty()) {
                info.setVolcanism(volc);
            }
        }

        void applyScanOrganic(ScanOrganicEvent e) {
            int bodyId = e.getBodyId();
            String bodyName = e.getBodyName(); // may be null or empty

            BodyInfo info = findOrCreateBodyByIdOrName(bodyId, bodyName);
            if (info == null) {
                return;
            }

            // Genus prefix (for narrowing predictions, if we ever use it)
            String genusPrefix = toLower(e.getGenusLocalised());
            if (genusPrefix.isEmpty()) {
                genusPrefix = toLower(e.getGenus());
            }
            if (!genusPrefix.isEmpty()) {
                if (info.getObservedGenusPrefixes() == null) {
                    info.setObservedGenusPrefixes(new HashSet<>());
                }
                info.getObservedGenusPrefixes().add(genusPrefix);
            }

            // Full display name (truth row): "Bacterium Nebulus", etc.
            String genusDisp = firstNonEmpty(e.getGenusLocalised(), e.getGenus());
            String speciesDisp = firstNonEmpty(e.getSpeciesLocalised(), e.getSpecies());
            String displayName = buildDisplayName(genusDisp, speciesDisp);

            if (!isEmpty(displayName)) {
                if (info.getObservedBioDisplayNames() == null) {
                    info.setObservedBioDisplayNames(new HashSet<>());
                }
                info.getObservedBioDisplayNames().add(displayName);
            }
        }
        private static String buildDisplayName(String genusDisp, String speciesDisp) {
            // If we have nothing, return null
            if (isEmpty(genusDisp) && isEmpty(speciesDisp)) {
                return null;
            }
            // If only one side is present, just use that
            if (isEmpty(genusDisp)) {
                return speciesDisp;
            }
            if (isEmpty(speciesDisp)) {
                return genusDisp;
            }

            String genusTrim = genusDisp.trim();
            String speciesTrim = speciesDisp.trim();

            // Species often already includes the genus, e.g. "Tussock Serrati" with genus "Tussock".
            // In that case, drop the leading genus so we don't get "Tussock Tussock Serrati".
            String[] parts = speciesTrim.split("\\s+");
            if (parts.length > 0 && parts[0].equalsIgnoreCase(genusTrim)) {
                if (parts.length == 1) {
                    // Species was just "Tussock" – effectively only genus
                    return genusTrim;
                }
                String epithets = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
                return genusTrim + " " + epithets;
            }

            // Normal case: species is just the epithet, e.g. "Serrati"
            return genusTrim + " " + speciesTrim;
        }

        private BodyInfo findOrCreateBodyByIdOrName(int bodyId, String bodyName) {
            if (bodyId >= 0) {
                BodyInfo info = bodies.get(bodyId);
                if (info == null) {
                    info = new BodyInfo();
                    info.setBodyId(bodyId);
                    info.setBodyName(bodyName);
                    bodies.put(bodyId, info);
                } else if (bodyName != null && !bodyName.isEmpty()
                        && (info.getBodyName() == null || info.getBodyName().isEmpty())) {
                    info.setBodyName(bodyName);
                }
                return info;
            }

            if (bodyName != null && !bodyName.isEmpty()) {
                for (BodyInfo b : bodies.values()) {
                    if (bodyName.equals(b.getBodyName())) {
                        return b;
                    }
                }
                BodyInfo info = new BodyInfo();
                info.setBodyId(-1);
                info.setBodyName(bodyName);
                // Use a synthetic key for "no numeric body ID"
                bodies.put(bodies.size() + 1_000_000, info);
                return info;
            }

            return null;
        }

        private static boolean isBeltOrRing(String bodyName) {
            if (bodyName == null) {
                return false;
            }
            String lower = bodyName.toLowerCase(Locale.ROOT);
            return lower.contains("belt cluster")
                    || lower.contains("belt ")
                    || lower.contains(" ring");
        }

        private static String toLower(String s) {
            return s == null ? "" : s.toLowerCase(Locale.ROOT);
        }

        private static boolean isEmpty(String s) {
            return s == null || s.isEmpty();
        }

        private static String firstNonEmpty(String a, String b) {
            if (!isEmpty(a)) {
                return a;
            }
            if (!isEmpty(b)) {
                return b;
            }
            return null;
        }
    }

    // ------------------------------------------------------------------------
    // Building BodyAttributes from BodyInfo
    // ------------------------------------------------------------------------

    private static BodyAttributes buildBodyAttributes(SystemAccumulator acc, BodyInfo b) {
        if (b == null) {
            return null;
        }

        PlanetType planetType = mapPlanetType(b.getPlanetClass());
        if (planetType == null) {
            return null;
        }

        AtmosphereType atmo = mapAtmosphereType(b.getAtmosphere());
        if (atmo == null) {
            atmo = AtmosphereType.UNKNOWN;
        }

        double gravity = (b.getGravityMS() != null) ? b.getGravityMS() : Double.NaN;
        if (Double.isNaN(gravity)) {
            return null;
        }

        double temp = (b.getSurfaceTempK() != null) ? b.getSurfaceTempK() : Double.NaN;
        if (Double.isNaN(temp)) {
            return null;
        }

        double tempMin = temp;
        double tempMax = temp;

        String volc = b.getVolcanism() != null ? b.getVolcanism() : "";
        boolean hasVolc = !volc.isEmpty()
                && !volc.toLowerCase(Locale.ROOT).contains("no volcanism");

        // --- Resolve parentStar + starClass (host star type) ---
        String parentStar = b.getParentStar();
        String starClass = null;

        Integer parentStarBodyId = b.getParentStarBodyId();
        if (parentStarBodyId != null && parentStarBodyId.intValue() >= 0) {
            BodyInfo star = null;
            if (acc != null && acc.bodies != null) {
                star = acc.bodies.get(parentStarBodyId);
            }

            if (star != null) {
                if (parentStar == null || parentStar.isEmpty()) {
                    parentStar = star.getBodyName();
                }
                starClass = star.getStarType();
            }
        }

        return new BodyAttributes(
                b.getBodyName(),
                b.getStarSystem(),
                b.getStarPos(),
                planetType,
                gravity,
                atmo,
                tempMin,
                tempMax,
                b.getSurfacePressure(),
                hasVolc,
                volc,
                null,   // atmosphereComponents
                null,   // orbitalPeriod
                null,   // distance
                null,   // guardian
                null,   // nebula
                parentStar,
                null,   // region
                starClass
        );
    }

    private static PlanetType mapPlanetType(String planetClassRaw) {
        if (planetClassRaw == null || planetClassRaw.isEmpty()) {
            return null;
        }
        String pc = planetClassRaw.toLowerCase(Locale.ROOT);

        if (pc.contains("rocky") && pc.contains("ice")) {
            return PlanetType.ROCKY_ICE;
        }
        if (pc.contains("rocky")) {
            return PlanetType.ROCKY;
        }
        if (pc.contains("icy")) {
            return PlanetType.ICY;
        }
        if (pc.contains("metal-rich") || pc.contains("metal rich")) {
            return PlanetType.METAL_RICH;
        }
        if (pc.contains("high metal content")) {
            return PlanetType.HIGH_METAL;
        }
        return PlanetType.OTHER;
    }

    private static AtmosphereType mapAtmosphereType(String atmosphereRaw) {
        if (atmosphereRaw == null) {
            return AtmosphereType.NONE;
        }
        String at = atmosphereRaw.toLowerCase(Locale.ROOT).trim();
        if (at.isEmpty() || at.contains("no atmosphere")) {
            return AtmosphereType.NONE;
        }
        if (at.contains("carbon dioxide")) {
            return AtmosphereType.CO2;
        }
        if (at.contains("oxygen")) {
            return AtmosphereType.OXYGEN;
        }
        if (at.contains("nitrogen")) {
            return AtmosphereType.NITROGEN;
        }
        if (at.contains("ammonia")) {
            return AtmosphereType.AMMONIA;
        }
        if (at.contains("methane")) {
            return AtmosphereType.METHANE;
        }
        if (at.contains("sulphur dioxide") || at.contains("sulfur dioxide")) {
            return AtmosphereType.SULPHUR_DIOXIDE;
        }
        if (at.contains("argon")) {
            return AtmosphereType.ARGON;
        }
        if (at.contains("neon")) {
            return AtmosphereType.NEON;
        }
        if (at.contains("water")) {
            return AtmosphereType.WATER;
        }
        if (at.contains("helium")) {
            return AtmosphereType.HELIUM;
        }
        return AtmosphereType.UNKNOWN;
    }

    // ------------------------------------------------------------------------
    // Audit case structure and comparison logic
    // ------------------------------------------------------------------------

    
    private static CachedBody findCachedBody(CachedSystem cs, BodyInfo b) {
        if (cs == null || cs.bodies == null || cs.bodies.isEmpty() || b == null) {
            return null;
        }

        int bodyId = b.getBodyId();
        String bodyName = b.getBodyName();

        CachedBody byId = null;
        CachedBody byName = null;

        for (CachedBody cb : cs.bodies) {
            if (cb == null) {
                continue;
            }
            if (bodyId != 0 && cb.bodyId == bodyId) {
                byId = cb;
                break;
            }
            if (byName == null && bodyName != null && cb.name != null && cb.name.equalsIgnoreCase(bodyName)) {
                byName = cb;
            }
        }

        if (byId != null) {
            return byId;
        }
        return byName;
    }

private static ExobiologyAuditCase buildAuditCase(String systemName,
                                                      long systemAddress,
                                                      BodyInfo b,
                                                      List<BioCandidate> predicted) {
        if (b.getObservedBioDisplayNames() == null || b.getObservedBioDisplayNames().isEmpty()) {
            return null;
        }

        // Normalize observed species (truth)
        Set<String> observedNorm = new LinkedHashSet<>();
        for (String raw : b.getObservedBioDisplayNames()) {
            String n = normalizeSpeciesName(raw);
            if (!n.isEmpty()) {
                observedNorm.add(n);
            }
        }
        if (observedNorm.isEmpty()) {
            return null;
        }

        // Normalize predicted species
        Set<String> predictedNorm = new LinkedHashSet<>();
        List<PredictedSpecies> predictedDetailed = new ArrayList<>();
        if (predicted == null) {
        	System.out.println("Predicted was null");
        	return null;
        }
        for (BioCandidate bc : predicted) {
            String display = bc.getDisplayName(); // "Genus Species"
            String n = normalizeSpeciesName(display);
            if (!n.isEmpty()) {
                predictedNorm.add(n);
            }
            predictedDetailed.add(new PredictedSpecies(
                    bc.getGenus(),
                    bc.getSpecies(),
                    display,
                    bc.getScore(),
                    bc.getBaseValue(),
                    ""//bc.getReason()
            ));
        }

        List<String> missingNorm = new ArrayList<>();
        List<String> missingNice = new ArrayList<>();
        for (String obs : observedNorm) {
            if (!predictedNorm.contains(obs)) {
                missingNorm.add(obs);
                String pretty = findOriginal(b.getObservedBioDisplayNames(), obs);
                missingNice.add(pretty != null ? pretty : obs);
            }
        }

        PlanetSnapshot planet = new PlanetSnapshot(
                b.getPlanetClass(),
                b.getAtmosphere(),
                b.getSurfaceTempK(),
                b.getGravityMS(),
                b.getVolcanism(),
                b.isLandable()
        );

        return new ExobiologyAuditCase(
                systemName,
                systemAddress,
                b.getBodyId(),
                b.getBodyName(),
                planet,
                new ArrayList<>(b.getObservedBioDisplayNames()),
                new ArrayList<>(observedNorm),
                predictedDetailed,
                missingNice,
                missingNorm
        );
    }

    private static String normalizeSpeciesName(String s) {
        if (s == null) {
            return "";
        }
        String trimmed = s.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        // Collapse multiple spaces, lowercase for matching
        String collapsed = trimmed.replaceAll("\\s+", " ");
        return collapsed.toLowerCase(Locale.ROOT);
    }

    private static String findOriginal(Set<String> originals, String normalized) {
        if (originals == null || originals.isEmpty()) {
            return null;
        }
        for (String s : originals) {
            if (normalizeSpeciesName(s).equals(normalized)) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------
    // DTOs for JSON output
    // ------------------------------------------------------------------------

    public static final class ExobiologyAuditCase {
        private final String systemName;
        private final long systemAddress;
        private final int bodyId;
        private final String bodyName;

        private final PlanetSnapshot planet;

        // Truth
        private final List<String> observedDisplayNames;
        private final List<String> observedNormalizedNames;

        // Predictions
        private final List<PredictedSpecies> predictedSpecies;

        // Observed-but-not-predicted
        private final List<String> missingObservedDisplayNames;
        private final List<String> missingObservedNormalizedNames;

        public ExobiologyAuditCase(String systemName,
                                   long systemAddress,
                                   int bodyId,
                                   String bodyName,
                                   PlanetSnapshot planet,
                                   List<String> observedDisplayNames,
                                   List<String> observedNormalizedNames,
                                   List<PredictedSpecies> predictedSpecies,
                                   List<String> missingObservedDisplayNames,
                                   List<String> missingObservedNormalizedNames) {
            this.systemName = systemName;
            this.systemAddress = systemAddress;
            this.bodyId = bodyId;
            this.bodyName = bodyName;
            this.planet = planet;
            this.observedDisplayNames = observedDisplayNames;
            this.observedNormalizedNames = observedNormalizedNames;
            this.predictedSpecies = predictedSpecies;
            this.missingObservedDisplayNames = missingObservedDisplayNames;
            this.missingObservedNormalizedNames = missingObservedNormalizedNames;
        }

        /**
         * A case is a "fail" if there is at least one observed species
         * that does not appear in the predicted set.
         */
        public boolean isFail() {
            return missingObservedNormalizedNames != null
                    && !missingObservedNormalizedNames.isEmpty();
        }
    }

    public static final class PlanetSnapshot {
        private final String planetClass;
        private final String atmosphere;
        private final Double surfaceTempK;
        private final Double gravityMS;
        private final String volcanism;
        private final boolean landable;

        public PlanetSnapshot(String planetClass,
                              String atmosphere,
                              Double surfaceTempK,
                              Double gravityMS,
                              String volcanism,
                              boolean landable) {
            this.planetClass = planetClass;
            this.atmosphere = atmosphere;
            this.surfaceTempK = surfaceTempK;
            this.gravityMS = gravityMS;
            this.volcanism = volcanism;
            this.landable = landable;
        }
    }

    public static final class PredictedSpecies {
        private final String genus;
        private final String species;
        private final String displayName;
        private final double score;
        private final long baseValue;
        private final String reason;

        public PredictedSpecies(String genus,
                                String species,
                                String displayName,
                                double score,
                                long baseValue,
                                String reason) {
            this.genus = genus;
            this.species = species;
            this.displayName = displayName;
            this.score = score;
            this.baseValue = baseValue;
            this.reason = reason;
        }
    }
}
