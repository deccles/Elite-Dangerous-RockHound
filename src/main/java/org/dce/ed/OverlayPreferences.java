package org.dce.ed;

import java.awt.Color;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.UIManager;

import org.dce.ed.logreader.EliteLogFileLocator;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;

import software.amazon.awssdk.services.polly.model.Engine;
import org.dce.ed.ui.EdoUi;

/**
 * Centralized preferences for the overlay, including log directory selection.
 */
public final class OverlayPreferences {

    private static final String KEY_IS_OVERLAY_TRANSPARENT = "overlay.transparent";

    /**
     * Legacy boolean: mouse clicks pass through the undecorated overlay to the game (WS_EX_TRANSPARENT).
     * Migrated to {@link #KEY_OVERLAY_MOUSE_INTERACTION_MODE} on first read.
     */
    private static final String KEY_OVERLAY_MOUSE_PASS_THROUGH_TO_GAME = "overlay.mousePassThroughToGame";

    /**
     * User choice: {@link MouseInteractionMode} prefs value ({@code normal} / {@code selective} / {@code full}).
     * Independent of {@code overlay.startInPassThrough} (window mode). Persisted so it survives restarts.
     */
    private static final String KEY_OVERLAY_MOUSE_INTERACTION_MODE = "overlay.mouseInteractionMode";

    // New overlay background preferences (normal + pass-through)
    private static final String KEY_OVERLAY_BG_RGB = "overlay.bg.rgb"; // 0xRRGGBB
    private static final String KEY_OVERLAY_BG_TRANSPARENCY_PCT = "overlay.bg.transparencyPct"; // 0-100
    private static final String KEY_OVERLAY_BG_PT_RGB = "overlay.bg.passthrough.rgb"; // 0xRRGGBB
    private static final String KEY_OVERLAY_BG_PT_TRANSPARENCY_PCT = "overlay.bg.passthrough.transparencyPct"; // 0-100

    private static final String KEY_PASSTHROUGH_TOGGLE_KEYCODE = "overlay.passthrough.toggleKeyCode"; // JNativeHook NativeKeyEvent VC_*
    private static final String KEY_NEXT_TAB_KEYCODE = "overlay.tabs.nextShown.keyCode"; // JNativeHook NativeKeyEvent VC_*
    private static final String KEY_LOG_AUTO = "log.autoDetect";
    private static final String KEY_LOG_CUSTOM_DIR = "log.customDir";

    private static final String KEY_UI_FONT_NAME = "ui.font.name";
    private static final String KEY_UI_FONT_SIZE = "ui.font.size";

    /**
     * While the preferences dialog previews a font, {@link #getUiFontName()}, {@link #getUiFontSize()},
     * and {@link #getUiFont()} reflect the spinner/combo selection so icons and derived sizes track the preview.
     * Cleared by {@link #clearUiFontLivePreview()} (cancel / revert) or at the start of a committed apply.
     */
    private static volatile String uiFontNameLivePreview = null;
    private static volatile Integer uiFontSizeLivePreview = null;

    // --- UI theme colors ---
    private static final String KEY_UI_MAIN_TEXT_RGB = "ui.colors.mainTextRgb"; // 0xRRGGBB
    private static final String KEY_UI_BACKGROUND_RGB = "ui.colors.backgroundRgb"; // 0xRRGGBB
    private static final String KEY_UI_SNEAKER_RGB = "ui.colors.sneakerRgb"; // 0xRRGGBB
    private static final String KEY_UI_PRIMARY_HIGHLIGHT_RGB = "ui.colors.primaryHighlightRgb"; // 0xRRGGBB
    private static final String KEY_UI_SECONDARY_HIGHLIGHT_RGB = "ui.colors.secondaryHighlightRgb"; // 0xRRGGBB

    // --- Speech / Polly (new) ---
    private static final String KEY_SPEECH_ENABLED = "speech.enabled";
    private static final String KEY_SPEECH_USE_AWS = "speech.useAwsSynthesis"; // allow AWS to generate missing speech
    private static final String KEY_SPEECH_ENGINE = "speech.engine"; // "standard" or "neural" (we'll default to standard)
    private static final String KEY_SPEECH_VOICE = "speech.voiceId"; // e.g. "Matthew"
    private static final String KEY_SPEECH_REGION = "speech.awsRegion"; // e.g. "us-east-1"
    private static final String KEY_SPEECH_AWS_PROFILE = "speech.awsProfile"; // optional, blank means default chain
    private static final String KEY_SPEECH_CACHE_DIR = "speech.cacheDir";
    private static final String KEY_SPEECH_SAMPLE_RATE = "speech.sampleRate"; // PCM sample rate in Hz (as string)
    private static final String KEY_SPEECH_FIRST_DISCOVERED_SYSTEM_ENABLED =
            "speech.announcement.firstDiscoveredSystem.enabled";
    private static final String KEY_SPEECH_BOUNTY_SCAN_FIRST_ENABLED =
            "speech.announcement.bountyScan.first.enabled";
    private static final String KEY_SPEECH_BOUNTY_SCAN_ADDITIONAL_ENABLED =
            "speech.announcement.bountyScan.additional.enabled";
    private static final String KEY_SPEECH_MISSION_PROGRESS_ENABLED =
            "speech.announcement.missionProgress.enabled";
    /** Last {@link org.dce.ed.tts.VoicePackManager#SPEECH_PACK_REVISION} successfully installed while AWS synthesis was off. */
    private static final String KEY_SPEECH_PACK_INSTALLED_REVISION = "speech.packInstalledRevision";
    /** Voice id matching the last successful GitHub pack install (see {@link #KEY_SPEECH_PACK_INSTALLED_REVISION}). */
    private static final String KEY_SPEECH_PACK_INSTALLED_VOICE = "speech.packInstalledVoiceId";


    private static final String KEY_NON_OVERLAY_ALWAYS_ON_TOP = "window.nonOverlay.alwaysOnTop"; // Decorated window (non-overlay mode)

    private static final String KEY_OVERLAY_TAB_ROUTE_VISIBLE = "overlay.tab.route.visible";
    private static final String KEY_OVERLAY_TAB_SYSTEM_VISIBLE = "overlay.tab.system.visible";
    private static final String KEY_OVERLAY_TAB_BIOLOGY_VISIBLE = "overlay.tab.biology.visible";
    private static final String KEY_OVERLAY_TAB_MINING_VISIBLE = "overlay.tab.mining.visible";
    private static final String KEY_OVERLAY_TAB_MISSIONS_VISIBLE = "overlay.tab.missions.visible";
    private static final String KEY_OVERLAY_TAB_FLEET_CARRIER_VISIBLE = "overlay.tab.fleetCarrier.visible";
    private static final String KEY_OVERLAY_TAB_ENGINEERING_VISIBLE = "overlay.tab.engineering.visible";
    private static final String KEY_ENGINEERING_MATERIALS_SORT_COLUMN = "overlay.engineering.materials.sortColumn";
    private static final String KEY_ENGINEERING_MATERIALS_SORT_DESC = "overlay.engineering.materials.sortDescending";
    /** Engineering tab: Materials Required section expanded (true) or collapsed to its Show button. */
    private static final String KEY_ENGINEERING_MATERIALS_SECTION_VISIBLE =
            "overlay.engineering.materials.sectionVisible";
    /** Engineering tab: trade/materials divider position while Materials Required is expanded. */
    private static final String KEY_ENGINEERING_LOWER_SPLIT_DIVIDER =
            "overlay.engineering.lowerSplit.dividerLocation";
    private static final String KEY_ENGINEERING_TRADE_SORT_COLUMN = "overlay.engineering.trade.sortColumn";
    private static final String KEY_ENGINEERING_TRADE_SORT_DESC = "overlay.engineering.trade.sortDescending";
    private static final String KEY_ENGINEERING_BLUEPRINT_SORT_COLUMN = "overlay.engineering.blueprintPicker.sortColumn";
    private static final String KEY_ENGINEERING_BLUEPRINT_SORT_DESC = "overlay.engineering.blueprintPicker.sortDescending";
    private static final String KEY_ENGINEERING_BLUEPRINT_INSTALLED_ONLY =
            "overlay.engineering.blueprintPicker.installedOnly";
    /** Build progress: hide All engineered / Unengineered rows that already have a matching goal. */
    private static final String KEY_ENGINEERING_BUILD_PROGRESS_HIDE_MODULES_WITH_GOALS =
            "overlay.engineering.buildProgress.hideModulesWithGoals";
    /** Last ship chosen in Add Goal; kept until the equipped ship changes. */
    private static final String KEY_ENGINEERING_ADD_GOAL_PREFERRED_SHIP_ID =
            "overlay.engineering.addGoal.preferredShipId";
    /** Equipped ship id when the preferred Add Goal ship was last set. */
    private static final String KEY_ENGINEERING_ADD_GOAL_EQUIPPED_BASELINE_ID =
            "overlay.engineering.addGoal.equippedBaselineId";
    private static final String KEY_OVERLAY_TAB_CONTROL_PANEL_VISIBLE = "overlay.tab.controlPanel.visible";
    /** @deprecated legacy key; migrated on read */
    private static final String KEY_OVERLAY_TAB_EXEC_VISIBLE = "overlay.tab.exec.visible";

    // --- Auto-switching / tab behavior ---
    private static final String KEY_AUTOSWITCH_GUIFOCUS_GALAXYMAP = "overlay.autoswitch.guiFocus.galaxyMap";
    private static final String KEY_AUTOSWITCH_GUIFOCUS_SYSTEMMAP = "overlay.autoswitch.guiFocus.systemMap";
    private static final String KEY_AUTOSWITCH_FSD_TARGET = "overlay.autoswitch.fsd.target";
    private static final String KEY_AUTOSWITCH_SYSTEMTAB_ON_JUMP_SCAN = "overlay.autoswitch.systemTab.onJumpOrScan";
    private static final String KEY_AUTOSWITCH_MINING_PLANETARY_RING = "overlay.autoswitch.mining.onPlanetaryRing";
    private static final String KEY_AUTOSWITCH_MINING_STARTUP_PLANETARY_RING = "overlay.autoswitch.mining.onStartupPlanetaryRing";
    private static final String KEY_AUTOSWITCH_BIOLOGY_NEAR_BODY = "overlay.autoswitch.biology.onNearLandableAtmosphere";
    private static final String KEY_AUTOSWITCH_FLEETCARRIER_ON_DROP = "overlay.autoswitch.fleetCarrier.onJsonDrop";
    /** System tab: expand exobiology lines for the targeted body (dashed outline); collapse when untargeted. */
    private static final String KEY_SYSTEM_AUTO_EXPAND_BIO_ON_TARGET = "system.autoExpandBioOnTargetedBody";

    /** Per-system: last non-star body used for System tab proximity sticky (key suffix = systemAddress). */
    private static final String KEY_SYSTEM_TAB_STICKY_LAST_VISITED_BODY_PREFIX = "system.stickyLastVisitedBody.";

    /** When true, distance column and body sort use approximate distance from your ship (requires Status near-body). */
    private static final String KEY_SYSTEM_TAB_DISTANCE_FROM_SHIP = "overlay.systemTab.distanceFromShip";
    /** {@link SystemTabTableSortMode#toPrefsString()} — body table sort (ship / star / value). */
    private static final String KEY_SYSTEM_TAB_TABLE_SORT_MODE = "overlay.systemTab.tableSortMode";
    /** {@link SystemTabShipRefMode#toPrefsString()} — how ship-centric anchor + plan-map “You” are resolved. */
    private static final String KEY_SYSTEM_TAB_SHIP_REF_MODE = "overlay.systemTab.shipRefMode";
    /**
     * When {@link SystemTabShipRefMode#TARGETED_BODY} and the HUD selects a body, animate the plan map to frame that
     * body’s orbit subsystem (~5 s).
     */
    private static final String KEY_SYSTEM_PLAN_MAP_AUTO_ZOOM_HUD_TARGET = "system.planMap.autoZoomHudTargetSubsystem";

    /** Per-system: last HUD body target used when “HUD target (sticky)” mode keeps a ref after untargeting. */
    private static final String KEY_SYSTEM_TAB_STICKY_HUD_TARGET_BODY_PREFIX = "system.stickyHudTargetBody.";
    /** System plan map Play: orbit-model days advanced per wall-clock second (slider range 1–500; default 110). */
    private static final String KEY_SYSTEM_TAB_ORBIT_ANIM_DAYS_PER_WALL_SECOND = "system.planMap.orbitAnim.daysPerWallSecond";
    /** True-scale map view tilt 0…90 ({@link org.dce.ed.systemmap.MapViewProjection}). */
    private static final String KEY_SYSTEM_PLAN_MAP_VIEW_TILT_DEG = "system.planMap.viewTiltDeg";
    /** Whether the System tab plan map canvas is collapsed (toolbar stays visible). */
    private static final String KEY_SYSTEM_PLAN_MAP_COLLAPSED = "system.planMap.collapsed";
    private static final int SYSTEM_TAB_ORBIT_ANIM_DAYS_PER_WALL_SECOND_DEFAULT = 110;
    private static final int SYSTEM_TAB_ORBIT_ANIM_DAYS_PER_WALL_SECOND_MIN = 1;
    private static final int SYSTEM_TAB_ORBIT_ANIM_DAYS_PER_WALL_SECOND_MAX = 500;

    // --- Mining / Prospector ---
    private static final String KEY_MINING_PROSPECTOR_MATERIALS = "mining.prospector.materials"; // comma-separated
    private static final String KEY_MINING_PROSPECTOR_MIN_PROP = "mining.prospector.minProportion"; // percent
    private static final String KEY_MINING_PROSPECTOR_MIN_AVG_VALUE = "mining.prospector.minAvgValuePerTon"; // credits/ton
    private static final String KEY_MINING_PROSPECTOR_EMAIL = "mining.prospector.email"; // for CSV log

    // Mining log / spreadsheet: backend (local vs Google Sheets vs both) and run counter
    private static final String KEY_MINING_LOG_BACKEND = "mining.log.backend"; // "local" | "google" | "both"
    /** When backend is "both": which side is the primary read source (and what the Mining-tab table displays). */
    private static final String KEY_MINING_LOG_BOTH_PRIMARY = "mining.log.bothPrimary"; // "google" | "local"
    /** Per-commander first-time-sync flag for Both mode; key is {@code mining.log.bothSyncedOnce.<sanitizedCommander>}. */
    private static final String KEY_MINING_LOG_BOTH_SYNCED_ONCE_PREFIX = "mining.log.bothSyncedOnce.";
    private static final String KEY_MINING_GOOGLE_SHEETS_URL = "mining.googleSheets.url";
    private static final String KEY_MINING_GOOGLE_CLIENT_ID = "mining.googleSheets.clientId";
    private static final String KEY_MINING_GOOGLE_CLIENT_SECRET = "mining.googleSheets.clientSecret";
    private static final String KEY_MINING_GOOGLE_REFRESH_TOKEN = "mining.googleSheets.refreshToken";
    /** 0 = legacy single-sheet layout; 1 = per-commander worksheet layout (migration done). */
    private static final String KEY_MINING_GOOGLE_SHEETS_LAYOUT_VERSION = "mining.googleSheets.layoutVersion";
    // Deprecated: mining run counter is now derived from sheet data (commander + system/body).
    private static final String KEY_MINING_LOG_RUN_COUNTER = "mining.log.runCounter";
    private static final String KEY_MINING_LOG_COMMANDER_NAME = "mining.log.commanderName";
    /** Prospector log sub-view: "table" or "scatter". */
    private static final String KEY_MINING_PROSPECTOR_LOG_VIEW = "mining.prospectorLog.view";
    /** Vertical split: fraction of Mining tab height for ship inventory (top pane). */
    private static final String KEY_MINING_PANEL_SPLIT_OUTER = "mining.panel.splitOuter";
    /** Vertical split: fraction of the lower pane for prospector (top of inner split). */
    private static final String KEY_MINING_PANEL_SPLIT_INNER = "mining.panel.splitInner";
    /** Vertical split: fraction of System tab height for the bodies table (top pane). */
    private static final String KEY_SYSTEM_TAB_PANEL_TABLE_SPLIT = "system.panel.splitTable";
    /** Vertical split: fraction of Biology tab height for the specimen table (top pane). */
    private static final String KEY_BIOLOGY_PANEL_TABLE_SPLIT = "biology.panel.splitTable";
    /** Biology tab surface map: rays from centre vs points at sample location. */
    private static final String KEY_BIOLOGY_MAP_DISPLAY_MODE = "biology.map.displayMode";

    // Fighter pilot reminder (fighter hangar + stocked SLF, no Active NPC crew)
    private static final String KEY_FIGHTER_PILOT_REMINDER_ENABLED = "fighterPilotReminder.enabled";
    private static final String KEY_NPC_CREW_ACTIVE_SHIP_PREFIX = "npcCrew.active.ship.";

    // Mining: low-limpet reminder
    private static final String KEY_MINING_LIMPET_REMINDER_ENABLED = "mining.limpetReminder.enabled";
    private static final String KEY_MINING_LIMPET_REMINDER_MODE = "mining.limpetReminder.mode"; // COUNT or PERCENT
    private static final String KEY_MINING_LIMPET_REMINDER_THRESHOLD = "mining.limpetReminder.threshold"; // COUNT
    private static final String KEY_MINING_LIMPET_REMINDER_THRESHOLD_PERCENT = "mining.limpetReminder.thresholdPercent"; // PERCENT
    /** Mining scatter gather animation: gun platform scale, 100 = current default artwork size. */
    private static final String KEY_MINING_ANIM_GUN_SIZE_PERCENT = "mining.animation.gunSizePercent";
    /** Mining scatter gather animation: asteroid line-art scale, 100 = current default artwork size. */
    private static final String KEY_MINING_ANIM_ASTEROID_SIZE_PERCENT = "mining.animation.asteroidSizePercent";
    /** Mining scatter: gun platform + mining laser during gather (default on). */
    private static final String KEY_MINING_ANIM_SHOW_LASER = "mining.animation.showLaser";
    /** Mining scatter: rotating asteroid line-art markers (default on); off leaves plot dots only. */
    private static final String KEY_MINING_ANIM_SHOW_ASTEROID = "mining.animation.showAsteroid";
    /**
     * Mining scatter: draw every plot point as a line-art asteroid; only the current-asteroid leader row(s)
     * spin (default off — plain dots, with optional gather rocks when {@link #KEY_MINING_ANIM_SHOW_ASTEROID}).
     */
    private static final String KEY_MINING_SCATTER_ASTEROID_ICONS_ALL_POINTS = "mining.scatter.asteroidIconsAllPoints";

    /**
     * Mining preferences ▸ Exobiology: minimum valuable exobiology threshold (million credits) — money bag, bio TTS,
     * and any internal uses that filter on predicted bio value (default 10).
     */
    private static final String KEY_MINING_EXO_BIO_VALUABLE_THRESHOLD_MILLION_CREDITS =
            "mining.exobiology.valuableBioThresholdMillionCredits";

    /** Legacy key; read when {@link #KEY_MINING_EXO_BIO_VALUABLE_THRESHOLD_MILLION_CREDITS} is unset. */
    private static final String KEY_BIO_VALUABLE_THRESHOLD_MILLION_CREDITS = "exobiology.valuableThresholdMillionCredits";

    /** Removed Nearby-tab UI; still read once for migration if no newer keys are set. */
    private static final String LEGACY_NEARBY_MIN_VALUE_MILLION_CREDITS = "nearby.minValueMillionCredits";

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
     * Mirrors {@link org.dce.ed.OverlayFrame#getMouseInteractionMode()}: non-{@link MouseInteractionMode#NORMAL}
     * means OS-level mouse pass-through (Selective or Full). Unlike {@link #isPassThroughWindowActive()}, this
     * flips when the user cycles the mouse-mode control.
     */
    private static volatile MouseInteractionMode overlayMouseInteractionMode = MouseInteractionMode.FULL_PASS_THROUGH;

    public static void setOverlayMouseInteractionMode(MouseInteractionMode mode) {
        overlayMouseInteractionMode = mode != null ? mode : MouseInteractionMode.NORMAL;
    }

    public static MouseInteractionMode getOverlayMouseInteractionMode() {
        return overlayMouseInteractionMode != null ? overlayMouseInteractionMode : MouseInteractionMode.NORMAL;
    }

    /** @deprecated Prefer {@link #setOverlayMouseInteractionMode(MouseInteractionMode)}; kept for call-site migration. */
    public static void setOverlayMousePassThroughToGame(boolean enabled) {
        setOverlayMouseInteractionMode(enabled ? MouseInteractionMode.FULL_PASS_THROUGH : MouseInteractionMode.NORMAL);
    }

    /** True when mode is Selective or Full (clicks may pass through outside interactive regions). */
    public static boolean isOverlayMousePassThroughToGame() {
        return getOverlayMouseInteractionMode().isPassThroughLike();
    }

    /** True only in Full MPT, where hover-dwell and global wheel controls remain active. */
    public static boolean isOverlayFullMousePassThrough() {
        return getOverlayMouseInteractionMode() == MouseInteractionMode.FULL_PASS_THROUGH;
    }

    /**
     * Last saved mouse-interaction mode for overlay (undecorated) mode.
     *
     * @param defaultIfUnset used when neither the mode key nor the legacy boolean has been written
     *                       (migration: use {@link MouseInteractionMode#FULL_PASS_THROUGH} to match
     *                       legacy “overlay on ⇒ clicks pass through” behavior)
     */
    public static MouseInteractionMode getOverlayMouseInteractionModePersisted(MouseInteractionMode defaultIfUnset) {
        String raw = PREFS.get(KEY_OVERLAY_MOUSE_INTERACTION_MODE, null);
        if (raw != null && !raw.isBlank()) {
            return MouseInteractionMode.fromPrefsValue(raw, defaultIfUnset);
        }
        // Migrate legacy boolean once.
        if (PREFS.get(KEY_OVERLAY_MOUSE_PASS_THROUGH_TO_GAME, null) != null) {
            boolean legacy = PREFS.getBoolean(KEY_OVERLAY_MOUSE_PASS_THROUGH_TO_GAME, true);
            MouseInteractionMode migrated = legacy
                    ? MouseInteractionMode.FULL_PASS_THROUGH
                    : MouseInteractionMode.NORMAL;
            putOverlayMouseInteractionModePersisted(migrated);
            return migrated;
        }
        return defaultIfUnset != null ? defaultIfUnset : MouseInteractionMode.FULL_PASS_THROUGH;
    }

    public static void putOverlayMouseInteractionModePersisted(MouseInteractionMode mode) {
        MouseInteractionMode m = mode != null ? mode : MouseInteractionMode.NORMAL;
        PREFS.put(KEY_OVERLAY_MOUSE_INTERACTION_MODE, m.prefsValue());
        // Keep legacy boolean in sync for older readers / tools.
        PREFS.putBoolean(KEY_OVERLAY_MOUSE_PASS_THROUGH_TO_GAME, m.isPassThroughLike());
        flushBackingStore();
    }

    /**
     * Last saved mouse pass-through preference for overlay (undecorated) mode.
     *
     * @param defaultIfUnset used when the key has never been written (migration: use {@code true} to match
     *                       legacy “overlay on ⇒ clicks pass through” behavior)
     * @deprecated Prefer {@link #getOverlayMouseInteractionModePersisted(MouseInteractionMode)}
     */
    public static boolean getOverlayMousePassThroughToGamePersisted(boolean defaultIfUnset) {
        MouseInteractionMode def = defaultIfUnset
                ? MouseInteractionMode.FULL_PASS_THROUGH
                : MouseInteractionMode.NORMAL;
        return getOverlayMouseInteractionModePersisted(def).isPassThroughLike();
    }

    /** @deprecated Prefer {@link #putOverlayMouseInteractionModePersisted(MouseInteractionMode)} */
    public static void putOverlayMousePassThroughToGamePersisted(boolean enabled) {
        putOverlayMouseInteractionModePersisted(
                enabled ? MouseInteractionMode.FULL_PASS_THROUGH : MouseInteractionMode.NORMAL);
    }

    /**
     * Root-pane client property ({@link Boolean}): when set on a floating tab window (or any host),
     * chrome painters use that window's transparency instead of the main overlay's global mode.
     */
    public static final String WINDOW_CHROME_TRANSPARENT_KEY = "edo.windowChromeTransparent";

    /**
     * Root-pane client property ({@link Integer} 0–100): transparency percent for that window's chrome.
     */
    public static final String WINDOW_CHROME_TRANSPARENCY_PCT_KEY = "edo.windowChromeTransparencyPct";

    /**
     * Root-pane client property ({@link MouseInteractionMode}): mouse mode for that window
     * (floating tabs), so selective chrome punches follow the float rather than the main overlay.
     */
    public static final String WINDOW_MOUSE_MODE_KEY = "edo.windowMouseMode";

    /**
     * True when table headers, tab row, etc. should use transparent fills so the desktop shows through.
     * Only the undecorated overlay host supports see-through; percent follows mouse pass-through on/off.
     */
    public static boolean overlayChromeRequestsTransparency() {
        return overlayChromeRequestsTransparency(null);
    }

    /**
     * Same as {@link #overlayChromeRequestsTransparency()}, but prefers the hosting window's chrome
     * flags when {@code under} is inside a floating tab frame (or any root pane that set
     * {@link #WINDOW_CHROME_TRANSPARENT_KEY}).
     */
    public static boolean overlayChromeRequestsTransparency(java.awt.Component under) {
        Boolean local = windowChromeTransparent(under);
        if (local != null) {
            return local;
        }
        return passThroughWindowActive && getActiveOverlayTransparencyPercent() > 0;
    }

    /** Active background transparency % for the current mouse pass-through state on the overlay host. */
    public static int getActiveOverlayTransparencyPercent() {
        return getActiveOverlayTransparencyPercent(null);
    }

    /**
     * Transparency % for chrome under {@code under}'s window when that window published
     * {@link #WINDOW_CHROME_TRANSPARENCY_PCT_KEY}; otherwise the main overlay's active %.
     */
    public static int getActiveOverlayTransparencyPercent(java.awt.Component under) {
        Integer localPct = windowChromeTransparencyPercent(under);
        if (localPct != null) {
            return Math.max(0, Math.min(100, localPct));
        }
        return isOverlayMousePassThroughToGame()
                ? getPassThroughTransparencyPercent()
                : getNormalTransparencyPercent();
    }

    /**
     * Fill for see-through chrome (scroll viewports, table headers): theme background RGB at the active
     * transparency percent. Alpha 0 (100% transparent) means painters should CLEAR instead of fill.
     */
    public static Color getActiveOverlayChromeBackground() {
        return getActiveOverlayChromeBackground(null);
    }

    public static Color getActiveOverlayChromeBackground(java.awt.Component under) {
        Color base = EdoUi.fromRgbInt(getUiBackgroundRgb());
        return buildOverlayBackgroundColor(base, getActiveOverlayTransparencyPercent(under));
    }

    /**
     * Publishes window-local chrome transparency for floating tab hosts (and optionally the main
     * overlay) so painters under that tree do not follow a different window's mouse mode.
     */
    public static void publishWindowChromeTransparency(javax.swing.JRootPane rootPane,
            boolean treatAsTransparent, int transparencyPercent) {
        if (rootPane == null) {
            return;
        }
        rootPane.putClientProperty(WINDOW_CHROME_TRANSPARENT_KEY, treatAsTransparent);
        rootPane.putClientProperty(WINDOW_CHROME_TRANSPARENCY_PCT_KEY,
                Math.max(0, Math.min(100, transparencyPercent)));
    }

    private static Boolean windowChromeTransparent(java.awt.Component under) {
        javax.swing.JRootPane rp = rootPaneOf(under);
        if (rp == null) {
            return null;
        }
        Object v = rp.getClientProperty(WINDOW_CHROME_TRANSPARENT_KEY);
        return v instanceof Boolean b ? b : null;
    }

    private static Integer windowChromeTransparencyPercent(java.awt.Component under) {
        javax.swing.JRootPane rp = rootPaneOf(under);
        if (rp == null) {
            return null;
        }
        Object v = rp.getClientProperty(WINDOW_CHROME_TRANSPARENCY_PCT_KEY);
        return v instanceof Integer i ? i : null;
    }

    private static javax.swing.JRootPane rootPaneOf(java.awt.Component under) {
        if (under == null) {
            return null;
        }
        if (under instanceof javax.swing.JRootPane rp) {
            return rp;
        }
        if (under instanceof javax.swing.RootPaneContainer rpc) {
            return rpc.getRootPane();
        }
        return javax.swing.SwingUtilities.getRootPane(under);
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

    public static boolean isOverlayTabMissionsVisible() {
        return PREFS.getBoolean(KEY_OVERLAY_TAB_MISSIONS_VISIBLE, true);
    }

    public static void setOverlayTabMissionsVisible(boolean visible) {
        PREFS.putBoolean(KEY_OVERLAY_TAB_MISSIONS_VISIBLE, visible);
    }

    public static boolean isOverlayTabFleetCarrierVisible() {
        return PREFS.getBoolean(KEY_OVERLAY_TAB_FLEET_CARRIER_VISIBLE, true);
    }

    public static void setOverlayTabFleetCarrierVisible(boolean visible) {
        PREFS.putBoolean(KEY_OVERLAY_TAB_FLEET_CARRIER_VISIBLE, visible);
    }

    public static boolean isOverlayTabEngineeringVisible() {
        return PREFS.getBoolean(KEY_OVERLAY_TAB_ENGINEERING_VISIBLE, true);
    }

    public static void setOverlayTabEngineeringVisible(boolean visible) {
        PREFS.putBoolean(KEY_OVERLAY_TAB_ENGINEERING_VISIBLE, visible);
    }

    public static int getEngineeringMaterialsSortColumn() {
        return PREFS.getInt(KEY_ENGINEERING_MATERIALS_SORT_COLUMN, 4);
    }

    public static void setEngineeringMaterialsSortColumn(int column) {
        PREFS.putInt(KEY_ENGINEERING_MATERIALS_SORT_COLUMN, column);
    }

    public static boolean isEngineeringMaterialsSortDescending() {
        return PREFS.getBoolean(KEY_ENGINEERING_MATERIALS_SORT_DESC, true);
    }

    public static void setEngineeringMaterialsSortDescending(boolean descending) {
        PREFS.putBoolean(KEY_ENGINEERING_MATERIALS_SORT_DESC, descending);
    }

    public static int getEngineeringTradeSortColumn() {
        return PREFS.getInt(KEY_ENGINEERING_TRADE_SORT_COLUMN, 0);
    }

    public static void setEngineeringTradeSortColumn(int column) {
        PREFS.putInt(KEY_ENGINEERING_TRADE_SORT_COLUMN, column);
    }

    public static boolean isEngineeringTradeSortDescending() {
        return PREFS.getBoolean(KEY_ENGINEERING_TRADE_SORT_DESC, false);
    }

    public static void setEngineeringTradeSortDescending(boolean descending) {
        PREFS.putBoolean(KEY_ENGINEERING_TRADE_SORT_DESC, descending);
    }

    public static int getEngineeringBlueprintPickerSortColumn() {
        return PREFS.getInt(KEY_ENGINEERING_BLUEPRINT_SORT_COLUMN, 0);
    }

    public static void setEngineeringBlueprintPickerSortColumn(int column) {
        PREFS.putInt(KEY_ENGINEERING_BLUEPRINT_SORT_COLUMN, column);
    }

    public static boolean isEngineeringBlueprintPickerSortDescending() {
        return PREFS.getBoolean(KEY_ENGINEERING_BLUEPRINT_SORT_DESC, false);
    }

    public static void setEngineeringBlueprintPickerSortDescending(boolean descending) {
        PREFS.putBoolean(KEY_ENGINEERING_BLUEPRINT_SORT_DESC, descending);
    }

    public static boolean isEngineeringBlueprintPickerInstalledOnly() {
        return PREFS.getBoolean(KEY_ENGINEERING_BLUEPRINT_INSTALLED_ONLY, true);
    }

    public static void setEngineeringBlueprintPickerInstalledOnly(boolean installedOnly) {
        PREFS.putBoolean(KEY_ENGINEERING_BLUEPRINT_INSTALLED_ONLY, installedOnly);
    }

    public static boolean isEngineeringMaterialsSectionVisible() {
        return PREFS.getBoolean(KEY_ENGINEERING_MATERIALS_SECTION_VISIBLE, true);
    }

    public static void setEngineeringMaterialsSectionVisible(boolean visible) {
        PREFS.putBoolean(KEY_ENGINEERING_MATERIALS_SECTION_VISIBLE, visible);
    }

    /** @return saved divider location in pixels, or {@code -1} if never saved */
    public static int getEngineeringLowerSplitDividerLocation() {
        return PREFS.getInt(KEY_ENGINEERING_LOWER_SPLIT_DIVIDER, -1);
    }

    public static void setEngineeringLowerSplitDividerLocation(int location) {
        PREFS.putInt(KEY_ENGINEERING_LOWER_SPLIT_DIVIDER, location);
    }

    public static boolean isEngineeringBuildProgressHideModulesWithGoals() {
        return PREFS.getBoolean(KEY_ENGINEERING_BUILD_PROGRESS_HIDE_MODULES_WITH_GOALS, true);
    }

    public static void setEngineeringBuildProgressHideModulesWithGoals(boolean hide) {
        PREFS.putBoolean(KEY_ENGINEERING_BUILD_PROGRESS_HIDE_MODULES_WITH_GOALS, hide);
    }

    /**
     * Ship id last chosen in Add Goal, or {@code null} if unset / follow equipped.
     */
    public static Long getEngineeringAddGoalPreferredShipId() {
        long id = PREFS.getLong(KEY_ENGINEERING_ADD_GOAL_PREFERRED_SHIP_ID, -1L);
        return id >= 0L ? Long.valueOf(id) : null;
    }

    public static void setEngineeringAddGoalPreferredShipId(long shipId) {
        if (shipId < 0L) {
            PREFS.remove(KEY_ENGINEERING_ADD_GOAL_PREFERRED_SHIP_ID);
            return;
        }
        PREFS.putLong(KEY_ENGINEERING_ADD_GOAL_PREFERRED_SHIP_ID, shipId);
    }

    /**
     * Equipped ship id when the Add Goal preferred ship was recorded.
     * When the current loadout ship differs, Add Goal should return to the equipped ship.
     */
    public static Long getEngineeringAddGoalEquippedBaselineId() {
        long id = PREFS.getLong(KEY_ENGINEERING_ADD_GOAL_EQUIPPED_BASELINE_ID, -1L);
        return id >= 0L ? Long.valueOf(id) : null;
    }

    public static void setEngineeringAddGoalEquippedBaselineId(long shipId) {
        if (shipId < 0L) {
            PREFS.remove(KEY_ENGINEERING_ADD_GOAL_EQUIPPED_BASELINE_ID);
            return;
        }
        PREFS.putLong(KEY_ENGINEERING_ADD_GOAL_EQUIPPED_BASELINE_ID, shipId);
    }

    public static boolean isOverlayTabControlPanelVisible() {
        if (PREFS.get(KEY_OVERLAY_TAB_CONTROL_PANEL_VISIBLE, null) != null) {
            return PREFS.getBoolean(KEY_OVERLAY_TAB_CONTROL_PANEL_VISIBLE, true);
        }
        return PREFS.getBoolean(KEY_OVERLAY_TAB_EXEC_VISIBLE, true);
    }

    public static void setOverlayTabControlPanelVisible(boolean visible) {
        PREFS.putBoolean(KEY_OVERLAY_TAB_CONTROL_PANEL_VISIBLE, visible);
    }

    /** @deprecated use {@link #isOverlayTabControlPanelVisible()} */
    public static boolean isOverlayTabExecVisible() {
        return isOverlayTabControlPanelVisible();
    }

    /** @deprecated use {@link #setOverlayTabControlPanelVisible(boolean)} */
    public static void setOverlayTabExecVisible(boolean visible) {
        setOverlayTabControlPanelVisible(visible);
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

    /**
     * Global hotkey to cycle to the next visible overlay tab (skips tabs hidden in Preferences → Overlay → Visible tabs).
     * Default: F8.
     */
    public static int getNextShownTabKeyCode() {
        return PREFS.getInt(KEY_NEXT_TAB_KEYCODE, NativeKeyEvent.VC_F8);
    }

    public static void setNextShownTabKeyCode(int keyCode) {
        PREFS.putInt(KEY_NEXT_TAB_KEYCODE, keyCode);
    }

    public static boolean isAutoSwitchRouteOnGalaxyMap() {
        return PREFS.getBoolean(KEY_AUTOSWITCH_GUIFOCUS_GALAXYMAP, true);
    }

    public static void setAutoSwitchRouteOnGalaxyMap(boolean enabled) {
        PREFS.putBoolean(KEY_AUTOSWITCH_GUIFOCUS_GALAXYMAP, enabled);
    }

    public static boolean isAutoSwitchSystemOnSystemMap() {
        return PREFS.getBoolean(KEY_AUTOSWITCH_GUIFOCUS_SYSTEMMAP, true);
    }

    public static void setAutoSwitchSystemOnSystemMap(boolean enabled) {
        PREFS.putBoolean(KEY_AUTOSWITCH_GUIFOCUS_SYSTEMMAP, enabled);
    }

    public static boolean isAutoSwitchTabOnFsdTarget() {
        return PREFS.getBoolean(KEY_AUTOSWITCH_FSD_TARGET, true);
    }

    public static void setAutoSwitchTabOnFsdTarget(boolean enabled) {
        PREFS.putBoolean(KEY_AUTOSWITCH_FSD_TARGET, enabled);
    }

    public static boolean isAutoSwitchSystemTabOnJumpOrScan() {
        return PREFS.getBoolean(KEY_AUTOSWITCH_SYSTEMTAB_ON_JUMP_SCAN, true);
    }

    public static void setAutoSwitchSystemTabOnJumpOrScan(boolean enabled) {
        PREFS.putBoolean(KEY_AUTOSWITCH_SYSTEMTAB_ON_JUMP_SCAN, enabled);
    }

    public static boolean isAutoSwitchMiningOnPlanetaryRing() {
        return PREFS.getBoolean(KEY_AUTOSWITCH_MINING_PLANETARY_RING, true);
    }

    public static void setAutoSwitchMiningOnPlanetaryRing(boolean enabled) {
        PREFS.putBoolean(KEY_AUTOSWITCH_MINING_PLANETARY_RING, enabled);
    }

    public static boolean isAutoSwitchMiningOnStartupPlanetaryRing() {
        return PREFS.getBoolean(KEY_AUTOSWITCH_MINING_STARTUP_PLANETARY_RING, true);
    }

    public static void setAutoSwitchMiningOnStartupPlanetaryRing(boolean enabled) {
        PREFS.putBoolean(KEY_AUTOSWITCH_MINING_STARTUP_PLANETARY_RING, enabled);
    }

    public static boolean isAutoSwitchBiologyOnNearLandableAtmosphere() {
        return PREFS.getBoolean(KEY_AUTOSWITCH_BIOLOGY_NEAR_BODY, true);
    }

    public static void setAutoSwitchBiologyOnNearLandableAtmosphere(boolean enabled) {
        PREFS.putBoolean(KEY_AUTOSWITCH_BIOLOGY_NEAR_BODY, enabled);
    }

    public static boolean isAutoSwitchFleetCarrierOnJsonDrop() {
        return PREFS.getBoolean(KEY_AUTOSWITCH_FLEETCARRIER_ON_DROP, true);
    }

    public static void setAutoSwitchFleetCarrierOnJsonDrop(boolean enabled) {
        PREFS.putBoolean(KEY_AUTOSWITCH_FLEETCARRIER_ON_DROP, enabled);
    }

    public static boolean isAutoExpandBioOnTargetedBody() {
        return PREFS.getBoolean(KEY_SYSTEM_AUTO_EXPAND_BIO_ON_TARGET, true);
    }

    public static void setAutoExpandBioOnTargetedBody(boolean enabled) {
        PREFS.putBoolean(KEY_SYSTEM_AUTO_EXPAND_BIO_ON_TARGET, enabled);
    }

    public static SystemTabTableSortMode getSystemTabTableSortMode() {
        String mode = PREFS.get(KEY_SYSTEM_TAB_TABLE_SORT_MODE, "");
        if (!mode.isEmpty()) {
            return SystemTabTableSortMode.fromPrefsString(mode);
        }
        return PREFS.getBoolean(KEY_SYSTEM_TAB_DISTANCE_FROM_SHIP, false)
                ? SystemTabTableSortMode.FROM_SHIP
                : SystemTabTableSortMode.FROM_STAR;
    }

    public static void setSystemTabTableSortMode(SystemTabTableSortMode mode) {
        if (mode == null) {
            mode = SystemTabTableSortMode.FROM_STAR;
        }
        PREFS.put(KEY_SYSTEM_TAB_TABLE_SORT_MODE, mode.toPrefsString());
        PREFS.putBoolean(KEY_SYSTEM_TAB_DISTANCE_FROM_SHIP, mode == SystemTabTableSortMode.FROM_SHIP);
        flushBackingStore();
    }

    public static boolean isSystemTabDistanceFromShip() {
        return getSystemTabTableSortMode() == SystemTabTableSortMode.FROM_SHIP;
    }

    public static void setSystemTabDistanceFromShip(boolean enabled) {
        setSystemTabTableSortMode(enabled ? SystemTabTableSortMode.FROM_SHIP : SystemTabTableSortMode.FROM_STAR);
    }

    public static SystemTabShipRefMode getSystemTabShipRefMode() {
        return SystemTabShipRefMode.fromPrefsString(PREFS.get(KEY_SYSTEM_TAB_SHIP_REF_MODE, ""));
    }

    public static void setSystemTabShipRefMode(SystemTabShipRefMode mode) {
        if (mode == null) {
            PREFS.remove(KEY_SYSTEM_TAB_SHIP_REF_MODE);
        } else {
            PREFS.put(KEY_SYSTEM_TAB_SHIP_REF_MODE, mode.toPrefsString());
        }
    }

    /** Default on: HUD-target subsystem framing in {@link SystemTabShipRefMode#TARGETED_BODY}. */
    public static boolean isSystemPlanMapAutoZoomHudTargetSubsystem() {
        return PREFS.getBoolean(KEY_SYSTEM_PLAN_MAP_AUTO_ZOOM_HUD_TARGET, true);
    }

    public static void setSystemPlanMapAutoZoomHudTargetSubsystem(boolean enabled) {
        PREFS.putBoolean(KEY_SYSTEM_PLAN_MAP_AUTO_ZOOM_HUD_TARGET, enabled);
    }

    /**
     * Last persisted HUD navigation body id for {@link SystemTabShipRefMode#TARGETED_BODY} in this system, or null.
     */
    public static Integer getSystemTabStickyHudTargetBodyId(long systemAddress) {
        if (systemAddress == 0L) {
            return null;
        }
        int v = PREFS.getInt(KEY_SYSTEM_TAB_STICKY_HUD_TARGET_BODY_PREFIX + systemAddress, -1);
        return v >= 0 ? Integer.valueOf(v) : null;
    }

    public static void setSystemTabStickyHudTargetBodyId(long systemAddress, Integer bodyId) {
        if (systemAddress == 0L) {
            return;
        }
        String k = KEY_SYSTEM_TAB_STICKY_HUD_TARGET_BODY_PREFIX + systemAddress;
        if (bodyId == null || bodyId.intValue() < 0) {
            PREFS.remove(k);
        } else {
            PREFS.putInt(k, bodyId.intValue());
        }
    }

    /**
     * Last persisted non-primary body id for proximity / plan-map sticky in this system, or null if unset / invalid.
     */
    public static Integer getSystemTabStickyLastVisitedBodyId(long systemAddress) {
        if (systemAddress == 0L) {
            return null;
        }
        int v = PREFS.getInt(KEY_SYSTEM_TAB_STICKY_LAST_VISITED_BODY_PREFIX + systemAddress, -1);
        return v >= 0 ? Integer.valueOf(v) : null;
    }

    /**
     * Persists {@link org.dce.ed.SystemTabPanel} sticky last-visited body for a system (non-star body id only).
     * Pass null to remove the entry (e.g. id no longer in body list).
     */
    public static void setSystemTabStickyLastVisitedBodyId(long systemAddress, Integer bodyId) {
        if (systemAddress == 0L) {
            return;
        }
        String k = KEY_SYSTEM_TAB_STICKY_LAST_VISITED_BODY_PREFIX + systemAddress;
        if (bodyId == null || bodyId.intValue() < 0) {
            PREFS.remove(k);
        } else {
            PREFS.putInt(k, bodyId.intValue());
        }
    }

    /** Orbit fast-forward: model days per second of real time (System tab map toolbar). */
    public static int getSystemTabOrbitAnimDaysPerWallSecond() {
        int v = PREFS.getInt(KEY_SYSTEM_TAB_ORBIT_ANIM_DAYS_PER_WALL_SECOND, SYSTEM_TAB_ORBIT_ANIM_DAYS_PER_WALL_SECOND_DEFAULT);
        if (v < SYSTEM_TAB_ORBIT_ANIM_DAYS_PER_WALL_SECOND_MIN) {
            return SYSTEM_TAB_ORBIT_ANIM_DAYS_PER_WALL_SECOND_MIN;
        }
        if (v > SYSTEM_TAB_ORBIT_ANIM_DAYS_PER_WALL_SECOND_MAX) {
            return SYSTEM_TAB_ORBIT_ANIM_DAYS_PER_WALL_SECOND_MAX;
        }
        return v;
    }

    public static void setSystemTabOrbitAnimDaysPerWallSecond(int daysPerWallSecond) {
        int c = daysPerWallSecond;
        if (c < SYSTEM_TAB_ORBIT_ANIM_DAYS_PER_WALL_SECOND_MIN) {
            c = SYSTEM_TAB_ORBIT_ANIM_DAYS_PER_WALL_SECOND_MIN;
        } else if (c > SYSTEM_TAB_ORBIT_ANIM_DAYS_PER_WALL_SECOND_MAX) {
            c = SYSTEM_TAB_ORBIT_ANIM_DAYS_PER_WALL_SECOND_MAX;
        }
        PREFS.putInt(KEY_SYSTEM_TAB_ORBIT_ANIM_DAYS_PER_WALL_SECOND, c);
    }

    public static int getSystemTabOrbitAnimDaysPerWallSecondMin() {
        return SYSTEM_TAB_ORBIT_ANIM_DAYS_PER_WALL_SECOND_MIN;
    }

    public static int getSystemTabOrbitAnimDaysPerWallSecondMax() {
        return SYSTEM_TAB_ORBIT_ANIM_DAYS_PER_WALL_SECOND_MAX;
    }

    public static int getSystemPlanMapViewTiltDegrees() {
        return clampSystemPlanMapViewTiltDegrees(PREFS.getInt(KEY_SYSTEM_PLAN_MAP_VIEW_TILT_DEG, 0));
    }

    public static void setSystemPlanMapViewTiltDegrees(int degrees) {
        int clamped = clampSystemPlanMapViewTiltDegrees(degrees);
        if (clamped <= 0) {
            PREFS.remove(KEY_SYSTEM_PLAN_MAP_VIEW_TILT_DEG);
        } else {
            PREFS.putInt(KEY_SYSTEM_PLAN_MAP_VIEW_TILT_DEG, clamped);
        }
        flushBackingStore();
    }

    public static boolean isSystemPlanMapCollapsed() {
        return PREFS.getBoolean(KEY_SYSTEM_PLAN_MAP_COLLAPSED, false);
    }

    public static void setSystemPlanMapCollapsed(boolean collapsed) {
        PREFS.putBoolean(KEY_SYSTEM_PLAN_MAP_COLLAPSED, collapsed);
        flushBackingStore();
    }

    private static int clampSystemPlanMapViewTiltDegrees(int degrees) {
        if (degrees <= 0) {
            return 0;
        }
        return Math.min(90, degrees);
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

    /** Persisted {@link #KEY_SPEECH_ENABLED} only; ignores test speech gating in {@link #isSpeechEnabled()}. */
    static boolean isSpeechEnabledPersisted() {
        return PREFS.getBoolean(KEY_SPEECH_ENABLED, true);
    }

    public static boolean isSpeechEnabled() {
        // Deterministic tests: treat speech as off unless @AllowSpeechForTest opts into gating checks.
        // Audio playback is separately suppressed via edo.test.disableSpeech in PollyTtsCached / TtsSprintf.
        if (Boolean.getBoolean("edo.test.disableSpeech")
                && !Boolean.getBoolean("edo.test.allowSpeechGating")) {
            return false;
        }
        return isSpeechEnabledPersisted();
    }

    public static void setSpeechEnabled(boolean enabled) {
        PREFS.putBoolean(KEY_SPEECH_ENABLED, enabled);
    }

    public static boolean isFirstDiscoveredSystemAnnouncementEnabled() {
        return PREFS.getBoolean(KEY_SPEECH_FIRST_DISCOVERED_SYSTEM_ENABLED, true);
    }

    public static void setFirstDiscoveredSystemAnnouncementEnabled(boolean enabled) {
        PREFS.putBoolean(KEY_SPEECH_FIRST_DISCOVERED_SYSTEM_ENABLED, enabled);
    }

    public static boolean isBountyScanFirstAnnouncementEnabled() {
        return PREFS.getBoolean(KEY_SPEECH_BOUNTY_SCAN_FIRST_ENABLED, true);
    }

    public static void setBountyScanFirstAnnouncementEnabled(boolean enabled) {
        PREFS.putBoolean(KEY_SPEECH_BOUNTY_SCAN_FIRST_ENABLED, enabled);
    }

    public static boolean isBountyScanAdditionalAnnouncementEnabled() {
        return PREFS.getBoolean(KEY_SPEECH_BOUNTY_SCAN_ADDITIONAL_ENABLED, true);
    }

    public static void setBountyScanAdditionalAnnouncementEnabled(boolean enabled) {
        PREFS.putBoolean(KEY_SPEECH_BOUNTY_SCAN_ADDITIONAL_ENABLED, enabled);
    }

    public static boolean isMissionProgressAnnouncementEnabled() {
        return PREFS.getBoolean(KEY_SPEECH_MISSION_PROGRESS_ENABLED, true);
    }

    public static void setMissionProgressAnnouncementEnabled(boolean enabled) {
        PREFS.putBoolean(KEY_SPEECH_MISSION_PROGRESS_ENABLED, enabled);
    }

    /**
     * When true, {@link org.dce.ed.tts.PollyTtsCached} may call Amazon Polly for missing cache clips.
     * Runtime playback is cache-only; only {@link org.dce.ed.tts.VoiceCacheWarmer} toggles this flag.
     */
    private static final AtomicBoolean speechUseAwsSynthesisWarmer = new AtomicBoolean(false);

    /**
     * Enables Polly synthesis while {@link org.dce.ed.tts.VoiceCacheWarmer} runs; runtime playback leaves this false.
     */
    public static void setSpeechUseAwsSynthesisForWarmer(boolean useAws) {
        speechUseAwsSynthesisWarmer.set(useAws);
    }

    public static boolean isSpeechUseAwsSynthesis() {
        return speechUseAwsSynthesisWarmer.get();
    }

    /** @deprecated Runtime no longer reads or writes this preference; retained for migration only. */
    @Deprecated
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
        // Default voice: "Matthew" (standard US English male)
        String v = PREFS.get(KEY_SPEECH_VOICE, "Matthew");
        if (v == null || v.isBlank() || "null".equalsIgnoreCase(v.trim())) {
            return "Matthew";
        }
        return v.trim();
    }

    public static void setSpeechVoiceId(String voiceId) {
        if (voiceId == null || voiceId.isBlank() || "null".equalsIgnoreCase(voiceId.trim())) {
            voiceId = "Matthew";
        }
        PREFS.put(KEY_SPEECH_VOICE, voiceId.trim());
    }

    public static String getSpeechAwsRegion() {
        // Legacy preference; runtime Polly uses AWS_REGION / AWS_DEFAULT_REGION env vars.
        return PREFS.get(KEY_SPEECH_REGION, "us-east-1");
    }

    /** @deprecated No longer exposed in preferences; retained for stored-value migration only. */
    @Deprecated
    public static void setSpeechAwsRegion(String region) {
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }
        PREFS.put(KEY_SPEECH_REGION, region.trim());
    }

    public static String getSpeechAwsProfile() {
        // Legacy preference; runtime Polly uses the standard AWS SDK credential chain (incl. AWS_PROFILE).
        return PREFS.get(KEY_SPEECH_AWS_PROFILE, "");
    }

    /** @deprecated No longer exposed in preferences; retained for stored-value migration only. */
    @Deprecated
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

    /**
     * Revision of the pre-built voice pack last installed from GitHub (AWS-off path). See
     * {@link org.dce.ed.tts.VoicePackManager#SPEECH_PACK_REVISION}.
     */
    public static int getSpeechPackInstalledRevision() {
        return PREFS.getInt(KEY_SPEECH_PACK_INSTALLED_REVISION, 0);
    }

    public static String getSpeechPackInstalledVoice() {
        return PREFS.get(KEY_SPEECH_PACK_INSTALLED_VOICE, "").trim();
    }

    /**
     * Record a successful GitHub voice-pack install so startup can skip until the app revision bumps again.
     */
    public static void setSpeechPackInstalledInfo(int revision, String voiceId) {
        PREFS.putInt(KEY_SPEECH_PACK_INSTALLED_REVISION, Math.max(0, revision));
        if (voiceId == null || voiceId.isBlank()) {
            PREFS.put(KEY_SPEECH_PACK_INSTALLED_VOICE, "");
        } else {
            PREFS.put(KEY_SPEECH_PACK_INSTALLED_VOICE, voiceId.trim());
        }
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
     * Prospector log backend: "local" (CSV file), "google" (Google Sheets), or "both" (CSV mirror + Sheets, with
     * a user-pickable primary display source). Defaults to "local".
     */
    public static String getMiningLogBackend() {
        String v = PREFS.get(KEY_MINING_LOG_BACKEND, "local").trim();
        if ("google".equalsIgnoreCase(v)) {
            return "google";
        }
        if ("both".equalsIgnoreCase(v)) {
            return "both";
        }
        return "local";
    }

    public static void setMiningLogBackend(String backend) {
        if (backend == null) {
            backend = "local";
        }
        String t = backend.trim();
        String normalized;
        if ("google".equalsIgnoreCase(t)) {
            normalized = "google";
        } else if ("both".equalsIgnoreCase(t)) {
            normalized = "both";
        } else {
            normalized = "local";
        }
        PREFS.put(KEY_MINING_LOG_BACKEND, normalized);
    }

    /**
     * Both-mode primary display source: which side the Mining tab table reads from and the composite treats as
     * authoritative. {@code "google"} (default) or {@code "local"}. Only meaningful when
     * {@link #getMiningLogBackend()} returns {@code "both"}.
     */
    public static String getMiningLogBothPrimary() {
        String v = PREFS.get(KEY_MINING_LOG_BOTH_PRIMARY, "google").trim();
        return "local".equalsIgnoreCase(v) ? "local" : "google";
    }

    public static void setMiningLogBothPrimary(String primary) {
        if (primary == null) {
            primary = "google";
        }
        PREFS.put(KEY_MINING_LOG_BOTH_PRIMARY, "local".equalsIgnoreCase(primary.trim()) ? "local" : "google");
    }

    /**
     * Per-commander flag set after the first successful Both-mode sync of that commander's rows. Use the same
     * sanitized {@code CMDR <name>}-style key for both Sheets tabs and CSV files.
     */
    public static boolean isMiningLogBothSyncedOnce(String sanitizedCommanderKey) {
        if (sanitizedCommanderKey == null || sanitizedCommanderKey.isBlank()) {
            return false;
        }
        return PREFS.getBoolean(KEY_MINING_LOG_BOTH_SYNCED_ONCE_PREFIX + sanitizedCommanderKey, false);
    }

    public static void setMiningLogBothSyncedOnce(String sanitizedCommanderKey, boolean synced) {
        if (sanitizedCommanderKey == null || sanitizedCommanderKey.isBlank()) {
            return;
        }
        PREFS.putBoolean(KEY_MINING_LOG_BOTH_SYNCED_ONCE_PREFIX + sanitizedCommanderKey, synced);
    }

    /**
     * Clears every {@code mining.log.bothSyncedOnce.*} flag. Called when the user toggles backend off Both so the
     * next time they re-enable Both we run the auto-sync again.
     */
    public static void clearAllMiningLogBothSyncedOnce() {
        try {
            for (String k : PREFS.keys()) {
                if (k != null && k.startsWith(KEY_MINING_LOG_BOTH_SYNCED_ONCE_PREFIX)) {
                    PREFS.remove(k);
                }
            }
        } catch (BackingStoreException ignored) {
        }
    }

    /**
     * Google Sheets URL for prospector log when backend is "google".
     */
    public static String getMiningGoogleSheetsUrl() {
        return PREFS.get(KEY_MINING_GOOGLE_SHEETS_URL, "").trim();
    }

    public static void setMiningGoogleSheetsUrl(String url) {
        String next = url != null ? url.trim() : "";
        // Defensive merge-save: transient blank UI reads (or failed connectivity flows) must not
        // erase an already configured spreadsheet URL. Use clearMiningGoogleSheetsUrl() to reset.
        if (next.isEmpty()) {
            String existing = PREFS.get(KEY_MINING_GOOGLE_SHEETS_URL, "").trim();
            if (!existing.isEmpty()) {
                return;
            }
        }
        PREFS.put(KEY_MINING_GOOGLE_SHEETS_URL, next);
    }

    /** Explicitly clears the saved Google Sheets URL from preferences. */
    public static void clearMiningGoogleSheetsUrl() {
        PREFS.put(KEY_MINING_GOOGLE_SHEETS_URL, "");
    }

    /**
     * Persists pending preference writes to the backing store (best-effort). Call after applying Preferences
     * so edits survive abrupt process exit more reliably.
     */
    public static void flushBackingStore() {
        try {
            PREFS.flush();
        } catch (BackingStoreException ignored) {
        }
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
     * Google Sheets prospector layout generation: 0 = legacy (mixed commanders on first sheet), 1 = per-commander tabs.
     */
    public static int getMiningGoogleSheetsLayoutVersion() {
        return PREFS.getInt(KEY_MINING_GOOGLE_SHEETS_LAYOUT_VERSION, 0);
    }

    public static void setMiningGoogleSheetsLayoutVersion(int version) {
        PREFS.putInt(KEY_MINING_GOOGLE_SHEETS_LAYOUT_VERSION, Math.max(0, version));
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

    /**
     * True if the Mining tab prospector log should open in scatter plot view; false for table.
     */
    public static boolean isMiningProspectorLogScatterView() {
        return "scatter".equalsIgnoreCase(PREFS.get(KEY_MINING_PROSPECTOR_LOG_VIEW, "table").trim());
    }

    public static void setMiningProspectorLogScatterView(boolean scatter) {
        PREFS.put(KEY_MINING_PROSPECTOR_LOG_VIEW, scatter ? "scatter" : "table");
    }

    /**
     * Fraction of Mining tab height for the ship inventory block (0–1). Remaining height is split
     * between prospector and prospector log / scatter by {@link #getMiningPanelSplitInnerRatio()}.
     */
    public static double getMiningPanelSplitOuterRatio() {
        String s = PREFS.get(KEY_MINING_PANEL_SPLIT_OUTER, "0.333");
        try {
            return clampSplitRatio(Double.parseDouble(s.trim()));
        } catch (Exception e) {
            return 1.0 / 3.0;
        }
    }

    public static void setMiningPanelSplitOuterRatio(double ratio) {
        PREFS.put(KEY_MINING_PANEL_SPLIT_OUTER, Double.toString(clampSplitRatio(ratio)));
    }

    /**
     * Fraction of the area below the inventory split that goes to the prospector block (0–1).
     * The rest is for the prospector log table / scatter plot.
     */
    public static double getMiningPanelSplitInnerRatio() {
        String s = PREFS.get(KEY_MINING_PANEL_SPLIT_INNER, "0.5");
        try {
            return clampSplitRatio(Double.parseDouble(s.trim()));
        } catch (Exception e) {
            return 0.5;
        }
    }

    public static void setMiningPanelSplitInnerRatio(double ratio) {
        PREFS.put(KEY_MINING_PANEL_SPLIT_INNER, Double.toString(clampSplitRatio(ratio)));
    }

    /**
     * Fraction of System tab height (below the header) for the bodies table; the remainder is the plan map.
     */
    public static double getSystemTabPanelTableSplitRatio() {
        String s = PREFS.get(KEY_SYSTEM_TAB_PANEL_TABLE_SPLIT, "0.58");
        try {
            return clampSplitRatio(Double.parseDouble(s.trim()));
        } catch (Exception e) {
            return 0.58;
        }
    }

    public static void setSystemTabPanelTableSplitRatio(double ratio) {
        PREFS.put(KEY_SYSTEM_TAB_PANEL_TABLE_SPLIT, Double.toString(clampSplitRatio(ratio)));
    }

    /**
     * Fraction of Biology tab height (below the header) for the specimen table; the remainder is the surface map.
     */
    public static double getBiologyPanelTableSplitRatio() {
        String s = PREFS.get(KEY_BIOLOGY_PANEL_TABLE_SPLIT, "0.42");
        try {
            return clampSplitRatio(Double.parseDouble(s.trim()));
        } catch (Exception e) {
            return 0.42;
        }
    }

    public static void setBiologyPanelTableSplitRatio(double ratio) {
        PREFS.put(KEY_BIOLOGY_PANEL_TABLE_SPLIT, Double.toString(clampSplitRatio(ratio)));
    }

    /**
     * How the ExoBio surface map draws incomplete / parked sample pins:
     * rays from the map centre, or points at the sample bearing with distance/heading labels.
     */
    public enum BiologyMapDisplayMode {
        RAYS,
        POINTS;

        public String displayName() {
            return this == POINTS ? "Points at location" : "Rays from centre";
        }
    }

    public static BiologyMapDisplayMode getBiologyMapDisplayMode() {
        String v = PREFS.get(KEY_BIOLOGY_MAP_DISPLAY_MODE, BiologyMapDisplayMode.RAYS.name());
        if (v == null) {
            return BiologyMapDisplayMode.RAYS;
        }
        try {
            return BiologyMapDisplayMode.valueOf(v.trim().toUpperCase());
        } catch (Exception e) {
            return BiologyMapDisplayMode.RAYS;
        }
    }

    public static void setBiologyMapDisplayMode(BiologyMapDisplayMode mode) {
        if (mode == null) {
            mode = BiologyMapDisplayMode.RAYS;
        }
        PREFS.put(KEY_BIOLOGY_MAP_DISPLAY_MODE, mode.name());
    }

    private static double clampSplitRatio(double ratio) {
        if (Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            return 0.5;
        }
        if (ratio < 0.05) {
            return 0.05;
        }
        if (ratio > 0.95) {
            return 0.95;
        }
        return ratio;
    }

    /**
     * Minimum valuable exobiology threshold (million credits): System tab money bag per species, bio-discovery TTS,
     * and any code that filters on predicted exobiology value.
     * <p>
     * Stored under {@link #KEY_MINING_EXO_BIO_VALUABLE_THRESHOLD_MILLION_CREDITS} (Mining preferences); falls back to
     * legacy {@code exobiology.valuableThresholdMillionCredits}, then removed Nearby-tab {@code nearby.minValueMillionCredits}
     * if neither is set. Default 10.
     */
    public static double getBioValuableThresholdMillionCredits() {
        String s = PREFS.get(KEY_MINING_EXO_BIO_VALUABLE_THRESHOLD_MILLION_CREDITS, null);
        if (s == null || s.isBlank()) {
            s = PREFS.get(KEY_BIO_VALUABLE_THRESHOLD_MILLION_CREDITS, null);
        }
        if (s == null || s.isBlank()) {
            s = PREFS.get(LEGACY_NEARBY_MIN_VALUE_MILLION_CREDITS, null);
        }
        if (s == null || s.isBlank()) {
            s = "10";
        }
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
        String str = Double.toString(v);
        PREFS.put(KEY_MINING_EXO_BIO_VALUABLE_THRESHOLD_MILLION_CREDITS, str);
        PREFS.put(KEY_BIO_VALUABLE_THRESHOLD_MILLION_CREDITS, str);
    }

    /** Same as {@link #getBioValuableThresholdMillionCredits()} in credits (rounded to nearest credit). */
    public static long getBioValuableThresholdCredits() {
        return getMiningExobiologyValuableBioThresholdCredits();
    }

    /**
     * {@link #getBioValuableThresholdMillionCredits()} as whole credits (rounded). Kept for call-site clarity
     * (System tab money bag, TTS, filters).
     */
    public static long getMiningExobiologyValuableBioThresholdCredits() {
        return Math.round(getBioValuableThresholdMillionCredits() * 1_000_000.0);
    }

    // --- UI Font (System / Route / Biology) ---

    /**
     * Font family name used across major panels (System / Route / Biology).
     * Default matches SystemTabPanel's historical font choice.
     */
    public static String getUiFontName() {
        if (uiFontNameLivePreview != null && !uiFontNameLivePreview.isBlank()) {
            return uiFontNameLivePreview;
        }
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
        if (uiFontSizeLivePreview != null) {
            return clampUiFontSize(uiFontSizeLivePreview.intValue());
        }
        try {
            int sz = Integer.parseInt(PREFS.get(KEY_UI_FONT_SIZE, "17"));
            return clampUiFontSize(sz);
        } catch (Exception e) {
            return 17;
        }
    }

    private static int clampUiFontSize(int size) {
        if (size < 8) {
            return 8;
        }
        if (size > 72) {
            return 72;
        }
        return size;
    }

    /**
     * Preferences dialog: apply spinner/combo font as the effective UI font for reads until cleared.
     */
    public static void setUiFontLivePreview(Font font) {
        if (font == null) {
            clearUiFontLivePreview();
            return;
        }
        String name = font.getFamily();
        if (name == null || name.isBlank() || "Dialog".equals(name)) {
            name = font.getName();
        }
        if (name != null) {
            name = name.trim();
        }
        uiFontNameLivePreview = (name != null && !name.isBlank()) ? name : null;
        uiFontSizeLivePreview = Integer.valueOf(clampUiFontSize(font.getSize()));
    }

    public static void clearUiFontLivePreview() {
        uiFontNameLivePreview = null;
        uiFontSizeLivePreview = null;
    }

    public static void setUiFontSize(int size) {
        PREFS.put(KEY_UI_FONT_SIZE, Integer.toString(clampUiFontSize(size)));
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
    // Fighter pilot reminder
    // ----------------------------

    /**
     * If enabled, warn (status bar + optional speech on undock) when the current ship has a fighter
     * hangar with stocked SLFs but no NPC crew member is set Active.
     */
    public static boolean isFighterPilotReminderEnabled() {
        return PREFS.getBoolean(KEY_FIGHTER_PILOT_REMINDER_ENABLED, true);
    }

    public static void setFighterPilotReminderEnabled(boolean enabled) {
        PREFS.putBoolean(KEY_FIGHTER_PILOT_REMINDER_ENABLED, enabled);
    }

    /**
     * Last known Active NPC crew member for a given ship id ({@code ShipID} from journal Loadout).
     * Survives overlay restarts so we can remember assignment when Elite does not rewrite {@code CrewAssign}.
     */
    public static String getNpcCrewActiveName(int shipId) {
        if (shipId < 0) {
            return null;
        }
        String v = PREFS.get(KEY_NPC_CREW_ACTIVE_SHIP_PREFIX + shipId, "");
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.trim();
    }

    public static void setNpcCrewActiveName(int shipId, String name) {
        if (shipId < 0) {
            return;
        }
        String key = KEY_NPC_CREW_ACTIVE_SHIP_PREFIX + shipId;
        if (name == null || name.isBlank()) {
            PREFS.remove(key);
        } else {
            PREFS.put(key, name.trim());
        }
    }

    /**
     * Export legacy per-ship Active NPC crew prefs (before session_json storage). Empty if none.
     */
    public static java.util.Map<Integer, String> exportNpcCrewActiveByShipId() {
        java.util.Map<Integer, String> out = new java.util.LinkedHashMap<>();
        try {
            for (String key : PREFS.keys()) {
                if (key == null || !key.startsWith(KEY_NPC_CREW_ACTIVE_SHIP_PREFIX)) {
                    continue;
                }
                String suffix = key.substring(KEY_NPC_CREW_ACTIVE_SHIP_PREFIX.length());
                try {
                    int shipId = Integer.parseInt(suffix);
                    String name = PREFS.get(key, "");
                    if (name != null && !name.isBlank()) {
                        out.put(shipId, name.trim());
                    }
                } catch (NumberFormatException ignored) {
                    // Skip non-numeric keys.
                }
            }
        } catch (Exception ignored) {
            return out;
        }
        return out;
    }

    /** Remove legacy per-ship Active NPC crew prefs after migrating into session_json. */
    public static void clearNpcCrewActiveByShipId() {
        try {
            for (String key : PREFS.keys()) {
                if (key != null && key.startsWith(KEY_NPC_CREW_ACTIVE_SHIP_PREFIX)) {
                    PREFS.remove(key);
                }
            }
        } catch (Exception ignored) {
            // Best-effort cleanup.
        }
    }

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

    private static int clampMiningAnimationSizePercent(int percent) {
        int v = percent;
        if (v < 25) {
            v = 25;
        }
        if (v > 400) {
            v = 400;
        }
        return v;
    }

    /**
     * Scale for the mining scatter gather gun platform (100 = default on-screen size).
     */
    public static int getMiningAnimationGunSizePercent() {
        return clampMiningAnimationSizePercent(PREFS.getInt(KEY_MINING_ANIM_GUN_SIZE_PERCENT, 100));
    }

    public static void setMiningAnimationGunSizePercent(int percent) {
        PREFS.putInt(KEY_MINING_ANIM_GUN_SIZE_PERCENT, clampMiningAnimationSizePercent(percent));
    }

    /**
     * Scale for the mining scatter asteroid line-art and gather animation rock (100 = default on-screen size).
     */
    public static int getMiningAnimationAsteroidSizePercent() {
        return clampMiningAnimationSizePercent(PREFS.getInt(KEY_MINING_ANIM_ASTEROID_SIZE_PERCENT, 100));
    }

    public static void setMiningAnimationAsteroidSizePercent(int percent) {
        PREFS.putInt(KEY_MINING_ANIM_ASTEROID_SIZE_PERCENT, clampMiningAnimationSizePercent(percent));
    }

    public static boolean isMiningAnimationShowLaser() {
        return PREFS.getBoolean(KEY_MINING_ANIM_SHOW_LASER, true);
    }

    public static void setMiningAnimationShowLaser(boolean show) {
        PREFS.putBoolean(KEY_MINING_ANIM_SHOW_LASER, show);
    }

    public static boolean isMiningAnimationShowAsteroid() {
        return PREFS.getBoolean(KEY_MINING_ANIM_SHOW_ASTEROID, true);
    }

    public static void setMiningAnimationShowAsteroid(boolean show) {
        PREFS.putBoolean(KEY_MINING_ANIM_SHOW_ASTEROID, show);
    }

    public static boolean isMiningScatterAsteroidIconsAllPoints() {
        return PREFS.getBoolean(KEY_MINING_SCATTER_ASTEROID_ICONS_ALL_POINTS, true);
    }

    public static void setMiningScatterAsteroidIconsAllPoints(boolean enabled) {
        PREFS.putBoolean(KEY_MINING_SCATTER_ASTEROID_ICONS_ALL_POINTS, enabled);
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

    public static int getUiPrimaryHighlightRgb() {
        // Default rgb(0, 200, 0) — matches {@link EdoUi.User#PRIMARY_HIGHLIGHT}
        return PREFS.getInt(KEY_UI_PRIMARY_HIGHLIGHT_RGB, 0x00C800);
    }

    public static void setUiPrimaryHighlightRgb(int rgb) {
        PREFS.putInt(KEY_UI_PRIMARY_HIGHLIGHT_RGB, rgb & 0x00FFFFFF);
    }

    public static int getUiSecondaryHighlightRgb() {
        // Default rgb(255, 255, 0) — matches {@link EdoUi.User#SECONDARY_HIGHLIGHT}
        return PREFS.getInt(KEY_UI_SECONDARY_HIGHLIGHT_RGB, 0xFFFF00);
    }

    public static void setUiSecondaryHighlightRgb(int rgb) {
        PREFS.putInt(KEY_UI_SECONDARY_HIGHLIGHT_RGB, rgb & 0x00FFFFFF);
    }

    /**
     * Apply persisted theme preferences into {@link EdoUi.User} and refresh derived colors.
     * Safe to call multiple times.
     */
    public static void applyThemeToEdoUi() {
        EdoUi.User.MAIN_TEXT = EdoUi.fromRgbInt(getUiMainTextRgb());
        EdoUi.User.BACKGROUND = EdoUi.fromRgbInt(getUiBackgroundRgb());
        EdoUi.User.SNEAKER = EdoUi.fromRgbInt(getUiSneakerRgb());
        EdoUi.User.PRIMARY_HIGHLIGHT = EdoUi.fromRgbInt(getUiPrimaryHighlightRgb());
        EdoUi.User.SECONDARY_HIGHLIGHT = EdoUi.fromRgbInt(getUiSecondaryHighlightRgb());
        EdoUi.refreshDerivedColors();
        applySwingChromeDefaults();
    }

    /** Dark LAF defaults so new components don't flash white before per-widget styling runs. */
    private static void applySwingChromeDefaults() {
        Color bg = EdoUi.User.BACKGROUND;
        Color fg = EdoUi.User.MAIN_TEXT;
        Color panel = EdoUi.User.PANEL_BG;
        UIManager.put("Panel.background", bg);
        UIManager.put("Viewport.background", bg);
        UIManager.put("ScrollPane.background", bg);
        UIManager.put("Table.background", bg);
        UIManager.put("Table.foreground", fg);
        UIManager.put("Table.selectionBackground", fg);
        UIManager.put("Table.selectionForeground", bg);
        UIManager.put("Label.foreground", fg);
        UIManager.put("CheckBox.foreground", fg);
        UIManager.put("TitledBorder.titleColor", fg);
        UIManager.put("TabbedPane.foreground", fg);
        UIManager.put("TabbedPane.selected", panel);
        UIManager.put("TabbedPane.highlight", panel);
        UIManager.put("TabbedPane.light", panel);
        UIManager.put("TabbedPane.focus", fg);
        UIManager.put("TabbedPane.darkShadow", EdoUi.Internal.separatorLine());
        UIManager.put("MenuBar.background", bg);
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