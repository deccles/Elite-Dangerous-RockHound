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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dce.ed.OverlayPreferences;
import org.dce.ed.exobiology.ExobiologyData.SpeciesConstraint;
import org.dce.ed.exobiology.ExobiologyDataConstraints;

import software.amazon.awssdk.services.polly.model.VoiceId;

/**
 * Developer helper that exercises the speech system to pre-populate the TTS cache
 * (including MID vs END variants).
 *
 * Intended usage:
 *   VoiceCacheWarmer.warmAll("Joanna");
 *
 * Or from the command line (voice is matched case-insensitively to a Polly {@link VoiceId}):
 *   java ... org.dce.ed.tts.VoiceCacheWarmer salli
 *   java ... org.dce.ed.tts.VoiceCacheWarmer salli -create
 * With {@code -create}, also writes {@code target/voice-&lt;voice&gt;.zip} for release upload.
 */
public final class VoiceCacheWarmer {

    private static final Pattern SPEAKF_LITERAL = Pattern.compile("\\.speakf(?:Blocking)?\\s*\\(\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.MULTILINE);
    private static final Pattern SPEAK_LITERAL = Pattern.compile("\\.speak(?:Blocking)?\\s*\\(\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.MULTILINE);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");

    private VoiceCacheWarmer() {
    }

    public static void warmAll(String voiceName) throws Exception {
        if (voiceName == null || voiceName.isBlank()) {
            throw new IllegalArgumentException("voiceName is required");
        }

        String canon = canonicalPollyVoiceName(voiceName);
        if (canon == null) {
            throw new IllegalArgumentException("Unknown Polly voice: " + voiceName);
        }

        String priorVoice = OverlayPreferences.getSpeechVoiceName();
        try {
            OverlayPreferences.setSpeechVoiceId(canon);
            OverlayPreferences.flushBackingStore();
            warmAllUsingCurrentPreferences();
        } finally {
            if (priorVoice != null && !priorVoice.isBlank()) {
                OverlayPreferences.setSpeechVoiceId(priorVoice);
            }
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

        Set<String> tokens = new LinkedHashSet<>();
        tokens.addAll(allLetters());
        tokens.addAll(allNumbers());
        tokens.addAll(units);
        tokens.addAll(speciesWords);

        List<String> sampleBodies = buildSampleBodyNames();
        List<String> sampleSpecies = buildSampleSpeciesNames(constraints);

        int tokenCount = tokens.size();
        System.out.println("Warming cache: tokens=" + tokenCount + ", templates=" + templates.size());

        try (PollyTtsCached tts = new PollyTtsCached()) {
            TtsSprintf sprintf = new TtsSprintf(tts, Locale.US);

            // Warm common single-token variants (MID vs END).
            warmTokens(sprintf, tokens);

            // Warm body tokens and mixed alphanumeric patterns.
            warmBodies(sprintf, sampleBodies);

            // Warm full templates found in the code with representative placeholder values.
            warmTemplates(sprintf, templates, sampleBodies, sampleSpecies);
        }
    }

    private static void warmTokens(TtsSprintf sprintf, Set<String> tokens) throws Exception {
        int i = 0;
        for (String tok : tokens) {
            if (tok == null || tok.isBlank()) {
                continue;
            }

            // MID variant: token not last chunk.
            sprintf.ensureCachedChunksBlocking(List.of("test", tok, "continue"));
            // END variant: token is last chunk.
            sprintf.ensureCachedChunksBlocking(List.of("test", tok));

            i++;
            if (i % 250 == 0) {
                System.out.println("  warmed tokens: " + i);
            }
        }
    }

    private static void warmBodies(TtsSprintf sprintf, List<String> bodies) throws Exception {
        if (bodies == null || bodies.isEmpty()) {
            return;
        }
        int i = 0;
        for (String b : bodies) {
            // Speak body both as a body tag and as plain text to exercise both paths.
            sprintf.ensureCachedfBlocking("planetary body {body}", b);
            sprintf.ensureCachedfBlocking("planetary body {body}.", b);

            i++;
            if (i % 50 == 0) {
                System.out.println("  warmed bodies: " + i);
            }
        }
    }

    private static void warmTemplates(TtsSprintf sprintf,
                                     List<String> templates,
                                     List<String> sampleBodies,
                                     List<String> sampleSpecies) throws Exception {
        if (templates == null || templates.isEmpty()) {
            return;
        }

        // Keep this bounded: we warm each template with a small set of representative values.
        List<String> nVals = List.of("0", "1", "2", "3", "10", "50", "100");
        List<Long> creditVals = List.of(0L, 1_000L, 50_000L, 1_500_000L, 12_300_000L, 2_000_000_000L);
        List<Long> meterVals = List.of(0L, 1L, 5L, 10L, 50L, 100L, 500L, 1_000L, 50_000L);

        String defaultBody = sampleBodies.isEmpty() ? "A 1" : sampleBodies.get(0);
        String defaultSpecies = sampleSpecies.isEmpty() ? "Bacterium Acies" : sampleSpecies.get(0);

        int warmed = 0;
        for (String tmpl : templates) {
            if (tmpl == null || tmpl.isBlank()) {
                continue;
            }

            List<String> tags = extractPlaceholderTagsInOrder(tmpl);
            if (tags.isEmpty()) {
                // No placeholders, just warm it as both MID-ish and END.
                sprintf.ensureCachedChunksBlocking(List.of(tmpl, "continue"));
                sprintf.ensureCachedChunksBlocking(List.of(tmpl));
                warmed++;
                continue;
            }

            // Build one or two representative argument sets.
            Object[] args1 = buildArgsForTags(tags, defaultBody, defaultSpecies, nVals.get(3), creditVals.get(3), meterVals.get(3));
            sprintf.ensureCachedfBlocking(tmpl, args1);

            Object[] args2 = buildArgsForTags(tags, defaultBody, defaultSpecies, nVals.get(6), creditVals.get(5), meterVals.get(8));
            sprintf.ensureCachedfBlocking(tmpl, args2);

            warmed++;
            if (warmed % 100 == 0) {
                System.out.println("  warmed templates: " + warmed);
            }
        }
    }

    private static Object[] buildArgsForTags(List<String> tags,
                                            String body,
                                            String species,
                                            String n,
                                            long credits,
                                            long meters) {
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
        int added = 0;
        for (SpeciesConstraint sc : constraints.values()) {
            out.add(sc.getGenus() + " " + sc.getSpecies());
            added++;
            if (added >= 25) {
                break;
            }
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

    private static Set<String> allNumbers() {
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i <= 99; i++) {
            out.add(Integer.toString(i));
        }
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
                "million",
                "billion",
                "thousand",
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
        for (Path root : findJavaSourceRoots()) {
            try {
                Files.walk(root)
                        .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                        .forEach(p -> {
                            try {
                                String txt = Files.readString(p, StandardCharsets.UTF_8);
                                extractStringLiterals(out, txt, SPEAKF_LITERAL);
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
     * If the launcher passes one string ({@code "salli -create"}), split into tokens.
     */
    private static String[] normalizeProgramArgs(String[] args) {
        if (args == null || args.length != 1 || args[0] == null) {
            return args;
        }
        String a = args[0].trim();
        if (a.contains(" ")) {
            return a.split("\\s+");
        }
        return args;
    }

    public static void main(String[] args) {
        args = normalizeProgramArgs(args);
        if (args == null || args.length == 0) {
            System.err.println("Usage: VoiceCacheWarmer <voice> [-create]");
            System.err.println("  voice    Polly voice id, case-insensitive (e.g. salli, Joanna)");
            System.err.println("  -create  after warming, write target/voice-<voice>.zip");
            return;
        }

        boolean createZip = false;
        String voiceRaw = null;
        for (String a : args) {
            if (a == null || a.isBlank()) {
                continue;
            }
            String t = a.trim();
            if ("-create".equalsIgnoreCase(t)) {
                createZip = true;
                continue;
            }
            if (t.startsWith("-")) {
                System.err.println("Unknown option: " + t);
                System.err.println("Usage: VoiceCacheWarmer <voice> [-create]");
                return;
            }
            if (voiceRaw != null) {
                System.err.println("Unexpected extra argument: " + t);
                System.err.println("Usage: VoiceCacheWarmer <voice> [-create]");
                return;
            }
            voiceRaw = t;
        }

        if (voiceRaw == null) {
            System.err.println("Voice name required.");
            System.err.println("Usage: VoiceCacheWarmer <voice> [-create]");
            return;
        }

        String voice = canonicalPollyVoiceName(voiceRaw);
        if (voice == null) {
            System.err.println("Unknown Polly voice: " + voiceRaw);
            return;
        }

        try {
            warmAll(voice);
            System.out.println("Done warming cache for voice: " + voice);
            if (createZip) {
                Path outDir = Path.of("target");
                Files.createDirectories(outDir);
                Path zip = outDir.resolve("voice-" + voice.toLowerCase(Locale.ROOT) + ".zip");
                VoicePackManager.createVoicePackZip(voice, zip);
                System.out.println("Created pack: " + zip.toAbsolutePath().normalize());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
