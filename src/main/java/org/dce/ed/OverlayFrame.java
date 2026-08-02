package org.dce.ed;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.AWTEvent;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.PaintEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.HashSet;
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
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.LineBorder;

import org.dce.ed.ui.OverlayBackgroundPanel;
import org.dce.ed.ui.PassThroughTooltipSupport;
import org.dce.ed.exobiology.ExobiologyData;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.session.EdoSessionPersistence;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.session.FleetCarrierSessionData;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.LiveJournalMonitor;
import org.dce.ed.logreader.CarrierJumpCooldown;
import org.dce.ed.logreader.OwnedFleetCarrierTracker;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.CarrierJumpRequestEvent;
import org.dce.ed.logreader.event.CarrierLocationEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.logreader.event.ScanOrganicEvent;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;
import org.dce.ed.util.ExplorationBodyCredits;
import org.dce.ed.util.FirstBonusHelper;
import org.dce.ed.util.SpanshBodyExobiologyInfo;
import org.dce.ed.util.SpanshLandmarkCache;
import org.dce.ed.util.ValuableBodyExplorationEstimate;
import org.dce.ed.mining.GoogleSheetsBackend;
import org.dce.ed.tts.PollyTtsCached;
import org.dce.ed.tts.TtsSprintf;

import com.google.gson.JsonObject;
import org.dce.ed.util.WindowsNativeMousePassThrough;

import org.dce.ed.exec.ExecBindingsConfig;
import org.dce.ed.exec.ExecTriggerService;
import org.dce.ed.exec.placeholder.ExecPlaceholderContext;
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

    private static final String PREF_KEY_DECORATED_MAXIMIZED = "overlay.decorated.maximized";
    private static final String PREF_KEY_PT_MAXIMIZED = "overlay.pt.maximized";

    /** Tolerance (px) when comparing a window's outer bounds to a monitor's screen bounds. */
    private static final int FULL_SCREEN_BOUNDS_TOLERANCE_PX = 8;

    private static final String DEFAULT_TITLE_BAR_TITLE = "Elite Dangerous RockHound";

    private static final TtsSprintf CARRIER_JUMP_TTS = new TtsSprintf(new PollyTtsCached());

    private final LineBorder overlayBorder = new LineBorder(
            new java.awt.Color(200, 200, 255, 180),
            1,
            true
    );
    
    private final Preferences prefs = Preferences.userNodeForPackage(OverlayFrame.class);

    /** Last HWND used for native pass-through; refreshed on each apply (peer can be recreated). */
    private MouseInteractionMode mouseInteractionMode = MouseInteractionMode.NORMAL;


    /**
     * AWT can clear {@link com.sun.jna.platform.win32.WinUser#WS_EX_TRANSPARENT} after layered repaints or
     * {@code setBounds}; re-apply while mouse pass-through is on.
     */
    private Timer mousePassThroughNativeStyleTimer;
    /** Coalesces EDT re-applies triggered by high-frequency overlay repaints. */
    private boolean nativePassThroughReapplyScheduled;
    private AWTEventListener passThroughPaintGuard;

    private volatile CargoMonitor.Snapshot lastCargoSnapshot;

    private final TitleBarPanel titleBar;
    private final JMenuBar passThroughMenuBar;
    private final JLabel passThroughStatusLabel;
    private final JPanel fleetCarrierTimeBadgeHost;
    private final JLabel fleetCarrierTimeLabel;
    private final JPanel fleetCarrierAnnouncementHost;
    private final JLabel fleetCarrierAnnouncementLabel;
    /** Same Tools menu as status-bar hammer (Preferences dialog is separate). */
    private final JMenu toolsMenu;
    private final OverlayContentPanel contentPanel;
	private final OverlayBackgroundPanel backgroundPanel;
    private final ExecTriggerService execTriggerService = new ExecTriggerService();
    private final ExecPlaceholderContext execPlaceholderContext = new ExecPlaceholderContext();
    private final CombatSessionTracker combatSessionTracker = new CombatSessionTracker();
    private boolean combatSessionPersistenceListenerInstalled;

    /** When non-null+not expired, overrides Low Limpet Warning red status. */
    private volatile String exceptionLeftStatusText;
    private volatile Instant exceptionLeftStatusUntil;

    // Crosshair on glass pane (draw-only, on top of all UI; {@link CrosshairOverlay#contains} is false)
    private final CrosshairOverlay crosshairOverlay = new CrosshairOverlay();
    private final Timer crosshairTimer;
    private static final int CROSSHAIR_POLL_MS = 8;
    private static final long PASS_THROUGH_CLOSE_DWELL_MS = 900L;
    private static final long PASS_THROUGH_TOGGLE_DWELL_MS = 700L;
    private static final long PASS_THROUGH_MENU_DWELL_MS = 900L;
    private long passThroughCloseHoverStartMs = -1L;
    private long passThroughMinimizeHoverStartMs = -1L;
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
    private final BountyCreditsTracker bountyCreditsTracker = new BountyCreditsTracker();
    private final Set<Long> countedGeoSurveyBodyKeys = new HashSet<>();

    /** Debounced save of session state (500 ms after last tab change). */
    private final Timer sessionSaveTimer = new Timer(500, e -> saveSessionState());

    /** Available update version for status bar; null when none. */
    private volatile String updateAvailableVersion;

    /** Persistent mining / Google Sheets error line (red) until cleared on success. */
    private volatile String miningSheetsStatusError;

    /** Transient Exec / Control Panel run status; cleared automatically after a delay. */
    private volatile String execOverlayStatusMessage;
    private volatile boolean execOverlayStatusError;
    private Timer execOverlayStatusClearTimer;
    private static final int EXEC_OVERLAY_STATUS_CLEAR_MS = 20_000;
    private static final int EXEC_OVERLAY_ERROR_CLEAR_MS = 35_000;
    private static final int EXEC_OVERLAY_STATUS_MAX_LEN = 72;

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
     * Shows a transient Exec / Control Panel status line in the overlay status bar (red for errors).
     * Clears after about 20 seconds; each call restarts the timer.
     */
    public void setExecOverlayStatus(String message) {
        setTransientOverlayStatus(message, isExecStatusError(message));
    }

    public void warnUnplannedEngineeringCraft() {
        Toolkit.getDefaultToolkit().beep();
        setTransientOverlayStatus("Engineered a Module with no goal", true);
    }

    private void setTransientOverlayStatus(String message, boolean error) {
        Runnable apply = () -> {
            if (message == null || message.isBlank()) {
                execOverlayStatusMessage = null;
                execOverlayStatusError = false;
            } else {
                execOverlayStatusMessage = truncateExecOverlayStatus(message.trim());
                execOverlayStatusError = error;
            }
            restartExecOverlayStatusClearTimer();
            refreshPassThroughUnifiedStatus();
            refreshDecoratedStatusFromExecOverlay();
        };
        if (SwingUtilities.isEventDispatchThread()) {
            apply.run();
        } else {
            SwingUtilities.invokeLater(apply);
        }
    }

    private void restartExecOverlayStatusClearTimer() {
        if (execOverlayStatusClearTimer != null) {
            execOverlayStatusClearTimer.stop();
            execOverlayStatusClearTimer = null;
        }
        if (execOverlayStatusMessage == null) {
            return;
        }
        execOverlayStatusClearTimer = new Timer(
                execOverlayStatusError ? EXEC_OVERLAY_ERROR_CLEAR_MS : EXEC_OVERLAY_STATUS_CLEAR_MS, e -> {
            execOverlayStatusMessage = null;
            execOverlayStatusError = false;
            if (execOverlayStatusClearTimer != null) {
                execOverlayStatusClearTimer.stop();
                execOverlayStatusClearTimer = null;
            }
            refreshPassThroughUnifiedStatus();
            refreshDecoratedStatusFromExecOverlay();
        });
        execOverlayStatusClearTimer.setRepeats(false);
        execOverlayStatusClearTimer.start();
    }

    private void refreshDecoratedStatusFromExecOverlay() {
        Consumer<String> extra = rightStatusListener;
        if (extra != null) {
            try {
                extra.accept(buildRightStatusHtml());
            } catch (Exception ignored) {
            }
        }
    }

    boolean hasExecOverlayStatus() {
        return execOverlayStatusMessage != null && !execOverlayStatusMessage.isBlank();
    }

    boolean isExecOverlayStatusError() {
        return execOverlayStatusError;
    }

    String buildExecOverlayStatusHtmlFragment() {
        String msg = execOverlayStatusMessage;
        if (msg == null || msg.isBlank()) {
            return "";
        }
        String color = execOverlayStatusError
                ? EdoUi.htmlRgb(EdoUi.User.ERROR)
                : EdoUi.htmlRgb(EdoUi.Internal.MENU_FG_LIGHT);
        return "<span style='color:" + color + ";'>" + EdoUi.escapeHtmlMinimal(msg) + "</span>";
    }

    static boolean isExecStatusError(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.trim();
        return m.startsWith("Failed:")
                || m.startsWith("Save failed")
                || m.contains("not ready")
                || m.contains("no longer configured")
                || m.contains("not configured")
                || m.startsWith("Select a row")
                || m.startsWith("Action not configured");
    }

    private static String truncateExecOverlayStatus(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }
        String trimmed = message.trim();
        if (trimmed.length() <= EXEC_OVERLAY_STATUS_MAX_LEN) {
            return trimmed;
        }
        return trimmed.substring(0, EXEC_OVERLAY_STATUS_MAX_LEN - 1).trim() + "…";
    }

    static String mergeExecIntoDecoratedStatus(String execFragment, String decoratedHtml) {
        if (execFragment == null || execFragment.isEmpty()) {
            return decoratedHtml != null ? decoratedHtml : "";
        }
        String sep = "<span style='color:" + EdoUi.htmlRgb(EdoUi.Internal.MENU_FG_LIGHT) + ";'>  |  </span>";
        if (decoratedHtml == null || decoratedHtml.isEmpty()) {
            return "<html>" + execFragment + "</html>";
        }
        if (decoratedHtml.startsWith("<html>") && decoratedHtml.endsWith("</html>")) {
            String inner = decoratedHtml.substring(6, decoratedHtml.length() - 7);
            if (inner.isEmpty()) {
                return "<html>" + execFragment + "</html>";
            }
            return "<html>" + execFragment + sep + inner + "</html>";
        }
        return "<html>" + execFragment + sep + EdoUi.escapeHtmlMinimal(decoratedHtml) + "</html>";
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
                String tgt = resolveCarrierJumpTitleTarget();
                if (tgt != null && !tgt.isBlank()) {
                    s += " → " + tgt;
                }
                return s;
            }
            if (carrierJumpCooldownEndTime != null) {
                return "Cooldown " + time;
            }
        }
        return formatScienceCredits(exoCreditsTotal, bountyCreditsTracker.getUnclaimedTotal());
    }

    /**
     * Marquee / HTML status main line: Bio / Bounties only. Geo survey totals are tracked but hidden
     * until estimates are more accurate. Fleet jump / cooldown text lives in the static announcement
     * label so it does not scroll with overflow messages.
     */
    private String getRightStatusMainSuffixPlain() {
        return formatScienceCredits(exoCreditsTotal, bountyCreditsTracker.getUnclaimedTotal());
    }

    private boolean hasFleetCarrierStatusAnnouncement() {
        return carrierJumpDepartureTime != null || carrierJumpCooldownEndTime != null;
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

    private void appendRightStatusInnerHtml(StringBuilder sb) {
        StringBuilder body = new StringBuilder();
        Long targetedBounty = BountyScanTracker.getInstance().getTargetedBountyInSight();
        boolean hasTargetedBounty = targetedBounty != null && targetedBounty.longValue() > 0L;
        if (hasTargetedBounty) {
            appendTargetedBountyInSightHtml(body, targetedBounty.longValue());
        }

        boolean hasMainLine = hasRightStatusMainSuffixContent();
        if (hasTargetedBounty && hasMainLine) {
            body.append("<span style='color:").append(EdoUi.htmlRgb(EdoUi.Internal.MENU_FG_LIGHT)).append(";'> · </span>");
        }

        String main = getRightStatusMainSuffixPlain();
        if (main == null) {
            main = "";
        }
        main = main.trim();
        if (!main.isEmpty()) {
            body.append("<span style='color:").append(EdoUi.htmlRgb(EdoUi.Internal.MENU_FG_LIGHT)).append(";'>")
                    .append(EdoUi.escapeHtmlMinimal(main)).append("</span>");
        }
        String hint = getRightStatusUpdateHintPlain();
        if (hint != null) {
            body.append("<span style='color:").append(EdoUi.htmlRgb(EdoUi.User.SUCCESS)).append(";'> | ")
                    .append(EdoUi.escapeHtmlMinimal(hint)).append("</span>");
        }
        appendSpeechCacheMissBannerHtml(body);

        if (body.length() == 0) {
            return;
        }
        sb.append(body);
    }

    private boolean hasRightStatusMainSuffixContent() {
        String main = getRightStatusMainSuffixPlain();
        return main != null && !main.trim().isEmpty();
    }

    private static void appendTargetedBountyInSightHtml(StringBuilder sb, long credits) {
        sb.append("<span style='color:").append(EdoUi.htmlRgb(EdoUi.User.ERROR)).append(";'>")
                .append(EdoUi.escapeHtmlMinimal(formatTargetedBountyInSightLabel(credits))).append("</span>");
    }

    static String formatTargetedBountyInSightLabel(long credits) {
        return "Target bounty: " + ExplorationBodyCredits.formatAbbreviatedCredits(credits);
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

    private Color getFleetCarrierAnnouncementForeground() {
        if (carrierJumpDepartureTime != null) {
            return EdoUi.User.ERROR;
        }
        if (carrierJumpCooldownEndTime != null) {
            return EdoUi.User.CORE_BLUE;
        }
        return EdoUi.Internal.MENU_FG_LIGHT;
    }

    /** HTML for fleet-carrier jump line: light, bold, slightly larger arrow between label and target. */
    private void appendFcJumpRightStatusHtml(StringBuilder sb) {
        Color fg = getFleetCarrierAnnouncementForeground();
        String fgHtml = EdoUi.htmlRgb(fg);
        sb.append("<span style='color:").append(fgHtml).append(";'>FC jump</span>");
        String tgt = resolveCarrierJumpTitleTarget();
        if (tgt != null && !tgt.isBlank()) {
            String arrowRgb = EdoUi.htmlRgb(EdoUi.Internal.MENU_FG_LIGHT);
            sb.append("<span style='font-weight:700;font-size:1.22em;color:").append(arrowRgb)
                    .append(";'> \u2192 </span>");
            sb.append("<span style='color:").append(fgHtml).append(";'>")
                    .append(EdoUi.escapeHtmlMinimal(tgt.trim())).append("</span>");
        }
    }

    private void appendFleetCarrierAnnouncementInnerHtml(StringBuilder sb, boolean trailingSeparator) {
        if (carrierJumpDepartureTime != null) {
            appendFcJumpRightStatusHtml(sb);
        } else if (carrierJumpCooldownEndTime != null) {
            sb.append("<span style='color:").append(EdoUi.htmlRgb(EdoUi.User.CORE_BLUE)).append(";'>Cooldown</span>");
        } else {
            return;
        }
        // Trailing separator stays with the static FC text so marquee content can read as "| Bounties…".
        if (trailingSeparator) {
            sb.append("<span style='color:").append(EdoUi.htmlRgb(EdoUi.Internal.MENU_FG_LIGHT)).append(";'> | </span>");
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
    static String buildDecoratedMenuStatusHtml(String rightStatusHtml, boolean limpet, boolean fighterPilot) {
        String right = rightStatusHtml != null ? rightStatusHtml.trim() : "";
        OverlayFrame f = OverlayFrame.overlayFrame;
        // Fleet jump / cooldown text is in a separate static label; only marquee HTML affects this separator.
        boolean noVisibleRight = (f != null)
                ? f.isMarqueeRightStatusEmpty()
                : isRightStatusHtmlVisuallyEmpty(right);
        String warningSpan = buildStatusWarningSpan(noVisibleRight, limpet, fighterPilot);
        if (warningSpan.isEmpty()) {
            return right;
        }
        if (noVisibleRight || right.isEmpty()) {
            return "<html>" + warningSpan + "</html>";
        }
        if (right.startsWith("<html>") && right.endsWith("</html>")) {
            String inner = right.substring(6, right.length() - 7);
            return "<html>" + inner + warningSpan + "</html>";
        }
        return "<html>" + EdoUi.escapeHtmlMinimal(right) + warningSpan + "</html>";
    }

    static String buildStatusWarningSpan(boolean noVisibleRight, boolean limpet, boolean fighterPilot) {
        String sep = noVisibleRight ? "" : "  |  ";
        String color = EdoUi.htmlRgb(EdoUi.User.ERROR);
        StringBuilder sb = new StringBuilder();
        if (limpet) {
            sb.append("<span style='color:").append(color).append(";'>")
                    .append(sep).append("Low Limpet Warning!</span>");
            sep = "  |  ";
        }
        if (fighterPilot) {
            sb.append("<span style='color:").append(color).append(";'>")
                    .append(sep).append(NpcCrewTracker.FIGHTER_PILOT_STATUS_WARNING).append("</span>");
        }
        return sb.toString();
    }

    static boolean isRightStatusHtmlVisuallyEmpty(String rightStatusHtml) {
        if (rightStatusHtml == null || rightStatusHtml.isBlank()) {
            return true;
        }
        String right = rightStatusHtml.trim();
        if (!right.startsWith("<html>") || !right.endsWith("</html>")) {
            return false;
        }
        String inner = right.substring(6, right.length() - 7).trim();
        if (inner.isEmpty()) {
            return true;
        }
        return inner.replaceAll("<[^>]+>", "").trim().isEmpty();
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

    /**
     * Keeps the static FC jump / cooldown announcement in sync (used by {@link DecoratedOverlayDialog}).
     */
    static void updateFleetCarrierAnnouncementExternal(JPanel host, JLabel label) {
        OverlayFrame f = overlayFrame;
        if (f == null) {
            OverlayMenuStatusBar.applyFleetAnnouncementCollapsedLayout(host, label);
            return;
        }
        f.applyFleetCarrierAnnouncement(host, label, !f.isMarqueeRightStatusEmpty());
    }

    /**
     * Same as {@link #updateFleetCarrierAnnouncementExternal(JPanel, JLabel)} but controls whether a trailing
     * {@code | } is shown (e.g. when limpet / exec text will appear in the marquee even with no credits).
     */
    static void updateFleetCarrierAnnouncementExternal(JPanel host, JLabel label, boolean trailingSeparator) {
        OverlayFrame f = overlayFrame;
        if (f == null) {
            OverlayMenuStatusBar.applyFleetAnnouncementCollapsedLayout(host, label);
            return;
        }
        f.applyFleetCarrierAnnouncement(host, label, trailingSeparator);
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

    private void applyFleetCarrierAnnouncement(JPanel host, JLabel label, boolean trailingSeparator) {
        if (host == null || label == null) {
            return;
        }
        label.setFont(OverlayMenuStatusBar.statusRowFontFromPreferences());
        if (!hasFleetCarrierStatusAnnouncement()) {
            OverlayMenuStatusBar.applyFleetAnnouncementCollapsedLayout(host, label);
            return;
        }
        StringBuilder sb = new StringBuilder("<html>");
        appendFleetCarrierAnnouncementInnerHtml(sb, trailingSeparator);
        sb.append("</html>");
        host.setPreferredSize(null);
        host.setMinimumSize(null);
        host.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        host.setVisible(true);
        label.setText(sb.toString());
        label.setForeground(getFleetCarrierAnnouncementForeground());
        host.revalidate();
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

        setGlassPane(crosshairOverlay);
        crosshairOverlay.setVisible(false);

        // Poll global mouse position and update crosshair (~120 Hz, direct tracking like RoboHound game message)
        crosshairTimer = new Timer(CROSSHAIR_POLL_MS, e -> updateCrosshair());
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
        setAlwaysOnTop(org.dce.ed.util.EliteWindowFocus.isEliteForeground());
        backgroundPanel.setLayout(new BorderLayout());
        setResizable(true);
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));

        // Re-apply WS_EX_TRANSPARENT whenever the frame is shown, moved, or resized (windowOpened fires only once).
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                reapplyNativeMousePassThroughIfEnabled();
                updateMousePassThroughNativeStyleTimer();
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                stopMousePassThroughNativeStyleTimer();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                reapplyNativeMousePassThroughIfEnabled();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                reapplyNativeMousePassThroughIfEnabled();
            }
        });

        installPassThroughPaintGuard();
        installPassThroughHierarchyGuard();

        // Save bounds and session state on close; re-apply Win32 click-through once HWND exists.
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                // If we toggled pass-through before first show, applyPassThrough was a no-op (hwnd was null).
                reapplyNativeMousePassThroughIfEnabled();
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
        org.dce.ed.ui.EdoWindowIconify.watch(this);
        passThroughStatusLabel = passThroughMenu.statusLabel;
        fleetCarrierTimeBadgeHost = passThroughMenu.fleetCarrierTimeBadgeHost;
        fleetCarrierTimeLabel = passThroughMenu.fleetCarrierTimeLabel;
        fleetCarrierAnnouncementHost = passThroughMenu.fleetCarrierAnnouncementHost;
        fleetCarrierAnnouncementLabel = passThroughMenu.fleetCarrierAnnouncementLabel;

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
        Long bountyCached = loadBountyCreditsTotalFromSystemCache();
        bountyCreditsTracker.setUnclaimedTotal(bountyCached != null ? bountyCached.longValue() : 0L);
        updateRightStatusDefault();

        // Transparent content panel with tabbed pane
        this.contentPanel = contentPanel;
        if (this.contentPanel != null) {
            this.contentPanel.setOnTabbedPaneRebuilt(this::onTabbedPaneRebuilt);
        }
        add(this.contentPanel, BorderLayout.CENTER);

        // Transparency % follows mouse pass-through, not which window host is active.
        applyOverlayBackgroundFromPreferences(mouseInteractionMode.isPassThroughLike());

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
        installExecTriggers();
        installExoCreditsTracker();
        installGeoSurveyCreditsTracker();
        installBountyCreditsTracker();
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
            // Combat ShipTargeted floods call this from the journal thread; Timer must be EDT.
            if (SwingUtilities.isEventDispatchThread()) {
                sessionSaveTimer.stop();
                sessionSaveTimer.start();
            } else {
                SwingUtilities.invokeLater(() -> {
                    sessionSaveTimer.stop();
                    sessionSaveTimer.start();
                });
            }
        };
        tabs.getRouteTabPanel().setSessionStateChangeCallback(debouncedSave);
        tabs.getFleetCarrierTabPanel().setSessionStateChangeCallback(debouncedSave);
        tabs.getSystemTabPanel().setSessionStateChangeCallback(debouncedSave);
        tabs.getMiningTabPanel().setSessionStateChangeCallback(debouncedSave);
        tabs.getMissionsTabPanel().setSessionStateChangeCallback(debouncedSave);
        tabs.getEngineeringTabPanel().setSessionStateChangeCallback(debouncedSave);
        tabs.getEngineeringTabPanel().setUnplannedCraftWarningCallback(this::warnUnplannedEngineeringCraft);
        tabs.getBiologyTabPanel().setSessionStateChangeCallback(debouncedSave);
        NpcCrewTracker.getInstance().setSessionStateChangeCallback(debouncedSave);
        CombatTargetTracker.getInstance().setSessionStateChangeCallback(debouncedSave);
        restoreSessionState();
        if (!combatSessionPersistenceListenerInstalled) {
            combatSessionTracker.addListener(debouncedSave);
            combatSessionPersistenceListenerInstalled = true;
        }
        tabs.getMissionsTabPanel().hydrateTrackerFromJournalIfNeeded(EliteDangerousOverlay.clientKey);
        tabs.getEngineeringTabPanel().hydrateFromJournalIfNeeded(EliteDangerousOverlay.clientKey);
    }

    /** Rebind session + mission board after {@link OverlayContentPanel#rebuildTabbedPane()}. */
    public void onTabbedPaneRebuilt() {
        installSessionPersistence();
        installExecTriggers();
        if (titleBar != null) {
            titleBar.setMouseInteractionMode(mouseInteractionMode);
        }
        reapplyNativeMousePassThroughIfEnabled();
    }

    /** After Tools full rescan when the user stays in-process: reload craft progress into goals. */
    public void refreshEngineeringProgressAfterRescan() {
        EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
        if (tabs != null) {
            tabs.getEngineeringTabPanel().refreshGoalProgressFromJournal();
        }
    }

    /**
     * Reload missions from {@code session_json} after a full journal rescan.
     * Rescan writes rebuilt massacre progress to SQLite; the live tracker still has pre-rescan
     * state, and {@link #prepareForApplicationRestart()} / exit would otherwise overwrite the DB
     * with those stale zeros.
     */
    public void reloadMissionsFromSessionAfterRescan() {
        EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
        if (tabs == null) {
            return;
        }
        try {
            EdoSessionState state = EdoSessionPersistence.load();
            tabs.getMissionsTabPanel().applySessionState(state);
            tabs.getMissionsTabPanel().hydrateTrackerFromJournalIfNeeded(EliteDangerousOverlay.clientKey);
        } catch (Exception ex) {
            System.err.println("OverlayFrame: reloadMissionsFromSessionAfterRescan failed: " + ex.getMessage());
        }
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
        tabs.getMissionsTabPanel().fillSessionState(state);
        tabs.getEngineeringTabPanel().fillSessionState(state);
        tabs.getBiologyTabPanel().fillSessionState(state);
        state.setExobiologyCreditsTotalUnsold(exoCreditsTotal);
        state.setGeoSurveyCreditsTotal(geoSurveyCreditsTotal);
        state.setBountyCreditsTotalUnclaimed(Long.valueOf(bountyCreditsTracker.getUnclaimedTotal()));
        NpcCrewTracker.getInstance().fillSessionState(state);
        CombatTargetTracker.getInstance().fillSessionState(state);
        combatSessionTracker.fillSessionState(state);
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
        tabs.getMissionsTabPanel().applySessionState(state);
        tabs.getEngineeringTabPanel().applySessionState(state);
        tabs.getBiologyTabPanel().applySessionState(state);
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
        if (state.getBountyCreditsTotalUnclaimed() != null) {
            bountyCreditsTracker.setUnclaimedTotal(state.getBountyCreditsTotalUnclaimed().longValue());
        } else {
            bountyCreditsTracker.setUnclaimedTotal(0L);
        }
        NpcCrewTracker.getInstance().applySessionState(state);
        CombatTargetTracker.getInstance().applySessionState(state);
        combatSessionTracker.applySessionState(state);
        LoadoutEvent loadout = EliteOverlayTabbedPane.getLatestLoadout();
        if (loadout != null) {
            NpcCrewTracker.getInstance().onLoadout(loadout);
        }
        updateRightStatusDefault();
    }

    private void applyCarrierSessionState(EdoSessionState state) {
        if (state == null) {
            return;
        }
        try {
            Instant now = Instant.now();
            String depStr = state.getCarrierJumpDepartureTime();
            if (depStr != null && !depStr.isBlank()) {
                Instant departure = Instant.parse(depStr);
                if (CarrierJumpCooldown.isDepartureRestorable(departure, now)) {
                    restoreCarrierJumpCountdownFromSession(departure, state.getCarrierJumpTargetSystem(), state);
                    return;
                }
                // Departure is too old to show as an in-flight countdown (avoids stuck "FC jump 0:00"
                // after restart when journal completion will not be replayed). Prefer a still-active
                // saved cooldown; otherwise synthesize cooldown from departure if it has not ended.
                String coolStr = state.getCarrierJumpCooldownEndTime();
                if (coolStr != null && !coolStr.isBlank()) {
                    Instant end = Instant.parse(coolStr);
                    if (end.isAfter(now)) {
                        resumeCarrierJumpCooldownWithPersistedEnd(end);
                        return;
                    }
                }
                Instant synthesizedEnd = CarrierJumpCooldown.cooldownEndFromJump(departure, true);
                if (synthesizedEnd != null && synthesizedEnd.isAfter(now)) {
                    resumeCarrierJumpCooldownWithPersistedEnd(synthesizedEnd);
                    return;
                }
            }
            String coolStr = state.getCarrierJumpCooldownEndTime();
            if (coolStr != null && !coolStr.isBlank()) {
                Instant end = Instant.parse(coolStr);
                if (end.isAfter(now)) {
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
                combatSessionTracker.applyJournalEvent(event);
                EliteOverlayTabbedPane pane = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
                if (pane != null) {
                    pane.processJournalEvent(event);
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void installExecTriggers() {
        EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
        if (tabs == null) {
            return;
        }
        execTriggerService.setPlaceholderContext(execPlaceholderContext);
        execTriggerService.setConfigSupplier(() -> execTriggerService.store().load());
        wireExecPlaceholderContext(tabs);
        tabs.setExecPlaceholderContext(execPlaceholderContext);
        tabs.wireExecTriggerService(execTriggerService);
        execTriggerService.setCarrierSystemSupplier(() -> {
            OwnedFleetCarrierTracker tracker = tabs.getOwnedFleetCarrierTracker();
            return tracker != null ? tracker.getOwnedSystemName() : null;
        });
        execTriggerService.setStatusListener(msg -> {
            if (msg != null) {
                setExecOverlayStatus(msg);
            }
        });
    }

    private void wireExecPlaceholderContext(EliteOverlayTabbedPane tabs) {
        execPlaceholderContext.setCarrierJumpTargetSupplier(() -> carrierJumpTargetSystem);
        execPlaceholderContext.setShipRouteSessionSupplier(() -> tabs.getRouteTabPanel().getRouteSession());
        execPlaceholderContext.setFleetRouteSessionSupplier(() -> tabs.getFleetCarrierTabPanel().getRouteSession());
        execPlaceholderContext.setSystemStateSupplier(() -> tabs.getSystemTabPanel().getState());
        execPlaceholderContext.setTargetBodyNameSupplier(() -> tabs.getSystemTabPanel().getTargetBodyNameForExec());
        execPlaceholderContext.setNearBodyNameSupplier(() -> tabs.getSystemTabPanel().getNearBodyNameForExec());
        execPlaceholderContext.setExobiologyCreditsSupplier(() -> Long.valueOf(exoCreditsTotal));
        execPlaceholderContext.setGeoSurveyCreditsSupplier(() -> Long.valueOf(geoSurveyCreditsTotal));
        execPlaceholderContext.setBountyCreditsSupplier(() -> Long.valueOf(bountyCreditsTracker.getUnclaimedTotal()));
        tabs.setCombatUnclaimedBountyCreditsSupplier(() -> bountyCreditsTracker.getUnclaimedTotal());
        tabs.setCombatSessionTracker(combatSessionTracker);
        execPlaceholderContext.setExecConfigSupplier(() -> {
            ExecBindingsConfig config = execTriggerService.store().load();
            return config;
        });
        execPlaceholderContext.setOwnedCarrierTrackerSupplier(tabs::getOwnedFleetCarrierTracker);
        execPlaceholderContext.setCarrierFuelTrackerSupplier(execTriggerService::fuelTracker);
        execTriggerService.bootstrapFuelFromJournal(tabs.getOwnedFleetCarrierTracker());
    }

    public ExecPlaceholderContext getExecPlaceholderContext() {
        return execPlaceholderContext;
    }

    public ExecTriggerService getExecTriggerService() {
        return execTriggerService;
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
                if (!acceptOwnedCarrierJumpRequest(e)) {
                    return;
                }
                if (e.getDepartureTime() != null) {
                    Instant dep = e.getDepartureTime();
                    String sys = e.getSystemName();
                    long addr = e.getSystemAddress();
                    SwingUtilities.invokeLater(() -> startCarrierJumpCountdown(dep, sys, addr));
                }
                return;
            }

            if (event.getType() == EliteEventType.CARRIER_JUMP_CANCELLED) {
                Instant cancelTs = event.getTimestamp();
                SwingUtilities.invokeLater(() -> onCarrierJumpCancelled(cancelTs, event));
                return;
            }

            if (event.getType() == EliteEventType.CARRIER_JUMP && event instanceof CarrierJumpEvent) {
                Instant jumpTs = event.getTimestamp();
                SwingUtilities.invokeLater(() -> onCarrierJumpCompleted(jumpTs, false));
                return;
            }

            if (event instanceof CarrierLocationEvent loc) {
                // Off-carrier owners get CarrierLocation at DepartureTime, not CarrierJump (see journal).
                Instant locTs = event.getTimestamp();
                SwingUtilities.invokeLater(() -> {
                    if (carrierJumpDepartureTime == null) {
                        return;
                    }
                    boolean aboard = isCommanderAboardFleetCarrier();
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
                    onCarrierJumpCompleted(locTs, true);
                });
            }
        });
    } catch (Exception ex) {
        ex.printStackTrace();
    }
}

private boolean acceptOwnedCarrierJumpRequest(CarrierJumpRequestEvent req) {
    if (req == null) {
        return false;
    }
    EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
    if (tabs == null) {
        return req.getCarrierId() == 0L;
    }
    org.dce.ed.logreader.OwnedFleetCarrierTracker tracker = tabs.getOwnedFleetCarrierTracker();
    if (tracker == null) {
        return req.getCarrierId() == 0L;
    }
    if (tracker.hasOwnedCarrierId()) {
        return tracker.isOwnedCarrierId(req.getCarrierId());
    }
    return req.getCarrierId() == 0L;
}

private void onCarrierJumpCompleted(Instant arrivalTime, boolean offCarrierCompletion) {
    onCarrierJumpCompleted(arrivalTime, offCarrierCompletion, true);
}

private void onCarrierJumpCompleted(Instant arrivalTime, boolean offCarrierCompletion, boolean speakJumpComplete) {
    boolean hadPendingCountdown = carrierJumpDepartureTime != null;
    clearCarrierJumpCountdownStateOnly();
    Instant now = Instant.now();
    if (!CarrierJumpCooldown.shouldStartOrResyncCooldown(
            hadPendingCountdown, arrivalTime, carrierJumpCooldownEndTime, now, offCarrierCompletion)) {
        return;
    }
    Instant cooldownStart = arrivalTime != null ? arrivalTime : now;
    startCarrierJumpCooldown(cooldownStart, offCarrierCompletion, speakJumpComplete);
}

/**
 * Cancelling a scheduled carrier jump also starts the in-game jump cooldown; clear the T- countdown
 * and run the same cooldown timer/viz (without "Jump complete" speech).
 */
private void onCarrierJumpCancelled(Instant cancelTime, EliteLogEvent event) {
    boolean hadPendingCountdown = carrierJumpDepartureTime != null;
    if (!hadPendingCountdown && !acceptOwnedCarrierCancel(event)) {
        return;
    }
    clearCarrierJumpCountdownStateOnly();
    Instant now = Instant.now();
    Instant start = cancelTime != null ? cancelTime : now;
    if (!hadPendingCountdown && !CarrierJumpCooldown.isJumpTimestampLive(start, now)) {
        setTitleBarText(DEFAULT_TITLE_BAR_TITLE);
        updateRightStatusDefault();
        saveSessionState();
        return;
    }
    // Cancel timestamp aligns with the schedule UI — use the full 5-minute duration.
    startCarrierJumpCooldown(start, true, false);
}

private boolean acceptOwnedCarrierCancel(EliteLogEvent event) {
    long carrierId = carrierIdFromEvent(event);
    EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
    if (tabs == null) {
        return carrierId == 0L;
    }
    org.dce.ed.logreader.OwnedFleetCarrierTracker tracker = tabs.getOwnedFleetCarrierTracker();
    if (tracker == null) {
        return carrierId == 0L;
    }
    if (tracker.hasOwnedCarrierId()) {
        return tracker.isOwnedCarrierId(carrierId);
    }
    return carrierId == 0L;
}

private static long carrierIdFromEvent(EliteLogEvent event) {
    if (event == null || event.getRawJson() == null) {
        return 0L;
    }
    JsonObject raw = event.getRawJson();
    if (!raw.has("CarrierID") || raw.get("CarrierID").isJsonNull()) {
        return 0L;
    }
    try {
        return raw.get("CarrierID").getAsLong();
    } catch (Exception ex) {
        return 0L;
    }
}

private boolean isCommanderAboardFleetCarrier() {
    EliteOverlayTabbedPane tabPane =
            (contentPanel != null) ? contentPanel.getTabbedPane() : null;
    SystemTabPanel systemTab =
            (tabPane != null) ? tabPane.getSystemTabPanel() : null;
    SystemState st = (systemTab != null) ? systemTab.getState() : null;
    return st != null && st.isCommanderAboardFleetCarrier();
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

/**
 * Target shown in the title bar during an FC jump countdown. The in-game
 * {@link CarrierJumpRequestEvent} target is authoritative — a manual jump can go off-route, and
 * showing the loaded Spansh route's next hop instead displayed the wrong destination. The route's
 * next hop is only a fallback when the request carried no system name.
 */
private String resolveCarrierJumpTitleTarget() {
    if (carrierJumpTargetSystem != null && !carrierJumpTargetSystem.isBlank()) {
        return carrierJumpTargetSystem;
    }
    EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
    if (tabs != null) {
        FleetCarrierTabPanel fleet = tabs.getFleetCarrierTabPanel();
        if (fleet != null && fleet.isSpanshRouteLoaded()) {
            String routeNext = RouteTabPanel.nextRouteDestinationSystemName(fleet.getRouteSession());
            if (routeNext != null && !routeNext.isBlank()) {
                return routeNext;
            }
        }
    }
    return carrierJumpTargetSystem;
}

private void updateCarrierJumpCountdown() {
    if (carrierJumpDepartureTime == null) {
        return;
    }
    Instant departure = carrierJumpDepartureTime;
    if (CarrierJumpCooldown.shouldForceCompleteCountdown(departure, Instant.now())) {
        // Journal missed completion (or same-system hop with no CarrierLocation/CarrierJump).
        // Do not announce "Jump complete" — this is a stuck-UI recovery path.
        onCarrierJumpCompleted(departure, !isCommanderAboardFleetCarrier(), false);
        return;
    }
    publishRightStatusText();
}

    /** True while an owned-carrier jump countdown is active (scheduled through arrival). */
    public boolean hasCarrierJumpCountdown() {
        return carrierJumpDepartureTime != null;
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
    startCarrierJumpCooldown(Instant.now(), false, true);
}

private void startCarrierJumpCooldown(Instant startTime, boolean offCarrierCompletion) {
    startCarrierJumpCooldown(startTime, offCarrierCompletion, true);
}

private void startCarrierJumpCooldown(Instant startTime, boolean offCarrierCompletion, boolean speakJumpComplete) {
    Instant effectiveStart = startTime != null ? startTime : Instant.now();
    carrierJumpCooldownEndTime = CarrierJumpCooldown.cooldownEndFromJump(effectiveStart, offCarrierCompletion);
    if (carrierJumpCooldownTimer != null) {
        carrierJumpCooldownTimer.stop();
    }
    carrierJumpCooldownTimer = new javax.swing.Timer(500, e -> updateCarrierJumpCooldown());
    carrierJumpCooldownTimer.setRepeats(true);
    carrierJumpCooldownTimer.start();
    updateCarrierJumpCooldown();
    if (speakJumpComplete && !carrierJumpCompleteSpokenForCurrentJump) {
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
        execTriggerService.onFleetCooldownComplete();
        setTitleBarText(DEFAULT_TITLE_BAR_TITLE);
        updateRightStatusDefault();
        saveSessionState();
    }
}

private void updateRightStatusDefault() {
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

private static String formatBountyCredits(long credits) {
    if (credits <= 0) {
        return "";
    }
    double d = credits;
    if (credits >= 1_000_000_000L) {
        return String.format(Locale.US, "Bounties Earned: %.1fB Cr", d / 1_000_000_000d);
    }
    if (credits >= 1_000_000L) {
        return String.format(Locale.US, "Bounties Earned: %.1fM Cr", d / 1_000_000d);
    }
    if (credits >= 1_000L) {
        return String.format(Locale.US, "Bounties Earned: %.1fK Cr", d / 1_000d);
    }
    NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
    return "Bounties Earned: " + nf.format(credits) + " Cr";
}

/** Status bar credit line: Bio + Bounties. Geo survey totals stay tracked but are not shown yet. */
private static String formatScienceCredits(long exoCredits, long bountyCredits) {
    String bio = formatExoCredits(exoCredits);
    String bounty = formatBountyCredits(bountyCredits);
    StringBuilder sb = new StringBuilder();
    appendStatusCreditSegment(sb, bio);
    appendStatusCreditSegment(sb, bounty);
    return sb.toString();
}

private static void appendStatusCreditSegment(StringBuilder sb, String segment) {
    if (segment == null || segment.isBlank()) {
        return;
    }
    if (sb.length() > 0) {
        sb.append(" · ");
    }
    sb.append(segment);
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

private Long loadBountyCreditsTotalFromSystemCache() {
    try {
        EdoSessionState s = SystemCache.getInstance().loadEdoSessionState();
        return s != null ? s.getBountyCreditsTotalUnclaimed() : null;
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
            st.setBountyCreditsTotalUnclaimed(Long.valueOf(bountyCreditsTracker.getUnclaimedTotal()));
            // Merge from live SystemState so journal fields (e.g. carrier orbit) are not dropped by load+save of credits only.
            cache.mergeCommanderSessionFromReplayedState(st);
        } else {
            EdoSessionState s = cache.loadEdoSessionState();
            s.setExobiologyCreditsTotalUnsold(exoCreditsTotal);
            s.setGeoSurveyCreditsTotal(geoSurveyCreditsTotal);
            s.setBountyCreditsTotalUnclaimed(Long.valueOf(bountyCreditsTracker.getUnclaimedTotal()));
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
            st.setBountyCreditsTotalUnclaimed(Long.valueOf(bountyCreditsTracker.getUnclaimedTotal()));
            cache.mergeCommanderSessionFromReplayedState(st);
        } else {
            EdoSessionState s = cache.loadEdoSessionState();
            s.setGeoSurveyCreditsTotal(geoSurveyCreditsTotal);
            s.setBountyCreditsTotalUnclaimed(Long.valueOf(bountyCreditsTracker.getUnclaimedTotal()));
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

            boolean firstBonus = false;
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
                // best-effort; omit first-bonus until Spansh/body state is known
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

private void installBountyCreditsTracker() {
    try {
        LiveJournalMonitor monitor = LiveJournalMonitor.getInstance(EliteDangerousOverlay.clientKey);
        monitor.addListener(event -> {
            if (!bountyCreditsTracker.applyJournalEvent(event)) {
                return;
            }
            // Persist/UI off the journal thread — SQLite + Swing work was delaying massacre kill handling.
            SwingUtilities.invokeLater(() -> {
                persistExoCreditsTotal();
                updateRightStatusDefault();
                EliteOverlayTabbedPane tp = (contentPanel == null) ? null : contentPanel.getTabbedPane();
                if (tp != null && tp.getCombatTabPanel() != null) {
                    // Force credit line refresh when unclaimed total changes.
                    tp.getCombatTabPanel().handleLogEvent(event);
                }
            });
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

    NpcCrewTracker.getInstance().addListener(this::refreshPassThroughUnifiedStatus);
    BountyScanTracker.getInstance().addListener(() -> {
        // ShipTargeted updates arrive on the journal thread during combat.
        if (SwingUtilities.isEventDispatchThread()) {
            publishRightStatusText();
        } else {
            SwingUtilities.invokeLater(this::publishRightStatusText);
        }
    });

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
    if (hasFleetCarrierStatusAnnouncement()) {
        return false;
    }
    return isMarqueeRightStatusEmpty();
}

/**
 * True when the marquee/status label has no Bio/Geo/Bounties, target bounty, update hint, or speech banner.
 * Fleet jump / cooldown text is shown in the static announcement label, not here.
 */
boolean isMarqueeRightStatusEmpty() {
    Long targetedBounty = BountyScanTracker.getInstance().getTargetedBountyInSight();
    if (targetedBounty != null && targetedBounty.longValue() > 0L) {
        return false;
    }
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
        boolean fighterPilot = EliteOverlayTabbedPane.shouldShowNoFighterPilotWarning(docked);
        Instant until = exceptionLeftStatusUntil;
        String err = exceptionLeftStatusText;
        boolean showErr = err != null && until != null && Instant.now().isBefore(until);

        String miningErr = miningSheetsStatusError;
        boolean showMiningErr = miningErr != null && !miningErr.isBlank();

        boolean showExec = hasExecOverlayStatus();
        String execFrag = showExec ? buildExecOverlayStatusHtmlFragment() : "";

        boolean marqueeEmpty = isMarqueeRightStatusEmpty();
        boolean showWarning = limpet || fighterPilot;

        boolean marqueeWillShow = showErr || showMiningErr || showExec || showWarning || !marqueeEmpty;
        applyFleetCarrierTimeBadge(fleetCarrierTimeBadgeHost, fleetCarrierTimeLabel);
        applyFleetCarrierAnnouncement(fleetCarrierAnnouncementHost, fleetCarrierAnnouncementLabel, marqueeWillShow);

        if (!showErr && !showMiningErr && !showExec && !showWarning && marqueeEmpty) {
            // Keep the status row visually clear when there is no marquee content.
            // A visible placeholder dash is confusing after totals reset (e.g., after SellOrganicData).
            passThroughStatusLabel.setText("");
            passThroughStatusLabel.setForeground(EdoUi.Internal.MENU_FG_LIGHT);
            passThroughStatusLabel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            passThroughStatusLabel.setVisible(true);
            return;
        }

        String sep = "<span style='color:" + EdoUi.htmlRgb(EdoUi.Internal.MENU_FG_LIGHT) + ";'>  |  </span>";
        StringBuilder html = new StringBuilder("<html>");
        if (showErr) {
            html.append("<span style='color:").append(EdoUi.htmlRgb(EdoUi.User.ERROR)).append(";'>")
                    .append(EdoUi.escapeHtmlMinimal(err)).append("</span>");
            if (!marqueeEmpty) {
                html.append(sep);
                appendRightStatusInnerHtml(html);
            }
        } else if (showMiningErr) {
            html.append("<span style='color:").append(EdoUi.htmlRgb(EdoUi.User.ERROR)).append(";'>")
                    .append(EdoUi.escapeHtmlMinimal(miningErr)).append("</span>");
            if (!marqueeEmpty) {
                html.append(sep);
                appendRightStatusInnerHtml(html);
            }
        } else if (showExec) {
            html.append(execFrag);
            if (!marqueeEmpty) {
                html.append(sep);
                appendRightStatusInnerHtml(html);
            }
        } else if (showWarning) {
            if (!marqueeEmpty) {
                appendRightStatusInnerHtml(html);
            }
            // FC announcement already owns the "| " when active and marquee is otherwise empty.
            html.append(buildStatusWarningSpan(marqueeEmpty, limpet, fighterPilot));
        } else {
            appendRightStatusInnerHtml(html);
        }
        html.append("</html>");
        passThroughStatusLabel.setText(html.toString());
        // JLabel ignores this for most HTML content; kept for non-HTML edge cases.
        passThroughStatusLabel.setForeground(EdoUi.Internal.MENU_FG_LIGHT);
        if (getRightStatusUpdateHintPlain() != null && !showErr && !showMiningErr && !showExec) {
            passThroughStatusLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            passThroughStatusLabel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
        passThroughStatusLabel.setVisible(true);
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

        reapplyNativeMousePassThroughIfEnabled();
        SwingUtilities.invokeLater(() -> {
            reapplyNativeMousePassThroughIfEnabled();
            if (titleBar != null) {
                titleBar.setMouseInteractionMode(mouseInteractionMode);
            }
        });
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
        if (!isDisplayable()) {
            return false;
        }
        return WindowsNativeMousePassThrough.isWindows();
    }

    private void installPassThroughPaintGuard() {
        passThroughPaintGuard = event -> {
            if (!mouseInteractionMode.isPassThroughLike() || !isShowing()) {
                return;
            }
            if (event.getID() != PaintEvent.PAINT) {
                return;
            }
            if (!(event.getSource() instanceof Component src)) {
                return;
            }
            if (!SwingUtilities.isDescendingFrom(src, this)) {
                return;
            }
            scheduleNativePassThroughReapplyAfterPaint();
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(passThroughPaintGuard, AWTEvent.PAINT_EVENT_MASK);
    }

    private void installPassThroughHierarchyGuard() {
        addHierarchyListener(e -> {
            if (!mouseInteractionMode.isPassThroughLike() || !isShowing()) {
                return;
            }
            if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
                scheduleNativePassThroughReapplyAfterPaint();
            }
        });
    }

    private void updateMousePassThroughNativeStyleTimer() {
        stopMousePassThroughNativeStyleTimer();
        if (!mouseInteractionMode.isPassThroughLike() || !isShowing()) {
            return;
        }
        mousePassThroughNativeStyleTimer = new Timer(CROSSHAIR_POLL_MS, e -> {
            if (!mouseInteractionMode.isPassThroughLike() || !isShowing()) {
                stopMousePassThroughNativeStyleTimer();
                return;
            }
            stampNativeMousePassThrough(true);
        });
        mousePassThroughNativeStyleTimer.setRepeats(true);
        mousePassThroughNativeStyleTimer.start();
    }

    private void stopMousePassThroughNativeStyleTimer() {
        if (mousePassThroughNativeStyleTimer != null) {
            mousePassThroughNativeStyleTimer.stop();
            mousePassThroughNativeStyleTimer = null;
        }
    }

    public void setPassThroughEnabled(boolean enabled) {
        setPassThroughEnabled(enabled, false);
    }

    /**
     * Binary convenience for window-mode switches: {@code true} → Full MPT, {@code false} → Normal.
     *
     * @param persistUserPreference when true, save for the next session. When false, only apply native/UI state.
     */
    public void setPassThroughEnabled(boolean enabled, boolean persistUserPreference) {
        setMouseInteractionMode(
                enabled ? MouseInteractionMode.FULL_PASS_THROUGH : MouseInteractionMode.NORMAL,
                persistUserPreference);
    }

    public void setMouseInteractionMode(MouseInteractionMode mode) {
        setMouseInteractionMode(mode, false);
    }

    /**
     * @param persistUserPreference when true, save {@code mode} for the next session (title-bar / dwell).
     *                              When false, only apply native/UI state (mode switches, startup).
     */
    public void setMouseInteractionMode(MouseInteractionMode mode, boolean persistUserPreference) {
        MouseInteractionMode next = mode != null ? mode : MouseInteractionMode.NORMAL;
        boolean stateChanged = this.mouseInteractionMode != next;
        this.mouseInteractionMode = next;
        OverlayPreferences.setOverlayMouseInteractionMode(next);
        if (persistUserPreference) {
            OverlayPreferences.putOverlayMouseInteractionModePersisted(next);
        }
        if (stateChanged) {
            resetPassThroughCloseHoverState();
            applyOverlayBackgroundFromPreferences(next.isPassThroughLike());
            System.out.println("Mouse interaction mode: " + next);
        }
        applyPassThrough(next.isPassThroughLike());
        if (titleBar != null) {
            titleBar.setMouseInteractionMode(next);
        }
        updateMousePassThroughNativeStyleTimer();
        if (!next.isPassThroughLike()) {
            PassThroughTooltipSupport.clear();
        }
        EliteOverlayTabbedPane tabs = contentPanel != null ? contentPanel.getTabbedPane() : null;
        if (tabs != null && tabs.getSystemTabPanel() != null) {
            tabs.getSystemTabPanel().onMouseInteractionModeChanged();
        }
        repaint();
    }

    /**
     * Re-applies {@link WinUser#WS_EX_TRANSPARENT} when mouse pass-through is on. Safe to call after
     * {@code setBounds}, mode switches, or theme rebuilds.
     */
    public void reapplyNativeMousePassThroughIfEnabled() {
        if (mouseInteractionMode.isPassThroughLike()) {
            stampNativeMousePassThrough(true);
        }
    }

    /**
     * Layered-window repaints can clear {@code WS_EX_TRANSPARENT} immediately after a re-apply; schedule one EDT
     * pass after the paint completes.
     */
    public void scheduleNativePassThroughReapplyAfterPaint() {
        if (!mouseInteractionMode.isPassThroughLike() || !isShowing()) {
            return;
        }
        if (nativePassThroughReapplyScheduled) {
            return;
        }
        nativePassThroughReapplyScheduled = true;
        SwingUtilities.invokeLater(() -> {
            nativePassThroughReapplyScheduled = false;
            if (mouseInteractionMode.isPassThroughLike()) {
                stampNativeMousePassThrough(true);
            }
        });
    }

    /** Advances Normal → Selective → Full → Normal and persists. */
    public void cycleMouseInteractionMode() {
        setMouseInteractionMode(mouseInteractionMode.next(), true);
    }

    /** @deprecated Prefer {@link #cycleMouseInteractionMode()} */
@Deprecated
    public void togglePassThrough() {
        cycleMouseInteractionMode();
    }

    public MouseInteractionMode getMouseInteractionMode() {
        return mouseInteractionMode;
    }

    /** True when mode is Selective or Full (OS click-through may be active). */
    public boolean isPassThroughEnabled() {
        return mouseInteractionMode.isPassThroughLike();
    }

    public void applyUiFontPreferences() {
        OverlayPreferences.clearUiFontLivePreview();
        contentPanel.applyUiFontPreferences();
        if (passThroughStatusLabel != null && fleetCarrierTimeLabel != null) {
            java.awt.Font rowFont = OverlayMenuStatusBar.statusRowFontFromPreferences();
            passThroughStatusLabel.setFont(rowFont);
            fleetCarrierTimeLabel.setFont(rowFont);
            if (fleetCarrierAnnouncementLabel != null) {
                fleetCarrierAnnouncementLabel.setFont(rowFont);
            }
            OverlayMenuStatusBar.clearFleetBadgeSlotCache(fleetCarrierTimeBadgeHost);
            applyFleetCarrierTimeBadge(fleetCarrierTimeBadgeHost, fleetCarrierTimeLabel);
            applyFleetCarrierAnnouncement(fleetCarrierAnnouncementHost, fleetCarrierAnnouncementLabel,
                    !isMarqueeRightStatusEmpty());
        }
        revalidate();
        repaint();
    }


    @Override
    public void applyThemeFromPreferences() {
        OverlayPreferences.applyThemeToEdoUi();

        if (passThroughMenuBar != null) {
            OverlayMenuStatusBar.refreshMenuBarTheme(passThroughMenuBar);
            applyFleetCarrierTimeBadge(fleetCarrierTimeBadgeHost, fleetCarrierTimeLabel);
            applyFleetCarrierAnnouncement(fleetCarrierAnnouncementHost, fleetCarrierAnnouncementLabel,
                    !isMarqueeRightStatusEmpty());
        }

        if (contentPanel != null) {
            contentPanel.rebuildTabbedPane();
        }

        // Rebuild copies getBackground(); re-push prefs so mouse-PT vs Normal % and alpha stay authoritative.
        applyOverlayBackgroundFromPreferences(mouseInteractionMode.isPassThroughLike());

        if (titleBar != null) {
            titleBar.setMouseInteractionMode(mouseInteractionMode);
        }
        reapplyNativeMousePassThroughIfEnabled();
        repaint();
    }

    @Override
    public void refreshSystemTabFromSavedPreferences() {
        EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
        if (tabs != null) {
            tabs.getSystemTabPanel().refreshFromSavedOverlayPreferences();
            tabs.getMissionsTabPanel().refreshFromSavedOverlayPreferences();
        }
    }

    @Override
    public void refreshOverlayTabBarFromSavedPreferences() {
        EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
        if (tabs != null) {
            tabs.refreshOverlayTabBarFromSavedPreferences();
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
            if (fleetCarrierAnnouncementLabel != null) {
                fleetCarrierAnnouncementLabel.setFont(rowFont);
            }
            OverlayMenuStatusBar.clearFleetBadgeSlotCache(fleetCarrierTimeBadgeHost);
            applyFleetCarrierTimeBadge(fleetCarrierTimeBadgeHost, fleetCarrierTimeLabel);
            applyFleetCarrierAnnouncement(fleetCarrierAnnouncementHost, fleetCarrierAnnouncementLabel,
                    !isMarqueeRightStatusEmpty());
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
            if (fleetCarrierAnnouncementLabel != null) {
                fleetCarrierAnnouncementLabel.setFont(rowFont);
            }
            OverlayMenuStatusBar.clearFleetBadgeSlotCache(fleetCarrierTimeBadgeHost);
            applyFleetCarrierTimeBadge(fleetCarrierTimeBadgeHost, fleetCarrierTimeLabel);
            applyFleetCarrierAnnouncement(fleetCarrierAnnouncementHost, fleetCarrierAnnouncementLabel,
                    !isMarqueeRightStatusEmpty());
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
        applyOverlayBackgroundFromPreferences(mouseInteractionMode.isPassThroughLike());
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
     * {@code passThroughMode} selects which transparency preset was edited; paint always uses the given percent
     * on this undecorated frame (per-pixel alpha). Decorated window mode cannot see through to the game.
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
        OverlayPreferences.publishWindowChromeTransparency(getRootPane(), treatAsTransparent, pct);
        getRootPane().putClientProperty(OverlayPreferences.WINDOW_MOUSE_MODE_KEY, mouseInteractionMode);
        if (contentPanel != null) {
            contentPanel.applyOverlayBackground(bg, treatAsTransparent);
        }

        if (passThroughMenuBar != null) {
            OverlayMenuStatusBar.refreshMenuBarTheme(passThroughMenuBar);
            applyFleetCarrierTimeBadge(fleetCarrierTimeBadgeHost, fleetCarrierTimeLabel);
            applyFleetCarrierAnnouncement(fleetCarrierAnnouncementHost, fleetCarrierAnnouncementLabel,
                    !isMarqueeRightStatusEmpty());
        }

        revalidate();
        repaint();
    }


    /**
     * Win32 {@link WinUser#WS_EX_TRANSPARENT} stamp only — no {@code revalidate}. Safe to call at crosshair poll
     * rate while layered map repaints run; full {@link #applyPassThrough} with layout must not run that often.
     */
    /**
     * While mouse pass-through is on, temporarily clear {@code WS_EX_TRANSPARENT} when the cursor is over
     * interactive chrome (and Selective tab hit regions). Win32 style is per-HWND.
     */
    private void stampNativeMousePassThrough(boolean enable) {
        if (!tryAcquireHwnd()) {
            return;
        }
        boolean stampEnable = enable && !isPointerOverInteractiveChrome();
        if (!WindowsNativeMousePassThrough.applyToWindowTree(this, stampEnable)) {
            System.err.println("Failed to apply native mouse pass-through to overlay window.");
        }
    }

    private boolean isPointerOverInteractiveChrome() {
        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi == null || !isShowing()) {
            return false;
        }
        Point mouse = pi.getLocation();
        if (containsScreenPoint(titleBar, mouse)) {
            return true;
        }
        if (containsScreenPoint(passThroughMenuBar, mouse)) {
            return true;
        }
        EliteOverlayTabbedPane tabs = contentPanel != null ? contentPanel.getTabbedPane() : null;
        if (tabs == null) {
            return false;
        }
        if (tabs.isPointerOverTabBar(mouse)) {
            return true;
        }
        if (mouseInteractionMode == MouseInteractionMode.SELECTIVE) {
            return tabs.isPointerOverSelectiveHit(mouse);
        }
        // Full MPT: Control Panel buttons, tab scrollbars, ExoBio map, and in-progress route row drags.
        if (tabs.isRouteReorderGestureActiveAnywhere()) {
            return true;
        }
        if (tabs.isPointerOverControlPanelActionButton(mouse)) {
            return true;
        }
        if (tabs.isPointerOverTabScrollBar(mouse)) {
            return true;
        }
        return tabs.isPointerOverBiologyMap(mouse);
    }

    private static boolean containsScreenPoint(Component component, Point screenPoint) {
        if (component == null || !component.isShowing() || screenPoint == null) {
            return false;
        }
        try {
            Point origin = component.getLocationOnScreen();
            return new Rectangle(origin.x, origin.y, component.getWidth(), component.getHeight())
                    .contains(screenPoint);
        } catch (IllegalComponentStateException ex) {
            return false;
        }
    }

    private void applyPassThrough(boolean enable) {
        stampNativeMousePassThrough(enable);

        if (enable) {
            getRootPane().setBorder(null);
        } else {
            getRootPane().setBorder(javax.swing.BorderFactory.createEmptyBorder());
        }

        revalidate();
    }
    public void prepareForShow(boolean passThroughAppearanceMode) {
        if (passThroughAppearanceMode) {
            // Restore last mouse-interaction mode; apply matching transparency % (Normal when off).
            MouseInteractionMode mode = OverlayPreferences.getOverlayMouseInteractionModePersisted(
                    MouseInteractionMode.FULL_PASS_THROUGH);
            applyOverlayBackgroundFromPreferences(mode.isPassThroughLike());
            setMouseInteractionMode(mode, false);
        } else {
            applyOverlayBackgroundFromPreferences(false);
            if (titleBar != null) {
                titleBar.setMouseInteractionMode(this.mouseInteractionMode);
            }
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

    public static boolean isDecoratedMaximizedStored() {
        return Preferences.userNodeForPackage(OverlayFrame.class).getBoolean(PREF_KEY_DECORATED_MAXIMIZED, false);
    }

    public static void setDecoratedMaximizedStored(boolean maximized) {
        if (EdoTestFlags.isolateUi()) {
            return;
        }
        Preferences p = Preferences.userNodeForPackage(OverlayFrame.class);
        p.putBoolean(PREF_KEY_DECORATED_MAXIMIZED, maximized);
        flushOverlayPrefs(p);
    }

    public static boolean isPassThroughMaximizedStored() {
        return Preferences.userNodeForPackage(OverlayFrame.class).getBoolean(PREF_KEY_PT_MAXIMIZED, false);
    }

    public static void setPassThroughMaximizedStored(boolean maximized) {
        if (EdoTestFlags.isolateUi()) {
            return;
        }
        Preferences p = Preferences.userNodeForPackage(OverlayFrame.class);
        p.putBoolean(PREF_KEY_PT_MAXIMIZED, maximized);
        flushOverlayPrefs(p);
    }

    /**
     * True when a decorated {@link Frame} is OS-maximized, or when an undecorated pass-through frame fills its
     * monitor (manual resize to screen edges).
     */
    public static boolean isWindowMaximizedOrFullScreen(Window w) {
        if (w == null) {
            return false;
        }
        if (w instanceof Frame frame) {
            if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
                return true;
            }
        }
        if (!w.isShowing()) {
            return false;
        }
        Rectangle outer = windowOuterRectangle(w);
        Rectangle screen = monitorBoundsForWindow(w);
        return screen != null && rectanglesApproxEqual(outer, screen, FULL_SCREEN_BOUNDS_TOLERANCE_PX);
    }

    /** Screen bounds of the monitor that contains most of {@code w}'s outer rectangle. */
    public static Rectangle monitorBoundsForWindow(Window w) {
        if (w == null) {
            return null;
        }
        Rectangle frameRect = windowOuterRectangle(w);
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] devices = ge.getScreenDevices();
        if (devices == null || devices.length == 0) {
            return ge.getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        }
        Point center = new Point(frameRect.x + frameRect.width / 2, frameRect.y + frameRect.height / 2);
        for (GraphicsDevice gd : devices) {
            Rectangle screen = gd.getDefaultConfiguration().getBounds();
            if (screen.contains(center)) {
                return new Rectangle(screen);
            }
        }
        GraphicsDevice best = null;
        long bestArea = 0L;
        for (GraphicsDevice gd : devices) {
            Rectangle screen = gd.getDefaultConfiguration().getBounds();
            long area = intersectionArea(frameRect, screen);
            if (area > bestArea) {
                bestArea = area;
                best = gd;
            }
        }
        if (best != null) {
            return new Rectangle(best.getDefaultConfiguration().getBounds());
        }
        return new Rectangle(ge.getDefaultScreenDevice().getDefaultConfiguration().getBounds());
    }

    private static long intersectionArea(Rectangle a, Rectangle b) {
        Rectangle inter = a.intersection(b);
        if (inter.width <= 0 || inter.height <= 0) {
            return 0L;
        }
        return (long) inter.width * inter.height;
    }

    private static boolean rectanglesApproxEqual(Rectangle a, Rectangle b, int tolerancePx) {
        if (a == null || b == null) {
            return false;
        }
        int tol = Math.max(0, tolerancePx);
        return Math.abs(a.x - b.x) <= tol
                && Math.abs(a.y - b.y) <= tol
                && Math.abs(a.width - b.width) <= tol
                && Math.abs(a.height - b.height) <= tol;
    }

    /** Persists pass-through outer bounds and mirrors to legacy {@code overlay.*} keys. */
    public static void persistPassThroughBoundsRectangle(Rectangle r) {
        if (r == null || EdoTestFlags.isolateUi()) {
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
        if (r == null || EdoTestFlags.isolateUi()) {
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
        if (EdoTestFlags.isolateUi()) {
            return;
        }
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
            Component src = e.getComponent();
            // Tables own their own drag gestures (route reorder, etc.); don't steal for window resize.
            if (src instanceof JTable) {
                dragCursor = Cursor.DEFAULT_CURSOR;
                dragging = false;
                return;
            }
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
            frame.reapplyNativeMousePassThroughIfEnabled();
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
        if (!isShowing()) {
            PassThroughTooltipSupport.clear();
            crosshairOverlay.setCrosshairPoint(null);
            crosshairOverlay.setVisible(false);
            resetPassThroughCloseHoverState();
            return;
        }

        if (!mouseInteractionMode.isPassThroughLike()) {
            PassThroughTooltipSupport.clear();
            crosshairOverlay.setCrosshairPoint(null);
            crosshairOverlay.setVisible(false);
            resetPassThroughCloseHoverState();
            return;
        }

        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi != null) {
            Point mouseOnScreen = pi.getLocation();
            try {
                Rectangle frameBounds = new Rectangle(getLocationOnScreen(), getSize());
                if (frameBounds.contains(mouseOnScreen)) {
                    updatePassThroughHoverClose(mouseOnScreen);
                } else {
                    resetPassThroughCloseHoverState();
                }
            } catch (IllegalComponentStateException ex) {
                resetPassThroughCloseHoverState();
            }
        } else {
            resetPassThroughCloseHoverState();
        }

        if (pi == null) {
            crosshairOverlay.setCrosshairPoint(null);
            crosshairOverlay.setVisible(false);
            return;
        }

        Point mouseOnScreen = pi.getLocation();
        Point frameOnScreen;
        try {
            frameOnScreen = getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            crosshairOverlay.setCrosshairPoint(null);
            crosshairOverlay.setVisible(false);
            return;
        }

        int relX = mouseOnScreen.x - frameOnScreen.x;
        int relY = mouseOnScreen.y - frameOnScreen.y;

        if (relX >= 0 && relY >= 0 && relX < getWidth() && relY < getHeight()) {
            EliteOverlayTabbedPane tabs = contentPanel != null ? contentPanel.getTabbedPane() : null;
            if (tabs != null && tabs.isPointerOverControlPanelActionButton(mouseOnScreen)) {
                crosshairOverlay.setCrosshairPoint(null);
                crosshairOverlay.setVisible(false);
                return;
            }
            crosshairOverlay.setCrosshairPoint(new Point2D.Double(relX, relY));
            if (!crosshairOverlay.isVisible()) {
                crosshairOverlay.setVisible(true);
            }
        } else {
            crosshairOverlay.setCrosshairPoint(null);
            crosshairOverlay.setVisible(false);
        }

        PassThroughTooltipSupport.poll(backgroundPanel, true);
    }

    private void updatePassThroughHoverClose(Point mouseOnScreen) {
        if (titleBar == null || mouseOnScreen == null) {
            resetPassThroughCloseHoverState();
            return;
        }
        java.awt.Rectangle closeRect = titleBar.getCloseButtonScreenBounds();
        java.awt.Rectangle minimizeRect = titleBar.getMinimizeButtonScreenBounds();
        java.awt.Rectangle toggleRect = titleBar.getToggleButtonScreenBounds();
        java.awt.Rectangle hammerRect = titleBar.getHammerButtonScreenBounds();
        java.awt.Rectangle settingsRect = titleBar.getSettingsButtonScreenBounds();
        if (closeRect == null || minimizeRect == null || toggleRect == null
                || hammerRect == null || settingsRect == null) {
            resetPassThroughCloseHoverState();
            return;
        }

        boolean hClose = closeRect.contains(mouseOnScreen);
        boolean hMinimize = minimizeRect.contains(mouseOnScreen);
        boolean hToggle = toggleRect.contains(mouseOnScreen);
        boolean hHammer = hammerRect.contains(mouseOnScreen);
        boolean hSettings = settingsRect.contains(mouseOnScreen);

        long now = System.currentTimeMillis();

        if (!mouseInteractionMode.isPassThroughLike()) {
            passThroughToggleHoverStartMs = -1L;
            return;
        }

        titleBar.setCloseHoverProgrammatic(hClose);
        titleBar.setMinimizeHoverProgrammatic(hMinimize);
        titleBar.setToggleHoverProgrammatic(hToggle);
        titleBar.setHammerHoverProgrammatic(hHammer);
        titleBar.setSettingsHoverProgrammatic(hSettings);

        if (hClose) {
            passThroughToggleHoverStartMs = -1L;
            passThroughHammerHoverStartMs = -1L;
            passThroughSettingsHoverStartMs = -1L;
            passThroughMinimizeHoverStartMs = -1L;
            if (passThroughCloseHoverStartMs < 0L) {
                passThroughCloseHoverStartMs = now;
            } else if (now - passThroughCloseHoverStartMs >= PASS_THROUGH_CLOSE_DWELL_MS) {
                closeOverlay();
            }
            return;
        }
        passThroughCloseHoverStartMs = -1L;

        if (hMinimize) {
            passThroughToggleHoverStartMs = -1L;
            passThroughHammerHoverStartMs = -1L;
            passThroughSettingsHoverStartMs = -1L;
            if (passThroughMinimizeHoverStartMs < 0L) {
                passThroughMinimizeHoverStartMs = now;
            } else if (now - passThroughMinimizeHoverStartMs >= PASS_THROUGH_CLOSE_DWELL_MS) {
                org.dce.ed.ui.EdoWindowIconify.iconifyAll();
                passThroughMinimizeHoverStartMs = -1L;
            }
            return;
        }
        passThroughMinimizeHoverStartMs = -1L;

        if (hToggle) {
            passThroughHammerHoverStartMs = -1L;
            passThroughSettingsHoverStartMs = -1L;
            if (passThroughToggleHoverStartMs < 0L) {
                passThroughToggleHoverStartMs = now;
            } else if (now - passThroughToggleHoverStartMs >= PASS_THROUGH_TOGGLE_DWELL_MS) {
                cycleMouseInteractionMode();
                passThroughToggleHoverStartMs = -1L;
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

        EliteOverlayTabbedPane tp = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
        // ExoBio map is punched interactive in Selective and Full — use AWT clicks, not dwell.
        if (tp != null) {
            tp.resetPassThroughBioMapControlsHover();
        }
    }

    private void resetPassThroughCloseHoverState() {
        passThroughCloseHoverStartMs = -1L;
        passThroughMinimizeHoverStartMs = -1L;
        passThroughToggleHoverStartMs = -1L;
        passThroughHammerHoverStartMs = -1L;
        passThroughSettingsHoverStartMs = -1L;
        if (titleBar != null) {
            titleBar.setCloseHoverProgrammatic(false);
            titleBar.setMinimizeHoverProgrammatic(false);
            titleBar.setToggleHoverProgrammatic(false);
            titleBar.setHammerHoverProgrammatic(false);
            titleBar.setSettingsHoverProgrammatic(false);
        }
        EliteOverlayTabbedPane tp = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
        if (tp != null) {
            tp.resetPassThroughBioMapControlsHover();
        }
    }

    private static class CrosshairOverlay extends JComponent {

        private static final Color CROSSHAIR_OUTLINE = new Color(0, 0, 0, 200);
        private static final Color CROSSHAIR_FILL = new Color(255, 255, 255, 245);

        private Point2D.Double crosshairPoint;

        CrosshairOverlay() {
            setOpaque(false);
        }

        void setCrosshairPoint(Point2D.Double p) {
            if (p == null && crosshairPoint == null) {
                return;
            }
            if (p != null && crosshairPoint != null && p.x == crosshairPoint.x && p.y == crosshairPoint.y) {
                return;
            }
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

                double x = crosshairPoint.x;
                double y = crosshairPoint.y;
                double arm = 11.0;

                g2.setStroke(new BasicStroke(3.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(CROSSHAIR_OUTLINE);
                g2.draw(new Line2D.Double(x - arm, y, x + arm, y));
                g2.draw(new Line2D.Double(x, y - arm, x, y + arm));

                g2.setStroke(new BasicStroke(1.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(CROSSHAIR_FILL);
                g2.draw(new Line2D.Double(x - arm, y, x + arm, y));
                g2.draw(new Line2D.Double(x, y - arm, x, y + arm));

            } finally {
                g2.dispose();
            }
            OverlayFrame frame = overlayFrame;
            if (frame != null) {
                frame.scheduleNativePassThroughReapplyAfterPaint();
            }
        }

        @Override
        public boolean contains(int x, int y) {
            // Critical: don't intercept mouse events.
            return false;
        }
    }

    private long getLong(JsonObject obj, String field, long defaultValue) {
        return obj.has(field) && !obj.get(field).isJsonNull()
                ? obj.get(field).getAsLong()
                : defaultValue;
    }

    private int getInt(JsonObject obj, String field, int defaultValue) {
        return obj.has(field) && !obj.get(field).isJsonNull()
                ? obj.get(field).getAsInt()
                : defaultValue;
    }

}
