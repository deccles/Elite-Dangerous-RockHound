package org.dce.ed.exobiology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.dce.ed.exobiology.RegionMapData;
import org.dce.ed.exobiology.ExobiologyData.SpeciesConstraint;

/**
 * Exobiology prediction helper using rulesets derived from the
 * open-source ruleset catalog.
 *
 * Species rules are stored as a set of SpeciesRule entries per species.
 * A species is considered valid for a body if ANY of its rules match.
 */
public final class ExobiologyData {

    private ExobiologyData() {
    }

    /* =====================================================================
     * Enums
     * ===================================================================== */

    public enum PlanetType {
        ROCKY,
        METAL_RICH,
        HIGH_METAL,
        ROCKY_ICE,
        ICY,
        OTHER,
        UNKNOWN
    }

    public enum AtmosphereType {
        NONE,

        CO2,
        CO2_RICH,

        METHANE,
        METHANE_RICH,

        NITROGEN,
        NITROGEN_RICH,

        OXYGEN,
        OXYGEN_RICH,

        NEON,
        NEON_RICH,

        ARGON,
        ARGON_RICH,

        WATER,
        WATER_RICH,

        SULPHUR_DIOXIDE,
        SULPHUR_DIOXIDE_RICH,

        AMMONIA,
        AMMONIA_RICH,

        HELIUM,
        OTHER,
        UNKNOWN
    }

    /**
     * Volcanism constraint used by a single rule.
     */
    public enum VolcanismRequirement {
        ANY,
        NO_VOLCANISM,
        VOLCANIC_ONLY
    }

    /* =====================================================================
     * BodyAttributes
     * ===================================================================== */

    

    /* =====================================================================
     * Rules and constraints
     * ===================================================================== */

    /**
     * One ruleset row for a given {genus, species}, mapped from the Python
     * rulesets entry.
     *
     * Missing dimensions in the source ruleset are represented as:
     *  - gravity:   [0, 100] (effectively "no constraint")
     *  - temp:      [0, 1_000_000]
     *  - pressure:  [0, 1_000_000]
     *  - atmospheres: empty set  => no restriction
     *  - bodyTypes:  empty set   => no restriction
     *  - volcanism:  ANY
     *
     * Additional fields (orbital period, distance, regions, guardian, nebula,
     * parentStars, starClasses, tuberTargets, atmosphereComponents, bodies)
     * are stored on the rule but are not currently enforced in {@link #matches}
     * because {@link BodyAttributes} does not yet expose matching data. They
     * are present so the full ruleset can be represented and used later.
     */
    public static final class SpeciesRule {

        public final Double minGravity;
        public final Double maxGravity;
        public final Double minTempK;
        public final Double maxTempK;

        public final Double minPressure;
        public final Double maxPressure;

        public final Set<AtmosphereType> atmospheres;
        public final Set<PlanetType> bodyTypes;

        /** If true, ruleset atmosphere is 'Any' which means the body must have an atmosphere (not airless). */
        public final boolean requireAtmosphere;

        /** Optional detailed atmosphere components (e.g. {"CO2": 0.8}). */
        public final Map<String, Double> atmosphereComponents;

        /** Specific host body names, when the ruleset targets named bodies. */
        public final List<String> bodies;

        /** Optional maximum orbital period (units as in the source ruleset). */
        public final Double maxOrbitalPeriod;

        /** Optional distance constraint (units as in the source ruleset). */
        public final Double distance;

        /** Optional guardian-only constraint (true = guardian only, false = explicitly non-guardian). */
        public final Boolean guardian;

        /** Optional nebula constraint (e.g. "all", "!orion-cygnus-core"). */
        public final String nebula;

        /** Optional list of allowed parent star categories. */
        public final List<String> parentStars;

        /** Optional galactic-region constraints. */
        public final List<String> regions;

        /** Optional allowed star classes (e.g. "B IV", "O"). */
        public final List<String> starClasses;

        /** Optional “tuber” anchor constraints. */
        public final List<String> tuberTargets;

        /** Volcanism constraint value (e.g. 'Any', 'None', '!water'). Null if not specified. */
        public final String volcanismValue;

        /** Volcanism any-of list (substring match, '=' prefix for exact). Null/empty if not specified. */
        public final List<String> volcanismAnyOf;

        public final VolcanismRequirement volcanismRequirement;

        public SpeciesRule(Double minGravity,
                           Double maxGravity,
                           Double minTempK,
                           Double maxTempK,
                           Double minPressure,
                           Double maxPressure,
                           Set<AtmosphereType> atmospheres,
                           Set<PlanetType> bodyTypes,
                           boolean requireAtmosphere,
                           Map<String, Double> atmosphereComponents,
                           List<String> bodies,
                           Double maxOrbitalPeriod,
                           Double distance,
                           Boolean guardian,
                           String nebula,
                           List<String> parentStars,
                           List<String> regions,
                           List<String> starClasses,
                           List<String> tuberTargets,
                           String volcanismValue,
                           List<String> volcanismAnyOf,
                           VolcanismRequirement volcanismRequirement) {

            this.minGravity = minGravity;
            this.maxGravity = maxGravity;
            this.minTempK = minTempK;
            this.maxTempK = maxTempK;
            this.minPressure = minPressure;
            this.maxPressure = maxPressure;
            this.atmospheres = atmospheres;
            this.bodyTypes = bodyTypes;
            this.requireAtmosphere = requireAtmosphere;
            this.atmosphereComponents = atmosphereComponents;
            this.bodies = bodies;
            this.maxOrbitalPeriod = maxOrbitalPeriod;
            this.distance = distance;
            this.guardian = guardian;
            this.nebula = nebula;
            this.parentStars = parentStars;
            this.regions = regions;
            this.starClasses = starClasses;
            this.tuberTargets = tuberTargets;
            this.volcanismValue = volcanismValue;
            this.volcanismAnyOf = volcanismAnyOf;
            this.volcanismRequirement = volcanismRequirement;
        }

        /**
         * Returns true if this rule considers the body a valid habitat.
         *
         * Right now this only checks the dimensions we actually
         * have in BodyAttributes: planetType, atmosphere, gravity,
         * temperature, and coarse volcanism. The extra fields
         * (pressure, regions, guardian, etc.) are stored but ignored
         * until BodyAttributes carries matching data and we decide
         * how to enforce them.
         */
		public boolean matches(String name, BodyAttributes body) {
            if (body == null) {
                warn("BodyAttributes was null");
                return false;
            }
            
            if (!bodyTypes.isEmpty() && (body.planetType == null || !bodyTypes.contains(body.planetType))) {
                return false;
            }

            AtmosphereType at = body.atmosphere != null ? body.atmosphere : AtmosphereType.UNKNOWN;

            if (requireAtmosphere) {
                if (at == AtmosphereType.NONE || at == AtmosphereType.UNKNOWN) {
                    return false;
                }
            }

            if (!atmospheres.isEmpty()) {
                if (!atmospheres.contains(at)) {
                    return false;
                }
            }

            if (minGravity != null || maxGravity != null) {
                double g = body.gravity;
                if (Double.isNaN(g)) {
                    warn("Missing gravity for rule that requires gravity range");
                    return false;
                }
                if (minGravity != null && g < minGravity) {
                    return false;
                }
                if (maxGravity != null && g > maxGravity) {
                    return false;
                }
            }

            if (minTempK != null || maxTempK != null) {
                double t = body.tempKMin;
                if (!Double.isNaN(t)) {
                    if (minTempK != null && t < minTempK) {
                        return false;
                    }
                    if (maxTempK != null && t > maxTempK) {
                        return false;
                    }
                } else {
                    warn("Missing temperature for rule that requires temperature range");
                    return false;
                }
            }

            if (minPressure != null || maxPressure != null) {
                double p = body.pressure != null ? body.pressure.doubleValue() : Double.NaN;
                if (!Double.isNaN(p)) {
                    if (minPressure != null && p < minPressure) {
                        return false;
                    }
                    if (maxPressure != null && p >= maxPressure) {
                        return false;
                    }
                } else {
                    warn("Missing pressure for rule that requires pressure range");
                    return false;
                }
            }

            if (atmosphereComponents != null && !atmosphereComponents.isEmpty()) {
                Map<String, Double> comps = body.atmosphereComponents != null ? body.atmosphereComponents : Collections.emptyMap();
                for (Map.Entry<String, Double> e : atmosphereComponents.entrySet()) {
                    String gas = e.getKey();
                    Double minPct = e.getValue();
                    if (minPct == null) {
                        continue;
                    }
                    double have = comps.getOrDefault(gas, 0.0);
                    if (have < minPct) {
                        return false;
                    }
                }
            }

            if (bodies != null && !bodies.isEmpty()) {
                if (body.bodyName == null || body.bodyName.trim().isEmpty()) {
                    warn("Rule restricts to bodies but bodyName was not provided");
                    return false;
                }
                if (!bodies.contains(body.bodyName)) {
                    return false;
                }
            }

            if (maxOrbitalPeriod != null) {
                if (body.orbitalPeriod == null) {
                    warn("Rule requires orbital period but body.orbitalPeriod was null");
                    return false;
                }
                if (body.orbitalPeriod >= maxOrbitalPeriod) {
                    return false;
                }
            }

            if (distance != null) {
                if (body.distance == null) {
                    warn("Rule requires distance but body.distance was null");
                    return false;
                }
                if (body.distance < distance) {
                    return false;
                }
            }

            if (guardian != null) {
                if (body.guardian == null) {
//                    warn("Rule requires guardian flag but body.guardian was null");
                    return false;
                }
                if (!guardian.equals(body.guardian)) {
                    return false;
                }
            }

            if (nebula != null) {
                if (body.nebula == null) {
                    warn("Rule requires nebula constraint but body.nebula was null");
                    return false;
                }
                if (nebula.startsWith("!")) {
                    String n = nebula.substring(1);
                    if (body.nebula.equalsIgnoreCase(n)) {
                        return false;
                    }
                } else if (!nebula.equalsIgnoreCase("all") && !nebula.equalsIgnoreCase("large")) {
                    if (!body.nebula.equalsIgnoreCase(nebula)) {
                        return false;
                    }
                } else {
                    if (body.nebula.trim().isEmpty()) {
                        return false;
                    }
                }
            }

            if (parentStars != null && !parentStars.isEmpty()) {
                if (body.parentStar == null) {
                    warn("Rule requires parent star constraint but body.parentStar was null");
                    return false;
                }
                if (!parentStars.contains(body.parentStar)) {
                    return false;
                }
            }

            if (regions != null && !regions.isEmpty()) {


            	double[] starPos = body.starPos;
            	if (starPos == null || starPos.length < 2) {
            		warn("Rule requires region constraint but body.starPos was null/short");
            		return false;
            	}
            	int regionId = RegionResolver.findRegionId(starPos[0], starPos[2]);
            	String regionName = RegionMapData.REGIONS[regionId].toLowerCase().replaceAll(" ", "-");

            	if (regions != null && !regions.isEmpty()) {
            		if (regionName == null) {
            			warn("Rule requires region constraint but body.region was null");
            			return false;
            		}
            		if (!RegionSlugMatcher.matchesAnySlug(regionId, regions)) {
            			return false;
            		}
            	}
            }
            
            if (starClasses != null && !starClasses.isEmpty()) {
                if (body.starType == null) {
                    warn("Rule requires star class constraint but body.starClass was null " + body.starSystem + " " + body.bodyName);
                    return false;
                }
                if (!starClasses.contains(body.starType)) {
                    return false;
                }
            }

            String bodyVolc = body.volcanismType != null ? body.volcanismType : "";
            boolean hasVolc = body.hasVolcanism;

            if (volcanismAnyOf != null && !volcanismAnyOf.isEmpty()) {
                boolean found = false;
                for (String volcType : volcanismAnyOf) {
                    if (volcType == null) {
                        continue;
                    }
                    if (volcType.startsWith("=")) {
                        if (bodyVolc.equalsIgnoreCase(volcType.substring(1))) {
                            found = true;
                            break;
                        }
                    } else {
                        if (bodyVolc.toLowerCase(Locale.ROOT).contains(volcType.toLowerCase(Locale.ROOT))) {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    return false;
                }
            } else if (volcanismValue != null) {
                String v = volcanismValue;
                if ("Any".equalsIgnoreCase(v)) {
                    if (!hasVolc || bodyVolc.isEmpty()) {
                        return false;
                    }
                } else if ("None".equalsIgnoreCase(v)) {
                    if (hasVolc && !bodyVolc.isEmpty()) {
                        return false;
                    }
                } else if (v.startsWith("!")) {
                    String sub = v.substring(1);
                    if (!hasVolc || bodyVolc.isEmpty()
                            || bodyVolc.toLowerCase(Locale.ROOT).contains(sub.toLowerCase(Locale.ROOT))) {
                        return false;
                    }
                } else if (v.startsWith("=")) {
                    if (!bodyVolc.equalsIgnoreCase(v.substring(1))) {
                        return false;
                    }
                } else {
                    if (!hasVolc || bodyVolc.isEmpty()
                            || !bodyVolc.toLowerCase(Locale.ROOT).contains(v.toLowerCase(Locale.ROOT))) {
                        return false;
                    }
                }
            } else {
                switch (volcanismRequirement) {
                    case NO_VOLCANISM:
                        if (body.hasVolcanism) {
                            return false;
                        }
                        break;
                    case VOLCANIC_ONLY:
                        if (!body.hasVolcanism) {
                            return false;
                        }
                        break;
                    case ANY:
                    default:
                        break;
                }
            }

            return true;
        }

        /**
         * Builder for SpeciesRule so that the generated Java from the Python
         * rulesets can stay readable and only specify the fields that matter
         * for a given rule.
         */
        public static final class SpeciesRuleBuilder {

            private Double minGravity, maxGravity;
            private Double minTemp, maxTemp;
            private Double minPressure, maxPressure;

            private boolean requireAtmosphere;

            private String volcanismValue;
            private final List<String> volcanismAnyOf = new ArrayList<>();

            private final Set<AtmosphereType> atmos = new HashSet<>();
            private final Set<PlanetType> planets = new HashSet<>();

            private Map<String, Double> atmosphereComponents;
            private List<String> bodies;
            private Double maxOrbitalPeriod;
            private Double distance;
            private Boolean guardian;
            private String nebula;
            private List<String> parentStars;
            private List<String> regions;
            private List<String> stars;
            private List<String> tubers;

            private VolcanismRequirement volc;

            public static SpeciesRuleBuilder create() {
                return new SpeciesRuleBuilder();
            }

            public SpeciesRuleBuilder gravity(double min, double max) {
                this.minGravity = min;
                this.maxGravity = max;
                return this;
            }

            public SpeciesRuleBuilder temperature(double minK, double maxK) {
                this.minTemp = minK;
                this.maxTemp = maxK;
                return this;
            }

            public SpeciesRuleBuilder pressure(double min, double max) {
                this.minPressure = min;
                this.maxPressure = max;
                return this;
            }

public SpeciesRuleBuilder gravity(Double min, Double max) {
                this.minGravity = min;
                this.maxGravity = max;
                return this;
            }

            public SpeciesRuleBuilder temperature(Double minK, Double maxK) {
                this.minTemp = minK;
                this.maxTemp = maxK;
                return this;
            }

            public SpeciesRuleBuilder pressure(Double min, Double max) {
                this.minPressure = min;
                this.maxPressure = max;
                return this;
            }

            public SpeciesRuleBuilder atmospheres(AtmosphereType... types) {
                if (types != null && types.length > 0) {
                    atmos.addAll(Arrays.asList(types));
                }
                return this;
            }

            public SpeciesRuleBuilder planetTypes(PlanetType... types) {
                if (types != null && types.length > 0) {
                    planets.addAll(Arrays.asList(types));
                }
                return this;
            }

            public SpeciesRuleBuilder atmosphereComponents(Map<String, Double> components) {
                this.atmosphereComponents = components;
                return this;
            }

            public SpeciesRuleBuilder bodies(List<String> bodies) {
                this.bodies = bodies;
                return this;
            }

            public SpeciesRuleBuilder maxOrbitalPeriod(Double maxOrbitalPeriod) {
                this.maxOrbitalPeriod = maxOrbitalPeriod;
                return this;
            }

            public SpeciesRuleBuilder distance(Double distance) {
                this.distance = distance;
                return this;
            }

            public SpeciesRuleBuilder guardian(Boolean guardian) {
                this.guardian = guardian;
                return this;
            }

            public SpeciesRuleBuilder nebula(String nebula) {
                this.nebula = nebula;
                return this;
            }

            public SpeciesRuleBuilder parentStars(List<String> parentStars) {
                this.parentStars = parentStars;
                return this;
            }

            public SpeciesRuleBuilder regions(List<String> regions) {
                this.regions = regions;
                return this;
            }

            public SpeciesRuleBuilder stars(List<String> stars) {
                this.stars = stars;
                return this;
            }

            public SpeciesRuleBuilder tubers(List<String> tubers) {
                this.tubers = tubers;
                return this;
            }

            public SpeciesRuleBuilder volcanism(VolcanismRequirement requirement) {
                this.volc = requirement;
                return this;
            }

            
            public SpeciesRuleBuilder requireAtmosphere() {
                this.requireAtmosphere = true;
                return this;
            }

            public SpeciesRuleBuilder volcanism(String value) {
                this.volcanismValue = value;
                return this;
            }

            public SpeciesRuleBuilder volcanismAnyOf(String... values) {
                if (values != null) {
                    for (String v : values) {
                        if (v != null) {
                            this.volcanismAnyOf.add(v);
                        }
                    }
                }
                return this;
            }

            public SpeciesRule build() {
                Double minG = minGravity;
                Double maxG = maxGravity;

                Double minT = minTemp;
                Double maxT = maxTemp;

                Double minP = minPressure;
                Double maxP = maxPressure;

                Set<AtmosphereType> atmoSet =
                        atmos.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(atmos));
                Set<PlanetType> bodySet =
                        planets.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(planets));

                VolcanismRequirement v = volc != null ? volc : VolcanismRequirement.ANY;

                List<String> volcanismAnyOfList =
                        volcanismAnyOf.isEmpty()
                                ? Collections.<String>emptyList()
                                : Collections.unmodifiableList(new ArrayList<>(volcanismAnyOf));
                
                Map<String, Double> ac =
                        atmosphereComponents == null
                                ? Collections.<String, Double>emptyMap()
                                : Collections.unmodifiableMap(new LinkedHashMap<>(atmosphereComponents));

                List<String> bodiesList =
                        bodies == null
                                ? Collections.<String>emptyList()
                                : Collections.unmodifiableList(new ArrayList<>(bodies));

                List<String> parentStarList =
                        parentStars == null
                                ? Collections.<String>emptyList()
                                : Collections.unmodifiableList(new ArrayList<>(parentStars));

                List<String> regionList =
                        regions == null
                                ? Collections.<String>emptyList()
                                : Collections.unmodifiableList(new ArrayList<>(regions));

                List<String> starClassList =
                        stars == null
                                ? Collections.<String>emptyList()
                                : Collections.unmodifiableList(new ArrayList<>(stars));

                List<String> tuberList =
                        tubers == null
                                ? Collections.<String>emptyList()
                                : Collections.unmodifiableList(new ArrayList<>(tubers));

                return new SpeciesRule(
                        minG,
                        maxG,
                        minT,
                        maxT,
                        minP,
                        maxP,
                        atmoSet,
                        bodySet,
                        requireAtmosphere,
                        ac,
                        bodiesList,
                        maxOrbitalPeriod,
                        distance,
                        guardian,
                        nebula,
                        parentStarList,
                        regionList,
                        starClassList,
                        tuberList,
                        volcanismValue,
                        volcanismAnyOfList,
                        v
                );

            }
        }
    }

    /**
     * Constraint block for a single {genus, species}.
     * Contains the Vista value and a list of SpeciesRule entries.
     */
    /**
     * Constraint block for a single {genus, species}.
     * Contains the Vista value and a list of SpeciesRule entries.
     */
    public static final class SpeciesConstraint {

        private final String genus;
        private final String species;
        private final long baseValue;
        private final List<SpeciesRule> rules;

        public SpeciesConstraint(String genus,
                                 String species,
                                 long baseValue,
                                 List<SpeciesRule> rules) {
            this.genus = genus;
            this.species = species;
            this.baseValue = baseValue;
            this.rules = rules;
        }

        public String getGenus() {
            return genus;
        }

        public String getSpecies() {
            return species;
        }

        public long getBaseValue() {
            return baseValue;
        }

        public List<SpeciesRule> getRules() {
            return rules;
        }

        /**
         * Key used in the constraints map.
         */
        public String key() {
            return genus + " " + species;
        }
    }

    /* =====================================================================
     * BioCandidate
     * ===================================================================== */

    /**
     * A scored candidate prediction for a given body.
     */
    public static final class BioCandidate {

//        private final SpeciesConstraint constraint;
        private final double score;
//        private final String reason;
        public String genus = "";
        public String species = "";
        public Long baseValue;
        
        public BioCandidate(SpeciesConstraint constraint, double score, String reason) {
        	genus = constraint.getGenus();
        	species = constraint.getSpecies();
        	baseValue = constraint.getBaseValue();
        	
            this.score = score;
//            this.reason = reason;
        }

        /** For Gson deserialization when loading predictions from cache. */
        @SuppressWarnings("unused")
        public BioCandidate() {
            this.score = 0.0;
        }

        public String getGenus() {
            return genus;
        }

        public String getSpecies() {
            return species;
        }

        public String getDisplayName() {
            return genus + " " + species;
        }

        public long getBaseValue() {
            return baseValue;
        }

        public double getScore() {
            return score;
        }

//        public String getReason() {
//            return reason;
//        }

//        public SpeciesConstraint getConstraint() {
//            return constraint;
//        }

        /**
         * Simple estimated payout based on Vista base value.
         * If you want a different multiplier for first discovery, change it here.
         */
        public long getEstimatedPayout(boolean firstDiscovery) {
            if (firstDiscovery) {
                return baseValue * 5L;  // rough ED-style bump
            }
            return baseValue;
        }

        @Override
        public String toString() {
            return "BioCandidate{" +
                    "name='" + getDisplayName() + '\'' +
                    ", score=" + score +
                    ", value=" + baseValue +
//                    ", reason='" + reason + '\'' +
                    '}';
        }
    }

    /* =====================================================================
     * Database and initialization
     * ===================================================================== */

    private static final Map<String, SpeciesConstraint> CONSTRAINTS = new LinkedHashMap<>();

    

    private static Consumer<String> WARNING_SINK = msg -> System.err.println("[ExobiologyData] " + msg);

    public static void setWarningSink(Consumer<String> sink) {
        WARNING_SINK = sink != null ? sink : (msg -> System.err.println("[ExobiologyData] " + msg));
    }

    private static void warn(String msg) {
        try {
            WARNING_SINK.accept(msg);
        } catch (Exception ignored) {
            // never let warnings break predictions
        }
    }

    static {
        initConstraints();
    }

    /**
     * Populates the CONSTRAINTS map.
     *
     * IMPORTANT:
     *  Replace the body of this method with the contents of your
     *  generated file: ExobiologyData_initConstraints.txt
     *  (everything between "private static void initConstraints() {" and
     *  its closing brace).
     */
    private static void initConstraints() {
    	ExobiologyDataConstraints.initConstraints(CONSTRAINTS);
    }

    /* =====================================================================
     * Prediction
     * ===================================================================== */
    public static List<BioCandidate> predict(BodyAttributes attrs, Set<String> allowedSpeciesKeys) {
        if (allowedSpeciesKeys == null || allowedSpeciesKeys.isEmpty()) {
            return predict(attrs);
        }

        if (attrs == null) {
            return Collections.emptyList();
        }

        List<BioCandidate> result = new ArrayList<>();

        for (SpeciesConstraint sc : CONSTRAINTS.values()) {
            String key = sc.genus + " " + sc.species;
            if (!allowedSpeciesKeys.contains(key)) {
                continue;
            }

            SpeciesRule bestRule = null;
            double bestScore = 0.0;
            String bestReason = null;

            for (SpeciesRule rule : sc.getRules()) {
                if (!rule.matches(key, attrs)) {
                    continue;
                }

                Double gScore = scoreInRange(attrs.gravity, rule.minGravity, rule.maxGravity);
                double tScore = scoreInRange(
                        0.5 * (attrs.tempKMin + attrs.tempKMax),
                        rule.minTempK,
                        rule.maxTempK
                );

                double score = 0.5 * (gScore + tScore);

                String reason = String.format(
                        Locale.ROOT,
                        "gravity=%.3f (%.3f–%.3f); temp=%.0f–%.0f K (%.0f–%.0f); atmo=%s; score=%.3f",
                        attrs.gravity, rule.minGravity, rule.maxGravity,
                        attrs.tempKMin, attrs.tempKMax,
                        rule.minTempK, rule.maxTempK,
                        attrs.atmosphere,
                        score
                );

                if (bestRule == null || score > bestScore) {
                    bestRule = rule;
                    bestScore = score;
                    bestReason = reason;
                }
            }

            if (bestRule != null) {
                result.add(new BioCandidate(sc, bestScore, bestReason));
            }
        }

        result.sort(
                Comparator.comparingDouble(BioCandidate::getScore).reversed()
                        .thenComparingLong(BioCandidate::getBaseValue).reversed()
        );

        return result;
    }

    
    /**
     * Predict possible exobiology candidates for the given body.
     * Returns a list sorted by descending score and then baseValue.
     */
    public static List<BioCandidate> predict(BodyAttributes attrs) {
        if (attrs == null) {
            return Collections.emptyList();
        }
        List<BioCandidate> result = new ArrayList<>();

        for (SpeciesConstraint sc : CONSTRAINTS.values()) {
            SpeciesRule bestRule = null;
            double bestScore = 0.0;
            String bestReason = null;

            for (SpeciesRule rule : sc.getRules()) {
                if (!rule.matches(sc.genus + " " + sc.species, attrs)) {
                    continue;
                }

                Double gScore = scoreInRange(attrs.gravity, rule.minGravity, rule.maxGravity);
                double tScore = scoreInRange(
                        0.5 * (attrs.tempKMin + attrs.tempKMax),
                        rule.minTempK,
                        rule.maxTempK
                );

                double score = 0.5 * (gScore + tScore);

                String reason = String.format(
                        Locale.ROOT,
                        "gravity=%.3f (%.3f–%.3f); temp=%.0f–%.0f K (%.0f–%.0f); atmo=%s; score=%.3f",
                        attrs.gravity, rule.minGravity, rule.maxGravity,
                        attrs.tempKMin, attrs.tempKMax,
                        rule.minTempK, rule.maxTempK,
                        attrs.atmosphere,
                        score
                );

                if (bestRule == null || score > bestScore) {
                    bestRule = rule;
                    bestScore = score;
                    bestReason = reason;
                }
            }

            if (bestRule != null) {
                result.add(new BioCandidate(sc, bestScore, bestReason));
            }
        }

        // Sort by score, then by Vista value
        result.sort(
                Comparator.comparingDouble(BioCandidate::getScore).reversed()
                        .thenComparingLong(BioCandidate::getBaseValue).reversed()
        );

        return result;
    }

    /**
     * Simple helper to score how "central" x is within [min,max].
     * 1.0 at the midpoint, 0.0 at or outside the bounds.
     */
    private static double scoreInRange(Double x, Double min, Double max) {
    	if (x == null)
    		return 0.0;
    	
    	if (min == null || max == null)
    		return 0.5;
    	
        if (x < min || x > max) {
            return 0.0;
        }
        if (max <= min) {
            return 1.0;
        }
        double mid = 0.5 * (min + max);
        double half = 0.5 * (max - min);
        if (half <= 0.0) {
            return 1.0;
        }
        return 1.0 - Math.abs(x - mid) / half;
    }

    /* =====================================================================
     * Parsing helpers used by BodyInfo / SystemEventProcessor
     * ===================================================================== */

    public static PlanetType parsePlanetType(String planetClassRaw) {
        if (planetClassRaw == null || planetClassRaw.isEmpty()) {
            return PlanetType.UNKNOWN;
        }
        String pc = planetClassRaw.toLowerCase(Locale.ROOT);

        if (pc.contains("rocky ice")) {
            return PlanetType.ROCKY_ICE;
        }
        if (pc.contains("icy body") || pc.contains("icy world")) {
            return PlanetType.ICY;
        }
        if (pc.contains("metal-rich") || pc.contains("metal rich")) {
            return PlanetType.METAL_RICH;
        }
        if (pc.contains("high metal content")) {
            return PlanetType.HIGH_METAL;
        }
        if (pc.contains("rocky body") || pc.contains("rocky world")) {
            return PlanetType.ROCKY;
        }

        return PlanetType.OTHER;
    }

    public static AtmosphereType parseAtmosphere(String atmosphereRaw) {
        if (atmosphereRaw == null) {
            return AtmosphereType.UNKNOWN;
        }
        String at = atmosphereRaw.toLowerCase(Locale.ROOT).trim();
        if (at.isEmpty()
                || at.equals("none")
                || at.contains("no atmosphere")) {
            return AtmosphereType.NONE;
        }

        if (at.contains("carbon dioxide")) {
            if (at.contains("rich")) {
                return AtmosphereType.CO2_RICH;
            }
            return AtmosphereType.CO2;
        }
        if (at.contains("methane")) {
            if (at.contains("rich")) {
                return AtmosphereType.METHANE_RICH;
            }
            return AtmosphereType.METHANE;
        }
        if (at.contains("nitrogen")) {
            if (at.contains("rich")) {
                return AtmosphereType.NITROGEN_RICH;
            }
            return AtmosphereType.NITROGEN;
        }
        if (at.contains("oxygen")) {
            if (at.contains("rich")) {
                return AtmosphereType.OXYGEN_RICH;
            }
            return AtmosphereType.OXYGEN;
        }
        if (at.contains("neon")) {
            if (at.contains("rich")) {
                return AtmosphereType.NEON_RICH;
            }
            return AtmosphereType.NEON;
        }
        if (at.contains("argon")) {
            if (at.contains("rich")) {
                return AtmosphereType.ARGON_RICH;
            }
            return AtmosphereType.ARGON;
        }
        if (at.contains("water")) {
            if (at.contains("rich")) {
                return AtmosphereType.WATER_RICH;
            }
            return AtmosphereType.WATER;
        }
        if (at.contains("sulphur dioxide") || at.contains("sulfur dioxide")) {
            return AtmosphereType.SULPHUR_DIOXIDE;
        }
        if (at.contains("ammonia")) {
            if (at.contains("rich")) {
                return AtmosphereType.AMMONIA_RICH;
            }
            return AtmosphereType.AMMONIA;
        }
        if (at.contains("helium")) {
            return AtmosphereType.HELIUM;
        }

        return AtmosphereType.OTHER;
    }

    /* =====================================================================
     * Utility lookup (optional)
     * ===================================================================== */

    public static SpeciesConstraint getConstraintFor(String genus, String species) {
        if (genus == null || species == null) {
            return null;
        }
        return CONSTRAINTS.get(genus + " " + species);
    }

    public static Long estimatePayout(String genusLocalised, String speciesLocalised, boolean firstDiscoveryBonus) {
    	
    	if (speciesLocalised.startsWith(genusLocalised)) {
    		speciesLocalised = speciesLocalised.replaceFirst(genusLocalised, "").trim();
    	}
        SpeciesConstraint c = getConstraintFor(genusLocalised, speciesLocalised);
        if (c == null) {
            return null;
        }

        long base = c.getBaseValue();
        if (firstDiscoveryBonus) {
            return base * 5L;
        }
        return base;
    }

    public static Map<String, SpeciesConstraint> getAllConstraints() {
        return Collections.unmodifiableMap(CONSTRAINTS);
    }
}
