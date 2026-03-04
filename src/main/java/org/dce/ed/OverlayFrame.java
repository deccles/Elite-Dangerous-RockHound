package org.dce.ed;

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
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.LineBorder;

import org.dce.ed.exobiology.ExobiologyData;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.session.EdoSessionPersistence;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.LiveJournalMonitor;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.CarrierJumpRequestEvent;
import org.dce.ed.logreader.event.ScanOrganicEvent;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;

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

    private static final String PREF_KEY_X = "overlay.x";
    private static final String PREF_KEY_Y = "overlay.y";
    private static final String PREF_KEY_WIDTH = "overlay.width";
    private static final String PREF_KEY_HEIGHT = "overlay.height";

    private static final String PREF_KEY_EXO_CREDITS_TOTAL = "exo.creditsTotal";

    private static final String DEFAULT_TITLE_BAR_TITLE = "Elite Dangerous Overlay";

    /** Cooldown duration after fleet jump countdown expires (seconds). */
    private static final int CARRIER_JUMP_COOLDOWN_SECONDS = 5 * 60;

    private final LineBorder overlayBorder = new LineBorder(
            new java.awt.Color(200, 200, 255, 180),
            1,
            true
    );
    
    private final Preferences prefs = Preferences.userNodeForPackage(OverlayFrame.class);

    private HWND hwnd;
    private boolean passThroughEnabled;

 // For top title-bar status (passthrough overlay mode)
    private volatile boolean lastDocked;
    private volatile CargoMonitor.Snapshot lastCargoSnapshot;

    
    private final TitleBarPanel titleBar;
    private final OverlayContentPanel contentPanel;
	private final OverlayBackgroundPanel backgroundPanel;

    // Crosshair overlay and timer to show mouse position in pass-through mode
    private final CrosshairOverlay crosshairOverlay = new CrosshairOverlay();
    private final Timer crosshairTimer;

    
    private javax.swing.Timer carrierJumpCountdownTimer;
    private Instant carrierJumpDepartureTime;
    private String carrierJumpTargetSystem;
    private boolean carrierJumpTextNotificationSent;

    /** Cooldown phase (5 min) after fleet jump countdown expires. */
    private Instant carrierJumpCooldownEndTime;
    private javax.swing.Timer carrierJumpCooldownTimer;

    private long exoCreditsTotal;

    /** Debounced save of session state (500 ms after last tab change). */
    private final Timer sessionSaveTimer = new Timer(500, e -> saveSessionState());

    /** Single entry point for right-hand status: whichever window is visible gets updates. */
    private Consumer<String> rightStatusListener = this::setRightStatusTextOnTitleBar;

    public void setRightStatusListener(Consumer<String> listener) {
        this.rightStatusListener = listener != null ? listener : this::setRightStatusTextOnTitleBar;
    }

    private void setRightStatusTextOnTitleBar(String text) {
        if (titleBar != null) titleBar.setRightStatusText(text);
    }

    private void publishRightStatusText(String text) {
        rightStatusListener.accept(text);
    }

    public void refreshRightStatusDisplay() {
        publishRightStatusText(getRightStatusText());
    }

    public String getRightStatusText() {
        if (carrierJumpDepartureTime != null) {
            long seconds = Math.max(0, carrierJumpDepartureTime.getEpochSecond() - Instant.now().getEpochSecond());
            long minutes = seconds / 60;
            long secs = seconds % 60;
            String countdown;
            if (minutes >= 60) {
                long hours = minutes / 60;
                minutes = minutes % 60;
                countdown = String.format(Locale.US, "FC jump T-%d:%02d:%02d", hours, minutes, secs);
            } else {
                countdown = String.format(Locale.US, "FC jump T-%d:%02d", minutes, secs);
            }
            if (carrierJumpTargetSystem != null && !carrierJumpTargetSystem.isBlank()) {
                countdown += " → " + carrierJumpTargetSystem;
            }
            return countdown;
        }
        if (carrierJumpCooldownEndTime != null) {
            long seconds = Math.max(0, carrierJumpCooldownEndTime.getEpochSecond() - Instant.now().getEpochSecond());
            long minutes = seconds / 60;
            long secs = seconds % 60;
            return String.format(Locale.US, "Cooldown T-%d:%02d", minutes, secs);
        }
        return formatExoCredits(exoCreditsTotal);
    }
    
    public static OverlayFrame overlayFrame = null;
    
    public OverlayFrame(OverlayContentPanel contentPanel) {
        super("Elite Dangerous Overlay");

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

        // Save bounds and session state on close
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                saveSessionState();
                closeOverlay();
            }
        });

        // Custom title bar (draggable, close button)
        titleBar = new TitleBarPanel(this, "Elite Dangerous Overlay");
        add(titleBar, BorderLayout.NORTH);

        exoCreditsTotal = prefs.getLong(PREF_KEY_EXO_CREDITS_TOTAL, 0L);
        updateRightStatusDefault();

        // Transparent content panel with tabbed pane
        this.contentPanel = contentPanel;
        add(this.contentPanel, BorderLayout.CENTER);

        applyOverlayBackgroundFromPreferences(false);

        // Load saved bounds if available; otherwise use defaults
        loadBoundsFromPreferences(prefs, PREF_KEY_X, PREF_KEY_Y, PREF_KEY_WIDTH, PREF_KEY_HEIGHT);

        // Add custom resize handler for edges/corners.
        // IMPORTANT: attach recursively so resizing works even when cursor is over child components.
        int dragThickness = calcBorderDragThicknessPx();
        ResizeHandler resizeHandler = new ResizeHandler(this, TitleBarPanel.TOP_RESIZE_STRIP);
        installResizeHandlerRecursive(getRootPane(), resizeHandler);
        installResizeHandlerRecursive(getContentPane(), resizeHandler);
        
        installCarrierJumpTitleUpdater();
        installExoCreditsTracker();
        installTabbedPaneJournalListener();
        installLowLimpetStatusUpdater();
        sessionSaveTimer.setRepeats(false);
        installSessionPersistence();
    }

    private void installSessionPersistence() {
        EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
        if (tabs == null) return;
        Runnable debouncedSave = () -> {
            sessionSaveTimer.stop();
            sessionSaveTimer.start();
        };
        tabs.getRouteTabPanel().setSessionStateChangeCallback(debouncedSave);
        tabs.getSystemTabPanel().setSessionStateChangeCallback(debouncedSave);
        restoreSessionState();
    }

    private void saveSessionState() {
        EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
        if (tabs == null) return;
        EdoSessionState state = new EdoSessionState();
        tabs.getRouteTabPanel().fillSessionState(state);
        tabs.getSystemTabPanel().fillSessionState(state);
        fillCarrierSessionState(state);
        EdoSessionPersistence.save(state);
    }

    private void fillCarrierSessionState(EdoSessionState state) {
        if (state == null) return;
        if (carrierJumpDepartureTime != null) {
            state.setCarrierJumpDepartureTime(carrierJumpDepartureTime.toString());
        }
        state.setCarrierJumpTargetSystem(carrierJumpTargetSystem);
        state.setCarrierJumpTextNotificationSent(carrierJumpTextNotificationSent);
    }

    private void restoreSessionState() {
        EdoSessionState state = EdoSessionPersistence.load();
        EliteOverlayTabbedPane tabs = (contentPanel != null) ? contentPanel.getTabbedPane() : null;
        if (tabs == null) return;
        tabs.getRouteTabPanel().applySessionState(state);
        tabs.getSystemTabPanel().applySessionState(state);
        applyCarrierSessionState(state);
    }

    private void applyCarrierSessionState(EdoSessionState state) {
        if (state == null || state.getCarrierJumpDepartureTime() == null || state.getCarrierJumpDepartureTime().isBlank()) return;
        try {
            Instant departure = Instant.parse(state.getCarrierJumpDepartureTime());
            if (departure.isAfter(Instant.now())) {
                carrierJumpDepartureTime = departure;
                carrierJumpTargetSystem = state.getCarrierJumpTargetSystem();
                carrierJumpTextNotificationSent = Boolean.TRUE.equals(state.getCarrierJumpTextNotificationSent());
                setTitleBarText("");
                if (carrierJumpCountdownTimer != null) {
                    carrierJumpCountdownTimer.stop();
                }
                carrierJumpCountdownTimer = new Timer(500, e -> updateCarrierJumpCountdown());
                carrierJumpCountdownTimer.setRepeats(true);
                carrierJumpCountdownTimer.start();
                updateCarrierJumpCountdown();
            }
        } catch (Exception e) {
            // ignore invalid or old timestamp
        }
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
                    SwingUtilities.invokeLater(() -> startCarrierJumpCountdown(dep, sys));
                }
                return;
            }

            if (event.getType() == EliteEventType.CARRIER_JUMP_CANCELLED) {
                SwingUtilities.invokeLater(this::clearCarrierJumpCountdown);
                return;
            }

            if (event.getType() == EliteEventType.CARRIER_JUMP) {
                SwingUtilities.invokeLater(() -> {
                    clearCarrierJumpCountdownStateOnly();
                    startCarrierJumpCooldown();
                });
            }
        });
    } catch (Exception ex) {
        ex.printStackTrace();
    }
}

private void startCarrierJumpCountdown(Instant departureTime, String targetSystem) {
    carrierJumpDepartureTime = departureTime;
    carrierJumpTargetSystem = targetSystem;
    carrierJumpTextNotificationSent = false;

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

    long seconds = carrierJumpDepartureTime.getEpochSecond() - Instant.now().getEpochSecond();
    if (seconds < 0) {
        seconds = 0;
    }

    long minutes = seconds / 60;
    long secs = seconds % 60;

    String countdown;
    if (minutes >= 60) {
        long hours = minutes / 60;
        minutes = minutes % 60;
        countdown = String.format("FC jump T-%d:%02d:%02d", hours, minutes, secs);
    } else {
        countdown = String.format("FC jump T-%d:%02d", minutes, secs);
    }

    if (carrierJumpTargetSystem != null && !carrierJumpTargetSystem.isBlank()) {
        countdown += " → " + carrierJumpTargetSystem;
    }

    publishRightStatusText(countdown);

    if (Instant.now().isAfter(carrierJumpDepartureTime.plusSeconds(5))) {
        maybeSendCarrierJumpTextNotification();
        clearCarrierJumpCountdownStateOnly();
        startCarrierJumpCooldown();
    }
}

/** Clears only the jump countdown state and timer; does not touch cooldown or right status. */
private void clearCarrierJumpCountdownStateOnly() {
    carrierJumpDepartureTime = null;
    carrierJumpTargetSystem = null;
    carrierJumpTextNotificationSent = false;
    if (carrierJumpCountdownTimer != null) {
        carrierJumpCountdownTimer.stop();
        carrierJumpCountdownTimer = null;
    }
}

private void startCarrierJumpCooldown() {
    carrierJumpCooldownEndTime = Instant.now().plusSeconds(CARRIER_JUMP_COOLDOWN_SECONDS);
    if (carrierJumpCooldownTimer != null) {
        carrierJumpCooldownTimer.stop();
    }
    carrierJumpCooldownTimer = new javax.swing.Timer(500, e -> updateCarrierJumpCooldown());
    carrierJumpCooldownTimer.setRepeats(true);
    carrierJumpCooldownTimer.start();
    updateCarrierJumpCooldown();
    saveSessionState();
}

private void updateCarrierJumpCooldown() {
    if (carrierJumpCooldownEndTime == null) {
        return;
    }
    long seconds = Math.max(0, carrierJumpCooldownEndTime.getEpochSecond() - Instant.now().getEpochSecond());
    long minutes = seconds / 60;
    long secs = seconds % 60;
    publishRightStatusText(String.format(Locale.US, "Cooldown T-%d:%02d", minutes, secs));
    if (Instant.now().compareTo(carrierJumpCooldownEndTime) >= 0) {
        if (carrierJumpCooldownTimer != null) {
            carrierJumpCooldownTimer.stop();
            carrierJumpCooldownTimer = null;
        }
        carrierJumpCooldownEndTime = null;
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

    setTitleBarText(DEFAULT_TITLE_BAR_TITLE);
    updateRightStatusDefault();
    saveSessionState();
}

private void updateRightStatusDefault() {
    if (carrierJumpDepartureTime != null || carrierJumpCooldownEndTime != null) {
        return;
    }
    publishRightStatusText(formatExoCredits(exoCreditsTotal));
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

private void installExoCreditsTracker() {
    try {
        LiveJournalMonitor monitor = LiveJournalMonitor.getInstance(EliteDangerousOverlay.clientKey);

        monitor.addListener(event -> {
            if (event.getType() == EliteEventType.SELL_ORGANIC_DATA) {
            	System.out.println("Sold " + exoCreditsTotal);
                exoCreditsTotal = 0L;
                prefs.putLong(PREF_KEY_EXO_CREDITS_TOTAL, exoCreditsTotal);
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
                        firstBonus = !Boolean.TRUE.equals(body.getWasFootfalled());
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
            prefs.putLong(PREF_KEY_EXO_CREDITS_TOTAL, exoCreditsTotal);

            updateRightStatusDefault();
        });
    } catch (Exception ex) {
        ex.printStackTrace();
    }
}

private void installLowLimpetStatusUpdater() {
    EliteOverlayTabbedPane tp = (contentPanel == null) ? null : contentPanel.getTabbedPane();
    if (tp != null) {
        lastDocked = tp.isCurrentlyDocked();
        tp.addDockedStateListener(docked -> {
            lastDocked = docked;
            updateLeftStatusLabel();
        });
        tp.addLoadoutChangeListener(this::updateLeftStatusLabel);
    }

    CargoMonitor.getInstance().addListener(snap -> {
        lastCargoSnapshot = snap;
        updateLeftStatusLabel();
    });

    lastCargoSnapshot = CargoMonitor.getInstance().getSnapshot();
    updateLeftStatusLabel();
}

private void updateLeftStatusLabel() {
    if (titleBar == null) {
        return;
    }

    Runnable r = () -> {
        boolean show = EliteOverlayTabbedPane.shouldShowLowLimpetWarning(lastDocked, lastCargoSnapshot);
        titleBar.setLeftStatusText(show ? "Low Limpet Warning!" : "");
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

        try {
            Pointer ptr = Native.getWindowPointer(this);
            if (ptr == null) {
                System.err.println("Failed to obtain native window pointer for overlay window.");
            } else {
                hwnd = new HWND(ptr);
                applyPassThrough(false);
                System.out.println(
                        "Overlay size: " + getWidth() + "x" + getHeight()
                                + " at (" + getX() + "," + getY() + ")"
                );
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void setPassThroughEnabled(boolean enabled) {
        if (this.passThroughEnabled == enabled) {
            return;
        }

        this.passThroughEnabled = enabled;
        applyPassThrough(this.passThroughEnabled);
        applyOverlayBackgroundFromPreferences(this.passThroughEnabled);

        titleBar.setPassThrough(this.passThroughEnabled); // hide/show X
        System.out.println("Pass-through " + (this.passThroughEnabled ? "ENABLED" : "DISABLED"));
        repaint();
    }

    public void togglePassThrough() {
        setPassThroughEnabled(!passThroughEnabled);
    }

    public boolean isPassThroughEnabled() {
        return passThroughEnabled;
    }

    public void applyUiFontPreferences() {
        contentPanel.applyUiFontPreferences();
        revalidate();
        repaint();
    }


    @Override
    public void applyThemeFromPreferences() {
        OverlayPreferences.applyThemeToEdoUi();

        if (contentPanel != null) {
            contentPanel.rebuildTabbedPane();
            installSessionPersistence();
        }

        repaint();
    }

    public void applyUiFontPreview(java.awt.Font font) {
        if (font == null) {
            return;
        }
        contentPanel.applyUiFont(font);
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
        int rgb = passThroughMode
                ? OverlayPreferences.getPassThroughBackgroundRgb()
                : OverlayPreferences.getNormalBackgroundRgb();

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

        revalidate();
        repaint();
    }


    private void applyPassThrough(boolean enable) {
        if (hwnd == null) {
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

        // Defensive: avoid any default opaque background painting
        setBackground(new java.awt.Color(0, 0, 0, 0));
        if (getContentPane() != null) {
            getContentPane().setBackground(new java.awt.Color(0, 0, 0, 0));
        }
        if (getRootPane() != null) {
            getRootPane().setOpaque(false);
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

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        if (w > screenSize.width) {
            w = screenSize.width;
        }
        if (h > screenSize.height) {
            h = screenSize.height;
        }

        if (x < 0) {
            x = 0;
        }
        if (y < 0) {
            y = 0;
        }
        if (x + w > screenSize.width) {
            x = screenSize.width - w;
        }
        if (y + h > screenSize.height) {
            y = screenSize.height - h;
        }
        System.out.println("Read " + x + " " + y + " " + w + " " + h);
        setBounds(x, y, w, h);
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

        System.out.println("Saved : " + getX() + " " + getY() + " " + getWidth() + " " + getHeight());
    }

    /**
     * Centralized close method: saves bounds then exits.
     */
    public void closeOverlay() {
        saveBoundsToPreferences(PREF_KEY_X, PREF_KEY_Y, PREF_KEY_WIDTH, PREF_KEY_HEIGHT);
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
            return;
        }

        Point mouseOnScreen = pi.getLocation();
        Point frameOnScreen;
        try {
            frameOnScreen = getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            crosshairOverlay.setVisible(false);
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
        } else {
            crosshairOverlay.setCrosshairPoint(null);
            crosshairOverlay.setVisible(false);
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

                // ED-style orange with some transparency
                g2.setColor(EdoUi.Internal.MAIN_TEXT_ALPHA_200);

                int x = crosshairPoint.x;
                int y = crosshairPoint.y;

                int arm = 10;

                // Horizontal segment
                g2.drawLine(x - arm, y, x + arm, y);

                // Vertical segment
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

    private static final class OverlayBackgroundPanel extends javax.swing.JPanel {

        private static final long serialVersionUID = 1L;
		private java.awt.Color paintColor = new java.awt.Color(0, 0, 0, 0);

        OverlayBackgroundPanel() {
            setOpaque(false);
        }

        void setPaintColor(java.awt.Color paintColor) {
            if (paintColor == null) {
                paintColor = new java.awt.Color(0, 0, 0, 0);
            }
            this.paintColor = paintColor;
            repaint();
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            if (paintColor == null) {
                return;
            }
            if (paintColor.getAlpha() <= 0) {
                return;
            }
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            try {
                g2.setColor(paintColor);
                g2.fillRect(0, 0, getWidth(), getHeight());
            } finally {
                g2.dispose();
            }
        }
    }
    private void maybeSendCarrierJumpTextNotification() {
//        if (!OverlayPreferences.isTextNotificationsEnabled()) {
//            return;
//        }
//
//        List<String> address = OverlayPreferences.getTextNotificationAddress();
//
//        Thread t = new Thread(() -> {
//            try {
//                TextNotificationSender.sendText(
//                        address,
//                        "EDO",
//                        "Fleet Carrier jumping"
//                );
//            } catch (Exception ex) {
//                ex.printStackTrace();
//            }
//        }, "edo-text-notify");
//
//        t.setDaemon(true);
//        t.start();
    }

}
