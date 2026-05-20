package org.dce.ed;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.Timer;
import javax.swing.border.LineBorder;

import org.dce.ed.ui.OverlayBackgroundPanel;
import org.dce.ed.exobiology.ExobiologyData;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.session.EdoSessionPersistence;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.session.FleetCarrierSessionData;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.LiveJournalMonitor;
import org.dce.ed.logreader.CarrierJumpCooldown;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.CarrierJumpRequestEvent;
import org.dce.ed.logreader.event.CarrierLocationEvent;
import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.logreader.event.ScanOrganicEvent;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;
import org.dce.ed.util.FirstBonusHelper;
import org.dce.ed.util.SpanshBodyExobiologyInfo;
import org.dce.ed.util.SpanshLandmark;
import org.dce.ed.util.SpanshLandmarkCache;
import org.dce.ed.util.ValuableBodyExplorationEstimate;
import org.dce.ed.mining.GoogleSheetsBackend;
import org.dce.ed.tts.PollyTtsCached;
import org.dce.ed.tts.TtsSprintf;

import com.google.gson.JsonObject;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;

import org.dce.ed.ui.EdoUi;

public class OverlayFrame extends JFrame implements OverlayUiPreviewHost {

    private static final int DEFAULT_WIDTH = 400;
    private static final int DEFAULT_HEIGHT = 1000;
    private static final int DEFAULT_X = 50;
    private static final int DEFAULT_Y = 50;

    private static final int MIN_WIDTH = 260;
    private static final int MIN_HEIGHT = 200;

    /** Legacy keys (still written on save so older builds / single-slot fallbacks keep working). */
    private static final String PREF_KEY_X = "overlay.x";
    private static final String PREF_KEY_Y = "overlay.y";
    private static final String PREF_KEY_WIDTH = "overlay.width";
    private static final String PREF_KEY_HEIGHT = "overlay.height";

    private static final String PREF_KEY_PT_X = "overlay.pt.x";
    private static final String PREF_KEY_PT_Y = "overlay.pt.y";
    private static final String PREF_KEY_PT_WIDTH = "overlay.pt.width";
    private static final String PREF_KEY_PT_HEIGHT = "overlay.pt.height";

    private static final String PREF_KEY_DECORATED_X = "overlay.decorated.x";
    private static final String PREF_KEY_DECORATED_Y = "overlay.decorated.y";
    private static final String PREF_KEY_DECORATED_WIDTH = "overlay.decorated.width";
    private static final String PREF_KEY_DECORATED_HEIGHT = "overlay.decorated.height";

    private static final String DEFAULT_TITLE_BAR_TITLE = "Elite Dangerous RockHound";

    private static final TtsSprintf CARRIER_JUMP_TTS = new TtsSprintf(new PollyTtsCached());

    private final LineBorder overlayBorder = new LineBorder(
            new java.awt.Color(200, 200, 255, 180),
            1,
            true
    );
    
    private final Preferences prefs = Preferences.userNodeForPackage(OverlayFrame.class);

    private HWND hwnd;
    private boolean passThroughEnabled;

    private volatile CargoMonitor.Snapshot lastCargoSnapshot;

    private final TitleBarPanel titleBar;
    private final JMenuBar passThroughMenuBar;
    private final JLabel passThroughStatusLabel;
    private final JPanel fleetCarrierTimeBadgeHost;
    private final JLabel fleetCarrierTimeLabel;
    /** Same Tools menu as status-bar hammer (Preferences dialog is separate). */
    private final JMenu toolsMenu;
    private final OverlayContentPanel contentPanel;
	private final OverlayBackgroundPanel backgroundPanel;

    /** When non-null+not expired, overrides Low Limpet Warning red status. */
    private volatile String exceptionLeftStatusText;
    private volatile Instant exceptionLeftStatusUntil;

    // Crosshair overlay and timer to show mouse position in pass-through mode
    private final CrosshairOverlay crosshairOverlay = new CrosshairOverlay();
    private final Timer crosshairTimer;
    private static final long PASS_THROUGH_CLOSE_DWELL_MS = 900L;
    private static final long PASS_THROUGH_TOGGLE_DWELL_MS = 700L;
    private static final long PASS_THROUGH_MENU_DWELL_MS = 900L;
    private long passThroughCloseHoverStartMs = -1L;
    private long passThroughToggleHoverStartMs = -1L;
    private long passThroughHammerHoverStartMs = -1L;
    private long passThroughSettingsHoverStartMs = -1L;

    
    private javax.swing.Timer carrierJumpCountdownTimer;
    private Instant carrierJumpDepartureTime;
    private String carrierJumpTargetSystem;
    private Long carrierJumpTargetSystemAddress;

    /** Cooldown phase (5 min) after fleet jump countdown expires. */
    private Instant carrierJumpCooldownEndTime;
    private javax.swing.Timer carrierJumpCooldownTimer;

    /** Suppress duplicate "Jump complete" speech if cooldown is restarted for the same jump. */
    private boolean carrierJumpCompleteSpokenForCurrentJump;

    private long exoCreditsTotal;
    private long geoSurveyCreditsTotal;
    private final Set<Long> countedGeoSurveyBodyKeys = new HashSet<>();

    /** Debounced save of session state (500 ms after last tab change). */
    private final Timer sessionSaveTimer = new Timer(500, e -> saveSessionState());

    /** Available update version for status bar; null when none. */
    private volatile String updateAvailableVersion;

    /** Persistent mining / Google Sheets error line (red) until cleared on success. */
    private volatile String miningSheetsStatusError;

    /** Transient TTS cache-miss hint (warning color); cleared automatically after a delay. */
    private volatile String speechCacheMissBanner;
    private Timer speechCacheMissBannerClearTimer;
    private static final int SPEECH_CACHE_MISS_BANNER_CLEAR_MS = 45_000;

    /**
     * Optional additional right-hand status sink.
     * The OverlayFrame title bar is always updated; this lets the decorated window mirror it.
     */
    private volatile Consumer<String> rightStatusListener;

    public void setRightStatusListener(Consumer<String> listener) {
        this.rightStatusListener = listener;
    }

    private void publishRightStatusText() {
        Consumer<String> extra = rightStatusListener;
        if (extra != null) {
            try {
                extra.accept(buildRightStatusHtml());
            } catch (Exception ignored) {
            }
        }
        refreshPassThroughUnifiedStatus();
    }

    public void refreshRightStatusDisplay() {
        publishRightStatusText();
    }

    public void setUpdateAvailableVersion(String latestVersion) {
        this.updateAvailableVersion = (latestVersion != null && !latestVersion.isBlank()) ? latestVersion : null;
        refreshRightStatusDisplay();
    }

    /**
     * Show a persistent mining / Google Sheets error in the pass-through status bar (red) until cleared.
     */
    public void setMiningSheetsStatusError(String message) {
        miningSheetsStatusError = (message != null && !message.isBlank()) ? message.trim() : null;
        refreshPassThroughUnifiedStatus();
    }

    public void clearMiningSheetsStatusError() {
        miningSheetsStatusError = null;
        refreshPassThroughUnifiedStatus();
    }

    /**
     * Shows a short, non-blocking speech-cache miss message in the right status area (pass-through and decorated).
     * Clears after about 45 seconds; each call restarts the timer.
     */
    public void setSpeechCacheMissBanner(String message) {
        Runnable apply = () -> {
            speechCacheMissBanner = (message != null && !message.isBlank()) ? message.trim() : null;
            if (speechCacheMissBannerClearTimer != null) {
                speechCacheMissBannerClearTimer.stop();
                speechCacheMissBannerClearTimer = null;
            }
            if (speechCacheMissBanner != null) {
                speechCacheMissBannerClearTimer = new Timer(SPEECH_CACHE_MISS_BANNER_CLEAR_MS, e -> {
                    speechCacheMissBanner = null;
                    if (speechCacheMissBannerClearTimer != null) {
                        speechCacheMissBannerClearTimer.stop();
                        speechCacheMissBannerClearTimer = null;
                    }
                    publishRightStatusText();
                });
                speechCacheMissBannerClearTimer.setRepeats(false);
                speechCacheMissBannerClearTimer.start();
            }
            publishRightStatusText();
        };
        if (SwingUtilities.isEventDispatchThread()) {
            apply.run();
        } else {
            SwingUtilities.invokeLater(apply);
        }
    }

    /**
     * Plain right status (no HTML). Prefer {@link #buildRightStatusHtml()} for menu/pass-through labels
     * so fleet jump / cooldown colors apply.
     */
    public String getRightStatusText() {
        String base = getRightStatusMainPlain();
        String hint = getRightStatusUpdateHintPlain();
        if (hint != null) {
            if (base == null || base.isBlank()) {
                return hint;
            }
            return base + " | " + hint;
        }
        return base != null ? base : "";
    }

    /**
     * Plain main line for APIs: includes the time token next to {@code FC jump} / {@code Cooldown} when active.
     */
    private String getRightStatusMainPlain() {
        String time = getFleetCarrierTimeBadgeTextOnly();
        if (time != null) {
            if (carrierJumpDepartureTime != null) {
                String s = "FC jump " + time;
                if (carrierJumpTargetSystem != null && !carrierJumpTargetSystem.isBlank()) {
                    s += " → " + carrierJumpTargetSystem;
                }
                return s;
            }
            if (carrierJumpCooldownEndTime != null) {
                return "Cooldown " + time;
            }
        }
        return formatScienceCredits(exoCreditsTotal, geoSurveyCreditsTotal);
    }

    /**
     * Text shown in the HTML status label (and “is main empty” checks): no time token when the left badge shows it.
     */
    private String getRightStatusMainSuffixPlain() {
        if (carrierJumpDepartureTime != null) {
            String s = "FC jump";
            if (carrierJumpTargetSystem != null && !carrierJumpTargetSystem.isBlank()) {
                s += " → " + carrierJumpTargetSystem;
            }
            return s;
        }
        if (carrierJumpCooldownEndTime != null) {
            return "Cooldown";
        }
        return formatScienceCredits(exoCreditsTotal, geoSurveyCreditsTotal);
    }

    private String getFleetCarrierTimeBadgeTextOnly() {
        if (carrierJumpDepartureTime != null) {
            long seconds = Math.max(0, carrierJumpDepartureTime.getEpochSecond() - Instant.now().getEpochSecond());
            return formatCarrierCountdownToken(seconds);
        }
        if (carrierJumpCooldownEndTime != null) {
            long seconds = Math.max(0, carrierJumpCooldownEndTime.getEpochSecond() - Instant.now().getEpochSecond());
            return formatCarrierCountdownToken(seconds);
        }
        return null;
    }

    private static String formatCarrierCountdownToken(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        if (minutes >= 60) {
            long hours = minutes / 60;
            minutes = minutes % 60;
            return String.format(Locale.US, "T-%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.US, "T-%d:%02d", minutes, secs);
    }

    private String getRightStatusUpdateHintPlain() {
        if (updateAvailableVersion == null || updateAvailableVersion.isBlank()) {
            return null;
        }
        return "New version " + updateAvailableVersion + " available";
    }

    private Color getRightStatusMainForeground() {
        if (carrierJumpDepartureTime != null) {
            return EdoUi.User.ERROR;
        }
        if (carrierJumpCooldownEndTime != null) {
            return EdoUi.User.CORE_BLUE;
        }
        return EdoUi.Internal.MENU_FG_LIGHT;
    }

    private void appendRightStatusInnerHtml(StringBuilder sb) {
        if (carrierJumpDepartureTime != null) {
            appendFcJumpRightStatusHtml(sb);
        } else {
            String main = getRightStatusMainSuffixPlain();
            if (main == null) {
                main = "";
            }
            main = main.trim();
            sb.append("<span style='color:").append(EdoUi.htmlRgb(getRightStatusMainForeground())).append(";'>")
                    .append(EdoUi.escapeHtmlMinimal(main)).append("</span>");
        }
        String hint = getRightStatusUpdateHintPlain();
        if (hint != null) {
            sb.append("<span style='color:").append(EdoUi.htmlRgb(EdoUi.User.SUCCESS)).append(";'> | ")
                    .append(EdoUi.escapeHtmlMinimal(hint)).append("</span>");
        }
        appendSpeechCacheMissBannerHtml(sb);
    }

    private void appendSpeechCacheMissBannerHtml(StringBuilder sb) {
        String s = speechCacheMissBanner;
        if (s == null || s.isBlank()) {
            return;
        }
        sb.append("<span style='color:").append(EdoUi.htmlRgb(EdoUi.Internal.MENU_FG_LIGHT)).append(";'> | </span>");
        sb.append("<span style='color:").append(EdoUi.htmlRgb(EdoUi.User.WARNING)).append(";'>")
                .append(EdoUi.escapeHtmlMinimal(s)).append("</span>");
    }

    /** HTML for fleet-carrier jump line: light, bold, slightly larger arrow between label and target. */
    private void appendFcJumpRightStatusHtml(StringBuilder sb) {
        Color fg = getRightStatusMainForeground();
        String fgHtml = EdoUi.htmlRgb(fg);
        sb.append("<span style='color:").append(fgHtml).append(";'>FC jump</span>");
        String tgt = carrierJumpTargetSystem;
        if (tgt != null && !tgt.isBlank()) {
            String arrowRgb = EdoUi.htmlRgb(EdoUi.Internal.MENU_FG_LIGHT);
            sb.append("<span style='font-weight:700;font-size:1.22em;color:").append(arrowRgb)
                    .append(";'> \u2192 </span>");
            sb.append("<span style='color:").append(fgHtml).append(";'>")
                    .append(EdoUi.escapeHtmlMinimal(tgt.trim())).append("</span>");
        }
    }

    private String buildRightStatusHtml() {
        StringBuilder sb = new StringBuilder("<html>");
        appendRightStatusInnerHtml(sb);
        sb.append("</html>");
        return sb.toString();
    }

    /**
     * Decorated window status bar: {@code rightStatusHtml} is usually {@link #buildRightStatusHtml()} from the
     * pass-through frame listener; optionally append the limpet warning in red.
     */
    static String buildDecoratedMenuStatusHtml(String rightStatusHtml, boolean limpet) {
        String right = rightStatusHtml != null ? rightStatusHtml.trim() : "";
        OverlayFrame f = OverlayFrame.overlayFrame;
        boolean noVisibleRight = (f != null)
                ? f.isRightStatusEffectivelyEmpty()
                : right.isEmpty();
        String limpetSpan = "<span style='color:" + EdoUi.htmlRgb(EdoUi.User.ERROR) + ";'>"
                + (noVisibleRight ? "" : "  |  ") + "Low Limpet Warning!</span>";
        if (!limpet) {
            return right;
        }
        if (right.isEmpty()) {
            return "<html>" + limpetSpan + "</html>";
        }
        if (right.startsWith("<html>") && right.endsWith("</html>")) {
            String inner = right.substring(6, right.length() - 7);
            return "<html>" + inner + limpetSpan + "</html>";
        }
        return "<html>" + EdoUi.escapeHtmlMinimal(right) + limpetSpan + "</html>";
    }

    /**
     * Keeps a status-row fleet time badge in sync with {@link #overlayFrame}'s carrier countdown state
     * (used by {@link DecoratedOverlayDialog}).
     */
    static void updateFleetCarrierTimeBadgeExternal(JPanel host, JLabel label) {
        OverlayFrame f = overlayFrame;
        if (f == null) {
            OverlayMenuStatusBar.applyFleetBadgeCollapsedLayout(host, label);
            return;
        }
        f.applyFleetCarrierTimeBadge(host, label);
    }

    private void applyFleetCarrierTimeBadge(JPanel host, JLabel label) {
        if (host == null || label == null) {
            return;
        }
        label.setFont(OverlayMenuStatusBar.statusRowFontFromPreferences());
        String token = getFleetCarrierTimeBadgeTextOnly();
        if (token == null || token.isBlank()) {
            OverlayMenuStatusBar.applyFleetBadgeCollapsedLayout(host, label);
            return;
        }
        host.setPreferredSize(null);
        host.setMinimumSize(null);
        host.setVisible(true);
        label.setText(token);
        Color border = carrierJumpDepartureTime != null ? EdoUi.User.ERROR : EdoUi.User.CORE_BLUE;
        host.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 1),
                OverlayMenuStatusBar.fleetTimeBadgeInnerPadding()));
        host.setBackground(OverlayMenuStatusBar.opaquePlate(EdoUi.User.BACKGROUND));
        label.setForeground(EdoUi.Internal.MENU_FG_LIGHT);
        host.setVisible(true);
        host.revalidate();
        OverlayMenuStatusBar.cacheFleetBadgeSlotFromPreferred(host);
    }
    
    public static OverlayFrame overlayFrame = null;
    
    public OverlayFrame(OverlayContentPanel contentPanel) {
        super("Elite Dangerous RockHound");

        overlayFrame = this;

        // Need transparency -> undecorated
        setUndecorated(true);
        
        // Check translucency support (informational)
        Window window = this;
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                               .getDefaultScreenDevice();
        if (!gd.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
            System.err.println("WARNING: Per-pixel translucency not supported on this device.");
        }

        // Install crosshair overlay as glass pane (draw-only, no mouse handling)
        setGlassPane(crosshairOverlay);
        crosshairOverlay.setVisible(false); // off until we detect pass-through + hover

        // Poll global mouse position and update crosshair
        crosshairTimer = new Timer(40, e -> updateCrosshair());
        crosshairTimer.start();

        // Transparent window background
        setBackground(new java.awt.Color(0, 0, 0, 0));

	    // Root + content transparent; background is painted by our custom content pane.
	    getRootPane().setOpaque(false);
	    backgroundPanel = new OverlayBackgroundPanel();
	    backgroundPanel.setOpaque(false);
	    backgroundPanel.setBackground(new java.awt.Color(0, 0, 0, 0));
	    setContentPane(backgroundPanel);

        // Subtle border so you can see the edges
        getRootPane().setBorder(overlayBorder);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setAlwaysOnTop(true);
        backgroundPanel.setLayout(new BorderLayout());
        setResizable(true);
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));

        // Save bounds and session state on close; re-apply Win32 click-through once HWND exists.
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                // If we toggled pass-through before first show, applyPassThrough was a no-op (hwnd was null).
                tryAcquireHwnd();
                applyPassThrough(passThroughEnabled);
                SwingUtilities.invokeLater(() -> GoogleSheetsBackend.scheduleFirstLaunchMigration(OverlayFrame.this));
            }

            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                saveSessionState();
                closeOverlay();
            }
        });

        // Custom title bar (draggable, close button) + same menu/status strip as decorated mode
        OverlayMenuStatusBar.Result passThroughMenu =
                OverlayMenuStatusBar.build(this, EliteDangerousOverlay.clientKey, false, null);
        passThroughMenuBar = passThroughMenu.menuBar;
        this.toolsMenu = passThroughMenu.toolsMenu;
        titleBar = new TitleBarPanel(this, "Elite Dangerous RockHound", passThroughMenu.toolsMenu);
        passThroughStatusLabel = passThroughMenu.statusLabel;
        fleetCarrierTimeBadgeHost = passThroughMenu.fleetCarrierTimeBadgeHost;
        fleetCarrierTimeLabel = passThroughMenu.fleetCarrierTimeLabel;

        JPanel northStack = new JPanel(new BorderLayout(0, 0));
        northStack.setOpaque(false);
        northStack.add(titleBar, BorderLayout.NORTH);
        northStack.add(passThroughMenuBar, BorderLayout.SOUTH);
        backgroundPanel.add(northStack, BorderLayout.NORTH);

        // Cross-cutting error reporting hook (used by prospector pipeline).
        ExceptionReporting.setReporter(this::reportExceptionToTitleBar);

        Long cached = loadExoCreditsTotalFromSystemCache();
        exoCreditsTotal = cached != null ? cached.longValue() : 0L;
        Long geoCached = loadGeoSurveyCreditsTotalFromSystemCache();
        geoSurveyCreditsTotal = geoCached != null ? geoCached.longValue() : 0L;
        updateRightStatusDefault();

        // Transparent content panel with tabbed pane
        this.contentPanel = contentPanel;
        add(this.contentPanel, BorderLayout.CENTER);

        applyOverlayBackgroundFromPreferences(false);

        Rectangle passThroughRect = readPassThroughStoredBounds();
        setBounds(passThroughRect.x, passThroughRect.y, passThroughRect.width, passThroughRect.height);

        // Add custom resize handler for edges/corners.
        // IMPORTANT: attach recursively so resizing works even when cursor is over child components.
        int dragThickness = calcBorderDragThicknessPx();
        ResizeHandler resizeHandler = new ResizeHandler(this, TitleBarPanel.TOP_RESIZE_STRIP);
        installResizeHandlerRecursive(getRootPane(), resizeHandler);
        installResizeHandlerRecursive(getContentPane(), resizeHandler);
        
        installLowLimpetStatusUpdater();
        sessionSaveTimer.setRepeats(false);
        // Restore session_json into tabs/SystemState before LiveJournalMonitor listeners merge or save,
        // so SystemState matches SQLite before journal backlog is processed.
        installSessionPersistence();
        // Register before any listener that starts LiveJournalMonitor so CarrierJump is not missed at startup.
        installCarrierJumpTitleUpdater();
        installTabbedPaneJournalListener();
        installExoCreditsTracker();
        installGeoSurveyCreditsTracker();
    }

    /**
     * Surface exceptions in the title-bar left red label.
     * <p>
     * This is intentionally fast/no-throw: exceptions reported here must not break event processing.
     */
    private void reportExceptionToTitleBar(Throwable t, String context) {
        try {
            if (t != null) {
                // Make the failure visible even when the title bar isn't readable (logs).
                t.printStackTrace();
            }
        } catch (Exception ignored) {
        }

        String safeContext = (context == null || context.isBlank())
                ? (t != null ? t.getClass().getSimpleName() : "Exception")
                : context;
        exceptionLeftStatusText = "ERROR: " + safeContext;
        exceptionLeftStatusUntil = Instant.now().plusSeconds(10);
        refreshPassThroughUnifiedStatus();
    }

    private void installSessionPersistence() {
        EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
        if (tabs == null) return;
        Runnable debouncedSave = () -> {
            sessionSaveTimer.stop();
            sessionSaveTimer.start();
        };
        tabs.getRouteTabPanel().setSessionStateChangeCallback(debouncedSave);
        tabs.getFleetCarrierTabPanel().setSessionStateChangeCallback(debouncedSave);
        tabs.getSystemTabPanel().setSessionStateChangeCallback(debouncedSave);
        tabs.getMiningTabPanel().setSessionStateChangeCallback(debouncedSave);
        restoreSessionState();
    }

    /**
     * Persist overlay session immediately (full snapshot). Use after fleet-route / carrier UI changes where
     * the 500 ms debounced save might not run before exit.
     */
    public void flushSessionStateNow() {
        if (SwingUtilities.isEventDispatchThread()) {
            saveSessionState();
        } else {
            SwingUtilities.invokeLater(this::saveSessionState);
        }
    }

    private void saveSessionState() {
        EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
        if (tabs == null) return;
        EdoSessionState state;
        try {
            state = EdoSessionPersistence.load();
        } catch (Exception e) {
            state = new EdoSessionState();
        }
        tabs.getRouteTabPanel().fillSessionState(state);
        tabs.getFleetCarrierTabPanel().fillSessionState(state);
        tabs.getSystemTabPanel().fillSessionState(state);
        tabs.getMiningTabPanel().fillSessionState(state);
        state.setExobiologyCreditsTotalUnsold(exoCreditsTotal);
        state.setGeoSurveyCreditsTotal(geoSurveyCreditsTotal);
        fillCarrierSessionState(state);
        EdoSessionPersistence.save(state);
    }

    private void fillCarrierSessionState(EdoSessionState state) {
        if (state == null) return;
        if (carrierJumpDepartureTime != null) {
            state.setCarrierJumpDepartureTime(carrierJumpDepartureTime.toString());
        } else {
            state.setCarrierJumpDepartureTime(null);
        }
        state.setCarrierJumpTargetSystem(carrierJumpTargetSystem);
        if (carrierJumpCooldownEndTime != null) {
            state.setCarrierJumpCooldownEndTime(carrierJumpCooldownEndTime.toString());
        } else {
            state.setCarrierJumpCooldownEndTime(null);
        }
    }

    private void restoreSessionState() {
        EdoSessionState state = EdoSessionPersistence.load();
        EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
        if (tabs == null) return;
        tabs.getRouteTabPanel().applySessionState(state);
        tabs.getFleetCarrierTabPanel().applySessionState(state);
        tabs.getSystemTabPanel().applySessionState(state);
        tabs.getMiningTabPanel().applySessionState(state);
        applyCarrierSessionState(state);
        if (state.getExobiologyCreditsTotalUnsold() != null) {
            exoCreditsTotal = state.getExobiologyCreditsTotalUnsold().longValue();
        } else {
            exoCreditsTotal = 0L;
        }
        if (state.getGeoSurveyCreditsTotal() != null) {
            geoSurveyCreditsTotal = state.getGeoSurveyCreditsTotal().longValue();
        } else {
            geoSurveyCreditsTotal = 0L;
        }
        updateRightStatusDefault();
    }

    private void applyCarrierSessionState(EdoSessionState state) {
        if (state == null) {
            return;
        }
        try {
            String depStr = state.getCarrierJumpDepartureTime();
            if (depStr != null && !depStr.isBlank()) {
                Instant departure = Instant.parse(depStr);
                if (CarrierJumpCooldown.isDepartureRestorable(departure, Instant.now())) {
                    restoreCarrierJumpCountdownFromSession(departure, state.getCarrierJumpTargetSystem(), state);
                    return;
                }
            }
            String coolStr = state.getCarrierJumpCooldownEndTime();
            if (coolStr != null && !coolStr.isBlank()) {
                Instant end = Instant.parse(coolStr);
                if (end.isAfter(Instant.now())) {
                    resumeCarrierJumpCooldownWithPersistedEnd(end);
                }
            }
        } catch (Exception e) {
            // ignore invalid or old timestamp
        }
    }

    private void restoreCarrierJumpCountdownFromSession(Instant departure, String targetSystem, EdoSessionState state) {
        carrierJumpDepartureTime = departure;
        carrierJumpTargetSystem = targetSystem;
        setTitleBarText("");
        if (carrierJumpCountdownTimer != null) {
            carrierJumpCountdownTimer.stop();
        }
        carrierJumpCountdownTimer = new Timer(500, e -> updateCarrierJumpCountdown());
        carrierJumpCountdownTimer.setRepeats(true);
        carrierJumpCountdownTimer.start();
        updateCarrierJumpCountdown();
        syncFleetCarrierPendingBlinkIfCountdownRestoredWithoutRouteLatch(state);
    }

    /** Restore cooldown UI/timer after restart; does not recompute end from jump time. */
    private void resumeCarrierJumpCooldownWithPersistedEnd(Instant endTime) {
        if (endTime == null || !endTime.isAfter(Instant.now())) {
            return;
        }
        carrierJumpCooldownEndTime = endTime;
        if (carrierJumpCooldownTimer != null) {
            carrierJumpCooldownTimer.stop();
        }
        carrierJumpCooldownTimer = new javax.swing.Timer(500, e -> updateCarrierJumpCooldown());
        carrierJumpCooldownTimer.setRepeats(true);
        carrierJumpCooldownTimer.start();
        publishRightStatusText();
    }

    /**
     * Title-bar countdown can be restored from session while the fleet route snapshot has no
     * {@code pendingJumpLocked*} fields (e.g. older saves). Re-latch the tab blink from the saved target name.
     */
    private void syncFleetCarrierPendingBlinkIfCountdownRestoredWithoutRouteLatch(EdoSessionState state) {
        if (fleetCarrierSessionHasPendingJumpLocked(state != null ? state.getFleetCarrier() : null)) {
            return;
        }
        String tgt = carrierJumpTargetSystem;
        if (tgt == null || tgt.isBlank()) {
            return;
        }
        final String targetTrim = tgt.trim();
        SwingUtilities.invokeLater(() -> {
            EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
            if (tabs != null) {
                tabs.getFleetCarrierTabPanel().startPendingJumpBlink(targetTrim, 0L);
            }
        });
    }

    private static boolean fleetCarrierSessionHasPendingJumpLocked(FleetCarrierSessionData fc) {
        if (fc == null) {
            return false;
        }
        String n = fc.getPendingJumpLockedName();
        if (n != null && !n.isBlank()) {
            return true;
        }
        Long a = fc.getPendingJumpLockedAddress();
        return a != null && a.longValue() != 0L;
    }

    /** Single journal listener that delegates to the current tabbed pane. Prevents duplicate prospector/CSV handling. */
    private void installTabbedPaneJournalListener() {
        try {
            LiveJournalMonitor monitor = LiveJournalMonitor.getInstance(EliteDangerousOverlay.clientKey);
            monitor.addListener(event -> {
                EliteOverlayTabbedPane pane = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
                if (pane != null) {
                    pane.processJournalEvent(event);
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void setTitleBarText(String text) {
        if (titleBar != null) {
            titleBar.setTitleText(text);
        }
    }


    
    
private void installCarrierJumpTitleUpdater() {
    try {
        LiveJournalMonitor monitor = LiveJournalMonitor.getInstance(EliteDangerousOverlay.clientKey);

        monitor.addListener(event -> {
            if (event instanceof CarrierJumpRequestEvent) {
                CarrierJumpRequestEvent e = (CarrierJumpRequestEvent) event;
                if (e.getDepartureTime() != null) {
                    Instant dep = e.getDepartureTime();
                    String sys = e.getSystemName();
                    long addr = e.getSystemAddress();
                    SwingUtilities.invokeLater(() -> startCarrierJumpCountdown(dep, sys, addr));
                }
                return;
            }

            if (event.getType() == EliteEventType.CARRIER_JUMP_CANCELLED) {
                SwingUtilities.invokeLater(this::clearCarrierJumpCountdown);
                return;
            }

            if (event.getType() == EliteEventType.CARRIER_JUMP && event instanceof CarrierJumpEvent) {
                Instant jumpTs = event.getTimestamp();
                SwingUtilities.invokeLater(() -> onCarrierJumpCompleted(jumpTs));
                return;
            }

            if (event instanceof CarrierLocationEvent loc) {
                // Off-carrier owners get CarrierLocation at DepartureTime, not CarrierJump (see journal).
                Instant locTs = event.getTimestamp();
                SwingUtilities.invokeLater(() -> {
                    if (carrierJumpDepartureTime == null) {
                        return;
                    }
                    boolean aboard = false;
                    EliteOverlayTabbedPane tabPane =
                            (contentPanel != null) ? contentPanel.getTabbedPane() : null;
                    SystemTabPanel systemTab =
                            (tabPane != null) ? tabPane.getSystemTabPanel() : null;
                    SystemState st = (systemTab != null) ? systemTab.getState() : null;
                    if (st != null) {
                        aboard = st.isCommanderAboardFleetCarrier();
                    }
                    if (!CarrierJumpCooldown.shouldTreatCarrierLocationAsJumpCompletion(
                            aboard,
                            locTs,
                            carrierJumpDepartureTime,
                            loc.getStarSystem(),
                            loc.getSystemAddress(),
                            carrierJumpTargetSystem,
                            carrierJumpTargetSystemAddress)) {
                        return;
                    }
                    onCarrierJumpCompleted(locTs);
                });
            }
        });
    } catch (Exception ex) {
        ex.printStackTrace();
    }
}

private void onCarrierJumpCompleted(Instant arrivalTime) {
    boolean hadPendingCountdown = carrierJumpDepartureTime != null;
    clearCarrierJumpCountdownStateOnly();
    Instant now = Instant.now();
    if (!CarrierJumpCooldown.shouldStartOrResyncCooldown(
            hadPendingCountdown, arrivalTime, carrierJumpCooldownEndTime, now)) {
        return;
    }
    Instant cooldownStart = arrivalTime != null ? arrivalTime : now;
    startCarrierJumpCooldown(cooldownStart);
}

private void startCarrierJumpCountdown(Instant departureTime, String targetSystem, long targetSystemAddress) {
    carrierJumpDepartureTime = departureTime;
    carrierJumpTargetSystem = targetSystem;
    carrierJumpTargetSystemAddress = targetSystemAddress > 0L ? Long.valueOf(targetSystemAddress) : null;
    carrierJumpCompleteSpokenForCurrentJump = false;

    if (carrierJumpCountdownTimer != null) {
        carrierJumpCountdownTimer.stop();
    }

    setTitleBarText("");
    carrierJumpCountdownTimer = new javax.swing.Timer(500, e -> updateCarrierJumpCountdown());
    carrierJumpCountdownTimer.setRepeats(true);
    carrierJumpCountdownTimer.start();

    updateCarrierJumpCountdown();
    saveSessionState();
}

private void updateCarrierJumpCountdown() {
    if (carrierJumpDepartureTime == null) {
        return;
    }
    publishRightStatusText();
}

/** Clears only the jump countdown state and timer; does not touch cooldown or right status. */
private void clearCarrierJumpCountdownStateOnly() {
    carrierJumpDepartureTime = null;
    carrierJumpTargetSystem = null;
    carrierJumpTargetSystemAddress = null;
    if (carrierJumpCountdownTimer != null) {
        carrierJumpCountdownTimer.stop();
        carrierJumpCountdownTimer = null;
    }
}

private void startCarrierJumpCooldown() {
    startCarrierJumpCooldown(Instant.now());
}

private void startCarrierJumpCooldown(Instant startTime) {
    Instant effectiveStart = startTime != null ? startTime : Instant.now();
    carrierJumpCooldownEndTime = CarrierJumpCooldown.cooldownEndFromJump(effectiveStart);
    if (carrierJumpCooldownTimer != null) {
        carrierJumpCooldownTimer.stop();
    }
    carrierJumpCooldownTimer = new javax.swing.Timer(500, e -> updateCarrierJumpCooldown());
    carrierJumpCooldownTimer.setRepeats(true);
    carrierJumpCooldownTimer.start();
    updateCarrierJumpCooldown();
    if (!carrierJumpCompleteSpokenForCurrentJump) {
        carrierJumpCompleteSpokenForCurrentJump = true;
        CARRIER_JUMP_TTS.speakf("Jump complete");
    }
    saveSessionState();
}

private void updateCarrierJumpCooldown() {
    if (carrierJumpCooldownEndTime == null) {
        return;
    }
    publishRightStatusText();
    if (Instant.now().compareTo(carrierJumpCooldownEndTime) >= 0) {
        if (carrierJumpCooldownTimer != null) {
            carrierJumpCooldownTimer.stop();
            carrierJumpCooldownTimer = null;
        }
        carrierJumpCooldownEndTime = null;
        CARRIER_JUMP_TTS.speakf("Cooldown complete");
        setTitleBarText(DEFAULT_TITLE_BAR_TITLE);
        updateRightStatusDefault();
        saveSessionState();
    }
}

private void clearCarrierJumpCountdown() {
    clearCarrierJumpCountdownStateOnly();
    if (carrierJumpCooldownTimer != null) {
        carrierJumpCooldownTimer.stop();
        carrierJumpCooldownTimer = null;
    }
    carrierJumpCooldownEndTime = null;
    carrierJumpCompleteSpokenForCurrentJump = false;

    setTitleBarText(DEFAULT_TITLE_BAR_TITLE);
    updateRightStatusDefault();
    saveSessionState();
}

private void updateRightStatusDefault() {
    if (carrierJumpDepartureTime != null || carrierJumpCooldownEndTime != null) {
        return;
    }
    publishRightStatusText();
}

private static String formatExoCredits(long credits) {
    if (credits <= 0) {
        return "";
    }

    double d = credits;
    if (credits >= 1_000_000_000L) {
        return String.format(Locale.US, "Bio: %.1fB Cr", d / 1_000_000_000d);
    }
    if (credits >= 1_000_000L) {
        return String.format(Locale.US, "Bio: %.1fM Cr", d / 1_000_000d);
    }
    if (credits >= 1_000L) {
        return String.format(Locale.US, "Bio: %.1fK Cr", d / 1_000d);
    }

    NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
    return "Bio: " + nf.format(credits) + " Cr";
}

private static String formatGeoSurveyCredits(long credits) {
    if (credits <= 0) {
        return "";
    }
    double d = credits;
    if (credits >= 1_000_000_000L) {
        return String.format(Locale.US, "Geo: %.1fB Cr", d / 1_000_000_000d);
    }
    if (credits >= 1_000_000L) {
        return String.format(Locale.US, "Geo: %.1fM Cr", d / 1_000_000d);
    }
    if (credits >= 1_000L) {
        return String.format(Locale.US, "Geo: %.1fK Cr", d / 1_000d);
    }
    NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
    return "Geo: " + nf.format(credits) + " Cr";
}

private static String formatScienceCredits(long exoCredits, long geoCredits) {
    String bio = formatExoCredits(exoCredits);
    String geo = formatGeoSurveyCredits(geoCredits);
    if (!bio.isBlank() && !geo.isBlank()) {
        return bio + " · " + geo;
    }
    if (!bio.isBlank()) {
        return bio;
    }
    return geo;
}

private Long loadExoCreditsTotalFromSystemCache() {
    try {
        return SystemCache.getInstance().getPersistedExobiologyCreditsTotalUnsold();
    } catch (Exception ignored) {
        return null;
    }
}

private Long loadGeoSurveyCreditsTotalFromSystemCache() {
    try {
        EdoSessionState s = SystemCache.getInstance().loadEdoSessionState();
        return s != null ? s.getGeoSurveyCreditsTotal() : null;
    } catch (Exception ignored) {
        return null;
    }
}

private void persistExoCreditsTotal() {
    try {
        SystemCache cache = SystemCache.getInstance();
        EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
        SystemTabPanel systemTab = (tabs != null) ? tabs.getSystemTabPanel() : null;
        SystemState st = (systemTab != null) ? systemTab.getState() : null;
        if (st != null) {
            st.setExobiologyCreditsTotalUnsold(exoCreditsTotal);
            st.setGeoSurveyCreditsTotal(geoSurveyCreditsTotal);
            // Merge from live SystemState so journal fields (e.g. carrier orbit) are not dropped by load+save of credits only.
            cache.mergeCommanderSessionFromReplayedState(st);
        } else {
            EdoSessionState s = cache.loadEdoSessionState();
            s.setExobiologyCreditsTotalUnsold(exoCreditsTotal);
            s.setGeoSurveyCreditsTotal(geoSurveyCreditsTotal);
            cache.saveEdoSessionState(s);
        }
    } catch (Exception ignored) {
        // Best-effort persistence; UI should never break.
    }
}

private void persistGeoSurveyCreditsTotal() {
    try {
        SystemCache cache = SystemCache.getInstance();
        EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
        SystemTabPanel systemTab = (tabs != null) ? tabs.getSystemTabPanel() : null;
        SystemState st = (systemTab != null) ? systemTab.getState() : null;
        if (st != null) {
            st.setGeoSurveyCreditsTotal(geoSurveyCreditsTotal);
            cache.mergeCommanderSessionFromReplayedState(st);
        } else {
            EdoSessionState s = cache.loadEdoSessionState();
            s.setGeoSurveyCreditsTotal(geoSurveyCreditsTotal);
            cache.saveEdoSessionState(s);
        }
    } catch (Exception ignored) {
        // Best-effort persistence; UI should never break.
    }
}

private void installExoCreditsTracker() {
    try {
        LiveJournalMonitor monitor = LiveJournalMonitor.getInstance(EliteDangerousOverlay.clientKey);

        monitor.addListener(event -> {
            if (event.getType() == EliteEventType.SELL_ORGANIC_DATA) {
//            	System.out.println("Sold " + exoCreditsTotal);
                exoCreditsTotal = 0L;
                persistExoCreditsTotal();
                updateRightStatusDefault();
                return;
            }

            if (!(event instanceof ScanOrganicEvent)) {
                return;
            }

            ScanOrganicEvent so = (ScanOrganicEvent) event;

            // In practice, the third sample completion is represented by ScanType=Analyse.
            if (so.getScanType() == null || !"Analyse".equalsIgnoreCase(so.getScanType().trim())) {
                return;
            }

            boolean firstBonus = true;
            try {
                EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
                SystemTabPanel systemTab = (tabs != null) ? tabs.getSystemTabPanel() : null;
                SystemState st = (systemTab != null) ? systemTab.getState() : null;
                if (st != null) {
                    BodyInfo body = st.getBodies().get(so.getBodyId());
                    if (body != null) {
                        if (!Boolean.TRUE.equals(body.getWasFootfalled()) && body.getSpanshLandmarks() == null) {
                            SpanshBodyExobiologyInfo info = SpanshLandmarkCache.getInstance().getOrFetch(body.getStarSystem(), body.getBodyName());
                            if (info != null) {
                                body.setSpanshLandmarks(info.getLandmarks());
                                body.setSpanshExcludeFromExobiology(info.isExcludeFromExobiology());
                            }
                        }
                        firstBonus = FirstBonusHelper.firstBonusApplies(body);
                    }
                }
            } catch (Exception ignored) {
                // best-effort; default to first bonus
            }

            Long payout = ExobiologyData.estimatePayout(so.getGenusLocalised(), so.getSpeciesLocalised(), firstBonus);
            if (payout == null || payout.longValue() <= 0L) {
                return;
            }

            exoCreditsTotal += payout.longValue();
            System.out.println("Earned " + exoCreditsTotal);
            persistExoCreditsTotal();

            updateRightStatusDefault();
        });
    } catch (Exception ex) {
        ex.printStackTrace();
    }
}

private static long geoSurveyBodyKey(ScanEvent e) {
    long addr = e != null ? e.getSystemAddress() : 0L;
    long body = e != null ? (e.getBodyId() & 0xFFFFFFFFL) : 0L;
    return (addr << 32) ^ body;
}

private void installGeoSurveyCreditsTracker() {
    try {
        LiveJournalMonitor monitor = LiveJournalMonitor.getInstance(EliteDangerousOverlay.clientKey);
        monitor.addListener(event -> {
            if (event.getType() == EliteEventType.SELL_EXPLORATION_DATA) {
                geoSurveyCreditsTotal = 0L;
                persistGeoSurveyCreditsTotal();
                updateRightStatusDefault();
                return;
            }

            if (!(event instanceof ScanEvent scan)) {
                return;
            }

            long key = geoSurveyBodyKey(scan);
            synchronized (countedGeoSurveyBodyKeys) {
                if (countedGeoSurveyBodyKeys.contains(Long.valueOf(key))) {
                    return;
                }
            }

            EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
            SystemTabPanel systemTab = (tabs != null) ? tabs.getSystemTabPanel() : null;
            SystemState st = (systemTab != null) ? systemTab.getState() : null;
            if (st == null) {
                return;
            }
            BodyInfo body = st.getBodies().get(scan.getBodyId());
            if (body == null) {
                return;
            }
            long estimate = ValuableBodyExplorationEstimate.resolveCreditsForDisplay(body);
            if (estimate <= 0L) {
                return;
            }

            synchronized (countedGeoSurveyBodyKeys) {
                if (!countedGeoSurveyBodyKeys.add(Long.valueOf(key))) {
                    return;
                }
            }
            geoSurveyCreditsTotal += estimate;
            persistGeoSurveyCreditsTotal();
            updateRightStatusDefault();
        });
    } catch (Exception ex) {
        ex.printStackTrace();
    }
}

private void installLowLimpetStatusUpdater() {
    EliteOverlayTabbedPane tp = (contentPanel == null) ? null : contentPanel.getTabbedPane();
    if (tp != null) {
        tp.addDockedStateListener(docked -> refreshPassThroughUnifiedStatus());
        tp.addLoadoutChangeListener(this::refreshPassThroughUnifiedStatus);
    }

    CargoMonitor.getInstance().addListener(snap -> {
        lastCargoSnapshot = snap;
        refreshPassThroughUnifiedStatus();
    });

    lastCargoSnapshot = CargoMonitor.getInstance().getSnapshot();
    refreshPassThroughUnifiedStatus();
}

/**
 * Same combined status string as {@link DecoratedOverlayDialog#updateStatusLabel()}, plus transient
 * exception text when {@link #reportExceptionToTitleBar} is active.
 */
/**
 * True when the right status area has no meaningful text (no FC line, no bio/geo credits line, no update hint, no speech banner).
 * Used by pass-through status and by {@link #buildDecoratedMenuStatusHtml} so a lone limpet warning does not get a leading {@code |}.
 */
boolean isRightStatusEffectivelyEmpty() {
    String main = getRightStatusMainSuffixPlain();
    if (main != null && !main.trim().isEmpty()) {
        return false;
    }
    if (getRightStatusUpdateHintPlain() != null) {
        return false;
    }
    String speech = speechCacheMissBanner;
    return speech == null || speech.isBlank();
}

private void refreshPassThroughUnifiedStatus() {
    if (passThroughStatusLabel == null) {
        return;
    }

    Runnable r = () -> {
        EliteOverlayTabbedPane tp = (contentPanel == null) ? null : contentPanel.getTabbedPane();
        boolean docked = tp != null && tp.isCurrentlyDocked();
        boolean limpet = EliteOverlayTabbedPane.shouldShowLowLimpetWarning(docked, lastCargoSnapshot);
        Instant until = exceptionLeftStatusUntil;
        String err = exceptionLeftStatusText;
        boolean showErr = err != null && until != null && Instant.now().isBefore(until);

        String miningErr = miningSheetsStatusError;
        boolean showMiningErr = miningErr != null && !miningErr.isBlank();

        boolean rightEmpty = isRightStatusEffectivelyEmpty();

        if (!showErr && !showMiningErr && !limpet && rightEmpty) {
            // Keep the status row visually clear when there is no content.
            // A visible placeholder dash is confusing after totals reset (e.g., after SellOrganicData).
            passThroughStatusLabel.setText("");
            passThroughStatusLabel.setForeground(EdoUi.Internal.MENU_FG_LIGHT);
            passThroughStatusLabel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            passThroughStatusLabel.setVisible(true);
            applyFleetCarrierTimeBadge(fleetCarrierTimeBadgeHost, fleetCarrierTimeLabel);
            return;
        }

        String sep = "<span style='color:" + EdoUi.htmlRgb(EdoUi.Internal.MENU_FG_LIGHT) + ";'>  |  </span>";
        StringBuilder html = new StringBuilder("<html>");
        if (showErr) {
            html.append("<span style='color:").append(EdoUi.htmlRgb(EdoUi.User.ERROR)).append(";'>")
                    .append(EdoUi.escapeHtmlMinimal(err)).append("</span>");
            if (!rightEmpty) {
                html.append(sep);
                appendRightStatusInnerHtml(html);
            }
        } else if (showMiningErr) {
            html.append("<span style='color:").append(EdoUi.htmlRgb(EdoUi.User.ERROR)).append(";'>")
                    .append(EdoUi.escapeHtmlMinimal(miningErr)).append("</span>");
            if (!rightEmpty) {
                html.append(sep);
                appendRightStatusInnerHtml(html);
            }
        } else if (limpet) {
            appendRightStatusInnerHtml(html);
            html.append("<span style='color:").append(EdoUi.htmlRgb(EdoUi.User.ERROR)).append(";'>");
            if (!rightEmpty) {
                html.append("  |  ");
            }
            html.append("Low Limpet Warning!</span>");
        } else {
            appendRightStatusInnerHtml(html);
        }
        html.append("</html>");
        passThroughStatusLabel.setText(html.toString());
        // JLabel ignores this for most HTML content; kept for non-HTML edge cases.
        passThroughStatusLabel.setForeground(EdoUi.Internal.MENU_FG_LIGHT);
        if (getRightStatusUpdateHintPlain() != null && !showErr && !showMiningErr) {
            passThroughStatusLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            passThroughStatusLabel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
        passThroughStatusLabel.setVisible(true);
        applyFleetCarrierTimeBadge(fleetCarrierTimeBadgeHost, fleetCarrierTimeLabel);
    };

    if (SwingUtilities.isEventDispatchThread()) {
        r.run();
    } else {
        SwingUtilities.invokeLater(r);
    }
}


    
    private static int calcBorderDragThicknessPx() {
        double scale = Toolkit.getDefaultToolkit().getScreenResolution() / 96.0;
        int px = (int) Math.round(16 * scale); // was smaller/lower
        return Math.max(px, 16);
    }
    private static void installResizeHandlerRecursive(Component c, ResizeHandler handler) {
        if (c == null) {
            return;
        }

        c.addMouseListener(handler);
        c.addMouseMotionListener(handler);

        if (c instanceof Container) {
            Container cont = (Container) c;
            for (Component child : cont.getComponents()) {
                installResizeHandlerRecursive(child, handler);
            }
        }
    }

    public void showOverlay() {
        setVisible(true);

        tryAcquireHwnd();
        // Respect the configured startup pass-through state instead of forcing non-pass-through.
        applyPassThrough(this.passThroughEnabled);
        System.out.println(
                "Overlay size: " + getWidth() + "x" + getHeight()
                        + " at (" + getX() + "," + getY() + ")"
        );
    }

    /**
     * Obtains the Win32 HWND after the AWT peer exists. Required for {@code WS_EX_TRANSPARENT}.
     * Previously only {@link #showOverlay()} did this; if the app started in decorated mode and
     * later switched to pass-through, {@code hwnd} stayed null and clicks never passed through.
     */
    private boolean tryAcquireHwnd() {
        if (hwnd != null) {
            return true;
        }
        if (!isDisplayable()) {
            return false;
        }
        try {
            Pointer ptr = Native.getWindowPointer(this);
            if (ptr == null) {
                System.err.println("Failed to obtain native window pointer for overlay window.");
                return false;
            }
            hwnd = new HWND(ptr);
            return true;
        } catch (Exception ex) {
            System.err.println("Failed to obtain native window pointer for overlay window.");
            ex.printStackTrace();
            return false;
        }
    }

    public void setPassThroughEnabled(boolean enabled) {
        setPassThroughEnabled(enabled, false);
    }

    /**
     * @param persistUserPreference when true, save {@code enabled} for the next session (title-bar toggle /
     *                              hover dwell). When false, only apply native/UI state (mode switches, startup).
     */
    public void setPassThroughEnabled(boolean enabled, boolean persistUserPreference) {
        if (this.passThroughEnabled == enabled) {
            // Keep title-bar controls in sync even when caller re-applies the same mode.
            titleBar.setPassThrough(this.passThroughEnabled);
            OverlayPreferences.setOverlayMousePassThroughToGame(enabled);
            return;
        }

        this.passThroughEnabled = enabled;
        resetPassThroughCloseHoverState();
        OverlayPreferences.setOverlayMousePassThroughToGame(enabled);
        if (persistUserPreference) {
            OverlayPreferences.putOverlayMousePassThroughToGamePersisted(enabled);
        }
        applyPassThrough(this.passThroughEnabled);
        applyOverlayBackgroundFromPreferences(this.passThroughEnabled);

        titleBar.setPassThrough(this.passThroughEnabled); // hide/show X
        System.out.println("Pass-through " + (this.passThroughEnabled ? "ENABLED" : "DISABLED"));
        repaint();
    }

    public void togglePassThrough() {
        setPassThroughEnabled(!passThroughEnabled, true);
    }

    public boolean isPassThroughEnabled() {
        return passThroughEnabled;
    }

    public void applyUiFontPreferences() {
        OverlayPreferences.clearUiFontLivePreview();
        contentPanel.applyUiFontPreferences();
        if (passThroughStatusLabel != null && fleetCarrierTimeLabel != null) {
            java.awt.Font rowFont = OverlayMenuStatusBar.statusRowFontFromPreferences();
            passThroughStatusLabel.setFont(rowFont);
            fleetCarrierTimeLabel.setFont(rowFont);
            OverlayMenuStatusBar.clearFleetBadgeSlotCache(fleetCarrierTimeBadgeHost);
            applyFleetCarrierTimeBadge(fleetCarrierTimeBadgeHost, fleetCarrierTimeLabel);
        }
        revalidate();
        repaint();
    }


    @Override
    public void applyThemeFromPreferences() {
        OverlayPreferences.applyThemeToEdoUi();

        UIManager.put("TitlePane.background", EdoUi.User.BACKGROUND);
        UIManager.put("TitlePane.foreground", EdoUi.User.MAIN_TEXT);

        if (passThroughMenuBar != null) {
            OverlayMenuStatusBar.refreshMenuBarTheme(passThroughMenuBar);
            applyFleetCarrierTimeBadge(fleetCarrierTimeBadgeHost, fleetCarrierTimeLabel);
        }

        if (contentPanel != null) {
            contentPanel.rebuildTabbedPane();
            installSessionPersistence();
        }

        repaint();
    }

    @Override
    public void refreshSystemTabFromSavedPreferences() {
        EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
        if (tabs != null) {
            tabs.getSystemTabPanel().refreshFromSavedOverlayPreferences();
        }
    }

    public void applyUiFontPreview(java.awt.Font font) {
        if (font == null) {
            return;
        }
        OverlayPreferences.setUiFontLivePreview(font);
        contentPanel.applyUiFont(font);
        if (passThroughStatusLabel != null && fleetCarrierTimeLabel != null) {
            java.awt.Font rowFont = OverlayMenuStatusBar.statusRowFontFromPreferences();
            passThroughStatusLabel.setFont(rowFont);
            fleetCarrierTimeLabel.setFont(rowFont);
            OverlayMenuStatusBar.clearFleetBadgeSlotCache(fleetCarrierTimeBadgeHost);
            applyFleetCarrierTimeBadge(fleetCarrierTimeBadgeHost, fleetCarrierTimeLabel);
        }
        revalidate();
        repaint();
    }

    @Override
    public void revertUiFontLivePreview(java.awt.Font savedFont) {
        OverlayPreferences.clearUiFontLivePreview();
        if (savedFont != null) {
            contentPanel.applyUiFont(savedFont);
        }
        if (savedFont != null && passThroughStatusLabel != null && fleetCarrierTimeLabel != null) {
            java.awt.Font rowFont = OverlayMenuStatusBar.statusRowFontFromPreferences();
            passThroughStatusLabel.setFont(rowFont);
            fleetCarrierTimeLabel.setFont(rowFont);
            OverlayMenuStatusBar.clearFleetBadgeSlotCache(fleetCarrierTimeBadgeHost);
            applyFleetCarrierTimeBadge(fleetCarrierTimeBadgeHost, fleetCarrierTimeLabel);
        }
        revalidate();
        repaint();
    }

    /**
     * Legacy wrapper kept so older call sites still compile.
     *
     * New behavior is driven by two settings:
     *  - background RGB
     *  - transparency percent (0..100)
     */
    public void applyOverlayTransparency(boolean transparent) {
        OverlayPreferences.setNormalTransparencyPercent(transparent ? 100 : 0);
        applyOverlayBackgroundFromPreferences(passThroughEnabled);
    }

    public void applyOverlayBackgroundFromPreferences(boolean passThroughMode) {
        // Colors tab "Background" drives EdoUi.User.BACKGROUND; use the same RGB for the painted overlay fill.
        int rgb = OverlayPreferences.getUiBackgroundRgb();

        int pct = passThroughMode
                ? OverlayPreferences.getPassThroughTransparencyPercent()
                : OverlayPreferences.getNormalTransparencyPercent();

        applyOverlayBackgroundPreview(passThroughMode, rgb, pct);
    }

    /**
     * Used by PreferencesDialog for live preview.
     */
    public void applyOverlayBackgroundPreview(boolean passThroughMode, int rgb, int transparencyPercent) {
        int pct = Math.max(0, Math.min(100, transparencyPercent));
        int alpha = (int) Math.round(255.0 * (1.0 - (pct / 100.0)));

        java.awt.Color base = new java.awt.Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        java.awt.Color bg = new java.awt.Color(base.getRed(), base.getGreen(), base.getBlue(), Math.max(0, Math.min(255, alpha)));

        // Frame background must stay fully transparent for per-pixel alpha.
        setBackground(new java.awt.Color(0, 0, 0, 0));

        if (backgroundPanel != null) {
            backgroundPanel.setPaintColor(bg);
        }

        boolean treatAsTransparent = pct > 0;
        if (contentPanel != null) {
            contentPanel.applyOverlayBackground(bg, treatAsTransparent);
        }

        if (passThroughMenuBar != null) {
            OverlayMenuStatusBar.refreshMenuBarTheme(passThroughMenuBar);
            applyFleetCarrierTimeBadge(fleetCarrierTimeBadgeHost, fleetCarrierTimeLabel);
        }

        revalidate();
        repaint();
    }


    private void applyPassThrough(boolean enable) {
        if (!tryAcquireHwnd()) {
            return;
        }

        int exStyle = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE);

        if (enable) {
            exStyle = exStyle | WinUser.WS_EX_LAYERED | WinUser.WS_EX_TRANSPARENT;
        } else {
            exStyle = exStyle | WinUser.WS_EX_LAYERED;
            exStyle = exStyle & ~WinUser.WS_EX_TRANSPARENT;
        }

        User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, exStyle);

        if (enable) {
            getRootPane().setBorder(null);
        } else {
            getRootPane().setBorder(javax.swing.BorderFactory.createEmptyBorder());
        }

        revalidate();
    }
    public void prepareForShow(boolean passThroughMode) {
        // Make sure we have the right background color/alpha set BEFORE first paint
        applyOverlayBackgroundFromPreferences(passThroughMode);
        if (titleBar != null) {
            titleBar.setPassThrough(passThroughMode);
        }

        // Defensive: avoid any default opaque background painting
        setBackground(new java.awt.Color(0, 0, 0, 0));
        if (getContentPane() != null) {
            getContentPane().setBackground(new java.awt.Color(0, 0, 0, 0));
        }
        if (getRootPane() != null) {
            getRootPane().setOpaque(false);
        }
    }

    /**
     * Union of every attached display in screen coordinates (same space as {@link Window#setBounds}).
     * {@link Toolkit#getScreenSize()} is primary-monitor only and was forcing restored windows onto that display.
     */
    private static Rectangle getVirtualScreenBoundsUnion() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] devices = ge.getScreenDevices();
        if (devices == null || devices.length == 0) {
            Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
            return new Rectangle(0, 0, d.width, d.height);
        }
        Rectangle union = null;
        for (GraphicsDevice gd : devices) {
            Rectangle b = gd.getDefaultConfiguration().getBounds();
            union = (union == null) ? new Rectangle(b) : union.union(b);
        }
        if (union == null) {
            Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
            return new Rectangle(0, 0, d.width, d.height);
        }
        return union;
    }

    /** Fits {@code x,y,w,h} into the multi-monitor virtual desktop (not just the primary display). */
    private static Rectangle clampFrameBoundsToVirtualScreens(int x, int y, int w, int h) {
        Rectangle union = getVirtualScreenBoundsUnion();
        w = Math.min(w, union.width);
        h = Math.min(h, union.height);
        w = Math.max(MIN_WIDTH, w);
        h = Math.max(MIN_HEIGHT, h);
        w = Math.min(w, union.width);
        h = Math.min(h, union.height);
        x = Math.max(union.x, Math.min(x, union.x + union.width - w));
        y = Math.max(union.y, Math.min(y, union.y + union.height - h));
        return new Rectangle(x, y, w, h);
    }

    private static Rectangle readLegacyStoredBounds(Preferences p) {
        int x = p.getInt(PREF_KEY_X, DEFAULT_X);
        int y = p.getInt(PREF_KEY_Y, DEFAULT_Y);
        int w = p.getInt(PREF_KEY_WIDTH, DEFAULT_WIDTH);
        int h = p.getInt(PREF_KEY_HEIGHT, DEFAULT_HEIGHT);
        Rectangle r = clampFrameBoundsToVirtualScreens(x, y, w, h);
        System.out.println("Read legacy bounds " + r.x + " " + r.y + " " + r.width + " " + r.height);
        return r;
    }

    /**
     * Undecorated pass-through {@link OverlayFrame} bounds for the next session.
     */
    public static Rectangle readPassThroughStoredBounds() {
        Preferences p = Preferences.userNodeForPackage(OverlayFrame.class);
        int w = p.getInt(PREF_KEY_PT_WIDTH, -1);
        if (w >= MIN_WIDTH) {
            int x = p.getInt(PREF_KEY_PT_X, DEFAULT_X);
            int y = p.getInt(PREF_KEY_PT_Y, DEFAULT_Y);
            int h = p.getInt(PREF_KEY_PT_HEIGHT, DEFAULT_HEIGHT);
            Rectangle r = clampFrameBoundsToVirtualScreens(x, y, w, h);
            System.out.println("Read pass-through bounds " + r.x + " " + r.y + " " + r.width + " " + r.height);
            return r;
        }
        return readLegacyStoredBounds(p);
    }

    /**
     * Normal decorated {@link org.dce.ed.DecoratedOverlayDialog} bounds for the next session.
     */
    public static Rectangle readDecoratedStoredBounds() {
        Preferences p = Preferences.userNodeForPackage(OverlayFrame.class);
        int w = p.getInt(PREF_KEY_DECORATED_WIDTH, -1);
        if (w >= MIN_WIDTH) {
            int x = p.getInt(PREF_KEY_DECORATED_X, DEFAULT_X);
            int y = p.getInt(PREF_KEY_DECORATED_Y, DEFAULT_Y);
            int h = p.getInt(PREF_KEY_DECORATED_HEIGHT, DEFAULT_HEIGHT);
            Rectangle r = clampFrameBoundsToVirtualScreens(x, y, w, h);
            System.out.println("Read decorated bounds " + r.x + " " + r.y + " " + r.width + " " + r.height);
            return r;
        }
        return readLegacyStoredBounds(p);
    }

    private static void putBoundsGroup(Preferences p, String kx, String ky, String kw, String kh, Rectangle r) {
        p.putInt(kx, r.x);
        p.putInt(ky, r.y);
        p.putInt(kw, r.width);
        p.putInt(kh, r.height);
    }

    private static void putLegacyBounds(Preferences p, Rectangle r) {
        putBoundsGroup(p, PREF_KEY_X, PREF_KEY_Y, PREF_KEY_WIDTH, PREF_KEY_HEIGHT, r);
    }

    private static void flushOverlayPrefs(Preferences p) {
        try {
            p.flush();
        } catch (Exception ignored) {
        }
    }

    /** Persists pass-through outer bounds and mirrors to legacy {@code overlay.*} keys. */
    public static void persistPassThroughBoundsRectangle(Rectangle r) {
        if (r == null) {
            return;
        }
        Rectangle c = clampFrameBoundsToVirtualScreens(r.x, r.y, r.width, r.height);
        Preferences p = Preferences.userNodeForPackage(OverlayFrame.class);
        putBoundsGroup(p, PREF_KEY_PT_X, PREF_KEY_PT_Y, PREF_KEY_PT_WIDTH, PREF_KEY_PT_HEIGHT, c);
        putLegacyBounds(p, c);
        flushOverlayPrefs(p);
        System.out.println("Saved pass-through bounds: " + c.x + " " + c.y + " " + c.width + " " + c.height);
    }

    /** Persists decorated-window outer bounds and mirrors to legacy {@code overlay.*} keys. */
    public static void persistDecoratedBoundsRectangle(Rectangle r) {
        if (r == null) {
            return;
        }
        Rectangle c = clampFrameBoundsToVirtualScreens(r.x, r.y, r.width, r.height);
        Preferences p = Preferences.userNodeForPackage(OverlayFrame.class);
        putBoundsGroup(p, PREF_KEY_DECORATED_X, PREF_KEY_DECORATED_Y, PREF_KEY_DECORATED_WIDTH, PREF_KEY_DECORATED_HEIGHT, c);
        putLegacyBounds(p, c);
        flushOverlayPrefs(p);
        System.out.println("Saved decorated bounds: " + c.x + " " + c.y + " " + c.width + " " + c.height);
    }

    /** Outer bounds using screen location when the peer exists (multi-monitor / DPI friendly). */
    public static Rectangle windowOuterRectangle(Window w) {
        if (w == null) {
            return new Rectangle(DEFAULT_X, DEFAULT_Y, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        }
        try {
            Point loc = w.getLocationOnScreen();
            Rectangle b = w.getBounds();
            return new Rectangle(loc.x, loc.y, b.width, b.height);
        } catch (IllegalComponentStateException e) {
            return new Rectangle(w.getX(), w.getY(), w.getWidth(), w.getHeight());
        }
    }

    public void loadBoundsFromPreferences(
            Preferences prefs,
            String keyX,
            String keyY,
            String keyWidth,
            String keyHeight
    ) {
        int x = prefs.getInt(keyX, DEFAULT_X);
        int y = prefs.getInt(keyY, DEFAULT_Y);
        int w = prefs.getInt(keyWidth, DEFAULT_WIDTH);
        int h = prefs.getInt(keyHeight, DEFAULT_HEIGHT);
        Rectangle r = clampFrameBoundsToVirtualScreens(x, y, w, h);
        System.out.println("Read " + r.x + " " + r.y + " " + r.width + " " + r.height);
        setBounds(r.x, r.y, r.width, r.height);
    }

    public void saveBoundsToPreferences(
            String keyX,
            String keyY,
            String keyWidth,
            String keyHeight
    ) {
        prefs.putInt(keyX, getX());
        prefs.putInt(keyY, getY());
        prefs.putInt(keyWidth, getWidth());
        prefs.putInt(keyHeight, getHeight());
        flushOverlayPrefs(prefs);
        System.out.println("Saved : " + getX() + " " + getY() + " " + getWidth() + " " + getHeight());
    }

    /**
     * Saves {@code w}'s outer bounds for decorated mode (call from {@link org.dce.ed.DecoratedOverlayDialog} on exit).
     */
    public static void persistOuterBounds(Window w) {
        persistDecoratedBoundsRectangle(windowOuterRectangle(w));
    }

    /**
     * Centralized close method: saves bounds then exits.
     */
    /** Persist window bounds and session before {@link org.dce.ed.util.OverlayAppRestart}. */
    public void prepareForApplicationRestart() {
        persistPassThroughBoundsRectangle(windowOuterRectangle(this));
        flushSessionStateNow();
    }

    public void closeOverlay() {
        persistPassThroughBoundsRectangle(windowOuterRectangle(this));
        dispose();
        System.exit(0);
    }

    /**
     * Mouse handler that provides resize handles on edges and corners
     * for the undecorated frame.
     */
    private static class ResizeHandler extends MouseAdapter {

        private final int borderDragThickness;

        private final OverlayFrame frame;
        private int dragCursor = Cursor.DEFAULT_CURSOR;
        private boolean dragging = false;

        // Mouse position at press time (screen coords)
        private int dragOffsetX;
        private int dragOffsetY;

        // Frame bounds at press time
        private int dragWidth;
        private int dragHeight;
        private int dragStartX;
        private int dragStartY;

        ResizeHandler(OverlayFrame frame, int borderDragThickness) {
            this.frame = frame;
            this.borderDragThickness = borderDragThickness;
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            if (dragging) {
                return;
            }
            int cursor = calcCursor(e);
            frame.setCursor(Cursor.getPredefinedCursor(cursor));
        }

        @Override
        public void mouseExited(MouseEvent e) {
            if (!dragging) {
                frame.setCursor(Cursor.getDefaultCursor());
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            dragCursor = calcCursor(e);
            if (dragCursor != Cursor.DEFAULT_CURSOR && SwingUtilities.isLeftMouseButton(e)) {
                dragging = true;
                dragOffsetX = e.getXOnScreen();
                dragOffsetY = e.getYOnScreen();
                dragWidth = frame.getWidth();
                dragHeight = frame.getHeight();
                dragStartX = frame.getX();
                dragStartY = frame.getY();
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            dragging = false;
            frame.setCursor(Cursor.getDefaultCursor());
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (!dragging) {
                return;
            }

            int dx = e.getXOnScreen() - dragOffsetX;
            int dy = e.getYOnScreen() - dragOffsetY;

            // Always base on the ORIGINAL frame position & size
            int newX = dragStartX;
            int newY = dragStartY;
            int newW = dragWidth;
            int newH = dragHeight;

            switch (dragCursor) {
                case Cursor.E_RESIZE_CURSOR:
                    newW = dragWidth + dx;
                    break;
                case Cursor.S_RESIZE_CURSOR:
                    newH = dragHeight + dy;
                    break;
                case Cursor.SE_RESIZE_CURSOR:
                    newW = dragWidth + dx;
                    newH = dragHeight + dy;
                    break;
                case Cursor.W_RESIZE_CURSOR:
                    newX = dragStartX + dx;
                    newW = dragWidth - dx;
                    break;
                case Cursor.N_RESIZE_CURSOR:
                    newY = dragStartY + dy;
                    newH = dragHeight - dy;
                    break;
                case Cursor.NW_RESIZE_CURSOR:
                    newX = dragStartX + dx;
                    newW = dragWidth - dx;
                    newY = dragStartY + dy;
                    newH = dragHeight - dy;
                    break;
                case Cursor.NE_RESIZE_CURSOR:
                    newY = dragStartY + dy;
                    newH = dragHeight - dy;
                    newW = dragWidth + dx;
                    break;
                case Cursor.SW_RESIZE_CURSOR:
                    newX = dragStartX + dx;
                    newW = dragWidth - dx;
                    newH = dragHeight + dy;
                    break;
                default:
                    break;
            }

            // Enforce minimum size
            if (newW < frame.getMinimumSize().width) {
                int diff = frame.getMinimumSize().width - newW;
                if (dragCursor == Cursor.W_RESIZE_CURSOR ||
                    dragCursor == Cursor.NW_RESIZE_CURSOR ||
                    dragCursor == Cursor.SW_RESIZE_CURSOR) {
                    newX -= diff;
                }
                newW = frame.getMinimumSize().width;
            }

            if (newH < frame.getMinimumSize().height) {
                int diff = frame.getMinimumSize().height - newH;
                if (dragCursor == Cursor.N_RESIZE_CURSOR ||
                    dragCursor == Cursor.NE_RESIZE_CURSOR ||
                    dragCursor == Cursor.NW_RESIZE_CURSOR) {
                    newY -= diff;
                }
                newH = frame.getMinimumSize().height;
            }

            frame.setBounds(newX, newY, newW, newH);
        }
        private int calcCursor(MouseEvent e) {
            // IMPORTANT: e.getX()/getY() are relative to the component that fired the event.
            // Convert to root-pane coordinates so hit-testing matches the actual window edges.
            Component src = (Component) e.getSource();
            Point p = SwingUtilities.convertPoint(src, e.getPoint(), frame.getRootPane());

            int x = p.x;
            int y = p.y;

            int w = frame.getRootPane().getWidth();
            int h = frame.getRootPane().getHeight();

            boolean left = x < borderDragThickness;
            boolean right = x >= w - borderDragThickness;
            boolean top = y < borderDragThickness;
            boolean bottom = y >= h - borderDragThickness;

            if (left && top) {
                return Cursor.NW_RESIZE_CURSOR;
            } else if (left && bottom) {
                return Cursor.SW_RESIZE_CURSOR;
            } else if (right && top) {
                return Cursor.NE_RESIZE_CURSOR;
            } else if (right && bottom) {
                return Cursor.SE_RESIZE_CURSOR;
            } else if (left) {
                return Cursor.W_RESIZE_CURSOR;
            } else if (right) {
                return Cursor.E_RESIZE_CURSOR;
            } else if (top) {
                return Cursor.N_RESIZE_CURSOR;
            } else if (bottom) {
                return Cursor.S_RESIZE_CURSOR;
            } else {
                return Cursor.DEFAULT_CURSOR;
            }
        }

    }

    private void updateCrosshair() {
        // If window isn't showing, don't bother
        if (!isShowing()) {
            crosshairOverlay.setVisible(false);
            resetPassThroughCloseHoverState();
            return;
        }

        // Only show crosshair when pass-through is enabled
//        if (!passThroughEnabled) {
//            crosshairOverlay.setVisible(false);
//            return;
//        }

        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi == null) {
            crosshairOverlay.setVisible(false);
            resetPassThroughCloseHoverState();
            return;
        }

        Point mouseOnScreen = pi.getLocation();
        Point frameOnScreen;
        try {
            frameOnScreen = getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            crosshairOverlay.setVisible(false);
            resetPassThroughCloseHoverState();
            return;
        }

        int relX = mouseOnScreen.x - frameOnScreen.x;
        int relY = mouseOnScreen.y - frameOnScreen.y;

        // Inside the overlay bounds?
        if (relX >= 0 && relY >= 0 && relX < getWidth() && relY < getHeight()) {
            crosshairOverlay.setCrosshairPoint(new Point(relX, relY));
            if (!crosshairOverlay.isVisible()) {
                crosshairOverlay.setVisible(true);
            }
            updatePassThroughHoverClose(mouseOnScreen);
        } else {
            crosshairOverlay.setCrosshairPoint(null);
            crosshairOverlay.setVisible(false);
            resetPassThroughCloseHoverState();
        }

    }

    private void updatePassThroughHoverClose(Point mouseOnScreen) {
        if (!passThroughEnabled || titleBar == null || mouseOnScreen == null) {
            resetPassThroughCloseHoverState();
            return;
        }
        java.awt.Rectangle closeRect = titleBar.getCloseButtonScreenBounds();
        java.awt.Rectangle toggleRect = titleBar.getToggleButtonScreenBounds();
        java.awt.Rectangle hammerRect = titleBar.getHammerButtonScreenBounds();
        java.awt.Rectangle settingsRect = titleBar.getSettingsButtonScreenBounds();
        if (closeRect == null || toggleRect == null || hammerRect == null || settingsRect == null) {
            resetPassThroughCloseHoverState();
            return;
        }

        boolean hClose = closeRect.contains(mouseOnScreen);
        boolean hToggle = toggleRect.contains(mouseOnScreen);
        boolean hHammer = hammerRect.contains(mouseOnScreen);
        boolean hSettings = settingsRect.contains(mouseOnScreen);

        titleBar.setCloseHoverProgrammatic(hClose);
        titleBar.setToggleHoverProgrammatic(hToggle);
        titleBar.setHammerHoverProgrammatic(hHammer);
        titleBar.setSettingsHoverProgrammatic(hSettings);

        long now = System.currentTimeMillis();

        if (hClose) {
            passThroughToggleHoverStartMs = -1L;
            passThroughHammerHoverStartMs = -1L;
            passThroughSettingsHoverStartMs = -1L;
            if (passThroughCloseHoverStartMs < 0L) {
                passThroughCloseHoverStartMs = now;
            } else if (now - passThroughCloseHoverStartMs >= PASS_THROUGH_CLOSE_DWELL_MS) {
                closeOverlay();
            }
            return;
        }
        passThroughCloseHoverStartMs = -1L;

        if (hToggle) {
            passThroughHammerHoverStartMs = -1L;
            passThroughSettingsHoverStartMs = -1L;
            if (passThroughToggleHoverStartMs < 0L) {
                passThroughToggleHoverStartMs = now;
            } else if (now - passThroughToggleHoverStartMs >= PASS_THROUGH_TOGGLE_DWELL_MS) {
                setPassThroughEnabled(false, true);
            }
            return;
        }
        passThroughToggleHoverStartMs = -1L;

        if (hHammer) {
            passThroughSettingsHoverStartMs = -1L;
            if (passThroughHammerHoverStartMs < 0L) {
                passThroughHammerHoverStartMs = now;
            } else if (now - passThroughHammerHoverStartMs >= PASS_THROUGH_MENU_DWELL_MS) {
                titleBar.showToolsMenuUnderHammer(toolsMenu);
                passThroughHammerHoverStartMs = -1L;
            }
            return;
        }
        passThroughHammerHoverStartMs = -1L;

        if (hSettings) {
            if (passThroughSettingsHoverStartMs < 0L) {
                passThroughSettingsHoverStartMs = now;
            } else if (now - passThroughSettingsHoverStartMs >= PASS_THROUGH_MENU_DWELL_MS) {
                PreferencesDialog.show(this, EliteDangerousOverlay.clientKey);
                passThroughSettingsHoverStartMs = -1L;
            }
            return;
        }
        passThroughSettingsHoverStartMs = -1L;
    }

    private void resetPassThroughCloseHoverState() {
        passThroughCloseHoverStartMs = -1L;
        passThroughToggleHoverStartMs = -1L;
        passThroughHammerHoverStartMs = -1L;
        passThroughSettingsHoverStartMs = -1L;
        if (titleBar != null) {
            titleBar.setCloseHoverProgrammatic(false);
            titleBar.setToggleHoverProgrammatic(false);
            titleBar.setHammerHoverProgrammatic(false);
            titleBar.setSettingsHoverProgrammatic(false);
        }
    }

    private static class CrosshairOverlay extends JComponent {

        private Point crosshairPoint;

        CrosshairOverlay() {
            setOpaque(false);
        }

        void setCrosshairPoint(Point p) {
            this.crosshairPoint = p;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (crosshairPoint == null) {
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

                int x = crosshairPoint.x;
                int y = crosshairPoint.y;

                int arm = 10;

                // Grey shadow (slightly thicker stroke behind the main crosshair)
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(EdoUi.withAlpha(new Color(200, 200, 200), 220));
                g2.drawLine(x - arm, y, x + arm, y);
                g2.drawLine(x, y - arm, x, y + arm);

                // ED-style orange with some transparency
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(EdoUi.Internal.MAIN_TEXT_ALPHA_200);
                g2.drawLine(x - arm, y, x + arm, y);
                g2.drawLine(x, y - arm, x, y + arm);

            } finally {
                g2.dispose();
            }
        }

        @Override
        public boolean contains(int x, int y) {
            // Critical: don't intercept mouse events.
            return false;
        }
    }
    
    private long getLong(JsonObject obj, String field) {
        return getLong(obj, field, 0L);
    }

    private long getLong(JsonObject obj, String field, long defaultValue) {
        return obj.has(field) && !obj.get(field).isJsonNull()
                ? obj.get(field).getAsLong()
                : defaultValue;
    }

    private int getInt(JsonObject obj, String field) {
        return getInt(obj, field, 0);
    }

    private int getInt(JsonObject obj, String field, int defaultValue) {
        return obj.has(field) && !obj.get(field).isJsonNull()
                ? obj.get(field).getAsInt()
                : defaultValue;
    }

}
