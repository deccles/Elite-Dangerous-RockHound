package org.dce.ed.util;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Consumer;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class GithubMsiUpdater {

    private static final String OWNER = "deccles";
    private static final String REPO = "EDO";

    // Must match your Maven coords for pom.properties lookup
    private static final String MAVEN_GROUP_ID = "org.dce";
    private static final String MAVEN_ARTIFACT_ID = "EliteDangerousOverlay";

    private static final String INSTALL_DIR_NAME = "EDO Overlay";
    private static final String EXE_NAME = "EDO Overlay.exe";

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);

    private GithubMsiUpdater() {
    }

    /** Result object for non-interactive update checks (status bar). */
    public static final class UpdateCheckResult {
        public final String currentVersion;
        public final String latestVersion;
        public final String msiName;
        public final String msiUrl;

        public UpdateCheckResult(String currentVersion, String latestVersion, String msiName, String msiUrl) {
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.msiName = msiName;
            this.msiUrl = msiUrl;
        }
    }

    public static void checkAndUpdate(Component parent) {
        Cursor oldCursor = parent != null ? parent.getCursor() : null;
        if (parent != null) {
            parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {

            private String currentVersion;
            private String latestVersion;
            private String latestHtmlUrl;
            private String msiName;
            private String msiUrl;

            private Exception failure;

            @Override
            protected Void doInBackground() {
                try {
                    currentVersion = readCurrentVersion();

                    JsonObject latest = fetchLatestReleaseJson();
                    latestVersion = readVersionFromTag(latest);
                    latestHtmlUrl = getString(latest, "html_url");

                    JsonObject msiAsset = findMsiAsset(latest);
                    if (msiAsset != null) {
                        msiName = getString(msiAsset, "name");
                        msiUrl = getString(msiAsset, "browser_download_url");
                    }
                } catch (Exception ex) {
                    failure = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                if (parent != null && oldCursor != null) {
                    parent.setCursor(oldCursor);
                }

                if (failure != null) {
                    JOptionPane.showMessageDialog(parent,
                            "Unable to check for updates:\n" + safeMessage(failure),
                            "Update Check Failed",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (latestVersion == null || latestVersion.isBlank()) {
                    JOptionPane.showMessageDialog(parent,
                            "Unable to determine latest release version from GitHub.",
                            "Update Check Failed",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (msiUrl == null || msiUrl.isBlank()) {
                    JOptionPane.showMessageDialog(parent,
                            "Latest release was found (" + latestVersion + "), but no MSI asset was attached.\n\n"
                                    + "Open the Releases page and download the MSI manually:\n"
                                    + (latestHtmlUrl != null ? latestHtmlUrl
                                            : "https://github.com/" + OWNER + "/" + REPO + "/releases"),
                            "No MSI Found",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }

                boolean newer = true;
                if (currentVersion != null && !currentVersion.isBlank() && !"(unknown)".equals(currentVersion)) {
                    newer = compareVersions(latestVersion, currentVersion) > 0;
                }

                if (!newer) {
                    JOptionPane.showMessageDialog(parent,
                            "You're already on the latest version.\n\n"
                                    + "Current: " + nullToUnknown(currentVersion) + "\n"
                                    + "Latest: " + latestVersion,
                            "No Update Available",
                            JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                int choice = JOptionPane.showConfirmDialog(parent,
                        "Update available.\n\n"
                                + "Current: " + nullToUnknown(currentVersion) + "\n"
                                + "Latest: " + latestVersion + "\n\n"
                                + "Download and run installer now?\n\n"
                                + "MSI: " + nullToUnknown(msiName) + "\n\n"
                                + "Windows will ask for admin permission to install.",
                        "Update Available",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);

                if (choice != JOptionPane.YES_OPTION) {
                    return;
                }

                downloadAndInstall(parent, msiUrl, msiName);
            }
        };

        worker.execute();
    }

    /**
     * Background update check intended for status-bar display.
     * - Never shows dialogs.
     * - If an installable newer version exists and currentVersion is known, calls callback with details.
     * - Otherwise calls callback with null.
     */
    public static void checkForStatusBar(Component parent, Consumer<UpdateCheckResult> callback) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private String currentVersion;
            private String latestVersion;
            private String msiName;
            private String msiUrl;
            private Exception failure;

            @Override
            protected Void doInBackground() {
                try {
                    currentVersion = readCurrentVersion();
                    JsonObject latest = fetchLatestReleaseJson();
                    latestVersion = readVersionFromTag(latest);
                    JsonObject msiAsset = findMsiAsset(latest);
                    if (msiAsset != null) {
                        msiName = getString(msiAsset, "name");
                        msiUrl = getString(msiAsset, "browser_download_url");
                    }
                } catch (Exception ex) {
                    failure = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                if (callback == null) {
                    return;
                }
                if (failure != null) {
                    callback.accept(null);
                    return;
                }
                // Need latest version and MSI.
                if (latestVersion == null || latestVersion.isBlank() || msiUrl == null || msiUrl.isBlank()) {
                    callback.accept(null);
                    return;
                }
                // If current version is unknown (dev/IDE), don't show update.
                if (currentVersion == null || currentVersion.isBlank() || "(unknown)".equals(currentVersion)) {
                    callback.accept(null);
                    return;
                }
                // Only signal when newer.
                if (compareVersions(latestVersion, currentVersion) <= 0) {
                    callback.accept(null);
                    return;
                }
                callback.accept(new UpdateCheckResult(currentVersion, latestVersion, msiName, msiUrl));
            }
        };
        worker.execute();
    }

    private static void downloadAndInstall(Component parent, String msiUrl, String msiName) {

        JDialog dialog = new JDialog(
                javax.swing.SwingUtilities.getWindowAncestor(parent),
                "Downloading Update",
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JLabel label = new JLabel("Downloading " + (msiName != null ? msiName : "installer") + "...");
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);

        dialog.getContentPane().setLayout(new java.awt.BorderLayout(10, 10));
        dialog.getContentPane().add(label, java.awt.BorderLayout.NORTH);
        dialog.getContentPane().add(bar, java.awt.BorderLayout.CENTER);
        dialog.setSize(460, 120);
        dialog.setLocationRelativeTo(parent);

        SwingWorker<Path, Integer> dlWorker = new SwingWorker<Path, Integer>() {

            private Exception failure;

            @Override
            protected Path doInBackground() {
                try {
                    return downloadMsiToUpdaterDir(msiUrl, msiName, bar, label);
                } catch (Exception ex) {
                    failure = ex;
                    return null;
                }
            }

            @Override
            protected void done() {
                dialog.dispose();

                if (failure != null) {
                    JOptionPane.showMessageDialog(parent,
                            "Unable to download update:\n" + safeMessage(failure),
                            "Download Failed",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                Path downloaded;
                try {
                    downloaded = get();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parent,
                            "Unable to download update:\n" + safeMessage(ex),
                            "Download Failed",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (downloaded == null || !Files.isRegularFile(downloaded)) {
                    JOptionPane.showMessageDialog(parent,
                            "Download did not produce a valid MSI file.",
                            "Download Failed",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                try {
                    // --- NEW: run msiexec elevated and then relaunch the installed EXE ---
                    String programFiles = System.getenv("ProgramFiles");
                    if (programFiles == null || programFiles.isBlank()) {
                        programFiles = "C:\\Program Files";
                    }

                    Path exe = Path.of(programFiles, INSTALL_DIR_NAME, EXE_NAME);

                    String msiPath = downloaded.toAbsolutePath().toString();
                    String exePath = exe.toAbsolutePath().toString();

                    // Inner command runs ELEVATED. No .ps1 involved.
                    String inner =
                            "$msi = \"" + escapeForPowershell(msiPath) + "\"; " +
                            "$exe = \"" + escapeForPowershell(exePath) + "\"; " +
                            "$args = \"/i `\"$msi`\" /passive /norestart\"; " +
                            "$p = Start-Process -FilePath \"msiexec.exe\" -ArgumentList $args -Wait -PassThru; " +
                            "    Start-Sleep -Seconds 1; " +
                            "    Start-Process -FilePath $exe; ";

                    String innerEncoded = toPowershellEncodedCommand(inner);

                    // Outer command just triggers UAC once and runs the encoded inner command elevated.
                    String outer =
                            "Start-Process -FilePath 'powershell.exe' -Verb RunAs -WindowStyle Hidden -ArgumentList @(" +
                            "    '-NoProfile'," +
                            "    '-EncodedCommand'," +
                            "    '" + innerEncoded + "'" +
                            ")";

                    String outerEncoded = toPowershellEncodedCommand(outer);

                    Process p = new ProcessBuilder(
                            "powershell.exe",
                            "-NoProfile",
                            "-EncodedCommand",
                            outerEncoded
                    ).start();

                    // Give Windows time to spawn the elevated process
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {
                    }

                    // Now exit so MSI can replace files
                    System.exit(0);

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parent,
                            "Downloaded installer, but couldn't start the updater:\n" + safeMessage(ex) + "\n\n"
                                    + "MSI:\n" + downloaded.toAbsolutePath(),
                            "Update Launch Failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        dlWorker.execute();
        dialog.setVisible(true);
    }

    private static Path downloadMsiToUpdaterDir(String url, String msiName, JProgressBar bar, JLabel label) throws Exception {

        Path updaterDir = getUpdaterDir();
        Files.createDirectories(updaterDir);

        String safeName = toSafeFilename(msiName);
        if (safeName == null || safeName.isBlank()) {
            safeName = "EDO-Overlay-Update.msi";
        }
        if (!safeName.toLowerCase(Locale.ROOT).endsWith(".msi")) {
            safeName = safeName + ".msi";
        }

        Path outFile = updaterDir.resolve(safeName);
        if (Files.exists(outFile)) {
            outFile = updaterDir.resolve(stripExtension(safeName) + "-" + System.currentTimeMillis() + ".msi");
        }

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(HTTP_TIMEOUT)
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "EDO-Overlay-Updater")
                .GET()
                .build();

        HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        int status = resp.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " downloading MSI");
        }

        long len = resp.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (len > 0) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                bar.setIndeterminate(false);
                bar.setMinimum(0);
                bar.setMaximum(1000);
            });
        } else {
            javax.swing.SwingUtilities.invokeLater(() -> bar.setIndeterminate(true));
        }

        try (InputStream in = resp.body();
             var out = Files.newOutputStream(outFile)) {

            byte[] buf = new byte[128 * 1024];
            long readTotal = 0;

            int r;
            while ((r = in.read(buf)) >= 0) {
                if (r == 0) {
                    continue;
                }
                out.write(buf, 0, r);
                readTotal += r;

                if (len > 0) {
                    final long rt = readTotal;
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        int v = (int) Math.min(1000L, (rt * 1000L) / len);
                        bar.setValue(v);
                        label.setText("Downloading... " + (v / 10) + "%");
                    });
                }
            }
        }

        return outFile;
    }

    // Left in place (your file already had these); currently unused, but harmless.
    private static Path createInstallAndRelaunchPs1(Path msi) throws IOException {
        Path updaterDir = getUpdaterDir();
        Files.createDirectories(updaterDir);

        Path updateLog = updaterDir.resolve("update.log");
        Path msiLog = updaterDir.resolve("msiexec.log");
        Path ps1 = updaterDir.resolve("update.ps1");

        String programFiles = System.getenv("ProgramFiles");
        if (programFiles == null || programFiles.isBlank()) {
            programFiles = "C:\\Program Files";
        }

        Path exe = Path.of(programFiles, INSTALL_DIR_NAME, EXE_NAME);

        String script =
                "$ErrorActionPreference = 'Stop'\n" +
                "$UpdateLog = '" + escapeForPowershell(updateLog.toAbsolutePath().toString()) + "'\n" +
                "$MsiLog = '" + escapeForPowershell(msiLog.toAbsolutePath().toString()) + "'\n" +
                "$MsiPath = '" + escapeForPowershell(msi.toAbsolutePath().toString()) + "'\n" +
                "$ExePath = '" + escapeForPowershell(exe.toAbsolutePath().toString()) + "'\n" +
                "\n" +
                "New-Item -ItemType Directory -Force -Path (Split-Path -Parent $UpdateLog) | Out-Null\n" +
                "Add-Content -Path $UpdateLog -Value ((Get-Date).ToString('s') + '  update.ps1 entered')\n" +
                "\n" +
                "function Log($m) { Add-Content -Path $UpdateLog -Value ((Get-Date).ToString('s') + '  ' + $m) }\n" +
                "Log '================================================'\n" +
                "Log ('Updater starting. Script=' + $PSCommandPath)\n" +
                "Log ('MSI=' + $MsiPath)\n" +
                "Log ('EXE=' + $ExePath)\n" +
                "Log ('User=' + [Environment]::UserName)\n" +
                "\n" +
                "$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] 'Administrator')\n" +
                "Log ('isAdmin=' + $isAdmin)\n" +
                "if (-not $isAdmin) {\n" +
                "  Log 'ERROR: Not running elevated. Aborting.'\n" +
                "  exit 740\n" +
                "}\n" +
                "\n" +
                "if (-not (Test-Path $MsiPath)) {\n" +
                "  Log 'MSI file missing; aborting.'\n" +
                "  exit 1619\n" +
                "}\n" +
                "\n" +
                "Start-Sleep -Seconds 1\n" +
                "\n" +
                "try {\n" +
                "  Log 'Killing any running EDO Overlay.exe'\n" +
                "  Get-Process -Name 'EDO Overlay' -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue\n" +
                "} catch { Log ('Kill failed: ' + $_) }\n" +
                "\n" +
                "Log ('Launching msiexec with verbose log ' + $MsiLog)\n" +
                "$msiArgs = @('/i', $MsiPath, '/passive', '/norestart', '/l*v', $MsiLog)\n" +
                "$p = Start-Process -FilePath 'msiexec.exe' -ArgumentList $msiArgs -Wait -PassThru\n" +
                "Log ('msiexec exit code: ' + $p.ExitCode)\n" +
                "\n" +
                "if (($p.ExitCode -ne 0) -and ($p.ExitCode -ne 3010)) {\n" +
                "  Log 'Install FAILED; not relaunching app.'\n" +
                "  exit $p.ExitCode\n" +
                "}\n" +
                "\n" +
                "if (Test-Path $ExePath) {\n" +
                "  Log 'Relaunching installed EXE'\n" +
                "  Start-Process -FilePath $ExePath\n" +
                "} else {\n" +
                "  Log 'EXE not found; opening install folder'\n" +
                "  Start-Process -FilePath (Split-Path -Parent $ExePath)\n" +
                "}\n" +
                "Log 'Updater finished.'\n";

        Files.writeString(ps1, script, StandardCharsets.UTF_8);
        return ps1;
    }

    private static Path createElevatedLauncherPs1(Path updatePs1) throws IOException {
        Path updaterDir = getUpdaterDir();
        Files.createDirectories(updaterDir);

        Path launcher = updaterDir.resolve("run_update.ps1");
        Path outLog = updaterDir.resolve("powershell-out.log");
        Path updateLog = updaterDir.resolve("update.log");

        String script =
                "$ErrorActionPreference = 'Continue'\n" +
                "$Out = '" + escapeForPowershell(outLog.toAbsolutePath().toString()) + "'\n" +
                "$Ulog = '" + escapeForPowershell(updateLog.toAbsolutePath().toString()) + "'\n" +
                "$Ps1 = '" + escapeForPowershell(updatePs1.toAbsolutePath().toString()) + "'\n" +
                "\n" +
                "New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Out) | Out-Null\n" +
                "Add-Content -Path $Ulog -Value ((Get-Date).ToString('s') + '  run_update.ps1 starting (elevated wrapper)')\n" +
                "Add-Content -Path $Out  -Value ((Get-Date).ToString('s') + '  run_update.ps1 starting (elevated wrapper)')\n" +
                "Add-Content -Path $Out  -Value ('Will run: ' + $Ps1)\n" +
                "\n" +
                "try {\n" +
                "  & $Ps1 *>> $Out\n" +
                "  Add-Content -Path $Out -Value ((Get-Date).ToString('s') + '  update.ps1 finished')\n" +
                "} catch {\n" +
                "  Add-Content -Path $Out  -Value ((Get-Date).ToString('s') + '  ERROR: ' + $_.Exception.Message)\n" +
                "  Add-Content -Path $Ulog -Value ((Get-Date).ToString('s') + '  ERROR: ' + $_.Exception.Message)\n" +
                "}\n";

        Files.writeString(launcher, script, StandardCharsets.UTF_8);
        return launcher;
    }

    private static Path getUpdaterDir() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) {
            localAppData = System.getProperty("user.home");
        }
        return Path.of(localAppData, INSTALL_DIR_NAME, "updater");
    }

    private static String toPowershellEncodedCommand(String command) {
        byte[] bytes = command.getBytes(StandardCharsets.UTF_16LE);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String escapeForPowershell(String s) {
        return s.replace("'", "''");
    }

    private static JsonObject fetchLatestReleaseJson() throws Exception {
        String api = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(HTTP_TIMEOUT)
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(api))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "EDO-Overlay-Updater")
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = resp.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " calling GitHub releases/latest");
        }

        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }
    public static void checkForUpdatesOnStartup(Component parent) {
        // Same as checkAndUpdate, but:
        // - no "No update available" dialog
        // - no "No MSI found" dialog
        // - only show UI if update exists and can be installed
        Cursor oldCursor = parent != null ? parent.getCursor() : null;

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {

            private String currentVersion;
            private String latestVersion;
            private String latestHtmlUrl;
            private String msiName;
            private String msiUrl;
            private Exception failure;

            @Override
            protected Void doInBackground() {
                try {
                    currentVersion = readCurrentVersion();

                    JsonObject latest = fetchLatestReleaseJson();
                    latestVersion = readVersionFromTag(latest);
                    latestHtmlUrl = getString(latest, "html_url");

                    JsonObject msiAsset = findMsiAsset(latest);
                    if (msiAsset != null) {
                        msiName = getString(msiAsset, "name");
                        msiUrl = getString(msiAsset, "browser_download_url");
                    }
                } catch (Exception ex) {
                    failure = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                // Startup behavior: NEVER pop errors or "no update" messages.
                if (failure != null) {
                    return;
                }

                if (latestVersion == null || latestVersion.isBlank()) {
                    return;
                }

                if (currentVersion != null && !currentVersion.isBlank() && !"(unknown)".equals(currentVersion)) {
                    if (compareVersions(latestVersion, currentVersion) <= 0) {
                        return;
                    }
                }

                // Only prompt if an update is actually available.
                if (msiUrl == null || msiUrl.isBlank()) {
                    // Optional: you could show a warning, but you asked for silent unless installable.
                    return;
                }

                int choice = JOptionPane.showConfirmDialog(parent,
                        "Update available.\n\n"
                                + "Current: " + nullToUnknown(currentVersion) + "\n"
                                + "Latest: " + latestVersion + "\n\n"
                                + "Download and run installer now?\n\n"
                                + "MSI: " + nullToUnknown(msiName) + "\n\n"
                                + "Windows will ask for admin permission to install.",
                        "Update Available",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);

                if (choice != JOptionPane.YES_OPTION) {
                    return;
                }

                downloadAndInstall(parent, msiUrl, msiName);
            }
        };

        worker.execute();
    }

    private static JsonObject findMsiAsset(JsonObject release) {
        JsonElement assetsEl = release.get("assets");
        if (assetsEl == null || !assetsEl.isJsonArray()) {
            return null;
        }

        JsonArray assets = assetsEl.getAsJsonArray();
        for (JsonElement el : assets) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject a = el.getAsJsonObject();
            String name = getString(a, "name");
            if (name == null) {
                continue;
            }
            if (name.toLowerCase(Locale.ROOT).endsWith(".msi")) {
                return a;
            }
        }
        return null;
    }

    private static String readVersionFromTag(JsonObject release) {
        String tag = getString(release, "tag_name");
        if (tag == null) {
            return null;
        }
        tag = tag.trim();
        if (tag.startsWith("v") || tag.startsWith("V")) {
            tag = tag.substring(1);
        }
        return tag;
    }

    private static String readCurrentVersion() {
        try {
            String v = GithubMsiUpdater.class.getPackage().getImplementationVersion();
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        } catch (Exception ignored) {
        }

        String pomPropsPath = "/META-INF/maven/" + MAVEN_GROUP_ID + "/" + MAVEN_ARTIFACT_ID + "/pom.properties";
        try (InputStream in = GithubMsiUpdater.class.getResourceAsStream(pomPropsPath)) {
            if (in == null) {
                return "(unknown)";
            }
            Properties props = new Properties();
            props.load(in);
            String v = props.getProperty("version");
            return (v != null && !v.isBlank()) ? v.trim() : "(unknown)";
        } catch (Exception ignored) {
            return "(unknown)";
        }
    }

    private static String getString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) {
            return null;
        }
        try {
            return el.getAsString();
        } catch (Exception ex) {
            return null;
        }
    }

    private static int compareVersions(String a, String b) {
        if (a == null || b == null) {
            return 0;
        }
        String[] ap = a.trim().split("[^0-9]+");
        String[] bp = b.trim().split("[^0-9]+");

        int n = Math.max(ap.length, bp.length);
        for (int i = 0; i < n; i++) {
            int ai = (i < ap.length && !ap[i].isBlank()) ? safeInt(ap[i]) : 0;
            int bi = (i < bp.length && !bp[i].isBlank()) ? safeInt(bp[i]) : 0;

            if (ai != bi) {
                return Integer.compare(ai, bi);
            }
        }
        return 0;
    }

    private static int safeInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ex) {
            return 0;
        }
    }

    private static String nullToUnknown(String v) {
        if (v == null || v.isBlank()) {
            return "(unknown)";
        }
        return v;
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.isBlank()) {
            return t.getClass().getSimpleName();
        }
        return m;
    }

    private static String toSafeFilename(String name) {
        if (name == null) {
            return null;
        }
        String s = name.trim();
        if (s.isBlank()) {
            return null;
        }
        return s.replaceAll("[\\\\/:*?\"<>|]+", "_");
    }

    private static String stripExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        if (idx <= 0) {
            return filename;
        }
        return filename.substring(0, idx);
    }
}
