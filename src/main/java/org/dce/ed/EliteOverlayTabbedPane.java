package org.dce.ed;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.IllegalComponentStateException;
import java.awt.Insets;
import java.awt.Point;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.UIManager;

import org.dce.ed.exec.ExecTriggerService;
import org.dce.ed.exec.placeholder.ExecPlaceholderContext;
import org.dce.ed.exec.placeholder.CommanderSnapshot;
import org.dce.ed.mining.ProspectorLogBackendFactory;
import org.dce.ed.OverlayPreferences.MiningLimpetReminderMode;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.OwnedFleetCarrierTracker;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.CarrierJumpRequestEvent;
import org.dce.ed.logreader.event.CarrierLocationEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.FssDiscoveryScanEvent;
import org.dce.ed.logreader.event.FsdTargetEvent;
import org.dce.ed.logreader.event.LoadGameEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.dce.ed.logreader.event.SetUserShipNameEvent;
import org.dce.ed.engineering.EngineeringLoadoutExperimentalPatch;
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
import org.dce.ed.ui.ScrollableTabBar;
import org.dce.ed.ui.TransparentViewportUI;
import org.dce.ed.ui.tabdock.OverlayTabId;
import org.dce.ed.ui.tabdock.TabDockHost;
import org.dce.ed.ui.tabdock.TabDockingController;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;


/**
 * Custom transparent "tabbed pane" for the overlay.
 * Does not extend JTabbedPane to avoid opaque background painting.
 *
 * Main tabs: Route, System, ExoBio, Mining, Fleet Carrier, Control Panel (visibility from preferences). Nearby panel is kept in the card stack but has no tab button.
 * Tabs can be dragged into floating windows via {@link TabDockingController}.
 */
public class EliteOverlayTabbedPane extends JPanel implements TabDockHost {

	private static final EliteLogParser STATUS_SNAPSHOT_PARSER = new EliteLogParser();

	private volatile Integer lastAutoBiologyBodyId;
	
	private static final long VALUABLE_MATERIAL_THRESHOLD_CREDITS = 2_000_000L;

	private static final String CARD_ROUTE = "ROUTE";
	private static final String CARD_SYSTEM = "SYSTEM";
	private static final String CARD_BIOLOGY = "BIOLOGY";
	private static final String CARD_MINING = "MINING";
	private static final String CARD_MISSIONS = "MISSIONS";
	private static final String CARD_COMBAT = "COMBAT";
	private static final String CARD_NEARBY = "NEARBY";
	private static final String CARD_FLEET_CARRIER = "FLEET_CARRIER";
	private static final String CARD_ENGINEERING = "ENGINEERING";
	private static final String CARD_CONTROL_PANEL = "CONTROL_PANEL";

	private static final int TAB_HOVER_DELAY_MS = 500;	private static final Color TAB_WHITE = EdoUi.Internal.WHITE_ALPHA_230;

	/** Selected tab fill (~half the luminance of {@link EdoUi.Internal#GRAY_180}). */
	private static final Color TAB_SELECTED_BG = new Color(92, 92, 92);

	private static final Insets TAB_PADDING = new Insets(2, 6, 2, 6);

	
	private final BooleanSupplier hoverSwitchEnabled;
	
	private final CardLayout cardLayout;
	private final JPanel cardPanel;
	private final ScrollableTabBar scrollableTabBar;

	private final RouteTabPanel routeTab;
	private final SystemTabPanel systemTab;
	private final BiologyTabPanel biologyTab;
	private final MiningTabPanel miningTab;
	private final MissionsTabPanel missionsTab;
	private final CombatTabPanel combatTab;
	private final NearbyTabPanel nearbyTab;
	private final OwnedFleetCarrierTracker ownedFleetCarrierTracker;
	private final FleetCarrierTabPanel fleetCarrierTab;
	private final ControlPanelTabPanel controlPanelTab;
	private final EngineeringTabPanel engineeringTab;

	private JButton missionsButton;
	private JButton combatButton;
	private JButton engineeringButton;
	private JButton controlPanelButton;

	private volatile String lastKnownSystemName;
	private volatile String lastKnownStationName;

	/** Current card name (same values as {@code CARD_*}) for pass-through wheel routing. */
	private volatile String visibleCardName = CARD_SYSTEM;

	/**
	 * After {@code CarrierStats} (owner opened carrier management), treat the next galaxy map open as carrier routing
	 * if {@code GuiFocus} was right panel (1) or station services (5). TTL avoids stale matches.
	 */
	private static final long CARRIER_STATS_GALAXY_MAP_LATCH_MS = TimeUnit.MINUTES.toMillis(12);

	private volatile long carrierStatsGalaxyMapLatchUntilMs;

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

	private ExecTriggerService execTriggerService;
	private ExecPlaceholderContext execPlaceholderContext;

	private TabDockingController tabDockingController;

	public EliteOverlayTabbedPane() {
		this(() -> true);
	}
	public EliteOverlayTabbedPane(BooleanSupplier hoverSwitchEnabled) {
		super(new BorderLayout());

		this.hoverSwitchEnabled = hoverSwitchEnabled;
		
		boolean opaque = !OverlayPreferences.overlayChromeRequestsTransparency(this);

		setOpaque(opaque);

		// ----- Tab bar (row of buttons, horizontal scroll when overflow) -----
		scrollableTabBar = new ScrollableTabBar(hoverSwitchEnabled, opaque);
		JPanel tabBar = scrollableTabBar.getTabStrip();
		// No ButtonGroup: selection is per dock (main strip + each floating window keeps its own
		// highlighted tab); a group would force a single selected tab across all windows.

		routeButton = createTabButton("Route");
		systemButton = createTabButton("System");
		biologyButton = createTabButton("ExoBio");
		miningButton = createTabButton("Mining");
		missionsButton = createTabButton("Missions");
		combatButton = createTabButton("Combat");
		nearbyButton = null;
		fleetCarrierButton = createTabButton("Fleet Carrier");
		engineeringButton = createTabButton("Engineering");
		controlPanelButton = createTabButton("Control Panel");

		tabBar.add(routeButton);
		tabBar.add(systemButton);
		tabBar.add(biologyButton);
		tabBar.add(miningButton);
		tabBar.add(missionsButton);
		tabBar.add(combatButton);
		tabBar.add(fleetCarrierButton);
		tabBar.add(engineeringButton);
		tabBar.add(controlPanelButton);

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
		            } else if (OverlayPreferences.overlayChromeRequestsTransparency(this)) {
		                // Alpha-0 bg: still honor Preferences % (CLEAR only at 100% transparent).
		                TransparentViewportUI.fillSeeThroughChrome(g2, 0, 0, getWidth(), getHeight());
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
		// Patched Loadouts after EngineerCraft (no fresh journal Loadout) must refresh the fuel gauge.
		addLoadoutChangeListener(routeTab::refreshShipFuelProfileFromLatestLoadout);
		this.systemTab = new SystemTabPanel();
		this.systemTab.setNearBodyChangedListener(this::handleNearBodyChanged);
		// Prefer System tab position so Route markers cannot lag one hop behind after FSDJump.
		this.routeTab.setLiveSystemStateSupplier(systemTab::getState);
		
		this.biologyTab = new BiologyTabPanel();
		this.biologyTab.setSystemTabPanel(systemTab);
		this.miningTab = new MiningTabPanel(
				galacticAvgPrices,
				this::isCurrentlyDocked,
				new TtsSprintf(new PollyTtsCached()),
				ProspectorLogBackendFactory::create,
				systemTab::getState,
				() -> CARD_MINING.equals(visibleCardName));
		LoadoutEvent initialLoadout = getLatestLoadout();
		if (initialLoadout != null && initialLoadout.getShip() != null && !initialLoadout.getShip().isBlank()) {
			miningTab.updateCurrentShipType(initialLoadout.getShip());
		}
		NpcCrewTracker.getInstance().bootstrapFromSession(initialLoadout);
		// Treat docking as the end of a mining "trip": when we transition to docked,
		// flush any pending mining gains and advance the run counter if needed.
		addDockedStateListener(docked -> {
			if (docked) {
				miningTab.onDocked();
			}
		});
		this.nearbyTab = new NearbyTabPanel(systemTab, hoverSwitchEnabled);
        this.missionsTab = new MissionsTabPanel(
				hoverSwitchEnabled,
				this::isCurrentlyDocked,
				() -> {
					// Prefer System tab state — lastKnownSystemName can lag if Location hasn't fired yet.
					var state = systemTab.getState();
					if (state != null) {
						String n = state.getSystemName();
						if (n != null && !n.isBlank()) {
							return n;
						}
					}
					return lastKnownSystemName;
				},
				() -> lastKnownStationName,
				() -> {
					var state = systemTab.getState();
					return state != null ? state.getStarPos() : null;
				});
		miningTab.setMissionTracker(missionsTab.getTracker(), hoverSwitchEnabled);
		this.combatTab = new CombatTabPanel(hoverSwitchEnabled);
		this.combatTab.setMissionTracker(missionsTab.getTracker());
		this.ownedFleetCarrierTracker = new OwnedFleetCarrierTracker();
		this.fleetCarrierTab = new FleetCarrierTabPanel(hoverSwitchEnabled, ownedFleetCarrierTracker);
		this.controlPanelTab = new ControlPanelTabPanel(hoverSwitchEnabled);
		this.engineeringTab = new EngineeringTabPanel(hoverSwitchEnabled);

		cardPanel.add(routeTab, CARD_ROUTE);
		cardPanel.add(systemTab, CARD_SYSTEM);
		cardPanel.add(biologyTab, CARD_BIOLOGY);
		cardPanel.add(miningTab, CARD_MINING);
		cardPanel.add(missionsTab, CARD_MISSIONS);
		cardPanel.add(combatTab, CARD_COMBAT);
		cardPanel.add(nearbyTab, CARD_NEARBY);
		cardPanel.add(fleetCarrierTab, CARD_FLEET_CARRIER);
		cardPanel.add(engineeringTab, CARD_ENGINEERING);
		cardPanel.add(controlPanelTab, CARD_CONTROL_PANEL);

		applyTabButtonStyle(routeButton);
		applyTabButtonStyle(systemButton);
		applyTabButtonStyle(biologyButton);
		applyTabButtonStyle(miningButton);
		applyTabButtonStyle(missionsButton);
		applyTabButtonStyle(combatButton);
		applyTabButtonStyle(fleetCarrierButton);
		applyTabButtonStyle(engineeringButton);
		applyTabButtonStyle(controlPanelButton);
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

		missionsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				selectTab(CARD_MISSIONS, missionsButton);
			}
		});

		combatButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				selectTab(CARD_COMBAT, combatButton);
			}
		});

		fleetCarrierButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				selectTab(CARD_FLEET_CARRIER, fleetCarrierButton);
			}
		});

		engineeringButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				selectTab(CARD_ENGINEERING, engineeringButton);
			}
		});

		controlPanelButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				selectTab(CARD_CONTROL_PANEL, controlPanelButton);
			}
		});

		// Hover-to-switch: resting over a tab for a short time activates it
		installHoverSwitch(routeButton, TAB_HOVER_DELAY_MS, () -> routeButton.doClick(), hoverSwitchEnabled);
		installHoverSwitch(systemButton, TAB_HOVER_DELAY_MS, () -> systemButton.doClick(), hoverSwitchEnabled);
		installHoverSwitch(biologyButton, TAB_HOVER_DELAY_MS, () -> biologyButton.doClick(), hoverSwitchEnabled);
		installHoverSwitch(miningButton, TAB_HOVER_DELAY_MS, () -> miningButton.doClick(), hoverSwitchEnabled);
		installHoverSwitch(missionsButton, TAB_HOVER_DELAY_MS, () -> missionsButton.doClick(), hoverSwitchEnabled);
		installHoverSwitch(combatButton, TAB_HOVER_DELAY_MS, () -> combatButton.doClick(), hoverSwitchEnabled);
		installHoverSwitch(fleetCarrierButton, TAB_HOVER_DELAY_MS, () -> fleetCarrierButton.doClick(), hoverSwitchEnabled);
		installHoverSwitch(engineeringButton, TAB_HOVER_DELAY_MS, () -> engineeringButton.doClick(), hoverSwitchEnabled);
		installHoverSwitch(controlPanelButton, TAB_HOVER_DELAY_MS, () -> controlPanelButton.doClick(), hoverSwitchEnabled);

		applyOverlayTabBarVisibility();
		selectFirstVisibleTab();
		maybeSelectMiningTabFromStartupJournal();
		maybeSelectBiologyTabFromStartupStatus();

		add(scrollableTabBar, BorderLayout.NORTH);

		// Journal events are delivered by a single app-level listener in OverlayFrame that
		// calls processJournalEvent() on the current tabbed pane (avoids duplicate handling).

		// Start watcher that syncs tabs with in-game Galaxy/System map
		GuiFocusWatcher watcher = new GuiFocusWatcher(this);
		Thread watcherThread = new Thread(watcher, "ED-GuiFocusWatcher");
		watcherThread.setDaemon(true);
		watcherThread.start();

		add(cardPanel, BorderLayout.CENTER);

		// Drag & drop Spansh fleet-carrier JSON/CSV import (drop anywhere on the overlay).
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
						if (name != null) {
							String lower = name.toLowerCase(Locale.US);
							if (lower.endsWith(".json") || lower.endsWith(".csv")) {
								dropped = f.toPath();
								break;
							}
						}
					}
					if (dropped == null) {
						return false;
					}

					final Path droppedFinal = dropped;
					SwingUtilities.invokeLater(() -> {
						if (OverlayPreferences.isAutoSwitchFleetCarrierOnJsonDrop()) {
							selectTab(CARD_FLEET_CARRIER, fleetCarrierButton);
						}
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
		this.scrollableTabBar.setTransferHandler(fcDropHandler);
		this.cardPanel.setTransferHandler(fcDropHandler);
	}

	/**
	 * Process a journal event (called by the single app-level listener in OverlayFrame).
	 * This ensures exactly one handler runs per event regardless of pane rebuilds.
	 * <p>
	 * Threading: invoked on the same thread as {@link org.dce.ed.logreader.LiveJournalMonitor#dispatch}
	 * (the {@code Elite-LiveJournalMonitor} worker), not automatically on the EDT.
	 */
	private void applyOwnedFleetCarrierTrackerFromJournal(EliteLogEvent event) {
		if (event == null) {
			return;
		}
		String prevName = ownedFleetCarrierTracker.getOwnedSystemName();
		long prevAddr = ownedFleetCarrierTracker.getOwnedSystemAddress();
		boolean hadLoc = ownedFleetCarrierTracker.hasOwnedCarrierLocation();
		if (event.getType() == EliteEventType.CARRIER_STATS) {
			ownedFleetCarrierTracker.onCarrierStats(
					OwnedFleetCarrierTracker.carrierIdFromJson(event.getRawJson()));
		} else if (event instanceof CarrierJumpRequestEvent req) {
			ownedFleetCarrierTracker.onCarrierJumpRequest(req);
		} else if (event instanceof CarrierLocationEvent loc) {
			ownedFleetCarrierTracker.onCarrierLocation(loc);
		}
		if (ownedCarrierLocationChanged(hadLoc, prevName, prevAddr)) {
			fleetCarrierTab.syncOwnedCarrierRouteMarker();
		}
	}

	private boolean ownedCarrierLocationChanged(boolean hadLoc, String prevName, long prevAddr) {
		if (!hadLoc && ownedFleetCarrierTracker.hasOwnedCarrierLocation()) {
			return true;
		}
		String name = ownedFleetCarrierTracker.getOwnedSystemName();
		long addr = ownedFleetCarrierTracker.getOwnedSystemAddress();
		return (name != null && !name.equals(prevName)) || (addr != 0L && addr != prevAddr);
	}

	public void bootstrapOwnedFleetCarrierFromJournalIfNeeded() {
		fleetCarrierTab.bootstrapOwnedFleetCarrierFromJournalIfNeeded();
	}

	public void processJournalEvent(EliteLogEvent event) {
		applyOwnedFleetCarrierTrackerFromJournal(event);
		updateExecPlaceholderSnapshot(event);
		if (execTriggerService != null) {
			execTriggerService.onJournalEvent(event, ownedFleetCarrierTracker);
		}
		this.handleLogEvent(event);

		if (event instanceof LoadGameEvent lg) {
			miningTab.updateCurrentShipType(lg.getShip());
		}
		if (event instanceof LoadoutEvent lo) {
			miningTab.updateCurrentShipType(lo.getShip());
		}

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

		if (event instanceof StatusEvent flagEvent && flagEvent.isFsdCharging()) {
			onFsdTargetTabFromStatus(flagEvent, null, null);
		}

		if (event instanceof LocationEvent le) {
			lastKnownSystemName = le.getStarSystem();
		} else if (event instanceof FsdJumpEvent jump) {
			lastKnownSystemName = jump.getStarSystem();
		} else if (event instanceof CarrierJumpEvent cj) {
			lastKnownSystemName = cj.getStarSystem();
		} else if (event instanceof org.dce.ed.logreader.event.SupercruiseExitEvent sc) {
			lastKnownSystemName = sc.getStarSystem();
		}
		if (event.getType() == EliteEventType.DOCKED) {
			JsonObject raw = event.getRawJson();
			if (raw != null) {
				if (raw.has("StationName") && !raw.get("StationName").isJsonNull()) {
					lastKnownStationName = raw.get("StationName").getAsString();
				}
				if (raw.has("StarSystem") && !raw.get("StarSystem").isJsonNull()) {
					lastKnownSystemName = raw.get("StarSystem").getAsString();
				}
				String stationType = raw.has("StationType") && !raw.get("StationType").isJsonNull()
						? raw.get("StationType").getAsString()
						: null;
				if (stationType != null && "FleetCarrier".equalsIgnoreCase(stationType.trim())) {
					long systemAddress = raw.has("SystemAddress") && !raw.get("SystemAddress").isJsonNull()
							? raw.get("SystemAddress").getAsLong()
							: 0L;
					String starSystem = raw.has("StarSystem") && !raw.get("StarSystem").isJsonNull()
							? raw.get("StarSystem").getAsString()
							: null;
					ownedFleetCarrierTracker.onDockedFleetCarrier(
							OwnedFleetCarrierTracker.marketIdFromDockedJson(raw),
							starSystem,
							systemAddress);
				}
			}
		} else if (event.getType() == EliteEventType.UNDOCKED) {
			lastKnownStationName = null;
		}

		// Missions before System so Bounty progress is applied even if system hydration is slow.
		// lastKnownSystemName is already updated above for hunt-system gating.
		missionsTab.handleLogEvent(event);
		systemTab.handleLogEvent(event);
		routeTab.handleLogEvent(event);
		biologyTab.handleLogEvent(event);
		CombatTargetTracker.getInstance().applyJournalEvent(event);
		if (combatTab != null) {
			combatTab.handleLogEvent(event);
		}
		engineeringTab.handleLogEvent(event);
		if (event instanceof org.dce.ed.logreader.event.MissionAcceptedEvent
				|| event instanceof org.dce.ed.logreader.event.MissionCompletedEvent
				|| event instanceof org.dce.ed.logreader.event.MissionFailedEvent
				|| event instanceof org.dce.ed.logreader.event.MissionAbandonedEvent
				|| event instanceof org.dce.ed.logreader.event.MissionRedirectedEvent
				|| event instanceof org.dce.ed.logreader.event.CargoDepotEvent
				|| event instanceof org.dce.ed.logreader.event.MissionsEvent) {
			miningTab.refreshMiningMissionsTable();
		}
		fleetCarrierTab.handleLogEvent(event);

		// Nearby panel is no longer user-facing; avoid background sphere scans on every jump.
		// Those scans can trigger broad exobiology/Spansh work across many cached systems.
	}

	public SystemTabPanel getSystemTabPanel() {
		return systemTab;
	}

	public RouteTabPanel getRouteTabPanel() {
		return routeTab;
	}

	public FleetCarrierTabPanel getFleetCarrierTabPanel() {
		return fleetCarrierTab;
	}

	private void updateExecPlaceholderSnapshot(EliteLogEvent event) {
		if (execPlaceholderContext == null || event == null) {
			return;
		}
		CommanderSnapshot snap = execPlaceholderContext.commanderSnapshot();
		if (event instanceof LoadGameEvent lg) {
			snap.updateFromLoadGame(lg);
		} else if (event instanceof LoadoutEvent lo) {
			snap.updateFromLoadout(lo);
		} else if (event instanceof SetUserShipNameEvent renamed) {
			snap.updateFromSetUserShipName(renamed);
		} else if (event instanceof StatusEvent se) {
			snap.updateFromStatus(se);
		} else if (event instanceof FsdTargetEvent ft) {
			snap.updateFromFsdTarget(ft);
		}
	}

	public OwnedFleetCarrierTracker getOwnedFleetCarrierTracker() {
		return ownedFleetCarrierTracker;
	}

	public MiningTabPanel getMiningTabPanel() {
		return miningTab;
	}

	public BiologyTabPanel getBiologyTabPanel() {
		return biologyTab;
	}

	public MissionsTabPanel getMissionsTabPanel() {
		return missionsTab;
	}

	public CombatTabPanel getCombatTabPanel() {
		return combatTab;
	}

	public void setCombatUnclaimedBountyCreditsSupplier(java.util.function.LongSupplier supplier) {
		if (combatTab != null) {
			combatTab.setUnclaimedBountyCreditsSupplier(supplier);
		}
	}

	public void setCombatSessionTracker(CombatSessionTracker tracker) {
		if (combatTab != null) {
			combatTab.setCombatSessionTracker(tracker);
		}
	}

	public EngineeringTabPanel getEngineeringTabPanel() {
		return engineeringTab;
	}

	public ControlPanelTabPanel getControlPanelTabPanel() {
		return controlPanelTab;
	}

	public boolean isPointerOverControlPanelActionButton(Point screenPoint) {
		return CARD_CONTROL_PANEL.equals(visibleCardName)
				&& controlPanelTab != null
				&& controlPanelTab.isPointerOverActionButton(screenPoint);
	}

	/** True when the pointer is over the tab strip or overflow chevrons. */
	public boolean isPointerOverTabBar(Point screenPoint) {
		if (screenPoint == null || !isShowing() || scrollableTabBar == null || !scrollableTabBar.isShowing()) {
			return false;
		}
		try {
			Point origin = scrollableTabBar.getLocationOnScreen();
			return new Rectangle(origin.x, origin.y, scrollableTabBar.getWidth(), scrollableTabBar.getHeight())
					.contains(screenPoint);
		} catch (IllegalComponentStateException ex) {
			return false;
		}
	}

	/**
	 * Selective mouse mode: true when the pointer is over a control that should receive real clicks
	 * on the visible tab.
	 */
	public boolean isPointerOverSelectiveHit(Point screenPoint) {
		if (isRouteReorderGestureActiveAnywhere()) {
			return true;
		}
		return isPointerOverSelectiveHitForCard(visibleCardName, screenPoint);
	}

	/** True while a Route / Fleet Carrier row drag is in progress. */
	public boolean isRouteReorderGestureActiveAnywhere() {
		return (routeTab != null && routeTab.isRouteReorderGestureActive())
				|| (fleetCarrierTab != null && fleetCarrierTab.isRouteReorderGestureActive());
	}

	/** Selective hit-test for a specific card (used by floating docks that host that card). */
	public boolean isPointerOverSelectiveHitForCard(String cardName, Point screenPoint) {
		if (screenPoint == null || cardName == null) {
			return false;
		}
		if (CARD_ROUTE.equals(cardName) && routeTab != null && routeTab.isRouteReorderGestureActive()) {
			return true;
		}
		if (CARD_FLEET_CARRIER.equals(cardName) && fleetCarrierTab != null
				&& fleetCarrierTab.isRouteReorderGestureActive()) {
			return true;
		}
		return switch (cardName) {
			case CARD_ROUTE -> routeTab != null && routeTab.isPointerOverInteractiveRegion(screenPoint);
			case CARD_FLEET_CARRIER -> fleetCarrierTab != null && fleetCarrierTab.isPointerOverInteractiveRegion(screenPoint);
			case CARD_SYSTEM -> systemTab != null && systemTab.isPointerOverInteractiveRegion(screenPoint);
			case CARD_BIOLOGY -> biologyTab != null && biologyTab.isPointerOverInteractiveRegion(screenPoint);
			case CARD_MINING -> miningTab != null && miningTab.isPointerOverInteractiveRegion(screenPoint);
			case CARD_MISSIONS -> missionsTab != null && missionsTab.isPointerOverInteractiveRegion(screenPoint);
			case CARD_COMBAT -> combatTab != null && combatTab.isPointerOverInteractiveRegion(screenPoint);
			case CARD_ENGINEERING -> engineeringTab != null && engineeringTab.isPointerOverInteractiveRegion(screenPoint);
			case CARD_CONTROL_PANEL -> controlPanelTab != null && controlPanelTab.isPointerOverActionButton(screenPoint);
			default -> false;
		};
	}

	/** True when the pointer is over a scroll bar on the visible tab (pass-through thumb drag). */
	public boolean isPointerOverTabScrollBar(Point screenPoint) {
		if (screenPoint == null || !isShowing()) {
			return false;
		}
		return switch (visibleCardName) {
			case CARD_ROUTE -> routeTab.isPointerOverScrollBar(screenPoint);
			case CARD_FLEET_CARRIER -> fleetCarrierTab != null && fleetCarrierTab.isPointerOverScrollBar(screenPoint);
			case CARD_SYSTEM -> systemTab.isPointerOverScrollBar(screenPoint);
			case CARD_ENGINEERING -> engineeringTab.isPointerOverScrollBar(screenPoint);
			case CARD_COMBAT -> combatTab != null && combatTab.isPointerOverScrollBar(screenPoint);
			default -> false;
		};
	}

	public void wireExecTriggerService(ExecTriggerService service) {
		this.execTriggerService = service;
		if (routeTab != null) {
			routeTab.setExecTriggerService(service);
		}
		if (fleetCarrierTab != null) {
			fleetCarrierTab.setExecTriggerService(service);
		}
		if (controlPanelTab != null) {
			controlPanelTab.setExecTriggerService(service);
		}
	}

	public void setExecPlaceholderContext(ExecPlaceholderContext context) {
		this.execPlaceholderContext = context;
	}

	public ExecPlaceholderContext getExecPlaceholderContext() {
		return execPlaceholderContext;
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

	/**
	 * Elite often skips a fresh {@code Loadout} after {@code EngineerCraft}. Patch the in-memory
	 * snapshot so Loadout UI / consumers see updated Level/Modifiers without waiting for the next
	 * board/module-change Loadout.
	 *
	 * @return true when {@link #getLatestLoadout()} was replaced with a patched event
	 */
	public static boolean applyEngineerCraftToLatestLoadout(EngineerCraftEvent craft) {
		if (craft == null || !EngineeringLoadoutExperimentalPatch.shouldPatchLoadout(craft)) {
			return false;
		}
		LoadoutEvent current = getLatestLoadout();
		if (current == null || current.getRawJson() == null) {
			return false;
		}
		String patched = EngineeringLoadoutExperimentalPatch.patchLoadoutRawJson(
				current.getRawJson().toString(), craft);
		if (patched == null || patched.isBlank()) {
			return false;
		}
		try {
			EliteLogEvent parsed = new EliteLogParser().parseRecord(patched);
			if (!(parsed instanceof LoadoutEvent updated)) {
				return false;
			}
			loadoutEventx = updated;
			NpcCrewTracker.getInstance().onLoadout(updated);
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

	public void handleLogEvent(EliteLogEvent event) {
        if (event instanceof LoadoutEvent e) {
        	loadoutEventx = e;
        	NpcCrewTracker.getInstance().onLoadout(e);
        	for (Runnable r : loadoutChangeListeners) {
        		SwingUtilities.invokeLater(r);
        	}
        } else if (event instanceof EngineerCraftEvent craft
        		&& applyEngineerCraftToLatestLoadout(craft)) {
        	for (Runnable r : loadoutChangeListeners) {
        		SwingUtilities.invokeLater(r);
        	}
        }

		NpcCrewTracker.getInstance().applyJournalEvent(event);
		BountyScanTracker.getInstance().applyJournalEvent(event);
		// CombatTargetTracker also receives events in processJournalEvent fan-out below.

        if (event instanceof org.dce.ed.logreader.event.StatusEvent se) {
        	// Do not tie limpet reminders to Status dock transitions: when Elite exits, Status.json often
        	// resets (e.g. Flags=0) so Docked clears while our cached state was still docked, which falsely
        	// matched "just undocked". Real undocks still emit journal {@link EliteEventType#UNDOCKED}.
        	setCurrentlyDocked(se.isDocked());
        } else if (event instanceof org.dce.ed.logreader.event.LocationEvent le) {
        	setCurrentlyDocked(le.isDocked());
        } else if (event.getType() == EliteEventType.DOCKED) {
        	// Journal Docked must close mining runs via setCurrentlyDocked. SystemEventProcessor also sets
        	// docked on this event; if we only relied on Status.json afterward, previous would already be true
        	// and onDocked would never run (common when docking at a station after mining in the same system).
        	setCurrentlyDocked(true);
        }


		if (event.getType() == EliteEventType.UNDOCKED) {
			setCurrentlyDocked(false);
			SwingUtilities.invokeLater(() -> {
				maybeRemindAboutLimpets();
				maybeRemindAboutFighterPilot();
			});
		}

		if (event.getType() == EliteEventType.CARRIER_STATS) {
			armCarrierStatsGalaxyMapLatch();
		}

		if (event instanceof FsdJumpEvent e) {
			if (e.getDocked() == null || e.getDocked()) {
				if (OverlayPreferences.isAutoSwitchSystemTabOnJumpOrScan()) {
					showSystemTabFromStatusWatcher();
				}
			}
		} else if (event instanceof FssDiscoveryScanEvent) {
			if (OverlayPreferences.isAutoSwitchSystemTabOnJumpOrScan()) {
				showSystemTabFromStatusWatcher();
			}
		}

		if (event instanceof StartJumpEvent sj) {
			StatusEvent snap = readStatusSnapshotFromDisk();
			onFsdTargetTabFromStatus(snap, sj.getSystemAddress(), sj);
		}
		if (event instanceof SupercruiseExitEvent e) {
		    miningTab.updateFromSupercruiseExit(e);
		    String bodyType = e.getBodyType();
		    if (bodyType != null && bodyType.contains("PlanetaryRing")) {
		        showMiningTabFromStatusWatcher();
		    }
		}

	}
	private StatusEvent readStatusSnapshotFromDisk() {
		try {
			String home = System.getProperty("user.home");
			Path p = Path.of(home, "Saved Games", "Frontier Developments", "Elite Dangerous", "Status.json");
			if (!Files.exists(p)) {
				return null;
			}
			return STATUS_SNAPSHOT_PARSER.parseStatusJsonFile(p);
		} catch (Exception ignored) {
			return null;
		}
	}

	private long currentOverlaySystemAddressOrZero() {
		if (systemTab == null || systemTab.getState() == null) {
			return 0L;
		}
		return systemTab.getState().getSystemAddress();
	}

	/**
	 * Fleet Carrier tab for owned-carrier jumps; System tab when Status shows a body/station target; Route tab
	 * for system-only ship hyperspace. Does not cross-switch (FC jump stays on FC tab; ship jump stays on Route).
	 *
	 * @param startJumpOrNull journal {@code StartJump} when this decision is tied to that event; otherwise null
	 */
	private void onFsdTargetTabFromStatus(StatusEvent status, Long jumpTargetSystemAddress,
			StartJumpEvent startJumpOrNull) {
		if (!OverlayPreferences.isAutoSwitchTabOnFsdTarget()) {
			return;
		}
		boolean fcPending = fleetCarrierTab != null && fleetCarrierTab.isOwnedCarrierJumpPending();
		boolean fcCountdown = OverlayFrame.overlayFrame != null && OverlayFrame.overlayFrame.hasCarrierJumpCountdown();
		boolean aboardFc = systemTab != null && systemTab.getState() != null
				&& systemTab.getState().isCommanderAboardFleetCarrier();
		AutoTabJumpLogic.JumpKind kind = AutoTabJumpLogic.classifyForAutoTabSwitch(
				fcPending, fcCountdown, aboardFc, status, startJumpOrNull);
		switch (kind) {
			case FLEET_CARRIER -> showFleetCarrierTabFromStatusWatcher();
			case SHIP_HYPERSPACE -> {
				long cur = currentOverlaySystemAddressOrZero();
				if (SystemTabTargetLogic.preferSystemTabForFsdTarget(status, jumpTargetSystemAddress, cur)) {
					showSystemTabFromStatusWatcher();
				} else {
					showRouteTabFromStatusWatcher();
				}
			}
			case NONE -> { /* unrelated Status noise */ }
		}
	}

	private void showMiningTabFromStatusWatcher() {
	    if (!OverlayPreferences.isAutoSwitchMiningOnPlanetaryRing()) {
	    	return;
	    }
	    SwingUtilities.invokeLater(() -> selectTab(CARD_MINING, miningButton));
	}

	/**
	 * If journals indicate the commander is undocked in a planetary ring, show Mining on startup
	 * (same {@code PlanetaryRing} check as live {@link SupercruiseExitEvent} handling).
	 */
	private void maybeSelectMiningTabFromStartupJournal() {
		if (!OverlayPreferences.isAutoSwitchMiningOnStartupPlanetaryRing()) {
			return;
		}
		if (miningButton == null || !miningButton.isVisible()) {
			return;
		}
		try {
			Path dir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
			if (dir == null || !Files.isDirectory(dir)) {
				return;
			}
			EliteJournalReader reader = new EliteJournalReader(dir);
			if (reader.isLatestSituationPlanetaryRingMining()) {
				showMiningTabFromStatusWatcher();
			}
		} catch (IOException | RuntimeException ignored) {
		}
	}

	/**
	 * If {@code Status.json} shows the commander on a planetary surface (landed ship, SRV, or on foot),
	 * open Biology on startup — after mining startup so exobiology takes priority over ring mining.
	 */
	private void maybeSelectBiologyTabFromStartupStatus() {
		if (!OverlayPreferences.isAutoSwitchBiologyOnNearLandableAtmosphere()) {
			return;
		}
		if (biologyButton == null || !biologyButton.isVisible()) {
			return;
		}
		StatusEvent se = readStatusSnapshotFromDisk();
		if (!isOnPlanetarySurface(se)) {
			return;
		}
		Integer bodyId = se.getStatusBodyId();
		if (bodyId != null) {
			lastAutoBiologyBodyId = bodyId;
		}
		showBiologyTabFromStatusWatcher();
	}

	/** True when undocked on a world with surface coordinates (ship landed, SRV, or Odyssey on foot). */
	private static boolean isOnPlanetarySurface(StatusEvent se) {
		if (se == null || se.getDecodedFlags() == null) {
			return false;
		}
		if (se.isDocked()) {
			return false;
		}
		StatusEvent.DecodedFlags f = se.getDecodedFlags();
		if (!f.hasLatLong) {
			return false;
		}
		Double radius = se.getPlanetRadius();
		if (radius == null || radius.doubleValue() <= 1.0) {
			return false;
		}
		String body = se.getBodyNamePhysical();
		if (body == null || body.isBlank()) {
			body = se.getBodyName();
		}
		if (body == null || body.isBlank()) {
			return false;
		}
		return f.landed || f.inSrv || f.onFootOnPlanet || (se.isOnFoot() && f.hasLatLong);
	}

	private void showBiologyTabFromStatusWatcher() {
		if (!OverlayPreferences.isAutoSwitchBiologyOnNearLandableAtmosphere()) {
			return;
		}
		if (biologyButton == null || !biologyButton.isVisible()) {
			return;
		}
		SwingUtilities.invokeLater(() -> selectTab(CARD_BIOLOGY, biologyButton));
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
	/**
	 * Uses {@link org.dce.ed.ui.HoverClickPoller} so hover works in mouse pass-through mode.
	 */
	private static void installHoverSwitch(JButton button, int delayMs, Runnable action, BooleanSupplier enabled) {
		org.dce.ed.ui.HoverClickPoller.register(button, delayMs, action, enabled);
	}

	private JButton createTabButton(String text) {
		JButton button = new JButton(text);
		button.setUI(TabButtonUI.INSTANCE);
		button.setFocusable(false);
		button.setFocusPainted(false);
		button.setFont(systemTabButtonFont());
		button.setContentAreaFilled(false);
		button.setToolTipText("Drag off the strip to open in a separate window; drop on another window's tabs to move");

		// Default: slightly translucent dark background when overlay is transparent.
		// Selected tab gets an opaque background in applyTabButtonStyle to prevent
		// adjacent tab text from peeking through (z-order/alpha bleed).
		// When mouse pass-through is off, applyTabButtonStyle forces a solid fill so the
		// entire chip is a hit target (Windows layered windows ignore alpha-0 pixels).
		button.setOpaque(!OverlayPreferences.overlayChromeRequestsTransparency(this));
		button.setBackground(EdoUi.Internal.DARK_ALPHA_220);

		applyTabButtonStyle(button);
		return button;
	}

	private static Font systemTabButtonFont() {
		Font systemFont = UIManager.getLookAndFeelDefaults().getFont("Button.font");
		if (systemFont == null) {
			systemFont = new Font(Font.DIALOG, Font.PLAIN, 10);
		}
		return systemFont.deriveFont(Font.BOLD, 10f);
	}

	/**
	 * BasicButtonUI clips labels with an ellipsis when the laid-out width is smaller than the text.
	 * On some JDK/LAF/DPI combinations the preferred width is underestimated (border + margin),
	 * which shows up intermittently as abbreviated tab names (e.g. "Rou...").
	 * <p>
	 * Also paints the button background when {@link AbstractButton#isContentAreaFilled()} so that
	 * interactive (non–mouse-pass-through) tabs keep a full-rect hit target under per-pixel alpha.
	 */
	private static final class TabButtonUI extends BasicButtonUI {
		private static final TabButtonUI INSTANCE = new TabButtonUI();

		@Override
		public void paint(Graphics g, JComponent c) {
			AbstractButton b = (AbstractButton) c;
			if (b.isContentAreaFilled() && b.getBackground() != null) {
				Graphics2D g2 = (Graphics2D) g.create();
				try {
					g2.setColor(b.getBackground());
					g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 6, 6);
				} finally {
					g2.dispose();
				}
			}
			super.paint(g, c);
		}

		@Override
		protected void paintText(Graphics g, AbstractButton b, Rectangle textRect, String text) {
			super.paintText(g, b, textRect, b.getText());
		}
	}

	/** Minimum width/height so the full tab label fits (margin + border + text). */
	private static Dimension computeTabButtonSize(JButton button) {
		FontMetrics fm = button.getFontMetrics(button.getFont());
		String text = button.getText() != null ? button.getText() : "";
		int textW = fm.stringWidth(text);
		int textH = fm.getHeight();

		Insets margin = button.getMargin();
		if (margin == null) {
			margin = new Insets(0, 0, 0, 0);
		}
		Insets borderInsets = new Insets(0, 0, 0, 0);
		if (button.getBorder() != null) {
			borderInsets = button.getBorder().getBorderInsets(button);
		}

		int w = textW + margin.left + margin.right + borderInsets.left + borderInsets.right + 4;
		int h = textH + margin.top + margin.bottom + borderInsets.top + borderInsets.bottom + 2;
		return new Dimension(w, h);
	}

	private static void applyTabButtonLayoutSize(JButton button) {
		if (button == null) {
			return;
		}
		Dimension size = computeTabButtonSize(button);
		button.setMinimumSize(size);
		button.setPreferredSize(size);
	}

	private void refreshAllTabButtonSizes() {
		applyTabButtonLayoutSize(routeButton);
		applyTabButtonLayoutSize(systemButton);
		applyTabButtonLayoutSize(biologyButton);
		applyTabButtonLayoutSize(miningButton);
		applyTabButtonLayoutSize(missionsButton);
		applyTabButtonLayoutSize(combatButton);
		applyTabButtonLayoutSize(fleetCarrierButton);
		applyTabButtonLayoutSize(engineeringButton);
		applyTabButtonLayoutSize(controlPanelButton);
		if (scrollableTabBar != null) {
			scrollableTabBar.refreshLayout();
		}
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

	public void refreshOverlayTabBarFromSavedPreferences() {
		applyOverlayTabBarVisibility();
		if (combatTab != null) {
			combatTab.reloadCombatCommandBindings();
		}
		JButton active = tabButtonForCard(visibleCardName);
		if (active != null && !active.isVisible()) {
			selectFirstVisibleTab();
		}
		scrollableTabBar.refreshLayout();
		revalidate();
		repaint();
	}

	private JButton tabButtonForCard(String cardName) {
		if (cardName == null) {
			return null;
		}
		return switch (cardName) {
			case CARD_ROUTE -> routeButton;
			case CARD_SYSTEM -> systemButton;
			case CARD_BIOLOGY -> biologyButton;
			case CARD_MINING -> miningButton;
			case CARD_MISSIONS -> missionsButton;
			case CARD_COMBAT -> combatButton;
			case CARD_FLEET_CARRIER -> fleetCarrierButton;
			case CARD_ENGINEERING -> engineeringButton;
			case CARD_CONTROL_PANEL -> controlPanelButton;
			default -> null;
		};
	}

	private void applyOverlayTabBarVisibility() {
		boolean r = OverlayPreferences.isOverlayTabRouteVisible();
		boolean s = OverlayPreferences.isOverlayTabSystemVisible();
		boolean b = OverlayPreferences.isOverlayTabBiologyVisible();
		boolean m = OverlayPreferences.isOverlayTabMiningVisible();
		boolean ms = OverlayPreferences.isOverlayTabMissionsVisible();
		boolean combat = OverlayPreferences.isOverlayTabCombatVisible();
		boolean f = OverlayPreferences.isOverlayTabFleetCarrierVisible();
		boolean eng = OverlayPreferences.isOverlayTabEngineeringVisible();
		boolean cp = OverlayPreferences.isOverlayTabControlPanelVisible();
		if (!r && !s && !b && !m && !ms && !combat && !f && !eng && !cp) {
			r = s = b = m = ms = combat = f = eng = cp = true;
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
		if (missionsButton != null) {
			missionsButton.setVisible(ms);
		}
		if (combatButton != null) {
			combatButton.setVisible(combat);
		}
		if (fleetCarrierButton != null) {
			fleetCarrierButton.setVisible(f);
		}
		if (engineeringButton != null) {
			engineeringButton.setVisible(eng);
		}
		if (controlPanelButton != null) {
			controlPanelButton.setVisible(cp);
		}
		refreshAllTabButtonSizes();
	}

	/**
	 * Selects the first tab that is visible in the main bar (order: Route … Control Panel).
	 */
	private void selectFirstVisibleTab() {
		JButton[] buttons = { routeButton, systemButton, biologyButton, miningButton, missionsButton,
				combatButton, fleetCarrierButton, engineeringButton, controlPanelButton };
		String[] cards = { CARD_ROUTE, CARD_SYSTEM, CARD_BIOLOGY, CARD_MINING, CARD_MISSIONS,
				CARD_COMBAT, CARD_FLEET_CARRIER, CARD_ENGINEERING, CARD_CONTROL_PANEL };
		for (int i = 0; i < buttons.length; i++) {
			JButton b = buttons[i];
			if (b != null && b.isVisible() && b.getParent() == getTabStrip()) {
				selectTabInMain(cards[i], b);
				return;
			}
		}
		visibleCardName = CARD_SYSTEM;
		if (systemTab.getParent() == cardPanel) {
			cardLayout.show(cardPanel, CARD_SYSTEM);
		}
	}

	private void selectTab(String cardName, JButton selectedButton) {
		if (tabDockingController != null && cardName != null && !tabDockingController.isOnMain(cardName)) {
			tabDockingController.selectTabWherever(cardName);
			applyTabSelectionStyles(cardName);
			// Do NOT touch visibleCardName here: it tracks the MAIN dock's visible card and drives
			// the main overlay's Selective hit testing / wheel routing. Overwriting it with a
			// float-hosted card killed hybrid clicks on the tab main was actually showing.
			if (CARD_MINING.equals(cardName)) {
				miningTab.onMiningTabBecameVisible();
			}
            if (CARD_CONTROL_PANEL.equals(cardName) && controlPanelTab != null) {
				controlPanelTab.refreshButtons();
			}
			if (CARD_COMBAT.equals(cardName) && combatTab != null) {
				combatTab.reloadCombatCommandBindings();
			}
			return;
		}
		selectTabInMain(cardName, selectedButton);
	}

	/** Selects a tab that is currently hosted in the main overlay dock. */
	public void selectTabInMain(String cardName, JButton selectedButton) {
		if (selectedButton != null && !selectedButton.isVisible()) {
			selectFirstVisibleTab();
			return;
		}
		applyTabSelectionStyles(cardName);
		cardLayout.show(cardPanel, cardName);
		visibleCardName = cardName;
		if (CARD_MINING.equals(cardName)) {
			miningTab.onMiningTabBecameVisible();
		}
		if (CARD_CONTROL_PANEL.equals(cardName) && controlPanelTab != null) {
			controlPanelTab.refreshButtons();
		}
		if (CARD_COMBAT.equals(cardName) && combatTab != null) {
			combatTab.reloadCombatCommandBindings();
		}
	}

	/**
	 * Selection is per dock: selecting a tab only deselects its siblings in the same tab strip,
	 * so the main overlay and each floating window keep their own highlighted tab.
	 */
	public void applyTabSelectionStyles(String cardName) {
		JButton selectedButton = tabButtonForCard(cardName);
		java.awt.Container strip = selectedButton != null ? selectedButton.getParent() : null;
		JButton[] all = { routeButton, systemButton, biologyButton, miningButton, missionsButton,
				combatButton, nearbyButton, fleetCarrierButton, engineeringButton, controlPanelButton };
		for (JButton b : all) {
			if (b == null) {
				continue;
			}
			if (b == selectedButton) {
				b.setSelected(true);
			} else if (strip != null && b.getParent() == strip) {
				b.setSelected(false);
			}
		}

		applyTabButtonStyle(routeButton);
		applyTabButtonStyle(systemButton);
		applyTabButtonStyle(biologyButton);
		applyTabButtonStyle(miningButton);
		applyTabButtonStyle(missionsButton);
		applyTabButtonStyle(combatButton);
		applyTabButtonStyle(fleetCarrierButton);
		applyTabButtonStyle(engineeringButton);
		applyTabButtonStyle(controlPanelButton);
	}

	public void selectFirstVisibleTabInMain() {
		selectFirstVisibleTab();
	}

	public String getVisibleCardName() {
		return visibleCardName;
	}

	public void setTabDockingController(TabDockingController tabDockingController) {
		this.tabDockingController = tabDockingController;
	}

	public TabDockingController getTabDockingController() {
		return tabDockingController;
	}

	public JButton getTabButton(String cardName) {
		return tabButtonForCard(cardName);
	}

	public JComponent getTabContent(String cardName) {
		if (cardName == null) {
			return null;
		}
		return switch (cardName) {
			case CARD_ROUTE -> routeTab;
			case CARD_SYSTEM -> systemTab;
			case CARD_BIOLOGY -> biologyTab;
			case CARD_MINING -> miningTab;
			case CARD_MISSIONS -> missionsTab;
			case CARD_COMBAT -> combatTab;
			case CARD_NEARBY -> nearbyTab;
			case CARD_FLEET_CARRIER -> fleetCarrierTab;
			case CARD_ENGINEERING -> engineeringTab;
			case CARD_CONTROL_PANEL -> controlPanelTab;
			default -> null;
		};
	}

	public String cardNameForTabButton(JButton button) {
		if (button == null) {
			return null;
		}
		if (button == routeButton) {
			return CARD_ROUTE;
		}
		if (button == systemButton) {
			return CARD_SYSTEM;
		}
		if (button == biologyButton) {
			return CARD_BIOLOGY;
		}
		if (button == miningButton) {
			return CARD_MINING;
		}
		if (button == missionsButton) {
			return CARD_MISSIONS;
		}
		if (button == combatButton) {
			return CARD_COMBAT;
		}
		if (button == fleetCarrierButton) {
			return CARD_FLEET_CARRIER;
		}
		if (button == engineeringButton) {
			return CARD_ENGINEERING;
		}
		if (button == controlPanelButton) {
			return CARD_CONTROL_PANEL;
		}
		Object prop = button.getClientProperty("edo.cardName");
		return prop instanceof String s ? s : null;
	}

	@Override
	public String getDockId() {
		return OverlayTabId.MAIN_DOCK_ID;
	}

	@Override
	public java.awt.Window getWindow() {
		return javax.swing.SwingUtilities.getWindowAncestor(this);
	}

	@Override
	public JPanel getTabStrip() {
		return scrollableTabBar.getTabStrip();
	}

	@Override
	public JPanel getCardPanel() {
		return cardPanel;
	}

	@Override
	public CardLayout getCardLayout() {
		return cardLayout;
	}

	@Override
	public void onDockTabsChanged() {
		if (scrollableTabBar != null) {
			scrollableTabBar.refreshLayout();
		}
		revalidate();
		repaint();
	}

	@Override
	public Rectangle getBoundsOnScreen() {
		try {
			Point p = getLocationOnScreen();
			return new Rectangle(p.x, p.y, getWidth(), getHeight());
		} catch (IllegalComponentStateException ex) {
			return getBounds();
		}
	}

	/**
	 * Forward a global mouse wheel (Selective / Full pass-through): Route, System, Fleet Carrier,
	 * Biology, and Engineering. Applies when the pointer is over the scroll pane viewport (not only
	 * the scrollbar thumb). On System, zooms the orbital map when over the map.
	 *
	 * @return {@code true} if the wheel was consumed (map zoom, or scroll adjusted)
	 */
	public boolean handlePassThroughMouseWheelAtScreen(int screenX, int screenY, int wheelRotation) {
		if (!isShowing() || wheelRotation == 0) {
			return false;
		}
		return applyPassThroughWheelForCard(visibleCardName, screenX, screenY, wheelRotation);
	}

	/**
	 * Same as {@link #handlePassThroughMouseWheelAtScreen} for a specific card (main or floating dock).
	 */
	public boolean applyPassThroughWheelForCard(String cardName, int screenX, int screenY, int wheelRotation) {
		if (cardName == null || wheelRotation == 0) {
			return false;
		}
		return switch (cardName) {
			case CARD_ROUTE -> routeTab != null && routeTab.applyPassThroughWheelIfHit(screenX, screenY, wheelRotation);
			case CARD_SYSTEM -> systemTab != null && systemTab.applyPassThroughWheelIfHit(screenX, screenY, wheelRotation);
			case CARD_FLEET_CARRIER -> fleetCarrierTab != null
					&& fleetCarrierTab.applyPassThroughWheelIfHit(screenX, screenY, wheelRotation);
			case CARD_BIOLOGY -> biologyTab != null && biologyTab.applyPassThroughWheelIfHit(screenX, screenY, wheelRotation);
			case CARD_ENGINEERING -> engineeringTab != null
					&& engineeringTab.applyPassThroughWheelIfHit(screenX, screenY, wheelRotation);
			default -> false;
		};
	}

	/**
	 * Pass-through dwell on biology map controls (bookmark / zoom +/−).
	 *
	 * @return {@code true} while the pointer is over one of those controls
	 */
	public boolean applyPassThroughBioMapControlsAtScreen(int screenX, int screenY) {
		if (!isShowing() || !CARD_BIOLOGY.equals(visibleCardName)) {
			return false;
		}
		return biologyTab.applyPassThroughMapControlsAtScreen(screenX, screenY);
	}

	public void resetPassThroughBioMapControlsHover() {
		biologyTab.resetPassThroughMapControlsHover();
	}

	/** Full MPT chrome exception: pointer is over the ExoBio map surface. */
	public boolean isPointerOverBiologyMap(Point screenPoint) {
		return isPointerOverBiologyMapForCard(visibleCardName, screenPoint);
	}

	/** Full MPT chrome exception for a specific card (main or floating dock). */
	public boolean isPointerOverBiologyMapForCard(String cardName, Point screenPoint) {
		return CARD_BIOLOGY.equals(cardName)
				&& biologyTab != null
				&& biologyTab.isPointerOverInteractiveRegion(screenPoint);
	}

	private void applyTabButtonStyle(JButton button) {
		if (button == null) {
			return;
		}

		boolean overlayWindow = OverlayPreferences.isPassThroughWindowActive();
		boolean passThrough = hoverSwitchEnabled != null && hoverSwitchEnabled.getAsBoolean();
		boolean selected = button.isSelected();
		// Transparent overlay (incl. decorated host): never use bright white tab chrome — it reads as a stray frame.
		boolean useOverlayTabChrome = OverlayPreferences.isOverlayTransparent()
				|| overlayWindow
				|| passThrough;
		Color foreground = selected
				? (useOverlayTabChrome ? EdoUi.User.MAIN_TEXT : TAB_WHITE)
				: EdoUi.Internal.MAIN_TEXT_ALPHA_220;
		Color borderColor = (selected && !useOverlayTabChrome)
				? TAB_WHITE
				: (selected ? EdoUi.User.MAIN_TEXT : EdoUi.Internal.MAIN_TEXT_ALPHA_220);

		if (!passThrough && !OverlayPreferences.overlayChromeRequestsTransparency(this)) {
			/*
			 * Mouse is interactive on an opaque host: Windows layered/per-pixel-alpha hit-testing only
			 * delivers clicks to non-zero-alpha pixels. With contentAreaFilled(false) only the glyph was
			 * hittable, so most tab clicks were dropped. Paint a solid chip for the full button bounds.
			 * (On the see-through overlay host, ScrollableTabBar paints an alpha ≥ 1 plate under the tabs
			 * instead, so the transparency preference stays visible while clicks still land.)
			 */
			button.setContentAreaFilled(true);
			button.setOpaque(true);
			if (selected) {
				button.setBackground(new Color(70, 70, 70));
			} else {
				button.setBackground(new Color(40, 40, 40));
			}
		} else if (selected && !useOverlayTabChrome) {
			button.setContentAreaFilled(true);
			button.setOpaque(true);
			button.setBackground(TAB_SELECTED_BG);
		} else if (!useOverlayTabChrome) {
			button.setContentAreaFilled(true);
			button.setOpaque(true);
			Color base = EdoUi.User.BACKGROUND;
			button.setBackground(new Color(base.getRed(), base.getGreen(), base.getBlue(), 255));
		} else if (selected) {
			/*
			 * Transparent overlay chrome: the orange text/border alone (alpha 255 vs 220) is not
			 * distinguishable, so paint a translucent chip behind the active tab. Non-opaque so the
			 * overlay transparency preference still shows through around the rounded corners.
			 */
			button.setContentAreaFilled(true);
			button.setOpaque(false);
			button.setBackground(EdoUi.withAlpha(TAB_SELECTED_BG, 200));
		} else {
			button.setContentAreaFilled(false);
			button.setOpaque(false);
			button.setBackground(EdoUi.Internal.DARK_ALPHA_220);
		}

		button.setMargin(TAB_PADDING);
		button.setForeground(foreground);
		button.setBorder(createTabBorder(borderColor));
		applyTabButtonLayoutSize(button);
	}

	private void armCarrierStatsGalaxyMapLatch() {
		carrierStatsGalaxyMapLatchUntilMs = System.currentTimeMillis() + CARRIER_STATS_GALAXY_MAP_LATCH_MS;
	}

	private boolean isCarrierStatsGalaxyMapLatchActive() {
		return System.currentTimeMillis() < carrierStatsGalaxyMapLatchUntilMs;
	}

	private boolean isFleetCarrierTabCurrentlyShown() {
		return CARD_FLEET_CARRIER.equals(visibleCardName);
	}

	/**
	 * Prefer Fleet Carrier tab when opening the galaxy map from the carrier-management flow: a recent
	 * {@code CarrierStats} (owner opened management) and prior {@code GuiFocus} was the right panel or station services.
	 * Docked-on-carrier alone is not enough—you might be plotting a ship route to leave.
	 *
	 * @param previousGuiFocus last GuiFocus before the map; {@code 1} = right panel, {@code 5} = station services
	 */
	private boolean preferFleetCarrierTabForGalaxyMap(int previousGuiFocus) {
		if (!isCarrierStatsGalaxyMapLatchActive()) {
			return false;
		}
		return previousGuiFocus == 1 || previousGuiFocus == 5;
	}

	private void showRouteTabFromStatusWatcher() {
		// Note: this is used by the GuiFocus watcher; preference check is handled there.
		SwingUtilities.invokeLater(() -> selectTab(CARD_ROUTE, routeButton));
	}

	private void showFleetCarrierTabFromStatusWatcher() {
		SwingUtilities.invokeLater(() -> {
			if (fleetCarrierButton != null && fleetCarrierButton.isVisible()) {
				selectTab(CARD_FLEET_CARRIER, fleetCarrierButton);
			} else {
				selectTab(CARD_ROUTE, routeButton);
			}
		});
	}

	private void showSystemTabFromStatusWatcher() {
		// Called by multiple auto-switch sources; each caller should check its own preference.
		SwingUtilities.invokeLater(() -> selectTab(CARD_SYSTEM, systemButton));
	}

	private void handleNearBodyChanged(BodyInfo nearBody) {
	    if (!OverlayPreferences.isAutoSwitchBiologyOnNearLandableAtmosphere()) {
	        return;
	    }
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
	    showBiologyTabFromStatusWatcher();
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
	 * opens the Galaxy Map or System Map. Galaxy map uses {@code CarrierStats} + prior {@code GuiFocus} heuristics
	 * (see {@link #preferFleetCarrierTabForGalaxyMap(int)}) because ship and carrier maps share {@code GuiFocus 6}.
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
			// 6 = Galaxy Map -> Route tab, or Fleet Carrier after carrier management (CarrierStats) + prior panel focus
			if (guiFocus == 6) {
				if (!OverlayPreferences.isAutoSwitchRouteOnGalaxyMap()) {
					return;
				}
				if (parent.isFleetCarrierTabCurrentlyShown()) {
					return;
				}
				int previousGuiFocus = lastGuiFocus;
				if (parent.preferFleetCarrierTabForGalaxyMap(previousGuiFocus)) {
					parent.showFleetCarrierTabFromStatusWatcher();
				} else {
					parent.showRouteTabFromStatusWatcher();
				}
			}
			// 7 = System Map -> System tab
			else if (guiFocus == 7) {
				if (OverlayPreferences.isAutoSwitchSystemOnSystemMap()) {
					parent.showSystemTabFromStatusWatcher();
				}
			}
		}
	}

	/**
	 * Cycles to the next visible tab on the main overlay strip (skips preference-hidden and floated tabs).
	 * Order: Route → System → ExoBio → Mining → Missions → Fleet Carrier → Control Panel.
	 */
	public void selectNextVisibleTab() {
		JButton[] buttons = { routeButton, systemButton, biologyButton, miningButton, missionsButton,
				combatButton, fleetCarrierButton, engineeringButton, controlPanelButton };
		String[] cards = { CARD_ROUTE, CARD_SYSTEM, CARD_BIOLOGY, CARD_MINING, CARD_MISSIONS,
				CARD_COMBAT, CARD_FLEET_CARRIER, CARD_ENGINEERING, CARD_CONTROL_PANEL };

		int selected = -1;
		for (int i = 0; i < buttons.length; i++) {
			JButton b = buttons[i];
			if (b != null && b.isSelected() && b.getParent() == getTabStrip()) {
				selected = i;
				break;
			}
		}

		for (int step = 1; step <= buttons.length; step++) {
			int idx = (selected + step) % buttons.length;
			JButton b = buttons[idx];
			if (b != null && b.isVisible() && b.getParent() == getTabStrip()) {
				final int fIdx = idx;
				SwingUtilities.invokeLater(() -> selectTab(cards[fIdx], buttons[fIdx]));
				return;
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
		if (!isOpaque() && !OverlayPreferences.overlayChromeRequestsTransparency(this)) {
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

		scrollableTabBar.applyOverlayChrome(bgWithAlpha, treatAsTransparent);

		cardPanel.setOpaque(opaque);
		cardPanel.setBackground(bgWithAlpha);

		// Re-apply tab button opacity/background so selected tab stays opaque and no text bleeds through.
		applyTabButtonStyle(routeButton);
		applyTabButtonStyle(systemButton);
		applyTabButtonStyle(biologyButton);
		applyTabButtonStyle(miningButton);
		applyTabButtonStyle(missionsButton);
		applyTabButtonStyle(combatButton);
		applyTabButtonStyle(fleetCarrierButton);
		applyTabButtonStyle(engineeringButton);
		applyTabButtonStyle(controlPanelButton);

		applyOverlayBackgroundToCard(CARD_ROUTE, bgWithAlpha, treatAsTransparent);
		applyOverlayBackgroundToCard(CARD_NEARBY, bgWithAlpha, treatAsTransparent);
		applyOverlayBackgroundToCard(CARD_FLEET_CARRIER, bgWithAlpha, treatAsTransparent);
		applyOverlayBackgroundToCard(CARD_CONTROL_PANEL, bgWithAlpha, treatAsTransparent);
		applyOverlayBackgroundToCard(CARD_ENGINEERING, bgWithAlpha, treatAsTransparent);
		applyOverlayBackgroundToCard(CARD_MINING, bgWithAlpha, treatAsTransparent);
		applyOverlayBackgroundToCard(CARD_COMBAT, bgWithAlpha, treatAsTransparent);

		revalidate();
		repaint();
	}

	/**
	 * Apply overlay chrome to a single tab card (used when a tab lives in a floating window
	 * whose mouse mode / transparency differs from the main overlay).
	 */
	public void applyOverlayBackgroundToCard(String cardName, Color bgWithAlpha, boolean treatAsTransparent) {
		if (cardName == null) {
			return;
		}
		switch (cardName) {
			case CARD_ROUTE -> {
				if (routeTab != null) {
					routeTab.applyOverlayBackground(bgWithAlpha, treatAsTransparent);
				}
			}
			case CARD_NEARBY -> {
				if (nearbyTab != null) {
					nearbyTab.applyOverlayBackground(bgWithAlpha);
				}
			}
			case CARD_FLEET_CARRIER -> {
				if (fleetCarrierTab != null) {
					fleetCarrierTab.applyOverlayBackground(bgWithAlpha, treatAsTransparent);
				}
			}
			case CARD_CONTROL_PANEL -> {
				if (controlPanelTab != null) {
					controlPanelTab.applyOverlayBackground(bgWithAlpha, treatAsTransparent);
				}
			}
			case CARD_ENGINEERING -> {
				if (engineeringTab != null) {
					engineeringTab.applyOverlayBackground(bgWithAlpha, treatAsTransparent);
				}
			}
			case CARD_MINING -> {
				if (miningTab != null) {
					miningTab.applyOverlayBackground(bgWithAlpha);
				}
			}
			case CARD_COMBAT -> {
				if (combatTab != null) {
					combatTab.applyOverlayBackground(bgWithAlpha, treatAsTransparent);
				}
			}
			default -> {
				// System / Biology / Missions rely on window-local chrome prefs + non-opaque roots.
			}
		}
		JComponent content = getTabContent(cardName);
		if (content != null) {
			content.revalidate();
			content.repaint();
		}
	}
	public static boolean shouldShowNoFighterPilotWarning(boolean docked) {
		return NpcCrewTracker.shouldShowNoFighterPilotWarning(docked, getLatestLoadout());
	}

	public static void maybeRemindAboutFighterPilot() {
		if (!OverlayPreferences.isSpeechEnabled()) {
			return;
		}
		if (!OverlayPreferences.isFighterPilotReminderEnabled()) {
			return;
		}

		LoadoutEvent loadout = getLatestLoadout();
		if (loadout == null) {
			Path journalDir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
			if (journalDir != null && Files.isDirectory(journalDir)) {
				try {
					EliteJournalReader r = new EliteJournalReader(journalDir);
					loadout = (LoadoutEvent) r.findMostRecentEvent(EliteEventType.LOADOUT, 1);
				} catch (IOException e) {
					return;
				}
			}
		}

		if (!NpcCrewTracker.shouldShowNoFighterPilotWarning(true, loadout)) {
			return;
		}
		tts.speakf(NpcCrewTracker.FIGHTER_PILOT_REMINDER_SPEECH);
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

	// Limpet controllers only: int_dronecontrol_* / int_multidronecontrol_*
	// (prospector, collector, repair, hatch breaker, multi-limpet, etc.).
	String[] miningKeywords = new String[] {
			"dronecontrol",
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
//				System.out.println("is mining ship because it has " + norm);
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
		engineeringTab.applyUiFontPreferences();
		missionsTab.refreshFromSavedOverlayPreferences();
		nearbyTab.applyUiFontPreferences();
		if (combatTab != null) {
			combatTab.applyUiFontPreferences();
		}
		if (controlPanelTab != null) {
			controlPanelTab.applyUiFontPreferences();
		}
		revalidate();
		repaint();
	}

	public void applyUiFont(Font font) {
		systemTab.applyUiFont(font);
		routeTab.applyUiFont(font);
		fleetCarrierTab.applyUiFont(font);
		biologyTab.applyUiFont(font);
		miningTab.applyUiFont(font);
		engineeringTab.applyUiFont(font);
		missionsTab.refreshFromSavedOverlayPreferences();
		nearbyTab.applyUiFont(font);
		if (combatTab != null) {
			combatTab.applyUiFont(font);
		}
		if (controlPanelTab != null) {
			controlPanelTab.applyUiFont(font);
		}
		revalidate();
		repaint();
	}


}
