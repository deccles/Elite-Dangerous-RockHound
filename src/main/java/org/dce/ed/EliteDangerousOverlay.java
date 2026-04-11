package org.dce.ed;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.Timer;

import java.time.LocalTime;

import org.dce.ed.logreader.RescanJournalsMain;
import org.dce.ed.tts.PollyTtsCached;
import org.dce.ed.tts.TtsSprintf;
import org.dce.ed.tts.VoicePackManager;
import org.dce.ed.ui.ConsoleMonitor;
import org.dce.ed.ui.StartupSplashOverlay;
import org.dce.ed.util.AppIconUtil;
import org.dce.ed.util.GithubMsiUpdater;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.NativeInputEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseWheelEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseWheelListener;
import org.dce.ed.ui.EdoUi;

public class EliteDangerousOverlay implements NativeKeyListener, NativeMouseWheelListener {

    private static final String PREF_WINDOW_X = "windowX";
    private static final String PREF_WINDOW_Y = "windowY";
    private static final String PREF_WINDOW_WIDTH = "windowWidth";
    private static final String PREF_WINDOW_HEIGHT = "windowHeight";
    public static String clientKey = "EDO";

    private static final String MAVEN_GROUP_ID = "org.dce";
    private static final String MAVEN_ARTIFACT_ID = "EliteDangerousOverlay";

    private final Preferences prefs;

    private final OverlayContentPanel contentPanel;
    private final OverlayFrame passThroughFrame;
    private final DecoratedOverlayDialog decoratedDialog;

    private volatile boolean passThroughMode;

    private static final String PREF_START_IN_PASSTHROUGH = "overlay.startInPassThrough";

    
    public EliteDangerousOverlay() {
        // Apply theme prefs BEFORE constructing any UI components, so everything is styled consistently.
        OverlayPreferences.applyThemeToEdoUi();

        this.prefs = Preferences.userNodeForPackage(EliteDangerousOverlay.class);
        this.passThroughMode = prefs.getBoolean(PREF_START_IN_PASSTHROUGH, false);
        OverlayPreferences.setPassThroughWindowActive(this.passThroughMode);
        this.contentPanel = new OverlayContentPanel(() -> passThroughMode);

        this.passThroughFrame = new OverlayFrame(contentPanel);
        boolean initialMousePassThrough = this.passThroughMode
                ? OverlayPreferences.getOverlayMousePassThroughToGamePersisted(true)
                : false;
        this.passThroughFrame.setPassThroughEnabled(initialMousePassThrough, false);

        this.decoratedDialog = new DecoratedOverlayDialog(passThroughFrame, contentPanel, clientKey);
        this.decoratedDialog.setOnRequestSwitchToPassThrough(() -> SwingUtilities.invokeLater(() -> setPassThroughMode(true)));

        UIManager.put("TitlePane.background", EdoUi.User.BACKGROUND);
        UIManager.put("TitlePane.foreground", EdoUi.User.MAIN_TEXT);
        
        AppIconUtil.applyAppIcon(passThroughFrame, AppIconUtil.APP_ICON_RESOURCE);
        // Normal interactive startup check (dialog-based).
        GithubMsiUpdater.checkForUpdatesOnStartup(passThroughFrame);
        // Background check for status bar hint (immediate).
        GithubMsiUpdater.checkForStatusBar(passThroughFrame,
            result -> {
                if (result != null) {
                    passThroughFrame.setUpdateAvailableVersion(result.latestVersion);
                }
            });

        // Periodic background check every 20 minutes on the clock (00, 20, 40).
        startPeriodicUpdateChecks();
    }

    private void startPeriodicUpdateChecks() {
        // Fire once per minute; only actually hit GitHub at minutes 0, 20, 40.
        final int[] lastMinuteChecked = { -1 };
        Timer t = new Timer(60_000, e -> {
            LocalTime now = LocalTime.now();
            int minute = now.getMinute();
            if (minute % 20 != 0) {
                return;
            }
            if (minute == lastMinuteChecked[0]) {
                return;
            }
            lastMinuteChecked[0] = minute;
            GithubMsiUpdater.checkForStatusBar(passThroughFrame,
                result -> {
                    if (result != null) {
                        passThroughFrame.setUpdateAvailableVersion(result.latestVersion);
                    }
                });
        });
        t.setRepeats(true);
        t.setInitialDelay(60_000);
        t.start();
    }

    public static void main(String[] args) throws IOException {

        System.out.println("EDO Overlay version: " + getAppVersion());

        ConsoleMonitor consoleMonitor = ConsoleMonitor.getInstance(1000);
        consoleMonitor.redirectOutput();

        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        try {
            RescanJournalsMain.rescanJournals(false);
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            if (!OverlayPreferences.isJournalDirectoryAvailable(clientKey)) {
                JOptionPane.showMessageDialog(null,
                    "Elite Dangerous journal directory was not found.\n\n"
                    + "The overlay will start without live journal data. You can still use features that don't require the game (e.g. route planning).\n\n"
                    + "To use journal features, install Elite Dangerous or set a custom journal path in Settings.",
                    "Journal directory not found",
                    JOptionPane.WARNING_MESSAGE);
            }
            EliteDangerousOverlay app = new EliteDangerousOverlay();
            app.start();
        });
    }

    private static String getAppVersion() {
        // 1) If you set Implementation-Version in the manifest, this will be populated.
        try {
            String v = EliteDangerousOverlay.class.getPackage().getImplementationVersion();
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        } catch (Exception ignored) {
        }

        // 2) Maven embeds pom.properties inside the JAR.
        String pomPropsPath = "/META-INF/maven/" + MAVEN_GROUP_ID + "/" + MAVEN_ARTIFACT_ID + "/pom.properties";
        try (InputStream in = EliteDangerousOverlay.class.getResourceAsStream(pomPropsPath)) {
            if (in == null) {
                return "(unknown)";
            }
            Properties props = new Properties();
            props.load(in);
            String v = props.getProperty("version");
            if (v == null || v.isBlank()) {
                return "(unknown)";
            }
            return v.trim();
        } catch (Exception ignored) {
            return "(unknown)";
        }
    }

    private void start() {
        if (passThroughMode) {
            // Start directly in pass-through frame mode.
            passThroughFrame.showOverlay();
            prewarmDecoratedDialog();
            StartupSplashOverlay.install(passThroughFrame);
        } else {
            // Start directly in decorated non-pass-through mode (no startup mode flip).
            java.awt.Rectangle bounds = passThroughFrame.getBounds();
            passThroughFrame.setPassThroughEnabled(false);

            decoratedDialog.setBounds(bounds);
            decoratedDialog.attachContent();
            passThroughFrame.setRightStatusListener(decoratedDialog::setRightStatusText);
            passThroughFrame.refreshRightStatusDisplay();
            decoratedDialog.applyOverlayBackgroundFromPreferences(false);
            decoratedDialog.setVisible(true);
            decoratedDialog.toFront();
            StartupSplashOverlay.install(decoratedDialog);
        }

        // Save bounds and clean up on close
        passThroughFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    GlobalScreen.removeNativeMouseWheelListener(EliteDangerousOverlay.this);
                    GlobalScreen.unregisterNativeHook();
                } catch (NativeHookException ex) {
                    ex.printStackTrace();
                }
            }
        });

        // Quiet JNativeHook logging
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.WARNING);
        logger.setUseParentHandlers(false);

        try {
            GlobalScreen.registerNativeHook();
        } catch (NativeHookException ex) {
            ex.printStackTrace();
            return;
        }

        Window voicePackHost = passThroughMode ? passThroughFrame : decoratedDialog;
        PollyTtsCached.setSpeechDialogParentWindow(voicePackHost);
        PollyTtsCached.setSpeechCacheMissBannerReporter(passThroughFrame::setSpeechCacheMissBanner);

        GlobalScreen.addNativeKeyListener(this);
        GlobalScreen.addNativeMouseWheelListener(this);
        TtsSprintf ttsSprintf = new TtsSprintf(new PollyTtsCached());
        ttsSprintf.speakf("Welcome commander");

        SwingUtilities.invokeLater(() -> VoicePackManager.checkAutoVoicePackOnStartup(voicePackHost));
    }

    private void setPassThroughMode(boolean enablePassThrough) {
    	if (this.passThroughMode == enablePassThrough) {
    		return;
    	}

    	java.awt.Window fromWindow = this.passThroughMode ? passThroughFrame : decoratedDialog;
    	java.awt.Window toWindow = enablePassThrough ? passThroughFrame : decoratedDialog;

    	Rectangle captured = captureWindowOuterRect(fromWindow);
    	if (captured == null) {
    		captured = new Rectangle(fromWindow.getBounds());
    	}
    	// Always take outer size from the window we are leaving. Merging with a stored size could keep an
    	// inflated getBounds() from the other host (pass-through often reports slightly larger on Windows).
    	final Rectangle outerBounds = new Rectangle(captured);
    	final Point sourceWindowTopLeft = new Point(outerBounds.x, outerBounds.y);

    	// Horizontal: align shared content. Vertical: pin to source window top (content-based dy is unreliable
    	// across decorated caption+menu vs undecorated custom title bar — fixes pass-through "dropping" down).
    	Rectangle contentScreenBefore = captureContentPanelScreenRect();

    	if (contentPanel.getParent() != null) {
    		contentPanel.getParent().remove(contentPanel);
    	}

    	if (enablePassThrough) {
    		// Prepare pass-through frame fully before showing.
    		passThroughFrame.setBounds(outerBounds);
    		// Add content before pass-through styling so layout / background apply to the full hierarchy.
    		passThroughFrame.add(contentPanel, java.awt.BorderLayout.CENTER);
    		passThroughFrame.setPassThroughEnabled(
    				OverlayPreferences.getOverlayMousePassThroughToGamePersisted(true), false);
    		passThroughFrame.prepareForShow(true);
    		passThroughFrame.setRightStatusListener(null);
    		passThroughFrame.refreshRightStatusDisplay();
    		passThroughFrame.applyOverlayBackgroundFromPreferences(true);
    		passThroughFrame.applyUiFontPreferences();
    		passThroughFrame.validate();
    		passThroughFrame.repaint();
    		passThroughFrame.setVisible(true);
    		passThroughFrame.toFront();
    		passThroughFrame.requestFocus();
    		fromWindow.setVisible(false);
    		scheduleStabilizeWindowBounds(passThroughFrame, outerBounds, contentScreenBefore, sourceWindowTopLeft);
    	} else {
    		// Prepare decorated dialog fully while hidden, then show once.
    		passThroughFrame.setPassThroughEnabled(false);

    		decoratedDialog.setBounds(outerBounds);
    		decoratedDialog.attachContent();
    		passThroughFrame.setRightStatusListener(decoratedDialog::setRightStatusText);
    		passThroughFrame.refreshRightStatusDisplay();
    		decoratedDialog.prepareForShow();
    		decoratedDialog.showTransitionShield();
    		decoratedDialog.setVisible(true);
            // Keep pass-through frame on top until decorated is fully ready.
            passThroughFrame.toFront();
    		javax.swing.SwingUtilities.invokeLater(() -> {
                decoratedDialog.toFront();
                decoratedDialog.requestFocus();
                fromWindow.setVisible(false);
                Rectangle applied = stabilizeOverlayWindowBounds(
                        decoratedDialog, outerBounds, contentScreenBefore, sourceWindowTopLeft);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    reapplyFixedOverlayBounds(decoratedDialog, applied);
                    Rectangle afterContent = matchContentPanelSizeOnce(decoratedDialog, contentScreenBefore);
                    Rectangle finalRect = afterContent != null ? afterContent : applied;
                    reapplyFixedOverlayBounds(decoratedDialog, finalRect);
                    decoratedDialog.hideTransitionShield();
                });
    		});
    	}

    	// 5) Final state.
    	this.passThroughMode = enablePassThrough;
        OverlayPreferences.setPassThroughWindowActive(enablePassThrough);
        prefs.putBoolean(PREF_START_IN_PASSTHROUGH, enablePassThrough);

    	// If we're switching to the decorated window, make sure pass-through is disabled.
    	if (!enablePassThrough) {
    		passThroughFrame.setPassThroughEnabled(false);
    	    forceWindowToFront(toWindow);
    	}

        PollyTtsCached.setSpeechDialogParentWindow(toWindow);
    }

    /**
     * Outer window bounds for mode switching. Position from {@link Window#getLocationOnScreen()}; width and
     * height from {@link Window#getBounds()} so they match {@link Window#setBounds(int, int, int, int)} semantics
     * (getWidth/getHeight can disagree slightly on some Windows DPI paths).
     */
    private static Rectangle captureWindowOuterRect(Window w) {
        if (w == null || !w.isShowing()) {
            return null;
        }
        try {
            Point loc = w.getLocationOnScreen();
            Rectangle b = w.getBounds();
            return new Rectangle(loc.x, loc.y, b.width, b.height);
        } catch (IllegalComponentStateException e) {
            return null;
        }
    }

    /**
     * Screen-space bounds of the shared {@link OverlayContentPanel} before it is reparented.
     * Used to keep the overlay content from drifting when swapping between decorated and pass-through hosts.
     */
    private Rectangle captureContentPanelScreenRect() {
        if (contentPanel == null || !contentPanel.isShowing()) {
            return null;
        }
        try {
            Point p = contentPanel.getLocationOnScreen();
            return new Rectangle(p.x, p.y, contentPanel.getWidth(), contentPanel.getHeight());
        } catch (IllegalComponentStateException e) {
            return null;
        }
    }

    /**
     * Re-applies the intended outer frame rectangle and nudges the window so the shared content panel
     * matches its pre-switch horizontal position; vertical position uses {@code sourceWindowTopLeft.y} so
     * the window stays flush with the same screen edge as before the switch (decorated vs undecorated chrome
     * makes content-based vertical delta unreliable).
     *
     * @return the final bounds applied (caller may re-apply the same rectangle later without recomputing).
     */
    private Rectangle stabilizeOverlayWindowBounds(
            Window w,
            Rectangle outerBounds,
            Rectangle contentBefore,
            Point sourceWindowTopLeft) {
        if (w == null || outerBounds == null) {
            return null;
        }
        w.setBounds(outerBounds);
        int x = outerBounds.x;
        int y = outerBounds.y;
        if (contentBefore != null && contentPanel != null) {
            try {
                if (contentPanel.isShowing()) {
                    Point p = contentPanel.getLocationOnScreen();
                    int dx = contentBefore.x - p.x;
                    x = w.getX() + dx;
                    y = (sourceWindowTopLeft != null) ? sourceWindowTopLeft.y : w.getY() + (contentBefore.y - p.y);
                }
            } catch (IllegalComponentStateException ignored) {
            }
        }
        int targetWidth = outerBounds.width;
        int targetHeight = outerBounds.height;
        w.setBounds(x, y, targetWidth, targetHeight);
        Rectangle applied = new Rectangle(x, y, targetWidth, targetHeight);
        enforceIntendedOuterSize(w, applied.x, applied.y, applied.width, applied.height);
        return applied;
    }

    /** Re-applies a fixed rectangle without remeasuring content (avoids feedback loops / visible pulsing). */
    private static void reapplyFixedOverlayBounds(Window w, Rectangle r) {
        if (w == null || r == null) {
            return;
        }
        w.setBounds(r);
        enforceIntendedOuterSize(w, r.x, r.y, r.width, r.height);
    }

    /**
     * After reparenting, the two hosts reserve different space for chrome (OS title/menu vs custom title bar).
     * Matching outer bounds alone therefore leaves the shared content panel a different size. This runs
     * <strong>once</strong> per toggle: grow or shrink the outer window so the content panel matches the
     * pre-switch width/height from {@code contentBefore}. Not repeated on timers (avoids pulsing).
     */
    private Rectangle matchContentPanelSizeOnce(Window w, Rectangle contentBefore) {
        if (w == null || contentBefore == null || contentPanel == null) {
            return null;
        }
        if (!contentPanel.isShowing()) {
            return null;
        }
        try {
            int cw = contentPanel.getWidth();
            int ch = contentPanel.getHeight();
            if (cw < 2 || ch < 2) {
                return null;
            }
            int dw = contentBefore.width - cw;
            int dh = contentBefore.height - ch;
            if (dw == 0 && dh == 0) {
                return new Rectangle(w.getBounds());
            }
            int nw = w.getWidth() + dw;
            int nh = w.getHeight() + dh;
            Dimension min = w.getMinimumSize();
            nw = Math.max(nw, min.width);
            nh = Math.max(nh, min.height);
            int x = w.getX();
            int y = w.getY();
            w.setBounds(x, y, nw, nh);
            enforceIntendedOuterSize(w, x, y, nw, nh);
            return new Rectangle(x, y, nw, nh);
        } catch (IllegalComponentStateException e) {
            return new Rectangle(w.getBounds());
        }
    }

    /**
     * Re-applies size if the window manager reported a larger frame than we requested (common for layered
     * undecorated windows on Windows).
     */
    private static void enforceIntendedOuterSize(Window w, int x, int y, int width, int height) {
        if (w == null) {
            return;
        }
        if (w.getWidth() != width || w.getHeight() != height) {
            w.setBounds(x, y, width, height);
        }
    }

    /**
     * One compute pass on the EDT, then optional fixed re-applies (same pixels) for layered-window settle.
     * Retries must not call {@link #stabilizeOverlayWindowBounds} again — remeasuring content after each
     * {@code setBounds} caused width/height feedback and visible pulsing.
     */
    private void scheduleStabilizeWindowBounds(
            Window w,
            Rectangle outerBounds,
            Rectangle contentBefore,
            Point sourceWindowTopLeft) {
        SwingUtilities.invokeLater(() -> {
            Rectangle applied = stabilizeOverlayWindowBounds(w, outerBounds, contentBefore, sourceWindowTopLeft);
            if (applied == null) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                reapplyFixedOverlayBounds(w, applied);
                Rectangle afterContent = matchContentPanelSizeOnce(w, contentBefore);
                Rectangle finalRect = afterContent != null ? afterContent : applied;
                reapplyFixedOverlayBounds(w, finalRect);
                Timer t = new Timer(120, e -> {
                    ((Timer) e.getSource()).stop();
                    reapplyFixedOverlayBounds(w, finalRect);
                });
                t.setRepeats(false);
                t.start();
            });
        });
    }

    //
    // Global key listener: F9 toggles between click-through overlay and a normal decorated window.
    //
    @Override
    public void nativeKeyPressed(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent e) {
        int toggleKey = OverlayPreferences.getPassThroughToggleKeyCode();
        if (toggleKey > 0 && e.getKeyCode() == toggleKey) {
            SwingUtilities.invokeLater(() -> setPassThroughMode(!passThroughMode));
            return;
        }

        int nextTabKey = OverlayPreferences.getNextShownTabKeyCode();
        if (nextTabKey > 0 && e.getKeyCode() == nextTabKey) {
            SwingUtilities.invokeLater(() -> {
                EliteOverlayTabbedPane tp = (contentPanel == null) ? null : contentPanel.getTabbedPane();
                if (tp != null) {
                    tp.selectNextVisibleTab();
                }
            });
        }
    }
    private static void forceWindowToFront(java.awt.Window w) {
        if (w == null) {
            return;
        }

        try {
            // Make sure native peer exists before messing with z-order
            if (!w.isDisplayable()) {
                w.addNotify();
            }

            // "Kick" it above fullscreen/borderless windows, then restore.
            boolean wasAot = w.isAlwaysOnTop();
            w.setAlwaysOnTop(true);
            w.setVisible(true);
            w.toFront();
            w.requestFocus();

            // Restore user's expected behavior (not always-on-top).
            w.setAlwaysOnTop(wasAot);
        } catch (Exception ignored) {
            // Best-effort only
        }
    }

    @Override
    public void nativeKeyReleased(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent e) {
        // not used
    }

    @Override
    public void nativeKeyTyped(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent e) {
        // not used
    }

    /**
     * While mouse pass-through is active, forward wheel events to the System / Route / Fleet Carrier table scrollers
     * when the pointer is over the overlay and that tab's vertical scroll bar is visible.
     */
    @Override
    public void nativeMouseWheelMoved(NativeMouseWheelEvent e) {
        if (!passThroughMode || !OverlayPreferences.isOverlayMousePassThroughToGame()) {
            return;
        }
        if (passThroughFrame == null || !passThroughFrame.isVisible()) {
            return;
        }
        Rectangle bounds = captureWindowOuterRect(passThroughFrame);
        int x = e.getX();
        int y = e.getY();
        if (bounds == null || !bounds.contains(x, y)) {
            return;
        }
        int rot = e.getWheelRotation();
        if (rot == 0) {
            return;
        }
        final boolean[] applied = { false };
        try {
            SwingUtilities.invokeAndWait(() -> {
                if (!passThroughMode || !OverlayPreferences.isOverlayMousePassThroughToGame()) {
                    return;
                }
                EliteOverlayTabbedPane tp = (contentPanel == null) ? null : contentPanel.getTabbedPane();
                if (tp != null) {
                    applied[0] = tp.handlePassThroughMouseWheelAtScreen(x, y, rot);
                }
            });
        } catch (Exception ignored) {
        }
        if (applied[0]) {
            markNativeInputEventConsumed(e);
        }
    }

    /**
     * Best-effort: stop propagating this native event to the OS (Windows/macOS). Unsupported on X11; uses
     * JNativeHook's internal {@code reserved} flag (see upstream {@code doc/ConsumingEvents.md}).
     */
    private static void markNativeInputEventConsumed(NativeInputEvent e) {
        if (e == null) {
            return;
        }
        try {
            java.lang.reflect.Field f = NativeInputEvent.class.getDeclaredField("reserved");
            f.setAccessible(true);
            f.setShort(e, (short) 0x01);
        } catch (Exception ignored) {
        }
    }

    private void prewarmDecoratedDialog() {
    	SwingUtilities.invokeLater(() -> {
    		// Make sure the peer is created.
    		decoratedDialog.addNotify();

    		// Put it somewhere not visible.
    		java.awt.Rectangle b = passThroughFrame.getBounds();
    		decoratedDialog.setBounds(b.x, b.y + 3000, b.width, b.height);

    		// Show once at essentially invisible opacity (decorated window stays opaque for normal use).
    		try {
    			decoratedDialog.setOpacity(0.01f);
    		} catch (Exception ignored) {
    		}

    		decoratedDialog.setVisible(true);

    		// Immediately hide again; restore opacity.
    		decoratedDialog.setVisible(false);
    		try {
    			decoratedDialog.setOpacity(1.0f);
    		} catch (Exception ignored) {
    		}
    	});
    }

}
