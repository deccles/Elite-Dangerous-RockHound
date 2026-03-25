package org.dce.ed;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.Preferences;

import org.dce.ed.logreader.EliteLogFileLocator;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;

import software.amazon.awssdk.services.polly.model.Engine;
import org.dce.ed.ui.EdoUi;

/**
 * Centralized preferences for the overlay, including log directory selection.
 */
public final class OverlayPreferences {

    private static final String KEY_IS_OVERLAY_TRANSPARENT = "overlay.transparent";

    // New overlay background preferences (normal + pass-through)
    private static final String KEY_OVERLAY_BG_RGB = "overlay.bg.rgb"; // 0xRRGGBB
    private static final String KEY_OVERLAY_BG_TRANSPARENCY_PCT = "overlay.bg.transparencyPct"; // 0-100
    private static final String KEY_OVERLAY_BG_PT_RGB = "overlay.bg.passthrough.rgb"; // 0xRRGGBB
    private static final String KEY_OVERLAY_BG_PT_TRANSPARENCY_PCT = "overlay.bg.passthrough.transparencyPct"; // 0-100

    private static final String KEY_PASSTHROUGH_TOGGLE_KEYCODE = "overlay.passthrough.toggleKeyCode"; // JNativeHook NativeKeyEvent VC_*
    private static final String KEY_LOG_AUTO = "log.autoDetect";
    private static final String KEY_LOG_CUSTOM_DIR = "log.customDir";

    private static final String KEY_UI_FONT_NAME = "ui.font.name";
    private static final String KEY_UI_FONT_SIZE = "ui.font.size";


    // --- UI theme colors ---
    private static final String KEY_UI_MAIN_TEXT_RGB = "ui.colors.mainTextRgb"; // 0xRRGGBB
    private static final String KEY_UI_BACKGROUND_RGB = "ui.colors.backgroundRgb"; // 0xRRGGBB
    private static final String KEY_UI_SNEAKER_RGB = "ui.colors.sneakerRgb"; // 0xRRGGBB

    // --- Speech / Polly (new) ---
    private static final String KEY_SPEECH_ENABLED = "speech.enabled";
    private static final String KEY_SPEECH_USE_AWS = "speech.useAwsSynthesis"; // allow AWS to generate missing speech
    private static final String KEY_SPEECH_ENGINE = "speech.engine"; // "standard" or "neural" (we'll default to standard)
    private static final String KEY_SPEECH_VOICE = "speech.voiceId"; // e.g. "Joanna"
    private static final String KEY_SPEECH_REGION = "speech.awsRegion"; // e.g. "us-east-1"
    private static final String KEY_SPEECH_AWS_PROFILE = "speech.awsProfile"; // optional, blank means default chain
    private static final String KEY_SPEECH_CACHE_DIR = "speech.cacheDir";
    private static final String KEY_SPEECH_SAMPLE_RATE = "speech.sampleRate"; // PCM sample rate in Hz (as string)


    private static final String KEY_NON_OVERLAY_ALWAYS_ON_TOP = "window.nonOverlay.alwaysOnTop"; // Decorated window (non-overlay mode)

    private static final String KEY_OVERLAY_TAB_ROUTE_VISIBLE = "overlay.tab.route.visible";
    private static final String KEY_OVERLAY_TAB_SYSTEM_VISIBLE = "overlay.tab.system.visible";
    private static final String KEY_OVERLAY_TAB_BIOLOGY_VISIBLE = "overlay.tab.biology.visible";
    private static final String KEY_OVERLAY_TAB_MINING_VISIBLE = "overlay.tab.mining.visible";
    private static final String KEY_OVERLAY_TAB_FLEET_CARRIER_VISIBLE = "overlay.tab.fleetCarrier.visible";

    // --- Mining / Prospector ---
    private static final String KEY_MINING_PROSPECTOR_MATERIALS = "mining.prospector.materials"; // comma-separated
    private static final String KEY_MINING_PROSPECTOR_MIN_PROP = "mining.prospector.minProportion"; // percent
    private static final String KEY_MINING_PROSPECTOR_MIN_AVG_VALUE = "mining.prospector.minAvgValuePerTon"; // credits/ton
    private static final String KEY_MINING_PROSPECTOR_EMAIL = "mining.prospector.email"; // for CSV log

    // Mining log / spreadsheet: backend (local vs Google Sheets) and run counter
    private static final String KEY_MINING_LOG_BACKEND = "mining.log.backend"; // "local" | "google"
    private static final String KEY_MINING_GOOGLE_SHEETS_URL = "mining.googleSheets.url";
    private static final String KEY_MINING_GOOGLE_CLIENT_ID = "mining.googleSheets.clientId";
    private static final String KEY_MINING_GOOGLE_CLIENT_SECRET = "mining.googleSheets.clientSecret";
    private static final String KEY_MINING_GOOGLE_REFRESH_TOKEN = "mining.googleSheets.refreshToken";
    // Deprecated: mining run counter is now derived from sheet data (commander + system/body).
    private static final String KEY_MINING_LOG_RUN_COUNTER = "mining.log.runCounter";
    private static final String KEY_MINING_LOG_COMMANDER_NAME = "mining.log.commanderName";

    // Mining value estimation (Mining tab)
    private static final String KEY_MINING_EST_TONS_LOW = "mining.estimate.tons.low";
    private static final String KEY_MINING_EST_TONS_MED = "mining.estimate.tons.medium";
    private static final String KEY_MINING_EST_TONS_HIGH = "mining.estimate.tons.high";
    private static final String KEY_MINING_EST_TONS_CORE = "mining.estimate.tons.core";
    
    // Mining: low-limpet reminder
    private static final String KEY_MINING_LIMPET_REMINDER_ENABLED = "mining.limpetReminder.enabled";
    private static final String KEY_MINING_LIMPET_REMINDER_MODE = "mining.limpetReminder.mode"; // COUNT or PERCENT
    private static final String KEY_MINING_LIMPET_REMINDER_THRESHOLD = "mining.limpetReminder.threshold"; // COUNT
    private static final String KEY_MINING_LIMPET_REMINDER_THRESHOLD_PERCENT = "mining.limpetReminder.thresholdPercent"; // PERCENT

    // Nearby tab (exobiology sphere search)
    private static final String KEY_NEARBY_SPHERE_RADIUS_LY = "nearby.sphereRadiusLy";
    private static final String KEY_NEARBY_MIN_VALUE_MILLION_CREDITS = "nearby.minValueMillionCredits";
    private static final String KEY_NEARBY_MAX_SYSTEMS = "nearby.maxSystems";

    /** System tab: min predicted exobiology value (million credits) for dollar icon + credit TTS (default 10). */
    private static final String KEY_BIO_VALUABLE_THRESHOLD_MILLION_CREDITS = "exobiology.valuableThresholdMillionCredits";

    // Reuse the same prefs node as OverlayFrame so everything is in one place.
    private static final Preferences PREFS = Preferences.userNodeForPackage(OverlayFrame.class);

    private OverlayPreferences() {
    }

    public static boolean isOverlayTransparent() {
        boolean b = PREFS.getBoolean(KEY_IS_OVERLAY_TRANSPARENT, true);
        return b;
    }

    public static void setOverlayTransparent(boolean transparent) {
        PREFS.putBoolean(KEY_IS_OVERLAY_TRANSPARENT, transparent);
    }

    /**
     * Set by the app when the undecorated pass-through {@link org.dce.ed.OverlayFrame} is the visible host.
     * Decorated mode uses an opaque theme plate; legacy {@link #isOverlayTransparent()} must not force
     * transparent table/tab chrome there (Windows LAF shows through as blue).
     */
    private static volatile boolean passThroughWindowActive;

    public static void setPassThroughWindowActive(boolean active) {
        passThroughWindowActive = active;
    }

    public static boolean isPassThroughWindowActive() {
        return passThroughWindowActive;
    }

    /**
     * True when table headers, tab row, etc. should use transparent fills so the desktop shows through.
     */
    public static boolean overlayChromeRequestsTransparency() {
        return isOverlayTransparent() && passThroughWindowActive;
    }

    // ---------------------------------------------------------------------
    // Overlay background (new)
    // ---------------------------------------------------------------------

    public static int getNormalBackgroundRgb() {
        ensureOverlayBackgroundMigratedIfNeeded();
        return PREFS.getInt(KEY_OVERLAY_BG_RGB, 0x000000);
    }

    public static void setNormalBackgroundRgb(int rgb) {
        PREFS.putInt(KEY_OVERLAY_BG_RGB, rgb & 0xFFFFFF);
    }

    public static int getNormalTransparencyPercent() {
        ensureOverlayBackgroundMigratedIfNeeded();
        return clampPercent(PREFS.getInt(KEY_OVERLAY_BG_TRANSPARENCY_PCT, 100));
    }

    public static void setNormalTransparencyPercent(int percent) {
        PREFS.putInt(KEY_OVERLAY_BG_TRANSPARENCY_PCT, clampPercent(percent));
    }

    public static int getPassThroughBackgroundRgb() {
        ensureOverlayBackgroundMigratedIfNeeded();
        return PREFS.getInt(KEY_OVERLAY_BG_PT_RGB, 0x000000);
    }

    public static void setPassThroughBackgroundRgb(int rgb) {
        PREFS.putInt(KEY_OVERLAY_BG_PT_RGB, rgb & 0xFFFFFF);
    }
 // ---------------------------------------------------------------------
 // Non-overlay window behavior (decorated window)
 // ---------------------------------------------------------------------

 public static boolean isNonOverlayAlwaysOnTop() {
     boolean b = PREFS.getBoolean(KEY_NON_OVERLAY_ALWAYS_ON_TOP, false);
     return b;
 }

 public static void setNonOverlayAlwaysOnTop(boolean alwaysOnTop) {
     PREFS.putBoolean(KEY_NON_OVERLAY_ALWAYS_ON_TOP, alwaysOnTop);
 }

    public static boolean isOverlayTabRouteVisible() {
        return PREFS.getBoolean(KEY_OVERLAY_TAB_ROUTE_VISIBLE, true);
    }

    public static void setOverlayTabRouteVisible(boolean visible) {
        PREFS.putBoolean(KEY_OVERLAY_TAB_ROUTE_VISIBLE, visible);
    }

    public static boolean isOverlayTabSystemVisible() {
        return PREFS.getBoolean(KEY_OVERLAY_TAB_SYSTEM_VISIBLE, true);
    }

    public static void setOverlayTabSystemVisible(boolean visible) {
        PREFS.putBoolean(KEY_OVERLAY_TAB_SYSTEM_VISIBLE, visible);
    }

    public static boolean isOverlayTabBiologyVisible() {
        return PREFS.getBoolean(KEY_OVERLAY_TAB_BIOLOGY_VISIBLE, true);
    }

    public static void setOverlayTabBiologyVisible(boolean visible) {
        PREFS.putBoolean(KEY_OVERLAY_TAB_BIOLOGY_VISIBLE, visible);
    }

    public static boolean isOverlayTabMiningVisible() {
        return PREFS.getBoolean(KEY_OVERLAY_TAB_MINING_VISIBLE, true);
    }

    public static void setOverlayTabMiningVisible(boolean visible) {
        PREFS.putBoolean(KEY_OVERLAY_TAB_MINING_VISIBLE, visible);
    }

    public static boolean isOverlayTabFleetCarrierVisible() {
        return PREFS.getBoolean(KEY_OVERLAY_TAB_FLEET_CARRIER_VISIBLE, true);
    }

    public static void setOverlayTabFleetCarrierVisible(boolean visible) {
        PREFS.putBoolean(KEY_OVERLAY_TAB_FLEET_CARRIER_VISIBLE, visible);
    }

    public static int getPassThroughTransparencyPercent() {
        ensureOverlayBackgroundMigratedIfNeeded();
        return clampPercent(PREFS.getInt(KEY_OVERLAY_BG_PT_TRANSPARENCY_PCT, 100));
    }

    public static void setPassThroughTransparencyPercent(int percent) {
        PREFS.putInt(KEY_OVERLAY_BG_PT_TRANSPARENCY_PCT, clampPercent(percent));
    }

    public static int getPassThroughToggleKeyCode() {
        // Default: F9
        return PREFS.getInt(KEY_PASSTHROUGH_TOGGLE_KEYCODE, NativeKeyEvent.VC_F9);
    }

    public static void setPassThroughToggleKeyCode(int keyCode) {
        PREFS.putInt(KEY_PASSTHROUGH_TOGGLE_KEYCODE, keyCode);
    }

    // ---------------------------------------------------------------------
    // Legacy compatibility (used by existing panels like MiningTabPanel)
    // ---------------------------------------------------------------------

    public static Color getOverlayBackgroundColor() {
        int rgb = getNormalBackgroundRgb();
        return EdoUi.fromRgbInt(rgb);
    }

    public static int getOverlayTransparencyPercent() {
        return getNormalTransparencyPercent();
    }

    private static int clampPercent(int percent) {
        if (percent < 0) {
            return 0;
        }
        if (percent > 100) {
            return 100;
        }
        return percent;
    }

    /**
     * One-time migration from the legacy boolean "overlay.transparent".
     *
     * Previous behavior:
     *  - true  => fully transparent background
     *  - false => fully opaque black background
     *
     * New defaults:
     *  - Normal mode: derived from old flag
     *  - Pass-through: default to fully transparent (matches previous toggle behavior)
     */
    private static void ensureOverlayBackgroundMigratedIfNeeded() {
        if (PREFS.get(KEY_OVERLAY_BG_TRANSPARENCY_PCT, null) != null) {
            return;
        }

        boolean wasTransparent = isOverlayTransparent();
        setNormalBackgroundRgb(0x000000);
        setNormalTransparencyPercent(wasTransparent ? 100 : 0);

        setPassThroughBackgroundRgb(0x000000);
        setPassThroughTransparencyPercent(100);
    }

    public static boolean isAutoLogDir(String clientKey) {
        return PREFS.getBoolean(KEY_LOG_AUTO + "." +clientKey, true);
    }

    public static void setAutoLogDir(String clientKey, boolean auto) {
        PREFS.putBoolean(KEY_LOG_AUTO + "." +clientKey, auto);
    }

    public static String getCustomLogDir(String clientKey) {
        return PREFS.get(KEY_LOG_CUSTOM_DIR + "." +clientKey, "");
    }

    public static void setCustomLogDir(String clientKey, String path) {
        if (path == null) {
            path = "";
        }
        PREFS.put(KEY_LOG_CUSTOM_DIR + "." +clientKey, path);
    }

    /**
     * Resolve the journal directory based on preferences:
     * - If "auto" is enabled, use the default journal folder.
     * - Otherwise, try the custom path; if it looks valid, use it.
     * - If custom is invalid, fall back to the default journal folder.
     */
    public static Path resolveJournalDirectory(String clientKey) {
        if (isAutoLogDir(clientKey)) {
            return EliteLogFileLocator.findDefaultJournalDirectory();
        }

        String custom = getCustomLogDir(clientKey);
        if (custom != null && !custom.isBlank()) {
            Path p = Paths.get(custom.trim());
            if (Files.isDirectory(p) && EliteLogFileLocator.looksLikeJournalDirectory(p)) {
                return p;
            }
        }

        // Fallback so we don't completely break if the custom dir is bad
        return EliteLogFileLocator.findDefaultJournalDirectory();
    }

    /**
     * Returns true if the journal directory is available (resolved and present on disk).
     * Use this before creating {@link org.dce.ed.logreader.EliteJournalReader} when running
     * on a machine that may not have Elite Dangerous installed.
     */
    public static boolean isJournalDirectoryAvailable(String clientKey) {
        Path p = resolveJournalDirectory(clientKey);
        return p != null && Files.isDirectory(p);
    }

    // ----------------------------
    // Speech / Polly getters/setters
    // ----------------------------

    public static boolean isSpeechEnabled() {
        // Deterministic tests: disable speech/TTS side effects during `mvn test`.
        if (Boolean.getBoolean("edo.test.disableSpeech")) {
            return false;
        }
        return PREFS.getBoolean(KEY_SPEECH_ENABLED, false);
    }

    public static void setSpeechEnabled(boolean enabled) {
        PREFS.putBoolean(KEY_SPEECH_ENABLED, enabled);
    }

    

public static boolean isSpeechUseAwsSynthesis() {
    // Default: enabled (for now)
    return PREFS.getBoolean(KEY_SPEECH_USE_AWS, true);
}

public static void setSpeechUseAwsSynthesis(boolean useAws) {
    PREFS.putBoolean(KEY_SPEECH_USE_AWS, useAws);
}

public static Engine getSpeechEngine() {
        // Defaults: as you requested, keep sane hardcoded defaults.
        // Start with "standard" to avoid Neural costs.
        return Engine.fromValue(PREFS.get(KEY_SPEECH_ENGINE, "standard"));
    }

    public static void setSpeechEngine(String engine) {
        if (engine == null || engine.isBlank()) {
            engine = "standard";
        }
        PREFS.put(KEY_SPEECH_ENGINE, engine.trim().toLowerCase());
    }

    public static String getSpeechVoiceName() {
        // Default voice: "Joanna" (standard, decent baseline)
        return PREFS.get(KEY_SPEECH_VOICE, "Joanna");
    }

    public static void setSpeechVoiceId(String voiceId) {
        if (voiceId == null || voiceId.isBlank()) {
            voiceId = "Joanna";
        }
        PREFS.put(KEY_SPEECH_VOICE, voiceId.trim());
    }

    public static String getSpeechAwsRegion() {
        // Default: us-east-1
        return PREFS.get(KEY_SPEECH_REGION, "us-east-1");
    }

    public static void setSpeechAwsRegion(String region) {
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }
        PREFS.put(KEY_SPEECH_REGION, region.trim());
    }

    public static String getSpeechAwsProfile() {
        // Optional. Blank => DefaultCredentialsProvider chain.
        return PREFS.get(KEY_SPEECH_AWS_PROFILE, "");
    }

    public static void setSpeechAwsProfile(String profile) {
        if (profile == null) {
            profile = "";
        }
        PREFS.put(KEY_SPEECH_AWS_PROFILE, profile.trim());
    }

    public static Path getSpeechCacheDir() {
        String defaultDir = Paths.get(System.getProperty("user.home"), ".edo", "tts-cache").toString();
        String configured = PREFS.get(KEY_SPEECH_CACHE_DIR, defaultDir);
        return Paths.get(configured);
    }

    public static void setSpeechCacheDir(String dir) {
        if (dir == null || dir.isBlank()) {
            dir = Paths.get(System.getProperty("user.home"), ".edo", "tts-cache").toString();
        }
        PREFS.put(KEY_SPEECH_CACHE_DIR, dir.trim());
    }

    public static int getSpeechSampleRateHz() {
        String s = PREFS.get(KEY_SPEECH_SAMPLE_RATE, "16000");
        try {
            int hz = Integer.parseInt(s.trim());
            if (hz < 8000) {
                hz = 8000;
            }
            return hz;
        } catch (Exception e) {
            return 16000;
        }
    }

    public static void setSpeechSampleRateHz(int sampleRateHz) {
        if (sampleRateHz < 8000) {
            sampleRateHz = 8000;
        }
        PREFS.put(KEY_SPEECH_SAMPLE_RATE, Integer.toString(sampleRateHz));
    }

    // ----------------------------
    // Mining / Prospector
    // ----------------------------

    /**
     * Comma-separated list of materials to announce for ProspectedAsteroid events.
     * Leave blank to announce any material meeting the threshold.
     */
    public static String getProspectorMaterialsCsv() {
        return PREFS.get(KEY_MINING_PROSPECTOR_MATERIALS, "").trim();
    }

    public static void setProspectorMaterialsCsv(String csv) {
        if (csv == null) {
            csv = "";
        }
        PREFS.put(KEY_MINING_PROSPECTOR_MATERIALS, csv.trim());
    }

    /**
     * Minimum material proportion (percent) required to trigger an announcement.
     */
    public static double getProspectorMinProportionPercent() {
        String s = PREFS.get(KEY_MINING_PROSPECTOR_MIN_PROP, "20");
        try {
            double v = Double.parseDouble(s.trim());
            if (v < 0.0) {
                v = 0.0;
            }
            if (v > 100.0) {
                v = 100.0;
            }
            return v;
        } catch (Exception e) {
            return 20.0;
        }
    }

    public static void setProspectorMinProportionPercent(double percent) {
        if (percent < 0.0) {
            percent = 0.0;
        }
        if (percent > 100.0) {
            percent = 100.0;
        }
        PREFS.put(KEY_MINING_PROSPECTOR_MIN_PROP, Double.toString(percent));
    }

    /**
     * Minimum "galactic average" value (credits/ton) required for a material to count as "valuable".
     *
     * This is used for ProspectedAsteroid announcements when enabled.
     */
    public static int getProspectorMinAvgValueCrPerTon() {
        String s = PREFS.get(KEY_MINING_PROSPECTOR_MIN_AVG_VALUE, "150000");
        try {
            int v = Integer.parseInt(s.trim());
            if (v < 0) {
                v = 0;
            }
            return v;
        } catch (Exception e) {
            return 150000;
        }
    }

    public static void setProspectorMinAvgValueCrPerTon(int creditsPerTon) {
        if (creditsPerTon < 0) {
            creditsPerTon = 0;
        }
        PREFS.put(KEY_MINING_PROSPECTOR_MIN_AVG_VALUE, Integer.toString(creditsPerTon));
    }

    /**
     * Email address written into the prospector log CSV (e.g. for notifications).
     */
    public static String getProspectorEmail() {
        return PREFS.get(KEY_MINING_PROSPECTOR_EMAIL, "").trim();
    }

    public static void setProspectorEmail(String email) {
        if (email == null) {
            email = "";
        }
        PREFS.put(KEY_MINING_PROSPECTOR_EMAIL, email.trim());
    }

    /**
     * Prospector log backend: "local" (CSV file) or "google" (Google Sheets).
     */
    public static String getMiningLogBackend() {
        String v = PREFS.get(KEY_MINING_LOG_BACKEND, "local").trim();
        return "google".equalsIgnoreCase(v) ? "google" : "local";
    }

    public static void setMiningLogBackend(String backend) {
        if (backend == null) {
            backend = "local";
        }
        PREFS.put(KEY_MINING_LOG_BACKEND, "google".equalsIgnoreCase(backend.trim()) ? "google" : "local");
    }

    /**
     * Google Sheets URL for prospector log when backend is "google".
     */
    public static String getMiningGoogleSheetsUrl() {
        return PREFS.get(KEY_MINING_GOOGLE_SHEETS_URL, "").trim();
    }

    public static void setMiningGoogleSheetsUrl(String url) {
        PREFS.put(KEY_MINING_GOOGLE_SHEETS_URL, url != null ? url.trim() : "");
    }

    /** OAuth 2.0 Client ID from Google Cloud Console (Desktop app). */
    public static String getMiningGoogleSheetsClientId() {
        return PREFS.get(KEY_MINING_GOOGLE_CLIENT_ID, "").trim();
    }

    public static void setMiningGoogleSheetsClientId(String clientId) {
        PREFS.put(KEY_MINING_GOOGLE_CLIENT_ID, clientId != null ? clientId.trim() : "");
    }

    /** OAuth 2.0 Client Secret from Google Cloud Console. */
    public static String getMiningGoogleSheetsClientSecret() {
        return PREFS.get(KEY_MINING_GOOGLE_CLIENT_SECRET, "").trim();
    }

    public static void setMiningGoogleSheetsClientSecret(String clientSecret) {
        PREFS.put(KEY_MINING_GOOGLE_CLIENT_SECRET, clientSecret != null ? clientSecret.trim() : "");
    }

    /** Stored refresh token after user signs in (opaque string). */
    public static String getMiningGoogleSheetsRefreshToken() {
        return PREFS.get(KEY_MINING_GOOGLE_REFRESH_TOKEN, "").trim();
    }

    public static void setMiningGoogleSheetsRefreshToken(String refreshToken) {
        PREFS.put(KEY_MINING_GOOGLE_REFRESH_TOKEN, refreshToken != null ? refreshToken.trim() : "");
    }

    /**
     * Deprecated: mining run counter is now derived from sheet data (commander + system/body).
     * The stored value is retained only for backward compatibility and is no longer updated.
     */
    public static int getMiningLogRunCounter() {
        int v = PREFS.getInt(KEY_MINING_LOG_RUN_COUNTER, 1);
        return v < 1 ? 1 : v;
    }

    public static void setMiningLogRunCounter(int run) {
        PREFS.putInt(KEY_MINING_LOG_RUN_COUNTER, run < 1 ? 1 : run);
    }

    /** @deprecated Mining run counter is now derived from sheet data. This method is a no-op wrapper. */
    public static int incrementMiningLogRunCounter() {
        return getMiningLogRunCounter();
    }

    /**
     * Commander name written into the prospector log (CSV / Google Sheets). Shown in Log/Spreadsheet block.
     */
    public static String getMiningLogCommanderName() {
        String v = PREFS.get(KEY_MINING_LOG_COMMANDER_NAME, "").trim();
        if (v.isEmpty()) {
            v = getProspectorEmail();
        }
        return v;
    }

    public static void setMiningLogCommanderName(String name) {
        PREFS.put(KEY_MINING_LOG_COMMANDER_NAME, name != null ? name.trim() : "");
    }

    // --- Nearby tab (exobiology sphere search) ---

    /** Sphere search radius in ly (default 20). EDSM API caps at 100. */
    public static int getNearbySphereRadiusLy() {
        String s = PREFS.get(KEY_NEARBY_SPHERE_RADIUS_LY, "10");
        try {
            int v = Integer.parseInt(s.trim());
            if (v < 1) {
                v = 1;
            }
            if (v > 100) {
                v = 100;
            }
            return v;
        } catch (Exception e) {
            return 20;
        }
    }

    public static void setNearbySphereRadiusLy(int radiusLy) {
        int v = radiusLy;
        if (v < 1) {
            v = 1;
        }
        if (v > 100) {
            v = 100;
        }
        PREFS.put(KEY_NEARBY_SPHERE_RADIUS_LY, Integer.toString(v));
    }

    /** Max number of systems to query for the Nearby table (default 40). Limits EDSM/Spansh API calls per hour. */
    public static int getNearbyMaxSystems() {
        String s = PREFS.get(KEY_NEARBY_MAX_SYSTEMS, "40");
        try {
            int v = Integer.parseInt(s.trim());
            if (v < 1) {
                v = 1;
            }
            if (v > 200) {
                v = 200;
            }
            return v;
        } catch (Exception e) {
            return 40;
        }
    }

    public static void setNearbyMaxSystems(int maxSystems) {
        int v = maxSystems;
        if (v < 1) {
            v = 1;
        }
        if (v > 200) {
            v = 200;
        }
        PREFS.put(KEY_NEARBY_MAX_SYSTEMS, Integer.toString(v));
    }

    /** Minimum exobiology value (million credits) to show a system in the Nearby table (default 5). */
    public static double getNearbyMinValueMillionCredits() {
        String s = PREFS.get(KEY_NEARBY_MIN_VALUE_MILLION_CREDITS, "5");
        try {
            double v = Double.parseDouble(s.trim());
            if (v < 0.0) {
                v = 0.0;
            }
            return v;
        } catch (Exception e) {
            return 5.0;
        }
    }

    public static void setNearbyMinValueMillionCredits(double millionCredits) {
        double v = millionCredits;
        if (v < 0.0) {
            v = 0.0;
        }
        PREFS.put(KEY_NEARBY_MIN_VALUE_MILLION_CREDITS, Double.toString(v));
    }

    /**
     * Minimum predicted exobiology value (million credits) for the System tab dollar icon and for speaking
     * estimated credits on initial bio prediction. Default 10.
     */
    public static double getBioValuableThresholdMillionCredits() {
        String s = PREFS.get(KEY_BIO_VALUABLE_THRESHOLD_MILLION_CREDITS, "10");
        try {
            double v = Double.parseDouble(s.trim());
            if (v < 0.0) {
                v = 0.0;
            }
            return v;
        } catch (Exception e) {
            return 10.0;
        }
    }

    public static void setBioValuableThresholdMillionCredits(double millionCredits) {
        double v = millionCredits;
        if (v < 0.0) {
            v = 0.0;
        }
        PREFS.put(KEY_BIO_VALUABLE_THRESHOLD_MILLION_CREDITS, Double.toString(v));
    }

    /** Same as {@link #getBioValuableThresholdMillionCredits()} in credits (rounded to nearest credit). */
    public static long getBioValuableThresholdCredits() {
        return Math.round(getBioValuableThresholdMillionCredits() * 1_000_000.0);
    }

    // --- UI Font (System / Route / Biology) ---

    /**
     * Font family name used across major panels (System / Route / Biology).
     * Default matches SystemTabPanel's historical font choice.
     */
    public static String getUiFontName() {
        return PREFS.get(KEY_UI_FONT_NAME, "Segoe UI");
    }

    public static void setUiFontName(String fontName) {
        if (fontName == null || fontName.isBlank()) {
            fontName = "Segoe UI";
        }
        PREFS.put(KEY_UI_FONT_NAME, fontName.trim());
    }

    /**
     * Base font size (points) used across major panels.
     * Default matches SystemTabPanel's historical font size.
     */
    public static int getUiFontSize() {
        try {
            int sz = Integer.parseInt(PREFS.get(KEY_UI_FONT_SIZE, "17"));
            if (sz < 8) {
                sz = 8;
            }
            if (sz > 72) {
                sz = 72;
            }
            return sz;
        } catch (Exception e) {
            return 17;
        }
    }

    public static void setUiFontSize(int size) {
        if (size < 8) {
            size = 8;
        }
        if (size > 72) {
            size = 72;
        }
        PREFS.put(KEY_UI_FONT_SIZE, Integer.toString(size));
    }

    /**
     * Convenience: returns the configured UI font. If the requested family is
     * unavailable on the current system, Java will substitute.
     */
    public static java.awt.Font getUiFont() {
        String name = getUiFontName();
        int size = getUiFontSize();
        return new java.awt.Font(name, java.awt.Font.PLAIN, size);
    }

    // ----------------------------
    // Mining value estimation (Mining tab)
    // ----------------------------

    // ----------------------------
    // Mining: low-limpet reminder
    // ----------------------------

    /**
     * If enabled, the overlay will announce when you appear to be in a mining loadout but have
     * fewer limpets in your hold than the configured threshold.
     */
    public static boolean isMiningLowLimpetReminderEnabled() {
        return PREFS.getBoolean(KEY_MINING_LIMPET_REMINDER_ENABLED, true);
    }

    public static void setMiningLowLimpetReminderEnabled(boolean enabled) {
        PREFS.putBoolean(KEY_MINING_LIMPET_REMINDER_ENABLED, enabled);
    }

    /**
     * Limpet reminder threshold. The overlay will announce if (limpetCount < threshold).
     * Default is 1 (announce when you have 0 limpets).
     */
    public static int getMiningLowLimpetReminderThreshold() {
        int v = PREFS.getInt(KEY_MINING_LIMPET_REMINDER_THRESHOLD, 1);
        if (v < 0) {
            v = 0;
        }
        if (v > 10_000) {
            v = 10_000;
        }
        return v;
    }

    public static void setMiningLowLimpetReminderThreshold(int threshold) {
        int v = threshold;
        if (v < 0) {
            v = 0;
        }
        if (v > 10_000) {
            v = 10_000;
        }
        PREFS.putInt(KEY_MINING_LIMPET_REMINDER_THRESHOLD, v);
    }


    public enum MiningLimpetReminderMode {
        COUNT,
        PERCENT
    }

    public static MiningLimpetReminderMode getMiningLowLimpetReminderMode() {
        String v = PREFS.get(KEY_MINING_LIMPET_REMINDER_MODE, MiningLimpetReminderMode.COUNT.name());
        if (v == null) {
            return MiningLimpetReminderMode.COUNT;
        }
        try {
            return MiningLimpetReminderMode.valueOf(v.trim().toUpperCase());
        } catch (Exception e) {
            return MiningLimpetReminderMode.COUNT;
        }
    }

    public static void setMiningLowLimpetReminderMode(MiningLimpetReminderMode mode) {
        if (mode == null) {
            mode = MiningLimpetReminderMode.COUNT;
        }
        PREFS.put(KEY_MINING_LIMPET_REMINDER_MODE, mode.name());
    }

    /**
     * Limpet reminder threshold, stored as a percentage (0..100) of your ship's CargoCapacity.
     */
    public static int getMiningLowLimpetReminderThresholdPercent() {
        int v = PREFS.getInt(KEY_MINING_LIMPET_REMINDER_THRESHOLD_PERCENT, 10);
        if (v < 0) {
            v = 0;
        }
        if (v > 100) {
            v = 100;
        }
        return v;
    }

    public static void setMiningLowLimpetReminderThresholdPercent(int thresholdPercent) {
        int v = thresholdPercent;
        if (v < 0) {
            v = 0;
        }
        if (v > 100) {
            v = 100;
        }
        PREFS.putInt(KEY_MINING_LIMPET_REMINDER_THRESHOLD_PERCENT, v);
    }


    /**
     * Estimated total collectible tons for a prospected asteroid with Content=Low.
     */
    public static double getMiningEstimateTonsLow() {
        return getDoubleClamped(KEY_MINING_EST_TONS_LOW, 8.0, 0.0, 200.0);
    }

    public static void setMiningEstimateTonsLow(double tons) {
        putDoubleClamped(KEY_MINING_EST_TONS_LOW, tons, 0.0, 200.0);
    }

    /**
     * Estimated total collectible tons for a prospected asteroid with Content=Medium.
     */
    public static double getMiningEstimateTonsMedium() {
        return getDoubleClamped(KEY_MINING_EST_TONS_MED, 16.0, 0.0, 200.0);
    }

    public static void setMiningEstimateTonsMedium(double tons) {
        putDoubleClamped(KEY_MINING_EST_TONS_MED, tons, 0.0, 200.0);
    }

    /**
     * Estimated total collectible tons for a prospected asteroid with Content=High.
     */
    public static double getMiningEstimateTonsHigh() {
        return getDoubleClamped(KEY_MINING_EST_TONS_HIGH, 25.0, 0.0, 200.0);
    }

    public static void setMiningEstimateTonsHigh(double tons) {
        putDoubleClamped(KEY_MINING_EST_TONS_HIGH, tons, 0.0, 200.0);
    }

    /**
     * Estimated tons yielded by a core (MotherlodeMaterial) asteroid.
     */
    public static double getMiningEstimateTonsCore() {
        return getDoubleClamped(KEY_MINING_EST_TONS_CORE, 12.0, 0.0, 200.0);
    }

    public static void setMiningEstimateTonsCore(double tons) {
        putDoubleClamped(KEY_MINING_EST_TONS_CORE, tons, 0.0, 200.0);
    }

    private static double getDoubleClamped(String key, double def, double min, double max) {
        String s = PREFS.get(key, Double.toString(def));
        try {
            double v = Double.parseDouble(s.trim());
            if (v < min) {
                v = min;
            }
            if (v > max) {
                v = max;
            }
            return v;
        } catch (Exception e) {
            return def;
        }
    }
    public static Color buildOverlayBackgroundColor(Color baseColor, int transparencyPercent) {
        if (baseColor == null) {
            baseColor = Color.BLACK;
        }

        int pct = clampPercent(transparencyPercent);

        // 100% transparent => alpha 0
        // 0% transparent   => alpha 255
        int alpha = (int)Math.round(255.0 * (1.0 - (pct / 100.0)));

        return EdoUi.withAlpha(baseColor, alpha);
    }


    // ---------------------------------------------------------------------
    // UI theme colors
    // ---------------------------------------------------------------------

    public static int getUiMainTextRgb() {
        return PREFS.getInt(KEY_UI_MAIN_TEXT_RGB, EdoUi.User.MAIN_TEXT.getRGB() & 0x00FFFFFF);
    }

    public static void setUiMainTextRgb(int rgb) {
        PREFS.putInt(KEY_UI_MAIN_TEXT_RGB, rgb & 0x00FFFFFF);
    }

    public static int getUiBackgroundRgb() {
        return PREFS.getInt(KEY_UI_BACKGROUND_RGB, EdoUi.User.BACKGROUND.getRGB() & 0x00FFFFFF);
    }

    public static void setUiBackgroundRgb(int rgb) {
        PREFS.putInt(KEY_UI_BACKGROUND_RGB, rgb & 0x00FFFFFF);
    }

    public static int getUiSneakerRgb() {
        // Default matches legacy hard-coded sneaker canvas: rgb(206, 44, 44)
        return PREFS.getInt(KEY_UI_SNEAKER_RGB, 0xCE2C2C);
    }

    public static void setUiSneakerRgb(int rgb) {
        PREFS.putInt(KEY_UI_SNEAKER_RGB, rgb & 0x00FFFFFF);
    }

    /**
     * Apply persisted theme preferences into {@link EdoUi.User} and refresh derived colors.
     * Safe to call multiple times.
     */
    public static void applyThemeToEdoUi() {
        EdoUi.User.MAIN_TEXT = EdoUi.fromRgbInt(getUiMainTextRgb());
        EdoUi.User.BACKGROUND = EdoUi.fromRgbInt(getUiBackgroundRgb());
        EdoUi.User.SNEAKER = EdoUi.fromRgbInt(getUiSneakerRgb());
        EdoUi.refreshDerivedColors();
    }

    private static void putDoubleClamped(String key, double v, double min, double max) {
        if (v < min) {
            v = min;
        }
        if (v > max) {
            v = max;
        }
        PREFS.put(key, Double.toString(v));
    }

}