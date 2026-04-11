package org.dce.ed.tts;

import java.awt.Component;
import java.awt.Dialog;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.dce.ed.OverlayPreferences;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Downloads and installs pre-generated TTS voice packs from GitHub releases.
 * 
 * Voice packs are zip files named like "voice-salli.zip" attached to releases.
 * They contain the cached WAV files (end/ and mid/ subdirectories) plus manifest.tsv.
 * 
 * <p><b>Where to publish packs (maintainers):</b> upload each {@code voice-*.zip} as a
 * <b>release binary asset</b> (the “Attach binaries” area when editing a release).
 * Links in the release description are <em>not</em> visible to the GitHub API and will
 * not be found. Prefer a release tagged {@value #VOICE_PACKS_RELEASE_TAG}; otherwise
 * the app tries {@code /releases/latest}, then scans recent releases for the asset.
 *
 * <p>Bump {@link #SPEECH_PACK_REVISION} whenever you publish new pack zips so clients with
 * “Use AWS” disabled refresh their cache on next startup.
 */
public final class VoicePackManager {

    private static final String OWNER = "deccles";
    private static final String REPO = "Elite-Dangerous-RockHound";

    /**
     * Git tag for the release that holds voice-pack zips. One long-lived release;
     * update its assets when packs change — not tied to app version tags.
     */
    public static final String VOICE_PACKS_RELEASE_TAG = "tts-voice-packs";

    /**
     * Increment when GitHub {@code voice-*.zip} assets change. Users with speech enabled and
     * AWS synthesis off will auto re-download and replace the selected voice’s cache at startup.
     */
    public static final int SPEECH_PACK_REVISION = 1;

    private static final String VOICE_PACK_PREFIX = "voice-";
    private static final String VOICE_PACK_SUFFIX = ".zip";

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    private VoicePackManager() {
    }

    /**
     * Check if a voice pack is installed locally (has cached WAV files).
     */
    public static boolean isVoicePackInstalled(String voiceName) {
        if (voiceName == null || voiceName.isBlank()) {
            return false;
        }

        Path voiceDir = getVoiceCacheDir(voiceName);
        if (!Files.isDirectory(voiceDir)) {
            return false;
        }

        // Check for end/ or mid/ subdirectories with at least one .wav file
        Path endDir = voiceDir.resolve("end");
        Path midDir = voiceDir.resolve("mid");

        return hasWavFiles(endDir) || hasWavFiles(midDir);
    }

    private static boolean hasWavFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (var stream = Files.list(dir)) {
            return stream.anyMatch(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".wav"));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * If speech is on, AWS synthesis is off, and the app’s {@link #SPEECH_PACK_REVISION} is newer than
     * the last installed pack (or the selected voice changed), download the GitHub pack in the background
     * (no progress dialog) and replace the voice cache folder.
     * <p>
     * Also downloads when preferences claim an up-to-date pack but {@link #isVoicePackInstalled(String)}
     * finds no WAV cache (deleted cache, failed extract, new machine, etc.).
     *
     * @param errorParent optional window for failure dialogs; may be null (errors only logged)
     */
    public static void checkAutoVoicePackOnStartup(Component errorParent) {
        if (!OverlayPreferences.isSpeechEnabled()) {
            return;
        }
        if (OverlayPreferences.isSpeechUseAwsSynthesis()) {
            return;
        }
        String voice = OverlayPreferences.getSpeechVoiceName();
        if (voice == null || voice.isBlank()) {
            return;
        }
        String voiceTrim = voice.trim();
        int installed = OverlayPreferences.getSpeechPackInstalledRevision();
        String installedVoice = OverlayPreferences.getSpeechPackInstalledVoice();
        if (installed >= SPEECH_PACK_REVISION
                && !installedVoice.isBlank()
                && installedVoice.equalsIgnoreCase(voiceTrim)
                && isVoicePackInstalled(voiceTrim)) {
            return;
        }
        downloadAndInstallVoicePack(errorParent, voice, false, null);
    }

    /**
     * Download and install a voice pack for the given voice name.
     * Shows a progress dialog during download.
     *
     * @param parent Parent component for dialogs
     * @param voiceName Voice name (e.g., "Salli", "Joanna")
     * @param onComplete Callback when complete (success or failure)
     */
    public static void downloadAndInstallVoicePack(Component parent, String voiceName, Runnable onComplete) {
        downloadAndInstallVoicePack(parent, voiceName, true, onComplete);
    }

    /**
     * @param showProgressDialog if false, runs download/extract on a worker thread with no modal UI (startup path)
     */
    public static void downloadAndInstallVoicePack(Component parent, String voiceName, boolean showProgressDialog,
            Runnable onComplete) {
        if (voiceName == null || voiceName.isBlank()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        final String voiceNameFinal = voiceName.trim();
        String normalizedVoice = voiceNameFinal.toLowerCase(Locale.ROOT);
        String zipName = VOICE_PACK_PREFIX + normalizedVoice + VOICE_PACK_SUFFIX;

        JDialog dialog = null;
        JLabel label = null;
        JProgressBar bar = null;
        if (showProgressDialog) {
            dialog = new JDialog(
                    SwingUtilities.getWindowAncestor(parent),
                    "Downloading Voice Pack",
                    Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

            label = new JLabel("Downloading voice pack for " + voiceNameFinal + "...");
            bar = new JProgressBar();
            bar.setIndeterminate(true);

            dialog.getContentPane().setLayout(new java.awt.BorderLayout(10, 10));
            dialog.getContentPane().add(label, java.awt.BorderLayout.NORTH);
            dialog.getContentPane().add(bar, java.awt.BorderLayout.CENTER);
            dialog.setSize(400, 100);
            dialog.setLocationRelativeTo(parent);
        }

        final JDialog dialogFinal = dialog;
        final JLabel labelFinal = label;
        final JProgressBar barFinal = bar;

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {

            private Exception failure;

            @Override
            protected Boolean doInBackground() {
                try {
                    String assetUrl = findVoicePackAssetUrl(zipName);
                    if (assetUrl == null) {
                        throw new IOException("Voice pack not found: " + zipName);
                    }

                    downloadAndExtract(assetUrl, voiceNameFinal, barFinal, labelFinal);
                    return true;

                } catch (Exception ex) {
                    failure = ex;
                    return false;
                }
            }

            @Override
            protected void done() {
                if (dialogFinal != null) {
                    dialogFinal.dispose();
                }

                if (failure != null) {
                    String msg = failure.getMessage();
                    if (msg == null || msg.isBlank()) {
                        msg = failure.getClass().getSimpleName();
                    }

                    if (!msg.contains("not found")) {
                        if (showProgressDialog) {
                            JOptionPane.showMessageDialog(parent,
                                    "Unable to download voice pack:\n" + msg,
                                    "Voice Pack Download Failed",
                                    JOptionPane.WARNING_MESSAGE);
                        } else {
                            System.err.println("[EDO] Voice pack auto-update failed: " + msg);
                        }
                    } else {
                        System.out.println("Voice pack not available for download: " + voiceNameFinal + " (" + zipName + ")");
                        System.out.println("Hint: upload " + zipName
                                + " as a release binary (Assets on GitHub), not only a link in the description.");
                    }
                } else {
                    OverlayPreferences.setSpeechPackInstalledInfo(SPEECH_PACK_REVISION, voiceNameFinal);
                    OverlayPreferences.flushBackingStore();
                    System.out.println("Voice pack installed: " + voiceNameFinal);
                }

                if (onComplete != null) {
                    onComplete.run();
                }
            }
        };

        worker.execute();
        if (dialogFinal != null) {
            dialogFinal.setVisible(true);
        }
    }

    /**
     * Find the download URL for a voice pack: release tagged {@link #VOICE_PACKS_RELEASE_TAG},
     * then {@code /releases/latest}, then recent releases (e.g. tag {@code 1.0.1}).
     */
    private static String findVoicePackAssetUrl(String zipName) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();

        String tagged = apiUrlForReleaseByTag(VOICE_PACKS_RELEASE_TAG);
        String url = findVoicePackAssetUrlInRelease(client, zipName, tagged);
        if (url != null) {
            return url;
        }

        String latest = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";
        url = findVoicePackAssetUrlInRelease(client, zipName, latest);
        if (url != null) {
            return url;
        }

        return findVoicePackAssetUrlInRecentReleases(client, zipName);
    }

    /**
     * Walk recent releases (newest first) and return the first matching {@code voice-*.zip} asset.
     */
    private static String findVoicePackAssetUrlInRecentReleases(HttpClient client, String zipName)
            throws IOException, InterruptedException {

        String apiUrl = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases?per_page=40";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "EDO-Overlay")
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("GitHub API returned " + resp.statusCode() + " listing releases");
        }

        JsonElement root = JsonParser.parseString(resp.body());
        if (!root.isJsonArray()) {
            return null;
        }

        String wantName = zipName.toLowerCase(Locale.ROOT);
        for (JsonElement relEl : root.getAsJsonArray()) {
            if (!relEl.isJsonObject()) {
                continue;
            }
            JsonObject release = relEl.getAsJsonObject();
            JsonArray assets = release.getAsJsonArray("assets");
            if (assets == null) {
                continue;
            }
            for (JsonElement el : assets) {
                JsonObject asset = el.getAsJsonObject();
                String name = asset.has("name") ? asset.get("name").getAsString() : "";
                if (name.toLowerCase(Locale.ROOT).equals(wantName)) {
                    return asset.has("browser_download_url")
                            ? asset.get("browser_download_url").getAsString()
                            : null;
                }
            }
        }

        return null;
    }

    private static String apiUrlForReleaseByTag(String tag) {
        String enc = URLEncoder.encode(tag.trim(), StandardCharsets.UTF_8).replace("+", "%20");
        return "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/tags/" + enc;
    }

    /**
     * @return asset download URL, or null if release not found (404), no assets, or zip missing
     */
    private static String findVoicePackAssetUrlInRelease(HttpClient client, String zipName, String apiUrl)
            throws IOException, InterruptedException {

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "EDO-Overlay")
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 404) {
            return null;
        }
        if (resp.statusCode() != 200) {
            throw new IOException("GitHub API returned " + resp.statusCode());
        }

        JsonObject release = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonArray assets = release.getAsJsonArray("assets");
        if (assets == null) {
            return null;
        }

        String wantName = zipName.toLowerCase(Locale.ROOT);
        for (JsonElement el : assets) {
            JsonObject asset = el.getAsJsonObject();
            String name = asset.has("name") ? asset.get("name").getAsString() : "";
            if (name.toLowerCase(Locale.ROOT).equals(wantName)) {
                return asset.has("browser_download_url")
                        ? asset.get("browser_download_url").getAsString()
                        : null;
            }
        }

        return null;
    }

    /**
     * Download a zip file and extract it to the voice cache directory (replacing previous pack files).
     *
     * @param bar progress bar, or null when no UI
     * @param label status label, or null when no UI
     */
    private static void downloadAndExtract(String url, String voiceName, JProgressBar bar, JLabel label)
            throws IOException, InterruptedException {

        Path voiceDir = getVoiceCacheDir(voiceName);
        Files.createDirectories(voiceDir);

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(HTTP_TIMEOUT)
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "EDO-Overlay")
                .GET()
                .build();

        HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("HTTP " + resp.statusCode() + " downloading voice pack");
        }

        long contentLength = resp.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (contentLength > 0 && bar != null) {
            SwingUtilities.invokeLater(() -> {
                bar.setIndeterminate(false);
                bar.setMinimum(0);
                bar.setMaximum(100);
            });
        }

        // Download to temp file first
        Path tempZip = Files.createTempFile("voice-pack-", ".zip");
        try {
            try (InputStream in = resp.body();
                 var out = Files.newOutputStream(tempZip)) {

                byte[] buf = new byte[64 * 1024];
                long totalRead = 0;
                int r;

                while ((r = in.read(buf)) >= 0) {
                    out.write(buf, 0, r);
                    totalRead += r;

                    if (contentLength > 0 && bar != null && label != null) {
                        final int pct = (int) ((totalRead * 100L) / contentLength);
                        SwingUtilities.invokeLater(() -> {
                            bar.setValue(pct);
                            label.setText("Downloading... " + pct + "%");
                        });
                    }
                }
            }

            if (bar != null && label != null) {
                SwingUtilities.invokeLater(() -> {
                    bar.setIndeterminate(true);
                    label.setText("Extracting voice pack...");
                });
            }

            // Only clear after a full download succeeds (avoid wiping cache on network failure).
            clearVoicePackDirContents(voiceDir);
            extractZip(tempZip, voiceDir);

        } finally {
            Files.deleteIfExists(tempZip);
        }
    }

    private static void clearVoicePackDirContents(Path voiceDir) throws IOException {
        if (!Files.isDirectory(voiceDir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(voiceDir)) {
            for (Path child : stream.toList()) {
                deletePathRecursive(child);
            }
        }
    }

    private static void deletePathRecursive(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        } else {
            Files.deleteIfExists(path);
        }
    }

    /**
     * Extract a zip file to a target directory.
     */
    private static void extractZip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                // Security: prevent path traversal
                if (name.contains("..")) {
                    continue;
                }

                Path outPath = targetDir.resolve(name);

                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                }

                zis.closeEntry();
            }
        }
    }

    /**
     * Get the cache directory for a voice.
     */
    private static Path getVoiceCacheDir(String voiceName) {
        Path root = OverlayPreferences.getSpeechCacheDir();
        String safeVoice = voiceName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
        return root.resolve(safeVoice);
    }

    /**
     * Create a zip file from a voice cache directory (for uploading to GitHub).
     * 
     * @param voiceName Voice name
     * @param outputZip Path to write the zip file
     */
    public static void createVoicePackZip(String voiceName, Path outputZip) throws IOException {
        Path voiceDir = getVoiceCacheDir(voiceName);
        if (!Files.isDirectory(voiceDir)) {
            throw new IOException("Voice cache directory not found: " + voiceDir);
        }

        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(outputZip))) {
            Files.walk(voiceDir)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        try {
                            String entryName = voiceDir.relativize(file).toString().replace('\\', '/');
                            zos.putNextEntry(new ZipEntry(entryName));
                            Files.copy(file, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        System.out.println("Created voice pack: " + outputZip);
    }

    /**
     * CLI entry point for creating voice packs.
     * Usage: java ... VoicePackManager create <voiceName> <outputZip>
     */
    public static void main(String[] args) {
        if (args.length < 3 || !"create".equals(args[0])) {
            System.out.println("Usage: VoicePackManager create <voiceName> <outputZip>");
            System.out.println("Example: VoicePackManager create Salli voice-salli.zip");
            return;
        }

        String voiceName = args[1];
        Path outputZip = Path.of(args[2]);

        try {
            createVoicePackZip(voiceName, outputZip);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
