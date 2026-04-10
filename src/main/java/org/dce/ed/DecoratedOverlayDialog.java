package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.dce.ed.util.AppIconUtil;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.OverlayBackgroundPanel;

/**
 * Decorated window (min/max/resize) where ONLY the background fades.
 *
 * Implementation detail:
 * - Do NOT use setOpacity() (that fades everything)
 * - Instead paint the content background with an alpha channel
 */
public class DecoratedOverlayDialog extends JFrame implements OverlayUiPreviewHost {

	private static final long serialVersionUID = 1L;


	private final OverlayContentPanel contentPanel;
	private final String clientKey;

	private Runnable onRequestSwitchToPassThrough;

	private JMenuBar menuBar;
	private JLabel statusLabel;
	private volatile CargoMonitor.Snapshot lastCargoSnapshot;
	private String lastRightStatusText = "";
	private JComponent transitionShield;

	/** Same full-area fill as {@link OverlayFrame} so theme/overlay color shows in non-pass-through mode. */
	private OverlayBackgroundPanel decoratedBackgroundPanel;

	/**
	 * Minimal DWM binding.
	 */
	private interface DwmApi extends Library {
		DwmApi INSTANCE = Native.load("dwmapi", DwmApi.class);

		int DwmSetWindowAttribute(HWND hwnd, int dwAttribute, Pointer pvAttribute, int cbAttribute);
	}

	public DecoratedOverlayDialog(Window owner, OverlayContentPanel contentPanel, String clientKey) {
	    this(contentPanel, clientKey);
	    // Don't call setLocationRelativeTo(owner).
	    // The toggle logic will always setBounds() before showing, and calling
	    // setLocationRelativeTo causes a visible "first show" jump.
	}


	public DecoratedOverlayDialog(OverlayContentPanel contentPanel, String clientKey) {
		super("Elite Dangerous RockHound");

		this.contentPanel = contentPanel;
		this.clientKey = clientKey;

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setLayout(new BorderLayout());
		// Match OverlayFrame so mode switches do not fight different minimum sizes.
		setMinimumSize(new Dimension(260, 200));

		this.menuBar = createMenuBar();
		setJMenuBar(menuBar);

		AppIconUtil.applyAppIcon(this, AppIconUtil.APP_ICON_RESOURCE);

		addWindowListener(new WindowAdapter() {

		    @Override
		    public void windowOpened(WindowEvent e) {
		        applyDarkTitleBarIfSupported();
		    }

		    @Override
		    public void windowActivated(WindowEvent e) {
		        applyDarkTitleBarIfSupported();
		    }

		    @Override
		    public void windowClosing(WindowEvent e) {
		        dispose();
		        System.exit(0);
		    }
		});

	}

	public void setOnRequestSwitchToPassThrough(Runnable onRequestSwitchToPassThrough) {
		this.onRequestSwitchToPassThrough = onRequestSwitchToPassThrough;
	}

	private void firePassThroughRequest() {
		Runnable r = onRequestSwitchToPassThrough;
		if (r != null) {
			r.run();
		}
	}

	public void attachContent() {
		if (contentPanel.getParent() != null) {
			contentPanel.getParent().remove(contentPanel);
		}

		if (decoratedBackgroundPanel == null) {
			decoratedBackgroundPanel = new OverlayBackgroundPanel();
			decoratedBackgroundPanel.setOpaque(false);
			decoratedBackgroundPanel.setLayout(new BorderLayout());
		} else {
			decoratedBackgroundPanel.removeAll();
		}
		decoratedBackgroundPanel.add(contentPanel, BorderLayout.CENTER);
		setContentPane(decoratedBackgroundPanel);

		revalidate();
		repaint();

		applyOverlayBackgroundFromPreferences(false);
		applyUiFontPreferences();
		setAlwaysOnTop(OverlayPreferences.isNonOverlayAlwaysOnTop());

		installStatusArea();

	}

	private void installStatusArea() {
		EliteOverlayTabbedPane tp = (contentPanel == null) ? null : contentPanel.getTabbedPane();
		if (tp != null) {
			tp.addDockedStateListener(docked -> updateStatusLabel());
		}

		CargoMonitor.getInstance().addListener(snap -> {
			lastCargoSnapshot = snap;
			updateStatusLabel();
		});

		// Initial paint.
		lastCargoSnapshot = CargoMonitor.getInstance().getSnapshot();
		updateStatusLabel();
	}

	/**
	 * Show a temporary dark shield while switching windows to mask compositor flashes.
	 */
	public void showTransitionShield() {
		if (transitionShield == null) {
			transitionShield = new javax.swing.JPanel();
			transitionShield.setOpaque(true);
			transitionShield.setBackground(EdoUi.User.BACKGROUND != null ? EdoUi.User.BACKGROUND : Color.BLACK);
		}
		javax.swing.JLayeredPane lp = getLayeredPane();
		if (lp == null) {
			return;
		}
		int w = Math.max(getWidth(), 1);
		int h = Math.max(getHeight(), 1);
		transitionShield.setBounds(0, 0, w, h);
		if (transitionShield.getParent() != lp) {
			lp.add(transitionShield, javax.swing.JLayeredPane.DRAG_LAYER);
		}
		transitionShield.setVisible(true);
		lp.revalidate();
		lp.repaint();
	}

	public void hideTransitionShield() {
		if (transitionShield == null) {
			return;
		}
		if (transitionShield.getParent() != null) {
			transitionShield.getParent().remove(transitionShield);
		}
		transitionShield.setVisible(false);
		repaint();
	}

	/**
	 * Prepare decorated window visuals before first show to avoid bright default paints.
	 */
	public void prepareForShow() {
		try {
			Color base = EdoUi.User.BACKGROUND != null ? EdoUi.User.BACKGROUND : Color.BLACK;
			getContentPane().setBackground(base);
			getRootPane().setBackground(base);
			applyOverlayBackgroundFromPreferences(false);
			applyUiFontPreferences();
			validate();
			repaint();
		} catch (Exception ignored) {
		}
	}

	/** Called by OverlayFrame when this dialog is the visible status display (single entry point). */
	public void setRightStatusText(String text) {
		this.lastRightStatusText = text != null ? text : "";
		updateStatusLabel();
	}

	private void updateStatusLabel() {
		if (statusLabel == null) {
			return;
		}

		Runnable r = () -> {
			boolean limpet = shouldShowLowLimpetWarning();
			String right = lastRightStatusText != null ? lastRightStatusText.trim() : "";
			String full = OverlayFrame.buildDecoratedMenuStatusHtml(right, limpet);
			if (full.isEmpty()) {
				statusLabel.setText("");
				statusLabel.setVisible(false);
				return;
			}
			statusLabel.setText(full);
			if (limpet) {
				statusLabel.setForeground(EdoUi.User.ERROR);
				statusLabel.setCursor(Cursor.getDefaultCursor());
			} else if (full.contains("New version")) {
				statusLabel.setForeground(EdoUi.User.SUCCESS);
				statusLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			} else {
				statusLabel.setForeground(EdoUi.Internal.MENU_FG_LIGHT);
				statusLabel.setCursor(Cursor.getDefaultCursor());
			}
			statusLabel.setVisible(true);
		};

		if (SwingUtilities.isEventDispatchThread()) {
			r.run();
		} else {
			SwingUtilities.invokeLater(r);
		}
	}

	private boolean shouldShowLowLimpetWarning() {
		EliteOverlayTabbedPane tp = (contentPanel == null) ? null : contentPanel.getTabbedPane();
		boolean docked = tp != null && tp.isCurrentlyDocked();
		return EliteOverlayTabbedPane.shouldShowLowLimpetWarning(
				docked,
				lastCargoSnapshot
		);
	}

	private JMenuBar createMenuBar() {
		OverlayMenuStatusBar.Result r = OverlayMenuStatusBar.build(this, clientKey, true, this::firePassThroughRequest);
		statusLabel = r.statusLabel;
		return r.menuBar;
	}

	private void applyDarkTitleBarIfSupported() {
		String os = System.getProperty("os.name");
		if (os == null || !os.toLowerCase().contains("windows")) {
			return;
		}

		if (!isDisplayable()) {
			SwingUtilities.invokeLater(this::applyDarkTitleBarIfSupported);
			return;
		}

		try {
			Pointer ptr = Native.getComponentPointer(this);
			if (ptr == null) {
				return;
			}

			HWND hwnd = new HWND(ptr);

			Memory mem = new Memory(4);
			mem.setInt(0, 1);

			DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, 20, mem, 4);
			DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, 19, mem, 4);

		} catch (Throwable ignored) {
		}
	}

	@Override
	public boolean isPassThroughEnabled() {
		return false;
	}

	@Override
	public void applyUiFontPreferences() {
		OverlayPreferences.clearUiFontLivePreview();
		contentPanel.applyUiFontPreferences();
		revalidate();
		repaint();
	}


    @Override
    public void applyThemeFromPreferences() {
        OverlayPreferences.applyThemeToEdoUi();

        UIManager.put("TitlePane.background", EdoUi.User.BACKGROUND);
        UIManager.put("TitlePane.foreground", EdoUi.User.MAIN_TEXT);

        if (contentPanel != null) {
            contentPanel.rebuildTabbedPane();
        }

        refreshMenuBarAccentColors();
        repaint();
    }

	/**
	 * Swing stores foreground {@link Color} instances; after {@link OverlayPreferences#applyThemeToEdoUi()}
	 * the accent must be pushed again into the menu bar.
	 */
	private void refreshMenuBarAccentColors() {
		OverlayMenuStatusBar.refreshMenuBarTheme(menuBar);
	}

	@Override
	public void applyUiFontPreview(Font font) {
		if (font == null) {
			return;
		}
		OverlayPreferences.setUiFontLivePreview(font);
		contentPanel.applyUiFont(font);
		revalidate();
		repaint();
	}

	@Override
	public void revertUiFontLivePreview(Font savedFont) {
		OverlayPreferences.clearUiFontLivePreview();
		if (savedFont != null) {
			contentPanel.applyUiFont(savedFont);
		}
		revalidate();
		repaint();
	}

	@Override
	public void applyOverlayBackgroundFromPreferences(boolean passThroughMode) {
		int rgb = OverlayPreferences.getUiBackgroundRgb();
		int pct = OverlayPreferences.getNormalTransparencyPercent();
		applyOverlayBackgroundPreview(false, rgb, pct);
	}

	@Override
	public void applyOverlayBackgroundPreview(boolean passThroughMode, int rgb, int transparencyPercent) {

		// Do NOT call setOpacity() here. That fades ALL text and UI.
		// Instead: make the background color translucent.
		int pct = Math.max(0, Math.min(100, transparencyPercent));

		// Your existing preference semantics were "transparency percent".
		// 0% transparency => alpha 255 (opaque)
		// 100% transparency => alpha 0 (fully transparent background)
		int alpha = (int) Math.round(255.0 * (1.0 - (pct / 100.0)));

		if (alpha < 0) {
			alpha = 0;
		}
		if (alpha > 255) {
			alpha = 255;
		}

		Color base = EdoUi.rgb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
		Color bg = EdoUi.withAlpha(base, alpha);

		// Match pass-through window: paint fill behind non-opaque children (see OverlayFrame).
		if (decoratedBackgroundPanel != null) {
			if (alpha <= 0) {
				// This window is a normal decorated JFrame, not a per-pixel layered overlay. When overlay
				// prefs say "100% transparent", painting alpha-0 would skip the plate and leave random
				// framebuffer (often neon green) in gaps. Keep theme RGB as an opaque backing plate.
				decoratedBackgroundPanel.setPaintColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 255));
			} else {
				decoratedBackgroundPanel.setPaintColor(bg);
			}
		}

		// Same semantics as OverlayFrame.applyOverlayBackgroundPreview (pct > 0 => non-opaque Swing fill off).
		boolean treatAsTransparent = pct > 0;
		contentPanel.applyOverlayBackground(bg, treatAsTransparent);

		// Match content pane to theme so transparent / non-opaque regions don't read as flat black.
		getContentPane().setBackground(base);

		if (menuBar != null) {
			refreshMenuBarAccentColors();
		}

		revalidate();
		repaint();
	}
}