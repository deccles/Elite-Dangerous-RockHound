package org.dce.ed.tts;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.Frame;
import java.awt.Window;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.dce.ed.OverlayPreferences;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.Engine;
import software.amazon.awssdk.services.polly.model.OutputFormat;
import software.amazon.awssdk.services.polly.model.SpeechMarkType;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechResponse;
import software.amazon.awssdk.services.polly.model.TextType;
import software.amazon.awssdk.services.polly.model.VoiceId;

public class PollyTtsCached implements Closeable {

    /**
     * Owner for TTS-related {@link JOptionPane}s. When null, Swing may place modals behind the overlay on
     * Windows while still blocking input (looks “frozen”). Set from the main UI host on startup.
     */
    private static volatile Window speechDialogParentWindow;

    public static void setSpeechDialogParentWindow(Window window) {
        speechDialogParentWindow = window;
    }

    /**
     * When set (e.g. from the main overlay), speech cache misses are reported here instead of a modal dialog.
     */
    private static volatile Consumer<String> speechCacheMissBannerReporter;

    public static void setSpeechCacheMissBannerReporter(Consumer<String> reporter) {
        speechCacheMissBannerReporter = reporter;
    }

    private static Component speechDialogParentComponent() {
        Window w = speechDialogParentWindow;
        if (w != null && w.isDisplayable()) {
            return w;
        }
        Frame[] frames = Frame.getFrames();
        for (int i = frames.length - 1; i >= 0; i--) {
            Frame f = frames[i];
            if (f != null && f.isDisplayable() && f.isVisible()) {
                return f;
            }
        }
        return null;
    }

    /** Avoid spamming the same modal when many TTS calls fail for the same reason. */
    private static final AtomicBoolean AWS_CREDENTIAL_POPUP_SHOWN = new AtomicBoolean(false);

    // Trim leading/trailing silence from Polly PCM before writing cache WAVs (full-utterance path only).
    private static final int TRIM_ABS_THRESHOLD = 250;  // 16-bit PCM amplitude threshold (0..32767)
    /** Padding kept beyond last "loud" sample so quiet word tails (s, f, th, …) are not clipped. */
    private static final int TRIM_KEEP_MS = 80;

    // Speech-mark parsing (JSON lines)

    public static final List<String> STANDARD_US_ENGLISH_VOICES = List.of(
            "Ivy", "Joanna", "Kendra", "Kimberly", "Salli", "Joey", "Justin", "Matthew"
    );

    /**
     * Optional Polly voice name for preview synthesis (e.g. Speech preferences before OK).
     * When {@code voiceName} is null or blank, {@link OverlayPreferences} is used.
     */
    public record SpeechSynthesisVoicePreview(String voiceName) {
    }

    // Single-thread executor = non-blocking callers, but sequential playback.
    private static final ExecutorService PLAYBACK_QUEUE  = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PollyTtsCached-Playback");
        t.setDaemon(true);
        return t;
    });

    private final PollyClient polly;
    private final Object manifestLock = new Object();
    /** Striped locks so parallel {@link VoiceCacheWarmer} threads cannot corrupt the same output {@code .wav}. */
    private final Object[] cachePathStripes;

    public PollyTtsCached() {
        var b = PollyClient.builder()
                .region(software.amazon.awssdk.regions.Region.of(OverlayPreferences.getSpeechAwsRegion()));
        b.credentialsProvider(resolveSpeechCredentialsProvider());
        this.polly = b.build();
        this.cachePathStripes = new Object[64];
        for (int i = 0; i < cachePathStripes.length; i++) {
            cachePathStripes[i] = new Object();
        }
    }

    private Object stripeFor(Path wav) {
        int h = wav.hashCode() ^ (wav.hashCode() >>> 16);
        return cachePathStripes[Math.floorMod(h, cachePathStripes.length)];
    }

    private static AwsCredentialsProvider resolveSpeechCredentialsProvider() {
        String profile = OverlayPreferences.getSpeechAwsProfile();
        if (profile != null && !profile.isBlank()) {
            return ProfileCredentialsProvider.builder()
                    .profileName(profile.trim())
                    .build();
        }
        return DefaultCredentialsProvider.create();
    }

    static boolean isMissingAwsCredentialsError(Throwable t) {
        for (Throwable x = t; x != null; x = x.getCause()) {
            if (x instanceof SdkClientException sce) {
                String m = sce.getMessage();
                if (m != null && m.contains("Unable to load credentials")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void reportMissingAwsCredentialsIfNeeded(String voiceNameForUi) {
        String label = (voiceNameForUi == null || voiceNameForUi.isBlank()) ? "TTS" : voiceNameForUi;
        if (AWS_CREDENTIAL_POPUP_SHOWN.compareAndSet(false, true)) {
            showMissingAwsTtsKeyPopup(label);
        } else {
            System.err.println("[EDO] TTS skipped: AWS credentials not configured (Amazon Polly). Voice: " + label);
        }
    }

    public ExecutorService getPlaybackQueue() {
        return PLAYBACK_QUEUE;
    }

    public void speak(String text) {
        if (isTestSpeechSuppressed() || text == null || text.isBlank()) {
            return;
        }
        PLAYBACK_QUEUE.submit(() -> {
            try {
                speakBlocking(text);
            } catch (Exception e) {
                if (isMissingAwsCredentialsError(e)) {
                    System.err.println("[EDO] TTS skipped: Amazon Polly needs AWS credentials (see earlier dialog or Preferences).");
                } else {
                    e.printStackTrace();
                }
            }
        });
    }

    public void speakBlocking(String text) throws Exception {
        if (isTestSpeechSuppressed()) {
            return;
        }
        Objects.requireNonNull(text, "text");

        VoiceSettings s = resolveVoiceSettings(null);
        Path wavFile = getOrCreateCachedWav(text, s.voiceName, s.engine, s.sampleRate);
        if (wavFile == null) {
            return;
        }
        playWavBlockingInternal(wavFile);
    }

    public Path ensureCachedWav(String text) throws Exception {
        if (text == null || text.isBlank()) {
            return null;
        }
        VoiceSettings s = resolveVoiceSettings(null);
        return getOrCreateCachedWav(text, s.voiceName, s.engine, s.sampleRate);
    }

    public List<Path> ensureCachedWavs(List<String> utterances) throws Exception {
        if (utterances == null || utterances.isEmpty()) {
            return List.of();
        }

        List<Path> out = new ArrayList<>();
        for (String u : utterances) {
            Path p = ensureCachedWav(u);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Option B (SSML marks): synthesize the full SSML sentence once, get SSML-mark times,
     * then cache each chunk by slicing the full PCM between marks.
     *
     * This overload keys each chunk by its literal chunk text (legacy behavior).
     */
    public List<Path> ensureCachedWavsFromSsmlMarks(List<String> chunkTexts, String ssml, List<String> markNames) throws Exception {
        return ensureCachedWavsFromSsmlMarks(chunkTexts, chunkTexts, ssml, markNames, null);
    }

    /**
     * Option B (SSML marks) with explicit cache keys per chunk.
     *
     * chunkTexts: what Polly speaks (used for SSML and manifest text)
     * cacheKeys : what we use to compute the cache file path (lets you include context like END vs MID)
     * markNames : one mark per chunk, in order, present in the SSML as <mark name="..."/>
     */
    public List<Path> ensureCachedWavsFromSsmlMarks(List<String> chunkTexts, List<String> cacheKeys, String ssml, List<String> markNames) throws Exception {
        return ensureCachedWavsFromSsmlMarks(chunkTexts, cacheKeys, ssml, markNames, null);
    }

    /**
     * Same as {@link #ensureCachedWavsFromSsmlMarks(List, List, String, List)} with an optional voice name
     * override for preview (non-persisted UI selection).
     */
    public List<Path> ensureCachedWavsFromSsmlMarks(List<String> chunkTexts, List<String> cacheKeys, String ssml,
            List<String> markNames, SpeechSynthesisVoicePreview voicePreview) throws Exception {
        if (chunkTexts == null || markNames == null || chunkTexts.isEmpty()) {
            return List.of();
        }
        if (cacheKeys == null) {
            cacheKeys = chunkTexts;
        }
        if (chunkTexts.size() != markNames.size()) {
            throw new IllegalArgumentException("chunkTexts.size != markNames.size");
        }
        if (cacheKeys.size() != chunkTexts.size()) {
            throw new IllegalArgumentException("cacheKeys.size != chunkTexts.size");
        }
        if (ssml == null || ssml.isBlank()) {
            throw new IllegalArgumentException("ssml is blank");
        }

        VoiceSettings s = resolveVoiceSettings(voicePreview);
        Path voiceDir = getVoiceCacheDir(s.voiceName, s.engine, s.sampleRate);
        Files.createDirectories(voiceDir);

        // Determine which chunk wavs are missing.
        List<Path> paths = new ArrayList<>(chunkTexts.size());
        List<Integer> missingIdx = new ArrayList<>();

        for (int i = 0; i < chunkTexts.size(); i++) {
            String spoken = chunkTexts.get(i);
            if (spoken == null || spoken.isBlank()) {
                paths.add(null);
                continue;
            }

            String keyText = cacheKeys.get(i);
            if (keyText == null || keyText.isBlank()) {
                keyText = spoken;
            }

            Path wav = resolveCachedWavPathWithFallbacks(voiceDir, s, keyText, spoken);
            paths.add(wav);
            if (!Files.exists(wav)) {
                missingIdx.add(i);
            }
        }

        if (missingIdx.isEmpty()) {
            return paths;
        }

        

if (!OverlayPreferences.isSpeechUseAwsSynthesis()) {
    List<String> missing = new ArrayList<>();
    for (int idx : missingIdx) {
        String spoken = chunkTexts.get(idx);
        String keyText = cacheKeys.get(idx);
        boolean end = isEndOfSentenceKey(keyText);
        String label = end ? "END" : "MID";
        if (spoken == null || spoken.isBlank()) {
            missing.add(label + ": <blank>");
        } else {
            missing.add(label + ": " + spoken);
        }
    }
    showMissingSpeechCachePopup(s.voiceName, voiceDir, missing);
    return paths;
}

// Synthesize full sentence audio once, and SSML mark times once.
        byte[] fullPcm = synthesizePcmSsml(ssml, s);
        Map<String, Integer> markTimesMs = synthesizeSsmlMarkTimes(ssml, s);

        int totalSamples = fullPcm.length / 2; // 16-bit mono
        int totalMs = (int) ((totalSamples * 1000L) / s.sampleRate);

        for (int idx : missingIdx) {
            String mark = markNames.get(idx);
            int startMs = resolveSsmlMarkStartMs(idx, mark, markNames, markTimesMs);

            int endMs = resolveSsmlMarkEndMs(idx, markNames, markTimesMs, totalMs);

            if (endMs < startMs) {
                endMs = startMs;
            }

            int startSample = (int) ((startMs * (long) s.sampleRate) / 1000L);
            int endSample = (int) ((endMs * (long) s.sampleRate) / 1000L);

            int startByte = Math.max(0, Math.min(fullPcm.length, startSample * 2));
            int endByte = Math.max(startByte, Math.min(fullPcm.length, endSample * 2));
            if (idx + 1 >= markNames.size()) {
                endByte = fullPcm.length;
            }

            byte[] slice = java.util.Arrays.copyOfRange(fullPcm, startByte, endByte);
            String spokenTrim = chunkTexts.get(idx).trim();
            boolean tinyLetterOrDigit = spokenTrim.length() == 1
                    && Character.isLetterOrDigit(spokenTrim.charAt(0));
            int minSliceBytes = minSsmlMarkSliceBytesForTinyToken(s);
            boolean isolatedSynth = tinyLetterOrDigit && slice.length < minSliceBytes;
            if (isolatedSynth) {
                slice = synthesizePcmPlainText(spokenTrim, s);
            }

            Path wavOut = paths.get(idx);

            synchronized (stripeFor(wavOut)) {
                if (Files.exists(wavOut)) {
                    continue;
                }
                writePcmBytesAsWav(slice, wavOut, s.sampleRate, isolatedSynth);
                writeManifestLine(voiceDir, voiceDir.relativize(wavOut).toString(), chunkTexts.get(idx));
            }
        }

        return paths;
    }

    /**
     * SSML mark times for adjacent {@code <mark/>} + short text can sit within a few milliseconds of each other;
     * slicing then yields nearly empty PCM for single-letter body tokens. Require at least ~38 ms before trusting
     * the slice; shorter slices for one character are re-synthesized as plain text.
     */
    private static int minSsmlMarkSliceBytesForTinyToken(VoiceSettings s) {
        return Math.max(256, (int) (2L * s.sampleRate * 38L / 1000L));
    }

    private byte[] synthesizePcmPlainText(String text, VoiceSettings s) throws IOException {
        VoiceId voiceId = resolveVoiceId(s.voiceName);
        if (voiceId == null) {
            throw new IllegalArgumentException("Unknown Polly voice: " + s.voiceName);
        }
        SynthesizeSpeechRequest req = SynthesizeSpeechRequest.builder()
                .engine(s.engine)
                .voiceId(voiceId)
                .outputFormat(OutputFormat.PCM)
                .sampleRate(Integer.toString(s.sampleRate))
                .textType(TextType.TEXT)
                .text(text)
                .build();
        try (ResponseInputStream<SynthesizeSpeechResponse> audio = polly.synthesizeSpeech(req)) {
            return audio.readAllBytes();
        } catch (Exception e) {
            if (isMissingAwsCredentialsError(e)) {
                reportMissingAwsCredentialsIfNeeded(s.voiceName);
                throw new IOException("AWS Polly credentials not configured", e);
            }
            if (e instanceof IOException ioe) {
                throw ioe;
            }
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IOException(e);
        }
    }

    public void playWavBlocking(Path wavPath) {
        if (isTestSpeechSuppressed()) {
            return;
        }
        playWavBlockingInternal(wavPath);
    }

    /**
     * Plays a list of WAV files as one continuous stream using a single Clip.
     * All WAVs must share the same AudioFormat.
     * Adjacent chunks are merged with a short linear crossfade to reduce clicks when sample
     * levels jump between separately cached clips.
     */
    void playCombinedWavsBlocking(List<Path> wavPaths) throws Exception {
        if (isTestSpeechSuppressed()) {
            return;
        }
        if (wavPaths == null || wavPaths.isEmpty()) {
            return;
        }

        List<byte[]> pcmChunks = new ArrayList<>();
        AudioFormat format = null;

        for (Path p : wavPaths) {
            if (p == null || !Files.exists(p)) {
                continue;
            }
            try (AudioInputStream ais = AudioSystem.getAudioInputStream(p.toFile())) {
                AudioFormat f = ais.getFormat();
                if (format == null) {
                    format = f;
                } else if (!formatsEquivalent(format, f)) {
                    throw new IllegalArgumentException("WAV format mismatch: " + p);
                }
                pcmChunks.add(ais.readAllBytes());
            }
        }

        if (pcmChunks.isEmpty()) {
            return;
        }

        int frameSize = format.getFrameSize();
        if (frameSize <= 0) {
            throw new IllegalArgumentException("Unsupported AudioFormat frame size");
        }

        byte[] mergedPcm;
        if (pcmChunks.size() == 1) {
            mergedPcm = pcmChunks.get(0);
        } else {
            mergedPcm = mergePcm16ChunksWithCrossfade(pcmChunks, format,
                    Math.max(4, Math.min((int) (format.getSampleRate() * 4L / 1000L), 512)));
        }

        long frames = mergedPcm.length / frameSize;
        try (AudioInputStream combined = new AudioInputStream(
                new ByteArrayInputStream(mergedPcm), format, frames)) {
            playAudioBlocking(combined);
        }
    }

    /**
     * When crossfading into the next chunk, keep at least this much of the next chunk after the overlap so
     * very short clips (e.g. single-letter body tokens like {@code A}) are not reduced to an inaudible blip.
     */
    private static int minPostCrossfadeTailSamples(AudioFormat format) {
        double sr = format.getSampleRate();
        return Math.max(16, (int) Math.round(sr * 0.030)); // ~30 ms
    }

    /**
     * Linear crossfade at each join: last {@code overlapSamples} of the accumulated buffer
     * with the first {@code overlapSamples} of the next chunk (16-bit PCM, format endianness).
     */
    private static byte[] mergePcm16ChunksWithCrossfade(List<byte[]> chunks, AudioFormat format, int overlapSamples)
            throws IOException {
        if (chunks == null || chunks.isEmpty()) {
            return new byte[0];
        }
        if (format.getSampleSizeInBits() != 16 || format.getChannels() != 1) {
            throw new IOException("Crossfade merge requires 16-bit mono PCM");
        }
        boolean bigEndian = format.isBigEndian();

        short[] acc = pcmBytesToShorts16Mono(chunks.get(0), bigEndian);
        for (int ci = 1; ci < chunks.size(); ci++) {
            short[] nxt = pcmBytesToShorts16Mono(chunks.get(ci), bigEndian);
            if (nxt.length == 0) {
                continue;
            }
            if (acc.length == 0) {
                acc = nxt;
                continue;
            }
            int maxOl = Math.min(overlapSamples, Math.min(acc.length, nxt.length));
            int minTail = minPostCrossfadeTailSamples(format);
            int tailBudget = nxt.length - minTail;
            // If the next clip is shorter than ~one crossfade tail, skip blending and concatenate (prevents
            // discarding almost all of a single-letter or other micro-chunk).
            int ol = tailBudget <= 0 ? 0 : Math.min(maxOl, tailBudget);
            int accLen = acc.length;
            for (int i = 0; i < ol; i++) {
                float w = (i + 1f) / (ol + 1f);
                int a = acc[accLen - ol + i];
                int b = nxt[i];
                int blended = Math.round(a * (1f - w) + b * w);
                if (blended > Short.MAX_VALUE) {
                    blended = Short.MAX_VALUE;
                } else if (blended < Short.MIN_VALUE) {
                    blended = Short.MIN_VALUE;
                }
                acc[accLen - ol + i] = (short) blended;
            }
            short[] merged = new short[accLen + nxt.length - ol];
            System.arraycopy(acc, 0, merged, 0, accLen);
            System.arraycopy(nxt, ol, merged, accLen, nxt.length - ol);
            acc = merged;
        }
        return shortsToPcmBytes16Mono(acc, bigEndian);
    }

    private static short[] pcmBytesToShorts16Mono(byte[] pcm, boolean bigEndian) {
        int len = pcm.length & ~1;
        int n = len / 2;
        short[] out = new short[n];
        if (bigEndian) {
            for (int i = 0; i < n; i++) {
                out[i] = (short) ((pcm[i * 2] << 8) | (pcm[i * 2 + 1] & 0xff));
            }
        } else {
            for (int i = 0; i < n; i++) {
                out[i] = (short) (((pcm[i * 2 + 1] << 8) | (pcm[i * 2] & 0xff)));
            }
        }
        return out;
    }

    private static byte[] shortsToPcmBytes16Mono(short[] samples, boolean bigEndian) {
        byte[] b = new byte[samples.length * 2];
        if (bigEndian) {
            for (int i = 0; i < samples.length; i++) {
                b[i * 2] = (byte) (samples[i] >> 8);
                b[i * 2 + 1] = (byte) (samples[i] & 0xff);
            }
        } else {
            for (int i = 0; i < samples.length; i++) {
                b[i * 2] = (byte) (samples[i] & 0xff);
                b[i * 2 + 1] = (byte) (samples[i] >> 8);
            }
        }
        return b;
    }

    // ------------------------------
    // Cache implementation
    // ------------------------------

    private Path getOrCreateCachedWav(String text, String voiceName, Engine engine, int sampleRate) throws IOException {
        VoiceSettings s = new VoiceSettings(voiceName, engine, sampleRate);

        Path voiceDir = getVoiceCacheDir(voiceName, engine, sampleRate);
        Files.createDirectories(voiceDir);

        Path wav = getCachedWavPath(voiceDir, s, text);

        synchronized (stripeFor(wav)) {
            if (Files.exists(wav)) {
                return wav;
            }
            Path resolved = resolveCachedWavPathWithFallbacks(voiceDir, s, text, text);
            if (Files.exists(resolved)) {
                return resolved;
            }

            Files.createDirectories(wav.getParent());

            if (!OverlayPreferences.isSpeechUseAwsSynthesis()) {
                List<String> missing = new ArrayList<>();
                String label = isEndOfSentenceKey(text) ? "END" : "MID";
                missing.add(label + ": " + text);
                showMissingSpeechCachePopup(voiceName, voiceDir, missing);
                return null;
            }

            VoiceId voiceId = resolveVoiceId(voiceName);
            if (voiceId == null) {
                throw new IllegalArgumentException("Unknown Polly voice: " + voiceName);
            }

            SynthesizeSpeechRequest req = SynthesizeSpeechRequest.builder()
                    .engine(engine)
                    .voiceId(voiceId)
                    .outputFormat(OutputFormat.PCM)
                    .sampleRate(Integer.toString(sampleRate))
                    .textType(TextType.TEXT)
                    .text(text)
                    .build();

            Path tmp = wav.getParent().resolve(wav.getFileName().toString() + ".tmp");
            try (ResponseInputStream<SynthesizeSpeechResponse> audio = polly.synthesizeSpeech(req)) {
                writePcmAsWav(audio, tmp, sampleRate);
            } catch (Exception e) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                }
                if (isMissingAwsCredentialsError(e)) {
                    reportMissingAwsCredentialsIfNeeded(voiceName);
                    return null;
                }
                if (e instanceof IOException ioe) {
                    throw ioe;
                }
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new IOException(e);
            }

            try {
                Files.move(tmp, wav, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveEx) {
                Files.copy(tmp, wav, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(tmp);
            }

            appendToManifest(voiceDir, voiceDir.relativize(wav).toString(), text);
            return wav;
        }
    }

    private Path getCachedWavPath(Path voiceDir, VoiceSettings s, String text) {
        Path bucketDir = voiceDir.resolve(isEndOfSentenceKey(text) ? "end" : "mid");

        String key = sha256(
                "v=" + s.voiceName
                        + "|e=" + s.engine.toString()
                        + "|sr=" + s.sampleRate
                        + "|t=" + normalize(text)
                        // Bust cache when PCM handling changes (e.g. slice trim policy, last-chunk length).
                        + "|pcm=v2"
        );

        return bucketDir.resolve(key + ".wav");
    }
    private static boolean isEndOfSentenceKey(String keyText) {
        if (keyText == null) {
            return false;
        }
        String t = keyText.trim();
        return t.endsWith("|END");
    }

    /**
     * Offline packs warm digit tokens ({@code N|12|MID}) but not English number words ({@code T|twelve|MID}).
     * Map a spoken chunk to the numeric string used in those digit keys (0–99).
     */
    private static String spokenEnglishNumberToNumericToken(String spoken) {
        if (spoken == null) {
            return null;
        }
        switch (spoken.trim().toLowerCase(Locale.ROOT)) {
            case "zero":
                return "0";
            case "one":
                return "1";
            case "two":
                return "2";
            case "three":
                return "3";
            case "four":
                return "4";
            case "five":
                return "5";
            case "six":
                return "6";
            case "seven":
                return "7";
            case "eight":
                return "8";
            case "nine":
                return "9";
            case "ten":
                return "10";
            case "eleven":
                return "11";
            case "twelve":
                return "12";
            case "thirteen":
                return "13";
            case "fourteen":
                return "14";
            case "fifteen":
                return "15";
            case "sixteen":
                return "16";
            case "seventeen":
                return "17";
            case "eighteen":
                return "18";
            case "nineteen":
                return "19";
            case "twenty":
                return "20";
            case "thirty":
                return "30";
            case "forty":
                return "40";
            case "fifty":
                return "50";
            case "sixty":
                return "60";
            case "seventy":
                return "70";
            case "eighty":
                return "80";
            case "ninety":
                return "90";
            default:
                return null;
        }
    }

    /** {@code |END} only when {@code keyText} ends with {@code |END}; otherwise {@code |MID} (incl. plain keys). */
    private static String midEndSuffixForDigitFallback(String keyText) {
        if (keyText != null && keyText.trim().endsWith("|END")) {
            return "|END";
        }
        return "|MID";
    }

    /** Middle segment of {@code T|spoken|…} keys; otherwise the whole string (e.g. raw {@code one}). */
    private static String extractSpokenFromTypedCacheKey(String keyText) {
        if (keyText == null) {
            return "";
        }
        String t = keyText.trim();
        if (t.startsWith("T|")) {
            int a = t.indexOf('|');
            int b = t.indexOf('|', a + 1);
            if (b > a) {
                return t.substring(a + 1, b);
            }
        }
        return t;
    }

    /**
     * Prefer the exact cache path; else typed-word and digit fallbacks for number words.
     * <p>
     * Legacy/raw callers may request plain text (for example {@code "hundred"}) while voice packs warm typed keys
     * such as {@code T|hundred|MID}/{@code T|hundred|END}. Try the typed-word key first, then a digit token
     * fallback ({@code N|0}–{@code N|99}) with matching {@code |MID}/{@code |END} — never the other position.
     * </p>
     */
    private Path resolveCachedWavPathWithFallbacks(Path voiceDir, VoiceSettings s, String keyText, String spokenChunk) {
        Path primary = getCachedWavPath(voiceDir, s, keyText);
        if (Files.exists(primary)) {
            return primary;
        }
        String sfx = midEndSuffixForDigitFallback(keyText);
        String spoken = spokenChunk != null && !spokenChunk.isBlank()
                ? spokenChunk.trim()
                : extractSpokenFromTypedCacheKey(keyText);
        if (keyText != null && !keyText.isBlank() && !keyText.trim().startsWith("T|")
                && spoken != null && !spoken.isBlank()) {
            Path typedWord = getCachedWavPath(voiceDir, s, "T|" + spoken + sfx);
            if (Files.exists(typedWord)) {
                return typedWord;
            }
        }
        String numTok = spokenEnglishNumberToNumericToken(spoken);
        if (numTok != null) {
            Path alt = getCachedWavPath(voiceDir, s, "N|" + numTok + sfx);
            if (Files.exists(alt)) {
                return alt;
            }
        }
        return primary;
    }

    private volatile boolean cacheDirLogged = false;

    private Path getVoiceCacheDir(String voiceName, Engine engine, int sampleRate) {
        Path root = OverlayPreferences.getSpeechCacheDir();

        // Separate directories per voice
        String safeVoice = voiceName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
        Path voiceDir = root.resolve(safeVoice);

        if (!cacheDirLogged) {
            cacheDirLogged = true;
            System.out.println("TTS cache root = " + root.toAbsolutePath());
            System.out.println("TTS voice dir  = " + voiceDir.toAbsolutePath());
        }

        return voiceDir;
    }

    private void appendToManifest(Path voiceDir, String fileName, String text) {
        synchronized (manifestLock) {
            Path manifest = voiceDir.resolve("manifest.tsv");
            String safe = (text == null) ? "" : text.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
            String line = Instant.now() + "\t" + fileName + "\t" + safe + System.lineSeparator();
            try {
                Files.writeString(
                        manifest,
                        line,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND
                );
            } catch (Exception e) {
                // ignore
            }
        }
    }  // <-- add this

    private void writeManifestLine(Path voiceDir, String fileName, String spokenText) {
        appendToManifest(voiceDir, fileName, spokenText);
    }


    // ------------------------------
    // Polly synth helpers
    // ------------------------------

    private byte[] synthesizePcmSsml(String ssml, VoiceSettings s) throws IOException {
        VoiceId voiceId = resolveVoiceId(s.voiceName);
        if (voiceId == null) {
            throw new IllegalArgumentException("Unknown Polly voice: " + s.voiceName);
        }

        SynthesizeSpeechRequest req = SynthesizeSpeechRequest.builder()
                .engine(s.engine)
                .voiceId(voiceId)
                .outputFormat(OutputFormat.PCM)
                .sampleRate(Integer.toString(s.sampleRate))
                .textType(TextType.SSML)
                .text(ssml)
                .build();

        try (ResponseInputStream<SynthesizeSpeechResponse> audio = polly.synthesizeSpeech(req)) {
            return audio.readAllBytes();
        } catch (Exception e) {
            if (isMissingAwsCredentialsError(e)) {
                reportMissingAwsCredentialsIfNeeded(s.voiceName);
                throw new IOException("AWS Polly credentials not configured", e);
            }
            if (e instanceof IOException ioe) {
                throw ioe;
            }
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IOException(e);
        }
    }

    public static void showMissingAwsTtsKeyPopup(String voiceName) {
        String url = "https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html";

        String html =
                "<html>"
              + "<body style='font-family:sans-serif; font-size:12px;'>"
              + "Could not use Amazon Polly for <b>" + escapeHtml(voiceName) + "</b> because no AWS credentials were found.<br><br>"
              + "Fix: set environment variables <code>AWS_ACCESS_KEY_ID</code> and <code>AWS_SECRET_ACCESS_KEY</code>, "
              + "or create a credentials file (see link below). In EDO Preferences you can set the AWS region and optional profile name.<br><br>"
              + "Alternatively, turn off on-demand AWS synthesis and use only cached speech clips if you already have them.<br><br>"
              + "IAM access keys overview:<br>"
              + "<a href='" + url + "'>" + url + "</a>"
              + "</body>"
              + "</html>";

        JEditorPane pane = new JEditorPane("text/html", html);
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        pane.addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED)
                {
                    return;
                }

                URI uri = e.getURL() != null ? URI.create(e.getURL().toString()) : null;
                if (uri == null)
                {
                    return;
                }

                if (!Desktop.isDesktopSupported())
                {
                    return;
                }

                try {
                    Desktop.getDesktop().browse(uri);
                    Window window = SwingUtilities.getWindowAncestor(pane);
                    if (window != null)
                    {
                        window.dispose();
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(
                            speechDialogParentComponent(),
                            "Could not open your browser.\n\n" + url,
                            "Open Link Failed",
                            JOptionPane.WARNING_MESSAGE
                    );
                }
            }
        });

        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        JOptionPane.showMessageDialog(
                speechDialogParentComponent(),
                scroll,
                "Text-to-Speech Unavailable",
                JOptionPane.WARNING_MESSAGE
        );
    }

    private static final int SPEECH_CACHE_MISS_BANNER_MAX_SPOKEN_CHARS = 40;

    /** Spoken text from a miss line such as {@code MID: Tritium} or {@code END: percent.} */
    private static String spokenFromSpeechCacheMissLine(String line) {
        if (line == null) {
            return "?";
        }
        String t = line.trim();
        int sep = t.indexOf(": ");
        if (sep >= 0 && sep + 2 <= t.length()) {
            return t.substring(sep + 2).trim();
        }
        return t;
    }

    private static String ellipsizeSpokenForBanner(String spoken, int maxChars) {
        if (spoken == null) {
            return "?";
        }
        String t = spoken.trim();
        if (t.isEmpty()) {
            return "?";
        }
        if (t.length() <= maxChars) {
            return t;
        }
        if (maxChars <= 1) {
            return "\u2026";
        }
        return t.substring(0, maxChars - 1) + "\u2026";
    }

    private static String formatSpeechCacheMissBannerText(List<String> missing) {
        String fragment = ellipsizeSpokenForBanner(
                spokenFromSpeechCacheMissLine(missing.get(0)),
                SPEECH_CACHE_MISS_BANNER_MAX_SPOKEN_CHARS);
        int n = missing.size();
        if (n <= 1) {
            return "TTS: no clip for \"" + fragment + "\" — AWS or pack";
        }
        return "TTS: no clip for \"" + fragment + "\" +" + (n - 1) + " — AWS or pack";
    }

    private static void showMissingSpeechCachePopup(String voiceName, Path voiceDir, List<String> missing) {
        if (missing == null || missing.isEmpty()) {
            return;
        }

        String vn = voiceName != null && !voiceName.isBlank() ? voiceName.trim() : "selected voice";
        String detailMsg = "Speech cache: " + missing.size() + " clip(s) missing (" + vn + ") — enable AWS or voice pack";
        Consumer<String> rep = speechCacheMissBannerReporter;
        if (rep != null) {
            String banner = formatSpeechCacheMissBannerText(missing);
            SwingUtilities.invokeLater(() -> rep.accept(banner));
        } else {
            System.out.println("[EDO] " + detailMsg);
            if (voiceDir != null) {
                System.out.println("[EDO] Cache dir: " + voiceDir.toAbsolutePath());
            }
            for (String m : missing) {
                if (m != null && !m.isBlank()) {
                    System.out.println("[EDO]   " + m);
                }
            }
        }
    }

    private static String escapeHtml(String s) {
        if (s == null)
        {
            return "";
        }

        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }


    private Map<String, Integer> synthesizeSsmlMarkTimes(String ssml, VoiceSettings s) throws IOException {
        VoiceId voiceId = resolveVoiceId(s.voiceName);
        if (voiceId == null) {
            throw new IllegalArgumentException("Unknown Polly voice: " + s.voiceName);
        }

        SynthesizeSpeechRequest req = SynthesizeSpeechRequest.builder()
                .engine(s.engine)
                .voiceId(voiceId)
                .outputFormat(OutputFormat.JSON)
                .speechMarkTypes(SpeechMarkType.SSML)
                .textType(TextType.SSML)
                .text(ssml)
                .build();

        try (ResponseInputStream<SynthesizeSpeechResponse> marks = polly.synthesizeSpeech(req)) {
            String jsonLines = new String(marks.readAllBytes(), StandardCharsets.UTF_8);
            return parseSsmlMarks(jsonLines);
        } catch (Exception e) {
            if (isMissingAwsCredentialsError(e)) {
                reportMissingAwsCredentialsIfNeeded(s.voiceName);
                throw new IOException("AWS Polly credentials not configured", e);
            }
            if (e instanceof IOException ioe) {
                throw ioe;
            }
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IOException(e);
        }
    }

    private static Map<String, Integer> parseSsmlMarks(String jsonLines) {
        // Polly returns JSON Lines; we only care about ssml marks: {"time":123,"type":"ssml","value":"C0"}
        Map<String, Integer> out = new LinkedHashMap<>();
        if (jsonLines == null || jsonLines.isBlank()) {
            return out;
        }

        String[] lines = jsonLines.split("\\r?\\n");
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }

            String type = extractJsonString(line, "type");
            if (!"ssml".equalsIgnoreCase(type)) {
                continue;
            }

            String value = extractJsonString(line, "value");
            String time = extractJsonNumber(line, "time");
            if (value == null || time == null) {
                continue;
            }

            try {
                out.put(value, Integer.parseInt(time));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return out;
    }

    /**
     * Polly sometimes omits the first SSML mark timestamp (often {@code C0}) in the JSON marks response,
     * especially for child voices (e.g. Ivy). Treat a missing first mark as 0 ms; later missing marks inherit
     * the nearest preceding mark time.
     */
    static int resolveSsmlMarkStartMs(int chunkIdx, String mark, List<String> markNames,
            Map<String, Integer> markTimesMs) {
        Integer t = markTimesMs.get(mark);
        if (t != null) {
            return t;
        }
        if (chunkIdx <= 0) {
            return 0;
        }
        for (int i = chunkIdx - 1; i >= 0; i--) {
            Integer prev = markTimesMs.get(markNames.get(i));
            if (prev != null) {
                return prev;
            }
        }
        return 0;
    }

    static int resolveSsmlMarkEndMs(int chunkIdx, List<String> markNames, Map<String, Integer> markTimesMs,
            int totalMs) {
        if (chunkIdx + 1 < markNames.size()) {
            for (int i = chunkIdx + 1; i < markNames.size(); i++) {
                Integer t = markTimesMs.get(markNames.get(i));
                if (t != null) {
                    return t;
                }
            }
        }
        return totalMs;
    }

    private static String extractJsonString(String line, String key) {
        int idx = line.indexOf("\"" + key + "\"");
        if (idx < 0) {
            return null;
        }
        int colon = line.indexOf(':', idx);
        if (colon < 0) {
            return null;
        }
        int q1 = line.indexOf('"', colon + 1);
        if (q1 < 0) {
            return null;
        }
        int q2 = line.indexOf('"', q1 + 1);
        if (q2 < 0) {
            return null;
        }
        return line.substring(q1 + 1, q2);
    }

    private static String extractJsonNumber(String line, String key) {
        int idx = line.indexOf("\"" + key + "\"");
        if (idx < 0) {
            return null;
        }
        int colon = line.indexOf(':', idx);
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        int j = i;
        while (j < line.length() && Character.isDigit(line.charAt(j))) {
            j++;
        }
        if (j == i) {
            return null;
        }
        return line.substring(i, j);
    }

    // ------------------------------
    // Playback helpers
    // ------------------------------

    private static boolean isTestSpeechSuppressed() {
        return Boolean.getBoolean("edo.test.disableSpeech");
    }

    private static void playWavBlockingInternal(Path wavPath) {
        if (isTestSpeechSuppressed() || wavPath == null) {
            return;
        }

        try (AudioInputStream ais = AudioSystem.getAudioInputStream(wavPath.toFile())) {
            Clip clip = AudioSystem.getClip();

            CountDownLatch done = new CountDownLatch(1);
            clip.addLineListener(e -> {
                if (e.getType() == LineEvent.Type.STOP || e.getType() == LineEvent.Type.CLOSE) {
                    done.countDown();
                }
            });

            clip.open(ais);
            clip.start();

            done.await();
            clip.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playAudioBlocking(AudioInputStream ais) throws Exception {
        if (isTestSpeechSuppressed()) {
            return;
        }
        Clip clip = AudioSystem.getClip();
        CountDownLatch done = new CountDownLatch(1);

        clip.addLineListener(e -> {
            if (e.getType() == LineEvent.Type.STOP || e.getType() == LineEvent.Type.CLOSE) {
                done.countDown();
            }
        });

        clip.open(ais);
        clip.start();
        done.await();
        clip.close();
    }

    private static boolean formatsEquivalent(AudioFormat a, AudioFormat b) {
        if (!Objects.equals(a.getEncoding(), b.getEncoding())) {
            return false;
        }
        if (a.getSampleRate() != b.getSampleRate()) {
            return false;
        }
        if (a.getSampleSizeInBits() != b.getSampleSizeInBits()) {
            return false;
        }
        if (a.getChannels() != b.getChannels()) {
            return false;
        }
        if (a.isBigEndian() != b.isBigEndian()) {
            return false;
        }
        if (a.getFrameSize() != b.getFrameSize()) {
            return false;
        }
        return a.getFrameRate() == b.getFrameRate();
    }

    // ------------------------------
    // WAV writing + trimming
    // ------------------------------

    private static void writePcmAsWav(InputStream pcm, Path wavOut, int sampleRate) throws IOException {
        byte[] pcmBytes = pcm.readAllBytes();
        writePcmBytesAsWav(pcmBytes, wavOut, sampleRate, true);
    }

    private static void writePcmBytesAsWav(byte[] pcmBytes, Path wavOut, int sampleRate) throws IOException {
        writePcmBytesAsWav(pcmBytes, wavOut, sampleRate, true);
    }

    private static void writePcmBytesAsWav(byte[] pcmBytes, Path wavOut, int sampleRate, boolean trimSilence)
            throws IOException {
        if (pcmBytes == null) {
            pcmBytes = new byte[0];
        }

        Files.createDirectories(wavOut.getParent());

        if (trimSilence) {
            pcmBytes = trimSilencePcm16leMono(pcmBytes, sampleRate, TRIM_ABS_THRESHOLD, TRIM_KEEP_MS);
        }

        int numChannels = 1;
        int bitsPerSample = 16;
        int byteRate = sampleRate * numChannels * bitsPerSample / 8;
        int blockAlign = numChannels * bitsPerSample / 8;

        int subchunk2Size = pcmBytes.length;
        int chunkSize = 36 + subchunk2Size;

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(
                wavOut, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)))) {

            out.writeBytes("RIFF");
            writeIntLE(out, chunkSize);
            out.writeBytes("WAVE");

            out.writeBytes("fmt ");
            writeIntLE(out, 16);
            writeShortLE(out, (short) 1);
            writeShortLE(out, (short) numChannels);
            writeIntLE(out, sampleRate);
            writeIntLE(out, byteRate);
            writeShortLE(out, (short) blockAlign);
            writeShortLE(out, (short) bitsPerSample);

            out.writeBytes("data");
            writeIntLE(out, subchunk2Size);
            out.write(pcmBytes);
        }
    }

    private static byte[] trimSilencePcm16leMono(byte[] pcm, int sampleRate, int absThreshold, int keepMs) {
        if (pcm == null || pcm.length < 4) {
            return pcm;
        }

        int len = pcm.length & ~1;
        if (len < 2) {
            return pcm;
        }

        int keepSamples = (int) ((keepMs / 1000.0) * sampleRate);
        if (keepSamples < 0) {
            keepSamples = 0;
        }
        int keepBytes = keepSamples * 2;

        int start = 0;
        while (start + 1 < len) {
            int sample = (short) ((pcm[start + 1] << 8) | (pcm[start] & 0xFF));
            if (Math.abs(sample) > absThreshold) {
                break;
            }
            start += 2;
        }

        int end = len - 2;
        while (end >= 0) {
            int sample = (short) ((pcm[end + 1] << 8) | (pcm[end] & 0xFF));
            if (Math.abs(sample) > absThreshold) {
                break;
            }
            end -= 2;
        }

        if (end < start) {
            int outLen = keepBytes;
            if (outLen < 2) {
                outLen = 2;
            }
            if (outLen > len) {
                outLen = len;
            }

            byte[] out = new byte[outLen];
            System.arraycopy(pcm, 0, out, 0, outLen);
            return out;
        }

        start -= keepBytes;
        if (start < 0) {
            start = 0;
        }

        end += keepBytes;
        if (end > len - 2) {
            end = len - 2;
        }

        int outLen = (end - start) + 2;
        if (outLen <= 0) {
            return pcm;
        }

        byte[] out = new byte[outLen];
        System.arraycopy(pcm, start, out, 0, outLen);
        return out;
    }

    private static void writeIntLE(DataOutputStream out, int v) throws IOException {
        out.writeByte(v & 0xFF);
        out.writeByte((v >>> 8) & 0xFF);
        out.writeByte((v >>> 16) & 0xFF);
        out.writeByte((v >>> 24) & 0xFF);
    }

    private static void writeShortLE(DataOutputStream out, short v) throws IOException {
        out.writeByte(v & 0xFF);
        out.writeByte((v >>> 8) & 0xFF);
    }

    // ------------------------------
    // Settings + utils
    // ------------------------------

    private VoiceSettings resolveVoiceSettings(SpeechSynthesisVoicePreview voicePreview) {
        String voiceName = OverlayPreferences.getSpeechVoiceName();
        if (voicePreview != null && voicePreview.voiceName() != null && !voicePreview.voiceName().isBlank()) {
            voiceName = voicePreview.voiceName().trim();
        }
        if (voiceName == null || voiceName.isBlank()) {
            voiceName = "Matthew";
        }

        Engine engine = OverlayPreferences.getSpeechEngine();
        if (engine == null) {
            engine = Engine.NEURAL;
        }

        int sampleRate = OverlayPreferences.getSpeechSampleRateHz();
        if (sampleRate != 8000 && sampleRate != 16000) {
            sampleRate = 16000;
        }

        return new VoiceSettings(voiceName, engine, sampleRate);
    }


private static VoiceId resolveVoiceId(String voiceName) {
    if (voiceName == null) {
        return null;
    }

    String v = voiceName.trim();
    if (v.isEmpty() || "null".equalsIgnoreCase(v)) {
        return null;
    }

    try {
        VoiceId id = VoiceId.fromValue(v);
        if (!VoiceId.UNKNOWN_TO_SDK_VERSION.equals(id)) {
            return id;
        }
    } catch (Exception ignored) {
    }

    String normalized = v.toLowerCase(Locale.ROOT);
    for (VoiceId id : VoiceId.values()) {
        if (VoiceId.UNKNOWN_TO_SDK_VERSION.equals(id)) {
            continue;
        }
        if (id.toString().toLowerCase(Locale.ROOT).equals(normalized)) {
            return id;
        }
    }

    return null;
}


    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().replaceAll("\s+", " ");
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final class VoiceSettings {
        private final String voiceName;
        private final Engine engine;
        private final int sampleRate;

        private VoiceSettings(String voiceName, Engine engine, int sampleRate) {
            this.voiceName = voiceName;
            this.engine = engine;
            this.sampleRate = sampleRate;
        }
    }

    @Override
    public void close() {
        try {
            polly.close();
        } catch (Exception e) {
            // ignore
        }
    }

    public static void main(String[] args) {
        System.out.println("Starting");
        try (PollyTtsCached tts = new PollyTtsCached()) {
            tts.speak("hello");
            tts.speak("this should play after hello finishes");
            tts.speak("and this should play after that");
            System.out.println("Queued.");
            Thread.sleep(4000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}