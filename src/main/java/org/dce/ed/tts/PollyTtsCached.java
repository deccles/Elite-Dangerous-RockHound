package org.dce.ed.tts;

import java.awt.Desktop;
import java.awt.Window;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
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

    /** Avoid spamming the same modal when many TTS calls fail for the same reason. */
    private static final AtomicBoolean AWS_CREDENTIAL_POPUP_SHOWN = new AtomicBoolean(false);

    // Trim leading/trailing silence from Polly PCM before writing cache WAVs.
    private static final int TRIM_ABS_THRESHOLD = 250;  // 16-bit PCM amplitude threshold (0..32767)
    private static final int TRIM_KEEP_MS = 1;          // you tuned this down and liked it

    // Speech-mark parsing (JSON lines)

    public static final List<String> STANDARD_US_ENGLISH_VOICES = List.of(
            "Ivy", "Joanna", "Kendra", "Kimberly", "Salli", "Joey", "Justin", "Matthew"
    );

    // Single-thread executor = non-blocking callers, but sequential playback.
    private static final ExecutorService PLAYBACK_QUEUE  = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PollyTtsCached-Playback");
        t.setDaemon(true);
        return t;
    });

    private final PollyClient polly;
    private final Object manifestLock = new Object();

    public PollyTtsCached() {
        var b = PollyClient.builder()
                .region(software.amazon.awssdk.regions.Region.of(OverlayPreferences.getSpeechAwsRegion()));
        b.credentialsProvider(resolveSpeechCredentialsProvider());
        this.polly = b.build();
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
        if (text == null || text.isBlank()) {
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
        Objects.requireNonNull(text, "text");

        VoiceSettings s = resolveVoiceSettings();
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
        VoiceSettings s = resolveVoiceSettings();
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
        return ensureCachedWavsFromSsmlMarks(chunkTexts, chunkTexts, ssml, markNames);
    }

    /**
     * Option B (SSML marks) with explicit cache keys per chunk.
     *
     * chunkTexts: what Polly speaks (used for SSML and manifest text)
     * cacheKeys : what we use to compute the cache file path (lets you include context like END vs MID)
     * markNames : one mark per chunk, in order, present in the SSML as <mark name="..."/>
     */
    public List<Path> ensureCachedWavsFromSsmlMarks(List<String> chunkTexts, List<String> cacheKeys, String ssml, List<String> markNames) throws Exception {
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

        VoiceSettings s = resolveVoiceSettings();
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

            Path wav = getCachedWavPath(voiceDir, s, keyText);
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
            Integer startMsObj = markTimesMs.get(mark);
            if (startMsObj == null) {
                throw new IllegalStateException("Missing SSML mark time for: " + mark);
            }
            int startMs = startMsObj;

            int endMs;
            if (idx + 1 < markNames.size()) {
                String nextMark = markNames.get(idx + 1);
                Integer endMsObj = markTimesMs.get(nextMark);
                if (endMsObj == null) {
                    // If the next mark is missing for some reason, fall back to end of audio.
                    endMs = totalMs;
                } else {
                    endMs = endMsObj;
                }
            } else {
                endMs = totalMs;
            }

            if (endMs < startMs) {
                endMs = startMs;
            }

            int startSample = (int) ((startMs * (long) s.sampleRate) / 1000L);
            int endSample = (int) ((endMs * (long) s.sampleRate) / 1000L);

            int startByte = Math.max(0, Math.min(fullPcm.length, startSample * 2));
            int endByte = Math.max(startByte, Math.min(fullPcm.length, endSample * 2));

            byte[] slice = java.util.Arrays.copyOfRange(fullPcm, startByte, endByte);

            Path wavOut = paths.get(idx);
            writePcmBytesAsWav(slice, wavOut, s.sampleRate);

            // Manifest uses what was spoken, not the cache key.
            writeManifestLine(voiceDir, voiceDir.relativize(wavOut).toString(), chunkTexts.get(idx));

        }

        return paths;
    }
    public void playWavBlocking(Path wavPath) {
        playWavBlockingInternal(wavPath);
    }

    /**
     * Plays a list of WAV files as one continuous stream using a single Clip.
     * All WAVs must share the same AudioFormat.
     */
    void playCombinedWavsBlocking(List<Path> wavPaths) throws Exception {
        if (wavPaths == null || wavPaths.isEmpty()) {
            return;
        }

        List<AudioInputStream> streams = new ArrayList<>();
        AudioFormat format = null;

        try {
            for (Path p : wavPaths) {
                if (p == null) {
                    continue;
                }
                if (!Files.exists(p)) {
                    continue;
                }

                AudioInputStream ais = AudioSystem.getAudioInputStream(p.toFile());
                AudioFormat f = ais.getFormat();

                if (format == null) {
                    format = f;
                } else if (!formatsEquivalent(format, f)) {
                    ais.close();
                    throw new IllegalArgumentException("WAV format mismatch: " + p);
                }

                streams.add(ais);
            }

            if (streams.isEmpty()) {
                return;
            }

            Vector<InputStream> inputs = new Vector<>();
            for (AudioInputStream ais : streams) {
                inputs.add(ais);
            }
            Enumeration<InputStream> en = inputs.elements();
            SequenceInputStream seq = new SequenceInputStream(en);

            // Frame length unknown is fine for Clip in practice; compute if available.
            long totalFrames = 0;
            boolean anyKnown = false;
            for (AudioInputStream ais : streams) {
                long fl = ais.getFrameLength();
                if (fl > 0) {
                    anyKnown = true;
                    totalFrames += fl;
                }
            }
            long combinedFrames = anyKnown ? totalFrames : AudioSystem.NOT_SPECIFIED;

            try (AudioInputStream combined = new AudioInputStream(seq, format, combinedFrames)) {
                playAudioBlocking(combined);
            }
        } finally {
            for (AudioInputStream ais : streams) {
                try {
                    ais.close();
                } catch (Exception ignore) {
                    // ignore
                }
            }
        }
    }

    // ------------------------------
    // Cache implementation
    // ------------------------------

    private Path getOrCreateCachedWav(String text, String voiceName, Engine engine, int sampleRate) throws IOException {
        VoiceSettings s = new VoiceSettings(voiceName, engine, sampleRate);

        Path voiceDir = getVoiceCacheDir(voiceName, engine, sampleRate);
        Files.createDirectories(voiceDir);

        // Preferred location (new structure)
        Path wav = getCachedWavPath(voiceDir, s, text);
        if (Files.exists(wav)) {
            return wav;
        }

        // Make sure the subdir exists (end/ or mid/)
        Files.createDirectories(wav.getParent());
if (!OverlayPreferences.isSpeechUseAwsSynthesis()) {
    List<String> missing = new ArrayList<>();
    String label = isEndOfSentenceKey(text) ? "END" : "MID";
    missing.add(label + ": " + text);
    showMissingSpeechCachePopup(voiceName, voiceDir, missing);
    return null;
}


        // Generate PCM with Polly (TEXT) and cache it.
        VoiceId voiceId = resolveVoiceId(voiceName);
        if (voiceId == null) {
            throw new IllegalArgumentException("Unknown Polly voice: " + voiceName);
        }

        SynthesizeSpeechRequest req = SynthesizeSpeechRequest.builder()
                .engine(engine)
                .voiceId(voiceId)
                .outputFormat(OutputFormat.PCM) // PCM 16-bit signed little-endian mono
                .sampleRate(Integer.toString(sampleRate))
                .textType(TextType.TEXT)
                .text(text)
                .build();

        // tmp in the SAME directory as final target (atomic move works on more filesystems)
        Path tmp = wav.getParent().resolve(wav.getFileName().toString() + ".tmp");
        try (ResponseInputStream<SynthesizeSpeechResponse> audio = polly.synthesizeSpeech(req)) {
            writePcmAsWav(audio, tmp, sampleRate);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // ignore
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
            Files.move(tmp, wav);
        } catch (IOException moveEx) {
            Files.copy(tmp, wav);
            Files.deleteIfExists(tmp);
        }

        appendToManifest(voiceDir, voiceDir.relativize(wav).toString(), text);
        return wav;
    }

    private Path getCachedWavPath(Path voiceDir, VoiceSettings s, String text) {
        Path bucketDir = voiceDir.resolve(isEndOfSentenceKey(text) ? "end" : "mid");

        String key = sha256(
                "v=" + s.voiceName
                        + "|e=" + s.engine.toString()
                        + "|sr=" + s.sampleRate
                        + "|t=" + normalize(text)
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
                            null,
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
                null,
                scroll,
                "Text-to-Speech Unavailable",
                JOptionPane.WARNING_MESSAGE
        );
    }

private static void showMissingSpeechCachePopup(String voiceName, Path voiceDir, List<String> missing) {
    if (missing == null || missing.isEmpty()) {
        return;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("AWS TTS generation is disabled, and the following speech clips were not found in the cache.\n\n");
    if (voiceName != null && !voiceName.isBlank()) {
        sb.append("Voice: ").append(voiceName).append("\n");
    }
    if (voiceDir != null) {
        sb.append("Cache dir: ").append(voiceDir.toAbsolutePath()).append("\n");
    }
    sb.append("\nMissing clips:\n");
    for (String m : missing) {
        if (m == null || m.isBlank()) {
            continue;
        }
        sb.append(" - ").append(m).append("\n");
    }

    JTextArea area = new JTextArea(sb.toString(), 18, 70);
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);

    Runnable show = () -> JOptionPane.showMessageDialog(
            null,
            new JScrollPane(area),
            "Speech Cache Miss",
            JOptionPane.WARNING_MESSAGE
    );

    if (SwingUtilities.isEventDispatchThread()) {
        show.run();
        return;
    }

    try {
        SwingUtilities.invokeAndWait(show);
    } catch (Exception ignored) {
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

    private static void playWavBlockingInternal(Path wavPath) {
        if (wavPath == null) {
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
        writePcmBytesAsWav(pcmBytes, wavOut, sampleRate);
    }

    private static void writePcmBytesAsWav(byte[] pcmBytes, Path wavOut, int sampleRate) throws IOException {
        if (pcmBytes == null) {
            pcmBytes = new byte[0];
        }

        Files.createDirectories(wavOut.getParent());
        
        pcmBytes = trimSilencePcm16leMono(pcmBytes, sampleRate, TRIM_ABS_THRESHOLD, TRIM_KEEP_MS);

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

    private VoiceSettings resolveVoiceSettings() {
        String voiceName = OverlayPreferences.getSpeechVoiceName();
        if (voiceName == null || voiceName.isBlank()) {
            voiceName = "Joanna";
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