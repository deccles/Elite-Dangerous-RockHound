package org.dce.ed.tts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dce.ed.OverlayPreferences;
import org.dce.ed.exobiology.ExobiologyData.SpeciesConstraint;
import org.dce.ed.exobiology.ExobiologyDataConstraints;
import org.dce.ed.market.GalacticAveragePrices;

import software.amazon.awssdk.services.polly.model.VoiceId;

/**
 * Developer helper that exercises the speech system to pre-populate the TTS cache
 * (including MID vs END variants).
 *
 * Intended usage:
 *   VoiceCacheWarmer.warmAll("Joanna");
 *
 * <p>Warms every commodity display name from {@link org.dce.ed.market.GalacticAveragePrices} (bundled INARA CSV),
 * all exobiology genus/species pairs, and prospector list phrases for adjacent CSV-ordered pairs/triples so offline
 * voice packs cover Tritium, multi-word materials, and common multi-material prospector lines.
 *
 * Or from the command line (voice is matched case-insensitively to a Polly {@link VoiceId}):
 *   java ... org.dce.ed.tts.VoiceCacheWarmer salli
 *   java ... org.dce.ed.tts.VoiceCacheWarmer salli -create
 *   java ... org.dce.ed.tts.VoiceCacheWarmer all -create
 *   java ... org.dce.ed.tts.VoiceCacheWarmer all -deploy 1.0.1
 * Use {@code all} to warm every voice in {@link PollyTtsCached#STANDARD_US_ENGLISH_VOICES}. With
 * {@code -create} (or {@code --create}), also writes {@code target/voice-&lt;voice&gt;.zip} per voice.
 * With {@code -deploy &lt;releaseTag&gt;} (after zips exist), runs {@code gh release upload} only for the
 * pack zip(s) built in that run (one voice → one zip; {@code all} → one zip per standard voice) to
 * {@code deccles/Elite-Dangerous-RockHound} (implies {@code -create}).
 * If {@code gh} is not on the JVM's {@code PATH} (common on Windows), set {@code -Dedo.ghPath=...} or
 * {@code EDO_GH_PATH} to {@code gh.exe}, or rely on the standard GitHub CLI install locations.
 *
 * <p><b>Parallelism:</b> warming runs several worker threads (default {@code min(8, availableProcessors)},
 * at least 2). Override with JVM flag {@code -Dedo.voiceWarmParallelism=N}. Polly rate limits may require lowering N.
 */
public final class VoiceCacheWarmer {

    private static final Pattern SPEAKF_LITERAL = Pattern.compile("\\.speakf(?:Blocking)?\\s*\\(\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.MULTILINE);
    /** Preferences preview uses {@code speakfWithSpeechGate(boolean, "template", args...)} — capture the template string. */
    private static final Pattern SPEAKF_WITH_GATE_LITERAL = Pattern.compile(
            "\\.speakfWithSpeechGate\\s*\\(\\s*(?:[^,]+),\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
            Pattern.MULTILINE);
    private static final Pattern SPEAK_LITERAL = Pattern.compile("\\.speak(?:Blocking)?\\s*\\(\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.MULTILINE);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");
    /**
     * Templates not discoverable by scraping {@code .speakf("...")} literals (e.g. constant references).
     *
     * <p><b>Maintainer / agent:</b> when adding a line here (or any new runtime speech), also bump
     * {@link VoicePackManager#SPEECH_PACK_REVISION}, warm packs, and publish new {@code voice-*.zip}
     * assets so offline clients auto-refresh on next startup.
     */
    private static final Set<String> REQUIRED_WARMUP_TEMPLATES = Set.of(
            "First Discovered System",
            "Did you forget to assign your fighter pilot again, commander?");

    @FunctionalInterface
    private interface ItemWarm<T> {
        void accept(T item) throws Exception;
    }

    private VoiceCacheWarmer() {
    }

    static int warmParallelism() {
        Integer prop = Integer.getInteger(VOICE_WARM_PARALLELISM_PROPERTY);
        if (prop != null && prop > 0) {
            return Math.min(32, prop);
        }
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(2, Math.min(8, cores));
    }

    static final String VOICE_WARM_PARALLELISM_PROPERTY = "edo.voiceWarmParallelism";

    static Set<String> requiredWarmupTemplatesForTests() {
        return REQUIRED_WARMUP_TEMPLATES;
    }

    /** Same repo as {@link VoicePackManager} voice-pack downloads; used by {@code -deploy}. */
    private static final String VOICE_PACK_GITHUB_REPO = "deccles/Elite-Dangerous-RockHound";

    /**
     * Absolute path to {@code gh} when it is not on the JVM's {@code PATH} (typical for IDE / GUI launches on Windows).
     * Example: {@code -Dedo.ghPath="C:\Program Files\GitHub CLI\gh.exe"}
     */
    static final String GH_PATH_PROPERTY = "edo.ghPath";

    private static <T> void runChunkedParallel(String phase, List<T> items, ItemWarm<T> work) throws Exception {
        if (items == null || items.isEmpty()) {
            return;
        }
        int threads = warmParallelism();
        if (threads <= 1) {
            for (T t : items) {
                work.accept(t);
            }
            return;
        }
        int nThreads = Math.min(threads, items.size());
        int chunkSize = (items.size() + nThreads - 1) / nThreads;
        ExecutorService ex = Executors.newFixedThreadPool(nThreads, r -> {
            Thread t = new Thread(r, "VoiceCacheWarmer-" + phase);
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int start = 0; start < items.size(); start += chunkSize) {
                int end = Math.min(items.size(), start + chunkSize);
                List<T> slice = items.subList(start, end);
                futures.add(ex.submit(() -> {
                    for (T item : slice) {
                        work.accept(item);
                    }
                    return null;
                }));
            }
            for (Future<Void> f : futures) {
                f.get();
            }
        } finally {
            ex.shutdown();
        }
    }

    public static void warmAll(String voiceName) throws Exception {
        if (voiceName == null || voiceName.isBlank()) {
            throw new IllegalArgumentException("voiceName is required");
        }

        String canon = canonicalPollyVoiceName(voiceName);
        if (canon == null) {
            throw new IllegalArgumentException("Unknown Polly voice: " + voiceName);
        }

        boolean priorUseAws = OverlayPreferences.isSpeechUseAwsSynthesis();
        String priorVoice = OverlayPreferences.getSpeechVoiceName();
        try {
            // Warmer must synthesize via Polly; respect user's "use AWS" choice in UI otherwise skips generation.
            OverlayPreferences.setSpeechUseAwsSynthesis(true);
            OverlayPreferences.setSpeechVoiceId(canon);
            OverlayPreferences.flushBackingStore();
            warmAllUsingCurrentPreferences();
        } finally {
            OverlayPreferences.setSpeechUseAwsSynthesis(priorUseAws);
            if (priorVoice != null && !priorVoice.isBlank()) {
                OverlayPreferences.setSpeechVoiceId(priorVoice);
            }
            OverlayPreferences.flushBackingStore();
        }
    }

    private static void warmAllUsingCurrentPreferences() throws Exception {
        List<String> templates = findAllSpeakTemplatesFromSourceTree();
        Set<String> units = findUnitWordsFromTemplates(templates);

        // Genus/species names from exobiology data.
        Map<String, SpeciesConstraint> constraints = new LinkedHashMap<>();
        ExobiologyDataConstraints.initConstraints(constraints);

        Set<String> speciesWords = new LinkedHashSet<>();
        for (SpeciesConstraint sc : constraints.values()) {
            addWords(speciesWords, sc.getGenus());
            addWords(speciesWords, sc.getSpecies());
        }

        GalacticAveragePrices commodityPrices = GalacticAveragePrices.loadDefault();
        List<String> commodityDisplayNames = commodityPrices.getAllDisplayNamesSorted();
        Set<String> commodityWords = new LinkedHashSet<>();
        for (String dn : commodityDisplayNames) {
            addWords(commodityWords, dn);
        }

        Set<String> tokens = new LinkedHashSet<>();
        tokens.addAll(allLetters());
        tokens.addAll(numericComboSpeechTokens());
        TtsSprintf.addEnglishNumberSpeechVocabulary(tokens);
        tokens.addAll(units);
        tokens.addAll(speciesWords);
        tokens.addAll(commodityWords);

        List<String> sampleBodies = buildSampleBodyNames();
        List<String> sampleSpecies = buildSampleSpeciesNames(constraints);
        String sampleListTwo = buildSampleProspectorListTwo(commodityDisplayNames);
        String sampleListOxford = buildSampleProspectorListOxford(commodityDisplayNames);
        String sampleMaterialA = commodityDisplayNames.isEmpty() ? "Tritium" : commodityDisplayNames.get(0);
        String sampleMaterialB = commodityDisplayNames.size() > 1 ? commodityDisplayNames.get(1) : sampleMaterialA;

        int tokenCount = tokens.size();
        System.out.println("Warming cache: tokens=" + tokenCount + ", templates=" + templates.size()
                + ", parallelism=" + warmParallelism() + " (override -D" + VOICE_WARM_PARALLELISM_PROPERTY + "=N)");

        List<String> nVals = List.of("0", "1", "2", "3", "10", "50", "100");
        List<Long> creditVals = List.of(0L, 1_000L, 50_000L, 1_500_000L, 12_300_000L, 2_000_000_000L);
        List<Long> meterVals = List.of(0L, 1L, 5L, 10L, 50L, 100L, 500L, 1_000L, 50_000L);

        try (PollyTtsCached tts = new PollyTtsCached()) {
            TtsSprintf sprintf = new TtsSprintf(tts, Locale.US);

            // Warm common single-token variants (MID vs END).
            warmTokens(sprintf, tokens);

            // Warm body tokens and mixed alphanumeric patterns.
            warmBodies(sprintf, sampleBodies);

            // Full prospector line per commodity display name so multi-word materials match real SSML mark boundaries.
            warmProspectorSingleMaterialTemplates(sprintf, commodityDisplayNames);

            // Eleven–nineteen are single English words in TTS ({n}/{min}/{max}); warm real prospector lines so those
            // clips exist. Other integers still split (e.g. 21 → twenty + one) and are covered by numeric tokens + templates.
            warmProspectorTeenPercentLines(sprintf, sampleMaterialA, sampleListTwo);

            // Warm full templates found in the code with representative placeholder values.
            warmTemplates(sprintf, templates, sampleBodies, sampleSpecies,
                    sampleMaterialA, sampleMaterialB, sampleListTwo, sampleListOxford);

            // Every exobiology genus/species phrase with each template that references {species} (e.g. clonal colony lines).
            warmSpeciesAcrossAllTemplates(sprintf, templates, sampleBodies, sampleSpecies,
                    nVals, creditVals, meterVals, sampleMaterialA, sampleListTwo);

            warmProspectorListTemplatesAdjacent(sprintf, commodityDisplayNames);
        }
    }

    private static void warmSpeciesAcrossAllTemplates(TtsSprintf sprintf,
            List<String> templates,
            List<String> sampleBodies,
            List<String> allSpecies,
            List<String> nVals,
            List<Long> creditVals,
            List<Long> meterVals,
            String sampleMaterialA,
            String sampleListTwo) throws Exception {
        if (templates == null || allSpecies == null || allSpecies.isEmpty()) {
            return;
        }
        List<String> speciesTemplates = new ArrayList<>();
        for (String tmpl : templates) {
            if (tmpl == null || tmpl.isBlank() || !tmpl.contains("{species}")) {
                continue;
            }
            List<String> tags = extractPlaceholderTagsInOrder(tmpl);
            if (!tags.isEmpty()) {
                speciesTemplates.add(tmpl);
            }
        }
        if (speciesTemplates.isEmpty()) {
            return;
        }
        String defaultBody = sampleBodies.isEmpty() ? "A 1" : sampleBodies.get(0);
        List<String> speciesList = new ArrayList<>();
        for (String species : allSpecies) {
            if (species != null && !species.isBlank()) {
                speciesList.add(species);
            }
        }
        AtomicInteger sp = new AtomicInteger();
        int total = speciesList.size() * speciesTemplates.size() * 2;
        runChunkedParallel("species", speciesList, species -> {
            for (String tmpl : speciesTemplates) {
                List<String> tags = extractPlaceholderTagsInOrder(tmpl);
                Object[] args1 = buildArgsForTags(tags, defaultBody, species, nVals.get(3), creditVals.get(3), meterVals.get(3),
                        sampleMaterialA, sampleListTwo);
                sprintf.ensureCachedfBlocking(tmpl, args1);
                Object[] args2 = buildArgsForTags(tags, defaultBody, species, nVals.get(6), creditVals.get(5), meterVals.get(8),
                        sampleMaterialA, sampleListTwo);
                sprintf.ensureCachedfBlocking(tmpl, args2);
                int n = sp.addAndGet(2);
                if (n % 500 == 0) {
                    System.out.println("  warmed species-template combos: " + n + "/" + total);
                }
            }
        });
    }

    private static void warmProspectorTeenPercentLines(TtsSprintf sprintf, String sampleMaterial, String sampleListTwo)
            throws Exception {
        String material = (sampleMaterial == null || sampleMaterial.isBlank()) ? "Tritium" : sampleMaterial;
        for (int pct = 11; pct <= 19; pct++) {
            sprintf.ensureCachedfBlocking("Prospector found {material} at {n} percent.", material, pct);
        }
        if (sampleListTwo != null && !sampleListTwo.isBlank()) {
            sprintf.ensureCachedfBlocking(
                    "Prospector found {list} from {min} to {max} percent.", sampleListTwo, 11, 19);
        }
    }

    private static void warmProspectorSingleMaterialTemplates(TtsSprintf sprintf, List<String> commodityDisplayNames) throws Exception {
        if (commodityDisplayNames == null || commodityDisplayNames.isEmpty()) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (String name : commodityDisplayNames) {
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        AtomicInteger warmed = new AtomicInteger();
        int total = names.size();
        runChunkedParallel("prospector-material", names, name -> {
            sprintf.ensureCachedfBlocking("Prospector found {material} at {n} percent.", name, 50);
            int n = warmed.incrementAndGet();
            if (n % 200 == 0) {
                System.out.println("  warmed prospector single-material templates: " + n + "/" + total);
            }
        });
    }

    private static String buildSampleProspectorListTwo(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "Platinum and Painite";
        }
        if (names.size() == 1) {
            String one = names.get(0);
            return one + " and " + one;
        }
        return names.get(0) + " and " + names.get(1);
    }

    private static String buildSampleProspectorListOxford(List<String> names) {
        if (names == null || names.size() < 3) {
            return buildSampleProspectorListTwo(names);
        }
        return names.get(0) + ", " + names.get(1) + ", and " + names.get(2);
    }

    /**
     * Two-commodity phrase used when warming {@code Prospector found {list} ...} (same logic as
     * {@link #buildSampleProspectorListTwo}). Preferences "Test Speech" should pass this string with {@code 10}/{@code 90}
     * so offline packs that included adjacent-pair warming hit cache for that clip.
     */
    public static String sampleProspectorListTwoForVoicePack() {
        List<String> names = GalacticAveragePrices.loadDefault().getAllDisplayNamesSorted();
        return buildSampleProspectorListTwo(names);
    }

    /**
     * Warm {@code Prospector found {list} ...} for consecutive commodity pairs and triples so multi-material
     * prospector lines match cached SSML chunks for many name combinations.
     */
    private static void warmProspectorListTemplatesAdjacent(TtsSprintf sprintf, List<String> names) throws Exception {
        if (names == null || names.size() < 2) {
            return;
        }
        List<Integer> pairStarts = new ArrayList<>();
        for (int i = 0; i < names.size() - 1; i++) {
            pairStarts.add(i);
        }
        List<Integer> tripleStarts = new ArrayList<>();
        for (int i = 0; i < names.size() - 2; i++) {
            tripleStarts.add(i);
        }
        AtomicInteger warmed = new AtomicInteger();
        runChunkedParallel("prospector-list-pairs", pairStarts, i -> {
            String list = names.get(i) + " and " + names.get(i + 1);
            sprintf.ensureCachedfBlocking("Prospector found {list} from {min} to {max} percent.", list, 10, 90);
            warmed.incrementAndGet();
        });
        runChunkedParallel("prospector-list-triples", tripleStarts, i -> {
            String list = names.get(i) + ", " + names.get(i + 1) + ", and " + names.get(i + 2);
            sprintf.ensureCachedfBlocking("Prospector found {list} from {min} to {max} percent.", list, 5, 95);
            warmed.incrementAndGet();
        });
        System.out.println("  warmed prospector list templates (adjacent pairs/triples): " + warmed.get());
    }

    private static void warmTokens(TtsSprintf sprintf, Set<String> tokens) throws Exception {
        List<String> list = new ArrayList<>();
        for (String tok : tokens) {
            if (tok != null && !tok.isBlank()) {
                list.add(tok);
            }
        }
        AtomicInteger i = new AtomicInteger();
        int total = list.size();
        runChunkedParallel("tokens", list, tok -> {
            sprintf.ensureCachedChunksBlocking(List.of("test", tok, "continue"));
            sprintf.ensureCachedChunksBlocking(List.of("test", tok));
            int n = i.incrementAndGet();
            if (n % 250 == 0) {
                System.out.println("  warmed tokens: " + n + "/" + total);
            }
        });
    }

    private static void warmBodies(TtsSprintf sprintf, List<String> bodies) throws Exception {
        if (bodies == null || bodies.isEmpty()) {
            return;
        }
        AtomicInteger i = new AtomicInteger();
        int total = bodies.size();
        runChunkedParallel("bodies", bodies, b -> {
            sprintf.ensureCachedfBlocking("planetary body {body}", b);
            sprintf.ensureCachedfBlocking("planetary body {body}.", b);
            int n = i.incrementAndGet();
            if (n % 50 == 0) {
                System.out.println("  warmed bodies: " + n + "/" + total);
            }
        });
    }

    private static void warmTemplates(TtsSprintf sprintf,
                                     List<String> templates,
                                     List<String> sampleBodies,
                                     List<String> sampleSpecies,
                                     String sampleMaterialA,
                                     String sampleMaterialB,
                                     String sampleListTwo,
                                     String sampleListOxford) throws Exception {
        if (templates == null || templates.isEmpty()) {
            return;
        }

        // Keep this bounded: we warm each template with a small set of representative values.
        List<String> nVals = List.of("0", "1", "2", "3", "10", "50", "100");
        List<Long> creditVals = List.of(0L, 1_000L, 50_000L, 1_500_000L, 12_300_000L, 2_000_000_000L);
        List<Long> meterVals = List.of(0L, 1L, 5L, 10L, 50L, 100L, 500L, 1_000L, 50_000L);

        String defaultBody = sampleBodies.isEmpty() ? "A 1" : sampleBodies.get(0);
        String defaultSpecies = sampleSpecies.isEmpty() ? "Bacterium Acies" : sampleSpecies.get(0);

        List<String> tmplList = new ArrayList<>();
        for (String tmpl : templates) {
            if (tmpl != null && !tmpl.isBlank()) {
                tmplList.add(tmpl);
            }
        }
        AtomicInteger warmed = new AtomicInteger();
        int total = tmplList.size();
        runChunkedParallel("templates", tmplList, tmpl -> {
            List<String> tags = extractPlaceholderTagsInOrder(tmpl);
            if (tags.isEmpty()) {
                sprintf.ensureCachedChunksBlocking(List.of(tmpl, "continue"));
                sprintf.ensureCachedChunksBlocking(List.of(tmpl));
            } else {
                Object[] args1 = buildArgsForTags(tags, defaultBody, defaultSpecies, nVals.get(3), creditVals.get(3), meterVals.get(3),
                        sampleMaterialA, sampleListTwo);
                sprintf.ensureCachedfBlocking(tmpl, args1);
                Object[] args2 = buildArgsForTags(tags, defaultBody, defaultSpecies, nVals.get(6), creditVals.get(5), meterVals.get(8),
                        sampleMaterialB, sampleListOxford);
                sprintf.ensureCachedfBlocking(tmpl, args2);
            }
            int n = warmed.incrementAndGet();
            if (n % 100 == 0) {
                System.out.println("  warmed templates: " + n + "/" + total);
            }
        });
    }

    private static Object[] buildArgsForTags(List<String> tags,
                                            String body,
                                            String species,
                                            String n,
                                            long credits,
                                            long meters,
                                            String sampleMaterial,
                                            String sampleList) {
        Object[] args = new Object[tags.size()];
        for (int i = 0; i < tags.size(); i++) {
            String t = tags.get(i);
            if (t == null) {
                args[i] = "";
                continue;
            }

            if (t.equals("species")) {
                args[i] = species;
            } else if (t.equals("body") || t.equals("bodyId")) {
                args[i] = body;
            } else if (t.equals("n") || t.equals("num") || t.equals("number") || t.equals("min") || t.equals("max")) {
                args[i] = n;
            } else if (t.equals("credits")) {
                args[i] = credits;
            } else if (t.equals("meters")) {
                args[i] = meters;
            } else if (t.equals("material")) {
                args[i] = sampleMaterial != null && !sampleMaterial.isBlank() ? sampleMaterial : "Tritium";
            } else if (t.equals("list")) {
                args[i] = sampleList != null && !sampleList.isBlank() ? sampleList : "Platinum and Painite";
            } else {
                args[i] = "test";
            }
        }
        return args;
    }

    private static List<String> extractPlaceholderTagsInOrder(String template) {
        List<String> out = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(template);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static List<String> buildSampleBodyNames() {
        List<String> out = new ArrayList<>();

        // Single token / no-space bodies.
        out.addAll(List.of(
                "A1",
                "A1A",
                "BC1",
                "1A",
                "5f",
                "12",
                "1",
                "0"
        ));

        // Spaced bodies (caller sometimes passes this already tokenized).
        out.addAll(List.of(
                "A 1",
                "A 1 A",
                "BC 1",
                "1 A",
                "A 10",
                "AA 1",
                "AB 12",
                "Z 9"
        ));

        return out;
    }

    private static List<String> buildSampleSpeciesNames(Map<String, SpeciesConstraint> constraints) {
        if (constraints == null || constraints.isEmpty()) {
            return List.of();
        }

        // Preserve insertion order (generated file order is stable).
        List<String> out = new ArrayList<>();
        for (SpeciesConstraint sc : constraints.values()) {
            out.add(sc.getGenus() + " " + sc.getSpecies());
        }
        return out;
    }

    private static Set<String> allLetters() {
        Set<String> out = new LinkedHashSet<>();
        for (char c = 'A'; c <= 'Z'; c++) {
            out.add(String.valueOf(c));
        }
        return out;
    }

    /**
     * Digits and round numeric strings for body codes / literal warm paths. {@link TtsSprintf} expands
     * {@code {n}} and {@code {credits}} with {@link TtsSprintf#expandNumberToWords(long)} (e.g. {@code hundred},
     * {@code thousand}); those English words are warmed via {@link TtsSprintf#addEnglishNumberSpeechVocabulary}.
     */
    private static Set<String> numericComboSpeechTokens() {
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i <= 9; i++) {
            out.add(Integer.toString(i));
        }
        for (int t = 1; t <= 9; t++) {
            out.add(Integer.toString(t * 10));
        }
        for (int h = 1; h <= 9; h++) {
            out.add(Integer.toString(h * 100));
        }
        for (int k = 1; k <= 9; k++) {
            out.add(Integer.toString(k * 1000));
        }
        for (int m = 1; m <= 9; m++) {
            out.add(Integer.toString(m * 1_000_000));
        }
        out.add("minus");
        out.add("zero");
        return out;
    }

    private static void addWords(Set<String> out, String s) {
        if (s == null) {
            return;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return;
        }
        for (String p : t.split("\\s+")) {
            if (!p.isBlank()) {
                out.add(p.trim());
            }
        }
    }

    private static Set<String> findUnitWordsFromTemplates(List<String> templates) {
        // A conservative, explicit baseline list.
        Set<String> out = new LinkedHashSet<>(Arrays.asList(
                "credits",
                "credit",
                "hundred",
                "thousand",
                "thousands",
                "million",
                "millions",
                "billion",
                "billions",
                "point",
                "meters",
                "meter",
                "kilometers",
                "kilometer",
                "km",
                "ly",
                "light",
                "years",
                "year",
                "seconds",
                "second",
                "minutes",
                "minute",
                "hours",
                "hour",
                "jumps",
                "jump"
        ));

        if (templates == null || templates.isEmpty()) {
            return out;
        }

        // Also pick up unit-ish words we already say in templates.
        Pattern word = Pattern.compile("[A-Za-z]{2,}");
        for (String t : templates) {
            if (t == null) {
                continue;
            }
            Matcher m = word.matcher(t);
            while (m.find()) {
                String w = m.group();
                if (w == null) {
                    continue;
                }
                String wl = w.toLowerCase(Locale.ROOT);
                // Keep it bounded to likely units / counts.
                if (wl.endsWith("s") || wl.equals("percent") || wl.equals("meters") || wl.equals("credits")) {
                    out.add(wl);
                }
            }
        }
        return out;
    }

    private static List<String> findAllSpeakTemplatesFromSourceTree() {
        Set<String> out = new LinkedHashSet<>();
        out.addAll(REQUIRED_WARMUP_TEMPLATES);
        for (Path root : findJavaSourceRoots()) {
            try {
                Files.walk(root)
                        .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                        .forEach(p -> {
                            try {
                                String txt = Files.readString(p, StandardCharsets.UTF_8);
                                extractStringLiterals(out, txt, SPEAKF_LITERAL);
                                extractStringLiterals(out, txt, SPEAKF_WITH_GATE_LITERAL);
                                extractStringLiterals(out, txt, SPEAK_LITERAL);
                            } catch (Exception e) {
                                // ignore unreadable source file
                            }
                        });
            } catch (IOException e) {
                // ignore
            }
        }

        // Deterministic order.
        return new ArrayList<>(new TreeSet<>(out));
    }

    private static void extractStringLiterals(Set<String> out, String txt, Pattern p) {
        if (txt == null || txt.isBlank()) {
            return;
        }
        Matcher m = p.matcher(txt);
        while (m.find()) {
            String raw = m.group(1);
            if (raw == null) {
                continue;
            }
            String s = unescapeJavaString(raw).trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
    }

    private static String unescapeJavaString(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                out.append(c);
                continue;
            }

            char n = s.charAt(i + 1);
            if (n == 'n') {
                out.append('\n');
                i++;
            } else if (n == 'r') {
                out.append('\r');
                i++;
            } else if (n == 't') {
                out.append('\t');
                i++;
            } else if (n == '\\') {
                out.append('\\');
                i++;
            } else if (n == '\"') {
                out.append('\"');
                i++;
            } else {
                // keep unknown escape as-is.
                out.append(c);
            }
        }
        return out.toString();
    }

    private static List<Path> findJavaSourceRoots() {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath();

        // Common project layouts.
        List<Path> candidates = List.of(
                cwd.resolve("src").resolve("main").resolve("java"),
                cwd.resolve("src").resolve("src").resolve("main").resolve("java"),
                cwd.resolve("EDO").resolve("src").resolve("main").resolve("java")
        );

        List<Path> found = new ArrayList<>();
        for (Path p : candidates) {
            if (Files.isDirectory(p)) {
                found.add(p);
            }
        }

        // If none match, walk up a couple of levels looking for src/main/java.
        if (found.isEmpty()) {
            Path here = cwd;
            for (int i = 0; i < 3 && here != null; i++) {
                Path p = here.resolve("src").resolve("main").resolve("java");
                if (Files.isDirectory(p)) {
                    found.add(p);
                    break;
                }
                here = here.getParent();
            }
        }

        return found;
    }

    /**
     * Map CLI or config input to Polly's canonical voice id (e.g. {@code salli} → {@code Salli}).
     *
     * @return canonical name, or {@code null} if no matching {@link VoiceId}
     */
    static String canonicalPollyVoiceName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        if ("null".equalsIgnoreCase(v)) {
            return null;
        }
        try {
            VoiceId id = VoiceId.fromValue(v);
            if (!VoiceId.UNKNOWN_TO_SDK_VERSION.equals(id)) {
                return id.toString();
            }
        } catch (Exception ignored) {
        }
        String normalized = v.toLowerCase(Locale.ROOT);
        for (VoiceId id : VoiceId.values()) {
            if (VoiceId.UNKNOWN_TO_SDK_VERSION.equals(id)) {
                continue;
            }
            if (id.toString().toLowerCase(Locale.ROOT).equals(normalized)) {
                return id.toString();
            }
        }
        return null;
    }

    /**
     * If the launcher passes one string ({@code "salli -create"}), split on whitespace (space, tab, etc.).
     */
    private static String[] normalizeProgramArgs(String[] args) {
        if (args == null || args.length != 1 || args[0] == null) {
            return args;
        }
        String trimmed = args[0].trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length > 1) {
            return parts;
        }
        return args;
    }

    private static boolean isCreatePackFlag(String t) {
        if (t == null) {
            return false;
        }
        String s = t.trim();
        return "-create".equalsIgnoreCase(s)
                || "--create".equalsIgnoreCase(s)
                || "/create".equalsIgnoreCase(s);
    }

    private static boolean isDeployFlag(String t) {
        if (t == null) {
            return false;
        }
        String s = t.trim();
        return "-deploy".equalsIgnoreCase(s)
                || "--deploy".equalsIgnoreCase(s)
                || "/deploy".equalsIgnoreCase(s);
    }

    public static void main(String[] args) {
        args = normalizeProgramArgs(args);
        if (args == null || args.length == 0) {
            printUsage();
            return;
        }

        boolean createZip = false;
        boolean deploy = false;
        String deployTag = null;
        String voiceRaw = null;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a == null || a.isBlank()) {
                continue;
            }
            String t = a.trim();
            if (isCreatePackFlag(t)) {
                createZip = true;
                continue;
            }
            if (isDeployFlag(t)) {
                deploy = true;
                if (i + 1 < args.length) {
                    String next = args[i + 1].trim();
                    if (!next.isEmpty()
                            && !next.startsWith("-")
                            && !isCreatePackFlag(next)
                            && !isDeployFlag(next)) {
                        deployTag = next;
                        i++;
                    }
                }
                continue;
            }
            if (t.startsWith("-")) {
                System.err.println("Unknown option: " + t);
                printUsage();
                return;
            }
            if (voiceRaw != null) {
                System.err.println("Unexpected extra argument: " + t);
                printUsage();
                return;
            }
            voiceRaw = t;
        }

        if (deploy && (deployTag == null || deployTag.isBlank())) {
            System.err.println("-deploy requires a GitHub release tag (example: -deploy 1.0.1)");
            printUsage();
            return;
        }
        if (deploy) {
            createZip = true;
        }

        if (voiceRaw == null) {
            System.err.println("Voice name required.");
            printUsage();
            return;
        }

        if ("all".equalsIgnoreCase(voiceRaw)) {
            List<String> voices = PollyTtsCached.STANDARD_US_ENGLISH_VOICES;
            System.out.println("Warming " + voices.size() + " standard US English voices: " + voices);
            List<Path> zipsForDeploy = new ArrayList<>();
            for (String voice : voices) {
                Path created = warmAndMaybeZipOneVoice(voice, createZip);
                if (created != null) {
                    zipsForDeploy.add(created);
                }
            }
            System.out.println("Finished all voices.");
            tryFinishDeploy(deploy, deployTag, zipsForDeploy);
            return;
        }

        String voice = canonicalPollyVoiceName(voiceRaw);
        if (voice == null) {
            System.err.println("Unknown Polly voice: " + voiceRaw);
            printUsage();
            return;
        }

        Path createdZip = warmAndMaybeZipOneVoice(voice, createZip);
        tryFinishDeploy(deploy, deployTag, createdZip != null ? List.of(createdZip) : List.of());
    }

    private static void tryFinishDeploy(boolean deploy, String deployTag, List<Path> zipsToUpload) {
        if (!deploy) {
            return;
        }
        try {
            runGhReleaseUploadVoicePacks(deployTag, zipsToUpload);
        } catch (Exception e) {
            System.err.println("Deploy failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Runs: {@code gh release upload <tag> <zip paths> --repo ... --clobber} for the given zips only
     * (typically those just created by this warmer run).
     */
    private static void runGhReleaseUploadVoicePacks(String releaseTag, List<Path> zips) throws IOException, InterruptedException {
        if (zips == null || zips.isEmpty()) {
            System.err.println("No voice pack zip was produced in this run; skipping gh release upload.");
            return;
        }
        List<Path> existing = new ArrayList<>();
        for (Path zip : zips) {
            if (zip != null && Files.isRegularFile(zip)) {
                existing.add(zip.toAbsolutePath().normalize());
            }
        }
        if (existing.isEmpty()) {
            System.err.println("Pack zip path(s) from this run are missing on disk; skipping gh release upload.");
            return;
        }

        Path ghExe = resolveGhExecutable();

        List<String> cmd = new ArrayList<>();
        cmd.add(ghExe.toString());
        cmd.add("release");
        cmd.add("upload");
        cmd.add(releaseTag);
        for (Path zip : existing) {
            cmd.add(zip.toString());
        }
        cmd.add("--repo");
        cmd.add(VOICE_PACK_GITHUB_REPO);
        cmd.add("--clobber");

        System.out.println("Running: " + ghExe.getFileName() + " release upload " + releaseTag + " (" + existing.size()
                + " zips) --repo " + VOICE_PACK_GITHUB_REPO + " --clobber");
        System.out.println("(gh path: " + ghExe + ")");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        Process p = pb.start();
        int code = p.waitFor();
        if (code != 0) {
            throw new IOException("gh release upload exited with code " + code);
        }
        System.out.println("Uploaded " + existing.size() + " voice pack(s) to release " + releaseTag + ".");
    }

    private static Path resolveGhExecutable() throws IOException {
        String prop = System.getProperty(GH_PATH_PROPERTY);
        if (prop != null && !prop.isBlank()) {
            Path p = Path.of(prop.trim());
            if (Files.isRegularFile(p)) {
                return p.toAbsolutePath().normalize();
            }
            throw new IOException(GH_PATH_PROPERTY + " points to a missing file: " + p.toAbsolutePath());
        }
        String env = firstNonBlankEnv("EDO_GH_PATH");
        if (env != null) {
            Path p = Path.of(env.trim());
            if (Files.isRegularFile(p)) {
                return p.toAbsolutePath().normalize();
            }
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("windows")) {
            for (Path candidate : ghWindowsInstallCandidates()) {
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().normalize();
                }
            }
        } else {
            for (String c : List.of("/usr/local/bin/gh", "/opt/homebrew/bin/gh", "/usr/bin/gh")) {
                Path p = Path.of(c);
                if (Files.isRegularFile(p)) {
                    return p.toAbsolutePath().normalize();
                }
            }
        }

        throw new IOException(
                "GitHub CLI (gh) not found. Install gh and/or set path explicitly, e.g. -D" + GH_PATH_PROPERTY
                        + "=\"C:\\\\Program Files\\\\GitHub CLI\\\\gh.exe\" or environment variable EDO_GH_PATH."
                        + " IDE launches on Windows often omit gh from PATH.");
    }

    private static String firstNonBlankEnv(String... names) {
        for (String n : names) {
            if (n == null) {
                continue;
            }
            String v = System.getenv(n);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static List<Path> ghWindowsInstallCandidates() {
        List<Path> out = new ArrayList<>();
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            out.add(Path.of(localAppData, "Programs", "GitHub CLI", "gh.exe"));
            out.add(Path.of(localAppData, "GitHub CLI", "gh.exe"));
        }
        String pf = System.getenv("ProgramFiles");
        if (pf != null && !pf.isBlank()) {
            out.add(Path.of(pf, "GitHub CLI", "gh.exe"));
        }
        String pf86 = System.getenv("ProgramFiles(x86)");
        if (pf86 != null && !pf86.isBlank()) {
            out.add(Path.of(pf86, "GitHub CLI", "gh.exe"));
        }
        return out;
    }

    private static void printUsage() {
        System.err.println("Usage: VoiceCacheWarmer <voice|all> [-create] [-deploy <releaseTag>]");
        System.err.println("  voice    Polly voice id, case-insensitive (e.g. salli, Joanna)");
        System.err.println("  all      warm every voice in PollyTtsCached.STANDARD_US_ENGLISH_VOICES");
        System.err.println("  -create  after warming, write target/voice-<voice>.zip (one zip per voice)");
        System.err.println("  -deploy <tag>  gh release upload for pack(s) built this run only (not every");
        System.err.println("                 target/voice-*.zip) to " + VOICE_PACK_GITHUB_REPO);
        System.err.println("                 (implies -create; needs GitHub CLI: gh on PATH, or standard Windows");
        System.err.println("                 install dirs, or -D" + GH_PATH_PROPERTY + " / env EDO_GH_PATH)");
    }

    /**
     * Warm one canonical voice name and optionally create {@code target/voice-<lower>.zip}.
     *
     * @return absolute path to the zip if {@code createZip} and packaging succeeded; otherwise {@code null}
     */
    private static Path warmAndMaybeZipOneVoice(String voice, boolean createZip) {
        try {
            warmAll(voice);
            System.out.println("Done warming cache for voice: " + voice);
        } catch (Exception e) {
            System.err.println("Warm failed for " + voice + " (pack zip will still be attempted if -create): "
                    + e.getMessage());
            e.printStackTrace();
        }

        if (createZip) {
            Path outDir = Path.of("target");
            try {
                Files.createDirectories(outDir);
                Path zip = outDir.resolve("voice-" + voice.toLowerCase(Locale.ROOT) + ".zip");
                Path absZip = zip.toAbsolutePath().normalize();
                System.out.println("Creating voice pack: " + absZip);
                VoicePackManager.createVoicePackZip(voice, zip);
                System.out.println("Created pack: " + absZip);
                return absZip;
            } catch (Exception e) {
                System.err.println("Pack zip failed for " + voice + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        return null;
    }
}
