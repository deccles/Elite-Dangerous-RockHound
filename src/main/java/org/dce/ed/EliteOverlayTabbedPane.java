package org.dce.ed;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BooleanSupplier;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;

import org.dce.ed.OverlayPreferences.MiningLimpetReminderMode;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.EliteLogFileLocator;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.FssDiscoveryScanEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent;
import org.dce.ed.logreader.event.StartJumpEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.logreader.event.SupercruiseExitEvent;
import org.dce.ed.market.GalacticAveragePrices;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.tts.PollyTtsCached;
import org.dce.ed.tts.TtsSprintf;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.dce.ed.ui.EdoUi;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;


/**
 * Custom transparent "tabbed pane" for the overlay.
 * Does not extend JTabbedPane to avoid opaque background painting.
 *
 * Main tabs: Route, System, Biology, Mining, Fleet Carrier (visibility from preferences). Nearby panel is kept in the card stack but has no tab button.
 */
public class EliteOverlayTabbedPane extends JPanel {

	private volatile Integer lastAutoBiologyBodyId;
	
	private static final long VALUABLE_MATERIAL_THRESHOLD_CREDITS = 2_000_000L;

	private static final String CARD_ROUTE = "ROUTE";
	private static final String CARD_SYSTEM = "SYSTEM";
	private static final String CARD_BIOLOGY = "BIOLOGY";
	private static final String CARD_MINING = "MINING";
	private static final String CARD_NEARBY = "NEARBY";
	private static final String CARD_FLEET_CARRIER = "FLEET_CARRIER";

	private static final int TAB_HOVER_DELAY_MS = 500;	private static final Color TAB_WHITE = EdoUi.Internal.WHITE_ALPHA_230;

	/** Selected tab fill (~half the luminance of {@link EdoUi.Internal#GRAY_180}). */
	private static final Color TAB_SELECTED_BG = new Color(92, 92, 92);

	// Restores the original "bigger" tab look (padding inside the outline)
	private static final Insets TAB_PADDING = new Insets(4, 10, 4, 10);

	
	private final BooleanSupplier hoverSwitchEnabled;
	
	private final CardLayout cardLayout;
	private final JPanel cardPanel;
	private final JPanel tabBar;

	private final RouteTabPanel routeTab;
	private final SystemTabPanel systemTab;
	private final BiologyTabPanel biologyTab;
	private final MiningTabPanel miningTab;
	private final NearbyTabPanel nearbyTab;
	private final FleetCarrierTabPanel fleetCarrierTab;

	private static final TtsSprintf tts = new TtsSprintf(new PollyTtsCached());

	private final GalacticAveragePrices galacticAvgPrices = GalacticAveragePrices.loadDefault();

	private long lastLimpetReminderMs;

	private JButton routeButton;
	private JButton systemButton;
	private JButton biologyButton;
	private JButton miningButton;
	/** Nearby tab content is kept for data/journals; no tab button (see preferences / future use). */
	private JButton nearbyButton;
	private JButton fleetCarrierButton;

	public EliteOverlayTabbedPane() {
		this(() -> true);
	}
	public EliteOverlayTabbedPane(BooleanSupplier hoverSwitchEnabled) {
		super(new BorderLayout());

		this.hoverSwitchEnabled = hoverSwitchEnabled;
		
		boolean opaque = !OverlayPreferences.overlayChromeRequestsTransparency();

		setOpaque(opaque);

		// ----- Tab bar (row of buttons) -----
		tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		tabBar.setOpaque(opaque);
		tabBar.setBackground(EdoUi.User.BACKGROUND);
		ButtonGroup group = new ButtonGroup();

		routeButton = createTabButton("Route");
		systemButton = createTabButton("System");
		biologyButton = createTabButton("Biology");
		miningButton = createTabButton("Mining");
		nearbyButton = null;
		fleetCarrierButton = createTabButton("Fleet Carrier");

		group.add(routeButton);
		group.add(systemButton);
		group.add(biologyButton);
		group.add(miningButton);
		group.add(fleetCarrierButton);

		tabBar.add(routeButton);
		tabBar.add(systemButton);
		tabBar.add(biologyButton);
		tabBar.add(miningButton);
		tabBar.add(fleetCarrierButton);

		// ----- Card area with the actual tab contents -----
		cardLayout = new CardLayout();

		cardPanel = new JPanel(cardLayout) {

		    private static final long serialVersionUID = 1L;

		    @Override
		    protected void paintComponent(Graphics g) {
		        Color bg = getBackground();
		        Graphics2D g2 = (Graphics2D) g.create();
		        try {
		            // Src + a color with alpha 0 leaves broken premultiplied RGB on some GPUs (neon green smear).
		            if (bg != null && bg.getAlpha() > 0) {
		                // Overwrite pixels (including alpha) to stop CardLayout swap ghosting.
		                g2.setComposite(AlphaComposite.Src);
		                g2.setColor(bg);
		                g2.fillRect(0, 0, getWidth(), getHeight());
		            } else if (OverlayPreferences.overlayChromeRequestsTransparency()) {
		                g2.setComposite(AlphaComposite.Clear);
		                g2.fillRect(0, 0, getWidth(), getHeight());
		            } else {
		                Color b = EdoUi.User.BACKGROUND;
		                g2.setComposite(AlphaComposite.SrcOver);
		                g2.setColor(new Color(b.getRed(), b.getGreen(), b.getBlue(), 255));
		                g2.fillRect(0, 0, getWidth(), getHeight());
		            }
		        } finally {
		            g2.dispose();
		        }

		        super.paintComponent(g);
		    }
		};

		cardPanel.setOpaque(opaque);
		cardPanel.setBackground(EdoUi.User.BACKGROUND);
		cardPanel.setPreferredSize(new Dimension(400, 1000));

		// Create tab content panels
		this.routeTab = new RouteTabPanel(hoverSwitchEnabled);
		this.systemTab = new SystemTabPanel();
		this.systemTab.setNearBodyChangedListener(this::handleNearBodyChanged);
		
		this.biologyTab = new BiologyTabPanel();
		this.biologyTab.setSystemTabPanel(systemTab);
		this.miningTab = new MiningTabPanel(galacticAvgPrices, this::isCurrentlyDocked);
		// Treat docking as the end of a mining "trip": when we transition to docked,
		// flush any pending mining gains and advance the run counter if needed.
		addDockedStateListener(docked -> {
			if (docked) {
				miningTab.onDocked();
			}
		});
		this.nearbyTab = new NearbyTabPanel(systemTab, hoverSwitchEnabled);
		this.fleetCarrierTab = new FleetCarrierTabPanel(hoverSwitchEnabled);

		cardPanel.add(routeTab, CARD_ROUTE);
		cardPanel.add(systemTab, CARD_SYSTEM);
		cardPanel.add(biologyTab, CARD_BIOLOGY);
		cardPanel.add(miningTab, CARD_MINING);
		cardPanel.add(nearbyTab, CARD_NEARBY);
		cardPanel.add(fleetCarrierTab, CARD_FLEET_CARRIER);

		applyTabButtonStyle(routeButton);
		applyTabButtonStyle(systemButton);
		applyTabButtonStyle(biologyButton);
		applyTabButtonStyle(miningButton);
		applyTabButtonStyle(fleetCarrierButton);
		// SystemTabPanel already refreshes cache in its constructor.
		// Avoid triggering a second startup load path.

		// Wire up buttons to show cards
		routeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				selectTab(CARD_ROUTE, routeButton);
			}
		});

		systemButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				selectTab(CARD_SYSTEM, systemButton);
			}
		});

		biologyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				selectTab(CARD_BIOLOGY, biologyButton);
			}
		});

		miningButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				selectTab(CARD_MINING, miningButton);
			}
		});

		fleetCarrierButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				selectTab(CARD_FLEET_CARRIER, fleetCarrierButton);
			}
		});

		// Hover-to-switch: resting over a tab for a short time activates it
		installHoverSwitch(routeButton, TAB_HOVER_DELAY_MS, () -> routeButton.doClick(), hoverSwitchEnabled);
		installHoverSwitch(systemButton, TAB_HOVER_DELAY_MS, () -> systemButton.doClick(), hoverSwitchEnabled);
		installHoverSwitch(biologyButton, TAB_HOVER_DELAY_MS, () -> biologyButton.doClick(), hoverSwitchEnabled);
		installHoverSwitch(miningButton, TAB_HOVER_DELAY_MS, () -> miningButton.doClick(), hoverSwitchEnabled);
		installHoverSwitch(fleetCarrierButton, TAB_HOVER_DELAY_MS, () -> fleetCarrierButton.doClick(), hoverSwitchEnabled);

		applyOverlayTabBarVisibility();
		selectFirstVisibleTab();

		add(tabBar, BorderLayout.NORTH);

		// Journal events are delivered by a single app-level listener in OverlayFrame that
		// calls processJournalEvent() on the current tabbed pane (avoids duplicate handling).

		// Start watcher that syncs tabs with in-game Galaxy/System map
		GuiFocusWatcher watcher = new GuiFocusWatcher(this);
		Thread watcherThread = new Thread(watcher, "ED-GuiFocusWatcher");
		watcherThread.setDaemon(true);
		watcherThread.start();

		add(cardPanel, BorderLayout.CENTER);

		// Drag & drop Spansh fleet-carrier JSON import (drop anywhere on the overlay).
		// Mouse pass-through mode typically prevents receiving drag events, so we decline drops there.
		TransferHandler fcDropHandler = new TransferHandler() {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean canImport(TransferSupport support) {
				if (support == null || !support.isDrop()) {
					return false;
				}

				// In pass-through mode, ignore drops (overlay often won't receive DnD events anyway).
				if (hoverSwitchEnabled != null && hoverSwitchEnabled.getAsBoolean()) {
					return false;
				}

				return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
			}

			@Override
			public boolean importData(TransferSupport support) {
				if (!canImport(support)) {
					return false;
				}

				try {
					@SuppressWarnings("unchecked")
					List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					if (files == null || files.isEmpty()) {
						return false;
					}

					Path dropped = null;
					for (File f : files) {
						if (f == null) {
							continue;
						}
						String name = f.getName();
						if (name != null && name.toLowerCase(Locale.US).endsWith(".json")) {
							dropped = f.toPath();
							break;
						}
					}
					if (dropped == null) {
						return false;
					}

					final Path droppedFinal = dropped;
					SwingUtilities.invokeLater(() -> {
						selectTab(CARD_FLEET_CARRIER, fleetCarrierButton);
						fleetCarrierTab.importSpanshFleetCarrierRouteFile(droppedFinal);
					});

					return true;
				} catch (Exception e) {
					e.printStackTrace();
					return false;
				}
			}
		};

		this.setTransferHandler(fcDropHandler);
		this.tabBar.setTransferHandler(fcDropHandler);
		this.cardPanel.setTransferHandler(fcDropHandler);
	}

	/**
	 * Process a journal event (called by the single app-level listener in OverlayFrame).
	 * This ensures exactly one handler runs per event regardless of pane rebuilds.
	 * <p>
	 * Threading: invoked on the same thread as {@link org.dce.ed.logreader.LiveJournalMonitor#dispatch}
	 * (the {@code Elite-LiveJournalMonitor} worker), not automatically on the EDT.
	 */
	public void processJournalEvent(EliteLogEvent event) {
		this.handleLogEvent(event);

		if (event instanceof ProspectedAsteroidEvent) {
			handleProspectedAsteroid((ProspectedAsteroidEvent) event);
		}

		if (event instanceof StartJumpEvent e) {
			miningTab.onStartJump(e);
		}
		if (event instanceof LocationEvent le) {
			miningTab.updateFromLocation(le);
		}
		if (event instanceof StatusEvent se) {
			miningTab.updateFromStatus(se);
		}

		if (event instanceof StatusEvent) {
			StatusEvent flagEvent = (StatusEvent) event;
			if (flagEvent.isFsdCharging()) {
				showRouteTabFromStatusWatcher();
			}
		}

		systemTab.handleLogEvent(event);
		routeTab.handleLogEvent(event);
		biologyTab.handleLogEvent(event);
		fleetCarrierTab.handleLogEvent(event);

		if (event instanceof FsdJumpEvent) {
			FsdJumpEvent e = (FsdJumpEvent) event;
			nearbyTab.onCurrentSystemChanged(e.getStarSystem(), e.getSystemAddress());
		}
	}

	public SystemTabPanel getSystemTabPanel() {
		return systemTab;
	}

	public RouteTabPanel getRouteTabPanel() {
		return routeTab;
	}

	public MiningTabPanel getMiningTabPanel() {
		return miningTab;
	}
	static LoadoutEvent loadoutEventx = null;

	private final CopyOnWriteArrayList<Consumer<Boolean>> dockedListeners = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<Runnable> loadoutChangeListeners = new CopyOnWriteArrayList<>();

	public void addLoadoutChangeListener(Runnable listener) {
		if (listener != null) {
			loadoutChangeListeners.add(listener);
		}
	}

	public boolean isCurrentlyDocked() {
		return systemTab != null && systemTab.getState() != null && systemTab.getState().isDocked();
	}

	public void addDockedStateListener(Consumer<Boolean> listener) {
		if (listener != null) {
			dockedListeners.add(listener);
		}
	}

	private void setCurrentlyDocked(boolean docked) {
		boolean previous = isCurrentlyDocked();
		if (previous == docked) {
			return;
		}
		systemTab.getState().setDocked(docked);
		if (docked) {
			miningTab.clearLastUndockTime();
		} else {
			miningTab.onUndocked();
		}
		for (Consumer<Boolean> c : dockedListeners) {
			try {
				c.accept(docked);
			} catch (Exception ignored) {
			}
		}
	}

	public static LoadoutEvent getLatestLoadout() {
		if (loadoutEventx == null) {
			Path journalDir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
			if (journalDir != null && Files.isDirectory(journalDir)) {
				try {
					EliteJournalReader r = new EliteJournalReader(journalDir);
					loadoutEventx = (LoadoutEvent) r.findMostRecentEvent(EliteEventType.LOADOUT, 8);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return loadoutEventx;
	}
	public void handleLogEvent(EliteLogEvent event) {
        if (event instanceof LoadoutEvent e) {
        	loadoutEventx = e;
        	for (Runnable r : loadoutChangeListeners) {
        		SwingUtilities.invokeLater(r);
        	}
        }

        if (event instanceof org.dce.ed.logreader.event.StatusEvent se) {
        	boolean wasDocked = isCurrentlyDocked();
        	setCurrentlyDocked(se.isDocked());
        	if (wasDocked && !se.isDocked()) {
        		SwingUtilities.invokeLater(() -> maybeRemindAboutLimpets());
        	}
        } else if (event instanceof org.dce.ed.logreader.event.LocationEvent le) {
        	setCurrentlyDocked(le.isDocked());
        }


		if (event.getType() == EliteEventType.UNDOCKED) {
			setCurrentlyDocked(false);
			SwingUtilities.invokeLater(() -> maybeRemindAboutLimpets());
		}

		if (event instanceof FsdJumpEvent e) {
			if (e.getDocked() == null || e.getDocked()) {
				showSystemTabFromStatusWatcher();
			}
		} else if (event instanceof FssDiscoveryScanEvent) {
			showSystemTabFromStatusWatcher();
		}

		if (event instanceof StartJumpEvent) {
			showRouteTabFromStatusWatcher();
		}
		if (event instanceof SupercruiseExitEvent e) {
		    miningTab.updateFromSupercruiseExit(e);
		    String bodyType = e.getBodyType();
		    if (bodyType != null && bodyType.contains("PlanetaryRing")) {
		        showMiningTabFromStatusWatcher();
		    }
		}

	}
	private void showMiningTabFromStatusWatcher() {
	    SwingUtilities.invokeLater(() -> selectTab(CARD_MINING, miningButton));
	}

	private void handleProspectedAsteroid(ProspectedAsteroidEvent event) {
		// Prospecting an asteroid means we're in space, not docked (enables CSV logging when undocked).
		setCurrentlyDocked(false);
		// Update Mining tab UI (always), regardless of whether announcements are enabled.
		SwingUtilities.invokeLater(() -> {
			try {
				miningTab.updateFromProspector(event);
			} catch (Exception e) {
				ExceptionReporting.report(e, "Prospector update");
			}
		});
	}


	private static double estimateTotalTons(String content) {
		if (content == null) {
			return OverlayPreferences.getMiningEstimateTonsMedium();
		}
		String c = content.trim().toLowerCase(Locale.US);
		if (c.equals("high")) {
			return OverlayPreferences.getMiningEstimateTonsHigh();
		}
		if (c.equals("low")) {
			return OverlayPreferences.getMiningEstimateTonsLow();
		}
		return OverlayPreferences.getMiningEstimateTonsMedium();
	}


	private static Set<String> parseMaterialList(String csv) {
		if (csv == null || csv.isBlank()) {
			return Set.of();
		}
		Set<String> out = new HashSet<>();
		Arrays.stream(csv.split(","))
		.map(String::trim)
		.filter(s -> !s.isBlank())
		.map(EliteOverlayTabbedPane::normalizeMaterialName)
		.forEach(out::add);
		return out;
	}

	/**
	 * Normalize material names so user input like "Low Temperature Diamonds" can
	 * match journal material keys like "$LowTemperatureDiamonds_Name;".
	 */
	private static String normalizeMaterialName(String s) {
		if (s == null) {
			return "";
		}

		String t = s.trim();
		if (t.startsWith("$")) {
			t = t.substring(1);
		}
		t = t.replace("_name", "");
		t = t.replace("_Name", "");
		t = t.replace(";", "");

		StringBuilder out = new StringBuilder();
		for (int i = 0; i < t.length(); i++) {
			char c = t.charAt(i);
			if (Character.isLetterOrDigit(c)) {
				out.append(Character.toLowerCase(c));
			}
		}
		return out.toString();
	}

	private static String toSpokenMaterialName(String raw) {
		if (raw == null || raw.isBlank()) {
			return "material";
		}

		String t = raw.trim();
		if (t.startsWith("$")) {
			t = t.substring(1);
		}
		t = t.replace("_Name", "").replace("_name", "").replace(";", "");

		// LowTemperatureDiamonds -> Low Temperature Diamonds
		t = t.replaceAll("(?<=[a-z])(?=[A-Z])", " ");
		t = t.replace('_', ' ');
		t = t.replaceAll("\\s+", " ").trim();
		return t;
	}

	/**
	 * Attach a generic hover handler to a button; when the mouse rests over
	 * the button for the given delay, the action is invoked on the EDT.
	 */
	private static void installHoverSwitch(JButton button, int delayMs, Runnable action, BooleanSupplier enabled) {
		TabHoverPoller.register(button, delayMs, action, enabled);
	}

	/**
	 * Global tab hover poller: periodically polls the global mouse position and,
	 * if it is resting on any registered tab button longer than the configured
	 * delay, invokes that tab's action (typically button.doClick()).
	 *
	 * This works even when the overlay is in OS pass-through mode because it
	 * does not depend on Swing mouse events.
	 */
	private static class TabHoverPoller implements ActionListener {

		private static final int POLL_INTERVAL_MS = 40;

		private static final List<Entry> entries = new ArrayList<>();
		private static final Timer pollTimer;

		static {
			TabHoverPoller listener = new TabHoverPoller();
			pollTimer = new Timer(POLL_INTERVAL_MS, listener);
			pollTimer.start();
		}

		private static class Entry {
			final JButton button;
			final int delayMs;
			final Runnable action;
			final BooleanSupplier enabled;

			long hoverStartMs = -1L;
			boolean firedForCurrentHover = false;

			Entry(JButton button, int delayMs, Runnable action, BooleanSupplier enabled) {
				this.button = button;
				this.delayMs = delayMs;
				this.action = action;
				this.enabled = enabled;
			}
		}

		static void register(JButton button, int delayMs, Runnable action, BooleanSupplier enabled) {
			entries.add(new Entry(button, delayMs, action, enabled));
		}


		@Override
		public void actionPerformed(ActionEvent e) {
			if (entries.isEmpty()) {
				return;
			}

			PointerInfo pointerInfo = MouseInfo.getPointerInfo();
			if (pointerInfo == null) {
				resetAll();
				return;
			}

			Point mouseOnScreen = pointerInfo.getLocation();
			long now = System.currentTimeMillis();

			for (Entry entry : entries) {
				if (entry.enabled != null && !entry.enabled.getAsBoolean()) {
					entry.hoverStartMs = -1L;
					entry.firedForCurrentHover = false;
					continue;
				}

				JButton button = entry.button;
				if (button == null || !button.isShowing()) {
					entry.hoverStartMs = -1L;
					entry.firedForCurrentHover = false;
					continue;
				}

				Point buttonLoc;
				try {
					buttonLoc = button.getLocationOnScreen();
				} catch (IllegalStateException ex) {
					entry.hoverStartMs = -1L;
					entry.firedForCurrentHover = false;
					continue;
				}

				Rectangle bounds = new Rectangle(
						buttonLoc.x,
						buttonLoc.y,
						button.getWidth(),
						button.getHeight()
						);

				if (bounds.contains(mouseOnScreen)) {
					if (entry.hoverStartMs < 0L) {
						entry.hoverStartMs = now;
						entry.firedForCurrentHover = false;
					} else if (!entry.firedForCurrentHover && now - entry.hoverStartMs >= entry.delayMs) {
						if (entry.action != null) {
							SwingUtilities.invokeLater(entry.action);
						}
						entry.firedForCurrentHover = true;
					}
				} else {
					entry.hoverStartMs = -1L;
					entry.firedForCurrentHover = false;
				}
			}
		}

		private static void resetAll() {
			for (Entry entry : entries) {
				entry.hoverStartMs = -1L;
				entry.firedForCurrentHover = false;
			}
		}
	}

	private JButton createTabButton(String text) {
		JButton button = new JButton(text);
		button.setUI(new BasicButtonUI());
		button.setFocusable(false);
		button.setFocusPainted(false);
		button.setFont(button.getFont().deriveFont(Font.BOLD, 11f));
		button.setContentAreaFilled(false);

		// Default: slightly translucent dark background when overlay is transparent.
		// Selected tab gets an opaque background in applyTabButtonStyle to prevent
		// adjacent tab text from peeking through (z-order/alpha bleed).
		button.setOpaque(!OverlayPreferences.overlayChromeRequestsTransparency());
		button.setBackground(EdoUi.Internal.DARK_ALPHA_220);

		applyTabButtonStyle(button);
		return button;
	}

	private javax.swing.border.Border createTabBorder(Color c) {
		return javax.swing.BorderFactory.createCompoundBorder(
				javax.swing.BorderFactory.createLineBorder(c, 1, true),
				javax.swing.BorderFactory.createEmptyBorder(
						TAB_PADDING.top,
						TAB_PADDING.left,
						TAB_PADDING.bottom,
						TAB_PADDING.right
						)
				);
	}

	private void applyOverlayTabBarVisibility() {
		boolean r = OverlayPreferences.isOverlayTabRouteVisible();
		boolean s = OverlayPreferences.isOverlayTabSystemVisible();
		boolean b = OverlayPreferences.isOverlayTabBiologyVisible();
		boolean m = OverlayPreferences.isOverlayTabMiningVisible();
		boolean f = OverlayPreferences.isOverlayTabFleetCarrierVisible();
		if (!r && !s && !b && !m && !f) {
			r = s = b = m = f = true;
		}
		if (routeButton != null) {
			routeButton.setVisible(r);
		}
		if (systemButton != null) {
			systemButton.setVisible(s);
		}
		if (biologyButton != null) {
			biologyButton.setVisible(b);
		}
		if (miningButton != null) {
			miningButton.setVisible(m);
		}
		if (fleetCarrierButton != null) {
			fleetCarrierButton.setVisible(f);
		}
	}

	/**
	 * Selects the first tab that is visible in the bar (order: Route … Fleet Carrier).
	 */
	private void selectFirstVisibleTab() {
		JButton[] buttons = { routeButton, systemButton, biologyButton, miningButton, fleetCarrierButton };
		String[] cards = { CARD_ROUTE, CARD_SYSTEM, CARD_BIOLOGY, CARD_MINING, CARD_FLEET_CARRIER };
		for (int i = 0; i < buttons.length; i++) {
			JButton b = buttons[i];
			if (b != null && b.isVisible()) {
				selectTab(cards[i], b);
				return;
			}
		}
		cardLayout.show(cardPanel, CARD_SYSTEM);
	}

	private void selectTab(String cardName, JButton selectedButton) {
		if (selectedButton != null && !selectedButton.isVisible()) {
			selectFirstVisibleTab();
			return;
		}
		if (routeButton != null) {
			routeButton.setSelected(selectedButton == routeButton);
		}
		if (systemButton != null) {
			systemButton.setSelected(selectedButton == systemButton);
		}
		if (biologyButton != null) {
			biologyButton.setSelected(selectedButton == biologyButton);
		}
		if (miningButton != null) {
			miningButton.setSelected(selectedButton == miningButton);
		}
		if (nearbyButton != null) {
			nearbyButton.setSelected(selectedButton == nearbyButton);
		}
		if (fleetCarrierButton != null) {
			fleetCarrierButton.setSelected(selectedButton == fleetCarrierButton);
		}

		applyTabButtonStyle(routeButton);
		applyTabButtonStyle(systemButton);
		applyTabButtonStyle(biologyButton);
		applyTabButtonStyle(miningButton);
		applyTabButtonStyle(fleetCarrierButton);

		cardLayout.show(cardPanel, cardName);
	}

	private void applyTabButtonStyle(JButton button) {
		if (button == null) {
			return;
		}

		Color c = button.isSelected() ? TAB_WHITE : EdoUi.Internal.MAIN_TEXT_ALPHA_220;
		boolean passThrough = hoverSwitchEnabled != null && hoverSwitchEnabled.getAsBoolean();
		boolean forceSolidTabBackground = !passThrough;

		// Selected tab: force opaque background so adjacent tab labels don't show through
		// (avoids "logy" / "ining" ghosting when overlay is transparent).
		if (button.isSelected()) {
			button.setOpaque(true);
			button.setBackground(TAB_SELECTED_BG);
		} else {
			if (forceSolidTabBackground) {
				// Match Colors → Background instead of fixed PANEL_BG; avoids Windows LAF tint clashes.
				button.setOpaque(true);
				Color base = EdoUi.User.BACKGROUND;
				button.setBackground(new Color(base.getRed(), base.getGreen(), base.getBlue(), 255));
			} else {
				button.setOpaque(!OverlayPreferences.overlayChromeRequestsTransparency());
				button.setBackground(EdoUi.Internal.DARK_ALPHA_220);
			}
		}

		// This restores size/padding compared to a bare LineBorder.
		button.setMargin(TAB_PADDING);
		button.setForeground(c);
		button.setBorder(createTabBorder(c));
	}

	private void showRouteTabFromStatusWatcher() {
		SwingUtilities.invokeLater(() -> selectTab(CARD_ROUTE, routeButton));
	}

	private void showSystemTabFromStatusWatcher() {
		SwingUtilities.invokeLater(() -> selectTab(CARD_SYSTEM, systemButton));
	}

	private void handleNearBodyChanged(BodyInfo nearBody) {
	    if (nearBody == null) {
	        lastAutoBiologyBodyId = null;
	        return;
	    }

	    int bodyId = nearBody.getBodyId();
	    if (lastAutoBiologyBodyId != null && lastAutoBiologyBodyId.intValue() == bodyId) {
	        return;
	    }

	    if (!nearBody.isLandable()) {
	        return;
	    }

	    if (!hasAtmosphere(nearBody.getAtmosphere())) {
	        return;
	    }

	    lastAutoBiologyBodyId = bodyId;
	    SwingUtilities.invokeLater(() -> selectTab(CARD_BIOLOGY, biologyButton));
	}

	private boolean hasAtmosphere(String atmosphere) {
	    if (atmosphere == null) {
	        return false;
	    }

	    String a = atmosphere.trim();
	    if (a.isEmpty()) {
	        return false;
	    }

	    String l = a.toLowerCase(Locale.ROOT);
	    if (l.equals("none") || l.equals("no atmosphere") || l.contains("no atmosphere")) {
	        return false;
	    }
	    if (l.equals("unknown")) {
	        return false;
	    }

	    return true;
	}

	/**
	 * Watches Elite Dangerous Status.json and switches tabs when the player
	 * opens the Galaxy Map (Route tab) or System Map (System tab).
	 */
	private static class GuiFocusWatcher implements Runnable {

		private static final long POLL_INTERVAL_MS = 200L;

		private final EliteOverlayTabbedPane parent;
		private final Path statusPath;
		private final Gson gson = new Gson();

		private volatile boolean running = true;
		private int lastGuiFocus = -1;

		GuiFocusWatcher(EliteOverlayTabbedPane parent) {
			this.parent = parent;

			String home = System.getProperty("user.home");
			this.statusPath = Path.of(
					home,
					"Saved Games",
					"Frontier Developments",
					"Elite Dangerous",
					"Status.json");
		}

		@Override
		public void run() {
			while (running) {
				try {
					pollOnce();
					Thread.sleep(POLL_INTERVAL_MS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				} catch (IOException e) {
					try {
						Thread.sleep(500L);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		}

		private void pollOnce() throws IOException {
			if (!Files.exists(statusPath)) {
				return;
			}

			try (Reader reader = Files.newBufferedReader(statusPath, StandardCharsets.UTF_8)) {
				JsonObject root = gson.fromJson(reader, JsonObject.class);
				if (root == null || !root.has("GuiFocus")) {
					return;
				}

				int guiFocus = root.get("GuiFocus").getAsInt();
				if (guiFocus != lastGuiFocus) {
					handleGuiFocusChange(guiFocus);
					lastGuiFocus = guiFocus;
				}
			}
		}

		private void handleGuiFocusChange(int guiFocus) {
			// 6 = Galaxy Map -> Route tab
			if (guiFocus == 6) {
				parent.showRouteTabFromStatusWatcher();
			}
			// 7 = System Map -> System tab
			else if (guiFocus == 7) {
				parent.showSystemTabFromStatusWatcher();
			}
		}
	}

	private static class HoverSwitchHandler extends MouseAdapter {

		private final Timer hoverTimer;
		private final Runnable action;

		HoverSwitchHandler(int delayMs, Runnable action) {
			this.action = action;
			this.hoverTimer = new Timer(delayMs, e -> {
				if (this.action != null) {
					this.action.run();
				}
			});
			this.hoverTimer.setRepeats(false);
		}

		@Override
		public void mouseEntered(MouseEvent e) {
			hoverTimer.restart();
		}

		@Override
		public void mouseMoved(MouseEvent e) {
			hoverTimer.restart();
		}

		@Override
		public void mouseExited(MouseEvent e) {
			hoverTimer.stop();
		}

		@Override
		public void mousePressed(MouseEvent e) {
			hoverTimer.stop();
		}
	}


	@Override
	protected void paintComponent(Graphics g) {
		// FlowLayout tab gaps and non-opaque children leave holes; decorated JFrames + CLEAR show lime artifacts.
		if (!isOpaque() && !OverlayPreferences.overlayChromeRequestsTransparency()) {
			Graphics2D g2 = (Graphics2D) g.create();
			try {
				Color b = EdoUi.User.BACKGROUND;
				g2.setColor(new Color(b.getRed(), b.getGreen(), b.getBlue(), 255));
				g2.fillRect(0, 0, getWidth(), getHeight());
			} finally {
				g2.dispose();
			}
		}
		super.paintComponent(g);
	}

	public void applyOverlayTransparency(boolean transparent) {
		applyOverlayBackground(transparent ? EdoUi.Internal.TRANSPARENT : Color.BLACK, transparent);
	}

	public void applyOverlayBackground(Color bgWithAlpha, boolean treatAsTransparent) {
		boolean opaque = !treatAsTransparent;

		setOpaque(opaque);
		setBackground(bgWithAlpha);

		tabBar.setOpaque(opaque);
		tabBar.setBackground(bgWithAlpha);

		cardPanel.setOpaque(opaque);
		cardPanel.setBackground(bgWithAlpha);

		// Re-apply tab button opacity/background so selected tab stays opaque and no text bleeds through.
		applyTabButtonStyle(routeButton);
		applyTabButtonStyle(systemButton);
		applyTabButtonStyle(biologyButton);
		applyTabButtonStyle(miningButton);
		applyTabButtonStyle(fleetCarrierButton);

		if (nearbyTab != null) {
			nearbyTab.applyOverlayBackground(bgWithAlpha);
		}
		if (fleetCarrierTab != null) {
			fleetCarrierTab.applyOverlayBackground(bgWithAlpha, treatAsTransparent);
		}

		revalidate();
		repaint();
	}
	public static boolean shouldShowLowLimpetWarning(boolean docked, CargoMonitor.Snapshot snap) {
		// Only while docked.
		if (!docked) {
			return false;
		}
		if (!OverlayPreferences.isMiningLowLimpetReminderEnabled()) {
			return false;
		}

		LoadoutEvent loadout = getLatestLoadout();
		if (!hasMiningEquipment(loadout)) {
			return false;
		}

		if (snap == null) {
			snap = CargoMonitor.getInstance().getSnapshot();
		}
		int numLimpets = (snap == null) ? 0 : snap.getLimpetCount();

		if (OverlayPreferences.getMiningLowLimpetReminderMode() == MiningLimpetReminderMode.COUNT) {
			return numLimpets < OverlayPreferences.getMiningLowLimpetReminderThreshold();
		}

		Integer cargoCapacity = (loadout == null) ? null : loadout.getCargoCapacity();
		if (cargoCapacity == null || cargoCapacity <= 0) {
			return false;
		}

		double percentage = (numLimpets * 100.0) / cargoCapacity;
		return percentage < OverlayPreferences.getMiningLowLimpetReminderThresholdPercent();
	}

	public static void maybeRemindAboutLimpets() {
		// Avoid spamming if multiple events fire close together.
		long now = System.currentTimeMillis();
//		if (now - lastLimpetReminderMs < 60_000L) {
//			return;
//		}
		if (!OverlayPreferences.isSpeechEnabled()) {
			return;
		}
		if (!OverlayPreferences.isMiningLowLimpetReminderEnabled()) {
			return;
		}

		CargoMonitor.Snapshot snap = CargoMonitor.getInstance().getSnapshot();
		int numLimpets = (snap == null) ? 0 : snap.getLimpetCount();

		// Use loadout from journal if not yet set (e.g. Undocked fired before Loadout when switching ships)
		LoadoutEvent loadout = getLatestLoadout();
		if (loadout == null) {
			Path journalDir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
			if (journalDir != null && Files.isDirectory(journalDir)) {
				try {
					EliteJournalReader r = new EliteJournalReader(journalDir);
					loadout = (LoadoutEvent) r.findMostRecentEvent(EliteEventType.LOADOUT, 1);
				} catch (IOException e) {
					// ignore; we'll skip the reminder
				}
			}
		}

		boolean lowLimpets = false;
		if (OverlayPreferences.getMiningLowLimpetReminderMode() == MiningLimpetReminderMode.COUNT) {
			lowLimpets = numLimpets < OverlayPreferences.getMiningLowLimpetReminderThreshold();
		} else {
			Integer cargoCapacity = (loadout == null) ? null : loadout.getCargoCapacity();

			if (cargoCapacity == null || cargoCapacity <= 0) {
				// Without CargoCapacity, the percent threshold is meaningless.
				return;
			}
			double percentage = (numLimpets * 100.0) / cargoCapacity;

			lowLimpets = percentage < OverlayPreferences.getMiningLowLimpetReminderThresholdPercent();
		}

		if (!hasMiningEquipment(loadout)) {
			System.out.println("Not a mining ship");
			return;
		}

		if (!lowLimpets) {
			System.out.println("Not low limpets");
			return;
		}
		tts.speakf("Did you forget your limpets again commander?");
	}

	private static JsonObject readJsonObject(Path file) {
		if (file == null) {
			return null;
		}
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement el = JsonParser.parseReader(r);
			if (el != null && el.isJsonObject()) {
				return el.getAsJsonObject();
			}
			return null;
		} catch (Exception e) {
			return null;
		}
	}
	
	private static int getLimpetCount(JsonObject cargo) {
		if (cargo == null) {
			return 0;
		}

		JsonArray inv = null;
		if (cargo.has("Inventory") && cargo.get("Inventory").isJsonArray()) {
			inv = cargo.getAsJsonArray("Inventory");
		} else if (cargo.has("inventory") && cargo.get("inventory").isJsonArray()) {
			inv = cargo.getAsJsonArray("inventory");
		}

		if (inv == null) {
			return 0;
		}

		for (JsonElement e : inv) {
			if (e == null || !e.isJsonObject()) {
				continue;
			}

			JsonObject o = e.getAsJsonObject();

			String name = null;
			if (o.has("Name") && !o.get("Name").isJsonNull()) {
				try {
					name = o.get("Name").getAsString();
				} catch (Exception ignored) {
				}
			} else if (o.has("name") && !o.get("name").isJsonNull()) {
				try {
					name = o.get("name").getAsString();
				} catch (Exception ignored) {
				}
			}

			if (name == null || !name.equalsIgnoreCase("drones")) {
				continue;
			}

			if (o.has("Count") && !o.get("Count").isJsonNull()) {
				try {
					return (int) o.get("Count").getAsLong();
				} catch (Exception ignored) {
				}
			} else if (o.has("count") && !o.get("count").isJsonNull()) {
				try {
					return (int) o.get("count").getAsLong();
				} catch (Exception ignored) {
				}
			}

			return 0;
		}

		return 0;
	}


public static boolean hasMiningEquipment(LoadoutEvent loadout) {
	if (loadout == null) {
		return false;
	}

	List<LoadoutEvent.Module> modules = loadout.getModules();
	if (modules == null || modules.isEmpty()) {
		return false;
	}

	// Conservative keyword match on module item names.
	String[] miningKeywords = new String[] {
			"mining",
			"abrasion",
			"seismic",
			"subsurf",
			"displacement",
	};

	for (LoadoutEvent.Module m : modules) {
		if (m == null) {
			continue;
		}

		String item = m.getItem();
		if (item == null || item.isBlank()) {
			continue;
		}

		String norm = item.toLowerCase(Locale.US);
		for (String kw : miningKeywords) {
			if (norm.contains(kw)) {
				System.out.println("is mining ship because it has " + norm);
				return true;
			}
		}
	}

	return false;
}

	public void applyUiFontPreferences() {
		systemTab.applyUiFontPreferences();
		routeTab.applyUiFontPreferences();
		fleetCarrierTab.applyUiFontPreferences();
		biologyTab.applyUiFontPreferences();
		miningTab.applyUiFontPreferences();
		nearbyTab.applyUiFontPreferences();
		revalidate();
		repaint();
	}

	public void applyUiFont(Font font) {
		systemTab.applyUiFont(font);
		routeTab.applyUiFont(font);
		fleetCarrierTab.applyUiFont(font);
		biologyTab.applyUiFont(font);
		miningTab.applyUiFont(font);
		nearbyTab.applyUiFont(font);
		revalidate();
		repaint();
	}


}