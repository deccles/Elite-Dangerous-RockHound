package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
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

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

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
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.OverlayScrollPaneSupport;

/**
 * Hierarchical engineering build progress: ship → blueprint → fitted modules,
 * plus a full list of engineered components currently installed on each ship.
 */
final class EngineeringBuildProgressDialog extends JDialog {

	private static final int HOVER_CLICK_DELAY_MS = 500;

	private final EngineeringDatabase database;
	private final BooleanSupplier passThroughEnabledSupplier;
	private final EngineeringShipCatalog shipCatalog;
	private final Long initialShipFilterId;
	private final JComboBox<ShipFilterItem> shipCombo;
	private final JPanel contentPanel;
	private final Font baseFont;
	private final int fontSize;

	private List<ModuleUnitProgress> allUnits = List.of();
	/** Ship id → engineered modules from the latest stored loadout. */
	private Map<Long, List<FittedModuleRow>> fittedByShip = Map.of();
	private Long lastSelectedShipFilterId;
	private SwingWorker<?, ?> loadWorker;

	private EngineeringBuildProgressDialog(Window owner,
			EngineeringShipCatalog shipCatalog,
			Long initialShipFilterId,
			EngineeringDatabase database,
			BooleanSupplier passThroughEnabledSupplier) {
		super(owner, "Engineering build progress", ModalityType.MODELESS);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		this.database = database;
		this.passThroughEnabledSupplier = passThroughEnabledSupplier;
		this.shipCatalog = shipCatalog != null ? shipCatalog : new EngineeringShipCatalog();
		this.initialShipFilterId = initialShipFilterId;
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
		shipCombo.addItem(ShipFilterItem.all());
		shipCombo.setEnabled(false);
		shipCombo.addActionListener(e -> {
			if (!shipCombo.isEnabled()) {
				return;
			}
			ShipFilterItem item = (ShipFilterItem) shipCombo.getSelectedItem();
			if (item != null && item.isSeparator()) {
				Long prefer = lastSelectedShipFilterId != null
						? lastSelectedShipFilterId
						: initialShipFilterId;
				shipCombo.setSelectedItem(findShipFilterItem(prefer));
				return;
			}
			lastSelectedShipFilterId = item != null ? item.shipId() : null;
			rebuildContent();
		});
		filterRow.add(shipCombo);
		north.add(filterRow, BorderLayout.SOUTH);
		root.add(north, BorderLayout.NORTH);

		contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setOpaque(false);
		contentPanel.setBorder(new EmptyBorder(4, 0, 8, 0));
		showLoading();

		JScrollPane scroll = new JScrollPane(contentPanel);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setOpaque(false);
		scroll.setOpaque(false);
		OverlayScrollPaneSupport.installSubtleScrollBars(scroll);
		root.add(scroll, BorderLayout.CENTER);

		JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		south.setOpaque(false);
		JButton closeBtn = new JButton("Close");
		OverlayOutlineButtonStyle.applyChip(closeBtn, baseFont, false);
		closeBtn.addActionListener(e -> dispose());
		HoverClickPoller.register(closeBtn, HOVER_CLICK_DELAY_MS, this::dispose, passThroughEnabledSupplier);
		south.add(closeBtn);
		root.add(south, BorderLayout.SOUTH);

		setContentPane(root);
		setMinimumSize(new Dimension(520, 480));
		setPreferredSize(new Dimension(680, 820));
		pack();
		setSize(680, 820);
		setLocationRelativeTo(owner);
		setAlwaysOnTop(true);
	}

	static void show(Window owner,
			List<EngineeringGoal> goals,
			EngineeringShipCatalog shipCatalog,
			Long initialShipFilterId,
			EngineeringDatabase database,
			String clientKey,
			BooleanSupplier passThroughEnabledSupplier) {
		EngineeringBuildProgressDialog dialog = new EngineeringBuildProgressDialog(
				owner, shipCatalog, initialShipFilterId, database, passThroughEnabledSupplier);
		dialog.setVisible(true);
		dialog.startLoad(goals, clientKey);
	}

	private void startLoad(List<EngineeringGoal> goals, String clientKey) {
		List<EngineeringGoal> goalSnapshot = goals != null ? List.copyOf(goals) : List.of();
		String key = clientKey != null ? clientKey : "";
		EngineeringDatabase db = database;
		EngineeringShipCatalog catalog = shipCatalog;

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
				return new DialogLoadResult(units, fitted);
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
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					allUnits = List.of();
					fittedByShip = Map.of();
				} catch (ExecutionException ex) {
					allUnits = List.of();
					fittedByShip = Map.of();
				}
				populateShipCombo(initialShipFilterId);
				shipCombo.setEnabled(true);
				rebuildContent();
			}
		};
		loadWorker.execute();
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
				: (keep != null && !keep.isSeparator() ? keep.shipId() : lastSelectedShipFilterId);
		shipCombo.removeAllItems();
		shipCombo.addItem(ShipFilterItem.all());

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
		shipCombo.setSelectedItem(select);
		lastSelectedShipFilterId = select.shipId();
		widenShipComboToFitItems();
	}

	private ShipFilterItem findShipFilterItem(Long shipId) {
		if (shipId == null) {
			return ShipFilterItem.all();
		}
		for (int i = 0; i < shipCombo.getItemCount(); i++) {
			ShipFilterItem item = shipCombo.getItemAt(i);
			if (item != null && !item.isSeparator() && shipId.equals(item.shipId())) {
				return item;
			}
		}
		return ShipFilterItem.all();
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
		combo.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		combo.setForeground(EdoUi.User.MAIN_TEXT);
		combo.setBackground(EdoUi.User.PANEL_BG);
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
				if (value instanceof ShipFilterItem item) {
					setText(item.label());
				}
				setForeground(EdoUi.User.MAIN_TEXT);
				setBackground(isSelected ? EdoUi.ED_ORANGE_LESS_TRANS : EdoUi.User.PANEL_BG);
				return c;
			}
		});
	}

	private void rebuildContent() {
		contentPanel.removeAll();
		ShipFilterItem filter = (ShipFilterItem) shipCombo.getSelectedItem();
		Long shipFilterId = filter != null ? filter.shipId() : null;

		List<ModuleUnitProgress> visibleGoals = new ArrayList<>();
		for (ModuleUnitProgress u : allUnits) {
			if (shipFilterId == null || u.shipId() == shipFilterId.longValue()) {
				visibleGoals.add(u);
			}
		}

		Map<Long, List<FittedModuleRow>> visibleFitted = new LinkedHashMap<>();
		if (shipFilterId != null) {
			List<FittedModuleRow> rows = fittedByShip.getOrDefault(shipFilterId, List.of());
			if (!rows.isEmpty()) {
				visibleFitted.put(shipFilterId, rows);
			}
		} else {
			visibleFitted.putAll(fittedByShip);
		}

		if (visibleGoals.isEmpty() && visibleFitted.isEmpty()) {
			JLabel empty = mutedLabel(allUnits.isEmpty() && fittedByShip.isEmpty()
					? "No engineering goals or fitted modules yet."
					: "Nothing for this ship.");
			empty.setAlignmentX(Component.LEFT_ALIGNMENT);
			contentPanel.add(empty);
			contentPanel.revalidate();
			contentPanel.repaint();
			return;
		}

		boolean showShipSections = shipFilterId == null
				&& distinctShipCount(visibleGoals, visibleFitted) > 1;

		Map<Long, List<ModuleUnitProgress>> goalsByShip = new LinkedHashMap<>();
		for (ModuleUnitProgress u : visibleGoals) {
			goalsByShip.computeIfAbsent(Long.valueOf(u.shipId()), k -> new ArrayList<>()).add(u);
		}

		List<Long> shipOrder = orderedShipIds(goalsByShip, visibleFitted);
		boolean firstShip = true;
		for (Long shipIdObj : shipOrder) {
			long shipId = shipIdObj.longValue();
			List<ModuleUnitProgress> shipUnits = goalsByShip.getOrDefault(shipIdObj, List.of());
			List<FittedModuleRow> fitted = visibleFitted.getOrDefault(shipIdObj, List.of());
			if (shipUnits.isEmpty() && fitted.isEmpty()) {
				continue;
			}
			if (!firstShip) {
				contentPanel.add(Box.createVerticalStrut(16));
			}
			firstShip = false;

			if (showShipSections) {
				contentPanel.add(shipSectionHeader(resolveShipTitle(shipId, shipUnits)));
				contentPanel.add(Box.createVerticalStrut(8));
			}

			boolean firstBlock = true;
			if (!shipUnits.isEmpty()) {
				firstBlock = false;
				contentPanel.add(sectionHeadline("Goal progress"));
				contentPanel.add(Box.createVerticalStrut(4));
				appendGoalBlocks(shipUnits);
			}
			if (!fitted.isEmpty()) {
				if (!firstBlock) {
					contentPanel.add(Box.createVerticalStrut(14));
				}
				contentPanel.add(sectionHeadline("All engineered on this ship"));
				contentPanel.add(Box.createVerticalStrut(4));
				contentPanel.add(createFittedTablePanel(fitted));
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

	private static int distinctShipCount(List<ModuleUnitProgress> goals,
			Map<Long, List<FittedModuleRow>> fitted) {
		Map<Long, Boolean> ids = new LinkedHashMap<>();
		for (ModuleUnitProgress u : goals) {
			ids.put(Long.valueOf(u.shipId()), Boolean.TRUE);
		}
		for (Long shipId : fitted.keySet()) {
			ids.put(shipId, Boolean.TRUE);
		}
		return ids.size();
	}

	private List<Long> orderedShipIds(Map<Long, List<ModuleUnitProgress>> goalsByShip,
			Map<Long, List<FittedModuleRow>> fittedByShipVisible) {
		Map<Long, String> labels = new LinkedHashMap<>();
		for (Long id : goalsByShip.keySet()) {
			labels.put(id, resolveShipTitle(id.longValue(), goalsByShip.get(id)));
		}
		for (Long id : fittedByShipVisible.keySet()) {
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
		return shipId >= 0 ? "Ship #" + shipId : "Unassigned";
	}

	private JPanel shipSectionHeader(String title) {
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, fontSize + 18));
		JLabel label = new JLabel(title);
		label.setFont(baseFont.deriveFont(Font.BOLD, fontSize + 1));
		label.setForeground(EdoUi.User.MAIN_TEXT);
		row.add(label, BorderLayout.WEST);
		JPanel rule = new JPanel();
		rule.setOpaque(true);
		rule.setBackground(EdoUi.Internal.separatorLine());
		rule.setPreferredSize(new Dimension(10, 1));
		row.add(rule, BorderLayout.CENTER);
		return row;
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
		FittedTableModel model = new FittedTableModel(fitted);
		JTable table = new JTable(model);
		table.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		table.setRowHeight(Math.max(22, fontSize + 10));
		table.setOpaque(false);
		table.setBackground(EdoUi.Internal.TRANSPARENT);
		table.setForeground(EdoUi.User.MAIN_TEXT);
		table.setShowGrid(false);
		table.setShowHorizontalLines(false);
		table.setShowVerticalLines(false);
		table.setIntercellSpacing(new Dimension(0, 0));
		table.setFocusable(false);
		table.setRowSelectionAllowed(false);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		table.setFillsViewportHeight(true);

		DefaultTableCellRenderer cell = new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable t, Object value,
					boolean isSelected, boolean hasFocus, int row, int column) {
				Component c = super.getTableCellRendererComponent(t, value, false, false, row, column);
				if (c instanceof JLabel label) {
					label.setOpaque(false);
					label.setBorder(new EmptyBorder(1, 6, 1, 6));
					label.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
					label.setForeground(column == FittedTableModel.COL_GRADE
							? EdoUi.User.MAIN_TEXT
							: EdoUi.Internal.MAIN_TEXT_ALPHA_220);
					if (column == FittedTableModel.COL_GRADE) {
						label.setHorizontalAlignment(SwingConstants.CENTER);
					} else {
						label.setHorizontalAlignment(SwingConstants.LEFT);
					}
					String text = value != null ? value.toString() : "";
					label.setToolTipText(text.isBlank() ? null : text);
				}
				return c;
			}
		};
		table.setDefaultRenderer(Object.class, cell);

		JTableHeader header = table.getTableHeader();
		if (header != null) {
			header.setFont(baseFont.deriveFont(Font.BOLD, fontSize));
			header.setForeground(EdoUi.User.MAIN_TEXT);
			header.setBackground(EdoUi.User.PANEL_BG);
			header.setReorderingAllowed(false);
			header.setResizingAllowed(true);
			header.setDefaultRenderer(new DefaultTableCellRenderer() {
				@Override
				public Component getTableCellRendererComponent(JTable t, Object value,
						boolean isSelected, boolean hasFocus, int row, int column) {
					Component c = super.getTableCellRendererComponent(t, value, false, false, row, column);
					if (c instanceof JLabel label) {
						label.setOpaque(true);
						label.setBackground(EdoUi.User.PANEL_BG);
						label.setForeground(EdoUi.User.MAIN_TEXT);
						label.setFont(baseFont.deriveFont(Font.BOLD, fontSize));
						label.setBorder(new EmptyBorder(2, 6, 4, 6));
						label.setHorizontalAlignment(column == FittedTableModel.COL_GRADE
								? SwingConstants.CENTER
								: SwingConstants.LEFT);
					}
					return c;
				}
			});
		}

		TableColumnModel cols = table.getColumnModel();
		setColWidth(cols.getColumn(FittedTableModel.COL_MODULE), 200, 140, 320);
		setColWidth(cols.getColumn(FittedTableModel.COL_BLUEPRINT), 170, 110, 260);
		setColWidth(cols.getColumn(FittedTableModel.COL_GRADE), 56, 48, 72);
		setColWidth(cols.getColumn(FittedTableModel.COL_EXPERIMENTAL), 140, 100, 240);

		int headerH = header != null ? header.getPreferredSize().height : fontSize + 12;
		int bodyH = table.getRowHeight() * Math.max(1, fitted.size());
		int tableH = headerH + bodyH + 2;

		JScrollPane scroll = new JScrollPane(table);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		OverlayScrollPaneSupport.installSubtleScrollBars(scroll);
		scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		scroll.setPreferredSize(new Dimension(620, tableH));
		scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, tableH));
		scroll.setMinimumSize(new Dimension(280, Math.min(tableH, headerH + table.getRowHeight() * 3)));
		return scroll;
	}

	private static void setColWidth(TableColumn col, int preferred, int min, int max) {
		col.setPreferredWidth(preferred);
		col.setMinWidth(min);
		col.setMaxWidth(max);
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
			List<FittedModuleRow> rows = new ArrayList<>();
			for (LoadoutEvent.Module module : loadout.getModules()) {
				LoadoutEvent.Engineering engineering = module.getEngineering();
				if (engineering == null || engineering.getLevel() <= 0) {
					continue;
				}
				String blueprint = friendlyBlueprint(module, engineering, db);
				String experimental = friendlyExperimental(engineering, db);
				rows.add(new FittedModuleRow(
						shipId,
						shipLabel,
						friendlifySlot(module.getSlot()),
						friendlifyItemId(module.getItem()),
						blueprint,
						engineering.getLevel(),
						engineering.getQuality(),
						experimental));
			}
			rows.sort(Comparator
					.comparing(FittedModuleRow::slotLabel, String.CASE_INSENSITIVE_ORDER)
					.thenComparing(FittedModuleRow::moduleLabel, String.CASE_INSENSITIVE_ORDER));
			if (!rows.isEmpty()) {
				out.put(Long.valueOf(shipId), List.copyOf(rows));
			}
		}
		return out;
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

	private static String friendlifyItemId(String item) {
		if (item == null || item.isBlank()) {
			return "";
		}
		String s = item;
		if (s.regionMatches(true, 0, "hpt_", 0, 4)) {
			s = s.substring(4);
		} else if (s.regionMatches(true, 0, "int_", 0, 4)) {
			s = s.substring(4);
		}
		return titleCaseWords(s.replace('_', ' '));
	}

	/** {@code LargeHardpoint1} → {@code Large Hardpoint 1}; {@code Slot01_Size7} → {@code Slot 01 Size 7}. */
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

	private record DialogLoadResult(
			List<ModuleUnitProgress> units,
			Map<Long, List<FittedModuleRow>> fittedByShip) {
	}

	private record FittedModuleRow(
			long shipId,
			String shipLabel,
			String slotLabel,
			String moduleLabel,
			String blueprintLabel,
			int level,
			double quality,
			String experimentalLabel) {
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

	private static final class FittedTableModel extends AbstractTableModel {
		static final int COL_MODULE = 0;
		static final int COL_BLUEPRINT = 1;
		static final int COL_GRADE = 2;
		static final int COL_EXPERIMENTAL = 3;

		private final List<FittedModuleRow> rows;

		FittedTableModel(List<FittedModuleRow> rows) {
			this.rows = rows != null ? List.copyOf(rows) : List.of();
		}

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public int getColumnCount() {
			return 4;
		}

		@Override
		public String getColumnName(int column) {
			return switch (column) {
				case COL_MODULE -> "Module";
				case COL_BLUEPRINT -> "Blueprint";
				case COL_GRADE -> "Grade";
				case COL_EXPERIMENTAL -> "Experimental";
				default -> "";
			};
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			if (rowIndex < 0 || rowIndex >= rows.size()) {
				return "";
			}
			FittedModuleRow row = rows.get(rowIndex);
			return switch (columnIndex) {
				case COL_MODULE -> row.moduleLabel();
				case COL_BLUEPRINT -> row.blueprintLabel();
				case COL_GRADE -> row.gradeLabel();
				case COL_EXPERIMENTAL -> row.experimentalLabel();
				default -> "";
			};
		}
	}

	private record ShipFilterItem(Long shipId, String label, boolean isSeparator) {
		static ShipFilterItem all() {
			return new ShipFilterItem(null, "All", false);
		}

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
