package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import org.dce.ed.util.AppIconUtil;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.OverlayBackgroundPanel;
import org.dce.ed.OverlayPreferences.MiningLimpetReminderMode;
import org.dce.ed.logreader.event.LoadoutEvent;

/**
 * Decorated window (min/max/resize) where ONLY the background fades.
 *
 * Implementation detail:
 * - Do NOT use setOpacity() (that fades everything)
 * - Instead paint the content background with an alpha channel
 */
public class DecoratedOverlayDialog extends JFrame implements OverlayUiPreviewHost {

	private static final long serialVersionUID = 1L;

	private static final String APP_ICON_RESOURCE = "/org/dce/ed/edsm/locate_icon.png";

	// Menu bar uses Colors → Background; popups stay slightly darker for contrast.
	private static final Color MENU_POPUP_BG = EdoUi.Internal.DARK_14;
	private static final Color MENU_POPUP_FG = EdoUi.Internal.MENU_FG_LIGHT;

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
		super("Elite Dangerous Overlay");

		this.contentPanel = contentPanel;
		this.clientKey = clientKey;

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setLayout(new BorderLayout());

		this.menuBar = createMenuBar();
		setJMenuBar(menuBar);

		AppIconUtil.applyAppIcon(this, APP_ICON_RESOURCE);

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
			String full = right + (limpet ? (right.isEmpty() ? "" : "  |  ") + "Low Limpet Warning!" : "");
			statusLabel.setText(full);
			// Highlight low-limpet warning in red, otherwise use the normal menu foreground.
			if (limpet) {
				statusLabel.setForeground(EdoUi.User.ERROR);
			} else {
				statusLabel.setForeground(EdoUi.Internal.MENU_FG_LIGHT);
			}
			statusLabel.setVisible(!full.isEmpty());
		};

		if (SwingUtilities.isEventDispatchThread()) {
			r.run();
		} else {
			SwingUtilities.invokeLater(r);
		}
	}

	private static Color opaquePlate(Color c) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), 255);
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
		JMenuBar bar = new JMenuBar();
		bar.setOpaque(true);
		bar.setBackground(opaquePlate(EdoUi.User.BACKGROUND));
		bar.setBorder(new EmptyBorder(2, 6, 2, 6));

		JMenu overlayMenu = new JMenu("Menu");
		overlayMenu.setForeground(EdoUi.Internal.MENU_ACCENT);

		JMenuItem prefs = new JMenuItem("Preferences...");
		styleMenuItem(prefs);
		prefs.addActionListener(e -> {
			PreferencesDialog dialog = new PreferencesDialog(this, clientKey);
			dialog.setVisible(true);
		});
		overlayMenu.add(prefs);

		JMenu toolsMenu = new JMenu("Tools");
		toolsMenu.setForeground(EdoUi.Internal.MENU_ACCENT);
		addSortedToolsMenuItems(toolsMenu);
		overlayMenu.add(toolsMenu);

		JPopupMenu popup = overlayMenu.getPopupMenu();
		popup.setOpaque(true);
		popup.setBackground(MENU_POPUP_BG);
		popup.setBorder(new EmptyBorder(4, 4, 4, 4));

		statusLabel = new JLabel("");
		statusLabel.setOpaque(false);
		statusLabel.setForeground(EdoUi.Internal.MENU_FG_LIGHT);
		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));

		bar.add(Box.createHorizontalGlue());
		bar.add(statusLabel);
		bar.add(Box.createHorizontalStrut(10));
		bar.add(overlayMenu);
		return bar;
	}

	private void styleMenuItem(JMenuItem item) {
		item.setOpaque(true);
		item.setBackground(MENU_POPUP_BG);
		item.setForeground(MENU_POPUP_FG);
	}

	private void styleMenuTree(JMenuItem node) {
		if (node == null) {
			return;
		}
		styleMenuItem(node);
		if (node instanceof JMenu) {
			JMenu sub = (JMenu) node;
			JPopupMenu subPopup = sub.getPopupMenu();
			if (subPopup != null) {
				subPopup.setOpaque(true);
				subPopup.setBackground(MENU_POPUP_BG);
			}
			for (int i = 0; i < sub.getItemCount(); i++) {
				styleMenuTree(sub.getItem(i));
			}
		}
	}

	private void addSortedToolsMenuItems(JMenu toolsMenu) {
		JMenuItem backfill = new JMenuItem(OverlayToolsLaunchers.backfillMiningRunTimesMenuLabel());
		styleMenuItem(backfill);
		backfill.addActionListener(e -> OverlayToolsLaunchers.backfillMiningRunTimes(this));
		toolsMenu.add(backfill);

		JMenuItem updates = new JMenuItem("Check for Updates");
		styleMenuItem(updates);
		updates.addActionListener(e -> OverlayToolsLaunchers.checkForUpdates(this));
		toolsMenu.add(updates);

		JMenuItem exoDbg = new JMenuItem("Exo Prediction Debugger");
		styleMenuItem(exoDbg);
		exoDbg.addActionListener(e -> OverlayToolsLaunchers.launchExoPredictionDebugger(this));
		toolsMenu.add(exoDbg);

		JMenuItem fixRuns = new JMenuItem("Fix mining runs in Google Sheet");
		styleMenuItem(fixRuns);
		fixRuns.addActionListener(e -> OverlayToolsLaunchers.fixMiningRunsInGoogleSheet(this));
		toolsMenu.add(fixRuns);

		JMenuItem journal = new JMenuItem("Journal Monitor");
		styleMenuItem(journal);
		journal.addActionListener(e -> OverlayToolsLaunchers.launchJournalMonitor(this));
		toolsMenu.add(journal);

		JMenuItem edsm = new JMenuItem("Run EDSM Query Tools");
		styleMenuItem(edsm);
		edsm.addActionListener(e -> OverlayToolsLaunchers.launchEdsmQueryTools(this));
		toolsMenu.add(edsm);

		JMenuItem console = new JMenuItem("Show console");
		styleMenuItem(console);
		console.addActionListener(e -> OverlayToolsLaunchers.showConsole());
		toolsMenu.add(console);
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
		if (menuBar == null) {
			return;
		}
		menuBar.setBackground(opaquePlate(EdoUi.User.BACKGROUND));
		Color accent = EdoUi.Internal.MENU_ACCENT;
		menuBar.setForeground(accent);
		for (int i = 0; i < menuBar.getMenuCount(); i++) {
			JMenu m = menuBar.getMenu(i);
			if (m != null) {
				m.setForeground(accent);
			}
		}
	}

	@Override
	public void applyUiFontPreview(Font font) {
		if (font == null) {
			return;
		}
		contentPanel.applyUiFont(font);
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
			menuBar.setOpaque(true);
			refreshMenuBarAccentColors();

			for (int i = 0; i < menuBar.getMenuCount(); i++) {
				JMenu m = menuBar.getMenu(i);
				if (m != null) {

					JPopupMenu popup = m.getPopupMenu();
					if (popup != null) {
						popup.setOpaque(true);
						popup.setBackground(MENU_POPUP_BG);
					}

					int itemCount = m.getItemCount();
					for (int j = 0; j < itemCount; j++) {
						styleMenuTree(m.getItem(j));
					}
				}
			}
		}

		revalidate();
		repaint();
	}
}