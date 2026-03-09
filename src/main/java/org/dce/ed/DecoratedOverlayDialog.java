package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.Box;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.dce.ed.util.AppIconUtil;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;
import org.dce.ed.ui.EdoUi;
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

	// Menu styling
	private static final Color MENU_BG = EdoUi.Internal.DARK_22;
	private static final Color MENU_FG = EdoUi.Internal.MENU_ACCENT;
	private static final Color MENU_POPUP_BG = EdoUi.Internal.DARK_14;
	private static final Color MENU_POPUP_FG = EdoUi.Internal.MENU_FG_LIGHT;

	private final OverlayContentPanel contentPanel;
	private final String clientKey;

	private Runnable onRequestSwitchToPassThrough;

	private JMenuBar menuBar;
	private JLabel statusLabel;
	private volatile boolean lastDocked;
	private volatile CargoMonitor.Snapshot lastCargoSnapshot;
	private String lastRightStatusText = "";

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

		getContentPane().add(contentPanel, BorderLayout.CENTER);
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
			lastDocked = tp.isCurrentlyDocked();
			tp.addDockedStateListener(docked -> {
				lastDocked = docked;
				updateStatusLabel();
			});
		}

		CargoMonitor.getInstance().addListener(snap -> {
			lastCargoSnapshot = snap;
			updateStatusLabel();
		});

		// Initial paint.
		lastCargoSnapshot = CargoMonitor.getInstance().getSnapshot();
		updateStatusLabel();
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

	private boolean shouldShowLowLimpetWarning() {
		return EliteOverlayTabbedPane.shouldShowLowLimpetWarning(lastDocked, lastCargoSnapshot);
	}

	private JMenuBar createMenuBar() {
		JMenuBar bar = new JMenuBar();
		bar.setOpaque(true);
		bar.setBackground(MENU_BG);
		bar.setBorder(new EmptyBorder(2, 6, 2, 6));

		JMenu overlayMenu = new JMenu("Menu");
		overlayMenu.setForeground(MENU_FG);

		JMenuItem prefs = new JMenuItem("Preferences...");
		styleMenuItem(prefs);
		prefs.addActionListener(e -> {
			PreferencesDialog dialog = new PreferencesDialog(this, clientKey);
			dialog.setVisible(true);
		});
		overlayMenu.add(prefs);

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

        if (contentPanel != null) {
            contentPanel.rebuildTabbedPane();
        }

        repaint();
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
		int rgb = OverlayPreferences.getNormalBackgroundRgb();
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

		// Push the translucent background into your content panel (this is the important part).
		// If OverlayContentPanel already propagates this to sub-panels, you're done.
		contentPanel.applyOverlayBackground(bg, alpha == 0);

		// For the frame content pane itself, keep it opaque black so the alpha blending has a base.
		// (We don't want the default LAF panel gray bleeding in.)
		getContentPane().setBackground(Color.black);

		// Keep the menu bar dark regardless.
		if (menuBar != null) {
			menuBar.setOpaque(true);
			menuBar.setBackground(MENU_BG);
			menuBar.setForeground(MENU_FG);

			for (int i = 0; i < menuBar.getMenuCount(); i++) {
				JMenu m = menuBar.getMenu(i);
				if (m != null) {
					m.setForeground(MENU_FG);

					JPopupMenu popup = m.getPopupMenu();
					if (popup != null) {
						popup.setOpaque(true);
						popup.setBackground(MENU_POPUP_BG);
					}

					int itemCount = m.getItemCount();
					for (int j = 0; j < itemCount; j++) {
						JMenuItem it = m.getItem(j);
						if (it != null) {
							styleMenuItem(it);
						}
					}
				}
			}
		}

		revalidate();
		repaint();
	}
}