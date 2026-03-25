package org.dce.ed;

import java.awt.Color;
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
import org.dce.ed.ui.ConsoleMonitor;
import org.dce.ed.util.AppIconUtil;
import org.dce.ed.util.GithubMsiUpdater;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import org.dce.ed.ui.EdoUi;

public class EliteDangerousOverlay implements NativeKeyListener {

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
        this.passThroughFrame.setPassThroughEnabled(this.passThroughMode);

        this.decoratedDialog = new DecoratedOverlayDialog(passThroughFrame, contentPanel, clientKey);
        this.decoratedDialog.setOnRequestSwitchToPassThrough(() -> SwingUtilities.invokeLater(() -> setPassThroughMode(true)));

        UIManager.put("TitlePane.background", EdoUi.User.BACKGROUND);
        UIManager.put("TitlePane.foreground", EdoUi.User.MAIN_TEXT);
        
        AppIconUtil.applyAppIcon(passThroughFrame, "/org/dce/ed/edsm/locate_icon.png");
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
        }

        // Save bounds and clean up on close
        passThroughFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
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

        GlobalScreen.addNativeKeyListener(this);
        TtsSprintf ttsSprintf = new TtsSprintf(new PollyTtsCached());
        ttsSprintf.speakf("Welcome commander");
    }

    private void setPassThroughMode(boolean enablePassThrough) {
    	if (this.passThroughMode == enablePassThrough) {
    		return;
    	}

    	// Determine current bounds from the currently-visible window.
    	java.awt.Rectangle bounds = this.passThroughMode
    			? passThroughFrame.getBounds()
    			: decoratedDialog.getBounds();

    	java.awt.Window fromWindow = this.passThroughMode ? passThroughFrame : decoratedDialog;
    	java.awt.Window toWindow = enablePassThrough ? passThroughFrame : decoratedDialog;
    	if (contentPanel.getParent() != null) {
    		contentPanel.getParent().remove(contentPanel);
    	}

    	if (enablePassThrough) {
    		// Prepare pass-through frame fully before showing.
    		passThroughFrame.setBounds(bounds);
    		passThroughFrame.setPassThroughEnabled(true);
    		passThroughFrame.prepareForShow(true);
    		passThroughFrame.add(contentPanel, java.awt.BorderLayout.CENTER);
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
    	} else {
    		// Prepare decorated dialog fully while hidden, then show once.
    		passThroughFrame.setPassThroughEnabled(false);

    		decoratedDialog.setBounds(bounds);
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
                javax.swing.SwingUtilities.invokeLater(() -> decoratedDialog.hideTransitionShield());
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
    }

    //
    // Global key listener: F9 toggles between click-through overlay and a normal decorated window.
    //
    @Override
    public void nativeKeyPressed(com.github.kwhat.jnativehook.keyboard.NativeKeyEvent e) {
        int toggleKey = OverlayPreferences.getPassThroughToggleKeyCode();
        if (toggleKey > 0 && e.getKeyCode() == toggleKey) {
            SwingUtilities.invokeLater(() -> setPassThroughMode(!passThroughMode));
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
