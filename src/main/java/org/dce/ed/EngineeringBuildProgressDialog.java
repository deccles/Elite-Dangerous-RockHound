package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import org.dce.ed.engineering.BlueprintGrade;
import org.dce.ed.engineering.EngineeringCraftStore;
import org.dce.ed.engineering.EngineeringDatabase;
import org.dce.ed.engineering.EngineeringGradeProgress;
import org.dce.ed.engineering.EngineeringGoal;
import org.dce.ed.engineering.EngineeringGoalProgress;
import org.dce.ed.engineering.EngineeringGoalProgress.ModuleUnitProgress;
import org.dce.ed.engineering.EngineeringJournalBlueprintResolver;
import org.dce.ed.engineering.EngineeringShipCatalog;
import org.dce.ed.engineering.EngineeringShipRef;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.HoverClickPoller;
import org.dce.ed.ui.OverlayCheckBoxStyle;
import org.dce.ed.ui.OverlayComboBoxStyle;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.OverlayScrollPaneSupport;

/**
 * Hierarchical engineering build progress: ship → blueprint → fitted modules,
 * engineered components currently installed, and unengineered engineerable modules
 * with a quick Add goal affordance.
 */
final class EngineeringBuildProgressDialog extends JDialog {

	private static final int HOVER_CLICK_DELAY_MS = 500;
	/** Extra width for combo chrome (border + arrow) beyond the longest item string. */
	private static final int COMBO_CHROME_PAD = 28;

	private final EngineeringDatabase database;
	private final BooleanSupplier passThroughEnabledSupplier;
	private final EngineeringShipCatalog shipCatalog;
	private final Long initialShipFilterId;
	private final JComboBox<ShipFilterItem> shipCombo;
	private final JCheckBox hideModulesWithGoalsCheck;
	private final JPanel contentPanel;
	private final JScrollPane contentScroll;
	private final Font baseFont;
	private final int fontSize;
	/** False while async load has not finished — ignore combo selection changes. */
	private boolean shipComboReady;

	private List<ModuleUnitProgress> allUnits = List.of();
	/** Ship id → engineered modules from the latest stored loadout. */
	private Map<Long, List<FittedModuleRow>> fittedByShip = Map.of();
	/** Ship id → unengineered but engineerable modules from the latest stored loadout. */
	private Map<Long, List<UnengineeredModuleRow>> unengineeredByShip = Map.of();
	private Long lastSelectedShipFilterId;
	private SwingWorker<?, ?> loadWorker;
	private final Function<AddGoalRequest, EngineeringGoal> addGoalHandler;
	private final Supplier<List<EngineeringGoal>> goalsSupplier;
	private String clientKey = "";
	/** Vertical scroll to restore after the next load/rebuild, or {@code -1} if none. */
	private int pendingRestoreScrollY = -1;

	private EngineeringBuildProgressDialog(Window owner,
			EngineeringShipCatalog shipCatalog,
			Long initialShipFilterId,
			EngineeringDatabase database,
			BooleanSupplier passThroughEnabledSupplier,
			Function<AddGoalRequest, EngineeringGoal> addGoalHandler,
			Supplier<List<EngineeringGoal>> goalsSupplier) {
		super(owner, "Engineering build progress", ModalityType.MODELESS);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		this.database = database;
		this.passThroughEnabledSupplier = passThroughEnabledSupplier;
		this.shipCatalog = shipCatalog != null ? shipCatalog : new EngineeringShipCatalog();
		this.initialShipFilterId = initialShipFilterId;
		this.addGoalHandler = addGoalHandler;
		this.goalsSupplier = goalsSupplier != null ? goalsSupplier : List::of;
		this.baseFont = OverlayPreferences.getUiFont() != null ? OverlayPreferences.getUiFont() : getFont();
		this.fontSize = OverlayPreferences.getUiFontSize();

		JPanel root = new JPanel(new BorderLayout(10, 10));
		root.setBorder(new EmptyBorder(12, 14, 12, 14));
		root.setBackground(EdoUi.User.BACKGROUND);
		root.setOpaque(true);

		JPanel north = new JPanel(new BorderLayout(8, 6));
		north.setOpaque(false);
		JLabel title = new JLabel("Build progress");
		title.setFont(baseFont.deriveFont(Font.BOLD, fontSize + 2));
		title.setForeground(EdoUi.User.MAIN_TEXT);
		north.add(title, BorderLayout.NORTH);

		JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		filterRow.setOpaque(false);
		JLabel shipLbl = new JLabel("Ship:");
		shipLbl.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		shipLbl.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
		filterRow.add(shipLbl);
		shipCombo = new JComboBox<>();
		styleShipCombo(shipCombo);
		shipComboReady = false;
		shipCombo.addActionListener(e -> {
			if (!shipComboReady) {
				return;
			}
			ShipFilterItem item = (ShipFilterItem) shipCombo.getSelectedItem();
			if (item == null || item.isSeparator() || item.shipId() == null) {
				Long prefer = lastSelectedShipFilterId != null
						? lastSelectedShipFilterId
						: initialShipFilterId;
				ShipFilterItem fallback = findShipFilterItem(prefer);
				if (fallback != null) {
					shipCombo.setSelectedItem(fallback);
				}
				return;
			}
			lastSelectedShipFilterId = item.shipId();
			rebuildContent();
		});
		filterRow.add(shipCombo);

		hideModulesWithGoalsCheck = new JCheckBox("Hide Modules w/ Goals");
		OverlayCheckBoxStyle.apply(hideModulesWithGoalsCheck);
		hideModulesWithGoalsCheck.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		hideModulesWithGoalsCheck.setToolTipText(
				"Hide All engineered and Unengineered rows that already have a matching goal");
		hideModulesWithGoalsCheck.setSelected(OverlayPreferences.isEngineeringBuildProgressHideModulesWithGoals());
		hideModulesWithGoalsCheck.addItemListener(e -> {
			if (e.getStateChange() != java.awt.event.ItemEvent.SELECTED
					&& e.getStateChange() != java.awt.event.ItemEvent.DESELECTED) {
				return;
			}
			OverlayPreferences.setEngineeringBuildProgressHideModulesWithGoals(
					hideModulesWithGoalsCheck.isSelected());
			rebuildContent();
		});
		HoverClickPoller.register(
				hideModulesWithGoalsCheck,
				HOVER_CLICK_DELAY_MS,
				() -> hideModulesWithGoalsCheck.setSelected(!hideModulesWithGoalsCheck.isSelected()),
				passThroughEnabledSupplier);
		filterRow.add(hideModulesWithGoalsCheck);

		north.add(filterRow, BorderLayout.SOUTH);

		JPanel northWithRule = new JPanel(new BorderLayout());
		northWithRule.setOpaque(false);
		northWithRule.add(north, BorderLayout.NORTH);
		northWithRule.add(sectionSeparator(), BorderLayout.SOUTH);
		root.add(northWithRule, BorderLayout.NORTH);

		contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setOpaque(false);
		contentPanel.setBorder(new EmptyBorder(4, 0, 8, 0));
		showLoading();

		contentScroll = new JScrollPane(contentPanel);
		contentScroll.setBorder(BorderFactory.createEmptyBorder());
		contentScroll.getViewport().setOpaque(false);
		contentScroll.setOpaque(false);
		OverlayScrollPaneSupport.installSubtleScrollBars(contentScroll);
		root.add(contentScroll, BorderLayout.CENTER);

		JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		south.setOpaque(false);
		JButton closeBtn = new JButton("Close");
		OverlayOutlineButtonStyle.applyChip(closeBtn, baseFont, false);
		closeBtn.addActionListener(e -> dispose());
		HoverClickPoller.register(closeBtn, HOVER_CLICK_DELAY_MS, this::dispose, passThroughEnabledSupplier);
		south.add(closeBtn);
		JPanel southWithRule = new JPanel(new BorderLayout());
		southWithRule.setOpaque(false);
		southWithRule.add(sectionSeparator(), BorderLayout.NORTH);
		southWithRule.add(south, BorderLayout.SOUTH);
		root.add(southWithRule, BorderLayout.SOUTH);

		setContentPane(root);
		setMinimumSize(new Dimension(720, 480));
		setPreferredSize(new Dimension(940, 820));
		pack();
		setSize(940, 820);
		setLocationRelativeTo(owner);
		setAlwaysOnTop(true);
	}

	static void show(Window owner,
			List<EngineeringGoal> goals,
			EngineeringShipCatalog shipCatalog,
			Long initialShipFilterId,
			EngineeringDatabase database,
			String clientKey,
			BooleanSupplier passThroughEnabledSupplier,
			Function<AddGoalRequest, EngineeringGoal> addGoalHandler,
			Supplier<List<EngineeringGoal>> goalsSupplier) {
		EngineeringBuildProgressDialog dialog = new EngineeringBuildProgressDialog(
				owner, shipCatalog, initialShipFilterId, database, passThroughEnabledSupplier,
				addGoalHandler, goalsSupplier);
		dialog.setVisible(true);
		dialog.startLoad(goals, clientKey);
	}

	private void startLoad(List<EngineeringGoal> goals, String clientKey) {
		startLoad(goals, clientKey, false);
	}

	private void startLoad(List<EngineeringGoal> goals, String clientKey, boolean preserveScroll) {
		List<EngineeringGoal> goalSnapshot = goals != null ? List.copyOf(goals) : List.of();
		String key = clientKey != null ? clientKey : "";
		this.clientKey = key;
		EngineeringDatabase db = database;
		EngineeringShipCatalog catalog = shipCatalog;
		final boolean keepScroll = preserveScroll;
		if (keepScroll) {
			pendingRestoreScrollY = contentScroll.getVerticalScrollBar().getValue();
		} else {
			pendingRestoreScrollY = -1;
		}

		if (loadWorker != null) {
			loadWorker.cancel(true);
		}
		loadWorker = new SwingWorker<DialogLoadResult, Void>() {
			@Override
			protected DialogLoadResult doInBackground() {
				if (!EngineeringCraftStore.hasCrafts(key)
						&& EngineeringCraftStore.loadLatestLoadouts(key).isEmpty()) {
					EngineeringCraftStore.reparseFromJournal(key);
				}
				List<ModuleUnitProgress> units =
						EngineeringGoalProgress.collectModuleUnitProgress(goalSnapshot, key, db);
				Map<Long, LoadoutEvent> loadouts = EngineeringCraftStore.loadLatestLoadouts(key);
				Map<Long, List<FittedModuleRow>> fitted = collectFittedModules(loadouts, db, catalog);
				Map<Long, List<UnengineeredModuleRow>> unengineered =
						collectUnengineeredModules(loadouts, catalog);
				if (catalog != null) {
					for (ModuleUnitProgress u : units) {
						if (u.shipId() >= 0 && catalog.get(u.shipId()) == null) {
							catalog.remember(new EngineeringShipRef(u.shipId(), "", u.shipLabel(), ""));
						}
					}
					for (Map.Entry<Long, LoadoutEvent> e : loadouts.entrySet()) {
						LoadoutEvent loadout = e.getValue();
						if (loadout != null && catalog.get(e.getKey()) == null) {
							catalog.remember(new EngineeringShipRef(
									e.getKey(),
									loadout.getShip() != null ? loadout.getShip() : "",
									loadout.getShipName() != null ? loadout.getShipName() : "",
									loadout.getShipIdent() != null ? loadout.getShipIdent() : ""));
						}
					}
				}
				return new DialogLoadResult(units, fitted, unengineered);
			}

			@Override
			protected void done() {
				if (isCancelled() || !isDisplayable()) {
					return;
				}
				try {
					DialogLoadResult result = get();
					allUnits = result.units();
					fittedByShip = result.fittedByShip();
					unengineeredByShip = result.unengineeredByShip();
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					allUnits = List.of();
					fittedByShip = Map.of();
					unengineeredByShip = Map.of();
				} catch (ExecutionException ex) {
					allUnits = List.of();
					fittedByShip = Map.of();
					unengineeredByShip = Map.of();
				}
				// Keep current ship filter on refresh; use initial only for the first open.
				populateShipCombo(keepScroll ? null : initialShipFilterId);
				shipComboReady = true;
				OverlayComboBoxStyle.refreshInk(shipCombo);
				rebuildContent();
				restoreScrollIfPending();
			}
		};
		loadWorker.execute();
	}

	private void restoreScrollIfPending() {
		final int y = pendingRestoreScrollY;
		if (y < 0) {
			return;
		}
		pendingRestoreScrollY = -1;
		Runnable apply = () -> {
			JScrollBar bar = contentScroll.getVerticalScrollBar();
			if (bar == null) {
				return;
			}
			int max = Math.max(0, bar.getMaximum() - bar.getVisibleAmount());
			bar.setValue(Math.min(y, max));
		};
		SwingUtilities.invokeLater(() -> {
			apply.run();
			// Second pass after layout settles (content height changes post-revalidate).
			SwingUtilities.invokeLater(apply);
		});
	}

	@Override
	public void dispose() {
		if (loadWorker != null) {
			loadWorker.cancel(true);
			loadWorker = null;
		}
		super.dispose();
	}

	private void showLoading() {
		contentPanel.removeAll();
		JLabel loading = mutedLabel("Loading build progress…");
		loading.setAlignmentX(Component.LEFT_ALIGNMENT);
		contentPanel.add(loading);
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private void populateShipCombo(Long preferShipFilterId) {
		ShipFilterItem keep = (ShipFilterItem) shipCombo.getSelectedItem();
		Long prefer = preferShipFilterId != null
				? preferShipFilterId
				: (keep != null && keep.shipId() != null ? keep.shipId() : lastSelectedShipFilterId);
		shipCombo.removeAllItems();

		Set<Long> shipsWithGoals = new HashSet<>();
		for (ModuleUnitProgress u : allUnits) {
			if (u.shipId() >= 0) {
				shipsWithGoals.add(Long.valueOf(u.shipId()));
			}
		}

		List<EngineeringShipRef> withGoals = new ArrayList<>();
		List<EngineeringShipRef> withoutGoals = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (EngineeringShipRef ref : shipCatalog.listSorted()) {
			Long id = Long.valueOf(ref.getShipId());
			seen.add(id);
			if (shipsWithGoals.contains(id)) {
				withGoals.add(ref);
			} else {
				withoutGoals.add(ref);
			}
		}
		// Fitted-only / goal-only hulls not yet in the catalog.
		for (Long shipId : shipsWithGoals) {
			if (shipId != null && seen.add(shipId) && shipId >= 0) {
				withGoals.add(new EngineeringShipRef(shipId.longValue(), "", resolveShipTitle(shipId, List.of()), ""));
			}
		}
		for (Long shipId : fittedByShip.keySet()) {
			if (shipId != null && shipId >= 0 && seen.add(shipId) && !shipsWithGoals.contains(shipId)) {
				withoutGoals.add(new EngineeringShipRef(shipId.longValue(), "", resolveShipTitle(shipId, List.of()), ""));
			}
		}
		for (Long shipId : unengineeredByShip.keySet()) {
			if (shipId != null && shipId >= 0 && seen.add(shipId) && !shipsWithGoals.contains(shipId)) {
				withoutGoals.add(new EngineeringShipRef(shipId.longValue(), "", resolveShipTitle(shipId, List.of()), ""));
			}
		}
		withGoals.sort(Comparator.comparing(r -> shipCatalog.displayLabel(r), String.CASE_INSENSITIVE_ORDER));
		withoutGoals.sort(Comparator.comparing(r -> shipCatalog.displayLabel(r), String.CASE_INSENSITIVE_ORDER));

		for (EngineeringShipRef ref : withGoals) {
			shipCombo.addItem(ShipFilterItem.ship(ref.getShipId(), shipCatalog.displayLabel(ref)));
		}
		if (!withGoals.isEmpty() && !withoutGoals.isEmpty()) {
			shipCombo.addItem(ShipFilterItem.separator());
		}
		for (EngineeringShipRef ref : withoutGoals) {
			shipCombo.addItem(ShipFilterItem.ship(ref.getShipId(), shipCatalog.displayLabel(ref)));
		}

		ShipFilterItem select = findShipFilterItem(prefer);
		if (select != null) {
			shipCombo.setSelectedItem(select);
			lastSelectedShipFilterId = select.shipId();
		} else {
			lastSelectedShipFilterId = null;
		}
		widenShipComboToFitItems();
	}

	/** Prefer {@code shipId} when present; otherwise the first concrete ship in the combo. */
	private ShipFilterItem findShipFilterItem(Long shipId) {
		if (shipId != null) {
			for (int i = 0; i < shipCombo.getItemCount(); i++) {
				ShipFilterItem item = shipCombo.getItemAt(i);
				if (item != null && !item.isSeparator() && shipId.equals(item.shipId())) {
					return item;
				}
			}
		}
		for (int i = 0; i < shipCombo.getItemCount(); i++) {
			ShipFilterItem item = shipCombo.getItemAt(i);
			if (item != null && !item.isSeparator() && item.shipId() != null) {
				return item;
			}
		}
		return null;
	}

	private void widenShipComboToFitItems() {
		Font font = shipCombo.getFont();
		int maxText = 0;
		java.awt.FontMetrics fm = shipCombo.getFontMetrics(
				font != null ? font : new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		for (int i = 0; i < shipCombo.getItemCount(); i++) {
			ShipFilterItem item = shipCombo.getItemAt(i);
			if (item == null || item.isSeparator()) {
				continue;
			}
			maxText = Math.max(maxText, fm.stringWidth(item.label()));
		}
		Dimension pref = shipCombo.getPreferredSize();
		int width = Math.max(260, maxText + 48);
		shipCombo.setPreferredSize(new Dimension(width, pref.height));
		shipCombo.revalidate();
	}

	private void styleShipCombo(JComboBox<ShipFilterItem> combo) {
		OverlayComboBoxStyle.apply(combo, baseFont.deriveFont(Font.PLAIN, fontSize));
		combo.setMaximumRowCount(12);
		Dimension pref = combo.getPreferredSize();
		combo.setPreferredSize(new Dimension(Math.max(260, pref.width), pref.height));
		OverlayScrollPaneSupport.installSubtleScrollBarsOnComboPopup(combo);
		combo.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				if (value instanceof ShipFilterItem item && item.isSeparator()) {
					JPanel line = new JPanel() {
						@Override
						protected void paintComponent(java.awt.Graphics g) {
							super.paintComponent(g);
							g.setColor(EdoUi.Internal.separatorLineStrong());
							int y = getHeight() / 2;
							g.drawLine(6, y, getWidth() - 6, y);
						}
					};
					line.setOpaque(true);
					line.setBackground(EdoUi.User.PANEL_BG);
					line.setPreferredSize(new Dimension(1, Math.max(8, fontSize / 2)));
					return line;
				}
				Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				setEnabled(true);
				if (value instanceof ShipFilterItem item) {
					setText(item.label());
				}
				setForeground(EdoUi.User.MAIN_TEXT);
				setBackground(isSelected ? EdoUi.ED_ORANGE_LESS_TRANS : EdoUi.User.PANEL_BG);
				setOpaque(true);
				return c;
			}
		});
	}

	private void rebuildContent() {
		contentPanel.removeAll();
		ShipFilterItem filter = (ShipFilterItem) shipCombo.getSelectedItem();
		Long shipFilterId = filter != null && filter.shipId() != null ? filter.shipId() : null;
		if (shipFilterId == null) {
			JLabel empty = mutedLabel("Select a ship to view build progress.");
			empty.setAlignmentX(Component.LEFT_ALIGNMENT);
			contentPanel.add(empty);
			contentPanel.revalidate();
			contentPanel.repaint();
			return;
		}

		List<ModuleUnitProgress> visibleGoals = new ArrayList<>();
		for (ModuleUnitProgress u : allUnits) {
			if (u.shipId() == shipFilterId.longValue()) {
				visibleGoals.add(u);
			}
		}

		Map<Long, List<FittedModuleRow>> visibleFitted = new LinkedHashMap<>();
		Map<Long, List<UnengineeredModuleRow>> visibleUnengineered = new LinkedHashMap<>();
		List<FittedModuleRow> fittedRows = fittedByShip.getOrDefault(shipFilterId, List.of());
		if (!fittedRows.isEmpty()) {
			visibleFitted.put(shipFilterId, fittedRows);
		}
		List<UnengineeredModuleRow> unRows = unengineeredByShip.getOrDefault(shipFilterId, List.of());
		if (!unRows.isEmpty()) {
			visibleUnengineered.put(shipFilterId, unRows);
		}

		if (hideModulesWithGoalsCheck.isSelected()) {
			visibleFitted = filterFittedWithoutGoals(visibleFitted);
			visibleUnengineered = filterUnengineeredWithoutGoals(visibleUnengineered);
		}

		if (visibleGoals.isEmpty() && visibleFitted.isEmpty() && visibleUnengineered.isEmpty()) {
			JLabel empty = mutedLabel(allUnits.isEmpty() && fittedByShip.isEmpty() && unengineeredByShip.isEmpty()
					? "No engineering goals or fitted modules yet."
					: (hideModulesWithGoalsCheck.isSelected()
							? "Nothing to show (modules with goals are hidden)."
							: "Nothing for this ship."));
			empty.setAlignmentX(Component.LEFT_ALIGNMENT);
			contentPanel.add(empty);
			contentPanel.revalidate();
			contentPanel.repaint();
			return;
		}

		Map<Long, List<ModuleUnitProgress>> goalsByShip = new LinkedHashMap<>();
		for (ModuleUnitProgress u : visibleGoals) {
			goalsByShip.computeIfAbsent(Long.valueOf(u.shipId()), k -> new ArrayList<>()).add(u);
		}

		List<Long> shipOrder = orderedShipIds(goalsByShip, visibleFitted, visibleUnengineered);
		boolean firstShip = true;
		for (Long shipIdObj : shipOrder) {
			List<ModuleUnitProgress> shipUnits = goalsByShip.getOrDefault(shipIdObj, List.of());
			List<FittedModuleRow> fitted = visibleFitted.getOrDefault(shipIdObj, List.of());
			List<UnengineeredModuleRow> unengineered = visibleUnengineered.getOrDefault(shipIdObj, List.of());
			if (shipUnits.isEmpty() && fitted.isEmpty() && unengineered.isEmpty()) {
				continue;
			}
			if (!firstShip) {
				contentPanel.add(Box.createVerticalStrut(16));
			}
			firstShip = false;

			boolean firstBlock = true;
			if (!fitted.isEmpty()) {
				firstBlock = false;
				contentPanel.add(sectionHeadline("All engineered"));
				contentPanel.add(Box.createVerticalStrut(4));
				contentPanel.add(createFittedTablePanel(fitted));
			}
			if (!unengineered.isEmpty()) {
				if (!firstBlock) {
					contentPanel.add(sectionSeparator());
				}
				firstBlock = false;
				contentPanel.add(sectionHeadline("Unengineered"));
				contentPanel.add(Box.createVerticalStrut(4));
				contentPanel.add(createUnengineeredPanel(unengineered));
			}
			if (!shipUnits.isEmpty()) {
				if (!firstBlock) {
					contentPanel.add(sectionSeparator());
				}
				contentPanel.add(sectionHeadline("Goal progress"));
				contentPanel.add(Box.createVerticalStrut(4));
				appendGoalBlocks(shipUnits);
			}
		}
		contentPanel.add(Box.createVerticalGlue());
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private void appendGoalBlocks(List<ModuleUnitProgress> shipUnits) {
		Map<String, List<ModuleUnitProgress>> byGoal = new LinkedHashMap<>();
		for (ModuleUnitProgress u : shipUnits) {
			String key = u.moduleType() + "\0" + u.blueprintName() + "\0" + u.targetGrade();
			byGoal.computeIfAbsent(key, k -> new ArrayList<>()).add(u);
		}
		boolean firstGoal = true;
		for (List<ModuleUnitProgress> goalUnits : byGoal.values()) {
			if (!firstGoal) {
				contentPanel.add(Box.createVerticalStrut(12));
			}
			firstGoal = false;
			ModuleUnitProgress head = goalUnits.get(0);
			contentPanel.add(goalHeadline(head));
			contentPanel.add(Box.createVerticalStrut(4));
			for (ModuleUnitProgress unit : goalUnits) {
				contentPanel.add(moduleSubline(unit));
			}
		}
	}

	private List<Long> orderedShipIds(Map<Long, List<ModuleUnitProgress>> goalsByShip,
			Map<Long, List<FittedModuleRow>> fittedByShipVisible,
			Map<Long, List<UnengineeredModuleRow>> unengineeredByShipVisible) {
		Map<Long, String> labels = new LinkedHashMap<>();
		for (Long id : goalsByShip.keySet()) {
			labels.put(id, resolveShipTitle(id.longValue(), goalsByShip.get(id)));
		}
		for (Long id : fittedByShipVisible.keySet()) {
			labels.putIfAbsent(id, resolveShipTitle(id.longValue(), List.of()));
		}
		for (Long id : unengineeredByShipVisible.keySet()) {
			labels.putIfAbsent(id, resolveShipTitle(id.longValue(), List.of()));
		}
		List<Long> ordered = new ArrayList<>(labels.keySet());
		ordered.sort(Comparator.comparing(labels::get, String.CASE_INSENSITIVE_ORDER));
		return ordered;
	}

	private String resolveShipTitle(long shipId, List<ModuleUnitProgress> shipUnits) {
		if (shipId >= 0) {
			EngineeringShipRef ref = shipCatalog.get(shipId);
			if (ref != null) {
				return shipCatalog.displayLabel(ref);
			}
		}
		if (!shipUnits.isEmpty() && !shipUnits.get(0).shipLabel().isBlank()) {
			return shipUnits.get(0).shipLabel();
		}
		List<FittedModuleRow> fitted = fittedByShip.get(Long.valueOf(shipId));
		if (fitted != null && !fitted.isEmpty() && !fitted.get(0).shipLabel().isBlank()) {
			return fitted.get(0).shipLabel();
		}
		List<UnengineeredModuleRow> unengineered = unengineeredByShip.get(Long.valueOf(shipId));
		if (unengineered != null && !unengineered.isEmpty() && !unengineered.get(0).shipLabel().isBlank()) {
			return unengineered.get(0).shipLabel();
		}
		return shipId >= 0 ? "Ship #" + shipId : "Unassigned";
	}

	private JPanel sectionHeadline(String title) {
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, fontSize + 16));
		JLabel label = new JLabel(title);
		label.setFont(baseFont.deriveFont(Font.BOLD, fontSize));
		label.setForeground(EdoUi.User.MAIN_TEXT);
		row.add(label, BorderLayout.WEST);
		return row;
	}

	/** Thin non-interactive horizontal rule (Route-tab style), between content sections. */
	private JComponent sectionSeparator() {
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(false);
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.setBorder(new EmptyBorder(10, 0, 10, 0));
		wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		JPanel rule = new JPanel() {
			@Override
			public Dimension getMaximumSize() {
				Dimension d = super.getMaximumSize();
				return new Dimension(Integer.MAX_VALUE, 1);
			}
		};
		rule.setOpaque(true);
		rule.setBackground(EdoUi.ED_ORANGE_TRANS);
		rule.setPreferredSize(new Dimension(10, 1));
		wrap.add(rule, BorderLayout.CENTER);
		return wrap;
	}

	private JPanel goalHeadline(ModuleUnitProgress unit) {
		JPanel row = new JPanel(new BorderLayout(10, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, fontSize + 16));
		JLabel left = new JLabel(unit.goalHeadline());
		left.setFont(baseFont.deriveFont(Font.BOLD, fontSize));
		left.setForeground(EdoUi.User.MAIN_TEXT);
		JLabel right = new JLabel("Target G" + unit.targetGrade());
		right.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		right.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
		right.setHorizontalAlignment(SwingConstants.RIGHT);
		row.add(left, BorderLayout.WEST);
		row.add(right, BorderLayout.EAST);
		return row;
	}

	private JPanel moduleSubline(ModuleUnitProgress unit) {
		return twoColumnRow(
				unit.moduleLabel(),
				unit.installed() ? gradeProgressText(unit) : "",
				unit.installed()
						? EdoUi.Internal.MAIN_TEXT_ALPHA_220
						: EdoUi.withAlpha(EdoUi.User.MAIN_TEXT, 140),
				gradeColor(unit));
	}

	private JComponent createFittedTablePanel(List<FittedModuleRow> fitted) {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setOpaque(false);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(0, 0, 2, 8);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.gridy = 0;

		JLabel[] headers = new JLabel[6];
		headers[0] = headerLabel("Module");
		gbc.gridx = 0;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		panel.add(headers[0], gbc);

		headers[1] = headerLabel("#");
		headers[1].setHorizontalAlignment(SwingConstants.CENTER);
		gbc.gridx = 1;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		panel.add(headers[1], gbc);

		headers[2] = headerLabel("Blueprint");
		gbc.gridx = 2;
		panel.add(headers[2], gbc);

		headers[3] = headerLabel("Experimental");
		gbc.gridx = 3;
		panel.add(headers[3], gbc);

		headers[4] = headerLabel("Grade");
		headers[4].setHorizontalAlignment(SwingConstants.CENTER);
		gbc.gridx = 4;
		panel.add(headers[4], gbc);

		headers[5] = headerLabel(" ");
		gbc.gridx = 5;
		panel.add(headers[5], gbc);

		List<JLabel> countLabels = new ArrayList<>();
		List<JLabel> blueprintLabels = new ArrayList<>();
		List<JLabel> gradeLabels = new ArrayList<>();
		List<JLabel> experimentalLabels = new ArrayList<>();
		List<JButton> upgradeButtons = new ArrayList<>();

		gbc.insets = new Insets(0, 0, 4, 8);
		int y = 1;
		for (FittedModuleRow row : fitted) {
			gbc.gridy = y++;

			JLabel moduleLbl = new JLabel(row.moduleLabel());
			moduleLbl.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
			moduleLbl.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
			moduleLbl.setToolTipText(row.moduleType() != null && !row.moduleType().isBlank()
					? row.moduleType()
					: row.moduleLabel());
			gbc.gridx = 0;
			gbc.weightx = 1;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			panel.add(moduleLbl, gbc);

			JLabel countLbl = new JLabel(Integer.toString(Math.max(1, row.count())));
			countLbl.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
			countLbl.setForeground(EdoUi.User.MAIN_TEXT);
			countLbl.setHorizontalAlignment(SwingConstants.CENTER);
			gbc.gridx = 1;
			gbc.weightx = 0;
			gbc.fill = GridBagConstraints.NONE;
			panel.add(countLbl, gbc);
			countLabels.add(countLbl);

			JLabel blueprintLbl = new JLabel(blankDash(row.blueprintLabel()));
			blueprintLbl.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
			blueprintLbl.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
			blueprintLbl.setToolTipText(blankDash(row.blueprintLabel()));
			gbc.gridx = 2;
			panel.add(blueprintLbl, gbc);
			blueprintLabels.add(blueprintLbl);

			String experimental = blankDash(row.experimentalLabel());
			JLabel experimentalLbl = new JLabel(experimental);
			experimentalLbl.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
			experimentalLbl.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
			experimentalLbl.setToolTipText("—".equals(experimental) ? null : experimental);
			gbc.gridx = 3;
			panel.add(experimentalLbl, gbc);
			experimentalLabels.add(experimentalLbl);

			JLabel gradeLbl = new JLabel(blankDash(row.gradeLabel()));
			gradeLbl.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
			gradeLbl.setForeground(EdoUi.User.MAIN_TEXT);
			gradeLbl.setHorizontalAlignment(SwingConstants.CENTER);
			gbc.gridx = 4;
			panel.add(gradeLbl, gbc);
			gradeLabels.add(gradeLbl);

			gbc.gridx = 5;
			if (row.canUpgrade() && addGoalHandler != null && !hasExistingGoalFor(row)) {
				JButton upgradeBtn = new JButton("+ Upgrade Goal");
				OverlayOutlineButtonStyle.applyChip(upgradeBtn, baseFont, false);
				upgradeBtn.setToolTipText("Add a goal to finish this module at G" + row.maxGrade());
				Runnable upgradeAction = () -> addGoalFromFittedUpgrade(row);
				upgradeBtn.addActionListener(e -> upgradeAction.run());
				HoverClickPoller.register(upgradeBtn, HOVER_CLICK_DELAY_MS, upgradeAction, passThroughEnabledSupplier);
				panel.add(upgradeBtn, gbc);
				upgradeButtons.add(upgradeBtn);
			} else {
				JLabel spacer = new JLabel(" ");
				spacer.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
				panel.add(spacer, gbc);
			}
		}

		equalizeFittedColumns(headers, countLabels, blueprintLabels, gradeLabels, experimentalLabels, upgradeButtons);

		gbc.gridx = 0;
		gbc.gridy = y;
		gbc.gridwidth = 6;
		gbc.weightx = 1;
		gbc.weighty = 1;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.insets = new Insets(0, 0, 0, 0);
		JPanel filler = new JPanel();
		filler.setOpaque(false);
		panel.add(filler, gbc);

		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		return panel;
	}

	private static String blankDash(String value) {
		return value == null || value.isBlank() ? "—" : value;
	}

	private void equalizeFittedColumns(JLabel[] headers,
			List<JLabel> countLabels,
			List<JLabel> blueprintLabels,
			List<JLabel> gradeLabels,
			List<JLabel> experimentalLabels,
			List<JButton> upgradeButtons) {
		FontMetrics fm = getFontMetrics(baseFont.deriveFont(Font.PLAIN, fontSize));
		FontMetrics headerFm = getFontMetrics(baseFont.deriveFont(Font.BOLD, fontSize));
		int countW = Math.max(headerFm.stringWidth("#"), fm.stringWidth("99")) + 12;
		int bpW = Math.max(120, headers[2] != null ? headers[2].getPreferredSize().width : 0);
		int expW = Math.max(120, headers[3] != null ? headers[3].getPreferredSize().width : 0);
		int gradeW = Math.max(48, headers[4] != null ? headers[4].getPreferredSize().width : 0);
		int upgradeW = 80;
		int h = fontSize + 8;
		for (JLabel label : blueprintLabels) {
			bpW = Math.max(bpW, label.getPreferredSize().width);
			h = Math.max(h, label.getPreferredSize().height);
		}
		for (JLabel label : experimentalLabels) {
			expW = Math.max(expW, label.getPreferredSize().width);
			h = Math.max(h, label.getPreferredSize().height);
		}
		for (JLabel label : gradeLabels) {
			gradeW = Math.max(gradeW, label.getPreferredSize().width);
			h = Math.max(h, label.getPreferredSize().height);
		}
		for (JButton btn : upgradeButtons) {
			Dimension pref = btn.getPreferredSize();
			upgradeW = Math.max(upgradeW, pref.width);
			h = Math.max(h, pref.height);
		}
		Dimension countSize = new Dimension(countW, h);
		Dimension bpSize = new Dimension(bpW, h);
		Dimension expSize = new Dimension(expW, h);
		Dimension gradeSize = new Dimension(gradeW + 8, h);
		Dimension upgradeSize = new Dimension(upgradeW, h);
		if (headers[1] != null) {
			headers[1].setPreferredSize(countSize);
			headers[1].setMinimumSize(countSize);
			headers[1].setHorizontalAlignment(SwingConstants.CENTER);
		}
		if (headers[2] != null) {
			headers[2].setPreferredSize(bpSize);
		}
		if (headers[3] != null) {
			headers[3].setPreferredSize(expSize);
		}
		if (headers[4] != null) {
			headers[4].setPreferredSize(gradeSize);
			headers[4].setHorizontalAlignment(SwingConstants.CENTER);
		}
		if (headers[5] != null && !upgradeButtons.isEmpty()) {
			headers[5].setPreferredSize(upgradeSize);
			headers[5].setMinimumSize(upgradeSize);
		}
		for (JLabel label : countLabels) {
			label.setPreferredSize(countSize);
			label.setMinimumSize(countSize);
		}
		for (JLabel label : blueprintLabels) {
			label.setPreferredSize(bpSize);
			label.setMinimumSize(bpSize);
		}
		for (JLabel label : experimentalLabels) {
			label.setPreferredSize(expSize);
			label.setMinimumSize(expSize);
		}
		for (JLabel label : gradeLabels) {
			label.setPreferredSize(gradeSize);
			label.setMinimumSize(gradeSize);
			label.setHorizontalAlignment(SwingConstants.CENTER);
		}
		for (JButton btn : upgradeButtons) {
			btn.setPreferredSize(upgradeSize);
			btn.setMinimumSize(upgradeSize);
			btn.setMaximumSize(upgradeSize);
		}
	}

	private JComponent createUnengineeredPanel(List<UnengineeredModuleRow> rows) {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setOpaque(false);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(0, 0, 4, 8);
		gbc.anchor = GridBagConstraints.WEST;

		List<JLabel> countLabels = new ArrayList<>();
		List<JComboBox<String>> blueprintCombos = new ArrayList<>();
		List<JComboBox<String>> experimentalCombos = new ArrayList<>();
		List<JButton> addButtons = new ArrayList<>();
		JLabel[] headers = new JLabel[5];
		int maxExperimentalTextW = 0;
		FontMetrics fm = getFontMetrics(baseFont.deriveFont(Font.PLAIN, fontSize));

		gbc.gridy = 0;
		gbc.insets = new Insets(0, 0, 2, 8);
		addUnengineeredHeader(panel, gbc, headers);

		gbc.insets = new Insets(0, 0, 4, 8);
		int y = 1;
		for (UnengineeredModuleRow row : rows) {
			gbc.gridy = y++;
			maxExperimentalTextW = Math.max(maxExperimentalTextW,
					maxExperimentalTextWidth(row.moduleType(), fm));
			addUnengineeredDataRow(panel, gbc, row, countLabels, blueprintCombos, experimentalCombos, addButtons);
		}

		equalizeUnengineeredColumns(headers, countLabels, blueprintCombos, experimentalCombos,
				addButtons, maxExperimentalTextW);

		// Stretch horizontally with the dialog; pin content to the top.
		gbc.gridx = 0;
		gbc.gridy = y;
		gbc.gridwidth = 5;
		gbc.weightx = 1;
		gbc.weighty = 1;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.insets = new Insets(0, 0, 0, 0);
		JPanel filler = new JPanel();
		filler.setOpaque(false);
		panel.add(filler, gbc);

		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		return panel;
	}

	private void addUnengineeredHeader(JPanel panel, GridBagConstraints gbc, JLabel[] headers) {
		JLabel moduleHdr = headerLabel("Module");
		headers[0] = moduleHdr;
		gbc.gridx = 0;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		panel.add(moduleHdr, gbc);

		JLabel countHdr = headerLabel("#");
		countHdr.setHorizontalAlignment(SwingConstants.CENTER);
		headers[1] = countHdr;
		gbc.gridx = 1;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		panel.add(countHdr, gbc);

		JLabel blueprintHdr = headerLabel("Blueprint");
		headers[2] = blueprintHdr;
		gbc.gridx = 2;
		panel.add(blueprintHdr, gbc);

		JLabel expHdr = headerLabel("Experimental");
		headers[3] = expHdr;
		gbc.gridx = 3;
		panel.add(expHdr, gbc);

		JLabel addHdr = headerLabel(" ");
		headers[4] = addHdr;
		gbc.gridx = 4;
		panel.add(addHdr, gbc);
	}

	private JLabel headerLabel(String text) {
		JLabel label = new JLabel(text);
		label.setFont(baseFont.deriveFont(Font.BOLD, fontSize));
		label.setForeground(EdoUi.User.MAIN_TEXT);
		return label;
	}

	private void addUnengineeredDataRow(JPanel panel,
			GridBagConstraints gbc,
			UnengineeredModuleRow row,
			List<JLabel> countLabels,
			List<JComboBox<String>> blueprintCombos,
			List<JComboBox<String>> experimentalCombos,
			List<JButton> addButtons) {
		JLabel moduleLbl = new JLabel(row.moduleLabel());
		moduleLbl.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		moduleLbl.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
		moduleLbl.setToolTipText(row.moduleType());
		gbc.gridx = 0;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		panel.add(moduleLbl, gbc);

		JLabel countLbl = new JLabel(Integer.toString(Math.max(1, row.count())));
		countLbl.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		countLbl.setForeground(EdoUi.User.MAIN_TEXT);
		countLbl.setHorizontalAlignment(SwingConstants.CENTER);
		gbc.gridx = 1;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		panel.add(countLbl, gbc);
		countLabels.add(countLbl);

		JComboBox<String> blueprintCombo = new JComboBox<>();
		styleActionCombo(blueprintCombo);
		for (String name : blueprintNamesForModuleType(row.moduleType())) {
			blueprintCombo.addItem(name);
		}
		boolean hasBlueprints = blueprintCombo.getItemCount() > 0;
		blueprintCombo.setEnabled(hasBlueprints);
		gbc.gridx = 2;
		panel.add(blueprintCombo, gbc);
		blueprintCombos.add(blueprintCombo);

		JComboBox<String> experimentalCombo = new JComboBox<>();
		styleActionCombo(experimentalCombo);
		experimentalCombo.addItem("(none)");
		experimentalCombo.setEnabled(hasBlueprints);
		gbc.gridx = 3;
		panel.add(experimentalCombo, gbc);
		experimentalCombos.add(experimentalCombo);

		Runnable refreshExperimentals = () -> {
			String blueprint = (String) blueprintCombo.getSelectedItem();
			String keep = (String) experimentalCombo.getSelectedItem();
			experimentalCombo.removeAllItems();
			experimentalCombo.addItem("(none)");
			if (blueprint != null && !blueprint.isBlank()) {
				for (String name : experimentalNamesFor(row.moduleType(), blueprint)) {
					experimentalCombo.addItem(name);
				}
			}
			if (keep != null) {
				experimentalCombo.setSelectedItem(keep);
			}
			if (experimentalCombo.getSelectedItem() == null && experimentalCombo.getItemCount() > 0) {
				experimentalCombo.setSelectedIndex(0);
			}
		};
		blueprintCombo.addActionListener(e -> refreshExperimentals.run());
		refreshExperimentals.run();

		JButton addBtn = new JButton("Add goal");
		OverlayOutlineButtonStyle.applyChip(addBtn, baseFont, false);
		addBtn.setEnabled(hasBlueprints && addGoalHandler != null);
		Runnable addAction = () -> addGoalFromUnengineered(row, blueprintCombo, experimentalCombo);
		addBtn.addActionListener(e -> addAction.run());
		HoverClickPoller.register(addBtn, HOVER_CLICK_DELAY_MS, addAction, passThroughEnabledSupplier);
		gbc.gridx = 4;
		panel.add(addBtn, gbc);
		addButtons.add(addBtn);
	}

	private void equalizeUnengineeredColumns(JLabel[] headers,
			List<JLabel> countLabels,
			List<JComboBox<String>> blueprintCombos,
			List<JComboBox<String>> experimentalCombos,
			List<JButton> addButtons,
			int maxExperimentalTextW) {
		FontMetrics fm = getFontMetrics(baseFont.deriveFont(Font.PLAIN, fontSize));
		FontMetrics headerFm = getFontMetrics(baseFont.deriveFont(Font.BOLD, fontSize));
		int countW = Math.max(headerFm.stringWidth("#"), fm.stringWidth("99")) + 12;
		int bpW = Math.max(headerFm.stringWidth("Blueprint"), 80);
		int expW = Math.max(headerFm.stringWidth("Experimental"), Math.max(80, maxExperimentalTextW));
		int addW = Math.max(headerFm.stringWidth("Add goal"), 80);
		int h = fontSize + 10;

		for (JComboBox<String> combo : blueprintCombos) {
			bpW = Math.max(bpW, measureComboContentWidth(combo, fm));
			h = Math.max(h, Math.max(combo.getPreferredSize().height, fontSize + 10));
		}
		for (JComboBox<String> combo : experimentalCombos) {
			expW = Math.max(expW, measureComboContentWidth(combo, fm));
			h = Math.max(h, Math.max(combo.getPreferredSize().height, fontSize + 10));
		}
		for (JButton btn : addButtons) {
			Dimension pref = btn.getPreferredSize();
			addW = Math.max(addW, pref.width);
			h = Math.max(h, pref.height);
		}

		// Cap so Module keeps breathing room in the ~940 dialog.
		bpW = Math.min(Math.max(bpW + COMBO_CHROME_PAD, 100), 220);
		expW = Math.min(Math.max(expW + COMBO_CHROME_PAD, 100), 200);

		Dimension countSize = new Dimension(countW, h);
		Dimension bpSize = new Dimension(bpW, h);
		Dimension expSize = new Dimension(expW, h);
		Dimension addSize = new Dimension(addW, h);

		if (headers[1] != null) {
			headers[1].setPreferredSize(countSize);
			headers[1].setMinimumSize(countSize);
			headers[1].setHorizontalAlignment(SwingConstants.CENTER);
		}
		if (headers[2] != null) {
			headers[2].setPreferredSize(bpSize);
			headers[2].setMinimumSize(bpSize);
		}
		if (headers[3] != null) {
			headers[3].setPreferredSize(expSize);
			headers[3].setMinimumSize(expSize);
		}
		if (headers[4] != null) {
			headers[4].setPreferredSize(addSize);
			headers[4].setMinimumSize(addSize);
		}

		for (JLabel label : countLabels) {
			label.setPreferredSize(countSize);
			label.setMinimumSize(countSize);
		}
		for (JComboBox<String> combo : blueprintCombos) {
			combo.setPreferredSize(bpSize);
			combo.setMinimumSize(bpSize);
			combo.setMaximumSize(bpSize);
		}
		for (JComboBox<String> combo : experimentalCombos) {
			combo.setPreferredSize(expSize);
			combo.setMinimumSize(expSize);
			combo.setMaximumSize(expSize);
		}
		for (JButton btn : addButtons) {
			btn.setPreferredSize(addSize);
			btn.setMinimumSize(addSize);
			btn.setMaximumSize(addSize);
		}
	}

	private int maxExperimentalTextWidth(String moduleType, FontMetrics fm) {
		int max = fm.stringWidth("(none)");
		for (String blueprint : blueprintNamesForModuleType(moduleType)) {
			for (String name : experimentalNamesFor(moduleType, blueprint)) {
				if (name != null && !name.isBlank()) {
					max = Math.max(max, fm.stringWidth(name));
				}
			}
		}
		return max;
	}

	private static int measureComboContentWidth(JComboBox<String> combo, FontMetrics fm) {
		int max = 0;
		for (int i = 0; i < combo.getItemCount(); i++) {
			String item = combo.getItemAt(i);
			if (item != null && !item.isBlank()) {
				max = Math.max(max, fm.stringWidth(item));
			}
		}
		return max;
	}

	private void styleActionCombo(JComboBox<String> combo) {
		OverlayComboBoxStyle.apply(combo, baseFont.deriveFont(Font.PLAIN, fontSize));
		combo.setMaximumRowCount(10);
		OverlayScrollPaneSupport.installSubtleScrollBarsOnComboPopup(combo);
		combo.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				setEnabled(true);
				setForeground(EdoUi.User.MAIN_TEXT);
				setBackground(isSelected ? EdoUi.ED_ORANGE_LESS_TRANS : EdoUi.User.PANEL_BG);
				setOpaque(true);
				return c;
			}
		});
	}

	private List<String> blueprintNamesForModuleType(String moduleType) {
		if (database == null || moduleType == null || moduleType.isBlank()) {
			return List.of();
		}
		Map<String, Boolean> names = new LinkedHashMap<>();
		for (BlueprintGrade bp : database.getAllBlueprints()) {
			if (bp == null || bp.isExperimental()) {
				continue;
			}
			if (EngineeringJournalBlueprintResolver.sameModuleType(moduleType, bp.getModuleType())) {
				names.putIfAbsent(bp.getName(), Boolean.TRUE);
			}
		}
		return List.copyOf(names.keySet());
	}

	private List<String> experimentalNamesFor(String moduleType, String blueprintName) {
		if (database == null || moduleType == null || moduleType.isBlank()
				|| blueprintName == null || blueprintName.isBlank()) {
			return List.of();
		}
		Map<String, Boolean> names = new LinkedHashMap<>();
		for (BlueprintGrade bp : database.experimentalsFor(moduleType, blueprintName)) {
			if (bp != null && bp.getName() != null && !bp.getName().isBlank()) {
				names.putIfAbsent(bp.getName(), Boolean.TRUE);
			}
		}
		return List.copyOf(names.keySet());
	}

	private void addGoalFromUnengineered(UnengineeredModuleRow row,
			JComboBox<String> blueprintCombo,
			JComboBox<String> experimentalCombo) {
		if (addGoalHandler == null || row == null) {
			return;
		}
		String blueprint = blueprintCombo != null ? (String) blueprintCombo.getSelectedItem() : null;
		String experimental = experimentalCombo != null ? (String) experimentalCombo.getSelectedItem() : null;
		if (experimental != null && "(none)".equalsIgnoreCase(experimental)) {
			experimental = null;
		}
		EngineeringShipRef ship = shipCatalog.get(row.shipId());
		if (ship == null) {
			ship = new EngineeringShipRef(row.shipId(), "", row.shipLabel(), "");
		}
		EngineeringGoal created = addGoalHandler.apply(new AddGoalRequest(
				ship,
				row.moduleType(),
				row.moduleLabel(),
				blueprint,
				experimental,
				Math.max(1, row.count())));
		if (created != null) {
			startLoad(goalsSupplier.get(), clientKey, true);
		}
	}

	private void addGoalFromFittedUpgrade(FittedModuleRow row) {
		if (addGoalHandler == null || row == null || !row.canUpgrade() || hasExistingGoalFor(row)) {
			return;
		}
		EngineeringShipRef ship = shipCatalog.get(row.shipId());
		if (ship == null) {
			ship = new EngineeringShipRef(row.shipId(), "", row.shipLabel(), "");
		}
		String experimental = row.experimentalLabel();
		if (experimental != null && ("—".equals(experimental) || experimental.isBlank())) {
			experimental = null;
		}
		EngineeringGoal created = addGoalHandler.apply(new AddGoalRequest(
				ship,
				row.moduleType(),
				row.moduleLabel(),
				row.blueprintLabel(),
				experimental,
				Math.max(1, row.count())));
		if (created != null) {
			startLoad(goalsSupplier.get(), clientKey, true);
		}
	}

	/** True when a goal already covers this ship + module type + blueprint. */
	private boolean hasExistingGoalFor(FittedModuleRow row) {
		if (row == null || allUnits == null || allUnits.isEmpty()) {
			return false;
		}
		String rowBp = EngineeringJournalBlueprintResolver.normalizeToken(row.blueprintLabel());
		for (ModuleUnitProgress unit : allUnits) {
			if (unit == null || unit.shipId() != row.shipId()) {
				continue;
			}
			if (!EngineeringJournalBlueprintResolver.sameModuleType(unit.moduleType(), row.moduleType())) {
				continue;
			}
			String goalBp = EngineeringJournalBlueprintResolver.normalizeToken(unit.blueprintName());
			if (!rowBp.isEmpty() && rowBp.equals(goalBp)) {
				return true;
			}
		}
		return false;
	}

	/** True when any goal on this ship covers this module type (any blueprint). */
	private boolean hasExistingGoalForModuleType(long shipId, String moduleType) {
		if (moduleType == null || moduleType.isBlank() || allUnits == null || allUnits.isEmpty()) {
			return false;
		}
		for (ModuleUnitProgress unit : allUnits) {
			if (unit == null || unit.shipId() != shipId) {
				continue;
			}
			if (EngineeringJournalBlueprintResolver.sameModuleType(unit.moduleType(), moduleType)) {
				return true;
			}
		}
		return false;
	}

	private Map<Long, List<FittedModuleRow>> filterFittedWithoutGoals(
			Map<Long, List<FittedModuleRow>> byShip) {
		Map<Long, List<FittedModuleRow>> out = new LinkedHashMap<>();
		for (Map.Entry<Long, List<FittedModuleRow>> e : byShip.entrySet()) {
			List<FittedModuleRow> kept = new ArrayList<>();
			for (FittedModuleRow row : e.getValue()) {
				if (!hasExistingGoalFor(row)) {
					kept.add(row);
				}
			}
			if (!kept.isEmpty()) {
				out.put(e.getKey(), List.copyOf(kept));
			}
		}
		return out;
	}

	private Map<Long, List<UnengineeredModuleRow>> filterUnengineeredWithoutGoals(
			Map<Long, List<UnengineeredModuleRow>> byShip) {
		Map<Long, List<UnengineeredModuleRow>> out = new LinkedHashMap<>();
		for (Map.Entry<Long, List<UnengineeredModuleRow>> e : byShip.entrySet()) {
			List<UnengineeredModuleRow> kept = new ArrayList<>();
			for (UnengineeredModuleRow row : e.getValue()) {
				if (!hasExistingGoalForModuleType(row.shipId(), row.moduleType())) {
					kept.add(row);
				}
			}
			if (!kept.isEmpty()) {
				out.put(e.getKey(), List.copyOf(kept));
			}
		}
		return out;
	}

	private JPanel twoColumnRow(String leftText, String rightText,
			java.awt.Color leftColor, java.awt.Color rightColor) {
		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, fontSize + 14));
		row.setBorder(new EmptyBorder(1, 18, 1, 0));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridy = 0;
		gbc.insets = new Insets(0, 0, 0, 10);
		gbc.anchor = GridBagConstraints.WEST;

		JLabel left = new JLabel(leftText != null ? leftText : "");
		left.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		left.setForeground(leftColor);
		gbc.gridx = 0;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		row.add(left, gbc);

		JLabel right = new JLabel(rightText != null ? rightText : "");
		right.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		right.setForeground(rightColor);
		gbc.gridx = 1;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.EAST;
		row.add(right, gbc);
		return row;
	}

	private String gradeProgressText(ModuleUnitProgress unit) {
		EngineeringGoal g = unit.unit();
		if (g == null) {
			return "";
		}
		String progress;
		int from = g.getFromGrade();
		int target = unit.targetGrade();
		int crafts = g.getCraftsAtCurrentGrade();
		if (g.isComplete() || from >= target) {
			progress = "G" + target + " done";
		} else if (from <= 0 && crafts <= 0) {
			progress = "Not started";
		} else if (crafts <= 0) {
			progress = "G" + from;
		} else {
			progress = "G" + from + " · G" + (from + 1) + " "
					+ crafts + "/" + EngineeringGradeProgress.ROLLS_PER_GRADE;
		}
		if (!g.getExperimentalId().isBlank()) {
			String expName = database != null
					? database.findById(g.getExperimentalId()).map(BlueprintGrade::getName).orElse("experimental")
					: "experimental";
			progress += g.isExperimentalApplied()
					? " · " + expName
					: " · needs " + expName;
		}
		return progress;
	}

	private java.awt.Color gradeColor(ModuleUnitProgress unit) {
		if (!unit.installed()) {
			return EdoUi.Internal.MAIN_TEXT_ALPHA_220;
		}
		EngineeringGoal g = unit.unit();
		if (g == null) {
			return EdoUi.Internal.MAIN_TEXT_ALPHA_220;
		}
		if (g.isComplete() || g.getFromGrade() >= unit.targetGrade()) {
			return EdoUi.User.SUCCESS;
		}
		if (g.getFromGrade() <= 0 && g.getCraftsAtCurrentGrade() <= 0) {
			return EdoUi.Internal.MAIN_TEXT_ALPHA_220;
		}
		return EdoUi.User.MAIN_TEXT;
	}

	private JLabel mutedLabel(String text) {
		JLabel label = new JLabel(text);
		label.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		label.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
		return label;
	}

	private static Map<Long, List<FittedModuleRow>> collectFittedModules(
			Map<Long, LoadoutEvent> loadouts,
			EngineeringDatabase db,
			EngineeringShipCatalog catalog) {
		Map<Long, List<FittedModuleRow>> out = new LinkedHashMap<>();
		if (loadouts == null || loadouts.isEmpty()) {
			return out;
		}
		for (Map.Entry<Long, LoadoutEvent> entry : loadouts.entrySet()) {
			LoadoutEvent loadout = entry.getValue();
			if (loadout == null) {
				continue;
			}
			long shipId = entry.getKey() != null ? entry.getKey().longValue() : loadout.getShipId();
			String shipLabel = "";
			if (catalog != null && shipId >= 0) {
				EngineeringShipRef ref = catalog.get(shipId);
				if (ref != null) {
					shipLabel = catalog.displayLabel(ref);
				}
			}
			if (shipLabel.isBlank()) {
				String name = loadout.getShipName() != null ? loadout.getShipName().trim() : "";
				String ship = loadout.getShip() != null ? loadout.getShip().trim() : "";
				if (!name.isBlank() && !ship.isBlank()) {
					shipLabel = ship + " · " + name;
				} else if (!name.isBlank()) {
					shipLabel = name;
				} else if (!ship.isBlank()) {
					shipLabel = ship;
				} else if (shipId >= 0) {
					shipLabel = "Ship #" + shipId;
				}
			}
			Map<String, FittedModuleRow> merged = new LinkedHashMap<>();
			for (LoadoutEvent.Module module : loadout.getModules()) {
				LoadoutEvent.Engineering engineering = module.getEngineering();
				if (engineering == null || engineering.getLevel() <= 0) {
					continue;
				}
				String moduleType = EngineeringJournalBlueprintResolver.moduleItemToModuleType(module.getItem());
				String moduleLabel = EngineeringJournalBlueprintResolver.displayModuleName(module.getItem());
				String blueprint = friendlyBlueprint(module, engineering, db);
				String experimental = friendlyExperimental(engineering, db);
				int maxGrade = maxBlueprintGrade(db, moduleType, blueprint);
				int level = engineering.getLevel();
				double quality = engineering.getQuality();
				String key = moduleLabel.toLowerCase(Locale.ROOT)
						+ "\0" + blueprint.toLowerCase(Locale.ROOT)
						+ "\0" + level
						+ "\0" + Math.round(Math.max(0.0d, quality) * 100.0d)
						+ "\0" + (experimental != null ? experimental.toLowerCase(Locale.ROOT) : "");
				FittedModuleRow existing = merged.get(key);
				if (existing == null) {
					merged.put(key, new FittedModuleRow(
							shipId,
							shipLabel,
							friendlifySlot(module.getSlot()),
							moduleLabel,
							moduleType != null ? moduleType : "",
							blueprint,
							level,
							maxGrade,
							quality,
							experimental,
							1));
				} else {
					merged.put(key, existing.withCount(existing.count() + 1));
				}
			}
			List<FittedModuleRow> rows = new ArrayList<>(merged.values());
			rows.sort(Comparator
					.comparing(FittedModuleRow::moduleLabel, String.CASE_INSENSITIVE_ORDER)
					.thenComparing(FittedModuleRow::blueprintLabel, String.CASE_INSENSITIVE_ORDER)
					.thenComparingInt(FittedModuleRow::level));
			if (!rows.isEmpty()) {
				out.put(Long.valueOf(shipId), List.copyOf(rows));
			}
		}
		return out;
	}

	private static Map<Long, List<UnengineeredModuleRow>> collectUnengineeredModules(
			Map<Long, LoadoutEvent> loadouts,
			EngineeringShipCatalog catalog) {
		Map<Long, List<UnengineeredModuleRow>> out = new LinkedHashMap<>();
		if (loadouts == null || loadouts.isEmpty()) {
			return out;
		}
		for (Map.Entry<Long, LoadoutEvent> entry : loadouts.entrySet()) {
			LoadoutEvent loadout = entry.getValue();
			if (loadout == null) {
				continue;
			}
			long shipId = entry.getKey() != null ? entry.getKey().longValue() : loadout.getShipId();
			String shipLabel = resolveLoadoutShipLabel(shipId, loadout, catalog);
			Map<String, UnengineeredModuleRow> merged = new LinkedHashMap<>();
			for (LoadoutEvent.Module module : loadout.getModules()) {
				if (module == null || module.getItem() == null || module.getItem().isBlank()) {
					continue;
				}
				LoadoutEvent.Engineering engineering = module.getEngineering();
				if (engineering != null && engineering.getLevel() > 0) {
					continue;
				}
				String moduleType = EngineeringJournalBlueprintResolver.moduleItemToModuleType(module.getItem());
				if (moduleType == null || moduleType.isBlank()) {
					continue;
				}
				String moduleLabel = EngineeringJournalBlueprintResolver.displayModuleName(module.getItem());
				String key = moduleLabel.toLowerCase(Locale.ROOT);
				UnengineeredModuleRow existing = merged.get(key);
				if (existing == null) {
					merged.put(key, new UnengineeredModuleRow(
							shipId,
							shipLabel,
							friendlifySlot(module.getSlot()),
							moduleLabel,
							moduleType,
							1));
				} else {
					merged.put(key, existing.withCount(existing.count() + 1));
				}
			}
			List<UnengineeredModuleRow> rows = new ArrayList<>(merged.values());
			rows.sort(Comparator.comparing(UnengineeredModuleRow::moduleLabel, String.CASE_INSENSITIVE_ORDER));
			if (!rows.isEmpty()) {
				out.put(Long.valueOf(shipId), List.copyOf(rows));
			}
		}
		return out;
	}

	private static String resolveLoadoutShipLabel(long shipId, LoadoutEvent loadout, EngineeringShipCatalog catalog) {
		String shipLabel = "";
		if (catalog != null && shipId >= 0) {
			EngineeringShipRef ref = catalog.get(shipId);
			if (ref != null) {
				shipLabel = catalog.displayLabel(ref);
			}
		}
		if (!shipLabel.isBlank()) {
			return shipLabel;
		}
		String name = loadout.getShipName() != null ? loadout.getShipName().trim() : "";
		String ship = loadout.getShip() != null ? loadout.getShip().trim() : "";
		if (!name.isBlank() && !ship.isBlank()) {
			return ship + " · " + name;
		}
		if (!name.isBlank()) {
			return name;
		}
		if (!ship.isBlank()) {
			return ship;
		}
		return shipId >= 0 ? "Ship #" + shipId : "";
	}

	private static String friendlyBlueprint(LoadoutEvent.Module module,
			LoadoutEvent.Engineering engineering,
			EngineeringDatabase db) {
		Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
				EngineeringJournalBlueprintResolver.resolve(
						module.getSlot(), module.getItem(), engineering.getBlueprintName(), db);
		if (resolved.isPresent()) {
			String bp = resolved.get().blueprintName();
			if (bp != null && !bp.isBlank()) {
				return bp;
			}
		}
		return friendlifyJournalToken(engineering.getBlueprintName());
	}

	private static String friendlyExperimental(LoadoutEvent.Engineering engineering,
			EngineeringDatabase db) {
		String localised = engineering.getExperimentalEffectLocalised();
		if (localised != null && !localised.isBlank()) {
			return localised.trim();
		}
		String effect = engineering.getExperimentalEffect();
		if (effect == null || effect.isBlank()) {
			return "";
		}
		if (db != null) {
			String norm = normalizeToken(effect);
			for (BlueprintGrade bp : db.getAllBlueprints()) {
				if (!bp.isExperimental()) {
					continue;
				}
				String nName = normalizeToken(bp.getName());
				String nId = normalizeToken(bp.getId());
				if ((!nName.isEmpty() && (norm.contains(nName) || nName.contains(norm)))
						|| (!nId.isEmpty() && (norm.contains(nId) || nId.contains(norm)))) {
					return bp.getName();
				}
			}
		}
		return friendlifyJournalToken(effect);
	}

	private static String normalizeToken(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	private static String friendlifySlot(String slot) {
		if (slot == null || slot.isBlank()) {
			return "";
		}
		String s = slot.trim().replace('_', ' ');
		StringBuilder out = new StringBuilder(s.length() + 8);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (i > 0) {
				char prev = s.charAt(i - 1);
				boolean boundary = (Character.isLowerCase(prev) && Character.isUpperCase(c))
						|| (Character.isLetter(prev) && Character.isDigit(c))
						|| (Character.isDigit(prev) && Character.isLetter(c));
				if (boundary && out.charAt(out.length() - 1) != ' ') {
					out.append(' ');
				}
			}
			out.append(c);
		}
		return titleCaseWords(out.toString());
	}

	private static String friendlifyJournalToken(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		String s = value.trim();
		if (s.regionMatches(true, 0, "special_", 0, 8)) {
			s = s.substring(8);
		}
		return titleCaseWords(s.replace('_', ' '));
	}

	private static String titleCaseWords(String s) {
		if (s == null || s.isBlank()) {
			return "";
		}
		StringBuilder out = new StringBuilder(s.length());
		boolean cap = true;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == ' ') {
				out.append(c);
				cap = true;
			} else if (cap) {
				out.append(Character.toUpperCase(c));
				cap = false;
			} else {
				out.append(Character.toLowerCase(c));
			}
		}
		return out.toString().trim();
	}

	private static int maxBlueprintGrade(EngineeringDatabase db, String moduleType, String blueprintName) {
		if (db == null || moduleType == null || moduleType.isBlank()
				|| blueprintName == null || blueprintName.isBlank()) {
			return 0;
		}
		int max = 0;
		for (BlueprintGrade bp : db.gradesFor(moduleType, blueprintName)) {
			if (bp != null && !bp.isExperimental()) {
				max = Math.max(max, bp.getGrade());
			}
		}
		if (max > 0) {
			return max;
		}
		// Fallback if journal/localised blueprint name doesn't exact-match the catalog key.
		for (BlueprintGrade bp : db.getAllBlueprints()) {
			if (bp == null || bp.isExperimental()) {
				continue;
			}
			if (!EngineeringJournalBlueprintResolver.sameModuleType(moduleType, bp.getModuleType())) {
				continue;
			}
			if (!EngineeringJournalBlueprintResolver.normalizeToken(blueprintName)
					.equals(EngineeringJournalBlueprintResolver.normalizeToken(bp.getName()))) {
				continue;
			}
			max = Math.max(max, bp.getGrade());
		}
		return max;
	}

	private record DialogLoadResult(
			List<ModuleUnitProgress> units,
			Map<Long, List<FittedModuleRow>> fittedByShip,
			Map<Long, List<UnengineeredModuleRow>> unengineeredByShip) {
	}

	static record AddGoalRequest(
			EngineeringShipRef ship,
			String moduleType,
			String moduleLabel,
			String blueprintName,
			String experimentalName,
			int quantity) {
	}

	private record UnengineeredModuleRow(
			long shipId,
			String shipLabel,
			String slotLabel,
			String moduleLabel,
			String moduleType,
			int count) {
		UnengineeredModuleRow withCount(int newCount) {
			return new UnengineeredModuleRow(shipId, shipLabel, slotLabel, moduleLabel, moduleType, Math.max(1, newCount));
		}
	}

	private record FittedModuleRow(
			long shipId,
			String shipLabel,
			String slotLabel,
			String moduleLabel,
			String moduleType,
			String blueprintLabel,
			int level,
			int maxGrade,
			double quality,
			String experimentalLabel,
			int count) {
		FittedModuleRow withCount(int newCount) {
			return new FittedModuleRow(
					shipId, shipLabel, slotLabel, moduleLabel, moduleType, blueprintLabel,
					level, maxGrade, quality, experimentalLabel, Math.max(1, newCount));
		}

		boolean canUpgrade() {
			return moduleType != null && !moduleType.isBlank()
					&& blueprintLabel != null && !blueprintLabel.isBlank()
					&& maxGrade > 0
					&& level < maxGrade;
		}

		String gradeLabel() {
			if (level <= 0) {
				return "";
			}
			if (quality >= 0 && quality < 0.999d) {
				return "G" + level + " · " + Math.round(quality * 100.0) + "%";
			}
			return "G" + level;
		}
	}

	private record ShipFilterItem(Long shipId, String label, boolean isSeparator) {
		static ShipFilterItem ship(long shipId, String label) {
			String text = label != null && !label.isBlank() ? label : "Ship #" + shipId;
			return new ShipFilterItem(Long.valueOf(shipId), text, false);
		}

		static ShipFilterItem separator() {
			return new ShipFilterItem(null, "", true);
		}

		@Override
		public String toString() {
			return isSeparator ? "" : label;
		}
	}
}
