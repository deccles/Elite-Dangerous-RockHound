package org.dce.ed.tts;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dce.ed.OverlayPreferences;

/**
 * "sprintf for voice" that expands templates like:
 *   "Leaving clonal colony range of {species}"
 *   "{n} signals found on planetary body {body}"
 *
 * The key design goal is: caller stays simple, while you can gradually move
 * toward a fully-cached speech library by controlling how templates are split
 * into utterance chunks.
 *
 * IMPORTANT: This class lives outside the TTS engine classes.
 * It only depends on PollyTtsCached's public speak()/speakBlocking().
 */
public class TtsSprintf {

    private static final String CACHEKEY_LETTER_PREFIX = "L|";

    /**
     * Resolve a {tag} placeholder into one or more utterance chunks.
     * Each returned chunk is spoken separately (and therefore cached separately by PollyTtsCached).
     */
    @FunctionalInterface
    public interface TagResolver {
        List<String> resolve(String tag, Object value);
    }

    private final ExecutorService speakQueue = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "edo-tts-queue");
        t.setDaemon(true);
        return t;
    });

    
    public static void main(String args[]) {
    	TtsSprintf ttsSprintf = new TtsSprintf(new PollyTtsCached());
    	ttsSprintf.speakf("Hello McFly!");
    }
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");

    private final PollyTtsCached tts;
    private final Map<String, TagResolver> resolvers = new HashMap<>();
    private final Locale locale;

    public TtsSprintf(PollyTtsCached tts) {
        this(tts, Locale.US);
    }

    public TtsSprintf(PollyTtsCached tts, Locale locale) {
        this.tts = Objects.requireNonNull(tts, "tts");
        this.locale = (locale == null) ? Locale.US : locale;

        // Default resolvers. Add/override with registerResolver().
        registerResolver("species", TtsSprintf::resolveSpeciesDefault);
        registerResolver("material", TtsSprintf::resolveMaterialDefault);
        registerResolver("body", TtsSprintf::resolveBodyDefault);
        registerResolver("bodyId", TtsSprintf::resolveBodyDefault);

        registerResolver("n", TtsSprintf::resolveNumberDefault);
        registerResolver("num", TtsSprintf::resolveNumberDefault);
        registerResolver("number", TtsSprintf::resolveNumberDefault);
        registerResolver("min", TtsSprintf::resolveNumberDefault);
        registerResolver("max", TtsSprintf::resolveNumberDefault);
        /** Whole millions (integer); templates say e.g. "from mm to mm million credits" (single "million"). */
        registerResolver("mm", TtsSprintf::resolveNumberDefault);

        // Large numeric expansions (caller still writes the unit word in the template)
        // Example: "... value of {credits} credits" -> ["one", "million", "five", "hundred", "thousand"]
        registerResolver("credits", TtsSprintf::resolveCreditsDefault);
        registerResolver("meters", TtsSprintf::resolveMetersDefault);
    }

    public void registerResolver(String tag, TagResolver resolver) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("tag is required");
        }
        Objects.requireNonNull(resolver, "resolver");
        resolvers.put(tag, resolver);
    }

    /**
     * Non-blocking: queues speech (delegates to PollyTtsCached.speak()).
     */
    public void speakf(String template, Object... args) {
        speakfWithSpeechGate(OverlayPreferences.isSpeechEnabled(), template, args);
    }

    /**
     * Like {@link #speakf(String, Object...)} but uses {@code speechEnabled} instead of
     * {@link OverlayPreferences#isSpeechEnabled()} for the gate. Used by the Speech preferences tab so a
     * sample can play when the user has turned speech on in the dialog but not yet pressed OK.
     */
    public void speakfWithSpeechGate(boolean speechEnabled, String template, Object... args) {
        speakfWithSpeechGateArray(speechEnabled, template, args);
    }

    /**
     * Like {@link #speakfWithSpeechGate(boolean, String, Object...)} but accepts an argument array (e.g. from a
     * sample table) without varargs ambiguity when passing {@code Object[]}.
     */
    public void speakfWithSpeechGateArray(boolean speechEnabled, String template, Object[] args) {
        speakfWithSpeechGateArray(speechEnabled, null, template, args);
    }

    /**
     * Like {@link #speakfWithSpeechGateArray(boolean, String, Object[])} but uses {@code voicePreview} for the
     * Polly voice when non-null and non-blank (Speech tab test before OK).
     */
    public void speakfWithSpeechGateArray(boolean speechEnabled, PollyTtsCached.SpeechSynthesisVoicePreview voicePreview,
            String template, Object[] args) {
        // Double-gate: most callers already check speech enabled, but tests (and any missed call sites)
        // must never produce console spam or invoke TTS side effects.
        if (Boolean.getBoolean("edo.test.disableSpeech") || !speechEnabled) {
            return;
        }
        Object[] a = args != null ? args : new Object[0];
        SpeechPlan plan = formatToSpeechPlanPositional(template, a);
        List<String> chunks = plan.chunkTexts;
        if (chunks.isEmpty()) {
            return;
        }

        final PollyTtsCached.SpeechSynthesisVoicePreview previewForWorker = voicePreview;
        tts.getPlaybackQueue().submit(() -> {
            try {
                speakAssembledBlocking(plan, previewForWorker);
            } catch (Exception e) {
                if (PollyTtsCached.isMissingAwsCredentialsError(e)) {
                    System.err.println("[EDO] TTS skipped: Amazon Polly needs AWS credentials (see earlier dialog).");
                } else {
                    e.printStackTrace();
                }
            }
        });
    }

    private void speakAssembledBlocking(SpeechPlan plan, PollyTtsCached.SpeechSynthesisVoicePreview voicePreview) throws Exception {
        if (Boolean.getBoolean("edo.test.disableSpeech")) {
            return;
        }
        if (plan == null || plan.chunkTexts.isEmpty()) {
            return;
        }

        // Build SSML with explicit <mark/> boundaries so Polly returns precise timestamps.
        SsmlWithMarks ssmlPlan = buildSsmlWithMarks(plan);

        // Ensure cached WAVs, preferring context-derived chunks when missing.
        List<Path> wavs = tts.ensureCachedWavsFromSsmlMarks(
                ssmlPlan.chunkTexts,
                ssmlPlan.cacheKeys,
                ssmlPlan.ssml,
                ssmlPlan.markNames,
                voicePreview
        );

        // Filter nulls (blank chunks) and play as a single continuous stream.
        List<Path> toPlay = new ArrayList<>();
        for (Path p : wavs) {
            if (p != null) {
                toPlay.add(p);
            }
        }

        if (!toPlay.isEmpty()) {
            tts.playCombinedWavsBlocking(toPlay);
        }
    }

    private static final class SpeechPlan {
        private final List<String> chunkTexts;
        private final List<String> cacheKeys;

        private SpeechPlan(List<String> chunkTexts, List<String> cacheKeys) {
            this.chunkTexts = chunkTexts;
            this.cacheKeys = cacheKeys;
        }
    }

    private static final class SsmlWithMarks {
        private final String ssml;
        private final List<String> markNames;
        private final List<String> chunkTexts;
        private final List<String> cacheKeys;

        private SsmlWithMarks(String ssml, List<String> markNames, List<String> chunkTexts, List<String> cacheKeys) {
            this.ssml = ssml;
            this.markNames = markNames;
            this.chunkTexts = chunkTexts;
            this.cacheKeys = cacheKeys;
        }
    }

    private SsmlWithMarks buildSsmlWithMarks(SpeechPlan plan) {
        List<String> markNames = new ArrayList<>(plan.chunkTexts.size());
        List<String> chunkTexts = new ArrayList<>(plan.chunkTexts.size());
        List<String> cacheKeys = new ArrayList<>(plan.chunkTexts.size());

        int lastNonBlankIdx = -1;
        for (int i = 0; i < plan.chunkTexts.size(); i++) {
            String c = plan.chunkTexts.get(i);
            if (c != null && !c.isBlank()) {
                lastNonBlankIdx = i;
            }
        }

        StringBuilder ssml = new StringBuilder();
        ssml.append("<speak>");

        for (int i = 0; i < plan.chunkTexts.size(); i++) {
            String c = plan.chunkTexts.get(i);
            if (c == null || c.isBlank()) {
                continue;
            }

            String mark = "C" + i;
            markNames.add(mark);
            chunkTexts.add(c);

            String key = null;
            if (plan.cacheKeys != null && i < plan.cacheKeys.size()) {
                key = plan.cacheKeys.get(i);
            }
            if (key == null || key.isBlank()) {
                key = computeCacheKey(c, i, lastNonBlankIdx);
            }
            cacheKeys.add(key);

            ssml.append("<mark name=\"").append(mark).append("\"/>");
            ssml.append(escapeForSsml(c));
            ssml.append(" ");
        }

        ssml.append("</speak>");
        return new SsmlWithMarks(ssml.toString(), markNames, chunkTexts, cacheKeys);
    }

    private static String computeCacheKey(String chunkText, int idx, int lastNonBlankIdx) {
        if (chunkText == null) {
            return null;
        }

        String pos = (idx == lastNonBlankIdx) ? "END" : "MID";

        // Numbers sound different at the end of a sentence vs. mid-phrase.
        if (isAllDigits(chunkText)) {
            return "N|" + chunkText + "|" + pos;
        }

        // Single letters also can differ (useful for body names).
        if (isSingleLetter(chunkText)) {
            return "L|" + chunkText.toUpperCase(Locale.ROOT) + "|" + pos;
        }

        // Everything else: position-aware cache key, but keep spoken text identical.
        // We only use this for caching, so a delimiter-safe normalization is fine.
        String safe = chunkText.replace('|', ' ').trim();
        return "T|" + safe + "|" + pos;
    }

    private static boolean isSingleLetter(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim();
        return t.length() == 1 && Character.isLetter(t.charAt(0));
    }

    private static boolean isAllDigits(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String escapeForSsml(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim().replaceAll("\\s+", " ");
        // Minimal XML escaping for SSML text nodes.
        t = t.replace("&", "&amp;");
        t = t.replace("<", "&lt;");
        t = t.replace(">", "&gt;");
        t = t.replace("\"", "&quot;");
        t = t.replace("'", "&apos;");
        return t;
    }

    /**
     * Blocking: speaks the fully expanded chunks using the SSML mark/slice path (so cache keys apply).
     */
    public void speakfBlocking(String template, Object... args) throws Exception {
        SpeechPlan plan = formatToSpeechPlan(template, args);
        speakAssembledBlocking(plan, null);
    }

    /**
     * Named-arg version (lets you call with a map instead of positional arguments).
     */
    public void speakf(String template, Map<String, ?> argsByTag) {
        SpeechPlan plan = formatToSpeechPlan(template, argsByTag);
        if (plan.chunkTexts.isEmpty()) {
            return;
        }

        tts.getPlaybackQueue().submit(() -> {
            try {
                speakAssembledBlocking(plan, null);
            } catch (Exception e) {
                if (PollyTtsCached.isMissingAwsCredentialsError(e)) {
                    System.err.println("[EDO] TTS skipped: Amazon Polly needs AWS credentials (see earlier dialog).");
                } else {
                    e.printStackTrace();
                }
            }
        });
    }

    public void speakfBlocking(String template, Map<String, ?> argsByTag) throws Exception {
        SpeechPlan plan = formatToSpeechPlan(template, argsByTag);
        speakAssembledBlocking(plan, null);
    }

    /**
     * Positional-arg formatter.
     * Args are matched to placeholders in order of appearance.
     */
    public List<String> formatToUtteranceChunks(String template, Object... args) {
        return formatToSpeechPlan(template, args).chunkTexts;
    }

    private SpeechPlan formatToSpeechPlan(String template, Object... args) {
        if (args == null) {
            return formatToSpeechPlanPositional(template, new Object[0]);
        }
        return formatToSpeechPlanPositional(template, args);
    }

    private SpeechPlan formatToSpeechPlanPositional(String template, Object[] args) {
        Objects.requireNonNull(template, "template");
        if (args == null) {
            args = new Object[0];
        }

        List<String> out = new ArrayList<>();
        List<String> cacheKeys = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(template);

        int last = 0;
        int argIndex = 0;

        while (m.find()) {
            String literal = template.substring(last, m.start());
            addLiteralChunk(out, cacheKeys, literal);

            String tag = m.group(1);

            Object value = null;
            if (argIndex < args.length) {
                value = args[argIndex];
            }
            argIndex++;

            addResolvedChunks(out, cacheKeys, tag, value);

            last = m.end();
        }

        addLiteralChunk(out, cacheKeys, template.substring(last));
        return finalizeSpeechPlan(out, cacheKeys);
    }

    /**
     * Named-arg formatter.
     * Each placeholder pulls its value from the map by tag name.
     */
    public List<String> formatToUtteranceChunks(String template, Map<String, ?> argsByTag) {
        return formatToSpeechPlan(template, argsByTag).chunkTexts;
    }

    private SpeechPlan formatToSpeechPlan(String template, Map<String, ?> argsByTag) {
        Objects.requireNonNull(template, "template");

        Map<String, ?> map = (argsByTag == null) ? Collections.emptyMap() : argsByTag;

        List<String> out = new ArrayList<>();
        List<String> cacheKeys = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(template);

        int last = 0;
        while (m.find()) {
            String literal = template.substring(last, m.start());
            addLiteralChunk(out, cacheKeys, literal);

            String tag = m.group(1);
            Object value = map.get(tag);

            addResolvedChunks(out, cacheKeys, tag, value);

            last = m.end();
        }

        addLiteralChunk(out, cacheKeys, template.substring(last));
        return finalizeSpeechPlan(out, cacheKeys);
    }

    private void addLiteralChunk(List<String> out, List<String> cacheKeys, String literal) {
        if (literal == null || literal.isBlank()) {
            return;
        }

        // Keep literal runs as a single chunk so your cache learns reusable phrases:
        // e.g. "Leaving clonal colony range of"
        String normalized = normalizeSpaces(literal);

        // Trim but preserve internal spacing.
        normalized = normalized.trim();
        // After a multi-token {species}/{body}/… expansion, the next literal often begins with ". NextClause"
        // (sentence period in the template). A separate chunk starting with "." is spoken as "dot" by Polly;
        // drop the period when it is only clause punctuation before whitespace + words (".NET" keeps its dot).
        normalized = normalized.replaceFirst("^\\s*\\.\\s+", "");
        normalized = normalized.trim();
        if (!normalized.isEmpty()) {
            out.add(normalized);
            // Null: let finalizeSpeechPlan apply computeCacheKey() so literals match the SSML mark path
            // (T|...|MID vs |END) and VoiceCacheWarmer. A raw string key would wrongly use mid/ only.
            cacheKeys.add(null);
        }
    }

    private void addResolvedChunks(List<String> out, List<String> cacheKeys, String tag, Object value) {
        TagResolver resolver = resolvers.get(tag);
        List<String> chunks;

        if (resolver != null) {
            chunks = resolver.resolve(tag, value);
        } else {
            // Default: just stringify.
            chunks = defaultStringify(value);
        }

        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        List<String> normalizedChunks = new ArrayList<>();
        for (String c : chunks) {
            if (c == null) {
                continue;
            }
            String s = normalizeSpaces(c).trim();
            if (!s.isEmpty()) {
                normalizedChunks.add(s);
            }
        }

        if (normalizedChunks.isEmpty()) {
            return;
        }

        // Tag-aware cache keys (lets us keep spoken text clean while still caching context-specific variants).
        // Example: body names: "A 3 A" -> cache keys: ["L|A|MID", "N|3|MID", "L|A|END"]
        int lastIdx = normalizedChunks.size() - 1;
        for (int i = 0; i < normalizedChunks.size(); i++) {
            String s = normalizedChunks.get(i);
            out.add(s);
            cacheKeys.add(computeCacheKeyForTag(tag, s, i, lastIdx));
        }
    }

    private static String computeCacheKeyForTag(String tag, String chunkText, int idxInTag, int lastIdxInTag) {
        if (chunkText == null || chunkText.isBlank()) {
            return null;
        }

        if (tag != null) {
            if (tag.equals("body") || tag.equals("bodyId")) {
                if (isAllDigits(chunkText)) {
                    String pos = (idxInTag == lastIdxInTag) ? "END" : "MID";
                    return "N|" + chunkText + "|" + pos;
                }
                if (isSingleLetter(chunkText)) {
                    String pos = (idxInTag == lastIdxInTag) ? "END" : "MID";
                    return CACHEKEY_LETTER_PREFIX + chunkText.toUpperCase(Locale.ROOT) + "|" + pos;
                }

                // Default: cache body tokens by literal text (e.g., "IV" or "A1" if caller passes it that way)
                return chunkText;
            }

            // Credits/meters are already expanded to word tokens; cache each token as-is.
            if (tag.equals("credits") || tag.equals("meters")) {
                return chunkText;
            }
        }

        // Fallback: allow global sentence-position rules (digits/letters at end) to decide.
        // We return null so buildSsmlWithMarks() can compute a cache key later with full-sentence context.
        return null;
    }

    // -----------------------
    // Default tag resolvers
    // -----------------------

    /**
     * Split on whitespace into speakable chunks so single-word clips (e.g. {@code Tritium}, {@code Frutexa}) cache once
     * and reuse across phrases.
     */
    private static List<String> resolveWhitespaceDelimitedSpeech(Object value, String emptyPhrase) {
        if (value == null) {
            return List.of(emptyPhrase);
        }
        String s = value.toString().trim();
        if (s.isEmpty()) {
            return List.of(emptyPhrase);
        }
        String[] parts = s.split("\\s+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (!p.isBlank()) {
                out.addAll(expandSingleIntegerTokenOrPassThrough(p));
            }
        }
        return out.isEmpty() ? List.of(emptyPhrase) : out;
    }

    private static List<String> resolveSpeciesDefault(String tag, Object value) {
        return resolveWhitespaceDelimitedSpeech(value, "unknown species");
    }

    private static List<String> resolveMaterialDefault(String tag, Object value) {
        return resolveWhitespaceDelimitedSpeech(value, "unknown material");
    }

    /**
     * Speaks whole integers as English word tokens (e.g. {@code 15} → {@code "fifteen"}, {@code 41} →
     * {@code "forty"} then {@code "one"}) so prospector percentages and similar lines do not sound like digit pairs
     * ("ten five"). Non-integers fall back to a single spoken string. {@link Long#MIN_VALUE} falls back to digits.
     */
    private static List<String> resolveNumberDefault(String tag, Object value) {
        if (value == null) {
            return List.of("zero");
        }
        String raw = stripTrailingDotZero(value.toString()).trim();
        if (raw.isEmpty()) {
            return List.of("zero");
        }
        if (raw.matches("-?\\d+")) {
            try {
                long v = Long.parseLong(raw);
                if (v == Long.MIN_VALUE) {
                    return List.of(raw);
                }
                if (v < 0) {
                    List<String> out = new ArrayList<>();
                    out.add("minus");
                    out.addAll(expandNumberToWords(-v));
                    return out;
                }
                return expandNumberToWords(v);
            } catch (NumberFormatException e) {
                return List.of(raw);
            }
        }
        return List.of(raw);
    }

    private static List<String> resolveCreditsDefault(String tag, Object value) {
        Long n = coerceLong(value);
        if (n == null) {
            return List.of("0");
        }

        if (n < 0) {
            List<String> out = new ArrayList<>();
            out.add("minus");
            out.addAll(expandCreditsCompact(Math.abs(n)));
            return out;
        }

        return expandCreditsCompact(n);
    }

    /**
     * Rounds a credit amount for speech so Polly does not read huge exact integers.
     * At or above one billion: nearest 0.1 billion; at or above one million: nearest 0.1 million
     * (so {@code {credits}} can say "five point three million"); under one million: nearest 10k.
     */
    public static long roundCreditsForSpeech(long credits) {
        if (credits == 0) {
            return 0;
        }
        long sign = credits < 0 ? -1 : 1;
        long n = Math.abs(credits);
        long rounded;
        if (n >= 1_000_000_000L) {
            rounded = (n + 50_000_000L) / 100_000_000L * 100_000_000L;
        } else if (n >= 1_000_000L) {
            rounded = (n + 50_000L) / 100_000L * 100_000L;
        } else {
            rounded = (n + 5_000L) / 10_000L * 10_000L;
        }
        return sign * rounded;
    }

    private static List<String> expandCreditsCompact(long n) {
        if (n == 0) {
            return expandNumberToWords(0);
        }

        // Prefer compact "X point Y million/billion" with one decimal digit.
        // Whole and fractional parts use English words (reusable clips), not multi-digit strings like "53".
        // Examples:
        //  1,500,000 -> ["one","point","five","million"]
        //  2,000,000 -> ["two","million"]
        //  12,300,000 -> ["twelve","point","three","million"]
        //  5,349,000 -> ["five","point","three","million"] (rounded to 0.1)
        if (n >= 1_000_000_000L) {
            return compactWithOneDecimal(n, 1_000_000_000L, "billion");
        }

        if (n >= 1_000_000L) {
            return compactWithOneDecimal(n, 1_000_000L, "million");
        }

        // Below a million: keep your existing word expansion so small numbers don’t sound weird
        // (and you don’t need digit audio for everything).
        return expandNumberToWords(n);
    }

    private static List<String> compactWithOneDecimal(long n, long scale, String scaleWord) {
        long whole = n / scale;
        long rem = n % scale;

        // Round remainder to one decimal place of the scale (e.g. 0.1 million).
        long tenth = scale / 10;
        long decimalDigit = (rem + tenth / 2) / tenth;
        if (decimalDigit >= 10) {
            whole++;
            decimalDigit = 0;
        }

        // Exact scale (after rounding): "<words for whole> scaleWord"
        if (decimalDigit == 0) {
            List<String> out = new ArrayList<>(expandNumberToWords(whole));
            out.add(scaleWord);
            return out;
        }

        List<String> out = new ArrayList<>(expandNumberToWords(whole));
        out.add("point");
        out.add(basicNumberWord((int) decimalDigit));
        out.add(scaleWord);
        return out;
    }

    private static List<String> resolveMetersDefault(String tag, Object value) {
        Long n = coerceLong(value);
        if (n == null) {
            // If someone passed a floating distance string, just let Polly handle it.
            if (value != null) {
                return List.of(value.toString().trim());
            }
            return List.of("zero");
        }
        if (n < 0) {
            List<String> out = new ArrayList<>();
            out.add("minus");
            out.addAll(expandNumberToWords(Math.abs(n)));
            return out;
        }
        return expandNumberToWords(n);
    }

    private static List<String> resolveBodyDefault(String tag, Object value) {
        if (value == null) {
            return List.of("unknown body");
        }

        // Example values you might pass:
        //   "5f" => ["five", "f"]  (pure integer runs use words, same as {n})
        //   "A 61 b" => ["A", "sixty", "one", "b"]
        String s = value.toString().trim();
        if (s.isEmpty()) {
            return List.of("unknown body");
        }

        List<String> rawParts;
        if (s.contains(" ")) {
            rawParts = new ArrayList<>();
            for (String p : s.split("\\s+")) {
                if (!p.isBlank()) {
                    rawParts.add(p.trim());
                }
            }
        } else {
            rawParts = splitAlphaNumericTransitions(s);
        }

        if (rawParts.isEmpty()) {
            return List.of("unknown body");
        }

        return expandBodyDigitTokensToWords(rawParts);
    }

    /**
     * Expands each body token that is only an integer (e.g. {@code "61"}) into {@link #expandNumberToWords} tokens
     * so the cache uses reusable word clips (sixty, one) instead of one clip per digit string.
     */
    private static List<String> expandBodyDigitTokensToWords(List<String> rawParts) {
        List<String> out = new ArrayList<>();
        for (String part : rawParts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            out.addAll(expandSingleIntegerTokenOrPassThrough(part));
        }
        return out.isEmpty() ? List.of("unknown body") : out;
    }

    /**
     * One whitespace token: if it is only an optional sign and digits (e.g. {@code -1294967296} from bad overflow
     * upstream), expand to English word chunks; otherwise keep one non-numeric token.
     */
    private static List<String> expandSingleIntegerTokenOrPassThrough(String token) {
        if (token == null) {
            return List.of();
        }
        String t = token.trim();
        if (t.isEmpty()) {
            return List.of();
        }
        if (t.matches("-?\\d+")) {
            try {
                long v = Long.parseLong(t);
                if (v == Long.MIN_VALUE) {
                    return List.of(t);
                }
                if (v < 0) {
                    List<String> o = new ArrayList<>();
                    o.add("minus");
                    o.addAll(expandNumberToWords(-v));
                    return o;
                }
                return new ArrayList<>(expandNumberToWords(v));
            } catch (NumberFormatException e) {
                return List.of(t);
            }
        }
        return List.of(t);
    }

    // -----------------------
    // Helpers
    // -----------------------

    /**
     * Unknown {@code {tag}} values: avoid a single spoken chunk like {@code -1294967296} (overflowed {@code int}
     * stringified) by expanding integer-looking strings the same way as {@link #resolveNumberDefault}.
     */
    private static List<String> defaultStringify(Object value) {
        if (value == null) {
            return List.of();
        }
        String s = value.toString();
        if (s == null || s.isBlank()) {
            return List.of();
        }
        return expandSingleIntegerTokenOrPassThrough(s);
    }

    private static String normalizeSpaces(String s) {
        return s.replaceAll("\\s+", " ");
    }

    private static String stripTrailingDotZero(String s) {
        // "5.0" -> "5" (common when someone passes a double)
        if (s != null && s.endsWith(".0")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }

    private static Long coerceLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String s = value.toString();
        if (s == null) {
            return null;
        }
        s = s.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            if (s.contains(".")) {
                // Avoid surprising truncation for non-integers; caller can pass a long if they want.
                double d = Double.parseDouble(s);
                return (long) d;
            }
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Expands a non-negative integer into spoken English word tokens (US locale by default).
     *
     * Examples:
     *  0 -> ["zero"]
     *  1500000 -> ["one","million","five","hundred","thousand"]
     */
    private static List<String> expandNumberToWords(long n) {
        if (n == 0) {
            return List.of("zero");
        }

        List<String> out = new ArrayList<>();
        if (n >= 1_000_000_000L) {
            long billions = n / 1_000_000_000L;
            out.addAll(expandNumberToWords(billions));
            out.add("billion");
            n = n % 1_000_000_000L;
        }
        if (n >= 1_000_000L) {
            long millions = n / 1_000_000L;
            out.addAll(expandNumberToWords(millions));
            out.add("million");
            n = n % 1_000_000L;
        }
        if (n >= 1_000L) {
            long thousands = n / 1_000L;
            out.addAll(expandNumberToWords(thousands));
            out.add("thousand");
            n = n % 1_000L;
        }
        if (n > 0) {
            out.addAll(expandBelowThousand((int) n));
        }
        return out;
    }

    /**
     * Adds every distinct English word that {@link #expandNumberToWords(long)} can emit for common magnitudes,
     * plus {@code point} from credit compact reads. Used by {@link org.dce.ed.tts.VoiceCacheWarmer} so offline
     * packs include clips for {@code hundred}, {@code thousand}, {@code million}, etc.
     */
    public static void addEnglishNumberSpeechVocabulary(Set<String> out) {
        if (out == null) {
            return;
        }
        for (long n = 0; n <= 999_999L; n++) {
            out.addAll(expandNumberToWords(n));
        }
        out.add("point");
        for (long n : new long[] {
                1_000_000L,
                999_000_000L,
                1_000_000_000L,
                2_000_000_000L,
                9_000_000_000L }) {
            out.addAll(expandNumberToWords(n));
        }
    }

    private static List<String> expandBelowThousand(int n) {
        if (n == 0) {
            return List.of();
        }

        List<String> out = new ArrayList<>();

        int hundreds = n / 100;
        int rem = n % 100;

        if (hundreds > 0) {
            out.add(basicNumberWord(hundreds));
            out.add("hundred");
        }

        if (rem > 0) {
            out.addAll(expandBelowHundred(rem));
        }

        return out;
    }

    private static List<String> expandBelowHundred(int n) {
        if (n == 0) {
            return List.of();
        }
        if (n < 20) {
            return List.of(basicNumberWord(n));
        }

        List<String> out = new ArrayList<>();
        int tens = n / 10;
        int ones = n % 10;

        out.add(tensWord(tens));
        if (ones > 0) {
            out.add(basicNumberWord(ones));
        }
        return out;
    }

    private static String tensWord(int tens) {
        switch (tens) {
            case 2:
                return "twenty";
            case 3:
                return "thirty";
            case 4:
                return "forty";
            case 5:
                return "fifty";
            case 6:
                return "sixty";
            case 7:
                return "seventy";
            case 8:
                return "eighty";
            case 9:
                return "ninety";
            default:
                return Integer.toString(tens * 10);
        }
    }

    private static String basicNumberWord(int n) {
        switch (n) {
            case 0:
                return "zero";
            case 1:
                return "one";
            case 2:
                return "two";
            case 3:
                return "three";
            case 4:
                return "four";
            case 5:
                return "five";
            case 6:
                return "six";
            case 7:
                return "seven";
            case 8:
                return "eight";
            case 9:
                return "nine";
            case 10:
                return "ten";
            case 11:
                return "eleven";
            case 12:
                return "twelve";
            case 13:
                return "thirteen";
            case 14:
                return "fourteen";
            case 15:
                return "fifteen";
            case 16:
                return "sixteen";
            case 17:
                return "seventeen";
            case 18:
                return "eighteen";
            case 19:
                return "nineteen";
            default:
                return Integer.toString(n);
        }
    }

    private static List<String> splitAlphaNumericTransitions(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();

        Character prevType = null; // 'D' digit, 'A' alpha, 'O' other
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);

            char type;
            if (Character.isDigit(ch)) {
                type = 'D';
            } else if (Character.isLetter(ch)) {
                type = 'A';
            } else {
                type = 'O';
            }

            if (cur.length() == 0) {
                cur.append(ch);
                prevType = type;
                continue;
            }

            // Break on transitions digit<->alpha; keep other characters attached.
            if ((prevType == 'D' && type == 'A') || (prevType == 'A' && type == 'D')) {
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(ch);
                prevType = type;
                continue;
            }

            cur.append(ch);
            prevType = type;
        }

        if (cur.length() > 0) {
            out.add(cur.toString());
        }

        // Final cleanup: trim empty
        List<String> cleaned = new ArrayList<>();
        for (String p : out) {
            if (p != null) {
                String t = p.trim();
                if (!t.isEmpty()) {
                    cleaned.add(t);
                }
            }
        }
        return cleaned.isEmpty() ? List.of(s) : cleaned;
    }

    private static SpeechPlan finalizeSpeechPlan(List<String> chunks, List<String> cacheKeys) {
        List<String> out = new ArrayList<>();
        List<String> keys = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            String c = chunks.get(i);
            if (c == null) {
                continue;
            }
            String t = c.trim();
            if (t.isEmpty()) {
                continue;
            }
            out.add(t);

            String k = null;
            if (cacheKeys != null && i < cacheKeys.size()) {
                k = cacheKeys.get(i);
            }
            keys.add((k == null || k.isBlank()) ? null : k.trim());
        }

        // Any cache keys we couldn't decide during expansion get computed with full-sentence context.
        int lastNonBlankIdx = out.size() - 1;
        for (int i = 0; i < out.size(); i++) {
            String k = keys.get(i);
            if (k == null || k.isBlank()) {
                keys.set(i, computeCacheKey(out.get(i), i, lastNonBlankIdx));
            }
        }

        return new SpeechPlan(out, keys);
    }
    /**
     * Blocking cache-warm: expands a template and ensures all resulting speech chunks
     * are cached (MID vs END variants included via cache keys), but does not play audio.
     */
    public List<Path> ensureCachedfBlocking(String template, Object... args) throws Exception {
        SpeechPlan plan = formatToSpeechPlan(template, args);
        return ensureCachedBlocking(plan);
    }

    /**
     * Blocking cache-warm: expands a template and ensures all resulting speech chunks
     * are cached (MID vs END variants included via cache keys), but does not play audio.
     */
    public List<Path> ensureCachedfBlocking(String template, Map<String, ?> argsByTag) throws Exception {
        SpeechPlan plan = formatToSpeechPlan(template, argsByTag);
        return ensureCachedBlocking(plan);
    }

    /**
     * Blocking cache-warm: ensures the given chunks are cached using the same
     * SSML mark/slice path as speakfBlocking(), but does not play audio.
     */
    public List<Path> ensureCachedChunksBlocking(List<String> chunks) throws Exception {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        SpeechPlan plan = new SpeechPlan(new ArrayList<>(chunks), null);
        return ensureCachedBlocking(plan);
    }

    private List<Path> ensureCachedBlocking(SpeechPlan plan) throws Exception {
        if (plan == null || plan.chunkTexts == null || plan.chunkTexts.isEmpty()) {
            return List.of();
        }

        SsmlWithMarks ssmlPlan = buildSsmlWithMarks(plan);
        return tts.ensureCachedWavsFromSsmlMarks(
                ssmlPlan.chunkTexts,
                ssmlPlan.cacheKeys,
                ssmlPlan.ssml,
                ssmlPlan.markNames
        );
    }

    /**
     * Returns placeholders in appearance order (useful for debugging / tooling).
     */
    public static List<String> listPlaceholders(String template) {
        if (template == null) {
            return List.of();
        }
        Matcher m = PLACEHOLDER.matcher(template);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /**
     * Returns unique placeholder tags (useful if you want to ensure resolvers exist).
     */
    public static Set<String> uniquePlaceholderTags(String template) {
        return new LinkedHashSet<>(listPlaceholders(template));
    }
}
